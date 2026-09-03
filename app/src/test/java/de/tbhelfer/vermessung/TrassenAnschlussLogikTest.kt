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
            id = 1,
            name = "Hauptgleis",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val ast = achse(
            id = 2,
            name = "Weichenast",
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
    fun gegenseitigErkannterAnschluss_wirdNurEinmalGeliefert() {
        val a = achse(
            id = 10,
            name = "A",
            0.0 to (0.0 to 0.0),
            10.0 to (10.0 to 0.0)
        )
        val b = achse(
            id = 20,
            name = "B",
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
            id = 1,
            name = "A",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val b = achse(
            id = 2,
            name = "B",
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
            id = 1,
            name = "A",
            0.0 to (0.0 to 0.0),
            10.0 to (10.0 to 0.0)
        )
        val b = achse(
            id = 2,
            name = "B",
            0.0 to (10.0 to 5.0),
            10.0 to (20.0 to 5.0)
        )

        assertTrue(TrassenAnschlussLogik.findeAnschluesse(listOf(a, b), toleranzM = 2.5).isEmpty())
    }

    @Test
    fun dkwVierAeste_liefertAnschluesseMitStationAufBeidenAchsen() {
        val da = achse(
            id = 100,
            name = "D-A",
            0.0 to (0.0 to 0.0),
            20.0 to (20.0 to 20.0)
        )
        val cb = achse(
            id = 200,
            name = "C-B",
            0.0 to (0.0 to 20.0),
            20.0 to (20.0 to 0.0)
        )
        val zubringerD = achse(
            id = 300,
            name = "Zubringer D",
            0.0 to (-10.0 to -10.0),
            14.142 to (0.0 to 0.0)
        )
        val zubringerC = achse(
            id = 400,
            name = "Zubringer C",
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
