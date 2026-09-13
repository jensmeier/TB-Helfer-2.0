package de.tbhelfer.ui.bautagebuch

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.UserDisplayCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.WeakHashMap

/**
 * Verdrahtet das moderne BTB-Zielmodell ohne den historischen Gross-Fragmentcode zu duplizieren:
 * - Stift neben + fuer den aktuell geladenen BTB,
 * - Abschluss erst nach erfolgreichem PDF-Schreiben,
 * - genau ein aktuelles PDF pro btbId,
 * - erster Abschluss bleibt unveraenderliches Original fuer Text UND Foto-Bestand,
 * - Nachbearbeitung loescht das alte Abschlussdokument,
 * - kein sichtbarer Schein-Entwurf, solange noch gar kein BTB existiert.
 */
object BtbModernLifecycleInstaller {

    private val boundViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

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
        addEditButton(fragment, root)
        bindFinalButton(fragment, root)
        startLifecycleWatcher(fragment, root)
    }

    private fun addEditButton(fragment: BautagebuchFragment, root: View) {
        val bar = root.findViewById<LinearLayout>(R.id.reportQuickBar) ?: return
        val plus = root.findViewById<View>(R.id.btnQuickNewBtb) ?: return
        val density = root.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = MaterialCardView(fragment.requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            radius = dp(12).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.rgb(251, 140, 0))
            isClickable = true
            isFocusable = true
            contentDescription = "Aktuellen BTB bearbeiten"
        }
        val frame = FrameLayout(fragment.requireContext())
        val icon = ImageView(fragment.requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
            contentDescription = "Bearbeiten"
        }
        frame.addView(icon)
        card.addView(frame)
        card.setOnClickListener { showEditMenu(fragment, root, card) }

        val plusIndex = bar.indexOfChild(plus).coerceAtLeast(0)
        bar.addView(card, plusIndex)
        bar.addView(Space(fragment.requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), 1)
        }, plusIndex + 1)
    }

    private fun showEditMenu(fragment: BautagebuchFragment, root: View, anchor: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val current = BtbUiSaveBridge.aktuell(fragment)
            if (current == null) {
                Snackbar.make(anchor, "Noch kein aktueller BTB vorhanden. Neuen BTB über + starten.", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val status = withContext(Dispatchers.IO) {
                BtbDokumentVersionierung.status(fragment.requireContext().applicationContext, current.entry)
            }
            val message = if (status == BtbAbschlussStatus.ABGESCHLOSSEN) {
                "Dieser BTB ist abgeschlossen. Nur das Öffnen ändert noch nichts. Erst eine tatsächlich gespeicherte Änderung macht das Abschluss-PDF ungültig. Das erste Original bleibt für die Rot-Markierung erhalten."
            } else {
                "Es wird immer derselbe BTB mit derselben btbId bearbeitet."
            }
            val options = arrayOf(
                "Datum / Zeit bearbeiten",
                "Personal bearbeiten",
                "Geräte / Maschinen bearbeiten",
                "Wetter bearbeiten"
            )
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle("Aktuellen BTB bearbeiten")
                .setMessage(message)
                .setItems(options) { dialog, which ->
                    dialog.dismiss()
                    openExistingEditor(root, which)
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    private fun openExistingEditor(root: View, which: Int) {
        val toggle = root.findViewById<TextView>(R.id.tvTopFieldsToggle)
        if (toggle?.text?.toString()?.contains("anzeigen", ignoreCase = true) == true) {
            toggle.performClick()
        }
        val targetId = when (which) {
            0 -> R.id.btnDateTime
            1 -> R.id.tvPersonalPreview
            2 -> R.id.tvMaschinenPreview
            else -> R.id.tvWetterPreview
        }
        root.post { root.findViewById<View>(targetId)?.performClick() }
    }

    private fun bindFinalButton(fragment: BautagebuchFragment, root: View) {
        val finalButton = root.findViewById<MaterialButton>(R.id.btnPdfFinal) ?: return
        finalButton.text = "BTB Abschluss"
        finalButton.setOnClickListener { anchor -> closeCurrentBtb(fragment, root, anchor) }
        finalButton.setOnLongClickListener { anchor ->
            Snackbar.make(anchor, "ZIP bitte beim gewünschten BTB im BTB-Verlauf teilen.", Snackbar.LENGTH_LONG).show()
            true
        }
    }

    private fun closeCurrentBtb(fragment: BautagebuchFragment, root: View, anchor: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            Snackbar.make(anchor, "BTB wird gespeichert und abgeschlossen …", Snackbar.LENGTH_LONG).show()
            val saved = BtbUiSaveBridge.speichernUndNeuLaden(fragment, root)
            if (saved == null) {
                Snackbar.make(anchor, "BTB konnte nicht vollständig gespeichert werden. Bitte Eingaben prüfen.", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val appContext = fragment.requireContext().applicationContext
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Bei echter Nachbearbeitung ist das vorige PDF ungueltig. Das erste Original
                    // bleibt dagegen dauerhaft erhalten und wird fuer den Renderer gespiegelt.
                    BtbDokumentVersionierung.pflegeNachAutosave(appContext, saved.entry)
                    BtbRendererBaselineBridge.prepare(appContext, saved.entry)

                    // Nur die PDF-Darstellung ergaenzen: geloeschte Originalzeilen erscheinen als
                    // rote "(entfernt) ..."-Eintraege. Der gespeicherte BTB bleibt wirklich geloescht.
                    val renderEntry = BtbRenderOriginalDiff.aufbereiten(appContext, saved.entry)

                    BtbFotoRenderMarkierung.clear()
                    val pdfEngine = BautagebuchPdf(appContext, ServiceLocator.fotoRepository)
                    // Noch ohne Render-Overrides laden, damit wir die fachliche Foto-Menge bestimmen.
                    val alleFotos = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(saved.entry.baustelleId)
                    // BTBs aus 200026 bekommen ihre Foto-Basis einmalig aus dem Zeitpunkt des bereits
                    // vorhandenen unveraenderlichen Text-Originals rekonstruiert.
                    BtbOriginalFotoSpeicher.migriereAusBestehendemOriginalWennFehlt(appContext, saved.entry, alleFotos)
                    val aktuelleFotoIds = BtbOriginalFotoSpeicher.aktuelleFotoIds(appContext, saved.entry, alleFotos)
                    val roteFotoIds = BtbOriginalFotoSpeicher.neueFotoIds(appContext, saved.entry.btbId, aktuelleFotoIds)
                    BtbFotoRenderMarkierung.vorbereiten(appContext, alleFotos, roteFotoIds)

                    val file = try {
                        BtbFotoBtbFilter.mitFilter(appContext, saved.entry, alleFotos) {
                            pdfEngine.erstellenPdf(renderEntry, asDraft = false)
                        }
                    } finally {
                        BtbFotoRenderMarkierung.clear()
                    }

                    val user = UserDisplayCache.userName(appContext)?.trim().orEmpty().ifBlank { "TB" }
                    if (!BtbDokumentVersionierung.abschlussBestaetigen(appContext, saved.entry, file, user)) {
                        throw IllegalStateException("Abschluss konnte nicht bestätigt werden")
                    }
                    // Erst nach erfolgreichem allerersten Abschluss die Foto-Basis festschreiben.
                    // Spaetere Abschluesse duerfen diese Menge nie ersetzen.
                    BtbOriginalFotoSpeicher.sichernWennFehlt(appContext, saved.entry.btbId, aktuelleFotoIds)
                    pdfEngine.deleteEntwurfPdf(saved.entry)
                    file
                }.also {
                    // Auch bei unerwartetem Fehler nie temporaere Bildkopien liegen lassen.
                    BtbFotoRenderMarkierung.clear()
                }
            }

            if (!fragment.isAdded || fragment.view == null) return@launch
            result.onSuccess { file ->
                root.findViewById<TextView>(R.id.tvCurrentReportLine2)?.text = "Abgeschlossen"
                Snackbar.make(anchor, "BTB abgeschlossen", Snackbar.LENGTH_SHORT).show()
                fragment.startActivity(Intent(fragment.requireContext(), BtbPdfViewerActivity::class.java).apply {
                    putExtra(BtbPdfViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                    putExtra(BtbPdfViewerActivity.EXTRA_TITLE, "BTB-PDF")
                })
            }.onFailure { error ->
                Snackbar.make(anchor, "BTB-Abschluss fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun startLifecycleWatcher(fragment: BautagebuchFragment, root: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastSeenId: String? = null
                var lastSeenChange: Long = Long.MIN_VALUE
                while (isActive) {
                    val current = runCatching { BtbUiSaveBridge.aktuell(fragment) }.getOrNull()
                    if (current == null) {
                        // Der historische Fragment-Banner zeigt sonst auch ohne Datei "Entwurf".
                        // Fachlich existiert aber erst nach dem grünen + ein BTB.
                        root.findViewById<TextView>(R.id.tvCurrentReportLine1)?.text = "Noch kein BTB"
                        root.findViewById<TextView>(R.id.tvCurrentReportLine2)?.text = ""
                        lastSeenId = null
                        lastSeenChange = Long.MIN_VALUE
                    } else {
                        val changedStamp = current.entry.geaendertAmEpochMs
                        if (current.btbId != lastSeenId || changedStamp != lastSeenChange) {
                            withContext(Dispatchers.IO) {
                                BtbDokumentVersionierung.pflegeNachAutosave(
                                    fragment.requireContext().applicationContext,
                                    current.entry
                                )
                            }
                            lastSeenId = current.btbId
                            lastSeenChange = changedStamp
                        }
                        val status = withContext(Dispatchers.IO) {
                            BtbDokumentVersionierung.status(fragment.requireContext().applicationContext, current.entry)
                        }
                        root.findViewById<TextView>(R.id.tvCurrentReportLine2)?.text = when (status) {
                            BtbAbschlussStatus.ENTWURF -> "Entwurf"
                            BtbAbschlussStatus.ABGESCHLOSSEN -> "Abgeschlossen"
                            BtbAbschlussStatus.NACHBEARBEITUNG -> "Nachbearbeitung · Abschluss-PDF ungültig"
                        }
                    }
                    delay(600L)
                }
            }
        }
    }
}
