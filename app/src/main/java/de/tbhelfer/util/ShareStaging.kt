package de.tbhelfer.util

import java.io.File
import java.io.IOException
import java.util.UUID

/** A unique directory prevents same-name photos overwriting one another. Call on IO. */
internal object ShareStaging {
    fun copy(source: File, root: File): File {
        if (!source.isFile || !source.canRead() || source.length() == 0L) {
            throw IOException("Datei fehlt oder ist nicht lesbar.")
        }
        val directory = File(root, UUID.randomUUID().toString())
        if (!directory.mkdirs()) throw IOException("Übergabeordner konnte nicht angelegt werden.")
        val target = File(directory, source.name)
        try {
            source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            return target
        } catch (error: Exception) {
            target.delete()
            directory.delete()
            throw error
        }
    }
}
