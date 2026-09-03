package de.tbhelfer.ui.streckenpunkte

import android.Manifest
import android.app.AlertDialog
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Filter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.StreckenpunktEntity
import de.tbhelfer.datenbank.StreckenpunktTyp
import de.tbhelfer.vermessung.SensorLiveQuelle
import de.tbhelfer.vermessung.StandortLiveQuelle
import de.tbhelfer.util.BahnhofSucheUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

class StreckenpunkteFragment : Fragment(R.layout.fragment_streckenpunkte) {

    private val viewModel: StreckenpunkteViewModel by viewModels {
        StreckenpunkteViewModel.Fabrik(
            streckenRepo = ServiceLocator.streckenpunkteRepository,
            baustellenRepo = ServiceLocator.baustellenRepository,
            baumRepo = ServiceLocator.baustellenBaumRepository,
            aktuelleSpeicher = ServiceLocator.aktuelleBaustelleSpeicher
        )
    }

    private lateinit var adapter: StreckenpunkteAdapter
    private var txtLetzterExport: TextView? = null
    private var automatikGpsJob: Job? = null
    private var automatikSchritteJob: Job? = null
    private var automatikKompassJob: Job? = null
    private var gpsVorwaermJob: Job? = null
    private var letzteVorwaermLocation: Location? = null
    private var listeSichtbar: Boolean = false

    private val activityRecognitionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { erlaubt ->
        if (erlaubt) {
            starteAutomatikSchrittFlow()
        } else {
            viewModel.automatikSensorStatusSetzen(schritteAktiv = false)
            Toast.makeText(requireContext(), "Schrittzähler läuft ohne Berechtigung nicht mit.", Toast.LENGTH_SHORT).show()
        }
    }

    private data class BahnhofVorschlag(
        val code: String,
        val name: String,
        val display: String
    ) {
        val codeNorm: String = BahnhofSucheUtil.normalisiereSuchtext(code)
        val nameNorm: String = BahnhofSucheUtil.normalisiereSuchtext(name)
        val displayNorm: String = BahnhofSucheUtil.normalisiereSuchtext(display)
        val suchBlob: String = "$codeNorm|$nameNorm|$displayNorm"
    }

    private data class WeichenErfassungsDraft(
        var kmText: String = "",
        var notiz: String = "",
        var gpsLat: Double? = null,
        var gpsLon: Double? = null,
        var gpsAltM: Double? = null,
        var gpsZeitMs: Long? = null,
        var gpsAccuracyM: Float? = null
    )

    private data class PauseGpsSample(
        val lat: Double,
        val lon: Double,
        val altM: Double?,
        val accuracyM: Float?,
        val epochMs: Long
    )

    private data class PauseAnkerAuswertung(
        val lat: Double,
        val lon: Double,
        val altM: Double?,
        val epochMs: Long,
        val gruen: Int,
        val gelb: Int,
        val rot: Int,
        val gesamt: Int,
        val streuungM: Double?,
        val warnungNurGelb: Boolean,
        val besteAccuracyM: Float?
    )

    private val bahnhofVorschlaege: MutableList<BahnhofVorschlag> by lazy(LazyThreadSafetyMode.NONE) {
        ladeBahnhofVorschlaegeAusAssets().toMutableList()
    }

