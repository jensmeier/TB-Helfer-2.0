package de.tbhelfer.ui.bautagebuch

import de.tbhelfer.datenbank.Bildtyp
import java.util.Locale

/** A caption is a fallback only within one report, section, company and node. */
object BtbPhotoEntryMatcher {
    private fun norm(value: String?) = value.orEmpty().trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun find(entry: BautagebuchEintrag, type: Bildtyp, firm: String?, node: String?, text: String, firmId: String? = null): String? {
        if (text.isBlank()) return null
        val block = when (type) {
            Bildtyp.FOTODOKUMENTATION -> entry.arbeiten
            Bildtyp.ABNAHMEN -> entry.abnahmen
            Bildtyp.EINWEISUNG_UNTERWEISUNG -> entry.einweisungUnterweisung
            Bildtyp.BESONDERHEITEN -> entry.besonderheiten
            Bildtyp.MAENGEL_SCHAEDEN -> entry.maengelSchaeden
        }
        val firms = entry.firmen.associate { it.firmaId to it.name }
        return block.items.filter { item ->
            norm(item.text) == norm(text) && norm(item.firmaId?.let(firms::get) ?: item.firmaName) == norm(firm) &&
                item.worksiteNodeKey.orEmpty() == node.orEmpty() &&
                (firmId.isNullOrBlank() || item.firmaId == firmId)
        }.mapNotNull { it.clientEntryKey?.takeIf(String::isNotBlank) }.distinct().singleOrNull()
    }
}
