package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ServiceLocator

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
        val entry = BautagebuchSpeicher.ladenById(appContext, baustelleId, name, btbId) ?: return null
        return Result(baustelleId, name, btbId, entry)
    }
}
