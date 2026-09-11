package de.tbhelfer.ui.bautagebuch

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Persistiert KM-Overrides ausschliesslich fuer die BTB-/PDF-Darstellung.
 *
 * Neue Multi-BTB-Berichte verwenden automatisch die aktive stabile BTB-ID als zusätzlichen
 * Dateischlüssel. Altbestände ohne BTB-ID bleiben über Datum/Schicht/Zeit lesbar.
 *
 * Wichtig:
 * - FotoEntity.km bleibt unveraendert.
 * - Der Override gilt nur fuer den konkreten BTB.
 * - Fuer eine Fotogruppe wird derselbe Wert auf alle zugehoerigen Foto-IDs geschrieben.
 */
object BtbPhotoKmOverrideStore {

    data class KmOverride(
        val kmVon: Double,
        val kmBis: Double? = null
    )

    private const val DIR_NAME = "btb_photo_km_overrides"
    private const val VERSION = 2

    fun loadOverrides(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp
    ): MutableMap<Long, KmOverride> {
        val f = fileForRead(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        if (!f.exists()) return mutableMapOf()
        return try {
            val txt = f.readText(StandardCharsets.UTF_8)
            if (txt.isBlank()) return mutableMapOf()
            val root = JSONObject(txt)
            val obj = root.optJSONObject("overrides") ?: JSONObject()
            val out = linkedMapOf<Long, KmOverride>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val id = k.toLongOrNull() ?: continue
                val value = obj.optJSONObject(k) ?: continue
                val von = value.optDouble("kmVon", Double.NaN)
                if (!von.isFinite()) continue
                val bisRaw = value.optDouble("kmBis", Double.NaN)
                val bis = bisRaw.takeIf { it.isFinite() }
                out[id] = KmOverride(kmVon = von, kmBis = bis)
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
        schicht: SchichtTyp,
        overrides: Map<Long, KmOverride>
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
        overrides.forEach { (id, value) ->
            if (!value.kmVon.isFinite()) return@forEach
            obj.put(id.toString(), JSONObject().apply {
                put("kmVon", value.kmVon)
                value.kmBis?.takeIf { it.isFinite() }?.let { put("kmBis", it) }
            })
        }
        root.put("overrides", obj)

        tmp.writeText(root.toString(), StandardCharsets.UTF_8)
        if (f.exists()) f.delete()
        tmp.renameTo(f)
    }

    /** value == null => Automatik aus den Original-Foto-KM verwenden. */
    fun setOverrideForIds(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp,
        fotoIds: List<Long>,
        value: KmOverride?
    ) {
        val map = loadOverrides(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        if (value == null) {
            fotoIds.forEach { map.remove(it) }
        } else {
            fotoIds.forEach { map[it] = value }
        }
        if (map.isEmpty()) {
            runCatching { fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht).delete() }
        } else {
            saveOverrides(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht, map)
        }
    }

    fun deleteAllFor(context: Context, baustelleId: Long, datumIso: String, schicht: SchichtTyp) {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) return
        val token = BtbPhotoInstanzKey.activeToken(context, baustelleId)
        if (token != null) {
            val prefix = "kmovr_${baustelleId}_btb_${token}_"
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(prefix)) runCatching { f.delete() }
            }
            return
        }

        val st = schicht.name.lowercase()
        val prefix = "kmovr_${baustelleId}_${datumIso}_${st}_"
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith(prefix)) runCatching { f.delete() }
        }
    }

    private fun fileForWrite(
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
            "kmovr_${baustelleId}_btb_${token}_${datumIso}_${st}_${s}_${e}.json"
        } else {
            "kmovr_${baustelleId}_${datumIso}_${st}_${s}_${e}.json"
        }
        return File(dir, name)
    }

    /**
     * Neue BTBs mit stabiler ID fallen nicht auf den alten Datum-/Schicht-/Zeit-Key zurück.
     * Nur echte Altbestände ohne aktive BTB-ID lesen den bisherigen Dateinamen.
     */
    private fun fileForRead(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp
    ): File {
        val token = BtbPhotoInstanzKey.activeToken(context, baustelleId)
        val scoped = fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        if (token != null || scoped.exists()) return scoped

        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val s = BtbPhotoSelectionStore.safeTimeKey(shiftStart)
        val e = BtbPhotoSelectionStore.safeTimeKey(shiftEnd)
        val st = schicht.name.lowercase()
        return File(dir, "kmovr_${baustelleId}_${datumIso}_${st}_${s}_${e}.json")
    }
}
