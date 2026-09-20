package com.swingsense.app.analysis

import com.swingsense.app.model.*
import com.swingsense.app.vision.ClubKinematics
import com.swingsense.app.vision.TrackPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Camera face au golfeur, perpendiculaire a la ligne de jeu.
 * Le vol de la balle se fait (quasi) dans le plan image : une simple echelle
 * metres/pixel suffit pour passer des pixels aux m/s.
 *
 * Tous les calculs restent en SI ; la conversion vers km/h ou mph n'a lieu
 * qu'au moment de fabriquer la chaine affichee (voir model/Units.kt).
 */
object FaceOnAnalyzer {

    fun analyze(
        points: List<TrackPoint>,
        metersPerPixel: Double,
        club: Club,
        clubKin: ClubKinematics?,
        tempo: Double?,
        fps: Double,
        units: UnitSystem,
        selected: Set<Metric>
    ): Pair<List<MetricValue>, List<String>> {

        val warnings = mutableListOf<String>()
        val tiles = mutableListOf<MetricValue>()

        // On garde les premieres images apres impact : c'est la que la balle
        // est la plus proche du plan calibre (moins d'erreur de perspective).
        val used = points.take(14)
        if (used.size < 4) {
            warnings += "Trop peu d'images exploitables apres l'impact (${used.size})."
            return tiles to warnings
        }

        val t = DoubleArray(used.size) { used[it].tSec }
        val xPx = DoubleArray(used.size) { used[it].xPx }
        val yPx = DoubleArray(used.size) { used[it].yPx }

        val (axPx, _, r2x) = Fitting.linear(t, xPx)
        val quad = Fitting.quadratic(t, yPx)      // c2, c1, c0 en pixels, y vers le bas
        val vyPx = -quad[1]                        // vers le haut = positif
        val c2 = quad[0]

        // Verification croisee : la gravite doit apparaitre dans la courbure.
        // g_px = 2*c2  ->  echelle implicite = g / (2*c2). On compare a la calibration.
        if (abs(c2) > 1e-3) {
            val implicitMpp = 9.80665 / (2.0 * c2)
            val ratio = implicitMpp / metersPerPixel
            if (ratio < 0.4 || ratio > 2.5) {
                warnings += ("Echelle balle/gravite incoherente (x%.2f) : verifiez que la " +
                    "camera est bien perpendiculaire a la ligne de jeu.").format(ratio)
            }
        }

        val vx = abs(axPx) * metersPerPixel
        val vy = vyPx * metersPerPixel
        val ballSpeed = hypot(vx, vy)
        val launchDeg = Math.toDegrees(atan2(vy, vx))

        if (r2x < 0.95) warnings += "Suivi horizontal bruite (R2=%.2f).".format(r2x)
        if (ballSpeed < 5.0 || ballSpeed > 110.0) {
            warnings += "Vitesse de balle hors plage plausible : calibration probablement fausse."
        }

        // --- Club ---
        var clubSpeed = Double.NaN
        var attackDeg = 0.0
        if (clubKin != null) {
            clubSpeed = clubKin.speedPxPerSec * metersPerPixel
            attackDeg = Math.toDegrees(atan2(-clubKin.dirY, abs(clubKin.dirX)))
            if (clubKin.samples < 5) warnings += "Peu d'images pour la tete de club : vitesse de club approximative."
        } else {
            warnings += "Tete de club non detectee : vitesse de club et angle d'attaque indisponibles."
        }

        val backspin = Ballistics.estimateBackspin(club, ballSpeed, attackDeg)
        val flight = Ballistics.simulate(
            speedMs = ballSpeed,
            launchDeg = launchDeg,
            azimuthDeg = 0.0,
            backspinRpm = backspin,
            sideSpinRpm = 0.0
        )

        // La valeur stockee reste en SI ; seul l'affichage est converti.
        fun add(m: Metric, si: Double, display: String? = null) {
            if (m in selected) tiles += MetricValue(
                metric = m,
                value = si,
                display = display ?: Units.format(m.kind, si, units),
                unit = Units.unitLabel(m.kind, units),
                confidence = m.confidence
            )
        }

        add(Metric.BALL_SPEED, ballSpeed)
        add(Metric.LAUNCH_ANGLE, launchDeg)
        if (!clubSpeed.isNaN()) {
            add(Metric.CLUB_SPEED, clubSpeed)
            add(Metric.SMASH_FACTOR, ballSpeed / clubSpeed)
            add(
                Metric.ATTACK_ANGLE, attackDeg,
                "%.1f deg %s".format(abs(attackDeg), if (attackDeg < 0) "Down" else "Up")
            )
        }
        add(Metric.APEX, flight.apexM)
        add(Metric.CARRY, flight.carryM)
        add(Metric.TOTAL, flight.totalM)
        add(Metric.BACKSPIN, backspin)
        tempo?.let { add(Metric.TEMPO, it, "%.1f : 1".format(it)) }

        return tiles to warnings
    }
}
