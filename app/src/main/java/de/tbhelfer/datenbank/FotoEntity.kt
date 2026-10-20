package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "foto",
    foreignKeys = [
        ForeignKey(
            entity = BaustelleEntity::class,
            parentColumns = ["id"],
            childColumns = ["baustelleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BaustellenKnotenEntity::class,
            parentColumns = ["id"],
            childColumns = ["baustellenKnotenId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("baustelleId"),
        Index("baustellenKnotenId"),
        Index(value = ["baustelleId", "erstelltAmEpochMs"], name = "idx_foto_baustelle_time"),
        Index(value = ["baustelleId", "km"], name = "idx_foto_baustelle_km")
    ]
)
data class FotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baustelleId: Long,

    /** Optionaler Unterbaustellen-/Objektknoten aus dem Baustellenbaum. */
    val baustellenKnotenId: Long? = null,

    /** Dateipfad zum JPG (App-Ordner). */
    val dateipfad: String,

    /** GPS (Pflicht pro Bild). */
    val gpsLat: Double,
    val gpsLon: Double,
    val gpsAltM: Double? = null,
    /** km (optional; kann automatisch oder manuell gesetzt sein). */
    val km: Double? = null,
    val kmSuffix: Int = 0,
    val kmManuell: Boolean = false,

    /** Abstand zur berechneten Achse (m), falls berechnet. */
    val achsabstandM: Double? = null,

    /** Indikatorstatus zur Aufnahmezeit. */
    val indikator: Indikator? = null,

    /** Firma (Auswahl/Autovervollständigung). */
    val firma: String,

    /** Bildtyp (BTB-konform). */
    val bildtyp: Bildtyp,

    /** Kurzbeschreibung (stichwortartig). */
    val kurztext: String,

    /** Optional: Ausführlichere Notiz (für BTB/Leistungsaufschreibung). */
    val notiz: String? = null,

    /** Optional: Gleis/Weiche/S... Kennung, falls erfasst. */
    val objektKennung: String? = null,

    /** Manueller Foto-Bereich bei Weiche oder DKW/Kreuzung; "Allgemein" wird als null gespeichert. */
    val objektBereich: String? = null,

    /** Optional frei benennbare Zusatzinfos (Werte). */
    val info1Wert: String? = null,
    val info2Wert: String? = null,
    val info3Wert: String? = null,
    val info4Wert: String? = null,
    val info5Wert: String? = null,

    /** Zeitstempel der Aufnahme. */
    val erstelltAmEpochMs: Long = System.currentTimeMillis()
) {
    /** Nur Anzeige/Transport; der Room-Aufnahmedatensatz bleibt unveraendert. */
    @androidx.room.Ignore
    var btbMetadaten: AufnahmeMetadaten? = null

    @get:androidx.room.Ignore
    val gpsLatEffektiv: Double?
        get() = if (btbMetadaten != null) btbMetadaten?.lat else
            gpsLat.takeIf { it.isFinite() && it in -90.0..90.0 && (gpsLat != 0.0 || gpsLon != 0.0) }
    @get:androidx.room.Ignore
    val gpsLonEffektiv: Double?
        get() = if (btbMetadaten != null) btbMetadaten?.lon else
            gpsLon.takeIf { it.isFinite() && it in -180.0..180.0 && (gpsLat != 0.0 || gpsLon != 0.0) }
    @get:androidx.room.Ignore
    val aufnahmeEpochEffektiv: Long?
        get() = if (btbMetadaten != null) btbMetadaten?.epoch else erstelltAmEpochMs.takeIf { it > 0L }
    @get:androidx.room.Ignore
    val gpsAccuracyEffektiv: Double? get() = btbMetadaten?.accuracyM
    @get:androidx.room.Ignore
    val gpsAnzeige: String
        get() = if (gpsLatEffektiv != null && gpsLonEffektiv != null)
            String.format(java.util.Locale.US, "%.6f, %.6f", gpsLatEffektiv, gpsLonEffektiv) else "–"

    fun mitMetadatenVon(source: FotoEntity): FotoEntity = apply { btbMetadaten = source.btbMetadaten }
}

data class AufnahmeMetadaten(val lat: Double?, val lon: Double?, val epoch: Long?, val accuracyM: Double?)

