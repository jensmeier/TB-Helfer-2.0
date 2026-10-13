package de.tbhelfer.ui.einstellungen.btbpdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.tbhelfer.einstellungen.BtbPdfEinstellungenSpeicher
import de.tbhelfer.ui.bautagebuch.BtbPdfHeaderRenderer
import de.tbhelfer.ui.einstellungen.nutzer.NutzerSpeicher
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Erzeugt eine echte A4-PDF-Vorschau fuer Kopf, Firmenlogo und Foto-Raster. */
object BtbPdfLayoutVorschau {
    suspend fun erstellen(context: Context): File {
        val cfg = BtbPdfEinstellungenSpeicher(context).einstellungen.first()
        val company = NutzerSpeicher.laden(context)
        val logo = company?.logoDateiname
            ?.let { File(NutzerSpeicher.ordner(context), it) }
            ?.takeIf { it.exists() && it.isFile }

        val pageW = 595
        val pageH = 842
        val marginL = 40f
        val marginR = 40f
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        val c = page.canvas

        val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val pH = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val pT = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.BLACK }
        val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.DKGRAY }
        val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.DKGRAY }
        val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(245, 245, 245) }

        c.drawText("BAUTAGEBUCH – PDF-VORSCHAU", marginL, 48f, pTitle)
        val metrics = BtbPdfHeaderRenderer.drawCompanyBlock(
            context = context,
            canvas = c,
            pageWidth = pageW,
            marginRight = marginR,
            marginTop = 44f,
            company = company,
            logoFile = logo,
            baseTextPaint = pT
        )

        var y = 82f
        c.drawText("Baustelle: Beispielbaustelle", marginL, y, pH); y += 18f
        BtbPdfHeaderRenderer.personalHeaderLines(context, "Beispiel TB").forEach {
            c.drawText(it, marginL, y, pT); y += 14f
        }
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        c.drawText("Datum: $date", marginL, y, pT); y += 14f
        c.drawText("Schicht: TAG", marginL, y, pT); y += 14f
        c.drawText("Zeit: 07:00 – 17:00", marginL, y, pT)
        y = maxOf(y + 28f, metrics.bottom + 22f, 190f)

        c.drawText("Foto-Raster / Anhang", marginL, y, pH)
        y += 14f
        c.drawText(rasterText(cfg.raster), marginL, y, pSmall)
        y += 14f

        val areaTop = y
        val areaBottom = pageH - 72f
        when (cfg.raster) {
            BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_2x6 -> drawAutoPreview(c, marginL, areaTop, pageW - marginR, areaBottom, pFill, pBorder, pSmall)
            BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_3x3 -> drawGrid(c, 3, 3, marginL, areaTop, pageW - marginR, areaBottom, pFill, pBorder, pSmall)
            BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_1x6 -> drawGrid(c, 1, 6, marginL, areaTop, pageW - marginR, areaBottom, pFill, pBorder, pSmall)
            BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_1x1 -> drawGrid(c, 1, 1, marginL, areaTop, pageW - marginR, areaBottom, pFill, pBorder, pSmall)
        }

        val footer = BtbPdfHeaderRenderer.creatorFooter(context, date)
        c.drawText(footer.take(78), marginL, (pageH - 22).toFloat(), pSmall)
        val right = "Seite 1"
        c.drawText(right, pageW - marginR - pSmall.measureText(right), (pageH - 22).toFloat(), pSmall)

        pdf.finishPage(page)
        val dir = File(context.cacheDir, "btb_pdf_preview").apply { mkdirs() }
        val file = File(dir, "BTB_PDF_Vorschau.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun rasterText(raster: BtbPdfEinstellungenSpeicher.FotoRaster): String = when (raster) {
        BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_2x6 -> "Automatisch: Querformat 2 nebeneinander / Hochformat bis 3 nebeneinander"
        BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_3x3 -> "3 × 3"
        BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_1x6 -> "1 × 6"
        BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_1x1 -> "1 × 1"
    }

    private fun drawGrid(
        c: android.graphics.Canvas,
        cols: Int,
        rows: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        fill: Paint,
        border: Paint,
        text: Paint
    ) {
        val gap = 8f
        val w = (right - left - gap * (cols - 1)) / cols
        val h = (bottom - top - gap * (rows - 1)) / rows
        var n = 1
        for (r in 0 until rows) for (col in 0 until cols) {
            val x = left + col * (w + gap)
            val y = top + r * (h + gap)
            drawCell(c, RectF(x, y, x + w, y + h), "Bild ${n++}", fill, border, text)
        }
    }

    private fun drawAutoPreview(
        c: android.graphics.Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        fill: Paint,
        border: Paint,
        text: Paint
    ) {
        val gap = 10f
        val totalH = bottom - top
        val rowH = (totalH - gap) / 2f
        val wideW = (right - left - gap) / 2f
        drawCell(c, RectF(left, top, left + wideW, top + rowH), "Bild 1 · Quer", fill, border, text)
        drawCell(c, RectF(left + wideW + gap, top, right, top + rowH), "Bild 2 · Quer", fill, border, text)
        val highW = (right - left - gap * 2f) / 3f
        for (i in 0..2) {
            val x = left + i * (highW + gap)
            drawCell(c, RectF(x, top + rowH + gap, x + highW, bottom), "Bild ${i + 3} · Hoch", fill, border, text)
        }
    }

    private fun drawCell(
        c: android.graphics.Canvas,
        rect: RectF,
        label: String,
        fill: Paint,
        border: Paint,
        text: Paint
    ) {
        c.drawRoundRect(rect, 6f, 6f, fill)
        c.drawRoundRect(rect, 6f, 6f, border)
        c.drawText(label, rect.left + 7f, rect.top + 14f, text)
        val img = RectF(rect.left + 7f, rect.top + 22f, rect.right - 7f, rect.bottom - 28f)
        if (img.height() > 20f) c.drawRect(img, border)
        c.drawText("KM · Zeit · Text · GPS", rect.left + 7f, rect.bottom - 10f, text)
    }
}
