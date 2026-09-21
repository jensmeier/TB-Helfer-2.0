package de.tbhelfer.ui.baustelle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

object MaengellisteExport {

    data class MangelFoto(
        val foto: FotoEntity,
        val art: MangelStatusSpeicher.FotoArt
    )

    data class MangelItem(
        val key: String,
        val datumIso: String,
        val zeit: String,
        val baustelle: String,
        val bereich: String,
        val km: String,
        val firma: String,
        val beschreibung: String,
        val gps: String,
        val fotos: List<MangelFoto> = emptyList(),
        val btbBildNr: String = ""
    ) {
        val hauptFoto: FotoEntity? get() = fotos.sortedWith(compareBy<MangelFoto> { MaengellisteExport.artSort(it.art) }.thenBy { it.foto.erstelltAmEpochMs }).firstOrNull()?.foto
    }

    private data class Meta(
        var datumIso: String? = null,
        var zeit: String? = null,
        var bereich: String? = null,
        var km: String? = null,
        var firma: String? = null,
        var beschreibung: String? = null,
        var gps: String? = null,
        val fotos: MutableList<MangelFoto> = mutableListOf(),
        var btbBildNr: String = ""
    )

    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val deFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    suspend fun ladeItems(context: Context, baustelleId: Long, baustelleName: String): List<MangelItem> {
        val alleFotos = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(baustelleId).sortedBy { it.erstelltAmEpochMs }
        val fotoById = alleFotos.associateBy { it.id }
        val zuordnungen = MangelStatusSpeicher.alleFotoZuordnungen(context).associateBy { it.fotoId }
        val gruppen = linkedMapOf<String, Meta>()

        val knotenLabels = HashMap<Long, String>()
        alleFotos.mapNotNull { it.baustellenKnotenId }.distinct().forEach { id ->
            knotenLabels[id] = runCatching { knotenAnzeige(ServiceLocator.baustellenBaumRepository.knotenMitId(id)) }
                .getOrDefault("Hauptbaustelle")
        }
        fun bereich(foto: FotoEntity?): String {
            val id = foto?.baustellenKnotenId ?: return "Hauptbaustelle"
            return knotenLabels[id].orEmpty().ifBlank { "Hauptbaustelle" }
        }

        fun ensure(key: String): Meta = gruppen.getOrPut(key) { Meta() }
        fun metaAusFoto(meta: Meta, foto: FotoEntity) {
            if (meta.datumIso == null || foto.erstelltAmEpochMs < epoch(meta.datumIso, meta.zeit)) {
                val z = Instant.ofEpochMilli(foto.erstelltAmEpochMs).atZone(ZoneId.systemDefault())
                meta.datumIso = z.toLocalDate().toString()
                meta.zeit = "%02d:%02d".format(z.hour, z.minute)
            }
            if (meta.bereich.isNullOrBlank()) meta.bereich = bereich(foto)
            if (meta.km.isNullOrBlank()) meta.km = foto.km?.let { "KM ${de.tbhelfer.util.KmAnzeige.wert(it)}" } ?: "-"
            if (meta.firma.isNullOrBlank() || meta.firma == "-") meta.firma = foto.firma.ifBlank { "-" }
            if (meta.beschreibung.isNullOrBlank()) meta.beschreibung = foto.notiz?.takeIf { it.isNotBlank() } ?: foto.kurztext
            if (meta.gps.isNullOrBlank()) meta.gps = fotoGpsText(foto).ifBlank { "-" }
        }

        alleFotos
            .filter { it.bildtyp == Bildtyp.MAENGEL_SCHAEDEN || zuordnungen.containsKey(it.id) }
            .forEach { foto ->
                val z = zuordnungen[foto.id]
                val key = z?.mangelKey ?: "foto:${foto.id}"
                val art = z?.fotoArt ?: MangelStatusSpeicher.FotoArt.MANGEL
                val meta = ensure(key)
                meta.fotos.add(MangelFoto(foto, art))
                metaAusFoto(meta, foto)
            }

        // Alte Ein-Foto-Nachweise aus patch33/v12: nachweisFotoId als erledigtes Foto in den Block ziehen.
        gruppen.toMap().forEach { (key, meta) ->
            val nachweisId = MangelStatusSpeicher.laden(context, key).nachweisFotoId
            val foto = nachweisId?.let { fotoById[it] }
            if (foto != null && meta.fotos.none { it.foto.id == foto.id }) {
                meta.fotos.add(MangelFoto(foto, MangelStatusSpeicher.FotoArt.ERLEDIGT))
                metaAusFoto(meta, foto)
            }
        }

        // Manuelle Mängel aus Abschnitt 8. Wenn zu diesem manuellen Key Fotos zugeordnet sind,
        // wird der manuelle Text als Beschreibung des vorhandenen Mangelblocks genutzt.
        val verlauf = BautagebuchSpeicher.verlaufFuerBaustelle(context, baustelleId, baustelleName)
        verlauf.forEach { v ->
            v.entry.maengelSchaeden.items.forEachIndexed { i, it ->
                val text = listOfNotNull(it.uhrzeit?.takeIf { z -> z.isNotBlank() }, it.text).joinToString(" ").trim()
                if (text.isBlank()) return@forEachIndexed
                val key = "btb:${v.entry.datumIso}:${v.entry.schicht.name}:$i:${text.hashCode()}"
                val meta = ensure(key)
                meta.datumIso = meta.datumIso ?: v.entry.datumIso
                meta.zeit = meta.zeit ?: it.uhrzeit.orEmpty().ifBlank { "-" }
                meta.bereich = meta.bereich ?: "BTB Abschnitt 8"
                meta.km = meta.km ?: "-"
                meta.firma = meta.firma ?: "-"
                meta.beschreibung = it.text
                meta.gps = meta.gps ?: "-"
            }
        }

        return gruppen.map { (key, meta) ->
            val fotos = meta.fotos.distinctBy { it.foto.id }.sortedBy { it.foto.erstelltAmEpochMs }
            val base = MangelItem(
                key = key,
                datumIso = meta.datumIso ?: fotos.firstOrNull()?.foto?.let { datumIso(it.erstelltAmEpochMs) }.orEmpty(),
                zeit = meta.zeit ?: fotos.firstOrNull()?.foto?.let { zeit(it.erstelltAmEpochMs) }.orEmpty(),
                baustelle = baustelleName,
                bereich = meta.bereich ?: "Hauptbaustelle",
                km = meta.km ?: "-",
                firma = meta.firma ?: "-",
                beschreibung = meta.beschreibung ?: fotos.firstOrNull()?.foto?.kurztext.orEmpty().ifBlank { "-" },
                gps = meta.gps ?: "-",
                fotos = fotos,
                btbBildNr = meta.btbBildNr
            )
            val edit = MangelStatusSpeicher.laden(context, key)
            base.copy(
                bereich = edit.bereichOverride ?: base.bereich,
                km = edit.kmOverride ?: base.km,
                firma = edit.firmaOverride ?: base.firma,
                beschreibung = edit.beschreibungOverride ?: base.beschreibung
            )
        }
            .distinctBy { it.key }
            .sortedWith(compareBy<MangelItem> { it.datumIso }.thenBy { it.zeit }.thenBy { it.beschreibung })
    }

