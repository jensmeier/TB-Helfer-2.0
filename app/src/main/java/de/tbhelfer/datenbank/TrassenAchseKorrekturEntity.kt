package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.Index

/**
 * Manuelle Korrekturebene fuer importierte Achsen.
 *
 * Die importierte TRA-Geometrie bleibt unveraendert. Gespeichert wird nur,
 * wie eine Rohachse in der App fachlich verwendet/angezeigt werden soll.
 */
@Entity(
    tableName = "trassen_achse_korrektur",
    primaryKeys = ["achseName", "quelleDatei"],
    indices = [
        Index(value = ["achseName"], name = "idx_trassen_korrektur_name"),
        Index(value = ["quelleDatei"], name = "idx_trassen_korrektur_quelle"),
        Index(value = ["achsTypNeu", "streckeNeu", "gleisNeu"], name = "idx_trassen_korrektur_ziel")
    ]
)
data class TrassenAchseKorrekturEntity(
    val achseName: String,
    val quelleDatei: String,
    val dateiSignatur: String? = null,
    val originalAchsTyp: String? = null,
    val originalStrecke: String? = null,
    val originalGleis: String? = null,
    val originalRichtung: String? = null,
    val achsTypNeu: String,
    val streckeNeu: String? = null,
    val gleisNeu: String? = null,
    val richtungNeu: String? = null,
    val erstelltAmEpochMs: Long = System.currentTimeMillis(),
    val aktualisiertAmEpochMs: Long = System.currentTimeMillis(),
    val notiz: String? = null
) {
    fun zielKurzText(): String = when (achsTypNeu) {
        TrassenAchseEntity.ACHSTYP_STRECKE -> {
            val str = streckeNeu?.takeIf { it.isNotBlank() } ?: "?"
            val ri = richtungNeu?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: ""
            "Str $str$ri"
        }
        TrassenAchseEntity.ACHSTYP_GLEIS -> gleisNeu?.takeIf { it.isNotBlank() }?.let { "Gl $it" } ?: "Gleis"
        TrassenAchseEntity.ACHSTYP_WEICHE -> "Weiche"
        TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> "WV"
        else -> "Unbekannt"
    }
}
