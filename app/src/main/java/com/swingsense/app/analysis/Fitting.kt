package com.swingsense.app.analysis

/** Petits outils de regression utilises par les analyseurs. */
object Fitting {

    /** Regression lineaire y = a*t + b. Retourne (a, b, r2). */
    fun linear(t: DoubleArray, y: DoubleArray): Triple<Double, Double, Double> {
        val n = t.size
        require(n == y.size && n >= 2)
        var st = 0.0; var sy = 0.0; var stt = 0.0; var sty = 0.0
        for (i in 0 until n) { st += t[i]; sy += y[i]; stt += t[i] * t[i]; sty += t[i] * y[i] }
        val denom = n * stt - st * st
        val a = if (kotlin.math.abs(denom) < 1e-12) 0.0 else (n * sty - st * sy) / denom
        val b = (sy - a * st) / n
        var ssTot = 0.0; var ssRes = 0.0
        val mean = sy / n
        for (i in 0 until n) {
            val pred = a * t[i] + b
            ssRes += (y[i] - pred) * (y[i] - pred)
            ssTot += (y[i] - mean) * (y[i] - mean)
        }
        val r2 = if (ssTot < 1e-12) 1.0 else 1.0 - ssRes / ssTot
        return Triple(a, b, r2)
    }

    /**
     * Regression quadratique y = c2*t^2 + c1*t + c0 (moindres carres, systeme 3x3).
     * Sert a extraire la vitesse verticale initiale quand la gravite est visible.
     */
    fun quadratic(t: DoubleArray, y: DoubleArray): DoubleArray {
        val n = t.size
        var s0 = n.toDouble(); var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
        var sy = 0.0; var sty = 0.0; var stty = 0.0
        for (i in 0 until n) {
            val ti = t[i]
            val t2 = ti * ti
            s1 += ti; s2 += t2; s3 += t2 * ti; s4 += t2 * t2
            sy += y[i]; sty += ti * y[i]; stty += t2 * y[i]
        }
        val a = arrayOf(
            doubleArrayOf(s4, s3, s2, stty),
            doubleArrayOf(s3, s2, s1, sty),
            doubleArrayOf(s2, s1, s0, sy)
        )
        return solve3(a) ?: doubleArrayOf(0.0, 0.0, 0.0)
    }

    private fun solve3(m: Array<DoubleArray>): DoubleArray? {
        for (col in 0 until 3) {
            var pivot = col
            for (r in col until 3) if (kotlin.math.abs(m[r][col]) > kotlin.math.abs(m[pivot][col])) pivot = r
            if (kotlin.math.abs(m[pivot][col]) < 1e-12) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (r in 0 until 3) {
                if (r == col) continue
                val f = m[r][col] / m[col][col]
                for (c in col until 4) m[r][c] -= f * m[col][c]
            }
        }
        return doubleArrayOf(m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2])
    }

    /** Filtre median simple (elimine les detections aberrantes du rayon apparent). */
    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}
