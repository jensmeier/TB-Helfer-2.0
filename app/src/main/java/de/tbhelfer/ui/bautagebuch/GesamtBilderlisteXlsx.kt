package de.tbhelfer.ui.bautagebuch

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Kleine XLSX-Erzeugung ohne Apache POI.
 *
 * Die Datei ist fuer den Langzeitordner auf dem Laptop gedacht: Filter-/Sortierpfeile,
 * eingefrorene Kopfzeile, lesbare Spaltenbreiten, Zebra-Zeilen und anklickbare relative
 * Bildpfade. CSV bleibt parallel als universelle Roh-/Sicherungsliste erhalten.
 */
object GesamtBilderlisteXlsx {

    data class Row(
        val nr: Int,
        val datum: String,
        val uhrzeit: String,
        val ersteller: String,
        val verantwortlicherTb: String,
        val art: String,
        val baustelle: String,
        val bereich: String,
        val km: String,
        val firma: String,
        val kurzbeschreibung: String,
        val langtext: String,
        val status: String,
        val bilddatei: String,
        val gps: String,
        /** Relativer Pfad ausgehend von Bilderliste_gesamt.xlsx im Baustellenordner. */
        val relativePath: String
    )

    /** Original und Sync-Kopie mit demselben Exportziel nur einmal auffuehren. */
    internal fun eindeutigNummeriert(rows: List<Row>): List<Row> {
        val seen = hashSetOf<String>()
        return rows.filter { it.relativePath.isBlank() || seen.add(it.relativePath) }
            .mapIndexed { index, row -> row.copy(nr = index + 1) }
    }

    private val headers = listOf(
        "Nr.", "Datum", "Uhrzeit", "Ersteller", "Verantwortlicher TB", "Art",
        "Baustelle", "Bereich/Gleis", "KM", "Firma", "Kurzbeschreibung", "Langtext / BTB-Text",
        "Status", "Bilddatei", "GPS"
    )

