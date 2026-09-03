package de.tbhelfer.vermessung

import org.junit.Assert.assertEquals
import org.junit.Test

class VermessungspunktKategorieTest {

    @Test
    fun ps4UndGvpv_sindGleisvermarkung() {
        assertEquals(VermessungspunktKategorie.GVP, VermessungspunktKategorie.grafikArt("PS4", "punkte.kf"))
        assertEquals(VermessungspunktKategorie.GVP, VermessungspunktKategorie.grafikArt("GVPV", "punkte.kf"))
    }

    @Test
    fun ps0BisPs3_sindFestpunkte() {
        listOf("PS0", "PS1", "PS2", "PS3").forEach { typ ->
            assertEquals(VermessungspunktKategorie.FESTPUNKT, VermessungspunktKategorie.grafikArt(typ, "punkte.kf"))
        }
    }

    @Test
    fun unbekannterRohKfPunkt_bleibtFestpunkt() {
        assertEquals(VermessungspunktKategorie.FESTPUNKT, VermessungspunktKategorie.grafikArt(null, "Pasing_DBREF.kf"))
    }

    @Test
    fun alteGvpTxtQuelleOhnePart_bleibtGvp() {
        assertEquals(VermessungspunktKategorie.GVP, VermessungspunktKategorie.grafikArt(null, "GVP_Gessertshausen.txt"))
    }
}
