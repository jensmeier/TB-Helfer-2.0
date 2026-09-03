package de.tbhelfer.vermessung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrassenPfadLogikTest {

    @Test
    fun weichenKnoten_bietetGeradeausUndAlleAesteAn() {
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
            20.0 to (50.0 to 0.2)
        )
        val rechts = achse(
            3,
            "Ast rechts",
            0.0 to (55.0 to -20.0),
            20.0 to (50.0 to -0.2)
        )
        val achsen = listOf(haupt, links, rechts)
        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(achsen, toleranzM = 0.5)
        val start = TrassenPfadLogik.starte(
            achsen,
            achseId = 1,
            station = 10.0,
            stationRichtung = TrassenPfadLogik.STATION_RICHTUNG_STEIGEND
        )

        val entscheidung = TrassenPfadLogik.naechsteEntscheidung(achsen, anschluesse, start)!!

        assertEquals(50.0, entscheidung.station, 0.001)
        assertEquals(40.0, entscheidung.entfernungM, 0.001)
        assertEquals(3, entscheidung.optionen.size)
        assertEquals(1, entscheidung.optionen.count { it.typ == TrassenPfadLogik.OptionsTyp.AUF_ACHSE_BLEIBEN })
        assertEquals(
            setOf(2L, 3L),
            entscheidung.optionen
                .filter { it.typ == TrassenPfadLogik.OptionsTyp.ACHSE_WECHSELN }
                .map { it.nachAchseId }
                .toSet()
        )
    }

    @Test
    fun gewaehlterWeichenast_bautAchsfolgeUndPfadlaengeAuf() {
        val haupt = achse(
            1,
            "Hauptgleis",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val ast = achse(
            2,
            "Abzweig",
            0.0 to (50.0 to 20.0),
            20.0 to (50.0 to 0.0)
        )
        val achsen = listOf(haupt, ast)
        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(achsen, toleranzM = 0.2)
        val start = TrassenPfadLogik.starte(achsen, 1, 10.0, 1)
        val entscheidung = TrassenPfadLogik.naechsteEntscheidung(achsen, anschluesse, start)!!
        val wechsel = entscheidung.optionen.single {
            it.typ == TrassenPfadLogik.OptionsTyp.ACHSE_WECHSELN
        }

        val aufAst = TrassenPfadLogik.wendeOptionAn(start, entscheidung, wechsel)
        val pfad = TrassenPfadLogik.beendeBei(achsen, aufAst, zielStation = 0.0)!!

        assertEquals(2L, aufAst.achseId)
        assertEquals(20.0, aufAst.station, 0.001)
        assertEquals(TrassenPfadLogik.STATION_RICHTUNG_FALLEND, aufAst.stationRichtung)
        assertEquals(60.0, pfad.gesamtLaengeM, 0.001)
        assertEquals(listOf(1L, 2L), pfad.achsFolge)
        assertEquals(70_060.0, pfad.kmAmEnde(70_000.0, +1), 0.001)
        assertEquals(69_940.0, pfad.kmAmEnde(70_000.0, -1), 0.001)
    }

    @Test
    fun achsEnde_hatKeinGeradeausUndPartnerRichtungWirdBestimmt() {
        val a = achse(
            1,
            "A",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val b = achse(
            2,
            "B",
            0.0 to (100.0 to 0.0),
            50.0 to (150.0 to 0.0)
        )
        val achsen = listOf(a, b)
        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(achsen, toleranzM = 0.1)
        val start = TrassenPfadLogik.starte(achsen, 1, 90.0, +1)

        val entscheidung = TrassenPfadLogik.naechsteEntscheidung(achsen, anschluesse, start)!!

        assertEquals(100.0, entscheidung.station, 0.001)
        assertEquals(1, entscheidung.optionen.size)
        val option = entscheidung.optionen.single()
        assertEquals(TrassenPfadLogik.OptionsTyp.ACHSE_WECHSELN, option.typ)
        assertEquals(2L, option.nachAchseId)
        assertEquals(0.0, option.nachStation, 0.001)
        assertEquals(TrassenPfadLogik.STATION_RICHTUNG_STEIGEND, option.nachStationRichtung)
    }

    @Test
    fun partnerMitAnschlussMittenInAchse_bietetBeideRichtungenAn() {
        val zubringer = achse(
            1,
            "Zubringer",
            0.0 to (50.0 to 20.0),
            10.0 to (50.0 to 0.0)
        )
        val durchgehend = achse(
            2,
            "Durchgehend",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val achsen = listOf(zubringer, durchgehend)
        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(achsen, toleranzM = 0.1)
        val start = TrassenPfadLogik.starte(achsen, 1, 0.0, +1)

        val entscheidung = TrassenPfadLogik.naechsteEntscheidung(achsen, anschluesse, start)!!
        val wechsel = entscheidung.optionen.filter { it.typ == TrassenPfadLogik.OptionsTyp.ACHSE_WECHSELN }

        assertEquals(2, wechsel.size)
        assertEquals(setOf(-1, +1), wechsel.map { it.nachStationRichtung }.toSet())
        assertTrue(wechsel.all { it.nachAchseId == 2L })
        assertTrue(wechsel.all { kotlin.math.abs(it.nachStation - 50.0) < 0.001 })
    }

    @Test
    fun geradeausGewaehlterKnoten_wirdNichtSofortErneutAngeboten() {
        val haupt = achse(
            1,
            "Haupt",
            0.0 to (0.0 to 0.0),
            120.0 to (120.0 to 0.0)
        )
        val ast50 = achse(
            2,
            "Ast 50",
            0.0 to (50.0 to 15.0),
            15.0 to (50.0 to 0.0)
        )
        val ast80 = achse(
            3,
            "Ast 80",
            0.0 to (80.0 to 15.0),
            15.0 to (80.0 to 0.0)
        )
        val achsen = listOf(haupt, ast50, ast80)
        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(achsen, toleranzM = 0.1)
        val start = TrassenPfadLogik.starte(achsen, 1, 10.0, +1)
        val erste = TrassenPfadLogik.naechsteEntscheidung(achsen, anschluesse, start)!!
        val geradeaus = erste.optionen.single { it.typ == TrassenPfadLogik.OptionsTyp.AUF_ACHSE_BLEIBEN }
        val nachErster = TrassenPfadLogik.wendeOptionAn(start, erste, geradeaus)

        val zweite = TrassenPfadLogik.naechsteEntscheidung(achsen, anschluesse, nachErster)!!

        assertEquals(50.0, nachErster.station, 0.001)
        assertEquals(80.0, zweite.station, 0.001)
        assertEquals(30.0, zweite.entfernungM, 0.001)
    }

    @Test
    fun reineKreuzungOhneAnschluss_hatKeinePfadentscheidung() {
        val a = achse(
            1,
            "A",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 100.0)
        )
        val b = achse(
            2,
            "B",
            0.0 to (0.0 to 100.0),
            100.0 to (100.0 to 0.0)
        )
        val achsen = listOf(a, b)
        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(achsen, toleranzM = 0.1)
        val start = TrassenPfadLogik.starte(achsen, 1, 10.0, +1)

        assertTrue(anschluesse.isEmpty())
        assertNull(TrassenPfadLogik.naechsteEntscheidung(achsen, anschluesse, start))
    }

    @Test
    fun pfadKannNichtRueckwaertsAufAktuellerAchseBeendetWerden() {
        val a = achse(
            1,
            "A",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val start = TrassenPfadLogik.starte(listOf(a), 1, 50.0, +1)

        assertNull(TrassenPfadLogik.beendeBei(listOf(a), start, zielStation = 40.0))
        assertEquals(25.0, TrassenPfadLogik.beendeBei(listOf(a), start, zielStation = 75.0)!!.gesamtLaengeM, 0.001)
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
