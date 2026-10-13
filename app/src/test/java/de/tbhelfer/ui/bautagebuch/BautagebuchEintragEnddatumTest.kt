package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BautagebuchEintragEnddatumTest {

    @Test
    fun `nacht ueber mitternacht bekommt folgetag`() {
        val entry = BautagebuchEintrag(
            baustelleName = "Test",
            baustelleId = 1L,
            datumIso = "2026-09-10",
            schicht = SchichtTyp.NACHT,
            startUhrzeit = "22:00",
            endeUhrzeit = "06:00"
        )

        assertEquals("2026-09-11", entry.endeDatumIso)
    }

    @Test
    fun `normaler tag braucht kein separates enddatum`() {
        val entry = BautagebuchEintrag(
            baustelleName = "Test",
            baustelleId = 1L,
            datumIso = "2026-09-10",
            startUhrzeit = "07:00",
            endeUhrzeit = "15:00"
        )

        assertNull(entry.endeDatumIso)
    }

    @Test
    fun `explizites enddatum bleibt erhalten`() {
        val entry = BautagebuchEintrag(
            baustelleName = "Test",
            baustelleId = 1L,
            datumIso = "2026-09-10",
            endeDatumIso = "2026-09-12",
            startUhrzeit = "22:00",
            endeUhrzeit = "06:00"
        )

        assertEquals("2026-09-12", entry.endeDatumIso)
    }
}
