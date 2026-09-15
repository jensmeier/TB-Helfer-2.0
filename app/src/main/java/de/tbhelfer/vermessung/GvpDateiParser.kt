package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.GvpPointEntity
import de.tbhelfer.datenbank.TrassenAchseEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.abs

/**
 * Parser fuer Festpunkt-/GVP-Dateien mit DB_REF-Koordinaten.
 *
 * Unterstuetzte Varianten:
 * - rohe KF 7.0 / 7.0 R (102-Byte-Datensaetze)
 * - GVP-/Streifen-TXT
 * - KF.TXT-Koordinatenverzeichnisse
 * - KV-Festpunktdateien mit Station
 *
 * Entscheidend fuer eine automatische KM-Kalibrierung ist nicht die Dateiendung,
 * sondern ob pro Punkt eine echte Strecke und eine plausible Station vorhanden sind.
 */
object GvpDateiParser {

    private const val KF_RECORD_SIZE = 102
    private val kfCharset: Charset = Charset.forName("windows-1252")

    fun parse(dateiname: String, bytes: ByteArray): GvpParseResult {
        val warnungen = mutableListOf<String>()
        val lower = dateiname.lowercase(Locale.GERMANY)

        if (lower.endsWith(".kf") && istRohKf7(bytes)) {
            val punkte = parseRohKf(dateiname, bytes, warnungen)
            require(punkte.isNotEmpty()) {
                "Rohe KF-Datei erkannt, aber keine plausiblen Festpunkte gefunden."
            }
            return GvpParseResult(
                punkte = punkte,
                warnungen = warnungen.distinct()
            )
        }

        val text = dekodiereText(bytes)
        val istKv = lower.endsWith(".kv")
        val istKfText = lower.endsWith(".kf.txt") ||
            text.contains("Koordinatenverzeichnis", ignoreCase = true)
        val kv = if (istKv) parseKv(dateiname, text, warnungen) else emptyList()
        val standard = when {
            kv.isNotEmpty() -> emptyList()
            istKfText -> parseKfText(dateiname, text, warnungen)
            else -> parseStandardOderKf(dateiname, text, warnungen)
        }
        var punkte = if (kv.isNotEmpty()) kv else standard

        require(punkte.isNotEmpty()) {
            "Keine GVP/Festpunkte gefunden. Erwartet wird rohe KF 7.0, GVP-/KF.TXT oder eine KV-Datei mit DB_REF-Koordinaten."
        }

        // Harburg-Sonderfall: Eine KM-/Stationsspalte, die komplett 0.00000 ist,
        // ist keine belastbare KM-Quelle.
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

    private fun istRohKf7(bytes: ByteArray): Boolean {
        if (bytes.size < KF_RECORD_SIZE * 2 || bytes.size % KF_RECORD_SIZE != 0) return false
        val version = textFeld(bytes, 40, 34).uppercase(Locale.GERMANY)
        return version.contains("VERSION 7.0")
    }

    /**
     * Direkter Import der gelieferten KF-Version 7.0.
     *
     * Datensatzaufbau (Little Endian):
     * 0..7 KF-Punktnummer, 8..15 Rechtswert, 16..23 Hochwert,
     * 24..31 Hoehe, 32..39 Station, 40..55 Lagebezeichnung,
     * 56..69 Zusatztext, 70..73 PArt, 74 VArt, 75..82 Status,
     * 83..96 Metadaten, 97..100 Strecke, 101 RiKz.
     */
    private fun parseRohKf(
        dateiname: String,
        bytes: ByteArray,
        warnungen: MutableList<String>
    ): List<GvpPointEntity> {
        val punkte = mutableListOf<GvpPointEntity>()
        var ohneStrecke = 0
        var ohneStation = 0
        var ohneHoehe = 0
        var zone3 = 0

        val recordCount = bytes.size / KF_RECORD_SIZE
        for (recordIndex in 1 until recordCount) {
            val base = recordIndex * KF_RECORD_SIZE
            val kfNummer = plausibleWert(doubleLe(bytes, base)) ?: continue
            val rechtswert = plausibleWert(doubleLe(bytes, base + 8)) ?: continue
            val hochwert = plausibleWert(doubleLe(bytes, base + 16)) ?: continue
            if (!istDbRefPlausibel(rechtswert, hochwert)) continue

            val hoehe = plausibleWert(doubleLe(bytes, base + 24))
            val stationRoh = plausibleWert(doubleLe(bytes, base + 32))
            // Eine blanke/Platzhalter-Station 0 darf nicht als km 0.000 erfunden werden.
            val station = stationRoh?.takeIf { abs(it) > 0.001 }

            val lage = textFeld(bytes, base + 40, 16).takeIf { it.isNotBlank() }
            val zusatz = textFeld(bytes, base + 56, 14)
            val part = textFeld(bytes, base + 70, 4).takeIf { it.isNotBlank() }
            val vart = textFeld(bytes, base + 74, 1)
            val meta = textFeld(bytes, base + 83, 14)
            val strecke = textFeld(bytes, base + 97, 4)
                .takeIf { it.matches(Regex("[1-9]\\d{3}")) }
            val rikz = textFeld(bytes, base + 101, 1)

            if (strecke == null) ohneStrecke++
            if (station == null) ohneStation++
            if (hoehe == null) ohneHoehe++

            val koordinatensystem: String
            val gps = when {
                rechtswert in 4_000_000.0..<5_000_000.0 -> {
                    koordinatensystem = TrassenAchseEntity.KOORD_DB_REF2016_4
                    DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(rechtswert, hochwert)
                }
                rechtswert in 3_000_000.0..<4_000_000.0 -> {
                    // Gelieferte 5362-KF liegt im GK-/DB_REF-Streifen 3. Fuer diesen
                    // Streifen gibt es aktuell bewusst noch keinen GPS-Wandler. Die
                    // Rohkoordinaten bleiben dennoch voll fuer TRA/GVP-KM-Zuordnung nutzbar.
                    koordinatensystem = "DB_REF_GK_3"
                    zone3++
                    null
                }
                else -> {
                    koordinatensystem = "DB_REF_UNBEKANNT"
                    null
                }
            }

            val statusHex = (75..82).joinToString("") { offset ->
                "%02X".format(Locale.ROOT, bytes[base + offset].toInt() and 0xFF)
            }
            val recordHex = bytes.copyOfRange(base, base + KF_RECORD_SIZE).joinToString("") { b ->
                "%02X".format(Locale.ROOT, b.toInt() and 0xFF)
            }
            val kfNummerText = formatKfNummer(kfNummer)

            punkte += GvpPointEntity(
                strecke = strecke,
                punktName = lage ?: kfNummerText.ifBlank { "KF-$recordIndex" },
                kmMeter = station,
                xDbRef = rechtswert,
                yDbRef = hochwert,
                lat = gps?.lat,
                lon = gps?.lon,
                hoeheM = hoehe,
                typ = part,
                klasse = kfNummerText.takeIf { it.isNotBlank() },
                koordinatensystem = koordinatensystem,
                quelleDatei = dateiname,
                rawLine = buildString {
                    append("KF7")
                    if (zusatz.isNotBlank()) append("|zusatz=").append(zusatz)
                    if (vart.isNotBlank()) append("|vart=").append(vart)
                    append("|status=").append(statusHex)
                    if (meta.isNotBlank()) append("|meta=").append(meta)
                    if (rikz.isNotBlank()) append("|rikz=").append(rikz)
                    append("|raw=").append(recordHex)
                }
            )
        }

        warnungen += "Roh-KF 7.0 direkt importiert: ${punkte.size} Punkt(e)."
        if (ohneStrecke > 0) warnungen += "$ohneStrecke Roh-KF-Punkt(e) ohne plausible Strecke wurden als Koordinatenpunkte erhalten."
        if (ohneStation > 0) warnungen += "$ohneStation Roh-KF-Punkt(e) ohne plausible Station liefern keine direkte Kilometrierung."
        if (ohneHoehe > 0) warnungen += "$ohneHoehe Roh-KF-Punkt(e) ohne plausible Hoehe wurden erhalten."
        if (zone3 > 0) warnungen += "$zone3 Roh-KF-Punkt(e) im DB_REF/GK-Streifen 3 bleiben fuer Achs-/KM-Zuordnung nutzbar; GPS-Umrechnung folgt separat."
        return punkte
    }

    /**
     * iGeo-KF.TXT ist ein Festbreitenformat. Gerade wenn KM und Strecke leer sind,
     * darf es nicht ueber whitespace-Tokens gelesen werden: sonst rutschen Punktname,
     * Strecke und KM nach links.
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
                upper.startsWith("GVP") || upper in setOf("PPB", "GPSC", "HBH", "PS0", "PS1", "PS2", "PS3", "PS4")
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

    private fun doubleLe(bytes: ByteArray, offset: Int): Double =
        ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).double

    private fun plausibleWert(value: Double): Double? =
        value.takeIf { it.isFinite() && abs(it) < 1e30 }

    private fun textFeld(bytes: ByteArray, start: Int, length: Int): String {
        if (start < 0 || start >= bytes.size || length <= 0) return ""
        val ende = (start + length).coerceAtMost(bytes.size)
        return String(bytes, start, ende - start, kfCharset)
            .replace("\u0000", "")
            .trim()
    }

    private fun formatKfNummer(value: Double): String =
        String.format(Locale.ROOT, "%.5f", value).trimEnd('0').trimEnd('.')

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
        replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && abs(it) < 1e30 }
}

data class GvpParseResult(
    val punkte: List<GvpPointEntity>,
    val warnungen: List<String> = emptyList()
)
