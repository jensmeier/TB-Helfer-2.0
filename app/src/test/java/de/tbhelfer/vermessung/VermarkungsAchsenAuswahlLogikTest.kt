package de.tbhelfer.vermessung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VermarkungsAchsenAuswahlLogikTest {

    @Test
    fun klareNaechsteAchseWirdAutomatischVorgeschlagen() {
        val ergebnis = VermarkungsAchsenAuswahlLogik.bewerte(
            listOf(
                VermarkungsAchsenAuswahlLogik.Kandidat(725, 0.8),
                VermarkungsAchsenAuswahlLogik.Kandidat(724, 4.2)
            )
        )

        assertTrue(ergebnis.istEindeutig)
        assertEquals(725L, ergebnis.automatischeAchseId)
    }

    @Test
    fun benachbarteAchsenBleibenZurAuswahl() {
        val ergebnis = VermarkungsAchsenAuswahlLogik.bewerte(
            listOf(
                VermarkungsAchsenAuswahlLogik.Kandidat(725, 1.4),
                VermarkungsAchsenAuswahlLogik.Kandidat(723, 2.1)
            )
        )

        assertFalse(ergebnis.istEindeutig)
        assertNull(ergebnis.automatischeAchseId)
        assertEquals(listOf(725L, 723L), ergebnis.relevanteKandidaten.map { it.achseId })
    }

    @Test
    fun weiterEntfernterPunktWirdNichtAutomatischZugeordnet() {
        val ergebnis = VermarkungsAchsenAuswahlLogik.bewerte(
            listOf(
                VermarkungsAchsenAuswahlLogik.Kandidat(725, 14.0),
                VermarkungsAchsenAuswahlLogik.Kandidat(723, 30.0)
            )
        )

        assertFalse(ergebnis.istEindeutig)
        assertEquals(listOf(725L), ergebnis.relevanteKandidaten.map { it.achseId })
    }

    @Test
    fun kandidatenAusserhalbDesSicherheitsbereichsWerdenNichtAngeboten() {
        val ergebnis = VermarkungsAchsenAuswahlLogik.bewerte(
            listOf(VermarkungsAchsenAuswahlLogik.Kandidat(725, 80.0))
        )

        assertTrue(ergebnis.relevanteKandidaten.isEmpty())
        assertNull(ergebnis.automatischeAchseId)
    }
}
