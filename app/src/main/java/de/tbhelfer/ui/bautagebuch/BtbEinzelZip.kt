package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.os.Environment
import de.tbhelfer.datenbank.FotoEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Einzel-Export genau einer btbId. Keine TAG/NACHT-/"(2)"-Dateinamen mehr. */
object BtbEinzelZip {

    suspend fun erstellen(
        context: Context,
        entry: BautagebuchEintrag,
        pdf: File,
        alleFotos: List<FotoEntity>
    ): File {
        require(!entry.btbId.isNullOrBlank()) { "BTB-ID fehlt" }
        require(pdf.exists()) { "Abschluss-PDF fehlt" }

        val identity = BtbDokumentIdentitaet.aufloesen(context, entry)
        val rootName = safe(entry.baustelleName)
        val btbFolder = "TB-Helfer/$rootName/BTB/${entry.datumIso}/${identity.btbToken}"
        val (from, to) = timeWindow(entry)
        val fotos = BtbFotoBtbFilter.fotosFuerBtb(context, entry, alleFotos, from, to)
            .sortedBy { it.erstelltAmEpochMs }

        val exportBase = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val outDir = File(exportBase, "TB-Helfer-Export").apply { mkdirs() }
        val zip = File(outDir, "BTB_${identity.btbToken}_${System.currentTimeMillis()}.zip")

        ZipOutputStream(zip.outputStream().buffered(64 * 1024)).use { zos ->
            addFile(zos, pdf, "$btbFolder/${identity.pdfDateiname()}")
            addBytes(zos, "$btbFolder/${identity.docxDateiname()}", simpleDocx(entry, identity))
            addText(zos, "$btbFolder/Bilderliste.csv", bilderCsv(entry, fotos))
            fotos.forEachIndexed { index, foto ->
                val src = File(foto.dateipfad)
                if (!src.exists()) return@forEachIndexed
                val ext = src.extension.ifBlank { "jpg" }
                val name = "Bild_${(index + 1).toString().padStart(3, '0')}_FotoID_${foto.id}.$ext"
                addFile(zos, src, "$btbFolder/Bilder/$name")
            }
        }
        return zip
    }

    private fun timeWindow(entry: BautagebuchEintrag): Pair<Long, Long> {
        val date = runCatching { LocalDate.parse(entry.datumIso) }.getOrDefault(LocalDate.now())
        val start = parseTime(entry.startUhrzeit) ?: LocalTime.MIN
        val end = parseTime(entry.endeUhrzeit) ?: LocalTime.MAX
        val endDate = entry.endeDatumIso
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: if (!end.isAfter(start)) date.plusDays(1) else date
        val zone = ZoneId.systemDefault()
        return date.atTime(start).atZone(zone).toInstant().toEpochMilli() to
            endDate.atTime(end).atZone(zone).toInstant().toEpochMilli()
    }

    private fun parseTime(raw: String?): LocalTime? = raw?.trim()?.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
    }

    private fun bilderCsv(entry: BautagebuchEintrag, fotos: List<FotoEntity>): String = buildString {
        appendLine("FotoID;BTB_ID;Zeit;Firma;Bildtyp;Kurzbeschreibung;Datei")
        fotos.forEach { f ->
            appendLine(
                listOf(
                    f.id.toString(),
                    entry.btbId.orEmpty(),
                    f.erstelltAmEpochMs.toString(),
                    csv(f.firma),
                    csv(f.bildtyp.name),
                    csv(f.kurztext),
                    csv(File(f.dateipfad).name)
                ).joinToString(";")
            )
        }
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ")}\""

    private fun simpleDocx(entry: BautagebuchEintrag, identity: BtbDokumentIdentitaet.Identitaet): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun paragraph(text: String, bold: Boolean = false): String {
            val rpr = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
            return "<w:p><w:r>$rpr<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"
        }
        val lines = buildList {
            add("BAUTAGEBUCH")
            add("Baustelle: ${entry.baustelleName}")
            identity.firmenname?.takeIf { it.isNotBlank() }?.let { add("Firma: $it") }
            add("Verantwortlicher TB: ${identity.verantwortlicherTb}")
            add("Schicht: ${identity.schichtAnzeige}")
            add("BTB-ID: ${entry.btbId}")
            add("Zeitraum: ${identity.zeitraumAnzeige}")
            add("")
            add("4. AUSGEFÜHRTE ARBEITEN")
            entry.notizen.orEmpty().lines().filter { it.isNotBlank() }.forEach(::add)
            add("")
            fun addSection(title: String, block: SectionBlock) {
                add(title)
                if (block.items.isEmpty()) add("-") else block.items.forEach { item ->
                    add(listOfNotNull(item.zeitAnzeige, item.text).joinToString(" "))
                }
                add("")
            }
            addSection("5. ABNAHMEN", entry.abnahmen)
            addSection("6. EINWEISUNG/UNTERWEISUNG", entry.einweisungUnterweisung)
            addSection("7. BESONDERHEITEN", entry.besonderheiten)
            addSection("8. MÄNGEL/SCHÄDEN", entry.maengelSchaeden)
        }
        val body = lines.mapIndexed { index, line -> paragraph(line, index == 0 || Regex("^[4-8]\\.").containsMatchIn(line)) }.joinToString("")
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        val doc = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr></w:body></w:document>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            addText(zos, "[Content_Types].xml", contentTypes)
            addText(zos, "_rels/.rels", rels)
            addText(zos, "word/document.xml", doc)
        }
        return out.toByteArray()
    }

    private fun addFile(zos: ZipOutputStream, file: File, path: String) {
        zos.putNextEntry(ZipEntry(path))
        file.inputStream().buffered(64 * 1024).use { it.copyTo(zos, 64 * 1024) }
        zos.closeEntry()
    }

    private fun addBytes(zos: ZipOutputStream, path: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun addText(zos: ZipOutputStream, path: String, text: String) = addBytes(zos, path, text.toByteArray(Charsets.UTF_8))

    private fun safe(input: String): String = input.trim()
        .replace("..", ".")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(80)
        .ifBlank { "Baustelle" }
}

