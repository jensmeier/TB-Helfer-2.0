package de.tbhelfer.ui.bautagebuch

import android.view.View
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
 * Die normalen Eingabebereiche speichern ohnehin automatisch. Wir geben ihnen nach Focus-Verlust
 * kurz Zeit und lesen danach ausschliesslich denselben aktiven BTB ueber seine btbId neu ein.
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

    suspend fun speichernUndNeuLaden(fragment: Fragment, root: View, timeoutMs: Long = 3_500L): AktiverBtb? {
        val before = aktuell(fragment) ?: return null
        val beforeChanged = before.entry.geaendertAmEpochMs

        // Focus-Verlust triggert/deblockiert die vorhandenen Autosave-Wege.
        fragment.requireActivity().currentFocus?.clearFocus()

        val started = System.currentTimeMillis()
        var latest: AktiverBtb = before
        while (System.currentTimeMillis() - started < timeoutMs) {
            delay(120L)
            if (!fragment.isAdded || fragment.view == null) return latest
            val now = aktuell(fragment) ?: continue
            if (now.btbId != before.btbId) return now
            latest = now
            if (now.entry.geaendertAmEpochMs > beforeChanged) return now

            // Ohne neue Aenderung gibt es nichts abzuwarten. Etwas Puffer bleibt fuer einen gerade
            // laufenden TextWatcher-/Focus-Autosave, ohne erneut eine feste 1,7-s-Wechselwartezeit
            // als Datenintegritaetsmechanismus einzufuehren.
            if (System.currentTimeMillis() - started >= 900L) return latest
        }
        return latest
    }
}
