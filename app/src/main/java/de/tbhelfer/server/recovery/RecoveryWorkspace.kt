package de.tbhelfer.server.recovery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** A generation is immutable for the lifetime of a process, including Room/DataStore instances. */
object RecoveryWorkspace {
    private const val REGISTRY = "tbhelfer_recovery_workspace"
    lateinit var raw: Context
        private set
    var generation: String? = null
        private set
    private lateinit var boot: JSONObject
    private val lock = Any()
    internal val preferenceNames = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    fun initialize(context: Context) {
        raw = context
        boot = read()
        generation = boot.optString("generation").takeIf { it.isNotBlank() }?.also {
            require(UUID.fromString(it).toString() == it) { "Ungültiger lokaler Datenbestand." }
        }
    }
    fun wrap(context: Context): Context = if (context is WorkspaceContext) context else WorkspaceContext(context)
    fun read(): JSONObject = synchronized(lock) {
        JSONObject(raw.getSharedPreferences(REGISTRY, Context.MODE_PRIVATE).getString("state", "{}") ?: "{}")
    }
    fun change(block: (JSONObject) -> Unit) = synchronized(lock) {
        val state = read()
        block(state)
        check(raw.getSharedPreferences(REGISTRY, Context.MODE_PRIVATE).edit().putString("state", state.toString()).commit()) {
            "Wiederherstellungsstatus konnte nicht gespeichert werden."
        }
    }
    fun acknowledged(owner: String): String? = boot.optString("epoch").takeIf { it.isNotBlank() && boot.optString("owner") == owner }
    fun ownerMatches(owner: String): Boolean = generation == null || boot.optString("owner") == owner
    fun startedBlocked(): Boolean = boot.optBoolean("blocked", false)
    fun switchPending(): Boolean = read().optString("generation").takeIf { it.isNotBlank() } != generation
    fun blocked(): Boolean = read().optBoolean("blocked", false) || switchPending()
    fun block(epoch: String) = change { it.put("blocked", true).put("observed", epoch) }
    fun journal(): File = File(raw.filesDir, "recovery-journal").apply { mkdirs() }
    fun databaseName(name: String): String = generation?.let {
        File(raw.filesDir, "recovery-workspaces/$it/databases/$name").apply { parentFile?.mkdirs() }.absolutePath
    } ?: name
    fun select(epoch: String, owner: String, archive: String) {
        require(RecoveryPolicy.epoch(epoch) != null)
        require(File(journal(), "$archive/local.zip").isFile)
        change {
            it.put("generation", UUID.randomUUID().toString()).put("epoch", epoch).put("owner", owner)
                .put("blocked", false).put("initial_sync", true).put("archive", archive)
        }
        // Never change generation/boot here. The old process MUST terminate first.
    }
    internal fun globalPreference(name: String): Boolean = name in setOf(
        REGISTRY, "tbhelfer_server_config", "tbhelfer_secure_session"
    ) || name.startsWith("androidx.") || name.startsWith("com.google.")

    private class WorkspaceContext(base: Context) : ContextWrapper(base) {
        private val id = generation
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            if (!globalPreference(name)) preferenceNames.add(name)
            val scoped = if (id != null && !globalPreference(name) && !name.startsWith("tbw_")) "tbw_${id}__$name" else name
            return raw.getSharedPreferences(scoped, mode)
        }
        override fun getFilesDir(): File = id?.let { File(raw.filesDir, "recovery-workspaces/$it/files").apply { mkdirs() } } ?: raw.filesDir
        override fun getCacheDir(): File = id?.let { File(raw.cacheDir, "recovery-workspaces/$it").apply { mkdirs() } } ?: raw.cacheDir
        override fun getNoBackupFilesDir(): File = id?.let { File(raw.noBackupFilesDir, "recovery-workspaces/$it").apply { mkdirs() } } ?: raw.noBackupFilesDir
        override fun getExternalFilesDir(type: String?): File? = external(raw.getExternalFilesDir(null), type)
        override fun getExternalFilesDirs(type: String?): Array<File> = raw.getExternalFilesDirs(null).mapNotNull { external(it, type) }.toTypedArray()
        private fun external(root: File?, type: String?): File? {
            if (id == null) return if (root == null || type == null) root else File(root, type).apply { mkdirs() }
            val parent = root?.let { File(it, "recovery-workspaces/$id") } ?: return null
            val result = if (type == null) parent else File(parent, type)
            if (!result.canonicalPath.startsWith(parent.canonicalPath + File.separator) && result.canonicalFile != parent.canonicalFile) throw IOException("Ungültiger Datenpfad.")
            return result.apply { mkdirs() }
        }
    }
}
