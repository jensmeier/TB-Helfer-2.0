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

    suspend fun speichernUndNeuLaden(fragment: Fragment, root: View, timeoutMs: Long = 4_500L, requireConfirmedSave: Boolean = false): AktiverBtb? {
        val before = aktuell(fragment) ?: return null
        val beforeChanged = before.entry.geaendertAmEpochMs
        val focused = fragment.requireActivity().currentFocus
        val pendingUserInput = focused is EditText

        focused?.clearFocus()

        if (!pendingUserInput) {
            delay(80L)
            return aktuell(fragment) ?: before
        }

        val started = System.currentTimeMillis()
        var latest: AktiverBtb = before
        while (System.currentTimeMillis() - started < timeoutMs) {
            delay(120L)
            if (!fragment.isAdded || fragment.view == null) return if (requireConfirmedSave) null else latest
            val now = aktuell(fragment) ?: continue
            if (now.btbId != before.btbId) return if (requireConfirmedSave) null else now
            latest = now
            if (now.entry.geaendertAmEpochMs > beforeChanged) return now
        }

        return if (requireConfirmedSave) null else latest
    }
}

