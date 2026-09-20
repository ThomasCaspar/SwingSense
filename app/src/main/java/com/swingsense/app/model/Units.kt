package com.swingsense.app.model

/**
 * Conversion et formatage des unites.
 *
 * Regle : tout le pipeline de calcul travaille en SI (m, m/s, degres, rpm).
 * La conversion n'intervient qu'au moment de fabriquer la chaine affichee,
 * et la valeur SI reste stockee dans MetricValue.value pour tout traitement
 * ulterieur (historique, comparaisons, export).
 */
object Units {

    private const val MS_TO_KMH = 3.6
    private const val MS_TO_MPH = 2.2369363
    private const val M_TO_YARDS = 1.0936133

    fun unitLabel(kind: MetricKind, system: UnitSystem): String = when (kind) {
        MetricKind.SPEED -> if (system == UnitSystem.METRIC) "km/h" else "mph"
        MetricKind.DISTANCE -> if (system == UnitSystem.METRIC) "m" else "yds"
        MetricKind.ANGLE -> "deg"
        MetricKind.SPIN -> "rpm"
        MetricKind.RATIO -> ""
        MetricKind.TEXT -> ""
    }

    /** Convertit une valeur SI vers le systeme demande. */
    fun convert(kind: MetricKind, si: Double, system: UnitSystem): Double = when (kind) {
        MetricKind.SPEED -> if (system == UnitSystem.METRIC) si * MS_TO_KMH else si * MS_TO_MPH
        MetricKind.DISTANCE -> if (system == UnitSystem.METRIC) si else si * M_TO_YARDS
        else -> si
    }

    fun format(kind: MetricKind, si: Double, system: UnitSystem): String {
        val v = convert(kind, si, system)
        return when (kind) {
            MetricKind.SPEED -> "%.1f".format(v)
            MetricKind.DISTANCE -> "%.1f".format(v)
            MetricKind.ANGLE -> "%.1f".format(v)
            MetricKind.SPIN -> "%.0f".format(v)
            MetricKind.RATIO -> "%.2f".format(v)
            MetricKind.TEXT -> "%.1f".format(v)
        }
    }

    /** Distance courte utilisee dans les textes d'aide (calibration, placement). */
    fun shortDistance(meters: Double, system: UnitSystem): String =
        if (system == UnitSystem.METRIC) "%.1f m".format(meters)
        else "%.1f ft".format(meters * 3.28084)
}