    fun csvText(context: Context, items: List<MangelItem>): String {
        val header = listOf(
            "MangelNr", "Status", "Fotoart", "Datum", "Zeit", "Baustelle", "Unterbaustelle", "KM", "Firma",
            "Beschreibung", "Fotokommentar", "BTB_BildNr", "Bilddatei", "GPS", "Erledigt_am", "Kommentar", "MangelKey"
        )
        return buildString {
            append(header.joinToString(";") { csv(it) }).append('\n')
            items.forEachIndexed { idx, item ->
                val wert = MangelStatusSpeicher.laden(context, item.key)
                val fotos: List<MangelFoto?> = if (item.fotos.isEmpty()) listOf(null) else item.fotos.map { it }
                fotos.forEach { mf ->
                    val f = mf?.foto
                    val row = listOf(
                        (idx + 1).toString().padStart(3, '0'),
                        statusText(wert.status),
                        mf?.art?.let { fotoArtText(it) }.orEmpty(),
                        datumDe(item.datumIso),
                        item.zeit,
                        item.baustelle,
                        item.bereich,
                        item.km,
                        item.firma,
                        item.beschreibung,
                        f?.notiz?.takeIf { it.isNotBlank() }.orEmpty(),
                        item.btbBildNr,
                        f?.dateipfad?.let { File(it).name }.orEmpty(),
                        f?.let { "%.6f, %.6f".format(Locale.US, it.gpsLat, it.gpsLon) } ?: item.gps,
                        wert.erledigtAmEpochMs?.let { datumZeit(it) }.orEmpty(),
                        wert.kommentar,
                        item.key
                    )
                    append(row.joinToString(";") { csv(it) }).append('\n')
                }
            }
        }
    }

