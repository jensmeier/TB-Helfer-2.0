package de.tbhelfer.datenbank

/**
 * Leichtgewichtiges Projection-Model für Speicher-Bereinigung.
 * Room befüllt das direkt aus "SELECT id, dateipfad ...".
 */
data class FotoPfad(
    val id: Long,
    val dateipfad: String
)
