package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Test

class BtbDokumentVersionierungTest {

    private val token = "11111111-1111-1111"

    @Test
    fun `neues Modell kennt nur ein aktuelles Abschlussdokument`() {
        assertEquals(
            1,
            BtbDokumentVersionierung.naechsteVersionAusDateinamen(emptyList(), token)
        )
    }

    @Test
    fun `historische V Dateien erzeugen keine neue sichtbare Version mehr`() {
        val result = BtbDokumentVersionierung.naechsteVersionAusDateinamen(
            listOf(
                "BTB_2026-06-05_Jens_${token}_V1.pdf",
                "BTB_2026-06-05_Jens_${token}_V99.pdf"
            ),
            token
        )
        assertEquals(1, result)
    }

    @Test
    fun `alte Modellversion wird im neuen Modell ignoriert`() {
        assertEquals(
            1,
            BtbDokumentVersionierung.naechsteVersionAusDateinamen(emptyList(), token, modellVersion = 42)
        )
    }
}
