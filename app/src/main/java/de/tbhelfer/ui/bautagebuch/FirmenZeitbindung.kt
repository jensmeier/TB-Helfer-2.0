package de.tbhelfer.ui.bautagebuch

/** Gleiche REPORT/MANUAL- und Legacy-Regel wie serverseitig in btb_times.py. */
object FirmenZeitbindung {
    const val REPORT = "REPORT"
    const val MANUAL = "MANUAL"

    data class Zeiten(val von: String?, val bis: String?, val bindung: String?)

    private fun wert(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

    fun berichtGeaendert(zeiten: Zeiten, vorherVon: String?, vorherBis: String?, von: String?, bis: String?): Zeiten {
        if (zeiten.bindung == MANUAL) return zeiten
        if (zeiten.bindung != null && zeiten.bindung != REPORT) return zeiten
        val actual = listOf(wert(zeiten.von), wert(zeiten.bis))
        val before = listOf(wert(vorherVon), wert(vorherBis))
        if (zeiten.bindung != REPORT && actual.indices.any { actual[it] != null && actual[it] != before[it] }) {
            return zeiten
        }
        // Ohne fachliche Zeitänderung keinen alten JSON-/Abschluss-Hash durch ein neues Flag ändern.
        if (actual == listOf(wert(von), wert(bis))) return zeiten
        return Zeiten(wert(von), wert(bis), REPORT)
    }

    fun manuellGewaehlt(von: String?, bis: String?, berichtVon: String?, berichtBis: String?): String =
        if (listOf(wert(von), wert(bis)) == listOf(wert(berichtVon), wert(berichtBis))) REPORT else MANUAL

    fun uebernehmen(firma: FirmaBlock, vorherVon: String?, vorherBis: String?, von: String?, bis: String?): FirmaBlock {
        val personal = berichtGeaendert(Zeiten(firma.personalVon, firma.personalBis, firma.personalZeitbindung), vorherVon, vorherBis, von, bis)
        val maschinen = berichtGeaendert(Zeiten(firma.maschinenVon, firma.maschinenBis, firma.maschinenZeitbindung), vorherVon, vorherBis, von, bis)
        return firma.copy(
            personalVon = personal.von, personalBis = personal.bis, personalZeitbindung = personal.bindung,
            maschinenVon = maschinen.von, maschinenBis = maschinen.bis, maschinenZeitbindung = maschinen.bindung
        )
    }
}
