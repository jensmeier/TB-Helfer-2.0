package de.tbhelfer.ui.bautagebuch

import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Kleine Brücke zwischen dem großen historischen BTB-Fragment und den neuen btbId-Komponenten.
 *
 * Wichtig: Der alte versteckte Speichern-Button fuehrt eine UI-Validierung gegen private
 * Fragmentfelder aus. Nach externer btbId-basierter Bearbeitung (z. B. Arbeitszeit) koennen diese
 * Felder kurzfristig noch veraltet sein, obwohl der kanonische BTB-Datensatz bereits korrekt
 * gespeichert ist. Deshalb wird fuer Abschluss/Vorschau nicht mehr dieser Button gedrueckt.
 *
 * Die normalen Eingabebereiche speichern automatisch. Wenn beim Klick auf Abschluss/Vorschau noch
 * ein echtes Eingabefeld Fokus hatte, wartet diese Bruecke gezielt auf den neuen gespeicherten
 * Aenderungszeitpunkt desselben BTB. Ohne offene Eingabe wird der kanonische Datensatz sofort neu
 * gelesen. Es gibt damit keine feste Wartezeit als Identitaets-/Wechsellogik.
 */
object BtbUiSaveBridge {

    data class AktiverBtb(
        val baustelleId: Long,
        val baustelleName: String,
        val btbId: String,
        val entry: BautagebuchEintrag
    )

    suspend fun aktuell(fragment: Fragment): AktiverBtb? {
        val id = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first() ?: return null
        val context = fragment.requireContext().applicationContext
        val canonical = withContext(Dispatchers.IO) { BtbActiveContext.laden(context, id) } ?: return null
        return AktiverBtb(canonical.baustelleId, canonical.baustelleName, canonical.btbId, canonical.entry)
    }

    suspend fun speichernUndNeuLaden(fragment: Fragment, root: View, timeoutMs: Long = 4_500L): AktiverBtb? {
        val before = aktuell(fragment) ?: return null
        val beforeChanged = before.entry.geaendertAmEpochMs
        val focused = fragment.requireActivity().currentFocus
        val pendingUserInput = focused is EditText

        // Focus-Verlust beendet die konkrete Eingabe. Die vorhandenen TextWatcher-/Autosave-Wege
        // duerfen danach ihren normalen persistierenden Lauf abschliessen.
        focused?.clearFocus()

        if (!pendingUserInput) {
            // Kein offenes Textfeld: es ist nichts Neues abzuwarten. Direkt aus derselben btbId
            // neu lesen, damit externe Kopfdaten-Aenderungen ebenfalls sicher enthalten sind.
            delay(80L)
            return aktuell(fragment) ?: before
        }

        val started = System.currentTimeMillis()
        var latest: AktiverBtb = before
        while (System.currentTimeMillis() - started < timeoutMs) {
            delay(120L)
            if (!fragment.isAdded || fragment.view == null) return latest
            val now = aktuell(fragment) ?: continue
            if (now.btbId != before.btbId) return now
            latest = now
            if (now.entry.geaendertAmEpochMs > beforeChanged) return now
        }

        // Kein versteckter Alt-Save als Fallback: lieber den zuletzt kanonisch gespeicherten Stand
        // zurueckgeben als stale UI-Felder in denselben BTB zurueckzuschreiben.
        return latest
    }
}