    fun pdfBytes(context: Context, baustelleName: String, items: List<MangelItem>, titelSuffix: String = ""): ByteArray {
        val doc = PdfDocument()
        val w = 595
        val h = 842
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var canvas = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10.5f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 7.6f }
        val gpsLinkPaint = Paint(small).apply {
            color = Color.parseColor("#1565C0")
            isUnderlineText = true
        }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val gpsLinks = mutableListOf<PdfGpsLinkAnnotator.Target>()

        fun header() {
            val suffix = titelSuffix.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
            canvas.drawText("MÄNGELLISTE - ${baustelleName.ifBlank { "Baustelle" }}$suffix", 36f, 42f, title)
            canvas.drawText("Stand: ${datumZeit(System.currentTimeMillis())}", 36f, 60f, text)
        }
        fun newPage() {
            doc.finishPage(page)
            pageNo += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            canvas = page.canvas
            header()
        }
        fun wrapLines(s: String, maxW: Float, paint: Paint): List<String> {
            val words = s.split(" ").filter { it.isNotBlank() }
            if (words.isEmpty()) return listOf("")
            val result = mutableListOf<String>()
            var line = ""
            words.forEach { word ->
                val test = if (line.isBlank()) word else "$line $word"
                if (paint.measureText(test) > maxW && line.isNotBlank()) {
                    result.add(line)
                    line = word
                } else {
                    line = test
                }
            }
            if (line.isNotBlank()) result.add(line)
            return result
        }
        fun fieldLines(label: String, value: String, maxW: Float): List<String> = wrapLines("$label$value", maxW, text)
        fun drawField(label: String, value: String, x: Float, startY: Float, maxW: Float): Float {
            val lines = fieldLines(label, value, maxW)
            var cy = startY
            lines.forEachIndexed { index, line ->
                if (index == 0 && line.startsWith(label)) {
                    canvas.drawText(label, x, cy, bold)
                    canvas.drawText(line.removePrefix(label), x + bold.measureText(label), cy, text)
                } else {
                    canvas.drawText(line, x, cy, text)
                }
                cy += text.textSize + 3f
            }
            return cy
        }
        header()
        var y = 84f
        items.forEachIndexed { idx, item ->
            val wert = MangelStatusSpeicher.laden(context, item.key)
            val fotos = item.fotos
            val fotosAnzahl = fotos.size.coerceAtMost(18)
            val rows = if (fotosAnzahl <= 0) 0 else ceil(fotosAnzahl / 3.0).toInt()
            val colW = 238f
            val gvp = gvpMastText(item)
            val leftInfo = buildList {
                add("Datum/Zeit: " to "${datumDe(item.datumIso)} ${item.zeit}")
                add("Baustelle: " to anzeigeBaustelle(item))
                add("Firma: " to item.firma)
                add("KM: " to item.km)
                if (gvp.isNotBlank()) add("GVP/Mast: " to gvp)
                add("Text: " to item.beschreibung)
            }
            val rightInfo = listOf(
                "Fotos: " to "Mangel ${countArt(item, MangelStatusSpeicher.FotoArt.MANGEL)} · in Arbeit ${countArt(item, MangelStatusSpeicher.FotoArt.IN_ARBEIT)} · erledigt ${countArt(item, MangelStatusSpeicher.FotoArt.ERLEDIGT)}",
                "Erledigt: " to (wert.erledigtAmEpochMs?.let { datumZeit(it) } ?: "__________"),
                "Kommentar: " to wert.kommentar.ifBlank { "__________" }
            )
            val leftLines = leftInfo.sumOf { fieldLines(it.first, it.second, colW).size }
            val rightLines = rightInfo.sumOf { fieldLines(it.first, it.second, colW).size }
            val infoH = maxOf(leftLines, rightLines) * (text.textSize + 3f)
            val photoRowH = 176f
            val photosH = if (rows > 0) 18f + rows * photoRowH else 18f
            val cardH = (44f + infoH + photosH + 20f).coerceAtLeast(160f).coerceAtMost(730f)
            if (y + cardH > h - 36f) { newPage(); y = 84f }

            val stroke = statusColor(wert.status)
            val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = stroke }
            val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
            canvas.drawRoundRect(32f, y, w - 32f, y + cardH, 10f, 10f, pFill)
            canvas.drawRoundRect(32f, y, w - 32f, y + cardH, 10f, 10f, pStroke)
            canvas.drawText("Mangel ${(idx + 1).toString().padStart(3, '0')} · ${statusText(wert.status).uppercase(Locale.GERMANY)}", 44f, y + 20f, bold)

