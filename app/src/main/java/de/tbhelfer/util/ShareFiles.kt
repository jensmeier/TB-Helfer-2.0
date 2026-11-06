package de.tbhelfer.util

import android.net.Uri
import androidx.core.content.FileProvider
import de.tbhelfer.server.recovery.RecoveryWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShareFiles {
    /** Existing public app paths stay zero-copy; private photos use a bounded provider root. */
    suspend fun uri(file: File): Uri = withContext(Dispatchers.IO) {
        val raw = RecoveryWorkspace.raw
        val authority = "${raw.packageName}.dateianbieter"
        require(file.isFile && file.canRead() && file.length() > 0L) { "Datei fehlt oder ist nicht lesbar." }
        try {
            FileProvider.getUriForFile(raw, authority, file)
        } catch (_: IllegalArgumentException) {
            val root = File(raw.cacheDir, "share-outgoing/${RecoveryWorkspace.generation ?: "legacy"}")
            val copy = ShareStaging.copy(file, root)
            try {
                FileProvider.getUriForFile(raw, authority, copy)
            } catch (error: Exception) {
                copy.delete()
                copy.parentFile?.delete()
                throw error
            }
        }
    }
}
