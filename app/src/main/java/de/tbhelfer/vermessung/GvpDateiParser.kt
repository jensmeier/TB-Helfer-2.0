package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.GvpPointEntity
import de.tbhelfer.datenbank.TrassenAchseEntity
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.abs

/**
 * Parser fuer Festpunkt-/GVP-Dateien mit DB_REF-Koordinaten.
 *
 * Unterstuetzte gelieferte Varianten:
 * - GVP-/Streifen-TXT, z. B. Gessertshausen
 * - KF.TXT-Koordinatenverzeichnisse, z. B. Pasing/Harburg
 * - KV-Festpunktdateien mit Station, z. B. 8771
 *
 * Entscheidend fuer eine automatische KM-Kalibrierung ist nicht die Dateiendung,
 * sondern ob pro Punkt eine echte Station/KM vorhanden ist. Eine KF.TXT, deren
 * KM-Spalte komplett 0.00000 ist, liefert deshalb Punkte, aber keine KM-Quelle.
 */
object GvpDateiParser {

    fun parse(dateiname: String, bytes: ByteArray): GvpParseResult {
        val text = dekodiereText(bytes)
        val warnungen = mutableListOf<String>()

        val istKv = dateiname.lowercase(Locale.GERMANY).endsWith(".kv")
        val istKfText = dateiname.lowercase(Locale.GERMANY).endsWith(".kf.txt") ||
            text.contains("Koordinatenverzeichnis", ignoreCase = true)
        val kv = if (istKv) parseKv(dateiname, text, warnungen) else emptyList()
        val standard = when {
            kv.isNotEmpty() -> emptyList()
            istKfText -> parseKfText(dateiname, text, warnungen)
            else -> parseStandardOderKf(dateiname, text, warnungen)
        }
        var punkte = if (kv.isNotEmpty()) kv else standard

        require(punkte.isNotEmpty()) {
            "Keine GVP/Festpunkte gefunden. Erwartet wird GVP-/KF.TXT oder eine KV-Datei mit DB_REF-Koordinaten."
        }

        // Harburg zeigt den wichtigen Sonderfall: KM-Spalte ist vorhanden, aber in
        // der gesamten KF.TXT 0.00000. Dann darf daraus keine gruen markierte,
        // automatische KM-Kalibrierung entstehen.
        val kmWerte = punkte.mapNotNull { it.kmMeter }
        if (kmWerte.isNotEmpty() && kmWerte.all { abs(it) <= 0.001 }) {
            punkte = punkte.map { it.copy(kmMeter = null) }
            warnungen += "KM-/Stationsspalte ist komplett 0.00000 und wird nicht als KM-Quelle verwendet."
        }

        return GvpParseResult(
            punkte = punkte,
            warnungen = warnungen.distinct()
        )
    }


    /**
     * iGeo-KF.TXT ist ein Festbreitenformat. Gerade wenn KM und Strecke leer sind,
     * darf es nicht ueber whitespace-Tokens gelesen werden: sonst rutschen Punktname,
     * Strecke und KM nach links. Patch45ZN behaelt deshalb jeden plausiblen
     * Koordinatenpunkt; Strecke und KM duerfen null sein.
     */
    private fun parseKfText(
        dateiname: String,
        text: String,
        warnungen: MutableList<String>
    ): List<GvpPointEntity> {
        val punkte = mutableListOf<GvpPointEntity>()

        text.lineSequence().forEach { raw ->
            if (raw.length < 54) return@forEach
            val punktNr = raw.safeSlice(0, 14).trim().takeIf { it.isNotBlank() } ?: return@forEach
            if (punktNr.equals("Pkt-Nr.", ignoreCase = true) || punktNr.all { it == '-' }) return@forEach

            val x = raw.safeSlice(14, 29).trim().toDoubleDeOrNull() ?: return@forEach
            val y = raw.safeSlice(29, 46).trim().toDoubleDeOrNull() ?: return@forEach
            if (!istDbRefPlausibel(x, y)) return@forEach

            val hoehe = raw.safeSlice(46, 59).trim().toDoubleDeOrNull()
            val km = raw.safeSlice(59, 72).trim().toDoubleDeOrNull()
            val lageName = raw.safeSlice(72, 90).trim().takeIf { it.isNotBlank() }
            val strecke = raw.safeSlice(90, 98).trim()
                .takeIf { it.matches(Regex("[1-9]\\d{3}")) }
            val typ = raw.safeSlice(104, 120).trim()
                .split(Regex("\\s+"))
                .firstOrNull { token -> token.any { it.isLetter() } }
            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(x, y)

            punkte += GvpPointEntity(
                strecke = strecke,
                punktName = lageName ?: punktNr,
                kmMeter = km,
                xDbRef = x,
                yDbRef = y,
                lat = gps?.lat,
                lon = gps?.lon,
                hoeheM = hoehe,
                typ = typ,
                klasse = punktNr,
                koordinatensystem = TrassenAchseEntity.KOORD_DB_REF2016_4,
                quelleDatei = dateiname,
                rawLine = raw
            )
        }

        if (punkte.isEmpty()) {
            warnungen += "KF.TXT-Festbreitenformat erkannt, aber keine plausiblen Koordinatenpunkte gefunden."
        } else {
            val ohneStrecke = punkte.count { it.strecke == null }
            if (ohneStrecke > 0) {
                warnungen += "$ohneStrecke KF.TXT-Punkt(e) ohne Streckenbezug wurden als neutrale Vermessungspunkte erhalten."
            }
        }
        return punkte
    }

