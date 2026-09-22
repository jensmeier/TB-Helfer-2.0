package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.ui.kamera.FotoOptionalFelderSpeicher
import de.tbhelfer.ui.baustelle.MaengellisteExport
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import de.tbhelfer.ui.abnahme.AbnahmeExport
import de.tbhelfer.util.ImageExifUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BtbExportPaket {

    data class Ergebnis(
        val zipFile: File,
        val fotosExportiert: Int,
        val fehlendeFotos: Int
    )

    private data class FotoExportInfo(
        val foto: FotoEntity,
        val exportName: String
    )

    private data class BtbFotoPlan(
        /** Genau die Foto-Liste, die PDF/BTB benutzt: Schicht + manuelle Auswahl + PDF-Reihenfolge + maxFotosInPdf. */
        val selectedFotosOrdered: List<FotoEntity>,
        /** PDF-/BTB-Bildnummer pro Foto-ID. Export-Bildnummern sind davon getrennt. */
        val fotoNum: Map<Long, Int>,
        /** Abschnitt im PDF/BTB pro Foto-ID, z. B. "8. Mängel/Schäden". */
        val abschnittById: Map<Long, String>
    )

    private data class TxtLine(
        val firma: String?,
        val ort: String?,
        val text: String,
        val sortMs: Long = 0L
    )

    private val isoFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val deDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun safeSegment(input: String): String {
        return input.trim()
            .replace("..", ".")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(80)
            .ifBlank { "Unbekannt" }
    }

    private fun safeFilePart(input: String?, fallback: String, maxLen: Int): String {
        return safeSegment(input.orEmpty())
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.', ' ')
            .take(maxLen)
            .ifBlank { fallback }
    }

    /**
     * Dateiname für ZIP-Bilder: bereinigt Sonderzeichen, kürzt aber so,
     * dass die Dateiendung (.jpg/.jpeg/...) niemals abgeschnitten wird.
     */
    private fun safeFileNameKeepingExtension(input: String, maxLen: Int = 145): String {
        val cleaned = input.trim()
            .replace("..", ".")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.', ' ')
            .ifBlank { "Foto.jpg" }
        val dot = cleaned.lastIndexOf('.')
        val ext = if (dot > 0) cleaned.substring(dot).take(12) else ""
        val base = if (dot > 0) cleaned.substring(0, dot) else cleaned
        val maxBase = (maxLen - ext.length).coerceAtLeast(24)
        return (base.take(maxBase).trim('_', '.', ' ').ifBlank { "Foto" } + ext).ifBlank { "Foto.jpg" }
    }

    private fun parseTime(raw: String?): LocalTime? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null
        return runCatching { LocalTime.parse(s, timeFmt) }.getOrNull()
    }

    private fun shiftWindowEpochMs(entry: BautagebuchEintrag): Pair<Long, Long> {
        val date = runCatching { LocalDate.parse(entry.datumIso, isoFmt) }.getOrDefault(LocalDate.now())
        val zone = ZoneId.systemDefault()
        val start = parseTime(entry.startUhrzeit) ?: LocalTime.MIN
        val end = parseTime(entry.endeUhrzeit) ?: LocalTime.MAX
        val startDateTime = date.atTime(start)
        val endDateTime = if (end.isAfter(start)) date.atTime(end) else date.plusDays(1).atTime(end)
        return startDateTime.atZone(zone).toInstant().toEpochMilli() to endDateTime.atZone(zone).toInstant().toEpochMilli()
    }

    private fun fotoZeit(f: FotoEntity): String {
        return Instant.ofEpochMilli(f.erstelltAmEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }

    private fun fotoZeitDatei(f: FotoEntity): String {
        return Instant.ofEpochMilli(f.erstelltAmEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
    }

    private fun fotoZeitKurz(f: FotoEntity): String {
        return Instant.ofEpochMilli(f.erstelltAmEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun fotoDatumIso(f: FotoEntity): String {
        return Instant.ofEpochMilli(f.erstelltAmEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(isoFmt)
    }

    private fun addText(zos: ZipOutputStream, path: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        zos.putNextEntry(ZipEntry(path))
        zos.write(bytes)
        zos.closeEntry()
    }

    /**
     * CSV wird mit UTF-8-BOM geschrieben, damit Excel Umlaute direkt korrekt erkennt
     * (z. B. "Ausgeführte Arbeiten" statt "AusgefÃ¼hrte Arbeiten").
     */
    private fun addCsvText(zos: ZipOutputStream, path: String, text: String) {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = text.toByteArray(Charsets.UTF_8)
        zos.putNextEntry(ZipEntry(path))
        zos.write(bom)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun addBinary(zos: ZipOutputStream, path: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun xmlEsc(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun docxPara(text: String, bold: Boolean = false, sizeHalfPt: Int = 22, indentTwips: Int = 0): String {
        val pPr = buildString {
            append("<w:pPr>")
            if (indentTwips > 0) append("<w:ind w:left=\"").append(indentTwips).append("\"/>")
            append("<w:spacing w:after=\"60\"/>")
            append("</w:pPr>")
        }
        val rPr = buildString {
            append("<w:rPr>")
            if (bold) append("<w:b/>")
            append("<w:sz w:val=\"").append(sizeHalfPt).append("\"/>")
            append("<w:szCs w:val=\"").append(sizeHalfPt).append("\"/>")
            append("</w:rPr>")
        }
        return "<w:p>$pPr<w:r>$rPr<w:t xml:space=\"preserve\">${xmlEsc(text)}</w:t></w:r></w:p>"
    }

    /**
     * Einfache DOCX-Erzeugung ohne externe Bibliothek.
     * Das DOCX ist die bearbeitbare Kopie des BTB-Hauptteils; Bilder bleiben separat im ZIP.
     */
    private fun btbDocxBytes(btbText: String): ByteArray {
        fun put(zos: ZipOutputStream, path: String, text: String) {
            zos.putNextEntry(ZipEntry(path))
            zos.write(text.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".trimIndent()

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".trimIndent()

        val documentBody = buildString {
            btbText.lines().forEach { raw ->
                val line = raw.trimEnd()
                val trimmed = line.trim()
                when {
                    trimmed.isBlank() -> append("<w:p/>")
                    trimmed == "BAUTAGEBUCH" -> append(docxPara(trimmed, bold = true, sizeHalfPt = 32))
                    trimmed.matches(Regex("""^(WETTER|PERSONAL|MASCHINEN / GERÄTE|\d+\. .*)$""")) -> append(docxPara(trimmed, bold = true, sizeHalfPt = 26))
                    trimmed.startsWith("Ort:") || trimmed.startsWith("Station:") || trimmed.startsWith("Koordinaten:") -> append(docxPara(trimmed, bold = false, sizeHalfPt = 20, indentTwips = 360))
                    line.startsWith("    - ") -> append(docxPara("• " + trimmed.removePrefix("-").trim(), sizeHalfPt = 22, indentTwips = 1080))
                    line.startsWith("    ") -> append(docxPara(trimmed, bold = true, sizeHalfPt = 22, indentTwips = 720))
                    line.startsWith("  - ") -> append(docxPara("• " + trimmed.removePrefix("-").trim(), sizeHalfPt = 22, indentTwips = 720))
                    line.startsWith("  ") -> append(docxPara(trimmed, bold = true, sizeHalfPt = 22, indentTwips = 360))
                    line.startsWith("- ") -> append(docxPara("• " + trimmed.removePrefix("-").trim(), sizeHalfPt = 22, indentTwips = 360))
                    else -> append(docxPara(trimmed, bold = true, sizeHalfPt = 22))
                }
            }
            append("""
<w:sectPr>
  <w:pgSz w:w="11906" w:h="16838"/>
  <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/>
</w:sectPr>""".trimIndent())
        }

        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>$documentBody</w:body>
</w:document>""".trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            put(zos, "[Content_Types].xml", contentTypes)
            put(zos, "_rels/.rels", rels)
            put(zos, "word/document.xml", docXml)
        }
        return out.toByteArray()
    }



    private fun btbTextFromModell(
        model: BtbDruckModell,
        identity: BtbDokumentIdentitaet.Identitaet
    ): String {
        return buildString {
            appendLine("BAUTAGEBUCH")
            appendLine("Baustelle: ${model.baustelle}")
            identity.firmenname?.takeIf { it.isNotBlank() }?.let { appendLine("Firma: $it") }
            appendLine("Verantwortlicher TB: ${identity.verantwortlicherTb}")
            appendLine("Schicht: ${identity.schichtAnzeige}")
            identity.btbId?.takeIf { it.isNotBlank() }?.let { appendLine("BTB-ID: $it") }
            appendLine("Datum: ${model.datumAnzeige}")
            appendLine("Zeit: ${model.zeitAnzeige}")
            appendLine()

            appendLine("WETTER")
            model.wetterInfo?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            model.wetter.ifEmpty { listOf("Keine Wetterdaten") }.forEach { appendLine("- $it") }
            appendLine()

            appendLine("PERSONAL")
            if (model.personal.isEmpty()) {
                appendLine("- Keine Einträge")
            } else {
                model.personal.forEach { block ->
                    appendLine(block.kopf)
                    block.zeilen.forEach { appendLine("  - $it") }
                }
            }
            appendLine()

            appendLine("MASCHINEN / GERÄTE")
            if (model.maschinen.isEmpty()) {
                appendLine("- Keine Einträge")
            } else {
                model.maschinen.forEach { block ->
                    appendLine(block.kopf)
                    block.zeilen.forEach { appendLine("  - $it") }
                }
            }
            appendLine()

            model.abschnitte.forEach { abschnitt ->
                appendLine(abschnitt.titel.uppercase(Locale.GERMANY))
                if (abschnitt.firmen.isEmpty()) {
                    appendLine("- ${abschnitt.leerText}")
                    appendLine()
                } else {
                    abschnitt.firmen.forEach { firma ->
                        appendLine(firma.name)
                        var lastParent: String? = null
                        firma.orte.forEach { ort ->
                            val parent = ort.parentName?.takeIf { it.isNotBlank() }
                            if (ort.depth > 0 && parent != null && parent != lastParent) {
                                appendLine("  $parent")
                                lastParent = parent
                            }
                            if (ort.depth > 0) {
                                appendLine("    ${ort.name}")
                                ort.eintraege.forEach { appendLine("    - $it") }
                            } else {
                                appendLine("  ${ort.name}")
                                lastParent = ort.name
                                ort.eintraege.forEach { appendLine("  - $it") }
                            }
                        }
                        appendLine()
                    }
                }
            }
        }
    }

    private fun addFile(zos: ZipOutputStream, src: File, path: String) {
        FileInputStream(src).buffered(64 * 1024).use { input ->
            zos.putNextEntry(ZipEntry(path))
            input.copyTo(zos, 64 * 1024)
            zos.closeEntry()
        }
    }

    private fun addBildDateiKomprimiert(context: Context, zos: ZipOutputStream, src: File, path: String) {
        val ext = src.extension.lowercase(Locale.ROOT)
        if (ext != "jpg" && ext != "jpeg") {
            addFile(zos, src, path)
            return
        }
        val tmp = File(context.cacheDir, "tbh_abnahme_plan_${System.nanoTime()}.jpg")
        try {
            val decoded = ImageExifUtil.decodeSampledBitmapRespectingExif(src.absolutePath, 1024, 1024, Bitmap.Config.ARGB_8888)
                ?: throw IllegalStateException("Bild konnte nicht gelesen werden")
            val maxSide = maxOf(decoded.width, decoded.height)
            val scale = (2048f / maxSide.toFloat()).coerceAtMost(1f)
            val outW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val outH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val outBmp = if (outW != decoded.width || outH != decoded.height) {
                Bitmap.createScaledBitmap(decoded, outW, outH, true).also { if (it !== decoded) decoded.recycle() }
            } else decoded
            tmp.outputStream().buffered(64 * 1024).use { out -> outBmp.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            outBmp.recycle()
            addFile(zos, tmp, path)
        } catch (_: Throwable) {
            addFile(zos, src, path)
        } finally {
            runCatching { tmp.delete() }
        }
    }

    private fun abnahmeDatumTreffer(a: AbnahmeSpeicher.Abnahme, datum: LocalDate): Boolean {
        return listOfNotNull(a.startEpochMs, a.geaendertAmEpochMs, a.abgeschlossenAmEpochMs)
            .map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            .any { it == datum }
    }

    private fun addFotoFileMitSuchMetadaten(
        context: Context,
        zos: ZipOutputStream,
        src: File,
        path: String,
        entry: BautagebuchEintrag,
        foto: FotoEntity,
        labels: List<String>,
        knotenLabels: Map<Long, String>,
        btbPlan: BtbFotoPlan
    ) {
        val ext = src.extension.lowercase(Locale.ROOT)
        if (ext != "jpg" && ext != "jpeg") {
            addFile(zos, src, path)
            return
        }

        // Die Originaldatei auf dem Handy wird nicht verändert. Für das ZIP entsteht
        // eine dokumentationstaugliche, deutlich kleinere Kopie (max. 2048 px, JPEG 85).
        val tmp = File(context.cacheDir, "tbh_export_${System.nanoTime()}.jpg")
        try {
            val decoded = ImageExifUtil.decodeSampledBitmapRespectingExif(
                src.absolutePath,
                // 1024 erzwingt bei typischen 12-MP-Fotos ein speicherschonendes
                // inSampleSize=2; die resultierende lange Seite liegt trotzdem nahe 2048 px.
                1024,
                1024,
                Bitmap.Config.ARGB_8888
            ) ?: throw IllegalStateException("Bild konnte nicht gelesen werden")
            val maxSide = maxOf(decoded.width, decoded.height)
            val scale = (2048f / maxSide.toFloat()).coerceAtMost(1f)
            val outW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val outH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val exportBitmap = if (outW != decoded.width || outH != decoded.height) {
                Bitmap.createScaledBitmap(decoded, outW, outH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else decoded
            tmp.outputStream().buffered(64 * 1024).use { out ->
                if (!exportBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
                    throw IllegalStateException("JPEG-Komprimierung fehlgeschlagen")
                }
            }
            exportBitmap.recycle()

            kopiereExifBasis(src, tmp, foto)
            val meta = fotoMetaTextBlock(entry, foto, labels, knotenLabels, btbPlan)
            ImageExifUtil.writeFotoMetadata(
                file = tmp,
                lat = foto.gpsLat.takeIf { it != 0.0 },
                lon = foto.gpsLon.takeIf { it != 0.0 },
                metaTextBlock = meta,
                forceOrientationNormal = true,
                windowsTitle = windowsTitle(entry, foto, knotenLabels),
                windowsSubject = windowsSubject(foto, knotenLabels),
                windowsComment = meta,
                windowsKeywords = windowsKeywords(entry, foto, knotenLabels, btbPlan)
            )
            addFile(zos, tmp, path)
        } catch (_: Throwable) {
            // Bei einem unbekannten Bildformat/Decoderproblem lieber das Original exportieren,
            // statt ein Bild im Dokumentationspaket zu verlieren.
            addFile(zos, src, path)
        } finally {
            runCatching { tmp.delete() }
        }
    }

    /**
     * Komprimierte Exportkopie für den Gesamt-/Zeitraum-ZIP-Export.
     * Das Original auf dem Handy bleibt unverändert; EXIF-Basisdaten und
     * suchbare TB-Helfer-Metadaten werden in die ZIP-Kopie übernommen.
     */
    private fun addFotoFileKomprimiert(
        context: Context,
        zos: ZipOutputStream,
        src: File,
        path: String,
        baustelleName: String,
        foto: FotoEntity,
        labels: List<String>,
        knotenLabels: Map<Long, String>
    ) {
        val ext = src.extension.lowercase(Locale.ROOT)
        if (ext != "jpg" && ext != "jpeg") {
            addFile(zos, src, path)
            return
        }

        val tmp = File(context.cacheDir, "tbh_export_gesamt_${System.nanoTime()}.jpg")
        try {
            val decoded = ImageExifUtil.decodeSampledBitmapRespectingExif(
                src.absolutePath,
                1024,
                1024,
                Bitmap.Config.ARGB_8888
            ) ?: throw IllegalStateException("Bild konnte nicht gelesen werden")
            val maxSide = maxOf(decoded.width, decoded.height)
            val scale = (2048f / maxSide.toFloat()).coerceAtMost(1f)
            val outW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val outH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val exportBitmap = if (outW != decoded.width || outH != decoded.height) {
                Bitmap.createScaledBitmap(decoded, outW, outH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else decoded
            tmp.outputStream().buffered(64 * 1024).use { out ->
                if (!exportBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
                    throw IllegalStateException("JPEG-Komprimierung fehlgeschlagen")
                }
            }
            exportBitmap.recycle()

            kopiereExifBasis(src, tmp, foto)
            val ort = unterbaustelle(foto, knotenLabels)
            val meta = buildString {
                appendLine("TBHelfer Foto")
                appendLine("Baustelle: $baustelleName")
                if (ort.isNotBlank()) appendLine("Unterbaustelle: $ort")
                appendLine("Datum/Zeit: ${fotoZeit(foto)}")
                appendLine("GPS: ${foto.gpsLat}, ${foto.gpsLon}")
                val kmText = kmCsv(foto.km)
                if (kmText.isNotBlank()) appendLine("KM: $kmText")
                appendLine("Firma: ${foto.firma}")
                appendLine("Bildtyp: ${foto.bildtyp}")
                appendLine("Kurzbeschreibung: ${foto.kurztext}")
                if (!foto.notiz.isNullOrBlank()) appendLine("Langbeschreibung: ${foto.notiz}")
                if (!foto.objektKennung.isNullOrBlank()) appendLine("Objektkennung: ${foto.objektKennung}")
                if (!foto.objektBereich.isNullOrBlank()) appendLine("Bereich: ${foto.objektBereich}")
                optionalLines(foto, labels).forEach { appendLine(it) }
            }.trim()
            val title = listOf("TBHelfer", baustelleName, ort, kmCsv(foto.km), foto.kurztext)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .take(240)
            val subject = listOf(foto.firma, ort, kmCsv(foto.km), foto.bildtyp.toString(), foto.kurztext)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .take(240)
            val keywords = listOf(
                "TB-Helfer",
                baustelleName,
                ort,
                kmCsv(foto.km),
                foto.firma,
                foto.bildtyp.toString(),
                foto.kurztext
            ).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("; ").take(1000)
            ImageExifUtil.writeFotoMetadata(
                file = tmp,
                lat = foto.gpsLat.takeIf { it != 0.0 },
                lon = foto.gpsLon.takeIf { it != 0.0 },
                metaTextBlock = meta,
                forceOrientationNormal = true,
                windowsTitle = title,
                windowsSubject = subject,
                windowsComment = meta,
                windowsKeywords = keywords
            )
            addFile(zos, tmp, path)
        } catch (_: Throwable) {
            addFile(zos, src, path)
        } finally {
            runCatching { tmp.delete() }
        }
    }

    private fun kopiereExifBasis(src: File, dst: File, foto: FotoEntity) {
        runCatching {
            val from = ExifInterface(src)
            val to = ExifInterface(dst)
            val tags = listOf(
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_OFFSET_TIME,
                ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
                ExifInterface.TAG_SUBSEC_TIME,
                ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_FOCAL_LENGTH
            )
            tags.forEach { tag -> from.getAttribute(tag)?.let { to.setAttribute(tag, it) } }
            val ll = FloatArray(2)
            if (from.getLatLong(ll)) to.setLatLong(ll[0].toDouble(), ll[1].toDouble())
            else if (foto.gpsLat != 0.0 && foto.gpsLon != 0.0) to.setLatLong(foto.gpsLat, foto.gpsLon)
            to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            to.saveAttributes()
        }
    }

    private fun uniquePath(basePath: String, used: MutableSet<String>): String {
        if (used.add(basePath)) return basePath
        val dot = basePath.lastIndexOf('.')
        val base = if (dot > 0) basePath.substring(0, dot) else basePath
        val ext = if (dot > 0) basePath.substring(dot) else ""
        var i = 2
        while (true) {
            val candidate = "$base($i)$ext"
            if (used.add(candidate)) return candidate
            i++
        }
    }

    private fun kmStr(km: Double?): String? = km?.let { de.tbhelfer.util.KmAnzeige.wert(it) }

    private fun knotenAnzeigeName(k: BaustellenKnotenEntity?): String {
        if (k == null) return ""
        val name = k.knotenName?.trim().orEmpty()
        val km = kmStr(k.kmVon)
        if (name.isNotBlank()) return if (km != null) "$name (KM $km)" else name

        val bahnhof = k.bahnhofName?.trim().orEmpty()
        val gleis = k.gleisOderWeiche?.trim().orEmpty()
        if (bahnhof.isNotBlank() && gleis.isNotBlank()) return "$bahnhof – $gleis"
        if (bahnhof.isNotBlank()) return bahnhof

        val strecke = k.streckenkennung?.trim().orEmpty()
        if (strecke.isNotBlank()) return if (km != null) "$strecke (KM $km)" else strecke

        return k.hauptbaustellenName.trim()
    }

    private fun normalizeOrtLabel(raw: String): String {
        val cleaned = raw.trim().removePrefix("—").trim()
        return when {
            cleaned.isBlank() -> ""
            cleaned.equals("Nur Hauptbaustelle", ignoreCase = true) -> "Hauptbaustelle"
            cleaned.endsWith("Nur Hauptbaustelle", ignoreCase = true) -> "Hauptbaustelle"
            else -> cleaned
        }
    }

    private fun unterbaustelle(f: FotoEntity, knotenLabels: Map<Long, String>): String {
        val id = f.baustellenKnotenId ?: return ""
        return normalizeOrtLabel(knotenLabels[id].orEmpty())
    }

    private fun optionalLines(f: FotoEntity, labels: List<String>): List<String> {
        val alleLabels = listOf(
            "GVP / Mast",
            "AP / KOR",
            labels.getOrNull(0)?.trim().orEmpty().ifBlank { "Info1" },
            labels.getOrNull(1)?.trim().orEmpty().ifBlank { "Info2" },
            labels.getOrNull(2)?.trim().orEmpty().ifBlank { "Info3" }
        )
        val alleWerte = listOf(f.info1Wert, f.info2Wert, f.info3Wert, f.info4Wert, f.info5Wert)
        return alleWerte.mapIndexedNotNull { index, value ->
            val v = value?.trim().orEmpty()
            if (v.isBlank()) null else "${alleLabels.getOrNull(index) ?: "Info${index + 1}"}: $v"
        }
    }

    private fun cleanMarkerText(raw: String): String {
        return raw
            .replace(Regex("""@ORT=\d+"""), " ")
            .replace("@ALLGEMEIN", " ")
            .replace(Regex("""^[•\-]\s*"""), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun splitFirmaPrefix(raw: String, knownFirms: Set<String>): Pair<String?, String> {
        val txt = cleanMarkerText(raw)
        val idx = txt.indexOf(':')
        if (idx <= 0) return null to txt
        val prefix = txt.substring(0, idx).trim()
        val tail = txt.substring(idx + 1).trim()
        if (prefix.isBlank() || tail.isBlank()) return null to txt
        if (Regex("""^\d{1,2}$""").matches(prefix)) return null to txt
        val canonical = knownFirms.firstOrNull { it.equals(prefix, ignoreCase = true) } ?: prefix
        return canonical to tail
    }

    private fun manualLinesFromRaw(raw: String?, knownFirms: Set<String>): List<TxtLine> {
        return raw.orEmpty().lines().mapNotNull { line ->
            val forcedGeneral = line.contains("@ALLGEMEIN")
            val z = de.tbhelfer.util.KmAnzeige.zerlegen(line)
            val cleaned = cleanMarkerText(z.text)
            if (cleaned.isBlank()) return@mapNotNull null
            val (firma, txt) = if (forcedGeneral) null to cleaned else splitFirmaPrefix(cleaned, knownFirms)
            val km = de.tbhelfer.util.KmAnzeige.bereich(z.kmVon, z.kmBis, inKlammern = true)
            TxtLine(firma = firma, ort = null, text = listOfNotNull(km, txt).joinToString(" "))
        }
    }

    private fun manualLinesFromBlock(block: SectionBlock, knownFirms: Set<String>): List<TxtLine> {
        return block.items.mapNotNull { item ->
            val forcedGeneral = item.text.contains("@ALLGEMEIN")
            val cleaned = cleanMarkerText(item.text)
            if (cleaned.isBlank()) return@mapNotNull null
            val (firma, txt) = if (forcedGeneral) null to cleaned else splitFirmaPrefix(cleaned, knownFirms)
            val time = item.uhrzeit?.trim().orEmpty().takeIf { it.isNotBlank() }
            val kmLabel = de.tbhelfer.util.KmAnzeige.bereich(item.kmVon, item.kmBis, inKlammern = true)
            val line = listOfNotNull(time, kmLabel, txt).joinToString(" ")
            TxtLine(firma = firma, ort = null, text = line)
        }
    }

    private fun bildSuffix(nums: List<Int>): String {
        if (nums.isEmpty()) return ""
        val sorted = nums.distinct().sorted()
        val ranges = ArrayList<Pair<Int, Int>>()
        var a = sorted.first()
        var b = a
        for (n in sorted.drop(1)) {
            if (n == b + 1) b = n else {
                ranges.add(a to b)
                a = n
                b = n
            }
        }
        ranges.add(a to b)
        val label = ranges.joinToString("+") { (x, y) -> if (x == y) "$x" else "$x–$y" }
        return " [Bild $label]"
    }

    private val anhangOrder = listOf(
        Bildtyp.ABNAHMEN,
        Bildtyp.EINWEISUNG_UNTERWEISUNG,
        Bildtyp.BESONDERHEITEN,
        Bildtyp.MAENGEL_SCHAEDEN,
        Bildtyp.FOTODOKUMENTATION
    )

    private fun pdfAbschnitt(type: Bildtyp): String = when (type) {
        Bildtyp.FOTODOKUMENTATION -> "4. Ausgeführte Arbeiten"
        Bildtyp.ABNAHMEN -> "5. Abnahmen"
        Bildtyp.EINWEISUNG_UNTERWEISUNG -> "6. Einweisung/Unterweisung"
        Bildtyp.BESONDERHEITEN -> "7. Besonderheiten"
        Bildtyp.MAENGEL_SCHAEDEN -> "8. Mängel/Schäden"
    }

    private fun buildBtbFotoPlan(
        context: Context,
        entry: BautagebuchEintrag,
        fotosImZeitfenster: List<FotoEntity>
    ): BtbFotoPlan {
        if (!entry.includeFotos) return BtbFotoPlan(emptyList(), emptyMap(), emptyMap())

        val excludedByType = BtbPhotoSelectionStore.loadExcluded(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        )

        val ausgewaehlt = fotosImZeitfenster.filter { f ->
            val ex = excludedByType[f.bildtyp.name]
            ex == null || !ex.contains(f.id)
        }

        val byType = ausgewaehlt.groupBy { it.bildtyp }
        val selectedOrdered = anhangOrder
            .flatMap { typ -> byType[typ].orEmpty().sortedBy { it.erstelltAmEpochMs } }
            .take(entry.maxFotosInPdf.coerceAtLeast(1))

        val fotoNum = LinkedHashMap<Long, Int>()
        val abschnittById = LinkedHashMap<Long, String>()
        selectedOrdered.forEachIndexed { index, f ->
            fotoNum[f.id] = index + 1
            abschnittById[f.id] = pdfAbschnitt(f.bildtyp)
        }

        return BtbFotoPlan(
            selectedFotosOrdered = selectedOrdered,
            fotoNum = fotoNum,
            abschnittById = abschnittById
        )
    }

    private fun hmToMin(hm: String): Int? {
        val p = hm.split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun minToHm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    private fun normalizeHm(hm: String): String? {
        val min = hmToMin(hm) ?: return null
        return minToHm(min)
    }

    private fun parsePhotoTimeOverride(raw: String?): Pair<Int?, Int?>? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null
        normalizeHm(s)?.let { one ->
            val m = hmToMin(one) ?: return null
            return m to null
        }
        val match = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})$""").matchEntire(s)
            ?: return null
        val start = normalizeHm(match.groupValues[1])?.let { hmToMin(it) } ?: return null
        val end = normalizeHm(match.groupValues[2])?.let { hmToMin(it) } ?: return null
        return start to end
    }

    private fun wallTimeEpochNear(refEpochMs: Long, minuteOfDay: Int): Long {
        val zone = ZoneId.systemDefault()
        val ref = Instant.ofEpochMilli(refEpochMs).atZone(zone)
        val base = ref.toLocalDate()
        return listOf(-1L, 0L, 1L)
            .map { delta ->
                base.plusDays(delta)
                    .atStartOfDay(zone)
                    .plusMinutes(minuteOfDay.toLong())
                    .toInstant()
                    .toEpochMilli()
            }
            .minBy { kotlin.math.abs(it - refEpochMs) }
    }

    private fun effectiveRangeFor(
        ids: List<Long>,
        autoStartEpochMs: Long,
        autoEndEpochMs: Long,
        startOverrides: Map<Long, String>
    ): Pair<String, String> {
        val manualRanges = ids.asSequence()
            .mapNotNull { startOverrides[it] }
            .mapNotNull { parsePhotoTimeOverride(it) }
            .toList()
        val manualStartEpoch = manualRanges.mapNotNull { it.first?.let { min -> wallTimeEpochNear(autoStartEpochMs, min) } }.minOrNull()
        val manualEndEpoch = manualRanges.mapNotNull { it.second?.let { min -> wallTimeEpochNear(autoEndEpochMs, min) } }.maxOrNull()
        val startEpoch = manualStartEpoch?.let { minOf(it, autoStartEpochMs) } ?: autoStartEpochMs
        val endEpoch = manualEndEpoch?.let { maxOf(it, autoEndEpochMs) } ?: autoEndEpochMs
        val start = Instant.ofEpochMilli(startEpoch).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
        val end = Instant.ofEpochMilli(endEpoch).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
        return start to end
    }

    private fun btbTextOfFoto(f: FotoEntity, textOverrides: Map<Long, String>): String {
        val ov = textOverrides[f.id]?.trim().orEmpty()
        if (ov.isNotBlank()) return ov
        return f.notiz?.trim()?.takeIf { it.isNotBlank() } ?: f.kurztext.trim()
    }

    private fun fotoLinesForType(
        fotos: List<FotoEntity>,
        type: Bildtyp,
        fotoNum: Map<Long, Int>,
        knotenLabels: Map<Long, String>,
        textOverrides: Map<Long, String>,
        startOverrides: Map<Long, String>
    ): List<TxtLine> {
        data class Key(val firma: String?, val ort: String?, val text: String)
        return fotos
            .filter { it.bildtyp == type }
            .groupBy { f ->
                Key(
                    firma = f.firma.trim().takeIf { it.isNotBlank() },
                    ort = unterbaustelle(f, knotenLabels).takeIf { it.isNotBlank() },
                    text = btbTextOfFoto(f, textOverrides)
                )
            }
            .mapNotNull { (key, list) ->
                val sorted = list.sortedBy { it.erstelltAmEpochMs }
                val first = sorted.firstOrNull() ?: return@mapNotNull null
                if (key.text.isBlank()) return@mapNotNull null
                val last = sorted.last()
                val (startHm, endHm) = effectiveRangeFor(
                    ids = sorted.map { it.id },
                    autoStartEpochMs = first.erstelltAmEpochMs,
                    autoEndEpochMs = last.erstelltAmEpochMs,
                    startOverrides = startOverrides
                )
                val time = if (startHm == endHm) startHm else "$startHm–$endHm"
                val nums = sorted.mapNotNull { fotoNum[it.id] }
                TxtLine(
                    firma = key.firma,
                    ort = key.ort,
                    text = "$time ${key.text}${bildSuffix(nums)}".trim(),
                    sortMs = first.erstelltAmEpochMs
                )
            }
            .sortedWith(compareBy<TxtLine> { it.sortMs }.thenBy { it.firma.orEmpty() }.thenBy { it.ort.orEmpty() })
    }

    private fun renderGroupedSection(title: String, lines: List<TxtLine>, emptyText: String): String {
        if (lines.isEmpty()) return "$title\n- $emptyText\n\n"
        val grouped = lines.groupBy { it.firma }
        val firms = grouped.keys.filterNotNull().sortedBy { f -> grouped[f]?.minOfOrNull { it.sortMs } ?: 0L }
        val ordered = buildList<String?> {
            if (grouped[null].orEmpty().isNotEmpty()) add(null)
            addAll(firms)
        }
        return buildString {
            appendLine(title)
            ordered.forEach { firm ->
                val list = grouped[firm].orEmpty()
                if (list.isEmpty()) return@forEach
                appendLine(firm ?: "Allgemein")
                list.groupBy { it.ort }.forEach { (ort, items) ->
                    if (!ort.isNullOrBlank()) appendLine("  $ort")
                    items.forEach { appendLine("  - ${it.text}") }
                }
                appendLine()
            }
        }
    }

    private fun btbTxt(
        entry: BautagebuchEintrag,
        btbPlan: BtbFotoPlan,
        labels: List<String>,
        knotenLabels: Map<Long, String>,
        textOverrides: Map<Long, String>,
        startOverrides: Map<Long, String>
    ): String {
        val datum = runCatching { LocalDate.parse(entry.datumIso, isoFmt).format(deDateFmt) }.getOrDefault(entry.datumIso)
        val knownFirms = entry.firmen.map { it.name.trim() }.filter { it.isNotBlank() }.toSet()
        val btbFotos = btbPlan.selectedFotosOrdered
        val fotoNum = btbPlan.fotoNum
        return buildString {
            appendLine("BAUTAGEBUCH")
            appendLine("Baustelle: ${entry.baustelleName}")
            appendLine("Datum: $datum")
            appendLine("Zeit: ${entry.startUhrzeit.orEmpty()}-${entry.endeUhrzeit.orEmpty()}")
            appendLine()

            appendLine("WETTER")
            val slots = entry.weather?.slots?.takeIf { it.isNotEmpty() } ?: entry.weatherSlots
            if (slots.isEmpty()) {
                appendLine(entry.wetter?.takeIf { it.isNotBlank() } ?: "Keine Wetterdaten")
            } else {
                slots.forEach { s ->
                    val temp = s.tempC?.let { "$it°C" } ?: ""
                    val bew = s.bewoelkung.orEmpty()
                    val nied = s.niederschlag.orEmpty()
                    appendLine("- ${s.uhrzeit} $temp $bew $nied".trim())
                }
            }
            appendLine()

            appendLine("PERSONAL")
            if (entry.personal.isNotEmpty()) entry.personal.forEach { appendLine("- $it") } else appendLine("- Keine Einträge")
            appendLine()

            appendLine("MASCHINEN / GERÄTE")
            if (entry.geraete.isNotEmpty()) entry.geraete.forEach { appendLine("- $it") } else appendLine("- Keine Einträge")
            appendLine()

            append(renderGroupedSection(
                title = "4. AUSGEFÜHRTE ARBEITEN",
                lines = manualLinesFromRaw(entry.notizen, knownFirms) + fotoLinesForType(btbFotos, Bildtyp.FOTODOKUMENTATION, fotoNum, knotenLabels, textOverrides, startOverrides),
                emptyText = "Keine Arbeiten eingetragen"
            ))
            append(renderGroupedSection(
                title = "5. ABNAHMEN",
                lines = manualLinesFromBlock(entry.abnahmen, knownFirms) + fotoLinesForType(btbFotos, Bildtyp.ABNAHMEN, fotoNum, knotenLabels, textOverrides, startOverrides),
                emptyText = "Keine Abnahmen durchgeführt"
            ))
            append(renderGroupedSection(
                title = "6. EINWEISUNG/UNTERWEISUNG",
                lines = manualLinesFromBlock(entry.einweisungUnterweisung, knownFirms) + fotoLinesForType(btbFotos, Bildtyp.EINWEISUNG_UNTERWEISUNG, fotoNum, knotenLabels, textOverrides, startOverrides),
                emptyText = "Keine Einweisung/Unterweisung durchgeführt"
            ))
            append(renderGroupedSection(
                title = "7. BESONDERHEITEN",
                lines = manualLinesFromBlock(entry.besonderheiten, knownFirms) + fotoLinesForType(btbFotos, Bildtyp.BESONDERHEITEN, fotoNum, knotenLabels, textOverrides, startOverrides),
                emptyText = "Keine Besonderheiten"
            ))
            append(renderGroupedSection(
                title = "8. MÄNGEL/SCHÄDEN",
                lines = manualLinesFromBlock(entry.maengelSchaeden, knownFirms) + fotoLinesForType(btbFotos, Bildtyp.MAENGEL_SCHAEDEN, fotoNum, knotenLabels, textOverrides, startOverrides),
                emptyText = "Keine Mängel/Schäden gemeldet"
            ))
        }
    }

    private fun fotoMetaTextBlock(
        entry: BautagebuchEintrag,
        f: FotoEntity,
        labels: List<String>,
        knotenLabels: Map<Long, String>,
        btbPlan: BtbFotoPlan
    ): String {
        val btbNr = btbPlan.fotoNum[f.id]
        val abschnitt = btbPlan.abschnittById[f.id].orEmpty()
        return buildString {
            appendLine("TBHelfer Foto")
            appendLine("Baustelle: ${entry.baustelleName}")
            val ort = unterbaustelle(f, knotenLabels)
            if (ort.isNotBlank()) appendLine("Unterbaustelle: $ort")
            appendLine("Datum/Zeit: ${fotoZeit(f)}")
            appendLine("BTB-Datum: ${entry.datumIso}")
            appendLine("BTB-Zeitfenster: ${entry.startUhrzeit.orEmpty()}-${entry.endeUhrzeit.orEmpty()}")
            if (btbNr != null) {
                appendLine("BTB-BildNr: $btbNr")
                if (abschnitt.isNotBlank()) appendLine("PDF-Abschnitt: $abschnitt")
            }
            appendLine("GPS: ${f.gpsLat}, ${f.gpsLon}")
            val kmText = kmCsv(f.km)
            if (kmText.isNotBlank()) appendLine("KM: $kmText")
            appendLine("Firma: ${f.firma}")
            appendLine("Bildtyp: ${f.bildtyp}")
            appendLine("Kurzbeschreibung: ${f.kurztext}")
            if (!f.notiz.isNullOrBlank()) appendLine("Langbeschreibung: ${f.notiz}")
            if (!f.objektKennung.isNullOrBlank()) appendLine("Objektkennung: ${f.objektKennung}")
            if (!f.objektBereich.isNullOrBlank()) appendLine("Bereich: ${f.objektBereich}")
            optionalLines(f, labels).forEach { appendLine(it) }
        }.trim()
    }

    private fun windowsTitle(entry: BautagebuchEintrag, f: FotoEntity, knotenLabels: Map<Long, String>): String {
        return listOf("TBHelfer", entry.baustelleName, unterbaustelle(f, knotenLabels), kmCsv(f.km), f.kurztext)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(240)
    }

    private fun windowsSubject(f: FotoEntity, knotenLabels: Map<Long, String>): String {
        return listOf(f.firma, unterbaustelle(f, knotenLabels), kmCsv(f.km), f.bildtyp.toString(), f.kurztext)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .take(240)
    }

    private fun windowsKeywords(
        entry: BautagebuchEintrag,
        f: FotoEntity,
        knotenLabels: Map<Long, String>,
        btbPlan: BtbFotoPlan
    ): String {
        val btbNr = btbPlan.fotoNum[f.id]
        return listOfNotNull(
            "TB-Helfer",
            entry.baustelleName,
            unterbaustelle(f, knotenLabels).takeIf { it.isNotBlank() },
            kmCsv(f.km).takeIf { it.isNotBlank() },
            f.firma.takeIf { it.isNotBlank() },
            f.bildtyp.toString(),
            f.kurztext.takeIf { it.isNotBlank() },
            btbNr?.let { "BTB Bild $it" },
            btbPlan.abschnittById[f.id]?.takeIf { it.isNotBlank() }
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
            .take(1000)
    }

    private fun bilderListeText(
        entry: BautagebuchEintrag,
        exportFotos: List<FotoExportInfo>,
        labels: List<String>,
        knotenLabels: Map<Long, String>,
        btbPlan: BtbFotoPlan,
        gesamt: Boolean = false
    ): String {
        return buildString {
            appendLine(if (gesamt) "BILDERLISTE GESAMT" else "BILDERLISTE SCHICHT")
            appendLine("Baustelle: ${entry.baustelleName}")
            if (gesamt) {
                appendLine("Umfang: alle Bilder dieser Baustelle")
                appendLine("Anzahl Bilder: ${exportFotos.size}")
            } else {
                appendLine("Datum: ${entry.datumIso}")
                appendLine("Zeitfenster: ${entry.startUhrzeit.orEmpty()}-${entry.endeUhrzeit.orEmpty()}")
                appendLine("Anzahl Bilder im Export-Zeitfenster: ${exportFotos.size}")
                appendLine("Davon im BTB/PDF: ${btbPlan.selectedFotosOrdered.size}")
            }
            appendLine()

            exportFotos.forEachIndexed { index, info ->
                val f = info.foto
                val btbNr = btbPlan.fotoNum[f.id]
                appendLine("Export-Bild ${index + 1}")
                appendLine("BTB/PDF-BildNr: ${btbNr?.toString() ?: "-"}")
                appendLine("Im BTB/PDF: ${if (btbNr != null) "ja" else "nein"}")
                if (btbNr != null) appendLine("PDF-Abschnitt: ${btbPlan.abschnittById[f.id].orEmpty()}")
                appendLine("Datei: ${info.exportName}")
                appendLine("Zeit: ${fotoZeit(f)}")
                appendLine("Baustelle: ${entry.baustelleName}")
                val ort = unterbaustelle(f, knotenLabels)
                if (ort.isNotBlank()) appendLine("Unterbaustelle: $ort")
                appendLine("GPS: ${f.gpsLat}, ${f.gpsLon}")
                appendLine("KM: ${kmCsv(f.km)}")
                appendLine("Firma: ${f.firma}")
                appendLine("Bildtyp: ${f.bildtyp}")
                appendLine("Kurzbeschreibung: ${f.kurztext}")
                if (!f.notiz.isNullOrBlank()) appendLine("Langbeschreibung: ${f.notiz}")
                if (!f.objektKennung.isNullOrBlank()) appendLine("Objektkennung: ${f.objektKennung}")
                if (!f.objektBereich.isNullOrBlank()) appendLine("Bereich: ${f.objektBereich}")
                optionalLines(f, labels).forEach { appendLine(it) }
                appendLine()
            }
        }
    }

    private fun csv(value: Any?): String {
        val s = value?.toString().orEmpty()
        return "\"" + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
    }

    /** Excel interpretiert Werte wie 3.090 sonst gerne als Datum ("03. Sep"). */
    private fun kmCsv(km: Double?): String {
        return km?.let { "KM " + de.tbhelfer.util.KmAnzeige.wert(it) }.orEmpty()
    }

    private fun bilderListeCsv(
        entry: BautagebuchEintrag,
        exportFotos: List<FotoExportInfo>,
        labels: List<String>,
        knotenLabels: Map<Long, String>,
        btbPlan: BtbFotoPlan,
        gesamt: Boolean = false
    ): String {
        val l1 = labels.getOrNull(0)?.ifBlank { "Info1" } ?: "Info1"
        val l2 = labels.getOrNull(1)?.ifBlank { "Info2" } ?: "Info2"
        val l3 = labels.getOrNull(2)?.ifBlank { "Info3" } ?: "Info3"
        val gvpLabel = "GVP_Mast"
        val apKorLabel = "AP_KOR"
        val zeitfenster = "${entry.startUhrzeit.orEmpty()}-${entry.endeUhrzeit.orEmpty()}"
        return buildString {
            appendLine(listOf(
                "ExportNr", "BTB_BildNr", "Im_BTB", "PDF_Abschnitt",
                "Datei", "BTB_Datum", "BTB_Zeitfenster", "Zeit", "Baustelle", "Unterbaustelle",
                "GPS_Lat", "GPS_Lon", "KM", "Firma", "Bildtyp", "Kurzbeschreibung",
                "Langbeschreibung", "Objektkennung", "Bereich", gvpLabel, apKorLabel, l1, l2, l3
            ).joinToString(";") { csv(it) })
            exportFotos.forEachIndexed { index, info ->
                val f = info.foto
                val btbNr = btbPlan.fotoNum[f.id]
                appendLine(listOf(
                    index + 1,
                    btbNr ?: "",
                    if (btbNr != null) "ja" else "nein",
                    btbPlan.abschnittById[f.id].orEmpty(),
                    info.exportName,
                    if (gesamt) fotoDatumIso(f) else entry.datumIso,
                    if (gesamt) "" else zeitfenster,
                    fotoZeit(f),
                    entry.baustelleName,
                    unterbaustelle(f, knotenLabels),
                    f.gpsLat,
                    f.gpsLon,
                    kmCsv(f.km),
                    f.firma,
                    f.bildtyp,
                    f.kurztext,
                    f.notiz.orEmpty(),
                    f.objektKennung.orEmpty(),
                    f.objektBereich.orEmpty(),
                    f.info1Wert.orEmpty(),
                    f.info2Wert.orEmpty(),
                    f.info3Wert.orEmpty(),
                    f.info4Wert.orEmpty(),
                    f.info5Wert.orEmpty()
                ).joinToString(";") { csv(it) })
            }
        }
    }

    private fun bilderListeXlsxRows(
        entry: BautagebuchEintrag,
        exportFotos: List<FotoExportInfo>,
        knotenLabels: Map<Long, String>,
        relativePathFor: (FotoExportInfo) -> String,
        verantwortlicherTb: String = "",
        btbPlan: BtbFotoPlan? = null
    ): List<GesamtBilderlisteXlsx.Row> {
        return exportFotos.mapIndexed { index, info ->
            val f = info.foto
            val z = Instant.ofEpochMilli(f.erstelltAmEpochMs).atZone(ZoneId.systemDefault())
            val ort = unterbaustelle(f, knotenLabels)
            val bereich = listOf(ort, f.objektBereich.orEmpty())
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
            val btbNr = btbPlan?.fotoNum?.get(f.id)
            GesamtBilderlisteXlsx.Row(
                nr = index + 1,
                datum = z.toLocalDate().format(isoFmt),
                uhrzeit = z.toLocalTime().format(timeFmt),
                ersteller = "",
                verantwortlicherTb = verantwortlicherTb,
                art = f.bildtyp.name,
                baustelle = entry.baustelleName,
                bereich = bereich,
                km = kmCsv(f.km),
                firma = f.firma,
                kurzbeschreibung = f.kurztext,
                langtext = f.notiz.orEmpty(),
                status = if (btbNr != null) "Im BTB/PDF · Bild $btbNr" else "",
                bilddatei = info.exportName,
                gps = if (f.gpsLat != 0.0 || f.gpsLon != 0.0) {
                    "%.6f, %.6f".format(Locale.US, f.gpsLat, f.gpsLon)
                } else {
                    ""
                },
                relativePath = relativePathFor(info)
            )
        }
    }

    private fun bildtypKurz(typ: Bildtyp): String = when (typ) {
        Bildtyp.FOTODOKUMENTATION -> "FOTO"
        Bildtyp.ABNAHMEN -> "ABNAHME"
        Bildtyp.EINWEISUNG_UNTERWEISUNG -> "EINWEISUNG"
        Bildtyp.BESONDERHEITEN -> "BESONDERHEIT"
        Bildtyp.MAENGEL_SCHAEDEN -> "MANGEL"
    }

    private fun exportFotoName(
        foto: FotoEntity,
        index: Int,
        knotenLabels: Map<Long, String>,
        baustelleName: String
    ): String {
        val src = File(foto.dateipfad)
        val ext = src.extension.ifBlank { "jpg" }.lowercase(Locale.ROOT)
        val nr = (index + 1).toString().padStart(3, '0')

        // 200056: kurz genug für Windows-ZIP-Pfade, aber weiterhin direkt suchbar.
        // Baustelle/Ort/Firma stehen bereits in Ordner, Bilderliste und JPEG-Metadaten.
        val zeit = fotoZeitDatei(foto).replace("-", "")
        val km = safeFilePart(kmCsv(foto.km).replace(" ", ""), "", 10).takeIf { it.isNotBlank() }
        val typ = bildtypKurz(foto.bildtyp)
        val kurz = safeFilePart(foto.kurztext, "Foto", 30)

        val parts = listOfNotNull("B$nr", zeit, km, typ, kurz)
        return safeFileNameKeepingExtension(parts.joinToString("_") + ".$ext", maxLen = 80)
    }

    suspend fun erstellen(
        context: Context,
        entry: BautagebuchEintrag,
        pdfFile: File,
        alleFotosDerBaustelle: List<FotoEntity>
    ): Ergebnis {
        val labels = FotoOptionalFelderSpeicher.laden(context, entry.baustelleId).asList().take(3)
        val allNodeIds = alleFotosDerBaustelle.mapNotNull { it.baustellenKnotenId }.distinct()
        val knotenLabels = linkedMapOf<Long, String>()
        for (id in allNodeIds) {
            knotenLabels[id] = runCatching { knotenAnzeigeName(ServiceLocator.baustellenKnotenDao.laden(id)) }.getOrDefault("")
        }

        val (from, to) = shiftWindowEpochMs(entry)
        val fotos = alleFotosDerBaustelle
            .filter { it.baustelleId == entry.baustelleId && it.erstelltAmEpochMs in from until to }
            .sortedBy { it.erstelltAmEpochMs }

        val exportFotos = fotos.mapIndexed { index, foto ->
            FotoExportInfo(foto, exportFotoName(foto, index, knotenLabels, entry.baustelleName))
        }
        val gesamtFotos = alleFotosDerBaustelle
            .filter { it.baustelleId == entry.baustelleId }
            .sortedBy { it.erstelltAmEpochMs }
            .mapIndexed { index, foto -> FotoExportInfo(foto, exportFotoName(foto, index, knotenLabels, entry.baustelleName)) }

        // PDF/BTB-Liste ist separat vom Export:
        // - ExportFotos = alle Bilder im BTB-Zeitfenster
        // - btbPlan = nur gewählte PDF-/BTB-Bilder mit PDF-Bildnummern
        val btbFotos = BtbFotoBtbFilter.fotosFuerBtb(
            context = context,
            entry = entry,
            alleFotos = alleFotosDerBaustelle,
            legacyFromEpochMs = from,
            legacyToEpochMs = to
        ).sortedBy { it.erstelltAmEpochMs }
        val btbPlan = buildBtbFotoPlan(context, entry, btbFotos)
        val textOverrides = BtbPhotoTextOverrideStore.loadOverrides(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        )
        val startOverrides = BtbPhotoStartOverrideStore.loadOverrides(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        )
        val kmOverrides = BtbPhotoKmOverrideStore.loadOverrides(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        )
        val safeBaustelle = safeSegment(entry.baustelleName)
        val datum = entry.datumIso.ifBlank { LocalDate.now().format(isoFmt) }
        val exportBase = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val exportRoot = File(exportBase, "TB-Helfer-Export")
        exportRoot.mkdirs()
        val zipFile = File(exportRoot, "TB-Helfer_${safeBaustelle}_${datum}_${System.currentTimeMillis()}.zip")

        val root = safeBaustelle
        val btbFolder = "$root/BTB/$datum"
        val bilderFolder = "$root/Bilder/$datum"
        val maengellisteFolder = "$root/Maengelliste"
        val used = linkedSetOf<String>()
        var fotosOk = 0
        var fotosFehlen = 0

        ZipOutputStream(zipFile.outputStream().buffered(64 * 1024)).use { zos ->
            if (pdfFile.exists()) {
                // V7: Im Export-BTB-Ordner heißt das offizielle Dokument einheitlich BTB_<Datum>.pdf.
                addFile(zos, pdfFile, uniquePath("$btbFolder/BTB_$datum.pdf", used))
            }

            val druckModell = BtbDruckModellBuilder.build(
                context = context,
                entry = entry,
                selectedFotosOrdered = btbPlan.selectedFotosOrdered,
                fotoNum = btbPlan.fotoNum,
                textOverrides = textOverrides,
                startOverrides = startOverrides,
                kmOverrides = kmOverrides
            )
            val dokumentIdentitaet = BtbDokumentIdentitaet.aufloesen(context, entry)
            val btbTxtContent = btbTextFromModell(druckModell, dokumentIdentitaet)

            // Patch37-Mängelbasis: ein Mangel kann mehrere Fotos und Fotoarten besitzen.
            // Schichtliste liegt im BTB-Ordner, die vollständige Liste im eigenen Ordner.
            val maengelGesamt = MaengellisteExport.ladeItems(context, entry.baustelleId, entry.baustelleName)
            val maengelSchicht = maengelGesamt.filter { item ->
                item.fotos.any { it.foto.erstelltAmEpochMs in from until to } || item.datumIso == entry.datumIso
            }
            val maengelSchichtCsv = MaengellisteExport.csvText(context, maengelSchicht)
            val maengelGesamtCsv = MaengellisteExport.csvText(context, maengelGesamt)
            val maengelSchichtPdf = MaengellisteExport.pdfBytes(context, entry.baustelleName, maengelSchicht, "Schicht")
            val maengelGesamtPdf = MaengellisteExport.pdfBytes(context, entry.baustelleName, maengelGesamt, "Gesamt")

            val abnahmenGesamt = AbnahmeSpeicher.alle(context, entry.baustelleId)
            val schichtDatum = runCatching { LocalDate.parse(datum, isoFmt) }.getOrDefault(LocalDate.now())
            val abnahmenSchicht = abnahmenGesamt.filter { abnahmeDatumTreffer(it, schichtDatum) }
            val abnahmenSchichtPdf = AbnahmeExport.listenPdfBytes(context, entry.baustelleName, abnahmenSchicht, "Schicht")
            val abnahmenGesamtPdf = AbnahmeExport.listenPdfBytes(context, entry.baustelleName, abnahmenGesamt, "Gesamt")
            val abnahmenSchichtCsv = AbnahmeExport.csvText(abnahmenSchicht)
            val abnahmenGesamtCsv = AbnahmeExport.csvText(abnahmenGesamt)
            val strukturierteAbnahmeFotoIds = abnahmenSchicht.flatMap { it.fotos }.map { it.fotoId }.toSet()
            val strukturierteAbnahmeFotoIdsGesamt = abnahmenGesamt.flatMap { it.fotos }.map { it.fotoId }.toSet()
            val strukturierteAbnahmeFotos = ServiceLocator.fotoRepository
                .fotosMitIds(strukturierteAbnahmeFotoIds.toList())
                .associateBy { it.id }
            // Strukturierte Abnahmefotos liegen ausschließlich im Abnahmeordner, nicht doppelt unter Bilder/.
            val bilderlisteSchichtCsv = bilderListeCsv(
                entry, exportFotos.filterNot { it.foto.id in strukturierteAbnahmeFotoIds }, labels, knotenLabels, btbPlan
            )
            val bilderlisteGesamtCsv = bilderListeCsv(
                entry, gesamtFotos.filterNot { it.foto.id in strukturierteAbnahmeFotoIdsGesamt }, labels, knotenLabels, btbPlan, gesamt = true
            )
            val schichtExportFotos = exportFotos.filterNot { it.foto.id in strukturierteAbnahmeFotoIds }
            val gesamtExportFotos = gesamtFotos.filterNot { it.foto.id in strukturierteAbnahmeFotoIdsGesamt }
            val verantwortlicherTb = dokumentIdentitaet.verantwortlicherTb
            val bilderlisteSchichtXlsx = GesamtBilderlisteXlsx.bytes(
                bilderListeXlsxRows(
                    entry = entry,
                    exportFotos = schichtExportFotos,
                    knotenLabels = knotenLabels,
                    relativePathFor = { info -> "../../Bilder/$datum/${info.exportName}" },
                    verantwortlicherTb = verantwortlicherTb,
                    btbPlan = btbPlan
                ),
                sheetName = "Bilder Schicht"
            )
            val bilderlisteGesamtXlsx = GesamtBilderlisteXlsx.bytes(
                bilderListeXlsxRows(
                    entry = entry,
                    exportFotos = gesamtExportFotos,
                    knotenLabels = knotenLabels,
                    relativePathFor = { info -> "Bilder/${fotoDatumIso(info.foto)}/${info.exportName}" },
                    verantwortlicherTb = verantwortlicherTb,
                    btbPlan = null
                ),
                sheetName = "Bilder gesamt"
            )
            val schichtBilderlisteName = runCatching {
                LocalDate.parse(datum, isoFmt).format(DateTimeFormatter.ofPattern("dd.MM.yy"))
            }.getOrDefault(datum) + "_Bilderliste.xlsx"

            // V7: Keine TXT-Dateien mehr im Export. BTB gibt es als PDF + bearbeitbares DOCX.
            addBinary(zos, uniquePath("$btbFolder/BTB_$datum.docx", used), btbDocxBytes(btbTxtContent))
            addCsvText(zos, uniquePath("$btbFolder/Bilderliste_Schicht.csv", used), bilderlisteSchichtCsv)
            addBinary(zos, uniquePath("$btbFolder/$schichtBilderlisteName", used), bilderlisteSchichtXlsx)
            addBinary(zos, uniquePath("$root/Bilderliste_gesamt.xlsx", used), bilderlisteGesamtXlsx)
            addBinary(zos, uniquePath("$btbFolder/Maengelliste_Schicht.pdf", used), maengelSchichtPdf)
            addCsvText(zos, uniquePath("$btbFolder/Maengelliste_Schicht.csv", used), maengelSchichtCsv)
            // Gesamt = alle Bilder und Mängel der Baustelle.
            addCsvText(zos, uniquePath("$root/Bilderliste_gesamt.csv", used), bilderlisteGesamtCsv)
            addBinary(zos, uniquePath("$maengellisteFolder/Maengelliste_Gesamt.pdf", used), maengelGesamtPdf)
            addCsvText(zos, uniquePath("$maengellisteFolder/Maengelliste_Gesamt.csv", used), maengelGesamtCsv)
            addBinary(zos, uniquePath("$btbFolder/Abnahmeliste_Schicht.pdf", used), abnahmenSchichtPdf)
            addCsvText(zos, uniquePath("$btbFolder/Abnahmeliste_Schicht.csv", used), abnahmenSchichtCsv)
            addBinary(zos, uniquePath("$root/Abnahmeliste/Abnahmeliste_Gesamt.pdf", used), abnahmenGesamtPdf)
            addCsvText(zos, uniquePath("$root/Abnahmeliste/Abnahmeliste_Gesamt.csv", used), abnahmenGesamtCsv)

            abnahmenSchicht.forEach { a ->
                val folderName = AbnahmeSpeicher.abnahmeOrdner(context, a).name
                val base = "$root/Abnahmen/${a.startDatumIso}/$folderName"
                val einzelPdf = AbnahmeExport.erzeugeEinzelPdf(context, a)
                if (einzelPdf.exists()) addFile(zos, einzelPdf, uniquePath("$base/${einzelPdf.name}", used))
                val json = AbnahmeSpeicher.jsonDatei(context, a)
                if (json.exists()) addFile(zos, json, uniquePath("$base/${json.name}", used))

                a.anlagen.forEachIndexed { index, anlage ->
                    val prefix = (index + 1).toString().padStart(2, '0')
                    val original = File(anlage.originalPfad)
                    if (original.exists()) {
                        val name = "${prefix}_Original_${safeSegment(anlage.bezeichnung.ifBlank { original.nameWithoutExtension })}.${original.extension.ifBlank { if (anlage.typ == AbnahmeSpeicher.AnlageTyp.PDF) "pdf" else "jpg" }}"
                        if (anlage.typ == AbnahmeSpeicher.AnlageTyp.PDF) addFile(zos, original, uniquePath("$base/Objekt/$name", used))
                        else addBildDateiKomprimiert(context, zos, original, uniquePath("$base/Objekt/$name", used))
                    }
                    anlage.ausschnittPfad?.let { path ->
                        val crop = File(path)
                        if (crop.exists()) addBildDateiKomprimiert(context, zos, crop, uniquePath("$base/Objekt/${prefix}_Ausschnitt_${safeSegment(anlage.bezeichnung.ifBlank { "Plan" })}.jpg", used))
                    }
                }

                a.fotos.forEachIndexed { index, ref ->
                    val foto = strukturierteAbnahmeFotos[ref.fotoId]
                    if (foto == null || !File(foto.dateipfad).exists()) {
                        fotosFehlen++
                    } else {
                        val ext = File(foto.dateipfad).extension.ifBlank { "jpg" }
                        val name = "Bild_${(index + 1).toString().padStart(2, '0')}_${safeSegment(AbnahmeSpeicher.bildArtLabel(ref.bildArt))}.$ext"
                        addFotoFileMitSuchMetadaten(
                            context, zos, File(foto.dateipfad), uniquePath("$base/Fotos/$name", used),
                            entry, foto, labels, knotenLabels, btbPlan
                        )
                        fotosOk++
                    }
                }
            }

            exportFotos.forEach { info ->
                if (info.foto.id in strukturierteAbnahmeFotoIds) return@forEach
                val foto = info.foto
                val src = File(foto.dateipfad)
                if (!src.exists()) {
                    fotosFehlen++
                    return@forEach
                }
                addFotoFileMitSuchMetadaten(
                    context = context,
                    zos = zos,
                    src = src,
                    path = uniquePath("$bilderFolder/${info.exportName}", used),
                    entry = entry,
                    foto = foto,
                    labels = labels,
                    knotenLabels = knotenLabels,
                    btbPlan = btbPlan
                )
                fotosOk++
            }
        }

        return Ergebnis(zipFile = zipFile, fotosExportiert = fotosOk, fehlendeFotos = fotosFehlen)
    }

    suspend fun erstellenBaustellenExport(
        context: Context,
        baustelleId: Long,
        baustelleName: String,
        vonDatum: LocalDate? = null,
        bisDatum: LocalDate? = null
    ): Ergebnis {
        val alleFotosDerBaustelle = ServiceLocator.fotoRepository
            .fotosFuerBaustelleOnce(baustelleId)
            .filter { it.baustelleId == baustelleId }
            .sortedBy { it.erstelltAmEpochMs }

        fun inRangeDatum(d: LocalDate): Boolean {
            if (vonDatum != null && d.isBefore(vonDatum)) return false
            if (bisDatum != null && d.isAfter(bisDatum)) return false
            return true
        }

        val fotos = alleFotosDerBaustelle.filter { f ->
            val d = Instant.ofEpochMilli(f.erstelltAmEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
            inRangeDatum(d)
        }
        val verlauf = BautagebuchSpeicher.verlaufFuerBaustelle(context, baustelleId, baustelleName)
            .filter { inRangeDatum(it.datum) }
            .sortedWith(compareBy<BautagebuchSpeicher.VerlaufEintrag> { it.datum }.thenBy { it.schicht.name })

        val labels = FotoOptionalFelderSpeicher.laden(context, baustelleId).asList().take(3)
        val allNodeIds = alleFotosDerBaustelle.mapNotNull { it.baustellenKnotenId }.distinct()
        val knotenLabels = linkedMapOf<Long, String>()
        for (id in allNodeIds) {
            knotenLabels[id] = runCatching { knotenAnzeigeName(ServiceLocator.baustellenKnotenDao.laden(id)) }.getOrDefault("")
        }

        val safeBaustelle = safeSegment(baustelleName)
        val rangeName = when {
            vonDatum != null && bisDatum != null -> "${vonDatum.format(isoFmt)}_bis_${bisDatum.format(isoFmt)}"
            vonDatum != null -> "ab_${vonDatum.format(isoFmt)}"
            bisDatum != null -> "bis_${bisDatum.format(isoFmt)}"
            else -> "Gesamt"
        }
        val exportBase = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val exportRoot = File(exportBase, "TB-Helfer-Export")
        exportRoot.mkdirs()
        val zipFile = File(exportRoot, "TB-Helfer_${safeBaustelle}_${rangeName}_${System.currentTimeMillis()}.zip")
        val root = safeBaustelle
        val used = linkedSetOf<String>()
        var fotosOk = 0
        var fotosFehlen = 0
        val exportFotos = fotos.mapIndexed { index, foto ->
            FotoExportInfo(foto, exportFotoName(foto, index, knotenLabels, baustelleName))
        }

        fun einfacheBilderlisteCsv(ausgeschlosseneFotoIds: Set<Long>): String = buildString {
            appendLine("Datum;Zeit;Baustelle;Unterbaustelle;Bereich;KM;Firma;Bildtyp;Kurzbeschreibung;Bilddatei;GPS")
            fotos.filterNot { it.id in ausgeschlosseneFotoIds }.forEach { f ->
                val z = Instant.ofEpochMilli(f.erstelltAmEpochMs).atZone(ZoneId.systemDefault())
                val row = listOf(
                    z.toLocalDate().format(isoFmt),
                    z.toLocalTime().format(timeFmt),
                    baustelleName,
                    unterbaustelle(f, knotenLabels),
                    f.objektBereich.orEmpty(),
                    kmCsv(f.km),
                    f.firma,
                    f.bildtyp.name,
                    f.kurztext,
                    File(f.dateipfad).name,
                    "%.6f, %.6f".format(Locale.US, f.gpsLat, f.gpsLon)
                )
                appendLine(row.joinToString(";") { "\"" + it.replace("\"", "\"\"") + "\"" })
            }
        }

        ZipOutputStream(zipFile.outputStream().buffered(64 * 1024)).use { zos ->
            verlauf.forEach { v ->
                val datum = v.datum.format(isoFmt)
                // Android 200036: Verlauf kann bei modernen BTBs aus Altcode eine null-pdfDatei
                // liefern. Deshalb zusätzlich den kanonischen Abschluss-PDF-Finder verwenden.
                val btbToken = BtbDokumentVersionierung.dokumentToken(v.entry)
                val pdfForExport = v.pdfDatei?.takeIf { it.exists() }
                    ?: BtbAbschlussPdfFinder.neueste(context, v.entry)?.takeIf { it.exists() }
                pdfForExport?.let { pdf ->
                    val name = "BTB_${datum}_${v.schicht.name}_${btbToken}.pdf"
                    addFile(zos, pdf, uniquePath("$root/BTB/$datum/$name", used))
                }
                val (legacyFrom, legacyTo) = shiftWindowEpochMs(v.entry)
                val btbFotos = BtbFotoBtbFilter.fotosFuerBtb(
                    context = context,
                    entry = v.entry,
                    alleFotos = alleFotosDerBaustelle,
                    legacyFromEpochMs = legacyFrom,
                    legacyToEpochMs = legacyTo
                ).sortedBy { it.erstelltAmEpochMs }
                val btbPlan = buildBtbFotoPlan(context, v.entry, btbFotos)
                val textOverrides = BtbPhotoTextOverrideStore.loadOverrides(context, v.entry.baustelleId, v.entry.datumIso, v.entry.startUhrzeit, v.entry.endeUhrzeit, v.entry.schicht)
                val startOverrides = BtbPhotoStartOverrideStore.loadOverrides(context, v.entry.baustelleId, v.entry.datumIso, v.entry.startUhrzeit, v.entry.endeUhrzeit, v.entry.schicht)
                val kmOverrides = BtbPhotoKmOverrideStore.loadOverrides(context, v.entry.baustelleId, v.entry.datumIso, v.entry.startUhrzeit, v.entry.endeUhrzeit, v.entry.schicht)
                val druckModell = BtbDruckModellBuilder.build(
                    context = context,
                    entry = v.entry,
                    selectedFotosOrdered = btbPlan.selectedFotosOrdered,
                    fotoNum = btbPlan.fotoNum,
                    textOverrides = textOverrides,
                    startOverrides = startOverrides,
                    kmOverrides = kmOverrides
                )
                val dokumentIdentitaet = BtbDokumentIdentitaet.aufloesen(context, v.entry)
                val docxName = "BTB_${datum}_${v.schicht.name}_${btbToken}.docx"
                addBinary(
                    zos,
                    uniquePath("$root/BTB/$datum/$docxName", used),
                    btbDocxBytes(btbTextFromModell(druckModell, dokumentIdentitaet))
                )
            }

            val maengelGesamt = MaengellisteExport.ladeItems(context, baustelleId, baustelleName)
            val maengelZeitraum = maengelGesamt.filter { item ->
                item.fotos.any { mf ->
                    val d = Instant.ofEpochMilli(mf.foto.erstelltAmEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
                    inRangeDatum(d)
                } || runCatching { LocalDate.parse(item.datumIso, isoFmt) }.getOrNull()?.let { inRangeDatum(it) } == true
            }
            val abnahmenGesamt = AbnahmeSpeicher.alle(context, baustelleId)
            val abnahmenZeitraum = abnahmenGesamt.filter { a ->
                listOfNotNull(a.startEpochMs, a.geaendertAmEpochMs, a.abgeschlossenAmEpochMs)
                    .map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    .any(::inRangeDatum)
            }
            val abnahmenExport = if (vonDatum == null && bisDatum == null) abnahmenGesamt else abnahmenZeitraum
            val strukturierteAbnahmeFotoIds = abnahmenExport.flatMap { it.fotos }.map { it.fotoId }.toSet()
            val strukturierteAbnahmeFotos = ServiceLocator.fotoRepository
                .fotosMitIds(strukturierteAbnahmeFotoIds.toList())
                .associateBy { it.id }

            val bilderlisteGesamtXlsx = GesamtBilderlisteXlsx.bytes(
                bilderListeXlsxRows(
                    entry = BautagebuchEintrag(
                        baustelleName = baustelleName,
                        baustelleId = baustelleId,
                        datumIso = vonDatum?.format(isoFmt) ?: bisDatum?.format(isoFmt) ?: LocalDate.now().format(isoFmt)
                    ),
                    exportFotos = exportFotos.filterNot { it.foto.id in strukturierteAbnahmeFotoIds },
                    knotenLabels = knotenLabels,
                    relativePathFor = { info -> "Bilder/${fotoDatumIso(info.foto)}/${info.exportName}" }
                ),
                sheetName = "Bilder gesamt"
            )
            addCsvText(zos, uniquePath("$root/Bilderliste_gesamt.csv", used), einfacheBilderlisteCsv(strukturierteAbnahmeFotoIds))
            addBinary(zos, uniquePath("$root/Bilderliste_gesamt.xlsx", used), bilderlisteGesamtXlsx)
            addBinary(zos, uniquePath("$root/Maengelliste/Maengelliste_Gesamt.pdf", used), MaengellisteExport.pdfBytes(context, baustelleName, maengelGesamt, "Gesamt"))
            addCsvText(zos, uniquePath("$root/Maengelliste/Maengelliste_Gesamt.csv", used), MaengellisteExport.csvText(context, maengelGesamt))
            if (vonDatum != null || bisDatum != null) {
                addBinary(zos, uniquePath("$root/Maengelliste/Maengelliste_Zeitraum.pdf", used), MaengellisteExport.pdfBytes(context, baustelleName, maengelZeitraum, "Zeitraum"))
                addCsvText(zos, uniquePath("$root/Maengelliste/Maengelliste_Zeitraum.csv", used), MaengellisteExport.csvText(context, maengelZeitraum))
            }

            addBinary(zos, uniquePath("$root/Abnahmeliste/Abnahmeliste_Gesamt.pdf", used), AbnahmeExport.listenPdfBytes(context, baustelleName, abnahmenGesamt, "Gesamt"))
            addCsvText(zos, uniquePath("$root/Abnahmeliste/Abnahmeliste_Gesamt.csv", used), AbnahmeExport.csvText(abnahmenGesamt))
            if (vonDatum != null || bisDatum != null) {
                addBinary(zos, uniquePath("$root/Abnahmeliste/Abnahmeliste_Zeitraum.pdf", used), AbnahmeExport.listenPdfBytes(context, baustelleName, abnahmenZeitraum, "Zeitraum"))
                addCsvText(zos, uniquePath("$root/Abnahmeliste/Abnahmeliste_Zeitraum.csv", used), AbnahmeExport.csvText(abnahmenZeitraum))
            }

            abnahmenExport.forEach { a ->
                val folderName = AbnahmeSpeicher.abnahmeOrdner(context, a).name
                val base = "$root/Abnahmen/${a.startDatumIso}/$folderName"
                val einzelPdf = AbnahmeExport.erzeugeEinzelPdf(context, a)
                if (einzelPdf.exists()) addFile(zos, einzelPdf, uniquePath("$base/${einzelPdf.name}", used))
                val json = AbnahmeSpeicher.jsonDatei(context, a)
                if (json.exists()) addFile(zos, json, uniquePath("$base/${json.name}", used))
                a.anlagen.forEachIndexed { anlageIndex, anlage ->
                    val prefix = (anlageIndex + 1).toString().padStart(2, '0')
                    val original = File(anlage.originalPfad)
                    if (original.exists()) {
                        val name = "${prefix}_Original_${safeSegment(anlage.bezeichnung.ifBlank { original.nameWithoutExtension })}.${original.extension.ifBlank { if (anlage.typ == AbnahmeSpeicher.AnlageTyp.PDF) "pdf" else "jpg" }}"
                        if (anlage.typ == AbnahmeSpeicher.AnlageTyp.PDF) addFile(zos, original, uniquePath("$base/Objekt/$name", used))
                        else addBildDateiKomprimiert(context, zos, original, uniquePath("$base/Objekt/$name", used))
                    }
                    anlage.ausschnittPfad?.let { path ->
                        val crop = File(path)
                        if (crop.exists()) addBildDateiKomprimiert(context, zos, crop, uniquePath("$base/Objekt/${prefix}_Ausschnitt_${safeSegment(anlage.bezeichnung.ifBlank { "Plan" })}.jpg", used))
                    }
                }
                a.fotos.forEachIndexed { fotoIndex, ref ->
                    val foto = strukturierteAbnahmeFotos[ref.fotoId]
                    if (foto == null || !File(foto.dateipfad).exists()) {
                        fotosFehlen++
                    } else {
                        val ext = File(foto.dateipfad).extension.ifBlank { "jpg" }
                        val name = "Bild_${(fotoIndex + 1).toString().padStart(2, '0')}_${safeSegment(AbnahmeSpeicher.bildArtLabel(ref.bildArt))}.$ext"
                        addFotoFileKomprimiert(
                            context, zos, File(foto.dateipfad), uniquePath("$base/Fotos/$name", used),
                            baustelleName, foto, labels, knotenLabels
                        )
                        fotosOk++
                    }
                }
            }

            exportFotos.forEach { info ->
                val foto = info.foto
                if (foto.id in strukturierteAbnahmeFotoIds) return@forEach
                val src = File(foto.dateipfad)
                if (!src.exists()) {
                    fotosFehlen++
                    return@forEach
                }
                val d = fotoDatumIso(foto)
                addFotoFileKomprimiert(
                    context = context,
                    zos = zos,
                    src = src,
                    path = uniquePath("$root/Bilder/$d/${info.exportName}", used),
                    baustelleName = baustelleName,
                    foto = foto,
                    labels = labels,
                    knotenLabels = knotenLabels
                )
                fotosOk++
            }
        }

        return Ergebnis(zipFile = zipFile, fotosExportiert = fotosOk, fehlendeFotos = fotosFehlen)
    }

    fun shareZip(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".dateianbieter", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportpaket teilen"))
    }
}
