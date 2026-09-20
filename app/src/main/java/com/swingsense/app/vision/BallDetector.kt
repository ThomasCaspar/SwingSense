package com.swingsense.app.vision

import com.swingsense.app.model.BallColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Detection(
    val cx: Double,
    val cy: Double,
    val radiusPx: Double,
    val pixels: Int,
    val score: Double,
    val meanY: Int,
    val meanU: Int,
    val meanV: Int
)

/**
 * Profil colorimetrique appris pendant la calibration.
 * Une fois la balle verrouillee, on memorise sa teinte moyenne reelle :
 * utile pour le suivi en vol (ROI etroite, prediction de position), la ou
 * une couleur apprise reste un signal fiable.
 */
data class BallProfile(
    val color: BallColor,
    val refY: Int,
    val refU: Int,
    val refV: Int,
    val radiusPx: Double
)

/**
 * Fond de reference capture avant que la balle soit posee (voir
 * camera/CalibrationSession.kt). C'est la piece centrale de la strategie de
 * detection : sur de l'herbe, un seuillage colorimetrique fixe echoue
 * regulierement (texture, ombres, brins plus ou moins clairs). Comparer
 * chaque image a ce fond capture au meme endroit, avant la balle, revient a
 * chercher "ce qui a change" plutot que "ce qui est blanc" - la couleur du
 * fond en dessous n'a alors plus d'importance.
 *
 * Avantage secondaire : si le golfeur est deja dans le champ pendant la
 * capture (pieds, club), il fait partie du fond et n'est donc PAS signale
 * comme un changement une fois la balle posee - seule la balle, nouvelle
 * dans la scene, ressort du diff.
 */
class BackgroundModel private constructor(
    val width: Int,
    val height: Int,
    val y: ByteArray,
    val u: ByteArray,
    val v: ByteArray,
    val uvRowStride: Int,
    val uvPixelStride: Int
) {
    fun luma(x: Int, yy: Int): Int = y[yy * width + x].toInt() and 0xFF

    fun chromaU(x: Int, yy: Int): Int {
        val i = (yy / 2) * uvRowStride + (x / 2) * uvPixelStride
        return if (i in u.indices) u[i].toInt() and 0xFF else 128
    }

    fun chromaV(x: Int, yy: Int): Int {
        val i = (yy / 2) * uvRowStride + (x / 2) * uvPixelStride
        return if (i in v.indices) v[i].toInt() and 0xFF else 128
    }

    /**
     * Redimensionnement (plus proche voisin) pour reutiliser ce fond a une
     * resolution differente - typiquement celle de l'enregistrement haute
     * vitesse, qui ne correspond pas forcement a celle du flux de calibration.
     * Usage ponctuel (une fois par analyse), la simplicite prime ici sur la
     * qualite d'interpolation.
     */
    fun resizedTo(newW: Int, newH: Int): BackgroundModel {
        if (newW == width && newH == height) return this
        val ny = ByteArray(newW * newH)
        for (j in 0 until newH) {
            val sy = (j * height / newH).coerceIn(0, height - 1)
            for (i in 0 until newW) {
                val sx = (i * width / newW).coerceIn(0, width - 1)
                ny[j * newW + i] = y[sy * width + sx]
            }
        }
        val cw = newW / 2 + 1
        val ch = newH / 2 + 1
        val nu = ByteArray(cw * ch)
        val nv = ByteArray(cw * ch)
        for (j in 0 until ch) {
            val sy = ((j * 2) * height / newH).coerceIn(0, height - 1) / 2
            for (i in 0 until cw) {
                val sx = ((i * 2) * width / newW).coerceIn(0, width - 1) / 2
                val ci = sy * uvRowStride + sx * uvPixelStride
                nu[j * cw + i] = if (ci in u.indices) u[ci] else 128.toByte()
                nv[j * cw + i] = if (ci in v.indices) v[ci] else 128.toByte()
            }
        }
        return BackgroundModel(newW, newH, ny, nu, nv, cw, 1)
    }

    companion object {
        /** Moyenne plusieurs images successives : lisse le bruit capteur et le grain de l'herbe. */
        fun average(frames: List<YuvFrame>): BackgroundModel {
            require(frames.isNotEmpty())
            val first = frames[0]
            val w = first.width
            val h = first.height
            val n = frames.size

            val y = ByteArray(w * h)
            for (idx in 0 until w * h) {
                var sum = 0
                for (f in frames) sum += f.y[idx].toInt() and 0xFF
                y[idx] = (sum / n).toByte()
            }
            val u = ByteArray(first.u.size)
            for (idx in u.indices) {
                var sum = 0
                for (f in frames) sum += (f.u.getOrElse(idx) { 128.toByte() }).toInt() and 0xFF
                u[idx] = (sum / n).toByte()
            }
            val v = ByteArray(first.v.size)
            for (idx in v.indices) {
                var sum = 0
                for (f in frames) sum += (f.v.getOrElse(idx) { 128.toByte() }).toInt() and 0xFF
                v[idx] = (sum / n).toByte()
            }
            return BackgroundModel(w, h, y, u, v, first.uvRowStride, first.uvPixelStride)
        }
    }
}

