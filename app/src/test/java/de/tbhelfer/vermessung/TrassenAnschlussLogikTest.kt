package de.tbhelfer.vermessung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrassenAnschlussLogikTest {

    @Test
    fun weichenAst_trifftHauptachseMittenImSegment() {
        val haupt = achse(
            1,
            "Hauptgleis",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val ast = achse(
            2,
            "Weichenast",
            0.0 to (50.0 to 20.0),
            20.0 to (50.0 to 0.8)
        )

        val anschluss = TrassenAnschlussLogik.findeAnschluesse(listOf(haupt, ast), toleranzM = 2.5).single()

        val hauptPos = anschluss.positionFuer(1)
        val astPos = anschluss.positionFuer(2)
        assertNotNull(hauptPos)
        assertNotNull(astPos)
        assertEquals(50.0, hauptPos!!.station, 0.001)
        assertFalse(hauptPos.istAchsEnde)
        assertEquals(20.0, astPos!!.station, 0.001)
        assertTrue(astPos.istAchsEnde)
        assertEquals(0.8, anschluss.abstandM, 0.001)
    }

    @Test
    fun mehrereWeichenaeste_werdenAlsAuswahlGeliefertNichtAutomatischEntschieden() {
        val haupt = achse(
            1,
            "Hauptgleis",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val links = achse(
            2,
            "Ast links",
            0.0 to (45.0 to 20.0),
            20.0 to (50.0 to 0.4)
        )
        val rechts = achse(
            3,
            "Ast rechts",
            0.0 to (55.0 to -20.0),
            20.0 to (50.0 to -0.4)
        )

        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(
            listOf(haupt, links, rechts),
            toleranzM = 1.0
        )
        val fortsetzungen = TrassenAnschlussLogik.fortsetzungenVon(
            achseId = 1,
            anschluesse = anschluesse,
            naheStation = 50.0,
            stationToleranzM = 1.0
        )

        assertEquals(2, fortsetzungen.size)
        assertEquals(setOf(2L, 3L), fortsetzungen.map { it.nach.achseId }.toSet())
        assertTrue(TrassenAnschlussLogik.fortsetzungenVon(1, anschluesse, naheStation = 10.0).isEmpty())
    }

    @Test
    fun gegenseitigErkannterAnschluss_wirdNurEinmalGeliefert() {
        val a = achse(
            10,
            "A",
            0.0 to (0.0 to 0.0),
            10.0 to (10.0 to 0.0)
        )
        val b = achse(
            20,
            "B",
            0.0 to (10.0 to 0.0),
            10.0 to (20.0 to 0.0)
        )

        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(listOf(a, b))

        assertEquals(1, anschluesse.size)
        assertEquals(10.0, anschluesse.single().positionFuer(10)!!.station, 0.001)
        assertEquals(0.0, anschluesse.single().positionFuer(20)!!.station, 0.001)
    }

    @Test
    fun zweiGetrennteAnschluesseDesselbenAchsenpaares_bleibenErhalten() {
        val a = achse(
            1,
            "A",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val b = achse(
            2,
            "B",
            0.0 to (0.0 to 0.5),
            100.0 to (100.0 to 0.5)
        )

        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(listOf(a, b), toleranzM = 1.0)

        assertEquals(2, anschluesse.size)
        assertTrue(anschluesse.any { it.positionFuer(1)?.station == 0.0 })
        assertTrue(anschluesse.any { it.positionFuer(1)?.station == 100.0 })
    }

    @Test
    fun zuWeitEntfernteAchse_istKeinAnschluss() {
        val a = achse(
            1,
            "A",
            0.0 to (0.0 to 0.0),
            10.0 to (10.0 to 0.0)
        )
        val b = achse(
            2,
            "B",
            0.0 to (10.0 to 5.0),
            10.0 to (20.0 to 5.0)
        )

        assertTrue(TrassenAnschlussLogik.findeAnschluesse(listOf(a, b), toleranzM = 2.5).isEmpty())
    }

    @Test
    fun dkwVierAeste_liefertAnschluesseMitStationAufBeidenAchsen() {
        val da = achse(
            100,
            "D-A",
            0.0 to (0.0 to 0.0),
            20.0 to (20.0 to 20.0)
        )
        val cb = achse(
            200,
            "C-B",
            0.0 to (0.0 to 20.0),
            20.0 to (20.0 to 0.0)
        )
        val zubringerD = achse(
            300,
            "Zubringer D",
            0.0 to (-10.0 to -10.0),
            14.142 to (0.0 to 0.0)
        )
        val zubringerC = achse(
            400,
            "Zubringer C",
            0.0 to (-10.0 to 30.0),
            14.142 to (0.0 to 20.0)
        )

        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(
            listOf(da, cb, zubringerD, zubringerC),
            toleranzM = 0.2
        )

        assertTrue(anschluesse.any { a -> a.enthaeltAchse(100) && a.enthaeltAchse(300) })
        assertTrue(anschluesse.any { a -> a.enthaeltAchse(200) && a.enthaeltAchse(400) })
        anschluesse.forEach {
            assertTrue(it.a.station.isFinite())
            assertTrue(it.b.station.isFinite())
        }
    }

    private fun achse(
        id: Long,
        name: String,
        vararg punkte: Pair<Double, Pair<Double, Double>>
    ): TrassenAnschlussLogik.Achse = TrassenAnschlussLogik.Achse(
        id = id,
        name = name,
        punkte = punkte.map { (station, xy) ->
            TrassenAnschlussLogik.Punkt(station, xy.first, xy.second)
        }
    )
}
