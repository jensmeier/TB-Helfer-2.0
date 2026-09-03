package de.tbhelfer.ui.kamera

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.AbsteckpunktEntity
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import de.tbhelfer.datenbank.GvpPointEntity
import de.tbhelfer.datenbank.TrassenAchseEntity
import de.tbhelfer.datenbank.TrassenKmAnkerEntity
import de.tbhelfer.datenbank.TrassenManuellerAbschnittEntity
import de.tbhelfer.datenbank.TrassenAbschnittKmPunktEntity
import de.tbhelfer.einstellungen.KameraEinstellungenSpeicher
import de.tbhelfer.ui.streckenpunkte.AktiverTrassenKontext
import de.tbhelfer.ui.streckenpunkte.AktiverTrassenKontextResolver
import de.tbhelfer.ui.streckenpunkte.TrassenKontextFilter
import de.tbhelfer.ui.streckenpunkte.TrassenKontextArt
import de.tbhelfer.streckenobjekte.BaustellenObjektArt
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik
import de.tbhelfer.streckenobjekte.StreckenObjektSpeicher
import de.tbhelfer.vermessung.KmBerechner
import de.tbhelfer.vermessung.StandortLiveQuelle
import de.tbhelfer.vermessung.TrassenNaheBerechner
import de.tbhelfer.vermessung.TrassenKmStatusLogik
import de.tbhelfer.vermessung.ManuelleAbschnittKmLogik
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.Executor
import de.tbhelfer.util.KontextNormalisierung

class KameraFragment : Fragment(R.layout.fragment_kamera) {

    private lateinit var previewView: PreviewView
    private lateinit var textStatus: TextView
    private lateinit var btnUnterbaustelle: Button
    private lateinit var btnFoto: Button
    private lateinit var btnStreckenpunkt: Button
    private lateinit var btnBlitzAus: MaterialButton
    private lateinit var btnBlitzAuto: MaterialButton
    private lateinit var btnBlitzAn: MaterialButton
    private lateinit var btnNacht: MaterialButton
    private lateinit var textZoomAnzeige: TextView

    private var imageCapture: ImageCapture? = null
    private var gebundeneKamera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var kameraEinstellungen = KameraEinstellungenSpeicher.Einstellungen()
    private lateinit var zoomDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector
    private var zoomAnzeigeJob: Job? = null
    private var overlayKontrastJob: Job? = null
    private var overlayKontrastPausiert: Boolean = false
    private var widgetCameraLaunch: Boolean = false
    private var overlayTextWeiss: Boolean? = true
    private var nachtmodusUnterstuetzt: Boolean = false
    private var extensionsManager: ExtensionsManager? = null
    private var nachtAufnahmeDialog: androidx.appcompat.app.AlertDialog? = null
    private var kameraBedienungAktiv: Boolean = false

    private val hauptExecutor: Executor by lazy { ContextCompat.getMainExecutor(requireContext()) }

    private var aktuelleBaustelleId: Long? = null
    private var aktuelleBaustellenName: String? = null
    private var aktuelleHauptKnotenId: Long? = null
    private var aktuellerKontext: AktiverTrassenKontext? = null

    /** Unterbaustellen-Auswahl nur für diesen Kamera-Flow (User-Wunsch: pro Foto wählbar). */
    private var selectedKnotenId: Long? = null

    /** Sondermodus aus der Mängelliste: Nachweisfoto zu vorhandenem Mangel. */
    private var nachweisMode: Boolean = false
    private var nachweisMangelKey: String = ""
    private var nachweisStatus: String = ""
    private var nachweisFotoArt: String = ""
    private var nachweisKnotenId: Long? = null
    private var nachweisFirma: String = ""
    private var nachweisKurztext: String = ""
    private var nachweisNotiz: String = ""
    private var nachweisKm: Double? = null

    /** Sondermodus aus einer strukturierten Abnahme. */
    private var abnahmeMode: Boolean = false
    private var abnahmeId: String = ""
    private var abnahmeBildArt: String = ""
    private var abnahmeBaustelleId: Long? = null
    private var abnahmeHauptKnotenId: Long? = null
    private var abnahmeBaustelleName: String = ""

