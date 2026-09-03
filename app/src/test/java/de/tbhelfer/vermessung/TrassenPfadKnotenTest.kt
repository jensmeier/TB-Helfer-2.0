package de.tbhelfer.vermessung

import org.junit.Assert.assertNull
import org.junit.Test

class TrassenPfadKnotenTest {

    @Test
    fun nachAstwahl_wirdDerselbeDreiAchsKnotenNichtSofortErneutAngeboten() {
        val haupt = achse(
            1,
            "Haupt",
            0.0 to (0.0 to 0.0),
            100.0 to (100.0 to 0.0)
        )
        val astLinks = achse(
            2,
            "Ast links",
            0.0 to (45.0 to 20.0),
            20.0 to (50.0 to 0.2)
        )
        val astRechts = achse(
            3,
            "Ast rechts",
            0.0 to (55.0 to -20.0),
            20.0 to (50.0 to -0.2)
        )
        val achsen = listOf(haupt, astLinks, astRechts)
        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(achsen, toleranzM = 0.5)
        val start = TrassenPfadLogik.starte(achsen, 1, 10.0, +1)
        val entscheidung = TrassenPfadLogik.naechsteEntscheidung(achsen, anschluesse, start)!!
        val aufLinks = entscheidung.optionen.single {
            it.typ == TrassenPfadLogik.OptionsTyp.ACHSE_WECHSELN && it.nachAchseId == 2L
        }

        val nachWechsel = TrassenPfadLogik.wendeOptionAn(start, entscheidung, aufLinks)

        assertNull(TrassenPfadLogik.naechsteEntscheidung(achsen, anschluesse, nachWechsel))
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
