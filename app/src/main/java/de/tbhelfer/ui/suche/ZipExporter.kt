package de.tbhelfer.ui.suche

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Helper für ZIP-Exports (Fotos/BTB). */
object ZipExporter {

    data class ZipInput(
        val file: File,
        /** Zip-Pfad (z.B. "Fotos/IMG_123.jpg" oder "BTB/Plan.pdf"). */
        val entryPath: String
    )

    data class Result(
        val zip: File?,
        val exported: Int,
        val missing: Int
    )

    /**
     * Erstellt eine ZIP im Cache-Verzeichnis.
     * - Skipt fehlende Dateien (counted as missing)
     * - Vermeidet doppelte Entry-Namen durch Anhängen von (2), (3), ...
     */
    fun createZip(
        context: Context,
        inputs: List<ZipInput>,
        namePrefix: String = "tbhelfer_export"
    ): Result {
        val existing = inputs.filter { it.file.exists() && it.file.isFile }
        val missing = inputs.size - existing.size
        if (existing.isEmpty()) return Result(null, 0, missing)

        val zipFile = File(context.cacheDir, "${namePrefix}_${System.currentTimeMillis()}.zip")

        // Größerer Buffer = deutlich schneller bei vielen/größeren Dateien.
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile), 64 * 1024)).use { zos ->
            val usedNames = HashSet<String>()

            fun uniqueName(desired: String): String {
                // Normalisieren, keine absoluten Pfade
                val clean = desired.replace("\\\\", "/").trimStart('/')
                if (usedNames.add(clean)) return clean

                val dir = clean.substringBeforeLast('/', "")
                val name = clean.substringAfterLast('/')
                val base = name.substringBeforeLast('.', name)
                val ext = name.substringAfterLast('.', "")

                var i = 2
                while (true) {
                    val candidateName = if (ext.isBlank()) "$base($i)" else "$base($i).$ext"
                    val candidate = if (dir.isBlank()) candidateName else "$dir/$candidateName"
                    if (usedNames.add(candidate)) return candidate
                    i++
                }
            }

            existing.forEach { input ->
                val entryName = uniqueName(input.entryPath)
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(input.file).buffered(64 * 1024).use { it.copyTo(zos, 64 * 1024) }
                zos.closeEntry()
            }
        }

        return Result(zipFile, existing.size, missing)
    }
}