    /**
     * Gessertshausen und KF.TXT haben nach Aufteilung mindestens:
     * PunktNr, X, Y, Hoehe, KM/Station, Punktname, Strecke, ...
     */
    private fun parseStandardOderKf(
        dateiname: String,
        text: String,
        warnungen: MutableList<String>
    ): List<GvpPointEntity> {
        val punkte = mutableListOf<GvpPointEntity>()

        text.lineSequence().forEachIndexed { _, raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed

            val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.size < 7) return@forEachIndexed

            val x = tokens.getOrNull(1)?.toDoubleDeOrNull()
            val y = tokens.getOrNull(2)?.toDoubleDeOrNull()
            val hoehe = tokens.getOrNull(3)?.toDoubleDeOrNull()
            val kmMeter = tokens.getOrNull(4)?.toDoubleDeOrNull()
            val punktName = tokens.getOrNull(5)?.takeIf { it.isNotBlank() }
            val strecke = tokens.getOrNull(6)?.takeIf { it.matches(Regex("[1-9]\\d{3}")) }

            if (x == null || y == null || punktName == null || strecke == null) return@forEachIndexed
            if (!istDbRefPlausibel(x, y)) return@forEachIndexed

            val typ = tokens.firstOrNull { token ->
                val upper = token.uppercase(Locale.GERMANY)
                upper.startsWith("GVP") || upper in setOf("PPB", "GPSC", "HBH", "PS0", "PS2")
            }
            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(x, y)

            punkte += GvpPointEntity(
                strecke = strecke,
                punktName = punktName,
                kmMeter = kmMeter,
                xDbRef = x,
                yDbRef = y,
                lat = gps?.lat,
                lon = gps?.lon,
                hoeheM = hoehe,
                typ = typ,
                klasse = tokens.firstOrNull()?.takeIf { it.isNotBlank() },
                koordinatensystem = TrassenAchseEntity.KOORD_DB_REF2016_4,
                quelleDatei = dateiname,
                rawLine = raw
            )
        }

        if (punkte.isEmpty() && text.lineSequence().count() > 5) {
            warnungen += "Standard-/KF-Format nicht erkannt; pruefe KV-Format."
        }
        return punkte
    }

    /**
     * KV-Beispiel 8771:
     * 545600  4403285.27551 5407255.31497 415.4400 54319.7580 54/56 54/56 GVPV 5300 ...
     *
     * Die Hoehe kann leer sein. Darum werden Station, Punktart und Strecke nicht
     * ueber starre Token-Indizes, sondern ueber ihre Form erkannt.
     */
    private fun parseKv(
        dateiname: String,
        text: String,
        warnungen: MutableList<String>
    ): List<GvpPointEntity> {
        val punkte = mutableListOf<GvpPointEntity>()

        text.lineSequence().forEachIndexed { _, raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed
            val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.size < 7) return@forEachIndexed

            val x = tokens.getOrNull(1)?.toDoubleDeOrNull() ?: return@forEachIndexed
            val y = tokens.getOrNull(2)?.toDoubleDeOrNull() ?: return@forEachIndexed
            if (!istDbRefPlausibel(x, y)) return@forEachIndexed

            val routeIndex = (4 until tokens.size).firstOrNull { i ->
                tokens[i].matches(Regex("[1-9]\\d{3}")) &&
                    i > 0 && tokens[i - 1].any { it.isLetter() }
            } ?: return@forEachIndexed
            val strecke = tokens[routeIndex]
            val typIndex = routeIndex - 1
            val typ = tokens.getOrNull(typIndex)

            val stationIndex = (3 until typIndex)
                .filter { tokens[it].toDoubleDeOrNull() != null }
                .firstOrNull { (tokens[it].toDoubleDeOrNull() ?: 0.0) > 1_000.0 }
                ?: return@forEachIndexed
            val station = tokens[stationIndex].toDoubleDeOrNull() ?: return@forEachIndexed

            val hoehe = (3 until stationIndex)
                .mapNotNull { tokens[it].toDoubleDeOrNull() }
                .firstOrNull { it in -500.0..4_000.0 }
            val punktName = tokens.getOrNull(stationIndex + 1)
                ?.takeIf { stationIndex + 1 < typIndex && it.isNotBlank() }
                ?: tokens.first()

            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(x, y)
            punkte += GvpPointEntity(
                strecke = strecke,
                punktName = punktName,
                kmMeter = station,
                xDbRef = x,
                yDbRef = y,
                lat = gps?.lat,
                lon = gps?.lon,
                hoeheM = hoehe,
                typ = typ,
                klasse = tokens.firstOrNull()?.takeIf { it.isNotBlank() },
                koordinatensystem = TrassenAchseEntity.KOORD_DB_REF2016_4,
                quelleDatei = dateiname,
                rawLine = raw
            )
        }

        if (punkte.isEmpty()) {
            warnungen += "KV-Format nicht erkannt."
        }
        return punkte
    }

    private fun istDbRefPlausibel(x: Double, y: Double): Boolean =
        x in 3_000_000.0..6_000_000.0 && y in 4_000_000.0..6_500_000.0

    private fun dekodiereText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if ('\uFFFD' in utf8) {
            String(bytes, Charset.forName("ISO-8859-1"))
        } else {
            utf8
        }
    }

    private fun String.safeSlice(start: Int, endExclusive: Int): String {
        if (start >= length) return ""
        return substring(start.coerceAtLeast(0), endExclusive.coerceAtMost(length))
    }

    private fun String.toDoubleDeOrNull(): Double? =
        replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

data class GvpParseResult(
    val punkte: List<GvpPointEntity>,
    val warnungen: List<String> = emptyList()
)
