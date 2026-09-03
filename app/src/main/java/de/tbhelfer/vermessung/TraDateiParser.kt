package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.TrassenAchseElementEntity
import de.tbhelfer.datenbank.TrassenAchseEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Parser fuer DB-TRA-Achsdateien (*.TRA).
 *
 * Der Parser liest die in den Absteckungsdaten verwendete binaere ER0-TRA-Struktur:
 * - Header mit Elementanzahl an Offset 0x30
 * - Stuetzzpunkte/Knoten ab Offset 0x48
 * - 78 Byte je Knoten
 *
 * Die Datei liefert DB_REF-X/Y. Patch 8 rechnet daraus beim Speichern zusaetzlich WGS84/GPS.
 */
object TraDateiParser {

    private const val HEADER_BYTES = 0x48
    private const val RECORD_BYTES = 78
    private const val ELEMENT_COUNT_OFFSET = 0x30
    private const val SAMPLE_ABSTAND_M = 2.0
    private const val UEBERGANG_NUMERIK_SCHRITT_M = 0.5

    fun parse(dateiname: String, bytes: ByteArray): TraParseResult {
        require(dateiname.lowercase(Locale.GERMANY).endsWith(".tra")) {
            "Nur .TRA-Dateien werden in Patch 3 importiert."
        }
        require(bytes.size >= HEADER_BYTES + RECORD_BYTES * 2) {
            "TRA-Datei ist zu klein oder unvollstaendig."
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val anzahlElemente = buffer.getInt(ELEMENT_COUNT_OFFSET)
        require(anzahlElemente > 0) { "Keine Achselemente in TRA-Datei gefunden." }
        require(anzahlElemente < 20_000) { "Unplausible Elementanzahl: $anzahlElemente." }

        val benoetigt = HEADER_BYTES + (anzahlElemente + 1) * RECORD_BYTES
        require(bytes.size >= benoetigt) {
            "TRA-Datei unvollstaendig: $anzahlElemente Elemente erwartet, Datei zu kurz."
        }

        val records = (0..anzahlElemente).map { index ->
            readRecord(buffer, HEADER_BYTES + index * RECORD_BYTES, index)
        }

        val meta = AchsMeta.ausDateiname(dateiname)
        val stationVon = records.first().station
        val stationBis = records.last().station
        val laenge = stationBis - stationVon
        val signatur = sha256Kurz(bytes)

        val elementVorlagen = (0 until anzahlElemente).map { index ->
            val start = records[index]
            val ende = records[index + 1]
            TraElementVorlage(
                elementIndex = index,
                stationStart = start.station,
                stationEnde = ende.station,
                xDbRef = start.x,
                yDbRef = start.y,
                richtungDeg = Math.toDegrees(start.richtungRad).normalisiereGrad(),
                elementTyp = rawTypZuText(start.rawTyp),
                laengeM = start.laenge,
                radiusM = radiusAus(start.radiusVon, start.radiusBis),
                parameterVon = start.parameter,
                parameterBis = ende.parameter,
                rawTyp = start.rawTyp
            )
        }

        val sampleVorlagen = erstelleVerdichteteSampleVorlagen(records)

        val achse = TrassenAchseEntity(
            name = meta.name,
            strecke = meta.strecke,
            gleis = meta.gleis,
            richtung = meta.richtung,
            achsTyp = meta.achsTyp,
            quelleDatei = dateiname,
            dateiSignatur = signatur,
            koordinatensystem = TrassenAchseEntity.KOORD_DB_REF2016_4,
            stationVon = stationVon,
            stationBis = stationBis,
            anzahlElemente = anzahlElemente,
            anzahlSamplepunkte = sampleVorlagen.size,
            importStatus = TrassenAchseEntity.STATUS_IMPORTIERT,
            notiz = "TRA importiert: ${format3(stationVon)} bis ${format3(stationBis)}, Laenge ${format3(laenge)} m, verdichtet auf ${sampleVorlagen.size} Punkte"
        )

        return TraParseResult(
            achse = achse,
            elemente = elementVorlagen,
            samples = sampleVorlagen,
            warnungen = pruefePlausibilitaet(records, anzahlElemente)
        )
    }

    private fun readRecord(buffer: ByteBuffer, offset: Int, index: Int): TraRecord {
        val radiusVon = buffer.getDouble(offset + 6)
        val radiusBis = buffer.getDouble(offset + 14)
        val x = buffer.getDouble(offset + 22)
        val y = buffer.getDouble(offset + 30)
        val richtungRad = buffer.getDouble(offset + 38)
        val station = buffer.getDouble(offset + 46)
        val rawTyp = buffer.getShort(offset + 54).toInt()
        val laenge = buffer.getDouble(offset + 56)
        val parameter = buffer.getDouble(offset + 64)

        require(x in 3_000_000.0..6_000_000.0 && y in 4_000_000.0..6_500_000.0) {
            "Unplausible DB_REF-Koordinate in Knoten $index."
        }
        require(station.isFinite()) { "Ungueltige Station in Knoten $index." }
        require(richtungRad.isFinite()) { "Ungueltige Richtung in Knoten $index." }

        return TraRecord(index, radiusVon, radiusBis, x, y, richtungRad, station, rawTyp, laenge, parameter)
    }

    private fun rawTypZuText(rawTyp: Int): String = when (rawTyp) {
        0 -> TrassenAchseElementEntity.ELEMENT_GERADE
        1 -> TrassenAchseElementEntity.ELEMENT_BOGEN
        2 -> TrassenAchseElementEntity.ELEMENT_UEBERGANG
        else -> TrassenAchseElementEntity.ELEMENT_UNBEKANNT
    }

    private fun radiusAus(radiusVon: Double, radiusBis: Double): Double? {
        val kandidat = when {
            abs(radiusBis) > 0.0001 -> radiusBis
            abs(radiusVon) > 0.0001 -> radiusVon
            else -> return null
        }
        return kandidat.takeIf { it.isFinite() && abs(it) < 1_000_000.0 }
    }

    private fun pruefePlausibilitaet(records: List<TraRecord>, anzahlElemente: Int): List<String> {
        val warnungen = mutableListOf<String>()
        if (records.size != anzahlElemente + 1) {
            warnungen += "Knotenanzahl passt nicht zur Elementanzahl."
        }
        records.zipWithNext().forEachIndexed { index, pair ->
            if (pair.second.station < pair.first.station) {
                warnungen += "Station faellt bei Element $index."
            }
            if (index < anzahlElemente && pair.first.laenge < 0.0) {
                warnungen += "Negative Elementlaenge bei Element $index."
            }
        }
        return warnungen
    }

    /**
     * Patch 16: Die TRA-Datei ist eine verbundene Achse, keine Punktwolke.
     * Deshalb werden die importierten Knoten in 2-m-Samplepunkte verdichtet.
     * Gerade und Kreisboegen werden geometrisch berechnet; Uebergangsboegen werden
     * mit kurzer numerischer Integration angenaehert. Die echten TRA-Knoten bleiben
     * als exakte Segmentenden erhalten.
     */
    private fun erstelleVerdichteteSampleVorlagen(records: List<TraRecord>): List<TraSampleVorlage> {
        if (records.isEmpty()) return emptyList()
        if (records.size == 1) return listOf(records.first().toSampleVorlage())

        val samples = mutableListOf<TraSampleVorlage>()
        samples += records.first().toSampleVorlage()

        records.zipWithNext().forEach { (start, ende) ->
            val stationLaenge = ende.station - start.station
            val laenge = when {
                stationLaenge > 0.001 -> stationLaenge
                start.laenge > 0.001 -> start.laenge
                else -> 0.0
            }
            if (laenge <= 0.001) {
                samples += ende.toSampleVorlage()
                return@forEach
            }

            var s = SAMPLE_ABSTAND_M
            while (s < laenge - 0.10) {
                samples += sampleAufElement(start, ende, s, laenge)
                s += SAMPLE_ABSTAND_M
            }
            samples += ende.toSampleVorlage()
        }

        return samples.distinctBy { sample ->
            // Doppelte Stationen vermeiden, aber keine teure Rundung in der DB erzwingen.
            kotlin.math.round(sample.station * 1000.0).toLong()
        }
    }

    private fun sampleAufElement(start: TraRecord, ende: TraRecord, s: Double, laenge: Double): TraSampleVorlage {
        val rawTyp = start.rawTyp
        val punkt = when (rawTyp) {
            0 -> punktGerade(start, s)
            1 -> punktKreisbogen(start, s)
            2 -> punktUebergang(start, s, laenge)
            else -> punktLinear(start, ende, s / laenge)
        }
        return TraSampleVorlage(
            station = start.station + s,
            xDbRef = punkt.x,
            yDbRef = punkt.y,
            richtungDeg = Math.toDegrees(punkt.richtungRad).normalisiereGrad()
        )
    }

    private fun punktGerade(start: TraRecord, s: Double): SamplePunktIntern {
        val theta = start.richtungRad
        return SamplePunktIntern(
            x = start.x + s * sin(theta),
            y = start.y + s * cos(theta),
            richtungRad = theta
        )
    }

    private fun punktKreisbogen(start: TraRecord, s: Double): SamplePunktIntern {
        val radius = radiusAus(start.radiusVon, start.radiusBis)
        if (radius == null || abs(radius) < 0.001) return punktGerade(start, s)
        val theta0 = start.richtungRad
        val theta = theta0 + s / radius
        return SamplePunktIntern(
            x = start.x + radius * (cos(theta0) - cos(theta)),
            y = start.y + radius * (sin(theta) - sin(theta0)),
            richtungRad = theta
        )
    }

    private fun punktUebergang(start: TraRecord, s: Double, laenge: Double): SamplePunktIntern {
        val k0 = kruemmung(start.radiusVon)
        val k1 = kruemmung(start.radiusBis)
        if (abs(k0) < 0.000000001 && abs(k1) < 0.000000001) return punktGerade(start, s)

        var x = start.x
        var y = start.y
        var theta = start.richtungRad
        var gelaufen = 0.0
        while (gelaufen < s - 0.000001) {
            val ds = min(UEBERGANG_NUMERIK_SCHRITT_M, s - gelaufen)
            val mitte = gelaufen + ds * 0.5
            val t = (mitte / laenge).coerceIn(0.0, 1.0)
            val k = k0 + (k1 - k0) * t
            val thetaMitte = theta + k * ds * 0.5
            x += ds * sin(thetaMitte)
            y += ds * cos(thetaMitte)
            theta += k * ds
            gelaufen += ds
        }
        return SamplePunktIntern(x = x, y = y, richtungRad = theta)
    }

    private fun punktLinear(start: TraRecord, ende: TraRecord, tRaw: Double): SamplePunktIntern {
        val t = tRaw.coerceIn(0.0, 1.0)
        return SamplePunktIntern(
            x = start.x + (ende.x - start.x) * t,
            y = start.y + (ende.y - start.y) * t,
            richtungRad = start.richtungRad + (ende.richtungRad - start.richtungRad) * t
        )
    }

    private fun kruemmung(radius: Double): Double = when {
        !radius.isFinite() -> 0.0
        abs(radius) < 0.0001 -> 0.0
        else -> 1.0 / radius
    }

    private fun TraRecord.toSampleVorlage(): TraSampleVorlage = TraSampleVorlage(
        station = station,
        xDbRef = x,
        yDbRef = y,
        richtungDeg = Math.toDegrees(richtungRad).normalisiereGrad()
    )

    private fun sha256Kurz(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun Double.normalisiereGrad(): Double {
        var wert = this % 360.0
        if (wert < 0.0) wert += 360.0
        return wert
    }

    private fun format3(value: Double): String = String.format(Locale.GERMANY, "%.3f", value)

    private data class SamplePunktIntern(
        val x: Double,
        val y: Double,
        val richtungRad: Double
    )

    private data class TraRecord(
        val index: Int,
        val radiusVon: Double,
        val radiusBis: Double,
        val x: Double,
        val y: Double,
        val richtungRad: Double,
        val station: Double,
        val rawTyp: Int,
        val laenge: Double,
        val parameter: Double
    )

    private data class AchsMeta(
        val name: String,
        val strecke: String?,
        val gleis: String?,
        val richtung: String?,
        val achsTyp: String
    ) {
        companion object {
            private fun findeStreckeUndQuellrichtung(text: String): Pair<String, String>? {
                val upper = text.uppercase(Locale.GERMANY)

                // Klassische Form: 5524_L / 5302L / 5362_SL
                Regex("(?<!\\d)([1-9]\\d{3})[_-]?(SL|[LRS])(?:[_\\s.-]|$)")
                    .find(upper)?.let { match ->
                        return match.groupValues[1] to match.groupValues[2]
                    }

                // KM-/Stationsbereich mit angehaengter Richtung: 5504_005-015L
                Regex("(?<!\\d)([1-9]\\d{3})[_-]\\d{3}[-_]\\d{3}(SL|[LRS])(?:[_\\s.-]|$)")
                    .find(upper)?.let { match ->
                        return match.groupValues[1] to match.groupValues[2]
                    }

                // Flexible Exportnamen: 5300_054-059_DHDN_R,
                // 2025-04-25_5300_050-053_S, 5504_ER0_20240227_S.
                val richtungMatch = Regex("(?<![A-Z0-9])(SL|[LRS])(?![A-Z0-9])")
                    .findAll(upper)
                    .lastOrNull()
                    ?: return null
                val streckeMatch = Regex("(?<!\\d)([1-9]\\d{3})(?!\\d)")
                    .findAll(upper)
                    .filter { it.range.first < richtungMatch.range.first }
                    .lastOrNull()
                    ?: return null
                return streckeMatch.groupValues[1] to richtungMatch.groupValues[1]
            }

            fun ausDateiname(dateiname: String): AchsMeta {
                val basis = dateiname.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                val upper = basis.uppercase(Locale.GERMANY)
                val nameKurz = basis
                    .replace(Regex("_ER0.*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("_N00.*", RegexOption.IGNORE_CASE), "")
                    .trim('_')

                // Einzelweichen: SW_125W, W_14, W14
                if (upper.startsWith("SW_") || Regex("^W[_-]?\\d+[A-Z]?").containsMatchIn(upper)) {
                    return AchsMeta(
                        name = nameKurz,
                        strecke = Regex("(?<![A-Z0-9])([1-9]\\d{3})(?!\\d)").find(upper)?.groupValues?.getOrNull(1),
                        gleis = null,
                        richtung = null,
                        achsTyp = TrassenAchseEntity.ACHSTYP_WEICHE
                    )
                }

                // Weichenverbindungen: WV_127-128, WVB_14-22
                if (upper.startsWith("WV_") || upper.startsWith("WVB_")) {
                    return AchsMeta(
                        name = nameKurz,
                        strecke = Regex("(?<![A-Z0-9])([1-9]\\d{3})(?!\\d)").find(upper)?.groupValues?.getOrNull(1),
                        gleis = null,
                        richtung = null,
                        achsTyp = TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG
                    )
                }

                // Bahnhofsgleise mit Streckennummer: 5528_Gleis_21, 5904_Gleis04B
                Regex("(?<!\\d)([1-9]\\d{3})[_-]?(?:GL|GLEIS)[_\\s-]*(\\d+[A-Z]?)(?:[_\\s.-]|$)").find(upper)?.let { match ->
                    return AchsMeta(
                        name = nameKurz,
                        strecke = match.groupValues[1],
                        gleis = match.groupValues[2],
                        richtung = null, // Bahnhofsgleise haben keine 0/1/2-Kennziffer
                        achsTyp = TrassenAchseEntity.ACHSTYP_GLEIS
                    )
                }

                // Bahnhofsgleis mit kurzer Schreibweise: G4_MGHN, G_4.
                // Ein vorangestelltes Datum (z. B. 2025-04-25_...) ist keine Streckennummer.
                Regex("(?:^|[_\\s-])G[_\\s-]*(\\d+[A-Z]?)(?:[_\\s.-]|$)").find(upper)?.let { match ->
                    return AchsMeta(
                        name = nameKurz,
                        strecke = null,
                        gleis = match.groupValues[1],
                        richtung = null,
                        achsTyp = TrassenAchseEntity.ACHSTYP_GLEIS
                    )
                }

                // Streckengleise / Streckenachsen mit Quellrichtung. In echten Daten steht die
                // Richtung nicht immer direkt hinter der Streckennummer. Beispiele:
                // 5524_L, 5302L, 5504_005-015L, 5300_054-059_DHDN_R,
                // 2025-04-25_5300_050-053_S und 5504_ER0_20240227_S.
                // Die Rohwerte S/SL/L/R bleiben fachlich sichtbar; die bestehende interne
                // 0/1/2-Abbildung wird aus Kompatibilitaetsgruenden beibehalten.
                findeStreckeUndQuellrichtung(upper)?.let { (strecke, variante) ->
                    val richtung = when (variante) {
                        "S" -> "0"
                        "L", "SL" -> "1"
                        "R" -> "2"
                        else -> null
                    }
                    // Bestehende Gessertshausen-Sonderzuordnung unangetastet lassen.
                    val gleis = when ("$strecke$variante") {
                        "5302L" -> "302"
                        "5302R" -> "303"
                        else -> null
                    }
                    return AchsMeta(
                        name = nameKurz,
                        strecke = strecke,
                        gleis = gleis,
                        richtung = richtung,
                        achsTyp = if (!gleis.isNullOrBlank()) {
                            TrassenAchseEntity.ACHSTYP_GLEIS
                        } else {
                            TrassenAchseEntity.ACHSTYP_STRECKE
                        }
                    )
                }
                // Bahnhofsgleis ohne Streckennummer: Gleis_21, Gl302, Gleis04B
                Regex("(?:^|[_\\s-])(?:GL|GLEIS)[_\\s-]*(\\d+[A-Z]?)(?:[_\\s.-]|$)").find(upper)?.let { match ->
                    return AchsMeta(
                        name = nameKurz,
                        strecke = null,
                        gleis = match.groupValues[1],
                        richtung = null,
                        achsTyp = TrassenAchseEntity.ACHSTYP_GLEIS
                    )
                }

                return AchsMeta(
                    name = nameKurz,
                    strecke = Regex("(?<![A-Z0-9])([1-9]\\d{3})(?!\\d)").find(upper)?.groupValues?.getOrNull(1),
                    gleis = null,
                    richtung = null,
                    achsTyp = TrassenAchseEntity.ACHSTYP_UNBEKANNT
                )
            }
        }
    }
}

data class TraParseResult(
    val achse: TrassenAchseEntity,
    val elemente: List<TraElementVorlage>,
    val samples: List<TraSampleVorlage>,
    val warnungen: List<String> = emptyList()
)

data class TraElementVorlage(
    val elementIndex: Int,
    val stationStart: Double,
    val stationEnde: Double?,
    val xDbRef: Double,
    val yDbRef: Double,
    val richtungDeg: Double?,
    val elementTyp: String,
    val laengeM: Double,
    val radiusM: Double?,
    val parameterVon: Double?,
    val parameterBis: Double?,
    val rawTyp: Int?
)

data class TraSampleVorlage(
    val station: Double,
    val xDbRef: Double,
    val yDbRef: Double,
    val richtungDeg: Double?
)
