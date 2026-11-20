package de.tbhelfer.ui.bautagebuch

import java.time.Duration
import java.time.LocalDateTime

object BtbWeatherPolicy {
    /** Explicit start, midpoint and end, also when the shift crosses midnight. */
    fun shiftSlots(start: String?, end: String?): List<String> {
        fun minutes(raw: String?): Int? {
            val parts = raw?.trim()?.split(':') ?: return null
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
        }
        val from = minutes(start) ?: return emptyList()
        val finish = minutes(end) ?: return emptyList()
        val finishAbsolute = if (finish <= from) finish + 24 * 60 else finish
        val middle = from + (finishAbsolute - from) / 2
        return listOf(from, middle, finishAbsolute).map { value ->
            val minute = value % (24 * 60)
            String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
        }
    }

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
