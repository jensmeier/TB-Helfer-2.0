package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Patch45e: Manuell festgelegter betrieblicher Abschnitt auf einer vorhandenen TRA-Geometrie.
 *
 * Die Roh-TRA-Achse bleibt unveraendert. Ein Abschnitt darf als Entwurf auch unvollstaendig
 * gespeichert werden, damit Start/KM-Anker/Ende an verschiedenen Tagen aufgenommen werden koennen.
 */
@Entity(
    tableName = "trassen_manueller_abschnitt",
    indices = [
        Index(value = ["achseName", "quelleDatei"], name = "idx_trassen_manueller_abschnitt_achse"),
        Index(value = ["aktuelleAchseId"], name = "idx_trassen_manueller_abschnitt_achse_id"),
        Index(value = ["achsTyp", "strecke", "gleis"], name = "idx_trassen_manueller_abschnitt_ziel")
    ]
)
data class TrassenManuellerAbschnittEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val achseName: String? = null,
    val quelleDatei: String? = null,
    val aktuelleAchseId: Long? = null,
    val dateiSignatur: String? = null,

    val achsTyp: String,
    val bezeichnung: String,
    val strecke: String? = null,
    val gleis: String? = null,
    val richtung: String? = null,

    /** Interne TRA-Station in Meter. Null = Start wird spaeter gesetzt. */
    val stationStart: Double? = null,
    /** Null = Ende noch nicht festgelegt. */
    val stationEnde: Double? = null,
    /** GPS, GRAFIK oder SPAETER. */
    val startQuelle: String? = null,
    /** Optional bekannte Gesamtlaenge ab Start in Metern. */
    val bekannteLaengeM: Double? = null,

    /**
     * Nutzerangabe relativ zur Laufrichtung Start -> Ende: STEIGEND oder FALLEND.
     * Kann schon vor dem Ende bekannt sein.
     */
    val kmRichtung: String? = null,
    /** +1 = KM steigt mit TRA-Station, -1 = KM faellt mit TRA-Station. */
    val kmStationVorzeichen: Int? = null,
    /** Erster belastbarer KM-Anker darf irgendwo im Abschnitt liegen. */
    val kmAnkerStation: Double? = null,
    val kmAnkerMeter: Double? = null,

    /** Alt-/Kompatibilitaetsfelder aus Patch45c/d. */
    val kmPunkt1Meter: Double? = null,
    val kmPunkt2Station: Double? = null,
    val kmPunkt2Meter: Double? = null,

    val startGpsLat: Double? = null,
    val startGpsLon: Double? = null,
    val startGpsAccuracyM: Float? = null,
    val startGpsStreuungM: Double? = null,
    val startAchsabstandM: Double? = null,

    val punkt2GpsLat: Double? = null,
    val punkt2GpsLon: Double? = null,
    val punkt2GpsAccuracyM: Float? = null,
    val punkt2GpsStreuungM: Double? = null,
    val punkt2AchsabstandM: Double? = null,

    /** GPS, GRAFIK, LAENGE, FREIES_ACHSENDE oder SPAETER. */
    val endeQuelle: String? = null,
    /** OFFEN oder FERTIG. */
    val bearbeitungsStatus: String = "OFFEN",
    val notiz: String? = null,
    val erstelltAmEpochMs: Long = System.currentTimeMillis(),
    val aktualisiertAmEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val KM_RICHTUNG_STEIGEND = "STEIGEND"
        const val KM_RICHTUNG_FALLEND = "FALLEND"
        const val STATUS_OFFEN = "OFFEN"
        const val STATUS_FERTIG = "FERTIG"
    }
}
