package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.datenbank.FotoEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Unveränderliche Foto-Basis des ERSTEN BTB-Abschlusses.
 *
 * Der Text-/Fachdaten-Originalstand liegt in btb_<id>_original.json. Fotos leben außerhalb dieser
 * JSON-Datei, deshalb wird ihre erste Abschlussmenge separat und ebenfalls unveränderlich gehalten.
 * Spätere Abschlüsse überschreiben diese Datei niemals.
 */
object BtbOriginalFotoSpeicher {

    private const val DIR_NAME = "btb_original_fotos"

    private fun safeToken(raw: String): String = raw.lowercase()
        .replace(Regex("[^a-z0-9_-]"), "_")
        .take(80)
        .ifBlank { "btb" }

    private fun file(context: Context, btbId: String): File =
        File(File(context.filesDir, DIR_NAME).apply { mkdirs() }, "${safeToken(btbId)}.json")

    fun hatOriginal(context: Context, btbId: String?): Boolean {
        val id = btbId?.trim().orEmpty()
        return id.isNotBlank() && file(context, id).exists()
    }

    fun originalIds(context: Context, btbId: String?): Set<Long> {
        val id = btbId?.trim().orEmpty()
        if (id.isBlank()) return emptySet()
        val f = file(context, id)
        if (!f.exists()) return emptySet()
        return runCatching {
            val arr = JSONObject(f.readText(Charsets.UTF_8)).optJSONArray("fotoIds") ?: JSONArray()
            buildSet {
                for (i in 0 until arr.length()) {
                    val value = arr.optLong(i, Long.MIN_VALUE)
                    if (value != Long.MIN_VALUE) add(value)
                }
            }
        }.getOrDefault(emptySet())
    }

    /** Schreibt nur beim allerersten erfolgreichen Abschluss. Danach niemals überschreiben. */
    fun sichernWennFehlt(context: Context, btbId: String?, fotoIds: Set<Long>) {
        val id = btbId?.trim().orEmpty()
        if (id.isBlank()) return
        val f = file(context, id)
        if (f.exists()) return
        val root = JSONObject().apply {
            put("version", 1)
            put("btbId", id)
            put("erstelltEpochMs", System.currentTimeMillis())
            put("fotoIds", JSONArray().apply { fotoIds.sorted().forEach(::put) })
        }
        runCatching { f.writeText(root.toString(2), Charsets.UTF_8) }
    }

    /**
     * Moderne, aktuell sichtbare Fotos dieses BTB. Fremde btbId-Fotos sind ausgeschlossen;
     * bewusst abgewählte Fotos zählen nicht zum Abschlussbestand.
     */
    fun aktuelleFotoIds(
        context: Context,
        entry: BautagebuchEintrag,
        alleFotos: List<FotoEntity>
    ): Set<Long> {
        val btbId = entry.btbId?.trim().orEmpty()
        if (btbId.isBlank()) return emptySet()
        val excluded = BtbPhotoSelectionStore.loadExcluded(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        )
        return alleFotos.asSequence()
            .filter { BtbFotoZuordnungSpeicher.btbId(context, it.id) == btbId }
            .filter { foto -> excluded[foto.bildtyp.name]?.contains(foto.id) != true }
            .map { it.id }
            .toSet()
    }

    fun neueFotoIds(context: Context, btbId: String?, aktuelleIds: Set<Long>): Set<Long> {
        if (!hatOriginal(context, btbId)) return emptySet()
        return aktuelleIds - originalIds(context, btbId)
    }

    fun entfernteFotoIds(context: Context, btbId: String?, aktuelleIds: Set<Long>): Set<Long> {
        if (!hatOriginal(context, btbId)) return emptySet()
        return originalIds(context, btbId) - aktuelleIds
    }
}
