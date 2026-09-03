package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ein Element einer importierten TRA-Achse: Gerade, Bogen oder Übergangsbogen. */
@Entity(
    tableName = "trassen_achse_element",
    foreignKeys = [
        ForeignKey(
            entity = TrassenAchseEntity::class,
            parentColumns = ["id"],
            childColumns = ["achseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["achseId"], name = "index_trassen_achse_element_achseId"),
        Index(value = ["achseId", "elementIndex"], name = "idx_trassen_element_achse_index", unique = true),
        Index(value = ["achseId", "stationStart"], name = "idx_trassen_element_achse_station")
    ]
)
data class TrassenAchseElementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val achseId: Long,
    val elementIndex: Int,
    val stationStart: Double,
    val stationEnde: Double? = null,
    val xDbRef: Double,
    val yDbRef: Double,
    val richtungDeg: Double? = null,
    val elementTyp: String = "UNBEKANNT",
    val laengeM: Double,
    val radiusM: Double? = null,
    val parameterVon: Double? = null,
    val parameterBis: Double? = null,
    val rawTyp: Int? = null
) {
    companion object {
        const val ELEMENT_GERADE = "GERADE"
        const val ELEMENT_BOGEN = "BOGEN"
        const val ELEMENT_UEBERGANG = "UEBERGANG"
        const val ELEMENT_UNBEKANNT = "UNBEKANNT"
    }
}
