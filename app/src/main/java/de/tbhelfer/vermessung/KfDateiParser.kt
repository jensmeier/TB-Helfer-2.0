package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.GvpPointEntity
import de.tbhelfer.datenbank.TrassenAchseEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.abs

/**
 * Direkter Parser fuer rohe iGeo/DB-KF-Dateien Version 7.0.
 *
 * Die im Projekt geprueften realen Baustellenbestaende verwenden feste 102-Byte-Datensaetze:
 * ein Headerblock, danach ein Block je Vermessungspunkt. Numerische Werte sind
 * Little-Endian-IEEE754-Doubles. Fehlwerte werden in realen Dateien u. a. als 1e40 codiert.
 *
 * Der Parser schreibt bewusst in das bestehende GVP/Festpunkt-Modell. Dadurch ist fuer 2.0 (006)
 * keine Room-Migration noetig und bestehende KF.TXT/KV-Importe bleiben kompatibel.
 */
object KfDateiParser {

    private const val DATENSATZ_BYTES = 102
    private const val FEHLWERT_GRENZE = 1e30
    private val TEXT_CHARSET: Charset = Charset.forName("windows-1252")

    fun parse(dateiname: String, bytes: ByteArray): GvpParseResult {
        require(bytes.size >= DATENSATZ_BYTES * 2) {
            "KF-Datei ist zu klein: erwartet werden Header + mindestens ein 102-Byte-Punktdatensatz."
        }
        require(bytes.size % DATENSATZ_BYTES == 0) {
            "KF-Dateigroesse ${bytes.size} ist kein Vielfaches von $DATENSATZ_BYTES Byte."
        }

        val headerText = text(bytes, 40, 34)
        require(headerText.contains("VERSION 7.0", ignoreCase = true)) {
            "KF-Version nicht unterstuetzt: '${headerText.ifBlank { "kein VERSION-Feld" }}'. Erwartet VERSION 7.0."
        }

        val punkte = mutableListOf<GvpPointEntity>()
        val warnungen = mutableListOf<String>()
        var fehlendeHoehen = 0
        var fehlendeStationen = 0
        var nullStationen = 0
        var ohneStrecke = 0
        var ohnePArt = 0
        var unplausibleKoordinaten = 0

        val punktAnzahl = bytes.size / DATENSATZ_BYTES - 1
        for (index in 0 until punktAnzahl) {
            val offset = (index + 1) * DATENSATZ_BYTES
            val kfPktNrRaw = doubleLe(bytes, offset)
            val rechtswert = doubleLe(bytes, offset + 8)
            val hochwert = doubleLe(bytes, offset + 16)
            val hoeheRaw = doubleLe(bytes, offset + 24)
            val stationRaw = doubleLe(bytes, offset + 32)

            if (!koordinatePlausibel(rechtswert, hochwert)) {
                unplausibleKoordinaten++
                continue
            }

            val lage = text(bytes, offset + 40, 16)
            val zusatz = text(bytes, offset + 56, 14)
            val pArt = text(bytes, offset + 70, 4).trim().takeIf { it.isNotBlank() }
            val vArtByte = bytes[offset + 74].toInt() and 0xff
            val statusHex = hex(bytes, offset + 74, 9)
            val metadaten = text(bytes, offset + 83, 14)
            val streckeRaw = text(bytes, offset + 97, 4)
            val riKz = text(bytes, offset + 101, 1).takeIf { it.isNotBlank() }

            val hoehe = hoeheRaw.takeIf(::messwertPlausibel)
                ?: run { fehlendeHoehen++; null }
            val station = when {
                !messwertPlausibel(stationRaw) -> {
                    fehlendeStationen++
                    null
                }
                abs(stationRaw) <= 0.000001 -> {
                    // Harburg zeigt reale KF-Dateien mit Strecke, aber ausschliesslich 0.0
                    // in der Stationsspalte. Ohne weitere Bestaetigung darf daraus kein km 0 entstehen.
                    nullStationen++
                    null
                }
                else -> stationRaw
            }
            val strecke = streckeRaw
                .takeIf { it.matches(Regex("[1-9]\\d{3}")) }
                ?: run { ohneStrecke++; null }
            if (pArt == null) ohnePArt++

            val kfPktNr = kfPktNrRaw
                .takeIf(::messwertPlausibel)
                ?.let(::formatKfPunktNr)
            val punktName = lage.ifBlank { kfPktNr ?: "KF-${index + 1}" }
            val gps = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(rechtswert, hochwert)

            val rawInfo = buildString {
                append("KF_VERSION=7.0")
                kfPktNr?.let { append(";KF_PKTNR=$it") }
                if (zusatz.isNotBlank()) append(";ZUSATZ=${zusatz.replace(';', ',')}")
                append(";VART=$vArtByte")
                append(";STATUS_74_82=$statusHex")
                if (metadaten.isNotBlank()) append(";META=${metadaten.replace(';', ',')}")
                if (streckeRaw.isNotBlank()) append(";STRECKE_RAW=$streckeRaw")
                riKz?.let { append(";RIKZ=$it") }
            }

            punkte += GvpPointEntity(
                strecke = strecke,
                punktName = punktName,
                kmMeter = station,
                xDbRef = rechtswert,
                yDbRef = hochwert,
                lat = gps?.lat,
                lon = gps?.lon,
                hoeheM = hoehe,
                typ = pArt,
                klasse = kfPktNr,
                koordinatensystem = TrassenAchseEntity.KOORD_DB_REF2016_4,
                quelleDatei = dateiname,
                rawLine = rawInfo
            )
        }

        require(punkte.isNotEmpty()) {
            "KF 7.0 erkannt, aber keine plausiblen Koordinatenpunkte gefunden."
        }

        if (fehlendeHoehen > 0) {
            warnungen += "$fehlendeHoehen KF-Punkt(e) ohne plausible Hoehe (z. B. 1e40) wurden mit Hoehe=NULL importiert."
        }
        if (fehlendeStationen > 0) {
            warnungen += "$fehlendeStationen KF-Punkt(e) ohne plausible Station (z. B. 1e40) wurden ohne km importiert."
        }
        if (nullStationen > 0) {
            warnungen += "$nullStationen KF-Punkt(e) mit Station 0.0 wurden vorsichtshalber nicht als km 0 verwendet."
        }
        if (ohneStrecke > 0) {
            warnungen += "$ohneStrecke KF-Punkt(e) ohne plausible Strecke wurden als koordinatenbasierte Vermessungspunkte erhalten."
        }
        if (ohnePArt > 0) {
            warnungen += "$ohnePArt KF-Punkt(e) ohne PArt wurden mit leerer Original-PArt erhalten."
        }
        if (unplausibleKoordinaten > 0) {
            warnungen += "$unplausibleKoordinaten KF-Datensatz/-saetze mit unplausiblen Koordinaten wurden uebersprungen."
        }

        return GvpParseResult(
            punkte = punkte,
            warnungen = warnungen.distinct()
        )
    }

    private fun doubleLe(bytes: ByteArray, offset: Int): Double =
        ByteBuffer.wrap(bytes, offset, 8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .double

    private fun text(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, TEXT_CHARSET)
            .trim { it == '\u0000' || it.isWhitespace() }

    private fun hex(bytes: ByteArray, offset: Int, length: Int): String =
        (offset until offset + length)
            .joinToString(" ") { i -> String.format(Locale.US, "%02X", bytes[i].toInt() and 0xff) }

    private fun messwertPlausibel(value: Double): Boolean =
        value.isFinite() && abs(value) < FEHLWERT_GRENZE

    private fun koordinatePlausibel(rechtswert: Double, hochwert: Double): Boolean =
        messwertPlausibel(rechtswert) && messwertPlausibel(hochwert) &&
            rechtswert in 3_000_000.0..6_000_000.0 &&
            hochwert in 4_000_000.0..6_500_000.0

    private fun formatKfPunktNr(value: Double): String =
        String.format(Locale.US, "%.5f", value)
}
