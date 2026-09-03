package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.TrassenAchseEntity
import de.tbhelfer.datenbank.TrassenSamplePointEntity

/**
 * Adapter zwischen den importierten Room-/TRA-Daten und der UI-unabhaengigen Anschluss-/Pfadlogik.
 * Es werden keine Daten gespeichert oder veraendert.
 */
object TrassenAnschlussDbAdapter {

    /**
     * Baut die gemeinsame Fachgeometrie aus den bestehenden Room-Entities auf.
     * Dieselben Achsen koennen dadurch sowohl fuer Anschluss- als auch fuer Pfadwahl verwendet werden.
     */
    fun zuFachAchsen(
        achsen: List<TrassenAchseEntity>,
        samplepunkte: List<TrassenSamplePointEntity>
    ): List<TrassenAnschlussLogik.Achse> {
        if (achsen.isEmpty()) return emptyList()
        val punkteNachAchse = samplepunkte.groupBy { it.achseId }
        return achsen.map { achse ->
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
    }

    fun findeAnschluesse(
        achsen: List<TrassenAchseEntity>,
        samplepunkte: List<TrassenSamplePointEntity>,
        toleranzM: Double = 2.5
    ): List<TrassenAnschlussLogik.Anschluss> {
        if (achsen.isEmpty() || samplepunkte.isEmpty()) return emptyList()
        return TrassenAnschlussLogik.findeAnschluesse(
            zuFachAchsen(achsen, samplepunkte),
            toleranzM
        )
    }
}
