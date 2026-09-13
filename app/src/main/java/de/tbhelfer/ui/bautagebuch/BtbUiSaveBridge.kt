package de.tbhelfer.ui.bautagebuch

import android.view.View
import androidx.fragment.app.Fragment
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Kleine Brücke zwischen dem großen historischen BTB-Fragment und den neuen btbId-Komponenten.
 * Sie ersetzt die feste 1,7-s-Wartezeit: vorhandenen Speichern-Button auslösen und so lange prüfen,
 * bis genau derselbe aktive btbId tatsächlich mit einem neuen Änderungszeitpunkt geschrieben wurde.
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
        val baustelle = ServiceLocator.baustellenRepository.baustelleMitIdOnce(id) ?: return null
        val name = baustelle.baustellenname
        val context = fragment.requireContext().applicationContext
        val btbId = withContext(Dispatchers.IO) {
            BautagebuchSpeicher.aktiveBtbId(context, id, name)
        } ?: return null
        val entry = withContext(Dispatchers.IO) {
            BautagebuchSpeicher.ladenById(context, id, name, btbId)
        } ?: return null
        return AktiverBtb(id, name, btbId, entry)
    }

    suspend fun speichernUndNeuLaden(fragment: Fragment, root: View, timeoutMs: Long = 3_500L): AktiverBtb? {
        val before = aktuell(fragment) ?: return null
        val beforeChanged = before.entry.geaendertAmEpochMs

        fragment.requireActivity().currentFocus?.clearFocus()
        val saveButton = root.findViewById<View>(R.id.btnSpeichern) ?: return null
        saveButton.performClick()

        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < timeoutMs) {
            delay(100L)
            if (!fragment.isAdded || fragment.view == null) return null
            val now = aktuell(fragment) ?: continue
            if (now.btbId != before.btbId) return now
            if (now.entry.geaendertAmEpochMs > beforeChanged) return now
        }
        return null
    }
}
