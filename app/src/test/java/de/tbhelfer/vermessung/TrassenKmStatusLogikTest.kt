package de.tbhelfer.vermessung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrassenKmStatusLogikTest {

    @Test
    fun manuellerAbschnitt_ohneGrenzen_bleibtRot() {
        val bewertung = TrassenKmStatusLogik.bewerteManuellenAbschnitt(
            kmAnkerStation = 100.0,
            kmAnkerMeter = 70_000.0,
            kmStationVorzeichen = 1,
            stationStart = null,
            stationEnde = null
        )

        assertEquals(TrassenKmStatusLogik.STATUS_FEHLT, bewertung.status)
        assertTrue(bewertung.grund.contains("Bereich"))
    }

    @Test
    fun manuellerAbschnitt_mitAnkerRichtungUndGrenzen_istOrange() {
        val bewertung = TrassenKmStatusLogik.bewerteManuellenAbschnitt(
            kmAnkerStation = 100.0,
            kmAnkerMeter = 70_000.0,
            kmStationVorzeichen = 1,
            stationStart = 50.0,
            stationEnde = 250.0
        )

        assertEquals(TrassenKmStatusLogik.STATUS_MANUELL, bewertung.status)
        assertEquals(69_950.0, bewertung.kmVonMeter!!, 0.001)
        assertEquals(70_150.0, bewertung.kmBisMeter!!, 0.001)
    }

    @Test
    fun manuellerAbschnitt_ankerAusserhalbDesBereichs_bleibtRot() {
        val bewertung = TrassenKmStatusLogik.bewerteManuellenAbschnitt(
            kmAnkerStation = 500.0,
            kmAnkerMeter = 70_000.0,
            kmStationVorzeichen = -1,
            stationStart = 100.0,
            stationEnde = 300.0
        )

        assertEquals(TrassenKmStatusLogik.STATUS_FEHLT, bewertung.status)
        assertTrue(bewertung.grund.contains("ausserhalb"))
    }

    @Test
    fun automatischePlausibleKm_istGruen() {
        val bewertung = TrassenKmStatusLogik.bewerte(
            punkte = listOf(
                TrassenKmStatusLogik.Punkt(0.0, 70_000.0),
                TrassenKmStatusLogik.Punkt(100.0, 70_100.0),
                TrassenKmStatusLogik.Punkt(200.0, 70_200.0)
            ),
            manuelleAnker = emptyList()
        )

        assertEquals(TrassenKmStatusLogik.STATUS_AUTOMATISCH, bewertung.status)
    }

    @Test
    fun unplausibleAutomatischeKm_istRotNichtGrau() {
        val bewertung = TrassenKmStatusLogik.bewerte(
            punkte = listOf(
                TrassenKmStatusLogik.Punkt(0.0, 70_000.0),
                TrassenKmStatusLogik.Punkt(100.0, 72_000.0)
            ),
            manuelleAnker = emptyList()
        )

        assertEquals(TrassenKmStatusLogik.STATUS_FEHLT, bewertung.status)
    }

    @Test
    fun alteZweiPunktKalibrierung_bleibtKompatibel() {
        val bewertung = TrassenKmStatusLogik.bewerte(
            punkte = emptyList(),
            manuelleAnker = listOf(
                TrassenKmStatusLogik.Anker(1, 100.0, 70_000.0),
                TrassenKmStatusLogik.Anker(2, 200.0, 70_100.0)
            )
        )

        assertEquals(TrassenKmStatusLogik.STATUS_MANUELL, bewertung.status)
    }
}