/**
 * Detection de balle : composantes connexes + filtres de forme, sur un
 * masque de candidats. Le masque peut venir de deux strategies :
 *  - seuillage colorimetrique (couleur connue/apprise) - utilise pour le
 *    suivi en vol, dans une ROI etroite et predite ;
 *  - difference a un fond de reference - strategie principale a l'adresse,
 *    independante de la texture du terrain (herbe, tapis, sable).
 *
 * Pas d'OpenCV : traitement Kotlin pur, directement en YUV.
 */
object BallDetector {

    private fun matchesDefault(color: BallColor, y: Int, u: Int, v: Int): Boolean = when (color) {
        BallColor.WHITE -> y > 165 && abs(u - 128) < 24 && abs(v - 128) < 24
        BallColor.YELLOW -> y > 110 && u < 105 && v in 120..180
        BallColor.ORANGE -> y > 80 && u < 115 && v > 160
    }

    private fun matchesProfile(p: BallProfile, y: Int, u: Int, v: Int): Boolean {
        val du = u - p.refU
        val dv = v - p.refV
        val chromaDist = sqrt((du * du + dv * dv).toDouble())
        val lumaOk = y > p.refY - 70
        return chromaDist < 34 && lumaOk
    }

    /** Affinite douce avec une couleur de balle attendue : bonus de score, jamais un filtre bloquant. */
    private fun colorAffinity(color: BallColor?, y: Int, u: Int, v: Int): Double {
        if (color == null) return 0.0
        return if (matchesDefault(color, y, u, v)) 0.25 else 0.0
    }

    /**
     * @param step 1 = pleine resolution, 2 = une ligne/colonne sur deux (balayage large).
     */
    fun detect(
        frame: YuvFrame,
        color: BallColor,
        profile: BallProfile? = null,
        roi: Roi = Roi(0, 0, frame.width, frame.height),
        step: Int = 1,
        minRadius: Double = 3.0,
        maxRadius: Double = 90.0
    ): Detection? = detectWithMask(frame, roi, step, minRadius, maxRadius, colorHint = null) { px, py ->
        val yy = frame.luma(px, py)
        val uu = frame.chromaU(px, py)
        val vv = frame.chromaV(px, py)
        if (profile != null) matchesProfile(profile, yy, uu, vv) else matchesDefault(color, yy, uu, vv)
    }

    /**
     * Detection par difference au fond de reference.
     *
     * `colorHint`, si fourni, ne fait qu'departager deux candidats de score
     * proche (ex. la balle ET le bout d'une chaussure blanche ont bouge) ;
     * il ne rejette jamais un candidat qui ne matcherait pas la couleur -
     * c'est justement ce qui coincait sur l'herbe.
     */
    fun detectByBackground(
        frame: YuvFrame,
        background: BackgroundModel,
        roi: Roi = Roi(0, 0, frame.width, frame.height),
        step: Int = 1,
        minRadius: Double = 3.0,
        maxRadius: Double = 90.0,
        diffThreshold: Int = 50,
        colorHint: BallColor? = null
    ): Detection? = detectWithMask(frame, roi, step, minRadius, maxRadius, colorHint) { px, py ->
        val dy = abs(frame.luma(px, py) - background.luma(px, py))
        val du = abs(frame.chromaU(px, py) - background.chromaU(px, py))
        val dv = abs(frame.chromaV(px, py) - background.chromaV(px, py))
        (dy + du + dv) > diffThreshold
    }

