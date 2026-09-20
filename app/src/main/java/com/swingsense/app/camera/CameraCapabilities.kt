package com.swingsense.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size

data class HighSpeedOption(
    val cameraId: String,
    val size: Size,
    val fpsRange: Range<Int>
) {
    val fps: Int get() = fpsRange.upper
    override fun toString() = "${size.width}x${size.height} @ ${fps} im/s"
}

data class LensInfo(
    val cameraId: String,
    val focalLengthMm: Float,
    val sensorWidthMm: Float
)

/**
 * Interrogation des capacites "constrained high speed" de l'appareil.
 *
 * Sur Pixel 7 Pro on trouve typiquement 1280x720 @ 240 im/s et 1920x1080 @ 120 im/s.
 * On prend par defaut la cadence la plus elevee : c'est elle qui conditionne
 * toute la precision de l'analyse (a 60 im/s, une balle a 60 m/s parcourt 1 m
 * entre deux images - inexploitable).
 */
object CameraCapabilities {

    fun listHighSpeed(context: Context): List<HighSpeedOption> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val result = mutableListOf<HighSpeedOption>()

        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing != CameraCharacteristics.LENS_FACING_BACK) continue

            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: continue
            if (!caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) continue

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
            for (size in map.highSpeedVideoSizes) {
                for (range in map.getHighSpeedVideoFpsRangesFor(size)) {
                    // On ne garde que les plages "fixes" (lower == upper) : ce sont
                    // celles qui garantissent la cadence demandee.
                    if (range.lower == range.upper) {
                        result += HighSpeedOption(id, size, range)
                    }
                }
            }
        }
        return result.sortedWith(
            compareByDescending<HighSpeedOption> { it.fps }
                .thenByDescending { it.size.width * it.size.height }
        )
    }

    /** Meilleur compromis : cadence maximale, puis plus grande resolution a cette cadence. */
    fun best(context: Context): HighSpeedOption? = listHighSpeed(context).firstOrNull()

    /** Repli quand l'appareil n'expose aucune session haute vitesse. */
    fun fallbackSize(context: Context): Size {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: return Size(1280, 720)
        val map = manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
        return sizes?.firstOrNull { it.width == 1280 && it.height == 720 } ?: Size(1280, 720)
    }

    fun lensInfo(context: Context, cameraId: String): LensInfo? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val chars = manager.getCameraCharacteristics(cameraId)
        val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focals == null || focals.isEmpty() || sensor == null) return null
        return LensInfo(cameraId, focals[0], sensor.width)
    }

    fun backCameraId(context: Context): String? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
    }
}
