package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Absteck- oder Kontrollpunkt aus *.kor.txt. */
@Entity(
    tableName = "absteckpunkt",
    foreignKeys = [
        ForeignKey(
            entity = BaustelleEntity::class,
            parentColumns = ["id"],
            childColumns = ["baustelleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["baustelleId"], name = "index_absteckpunkt_baustelleId"),
        Index(value = ["strecke", "gleis"], name = "idx_absteckpunkt_strecke_gleis"),
        Index(value = ["punktName"], name = "idx_absteckpunkt_punktName"),
        Index(value = ["lat", "lon"], name = "idx_absteckpunkt_lat_lon")
    ]
)
data class AbsteckpunktEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baustelleId: Long? = null,
    val punktName: String,
    val strecke: String? = null,
    val gleis: String? = null,
    val kmMeter: Double? = null,
    val xDbRef: Double,
    val yDbRef: Double,
    val lat: Double? = null,
    val lon: Double? = null,
    val hoeheM: Double? = null,
    val typ: String? = null,
    val quelleDatei: String,
    val rawLine: String? = null,
    val importiertAmEpochMs: Long = System.currentTimeMillis()
)
