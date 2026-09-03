package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.AbsteckpunktEntity
import de.tbhelfer.datenbank.TrassenAchseEntity
import java.nio.charset.Charset

/**
 * Parser fuer einfache KOR-Textdateien aus der Absteckung.
 *
 * Erwartetes Zeilenformat aus den gelieferten *.kor.txt-Dateien:
 * punktName xDbRef yDbRef hoehe code
 * Beispiel:
 * 201 4406608.40275 5355994.48276 477.68171 3
 *
 * Die Datei enthaelt keine fertigen GPS-Koordinaten und meistens keinen km-Wert.
 * Gespeichert werden daher Punktname, DB_REF-X/Y, Hoehe und der rohe Code.
 */
object KorDateiParser {

    fun parse(dateiname: String, bytes: ByteArray): KorParseResult {
        val text = dekodiereText(bytes)
        val punkte = mutableListOf<AbsteckpunktEntity>()
        val warnungen = mutableListOf<String>()
        val streckeAusDateiname = dateiname.findeStreckeOderNull()

        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEachIndexed
            if (line.startsWith("#") || line.startsWith("//")) return@forEachIndexed

            val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.size < 4) {
                warnungen += "Zeile ${index + 1}: zu wenig Spalten"
                return@forEachIndexed
            }

            val punktName = tokens.getOrNull(0)?.takeIf { it.isNotBlank() }
            val x = tokens.getOrNull(1)?.toDoubleDeOrNull()
            val y = tokens.getOrNull(2)?.toDoubleDeOrNull()
            val hoehe = tokens.getOrNull(3)?.toDoubleDeOrNull()
            val code = tokens.getOrNull(4)?.takeIf { it.isNotBlank() }

            if (punktName == null || x == null || y == null) {
                warnungen += "Zeile ${index + 1}: Pflichtwerte fehlen"
                return@forEachIndexed
            }
            if (x !in 3_000_000.0..6_000_000.0 || y !in 4_000_000.0..6_500_000.0) {
                warnungen += "Zeile ${index + 1}: unplausible DB_REF-Koordinate"
                return@forEachIndexed
            }

            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(x, y)

            punkte += AbsteckpunktEntity(
                punktName = punktName,
                strecke = streckeAusDateiname,
                gleis = null,
                kmMeter = null,
                xDbRef = x,
                yDbRef = y,
                lat = gps?.lat,
                lon = gps?.lon,
                hoeheM = hoehe,
                typ = code?.let { "KOR-$it" } ?: "KOR",
                quelleDatei = dateiname,
                rawLine = raw
            )
        }

        require(punkte.isNotEmpty()) {
            "Keine Absteckpunkte gefunden. Erwartet wird eine .kor.txt mit Punktnummer, DB_REF-X/Y und Hoehe."
        }

        return KorParseResult(
            punkte = punkte,
            warnungen = warnungen
        )
    }

    private fun dekodiereText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if ('\uFFFD' in utf8) {
            String(bytes, Charset.forName("ISO-8859-1"))
        } else {
            utf8
        }
    }

    private fun String.toDoubleDeOrNull(): Double? =
        replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun String.findeStreckeOderNull(): String? {
        val treffer = Regex("(?<![A-Z0-9])([1-9]\\d{3})(?!\\d)").find(this)?.groupValues?.getOrNull(1)
        return treffer?.takeIf { it != "3000" }
    }


}

data class KorParseResult(
    val punkte: List<AbsteckpunktEntity>,
    val warnungen: List<String> = emptyList()
)
