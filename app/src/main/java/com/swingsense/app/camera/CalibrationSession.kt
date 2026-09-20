package com.swingsense.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.swingsense.app.model.BallColor
import com.swingsense.app.vision.BackgroundModel
import com.swingsense.app.vision.BallDetector
import com.swingsense.app.vision.Detection
import com.swingsense.app.vision.Roi
import com.swingsense.app.vision.YuvFrame

enum class CalibrationPhase { CAPTURING_BACKGROUND, AWAITING_BALL, TRACKING }

/**
 * Session de CALIBRATION (basse cadence, ~30 im/s).
 *
 * Deroulement en deux temps, revu apres les premiers essais sur l'herbe :
 *
 * 1. CAPTURING_BACKGROUND : le golfeur ne pose pas encore sa balle. On
 *    moyenne quelques images du sol tel qu'il est - herbe, tapis, ombre du
 *    golfeur, tout ce qui est deja la. C'est cette photo de reference qui
 *    remplace le seuillage colorimetrique, en echec regulier sur l'herbe
 *    (texture, ombres, brins plus ou moins clairs).
 * 2. AWAITING_BALL / TRACKING : une fois la balle posee, chaque image est
 *    comparee a ce fond. Peu importe la couleur du terrain en dessous : on
 *    cherche ce qui a change, pas ce qui est blanc.
 *
 * Le temps reel a cadence normale est ici parfaitement suffisant (la balle
 * est immobile) ; c'est exactement l'usage pour lequel ImageReader est fait.
 */
class CalibrationSession(
    private val context: Context,
    private val ballColor: BallColor,
    private val analysisSize: Size = Size(1280, 720),
    private val referenceFrameCount: Int = 18,
    private val onPhaseChanged: (CalibrationPhase) -> Unit,
    private val onBackgroundProgress: (Int, Int) -> Unit,
    private val onBackgroundReady: (BackgroundModel) -> Unit,
    private val onDetection: (Detection?, Int, Int) -> Unit
) {
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var busy = false

    private var phase = CalibrationPhase.CAPTURING_BACKGROUND
    private val refFrames = mutableListOf<YuvFrame>()
    private var background: BackgroundModel? = null

    /**
     * Zone de recherche optionnelle, designee au doigt. Purement une aide :
     * la detection par difference au fond fonctionne des le depart sur toute
     * l'image. Le pointage sert seulement a departager deux objets qui ont
     * tous les deux bouge (la balle ET, par exemple, le bout d'une chaussure).
     */
    @Volatile var seedX: Float? = null
    @Volatile var seedY: Float? = null
    @Volatile var searchHalfFraction: Float = 0.12f

    @SuppressLint("MissingPermission")
    fun start(previewSurface: Surface, cameraId: String, reuseBackground: BackgroundModel? = null) {
        thread = HandlerThread("calib").also { it.start() }
        handler = Handler(thread!!.looper)
        refFrames.clear()

        if (reuseBackground != null) {
            // Le trepied n'a pas bouge depuis le swing precedent : inutile de
            // refaire une capture de fond, on repart directement en attente de balle.
            background = reuseBackground
            phase = CalibrationPhase.AWAITING_BALL
        } else {
            background = null
            phase = CalibrationPhase.CAPTURING_BACKGROUND
        }
        onPhaseChanged(phase)

        reader = ImageReader.newInstance(
            analysisSize.width, analysisSize.height, ImageFormat.YUV_420_888, 2
        ).apply {
            setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (busy) { image.close(); return@setOnImageAvailableListener }
                busy = true
                try {
                    val frame = YuvFrame.from(image)
                    image.close()
                    handleFrame(frame)
                } catch (t: Throwable) {
                    runCatching { image.close() }
                } finally {
                    busy = false
                }
            }, handler)
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                device = camera
                val targets = listOf(previewSurface, reader!!.surface)
                @Suppress("DEPRECATION")
                camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                            addTarget(reader!!.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        }
                        s.setRepeatingRequest(req.build(), null, handler)
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) = Unit
                }, handler)
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close(); device = null }
            override fun onError(camera: CameraDevice, error: Int) { camera.close(); device = null }
        }, handler)
    }

    private fun handleFrame(frame: YuvFrame) {
        when (phase) {
            CalibrationPhase.CAPTURING_BACKGROUND -> {
                refFrames.add(frame)
                onBackgroundProgress(refFrames.size, referenceFrameCount)
                if (refFrames.size >= referenceFrameCount) {
                    val bg = BackgroundModel.average(refFrames)
                    background = bg
                    refFrames.clear()
                    phase = CalibrationPhase.AWAITING_BALL
                    onPhaseChanged(phase)
                    onBackgroundReady(bg)
                }
            }
            CalibrationPhase.AWAITING_BALL, CalibrationPhase.TRACKING -> {
                val bg = background ?: return
                val sx = seedX
                val sy = seedY
                val roi = if (sx != null && sy != null) {
                    val half = frame.width * searchHalfFraction
                    Roi.around((sx * frame.width).toDouble(), (sy * frame.height).toDouble(), half.toDouble())
                        .clamp(frame.width, frame.height)
                } else {
                    Roi(0, 0, frame.width, frame.height)
                }
                val det = BallDetector.detectByBackground(
                    frame, bg, roi = roi,
                    step = if (sx != null) 1 else 2,
                    colorHint = ballColor
                )
                onDetection(det, frame.width, frame.height)
            }
        }
    }

    /** Relance la capture de fond : eclairage qui a change, faux positif persistant. */
    fun recaptureBackground() {
        refFrames.clear()
        background = null
        phase = CalibrationPhase.CAPTURING_BACKGROUND
        onPhaseChanged(phase)
    }

    /** Fond actuellement valide, a transmettre a la session suivante (meme swing) ou a l'analyse. */
    fun currentBackground(): BackgroundModel? = background

    fun stop() {
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        thread?.quitSafely()
        refFrames.clear()
        session = null; device = null; reader = null; thread = null; handler = null
    }
}
