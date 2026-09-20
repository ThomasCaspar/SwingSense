package com.swingsense.app.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.sqrt

data class LevelReading(
    /** Inclinaison laterale ("l'horizon est-il droit ?") - c'est elle qui compte pour la geometrie. */
    val rollDeg: Float,
    /** Inclinaison avant/arriere - informatif seulement : viser legerement vers le bas pour cadrer une balle au sol est normal. */
    val pitchDeg: Float
)

/**
 * Niveau a bulle base sur le capteur de gravite (repli sur l'accelerometre brut).
 *
 * Gravite et non gyroscope, volontairement : le gyroscope mesure une VITESSE
 * de rotation, pas une orientation absolue - l'integrer dans le temps derive.
 * Le capteur de gravite (deja filtre par la plateforme) donne directement "ou
 * est le bas", ce qui suffit pour un niveau statique, sans derive.
 *
 * Le roll (inclinaison laterale) est la valeur qui compte reellement pour
 * l'analyse : un telephone incline sur le cote tourne l'image, et toute la
 * geometrie (angle de lancement, decomposition de la vitesse en x/y) suppose
 * que "haut de l'image" = "vertical reel". Un telephone visiblement de travers
 * fausse directement les distances calculees - c'est le symptome remonte.
 * Le pitch (avant/arriere) reste affiche a titre informatif seulement : viser
 * legerement vers le bas pour cadrer une balle posee au sol est normal et
 * attendu, donc il n'a pas de seuil pass/fail.
 */
class LevelSensor(private val context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var onReading: ((LevelReading) -> Unit)? = null
    val available: Boolean get() = sensor != null

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rotation = currentRotation()
        val (sx, sy) = toScreenRelative(event.values[0], event.values[1], rotation)
        val gz = event.values[2]

        val rollRad = atan2(sx.toDouble(), sy.toDouble())
        val pitchRad = atan2((-gz).toDouble(), sqrt((sx * sx + sy * sy).toDouble()))

        onReading?.invoke(
            LevelReading(
                rollDeg = Math.toDegrees(rollRad).toFloat(),
                pitchDeg = Math.toDegrees(pitchRad).toFloat()
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun currentRotation(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }
    } catch (t: Throwable) {
        Surface.ROTATION_0
    }

    /**
     * Reprojette les axes bruts du capteur (fixes par rapport au chassis) sur
     * les axes actuels de l'ecran. Necessaire car l'app est verrouillee en
     * paysage, mais le repere de l'accelerometre reste toujours celui de
     * l'orientation naturelle (portrait) de l'appareil.
     *
     * NOTE : signe non verifie sur appareil physique (indisponible dans cet
     * environnement de build) - si la bulle tourne dans le mauvais sens sur
     * votre telephone, inversez simplement le signe de rollDeg a l'usage.
     */
    private fun toScreenRelative(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        Surface.ROTATION_90 -> -y to x
        Surface.ROTATION_180 -> -x to -y
        Surface.ROTATION_270 -> y to -x
        else -> x to y
    }

    companion object {
        /** Tolerance sous laquelle le roll est considere "niveau". */
        const val TOLERANCE_DEG = 3f
    }
}
