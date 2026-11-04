package de.tbhelfer.speech

import de.tbhelfer.ui.bautagebuch.*
import java.text.Normalizer
import java.util.Locale

/** Pure policy: the recognizer proposes; only the editor may commit a business entry. */
data class SpeechDraft(
    val id: String,
    val owner: String,
    val siteId: Long,
    val siteName: String,
    val btbId: String,
    val text: String,
    val section: Int? = null,
    val firmId: String? = null,
    val created: Long = System.currentTimeMillis(),
    val nodeId: Long? = null,
    val originalText: String? = null,
    val fieldsParsed: Boolean = false,
    val timeFrom: String = "",
    val timeTo: String = "",
    val kmFrom: String = "",
    val kmTo: String = "",
    val parseNotice: String = ""
)

object SpeechDraftPolicy {
    val sections = linkedMapOf(4 to "Ausgeführte Arbeiten", 5 to "Abnahmen", 6 to "Einweisung / Unterweisung", 7 to "Besonderheiten", 8 to "Mängel / Schäden")
    private fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.GERMAN), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace("ß", "ss").replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun has(text: String, word: String) = (" $text ").contains(" ${norm(word)} ")
    fun suggest(text: String, firms: List<Pair<String, String>>): Pair<Int?, String?> {
        return SpeechMatching.section(text) to SpeechMatching.firm(text,firms)?.id
    }

    /** Only a unique explicit place name is proposed; the editor still confirms it. */
    fun suggestNode(text: String, nodes: List<Pair<Long, List<String>>>): Long? {
        val n = norm(text)
        return nodes.filter { (_, names) -> names.any { name ->
            val key = norm(name)
            key.length >= 3 && key.any { it in 'a'..'z' } && has(n, name)
        } }.map { it.first }.distinct().singleOrNull()
    }
    fun hasDescription(text: String, firmName: String?): Boolean {
        var value = norm(text)
        if (!firmName.isNullOrBlank()) value = (" $value ").replace(" ${norm(firmName)} ", " ").trim()
        val labels = listOf("arbeiten", "ausgeführte arbeiten", "abnahme", "abnahmen", "einweisung", "unterweisung", "besonderheiten", "mangel", "mängel", "schäden")
        return value.isNotBlank() && labels.none { norm(it) == value }
    }
    fun bucket(draft: SpeechDraft): Int? = draft.section?.takeIf { it in sections && draft.firmId != null }
    fun items(e: BautagebuchEintrag, section: Int): List<SectionItem> = when(section) {
        4 -> e.arbeiten.items; 5 -> e.abnahmen.items; 6 -> e.einweisungUnterweisung.items
        7 -> e.besonderheiten.items; 8 -> e.maengelSchaeden.items; else -> error("BTB-Punkt fehlt")
    }
    fun contains(e: BautagebuchEintrag, id: String): Boolean = sections.keys.any { s -> items(e,s).any { it.clientEntryKey == id } }
    fun append(e: BautagebuchEintrag, section: Int, item: SectionItem): BautagebuchEintrag {
        require(section in sections && item.text.isNotBlank() && !item.clientEntryKey.isNullOrBlank())
        if (contains(e, item.clientEntryKey!!)) {
            require(items(e,section).any { it == item }) { "Eintrag bereits mit anderem Inhalt übernommen" }
            return e
        }
        val block = SectionBlock(items(e, section) + item)
        return when(section) {
            4 -> e.copy(arbeiten=block); 5 -> e.copy(abnahmen=block); 6 -> e.copy(einweisungUnterweisung=block)
            7 -> e.copy(besonderheiten=block); else -> e.copy(maengelSchaeden=block)
        }
    }
    fun mayStart(resumed: Boolean, busy: Boolean, preparing: Boolean, permissionPending: Boolean): Boolean =
        resumed && !busy && !preparing && !permissionPending
    enum class Route { OFFLINE, ONLINE, BLOCKED }
    fun route(onDevice: Boolean, onlineAllowed: Boolean, connected: Boolean): Route = when {
        onDevice -> Route.OFFLINE
        onlineAllowed && connected -> Route.ONLINE
        else -> Route.BLOCKED
    }
}



