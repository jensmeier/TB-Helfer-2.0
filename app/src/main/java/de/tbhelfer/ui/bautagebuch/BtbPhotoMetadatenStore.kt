package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.datenbank.AufnahmeMetadaten
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.server.BtbServerStateStore
import org.json.JSONObject

/** Original-Aufnahmeangaben und wirksame BTB-Linkkorrekturen bleiben getrennt. */
object BtbPhotoMetadatenStore {
    private val updates = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> get() = updates
    @Synchronized fun notifyChanges() { updates.value = updates.value + 1L }
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("btb_photo_metadata_v1", Context.MODE_PRIVATE)
    private fun read(context: Context, key: String): JSONObject? = prefs(context).getString(key, null)?.let {
        runCatching { JSONObject(it) }.getOrNull()
    }

    @Synchronized
    fun source(context: Context, fotoId: Long, lat: Double?, lon: Double?, epoch: Long?, accuracy: Double? = null) {
        val validGps = lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
        val json = JSONObject().put("lat", lat.takeIf { validGps } ?: JSONObject.NULL).put("lon", lon.takeIf { validGps } ?: JSONObject.NULL)
            .put("epoch", epoch?.takeIf { it >= 0 } ?: JSONObject.NULL)
            .put("accuracy", accuracy?.takeIf { it.isFinite() && it >= 0 } ?: JSONObject.NULL)
        check(prefs(context).edit().putString("source:$fotoId", json.toString()).commit())
        notifyChanges()
    }

    /** Ein vorhandenes photo_metadata-Objekt ist vollstaendig, auch wenn GPS darin fehlt. */
    fun merge(server: JSONObject?, legacy: JSONObject?): JSONObject {
        val structured = server?.optJSONObject("photo_metadata")
        val out = if (structured != null) JSONObject(structured.toString()) else
            legacy?.let { JSONObject(it.toString()) } ?: JSONObject()
        if (!out.has("epoch") && server?.has("captured_at") == true) {
            val epoch = if (server.isNull("captured_at")) null else runCatching {
                java.time.OffsetDateTime.parse(server.optString("captured_at")).toInstant().toEpochMilli()
            }.getOrNull()
            out.put("epoch", epoch ?: JSONObject.NULL)
        }
        if (structured == null) {
            for ((to, from) in listOf("lat" to "gps_lat", "lon" to "gps_lon")) {
                if (server?.has(from) == true) out.put(to, server.opt(from))
            }
        }
        if (server?.has("gps_accuracy_m") == true) out.put("accuracy", server.opt("gps_accuracy_m"))
        return out
    }

    @Synchronized
    fun rememberServer(context: Context, btbId: String, fotoId: Long, server: JSONObject, legacy: JSONObject? = null, localNodeId: Long? = null) {
        val meta = merge(server, legacy).put("_localNodeId", localNodeId ?: JSONObject.NULL).toString()
        val key = "server:$btbId:$fotoId"
        if (prefs(context).getString(key, null) == meta) return
        check(prefs(context).edit().putString(key, meta).commit())
        notifyChanges()
    }

    fun serverMetadata(context: Context, btbId: String, fotoId: Long): JSONObject? = read(context, "server:$btbId:$fotoId")

    @Synchronized
    fun forget(context: Context, fotoId: Long) {
        val preferences = prefs(context)
        val edit = preferences.edit().remove("source:$fotoId")
        preferences.all.keys.filter {
            (it.startsWith("server:") || it.startsWith("binding:")) && it.endsWith(":$fotoId")
        }.forEach { edit.remove(it) }
        check(edit.commit())
        notifyChanges()
    }

    data class EntryIntent(val clientEntryKey: String?)
    @Synchronized
    fun bind(context: Context, btbId: String, fotoId: Long, key: String?) {
        if (btbId.isBlank() || fotoId <= 0) return
        val value = JSONObject().put("key", key?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        check(prefs(context).edit().putString("binding:$btbId:$fotoId", value.toString()).commit())
    }
    fun pending(context: Context, btbId: String, fotoId: Long): EntryIntent? = read(context, "binding:$btbId:$fotoId")?.let {
        EntryIntent(if (it.isNull("key")) null else it.optString("key"))
    }
    @Synchronized
    fun acknowledge(context: Context, btbId: String, fotoId: Long, expected: EntryIntent?) {
        if (expected != null && pending(context, btbId, fotoId) == expected)
            check(prefs(context).edit().remove("binding:$btbId:$fotoId").commit())
    }
    fun entryKey(context: Context, btbId: String, fotoId: Long): String? {
        pending(context, btbId, fotoId)?.let { return it.clientEntryKey }
        val meta = serverMetadata(context, btbId, fotoId) ?: return null
        val id = if (meta.isNull("btbEntryId")) return null else meta.optString("btbEntryId")
        return BtbServerStateStore.entryKey(context, btbId, id)
    }

    fun apply(context: Context, foto: FotoEntity): FotoEntity {
        val btbId = BtbFotoZuordnungSpeicher.btbId(context, foto.id)
        val server = btbId?.let { serverMetadata(context, it, foto.id) }
        val meta = server ?: read(context, "source:${foto.id}") ?: return foto
        val lat = number(meta, "lat")?.takeIf { it in -90.0..90.0 }
        val lon = number(meta, "lon")?.takeIf { it in -180.0..180.0 }
        val epoch = if (meta.isNull("epoch")) null else meta.optLong("epoch", -1L).takeIf { it >= 0L }
        val out = if (server != null) foto.copy(
            baustellenKnotenId = number(meta, "_localNodeId")?.toLong(),
            firma = if (meta.isNull("firma")) "" else meta.optString("firma"),
            bildtyp = runCatching { Bildtyp.valueOf(meta.optString("typ")) }.getOrDefault(foto.bildtyp),
            kurztext = if (meta.isNull("kurztext")) "" else meta.optString("kurztext"),
            km = number(meta, "km"),
            objektKennung = if (meta.isNull("objektKennung")) null else meta.optString("objektKennung"),
            objektBereich = if (meta.isNull("objektBereich")) null else meta.optString("objektBereich"),
            erstelltAmEpochMs = epoch ?: 0L
        ) else foto
        out.btbMetadaten = AufnahmeMetadaten(
            lat.takeIf { lon != null }, lon.takeIf { lat != null }, epoch,
            number(meta, "accuracy")?.takeIf { it >= 0.0 }
        )
        return out
    }

    private fun number(json: JSONObject, key: String): Double? = if (json.isNull(key)) null else
        json.optDouble(key, Double.NaN).takeIf { it.isFinite() }
}
