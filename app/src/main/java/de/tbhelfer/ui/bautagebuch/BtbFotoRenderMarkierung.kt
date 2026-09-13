package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.util.ImageExifUtil
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * PDF-only Markierung für Fotos, die nach dem unveränderlichen ersten BTB-Abschluss hinzugekommen
 * sind. Original-JPGs werden niemals verändert: für den PDF-Lauf entstehen temporäre Kopien mit
 * rotem Rahmen und rotem Änderungsbanner.
 */
object BtbFotoRenderMarkierung {

    private val overridePaths = ConcurrentHashMap<Long, String>()
    private val tempFiles = Collections.newSetFromMap(ConcurrentHashMap<File, Boolean>())

    fun overridePath(fotoId: Long): String? = overridePaths[fotoId]

    fun vorbereiten(context: Context, fotos: List<FotoEntity>, roteFotoIds: Set<Long>) {
        clear()
        if (roteFotoIds.isEmpty()) return
        val dir = File(context.cacheDir, "btb_pdf_diff_fotos").apply { mkdirs() }

        fotos.asSequence()
            .filter { it.id in roteFotoIds }
            .forEach { foto ->
                runCatching {
                    val source = File(foto.dateipfad)
                    if (!source.exists() || !source.isFile) return@runCatching
                    val bmp = ImageExifUtil.decodeSampledBitmapRespectingExif(
                        source.absolutePath,
                        reqW = 1800,
                        reqH = 1800,
                        preferredConfig = Bitmap.Config.ARGB_8888
                    ) ?: return@runCatching

                    val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(out)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(bmp, 0f, 0f, null)

                    val minSide = minOf(out.width, out.height).coerceAtLeast(1)
                    val stroke = (minSide * 0.018f).coerceIn(8f, 28f)
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.RED
                        style = Paint.Style.STROKE
                        strokeWidth = stroke
                    }
                    val half = stroke / 2f
                    canvas.drawRect(
                        RectF(half, half, out.width - half, out.height - half),
                        borderPaint
                    )

                    val bannerHeight = (minSide * 0.11f).coerceIn(54f, 150f)
                    val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.RED
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(0f, 0f, out.width.toFloat(), bannerHeight, bannerPaint)
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = bannerHeight * 0.45f
                        isFakeBoldText = true
                    }
                    canvas.drawText("ÄNDERUNG ZUM ORIGINAL", stroke, bannerHeight * 0.68f, textPaint)

                    val target = File(dir, "btb_diff_${foto.id}_${System.nanoTime()}.jpg")
                    FileOutputStream(target).use { stream ->
                        out.compress(Bitmap.CompressFormat.JPEG, 94, stream)
                    }
                    if (bmp != out && !bmp.isRecycled) bmp.recycle()
                    if (!out.isRecycled) out.recycle()

                    overridePaths[foto.id] = target.absolutePath
                    tempFiles.add(target)
                }
            }
    }

    /** Nur für PDF-Aufbereitung; Datenbank und Originalbilder bleiben unverändert. */
    fun anwenden(fotos: List<FotoEntity>): List<FotoEntity> = fotos.map { foto ->
        overridePath(foto.id)?.let { foto.copy(dateipfad = it) } ?: foto
    }

    fun clear() {
        overridePaths.clear()
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
    }
}
