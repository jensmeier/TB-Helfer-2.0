package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Zusaetzliche KM-Anker, Kontrollanker und KM-Spruenge eines manuellen Abschnitts. */
@Entity(
    tableName = "trassen_abschnitt_km_punkt",
    foreignKeys = [
        ForeignKey(
            entity = TrassenManuellerAbschnittEntity::class,
            parentColumns = ["id"],
            childColumns = ["abschnittId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["abschnittId"], name = "idx_trassen_abschnitt_km_punkt_abschnitt"),
        Index(value = ["abschnittId", "station"], name = "idx_trassen_abschnitt_km_punkt_station")
    ]
)
data class TrassenAbschnittKmPunktEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val abschnittId: Long,
    /** ANKER, KONTROLLANKER oder KM_SPRUNG. */
    val typ: String,
    val station: Double,
    /** ANKER/KONTROLLANKER: bekannter KM. KM_SPRUNG: KM vor Sprung. */
    val kmMeter: Double? = null,
    /** Nur KM_SPRUNG: KM nach Sprung. */
    val kmNachSprungMeter: Double? = null,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    val gpsAccuracyM: Float? = null,
    val gpsStreuungM: Double? = null,
    val achsabstandM: Double? = null,
    val notiz: String? = null,
    val erstelltAmEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYP_ANKER = "ANKER"
        const val TYP_KONTROLLANKER = "KONTROLLANKER"
        const val TYP_KM_SPRUNG = "KM_SPRUNG"
    }
}
