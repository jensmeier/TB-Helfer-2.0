package de.tbhelfer.ui.bautagebuch

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import de.tbhelfer.server.BtbLifecycleCoordinator
import de.tbhelfer.server.BtbServerStateStore
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
 * - kompakter Kopfbereich ohne reservierten Leerraum,
 * - Abschluss erst nach erfolgreichem PDF-Schreiben,
 * - genau ein aktuelles PDF pro btbId,
 * - erster Abschluss bleibt unveraenderliches Original fuer Text UND Foto-Bestand,
 * - Nachbearbeitung loescht das alte Abschlussdokument,
 * - kein sichtbarer Schein-Entwurf, solange noch gar kein BTB existiert.
 */
object BtbModernLifecycleInstaller {

    private const val EDIT_BUTTON_TAG = "tb_modern_edit_button"
    private const val EDIT_SPACER_TAG = "tb_modern_edit_spacer"

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
        compactHeaderSpacing(root)
        bindFinalButton(fragment, root)
        startLifecycleWatcher(fragment, root)
    }

    private fun compactHeaderSpacing(root: View) {
        val density = root.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        fun setMargins(view: View?, top: Int? = null, bottom: Int? = null) {
            val target = view ?: return
            val lp = target.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            top?.let { lp.topMargin = dp(it) }
            bottom?.let { lp.bottomMargin = dp(it) }
            target.layoutParams = lp
        }

        // Kopf kompakter: Buttons -> Details -> Punkt 4 sollen ohne grossen Leerlauf folgen.
        setMargins(root.findViewById(R.id.reportQuickBar), bottom = 3)
        setMargins(root.findViewById(R.id.topFieldsToggleRow), bottom = 4)
        val section4Header = root.findViewById<View>(R.id.btnFotosArbeiten)?.parent as? View
        setMargins(section4Header, top = 4)
    }

    private fun addEditButton(fragment: BautagebuchFragment, root: View) {
        val bar = root.findViewById<LinearLayout>(R.id.reportQuickBar) ?: return
        val plus = root.findViewById<View>(R.id.btnQuickNewBtb) ?: return
        val density = root.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = MaterialCardView(fragment.requireContext()).apply {
            tag = EDIT_BUTTON_TAG
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
        card.setOnClickListener { showEditDialog(fragment, root, card) }

        val spacer = Space(fragment.requireContext()).apply {
            tag = EDIT_SPACER_TAG
            layoutParams = LinearLayout.LayoutParams(dp(10), 1)
        }
        val plusIndex = bar.indexOfChild(plus).coerceAtLeast(0)
        bar.addView(card, plusIndex)
        bar.addView(spacer, plusIndex + 1)
    }

    private fun showEditDialog(fragment: BautagebuchFragment, root: View, anchor: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val current = BtbUiSaveBridge.aktuell(fragment)
            if (current == null) {
                Snackbar.make(anchor, "Noch kein aktueller BTB vorhanden. Neuen BTB über + starten.", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val serverState = BtbServerStateStore.state(
                fragment.requireContext().applicationContext,
                current.btbId
            )
            if (serverState?.readOnly == true) {
                Snackbar.make(
                    anchor,
                    if (serverState.serverFinalReadOnly) {
                        "Abgeschlossener Server-BTB ist schreibgeschützt."
                    } else {
                        "Eigene Schicht ist beendet. Zum Bearbeiten zuerst „Schicht fortsetzen“."
                    },
                    Snackbar.LENGTH_LONG
                ).show()
                return@launch
            }
            val status = withContext(Dispatchers.IO) {
                BtbDokumentVersionierung.status(fragment.requireContext().applicationContext, current.entry)
            }
            val entry = current.entry
            val ctx = fragment.requireContext()
            val density = root.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(6), dp(20), dp(4))
            }

            TextView(ctx).apply {
                text = if (status == BtbAbschlussStatus.ABGESCHLOSSEN) {
                    "Abgeschlossener BTB. Nur eine tatsächlich gespeicherte Änderung macht das aktuelle Abschluss-PDF ungültig."
                } else {
                    "Es wird derselbe BTB mit derselben btbId bearbeitet."
                }
                textSize = 12f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, 0, dp(8))
                content.addView(this)
            }

            var dialog: androidx.appcompat.app.AlertDialog? = null

            fun addRow(title: String, value: String, which: Int) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                val texts = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                texts.addView(TextView(ctx).apply {
                    text = title
                    textSize = 14f
                    setTextColor(Color.BLACK)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                texts.addView(TextView(ctx).apply {
                    text = value.ifBlank { "Keine Angaben" }
                    textSize = 11.5f
                    setTextColor(Color.DKGRAY)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                row.addView(texts)
                row.addView(MaterialButton(ctx).apply {
                    text = "Bearbeiten"
                    isAllCaps = false
                    minWidth = 0
                    setOnClickListener {
                        dialog?.dismiss()
                        openExistingEditor(root, which)
                    }
                })
                content.addView(row)
            }

            val dateTime = buildString {
                append(entry.startUhrzeit ?: "--:--")
                append("–")
                append(entry.endeUhrzeit ?: "--:--")
                append(" · ")
                append(entry.datumIso)
            }
            val personal = entry.firmen
                .filter { it.visibleInPersonal && !it.isDeleted }
                .flatMap { f -> f.personal.map { p -> listOfNotNull(p.anzahl?.toString(), p.rolle.takeIf { it.isNotBlank() }).joinToString(" ") } }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { entry.personal.filter { it.isNotBlank() }.joinToString(", ") }
            val geraete = entry.firmen
                .filter { it.visibleInMaschinen && !it.isDeleted }
                .flatMap { f -> f.geraete.map { g -> listOfNotNull(g.anzahl?.toString(), g.bezeichnung.takeIf { it.isNotBlank() }).joinToString(" ") } }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { entry.geraete.filter { it.isNotBlank() }.joinToString(", ") }
            val weatherTimes = (entry.weather?.slots?.takeIf { it.isNotEmpty() } ?: entry.weatherSlots)
                .map { it.uhrzeit }
                .filter { it.isNotBlank() }
                .joinToString(" · ")

            addRow("Datum/Zeit", dateTime, 0)
            addRow("Personal", personal, 1)
            addRow("Geräte/Maschinen", geraete, 2)
            addRow("Wetter", weatherTimes, 3)

            dialog = MaterialAlertDialogBuilder(ctx)
                .setTitle("Aktuellen BTB bearbeiten")
                .setView(content)
                .setNegativeButton("Abbrechen", null)
                .create()
            dialog?.show()
        }
    }

    /**
     * Öffnet nach dem neuen Stift-Dialog direkt den gewünschten Alt-Editor.
     * Die alte Vierer-Auswahl (Wetter / Datum-Zeit / Personal / Geräte) wird dabei nicht erneut
     * als Zwischenschritt stehen gelassen.
     */
    private fun openExistingEditor(root: View, which: Int) {
        val toggle = root.findViewById<TextView>(R.id.tvTopFieldsToggle)

        if (which == 0) {
            // Datum/Zeit hängt historisch an einem PopupMenu mit btnDateTime als Anchor. Damit der
            // Anchor eine echte Position hat, wird nur die Wetter-Kopfzeile unsichtbar gelayoutet;
            // die alte Vierer-Auswahl wird dabei nicht sichtbar eingeblendet.
            val weatherHeader = root.findViewById<View>(R.id.weatherHeader)
            val oldVisibility = weatherHeader?.visibility ?: View.GONE
            weatherHeader?.visibility = View.INVISIBLE
            root.post {
                root.findViewById<View>(R.id.btnDateTime)?.performClick()
                root.postDelayed({ weatherHeader?.visibility = oldVisibility }, 120L)
            }
            return
        }

        // Personal/Maschinen/Wetter benötigen ihre vorhandenen Edit-Container. Intern wird der
        // Details-Bereich geöffnet, danach verschwinden die alten Auswahl-Pills sofort wieder.
        if (toggle?.text?.toString()?.contains("anzeigen", ignoreCase = true) == true) {
            toggle.performClick()
        }
        val targetId = when (which) {
            1 -> R.id.tvPersonalPreview
            2 -> R.id.tvMaschinenPreview
            else -> R.id.tvWetterPreview
        }
        root.post {
            root.findViewById<View>(targetId)?.performClick()
            root.findViewById<View>(R.id.weatherHeader)?.visibility = View.GONE
            root.findViewById<View>(R.id.pmHeaderRow)?.visibility = View.GONE
        }
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
            Snackbar.make(anchor, "BTB wird gespeichert und mit dem Server abgeglichen …", Snackbar.LENGTH_LONG).show()
            val saved = BtbUiSaveBridge.speichernUndNeuLaden(fragment, root)
            if (saved == null) {
                Snackbar.make(anchor, "BTB konnte nicht vollständig gespeichert werden. Bitte Eingaben prüfen.", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val coordinator = BtbLifecycleCoordinator(fragment.requireContext())
            val prepared = coordinator.prepareAccess(saved.baustelleId, saved.btbId)
            val access = when (prepared) {
                is BtbLifecycleCoordinator.PrepareResult.Success -> prepared.access
                BtbLifecycleCoordinator.PrepareResult.SessionInvalid -> {
                    Snackbar.make(anchor, "Gerätefreigabe ist nicht mehr gültig.", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                is BtbLifecycleCoordinator.PrepareResult.Conflict -> {
                    Snackbar.make(anchor, prepared.message, Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                is BtbLifecycleCoordinator.PrepareResult.Error -> {
                    Snackbar.make(anchor, prepared.message, Snackbar.LENGTH_LONG).show()
                    return@launch
                }
            }

            suspend fun handleSuccess(
                result: BtbLifecycleCoordinator.ActionResult.Success,
                helperAction: Boolean
            ) {
                val state = BtbServerStateStore.state(
                    fragment.requireContext().applicationContext,
                    saved.btbId
                )
                if (helperAction) {
                    val finished = result.access.participantStatus.equals("FINISHED", true)
                    Snackbar.make(
                        anchor,
                        if (finished) "Schicht beendet." else "Schicht fortgesetzt.",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    if (!result.access.snapshot.status.equals("FERTIG", true)) {
                        Snackbar.make(anchor, "Server hat den Abschluss noch nicht bestätigt.", Snackbar.LENGTH_LONG).show()
                        return
                    }
                    withContext(Dispatchers.IO) {
                        BtbActivePointerControl.nachAbschlussInaktivMerken(
                            fragment.requireContext().applicationContext,
                            saved.baustelleId,
                            saved.baustelleName,
                            saved.btbId
                        )
                    }
                    root.findViewById<TextView>(R.id.tvCurrentReportLine2)?.text = "Abgeschlossen"
                    Snackbar.make(
                        anchor,
                        if (state?.canViewPdf == true) {
                            "BTB serverseitig abgeschlossen. Abschluss-PDF wird automatisch nachgeladen."
                        } else {
                            "BTB serverseitig abgeschlossen."
                        },
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }

            if (!access.isTeamLead) {
                val action = if (access.participantStatus.equals("FINISHED", true)) {
                    coordinator.shiftResume(saved.baustelleId, saved.btbId)
                } else {
                    coordinator.shiftFinish(saved.baustelleId, saved.btbId)
                }
                when (action) {
                    is BtbLifecycleCoordinator.ActionResult.Success -> handleSuccess(action, true)
                    BtbLifecycleCoordinator.ActionResult.SessionInvalid ->
                        Snackbar.make(anchor, "Gerätefreigabe ist nicht mehr gültig.", Snackbar.LENGTH_LONG).show()
                    is BtbLifecycleCoordinator.ActionResult.Conflict ->
                        Snackbar.make(anchor, action.message, Snackbar.LENGTH_LONG).show()
                    is BtbLifecycleCoordinator.ActionResult.Error ->
                        Snackbar.make(anchor, action.message, Snackbar.LENGTH_LONG).show()
                    is BtbLifecycleCoordinator.ActionResult.ParticipantsNotFinished ->
                        Snackbar.make(anchor, "Schichtstatus konnte nicht geändert werden.", Snackbar.LENGTH_LONG).show()
                }
                return@launch
            }

            if (!access.canFinalize) {
                Snackbar.make(
                    anchor,
                    "Nur der Haupt-TB darf den Bautagesbericht endgültig abschließen.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@launch
            }

            when (val action = coordinator.finalize(saved.baustelleId, saved.btbId, false)) {
                is BtbLifecycleCoordinator.ActionResult.Success -> handleSuccess(action, false)
                is BtbLifecycleCoordinator.ActionResult.ParticipantsNotFinished -> {
                    val names = action.participants.joinToString("\n") {
                        "• " + (it.displayName ?: it.userId)
                    }
                    MaterialAlertDialogBuilder(fragment.requireContext())
                        .setTitle("Teilnehmer noch nicht fertig")
                        .setMessage(
                            "Folgende Teilnehmer haben ihre Schicht noch nicht beendet:\n\n" +
                                names +
                                "\n\nNur wenn du bewusst fortfahren willst, wird trotzdem abgeschlossen."
                        )
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Trotzdem abschließen") { _, _ ->
                            fragment.viewLifecycleOwner.lifecycleScope.launch {
                                when (
                                    val forced = coordinator.finalize(
                                        saved.baustelleId,
                                        saved.btbId,
                                        true
                                    )
                                ) {
                                    is BtbLifecycleCoordinator.ActionResult.Success ->
                                        handleSuccess(forced, false)
                                    BtbLifecycleCoordinator.ActionResult.SessionInvalid ->
                                        Snackbar.make(anchor, "Gerätefreigabe ist nicht mehr gültig.", Snackbar.LENGTH_LONG).show()
                                    is BtbLifecycleCoordinator.ActionResult.Conflict ->
                                        Snackbar.make(anchor, forced.message, Snackbar.LENGTH_LONG).show()
                                    is BtbLifecycleCoordinator.ActionResult.Error ->
                                        Snackbar.make(anchor, forced.message, Snackbar.LENGTH_LONG).show()
                                    is BtbLifecycleCoordinator.ActionResult.ParticipantsNotFinished ->
                                        Snackbar.make(anchor, "Teilnehmerstatus hat sich erneut geändert.", Snackbar.LENGTH_LONG).show()
                                }
                            }
                        }
                        .show()
                }
                BtbLifecycleCoordinator.ActionResult.SessionInvalid ->
                    Snackbar.make(anchor, "Gerätefreigabe ist nicht mehr gültig.", Snackbar.LENGTH_LONG).show()
                is BtbLifecycleCoordinator.ActionResult.Conflict ->
                    Snackbar.make(anchor, action.message, Snackbar.LENGTH_LONG).show()
                is BtbLifecycleCoordinator.ActionResult.Error ->
                    Snackbar.make(anchor, action.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Fotos gehören zum unveränderlichen ersten Abschluss. Sobald sich die aktuell ausgewählte
     * Foto-Menge gegenüber diesem Original ändert, ist das bisherige Abschluss-PDF fachlich
     * ungültig. Das gilt auch dann, wenn am normalen BTB-JSON sonst nichts geändert wurde.
     */
    private suspend fun invalidateClosureForPhotoDiff(fragment: BautagebuchFragment, entry: BautagebuchEintrag) {
        val appContext = fragment.requireContext().applicationContext
        withContext(Dispatchers.IO) {
            val alleFotos = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(entry.baustelleId)
            BtbOriginalFotoSpeicher.migriereAusBestehendemOriginalWennFehlt(appContext, entry, alleFotos)
            if (!BtbOriginalFotoSpeicher.hatOriginal(appContext, entry.btbId)) return@withContext

            val aktuelleIds = BtbOriginalFotoSpeicher.aktuelleFotoIds(appContext, entry, alleFotos)
            val fotoGeaendert = BtbOriginalFotoSpeicher.neueFotoIds(appContext, entry.btbId, aktuelleIds).isNotEmpty() ||
                BtbOriginalFotoSpeicher.entfernteFotoIds(appContext, entry.btbId, aktuelleIds).isNotEmpty()
            if (fotoGeaendert) {
                BtbAbschlussPdfFinder.neueste(appContext, entry)?.delete()
            }
        }
    }

    private fun startLifecycleWatcher(fragment: BautagebuchFragment, root: View) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastSeenId: String? = null
                var lastSeenChange: Long = Long.MIN_VALUE
                var photoRefreshTick = 0
                while (isActive) {
                    val current = runCatching { BtbUiSaveBridge.aktuell(fragment) }.getOrNull()
                    if (current == null) {
                        root.findViewById<TextView>(R.id.tvCurrentReportLine1)?.text = "Noch kein BTB"
                        root.findViewById<TextView>(R.id.tvCurrentReportLine2)?.text = ""
                        // Kein BTB = keine Detailfelder und keine Bearbeiten-/PDF-Aktionen.
                        root.findViewById<View>(R.id.topFieldsToggleRow)?.visibility = View.GONE
                        root.findViewById<View>(R.id.weatherHeader)?.visibility = View.GONE
                        root.findViewById<View>(R.id.pmHeaderRow)?.visibility = View.GONE
                        root.findViewWithTag<View>(EDIT_BUTTON_TAG)?.visibility = View.GONE
                        root.findViewWithTag<View>(EDIT_SPACER_TAG)?.visibility = View.GONE
                        root.findViewById<View>(R.id.btnPdf)?.visibility = View.GONE
                        root.findViewById<View>(R.id.btnPdfFinal)?.visibility = View.GONE
                        lastSeenId = null
                        lastSeenChange = Long.MIN_VALUE
                    } else {
                        root.findViewById<View>(R.id.topFieldsToggleRow)?.visibility = View.VISIBLE
                        root.findViewWithTag<View>(EDIT_BUTTON_TAG)?.visibility = View.VISIBLE
                        root.findViewWithTag<View>(EDIT_SPACER_TAG)?.visibility = View.VISIBLE
                        root.findViewById<View>(R.id.btnPdf)?.visibility = View.VISIBLE
                        root.findViewById<View>(R.id.btnPdfFinal)?.visibility = View.VISIBLE

                        val confirmedServerState = BtbServerStateStore.state(
                            fragment.requireContext().applicationContext,
                            current.btbId
                        )
                        val changedStamp = current.entry.geaendertAmEpochMs
                        if (
                            confirmedServerState?.readOnly != true &&
                            (current.btbId != lastSeenId || changedStamp != lastSeenChange)
                        ) {
                            withContext(Dispatchers.IO) {
                                BtbDokumentVersionierung.pflegeNachAutosave(
                                    fragment.requireContext().applicationContext,
                                    current.entry
                                )
                            }
                            lastSeenId = current.btbId
                            lastSeenChange = changedStamp
                        }

                        if (photoRefreshTick % 2 == 0) {
                            try {
                                if (confirmedServerState?.readOnly != true) {
                                    invalidateClosureForPhotoDiff(fragment, current.entry)
                                }
                                BtbPhotoCounterFix.refresh(fragment.requireContext(), root, current.entry)
                            } catch (_: Exception) {
                                // UI-Helfer darf den BTB-Lebenszyklus nie abbrechen.
                            }
                        }

                        // Status bewusst NACH der Foto-Prüfung lesen: ein nachträglich hinzugefügtes
                        // Foto macht den abgeschlossenen BTB sofort zur Nachbearbeitung.
                        val status = withContext(Dispatchers.IO) {
                            BtbDokumentVersionierung.status(fragment.requireContext().applicationContext, current.entry)
                        }
                        val serverState = BtbServerStateStore.state(
                            fragment.requireContext().applicationContext,
                            current.btbId
                        )
                        val finalButton = root.findViewById<MaterialButton>(R.id.btnPdfFinal)
                        val previewButton = root.findViewById<View>(R.id.btnPdf)
                        val editButton = root.findViewWithTag<View>(EDIT_BUTTON_TAG)

                        when {
                            serverState?.serverFinalReadOnly == true -> {
                                root.findViewById<TextView>(R.id.tvCurrentReportLine2)?.text = "Abgeschlossen"
                                finalButton?.visibility = View.GONE
                                previewButton?.visibility = if (serverState.canViewPdf) View.VISIBLE else View.GONE
                                editButton?.visibility = View.GONE
                            }
                            serverState != null && !serverState.isTeamLead -> {
                                val finished = serverState.participantStatus.equals("FINISHED", true)
                                root.findViewById<TextView>(R.id.tvCurrentReportLine2)?.text =
                                    if (finished) "Eigene Schicht beendet" else "Aktiv"
                                finalButton?.visibility = View.VISIBLE
                                finalButton?.text = if (finished) "Schicht fortsetzen" else "Schicht Ende"
                                previewButton?.visibility = View.GONE
                                editButton?.visibility = if (finished) View.GONE else View.VISIBLE
                            }
                            else -> {
                                root.findViewById<TextView>(R.id.tvCurrentReportLine2)?.text = when (status) {
                                    BtbAbschlussStatus.ENTWURF -> "Entwurf"
                                    BtbAbschlussStatus.ABGESCHLOSSEN -> "Abgeschlossen"
                                    BtbAbschlussStatus.NACHBEARBEITUNG -> "Nachbearbeitung · Abschluss-PDF ungültig"
                                }
                                finalButton?.visibility =
                                    if (status == BtbAbschlussStatus.ABGESCHLOSSEN) View.GONE else View.VISIBLE
                                finalButton?.text = "BTB Abschluss"
                                previewButton?.visibility =
                                    if (status == BtbAbschlussStatus.ABGESCHLOSSEN) View.VISIBLE else View.GONE
                                editButton?.visibility = View.VISIBLE
                            }
                        }
                    }
                    photoRefreshTick += 1
                    delay(600L)
                }
            }
        }
    }
}