    // Live Standort + km
    private var letzterStandort: Location? = null
    private var letzterKm: KmBerechner.Ergebnis? = null
    private var letzterTrassenTreffer: TrassenNaheBerechner.Ergebnis? = null
    private var letzterStreckenObjektStrang: String? = null
    private var letztesStreckenObjektId: String? = null
    private var streckenpunkteCache: List<de.tbhelfer.datenbank.StreckenpunktEntity> = emptyList()
    private var trassenAchsenCache: List<TrassenNaheBerechner.AchsInfo> = emptyList()
    private var trassenSamplepunkteCache: List<TrassenNaheBerechner.Punkt> = emptyList()
    private var gvpPunkteCache: List<GvpPointEntity> = emptyList()
    private var absteckpunkteCache: List<AbsteckpunktEntity> = emptyList()
    private var trassenKmAnkerCache: List<TrassenKmAnkerEntity> = emptyList()
    private var trassenManuelleAbschnitteCache: List<TrassenManuellerAbschnittEntity> = emptyList()
    private var trassenManuelleAbschnittKmPunkteCache: List<TrassenAbschnittKmPunktEntity> = emptyList()
    private val kmBerechner = KmBerechner()
    private var letzteGnssInfo: StandortLiveQuelle.GnssZusammenfassung? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { erlaubt ->
        if (erlaubt) {
            starteKamera()
        } else {
            setzeStatus("Kamerazugriff verweigert. Bitte Berechtigung erlauben.")
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            starteStandortLive()
            starteGnssStatus()
        } else {
            // Standort ist Pflicht für Foto-Speichern (wie besprochen)
            setzeStatus("Standort-Berechtigung fehlt. GPS ist Pflicht für Fotos.")
            btnFoto.isEnabled = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = view.findViewById(R.id.previewView)
        textStatus = view.findViewById(R.id.textStatus)
        btnUnterbaustelle = view.findViewById(R.id.btnUnterbaustelle)
        btnFoto = view.findViewById(R.id.btnFoto)
        btnStreckenpunkt = view.findViewById(R.id.btnStreckenpunkt)
        btnBlitzAus = view.findViewById(R.id.btnBlitzAusKamera)
        btnBlitzAuto = view.findViewById(R.id.btnBlitzAutoKamera)
        btnBlitzAn = view.findViewById(R.id.btnBlitzAnKamera)
        btnNacht = view.findViewById(R.id.btnNachtKamera)
        textZoomAnzeige = view.findViewById(R.id.textZoomAnzeige)

        // Sichere Startfarbe: In Dunkelheit muss das Overlay sofort lesbar sein.
        overlayTextWeiss = true
        textStatus.setTextColor(Color.WHITE)
        textStatus.setShadowLayer(4.0f, 0f, 0f, Color.BLACK)
        nachtmodusUnterstuetzt = false
        btnNacht.visibility = View.GONE
        setzeKameraBedienungAktiv(false)

        zoomDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // Patch45m: Zwei-Finger-Zoom ist eine direkte Kamerabedienung und
                // deshalb immer aktiv; der alte versteckte Einstellungsschalter entfällt.
                val kamera = gebundeneKamera ?: return false
                val zoomState = kamera.cameraInfo.zoomState.value ?: return false
                val ziel = (zoomState.zoomRatio * detector.scaleFactor)
                    .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                kamera.cameraControl.setZoomRatio(ziel)
                zeigeZoomAnzeige(ziel)
                return true
            }
        })

        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val kamera = gebundeneKamera ?: return false
                val zoomState = kamera.cameraInfo.zoomState.value ?: return false
                val ziel = 1.0f.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                kamera.cameraControl.setZoomRatio(ziel)
                zeigeZoomAnzeige(ziel)
                return true
            }
        })

        previewView.setOnTouchListener { _, event ->
            val doppeltipp = gestureDetector.onTouchEvent(event)
            val zoom = zoomDetector.onTouchEvent(event)
            doppeltipp || zoom
        }

        btnBlitzAus.setOnClickListener { setzeBlitzModus(KameraEinstellungenSpeicher.BlitzModus.AUS) }
        btnBlitzAuto.setOnClickListener { setzeBlitzModus(KameraEinstellungenSpeicher.BlitzModus.AUTO) }
        btnBlitzAn.setOnClickListener { setzeBlitzModus(KameraEinstellungenSpeicher.BlitzModus.AN) }
        btnNacht.setOnClickListener { schalteNachtmodusUm() }

        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.kameraEinstellungenSpeicher.einstellungen.collectLatest { cfg ->
                kameraEinstellungen = cfg
                aktualisiereKameraBedienung()
                wendeKameraEinstellungenAn()
            }
        }

        widgetCameraLaunch = arguments?.getBoolean(ARG_WIDGET_CAMERA_LAUNCH, false) == true

        nachweisMode = arguments?.getBoolean(ARG_NACHWEIS_MODE, false) == true
        nachweisMangelKey = arguments?.getString(ARG_NACHWEIS_MANGEL_KEY).orEmpty()
        nachweisStatus = arguments?.getString(ARG_NACHWEIS_STATUS).orEmpty()
        nachweisFotoArt = arguments?.getString(ARG_MANGEL_FOTO_ART).orEmpty()
        nachweisKnotenId = arguments?.getLong(ARG_NACHWEIS_KNOTEN_ID, -1L)?.takeIf { it > 0L }
        nachweisFirma = arguments?.getString(ARG_PREFILL_FIRMA).orEmpty()
        nachweisKurztext = arguments?.getString(ARG_PREFILL_KURZTEXT).orEmpty()
        nachweisNotiz = arguments?.getString(ARG_PREFILL_NOTIZ).orEmpty()
        nachweisKm = arguments?.getDouble(ARG_PREFILL_KM, Double.NaN)?.takeIf { !it.isNaN() }
        abnahmeMode = arguments?.getBoolean(ARG_ABNAHME_MODE, false) == true
        abnahmeId = arguments?.getString(ARG_ABNAHME_ID).orEmpty()
        abnahmeBildArt = arguments?.getString(ARG_ABNAHME_BILD_ART).orEmpty()
        abnahmeBaustelleId = arguments?.getLong(ARG_ABNAHME_BAUSTELLE_ID, -1L)?.takeIf { it > 0L }
        abnahmeHauptKnotenId = arguments?.getLong(ARG_ABNAHME_HAUPT_KNOTEN_ID, -1L)?.takeIf { it > 0L }
        abnahmeBaustelleName = arguments?.getString(ARG_ABNAHME_BAUSTELLE_NAME).orEmpty()
        if (nachweisMode || abnahmeMode) {
            btnFoto.text = "Foto hinzufügen"
        }
        if (abnahmeMode) {
            // Die Unterbaustelle wird in der Abnahme festgelegt und darf hier nicht
            // versehentlich nur für das einzelne Foto abweichend geändert werden.
            btnUnterbaustelle.isEnabled = false
        }

        btnUnterbaustelle.setOnClickListener {
            zeigeUnterbaustellenAuswahl()
        }

        btnFoto.setOnClickListener { fotoMachen() }

        btnStreckenpunkt.visibility = View.GONE
        btnStreckenpunkt.setOnClickListener {
            // Patch45 NEU: Keine globale 2-Punkt-Kalibrierung der kompletten TRA-Achse mehr.
            // Der Kamera-Button fuehrt direkt in den neuen abschnittsbezogenen Editor.
            val args = Bundle().apply {
                putBoolean("autoOpenStreckenGleiseBearbeiten", true)
            }
            findNavController().navigate(R.id.importExportFragment, args)
        }

        // Im Abnahme-Flow bleibt das Foto sicher bei der gewählten Abnahme – auch wenn
        // im Hintergrund eine andere Baustelle als "aktuell" markiert ist.
        viewLifecycleOwner.lifecycleScope.launch {
            aktuelleBaustelleId = abnahmeBaustelleId
                ?: ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
            aktuelleHauptKnotenId = abnahmeHauptKnotenId
                ?: ServiceLocator.aktuelleBaustelleSpeicher.aktuelleHauptbaustelleKnotenId.first()
            if (aktuelleBaustelleId == null) {
                setzeStatus("Keine aktuelle Baustelle gewählt. Bitte zuerst unter 'Baustellen' auswählen.")
                btnFoto.isEnabled = false
                btnUnterbaustelle.isEnabled = false
                btnStreckenpunkt.visibility = View.GONE
                return@launch
            }

            // Fallback/Repair: Manche Stellen der App setzen (legacy) nur die alte BaustelleId.
            // Für Unterbaustellen-Auswahl brauchen wir aber die Haupt-KnotenId.
            if (aktuelleHauptKnotenId == null) {
                val haupt = ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(aktuelleBaustelleId!!)
                if (haupt != null) {
                    aktuelleHauptKnotenId = haupt.id
                    // Nur beim normalen Kamera-Flow reparieren/persistieren. Eine Abnahme
                    // darf die global aktive Baustelle nicht unbeabsichtigt umschalten.
                    if (!abnahmeMode) {
                        ServiceLocator.aktuelleBaustelleSpeicher.setzenNurHaupt(
                            hauptKnotenId = haupt.id,
                            altBaustelleId = aktuelleBaustelleId!!
                        )
                    }
                }
            }

            val b = ServiceLocator.baustellenRepository.baustelleMitIdOnce(aktuelleBaustelleId!!)
            aktuelleBaustellenName = abnahmeBaustelleName.ifBlank { b?.baustellenname.orEmpty() }

            // Button-Label einmal initial setzen. Im Abnahme-Flow ist die gespeicherte
            // Unterbaustelle maßgebend, nicht eine zufällige globale Auswahl.
            selectedKnotenId = if (abnahmeMode) nachweisKnotenId else ServiceLocator.aktuelleBaustelleSpeicher.aktuellerKnotenId.first()
            if ((nachweisMode || abnahmeMode) && nachweisKnotenId != null) {
                selectedKnotenId = nachweisKnotenId
                if (!abnahmeMode) {
                    runCatching {
                        ServiceLocator.aktuelleBaustelleSpeicher.setzenKnoten(
                            aktuellerKnotenId = nachweisKnotenId!!,
                            hauptKnotenId = aktuelleHauptKnotenId!!,
                            altBaustelleId = aktuelleBaustelleId!!
                        )
                    }
                }
            }
            updateUnterbaustelleButton(selectedKnotenId)

            val kontextStartId = selectedKnotenId ?: aktuelleHauptKnotenId
            val pfad = kontextStartId?.let { runCatching { ServiceLocator.baustellenBaumRepository.pfadVonRootBis(it) }.getOrDefault(emptyList()) } ?: emptyList()
            aktuellerKontext = AktiverTrassenKontextResolver.ausPfad(pfad, b)

            setzeStatus(buildBereitText())
            starteOderBerechtigungFragen()

            // Streckenpunkte live global nach fachlichem Kontext laden (Alt-Logik bleibt als Fallback).
            viewLifecycleOwner.lifecycleScope.launch {
                ServiceLocator.streckenpunkteRepository.allePunkte().collectLatest { alle ->
                    streckenpunkteCache = TrassenKontextFilter.filterPunkte(alle, aktuellerKontext)
                }
            }

            ladeTrassenDatenFuerKamera()
            starteOderStandortBerechtigungFragen()
        }
    }

    private fun zeigeUnterbaustellenAuswahl() {
        val altBaustelleId = aktuelleBaustelleId
        val hauptId = aktuelleHauptKnotenId
        if (altBaustelleId == null || hauptId == null) {
            Toast.makeText(requireContext(), "Keine aktuelle Baustelle gewählt.", Toast.LENGTH_SHORT).show()
            return
        }

        // Während Dialog und Kontextwechsel keine Preview-Screenshots anfordern.
        // Das verhindert den Widget-Kaltstart-ANR bei gleichzeitigem PixelCopy und Datenwechsel.
        overlayKontrastPausiert = true
        viewLifecycleOwner.lifecycleScope.launch {
            val alle = ServiceLocator.baustellenBaumRepository.alleKnotenZuRoot(hauptId).first()
            val byId = alle.associateBy { it.id }
            val childrenByParent = alle
                .filter { it.parentId != null }
                .groupBy { it.parentId!! }

            fun displayName(k: BaustellenKnotenEntity): String {
                val base = when (k.typ) {
                    BaustellenKnotenTyp.FREIE_STRECKE -> k.knotenName?.takeIf { it.isNotBlank() }
                        ?: (k.streckenkennung?.let { "Freie Strecke $it" } ?: "Freie Strecke")
                    BaustellenKnotenTyp.BAHNHOF -> k.knotenName?.takeIf { it.isNotBlank() }
                        ?: run {
                            val bhf = k.bahnhofName?.trim().orEmpty()
                            val glw = k.gleisOderWeiche?.trim().orEmpty()
                            when {
                                bhf.isNotBlank() && glw.isNotBlank() -> "Bahnhof $bhf – $glw"
                                bhf.isNotBlank() -> "Bahnhof $bhf"
                                else -> "Bahnhof"
                            }
                        }
                    BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> k.knotenName?.takeIf { it.isNotBlank() } ?: "Allgemein / Objekt"
                    BaustellenKnotenTyp.HAUPT -> k.hauptbaustellenName
                }

                val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
                    ?.let { " $it" }.orEmpty()

                return base + km
            }

            // Preorder-Liste (Unterbaustellen) mit Einrückung
            val lines = mutableListOf<Pair<Long, String>>()
            fun add(parentId: Long, level: Int) {
                val kids = childrenByParent[parentId].orEmpty()
                    .sortedBy { it.erstelltAmEpochMs }
                for (k in kids) {
                    val prefix = buildString {
                        repeat(level) { append("   ") }
                        append("› ")
                    }
                    lines.add(k.id to (prefix + displayName(k)))
                    add(k.id, level + 1)
                }
            }
            add(hauptId, 0)

            val entries = mutableListOf<Pair<Long, String>>()
            // "Keine Unterbaustelle" ist überflüssig -> raus. Abbrechen = keine Auswahl/keine Änderung.
            entries.add(hauptId to "— Nur Hauptbaustelle")
            entries.addAll(lines.map { (id, label) -> id to label })

            val labels = entries.map { it.second }.toTypedArray()
            val titleView = layoutInflater.inflate(R.layout.dialog_title_subtitle, null)
            titleView.findViewById<TextView>(R.id.dialogTitle).text = aktuelleBaustellenName ?: "Hauptbaustelle"
            titleView.findViewById<TextView>(R.id.dialogSubtitle).text = getString(R.string.action_select_subsite)

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(titleView)
                .setItems(labels) { _, which ->
                    val selId = entries[which].first
                    selectedKnotenId = selId
                    updateUnterbaustelleButton(selId)
                    // Global speichern (BTB/PDF/Kamera konsistent). Keine Foto-/PDF-Daten werden verändert.
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            if (selId == hauptId) {
                                ServiceLocator.aktuelleBaustelleSpeicher.setzenNurHaupt(
                                    hauptKnotenId = hauptId,
                                    altBaustelleId = altBaustelleId
                                )
                            } else {
                                ServiceLocator.aktuelleBaustelleSpeicher.setzenKnoten(
                                    aktuellerKnotenId = selId,
                                    hauptKnotenId = hauptId,
                                    altBaustelleId = altBaustelleId
                                )
                            }
                            refreshAktiverKontext()
                            aktualisiereKmKalibrierButton()
                            letzterStandort?.let { loc ->
                                val trassen = berechneTrassenTreffer(loc)
                                letzterTrassenTreffer = trassen
                                aktualisiereOverlayText(
                                    altBaustelleId,
                                    loc,
                                    letzterKm ?: KmBerechner.Ergebnis(
                                        KmBerechner.Indikator.ROT, null, null, null, false
                                    )
                                )
                            } ?: setzeStatus(buildBereitText())
                        } finally {
                            // Einen Moment warten, bis Overlay/Trassenkontext wieder stabil sind.
                            delay(500L)
                            overlayKontrastPausiert = false
                        }
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .create()
            dialog.setOnDismissListener {
                if (selectedKnotenId == null) overlayKontrastPausiert = false
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(500L)
                    overlayKontrastPausiert = false
                }
            }
            dialog.show()
        }
    }

    private fun updateUnterbaustelleButton(knotenId: Long?) {
        val hauptId = aktuelleHauptKnotenId
        if (knotenId == null || (hauptId != null && knotenId == hauptId)) {
            btnUnterbaustelle.text = "Unterbaustelle"
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val k = ServiceLocator.baustellenBaumRepository.knotenMitId(knotenId)
            val name = k?.knotenName?.takeIf { it.isNotBlank() }
                ?: k?.let { when (it.typ) {
                    BaustellenKnotenTyp.FREIE_STRECKE -> it.streckenkennung?.let { s -> "Freie Strecke $s" } ?: "Freie Strecke"
                    BaustellenKnotenTyp.BAHNHOF -> run {
                        val bhf = it.bahnhofName?.trim().orEmpty()
                        val glw = it.gleisOderWeiche?.trim().orEmpty()
                        when {
                            bhf.isNotBlank() && glw.isNotBlank() -> "Bahnhof $bhf – $glw"
                            bhf.isNotBlank() -> "Bahnhof $bhf"
                            else -> "Bahnhof"
                        }
                    }
                    BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> "Allgemein / Objekt"
                    BaustellenKnotenTyp.HAUPT -> it.hauptbaustellenName
                } }
                ?: "Unterbaustelle"

            btnUnterbaustelle.text = "Unterbaustelle: $name"
        }
    }

    override fun onResume() {
        super.onResume()
        if (gebundeneKamera != null && overlayKontrastJob == null) {
            starteOverlayKontrastAutomatik()
        }
    }

    override fun onStop() {
        overlayKontrastJob?.cancel()
        overlayKontrastJob = null
        super.onStop()
    }

    override fun onDestroyView() {
        nachtAufnahmeDialog?.dismiss()
        nachtAufnahmeDialog = null
        zoomAnzeigeJob?.cancel()
        overlayKontrastJob?.cancel()
        runCatching { gebundeneKamera?.cameraControl?.enableTorch(false) }
        gebundeneKamera = null
        imageCapture = null
        super.onDestroyView()
    }

    private fun starteOderBerechtigungFragen() {
        if (hatKameraBerechtigung()) {
            starteKamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun starteOderStandortBerechtigungFragen() {
        if (StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
            starteStandortLive()
            starteGnssStatus()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hatKameraBerechtigung(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun starteKamera() {
        val baustelleId = aktuelleBaustelleId ?: return
        setzeKameraBedienungAktiv(false)
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener(providerListener@{
            val provider = try {
                providerFuture.get()
            } catch (e: Throwable) {
                setzeStatus("Kamera konnte nicht gestartet werden: ${e.message}")
                return@providerListener
            }
            val managerFuture = ExtensionsManager.getInstanceAsync(requireContext(), provider)
            managerFuture.addListener({
                extensionsManager = runCatching { managerFuture.get() }.getOrNull()
                bindeKamera(provider, baustelleId)
            }, hauptExecutor)
        }, hauptExecutor)
    }

    private fun bindeKamera(cameraProvider: ProcessCameraProvider, baustelleId: Long) {
        val basisSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val manager = extensionsManager
        nachtmodusUnterstuetzt = runCatching {
            manager?.isExtensionAvailable(basisSelector, ExtensionMode.NIGHT) == true
        }.getOrDefault(false)
        if (kameraEinstellungen.nachtsicht && !nachtmodusUnterstuetzt) {
            kameraEinstellungen = kameraEinstellungen.copy(nachtsicht = false)
            speichereKameraEinstellungen()
        }
        val selector = if (kameraEinstellungen.nachtsicht && nachtmodusUnterstuetzt && manager != null) {
            runCatching { manager.getExtensionEnabledCameraSelector(basisSelector, ExtensionMode.NIGHT) }
                .getOrDefault(basisSelector)
        } else basisSelector

        // Die Zielausrichtung muss der aktuellen Display-/Vorschauausrichtung entsprechen.
        // Ohne diese Vorgabe kann CameraX bei gedreht gehaltenem Handy ein JPEG erzeugen,
        // dessen Pixel/EXIF-Ausrichtung später im BTB-PDF seitlich erscheint.
        val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val preview = Preview.Builder()
            .setTargetRotation(displayRotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val captureMode = if (kameraEinstellungen.nachtsicht) {
            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        } else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setTargetRotation(displayRotation)
            .build()

        try {
            cameraProvider.unbindAll()
            gebundeneKamera = cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                selector,
                preview,
                imageCapture
            )
            wendeKameraEinstellungenAn()
            setzeKameraBedienungAktiv(true)
            aktualisiereKameraBedienung()
            setzeStatus(
                if (kameraEinstellungen.nachtsicht) "Nachtmodus bereit · Handy bei Aufnahme ruhig halten"
                else "Kamera aktiv (${aktuelleBaustellenName ?: "Baustelle-ID: $baustelleId"})"
            )
            btnFoto.isEnabled = true
            btnUnterbaustelle.isEnabled = !abnahmeMode
            starteOverlayKontrastAutomatik()
        } catch (e: Exception) {
            setzeStatus("Kamera konnte nicht gestartet werden: ${e.message}")
            setzeKameraBedienungAktiv(false)
            btnFoto.isEnabled = false
            btnUnterbaustelle.isEnabled = false
        }
    }

    private fun wendeKameraEinstellungenAn() {
        val capture = imageCapture
        if (capture != null) {
            capture.flashMode = when {
                kameraEinstellungen.nachtsicht -> ImageCapture.FLASH_MODE_OFF
                kameraEinstellungen.blitzModus == KameraEinstellungenSpeicher.BlitzModus.AUTO -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
        }

        val kamera = gebundeneKamera ?: return
        val torchSollAn = !kameraEinstellungen.nachtsicht &&
            kameraEinstellungen.blitzModus == KameraEinstellungenSpeicher.BlitzModus.AN &&
            kamera.cameraInfo.hasFlashUnit()
        runCatching { kamera.cameraControl.enableTorch(torchSollAn) }
    }

    private fun setzeBlitzModus(modus: KameraEinstellungenSpeicher.BlitzModus) {
        val nachtWarAktiv = kameraEinstellungen.nachtsicht
        kameraEinstellungen = kameraEinstellungen.copy(
            blitzModus = modus,
            nachtsicht = false
        )
        speichereKameraEinstellungen()
        aktualisiereKameraBedienung()
        if (nachtWarAktiv) {
            starteKamera()
        } else {
            wendeKameraEinstellungenAn()
        }
    }

    private fun schalteNachtmodusUm() {
        if (!nachtmodusUnterstuetzt) {
            Toast.makeText(requireContext(), "Nachtmodus wird von dieser Kamera nicht unterstützt.", Toast.LENGTH_SHORT).show()
            return
        }
        val neuAktiv = !kameraEinstellungen.nachtsicht
        kameraEinstellungen = kameraEinstellungen.copy(nachtsicht = neuAktiv)
        speichereKameraEinstellungen()
        aktualisiereKameraBedienung()
        // Die echte CameraX-Night-Extension wird beim Binden ausgewählt.
        starteKamera()
    }

    private fun speichereKameraEinstellungen() {
        val cfg = kameraEinstellungen
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.kameraEinstellungenSpeicher.speichern(cfg)
        }
    }

    private fun aktualisiereKameraBedienung() {
        if (!::btnBlitzAus.isInitialized) return
        markiereKameraButton(btnBlitzAus, !kameraEinstellungen.nachtsicht && kameraEinstellungen.blitzModus == KameraEinstellungenSpeicher.BlitzModus.AUS)
        markiereKameraButton(btnBlitzAuto, !kameraEinstellungen.nachtsicht && kameraEinstellungen.blitzModus == KameraEinstellungenSpeicher.BlitzModus.AUTO)
        markiereKameraButton(btnBlitzAn, !kameraEinstellungen.nachtsicht && kameraEinstellungen.blitzModus == KameraEinstellungenSpeicher.BlitzModus.AN)
        markiereKameraButton(btnNacht, kameraEinstellungen.nachtsicht)
        btnNacht.visibility = if (nachtmodusUnterstuetzt) View.VISIBLE else View.GONE

        val hatBlitz = gebundeneKamera?.cameraInfo?.hasFlashUnit() ?: false
        btnBlitzAus.isEnabled = kameraBedienungAktiv
        btnBlitzAuto.isEnabled = kameraBedienungAktiv && hatBlitz
        btnBlitzAn.isEnabled = kameraBedienungAktiv && hatBlitz
        btnNacht.isEnabled = kameraBedienungAktiv && nachtmodusUnterstuetzt
    }

    private fun markiereKameraButton(button: MaterialButton, aktiv: Boolean) {
        val farbe = if (aktiv) Color.rgb(45, 110, 245) else Color.argb(220, 45, 45, 45)
        button.backgroundTintList = ColorStateList.valueOf(farbe)
        button.setTextColor(Color.WHITE)
        button.alpha = if (aktiv) 1.0f else 0.82f
    }

    private fun setzeKameraBedienungAktiv(aktiv: Boolean) {
        kameraBedienungAktiv = aktiv
        if (!::btnBlitzAus.isInitialized) return
        aktualisiereKameraBedienung()
    }

    private fun zeigeZoomAnzeige(zoomRatio: Float) {
        if (!::textZoomAnzeige.isInitialized) return
        textZoomAnzeige.text = String.format(Locale.GERMANY, "%.1fx", zoomRatio)
        textZoomAnzeige.visibility = View.VISIBLE
        zoomAnzeigeJob?.cancel()
        zoomAnzeigeJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(2_000L)
            if (::textZoomAnzeige.isInitialized) textZoomAnzeige.visibility = View.GONE
        }
    }

    private fun starteOverlayKontrastAutomatik() {
        overlayKontrastJob?.cancel()
        overlayKontrastJob = viewLifecycleOwner.lifecycleScope.launch {
            // Beim Kaltstart über ein Widget braucht CameraX/SurfaceView etwas länger.
            // Erst nach stabiler Vorschau prüfen und PixelCopy niemals im 700-ms-Takt belasten.
            delay(if (widgetCameraLaunch) 3_000L else 1_800L)
            while (true) {
                if (!overlayKontrastPausiert && kameraBedienungAktiv && isResumed && previewView.isAttachedToWindow) {
                    aktualisiereOverlayKontrast()
                }
                delay(if (widgetCameraLaunch) 3_000L else 2_200L)
            }
        }
    }

    private fun aktualisiereOverlayKontrast() {
        if (!::previewView.isInitialized || !::textStatus.isInitialized) return
        val bitmap = runCatching { previewView.bitmap }.getOrNull() ?: return
        if (bitmap.width <= 2 || bitmap.height <= 2) return

        // Nicht mehr die verschobenen View-Koordinaten verwenden: Safe-Area und Display-Cutout
        // können Text und Preview gegeneinander verschieben. Gemessen wird der gesamte obere
        // Informationsbereich der tatsächlichen Kameravorschau.
        val left = (bitmap.width * 0.04f).toInt().coerceAtLeast(0)
        val right = (bitmap.width * 0.96f).toInt().coerceAtMost(bitmap.width - 1)
        val top = (bitmap.height * 0.02f).toInt().coerceAtLeast(0)
        val bottom = (bitmap.height * 0.30f).toInt().coerceAtMost(bitmap.height - 1)
        var summe = 0.0
        var anzahl = 0
        val rasterX = 12
        val rasterY = 8
        for (iy in 0 until rasterY) {
            val y = top + ((bottom - top) * (iy + 1) / (rasterY + 1))
            for (ix in 0 until rasterX) {
                val x = left + ((right - left) * (ix + 1) / (rasterX + 1))
                val farbe = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
                summe += 0.2126 * Color.red(farbe) + 0.7152 * Color.green(farbe) + 0.0722 * Color.blue(farbe)
                anzahl++
            }
        }
        bitmap.recycle()
        if (anzahl == 0) return
        val helligkeit = summe / anzahl
        val textSollWeiss = when (overlayTextWeiss) {
            true -> helligkeit < 180.0
            false -> helligkeit < 125.0
            null -> helligkeit < 150.0
        }
        if (overlayTextWeiss == textSollWeiss) return
        overlayTextWeiss = textSollWeiss
        val textFarbe = if (textSollWeiss) Color.WHITE else Color.BLACK
        val konturFarbe = if (textSollWeiss) Color.BLACK else Color.WHITE
        textStatus.setTextColor(textFarbe)
        textStatus.setShadowLayer(4.0f, 0f, 0f, konturFarbe)
    }

    private fun starteGnssStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            StandortLiveQuelle.gnssStatusFlow(requireContext()).collectLatest { status ->
                letzteGnssInfo = status
                val loc = letzterStandort
                val res = letzterKm
                if (loc != null && res != null && aktuelleBaustelleId != null) {
                    aktualisiereOverlayText(aktuelleBaustelleId ?: return@collectLatest, loc, res)
                }
            }
        }
    }

    private fun starteStandortLive() {
        val baustelleId = aktuelleBaustelleId ?: return
        // GPS ist Pflicht -> Foto erst aktivieren, wenn wir mindestens 1 Fix haben.
        btnFoto.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            StandortLiveQuelle.standortFlow(requireContext()).collectLatest { loc ->
                letzterStandort = loc
                val trassenTreffer = berechneTrassenTreffer(loc)
                letzterTrassenTreffer = trassenTreffer

                // Alte Streckenpunkte-km-Logik bleibt nur als Fallback, falls noch keine Trassendaten importiert sind.
                val res = if (trassenTreffer == null && streckenpunkteCache.size >= 2) {
                    kmBerechner.berechne(streckenpunkteCache, loc.latitude, loc.longitude)
                } else {
                    KmBerechner.Ergebnis(KmBerechner.Indikator.ROT, null, null, null, false)
                }
                letzterKm = res
                val widgetFachzeile = trassenTreffer?.toKameraKurzzeile()
                    ?: res.km?.let { km -> "km " + String.format(java.util.Locale.GERMANY, "%.3f", km) }
                de.tbhelfer.widget.WidgetPositionStore.saveApp(
                    requireContext().applicationContext,
                    loc,
                    fachzeile = widgetFachzeile,
                    kontext = aktuellerKontext?.labelKurz
                )
                aktualisiereOverlayText(baustelleId, loc, res)
                aktualisiereKmKalibrierButton()

                // Foto nur erlaubt, wenn GPS da ist (wie besprochen)
                btnFoto.isEnabled = true
            }
        }
    }

    private fun aktualisiereOverlayText(baustelleId: Long, loc: Location, res: KmBerechner.Ergebnis) {
        val trassen = letzterTrassenTreffer
        val gpsAccuracy = if (loc.hasAccuracy()) String.format(Locale.GERMANY, "%.1f m", loc.accuracy) else "-"
        val gnssText = letzteGnssInfo?.alsText()

        textStatus.text = buildString {
            append(aktuelleBaustellenName ?: "Baustelle-ID: $baustelleId").append('\n')
            aktuellerKontext?.labelKurz?.let { append("Kontext: ").append(it).append('\n') }
            if (gnssText != null) append(gnssText).append('\n')

            if (trassen != null) {
                append(trassen.toKameraKurzzeile()).append('\n')
                append("GPS: ")
                    .append(String.format(Locale.GERMANY, "%.6f", loc.latitude))
                    .append(", ")
                    .append(String.format(Locale.GERMANY, "%.6f", loc.longitude))
                    .append(" · ")
                    .append(gpsAccuracy)
                return@buildString
            }

            if (trassenAchsenCache.isNotEmpty() && trassenSamplepunkteCache.isNotEmpty()) {
                append("Keine passende Achse für aktuellen Kontext.\n")
                append("Auswahl: ").append(aktuellerKontext?.labelKurz ?: "neutral").append('\n')
                append("GPS: ")
                    .append(String.format(Locale.GERMANY, "%.6f", loc.latitude))
                    .append(", ")
                    .append(String.format(Locale.GERMANY, "%.6f", loc.longitude))
                    .append(" · ")
                    .append(gpsAccuracy)
                return@buildString
            }

            // Fallback auf alte Streckenpunkte-Logik, falls keine Trassendaten importiert sind.
            val indikator = when (res.indikator) {
                KmBerechner.Indikator.GRUEN -> "🟢"
                KmBerechner.Indikator.GELB -> "🟡"
                KmBerechner.Indikator.ROT -> "🔴"
            }
            val kmText = res.km?.let { String.format(Locale.GERMANY, "%.3f", rundeKmAuf10m(it)) } ?: "-"
            val abstandText = res.achsabstandM?.let { String.format(Locale.GERMANY, "%.1f m", it) } ?: "-"
            append("Indikator: $indikator  (${res.indikator})\n")
            append("Live-km: $kmText\n")
            append("Abstand zur berechneten Achse: $abstandText")
        }
    }

    private fun TrassenNaheBerechner.Ergebnis.toKameraKurzzeile(): String {
        val objekt = letzterStreckenObjektStrang?.let { strang ->
            "${aktuellerKontext?.objektKennung.orEmpty().ifBlank { achseName }} · $strang"
        } ?: when (achsTyp) {
            TrassenAchseEntity.ACHSTYP_GLEIS -> "Gl ${gleis.orEmpty().ifBlank { achseName }}"
            TrassenAchseEntity.ACHSTYP_WEICHE -> "W ${anzeigeWeichenName()}"
            TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> "WV ${anzeigeWeichenName()}"
            else -> achseName
        }
        val kmText = kmMeter?.let { "km " + String.format(Locale.GERMANY, "%.3f", it / 1000.0) }
        val gvpText = naechsterGvpText(this)
        val abstandText = "Abstand " + String.format(Locale.GERMANY, "%.1f m", abstandM)
        val qualitaetText = qualitaet.name.lowercase(Locale.GERMANY)
        return listOfNotNull(objekt, kmText, gvpText, abstandText, qualitaetText).joinToString(" · ")
    }

    private fun TrassenNaheBerechner.Ergebnis.kameraObjektText(): String {
        letzterStreckenObjektStrang?.let { strang ->
            return "${aktuellerKontext?.objektKennung.orEmpty().ifBlank { achseName }} · $strang"
        }
        return when (achsTyp) {
            TrassenAchseEntity.ACHSTYP_GLEIS -> "Gl ${gleis.orEmpty().ifBlank { achseName }}"
            TrassenAchseEntity.ACHSTYP_WEICHE -> "W ${anzeigeWeichenName()}"
            TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> "WV ${anzeigeWeichenName()}"
            else -> achseName
        }
    }

    private fun TrassenNaheBerechner.Ergebnis.anzeigeWeichenName(): String {
        val zuordnung = ladeWeichenZuordnung(achseName)
        return zuordnung?.dbWeichenName?.takeIf { it.isNotBlank() } ?: achseName.removePrefix("SW_").removePrefix("WV_").removeSuffix("_ER0")
    }

    private fun naechsterGvpText(ergebnis: TrassenNaheBerechner.Ergebnis): String? {
        val km = ergebnis.kmMeter ?: return null
        val strecke = ergebnis.strecke?.trim().orEmpty()
        val kandidat = gvpPunkteCache
            .asSequence()
            .filter { it.punktName.isNotBlank() && it.kmMeter != null }
            .filter { strecke.isBlank() || it.strecke?.trim() == strecke }
            .minByOrNull { kotlin.math.abs((it.kmMeter ?: km) - km) }
            ?: return null
        val diff = kotlin.math.abs((kandidat.kmMeter ?: km) - km)
        return if (diff <= 80.0) "GVP ${kandidat.punktName}" else null
    }

    private fun gvpZwischenText(ergebnis: TrassenNaheBerechner.Ergebnis): String? {
        val km = ergebnis.kmMeter ?: return null
        val strecke = ergebnis.strecke?.trim().orEmpty()
        val gruppen = gvpPunkteCache
            .asSequence()
            .filter { it.punktName.isNotBlank() && it.kmMeter != null }
            .filter { strecke.isBlank() || it.strecke?.trim() == strecke }
            .groupBy { kotlin.math.round((it.kmMeter ?: 0.0) * 1000.0) / 1000.0 }
            .map { (kmKey, liste) -> kmKey to liste }
            .sortedBy { it.first }
        if (gruppen.isEmpty()) return null

        val unten = gruppen.lastOrNull { it.first <= km }
        val oben = gruppen.firstOrNull { it.first >= km && it.first != unten?.first }

        fun nameAus(liste: List<GvpPointEntity>): String {
            val best = liste.minByOrNull { p ->
                val lat = p.lat
                val lon = p.lon
                if (lat == null || lon == null) Double.MAX_VALUE else distM(ergebnis.projLat, ergebnis.projLon, lat, lon)
            } ?: liste.first()
            return best.punktName
        }

        return when {
            unten != null && oben != null -> {
                val dU = km - unten.first
                val dO = oben.first - km
                "${nameAus(unten.second)} +${fmtM(dU)} → ${nameAus(oben.second)} -${fmtM(dO)}"
            }
            unten != null -> "ab ${nameAus(unten.second)} +${fmtM(km - unten.first)}"
            oben != null -> "bis ${nameAus(oben.second)} -${fmtM(oben.first - km)}"
            else -> null
        }
    }

    private fun apKorZwischenText(ergebnis: TrassenNaheBerechner.Ergebnis): String? {
        if (absteckpunkteCache.isEmpty()) return null
        val gleisNorm = KontextNormalisierung.normalizeGleis(ergebnis.gleis)
        val axisSamples = trassenSamplepunkteCache
            .filter { it.achseId == ergebnis.achseId }
            .sortedBy { it.station }
        if (axisSamples.isEmpty()) return null

        data class ApStation(val punkt: AbsteckpunktEntity, val station: Double, val abstandZurAchse: Double)

        fun baueApStations(nurGleichesGleis: Boolean): List<ApStation> = absteckpunkteCache.asSequence()
            .filter { it.lat != null && it.lon != null }
            .filter {
                !nurGleichesGleis || gleisNorm.isBlank() || KontextNormalisierung.normalizeGleis(it.gleis) == gleisNorm
            }
            .mapNotNull { ap ->
                val lat = ap.lat ?: return@mapNotNull null
                val lon = ap.lon ?: return@mapNotNull null
                val best = axisSamples.minByOrNull { s -> distM(lat, lon, s.lat, s.lon) } ?: return@mapNotNull null
                val d = distM(lat, lon, best.lat, best.lon)
                // Patch 33: AP/KOR wird als Baustellen-Orientierung immer zwischen zwei APs
                // entlang der Achse ausgegeben. Daher etwas toleranter als vorher; falls keine
                // APs des gewaehlten Gleises passen, wird als Fallback ohne Gleisfilter gesucht.
                if (d <= 80.0) ApStation(ap, best.station, d) else null
            }
            .distinctBy { it.punkt.punktName }
            .sortedBy { it.station }
            .toList()

        val apStations = baueApStations(nurGleichesGleis = true).ifEmpty { baueApStations(nurGleichesGleis = false) }
        if (apStations.isEmpty()) return null

        val unten = apStations.lastOrNull { it.station <= ergebnis.station }
        val oben = apStations.firstOrNull { it.station >= ergebnis.station && it.punkt.punktName != unten?.punkt?.punktName }

        return when {
            unten != null && oben != null -> {
                val dU = ergebnis.station - unten.station
                val dO = oben.station - ergebnis.station
                "AP ${unten.punkt.punktName} +${fmtM(dU)} → AP ${oben.punkt.punktName} -${fmtM(dO)}"
            }
            unten != null -> "ab AP ${unten.punkt.punktName} +${fmtM(ergebnis.station - unten.station)}"
            oben != null -> "bis AP ${oben.punkt.punktName} -${fmtM(oben.station - ergebnis.station)}"
            else -> null
        }
    }

    private fun fmtM(meter: Double): String {
        val m = meter.coerceAtLeast(0.0)
        return if (m >= 10.0) String.format(Locale.GERMANY, "%.0f m", m) else String.format(Locale.GERMANY, "%.1f m", m)
    }

    private fun distM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dp / 2) * kotlin.math.sin(dp / 2) +
            kotlin.math.cos(p1) * kotlin.math.cos(p2) *
            kotlin.math.sin(dl / 2) * kotlin.math.sin(dl / 2)
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    private fun aktualisiereKmKalibrierButton() {
        if (!::btnStreckenpunkt.isInitialized) return

        // Patch45 NEU: Ein orange kalibrierter manueller Abschnitt braucht in der Kamera
        // keinen weiteren KM-Button. Ein grauer Abschnitt bleibt dagegen pruefbar.
        val aktuellerTreffer = letzterTrassenTreffer
        if (aktuellerTreffer?.kmQuelle == "MANUELL_ABSCHNITT" && aktuellerTreffer.kmMeter != null) {
            btnStreckenpunkt.visibility = View.GONE
            return
        }
        if (aktuellerTreffer?.kmQuelle == "MANUELL_ABSCHNITT_UNPLAUSIBEL") {
            btnStreckenpunkt.text = "KM prüfen"
            btnStreckenpunkt.visibility = View.VISIBLE
            btnStreckenpunkt.isEnabled = true
            return
        }

        val kandidaten = gefilterteTrassenAchsen()
        val problemAchsen = kandidaten.map { achse -> achse to kmBewertungFuerAchse(achse) }
            .filter { (_, bewertung) ->
                bewertung.status == TrassenKmStatusLogik.STATUS_FEHLT ||
                    bewertung.status == TrassenKmStatusLogik.STATUS_UNPLAUSIBEL
            }

        if (problemAchsen.isEmpty()) {
            // Gruen (automatisch) und Orange (manuell 2-Punkt): kein zusaetzlicher Button noetig.
            btnStreckenpunkt.visibility = View.GONE
            return
        }

        val direkteAchse = zielAchseFuerKmKalibrierung()
        val direkteBewertung = direkteAchse?.let { ziel ->
            problemAchsen.firstOrNull { (achse, _) -> achse.achseId == ziel.achseId }?.second
        }

        val status = direkteBewertung?.status ?: when {
            // Bei mehreren passenden Achsen ohne aktuellen GPS-Treffer bleibt der Button sichtbar.
            // Beim Antippen folgt die Achsauswahl. Das ist wichtig direkt nach der Baustellen-/Unterbaustellenwahl.
            problemAchsen.any { (_, bewertung) -> bewertung.status == TrassenKmStatusLogik.STATUS_FEHLT } ->
                TrassenKmStatusLogik.STATUS_FEHLT
            else -> TrassenKmStatusLogik.STATUS_UNPLAUSIBEL
        }

        btnStreckenpunkt.text = if (status == TrassenKmStatusLogik.STATUS_UNPLAUSIBEL) "KM prüfen" else "KM kalibrieren"
        btnStreckenpunkt.visibility = View.VISIBLE
        btnStreckenpunkt.isEnabled = true
    }

    private fun zielAchseFuerKmKalibrierung(): TrassenNaheBerechner.AchsInfo? {
        val kandidaten = gefilterteTrassenAchsen()
        if (kandidaten.isEmpty()) return null
        val letzterId = letzterTrassenTreffer?.achseId
        kandidaten.firstOrNull { it.achseId == letzterId }?.let { return it }
        if (kandidaten.size == 1) return kandidaten.first()
        val loc = letzterStandort ?: return null
        return TrassenNaheBerechner.findeNaechsteAchse(
            lat = loc.latitude,
            lon = loc.longitude,
            achsen = kandidaten,
            punkte = trassenSamplepunkteCache,
            maxAbstandM = 500.0,
            gleisVorrang = false
        )?.let { hit -> kandidaten.firstOrNull { it.achseId == hit.achseId } }
    }

    private fun kmBewertungFuerAchse(achse: TrassenNaheBerechner.AchsInfo): TrassenKmStatusLogik.Bewertung {
        val punkte = trassenSamplepunkteCache
            .filter { it.achseId == achse.achseId }
            .map { TrassenKmStatusLogik.Punkt(it.station, it.kmMeter.takeIf { _ -> it.kmQuelle != null }) }
        val anker = trassenKmAnkerCache
            .filter { it.aktuelleAchseId == achse.achseId }
            .map { TrassenKmStatusLogik.Anker(it.punktNummer, it.station, it.kmMeter) }
        return TrassenKmStatusLogik.bewerte(punkte, anker)
    }

    private fun starteKmKalibrierungAusKamera() {
        val kandidaten = gefilterteTrassenAchsen()
            .filter { achse ->
                val status = kmBewertungFuerAchse(achse).status
                status == TrassenKmStatusLogik.STATUS_FEHLT || status == TrassenKmStatusLogik.STATUS_UNPLAUSIBEL
            }
        if (kandidaten.isEmpty()) {
            btnStreckenpunkt.visibility = View.GONE
            Toast.makeText(requireContext(), "Die gewählte Achse hat bereits eine plausible KM-Kalibrierung.", Toast.LENGTH_SHORT).show()
            return
        }

        val direkt = zielAchseFuerKmKalibrierung()?.takeIf { ziel -> kandidaten.any { it.achseId == ziel.achseId } }
        if (direkt != null) {
            zeigeKmKalibrierungDialog(direkt)
            return
        }
        if (kandidaten.size == 1) {
            zeigeKmKalibrierungDialog(kandidaten.first())
            return
        }

        val labels = kandidaten.map { it.kameraAchsenName() }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Achse für KM-Kalibrierung")
            .setItems(labels) { _, which -> zeigeKmKalibrierungDialog(kandidaten[which]) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun zeigeKmKalibrierungDialog(achse: TrassenNaheBerechner.AchsInfo) {
        val bewertung = kmBewertungFuerAchse(achse)
        val vorhandeneAnker = trassenKmAnkerCache
            .filter { it.aktuelleAchseId == achse.achseId }
            .sortedBy { it.punktNummer }
        val punktNummer = when {
            vorhandeneAnker.none { it.punktNummer == 1 } -> 1
            vorhandeneAnker.none { it.punktNummer == 2 } -> 2
            else -> 1
        }

        if (vorhandeneAnker.size >= 2) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("KM-Kalibrierung neu beginnen?")
                .setMessage(
                    "${achse.kameraAchsenName()} hat bereits zwei manuelle Punkte, die aber nicht plausibel sind. " +
                        "Für eine neue Kalibrierung werden beide alten Punkte gelöscht."
                )
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Neu beginnen") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        ServiceLocator.trassenRepository.loescheManuelleKmKalibrierung(achse.achseId)
                        ladeTrassenDatenFuerKamera()
                        zeigeKmEingabeDialog(achse, 1)
                    }
                }
                .show()
            return
        }

        val statusText = when (bewertung.status) {
            TrassenKmStatusLogik.STATUS_FEHLT -> "Rot · keine KM-Kalibrierung"
            TrassenKmStatusLogik.STATUS_UNPLAUSIBEL -> "Grau · KM unplausibel"
            else -> bewertung.grund
        }
        val ankerText = vorhandeneAnker.joinToString("\n") { a ->
            "P${a.punktNummer}: km ${String.format(Locale.GERMANY, "%.3f", a.kmMeter / 1000.0)} · Achsstation ${String.format(Locale.GERMANY, "%.1f m", a.station)}"
        }.ifBlank { "Noch kein manueller Punkt gespeichert." }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${achse.kameraAchsenName()} · KM kalibrieren")
            .setMessage(
                "$statusText\n\n$ankerText\n\n" +
                    "Punkt $punktNummer von 2: Stelle dich an einen Punkt mit bekanntem km. " +
                    "Die App mittelt GPS 20 Sekunden und projiziert den Standort automatisch auf diese TRA-Achse.\n\n" +
                    "Zwischen Punkt 1 und Punkt 2 mindestens 20 m, empfohlen 50 m oder mehr."
            )
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Punkt $punktNummer aufnehmen") { _, _ ->
                zeigeKmEingabeDialog(achse, punktNummer)
            }
            .show()
    }

    private fun zeigeKmEingabeDialog(achse: TrassenNaheBerechner.AchsInfo, punktNummer: Int) {
        val feld = EditText(requireContext()).apply {
            hint = "z. B. 23,034"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 12, 32, 12)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Punkt $punktNummer · bekannten km eingeben")
            .setMessage("Du gibst nur den echten Bahn-km ein. Die Achsstation ermittelt die App selbst aus GPS + TRA.")
            .setView(feld)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("GPS 20 s aufnehmen", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val km = feld.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                if (km == null || km < 0.0 || km > 999.999) {
                    Toast.makeText(requireContext(), "Bitte einen gültigen km eingeben, z. B. 23,034.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                starteGpsAufnahmeFuerKmAnker(achse, punktNummer, km * 1000.0)
            }
        }
        dialog.show()
    }

    private fun starteGpsAufnahmeFuerKmAnker(
        achse: TrassenNaheBerechner.AchsInfo,
        punktNummer: Int,
        kmMeter: Double
    ) {
        if (!StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
            Toast.makeText(requireContext(), "Standort-Berechtigung fehlt.", Toast.LENGTH_LONG).show()
            starteOderStandortBerechtigungFragen()
            return
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 20, 36, 20)
        }
        val status = TextView(requireContext()).apply {
            text = "GPS wird gesammelt… 20 s"
            textSize = 16f
        }
        val progress = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 20
            progress = 0
        }
        layout.addView(status)
        layout.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("${achse.kameraAchsenName()} · Punkt $punktNummer")
            .setView(layout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val messung = sammleStabilenStandortFuerKm { sekunden, fixes ->
                    status.text = "GPS wird gesammelt… ${sekunden}s · Fixes $fixes"
                    progress.progress = 20 - sekunden
                } ?: error("Kein stabiler GPS-Fix empfangen.")

                val samples = trassenSamplepunkteCache.filter { it.achseId == achse.achseId }
                val treffer = TrassenNaheBerechner.findeNaechsteAchse(
                    lat = messung.latitude,
                    lon = messung.longitude,
                    achsen = listOf(achse),
                    punkte = samples,
                    maxAbstandM = 100.0,
                    gleisVorrang = false
                ) ?: error("GPS-Punkt konnte nicht auf ${achse.kameraAchsenName()} projiziert werden.")

                if (treffer.abstandM > 25.0) {
                    error("GPS-Punkt liegt ${String.format(Locale.GERMANY, "%.1f", treffer.abstandM)} m von der gewählten Achse entfernt. Bitte näher an der richtigen Achse aufnehmen.")
                }
                if ((messung.accuracyM ?: 0f) > 15f) {
                    error("GPS-Genauigkeit ist mit ${String.format(Locale.GERMANY, "%.1f", messung.accuracyM)} m zu schlecht. Bitte erneut aufnehmen.")
                }

                // Nicht ueber ein TRA-Achsende hinaus kalibrieren: Liegt die Projektion
                // praktisch am ersten/letzten Achspunkt, der reale GPS-Punkt aber deutlich
                // daneben, wurde sehr wahrscheinlich das Achsende bzw. ein Weichenbereich
                // ueberschritten. Dann muss zuerst die richtige Anschlussachse gewaehlt werden.
                val stationMin = samples.minOfOrNull { it.station }
                val stationMax = samples.maxOfOrNull { it.station }
                val amAchsende = (stationMin != null && kotlin.math.abs(treffer.station - stationMin) <= 0.5) ||
                    (stationMax != null && kotlin.math.abs(treffer.station - stationMax) <= 0.5)
                val endeToleranz = maxOf(5.0, ((messung.accuracyM ?: 3f) * 1.5f).toDouble())
                if (amAchsende && treffer.abstandM > endeToleranz) {
                    val anschluesse = TrassenNaheBerechner.findeAnschlussAchsenAmAchsende(
                        achseId = achse.achseId,
                        stationNaheEnde = treffer.station,
                        achsen = trassenAchsenCache,
                        punkte = trassenSamplepunkteCache,
                        maxAbstandM = 6.0,
                        limit = 4
                    )
                    val anschlussText = if (anschluesse.isEmpty()) {
                        "Keine eindeutige Anschlussachse innerhalb 6 m gefunden."
                    } else {
                        "Nahe Anschlussachsen: " + anschluesse.joinToString(", ") { kandidat ->
                            val label = trassenAchsenCache.firstOrNull { it.achseId == kandidat.achseId }?.kameraAchsenName()
                                ?: kandidat.achseName
                            "$label (${String.format(Locale.GERMANY, "%.1f", kandidat.abstandM)} m)"
                        }
                    }
                    error(
                        "Achsende erreicht oder ueberschritten. Der aktuelle GPS-Punkt liegt " +
                            "${String.format(Locale.GERMANY, "%.1f", treffer.abstandM)} m vom Ende der gewaehlten Achse entfernt. " +
                            "$anschlussText Bitte die richtige Anschlussachse/Weiche waehlen und dort neu kalibrieren."
                    )
                }

                val ergebnis = ServiceLocator.trassenRepository.speichereManuellenKmAnker(
                    achseId = achse.achseId,
                    punktNummer = punktNummer,
                    station = treffer.station,
                    kmMeter = kmMeter,
                    gpsLat = messung.latitude,
                    gpsLon = messung.longitude,
                    gpsAccuracyM = messung.accuracyM,
                    achsabstandM = treffer.abstandM
                )
                Triple(messung, treffer, ergebnis)
            }.onSuccess { (messung, treffer, ergebnis) ->
                progressDialog.dismiss()
                ladeTrassenDatenFuerKamera()
                val gpsText = messung.accuracyM?.let { "GPS ±${String.format(Locale.GERMANY, "%.1f", it)} m" } ?: "GPS"
                if (punktNummer == 1 && !ergebnis.angewendet) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Punkt 1 gespeichert")
                        .setMessage(
                            "km ${String.format(Locale.GERMANY, "%.3f", kmMeter / 1000.0)}\n" +
                                "Achsabstand ${String.format(Locale.GERMANY, "%.1f", treffer.abstandM)} m · $gpsText\n\n" +
                                "Gehe jetzt mindestens 20 m, empfohlen 50 m oder mehr, auf derselben Achse weiter und nimm Punkt 2 auf."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                } else if (ergebnis.angewendet) {
                    val richtung = if (ergebnis.steigend == true) "km steigend" else "km fallend"
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("KM-Kalibrierung fertig")
                        .setMessage(
                            "${achse.kameraAchsenName()} ist jetzt orange = manuell kalibriert.\n\n" +
                                "Achsabstand der Punkte: ${String.format(Locale.GERMANY, "%.1f m", ergebnis.stationAbstandM ?: 0.0)}\n" +
                                "KM-Differenz: ${String.format(Locale.GERMANY, "%.1f m", ergebnis.kmAbstandM ?: 0.0)}\n" +
                                "Richtung: $richtung\n\n" +
                                "Die App hat die KM-Werte über die gesamte TRA-Achse berechnet."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("KM-Kalibrierung nicht plausibel")
                        .setMessage(ergebnis.hinweis ?: "Die beiden Punkte konnten nicht plausibel verrechnet werden.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }.onFailure { throwable ->
                progressDialog.dismiss()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("KM-Punkt nicht gespeichert")
                    .setMessage(throwable.message ?: throwable.javaClass.simpleName)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private suspend fun sammleStabilenStandortFuerKm(
        onFortschritt: (restSekunden: Int, fixCount: Int) -> Unit
    ): KameraGpsMessung? {
        val fixes = mutableListOf<Location>()
        coroutineScope {
            val job = launch {
                StandortLiveQuelle.standortFlow(
                    context = requireContext(),
                    minTimeMs = 1000L,
                    minDistanceM = 0.0f,
                    emitLastKnown = false,
                    glaettungFenster = 1
                ).collectLatest { loc -> fixes += Location(loc) }
            }
            for (rest in 20 downTo 1) {
                onFortschritt(rest, fixes.size)
                delay(1000L)
            }
            job.cancelAndJoin()
        }
        return fixes.toKameraGpsMessung()
    }

    private fun List<Location>.toKameraGpsMessung(): KameraGpsMessung? {
        if (isEmpty()) return null
        val plausible = filter { !it.hasAccuracy() || it.accuracy <= 40f }.ifEmpty { this }
        val beste = plausible
            .sortedBy { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
            .take(8)
            .ifEmpty { plausible }
        val gewichte = beste.map { loc ->
            val acc = if (loc.hasAccuracy()) loc.accuracy.coerceAtLeast(1f).toDouble() else 10.0
            1.0 / (acc * acc)
        }
        val sum = gewichte.sum().takeIf { it > 0.0 } ?: return null
        val lat = beste.zip(gewichte).sumOf { (loc, w) -> loc.latitude * w } / sum
        val lon = beste.zip(gewichte).sumOf { (loc, w) -> loc.longitude * w } / sum
        val accs = beste.mapNotNull { if (it.hasAccuracy()) it.accuracy else null }.sorted()
        val acc = when {
            accs.isEmpty() -> null
            accs.size % 2 == 1 -> accs[accs.size / 2]
            else -> (accs[accs.size / 2 - 1] + accs[accs.size / 2]) / 2f
        }
        return KameraGpsMessung(lat, lon, acc, size)
    }

    private fun TrassenNaheBerechner.AchsInfo.kameraAchsenName(): String = when (achsTyp) {
        TrassenAchseEntity.ACHSTYP_GLEIS -> "Gl ${gleis.orEmpty().ifBlank { name }}"
        TrassenAchseEntity.ACHSTYP_WEICHE -> "Weiche $name"
        TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> "WV $name"
        TrassenAchseEntity.ACHSTYP_STRECKE -> {
            val ri = richtung?.takeIf { it.isNotBlank() }?.let { "-$it" }.orEmpty()
            "Str ${strecke.orEmpty().ifBlank { name }}$ri"
        }
        else -> name
    }

    private data class ObjektLinienTreffer(
        val label: String,
        val abstandM: Double,
        val stationM: Double,
        val projLat: Double,
        val projLon: Double
    )

    private data class KameraGpsMessung(
        val latitude: Double,
        val longitude: Double,
        val accuracyM: Float?,
        val fixCount: Int
    )

    private fun berechneOptionalesStreckenObjektTreffer(loc: Location): TrassenNaheBerechner.Ergebnis? {
        val kontext = aktuellerKontext ?: return null
        if (kontext.art != TrassenKontextArt.BAHNHOF_WEICHE) return null
        val kennung = kontext.weiche ?: kontext.objektKennung ?: return null
        val typ = when (BaustellenObjektLogik.artAusKennung(kennung)) {
            BaustellenObjektArt.DKW_KREUZUNG -> StreckenObjektSpeicher.Typ.DKW_KREUZUNG
            BaustellenObjektArt.WEICHE -> StreckenObjektSpeicher.Typ.WEICHE
            else -> return null
        }
        val objekt = StreckenObjektSpeicher.findePassend(requireContext(), kontext.bahnhof, kennung, typ) ?: return null
        val punkte = objekt.punkte.associateBy { it.rolle }
        val paare = if (typ == StreckenObjektSpeicher.Typ.WEICHE) {
            listOf("WA–WE" to ("WA" to "WE"), "WA–WEZ" to ("WA" to "WEZ"))
        } else {
            listOf("D–A" to ("D" to "A"), "C–B" to ("C" to "B"))
        }
        val linien = paare.mapNotNull { (label, rollen) ->
            val a = punkte[rollen.first] ?: return@mapNotNull null
            val b = punkte[rollen.second] ?: return@mapNotNull null
            val aLat = a.lat ?: return@mapNotNull null
            val aLon = a.lon ?: return@mapNotNull null
            val bLat = b.lat ?: return@mapNotNull null
            val bLon = b.lon ?: return@mapNotNull null
            projektAufObjektLinie(loc.latitude, loc.longitude, aLat, aLon, bLat, bLon)?.copy(label = label)
        }
        val naechsteLinie = linien.minByOrNull { it.abstandM } ?: return null
        if (letztesStreckenObjektId != objekt.id) {
            letzterStreckenObjektStrang = null
            letztesStreckenObjektId = objekt.id
        }
        // Am Schnittpunkt einer DKW/Kreuzung bzw. am gemeinsamen WA sind beide
        // Achsen geometrisch gleich nah. Dann bleibt die bereits erkannte Achse
        // bestehen, damit keine kreuzende Achse automatisch übernommen wird.
        val bisherigeLinie = letzterStreckenObjektStrang?.let { label ->
            linien.firstOrNull { it.label == label }
        }
        val linie = if (bisherigeLinie != null && bisherigeLinie.abstandM <= naechsteLinie.abstandM + 2.0) {
            bisherigeLinie
        } else {
            naechsteLinie
        }
        letzterStreckenObjektStrang = linie.label

        val referenz = objekt.traAchseId?.let { id ->
            val achse = trassenAchsenCache.firstOrNull { it.achseId == id } ?: return@let null
            TrassenNaheBerechner.findeNaechsteAchse(
                lat = loc.latitude,
                lon = loc.longitude,
                achsen = listOf(achse),
                punkte = trassenSamplepunkteCache,
                maxAbstandM = 500.0,
                gleisVorrang = false
            )?.let(::ueberlagereManuellenAbschnitt)
        }
        val qualitaet = when {
            linie.abstandM <= 2.0 -> TrassenNaheBerechner.Qualitaet.GRUEN
            linie.abstandM <= 5.0 -> TrassenNaheBerechner.Qualitaet.GELB
            linie.abstandM <= 15.0 -> TrassenNaheBerechner.Qualitaet.ORANGE
            else -> TrassenNaheBerechner.Qualitaet.ROT
        }
        return if (referenz != null) {
            referenz.copy(
                achseName = objekt.bezeichnung,
                achsTyp = TrassenAchseEntity.ACHSTYP_WEICHE,
                gleis = null,
                abstandM = linie.abstandM,
                qualitaet = qualitaet,
                projLat = linie.projLat,
                projLon = linie.projLon,
                kmMeter = if (objekt.traHatKm) referenz.kmMeter else null
            )
        } else {
            TrassenNaheBerechner.Ergebnis(
                achseId = objekt.traAchseId ?: -objekt.id.hashCode().toLong().coerceAtLeast(1L),
                achseName = objekt.bezeichnung,
                achsTyp = TrassenAchseEntity.ACHSTYP_WEICHE,
                strecke = null,
                gleis = null,
                richtung = null,
                station = linie.stationM,
                kmMeter = null,
                abstandM = linie.abstandM,
                seite = TrassenNaheBerechner.Seite.UNBEKANNT,
                qualitaet = qualitaet,
                projLat = linie.projLat,
                projLon = linie.projLon,
                vonPunktId = 0L,
                bisPunktId = 0L
            )
        }
    }

    private fun projektAufObjektLinie(
        lat: Double,
        lon: Double,
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double
    ): ObjektLinienTreffer? {
        val mLat = 111_320.0
        val mLon = kotlin.math.max(1.0, 111_320.0 * kotlin.math.cos(Math.toRadians(lat)))
        val ax = (aLon - lon) * mLon
        val ay = (aLat - lat) * mLat
        val bx = (bLon - lon) * mLon
        val by = (bLat - lat) * mLat
        val vx = bx - ax
        val vy = by - ay
        val len2 = vx * vx + vy * vy
        if (len2 <= 0.000001) return null
        val t = ((-ax * vx - ay * vy) / len2).coerceIn(0.0, 1.0)
        val px = ax + t * vx
        val py = ay + t * vy
        return ObjektLinienTreffer(
            label = "",
            abstandM = kotlin.math.hypot(px, py),
            stationM = kotlin.math.sqrt(len2) * t,
            projLat = lat + py / mLat,
            projLon = lon + px / mLon
        )
    }

    private fun berechneTrassenTreffer(loc: Location): TrassenNaheBerechner.Ergebnis? {
        berechneOptionalesStreckenObjektTreffer(loc)?.let { return it }
        letzterStreckenObjektStrang = null
        letztesStreckenObjektId = null
        val achsen = gefilterteTrassenAchsen()
        if (achsen.isEmpty() || trassenSamplepunkteCache.isEmpty()) return null
        val gleisVorrang = aktuellerKontext == null || aktuellerKontext?.art == TrassenKontextArt.STRECKE
        val kandidaten = TrassenNaheBerechner.findeNaechsteAchsen(
            lat = loc.latitude,
            lon = loc.longitude,
            achsen = achsen,
            punkte = trassenSamplepunkteCache,
            maxAbstandM = if (aktuellerKontext == null) 250.0 else 500.0,
            limit = 12,
            gleisVorrang = gleisVorrang
        )
        val verbessert = kandidaten.map { ueberlagereManuellenAbschnitt(it) }
        return if (aktuellerKontext == null) {
            verbessert.firstOrNull()
        } else {
            verbessert.firstOrNull { trefferPasstZumKontext(it, aktuellerKontext) }
        }
    }

    private fun ueberlagereManuellenAbschnitt(
        kandidat: TrassenNaheBerechner.Ergebnis
    ): TrassenNaheBerechner.Ergebnis {
        val abschnittKandidaten = trassenManuelleAbschnitteCache
            .asSequence()
            .filter { it.aktuelleAchseId == kandidat.achseId }
            .filter { abschnitt ->
                val grenze1 = abschnitt.stationStart
                val grenze2 = abschnitt.stationEnde
                grenze1 == null || grenze2 == null ||
                    (kandidat.station >= minOf(grenze1, grenze2) - 0.01 &&
                        kandidat.station <= maxOf(grenze1, grenze2) + 0.01)
            }
            .sortedByDescending { it.aktualisiertAmEpochMs }
            .toList()
        val abschnitt = aktuellerKontext?.let { kontext ->
            abschnittKandidaten.firstOrNull { manuellerAbschnittPasstZumKontext(it, kontext) }
        } ?: abschnittKandidaten.firstOrNull() ?: return kandidat

        val kmErgebnis = ManuelleAbschnittKmLogik.kmFuerStation(
            abschnitt = abschnitt,
            station = kandidat.station,
            punkte = trassenManuelleAbschnittKmPunkteCache.filter { it.abschnittId == abschnitt.id }
        )
        return kandidat.copy(
            achseName = abschnitt.bezeichnung,
            achsTyp = abschnitt.achsTyp,
            strecke = abschnitt.strecke,
            gleis = abschnitt.gleis,
            richtung = abschnitt.richtung,
            kmMeter = when {
                kmErgebnis.kmMeter != null -> kmErgebnis.kmMeter
                kmErgebnis.quelle == "MANUELL_ABSCHNITT_UNPLAUSIBEL" -> null
                else -> kandidat.kmMeter
            },
            kmQuelle = when {
                kmErgebnis.kmMeter != null -> kmErgebnis.quelle
                kmErgebnis.quelle == "MANUELL_ABSCHNITT_UNPLAUSIBEL" -> kmErgebnis.quelle
                else -> kandidat.kmQuelle
            }
        )
    }

    private fun manuellerAbschnittPasstZumKontext(
        abschnitt: de.tbhelfer.datenbank.TrassenManuellerAbschnittEntity,
        kontext: AktiverTrassenKontext
    ): Boolean = when (kontext.art) {
        TrassenKontextArt.BAHNHOF_GLEIS -> {
            val erlaubt = erlaubteGleise(kontext.gleis)
            abschnitt.achsTyp == TrassenAchseEntity.ACHSTYP_GLEIS &&
                (erlaubt.isEmpty() || erlaubt.contains(KontextNormalisierung.normalizeGleis(abschnitt.gleis)))
        }
        TrassenKontextArt.STRECKE -> {
            val (strecke, kennziffer) = streckeUndKennziffer(kontext.streckennummer)
            abschnitt.achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE &&
                (strecke.isBlank() || KontextNormalisierung.normalizeStrecke(abschnitt.strecke) == strecke) &&
                richtungPasstZuKennziffer(abschnitt.richtung, kennziffer)
        }
        TrassenKontextArt.BAHNHOF_WEICHE -> false
    }

    private fun trefferPasstZumKontext(
        treffer: TrassenNaheBerechner.Ergebnis,
        kontext: AktiverTrassenKontext?
    ): Boolean {
        kontext ?: return true
        return when (kontext.art) {
            TrassenKontextArt.BAHNHOF_GLEIS -> {
                val erlaubt = erlaubteGleise(kontext.gleis)
                erlaubt.isEmpty() || erlaubt.contains(KontextNormalisierung.normalizeGleis(treffer.gleis))
            }
            TrassenKontextArt.BAHNHOF_WEICHE -> {
                val ziel = KontextNormalisierung.normalizeWeiche(kontext.weiche)
                val istWeichenTyp = treffer.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE ||
                    treffer.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG
                istWeichenTyp && (ziel.isBlank() || KontextNormalisierung.normalizeWeiche(treffer.achseName).contains(ziel))
            }
            TrassenKontextArt.STRECKE -> {
                val (strecke, kennziffer) = streckeUndKennziffer(kontext.streckennummer)
                treffer.achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE &&
                    (strecke.isBlank() || KontextNormalisierung.normalizeStrecke(treffer.strecke) == strecke) &&
                    richtungPasstZuKennziffer(treffer.richtung, kennziffer)
            }
        }
    }

    private fun gefilterteTrassenAchsen(): List<TrassenNaheBerechner.AchsInfo> {
        val kontext = aktuellerKontext ?: return trassenAchsenCache
        return when (kontext.art) {
            TrassenKontextArt.BAHNHOF_GLEIS -> {
                val gleise = erlaubteGleise(kontext.gleis)
                val manuelleIds = trassenManuelleAbschnitteCache.asSequence()
                    .filter { it.achsTyp == TrassenAchseEntity.ACHSTYP_GLEIS }
                    .filter { gleise.isEmpty() || gleise.contains(KontextNormalisierung.normalizeGleis(it.gleis)) }
                    .mapNotNull { it.aktuelleAchseId }
                    .toSet()
                if (gleise.isEmpty()) {
                    trassenAchsenCache.filter {
                        it.achsTyp == TrassenAchseEntity.ACHSTYP_GLEIS || manuelleIds.contains(it.achseId)
                    }
                } else {
                    trassenAchsenCache.filter { achse ->
                        (achse.achsTyp == TrassenAchseEntity.ACHSTYP_GLEIS &&
                            gleise.contains(KontextNormalisierung.normalizeGleis(achse.gleis))) ||
                            manuelleIds.contains(achse.achseId)
                    }
                }
            }
            TrassenKontextArt.BAHNHOF_WEICHE -> {
                val ziel = KontextNormalisierung.normalizeWeiche(kontext.weiche)
                val weichen = trassenAchsenCache.filter { achse ->
                    achse.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE ||
                        achse.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG
                }
                val passend = weichen.filter { achse ->
                    val roh = KontextNormalisierung.normalizeWeiche(achse.name)
                    val db = KontextNormalisierung.normalizeWeiche(ladeWeichenZuordnung(achse.name)?.dbWeichenName)
                    ziel.isNotBlank() && (
                        roh == ziel ||
                            db == ziel ||
                            roh.contains(ziel) ||
                            ziel.contains(roh) ||
                            db.contains(ziel)
                    )
                }
                passend.ifEmpty { weichen }
            }
            TrassenKontextArt.STRECKE -> {
                val (strecke, kennziffer) = streckeUndKennziffer(kontext.streckennummer)
                val manuelleIds = trassenManuelleAbschnitteCache.asSequence()
                    .filter { it.achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE }
                    .filter { strecke.isBlank() || KontextNormalisierung.normalizeStrecke(it.strecke) == strecke }
                    .filter { richtungPasstZuKennziffer(it.richtung, kennziffer) }
                    .mapNotNull { it.aktuelleAchseId }
                    .toSet()
                trassenAchsenCache.filter { achse ->
                    (achse.achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE &&
                        (strecke.isBlank() || KontextNormalisierung.normalizeStrecke(achse.strecke) == strecke) &&
                        richtungPasstZuKennziffer(achse.richtung, kennziffer)) ||
                        manuelleIds.contains(achse.achseId)
                }
            }
        }
    }


    private fun streckeUndKennziffer(raw: String?): Pair<String, String> {
        val text = raw?.trim().orEmpty()
        val strecke = KontextNormalisierung.normalizeStrecke(text.substringBefore("-"))
        val kennziffer = text.substringAfter("-", "").trim()
        return strecke to kennziffer
    }

    private fun richtungPasstZuKennziffer(richtungRaw: String?, kennziffer: String): Boolean {
        val richtung = richtungRaw?.trim()?.uppercase(Locale.GERMANY).orEmpty()
        return when (kennziffer) {
            "0" -> richtung == "0" || richtung == "S"
            "1" -> richtung == "1" || richtung == "L" || richtung == "SL"
            "2" -> richtung == "2" || richtung == "R"
            else -> true
        }
    }

    private fun erlaubteGleise(text: String?): Set<String> {
        val raw = text?.trim().orEmpty()
        if (raw.isBlank()) return emptySet()
        val norm = raw.uppercase(Locale.GERMANY).replace(" ", "")
        if (norm == "BEIDE" || norm == "302/303" || norm == "303/302") return setOf("302", "303")
        return raw.split('/', '+', ',', ';', ' ')
            .map { KontextNormalisierung.normalizeGleis(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun ladeTrassenDatenFuerKamera() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                ServiceLocator.trassenRepository.ladeKameraTrassenDaten()
            }.onSuccess { daten ->
                trassenAchsenCache = daten.achsen
                trassenSamplepunkteCache = daten.samplepunkte
                gvpPunkteCache = daten.gvpPunkte
                absteckpunkteCache = daten.absteckpunkte
                trassenKmAnkerCache = daten.kmAnker
                trassenManuelleAbschnitteCache = daten.manuelleAbschnitte
                trassenManuelleAbschnittKmPunkteCache = daten.manuelleAbschnittKmPunkte
                aktualisiereKmKalibrierButton()
                letzterStandort?.let { loc ->
                    letzterTrassenTreffer = berechneTrassenTreffer(loc)
                    aktualisiereOverlayText(aktuelleBaustelleId ?: return@let, loc, letzterKm ?: KmBerechner.Ergebnis(KmBerechner.Indikator.ROT, null, null, null, false))
                }
            }.onFailure {
                // Kamera darf weiterlaufen; dann bleibt die alte Streckenpunkte-Logik als Fallback.
            }
        }
    }

    private fun weichenPrefs() = requireContext().getSharedPreferences("tbhelfer_weichen_zuordnung", android.content.Context.MODE_PRIVATE)

    private fun ladeWeichenZuordnung(rohName: String): KameraWeichenZuordnung? {
        val gespeichert = weichenPrefs().getString(rohName, null) ?: return null
        val teile = gespeichert.split("|")
        if (teile.size < 6) return null
        return KameraWeichenZuordnung(
            dbWeichenName = teile[0],
            lage = teile[1].ifBlank { "unbekannt" },
            waKmMeter = teile[2].toDoubleOrNull(),
            weKmMeter = teile[3].toDoubleOrNull(),
            wezKmMeter = teile[4].toDoubleOrNull(),
            geaendertAmEpochMs = teile[5].toLongOrNull() ?: 0L
        )
    }

    private data class KameraWeichenZuordnung(
        val dbWeichenName: String,
        val lage: String,
        val waKmMeter: Double?,
        val weKmMeter: Double?,
        val wezKmMeter: Double?,
        val geaendertAmEpochMs: Long
    )

    private fun rundeKmAuf10m(km: Double): Double = kotlin.math.round(km * 100.0) / 100.0

    private fun zeigeNachtAufnahmeDialog() {
        nachtAufnahmeDialog?.dismiss()
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(48, 36, 48, 28)
            addView(TextView(requireContext()).apply {
                text = "Nachtaufnahme\nBitte Handy ruhig halten"
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
            })
            addView(ProgressBar(requireContext()).apply {
                isIndeterminate = true
                setPadding(0, 22, 0, 10)
            })
            addView(TextView(requireContext()).apply {
                text = "Aufnahme und Restlicht-Verarbeitung laufen …"
                textSize = 13f
                gravity = android.view.Gravity.CENTER
            })
        }
        nachtAufnahmeDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(row)
            .setCancelable(false)
            .create().also { it.show() }
        setzeKameraBedienungAktiv(false)
        btnFoto.isEnabled = false
        btnUnterbaustelle.isEnabled = false
    }

    private fun beendeNachtAufnahmeDialog() {
        nachtAufnahmeDialog?.dismiss()
        nachtAufnahmeDialog = null
        // Der CameraX-Callback kann noch eintreffen, nachdem die Ansicht bereits verlassen wurde.
        // Dann keine alten View-Referenzen mehr anfassen.
        if (!isAdded || view == null) return
        setzeKameraBedienungAktiv(true)
        btnFoto.isEnabled = true
        btnUnterbaustelle.isEnabled = !abnahmeMode
    }

    private fun fotoMachen() {
        val baustelleId = aktuelleBaustelleId
        val capture = imageCapture
        if (baustelleId == null || capture == null) {
            Toast.makeText(requireContext(), "Kamera ist noch nicht bereit.", Toast.LENGTH_SHORT).show()
            return
        }

        val baustellenname = aktuelleBaustellenName
        if (baustellenname.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Baustellenname fehlt.", Toast.LENGTH_SHORT).show()
            return
        }

        val standort = letzterStandort
        if (standort == null) {
            Toast.makeText(requireContext(), "GPS ist Pflicht – bitte auf Standort warten.", Toast.LENGTH_SHORT).show()
            return
        }

        val kmRes = letzterKm
        // Wenn wir zu weit weg sind: km muss später manuell eingegeben werden, Foto ist aber erlaubt.

        val datum = LocalDate.now().toString() // yyyy-MM-dd

        val datei = FotoSpeicher.neueFotoDatei(requireContext(), baustellenname, datum)
        val output = ImageCapture.OutputFileOptions.Builder(datei).build()

        wendeKameraEinstellungenAn()
        // Noch einmal unmittelbar vor der Aufnahme aktualisieren. Das ist wichtig,
        // wenn das Handy nach dem Start der Kamera gedreht wurde.
        capture.targetRotation = previewView.display?.rotation ?: capture.targetRotation
        if (kameraEinstellungen.nachtsicht) zeigeNachtAufnahmeDialog()

        capture.takePicture(
            output,
            hauptExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    beendeNachtAufnahmeDialog()
                    Toast.makeText(requireContext(), "Foto gespeichert: ${datei.name}", Toast.LENGTH_SHORT).show()
                    // Foto-Maske öffnen
                    val bundle = Bundle().apply {
                        putString(FotoErfassenFragment.ARG_DATEIPFAD, datei.absolutePath)
                        putLong(FotoErfassenFragment.ARG_BAUSTELLE_ID, baustelleId)
                        // Knoten (Unterbaustelle) optional
                        // Auswahl gilt nur für dieses Foto (User-Wunsch).
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        val rootId = aktuelleHauptKnotenId
                        val kid = selectedKnotenId
                        if (kid != null && (rootId == null || kid != rootId)) {
                            bundle.putLong(FotoErfassenFragment.ARG_KNOTEN_ID, kid)
                        }
                        bundle.putDouble(FotoErfassenFragment.ARG_LAT, standort.latitude)
                        bundle.putDouble(FotoErfassenFragment.ARG_LON, standort.longitude)
                        standort.altitude.let { bundle.putDouble(FotoErfassenFragment.ARG_ALT, it) }
                        // KM Default: zuerst Trassendaten aus TRA/GRA/GVP, dann Unterbaustelle-kmVon, dann alte Live-km.
                        // Patch 31: Wenn eine Achse gefunden wurde und ein km-Wert vorhanden ist, wird der km
                        // immer an die Foto-Maske übergeben - auch bei ROT/orange. In der Foto-Maske bleibt der
                        // Wert änderbar und bei großem Achsabstand erscheint eine Warnung. Vorher blieb das Feld
                        // leer, sobald die Qualität ROT war, obwohl im Kamera-Overlay bereits ein km stand.
                        val trassenTreffer = letzterTrassenTreffer
                        if (trassenTreffer?.kmMeter != null) {
                            bundle.putDouble(FotoErfassenFragment.ARG_KM, trassenTreffer.kmMeter / 1000.0)
                            bundle.putInt(FotoErfassenFragment.ARG_KM_SUFFIX, 0)
                            bundle.putDouble(FotoErfassenFragment.ARG_ACHsA, trassenTreffer.abstandM)
                            bundle.putString(FotoErfassenFragment.ARG_OBJEKT_KENNUNG, trassenTreffer.kameraObjektText())
                            naechsterGvpText(trassenTreffer)?.let { bundle.putString(FotoErfassenFragment.ARG_GVP_KURZ, it) }
                            gvpZwischenText(trassenTreffer)?.let { bundle.putString(FotoErfassenFragment.ARG_GVP_MAST, it) }
                            apKorZwischenText(trassenTreffer)?.let { bundle.putString(FotoErfassenFragment.ARG_AP_KOR, it) }
                            bundle.putString(FotoErfassenFragment.ARG_INDIKATOR, when (trassenTreffer.qualitaet) {
                                TrassenNaheBerechner.Qualitaet.GRUEN -> "GRUEN"
                                TrassenNaheBerechner.Qualitaet.GELB -> "GELB"
                                TrassenNaheBerechner.Qualitaet.ORANGE -> "GELB"
                                TrassenNaheBerechner.Qualitaet.ROT -> "ROT"
                            })
                        } else {
                            val kmVonNode = try {
                                val useId = kid?.takeIf { rootId == null || it != rootId }
                                useId?.let { ServiceLocator.baustellenBaumRepository.knotenMitId(it)?.kmVon }
                            } catch (_: Exception) { null }
                            if (kmVonNode != null) {
                                bundle.putDouble(FotoErfassenFragment.ARG_KM, kmVonNode)
                                bundle.putInt(FotoErfassenFragment.ARG_KM_SUFFIX, 0)
                            } else {
                                if (kmRes?.indikator != KmBerechner.Indikator.ROT) {
                                    kmRes?.km?.let { bundle.putDouble(FotoErfassenFragment.ARG_KM, rundeKmAuf10m(it)) }
                                    val suffix = if (aktuellerKontext?.art == de.tbhelfer.ui.streckenpunkte.TrassenKontextArt.STRECKE) {
                                        kmRes?.kmSuffix ?: 0
                                    } else {
                                        0
                                    }
                                    bundle.putInt(FotoErfassenFragment.ARG_KM_SUFFIX, suffix)
                                }
                            }
                            kmRes?.achsabstandM?.let { bundle.putDouble(FotoErfassenFragment.ARG_ACHsA, it) }
                            bundle.putString(FotoErfassenFragment.ARG_INDIKATOR, kmRes?.indikator?.name ?: "ROT")
                        }

                        if (nachweisMode) {
                            bundle.putBoolean(FotoErfassenFragment.ARG_NACHWEIS_MODE, true)
                            bundle.putString(FotoErfassenFragment.ARG_NACHWEIS_MANGEL_KEY, nachweisMangelKey)
                            bundle.putString(FotoErfassenFragment.ARG_NACHWEIS_STATUS, nachweisStatus)
                            bundle.putString(FotoErfassenFragment.ARG_MANGEL_FOTO_ART, nachweisFotoArt)
                            bundle.putString(FotoErfassenFragment.ARG_PREFILL_FIRMA, nachweisFirma)
                            bundle.putString(FotoErfassenFragment.ARG_PREFILL_KURZTEXT, nachweisKurztext)
                            bundle.putString(FotoErfassenFragment.ARG_PREFILL_NOTIZ, nachweisNotiz)
                            nachweisKm?.let {
                                bundle.putDouble(FotoErfassenFragment.ARG_KM, it)
                                bundle.putInt(FotoErfassenFragment.ARG_KM_SUFFIX, 0)
                            }
                        }
                        if (abnahmeMode) {
                            bundle.putBoolean(FotoErfassenFragment.ARG_ABNAHME_MODE, true)
                            bundle.putString(FotoErfassenFragment.ARG_ABNAHME_ID, abnahmeId)
                            bundle.putString(FotoErfassenFragment.ARG_ABNAHME_BILD_ART, abnahmeBildArt)
                            bundle.putString(FotoErfassenFragment.ARG_PREFILL_FIRMA, nachweisFirma)
                            bundle.putString(FotoErfassenFragment.ARG_PREFILL_KURZTEXT, nachweisKurztext)
                            bundle.putString(FotoErfassenFragment.ARG_PREFILL_NOTIZ, nachweisNotiz)
                            nachweisKm?.let {
                                bundle.putDouble(FotoErfassenFragment.ARG_KM, it)
                                bundle.putInt(FotoErfassenFragment.ARG_KM_SUFFIX, 0)
                            }
                        }

                        // Kein Reset: Auswahl ist global und bleibt aktiv.
                        findNavController().navigate(R.id.fotoErfassenFragment, bundle)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    beendeNachtAufnahmeDialog()
                    Toast.makeText(requireContext(), "Fehler beim Speichern: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private suspend fun refreshAktiverKontext() {
        val baustelleId = aktuelleBaustelleId ?: return
        val baustelle = ServiceLocator.baustellenRepository.baustelleMitIdOnce(baustelleId)
        val startId = selectedKnotenId ?: aktuelleHauptKnotenId
        val pfad = startId?.let { runCatching { ServiceLocator.baustellenBaumRepository.pfadVonRootBis(it) }.getOrDefault(emptyList()) } ?: emptyList()
        aktuellerKontext = AktiverTrassenKontextResolver.ausPfad(pfad, baustelle)
    }

    private fun buildBereitText(): String {
        val basis = aktuelleBaustellenName ?: "Baustelle-ID: $aktuelleBaustelleId"
        val kontext = aktuellerKontext?.labelKurz
        val prefix = when {
            abnahmeMode -> "Abnahmefoto bereit"
            nachweisMode -> "Nachweisfoto bereit"
            else -> "Kamera bereit"
        }
        return if (kontext.isNullOrBlank()) {
            "$prefix ($basis)"
        } else {
            "$prefix ($basis · $kontext)"
        }
    }

    private fun setzeStatus(text: String) {
        textStatus.text = text
    }

    companion object {
        const val ARG_WIDGET_CAMERA_LAUNCH = "widgetCameraLaunch"
        const val ARG_NACHWEIS_MODE = "nachweisMode"
        const val ARG_NACHWEIS_MANGEL_KEY = "nachweisMangelKey"
        const val ARG_NACHWEIS_STATUS = "nachweisStatus"
        const val ARG_MANGEL_FOTO_ART = "mangelFotoArt"
        const val ARG_NACHWEIS_KNOTEN_ID = "nachweisKnotenId"
        const val ARG_PREFILL_FIRMA = "prefillFirma"
        const val ARG_PREFILL_KURZTEXT = "prefillKurztext"
        const val ARG_PREFILL_NOTIZ = "prefillNotiz"
        const val ARG_PREFILL_KM = "prefillKm"
        const val ARG_ABNAHME_MODE = "abnahmeMode"
        const val ARG_ABNAHME_ID = "abnahmeId"
        const val ARG_ABNAHME_BILD_ART = "abnahmeBildArt"
        const val ARG_ABNAHME_BAUSTELLE_ID = "abnahmeBaustelleId"
        const val ARG_ABNAHME_HAUPT_KNOTEN_ID = "abnahmeHauptKnotenId"
        const val ARG_ABNAHME_BAUSTELLE_NAME = "abnahmeBaustelleName"
    }
}
