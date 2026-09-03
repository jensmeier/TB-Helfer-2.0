package de.tbhelfer.ui.suche

import de.tbhelfer.datenbank.Bildtyp

data class SucheFilter(
    val baustelleId: Long? = null,
    val datumVonEpochMs: Long? = null,
    val datumBisEpochMs: Long? = null,
    val streckenNrQuery: String? = null,
    val volltext: String? = null,

    /** optionaler km-Filter (von/bis). */
    val kmVon: Double? = null,
    val kmBis: Double? = null,

    /**
     * Streckenpunkte:
     * - wenn [nurBeiStreckenpunkten] true: nur Fotos in der Nähe eines Streckenpunkts (± Toleranz).
     * - wenn [streckenpunktText] gesetzt: nur Streckenpunkte, deren Text passt (Bezeichnung/Objektkennung/Notiz).
     */
    val nurBeiStreckenpunkten: Boolean = false,
    val streckenpunktText: String? = null,

    val bildtypen: Set<Bildtyp> = emptySet(),
    /** true = neueste zuerst */
    val sortDesc: Boolean = true
)
