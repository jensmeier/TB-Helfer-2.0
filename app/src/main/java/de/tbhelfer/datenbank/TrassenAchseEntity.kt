package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Importierte Vermessungsachse aus TRA/GRA.
 * Beispiele: Gleis 302, Gleis 303, Weiche 125W, Weichenverbindung 127-128.
 */
@Entity(
    tableName = "trassen_achse",
    foreignKeys = [
        ForeignKey(
            entity = BaustelleEntity::class,
            parentColumns = ["id"],
            childColumns = ["baustelleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["baustelleId"], name = "index_trassen_achse_baustelleId"),
        Index(value = ["strecke", "gleis"], name = "idx_trassen_achse_strecke_gleis"),
        Index(value = ["name"], name = "idx_trassen_achse_name"),
        Index(value = ["quelleDatei"], name = "idx_trassen_achse_quelle")
    ]
)
data class TrassenAchseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baustelleId: Long? = null,
    val name: String,
    val strecke: String? = null,
    val gleis: String? = null,
    val richtung: String? = null,
    val achsTyp: String = "UNBEKANNT",
    val quelleDatei: String,
    val dateiSignatur: String? = null,
    val koordinatensystem: String = "DB_REF2016_4",
    val stationVon: Double? = null,
    val stationBis: Double? = null,
    val anzahlElemente: Int = 0,
    val anzahlSamplepunkte: Int = 0,
    val importStatus: String = "VORBEREITET",
    val importiertAmEpochMs: Long = System.currentTimeMillis(),
    val notiz: String? = null
) {
    companion object {
        const val ACHSTYP_GLEIS = "GLEIS"
        const val ACHSTYP_WEICHE = "WEICHE"
        const val ACHSTYP_WEICHENVERBINDUNG = "WEICHENVERBINDUNG"
        const val ACHSTYP_STRECKE = "STRECKE"
        const val ACHSTYP_UNBEKANNT = "UNBEKANNT"

        const val KOORD_DB_REF2016_4 = "DB_REF2016_4"
        const val STATUS_VORBEREITET = "VORBEREITET"
        const val STATUS_IMPORTIERT = "IMPORTIERT"
        const val STATUS_FEHLER = "FEHLER"
    }
}
