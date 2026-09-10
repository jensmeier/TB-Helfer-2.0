package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BtbDokumentIdentitaetTest {

    @Test
    fun `kanonischer Dateiname enthaelt Datum Schicht und verantwortlichen TB`() {
        val id = BtbDokumentIdentitaet.Identitaet(
            firmenname = "GTU",
            baustelleName = "Immenstadt",
            verantwortlicherTb = "Jens Zinkel",
            verantwortlicherToken = BtbDokumentIdentitaet.safeToken("Jens Zinkel"),
            datumIso = "2026-06-05",
            schicht = SchichtTyp.TAG,
            schichtAnzeige = "Tag",
            schichtToken = "TAG",
            worksiteServerId = "worksite-1",
            groupId = "group-1"
        )

        assertEquals("BTB_2026-06-05_TAG_Jens_Zinkel.pdf", id.pdfDateiname())
        assertEquals("BTB_2026-06-05_TAG_Jens_Zinkel.docx", id.docxDateiname())
    }

    @Test
    fun `Tag und Nacht sind verschiedene fachliche Dokumente`() {
        val tag = BtbDokumentIdentitaet.Identitaet(
            firmenname = null,
            baustelleName = "Baustelle",
            verantwortlicherTb = "Max Müller",
            verantwortlicherToken = BtbDokumentIdentitaet.safeToken("Max Müller"),
            datumIso = "2026-06-05",
            schicht = SchichtTyp.TAG,
            schichtAnzeige = "Tag",
            schichtToken = "TAG",
            worksiteServerId = "worksite-1",
            groupId = "group-7"
        )
        val nacht = tag.copy(schicht = SchichtTyp.NACHT, schichtAnzeige = "Nacht", schichtToken = "NACHT")

        assertNotEquals(tag.pdfDateiname(), nacht.pdfDateiname())
        assertEquals("BTB_2026-06-05_NACHT_Max_Müller.pdf", nacht.pdfDateiname())
    }

    @Test
    fun `gleichnamige sichtbare Ordinale bleiben im Dateinamen erhalten`() {
        val one = BtbDokumentIdentitaet.safeToken("Max Mustermann 1")
        val two = BtbDokumentIdentitaet.safeToken("Max Mustermann 2")

        assertEquals("Max_Mustermann_1", one)
        assertEquals("Max_Mustermann_2", two)
        assertNotEquals(one, two)
    }
}
