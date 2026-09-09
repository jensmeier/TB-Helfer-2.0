package de.tbhelfer.ui.baustelle

import android.content.Context
import org.json.JSONObject

/**
 * Kleiner lokaler Spiegel für Mängel, die von einem anderen Gerät über den Baustellen-Sync kommen.
 *
 * Der vorhandene Mängelspeicher bleibt unverändert die Quelle für lokale Foto-/BTB-Mängel. Dieser
 * Speicher ergänzt nur Vorgänge, die sonst auf dem Gerät noch keine lokale Foto- oder BTB-Quelle
 * besitzen. Die Zuordnung erfolgt ausschließlich über die explizit verknüpfte lokale Baustelle.
 */
object SyncMangelSpeicher {

    data class Eintrag(
        val localBaustelleId: Long,
        val worksiteServerId: String,
        val serverEntityId: String,
        val mangelKey: String,
        val datumIso: String = "",
        val zeit: String = "",
        val bereich: String = "Hauptbaustelle",
        val km: String = "-",
        val firma: String = "-",
        val beschreibung: String = "-",
        val gps: String = "-",
        val btbBildNr: String = "",
        val changedByName: String? = null,
        val updatedAtEpochMs: Long = System.currentTimeMillis()
    )

    private const val PREF = "tb_helfer_sync_maengel_v1"

    @Synchronized
    fun speichern(context: Context, eintrag: Eintrag) {
        if (eintrag.localBaustelleId <= 0L || eintrag.mangelKey.isBlank() || eintrag.serverEntityId.isBlank()) return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(storageKey(eintrag.localBaustelleId, eintrag.mangelKey), toJson(eintrag).toString())
            .apply()
    }

    fun alle(context: Context, localBaustelleId: Long): List<Eintrag> {
        if (localBaustelleId <= 0L) return emptyList()
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .all
            .values
            .mapNotNull { it as? String }
            .mapNotNull(::parse)
            .filter { it.localBaustelleId == localBaustelleId }
            .sortedWith(compareBy<Eintrag> { it.datumIso }.thenBy { it.zeit }.thenBy { it.beschreibung })
    }

    @Synchronized
    fun loeschen(context: Context, localBaustelleId: Long, mangelKey: String) {
        if (localBaustelleId <= 0L || mangelKey.isBlank()) return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(storageKey(localBaustelleId, mangelKey))
            .apply()
    }

    @Synchronized
    fun bereinigeNichtMehrVorhandene(
        context: Context,
        localBaustelleId: Long,
        worksiteServerId: String,
        aktiveServerEntityIds: Set<String>
    ) {
        if (localBaustelleId <= 0L || worksiteServerId.isBlank()) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            val item = (value as? String)?.let(::parse) ?: return@forEach
            if (item.localBaustelleId == localBaustelleId &&
                item.worksiteServerId == worksiteServerId &&
                item.serverEntityId !in aktiveServerEntityIds
            ) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    private fun storageKey(localBaustelleId: Long, mangelKey: String): String =
        "$localBaustelleId:${mangelKey.hashCode()}:${mangelKey.length}"

    private fun toJson(e: Eintrag): JSONObject = JSONObject().apply {
        put("localBaustelleId", e.localBaustelleId)
        put("worksiteServerId", e.worksiteServerId)
        put("serverEntityId", e.serverEntityId)
        put("mangelKey", e.mangelKey)
        put("datumIso", e.datumIso)
        put("zeit", e.zeit)
        put("bereich", e.bereich)
        put("km", e.km)
        put("firma", e.firma)
        put("beschreibung", e.beschreibung)
        put("gps", e.gps)
        put("btbBildNr", e.btbBildNr)
        put("changedByName", e.changedByName ?: JSONObject.NULL)
        put("updatedAtEpochMs", e.updatedAtEpochMs)
    }

    private fun parse(raw: String): Eintrag? = runCatching {
        val o = JSONObject(raw)
        Eintrag(
            localBaustelleId = o.optLong("localBaustelleId", -1L),
            worksiteServerId = o.optString("worksiteServerId", ""),
            serverEntityId = o.optString("serverEntityId", ""),
            mangelKey = o.optString("mangelKey", ""),
            datumIso = o.optString("datumIso", ""),
            zeit = o.optString("zeit", ""),
            bereich = o.optString("bereich", "Hauptbaustelle").ifBlank { "Hauptbaustelle" },
            km = o.optString("km", "-").ifBlank { "-" },
            firma = o.optString("firma", "-").ifBlank { "-" },
            beschreibung = o.optString("beschreibung", "-").ifBlank { "-" },
            gps = o.optString("gps", "-").ifBlank { "-" },
            btbBildNr = o.optString("btbBildNr", ""),
            changedByName = if (!o.has("changedByName") || o.isNull("changedByName")) null else o.optString("changedByName").takeIf { it.isNotBlank() },
            updatedAtEpochMs = o.optLong("updatedAtEpochMs", System.currentTimeMillis())
        )
    }.getOrNull()?.takeIf {
        it.localBaustelleId > 0L && it.worksiteServerId.isNotBlank() && it.serverEntityId.isNotBlank() && it.mangelKey.isNotBlank()
    }
}
