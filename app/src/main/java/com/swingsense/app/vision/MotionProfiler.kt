package com.swingsense.app.vision

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Deux mesures qui ne passent pas par la balle :
 *  - le profil d'energie de mouvement de tout le swing (-> tempo) ;
 *  - le suivi de la tete de club juste avant l'impact (-> vitesse de club,
 *    angle d'attaque, club path).
 *
 * La tete de club est trouvee par difference d'images : c'est l'objet le plus
 * rapide de la scene, et le plus bas (proche du sol) a l'approche de l'impact.
 */
class MotionProfiler(private val downscale: Int = 8) {

    private var prev: ByteArray? = null
    private var dw = 0
    private var dh = 0

    val energy = mutableListOf<Double>()
    private val clubPoints = mutableListOf<Triple<Int, Double, Double>>() // frame, x, y (pleine resolution)

    fun onFrame(index: Int, frame: YuvFrame, ballX: Double, ballY: Double, ballRadius: Double) {
        val (small, dims) = frame.downscaledLuma(downscale)
        dw = dims.first
        dh = dims.second
        val p = prev
        if (p != null && p.size == small.size) {
            energy.add(motionEnergy(p, small))
            findClubHead(index, p, small, frame, ballX, ballY, ballRadius)
        } else {
            energy.add(0.0)
        }
        prev = small
    }

    /**
     * Cherche le barycentre des pixels ayant le plus bouge, dans une bande
     * autour de la balle (+- 8 rayons horizontalement, du haut de balle au sol).
     */
    private fun findClubHead(
        index: Int,
        prevSmall: ByteArray,
        small: ByteArray,
        frame: YuvFrame,
        ballX: Double,
        ballY: Double,
        ballRadius: Double
    ) {
        val bx = (ballX / downscale).toInt()
        val by = (ballY / downscale).toInt()
        val halfW = ((ballRadius * 14) / downscale).toInt().coerceAtLeast(6)
        val halfH = ((ballRadius * 8) / downscale).toInt().coerceAtLeast(4)

        var sumW = 0.0
        var sumX = 0.0
        var sumY = 0.0
        val x0 = (bx - halfW).coerceIn(0, dw - 1)
        val x1 = (bx + halfW).coerceIn(0, dw - 1)
        val y0 = (by - halfH).coerceIn(0, dh - 1)
        val y1 = (by + halfH).coerceIn(0, dh - 1)

        for (j in y0..y1) {
            for (i in x0..x1) {
                val idx = j * dw + i
                val d = abs((small[idx].toInt() and 0xFF) - (prevSmall[idx].toInt() and 0xFF))
                if (d > 26) {
                    // On ignore la zone immediate de la balle elle-meme
                    val distBall = hypot((i - bx).toDouble(), (j - by).toDouble()) * downscale
                    if (distBall < ballRadius * 1.6) continue
                    val wgt = d.toDouble()
                    sumW += wgt
                    sumX += i * wgt
                    sumY += j * wgt
                }
            }
        }
        if (sumW > 120) {
            clubPoints.add(
                Triple(index, (sumX / sumW) * downscale, (sumY / sumW) * downscale)
            )
        }
    }

    /**
     * Vitesse de la tete de club (px/s) sur les N dernieres images avant impact,
     * + direction du deplacement (pour angle d'attaque / club path).
     */
    fun clubHeadKinematics(impactFrame: Int, fps: Double, framesBefore: Int = 10): ClubKinematics? {
        val window = clubPoints.filter { it.first in (impactFrame - framesBefore) until impactFrame }
        if (window.size < 3) return null
        val first = window.first()
        val last = window.last()
        val dt = (last.first - first.first) / fps
        if (dt <= 0.0) return null
        val dx = last.second - first.second
        val dy = last.third - first.third
        return ClubKinematics(
            speedPxPerSec = hypot(dx, dy) / dt,
            dirX = dx / dt,
            dirY = dy / dt,
            samples = window.size
        )
    }

    /**
     * Tempo = duree montee / duree descente.
     * Sur la courbe d'energie : debut du mouvement, creux au sommet du backswing,
     * pic a l'impact.
     */
    fun tempo(impactFrame: Int): Double? {
        if (energy.size < 20 || impactFrame < 15) return null
        val baseline = energy.take(8).average()
        val threshold = baseline + (energy.max() - baseline) * 0.12

        var start = -1
        for (i in energy.indices) {
            if (i >= impactFrame) break
            if (energy[i] > threshold) { start = i; break }
        }
        if (start < 0 || impactFrame - start < 10) return null

        // Sommet du backswing : minimum local d'energie entre start et impact
        var top = start
        var minE = Double.MAX_VALUE
        for (i in (start + 5) until (impactFrame - 3)) {
            if (energy[i] < minE) { minE = energy[i]; top = i }
        }
        val back = (top - start).toDouble()
        val down = (impactFrame - top).toDouble()
        if (down < 2.0) return null
        return back / down
    }
}

data class ClubKinematics(
    val speedPxPerSec: Double,
    val dirX: Double,
    val dirY: Double,
    val samples: Int
)