            var leftY = y + 40f
            leftInfo.forEach { leftY = drawField(it.first, it.second, 44f, leftY, colW) }
            var rightY = y + 40f
            rightInfo.forEach { rightY = drawField(it.first, it.second, 306f, rightY, colW) }

            var fy = maxOf(leftY, rightY) + 10f
            canvas.drawText("Fotos:", 44f, fy, bold)
            fy += 10f
            var fx = 44f
            val imgW = 160f
            val imgH = 106f
            val rowH = photoRowH
            fotos.take(18).forEachIndexed { fIdx, mf ->
                if (fIdx > 0 && fIdx % 3 == 0) {
                    fx = 44f
                    fy += rowH
                    if (fy + imgH > y + cardH - 12f) return@forEachIndexed
                }
                val src = File(mf.foto.dateipfad)
                if (src.exists()) {
                    val bmp = decodeSampledBitmap(src, 640, 480)
                    if (bmp != null) {
                        drawBitmapFit(canvas, bmp, RectF(fx, fy, fx + imgW, fy + imgH))
                        bmp.recycle()
                    } else {
                        canvas.drawText("Bild nicht lesbar", fx, fy + 28f, small)
                    }
                } else {
                    canvas.drawText("Bild fehlt", fx, fy + 28f, small)
                }
                val caption1 = "${fotoArtText(mf.art)} · ${zeit(mf.foto.erstelltAmEpochMs)} · Bild ${mf.foto.id}"
                canvas.drawText(caption1, fx, fy + imgH + 10f, small)
                val kmText = fotoKmText(mf.foto)
                val gvpFoto = mf.foto.info1Wert?.trim().orEmpty()
                val kmGvp = listOfNotNull(
                    kmText.takeIf { it.isNotBlank() }?.let { "KM: $it" },
                    gvpFoto.takeIf { it.isNotBlank() }?.let { "GVP/Mast: $it" }
                ).joinToString(" · ")
                if (kmGvp.isNotBlank()) canvas.drawText(kmGvp, fx, fy + imgH + 20f, small)
                val gpsText = fotoGpsText(mf.foto)
                if (gpsText.isNotBlank()) {
                    val gpsLabel = "GPS: $gpsText"
                    val gpsBaseline = fy + imgH + 30f
                    canvas.drawText(gpsLabel, fx, gpsBaseline, gpsLinkPaint)
                    gpsLinks += PdfGpsLinkAnnotator.Target(
                        pageIndex = pageNo - 1,
                        x = fx,
                        top = gpsBaseline - gpsLinkPaint.textSize - 2f,
                        width = gpsLinkPaint.measureText(gpsLabel),
                        height = gpsLinkPaint.textSize + 5f,
                        lat = mf.foto.gpsLat,
                        lon = mf.foto.gpsLon
                    )
                }
                val kommentar = mf.foto.notiz?.trim()?.takeIf { it.isNotBlank() } ?: mf.foto.kurztext.trim()
                wrapLines(kommentar, imgW, small).take(2).forEachIndexed { lineIdx, line ->
                    canvas.drawText(line, fx, fy + imgH + 40f + lineIdx * 9f, small)
                }
                fx += imgW + 12f
            }
            if (fotos.isEmpty()) {
                canvas.drawText("Keine Fotos zugeordnet", 44f, fy + 14f, small)
            }
            if (fotos.size > 18) canvas.drawText("Weitere Fotos: ${fotos.size - 18} (im ZIP/Bilderordner)", 44f, y + cardH - 12f, small)
            y += cardH + 10f
        }
        if (items.isEmpty()) canvas.drawText("Keine Mängel vorhanden.", 36f, y, text)
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        val rawPdf = out.toByteArray()
        return PdfGpsLinkAnnotator.addGoogleMapsLinks(context, rawPdf, gpsLinks)
    }

    fun statusText(status: MangelStatusSpeicher.Status): String = when (status) {
        MangelStatusSpeicher.Status.OFFEN -> "offen"
        MangelStatusSpeicher.Status.IN_ARBEIT -> "in Arbeit"
        MangelStatusSpeicher.Status.ERLEDIGT -> "erledigt"
        MangelStatusSpeicher.Status.ABGELEHNT -> "abgelehnt"
    }

    fun fotoArtText(art: MangelStatusSpeicher.FotoArt): String = when (art) {
        MangelStatusSpeicher.FotoArt.MANGEL -> "Mangel"
        MangelStatusSpeicher.FotoArt.IN_ARBEIT -> "In Arbeit"
        MangelStatusSpeicher.FotoArt.ERLEDIGT -> "Erledigt"
    }

    fun statusColor(status: MangelStatusSpeicher.Status): Int = when (status) {
        MangelStatusSpeicher.Status.OFFEN -> Color.parseColor("#C62828")
        MangelStatusSpeicher.Status.IN_ARBEIT -> Color.parseColor("#F9A825")
        MangelStatusSpeicher.Status.ERLEDIGT -> Color.parseColor("#2E7D32")
        MangelStatusSpeicher.Status.ABGELEHNT -> Color.parseColor("#607D8B")
    }

    fun datumDe(iso: String): String = runCatching { LocalDate.parse(iso, isoFmt).format(deFmt) }.getOrElse { iso }
    fun datumZeit(epoch: Long): String {
        val z = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
        return "%02d.%02d.%04d %02d:%02d".format(z.dayOfMonth, z.monthValue, z.year, z.hour, z.minute)
    }

    fun countArt(item: MangelItem, art: MangelStatusSpeicher.FotoArt): Int = item.fotos.count { it.art == art }

    fun anzeigeBaustelle(item: MangelItem): String =
        item.bereich.trim().takeIf { it.isNotBlank() && !it.equals("Hauptbaustelle", ignoreCase = true) }
            ?: item.baustelle.ifBlank { "Hauptbaustelle" }

    fun gvpMastText(item: MangelItem): String =
        item.fotos.asSequence().mapNotNull { it.foto.info1Wert?.trim()?.takeIf(String::isNotBlank) }.firstOrNull().orEmpty()

    fun fotoKmText(foto: FotoEntity): String =
        foto.km?.let { de.tbhelfer.util.KmAnzeige.wert(it) }.orEmpty()

    fun fotoGpsText(foto: FotoEntity): String =
        if (foto.gpsLat == 0.0 && foto.gpsLon == 0.0) "" else "%.6f, %.6f".format(Locale.US, foto.gpsLat, foto.gpsLon)

    private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
    private fun datumIso(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    private fun zeit(epoch: Long): String {
        val z = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
        return "%02d:%02d".format(z.hour, z.minute)
    }
    private fun epoch(datumIso: String?, zeit: String?): Long {
        val d = runCatching { LocalDate.parse(datumIso.orEmpty(), isoFmt) }.getOrDefault(LocalDate.now())
        val parts = zeit.orEmpty().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return d.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    fun artSort(art: MangelStatusSpeicher.FotoArt): Int = when (art) {
        MangelStatusSpeicher.FotoArt.MANGEL -> 0
        MangelStatusSpeicher.FotoArt.IN_ARBEIT -> 1
        MangelStatusSpeicher.FotoArt.ERLEDIGT -> 2
    }

    private fun decodeSampledBitmap(src: File, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        var sample = 1
        val h = bounds.outHeight
        val w = bounds.outWidth
        if (h > reqH || w > reqW) {
            var halfH = h / 2
            var halfW = w / 2
            while ((halfH / sample) >= reqH && (halfW / sample) >= reqW) {
                sample *= 2
            }
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeFile(src.absolutePath, opts) }.getOrNull()
    }

    private fun drawBitmapFit(canvas: android.graphics.Canvas, bmp: Bitmap, box: RectF) {
        val bw = bmp.width.toFloat().coerceAtLeast(1f)
        val bh = bmp.height.toFloat().coerceAtLeast(1f)
        val scale = minOf(box.width() / bw, box.height() / bh)
        val dw = bw * scale
        val dh = bh * scale
        val left = box.left + (box.width() - dw) / 2f
        val top = box.top + (box.height() - dh) / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), null)
    }

    private fun drawWrapped(canvas: android.graphics.Canvas, s: String, x: Float, y: Float, maxW: Float, paint: Paint) {
        var cy = y
        val words = s.split(" ")
        var line = ""
        words.forEach { word ->
            val test = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(test) > maxW && line.isNotBlank()) {
                canvas.drawText(line, x, cy, paint)
                cy += paint.textSize + 2f
                line = word
            } else line = test
        }
        if (line.isNotBlank()) canvas.drawText(line, x, cy, paint)
    }

    private fun knotenAnzeige(k: BaustellenKnotenEntity?): String {
        if (k == null) return "Hauptbaustelle"
        val base = k.knotenName?.trim().takeUnless { it.isNullOrBlank() }
            ?: listOfNotNull(k.bahnhofName, k.gleisOderWeiche).joinToString(" – ").takeIf { it.isNotBlank() }
            ?: k.streckenkennung?.trim().orEmpty()
        val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
            ?.let { " $it" }.orEmpty()
        return (base + km).ifBlank { "Hauptbaustelle" }
    }
}
