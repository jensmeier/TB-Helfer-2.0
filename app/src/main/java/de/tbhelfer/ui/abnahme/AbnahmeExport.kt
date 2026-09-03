package de.tbhelfer.ui.abnahme

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.util.ImageExifUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AbnahmeExport {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36f
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    suspend fun erzeugeEinzelPdf(context: Context, abnahme: AbnahmeSpeicher.Abnahme): File = withContext(Dispatchers.IO) {
        val target = AbnahmeSpeicher.pdfDatei(context, abnahme)
        target.parentFile?.mkdirs()
        val fotos = ServiceLocator.fotoRepository.fotosMitIds(abnahme.fotos.map { it.fotoId })
            .associateBy { it.id }
        val doc = PdfDocument()
        val pages = PageSequence(doc)
        try {
            renderSummary(pages, abnahme)
            abnahme.anlagen.sortedBy { it.erstelltAmEpochMs }.forEach { anlage ->
                when (anlage.typ) {
                    AbnahmeSpeicher.AnlageTyp.BILD -> {
                        val path = anlage.ausschnittPfad?.takeIf { File(it).exists() } ?: anlage.originalPfad
                        renderImagePage(
                            pages,
                            File(path),
                            "Plan-/Objektgrundlage",
                            anlage.bezeichnung.ifBlank { "Planausschnitt / Querprofil" },
                            anlage.bemerkung
                        )
                    }
                    AbnahmeSpeicher.AnlageTyp.PDF -> renderPdfAttachment(pages, File(anlage.originalPfad), anlage)
                }
            }
            abnahme.fotos
                .mapNotNull { ref -> fotos[ref.fotoId]?.let { ref to it } }
                .sortedBy { (_, f) -> f.erstelltAmEpochMs }
                .forEach { (ref, foto) -> renderFotoPage(pages, ref, foto) }

            if (abnahme.restpunkte.isNotBlank() || abnahme.kommentar.isNotBlank()) {
                renderClosingPage(pages, abnahme)
            }
            FileOutputStream(target).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        target
    }

    fun csvText(abnahmen: List<AbnahmeSpeicher.Abnahme>): String = buildString {
        appendLine("Nummer;Baustelle;Unterbaustelle;Bezeichnung;Objekt;Firma;Weitere Firmen;Abnehmender;Anwesende;KM von;KM bis;Ergebnis;Beginn;Abschluss;Fotos;Planunterlagen;Kommentar;Restpunkte")
        abnahmen.sortedBy { it.nummer }.forEach { a ->
            val row = listOf(
                a.nummer.toString(), a.baustelleName, a.unterKnotenName, a.bezeichnung, a.objekt,
                a.firma, a.weitereFirmen, a.abnehmender, a.anwesende,
                a.kmVon?.let { de.tbhelfer.util.KmAnzeige.wert(it) }.orEmpty(),
                a.kmBis?.let { de.tbhelfer.util.KmAnzeige.wert(it) }.orEmpty(),
                AbnahmeSpeicher.statusLabel(a), formatEpoch(a.startEpochMs),
                a.abgeschlossenAmEpochMs?.let(::formatEpoch).orEmpty(),
                a.fotos.size.toString(), a.anlagen.size.toString(), a.kommentar, a.restpunkte
            )
            appendLine(row.joinToString(";") { csv(it) })
        }
    }

    suspend fun listenPdfBytes(
        context: Context,
        baustelleName: String,
        abnahmen: List<AbnahmeSpeicher.Abnahme>,
        titelZusatz: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
        val bold = Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page!!.canvas
            canvas!!.drawText("Abnahmeliste $titelZusatz – $baustelleName", MARGIN, 45f, Paint(bold).apply { textSize = 16f })
            y = 70f
        }
        fun ensure(h: Float) { if (page == null || y + h > PAGE_H - MARGIN) newPage() }
        newPage()
        abnahmen.sortedByDescending { it.geaendertAmEpochMs }.forEach { a ->
            ensure(82f)
            canvas!!.drawText("Abnahme ${a.nummer.toString().padStart(3, '0')} · ${AbnahmeSpeicher.statusLabel(a)}", MARGIN, y, bold)
            y += 15f
            val lines = listOfNotNull(
                a.bezeichnung.ifBlank { null },
                a.objekt.takeIf { it.isNotBlank() }?.let { "Objekt: $it" },
                a.unterKnotenName.takeIf { it.isNotBlank() }?.let { "Unterbaustelle: $it" },
                a.firma.takeIf { it.isNotBlank() }?.let { "Firma: $it" },
                "Beginn: ${formatEpoch(a.startEpochMs)} · Fotos: ${a.fotos.size} · Pläne: ${a.anlagen.size}"
            )
            lines.forEach { line ->
                wrap(line, paint, PAGE_W - 2 * MARGIN).forEach { l ->
                    ensure(13f); canvas!!.drawText(l, MARGIN + 8f, y, paint); y += 13f
                }
            }
            y += 8f
            canvas!!.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply { color = 0xFFBBBBBB.toInt(); strokeWidth = 1f })
            y += 12f
        }
        page?.let { doc.finishPage(it) }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        out.toByteArray()
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".dateianbieter", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Abnahme-PDF teilen"))
    }

    private fun renderSummary(pages: PageSequence, a: AbnahmeSpeicher.Abnahme) {
        val page = pages.startPage()
        val c = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 19f; typeface = Typeface.DEFAULT_BOLD }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10.5f }
        c.drawText("Abnahme ${a.nummer.toString().padStart(3, '0')}", MARGIN, 48f, title)
        c.drawText(AbnahmeSpeicher.statusLabel(a), MARGIN, 70f, Paint(bold).apply { textSize = 13f })
        var y = 98f
        val rows = listOf(
            "Hauptbaustelle" to a.baustelleName,
            "Unterbaustelle" to a.unterKnotenName,
            "Bezeichnung" to a.bezeichnung,
            "Objekt" to a.objekt,
            "Firma" to a.firma,
            "Weitere Firmen" to a.weitereFirmen,
            "Abnehmender" to a.abnehmender,
            "Anwesende" to a.anwesende,
            "KM" to kmText(a),
            "Beginn" to formatEpoch(a.startEpochMs),
            "Abschluss" to (a.abgeschlossenAmEpochMs?.let(::formatEpoch) ?: "offen / in Prüfung"),
            "Fotos / Unterlagen" to "${a.fotos.size} / ${a.anlagen.size}"
        )
        rows.forEach { (label, value) ->
            if (value.isBlank()) return@forEach
            c.drawText(label, MARGIN, y, bold)
            val lines = wrap(value, normal, PAGE_W - MARGIN - 155f)
            lines.forEachIndexed { idx, line -> c.drawText(line, 155f, y + idx * 14f, normal) }
            y += maxOf(19f, lines.size * 14f + 5f)
        }
        if (a.kommentar.isNotBlank()) {
            y += 8f; c.drawText("Kommentar", MARGIN, y, bold); y += 17f
            wrap(a.kommentar, normal, PAGE_W - 2 * MARGIN).forEach { line -> c.drawText(line, MARGIN, y, normal); y += 14f }
        }
        if (a.restpunkte.isNotBlank()) {
            y += 10f; c.drawText("Restpunkte", MARGIN, y, bold); y += 17f
            wrap(a.restpunkte, normal, PAGE_W - 2 * MARGIN).forEach { line -> c.drawText(line, MARGIN, y, normal); y += 14f }
        }
        pages.finishPage(page)
    }

    private fun renderImagePage(pages: PageSequence, file: File, heading: String, label: String, note: String) {
        if (!file.exists()) return
        val bmp = ImageExifUtil.decodeSampledBitmapRespectingExif(file.absolutePath, 2200, 3000, Bitmap.Config.RGB_565) ?: return
        val page = pages.startPage()
        val c = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 15f; typeface = Typeface.DEFAULT_BOLD }
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
        c.drawText(heading, MARGIN, 42f, title)
        c.drawText(label, MARGIN, 61f, normal)
        val top = 76f
        val bottomReserve = if (note.isBlank()) 30f else 82f
        drawBitmapFit(c, bmp, RectF(MARGIN, top, PAGE_W - MARGIN, PAGE_H - bottomReserve))
        if (note.isNotBlank()) {
            var y = PAGE_H - 62f
            wrap("Bemerkung: $note", normal, PAGE_W - 2 * MARGIN).take(4).forEach { line -> c.drawText(line, MARGIN, y, normal); y += 13f }
        }
        pages.finishPage(page)
        bmp.recycle()
    }

    private fun renderPdfAttachment(pages: PageSequence, file: File, anlage: AbnahmeSpeicher.Anlage) {
        if (!file.exists()) return
        val pfd = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull() ?: return
        try {
            runCatching {
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { srcPage ->
                            val scale = minOf((PAGE_W - 2 * MARGIN) / srcPage.width.toFloat(), (PAGE_H - 100f) / srcPage.height.toFloat())
                            val bmp = Bitmap.createBitmap(
                                maxOf(1, (srcPage.width * scale).toInt()),
                                maxOf(1, (srcPage.height * scale).toInt()),
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val page = pages.startPage()
                            val c = page.canvas
                            c.drawText(
                                anlage.bezeichnung.ifBlank { "Planunterlage PDF" } + " · Seite ${i + 1}",
                                MARGIN,
                                40f,
                                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
                            )
                            drawBitmapFit(c, bmp, RectF(MARGIN, 55f, PAGE_W - MARGIN, PAGE_H - MARGIN))
                            pages.finishPage(page)
                            bmp.recycle()
                        }
                    }
                }
            }
        } finally {
            pfd.close()
        }
    }

    private fun renderFotoPage(pages: PageSequence, ref: AbnahmeSpeicher.FotoRef, foto: FotoEntity) {
        val file = File(foto.dateipfad)
        if (!file.exists()) return
        val bmp = ImageExifUtil.decodeSampledBitmapRespectingExif(file.absolutePath, 2200, 2600, Bitmap.Config.RGB_565) ?: return
        val page = pages.startPage()
        val c = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 15f; typeface = Typeface.DEFAULT_BOLD }
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
        val dt = formatEpoch(foto.erstelltAmEpochMs)
        c.drawText("${AbnahmeSpeicher.bildArtLabel(ref.bildArt)} · $dt", MARGIN, 42f, title)
        val subtitle = listOfNotNull(
            foto.firma.takeIf { it.isNotBlank() },
            foto.km?.let { "KM ${de.tbhelfer.util.KmAnzeige.wert(it)}" },
            foto.kurztext.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) c.drawText(subtitle, MARGIN, 61f, normal)
        val note = ref.bemerkung.ifBlank { foto.notiz.orEmpty() }
        val bottomReserve = if (note.isBlank()) 35f else 95f
        drawBitmapFit(c, bmp, RectF(MARGIN, 75f, PAGE_W - MARGIN, PAGE_H - bottomReserve))
        if (note.isNotBlank()) {
            var y = PAGE_H - 72f
            wrap("Bemerkung: $note", normal, PAGE_W - 2 * MARGIN).take(5).forEach { line -> c.drawText(line, MARGIN, y, normal); y += 13f }
        }
        pages.finishPage(page)
        bmp.recycle()
    }

    private fun renderClosingPage(pages: PageSequence, a: AbnahmeSpeicher.Abnahme) {
        val page = pages.startPage()
        val c = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10.5f }
        c.drawText("Ergebnis und Abschluss", MARGIN, 48f, title)
        c.drawText("Ergebnis", MARGIN, 78f, bold)
        c.drawText(AbnahmeSpeicher.statusLabel(a), 150f, 78f, normal)
        var y = 108f
        if (a.restpunkte.isNotBlank()) {
            c.drawText("Restpunkte", MARGIN, y, bold); y += 18f
            wrap(a.restpunkte, normal, PAGE_W - 2 * MARGIN).forEach { line -> c.drawText(line, MARGIN, y, normal); y += 14f }
            y += 12f
        }
        if (a.kommentar.isNotBlank()) {
            c.drawText("Kommentar", MARGIN, y, bold); y += 18f
            wrap(a.kommentar, normal, PAGE_W - 2 * MARGIN).forEach { line -> c.drawText(line, MARGIN, y, normal); y += 14f }
        }
        y = maxOf(y + 28f, 630f)
        c.drawText("Abnehmender: ${a.abnehmender.ifBlank { "-" }}", MARGIN, y, normal)
        c.drawText("Abschluss: ${a.abgeschlossenAmEpochMs?.let(::formatEpoch) ?: "noch offen"}", MARGIN, y + 22f, normal)
        if (a.wiederGeoeffnetAmEpochMs != null) {
            c.drawText("Hinweis: Die Abnahme wurde nachträglich zur Korrektur wieder geöffnet.", MARGIN, y + 48f, normal)
        }
        pages.finishPage(page)
    }

    private class PageSequence(private val document: PdfDocument) {
        private var number: Int = 0

        fun startPage(): PdfDocument.Page {
            number += 1
            return document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
        }

        fun finishPage(page: PdfDocument.Page) {
            document.finishPage(page)
        }
    }

    private fun drawBitmapFit(canvas: Canvas, bmp: Bitmap, target: RectF) {
        val scale = minOf(target.width() / bmp.width.toFloat(), target.height() / bmp.height.toFloat())
        val w = bmp.width * scale
        val h = bmp.height * scale
        val dst = RectF(target.centerX() - w / 2f, target.centerY() - h / 2f, target.centerX() + w / 2f, target.centerY() + h / 2f)
        canvas.drawBitmap(bmp, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val result = mutableListOf<String>()
        text.replace("\r", "").split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) { result += ""; return@forEach }
            var line = ""
            paragraph.trim().split(Regex("\\s+")).forEach { word ->
                val candidate = if (line.isBlank()) word else "$line $word"
                if (paint.measureText(candidate) <= width) line = candidate
                else {
                    if (line.isNotBlank()) result += line
                    line = word
                }
            }
            if (line.isNotBlank()) result += line
        }
        return result.ifEmpty { listOf("") }
    }

    private fun kmText(a: AbnahmeSpeicher.Abnahme): String =
        de.tbhelfer.util.KmAnzeige.bereich(a.kmVon, a.kmBis, inKlammern = false)
            ?.removePrefix("KM ").orEmpty()

    private fun formatEpoch(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(dateFmt)
    private fun csv(raw: String): String = "\"${raw.replace("\"", "\"\"").replace("\n", " ")}\""
}
