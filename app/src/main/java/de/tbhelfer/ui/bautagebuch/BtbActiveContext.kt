package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.flow.first

/**
 * Eine gemeinsame Quelle fuer den aktuell aktiven modernen BTB einer Baustelle.
 *
 * Wichtig: Nicht Datum/Uhrzeit/Schicht entscheiden, sondern der aktive Pointer -> btbId.
 * Dadurch lesen Kamera, BTB-Kopf und Folgefunktionen immer denselben Datensatz.
 */
object BtbActiveContext {

    data class Result(
        val baustelleId: Long,
        val baustelleName: String,
        val btbId: String,
        val entry: BautagebuchEintrag
    )

    /** Liest den aktiven BTB der aktuell gesetzten Baustelle. */
    suspend fun laden(context: Context): Result? {
        val baustelleId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first() ?: return null
        return laden(context, baustelleId)
    }

    suspend fun laden(context: Context, baustelleId: Long): Result? {
        if (baustelleId <= 0L) return null
        val appContext = context.applicationContext
        val baustelle = ServiceLocator.baustellenRepository.baustelleMitIdOnce(baustelleId) ?: return null
        val name = baustelle.baustellenname.trim()
        if (name.isBlank()) return null

        val btbId = BautagebuchSpeicher.aktiveBtbId(appContext, baustelleId, name)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        // Ein lokal oder von einem Teamgeraet abgeschlossener BTB darf nicht ueber einen stale
        // Pointer weiter als "aktiv" erscheinen. Nur die ausdrueckliche Verlauf-Aktion
        // "BTB bearbeiten" setzt bewusstReaktivieren(...) und erlaubt denselben Abschluss wieder.
        val inactiveId = BtbActivePointerControl.nachAbschlussInaktiveBtbId(
            appContext, baustelleId, name
        )
        if (inactiveId == btbId) return null

        val entry = BautagebuchSpeicher.ladenById(appContext, baustelleId, name, btbId) ?: return null
        val abgeschlossen = entry.istFertig || entry.dokumentVersion > 0
        if (abgeschlossen && !BtbActivePointerControl.istBewusstReaktiviert(
                appContext,
                baustelleId,
                name,
                btbId
            )
        ) {
            BtbActivePointerControl.nachAbschlussInaktivMerken(
                appContext,
                baustelleId,
                name,
                btbId
            )
            return null
        }
        return Result(baustelleId, name, btbId, entry)
    }
}
