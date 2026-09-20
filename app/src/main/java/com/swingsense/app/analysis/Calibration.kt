package com.swingsense.app.analysis

import kotlin.math.atan
import kotlin.math.tan

/**
 * Calibration metrique a partir du diametre apparent de la balle.
 *
 * Une balle de golf fait 42,67 mm de diametre (norme R&A/USGA) : c'est notre
 * etalon. Connaissant la focale en pixels, on en deduit :
 *   - la distance camera-balle  Z = f_px * D / (2 * r_px)
 *   - l'echelle au plan de la balle  s = D / (2 * r_px)  (metres par pixel)
 */
object Calibration {

    const val BALL_DIAMETER_M = 0.04267

    /** Focale en pixels a partir du champ de vision horizontal. */
    fun focalPxFromFov(imageWidthPx: Int, horizontalFovDeg: Double): Double {
        val half = Math.toRadians(horizontalFovDeg / 2.0)
        return (imageWidthPx / 2.0) / tan(half)
    }

    /** Focale en pixels a partir des caracteristiques capteur (mm). */
    fun focalPx(imageWidthPx: Int, focalLengthMm: Float, sensorWidthMm: Float): Double =
        imageWidthPx.toDouble() * focalLengthMm / sensorWidthMm

    fun fovDeg(imageWidthPx: Int, focalPx: Double): Double =
        Math.toDegrees(2.0 * atan((imageWidthPx / 2.0) / focalPx))

    fun distanceMeters(focalPx: Double, ballRadiusPx: Double): Double =
        focalPx * BALL_DIAMETER_M / (2.0 * ballRadiusPx)

    /** Metres par pixel dans le plan de la balle. */
    fun metersPerPixel(ballRadiusPx: Double): Double =
        BALL_DIAMETER_M / (2.0 * ballRadiusPx)

    /**
     * Fenetre de rayon apparent recherchee pendant la calibration.
     * Trop petit -> suivi imprecis ; trop gros -> la balle sort du champ en 3 images.
     */
    const val TARGET_RADIUS_MIN_PX = 7.0
    const val TARGET_RADIUS_MAX_PX = 22.0

    fun guidance(radiusPx: Double?): CalibrationGuidance = when {
        radiusPx == null -> CalibrationGuidance.NOT_FOUND
        radiusPx < TARGET_RADIUS_MIN_PX -> CalibrationGuidance.TOO_FAR
        radiusPx > TARGET_RADIUS_MAX_PX -> CalibrationGuidance.TOO_CLOSE
        else -> CalibrationGuidance.OK
    }
}

enum class CalibrationGuidance(val message: String) {
    NOT_FOUND("Rien de nouveau detecte - posez la balle bien visible dans le champ"),
    TOO_FAR("Rapprochez le telephone de la balle"),
    TOO_CLOSE("Eloignez le telephone de la balle"),
    OK("Balle verrouillee")
}
