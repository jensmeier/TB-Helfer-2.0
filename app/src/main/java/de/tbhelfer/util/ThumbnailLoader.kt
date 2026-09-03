package de.tbhelfer.util

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Sehr einfacher Thumbnail-Loader ohne externe Libs.
 *
 * Ziele:
 * - Kein Thread-pro-Bind (Executor)
 * - Keine falschen Bilder durch Recycling (Tag-Check)
 * - Weniger Decodes durch LRU-Cache
 */
object ThumbnailLoader {

    // Default für Listen/Details: ausreichend scharf, aber noch sehr schnell.
    // (Große Detailansichten übergeben i.d.R. eigene Werte.)
    private const val DEFAULT_REQ_W = 320
    private const val DEFAULT_REQ_H = 320

    /**
     * Dynamischer Cache (ca. 1/16 vom Heap), aber mit sinnvollen Grenzen.
     * Ziel: auf Low-End Geräten nicht zu groß, auf modernen Geräten nicht zu klein.
     */
    private val cacheSizeBytes: Int by lazy {
        val max = Runtime.getRuntime().maxMemory().coerceAtLeast(16L * 1024L * 1024L)
        val suggested = (max / 16L).toInt()
        suggested.coerceIn(4 * 1024 * 1024, 32 * 1024 * 1024)
    }

    private val cache = object : LruCache<String, Bitmap>(cacheSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val executor: ExecutorService by lazy {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        // Sweet spot: 2..4 Threads (mehr bringt bei Bitmap-Decodes oft keinen Gewinn)
        val threads = (cores / 2).coerceIn(2, 4)
        Executors.newFixedThreadPool(threads)
    }

    fun loadInto(
        imageView: ImageView,
        filePath: String,
        placeholderResId: Int,
        reqW: Int = DEFAULT_REQ_W,
        reqH: Int = DEFAULT_REQ_H,
    ) {
        // Bevorzugt vorhandenes Thumbnail neben dem Original (...._thumb.jpg)
        val original = File(filePath)
        val file = pickBestFile(original)

        if (!file.exists()) {
            imageView.tag = null
            imageView.setImageResource(placeholderResId)
            return
        }

        // Key ändert sich wenn Datei überschrieben wurde.
        val key = file.absolutePath + "@" + file.lastModified()
        imageView.tag = key

        cache.get(key)?.let {
            imageView.setImageBitmap(it)
            return
        }

        // Placeholder sofort, damit Recycler schnell bleibt.
        imageView.setImageResource(placeholderResId)

        executor.execute {
            val bm = decodeSampledBitmap(file.absolutePath, reqW, reqH) ?: return@execute
            cache.put(key, bm)

            // Nur setzen, wenn View noch zum gleichen Item gehört.
            imageView.post {
                if (imageView.tag == key) {
                    imageView.setImageBitmap(bm)
                }
            }
        }
    }

    private fun pickBestFile(original: File): File {
        if (!original.exists()) return original
        val name = original.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ".jpg"
        val thumb = File(original.parentFile, "${base}_thumb$ext")
        return if (thumb.exists() && thumb.length() > 0) thumb else original
    }

    private fun decodeSampledBitmap(path: String, reqW: Int, reqH: Int): Bitmap? =
        ImageExifUtil.decodeSampledBitmapRespectingExif(
            path = path,
            reqW = reqW,
            reqH = reqH,
            preferredConfig = Bitmap.Config.RGB_565
        )
}
