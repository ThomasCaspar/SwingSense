package com.swingsense.app.analysis

import com.swingsense.app.model.*
import com.swingsense.app.vision.ClubKinematics
import com.swingsense.app.vision.TrackPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Camera derriere le golfeur, dans l'axe de la cible.
 * La balle s'eloigne : la profondeur ne se lit pas dans le deplacement mais
 * dans le RETRECISSEMENT du diametre apparent.
 *
 *     z(t) = f_px * D_balle / (2 * r_px(t))
 *     x(t) = (u - cx) * z / f_px
 *     y(t) = -(v - cy) * z / f_px
 *
 * On reconstruit ainsi un vecteur vitesse 3D complet. La mesure est bruitee
 * (1 px d'erreur sur le rayon = plusieurs % sur z), d'ou les niveaux de
 * confiance plus bas annonces dans le catalogue de metriques.
 */
object DownTheLineAnalyzer {

    fun analyze(
        points: List<TrackPoint>,
        focalPx: Double,
        principalX: Double,
        principalY: Double,
        club: Club,
        clubKin: ClubKinematics?,
        units: UnitSystem,
        selected: Set<Metric>
    ): Pair<List<MetricValue>, List<String>> {

        val warnings = mutableListOf<String>()
        val tiles = mutableListOf<MetricValue>()
        val used = points.take(18)
        if (used.size < 5) {
            warnings += "Trop peu d'images exploitables apres l'impact (${used.size})."
            return tiles to warnings
        }

        val t = DoubleArray(used.size)
        val xw = DoubleArray(used.size)
        val yw = DoubleArray(used.size)
        val zw = DoubleArray(used.size)

        for (i in used.indices) {
            val p = used[i]
            val z = focalPx * Calibration.BALL_DIAMETER_M / (2.0 * p.radiusPx)
            t[i] = p.tSec
            zw[i] = z
            xw[i] = (p.xPx - principalX) * z / focalPx
            yw[i] = -(p.yPx - principalY) * z / focalPx
        }

        val (vz, _, r2z) = Fitting.linear(t, zw)
        val (vx, _, _) = Fitting.linear(t, xw)
        val quadY = Fitting.quadratic(t, yw)
        val vy = quadY[1]

        if (r2z < 0.85) {
            warnings += ("Profondeur bruitee (R2=%.2f) : la mesure de taille apparente est " +
                "sensible au flou de mouvement.").format(r2z)
        }

        val speed = sqrt(vx * vx + vy * vy + vz * vz)
        val startDirDeg = Math.toDegrees(atan2(vx, abs(vz)))
        val launchDeg = Math.toDegrees(atan2(vy, hypot(vx, vz)))

        // Courbure : acceleration laterale residuelle
        val quadX = Fitting.quadratic(t, xw)
        val lateralAcc = 2.0 * quadX[0]
        val curveLabel = when {
            abs(lateralAcc) < 4.0 -> "Droite"
            lateralAcc > 0 -> "Fade / Slice"
            else -> "Draw / Hook"
        }
        if (abs(lateralAcc) >= 4.0) {
            warnings += "Courbe estimee sur une portion de vol tres courte : indicatif seulement."
        }

        fun add(m: Metric, si: Double, display: String? = null) {
            if (m in selected) tiles += MetricValue(
                metric = m,
                value = si,
                display = display ?: Units.format(m.kind, si, units),
                unit = Units.unitLabel(m.kind, units),
                confidence = m.confidence
            )
        }

        add(Metric.BALL_SPEED_DTL, speed)
        add(Metric.LAUNCH_ANGLE_DTL, launchDeg)
        add(
            Metric.START_DIRECTION, startDirDeg,
            "%.1f deg %s".format(abs(startDirDeg), if (startDirDeg >= 0) "Droite" else "Gauche")
        )
        add(Metric.CURVATURE, lateralAcc, curveLabel)

        if (clubKin != null) {
            // En vue derriere le golfeur, le deplacement horizontal de la tete de club
            // juste avant l'impact donne le sens du club path.
            val pathDeg = Math.toDegrees(atan2(clubKin.dirX, abs(clubKin.dirY) + 1e-6))
            val label = if (pathDeg >= 0) "Out-In" else "In-Out"
            add(Metric.CLUB_PATH, pathDeg, "%.1f deg %s".format(abs(pathDeg), label))
        } else {
            warnings += "Tete de club non detectee : club path indisponible."
        }

        return tiles to warnings
    }
}
