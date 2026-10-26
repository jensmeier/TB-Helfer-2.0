package de.tbhelfer.ui.einstellungen.diagnose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.server.ErrorHistory
import de.tbhelfer.server.ErrorLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ErrorLogDialog {
    fun show(fragment: Fragment, entryId: String? = null) {
        val app = fragment.requireContext().applicationContext
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { ErrorLogStore.entries(app) }
            val roots = withContext(Dispatchers.IO) {
                runCatching { ServiceLocator.worksiteBindingRepository.localMainWorksites() }.getOrDefault(emptyList())
            }
            if (!fragment.isAdded) return@launch
            val focused = entries.firstOrNull { it.id == entryId }
            if (focused != null) detail(fragment, focused, roots) else list(fragment, entries, roots)
        }
    }
    private fun list(fragment: Fragment, entries: List<ErrorHistory.Entry>, roots: List<BaustellenKnotenEntity>) {
        val builder = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Fehlerprotokoll · ${entries.size}/${ErrorHistory.LIMIT}")
            .setPositiveButton("Schließen", null)
        if (entries.isEmpty()) builder.setMessage("Noch keine gespeicherten Fehler. Neue Fehlermeldungen werden ab dieser Version erfasst.")
        else {
            builder.setItems(entries.map { ErrorHistory.summary(it) }.toTypedArray()) { _, index -> detail(fragment, entries[index], roots) }
            builder.setNeutralButton("Teilen") { _, _ -> share(fragment, ErrorHistory.export(entries)) }
            builder.setNegativeButton("Mehr") { _, _ ->
                MaterialAlertDialogBuilder(fragment.requireContext()).setTitle("Fehlerprotokoll")
                    .setItems(arrayOf("Alles kopieren", "Protokoll löschen")) { _, selected ->
                        if (selected == 0) copy(fragment, ErrorHistory.export(entries)) else confirmClear(fragment)
                    }.setPositiveButton("Zurück") { _, _ -> show(fragment) }.let { showBound(fragment, it) }
            }
        }
        showBound(fragment, builder)
    }
    private fun detail(fragment: Fragment, entry: ErrorHistory.Entry, roots: List<BaustellenKnotenEntity>) {
        val root = roots.firstOrNull {
            when (entry.contextKind) {
                ErrorHistory.ContextKind.ROOT -> it.id == entry.contextId
                ErrorHistory.ContextKind.SITE -> it.altBaustelleId == entry.contextId
                else -> false
            }
        }
        val site = root?.hauptbaustellenName?.let { "Baustelle: $it\n\n" }.orEmpty()
        MaterialAlertDialogBuilder(fragment.requireContext()).setTitle(entry.code.area)
            .setMessage(site + ErrorHistory.describe(entry))
            .setPositiveButton("Zur Liste") { _, _ -> show(fragment) }
            .setNeutralButton("Kopieren") { _, _ -> copy(fragment, ErrorHistory.describe(entry)) }
            .setNegativeButton("Teilen") { _, _ -> share(fragment, ErrorHistory.describe(entry)) }.let { showBound(fragment, it) }
    }
    private fun copy(fragment: Fragment, text: String) {
        val clipboard = fragment.requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TB-Helfer Fehlerprotokoll", text))
        Toast.makeText(fragment.requireContext(), "Fehlerprotokoll kopiert · ohne Baustellennamen und Fachinhalte", Toast.LENGTH_LONG).show()
    }
    private fun share(fragment: Fragment, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { fragment.startActivity(Intent.createChooser(intent, "Fehlerprotokoll teilen")) }
            .onFailure { Toast.makeText(fragment.requireContext(), "Keine passende App zum Teilen verfügbar.", Toast.LENGTH_LONG).show() }
    }
    private fun confirmClear(fragment: Fragment) {
        val app = fragment.requireContext().applicationContext
        MaterialAlertDialogBuilder(fragment.requireContext()).setTitle("Fehlerprotokoll löschen?")
            .setMessage("Nur die gespeicherten Fehler dieses Handybestands werden gelöscht. Baustellen, Einträge und Bilder bleiben erhalten.")
            .setNegativeButton("Abbrechen") { _, _ -> show(fragment) }
            .setPositiveButton("Löschen") { _, _ ->
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    val cleared = withContext(Dispatchers.IO) { ErrorLogStore.clear(app) }
                    if (!fragment.isAdded) return@launch
                    Toast.makeText(fragment.requireContext(), if (cleared) "Fehlerprotokoll gelöscht" else "Protokoll konnte nicht gelöscht werden.", Toast.LENGTH_LONG).show()
                    show(fragment)
                }
            }.let { showBound(fragment, it) }
    }
    private fun showBound(fragment: Fragment, builder: MaterialAlertDialogBuilder) {
        val lifecycle = fragment.viewLifecycleOwner.lifecycle
        val dialog = builder.create()
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener { lifecycle.removeObserver(observer) }
        dialog.show()
    }
}