    private inline fun detectWithMask(
        frame: YuvFrame,
        roi: Roi,
        step: Int,
        minRadius: Double,
        maxRadius: Double,
        colorHint: BallColor?,
        isCandidate: (px: Int, py: Int) -> Boolean
    ): Detection? {
        val x0 = max(0, roi.x)
        val y0 = max(0, roi.y)
        val x1 = min(frame.width, roi.x + roi.w)
        val y1 = min(frame.height, roi.y + roi.h)
        if (x1 - x0 < 4 || y1 - y0 < 4) return null

        val w = (x1 - x0 + step - 1) / step
        val h = (y1 - y0 + step - 1) / step
        val mask = BooleanArray(w * h)

        for (j in 0 until h) {
            val py = y0 + j * step
            for (i in 0 until w) {
                val px = x0 + i * step
                mask[j * w + i] = isCandidate(px, py)
            }
        }

        // Composantes connexes (flood fill iteratif, 4-connexite)
        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var best: Detection? = null

        for (start in 0 until w * h) {
            if (!mask[start] || visited[start]) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true

            var count = 0
            var sumX = 0L
            var sumY = 0L
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE
            var sumLy = 0L
            var sumU = 0L
            var sumV = 0L

            while (sp > 0) {
                val idx = stack[--sp]
                val ix = idx % w
                val iy = idx / w
                count++
                sumX += ix
                sumY += iy
                if (ix < minX) minX = ix
                if (ix > maxX) maxX = ix
                if (iy < minY) minY = iy
                if (iy > maxY) maxY = iy

                val px = x0 + ix * step
                val py = y0 + iy * step
                sumLy += frame.luma(px, py)
                sumU += frame.chromaU(px, py)
                sumV += frame.chromaV(px, py)

                if (ix > 0) push(mask, visited, stack, sp, idx - 1).also { sp = it }
                if (ix < w - 1) push(mask, visited, stack, sp, idx + 1).also { sp = it }
                if (iy > 0) push(mask, visited, stack, sp, idx - w).also { sp = it }
                if (iy < h - 1) push(mask, visited, stack, sp, idx + w).also { sp = it }
            }

            if (count < 4) continue

            val bw = (maxX - minX + 1).toDouble()
            val bh = (maxY - minY + 1).toDouble()
            val aspect = min(bw, bh) / max(bw, bh)
            val fill = count / (bw * bh)
            val radius = sqrt(count / Math.PI) * step
            if (radius < minRadius || radius > maxRadius) continue
            // Une balle est ronde et pleine ; un bord d'ombre ou un brin d'herbe qui
            // bouge dans le vent forme une trainee allongee, pas un disque.
            if (aspect < 0.55 || fill < 0.55) continue

            val meanY = (sumLy / count).toInt()
            val meanU = (sumU / count).toInt()
            val meanV = (sumV / count).toInt()
            val bonus = colorAffinity(colorHint, meanY, meanU, meanV)
            val score = aspect * fill * min(1.0, radius / 12.0) * (1.0 + bonus)

            if (best == null || score > best!!.score) {
                best = Detection(
                    cx = x0 + (sumX.toDouble() / count) * step,
                    cy = y0 + (sumY.toDouble() / count) * step,
                    radiusPx = radius,
                    pixels = count,
                    score = score,
                    meanY = meanY,
                    meanU = meanU,
                    meanV = meanV
                )
            }
        }
        return best
    }

    private fun push(mask: BooleanArray, visited: BooleanArray, stack: IntArray, sp: Int, idx: Int): Int {
        if (mask[idx] && !visited[idx]) {
            visited[idx] = true
            stack[sp] = idx
            return sp + 1
        }
        return sp
    }
}

data class Roi(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun clamp(maxW: Int, maxH: Int): Roi {
        val nx = x.coerceIn(0, maxW - 1)
        val ny = y.coerceIn(0, maxH - 1)
        return Roi(nx, ny, w.coerceAtMost(maxW - nx), h.coerceAtMost(maxH - ny))
    }
    companion object {
        fun around(cx: Double, cy: Double, half: Double) =
            Roi((cx - half).toInt(), (cy - half).toInt(), (half * 2).toInt(), (half * 2).toInt())
    }
}
