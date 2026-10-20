package de.tbhelfer.server

import java.util.Locale
import de.tbhelfer.streckenobjekte.WorksiteObjectIdentity

/** Gemeinsamer Lesebestand; weder Downloadauftrag noch lokale Objektzuweisung. */
data class CatalogInventory(val rows: List<CatalogInventoryRow>) {
    fun summary(): String = if (rows.isEmpty()) "Keine gemeinsamen Vermessungsdaten vorhanden." else
        rows.groupingBy { it.kind }.eachCount().entries.joinToString(" · ") { (kind, count) ->
            val label = if (count == 1) CatalogInventoryRow.kindLabel(kind) else when (kind) {
                "GLEIS", "TRACK" -> "Gleise"
                "WEICHE", "SWITCH" -> "Weichen"
                "WEICHENVERBINDUNG" -> "Weichenverbindungen"
                "STRECKE" -> "Streckenachsen"
                "CROSSING", "KREUZUNG" -> "Kreuzungen"
                "UNBEKANNT" -> "Weitere Achsen"
                else -> CatalogInventoryRow.kindLabel(kind)
            }
            "$count $label"
        }
}

data class CatalogInventoryRow(
    val id: String,
    val kind: String,
    val label: String,
    val routeNumber: String?,
    val stationCode: String?,
    val stationName: String?,
    val trackKey: String?,
    val objectKey: String?,
    val status: String,
    val segments: List<CatalogInventorySegment>,
    val selectable: Boolean = false
) {
    fun selectionNumber(): String? = if (selectable && kind in setOf("GLEIS", "WEICHE", "WEICHENVERBINDUNG"))
        (objectKey ?: trackKey)?.let { WorksiteObjectIdentity.number(it).takeIf { number -> number.isNotBlank() } } else null
    fun title(): String = "${kindLabel(kind)} · $label"

    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase(Locale.ROOT)
        return needle.isEmpty() || listOf(label, kind, kindLabel(kind), routeNumber, stationCode,
            stationName, trackKey, objectKey).filterNotNull().any { it.lowercase(Locale.ROOT).contains(needle) }
    }

    fun details(): String = buildList {
        routeNumber?.let { add("Strecke $it") }
        listOfNotNull(stationCode, stationName).joinToString(" · ").takeIf { it.isNotBlank() }
            ?.let { add("Bahnhof: $it") }
        objectKey?.let { add("Objekt: $it") }
        trackKey?.takeIf { it != objectKey }?.let { add("Gleisbezug: $it") }
        if (segments.isEmpty()) {
            add(if (status == "KEINE_GEOMETRIE") "Keine Geometrie vorhanden." else "Keine KM-Abschnitte vorhanden.")
        } else segments.forEachIndexed { index, segment ->
            add((if (segments.size > 1) "Abschnitt ${index + 1}: " else "") + segment.description())
        }
    }.joinToString("\n")

    companion object {
        fun kindLabel(kind: String): String = when (kind) {
            "GLEIS", "TRACK" -> "Gleis"
            "WEICHE", "SWITCH" -> "Weiche"
            "WEICHENVERBINDUNG" -> "Weichenverbindung"
            "STRECKE" -> "Streckenachse"
            "CROSSING", "KREUZUNG" -> "Kreuzung"
            "UNBEKANNT" -> "Weitere Achse"
            else -> kind.ifBlank { "Weitere Daten" }
        }
    }
}

data class CatalogInventorySegment(val status: String, val fromMeters: Double?, val toMeters: Double?) {
    fun rangeMeters(): Pair<Double, Double>? {
        val a = fromMeters ?: return null
        val b = toMeters ?: return null
        return if (status in setOf("AUTOMATISCH", "MANUELL", "KM_VORHANDEN") && a.isFinite() && b.isFinite())
            minOf(a, b) to maxOf(a, b) else null
    }

    fun description(): String {
        val bounds = rangeMeters()
        if (bounds != null) {
            val range = String.format(Locale.GERMANY, "km %.3f–%.3f", bounds.first / 1000.0, bounds.second / 1000.0)
            return range + when (status) {
                "MANUELL" -> " (manuell)"
                "AUTOMATISCH" -> " (automatisch)"
                else -> ""
            }
        }
        return when (status) {
            "UNPLAUSIBEL" -> "Daten vorhanden, KM-Zuordnung nicht belastbar."
            "KEINE_GEOMETRIE" -> "Keine Geometrie vorhanden."
            "FEHLT", "OHNE_KM", "AUTOMATISCH", "MANUELL", "KM_VORHANDEN" -> "Daten vorhanden, ohne KM-Zuordnung."
            else -> "Daten vorhanden, KM-Status unbekannt."
        }
    }
}

