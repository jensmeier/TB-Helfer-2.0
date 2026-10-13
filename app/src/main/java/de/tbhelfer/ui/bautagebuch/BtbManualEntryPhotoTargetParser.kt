package de.tbhelfer.ui.bautagebuch

import java.util.Locale

/**
 * Liest die bereits vorhandenen manuellen BTB-Arbeitseintraege so, dass Fotos gezielt
 * einem fachlichen Eintrag (Firma + Ort + Text + Zeit/KM) zugeordnet werden koennen.
 *
 * Die bestehende Marker-Syntax (@ORT, @ALLGEMEIN, @KM) bleibt unveraendert. Damit ist
 * keine Datenbank- oder BTB-Dateimigration notwendig.
 */
object BtbManualEntryPhotoTargetParser {

    data class Target(
        val firma: String?,
        val ortId: Long,
        val time: String?,
        val text: String,
        val kmVon: Double?,
        val kmBis: Double?
    )

    fun parse(raw: String?, knownFirms: Collection<String>): List<Target> {
        val text = raw.orEmpty()
        if (text.isBlank()) return emptyList()

        val firmMap = knownFirms
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .associateBy { it.lowercase(Locale.ROOT) }

        val reOrt = Regex("""^@ORT=(\d+)\s*(.*)$""", RegexOption.IGNORE_CASE)
        val reKm = Regex(
            """^@KM=([0-9]+(?:[.,][0-9]+)?)(?:\s*[-–]\s*([0-9]+(?:[.,][0-9]+)?))?\s*(.*)$""",
            RegexOption.IGNORE_CASE
        )
        val reGeneral = Regex("""^@ALLGEMEIN\b\s*(.*)$""", RegexOption.IGNORE_CASE)
        val reTime = Regex(
            """^(\d{1,2}:\d{2})(?:\s*[-–]\s*\(?\s*(\d{1,2}:\d{2})\s*\)?\s*)?\s+(.*)$"""
        )

        var currentOrtId = 0L
        val out = ArrayList<Target>()

        text.lines().forEach { rawLine ->
            var line = rawLine.trim()
            if (line.isBlank()) return@forEach

            reOrt.matchEntire(line)?.let { match ->
                currentOrtId = match.groupValues[1].toLongOrNull() ?: 0L
                line = match.groupValues.getOrNull(2).orEmpty().trim()
            }
            if (line.isBlank()) return@forEach

            var forcedGeneral = false
            var kmVon: Double? = null
            var kmBis: Double? = null
            var markerFound: Boolean
            do {
                markerFound = false
                reGeneral.matchEntire(line)?.let { match ->
                    forcedGeneral = true
                    line = match.groupValues.getOrNull(1).orEmpty().trim()
                    markerFound = true
                    return@let
                }
                if (markerFound) continue

                reKm.matchEntire(line)?.let { match ->
                    kmVon = parseKm(match.groupValues[1])
                    kmBis = parseKm(match.groupValues.getOrNull(2))
                    line = match.groupValues.getOrNull(3).orEmpty().trim()
                    markerFound = true
                }
            } while (markerFound && line.isNotBlank())
            if (line.isBlank()) return@forEach

            while (line.startsWith("•") || line.startsWith("·")) {
                line = line.drop(1).trimStart()
            }
            if (line.isBlank()) return@forEach

            var time: String? = null
            var rest = line
            reTime.matchEntire(line)?.let { match ->
                val start = normalizeHm(match.groupValues[1])
                val end = match.groupValues.getOrNull(2).orEmpty().trim()
                    .takeIf(String::isNotBlank)
                    ?.let(::normalizeHm)
                if (start != null) {
                    time = if (end != null) "$start–$end" else start
                    rest = match.groupValues[3].trim()
                }
            }

            var firma: String? = null
            var entryText = rest
            if (!forcedGeneral) {
                val colon = rest.indexOf(':')
                if (colon > 0) {
                    val prefix = rest.substring(0, colon).trim()
                    val tail = rest.substring(colon + 1).trim()
                    val canonical = firmMap[prefix.lowercase(Locale.ROOT)]
                    if (canonical != null) {
                        firma = canonical
                        entryText = tail
                    }
                }
            }
            if (entryText.isBlank()) return@forEach

            val effectiveOrtId = if (forcedGeneral || firma == null) 0L else currentOrtId
            out += Target(
                firma = firma,
                ortId = effectiveOrtId,
                time = time,
                text = entryText,
                kmVon = kmVon,
                kmBis = kmBis
            )
        }

        return out
    }

    private fun parseKm(raw: String?): Double? = raw
        ?.trim()
        ?.replace(',', '.')
        ?.takeIf(String::isNotBlank)
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 && it < 10000.0 }

    private fun normalizeHm(raw: String): String? {
        val parts = raw.trim().split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return "%02d:%02d".format(Locale.ROOT, h, m)
    }
}
