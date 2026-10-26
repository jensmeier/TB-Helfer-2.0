package de.tbhelfer.server

import android.content.Context
import android.content.SharedPreferences
import de.tbhelfer.BuildConfig
import de.tbhelfer.server.recovery.RecoveryWorkspace
import org.json.JSONArray
import org.json.JSONObject

/** Bounded local diagnostics; no network, no raw message persistence, never breaks sync. */
object ErrorLogStore {
    private val lock = Any()
    private fun prefs(c: Context) = RecoveryWorkspace.wrap(c.applicationContext)
        .getSharedPreferences("tbhelfer_error_history_v1", Context.MODE_PRIVATE)
    private fun read(store: SharedPreferences): List<ErrorHistory.Entry> {
        val array = JSONArray(store.getString("entries", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { index -> runCatching {
            val j = array.getJSONObject(index)
            ErrorHistory.restore(j.getString("id"), j.getString("op"), j.getString("kind"), j.optLong("context"),
                j.getString("code"), j.getLong("first"), j.getLong("last"), j.getLong("count"), j.getInt("build"))
        }.getOrNull() }.sortedByDescending { it.lastMs }.take(ErrorHistory.LIMIT)
    }
    fun entries(c: Context): List<ErrorHistory.Entry> = synchronized(lock) { runCatching { read(prefs(c)) }.getOrDefault(emptyList()) }
    fun record(c: Context, op: ErrorHistory.Operation, raw: String,
               kind: ErrorHistory.ContextKind = ErrorHistory.ContextKind.NONE, contextId: Long = 0L): String? = synchronized(lock) {
        runCatching {
            val store = prefs(c) // Use one recovery-scoped preference instance for the entire operation.
            val before = read(store)
            val after = ErrorHistory.record(before, op, kind, contextId, raw, System.currentTimeMillis(), BuildConfig.VERSION_CODE)
            val array = JSONArray()
            after.forEach { e -> array.put(JSONObject().put("id", e.id).put("op", e.operation.name).put("kind", e.contextKind.name)
                .put("context", e.contextId).put("code", e.code.name).put("first", e.firstMs).put("last", e.lastMs)
                .put("count", e.count).put("build", e.build)) }
            check(store.edit().putString("entries", array.toString()).commit())
            after.firstOrNull { it.operation == op && it.contextKind == kind && it.contextId == (if (kind == ErrorHistory.ContextKind.NONE) 0L else contextId.coerceAtLeast(0L)) &&
                it.code == ErrorHistory.classify(raw) && it.build == BuildConfig.VERSION_CODE }?.id
        }.getOrNull()
    }
    fun clear(c: Context): Boolean = synchronized(lock) { runCatching { prefs(c).edit().clear().commit() }.getOrDefault(false) }
}
