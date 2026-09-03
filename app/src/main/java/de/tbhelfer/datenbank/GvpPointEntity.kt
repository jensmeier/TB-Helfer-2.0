package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Festpunkt/GVP aus DB_REF-Dateien, z. B. GVP_5302_15,0-18,0_DBREF2016_4.Streifen.txt. */
@Entity(
    tableName = "gvp_point",
    foreignKeys = [
        ForeignKey(
            entity = BaustelleEntity::class,
            parentColumns = ["id"],
            childColumns = ["baustelleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["baustelleId"], name = "index_gvp_point_baustelleId"),
        Index(value = ["strecke", "kmMeter"], name = "idx_gvp_point_strecke_km"),
        Index(value = ["punktName"], name = "idx_gvp_point_punktName"),
        Index(value = ["lat", "lon"], name = "idx_gvp_point_lat_lon")
    ]
)
data class GvpPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baustelleId: Long? = null,
    val strecke: String? = null,
    val punktName: String,
    val kmMeter: Double? = null,
    val xDbRef: Double,
    val yDbRef: Double,
    val lat: Double? = null,
    val lon: Double? = null,
    val hoeheM: Double? = null,
    val typ: String? = null,
    val klasse: String? = null,
    val koordinatensystem: String = TrassenAchseEntity.KOORD_DB_REF2016_4,
    val quelleDatei: String,
    val rawLine: String? = null,
    val importiertAmEpochMs: Long = System.currentTimeMillis()
)
