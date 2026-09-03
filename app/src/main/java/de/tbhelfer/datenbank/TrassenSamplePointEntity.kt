package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Vorberechneter Punkt auf einer Achse. Später erzeugt der Importer daraus z. B. 1-m- oder 2-m-Punkte,
 * damit GPS-Abstand, Gleis und km schnell berechnet werden können.
 */
@Entity(
    tableName = "trassen_sample_point",
    foreignKeys = [
        ForeignKey(
            entity = TrassenAchseEntity::class,
            parentColumns = ["id"],
            childColumns = ["achseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["achseId"], name = "index_trassen_sample_point_achseId"),
        Index(value = ["achseId", "station"], name = "idx_trassen_sample_achse_station"),
        Index(value = ["achseId", "kmMeter"], name = "idx_trassen_sample_achse_km"),
        Index(value = ["lat", "lon"], name = "idx_trassen_sample_lat_lon")
    ]
)
data class TrassenSamplePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val achseId: Long,
    val station: Double,
    val kmMeter: Double? = null,
    /** Herkunft des KM-Werts: GRA_KM, GVP, MANUELL_2PUNKT. Null = nicht belastbar/Altbestand. */
    val kmQuelle: String? = null,
    val xDbRef: Double,
    val yDbRef: Double,
    val lat: Double? = null,
    val lon: Double? = null,
    val hoeheM: Double? = null,
    val richtungDeg: Double? = null
)
