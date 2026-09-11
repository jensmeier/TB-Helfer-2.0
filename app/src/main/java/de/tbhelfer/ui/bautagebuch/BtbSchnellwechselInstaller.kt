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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Bindet den blauen "aktueller BTB"-Balken an das neue Multi-BTB-Modell.
 *
 * Der große historische [BautagebuchFragment]-Quelltext enthält noch den alten 7-Tage-
 * TAG/NACHT-Dialog. Dieser Installer ersetzt ausschließlich dessen Click-Listener, nachdem die
 * Fragment-View aufgebaut wurde. Dadurch wird produktiv nur noch die vorhandene
 * [BautagebuchSpeicher.verlaufFuerBaustelle]-Liste verwendet:
 *
 * - keine künstlichen "Neu (leer)"-Einträge,
 * - mehrere BTBs desselben Tages bleiben einzeln sichtbar,
 * - moderne BTBs werden über ihre stabile btbId aktiviert,
 * - neue BTBs entstehen weiterhin ausschließlich über den grünen Plus-Ablauf.
 *
 * Sobald BautagebuchFragment in kleinere Komponenten zerlegt wird, kann diese Bindung direkt
 * dorthin verschoben und der alte Dialog endgültig entfernt werden.
 */
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

        // Bei Activity-Restore kann der aktuelle NavHost-Inhalt bereits vor der Registrierung
        // aufgebaut worden sein. Deshalb vorhandene Fragmente einmal rekursiv nachziehen.
        activity.supportFragmentManager.fragments.forEach(::bindRecursively)
    }

    private fun bindRecursively(fragment: Fragment) {
        if (fragment is BautagebuchFragment) {
            fragment.view?.let { bind(fragment, it) }
        }
        fragment.childFragmentManager.fragments.forEach(::bindRecursively)
    }

    private fun bind(fragment: BautagebuchFragment, root: View) {
        root.findViewById<View>(R.id.cardCurrentReport)?.setOnClickListener { anchor ->
            showExistingReports(fragment, anchor)
        }
    }

    private fun showExistingReports(fragment: BautagebuchFragment, anchor: View) {
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
                val activeId = BautagebuchSpeicher.aktiveBtbId(appContext, altBaustelleId, baustelleName)
                val items = BautagebuchSpeicher.verlaufFuerBaustelle(
                    appContext,
                    altBaustelleId,
                    baustelleName
                )
                activeId to items.map { item ->
                    QuickItem(
                        item = item,
                        label = labelFor(appContext, item, activeId)
                    )
                }
            }

            if (data.second.isEmpty()) {
                Snackbar.make(
                    anchor,
                    "Noch kein BTB vorhanden. Neuen BTB über + starten.",
                    Snackbar.LENGTH_LONG
                ).show()
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
                    switchTo(fragment, anchor, altBaustelleId, baustelleName, selected.item)
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
        val endDate = item.entry.endeDatumIso
            ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        val date = if (endDate != null && endDate != startDate) {
            "${startDate.format(deDateFmt)}–${endDate.format(deDateFmt)}"
        } else {
            startDate.format(deDateFmt)
        }
        val time = when {
            !item.entry.startUhrzeit.isNullOrBlank() && !item.entry.endeUhrzeit.isNullOrBlank() ->
                "${item.entry.startUhrzeit}–${item.entry.endeUhrzeit}"
            !item.entry.startUhrzeit.isNullOrBlank() -> item.entry.startUhrzeit!!
            !item.entry.endeUhrzeit.isNullOrBlank() -> item.entry.endeUhrzeit!!
            else -> "ohne Zeit"
        }

        val confirmed = BtbDokumentVersionierung.finalisierteVersion(context, item.entry)
        val version = maxOf(
            confirmed,
            item.entry.dokumentVersion,
            if (item.baselineExists) 1 else 0
        )
        val changed = version > 0 &&
            BtbDokumentVersionierung.istSeitLetztemAbschlussGeaendert(context, item.entry)
        val status = when {
            changed -> "Geändert · nächster Abschluss V${version + 1}"
            version > 0 || item.entry.istFertig -> "Abgeschlossen · V${version.coerceAtLeast(1)}"
            else -> "Entwurf"
        }
        val current = if (!item.btbId.isNullOrBlank() && item.btbId == activeId) " · aktuell" else ""
        return "$date · $time · $status$current"
    }

    private fun switchTo(
        fragment: BautagebuchFragment,
        anchor: View,
        altBaustelleId: Long,
        baustelleName: String,
        item: BautagebuchSpeicher.VerlaufEintrag
    ) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            // Textfelder verlieren zuerst den Fokus. Die vorhandenen BTB-Autosaves arbeiten mit
            // 1,5 s Idle-Delay; wir lassen ihnen vor dem Fragmentwechsel bewusst Zeit, damit ein
            // unmittelbar zuvor geänderter Wert nicht durch Navigation verworfen wird.
            fragment.requireActivity().currentFocus?.clearFocus()
            Snackbar.make(anchor, "Aktuellen BTB speichern…", Snackbar.LENGTH_SHORT).show()
            delay(1_700L)

            withContext(Dispatchers.IO) {
                item.btbId
                    ?.takeIf { it.isNotBlank() && !it.startsWith("legacy:") }
                    ?.let { id ->
                        BautagebuchSpeicher.aktivierenBtb(
                            fragment.requireContext().applicationContext,
                            altBaustelleId,
                            baustelleName,
                            id,
                            item.datum,
                            item.schicht
                        )
                    }
            }

            if (!fragment.isAdded) return@launch
            val args = Bundle().apply {
                putString("datumIso", item.datum.toString())
                putString("schichtTyp", item.schicht.name)
                putString("btbId", item.btbId)
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
