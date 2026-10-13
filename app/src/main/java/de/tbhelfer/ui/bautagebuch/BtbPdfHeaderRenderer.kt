package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import de.tbhelfer.einstellungen.BtbPdfLogoLayoutSpeicher
import de.tbhelfer.ui.einstellungen.nutzer.NutzerProfil
import de.tbhelfer.ui.einstellungen.nutzer.PersoenlichesNutzerProfilSpeicher
import java.io.File

/** Gemeinsames Firmenlogo-/Kontaktdaten-Layout fuer reales BTB-PDF und PDF-Vorschau. */
object BtbPdfHeaderRenderer {
    data class Metrics(
        val bottom: Float,
        val blockWidth: Float
    )

    fun personalHeaderLines(context: Context, fallbackName: String): List<String> {
        val p = PersoenlichesNutzerProfilSpeicher.laden(context)
        val name = p?.name?.trim().takeUnless { it.isNullOrBlank() }
            ?: fallbackName.trim().ifBlank { "-" }
        return buildList {
            add("Verantwortlicher TB: $name")
            p?.email?.trim()?.takeIf { it.isNotBlank() }?.let { add("E-Mail: $it") }
            p?.telefon?.trim()?.takeIf { it.isNotBlank() }?.let { add("Mobil: $it") }
        }
    }

    fun creatorFooter(context: Context, dateText: String, fallbackName: String? = null): String {
        val p = PersoenlichesNutzerProfilSpeicher.laden(context)
        val name = p?.name?.trim()?.takeIf { it.isNotBlank() }
            ?: fallbackName?.trim()?.takeIf { it.isNotBlank() }
            ?: "TB"
        return "Erstellt von: $name · $dateText"
    }

    fun drawCompanyBlock(
        context: Context,
        canvas: Canvas,
        pageWidth: Int,
        marginRight: Float,
        marginTop: Float,
        company: NutzerProfil?,
        logoFile: File?,
        baseTextPaint: Paint
    ): Metrics {
        val cfg = BtbPdfLogoLayoutSpeicher.laden(context)
        val textPaint = Paint(baseTextPaint).apply { textSize = 8.4f }
        val lines = buildList {
            if (cfg.showCompanyName) {
                company?.firmenname?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            }
            if (!cfg.contactsInImage) {
                if (cfg.showCompanyEmail) {
                    company?.email?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
                if (cfg.showCompanyPhone) {
                    company?.telefon?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }

        // Der rechte Kopfbereich ist eine feste Logo-Zone. Das Logo wird dort mittig und
        // proportional eingepasst. Transparente PNG/WebP-Logos bekommen im PDF ausdrücklich
        // einen weissen Untergrund, damit der Firmenkopf in jedem PDF-Viewer gleich aussieht.
        val boxRight = pageWidth - marginRight
        val boxLeft = maxOf(300f, pageWidth * 0.51f)
        val boxWidth = (boxRight - boxLeft).coerceAtLeast(80f)
        val logoAreaTop = (marginTop - 10f).coerceAtLeast(18f)
        val logoAreaBottom = 174f
        val logoAreaHeight = (logoAreaBottom - logoAreaTop).coerceAtLeast(70f)

        var logoLeft = boxLeft
        var logoTop = logoAreaTop
        var logoWidth = 0f
        var logoHeight = 0f
        var contentBottom = marginTop
        val bmp = logoFile?.takeIf { it.exists() && it.isFile }?.let {
            runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
        }
        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
            var drawW = cfg.logoWidthPt.toFloat().coerceAtMost(boxWidth)
            var drawH = drawW * bmp.height.toFloat() / bmp.width.toFloat()
            if (drawH > logoAreaHeight) {
                val factor = logoAreaHeight / drawH
                drawW *= factor
                drawH *= factor
            }

            val centeredLeft = boxLeft + (boxWidth - drawW) / 2f
            val centeredTop = logoAreaTop + (logoAreaHeight - drawH) / 2f
            val desiredLeft = centeredLeft + cfg.offsetXPt
            val desiredTop = centeredTop + cfg.offsetYPt
            logoLeft = desiredLeft.coerceIn(boxLeft, boxRight - drawW)
            logoTop = desiredTop.coerceIn(8f, logoAreaBottom - drawH)
            logoWidth = drawW
            logoHeight = drawH
            val logoRect = RectF(logoLeft, logoTop, logoLeft + drawW, logoTop + drawH)
            canvas.drawRect(logoRect, Paint().apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            })
            canvas.drawBitmap(
                bmp,
                null,
                logoRect,
                null
            )
            contentBottom = logoTop + drawH
            bmp.recycle()
        }

        val maxTextWidth = boxWidth.coerceAtMost(230f)
        var y = if (logoHeight > 0f) {
            maxOf(contentBottom + 9f, logoAreaBottom + 4f)
        } else {
            (marginTop + cfg.offsetYPt).coerceAtLeast(12f)
        }
        var widest = logoWidth
        lines.forEach { raw ->
            val line = ellipsize(raw, textPaint, maxTextWidth)
            val width = textPaint.measureText(line)
            widest = maxOf(widest, width)
            val centerX = boxLeft + boxWidth / 2f + cfg.offsetXPt
            val x = (centerX - width / 2f).coerceIn(8f, pageWidth - 8f - width)
            canvas.drawText(line, x, y, textPaint)
            y += textPaint.textSize * 1.24f
        }
        if (lines.isNotEmpty()) contentBottom = maxOf(contentBottom, y)
        val effectiveWidth = widest.coerceAtMost(boxWidth)
        return Metrics(
            bottom = contentBottom,
            blockWidth = if (effectiveWidth > 0f || lines.isNotEmpty()) effectiveWidth else 0f
        )
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end).trimEnd() + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }
}
