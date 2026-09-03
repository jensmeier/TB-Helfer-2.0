package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.Index

/**
 * Manueller KM-Anker fuer eine importierte TRA-Achse.
 *
 * Der Schluessel aus Achsname + Quelldatei bleibt auch bei einem Re-Import stabil.
 * Die interne Achsstation wird von der App aus GPS -> TRA-Projektion automatisch ermittelt.
 */
@Entity(
    tableName = "trassen_km_anker",
    primaryKeys = ["achseName", "quelleDatei", "punktNummer"],
    indices = [
        Index(value = ["achseName", "quelleDatei"], name = "idx_trassen_km_anker_achse"),
        Index(value = ["aktuelleAchseId"], name = "idx_trassen_km_anker_achse_id")
    ]
)
data class TrassenKmAnkerEntity(
    val achseName: String,
    val quelleDatei: String,
    val punktNummer: Int,
    val aktuelleAchseId: Long? = null,
    val dateiSignatur: String? = null,
    val station: Double,
    val kmMeter: Double,
    val gpsLat: Double,
    val gpsLon: Double,
    val gpsAccuracyM: Float? = null,
    val achsabstandM: Double? = null,
    val erstelltAmEpochMs: Long = System.currentTimeMillis(),
    val aktualisiertAmEpochMs: Long = System.currentTimeMillis()
)
