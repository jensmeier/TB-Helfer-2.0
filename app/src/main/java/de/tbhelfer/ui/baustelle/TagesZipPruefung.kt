package de.tbhelfer.ui.baustelle

import java.io.File
import java.util.zip.ZipInputStream

/** Check every ZIP entry including its CRC before showing a successful export. */
internal object TagesZipPruefung {
    fun pruefen(file: File) {
        require(file.isFile && file.length() > 0L) { "ZIP fehlt oder ist leer" }
        ZipInputStream(file.inputStream().buffered()).use { archive ->
            var count = 0
            val seen = hashSetOf<String>()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = archive.nextEntry ?: break
                count++
                check(seen.add(entry.name)) { "ZIP enthält doppelte Einträge" }
                // Exhausting each entry also verifies CRC even for huge JPEGs.
                while (archive.read(buffer) != -1) { }
                archive.closeEntry()
            }
            check(count > 0) { "ZIP enthält keine Dateien" }
        }
    }
}
