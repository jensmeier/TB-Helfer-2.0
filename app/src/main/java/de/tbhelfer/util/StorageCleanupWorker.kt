package de.tbhelfer.util

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.tbhelfer.ServiceLocator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Speicher-Bereinigung / Orphan-Checks:
 * - DB-Einträge, deren Datei nicht mehr existiert -> DB löschen
 * - Bilddateien ohne DB-Eintrag -> in "TB-Helfer/_Orphans/<ts>/..." verschieben
 * - Thumbnails ohne Original -> löschen
 */
class StorageCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Defensive: falls WorkManager den Worker sehr früh startet.
            runCatching { ServiceLocator.initialisieren(applicationContext) }

            val fotoPfade = ServiceLocator.fotoDao.alleFotoPfade()
            val dbPfadSet = fotoPfade.map { it.dateipfad }.toHashSet()

            // 1) DB-Einträge ohne Datei
            val missingIds = fotoPfade
                .asSequence()
                .filter { !File(it.dateipfad).exists() }
                .map { it.id }
                .toList()

            var dbDeleted = 0
            if (missingIds.isNotEmpty()) {
                dbDeleted = ServiceLocator.fotoDao.loeschenMitIds(missingIds)
            }

            // 2) Orphan-Dateien + 3) Thumbnail-Leichen
            var orphanMoved = 0
            var thumbDeleted = 0

            val base = applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val tbRoot = if (base != null) File(base, "TB-Helfer") else null

            if (tbRoot != null && tbRoot.exists()) {
                val orphanRoot = File(tbRoot, "_Orphans")
                val trashRoot = File(tbRoot, "_Trash")
                val runFolder = File(orphanRoot, System.currentTimeMillis().toString())

                fun isImage(f: File): Boolean {
                    val ext = f.extension.lowercase()
                    return ext == "jpg" || ext == "jpeg" || ext == "png"
                }

                fun isThumb(f: File): Boolean = f.name.contains("_thumb")

                // Thumbnails ohne Original: löschen
                tbRoot.walkTopDown()
                    .filter { it.isFile && isImage(it) && isThumb(it) }
                    .forEach { thumb ->
                        val originalName = thumb.name.replace("_thumb", "")
                        val original = File(thumb.parentFile, originalName)
                        if (!original.exists()) {
                            if (runCatching { thumb.delete() }.getOrDefault(false)) {
                                thumbDeleted++
                            }
                        }
                    }

                // Orphans: Bilder ohne DB-Eintrag -> verschieben
                tbRoot.walkTopDown()
                    .filter { it.isFile && isImage(it) && !isThumb(it) }
                    .forEach { f ->
                        // Orphans-Ordner nie nochmal anfassen
                        if (f.absolutePath.startsWith(orphanRoot.absolutePath)) return@forEach
                        // Papierkorb ebenfalls ignorieren (Undo-Fenster / atomare Löschungen)
                        if (f.absolutePath.startsWith(trashRoot.absolutePath)) return@forEach

                        // Wenn DB die Datei kennt: ok
                        if (dbPfadSet.contains(f.absolutePath)) return@forEach

                        // Zielpfad: relative Struktur erhalten
                        val rel = f.absolutePath.removePrefix(tbRoot.absolutePath)
                            .trimStart('/', '\\')
                        val dest = File(runFolder, rel)
                        dest.parentFile?.mkdirs()

                        val moved = if (f.renameTo(dest)) {
                            true
                        } else {
                            runCatching {
                                f.copyTo(dest, overwrite = true)
                                f.delete()
                            }.getOrDefault(false)
                        }

                        if (moved) orphanMoved++
                    }

                // Leerer Run-Ordner -> wegräumen
                if (runFolder.exists() && runFolder.walkTopDown().none { it.isFile }) {
                    runFolder.deleteRecursively()
                }
            }

            Result.success(
                workDataOf(
                    KEY_DB_DELETED to dbDeleted,
                    KEY_ORPHAN_MOVED to orphanMoved,
                    KEY_THUMB_DELETED to thumbDeleted,
                    KEY_MISSING_DB_ENTRIES to missingIds.size
                )
            )
        } catch (t: Throwable) {
            // IO/FS kann temporär fehlschlagen -> Retry ist sinnvoll.
            Result.retry()
        }
    }

    companion object {
        const val KEY_DB_DELETED = "dbDeleted"
        const val KEY_ORPHAN_MOVED = "orphanMoved"
        const val KEY_THUMB_DELETED = "thumbDeleted"
        const val KEY_MISSING_DB_ENTRIES = "missingDbEntries"
    }
}
