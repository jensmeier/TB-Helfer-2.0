package de.tbhelfer.speech

import android.content.Context
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.ServerConfig
import de.tbhelfer.server.recovery.RecoveryWorkspace
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.security.MessageDigest

/** Private, generation- and activation-bound text drafts. Never part of BTB JSON or raw logs. */
object SpeechDraftStore {
    private val lock = Any()
    val changes = MutableStateFlow(0L)
    fun signal() { synchronized(lock) { changes.value += 1L } }
    private fun prefs(c: Context) = RecoveryWorkspace.wrap(c.applicationContext).getSharedPreferences("tbhelfer_speech_drafts_v1", Context.MODE_PRIVATE)
    fun owner(c: Context): String? {
        val token = SecureTokenStore(c).load()?.takeIf { it.isNotBlank() } ?: return null
        return MessageDigest.getInstance("SHA-256").digest((ServerConfig.BASE_URL + "|" + token).toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun list(c: Context, btbId: String): List<SpeechDraft> = synchronized(lock) {
        val owner = owner(c) ?: return emptyList()
        prefs(c).all.values.mapNotNull { raw ->
            if (raw !is String) null else runCatching {
                val j = JSONObject(raw)
                SpeechDraft(j.getString("id"),j.getString("owner"),j.getLong("site"),j.getString("name"),j.getString("btb"),j.getString("text"),
                    j.optInt("section").takeIf { it in 4..8 },j.optString("firm").takeIf { j.has("firm") && !j.isNull("firm") },j.getLong("created"),j.optLong("node",-1L).takeIf{it>=0L},
                    originalText=j.optString("originalText").takeIf { j.has("originalText") && !j.isNull("originalText") },
                    fieldsParsed=j.optBoolean("fieldsParsed",false),timeFrom=j.optString("timeFrom"),timeTo=j.optString("timeTo"),
                    kmFrom=j.optString("kmFrom"),kmTo=j.optString("kmTo"),parseNotice=j.optString("parseNotice"))
            }.getOrNull()
        }.filter { it.owner == owner && it.btbId == btbId }.sortedBy { it.created }
    }
    fun put(c: Context, d: SpeechDraft) = synchronized(lock) {
        check(d.owner == owner(c)) { "Anmeldung gewechselt. Bitte erneut öffnen." }
        check(!RecoveryWorkspace.blocked()) { "Wiederherstellung aktiv" }
        val j = JSONObject().put("id",d.id).put("owner",d.owner).put("site",d.siteId).put("name",d.siteName)
            .put("btb",d.btbId).put("text",d.text).put("section",d.section ?: 0).put("firm",d.firmId ?: JSONObject.NULL).put("created",d.created).put("node",d.nodeId ?: -1L)
        j.put("originalText",d.originalText ?: JSONObject.NULL).put("fieldsParsed",d.fieldsParsed)
            .put("timeFrom",d.timeFrom).put("timeTo",d.timeTo).put("kmFrom",d.kmFrom).put("kmTo",d.kmTo).put("parseNotice",d.parseNotice)
        check(prefs(c).edit().putString(d.id,j.toString()).commit()) { "Sprachentwurf konnte nicht gesichert werden" }
        signal()
    }
    fun remove(c: Context, d: SpeechDraft) = synchronized(lock) {
        check(d.owner == owner(c)) { "Anmeldung gewechselt" }
        check(prefs(c).edit().remove(d.id).commit()) { "Entwurf bleibt zur Prüfung erhalten" }
        signal()
    }
    fun enabled(c: Context) = prefs(c).getBoolean("enabled",true)
    fun online(c: Context) = prefs(c).getBoolean("online",false)
    fun setting(c: Context, key: String, value: Boolean) { check(key in setOf("enabled","online")); check(prefs(c).edit().putBoolean(key,value).commit()); signal() }
}

