package de.tbhelfer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Erzeugt eine separate kleine JPEG-Datei fuer den mobilen Online-Sync.
 *
 * Das Aufnahmeoriginal wird weder verschoben noch ueberschrieben. Die Sync-Datei liegt nur im
 * App-Cache und kann jederzeit aus dem Original neu erzeugt werden.
 */
object MobileImageSync {
    const val MAX_DIM_PX = 1920
    const val JPEG_QUALITY = 82
    const val MAX_BYTES = 2_000_000L
    private val JPEG_QUALITIES = intArrayOf(JPEG_QUALITY, 75, 68, 60)
    private const val DECODE_MAX_DIM_PX = 2560
    private const val DIR_NAME = "mobile_sync_images"

    @Synchronized
    fun prepare(context: Context, source: File, cacheKey: String): File? = runCatching {
        require(source.isFile) { "Bilddatei existiert nicht." }

        val safeKey = cacheKey
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.', ' ')
            .take(80)
            .ifBlank { "image" }
        val dir = File(context.applicationContext.cacheDir, DIR_NAME).apply {
            if (!exists() && !mkdirs()) error("Sync-Bildordner konnte nicht erstellt werden.")
        }
        val target = File(dir, "$safeKey.jpg")
        if (
            target.isFile &&
            target.length() in 1L..MAX_BYTES &&
            target.lastModified() >= source.lastModified()
        ) {
            return@runCatching target
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Bildabmessungen konnten nicht gelesen werden."
        }

        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > DECODE_MAX_DIM_PX) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        ) ?: error("Bild konnte nicht dekodiert werden.")

        var oriented = decoded
        var scaled: Bitmap? = null
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            oriented = ImageExifUtil.applyExifOrientation(decoded, source.absolutePath)
            val maxDim = max(oriented.width, oriented.height)
            var output = if (maxDim > MAX_DIM_PX) {
                val factor = MAX_DIM_PX.toDouble() / maxDim.toDouble()
                Bitmap.createScaledBitmap(
                    oriented,
                    (oriented.width * factor).roundToInt().coerceAtLeast(1),
                    (oriented.height * factor).roundToInt().coerceAtLeast(1),
                    true
                ).also { scaled = it }
            } else {
                oriented
            }

            // Die Pixelkante allein garantiert keine Dateigroesse. Erst nach geschlossenem
            // Stream pruefen, dann ggf. Qualitaet und anschliessend Abmessungen reduzieren.
            var fits = false
            while (!fits) {
                for (quality in JPEG_QUALITIES) {
                    FileOutputStream(temp).use { out ->
                        check(output.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                            "JPEG-Komprimierung fehlgeschlagen."
                        }
                    }
                    check(temp.length() > 0L) { "Sync-Bild ist leer." }
                    if (temp.length() <= MAX_BYTES) {
                        fits = true
                        break
                    }
                }
                if (!fits) {
                    check(output.width > 1 || output.height > 1) {
                        "Sync-Bild konnte nicht auf 2 MB verkleinert werden."
                    }
                    val previous = output
                    output = Bitmap.createScaledBitmap(
                        previous,
                        (previous.width * 0.8).toInt().coerceAtLeast(1),
                        (previous.height * 0.8).toInt().coerceAtLeast(1),
                        true
                    )
                    scaled = output
                    if (previous !== oriented && previous !== output && !previous.isRecycled) {
                        previous.recycle()
                    }
                }
            }

            check(temp.length() in 1L..MAX_BYTES) { "Ungueltige Sync-Bildgroesse." }
            try {
                if (target.exists()) check(target.delete()) { "Alter Sync-Cache nicht loeschbar." }
                if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
                check(target.length() in 1L..MAX_BYTES) { "Ungueltiger Sync-Cache." }
            } catch (error: Exception) {
                // Ein fehlgeschlagener Copy darf beim naechsten Lauf kein Cachetreffer werden.
                target.delete()
                throw error
            }
            target
        } finally {
            temp.delete()
            if (scaled != null && scaled !== oriented && scaled?.isRecycled == false) {
                scaled?.recycle()
            }
            if (!oriented.isRecycled) oriented.recycle()
            if (decoded !== oriented && !decoded.isRecycled) decoded.recycle()
        }
    }.getOrNull()

    fun syncFilename(originalName: String): String {
        val base = originalName.substringBeforeLast('.', originalName)
            .replace(Regex("[^A-Za-z0-9._()-]+"), "_")
            .trim('_', '.', ' ')
            .ifBlank { "foto" }
            .take(80)
        return "${base}_sync.jpg"
    }
}
