package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.TrassenAchseEntity
import de.tbhelfer.datenbank.TrassenSamplePointEntity

/**
 * Adapter zwischen den importierten Room-/TRA-Daten und der UI-unabhaengigen Anschlusslogik.
 * Es werden keine Daten gespeichert oder veraendert.
 */
object TrassenAnschlussDbAdapter {

    fun findeAnschluesse(
        achsen: List<TrassenAchseEntity>,
        samplepunkte: List<TrassenSamplePointEntity>,
        toleranzM: Double = 2.5
    ): List<TrassenAnschlussLogik.Anschluss> {
        if (achsen.isEmpty() || samplepunkte.isEmpty()) return emptyList()

        val punkteNachAchse = samplepunkte.groupBy { it.achseId }
        val fachAchsen = achsen.map { achse ->
            TrassenAnschlussLogik.Achse(
                id = achse.id,
                name = achse.name,
                achsTyp = achse.achsTyp,
                punkte = punkteNachAchse[achse.id].orEmpty().map { punkt ->
                    TrassenAnschlussLogik.Punkt(
                        station = punkt.station,
                        x = punkt.xDbRef,
                        y = punkt.yDbRef
                    )
                }
            )
        }

        return TrassenAnschlussLogik.findeAnschluesse(fachAchsen, toleranzM)
    }
}
