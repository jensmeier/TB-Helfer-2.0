package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BtbEintragszeitTest {
    @Test
    fun `portal felder und alter zeitraum ergeben dieselbe anzeige`() {
        val modern = BtbEintragszeit.lesen("8:00", "10:15")
        for (legacy in listOf("8:00-10:15", "08:00–10:15", "8:00 - (10:15)")) {
            assertEquals(modern, BtbEintragszeit.lesen(legacy))
        }
        assertEquals("08:00–10:15", modern.anzeige)
    }

    @Test
    fun `explizites null entfernt legacy ende ohne wiederherstellung`() {
        val cleared = BtbEintragszeit.lesen("08:00–10:15", null, endeVorhanden = true)
        assertEquals("08:00", cleared.von)
        assertNull(cleared.bis)
        assertEquals("08:00", cleared.anzeige)
        assertEquals(cleared, BtbEintragszeit.lesen(cleared.von, cleared.bis, true))
    }

    @Test
    fun `separates ende hat vorrang vor legacy ende`() {
        assertEquals("08:00–11:30", BtbEintragszeit.lesen("08:00–10:15", "11:30").anzeige)
        assertEquals("08:00", BtbEintragszeit.lesen("08:00–10:15", " ").anzeige)
    }

    @Test
    fun `nacht und gleiche zeiten werden nicht umsortiert oder als 24h umgerechnet`() {
        assertEquals("22:00–06:00", BtbEintragszeit.lesen("22:00", "06:00").anzeige)
        assertEquals("08:00–08:00", BtbEintragszeit.lesen("08:00", "08:00").anzeige)
    }

    @Test
    fun `einzelzeit leere zeit und nur ende bleiben unterscheidbar`() {
        assertEquals("08:05", BtbEintragszeit.lesen("8:05").anzeige)
        assertNull(BtbEintragszeit.lesen(null).anzeige)
        assertNull(BtbEintragszeit.lesen(" ", " ").anzeige)
        val endOnly = BtbEintragszeit.lesen(null, "6:00")
        assertEquals("–06:00", endOnly.anzeige)
        assertEquals(endOnly, BtbEintragszeit.lesen(endOnly.anzeige))
    }

    @Test
    fun `zeitspanne aendert keine fachliche identitaet`() {
        val entry = SectionItem(
            clientEntryKey = "entry-1", text = "Arbeit", firmaId = "firma-1",
            worksiteNodeKey = "node-1", uhrzeit = "08:00", uhrzeitBis = "10:00"
        )
        val edited = entry.copy(uhrzeitBis = null)
        assertEquals(entry.clientEntryKey, edited.clientEntryKey)
        assertEquals(entry.worksiteNodeKey, edited.worksiteNodeKey)
        assertEquals(entry.firmaId, edited.firmaId)
        assertEquals("08:00", edited.zeitAnzeige)
        assertEquals("08:00–10:00", entry.zeitAnzeige)
    }
}
