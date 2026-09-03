package de.tbhelfer.util

import android.content.Context
import android.os.Environment
import androidx.room.withTransaction
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.ui.kamera.FotoSpeicher
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Atomar" löschen (praktisch):
 * - Dateien werden zuerst in einen temporären Papierkorb verschoben (renameTo, sonst copy+delete).
 * - Danach wird die DB in einer Room-Transaction gelöscht.
 * - Falls die DB-Löschung fehlschlägt, werden die Dateien zurückverschoben (Rollback).
 *
 * Damit kann die UI ein Undo anbieten (Dateien zurück + DB-Einträge wieder einfügen).
 */
object AtomicDeleteHelper {

    data class Move(val from: File, val to: File)

    data class StagedDelete(
        val trashRunFolder: File?,
        val moved: List<Move>,
        val deletedFotos: List<FotoEntity>
    )

    data class Result(
        val requested: Int,
        val dbDeleted: Int,
        val filesStaged: Int,
        val skipped: Int,
        val missingFiles: Int
    )

    /**
     * Stage+Commit: Fotos (Original + Thumbnail) + DB, optional zusätzlich beliebige Dateien.
     *
     * @return Pair(Result, StagedDelete?) – staged == null bedeutet: komplett fehlgeschlagen.
     */
    suspend fun deleteAtomar(
        context: Context,
        fotoIds: List<Long>,
        btbFiles: List<File> = emptyList()
    ): Pair<Result, StagedDelete?> = withContext(Dispatchers.IO) {

        if (fotoIds.isEmpty() && btbFiles.isEmpty()) {
            return@withContext Result(0, 0, 0, 0, 0) to null
        }

        val fotos = if (fotoIds.isNotEmpty()) {
            ServiceLocator.fotoDao.fotosMitIds(fotoIds)
        } else emptyList()

        val tbRoot = tbRoot(context)
        val trashRun = tbRoot?.let {
            File(File(it, "_Trash"), System.currentTimeMillis().toString()).apply { mkdirs() }
        }

        val moves = mutableListOf<Move>()
        val fotosToDeleteFromDb = mutableListOf<FotoEntity>()
        var missingFiles = 0
        var skipped = 0

        fun stageMove(f: File, dest: File): Boolean {
            dest.parentFile?.mkdirs()
            val ok = f.renameTo(dest) || runCatching {
                f.copyTo(dest, overwrite = true)
                f.delete()
            }.getOrDefault(false)
            if (ok) moves += Move(f, dest)
            return ok
        }

        fun stageFileIfExists(file: File, relFromRoot: String? = null): Boolean {
            if (!file.exists()) return true
            if (trashRun == null) {
                // Kein Trash möglich -> direkt löschen (best effort, ohne Undo)
                return runCatching { file.delete() }.getOrDefault(false)
            }
            val rel = relFromRoot ?: file.name
            return stageMove(file, File(trashRun, rel))
        }

        fun relPath(root: File?, f: File, fallbackPrefix: String): String {
            if (root != null && f.absolutePath.startsWith(root.absolutePath)) {
                return f.absolutePath.removePrefix(root.absolutePath).trimStart('/', '\\')
            }
            return "$fallbackPrefix/${f.name}"
        }

        fun thumbRelFor(rel: String): String {
            val dot = rel.lastIndexOf('.')
            val base = if (dot > 0) rel.substring(0, dot) else rel
            val ext = if (dot > 0) rel.substring(dot) else ".jpg"
            return "${base}_thumb$ext"
        }

        // 1) Fotos: Original + Thumbnail stage
        fotos.forEach { e ->
            val original = File(e.dateipfad)
            val thumb = FotoSpeicher.thumbnailDateiFuer(original)
            val rel = relPath(tbRoot, original, fallbackPrefix = "fotos/${e.id}")

            if (!original.exists()) {
                // Datei fehlt bereits -> DB trotzdem löschen (bereinigt Inkonsistenzen)
                missingFiles++
                // Thumbnail, falls es noch existiert, ebenfalls stage/delete
                if (thumb.exists()) {
                    stageFileIfExists(thumb, thumbRelFor(rel))
                }
                fotosToDeleteFromDb += e
                return@forEach
            }

            val okOriginal = stageFileIfExists(original, rel)
            val okThumb = if (thumb.exists()) stageFileIfExists(thumb, thumbRelFor(rel)) else true

            if (okOriginal && okThumb) {
                fotosToDeleteFromDb += e
            } else {
                // nicht sauber stagebar -> nichts an DB ändern
                skipped++
            }
        }

        // 2) BTB/sonstige Files stage
        btbFiles.forEach { f ->
            if (!f.exists()) {
                missingFiles++
                return@forEach
            }
            val rel = relPath(tbRoot, f, fallbackPrefix = "btb")
            val ok = stageFileIfExists(f, rel)
            if (!ok) skipped++
        }

        // 3) Commit DB delete (Room transaction)
        var dbDeleted = 0
        val deletedFotos = fotosToDeleteFromDb.toList()
        if (deletedFotos.isNotEmpty()) {
            val ids = deletedFotos.map { it.id }
            try {
                ServiceLocator.datenbank.withTransaction {
                    dbDeleted = ServiceLocator.fotoDao.loeschenMitIds(ids)
                }
            } catch (_: Throwable) {
                rollbackMoves(moves)
                return@withContext Result(
                    requested = fotoIds.size + btbFiles.size,
                    dbDeleted = 0,
                    filesStaged = 0,
                    skipped = fotoIds.size + btbFiles.size,
                    missingFiles = missingFiles
                ) to null
            }
        }

        val res = Result(
            requested = fotoIds.size + btbFiles.size,
            dbDeleted = dbDeleted,
            filesStaged = moves.size,
            skipped = skipped,
            missingFiles = missingFiles
        )

        res to StagedDelete(
            trashRunFolder = trashRun,
            moved = moves.toList(),
            deletedFotos = deletedFotos
        )
    }

    /** Undo: Dateien zurück + DB-Einträge wieder einfügen (best effort). */
    @Suppress("UNUSED_PARAMETER")
    suspend fun undo(context: Context, staged: StagedDelete) = withContext(Dispatchers.IO) {
        rollbackMoves(staged.moved)

        if (staged.deletedFotos.isNotEmpty()) {
            runCatching {
                ServiceLocator.datenbank.withTransaction {
                    staged.deletedFotos.forEach { e ->
                        // Insert mit existierender ID (Room akzeptiert das bei autoGenerate)
                        ServiceLocator.fotoDao.einfuegen(e)
                    }
                }
            }
        }

        staged.trashRunFolder?.let { run ->
            runCatching {
                if (run.exists() && run.walkTopDown().none { it.isFile }) run.deleteRecursively()
            }
        }
    }

    /** Nach Ablauf des Undo-Fensters: endgültig löschen (Trash-Run). */
    suspend fun finalizeDelete(staged: StagedDelete) = withContext(Dispatchers.IO) {
        staged.trashRunFolder?.let { run ->
            runCatching { if (run.exists()) run.deleteRecursively() }
        }
    }

    private fun rollbackMoves(moves: List<Move>) {
        moves.asReversed().forEach { m ->
            if (!m.to.exists()) return@forEach
            m.from.parentFile?.mkdirs()
            m.to.renameTo(m.from) || runCatching {
                m.to.copyTo(m.from, overwrite = true)
                m.to.delete()
            }.getOrDefault(false)
        }
    }

    private fun tbRoot(context: Context): File? {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        val tb = File(base, "TB-Helfer")
        if (!tb.exists()) tb.mkdirs()
        return tb
    }
}
