package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "streckenpunkt",
    foreignKeys = [
        ForeignKey(
            entity = BaustelleEntity::class,
            parentColumns = ["id"],
            childColumns = ["baustelleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("baustelleId"),
        Index(value = ["baustelleId", "km"], name = "idx_streckenpunkt_baustelle_km"),
        Index(value = ["baustelleId", "typ"], name = "idx_streckenpunkt_baustelle_typ")
    ]
)
data class StreckenpunktEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baustelleId: Long,

    /** Kilometerangabe (z.B. 12.345). Für HILFS-Punkte kann dies interpoliert sein. */
    val km: Double,

    /** Suffix der Kilometrierung: -0 / -1 / -2 (siehe mehrgleisige Strecken). */
    val kmSuffix: Int = 0,

    /** GPS Position (WGS84). */
    val gpsLat: Double,
    val gpsLon: Double,
    val gpsAltM: Double? = null,

    /** Typ des Streckenpunkts (EXAKT/HILFS/Weichenrollen). */
    val typ: StreckenpunktTyp = StreckenpunktTyp.EXAKT,
    val typFreitext: String? = null,

    /** Optional: Strecken-/Bahnhofsbezug für Suche und Filter. */
    val streckennummer: String? = null,
    val bahnhof: String? = null,
    val gleis: String? = null,
    val weiche: String? = null,

    /** Optional: Weichennummer / Gleis-/Objektkennung (z.B. "W12", "Gleis 2"). */
    val objektKennung: String? = null,

    /** Kurze Bezeichnung (z.B. "BÜ Nord", "Signal A"). */
    val bezeichnung: String = "",

    val notiz: String? = null,
    val erstelltAmEpochMs: Long = System.currentTimeMillis()
)
