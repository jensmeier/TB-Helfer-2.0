package de.tbhelfer.ui.bautagebuch

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Persistiert BTB/PDF-Startzeit-Overrides pro BTB als JSON-Datei.
 *
 * Neue Multi-BTB-Berichte verwenden automatisch die aktive stabile BTB-ID als zusätzlichen
 * Dateischlüssel. Altbestände ohne BTB-ID bleiben über Datum/Schicht/Zeit lesbar.
 */
object BtbPhotoStartOverrideStore {

    private const val DIR_NAME = "btb_photo_start_overrides"
    private const val VERSION = 2

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
            put("btbId", BtbPhotoInstanzKey.activeBtbId(context, baustelleId) ?: JSONObject.NULL)
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

    /** time == null/blank => Override entfernen. */
    fun setOverrideForIds(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        fotoIds: List<Long>,
        time: String?
    ) = setOverrideForIds(context, baustelleId, datumIso, shiftStart, shiftEnd, SchichtTyp.TAG, fotoIds, time)

    fun setOverrideForIds(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp,
        fotoIds: List<Long>,
        time: String?
    ) {
        val map = loadOverrides(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        val cleaned = time?.trim().orEmpty()
        if (cleaned.isBlank()) {
            fotoIds.forEach { map.remove(it) }
        } else {
            fotoIds.forEach { map[it] = cleaned }
        }

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

    fun deleteAllFor(context: Context, baustelleId: Long, datumIso: String, schicht: SchichtTyp) {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) return
        val token = BtbPhotoInstanzKey.activeToken(context, baustelleId)
        if (token != null) {
            val prefix = "ovr_${baustelleId}_btb_${token}_"
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(prefix)) runCatching { f.delete() }
            }
            return
        }

        val st = schicht.name.lowercase()
        val prefixNew = "ovr_${baustelleId}_${datumIso}_${st}_"
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith(prefixNew)) runCatching { f.delete() }
        }
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
        val token = BtbPhotoInstanzKey.activeToken(context, baustelleId)
        val name = if (token != null) {
            "ovr_${baustelleId}_btb_${token}_${datumIso}_${st}_${s}_${e}.json"
        } else {
            "ovr_${baustelleId}_${datumIso}_${st}_${s}_${e}.json"
        }
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
        val token = BtbPhotoInstanzKey.activeToken(context, baustelleId)
        val newF = fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        if (token != null || newF.exists()) return newF
        val s = BtbPhotoSelectionStore.safeTimeKey(shiftStart)
        val e = BtbPhotoSelectionStore.safeTimeKey(shiftEnd)
        return File(dir, "ovr_${baustelleId}_${datumIso}_${s}_${e}.json")
    }
}
