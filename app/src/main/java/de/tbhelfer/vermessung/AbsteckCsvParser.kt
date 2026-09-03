package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.AbsteckpunktEntity
import java.nio.charset.Charset
import java.util.Locale

/**
 * Robuster Import fuer einfache Absteckpunkt-CSV-Dateien.
 *
 * Unterstuetzt insbesondere die gelieferten Dateien mit Spalten wie
 * Pkt.Nr, GK.Y..m, GK.X..m, GK.Z..m, Pkt.Erl.L.
 * Ein KM wird daraus nicht abgeleitet. Codes/Bezeichnungen bleiben reine
 * Punktinformationen.
 */
object AbsteckCsvParser {

    fun istAbsteckpunktCsv(bytes: ByteArray): Boolean {
        val text = dekodiereText(bytes)
        val headerLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        val separator = ermittleSeparator(headerLine)
        val header = splitCsvLine(headerLine, separator).map { normalisiereHeader(it) }
        val punkt = findeIndex(header, "pktnr", "punktnr", "punktnummer") >= 0
        val y = findeIndex(header, "gkym", "gky", "y", "rechtswert") >= 0
        val x = findeIndex(header, "gkxm", "gkx", "x", "hochwert") >= 0
        return punkt && x && y
    }

    fun parse(dateiname: String, bytes: ByteArray): AbsteckCsvParseResult {
        val text = dekodiereText(bytes)
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.isNotEmpty()) { "CSV ist leer." }

        val separator = ermittleSeparator(lines.first())
        val header = splitCsvLine(lines.first(), separator).map { normalisiereHeader(it) }
        val idxPunkt = findeIndex(header, "pktnr", "punktnr", "punktnummer")
        val idxY = findeIndex(header, "gkym", "gky", "y", "rechtswert")
        val idxX = findeIndex(header, "gkxm", "gkx", "x", "hochwert")
        val idxZ = findeIndex(header, "gkzm", "gkz", "z", "hoehe", "höhe")
        val idxCode = findeIndex(header, "pkterll", "pkterllage", "erllage", "code", "typ")

        require(idxPunkt >= 0 && idxY >= 0 && idxX >= 0) {
            "CSV-Spalten fuer Punktnummer und GK.Y/GK.X wurden nicht erkannt."
        }

        val strecke = dateiname.findeStreckeOderNull()
        val punkte = mutableListOf<AbsteckpunktEntity>()
        val warnungen = mutableListOf<String>()

        lines.drop(1).forEachIndexed { index, raw ->
            val cols = splitCsvLine(raw, separator)
            fun wert(i: Int): String? = if (i >= 0) cols.getOrNull(i)?.trim()?.trim('"')?.takeIf { it.isNotBlank() } else null

            val punktName = wert(idxPunkt) ?: return@forEachIndexed
            val xDbRef = wert(idxY)?.toDoubleDeOrNull()
            val yDbRef = wert(idxX)?.toDoubleDeOrNull()
            if (xDbRef == null || yDbRef == null || !istDbRefPlausibel(xDbRef, yDbRef)) {
                warnungen += "Zeile ${index + 2}: ungueltige DB_REF-Koordinate"
                return@forEachIndexed
            }
            val hoehe = wert(idxZ)?.toDoubleDeOrNull()
            val code = wert(idxCode)
            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(xDbRef, yDbRef)
            punkte += AbsteckpunktEntity(
                punktName = punktName,
                strecke = strecke,
                gleis = null,
                kmMeter = null,
                xDbRef = xDbRef,
                yDbRef = yDbRef,
                lat = gps?.lat,
                lon = gps?.lon,
                hoeheM = hoehe,
                typ = code?.let { "CSV-$it" } ?: "CSV",
                quelleDatei = dateiname,
                rawLine = raw
            )
        }

        require(punkte.isNotEmpty()) { "Keine Absteckpunkte aus CSV gefunden." }
        return AbsteckCsvParseResult(punkte, warnungen.distinct())
    }

    private fun ermittleSeparator(header: String): Char {
        val kandidaten = listOf(',', ';', '\t')
        return kandidaten.maxByOrNull { c -> header.count { it == c } } ?: ','
    }

    private fun splitCsvLine(line: String, separator: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == separator && !inQuotes -> {
                    result += current.toString()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        result += current.toString()
        return result
    }

    private fun normalisiereHeader(value: String): String = value
        .trim().trim('"')
        .lowercase(Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
        .replace(Regex("[^a-z0-9]+"), "")

    private fun findeIndex(header: List<String>, vararg namen: String): Int =
        header.indexOfFirst { it in namen }

    private fun String.findeStreckeOderNull(): String? =
        Regex("(?<![A-Z0-9])([1-9]\\d{3})(?!\\d)")
            .find(uppercase(Locale.GERMANY))
            ?.groupValues?.getOrNull(1)
            ?.takeIf { it != "3000" }

    private fun istDbRefPlausibel(x: Double, y: Double): Boolean =
        x in 3_000_000.0..6_000_000.0 && y in 4_000_000.0..6_500_000.0

    private fun String.toDoubleDeOrNull(): Double? =
        replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun dekodiereText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if ('\uFFFD' in utf8) String(bytes, Charset.forName("ISO-8859-1")) else utf8
    }
}

data class AbsteckCsvParseResult(
    val punkte: List<AbsteckpunktEntity>,
    val warnungen: List<String> = emptyList()
)
