package de.tbhelfer.ui.bautagebuch

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.widget.ArrayAdapter
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.location.Location
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import de.tbhelfer.R
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import de.tbhelfer.ServiceLocator
import de.tbhelfer.ui.einstellungen.nutzer.NutzerSpeicher
import de.tbhelfer.util.requireViewByIdCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.view.ViewCompat
import de.tbhelfer.vermessung.StandortLiveQuelle
import org.json.JSONObject
import androidx.appcompat.widget.ListPopupWindow
import kotlin.math.max
import de.tbhelfer.ui.suche.BtbSpeicher
import de.tbhelfer.ui.KeyboardAutoScroll
import kotlinx.coroutines.Job
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Space
import android.graphics.Typeface
/**
 * Bautagebuch (BTB) – leichter, dateibasierter Ansatz:
 * - Speichert pro Baustelle+Datum eine JSON-Datei
 * - Erstellt daraus ein PDF (inkl. Foto-Übersicht + BTB-PDF-Liste)
 */
class BautagebuchFragment : Fragment(R.layout.fragment_bautagebuch) {

    private companion object {
        private const val DEFAULT_BEWOELKUNG = "sonnig / wolkenlos / klar"
        private const val DEFAULT_NIEDERSCHLAG = "trocken"
        private const val WEATHER_AUTO_THROTTLE_MS = 60_000L
    }

    // Dropdown-Optionen (zentral, damit Reset/Manual die Adapter zuverlässig wieder binden kann)
    private val bewOptions = listOf(
        "sonnig / wolkenlos / klar",
        "heiter",
        "leicht bewölkt",
        "wolkig",
        "stark bewölkt",
        "bedeckt"
    )

    private val niedOptions = listOf(
        "trocken (kein Niederschlag)",
        "Nieselregen",
        "Regen (leicht)",
        "Regen (mäßig)",
        "Regen (stark)",
        "Schauer",
        "Dauerregen / Landregen",
        "Gewitter",
        "Graupel",
        "Hagel",
        "Schneefall (leicht)",
        "Schneefall (mäßig)",
        "Schneefall (stark)",
        "Schneeregen",
        "gefrierender Regen"
    )

    private val isoFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val deDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedSchichtTyp: SchichtTyp = SchichtTyp.TAG

    // Schichtzeiten (werden über Datum/Zeit Dropdown gesetzt)
    private var shiftStart: String? = null
    private var shiftEnd: String? = null

    // Wetter-Autofill: damit Klick auf Wetter nicht spammt
    private var lastWetterFetchMs: Long = 0L

    // Niederschlagsmengen (mm = l/m²) für die 3 Wetter-Slots (nur gesetzt bei Auto-Wetter)
    private var wetterMm1: Double? = null
    private var wetterMm2: Double? = null
    private var wetterMm3: Double? = null
    private var lastWetterLat: Double? = null
    private var lastWetterLon: Double? = null
    private var lastWetterOrt: String? = null
    private var lastWetterStation: String? = null
    private var lastWetterSource: WeatherSource = WeatherSource.MANUAL
    private var wetterFetchInProgress: Boolean = false

    private lateinit var btnDateTime: android.widget.TextView
    // (Main) "Vortag übernehmen" wird nicht mehr verwendet; Übernahme gibt es pro Sektion (Personal/Maschinen).
    private lateinit var btnSpeichern: MaterialButton
    private lateinit var btnPdf: MaterialButton
    private lateinit var btnPdfFinal: MaterialButton
    // Legacy: Start/Ende sind im Layout ausgeblendet (werden nicht mehr direkt benutzt)
    private lateinit var etStart: MaterialAutoCompleteTextView
    private lateinit var etEnde: MaterialAutoCompleteTextView
    private lateinit var btnWetterTime1: MaterialButton
    private lateinit var btnWetterTime2: MaterialButton
    private lateinit var btnWetterTime3: MaterialButton

    private lateinit var etTemp1: TextInputEditText
    private lateinit var etTemp2: TextInputEditText
    private lateinit var etTemp3: TextInputEditText

    private lateinit var acBew1: MaterialAutoCompleteTextView
    private lateinit var acBew2: MaterialAutoCompleteTextView
    private lateinit var acBew3: MaterialAutoCompleteTextView

    private lateinit var acNied1: MaterialAutoCompleteTextView
    private lateinit var acNied2: MaterialAutoCompleteTextView
    private lateinit var acNied3: MaterialAutoCompleteTextView
    private lateinit var tvWetterMm1: android.widget.TextView
    private lateinit var tvWetterMm2: android.widget.TextView
    private lateinit var tvWetterMm3: android.widget.TextView

    private lateinit var btnWetterAuto: MaterialButton
    private lateinit var btnWetterReset: MaterialButton

    private lateinit var weatherHeader: View
    private lateinit var tvWetterHeader: android.widget.TextView
    private lateinit var tvWetterPreview: android.widget.TextView
    private lateinit var wetterContainer: View

    private lateinit var etWetter: TextInputEditText
    private lateinit var etPersonal: TextInputEditText
    private lateinit var etGeraete: TextInputEditText

    // Phase 3b: Standardabschnitte (4–8)
    private lateinit var etArbeiten: TextInputEditText
    private lateinit var etAbnahmen: TextInputEditText
    private lateinit var etEinweisung: TextInputEditText
    private lateinit var etBesonderheiten: TextInputEditText
    private lateinit var etMaengel: TextInputEditText

    // Phase 3b: Rohtexte (vollständig) für Abschnitte 4–8. In der Hauptansicht werden nur die letzten 3 Einträge angezeigt.
    private var rawArbeiten: String = ""
    private var rawAbnahmen: String = ""
    private var rawEinweisung: String = ""
    private var rawBesonderheiten: String = ""
    private var rawMaengel: String = ""

    // BTB-EDIT-001: lokale Room-IDs sind nur UI-Hilfe. Synchronisiert wird ausschließlich
    // der stabile clientNodeKey/worksiteNodeKey.
    private val btbNodeKeyByLocalId = linkedMapOf<Long, String>()
    private val btbLocalIdByNodeKey = linkedMapOf<String, Long>()

    // Für Bild-Nummerierung (Bild 1–3) wie im PDF
    private var currentMaxFotosInPdf: Int = 60

    // Baseline (Long-Press "PDF erstellen")
    private var baselineExists: Boolean = false
    private var changedSinceBaseline: Boolean = false
    private var baselineLastModifiedEpochMs: Long? = null
    private var lastSavedEpochMs: Long? = null
    private var cachedStatusBaustelleId: Long? = null
    // Startzeitpunkt des aktuell geöffneten BTB-Entwurfs.
    // Wichtig für Foto-Zuordnung: Fotos ab diesem Zeitpunkt gehören in diesen BTB,
    // auch wenn der Nutzer vor dem ersten manuellen Speichern schon Fotos macht.
    private var currentBtbStartEpochMs: Long? = null

    // Foto-Auswahl Chips (4–8)
    private lateinit var btnFotosArbeiten: MaterialButton
    private lateinit var btnFotosAbnahmen: MaterialButton
    private lateinit var btnFotosEinweisung: MaterialButton
    private lateinit var btnFotosBesonderheiten: MaterialButton
    private lateinit var btnFotosMaengel: MaterialButton

    // Kompakte Status-Chips direkt neben Punkt 5 und Punkt 8.
    private lateinit var tvHeaderAbnahmen: android.widget.TextView
    private lateinit var btnNeueAbnahmeBtb: android.widget.TextView
    private lateinit var chipBtbAbnahmeJa: android.widget.TextView
    private lateinit var chipBtbAbnahmeNein: android.widget.TextView
    private lateinit var chipBtbAbnahmePruefung: android.widget.TextView
    private lateinit var tvHeaderMaengel: android.widget.TextView
    private lateinit var chipBtbMangelOffen: android.widget.TextView
    private lateinit var chipBtbMangelArbeit: android.widget.TextView
    private lateinit var chipBtbMangelErledigt: android.widget.TextView
    private lateinit var chipBtbMangelAbgelehnt: android.widget.TextView

    // Personal + Geräte/Maschinen (Dropdown-Style B)
    private lateinit var tvPersonalPreview: android.widget.TextView
    private lateinit var tvMaschinenPreview: android.widget.TextView
    private lateinit var pmHeaderRow: View
    private lateinit var topFieldsToggleRow: View
    private lateinit var tvTopFieldsToggle: android.widget.TextView
    private lateinit var personalSection: android.view.ViewGroup
    private lateinit var maschinenSection: android.view.ViewGroup
    private lateinit var personalBlocksContainer: android.widget.LinearLayout
    private lateinit var maschinenBlocksContainer: android.widget.LinearLayout
    private lateinit var btnAddPersonalFirma: MaterialButton
    private lateinit var btnAddMaschinenFirma: MaterialButton
    private lateinit var btnPersonalVortag: MaterialButton
    private lateinit var btnMaschinenVortag: MaterialButton
    private lateinit var btnPersonalSave: MaterialButton
    private lateinit var btnMaschinenSave: MaterialButton

    private enum class PmSection { PERSONAL, MASCHINEN }

    private enum class BtbEditSection(
        val point: Int,
        val label: String,
        val photoType: de.tbhelfer.datenbank.Bildtyp
    ) {
        ARBEIT(4, "Ausgeführte Arbeiten", de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION),
        ABNAHME(5, "Abnahmen", de.tbhelfer.datenbank.Bildtyp.ABNAHMEN),
        EINWEISUNG(6, "Einweisung/Unterweisung", de.tbhelfer.datenbank.Bildtyp.EINWEISUNG_UNTERWEISUNG),
        BESONDERHEIT(7, "Besonderheiten", de.tbhelfer.datenbank.Bildtyp.BESONDERHEITEN),
        MANGEL(8, "Mängel/Schäden", de.tbhelfer.datenbank.Bildtyp.MAENGEL_SCHAEDEN);

        companion object {
            fun fromPhotoType(type: de.tbhelfer.datenbank.Bildtyp?): BtbEditSection? =
                values().firstOrNull { it.photoType == type }
        }
    }

    private fun rawForEditSection(section: BtbEditSection): String = when (section) {
        BtbEditSection.ARBEIT -> rawArbeiten
        BtbEditSection.ABNAHME -> rawAbnahmen
        BtbEditSection.EINWEISUNG -> rawEinweisung
        BtbEditSection.BESONDERHEIT -> rawBesonderheiten
        BtbEditSection.MANGEL -> rawMaengel
    }

    private fun setRawForEditSection(section: BtbEditSection, value: String) {
        when (section) {
            BtbEditSection.ARBEIT -> rawArbeiten = value
            BtbEditSection.ABNAHME -> rawAbnahmen = value
            BtbEditSection.EINWEISUNG -> rawEinweisung = value
            BtbEditSection.BESONDERHEIT -> rawBesonderheiten = value
            BtbEditSection.MANGEL -> rawMaengel = value
        }
    }

    private data class PairRowUi(
        val root: View,
        val acAnzahl: MaterialAutoCompleteTextView,
        val acBez: MaterialAutoCompleteTextView,
        val btnRemove: android.widget.ImageButton
    )

    private data class FirmaBlockUi(
        val root: View,
        /** Stabiler Key, damit Personal- und Maschinenblock zur gleichen Firma gehören. */
        val firmaId: String,
        /** True wenn dieser Block automatisch aus Personal nach Maschinen gespiegelt wurde (Placeholder). */
        var autoLinked: Boolean = false,
        val acFirma: MaterialAutoCompleteTextView,
        val etVon: com.google.android.material.textfield.TextInputEditText,
        val etBis: com.google.android.material.textfield.TextInputEditText,
        val rowsContainer: android.widget.LinearLayout,
        val rows: MutableList<PairRowUi>,
        val btnAddRow: MaterialButton,
        val btnRemoveFirma: android.widget.ImageButton,
        var zeitbindung: String? = null
    )

    // Guard, damit wir beim Synchronisieren (Firma-Name zwischen Sektionen) keine Endlosschleifen erzeugen.
    private var pmSyncing = false

    // Guard, damit wir beim Laden/Apply keine Autosaves auslösen.
    private var pmApplying = false
    private var pmFirmenQuellstand: List<FirmaBlock> = emptyList()

    // Debounced Autosave für PM-Änderungen
    private var pmAutosaveJob: kotlinx.coroutines.Job? = null

    // Wetter: Debounced Autosave + Guard (damit SetText/Apply nicht sofort wieder Autosave triggert)
    private var wetterAutosaveJob: kotlinx.coroutines.Job? = null
    private var wetterApplying: Boolean = false

    // Phase 3b: Autosave für Standardabschnitte
    private var sectionsAutosaveJob: kotlinx.coroutines.Job? = null
    private var sectionsApplying: Boolean = false

    // Verhindert Autosave während Datum/Schicht-Wechsel (sonst werden Inhalte in falsche Datei gespeichert)
    private var isSwitchingContext: Boolean = false


    private val personalUis = mutableListOf<FirmaBlockUi>()
    private val maschinenUis = mutableListOf<FirmaBlockUi>()

    // Autocomplete Historie
    private val firmHistory = linkedSetOf<String>()
    private val roleHistory = linkedSetOf<String>()
    private val machineHistory = linkedSetOf<String>()

    private lateinit var tvStatus: View
    private lateinit var tvTitleBaustelle: android.widget.TextView
    private lateinit var scrollRoot: android.view.View


private lateinit var tvCurrentReportLine1: android.widget.TextView
private lateinit var tvCurrentReportLine2: android.widget.TextView
private lateinit var btnQuickCamera: View
private lateinit var btnQuickNewBtb: MaterialCardView
private lateinit var cardCurrentReport: MaterialCardView
private lateinit var cardNewBtbStartMode: MaterialCardView
private lateinit var tvNewBtbHint: android.widget.TextView
private lateinit var tvNewBtbDateTimeProposal: android.widget.TextView
private lateinit var tvNewBtbPersonalProposal: android.widget.TextView
private lateinit var tvNewBtbMaschinenProposal: android.widget.TextView
private lateinit var tvNewBtbWeatherProposal: android.widget.TextView
private lateinit var btnNewBtbUseDateTime: MaterialButton
private lateinit var btnNewBtbUsePersonal: MaterialButton
private lateinit var btnNewBtbUseMaschinen: MaterialButton
private lateinit var btnNewBtbUseWeather: MaterialButton
private lateinit var btnNewBtbCancel: MaterialButton
private lateinit var btnNewBtbStart: MaterialButton
private lateinit var tvNewBtbDateTimeLabel: android.widget.TextView
private lateinit var tvNewBtbPersonalLabel: android.widget.TextView
private lateinit var tvNewBtbMaschinenLabel: android.widget.TextView
private lateinit var tvNewBtbWeatherLabel: android.widget.TextView

private data class NewBtbProposal(
    val sourceLabel: String,
    val startUhrzeit: String?,
    val endeUhrzeit: String?,
    val firmen: List<FirmaBlock>,
    val weatherTimes: List<String>
)

private var newBtbProposal: NewBtbProposal? = null
private var newBtbUseDateTime: Boolean = false
private var newBtbUsePersonal: Boolean = false
private var newBtbUseMaschinen: Boolean = false
private var newBtbUseWeather: Boolean = false
private var newBtbPreparedDate: LocalDate? = null
private var newBtbPreparedStart: String? = null
private var newBtbPreparedEnd: String? = null
private var newBtbPreparedSchichtTyp: SchichtTyp? = null
private var newBtbPreparedFirmen: List<FirmaBlock>? = null
private var newBtbPreparedWeatherSlots: List<WetterSlot>? = null
private var newBtbPreparedWeatherMessage: String? = null

private var reportBannerJob: Job? = null
private var statusFadeJob: Job? = null
private var lastKnownRevisionCount: Int? = null
private var topFieldsCollapsed: Boolean = true
private var widgetSectionRequest: Int? = null
private var widgetOpenEditorRequest: Boolean = false
private var requestedBtbId: String? = null
private var loadedBtbId: String? = null
private var loadedBtbChangedEpochMs: Long = Long.MIN_VALUE
private var btbLoadInitialized: Boolean = false

// Für Wechsel-Dialog: wenn der Nutzer gerade noch tippt, kurz nachfragen
private var lastUserEditAtMs: Long = 0L
private val autosaveIdleDelayMs: Long = 1_500L
private val autosaveMaxDelayMs: Long = 5_000L
private var sectionsDirtySinceMs: Long? = null
private var wetterDirtySinceMs: Long? = null
private var pmDirtySinceMs: Long? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // V11: BTB-Verlauf kann ein bestimmtes Datum / eine Schicht öffnen.
        arguments?.getString("datumIso")?.takeIf { it.isNotBlank() }?.let { iso ->
            runCatching { selectedDate = LocalDate.parse(iso, isoFmt) }
        }
        arguments?.getString("schichtTyp")?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { selectedSchichtTyp = SchichtTyp.valueOf(raw) }
        }
        requestedBtbId = arguments?.getString("btbId")?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("legacy:") }
        widgetSectionRequest = arguments?.getInt("widgetSection", -1)?.takeIf { it in 4..8 }
        widgetOpenEditorRequest = arguments?.getBoolean("widgetOpenEditor", false) == true

        // Insets: damit der Titel nicht in die Kamera/Statusbar läuft
        scrollRoot = view.requireViewByIdCompat(R.id.scrollRoot)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scrollRoot) { v, insets ->
            val sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val extraTop = (8f * resources.displayMetrics.density).toInt()
            // Basis-Padding aus XML behalten und nur Top um Systembar erweitern
            v.setPadding(v.paddingLeft, sys.top + extraTop, v.paddingRight, v.paddingBottom)
            insets
        }
        tvTitleBaustelle = view.requireViewByIdCompat(R.id.tvTitleBaustelle)
        btnDateTime = view.requireViewByIdCompat(R.id.btnDateTime)
        // btnVortag (Main) entfernt – Übernahme erfolgt in Personal/Maschinen-Sektion.
        btnSpeichern = view.requireViewByIdCompat(R.id.btnSpeichern)
        btnPdf = view.requireViewByIdCompat(R.id.btnPdf)
        btnPdfFinal = view.requireViewByIdCompat(R.id.btnPdfFinal)
        etStart = view.requireViewByIdCompat(R.id.etStart)
        etEnde = view.requireViewByIdCompat(R.id.etEnde)

        btnWetterTime1 = view.requireViewByIdCompat(R.id.btnWetterTime1)
        btnWetterTime2 = view.requireViewByIdCompat(R.id.btnWetterTime2)
        btnWetterTime3 = view.requireViewByIdCompat(R.id.btnWetterTime3)

        etTemp1 = view.requireViewByIdCompat(R.id.etTemp1)
        etTemp2 = view.requireViewByIdCompat(R.id.etTemp2)
        etTemp3 = view.requireViewByIdCompat(R.id.etTemp3)

        acBew1 = view.requireViewByIdCompat(R.id.acBew1)
        acBew2 = view.requireViewByIdCompat(R.id.acBew2)
        acBew3 = view.requireViewByIdCompat(R.id.acBew3)

        acNied1 = view.requireViewByIdCompat(R.id.etNied1)
        acNied2 = view.requireViewByIdCompat(R.id.etNied2)
        acNied3 = view.requireViewByIdCompat(R.id.etNied3)
        tvWetterMm1 = view.requireViewByIdCompat(R.id.tvWetterMm1)
        tvWetterMm2 = view.requireViewByIdCompat(R.id.tvWetterMm2)
        tvWetterMm3 = view.requireViewByIdCompat(R.id.tvWetterMm3)

        btnWetterAuto = view.requireViewByIdCompat(R.id.btnWetterAuto)
        btnWetterReset = view.requireViewByIdCompat(R.id.btnWetterReset)


        weatherHeader = view.requireViewByIdCompat(R.id.weatherHeader)
        tvWetterHeader = view.requireViewByIdCompat(R.id.tvWetterHeader)
        tvWetterPreview = view.requireViewByIdCompat(R.id.tvWetterPreview)
        wetterContainer = view.requireViewByIdCompat(R.id.wetterContainer)
        pmHeaderRow = view.requireViewByIdCompat(R.id.pmHeaderRow)
        topFieldsToggleRow = view.requireViewByIdCompat(R.id.topFieldsToggleRow)
        tvTopFieldsToggle = view.requireViewByIdCompat(R.id.tvTopFieldsToggle)

        // UX: Nur die Wetter-Pill ist klickbar (Hitbox-Bug vermeiden)
        // Short click: Toggle auf/zu. Beim Öffnen wird aktualisiert.
        // Long click: Force-Refresh (ohne Toggle-Zwang).
        tvWetterPreview.setOnClickListener {
            val opening = toggleWeather()
            if (opening) {
                wetterAutomatischFuellen(force = true)
            }
        }
        tvWetterPreview.setOnLongClickListener {
            if (wetterContainer.visibility != View.VISIBLE) {
                toggleWeather()
            }
            wetterAutomatischFuellen(force = true)
            true
        }

        // Legacy (unsichtbar)
        etWetter = view.requireViewByIdCompat(R.id.etWetter)
        etPersonal = view.requireViewByIdCompat(R.id.etPersonal)
        etGeraete = view.requireViewByIdCompat(R.id.etGeraete)

        // Phase 3b: Standardabschnitte (4–8)
        etArbeiten = view.requireViewByIdCompat(R.id.etArbeiten)
        etAbnahmen = view.requireViewByIdCompat(R.id.etAbnahmen)
        etEinweisung = view.requireViewByIdCompat(R.id.etEinweisung)
        etBesonderheiten = view.requireViewByIdCompat(R.id.etBesonderheiten)
        etMaengel = view.requireViewByIdCompat(R.id.etMaengel)
        tvStatus = view.requireViewByIdCompat(R.id.tvStatus)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                de.tbhelfer.server.SelectiveSyncStore.revisions.collect {
                    if (lastSavedEpochMs != null && sectionsDirtySinceMs == null && pmDirtySinceMs == null && wetterDirtySinceMs == null) setStatus("Gespeichert")
                }
            }
        }


tvCurrentReportLine1 = view.requireViewByIdCompat(R.id.tvCurrentReportLine1)
tvCurrentReportLine2 = view.requireViewByIdCompat(R.id.tvCurrentReportLine2)
btnQuickCamera = view.requireViewByIdCompat(R.id.btnQuickCamera)
btnQuickNewBtb = view.requireViewByIdCompat(R.id.btnQuickNewBtb)
cardCurrentReport = view.requireViewByIdCompat(R.id.cardCurrentReport)
cardNewBtbStartMode = view.requireViewByIdCompat(R.id.cardNewBtbStartMode)
tvNewBtbHint = view.requireViewByIdCompat(R.id.tvNewBtbHint)
tvNewBtbDateTimeProposal = view.requireViewByIdCompat(R.id.tvNewBtbDateTimeProposal)
tvNewBtbPersonalProposal = view.requireViewByIdCompat(R.id.tvNewBtbPersonalProposal)
tvNewBtbMaschinenProposal = view.requireViewByIdCompat(R.id.tvNewBtbMaschinenProposal)
tvNewBtbWeatherProposal = view.requireViewByIdCompat(R.id.tvNewBtbWeatherProposal)
btnNewBtbUseDateTime = view.requireViewByIdCompat(R.id.btnNewBtbUseDateTime)
btnNewBtbUsePersonal = view.requireViewByIdCompat(R.id.btnNewBtbUsePersonal)
btnNewBtbUseMaschinen = view.requireViewByIdCompat(R.id.btnNewBtbUseMaschinen)
btnNewBtbUseWeather = view.requireViewByIdCompat(R.id.btnNewBtbUseWeather)
btnNewBtbCancel = view.requireViewByIdCompat(R.id.btnNewBtbCancel)
btnNewBtbStart = view.requireViewByIdCompat(R.id.btnNewBtbStart)
tvNewBtbDateTimeLabel = view.requireViewByIdCompat(R.id.tvNewBtbDateTimeLabel)
tvNewBtbPersonalLabel = view.requireViewByIdCompat(R.id.tvNewBtbPersonalLabel)
tvNewBtbMaschinenLabel = view.requireViewByIdCompat(R.id.tvNewBtbMaschinenLabel)
tvNewBtbWeatherLabel = view.requireViewByIdCompat(R.id.tvNewBtbWeatherLabel)

btnQuickCamera.setOnClickListener {
    findNavController().navigate(R.id.kameraFragment)
}
btnQuickNewBtb.setOnClickListener {
    openNewBtbStartMode()
}
btnNewBtbUseDateTime.setOnClickListener {
    newBtbUseDateTime = !newBtbUseDateTime
    renderNewBtbStartMode()
}
btnNewBtbUsePersonal.setOnClickListener {
    showNewBtbSectionEditorDialog(PmSection.PERSONAL)
}
btnNewBtbUseMaschinen.setOnClickListener {
    showNewBtbSectionEditorDialog(PmSection.MASCHINEN)
}
btnNewBtbUseWeather.setOnClickListener {
    newBtbUseWeather = !newBtbUseWeather
    renderNewBtbStartMode()
}
fun bindNewBtbTap(v: View, action: () -> Unit) {
    v.setOnClickListener { action() }
}
bindNewBtbTap(tvNewBtbDateTimeLabel) { showNewBtbDateTimeDialog() }
bindNewBtbTap(tvNewBtbDateTimeProposal) { showNewBtbDateTimeDialog() }
bindNewBtbTap(tvNewBtbPersonalLabel) { showNewBtbSectionEditorDialog(PmSection.PERSONAL) }
bindNewBtbTap(tvNewBtbPersonalProposal) { showNewBtbSectionEditorDialog(PmSection.PERSONAL) }
bindNewBtbTap(tvNewBtbMaschinenLabel) { showNewBtbSectionEditorDialog(PmSection.MASCHINEN) }
bindNewBtbTap(tvNewBtbMaschinenProposal) { showNewBtbSectionEditorDialog(PmSection.MASCHINEN) }
bindNewBtbTap(tvNewBtbWeatherLabel) { showNewBtbWeatherPreviewDialog() }
bindNewBtbTap(tvNewBtbWeatherProposal) { showNewBtbWeatherPreviewDialog() }
btnNewBtbCancel.setOnClickListener {
    closeNewBtbStartMode()
}
btnNewBtbStart.setOnClickListener {
    startPreparedNewBtb()
}

topFieldsToggleRow.setOnClickListener {
    setTopFieldsCollapsed(!topFieldsCollapsed)
}
tvTopFieldsToggle.setOnClickListener {
    setTopFieldsCollapsed(!topFieldsCollapsed)
}

// Blaues Feld: schneller Bericht-Wechsel (Tag/Nacht) ohne Dropdown
cardCurrentReport.setOnClickListener {
    confirmContextSwitchIfRecent {
        showBerichtOeffnenLetzte7TageDialog()
    }
}

        setupSectionPreviewUi()

        // Foto-Auswahl Chips (4–8)
        btnFotosArbeiten = view.requireViewByIdCompat(R.id.btnFotosArbeiten)
        btnFotosAbnahmen = view.requireViewByIdCompat(R.id.btnFotosAbnahmen)
        btnFotosEinweisung = view.requireViewByIdCompat(R.id.btnFotosEinweisung)
        btnFotosBesonderheiten = view.requireViewByIdCompat(R.id.btnFotosBesonderheiten)
        btnFotosMaengel = view.requireViewByIdCompat(R.id.btnFotosMaengel)
        tvHeaderAbnahmen = view.requireViewByIdCompat(R.id.tvHeaderAbnahmen)
        btnNeueAbnahmeBtb = view.requireViewByIdCompat(R.id.btnNeueAbnahmeBtb)
        chipBtbAbnahmeJa = view.requireViewByIdCompat(R.id.chipBtbAbnahmeJa)
        chipBtbAbnahmeNein = view.requireViewByIdCompat(R.id.chipBtbAbnahmeNein)
        chipBtbAbnahmePruefung = view.requireViewByIdCompat(R.id.chipBtbAbnahmePruefung)
        tvHeaderMaengel = view.requireViewByIdCompat(R.id.tvHeaderMaengel)
        chipBtbMangelOffen = view.requireViewByIdCompat(R.id.chipBtbMangelOffen)
        chipBtbMangelArbeit = view.requireViewByIdCompat(R.id.chipBtbMangelArbeit)
        chipBtbMangelErledigt = view.requireViewByIdCompat(R.id.chipBtbMangelErledigt)
        chipBtbMangelAbgelehnt = view.requireViewByIdCompat(R.id.chipBtbMangelAbgelehnt)


        // Personal + Geräte/Maschinen Dropdowns
        setupPmUi(view)
        setTopFieldsCollapsed(true)
        // "Zurück"-Button wurde entfernt – Navigation läuft über System-Back bzw. Toolbar
        btnDateTime.setOnClickListener { showDateTimeDropdown() }
        // (Main) "Vortag übernehmen" entfernt.
        // Speichern läuft automatisch; unten bleiben nur die beiden PDF-Aktionen.
        btnSpeichern.setOnClickListener {
            val msg = buildFinishValidationMessage()
            if (msg != null) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.btb_validate_title))
                    .setMessage(msg)
                    .setPositiveButton(getString(R.string.btb_validate_ok), null)
                    .show()
                return@setOnClickListener
            }
            speichern(closeAfter = false)

        }
        btnPdf.setOnClickListener { createDraftPdfDirect() }
        btnPdfFinal.setOnClickListener { showPdfFinalConfirmDialog() }
        btnPdfFinal.setOnLongClickListener {
            showPdfExportConfirmDialog()
            true
        }
        fun openFotoAuswahl(bildtyp: de.tbhelfer.datenbank.Bildtyp) {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                // Auswahl (pdfAuswahl) nicht überschreiben: bestehenden Eintrag laden und übernehmen
                val existing = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }

                val draft = buildEntry(ctx)
                val entry = draft.copy(
                    maxFotosInPdf = existing?.maxFotosInPdf ?: draft.maxFotosInPdf,
                    pdfAuswahl = existing?.pdfAuswahl ?: draft.pdfAuswahl,
                    erstelltAmEpochMs = existing?.erstelltAmEpochMs ?: draft.erstelltAmEpochMs,
                    geaendertAmEpochMs = System.currentTimeMillis()
                )

                // Speichern, damit Auswahl-Fragment konsistent ist
                withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.speichern(requireContext(), entry)
                }
                val b = Bundle().apply {
                    putLong("baustelleId", entry.baustelleId)
                    putString("baustelleName", entry.baustelleName)
                    putString("datumIso", entry.datumIso)
                    putString("shiftStart", entry.startUhrzeit)
                    putString("shiftEnd", entry.endeUhrzeit)
                    putString("schichtTyp", selectedSchichtTyp.name)
                    putString("filterBildtyp", bildtyp.name)
                }
                findNavController().navigate(R.id.btbPdfAuswahlFragment, b)
            }
        }

        btnFotosArbeiten.setOnClickListener { openFotoAuswahl(de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION) }
        btnFotosAbnahmen.setOnClickListener { openFotoAuswahl(de.tbhelfer.datenbank.Bildtyp.ABNAHMEN) }
        btnFotosEinweisung.setOnClickListener { openFotoAuswahl(de.tbhelfer.datenbank.Bildtyp.EINWEISUNG_UNTERWEISUNG) }
        btnFotosBesonderheiten.setOnClickListener { openFotoAuswahl(de.tbhelfer.datenbank.Bildtyp.BESONDERHEITEN) }
        btnFotosMaengel.setOnClickListener { openFotoAuswahl(de.tbhelfer.datenbank.Bildtyp.MAENGEL_SCHAEDEN) }

        fun openAbnahmen(filter: String) {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle() ?: return@launch
                val rootId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first() ?: -1L
                findNavController().navigate(R.id.abnahmelisteFragment, Bundle().apply {
                    putLong("baustelleId", ctx.id)
                    putString("baustelleName", ctx.name)
                    putLong("hauptKnotenId", rootId)
                    putString("filter", filter)
                })
            }
        }
        fun openNeueAbnahme() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle() ?: return@launch
                val rootId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first() ?: -1L
                findNavController().navigate(R.id.abnahmeEditorFragment, Bundle().apply {
                    putLong("baustelleId", ctx.id)
                    putString("baustelleName", ctx.name)
                    putLong("hauptKnotenId", rootId)
                    putString("abnahmeId", "")
                })
            }
        }
        fun openMaengel() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle() ?: return@launch
                val rootId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first() ?: -1L
                findNavController().navigate(R.id.maengellisteFragment, Bundle().apply {
                    putLong("hauptKnotenId", rootId)
                    putLong("altBaustelleId", ctx.id)
                    putString("baustelleName", ctx.name)
                })
            }
        }
        tvHeaderAbnahmen.setOnClickListener { openAbnahmen("ALLE") }
        btnNeueAbnahmeBtb.setOnClickListener { openNeueAbnahme() }
        chipBtbAbnahmeJa.setOnClickListener { openAbnahmen("JA") }
        chipBtbAbnahmeNein.setOnClickListener { openAbnahmen("NEIN") }
        chipBtbAbnahmePruefung.setOnClickListener { openAbnahmen("PRUEFUNG") }
        tvHeaderMaengel.setOnClickListener { openMaengel() }
        chipBtbMangelOffen.setOnClickListener { openMaengel() }
        chipBtbMangelArbeit.setOnClickListener { openMaengel() }
        chipBtbMangelErledigt.setOnClickListener { openMaengel() }
        chipBtbMangelAbgelehnt.setOnClickListener { openMaengel() }
        refreshBtbStatusChips()

        updateDateTimeButton()
        // Start/Ende Eingaben sind ausgeblendet; Adapter bleibt, damit Dialog-Dropdowns dieselben Werte nutzen können
        setupStartEndDropdowns()
        setupWetterDropdowns()
        updateWeatherPreview()


        // Beim Öffnen: heutigen Entwurf automatisch wieder öffnen (Tag/Nacht), damit nach dem Speichern nichts "leer" wirkt.
        //  - lädt bevorzugt den zuletzt geänderten Entwurf von HEUTE (Tag oder Nacht)
        //  - wenn nichts existiert: normal leer starten
        lifecycleScope.launch {
            ladeEintragFallsVorhanden(autoPickSchichtForToday = true)
            handleWidgetSectionRequest()
        }
    }

    private fun handleWidgetSectionRequest() {
        val section = widgetSectionRequest ?: return
        widgetSectionRequest = null
        val target: View = when (section) {
            4 -> etArbeiten
            5 -> etAbnahmen
            6 -> etEinweisung
            7 -> etBesonderheiten
            8 -> etMaengel
            else -> return
        }
        target.post {
            val scroll = scrollRoot as? android.widget.ScrollView
            scroll?.smoothScrollTo(0, (target.top - dpToPx(72)).coerceAtLeast(0))
            if (widgetOpenEditorRequest) {
                widgetOpenEditorRequest = false
                target.performClick()
            }
        }
    }



    override fun onResume() {
        super.onResume()
        if (::chipBtbAbnahmeJa.isInitialized) refreshBtbStatusChips()
        refreshActiveBtbAfterResume()
    }

    /**
     * Nach PDF/Foto/Mangel/Sync-Rueckkehr kann derselbe Fragment-View weiterleben.
     * Dann waren bisher Kopf/Pointer bereits aktuell, die Eingabefelder aber noch der alte Stand.
     * Nur ohne ungespeicherte lokale Eingabe wird der kanonische aktive BTB neu eingelesen.
     */
    private fun refreshActiveBtbAfterResume() {
        if (!btbLoadInitialized || isSwitchingContext || !isAdded) return
        if (sectionsDirtySinceMs != null || pmDirtySinceMs != null || wetterDirtySinceMs != null) return

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = resolveAktuelleBaustelle() ?: return@launch
            val active = withContext(Dispatchers.IO) {
                BtbActiveContext.laden(requireContext().applicationContext, ctx.id)
            }
            val mussNeuLaden = when {
                active == null -> loadedBtbId != null
                active.btbId != loadedBtbId -> true
                active.entry.geaendertAmEpochMs > loadedBtbChangedEpochMs -> true
                else -> false
            }
            if (!mussNeuLaden) return@launch
            if (isSwitchingContext || sectionsDirtySinceMs != null || pmDirtySinceMs != null || wetterDirtySinceMs != null) {
                return@launch
            }

            beginContextSwitch()
            try {
                ladeEintragFallsVorhanden(autoPickSchichtForToday = true)
            } finally {
                endContextSwitch()
            }
        }
    }

    private fun refreshBtbStatusChips() {
        if (!isAdded || !::chipBtbAbnahmeJa.isInitialized) return
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = resolveAktuelleBaustelle()
            if (ctx == null) {
                chipBtbAbnahmeJa.text = "Ja 0"
                chipBtbAbnahmeNein.text = "Nein 0"
                chipBtbAbnahmePruefung.text = "Prüf. 0"
                chipBtbMangelOffen.text = "Offen 0"
                chipBtbMangelArbeit.text = "Arbeit 0"
                chipBtbMangelErledigt.text = "Erled. 0"
                chipBtbMangelAbgelehnt.text = "Abgel. 0"
                return@launch
            }
            val a = withContext(Dispatchers.IO) { AbnahmeSpeicher.zaehler(requireContext().applicationContext, ctx.id) }
            val m = withContext(Dispatchers.IO) {
                de.tbhelfer.ui.baustelle.MangelFachlogik.zaehlerFuerBaustelle(requireContext().applicationContext, ctx.id, ctx.name)
            }
            chipBtbAbnahmeJa.text = "Ja ${a.ja}"
            chipBtbAbnahmeNein.text = "Nein ${a.nein}"
            chipBtbAbnahmePruefung.text = "Prüf. ${a.inPruefung}"
            chipBtbMangelOffen.text = "Offen ${m.offen}"
            chipBtbMangelArbeit.text = "Arbeit ${m.inArbeit}"
            chipBtbMangelErledigt.text = "Erled. ${m.erledigt}"
            chipBtbMangelAbgelehnt.text = "Abgel. ${m.abgelehnt}"
        }
    }

    private fun updateDateTimeButton() {
        val hasShift = !shiftStart.isNullOrBlank() && !shiftEnd.isNullOrBlank()

        val label = when {
            !hasShift -> "Keine Daten"
            !baselineExists -> "ENTWURF"
            changedSinceBaseline -> "GEÄNDERT"
            else -> "ABGESCHLOSSEN"
        }

        val bg = when {
            !hasShift -> "#FFCDD2"      // rot (Pflicht fehlt)
            !baselineExists -> "#E0E0E0" // grau (Entwurf)
            changedSinceBaseline -> "#FFCDD2" // rot (nach Abschluss geändert)
            else -> "#DFF3E0"           // grün (Abgeschlossen) – etwas dezenter
        }

        btnDateTime.text = label
        btnDateTime.setTextColor(Color.BLACK)
        ViewCompat.setBackgroundTintList(btnDateTime, ColorStateList.valueOf(Color.parseColor(bg)))

        // Blauer Balken (B2) aktualisieren
        scheduleUpdateCurrentReportBanner()
    }

        private fun formatHm(epochMs: Long?): String? {
            if (epochMs == null || epochMs <= 0L) return null
            return runCatching {
                java.time.Instant.ofEpochMilli(epochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(timeFmt)
            }.getOrNull()
        }

    
    private fun dateTimeStatusText(): String {
        val hasShift = !shiftStart.isNullOrBlank() && !shiftEnd.isNullOrBlank()
        if (!hasShift) return "Keine Daten"
        return when {
            !baselineExists -> "Entwurf"
            changedSinceBaseline -> {
                val rev = (lastKnownRevisionCount ?: 1).coerceAtLeast(1)
                "Geändert ($rev)"
            }
            else -> "Abgeschlossen"
        }
    }


    private fun scheduleUpdateCurrentReportBanner() {
        reportBannerJob?.cancel()
        reportBannerJob = lifecycleScope.launch {
            val ctx = resolveAktuelleBaustelle()
            if (ctx == null) {
                tvCurrentReportLine1.text = ""
                tvCurrentReportLine2.text = ""
                return@launch
            }

            // Zeile 1: Tag/Nacht + Zeiten + Datum (dd.MM.yy)
            val typ = if (selectedSchichtTyp == SchichtTyp.TAG) getString(R.string.btb_shift_tag) else getString(R.string.btb_shift_nacht)
            val times = if (!shiftStart.isNullOrBlank() && !shiftEnd.isNullOrBlank()) "${shiftStart}–${shiftEnd}" else ""
            val dateTxt = selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
            tvCurrentReportLine1.text = listOfNotNull(typ, times.takeIf { it.isNotBlank() }, dateTxt).joinToString(" · ")

            val currentSnapshot = runCatching { buildEntry(ctx) }.getOrNull()
            val line2 = withContext(Dispatchers.IO) { buildCurrentReportLine2(ctx, currentSnapshot) }
            tvCurrentReportLine2.text = line2
        }
    }

    private fun buildCurrentReportLine2(ctx: BaustelleKontext, currentSnapshot: BautagebuchEintrag?): String {
        return when {
            !baselineExists -> {
                lastKnownRevisionCount = null
                "Entwurf"
            }
            !changedSinceBaseline -> {
                lastKnownRevisionCount = null
                "Abgeschlossen"
            }
            else -> {
                val baselineEntry = BautagebuchSpeicher.ladenBaseline(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                val rev = computePendingChangeCount(currentSnapshot, baselineEntry).coerceAtLeast(1)
                lastKnownRevisionCount = rev
                "Geändert ($rev)"
            }
        }
    }

    private fun computePendingChangeCount(current: BautagebuchEintrag?, baseline: BautagebuchEintrag?): Int {
        if (current == null || baseline == null) return 1

        fun wsNorm(s: String): String = s.trim().replace(Regex("""\s+"""), " ")
        fun leadingTimePrefix(line: String): String? = Regex("""^(\d{2}:\d{2}(?:–\d{2}:\d{2})?)\b""").find(line.trim())?.groupValues?.getOrNull(1)

        fun pairAndCount(cur: Set<String>, base: Set<String>): Int {
            val added = (cur - base).toMutableSet()
            val removed = (base - cur).toMutableSet()
            if (added.isEmpty() && removed.isEmpty()) return 0

            fun bucket(key: String): String {
                val parts = key.split("||", limit = 3)
                if (parts.size >= 3) {
                    val prefix = leadingTimePrefix(parts[2]).orEmpty()
                    return parts[0] + "||" + parts[1] + "||" + prefix
                }
                if (key.startsWith("B||")) {
                    val p = key.split("||", limit = 3)
                    val hdr = p.getOrNull(1).orEmpty()
                    val line = p.getOrNull(2).orEmpty()
                    val prefix = leadingTimePrefix(line).orEmpty()
                    return "B||" + hdr + "||" + prefix
                }
                return key
            }

            val removedByBucket = linkedMapOf<String, MutableList<String>>()
            removed.forEach { removedByBucket.getOrPut(bucket(it)) { mutableListOf() }.add(it) }

            var edited = 0
            val matchedRemoved = mutableSetOf<String>()
            added.toList().forEach { addKey ->
                val candidates = removedByBucket[bucket(addKey)] ?: return@forEach
                val match = candidates.firstOrNull { it !in matchedRemoved }
                if (match != null) {
                    matchedRemoved += match
                    edited += 1
                }
            }
            val realRemoved = removed.count { it !in matchedRemoved }
            val realAdded = added.size - edited
            return edited + realAdded + realRemoved
        }

        fun buildWeather(e: BautagebuchEintrag): Set<String> = e.weatherSlots.map { s ->
            val time = wsNorm(s.uhrzeit)
            val temp = s.tempC?.let { "$it°C" } ?: "-"
            val bew = wsNorm(s.bewoelkung ?: "-")
            val nied = wsNorm(s.niederschlag ?: "-")
            "$time|$temp|$bew|$nied"
        }.toSet()

        fun buildPersonal(e: BautagebuchEintrag): Set<String> {
            fun fmt(von: String?, bis: String?, name: String): String {
                val v = von?.trim().orEmpty(); val b = bis?.trim().orEmpty()
                return when {
                    v.isNotBlank() && b.isNotBlank() -> "$v - $b $name"
                    v.isNotBlank() -> "$v - $name"
                    b.isNotBlank() -> "- $b $name"
                    else -> name
                }
            }
            val out = linkedSetOf<String>()
            if (e.firmen.isNotEmpty()) {
                e.firmen.filter { it.visibleInPersonal && !it.isDeleted }.forEach { f ->
                    val hdr = wsNorm(fmt(f.personalVon, f.personalBis, f.name))
                    val lns = f.personal.map { wsNorm("${it.anzahl ?: ""} ${it.rolle}".trim()) }.filter { it.isNotBlank() }
                    if (lns.isEmpty()) return@forEach
                    out += "H||$hdr"
                    lns.forEach { out += "B||$hdr||$it" }
                }
            } else {
                e.personal.map(::wsNorm).filter { it.isNotBlank() }.forEach { out += "B||||$it" }
            }
            return out
        }

        fun buildMaschinen(e: BautagebuchEintrag): Set<String> {
            fun fmt(von: String?, bis: String?, name: String): String {
                val v = von?.trim().orEmpty(); val b = bis?.trim().orEmpty()
                return when {
                    v.isNotBlank() && b.isNotBlank() -> "$v - $b $name"
                    v.isNotBlank() -> "$v - $name"
                    b.isNotBlank() -> "- $b $name"
                    else -> name
                }
            }
            val out = linkedSetOf<String>()
            if (e.firmen.isNotEmpty()) {
                e.firmen.filter { it.visibleInMaschinen && !it.isDeleted }.forEach { f ->
                    val hdr = wsNorm(fmt(f.maschinenVon, f.maschinenBis, f.name))
                    val lns = f.geraete.map { wsNorm("${it.anzahl ?: ""} ${it.bezeichnung}".trim()) }.filter { it.isNotBlank() }
                    if (lns.isEmpty()) return@forEach
                    out += "H||$hdr"
                    lns.forEach { out += "B||$hdr||$it" }
                }
            } else {
                e.geraete.map(::wsNorm).filter { it.isNotBlank() }.forEach { out += "B||||$it" }
            }
            return out
        }

        fun stripMarkers(s: String): String = s.replace(Regex("""(?m)^\s*@ORT=\d+\s*"""), "").replace("@ALLGEMEIN", "")

        fun buildSection(block: SectionBlock): Set<String> {
            val out = linkedSetOf<String>()
            block.items.forEach { item ->
                val txt = wsNorm(stripMarkers(item.text))
                if (txt.isBlank()) return@forEach
                val time = item.zeitAnzeige?.replace('-', '–')
                out += if (time != null) "$time $txt" else txt
            }
            return out
        }

        var count = 0
        count += pairAndCount(buildWeather(current), buildWeather(baseline))
        count += pairAndCount(buildPersonal(current), buildPersonal(baseline))
        count += pairAndCount(buildMaschinen(current), buildMaschinen(baseline))
        count += pairAndCount(buildSection(current.abnahmen), buildSection(baseline.abnahmen))
        count += pairAndCount(buildSection(current.einweisungUnterweisung), buildSection(baseline.einweisungUnterweisung))
        count += pairAndCount(buildSection(current.besonderheiten), buildSection(baseline.besonderheiten))
        count += pairAndCount(buildSection(current.maengelSchaeden), buildSection(baseline.maengelSchaeden))
        val baselineArbeiten = if (baseline.arbeiten.items.isNotEmpty()) {
            baseline.arbeiten
        } else {
            sectionFromMultiline(stripOrtMarkers(baseline.notizen.orEmpty()))
        }
        count += pairAndCount(buildSection(sectionFromMultiline(stripOrtMarkers(rawArbeiten))), buildSection(baselineArbeiten))
        return count.coerceAtLeast(1)
    }

    private data class PdfTimesInfo(
        val finalPdfHm: String?,
        val draftPdfHm: String?,
        val lastAnyPdfHm: String?,
        val changedRevisionCount: Int?
    )

    private fun findPdfTimes(ctx: BaustelleKontext): PdfTimesInfo {
        val safe = safeFolderName(ctx.name)
        val dateIso = selectedDate.format(isoFmt)
        val skey = schichtKeyForCurrent()

        val dir = runCatching { BtbSpeicher.btbsFuerBaustelle(requireContext(), ctx.id, dateIso) }.getOrNull()
            ?: return PdfTimesInfo(null, null, null, null)

        val finalFile = File(dir, "${safe}_${skey}.pdf")
        val finalHm = if (finalFile.exists()) formatHm(finalFile.lastModified()) else null

        val draft = dir.listFiles()?.filter {
            it.isFile && it.extension.equals("pdf", true) &&
                it.name.contains("_entwurf_", ignoreCase = true) &&
                it.name.contains("_${skey}", ignoreCase = true)
        }?.maxByOrNull { it.lastModified() }
        val draftHm = draft?.let { formatHm(it.lastModified()) }

        val changedRegex = Regex(
            """^${Regex.escape(safe)}_geaendert(\d*)_${Regex.escape(skey)}\.pdf$""",
            RegexOption.IGNORE_CASE
        )

        var maxRev = 0
        var lastChangedTime: Long? = null

        dir.listFiles()?.forEach { f ->
            if (!f.isFile || !f.extension.equals("pdf", true)) return@forEach
            val m = changedRegex.matchEntire(f.name) ?: return@forEach
            val g = m.groupValues.getOrNull(1).orEmpty()
            val rev = g.toIntOrNull() ?: 1
            if (rev > maxRev) maxRev = rev
            if (lastChangedTime == null || f.lastModified() > lastChangedTime!!) lastChangedTime = f.lastModified()
        }

        val lastChangedHm = lastChangedTime?.let { formatHm(it) }
        val revCount = maxRev.takeIf { it > 0 }
        val lastAny = listOfNotNull(lastChangedHm, finalHm, draftHm).firstOrNull()

        return PdfTimesInfo(finalHm, draftHm, lastAny, revCount)
    }

    private fun schichtKeyForCurrent(): String {
        val base = selectedDate.format(isoFmt).replace("-", "_")
        val s = shiftStart?.trim().orEmpty()
        val e = shiftEnd?.trim().orEmpty()
        if (s.isBlank() || e.isBlank()) return base

        val start = runCatching { LocalTime.parse(s, timeFmt) }.getOrNull() ?: return base
        val end = runCatching { LocalTime.parse(e, timeFmt) }.getOrNull() ?: return base

        if (!end.isAfter(start)) {
            val endDay = selectedDate.plusDays(1).dayOfMonth.toString().padStart(2, '0')
            return "$base-$endDay"
        }
        return base
    }

    private fun safeFolderName(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_\\-]"), "")
            .ifBlank { "baustelle" }
    }
    /**
     * Validierung beim Klick auf "Speichern":
     * - Datum/Zeit (Schicht Start/Ende) muss gesetzt sein
     * - Wenn in Personal oder Geräte/Maschinen eine Firma genutzt wird, müssen dort
     *   Zeit (von/bis) UND mindestens eine Zeile (Anzahl + Bezeichnung) vorhanden sein.
     *
     * Rückgabe:
     * - null => alles OK
     * - String => Meldung für Popup (gruppiert)
     */
    private fun buildFinishValidationMessage(): String? {
        val missingDateTime = shiftStart.isNullOrBlank() || shiftEnd.isNullOrBlank()

        val personalErrors = collectFirmaSectionErrors(personalUis)
        val machineErrors = collectFirmaSectionErrors(maschinenUis)

        if (!missingDateTime && personalErrors.isEmpty() && machineErrors.isEmpty()) return null

        val parts = mutableListOf<String>()
        parts += getString(R.string.btb_validate_intro_a)
        parts += ""

        if (missingDateTime) {
            parts += getString(R.string.btb_validate_section_datetime)
            parts += getString(R.string.btb_validate_missing_datetime)
            parts += ""
        }

        if (personalErrors.isNotEmpty()) {
            parts += getString(R.string.btb_validate_section_personal)
            parts += personalErrors
            parts += ""
        }

        if (machineErrors.isNotEmpty()) {
            parts += getString(R.string.btb_validate_section_machines)
            parts += machineErrors
        }

        return parts.joinToString("\n").trim()
    }

    private fun collectFirmaSectionErrors(uis: List<FirmaBlockUi>): List<String> {
        val errors = mutableListOf<String>()
        for (b in uis) {
            val firma = b.acFirma.text?.toString().orEmpty().trim()
            val von = b.etVon.text?.toString().orEmpty().trim()
            val bis = b.etBis.text?.toString().orEmpty().trim()

            val rowStates = b.rows.map { r ->
                val anz = r.acAnzahl.text?.toString().orEmpty().trim()
                val bez = r.acBez.text?.toString().orEmpty().trim()
                Triple(anz, bez, (anz.isNotBlank() || bez.isNotBlank()))
            }

            val hasRowsAny = rowStates.any { it.third }
            val hasTimeAny = von.isNotBlank() || bis.isNotBlank()
            val hasAny = firma.isNotBlank() || hasTimeAny || hasRowsAny

            // Auto-Linked Placeholder ignorieren, solange keine echten Daten eingetragen wurden
            if (b.autoLinked && !hasTimeAny && !hasRowsAny) continue
            if (!hasAny) continue

            if (firma.isBlank()) {
                errors.add(getString(R.string.btb_validate_company_missing))
                continue
            }

            if (von.isBlank() || bis.isBlank()) {
                errors.add(getString(R.string.btb_validate_company_time_missing, firma))
            }

            val nonEmptyRows = rowStates.withIndex().filter { it.value.third }
            if (nonEmptyRows.isEmpty()) {
                errors.add(getString(R.string.btb_validate_company_rows_missing, firma))
            } else {
                nonEmptyRows.forEach { (idx, t) ->
                    val anz = t.first
                    val bez = t.second
                    if (anz.isBlank() || bez.isBlank()) {
                        errors.add(getString(R.string.btb_validate_company_row_incomplete, firma, idx + 1))
                    }
                }
            }
        }
        return errors
    }


        private fun setTopFieldsCollapsed(collapsed: Boolean) {
            topFieldsCollapsed = collapsed
            weatherHeader.visibility = if (collapsed) View.GONE else View.VISIBLE
            pmHeaderRow.visibility = if (collapsed) View.GONE else View.VISIBLE
            if (collapsed) {
                wetterContainer.visibility = View.GONE
                personalSection.visibility = View.GONE
                maschinenSection.visibility = View.GONE
                tvWetterPreview.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down_24, 0)
                updatePmArrow(tvPersonalPreview, expanded = false)
                updatePmArrow(tvMaschinenPreview, expanded = false)
            }
            tvTopFieldsToggle.text = if (collapsed) "Details anzeigen" else "Details ausblenden"
            val iconRes = if (collapsed) R.drawable.ic_arrow_drop_down_24 else R.drawable.ic_arrow_drop_up_24
            tvTopFieldsToggle.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconRes, 0)
        }

        private fun toggleWeather(): Boolean {
            val expanded = wetterContainer.visibility == View.VISIBLE
            val willExpand = !expanded
            wetterContainer.visibility = if (expanded) View.GONE else View.VISIBLE
            // Pfeil im Pill-Button (nicht im Label), damit Wetter/Datum-Zeit gleich aussehen
            val iconRes = if (expanded) {
                // wird eingeklappt -> Pfeil nach unten
                R.drawable.ic_arrow_drop_down_24
            } else {
                // wird aufgeklappt -> Pfeil nach oben
                R.drawable.ic_arrow_drop_up_24
            }
            tvWetterPreview.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconRes, 0)

            return willExpand
        }

        private fun formatRainMm(value: Double?): String =
            value?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { String.format(Locale.GERMANY, "%.1f mm", it) }
                ?: "– mm"

        private fun updateWeatherMmLabels() {
            tvWetterMm1.text = formatRainMm(wetterMm1)
            tvWetterMm2.text = formatRainMm(wetterMm2)
            tvWetterMm3.text = formatRainMm(wetterMm3)
        }

        private fun updateWeatherPreview() {
            updateWeatherMmLabels()
            val slots = collectWeatherSlots()
            // Standardwerte zählen nicht als "eingetragen" (sonst wäre immer OK).
            // Ein bekannter 0,0-mm-Wert ist echte Wetterinformation und darf nicht wie null wirken.
            val hasAnyData = slots.any {
                it.tempC != null ||
                    it.niederschlagMm != null ||
                    (it.bewoelkung != null && it.bewoelkung != DEFAULT_BEWOELKUNG) ||
                    (it.niederschlag != null && it.niederschlag != DEFAULT_NIEDERSCHLAG)
            }
            if (!hasAnyData) {
                tvWetterPreview.text = "Keine Daten"
                tvWetterPreview.setTextColor(Color.BLACK)
                ViewCompat.setBackgroundTintList(
                    tvWetterPreview,
                    ColorStateList.valueOf(Color.parseColor("#FFCDD2"))
                )
            } else {
                val stand = lastWetterFetchMs.takeIf { it > 0L }?.let { ms ->
                    runCatching {
                        java.time.Instant.ofEpochMilli(ms)
                            .atZone(ZoneId.systemDefault())
                            .toLocalTime()
                            .format(timeFmt)
                    }.getOrNull()
                }
                tvWetterPreview.text = if (stand != null) "OK · $stand" else "OK"
                tvWetterPreview.setTextColor(Color.BLACK)
                ViewCompat.setBackgroundTintList(
                    tvWetterPreview,
                    ColorStateList.valueOf(Color.parseColor("#DFF3E0"))
                )
            }
        }

    
    private fun showDatePicker(onPicked: (() -> Unit)? = null) {
        val d = selectedDate
        BtbRangeCalendarDialog.show(this, d, 7) { picked ->
            showSchichtTypDialog(picked) { st ->
                lifecycleScope.launch {
                    beginContextSwitch()
                    selectedDate = picked
                    selectedSchichtTyp = st
                    updateDateTimeButton()
                    ladeEintragFallsVorhanden(activateSelection = true)
                    endContextSwitch()
                    onPicked?.invoke()
                }
            }
        }
    }

        // Statusmodell für den 7-Tage-Dialog
        private enum class ReportState { NEW_EMPTY, DRAFT, FINAL, CHANGED }

        private data class ShiftUi(
            val date: LocalDate,
            val schicht: SchichtTyp,
            val title: String,
            val status: String,
            val times: String?,
            val state: ReportState
        )


    private fun showBerichtOeffnenLetzte7TageDialog() {
        lifecycleScope.launch {
            val ctx = resolveAktuelleBaustelle()
            if (ctx == null) {
                Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val today = LocalDate.now()
            val days = (0..6).map { today.minusDays(it.toLong()) } // heute zuerst

            fun formatDayHeader(d: LocalDate): String {
                // z.B. Do., 27.02.2026
                return d.format(DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy"))
            }

            fun computeShiftUi(date: LocalDate, st: SchichtTyp): ShiftUi {
                val e = BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, date, st)
                val times = if (e != null && !e.startUhrzeit.isNullOrBlank() && !e.endeUhrzeit.isNullOrBlank()) {
                    "${e.startUhrzeit}–${e.endeUhrzeit}"
                } else null

                val baseKey = date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
                val nightKey = "${baseKey}-${date.plusDays(1).dayOfMonth.toString().padStart(2, '0')}"
                val keyForPdf = if (st == SchichtTyp.NACHT) nightKey else baseKey

                val bi = BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, date, st)
                val (state, status) = when {
                    e == null -> ReportState.NEW_EMPTY to getString(R.string.btb_status_new_empty)
                    !bi.exists -> ReportState.DRAFT to getString(R.string.btb_status_draft)
                    bi.changedSinceBaseline -> {
                        // Zähler aus PDFs (wenn vorhanden), sonst mindestens 1
                        val rev = findRevisionCountFor(ctx, date, keyForPdf).coerceAtLeast(1)
                        ReportState.CHANGED to "${getString(R.string.btb_status_changed)} ($rev)"
                    }
                    else -> ReportState.FINAL to getString(R.string.btb_status_final)
                }

                val title = if (st == SchichtTyp.TAG) getString(R.string.btb_shift_tag)
                else "${getString(R.string.btb_shift_nacht)}${getString(R.string.btb_shift_suffix_nextday)}"

                return ShiftUi(date, st, title, status, times, state)
            }

            val models = withContext(Dispatchers.IO) {
                days.map { d ->
                    val tag = computeShiftUi(d, SchichtTyp.TAG)
                    val nacht = computeShiftUi(d, SchichtTyp.NACHT)
                    Triple(d, tag, nacht)
                }
            }

            // UI bauen
            val density = resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(4))
            }

            fun makeHeader(text: String): TextView = TextView(requireContext()).apply {
                this.text = text
                setTextColor(Color.DKGRAY)
                setTypeface(typeface, Typeface.BOLD)
                textSize = 14f
                setPadding(0, dp(8), 0, dp(6))
            }

            fun makeShiftButton(ui: ShiftUi, onClick: () -> Unit): MaterialButton {
                val bg = when (ui.state) {
                    ReportState.DRAFT -> "#616161"      // grau
                    ReportState.FINAL -> "#2E7D32"      // grün
                    ReportState.CHANGED -> "#D32F2F"    // rot
                    ReportState.NEW_EMPTY -> "#1E66FF"  // blau (neu/leer)
                }

                val btn = MaterialButton(requireContext()).apply {
                    text = buildString {
                        append(ui.title)
                        ui.times?.let { append(" ("); append(it); append(")") }
                        append(" — ")
                        append(ui.status)
                    }
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(bg))
                    cornerRadius = dp(10)
                    insetTop = 0
                    insetBottom = 0
                    minHeight = dp(44)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    isAllCaps = false
                    setOnClickListener { onClick() }
                }
                return btn
            }

            val scroll = ScrollView(requireContext()).apply {
                addView(container)
            }

            val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.btb_open_title))
                .setView(scroll)
                .setNegativeButton("Abbrechen", null)
                .create()

            fun switchTo(date: LocalDate, st: SchichtTyp) {
                lifecycleScope.launch {
                    beginContextSwitch()
                    selectedDate = date
                    selectedSchichtTyp = st
                    updateDateTimeButton()
                    ladeEintragFallsVorhanden(activateSelection = true)
                    endContextSwitch()
                }
            }

            models.forEach { (date, tag, nacht) ->
                container.addView(makeHeader(formatDayHeader(date)))

                container.addView(makeShiftButton(tag) {
                    dlg.dismiss()
                    switchTo(tag.date, tag.schicht)
                })

                container.addView(Space(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
                })

                container.addView(makeShiftButton(nacht) {
                    dlg.dismiss()
                    switchTo(nacht.date, nacht.schicht)
                })

                container.addView(Space(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14))
                })
            }

            dlg.show()
        }
    }

    private fun findRevisionCountFor(ctx: BaustelleKontext, date: LocalDate, shiftKey: String): Int {
        val safe = safeFolderName(ctx.name)
        val dateIso = date.format(isoFmt)
        val dir = runCatching { BtbSpeicher.btbsFuerBaustelle(requireContext(), ctx.id, dateIso) }.getOrNull() ?: return 0

        // geaendertN_<shiftKey>.pdf oder legacy: geaendert_<shiftKey>.pdf
        val rx = Regex(
            """^${Regex.escape(safe)}_geaendert(\d*)_${Regex.escape(shiftKey)}\.pdf$""",
            RegexOption.IGNORE_CASE
        )

        var maxRev = 0
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (!f.extension.equals("pdf", true)) return@forEach
            val m = rx.matchEntire(f.name) ?: return@forEach
            val g = m.groupValues.getOrNull(1).orEmpty()
            val rev = g.toIntOrNull() ?: 1
            if (rev > maxRev) maxRev = rev
        }
        return maxRev
    }

    private fun showSchichtTypDialog(datum: LocalDate, onChosen: (SchichtTyp) -> Unit) {
        lifecycleScope.launch {
            val ctx = resolveAktuelleBaustelle()
            if (ctx == null) {
                Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val dateTxt = datum.format(deDateFmt)

            val (tagInfo, nachtInfo) = withContext(Dispatchers.IO) {
                data class ShiftInfo(val status: String, val times: String?)
                fun infoFor(st: SchichtTyp): ShiftInfo {
                    val e = BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, datum, st)
                    if (e == null) return ShiftInfo(getString(R.string.btb_status_new_empty), null)
                    val bi = BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, datum, st)
                    val status = when {
                        !bi.exists -> getString(R.string.btb_status_draft)
                        bi.changedSinceBaseline -> getString(R.string.btb_status_changed)
                        else -> getString(R.string.btb_status_final)
                    }
                    val times = if (!e.startUhrzeit.isNullOrBlank() && !e.endeUhrzeit.isNullOrBlank())
                        "${e.startUhrzeit}–${e.endeUhrzeit}" else null
                    return ShiftInfo(status, times)
                }
                infoFor(SchichtTyp.TAG) to infoFor(SchichtTyp.NACHT)
            }

            val dlgView = layoutInflater.inflate(R.layout.dialog_btb_open_shift, null)
            dlgView.findViewById<android.widget.TextView>(R.id.tvOpenDate)
                .text = getString(R.string.btb_open_date, dateTxt)

            val btnTag = dlgView.findViewById<MaterialButton>(R.id.btnShiftTag)
            val btnNacht = dlgView.findViewById<MaterialButton>(R.id.btnShiftNacht)

            val tagTimes = tagInfo.times?.let { " ($it)" } ?: ""
            val nachtTimes = nachtInfo.times?.let { " ($it)" } ?: ""
            btnTag.text = "${getString(R.string.btb_shift_tag)}$tagTimes — ${tagInfo.status}"
            btnNacht.text = "${getString(R.string.btb_shift_nacht)}$nachtTimes${getString(R.string.btb_shift_suffix_nextday)} — ${nachtInfo.status}"

            val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.btb_open_title))
                .setView(dlgView)
                .setNegativeButton("Abbrechen", null)
                .create()

            btnTag.setOnClickListener {
                dlg.dismiss()
                onChosen(SchichtTyp.TAG)
            }
            btnNacht.setOnClickListener {
                dlg.dismiss()
                onChosen(SchichtTyp.NACHT)
            }

            dlg.show()
        }
    }



        private fun openNewBtbStartMode() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                val proposal = withContext(Dispatchers.IO) { findNewBtbProposal(ctx) }
                newBtbProposal = proposal
                newBtbUseDateTime = false
                newBtbUsePersonal = false
                newBtbUseMaschinen = false
                newBtbUseWeather = false
                newBtbPreparedDate = null
                newBtbPreparedStart = null
                newBtbPreparedEnd = null
                newBtbPreparedSchichtTyp = null
                newBtbPreparedFirmen = null
                newBtbPreparedWeatherSlots = null
                newBtbPreparedWeatherMessage = null
                cardNewBtbStartMode.visibility = View.VISIBLE
                renderNewBtbStartMode()
                scrollRoot.post { (scrollRoot as? ScrollView)?.smoothScrollTo(0, 0) }
            }
        }

        private fun closeNewBtbStartMode() {
            cardNewBtbStartMode.visibility = View.GONE
            newBtbProposal = null
            newBtbUseDateTime = false
            newBtbUsePersonal = false
            newBtbUseMaschinen = false
            newBtbUseWeather = false
            newBtbPreparedDate = null
            newBtbPreparedStart = null
            newBtbPreparedEnd = null
            newBtbPreparedSchichtTyp = null
            newBtbPreparedFirmen = null
            newBtbPreparedWeatherSlots = null
            newBtbPreparedWeatherMessage = null
        }

        private fun renderNewBtbStartMode() {
            val proposal = newBtbProposal
            val src = proposal?.sourceLabel ?: "Kein letzter BTB gefunden"
            tvNewBtbHint.text = if (proposal == null) {
                "Kein letzter BTB gefunden. Neuer BTB startet leer."
            } else {
                "Vorschläge aus $src. Aktueller BTB bleibt unverändert."
            }

            val effectiveDate = newBtbPreparedDate ?: selectedDate
            val effectiveStart = newBtbPreparedStart ?: proposal?.startUhrzeit
            val effectiveEnd = newBtbPreparedEnd ?: proposal?.endeUhrzeit
            tvNewBtbDateTimeProposal.text = when {
                newBtbUseDateTime && (!effectiveStart.isNullOrBlank() || !effectiveEnd.isNullOrBlank()) -> {
                    val times = listOfNotNull(effectiveStart, effectiveEnd).joinToString("–")
                    val schichtTxt = if ((newBtbPreparedSchichtTyp ?: selectedSchichtTyp) == SchichtTyp.TAG) getString(R.string.btb_shift_tag) else getString(R.string.btb_shift_nacht)
                    "$times · ${effectiveDate.format(deDateFmt)} · $schichtTxt"
                }
                proposal != null -> {
                    val times = listOfNotNull(proposal.startUhrzeit, proposal.endeUhrzeit).joinToString("–")
                    if (times.isBlank()) "Kein Vorschlag" else "$times · ${proposal.sourceLabel}"
                }
                else -> "Kein Vorschlag"
            }

            val previewFirmen = newBtbPreparedFirmen ?: proposal?.firmen.orEmpty()
            tvNewBtbPersonalProposal.text = if (newBtbUsePersonal && previewFirmen.isNotEmpty()) {
                summarizeFirmenForProposal(previewFirmen, personal = true)
            } else proposal?.let {
                summarizeFirmenForProposal(it.firmen, personal = true)
            } ?: "Kein Vorschlag"

            tvNewBtbMaschinenProposal.text = if (newBtbUseMaschinen && previewFirmen.isNotEmpty()) {
                summarizeFirmenForProposal(previewFirmen, personal = false)
            } else proposal?.let {
                summarizeFirmenForProposal(it.firmen, personal = false)
            } ?: "Kein Vorschlag"

            tvNewBtbWeatherProposal.text = when {
                newBtbPreparedWeatherMessage != null -> newBtbPreparedWeatherMessage!!
                !newBtbPreparedWeatherSlots.isNullOrEmpty() -> "Nur Zeiten · " + newBtbPreparedWeatherSlots!!.joinToString(" · ") { it.uhrzeit }
                proposal != null -> if (proposal.weatherTimes.isEmpty()) "Nur Zeiten · kein Vorschlag" else "Nur Zeiten · ${proposal.weatherTimes.joinToString(" · ")}"
                else -> "Nur Zeiten · kein Vorschlag"
            }

            styleProposalButton(btnNewBtbUseDateTime, newBtbUseDateTime, newBtbPreparedStart != null || newBtbPreparedEnd != null || proposal?.startUhrzeit != null || proposal?.endeUhrzeit != null)
            styleProposalButton(btnNewBtbUsePersonal, newBtbUsePersonal, previewFirmen.any { it.visibleInPersonal })
            styleProposalButton(btnNewBtbUseMaschinen, newBtbUseMaschinen, previewFirmen.any { it.visibleInMaschinen })
            styleProposalButton(btnNewBtbUseWeather, newBtbUseWeather, !newBtbPreparedWeatherSlots.isNullOrEmpty() || proposal?.weatherTimes?.isNotEmpty() == true)
            if (newBtbUsePersonal && previewFirmen.any { it.visibleInPersonal }) btnNewBtbUsePersonal.text = "Bearbeiten"
            if (newBtbUseMaschinen && previewFirmen.any { it.visibleInMaschinen }) btnNewBtbUseMaschinen.text = "Bearbeiten"
        }

        private fun computeAutoWeatherTimesFor(start: String?, ende: String?): List<String> {
            fun parse(time: String?): Int? {
                val p = time?.trim()?.split(":") ?: return null
                val h = p.getOrNull(0)?.toIntOrNull() ?: return null
                val m = p.getOrNull(1)?.toIntOrNull() ?: return null
                return h * 60 + m
            }
            fun fmt(min: Int): String {
                val mm = (min % (24 * 60) + (24 * 60)) % (24 * 60)
                val h = mm / 60
                val m = mm % 60
                return String.format("%02d:%02d", h, m)
            }
            val sMin = parse(start)
            val eMinRaw = parse(ende)
            if (sMin == null || eMinRaw == null) return emptyList()
            val eMin = if (eMinRaw < sMin) eMinRaw + 24 * 60 else eMinRaw
            val span = (eMin - sMin).coerceAtLeast(1)
            val t1 = roundUpToQuarterMinutes(sMin + span / 6)
            val t2 = roundUpToQuarterMinutes(sMin + span / 2)
            val t3 = roundUpToQuarterMinutes(sMin + (span * 5) / 6)
            return listOf(fmt(t1), fmt(t2), fmt(t3))
        }

        private fun currentNewBtbFirmen(): List<FirmaBlock> = newBtbPreparedFirmen ?: newBtbProposal?.firmen.orEmpty()

        private fun buildNewBtbSectionPreview(section: PmSection, firmen: List<FirmaBlock>): String {
            val lines = firmenToLegacyLines(firmen, section).takeIf { it.isNotEmpty() } ?: return "Kein Vorschlag"
            return lines.joinToString("\n")
        }

        private fun parseLegacySectionToFirmen(section: PmSection, text: String, base: List<FirmaBlock>): List<FirmaBlock> {
            val blocks = legacyLinesToBlocks(text.lines().map { it.trim() }.filter { it.isNotBlank() })
            val byKey = linkedMapOf<String, FirmaBlock>()
            fun keyOf(name: String) = name.trim().lowercase(java.util.Locale.ROOT)
            base.forEach { byKey[keyOf(it.name)] = it }
            val clearedBase = byKey.mapValues { (_, block) ->
                if (section == PmSection.PERSONAL) block.copy(visibleInPersonal = false, personal = emptyList(), personalVon = null, personalBis = null)
                else block.copy(visibleInMaschinen = false, geraete = emptyList(), maschinenVon = null, maschinenBis = null)
            }.toMutableMap()
            blocks.forEach { block ->
                val name = block.firma.trim()
                if (name.isBlank()) return@forEach
                val key = keyOf(name)
                val current = clearedBase[key] ?: if (section == PmSection.PERSONAL) {
                    FirmaBlock(firmaId = "firma-" + key.hashCode(), name = name, visibleInPersonal = false, visibleInMaschinen = false)
                } else {
                    FirmaBlock(firmaId = "firma-" + key.hashCode(), name = name, visibleInPersonal = false, visibleInMaschinen = false)
                }
                clearedBase[key] = if (section == PmSection.PERSONAL) {
                    current.copy(
                        name = name,
                        visibleInPersonal = true,
                        personal = block.rows.mapNotNull { (anz, bez) ->
                            val role = bez?.trim().orEmpty()
                            if (anz.isNullOrBlank() && role.isBlank()) null else PersonalZeile(anz?.toIntOrNull(), role)
                        }
                    )
                } else {
                    current.copy(
                        name = name,
                        visibleInMaschinen = true,
                        geraete = block.rows.mapNotNull { (anz, bez) ->
                            val title = bez?.trim().orEmpty()
                            if (anz.isNullOrBlank() && title.isBlank()) null else GeraetZeile(anz?.toIntOrNull(), title)
                        }
                    )
                }
            }
            return clearedBase.values.filter { it.visibleInPersonal || it.visibleInMaschinen }
        }

        private fun showNewBtbSectionEditorDialog(section: PmSection) {
            val title = if (section == PmSection.PERSONAL) "Personal bearbeiten" else "Geräte/Maschinen bearbeiten"
            val ctx = requireContext()
            val density = resources.displayMetrics.density
            val pad = (12 * density).toInt()
            val currentAll = currentNewBtbFirmen()
            val proposalFiltered = (newBtbProposal?.firmen ?: emptyList()).filter {
                if (section == PmSection.PERSONAL) it.visibleInPersonal else it.visibleInMaschinen
            }
            val dialogBlocks = mutableListOf<FirmaBlockUi>()

            val scroll = ScrollView(ctx)
            val outer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
            }
            scroll.addView(
                outer,
                android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            fun styleBlueButton(button: MaterialButton) {
                button.isAllCaps = false
                button.setTextColor(Color.WHITE)
                ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(Color.parseColor("#3B82F6")))
                button.cornerRadius = (14 * density).toInt()
                button.insetTop = 0
                button.insetBottom = 0
            }

            fun styleCheckButton(button: MaterialButton) {
                button.isAllCaps = false
                button.setTextColor(Color.WHITE)
                ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(Color.parseColor("#2563EB")))
                button.cornerRadius = (14 * density).toInt()
                button.insetTop = 0
                button.insetBottom = 0
            }

            val actions = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val btnAddFirma = MaterialButton(ctx).apply {
                text = "+ Firma"
                styleBlueButton(this)
            }
            val btnVortag = MaterialButton(ctx).apply {
                text = "Vortag übernehmen"
                styleBlueButton(this)
            }
            val btnSave = MaterialButton(ctx).apply {
                text = "✓"
                styleCheckButton(this)
            }
            actions.addView(btnAddFirma, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = (8 * density).toInt() })
            actions.addView(btnVortag, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = (8 * density).toInt() })
            actions.addView(btnSave, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.55f))
            outer.addView(actions)

            val blocksContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (10 * density).toInt(), 0, 0)
            }
            outer.addView(blocksContainer)

            fun setupDialogTimeField(et: com.google.android.material.textfield.TextInputEditText) {
                et.isCursorVisible = false
                et.keyListener = null
                et.isFocusable = false
                et.isFocusableInTouchMode = false
                et.isClickable = true
                et.setOnLongClickListener { true }
            }

            fun addDialogRow(block: FirmaBlockUi, prefillAnzahl: String?, prefillBez: String?) {
                val rowView = layoutInflater.inflate(R.layout.item_firma_row, block.rowsContainer, false)
                val acAnzahl: MaterialAutoCompleteTextView = rowView.requireViewByIdCompat(R.id.acAnzahl)
                val acBez: MaterialAutoCompleteTextView = rowView.requireViewByIdCompat(R.id.acBez)
                val btnRemove: android.widget.ImageButton = rowView.requireViewByIdCompat(R.id.btnRemoveRow)

                refreshAnzahlAdapter(acAnzahl)
                refreshBezAdapter(acBez, section)
                prefillAnzahl?.let { acAnzahl.setText(it, false) }
                prefillBez?.let { acBez.setText(it, false) }

                val ui = PairRowUi(rowView, acAnzahl, acBez, btnRemove)
                block.rows.add(ui)
                block.rowsContainer.addView(rowView)

                btnRemove.setOnClickListener {
                    val idx = block.rows.indexOf(ui)
                    if (idx >= 0) {
                        block.rows.removeAt(idx)
                        block.rowsContainer.removeView(ui.root)
                        if (block.rows.isEmpty()) {
                            addDialogRow(block, null, null)
                        }
                    }
                }
            }

            fun addDialogBlock(prefill: FirmaBlock?) {
                val root = layoutInflater.inflate(R.layout.item_firma_block, blocksContainer, false)
                val acFirma: MaterialAutoCompleteTextView = root.requireViewByIdCompat(R.id.acFirma)
                val etVon: com.google.android.material.textfield.TextInputEditText = root.requireViewByIdCompat(R.id.acVon)
                val etBis: com.google.android.material.textfield.TextInputEditText = root.requireViewByIdCompat(R.id.acBis)
                val rowsContainer: android.widget.LinearLayout = root.requireViewByIdCompat(R.id.rowsContainer)
                val btnAddRow: MaterialButton = root.requireViewByIdCompat(R.id.btnAddRow)
                val btnRemoveFirma: android.widget.ImageButton = root.requireViewByIdCompat(R.id.btnRemoveFirma)
                val firmaId = prefill?.firmaId ?: java.util.UUID.randomUUID().toString()

                val ui = FirmaBlockUi(
                    root = root,
                    firmaId = firmaId,
                    autoLinked = false,
                    acFirma = acFirma,
                    etVon = etVon,
                    etBis = etBis,
                    rowsContainer = rowsContainer,
                    rows = mutableListOf(),
                    btnAddRow = btnAddRow,
                    btnRemoveFirma = btnRemoveFirma
                )

                refreshFirmaAdapter(acFirma)
                setupDialogTimeField(etVon)
                setupDialogTimeField(etBis)
                val times = buildTimes15()
                val suggestedVon = suggestedNowQuarter()

                acFirma.setText(prefill?.name.orEmpty(), false)
                val reportVon = newBtbPreparedStart ?: newBtbProposal?.startUhrzeit ?: shiftStart
                val reportBis = newBtbPreparedEnd ?: newBtbProposal?.endeUhrzeit ?: shiftEnd
                val source = prefill?.let {
                    FirmenZeitbindung.uebernehmen(it, newBtbProposal?.startUhrzeit, newBtbProposal?.endeUhrzeit, reportVon, reportBis)
                }
                val initialVon = if (section == PmSection.PERSONAL) source?.personalVon else source?.maschinenVon
                val initialBis = if (section == PmSection.PERSONAL) source?.personalBis else source?.maschinenBis
                ui.zeitbindung = if (prefill == null) FirmenZeitbindung.REPORT else
                    if (section == PmSection.PERSONAL) source?.personalZeitbindung else source?.maschinenZeitbindung
                etVon.setText(if (prefill == null) reportVon.orEmpty() else initialVon.orEmpty())
                etBis.setText(if (prefill == null) reportBis.orEmpty() else initialBis.orEmpty())
                fun onDialogTimeChanged() {
                    ui.zeitbindung = FirmenZeitbindung.manuellGewaehlt(
                        etVon.text?.toString(), etBis.text?.toString(), reportVon, reportBis
                    )
                }
                etVon.addTextChangedListener(SimpleTextWatcher { onDialogTimeChanged() })
                etBis.addTextChangedListener(SimpleTextWatcher { onDialogTimeChanged() })

                etVon.setOnClickListener {
                    showPmClockPicker(etVon, suggestedVon) {
                        autoFillBisIfBlank(etVon, etBis, 10L)
                    }
                }
                etBis.setOnClickListener {
                    autoFillBisIfBlank(etVon, etBis, 10L)
                    val suggestedBis = plusHours(etVon.text?.toString().orEmpty(), 10L) ?: suggestedVon
                    showPmClockPicker(etBis, suggestedBis)
                }

                val rows = if (section == PmSection.PERSONAL) {
                    prefill?.personal?.map { it.anzahl?.toString() to it.rolle }.orEmpty()
                } else {
                    prefill?.geraete?.map { it.anzahl?.toString() to it.bezeichnung }.orEmpty()
                }
                if (rows.isEmpty()) {
                    addDialogRow(ui, null, null)
                } else {
                    rows.forEach { (anz, bez) -> addDialogRow(ui, anz, bez) }
                }

                btnAddRow.setOnClickListener { addDialogRow(ui, null, null) }
                btnRemoveFirma.setOnClickListener {
                    dialogBlocks.remove(ui)
                    blocksContainer.removeView(root)
                    if (dialogBlocks.isEmpty()) addDialogBlock(null)
                }

                dialogBlocks.add(ui)
                blocksContainer.addView(root)
            }

            fun loadBlocks(source: List<FirmaBlock>) {
                dialogBlocks.clear()
                blocksContainer.removeAllViews()
                if (source.isEmpty()) {
                    addDialogBlock(null)
                } else {
                    source.forEach { addDialogBlock(it) }
                }
            }

            fun saveBlocks() {
                val cleared = linkedMapOf<String, FirmaBlock>()
                currentAll.forEach { block ->
                    cleared[block.firmaId] = if (section == PmSection.PERSONAL) {
                        block.copy(visibleInPersonal = false, personalVon = null, personalBis = null, personal = emptyList())
                    } else {
                        block.copy(visibleInMaschinen = false, maschinenVon = null, maschinenBis = null, geraete = emptyList())
                    }
                }

                dialogBlocks.forEach { ui ->
                    val name = ui.acFirma.text?.toString().orEmpty().trim()
                    val von = ui.etVon.text?.toString().orEmpty().trim().ifBlank { null }
                    val bis = ui.etBis.text?.toString().orEmpty().trim().ifBlank { null }
                    val rowValues = ui.rows.mapNotNull { row ->
                        val anzRaw = row.acAnzahl.text?.toString().orEmpty().trim()
                        val bezRaw = row.acBez.text?.toString().orEmpty().trim()
                        if (anzRaw.isBlank() && bezRaw.isBlank()) null else anzRaw to bezRaw
                    }
                    val hasData = name.isNotBlank() || von != null || bis != null || rowValues.isNotEmpty()
                    if (!hasData) return@forEach

                    val existing = cleared[ui.firmaId]
                        ?: cleared.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        ?: FirmaBlock(firmaId = ui.firmaId, name = name)
                    val finalName = name.ifBlank { existing.name }
                    val merged = if (section == PmSection.PERSONAL) {
                        existing.copy(
                            name = finalName,
                            personalVon = von,
                            personalBis = bis,
                            personalZeitbindung = ui.zeitbindung,
                            visibleInPersonal = true,
                            personal = rowValues.map { (anz, bez) -> PersonalZeile(anz.toIntOrNull(), bez) }
                        )
                    } else {
                        existing.copy(
                            name = finalName,
                            maschinenVon = von,
                            maschinenBis = bis,
                            maschinenZeitbindung = ui.zeitbindung,
                            visibleInMaschinen = true,
                            geraete = rowValues.map { (anz, bez) -> GeraetZeile(anz.toIntOrNull(), bez) }
                        )
                    }
                    cleared[merged.firmaId] = merged
                }

                newBtbPreparedFirmen = cleared.values.filter { it.visibleInPersonal || it.visibleInMaschinen }
                if (section == PmSection.PERSONAL) {
                    newBtbUsePersonal = newBtbPreparedFirmen!!.any { it.visibleInPersonal }
                } else {
                    newBtbUseMaschinen = newBtbPreparedFirmen!!.any { it.visibleInMaschinen }
                }
                renderNewBtbStartMode()
            }

            loadBlocks(currentAll.filter { if (section == PmSection.PERSONAL) it.visibleInPersonal else it.visibleInMaschinen })
            btnVortag.isEnabled = proposalFiltered.isNotEmpty()
            val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(scroll)
                .create()

            btnAddFirma.setOnClickListener {
                addDialogBlock(null)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
            btnVortag.setOnClickListener {
                loadBlocks(proposalFiltered)
            }
            btnSave.setOnClickListener {
                saveBlocks()
                dlg.dismiss()
            }
            dlg.show()
        }

        private fun showNewBtbSectionPreviewDialog(section: PmSection) {
            val title = if (section == PmSection.PERSONAL) "Personal" else "Geräte/Maschinen"
            val message = buildNewBtbSectionPreview(section, currentNewBtbFirmen())
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Schließen", null)
                .setPositiveButton("Übernehmen") { _, _ ->
                    if (section == PmSection.PERSONAL) newBtbUsePersonal = true else newBtbUseMaschinen = true
                    if (newBtbPreparedFirmen == null) newBtbPreparedFirmen = currentNewBtbFirmen()
                    renderNewBtbStartMode()
                }
                .setNeutralButton("Bearbeiten") { _, _ ->
                    showNewBtbSectionEditorDialog(section)
                }
                .show()
        }

        private fun showNewBtbWeatherPreviewDialog() {
            val message = newBtbPreparedWeatherMessage ?: tvNewBtbWeatherProposal.text?.toString().orEmpty().ifBlank { "Nur Zeiten · kein Vorschlag" }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Wetter")
                .setMessage(message)
                .setNegativeButton("Schließen", null)
                .setPositiveButton("Übernehmen") { _, _ ->
                    newBtbUseWeather = true
                    renderNewBtbStartMode()
                }
                .show()
        }

        private fun refreshNewBtbWeatherSuggestion() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    newBtbPreparedWeatherSlots = computeAutoWeatherTimesFor(newBtbPreparedStart, newBtbPreparedEnd).map {
                        WetterSlot(uhrzeit = it, tempC = null, bewoelkung = DEFAULT_BEWOELKUNG, niederschlag = DEFAULT_NIEDERSCHLAG)
                    }
                    newBtbPreparedWeatherMessage = "Kein Standort verfügbar · Wetter bitte manuell eingeben"
                    renderNewBtbStartMode()
                    return@launch
                }
                val times = computeAutoWeatherTimesFor(newBtbPreparedStart, newBtbPreparedEnd)
                if (times.isEmpty()) {
                    newBtbPreparedWeatherSlots = emptyList()
                    newBtbPreparedWeatherMessage = "Nur Zeiten · kein Vorschlag"
                    renderNewBtbStartMode()
                    return@launch
                }
                val (lat, lon) = withContext(Dispatchers.IO) { resolveWetterLocation(ctx) } ?: run {
                    newBtbPreparedWeatherSlots = times.map { WetterSlot(uhrzeit = it, tempC = null, bewoelkung = DEFAULT_BEWOELKUNG, niederschlag = DEFAULT_NIEDERSCHLAG) }
                    newBtbPreparedWeatherMessage = "Kein Standort verfügbar · Wetter bitte manuell eingeben"
                    renderNewBtbStartMode()
                    return@launch
                }
                val targetDate = newBtbPreparedDate ?: selectedDate
                val endDate = if (spansMidnight(newBtbPreparedStart, newBtbPreparedEnd)) targetDate.plusDays(1) else targetDate
                val hourly = withContext(Dispatchers.IO) { OpenMeteoClient.fetchHourly(lat, lon, targetDate, endDate) }
                if (hourly == null) {
                    newBtbPreparedWeatherSlots = times.map { WetterSlot(uhrzeit = it, tempC = null, bewoelkung = DEFAULT_BEWOELKUNG, niederschlag = DEFAULT_NIEDERSCHLAG) }
                    newBtbPreparedWeatherMessage = "Wetter konnte nicht geladen werden · bitte manuell eingeben"
                    renderNewBtbStartMode()
                    return@launch
                }
                val slots = times.map { timeStr ->
                    val hh = timeStr.substringBefore(':').toIntOrNull() ?: 0
                    val mm = timeStr.substringAfter(':').toIntOrNull() ?: 0
                    val dateForTime = if (spansMidnight(newBtbPreparedStart, newBtbPreparedEnd) && parseMinutes(timeStr) != null && parseMinutes(newBtbPreparedStart) != null && parseMinutes(timeStr)!! < parseMinutes(newBtbPreparedStart)!!) targetDate.plusDays(1) else targetDate
                    val target = LocalDateTime.of(dateForTime, LocalTime.of(hh, mm))
                    val h = hourly.closestTo(target)
                    if (h == null) WetterSlot(uhrzeit = timeStr, tempC = null, bewoelkung = DEFAULT_BEWOELKUNG, niederschlag = DEFAULT_NIEDERSCHLAG)
                    else WetterSlot(
                        uhrzeit = timeStr,
                        tempC = kotlin.math.round(h.tempC).toInt(),
                        bewoelkung = mapCloudToBew(h.cloudCoverPct),
                        niederschlag = mapCodeToNied(h.weatherCode, h.precipMm),
                        niederschlagMm = h.precipMm.takeIf { it.isFinite() && it >= 0.0 }
                    )
                }
                newBtbPreparedWeatherSlots = slots
                newBtbPreparedWeatherMessage = "Automatisch aktualisiert · " + slots.joinToString(" · ") { it.uhrzeit }
                renderNewBtbStartMode()
            }
        }

        private fun showNewBtbDateTimeDialog() {
            val dlgView = layoutInflater.inflate(R.layout.dialog_schicht, null)
            val tvDatum = dlgView.findViewById<android.widget.TextView>(R.id.tvDialogDatum)
            val btnDatum = dlgView.findViewById<MaterialButton>(R.id.btnDialogDatum)
            val acStart = dlgView.findViewById<MaterialAutoCompleteTextView>(R.id.acDialogStart)
            val acEnde = dlgView.findViewById<MaterialAutoCompleteTextView>(R.id.acDialogEnde)
            var pickedDate = newBtbPreparedDate ?: selectedDate
            fun setDatumText() { tvDatum.text = pickedDate.format(deDateFmt) }
            setDatumText()
            val times = buildTimes15()
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, times)
            acStart.setAdapter(adapter)
            acEnde.setAdapter(adapter)
            fun maybeAutoFillEndFromStart() {
                val start = acStart.text?.toString().orEmpty().trim()
                val end = acEnde.text?.toString().orEmpty().trim()
                if (start.isBlank() || end.isNotBlank()) return
                val suggestion = plusHours(start, 10L) ?: return
                acEnde.setText(suggestion, false)
            }
            // Android 200036: Start/Ende werden wie die Wetterzeiten über die grafische Uhr gewählt.
            fun showClockPicker(field: MaterialAutoCompleteTextView, autoFillEndBeforeOpen: Boolean = false) {
                if (autoFillEndBeforeOpen) maybeAutoFillEndFromStart()
                val raw = field.text?.toString().orEmpty().trim()
                val initial = runCatching { LocalTime.parse(raw, timeFmt) }.getOrNull()
                    ?: runCatching { LocalTime.parse(suggestedNowQuarter(), timeFmt) }.getOrNull()
                    ?: LocalTime.now()
                TimePickerDialog(requireContext(), { _, h, m ->
                    val rounded = roundUpToQuarter(LocalTime.of(h, m))
                    field.setText(rounded.format(timeFmt), false)
                    if (field === acStart) maybeAutoFillEndFromStart()
                }, initial.hour, initial.minute, true).show()
            }
            acStart.setOnClickListener { showClockPicker(acStart) }
            acEnde.setOnClickListener { showClockPicker(acEnde, autoFillEndBeforeOpen = true) }
            acStart.setText(newBtbPreparedStart ?: newBtbProposal?.startUhrzeit ?: shiftStart.orEmpty(), false)
            acEnde.setText(newBtbPreparedEnd ?: newBtbProposal?.endeUhrzeit ?: shiftEnd.orEmpty(), false)
            maybeAutoFillEndFromStart()
            btnDatum.setOnClickListener {
                val d = pickedDate
                DatePickerDialog(requireContext(), { _, y, m, day ->
                    pickedDate = LocalDate.of(y, m + 1, day)
                    setDatumText()
                }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Datum / Zeit")
                .setView(dlgView)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Übernehmen") { _, _ ->
                    newBtbPreparedDate = pickedDate
                    newBtbPreparedStart = acStart.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                    newBtbPreparedEnd = acEnde.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                    newBtbPreparedSchichtTyp = if (spansMidnight(newBtbPreparedStart, newBtbPreparedEnd)) SchichtTyp.NACHT else SchichtTyp.TAG
                    newBtbUseDateTime = !newBtbPreparedStart.isNullOrBlank() || !newBtbPreparedEnd.isNullOrBlank()
                    refreshNewBtbWeatherSuggestion()
                    renderNewBtbStartMode()
                }
                .show()
        }

        private fun styleProposalButton(button: MaterialButton, selected: Boolean, enabled: Boolean) {
            button.isEnabled = enabled
            if (!enabled) {
                button.text = "Kein Vorschlag"
                button.setTextColor(Color.parseColor("#757575"))
                ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(Color.parseColor("#E0E0E0")))
                return
            }
            button.text = if (selected) "Übernommen" else "Übernehmen"
            button.setTextColor(Color.BLACK)
            val bg = if (selected) "#DFF3E0" else "#E3F2FD"
            ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(Color.parseColor(bg)))
        }

        private fun summarizeFirmenForProposal(firmen: List<FirmaBlock>, personal: Boolean): String {
            val names = firmen.asSequence()
                .filter { if (personal) it.visibleInPersonal else it.visibleInMaschinen }
                .map { it.name.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            if (names.isEmpty()) return "Kein Vorschlag"
            val preview = names.take(2).joinToString(", ")
            return if (names.size <= 2) preview else "$preview +${names.size - 2}"
        }

        private fun findNewBtbProposal(ctx: BaustelleKontext): NewBtbProposal? {
            val candidates = mutableListOf<BautagebuchEintrag>()
            for (offset in 1..14L) {
                val date = selectedDate.minusDays(offset)
                BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, date, SchichtTyp.TAG)?.let { candidates.add(it) }
                BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, date, SchichtTyp.NACHT)?.let { candidates.add(it) }
                if (candidates.isNotEmpty()) break
            }
            val entry = candidates.maxByOrNull { it.geaendertAmEpochMs } ?: return null
            val sourceDate = runCatching { LocalDate.parse(entry.datumIso, isoFmt).format(deDateFmt) }.getOrElse { entry.datumIso }
            val sourceTyp = if (entry.schicht == SchichtTyp.TAG) getString(R.string.btb_shift_tag) else getString(R.string.btb_shift_nacht)
            val weatherTimes = (entry.weather?.slots ?: entry.weatherSlots)
                .mapNotNull { it.uhrzeit.trim().takeIf { s -> s.isNotBlank() } }
                .take(3)
            return NewBtbProposal(
                sourceLabel = "$sourceDate · $sourceTyp",
                startUhrzeit = entry.startUhrzeit,
                endeUhrzeit = entry.endeUhrzeit,
                firmen = entry.firmen,
                weatherTimes = weatherTimes
            )
        }

        private fun buildPreparedFirmenForNewBtb(von: String?, bis: String?): List<FirmaBlock> {
            val baseFirmen = newBtbPreparedFirmen ?: newBtbProposal?.firmen ?: return emptyList()
            return baseFirmen.mapNotNull { firma ->
                val keepPersonal = newBtbUsePersonal && firma.visibleInPersonal
                val keepMaschinen = newBtbUseMaschinen && firma.visibleInMaschinen
                if (!keepPersonal && !keepMaschinen) return@mapNotNull null
                val aligned = FirmenZeitbindung.uebernehmen(
                    firma, newBtbProposal?.startUhrzeit, newBtbProposal?.endeUhrzeit, von, bis
                )
                aligned.copy(
                    visibleInPersonal = keepPersonal,
                    visibleInMaschinen = keepMaschinen,
                    personalVon = if (keepPersonal) aligned.personalVon else null,
                    personalBis = if (keepPersonal) aligned.personalBis else null,
                    maschinenVon = if (keepMaschinen) aligned.maschinenVon else null,
                    maschinenBis = if (keepMaschinen) aligned.maschinenBis else null,
                    personal = if (keepPersonal) firma.personal else emptyList(),
                    geraete = if (keepMaschinen) firma.geraete else emptyList()
                )
            }
        }

        private fun buildPreparedWeatherSlotsForNewBtb(): List<WetterSlot> {
            if (!newBtbUseWeather) return emptyList()
            newBtbPreparedWeatherSlots?.takeIf { it.isNotEmpty() }?.let { return it }
            val times = computeAutoWeatherTimesFor(newBtbPreparedStart ?: newBtbProposal?.startUhrzeit, newBtbPreparedEnd ?: newBtbProposal?.endeUhrzeit)
                .ifEmpty { newBtbProposal?.weatherTimes.orEmpty().filter { it.isNotBlank() }.take(3) }
            return times.map { WetterSlot(uhrzeit = it, tempC = null, bewoelkung = DEFAULT_BEWOELKUNG, niederschlag = DEFAULT_NIEDERSCHLAG) }
        }

        private fun startPreparedNewBtb() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                beginContextSwitch()
                try {
                    val weatherSlots = buildPreparedWeatherSlotsForNewBtb()
                    val targetDate = if (newBtbUseDateTime) (newBtbPreparedDate ?: selectedDate) else selectedDate
                    val targetSchicht = if (newBtbUseDateTime) (newBtbPreparedSchichtTyp ?: if (spansMidnight(newBtbPreparedStart, newBtbPreparedEnd)) SchichtTyp.NACHT else SchichtTyp.TAG) else selectedSchichtTyp
                    val startValue = if (newBtbUseDateTime) (newBtbPreparedStart ?: newBtbProposal?.startUhrzeit) else (shiftStart ?: etStart.text?.toString()?.trim().takeUnless { it.isNullOrBlank() })
                    val endValue = if (newBtbUseDateTime) (newBtbPreparedEnd ?: newBtbProposal?.endeUhrzeit) else (shiftEnd ?: etEnde.text?.toString()?.trim().takeUnless { it.isNullOrBlank() })
                    val firmen = buildPreparedFirmenForNewBtb(startValue, endValue)
                    selectedDate = targetDate
                    selectedSchichtTyp = targetSchicht
                    val now = System.currentTimeMillis()
                    currentBtbStartEpochMs = now
                    withContext(Dispatchers.IO) {
                        BautagebuchSpeicher.archivierenUndLeeren(requireContext(), ctx.id, ctx.name, targetDate, targetSchicht)
                        BtbPhotoSelectionStore.deleteAllFor(requireContext(), ctx.id, targetDate.format(isoFmt), targetSchicht)
                        BtbPhotoTextOverrideStore.deleteAllFor(requireContext(), ctx.id, targetDate.format(isoFmt), targetSchicht)
                        BtbPhotoStartOverrideStore.deleteAllFor(requireContext(), ctx.id, targetDate.format(isoFmt), targetSchicht)
                        BtbPhotoKmOverrideStore.deleteAllFor(requireContext(), ctx.id, targetDate.format(isoFmt), targetSchicht)
                        val entry = BautagebuchEintrag(
                            baustelleName = ctx.name,
                            baustelleId = ctx.id,
                            datumIso = targetDate.format(isoFmt),
                            schicht = targetSchicht,
                            strecke = ctx.strecke,
                            startUhrzeit = startValue,
                            endeUhrzeit = endValue,
                            weather = WeatherBlock(
                                source = WeatherSource.MANUAL,
                                slots = weatherSlots
                            ),
                            weatherSlots = weatherSlots,
                            firmen = firmen,
                            personal = firmenToLegacyLines(firmen, PmSection.PERSONAL),
                            geraete = firmenToLegacyLines(firmen, PmSection.MASCHINEN),
                            includeFotos = true,
                            includeBtbs = true,
                            maxFotosInPdf = currentMaxFotosInPdf,
                            erstelltAmEpochMs = now,
                            geaendertAmEpochMs = now
                        )
                        BautagebuchSpeicher.speichern(requireContext(), entry)
                    }
                    closeNewBtbStartMode()
                    ladeEintragFallsVorhanden()
                    Snackbar.make(requireView(), "Neuer BTB gestartet", Snackbar.LENGTH_SHORT).show()
                } finally {
                    endContextSwitch()
                }
            }
        }

        private fun showDateTimeDropdown() {
            val popup = PopupMenu(requireContext(), btnDateTime)

            val hasShift = !shiftStart.isNullOrBlank() && !shiftEnd.isNullOrBlank()
            val schichtTxt = if (hasShift) "${shiftStart} - ${shiftEnd}" else "Keine Daten"
            val typTxt = if (selectedSchichtTyp == SchichtTyp.TAG) getString(R.string.btb_shift_tag) else getString(R.string.btb_shift_nacht)

            // Infos (Datum/Schicht klickbar)
            popup.menu.add(0, 11, 1, "Datum    ${dateRangeText()}")
            popup.menu.add(0, 12, 2, "Schicht  $schichtTxt")
            popup.menu.add(0, 6, 3, "Typ     $typTxt").isEnabled = false
            popup.menu.add(0, 5, 4, "Status  ${dateTimeStatusText()}").isEnabled = false

            // Aktionen
            popup.menu.add(0, 13, 5, "+ Neuer BTB")
            popup.menu.add(0, 4, 6, "Entfernen")

            popup.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    11 -> { confirmContextSwitchIfRecent { showDatePicker() }; true }
                    12 -> { confirmContextSwitchIfRecent { showSchichtDialog() }; true }
                    13 -> { openNewBtbStartMode(); true }
                    4 -> {
                        shiftStart = null
                        shiftEnd = null
                        etStart.setText("")
                        etEnde.setText("")
                        updateDateTimeButton()
                        autoWetterZeiten()
                        true
                    }
                    else -> true
                }
            }

            popup.show()
        }


    
    
    private fun confirmNeuerBtb() {
        lifecycleScope.launch {
            val ctx = resolveAktuelleBaustelle()
            if (ctx == null) {
                Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val typTxt = if (selectedSchichtTyp == SchichtTyp.TAG) getString(R.string.btb_shift_tag) else getString(R.string.btb_shift_nacht)
            val msg = "Aktueller Entwurf für ${selectedDate.format(deDateFmt)} ($typTxt) wird archiviert und ein neuer gestartet."
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Neuer BTB?")
                .setMessage(msg)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Archivieren & neu") { _, _ ->
                    lifecycleScope.launch {
                        beginContextSwitch()
                        val preservedStart = shiftStart ?: etStart.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                        val preservedEnd = shiftEnd ?: etEnde.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                        withContext(Dispatchers.IO) {
                            BautagebuchSpeicher.archivierenUndLeeren(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                            // Foto-Auswahl/Overrides für dieses Datum+Schicht entfernen (alle Zeiten)
                            BtbPhotoSelectionStore.deleteAllFor(requireContext(), ctx.id, selectedDate.format(isoFmt), selectedSchichtTyp)
                            BtbPhotoTextOverrideStore.deleteAllFor(requireContext(), ctx.id, selectedDate.format(isoFmt), selectedSchichtTyp)
                            BtbPhotoStartOverrideStore.deleteAllFor(requireContext(), ctx.id, selectedDate.format(isoFmt), selectedSchichtTyp)
                            BtbPhotoKmOverrideStore.deleteAllFor(requireContext(), ctx.id, selectedDate.format(isoFmt), selectedSchichtTyp)

                            val now = System.currentTimeMillis()
                            currentBtbStartEpochMs = now
                            BautagebuchSpeicher.speichern(
                                requireContext(),
                                BautagebuchEintrag(
                                    baustelleName = ctx.name,
                                    baustelleId = ctx.id,
                                    datumIso = selectedDate.format(isoFmt),
                                    schicht = selectedSchichtTyp,
                                    strecke = ctx.strecke,
                                    startUhrzeit = preservedStart,
                                    endeUhrzeit = preservedEnd,
                                    includeFotos = true,
                                    includeBtbs = true,
                                    maxFotosInPdf = currentMaxFotosInPdf,
                                    erstelltAmEpochMs = now,
                                    geaendertAmEpochMs = now
                                )
                            )
                        }
                        ladeEintragFallsVorhanden()
                        endContextSwitch()
                        Snackbar.make(requireView(), "Neuer BTB gestartet", Snackbar.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
    }

    private fun showSchichtDialog() {
            val dlgView = layoutInflater.inflate(R.layout.dialog_schicht, null)
            val tvDatum = dlgView.findViewById<android.widget.TextView>(R.id.tvDialogDatum)
            val btnDatum = dlgView.findViewById<MaterialButton>(R.id.btnDialogDatum)
            val acStart = dlgView.findViewById<MaterialAutoCompleteTextView>(R.id.acDialogStart)
            val acEnde = dlgView.findViewById<MaterialAutoCompleteTextView>(R.id.acDialogEnde)

            fun setDatumText() { tvDatum.text = selectedDate.format(deDateFmt) }
            setDatumText()

            // Zeiten (15-Min Raster)
            val times = buildTimes15()
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, times)
            acStart.setAdapter(adapter)
            acEnde.setAdapter(adapter)

            fun maybeAutoFillEndFromStart() {
                val start = acStart.text?.toString().orEmpty().trim()
                val end = acEnde.text?.toString().orEmpty().trim()
                if (start.isBlank() || end.isNotBlank()) return
                val suggestion = plusHours(start, 10L) ?: return
                acEnde.setText(suggestion, false)
            }

            acStart.setOnClickListener { showTimePopup(acStart, times) }
            acEnde.setOnClickListener {
                // Wenn Ende noch leer ist, Vorschlag = Start +10h (nicht 00:00)
                maybeAutoFillEndFromStart()
                showTimePopup(acEnde, times)
            }
            acEnde.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    maybeAutoFillEndFromStart()
                    showTimePopup(acEnde, times)
                }
            }

            acStart.setText(shiftStart.orEmpty(), false)
            acEnde.setText(shiftEnd.orEmpty(), false)

            // Auto-Ende (+10h) auch im Dialog, wenn Start gewählt wird und Ende noch leer ist
            maybeAutoFillEndFromStart()
            acStart.setOnItemClickListener { _, _, _, _ -> maybeAutoFillEndFromStart() }
            acStart.addTextChangedListener(SimpleTextWatcher { maybeAutoFillEndFromStart() })

            btnDatum.setOnClickListener {
                showDatePicker {
                    setDatumText()
                }
            }

            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Datum / Schicht")
                .setView(dlgView)
                .setNegativeButton("Entfernen") { _, _ ->
                    passeFirmenBerichtszeitenAn(shiftStart, shiftEnd, null, null)
                    shiftStart = null
                    shiftEnd = null
                    etStart.setText("")
                    etEnde.setText("")
                    updateDateTimeButton()
                    autoWetterZeiten()
                    schedulePmAutosave(force = true)
                }
                .setPositiveButton("OK") { _, _ ->
                    val newStart = acStart.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                    val newEnd = acEnde.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }

                    val newType = if (spansMidnight(newStart, newEnd)) SchichtTyp.NACHT else SchichtTyp.TAG
                    val typeChanged = newType != selectedSchichtTyp

                    if (!typeChanged) {
                        // Zeiten übernehmen (gleiche Schicht)
                        passeFirmenBerichtszeitenAn(shiftStart, shiftEnd, newStart, newEnd)
                        shiftStart = newStart
                        shiftEnd = newEnd
                        etStart.setText(shiftStart.orEmpty())
                        etEnde.setText(shiftEnd.orEmpty())
                        updateDateTimeButton()
                        autoWetterZeiten()
                        schedulePmAutosave(force = true)
                    } else {
                        // Schichtwechsel: Autosave stoppen, Ziel-BTB laden (oder leer), dann Zeiten setzen
                        lifecycleScope.launch {
                            beginContextSwitch()

                            selectedSchichtTyp = newType

                            // Ziel-Schicht laden/leer starten
                            setStatus("Lade ${if (newType == SchichtTyp.TAG) getString(R.string.btb_shift_tag) else getString(R.string.btb_shift_nacht)}…")
                            ladeEintragFallsVorhanden(activateSelection = true)

                            // gewählte Zeiten im Ziel-BTB beibehalten
                            passeFirmenBerichtszeitenAn(shiftStart, shiftEnd, newStart, newEnd)
                            shiftStart = newStart
                            shiftEnd = newEnd
                            etStart.setText(shiftStart.orEmpty())
                            etEnde.setText(shiftEnd.orEmpty())

                            updateDateTimeButton()
                            autoWetterZeiten()

                            endContextSwitch()
                            schedulePmAutosave(force = true)
                        }
                    }
                }
                .show()
        }

        private fun buildTimes15(): List<String> {
            val times = mutableListOf<String>()
            for (h in 0..23) {
                for (m in listOf(0, 15, 30, 45)) {
                    times += String.format("%02d:%02d", h, m)
                }
            }
            return times
        }

        /**
         * Zeitbereich aus einem Feld, z.B. "00:30–21:45" oder "00:30-21:45".
         * Liefert (von, bis). Beide Werte können null sein.
         */
        private fun parseZeitRange(raw: String): Pair<String?, String?> {
            val t = raw.trim()
            if (t.isBlank()) return null to null

            // Normalize: verschiedene Striche vereinheitlichen
            val normalized = t
                .replace('—', '–')
                .replace('-', '–')

            val parts = normalized.split('–', limit = 2)
            val von = parts.getOrNull(0)?.trim().takeUnless { it.isNullOrBlank() }
            val bis = parts.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() }
            return von to bis
        }

        private fun buildZeitRange(von: String?, bis: String?): String? {
            val v = von?.trim().orEmpty()
            val b = bis?.trim().orEmpty()
            return when {
                v.isBlank() && b.isBlank() -> null
                v.isNotBlank() && b.isNotBlank() -> "${v}–${b}"
                v.isNotBlank() -> "${v}–"
                else -> "–${b}"
            }
        }
        /** Entfernt nur technische Editor-Marker aus sichtbaren Vorschauen. */
        private fun stripOrtMarkers(raw: String): String {
            if (raw.isBlank()) return raw
            val marker = Regex("""^@(ORT|NODE|ENTRY|FIRMAID)=[^\s]+\s*""", RegexOption.IGNORE_CASE)
            val general = Regex("""^@ALLGEMEIN\b\s*""", RegexOption.IGNORE_CASE)
            return raw.lines().mapNotNull { source ->
                var line = source.trimEnd().trimStart()
                if (line.isBlank()) return@mapNotNull null
                var changed: Boolean
                do {
                    changed = false
                    val next = marker.replaceFirst(line, "")
                    if (next != line) {
                        line = next.trimStart()
                        changed = true
                    }
                    val generalNext = general.replaceFirst(line, "")
                    if (generalNext != line) {
                        line = generalNext.trimStart()
                        changed = true
                    }
                } while (changed)
                line.takeIf { it.isNotBlank() }
            }.joinToString("\n").trimEnd()
        }

        private fun setupStartEndDropdowns() {
            // Zeiten im 15-Minuten-Raster (Layout-Felder sind ausgeblendet, aber bleiben als Fallback/Legacy)
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, buildTimes15())
            etStart.setAdapter(adapter)
            etEnde.setAdapter(adapter)

            // Dropdown sofort öffnen bei Klick
            etStart.setOnClickListener { etStart.showDropDown() }
            etEnde.setOnClickListener { etEnde.showDropDown() }
        }

        private suspend fun resolveAktuelleBaustelle(): BaustelleKontext? {
            val altId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first() ?: return null
            val entity = ServiceLocator.baustellenRepository.baustelleMitIdOnce(altId) ?: return null

            val baumRepo = ServiceLocator.baustellenBaumRepository
            val aktuellerKnotenId = ServiceLocator.aktuelleBaustelleSpeicher.aktuellerKnotenId.first()
            val hauptKnotenId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()

            suspend fun streckeAusPfad(startId: Long?): String? {
                if (startId == null) return null
                val pfad = runCatching { baumRepo.pfadVonRootBis(startId) }.getOrNull().orEmpty()
                return pfad.asReversed().firstOrNull { !it.streckenkennung.isNullOrBlank() }?.streckenkennung?.trim()
            }

            suspend fun streckeAusRoot(rootId: Long?): String? {
                if (rootId == null) return null
                val alle = runCatching { baumRepo.alleKnotenZuRoot(rootId).first() }.getOrNull().orEmpty()
                return alle.firstOrNull { !it.streckenkennung.isNullOrBlank() }?.streckenkennung?.trim()
            }

            val strecke = streckeAusPfad(aktuellerKnotenId)
                ?: streckeAusPfad(hauptKnotenId)
                ?: streckeAusRoot(hauptKnotenId)
                ?: entity.streckennummer

            return BaustelleKontext(
                id = altId,
                name = entity.baustellenname,
                strecke = strecke,
                rootKnotenId = hauptKnotenId
            )
        }

        private suspend fun refreshBtbNodeBindingCache(rootKnotenId: Long?) {
            btbNodeKeyByLocalId.clear()
            btbLocalIdByNodeKey.clear()
            val rootId = rootKnotenId ?: return
            val binding = ServiceLocator.worksiteBindingRepository.bindingForLocalRoot(rootId) ?: return
            val bindings = ServiceLocator.syncDatenbank.worksiteNodeSyncDao()
                .forWorksite(binding.worksiteServerId)
            bindings.filter { it.localRootKnotenId == rootId }.forEach { item ->
                btbNodeKeyByLocalId[item.localKnotenId] = item.clientNodeKey
                btbLocalIdByNodeKey[item.clientNodeKey] = item.localKnotenId
            }
        }

        private suspend fun stableNodeKey(rootKnotenId: Long?, localNodeId: Long): String? {
            if (localNodeId <= 0L) return null
            btbNodeKeyByLocalId[localNodeId]?.let { return it }
            val rootId = rootKnotenId ?: return null
            return ServiceLocator.worksiteStructureSync
                .ensureClientNodeKey(rootId, localNodeId)
                ?.also { key ->
                    btbNodeKeyByLocalId[localNodeId] = key
                    btbLocalIdByNodeKey[key] = localNodeId
                }
        }

        private fun filterFotosForBtbStart(
            fotos: List<de.tbhelfer.datenbank.FotoEntity>,
            startEpochMs: Long?
        ): List<de.tbhelfer.datenbank.FotoEntity> {
            // BTB-Regel: Fotos werden über das Schicht-Zeitfenster bestimmt,
            // nicht über den Zeitpunkt, an dem der BTB-Eintrag angelegt wurde.
            // Dadurch sind auch Bilder vor dem Öffnen des BTB auswählbar.
            return fotos
        }

        private suspend fun ladeEintragFallsVorhanden(
            autoPickSchichtForToday: Boolean = false,
            activateSelection: Boolean = false
        ) {
            val ctx = resolveAktuelleBaustelle()
            if (ctx == null) {
                currentBtbStartEpochMs = null
                closeNewBtbStartMode()
                setStatus("Bitte zuerst eine Baustelle auswählen")
                setEnabledUi(false)
                return
            }
            refreshBtbNodeBindingCache(ctx.rootKnotenId)

            // Titel: aktuelle Baustelle ganz oben anzeigen
            tvTitleBaustelle.text = "Aktuelle Baustelle: ${ctx.name}"
            setEnabledUi(true)
            if (cardNewBtbStartMode.isVisible && !isSwitchingContext) {
                renderNewBtbStartMode()
            }

            // Autocomplete Optionen aus Historie laden
            loadHistoryOptions(ctx.name)

            val explicitBtbId = requestedBtbId
            val entry = withContext(Dispatchers.IO) {
                when {
                    explicitBtbId != null -> {
                        // Verlauf/Schnellwechsel: exakt dieselbe stabile btbId laden.
                        BautagebuchSpeicher.ladenById(requireContext(), ctx.id, ctx.name, explicitBtbId)
                    }
                    autoPickSchichtForToday -> {
                        // Normaler Einstieg: ausschliesslich der aktive Pointer entscheidet.
                        // Datum/Schicht-Historienabfragen duerfen diesen Pointer nicht veraendern.
                        BtbActiveContext.laden(requireContext(), ctx.id)?.entry
                    }
                    else -> {
                        // Bewusster Datum-/Schichtwechsel im Editor. Der Leseweg selbst bleibt
                        // side-effect-frei; erfolgreiche Auswahl wird unten explizit aktiviert.
                        BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                    }
                }
            }
            requestedBtbId = null

            if (entry != null && (explicitBtbId != null || activateSelection)) {
                val id = entry.btbId?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("legacy:") }
                val entryDate = runCatching { LocalDate.parse(entry.datumIso, isoFmt) }.getOrNull()
                if (id != null && entryDate != null) {
                    withContext(Dispatchers.IO) {
                        BautagebuchSpeicher.aktivierenBtb(
                            requireContext(),
                            ctx.id,
                            ctx.name,
                            id,
                            entryDate,
                            entry.schicht
                        )
                        BtbActivePointerControl.bewusstReaktivieren(
                            requireContext(),
                            ctx.id,
                            ctx.name,
                            id
                        )
                    }
                }
            }

            if (entry == null) {
                loadedBtbId = null
                loadedBtbChangedEpochMs = Long.MIN_VALUE
                currentBtbStartEpochMs = System.currentTimeMillis()
                // Felder leeren
                shiftStart = null
                shiftEnd = null
                etStart.setText("")
                etEnde.setText("")
                resetWetter()
                lastWetterFetchMs = 0L
                lastWetterLat = null
                lastWetterLon = null
                lastWetterOrt = null
                lastWetterStation = null
                lastWetterSource = WeatherSource.MANUAL
                etWetter.setText("")
                etPersonal.setText("")
                etGeraete.setText("")
                rawArbeiten = ""
                rawAbnahmen = ""
                rawEinweisung = ""
                rawBesonderheiten = ""
                rawMaengel = ""
                renderSectionPreviews()
                setStatus("Neuer Eintrag")
                clearPmAll()
                updatePmPreviews()

                // Baseline-Status zurücksetzen
                baselineExists = false
                changedSinceBaseline = false
                baselineLastModifiedEpochMs = null
                lastSavedEpochMs = null
            } else {
                currentBtbStartEpochMs = entry.erstelltAmEpochMs
                runCatching { LocalDate.parse(entry.datumIso, isoFmt) }.getOrNull()?.let { selectedDate = it }
                selectedSchichtTyp = entry.schicht
                loadedBtbId = entry.btbId
                loadedBtbChangedEpochMs = entry.geaendertAmEpochMs
                shiftStart = entry.startUhrzeit
                shiftEnd = entry.endeUhrzeit
                etStart.setText(entry.startUhrzeit.orEmpty())
                etEnde.setText(entry.endeUhrzeit.orEmpty())

                // Wetter: neuer Block bevorzugt (mit Stand/Quelle/Ort)
                val wb = entry.weather
                if (wb != null) {
                    lastWetterFetchMs = wb.standEpochMs ?: 0L
                    lastWetterLat = wb.lat
                    lastWetterLon = wb.lon
                    lastWetterOrt = wb.locationName
                    lastWetterStation = wb.stationName
                    lastWetterSource = wb.source
                    if (wb.slots.isNotEmpty()) {
                        applyWeatherSlots(wb.slots)
                    } else if (entry.weatherSlots.isNotEmpty()) {
                        applyWeatherSlots(entry.weatherSlots)
                    } else {
                        resetWetter()
                    }
                } else if (entry.weatherSlots.isNotEmpty()) {
                    applyWeatherSlots(entry.weatherSlots)
                } else {
                    lastWetterFetchMs = 0L
                    lastWetterLat = null
                    lastWetterLon = null
                    lastWetterOrt = null
                    lastWetterStation = null
                    lastWetterSource = WeatherSource.MANUAL
                    resetWetter()
                    // fallback
                    etWetter.setText(entry.wetter.orEmpty())
                }
                if (entry.firmen.isNotEmpty()) {
                    applyPmFromEntryFirmen(entry)
                } else {
                    // Fallback: ältere Einträge (Legacy-Textlisten)
                    etPersonal.setText(entry.personal.joinToString("\n"))
                    etGeraete.setText(entry.geraete.joinToString("\n"))
                    applyLegacyPmFromText()
                }

                // Phase 3b: Standardabschnitte aus Entry übernehmen
                rawArbeiten = if (entry.arbeiten.items.isNotEmpty()) {
                    sectionToMultiline(entry.arbeiten)
                } else {
                    // Historische Android-BTBs legten Punkt 4 als notizen ab.
                    entry.notizen.orEmpty()
                }
                rawAbnahmen = sectionToMultiline(entry.abnahmen)
                rawEinweisung = sectionToMultiline(entry.einweisungUnterweisung)
                rawBesonderheiten = sectionToMultiline(entry.besonderheiten)
                rawMaengel = sectionToMultiline(entry.maengelSchaeden)
                currentMaxFotosInPdf = entry.maxFotosInPdf
                cachedStatusBaustelleId = entry.baustelleId
                lastSavedEpochMs = entry.geaendertAmEpochMs
                renderSectionPreviews()
                setStatus("Geladen")
            }

            btbLoadInitialized = true

            // Foto-Chips: Zähler aktualisieren (Fotos in aktueller Schicht)
            val fotoCutoffEpochMs = currentBtbStartEpochMs ?: entry?.erstelltAmEpochMs
            val fotosInShift = withContext(Dispatchers.IO) {
                try {
                    val all = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(ctx.id)
                    val (from, to) = shiftWindowEpochMs(selectedDate, shiftStart, shiftEnd)
                    filterFotosForBtbStart(
                        all.filter { it.erstelltAmEpochMs in from until to },
                        fotoCutoffEpochMs
                    )
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val excludedByType = withContext(Dispatchers.IO) {
                BtbPhotoSelectionStore.loadExcluded(requireContext(), ctx.id, selectedDate.format(isoFmt), shiftStart, shiftEnd, selectedSchichtTyp)
            }
            val overridesByFotoId = withContext(Dispatchers.IO) {
                BtbPhotoTextOverrideStore.loadOverrides(
                    requireContext(),
                    ctx.id,
                    selectedDate.format(isoFmt),
                    shiftStart,
                    shiftEnd,
                    selectedSchichtTyp
                )
            }
            val startOverridesByFotoId = withContext(Dispatchers.IO) {
                BtbPhotoStartOverrideStore.loadOverrides(
                    requireContext(),
                    ctx.id,
                    selectedDate.format(isoFmt),
                    shiftStart,
                    shiftEnd,
                    selectedSchichtTyp
                )
            }
            val photoUi = withContext(Dispatchers.Default) {
                computePhotoUiState(
                    excludedByType = excludedByType,
                    fotosInShift = fotosInShift,
                    overridesByFotoId = overridesByFotoId,
                    startOverridesByFotoId = startOverridesByFotoId
                )
            }
            applyPhotoUiState(photoUi)

            // Baseline-Info (Long-Press "PDF erstellen")
            val bi = withContext(Dispatchers.IO) {
                BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
            }
            baselineExists = bi.exists
            changedSinceBaseline = bi.changedSinceBaseline
            baselineLastModifiedEpochMs = bi.baselineLastModifiedEpochMs

            // Option A: Vergangene Tage automatisch mit Wetter füllen, wenn noch keine Wetterdaten vorhanden sind.
            if (entry != null && selectedDate.isBefore(LocalDate.now()) && entryHasNoWeather(entry)) {
                wetterAutomatischFuellen(force = true)
            }

            updateDateTimeButton()
        }

        private fun entryHasNoWeather(entry: BautagebuchEintrag): Boolean {
            val hasWeatherBlockSlots = entry.weather?.slots?.any { s ->
                s.tempC != null ||
                    s.niederschlagMm != null ||
                    (s.bewoelkung != null && s.bewoelkung != DEFAULT_BEWOELKUNG) ||
                    (s.niederschlag != null && s.niederschlag != DEFAULT_NIEDERSCHLAG)
            } == true

            val hasLegacySlots = entry.weatherSlots.any { s ->
                s.tempC != null ||
                    s.niederschlagMm != null ||
                    (s.bewoelkung != null && s.bewoelkung != DEFAULT_BEWOELKUNG) ||
                    (s.niederschlag != null && s.niederschlag != DEFAULT_NIEDERSCHLAG)
            }

            val hasLegacyText = !entry.wetter.isNullOrBlank()
            return !hasWeatherBlockSlots && !hasLegacySlots && !hasLegacyText
        }


    
        private data class PhotoUiState(
            val chipArbeiten: String,
            val chipAbnahmen: String,
            val chipEinweisung: String,
            val chipBesonderheiten: String,
            val chipMaengel: String,
            val arbeitenText: String,
            val abnahmenText: String,
            val einweisungText: String,
            val besonderheitenText: String,
            val maengelText: String
        )

        /** Berechnet Chip-Texte + Vorschau-Texte im Background, damit UI nicht hängt (ANR-Schutz). */
        private fun computePhotoUiState(
            excludedByType: Map<String, Set<Long>>,
            fotosInShift: List<de.tbhelfer.datenbank.FotoEntity>,
            overridesByFotoId: Map<Long, String>,
            startOverridesByFotoId: Map<Long, String>
        ): PhotoUiState {

            fun labelFor(typ: de.tbhelfer.datenbank.Bildtyp): String {
                val ids = fotosInShift.asSequence().filter { it.bildtyp == typ }.map { it.id }.toList()
                val total = ids.size
                if (total == 0) return "📷 0"
                val excluded = excludedByType[typ.name] ?: emptySet()
                val selected = ids.count { !excluded.contains(it) }
                return "📷 $selected/$total"
            }

            fun merged(raw: String, typ: de.tbhelfer.datenbank.Bildtyp): String {
                val manual = stripOrtMarkers(raw)
                    .lines()
                    .map { de.tbhelfer.util.KmAnzeige.zeile(it) }
                    .filter { it.isNotBlank() }

                val manualLast = manual.takeLast(2)
                val need = (2 - manualLast.size).coerceAtLeast(0)

                val photoLines = buildPhotoPreviewLines(
                    typ = typ,
                    fotosInShift = fotosInShift,
                    excludedByType = excludedByType,
                    overridesByFotoId = overridesByFotoId,
                    startOverridesByFotoId = startOverridesByFotoId
                )

                val addPhotos = if (need > 0) photoLines.takeLast(need) else emptyList()
                val out = manualLast + addPhotos
                return out.joinToString("\n") { "• $it" }
            }

            return PhotoUiState(
                chipArbeiten = labelFor(de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION),
                chipAbnahmen = labelFor(de.tbhelfer.datenbank.Bildtyp.ABNAHMEN),
                chipEinweisung = labelFor(de.tbhelfer.datenbank.Bildtyp.EINWEISUNG_UNTERWEISUNG),
                chipBesonderheiten = labelFor(de.tbhelfer.datenbank.Bildtyp.BESONDERHEITEN),
                chipMaengel = labelFor(de.tbhelfer.datenbank.Bildtyp.MAENGEL_SCHAEDEN),
                arbeitenText = merged(rawArbeiten, de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION),
                abnahmenText = merged(rawAbnahmen, de.tbhelfer.datenbank.Bildtyp.ABNAHMEN),
                einweisungText = merged(rawEinweisung, de.tbhelfer.datenbank.Bildtyp.EINWEISUNG_UNTERWEISUNG),
                besonderheitenText = merged(rawBesonderheiten, de.tbhelfer.datenbank.Bildtyp.BESONDERHEITEN),
                maengelText = merged(rawMaengel, de.tbhelfer.datenbank.Bildtyp.MAENGEL_SCHAEDEN)
            )
        }

        private fun applyPhotoUiState(s: PhotoUiState) {
            btnFotosArbeiten.text = s.chipArbeiten
            btnFotosAbnahmen.text = s.chipAbnahmen.replace("📷 ", "📷")
            btnFotosEinweisung.text = s.chipEinweisung
            btnFotosBesonderheiten.text = s.chipBesonderheiten
            btnFotosMaengel.text = s.chipMaengel.replace("📷 ", "📷")

            sectionsApplying = true
            try {
                etArbeiten.setText(s.arbeitenText)
                etAbnahmen.setText(s.abnahmenText)
                etEinweisung.setText(s.einweisungText)
                etBesonderheiten.setText(s.besonderheitenText)
                etMaengel.setText(s.maengelText)
            } finally {
                sectionsApplying = false
            }
        }

    private fun updateFotoChipTexts(
            excludedByType: Map<String, Set<Long>>,
            fotosInShift: List<de.tbhelfer.datenbank.FotoEntity>
        ) {
            fun labelFor(typ: de.tbhelfer.datenbank.Bildtyp): String {
                val idsInShift = fotosInShift.filter { it.bildtyp == typ }.map { it.id }
                val total = idsInShift.size
                if (total == 0) return "📷 0"

                val excluded = excludedByType[typ.name] ?: emptySet()
                val selected = idsInShift.count { !excluded.contains(it) }
                return "📷 $selected/$total"
            }

            btnFotosArbeiten.text = labelFor(de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION)
            btnFotosAbnahmen.text = labelFor(de.tbhelfer.datenbank.Bildtyp.ABNAHMEN).replace("📷 ", "📷")
            btnFotosEinweisung.text = labelFor(de.tbhelfer.datenbank.Bildtyp.EINWEISUNG_UNTERWEISUNG)
            btnFotosBesonderheiten.text = labelFor(de.tbhelfer.datenbank.Bildtyp.BESONDERHEITEN)
            btnFotosMaengel.text = labelFor(de.tbhelfer.datenbank.Bildtyp.MAENGEL_SCHAEDEN).replace("📷 ", "📷")
        }

        private fun setEnabledUi(enabled: Boolean) {
            btnDateTime.isEnabled = enabled
            btnSpeichern.isEnabled = enabled
            btnPdf.isEnabled = enabled
            btnPdfFinal.isEnabled = enabled
            btnQuickNewBtb.isEnabled = enabled
            btnQuickNewBtb.alpha = if (enabled) 1f else 0.5f
        }

        private fun setStatus(text: String) {
            val tv = (tvStatus as? android.widget.TextView) ?: return
            val normalized = when (text) {
                "Automatisch gespeichert", "Auto-gespeichert" -> buildAutosaveStatusText(lastSavedEpochMs)
                "Gespeichert" -> buildAutosaveStatusText(lastSavedEpochMs)
                "Geladen" -> buildAutosaveStatusText(lastSavedEpochMs)
                "Neuer Eintrag" -> "Neu · noch nicht gespeichert"
                else -> text
            }
            tv.visibility = View.VISIBLE
            tv.alpha = 1f
            tv.text = normalized
            val color = when {
                normalized.startsWith("Speichert") -> Color.parseColor("#1565C0")
                normalized.contains("fehlgeschlagen", ignoreCase = true) -> Color.parseColor("#B00020")
                normalized.contains("Vom Server bestätigt") -> Color.parseColor("#2E7D32")
                normalized.startsWith("Neu") -> Color.parseColor("#757575")
                else -> Color.parseColor("#616161")
            }
            tv.setTextColor(color)
            statusFadeJob?.cancel()
        }

        private fun buildAutosaveStatusText(epochMs: Long?): String {
            val localId = cachedStatusBaustelleId ?: return "Lokal gespeichert · Serverbestätigung ausstehend"
            val id = loadedBtbId ?: return "Lokal gespeichert · Serverbestätigung ausstehend"
            val status = de.tbhelfer.server.SelectiveSyncStore.status(requireContext(), localId, id)
            val hm = formatHm(epochMs)
            return if (hm.isNullOrBlank()) status else "$status · $hm"
        }

        private fun existingLegacyNotesForBuild(): String? {
            // Punkt 4 wurde bis 200068 in notizen gespeichert. Nach dem ersten Laden landet der
            // Inhalt in rawArbeiten und wird fortan als arbeiten geschrieben. Neue Punkt-4-Texte
            // duerfen nicht erneut einen NOTIZEN-Servereintrag erzeugen.
            return null
        }

        private fun buildEntry(ctx: BaustelleKontext): BautagebuchEintrag {
            // Phase 3: Personal/Geräte kommen aus PM-UI und werden als Firmenblöcke persistiert.
            // Für Abwärtskompatibilität erzeugen wir zusätzlich Legacy-Zeilen.
            val firmen = collectFirmenFromPmUi()

            fun legacyPersonalLines(): List<String> = firmenToLegacyLines(firmen, PmSection.PERSONAL)
            fun legacyGeraeteLines(): List<String> = firmenToLegacyLines(firmen, PmSection.MASCHINEN)

            return BautagebuchEintrag(
                baustelleName = ctx.name,
                baustelleId = ctx.id,
                datumIso = selectedDate.format(isoFmt),
                schicht = selectedSchichtTyp,
                strecke = ctx.strecke,
                startUhrzeit = shiftStart ?: etStart.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
                endeUhrzeit = shiftEnd ?: etEnde.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
                    wetter = etWetter.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
                    weather = WeatherBlock(
                        standEpochMs = lastWetterFetchMs.takeIf { it > 0L },
                        source = lastWetterSource,
                        locationName = lastWetterOrt,
                        stationName = lastWetterStation,
                        lat = lastWetterLat,
                        lon = lastWetterLon,
                        slots = collectWeatherSlots()
                    ),
                    weatherSlots = collectWeatherSlots(),
                firmen = firmen,
                personal = legacyPersonalLines(),
                geraete = legacyGeraeteLines(),
                arbeiten = sectionFromMultiline(rawArbeiten),
                abnahmen = sectionFromMultiline(rawAbnahmen),
                einweisungUnterweisung = sectionFromMultiline(rawEinweisung),
                besonderheiten = sectionFromMultiline(rawBesonderheiten),
                maengelSchaeden = sectionFromMultiline(rawMaengel),
                // Neue Punkt-4-Arbeiten sind echte ARBEIT-Eintraege; notizen bleibt Legacy/sonstige Notiz.
                notizen = existingLegacyNotesForBuild(),
                includeFotos = true,
                includeBtbs = true,
                erstelltAmEpochMs = currentBtbStartEpochMs ?: System.currentTimeMillis())
        }



        private suspend fun istAusgewaehlterBtbGesperrt(ctx: BaustelleKontext): Boolean {
            val bi = withContext(Dispatchers.IO) {
                BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
            }
            val alterTage = java.time.temporal.ChronoUnit.DAYS.between(selectedDate, LocalDate.now())
            return bi.exists && alterTage > 7
        }

        private fun zeigeBtbGesperrtHinweis() {
            Snackbar.make(requireView(), "BTB ist älter als 7 Tage und gesperrt. Nur PDF/ZIP anzeigen.", Snackbar.LENGTH_LONG).show()
        }

        private fun saveAndReloadWholeBtbSilently() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle() ?: return@launch
                if (istAusgewaehlterBtbGesperrt(ctx)) return@launch
                val existing = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
                val draft = buildEntry(ctx)
                val entry = draft.copy(
                    maxFotosInPdf = existing?.maxFotosInPdf ?: draft.maxFotosInPdf,
                    pdfAuswahl = existing?.pdfAuswahl ?: draft.pdfAuswahl,
                    erstelltAmEpochMs = existing?.erstelltAmEpochMs ?: draft.erstelltAmEpochMs,
                    geaendertAmEpochMs = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.speichern(requireContext(), entry)
                }
                sectionsDirtySinceMs = null
                pmDirtySinceMs = null
                wetterDirtySinceMs = null
                cachedStatusBaustelleId = entry.baustelleId
                lastSavedEpochMs = entry.geaendertAmEpochMs
                scheduleUpdateCurrentReportBanner()
                val bi = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
                baselineExists = bi.exists
                changedSinceBaseline = bi.changedSinceBaseline
                baselineLastModifiedEpochMs = bi.baselineLastModifiedEpochMs
                ladeEintragFallsVorhanden(autoPickSchichtForToday = false)
                setStatus("Automatisch gespeichert")
            }
        }

        private fun speichern(closeAfter: Boolean = false) {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                if (istAusgewaehlterBtbGesperrt(ctx)) {
                    zeigeBtbGesperrtHinweis()
                    return@launch
                }
                val existing = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
                val draft = buildEntry(ctx)
                val entry = draft.copy(
                    maxFotosInPdf = existing?.maxFotosInPdf ?: draft.maxFotosInPdf,
                    pdfAuswahl = existing?.pdfAuswahl ?: draft.pdfAuswahl,
                    erstelltAmEpochMs = existing?.erstelltAmEpochMs ?: draft.erstelltAmEpochMs,
                    geaendertAmEpochMs = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.speichern(requireContext(), entry)
                }
                cachedStatusBaustelleId = entry.baustelleId
                lastSavedEpochMs = entry.geaendertAmEpochMs
                val bi = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
                baselineExists = bi.exists
                changedSinceBaseline = bi.changedSinceBaseline
                baselineLastModifiedEpochMs = bi.baselineLastModifiedEpochMs
                updateDateTimeButton()
                scheduleUpdateCurrentReportBanner()
                setStatus("Gespeichert")
                Snackbar.make(requireView(), "Bautagebuch lokal gespeichert (${entry.datumIso})", Snackbar.LENGTH_SHORT).show()
                if (closeAfter) {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }

            }
        }

        private fun uebernehmenVomVortag() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val prev = selectedDate.minusDays(1)
                val entry = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, prev, selectedSchichtTyp)
                }

                if (entry == null) {
                    Snackbar.make(requireView(), "Kein Eintrag für Vortag (${prev.format(isoFmt)})", Snackbar.LENGTH_SHORT).show()
                    return@launch
                }

                    // Übernehmen vom Vortag: nur Personal + Geräte/Maschinen
                if (entry.firmen.isNotEmpty()) {
                    applyPmFromEntryFirmen(entry)
                } else {
                    etPersonal.setText(entry.personal.joinToString("\n"))
                    etGeraete.setText(entry.geraete.joinToString("\n"))
                    applyLegacyPmFromText()
                }
                setStatus("Vom Vortag übernommen")
                Snackbar.make(requireView(), "Übernommen vom ${prev.format(isoFmt)}", Snackbar.LENGTH_SHORT).show()
            }
        }

        /** Übernimmt nur die gewählte PM-Sektion (Personal ODER Maschinen) vom Vortag. */
        private fun uebernehmenPmVomVortag(section: PmSection) {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val prev = selectedDate.minusDays(1)
                val prevEntry = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, prev, selectedSchichtTyp)
                }

                if (prevEntry == null) {
                    Snackbar.make(requireView(), "Kein Eintrag für Vortag (${prev.format(isoFmt)})", Snackbar.LENGTH_SHORT).show()
                    return@launch
                }

                // Nur diese Sektion austauschen
                pmApplying = true
                try {
                    clearBlocks(section)
                    for (firma in prevEntry.firmen) {
                        val visible = if (section == PmSection.PERSONAL) firma.visibleInPersonal else firma.visibleInMaschinen
                        if (!visible) continue

                        val aligned = FirmenZeitbindung.uebernehmen(
                            firma, prevEntry.startUhrzeit, prevEntry.endeUhrzeit, shiftStart, shiftEnd
                        )
                        val von = if (section == PmSection.PERSONAL) aligned.personalVon else aligned.maschinenVon
                        val bis = if (section == PmSection.PERSONAL) aligned.personalBis else aligned.maschinenBis
                        val binding = if (section == PmSection.PERSONAL) aligned.personalZeitbindung else aligned.maschinenZeitbindung
                        addFirmaBlock(section, firmaId = firma.firmaId, prefillFirma = firma.name, prefillVon = von, prefillBis = bis, zeitbindung = binding)
                        val ui = (if (section == PmSection.PERSONAL) personalUis else maschinenUis)
                            .lastOrNull() ?: continue
                        ui.rowsContainer.removeAllViews(); ui.rows.clear()

                        if (section == PmSection.PERSONAL) {
                            if (firma.personal.isEmpty()) {
                                addPairRow(ui, section, null, null)
                            } else {
                                firma.personal.forEach { p ->
                                    addPairRow(ui, section, p.anzahl?.toString(), p.rolle)
                                }
                            }
                        } else {
                            if (firma.geraete.isEmpty()) {
                                addPairRow(ui, section, null, null)
                            } else {
                                firma.geraete.forEach { g ->
                                    addPairRow(ui, section, g.anzahl?.toString(), g.bezeichnung)
                                }
                            }
                        }
                    }
                    updatePmPreviews()
                } finally {
                    pmApplying = false
                }

                schedulePmAutosave(force = true)
                setStatus("Vom Vortag übernommen")
                Snackbar.make(requireView(), "Übernommen (${if (section == PmSection.PERSONAL) "Personal" else "Geräte"}) vom ${prev.format(isoFmt)}", Snackbar.LENGTH_SHORT).show()
            }
        }

        private fun createDraftPdfDirect() {
            pdfErstellen(setBaseline = false, closeAfterSuccess = false, asDraft = true, deleteDraftOnFinal = false)
        }


        private fun showPdfExportConfirmDialog() {
            val msg = buildFinishValidationMessage()
            if (msg != null) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.btb_validate_title))
                    .setMessage(msg)
                    .setPositiveButton(getString(R.string.btb_validate_ok), null)
                    .show()
                return
            }

            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Exportpaket erstellen")
                .setMessage(
                    "Enthält:\n" +
                        "✓ PDF Abschluss\n" +
                        "✓ BTB_Textdatei.txt\n" +
                        "✓ Bilderliste_Schicht.txt + .csv\n" +
                        "✓ Bilderliste_gesamt.csv pro Baustelle\n" +
                        "✓ aussagekräftige Bild-Dateinamen\n" +
                        "✓ Windows-Suchinfos in JPG-Eigenschaften\n" +
                        "✓ Original-GPS/Aufnahmezeit bleiben unverändert\n" +
                        "✓ alle Bilder aus dem BTB-Zeitfenster"
                )
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Export erstellen") { _, _ ->
                    exportPdfAbschlussPaket()
                }
                .show()
        }

        private fun exportPdfAbschlussPaket() {
            lifecycleScope.launch {
                val baustellenCtx = resolveAktuelleBaustelle()
                if (baustellenCtx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                if (istAusgewaehlterBtbGesperrt(baustellenCtx)) {
                    zeigeBtbGesperrtHinweis()
                    return@launch
                }

                val existing = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.laden(requireContext(), baustellenCtx.id, baustellenCtx.name, selectedDate, selectedSchichtTyp)
                }

                val entryDraft = buildEntry(baustellenCtx)
                val entry = entryDraft.copy(
                    maxFotosInPdf = existing?.maxFotosInPdf ?: entryDraft.maxFotosInPdf,
                    pdfAuswahl = existing?.pdfAuswahl ?: emptyMap(),
                    erstelltAmEpochMs = existing?.erstelltAmEpochMs ?: entryDraft.erstelltAmEpochMs,
                    geaendertAmEpochMs = System.currentTimeMillis()
                )

                val appContext = requireContext().applicationContext

                try {
                    val pdfFile = withContext(Dispatchers.IO) {
                        BautagebuchSpeicher.speichern(appContext, entry)
                        cachedStatusBaustelleId = entry.baustelleId
                lastSavedEpochMs = entry.geaendertAmEpochMs

                        // Export über langen Druck auf PDF Abschluss soll auch dann einen echten Abschluss erzeugen,
                        // wenn der Tagesabschluss noch nicht vorher gesetzt wurde.
                        BautagebuchSpeicher.baselineSetzenWennNichtVorhanden(appContext, baustellenCtx.id, baustellenCtx.name, selectedDate, selectedSchichtTyp)

                        val pdf = BautagebuchPdf(
                            context = appContext,
                            fotoRepository = ServiceLocator.fotoRepository
                        ).erstellenPdf(entry, asDraft = false)

                        BautagebuchPdf(
                            context = appContext,
                            fotoRepository = ServiceLocator.fotoRepository
                        ).deleteEntwurfPdf(entry)

                        pdf
                    }

                    val alleFotos = withContext(Dispatchers.IO) {
                        ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(entry.baustelleId)
                    }

                    val result = withContext(Dispatchers.IO) {
                        BtbExportPaket.erstellen(
                            context = appContext,
                            entry = entry,
                            pdfFile = pdfFile,
                            alleFotosDerBaustelle = alleFotos
                        )
                    }

                    val bi = withContext(Dispatchers.IO) {
                        BautagebuchSpeicher.baselineInfo(appContext, baustellenCtx.id, baustellenCtx.name, selectedDate, selectedSchichtTyp)
                    }
                    baselineExists = bi.exists
                    changedSinceBaseline = bi.changedSinceBaseline
                    baselineLastModifiedEpochMs = bi.baselineLastModifiedEpochMs
                    updateDateTimeButton()

                    val hostView = view ?: activity?.findViewById(android.R.id.content)
                    val text = "Exportpaket erstellt · ${result.fotosExportiert} Bilder" +
                        if (result.fehlendeFotos > 0) " · ${result.fehlendeFotos} fehlen" else ""
                    if (hostView != null) {
                        Snackbar.make(hostView, text, Snackbar.LENGTH_LONG)
                            .setAction("Teilen") {
                                val shareContext = context ?: activity ?: return@setAction
                                BtbExportPaket.shareZip(shareContext, result.zipFile)
                            }
                            .show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val hostView = view ?: activity?.findViewById(android.R.id.content)
                    if (hostView != null) {
                        Snackbar.make(hostView, "Exportpaket konnte nicht erstellt werden", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        private fun showPdfFinalConfirmDialog() {
            lifecycleScope.launch {
                val ctx = resolveAktuelleBaustelle()
                if (ctx == null) {
                    Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val bi = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }

                if (bi.exists) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("PDF Abschluss")
                        .setMessage("Dieser BTB besitzt bereits ein unveränderliches Original. Der neue Abschluss ersetzt nur das aktuelle PDF; fachliche Abweichungen zum ersten Original bleiben rot. Abschluss-PDF jetzt erstellen?")
                        .setNegativeButton("Nein", null)
                        .setPositiveButton("Ja") { _, _ ->
                            pdfErstellen(setBaseline = false, closeAfterSuccess = true, asDraft = false, deleteDraftOnFinal = false)
                        }
                        .show()
                    return@launch
                }

                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("PDF Abschluss")
                    .setMessage("Erster Abschluss: Dieser Stand wird als unveränderliches Original festgeschrieben. Spätere fachliche Änderungen werden im Folge-PDF rot dargestellt. PDF-Abschluss jetzt erstellen?")
                    .setNegativeButton("Nein", null)
                    .setPositiveButton("Ja") { _, _ ->
                        val msg = buildFinishValidationMessage()
                        if (msg != null) {
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.btb_validate_title))
                                .setMessage(msg)
                                .setPositiveButton(getString(R.string.btb_validate_ok), null)
                                .show()
                            return@setPositiveButton
                        }
                        pdfErstellen(setBaseline = true, closeAfterSuccess = true, asDraft = false, deleteDraftOnFinal = true)
                    }
                    .show()
            }
        }


    private fun pdfErstellen(setBaseline: Boolean = false, closeAfterSuccess: Boolean = false, asDraft: Boolean = false, deleteDraftOnFinal: Boolean = false) {
            lifecycleScope.launch {
            val ctx = resolveAktuelleBaustelle()
            if (ctx == null) {
                Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            if (istAusgewaehlterBtbGesperrt(ctx)) {
                zeigeBtbGesperrtHinweis()
                return@launch
            }
            // 3B: PDF enthält nur markierte Fotos.
            // Wenn noch keine Auswahl existiert, soll der User erst über die 📷-Buttons markieren.
            val existing = withContext(Dispatchers.IO) {
                BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
            }

            val entryDraft = buildEntry(ctx)
            val entry = entryDraft.copy(
                maxFotosInPdf = existing?.maxFotosInPdf ?: entryDraft.maxFotosInPdf,
                pdfAuswahl = existing?.pdfAuswahl ?: emptyMap(),
                erstelltAmEpochMs = existing?.erstelltAmEpochMs ?: entryDraft.erstelltAmEpochMs,
                geaendertAmEpochMs = System.currentTimeMillis()
            )

            // Speichern, damit PDF konsistent ist
            withContext(Dispatchers.IO) {
                BautagebuchSpeicher.speichern(requireContext(), entry)
            }

            cachedStatusBaustelleId = entry.baustelleId
                lastSavedEpochMs = entry.geaendertAmEpochMs

            // Tagesabschluss: Baseline setzen (ab dann gelten spätere Änderungen als "GEÄNDERT")
            if (setBaseline) {
                val created = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.baselineSetzenWennNichtVorhanden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
                if (created) {
                    Snackbar.make(requireView(), "Tagesabschluss aktiviert – Änderungen ab jetzt werden markiert", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), "Tagesabschluss ist bereits aktiv", Snackbar.LENGTH_SHORT).show()
                }
            }

            // Baseline-Status aktualisieren (für Pill + Dropdown)
            val bi = withContext(Dispatchers.IO) {
                BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
            }
            baselineExists = bi.exists
            changedSinceBaseline = bi.changedSinceBaseline
            baselineLastModifiedEpochMs = bi.baselineLastModifiedEpochMs
            updateDateTimeButton()

            val fotosInShift = withContext(Dispatchers.IO) {
                try {
                    val all = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(entry.baustelleId)
                    val (from, to) = shiftWindowEpochMs(selectedDate, entry.startUhrzeit, entry.endeUhrzeit)
                    filterFotosForBtbStart(
                        all.filter { it.erstelltAmEpochMs in from until to },
                        entry.erstelltAmEpochMs
                    )
                } catch (_: Exception) {
                    emptyList()
                }
            }

            // 3B: PDF soll immer erzeugbar sein.
            // Wenn Fotos existieren, aber noch nichts ausgewählt wurde, erstellen wir das PDF ohne Foto-Anhang
            // und informieren nur kurz.
            if (fotosInShift.isNotEmpty() && entry.pdfAuswahl.isEmpty()) {
                Snackbar.make(requireView(), "Keine Fotos ausgewählt – PDF wird ohne Fotos erstellt", Snackbar.LENGTH_SHORT).show()
            }

            val pdfFile = try {
                withContext(Dispatchers.IO) {
                    BautagebuchPdf(
                        context = requireContext(),
                        fotoRepository = ServiceLocator.fotoRepository
                    ).erstellenPdf(entry, asDraft = asDraft)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(requireView(), "PDF konnte nicht erstellt werden", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            if (setBaseline && deleteDraftOnFinal) {
                withContext(Dispatchers.IO) {
                    BautagebuchPdf(
                        context = requireContext(),
                        fotoRepository = ServiceLocator.fotoRepository
                    ).deleteEntwurfPdf(entry)
                }
            }

            val hostView = view ?: activity?.findViewById(android.R.id.content)
            val finalMessage = if (!asDraft && closeAfterSuccess) "PDF Abschluss erstellt" else "PDF erstellt: ${pdfFile.name}"
            if (hostView != null) {
                if (asDraft) {
                    Snackbar.make(hostView, finalMessage, Snackbar.LENGTH_LONG)
                        .setAction("Öffnen") {
                            val ctxOpen = context ?: activity ?: return@setAction
                            BautagebuchPdf(ctxOpen, ServiceLocator.fotoRepository).openPdf(pdfFile)
                        }
                        .show()
                } else {
                    Snackbar.make(hostView, finalMessage, Snackbar.LENGTH_LONG).show()
                }
            }

            if (closeAfterSuccess) {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
    }
        }

        /**
         * MaterialAutoCompleteTextView verliert nach setText()/Reset manchmal den Adapter.
         * Wir binden daher die Adapter bei Bedarf erneut und stellen sicher, dass die Views klickbar sind.
         */
        private fun ensureWetterDropdownsReady() {
            val ctx = requireContext()
            val bewAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, bewOptions)
            val niedAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, niedOptions)

            fun bind(ac: MaterialAutoCompleteTextView, adapter: ArrayAdapter<String>) {
                ac.setAdapter(adapter)
                ac.threshold = 0
                ac.isEnabled = true
                ac.isClickable = true
                ac.isFocusable = true
                ac.isFocusableInTouchMode = true
            }

            bind(acBew1, bewAdapter)
            bind(acBew2, bewAdapter)
            bind(acBew3, bewAdapter)
            bind(acNied1, niedAdapter)
            bind(acNied2, niedAdapter)
            bind(acNied3, niedAdapter)
        }

        private fun setupWetterDropdowns() {
            // Adapter einmalig binden. Bei Reset/Manual wird über ensureWetterDropdownsReady() erneut gebunden,
            // weil MaterialAutoCompleteTextView nach setText()/Rebuild gelegentlich den Adapter "vergisst".
            ensureWetterDropdownsReady()

            // Dropdown zuverlässig öffnen (Click/Touch/Focus), damit "Manuelle Eingabe" immer Auswahl anbietet.
            fun hookDropdown(ac: MaterialAutoCompleteTextView) {
                ac.setOnClickListener { ac.showDropDown() }
                ac.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) ac.showDropDown() }
                ac.setOnTouchListener { v, ev ->
                    if (ev.action == android.view.MotionEvent.ACTION_UP) {
                        // Accessibility/Lint: bei Touch-Clicks performClick() aufrufen
                        v.performClick()
                        ac.showDropDown()
                    }
                    false
                }
            }
            hookDropdown(acBew1)
            hookDropdown(acBew2)
            hookDropdown(acBew3)
            hookDropdown(acNied1)
            hookDropdown(acNied2)
            hookDropdown(acNied3)

            btnWetterTime1.setOnClickListener { showTimePicker(btnWetterTime1) }
            btnWetterTime2.setOnClickListener { showTimePicker(btnWetterTime2) }
            btnWetterTime3.setOnClickListener { showTimePicker(btnWetterTime3) }

            // UX: "Manuelle Eingabe" öffnet/aktiviert die Eingabe. Zeiten automatisch gibt's per Long-Press.
            btnWetterAuto.setOnClickListener { enterManualWeatherMode() }
            btnWetterAuto.setOnLongClickListener {
                autoWetterZeiten()
                true
            }
            btnWetterReset.setOnClickListener { resetWetter() }

            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    updateWeatherPreview()
                    if (!wetterApplying) {
                        // Manuelle Änderung -> Auto-precip-Werte verwerfen
                        wetterMm1 = null
                        wetterMm2 = null
                        wetterMm3 = null
                        scheduleWetterAutosave()
                    }
                }
            }
            etTemp1.addTextChangedListener(watcher)
            etTemp2.addTextChangedListener(watcher)
            etTemp3.addTextChangedListener(watcher)
            acBew1.addTextChangedListener(watcher)
            acBew2.addTextChangedListener(watcher)
            acBew3.addTextChangedListener(watcher)
            acNied1.addTextChangedListener(watcher)
            acNied2.addTextChangedListener(watcher)
            acNied3.addTextChangedListener(watcher)

            autoWetterZeiten()

            // Standardwerte
            acBew1.setText(DEFAULT_BEWOELKUNG, false)
            acBew2.setText(DEFAULT_BEWOELKUNG, false)
            acBew3.setText(DEFAULT_BEWOELKUNG, false)
            acNied1.setText(DEFAULT_NIEDERSCHLAG, false)
            acNied2.setText(DEFAULT_NIEDERSCHLAG, false)
            acNied3.setText(DEFAULT_NIEDERSCHLAG, false)
            updateWeatherPreview()
        }

        private fun showTimePicker(btn: MaterialButton) {
            val txt = btn.text?.toString().orEmpty()
            val parts = txt.split(":")
            val h0 = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val m0 = parts.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(requireContext(), { _, h, m ->
                val rounded = roundUpToQuarter(LocalTime.of(h, m))
                btn.text = String.format(java.util.Locale.ROOT, "%02d:%02d", rounded.hour, rounded.minute)
                updateWeatherPreview()
            }, h0, m0, true).show()
        }

        private fun roundUpToQuarter(t: LocalTime): LocalTime {
            val total = t.hour * 60 + t.minute
            val rounded = ((total + 14) / 15) * 15
            val mm = (rounded % (24 * 60) + (24 * 60)) % (24 * 60)
            return LocalTime.of(mm / 60, mm % 60)
        }

        private fun roundUpToQuarterMinutes(min: Int): Int {
            return ((min + 14) / 15) * 15
        }

        private fun autoWetterZeiten() {
            // 3 Zeitpunkte gleichmäßig innerhalb Start/Ende, fallback 08:00/12:00/16:00
            val start = (shiftStart ?: etStart.text?.toString())?.trim().orEmpty()
            val ende = (shiftEnd ?: etEnde.text?.toString())?.trim().orEmpty()

            fun parse(s: String): Int? {
                val p = s.split(":")
                val h = p.getOrNull(0)?.toIntOrNull() ?: return null
                val m = p.getOrNull(1)?.toIntOrNull() ?: return null
                return h * 60 + m
            }
            val sMin = parse(start)
            val eMinRaw = parse(ende)
            if (sMin == null || eMinRaw == null) {
                btnWetterTime1.text = "08:00"
                btnWetterTime2.text = "12:00"
                btnWetterTime3.text = "16:00"
                updateWeatherPreview()
                return
            }
            val eMin = if (eMinRaw < sMin) eMinRaw + 24 * 60 else eMinRaw
            val span = (eMin - sMin).coerceAtLeast(1)
            // Auf 15 Minuten aufrunden (gemäß Arbeitszeit)
            val t1 = roundUpToQuarterMinutes(sMin + span / 6)
            val t2 = roundUpToQuarterMinutes(sMin + span / 2)
            val t3 = roundUpToQuarterMinutes(sMin + (span * 5) / 6)

            fun fmt(min: Int): String {
                val mm = (min % (24 * 60) + (24 * 60)) % (24 * 60)
                val h = mm / 60
                val m = mm % 60
                return String.format("%02d:%02d", h, m)
            }
            btnWetterTime1.text = fmt(t1)
            btnWetterTime2.text = fmt(t2)
            btnWetterTime3.text = fmt(t3)
            updateWeatherPreview()

        }

        private fun resetWetter() {
            wetterApplying = true
            try {
                wetterMm1 = null
                wetterMm2 = null
                wetterMm3 = null
                autoWetterZeiten()
                etTemp1.setText("")
                etTemp2.setText("")
                etTemp3.setText("")
                ensureWetterDropdownsReady()
                acBew1.setText(DEFAULT_BEWOELKUNG, false)
                acBew2.setText(DEFAULT_BEWOELKUNG, false)
                acBew3.setText(DEFAULT_BEWOELKUNG, false)
                acNied1.setText(DEFAULT_NIEDERSCHLAG, false)
                acNied2.setText(DEFAULT_NIEDERSCHLAG, false)
                acNied3.setText(DEFAULT_NIEDERSCHLAG, false)
            } finally {
                wetterApplying = false
            }
            updateWeatherPreview()
            scheduleWetterAutosave(force = true)
        }

        private fun enterManualWeatherMode() {
            // Container aufklappen, falls noch zu.
            if (wetterContainer.visibility != View.VISIBLE) {
                toggleWeather()
            }
            // Nach Reset/Toggle kann der Adapter in MaterialAutoCompleteTextView "verloren" gehen.
            // Daher vor manueller Eingabe immer sicherstellen, dass die Dropdowns vollständig gebunden sind.
            ensureWetterDropdownsReady()
            // Fokus in die erste Eingabe setzen und Dropdown öffnen
            etTemp1.requestFocus()
            view?.post { runCatching { acBew1.showDropDown() } }
            Snackbar.make(requireView(), "Manuelle Eingabe: Werte auswählen oder Temperatur eintragen", Snackbar.LENGTH_SHORT).show()
        }

        private fun collectWeatherSlots(): List<WetterSlot> {
            fun slot(btn: MaterialButton, temp: TextInputEditText, bew: MaterialAutoCompleteTextView, nied: MaterialAutoCompleteTextView): WetterSlot {
                val u = btn.text?.toString()?.trim().orEmpty()
                val t = temp.text?.toString()?.trim()?.toIntOrNull()
                val bRaw = bew.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                val nRaw = nied.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }

                // Bewölkung/Niederschlag immer persistieren (PDF soll die Werte sehen)
                val mm = when (btn) {
                    btnWetterTime1 -> wetterMm1
                    btnWetterTime2 -> wetterMm2
                    btnWetterTime3 -> wetterMm3
                    else -> null
                }
                return WetterSlot(uhrzeit = u, tempC = t, bewoelkung = bRaw, niederschlag = nRaw, niederschlagMm = mm)
            }
            return listOf(
                slot(btnWetterTime1, etTemp1, acBew1, acNied1),
                slot(btnWetterTime2, etTemp2, acBew2, acNied2),
                slot(btnWetterTime3, etTemp3, acBew3, acNied3)
            )
        }

        private fun applyWeatherSlots(slots: List<WetterSlot>) {
            fun apply(i: Int, btn: MaterialButton, temp: TextInputEditText, bew: MaterialAutoCompleteTextView, nied: MaterialAutoCompleteTextView) {
                val s = slots.getOrNull(i) ?: return
                // mm-Wert übernehmen (falls vorhanden)
                when (i) {
                    0 -> wetterMm1 = s.niederschlagMm
                    1 -> wetterMm2 = s.niederschlagMm
                    2 -> wetterMm3 = s.niederschlagMm
                }
                btn.text = s.uhrzeit
                temp.setText(s.tempC?.toString().orEmpty())
                bew.setText(s.bewoelkung ?: DEFAULT_BEWOELKUNG, false)
                nied.setText(s.niederschlag ?: DEFAULT_NIEDERSCHLAG, false)
            }
            wetterApplying = true
            try {
                ensureWetterDropdownsReady()
                apply(0, btnWetterTime1, etTemp1, acBew1, acNied1)
                apply(1, btnWetterTime2, etTemp2, acBew2, acNied2)
                apply(2, btnWetterTime3, etTemp3, acBew3, acNied3)
            } finally {
                wetterApplying = false
            }
            updateWeatherPreview()
        }

        private fun spansMidnight(start: String?, end: String?): Boolean {
            val s = parseMinutes(start)
            val e = parseMinutes(end)
            if (s == null || e == null) return false
            return e <= s
        }

        private fun dateRangeText(): String {
            val hasShift = !shiftStart.isNullOrBlank() && !shiftEnd.isNullOrBlank()
            if (!hasShift) return selectedDate.format(deDateFmt)

            val endDate = if (spansMidnight(shiftStart, shiftEnd)) selectedDate.plusDays(1) else selectedDate
            if (endDate == selectedDate) return selectedDate.format(deDateFmt)

            // gleiche Monats-/Jahresanzeige: 25/26.04.2026
            return if (endDate.month == selectedDate.month && endDate.year == selectedDate.year) {
                "${selectedDate.dayOfMonth}/${endDate.dayOfMonth}." + selectedDate.format(DateTimeFormatter.ofPattern("MM.yyyy"))
            } else {
                "${selectedDate.format(deDateFmt)}/${endDate.format(deDateFmt)}"
            }
        }

        private fun parseMinutes(t: String?): Int? {
            val s = t?.trim().orEmpty()
            val p = s.split(":")
            val h = p.getOrNull(0)?.toIntOrNull() ?: return null
            val m = p.getOrNull(1)?.toIntOrNull() ?: return null
            return h * 60 + m
        }

        private fun shiftWindowEpochMs(datum: LocalDate, start: String?, ende: String?): Pair<Long, Long> {
            val zone = ZoneId.systemDefault()
            val dayStart = datum.atStartOfDay(zone).toInstant().toEpochMilli()
            val nextDayStart = datum.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val sMin = parseMinutes(start)
            val eMin = parseMinutes(ende)
            if (sMin == null || eMin == null) return Pair(dayStart, nextDayStart)

            val from = dayStart + sMin * 60L * 1000L
            val to = if (eMin >= sMin) dayStart + eMin * 60L * 1000L else nextDayStart + eMin * 60L * 1000L
            return Pair(from, to)
        }

        private fun slotDateForTime(time: String): LocalDate {
            val tMin = parseMinutes(time) ?: return selectedDate
            val sMin = parseMinutes(shiftStart)
            val eMin = parseMinutes(shiftEnd)
            val crosses = spansMidnight(shiftStart, shiftEnd)
            return if (crosses && sMin != null && eMin != null && tMin < sMin) selectedDate.plusDays(1) else selectedDate
        }

        private fun wetterAutomatischFuellen(force: Boolean = false) {
            // Schutz gegen Mehrfachklick / Header-Toggle
            if (wetterFetchInProgress) return

            val now = System.currentTimeMillis()
            if (!force && (now - lastWetterFetchMs) < WEATHER_AUTO_THROTTLE_MS) {
                return
            }

            val requestBtbId = loadedBtbId
            val requestDate = selectedDate
            val requestStart = shiftStart
            val requestEnd = shiftEnd
            val requestSlots = collectWeatherSlots()
            val requestDirty = wetterDirtySinceMs
            wetterFetchInProgress = true
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val ctx = resolveAktuelleBaustelle()
                    if (ctx == null) {
                        Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                        return@launch
                    }

                    val (lat, lon) = withContext(Dispatchers.IO) {
                        resolveWetterLocation(ctx)
                    } ?: run {
                        Snackbar.make(requireView(), "Kein Standort verfügbar (Foto/Handy)", Snackbar.LENGTH_LONG).show()
                        return@launch
                    }

                    val weatherOrt = withContext(Dispatchers.IO) { resolveOrtName(lat, lon) }
                    val startDate = requestDate
                    val endDate = if (spansMidnight(requestStart, requestEnd)) requestDate.plusDays(1) else requestDate

                    val hourly = withContext(Dispatchers.IO) {
                        OpenMeteoClient.fetchHourly(lat, lon, startDate, endDate)
                    } ?: run {
                        Snackbar.make(requireView(), "Wetterdaten konnten nicht geladen werden", Snackbar.LENGTH_LONG).show()
                        return@launch
                    }

                    val currentSite = resolveAktuelleBaustelle()
                    if (currentSite?.id != ctx.id || loadedBtbId != requestBtbId || selectedDate != requestDate ||
                        shiftStart != requestStart || shiftEnd != requestEnd ||
                        collectWeatherSlots() != requestSlots || wetterDirtySinceMs != requestDirty) {
                        Snackbar.make(requireView(), "BTB oder Wetter geändert – Wetter bitte erneut laden", Snackbar.LENGTH_LONG).show()
                        return@launch
                    }
                    // Commit provenance together with the accepted response, never before a failed request.
                    lastWetterLat = lat
                    lastWetterLon = lon
                    lastWetterSource = WeatherSource.AUTO
                    lastWetterStation = null
                    lastWetterOrt = weatherOrt

                    fun fill(idx: Int, btn: MaterialButton, temp: TextInputEditText, bew: MaterialAutoCompleteTextView, nied: MaterialAutoCompleteTextView) {
                        val raw = btn.text?.toString().orEmpty()
                        // Akzeptiert z.B. "7:00", "07:00" oder "07:00 Uhr"
                        val m = Regex("""(\d{1,2}):(\d{2})""").find(raw) ?: return
                        val hh = m.groupValues[1].toIntOrNull() ?: return
                        val mm = m.groupValues[2].toIntOrNull() ?: return
                        val time = LocalTime.of(hh, mm)
                        val timeStr = String.format(java.util.Locale.ROOT, "%02d:%02d", hh, mm)
                        val dateForTime = slotDateForTime(timeStr)
                        val target = LocalDateTime.of(dateForTime, time)
                        val h = hourly.closestTo(target) ?: return

                        val mmVal = h.precipMm.takeIf { it.isFinite() && it >= 0.0 }
                        when (idx) {
                            0 -> wetterMm1 = mmVal
                            1 -> wetterMm2 = mmVal
                            2 -> wetterMm3 = mmVal
                        }

                        temp.setText(kotlin.math.round(h.tempC).toInt().toString())
                        bew.setText(mapCloudToBew(h.cloudCoverPct), false)
                        nied.setText(mapCodeToNied(h.weatherCode, h.precipMm), false)
                    }

                    wetterApplying = true
                    try {
                        fill(0, btnWetterTime1, etTemp1, acBew1, acNied1)
                        fill(1, btnWetterTime2, etTemp2, acBew2, acNied2)
                        fill(2, btnWetterTime3, etTemp3, acBew3, acNied3)
                    } finally {
                        wetterApplying = false
                    }

                        lastWetterFetchMs = System.currentTimeMillis()
                        updateWeatherPreview()
                    scheduleWetterAutosave(force = true)
                    Snackbar.make(requireView(), "Wetter automatisch gefüllt", Snackbar.LENGTH_SHORT).show()
                } finally {
                    wetterFetchInProgress = false
                }
            }
        }

        private suspend fun resolveWetterLocation(ctx: BaustelleKontext): Pair<Double, Double>? {
            // 1) Foto (in der Schicht)
            runCatching {
                val all = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(ctx.id)
                val (from, to) = shiftWindowEpochMs(selectedDate, shiftStart, shiftEnd)
                val currentEntry = BautagebuchSpeicher.laden(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                val inWindow = filterFotosForBtbStart(
                    all.filter { it.erstelltAmEpochMs in from until to },
                    currentBtbStartEpochMs ?: currentEntry?.erstelltAmEpochMs
                )
                if (inWindow.isNotEmpty()) {
                    val f = inWindow.last()
                    val lat = f.gpsLatEffektiv
                    val lon = f.gpsLonEffektiv
                    if (lat != null && lon != null) return Pair(lat, lon)
                }
            }

            // 2) Handy (letzte bekannte Position)
            if (StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
                val loc: Location? = withTimeoutOrNull(2500L) {
                    StandortLiveQuelle.standortFlow(requireContext()).first()
                }
                if (loc != null) return Pair(loc.latitude, loc.longitude)
            }

            // 3) Baustelle (aktuell keine GPS-Felder im Datenmodell)
            return null
        }

        private fun resolveOrtName(lat: Double, lon: Double): String? {
            return runCatching {
                val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                val list = geocoder.getFromLocation(lat, lon, 1)
                val a = list?.firstOrNull() ?: return null
                (a.locality
                    ?: a.subAdminArea
                    ?: a.adminArea
                    ?: a.featureName)?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        // ---------------------------
        // Personal + Geräte/Maschinen (Dropdown-Style B)
        // ---------------------------
        private fun setupPmUi(view: View) {
            tvPersonalPreview = view.requireViewByIdCompat(R.id.tvPersonalPreview)
            tvMaschinenPreview = view.requireViewByIdCompat(R.id.tvMaschinenPreview)
            personalSection = view.requireViewByIdCompat(R.id.personalSection)
            maschinenSection = view.requireViewByIdCompat(R.id.maschinenSection)
            personalBlocksContainer = view.requireViewByIdCompat(R.id.personalBlocksContainer)
            maschinenBlocksContainer = view.requireViewByIdCompat(R.id.maschinenBlocksContainer)
            btnAddPersonalFirma = view.requireViewByIdCompat(R.id.btnAddPersonalFirma)
            btnAddMaschinenFirma = view.requireViewByIdCompat(R.id.btnAddMaschinenFirma)
            btnPersonalVortag = view.requireViewByIdCompat(R.id.btnPersonalVortag)
            btnMaschinenVortag = view.requireViewByIdCompat(R.id.btnMaschinenVortag)
            btnPersonalSave = view.requireViewByIdCompat(R.id.btnPersonalSave)
            btnMaschinenSave = view.requireViewByIdCompat(R.id.btnMaschinenSave)

            tvPersonalPreview.setOnClickListener { togglePm(PmSection.PERSONAL) }
            tvMaschinenPreview.setOnClickListener { togglePm(PmSection.MASCHINEN) }

            // Firmen können in Personal und Maschinen unterschiedlich sein.
            btnAddPersonalFirma.setOnClickListener { addFirmaBlock(PmSection.PERSONAL, firmaId = java.util.UUID.randomUUID().toString(), prefillFirma = null) }
            btnAddMaschinenFirma.setOnClickListener { addFirmaBlock(PmSection.MASCHINEN, firmaId = java.util.UUID.randomUUID().toString(), prefillFirma = null) }

            // Vortag übernehmen: pro Sektion getrennt (Personal != Maschinen möglich)
            btnPersonalVortag.setOnClickListener { uebernehmenPmVomVortag(PmSection.PERSONAL) }
            btnMaschinenVortag.setOnClickListener { uebernehmenPmVomVortag(PmSection.MASCHINEN) }

            // Speichern schließt die aktuelle Sektion (Autosave passiert ohnehin, aber hier "fertig")
            btnPersonalSave.setOnClickListener {
                schedulePmAutosave(force = true)
                if (personalSection.isVisible) togglePm(PmSection.PERSONAL)
            }
            btnMaschinenSave.setOnClickListener {
                schedulePmAutosave(force = true)
                if (maschinenSection.isVisible) togglePm(PmSection.MASCHINEN)
            }

            updatePmArrow(tvPersonalPreview, expanded = false)
            updatePmArrow(tvMaschinenPreview, expanded = false)
            updatePmPreviews()
        }

        // (Phase 3a alt) addFirmaPair entfernt – Sektionen sind unabhängig.

        private fun togglePm(section: PmSection) {
            val showPersonal = section == PmSection.PERSONAL && personalSection.visibility != View.VISIBLE
            val showMaschinen = section == PmSection.MASCHINEN && maschinenSection.visibility != View.VISIBLE

            personalSection.visibility = if (showPersonal) View.VISIBLE else View.GONE
            maschinenSection.visibility = if (showMaschinen) View.VISIBLE else View.GONE

            updatePmArrow(tvPersonalPreview, expanded = personalSection.visibility == View.VISIBLE)
            updatePmArrow(tvMaschinenPreview, expanded = maschinenSection.visibility == View.VISIBLE)
        }

        private fun updatePmArrow(tv: android.widget.TextView, expanded: Boolean) {
            val icon = if (expanded) R.drawable.ic_arrow_drop_up_24 else R.drawable.ic_arrow_drop_down_24
            tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0)
        }

        private fun updatePmPreviews() {
            fun hasData(list: List<FirmaBlockUi>): Boolean =
                list.any { b ->
                    val firmaOk = b.acFirma.text?.toString().orEmpty().trim().isNotBlank()
                    val timeOk = b.etVon.text?.toString().orEmpty().trim().isNotBlank() ||
                        b.etBis.text?.toString().orEmpty().trim().isNotBlank()
                    val rowOk = b.rows.any { r ->
                        r.acBez.text?.toString().orEmpty().trim().isNotBlank() ||
                            r.acAnzahl.text?.toString().orEmpty().trim().isNotBlank()
                    }
                    // Auto-Linked Placeholder (von Personal -> Maschinen): zählt erst als "Daten",
                    // wenn wirklich Maschinen-Zeit oder Zeilen befüllt wurden.
                    if (b.autoLinked && !timeOk && !rowOk) return@any false

                    firmaOk || timeOk || rowOk
                }

            setPillStatus(tvPersonalPreview, ok = hasData(personalUis))
            setPillStatus(tvMaschinenPreview, ok = hasData(maschinenUis))
        }

        private fun setPillStatus(tv: android.widget.TextView, ok: Boolean) {
            tv.text = if (ok) "OK" else "Keine Daten"
            ViewCompat.setBackgroundTintList(
                tv,
                ColorStateList.valueOf(Color.parseColor(if (ok) "#DFF3E0" else "#FFCDD2"))
            )
        }

        private fun clearBlocks(section: PmSection) {
            val (container, uis) = if (section == PmSection.PERSONAL) {
                personalBlocksContainer to personalUis
            } else {
                maschinenBlocksContainer to maschinenUis
            }
            container.removeAllViews()
            uis.clear()
        }

        private fun passeFirmenBerichtszeitenAn(altVon: String?, altBis: String?, von: String?, bis: String?) {
            val wasApplying = pmApplying
            pmApplying = true
            try {
                (personalUis + maschinenUis).filterNot { it.autoLinked }.forEach { ui ->
                    val next = FirmenZeitbindung.berichtGeaendert(
                        FirmenZeitbindung.Zeiten(ui.etVon.text?.toString(), ui.etBis.text?.toString(), ui.zeitbindung),
                        altVon, altBis, von, bis
                    )
                    ui.zeitbindung = next.bindung
                    ui.etVon.setText(next.von.orEmpty())
                    ui.etBis.setText(next.bis.orEmpty())
                }
            } finally {
                pmApplying = wasApplying
            }
            updatePmPreviews()
        }

        private fun addFirmaBlock(
            section: PmSection,
            firmaId: String,
            prefillFirma: String?,
            prefillVon: String? = null,
            prefillBis: String? = null,
            autoLinked: Boolean = false,
            zeitbindung: String? = null
        ) {
            val container = if (section == PmSection.PERSONAL) personalBlocksContainer else maschinenBlocksContainer
            val uis = if (section == PmSection.PERSONAL) personalUis else maschinenUis

            val root = layoutInflater.inflate(R.layout.item_firma_block, container, false)

            val acFirma: MaterialAutoCompleteTextView = root.requireViewByIdCompat(R.id.acFirma)
            val etVon: com.google.android.material.textfield.TextInputEditText = root.requireViewByIdCompat(R.id.acVon)
            val etBis: com.google.android.material.textfield.TextInputEditText = root.requireViewByIdCompat(R.id.acBis)
            val rowsContainer: android.widget.LinearLayout = root.requireViewByIdCompat(R.id.rowsContainer)
            val btnAddRow: MaterialButton = root.requireViewByIdCompat(R.id.btnAddRow)
            val btnRemoveFirma: android.widget.ImageButton = root.requireViewByIdCompat(R.id.btnRemoveFirma)

            val ui = FirmaBlockUi(
                root = root,
                firmaId = firmaId,
                autoLinked = autoLinked,
                zeitbindung = zeitbindung ?: if (!pmApplying) FirmenZeitbindung.REPORT else null,
                acFirma = acFirma,
                etVon = etVon,
                etBis = etBis,
                rowsContainer = rowsContainer,
                rows = mutableListOf(),
                btnAddRow = btnAddRow,
                btnRemoveFirma = btnRemoveFirma
            )

            refreshFirmaAdapter(acFirma)

            // Zeit je Firma: von / bis (15-Minuten Raster)
            val times = buildTimes15()

            // UX: keine Tastatur, Tap öffnet Auswahl
            fun setupTimeField(et: com.google.android.material.textfield.TextInputEditText) {
                et.isCursorVisible = false
                et.keyListener = null
                et.isFocusable = false
                et.isFocusableInTouchMode = false
                et.isClickable = true
                et.setOnLongClickListener { true }
            }
            setupTimeField(etVon)
            setupTimeField(etBis)

            val suggestedVon = suggestedNowQuarter()

            // Defaults beim Anlegen
            val initVon = if (pmApplying) prefillVon else prefillVon ?: shiftStart
            val initBis = if (pmApplying) prefillBis else prefillBis ?: shiftEnd
            etVon.setText(if (autoLinked) "" else initVon.orEmpty())
            etBis.setText(if (autoLinked) "" else initBis.orEmpty())

            etVon.setOnClickListener {
                showPmClockPicker(etVon, suggestedVon) {
                    autoFillBisIfBlank(etVon, etBis, 10L)
                }
            }

            etBis.setOnClickListener {
                autoFillBisIfBlank(etVon, etBis, 10L)
                val suggestedBis = plusHours(etVon.text?.toString().orEmpty(), 10L) ?: suggestedVon
                showPmClockPicker(etBis, suggestedBis)
            }

            prefillFirma?.let { acFirma.setText(it, false) }

            addPairRow(ui, section, prefillAnzahl = null, prefillBez = null)

            btnAddRow.setOnClickListener { addPairRow(ui, section, prefillAnzahl = null, prefillBez = null) }

            btnRemoveFirma.setOnClickListener {
                removeFirmaInSection(section, ui.firmaId)
            }

            fun onFirmaChanged() {
                // sobald Nutzer irgendwas anfasst, ist es kein Placeholder mehr
                if (ui.autoLinked && section == PmSection.MASCHINEN && !pmApplying && !pmSyncing) {
                    ui.autoLinked = false
                    val wasApplying = pmApplying
                    pmApplying = true
                    try {
                        ui.etVon.setText(shiftStart.orEmpty())
                        ui.etBis.setText(shiftEnd.orEmpty())
                        ui.zeitbindung = FirmenZeitbindung.REPORT
                    } finally {
                        pmApplying = wasApplying
                    }
                }
                // Sync-Vorgabe: Personal-Firma automatisch auch bei Maschinen einfügen
                if (!pmApplying && !pmSyncing && section == PmSection.PERSONAL) {
                    syncPersonalFirmaToMaschinen(ui)
                }
                updatePmPreviews()
                schedulePmAutosave()
            }
            acFirma.setOnItemClickListener { _, _, _, _ -> onFirmaChanged() }
            acFirma.addTextChangedListener(SimpleTextWatcher { onFirmaChanged() })
            fun onVonChanged() {
                if (pmApplying || pmSyncing) return
                // Auto-Bis (+10h) nur wenn Bis noch leer ist
                autoFillBisIfBlank(etVon, etBis, 10L)
                ui.zeitbindung = FirmenZeitbindung.manuellGewaehlt(etVon.text?.toString(), etBis.text?.toString(), shiftStart, shiftEnd)
                if (ui.autoLinked && (section == PmSection.MASCHINEN)) ui.autoLinked = false
                schedulePmAutosave()
            }
            fun onBisChanged() {
                if (pmApplying || pmSyncing) return
                ui.zeitbindung = FirmenZeitbindung.manuellGewaehlt(etVon.text?.toString(), etBis.text?.toString(), shiftStart, shiftEnd)
                if (ui.autoLinked && (section == PmSection.MASCHINEN)) ui.autoLinked = false
                schedulePmAutosave()
            }
            etVon.addTextChangedListener(SimpleTextWatcher { onVonChanged() })
            etBis.addTextChangedListener(SimpleTextWatcher { onBisChanged() })

            container.addView(root)
            uis.add(ui)
            updatePmPreviews()
            schedulePmAutosave()
        }

        private fun addPairRow(block: FirmaBlockUi, section: PmSection, prefillAnzahl: String?, prefillBez: String?) {
            val rowView = layoutInflater.inflate(R.layout.item_firma_row, block.rowsContainer, false)
            val acAnzahl: MaterialAutoCompleteTextView = rowView.requireViewByIdCompat(R.id.acAnzahl)
            val acBez: MaterialAutoCompleteTextView = rowView.requireViewByIdCompat(R.id.acBez)
            val btnRemove: android.widget.ImageButton = rowView.requireViewByIdCompat(R.id.btnRemoveRow)

            refreshAnzahlAdapter(acAnzahl)
            refreshBezAdapter(acBez, section)

            prefillAnzahl?.let { acAnzahl.setText(it, false) }
            prefillBez?.let { acBez.setText(it, false) }

            val ui = PairRowUi(rowView, acAnzahl, acBez, btnRemove)
            block.rows.add(ui)
            block.rowsContainer.addView(rowView)

            fun onChanged() {
                // sobald Nutzer Zeilen befüllt, ist es kein Placeholder mehr
                if (pmApplying || pmSyncing) return
                if (block.autoLinked && (section == PmSection.MASCHINEN)) {
                    val hasAny = block.rows.any { r ->
                        r.acBez.text?.toString().orEmpty().trim().isNotBlank() ||
                            r.acAnzahl.text?.toString().orEmpty().trim().isNotBlank()
                    }
                    if (hasAny) {
                        block.autoLinked = false
                        val wasApplying = pmApplying
                        pmApplying = true
                        try {
                            block.etVon.setText(shiftStart.orEmpty())
                            block.etBis.setText(shiftEnd.orEmpty())
                            block.zeitbindung = FirmenZeitbindung.REPORT
                        } finally {
                            pmApplying = wasApplying
                        }
                    }
                }
                updatePmPreviews()
                schedulePmAutosave()
            }

            acAnzahl.setOnItemClickListener { _, _, _, _ -> onChanged() }
            acBez.setOnItemClickListener { _, _, _, _ -> onChanged() }
            acAnzahl.addTextChangedListener(SimpleTextWatcher { onChanged() })
            acBez.addTextChangedListener(SimpleTextWatcher { onChanged() })

            btnRemove.setOnClickListener {
                val idx = block.rows.indexOf(ui)
                if (idx >= 0) {
                    block.rows.removeAt(idx)
                    block.rowsContainer.removeView(ui.root)
                    if (block.rows.isEmpty()) {
                        addPairRow(block, section, prefillAnzahl = null, prefillBez = null)
                    } else {
                        onChanged()
                    }
                }
            }
        }

        private fun refreshFirmaAdapter(ac: MaterialAutoCompleteTextView) {
            val items = firmHistory.toList()
            ac.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items))
        }

        private fun refreshAnzahlAdapter(ac: MaterialAutoCompleteTextView) {
            val common = listOf("1","2","3","4","5","6","7","8","9","10")
            ac.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, common))
        }

        private fun refreshBezAdapter(ac: MaterialAutoCompleteTextView, section: PmSection) {
            val items = if (section == PmSection.PERSONAL) roleHistory.toList() else machineHistory.toList()
            ac.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items))
        }

        private fun refreshAllAdapters() {
            personalUis.forEach { b ->
                refreshFirmaAdapter(b.acFirma)
                b.rows.forEach { r ->
                    refreshAnzahlAdapter(r.acAnzahl)
                    refreshBezAdapter(r.acBez, PmSection.PERSONAL)
                }
            }
            maschinenUis.forEach { b ->
                refreshFirmaAdapter(b.acFirma)
                b.rows.forEach { r ->
                    refreshAnzahlAdapter(r.acAnzahl)
                    refreshBezAdapter(r.acBez, PmSection.MASCHINEN)
                }
            }
        }

        /** Entfernt alle Firmenblöcke in beiden Sektionen. */
        private fun clearPmAll() {
            clearBlocks(PmSection.PERSONAL)
            clearBlocks(PmSection.MASCHINEN)
            pmFirmenQuellstand = emptyList()
        }

        // Keine Synchronisation zwischen Personal/Maschinen: Firmen können unterschiedlich sein.

        /** Entfernt eine Firma nur in der jeweiligen Sektion (UX-Vorgabe). */
        private fun removeFirmaInSection(section: PmSection, firmaId: String) {
            fun removeFrom(container: ViewGroup, list: MutableList<FirmaBlockUi>) {
                val idx = list.indexOfFirst { it.firmaId == firmaId }
                if (idx >= 0) {
                    val ui = list.removeAt(idx)
                    container.removeView(ui.root)
                }
            }
            if (section == PmSection.PERSONAL) {
                removeFrom(personalBlocksContainer, personalUis)
            } else {
                removeFrom(maschinenBlocksContainer, maschinenUis)
            }
            updatePmPreviews()
            schedulePmAutosave()
        }

        /** Hilfsfunktion: HH:mm + hours (Wrap über Mitternacht erlaubt). */
        private fun plusHours(hhmm: String, hours: Long): String? {
            val t = runCatching { LocalTime.parse(hhmm.trim(), timeFmt) }.getOrNull() ?: return null
            return t.plusHours(hours).format(timeFmt)
        }

        /**
         * Öffnet das Dropdown und scrollt direkt zur aktuellen Auswahl (falls vorhanden).
         * Wenn kein Match gefunden wird, bleibt die Liste oben.
         */
        private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

        private fun suggestedNowQuarter(): String {
            return roundUpToQuarter(java.time.LocalTime.now()).format(timeFmt)
        }

        // Android 200037: Personal and machine times use the same 24h clock picker.
        private fun showPmClockPicker(
            field: com.google.android.material.textfield.TextInputEditText,
            suggestedWhenBlank: String,
            onChosen: (() -> Unit)? = null
        ) {
            val raw = field.text?.toString().orEmpty().trim()
            val initial = runCatching { LocalTime.parse(raw, timeFmt) }.getOrNull()
                ?: runCatching { LocalTime.parse(suggestedWhenBlank, timeFmt) }.getOrNull()
                ?: LocalTime.now()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                val rounded = roundUpToQuarter(LocalTime.of(hour, minute))
                field.setText(rounded.format(timeFmt))
                onChosen?.invoke()
            }, initial.hour, initial.minute, true).show()
        }

        private fun showTimePopup(ac: MaterialAutoCompleteTextView, items: List<String>) {
            val suggested = suggestedNowQuarter()
            showTimePopup(
                anchor = ac,
                setValue = { ac.setText(it, false) },
                currentValue = ac.text?.toString().orEmpty(),
                items = items,
                suggestedWhenBlank = suggested
            )
        }

        /**
         * Öffnet eine kompakte Auswahlliste (ListPopupWindow) für Zeiten.
         * - Kein Pfeil nötig (Tap aufs Feld reicht)
         * - Startet mittig bei: aktuellem Feldwert, sonst bei suggestedWhenBlank
         */
        private fun showTimePopup(
            anchor: android.view.View,
            setValue: (String) -> Unit,
            currentValue: String,
            items: List<String>,
            suggestedWhenBlank: String
        ) {
            val current = currentValue.trim().takeIf { it.isNotBlank() } ?: suggestedWhenBlank
            val idx = items.indexOfFirst { it == current }.takeIf { it >= 0 } ?: 0

            val popup = ListPopupWindow(requireContext()).apply {
                anchorView = anchor
                isModal = true
                // Größe: nicht riesig
                width = maxOf(anchor.width, dpToPx(140))
                height = dpToPx(280) // ~8–10 Zeilen je nach Gerät
                setAdapter(ArrayAdapter(anchor.context, android.R.layout.simple_list_item_1, items))
                setOnItemClickListener { _, _, position, _ ->
                    setValue(items[position])
                    dismiss()
                }
            }

            popup.show()

            popup.listView?.post {
                val lv = popup.listView ?: return@post
                val childH = lv.getChildAt(0)?.height ?: 0
                val offset = kotlin.math.max(0, lv.height / 2 - childH / 2)
                lv.setSelectionFromTop(idx, offset)
            }
        }


        /** Auto-Bis: setzt Bis = Von + hours, aber nur wenn Bis leer ist. */
        private fun autoFillBisIfBlank(etVon: com.google.android.material.textfield.TextInputEditText, etBis: com.google.android.material.textfield.TextInputEditText, hours: Long = 10L) {
            val von = etVon.text?.toString().orEmpty().trim()
            val bis = etBis.text?.toString().orEmpty().trim()
            if (von.isBlank() || bis.isNotBlank()) return
            val suggestion = plusHours(von, hours) ?: return
            etBis.setText(suggestion)
        }



        /**
         * UX-Vorgabe: Wenn in Personal eine Firma gesetzt wird, soll sie automatisch auch bei
         * Geräte/Maschinen verfügbar sein.
         *
         * Umsetzung: Wir legen (falls nicht vorhanden) einen Maschinen-Block mit gleicher firmaId an,
         * markieren ihn als autoLinked Placeholder und persistieren ihn nur, wenn wirklich
         * Maschinen-Daten (Zeit oder Zeilen) befüllt werden.
         */
        private fun syncPersonalFirmaToMaschinen(personalUi: FirmaBlockUi) {
            val name = personalUi.acFirma.text?.toString().orEmpty().trim()
            pmSyncing = true
            try {
                val existing = maschinenUis.firstOrNull { it.firmaId == personalUi.firmaId }

                if (name.isBlank()) {
                    // Wenn Personal wieder leer wird: auto-linked Placeholder entfernen (nur wenn unbenutzt)
                    existing?.let { m ->
                        val hasMachineData =
                            m.etVon.text?.toString().orEmpty().trim().isNotBlank() ||
                                m.etBis.text?.toString().orEmpty().trim().isNotBlank() ||
                                m.rows.any { r ->
                                    r.acBez.text?.toString().orEmpty().trim().isNotBlank() ||
                                        r.acAnzahl.text?.toString().orEmpty().trim().isNotBlank()
                                }
                        if (m.autoLinked && !hasMachineData) {
                            removeFirmaInSection(PmSection.MASCHINEN, m.firmaId)
                        }
                    }
                    return
                }

                if (existing == null) {
                    addFirmaBlock(
                        section = PmSection.MASCHINEN,
                        firmaId = personalUi.firmaId,
                        prefillFirma = name,
                        prefillVon = null,
                        prefillBis = null,
                        autoLinked = true
                    )
                } else {
                    // Nur überschreiben, wenn der Maschinen-Block ein Placeholder ist oder leer
                    val cur = existing.acFirma.text?.toString().orEmpty().trim()
                    if (existing.autoLinked || cur.isBlank()) {
                        existing.acFirma.setText(name, false)
                        existing.autoLinked = true
                    }
                }
            } finally {
                pmSyncing = false
            }
        }

        /** Baut die PM-UI aus den persistierten Firmenblöcken (Single Source of Truth). */
        private fun applyPmFromEntryFirmen(entry: BautagebuchEintrag) {
            pmApplying = true
            try {
                clearPmAll()
                pmFirmenQuellstand = entry.firmen
                for (firma in entry.firmen) {
                // Firma je nach Sichtbarkeit anlegen
                if (firma.visibleInPersonal) {
                    addFirmaBlock(PmSection.PERSONAL, firmaId = firma.firmaId, prefillFirma = firma.name, prefillVon = firma.personalVon, prefillBis = firma.personalBis, zeitbindung = firma.personalZeitbindung)
                }
                if (firma.visibleInMaschinen) {
                    addFirmaBlock(PmSection.MASCHINEN, firmaId = firma.firmaId, prefillFirma = firma.name, prefillVon = firma.maschinenVon, prefillBis = firma.maschinenBis, zeitbindung = firma.maschinenZeitbindung)
                }

                // Personal-Zeilen befüllen
                personalUis.firstOrNull { it.firmaId == firma.firmaId }?.let { ui ->
                    ui.rowsContainer.removeAllViews()
                    ui.rows.clear()
                    if (firma.personal.isEmpty()) {
                        addPairRow(ui, PmSection.PERSONAL, prefillAnzahl = null, prefillBez = null)
                    } else {
                        firma.personal.forEach { p ->
                            addPairRow(ui, PmSection.PERSONAL, prefillAnzahl = p.anzahl?.toString(), prefillBez = p.rolle)
                        }
                    }
                }

                // Geräte-Zeilen befüllen
                maschinenUis.firstOrNull { it.firmaId == firma.firmaId }?.let { ui ->
                    ui.rowsContainer.removeAllViews()
                    ui.rows.clear()
                    if (firma.geraete.isEmpty()) {
                        addPairRow(ui, PmSection.MASCHINEN, prefillAnzahl = null, prefillBez = null)
                    } else {
                        firma.geraete.forEach { g ->
                            addPairRow(ui, PmSection.MASCHINEN, prefillAnzahl = g.anzahl?.toString(), prefillBez = g.bezeichnung)
                        }
                    }
                }
                }
                updatePmPreviews()
            } finally {
                pmApplying = false
            }
        }

        /** Liest Firmenblöcke aus beiden PM-UI Sektionen und fasst sie pro firmaId zusammen. */
        private fun collectFirmenFromPmUi(): List<FirmaBlock> {
            // Firmen können in Personal und Maschinen unterschiedlich sein.
            // Persistiert wird pro Firmenname (case-insensitive), damit identische Firmen in beiden Sektionen
            // zusammengeführt werden, unterschiedliche Firmen aber getrennt bleiben.
            data class Acc(
                val key: String,
                var name: String,
                val firmaId: String? = null,
                var visibleInPersonal: Boolean = false,
                var visibleInMaschinen: Boolean = false,
                var personalVon: String? = null,
                var personalBis: String? = null,
                var maschinenVon: String? = null,
                var maschinenBis: String? = null,
                var personalZeitbindung: String? = null,
                var maschinenZeitbindung: String? = null,
                val personal: MutableList<PersonalZeile> = mutableListOf(),
                val geraete: MutableList<GeraetZeile> = mutableListOf()
            )

            fun normKey(name: String): String = name.trim().lowercase(java.util.Locale.ROOT)

            val map = linkedMapOf<String, Acc>()

            fun accFor(nameRaw: String, uiId: String): Acc? {
                val name = nameRaw.trim()
                if (name.isBlank()) return null
                val key = normKey(name)
                return map.getOrPut(key) {
                    val source = pmFirmenQuellstand.firstOrNull { it.firmaId == uiId }
                        ?: pmFirmenQuellstand.firstOrNull { normKey(it.name) == key }
                    Acc(key = key, name = name, firmaId = source?.firmaId,
                        personalZeitbindung = source?.personalZeitbindung,
                        maschinenZeitbindung = source?.maschinenZeitbindung)
                }.also { it.name = name }
            }

            // Personal
            for (b in personalUis) {
                val a = accFor(b.acFirma.text?.toString().orEmpty(), b.firmaId) ?: continue
                a.visibleInPersonal = true
                a.personalZeitbindung = b.zeitbindung
                a.personalVon = b.etVon.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                a.personalBis = b.etBis.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                for (r in b.rows) {
                    val rolle = r.acBez.text?.toString().orEmpty().trim()
                    val anz = r.acAnzahl.text?.toString().orEmpty().trim().toIntOrNull()
                    if (rolle.isBlank() && anz == null) continue
                    // rolle ist im Modell nicht nullable
                    a.personal.add(PersonalZeile(anzahl = anz, rolle = rolle.ifBlank { "" }))
                }
            }

            // Maschinen
            for (b in maschinenUis) {
                // Placeholder aus Personal -> Maschinen nur persistieren, wenn wirklich Maschinen-Daten befüllt wurden
                if (b.autoLinked) {
                    val hasMachineData =
                        b.etVon.text?.toString().orEmpty().trim().isNotBlank() ||
                            b.etBis.text?.toString().orEmpty().trim().isNotBlank() ||
                            b.rows.any { r ->
                                r.acBez.text?.toString().orEmpty().trim().isNotBlank() ||
                                    r.acAnzahl.text?.toString().orEmpty().trim().isNotBlank()
                            }
                    if (!hasMachineData) continue
                }
                val a = accFor(b.acFirma.text?.toString().orEmpty(), b.firmaId) ?: continue
                a.visibleInMaschinen = true
                a.maschinenZeitbindung = b.zeitbindung
                a.maschinenVon = b.etVon.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                a.maschinenBis = b.etBis.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                for (r in b.rows) {
                    val bez = r.acBez.text?.toString().orEmpty().trim()
                    val anz = r.acAnzahl.text?.toString().orEmpty().trim().toIntOrNull()
                    if (bez.isBlank() && anz == null) continue
                    // bezeichnung ist im Modell nicht nullable
                    a.geraete.add(GeraetZeile(anzahl = anz, bezeichnung = bez.ifBlank { "" }))
                }
            }

            // FirmaId: stabil aus Key ableiten (damit Reload konsistent ist)
            fun stableId(key: String): String = "firma-" + key.hashCode().toString()

            return map.values.map { a ->
                FirmaBlock(
                    firmaId = a.firmaId ?: stableId(a.key),
                    name = a.name.trim(),
                    personalVon = a.personalVon,
                    personalBis = a.personalBis,
                    maschinenVon = a.maschinenVon,
                    maschinenBis = a.maschinenBis,
                    personalZeitbindung = a.personalZeitbindung,
                    maschinenZeitbindung = a.maschinenZeitbindung,
                    visibleInPersonal = a.visibleInPersonal,
                    visibleInMaschinen = a.visibleInMaschinen,
                    personal = a.personal.toList(),
                    geraete = a.geraete.toList(),
                    leistungen = emptyList()
                )
            }
        }

        /** Erzeugt Legacy-Zeilen (für bestehende PDF/History), aus Firmenblöcken. */
        private fun firmenToLegacyLines(firmen: List<FirmaBlock>, section: PmSection): List<String> {
            fun header(f: FirmaBlock): String {
                val von = if (section == PmSection.PERSONAL) f.personalVon else f.maschinenVon
                val bis = if (section == PmSection.PERSONAL) f.personalBis else f.maschinenBis
                val v = von?.trim().orEmpty()
                val b = bis?.trim().orEmpty()
                return when {
                    v.isNotBlank() && b.isNotBlank() -> "${v} - ${b} ${f.name}"
                    v.isNotBlank() && b.isBlank() -> "${v} - ${f.name}"
                    v.isBlank() && b.isNotBlank() -> "- ${b} ${f.name}"
                    else -> f.name
                }
            }

            val out = mutableListOf<String>()
            for (f in firmen) {
                val firmHeader = header(f)
                val rows = if (section == PmSection.PERSONAL) {
                    f.personal.map { (it.anzahl?.toString()) to it.rolle }
                } else {
                    f.geraete.map { (it.anzahl?.toString()) to it.bezeichnung }
                }
                if (rows.isEmpty()) {
                    out.add(firmHeader)
                } else {
                    for ((anz, bez) in rows) {
                        val right = when {
                            !anz.isNullOrBlank() && bez.isNotBlank() -> "$anz $bez"
                            !anz.isNullOrBlank() -> anz
                            else -> bez
                        }
                        out.add("$firmHeader - $right")
                    }
                }
            }
            return out
        }

        // ----------------------
        // Phase 3b: Standardabschnitte (Abnahmen/Einweisung/Besonderheiten/Mängel)
        // ----------------------

        private fun sectionFromMultiline(raw: String?): SectionBlock {
            val timeIn = java.time.format.DateTimeFormatter.ofPattern("H:mm")
            val timeOut = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            val reTime = Regex("""^(\d{1,2}:\d{2})?(?:\s*[-–]\s*\(?\s*(\d{1,2}:\d{2})\s*\)?)?\s+(.+)$""")
            val reEntry = Regex("""^@ENTRY=([^\s]+)\s*(.*)$""", RegexOption.IGNORE_CASE)
            val reNode = Regex("""^@NODE=([^\s]+)\s*(.*)$""", RegexOption.IGNORE_CASE)
            val reFirmaId = Regex("""^@FIRMAID=([^\s]+)\s*(.*)$""", RegexOption.IGNORE_CASE)
            val reOrt = Regex("""^@ORT=(\d+)\s*(.*)$""", RegexOption.IGNORE_CASE)
            val reGeneral = Regex("""^@ALLGEMEIN\b\s*(.*)$""", RegexOption.IGNORE_CASE)
            val reKm = Regex("""^@KM=([0-9]+(?:[.,][0-9]+)?)(?:\s*[-–]\s*([0-9]+(?:[.,][0-9]+)?))?\s*(.*)$""", RegexOption.IGNORE_CASE)

            fun norm(t: String): String? = runCatching {
                java.time.LocalTime.parse(t.trim(), timeIn).format(timeOut)
            }.getOrNull()
            fun km(rawKm: String?): Double? = rawKm?.trim()?.replace(',', '.')?.toDoubleOrNull()

            val items = raw.orEmpty().lines().mapNotNull { source ->
                var line = source.trim().removePrefix("•").trim()
                if (line.isBlank()) return@mapNotNull null

                var clientEntryKey: String? = null
                var worksiteNodeKey: String? = null
                var firmaId: String? = null
                var legacyOrtId: Long? = null
                var explicitGeneral = false

                // Technische Editor-Marker dürfen in beliebiger Reihenfolge am Zeilenanfang stehen.
                // Dauerhafte Sync-Identitäten sind ENTRY/NODE/FIRMAID; ORT ist nur Legacy/UI-Cache.
                var markerConsumed: Boolean
                do {
                    markerConsumed = false
                    reEntry.matchEntire(line)?.let {
                        clientEntryKey = it.groupValues[1].trim().takeIf(String::isNotBlank)
                        line = it.groupValues[2].trim()
                        markerConsumed = true
                    }
                    if (markerConsumed) continue
                    reNode.matchEntire(line)?.let {
                        worksiteNodeKey = it.groupValues[1].trim().takeIf(String::isNotBlank)
                        line = it.groupValues[2].trim()
                        markerConsumed = true
                    }
                    if (markerConsumed) continue
                    reFirmaId.matchEntire(line)?.let {
                        firmaId = it.groupValues[1].trim().takeIf(String::isNotBlank)
                        line = it.groupValues[2].trim()
                        markerConsumed = true
                    }
                    if (markerConsumed) continue
                    reOrt.matchEntire(line)?.let {
                        legacyOrtId = it.groupValues[1].toLongOrNull()
                        line = it.groupValues[2].trim()
                        markerConsumed = true
                    }
                    if (markerConsumed) continue
                    reGeneral.matchEntire(line)?.let {
                        explicitGeneral = true
                        line = it.groupValues[1].trim()
                        markerConsumed = true
                    }
                } while (markerConsumed && line.isNotBlank())

                if (worksiteNodeKey == null) {
                    worksiteNodeKey = legacyOrtId?.let { btbNodeKeyByLocalId[it] }
                }

                var kmVon: Double? = null
                var kmBis: Double? = null
                reKm.matchEntire(line)?.let {
                    kmVon = km(it.groupValues.getOrNull(1))
                    kmBis = km(it.groupValues.getOrNull(2))
                    line = it.groupValues.getOrNull(3).orEmpty().trim()
                }
                line = line.removePrefix("•").trim()
                if (line.isBlank()) return@mapNotNull null

                fun build(time: String?, rawText: String, endTime: String? = null): SectionItem? {
                    var text = rawText.trim()
                    if (text.isBlank()) return null
                    var firmaName: String? = null
                    if (!explicitGeneral && firmaId != null) {
                        val splitAt = text.indexOf(": ")
                        if (splitAt > 0) {
                            firmaName = text.substring(0, splitAt).trim().takeIf(String::isNotBlank)
                            text = text.substring(splitAt + 2).trim()
                        }
                    }
                    if (text.isBlank()) return null
                    return SectionItem(
                        clientEntryKey = clientEntryKey,
                        uhrzeit = time,
                        uhrzeitBis = endTime,
                        text = text,
                        kmVon = kmVon,
                        kmBis = kmBis,
                        firmaId = if (explicitGeneral) null else firmaId,
                        firmaName = if (explicitGeneral) null else firmaName,
                        worksiteNodeKey = worksiteNodeKey
                    )
                }

                val m = reTime.matchEntire(line)
                if (m != null) {
                    val startNorm = m.groupValues[1].takeIf { it.isNotBlank() }?.let {
                        norm(it) ?: return@mapNotNull null
                    }
                    val endRaw = m.groupValues.getOrNull(2).orEmpty().trim()
                    val endNorm = endRaw.takeIf { it.isNotBlank() }?.let(::norm)
                    build(startNorm, m.groupValues[3], endNorm)
                } else {
                    build(null, line)
                }
            }
            return if (items.isEmpty()) SectionBlock() else SectionBlock(items = items)
        }

        private fun sectionToMultiline(sb: SectionBlock): String {
            return sb.items.filter { it.text.isNotBlank() }.joinToString("\n") { item ->
                val markers = buildList {
                    item.clientEntryKey?.trim()?.takeIf { it.isNotBlank() }?.let { add("@ENTRY=$it") }
                    item.worksiteNodeKey?.trim()?.takeIf { it.isNotBlank() }?.let { add("@NODE=$it") }
                    item.firmaId?.trim()?.takeIf { it.isNotBlank() }?.let { add("@FIRMAID=$it") }
                        ?: add("@ALLGEMEIN")
                    item.kmVon?.let { von ->
                        val a = String.format(java.util.Locale.US, "%.3f", von)
                        val b = item.kmBis?.takeIf { kotlin.math.abs(it - von) > 0.0000001 }
                            ?.let { String.format(java.util.Locale.US, "%.3f", it) }
                        add(if (b != null) "@KM=$a-$b" else "@KM=$a")
                    }
                }.joinToString(" ")
                val time = item.zeitAnzeige?.plus(" ").orEmpty()
                val firma = item.firmaName?.trim()?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
                listOf(markers, "• $time$firma${item.text}".trimEnd())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
        }

        private fun installBulletEnterBehavior(et: android.widget.EditText) {
            var selfChange = false

            et.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && !sectionsApplying) {
                    val t = et.text?.toString().orEmpty()
                    if (t.isBlank()) {
                        selfChange = true
                        et.setText("• ")
                        et.setSelection(et.text?.length ?: 0)
                        selfChange = false
                    }
                }
            }

            et.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (sectionsApplying || selfChange) return
                    if (s == null || s.isEmpty()) return
                    if (s[s.length - 1] == '\n') {
                        selfChange = true
                        s.append("• ")
                        selfChange = false
                    }
                }
            })
        }

        private fun setupSectionPreviewUi() {
            fun hook(
                et: TextInputEditText,
                title: String,
                getter: () -> String,
                fotoType: de.tbhelfer.datenbank.Bildtyp? = null,
                setter: (String) -> Unit
            ) {
                et.setOnClickListener {
                    openSectionEditor(title = title, initial = getter(), fotoType = fotoType) { newRaw ->
                        setter(newRaw)
                    }
                }
            }

            hook(etArbeiten, "4. Ausgeführte Arbeiten", { rawArbeiten }, fotoType = de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION) {
                rawArbeiten = it
                renderSectionPreviews()
                scheduleSectionsAutosave(force = true)
            }
            hook(etAbnahmen, "5. Abnahmen", { rawAbnahmen }, fotoType = de.tbhelfer.datenbank.Bildtyp.ABNAHMEN) {
                rawAbnahmen = it
                renderSectionPreviews()
                scheduleSectionsAutosave(force = true)
            }
            hook(etEinweisung, "6. Einweisung/Unterweisung", { rawEinweisung }, fotoType = de.tbhelfer.datenbank.Bildtyp.EINWEISUNG_UNTERWEISUNG) {
                rawEinweisung = it
                renderSectionPreviews()
                scheduleSectionsAutosave(force = true)
            }
            hook(etBesonderheiten, "7. Besonderheiten", { rawBesonderheiten }, fotoType = de.tbhelfer.datenbank.Bildtyp.BESONDERHEITEN) {
                rawBesonderheiten = it
                renderSectionPreviews()
                scheduleSectionsAutosave(force = true)
            }
            hook(etMaengel, "8. Mängel/Schäden", { rawMaengel }, fotoType = de.tbhelfer.datenbank.Bildtyp.MAENGEL_SCHAEDEN) {
                rawMaengel = it
                renderSectionPreviews()
                scheduleSectionsAutosave(force = true)
            }

            renderSectionPreviews()
        }

        private fun renderSectionPreviews() {
            sectionsApplying = true
            try {
                val pA = previewLast3(rawArbeiten)
                val pB = previewLast3(rawAbnahmen)
                val pE = previewLast3(rawEinweisung)
                val pBe = previewLast3(rawBesonderheiten)
                val pM = previewLast3(rawMaengel)

                etArbeiten.setText(pA)
                etAbnahmen.setText(pB)
                etEinweisung.setText(pE)
                etBesonderheiten.setText(pBe)
                etMaengel.setText(pM)

                applySectionCollapseUi(etArbeiten, pA)
                applySectionCollapseUi(etAbnahmen, pB)
                applySectionCollapseUi(etEinweisung, pE)
                applySectionCollapseUi(etBesonderheiten, pBe)
                applySectionCollapseUi(etMaengel, pM)
            } finally {
                sectionsApplying = false
            }
        }

        /** Vorschau-Kästen 4–8: überall gleiche Größe, zeigen nur die letzten 2 Zeilen. */
        private fun applySectionCollapseUi(et: TextInputEditText, previewText: String) {
            et.textSize = 12.5f
            et.setLineSpacing(0f, 1.0f)
            et.setMinLines(2)
            et.maxLines = 2
            et.minHeight = dpToPx(72)
            et.layoutParams = et.layoutParams?.apply { height = dpToPx(72) }
            et.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            et.ellipsize = android.text.TextUtils.TruncateAt.END
            et.setHorizontallyScrolling(false)
            et.isVerticalScrollBarEnabled = false
            et.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        }

        private fun previewLast3(raw: String): String {
            val cleaned = stripOrtMarkers(raw)
            val lines = cleaned
                .lines()
                .map { de.tbhelfer.util.KmAnzeige.zeile(it) }
                .filter { it.isNotBlank() }

            return lines.takeLast(2)
                .joinToString("\n") { "• $it" }
        }


        /**
         * Hauptansicht: Vorschau soll auch Foto-Gruppen zeigen (sonst bleibt Besonderheiten/Abnahmen oft leer).
         * Logik:
         * - Manuelle Zeilen wie bisher (letzte 3)
         * - Wenn weniger als 3 manuelle Zeilen vorhanden sind, wird mit Foto-Gruppen aufgefüllt
         * - Foto-Gruppen werden nach Firma+Text zusammengefasst (ganzer Tag), Zeitspanne = erstes/letztes Foto,
         *   Bild-Nummerierung wie im PDF (Anhang-Reihenfolge + maxFotosInPdf).
         */
        private fun renderSectionPreviewsWithPhotos(
            excludedByType: Map<String, Set<Long>>,
            fotosInShift: List<de.tbhelfer.datenbank.FotoEntity>,
            overridesByFotoId: Map<Long, String>,
            startOverridesByFotoId: Map<Long, String>
        ) {
            fun merged(raw: String, typ: de.tbhelfer.datenbank.Bildtyp): String {
                val manual = stripOrtMarkers(raw)
                    .lines()
                    .map { de.tbhelfer.util.KmAnzeige.zeile(it) }
                    .filter { it.isNotBlank() }

                val manualLast = manual.takeLast(2)
                val need = (2 - manualLast.size).coerceAtLeast(0)

                val photoLines = buildPhotoPreviewLines(
                    typ = typ,
                    fotosInShift = fotosInShift,
                    excludedByType = excludedByType,
                    overridesByFotoId = overridesByFotoId,
                    startOverridesByFotoId = startOverridesByFotoId
                )

                val addPhotos = if (need > 0) photoLines.takeLast(need) else emptyList()
                val out = manualLast + addPhotos
                return out.joinToString("\n") { "• $it" }
            }

            sectionsApplying = true
            try {
                val pA = merged(rawArbeiten, de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION)
                val pB = merged(rawAbnahmen, de.tbhelfer.datenbank.Bildtyp.ABNAHMEN)
                val pE = merged(rawEinweisung, de.tbhelfer.datenbank.Bildtyp.EINWEISUNG_UNTERWEISUNG)
                val pBe = merged(rawBesonderheiten, de.tbhelfer.datenbank.Bildtyp.BESONDERHEITEN)
                val pM = merged(rawMaengel, de.tbhelfer.datenbank.Bildtyp.MAENGEL_SCHAEDEN)

                etArbeiten.setText(pA)
                etAbnahmen.setText(pB)
                etEinweisung.setText(pE)
                etBesonderheiten.setText(pBe)
                etMaengel.setText(pM)

                applySectionCollapseUi(etArbeiten, pA)
                applySectionCollapseUi(etAbnahmen, pB)
                applySectionCollapseUi(etEinweisung, pE)
                applySectionCollapseUi(etBesonderheiten, pBe)
                applySectionCollapseUi(etMaengel, pM)
            } finally {
                sectionsApplying = false
            }
        }

        private fun buildPhotoPreviewLines(
            typ: de.tbhelfer.datenbank.Bildtyp,
            fotosInShift: List<de.tbhelfer.datenbank.FotoEntity>,
            excludedByType: Map<String, Set<Long>>,
            overridesByFotoId: Map<Long, String>,
            startOverridesByFotoId: Map<Long, String>
        ): List<String> {

            fun timeOf(epochMs: Long): String {
                val z = java.time.ZoneId.systemDefault()
                val dt = java.time.Instant.ofEpochMilli(epochMs).atZone(z)
                return dt.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            }

            fun hmToMin(hm: String): Int? {
                val p = hm.split(":")
                if (p.size != 2) return null
                val h = p[0].toIntOrNull() ?: return null
                val m = p[1].toIntOrNull() ?: return null
                if (h !in 0..23 || m !in 0..59) return null
                return h * 60 + m
            }

            fun minToHm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

            fun normalizeHm(hm: String): String? {
                val min = hmToMin(hm) ?: return null
                return minToHm(min)
            }

            fun parsePhotoTimeOverride(raw: String?): Pair<Int?, Int?>? {
                val s = raw?.trim().orEmpty()
                if (s.isBlank()) return null
                normalizeHm(s)?.let { one ->
                    val m = hmToMin(one) ?: return null
                    return m to null
                }
                val match = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})$""").matchEntire(s)
                    ?: return null
                val start = normalizeHm(match.groupValues[1])?.let { hmToMin(it) } ?: return null
                val end = normalizeHm(match.groupValues[2])?.let { hmToMin(it) } ?: return null
                return start to end
            }

            fun wallTimeEpochNear(refEpochMs: Long, minuteOfDay: Int): Long {
                val zone = java.time.ZoneId.systemDefault()
                val ref = java.time.Instant.ofEpochMilli(refEpochMs).atZone(zone)
                val base = ref.toLocalDate()
                return listOf(-1L, 0L, 1L)
                    .map { delta ->
                        base.plusDays(delta)
                            .atStartOfDay(zone)
                            .plusMinutes(minuteOfDay.toLong())
                            .toInstant()
                            .toEpochMilli()
                    }
                    .minBy { kotlin.math.abs(it - refEpochMs) }
            }

            fun effectivePhotoRangeFor(sorted: List<de.tbhelfer.datenbank.FotoEntity>): Triple<String, String, Long> {
                val autoStartEpoch = sorted.first().erstelltAmEpochMs
                val autoEndEpoch = sorted.last().erstelltAmEpochMs
                val manualRanges = sorted.mapNotNull { parsePhotoTimeOverride(startOverridesByFotoId[it.id]) }
                val manualStartEpoch = manualRanges.mapNotNull { it.first?.let { min -> wallTimeEpochNear(autoStartEpoch, min) } }.minOrNull()
                val manualEndEpoch = manualRanges.mapNotNull { it.second?.let { min -> wallTimeEpochNear(autoEndEpoch, min) } }.maxOrNull()
                val effectiveStartEpoch = manualStartEpoch?.let { minOf(it, autoStartEpoch) } ?: autoStartEpoch
                val effectiveEndEpoch = manualEndEpoch?.let { maxOf(it, autoEndEpoch) } ?: autoEndEpoch
                return Triple(timeOf(effectiveStartEpoch), timeOf(effectiveEndEpoch), effectiveStartEpoch)
            }

            fun bildLabel(numsIn: List<Int>): String {
                if (numsIn.isEmpty()) return ""
                val nums = numsIn.distinct().sorted()
                val runs = ArrayList<Pair<Int, Int>>()
                var a = nums.first()
                var b = a
                for (n in nums.drop(1)) {
                    if (n == b + 1) b = n
                    else {
                        runs.add(a to b)
                        a = n
                        b = n
                    }
                }
                runs.add(a to b)
                val label = runs.joinToString("+") { (x, y) -> if (x == y) "$x" else "$x–$y" }
                return "Bild $label"
            }

            // nur ausgewählte Fotos (Exclude-Store)
            val selected = fotosInShift
                .asSequence()
                .filter { f ->
                    val ex = excludedByType[f.bildtyp.name]
                    ex == null || !ex.contains(f.id)
                }
                .toList()

            // Bildnummerierung wie im PDF (Anhang-Reihenfolge + maxFotosInPdf)
            val anhangOrder = listOf(
                de.tbhelfer.datenbank.Bildtyp.ABNAHMEN,
                de.tbhelfer.datenbank.Bildtyp.EINWEISUNG_UNTERWEISUNG,
                de.tbhelfer.datenbank.Bildtyp.BESONDERHEITEN,
                de.tbhelfer.datenbank.Bildtyp.MAENGEL_SCHAEDEN,
                de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION
            )
            val byType = selected.groupBy { it.bildtyp }
            val maxFotos = currentMaxFotosInPdf.coerceAtLeast(1)
            val selectedOrdered = anhangOrder
                .flatMap { t -> byType[t].orEmpty().sortedBy { it.erstelltAmEpochMs } }
                .take(maxFotos)

            val fotoNum = LinkedHashMap<Long, Int>().apply {
                var n = 1
                selectedOrdered.forEach { f ->
                    this[f.id] = n
                    n += 1
                }
            }

            val relevant = selected.filter { it.bildtyp == typ }
            if (relevant.isEmpty()) return emptyList()

            data class L(val sortEpochMs: Long, val line: String)

            val grouped = relevant.groupBy { f ->
                val firm = f.firma.trim().takeIf { it.isNotBlank() }
                val ov = overridesByFotoId[f.id]?.trim().orEmpty()
                val txt = if (ov.isNotBlank()) ov else BtbPhotoDescription.forBtb(f.kurztext, f.notiz)
                firm to txt
            }

            val out = ArrayList<L>()
            grouped.entries.forEach { (k, list) ->
                val (firm, txt) = k
                if (txt.isBlank()) return@forEach
                val sorted = list.sortedBy { it.erstelltAmEpochMs }
                val (from, to, sortEpochMs) = effectivePhotoRangeFor(sorted)

                val nums = sorted.mapNotNull { fotoNum[it.id] }
                val bl = bildLabel(nums)

                val core = if (from == to) "$from $txt" else "$from–$to $txt"
                val withBild = if (bl.isNotBlank()) "$core  $bl" else core
                val line = if (!firm.isNullOrBlank()) "${firm.trim()}: $withBild" else withBild

                out.add(L(sortEpochMs = sortEpochMs, line = line))
            }

            return out.sortedWith(compareBy<L> { it.sortEpochMs }.thenBy { it.line }).map { it.line }
        }




        private fun openSectionEditor(
            title: String,
            initial: String,
            fotoType: de.tbhelfer.datenbank.Bildtyp? = null,
            onSave: (String) -> Unit
        ) {
            val dialog = android.app.Dialog(requireContext())
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_section_editor_full)
            dialog.setCancelable(true)

            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            val tvTitle = dialog.findViewById<android.widget.TextView>(R.id.tvDialogTitle)

            // Robust gegen Layout-Varianten: TextInputEditText ist ein EditText, Buttons können AppCompat/Material sein.
            val etFull = requireNotNull(dialog.findViewById<android.widget.EditText>(R.id.etSectionFull)) {
                "dialog_section_editor_full.xml: etSectionFull fehlt"
            }
            val btnCancel = requireNotNull(dialog.findViewById<android.view.View>(R.id.btnDialogCancel)) {
                "dialog_section_editor_full.xml: btnDialogCancel fehlt"
            }
            val btnSave = requireNotNull(dialog.findViewById<android.view.View>(R.id.btnDialogSave)) {
                "dialog_section_editor_full.xml: btnDialogSave fehlt"
            }

            val svPhoto = dialog.findViewById<androidx.core.widget.NestedScrollView>(R.id.svPhotoEntries)
            val llPhoto = dialog.findViewById<android.widget.LinearLayout>(R.id.llPhotoEntries)
            val divider = dialog.findViewById<android.view.View>(R.id.dividerSectionEditor)

            tvTitle.text = title

            // Abschnitte 4–8 nutzen den Firmen-/Pill-Editor (Einträge + Foto-Pills pro Bildtyp).
            val sectionNo = Regex("""^\s*(\d+)\.""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val currentEditSection = BtbEditSection.fromPhotoType(fotoType)
            val enableFotoPanel = (sectionNo != null && sectionNo in 4..8 && currentEditSection != null)

            // Volltext anzeigen
            etFull.setText(initial)
            etFull.setSelection(etFull.text?.length ?: 0)

            // Bullet/Enter-Logik wie bisher (eine Zeile pro Eintrag)
            installBulletEnterBehavior(etFull)

            // Abschnitt 4: Editor-Textfeld ausblenden (manuelle Einträge werden über Firmen-/Pill-Ansicht gepflegt)
            if (enableFotoPanel) {
                etFull.visibility = android.view.View.GONE
                divider?.visibility = android.view.View.GONE
            } else {
                // Standardabschnitte: kein Foto-/Firmenpanel
                svPhoto?.visibility = android.view.View.GONE
                llPhoto?.removeAllViews()
            }

            // Foto-Einträge (je Bildtyp) im gleichen Dialog anzeigen und editierbar machen.
            // Kein "Allgemein"-Header/Separation – es sind einfach zusätzliche Zeilen.
            if (enableFotoPanel && svPhoto != null && llPhoto != null) {
                fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

                var loadFotoGroups: (() -> Unit)? = null


            
                data class FotoGroup(
                    val firma: String?,
                    val text: String,
                    val ortId: Long,
                    val from: String,
                    val to: String,
                    /** Effektive KM nur fuer BTB/PDF. Original-Foto-KM bleiben unveraendert. */
                    val kmVon: Double?,
                    val kmBis: Double?,
                    val kmManual: Boolean,
                    val bildLabel: String,
                    val ids: List<Long>,
                    val sortEpochMs: Long
                )

                var lastGroups: List<FotoGroup> = emptyList()
                var lastFirmaBlocks: List<FirmaBlock> = emptyList()
                var renderMerged: ((List<FotoGroup>) -> Unit)? = null


                fun timeOf(epochMs: Long): String {
                    val z = java.time.ZoneId.systemDefault()
                    val dt = java.time.Instant.ofEpochMilli(epochMs).atZone(z)
                    return dt.toLocalTime().format(timeFmt)
                }

                fun effectiveText(f: de.tbhelfer.datenbank.FotoEntity): String =
                    BtbPhotoDescription.forBtb(f.kurztext, f.notiz)

                fun bildLabel(numsIn: List<Int>): String {
                    if (numsIn.isEmpty()) return ""
                    val nums = numsIn.distinct().sorted()
                    val runs = ArrayList<Pair<Int, Int>>()
                    var a = nums.first()
                    var b = a
                    for (n in nums.drop(1)) {
                        if (n == b + 1) b = n
                        else {
                            runs.add(a to b)
                            a = n
                            b = n
                        }
                    }
                    runs.add(a to b)
                    val label = runs.joinToString("+") { (x, y) -> if (x == y) "$x" else "$x–$y" }
                    return "Bild $label"
                }

            
                data class ManualItem(
                    val lineIndex: Int,
                    val firma: String?,
                    val firmaId: String?,
                    val time: String?,     // "HH:mm" oder "HH:mm–HH:mm" oder null
                    val timeMin: Int?,     // Minuten seit 00:00 oder null
                    val ortId: Long,       // lokale UI-ID; nicht synchronisieren
                    val worksiteNodeKey: String?,
                    val clientEntryKey: String?,
                    val text: String,
                    val kmVon: Double? = null,
                    val kmBis: Double? = null
                )

                fun hmToMin(hm: String): Int? {
                    val p = hm.split(":")
                    if (p.size != 2) return null
                    val h = p[0].toIntOrNull() ?: return null
                    val m = p[1].toIntOrNull() ?: return null
                    if (h !in 0..23 || m !in 0..59) return null
                    return h * 60 + m
                }

                fun normalizeHm(hm: String): String? {
                    val min = hmToMin(hm) ?: return null
                    val h = min / 60
                    val m = min % 60
                    return "%02d:%02d".format(h, m)
                }

                fun minToHm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

                fun parsePhotoTimeOverride(raw: String?): Pair<Int?, Int?>? {
                    val s = raw?.trim().orEmpty()
                    if (s.isBlank()) return null
                    normalizeHm(s)?.let { one ->
                        val m = hmToMin(one) ?: return null
                        return m to null
                    }
                    val match = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})$""").matchEntire(s)
                        ?: return null
                    val start = normalizeHm(match.groupValues[1])?.let { hmToMin(it) } ?: return null
                    val end = normalizeHm(match.groupValues[2])?.let { hmToMin(it) } ?: return null
                    return start to end
                }

                fun wallTimeEpochNear(refEpochMs: Long, minuteOfDay: Int): Long {
                    val zone = java.time.ZoneId.systemDefault()
                    val ref = java.time.Instant.ofEpochMilli(refEpochMs).atZone(zone)
                    val base = ref.toLocalDate()
                    return listOf(-1L, 0L, 1L)
                        .map { delta ->
                            base.plusDays(delta)
                                .atStartOfDay(zone)
                                .plusMinutes(minuteOfDay.toLong())
                                .toInstant()
                                .toEpochMilli()
                        }
                        .minBy { kotlin.math.abs(it - refEpochMs) }
                }

                fun normalizeTimeOrSpan(input: String): String? {
                    val s = input.trim()
                    if (s.isBlank()) return null

                    Regex("""^[-–]\s*(\d{1,2}:\d{2})$""").matchEntire(s)?.let {
                        return normalizeHm(it.groupValues[1])?.let { end -> "–$end" }
                    }

                    // Single time
                    normalizeHm(s)?.let { return it }

                    // Span: HH:mm-HH:mm oder HH:mm–HH:mm
                    val m = Regex("^(\\d{1,2}:\\d{2})\\s*[-–]\\s*\\(?\\s*(\\d{1,2}:\\d{2})\\s*\\)?$").matchEntire(s)
                    if (m != null) {
                        val a = normalizeHm(m.groupValues[1]) ?: return null
                        val b = normalizeHm(m.groupValues[2]) ?: return null
                        return "$a–$b"
                    }
                    return null
                }

                fun parseKmValue(raw: String?): Double? {
                    val s = raw?.trim().orEmpty().replace(',', '.')
                    if (s.isBlank()) return null
                    val v = s.toDoubleOrNull() ?: return null
                    return v.takeIf { it >= 0.0 && it < 10000.0 }
                }

                fun kmMarker(von: Double?, bis: Double?): String? {
                    val first = von ?: bis ?: return null
                    val a = String.format(java.util.Locale.US, "%.3f", first)
                    val second = bis?.takeIf { kotlin.math.abs(it - first) > 0.0000001 }
                    return if (second != null) {
                        val b = String.format(java.util.Locale.US, "%.3f", second)
                        "@KM=$a-$b"
                    } else "@KM=$a"
                }

                fun kmAnzeige(von: Double?, bis: Double?): String? =
                    de.tbhelfer.util.KmAnzeige.bereich(von, bis, inKlammern = true)

                fun parseManualItems(raw: String, knownFirms: Set<String>): List<ManualItem> {
                    val out = ArrayList<ManualItem>()
                    val lines = raw.lines()
                    var currentOrtId: Long = 0L
                    var currentNodeKey: String? = null
                    var currentOrtIstStandaloneMarker = false

                    val firmMap = knownFirms
                        .mapNotNull { it.trim().takeIf { s -> s.isNotBlank() } }
                        .associateBy { it.lowercase(java.util.Locale.ROOT) }
                    val firmIdByName = lastFirmaBlocks
                        .filter { !it.isDeleted && it.name.isNotBlank() }
                        .associate { it.name.trim().lowercase(java.util.Locale.ROOT) to it.firmaId }

                    val reEntry = Regex("""^@ENTRY=([^\s]+)\s*(.*)$""", RegexOption.IGNORE_CASE)
                    val reNode = Regex("""^@NODE=([^\s]+)\s*(.*)$""", RegexOption.IGNORE_CASE)
                    val reFirmaId = Regex("""^@FIRMAID=([^\s]+)\s*(.*)$""", RegexOption.IGNORE_CASE)
                    val reOrt = Regex("""^@ORT=(\d+)\s*(.*)$""", RegexOption.IGNORE_CASE)
                    val reKm = Regex("""^@KM=([0-9]+(?:[.,][0-9]+)?)(?:\s*[-–]\s*([0-9]+(?:[.,][0-9]+)?))?\s*(.*)$""", RegexOption.IGNORE_CASE)
                    val reGeneral = Regex("""^@ALLGEMEIN\b\s*(.*)$""", RegexOption.IGNORE_CASE)

                    lines.forEachIndexed { idxLine, line0 ->
                        var line = line0.trim()
                        if (line.isBlank()) return@forEachIndexed

                        var clientEntryKey: String? = null
                        var lineNodeKey: String? = null
                        var lineOrtId: Long? = null
                        var explicitFirmaId: String? = null
                        var forcedGeneral = false
                        var kmVon: Double? = null
                        var kmBis: Double? = null

                        var markerGefunden: Boolean
                        do {
                            markerGefunden = false
                            reEntry.matchEntire(line)?.let {
                                clientEntryKey = it.groupValues[1].trim().takeIf(String::isNotBlank)
                                line = it.groupValues[2].trim()
                                markerGefunden = true
                            }
                            if (markerGefunden) continue

                            reNode.matchEntire(line)?.let {
                                val markerNodeKey = it.groupValues[1].trim().takeIf(String::isNotBlank)
                                val rest = it.groupValues[2].trim()
                                if (rest.isBlank()) {
                                    currentNodeKey = markerNodeKey
                                    currentOrtId = markerNodeKey
                                        ?.let { key -> btbLocalIdByNodeKey[key] }
                                        ?: 0L
                                    currentOrtIstStandaloneMarker = true
                                    line = ""
                                } else {
                                    lineNodeKey = markerNodeKey
                                    markerNodeKey
                                        ?.let { key -> btbLocalIdByNodeKey[key] }
                                        ?.let { lineOrtId = it }
                                    currentOrtIstStandaloneMarker = false
                                    line = rest
                                }
                                markerGefunden = true
                            }
                            if (markerGefunden) continue

                            reFirmaId.matchEntire(line)?.let {
                                explicitFirmaId = it.groupValues[1].trim().takeIf(String::isNotBlank)
                                line = it.groupValues[2].trim()
                                markerGefunden = true
                            }
                            if (markerGefunden) continue

                            reOrt.matchEntire(line)?.let {
                                val markerOrtId = it.groupValues[1].toLongOrNull() ?: 0L
                                val rest = it.groupValues[2].trim()
                                if (rest.isBlank()) {
                                    currentOrtId = markerOrtId
                                    currentNodeKey = btbNodeKeyByLocalId[currentOrtId]
                                    currentOrtIstStandaloneMarker = true
                                    line = ""
                                } else {
                                    lineOrtId = markerOrtId
                                    if (lineNodeKey == null) {
                                        lineNodeKey = btbNodeKeyByLocalId[markerOrtId]
                                    }
                                    currentOrtIstStandaloneMarker = false
                                    line = rest
                                }
                                markerGefunden = true
                            }
                            if (markerGefunden) continue

                            reGeneral.matchEntire(line)?.let {
                                forcedGeneral = true
                                explicitFirmaId = null
                                line = it.groupValues[1].trim()
                                markerGefunden = true
                            }
                            if (markerGefunden) continue

                            reKm.matchEntire(line)?.let {
                                kmVon = parseKmValue(it.groupValues[1])
                                kmBis = parseKmValue(it.groupValues.getOrNull(2))
                                line = it.groupValues.getOrNull(3).orEmpty().trim()
                                markerGefunden = true
                            }
                        } while (markerGefunden && line.isNotBlank())
                        if (line.isBlank()) return@forEachIndexed

                        if (line.startsWith("•")) line = line.removePrefix("•").trimStart()
                        if (line.isBlank()) return@forEachIndexed

                        var timeStr: String? = null
                        var timeMin: Int? = null
                        var rest = line
                        val m = Regex("""^(\d{1,2}:\d{2})?(?:\s*[-–]\s*\(?\s*(\d{1,2}:\d{2})\s*\)?\s*)?\s+(.*)$""").find(line)
                        if (m != null) {
                            val startNorm = m.groupValues[1].takeIf { it.isNotBlank() }?.let { normalizeHm(it) }
                            if (startNorm != null || m.groupValues[2].isNotBlank()) {
                                val endRaw = m.groupValues.getOrNull(2).orEmpty().trim()
                                val endNorm = endRaw.takeIf { it.isNotBlank() }?.let { normalizeHm(it) }
                                timeStr = if (endNorm != null) "${startNorm.orEmpty()}–$endNorm" else startNorm
                                timeMin = startNorm?.let { hmToMin(it) }
                                rest = m.groupValues[3].trim()
                            }
                        }

                        var firma: String? = null
                        var text2 = rest
                        if (!forcedGeneral) {
                            val cIdx = rest.indexOf(':')
                            if (cIdx > 0) {
                                val prefixName = rest.substring(0, cIdx).trim()
                                val tail = rest.substring(cIdx + 1).trim()
                                val canon = firmMap[prefixName.lowercase(java.util.Locale.ROOT)]
                                if (canon != null || explicitFirmaId != null) {
                                    firma = canon ?: prefixName
                                    text2 = tail
                                }
                            }
                        }
                        if (text2.isBlank()) return@forEachIndexed

                        val firmaId = if (forcedGeneral) null else (
                            explicitFirmaId ?: firma?.lowercase(java.util.Locale.ROOT)?.let { firmIdByName[it] }
                        )
                        val effectiveOrtId = lineOrtId
                            ?: if (currentOrtIstStandaloneMarker) currentOrtId else 0L
                        val effectiveNodeKey = lineNodeKey
                            ?: if (currentOrtIstStandaloneMarker) currentNodeKey
                            else btbNodeKeyByLocalId[effectiveOrtId]

                        out.add(
                            ManualItem(
                                lineIndex = idxLine,
                                firma = if (forcedGeneral) null else firma,
                                firmaId = firmaId,
                                time = timeStr,
                                timeMin = timeMin,
                                ortId = effectiveOrtId,
                                worksiteNodeKey = effectiveNodeKey,
                                clientEntryKey = clientEntryKey,
                                text = text2,
                                kmVon = kmVon,
                                kmBis = kmBis
                            )
                        )
                    }
                    return out
                }

                fun buildManualLine(
                    ortId: Long,
                    firm: String?,
                    time: String?,
                    text2: String,
                    kmVon: Double? = null,
                    kmBis: Double? = null,
                    firmaId: String? = null,
                    worksiteNodeKey: String? = null,
                    clientEntryKey: String? = null
                ): String {
                    val t = time?.trim().orEmpty().takeIf { it.isNotBlank() }
                    val f = firm?.trim().orEmpty().takeIf { it.isNotBlank() }
                    val txt = text2.trim()
                    val stableEntry = clientEntryKey?.trim()?.takeIf { it.isNotBlank() }
                        ?: "bte-${java.util.UUID.randomUUID()}"
                    val stableNode = worksiteNodeKey?.trim()?.takeIf { it.isNotBlank() }
                        ?: btbNodeKeyByLocalId[ortId]
                    val stableFirmaId = if (f == null) null else (
                        firmaId?.trim()?.takeIf { it.isNotBlank() }
                            ?: lastFirmaBlocks.firstOrNull {
                                it.name.trim().equals(f, ignoreCase = true) && !it.isDeleted
                            }?.firmaId
                    )

                    return buildString {
                        append("@ENTRY=").append(stableEntry).append(" ")
                        stableNode?.let { append("@NODE=").append(it).append(" ") }
                        // Lokale UI-ID wird immer explizit geschrieben; Hauptbaustelle = @ORT=0.
                        // Geräteübergreifende Identität bleibt ausschließlich @NODE/clientNodeKey.
                        append("@ORT=").append(ortId).append(" ")
                        if (stableFirmaId != null) append("@FIRMAID=").append(stableFirmaId).append(" ")
                        else append("@ALLGEMEIN ")
                        kmMarker(kmVon, kmBis)?.let { append(it).append(" ") }
                        append("• ")
                        if (t != null) append(t).append(' ')
                        if (f != null) append(f).append(": ")
                        append(txt)
                    }.trimEnd()
                }


                fun setRawLines(lines: List<String>) {
                    // Wichtig: Ort-Marker im hidden Field behalten, sonst geht die Gruppierung verloren.
                    val next = lines.joinToString("\n").trimEnd()
                    etFull.setText(next)
                    etFull.setSelection(etFull.text?.length ?: 0)
                }

                fun matchingFotoIds(mi: ManualItem): List<Long> {
                    val btbId = loadedBtbId ?: return emptyList()
                    val ids = lastGroups.flatMap { it.ids }.distinct()
                    val direct = mi.clientEntryKey?.let { key -> ids.filter {
                        BtbPhotoMetadatenStore.entryKey(requireContext(), btbId, it) == key
                    } }.orEmpty()
                    val known = lastFirmaBlocks.filter { !it.isDeleted }.map { it.name }.toSet()
                    val targets = parseManualItems(etFull.text?.toString().orEmpty(), known).filter {
                        it.ortId == mi.ortId && it.firma.orEmpty().equals(mi.firma.orEmpty(), true) &&
                            it.text.trim().equals(mi.text.trim(), true)
                    }
                    if (targets.size != 1) return direct
                    val fallback = lastGroups.filter { g -> g.ortId == mi.ortId &&
                        g.firma.orEmpty().equals(mi.firma.orEmpty(), true) && g.text.trim().equals(mi.text.trim(), true)
                    }.flatMap { it.ids }.filter {
                        BtbPhotoMetadatenStore.entryKey(requireContext(), btbId, it) == null &&
                            BtbPhotoMetadatenStore.autoBindingAllowed(requireContext(), btbId, it)
                    }
                    return (direct + fallback).distinct()
                }


                fun confirmDeleteManualItem(mi: ManualItem) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Eintrag löschen?")
                        .setMessage(if (mi.time != null) "${mi.time} ${mi.text}".trim() else mi.text)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.action_delete) { _, _ ->
                            val photoIds = matchingFotoIds(mi)
                            val lines = etFull.text?.toString().orEmpty().lines().toMutableList()
                            if (mi.lineIndex in lines.indices) {
                                lines.removeAt(mi.lineIndex)
                                setRawLines(lines.filter { it.isNotBlank() })
                                loadedBtbId?.let { btb -> photoIds.forEach { id ->
                                    BtbPhotoMetadatenStore.bind(requireContext(), btb, id, null)
                                } }
                                onSave(etFull.text?.toString().orEmpty())
                                saveAndReloadWholeBtbSilently()
                                renderMerged?.invoke(lastGroups)
                            }
                        }
                        .show()
                }

                fun excludePhotoGroupFromBtb(g: FotoGroup) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val ctx = resolveAktuelleBaustelle() ?: return@launch
                        val entry = buildEntry(ctx)
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Foto aus BTB entfernen?")
                            .setMessage("${g.bildLabel.ifBlank { "Bild" }} wird aus BTB/PDF entfernt. Die Bilddatei bleibt erhalten.")
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.action_delete) { _, _ ->
                                viewLifecycleOwner.lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        val excluded = BtbPhotoSelectionStore.loadExcluded(
                                            requireContext(),
                                            ctx.id,
                                            entry.datumIso,
                                            entry.startUhrzeit,
                                            entry.endeUhrzeit,
                                            selectedSchichtTyp
                                        )
                                        val key = fotoType?.name ?: return@withContext
                                        val set = excluded.getOrPut(key) { linkedSetOf() }
                                        g.ids.forEach { set.add(it) }
                                        BtbPhotoSelectionStore.saveExcluded(
                                            requireContext(),
                                            ctx.id,
                                            entry.datumIso,
                                            entry.startUhrzeit,
                                            entry.endeUhrzeit,
                                            selectedSchichtTyp,
                                            excluded
                                        )
                                    }
                                    loadFotoGroups?.invoke()
                                }
                            }
                            .show()
                    }
                }

                fun addHeaderRow(
                    title: String,
                    onAdd: (() -> Unit)?,
                    onRemoveManual: (() -> Unit)?
                ) {
                    val row = android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        setPadding(dp(8), dp(6), dp(8), dp(2))
                        setBackgroundColor(0x11000000)
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(4) }
                    }

                    val tv = android.widget.TextView(requireContext()).apply {
                        text = title
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        textSize = 13.5f
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    row.addView(tv)

                    if (onRemoveManual != null) {
                        val btnDel = android.widget.ImageButton(requireContext()).apply {
                            setImageResource(android.R.drawable.ic_menu_delete)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            contentDescription = getString(R.string.btb_remove_company_entries)
                            layoutParams = android.widget.LinearLayout.LayoutParams(dp(32), dp(32))
                        }
                        btnDel.setOnClickListener { onRemoveManual() }
                        row.addView(btnDel)
                    }

                    if (onAdd != null) {
                        val btn = android.widget.ImageButton(requireContext()).apply {
                            setImageResource(android.R.drawable.ic_input_add)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            contentDescription = getString(R.string.btb_add_entry_cd)
                            layoutParams = android.widget.LinearLayout.LayoutParams(dp(32), dp(32))
                        }
                        btn.setOnClickListener { onAdd() }
                        row.addView(btn)
                    }

                    llPhoto.addView(row)
                }

                fun formatManual(mi: ManualItem): String {
                    return listOfNotNull(mi.time, kmAnzeige(mi.kmVon, mi.kmBis), mi.text).joinToString(" ").trim()
                }


                fun showManualPhotoLarge(foto: de.tbhelfer.datenbank.FotoEntity, number: Int) {
                    val ctx = requireContext()
                    val image = android.widget.ImageView(ctx).apply {
                        adjustViewBounds = true
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(380)
                        )
                    }
                    de.tbhelfer.util.ThumbnailLoader.loadInto(
                        imageView = image,
                        filePath = foto.dateipfad,
                        placeholderResId = android.R.drawable.ic_menu_report_image,
                        reqW = 1600,
                        reqH = 1600
                    )
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                        .setTitle("Bild $number")
                        .setView(image)
                        .setNegativeButton("Schließen", null)
                        .show()
                }

                fun launchPhotoForManualItem(mi: ManualItem, source: String) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val ctx = resolveAktuelleBaustelle()
                        if (ctx == null) {
                            Snackbar.make(requireView(), "Bitte zuerst eine Baustelle auswählen", Snackbar.LENGTH_LONG).show()
                            return@launch
                        }
                        val activeId = loadedBtbId?.trim()?.takeIf { it.isNotBlank() }
                            ?: withContext(Dispatchers.IO) {
                                BtbActiveContext.laden(requireContext(), ctx.id)?.btbId
                            }
                        if (activeId.isNullOrBlank()) {
                            Snackbar.make(requireView(), "Kein aktiver BTB für Bilder", Snackbar.LENGTH_LONG).show()
                            return@launch
                        }

                        // Vor dem Wechsel in Kamera/Galerie den kompletten aktuellen Abschnittsstand
                        // persistieren. So gehen auch Änderungen an anderen manuellen Einträgen aus
                        // demselben geöffneten Editor nicht verloren.
                        onSave(etFull.text?.toString().orEmpty())
                        val existing = withContext(Dispatchers.IO) {
                            BautagebuchSpeicher.ladenById(requireContext(), ctx.id, ctx.name, activeId)
                        }
                        val draft = buildEntry(ctx)
                        val snapshot = draft.copy(
                            btbId = activeId,
                            maxFotosInPdf = existing?.maxFotosInPdf ?: draft.maxFotosInPdf,
                            pdfAuswahl = existing?.pdfAuswahl ?: draft.pdfAuswahl,
                            erstelltAmEpochMs = existing?.erstelltAmEpochMs ?: draft.erstelltAmEpochMs,
                            geaendertAmEpochMs = System.currentTimeMillis()
                        )
                        withContext(Dispatchers.IO) {
                            BautagebuchSpeicher.speichern(requireContext(), snapshot)
                        }

                        val b = Bundle().apply {
                            putLong("baustelleId", snapshot.baustelleId)
                            putString("baustelleName", snapshot.baustelleName)
                            putString("datumIso", snapshot.datumIso)
                            putString("shiftStart", snapshot.startUhrzeit)
                            putString("shiftEnd", snapshot.endeUhrzeit)
                            putString("schichtTyp", snapshot.schicht.name)
                            putString("filterBildtyp", fotoType?.name ?: de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION.name)
                            putString("btbId", activeId)
                            putString("autoPhotoSource", source)
                            putString("autoPhotoFirma", mi.firma.orEmpty())
                            putLong("autoPhotoOrtId", mi.ortId)
                            putString("autoPhotoText", mi.text)
                            putString("autoPhotoEntryKey", mi.clientEntryKey)
                            mi.time?.let { putString("autoPhotoTime", it) }
                            mi.kmVon?.let { putDouble("autoPhotoKmVon", it) }
                            mi.kmBis?.let { putDouble("autoPhotoKmBis", it) }
                        }
                        dialog.dismiss()
                        findNavController().navigate(R.id.btbPdfAuswahlFragment, b)
                    }
                }

                fun moveManualItem(mi: ManualItem) {
                    val firms = LinkedHashSet<String>().apply {
                        lastFirmaBlocks.asSequence()
                            .filter { !it.isDeleted }
                            .map { it.name.trim() }
                            .filter { it.isNotBlank() }
                            .forEach { add(it) }
                        lastGroups.asSequence()
                            .mapNotNull { it.firma?.trim() }
                            .filter { it.isNotBlank() }
                            .forEach { add(it) }
                    }.toList()

                    val targets = (listOf<String?>(null) + firms).filterNot { target ->
                        target.orEmpty().trim().equals(mi.firma.orEmpty().trim(), ignoreCase = true)
                    }
                    if (targets.isEmpty()) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Eintrag verschieben")
                            .setMessage("Kein anderes Ziel vorhanden.")
                            .setPositiveButton("OK", null)
                            .show()
                        return
                    }

                    val labels = targets.map { it ?: getString(R.string.btb_company_general) }.toTypedArray()
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Eintrag verschieben – Ziel wählen")
                        // AlertDialog zeigt Message und Listeninhalt nicht zuverlässig gemeinsam.
                        // Deshalb hier nur die Zielliste; so bleiben Allgemein/Firmen immer sichtbar.
                        .setItems(labels) { _, which ->
                            val targetFirma = targets[which]
                            val targetFirmaId = targetFirma?.let { name ->
                                lastFirmaBlocks.firstOrNull {
                                    !it.isDeleted && it.name.trim().equals(name.trim(), ignoreCase = true)
                                }?.firmaId
                            }

                            val photoIds = matchingFotoIds(mi)
                            val targetOrt = mi.ortId
                            val lines = etFull.text?.toString().orEmpty().lines().toMutableList()
                            if (mi.lineIndex in lines.indices) {
                                lines[mi.lineIndex] = buildManualLine(
                                    ortId = targetOrt,
                                    firm = targetFirma,
                                    time = mi.time,
                                    text2 = mi.text,
                                    kmVon = mi.kmVon,
                                    kmBis = mi.kmBis,
                                    firmaId = targetFirmaId,
                                    worksiteNodeKey = mi.worksiteNodeKey,
                                    clientEntryKey = mi.clientEntryKey
                                )
                                setRawLines(lines)
                                onSave(etFull.text?.toString().orEmpty())
                            }

                            val btbId = loadedBtbId?.trim().orEmpty()
                            viewLifecycleOwner.lifecycleScope.launch {
                                if (photoIds.isNotEmpty() && btbId.isNotBlank()) {
                                    val photos = withContext(Dispatchers.IO) {
                                        ServiceLocator.fotoRepository.fotosMitIds(photoIds)
                                    }
                                    withContext(Dispatchers.IO) {
                                        val currentOverrides = BtbPhotoContextOverrideStore.load(requireContext(), btbId)
                                        photos.forEach { foto ->
                                            mi.clientEntryKey?.let { BtbPhotoMetadatenStore.bind(requireContext(), btbId, foto.id, it) }
                                            val previous = currentOverrides[foto.id]
                                            BtbPhotoContextOverrideStore.setOverride(
                                                requireContext(),
                                                btbId,
                                                foto.id,
                                                BtbPhotoContextOverrideStore.ContextOverride(
                                                    firma = targetFirma.orEmpty(),
                                                    firmaId = targetFirmaId,
                                                    objektKennung = previous?.objektKennung ?: foto.objektKennung,
                                                    objektBereich = previous?.objektBereich ?: foto.objektBereich,
                                                    ortId = targetOrt,
                                                    worksiteNodeKey = mi.worksiteNodeKey,
                                                    bildtyp = previous?.bildtyp ?: foto.bildtyp
                                                )
                                            )
                                        }
                                    }
                                }
                                saveAndReloadWholeBtbSilently()
                                Snackbar.make(requireView(), "Firma / Allgemein geändert", Snackbar.LENGTH_SHORT).show()
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }

                fun moveManualItemToSection(mi: ManualItem) {
                    val sourceSection = currentEditSection ?: return
                    val targets = BtbEditSection.values().filter { it != sourceSection }
                    val labels = targets.map { "${it.point}. ${it.label}" }.toTypedArray()

                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("BTB-Punkt ändern")
                        .setItems(labels) { _, which ->
                            val targetSection = targets[which]
                            val photoIds = matchingFotoIds(mi)

                            // Quelle entfernen.
                            val sourceLines = etFull.text?.toString().orEmpty().lines().toMutableList()
                            if (mi.lineIndex !in sourceLines.indices) return@setItems
                            sourceLines.removeAt(mi.lineIndex)
                            setRawLines(sourceLines.filter { it.isNotBlank() })
                            onSave(etFull.text?.toString().orEmpty())

                            // Derselbe fachliche Eintrag wandert mit derselben clientEntryKey in
                            // den Zielabschnitt. Das führt im Server1BtbApi zum section-PATCH,
                            // nicht zu Delete/Create.
                            val movedLine = buildManualLine(
                                ortId = mi.ortId,
                                firm = mi.firma,
                                time = mi.time,
                                text2 = mi.text,
                                kmVon = mi.kmVon,
                                kmBis = mi.kmBis,
                                firmaId = mi.firmaId,
                                worksiteNodeKey = mi.worksiteNodeKey,
                                clientEntryKey = mi.clientEntryKey
                            )
                            val targetRaw = rawForEditSection(targetSection)
                            val targetLines = targetRaw.lines()
                                .filter { it.isNotBlank() }
                                .toMutableList()
                                .apply { add(movedLine) }
                            setRawForEditSection(targetSection, targetLines.joinToString("\n"))
                            renderSectionPreviews()
                            scheduleSectionsAutosave(force = true)

                            // Zugeordnete Fotos behalten Datei, Aufnahmezeit, GPS und Foto-ID.
                            // Nur ihre BTB-Link-Metadaten/Punktzuordnung werden geändert.
                            val btbId = loadedBtbId?.trim().orEmpty()
                            viewLifecycleOwner.lifecycleScope.launch {
                                if (photoIds.isNotEmpty() && btbId.isNotBlank()) {
                                    val photos = withContext(Dispatchers.IO) {
                                        ServiceLocator.fotoRepository.fotosMitIds(photoIds)
                                    }
                                    withContext(Dispatchers.IO) {
                                        val currentOverrides =
                                            BtbPhotoContextOverrideStore.load(requireContext(), btbId)
                                        photos.forEach { foto ->
                                            mi.clientEntryKey?.let { BtbPhotoMetadatenStore.bind(requireContext(), btbId, foto.id, it) }
                                            val previous = currentOverrides[foto.id]
                                            BtbPhotoContextOverrideStore.setOverride(
                                                requireContext(),
                                                btbId,
                                                foto.id,
                                                BtbPhotoContextOverrideStore.ContextOverride(
                                                    firma = mi.firma.orEmpty(),
                                                    firmaId = mi.firmaId,
                                                    objektKennung = previous?.objektKennung ?: foto.objektKennung,
                                                    objektBereich = previous?.objektBereich ?: foto.objektBereich,
                                                    ortId = mi.ortId,
                                                    worksiteNodeKey = mi.worksiteNodeKey,
                                                    bildtyp = targetSection.photoType
                                                )
                                            )
                                        }
                                    }
                                }
                                saveAndReloadWholeBtbSilently()
                                Snackbar.make(
                                    requireView(),
                                    "Eintrag nach Punkt ${targetSection.point} verschoben",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }

                fun addManualBullet(mi: ManualItem) {
                    val line = formatManual(mi)
                    val relatedPhotoCount = matchingFotoIds(mi).size

                    val tv = android.widget.TextView(requireContext()).apply {
                        text = if (relatedPhotoCount > 0) "• $line   📷 $relatedPhotoCount" else "• $line"
                        setPadding(dp(10), dp(6), dp(10), dp(6))
                        textSize = 13f
                    }

                    tv.setOnClickListener {
                        val root = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(dp(8), dp(8), dp(8), 0)
                        }
                        val scroll = android.widget.ScrollView(requireContext()).apply {
                            isFillViewport = true
                            addView(root)
                        }

                        val editingBtbId = loadedBtbId
                        var selectedFirma: String? = mi.firma
                        var selectedFirmaId: String? = mi.firmaId
                        val companyChoices = (listOf<String?>(null, mi.firma) +
                            lastFirmaBlocks.filter { !it.isDeleted }.map { it.name } +
                            lastGroups.map { it.firma }).distinct()
                        root.addView(android.widget.TextView(requireContext()).apply { text = "Firma / Allgemein" })
                        val companySpinner = android.widget.Spinner(requireContext()).apply {
                            adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                                companyChoices.map { it ?: getString(R.string.btb_company_general) })
                            setSelection(companyChoices.indexOf(mi.firma).coerceAtLeast(0))
                            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                                    selectedFirma = companyChoices[position]
                                    selectedFirmaId = if (selectedFirma == mi.firma) mi.firmaId else lastFirmaBlocks.firstOrNull {
                                        !it.isDeleted && it.name.trim().equals(selectedFirma?.trim(), ignoreCase = true)
                                    }?.firmaId
                                }
                            }
                        }
                        root.addView(companySpinner)

                        // Ort / Unterbaustelle (edit)
                        var selectedOrtMarkerId: Long = mi.ortId
                        var cachedAltBaustelleId: Long? = null
                        var cachedHauptKnotenId: Long? = null

                        val rowOrt = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            setPadding(dp(4), dp(2), dp(4), dp(2))
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = dp(6) }
                        }

                        val tvOrt = android.widget.TextView(requireContext()).apply {
                            text = "Ort / Unterbaustelle: …"
                            textSize = 13f
                            isSingleLine = false
                            maxLines = 2
                            setLineSpacing(0f, 1.05f)
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        rowOrt.addView(tvOrt)

                        val btnOrt = android.widget.ImageButton(requireContext()).apply {
                            setImageResource(android.R.drawable.ic_input_add)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            contentDescription = "Ort / Unterbaustelle wählen"
                            layoutParams = android.widget.LinearLayout.LayoutParams(dp(32), dp(32))
                        }
                        rowOrt.addView(btnOrt)

                        root.addView(rowOrt)

                        suspend fun labelForOrt(markerId: Long): String {
                            if (markerId == 0L) return "Hauptbaustelle"
                            val k = ServiceLocator.baustellenBaumRepository.knotenMitId(markerId)
                            if (k == null) return "Ort $markerId"
                            val base = de.tbhelfer.streckenobjekte.BaustellenObjektLogik.unterbaustellenName(k).orEmpty()
                            val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
                                ?.let { " $it" }.orEmpty()
                            return base + km
                        }

                        fun refreshOrtLabel() {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val label = labelForOrt(selectedOrtMarkerId)
                                val title = "Ort / Unterbaustelle"
                                val s = "$title\n$label"
                                val sp = android.text.SpannableString(s)
                                sp.setSpan(android.text.style.ForegroundColorSpan(0xFF777777.toInt()), 0, title.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                sp.setSpan(android.text.style.RelativeSizeSpan(0.85f), 0, title.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), title.length + 1, sp.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                tvOrt.text = sp
                            }
                        }

                        // Initial aus globaler Auswahl lesen
                        viewLifecycleOwner.lifecycleScope.launch {
                            val altId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
                            var hauptId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
                            if (altId != null && hauptId == null) {
                                val haupt = ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(altId)
                                if (haupt != null) hauptId = haupt.id
                            }
                            cachedAltBaustelleId = altId
                            cachedHauptKnotenId = hauptId
                            refreshOrtLabel()
                        }

                        btnOrt.setOnClickListener {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val altId = cachedAltBaustelleId ?: ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
                                var hauptId = cachedHauptKnotenId ?: ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
                                if (altId == null) {
                                    android.widget.Toast.makeText(requireContext(), "Keine aktuelle Baustelle gewählt.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                if (hauptId == null) {
                                    val haupt = ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(altId)
                                    hauptId = haupt?.id
                                }
                                if (hauptId == null) {
                                    android.widget.Toast.makeText(requireContext(), "Keine Hauptbaustelle gefunden.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                cachedAltBaustelleId = altId
                                cachedHauptKnotenId = hauptId

                                val alle = ServiceLocator.baustellenBaumRepository.alleKnotenZuRoot(hauptId).first()
                                val childrenByParent = alle.filter { it.parentId != null }.groupBy { it.parentId!! }

                                fun disp(k: de.tbhelfer.datenbank.BaustellenKnotenEntity): String {
                                    val base = de.tbhelfer.streckenobjekte.BaustellenObjektLogik.unterbaustellenName(k).orEmpty()
                                    val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
                                        ?.let { " $it" }.orEmpty()
                                    return base + km
                                }

                                val entries = mutableListOf<Pair<Long, String>>()
                                entries.add(0L to "— Hauptbaustelle")

                                fun add(parentId: Long, level: Int) {
                                    val kids = childrenByParent[parentId].orEmpty().sortedBy { it.erstelltAmEpochMs }
                                    for (k in kids) {
                                        val prefix = buildString { repeat(level) { append("   ") }; append("› ") }
                                        entries.add(k.id to (prefix + disp(k)))
                                        add(k.id, level + 1)
                                    }
                                }
                                add(hauptId, 0)

                                val labels = entries.map { it.second }.toTypedArray()

                                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Unterbaustelle auswählen")
                                    .setItems(labels) { _, which ->
                                        val sel = entries[which].first
                                        when {
                                            sel == 0L -> {
                                                selectedOrtMarkerId = 0L
                                                refreshOrtLabel()
                                                viewLifecycleOwner.lifecycleScope.launch {
                                                    ServiceLocator.aktuelleBaustelleSpeicher.setzenNurHaupt(hauptKnotenId = hauptId, altBaustelleId = altId)
                                                }
                                            }
                                            else -> {
                                                selectedOrtMarkerId = sel
                                                refreshOrtLabel()
                                                viewLifecycleOwner.lifecycleScope.launch {
                                                    ServiceLocator.aktuelleBaustelleSpeicher.setzenKnoten(aktuellerKnotenId = sel, hauptKnotenId = hauptId, altBaustelleId = altId)
                                                }
                                            }
                                        }
                                    }
                                    .setNegativeButton("Abbrechen", null)
                                    .show()
                            }
                        }

                        val tvTimeLabel = android.widget.TextView(requireContext()).apply {
                            text = getString(R.string.btb_entry_time_hint)
                            setTextColor(0xFF666666.toInt())
                            textSize = 12.5f
                            setPadding(0, 0, 0, dp(2))
                        }
                        root.addView(tvTimeLabel)

                        val tilTime = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                            setHintEnabled(false)
                        }
                        val etTime = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                            hint = null
                            setText(mi.time.orEmpty())
                            setSelection(text?.length ?: 0)
                        }
                        tilTime.addView(etTime)
                        root.addView(tilTime)

                        val info = android.widget.TextView(requireContext()).apply {
                            text = getString(R.string.btb_entry_time_info)
                            setTextColor(0xFF666666.toInt())
                            textSize = 12.5f
                            setPadding(0, dp(6), 0, 0)
                        }
                        root.addView(info)
                        val kmRow = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            weightSum = 2f
                            setPadding(0, dp(8), 0, 0)
                        }
                        val tilKmVon = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                            hint = "KM von / KM"
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
                        }
                        val etKmVon = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                            setText(mi.kmVon?.let { String.format(java.util.Locale.US, "%.3f", it) }.orEmpty())
                            setSelectAllOnFocus(true)
                        }
                        tilKmVon.addView(etKmVon)
                        kmRow.addView(tilKmVon)
                        val tilKmBis = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                            hint = "KM bis (optional)"
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) }
                        }
                        val etKmBis = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                            setText(mi.kmBis?.let { String.format(java.util.Locale.US, "%.3f", it) }.orEmpty())
                            setSelectAllOnFocus(true)
                        }
                        tilKmBis.addView(etKmBis)
                        kmRow.addView(tilKmBis)
                        root.addView(kmRow)
                        root.addView(android.widget.TextView(requireContext()).apply {
                            text = "Leer lassen, wenn für den manuellen BTB-Eintrag keine KM bekannt ist."
                            setTextColor(0xFF666666.toInt())
                            textSize = 12.0f
                            setPadding(0, dp(3), 0, 0)
                        })


                        val tvTextLabel = android.widget.TextView(requireContext()).apply {
                            text = getString(R.string.btb_entry_text_hint)
                            setTextColor(0xFF666666.toInt())
                            textSize = 12.5f
                            setPadding(0, dp(8), 0, dp(2))
                        }
                        root.addView(tvTextLabel)

                        val tilText = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                            setHintEnabled(false)
                        }
                        val etText = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                            hint = getString(R.string.btb_entry_text_hint)
                            setText(mi.text)
                            setSelection(text?.length ?: 0)
                        }
                        tilText.addView(etText)
                        root.addView(tilText)

                        val photoHeader = android.widget.TextView(requireContext()).apply {
                            text = "Bilder zu diesem Eintrag"
                            textSize = 13f
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            setPadding(0, dp(12), 0, dp(4))
                        }
                        root.addView(photoHeader)

                        val photoScroller = android.widget.HorizontalScrollView(requireContext()).apply {
                            isFillViewport = false
                            isHorizontalScrollBarEnabled = true
                            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        }
                        val photoRow = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            setPadding(0, 0, dp(6), 0)
                        }
                        // HorizontalScrollView ist intern ein FrameLayout; addView erzeugt
                        // hier die passenden Child-LayoutParams automatisch.
                        photoScroller.addView(photoRow)
                        root.addView(
                            photoScroller,
                            android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        )

                        val relatedPhotoIds = matchingFotoIds(mi)
                        val btnEntryPhotos = com.google.android.material.button.MaterialButton(requireContext()).apply {
                            isAllCaps = false
                            text = if (relatedPhotoIds.isEmpty()) "Bild hinzufügen" else "Bilder (${relatedPhotoIds.size}) · hinzufügen"
                            setIconResource(R.drawable.ic_image_picker_24)
                        }
                        root.addView(
                            btnEntryPhotos,
                            android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = dp(6)
                                bottomMargin = dp(4)
                            }
                        )

                        if (relatedPhotoIds.isEmpty()) {
                            photoRow.addView(android.widget.TextView(requireContext()).apply {
                                text = "Noch keine Bilder direkt zu diesem Eintrag."
                                textSize = 12f
                                alpha = 0.70f
                                setPadding(dp(4), dp(2), dp(4), dp(6))
                            })
                        } else {
                            root.addView(android.widget.TextView(requireContext()).apply {
                                text = "← Bilder nach links / rechts wischen →"
                                textSize = 11.5f
                                alpha = 0.68f
                                gravity = android.view.Gravity.CENTER
                                setPadding(0, dp(2), 0, dp(2))
                            }, root.indexOfChild(photoScroller) + 1)

                            viewLifecycleOwner.lifecycleScope.launch {
                                val photos = withContext(Dispatchers.IO) {
                                    ServiceLocator.fotoRepository.fotosMitIds(relatedPhotoIds)
                                }.associateBy { it.id }
                                if (!isAdded) return@launch
                                photoRow.removeAllViews()

                                relatedPhotoIds.forEachIndexed { index, id ->
                                    val foto = photos[id] ?: return@forEachIndexed
                                    val cell = android.widget.LinearLayout(requireContext()).apply {
                                        orientation = android.widget.LinearLayout.VERTICAL
                                        gravity = android.view.Gravity.CENTER
                                        setPadding(dp(4), dp(3), dp(4), dp(3))
                                        layoutParams = android.widget.LinearLayout.LayoutParams(
                                            dp(240),
                                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                        ).apply {
                                            marginEnd = dp(8)
                                        }
                                    }
                                    val image = android.widget.ImageView(requireContext()).apply {
                                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                                        adjustViewBounds = false
                                        contentDescription = "Bild ${index + 1} öffnen"
                                        layoutParams = android.widget.LinearLayout.LayoutParams(
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                            dp(175)
                                        )
                                    }
                                    de.tbhelfer.util.ThumbnailLoader.loadInto(
                                        imageView = image,
                                        filePath = foto.dateipfad,
                                        placeholderResId = android.R.drawable.ic_menu_report_image,
                                        reqW = 960,
                                        reqH = 700
                                    )
                                    val number = index + 1
                                    image.setOnClickListener { showManualPhotoLarge(foto, number) }
                                    cell.addView(image)
                                    cell.addView(android.widget.TextView(requireContext()).apply {
                                        text = "Bild $number"
                                        textSize = 12f
                                        gravity = android.view.Gravity.CENTER
                                        setPadding(0, dp(3), 0, 0)
                                    })
                                    photoRow.addView(cell)
                                }
                            }
                        }

                        val firmTitle = mi.firma ?: getString(R.string.btb_company_general)

                        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.btb_entry_dialog_title, firmTitle))
                            .setView(scroll)
                            .setNegativeButton(R.string.action_cancel, null)
                            .setNeutralButton(R.string.action_delete) { _, _ ->
                                val lines = etFull.text?.toString().orEmpty().lines().toMutableList()
                                if (mi.lineIndex in lines.indices) {
                                    lines.removeAt(mi.lineIndex)
                                    setRawLines(lines)
                                }
                                renderMerged?.invoke(lastGroups)
                            }
                            .setPositiveButton(R.string.action_save, null)
                            .create()

                        fun updateTimeError() {
                            val raw = etTime.text?.toString().orEmpty().trim()
                            if (raw.isBlank()) {
                                tilTime.error = null
                                return
                            }
                            val norm = normalizeTimeOrSpan(raw)
                            tilTime.error = if (norm == null) getString(R.string.btb_entry_time_error) else null
                        }

                        etTime.addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                            override fun afterTextChanged(s: android.text.Editable?) { updateTimeError() }
                        })
                        updateTimeError()

                        btnEntryPhotos.setOnClickListener {
                            val textChanged = etText.text?.toString().orEmpty().trim() != mi.text.trim()
                            val timeChanged = etTime.text?.toString().orEmpty().trim().replace('-', '–') != mi.time.orEmpty().trim().replace('-', '–')
                            val kmVonChanged = parseKmValue(etKmVon.text?.toString()) != mi.kmVon
                            val kmBisChanged = parseKmValue(etKmBis.text?.toString()) != mi.kmBis
                            val ortChanged = selectedOrtMarkerId != mi.ortId || selectedFirma != mi.firma
                            if (textChanged || timeChanged || kmVonChanged || kmBisChanged || ortChanged) {
                                Snackbar.make(
                                    requireView(),
                                    "Bitte Änderungen zuerst speichern und den Eintrag danach erneut öffnen.",
                                    Snackbar.LENGTH_LONG
                                ).show()
                                return@setOnClickListener
                            }

                            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Bild hinzufügen")
                                .setItems(arrayOf("Kamera", "Galerie & Dateien")) { _, which ->
                                    dlg.dismiss()
                                    launchPhotoForManualItem(mi, if (which == 0) "camera" else "gallery")
                                }
                                .setNegativeButton("Abbrechen", null)
                                .show()
                        }

                        dlg.setOnShowListener {
                            val btn = dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                            btn.setOnClickListener {
                                if (loadedBtbId != editingBtbId) {
                                    Snackbar.make(requireView(), "BTB gewechselt – Eintrag bitte erneut öffnen", Snackbar.LENGTH_LONG).show()
                                    return@setOnClickListener
                                }
                                val txt = etText.text?.toString().orEmpty().trim()
                                if (txt.isBlank()) return@setOnClickListener

                                val tRaw = etTime.text?.toString().orEmpty().trim()
                                val tNorm = if (tRaw.isBlank()) null else normalizeTimeOrSpan(tRaw)
                                if (tRaw.isNotBlank() && tNorm == null) {
                                    tilTime.error = getString(R.string.btb_entry_time_error)
                                    return@setOnClickListener
                                }
                                tilTime.error = null

                                val kmVonRaw = etKmVon.text?.toString().orEmpty().trim()
                                val kmBisRaw = etKmBis.text?.toString().orEmpty().trim()
                                val kmVon = parseKmValue(kmVonRaw)
                                val kmBis = parseKmValue(kmBisRaw)
                                if (kmVonRaw.isNotBlank() && kmVon == null) {
                                    tilKmVon.error = "KM prüfen"
                                    return@setOnClickListener
                                }
                                if (kmBisRaw.isNotBlank() && kmBis == null) {
                                    tilKmBis.error = "KM prüfen"
                                    return@setOnClickListener
                                }
                                tilKmVon.error = null
                                tilKmBis.error = null

                                viewLifecycleOwner.lifecycleScope.launch {
                                    val rootId = cachedHauptKnotenId
                                        ?: ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
                                    val nodeKey = if (selectedOrtMarkerId > 0L) {
                                        stableNodeKey(rootId, selectedOrtMarkerId)
                                    } else null
                                    if (selectedOrtMarkerId > 0L && nodeKey == null) {
                                        Snackbar.make(
                                            requireView(),
                                            "Unterbaustelle konnte nicht stabil zugeordnet werden.",
                                            Snackbar.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }

                                    val photoIds = matchingFotoIds(mi)
                                    val lines = etFull.text?.toString().orEmpty().lines().toMutableList()
                                    if (mi.lineIndex in lines.indices) {
                                        lines[mi.lineIndex] = buildManualLine(
                                            ortId = selectedOrtMarkerId,
                                            firm = selectedFirma,
                                            time = tNorm,
                                            text2 = txt,
                                            kmVon = kmVon,
                                            kmBis = kmBis,
                                            firmaId = selectedFirmaId,
                                            worksiteNodeKey = nodeKey,
                                            clientEntryKey = mi.clientEntryKey
                                        )
                                        setRawLines(lines)
                                        // Apply the hidden editor to the report before buildEntry/save reloads it.
                                        onSave(etFull.text?.toString().orEmpty())
                                    }

                                    // Eine geänderte Unterbaustelle am Eintrag wird für bereits
                                    // direkt zugeordnete Fotos als BTB-Link-Kontext mitgeführt.
                                    val btbId = loadedBtbId?.trim().orEmpty()
                                    if (photoIds.isNotEmpty() && btbId.isNotBlank()) {
                                        val photos = withContext(Dispatchers.IO) {
                                            ServiceLocator.fotoRepository.fotosMitIds(photoIds)
                                        }
                                        withContext(Dispatchers.IO) {
                                            val currentOverrides =
                                                BtbPhotoContextOverrideStore.load(requireContext(), btbId)
                                            photos.forEach { foto ->
                                            mi.clientEntryKey?.let { BtbPhotoMetadatenStore.bind(requireContext(), btbId, foto.id, it) }
                                                val previous = currentOverrides[foto.id]
                                                BtbPhotoContextOverrideStore.setOverride(
                                                    requireContext(),
                                                    btbId,
                                                    foto.id,
                                                    BtbPhotoContextOverrideStore.ContextOverride(
                                                        firma = selectedFirma.orEmpty(),
                                                        firmaId = selectedFirmaId,
                                                        objektKennung = previous?.objektKennung ?: foto.objektKennung,
                                                        objektBereich = previous?.objektBereich ?: foto.objektBereich,
                                                        ortId = selectedOrtMarkerId,
                                                        worksiteNodeKey = nodeKey,
                                                        bildtyp = previous?.bildtyp ?: foto.bildtyp
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    renderMerged?.invoke(lastGroups)
                                    saveAndReloadWholeBtbSilently()
                                    dlg.dismiss()
                                }
                            }
                        }

                        dlg.show()
                    }

                    tv.setOnLongClickListener {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Eintrag bearbeiten")
                            .setItems(
                                arrayOf(
                                    "Firma / Allgemein ändern",
                                    "Punkt 4–8 ändern",
                                    "Löschen"
                                )
                            ) { _, which ->
                                when (which) {
                                    0 -> moveManualItem(mi)
                                    1 -> moveManualItemToSection(mi)
                                    2 -> confirmDeleteManualItem(mi)
                                }
                            }
                            .setNegativeButton("Abbrechen", null)
                            .show()
                        true
                    }

                    llPhoto.addView(tv)
                }

                fun addFotoPill(g: FotoGroup) {
                    fun kmPart(von: Double?, bis: Double?): String =
                        de.tbhelfer.util.KmAnzeige.bereich(von, bis, inKlammern = true)
                            ?.let { " $it" }.orEmpty()
                    val timePart = if (g.from == g.to) g.from else "${g.from}–${g.to}"
                    val core = "$timePart${kmPart(g.kmVon, g.kmBis)} ${g.text}"
                    val suffix = if (g.bildLabel.isNotBlank()) "   ${g.bildLabel}" else ""
                    val line = core + suffix

                    val tv = android.widget.TextView(requireContext()).apply {
                        text = "• $line"
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        setBackgroundResource(R.drawable.bg_pill)
                        textSize = 13f
                    }
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                    }
                    tv.layoutParams = lp

                    tv.setOnClickListener {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val ctx = resolveAktuelleBaustelle() ?: return@launch
                            val entry = buildEntry(ctx)

                            val overrides = withContext(Dispatchers.IO) {
                                BtbPhotoTextOverrideStore.loadOverrides(
                                    requireContext(),
                                    ctx.id,
                                    entry.datumIso,
                                    entry.startUhrzeit,
                                    entry.endeUhrzeit,
                                    selectedSchichtTyp
                                )
                            }
                            val startOverrides = withContext(Dispatchers.IO) {
                                BtbPhotoStartOverrideStore.loadOverrides(
                                    requireContext(),
                                    ctx.id,
                                    entry.datumIso,
                                    entry.startUhrzeit,
                                    entry.endeUhrzeit,
                                    selectedSchichtTyp
                                )
                            }
                            val kmOverrides = withContext(Dispatchers.IO) {
                                BtbPhotoKmOverrideStore.loadOverrides(
                                    requireContext(),
                                    ctx.id,
                                    entry.datumIso,
                                    entry.startUhrzeit,
                                    entry.endeUhrzeit,
                                    selectedSchichtTyp
                                )
                            }

                            val current = g.ids.firstOrNull()?.let { overrides[it] }?.takeIf { it.isNotBlank() } ?: g.text
                            val currentManualRange = g.ids.asSequence()
                                .mapNotNull { startOverrides[it] }
                                .map { it.trim() }
                                .firstOrNull { it.isNotBlank() }
                                .orEmpty()
                            val currentKmOverride = g.ids.asSequence().mapNotNull { kmOverrides[it] }.firstOrNull()
                            val currentKmVon = if (currentKmOverride != null) currentKmOverride.kmVon else g.kmVon
                            val currentKmBis = if (currentKmOverride != null) currentKmOverride.kmBis else g.kmBis
                            val currentKmVonText = currentKmVon?.let { String.format(java.util.Locale.US, "%.3f", it) }.orEmpty()
                            val currentKmBisText = currentKmBis?.let { String.format(java.util.Locale.US, "%.3f", it) }.orEmpty()
                            val showKmRange = currentKmBis != null && currentKmBisText.isNotBlank() && currentKmBisText != currentKmVonText

                            fun fieldLp(bottomDp: Int = 10) = android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = dp(bottomDp) }

                            val root = android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                setPadding(dp(4), dp(4), dp(4), dp(2))
                            }
                            val scroll = android.widget.ScrollView(requireContext()).apply {
                                isFillViewport = true
                                addView(root)
                            }

                            val info = android.widget.TextView(requireContext()).apply {
                                text = getString(R.string.btb_photo_override_info)
                                setTextColor(0xFF666666.toInt())
                                textSize = 12.5f
                                layoutParams = fieldLp(6)
                            }
                            root.addView(info)

                            // Correct only this BTB's photo links; original files remain untouched.
                            val contextRoot = ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(ctx.id)
                            val contextNodes = contextRoot?.let {
                                ServiceLocator.baustellenBaumRepository.alleKnotenZuRoot(it.id).first()
                            }.orEmpty().filter { it.id != contextRoot?.id }
                            val nodeChoices = listOf(0L to "Hauptbaustelle") + contextNodes.map {
                                it.id to de.tbhelfer.streckenobjekte.BaustellenObjektLogik.unterbaustellenName(it).orEmpty()
                            } + if (g.ortId > 0 && contextNodes.none { it.id == g.ortId }) listOf(g.ortId to "Ort ${g.ortId}") else emptyList()
                            val companyChoices = (listOf<String?>(null, g.firma) +
                                lastFirmaBlocks.filter { !it.isDeleted }.map { it.name } + lastGroups.map { it.firma }).distinct()
                            root.addView(android.widget.TextView(requireContext()).apply { text = "Firma / Allgemein" })
                            val photoCompany = android.widget.Spinner(requireContext()).apply {
                                adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                                    companyChoices.map { it ?: getString(R.string.btb_company_general) })
                                setSelection(companyChoices.indexOf(g.firma).coerceAtLeast(0))
                            }
                            root.addView(photoCompany)
                            root.addView(android.widget.TextView(requireContext()).apply { text = "Unterbaustelle" })
                            val photoNode = android.widget.Spinner(requireContext()).apply {
                                adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, nodeChoices.map { it.second })
                                setSelection(nodeChoices.indexOfFirst { it.first == g.ortId }.coerceAtLeast(0))
                            }
                            root.addView(photoNode)

                            val timeInfo = android.widget.TextView(requireContext()).apply {
                                text = "Zeit optional manuell. Erlaubt: 20:00 oder 20:00-23:00. Neue Bilder innerhalb bleiben unverändert. Frühere Bilder ziehen den Start vor, spätere Bilder verlängern das Ende automatisch."
                                setTextColor(0xFF666666.toInt())
                                textSize = 12.5f
                                layoutParams = fieldLp(10)
                            }
                            root.addView(timeInfo)

                            val tvTimeLabel = android.widget.TextView(requireContext()).apply {
                                text = "Zeit (optional)"
                                textSize = 13f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                layoutParams = fieldLp(4)
                            }
                            root.addView(tvTimeLabel)

                            val tilTime = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                                layoutParams = fieldLp()
                            }
                            val etTime = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                                hint = null
                                isSingleLine = true
                                setText(currentManualRange)
                                setSelection(text?.length ?: 0)
                            }
                            tilTime.addView(etTime)
                            root.addView(tilTime)

                            val kmInfo = android.widget.TextView(requireContext()).apply {
                                text = "KM-Änderung gilt nur für dieses BTB/PDF. Die KM der Originalfotos bleiben unverändert."
                                setTextColor(0xFF666666.toInt())
                                textSize = 12.5f
                                layoutParams = fieldLp(6)
                            }
                            root.addView(kmInfo)

                            val tvKmLabel = android.widget.TextView(requireContext()).apply {
                                text = if (showKmRange) "KM-Bereich (nur BTB)" else "KM (nur BTB)"
                                textSize = 13f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                layoutParams = fieldLp(4)
                            }
                            root.addView(tvKmLabel)

                            val tvKmVonFieldLabel = android.widget.TextView(requireContext()).apply {
                                text = if (showKmRange) "KM von" else "KM"
                                textSize = 12.5f
                                alpha = 0.75f
                                layoutParams = fieldLp(2)
                            }
                            root.addView(tvKmVonFieldLabel)

                            val tilKmVon = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                                layoutParams = fieldLp(if (showKmRange) 5 else 10)
                                setHintEnabled(false)
                            }
                            val etKmVon = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                                hint = null
                                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                                isSingleLine = true
                                setText(currentKmVonText)
                                setSelection(text?.length ?: 0)
                            }
                            tilKmVon.addView(etKmVon)
                            root.addView(tilKmVon)

                            val tvKmBisFieldLabel = android.widget.TextView(requireContext()).apply {
                                text = "KM bis"
                                textSize = 12.5f
                                alpha = 0.75f
                                layoutParams = fieldLp(2)
                                visibility = if (showKmRange) android.view.View.VISIBLE else android.view.View.GONE
                            }
                            root.addView(tvKmBisFieldLabel)

                            val tilKmBis = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                                layoutParams = fieldLp(10)
                                setHintEnabled(false)
                                visibility = if (showKmRange) android.view.View.VISIBLE else android.view.View.GONE
                            }
                            val etKmBis = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                                hint = null
                                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                                isSingleLine = true
                                setText(currentKmBisText)
                                setSelection(text?.length ?: 0)
                            }
                            tilKmBis.addView(etKmBis)
                            root.addView(tilKmBis)

                            val tvTextLabel = android.widget.TextView(requireContext()).apply {
                                text = getString(R.string.btb_photo_override_text_hint)
                                textSize = 13f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                layoutParams = fieldLp(4)
                            }
                            root.addView(tvTextLabel)

                            val til = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                                layoutParams = fieldLp(2)
                            }
                            val et = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                                isSingleLine = false
                                minLines = 2
                                setText(current)
                                setSelection(text?.length ?: 0)
                            }
                            til.addView(et)
                            root.addView(til)

                            val btnRemoveFromBtb = android.widget.Button(requireContext()).apply {
                                text = "Aus BTB entfernen"
                                isAllCaps = false
                                layoutParams = fieldLp(4)
                                setOnClickListener { excludePhotoGroupFromBtb(g) }
                            }
                            root.addView(btnRemoveFromBtb)

                            val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.btb_photo_override_title))
                                .setView(scroll)
                                .setNegativeButton(R.string.action_cancel, null)
                                .setNeutralButton(R.string.btb_photo_override_reset) { _, _ ->
                                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(getString(R.string.btb_confirm_reset_override_title))
                                        .setMessage(getString(R.string.btb_confirm_reset_override_msg))
                                        .setNegativeButton(R.string.action_cancel, null)
                                        .setPositiveButton(R.string.action_reset) { _, _ ->
                                            BtbPhotoTextOverrideStore.setOverrideForIds(
                                                requireContext(),
                                                ctx.id,
                                                entry.datumIso,
                                                entry.startUhrzeit,
                                                entry.endeUhrzeit,
                                                selectedSchichtTyp,
                                                g.ids,
                                                null
                                            )
                                            BtbPhotoStartOverrideStore.setOverrideForIds(
                                                requireContext(),
                                                ctx.id,
                                                entry.datumIso,
                                                entry.startUhrzeit,
                                                entry.endeUhrzeit,
                                                selectedSchichtTyp,
                                                g.ids,
                                                null
                                            )
                                            BtbPhotoKmOverrideStore.setOverrideForIds(
                                                requireContext(),
                                                ctx.id,
                                                entry.datumIso,
                                                entry.startUhrzeit,
                                                entry.endeUhrzeit,
                                                selectedSchichtTyp,
                                                g.ids,
                                                null
                                            )
                                            loadFotoGroups?.invoke()
                                        }
                                        .show()
                                }
                                .setPositiveButton(R.string.action_save, null)
                                .create()

                            fun normalizeManualPhotoTime(raw: String): String? {
                                val trimmed = raw.trim()
                                if (trimmed.isBlank()) return null
                                normalizeHm(trimmed)?.let { return it }
                                val match = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})$""").matchEntire(trimmed)
                                    ?: return null
                                val start = normalizeHm(match.groupValues[1]) ?: return null
                                val end = normalizeHm(match.groupValues[2]) ?: return null
                                return "$start–$end"
                            }

                            fun parseKmInput(raw: String): Double? = raw.trim()
                                .replace(',', '.')
                                .toDoubleOrNull()
                                ?.takeIf { it.isFinite() }

                            fun updateKmErrors(): Boolean {
                                val fromRaw = etKmVon.text?.toString().orEmpty().trim()
                                val toRaw = etKmBis.text?.toString().orEmpty().trim()
                                tilKmVon.error = null
                                tilKmBis.error = null

                                if (fromRaw.isBlank() && (!showKmRange || toRaw.isBlank())) return true
                                val von = parseKmInput(fromRaw)
                                if (von == null) {
                                    tilKmVon.error = "Bitte gültigen KM eingeben, z. B. 3.866"
                                    return false
                                }
                                if (showKmRange) {
                                    val bis = parseKmInput(toRaw)
                                    if (bis == null) {
                                        tilKmBis.error = "Bitte gültigen KM bis eingeben"
                                        return false
                                    }
                                }
                                return true
                            }

                            fun updateTimeError() {
                                val raw = etTime.text?.toString().orEmpty().trim()
                                if (raw.isBlank()) {
                                    tilTime.error = null
                                    return
                                }
                                tilTime.error = if (normalizeManualPhotoTime(raw) == null) getString(R.string.btb_entry_time_error) else null
                            }
                            etTime.addTextChangedListener(object : android.text.TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                override fun afterTextChanged(s: android.text.Editable?) { updateTimeError() }
                            })
                            updateTimeError()
                            val kmWatcher = object : android.text.TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                override fun afterTextChanged(s: android.text.Editable?) { updateKmErrors() }
                            }
                            etKmVon.addTextChangedListener(kmWatcher)
                            etKmBis.addTextChangedListener(kmWatcher)

                            dlg.setOnShowListener {
                                val btn = dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                                btn.setOnClickListener {
                                    if (loadedBtbId != entry.btbId) {
                                        Snackbar.make(requireView(), "BTB gewechselt – Foto bitte erneut öffnen", Snackbar.LENGTH_LONG).show()
                                        return@setOnClickListener
                                    }
                                    val newTxt = et.text?.toString().orEmpty().trim()
                                    val v: String? = newTxt.takeIf { it.isNotBlank() }
                                    val timeRaw = etTime.text?.toString().orEmpty().trim()
                                    val timeNorm = if (timeRaw.isBlank()) null else normalizeManualPhotoTime(timeRaw)
                                    if (timeRaw.isNotBlank() && timeNorm == null) {
                                        tilTime.error = getString(R.string.btb_entry_time_error)
                                        return@setOnClickListener
                                    }
                                    tilTime.error = null
                                    if (!updateKmErrors()) return@setOnClickListener

                                    val kmVonRaw = etKmVon.text?.toString().orEmpty().trim()
                                    val kmBisRaw = etKmBis.text?.toString().orEmpty().trim()
                                    val kmOverride = if (kmVonRaw.isBlank() && (!showKmRange || kmBisRaw.isBlank())) {
                                        null
                                    } else {
                                        val von = parseKmInput(kmVonRaw) ?: return@setOnClickListener
                                        val bis = if (showKmRange) parseKmInput(kmBisRaw) else null
                                        BtbPhotoKmOverrideStore.KmOverride(kmVon = von, kmBis = bis)
                                    }

                                    val targetFirma = companyChoices[photoCompany.selectedItemPosition]
                                    val targetNode = nodeChoices[photoNode.selectedItemPosition].first
                                    val contextChanged = targetFirma != g.firma || targetNode != g.ortId
                                    if (contextChanged) {
                                        btn.isEnabled = false
                                        viewLifecycleOwner.lifecycleScope.launch {
                                            try {
                                                val btbId = entry.btbId?.takeIf { it.isNotBlank() }
                                                    ?: error("BTB-Zuordnung fehlt")
                                                val nodeKey = if (targetNode > 0L) stableNodeKey(contextRoot?.id, targetNode) else null
                                                check(targetNode == 0L || nodeKey != null) { "Unterbaustelle konnte nicht stabil zugeordnet werden" }
                                                val firmId = lastFirmaBlocks.firstOrNull {
                                                    !it.isDeleted && it.name.trim().equals(targetFirma?.trim(), true)
                                                }?.firmaId
                                                val photos = ServiceLocator.fotoRepository.fotosMitIds(g.ids)
                                                withContext(Dispatchers.IO) {
                                                    val previous = BtbPhotoContextOverrideStore.load(requireContext(), btbId)
                                                    photos.forEach { photo ->
                                                        val old = previous[photo.id]
                                                        BtbPhotoContextOverrideStore.setOverride(requireContext(), btbId, photo.id,
                                                            BtbPhotoContextOverrideStore.ContextOverride(
                                                                firma = targetFirma.orEmpty(), firmaId = firmId,
                                                                objektKennung = old?.objektKennung ?: photo.objektKennung,
                                                                objektBereich = old?.objektBereich ?: photo.objektBereich,
                                                                ortId = targetNode, worksiteNodeKey = nodeKey,
                                                                bildtyp = old?.bildtyp ?: photo.bildtyp))
                                                        val match = BtbPhotoEntryMatcher.find(entry, photo.bildtyp, targetFirma,
                                                            nodeKey, photo.kurztext, firmId)
                                                        // A stale entry binding must never drag the corrected photo back.
                                                        BtbPhotoMetadatenStore.bind(requireContext(), btbId, photo.id, match)
                                                    }
                                                    BtbPhotoTextOverrideStore.setOverrideForIds(requireContext(), ctx.id, entry.datumIso,
                                                        entry.startUhrzeit, entry.endeUhrzeit, entry.schicht, g.ids, v)
                                                    BtbPhotoStartOverrideStore.setOverrideForIds(requireContext(), ctx.id, entry.datumIso,
                                                        entry.startUhrzeit, entry.endeUhrzeit, entry.schicht, g.ids, timeNorm)
                                                    BtbPhotoKmOverrideStore.setOverrideForIds(requireContext(), ctx.id, entry.datumIso,
                                                        entry.startUhrzeit, entry.endeUhrzeit, entry.schicht, g.ids, kmOverride)
                                                }
                                                de.tbhelfer.server.SofortSync.btbAnstossen(requireContext(), ctx.id, btbId)
                                                loadFotoGroups?.invoke()
                                                saveAndReloadWholeBtbSilently()
                                                dlg.dismiss()
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                Snackbar.make(requireView(), "Fotoänderung nicht vollständig gespeichert – bitte erneut prüfen", Snackbar.LENGTH_LONG).show()
                                            } finally { btn.isEnabled = true }
                                        }
                                        return@setOnClickListener
                                    }

                                    BtbPhotoTextOverrideStore.setOverrideForIds(
                                        requireContext(),
                                        ctx.id,
                                        entry.datumIso,
                                        entry.startUhrzeit,
                                        entry.endeUhrzeit,
                                        selectedSchichtTyp,
                                        g.ids,
                                        v
                                    )
                                    BtbPhotoStartOverrideStore.setOverrideForIds(
                                        requireContext(),
                                        ctx.id,
                                        entry.datumIso,
                                        entry.startUhrzeit,
                                        entry.endeUhrzeit,
                                        selectedSchichtTyp,
                                        g.ids,
                                        timeNorm
                                    )
                                    BtbPhotoKmOverrideStore.setOverrideForIds(
                                        requireContext(),
                                        ctx.id,
                                        entry.datumIso,
                                        entry.startUhrzeit,
                                        entry.endeUhrzeit,
                                        selectedSchichtTyp,
                                        g.ids,
                                        kmOverride
                                    )
                                    entry.btbId?.takeIf { it.isNotBlank() }?.let {
                                        de.tbhelfer.server.SofortSync.btbAnstossen(requireContext(), ctx.id, it)
                                    }
                                    loadFotoGroups?.invoke()
                                    saveAndReloadWholeBtbSilently()
                                    dlg.dismiss()
                                }
                            }
                            dlg.show()
                            KeyboardAutoScroll.install(dlg)
                        }
                    }

                    tv.setOnLongClickListener {
                        excludePhotoGroupFromBtb(g)
                        true
                    }

                    llPhoto.addView(tv)
                }


                var ortLabelCacheForHauptId: Long? = null
                var ortLabelCache: Map<Long, String> = mapOf(0L to "Hauptbaustelle")

                fun dispOrt(k: de.tbhelfer.datenbank.BaustellenKnotenEntity): String {
                    val base = de.tbhelfer.streckenobjekte.BaustellenObjektLogik.unterbaustellenName(k).orEmpty()
                    val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
                        ?.let { " $it" }.orEmpty()
                    return base + km
                }

                fun labelForOrtCached(id: Long): String = ortLabelCache[id] ?: if (id == 0L) "Hauptbaustelle" else "Ort $id"

                fun ensureOrtLabelsLoaded() {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val altId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
                        var hauptId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
                        if (altId != null && hauptId == null) {
                            val h = ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(altId)
                            hauptId = h?.id
                        }
                        if (hauptId == null) return@launch
                        if (ortLabelCacheForHauptId == hauptId && ortLabelCache.size > 1) return@launch
            
                        val alle = ServiceLocator.baustellenBaumRepository.alleKnotenZuRoot(hauptId).first()
                        val map = LinkedHashMap<Long, String>()
                        map[0L] = "Hauptbaustelle"
                        alle.forEach { k -> if (k.id != hauptId) map[k.id] = dispOrt(k) }
                        ortLabelCache = map
                        ortLabelCacheForHauptId = hauptId
            
                        if (dialog.isShowing) {
                            renderMerged?.invoke(lastGroups)
                        }
                    }
                }

                renderMerged = { groups: List<FotoGroup> ->
                    llPhoto.removeAllViews()
                    ensureOrtLabelsLoaded()
                    svPhoto.visibility = android.view.View.VISIBLE

                    // Firmen: Personal immer, Maschinen nur wenn im Abschnitt genutzt (und Firma als "Maschinen" markiert ist)
                    val personalFirms = lastFirmaBlocks
                        .asSequence()
                        .filter { !it.isDeleted && it.visibleInPersonal }
                        .map { it.name.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .toList()

                    val machineFirmSet = lastFirmaBlocks
                        .asSequence()
                        .filter { !it.isDeleted && it.visibleInMaschinen }
                        .map { it.name.trim() }
                        .filter { it.isNotBlank() }
                        .toSet()

                    // Alle Firmen, die in den aktuellen Foto-Gruppen tatsächlich vorkommen, müssen
                    // im Editor sichtbar sein. Vorher wurden nur Personal-/Maschinen-Firmen als Header
                    // angelegt. Dadurch konnten z. B. BBG-Fotoeinträge im PDF erscheinen, waren aber im
                    // BTB-Editor unsichtbar und damit nicht bearbeitbar.
                    val usedPhotoFirms = groups
                        .mapNotNull { it.firma?.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()

                    val usedMachineFirms = groups
                        .mapNotNull { it.firma?.trim() }
                        .filter { it.isNotBlank() && machineFirmSet.contains(it) && !personalFirms.contains(it) }
                        .distinct()

                    val headerFirms = LinkedHashSet<String>().apply {
                        // Erst Firmen mit echten Einträgen anzeigen, danach weitere bekannte Firmen zum Hinzufügen.
                        addAll(usedPhotoFirms)
                        addAll(personalFirms)
                        addAll(usedMachineFirms)
                    }.toList()

                    val knownFirms = headerFirms.toSet()

                    val manualItems = parseManualItems(etFull.text?.toString().orEmpty(), knownFirms)
                    val manualByFirm = manualItems.filter { it.firma != null }.groupBy { it.firma!! }
                    val manualNoFirm = manualItems.filter { it.firma == null }

                    val photosByFirm = groups.filter { !it.firma.isNullOrBlank() }.groupBy { it.firma!!.trim() }
                    val photosNoFirm = groups.filter { it.firma.isNullOrBlank() }

                    fun photoTimeMin(g: FotoGroup): Int? = hmToMin(g.from)

                    data class LineWrap(
                        val isPhoto: Boolean,
                        val timeMin: Int?,
                        val ortId: Long,
                        val seq: Int,
                        val manual: ManualItem? = null,
                        val foto: FotoGroup? = null
                    )

                    val lineComparator = compareBy<LineWrap> { if (it.timeMin == null) 0 else 1 }
                        .thenBy { it.timeMin ?: -1 }
                        .thenBy { if (it.isPhoto) 0 else 1 }
                        .thenBy { it.seq }

                    fun addManualEntryToText(firm: String?) {
                        val root = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(dp(8), dp(8), dp(8), 0)
                        }

                        // Ort / Unterbaustelle (global)
                        var selectedOrtMarkerId: Long = 0L
                        var cachedAltBaustelleId: Long? = null
                        var cachedHauptKnotenId: Long? = null

                        val rowOrt = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            setPadding(dp(4), dp(2), dp(4), dp(2))
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = dp(6) }
                        }

                        val tvOrt = android.widget.TextView(requireContext()).apply {
                            text = "Ort / Unterbaustelle: …"
                            textSize = 13f
                            isSingleLine = false
                            maxLines = 2
                            setLineSpacing(0f, 1.05f)
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        rowOrt.addView(tvOrt)

                        val btnOrt = android.widget.ImageButton(requireContext()).apply {
                            setImageResource(android.R.drawable.ic_input_add)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            contentDescription = "Ort / Unterbaustelle wählen"
                            layoutParams = android.widget.LinearLayout.LayoutParams(dp(32), dp(32))
                        }
                        rowOrt.addView(btnOrt)

                        root.addView(rowOrt)

                        suspend fun labelForOrt(markerId: Long): String {
                            if (markerId == 0L) return "Hauptbaustelle"
                            val k = ServiceLocator.baustellenBaumRepository.knotenMitId(markerId)
                            if (k == null) return "Ort $markerId"
                            val base = de.tbhelfer.streckenobjekte.BaustellenObjektLogik.unterbaustellenName(k).orEmpty()
                            val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
                                ?.let { " $it" }.orEmpty()
                            return base + km
                        }

                        fun refreshOrtLabel() {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val label = labelForOrt(selectedOrtMarkerId)
                                val title = "Ort / Unterbaustelle"
                                val s = "$title\n$label"
                                val sp = android.text.SpannableString(s)
                                sp.setSpan(android.text.style.ForegroundColorSpan(0xFF777777.toInt()), 0, title.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                sp.setSpan(android.text.style.RelativeSizeSpan(0.85f), 0, title.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), title.length + 1, sp.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                tvOrt.text = sp
                            }
                        }

                        // Initial aus globaler Auswahl lesen
                        viewLifecycleOwner.lifecycleScope.launch {
                            val altId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
                            var hauptId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
                            if (altId != null && hauptId == null) {
                                val haupt = ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(altId)
                                if (haupt != null) hauptId = haupt.id
                            }
                            cachedAltBaustelleId = altId
                            cachedHauptKnotenId = hauptId
                            val cur = ServiceLocator.aktuelleBaustelleSpeicher.aktuellerKnotenId.first()
                            selectedOrtMarkerId = if (cur != null && hauptId != null && cur != hauptId) cur else 0L
                            refreshOrtLabel()
                        }

                        btnOrt.setOnClickListener {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val altId = cachedAltBaustelleId ?: ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
                                var hauptId = cachedHauptKnotenId ?: ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
                                if (altId == null) {
                                    android.widget.Toast.makeText(requireContext(), "Keine aktuelle Baustelle gewählt.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                if (hauptId == null) {
                                    val haupt = ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(altId)
                                    hauptId = haupt?.id
                                }
                                if (hauptId == null) {
                                    android.widget.Toast.makeText(requireContext(), "Keine Hauptbaustelle gefunden.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                cachedAltBaustelleId = altId
                                cachedHauptKnotenId = hauptId

                                val alle = ServiceLocator.baustellenBaumRepository.alleKnotenZuRoot(hauptId).first()
                                val childrenByParent = alle.filter { it.parentId != null }.groupBy { it.parentId!! }

                                fun disp(k: de.tbhelfer.datenbank.BaustellenKnotenEntity): String {
                                    val base = de.tbhelfer.streckenobjekte.BaustellenObjektLogik.unterbaustellenName(k).orEmpty()
                                    val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
                                        ?.let { " $it" }.orEmpty()
                                    return base + km
                                }

                                val entries = mutableListOf<Pair<Long, String>>()
                                entries.add(0L to "— Hauptbaustelle")

                                fun add(parentId: Long, level: Int) {
                                    val kids = childrenByParent[parentId].orEmpty().sortedBy { it.erstelltAmEpochMs }
                                    for (k in kids) {
                                        val prefix = buildString { repeat(level) { append("   ") }; append("› ") }
                                        entries.add(k.id to (prefix + disp(k)))
                                        add(k.id, level + 1)
                                    }
                                }
                                add(hauptId, 0)

                                val labels = entries.map { it.second }.toTypedArray()

                                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Unterbaustelle auswählen")
                                    .setItems(labels) { _, which ->
                                        val sel = entries[which].first
                                        when {
                                            sel == 0L -> {
                                                selectedOrtMarkerId = 0L
                                                refreshOrtLabel()
                                                viewLifecycleOwner.lifecycleScope.launch {
                                                    ServiceLocator.aktuelleBaustelleSpeicher.setzenNurHaupt(hauptKnotenId = hauptId, altBaustelleId = altId)
                                                }
                                            }
                                            else -> {
                                                selectedOrtMarkerId = sel
                                                refreshOrtLabel()
                                                viewLifecycleOwner.lifecycleScope.launch {
                                                    ServiceLocator.aktuelleBaustelleSpeicher.setzenKnoten(aktuellerKnotenId = sel, hauptKnotenId = hauptId, altBaustelleId = altId)
                                                }
                                            }
                                        }
                                    }
                                    .setNegativeButton("Abbrechen", null)
                                    .show()
                            }
                        }

                        val tvTimeLabel = android.widget.TextView(requireContext()).apply {
                            text = getString(R.string.btb_entry_time_hint)
                            setTextColor(0xFF666666.toInt())
                            textSize = 12.5f
                            setPadding(0, 0, 0, dp(2))
                        }
                        root.addView(tvTimeLabel)

                        val tilTime = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                            setHintEnabled(false)
                        }
                        val etTime = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                            hint = null
                            setText("")
                        }
                        tilTime.addView(etTime)
                        root.addView(tilTime)

                        val info = android.widget.TextView(requireContext()).apply {
                            text = getString(R.string.btb_entry_time_info)
                            setTextColor(0xFF666666.toInt())
                            textSize = 12.5f
                            setPadding(0, dp(6), 0, 0)
                        }
                        root.addView(info)
                        val kmRow = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            weightSum = 2f
                            setPadding(0, dp(8), 0, 0)
                        }
                        val tilKmVon = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                            hint = "KM von / KM"
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
                        }
                        val etKmVon = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                            setText("")
                            setSelectAllOnFocus(true)
                        }
                        tilKmVon.addView(etKmVon)
                        kmRow.addView(tilKmVon)
                        val tilKmBis = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                            hint = "KM bis (optional)"
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) }
                        }
                        val etKmBis = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                            setText("")
                            setSelectAllOnFocus(true)
                        }
                        tilKmBis.addView(etKmBis)
                        kmRow.addView(tilKmBis)
                        root.addView(kmRow)
                        root.addView(android.widget.TextView(requireContext()).apply {
                            text = "Leer lassen, wenn für den manuellen BTB-Eintrag keine KM bekannt ist."
                            setTextColor(0xFF666666.toInt())
                            textSize = 12.0f
                            setPadding(0, dp(3), 0, 0)
                        })


                        val tvTextLabel = android.widget.TextView(requireContext()).apply {
                            text = getString(R.string.btb_entry_text_hint)
                            setTextColor(0xFF666666.toInt())
                            textSize = 12.5f
                            setPadding(0, dp(8), 0, dp(2))
                        }
                        root.addView(tvTextLabel)

                        val tilText = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                            setHintEnabled(false)
                        }
                        val etText = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                            hint = getString(R.string.btb_entry_text_hint)
                        }
                        tilText.addView(etText)
                        root.addView(tilText)

                        val firmTitle = firm ?: getString(R.string.btb_company_general)

                        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.btb_entry_dialog_title, firmTitle))
                            .setView(root)
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.action_add, null)
                            .create()

                        fun updateTimeError() {
                            val raw = etTime.text?.toString().orEmpty().trim()
                            if (raw.isBlank()) {
                                tilTime.error = null
                                return
                            }
                            val norm = normalizeTimeOrSpan(raw)
                            tilTime.error = if (norm == null) getString(R.string.btb_entry_time_error) else null
                        }

                        etTime.addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                            override fun afterTextChanged(s: android.text.Editable?) { updateTimeError() }
                        })
                        updateTimeError()

                        dlg.setOnShowListener {
                            val btn = dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                            btn.setOnClickListener {
                                val txt = etText.text?.toString().orEmpty().trim()
                                if (txt.isBlank()) return@setOnClickListener

                                val tRaw = etTime.text?.toString().orEmpty().trim()
                                val tNorm = if (tRaw.isBlank()) null else normalizeTimeOrSpan(tRaw)
                                if (tRaw.isNotBlank() && tNorm == null) {
                                    tilTime.error = getString(R.string.btb_entry_time_error)
                                    return@setOnClickListener
                                }
                                tilTime.error = null

                                val kmVonRaw = etKmVon.text?.toString().orEmpty().trim()
                                val kmBisRaw = etKmBis.text?.toString().orEmpty().trim()
                                val kmVon = parseKmValue(kmVonRaw)
                                val kmBis = parseKmValue(kmBisRaw)
                                if (kmVonRaw.isNotBlank() && kmVon == null) {
                                    tilKmVon.error = "KM prüfen"
                                    return@setOnClickListener
                                }
                                if (kmBisRaw.isNotBlank() && kmBis == null) {
                                    tilKmBis.error = "KM prüfen"
                                    return@setOnClickListener
                                }
                                tilKmVon.error = null
                                tilKmBis.error = null

                                viewLifecycleOwner.lifecycleScope.launch {
                                    val rootId = cachedHauptKnotenId
                                        ?: ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
                                    val nodeKey = if (selectedOrtMarkerId > 0L) {
                                        stableNodeKey(rootId, selectedOrtMarkerId)
                                    } else null
                                    if (selectedOrtMarkerId > 0L && nodeKey == null) {
                                        Snackbar.make(
                                            requireView(),
                                            "Unterbaustelle konnte nicht stabil zugeordnet werden.",
                                            Snackbar.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }
                                    val firmId = firm?.let { name ->
                                        lastFirmaBlocks.firstOrNull {
                                            !it.isDeleted && it.name.trim().equals(name.trim(), ignoreCase = true)
                                        }?.firmaId
                                    }
                                    val line = buildManualLine(
                                        ortId = selectedOrtMarkerId,
                                        firm = firm,
                                        time = tNorm,
                                        text2 = txt,
                                        kmVon = kmVon,
                                        kmBis = kmBis,
                                        firmaId = firmId,
                                        worksiteNodeKey = nodeKey
                                    )

                                    val curLines = etFull.text?.toString().orEmpty().lines().toMutableList()
                                    val base = if (curLines.size == 1 && curLines.first().isBlank()) mutableListOf<String>() else curLines
                                    val nextLines = (base + line).toMutableList()
                                    setRawLines(nextLines.filter { it.isNotBlank() })

                                    renderMerged?.invoke(lastGroups)
                                    dlg.dismiss()
                                }
                            }
                        }

                        dlg.show()
                    }

                    fun removeManualEntries(firm: String?, manual: List<ManualItem>) {
                        val titleName = firm ?: getString(R.string.btb_company_general)
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.btb_confirm_remove_manual_title))
                            .setMessage(getString(R.string.btb_confirm_remove_manual_msg, titleName))
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.action_delete) { _, _ ->
                                val lines = etFull.text?.toString().orEmpty().lines()
                                val removeIdx = mutableSetOf<Int>()
                                manual.forEach { mi ->
                                    removeIdx.add(mi.lineIndex)
                                }
                                val kept = lines.filterIndexed { i, _ -> !removeIdx.contains(i) }.filter { it.isNotBlank() }
                                setRawLines(kept)
                                renderMerged?.invoke(lastGroups)
                            }
                            .show()
                    }

                    fun addOrtSubHeader(ortId: Long) {
                        val tv = android.widget.TextView(requireContext()).apply {
                            text = labelForOrtCached(ortId)
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            textSize = 12.5f
                            setTextColor(0xFF333333.toInt())
                            setPadding(dp(22), dp(4), dp(10), dp(2))
                        }
                        llPhoto.addView(tv)
                    }

                    fun renderFirmBlock(firm: String?, headerText: String, manual: List<ManualItem>, fotos: List<FotoGroup>) {
                        val onRemove = if (manual.isNotEmpty()) ({ removeManualEntries(firm, manual) }) else null

                        addHeaderRow(
                            headerText,
                            onAdd = { addManualEntryToText(firm) },
                            onRemoveManual = onRemove
                        )

                        val byOrt = linkedMapOf<Long, MutableList<LineWrap>>()
                        var seq = 0
                    
                        manual.forEach { mi ->
                            val list = byOrt.getOrPut(mi.ortId) { mutableListOf() }
                            list.add(LineWrap(isPhoto = false, timeMin = mi.timeMin, ortId = mi.ortId, seq = seq++, manual = mi))
                        }
                        fotos.forEach { fg ->
                            val list = byOrt.getOrPut(fg.ortId) { mutableListOf() }
                            list.add(LineWrap(isPhoto = true, timeMin = photoTimeMin(fg), ortId = fg.ortId, seq = seq++, foto = fg))
                        }
                    
                        val showOrtHeaders = byOrt.isNotEmpty()
                    
                        val orderedOrte = byOrt.entries.sortedWith(
                            compareBy<Map.Entry<Long, MutableList<LineWrap>>> { if (it.key == 0L) 0 else 1 }
                                .thenBy { e -> e.value.mapNotNull { it.timeMin }.minOrNull() ?: Int.MAX_VALUE }
                        )
                    
                        orderedOrte.forEach { (ortId, list) ->
                            if (showOrtHeaders) addOrtSubHeader(ortId)
                            list.sortedWith(lineComparator).forEach { w ->
                                if (w.isPhoto) addFotoPill(w.foto!!)
                                else addManualBullet(w.manual!!)
                            }
                        }
                    }

                    // Allgemein immer zuerst
                    renderFirmBlock(
                        firm = null,
                        headerText = getString(R.string.btb_company_general),
                        manual = manualNoFirm,
                        fotos = photosNoFirm
                    )

                    // Danach Firmen
                    headerFirms.forEach { firm ->
                        renderFirmBlock(
                            firm = firm,
                            headerText = firm,
                            manual = manualByFirm[firm].orEmpty(),
                            fotos = photosByFirm[firm].orEmpty()
                        )
                    }
                }

    // Spiegel-Ansicht: wenn manuell editiert wird, Liste neu rendern (ohne Fotos neu zu laden)
                etFull.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) {
                        if (dialog.isShowing) {
                            renderMerged?.invoke(lastGroups)
                        }
                    }
                })

    // Lädt Foto-Gruppen und rendert sie in den Dialog
                loadFotoGroups = {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val ctx = resolveAktuelleBaustelle() ?: run {
                            renderMerged?.invoke(emptyList())
                            return@launch
                        }

                        // buildEntry liest UI-Werte -> Main Thread
                        val entry = buildEntry(ctx)
                        lastFirmaBlocks = entry.firmen
                        val (fromMs, toMs) = shiftWindowEpochMs(selectedDate, entry.startUhrzeit, entry.endeUhrzeit)

                        val groups = withContext(Dispatchers.IO) {
                            val overrides = BtbPhotoTextOverrideStore.loadOverrides(
                                requireContext(),
                                ctx.id,
                                entry.datumIso,
                                entry.startUhrzeit,
                                entry.endeUhrzeit,
                                selectedSchichtTyp
                            )
                            val startOverrides = BtbPhotoStartOverrideStore.loadOverrides(
                                requireContext(),
                                ctx.id,
                                entry.datumIso,
                                entry.startUhrzeit,
                                entry.endeUhrzeit,
                                selectedSchichtTyp
                            )
                            val kmOverrides = BtbPhotoKmOverrideStore.loadOverrides(
                                requireContext(),
                                ctx.id,
                                entry.datumIso,
                                entry.startUhrzeit,
                                entry.endeUhrzeit,
                                selectedSchichtTyp
                            )

                            val all = runCatching { ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(ctx.id) }.getOrDefault(emptyList())

                            // Exclude-Store (Default = alle drin, persistiert werden nur abgewählte IDs)
                            val excludedByType = BtbPhotoSelectionStore.loadExcluded(
                                requireContext(),
                                ctx.id,
                                entry.datumIso,
                                entry.startUhrzeit,
                                entry.endeUhrzeit,
                                selectedSchichtTyp
                            )

                            // Alle ausgewählten Fotos der Schicht (über alle Typen), damit Bildnummern wie im PDF sind
                            val inWindow = filterFotosForBtbStart(
                                all.asSequence()
                                    .filter { it.erstelltAmEpochMs in fromMs until toMs }
                                    .filter { f ->
                                        val ex = excludedByType[f.bildtyp.name]
                                        ex == null || !ex.contains(f.id)
                                    }
                                    .toList(),
                                entry.erstelltAmEpochMs
                            )

                            val byType = inWindow.groupBy { it.bildtyp }

                            val anhangOrder = listOf(
                                de.tbhelfer.datenbank.Bildtyp.ABNAHMEN,
                                de.tbhelfer.datenbank.Bildtyp.EINWEISUNG_UNTERWEISUNG,
                                de.tbhelfer.datenbank.Bildtyp.BESONDERHEITEN,
                                de.tbhelfer.datenbank.Bildtyp.MAENGEL_SCHAEDEN,
                                de.tbhelfer.datenbank.Bildtyp.FOTODOKUMENTATION
                            )

                            val maxFotos = entry.maxFotosInPdf.coerceAtLeast(1)
                            val selectedOrdered = anhangOrder
                                .flatMap { typ -> byType[typ].orEmpty().sortedBy { it.erstelltAmEpochMs } }
                                .take(maxFotos)

                            val fotoNum = LinkedHashMap<Long, Int>().apply {
                                var n = 1
                                selectedOrdered.forEach { f ->
                                    this[f.id] = n
                                    n += 1
                                }
                            }

                            val relevant = inWindow
                                .asSequence()
                                .filter { it.bildtyp == fotoType }
                                .toList()

                            val out = ArrayList<FotoGroup>()

                            data class FotoKey(val firm: String?, val txt: String, val ortId: Long)
                        
                            val grouped = relevant.groupBy {
                                val firm = it.firma.trim().takeIf { s -> s.isNotBlank() }
                                val txt = run {
                                    val ov = overrides[it.id]?.trim().orEmpty()
                                    if (ov.isNotBlank()) ov else effectiveText(it)
                                }
                                val ortId = it.baustellenKnotenId ?: 0L
                                FotoKey(firm, txt, ortId)
                            }
                        
                            // Gruppierung über den ganzen Tag: Firma + Text (Override zählt als Text)
                            // Gruppierung über den ganzen Tag: Firma + Text + Ort (Override zählt als Text)
                            grouped.entries.forEach { (k, list) ->
                                val firm = k.firm
                                val txt = k.txt
                                val ortId = k.ortId
                                if (txt.isBlank()) return@forEach
                                val sorted = list.sortedBy { it.erstelltAmEpochMs }
                                val first = sorted.first()
                                val last = sorted.last()
                                val nums = sorted.mapNotNull { fotoNum[it.id] }
                        
                                val ids = sorted.map { it.id }
                                val autoStartEpoch = first.erstelltAmEpochMs
                                val autoEndEpoch = last.erstelltAmEpochMs
                                val manualRanges = ids.mapNotNull { startOverrides[it] }
                                    .mapNotNull { parsePhotoTimeOverride(it) }
                                val manualStartEpoch = manualRanges.mapNotNull { it.first?.let { min -> wallTimeEpochNear(autoStartEpoch, min) } }.minOrNull()
                                val manualEndEpoch = manualRanges.mapNotNull { it.second?.let { min -> wallTimeEpochNear(autoEndEpoch, min) } }.maxOrNull()
                                val effectiveStartEpoch = manualStartEpoch?.let { minOf(it, autoStartEpoch) } ?: autoStartEpoch
                                val effectiveEndEpoch = manualEndEpoch?.let { maxOf(it, autoEndEpoch) } ?: autoEndEpoch

                                val manualKm = ids.asSequence().mapNotNull { kmOverrides[it] }.firstOrNull()
                                val autoKmValues = sorted.mapNotNull { it.km }
                                val autoKmVon = autoKmValues.minOrNull()
                                val autoKmBis = autoKmValues.maxOrNull()?.takeIf { maxKm ->
                                    autoKmVon != null && String.format(java.util.Locale.US, "%.3f", maxKm) != String.format(java.util.Locale.US, "%.3f", autoKmVon)
                                }
                                val effectiveKmVon = if (manualKm != null) manualKm.kmVon else autoKmVon
                                val effectiveKmBis = if (manualKm != null) manualKm.kmBis else autoKmBis

                                out.add(
                                    FotoGroup(
                                        firma = firm,
                                        text = txt,
                                        ortId = ortId,
                                        from = timeOf(effectiveStartEpoch),
                                        to = timeOf(effectiveEndEpoch),
                                        kmVon = effectiveKmVon,
                                        kmBis = effectiveKmBis,
                                        kmManual = manualKm != null,
                                        bildLabel = bildLabel(nums),
                                        ids = ids,
                                        sortEpochMs = effectiveStartEpoch
                                    )
                                )
                            }
                        
                                out.sortedWith(
                                    compareBy<FotoGroup> { it.sortEpochMs }
                                        .thenBy { it.firma.orEmpty() }
                                        .thenBy { it.text }
                                )
                        }

                        lastGroups = groups

                        if (dialog.isShowing) {
                            renderMerged?.invoke(groups)
                        }
                    }
                }

                loadFotoGroups?.invoke()

                dialog.setOnDismissListener {
                    // Cleanup: Liste leeren (verhindert Leaks auf manchen Geräten)
                    llPhoto.removeAllViews()
                }
            }

            btnCancel.setOnClickListener { dialog.dismiss() }
            btnSave.setOnClickListener {
                onSave(etFull.text?.toString().orEmpty())
                dialog.dismiss()
                saveAndReloadWholeBtbSilently()
            }

            // Größeres Fenster (ca. 80% Displayhöhe)
            dialog.setOnShowListener {
                val dm = resources.displayMetrics
                val w = (dm.widthPixels * 0.95f).toInt()
                val h = (dm.heightPixels * 0.80f).toInt()
                dialog.window?.setLayout(w, h)
            }

            dialog.show()
        }

        private fun setupSectionAutosaveListeners() {
            fun hook(et: TextInputEditText) {
                et.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        if (!sectionsApplying) scheduleSectionsAutosave()
                    }
                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                })
            }
            hook(etArbeiten)
            hook(etAbnahmen)
            hook(etEinweisung)
            hook(etBesonderheiten)
            hook(etMaengel)
        }

    
        private fun beginContextSwitch() {
            isSwitchingContext = true
            sectionsAutosaveJob?.cancel()
            wetterAutosaveJob?.cancel()
            pmAutosaveJob?.cancel()
            sectionsDirtySinceMs = null
            wetterDirtySinceMs = null
            pmDirtySinceMs = null
        }

        private fun endContextSwitch() {
            isSwitchingContext = false
        }

        /**
         * UX: Wenn der Nutzer gerade eben noch etwas geändert hat, vor einem Datum/Schicht-Wechsel kurz nachfragen.
         * Auto-Save läuft ohnehin – der Dialog nimmt nur Unsicherheit raus.
         */
        private fun confirmContextSwitchIfRecent(onContinue: () -> Unit) {
            val now = System.currentTimeMillis()
            val delta = now - lastUserEditAtMs
            if (lastUserEditAtMs > 0L && delta in 0..5_000L) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Wechseln?")
                    .setMessage("Änderungen sind auto-gespeichert. Trotzdem wechseln?")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Wechseln") { _, _ -> onContinue() }
                    .show()
            } else {
                onContinue()
            }
        }

        private fun rememberDirtySince(current: Long?, now: Long): Long = current ?: now

        private fun computeAutosaveDelayMs(firstDirtyMs: Long, force: Boolean): Long {
            if (force) return 0L
            val elapsed = System.currentTimeMillis() - firstDirtyMs
            val maxRemaining = (autosaveMaxDelayMs - elapsed).coerceAtLeast(0L)
            return minOf(autosaveIdleDelayMs, maxRemaining)
        }

    private fun scheduleSectionsAutosave(force: Boolean = false) {
            if (sectionsApplying) return
            if (isSwitchingContext) return
            val now = System.currentTimeMillis()
            lastUserEditAtMs = now
            sectionsDirtySinceMs = rememberDirtySince(sectionsDirtySinceMs, now)
            setStatus("Speichert…")
            sectionsAutosaveJob?.cancel()
            sectionsAutosaveJob = lifecycleScope.launch {
                val firstDirtyMs = sectionsDirtySinceMs ?: System.currentTimeMillis()
                val delayMs = computeAutosaveDelayMs(firstDirtyMs, force)
                if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)
                val ctx = resolveAktuelleBaustelle() ?: return@launch
                val entry = buildEntry(ctx)
                withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.speichern(requireContext(), entry)
                }
                sectionsDirtySinceMs = null
                cachedStatusBaustelleId = entry.baustelleId
                lastSavedEpochMs = entry.geaendertAmEpochMs
                scheduleUpdateCurrentReportBanner()
                // Status (Abgeschlossen/Geändert) sofort aktualisieren, wenn Baseline existiert
                val bi = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
                baselineExists = bi.exists
                changedSinceBaseline = bi.changedSinceBaseline
                baselineLastModifiedEpochMs = bi.baselineLastModifiedEpochMs
                updateDateTimeButton()
                setStatus("Auto-gespeichert")
            }
        }

        /** Debounced Autosave nach Änderungen im Wetterblock (manuelle Auswahl/Reset). */
        private fun scheduleWetterAutosave(force: Boolean = false) {
            if (wetterApplying) return
            if (isSwitchingContext) return
            val now = System.currentTimeMillis()
            lastUserEditAtMs = now
            wetterDirtySinceMs = rememberDirtySince(wetterDirtySinceMs, now)
            setStatus("Speichert…")
            wetterAutosaveJob?.cancel()
            wetterAutosaveJob = lifecycleScope.launch {
                val firstDirtyMs = wetterDirtySinceMs ?: System.currentTimeMillis()
                val delayMs = computeAutosaveDelayMs(firstDirtyMs, force)
                if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)
                val ctx = resolveAktuelleBaustelle() ?: return@launch
                val entry = buildEntry(ctx)
                withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.speichern(requireContext(), entry)
                }
                wetterDirtySinceMs = null
                cachedStatusBaustelleId = entry.baustelleId
                lastSavedEpochMs = entry.geaendertAmEpochMs
                scheduleUpdateCurrentReportBanner()
                val bi = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
                baselineExists = bi.exists
                changedSinceBaseline = bi.changedSinceBaseline
                baselineLastModifiedEpochMs = bi.baselineLastModifiedEpochMs
                updateDateTimeButton()
                setStatus("Auto-gespeichert")
            }
        }

        /** Debounced Autosave nach Änderungen in Personal/Geräte. */
        private fun schedulePmAutosave(force: Boolean = false) {
            if (pmApplying) return
            if (isSwitchingContext) return
            val now = System.currentTimeMillis()
            lastUserEditAtMs = now
            pmDirtySinceMs = rememberDirtySince(pmDirtySinceMs, now)
            setStatus("Speichert…")
            pmAutosaveJob?.cancel()
            pmAutosaveJob = lifecycleScope.launch {
                val firstDirtyMs = pmDirtySinceMs ?: System.currentTimeMillis()
                val delayMs = computeAutosaveDelayMs(firstDirtyMs, force)
                if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)
                val ctx = resolveAktuelleBaustelle() ?: return@launch
                val entry = buildEntry(ctx)
                withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.speichern(requireContext(), entry)
                }
                pmDirtySinceMs = null
                cachedStatusBaustelleId = entry.baustelleId
                lastSavedEpochMs = entry.geaendertAmEpochMs
                scheduleUpdateCurrentReportBanner()
                val bi = withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.baselineInfo(requireContext(), ctx.id, ctx.name, selectedDate, selectedSchichtTyp)
                }
                baselineExists = bi.exists
                changedSinceBaseline = bi.changedSinceBaseline
                baselineLastModifiedEpochMs = bi.baselineLastModifiedEpochMs
                updateDateTimeButton()
                setStatus("Auto-gespeichert")
            }
        }

        private fun applyLegacyPmFromText() {
            pmApplying = true
            try {
            // Fallback: alte Einträge hatten Personal/Geräte als Textlisten.
            // Wir bauen daraus Firmenblöcke – Personal und Maschinen getrennt (Firmen können unterschiedlich sein).

            val personalLines = etPersonal.text?.toString().orEmpty().lines().map { it.trim() }.filter { it.isNotBlank() }
            val maschinenLines = etGeraete.text?.toString().orEmpty().lines().map { it.trim() }.filter { it.isNotBlank() }
            val personalBlocks = legacyLinesToBlocks(personalLines)
            val maschinenBlocks = legacyLinesToBlocks(maschinenLines)

            clearPmAll()

            for (b in personalBlocks) {
                val id = java.util.UUID.randomUUID().toString()
                addFirmaBlock(PmSection.PERSONAL, firmaId = id, prefillFirma = b.firma)
                val ui = personalUis.lastOrNull() ?: continue
                ui.rowsContainer.removeAllViews(); ui.rows.clear()
                b.rows.forEach { r -> addPairRow(ui, PmSection.PERSONAL, prefillAnzahl = r.first, prefillBez = r.second) }
                if (b.rows.isEmpty()) addPairRow(ui, PmSection.PERSONAL, null, null)
            }

            for (b in maschinenBlocks) {
                val id = java.util.UUID.randomUUID().toString()
                addFirmaBlock(PmSection.MASCHINEN, firmaId = id, prefillFirma = b.firma)
                val ui = maschinenUis.lastOrNull() ?: continue
                ui.rowsContainer.removeAllViews(); ui.rows.clear()
                b.rows.forEach { r -> addPairRow(ui, PmSection.MASCHINEN, prefillAnzahl = r.first, prefillBez = r.second) }
                if (b.rows.isEmpty()) addPairRow(ui, PmSection.MASCHINEN, null, null)
            }

                updatePmPreviews()
            } finally {
                pmApplying = false
            }
        }

        private data class BlockModel(val firma: String, val rows: List<Pair<String?, String?>>)

        private fun legacyLinesToBlocks(lines: List<String>): List<BlockModel> {
            if (lines.isEmpty()) return emptyList()

            val map = linkedMapOf<String, MutableList<Pair<String?, String?>>>()
            val splitRegex = Regex("""^\s*([^:;\-]+)\s*[:;\-]\s*(.+)$""")
            val rowRegex = Regex("""^\s*(\d+)\s*(x)?\s*(.*)$""")

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isBlank()) continue

                val m1 = splitRegex.find(trimmed)
                val firma = m1?.groupValues?.get(1)?.trim() ?: trimmed
                val rest = m1?.groupValues?.get(2)?.trim()

                val (anzahl, bez) = if (rest.isNullOrBlank()) {
                    null to null
                } else {
                    val m2 = rowRegex.find(rest)
                    if (m2 != null) {
                        val a = m2.groupValues[1].trim()
                        val b = m2.groupValues[3].trim().ifBlank { null }
                        a to b
                    } else {
                        null to rest
                    }
                }

                val list = map.getOrPut(firma) { mutableListOf() }
                if (!rest.isNullOrBlank()) list.add(anzahl to bez)
            }

            return map.map { (firma, rows) -> BlockModel(firma, rows.toList()) }
        }

        private fun syncPmBlocksToLegacyText() {
            fun blocksToLines(uis: List<FirmaBlockUi>): List<String> {
                val out = mutableListOf<String>()
                for (b in uis) {
                    val firma = b.acFirma.text?.toString().orEmpty().trim()
                    if (firma.isBlank()) continue

                    val validRows = b.rows
                        .map { it.acAnzahl.text?.toString().orEmpty().trim() to it.acBez.text?.toString().orEmpty().trim() }
                        .filter { it.first.isNotBlank() || it.second.isNotBlank() }

                    if (validRows.isEmpty()) {
                        out.add(firma)
                    } else {
                        validRows.forEach { (anz, bez) ->
                            val right = when {
                                anz.isNotBlank() && bez.isNotBlank() -> "$anz $bez"
                                anz.isNotBlank() -> anz
                                else -> bez
                            }
                            out.add("$firma - $right")
                        }
                    }
                }
                return out
            }

            etPersonal.setText(blocksToLines(personalUis).joinToString("\n"))
            etGeraete.setText(blocksToLines(maschinenUis).joinToString("\n"))
        }

        private fun loadHistoryOptions(baustelleName: String) {
            lifecycleScope.launch {
                val triple = withContext(Dispatchers.IO) {
                    val root = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                    val base = File(root, "TB-Helfer/${BautagebuchSpeicher.sichererName(baustelleName)}/Bautagebuch")
                    if (base == null || !base.exists()) return@withContext Triple(emptySet<String>(), emptySet<String>(), emptySet<String>())

                    val firm = linkedSetOf<String>()
                    val role = linkedSetOf<String>()
                    val mach = linkedSetOf<String>()

                    val dates = base.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: emptyList()
                    for (d in dates.take(30)) {
                        val jsonFile = File(d, "bautagebuch.json")
                        if (!jsonFile.exists()) continue
                        runCatching {
                            val jo = JSONObject(jsonFile.readText())
                            val persArr = jo.optJSONArray("personal")
                            for (i in 0 until (persArr?.length() ?: 0)) {
                                val line = persArr?.optString(i).orEmpty()
                                val parts = line.split("-", ":", ";").map { it.trim() }.filter { it.isNotBlank() }
                                if (parts.isNotEmpty()) firm.add(parts[0])
                                if (parts.size >= 2) role.add(parts.drop(1).joinToString(" "))
                            }
                            val gerArr = jo.optJSONArray("geraete")
                            for (i in 0 until (gerArr?.length() ?: 0)) {
                                val line = gerArr?.optString(i).orEmpty()
                                val parts = line.split("-", ":", ";").map { it.trim() }.filter { it.isNotBlank() }
                                if (parts.isNotEmpty()) firm.add(parts[0])
                                if (parts.size >= 2) mach.add(parts.drop(1).joinToString(" "))
                            }
                        }
                    }
                    Triple(firm, role, mach)
                }

                firmHistory.clear(); firmHistory.addAll(triple.first)
                roleHistory.clear(); roleHistory.addAll(triple.second)
                machineHistory.clear(); machineHistory.addAll(triple.third)

                refreshAllAdapters()
            }
        }

        private class SimpleTextWatcher(private val onChange: () -> Unit) : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { onChange() }
        }

    }

    private object OpenMeteoClient {
        private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
        private val isoDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        data class HourlyPoint(
            val time: LocalDateTime,
            val tempC: Double,
            val precipMm: Double,
            val cloudCoverPct: Int,
            val weatherCode: Int
        )

        class HourlySeries(private val points: List<HourlyPoint>) {
            fun closestTo(target: LocalDateTime): HourlyPoint? {
                if (points.isEmpty()) return null
                var best: HourlyPoint = points[0]
                var bestDiff = kotlin.math.abs(java.time.Duration.between(best.time, target).toMinutes())
                for (p in points) {
                    val d = kotlin.math.abs(java.time.Duration.between(p.time, target).toMinutes())
                    if (d < bestDiff) { best = p; bestDiff = d }
                }
                return best
            }
        }

        private fun fetchUrl(url: URL): HourlySeries? {
            val con = (url.let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }

            return try {
                val code = con.responseCode
                val stream = if (code in 200..299) con.inputStream else con.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) return null

                val root = JSONObject(body)
                val hourly = root.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val temps = hourly.getJSONArray("temperature_2m")
                val prec = hourly.getJSONArray("precipitation")
                val cloud = hourly.getJSONArray("cloud_cover")
                val wcode = hourly.getJSONArray("weathercode")

                val pts = ArrayList<HourlyPoint>(times.length())
                for (i in 0 until times.length()) {
                    val t = LocalDateTime.parse(times.getString(i), isoDateTime)
                    pts += HourlyPoint(
                        time = t,
                        tempC = temps.getDouble(i),
                        precipMm = prec.getDouble(i),
                        cloudCoverPct = cloud.getInt(i),
                        weatherCode = wcode.getInt(i)
                    )
                }
                if (pts.isEmpty()) null else HourlySeries(pts)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { con.disconnect() }
            }
        }

        fun fetchHourly(lat: Double, lon: Double, start: LocalDate, end: LocalDate): HourlySeries? {
            val forecastUrl = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&hourly=temperature_2m,precipitation,cloud_cover,weathercode" +
                    "&start_date=${start.format(isoDate)}&end_date=${end.format(isoDate)}" +
                    "&timezone=Europe%2FBerlin"
            )

            val forecast = fetchUrl(forecastUrl)
            if (forecast != null) return forecast

            // Fallback: vergangene Tage zuverlässig über Archive-API holen
            val todayBerlin = LocalDate.now(ZoneId.of("Europe/Berlin"))
            if (end.isBefore(todayBerlin)) {
                val archiveUrl = URL(
                    "https://archive-api.open-meteo.com/v1/archive" +
                        "?latitude=$lat&longitude=$lon" +
                        "&hourly=temperature_2m,precipitation,cloud_cover,weathercode" +
                        "&start_date=${start.format(isoDate)}&end_date=${end.format(isoDate)}" +
                        "&timezone=Europe%2FBerlin"
                )
                return fetchUrl(archiveUrl)
            }

            return null
        }
    }


    private fun mapCloudToBew(cloudPct: Int): String {
        return when {
            cloudPct <= 5 -> "sonnig / wolkenlos / klar"
            cloudPct <= 25 -> "heiter"
            cloudPct <= 50 -> "leicht bewölkt"
            cloudPct <= 75 -> "wolkig"
            cloudPct <= 90 -> "stark bewölkt"
            else -> "bedeckt"
        }
    }


    private fun mapCodeToNied(code: Int, precipMm: Double): String {
        if (precipMm <= 0.0) return "trocken"
        return when (code) {
            51, 53, 55 -> "Nieselregen"
            56, 57, 66, 67 -> "gefrierender Regen"
            61 -> "Regen (leicht)"
            63 -> "Regen (mäßig)"
            65 -> "Regen (stark)"
            80, 81, 82 -> "Schauer"
            68, 69 -> "Schneeregen"
            77 -> "Graupel"
            71 -> "Schneefall (leicht)"
            73 -> "Schneefall (mäßig)"
            75 -> "Schneefall (stark)"
            85 -> "Schneefall (leicht)"
            86 -> "Schneefall (stark)"
            95 -> "Gewitter"
            96, 99 -> "Hagel"
            else -> {
                // Heuristik für unbekannte Codes
                when {
                    precipMm < 0.3 -> "Nieselregen"
                    precipMm < 1.5 -> "Regen (leicht)"
                    precipMm < 4.0 -> "Regen (mäßig)"
                    else -> "Regen (stark)"
                }
            }
        }
    }


private data class BaustelleKontext(
    val id: Long,
    val name: String,
    val strecke: String?,
    val rootKnotenId: Long? = null
)





