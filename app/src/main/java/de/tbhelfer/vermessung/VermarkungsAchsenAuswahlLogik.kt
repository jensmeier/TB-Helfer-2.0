package de.tbhelfer.vermessung

/**
 * Reine Entscheidungslogik fuer die Zuordnung eines gewaehlteten GVP/Festpunkts zu einer TRA.
 *
 * Ziel:
 * - eine klar naechste TRA darf automatisch vorgeschlagen werden,
 * - bei eng benachbarten Gleisen/Weichen wird nicht geraten,
 * - weit entfernte Projektionen bleiben bewusst manuell bestaetigungspflichtig.
 */
object VermarkungsAchsenAuswahlLogik {

    data class Kandidat(
        val achseId: Long,
        val abstandM: Double
    )

    data class Ergebnis(
        val relevanteKandidaten: List<Kandidat>,
        val automatischeAchseId: Long?
    ) {
        val istEindeutig: Boolean get() = automatischeAchseId != null
    }

    fun bewerte(
        kandidaten: List<Kandidat>,
        maxAbstandM: Double = 50.0,
        automatischBisAbstandM: Double = 10.0,
        mindestVorsprungM: Double = 2.5,
        relevanzFensterM: Double = 8.0,
        maxKandidaten: Int = 6
    ): Ergebnis {
        val sortiert = kandidaten
            .asSequence()
            .filter { it.achseId != 0L && it.abstandM.isFinite() && it.abstandM >= 0.0 && it.abstandM <= maxAbstandM }
            .groupBy { it.achseId }
            .map { (_, werte) -> werte.minBy { it.abstandM } }
            .sortedBy { it.abstandM }
            .toList()

        val bester = sortiert.firstOrNull() ?: return Ergebnis(emptyList(), null)
        val relevante = sortiert
            .filter { it.abstandM <= bester.abstandM + relevanzFensterM }
            .take(maxKandidaten.coerceAtLeast(1))

        val zweiter = sortiert.getOrNull(1)
        val genuegendVorsprung = zweiter == null || zweiter.abstandM - bester.abstandM >= mindestVorsprungM
        val automatisch = if (bester.abstandM <= automatischBisAbstandM && genuegendVorsprung) bester.achseId else null

        return Ergebnis(relevante, automatisch)
    }
}
