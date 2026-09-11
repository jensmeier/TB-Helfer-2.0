package de.tbhelfer.ui.baustelle

import de.tbhelfer.datenbank.BaustellenKnotenEntity

/**
 * UI-Modell für Listen/Accordion.
 *
 * Variante B (gewünschte Labels):
 * - Hauptliste: "Unterbaustellen: X" + "Gesamt: Y"
 * - Detail/Accordion: "Unterpunkte: X" + "Gesamt: Y"
 */
enum class UnterLabel {
    UNTERBAUSTELLEN,
    UNTERPUNKTE
}

data class BaustellenKnotenUiModell(
    val knoten: BaustellenKnotenEntity,
    val istAktuell: Boolean,
    val ebene: Int = 0,
    val unterCount: Int = 0,
    val gesamtCount: Int = 0,
    val bilderCount: Int = 0,
    val btbCount: Int = 0,
    val mangelOffenCount: Int = 0,
    val mangelInArbeitCount: Int = 0,
    val mangelErledigtCount: Int = 0,
    val mangelAbgelehntCount: Int = 0,
    val abnahmeJaCount: Int = 0,
    val abnahmeNeinCount: Int = 0,
    val abnahmePruefungCount: Int = 0,
    /** Null = lokal noch mit keiner Server-Baustelle verknüpft bzw. noch kein Serverstand bekannt. */
    val teamServerVerbunden: Boolean = false,
    val verantwortlicheTbCount: Int = 0,
    val helferCount: Int = 0,
    val letzterBaustellenSyncEpochMs: Long? = null,
    val unterLabel: UnterLabel = UnterLabel.UNTERPUNKTE,
    val istAufklappbar: Boolean = false,
    val istAufgeklappt: Boolean = false
)
