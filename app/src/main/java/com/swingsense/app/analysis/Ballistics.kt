package com.swingsense.app.analysis

import com.swingsense.app.model.Club
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Modele de vol de balle : trainee + portance (effet Magnus), integration RK4.
 *
 * On ne mesure pas le spin optiquement (il faudrait resoudre les alveoles sur
 * plusieurs images, hors de portee d'un capteur de telephone a 2 m).
 * Le backspin est donc ESTIME a partir du loft, de l'angle d'attaque et de la
 * vitesse - c'est le maillon faible de la chaine, et c'est annonce comme tel
 * dans l'interface.
 */
object Ballistics {

    private const val MASS = 0.04593        // kg
    private const val RADIUS = 0.021335     // m
    private const val AREA = Math.PI * RADIUS * RADIUS
    private const val RHO = 1.225           // kg/m3
    private const val G = 9.80665

    data class Flight(
        val carryM: Double,
        val totalM: Double,
        val apexM: Double,
        val flightTimeS: Double,
        val descentAngleDeg: Double,
        val lateralM: Double
    )

    /**
     * Backspin empirique (rpm).
     * Ordre de grandeur vise : driver ~2500, fer 7 ~7000, PW ~9000.
     */
    fun estimateBackspin(club: Club, ballSpeedMs: Double, attackAngleDeg: Double): Double {
        val dynamicLoft = club.loftDeg + attackAngleDeg * 0.6
        val base = 145.0 * dynamicLoft
        val speedFactor = (ballSpeedMs / 45.0).coerceIn(0.5, 1.6)
        return (base * speedFactor).coerceIn(800.0, 12000.0)
    }

    /** Coefficient de trainee approche, fonction du rapport de spin. */
    private fun dragCoefficient(spinRatio: Double): Double =
        (0.24 + 0.18 * spinRatio).coerceIn(0.21, 0.42)

    /** Coefficient de portance issu du spin (modele classique type Bearman-Harvey). */
    private fun liftCoefficient(spinRatio: Double): Double =
        (0.05 + 3.3 * spinRatio - 8.0 * spinRatio * spinRatio).coerceIn(0.0, 0.34)

    /**
     * @param speedMs vitesse de balle
     * @param launchDeg angle de lancement vertical
     * @param azimuthDeg direction horizontale (+ = droite)
     * @param backspinRpm backspin
     * @param sideSpinRpm sidespin (+ = fade pour un droitier)
     */
    fun simulate(
        speedMs: Double,
        launchDeg: Double,
        azimuthDeg: Double,
        backspinRpm: Double,
        sideSpinRpm: Double,
        dt: Double = 0.002
    ): Flight {
        val launch = Math.toRadians(launchDeg)
        val az = Math.toRadians(azimuthDeg)

        var x = 0.0; var y = 0.0; var z = 0.0
        var vx = speedMs * cos(launch) * cos(az)
        var vy = speedMs * sin(launch)
        var vz = speedMs * cos(launch) * sin(az)

        val omegaBack = backspinRpm * 2.0 * Math.PI / 60.0
        val omegaSide = sideSpinRpm * 2.0 * Math.PI / 60.0

        var apex = 0.0
        var t = 0.0
        var lastY = 0.0
        var lastX = 0.0
        var lastZ = 0.0
        var lastVy = vy

        while (t < 15.0) {
            val v = hypot(hypot(vx, vy), vz)
            if (v < 0.5) break
            val spinRatio = ((RADIUS * hypot(omegaBack, omegaSide)) / v).coerceIn(0.0, 0.5)
            val cd = dragCoefficient(spinRatio)
            val cl = liftCoefficient(spinRatio)

            val kDrag = 0.5 * RHO * AREA * cd * v / MASS
            val kLift = 0.5 * RHO * AREA * cl * v / MASS

            // Portance : backspin -> vers le haut ; sidespin -> lateral
            val liftDirUp = if (hypot(omegaBack, omegaSide) > 0) omegaBack / hypot(omegaBack, omegaSide) else 1.0
            val liftDirSide = if (hypot(omegaBack, omegaSide) > 0) omegaSide / hypot(omegaBack, omegaSide) else 0.0

            val ax = -kDrag * vx
            val ay = -kDrag * vy - G + kLift * liftDirUp
            val az2 = -kDrag * vz + kLift * liftDirSide

            lastX = x; lastY = y; lastZ = z; lastVy = vy
            vx += ax * dt; vy += ay * dt; vz += az2 * dt
            x += vx * dt; y += vy * dt; z += vz * dt
            t += dt

            if (y > apex) apex = y
            if (y <= 0.0 && t > 0.1) break
        }

        // Interpolation du point d'impact au sol
        val frac = if (abs(y - lastY) > 1e-6) lastY / (lastY - y) else 0.0
        val carry = lastX + (x - lastX) * frac
        val lateral = lastZ + (z - lastZ) * frac
        val descent = Math.toDegrees(kotlin.math.atan2(abs(lastVy), hypot(vx, vz)))

        // Roulement : plus la balle descend a plat, plus elle roule
        val rollFactor = when {
            descent < 25 -> 0.22
            descent < 40 -> 0.10
            else -> 0.04
        }
        return Flight(
            carryM = carry,
            totalM = carry * (1.0 + rollFactor),
            apexM = apex,
            flightTimeS = t,
            descentAngleDeg = descent,
            lateralM = lateral
        )
    }
}
