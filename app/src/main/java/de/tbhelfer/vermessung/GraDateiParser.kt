package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.GraKmPunktEntity
import de.tbhelfer.datenbank.TrassenAchseEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Parser fuer binaere GRA-/kmLinie-Dateien (*.GRA).
 *
 * Aus den gelieferten Dateien ist diese Struktur nutzbar:
 * - 36 Byte Kopf
 * - erste 8 Byte im Kopf enthalten die Punktanzahl als Double
 * - danach 36 Byte je Punkt
 * - je Punkt: Station/km, Hoehe, Radius/Gradientenwert, Parameter, Punktcode
 *
 * Wichtig: Patch 7 importiert die GRA-Punkte nur als km-/Hoehendaten.
 * Die exakte Zuordnung TRA-Station <-> Strecken-km kommt in einem spaeteren Berechner.
 */
object GraDateiParser {

    private const val HEADER_BYTES = 36
    private const val RECORD_BYTES = 36

    fun parse(dateiname: String, bytes: ByteArray): GraParseResult {
        require(dateiname.lowercase(Locale.GERMANY).endsWith(".gra")) {
            "Nur .GRA-Dateien werden im GRA-Import gelesen."
        }
        require(bytes.size >= HEADER_BYTES + RECORD_BYTES) {
            "GRA-Datei ist zu klein oder unvollstaendig."
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val countDouble = buffer.getDouble(0)
        val anzahlPunkte = countDouble.roundToInt()
        require(anzahlPunkte > 0) { "Keine GRA-Punkte gefunden." }
        require(abs(countDouble - anzahlPunkte.toDouble()) < 0.0001) {
            "GRA-Punktanzahl im Kopf ist unplausibel."
        }
        require(anzahlPunkte < 50_000) { "Unplausible GRA-Punktanzahl: $anzahlPunkte." }

        val benoetigt = HEADER_BYTES + anzahlPunkte * RECORD_BYTES
        require(bytes.size >= benoetigt) {
            "GRA-Datei unvollstaendig: $anzahlPunkte Punkte erwartet, Datei zu kurz."
        }

        val meta = GraMeta.ausDateiname(dateiname)
        val signatur = sha256Kurz(bytes)
        val warnungen = mutableListOf<String>()
        val punkte = mutableListOf<GraKmPunktEntity>()

        repeat(anzahlPunkte) { index ->
            val offset = HEADER_BYTES + index * RECORD_BYTES
            val kmMeter = buffer.getDouble(offset)
            val hoehe = buffer.getDouble(offset + 8).takeIf { it.isFinite() }
            val radius = buffer.getDouble(offset + 16).takeIf { it.isFinite() && abs(it) > 0.0001 }
            val parameter = buffer.getDouble(offset + 24).takeIf { it.isFinite() && abs(it) > 0.0001 }
            val code = buffer.getInt(offset + 32).takeIf { it != 0 }

            if (!kmMeter.isFinite()) {
                warnungen += "Punkt ${index + 1}: ungueltiger km-/Stationswert"
                return@repeat
            }
            if (kmMeter !in -10_000.0..500_000.0) {
                warnungen += "Punkt ${index + 1}: unplausibler km-/Stationswert"
            }
            if (hoehe != null && hoehe !in -500.0..4000.0) {
                warnungen += "Punkt ${index + 1}: unplausible Hoehe"
            }

            punkte += GraKmPunktEntity(
                quelleDatei = dateiname,
                punktIndex = index,
                strecke = meta.strecke,
                gleis = meta.gleis,
                richtung = meta.richtung,
                name = meta.name,
                kmMeter = kmMeter,
                hoeheM = hoehe,
                radiusM = radius,
                parameterM = parameter,
                punktCode = code,
                koordinatensystem = TrassenAchseEntity.KOORD_DB_REF2016_4,
                rawOffset = offset
            )
        }

        require(punkte.isNotEmpty()) {
            "Keine verwertbaren GRA-/kmLinie-Punkte gefunden."
        }

        return GraParseResult(
            punkte = punkte,
            signatur = signatur,
            warnungen = warnungen
        )
    }

    private fun sha256Kurz(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private data class GraMeta(
        val name: String,
        val strecke: String?,
        val gleis: String?,
        val richtung: String?
    ) {
        companion object {
            fun ausDateiname(dateiname: String): GraMeta {
                val basis = dateiname.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                val upper = basis.uppercase(Locale.GERMANY)
                val name = basis
                    .replace(Regex("_O\\d+.*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("_N00.*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("_KMLINIE", RegexOption.IGNORE_CASE), "")
                    .trim('_')

                // Bahnhofsgleise mit Streckennummer: 5528_Gleis_21_N00...
                Regex("(?<!\\d)([1-9]\\d{3})[_-]?(?:GL|GLEIS)[_\\s-]*(\\d+[A-Z]?)(?:[_\\s.-]|$)").find(upper)?.let { match ->
                    return GraMeta(
                        name = name,
                        strecke = match.groupValues[1],
                        gleis = match.groupValues[2],
                        richtung = null // Bahnhofsgleis: keine 0/1/2-Kennziffer
                    )
                }

                // Streckengleis / Streckenachse mit Quellrichtung: 5524_L_N00, 5528_R_N00, 5524S, 5524_SL
                Regex("(?<!\\d)([1-9]\\d{3})[_-]?(SL|[LRS])(?:[_\\s.-]|$)").find(upper)?.let { match ->
                    val strecke = match.groupValues[1]
                    val variante = match.groupValues[2].uppercase(Locale.GERMANY)
                    val richtung = when (variante) {
                        "S" -> "0"
                        "L", "SL" -> "1"
                        "R" -> "2"
                        else -> null
                    }
                    val gleis = when ("$strecke$variante") {
                        "5302L" -> "302"
                        "5302R" -> "303"
                        else -> null
                    }
                    return GraMeta(name = name, strecke = strecke, gleis = gleis, richtung = richtung)
                }
                // Bahnhofsgleis ohne Streckennummer: Gleis_21, Gl302, Gleis04B
                Regex("(?:^|[_\\s-])(?:GL|GLEIS)[_\\s-]*(\\d+[A-Z]?)(?:[_\\s.-]|$)").find(upper)?.let { match ->
                    return GraMeta(name = name, strecke = null, gleis = match.groupValues[1], richtung = null)
                }

                return GraMeta(
                    name = name,
                    strecke = Regex("(?<![A-Z0-9])([1-9]\\d{3})(?!\\d)").find(upper)?.groupValues?.getOrNull(1),
                    gleis = null,
                    richtung = null
                )
            }
        }
    }
}

data class GraParseResult(
    val punkte: List<GraKmPunktEntity>,
    val signatur: String,
    val warnungen: List<String> = emptyList()
)
