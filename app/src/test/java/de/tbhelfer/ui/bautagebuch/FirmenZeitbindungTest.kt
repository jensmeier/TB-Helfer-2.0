package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirmenZeitbindungTest {
    private fun change(times: FirmenZeitbindung.Zeiten, from: String? = "08:00", to: String? = "17:00") =
        FirmenZeitbindung.berichtGeaendert(times, "07:00", "16:00", from, to)

    @Test
    fun `report folgt auch ueber mitternacht`() {
        assertEquals(FirmenZeitbindung.Zeiten("22:00", "06:00", "REPORT"),
            change(FirmenZeitbindung.Zeiten("07:00", "16:00", "REPORT"), "22:00", "06:00"))
    }

    @Test
    fun `manual bleibt auch bei zuvor identischen berichtszeiten erhalten`() {
        val times = FirmenZeitbindung.Zeiten("07:00", "16:00", "MANUAL")
        assertEquals(times, change(times))
        assertEquals(times, change(times, null, null))
    }

    @Test
    fun `legacy nur leere oder passende werte folgen`() {
        for (times in listOf(
            FirmenZeitbindung.Zeiten(null, null, null),
            FirmenZeitbindung.Zeiten("07:00", "16:00", null),
            FirmenZeitbindung.Zeiten("07:00", null, null),
            FirmenZeitbindung.Zeiten(null, "16:00", null)
        )) assertEquals(FirmenZeitbindung.Zeiten("08:00", "17:00", "REPORT"), change(times))

        for (times in listOf(
            FirmenZeitbindung.Zeiten("07:15", "16:00", null),
            FirmenZeitbindung.Zeiten(null, "15:00", null)
        )) assertEquals(times, change(times))
    }

    @Test
    fun `entfernte berichtszeiten loeschen nur automatische firmenzeiten`() {
        assertEquals(FirmenZeitbindung.Zeiten(null, null, "REPORT"),
            change(FirmenZeitbindung.Zeiten("07:00", "16:00", "REPORT"), null, null))
    }

    @Test
    fun `keine aenderung liefert kein neues legacy flag`() {
        val times = FirmenZeitbindung.Zeiten("07:00", "16:00", null)
        assertEquals(times, change(times, "07:00", "16:00"))
        assertNull(change(times, "07:00", "16:00").bindung)
    }

    @Test
    fun `bewusste firmenzeitwahl bestimmt neuen modus`() {
        assertEquals("REPORT", FirmenZeitbindung.manuellGewaehlt("08:00", "17:00", "08:00", "17:00"))
        assertEquals("MANUAL", FirmenZeitbindung.manuellGewaehlt("08:15", "17:00", "08:00", "17:00"))
        assertEquals("MANUAL", FirmenZeitbindung.manuellGewaehlt(null, null, "08:00", "17:00"))
    }

    @Test
    fun `personal und maschinen unabhaengig und identitaet unveraendert`() {
        val source = FirmaBlock(
            firmaId = "firma-server-id", name = "Firma", personalVon = "07:00", personalBis = "16:00",
            personalZeitbindung = "MANUAL", maschinenVon = "07:00", maschinenBis = "16:00",
            maschinenZeitbindung = "REPORT", geraete = listOf(GeraetZeile(1, "Bagger"))
        )
        val result = FirmenZeitbindung.uebernehmen(source, "07:00", "16:00", "08:00", "17:00")
        assertEquals(source.firmaId, result.firmaId)
        assertEquals(source.geraete, result.geraete)
        assertEquals("07:00", result.personalVon)
        assertEquals("MANUAL", result.personalZeitbindung)
        assertEquals("08:00", result.maschinenVon)
        assertEquals("REPORT", result.maschinenZeitbindung)
    }

    @Test
    fun `unbekannte zukuenftige bindung nicht umdeuten`() {
        val times = FirmenZeitbindung.Zeiten("07:00", "16:00", "FUTURE")
        assertEquals(times, change(times))
    }
}
