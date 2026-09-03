package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Importprotokoll für Vermessungsdaten. */
@Entity(
    tableName = "trassen_import_status",
    indices = [
        Index(value = ["quelleDatei"], name = "idx_trassen_import_status_quelle"),
        Index(value = ["datenTyp"], name = "idx_trassen_import_status_typ"),
        Index(value = ["importiertAmEpochMs"], name = "idx_trassen_import_status_zeit")
    ]
)
data class TrassenImportStatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quelleDatei: String,
    val datenTyp: String,
    val status: String,
    val anzahlAchsen: Int = 0,
    val anzahlElemente: Int = 0,
    val anzahlSamplepunkte: Int = 0,
    val anzahlGvpPunkte: Int = 0,
    val anzahlAbsteckpunkte: Int = 0,
    val warnungen: Int = 0,
    val fehler: Int = 0,
    val meldung: String? = null,
    val importiertAmEpochMs: Long = System.currentTimeMillis()
)
