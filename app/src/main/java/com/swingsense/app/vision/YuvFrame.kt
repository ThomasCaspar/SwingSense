package com.swingsense.app.vision

import android.media.Image
import kotlin.math.abs

/**
 * Copie CPU legere d'une image YUV_420_888.
 * On garde les 3 plans separes : la detection couleur travaille directement en YUV,
 * sans conversion RGB (gain de temps considerable sur 480+ images).
 */
class YuvFrame(
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
        return if (i < u.size) u[i].toInt() and 0xFF else 128
    }

    fun chromaV(x: Int, yy: Int): Int {
        val i = (yy / 2) * uvRowStride + (x / 2) * uvPixelStride
        return if (i < v.size) v[i].toInt() and 0xFF else 128
    }

    /** Luma sous-echantillonnee, utilisee pour les profils de mouvement. */
    fun downscaledLuma(factor: Int): Pair<ByteArray, Pair<Int, Int>> {
        val w = width / factor
        val h = height / factor
        val out = ByteArray(w * h)
        for (j in 0 until h) {
            val src = j * factor * width
            for (i in 0 until w) {
                out[j * w + i] = y[src + i * factor]
            }
        }
        return out to (w to h)
    }

    companion object {
        fun from(image: Image): YuvFrame {
            val w = image.width
            val h = image.height
            val planes = image.planes

            val yPlane = planes[0]
            val yBuf = yPlane.buffer
            val yRowStride = yPlane.rowStride
            val yOut = ByteArray(w * h)
            if (yRowStride == w) {
                yBuf.get(yOut, 0, minOf(yBuf.remaining(), yOut.size))
            } else {
                val row = ByteArray(yRowStride)
                var off = 0
                for (j in 0 until h) {
                    val n = minOf(yRowStride, yBuf.remaining())
                    if (n <= 0) break
                    yBuf.get(row, 0, n)
                    System.arraycopy(row, 0, yOut, off, minOf(w, n))
                    off += w
                }
            }

            val uBuf = planes[1].buffer
            val vBuf = planes[2].buffer
            val uOut = ByteArray(uBuf.remaining())
            uBuf.get(uOut)
            val vOut = ByteArray(vBuf.remaining())
            vBuf.get(vOut)

            return YuvFrame(
                width = w,
                height = h,
                y = yOut,
                u = uOut,
                v = vOut,
                uvRowStride = planes[1].rowStride,
                uvPixelStride = planes[1].pixelStride
            )
        }
    }
}

/** Energie de mouvement entre deux lumas sous-echantillonnees de meme taille. */
fun motionEnergy(a: ByteArray, b: ByteArray): Double {
    var sum = 0L
    val n = minOf(a.size, b.size)
    for (i in 0 until n) {
        sum += abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
    }
    return sum.toDouble() / n
}