    fun csv(rows: List<Row>): String = buildString {
        appendLine(headers.joinToString(";") { csvCell(it) })
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.nr.toString(), row.datum, row.uhrzeit, row.ersteller,
                    row.verantwortlicherTb, row.art, row.baustelle, row.bereich, row.km,
                    row.firma, row.kurzbeschreibung, row.langtext, row.status, row.bilddatei, row.gps
                ).joinToString(";") { csvCell(it) }
            )
        }
    }

    fun bytes(rows: List<Row>, sheetName: String = "Bilder gesamt"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes())
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook(sheetName))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            put(zip, "xl/styles.xml", styles())

            val links = rows.mapIndexedNotNull { index, row ->
                row.relativePath.takeIf { it.isNotBlank() }?.let { path ->
                    Link(rowNumber = index + 2, id = "rId${index + 1}", target = path.replace('\\', '/'))
                }
            }
            put(zip, "xl/worksheets/sheet1.xml", sheet(rows, links))
            if (links.isNotEmpty()) {
                put(zip, "xl/worksheets/_rels/sheet1.xml.rels", sheetRels(links))
            }
        }
        return out.toByteArray()
    }

    private data class Link(val rowNumber: Int, val id: String, val target: String)

    private fun sheet(rows: List<Row>, links: List<Link>): String {
        val lastRow = (rows.size + 1).coerceAtLeast(1)
        val relationNamespace = if (links.isEmpty()) "" else " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
        val data = buildString {
            append("<row r=\"1\" ht=\"28\" customHeight=\"1\">")
            headers.forEachIndexed { index, value -> append(inlineCell(index + 1, 1, value, 1)) }
            append("</row>")
            rows.forEachIndexed { index, row ->
                val r = index + 2
                val alt = index % 2 == 1
                val style = if (alt) 3 else 2
                val linkStyle = if (alt) 5 else 4
                val values = listOf(
                    row.nr.toString(), row.datum, row.uhrzeit, row.ersteller,
                    row.verantwortlicherTb, row.art, row.baustelle, row.bereich, row.km,
                    row.firma, row.kurzbeschreibung, row.langtext, row.status, row.bilddatei, row.gps
                )
                append("<row r=\"").append(r).append("\" ht=\"22\" customHeight=\"1\">")
                values.forEachIndexed { column, value ->
                    append(inlineCell(column + 1, r, value, if (column == 13 && row.relativePath.isNotBlank()) linkStyle else style))
                }
                append("</row>")
            }
        }

        val hyperlinkXml = if (links.isEmpty()) "" else buildString {
            append("<hyperlinks>")
            links.forEach { link ->
                append("<hyperlink ref=\"N").append(link.rowNumber).append("\" r:id=\"")
                    .append(link.id).append("\"/>")
            }
            append("</hyperlinks>")
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"$relationNamespace>
  <dimension ref="A1:O$lastRow"/>
  <sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
  <sheetFormatPr defaultRowHeight="20"/>
  <cols>
    <col min="1" max="1" width="7" customWidth="1"/>
    <col min="2" max="3" width="12" customWidth="1"/>
    <col min="4" max="5" width="23" customWidth="1"/>
    <col min="6" max="6" width="22" customWidth="1"/>
    <col min="7" max="7" width="24" customWidth="1"/>
    <col min="8" max="8" width="25" customWidth="1"/>
    <col min="9" max="9" width="14" customWidth="1"/>
    <col min="10" max="10" width="20" customWidth="1"/>
    <col min="11" max="11" width="32" customWidth="1"/>
    <col min="12" max="12" width="48" customWidth="1"/>
    <col min="13" max="13" width="20" customWidth="1"/>
    <col min="14" max="14" width="48" customWidth="1"/>
    <col min="15" max="15" width="24" customWidth="1"/>
  </cols>
  <sheetData>$data</sheetData>
  <autoFilter ref="A1:O$lastRow"/>
  $hyperlinkXml
  <pageMargins left="0.25" right="0.25" top="0.5" bottom="0.5" header="0.2" footer="0.2"/>
</worksheet>""".trimIndent()
    }

    private fun sheetRels(links: List<Link>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        links.forEach { link ->
            append("<Relationship Id=\"").append(link.id)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\"")
                .append(" Target=\"").append(xml(link.target)).append("\" TargetMode=\"External\"/>")
        }
        append("</Relationships>")
    }

    private fun inlineCell(column: Int, row: Int, value: String, style: Int): String {
        val ref = columnName(column) + row
        return "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"
    }

    private fun columnName(index: Int): String {
        var n = index
        val result = StringBuilder()
        while (n > 0) {
            n--
            result.append(('A'.code + (n % 26)).toChar())
            n /= 26
        }
        return result.reverse().toString()
    }

    private fun contentTypes(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".trimIndent()

    private fun rootRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent()

    private fun workbook(sheetName: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <bookViews><workbookView xWindow="0" yWindow="0" windowWidth="24000" windowHeight="14000"/></bookViews>
  <sheets><sheet name="${xml(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets>
</workbook>""".trimIndent()

    private fun workbookRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trimIndent()

    private fun styles(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="10"/><name val="Aptos"/><family val="2"/></font>
    <font><b/><color rgb="FFFFFFFF"/><sz val="10"/><name val="Aptos Display"/><family val="2"/></font>
    <font><u/><color rgb="FF0563C1"/><sz val="10"/><name val="Aptos"/><family val="2"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1F4E78"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFEAF2F8"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border><left style="thin"><color rgb="FFD9E2F3"/></left><right style="thin"><color rgb="FFD9E2F3"/></right><top style="thin"><color rgb="FFD9E2F3"/></top><bottom style="thin"><color rgb="FFD9E2F3"/></bottom><diagonal/></border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="6">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""".trimIndent()

    private fun put(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
