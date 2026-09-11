package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BtbDokumentIdentitaetTest {

    private fun identitaet(
        btbId: String = "btb-abc",
        btbToken: String = "btb-abc",
        start: String? = null,
        ende: String? = null,
        version: Int = 0,
        schicht: SchichtTyp = SchichtTyp.TAG,
        verantwortlicher: String = "Jens Zinkel"
    ) = BtbDokumentIdentitaet.Identitaet(
        firmenname = "GTU",
        baustelleName = "Immenstadt",
        verantwortlicherTb = verantwortlicher,
        verantwortlicherToken = BtbDokumentIdentitaet.safeToken(verantwortlicher),
        btbId = btbId,
        btbToken = btbToken,
        datumIso = "2026-06-05",
        endeDatumIso = null,
        startUhrzeit = start,
        endeUhrzeit = ende,
        dokumentVersion = version,
        schicht = schicht,
        schichtAnzeige = if (schicht == SchichtTyp.TAG) "Tag" else "Nacht",
        schichtToken = schicht.name,
        worksiteServerId = "worksite-1",
        groupId = "group-1"
    )

    @Test
    fun `kanonischer Dateiname enthaelt stabile BTB Identitaet`() {
        val id = identitaet(version = 1)

        assertEquals("BTB_2026-06-05_TAG_Jens_Zinkel_btb-abc_V1.pdf", id.pdfDateiname())
        assertEquals("BTB_2026-06-05_TAG_Jens_Zinkel_btb-abc_V1.docx", id.docxDateiname())
    }

    @Test
    fun `mehrere BTBs am selben Tag kollidieren nicht`() {
        val erster = identitaet(btbId = "11111111-1111-1111-1111-111111111111", btbToken = "11111111-1111-1111")
        val zweiter = identitaet(btbId = "22222222-2222-2222-2222-222222222222", btbToken = "22222222-2222-2222")

        assertNotEquals(erster.pdfDateiname(), zweiter.pdfDateiname())
        assertNotEquals(erster.docxDateiname(), zweiter.docxDateiname())
    }

    @Test
    fun `Arbeitszeit ist Anzeigeattribut und darf Dateinamen unterscheiden`() {
        val vormittag = identitaet(start = "07:00", ende = "12:00")
        val abend = identitaet(btbId = "btb-def", btbToken = "btb-def", start = "18:00", ende = "22:00")

        assertEquals("BTB_2026-06-05_07_00-12_00_Jens_Zinkel_btb-abc.pdf", vormittag.pdfDateiname())
        assertEquals("BTB_2026-06-05_18_00-22_00_Jens_Zinkel_btb-def.pdf", abend.pdfDateiname())
    }

    @Test
    fun `Dokumentversionen ueberschreiben sich nicht`() {
        val id = identitaet()

        assertEquals("BTB_2026-06-05_TAG_Jens_Zinkel_btb-abc_V1.pdf", id.pdfDateiname(1))
        assertEquals("BTB_2026-06-05_TAG_Jens_Zinkel_btb-abc_V2.pdf", id.pdfDateiname(2))
        assertNotEquals(id.pdfDateiname(1), id.pdfDateiname(2))
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
