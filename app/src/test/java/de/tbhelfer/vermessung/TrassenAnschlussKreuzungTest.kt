package de.tbhelfer.vermessung

import org.junit.Assert.assertTrue
import org.junit.Test

class TrassenAnschlussKreuzungTest {

    @Test
    fun geometrischeKreuzungOhneAchsendeErzeugtKeinenUebergang() {
        val da = TrassenAnschlussLogik.Achse(
            id = 100,
            name = "D-A",
            punkte = listOf(
                TrassenAnschlussLogik.Punkt(0.0, 0.0, 0.0),
                TrassenAnschlussLogik.Punkt(20.0, 20.0, 20.0)
            )
        )
        val cb = TrassenAnschlussLogik.Achse(
            id = 200,
            name = "C-B",
            punkte = listOf(
                TrassenAnschlussLogik.Punkt(0.0, 0.0, 20.0),
                TrassenAnschlussLogik.Punkt(20.0, 20.0, 0.0)
            )
        )

        // Schnittpunkt bei 10/10, aber dort endet keine der beiden TRA-Achsen.
        // Deshalb darf aus der reinen Kreuzung kein automatischer Pfadwechsel entstehen.
        val anschluesse = TrassenAnschlussLogik.findeAnschluesse(
            achsen = listOf(da, cb),
            toleranzM = 0.2
        )

        assertTrue(anschluesse.isEmpty())
    }
}
