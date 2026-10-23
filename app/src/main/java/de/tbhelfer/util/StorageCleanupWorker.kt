package de.tbhelfer.util

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.tbhelfer.ServiceLocator
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only photo storage audit. A missing file or DAO reference alone never proves
 * that a photo, pending upload, BTB attachment or original can safely be deleted.
 * The worker class/name stays stable for already scheduled WorkManager jobs.
 */
class StorageCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            ServiceLocator.initialisieren(applicationContext)
            val photos = ServiceLocator.fotoDao.alleFotoPfade()
            val knownPaths = photos.map { it.dateipfad }.toHashSet()
            val missing = photos.count { !File(it.dateipfad).exists() }
            var unassigned = 0
            var orphanThumbs = 0
            val base = applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val root = base?.let { File(it, "TB-Helfer") }
            if (root != null && root.isDirectory) {
                root.walkTopDown()
                    .onEnter { it == root || it.name !in setOf("_Orphans", "_Trash") }
                    .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
                    .forEach { image ->
                        if (image.name.contains("_thumb")) {
                            if (!File(image.parentFile, image.name.replace("_thumb", "")).exists()) orphanThumbs++
                        } else if (image.absolutePath !in knownPaths) {
                            unassigned++
                        }
                    }
            }
            Result.success(workDataOf(
                KEY_MISSING_DB_ENTRIES to missing,
                KEY_ORPHAN_CANDIDATES to unassigned,
                KEY_THUMB_CANDIDATES to orphanThumbs,
                // Retained output contract for older observers; nothing was modified.
                KEY_DB_DELETED to 0,
                KEY_ORPHAN_MOVED to 0,
                KEY_THUMB_DELETED to 0
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_DB_DELETED = "dbDeleted"
        const val KEY_ORPHAN_MOVED = "orphanMoved"
        const val KEY_THUMB_DELETED = "thumbDeleted"
        const val KEY_MISSING_DB_ENTRIES = "missingDbEntries"
        const val KEY_ORPHAN_CANDIDATES = "orphanCandidates"
        const val KEY_THUMB_CANDIDATES = "thumbCandidates"
    }
}

