package de.tbhelfer.server

import android.content.Context
import android.util.Base64

/**
 * Persistenter Dirty-Scope für selektive Fachsynchronisation.
 *
 * Wichtig: Scope wird VOR dem WorkManager-Trigger geschrieben. Dadurch gehen schnelle Änderungen
 * desselben BTB auch dann nicht verloren, wenn bereits ein SelectiveSyncWorker wartet oder läuft.
 */
object SelectiveSyncStore {
    private const val PREFS = "tbhelfer_selective_sync_v1"
    private const val BTB_PREFIX = "btb:"
    private val lock = Any()
    private val btbMutationLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    data class BtbScope(
        val localBaustelleId: Long,
        val clientBtbId: String,
        val generation: Long
    )

    fun markBtbDirty(context: Context, localBaustelleId: Long, clientBtbId: String): BtbScope {
        require(clientBtbId.isNotBlank()) { "BTB-ID darf nicht leer sein." }
        val appContext = context.applicationContext
        synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = btbKey(localBaustelleId, clientBtbId)
            val previous = prefs.getLong(key, 0L)
            val now = System.currentTimeMillis()
            val generation = maxOf(now, previous + 1L)
            prefs.edit().putLong(key, generation).commit()
            return BtbScope(localBaustelleId, clientBtbId, generation)
        }
    }

    fun btbScopes(context: Context): List<BtbScope> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.asSequence()
            .mapNotNull { (key, value) ->
                if (!key.startsWith(BTB_PREFIX)) return@mapNotNull null
                val generation = (value as? Long) ?: return@mapNotNull null
                parseBtbKey(key, generation)
            }
            .sortedBy { it.generation }
            .toList()
    }

    /**
     * Entfernt nur exakt den Stand, den der Worker gerade erfolgreich verarbeitet hat.
     * Kam während des Uploads eine neue Änderung desselben BTB hinzu, bleibt der Scope erhalten.
     */
    fun removeBtbIfUnchanged(context: Context, scope: BtbScope): Boolean {
        val appContext = context.applicationContext
        synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = btbKey(scope.localBaustelleId, scope.clientBtbId)
            val current = prefs.getLong(key, 0L)
            if (current != scope.generation) return false
            return prefs.edit().remove(key).commit()
        }
    }

    fun hasPending(context: Context): Boolean = btbScopes(context).isNotEmpty()

    fun isBtbDirty(
        context: Context,
        localBaustelleId: Long,
        clientBtbId: String
    ): Boolean {
        if (clientBtbId.isBlank()) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.contains(btbKey(localBaustelleId, clientBtbId))
    }

    /**
     * Serialisiert lokale BTB-Dateischreibvorgänge und kanonische Server-Applies für genau
     * denselben BTB. Dadurch kann zwischen Dirty-Prüfung und Dateischreibzugriff kein Autosave
     * unbemerkt dazwischenlaufen und anschließend vom Serverstand überschrieben werden.
     */
    inline fun <T> withBtbMutationLock(
        localBaustelleId: Long,
        clientBtbId: String,
        block: () -> T
    ): T {
        val key = "$localBaustelleId:$clientBtbId"
        val mutationLock = btbMutationLocks.getOrPut(key) { Any() }
        return synchronized(mutationLock) { block() }
    }

    fun clearBtb(context: Context, localBaustelleId: Long, clientBtbId: String): Boolean {
        if (clientBtbId.isBlank()) return false
        val appContext = context.applicationContext
        synchronized(lock) {
            return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(btbKey(localBaustelleId, clientBtbId))
                .commit()
        }
    }

    private fun btbKey(localBaustelleId: Long, clientBtbId: String): String =
        "$BTB_PREFIX$localBaustelleId:${encode(clientBtbId)}"

    private fun parseBtbKey(key: String, generation: Long): BtbScope? {
        val body = key.removePrefix(BTB_PREFIX)
        val split = body.indexOf(':')
        if (split <= 0 || split >= body.lastIndex) return null
        val localId = body.substring(0, split).toLongOrNull() ?: return null
        val btbId = decode(body.substring(split + 1)) ?: return null
        if (btbId.isBlank()) return null
        return BtbScope(localId, btbId, generation)
    }

    private fun encode(value: String): String =
        Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    private fun decode(value: String): String? = runCatching {
        String(
            Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8
        )
    }.getOrNull()
}
