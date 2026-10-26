package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.server.recovery.RecoveryWorkspace
import org.json.JSONObject

/** Authoritative context revocation; never removes local drafts or pending mutations. */
object WorksiteAccessStore {
    private fun prefs(c: Context) = RecoveryWorkspace.wrap(c.applicationContext)
        .getSharedPreferences("tbhelfer_worksite_access_v1", Context.MODE_PRIVATE)
    fun replace(c: Context, contexts: List<TeamManagementApi.WorksiteContext>) {
        val j = JSONObject()
        contexts.forEach { j.put(it.worksiteId, JSONObject().put("team", it.groupId).put("role", it.memberRole)) }
        check(prefs(c).edit().putString("contexts", j.toString()).commit())
    }
    fun readOnly(c: Context, worksite: String, team: String): Boolean {
        val raw = prefs(c).getString("contexts", null) ?: return false // pre-upgrade offline state
        val item = JSONObject(raw).optJSONObject(worksite) ?: return true
        return item.optString("team") != team || item.optString("role") !in setOf("LEAD", "HELPER")
    }
}
