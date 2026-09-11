package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.datenbank.Bildtyp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Persistiert Foto-Auswahl pro BTB als JSON-Datei.
 *
 * Neue Multi-BTB-Berichte verwenden automatisch die aktive stabile BTB-ID als zusätzlichen
 * Dateischlüssel. Altbestände ohne BTB-ID bleiben über Datum/Schicht/Zeit rückwärtskompatibel.
 *
 * Robust-Strategie:
 * - Default: alle Fotos innerhalb des BTB-Zeitfensters sind ausgewählt.
 * - Persistiert werden nur ABGEWÄHLTE Foto-IDs (excluded).
 */
object BtbPhotoSelectionStore {

    private const val DIR_NAME = "btb_photo_selection"
    private const val VERSION = 2

    fun loadExcluded(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?
    ): MutableMap<String, MutableSet<Long>> =
        loadExcluded(context, baustelleId, datumIso, shiftStart, shiftEnd, SchichtTyp.TAG)

    fun loadExcluded(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp
    ): MutableMap<String, MutableSet<Long>> {
        val f = fileForRead(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        if (!f.exists()) return mutableMapOf()

        return try {
            val txt = f.readText(StandardCharsets.UTF_8)
            if (txt.isBlank()) return mutableMapOf()
            val root = JSONObject(txt)
            val out = mutableMapOf<String, MutableSet<Long>>()
            val excluded = root.optJSONObject("excludedByType") ?: JSONObject()
            val keys = excluded.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = excluded.optJSONArray(k) ?: JSONArray()
                val set = linkedSetOf<Long>()
                for (i in 0 until arr.length()) {
                    val v = arr.optLong(i, Long.MIN_VALUE)
                    if (v != Long.MIN_VALUE) set.add(v)
                }
                out[k] = set
            }
            out
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun saveExcluded(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        excludedByType: Map<String, Set<Long>>
    ) = saveExcluded(context, baustelleId, datumIso, shiftStart, shiftEnd, SchichtTyp.TAG, excludedByType)

    fun saveExcluded(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp,
        excludedByType: Map<String, Set<Long>>
    ) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val f = fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht)
        val tmp = File(dir, f.name + ".tmp")

        val root = JSONObject()
        root.put("version", VERSION)
        root.put("updatedEpochMs", System.currentTimeMillis())
        root.put("baustelleId", baustelleId)
        root.put("btbId", BtbPhotoInstanzKey.activeBtbId(context, baustelleId) ?: JSONObject.NULL)
        root.put("datumIso", datumIso)
        root.put("schicht", schicht.name)
        root.put("shiftStart", shiftStart ?: JSONObject.NULL)
        root.put("shiftEnd", shiftEnd ?: JSONObject.NULL)

        val excludedObj = JSONObject()
        for ((k, ids) in excludedByType) {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            excludedObj.put(k, arr)
        }
        root.put("excludedByType", excludedObj)

        tmp.writeText(root.toString(), StandardCharsets.UTF_8)
        if (f.exists()) f.delete()
        tmp.renameTo(f)
    }

    fun clear(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?
    ) = clear(context, baustelleId, datumIso, shiftStart, shiftEnd, SchichtTyp.TAG)

    fun clear(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp
    ) {
        runCatching { fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, schicht).delete() }
    }

    fun fileFor(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?
    ): File = fileForWrite(context, baustelleId, datumIso, shiftStart, shiftEnd, SchichtTyp.TAG)

    fun fileForWrite(
        context: Context,
        baustelleId: Long,
        datumIso: String,
        shiftStart: String?,
        shiftEnd: String?,
        schicht: SchichtTyp
    ): File {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val s = safeTimeKey(shiftStart)
        val e = safeTimeKey(shiftEnd)
        val st = schicht.name.lowercase()
        val token = BtbPhotoInstanzKey.activeToken(context, baustelleId)
        val name = if (token != null) {
            "sel_${baustelleId}_btb_${token}_${datumIso}_${st}_${s}_${e}.json"
        } else {
            "sel_${baustelleId}_${datumIso}_${st}_${s}_${e}.json"
        }
        return File(dir, name)
    }

    /**
     * Neue BTBs mit stabiler ID dürfen niemals in eine alte datum-/schichtbasierte Auswahl
     * zurückfallen. Nur echte Altbestände ohne aktive BTB-ID nutzen den Legacy-Fallback.
     */
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
        val s = safeTimeKey(shiftStart)
        val e = safeTimeKey(shiftEnd)
        return File(dir, "sel_${baustelleId}_${datumIso}_${s}_${e}.json")
    }

    fun excludedForType(excludedByType: Map<String, Set<Long>>, typ: Bildtyp): Set<Long> =
        excludedByType[typ.name] ?: emptySet()

    /**
     * Löscht beim Start eines neuen BTB nur dessen eigenen Zustand. Altlogik nach Datum+Schicht
     * wird nur verwendet, wenn keine stabile aktive BTB-ID vorhanden ist.
     */
    fun deleteAllFor(context: Context, baustelleId: Long, datumIso: String, schicht: SchichtTyp) {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) return
        val token = BtbPhotoInstanzKey.activeToken(context, baustelleId)
        if (token != null) {
            val prefix = "sel_${baustelleId}_btb_${token}_"
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(prefix)) runCatching { f.delete() }
            }
            return
        }

        val st = schicht.name.lowercase()
        val prefixNew = "sel_${baustelleId}_${datumIso}_${st}_"
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith(prefixNew)) runCatching { f.delete() }
        }
        if (schicht == SchichtTyp.TAG) {
            val prefixLegacy = "sel_${baustelleId}_${datumIso}_"
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(prefixLegacy) && !f.name.contains("_tag_") && !f.name.contains("_nacht_")) {
                    runCatching { f.delete() }
                }
            }
        }
    }

    fun safeTimeKey(time: String?): String {
        val t = time?.trim().orEmpty()
        if (t.isBlank()) return "-"

        val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(t)
        if (m != null) {
            val hh = m.groupValues[1].toIntOrNull() ?: return "-"
            val mm = m.groupValues[2].toIntOrNull() ?: return "-"
            return String.format("%02d%02d", hh, mm)
        }

        return t.replace(":", "").replace(Regex("[^0-9A-Za-z_-]"), "_")
    }
}