    private fun formatGpsFixZeit(zeitMs: Long?): String {
        if (zeitMs == null || zeitMs <= 0L) return "-"
        return runCatching {
            SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date(zeitMs))
        }.getOrDefault("-")
    }

    private fun gpsAlterMs(location: Location?): Long? {
        val zeitMs = location?.time ?: return null
        if (zeitMs <= 0L) return null
        return (System.currentTimeMillis() - zeitMs).coerceAtLeast(0L)
    }

    private fun istFrischerGpsFix(location: Location?, maxAlterMs: Long = 10_000L): Boolean {
        val alter = gpsAlterMs(location) ?: return false
        return alter <= maxAlterMs
    }

    private fun formatGenauigkeitMeter(genauigkeit: Float?): String {
        return genauigkeit?.let { String.format(Locale.GERMANY, "%.1f m", it) } ?: "-"
    }

    private fun Throwable.istIgnorierbarerCoroutineAbbruch(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is CancellationException) return true
            current = current.cause
        }
        return false
    }

    private fun starteGpsVorwaermenWennMoeglich() {
        if (gpsVorwaermJob != null) return
        if (!isAdded) return
        if (!StandortLiveQuelle.hatStandortBerechtigung(requireContext())) return
        gpsVorwaermJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                StandortLiveQuelle.standortFlow(
                    context = requireContext(),
                    minTimeMs = 500L,
                    minDistanceM = 0f,
                    emitLastKnown = false,
                    glaettungFenster = 1
                ).collect { location ->
                    letzteVorwaermLocation = Location(location)
                }
            } catch (t: Throwable) {
                if (!t.istIgnorierbarerCoroutineAbbruch()) {
                    letzteVorwaermLocation = null
                }
            } finally {
                gpsVorwaermJob = null
            }
        }
    }

    private fun stoppeGpsVorwaermen() {
        gpsVorwaermJob?.cancel()
        gpsVorwaermJob = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txtAktuelleBaustelle = view.findViewById<TextView>(R.id.txtAktuelleBaustelle)
        val txtGeladenerKontext = view.findViewById<TextView>(R.id.txtGeladenerKontext)
        val layoutKontextAktionen = view.findViewById<View>(R.id.layoutKontextAktionen)
        val btnAuswahlAendern = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAuswahlAendern)
        val btnNeuWaehlen = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNeuWaehlen)
        val txtStatistik = view.findViewById<TextView>(R.id.txtStatistik)
        val txtAutomatikStatus = view.findViewById<TextView>(R.id.txtAutomatikStatus)
        val txtHinweis = view.findViewById<TextView>(R.id.txtHinweis)
        val edtSuche = view.findViewById<EditText>(R.id.edtSuche)
        val spinnerListenFilter = view.findViewById<Spinner>(R.id.spinnerListenFilter)
        val skizze = view.findViewById<StreckenpunkteSkizzeView>(R.id.skizzeStreckenpunkte)
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerStreckenpunkte)
        val btnMehr = view.findViewById<View>(R.id.btnMehr)
        val btnBaustelleWaehlen = view.findViewById<View>(R.id.btnBaustelleWaehlen)
        val layoutHauptAktionen = view.findViewById<View>(R.id.layoutHauptAktionen)
        val btnPunktSetzen = view.findViewById<View>(R.id.btnPunktSetzen)
        val btnAutomatik = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAutomatik)
        val layoutAutomatikSteuerung = view.findViewById<View>(R.id.layoutAutomatikSteuerung)
        val btnAutomatikPause = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAutomatikPause)
        val btnAutomatikWeiter = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAutomatikWeiter)
        val btnAutomatikStop = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAutomatikStop)
        val btnListe = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnListe)
        val layoutListenbereich = view.findViewById<View>(R.id.layoutListenbereich)
        txtLetzterExport = view.findViewById(R.id.txtLetzterExport)

        adapter = StreckenpunkteAdapter(
            onClick = { punkt -> dialogStreckenpunkt(punkt) },
            onLongClick = null
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        edtSuche.addTextChangedListener {
            viewModel.sucheSetzen(it?.toString().orEmpty())
        }

        spinnerListenFilter.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Kontext", "Alles", "Strecke", "Bahnhof")
        )
        spinnerListenFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val filter = when (position) {
                    1 -> ListenFilterUi.ALLES
                    2 -> ListenFilterUi.STRECKE
                    3 -> ListenFilterUi.BAHNHOF
                    else -> ListenFilterUi.KONTEXT
                }
                if (viewModel.uiState.value.listenFilter != filter) {
                    viewModel.listenFilterSetzen(filter)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnPunktSetzen.setOnClickListener {
            val state = viewModel.uiState.value
            val kontext = state.aktiverKontext
            if (kontext != null) {
                dialogStreckenpunkt(null, kontext)
            } else {
                waehleKontextFuerAktion(fuerAutomatik = false)
            }
        }

        btnAutomatik.setOnClickListener {
            val state = viewModel.uiState.value
            val kontext = state.aktiverKontext
            if (kontext != null) {
                zeigeAutomatikAktionen(kontext)
            } else {
                waehleKontextFuerAktion(fuerAutomatik = true)
            }
        }

        btnListe.setOnClickListener {
            listeSichtbar = !listeSichtbar
            layoutListenbereich.isVisible = listeSichtbar && viewModel.uiState.value.aktuelleBaustelleId != null
            btnListe.text = getString(
                if (listeSichtbar) R.string.streckenpunkte_compact_liste_ausblenden
                else R.string.streckenpunkte_compact_liste_anzeigen
            )
        }

        btnMehr.setOnClickListener {
            zeigeMehrAktionen()
        }

        btnBaustelleWaehlen.setOnClickListener {
            findNavController().navigate(R.id.baustellenFragment)
        }

        btnAuswahlAendern.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.automatikAktiv) {
                Toast.makeText(requireContext(), "Automatik zuerst stoppen.", Toast.LENGTH_SHORT).show()
            } else {
                waehleKontextFuerAktion(fuerAutomatik = false, nurKontextSetzen = true)
            }
        }

        btnNeuWaehlen.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.automatikAktiv) {
                Toast.makeText(requireContext(), "Automatik zuerst stoppen.", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.kontextNeuWaehlen()
                Toast.makeText(requireContext(), "Auswahl zurückgesetzt.", Toast.LENGTH_SHORT).show()
            }
        }

        btnAutomatikPause.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.automatikAktiv && !state.automatikPausiert) {
                zeigePauseDialog()
            }
        }

        btnAutomatikWeiter.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.automatikAktiv && state.automatikPausiert) {
                viewModel.automatikFortsetzen()
                Toast.makeText(requireContext(), "Automatik läuft weiter.", Toast.LENGTH_SHORT).show()
            }
        }

        btnAutomatikStop.setOnClickListener {
            stoppeAutomatikUndExportiere()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                val name = state.baustellenname ?: "keine"
                txtAktuelleBaustelle.text = getString(R.string.streckenpunkte_patch_current_site, name)
                txtGeladenerKontext.text = state.aktiverKontext?.let {
                    getString(R.string.streckenpunkte_context_loaded, it.labelLang)
                } ?: getString(R.string.streckenpunkte_context_missing)
                txtStatistik.text = state.statistikText
                txtAutomatikStatus.text = buildAutomatikKurzText(state)

                if (edtSuche.text?.toString().orEmpty() != state.suchtext) {
                    edtSuche.setText(state.suchtext)
                    edtSuche.setSelection(state.suchtext.length)
                }

                txtHinweis.isVisible = !state.hinweisText.isNullOrBlank()
                txtHinweis.text = state.hinweisText.orEmpty()

                adapter.setzen(state.listenItems)
                skizze.setPunkte(state.kontextPunkte)

                if (spinnerListenFilter.selectedItemPosition != state.listenFilter.ordinal) {
                    spinnerListenFilter.setSelection(state.listenFilter.ordinal, false)
                }

                val hatSpeicherBaustelle = state.aktuelleBaustelleId != null
                val istWeiche = state.aktiverKontext?.art == TrassenKontextArt.BAHNHOF_WEICHE
                val automatikVerfuegbar = !istWeiche

                btnBaustelleWaehlen.isVisible = false
                layoutHauptAktionen.isVisible = true
                btnMehr.isVisible = true
                layoutKontextAktionen.isVisible = state.aktiverKontext != null
                btnPunktSetzen.isEnabled = true
                btnListe.isEnabled = hatSpeicherBaustelle

                btnAutomatik.isVisible = !state.automatikAktiv
                layoutAutomatikSteuerung.isVisible = state.automatikAktiv && automatikVerfuegbar
                btnAutomatik.isEnabled = automatikVerfuegbar
                btnAutomatik.text = when {
                    !automatikVerfuegbar && istWeiche -> getString(R.string.streckenpunkte_compact_automatik_weiche)
                    else -> getString(R.string.streckenpunkte_compact_automatik)
                }
                btnAutomatikPause.isEnabled = state.automatikAktiv && !state.automatikPausiert
                btnAutomatikWeiter.isEnabled = state.automatikAktiv && state.automatikPausiert
                btnAutomatikStop.isEnabled = state.automatikAktiv
                btnAutomatikPause.alpha = if (btnAutomatikPause.isEnabled) 1f else 0.5f
                btnAutomatikWeiter.alpha = if (btnAutomatikWeiter.isEnabled) 1f else 0.5f

                if (!hatSpeicherBaustelle) {
                    listeSichtbar = false
                }
                layoutListenbereich.isVisible = hatSpeicherBaustelle && listeSichtbar
                btnListe.text = getString(
                    if (listeSichtbar && hatSpeicherBaustelle) R.string.streckenpunkte_compact_liste_ausblenden
                    else R.string.streckenpunkte_compact_liste_anzeigen
                )

                if (state.automatikAktiv) {
                    stoppeGpsVorwaermen()
                } else {
                    stoppeAutomatikGpsFlow()
                    starteGpsVorwaermenWennMoeglich()
                }
            }
        }

        aktualisiereLetztenExportText()
    }

    override fun onDestroyView() {
        stoppeAutomatikGpsFlow()
        stoppeGpsVorwaermen()
        letzteVorwaermLocation = null
        txtLetzterExport = null
        super.onDestroyView()
    }

    private fun buildAutomatikKurzText(state: StreckenpunkteUiState): String {
        return when {
            state.automatikAktiv -> {
                val kontext = listOfNotNull(
                    state.automatikStreckennummer,
                    state.automatikBahnhof,
                    state.automatikGleis,
                    state.automatikWeiche
                ).joinToString(" · ")
                buildString {
                    append(if (state.automatikPausiert) "Automatik pausiert" else getString(R.string.streckenpunkte_compact_automatik_running))
                    if (kontext.isNotBlank()) append(" · ").append(kontext)
                    append("\nGPS ").append(state.automatikGpsFixAnzahl)
                    append(" · Grün ").append(state.automatikGruenAnzahl)
                    append(" · Gelb ").append(state.automatikGelbAnzahl)
                    append(" · Rot ").append(state.automatikRotAnzahl)
                }
            }
            else -> state.automatikStatusText
        }
    }

    private fun zeigeAutomatikAktionen(kontextOverride: AktiverTrassenKontext? = null) {
        val state = viewModel.uiState.value
        val kontext = kontextOverride ?: state.aktiverKontext
        if (kontext == null) {
            Toast.makeText(requireContext(), getString(R.string.streckenpunkte_context_missing_toast), Toast.LENGTH_LONG).show()
            return
        }
        if (kontext.art == TrassenKontextArt.BAHNHOF_WEICHE) {
            Toast.makeText(requireContext(), getString(R.string.streckenpunkte_compact_automatik_weiche_toast), Toast.LENGTH_LONG).show()
            return
        }
        if (!state.automatikAktiv) {
            zeigeAutomatikStartDialog(kontext)
            return
        }

        val items = if (state.automatikPausiert) {
            arrayOf("Weiter", getString(R.string.streckenpunkte_automatik_stop_short))
        } else {
            arrayOf("Pause", getString(R.string.streckenpunkte_automatik_stop_short))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.streckenpunkte_compact_automatik))
            .setMessage(buildAutomatikInfoText(state))
            .setItems(items) { _, which ->
                when {
                    state.automatikPausiert && which == 0 -> {
                        viewModel.automatikFortsetzen()
                        Toast.makeText(requireContext(), "Automatik läuft weiter.", Toast.LENGTH_SHORT).show()
                    }
                    !state.automatikPausiert && which == 0 -> zeigePauseDialog()
                    else -> stoppeAutomatikUndExportiere()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun zeigeMehrAktionen() {
        val items = arrayOf(
            getString(R.string.streckenpunkte_patch_export),
            getString(R.string.streckenpunkte_patch4b_export_sessions),
            getString(R.string.ui_fragment_baustellen_text_1)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.streckenpunkte_compact_more))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> exportiereAktuellePunkte()
                    1 -> zeigeExportListe()
                    2 -> findNavController().navigate(R.id.baustellenFragment)
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun buildAutomatikInfoText(state: StreckenpunkteUiState): String {
        if (!state.automatikAktiv) return state.automatikStatusText
        val header = if (state.automatikAktiv) {
            val titel = state.automatikSessionTitel?.takeIf { it.isNotBlank() }
            val kontext = listOfNotNull(
                state.automatikStreckennummer,
                state.automatikBahnhof,
                state.automatikGleis,
                state.automatikWeiche
            ).joinToString(" · ")
            buildString {
                append("Automatik läuft")
                if (!titel.isNullOrBlank()) append(" · ").append(titel)
                if (kontext.isNotBlank()) append("\n").append(kontext)
                append("\nTrackpunkte: ").append(state.automatikTrackpunkte)
                append(" · KM-Anker: ").append(state.automatikAnker)
                append(" · Distanz: ").append(state.automatikDistanzText)
            }
        } else {
            "Automatik: aus"
        }
        return "$header\n${state.automatikStatusText}"
    }

    private fun exportiereAktuellePunkte() {
        val state = viewModel.uiState.value
        if (state.kontextPunkte.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.streckenpunkte_patch_no_points_export), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val datei = StreckenpunkteJsonUtil.exportiereJson(
                context = requireContext(),
                baustellenname = state.baustellenname,
                punkte = state.kontextPunkte,
                exportLabel = state.aktiverKontext?.labelKurz
            )
            val gpsAnzahl = state.kontextPunkte.count { it.gpsLat != 0.0 || it.gpsLon != 0.0 }
            Toast.makeText(
                requireContext(),
                getString(
                    R.string.streckenpunkte_patch4b_export_done_with_summary,
                    datei.absolutePath,
                    state.kontextPunkte.size,
                    gpsAnzahl
                ),
                Toast.LENGTH_LONG
            ).show()
            aktualisiereLetztenExportText()
        }.onFailure {
            Toast.makeText(
                requireContext(),
                getString(R.string.streckenpunkte_patch_error_with_reason, it.message ?: "-"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun aktualisiereLetztenExportText() {
        val textView = txtLetzterExport ?: return
        val letzter = StreckenpunkteJsonUtil.ladeExportSessions(requireContext()).firstOrNull()
        if (letzter == null) {
            textView.text = getString(R.string.streckenpunkte_patch4b_last_export_placeholder)
            return
        }
        val zeitText = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(letzter.exportiertAmEpochMs))
        val dateiStatus = if (letzter.dateiVorhanden) letzter.dateiName else getString(
            R.string.streckenpunkte_patch4b_missing_file_suffix,
            letzter.dateiName
        )
        textView.text = getString(
            R.string.streckenpunkte_patch4b_last_export,
            zeitText,
            letzter.baustellenname,
            letzter.punktAnzahl,
            letzter.gpsAnzahl,
            dateiStatus
        )
    }

    private fun zeigeExportListe() {
        val sessions = StreckenpunkteJsonUtil.ladeExportSessions(requireContext())
        if (sessions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.streckenpunkte_patch4b_history_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val items = sessions.map { session ->
            val zeitText = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(session.exportiertAmEpochMs))
            val statusText = if (session.dateiVorhanden) session.dateiName else getString(
                R.string.streckenpunkte_patch4b_missing_file_suffix,
                session.dateiName
            )
            getString(
                R.string.streckenpunkte_patch4b_history_item,
                zeitText,
                session.baustellenname,
                session.punktAnzahl,
                session.gpsAnzahl,
                statusText
            )
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.streckenpunkte_patch4b_history_title))
            .setItems(items) { _, which ->
                zeigeExportAktionen(sessions[which])
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun zeigeExportAktionen(session: StreckenpunktExportSession) {
        val zeitText = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(session.exportiertAmEpochMs))
        val message = getString(
            R.string.streckenpunkte_patch4b_history_detail,
            session.baustellenname,
            zeitText,
            session.punktAnzahl,
            session.gpsAnzahl,
            session.dateiName,
            session.absolutePath
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.streckenpunkte_patch4b_history_actions_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.streckenpunkte_patch4b_history_action_repeat)) { _, _ ->
                if (!session.dateiVorhanden) {
                    Toast.makeText(requireContext(), getString(R.string.streckenpunkte_patch4b_file_missing), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                runCatching {
                    val datei = StreckenpunkteJsonUtil.exportiereSessionErneut(requireContext(), session)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.streckenpunkte_patch4b_repeat_done, datei.absolutePath),
                        Toast.LENGTH_LONG
                    ).show()
                    aktualisiereLetztenExportText()
                }.onFailure {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.streckenpunkte_patch_error_with_reason, it.message ?: "-"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun zeigeAutomatikStartDialog(kontextOverride: AktiverTrassenKontext? = null) {
        val state = viewModel.uiState.value
        val kontext = kontextOverride ?: state.aktiverKontext
        if (kontext == null) {
            Toast.makeText(requireContext(), getString(R.string.streckenpunkte_context_missing_toast), Toast.LENGTH_LONG).show()
            return
        }
        if (state.automatikAktiv) {
            Toast.makeText(requireContext(), getString(R.string.streckenpunkte_automatik_already_running), Toast.LENGTH_SHORT).show()
            return
        }

        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_streckenpunkte_automatik_start, null, false)
        val txtKontext = v.findViewById<TextView>(R.id.txtAutomatikKontext)
        val edtKmStart = v.findViewById<EditText>(R.id.edtAutomatikKmStart)
        val edtNotiz = v.findViewById<EditText>(R.id.edtAutomatikStartNotiz)

        txtKontext.text = kontext.labelLang
        kontext.kmVon?.let { edtKmStart.setText(String.format(Locale.GERMANY, "%.3f", it)) }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Automatik starten")
            .setView(v)
            .setPositiveButton("Starten", null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val kmStart = edtKmStart.text?.toString().orEmpty().trim()
                if (kmStart.replace(',', '.').toDoubleOrNull() == null) {
                    edtKmStart.error = "Bitte KM-Start eingeben"
                    return@setOnClickListener
                }
                zeigeGpsSammelDialog("Automatik starten") { auswertung ->
                    viewModel.automatikStarten(
                        streckennummer = kontext.streckennummer,
                        bahnhof = kontext.bahnhof,
                        gleis = kontext.gleis,
                        weiche = kontext.weiche,
                        kmStartText = kmStart,
                        startNotiz = edtNotiz.text?.toString(),
                        kontextOverride = kontext
                    )
                    if (!starteAutomatikGpsFlow()) {
                        viewModel.automatikAbbrechen()
                        return@zeigeGpsSammelDialog
                    }
                    viewModel.liveStandortAktualisieren(
                        lat = auswertung.lat,
                        lon = auswertung.lon,
                        altM = auswertung.altM,
                        accuracyM = auswertung.besteAccuracyM,
                        epochMs = auswertung.epochMs
                    )
                    Toast.makeText(requireContext(), getString(R.string.streckenpunkte_automatik_started), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun starteAutomatikGpsFlow(): Boolean {
        if (automatikGpsJob != null) return true
        if (!StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
            Toast.makeText(requireContext(), getString(R.string.ui_streckenpunkt_gps_permission_missing), Toast.LENGTH_LONG).show()
            return false
        }
        automatikGpsJob = viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                StandortLiveQuelle.standortFlow(
                    context = requireContext(),
                    minTimeMs = 500L,
                    minDistanceM = 0f,
                    emitLastKnown = false,
                    glaettungFenster = 1
                ).collect { location ->
                    viewModel.liveStandortAktualisieren(
                        lat = location.latitude,
                        lon = location.longitude,
                        altM = if (location.hasAltitude()) location.altitude else null,
                        accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                        epochMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                }
            }.onFailure {
                if (it.istIgnorierbarerCoroutineAbbruch()) return@onFailure
                Toast.makeText(
                    requireContext(),
                    getString(R.string.ui_streckenpunkt_gps_error, it.message ?: "-"),
                    Toast.LENGTH_LONG
                ).show()
                stoppeAutomatikSensorFlows()
                stoppeAutomatikGpsFlow()
                viewModel.automatikAbbrechen()
            }
        }
        starteAutomatikSensorFlows()
        return true
    }

    private fun starteAutomatikSensorFlows() {
        starteAutomatikKompassFlow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !SensorLiveQuelle.hatAktivitaetsBerechtigung(requireContext())) {
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            viewModel.automatikSensorStatusSetzen(schritteAktiv = false)
        } else {
            starteAutomatikSchrittFlow()
        }
    }

    private fun starteAutomatikSchrittFlow() {
        if (automatikSchritteJob != null) return
        if (!SensorLiveQuelle.schrittZaehlerVerfuegbar(requireContext())) {
            viewModel.automatikSensorStatusSetzen(schritteAktiv = false)
            return
        }
        automatikSchritteJob = viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                SensorLiveQuelle.schrittZaehlerFlow(requireContext()).collect { schritte ->
                    viewModel.automatikSensorStatusSetzen(schritteAktiv = true)
                    viewModel.automatikSchritteAktualisieren(schritte)
                }
            }.onFailure {
                viewModel.automatikSensorStatusSetzen(schritteAktiv = false)
            }
        }
    }

    private fun starteAutomatikKompassFlow() {
        if (automatikKompassJob != null) return
        if (!SensorLiveQuelle.kompassVerfuegbar(requireContext())) {
            viewModel.automatikSensorStatusSetzen(kompassAktiv = false)
            return
        }
        automatikKompassJob = viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                SensorLiveQuelle.headingFlow(requireContext()).collect { heading ->
                    viewModel.automatikSensorStatusSetzen(kompassAktiv = true)
                    viewModel.automatikHeadingAktualisieren(heading)
                }
            }.onFailure {
                viewModel.automatikSensorStatusSetzen(kompassAktiv = false)
            }
        }
    }

    private fun stoppeAutomatikSensorFlows() {
        automatikSchritteJob?.cancel()
        automatikSchritteJob = null
        automatikKompassJob?.cancel()
        automatikKompassJob = null
    }

    private fun stoppeAutomatikGpsFlow() {
        automatikGpsJob?.cancel()
        automatikGpsJob = null
        stoppeAutomatikSensorFlows()
    }

    private fun zeigePauseDialog(vorbelegtKm: String? = null, vorbelegtNotiz: String? = null) {
        val state = viewModel.uiState.value
        if (!state.automatikAktiv) {
            Toast.makeText(requireContext(), getString(R.string.streckenpunkte_automatik_not_running), Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.automatikPausieren()

        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_streckenpunkte_km_anker, null, false)
        val txtInfo = v.findViewById<TextView>(R.id.txtAnkerInfo)
        val edtKm = v.findViewById<EditText>(R.id.edtAnkerKm)
        val edtNotiz = v.findViewById<EditText>(R.id.edtAnkerNotiz)
        val samples = mutableListOf<PauseGpsSample>()
        var letzteAuswertung: PauseAnkerAuswertung? = null
        val startedAtMs = System.currentTimeMillis()
        var sammlungAbgeschlossen = false

        state.automatikLiveKm?.let {
            edtKm.setText(String.format(Locale.GERMANY, "%.3f", it))
        }
        vorbelegtKm?.takeIf { it.isNotBlank() }?.let { edtKm.setText(it) }
        vorbelegtNotiz?.let { edtNotiz.setText(it) }

        fun updateInfo() {
            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
            val gesammeltSek = minOf(10.0, elapsedMs / 1000.0)
            letzteAuswertung = berechnePauseAnkerAuswertung(samples)
            val auswertung = letzteAuswertung
            val status = when {
                elapsedMs < 5_000L -> String.format(Locale.GERMANY, "Sammelt GPS … %.1f / 5,0 s", gesammeltSek)
                elapsedMs < 10_000L -> String.format(Locale.GERMANY, "Vorschlag wird verfeinert … %.1f / 10,0 s", gesammeltSek)
                elapsedMs < 15_000L && auswertung == null -> String.format(Locale.GERMANY, "Noch kein brauchbarer Fix … %.1f / 15,0 s", minOf(15.0, elapsedMs / 1000.0))
                else -> "GPS-Sammlung abgeschlossen"
            }
            val details = if (auswertung != null) {
                val streuung = auswertung.streuungM?.let { String.format(Locale.GERMANY, "%.1f m", it) } ?: "-"
                buildString {
                    append("GPS ").append(auswertung.gesamt)
                    append(" · Grün ").append(auswertung.gruen)
                    append(" · Gelb ").append(auswertung.gelb)
                    append(" · Rot ").append(auswertung.rot)
                    append(" · Streuung ").append(streuung)
                    if (auswertung.warnungNurGelb) {
                        append("\nWarnung: kein grüner Fix, gelber Mittelpunkt als Vorschlag.")
                    }
                }
            } else {
                "GPS 0 · Grün 0 · Gelb 0 · Rot 0 · Streuung -"
            }
            txtInfo.text = "$status\n$details"
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Pause")
            .setView(v)
            .setPositiveButton("Übernehmen", null)
            .setNeutralButton("Neu abfragen", null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()

        var samplingJob: Job? = null
        var turboSamplingJob: Job? = null
        var timerJob: Job? = null

        fun starteSamplingJob(minTimeMs: Long): Job = viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                StandortLiveQuelle.standortFlow(
                    context = requireContext(),
                    minTimeMs = minTimeMs,
                    minDistanceM = 0f,
                    emitLastKnown = false,
                    glaettungFenster = 1
                ).collect { location ->
                    if (sammlungAbgeschlossen) return@collect
                    samples += PauseGpsSample(
                        lat = location.latitude,
                        lon = location.longitude,
                        altM = if (location.hasAltitude()) location.altitude else null,
                        accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                        epochMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                }
            }.onFailure {
                if (it.istIgnorierbarerCoroutineAbbruch()) return@onFailure
                if (isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.streckenpunkte_patch_error_with_reason, it.message ?: "-"), Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            updateInfo()

            samplingJob = starteSamplingJob(minTimeMs = 250L)

            timerJob = viewLifecycleOwner.lifecycleScope.launch {
                while (dialog.isShowing && !sammlungAbgeschlossen) {
                    val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
                    val maxDauerMs = if (letzteAuswertung != null) 10_000L else 15_000L
                    if (elapsedMs >= 5_000L && (letzteAuswertung?.gruen ?: 0) == 0) {
                        if (turboSamplingJob?.isActive != true) {
                            turboSamplingJob = starteSamplingJob(minTimeMs = 125L)
                        }
                    } else if ((letzteAuswertung?.gruen ?: 0) > 0) {
                        turboSamplingJob?.cancel()
                        turboSamplingJob = null
                    }
                    if (elapsedMs >= maxDauerMs) {
                        sammlungAbgeschlossen = true
                    }
                    updateInfo()
                    val bereit = elapsedMs >= 5_000L && letzteAuswertung != null
                    positive.isEnabled = bereit
                    if (sammlungAbgeschlossen) break
                    delay(250L)
                }
                turboSamplingJob?.cancel()
                turboSamplingJob = null
                updateInfo()
                positive.isEnabled = letzteAuswertung != null
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val kmText = edtKm.text?.toString().orEmpty()
                val notizText = edtNotiz.text?.toString().orEmpty()
                dialog.dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(100L)
                    if (isAdded) {
                        zeigePauseDialog(kmText, notizText)
                    }
                }
            }

            positive.setOnClickListener {
                val auswertung = letzteAuswertung
                if (auswertung == null) {
                    Toast.makeText(requireContext(), "Noch keine brauchbaren GPS-Punkte gesammelt.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val ok = viewModel.automatikAnkerSpeichern(
                    kmText = edtKm.text?.toString().orEmpty(),
                    kmSuffix = 0,
                    bezeichnung = null,
                    notiz = edtNotiz.text?.toString(),
                    gpsLatOverride = auswertung.lat,
                    gpsLonOverride = auswertung.lon,
                    gpsAltOverrideM = auswertung.altM,
                    gpsEpochMsOverride = auswertung.epochMs
                )
                if (!ok) {
                    Toast.makeText(requireContext(), getString(R.string.streckenpunkte_automatik_anchor_failed), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Toast.makeText(requireContext(), "Pause gespeichert. Über Automatik → Weiter fortsetzen.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener {
            sammlungAbgeschlossen = true
            samplingJob?.cancel()
            turboSamplingJob?.cancel()
            timerJob?.cancel()
        }

        dialog.show()
    }

    private fun berechnePauseAnkerAuswertung(samples: List<PauseGpsSample>): PauseAnkerAuswertung? {
        if (samples.isEmpty()) return null
        val gruen = samples.filter { istGruenerPauseFix(it.accuracyM) }
        val gelb = samples.filter { istGelberPauseFix(it.accuracyM) }
        val rot = samples.size - gruen.size - gelb.size
        val verwendeteBasis = if (gruen.isNotEmpty()) gruen else gelb
        if (verwendeteBasis.isEmpty()) return null

        fun gewicht(sample: PauseGpsSample): Double {
            val acc = sample.accuracyM?.toDouble()?.coerceIn(0.5, 10.0) ?: 6.0
            return 1.0 / (acc * acc)
        }

        fun gewichteterMittelpunkt(basis: List<PauseGpsSample>): Pair<Double, Double> {
            var sumGew = 0.0
            var sumLat = 0.0
            var sumLon = 0.0
            basis.forEach { sample ->
                val g = gewicht(sample)
                sumGew += g
                sumLat += sample.lat * g
                sumLon += sample.lon * g
            }
            if (sumGew <= 0.0) return basis.last().lat to basis.last().lon
            return (sumLat / sumGew) to (sumLon / sumGew)
        }

        val initialCenter = gewichteterMittelpunkt(verwendeteBasis)
        val gefiltert = verwendeteBasis.filter {
            distanzMeter(initialCenter.first, initialCenter.second, it.lat, it.lon) <= 4.0
        }.ifEmpty { verwendeteBasis }
        val center = gewichteterMittelpunkt(gefiltert)
        val streuung = if (gefiltert.isEmpty()) null else {
            kotlin.math.sqrt(gefiltert.map { sample ->
                val d = distanzMeter(center.first, center.second, sample.lat, sample.lon)
                d * d
            }.average())
        }
        val alt = gefiltert.mapNotNull { it.altM }.takeIf { it.isNotEmpty() }?.average()
        val epochMs = gefiltert.maxOfOrNull { it.epochMs } ?: System.currentTimeMillis()
        return PauseAnkerAuswertung(
            lat = center.first,
            lon = center.second,
            altM = alt,
            epochMs = epochMs,
            gruen = gruen.size,
            gelb = gelb.size,
            rot = rot,
            gesamt = samples.size,
            streuungM = streuung,
            warnungNurGelb = gruen.isEmpty() && gelb.isNotEmpty(),
            besteAccuracyM = gefiltert.mapNotNull { it.accuracyM }.minOrNull()
        )
    }

    private fun istGruenerPauseFix(accuracyM: Float?): Boolean = accuracyM != null && accuracyM <= 2f

    private fun istGelberPauseFix(accuracyM: Float?): Boolean = accuracyM != null && accuracyM > 2f && accuracyM <= 3f

    private fun distanzMeter(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    private fun zeigeGpsSammelDialog(
        titel: String,
        onUebernehmen: (PauseAnkerAuswertung) -> Unit
    ) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_gps_sammelstatus, null, false)
        val txtInfo = v.findViewById<TextView>(R.id.txtGpsSammelInfo)
        val samples = mutableListOf<PauseGpsSample>()
        var letzteAuswertung: PauseAnkerAuswertung? = null
        val startedAtMs = System.currentTimeMillis()
        var sammlungAbgeschlossen = false

        fun updateInfo() {
            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
            val gesammeltSek = minOf(15.0, elapsedMs / 1000.0)
            letzteAuswertung = berechnePauseAnkerAuswertung(samples)
            val auswertung = letzteAuswertung
            val status = when {
                elapsedMs < 5_000L -> String.format(Locale.GERMANY, "Sammelt GPS … %.1f / 5,0 s", gesammeltSek)
                elapsedMs < 10_000L -> String.format(Locale.GERMANY, "Vorschlag wird verfeinert … %.1f / 10,0 s", gesammeltSek)
                elapsedMs < 15_000L && auswertung == null -> String.format(Locale.GERMANY, "Noch kein brauchbarer Fix … %.1f / 15,0 s", gesammeltSek)
                else -> "GPS-Sammlung abgeschlossen"
            }
            val details = if (auswertung != null) {
                val streuung = auswertung.streuungM?.let { String.format(Locale.GERMANY, "%.1f m", it) } ?: "-"
                buildString {
                    append("GPS ").append(auswertung.gesamt)
                    append(" · Grün ").append(auswertung.gruen)
                    append(" · Gelb ").append(auswertung.gelb)
                    append(" · Rot ").append(auswertung.rot)
                    append(" · Streuung ").append(streuung)
                    if (auswertung.warnungNurGelb) {
                        append("\nWarnung: kein grüner Fix, gelber Mittelpunkt als Vorschlag.")
                    }
                }
            } else {
                "GPS 0 · Grün 0 · Gelb 0 · Rot 0 · Streuung -"
            }
            txtInfo.text = "$status\n$details"
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(titel)
            .setView(v)
            .setPositiveButton("Übernehmen", null)
            .setNeutralButton("Neu abfragen", null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()

        var samplingJob: Job? = null
        var turboSamplingJob: Job? = null
        var timerJob: Job? = null

        fun starteSamplingJob(minTimeMs: Long): Job = viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                StandortLiveQuelle.standortFlow(
                    context = requireContext(),
                    minTimeMs = minTimeMs,
                    minDistanceM = 0f,
                    emitLastKnown = false,
                    glaettungFenster = 1
                ).collect { location ->
                    if (sammlungAbgeschlossen) return@collect
                    samples += PauseGpsSample(
                        lat = location.latitude,
                        lon = location.longitude,
                        altM = if (location.hasAltitude()) location.altitude else null,
                        accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                        epochMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                }
            }.onFailure {
                if (it.istIgnorierbarerCoroutineAbbruch()) return@onFailure
                if (isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.streckenpunkte_patch_error_with_reason, it.message ?: "-"), Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            updateInfo()

            samplingJob = starteSamplingJob(minTimeMs = 250L)

            timerJob = viewLifecycleOwner.lifecycleScope.launch {
                while (dialog.isShowing && !sammlungAbgeschlossen) {
                    val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
                    val maxDauerMs = if (letzteAuswertung != null) 10_000L else 15_000L
                    if (elapsedMs >= 5_000L && (letzteAuswertung?.gruen ?: 0) == 0) {
                        if (turboSamplingJob?.isActive != true) {
                            turboSamplingJob = starteSamplingJob(minTimeMs = 125L)
                        }
                    } else if ((letzteAuswertung?.gruen ?: 0) > 0) {
                        turboSamplingJob?.cancel()
                        turboSamplingJob = null
                    }
                    if (elapsedMs >= maxDauerMs) {
                        sammlungAbgeschlossen = true
                    }
                    updateInfo()
                    positive.isEnabled = elapsedMs >= 5_000L && letzteAuswertung != null
                    if (sammlungAbgeschlossen) break
                    delay(250L)
                }
                turboSamplingJob?.cancel()
                turboSamplingJob = null
                updateInfo()
                positive.isEnabled = letzteAuswertung != null
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(100L)
                    if (isAdded) {
                        zeigeGpsSammelDialog(titel, onUebernehmen)
                    }
                }
            }

            positive.setOnClickListener {
                val auswertung = letzteAuswertung
                if (auswertung == null) {
                    Toast.makeText(requireContext(), "Noch keine brauchbaren GPS-Punkte gesammelt.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                onUebernehmen(auswertung)
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener {
            sammlungAbgeschlossen = true
            samplingJob?.cancel()
            turboSamplingJob?.cancel()
            timerJob?.cancel()
        }

        dialog.show()
    }

    private fun stoppeAutomatikUndExportiere() {
        val state = viewModel.uiState.value
        val snapshot = viewModel.automatikStoppen()
        stoppeAutomatikGpsFlow()
        if (snapshot == null) {
            Toast.makeText(requireContext(), getString(R.string.streckenpunkte_automatik_not_running), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val datei = StreckenpunkteJsonUtil.exportiereAutomatikSession(
                context = requireContext(),
                baustellenname = state.baustellenname,
                exportLabel = state.aktiverKontext?.labelKurz,
                snapshot = snapshot
            )
            Toast.makeText(
                requireContext(),
                getString(R.string.streckenpunkte_automatik_export_done, datei.absolutePath),
                Toast.LENGTH_LONG
            ).show()
        }.onFailure {
            Toast.makeText(
                requireContext(),
                getString(R.string.streckenpunkte_patch_error_with_reason, it.message ?: "-"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun dialogStreckenpunkt(
        punkt: StreckenpunktEntity?,
        kontextOverride: AktiverTrassenKontext? = null
    ) {
        val state = viewModel.uiState.value
        val punktKontext = punkt?.let { kontextAusPunkt(it) }
        val effektiverKontext = kontextOverride ?: punktKontext ?: state.aktiverKontext
        if (punkt == null && effektiverKontext == null) {
            waehleKontextFuerAktion(fuerAutomatik = false)
            return
        }
        if (punkt == null && effektiverKontext?.art == TrassenKontextArt.BAHNHOF_WEICHE) {
            dialogWeichenpunktAuswahl(effektiverKontext)
            return
        }
        if (punkt != null && punkt.typ in listOf(StreckenpunktTyp.WEICHE_WA, StreckenpunktTyp.WEICHE_WS, StreckenpunktTyp.WEICHE_WAZ)) {
            val kontext = effektiverKontext ?: return
            dialogWeichenpunktDetail(kontext, punkt, punkt.typ)
            return
        }
        val kontext = effektiverKontext ?: return

        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_neuer_streckenpunkt, null, false)
        val txtKontext = v.findViewById<TextView>(R.id.txtPunktKontext)
        val edtKm = v.findViewById<EditText>(R.id.edtKm)
        val edtNotiz = v.findViewById<EditText>(R.id.edtNotiz)
        val txtGpsStatus = v.findViewById<TextView>(R.id.txtGpsStatus)
        val btnGpsUebernehmen = v.findViewById<Button>(R.id.btnGpsUebernehmen)

        txtKontext.text = kontext.labelLang
        var gespeicherteGpsLat: Double? = null
        var gespeicherteGpsLon: Double? = null
        var gespeicherteGpsAltM: Double? = null
        var gespeicherteGpsZeitMs: Long? = null
        var gespeicherteGpsAccuracyM: Float? = null
        var letzteLocation: Location? = letzteVorwaermLocation?.let { Location(it) }

        fun aktualisiereGpsText() {
            val liveText = letzteLocation?.let { location ->
                val accText = formatGenauigkeitMeter(if (location.hasAccuracy()) location.accuracy else null)
                val basis = getString(R.string.ui_streckenpunkt_gps_live, location.latitude, location.longitude, accText)
                val zeitText = "Zeit des Fix: ${formatGpsFixZeit(location.time)}"
                val alterText = gpsAlterMs(location)?.let { "Alter: ${it / 1000}s" } ?: "Alter: -"
                "$basis\n$zeitText · $alterText"
            } ?: getString(R.string.ui_streckenpunkt_gps_none)
            val gespeichertText = if (gespeicherteGpsLat != null && gespeicherteGpsLon != null) {
                val basis = getString(R.string.ui_streckenpunkt_gps_saved, gespeicherteGpsLat ?: 0.0, gespeicherteGpsLon ?: 0.0)
                val zeitText = "Zeit des Fix: ${formatGpsFixZeit(gespeicherteGpsZeitMs)}"
                val accText = "Genauigkeit: ${formatGenauigkeitMeter(gespeicherteGpsAccuracyM)}"
                "$basis\n$zeitText · $accText"
            } else {
                getString(R.string.ui_streckenpunkt_gps_not_saved)
            }
            txtGpsStatus.text = "$liveText\n$gespeichertText"
        }

        if (punkt != null) {
            if (hatKm(punkt)) edtKm.setText(String.format(Locale.GERMANY, "%.3f", punkt.km))
            edtNotiz.setText(punkt.notiz.orEmpty())
            if (punkt.gpsLat != 0.0 || punkt.gpsLon != 0.0) {
                gespeicherteGpsLat = punkt.gpsLat
                gespeicherteGpsLon = punkt.gpsLon
                gespeicherteGpsAltM = punkt.gpsAltM
            }
        } else {
            kontext.kmVon?.let { edtKm.setText(String.format(Locale.GERMANY, "%.3f", it)) }
        }
        aktualisiereGpsText()

        btnGpsUebernehmen.setOnClickListener {
            zeigeGpsSammelDialog("GPS übernehmen") { auswertung ->
                gespeicherteGpsLat = auswertung.lat
                gespeicherteGpsLon = auswertung.lon
                gespeicherteGpsAltM = auswertung.altM
                gespeicherteGpsZeitMs = auswertung.epochMs
                gespeicherteGpsAccuracyM = auswertung.besteAccuracyM
                aktualisiereGpsText()
                if (auswertung.warnungNurGelb) {
                    Toast.makeText(requireContext(), "GPS übernommen (gelber Vorschlag).", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.ui_streckenpunkt_gps_taken), Toast.LENGTH_SHORT).show()
                }
            }
        }

        val gpsJob: Job? = if (StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    StandortLiveQuelle.standortFlow(
                        requireContext(),
                        emitLastKnown = false,
                        glaettungFenster = 1
                    ).collect { location ->
                        letzteLocation = location
                        aktualisiereGpsText()
                    }
                }.onFailure {
                    if (it.istIgnorierbarerCoroutineAbbruch()) return@onFailure
                    txtGpsStatus.text = getString(R.string.ui_streckenpunkt_gps_error, it.message ?: "-")
                }
            }
        } else {
            txtGpsStatus.text = getString(R.string.ui_streckenpunkt_gps_permission_missing)
            null
        }

        val dialogTitel = if (punkt == null) {
            getString(R.string.streckenpunkte_context_new_point_title, kontext.labelKurz)
        } else {
            getString(R.string.ui_streckenpunkt_dialog_edit_title)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(dialogTitel)
            .setView(v)
            .setPositiveButton(getString(R.string.btn_save), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .apply {
                if (punkt != null) {
                    setNeutralButton(getString(R.string.btn_delete), null)
                }
            }
            .create()

        dialog.setOnDismissListener { gpsJob?.cancel() }
        dialog.setOnShowListener {
            if (punkt != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.ui_streckenpunkt_delete_title))
                        .setMessage(getString(R.string.ui_streckenpunkt_delete_message))
                        .setPositiveButton(getString(R.string.ui_streckenpunkt_delete_confirm)) { _, _ ->
                            viewModel.loeschen(punkt)
                            dialog.dismiss()
                        }
                        .setNegativeButton(getString(R.string.btn_cancel), null)
                        .show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val kmText = edtKm.text?.toString().orEmpty().trim()
                if (kmText.replace(',', '.').toDoubleOrNull() == null) {
                    edtKm.error = getString(R.string.ui_streckenpunkt_error_km)
                    return@setOnClickListener
                }
                val notiz = edtNotiz.text?.toString()
                val bezeichnung = defaultBezeichnungFuerKontext(kontext)
                if (punkt == null) {
                    viewModel.neuenPunktAnlegen(
                        kmText = kmText,
                        bezeichnung = bezeichnung,
                        notiz = notiz,
                        kmSuffix = 0,
                        typ = StreckenpunktTyp.EXAKT,
                        typFreitext = "KM-Anker",
                        streckennummer = kontext.streckennummer,
                        bahnhof = kontext.bahnhof,
                        gleis = kontext.gleis,
                        weiche = kontext.weiche,
                        objektKennung = kontext.objektKennung,
                        gpsLat = gespeicherteGpsLat,
                        gpsLon = gespeicherteGpsLon,
                        gpsAltM = gespeicherteGpsAltM,
                        kontextOverride = kontext
                    )
                } else {
                    viewModel.punktAktualisieren(
                        punkt = punkt,
                        kmText = kmText,
                        bezeichnung = bezeichnung,
                        notiz = notiz,
                        kmSuffix = punkt.kmSuffix,
                        typ = punkt.typ,
                        typFreitext = punkt.typFreitext,
                        streckennummer = punkt.streckennummer,
                        bahnhof = punkt.bahnhof,
                        gleis = punkt.gleis,
                        weiche = punkt.weiche,
                        objektKennung = punkt.objektKennung,
                        gpsLat = gespeicherteGpsLat,
                        gpsLon = gespeicherteGpsLon,
                        gpsAltM = gespeicherteGpsAltM
                    )
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun dialogWeichenpunktAuswahl(kontext: AktiverTrassenKontext) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_weichenpunkt_detail, null, false)
        val txtKontext = v.findViewById<TextView>(R.id.txtWeicheDetailKontext)
        val edtKm = v.findViewById<EditText>(R.id.edtWeicheKm)
        val edtNotiz = v.findViewById<EditText>(R.id.edtWeicheNotiz)
        val txtGpsStatus = v.findViewById<TextView>(R.id.txtWeicheGpsStatus)
        val btnGpsUebernehmen = v.findViewById<Button>(R.id.btnWeicheGpsUebernehmen)
        val btnTypWa = v.findViewById<Button>(R.id.btnWeicheTypWa)
        val btnTypWe = v.findViewById<Button>(R.id.btnWeicheTypWe)
        val btnTypWez = v.findViewById<Button>(R.id.btnWeicheTypWez)

        val drafts = linkedMapOf(
            StreckenpunktTyp.WEICHE_WA to WeichenErfassungsDraft(),
            StreckenpunktTyp.WEICHE_WS to WeichenErfassungsDraft(),
            StreckenpunktTyp.WEICHE_WAZ to WeichenErfassungsDraft()
        )

        var aktiverTyp = StreckenpunktTyp.WEICHE_WA
        var letzteLocation: Location? = letzteVorwaermLocation?.let { Location(it) }

        fun typLabelVon(wert: StreckenpunktTyp): String = when (wert) {
            StreckenpunktTyp.WEICHE_WA -> "WA"
            StreckenpunktTyp.WEICHE_WS -> "WE"
            StreckenpunktTyp.WEICHE_WAZ -> "WEZ"
            else -> "WA"
        }

        fun aktuellerDraft(): WeichenErfassungsDraft = drafts.getValue(aktiverTyp)

        fun sichereAktiveEingaben() {
            val draft = aktuellerDraft()
            draft.kmText = edtKm.text?.toString().orEmpty().trim()
            draft.notiz = edtNotiz.text?.toString().orEmpty()
        }

        fun aktualisiereGpsText() {
            val draft = aktuellerDraft()
            val liveText = letzteLocation?.let { location ->
                val accText = formatGenauigkeitMeter(if (location.hasAccuracy()) location.accuracy else null)
                val basis = getString(R.string.ui_streckenpunkt_gps_live, location.latitude, location.longitude, accText)
                val zeitText = "Zeit des Fix: ${formatGpsFixZeit(location.time)}"
                val alterText = gpsAlterMs(location)?.let { "Alter: ${it / 1000}s" } ?: "Alter: -"
                "$basis\n$zeitText · $alterText"
            } ?: getString(R.string.ui_streckenpunkt_gps_none)
            val gespeichertText = if (draft.gpsLat != null && draft.gpsLon != null) {
                val basis = getString(R.string.ui_streckenpunkt_gps_saved, draft.gpsLat ?: 0.0, draft.gpsLon ?: 0.0)
                val zeitText = "Zeit des Fix: ${formatGpsFixZeit(draft.gpsZeitMs)}"
                val accText = "Genauigkeit: ${formatGenauigkeitMeter(draft.gpsAccuracyM)}"
                "$basis\n$zeitText · $accText"
            } else {
                getString(R.string.ui_streckenpunkt_gps_not_saved)
            }
            txtGpsStatus.text = "$liveText\n$gespeichertText"
        }

        fun ladeAktivenDraft() {
            val draft = aktuellerDraft()
            edtKm.setText(draft.kmText)
            edtKm.setSelection(edtKm.text?.length ?: 0)
            edtNotiz.setText(draft.notiz)
            txtKontext.text = "${kontext.labelLang} · ${typLabelVon(aktiverTyp)}"
            btnTypWa.alpha = if (aktiverTyp == StreckenpunktTyp.WEICHE_WA) 1f else 0.6f
            btnTypWe.alpha = if (aktiverTyp == StreckenpunktTyp.WEICHE_WS) 1f else 0.6f
            btnTypWez.alpha = if (aktiverTyp == StreckenpunktTyp.WEICHE_WAZ) 1f else 0.6f
            aktualisiereGpsText()
        }

        fun wechsleZu(neuerTyp: StreckenpunktTyp) {
            sichereAktiveEingaben()
            aktiverTyp = neuerTyp
            ladeAktivenDraft()
        }

        btnTypWa.setOnClickListener { wechsleZu(StreckenpunktTyp.WEICHE_WA) }
        btnTypWe.setOnClickListener { wechsleZu(StreckenpunktTyp.WEICHE_WS) }
        btnTypWez.setOnClickListener { wechsleZu(StreckenpunktTyp.WEICHE_WAZ) }
        ladeAktivenDraft()

        btnGpsUebernehmen.setOnClickListener {
            zeigeGpsSammelDialog("GPS übernehmen") { auswertung ->
                sichereAktiveEingaben()
                val draft = aktuellerDraft()
                draft.gpsLat = auswertung.lat
                draft.gpsLon = auswertung.lon
                draft.gpsAltM = auswertung.altM
                draft.gpsZeitMs = auswertung.epochMs
                draft.gpsAccuracyM = auswertung.besteAccuracyM
                aktualisiereGpsText()
                if (auswertung.warnungNurGelb) {
                    Toast.makeText(requireContext(), "GPS übernommen (gelber Vorschlag).", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.ui_streckenpunkt_gps_taken), Toast.LENGTH_SHORT).show()
                }
            }
        }

        val gpsJob: Job? = if (StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    StandortLiveQuelle.standortFlow(
                        requireContext(),
                        emitLastKnown = false,
                        glaettungFenster = 1
                    ).collect { location ->
                        letzteLocation = location
                        aktualisiereGpsText()
                    }
                }.onFailure {
                    if (it.istIgnorierbarerCoroutineAbbruch()) return@onFailure
                    txtGpsStatus.text = getString(R.string.ui_streckenpunkt_gps_error, it.message ?: "-")
                }
            }
        } else {
            txtGpsStatus.text = getString(R.string.ui_streckenpunkt_gps_permission_missing)
            null
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Weichenpunkt")
            .setView(v)
            .setPositiveButton(getString(R.string.btn_save), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()

        dialog.setOnDismissListener { gpsJob?.cancel() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                sichereAktiveEingaben()
                val aktiveDrafts = drafts.filterValues { draft ->
                    draft.kmText.isNotBlank() || draft.notiz.isNotBlank() || draft.gpsLat != null || draft.gpsLon != null
                }
                if (aktiveDrafts.isEmpty()) {
                    edtKm.error = getString(R.string.ui_streckenpunkt_error_km)
                    return@setOnClickListener
                }
                aktiveDrafts.forEach { (typ, draft) ->
                    if (draft.kmText.replace(',', '.').toDoubleOrNull() == null) {
                        aktiverTyp = typ
                        ladeAktivenDraft()
                        edtKm.error = getString(R.string.ui_streckenpunkt_error_km)
                        return@setOnClickListener
                    }
                }
                aktiveDrafts.forEach { (typ, draft) ->
                    val typLabel = typLabelVon(typ)
                    val bezeichnung = "${kontext.weiche.orEmpty()} $typLabel".trim()
                    viewModel.neuenPunktAnlegen(
                        kmText = draft.kmText,
                        bezeichnung = bezeichnung,
                        notiz = draft.notiz,
                        kmSuffix = 0,
                        typ = typ,
                        typFreitext = typLabel,
                        streckennummer = null,
                        bahnhof = kontext.bahnhof,
                        gleis = null,
                        weiche = kontext.weiche,
                        objektKennung = kontext.weiche,
                        gpsLat = draft.gpsLat,
                        gpsLon = draft.gpsLon,
                        gpsAltM = draft.gpsAltM,
                        kontextOverride = kontext
                    )
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun dialogWeichenpunktDetail(
        kontext: AktiverTrassenKontext,
        punkt: StreckenpunktEntity? = null,
        typ: StreckenpunktTyp
    ) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_weichenpunkt_detail, null, false)
        val txtKontext = v.findViewById<TextView>(R.id.txtWeicheDetailKontext)
        val edtKm = v.findViewById<EditText>(R.id.edtWeicheKm)
        val edtNotiz = v.findViewById<EditText>(R.id.edtWeicheNotiz)
        val txtGpsStatus = v.findViewById<TextView>(R.id.txtWeicheGpsStatus)
        val btnGpsUebernehmen = v.findViewById<Button>(R.id.btnWeicheGpsUebernehmen)
        val btnTypWa = v.findViewById<Button>(R.id.btnWeicheTypWa)
        val btnTypWe = v.findViewById<Button>(R.id.btnWeicheTypWe)
        val btnTypWez = v.findViewById<Button>(R.id.btnWeicheTypWez)

        var ausgewaehlterTyp = typ
        fun typLabelVon(wert: StreckenpunktTyp): String = when (wert) {
            StreckenpunktTyp.WEICHE_WA -> "WA"
            StreckenpunktTyp.WEICHE_WS -> "WE"
            StreckenpunktTyp.WEICHE_WAZ -> "WEZ"
            else -> "WA"
        }
        fun aktualisiereTypUi() {
            btnTypWa.alpha = if (ausgewaehlterTyp == StreckenpunktTyp.WEICHE_WA) 1f else 0.6f
            btnTypWe.alpha = if (ausgewaehlterTyp == StreckenpunktTyp.WEICHE_WS) 1f else 0.6f
            btnTypWez.alpha = if (ausgewaehlterTyp == StreckenpunktTyp.WEICHE_WAZ) 1f else 0.6f
            txtKontext.text = "${kontext.labelLang} · ${typLabelVon(ausgewaehlterTyp)}"
        }
        btnTypWa.setOnClickListener { ausgewaehlterTyp = StreckenpunktTyp.WEICHE_WA; aktualisiereTypUi() }
        btnTypWe.setOnClickListener { ausgewaehlterTyp = StreckenpunktTyp.WEICHE_WS; aktualisiereTypUi() }
        btnTypWez.setOnClickListener { ausgewaehlterTyp = StreckenpunktTyp.WEICHE_WAZ; aktualisiereTypUi() }
        aktualisiereTypUi()

        var gespeicherteGpsLat: Double? = null
        var gespeicherteGpsLon: Double? = null
        var gespeicherteGpsAltM: Double? = null
        var gespeicherteGpsZeitMs: Long? = null
        var gespeicherteGpsAccuracyM: Float? = null
        var letzteLocation: Location? = letzteVorwaermLocation?.let { Location(it) }

        fun aktualisiereGpsText() {
            val liveText = letzteLocation?.let { location ->
                val accText = formatGenauigkeitMeter(if (location.hasAccuracy()) location.accuracy else null)
                val basis = getString(R.string.ui_streckenpunkt_gps_live, location.latitude, location.longitude, accText)
                val zeitText = "Zeit des Fix: ${formatGpsFixZeit(location.time)}"
                val alterText = gpsAlterMs(location)?.let { "Alter: ${it / 1000}s" } ?: "Alter: -"
                "$basis\n$zeitText · $alterText"
            } ?: getString(R.string.ui_streckenpunkt_gps_none)
            val gespeichertText = if (gespeicherteGpsLat != null && gespeicherteGpsLon != null) {
                val basis = getString(R.string.ui_streckenpunkt_gps_saved, gespeicherteGpsLat ?: 0.0, gespeicherteGpsLon ?: 0.0)
                val zeitText = "Zeit des Fix: ${formatGpsFixZeit(gespeicherteGpsZeitMs)}"
                val accText = "Genauigkeit: ${formatGenauigkeitMeter(gespeicherteGpsAccuracyM)}"
                "$basis\n$zeitText · $accText"
            } else {
                getString(R.string.ui_streckenpunkt_gps_not_saved)
            }
            txtGpsStatus.text = "$liveText\n$gespeichertText"
        }

        if (punkt != null) {
            if (hatKm(punkt)) edtKm.setText(String.format(Locale.GERMANY, "%.3f", punkt.km))
            edtNotiz.setText(punkt.notiz.orEmpty())
            if (punkt.gpsLat != 0.0 || punkt.gpsLon != 0.0) {
                gespeicherteGpsLat = punkt.gpsLat
                gespeicherteGpsLon = punkt.gpsLon
                gespeicherteGpsAltM = punkt.gpsAltM
            }
        }
        aktualisiereGpsText()

        btnGpsUebernehmen.setOnClickListener {
            zeigeGpsSammelDialog("GPS übernehmen") { auswertung ->
                gespeicherteGpsLat = auswertung.lat
                gespeicherteGpsLon = auswertung.lon
                gespeicherteGpsAltM = auswertung.altM
                gespeicherteGpsZeitMs = auswertung.epochMs
                gespeicherteGpsAccuracyM = auswertung.besteAccuracyM
                aktualisiereGpsText()
                if (auswertung.warnungNurGelb) {
                    Toast.makeText(requireContext(), "GPS übernommen (gelber Vorschlag).", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.ui_streckenpunkt_gps_taken), Toast.LENGTH_SHORT).show()
                }
            }
        }

        val gpsJob: Job? = if (StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    StandortLiveQuelle.standortFlow(
                        requireContext(),
                        emitLastKnown = false,
                        glaettungFenster = 1
                    ).collect { location ->
                        letzteLocation = location
                        aktualisiereGpsText()
                    }
                }.onFailure {
                    if (it.istIgnorierbarerCoroutineAbbruch()) return@onFailure
                    txtGpsStatus.text = getString(R.string.ui_streckenpunkt_gps_error, it.message ?: "-")
                }
            }
        } else {
            txtGpsStatus.text = getString(R.string.ui_streckenpunkt_gps_permission_missing)
            null
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Weichenpunkt")
            .setView(v)
            .setPositiveButton(getString(R.string.btn_save), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .apply {
                if (punkt != null) {
                    setNeutralButton(getString(R.string.btn_delete), null)
                }
            }
            .create()

        dialog.setOnDismissListener { gpsJob?.cancel() }
        dialog.setOnShowListener {
            if (punkt != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.ui_streckenpunkt_delete_title))
                        .setMessage(getString(R.string.ui_streckenpunkt_delete_message))
                        .setPositiveButton(getString(R.string.ui_streckenpunkt_delete_confirm)) { _, _ ->
                            viewModel.loeschen(punkt)
                            dialog.dismiss()
                        }
                        .setNegativeButton(getString(R.string.btn_cancel), null)
                        .show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val kmText = edtKm.text?.toString().orEmpty().trim()
                if (kmText.replace(',', '.').toDoubleOrNull() == null) {
                    edtKm.error = getString(R.string.ui_streckenpunkt_error_km)
                    return@setOnClickListener
                }
                val typLabel = typLabelVon(ausgewaehlterTyp)
                val bezeichnung = "${kontext.weiche.orEmpty()} $typLabel".trim()
                if (punkt == null) {
                    viewModel.neuenPunktAnlegen(
                        kmText = kmText,
                        bezeichnung = bezeichnung,
                        notiz = edtNotiz.text?.toString(),
                        kmSuffix = 0,
                        typ = ausgewaehlterTyp,
                        typFreitext = typLabel,
                        streckennummer = null,
                        bahnhof = kontext.bahnhof,
                        gleis = null,
                        weiche = kontext.weiche,
                        objektKennung = kontext.weiche,
                        gpsLat = gespeicherteGpsLat,
                        gpsLon = gespeicherteGpsLon,
                        gpsAltM = gespeicherteGpsAltM,
                        kontextOverride = kontext
                    )
                } else {
                    viewModel.punktAktualisieren(
                        punkt = punkt,
                        kmText = kmText,
                        bezeichnung = bezeichnung,
                        notiz = edtNotiz.text?.toString(),
                        kmSuffix = 0,
                        typ = ausgewaehlterTyp,
                        typFreitext = typLabel,
                        streckennummer = punkt.streckennummer,
                        bahnhof = kontext.bahnhof,
                        gleis = null,
                        weiche = kontext.weiche,
                        objektKennung = kontext.weiche,
                        gpsLat = gespeicherteGpsLat,
                        gpsLon = gespeicherteGpsLon,
                        gpsAltM = gespeicherteGpsAltM
                    )
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun waehleKontextFuerAktion(fuerAutomatik: Boolean, nurKontextSetzen: Boolean = false) {
        val items = arrayOf("Freie Strecke", "Bahnhof")
        val titel = when {
            nurKontextSetzen -> "Auswahl ändern"
            fuerAutomatik -> "Automatik ohne Baustelle/Kontext"
            else -> "Streckenpunkt ohne Baustelle/Kontext"
        }
        AlertDialog.Builder(requireContext())
            .setTitle(titel)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> zeigeDialogFreieStrecke(fuerAutomatik, nurKontextSetzen)
                    1 -> zeigeDialogBahnhofKontext(fuerAutomatik, nurKontextSetzen)
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun zeigeDialogFreieStrecke(fuerAutomatik: Boolean, nurKontextSetzen: Boolean = false) {
        val input = AutoCompleteTextView(requireContext())
        input.hint = "Streckennummer (z. B. 1234-0)"
        bindeStreckenVorschlaege(input)
        AlertDialog.Builder(requireContext())
            .setTitle("Freie Strecke")
            .setView(input)
            .setPositiveButton(getString(R.string.btn_save), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val strecke = normalizeStrecke(input.text?.toString().orEmpty())
                        if (strecke.isBlank()) {
                            input.error = "Bitte Streckennummer eingeben"
                            return@setOnClickListener
                        }
                        val kontext = AktiverTrassenKontext(
                            art = TrassenKontextArt.STRECKE,
                            streckennummer = strecke,
                            labelKurz = strecke,
                            labelLang = "Freie Strecke $strecke",
                            objektKennung = strecke
                        )
                        starteAktionMitAusgewaehltemKontext(kontext, fuerAutomatik, nurKontextSetzen)
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun zeigeDialogBahnhofKontext(fuerAutomatik: Boolean, nurKontextSetzen: Boolean = false) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_streckenpunkte_bahnhof_kontext, null, false)
        val actBahnhof = v.findViewById<AutoCompleteTextView>(R.id.actBahnhof)
        val btnArtGleis = v.findViewById<Button>(R.id.btnArtGleis)
        val btnArtWeiche = v.findViewById<Button>(R.id.btnArtWeiche)
        val edtGleis = v.findViewById<EditText>(R.id.edtBahnhofGleis)
        val edtWeiche = v.findViewById<EditText>(R.id.edtBahnhofWeiche)
        val btnNeuenBahnhofAnlegen = v.findViewById<Button>(R.id.btnNeuenBahnhofAnlegen)

        var istWeiche = false
        fun refreshArtUi() {
            edtGleis.isVisible = !istWeiche
            edtWeiche.isVisible = istWeiche
            btnArtGleis.alpha = if (istWeiche) 0.6f else 1f
            btnArtWeiche.alpha = if (istWeiche) 1f else 0.6f
        }
        btnArtGleis.setOnClickListener { istWeiche = false; refreshArtUi() }
        btnArtWeiche.setOnClickListener { istWeiche = true; refreshArtUi() }
        refreshArtUi()
        bindeBahnhofVorschlaege(actBahnhof)
        val letzterBahnhof = BahnhofSucheUtil.ladeLetzteEingabe(requireContext())
        if (actBahnhof.text.isNullOrBlank() && letzterBahnhof.isNotBlank()) {
            actBahnhof.setText(letzterBahnhof, false)
            actBahnhof.setSelection(actBahnhof.text?.length ?: 0)
        }

        btnNeuenBahnhofAnlegen.setOnClickListener {
            zeigeNeuenBahnhofDialog { vorschlag ->
                if (bahnhofVorschlaege.none { it.code.equals(vorschlag.code, true) }) {
                    bahnhofVorschlaege.add(0, vorschlag)
                }
                actBahnhof.setText(vorschlag.display)
                actBahnhof.setSelection(actBahnhof.text?.length ?: 0)
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Bahnhof")
            .setView(v)
            .setPositiveButton(getString(R.string.btn_save), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val raw = actBahnhof.text?.toString().orEmpty().trim()
                        if (raw.isBlank()) {
                            actBahnhof.error = "Bitte Bahnhof eingeben"
                            return@setOnClickListener
                        }
                        val vorschlag = findeBahnhofVorschlag(raw)
                        val code = extrahiereBahnhofCode(raw, vorschlag)
                        val name = extrahiereBahnhofName(raw, vorschlag)
                        if (code.isBlank()) {
                            actBahnhof.error = "Bitte Bahnhof oder Kürzel eingeben"
                            return@setOnClickListener
                        }
                        val gleis = edtGleis.text?.toString().orEmpty().trim()
                        val weiche = edtWeiche.text?.toString().orEmpty().trim()
                        if (!istWeiche && gleis.isBlank()) {
                            edtGleis.error = "Bitte Gleis eingeben"
                            return@setOnClickListener
                        }
                        if (istWeiche && weiche.isBlank()) {
                            edtWeiche.error = "Bitte Weiche eingeben"
                            return@setOnClickListener
                        }
                        BahnhofSucheUtil.speichereLetzteEingabe(requireContext(), vorschlag?.display ?: raw)
                        val kontext = if (istWeiche) {
                            AktiverTrassenKontext(
                                art = TrassenKontextArt.BAHNHOF_WEICHE,
                                bahnhof = code,
                                weiche = weiche,
                                labelKurz = listOfNotNull(code, weiche).joinToString(" · "),
                                labelLang = buildString {
                                    append("Bahnhof ").append(code)
                                    if (!name.isNullOrBlank() && !name.equals(code, true)) append(" – ").append(name)
                                    append(" · Weiche ").append(weiche)
                                },
                                objektKennung = weiche
                            )
                        } else {
                            AktiverTrassenKontext(
                                art = TrassenKontextArt.BAHNHOF_GLEIS,
                                bahnhof = code,
                                gleis = gleis,
                                labelKurz = listOfNotNull(code, gleis).joinToString(" · "),
                                labelLang = buildString {
                                    append("Bahnhof ").append(code)
                                    if (!name.isNullOrBlank() && !name.equals(code, true)) append(" – ").append(name)
                                    append(" · Gleis ").append(gleis)
                                },
                                objektKennung = gleis
                            )
                        }
                        starteAktionMitAusgewaehltemKontext(kontext, fuerAutomatik, nurKontextSetzen)
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun zeigeNeuenBahnhofDialog(onCreated: (BahnhofVorschlag) -> Unit) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_neuer_bahnhof, null, false)
        val edtCode = v.findViewById<EditText>(R.id.edtNeuerBahnhofCode)
        val edtName = v.findViewById<EditText>(R.id.edtNeuerBahnhofName)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Neuen Bahnhof anlegen")
            .setView(v)
            .setPositiveButton(getString(R.string.btn_save), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = edtCode.text?.toString().orEmpty().trim().uppercase(Locale.GERMANY)
                val name = edtName.text?.toString().orEmpty().trim()
                if (code.isBlank()) {
                    edtCode.error = "Bitte Kürzel eingeben"
                    return@setOnClickListener
                }
                if (name.isBlank()) {
                    edtName.error = "Bitte Name eingeben"
                    return@setOnClickListener
                }
                onCreated(BahnhofVorschlag(code = code, name = name, display = "$name ($code)"))
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun starteAktionMitAusgewaehltemKontext(
        kontext: AktiverTrassenKontext,
        fuerAutomatik: Boolean,
        nurKontextSetzen: Boolean = false
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.kontextSetzen(kontext)
            if (fuerAutomatik) {
                zeigeAutomatikAktionen(kontext)
            } else if (!nurKontextSetzen) {
                dialogStreckenpunkt(null, kontext)
            }
        }
    }

    private fun kontextAusPunkt(punkt: StreckenpunktEntity): AktiverTrassenKontext? {
        return when {
            !punkt.streckennummer.isNullOrBlank() -> AktiverTrassenKontext(
                art = TrassenKontextArt.STRECKE,
                streckennummer = punkt.streckennummer,
                labelKurz = punkt.streckennummer.orEmpty(),
                labelLang = "Freie Strecke ${punkt.streckennummer.orEmpty()}",
                objektKennung = punkt.objektKennung ?: punkt.streckennummer
            )
            !punkt.bahnhof.isNullOrBlank() && !punkt.weiche.isNullOrBlank() -> AktiverTrassenKontext(
                art = TrassenKontextArt.BAHNHOF_WEICHE,
                bahnhof = punkt.bahnhof,
                weiche = punkt.weiche,
                labelKurz = listOfNotNull(punkt.bahnhof, punkt.weiche).joinToString(" · "),
                labelLang = "Bahnhof ${punkt.bahnhof.orEmpty()} · Weiche ${punkt.weiche.orEmpty()}",
                objektKennung = punkt.weiche
            )
            !punkt.bahnhof.isNullOrBlank() && !punkt.gleis.isNullOrBlank() -> AktiverTrassenKontext(
                art = TrassenKontextArt.BAHNHOF_GLEIS,
                bahnhof = punkt.bahnhof,
                gleis = punkt.gleis,
                labelKurz = listOfNotNull(punkt.bahnhof, punkt.gleis).joinToString(" · "),
                labelLang = "Bahnhof ${punkt.bahnhof.orEmpty()} · Gleis ${punkt.gleis.orEmpty()}",
                objektKennung = punkt.gleis
            )
            else -> null
        }
    }

    private fun defaultBezeichnungFuerKontext(kontext: AktiverTrassenKontext): String {
        return when (kontext.art) {
            TrassenKontextArt.STRECKE -> kontext.streckennummer ?: "Streckenpunkt"
            TrassenKontextArt.BAHNHOF_GLEIS -> listOfNotNull(kontext.bahnhof, kontext.gleis).joinToString(" ")
            TrassenKontextArt.BAHNHOF_WEICHE -> listOfNotNull(kontext.bahnhof, kontext.weiche).joinToString(" ")
        }
    }

    private fun normalizeStrecke(input: String): String {
        val raw = input.trim().uppercase(Locale.GERMANY)
        if (raw.isBlank()) return ""
        if (raw.contains("-")) return raw
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotBlank()) "$digits-0" else raw
    }

    private fun bindeStreckenVorschlaege(input: AutoCompleteTextView) {
        fun refresh() {
            val raw = input.text?.toString().orEmpty().trim().uppercase(Locale.GERMANY)
            val basis = raw.substringBefore('-').filter { it.isDigit() }
            val vorschlaege = if (basis.isBlank()) emptyList() else listOf("$basis-0", "$basis-1", "$basis-2")
            input.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vorschlaege))
            if (vorschlaege.isNotEmpty()) input.showDropDown()
        }
        input.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) refresh() }
        input.addTextChangedListener { refresh() }
    }

    private fun bindeBahnhofVorschlaege(input: AutoCompleteTextView) {
        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        ) {
            private val items = mutableListOf<String>()

            override fun getCount(): Int = items.size
            override fun getItem(position: Int): String = items[position]

            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val resultItems = filterBahnhofVorschlaege(constraint?.toString().orEmpty()).map { it.display }
                        return FilterResults().apply {
                            values = resultItems
                            count = resultItems.size
                        }
                    }

                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        items.clear()
                        @Suppress("UNCHECKED_CAST")
                        items.addAll(results?.values as? List<String> ?: emptyList())
                        notifyDataSetChanged()
                    }

                    override fun convertResultToString(resultValue: Any?): CharSequence {
                        return resultValue?.toString().orEmpty()
                    }
                }
            }
        }
        input.setAdapter(adapter)
        input.threshold = 1
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !input.text.isNullOrBlank()) {
                input.post { input.showDropDown() }
            }
        }
    }

    private fun filterBahnhofVorschlaege(query: String): List<BahnhofVorschlag> {
        val norm = BahnhofSucheUtil.normalisiereSuchtext(query)
        if (norm.isBlank()) return emptyList()
        return bahnhofVorschlaege
            .asSequence()
            .filter { it.suchBlob.contains(norm) }
            .distinctBy { it.code.uppercase(Locale.GERMANY) }
            .take(25)
            .toList()
    }

    private fun findeBahnhofVorschlag(raw: String): BahnhofVorschlag? {
        val norm = BahnhofSucheUtil.normalisiereSuchtext(raw)
        if (norm.isBlank()) return null
        return bahnhofVorschlaege.firstOrNull {
            it.displayNorm == norm || it.codeNorm == norm || it.nameNorm == norm
        } ?: filterBahnhofVorschlaege(raw).firstOrNull()
    }

    private fun extrahiereBahnhofCode(raw: String, vorschlag: BahnhofVorschlag?): String {
        vorschlag?.let { return it.code }
        val text = raw.trim()
        val klammerStart = text.lastIndexOf('(')
        val klammerEnde = text.lastIndexOf(')')
        if (klammerStart >= 0 && klammerEnde > klammerStart) {
            val code = text.substring(klammerStart + 1, klammerEnde).trim().uppercase(Locale.GERMANY)
            if (code.isNotBlank()) return code
        }
        val codeTeil = text.substringBefore('–').substringBefore('-').trim().uppercase(Locale.GERMANY)
        return if (codeTeil.length in 2..8 && codeTeil.all { it.isLetterOrDigit() }) codeTeil else text.uppercase(Locale.GERMANY)
    }

    private fun extrahiereBahnhofName(raw: String, vorschlag: BahnhofVorschlag?): String? {
        vorschlag?.let { return it.name }
        val text = raw.trim()
        val klammerStart = text.lastIndexOf('(')
        if (klammerStart > 0) {
            return text.substring(0, klammerStart).trim().takeUnless { it.isBlank() }
        }
        return text.substringAfter('–', text).substringAfter('-', text).trim().takeUnless { it.equals(text, true) || it.isBlank() }
    }

    private fun ladeBahnhofVorschlaegeAusAssets(): List<BahnhofVorschlag> {
        val map = linkedMapOf<String, BahnhofVorschlag>()
        fun add(code: String?, name: String?) {
            val c = code?.replace(" ", "")?.trim().orEmpty().uppercase(Locale.GERMANY)
            val n = name?.trim().orEmpty()
            if (c.isBlank() || n.isBlank()) return
            map.putIfAbsent(c, BahnhofVorschlag(c, n, "$n ($c)"))
        }
        runCatching {
            requireContext().assets.open("TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json").bufferedReader().use { reader ->
                val root = JSONObject(reader.readText())
                val arr = root.optJSONArray("eintraege")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(obj.optString("code"), obj.optString("name"))
                    }
                }
            }
        }
        runCatching {
            requireContext().assets.open("TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json").bufferedReader().use { reader ->
                val root = JSONObject(reader.readText())
                val arr = root.optJSONArray("betriebsstellen")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(obj.optString("rl100Code"), obj.optString("rl100Langname"))
                    }
                }
            }
        }
        return map.values.toList()
    }
}
