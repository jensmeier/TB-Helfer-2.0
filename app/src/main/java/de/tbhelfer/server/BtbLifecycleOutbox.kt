package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.server.recovery.RecoveryWorkspace
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

/** Synchronous disk commit BEFORE scheduling. Preferences follow the recovery workspace. */
object BtbLifecycleOutbox {
    data class Intent(
        val localId: Long, val btbId: String, val target: BtbLifecyclePolicy.Target,
        val changeId: String, val owner: String, val allowUnfinished: Boolean = false,
        val problem: String = "", val participantsBlocked: Boolean = false
    )
    private const val PREFS = "tbhelfer_btb_lifecycle_outbox_v1"
    private val lock = Any()
    val running = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    val processMutex = Mutex()
    private fun prefs(context: Context) = RecoveryWorkspace.wrap(context.applicationContext)
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun owner(context: Context): String? = SecureTokenStore(context).load()?.let { token ->
        MessageDigest.getInstance("SHA-256").digest((ServerConfig.BASE_URL + "\n" + token).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    private fun encode(i: Intent) = JSONObject().put("local", i.localId).put("btb", i.btbId)
        .put("target", i.target.name).put("id", i.changeId).put("owner", i.owner)
        .put("force", i.allowUnfinished).put("problem", i.problem).put("participants", i.participantsBlocked).toString()
    private fun decode(raw: String): Intent {
        val j = JSONObject(raw)
        return Intent(j.getLong("local"), j.getString("btb"), BtbLifecyclePolicy.Target.valueOf(j.getString("target")),
            j.getString("id"), j.getString("owner"), j.optBoolean("force"), j.optString("problem"), j.optBoolean("participants"))
    }
    fun all(context: Context): List<Intent> = synchronized(lock) {
        prefs(context).all.values.mapNotNull { raw -> (raw as? String)?.let { runCatching { decode(it) }.getOrNull() } }
    }
    fun get(context: Context, btbId: String): Intent? = all(context).firstOrNull { it.btbId == btbId }
    fun put(context: Context, localId: Long, btbId: String, target: BtbLifecyclePolicy.Target, force: Boolean = false): Intent = synchronized(lock) {
        require(btbId.isNotBlank())
        val previous = get(context, btbId)
        if (previous != null && !force) return@synchronized previous
        check(!force || previous?.participantsBlocked == true) { "Teilnehmerfreigabe nicht bestätigt." }
        val i = Intent(localId, btbId, target, "android-lifecycle-${UUID.randomUUID()}",
            checkNotNull(owner(context)) { "Gerät ist nicht aktiviert." }, force)
        check(prefs(context).edit().putString(btbId, encode(i)).commit()) { "Abschlussauftrag konnte nicht gespeichert werden." }
        i
    }
    fun problem(context: Context, i: Intent, message: String, participants: Boolean = false) = synchronized(lock) {
        if (get(context, i.btbId)?.changeId == i.changeId)
            check(prefs(context).edit().putString(i.btbId, encode(i.copy(problem = message, participantsBlocked = participants))).commit())
    }
    fun remove(context: Context, i: Intent) = synchronized(lock) {
        if (get(context, i.btbId)?.changeId == i.changeId) check(prefs(context).edit().remove(i.btbId).commit())
    }
    fun status(context: Context, btbId: String): String? = get(context, btbId)?.let {
        when {
            it.btbId in running -> "Abschluss wird mit dem Server abgeglichen …"
            it.problem.isNotBlank() -> "Abschluss ausstehend: ${it.problem}"
            else -> "Abschluss auf dem Handy vorgemerkt · Serverbestätigung ausstehend"
        }
    }
}
