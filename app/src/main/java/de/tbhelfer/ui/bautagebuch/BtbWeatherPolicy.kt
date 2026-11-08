package de.tbhelfer.ui.bautagebuch

import java.time.Duration
import java.time.LocalDateTime

object BtbWeatherPolicy {
    fun coordinates(lat: Double?, lon: Double?): Pair<Double, Double>? =
        if (lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null

    fun nearestIndex(times: List<LocalDateTime>, target: LocalDateTime): Int? =
        times.indices.minByOrNull { kotlin.math.abs(Duration.between(times[it], target).seconds) }
            ?.takeIf { kotlin.math.abs(Duration.between(times[it], target).seconds) <= 3600 }

    fun cloud(percent: Int?): String? = when {
        percent == null || percent !in 0..100 -> null
        percent <= 5 -> "sonnig / wolkenlos / klar"
        percent <= 25 -> "heiter"
        percent <= 50 -> "leicht bewölkt"
        percent <= 75 -> "wolkig"
        percent <= 90 -> "stark bewölkt"
        else -> "bedeckt"
    }

    fun rain(code: Int?, amount: Double?): String? {
        val mm = amount?.takeIf { it.isFinite() && it >= 0 }
        if (mm != null && mm == 0.0) return "trocken"
        return when (code) {
            51, 53, 55 -> "Nieselregen"
            56, 57, 66, 67 -> "gefrierender Regen"
            61 -> "Regen (leicht)"
            63 -> "Regen (mäßig)"
            65 -> "Regen (stark)"
            80, 81, 82 -> "Schauer"
            68, 69 -> "Schneeregen"
            77 -> "Graupel"
            71, 85 -> "Schneefall (leicht)"
            73 -> "Schneefall (mäßig)"
            75, 86 -> "Schneefall (stark)"
            95 -> "Gewitter"
            96, 99 -> "Hagel"
            else -> when {
                mm == null -> null
                mm < 0.3 -> "Nieselregen"
                mm < 1.5 -> "Regen (leicht)"
                mm < 4 -> "Regen (mäßig)"
                else -> "Regen (stark)"
            }
        }
    }
}
