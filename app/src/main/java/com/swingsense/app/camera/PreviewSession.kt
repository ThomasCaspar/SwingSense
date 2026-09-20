package com.swingsense.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface

/**
 * Aperitif camera minimal : ouvre la camera et affiche le flux, sans
 * analyse d'aucune sorte (pas d'ImageReader).
 *
 * Utilise sur l'ecran de PLACEMENT pour que l'utilisateur voie reellement ce
 * que la camera cadre - avec le niveau a bulle en superposition - avant de
 * passer a la calibration (CalibrationSession, sur l'ecran suivant, ouvre sa
 * propre session avec detection ; le bref rechargement de la camera lors de
 * la transition entre les deux ecrans est le compromis retenu pour garder
 * PlacementScreen et CalibrationScreen independants l'un de l'autre).
 */
class PreviewSession(private val context: Context) {

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    @SuppressLint("MissingPermission")
    fun start(previewSurface: Surface, cameraId: String) {
        thread = HandlerThread("preview").also { it.start() }
        handler = Handler(thread!!.looper)

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                device = camera
                @Suppress("DEPRECATION")
                camera.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
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

    fun stop() {
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        runCatching { device?.close() }
        thread?.quitSafely()
        session = null; device = null; thread = null; handler = null
    }
}
