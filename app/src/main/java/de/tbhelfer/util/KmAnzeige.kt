package de.tbhelfer.util

import java.util.Locale
import kotlin.math.abs

/** Einheitliche, nutzerlesbare KM-Darstellung für BTB, Widget, PDF und Exporte. */
object KmAnzeige {
    private val reOrt = Regex("""^@ORT=\d+\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val reAllgemein = Regex("""^@ALLGEMEIN\b\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val reKm = Regex(
        """^@KM=([0-9]+(?:[.,][0-9]+)?)(?:\s*[-–]\s*([0-9]+(?:[.,][0-9]+)?))?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val reZeit = Regex("""^(\d{1,2}:\d{2}(?:\s*[-–]\s*\(?\s*\d{1,2}:\d{2}\s*\)?)?)\s+(.+)$""")

    data class Zerlegt(
        val text: String,
        val kmVon: Double? = null,
        val kmBis: Double? = null
    )

    fun wert(km: Double): String = String.format(Locale.GERMANY, "%.3f", km)

    fun bereich(kmVon: Double?, kmBis: Double? = null, inKlammern: Boolean = true): String? {
        val first = kmVon ?: kmBis ?: return null
        val second = kmBis?.takeIf { abs(it - first) > 0.0000001 }
        val core = if (second != null) "KM ${wert(first)}–${wert(second)}" else "KM ${wert(first)}"
        return if (inKlammern) "($core)" else core
    }

    /** Entfernt interne Marker und liefert KM separat zurück. */
    fun zerlegen(raw: String?): Zerlegt {
        var line = raw.orEmpty().trim().removePrefix("•").trim()
        var kmVon: Double? = null
        var kmBis: Double? = null
        while (line.isNotBlank()) {
            val ort = reOrt.matchEntire(line)
            if (ort != null) {
                line = ort.groupValues.getOrNull(1).orEmpty().trim()
                continue
            }
            val allgemein = reAllgemein.matchEntire(line)
            if (allgemein != null) {
                line = allgemein.groupValues.getOrNull(1).orEmpty().trim()
                continue
            }
            val km = reKm.matchEntire(line)
            if (km != null) {
                kmVon = km.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
                kmBis = km.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                    ?.replace(',', '.')?.toDoubleOrNull()
                line = km.groupValues.getOrNull(3).orEmpty().trim().removePrefix("•").trim()
                continue
            }
            break
        }
        return Zerlegt(line, kmVon, kmBis)
    }

    /**
     * Formatiert eine gespeicherte BTB-Zeile für die Anzeige.
     * Aus "@KM=3.123-4.123 13:00 Text" wird
     * "13:00 KM 3,123–4,123 · Text".
     */
    fun zeile(raw: String?): String {
        val z = zerlegen(raw)
        val km = bereich(z.kmVon, z.kmBis, inKlammern = false)
        val text = z.text.trim().removePrefix("•").trim()
        if (km == null) return text
        val zeit = reZeit.matchEntire(text)
        return if (zeit != null) {
            "${zeit.groupValues[1]} $km · ${zeit.groupValues[2]}"
        } else if (text.isNotBlank()) {
            "$km · $text"
        } else km
    }

    fun mehrzeilig(raw: String?): String = raw.orEmpty().lines()
        .map(::zeile)
        .filter(String::isNotBlank)
        .joinToString("\n")
}
