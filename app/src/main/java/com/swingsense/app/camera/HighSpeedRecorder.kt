package com.swingsense.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import java.util.concurrent.Executor

/**
 * Enregistrement en session "constrained high speed" (120 / 240 im/s).
 *
 * C'est le coeur technique du projet, et la raison pour laquelle l'analyse est
 * differee : dans ce mode, la camera ne delivre PAS d'images a l'application.
 * Elle alimente directement l'encodeur materiel, en rafale. Aucun ImageReader
 * n'est autorise dans la session. L'application ne voit donc rien pendant le
 * swing ; elle relit le fichier juste apres (VideoFrameExtractor).
 *
 * Contraintes Android a respecter :
 *  - au plus 2 surfaces dans la session (preview et/ou encodeur) ;
 *  - la taille doit venir de highSpeedVideoSizes ;
 *  - setCaptureRate == setVideoFrameRate pour ecrire un fichier a la cadence
 *    reelle (sinon Android produit un fichier "ralenti" a 30 im/s et les
 *    horodatages sont etires) ;
 *  - pas de piste audio : le micro reste libre pour la detection d'impact.
 */
class HighSpeedRecorder(private val context: Context) {

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var outputFile: File? = null
    var isRecording = false
        private set

    var actualFps: Int = 0
        private set

    private val executor = Executor { r -> handler?.post(r) ?: r.run() }

    @SuppressLint("MissingPermission")
    fun start(
        option: HighSpeedOption,
        outputDir: File,
        onReady: () -> Unit,
        onError: (String) -> Unit
    ) {
        thread = HandlerThread("highspeed").also { it.start() }
        handler = Handler(thread!!.looper)
        actualFps = option.fps

        val file = File(outputDir, "swing_${System.currentTimeMillis()}.mp4")
        outputFile = file

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        recorder = rec
        try {
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setOutputFile(file.absolutePath)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setVideoSize(option.size.width, option.size.height)
            rec.setVideoFrameRate(option.fps)
            rec.setCaptureRate(option.fps.toDouble())   // fichier a la cadence reelle
            rec.setVideoEncodingBitRate(estimateBitrate(option))
            rec.prepare()
        } catch (t: Throwable) {
            onError("Impossible de preparer l'encodeur : ${t.message}")
            return
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        manager.openCamera(option.cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                device = camera
                val surface = rec.surface
                val outputs = listOf(OutputConfiguration(surface))
                val config = SessionConfiguration(
                    SessionConfiguration.SESSION_HIGH_SPEED,
                    outputs,
                    executor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            session = s
                            try {
                                val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                    addTarget(surface)
                                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, option.fpsRange)
                                    set(CaptureRequest.CONTROL_AE_LOCK, true)
                                }
                                val hs = s as CameraConstrainedHighSpeedCaptureSession
                                val burst = hs.createHighSpeedRequestList(builder.build())
                                hs.setRepeatingBurst(burst, null, handler)
                                rec.start()
                                isRecording = true
                                onReady()
                            } catch (t: Throwable) {
                                onError("Demarrage haute vitesse impossible : ${t.message}")
                            }
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            onError("Configuration de la session haute vitesse refusee par l'appareil.")
                        }
                    }
                )
                camera.createCaptureSession(config)
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close(); device = null }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close(); device = null
                onError("Erreur camera $error")
            }
        }, handler)
    }

    /** @return le fichier enregistre, ou null si l'enregistrement a echoue. */
    fun stop(): File? {
        if (!isRecording) { release(); return null }
        isRecording = false
        runCatching { session?.stopRepeating() }
        runCatching { session?.abortCaptures() }
        val ok = runCatching { recorder?.stop() }.isSuccess
        release()
        return if (ok) outputFile else null
    }

    private fun release() {
        runCatching { session?.close() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        runCatching { device?.close() }
        thread?.quitSafely()
        session = null; recorder = null; device = null; thread = null; handler = null
    }

    private fun estimateBitrate(option: HighSpeedOption): Int {
        val pixels = option.size.width.toLong() * option.size.height
        val raw = pixels * option.fps * 0.09
        return raw.toLong().coerceIn(12_000_000L, 90_000_000L).toInt()
    }
}
