package de.tbhelfer.ui.kamera

import de.tbhelfer.datenbank.Indikator
import org.junit.Assert.assertEquals
import org.junit.Test

class FotoKmQualitaetTest {

    @Test
    fun `achsabstand verwendet exakt die 10 und 30 meter grenzen`() {
        assertEquals(Indikator.GRUEN, FotoKmQualitaet.bewerten(10.0, 3.0).indikator)
        assertEquals(Indikator.GELB, FotoKmQualitaet.bewerten(10.01, 3.0).indikator)
        assertEquals(Indikator.GELB, FotoKmQualitaet.bewerten(30.0, 3.0).indikator)
        assertEquals(Indikator.ROT, FotoKmQualitaet.bewerten(30.01, 3.0).indikator)
    }

    @Test
    fun `schlechte gps genauigkeit verbessert den achsabstand niemals`() {
        assertEquals(Indikator.GELB, FotoKmQualitaet.bewerten(8.0, 18.0).indikator)
        assertEquals(Indikator.GELB, FotoKmQualitaet.bewerten(20.0, 18.0).indikator)
        assertEquals(Indikator.ROT, FotoKmQualitaet.bewerten(35.0, 18.0).indikator)
    }

    @Test
    fun `guter gps fix laesst die reine abstandsampel unveraendert`() {
        assertEquals(Indikator.GRUEN, FotoKmQualitaet.bewerten(8.0, 5.0).indikator)
        assertEquals(Indikator.GELB, FotoKmQualitaet.bewerten(20.0, 5.0).indikator)
        assertEquals(Indikator.ROT, FotoKmQualitaet.bewerten(35.0, 5.0).indikator)
    }
}
