package de.tbhelfer.datenbank

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by file refresh and BTB upload/import: never import an upload before its mapping exists. */
internal object MediaSyncGate {
    private val mutex = Mutex()

    suspend fun <T> run(action: suspend () -> T): T = mutex.withLock { action() }
}

/** File identity, not a similar name/text/time, determines whether a download is needed. */
internal object LocalMediaReuse {
    fun path(
        mappedPhotoPath: String?,
        cachedPath: String?,
        cacheReady: Boolean,
        image: Boolean,
        serverSha256: String?,
        cachedSha256: String?
    ): String? {
        val remoteHash = serverSha256?.trim()?.takeIf { it.isNotEmpty() }
        val previousHash = cachedSha256?.trim()?.takeIf { it.isNotEmpty() }
        // A changed payload must not silently reuse the old image, including its mapped original.
        if (remoteHash != null && previousHash != null && !remoteHash.equals(previousHash, true)) return null
        usable(mappedPhotoPath)?.let { return it.absolutePath }
        if (!cacheReady) return null
        val file = usable(cachedPath) ?: return null
        if (!image || file.name.endsWith("_mobile_preview.jpg", ignoreCase = true)) return file.absolutePath
        // A locally prepared upload has different bytes from a preview, but exactly the server's
        // source hash. Verify the file itself; the stored hash alone is not sufficient.
        val expected = remoteHash ?: previousHash ?: return null
        if (!expected.matches(Regex("[0-9a-fA-F]{64}"))) return null
        val actual = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
        return file.absolutePath.takeIf { expected.equals(actual, ignoreCase = true) }
    }

    private fun usable(path: String?): File? = path?.takeIf { it.isNotBlank() }?.let(::File)
        ?.takeIf { it.isFile && it.canRead() && it.length() > 0L }

    fun confirmedUnlink(snapshotContainsPhoto: Boolean, attachmentKnown: Boolean,
                        attachmentDeleted: Boolean, sameParent: Boolean): Boolean =
        !snapshotContainsPhoto && attachmentKnown && (attachmentDeleted || !sameParent)
}
