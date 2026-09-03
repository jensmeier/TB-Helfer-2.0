package de.tbhelfer.ui.suche

/** Anzeige-Model für die Foto-Sucheliste. */
data class SucheFotoItem(
    val fotoId: Long,
    val dateiPfad: String,
    val titel: String,
    val zeile1: String,
    val zeile2: String,
    val bildtypLabel: String
)
