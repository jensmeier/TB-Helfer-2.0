package de.tbhelfer.server

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

/** Dirty generations and confirmed server versions; never names as identity. */
object StructureSyncStore {
    private const val PREFS = "tbhelfer_structure_sync_v1"
    val mutationMutex = Mutex()
    private val lock = Any()
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mark(context: Context, rootId: Long) = synchronized(lock) {
        val p = prefs(context)
        check(p.edit().putLong("dirty:$rootId", maxOf(System.currentTimeMillis(), p.getLong("dirty:$rootId", 0L) + 1L)).commit())
    }
    fun generation(context: Context, rootId: Long): Long = prefs(context).getLong("dirty:$rootId", 0L)
    fun roots(context: Context): Set<Long> = prefs(context).all.keys.mapNotNull { key ->
        key.takeIf { it.startsWith("dirty:") }?.removePrefix("dirty:")?.toLongOrNull()
    }.toSet()
    fun clear(context: Context, rootId: Long, generation: Long): Boolean = synchronized(lock) {
        if (generation(context, rootId) != generation) return@synchronized false
        prefs(context).edit().remove("dirty:$rootId").commit()
    }
    fun versions(context: Context, worksiteId: String): Map<String, Long> {
        val json = JSONObject(prefs(context).getString("versions:$worksiteId", "{}") ?: "{}")
        return json.keys().asSequence().associateWith { json.getLong(it) }
    }
    fun confirm(context: Context, worksiteId: String, versions: Map<String, Long>) {
        check(prefs(context).edit().putString("versions:$worksiteId", JSONObject(versions).toString()).commit())
    }
}
