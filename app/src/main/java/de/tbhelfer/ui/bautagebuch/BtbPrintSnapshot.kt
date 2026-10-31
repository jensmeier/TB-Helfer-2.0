package de.tbhelfer.ui.bautagebuch

import de.tbhelfer.ServiceLocator
import android.content.Context
import de.tbhelfer.datenbank.FotoEntity

/** Read-only adapter for the existing renderers. Never persisted or synchronized. */
object BtbPrintSnapshot {
    suspend fun prepare(entry: BautagebuchEintrag, photoNumbers: Map<String, List<Int>> = emptyMap()): BautagebuchEintrag {
        val root = ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(entry.baustelleId)
        val binding = root?.let { ServiceLocator.worksiteBindingRepository.bindingForLocalRoot(it.id) }
        val nodes = binding?.let {
            ServiceLocator.syncDatenbank.worksiteNodeSyncDao().forWorksite(it.worksiteServerId)
        }.orEmpty().filter { it.localRootKnotenId == root?.id }
            .associate { it.clientNodeKey to it.localKnotenId }
        val firms = entry.firmen.associate { it.firmaId to it.name }
        fun block(source: SectionBlock): SectionBlock = source.copy(items = source.items.map {
            renderItem(it, firms, nodes, photoNumbers)
        })
        return entry.copy(arbeiten = block(entry.arbeiten), abnahmen = block(entry.abnahmen),
            einweisungUnterweisung = block(entry.einweisungUnterweisung),
            besonderheiten = block(entry.besonderheiten), maengelSchaeden = block(entry.maengelSchaeden))
    }
    fun renderItem(item: SectionItem, firms: Map<String, String>, nodes: Map<String, Long>,
        photoNumbers: Map<String, List<Int>> = emptyMap()): SectionItem {
        return if (item.clientEntryKey == null && item.firmaId == null && item.firmaName == null && item.worksiteNodeKey == null) item
            else {
                val firm = item.firmaId?.let(firms::get) ?: item.firmaName
                val node = item.worksiteNodeKey?.let { key ->
                    checkNotNull(nodes[key]) { "Unterbaustelle des Eintrags ist noch nicht verfügbar: $key" }
                } ?: 0L
                val clean = item.text.replace(Regex("^@ORT=\\d+\\s*"), "").removePrefix("@ALLGEMEIN ")
                val body = if (firm.isNullOrBlank()) clean else clean.removePrefix("${firm.trim()}: ")
                val numbers = photoNumbers[item.clientEntryKey].orEmpty().distinct().sorted()
                val suffix = if (numbers.isEmpty()) "" else " [Bild ${numbers.joinToString(", ")}]"
                val text = if (firm.isNullOrBlank()) "@ALLGEMEIN $body$suffix" else "${firm.trim()}: $body$suffix"
                item.copy(text = "@ORT=$node $text")
            }
    }

    suspend fun photoBindings(context: Context, entry: BautagebuchEintrag, photos: List<FotoEntity>,
        textOverrides: Map<Long, String> = emptyMap()): Map<Long, String> {
        val root = ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(entry.baustelleId)
        val binding = root?.let { ServiceLocator.worksiteBindingRepository.bindingForLocalRoot(it.id) }
        val keys = binding?.let { ServiceLocator.syncDatenbank.worksiteNodeSyncDao().forWorksite(it.worksiteServerId) }
            .orEmpty().filter { it.localRootKnotenId == root?.id }.associate { it.localKnotenId to it.clientNodeKey }
        val valid = listOf(entry.arbeiten, entry.abnahmen, entry.einweisungUnterweisung, entry.besonderheiten, entry.maengelSchaeden)
            .flatMap { it.items }.mapNotNull { it.clientEntryKey }.toSet()
        val btb = entry.btbId.orEmpty()
        return photos.mapNotNull { photo ->
            val explicit = BtbPhotoMetadatenStore.entryKey(context, btb, photo.id)
            val target = if (explicit != null) explicit.takeIf { it in valid }
            else if (!BtbPhotoMetadatenStore.autoBindingAllowed(context, btb, photo.id)) null
            else BtbPhotoEntryMatcher.find(entry, photo.bildtyp, photo.firma, keys[photo.baustellenKnotenId],
                textOverrides[photo.id] ?: photo.kurztext)
            target?.let { photo.id to it }
        }.toMap()
    }

}
