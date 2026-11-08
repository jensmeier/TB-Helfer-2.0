package de.tbhelfer.ui.bautagebuch

/** Deduplicate export bytes only by an established attachment identity or exact local path. */
object LocalExportManifest {
    data class Photo(val id: Long, val attachmentId: String?, val path: String, val available: Boolean, val preview: Boolean)
    data class Selection(val primaryIds: List<Long>, val primaryById: Map<Long, Long>, val missing: Int)

    fun select(photos: List<Photo>): Selection {
        val groups = photos.groupBy { photo ->
            photo.attachmentId?.takeIf { it.isNotBlank() }?.let { "attachment:$it" } ?: "path:${photo.path}"
        }
        val primary = mutableListOf<Long>()
        val aliases = linkedMapOf<Long, Long>()
        var missing = 0
        for (group in groups.values) {
            val chosen = group.filter { it.available }.minByOrNull { if (it.preview) 1 else 0 }
            if (chosen == null) { missing++; continue }
            primary.add(chosen.id)
            group.forEach { aliases[it.id] = chosen.id }
        }
        return Selection(primary, aliases, missing)
    }
}
