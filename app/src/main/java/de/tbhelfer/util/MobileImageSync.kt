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
    private const val DECODE_MAX_DIM_PX = 2560
    private const val DIR_NAME = "mobile_sync_images"

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
            target.length() > 0L &&
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

        val oriented = ImageExifUtil.applyExifOrientation(decoded, source.absolutePath)
        var scaled: Bitmap? = null
        try {
            val maxDim = max(oriented.width, oriented.height)
            val output = if (maxDim > MAX_DIM_PX) {
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

            val temp = File(target.parentFile, target.name + ".tmp")
            FileOutputStream(temp).use { out ->
                check(output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    "JPEG-Komprimierung fehlgeschlagen."
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target
        } finally {
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
