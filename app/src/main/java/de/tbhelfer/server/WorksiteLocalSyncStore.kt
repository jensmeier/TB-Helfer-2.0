package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.server.recovery.RecoveryWorkspace
import org.json.JSONObject
import java.util.UUID

/** Durable receipts and frozen requests, separate from names and Room row identity. */
object WorksiteLocalSyncStore {
    private val lock = Any()
    private fun prefs(c: Context) = RecoveryWorkspace.wrap(c.applicationContext)
        .getSharedPreferences("tbhelfer_worksite_local_sync_v1", Context.MODE_PRIVATE)
    private fun read(c: Context, key: String): JSONObject? = prefs(c).getString(key, null)?.let(::JSONObject)
    private fun write(c: Context, key: String, value: JSONObject) {
        check(prefs(c).edit().putString(key, value.toString()).commit())
    }
    fun markInitial(c: Context, server: String) = synchronized(lock) { check(prefs(c).edit().putString("initial:$server", UUID.randomUUID().toString()).commit()) }
    fun initial(c: Context): Map<String, String> = prefs(c).all.filterKeys { it.startsWith("initial:") }
        .mapNotNull { (key, value) -> (value as? String)?.let { key.removePrefix("initial:") to it } }.toMap()
    fun confirmInitial(c: Context, ids: Map<String, String>) = synchronized(lock) {
        val p = prefs(c); val e = p.edit()
        ids.forEach { (id, generation) -> if (p.getString("initial:$id", null) == generation) e.remove("initial:$id") }
        check(e.commit())
    }
    fun blockBtb(c: Context, id: String) { check(prefs(c).edit().putBoolean("blockedBtb:$id", true).commit()) }
    fun btbBlocked(c: Context, id: String): Boolean = prefs(c).getBoolean("blockedBtb:$id", false)
    fun roots(c: Context): Set<Long> = prefs(c).all.keys.mapNotNull { key ->
        if (key.startsWith("create:") || key.startsWith("desired:")) key.substringAfter(':').toLongOrNull() else null
    }.toSet()
    fun hasCreation(c: Context): Boolean = prefs(c).all.keys.any { it.startsWith("create:") }
    fun pending(c: Context): Boolean = roots(c).isNotEmpty()
    fun problem(c: Context, root: Long, message: String) {
        check(prefs(c).edit().putString("problem:$root", message).commit()); SelectiveSyncStore.changed()
    }
    fun status(c: Context, root: Long): String? = if (root !in roots(c)) null else
        "Baustelle: Lokal gespeichert · " + (prefs(c).getString("problem:$root", null) ?: "Übertragung ausstehend")
    fun markCreate(c: Context, root: Long) = synchronized(lock) {
        if (read(c, "create:$root") == null) write(c, "create:$root", JSONObject().put("id", "android-worksite-${UUID.randomUUID()}")
            .put("owner", BtbLifecycleOutbox.owner(c) ?: ""))
    }
    fun createRequest(c: Context, root: Long, name: String): JSONObject = synchronized(lock) {
        markCreate(c, root)
        val j = checkNotNull(read(c, "create:$root"))
        val owner = checkNotNull(BtbLifecycleOutbox.owner(c))
        check(j.optString("owner").isBlank() || j.optString("owner") == owner) { "Baustellenauftrag gehört zu einer anderen Gerätefreigabe." }
        if (!j.has("name")) {
            j.put("name", name).put("owner", checkNotNull(BtbLifecycleOutbox.owner(c)))
            write(c, "create:$root", j)
        }
        j
    }
    fun rememberCreated(c: Context, root: Long, id: String, version: Long, name: String) = synchronized(lock) {
        val j = checkNotNull(read(c, "create:$root"))
        j.put("server", id).put("version", version).put("createdName", name)
        write(c, "create:$root", j)
    }
    fun createdRequests(c: Context): Map<Long, JSONObject> = roots(c).mapNotNull { root ->
        read(c, "create:$root")?.takeIf { it.has("server") }?.let { root to it }
    }.toMap()
    fun finishCreate(c: Context, root: Long) = synchronized(lock) { check(prefs(c).edit().remove("create:$root").remove("problem:$root").commit()) }
    fun desired(c: Context, root: Long): String? = prefs(c).getString("desired:$root", null)
    fun rename(c: Context, root: Long, name: String, previousName: String? = null) = synchronized(lock) {
        val p = prefs(c)
        val owner = BtbLifecycleOutbox.owner(c).orEmpty()
        val previousOwner = p.getString("headOwner:$root", owner)
        check(!p.contains("desired:$root") || previousOwner.isNullOrBlank() || previousOwner == owner) { "Namensänderung gehört zu einer anderen Gerätefreigabe." }
        val e = p.edit().remove("problem:$root").putString("desired:$root", name).putString("headOwner:$root", owner)
        if (!p.contains("desired:$root") && previousName != null) e.putString("previousName:$root", previousName)
        check(e.commit())
    }
    fun basis(c: Context, root: Long): JSONObject? = read(c, "basis:$root")
    fun rememberBasis(c: Context, root: Long, server: String, version: Long, name: String) = synchronized(lock) {
        write(c, "basis:$root", JSONObject().put("server", server).put("version", version).put("name", name))
    }
    fun seedMissingBasis(c: Context, root: Long, server: String, version: Long, remoteName: String) = synchronized(lock) {
        // Upgrade case: only the name field is patched, so an unchanged original name
        // permits a first CAS basis. A different server name remains an explicit conflict.
        if (basis(c, root) == null && prefs(c).getString("previousName:$root", null) == remoteName)
            rememberBasis(c, root, server, version, remoteName)
    }
    fun headRequest(c: Context, root: Long, server: String): JSONObject? = synchronized(lock) {
        read(c, "head:$root")?.let { return@synchronized it }
        val name = desired(c, root) ?: return@synchronized null
        val owner = prefs(c).getString("headOwner:$root", "").orEmpty()
        check(owner.isBlank() || owner == BtbLifecycleOutbox.owner(c)) { "Namensänderung gehört zu einer anderen Gerätefreigabe." }
        val base = basis(c, root) ?: error("Baustellenname: bestätigter Basisstand fehlt; zuerst Zuordnung prüfen.")
        check(base.getString("server") == server) { "Baustellenzuordnung hat sich geändert." }
        JSONObject().put("id", "android-worksite-head-${UUID.randomUUID()}").put("server", server)
            .put("owner", checkNotNull(BtbLifecycleOutbox.owner(c))).put("name", name)
            .put("base_version", base.getLong("version")).also { write(c, "head:$root", it) }
    }
    fun confirmHead(c: Context, root: Long, request: JSONObject, version: Long) = synchronized(lock) {
        rememberBasis(c, root, request.getString("server"), version, request.getString("name"))
        val e = prefs(c).edit().remove("head:$root").remove("problem:$root")
        if (desired(c, root) == request.getString("name")) e.remove("desired:$root")
        check(e.commit())
    }
}
