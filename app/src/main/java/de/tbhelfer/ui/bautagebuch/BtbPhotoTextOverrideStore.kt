package de.tbhelfer.ui.bautagebuch

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Persistiert BTB/PDF-Text-Overrides pro Schicht als JSON-Datei.
 * Key: (baustelleId + datumIso + shiftStart + shiftEnd) -> Map<fotoId, text>
 *
 * Wichtig: überschreibt NICHT FotoEntity.notiz/kurtztext.
 */
object BtbPhotoTextOverrideStore {

    private const val DIR_NAME = "btb_photo_overrides"
    private const val VERSION = 1

    fun loadOverrides(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?
    ): MutableMap<Long, String> =
        loadOverrides(context, baustelleId, datumIso, shiftStart, shiftEnd, SchichtTyp.TAG)

    fun loadOverrides(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp
    ): MutableMap<Long, String> {
        val f = fileForRead(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        if (!f.exists()) return mutableMapOf()

        return try {
            val txt = f.readText(StandardCharsets.UTF_8)
            if (txt.isBlank()) return mutableMapOf()
            val root = JSONObject(txt)
            val obj = root.optJSONObject("overrides") ?: JSONObject()
            val out = linkedMapOf<Long, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val id = k.toLongOrNull() ?: continue
                val v = obj.optString(k, "").trim()
                if (v.isNotBlank()) out[id] = v
            }
            out
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun saveOverrides(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        overrides: Map<Long, String>
    ) = saveOverrides(context, baustelleId, datumIso, shiftStart, shiftEnd, SchichtTyp.TAG, overrides)

    fun saveOverrides(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp,
        overrides: Map<Long, String>
    ) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val f = fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        val tmp = File(dir, f.name + ".tmp")

        val root = JSONObject().apply {
            put("version", VERSION)
            put("updatedEpochMs", System.currentTimeMillis())
            put("baustelleId", baustelleId)
            put("datumIso", datumIso)
            put("schicht", schicht.name)
            put("shiftStart", shiftStart ?: JSONObject.NULL)
            put("shiftEnd", shiftEnd ?: JSONObject.NULL)
        }

        val obj = JSONObject()
        overrides.forEach { (id, txt) ->
            val v = txt.trim()
            if (v.isNotBlank()) obj.put(id.toString(), v)
        }
        root.put("overrides", obj)

        tmp.writeText(root.toString(), StandardCharsets.UTF_8)
        if (f.exists()) f.delete()
        tmp.renameTo(f)
    }

    /**
     * Setzt/entfernt Override für mehrere Foto-IDs.
     * text == null/blank => entfernen (Reset auf Standard)
     */
    fun setOverrideForIds(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        fotoIds: List<Long>,
        text: String?
    ) = setOverrideForIds(context, baustelleId, datumIso, shiftStart, shiftEnd, SchichtTyp.TAG, fotoIds, text)

    fun setOverrideForIds(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp,
        fotoIds: List<Long>,
        text: String?
    ) {
        val map = loadOverrides(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        val cleaned = text?.trim().orEmpty()
        if (cleaned.isBlank()) {
            fotoIds.forEach { map.remove(it) }
        } else {
            fotoIds.forEach { map[it] = cleaned }
        }

        // Wenn leer -> Datei löschen (optional)
        if (map.isEmpty()) {
            runCatching { fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht).delete() }
            return
        }

        saveOverrides(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht, map)
    }

    
fun fileFor(
    context: Context,
    baustelleId: Long,
    datumIso: String,
    shiftStart: String?,
    shiftEnd: String?
): File = fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, SchichtTyp.TAG)


    /** Löscht alle gespeicherten Text-Overrides für dieses Datum+Schicht (alle Zeiten). */
    fun deleteAllFor(context: Context, baustelleId: Long, datumIso: String, schicht: SchichtTyp) {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) return
        val st = schicht.name.lowercase()
        val prefixNew = "ovr_${baustelleId}_${datumIso}_${st}_"
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith(prefixNew)) runCatching { f.delete() }
        }
        // Legacy nur für TAG (ohne schichtKey)
        if (schicht == SchichtTyp.TAG) {
            val prefixLegacy = "ovr_${baustelleId}_${datumIso}_"
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(prefixLegacy) && !f.name.contains("_tag_") && !f.name.contains("_nacht_")) {
                    runCatching { f.delete() }
                }
            }
        }
    }

fun fileForWrite(
    context: Context,
    baustelleId: Long,
    datumIso: String,
    shiftStart: String?,
    shiftEnd: String?,
    schicht: SchichtTyp
): File {
    val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
    val s = BtbPhotoSelectionStore.safeTimeKey(shiftStart)
    val e = BtbPhotoSelectionStore.safeTimeKey(shiftEnd)
    val st = schicht.name.lowercase()
    val name = "ovr_${baustelleId}_${datumIso}_${st}_${s}_${e}.json"
    return File(dir, name)
}

fun fileForRead(
    context: Context,
    baustelleId: Long,
    datumIso: String,
    shiftStart: String?,
    shiftEnd: String?,
    schicht: SchichtTyp
): File {
    val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
    val newF = fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
    if (newF.exists()) return newF
    val s = BtbPhotoSelectionStore.safeTimeKey(shiftStart)
    val e = BtbPhotoSelectionStore.safeTimeKey(shiftEnd)
    val legacy = File(dir, "ovr_${baustelleId}_${datumIso}_${s}_${e}.json")
    return legacy
}
}
