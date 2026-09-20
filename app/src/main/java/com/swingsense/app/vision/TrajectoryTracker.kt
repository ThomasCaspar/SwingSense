package com.swingsense.app.vision

import com.swingsense.app.model.BallColor
import kotlin.math.abs
import kotlin.math.hypot

data class TrackPoint(
    val frame: Int,
    val tSec: Double,
    val xPx: Double,
    val yPx: Double,
    val radiusPx: Double
)

/**
 * Suit la balle image apres image a partir de sa position calibree.
 *
 * Strategie :
 *  1. tant que la balle est immobile (adresse), on la retrouve au meme endroit ;
 *  2. des qu'elle bouge de plus d'un rayon entre deux images -> impact detecte ;
 *  3. ensuite on predit la position suivante (vitesse constante) et on cherche
 *     dans une ROI centree sur la prediction, qui grandit tant qu'on ne trouve rien.
 *
 * La balle en vol est floue (motion blur) : on relache donc les criteres de forme
 * en autorisant un rayon plus petit et une ROI large.
 */
class TrajectoryTracker(
    private val color: BallColor,
    private val profile: BallProfile,
    private val fps: Double
) {
    val points = mutableListOf<TrackPoint>()
    var impactFrame: Int = -1
        private set
    var lostFrame: Int = -1
        private set

    private var lastX = Double.NaN
    private var lastY = Double.NaN
    private var vx = 0.0
    private var vy = 0.0
    private var missStreak = 0
    private var restFrames = 0

    /** @return false quand il faut arreter le decodage (balle sortie du champ depuis assez longtemps) */
    fun onFrame(index: Int, frame: YuvFrame): Boolean {
        val dt = 1.0 / fps
        val searchHalf = when {
            lastX.isNaN() -> 0.0
            impactFrame < 0 -> profile.radiusPx * 6
            else -> (profile.radiusPx * 4 + hypot(vx, vy) * dt * 2 + missStreak * profile.radiusPx * 3)
        }

        val det = if (lastX.isNaN()) {
            BallDetector.detect(frame, color, profile, step = 2)
        } else {
            val predX = lastX + vx * dt
            val predY = lastY + vy * dt
            BallDetector.detect(
                frame, color, profile,
                roi = Roi.around(predX, predY, searchHalf).clamp(frame.width, frame.height),
                step = 1,
                minRadius = profile.radiusPx * 0.35,
                maxRadius = profile.radiusPx * 2.5
            ) ?: BallDetector.detect(frame, color, profile, step = 2, minRadius = profile.radiusPx * 0.35)
        }

        if (det == null) {
            missStreak++
            // Apres impact, 6 images sans balle = elle est sortie du champ.
            if (impactFrame >= 0 && missStreak > 6) {
                if (lostFrame < 0) lostFrame = index
                return false
            }
            return true
        }

        missStreak = 0
        if (!lastX.isNaN()) {
            val d = hypot(det.cx - lastX, det.cy - lastY)
            if (impactFrame < 0) {
                if (d > profile.radiusPx * 1.2) {
                    impactFrame = index
                } else {
                    restFrames++
                }
            }
            vx = (det.cx - lastX) / dt
            vy = (det.cy - lastY) / dt
        }
        lastX = det.cx
        lastY = det.cy

        if (impactFrame >= 0) {
            points.add(
                TrackPoint(
                    frame = index,
                    tSec = (index - impactFrame) / fps,
                    xPx = det.cx,
                    yPx = det.cy,
                    radiusPx = det.radiusPx
                )
            )
        }

        // Balle qui touche le bord de l'image -> on arrete proprement
        val margin = profile.radiusPx * 1.5
        if (impactFrame >= 0 &&
            (det.cx < margin || det.cx > frame.width - margin ||
                det.cy < margin || det.cy > frame.height - margin)
        ) {
            lostFrame = index
            return false
        }
        return true
    }

    fun hasEnoughData(): Boolean = points.size >= 4

    /** Ecart moyen au modele lineaire, sert d'indicateur de qualite du suivi. */
    fun residual(): Double {
        if (points.size < 3) return Double.NaN
        val n = points.size
        var sx = 0.0; var sy = 0.0; var st = 0.0; var stt = 0.0; var stx = 0.0
        for (p in points) { sx += p.xPx; st += p.tSec; stt += p.tSec * p.tSec; stx += p.tSec * p.xPx; sy += p.yPx }
        val denom = n * stt - st * st
        if (abs(denom) < 1e-9) return Double.NaN
        val a = (n * stx - st * sx) / denom
        val b = (sx - a * st) / n
        var err = 0.0
        for (p in points) err += abs(p.xPx - (a * p.tSec + b))
        return err / n
    }
}
