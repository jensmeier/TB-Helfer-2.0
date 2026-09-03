package de.tbhelfer.ui.einstellungen.diagnose

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import de.tbhelfer.R
import de.tbhelfer.vermessung.StandortLiveQuelle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Geräte-Diagnose für die für TB-Helfer wichtigen Hardware-Funktionen.
 *
 * Die Seite liest die echten Android-Hardwaremerkmale aus. Sie verändert keine
 * Vermessungsdaten und keine Baustellen-/BTB-Daten.
 */
class GeraeteDiagnoseFragment : Fragment(R.layout.fragment_geraete_diagnose) {

    private lateinit var textGeraet: TextView
    private lateinit var textKameras: TextView
    private lateinit var textGnss: TextView
    private lateinit var btnBlitzTest: Button
    private lateinit var btnGnssLive: Button

    private var blitzKameraId: String? = null
    private var gnssJob: Job? = null
    private var locationJob: Job? = null
    private var letzteGnss: StandortLiveQuelle.GnssZusammenfassung? = null
    private var letzteLocation: Location? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { erlaubt ->
        if (erlaubt) starteBlitzTest() else toast("Kameraberechtigung fehlt – Blitztest nicht möglich.")
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val erlaubt = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (erlaubt) starteGnssLive() else toast("Standortberechtigung fehlt – GNSS-Liveanzeige nicht möglich.")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textGeraet = view.findViewById(R.id.textDiagnoseGeraet)
        textKameras = view.findViewById(R.id.textDiagnoseKameras)
        textGnss = view.findViewById(R.id.textDiagnoseGnss)
        btnBlitzTest = view.findViewById(R.id.btnDiagnoseBlitzTest)
        btnGnssLive = view.findViewById(R.id.btnDiagnoseGnssLive)

        view.findViewById<View>(R.id.btnDiagnoseNeuLaden).setOnClickListener { ladeDiagnose() }
        btnBlitzTest.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                starteBlitzTest()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        btnGnssLive.setOnClickListener {
            if (gnssJob != null || locationJob != null) {
                stoppeGnssLive()
            } else if (StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
                starteGnssLive()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }
        view.findViewById<View>(R.id.btnDiagnoseKameraTest).setOnClickListener {
            findNavController().navigate(R.id.kameraFragment)
        }

        ladeDiagnose()
    }

    private fun ladeDiagnose() {
        textGeraet.text = baueGeraeteText()
        textKameras.text = baueKameraText()
        aktualisiereGnssText()
    }

    private fun baueGeraeteText(): String {
        return buildString {
            append("Hersteller: ").append(Build.MANUFACTURER).append('\n')
            append("Modell: ").append(Build.MODEL).append('\n')
            append("Gerät: ").append(Build.DEVICE).append('\n')
            append("Android: ").append(Build.VERSION.RELEASE).append(" · API ").append(Build.VERSION.SDK_INT)
        }
    }

    private fun baueKameraText(): String {
        val manager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())
        if (cameraIds.isEmpty()) {
            blitzKameraId = null
            btnBlitzTest.isEnabled = false
            return "Android meldet keine Kamera."
        }

        blitzKameraId = null
        val bloecke = cameraIds.map { id ->
            runCatching {
                val c = manager.getCameraCharacteristics(id)
                val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "hinten"
                    CameraCharacteristics.LENS_FACING_FRONT -> "vorne"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "extern"
                    else -> "unbekannt"
                }
                val hatBlitz = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (blitzKameraId == null && hatBlitz && c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    blitzKameraId = id
                }

                val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.joinToString(" / ") { String.format(Locale.GERMANY, "%.1f mm", it) }
                    .orEmpty()

                val zoomText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { range ->
                        String.format(Locale.GERMANY, "%.1fx–%.1fx", range.lower, range.upper)
                    }
                } else null
                    ?: c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let {
                        String.format(Locale.GERMANY, "1,0x–%.1fx", it)
                    }
                    ?: "unbekannt"

                val sceneModes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES) ?: intArrayOf()
                val camera2Nacht = sceneModes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_NIGHT)
                val hwLevel = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "Extern"
                    else -> "unbekannt"
                }

                val jpegMax = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)
                    ?.maxByOrNull { size -> size.width.toLong() * size.height.toLong() }
                val mpText = jpegMax?.let {
                    String.format(Locale.GERMANY, "%.1f MP (%d×%d)", it.width.toDouble() * it.height.toDouble() / 1_000_000.0, it.width, it.height)
                } ?: "unbekannt"

                val physisch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    c.physicalCameraIds.takeIf { it.isNotEmpty() }?.joinToString(", ")
                } else null

                buildString {
                    append("Kamera ").append(id).append(" · ").append(facing).append('\n')
                    append("  Blitz: ").append(if (hatBlitz) "JA" else "NEIN").append('\n')
                    append("  Zoom: ").append(zoomText).append('\n')
                    append("  Brennweite: ").append(focal.ifBlank { "unbekannt" }).append('\n')
                    append("  Foto max.: ").append(mpText).append('\n')
                    append("  Camera2-Nachtszene: ").append(if (camera2Nacht) "JA" else "NEIN").append('\n')
                    append("  Hardware-Level: ").append(hwLevel)
                    if (!physisch.isNullOrBlank()) append("\n  Physische Kameras: ").append(physisch)
                }
            }.getOrElse { e -> "Kamera $id · Fehler beim Auslesen: ${e.message ?: e.javaClass.simpleName}" }
        }

        val extensionsBibliothek = runCatching { Class.forName("androidx.camera.extensions.ExtensionsManager") }.isSuccess
        btnBlitzTest.isEnabled = blitzKameraId != null

        return buildString {
            append(bloecke.joinToString("\n\n"))
            append("\n\nCameraX Extensions-Bibliothek: ")
            append(if (extensionsBibliothek) "vorhanden" else "nicht eingebunden")
            append("\nHinweis: Ein Hersteller-Nachtmodus ist nicht automatisch für Drittanbieter-Apps verfügbar.")
        }
    }

    private fun starteBlitzTest() {
        val id = blitzKameraId
        if (id == null) {
            toast("Keine hintere Kamera mit Blitz gefunden.")
            return
        }
        val manager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        runCatching {
            manager.setTorchMode(id, true)
            btnBlitzTest.isEnabled = false
            btnBlitzTest.text = "Blitz leuchtet…"
            btnBlitzTest.postDelayed({
                runCatching { manager.setTorchMode(id, false) }
                if (isAdded) {
                    btnBlitzTest.isEnabled = true
                    btnBlitzTest.text = "Blitz 2 s testen"
                }
            }, 2000L)
        }.onFailure { e ->
            toast("Blitztest fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun starteGnssLive() {
        stoppeGnssLive()
        btnGnssLive.text = "GNSS-Live stoppen"

        gnssJob = viewLifecycleOwner.lifecycleScope.launch {
            StandortLiveQuelle.gnssStatusFlow(requireContext()).collectLatest { status ->
                letzteGnss = status
                aktualisiereGnssText()
            }
        }
        locationJob = viewLifecycleOwner.lifecycleScope.launch {
            StandortLiveQuelle.standortFlow(
                context = requireContext(),
                minTimeMs = 1000L,
                minDistanceM = 0f,
                emitLastKnown = true,
                glaettungFenster = 1
            ).collectLatest { location ->
                letzteLocation = location
                aktualisiereGnssText()
            }
        }
    }

    private fun stoppeGnssLive() {
        gnssJob?.cancel()
        locationJob?.cancel()
        gnssJob = null
        locationJob = null
        if (::btnGnssLive.isInitialized) btnGnssLive.text = "GNSS live starten"
    }

    @SuppressLint("MissingPermission")
    private fun aktualisiereGnssText() {
        if (!::textGnss.isInitialized) return
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hwModell = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { lm.gnssHardwareModelName }.getOrNull()
        } else null
        val hwJahr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { lm.gnssYearOfHardware }.getOrNull()?.takeIf { it > 0 }
        } else null
        val provider = runCatching { lm.allProviders.joinToString(", ") }.getOrDefault("-")
        val status = letzteGnss
        val loc = letzteLocation

        textGnss.text = buildString {
            append("GNSS-Hardware: ").append(hwModell ?: "vom Gerät nicht gemeldet")
            hwJahr?.let { append(" · Jahr ").append(it) }
            append('\n').append("Provider: ").append(provider)
            append("\n\nLive gesehene Satelliten:")
            if (status == null) {
                append("\nNoch keine Live-Daten. 'GNSS live starten' drücken.")
            } else {
                append("\nGPS: ").append(status.gps)
                append(" · Galileo: ").append(status.galileo)
                append(" · GLONASS: ").append(status.glonass)
                append("\nBeiDou: ").append(status.beidou)
                append(" · QZSS: ").append(status.qzss)
                append(" · NavIC/IRNSS: ").append(status.navic)
                append("\nSBAS: ").append(status.sbas)
                append(" · unbekannt: ").append(status.unbekannt)
                append("\nGesamt: ").append(status.total)
                append(" · im Fix verwendet: ").append(status.usedInFix)
            }
            if (loc != null) {
                append("\n\nLetzter Standort:")
                append("\n").append(String.format(Locale.GERMANY, "%.6f, %.6f", loc.latitude, loc.longitude))
                if (loc.hasAccuracy()) append(" · ±").append(String.format(Locale.GERMANY, "%.1f m", loc.accuracy))
            }
        }
    }

    override fun onDestroyView() {
        stoppeGnssLive()
        blitzKameraId?.let { id ->
            val manager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            runCatching { manager.setTorchMode(id, false) }
        }
        super.onDestroyView()
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
    }
}
