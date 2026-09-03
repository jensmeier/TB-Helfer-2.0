package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Baustellenbaum-Knoten.
 *
 * Wichtige Regeln:
 * - HAUPT: rootId == id, parentId == null, knotenName == null
 * - Unterknoten: rootId zeigt immer auf die zugehörige Hauptbaustelle.
 * - Merge/Zusammenführen: Nur bei EXAKTEM Hauptbaustellen-Namen (Groß/Klein zählt!).
 */
@Entity(
    tableName = "baustellen_knoten",
    indices = [
        Index("rootId"),
        Index("parentId"),
        Index("hauptbaustellenName")
    ]
)
data class BaustellenKnotenEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Zeigt immer auf die Hauptbaustelle. Bei HAUPT = id. */
    val rootId: Long,

    /** Parent-Knoten (null bei HAUPT). */
    val parentId: Long?,

    /** Name der Hauptbaustelle (EXAKT wie eingegeben). */
    val hauptbaustellenName: String,

    /** Optionaler Unterbaustellen-/Objektname (z.B. "Kabelkanal", "Schacht 1"). */
    val knotenName: String? = null,

    /** Verknüpfung zur alten Tabelle (für Streckenpunkte/Fotos). */
    val altBaustelleId: Long,

    val typ: BaustellenKnotenTyp,

    // FREIE STRECKE
    val streckenkennung: String? = null,
    val kmVon: Double? = null,
    val kmBis: Double? = null,

    // BAHNHOF
    val bahnhofName: String? = null,
    val gleisOderWeiche: String? = null,

    /** Zeitstempel zur Sortierung. */
    val erstelltAmEpochMs: Long = System.currentTimeMillis()
)
