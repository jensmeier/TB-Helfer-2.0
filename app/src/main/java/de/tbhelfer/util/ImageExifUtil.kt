package de.tbhelfer.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException

object ImageExifUtil {

    fun decodeSampledBitmapRespectingExif(
        path: String,
        reqW: Int,
        reqH: Int,
        preferredConfig: Bitmap.Config = Bitmap.Config.RGB_565
    ): Bitmap? {
        return runCatching {
            val o1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o1)
            if (o1.outWidth <= 0 || o1.outHeight <= 0) return null

            var sample = 1
            val halfH = o1.outHeight / 2
            val halfW = o1.outWidth / 2
            while ((halfH / sample) >= reqH && (halfW / sample) >= reqW) {
                sample *= 2
            }

            val o2 = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = preferredConfig
            }
            val bmp = BitmapFactory.decodeFile(path, o2) ?: return null
            applyExifOrientation(bmp, path)
        }.getOrNull()
    }

    fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    @Throws(IOException::class)
    fun writeFotoMetadata(
        file: File,
        lat: Double?,
        lon: Double?,
        metaTextBlock: String?,
        forceOrientationNormal: Boolean = false,
        windowsTitle: String? = null,
        windowsSubject: String? = null,
        windowsComment: String? = null,
        windowsKeywords: String? = null
    ) {
        val exif = ExifInterface(file)
        if (lat != null && lon != null && !(lat == 0.0 && lon == 0.0)) {
            exif.setLatLong(lat, lon)
        }
        val meta = metaTextBlock?.trim().orEmpty()
        val comment = windowsComment?.trim().orEmpty().ifBlank { meta }
        val title = windowsTitle?.trim().orEmpty()
        val subject = windowsSubject?.trim().orEmpty()
        val keywords = windowsKeywords?.trim().orEmpty()

        if (meta.isNotBlank()) {
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, meta)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, meta)
        }
        if (title.isNotBlank()) {
            runCatching { exif.setAttribute("XPTitle", title.take(240)) }
        }
        if (subject.isNotBlank()) {
            runCatching { exif.setAttribute("XPSubject", subject.take(240)) }
        }
        if (comment.isNotBlank()) {
            runCatching { exif.setAttribute("XPComment", comment.take(4000)) }
            val kw = keywords.ifBlank { comment.replace('\n', ';') }
            runCatching { exif.setAttribute("XPKeywords", kw.take(1000)) }
            if (meta.isBlank()) {
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, comment.take(4000))
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, comment.take(4000))
            }
        }
        if (forceOrientationNormal) {
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
        }
        exif.saveAttributes()
    }
}
