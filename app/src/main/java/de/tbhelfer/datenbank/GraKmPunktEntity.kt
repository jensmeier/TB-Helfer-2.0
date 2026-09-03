package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Punkt aus einer GRA-/kmLinie-Datei.
 *
 * Die gelieferten GRA-Dateien sind binaer. Patch 7 speichert zuerst die nutzbaren Werte
 * km/Station, Hoehe und Gradienten-/Bogenparameter. Die Verknuepfung zur TRA-Achse
 * wird spaeter im Berechner hergestellt.
 */
@Entity(
    tableName = "gra_km_punkt",
    foreignKeys = [
        ForeignKey(
            entity = BaustelleEntity::class,
            parentColumns = ["id"],
            childColumns = ["baustelleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["baustelleId"], name = "index_gra_km_punkt_baustelleId"),
        Index(value = ["strecke", "gleis", "kmMeter"], name = "idx_gra_km_strecke_gleis_km"),
        Index(value = ["quelleDatei"], name = "idx_gra_km_quelle")
    ]
)
data class GraKmPunktEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baustelleId: Long? = null,
    val quelleDatei: String,
    val punktIndex: Int,
    val strecke: String? = null,
    val gleis: String? = null,
    val richtung: String? = null,
    val name: String? = null,
    val kmMeter: Double,
    val hoeheM: Double? = null,
    val radiusM: Double? = null,
    val parameterM: Double? = null,
    val punktCode: Int? = null,
    val koordinatensystem: String = TrassenAchseEntity.KOORD_DB_REF2016_4,
    val rawOffset: Int? = null,
    val importiertAmEpochMs: Long = System.currentTimeMillis()
)
