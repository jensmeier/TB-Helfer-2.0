package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class Export1108BTest {
    private fun contents(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while(e != null) { result[e.name] = z.readBytes().toString(Charsets.UTF_8); e = z.nextEntry }
        }
        return result
    }
    private fun row() = GesamtBilderlisteXlsx.Row(1, "2026-11-04", "08:27", "TB", "TB", "Arbeit", "Berchtesgaden", "Bahnhof MBG – W 99W1", "KM 0,000", "Firma", "=kein Excel-Befehl", "Langer Text ".repeat(40), "Im BTB/PDF · Bild 3", "Bild #1+%.jpg", "47.6, 13.0", "Bilder/Änderung #1+%.jpg")

    @Test fun `date and time are numeric text remains literal and rows grow`() {
        val bytes = GesamtBilderlisteXlsx.bytes(listOf(row()))
        val files = contents(bytes)
        files.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.values.forEach { xml ->
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        }
        val sheet = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<c r=\"B2\" s=\"6\"><v>"))
        assertTrue(sheet.contains("<c r=\"C2\" s=\"8\"><v>"))
        assertTrue(sheet.contains("=kein Excel-Befehl"))
        assertFalse(sheet.contains("<f>"))
        assertFalse(sheet.contains("<row r=\"2\" ht=\"22\""))
        assertTrue(sheet.contains("Bild #1+%.jpg"))
        assertTrue(files.getValue("xl/worksheets/_rels/sheet1.xml.rels").contains("https://www.google.com/maps"))
        File("build/export-qa").mkdirs()
        File("build/export-qa/Fotoliste_Test.xlsx").writeBytes(bytes)
    }

    @Test fun `archive links survive moving whole folder including special characters and second worksheet`() {
        val photo = "Bilder/Änderung #1+%.jpg"
        val list = ExportTabelleXlsx.bytes(listOf(
            ExportTabelleXlsx.Sheet("Vorgänge", listOf(ExportTabelleXlsx.Column("ID")), listOf(listOf(ExportTabelleXlsx.Cell("m1")))),
            ExportTabelleXlsx.Sheet("Fotos", listOf(ExportTabelleXlsx.Column("Bilddatei")), listOf(listOf(ExportTabelleXlsx.Cell("Bild öffnen", "../$photo"))))
        ))
        val root = Files.createTempDirectory("export-links").toFile()
        try {
            val original = File(root, "Vorher.zip")
            ZipOutputStream(original.outputStream()).use { z ->
                for((path, data) in listOf("Baustelle/Foto.xlsx" to GesamtBilderlisteXlsx.bytes(listOf(row())), "Baustelle/Maengelliste/Liste.xlsx" to list, "Baustelle/$photo" to byteArrayOf(1))) {
                    z.putNextEntry(ZipEntry(path)); z.write(data); z.closeEntry()
                }
            }
            assertEquals(2, ExportArchivPruefung.pruefen(original))
            val moved = File(root, "Anderer Ordner ü/Kopie.zip"); moved.parentFile?.mkdirs(); original.copyTo(moved)
            assertEquals(2, ExportArchivPruefung.pruefen(moved))
            // Extract and resolve against a renamed parent as well, without using the old location.
            val extracted = File(root, "USB Kopie ü/Baustelle"); File(extracted, photo).parentFile?.mkdirs(); File(extracted, photo).writeBytes(byteArrayOf(1))
            assertTrue(File(extracted, java.net.URLDecoder.decode(ExportTabelleXlsx.linkTarget(photo).replace("+", "%2B"), "UTF-8")).isFile)
        } finally { root.deleteRecursively() }
    }

    @Test fun `empty sheets missing links and invalid dates remain explicit`() {
        val files = contents(GesamtBilderlisteXlsx.bytes(listOf(row().copy(datum="unbekannt", uhrzeit="", relativePath="", gps="-"))))
        assertFalse(files.containsKey("xl/worksheets/_rels/sheet1.xml.rels"))
        assertTrue(files.getValue("xl/worksheets/sheet1.xml").contains("Nicht im Export"))
        assertTrue(files.getValue("xl/worksheets/sheet1.xml").contains("unbekannt"))
        assertTrue(contents(GesamtBilderlisteXlsx.bytes(emptyList())).getValue("xl/worksheets/sheet1.xml").contains("A1:J1"))
    }

    @Test fun `PDF short name never hides station and switch`() {
        assertEquals("Bahnhof MBG – W 99W1", PdfOrtsLabel.basis("BAHNHOF", "MBG", "MBG", "W 99W1", null))
        assertEquals("Bahnhof MBG – W 99W4", PdfOrtsLabel.basis("BAHNHOF", "MBG", "MBG", "W 99W4", null))
        assertEquals("Bahnhof MBG – W 1 – W 10", PdfOrtsLabel.basis("BAHNHOF", "W 10", "MBG", "W 1", null))
        assertEquals("Hauptbaustelle", PdfOrtsLabel.basis("HAUPT", null, null, null, null))
        assertEquals("Baulos – Strecke 5740".replace(" – ", " · "), PdfOrtsLabel.basis("FREIE_STRECKE", "Baulos", "alt", "alt", "5740"))
    }

    @Test fun `acceptance is one record with separate photos and missing target stays explicit`() {
        val a = de.tbhelfer.ui.abnahme.AbnahmeSpeicher.Abnahme(id="a1", nummer=7, baustelleId=1,
            baustelleName="Test", unterKnotenName="Bahnhof MBG – W 99W1", kmVon=0.0,
            fotos=listOf(de.tbhelfer.ui.abnahme.AbnahmeSpeicher.FotoRef(1), de.tbhelfer.ui.abnahme.AbnahmeSpeicher.FotoRef(2)))
        val data = FachlistenXlsx.abnahmen(listOf(a)) { _, id -> if(id==1L) "../Abnahmen/Bild.jpg" else null }
        val xml = contents(data)
        assertTrue(xml.getValue("xl/worksheets/sheet1.xml").contains("A1:T2"))
        assertTrue(xml.getValue("xl/worksheets/sheet2.xml").contains("A1:E3"))
        assertTrue(xml.getValue("xl/worksheets/sheet2.xml").contains("Nicht im Export"))
        assertTrue(xml.getValue("xl/worksheets/sheet1.xml").contains("<c r=\"I2\" s=\"12\"><v>0.0</v>"))
        File("build/export-qa").mkdirs()
        File("build/export-qa/Abnahmeliste_Test.xlsx").writeBytes(data)
    }
}
