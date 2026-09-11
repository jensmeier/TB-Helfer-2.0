package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Test

class BtbDokumentVersionierungTest {

    private val token = "11111111-1111-1111"

    @Test
    fun `ohne Abschluss beginnt die Folge mit V1`() {
        val next = BtbDokumentVersionierung.naechsteVersionAusDateinamen(
            dateinamen = emptyList(),
            token = token
        )
        assertEquals(1, next)
    }

    @Test
    fun `V1 und V2 fuehren zu V3`() {
        val next = BtbDokumentVersionierung.naechsteVersionAusDateinamen(
            dateinamen = listOf(
                "BTB_2026-06-05_07_00-12_00_Jens_Zinkel_${token}_V1.pdf",
                "BTB_2026-06-05_07_00-12_00_Jens_Zinkel_${token}_V2.pdf"
            ),
            token = token
        )
        assertEquals(3, next)
    }

    @Test
    fun `Entwurf zaehlt nicht als Dokumentversion`() {
        val next = BtbDokumentVersionierung.naechsteVersionAusDateinamen(
            dateinamen = listOf(
                "Immenstadt_entwurf_2026_06_05.pdf",
                "BTB_2026-06-05_07_00-12_00_Jens_Zinkel_${token}_V1.pdf"
            ),
            token = token
        )
        assertEquals(2, next)
    }

    @Test
    fun `anderer BTB am selben Tag beeinflusst Version nicht`() {
        val next = BtbDokumentVersionierung.naechsteVersionAusDateinamen(
            dateinamen = listOf(
                "BTB_2026-06-05_07_00-12_00_Jens_Zinkel_22222222-2222-2222_V7.pdf",
                "BTB_2026-06-05_18_00-22_00_Jens_Zinkel_${token}_V1.pdf"
            ),
            token = token
        )
        assertEquals(2, next)
    }

    @Test
    fun `persistierte Version bleibt Untergrenze`() {
        val next = BtbDokumentVersionierung.naechsteVersionAusDateinamen(
            dateinamen = emptyList(),
            token = token,
            modellVersion = 4
        )
        assertEquals(5, next)
    }
}
