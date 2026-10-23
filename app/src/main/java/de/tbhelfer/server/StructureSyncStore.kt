package de.tbhelfer.server

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

/** Dirty generations and confirmed server versions; never names as identity. */
object StructureSyncStore {
    private const val PREFS = "tbhelfer_structure_sync_v1"
    val mutationMutex = Mutex()
    private val lock = Any()
    private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    fun transferring(rootId: Long, running: Boolean) {
        if (running) active.add(rootId) else active.remove(rootId)
        SelectiveSyncStore.changed()
    }
    fun problem(context: Context, rootId: Long, message: String, expected: Long) = synchronized(lock) {
        if (generation(context, rootId) == expected) {
            check(prefs(context).edit().putString("problem:$rootId", message).commit())
            SelectiveSyncStore.changed()
        }
    }
    fun status(context: Context, rootId: Long): String? {
        val p = prefs(context)
        val dirty = generation(context, rootId)
        if (dirty == 0L && !p.contains("confirmed:$rootId")) return null
        val state = BtbSyncPresentation.text(dirty, if (rootId in active) dirty else null,
            p.getLong("confirmed:$rootId", 0L), p.getLong("latest:$rootId", 0L), p.getString("problem:$rootId", null))
        return "Unterbaustellen: $state"
    }
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mark(context: Context, rootId: Long) = synchronized(lock) {
        val p = prefs(context)
        val generation = maxOf(System.currentTimeMillis(), p.getLong("latest:$rootId", 0L) + 1L)
        check(p.edit().putLong("dirty:$rootId", generation).putLong("latest:$rootId", generation).remove("problem:$rootId").commit())
        SelectiveSyncStore.changed()
    }
    fun generation(context: Context, rootId: Long): Long = prefs(context).getLong("dirty:$rootId", 0L)
    fun roots(context: Context): Set<Long> = prefs(context).all.keys.mapNotNull { key ->
        key.takeIf { it.startsWith("dirty:") }?.removePrefix("dirty:")?.toLongOrNull()
    }.toSet()
    fun clear(context: Context, rootId: Long, expected: Long): Boolean = synchronized(lock) {
        if (generation(context, rootId) != expected) return@synchronized false
        val editor = prefs(context).edit().remove("dirty:$rootId").remove("problem:$rootId")
        if (expected != 0L) editor.putLong("confirmed:$rootId", expected)
        val saved = editor.commit()
        SelectiveSyncStore.changed()
        saved
    }
    fun versions(context: Context, worksiteId: String): Map<String, Long> {
        val json = JSONObject(prefs(context).getString("versions:$worksiteId", "{}") ?: "{}")
        return json.keys().asSequence().associateWith { json.getLong(it) }
    }
    fun confirm(context: Context, worksiteId: String, versions: Map<String, Long>) {
        check(prefs(context).edit().putString("versions:$worksiteId", JSONObject(versions).toString()).commit())
    }
}
