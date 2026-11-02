package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class GesamtBilderlisteXlsxTest {

    @Test
    fun `xlsx enthaelt filter eingefrorene kopfzeile und bildlink`() {
        val bytes = GesamtBilderlisteXlsx.bytes(
            listOf(
                GesamtBilderlisteXlsx.Row(
                    nr = 1,
                    datum = "2026-09-10",
                    uhrzeit = "12:34",
                    ersteller = "Jens Zinkel",
                    verantwortlicherTb = "Jens Zinkel",
                    art = "Mangel – erledigt",
                    baustelle = "Pasing",
                    bereich = "Gleis 2",
                    km = "KM 10+123,000",
                    firma = "GTU",
                    kurzbeschreibung = "Beispiel kurz",
                    langtext = "Beispiel ausführlicher BTB-Text",
                    status = "Erledigt",
                    bilddatei = "MANGEL_abc123.jpg",
                    gps = "48.123456, 11.123456",
                    relativePath = "Mängel/2026-09-10/Mangel_abc/Fotos/MANGEL_abc123.jpg"
                )
            )
        )

        val entries = unzipText(bytes)
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        val rels = entries.getValue("xl/worksheets/_rels/sheet1.xml.rels")

        assertTrue(sheet.contains("<autoFilter ref=\"A1:O2\"/>"))
        assertTrue(sheet.contains("state=\"frozen\""))
        assertTrue(sheet.contains("<hyperlink ref=\"N2\" r:id=\"rId1\"/>"))
        assertTrue(sheet.contains("Verantwortlicher TB"))
        assertTrue(sheet.contains("Jens Zinkel"))
        assertTrue(sheet.contains("Kurzbeschreibung"))
        assertTrue(sheet.contains("Langtext / BTB-Text"))
        assertTrue(sheet.contains("Beispiel ausführlicher BTB-Text"))
        assertTrue(rels.contains("Mängel/2026-09-10/Mangel_abc/Fotos/MANGEL_abc123.jpg"))
    }

    @Test
    fun `schicht xlsx behaelt relativen pfad zu bilderordner`() {
        val bytes = GesamtBilderlisteXlsx.bytes(
            listOf(
                GesamtBilderlisteXlsx.Row(
                    nr = 1,
                    datum = "2026-09-21",
                    uhrzeit = "17:17",
                    ersteller = "",
                    verantwortlicherTb = "pixeljens",
                    art = "FOTODOKUMENTATION",
                    baustelle = "GE Immenstadt - Kempten",
                    bereich = "",
                    km = "KM 74,977",
                    firma = "Schwerbau",
                    kurzbeschreibung = "BRM",
                    langtext = "",
                    status = "Im BTB/PDF · Bild 1",
                    bilddatei = "B001_20260921_1717_KM74,977_FOTO_BRM.jpg",
                    gps = "47.000000, 10.000000",
                    relativePath = "../../Bilder/2026-09-21/B001_20260921_1717_KM74,977_FOTO_BRM.jpg"
                )
            ),
            sheetName = "Bilder Schicht"
        )

        val entries = unzipText(bytes)
        val rels = entries.getValue("xl/worksheets/_rels/sheet1.xml.rels")
        assertTrue(rels.contains("../../Bilder/2026-09-21/B001_20260921_1717_KM74,977_FOTO_BRM.jpg"))
    }

    @Test
    fun `sync kopie einmal aber verschiedene bilder mit gleichem text bleiben erhalten`() {
        val original = GesamtBilderlisteXlsx.Row(
            nr = 7, datum = "2026-11-02", uhrzeit = "08:00", ersteller = "TB",
            verantwortlicherTb = "TB", art = "FOTO", baustelle = "Test", bereich = "Gleis 1",
            km = "", firma = "Firma", kurzbeschreibung = "Schwellenwechsel", langtext = "",
            status = "", bilddatei = "Bild.jpg", gps = "", relativePath = "../../Bilder/a.jpg"
        )
        val rows = GesamtBilderlisteXlsx.eindeutigNummeriert(listOf(
            original, original.copy(nr = 8), original.copy(relativePath = "../../Bilder/b.jpg"),
            original.copy(relativePath = ""), original.copy(relativePath = "")
        ))
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.nr })
        assertEquals(listOf("../../Bilder/a.jpg", "../../Bilder/b.jpg", "", ""), rows.map { it.relativePath })
        val entries = unzipText(GesamtBilderlisteXlsx.bytes(rows.take(2)))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("A1:O3"))
        val rels = entries.getValue("xl/worksheets/_rels/sheet1.xml.rels")
        assertEquals(1, Regex("Bilder/a.jpg").findAll(rels).count())
        assertEquals(1, Regex("Bilder/b.jpg").findAll(rels).count())
    }

    private fun unzipText(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return result
    }
}

