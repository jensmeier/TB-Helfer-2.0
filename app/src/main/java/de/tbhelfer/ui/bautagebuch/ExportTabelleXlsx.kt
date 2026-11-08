package de.tbhelfer.ui.bautagebuch

import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

/** Offline OOXML tables. Text is always literal (never a formula); file links are relative. */
object ExportTabelleXlsx {
    enum class Format { TEXT, NUMBER, DATE, TIME, DATETIME, KM }
    data class Column(val title: String, val width: Int = 22, val format: Format = Format.TEXT)
    data class Cell(val text: String = "", val target: String? = null)
    data class Sheet(val name: String, val columns: List<Column>, val rows: List<List<Cell>>)

    fun bytes(sheets: List<Sheet>): ByteArray {
        require(sheets.isNotEmpty())
        require(sheets.map { it.name }.distinct().size == sheets.size)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>""")
            put("_rels/.rels", relationships(listOf(Triple("rId1", "officeDocument", "xl/workbook.xml"))))
            put("xl/_rels/workbook.xml.rels", relationships(sheets.indices.map { Triple("rId${it+1}", "worksheet", "worksheets/sheet${it+1}.xml") } + Triple("styles", "styles", "styles.xml")))
            val names = sheets.map { it.name.replace(Regex("[\\[\\]:*?/\\\\]"), "_").take(31) }
            require(names.distinct().size == names.size)
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$NS" xmlns:r="$REL"><sheets>${names.mapIndexed { i, name -> "<sheet name=\"${xml(name)}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>" }.joinToString("")}</sheets><definedNames>${names.mapIndexed { i, name -> "<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"$i\">'${xml(name.replace("'", "''"))}'!\$1:\$1</definedName>" }.joinToString("")}</definedNames></workbook>""")
            put("xl/styles.xml", styles())
            sheets.forEachIndexed { i, sheet ->
                require(sheet.columns.isNotEmpty() && sheet.rows.all { it.size == sheet.columns.size })
                val links = mutableListOf<Triple<String, String, String>>()
                val hyperlinkXml = StringBuilder()
                val data = buildString {
                    append("<row r=\"1\" ht=\"32\" customHeight=\"1\">")
                    sheet.columns.forEachIndexed { c, col -> append(cell(column(c+1)+"1", col.title, 1)) }
                    append("</row>")
                    sheet.rows.forEachIndexed { rowIndex, row ->
                        val r = rowIndex + 2
                        val lines = row.mapIndexed { c, value -> value.text.split('\n').sumOf { ceil(it.length.toDouble() / (sheet.columns[c].width - 2).coerceAtLeast(1)).toInt().coerceAtLeast(1) } }.maxOrNull() ?: 1
                        val height = (lines * 14 + 8).coerceIn(28, 409)
                        append("<row r=\"$r\" ht=\"$height\" customHeight=\"1\">")
                        row.forEachIndexed { c, value ->
                            val ref = column(c+1)+r
                            val alt = rowIndex % 2
                            val number = number(value.text, sheet.columns[c].format)
                            val target = value.target?.takeIf { it.isNotBlank() }
                            val style = if (target != null) 4 + alt else if (number != null) when(sheet.columns[c].format) {
                                Format.DATE -> 6 + alt
                                Format.TIME -> 8 + alt
                                Format.DATETIME -> 10 + alt
                                Format.KM -> 12 + alt
                                else -> 2 + alt
                            } else 2 + alt
                            if (number == null) append(cell(ref, value.text, style))
                            else append("<c r=\"$ref\" s=\"$style\"><v>$number</v></c>")
                            if (target != null) {
                                val id = "rId${links.size+1}"
                                links += Triple(id, "hyperlink", linkTarget(target))
                                hyperlinkXml.append("<hyperlink ref=\"$ref\" r:id=\"$id\"/>")
                            }
                        }
                        append("</row>")
                    }
                }
                val range = "A1:${column(sheet.columns.size)}${sheet.rows.size+1}"
                put("xl/worksheets/sheet${i+1}.xml", """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$NS" xmlns:r="$REL"><sheetPr><pageSetUpPr fitToPage="1"/></sheetPr><dimension ref="$range"/><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="28"/><cols>${sheet.columns.mapIndexed { c, col -> "<col min=\"${c+1}\" max=\"${c+1}\" width=\"${col.width}\" customWidth=\"1\"/>" }.joinToString("")}</cols><sheetData>$data</sheetData><autoFilter ref="$range"/>${if(links.isEmpty()) "" else "<hyperlinks>$hyperlinkXml</hyperlinks>"}<pageMargins left="0.25" right="0.25" top="0.5" bottom="0.5" header="0.2" footer="0.2"/><pageSetup paperSize="9" orientation="landscape" fitToWidth="1" fitToHeight="0"/></worksheet>""")
                if (links.isNotEmpty()) put("xl/worksheets/_rels/sheet${i+1}.xml.rels", relationships(links, true))
            }
        }
        return out.toByteArray()
    }

    internal fun linkTarget(target: String): String {
        if (target.startsWith("https://")) return target
        val path = target.replace('\\', '/')
        require(!path.startsWith('/') && !path.contains(':')) { "Bildpfad muss relativ zum Exportordner sein." }
        return URI(null, null, path, null).toString()
    }

    private fun number(text: String, format: Format): String? = runCatching {
        val t = text.trim()
        when (format) {
            Format.TEXT -> null
            Format.NUMBER, Format.KM -> t.toDoubleOrNull()?.takeIf { it.isFinite() }?.toString()
            Format.DATE -> serial(LocalDate.parse(t)).toString()
            Format.TIME -> (LocalTime.parse(t).toSecondOfDay() / 86400.0).toString()
            Format.DATETIME -> LocalDateTime.parse(t).let { (serial(it.toLocalDate()) + it.toLocalTime().toSecondOfDay() / 86400.0).toString() }
        }
    }.getOrNull()
    private fun serial(date: LocalDate): Long {
        require(!date.isBefore(LocalDate.of(1900, 1, 1)))
        return ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 31), date) + if (date >= LocalDate.of(1900, 3, 1)) 1 else 0
    }
    private fun cell(ref: String, value: String, style: Int) = "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"
    private fun column(index: Int): String {
        var n = index; val result = StringBuilder()
        while(n > 0) { n--; result.append(('A'.code+n%26).toChar()); n /= 26 }
        return result.reverse().toString()
    }
    private fun relationships(items: List<Triple<String, String, String>>, external: Boolean = false) = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${items.joinToString("") { (id, type, path) -> "<Relationship Id=\"$id\" Type=\"$REL/$type\" Target=\"${xml(path)}\"${if(external) " TargetMode=\"External\"" else ""}/>" }}</Relationships>"""
    private fun styles(): String {
        fun xf(font: Int, fill: Int, num: Int = 0) = "<xf numFmtId=\"$num\" fontId=\"$font\" fillId=\"$fill\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>"
        val xfs = xf(0,0) + xf(1,2) + xf(0,0) + xf(0,3) + xf(2,0) + xf(2,3) + listOf(164,165,166,167).joinToString("") { xf(0,0,it)+xf(0,3,it) }
        return """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$NS"><numFmts count="4"><numFmt numFmtId="164" formatCode="dd.mm.yyyy"/><numFmt numFmtId="165" formatCode="hh:mm"/><numFmt numFmtId="166" formatCode="dd.mm.yyyy hh:mm"/><numFmt numFmtId="167" formatCode="0.000"/></numFmts><fonts count="3"><font><sz val="10"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="10"/><name val="Calibri"/></font><font><u/><color rgb="FF0563C1"/><sz val="10"/><name val="Calibri"/></font></fonts><fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1F4E78"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEAF2F8"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="14">$xfs</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
    }
    private fun xml(value: String): String = value.filter { it == '\n' || it == '\r' || it == '\t' || it >= ' ' }.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
}
