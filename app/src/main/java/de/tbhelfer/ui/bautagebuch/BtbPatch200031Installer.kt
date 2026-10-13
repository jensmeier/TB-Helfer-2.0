package de.tbhelfer.ui.bautagebuch

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.WeakHashMap

/**
 * Kleiner Folge-Installer fuer Android 200031.
 *
 * Bewusst ausserhalb des historischen BautagebuchFragment:
 * - PDF Entwurf oeffnet sofort den internen Viewer,
 * - Arbeitszeit wird direkt am selben btbId gespeichert und danach sauber neu geladen,
 * - Wetter-Autofill ist nach dem ersten Abschluss gesperrt; manuelle Nachbearbeitung bleibt ueber
 *   den orangefarbenen Stift moeglich.
 */
object BtbPatch200031Installer {

    private val boundViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

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
        if (!boundViews.add(root)) return
        bindDraftButton(fragment, root)
        bindStableTimeEditor(fragment, root)
        lockWeatherAutofillAfterFirstClosure(fragment, root)
    }

    private fun bindDraftButton(fragment: BautagebuchFragment, root: View) {
        val button = root.findViewById<MaterialButton>(R.id.btnPdf) ?: return
        button.text = "PDF Entwurf"
        button.setOnClickListener { anchor ->
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val saved = BtbUiSaveBridge.speichernUndNeuLaden(fragment, root)
                if (saved == null) {
                    Snackbar.make(anchor, "Kein BTB zum Anzeigen vorhanden.", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                Snackbar.make(anchor, "PDF-Entwurf wird erstellt …", Snackbar.LENGTH_SHORT).show()
                val appContext = fragment.requireContext().applicationContext
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        BtbDokumentVersionierung.pflegeNachAutosave(appContext, saved.entry)
                        BtbRendererBaselineBridge.prepare(appContext, saved.entry)
                        val renderEntry = BtbRenderOriginalDiff.aufbereiten(appContext, saved.entry)
                            .copy(includeFotos = true, includeBtbs = true)

                        val alleFotos = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(saved.entry.baustelleId)
                        BtbOriginalFotoSpeicher.migriereAusBestehendemOriginalWennFehlt(appContext, saved.entry, alleFotos)
                        val aktuelleFotoIds = BtbOriginalFotoSpeicher.aktuelleFotoIds(appContext, saved.entry, alleFotos)
                        val roteFotoIds = BtbOriginalFotoSpeicher.neueFotoIds(appContext, saved.entry.btbId, aktuelleFotoIds)
                        BtbFotoRenderMarkierung.clear()
                        BtbFotoRenderMarkierung.vorbereiten(appContext, alleFotos, roteFotoIds)
                        val pdfEngine = BautagebuchPdf(appContext, ServiceLocator.fotoRepository)
                        try {
                            BtbFotoBtbFilter.mitFilter(appContext, saved.entry, alleFotos) {
                                pdfEngine.erstellenPdf(renderEntry, asDraft = true)
                            }
                        } finally {
                            BtbFotoRenderMarkierung.clear()
                        }
                    }
                }
                if (!fragment.isAdded || fragment.view == null) return@launch
                result.onSuccess { file ->
                    fragment.startActivity(Intent(fragment.requireContext(), BtbPdfViewerActivity::class.java).apply {
                        putExtra(BtbPdfViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                        putExtra(BtbPdfViewerActivity.EXTRA_TITLE, "BTB-PDF · Entwurf")
                    })
                }.onFailure { error ->
                    Snackbar.make(anchor, "PDF-Entwurf fehlgeschlagen: ${error.message ?: "Fehler"}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Ueberschreibt nur den Klick auf das vorhandene Datum/Zeit-Feld. Dadurch landet auch der
     * moderne Stift-Dialog direkt hier und nicht mehr im alten Popup mit '+ Neuer BTB/Entfernen'.
     */
    private fun bindStableTimeEditor(fragment: BautagebuchFragment, root: View) {
        val dateTime = root.findViewById<View>(R.id.btnDateTime) ?: return
        dateTime.setOnClickListener { anchor -> showTimeDialog(fragment, root, anchor) }
    }

    private fun showTimeDialog(fragment: BautagebuchFragment, root: View, anchor: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val current = BtbUiSaveBridge.aktuell(fragment)
            if (current == null) {
                Snackbar.make(anchor, "Noch kein aktueller BTB vorhanden.", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val entry = current.entry
            var start = parseTime(entry.startUhrzeit) ?: LocalTime.now().withSecond(0).withNano(0)
            var end = parseTime(entry.endeUhrzeit) ?: start.plusHours(8)
            val ctx = fragment.requireContext()
            val density = root.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(4), dp(20), dp(4))
            }
            content.addView(TextView(ctx).apply {
                text = "Datum: ${entry.datumIso}\nDie btbId bleibt unverändert."
                textSize = 12f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, 0, dp(8))
            })

            fun row(label: String, initial: LocalTime, onChanged: (LocalTime) -> Unit): MaterialButton {
                val line = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                line.addView(TextView(ctx).apply {
                    text = label
                    textSize = 14f
                    setTextColor(Color.BLACK)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                val btn = MaterialButton(ctx).apply {
                    text = initial.format(timeFmt)
                    isAllCaps = false
                }
                btn.setOnClickListener {
                    val now = parseTime(btn.text?.toString()) ?: initial
                    TimePickerDialog(ctx, { _, h, m ->
                        val selected = LocalTime.of(h, m)
                        btn.text = selected.format(timeFmt)
                        onChanged(selected)
                    }, now.hour, now.minute, true).show()
                }
                line.addView(btn)
                content.addView(line)
                return btn
            }

            val startButton = row("Beginn", start) { start = it }
            val endButton = row("Ende", end) { end = it }

            val dialog = MaterialAlertDialogBuilder(ctx)
                .setTitle("Datum/Zeit bearbeiten")
                .setView(content)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    start = parseTime(startButton.text?.toString()) ?: start
                    end = parseTime(endButton.text?.toString()) ?: end
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        val baseDate = runCatching { LocalDate.parse(entry.datumIso) }.getOrNull()
                        val endDate = baseDate?.let { if (!end.isAfter(start)) it.plusDays(1) else it }
                        val updated = entry.copy(
                            startUhrzeit = start.format(timeFmt),
                            endeUhrzeit = end.format(timeFmt),
                            endeDatumIso = endDate?.toString(),
                            geaendertAmEpochMs = System.currentTimeMillis()
                        )
                        withContext(Dispatchers.IO) {
                            BautagebuchSpeicher.speichern(ctx.applicationContext, updated)
                            BtbDokumentVersionierung.pflegeNachAutosave(ctx.applicationContext, updated)
                        }
                        dialog.dismiss()
                        if (!fragment.isAdded) return@launch
                        Snackbar.make(anchor, "Arbeitszeit gespeichert", Snackbar.LENGTH_SHORT).show()
                        // Der historische Fragmentcode haelt Start/Ende privat im Speicher. View neu
                        // anbinden, damit ein spaeterer Autosave die eben gespeicherte Zeit nicht
                        // wieder mit den alten Werten ueberschreibt.
                        fragment.parentFragmentManager.beginTransaction()
                            .detach(fragment)
                            .attach(fragment)
                            .commitAllowingStateLoss()
                    }
                }
            }
            dialog.show()
        }
    }

    /**
     * Vor dem ersten Abschluss darf die bisherige Wetter-Automatik weiter benutzt werden. Sobald
     * ein Original existiert, ist die Wetter-Vorschau nicht mehr klickbar und kann daher keinen
     * automatischen Refresh ausloesen. Manuelle Wetter-Nachbearbeitung erfolgt ueber den Stift.
     */
    private fun lockWeatherAutofillAfterFirstClosure(fragment: BautagebuchFragment, root: View) {
        val preview = root.findViewById<View>(R.id.tvWetterPreview) ?: return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val current = runCatching { BtbUiSaveBridge.aktuell(fragment) }.getOrNull()
                    val locked = if (current == null) false else withContext(Dispatchers.IO) {
                        BtbOriginalFotoSpeicher.hatOriginal(fragment.requireContext().applicationContext, current.btbId) ||
                            BtbDokumentVersionierung.status(fragment.requireContext().applicationContext, current.entry) != BtbAbschlussStatus.ENTWURF
                    }
                    preview.isClickable = !locked
                    preview.contentDescription = if (locked) {
                        "Wetter nach BTB-Abschluss nur über Bearbeiten-Stift ändern"
                    } else {
                        "Wetter öffnen und aktualisieren"
                    }
                    delay(700L)
                }
            }
        }
    }

    private fun parseTime(value: String?): LocalTime? = value
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() }
}
