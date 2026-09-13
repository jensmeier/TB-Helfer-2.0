package de.tbhelfer.ui.bautagebuch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.UserDisplayCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.WeakHashMap

/**
 * Android 200033:
 * - erfolgreicher BTB-Abschluss loest den aktiven Pointer; der BTB bleibt nur im Verlauf gespeichert,
 * - absichtlich aus dem Verlauf geoeffnete abgeschlossene BTBs duerfen wieder aktiv werden,
 * - Kamera ohne aktiven BTB fragt: Nur Baustellenfoto oder BTB starten/oeffnen,
 * - Details-Dialog verwendet echte Day/Night-Theme-Farben,
 * - verhindert ungefragtes Wiederaktivieren des gerade abgeschlossenen BTB durch Legacy-Ladewege.
 */
object BtbPatch200033Installer {

    private val boundViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private const val EDIT_BUTTON_TAG = "tb_modern_edit_button"
    private const val EDIT_SPACER_TAG = "tb_modern_edit_spacer"

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
        bindReadonlyDetails(fragment, root)
        bindQuickCamera(fragment, root)
        bindFinalButton(fragment, root)
        startActivePointerGuard(fragment, root)
    }

    private fun bindReadonlyDetails(fragment: BautagebuchFragment, root: View) {
        val action = View.OnClickListener {
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val current = BtbUiSaveBridge.aktuell(fragment)
                if (current == null) {
                    Snackbar.make(root, "Noch kein BTB aktiv", Snackbar.LENGTH_SHORT).show()
                    return@launch
                }
                showReadonlyDetails(fragment, current.entry)
            }
        }
        root.findViewById<View>(R.id.topFieldsToggleRow)?.setOnClickListener(action)
        root.findViewById<View>(R.id.tvTopFieldsToggle)?.setOnClickListener(action)
    }

    private fun showReadonlyDetails(fragment: BautagebuchFragment, entry: BautagebuchEintrag) {
        val ctx = fragment.requireContext()
        val primary = ContextCompat.getColor(ctx, R.color.tb_on_surface)
        val secondary = ContextCompat.getColor(ctx, R.color.tb_text_secondary)
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(2))
        }

        fun addBlock(title: String, value: String) {
            content.addView(TextView(ctx).apply {
                text = title
                textSize = 13f
                setTextColor(primary)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            })
            content.addView(TextView(ctx).apply {
                text = value.ifBlank { "Keine Angaben" }
                textSize = 12f
                setTextColor(secondary)
                setPadding(0, dp(2), 0, dp(3))
            })
        }

        val dateTime = buildString {
            append(entry.datumIso)
            append(" · ")
            append(entry.startUhrzeit ?: "--:--")
            append("–")
            append(entry.endeUhrzeit ?: "--:--")
        }
        val personal = entry.firmen
            .filter { it.visibleInPersonal && !it.isDeleted }
            .joinToString("\n") { f ->
                val rows = f.personal.joinToString(", ") { p ->
                    listOfNotNull(p.anzahl?.toString(), p.rolle.takeIf { it.isNotBlank() }).joinToString(" ")
                }
                listOf(f.name, rows).filter { it.isNotBlank() }.joinToString(": ")
            }
            .ifBlank { entry.personal.filter { it.isNotBlank() }.joinToString("\n") }
        val machines = entry.firmen
            .filter { it.visibleInMaschinen && !it.isDeleted }
            .joinToString("\n") { f ->
                val rows = f.geraete.joinToString(", ") { g ->
                    listOfNotNull(g.anzahl?.toString(), g.bezeichnung.takeIf { it.isNotBlank() }).joinToString(" ")
                }
                listOf(f.name, rows).filter { it.isNotBlank() }.joinToString(": ")
            }
            .ifBlank { entry.geraete.filter { it.isNotBlank() }.joinToString("\n") }
        val weather = (entry.weather?.slots?.takeIf { it.isNotEmpty() } ?: entry.weatherSlots)
            .joinToString("\n") { slot ->
                listOfNotNull(
                    slot.uhrzeit.takeIf { it.isNotBlank() },
                    slot.tempC?.let { "$it °C" },
                    slot.bewoelkung?.takeIf { it.isNotBlank() },
                    slot.niederschlag?.takeIf { it.isNotBlank() },
                    slot.niederschlagMm?.let { "${String.format(java.util.Locale.GERMANY, "%.1f", it)} l/m²" }
                ).joinToString(" · ")
            }

        addBlock("Datum/Zeit", dateTime)
        addBlock("Personal", personal)
        addBlock("Geräte/Maschinen", machines)
        addBlock("Wetter", weather)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("BTB-Details")
            .setView(content)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun bindQuickCamera(fragment: BautagebuchFragment, root: View) {
        root.findViewById<View>(R.id.btnQuickCamera)?.setOnClickListener {
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val current = BtbActiveContext.laden(fragment.requireContext().applicationContext)
                if (current != null) {
                    fragment.findNavController().navigate(R.id.kameraFragment)
                    return@launch
                }
                showCameraWithoutBtbDialog(fragment, root)
            }
        }
    }

    private fun showCameraWithoutBtbDialog(fragment: BautagebuchFragment, root: View) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Kein BTB aktiv")
            .setMessage("Wie soll das Foto gespeichert werden?")
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("Nur Baustellenfoto") { _, _ ->
                fragment.findNavController().navigate(R.id.kameraFragment)
            }
            .setPositiveButton("BTB starten/öffnen") { _, _ ->
                MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle("BTB auswählen")
                    .setItems(arrayOf("Neuen BTB starten", "Vorhandenen BTB öffnen")) { _, which ->
                        when (which) {
                            0 -> root.findViewById<View>(R.id.btnQuickNewBtb)?.performClick()
                            else -> openBtbHistory(fragment)
                        }
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
            .show()
    }

    private fun openBtbHistory(fragment: BautagebuchFragment) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val siteId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
            if (siteId == null) {
                Snackbar.make(fragment.requireView(), "Keine aktuelle Baustelle", Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            val site = withContext(Dispatchers.IO) { ServiceLocator.baustellenRepository.baustelleMitIdOnce(siteId) }
            val hauptId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
                ?: withContext(Dispatchers.IO) { ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(siteId)?.id }
                ?: -1L
            fragment.findNavController().navigate(
                R.id.btbVerlaufFragment,
                Bundle().apply {
                    putLong("hauptKnotenId", hauptId)
                    putLong("altBaustelleId", siteId)
                    putString("baustelleName", site?.baustellenname.orEmpty())
                }
            )
        }
    }

    private fun bindFinalButton(fragment: BautagebuchFragment, root: View) {
        val button = root.findViewById<MaterialButton>(R.id.btnPdfFinal) ?: return
        button.text = "BTB Abschluss"
        button.setOnClickListener { anchor -> closeAndRelease(fragment, root, anchor) }
        button.setOnLongClickListener { anchor ->
            Snackbar.make(anchor, "ZIP bitte beim gewünschten BTB im BTB-Verlauf teilen.", Snackbar.LENGTH_LONG).show()
            true
        }
    }

    private fun closeAndRelease(fragment: BautagebuchFragment, root: View, anchor: View) {
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
                    BtbDokumentVersionierung.pflegeNachAutosave(appContext, saved.entry)
                    BtbRendererBaselineBridge.prepare(appContext, saved.entry)
                    val renderEntry = BtbRenderOriginalDiff.aufbereiten(appContext, saved.entry)

                    BtbFotoRenderMarkierung.clear()
                    val pdfEngine = BautagebuchPdf(appContext, ServiceLocator.fotoRepository)
                    val alleFotos = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(saved.entry.baustelleId)
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
                    BtbOriginalFotoSpeicher.sichernWennFehlt(appContext, saved.entry.btbId, aktuelleFotoIds)
                    pdfEngine.deleteEntwurfPdf(saved.entry)

                    val btbId = saved.btbId.trim()
                    if (btbId.isBlank()) throw IllegalStateException("BTB-ID fehlt")
                    BtbActivePointerControl.nachAbschlussInaktivMerken(
                        appContext,
                        saved.baustelleId,
                        saved.baustelleName,
                        btbId
                    )
                    file
                }.also { BtbFotoRenderMarkierung.clear() }
            }

            if (!fragment.isAdded || fragment.view == null) return@launch
            result.onSuccess { file ->
                renderNoActiveUi(root)
                Snackbar.make(anchor, "BTB abgeschlossen · kein BTB mehr aktiv", Snackbar.LENGTH_SHORT).show()
                fragment.startActivity(Intent(fragment.requireContext(), BtbPdfViewerActivity::class.java).apply {
                    putExtra(BtbPdfViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                    putExtra(BtbPdfViewerActivity.EXTRA_TITLE, "BTB-PDF")
                })
            }.onFailure { error ->
                Snackbar.make(anchor, "BTB-Abschluss fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun startActivePointerGuard(fragment: BautagebuchFragment, root: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastActive: String? = null
                while (isActive) {
                    val siteId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
                    if (siteId != null) {
                        val site = withContext(Dispatchers.IO) { ServiceLocator.baustellenRepository.baustelleMitIdOnce(siteId) }
                        val name = site?.baustellenname.orEmpty()
                        if (name.isNotBlank()) {
                            val explicitId = fragment.arguments?.getString("btbId")?.takeIf { it.isNotBlank() && !it.startsWith("legacy:") }
                            var activeId = withContext(Dispatchers.IO) {
                                BautagebuchSpeicher.aktiveBtbId(fragment.requireContext().applicationContext, siteId, name)
                            }
                            val blockedId = withContext(Dispatchers.IO) {
                                BtbActivePointerControl.nachAbschlussInaktiveBtbId(fragment.requireContext().applicationContext, siteId, name)
                            }

                            if (explicitId != null && activeId == explicitId) {
                                withContext(Dispatchers.IO) {
                                    BtbActivePointerControl.inaktivMarkerLoeschen(fragment.requireContext().applicationContext, siteId, name)
                                }
                            } else if (activeId != null && blockedId != null && activeId == blockedId) {
                                withContext(Dispatchers.IO) {
                                    BtbActivePointerControl.aktiveBtbLoesen(fragment.requireContext().applicationContext, siteId, name)
                                }
                                activeId = null
                            } else if (activeId != null && blockedId != null && activeId != blockedId) {
                                withContext(Dispatchers.IO) {
                                    BtbActivePointerControl.inaktivMarkerLoeschen(fragment.requireContext().applicationContext, siteId, name)
                                }
                            }

                            if (activeId == null) {
                                renderNoActiveUi(root)
                            } else if (lastActive == null || lastActive != activeId) {
                                setSectionPreviewEnabled(root, true)
                            }
                            lastActive = activeId
                        }
                    }
                    delay(450L)
                }
            }
        }
    }

    private fun renderNoActiveUi(root: View) {
        root.findViewById<TextView>(R.id.tvCurrentReportLine1)?.text = "Noch kein BTB"
        root.findViewById<TextView>(R.id.tvCurrentReportLine2)?.text = ""
        root.findViewById<View>(R.id.topFieldsToggleRow)?.visibility = View.GONE
        root.findViewWithTag<View>(EDIT_BUTTON_TAG)?.visibility = View.GONE
        root.findViewWithTag<View>(EDIT_SPACER_TAG)?.visibility = View.GONE
        root.findViewById<View>(R.id.btnPdf)?.visibility = View.GONE
        root.findViewById<View>(R.id.btnPdfFinal)?.visibility = View.GONE
        setSectionPreviewEnabled(root, false)
    }

    private fun setSectionPreviewEnabled(root: View, enabled: Boolean) {
        intArrayOf(
            R.id.etArbeiten,
            R.id.etAbnahmen,
            R.id.etEinweisung,
            R.id.etBesonderheiten,
            R.id.etMaengel
        ).forEach { id ->
            root.findViewById<TextView>(id)?.let { view ->
                view.isEnabled = enabled
                if (!enabled && view.text?.isNotEmpty() == true) view.text = ""
            }
        }
        intArrayOf(
            R.id.btnFotosArbeiten,
            R.id.btnFotosAbnahmen,
            R.id.btnFotosEinweisung,
            R.id.btnFotosBesonderheiten,
            R.id.btnFotosMaengel
        ).forEach { id ->
            root.findViewById<MaterialButton>(id)?.let { button ->
                button.isEnabled = enabled
                if (!enabled) button.text = "📷 0"
            }
        }
    }
}
