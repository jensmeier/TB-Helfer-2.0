package de.tbhelfer.ui.bautagebuch

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Blauer Schnellwechsel: ausschließlich echte BTBs, Auswahl/Öffnen immer über stabile btbId. */
object BtbSchnellwechselInstaller {

    private val deDateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun install(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    if (f is BautagebuchFragment) bind(f, v)
                }
            },
            true
        )
        activity.supportFragmentManager.fragments.forEach(::bindRecursively)
    }

    private fun bindRecursively(fragment: Fragment) {
        if (fragment is BautagebuchFragment) fragment.view?.let { bind(fragment, it) }
        runCatching { fragment.childFragmentManager.fragments }.getOrDefault(emptyList()).forEach(::bindRecursively)
    }

    private fun bind(fragment: BautagebuchFragment, root: View) {
        root.findViewById<View>(R.id.cardCurrentReport)?.setOnClickListener { anchor ->
            showExistingReports(fragment, root, anchor)
        }
    }

    private fun showExistingReports(fragment: BautagebuchFragment, root: View, anchor: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val altBaustelleId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
            if (altBaustelleId == null) {
                Snackbar.make(anchor, "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val baustelle = ServiceLocator.baustellenRepository.baustelleMitIdOnce(altBaustelleId)
            val baustelleName = baustelle?.baustellenname.orEmpty()
            if (baustelleName.isBlank()) {
                Snackbar.make(anchor, "Baustelle konnte nicht geladen werden", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val appContext = fragment.requireContext().applicationContext
            val data = withContext(Dispatchers.IO) {
                // Android 200038: inactive-after-completion beats any stale active pointer.
                val rawActiveId = BautagebuchSpeicher.aktiveBtbId(appContext, altBaustelleId, baustelleName)
                val inactiveId = BtbActivePointerControl.nachAbschlussInaktiveBtbId(
                    appContext, altBaustelleId, baustelleName
                )
                val activeId = rawActiveId?.takeUnless { it == inactiveId }
                val items = BautagebuchSpeicher.verlaufFuerBaustelle(appContext, altBaustelleId, baustelleName)
                activeId to items.map { item -> QuickItem(item, labelFor(appContext, item, activeId)) }
            }

            if (data.second.isEmpty()) {
                Snackbar.make(anchor, "Noch kein BTB vorhanden. Neuen BTB über + starten.", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle("BTB öffnen")
                .setItems(data.second.map { it.label }.toTypedArray()) { dialog, which ->
                    dialog.dismiss()
                    val selected = data.second.getOrNull(which) ?: return@setItems
                    if (!selected.item.btbId.isNullOrBlank() && selected.item.btbId == data.first) {
                        Snackbar.make(anchor, "Dieser BTB ist bereits geöffnet", Snackbar.LENGTH_SHORT).show()
                        return@setItems
                    }
                    switchTo(fragment, root, anchor, appContext, altBaustelleId, baustelleName, selected.item)
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    private fun labelFor(
        context: android.content.Context,
        item: BautagebuchSpeicher.VerlaufEintrag,
        activeId: String?
    ): String {
        val startDate = runCatching { LocalDate.parse(item.entry.datumIso) }.getOrDefault(item.datum)
        val endDate = item.entry.endeDatumIso?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        val date = if (endDate != null && endDate != startDate) {
            "${startDate.format(deDateFmt)}–${endDate.format(deDateFmt)}"
        } else startDate.format(deDateFmt)

        val time = when {
            !item.entry.startUhrzeit.isNullOrBlank() && !item.entry.endeUhrzeit.isNullOrBlank() ->
                "${item.entry.startUhrzeit}–${item.entry.endeUhrzeit}"
            !item.entry.startUhrzeit.isNullOrBlank() -> item.entry.startUhrzeit!!
            !item.entry.endeUhrzeit.isNullOrBlank() -> item.entry.endeUhrzeit!!
            else -> "ohne Zeit"
        }

        val status = when (BtbDokumentVersionierung.status(context, item.entry)) {
            BtbAbschlussStatus.ENTWURF -> "Entwurf"
            BtbAbschlussStatus.ABGESCHLOSSEN -> "Abgeschlossen"
            BtbAbschlussStatus.NACHBEARBEITUNG -> "Nachbearbeitung"
        }
        val current = if (!item.btbId.isNullOrBlank() && item.btbId == activeId) " · aktuell" else ""
        return "$date · $time · $status$current"
    }

    private fun switchTo(
        fragment: BautagebuchFragment,
        root: View,
        anchor: View,
        appContext: android.content.Context,
        altBaustelleId: Long,
        baustelleName: String,
        item: BautagebuchSpeicher.VerlaufEintrag
    ) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val current = BtbUiSaveBridge.aktuell(fragment)
            if (current != null) {
                Snackbar.make(anchor, "Aktuellen BTB speichern …", Snackbar.LENGTH_SHORT).show()
                val saved = BtbUiSaveBridge.speichernUndNeuLaden(fragment, root)
                if (saved == null) {
                    Snackbar.make(anchor, "Aktueller BTB konnte nicht gespeichert werden. Wechsel abgebrochen.", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    BtbDokumentVersionierung.pflegeNachAutosave(appContext, saved.entry)
                }
            }

            withContext(Dispatchers.IO) {
                item.btbId
                    ?.takeIf { it.isNotBlank() && !it.startsWith("legacy:") }
                    ?.let { id ->
                        BautagebuchSpeicher.aktivierenBtb(
                            appContext,
                            altBaustelleId,
                            baustelleName,
                            id,
                            item.datum,
                            item.schicht
                        )
                        // Bewusstes Oeffnen eines vorhandenen/abgeschlossenen BTB muss
                        // nicht nur den Inaktivmarker loeschen, sondern die Reaktivierung explizit
                        // markieren. Sonst kann der Lifecycle-Watcher den BTB direkt wieder loesen.
                        BtbActivePointerControl.bewusstReaktivieren(
                            appContext,
                            altBaustelleId,
                            baustelleName,
                            id
                        )
                    }
            }

            if (!fragment.isAdded || fragment.view == null) return@launch
            val args = Bundle().apply {
                putString("datumIso", item.datum.toString())
                putString("schichtTyp", item.schicht.name)
                putString("btbId", item.btbId)
                putLong("baustelleId", altBaustelleId)
            }
            val nav = fragment.findNavController()
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.bautagebuchFragment, true)
                .build()
            nav.navigate(R.id.bautagebuchFragment, args, options)
        }
    }

    private data class QuickItem(
        val item: BautagebuchSpeicher.VerlaufEintrag,
        val label: String
    )
}
