package de.tbhelfer.ui.baustelle

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class TagesZipPruefungTest {
    @Test fun acceptsCompleteZipWithTextOnly() {
        val file = File.createTempFile("tbh-zip", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use {
                it.putNextEntry(ZipEntry("BTB.txt"))
                it.write("Ohne Fotos".toByteArray())
                it.closeEntry()
            }
            TagesZipPruefung.pruefen(file)
        } finally { file.delete() }
    }

    @Test fun refusesInvalidArchiveInsteadOfSharing() {
        val file = File.createTempFile("tbh-invalid", ".zip")
        try {
            file.writeText("nicht-zip")
            assertThrows(IllegalStateException::class.java) { TagesZipPruefung.pruefen(file) }
        } finally { file.delete() }
    }
}
