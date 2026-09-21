package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertTrue
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
