package de.tbhelfer.ui.importexport

import android.Manifest
import android.content.Context
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.TextView
import android.view.ViewGroup
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.CheckBox
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import de.tbhelfer.datenbank.AbsteckpunktUebersichtRow
import de.tbhelfer.datenbank.GvpUebersichtRow
import de.tbhelfer.datenbank.GraUebersichtRow
import de.tbhelfer.datenbank.TrassenAchseEntity
import de.tbhelfer.datenbank.TrassenAchseKorrekturEntity
import de.tbhelfer.datenbank.TrassenManuellerAbschnittEntity
import de.tbhelfer.datenbank.TrassenAbschnittKmPunktEntity
import de.tbhelfer.datenbank.TrassenImportStatusEntity
import de.tbhelfer.datenbank.TrassenUebersicht
import de.tbhelfer.streckenobjekte.StreckenObjektSpeicher
import de.tbhelfer.util.BahnhofSucheUtil
import de.tbhelfer.vermessung.AbsteckCsvParser
import de.tbhelfer.vermessung.GvpDateiParser
import de.tbhelfer.vermessung.GraDateiParser
import de.tbhelfer.vermessung.KorDateiParser
import de.tbhelfer.vermessung.TraDateiParser
import de.tbhelfer.vermessung.StandortLiveQuelle
import de.tbhelfer.vermessung.TrassenNaheBerechner
import de.tbhelfer.vermessung.TrassenKmStatusLogik
import de.tbhelfer.vermessung.ManuelleAbschnittKmLogik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

class ImportExportFragment : Fragment(R.layout.fragment_import_export) {

    private var txtStatus: TextView? = null
    private var layoutBerechnungsFortschritt: View? = null
    private var txtBerechnungsFortschritt: TextView? = null
    private var progressBerechnung: View? = null
    private var starteNaheTestNachStandortBerechtigung: Boolean = false
    private var statusDetailsAnzeigen: Boolean = false
    private var letzteWeichenKandidaten: List<TrassenNaheBerechner.Ergebnis> = emptyList()
    private var letzteWeichenAchsenById: Map<Long, TrassenAchseEntity> = emptyMap()
    private var letzterZipScan: StreckendatenZipScan? = null
    private var starteAbschnittEditorNachStandortBerechtigung: Boolean = false
    private var pendingStandortAktion: (() -> Unit)? = null
    private var abschnittGpsWarmupJob: Job? = null
    private var abschnittGnssJob: Job? = null
    private var abschnittLiveDistanzJob: Job? = null
    private val abschnittGpsFixes = ArrayDeque<Location>()
    private var abschnittGnssStatus: StandortLiveQuelle.GnssZusammenfassung = StandortLiveQuelle.GnssZusammenfassung()

    // Patch45ZW: Anzeigeauswahl ist nur Orientierung. Der aktive betriebliche
    // Abschnitt behält seine konkrete Roh-TRA unabhängig davon dauerhaft bei.
    private var letzteGrafikSichtbareRohAchseIds: Set<Long>? = null
    private var letzteGrafikSichtbarePunktArten: Set<String>? = null

    private companion object {
        const val LIVE_GPS_SAMMEL_DAUER_MS = 20_000L
        const val LIVE_GPS_MIN_FIXES = 6
        const val LIVE_GPS_BESTE_FIXES = 8
        const val ARG_AUTO_OPEN_ABSCHNITT_EDITOR = "autoOpenStreckenGleiseBearbeiten"
        const val ARG_AUTO_OPEN_STRECKEN_OBJEKT_EDITOR = "autoOpenStreckenObjektEditor"
        const val ARG_PREFILL_BETRIEBSSTELLE = "prefillBetriebsstelle"
        const val ARG_PREFILL_OBJEKT = "prefillObjekt"
        const val ARG_PREFILL_OBJEKT_TYP = "prefillObjektTyp"
    }

    private val traPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        importiereTraGraDateien(uris)
    }

    private val graPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        importiereGraDateien(uris)
    }

    private val gvpPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        importiereGvpDateien(uris)
    }

    private val korPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        importiereKorDateien(uris)
    }

    private val streckendatenZipPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        streckendatenZipScannen(uri)
    }

    private val standortPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val sollNaheStarten = starteNaheTestNachStandortBerechtigung
        val sollEditorStarten = starteAbschnittEditorNachStandortBerechtigung
        val aktion = pendingStandortAktion
        starteNaheTestNachStandortBerechtigung = false
        starteAbschnittEditorNachStandortBerechtigung = false
        pendingStandortAktion = null
        when {
            ok && aktion != null -> aktion.invoke()
            ok && sollEditorStarten -> zeigeStreckenGleiseBearbeiten()
            ok && sollNaheStarten -> naheberechnungLiveTesten()
            !ok -> Toast.makeText(requireContext(), R.string.import_export_v19_nahe_permission_missing, Toast.LENGTH_LONG).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        txtStatus = view.findViewById(R.id.txtVermessungsdatenStatus)
        layoutBerechnungsFortschritt = view.findViewById(R.id.layoutBerechnungsFortschritt)
        txtBerechnungsFortschritt = view.findViewById(R.id.txtBerechnungsFortschritt)
        progressBerechnung = view.findViewById(R.id.progressBerechnung)

        view.findViewById<View?>(R.id.btnStreckendatenZip)?.setOnClickListener {
            streckendatenZipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
        }
        view.findViewById<View?>(R.id.btnGrafischePruefung)?.setOnClickListener {
            zeigeGrafischePruefung()
        }
        view.findViewById<View?>(R.id.btnStreckenGleiseBearbeiten)?.setOnClickListener {
            zeigeStreckenGleiseBearbeiten()
        }
        view.findViewById<View?>(R.id.btnVermessungsdatenLoeschen)?.setOnClickListener {
            vermessungsdatenLoeschenBestaetigen()
        }

        statusAktualisieren(mitToast = false)

        if (arguments?.getBoolean(ARG_AUTO_OPEN_ABSCHNITT_EDITOR, false) == true) {
            arguments?.remove(ARG_AUTO_OPEN_ABSCHNITT_EDITOR)
            view.post { zeigeStreckenGleiseBearbeiten() }
        }
        if (arguments?.getBoolean(ARG_AUTO_OPEN_STRECKEN_OBJEKT_EDITOR, false) == true) {
            val betriebsstelle = arguments?.getString(ARG_PREFILL_BETRIEBSSTELLE).orEmpty()
            val objekt = arguments?.getString(ARG_PREFILL_OBJEKT).orEmpty()
            val typRaw = arguments?.getString(ARG_PREFILL_OBJEKT_TYP).orEmpty()
            arguments?.remove(ARG_AUTO_OPEN_STRECKEN_OBJEKT_EDITOR)
            val typ = if (typRaw == "DKW_KREUZUNG") StreckenObjektSpeicher.Typ.DKW_KREUZUNG else StreckenObjektSpeicher.Typ.WEICHE
            view.post { zeigeStreckenObjektEditor(typ, betriebsstelle, objekt) }
        }
    }

    override fun onDestroyView() {
        abschnittGpsWarmupJob?.cancel()
        abschnittGnssJob?.cancel()
        abschnittLiveDistanzJob?.cancel()
        abschnittGpsWarmupJob = null
        abschnittGnssJob = null
        abschnittLiveDistanzJob = null
        abschnittGpsFixes.clear()
        layoutBerechnungsFortschritt = null
        txtBerechnungsFortschritt = null
        progressBerechnung = null
        super.onDestroyView()
    }

    private fun streckendatenZipScannen(uri: Uri) {
        val zipName = dateinameAusUri(uri)
        txtStatus?.text = "Streckendaten-ZIP wird gelesen…\n$zipName"
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { scanneStreckendatenZip(uri, zipName) }
            }.onSuccess { scan ->
                letzterZipScan = scan
                txtStatus?.text = scan.toKurzStatusText()
                zeigeStreckendatenZipVorschau(scan)
            }.onFailure { throwable ->
                val meldung = "ZIP konnte nicht gelesen werden: ${throwable.message ?: throwable.javaClass.simpleName}"
                txtStatus?.text = meldung
                Toast.makeText(requireContext(), meldung, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun scanneStreckendatenZip(uri: Uri, zipName: String): StreckendatenZipScan {
        val dateien = mutableListOf<StreckendatenZipDatei>()
        val pdfs = mutableListOf<String>()
        val rootBytes = requireNotNull(requireContext().contentResolver.openInputStream(uri)) {
            "ZIP konnte nicht geöffnet werden."
        }.use { input -> input.readBytes() }

        sammleStreckendatenAusZipBytes(
            zipBytes = rootBytes,
            prefix = "",
            dateien = dateien,
            pdfs = pdfs,
            tiefe = 0
        )

        require(dateien.isNotEmpty()) {
            "Im ZIP wurden keine TRA/GRA/TRG/KOR/CSV/TXT/KF-Dateien gefunden."
        }
        return StreckendatenZipScan(
            zipName = zipName,
            dateien = dateien,
            pdfPfade = pdfs
        )
    }

    private fun sammleStreckendatenAusZipBytes(
        zipBytes: ByteArray,
        prefix: String,
        dateien: MutableList<StreckendatenZipDatei>,
        pdfs: MutableList<String>,
        tiefe: Int
    ) {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val entryName = entry.name.orEmpty()
                    val pfad = if (prefix.isBlank()) entryName else "$prefix/$entryName"
                    val bytes = zip.readBytes()
                    val lower = pfad.lowercase(Locale.GERMANY)
                    if (lower.endsWith(".zip") && tiefe < 3) {
                        val nestedPrefix = pfad.substringBeforeLast(".zip")
                        sammleStreckendatenAusZipBytes(
                            zipBytes = bytes,
                            prefix = nestedPrefix,
                            dateien = dateien,
                            pdfs = pdfs,
                            tiefe = tiefe + 1
                        )
                    } else {
                        val erkannt = klassifiziereStreckendatenZipDatei(pfad, bytes)
                        if (erkannt != null) {
                            if (erkannt.typ == ZipDateiTyp.PDF) pdfs += pfad
                            dateien += erkannt
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun klassifiziereStreckendatenZipDatei(pfad: String, bytes: ByteArray): StreckendatenZipDatei? {
        val lower = pfad.lowercase(Locale.GERMANY)
        val dateiname = pfad.substringAfterLast('/').substringAfterLast('\\')
        val typ = when {
            lower.endsWith(".tra") -> ZipDateiTyp.TRA
            lower.endsWith(".gra") -> ZipDateiTyp.GRA
            lower.endsWith(".trg") -> ZipDateiTyp.TRG
            lower.endsWith(".kor.txt") || lower.endsWith(".kor") -> ZipDateiTyp.KOR
            lower.endsWith(".kf.txt") -> ZipDateiTyp.KF_TXT
            lower.endsWith(".kf") -> ZipDateiTyp.KF_RAW
            lower.endsWith(".kv") -> ZipDateiTyp.KV
            lower.endsWith(".csv") -> ZipDateiTyp.CSV
            lower.endsWith(".txt") && lower.contains("gvp") -> ZipDateiTyp.GVP_TXT
            lower.endsWith(".txt") -> ZipDateiTyp.TXT
            lower.endsWith(".pdf") -> ZipDateiTyp.PDF
            else -> return null
        }
        val streckeMitRichtung = findeStreckeUndQuellrichtung(dateiname)
            ?: findeStreckeUndQuellrichtung(pfad)
        val strecken = streckeMitRichtung?.first?.let { listOf(it) }
            ?: findeStreckenAusText(dateiname).ifEmpty { findeStreckenAusText(pfad) }
        val objektTyp = when {
            typ != ZipDateiTyp.TRA && typ != ZipDateiTyp.GRA && typ != ZipDateiTyp.TRG -> ZipObjektTyp.PUNKTE_ODER_DOKUMENT
            Regex("(?i)(^|[/_\\s-])WVB[_\\s-]*\\d+[-_]\\d+").containsMatchIn(pfad) -> ZipObjektTyp.WEICHENVERBINDUNG
            Regex("(?i)(^|[/_\\s-])WV[_\\s-]*\\d+[-_]\\d+").containsMatchIn(pfad) -> ZipObjektTyp.WEICHENVERBINDUNG
            Regex("(?i)(^|[/_\\s-])SW[_\\s-]*\\d+W?").containsMatchIn(pfad) -> ZipObjektTyp.WEICHE
            Regex("(?i)(^|[/_\\s-])W\\d+[A-Z]?").containsMatchIn(pfad) && lower.contains("weiche") -> ZipObjektTyp.WEICHE
            lower.contains("streckengleise") -> ZipObjektTyp.STRECKENACHSE
            Regex("(?i)(^|[/_\\s-])(GL|GLEIS)[_\\s-]*\\d+[A-Z]?").containsMatchIn(pfad) -> ZipObjektTyp.BAHNHOFS_GLEIS
            Regex("(?i)(^|[/_\\s-])G[_\\s-]*\\d+[A-Z]?(?:[_\\s.-]|$)").containsMatchIn(pfad) -> ZipObjektTyp.BAHNHOFS_GLEIS
            streckeMitRichtung != null -> ZipObjektTyp.STRECKENACHSE
            else -> ZipObjektTyp.UNBEKANNT
        }
        val rohName = dateiname.substringBeforeLast('.')
        val gleis = Regex("(?i)(?:^|[_\\s-])(?:GL|GLEIS)[_\\s-]*(\\d+[A-Z]?)(?:[_\\s.-]|$)")
            .find(rohName)?.groupValues?.getOrNull(1)
            ?: Regex("(?i)(?:^|[_\\s-])G[_\\s-]*(\\d+[A-Z]?)(?:[_\\s.-]|$)")
                .find(rohName)?.groupValues?.getOrNull(1)
        val variante = streckeMitRichtung?.second
        val wvWeichen = Regex("(?i)(?:WVB|WV)[_\\s-]*(\\d+)[-_](\\d+)")
            .find(rohName)?.let { listOf(it.groupValues[1], it.groupValues[2]) }
            ?: emptyList()
        return StreckendatenZipDatei(
            pfad = pfad,
            dateiname = dateiname,
            typ = typ,
            objektTyp = objektTyp,
            strecken = strecken,
            rohName = rohName,
            gleis = gleis,
            variante = variante,
            wvWeichen = wvWeichen,
            bytes = bytes
        )
    }

    private fun findeStreckeUndQuellrichtung(text: String): Pair<String, String>? {
        val upper = text.uppercase(Locale.GERMANY)

        Regex("(?<!\\d)([1-9]\\d{3})[_-]?(SL|[LRS])(?:[_\\s.-]|$)")
            .find(upper)?.let { match ->
                return match.groupValues[1] to match.groupValues[2]
            }

        Regex("(?<!\\d)([1-9]\\d{3})[_-]\\d{3}[-_]\\d{3}(SL|[LRS])(?:[_\\s.-]|$)")
            .find(upper)?.let { match ->
                return match.groupValues[1] to match.groupValues[2]
            }

        val richtungMatch = Regex("(?<![A-Z0-9])(SL|[LRS])(?![A-Z0-9])")
            .findAll(upper)
            .lastOrNull()
            ?: return null
        val streckeMatch = Regex("(?<!\\d)([1-9]\\d{3})(?!\\d)")
            .findAll(upper)
            .filter { it.range.first < richtungMatch.range.first }
            .lastOrNull()
            ?: return null
        return streckeMatch.groupValues[1] to richtungMatch.groupValues[1]
    }

    private fun findeStreckenAusText(text: String): List<String> {
        return Regex("(?<![A-Z0-9])([1-9]\\d{3})(?!\\d)")
            .findAll(text.uppercase(Locale.GERMANY))
            .map { it.groupValues[1] }
            .filter { it != "3000" }
            .distinct()
            .sorted()
            .toList()
    }

    private fun zeigeStreckendatenZipVorschau(scan: StreckendatenZipScan) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 20, 42, 0)
        }
        container.addView(TextView(context).apply {
            text = scan.toVorschauText()
            textSize = 14f
            setPadding(0, 0, 0, 16)
        })

        val gruppen = listOf(
            ZipObjektTyp.BAHNHOFS_GLEIS to "Gleise",
            ZipObjektTyp.WEICHE to "Weichen",
            ZipObjektTyp.WEICHENVERBINDUNG to "Weichenverbindungen"
        )
        val gruppenUi = mutableListOf<ZuordnungsGruppeUi>()
        val betriebsstellenVorschlaege = ladeBetriebsstellenVorschlaegeAusAssets()

        if (scan.zuordnungsObjekte().isNotEmpty()) {
            container.addView(TextView(context).apply {
                text = "Zuordnung vor dem Speichern (keine Kennziffer fuer Bahnhofsgleise/Weichen/WV):"
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 10, 0, 6)
            })
            container.addView(TextView(context).apply {
                text = "Nur angehakte Objekte werden importiert. Jede Gruppe kann eine andere Betriebsstelle bekommen."
                textSize = 13f
                setPadding(0, 0, 0, 8)
            })
        }

        gruppen.forEach { (objektTyp, titel) ->
            val objekte = scan.zuordnungsObjekte().filter { it.objektTyp == objektTyp }
            if (objekte.isEmpty()) return@forEach

            val gruppeLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10, 0, 10)
            }
            container.addView(gruppeLayout)

            val alleCheckbox = CheckBox(context).apply {
                text = "$titel markieren"
                isChecked = true
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            gruppeLayout.addView(alleCheckbox)

            val betriebsstelleInput = AutoCompleteTextView(context).apply {
                hint = "Betriebsstelle fuer $titel suchen, z. B. Pasing oder MP"
                setSingleLine(true)
                threshold = 1
            }
            bindeBetriebsstellenVorschlaege(betriebsstelleInput, betriebsstellenVorschlaege)
            gruppeLayout.addView(betriebsstelleInput)
            gruppeLayout.addView(TextView(context).apply {
                text = "Treffer antippen – Kürzel und Name werden automatisch übernommen."
                textSize = 12f
                setPadding(0, 0, 0, dp(4))
            })

            val checkboxen = objekte.take(80).map { objekt ->
                CheckBox(context).apply {
                    text = objekt.anzeigeText()
                    isChecked = true
                    tag = objekt.rawKey
                    setPadding(22, 0, 0, 0)
                }.also { gruppeLayout.addView(it) }
            }
            if (objekte.size > 80) {
                gruppeLayout.addView(TextView(context).apply {
                    text = "… ${objekte.size - 80} weitere $titel in dieser Vorschau nicht einzeln angezeigt."
                    textSize = 13f
                })
            }
            alleCheckbox.setOnCheckedChangeListener { _, checked ->
                checkboxen.forEach { it.isChecked = checked }
            }
            gruppenUi += ZuordnungsGruppeUi(titel, betriebsstelleInput, betriebsstellenVorschlaege, checkboxen)
        }

        val scrollView = android.widget.ScrollView(context).apply { addView(container) }
        MaterialAlertDialogBuilder(context)
            .setTitle("Streckendaten-ZIP Vorschau")
            .setView(scrollView)
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("Nur Vorschau schließen", null)
            .setPositiveButton("Auswahl speichern", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                        val angehakteObjekte = gruppenUi.flatMap { gruppe ->
                            gruppe.checkboxen
                                .filter { it.isChecked }
                                .mapNotNull { it.tag as? String }
                        }
                        val ausgewaehlteGruppen = gruppenUi.filter { gruppe -> gruppe.checkboxen.any { it.isChecked } }
                        val ungueltigeGruppe = ausgewaehlteGruppen.firstOrNull { gruppe ->
                            gruppe.ausgewaehlteBetriebsstelle() == null
                        }
                        if (ungueltigeGruppe != null) {
                            Toast.makeText(
                                requireContext(),
                                "Bitte für ${ungueltigeGruppe.titel} eine Betriebsstelle aus der Trefferliste auswählen.",
                                Toast.LENGTH_LONG
                            ).show()
                            ungueltigeGruppe.betriebsstelleInput.requestFocus()
                            ungueltigeGruppe.betriebsstelleInput.showDropDown()
                            return@setOnClickListener
                        }

                        val zuordnungen = ausgewaehlteGruppen.flatMap { gruppe ->
                            val auswahl = gruppe.ausgewaehlteBetriebsstelle() ?: return@flatMap emptyList()
                            gruppe.checkboxen
                                .filter { it.isChecked }
                                .mapNotNull { checkbox ->
                                    val rawKey = checkbox.tag as? String ?: return@mapNotNull null
                                    rawKey to ZipObjektZuordnung(auswahl.code, auswahl.name)
                                }
                        }.toMap()

                        if (angehakteObjekte.isNotEmpty() && zuordnungen.isEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                "Bitte für angehakte Gleise/Weichen/WV eine Betriebsstelle aus der Trefferliste auswählen.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@setOnClickListener
                        }

                        val warteDialog = MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Bitte warten")
                            .setMessage("Daten werden gespeichert und berechnet …")
                            .setCancelable(false)
                            .create()
                        dialog.dismiss()
                        warteDialog.show()
                        importiereStreckendatenZip(
                            scan = scan,
                            objektZuordnungen = zuordnungen,
                            warteDialog = warteDialog
                        )
                    }
                }
                dialog.show()
            }
    }

    private fun importiereStreckendatenZip(
        scan: StreckendatenZipScan,
        objektZuordnungen: Map<String, ZipObjektZuordnung>,
        warteDialog: androidx.appcompat.app.AlertDialog? = null
    ) {
        txtStatus?.text = "Streckendaten werden importiert…\n${scan.zipName}"
        viewLifecycleOwner.lifecycleScope.launch {
            val ergebnisse = withContext(Dispatchers.IO) {
                scan.dateien
                    .filter { it.sollImportiertWerden(objektZuordnungen.keys) }
                    .map { datei -> importiereZipDatei(datei, objektZuordnungen[datei.rawKey]) }
            }
            val ok = ergebnisse.count { it.ok }
            val fehler = ergebnisse.count { !it.ok }
            val uebersprungen = ergebnisse.count { it.ok && it.meldung.startsWith("nicht importiert", ignoreCase = true) }
            val fachlichImportiert = ok - uebersprungen
            val details = buildString {
                appendLine("Streckendaten-ZIP importiert: $ok ok, $fehler Fehler.")
                if (fachlichImportiert > 0) appendLine("Die Daten werden jetzt automatisch berechnet und geprüft.")
                if (uebersprungen > 0) appendLine("$uebersprungen Datei(en) erkannt, aber noch nicht fachlich importiert.")
                appendLine()
                ergebnisse.take(80).forEach { ergebnis ->
                    val marker = if (ergebnis.ok) "✓" else "!"
                    appendLine("$marker ${ergebnis.dateiname}: ${ergebnis.meldung}")
                }
                if (ergebnisse.size > 80) appendLine("… ${ergebnisse.size - 80} weitere Dateien")
            }.trimEnd()
            txtStatus?.text = details
            if (fachlichImportiert > 0) {
                // Nach einem neuen Import alle Punktarten wieder standardmaessig zeigen.
                // So erscheinen neu importierte GVP/Festpunkte sofort in der grafischen Uebersicht,
                // auch wenn vorher eine Anzeigeauswahl aktiv war.
                letzteGrafikSichtbarePunktArten = null
                Toast.makeText(requireContext(), "ZIP importiert. Daten werden jetzt automatisch berechnet.", Toast.LENGTH_LONG).show()
                datenBerechnenUndPruefen(warteDialog)
            } else {
                warteDialog?.dismiss()
                Toast.makeText(requireContext(), "ZIP konnte nicht fachlich importiert werden.", Toast.LENGTH_LONG).show()
                statusAktualisieren(mitToast = false)
            }
        }
    }

    private suspend fun importiereZipDatei(
        datei: StreckendatenZipDatei,
        zuordnung: ZipObjektZuordnung?
    ): ImportErgebnis {
        speichereStreckendatenZuordnung(datei, zuordnung)
        return when (datei.typ) {
            ZipDateiTyp.TRA -> importiereZipTraDatei(datei)
            ZipDateiTyp.GRA -> importiereZipGraDatei(datei)
            ZipDateiTyp.KOR -> importiereZipKorDatei(datei)
            ZipDateiTyp.GVP_TXT,
            ZipDateiTyp.KF_TXT,
            ZipDateiTyp.KV -> importiereZipGvpDatei(datei)
            ZipDateiTyp.KF_RAW -> ImportErgebnis(datei.dateiname, true, "nicht direkt importiert: KF-Original erkannt; falls vorhanden wird KF.TXT verwendet")
            ZipDateiTyp.TRG -> ImportErgebnis(datei.dateiname, true, "nicht importiert: TRG erkannt, Parser folgt nach GRA-Prüfung")
            ZipDateiTyp.CSV -> importiereZipCsvDatei(datei)
            ZipDateiTyp.TXT -> ImportErgebnis(datei.dateiname, true, "nicht importiert: TXT erkannt, Inhalt muss noch zugeordnet werden")
            ZipDateiTyp.PDF -> ImportErgebnis(datei.dateiname, true, "nicht importiert: PDF nur gemeldet")
        }
    }

    private suspend fun importiereZipTraDatei(datei: StreckendatenZipDatei): ImportErgebnis = runCatching {
        val result = TraDateiParser.parse(datei.dateiname, datei.bytes)
        ServiceLocator.trassenRepository.traImportSpeichern(result)
        ImportErgebnis(datei.dateiname, true, "${result.achse.anzahlElemente} Elemente, ${result.samples.size} Knoten")
    }.getOrElse { throwable ->
        ImportErgebnis(datei.dateiname, false, throwable.message ?: throwable.javaClass.simpleName)
    }

    private suspend fun importiereZipGraDatei(datei: StreckendatenZipDatei): ImportErgebnis = runCatching {
        val result = GraDateiParser.parse(datei.dateiname, datei.bytes)
        ServiceLocator.trassenRepository.graImportSpeichern(datei.dateiname, result)
        ImportErgebnis(datei.dateiname, true, "${result.punkte.size} GRA/kmLinie-Punkte")
    }.getOrElse { throwable ->
        ImportErgebnis(datei.dateiname, false, throwable.message ?: throwable.javaClass.simpleName)
    }

    private suspend fun importiereZipKorDatei(datei: StreckendatenZipDatei): ImportErgebnis = runCatching {
        val result = KorDateiParser.parse(datei.dateiname, datei.bytes)
        ServiceLocator.trassenRepository.korImportSpeichern(datei.dateiname, result)
        ImportErgebnis(datei.dateiname, true, "${result.punkte.size} Absteckpunkte/KOR")
    }.getOrElse { throwable ->
        ImportErgebnis(datei.dateiname, false, throwable.message ?: throwable.javaClass.simpleName)
    }

    private suspend fun importiereZipCsvDatei(datei: StreckendatenZipDatei): ImportErgebnis {
        if (!AbsteckCsvParser.istAbsteckpunktCsv(datei.bytes)) {
            return ImportErgebnis(datei.dateiname, true, "nicht importiert: CSV ist keine erkannte Absteckpunktdatei")
        }
        return runCatching {
            val result = AbsteckCsvParser.parse(datei.dateiname, datei.bytes)
            ServiceLocator.trassenRepository.csvAbsteckpunkteImportSpeichern(datei.dateiname, result)
            ImportErgebnis(datei.dateiname, true, "${result.punkte.size} Absteckpunkte/CSV")
        }.getOrElse { throwable ->
            ImportErgebnis(datei.dateiname, false, throwable.message ?: throwable.javaClass.simpleName)
        }
    }

    private suspend fun importiereZipGvpDatei(datei: StreckendatenZipDatei): ImportErgebnis = runCatching {
        val result = GvpDateiParser.parse(datei.dateiname, datei.bytes)
        ServiceLocator.trassenRepository.gvpImportSpeichern(datei.dateiname, result)
        ImportErgebnis(datei.dateiname, true, "${result.punkte.size} GVP/Festpunkte")
    }.getOrElse { throwable ->
        ImportErgebnis(datei.dateiname, false, throwable.message ?: throwable.javaClass.simpleName)
    }

    private fun speichereStreckendatenZuordnung(
        datei: StreckendatenZipDatei,
        zuordnung: ZipObjektZuordnung?
    ) {
        if (datei.objektTyp != ZipObjektTyp.BAHNHOFS_GLEIS &&
            datei.objektTyp != ZipObjektTyp.WEICHE &&
            datei.objektTyp != ZipObjektTyp.WEICHENVERBINDUNG
        ) return
        if (zuordnung == null) return
        if (zuordnung.kuerzel.isBlank() && zuordnung.name.isBlank()) return
        val wert = listOf(
            zuordnung.kuerzel.replace("|", "/"),
            zuordnung.name.replace("|", "/"),
            datei.strecken.joinToString(","),
            datei.objektTyp.name,
            System.currentTimeMillis().toString()
        ).joinToString("|")
        requireContext()
            .getSharedPreferences("tbhelfer_streckendaten_zuordnung", Context.MODE_PRIVATE)
            .edit()
            .putString(datei.rawKey, wert)
            .apply()
    }

    private fun importiereTraGraDateien(uris: List<Uri>) {
        txtStatus?.text = getString(R.string.import_export_v19_import_running, uris.size)
        viewLifecycleOwner.lifecycleScope.launch {
            val ergebnisse = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val name = dateinameAusUri(uri)
                    when {
                        name.endsWith(".TRA", ignoreCase = true) -> importiereEineTraDatei(uri)
                        name.endsWith(".GRA", ignoreCase = true) -> importiereEineGraDatei(uri)
                        else -> ImportErgebnis(
                            dateiname = name,
                            ok = false,
                            meldung = "Nicht importiert: bitte .TRA oder .GRA waehlen"
                        )
                    }
                }
            }

            val ok = ergebnisse.count { it.ok }
            val fehler = ergebnisse.size - ok
            val traOk = ergebnisse.count { it.ok && it.dateiname.endsWith(".TRA", ignoreCase = true) }
            val graOk = ergebnisse.count { it.ok && it.dateiname.endsWith(".GRA", ignoreCase = true) }
            Toast.makeText(
                requireContext(),
                getString(R.string.import_export_v19_import_done_tra_gra, traOk, graOk, fehler),
                Toast.LENGTH_LONG
            ).show()

            val details = ergebnisse.joinToString(separator = "\n") { ergebnis ->
                val marker = if (ergebnis.ok) "✓" else "!"
                "$marker ${ergebnis.dateiname}: ${ergebnis.meldung}"
            }
            txtStatus?.text = details
            statusAktualisieren(mitToast = false)
        }
    }

    private fun importiereTraDateien(uris: List<Uri>) {
        txtStatus?.text = getString(R.string.import_export_v19_import_running, uris.size)
        viewLifecycleOwner.lifecycleScope.launch {
            val ergebnisse = withContext(Dispatchers.IO) {
                uris.map { uri -> importiereEineTraDatei(uri) }
            }

            val ok = ergebnisse.count { it.ok }
            val fehler = ergebnisse.size - ok
            val nachricht = getString(R.string.import_export_v19_import_done, ok, fehler)
            Toast.makeText(requireContext(), nachricht, Toast.LENGTH_LONG).show()

            val details = ergebnisse.joinToString(separator = "\n") { ergebnis ->
                val marker = if (ergebnis.ok) "✓" else "!"
                "$marker ${ergebnis.dateiname}: ${ergebnis.meldung}"
            }
            txtStatus?.text = details
            statusAktualisieren(mitToast = false)
        }
    }

    private fun importiereGraDateien(uris: List<Uri>) {
        txtStatus?.text = getString(R.string.import_export_v19_import_running, uris.size)
        viewLifecycleOwner.lifecycleScope.launch {
            val ergebnisse = withContext(Dispatchers.IO) {
                uris.map { uri -> importiereEineGraDatei(uri) }
            }

            val ok = ergebnisse.count { it.ok }
            val fehler = ergebnisse.size - ok
            val nachricht = getString(R.string.import_export_v19_import_done_gra, ok, fehler)
            Toast.makeText(requireContext(), nachricht, Toast.LENGTH_LONG).show()

            val details = ergebnisse.joinToString(separator = "\n") { ergebnis ->
                val marker = if (ergebnis.ok) "✓" else "!"
                "$marker ${ergebnis.dateiname}: ${ergebnis.meldung}"
            }
            txtStatus?.text = details
            statusAktualisieren(mitToast = false)
        }
    }

    private fun importiereGvpDateien(uris: List<Uri>) {
        txtStatus?.text = getString(R.string.import_export_v19_import_running, uris.size)
        viewLifecycleOwner.lifecycleScope.launch {
            val ergebnisse = withContext(Dispatchers.IO) {
                uris.map { uri -> importiereEineGvpDatei(uri) }
            }

            val ok = ergebnisse.count { it.ok }
            val fehler = ergebnisse.size - ok
            val nachricht = getString(R.string.import_export_v19_import_done_gvp, ok, fehler)
            Toast.makeText(requireContext(), nachricht, Toast.LENGTH_LONG).show()

            val details = ergebnisse.joinToString(separator = "\n") { ergebnis ->
                val marker = if (ergebnis.ok) "✓" else "!"
                "$marker ${ergebnis.dateiname}: ${ergebnis.meldung}"
            }
            txtStatus?.text = details
            statusAktualisieren(mitToast = false)
        }
    }

    private fun importiereKorDateien(uris: List<Uri>) {
        txtStatus?.text = getString(R.string.import_export_v19_import_running, uris.size)
        viewLifecycleOwner.lifecycleScope.launch {
            val ergebnisse = withContext(Dispatchers.IO) {
                uris.map { uri -> importiereEineKorDatei(uri) }
            }

            val ok = ergebnisse.count { it.ok }
            val fehler = ergebnisse.size - ok
            val nachricht = getString(R.string.import_export_v19_import_done_kor, ok, fehler)
            Toast.makeText(requireContext(), nachricht, Toast.LENGTH_LONG).show()

            val details = ergebnisse.joinToString(separator = "\n") { ergebnis ->
                val marker = if (ergebnis.ok) "✓" else "!"
                "$marker ${ergebnis.dateiname}: ${ergebnis.meldung}"
            }
            txtStatus?.text = details
            statusAktualisieren(mitToast = false)
        }
    }

    private suspend fun importiereEineGraDatei(uri: Uri): ImportErgebnis {
        val dateiname = dateinameAusUri(uri)
        return runCatching {
            val bytes = requireNotNull(requireContext().contentResolver.openInputStream(uri)) {
                "Datei konnte nicht geoeffnet werden."
            }.use { input -> input.readBytes() }

            val result = GraDateiParser.parse(dateiname, bytes)
            ServiceLocator.trassenRepository.graImportSpeichern(dateiname, result)

            val warnText = if (result.warnungen.isNotEmpty()) {
                " / ${result.warnungen.size} Warnung(en)"
            } else {
                ""
            }
            val kmVon = result.punkte.minOfOrNull { it.kmMeter }
            val kmBis = result.punkte.maxOfOrNull { it.kmMeter }
            val bereich = if (kmVon != null && kmBis != null) {
                ", ${formatKmOderStation(kmVon)}–${formatKmOderStation(kmBis)}"
            } else {
                ""
            }
            ImportErgebnis(
                dateiname = dateiname,
                ok = true,
                meldung = "${result.punkte.size} GRA/kmLinie-Punkte$bereich$warnText"
            )
        }.getOrElse { throwable ->
            ServiceLocator.trassenRepository.importStatusSpeichern(
                TrassenImportStatusEntity(
                    quelleDatei = dateiname,
                    datenTyp = "GRA",
                    status = "FEHLER",
                    fehler = 1,
                    meldung = throwable.message ?: throwable.javaClass.simpleName
                )
            )
            ImportErgebnis(
                dateiname = dateiname,
                ok = false,
                meldung = throwable.message ?: throwable.javaClass.simpleName
            )
        }
    }

    private suspend fun importiereEineGvpDatei(uri: Uri): ImportErgebnis {
        val dateiname = dateinameAusUri(uri)
        return runCatching {
            val bytes = requireNotNull(requireContext().contentResolver.openInputStream(uri)) {
                "Datei konnte nicht geoeffnet werden."
            }.use { input -> input.readBytes() }

            val result = GvpDateiParser.parse(dateiname, bytes)
            ServiceLocator.trassenRepository.gvpImportSpeichern(dateiname, result)

            val warnText = if (result.warnungen.isNotEmpty()) {
                " / ${result.warnungen.size} Warnung(en)"
            } else {
                ""
            }
            val strecken = result.punkte.mapNotNull { it.strecke }.distinct().joinToString()
                .ifBlank { "ohne Strecke" }
            ImportErgebnis(
                dateiname = dateiname,
                ok = true,
                meldung = "${result.punkte.size} GVP/Festpunkte, Strecke $strecken$warnText"
            )
        }.getOrElse { throwable ->
            ServiceLocator.trassenRepository.importStatusSpeichern(
                TrassenImportStatusEntity(
                    quelleDatei = dateiname,
                    datenTyp = "GVP",
                    status = "FEHLER",
                    fehler = 1,
                    meldung = throwable.message ?: throwable.javaClass.simpleName
                )
            )
            ImportErgebnis(
                dateiname = dateiname,
                ok = false,
                meldung = throwable.message ?: throwable.javaClass.simpleName
            )
        }
    }

    private suspend fun importiereEineKorDatei(uri: Uri): ImportErgebnis {
        val dateiname = dateinameAusUri(uri)
        return runCatching {
            val bytes = requireNotNull(requireContext().contentResolver.openInputStream(uri)) {
                "Datei konnte nicht geoeffnet werden."
            }.use { input -> input.readBytes() }

            val result = KorDateiParser.parse(dateiname, bytes)
            ServiceLocator.trassenRepository.korImportSpeichern(dateiname, result)

            val warnText = if (result.warnungen.isNotEmpty()) {
                " / ${result.warnungen.size} Warnung(en)"
            } else {
                ""
            }
            val gleise = result.punkte.mapNotNull { it.gleis }.distinct().joinToString()
                .ifBlank { "ohne Gleiszuordnung" }
            ImportErgebnis(
                dateiname = dateiname,
                ok = true,
                meldung = "${result.punkte.size} Absteckpunkte, Gleis $gleise$warnText"
            )
        }.getOrElse { throwable ->
            ServiceLocator.trassenRepository.importStatusSpeichern(
                TrassenImportStatusEntity(
                    quelleDatei = dateiname,
                    datenTyp = "KOR",
                    status = "FEHLER",
                    fehler = 1,
                    meldung = throwable.message ?: throwable.javaClass.simpleName
                )
            )
            ImportErgebnis(
                dateiname = dateiname,
                ok = false,
                meldung = throwable.message ?: throwable.javaClass.simpleName
            )
        }
    }

    private suspend fun importiereEineTraDatei(uri: Uri): ImportErgebnis {
        val dateiname = dateinameAusUri(uri)
        return runCatching {
            val bytes = requireNotNull(requireContext().contentResolver.openInputStream(uri)) {
                "Datei konnte nicht geoeffnet werden."
            }.use { input -> input.readBytes() }

            val result = TraDateiParser.parse(dateiname, bytes)
            ServiceLocator.trassenRepository.traImportSpeichern(result)

            val warnText = if (result.warnungen.isNotEmpty()) {
                " / ${result.warnungen.size} Warnung(en)"
            } else {
                ""
            }
            ImportErgebnis(
                dateiname = dateiname,
                ok = true,
                meldung = "${result.achse.anzahlElemente} Elemente, ${result.samples.size} Knoten$warnText"
            )
        }.getOrElse { throwable ->
            ServiceLocator.trassenRepository.importStatusSpeichern(
                TrassenImportStatusEntity(
                    quelleDatei = dateiname,
                    datenTyp = "TRA",
                    status = "FEHLER",
                    fehler = 1,
                    meldung = throwable.message ?: throwable.javaClass.simpleName
                )
            )
            ImportErgebnis(
                dateiname = dateiname,
                ok = false,
                meldung = throwable.message ?: throwable.javaClass.simpleName
            )
        }
    }

    private fun dateinameAusUri(uri: Uri): String {
        val resolver = requireContext().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "import.tra"
    }

    private fun vermessungsdatenLoeschenBestaetigen() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_export_v19_clear_title)
            .setMessage(R.string.import_export_v19_clear_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.import_export_v19_clear_positive) { _, _ ->
                txtStatus?.text = getString(R.string.import_export_v19_clear_running)
                val appContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    val geloescht = withContext(Dispatchers.IO) {
                        val dbErgebnis = ServiceLocator.trassenRepository.streckendatenKomplettZuruecksetzen()

                        // Persistente Zuordnungen gehoeren zum importierten Streckendatenstand.
                        // Sie muessen beim Reset ebenfalls weg, sonst koennen alte manuelle
                        // Zuordnungen (z. B. ein frueheres "Gl 28") nach dem Neuimport
                        // wieder auf die frische Roh-TRA angewendet werden.
                        val streckenPrefs = appContext.getSharedPreferences(
                            "tbhelfer_streckendaten_zuordnung",
                            Context.MODE_PRIVATE
                        )
                        val streckenZuordnungen = streckenPrefs.all.size
                        streckenPrefs.edit().clear().commit()

                        val weichenPrefs = appContext.getSharedPreferences(
                            "tbhelfer_weichen_zuordnung",
                            Context.MODE_PRIVATE
                        )
                        val weichenZuordnungen = weichenPrefs.all.size
                        weichenPrefs.edit().clear().commit()

                        // Nur Rechenstatus/-dauer, keine fachlichen GPS-Streckenpunkte.
                        appContext.getSharedPreferences(
                            "tb_helfer_trassen_berechnung",
                            Context.MODE_PRIVATE
                        ).edit().clear().commit()

                        dbErgebnis.gesamt + streckenZuordnungen + weichenZuordnungen
                    }

                    // Auch reine Anzeige-Caches des Fragments verwerfen.
                    letzterZipScan = null
                    letzteWeichenKandidaten = emptyList()
                    letzteWeichenAchsenById = emptyMap()
                    letzteGrafikSichtbareRohAchseIds = null
                    letzteGrafikSichtbarePunktArten = null

                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_export_v19_clear_done, geloescht),
                        Toast.LENGTH_LONG
                    ).show()
                    statusAktualisieren(mitToast = false)
                }
            }
            .show()
    }


    private fun datenBerechnenUndPruefen(warteDialog: androidx.appcompat.app.AlertDialog? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            val startMs = System.currentTimeMillis()
            var phaseText = "Vorbereitung"
            val prefs = requireContext().getSharedPreferences("tb_helfer_trassen_berechnung", Context.MODE_PRIVATE)
            val letzterLaufMs = prefs.getLong("letzte_dauer_ms", 0L).takeIf { it > 0L }

            layoutBerechnungsFortschritt?.visibility = View.VISIBLE
            progressBerechnung?.visibility = View.VISIBLE
            txtBerechnungsFortschritt?.text = berechnungStatusText(phaseText, startMs, letzterLaufMs)

            val progressJob = launch {
                while (true) {
                    txtBerechnungsFortschritt?.text = berechnungStatusText(phaseText, startMs, letzterLaufMs)
                    delay(500L)
                }
            }

            runCatching {
                phaseText = "1/3 GPS-Koordinaten"
                val gps = withContext(Dispatchers.IO) {
                    ServiceLocator.trassenRepository.gpsKoordinatenNachberechnen()
                }

                phaseText = "2/3 KM / Station"
                val km = withContext(Dispatchers.IO) {
                    ServiceLocator.trassenRepository.graMitAchspunktenVerknuepfen()
                }

                phaseText = "3/3 Prüfung"
                val uebersicht = withContext(Dispatchers.IO) {
                    ServiceLocator.trassenRepository.ladeUebersicht()
                }
                Triple(gps, km, uebersicht)
            }.onSuccess { (gps, km, uebersicht) ->
                progressJob.cancel()
                val dauerMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
                prefs.edit().putLong("letzte_dauer_ms", dauerMs).apply()
                progressBerechnung?.visibility = View.GONE
                txtBerechnungsFortschritt?.text = "✓ Berechnung fertig · ${formatDauer(dauerMs)}"

                val pruefung = uebersicht.toBerechnungsPruefungText()
                txtStatus?.text = buildString {
                    appendLine("Daten berechnet · ${formatDauer(dauerMs)}")
                    appendLine("GPS: ${gps.samplepunkte}/${uebersicht.anzahlSamplepunkte}")
                    appendLine("KM: ${uebersicht.anzahlGleisSamplepunkteMitKm}/${uebersicht.anzahlGleisSamplepunkte} Gleis-Achspunkte")
                    if (km.gvpSamplepunkteKalibriert > 0) {
                        appendLine("GVP-Kalibrierung: ${km.gvpSamplepunkteKalibriert} Punkte")
                    }
                    appendLine()
                    append(pruefung)
                }
                warteDialog?.dismiss()
                Toast.makeText(requireContext(), R.string.import_export_v19_data_calc_done, Toast.LENGTH_LONG).show()
            }.onFailure { throwable ->
                progressJob.cancel()
                progressBerechnung?.visibility = View.GONE
                txtBerechnungsFortschritt?.text = "✗ Berechnung fehlgeschlagen · ${formatDauer(System.currentTimeMillis() - startMs)}"
                txtStatus?.text = getString(
                    R.string.import_export_v19_status_error,
                    throwable.message ?: throwable.javaClass.simpleName
                )
                warteDialog?.dismiss()
            }
        }
    }

    private fun berechnungStatusText(phase: String, startMs: Long, letzterLaufMs: Long?): String {
        val vergangenMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
        val richtwert = letzterLaufMs?.let { letzter ->
            val restMs = (letzter - vergangenMs).coerceAtLeast(0L)
            if (restMs > 0L) {
                " · Richtwert noch ${formatDauer(restMs)}"
            } else {
                " · letzter Lauf ${formatDauer(letzter)}"
            }
        }.orEmpty()
        return "Berechnung läuft · $phase\nVergangen ${formatDauer(vergangenMs)}$richtwert"
    }

    private fun formatDauer(dauerMs: Long): String {
        val gesamtSekunden = (dauerMs.coerceAtLeast(0L) / 1000L)
        val minuten = gesamtSekunden / 60L
        val sekunden = gesamtSekunden % 60L
        return if (minuten > 0L) {
            String.format(Locale.GERMANY, "%d:%02d min", minuten, sekunden)
        } else {
            "${sekunden}s"
        }
    }


    private fun aktualisiereWeicheZuordnenButton() {
        // Patch45 NEU: Der alte separate Weichen-Zuordnen-Button wurde aus dem
        // vereinfachten Hauptmenü entfernt. Die Funktion bleibt als bewusstes
        // No-op erhalten, weil bestehende Live-GPS-Pfade sie weiterhin aufrufen.
    }

    private fun weicheZuordnenStarten() {
        val kandidat = letzteWeichenKandidaten.firstOrNull()
        if (kandidat == null) {
            Toast.makeText(requireContext(), "Erst Live-GPS prüfen: keine nahe Weiche gefunden.", Toast.LENGTH_LONG).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val achse = withContext(Dispatchers.IO) {
                ServiceLocator.trassenRepository.ladeUebersicht().achsen.firstOrNull { it.id == kandidat.achseId }
            }
            zeigeWeicheZuordnenDialog(kandidat.achseName, kandidat, achse)
        }
    }

    private fun weichenZuordnungenVerwalten() {
        viewLifecycleOwner.lifecycleScope.launch {
            val achsen = withContext(Dispatchers.IO) {
                ServiceLocator.trassenRepository.ladeUebersicht().achsen
                    .filter { it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE || it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG }
                    .sortedWith(compareBy<TrassenAchseEntity> { it.achsTyp }.thenBy { it.name })
            }
            if (achsen.isEmpty()) {
                Toast.makeText(requireContext(), "Keine Weichen/WV importiert.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val zeilen = achsen.map { it.toWeichenVerwaltungLine() }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Weichenzuordnungen verwalten")
                .setItems(zeilen) { _, which ->
                    zeigeWeicheZuordnenDialog(achsen[which].name, null, achsen[which])
                }
                .setNegativeButton("Schließen", null)
                .show()
        }
    }

    private fun zeigeWeicheZuordnenDialog(
        rohName: String,
        kandidat: TrassenNaheBerechner.Ergebnis?,
        achse: TrassenAchseEntity?
    ) {
        val vorhandeneZuordnung = ladeWeichenZuordnung(rohName)
        val laengeM = achse?.let { a ->
            if (a.stationVon != null && a.stationBis != null) a.stationBis - a.stationVon else null
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        fun text(zeile: String) {
            container.addView(TextView(requireContext()).apply {
                text = zeile
                textSize = 14f
                setPadding(0, 4, 0, 4)
            })
        }

        text("Gefundene Achse: $rohName")
        text("Länge: ${laengeM?.let { format1(it) + " m" } ?: "unbekannt"}")
        if (kandidat != null) {
            text("Aktueller Standort: ${kandidat.weichenMeterLangText(achse)}")
        } else {
            text("Aktueller Standort: kein Live-GPS in diesem Dialog")
        }
        text("Originalname bleibt gespeichert: $rohName")

        val nameInput = EditText(requireContext()).apply {
            hint = "DB-Weichenname, z.B. 52W26"
            setText(vorhandeneZuordnung?.dbWeichenName.orEmpty())
            setSingleLine(true)
        }
        container.addView(nameInput)

        text("Bahnhof: Gessertshausen / MGHN")
        text("Zuordnung: manuell")

        val lageSpinner = Spinner(requireContext())
        val lageOptionen = listOf(
            "unbekannt",
            "Linksweiche von WA gesehen",
            "Rechtsweiche von WA gesehen"
        )
        lageSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, lageOptionen)
        val lageIndex = lageOptionen.indexOf(vorhandeneZuordnung?.lage ?: "unbekannt").takeIf { it >= 0 } ?: 0
        lageSpinner.setSelection(lageIndex)
        text("Weichenlage:")
        container.addView(lageSpinner)

        val waInput = kmEditText("WA km, z.B. 17,225", vorhandeneZuordnung?.waKmMeter)
        val weVorschlag = vorhandeneZuordnung?.weKmMeter
            ?: vorhandeneZuordnung?.waKmMeter?.let { wa -> laengeM?.let { wa + it } }
        val weInput = kmEditText("WE km, Vorschlag aus WA + Länge", weVorschlag)
        val wezInput = kmEditText("WEZ km optional", vorhandeneZuordnung?.wezKmMeter)
        container.addView(waInput)
        container.addView(weInput)
        container.addView(wezInput)

        text("Hinweis: Die App nutzt automatisch die nächstliegende Gleisachse für km/Abstand.")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Weiche zuordnen")
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Speichern") { _, _ ->
                val wa = waInput.text?.toString().parseKmEingabeOderNull()
                val weEingabe = weInput.text?.toString().parseKmEingabeOderNull()
                val we = weEingabe ?: wa?.let { waMeter -> laengeM?.let { waMeter + it } }
                val zuordnung = WeichenZuordnung(
                    dbWeichenName = nameInput.text?.toString()?.trim().orEmpty(),
                    lage = lageSpinner.selectedItem?.toString() ?: "unbekannt",
                    waKmMeter = wa,
                    weKmMeter = we,
                    wezKmMeter = wezInput.text?.toString().parseKmEingabeOderNull(),
                    geaendertAmEpochMs = System.currentTimeMillis()
                )
                speichereWeichenZuordnung(rohName, zuordnung)
                Toast.makeText(requireContext(), "Weiche gespeichert: $rohName → ${zuordnung.anzeigeName()}", Toast.LENGTH_LONG).show()
                aktualisiereWeicheZuordnenButton()
                statusAktualisieren(mitToast = false)
            }
        if (vorhandeneZuordnung != null) {
            dialog.setNeutralButton("Zurücksetzen") { _, _ ->
                loescheWeichenZuordnung(rohName)
                Toast.makeText(requireContext(), "Zuordnung gelöscht: $rohName", Toast.LENGTH_LONG).show()
                aktualisiereWeicheZuordnenButton()
                statusAktualisieren(mitToast = false)
            }
        }
        dialog.show()
    }

    private fun kmEditText(hinweis: String, meter: Double?): EditText = EditText(requireContext()).apply {
        hint = hinweis
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        setText(meter?.let { formatKmEingabe(it) }.orEmpty())
        setSingleLine(true)
    }

    private fun String?.parseKmEingabeOderNull(): Double? {
        val raw = this?.trim().orEmpty()
        if (raw.isBlank()) return null
        val wert = raw.replace(" ", "").replace(",", ".").toDoubleOrNull() ?: return null
        return if (wert < 1000.0) wert * 1000.0 else wert
    }

    private fun formatKmEingabe(kmMeter: Double): String = String.format(Locale.GERMANY, "%.3f", kmMeter / 1000.0)

    private fun weichenPrefs() = requireContext().getSharedPreferences("tbhelfer_weichen_zuordnung", Context.MODE_PRIVATE)

    private fun ladeWeichenZuordnung(rohName: String): WeichenZuordnung? {
        val gespeichert = weichenPrefs().getString(rohName, null) ?: return null
        val teile = gespeichert.split("|")
        if (teile.size < 6) return null
        return WeichenZuordnung(
            dbWeichenName = teile[0],
            lage = teile[1].ifBlank { "unbekannt" },
            waKmMeter = teile[2].toDoubleOrNull(),
            weKmMeter = teile[3].toDoubleOrNull(),
            wezKmMeter = teile[4].toDoubleOrNull(),
            geaendertAmEpochMs = teile[5].toLongOrNull() ?: 0L
        )
    }

    private fun speichereWeichenZuordnung(rohName: String, zuordnung: WeichenZuordnung) {
        val wert = listOf(
            zuordnung.dbWeichenName.replace("|", "/"),
            zuordnung.lage.replace("|", "/"),
            zuordnung.waKmMeter?.toString().orEmpty(),
            zuordnung.weKmMeter?.toString().orEmpty(),
            zuordnung.wezKmMeter?.toString().orEmpty(),
            zuordnung.geaendertAmEpochMs.toString()
        ).joinToString("|")
        weichenPrefs().edit().putString(rohName, wert).apply()
    }

    private fun loescheWeichenZuordnung(rohName: String) {
        weichenPrefs().edit().remove(rohName).apply()
    }

    private data class WeichenZuordnung(
        val dbWeichenName: String,
        val lage: String,
        val waKmMeter: Double?,
        val weKmMeter: Double?,
        val wezKmMeter: Double?,
        val geaendertAmEpochMs: Long
    ) {
        fun anzeigeName(): String = dbWeichenName.takeIf { it.isNotBlank() } ?: "nicht zugeordnet"
        fun istZugeordnet(): Boolean = dbWeichenName.isNotBlank()
    }

    private data class GespeicherteStreckendatenZuordnung(
        val rawName: String,
        val typ: String,
        val kuerzel: String,
        val name: String,
        val strecken: List<String>,
        val geaendertAmEpochMs: Long
    ) {
        fun anzeigeName(): String {
            val k = kuerzel.trim()
            val n = name.trim()
            return when {
                k.isNotBlank() && n.isNotBlank() -> "$k / $n"
                k.isNotBlank() -> k
                n.isNotBlank() -> n
                else -> "nicht zugeordnet"
            }
        }
    }

    private fun ladeStreckendatenZuordnungen(): List<GespeicherteStreckendatenZuordnung> {
        val prefs = requireContext().getSharedPreferences("tbhelfer_streckendaten_zuordnung", Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            val keyTeile = key.split("|", limit = 2)
            if (keyTeile.size < 2) return@mapNotNull null
            val teile = (value as? String)?.split("|") ?: return@mapNotNull null
            if (teile.size < 4) return@mapNotNull null
            GespeicherteStreckendatenZuordnung(
                rawName = keyTeile[1],
                typ = teile[3].trim(),
                kuerzel = teile[0].trim(),
                name = teile[1].trim(),
                strecken = teile[2].split(",").map { it.trim() }.filter { it.isNotBlank() },
                geaendertAmEpochMs = teile.getOrNull(4)?.toLongOrNull() ?: 0L
            )
        }
    }

    private fun letzteBetriebsstelleAusStreckendatenImport(): String {
        val letzte = ladeStreckendatenZuordnungen()
            .filter { it.kuerzel.isNotBlank() || it.name.isNotBlank() }
            .maxByOrNull { it.geaendertAmEpochMs }
            ?: return ""
        return when {
            letzte.kuerzel.isNotBlank() && letzte.name.isNotBlank() -> "${letzte.kuerzel} · ${letzte.name}"
            letzte.kuerzel.isNotBlank() -> letzte.kuerzel
            else -> letzte.name
        }
    }

    private fun findeStreckendatenZuordnung(
        achse: TrassenAchseEntity,
        zuordnungen: List<GespeicherteStreckendatenZuordnung> = ladeStreckendatenZuordnungen()
    ): GespeicherteStreckendatenZuordnung? {
        val zielTyp = when (achse.achsTyp) {
            TrassenAchseEntity.ACHSTYP_GLEIS -> "BAHNHOFS_GLEIS"
            TrassenAchseEntity.ACHSTYP_WEICHE -> "WEICHE"
            TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> "WEICHENVERBINDUNG"
            else -> return null
        }
        return zuordnungen.firstOrNull { zuordnung ->
            if (!zuordnung.typ.equals(zielTyp, ignoreCase = true)) return@firstOrNull false
            val streckeOk = zuordnung.strecken.isEmpty() || achse.strecke.isNullOrBlank() || zuordnung.strecken.any { it == achse.strecke?.trim() }
            if (!streckeOk) return@firstOrNull false
            val achsName = normalisiereZuordnungsName(achse.name)
            val raw = normalisiereZuordnungsName(zuordnung.rawName)
            if (achsName == raw || achsName.contains(raw) || raw.contains(achsName)) return@firstOrNull true
            if (zielTyp == "BAHNHOFS_GLEIS") {
                val gleis = achse.gleis?.trim()?.trimStart('0')?.ifBlank { "0" } ?: return@firstOrNull false
                val rawGleis = Regex("(?i)(?:GL|GLEIS)[_\\s-]*(\\d+[A-Z]?)").find(zuordnung.rawName)
                    ?.groupValues?.getOrNull(1)
                    ?.trim()?.trimStart('0')?.ifBlank { "0" }
                rawGleis == gleis
            } else {
                false
            }
        }
    }

    private fun normalisiereZuordnungsName(raw: String): String = raw
        .replace(Regex("_ER0.*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("_N00.*", RegexOption.IGNORE_CASE), "")
        .substringBeforeLast('.')
        .uppercase(Locale.GERMANY)
        .replace(Regex("[^A-Z0-9]+"), "")
        .trim()


    private fun gpsKoordinatenNachberechnen() {
        txtStatus?.text = getString(R.string.import_export_v19_gps_running)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                ServiceLocator.trassenRepository.gpsKoordinatenNachberechnen()
            }.onSuccess { ergebnis ->
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.import_export_v19_gps_done,
                        ergebnis.samplepunkte,
                        ergebnis.gvpPunkte,
                        ergebnis.absteckpunkte,
                        ergebnis.fehler
                    ),
                    Toast.LENGTH_LONG
                ).show()
                statusAktualisieren(mitToast = false)
            }.onFailure { throwable ->
                txtStatus?.text = getString(
                    R.string.import_export_v19_status_error,
                    throwable.message ?: throwable.javaClass.simpleName
                )
            }
        }
    }


    private fun graMitAchspunktenVerknuepfen() {
        txtStatus?.text = getString(R.string.import_export_v19_gra_link_running)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                ServiceLocator.trassenRepository.graMitAchspunktenVerknuepfen()
            }.onSuccess { ergebnis ->
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.import_export_v19_gra_link_done,
                        ergebnis.achsenVerknuepft,
                        ergebnis.samplepunkteVerknuepft
                    ),
                    Toast.LENGTH_LONG
                ).show()
                statusAktualisieren(mitToast = false)
            }.onFailure { throwable ->
                txtStatus?.text = getString(
                    R.string.import_export_v19_status_error,
                    throwable.message ?: throwable.javaClass.simpleName
                )
            }
        }
    }


    private fun naheberechnungLiveTesten() {
        if (!StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
            starteNaheTestNachStandortBerechtigung = true
            standortPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        txtStatus?.text = getString(R.string.import_export_v19_nahe_test_running)
        letzteWeichenKandidaten = emptyList()
        letzteWeichenAchsenById = emptyMap()
        aktualisiereWeicheZuordnenButton()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val messung = sammleStabilenLiveStandort() ?: return@runCatching null

                val kandidaten = ServiceLocator.trassenRepository.findeNaechsteTrassenKandidaten(
                    lat = messung.latitude,
                    lon = messung.longitude,
                    maxAbstandM = 500.0,
                    limit = 12,
                    // Neutral heisst: keine Baustelle/Unterbaustelle als Filter.
                    // Fuer die Anzeige haben echte Gleis-/Weichenachsen trotzdem Vorrang
                    // vor allgemeinen Streckenachsen wie 5302S/5340S.
                    gleisVorrang = true
                )
                val achsenById = ServiceLocator.trassenRepository.ladeUebersicht().achsen.associateBy { it.id }
                LiveNaheResult(messung, kandidaten, achsenById)
            }.onSuccess { ergebnis ->
                if (ergebnis == null) {
                    txtStatus?.text = getString(R.string.import_export_v19_nahe_live_no_fix)
                    Toast.makeText(requireContext(), R.string.import_export_v19_nahe_live_no_fix, Toast.LENGTH_LONG).show()
                    return@onSuccess
                }
                val messung = ergebnis.messung
                val kandidaten = ergebnis.kandidaten
                letzteWeichenAchsenById = ergebnis.achsenById
                if (kandidaten.isEmpty()) {
                    letzteWeichenKandidaten = emptyList()
                    letzteWeichenAchsenById = emptyMap()
                    aktualisiereWeicheZuordnenButton()
                    txtStatus?.text = getString(
                        R.string.import_export_v19_nahe_live_no_axis,
                        formatGps(messung.latitude),
                        formatGps(messung.longitude)
                    )
                    Toast.makeText(requireContext(), R.string.import_export_v19_nahe_live_no_axis_short, Toast.LENGTH_LONG).show()
                } else {
                    letzteWeichenKandidaten = kandidaten
                        .filter {
                            (it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE || it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG) &&
                                it.abstandM <= 80.0
                        }
                        .sortedBy { it.abstandM }
                    aktualisiereWeicheZuordnenButton()
                    txtStatus?.text = kandidaten.toNaheStatusText(
                        liveLat = messung.latitude,
                        liveLon = messung.longitude,
                        accuracyM = messung.accuracyM,
                        fixCount = messung.fixCount,
                        durationMs = messung.durationMs,
                        bestAccuracyM = messung.bestAccuracyM,
                        achsenById = ergebnis.achsenById
                    )
                    val erster = kandidaten.first()
                    Toast.makeText(
                        requireContext(),
                        erster.toKompakteErgebnisZeile(),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure { throwable ->
                txtStatus?.text = getString(
                    R.string.import_export_v19_status_error,
                    throwable.message ?: throwable.javaClass.simpleName
                )
            }
        }
    }

    private suspend fun sammleStabilenLiveStandort(): LiveGpsMessung? {
        val fixes = mutableListOf<Location>()
        val startMs = System.currentTimeMillis()

        txtStatus?.text = getString(R.string.import_export_v19_nahe_test_running)

        coroutineScope {
            val job = launch {
                StandortLiveQuelle
                    .standortFlow(
                        context = requireContext(),
                        minTimeMs = 1000L,
                        minDistanceM = 0.0f,
                        // Fuer den neutralen Gleisachsen-Test bewusst keinen alten Cache-Fix
                        // verwenden. Wir sammeln aktuelle Fixes und bilden daraus eine
                        // stabile Messung.
                        emitLastKnown = false,
                        glaettungFenster = 1
                    )
                    .collect { loc ->
                        fixes += Location(loc)
                    }
            }
            delay(LIVE_GPS_SAMMEL_DAUER_MS)
            job.cancelAndJoin()
        }

        return fixes.toStabileLiveGpsMessung(System.currentTimeMillis() - startMs)
    }

    private fun List<Location>.toStabileLiveGpsMessung(durationMs: Long): LiveGpsMessung? {
        if (isEmpty()) return null

        val plausible = filter { loc ->
            !loc.hasAccuracy() || loc.accuracy <= 40f
        }.ifEmpty { this }

        val beste = plausible
            .sortedBy { loc -> if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE }
            .take(LIVE_GPS_BESTE_FIXES)
            .ifEmpty { plausible }

        val gewichte = beste.map { loc ->
            val acc = if (loc.hasAccuracy()) loc.accuracy.coerceAtLeast(1f).toDouble() else 10.0
            1.0 / (acc * acc)
        }
        val sumGewicht = gewichte.sum().takeIf { it > 0.0 } ?: return null
        val lat = beste.zip(gewichte).sumOf { (loc, gewicht) -> loc.latitude * gewicht } / sumGewicht
        val lon = beste.zip(gewichte).sumOf { (loc, gewicht) -> loc.longitude * gewicht } / sumGewicht
        val accuracies = beste.mapNotNull { loc -> if (loc.hasAccuracy()) loc.accuracy else null }.sorted()
        val medianAcc = accuracies.medianFloatOrNull()
        val bestAcc = accuracies.firstOrNull()

        return LiveGpsMessung(
            latitude = lat,
            longitude = lon,
            accuracyM = medianAcc,
            bestAccuracyM = bestAcc,
            fixCount = size,
            usedFixCount = beste.size,
            durationMs = durationMs
        )
    }

    private fun List<Float>.medianFloatOrNull(): Float? {
        if (isEmpty()) return null
        val mid = size / 2
        return if (size % 2 == 1) this[mid] else (this[mid - 1] + this[mid]) / 2f
    }



    // -------------------------------------------------------------------------
    // Patch45 NEU: einfacher Abschnitts-Editor statt automatischer Zerstueckelung.
    // Roh-TRA bleibt unveraendert; der Nutzer legt Start, KM-Punkte und Ende fest.
    // -------------------------------------------------------------------------

    private fun zeigeStreckenGleiseBearbeiten() {
        starteAbschnittGpsWarmup()
        viewLifecycleOwner.lifecycleScope.launch {
            val vorhandeneAbschnitte = withContext(Dispatchers.IO) { ServiceLocator.trassenDao.alleManuellenAbschnitte() }
            val vorhandeneObjekte = StreckenObjektSpeicher.alle(requireContext())
            val context = requireContext()
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(10), dp(24), dp(8))
            }
            layout.addView(TextView(context).apply {
                text = "TRA ist die technische Geometrie. Gleis-, Strecken- und Weichenabschnitte werden fachlich darübergelegt."
                textSize = 13f
                setPadding(0, 0, 0, dp(12))
            })
            val btnNeu = MaterialButton(context).apply {
                text = "Gleis-/Strecken-/Weichenabschnitt anlegen"
                isAllCaps = false
            }
            val btnDkw = MaterialButton(context).apply { text = "Neue DKW/Kreuzung anlegen"; isAllCaps = false }
            val btnVorhanden = MaterialButton(context).apply {
                text = "Bestehende Objekte (${vorhandeneAbschnitte.size + vorhandeneObjekte.size})"
                isAllCaps = false
                isEnabled = vorhandeneAbschnitte.isNotEmpty() || vorhandeneObjekte.isNotEmpty()
            }
            layout.addView(btnNeu)
            layout.addView(btnDkw)
            layout.addView(btnVorhanden)

            var weiter = false
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle("Strecken / Gleise / Weichen bearbeiten")
                .setView(layout)
                .setNegativeButton("Schließen", null)
                .create()
            btnNeu.setOnClickListener {
                weiter = true
                dialog.dismiss()
                zeigeNeuenAbschnittTypAuswahl()
            }
            btnDkw.setOnClickListener {
                weiter = true
                dialog.dismiss()
                zeigeStreckenObjektEditor(StreckenObjektSpeicher.Typ.DKW_KREUZUNG)
            }
            btnVorhanden.setOnClickListener {
                weiter = true
                dialog.dismiss()
                zeigeBestehendeStreckenObjekte()
            }
            dialog.setOnDismissListener { if (!weiter) stoppeAbschnittGpsWarmup() }
            dialog.show()
        }
    }

    private fun zeigeNeuenAbschnittTypAuswahl() {
        val optionen = arrayOf("Gleis", "Strecke", "Weiche")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Gleis-/Strecken-/Weichenabschnitt anlegen")
            .setItems(optionen) { _, which ->
                when (which) {
                    0 -> zeigeRohTraAuswahlFuerNeuenAbschnitt(TrassenAchseEntity.ACHSTYP_GLEIS)
                    1 -> zeigeRohTraAuswahlFuerNeuenAbschnitt(TrassenAchseEntity.ACHSTYP_STRECKE)
                    2 -> zeigeStreckenObjektEditor(StreckenObjektSpeicher.Typ.WEICHE)
                }
            }
            .setNegativeButton("Abbrechen") { _, _ -> stoppeAbschnittGpsWarmup() }
            .show()
    }

    /**
     * Patch45ZW: Die Roh-TRA ist reine Geometrie. Sie kann fachlich sowohl fuer ein Gleis
     * als auch fuer eine Strecke verwendet werden. Deshalb wird hier NICHT nach dem
     * importierten Achstyp GLEIS/STRECKE gefiltert. Weichen/WV bleiben separat.
     */
    private fun zeigeRohTraAuswahlFuerNeuenAbschnitt(fachTyp: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (rohAchsen, abschnitte) = withContext(Dispatchers.IO) {
                val raw = ladeRohGrafikAchsen()
                    .filter {
                        it.achsTyp != TrassenAchseEntity.ACHSTYP_WEICHE &&
                            it.achsTyp != TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG
                    }
                    .distinctBy { it.achseId }
                raw to ServiceLocator.trassenDao.alleManuellenAbschnitte()
            }
            if (rohAchsen.isEmpty()) {
                Toast.makeText(requireContext(), "Keine verwendbare TRA-Geometrie vorhanden.", Toast.LENGTH_LONG).show()
                return@launch
            }

            val rohById = rohAchsen.associateBy { it.achseId }
            val abschnittById = abschnitte.associateBy { it.id }
            val overlays = abschnitte.mapNotNull { abschnitt ->
                val rawId = abschnitt.aktuelleAchseId ?: return@mapNotNull null
                val roh = rohById[rawId] ?: return@mapNotNull null
                val segment = segmentFuerAbschnittGrafik(roh.punkte, abschnitt.stationStart, abschnitt.stationEnde)
                if (segment.size < 2) return@mapNotNull null
                TrassenGrafikAchse(
                    achseId = -abschnitt.id.coerceAtLeast(1L),
                    name = roh.name,
                    label = abschnitt.bezeichnung,
                    achsTyp = abschnitt.achsTyp,
                    strecke = abschnitt.strecke,
                    gleis = abschnitt.gleis,
                    richtung = abschnitt.richtung,
                    richtungQuelle = null,
                    quelleDatei = roh.quelleDatei,
                    dateiSignatur = roh.dateiSignatur,
                    notiz = "Betrieblicher Abschnitt",
                    istManuell = true,
                    kmStatus = abschnittKmStatus(abschnitt),
                    kmStatusGrund = "Betrieblicher Abschnitt",
                    manuellerAbschnittId = abschnitt.id,
                    abschnittStartStation = abschnitt.stationStart,
                    abschnittEndeStation = abschnitt.stationEnde,
                    punkte = segment
                )
            }
            val verwendeteRawIds = abschnitte.mapNotNull { it.aktuelleAchseId }.toSet()
            val rohDarstellung = rohAchsen.map { raw ->
                if (raw.achseId in verwendeteRawIds) raw.copy(neutralDarstellung = true, labelSichtbar = false) else raw
            }
            val darstellung = rohDarstellung + overlays
            val alleRawIds = rohAchsen.map { it.achseId }.toSet()
            val sichtbarRaw = (letzteGrafikSichtbareRohAchseIds
                ?.intersect(alleRawIds)
                ?.takeIf { it.isNotEmpty() }
                ?: alleRawIds).toMutableSet()

            val context = requireContext()
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            }
            val kopf = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            val btnZurueck = MaterialButton(context).apply { text = "Zurück"; minWidth = 0; minimumWidth = 0 }
            val titel = TextView(context).apply {
                text = if (fachTyp == TrassenAchseEntity.ACHSTYP_GLEIS) "Achse für neues Gleis" else "Achse für neue Strecke"
                textSize = 17f
                textStyleBold()
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnAnzeige = MaterialButton(context).apply { text = "Anzeige"; minWidth = 0; minimumWidth = 0 }
            kopf.addView(btnZurueck)
            kopf.addView(titel)
            kopf.addView(btnAnzeige)
            container.addView(kopf)
            container.addView(TextView(context).apply {
                text = "Gewünschte Geometrie bzw. freien Bereich antippen. Bereits angelegte Abschnitte bleiben sichtbar. Bei übereinanderliegenden Achsen lange drücken."
                textSize = 12f
                setPadding(dp(8), 0, dp(8), dp(4))
            })

            val grafik = TrassenGrafikView(context).apply {
                setAchsen(darstellung)
                setFestpunkteSichtbar(false)
                setSichtbareTypen(darstellung.map { it.achsTyp }.toSet())
                setLegendeSichtbar(false)
            }
            container.addView(grafik, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            fun sichtbareDarstellungsIds(): Set<Long> = darstellung.filter { achse ->
                if (achse.manuellerAbschnittId == null) {
                    achse.achseId in sichtbarRaw
                } else {
                    val rawId = abschnittById[achse.manuellerAbschnittId]?.aktuelleAchseId
                    rawId != null && rawId in sichtbarRaw
                }
            }.map { it.achseId }.toSet()

            fun aktualisiereSichtbarkeit(fit: Boolean = false) {
                grafik.setSichtbareAchseIds(sichtbareDarstellungsIds())
                if (fit) grafik.post { grafik.fitToContent() }
            }
            aktualisiereSichtbarkeit()

            lateinit var dialog: android.app.Dialog

            fun rawAchseFuerGrafikAchse(achse: TrassenGrafikAchse): TrassenGrafikAchse? {
                if (achse.manuellerAbschnittId == null) return rohById[achse.achseId]
                val rawId = abschnittById[achse.manuellerAbschnittId]?.aktuelleAchseId ?: return null
                return rohById[rawId]
            }

            fun pruefenUndWeiter(raw: TrassenGrafikAchse, station: Double) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val frei = withContext(Dispatchers.IO) { pruefeFreienTraBereich(raw, fachTyp, station) }
                    if (!frei.erlaubt) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Bereich nicht frei")
                            .setMessage(frei.meldung)
                            .setPositiveButton("OK", null)
                            .show()
                        return@launch
                    }
                    letzteGrafikSichtbareRohAchseIds = sichtbarRaw.toSet()
                    dialog.dismiss()
                    zeigeNeuenAbschnittAufRohTra(fachTyp, raw, frei)
                }
            }

            grafik.setOnAchsePunktRequested { achse, station ->
                rawAchseFuerGrafikAchse(achse)?.let { pruefenUndWeiter(it, station) }
            }
            grafik.setOnAchsenLongPressMitStationRequested { kandidaten ->
                val rawTreffer = kandidaten.mapNotNull { (achse, station) ->
                    rawAchseFuerGrafikAchse(achse)?.let { Triple(it.achseId, it, station) }
                }.distinctBy { it.first }
                when {
                    rawTreffer.isEmpty() -> Unit
                    rawTreffer.size == 1 -> pruefenUndWeiter(rawTreffer.first().second, rawTreffer.first().third)
                    else -> MaterialAlertDialogBuilder(context)
                        .setTitle("Welche Roh-TRA?")
                        .setItems(rawTreffer.map { it.second.toGrafikAuswahlText(rohAchsen) }.toTypedArray()) { _, which ->
                            val auswahl = rawTreffer[which]
                            pruefenUndWeiter(auswahl.second, auswahl.third)
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }

            fun zeigeAnzeige() {
                val liste = rohAchsen.sortedWith(compareBy<TrassenGrafikAchse> { it.label }.thenBy { it.name })
                val temp = sichtbarRaw.toMutableSet()
                val checked = BooleanArray(liste.size) { liste[it].achseId in temp }
                MaterialAlertDialogBuilder(context)
                    .setTitle("Anzeige · Orientierung")
                    .setMultiChoiceItems(liste.map { it.toGrafikAuswahlText(liste) }.toTypedArray(), checked) { _, which, isChecked ->
                        val id = liste[which].achseId
                        if (isChecked) temp.add(id) else temp.remove(id)
                    }
                    .setNegativeButton("Abbrechen", null)
                    .setNeutralButton("Alle anzeigen") { _, _ ->
                        sichtbarRaw.clear(); sichtbarRaw.addAll(alleRawIds)
                        letzteGrafikSichtbareRohAchseIds = sichtbarRaw.toSet()
                        aktualisiereSichtbarkeit(true)
                    }
                    .setPositiveButton("Übernehmen") { _, _ ->
                        sichtbarRaw.clear(); sichtbarRaw.addAll(temp)
                        if (sichtbarRaw.isEmpty()) sichtbarRaw.addAll(alleRawIds)
                        letzteGrafikSichtbareRohAchseIds = sichtbarRaw.toSet()
                        aktualisiereSichtbarkeit(true)
                    }
                    .show()
            }

            dialog = android.app.Dialog(context).apply {
                requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                setContentView(container)
                setCancelable(true)
            }
            btnZurueck.setOnClickListener { dialog.dismiss() }
            btnAnzeige.setOnClickListener { zeigeAnzeige() }
            ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                grafik.post { grafik.fitToContent() }
                insets
            }
            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                ViewCompat.requestApplyInsets(container)
                grafik.post { grafik.fitToContent() }
            }
            dialog.show()
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private suspend fun pruefeFreienTraBereich(
        rohAchse: TrassenGrafikAchse,
        fachTyp: String,
        station: Double
    ): TraFreiraumErgebnis {
        val rawPunkte = rohAchse.punkte.sortedBy { it.station }
        if (rawPunkte.size < 2) return TraFreiraumErgebnis(false, meldung = "Die TRA enthält zu wenige Geometriepunkte.")
        val rawVon = rawPunkte.first().station
        val rawBis = rawPunkte.last().station
        val abschnitte = ServiceLocator.trassenDao.alleManuellenAbschnitte()
            .filter { it.aktuelleAchseId == rohAchse.achseId && it.achsTyp == fachTyp }

        val offen = abschnitte.firstOrNull { it.stationStart == null || it.stationEnde == null }
        if (offen != null) {
            return TraFreiraumErgebnis(
                false,
                meldung = "${offen.bezeichnung} ist auf dieser TRA noch nicht mit zwei Grenzen eingegrenzt. Bitte diesen Abschnitt zuerst begrenzen; danach kann der freie Bereich separat angelegt werden."
            )
        }

        val belegt = abschnitte.mapNotNull { a ->
            val s = a.stationStart ?: return@mapNotNull null
            val e = a.stationEnde ?: return@mapNotNull null
            Triple(minOf(s, e), maxOf(s, e), a)
        }.sortedBy { it.first }

        val getroffen = belegt.firstOrNull { station >= it.first - 0.25 && station <= it.second + 0.25 }
        if (getroffen != null) {
            return TraFreiraumErgebnis(false, meldung = "Dieser Bereich gehört bereits zu ${getroffen.third.bezeichnung}. Bitte einen freien Teil derselben TRA antippen.")
        }
        if (belegt.isEmpty()) {
            return TraFreiraumErgebnis(true, null, null, null, null, "Gesamte TRA")
        }

        val links = belegt.filter { it.second < station }.maxOfOrNull { it.second } ?: rawVon
        val rechts = belegt.filter { it.first > station }.minOfOrNull { it.first } ?: rawBis
        if (rechts <= links + 0.05) return TraFreiraumErgebnis(false, meldung = "Zwischen den vorhandenen Abschnitten ist kein freier Bereich vorhanden.")
        return TraFreiraumErgebnis(
            erlaubt = true,
            stationVon = links,
            stationBis = rechts,
            quelleVon = if (kotlin.math.abs(links - rawVon) < 0.05) "TRA_ENDE" else "NACHBAR_ABSCHNITT",
            quelleBis = if (kotlin.math.abs(rechts - rawBis) < 0.05) "TRA_ENDE" else "NACHBAR_ABSCHNITT",
            meldung = "Freier TRA-Bereich ${format1(links)}–${format1(rechts)} m"
        )
    }

    private fun zeigeNeuenAbschnittAufRohTra(
        fachTyp: String,
        rohAchse: TrassenGrafikAchse,
        freiraum: TraFreiraumErgebnis
    ) {
        val context = requireContext()
        val input = EditText(context).apply {
            hint = if (fachTyp == TrassenAchseEntity.ACHSTYP_GLEIS) "Gleis, z. B. 28" else "Strecke, z. B. 5528-1"
            setSingleLine(true)
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(TextView(context).apply {
                text = buildString {
                    appendLine("Wie soll dieser Abschnitt heißen?")
                    appendLine("Der technische TRA-Name bleibt intern und wird nicht umbenannt.")
                    append(freiraum.meldung)
                }
                textSize = 13f
                setPadding(0, 0, 0, dp(8))
            })
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (fachTyp == TrassenAchseEntity.ACHSTYP_GLEIS) "Neues Gleis" else "Neue Strecke")
            .setView(layout)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Anlegen", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val basis = erstelleAbschnittEntwurf(fachTyp, input.text?.toString().orEmpty())
                if (basis == null) {
                    Toast.makeText(context, "Bitte eine gültige Bezeichnung eingeben.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val entwurf = basis.copy(
                    achseId = rohAchse.achseId,
                    achseName = rohAchse.name,
                    quelleDatei = rohAchse.quelleDatei,
                    dateiSignatur = rohAchse.dateiSignatur,
                    stationStart = freiraum.stationVon,
                    stationEnde = freiraum.stationBis,
                    startQuelle = freiraum.quelleVon,
                    endeQuelle = freiraum.quelleBis,
                    bearbeitungsStatus = TrassenManuellerAbschnittEntity.STATUS_OFFEN
                )
                dialog.dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    val mitKm = withContext(Dispatchers.IO) { uebernehmePlausibleKmKalibrierungDerselbenRohAchse(entwurf, rohAchse.achseId) }
                    val gespeichert = withContext(Dispatchers.IO) { persistiereAbschnittEntwurf(mitKm) }
                    zeigeNeuerAbschnittWeiterSchritt(gespeichert)
                }
            }
        }
        dialog.show()
    }

    private suspend fun uebernehmePlausibleKmKalibrierungDerselbenRohAchse(
        entwurf: ManuellerAbschnittEntwurf,
        rohAchseId: Long
    ): ManuellerAbschnittEntwurf {
        val kandidaten = ServiceLocator.trassenDao.alleManuellenAbschnitte()
            .filter { kandidat ->
                kandidat.aktuelleAchseId == rohAchseId &&
                    kandidat.achsTyp == entwurf.achsTyp &&
                    (entwurf.achsTyp != TrassenAchseEntity.ACHSTYP_STRECKE || entwurf.strecke.isNullOrBlank() || kandidat.strecke == entwurf.strecke) &&
                    kandidat.kmAnkerStation != null && kandidat.kmAnkerMeter != null && kandidat.kmStationVorzeichen in setOf(-1, 1)
            }
        if (kandidaten.isEmpty()) return entwurf
        val basis = kandidaten.first()
        val basisStation = basis.kmAnkerStation ?: return entwurf
        val basisKm = basis.kmAnkerMeter ?: return entwurf
        val vorzeichen = basis.kmStationVorzeichen ?: return entwurf
        val konsistent = kandidaten.all { kandidat ->
            val station = kandidat.kmAnkerStation ?: return@all false
            val km = kandidat.kmAnkerMeter ?: return@all false
            if (kandidat.kmStationVorzeichen != vorzeichen) return@all false
            kotlin.math.abs((basisKm + vorzeichen * (station - basisStation)) - km) <= 2.0
        }
        return if (konsistent) entwurf.copy(kmAnkerStation = basisStation, kmAnkerMeter = basisKm, kmStationVorzeichen = vorzeichen) else entwurf
    }

    private fun zeigeNeuerAbschnittWeiterSchritt(entwurf: ManuellerAbschnittEntwurf) {
        val kmVorhanden = entwurf.kmAnkerStation != null && entwurf.kmAnkerMeter != null && entwurf.kmStationVorzeichen != null
        val optionen = listOf(
            if (kmVorhanden) "KM-Achse vorhanden · Kontrollanker optional" else "KM-Anker setzen · Vermarkung / GPS",
            "Bereich / Grenzen festlegen",
            "Später bearbeiten"
        )
        zeigeAuswahlMitHinweis(
            titel = "${entwurf.bezeichnung} · Weiter",
            hinweis = if (kmVorhanden) {
                "Für diese Roh-TRA ist bereits eine widerspruchsfreie KM-Kalibrierung vorhanden. Der neue Abschnitt kann sie verwenden; ein weiterer Punkt dient nur der Kontrolle."
            } else {
                "KM-Kalibrierung und betriebliche Grenzen sind getrennt. Ohne zwei Grenzen bleibt der Abschnitt offen und ROT; er wird nicht auf die gesamte Roh-TRA angewendet."
            },
            optionen = optionen,
            negativText = "Schließen",
            onNegativ = { stoppeAbschnittGpsWarmup() }
        ) { index ->
            when (index) {
                0 -> zeigeKmPunktSetzen(entwurf, if (kmVorhanden) TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER else TrassenAbschnittKmPunktEntity.TYP_ANKER, false)
                1 -> zeigeAbschnittGrenzenMenue(entwurf)
                2 -> zeigeAbschnittBearbeitungsMenue(entwurf)
            }
        }
    }

    private fun segmentFuerAbschnittGrafik(
        roh: List<TrassenGrafikPunkt>,
        grenze1: Double?,
        grenze2: Double?
    ): List<TrassenGrafikPunkt> {
        if (roh.size < 2) return roh
        // Solange nicht beide Grenzen gesetzt sind, bleibt die fachliche Bezeichnung
        // bewusst auf der gesamten TRA sichtbar. Erst zwei Grenzen schneiden den Abschnitt aus.
        if (grenze1 == null || grenze2 == null) return roh
        val von = minOf(grenze1, grenze2)
        val bis = maxOf(grenze1, grenze2)
        val innen = roh.filter { it.station in von..bis }.toMutableList()
        fun interpoliert(station: Double): TrassenGrafikPunkt? {
            val exact = roh.minByOrNull { kotlin.math.abs(it.station - station) }
            if (exact != null && kotlin.math.abs(exact.station - station) < 0.001) return exact
            val i = roh.indexOfLast { it.station <= station }
            if (i < 0 || i >= roh.lastIndex) return null
            val a = roh[i]; val b = roh[i + 1]
            val ds = b.station - a.station
            if (kotlin.math.abs(ds) < 0.000001) return a
            val f = ((station - a.station) / ds).coerceIn(0.0, 1.0).toFloat()
            return TrassenGrafikPunkt(station, a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, null)
        }
        interpoliert(von)?.let { innen.add(it) }
        interpoliert(bis)?.let { innen.add(it) }
        return innen.distinctBy { kotlin.math.round(it.station * 1000.0).toLong() }.sortedBy { it.station }
    }

    private fun abschnittKmStatus(abschnitt: TrassenManuellerAbschnittEntity): String =
        TrassenKmStatusLogik.bewerteManuellenAbschnitt(
            kmAnkerStation = abschnitt.kmAnkerStation,
            kmAnkerMeter = abschnitt.kmAnkerMeter,
            kmStationVorzeichen = abschnitt.kmStationVorzeichen,
            stationStart = abschnitt.stationStart,
            stationEnde = abschnitt.stationEnde
        ).status

    private fun mitStandortBerechtigung(aktion: () -> Unit) {
        if (StandortLiveQuelle.hatStandortBerechtigung(requireContext())) {
            starteAbschnittGpsWarmup()
            aktion()
        } else {
            pendingStandortAktion = aktion
            standortPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun zeigeBestehendeStreckenObjekte() {
        viewLifecycleOwner.lifecycleScope.launch {
            val abschnitte = withContext(Dispatchers.IO) { ServiceLocator.trassenDao.alleManuellenAbschnitte() }
            val objekte = StreckenObjektSpeicher.alle(requireContext())
            val aktionen = mutableListOf<Pair<String, () -> Unit>>()
            if (abschnitte.isNotEmpty()) {
                aktionen += "Gleis-/Streckenabschnitte (${abschnitte.size})" to { zeigeManuelleAbschnitteListe() }
            }
            objekte.forEach { objekt ->
                val typText = if (objekt.typ == StreckenObjektSpeicher.Typ.WEICHE) "Weiche" else "DKW/Kreuzung"
                val punktText = if (objekt.punkte.isEmpty()) "noch ohne Punkte" else objekt.punkte.joinToString("/") { it.rolle }
                aktionen += "$typText · ${objekt.betriebsstelle} · ${objekt.bezeichnung}\n$punktText" to {
                    zeigeStreckenObjektAktionen(objekt)
                }
            }
            if (aktionen.isEmpty()) {
                Toast.makeText(requireContext(), "Noch keine Objekte gespeichert.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Bestehende Objekte")
                .setItems(aktionen.map { it.first }.toTypedArray()) { _, which -> aktionen.getOrNull(which)?.second?.invoke() }
                .setNegativeButton("Schließen", null)
                .show()
        }
    }

    private fun zeigeStreckenObjektAktionen(objekt: StreckenObjektSpeicher.Objekt) {
        val status = buildString {
            appendLine("${objekt.betriebsstelle} · ${objekt.bezeichnung}")
            appendLine(objekt.traAchsenPaar?.let { "TRA: $it${objekt.traAchseName?.let { n -> " · $n" }.orEmpty()}" } ?: "Keine eindeutige TRA-Zuordnung")
            append(if (objekt.traHatKm) "Kilometrierung verwendbar" else "Keine verwendbare TRA-Kilometrierung")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (objekt.typ == StreckenObjektSpeicher.Typ.WEICHE) "Weiche" else "DKW/Kreuzung")
            .setMessage(status)
            .setPositiveButton("Bearbeiten") { _, _ -> zeigeStreckenObjektEditor(objekt.typ, objekt.betriebsstelle, objekt.bezeichnung, objekt.id) }
            .setNeutralButton("Löschen") { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Objekt löschen?")
                    .setMessage("${objekt.bezeichnung} wird aus den optionalen Streckendaten entfernt. Die Baustelle bleibt erhalten.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Löschen") { _, _ ->
                        StreckenObjektSpeicher.loeschen(requireContext(), objekt.id)
                        Toast.makeText(requireContext(), "${objekt.bezeichnung} gelöscht.", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun zeigeStreckenObjektEditor(
        typ: StreckenObjektSpeicher.Typ,
        prefillBetriebsstelle: String = "",
        prefillObjekt: String = "",
        objektId: String? = null
    ) {
        val context = requireContext()
        val vorhanden = objektId?.let { StreckenObjektSpeicher.objektMitId(context, it) }
            ?: StreckenObjektSpeicher.findePassend(context, prefillBetriebsstelle, prefillObjekt, typ)
        val rollen = if (typ == StreckenObjektSpeicher.Typ.WEICHE) listOf("WA", "WE", "WEZ") else listOf("A", "B", "C", "D")
        val punkte = vorhanden?.punkte?.associateBy { it.rolle }?.toMutableMap() ?: mutableMapOf()
        val punktZeilen = mutableMapOf<String, StreckenObjektPunktUi>()

        val inhalt = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        inhalt.addView(TextView(context).apply {
            text = "Weichenpunkte WA/WE/WEZ und DKW-Punkte A/B/C/D können per Vermarkungspunkt oder GPS gesetzt werden. km bleibt optional; vorhandener Datei-km wird übernommen."
            textSize = 13f
            setPadding(0, 0, 0, dp(8))
        })
        val betriebsstellenVorschlaege = ladeBetriebsstellenVorschlaegeAusAssets()
        val betriebsstelleStart = vorhanden?.betriebsstelle
            ?: prefillBetriebsstelle.takeIf { it.isNotBlank() }
            ?: BahnhofSucheUtil.ladeLetzteEingabe(context).takeIf { it.isNotBlank() }
            ?: letzteBetriebsstelleAusStreckendatenImport()
        val betriebsstelle = AutoCompleteTextView(context).apply {
            hint = "Betriebsstelle, z. B. MPE oder Pasing"
            setSingleLine(true)
            threshold = 1
            setText(betriebsstelleStart, false)
        }
        bindeBetriebsstellenVorschlaege(betriebsstelle, betriebsstellenVorschlaege)
        val bezeichnung = EditText(context).apply {
            hint = if (typ == StreckenObjektSpeicher.Typ.WEICHE) "Bezeichnung, z. B. W 23" else "Bezeichnung, z. B. DKW 15 oder Kreuzung 3"
            setSingleLine(true)
            setText(vorhanden?.bezeichnung ?: prefillObjekt)
        }
        inhalt.addView(betriebsstelle)
        inhalt.addView(bezeichnung)
        if (typ == StreckenObjektSpeicher.Typ.DKW_KREUZUNG) {
            inhalt.addView(DkwKreuzungSchemaView(context))
        }

        fun statusText(p: StreckenObjektSpeicher.Punkt?): String = when {
            p == null -> "Noch nicht gesetzt"
            p.lat != null && p.lon != null -> buildString {
                append("GPS ✓")
                p.accuracyM?.let { append(" · ±${format1(it.toDouble())} m") }
                p.achseName?.let { append("\nTRA-Vorschlag: $it") }
                p.achsabstandM?.let { append(" · ${format1(it)} m") }
                if (p.km != null) append("\nkm ${formatKmEingabe(p.km)}${if (p.kmManuell) " manuell" else ""}")
            }
            p.achseId != null && p.station != null -> buildString {
                append("Vermarkung ✓")
                p.achseName?.let { append(" · TRA $it") }
                append(" · Station ${format1(p.station)} m")
                p.achsabstandM?.let { append(" · ${format1(it)} m") }
                if (p.km != null) append("\nkm ${formatKmEingabe(p.km)}${if (p.kmManuell) " manuell" else " aus Datei"}")
            }
            p.km != null -> "km ${formatKmEingabe(p.km)} eingetragen · GPS/Vermarkung noch offen"
            else -> "Noch nicht gesetzt"
        }

        fun aktualisiereZeile(rolle: String) {
            val ui = punktZeilen[rolle] ?: return
            val punkt = punkte[rolle]
            ui.status.text = statusText(punkt)
            ui.gps.text = if (punkt?.lat != null && punkt.lon != null) "GPS neu setzen" else "GPS setzen"
            ui.kmWirdAktualisiert = true
            ui.km.setText(punkt?.km?.let(::formatKmEingabe).orEmpty())
            ui.kmWirdAktualisiert = false
        }

        fun punktUebernehmen(rolle: String, punkt: StreckenObjektSpeicher.Punkt) {
            punkte[rolle] = punkt.copy(rolle = rolle)
            aktualisiereZeile(rolle)
        }

        fun gpsMessungUebernehmen(rolle: String, messung: AbschnittGpsMessung) {
            viewLifecycleOwner.lifecycleScope.launch {
                val kandidat = withContext(Dispatchers.IO) {
                    ServiceLocator.trassenRepository.findeNaechsteTrassenKandidaten(
                        messung.latitude, messung.longitude, 30.0, 6, false
                    ).firstOrNull()
                }
                val alt = punkte[rolle]
                val punkt = StreckenObjektSpeicher.Punkt(
                    rolle = rolle,
                    lat = messung.latitude,
                    lon = messung.longitude,
                    accuracyM = messung.accuracyM,
                    km = alt?.km?.takeIf { alt.kmManuell } ?: kandidat?.kmMeter,
                    kmManuell = alt?.kmManuell == true,
                    achseId = kandidat?.achseId,
                    achseName = kandidat?.achseName,
                    station = kandidat?.station,
                    achsabstandM = kandidat?.abstandM
                )
                punktUebernehmen(rolle, punkt)
            }
        }

        fun vermarkungWaehlen(rolle: String) {
            viewLifecycleOwner.lifecycleScope.launch {
                val (festpunkte, achsen) = withContext(Dispatchers.IO) {
                    ladeGrafikVermessungspunkte() to ladeRohGrafikAchsen()
                }
                val rollenRegex = Regex("(?i)^${Regex.escape(rolle)}(?:[_\\s-]|\\d|$)")
                val direktPassend = festpunkte.filter { punkt ->
                    rollenRegex.containsMatchIn(punkt.label.trim()) ||
                        rollenRegex.containsMatchIn(punkt.punktId.orEmpty().trim())
                }
                val kandidaten = if (typ == StreckenObjektSpeicher.Typ.WEICHE) {
                    direktPassend.ifEmpty {
                        festpunkte.filter { it.art.equals("WEICHENPUNKT", ignoreCase = true) }
                    }
                } else {
                    val gvp = festpunkte.filter {
                        it.art.equals("GVP", ignoreCase = true) ||
                            it.art.equals("FESTPUNKT", ignoreCase = true)
                    }
                    val passendeGvp = gvp.filter { punkt ->
                        rollenRegex.containsMatchIn(punkt.label.trim()) ||
                            rollenRegex.containsMatchIn(punkt.punktId.orEmpty().trim())
                    }
                    passendeGvp.ifEmpty { gvp }
                }.sortedWith(compareBy<TrassenGrafikFestpunkt> { it.label }.thenBy { it.punktId.orEmpty() })

                if (kandidaten.isEmpty()) {
                    Toast.makeText(context, "Keine passenden WA/WE/WEZ-Vermarkungspunkte vorhanden.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                fun uebernehmen(punkt: TrassenGrafikFestpunkt) {
                    val beste = achsen.mapNotNull { achse ->
                        projiziereVermessungspunktAufAchse(punkt, achse)?.let { projektion -> achse to projektion }
                    }.minByOrNull { it.second.abstandM }
                    val achse = beste?.first
                    val projektion = beste?.second
                    if (projektion == null || achse == null) {
                        Toast.makeText(context, "Vermarkungspunkt konnte keiner TRA sicher zugeordnet werden.", Toast.LENGTH_LONG).show()
                        return
                    }
                    val objektPunkt = StreckenObjektSpeicher.Punkt(
                        rolle = rolle,
                        km = punkt.kmMeter,
                        kmManuell = false,
                        achseId = achse.achseId,
                        achseName = achse.label.takeIf { it.isNotBlank() } ?: achse.name,
                        station = projektion.station,
                        achsabstandM = projektion.abstandM
                    )
                    val kmText = punkt.kmMeter?.let { "km ${formatKmMeter(it)} aus Datei" } ?: "kein Datei-km"
                    MaterialAlertDialogBuilder(context)
                        .setTitle("$rolle aus Vermarkung übernehmen?")
                        .setMessage("${punkt.label.ifBlank { punkt.punktId ?: rolle }}\n$kmText\nTRA: ${objektPunkt.achseName} · Station ${format1(projektion.station)} m · Abstand ${format1(projektion.abstandM)} m")
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Übernehmen") { _, _ -> punktUebernehmen(rolle, objektPunkt) }
                        .show()
                }

                MaterialAlertDialogBuilder(context)
                    .setTitle("$rolle · Vermarkungspunkt wählen")
                    .setItems(kandidaten.map { it.toGrafikPunktAuswahlText() }.toTypedArray()) { _, which ->
                        uebernehmen(kandidaten[which])
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }

        rollen.forEach { rolle ->
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            box.addView(TextView(context).apply { text = rolle; textStyleBold() })
            val zeile = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val gps = MaterialButton(context).apply {
                text = "GPS setzen"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val vermarkung = MaterialButton(context).apply {
                text = "Vermarkung wählen"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
            }
            val km = EditText(context).apply {
                hint = "km optional"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) }
            }
            val status = TextView(context).apply { textSize = 12f; setPadding(0, dp(3), 0, 0) }
            zeile.addView(gps)
            zeile.addView(km)
            box.addView(zeile)
            vermarkung?.let { box.addView(it) }
            box.addView(status)
            inhalt.addView(box)
            val ui = StreckenObjektPunktUi(gps, km, status)
            punktZeilen[rolle] = ui
            km.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (ui.kmWirdAktualisiert) return
                    val wert = s?.toString().parseKmEingabeOderNull()
                    val alt = punkte[rolle]
                    if (alt != null) {
                        punkte[rolle] = alt.copy(km = wert, kmManuell = wert != null)
                    } else if (wert != null) {
                        punkte[rolle] = StreckenObjektSpeicher.Punkt(rolle = rolle, km = wert, kmManuell = true)
                    }
                    status.text = statusText(punkte[rolle])
                }
            })
            gps.setOnClickListener {
                mitStandortBerechtigung {
                    sammleAbschnittGpsMessung("$rolle per GPS setzen") { messung ->
                        val nahe = StreckenObjektSpeicher.findeNahePunkte(context, messung.latitude, messung.longitude, 5.0, vorhanden?.id).firstOrNull()
                        if (nahe != null) {
                            MaterialAlertDialogBuilder(context)
                                .setTitle("Vorhandenen Punkt übernehmen?")
                                .setMessage("${nahe.punkt.rolle} von ${nahe.objekt.bezeichnung} liegt ${format1(nahe.abstandM)} m entfernt und kann als $rolle verwendet werden.")
                                .setNegativeButton("Neuen Punkt") { _, _ -> gpsMessungUebernehmen(rolle, messung) }
                                .setPositiveButton("Übernehmen") { _, _ -> punktUebernehmen(rolle, nahe.punkt.copy(rolle = rolle)) }
                                .show()
                        } else {
                            gpsMessungUebernehmen(rolle, messung)
                        }
                    }
                }
            }
            vermarkung?.setOnClickListener { vermarkungWaehlen(rolle) }
            aktualisiereZeile(rolle)
        }

        val statusGesamt = TextView(context).apply {
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
            text = vorhanden?.let(::streckenObjektStatusText) ?: "Noch nicht geprüft"
        }
        inhalt.addView(statusGesamt)
        val scroll = ScrollView(context).apply { addView(inhalt) }
        val titel = if (typ == StreckenObjektSpeicher.Typ.WEICHE) "Weiche anlegen / bearbeiten" else "DKW/Kreuzung anlegen / bearbeiten"
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titel)
            .setView(scroll)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Prüfen und speichern", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val bs = betriebsstelle.text?.toString()?.trim().orEmpty()
                val name = bezeichnung.text?.toString()?.trim().orEmpty()
                if (bs.isBlank() || name.isBlank()) {
                    Toast.makeText(context, "Betriebsstelle und Bezeichnung sind Pflicht.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                BahnhofSucheUtil.speichereLetzteEingabe(context, bs)
                val basis = StreckenObjektSpeicher.Objekt(
                    id = vorhanden?.id ?: java.util.UUID.randomUUID().toString(),
                    betriebsstelle = bs,
                    bezeichnung = name,
                    typ = typ,
                    punkte = rollen.mapNotNull { rolle ->
                        punkte[rolle]?.takeIf { punkt ->
                            (punkt.lat != null && punkt.lon != null) ||
                                punkt.km != null ||
                                (punkt.achseId != null && punkt.station != null)
                        }
                    }
                )
                val analysiert = analysiereStreckenObjekt(basis)
                StreckenObjektSpeicher.speichern(context, analysiert)
                statusGesamt.text = streckenObjektStatusText(analysiert)
                Toast.makeText(context, "${name} gespeichert. ${if (analysiert.traHatKm) "TRA und km verwendbar." else if (analysiert.traAchseId != null) "TRA-Geometrie ohne vollständigen km." else "Ohne eindeutige TRA-Zuordnung."}", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun analysiereStreckenObjekt(objekt: StreckenObjektSpeicher.Objekt): StreckenObjektSpeicher.Objekt {
        val byRole = objekt.punkte.associateBy { it.rolle }
        val paare = if (objekt.typ == StreckenObjektSpeicher.Typ.WEICHE) {
            listOf("WA–WE" to ("WA" to "WE"), "WA–WEZ" to ("WA" to "WEZ"))
        } else {
            listOf("D–A" to ("D" to "A"), "C–B" to ("C" to "B"))
        }
        val treffer = paare.mapNotNull { (label, rollen) ->
            val p1 = byRole[rollen.first] ?: return@mapNotNull null
            val p2 = byRole[rollen.second] ?: return@mapNotNull null
            val achse = p1.achseId?.takeIf { it == p2.achseId } ?: return@mapNotNull null
            val abstand = (p1.achsabstandM ?: 99.0) + (p2.achsabstandM ?: 99.0)
            StreckenObjektAchsenTreffer(label, achse, p1.achseName ?: p2.achseName, p1.km != null && p2.km != null, abstand)
        }.minByOrNull { it.abstandSumme }
        return objekt.copy(
            traAchsenPaar = treffer?.label,
            traAchseId = treffer?.achseId,
            traAchseName = treffer?.achseName,
            traHatKm = treffer?.hatKm == true
        )
    }

    private fun streckenObjektStatusText(objekt: StreckenObjektSpeicher.Objekt): String = buildString {
        val punktStatus = if (objekt.punkte.isEmpty()) {
            "noch keine"
        } else {
            objekt.punkte.joinToString(" · ") { punkt ->
                buildString {
                    append(punkt.rolle)
                    if (punkt.lat != null && punkt.lon != null) append(" GPS✓")
                    if (punkt.km != null) append(" km✓")
                }
            }
        }
        appendLine("Punkte: $punktStatus")
        when {
            objekt.traAchseId == null -> append("Keine eindeutige TRA-Achse gefunden. Hilfsachsen bleiben aus den gesetzten Punkten möglich.")
            objekt.traHatKm -> append("TRA ${objekt.traAchsenPaar} · ${objekt.traAchseName.orEmpty()} · Kilometrierung verwendbar")
            else -> append("TRA-Geometrie ${objekt.traAchsenPaar} · ${objekt.traAchseName.orEmpty()} · keine vollständige Kilometrierung")
        }
    }


    private fun starteAbschnittGpsWarmup() {
        if (!StandortLiveQuelle.hatStandortBerechtigung(requireContext())) return
        if (abschnittGpsWarmupJob?.isActive != true) {
            abschnittGpsWarmupJob = viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    StandortLiveQuelle.standortFlow(
                        context = requireContext(),
                        minTimeMs = 500L,
                        minDistanceM = 0.0f,
                        emitLastKnown = false,
                        glaettungFenster = 1
                    ).collect { loc ->
                        abschnittGpsFixes.addLast(Location(loc))
                        while (abschnittGpsFixes.size > 180) abschnittGpsFixes.removeFirst()
                        val minZeit = System.currentTimeMillis() - 90_000L
                        while (abschnittGpsFixes.isNotEmpty() && abschnittGpsFixes.first().time < minZeit) {
                            abschnittGpsFixes.removeFirst()
                        }
                    }
                }
            }
        }
        if (abschnittGnssJob?.isActive != true) {
            abschnittGnssJob = viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    StandortLiveQuelle.gnssStatusFlow(requireContext()).collect { status ->
                        abschnittGnssStatus = status
                    }
                }
            }
        }
    }

    private fun stoppeAbschnittGpsWarmup() {
        abschnittGpsWarmupJob?.cancel()
        abschnittGnssJob?.cancel()
        abschnittLiveDistanzJob?.cancel()
        abschnittGpsWarmupJob = null
        abschnittGnssJob = null
        abschnittLiveDistanzJob = null
        abschnittGpsFixes.clear()
    }

    private fun zeigeAbschnittObjektSchritt() {
        starteAbschnittGpsWarmup()
        val context = requireContext()
        val typWerte = listOf(
            TrassenAchseEntity.ACHSTYP_GLEIS,
            TrassenAchseEntity.ACHSTYP_STRECKE
        )
        val typTexte = listOf("Gleis", "Strecke")
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, typTexte)
        }
        val bezeichnung = EditText(context).apply {
            hint = "z. B. 28 oder 5528-0"
            setSingleLine(true)
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(8), dp(28), 0)
            addView(TextView(context).apply {
                text = "1. Was möchtest du festlegen?"
                textStyleBold()
                setPadding(0, 0, 0, dp(6))
            })
            addView(spinner)
            addView(bezeichnung)
            addView(TextView(context).apply {
                text = "Die Roh-TRA-Geometrie wird nicht verändert. Es wird nur ein betrieblicher Abschnitt darübergelegt."
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
            })
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Strecken / Gleise / Weichen bearbeiten")
            .setView(layout)
            .setNegativeButton("Abbrechen") { _, _ -> stoppeAbschnittGpsWarmup() }
            .setPositiveButton("Weiter", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val typ = typWerte[spinner.selectedItemPosition]
                val roh = bezeichnung.text?.toString()?.trim().orEmpty()
                val entwurf = erstelleAbschnittEntwurf(typ, roh)
                if (entwurf == null) {
                    Toast.makeText(context, "Bitte eine gültige Bezeichnung eingeben.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                zeigeAbschnittStartSchritt(entwurf)
            }
        }
        dialog.show()
    }

    private fun erstelleAbschnittEntwurf(typ: String, roh: String): ManuellerAbschnittEntwurf? {
        val sauber = roh.trim()
        if (sauber.isBlank()) return null
        return when (typ) {
            TrassenAchseEntity.ACHSTYP_GLEIS -> {
                val gleis = Regex("(?i)(?:GL(?:EIS)?\\s*)?(\\d+[A-Z]?)").find(sauber)?.groupValues?.getOrNull(1) ?: return null
                ManuellerAbschnittEntwurf(achsTyp = typ, bezeichnung = "Gl $gleis", gleis = gleis)
            }
            TrassenAchseEntity.ACHSTYP_STRECKE -> {
                val m = Regex("(?i)(?:STR\\s*)?([1-9]\\d{3})(?:[-_/ ]?([012]))?").find(sauber) ?: return null
                val strecke = m.groupValues[1]
                val richtung = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                ManuellerAbschnittEntwurf(
                    achsTyp = typ,
                    bezeichnung = "Str $strecke${richtung?.let { "-$it" }.orEmpty()}",
                    strecke = strecke,
                    richtung = richtung
                )
            }
            TrassenAchseEntity.ACHSTYP_WEICHE -> ManuellerAbschnittEntwurf(achsTyp = typ, bezeichnung = sauber)
            TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> ManuellerAbschnittEntwurf(achsTyp = typ, bezeichnung = sauber)
            else -> null
        }
    }

    private fun zeigeAbschnittStartSchritt(entwurf: ManuellerAbschnittEntwurf) {
        starteAbschnittGpsWarmup()
        val context = requireContext()
        val kmEdit = EditText(context).apply {
            hint = "KM am Start optional, z. B. 23,987"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(
                entwurf.kmAnkerMeter?.takeIf {
                    (entwurf.stationStart != null && entwurf.kmAnkerStation != null && kotlin.math.abs(entwurf.kmAnkerStation - entwurf.stationStart) < 0.01) ||
                        (entwurf.stationStart == null && entwurf.kmAnkerStation == null)
                }?.let(::formatKmMeter).orEmpty()
            )
        }
        val laengeEdit = EditText(context).apply {
            hint = "Bekannte Gesamtlänge optional, z. B. 754 m"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(entwurf.bekannteLaengeM?.let(::format1).orEmpty())
        }
        val inhalt = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(TextView(context).apply {
                text = buildString {
                    appendLine("2. Start von ${entwurf.bezeichnung}")
                    appendLine("Der KM am Start darf unbekannt sein. Punkt und Entwurf werden sofort dauerhaft gespeichert.")
                    append(abschnittGnssStatus.alsText())
                }
                textSize = 13f
                setPadding(0, 0, 0, dp(10))
            })
            addView(kmEdit)
            addView(laengeEdit)
        }
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        val btnGps = MaterialButton(context).apply { text = "Start jetzt per GPS setzen"; isAllCaps = false }
        val btnGrafik = MaterialButton(context).apply { text = "Start in Grafik wählen"; isAllCaps = false }
        val btnSpaeter = MaterialButton(context).apply { text = "Start später setzen · Entwurf speichern"; isAllCaps = false }
        inhalt.addView(btnGps)
        inhalt.addView(btnGrafik)
        inhalt.addView(btnSpaeter)

        fun mitEingaben(): ManuellerAbschnittEntwurf? {
            val kmText = kmEdit.text?.toString()?.trim().orEmpty()
            val km = kmText.takeIf { it.isNotBlank() }?.let(::parseKmMeter)
            if (kmText.isNotBlank() && km == null) {
                Toast.makeText(context, "Bitte einen gültigen km eingeben.", Toast.LENGTH_LONG).show()
                return null
            }
            val laengeText = laengeEdit.text?.toString()?.trim().orEmpty()
            val laenge = laengeText.replace(',', '.').toDoubleOrNull()
            if (laengeText.isNotBlank() && (laenge == null || laenge <= 0.0)) {
                Toast.makeText(context, "Bitte eine gültige Länge in Metern eingeben.", Toast.LENGTH_LONG).show()
                return null
            }
            return if (km != null) {
                entwurf.copy(
                    bekannteLaengeM = laenge,
                    kmAnkerStation = null,
                    kmAnkerMeter = km,
                    kmPunkt1Meter = km
                )
            } else {
                val nurVorgemerkterStartKm = entwurf.stationStart == null && entwurf.kmAnkerStation == null
                entwurf.copy(
                    bekannteLaengeM = laenge,
                    kmAnkerMeter = if (nurVorgemerkterStartKm) null else entwurf.kmAnkerMeter,
                    kmPunkt1Meter = if (nurVorgemerkterStartKm) null else entwurf.kmPunkt1Meter
                )
            }
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("${entwurf.bezeichnung} · Start")
            .setView(android.widget.ScrollView(context).apply { addView(inhalt) })
            .setNegativeButton("Abbrechen") { _, _ -> stoppeAbschnittGpsWarmup() }
            .create()
        btnGps.setOnClickListener {
            val basis = mitEingaben() ?: return@setOnClickListener
            dialog.dismiss()
            sammleAbschnittGpsMessung("Startpunkt wird aufgenommen") { messung ->
                fun uebernehmen(kandidat: TrassenNaheBerechner.Ergebnis, achse: TrassenAchseEntity) {
                    val km = basis.kmAnkerMeter
                    nachStartGesetzt(
                        basis.copy(
                            achseId = achse.id,
                            achseName = achse.name,
                            quelleDatei = achse.quelleDatei,
                            dateiSignatur = achse.dateiSignatur,
                            stationStart = kandidat.station,
                            startQuelle = "GPS",
                            kmAnkerStation = if (km != null && basis.kmAnkerStation == null) kandidat.station else basis.kmAnkerStation,
                            kmPunkt1Meter = if (km != null && basis.kmAnkerStation == null) km else basis.kmPunkt1Meter,
                            startGpsLat = messung.latitude,
                            startGpsLon = messung.longitude,
                            startGpsAccuracyM = messung.accuracyM,
                            startGpsStreuungM = messung.streuungM,
                            startAchsabstandM = kandidat.abstandM
                        )
                    )
                }
                if (basis.achseId != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val kandidat = withContext(Dispatchers.IO) { kandidatAufFesterAchse(basis.achseId, messung) }
                        val achse = withContext(Dispatchers.IO) { ServiceLocator.trassenDao.achseMitId(basis.achseId) }
                        if (kandidat == null || achse == null || kandidat.abstandM > 20.0) {
                            Toast.makeText(context, "Startpunkt liegt nicht sicher auf der bereits gewählten TRA-Achse.", Toast.LENGTH_LONG).show()
                            zeigeAbschnittStartSchritt(basis)
                        } else uebernehmen(kandidat, achse)
                    }
                } else {
                    waehleAchseFuerMessung(
                        messung = messung,
                        titel = "Welche Achse gehört zu ${basis.bezeichnung}?",
                        onErneut = { zeigeAbschnittStartSchritt(basis) },
                        onAuswahl = ::uebernehmen
                    )
                }
            }
        }
        btnGrafik.setOnClickListener {
            val basis = mitEingaben() ?: return@setOnClickListener
            dialog.dismiss()
            zeigeGrafischenPunktWaehler("${basis.bezeichnung} · Start in Grafik wählen", basis.achseId) { auswahl ->
                val km = basis.kmAnkerMeter
                nachStartGesetzt(
                    basis.copy(
                        achseId = auswahl.achse.achseId,
                        achseName = auswahl.achse.name,
                        quelleDatei = auswahl.achse.quelleDatei,
                        dateiSignatur = auswahl.achse.dateiSignatur,
                        stationStart = auswahl.station,
                        startQuelle = "GRAFIK",
                        kmAnkerStation = if (km != null) auswahl.station else basis.kmAnkerStation,
                        kmPunkt1Meter = if (km != null) km else basis.kmPunkt1Meter,
                        startAchsabstandM = 0.0
                    )
                )
            }
        }
        btnSpaeter.setOnClickListener {
            val basis = mitEingaben() ?: return@setOnClickListener
            dialog.dismiss()
            speichereEntwurfUndWeiter(
                basis.copy(startQuelle = "SPAETER", bearbeitungsStatus = TrassenManuellerAbschnittEntity.STATUS_OFFEN),
                "${basis.bezeichnung}: Entwurf gespeichert. Start kann später gesetzt werden."
            ) { gespeichert -> zeigeAbschnittBearbeitungsMenue(gespeichert) }
        }
        dialog.show()
    }

    private fun nachStartGesetzt(entwurf: ManuellerAbschnittEntwurf) {
        val referenz = entwurf.kmAnkerStation?.takeIf { anker ->
            entwurf.stationStart == null || kotlin.math.abs(anker - entwurf.stationStart) >= 0.01
        } ?: entwurf.stationEnde
        val kmVorzeichen = entwurf.kmStationVorzeichen ?: ManuelleAbschnittKmLogik.stationVorzeichenAusAbschnittsRichtung(
            entwurf.stationStart, referenz, entwurf.kmRichtung
        )
        val kmRichtung = entwurf.kmRichtung ?: ManuelleAbschnittKmLogik.kmRichtungAusStationVorzeichen(
            entwurf.stationStart, referenz, kmVorzeichen
        )
        val abgeleitet = entwurf.copy(
            kmStationVorzeichen = kmVorzeichen,
            kmRichtung = kmRichtung
        )
        speichereEntwurfUndWeiter(abgeleitet, "Startpunkt dauerhaft gespeichert.") { gespeichert ->
            // Wenn der KM am Start bekannt war, den ersten Anker ebenfalls als eigenen Datensatz sichern.
            val ankerStation = gespeichert.kmAnkerStation
            val ankerKm = gespeichert.kmAnkerMeter
            if (gespeichert.id > 0L && ankerStation != null && ankerKm != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val vorhandeneAnker = ServiceLocator.trassenDao.abschnittKmPunkte(gespeichert.id)
                            .filter { it.typ == TrassenAbschnittKmPunktEntity.TYP_ANKER }
                        val ankerIstStart = gespeichert.stationStart != null &&
                            gespeichert.kmPunkt1Meter != null &&
                            kotlin.math.abs(ankerStation - gespeichert.stationStart) < 0.01
                        if (ankerIstStart || vorhandeneAnker.isEmpty()) {
                            ServiceLocator.trassenDao.abschnittKmPunkteNachTypLoeschen(
                                gespeichert.id,
                                TrassenAbschnittKmPunktEntity.TYP_ANKER
                            )
                            ServiceLocator.trassenDao.abschnittKmPunktErsetzen(
                                TrassenAbschnittKmPunktEntity(
                                    abschnittId = gespeichert.id,
                                    typ = TrassenAbschnittKmPunktEntity.TYP_ANKER,
                                    station = ankerStation,
                                    kmMeter = ankerKm,
                                    gpsLat = gespeichert.startGpsLat,
                                    gpsLon = gespeichert.startGpsLon,
                                    gpsAccuracyM = gespeichert.startGpsAccuracyM,
                                    gpsStreuungM = gespeichert.startGpsStreuungM,
                                    achsabstandM = gespeichert.startAchsabstandM
                                )
                            )
                        }
                    }
                    zeigeAbschnittBearbeitungsMenue(gespeichert)
                }
            } else {
                zeigeAbschnittBearbeitungsMenue(gespeichert)
            }
        }
    }

    private fun sammleAbschnittGpsMessung(titel: String, onFertig: (AbschnittGpsMessung) -> Unit) {
        starteAbschnittGpsWarmup()
        val context = requireContext()
        val info = TextView(context).apply {
            textSize = 14f
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val progress = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 20
            progress = 0
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(info)
            addView(progress)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titel)
            .setView(layout)
            .setCancelable(false)
            .create()
        dialog.show()

        val start = System.currentTimeMillis()
        viewLifecycleOwner.lifecycleScope.launch {
            repeat(20) { sekunde ->
                delay(1000L)
                val seitStart = abschnittGpsFixes.filter { it.time >= start }
                val letzte = seitStart.lastOrNull()
                progress.progress = sekunde + 1
                info.text = buildString {
                    appendLine("${sekunde + 1}/20 s · aktuelle Fixes: ${seitStart.size}")
                    appendLine(abschnittGnssStatus.alsText())
                    if (letzte != null && letzte.hasAccuracy()) append("Aktuelle Genauigkeit: ±${format1(letzte.accuracy.toDouble())} m")
                }.trimEnd()
            }
            val fixes = abschnittGpsFixes.filter { it.time >= start }
            dialog.dismiss()
            val messung = fixes.toAbschnittGpsMessung(20_000L)
            if (messung == null || messung.fixCount < 4) {
                Toast.makeText(context, "Zu wenige aktuelle GPS-Fixes. Bitte unter freiem Himmel erneut aufnehmen.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val qualitaetsWert = listOfNotNull(messung.accuracyM?.toDouble(), messung.streuungM).maxOrNull()
            when {
                qualitaetsWert != null && qualitaetsWert > 15.0 -> {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("GPS zu ungenau")
                        .setMessage(messung.alsQualitaetsText() + "\n\nÜber 15 m wird der Punkt nicht übernommen. Bitte freie Sicht zum Himmel abwarten und neu messen.")
                        .setPositiveButton("Erneut messen") { _, _ -> sammleAbschnittGpsMessung(titel, onFertig) }
                        .show()
                }
                qualitaetsWert != null && qualitaetsWert > 8.0 -> {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("GPS-Qualität nur mäßig")
                        .setMessage(messung.alsQualitaetsText() + "\n\nBesser erneut messen oder trotzdem bewusst übernehmen.")
                        .setNegativeButton("Erneut messen") { _, _ -> sammleAbschnittGpsMessung(titel, onFertig) }
                        .setPositiveButton("Trotzdem verwenden") { _, _ -> onFertig(messung) }
                        .show()
                }
                else -> onFertig(messung)
            }
        }
    }

    private fun List<Location>.toAbschnittGpsMessung(durationMs: Long): AbschnittGpsMessung? {
        if (isEmpty()) return null
        val plausible = filter { !it.hasAccuracy() || it.accuracy <= 30f }.ifEmpty { this }
        val beste = plausible.sortedBy { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
            .take(LIVE_GPS_BESTE_FIXES)
            .ifEmpty { plausible }
        val gewichte = beste.map {
            val acc = if (it.hasAccuracy()) it.accuracy.coerceAtLeast(1f).toDouble() else 10.0
            1.0 / (acc * acc)
        }
        val sum = gewichte.sum().takeIf { it > 0.0 } ?: return null
        val lat = beste.zip(gewichte).sumOf { (loc, w) -> loc.latitude * w } / sum
        val lon = beste.zip(gewichte).sumOf { (loc, w) -> loc.longitude * w } / sum
        val accuracies = beste.mapNotNull { if (it.hasAccuracy()) it.accuracy else null }.sorted()
        val streuung = beste.maxOfOrNull { distanzMeter(lat, lon, it.latitude, it.longitude) }
        return AbschnittGpsMessung(
            latitude = lat,
            longitude = lon,
            accuracyM = accuracies.medianFloatOrNull(),
            bestAccuracyM = accuracies.firstOrNull(),
            streuungM = streuung,
            fixCount = size,
            usedFixCount = beste.size,
            durationMs = durationMs
        )
    }

    private fun waehleAchseFuerMessung(
        messung: AbschnittGpsMessung,
        titel: String,
        onErneut: () -> Unit,
        onAuswahl: (TrassenNaheBerechner.Ergebnis, TrassenAchseEntity) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (kandidaten, achsenById) = withContext(Dispatchers.IO) {
                ServiceLocator.trassenRepository.findeNaechsteTrassenKandidaten(
                    messung.latitude, messung.longitude, 80.0, 8, false
                ) to ServiceLocator.trassenDao.alleAchsen().associateBy { it.id }
            }
            val auswahl = kandidaten.mapNotNull { k -> achsenById[k.achseId]?.let { k to it } }
            if (auswahl.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Keine TRA-Achse gefunden")
                    .setMessage(messung.alsQualitaetsText() + "\n\nBitte erneut messen oder den Punkt in der Grafik wählen.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Erneut messen") { _, _ -> onErneut() }
                    .show()
                return@launch
            }
            val context = requireContext()
            val inhalt = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(TextView(context).apply {
                    text = messung.alsQualitaetsText()
                    textSize = 14f
                    setPadding(0, 0, 0, dp(10))
                })
            }
            lateinit var dialog: androidx.appcompat.app.AlertDialog
            auswahl.forEachIndexed { index, (kandidat, achse) ->
                inhalt.addView(MaterialButton(context).apply {
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    text = "${index + 1}. ${achse.toGrafikLabel()}\n${format1(kandidat.abstandM)} m zur Achse · Station ${format1(kandidat.station)} m"
                    setOnClickListener { dialog.dismiss(); onAuswahl(kandidat, achse) }
                })
            }
            dialog = MaterialAlertDialogBuilder(context)
                .setTitle(titel)
                .setView(android.widget.ScrollView(context).apply { addView(inhalt) })
                .setNegativeButton("Erneut messen") { _, _ -> onErneut() }
                .create()
            dialog.show()
        }
    }

    private fun zeigeAuswahlMitHinweis(
        titel: String,
        hinweis: String,
        optionen: List<String>,
        negativText: String = "Abbrechen",
        onNegativ: () -> Unit = {},
        onAuswahl: (Int) -> Unit
    ) {
        val context = requireContext()
        val inhalt = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(TextView(context).apply {
                text = hinweis
                textSize = 14f
                setPadding(0, 0, 0, dp(12))
            })
        }
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        optionen.forEachIndexed { index, option ->
            inhalt.addView(MaterialButton(context).apply {
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                text = option
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener { dialog.dismiss(); onAuswahl(index) }
            })
        }
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titel)
            .setView(android.widget.ScrollView(context).apply { addView(inhalt) })
            .setNegativeButton(negativText) { _, _ -> onNegativ() }
            .create()
        dialog.show()
    }

    private fun zeigeAbschnittBearbeitungsMenue(entwurf: ManuellerAbschnittEntwurf) {
        starteAbschnittGpsWarmup()
        viewLifecycleOwner.lifecycleScope.launch {
            val aktuell = if (entwurf.id > 0L) {
                withContext(Dispatchers.IO) { ServiceLocator.trassenDao.manuellerAbschnittMitId(entwurf.id) }?.toEntwurf() ?: entwurf
            } else entwurf
            val kmPunkte = if (aktuell.id > 0L) withContext(Dispatchers.IO) { ServiceLocator.trassenDao.abschnittKmPunkte(aktuell.id) } else emptyList()
            val optionen = mutableListOf<String>()
            val aktionen = mutableListOf<() -> Unit>()

            optionen += "Bezeichnung ändern · ${aktuell.bezeichnung}"
            aktionen += { zeigeAbschnittBezeichnungAendern(aktuell) }

            if (aktuell.kmAnkerStation == null || aktuell.kmAnkerMeter == null) {
                optionen += "KM-Anker setzen · Vermarkung / GPS"
                aktionen += { zeigeKmPunktSetzen(aktuell, TrassenAbschnittKmPunktEntity.TYP_ANKER, false) }
            } else {
                optionen += "KM-Anker ändern · aktuell km ${formatKmMeter(aktuell.kmAnkerMeter)}"
                aktionen += { zeigeKmPunktSetzen(aktuell, TrassenAbschnittKmPunktEntity.TYP_ANKER, false) }
                if (aktuell.kmStationVorzeichen == null) {
                    optionen += "KM-Richtung festlegen / automatisch bestimmen"
                    aktionen += { zeigeRichtungsMenue(aktuell) }
                } else {
                    optionen += "KM-Richtung prüfen / ändern"
                    aktionen += { zeigeRichtungsMenue(aktuell) }
                }
                optionen += "Weiteren Kontrollanker setzen"
                aktionen += { zeigeKmPunktSetzen(aktuell, TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER, false) }
                optionen += "KM-Sprung setzen"
                aktionen += { zeigeKmSprungSetzen(aktuell) }
                if (kmPunkte.any { it.typ != TrassenAbschnittKmPunktEntity.TYP_ANKER }) {
                    optionen += "Kontrollanker / KM-Sprünge verwalten"
                    aktionen += { zeigeKmPunkteVerwalten(aktuell) }
                }
            }

            optionen += "Bereich / Grenzen festlegen"
            aktionen += { zeigeAbschnittGrenzenMenue(aktuell) }

            optionen += "Zusammenfassung / speichern"
            aktionen += { zeigeAbschnittZusammenfassung(aktuell) }

            zeigeAuswahlMitHinweis(
                titel = "${aktuell.bezeichnung} · Abschnitt bearbeiten",
                hinweis = abschnittStatusText(aktuell, kmPunkte),
                optionen = optionen,
                negativText = "Schließen",
                onNegativ = { stoppeAbschnittGpsWarmup() }
            ) { index -> aktionen[index].invoke() }
        }
    }

    private fun zeigeAbschnittBezeichnungAendern(entwurf: ManuellerAbschnittEntwurf) {
        val context = requireContext()
        val input = EditText(context).apply {
            setSingleLine(true)
            setText(if (entwurf.achsTyp == TrassenAchseEntity.ACHSTYP_GLEIS) entwurf.gleis ?: entwurf.bezeichnung.removePrefix("Gl ") else entwurf.bezeichnung.removePrefix("Str "))
            selectAll()
            hint = if (entwurf.achsTyp == TrassenAchseEntity.ACHSTYP_GLEIS) "Gleis, z. B. 28" else "Strecke / Abschnitt"
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Nur diesen Abschnitt umbenennen")
            .setMessage("Die technische Roh-TRA bleibt unverändert. Andere Abschnitte auf derselben TRA werden nicht geändert.")
            .setView(input)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val basis = erstelleAbschnittEntwurf(entwurf.achsTyp, input.text?.toString().orEmpty())
                if (basis == null) {
                    Toast.makeText(context, "Bitte eine gültige Bezeichnung eingeben.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val aktualisiert = entwurf.copy(
                    bezeichnung = basis.bezeichnung,
                    strecke = basis.strecke,
                    gleis = basis.gleis,
                    richtung = basis.richtung
                )
                dialog.dismiss()
                speichereEntwurfUndWeiter(aktualisiert, "Abschnitt umbenannt.") { zeigeAbschnittBearbeitungsMenue(it) }
            }
        }
        dialog.show()
    }

    private fun abschnittStatusText(entwurf: ManuellerAbschnittEntwurf, kmPunkte: List<TrassenAbschnittKmPunktEntity>): String = buildString {
        val grenzen = when {
            entwurf.stationStart != null && entwurf.stationEnde != null -> "✓ Bereich: Grenze 1 + Grenze 2 gesetzt"
            entwurf.stationStart != null -> "○ Grenze 1 gesetzt · Grenze 2 offen"
            entwurf.stationEnde != null -> "○ Grenze 2 gesetzt · Grenze 1 offen"
            else -> "○ Bereich noch nicht begrenzt · bleibt ROT, bis beide Grenzen gesetzt sind"
        }
        appendLine(grenzen)
        if (entwurf.kmAnkerStation != null && entwurf.kmAnkerMeter != null) {
            appendLine("✓ KM-Anker: km ${formatKmMeter(entwurf.kmAnkerMeter)}")
        } else appendLine("○ Noch kein KM-Anker")
        appendLine("KM-Richtung: ${when (entwurf.kmStationVorzeichen) { 1 -> "steigt mit TRA-Station"; -1 -> "fällt mit TRA-Station"; else -> "noch offen" }}")
        val kontrollanker = kmPunkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER }
        val hauptanker = if (entwurf.kmAnkerStation != null && entwurf.kmAnkerMeter != null) 1 else 0
        append("KM-Anker gesamt: ${hauptanker + kontrollanker} · Hauptanker: $hauptanker · Kontrollanker: $kontrollanker · KM-Sprünge: ${kmPunkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG }}")
    }

    private fun setzeKmRichtung(entwurf: ManuellerAbschnittEntwurf, richtung: String) {
        val vorzeichen = when (richtung) {
            TrassenManuellerAbschnittEntity.KM_RICHTUNG_STEIGEND -> 1
            TrassenManuellerAbschnittEntity.KM_RICHTUNG_FALLEND -> -1
            else -> null
        }
        val aktualisiert = entwurf.copy(kmRichtung = richtung, kmStationVorzeichen = vorzeichen)
        speichereEntwurfUndWeiter(aktualisiert, "KM-Richtung gespeichert.") { zeigeAbschnittBearbeitungsMenue(it) }
    }

    private fun zeigeRichtungsMenue(entwurf: ManuellerAbschnittEntwurf) {
        val optionen = listOf(
            "KM steigt mit TRA-Station",
            "KM fällt mit TRA-Station",
            "Durch zweiten bekannten KM-Anker automatisch bestimmen"
        )
        zeigeAuswahlMitHinweis(
            "KM-Richtung",
            "Die Richtung bezieht sich auf die technische TRA-Station und ist unabhängig von Grenze 1/2. Ein zweiter echter KM-Anker kann die Richtung automatisch bestimmen und prüfen.",
            optionen
        ) { index ->
            when (index) {
                0 -> setzeKmRichtung(entwurf, TrassenManuellerAbschnittEntity.KM_RICHTUNG_STEIGEND)
                1 -> setzeKmRichtung(entwurf, TrassenManuellerAbschnittEntity.KM_RICHTUNG_FALLEND)
                2 -> zeigeKmPunktSetzen(entwurf, TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER, true)
            }
        }
    }

    private fun zeigeAbschnittGrenzenMenue(entwurf: ManuellerAbschnittEntwurf) {
        val optionen = mutableListOf<String>()
        val aktionen = mutableListOf<() -> Unit>()
        optionen += if (entwurf.stationStart == null) "Grenze 1 setzen" else "Grenze 1 ändern · Station ${format1(entwurf.stationStart)} m"
        aktionen += { zeigeGrenzeSetzen(entwurf, true) }
        optionen += if (entwurf.stationEnde == null) "Grenze 2 setzen" else "Grenze 2 ändern · Station ${format1(entwurf.stationEnde)} m"
        aktionen += { zeigeGrenzeSetzen(entwurf, false) }
        if (entwurf.stationStart != null) {
            optionen += "Grenze 2 über bekannte Länge ab Grenze 1"
            aktionen += { zeigeGrenzePerLaenge(entwurf, basisIstGrenze1 = true) }
        }
        if (entwurf.stationEnde != null) {
            optionen += "Grenze 1 über bekannte Länge ab Grenze 2"
            aktionen += { zeigeGrenzePerLaenge(entwurf, basisIstGrenze1 = false) }
        }
        zeigeAuswahlMitHinweis(
            titel = "${entwurf.bezeichnung} · Bereich / Grenzen",
            hinweis = "Ohne zwei Grenzen bleibt der Entwurf offen und ROT. Er wird nicht als gültiger KM-Abschnitt auf die gesamte Roh-TRA gelegt. Erst Grenze 1 + Grenze 2 legen den gültigen Bereich fest.",
            optionen = optionen,
            negativText = "Zurück",
            onNegativ = { zeigeAbschnittBearbeitungsMenue(entwurf) }
        ) { index -> aktionen[index].invoke() }
    }

    private fun zeigeGrenzeSetzen(entwurf: ManuellerAbschnittEntwurf, erste: Boolean) {
        val nr = if (erste) 1 else 2
        zeigeAuswahlMitHinweis(
            titel = "${entwurf.bezeichnung} · Grenze $nr",
            hinweis = "Die Grenze wird auf der fest gewählten TRA gespeichert. Andere eingeblendete Achsen dienen nur der Orientierung.",
            optionen = listOf("Vermarkungspunkt auswählen", "Jetzt per GPS setzen", "Später setzen"),
            negativText = "Zurück",
            onNegativ = { zeigeAbschnittGrenzenMenue(entwurf) }
        ) { index ->
            when (index) {
                0 -> zeigeGrenzeVermarkung(entwurf, erste)
                1 -> zeigeGrenzeGps(entwurf, erste)
                2 -> zeigeAbschnittGrenzenMenue(entwurf)
            }
        }
    }

    private fun zeigeGrenzeVermarkung(entwurf: ManuellerAbschnittEntwurf, erste: Boolean) {
        val zielId = entwurf.achseId
        if (zielId == null) {
            Toast.makeText(requireContext(), "Für den Abschnitt ist keine feste TRA gespeichert.", Toast.LENGTH_LONG).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val (rawAlle, festpunkte) = withContext(Dispatchers.IO) { ladeRohGrafikAchsen() to ladeGrafikVermessungspunkte() }
            val zielRaw = rawAlle.firstOrNull { it.achseId == zielId }
            if (zielRaw == null) {
                Toast.makeText(requireContext(), "Die feste TRA ist nicht mehr vorhanden.", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (festpunkte.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Keine Vermarkungspunkte")
                    .setMessage("Keine Fest-, Mast- oder Absteckpunkte vorhanden. Die Grenze kann per GPS gesetzt werden.")
                    .setPositiveButton("GPS") { _, _ -> zeigeGrenzeGps(entwurf, erste) }
                    .setNegativeButton("Zurück") { _, _ -> zeigeAbschnittGrenzenMenue(entwurf) }
                    .show()
                return@launch
            }
            val context = requireContext()
            val darstellung = rawAlle.map { a -> if (a.achseId == zielId) a.copy(label = entwurf.bezeichnung) else a }
            val sichtbar = mutableSetOf(zielId)
            val sichtbarePunktArten = festpunkte.map { it.art }.toMutableSet()
            val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val kopf = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(2), dp(4), dp(2)) }
            val zurueck = MaterialButton(context).apply { text = "Zurück"; minWidth = 0; minimumWidth = 0 }
            val titel = TextView(context).apply {
                text = "${entwurf.bezeichnung} · Grenze ${if (erste) 1 else 2}"
                textSize = 17f; textStyleBold(); gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val anzeige = MaterialButton(context).apply { text = "Anzeige"; minWidth = 0; minimumWidth = 0 }
            kopf.addView(zurueck); kopf.addView(titel); kopf.addView(anzeige); container.addView(kopf)
            container.addView(TextView(context).apply {
                text = "Vermarkungspunkt antippen. Er wird rechtwinklig auf ${entwurf.bezeichnung} projiziert. Weitere Achsen können über Anzeige nur zur Orientierung eingeblendet werden."
                textSize = 12f; setPadding(dp(8), 0, dp(8), dp(4))
            })
            val grafik = TrassenGrafikView(context).apply {
                setAchsen(darstellung); setFestpunkte(festpunkte); setFestpunkteSichtbar(true)
                setSichtbareTypen(darstellung.map { it.achsTyp }.toSet()); setSichtbareAchseIds(sichtbar.toSet())
                setSichtbarePunktArten(sichtbarePunktArten.toSet()); setAktiveAchseId(zielId); setLegendeSichtbar(false)
            }
            container.addView(grafik, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            lateinit var dialog: android.app.Dialog

            fun zeigeAnzeige() {
                val liste = darstellung.sortedWith(compareBy<TrassenGrafikAchse> { it.label }.thenBy { it.name })
                val labels = liste.map { if (it.achseId == zielId) "✓ Ziel: ${entwurf.bezeichnung}" else it.toGrafikAuswahlText(liste) }.toTypedArray()
                val temp = sichtbar.toMutableSet()
                val checked = BooleanArray(liste.size) { liste[it].achseId in temp }
                MaterialAlertDialogBuilder(context)
                    .setTitle("Anzeige · Orientierung")
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                        val id = liste[which].achseId
                        if (id == zielId) temp.add(id) else if (isChecked) temp.add(id) else temp.remove(id)
                    }
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Übernehmen") { _, _ ->
                        temp.add(zielId); sichtbar.clear(); sichtbar.addAll(temp)
                        grafik.setSichtbareAchseIds(sichtbar.toSet())
                    }
                    .show()
            }

            fun punktUebernehmen(punkt: TrassenGrafikFestpunkt) {
                val projektion = projiziereVermessungspunktAufAchse(punkt, zielRaw)
                if (projektion == null) {
                    Toast.makeText(context, "Punkt konnte nicht auf die Ziel-TRA projiziert werden.", Toast.LENGTH_LONG).show(); return
                }
                grafik.setAnkerProjektion(TrassenGrafikProjektion(punkt.x, punkt.y, projektion.projX, projektion.projY))
                val msg = "${punkt.label.ifBlank { punkt.punktId ?: "Vermessungspunkt" }}\nStation ${format1(projektion.station)} m · ${format1(projektion.abstandM)} m ${projektion.seite.alsSeiteText()}\n\nAls Grenze ${if (erste) 1 else 2} übernehmen?"
                MaterialAlertDialogBuilder(context)
                    .setTitle("Grenze übernehmen")
                    .setMessage(msg)
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Übernehmen") { _, _ ->
                        dialog.dismiss()
                        speichereGrenze(entwurf, erste, projektion.station, "VERMARKUNG")
                    }
                    .show()
            }

            grafik.setOnFestpunkteInfoRequested { kandidaten ->
                val eindeutig = kandidaten.distinctBy { p ->
                    val xCm = kotlin.math.round(p.x * 100f).toInt(); val yCm = kotlin.math.round(p.y * 100f).toInt()
                    "${p.label.ifBlank { p.punktId ?: p.art }.trim().uppercase(Locale.GERMANY)}|$xCm|$yCm"
                }
                when {
                    eindeutig.size == 1 -> punktUebernehmen(eindeutig.first())
                    eindeutig.isNotEmpty() -> MaterialAlertDialogBuilder(context)
                        .setTitle("Welchen Punkt?")
                        .setItems(eindeutig.map { it.toGrafikPunktAuswahlText() }.toTypedArray()) { _, which -> punktUebernehmen(eindeutig[which]) }
                        .setNegativeButton("Abbrechen", null).show()
                }
            }
            dialog = android.app.Dialog(context).apply { requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); setContentView(container); setCancelable(true) }
            zurueck.setOnClickListener { dialog.dismiss(); zeigeAbschnittGrenzenMenue(entwurf) }
            anzeige.setOnClickListener { zeigeAnzeige() }
            ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()); v.setPadding(bars.left, bars.top, bars.right, bars.bottom); grafik.post { grafik.fitToContent() }; insets
            }
            dialog.setOnShowListener {
                dialog.window?.let { w -> WindowCompat.setDecorFitsSystemWindows(w, false); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
                ViewCompat.requestApplyInsets(container); grafik.post { grafik.fitToContent() }
            }
            dialog.show(); dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun zeigeGrenzeGps(entwurf: ManuellerAbschnittEntwurf, erste: Boolean) {
        if (entwurf.achseId == null) {
            Toast.makeText(requireContext(), "Für den Abschnitt ist keine feste TRA gespeichert.", Toast.LENGTH_LONG).show(); return
        }
        sammleAbschnittGpsMessung("Grenze ${if (erste) 1 else 2} wird aufgenommen") { messung ->
            viewLifecycleOwner.lifecycleScope.launch {
                val kandidat = withContext(Dispatchers.IO) { kandidatAufFesterAchse(entwurf.achseId, messung) }
                if (kandidat == null || kandidat.abstandM > 20.0) {
                    Toast.makeText(requireContext(), "GPS-Punkt liegt nicht sicher auf der festgelegten TRA.", Toast.LENGTH_LONG).show()
                    zeigeGrenzeSetzen(entwurf, erste)
                } else {
                    speichereGrenze(entwurf, erste, kandidat.station, "GPS")
                }
            }
        }
    }

    private fun zeigeGrenzePerLaenge(entwurf: ManuellerAbschnittEntwurf, basisIstGrenze1: Boolean) {
        val basis = if (basisIstGrenze1) entwurf.stationStart else entwurf.stationEnde
        val zielId = entwurf.achseId
        if (basis == null || zielId == null) {
            Toast.makeText(requireContext(), "Zuerst den Bezugspunkt als Grenze setzen.", Toast.LENGTH_LONG).show(); return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val raw = withContext(Dispatchers.IO) { ladeRohGrafikAchsen().firstOrNull { it.achseId == zielId } }
            if (raw == null || raw.punkte.size < 2) {
                Toast.makeText(requireContext(), "TRA-Geometrie nicht verfügbar.", Toast.LENGTH_LONG).show(); return@launch
            }
            val edit = EditText(requireContext()).apply { hint = "Länge in Metern"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setSingleLine(true) }
            val dlg = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Bekannte Länge ab Grenze ${if (basisIstGrenze1) 1 else 2}")
                .setMessage("Die Länge wird entlang der TRA-Station gerechnet, nicht als Luftlinie.")
                .setView(edit).setNegativeButton("Abbrechen", null).setPositiveButton("Weiter", null).create()
            dlg.setOnShowListener {
                dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val laenge = edit.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                    if (laenge == null || laenge <= 0.0) { Toast.makeText(requireContext(), "Bitte gültige Länge eingeben.", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                    dlg.dismiss()
                    val rawMin = raw.punkte.minOf { it.station }; val rawMax = raw.punkte.maxOf { it.station }
                    val optionen = listOf("Richtung zum TRA-Anfang", "Richtung zum TRA-Ende")
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Richtung der Länge")
                        .setItems(optionen.toTypedArray()) { _, which ->
                            val station = if (which == 0) basis - laenge else basis + laenge
                            if (station < rawMin - 0.01 || station > rawMax + 0.01) {
                                Toast.makeText(requireContext(), "Die eingegebene Länge reicht über die vorhandene TRA hinaus.", Toast.LENGTH_LONG).show()
                            } else {
                                val ersteZiel = !basisIstGrenze1
                                speichereGrenze(entwurf.copy(bekannteLaengeM = laenge), ersteZiel, station, "LAENGE", laenge)
                            }
                        }
                        .setNegativeButton("Abbrechen", null).show()
                }
            }
            dlg.show()
        }
    }

    private fun speichereGrenze(entwurf: ManuellerAbschnittEntwurf, erste: Boolean, station: Double, quelle: String, laenge: Double? = null) {
        val aktualisiert = if (erste) {
            entwurf.copy(stationStart = station, startQuelle = quelle, bekannteLaengeM = laenge ?: entwurf.bekannteLaengeM)
        } else {
            entwurf.copy(stationEnde = station, endeQuelle = quelle, bekannteLaengeM = laenge ?: entwurf.bekannteLaengeM)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val konflikt = withContext(Dispatchers.IO) { findeAbschnittsUeberschneidung(aktualisiert) }
            if (konflikt != null) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Bereich überschneidet vorhandenen Abschnitt")
                    .setMessage("${aktualisiert.bezeichnung} würde sich mit ${konflikt.bezeichnung} überschneiden. Gemeinsame Grenzpunkte sind erlaubt, Überlappungen aber nicht.")
                    .setPositiveButton("OK") { _, _ -> zeigeAbschnittGrenzenMenue(entwurf) }
                    .show()
                return@launch
            }
            val status = if (aktualisiert.stationStart != null && aktualisiert.stationEnde != null) TrassenManuellerAbschnittEntity.STATUS_FERTIG else TrassenManuellerAbschnittEntity.STATUS_OFFEN
            val gespeichert = withContext(Dispatchers.IO) { persistiereAbschnittEntwurf(aktualisiert.copy(bearbeitungsStatus = status)) }
            Toast.makeText(requireContext(), "Grenze ${if (erste) 1 else 2} gespeichert.", Toast.LENGTH_SHORT).show()
            zeigeAbschnittGrenzenMenue(gespeichert)
        }
    }

    private suspend fun findeAbschnittsUeberschneidung(entwurf: ManuellerAbschnittEntwurf): TrassenManuellerAbschnittEntity? {
        val a = entwurf.stationStart ?: return null
        val b = entwurf.stationEnde ?: return null
        val rawId = entwurf.achseId ?: return null
        val von = minOf(a, b); val bis = maxOf(a, b)
        return ServiceLocator.trassenDao.alleManuellenAbschnitte().firstOrNull { anderer ->
            if (anderer.id == entwurf.id || anderer.aktuelleAchseId != rawId || anderer.achsTyp != entwurf.achsTyp) return@firstOrNull false
            val x = anderer.stationStart ?: return@firstOrNull false
            val y = anderer.stationEnde ?: return@firstOrNull false
            val av = minOf(x, y); val ab = maxOf(x, y)
            minOf(bis, ab) - maxOf(von, av) > 0.05
        }
    }

    private fun zeigeKmPunktSetzen(entwurf: ManuellerAbschnittEntwurf, typ: String, richtungAutomatisch: Boolean) {
        starteAbschnittGpsWarmup()
        val context = requireContext()
        val inhalt = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(TextView(context).apply {
                text = if (typ == TrassenAbschnittKmPunktEntity.TYP_ANKER) {
                    "Bekannten KM-Anker setzen. Wenn Vermessungs-/Festpunkte vorhanden sind, zuerst den Punkt in der Grafik wählen. Den echten km bestätigst du danach."
                } else {
                    "Weiteren bekannten KM-Anker setzen. Für die Richtungsprüfung sind 50–100 m Abstand empfohlen; unter 20 m wird die automatische Richtungsbestimmung abgelehnt."
                }
                textSize = 13f
                setPadding(0, 0, 0, dp(8))
            })
        }
        val btnVermarkung = MaterialButton(context).apply {
            text = "Vermarkungspunkt auswählen"
            isAllCaps = false
        }
        val btnGps = MaterialButton(context).apply {
            text = "GPS-Punkt aufnehmen"
            isAllCaps = false
        }
        inhalt.addView(btnVermarkung)
        inhalt.addView(btnGps)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (typ == TrassenAbschnittKmPunktEntity.TYP_ANKER) "Erster KM-Anker" else "Kontrollanker")
            .setView(android.widget.ScrollView(context).apply { addView(inhalt) })
            .setNegativeButton("Abbrechen", null)
            .create()

        btnVermarkung.setOnClickListener {
            dialog.dismiss()
            zeigeVermarkungsKmPunktWaehler(entwurf, typ, richtungAutomatisch)
        }

        btnGps.setOnClickListener {
            dialog.dismiss()
            sammleAbschnittGpsMessung("KM-Punkt wird aufgenommen") { messung ->
                fun uebernehmen(kandidat: TrassenNaheBerechner.Ergebnis, achse: TrassenAchseEntity) {
                    val basis = entwurf.copy(
                        achseId = achse.id,
                        achseName = achse.name,
                        quelleDatei = achse.quelleDatei,
                        dateiSignatur = achse.dateiSignatur
                    )
                    frageBekanntenKmUndVerarbeite(
                        entwurf = basis,
                        typ = typ,
                        station = kandidat.station,
                        vorgeschlagenKm = null,
                        achsabstandM = kandidat.abstandM,
                        seite = kandidat.seite,
                        punktLabel = "GPS-Punkt",
                        kmHinweis = null,
                        messung = messung,
                        richtungAutomatisch = richtungAutomatisch,
                        punktNotiz = "GPS-KM-Anker",
                        onZurueck = { zeigeKmPunktSetzen(entwurf, typ, richtungAutomatisch) }
                    )
                }

                if (entwurf.achseId == null) {
                    waehleAchseFuerMessung(
                        messung,
                        "Welche Achse gehört zum KM-Punkt?",
                        { zeigeKmPunktSetzen(entwurf, typ, richtungAutomatisch) },
                        ::uebernehmen
                    )
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val kandidat = withContext(Dispatchers.IO) { kandidatAufFesterAchse(entwurf.achseId, messung) }
                        val achse = withContext(Dispatchers.IO) { ServiceLocator.trassenDao.achseMitId(entwurf.achseId) }
                        if (kandidat == null || achse == null || kandidat.abstandM > 20.0) {
                            Toast.makeText(context, "Punkt liegt nicht sicher auf derselben TRA-Achse.", Toast.LENGTH_LONG).show()
                            zeigeKmPunktSetzen(entwurf, typ, richtungAutomatisch)
                        } else {
                            uebernehmen(kandidat, achse)
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun starteLiveAbstandAnzeige(dialog: androidx.appcompat.app.AlertDialog, liveStatus: TextView, entwurf: ManuellerAbschnittEntwurf) {
        abschnittLiveDistanzJob?.cancel()
        abschnittLiveDistanzJob = viewLifecycleOwner.lifecycleScope.launch {
            while (dialog.isShowing) {
                val letzterFix = abschnittGpsFixes.lastOrNull()
                val achseId = entwurf.achseId
                if (letzterFix == null || achseId == null) {
                    liveStatus.text = "GPS wird vorbereitet …"
                } else {
                    val kandidat = withContext(Dispatchers.IO) {
                        ServiceLocator.trassenRepository.findeNaechsteTrassenKandidaten(
                            letzterFix.latitude, letzterFix.longitude, 80.0, 20, false
                        ).firstOrNull { it.achseId == achseId }
                    }
                    if (kandidat == null || kandidat.abstandM > 20.0) {
                        liveStatus.text = "Aktuelle Position nicht sicher auf derselben Achse."
                    } else {
                        val referenz = entwurf.kmAnkerStation ?: entwurf.stationStart
                        val weg = referenz?.let { kotlin.math.abs(kandidat.station - it) }
                        val kmLive = berechneLiveKmVorlaeufig(entwurf, kandidat.station)
                        liveStatus.text = buildString {
                            if (weg != null) appendLine("Entlang der TRA-Achse: ${format1(weg)} m")
                            if (weg != null && weg < 20.0) appendLine("Noch ${format1(20.0 - weg)} m bis Mindestabstand")
                            else if (weg != null && weg < 50.0) appendLine("✓ Mindestabstand · noch ${format1(50.0 - weg)} m bis 50-m-Empfehlung")
                            else if (weg != null) appendLine("✓ Guter Abstand")
                            if (kmLive != null) appendLine("Aktueller berechneter km: ${formatKmMeter(kmLive)}")
                            append("Achsabstand: ${format1(kandidat.abstandM)} m")
                            if (letzterFix.hasAccuracy()) append(" · GPS ±${format1(letzterFix.accuracy.toDouble())} m")
                        }
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun berechneLiveKmVorlaeufig(entwurf: ManuellerAbschnittEntwurf, station: Double): Double? {
        val ankerStation = entwurf.kmAnkerStation ?: return null
        val ankerKm = entwurf.kmAnkerMeter ?: return null
        var vorzeichen = entwurf.kmStationVorzeichen
        if (vorzeichen == null && entwurf.kmRichtung != null) {
            val laufReferenz = when {
                entwurf.stationStart != null && kotlin.math.abs(ankerStation - entwurf.stationStart) >= 0.01 -> ankerStation
                kotlin.math.abs(station - ankerStation) >= 0.01 -> station
                else -> null
            }
            vorzeichen = ManuelleAbschnittKmLogik.stationVorzeichenAusAbschnittsRichtung(entwurf.stationStart ?: ankerStation, laufReferenz, entwurf.kmRichtung)
        }
        return vorzeichen?.let { ankerKm + it * (station - ankerStation) }
    }

    private suspend fun kandidatAufFesterAchse(achseId: Long?, messung: AbschnittGpsMessung): TrassenNaheBerechner.Ergebnis? {
        val id = achseId ?: return null
        return ServiceLocator.trassenRepository.findeNaechsteTrassenKandidaten(
            messung.latitude, messung.longitude, 80.0, 20, false
        ).firstOrNull { it.achseId == id }
    }

    private fun verarbeiteKmPunkt(
        entwurf: ManuellerAbschnittEntwurf,
        typ: String,
        station: Double,
        km: Double,
        messung: AbschnittGpsMessung?,
        achsabstandM: Double?,
        richtungAutomatisch: Boolean,
        punktNotiz: String? = null
    ) {
        val erster = entwurf.kmAnkerStation == null || entwurf.kmAnkerMeter == null || typ == TrassenAbschnittKmPunktEntity.TYP_ANKER
        if (erster) {
            val mitRichtung = entwurf.copy(
                kmAnkerStation = station,
                kmAnkerMeter = km,
                kmPunkt1Meter = if (entwurf.stationStart != null && kotlin.math.abs(station - entwurf.stationStart) < 0.01) km else entwurf.kmPunkt1Meter,
                kmStationVorzeichen = entwurf.kmStationVorzeichen ?: ManuelleAbschnittKmLogik.stationVorzeichenAusAbschnittsRichtung(
                    entwurf.stationStart,
                    station.takeIf { entwurf.stationStart != null && kotlin.math.abs(it - entwurf.stationStart) >= 0.01 },
                    entwurf.kmRichtung
                )
            )
            speichereEntwurfUndWeiter(mitRichtung, "Erster KM-Anker dauerhaft gespeichert.") { gespeichert ->
                speichereKmPunktDatensatz(gespeichert, TrassenAbschnittKmPunktEntity.TYP_ANKER, station, km, null, messung, achsabstandM, punktNotiz) {
                    if (gespeichert.kmStationVorzeichen == null) zeigeRichtungsMenue(gespeichert) else zeigeAbschnittBearbeitungsMenue(gespeichert)
                }
            }
            return
        }

        val aStation = entwurf.kmAnkerStation ?: return
        val aKm = entwurf.kmAnkerMeter ?: return
        val stationAbstand = kotlin.math.abs(station - aStation)
        val kmAbstand = kotlin.math.abs(km - aKm)
        if (richtungAutomatisch && stationAbstand < 20.0) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Punkte zu nah beieinander")
                .setMessage("Abstand entlang der TRA-Achse: ${format1(stationAbstand)} m. Unter 20 m wird die automatische Richtungsbestimmung nicht übernommen. 50–100 m sind empfohlen.")
                .setPositiveButton("Weitergehen") { _, _ -> zeigeKmPunktSetzen(entwurf, typ, true) }
                .show()
            return
        }
        val neuesVorzeichen = ManuelleAbschnittKmLogik.stationVorzeichenAusZweiAnkern(aStation, aKm, station, km)
        val plausibel = kotlin.math.abs(stationAbstand - kmAbstand) <= kotlin.math.max(5.0, stationAbstand * 0.10)
        if (richtungAutomatisch && !plausibel) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Möglicher KM-Sprung oder falscher Punkt")
                .setMessage("Achsweg: ${format1(stationAbstand)} m\nKM-Differenz: ${format1(kmAbstand)} m\n\nDas passt nicht zur normalen 1:1-Kilometrierung. Bitte Wert/Achse prüfen oder einen KM-Sprung setzen.")
                .setNegativeButton("Neu messen") { _, _ -> zeigeKmPunktSetzen(entwurf, typ, true) }
                .setNeutralButton("KM-Sprung setzen") { _, _ -> zeigeKmSprungSetzen(entwurf) }
                .setPositiveButton("Trotzdem als Kontrollanker") { _, _ ->
                    speichereKontrollankerUndWeiter(entwurf, station, km, messung, achsabstandM, false, neuesVorzeichen, punktNotiz)
                }
                .show()
            return
        }
        speichereKontrollankerUndWeiter(entwurf, station, km, messung, achsabstandM, richtungAutomatisch, neuesVorzeichen, punktNotiz)
    }

    private fun speichereKontrollankerUndWeiter(
        entwurf: ManuellerAbschnittEntwurf,
        station: Double,
        km: Double,
        messung: AbschnittGpsMessung?,
        achsabstandM: Double?,
        richtungAutomatisch: Boolean,
        neuesVorzeichen: Int?,
        punktNotiz: String? = null
    ) {
        val kmRichtung = if (richtungAutomatisch && neuesVorzeichen != null && entwurf.stationStart != null) {
            val laufVorzeichen = if (station >= entwurf.stationStart) 1 else -1
            if (neuesVorzeichen == laufVorzeichen) TrassenManuellerAbschnittEntity.KM_RICHTUNG_STEIGEND else TrassenManuellerAbschnittEntity.KM_RICHTUNG_FALLEND
        } else entwurf.kmRichtung
        val aktualisiert = entwurf.copy(
            kmStationVorzeichen = if (richtungAutomatisch) neuesVorzeichen ?: entwurf.kmStationVorzeichen else entwurf.kmStationVorzeichen,
            kmRichtung = kmRichtung,
            kmPunkt2Station = station,
            kmPunkt2Meter = km,
            punkt2GpsLat = messung?.latitude,
            punkt2GpsLon = messung?.longitude,
            punkt2GpsAccuracyM = messung?.accuracyM,
            punkt2GpsStreuungM = messung?.streuungM,
            punkt2AchsabstandM = achsabstandM
        )
        speichereEntwurfUndWeiter(aktualisiert, if (richtungAutomatisch) "KM-Richtung automatisch erkannt und gespeichert." else "Kontrollanker gespeichert.") { gespeichert ->
            speichereKmPunktDatensatz(gespeichert, TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER, station, km, null, messung, achsabstandM, punktNotiz) {
                zeigeKontrollankerErgebnis(gespeichert, station, km)
            }
        }
    }

    private fun zeigeKontrollankerErgebnis(entwurf: ManuellerAbschnittEntwurf, station: Double, km: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            val entity = withContext(Dispatchers.IO) { ServiceLocator.trassenDao.manuellerAbschnittMitId(entwurf.id) }
            val punkte = withContext(Dispatchers.IO) { ServiceLocator.trassenDao.abschnittKmPunkte(entwurf.id) }
            val abw = entity?.let { ManuelleAbschnittKmLogik.erwarteteAbweichungM(it, station, km, punkte) }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Kontrollanker gespeichert")
                .setMessage(buildString {
                    appendLine("Bekannt: km ${formatKmMeter(km)}")
                    if (abw != null) appendLine("Abweichung zur Berechnung: ${if (abw >= 0) "+" else ""}${format1(abw)} m")
                    append("Der Punkt bleibt dauerhaft gespeichert und kann später weiter geprüft werden.")
                })
                .setPositiveButton("Weiter") { _, _ -> zeigeAbschnittBearbeitungsMenue(entwurf) }
                .show()
        }
    }

    private fun speichereKmPunktDatensatz(
        entwurf: ManuellerAbschnittEntwurf,
        typ: String,
        station: Double,
        km: Double?,
        kmNach: Double?,
        messung: AbschnittGpsMessung?,
        achsabstandM: Double?,
        punktNotiz: String? = null,
        onFertig: () -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (typ == TrassenAbschnittKmPunktEntity.TYP_ANKER) {
                    ServiceLocator.trassenDao.abschnittKmPunkteNachTypLoeschen(
                        entwurf.id,
                        TrassenAbschnittKmPunktEntity.TYP_ANKER
                    )
                }
                ServiceLocator.trassenDao.abschnittKmPunktErsetzen(
                    TrassenAbschnittKmPunktEntity(
                        abschnittId = entwurf.id,
                        typ = typ,
                        station = station,
                        kmMeter = km,
                        kmNachSprungMeter = kmNach,
                        gpsLat = messung?.latitude,
                        gpsLon = messung?.longitude,
                        gpsAccuracyM = messung?.accuracyM,
                        gpsStreuungM = messung?.streuungM,
                        achsabstandM = achsabstandM,
                        notiz = punktNotiz
                    )
                )
            }
            onFertig()
        }
    }

    private fun zeigeKmPunkteVerwalten(entwurf: ManuellerAbschnittEntwurf) {
        viewLifecycleOwner.lifecycleScope.launch {
            val punkte = withContext(Dispatchers.IO) {
                ServiceLocator.trassenDao.abschnittKmPunkte(entwurf.id)
                    .filter { it.typ != TrassenAbschnittKmPunktEntity.TYP_ANKER }
            }
            if (punkte.isEmpty()) {
                Toast.makeText(requireContext(), "Keine zusätzlichen Kontrollanker oder KM-Sprünge vorhanden.", Toast.LENGTH_LONG).show()
                zeigeAbschnittBearbeitungsMenue(entwurf)
                return@launch
            }
            val context = requireContext()
            val inhalt = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(TextView(context).apply {
                    text = "Falsch gesetzte Kontrollanker oder KM-Sprünge können hier einzeln gelöscht werden. Der erste KM-Anker wird über „Ersten KM-Anker ändern“ ersetzt."
                    textSize = 13f
                    setPadding(0, 0, 0, dp(10))
                })
            }
            lateinit var dialog: androidx.appcompat.app.AlertDialog
            punkte.forEach { punkt ->
                val titel = when (punkt.typ) {
                    TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG -> "KM-Sprung ${punkt.kmMeter?.let(::formatKmMeter) ?: "?"} → ${punkt.kmNachSprungMeter?.let(::formatKmMeter) ?: "?"}"
                    else -> "Kontrollanker · km ${punkt.kmMeter?.let(::formatKmMeter) ?: "?"}"
                }
                inhalt.addView(MaterialButton(context).apply {
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    text = "$titel\nStation ${format1(punkt.station)} m · antippen zum Löschen"
                    setOnClickListener {
                        dialog.dismiss()
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Punkt löschen?")
                            .setMessage("$titel\nStation ${format1(punkt.station)} m\n\nNur dieser Punkt wird gelöscht. Der Abschnitt und die Roh-TRA bleiben erhalten.")
                            .setNegativeButton("Abbrechen") { _, _ -> zeigeKmPunkteVerwalten(entwurf) }
                            .setPositiveButton("Löschen") { _, _ ->
                                viewLifecycleOwner.lifecycleScope.launch {
                                    withContext(Dispatchers.IO) { ServiceLocator.trassenDao.abschnittKmPunktLoeschen(punkt.id) }
                                    Toast.makeText(context, "Punkt gelöscht.", Toast.LENGTH_SHORT).show()
                                    zeigeKmPunkteVerwalten(entwurf)
                                }
                            }
                            .show()
                    }
                })
            }
            dialog = MaterialAlertDialogBuilder(context)
                .setTitle("KM-Punkte verwalten")
                .setView(android.widget.ScrollView(context).apply { addView(inhalt) })
                .setNegativeButton("Zurück") { _, _ -> zeigeAbschnittBearbeitungsMenue(entwurf) }
                .create()
            dialog.show()
        }
    }

    private fun zeigeKmSprungSetzen(entwurf: ManuellerAbschnittEntwurf) {
        val context = requireContext()
        val vorher = EditText(context).apply { hint = "KM vor dem Sprung"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val nachher = EditText(context).apply { hint = "KM nach dem Sprung"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val inhalt = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(TextView(context).apply { text = "Setze die Sprungstelle. Der Sprung wird separat gespeichert und verzerrt nicht die lineare Berechnung vor oder nach der Stelle."; textSize = 13f })
            addView(vorher); addView(nachher)
        }
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        val btnGps = MaterialButton(context).apply { text = "Sprungstelle per GPS"; isAllCaps = false }
        val btnGrafik = MaterialButton(context).apply { text = "Sprungstelle in Grafik"; isAllCaps = false }
        inhalt.addView(btnGps); inhalt.addView(btnGrafik)
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("KM-Sprung")
            .setView(inhalt)
            .setNegativeButton("Abbrechen", null)
            .create()
        fun werte(): Pair<Double, Double>? {
            val v = parseKmMeter(vorher.text?.toString().orEmpty())
            val n = parseKmMeter(nachher.text?.toString().orEmpty())
            if (v == null || n == null) {
                Toast.makeText(context, "Bitte KM vor und nach dem Sprung eingeben.", Toast.LENGTH_LONG).show()
                return null
            }
            return v to n
        }
        btnGps.setOnClickListener {
            val (v, n) = werte() ?: return@setOnClickListener
            dialog.dismiss()
            sammleAbschnittGpsMessung("KM-Sprung wird aufgenommen") { messung ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val kandidat = withContext(Dispatchers.IO) { kandidatAufFesterAchse(entwurf.achseId, messung) }
                    if (kandidat == null || kandidat.abstandM > 20.0) {
                        Toast.makeText(context, "Sprungstelle liegt nicht sicher auf derselben TRA-Achse.", Toast.LENGTH_LONG).show()
                        zeigeKmSprungSetzen(entwurf)
                    } else {
                        speichereKmPunktDatensatz(entwurf, TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG, kandidat.station, v, n, messung, kandidat.abstandM) {
                            Toast.makeText(context, "KM-Sprung gespeichert: ${formatKmMeter(v)} → ${formatKmMeter(n)}", Toast.LENGTH_LONG).show()
                            zeigeAbschnittBearbeitungsMenue(entwurf)
                        }
                    }
                }
            }
        }
        btnGrafik.setOnClickListener {
            val (v, n) = werte() ?: return@setOnClickListener
            dialog.dismiss()
            zeigeGrafischenPunktWaehler("KM-Sprung in Grafik wählen", entwurf.achseId) { auswahl ->
                speichereKmPunktDatensatz(entwurf, TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG, auswahl.station, v, n, null, 0.0) {
                    zeigeAbschnittBearbeitungsMenue(entwurf)
                }
            }
        }
        dialog.show()
    }

    private fun zeigeBekannteLaengeEingeben(entwurf: ManuellerAbschnittEntwurf) {
        val context = requireContext()
        val edit = EditText(context).apply {
            hint = "Länge in Metern, z. B. 754"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(entwurf.bekannteLaengeM?.let(::format1).orEmpty())
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Bekannte Länge ab Start")
            .setMessage("Die Länge wird gespeichert und kann später zur Endberechnung verwendet werden.")
            .setView(edit)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val wert = edit.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                if (wert == null || wert <= 0.0) {
                    Toast.makeText(context, "Bitte eine gültige Länge eingeben.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                }
                dialog.dismiss()
                speichereEntwurfUndWeiter(entwurf.copy(bekannteLaengeM = wert), "Bekannte Länge gespeichert.") { zeigeAbschnittBearbeitungsMenue(it) }
            }
        }
        dialog.show()
    }

    private fun zeigeEndeFestlegenSchritt(entwurf: ManuellerAbschnittEntwurf) {
        val achseId = entwurf.achseId
        val start = entwurf.stationStart
        if (achseId == null || start == null) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Start fehlt")
                .setMessage("Vor dem Abschnittsende muss zuerst der Start auf einer TRA-Achse gesetzt werden.")
                .setPositiveButton("Start setzen") { _, _ -> zeigeAbschnittStartSchritt(entwurf) }
                .show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val freieEnden = withContext(Dispatchers.IO) { ermittleFreieAchsenden(entwurf) }
            val weichenZiele = withContext(Dispatchers.IO) { findeWeichenWvOrientierungen(entwurf) }
            val optionen = mutableListOf<String>()
            val aktionen = mutableListOf<() -> Unit>()

            optionen += "Jetzt per GPS setzen · hier endet ${entwurf.bezeichnung}"
            aktionen += { zeigeEndeGpsSchritt(entwurf) }
            optionen += "Später per GPS setzen · Bearbeitung offen speichern"
            aktionen += { speichereEntwurfUndWeiter(entwurf.copy(stationEnde = null, endeQuelle = "SPAETER", bearbeitungsStatus = TrassenManuellerAbschnittEntity.STATUS_OFFEN), "Ende bleibt offen und kann später gesetzt werden.") { zeigeAbschnittBearbeitungsMenue(it) } }
            optionen += "Ende in Grafik wählen"
            aktionen += {
                zeigeGrafischenPunktWaehler("${entwurf.bezeichnung} · Ende in Grafik wählen", achseId) { auswahl ->
                    endeUebernehmen(entwurf, auswahl.station, "GRAFIK")
                }
            }
            optionen += if (entwurf.bekannteLaengeM != null) "Bekannte Länge verwenden · ${format1(entwurf.bekannteLaengeM)} m" else "Bekannte Länge eingeben und verwenden"
            aktionen += { zeigeEndePerLaenge(entwurf) }
            freieEnden.forEach { ende ->
                optionen += "Bis zum eindeutigen TRA-Achsende berechnen · ${format1(kotlin.math.abs(ende - start))} m ab Start"
                aktionen += { endeUebernehmen(entwurf, ende, "FREIES_ACHSENDE") }
            }
            if (weichenZiele.isNotEmpty()) {
                optionen += "Bis Weichen-/WV-Bereich rechnen · nur Orientierung, Ende bleibt manuell"
                aktionen += { zeigeWeichenWvOrientierungsAuswahl(entwurf, weichenZiele) }
            }

            zeigeAuswahlMitHinweis(
                titel = "Wo endet ${entwurf.bezeichnung}?",
                hinweis = "Weichen und Weichenverbindungen werden niemals automatisch als exaktes Gleisende übernommen. Das echte Ende setzt du per GPS, Grafik, Länge oder eindeutigem TRA-Achsende.",
                optionen = optionen,
                onNegativ = { zeigeAbschnittBearbeitungsMenue(entwurf) }
            ) { index -> aktionen[index].invoke() }
        }
    }

    private fun zeigeEndeGpsSchritt(entwurf: ManuellerAbschnittEntwurf) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${entwurf.bezeichnung} · GPS-Endpunkt")
            .setMessage("Gehe bis zum tatsächlichen Ende und tippe dann auf „Hier endet der Abschnitt“. Es werden 20 Sekunden aktuelle GNSS-Fixes gemittelt.")
            .setNegativeButton("Später") { _, _ -> speichereEntwurfUndWeiter(entwurf.copy(endeQuelle = "SPAETER"), "Ende bleibt offen.") { zeigeAbschnittBearbeitungsMenue(it) } }
            .setPositiveButton("Hier endet der Abschnitt") { _, _ ->
                sammleAbschnittGpsMessung("Endpunkt wird aufgenommen") { messung ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val kandidat = withContext(Dispatchers.IO) { kandidatAufFesterAchse(entwurf.achseId, messung) }
                        if (kandidat == null || kandidat.abstandM > 20.0) {
                            Toast.makeText(requireContext(), "Endpunkt liegt nicht sicher auf derselben TRA-Achse.", Toast.LENGTH_LONG).show()
                            zeigeEndeGpsSchritt(entwurf)
                        } else endeUebernehmen(entwurf, kandidat.station, "GPS")
                    }
                }
            }
            .show()
    }

    private fun endeUebernehmen(entwurf: ManuellerAbschnittEntwurf, stationEnde: Double, quelle: String) {
        val kmVorzeichen = entwurf.kmStationVorzeichen ?: ManuelleAbschnittKmLogik.stationVorzeichenAusAbschnittsRichtung(
            entwurf.stationStart, stationEnde, entwurf.kmRichtung
        )
        val kmRichtung = entwurf.kmRichtung ?: ManuelleAbschnittKmLogik.kmRichtungAusStationVorzeichen(
            entwurf.stationStart, stationEnde, kmVorzeichen
        )
        val aktualisiert = entwurf.copy(
            stationEnde = stationEnde,
            endeQuelle = quelle,
            kmStationVorzeichen = kmVorzeichen,
            kmRichtung = kmRichtung,
            bearbeitungsStatus = TrassenManuellerAbschnittEntity.STATUS_FERTIG
        )
        speichereEntwurfUndWeiter(aktualisiert, "Abschnittsende dauerhaft gespeichert.") { zeigeAbschnittZusammenfassung(it) }
    }

    private fun zeigeEndePerLaenge(entwurf: ManuellerAbschnittEntwurf) {
        val context = requireContext()
        val edit = EditText(context).apply {
            hint = "z. B. 754"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(entwurf.bekannteLaengeM?.let(::format1).orEmpty())
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Länge ab Start")
            .setMessage("Die Länge wird entlang der vorhandenen TRA-Geometrie ab dem Start gemessen.")
            .setView(edit)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Weiter", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val laenge = edit.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                if (laenge == null || laenge <= 0.0) {
                    Toast.makeText(context, "Bitte eine gültige Länge in Metern eingeben.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                }
                val start = entwurf.stationStart ?: return@setOnClickListener
                val achseId = entwurf.achseId ?: return@setOnClickListener
                dialog.dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    val punkte = withContext(Dispatchers.IO) { ServiceLocator.trassenDao.samplepunkteFuerAchse(achseId) }
                    if (punkte.isEmpty()) {
                        Toast.makeText(context, "TRA-Achse konnte nicht geladen werden.", Toast.LENGTH_LONG).show(); return@launch
                    }
                    val min = punkte.minOf { it.station }
                    val max = punkte.maxOf { it.station }
                    val moeglich = buildList {
                        val plus = start + laenge
                        val minus = start - laenge
                        if (plus <= max + 0.01) add(plus.coerceAtMost(max))
                        if (minus >= min - 0.01) add(minus.coerceAtLeast(min))
                    }.distinct()
                    if (moeglich.isEmpty()) {
                        Toast.makeText(context, "Die bekannte Länge reicht über beide Enden der TRA-Achse hinaus.", Toast.LENGTH_LONG).show(); return@launch
                    }
                    if (moeglich.size == 1) {
                        endeUebernehmen(entwurf.copy(bekannteLaengeM = laenge), moeglich.first(), "LAENGE")
                    } else {
                        val optionen = moeglich.map { ziel ->
                            val richtung = if (ziel > start) "Richtung steigende TRA-Station" else "Richtung fallende TRA-Station"
                            "$richtung · Ende bei Station ${format1(ziel)} m"
                        }
                        zeigeAuswahlMitHinweis("Welche Richtung?", "Die Länge ist bekannt, aber die Laufrichtung auf der Rohachse ist noch nicht eindeutig.", optionen) { index ->
                            endeUebernehmen(entwurf.copy(bekannteLaengeM = laenge), moeglich[index], "LAENGE")
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private suspend fun ermittleFreieAchsenden(entwurf: ManuellerAbschnittEntwurf): List<Double> {
        val achseId = entwurf.achseId ?: return emptyList()
        val start = entwurf.stationStart ?: return emptyList()
        val dao = ServiceLocator.trassenDao
        val punkteDb = dao.alleSamplepunkteMitGps()
        val achsenDb = dao.alleAchsen()
        val achsPunkte = punkteDb.filter { it.achseId == achseId }.sortedBy { it.station }
        if (achsPunkte.size < 2) return emptyList()
        val achsenInfo = achsenDb.map { TrassenNaheBerechner.AchsInfo(it.id, it.name, it.achsTyp, it.strecke, it.gleis, it.richtung) }
        val punkteInfo = punkteDb.mapNotNull {
            val lat = it.lat ?: return@mapNotNull null
            val lon = it.lon ?: return@mapNotNull null
            TrassenNaheBerechner.Punkt(it.id, it.achseId, it.station, it.kmMeter, it.kmQuelle, lat, lon, it.hoeheM, it.richtungDeg)
        }
        return listOf(achsPunkte.first().station, achsPunkte.last().station)
            .filter { kotlin.math.abs(it - start) > 0.5 }
            .filter { kandidat ->
                TrassenNaheBerechner.findeAnschlussAchsenAmAchsende(
                    achseId, kandidat, achsenInfo, punkteInfo, 3.0, 5
                ).isEmpty()
            }
    }

    private suspend fun findeWeichenWvOrientierungen(entwurf: ManuellerAbschnittEntwurf): List<WeichenWvOrientierung> {
        val achseId = entwurf.achseId ?: return emptyList()
        val start = entwurf.stationStart ?: return emptyList()
        val dao = ServiceLocator.trassenDao
        val achsen = dao.alleAchsen()
        val allePunkte = dao.alleSamplepunkteMitGps()
        val roh = allePunkte.filter { it.achseId == achseId && it.lat != null && it.lon != null }
        if (roh.isEmpty()) return emptyList()
        val kandidatenAchsen = achsen.filter {
            it.id != achseId && (it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE || it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG)
        }
        val punkteByAchse = allePunkte.groupBy { it.achseId }
        return kandidatenAchsen.mapNotNull { zielAchse ->
            val zielPunkte = punkteByAchse[zielAchse.id].orEmpty().filter { it.lat != null && it.lon != null }
            if (zielPunkte.isEmpty()) return@mapNotNull null
            var besteDist = Double.POSITIVE_INFINITY
            var besteStation = Double.NaN
            for (zp in zielPunkte.asSequence().filterIndexed { i, _ -> i % 5 == 0 || i == zielPunkte.lastIndex }) {
                val zlat = zp.lat ?: continue
                val zlon = zp.lon ?: continue
                for (rp in roh.asSequence().filterIndexed { i, _ -> i % 5 == 0 || i == roh.lastIndex }) {
                    val rlat = rp.lat ?: continue
                    val rlon = rp.lon ?: continue
                    val d = distanzMeter(zlat, zlon, rlat, rlon)
                    if (d < besteDist) { besteDist = d; besteStation = rp.station }
                }
            }
            if (!besteStation.isFinite() || besteDist > 25.0 || kotlin.math.abs(besteStation - start) < 1.0) null
            else WeichenWvOrientierung(zielAchse.toGrafikLabel(), besteStation, kotlin.math.abs(besteStation - start), besteDist)
        }.sortedBy { it.distanzAbStartM }.take(12)
    }

    private fun zeigeWeichenWvOrientierungsAuswahl(entwurf: ManuellerAbschnittEntwurf, ziele: List<WeichenWvOrientierung>) {
        val optionen = ziele.map { "${it.label} · ca. ${format1(it.distanzAbStartM)} m entlang der Achse" }
        zeigeAuswahlMitHinweis(
            "Weiche/WV als Orientierung",
            "Nur Orientierung: Die Weiche/WV wird NICHT automatisch zum Ende. Am tatsächlichen Ende musst du ausdrücklich „Hier endet ${entwurf.bezeichnung}“ setzen.",
            optionen
        ) { index -> zeigeWeichenWvLiveOrientierung(entwurf, ziele[index]) }
    }

    private fun zeigeWeichenWvLiveOrientierung(entwurf: ManuellerAbschnittEntwurf, ziel: WeichenWvOrientierung) {
        val context = requireContext()
        val live = TextView(context).apply { textSize = 15f; setPadding(dp(22), dp(12), dp(22), dp(12)) }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(ziel.label)
            .setView(live)
            .setNegativeButton("Zurück") { _, _ -> zeigeEndeFestlegenSchritt(entwurf) }
            .setPositiveButton("Hier endet der Abschnitt") { _, _ -> zeigeEndeGpsSchritt(entwurf) }
            .create()
        dialog.setOnShowListener {
            abschnittLiveDistanzJob?.cancel()
            abschnittLiveDistanzJob = viewLifecycleOwner.lifecycleScope.launch {
                while (dialog.isShowing) {
                    val fix = abschnittGpsFixes.lastOrNull()
                    val kandidat = if (fix != null) withContext(Dispatchers.IO) {
                        ServiceLocator.trassenRepository.findeNaechsteTrassenKandidaten(fix.latitude, fix.longitude, 80.0, 20, false)
                            .firstOrNull { it.achseId == entwurf.achseId }
                    } else null
                    live.text = if (kandidat == null) {
                        "GPS wird vorbereitet …\n\nZielbereich liegt ca. ${format1(ziel.distanzAbStartM)} m ab Start."
                    } else {
                        val rest = kotlin.math.abs(ziel.station - kandidat.station)
                        buildString {
                            appendLine("Noch ca. ${format1(rest)} m bis zum Bereich ${ziel.label}")
                            appendLine("Seit Start: ${format1(kotlin.math.abs(kandidat.station - (entwurf.stationStart ?: kandidat.station)))} m")
                            append("Wichtig: Dort nicht automatisch speichern. Das tatsächliche Ende bestätigst du manuell per GPS.")
                        }
                    }
                    delay(1000L)
                }
            }
        }
        dialog.setOnDismissListener { abschnittLiveDistanzJob?.cancel(); abschnittLiveDistanzJob = null }
        dialog.show()
    }

    private fun zeigeAbschnittZusammenfassung(entwurf: ManuellerAbschnittEntwurf) {
        viewLifecycleOwner.lifecycleScope.launch {
            val entityDb = if (entwurf.id > 0L) withContext(Dispatchers.IO) { ServiceLocator.trassenDao.manuellerAbschnittMitId(entwurf.id) } else null
            val aktuell = entityDb?.toEntwurf() ?: entwurf
            val punkte = if (aktuell.id > 0L) withContext(Dispatchers.IO) { ServiceLocator.trassenDao.abschnittKmPunkte(aktuell.id) } else emptyList()
            val startKm = entityDb?.let { entity -> aktuell.stationStart?.let { station -> ManuelleAbschnittKmLogik.kmFuerStation(entity, station, punkte).kmMeter } }
            val endeKm = entityDb?.let { entity -> aktuell.stationEnde?.let { station -> ManuelleAbschnittKmLogik.kmFuerStation(entity, station, punkte).kmMeter } }
            val text = buildString {
                appendLine(aktuell.bezeichnung)
                appendLine("Grenze 1: ${aktuell.stationStart?.let { "Station ${format1(it)} m · ${aktuell.startQuelle ?: "?"}" } ?: "noch offen"}")
                if (startKm != null) appendLine("Berechneter KM an Grenze 1: ${formatKmMeter(startKm)}")
                aktuell.bekannteLaengeM?.let { appendLine("Bekannte Länge: ${format1(it)} m") }
                appendLine("Grenze 2: ${aktuell.stationEnde?.let { "Station ${format1(it)} m · ${aktuell.endeQuelle ?: "?"}" } ?: "noch offen"}")
                if (endeKm != null) appendLine("Berechneter KM an Grenze 2: ${formatKmMeter(endeKm)}")
                if (aktuell.kmAnkerStation != null && aktuell.kmAnkerMeter != null) appendLine("KM-Anker: km ${formatKmMeter(aktuell.kmAnkerMeter)} bei Station ${format1(aktuell.kmAnkerStation)} m")
                appendLine("KM-Richtung: ${when (aktuell.kmStationVorzeichen) { 1 -> "steigt mit TRA-Station"; -1 -> "fällt mit TRA-Station"; else -> "noch offen" }}")
                val kontrollanker = punkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER }
                val hauptanker = if (aktuell.kmAnkerStation != null && aktuell.kmAnkerMeter != null) 1 else 0
                appendLine("KM-Anker gesamt: ${hauptanker + kontrollanker} · Hauptanker: $hauptanker · Kontrollanker: $kontrollanker")
                appendLine("KM-Sprünge: ${punkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG }}")
                val kmStatus = when {
                    aktuell.kmAnkerStation != null && aktuell.kmAnkerMeter != null && aktuell.kmStationVorzeichen != null && aktuell.stationStart != null && aktuell.stationEnde != null -> "ORANGE · manuell kilometriert"
                    aktuell.kmAnkerStation != null && aktuell.kmAnkerMeter != null -> "ROT · KM-Anker vorhanden, Richtung noch offen"
                    else -> "ROT · keine belastbare KM"
                }
                append("KM-Status: $kmStatus")
            }
            val fertigMoeglich = aktuell.stationStart != null && aktuell.stationEnde != null
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (fertigMoeglich) "Abschnitt fertig speichern?" else "Bearbeitung noch offen")
                .setMessage(text)
                .setNegativeButton("Weiter bearbeiten") { _, _ -> zeigeAbschnittBearbeitungsMenue(aktuell) }
                .setPositiveButton(if (fertigMoeglich) "Fertig speichern" else "Offen speichern") { _, _ ->
                    val status = if (fertigMoeglich) TrassenManuellerAbschnittEntity.STATUS_FERTIG else TrassenManuellerAbschnittEntity.STATUS_OFFEN
                    speichereEntwurfUndWeiter(aktuell.copy(bearbeitungsStatus = status), "${aktuell.bezeichnung} gespeichert.") { gespeichert ->
                        stoppeAbschnittGpsWarmup()
                        if (gespeichert.stationStart != null && gespeichert.stationEnde != null) zeigeGrafischePruefung()
                    }
                }
                .show()
        }
    }

    private suspend fun persistiereAbschnittEntwurf(entwurf: ManuellerAbschnittEntwurf): ManuellerAbschnittEntwurf {
        val jetzt = System.currentTimeMillis()
        val entity = TrassenManuellerAbschnittEntity(
                id = entwurf.id,
                achseName = entwurf.achseName,
                quelleDatei = entwurf.quelleDatei,
                aktuelleAchseId = entwurf.achseId,
                dateiSignatur = entwurf.dateiSignatur,
                achsTyp = entwurf.achsTyp,
                bezeichnung = entwurf.bezeichnung,
                strecke = entwurf.strecke,
                gleis = entwurf.gleis,
                richtung = entwurf.richtung,
                stationStart = entwurf.stationStart,
                stationEnde = entwurf.stationEnde,
                startQuelle = entwurf.startQuelle,
                bekannteLaengeM = entwurf.bekannteLaengeM,
                kmRichtung = entwurf.kmRichtung,
                kmStationVorzeichen = entwurf.kmStationVorzeichen,
                kmAnkerStation = entwurf.kmAnkerStation,
                kmAnkerMeter = entwurf.kmAnkerMeter,
                kmPunkt1Meter = entwurf.kmPunkt1Meter,
                kmPunkt2Station = entwurf.kmPunkt2Station,
                kmPunkt2Meter = entwurf.kmPunkt2Meter,
                startGpsLat = entwurf.startGpsLat,
                startGpsLon = entwurf.startGpsLon,
                startGpsAccuracyM = entwurf.startGpsAccuracyM,
                startGpsStreuungM = entwurf.startGpsStreuungM,
                startAchsabstandM = entwurf.startAchsabstandM,
                punkt2GpsLat = entwurf.punkt2GpsLat,
                punkt2GpsLon = entwurf.punkt2GpsLon,
                punkt2GpsAccuracyM = entwurf.punkt2GpsAccuracyM,
                punkt2GpsStreuungM = entwurf.punkt2GpsStreuungM,
                punkt2AchsabstandM = entwurf.punkt2AchsabstandM,
                endeQuelle = entwurf.endeQuelle,
                bearbeitungsStatus = entwurf.bearbeitungsStatus,
                notiz = "Patch45e · flexibler manueller Abschnitt",
                erstelltAmEpochMs = entwurf.erstelltAmEpochMs ?: jetzt,
                aktualisiertAmEpochMs = jetzt
            )
        val id = if (entwurf.id > 0L) {
            val aktualisiert = ServiceLocator.trassenDao.manuellenAbschnittAktualisieren(entity)
            if (aktualisiert <= 0) {
                // Falls ein alter/zwischenzeitlich geloeschter Datensatz nicht mehr existiert,
                // als neuen Abschnitt anlegen statt andere Abschnittsdaten zu ueberschreiben.
                ServiceLocator.trassenDao.manuellenAbschnittEinfuegen(entity.copy(id = 0L))
            } else entwurf.id
        } else {
            ServiceLocator.trassenDao.manuellenAbschnittEinfuegen(entity)
        }
        return entwurf.copy(id = id, erstelltAmEpochMs = entwurf.erstelltAmEpochMs ?: jetzt)
    }

    private fun speichereEntwurfUndWeiter(
        entwurf: ManuellerAbschnittEntwurf,
        meldung: String,
        onFertig: (ManuellerAbschnittEntwurf) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val gespeichert = withContext(Dispatchers.IO) { persistiereAbschnittEntwurf(entwurf) }
            Toast.makeText(requireContext(), meldung, Toast.LENGTH_SHORT).show()
            onFertig(gespeichert)
        }
    }

    private fun TrassenManuellerAbschnittEntity.toEntwurf(): ManuellerAbschnittEntwurf = ManuellerAbschnittEntwurf(
        id = id,
        achsTyp = achsTyp,
        bezeichnung = bezeichnung,
        strecke = strecke,
        gleis = gleis,
        richtung = richtung,
        achseId = aktuelleAchseId,
        achseName = achseName,
        quelleDatei = quelleDatei,
        dateiSignatur = dateiSignatur,
        stationStart = stationStart,
        stationEnde = stationEnde,
        startQuelle = startQuelle,
        bekannteLaengeM = bekannteLaengeM,
        kmRichtung = kmRichtung,
        kmStationVorzeichen = kmStationVorzeichen,
        kmAnkerStation = kmAnkerStation ?: stationStart?.takeIf { kmPunkt1Meter != null },
        kmAnkerMeter = kmAnkerMeter ?: kmPunkt1Meter,
        kmPunkt1Meter = kmPunkt1Meter,
        kmPunkt2Station = kmPunkt2Station,
        kmPunkt2Meter = kmPunkt2Meter,
        startGpsLat = startGpsLat,
        startGpsLon = startGpsLon,
        startGpsAccuracyM = startGpsAccuracyM,
        startGpsStreuungM = startGpsStreuungM,
        startAchsabstandM = startAchsabstandM,
        punkt2GpsLat = punkt2GpsLat,
        punkt2GpsLon = punkt2GpsLon,
        punkt2GpsAccuracyM = punkt2GpsAccuracyM,
        punkt2GpsStreuungM = punkt2GpsStreuungM,
        punkt2AchsabstandM = punkt2AchsabstandM,
        endeQuelle = endeQuelle,
        bearbeitungsStatus = bearbeitungsStatus,
        erstelltAmEpochMs = erstelltAmEpochMs
    )

    private fun zeigeManuelleAbschnitteListe() {
        starteAbschnittGpsWarmup()
        viewLifecycleOwner.lifecycleScope.launch {
            val abschnitte = withContext(Dispatchers.IO) { ServiceLocator.trassenDao.alleManuellenAbschnitte() }
            if (abschnitte.isEmpty()) {
                Toast.makeText(requireContext(), "Noch keine manuellen Abschnitte oder Entwürfe vorhanden.", Toast.LENGTH_LONG).show()
                zeigeStreckenGleiseBearbeiten()
                return@launch
            }
            val context = requireContext()
            val inhalt = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(TextView(context).apply { text = "Offene Bearbeitungen bleiben dauerhaft gespeichert und können auch nach App-Neustart fortgesetzt werden."; textSize = 13f; setPadding(0, 0, 0, dp(10)) })
            }
            lateinit var dialog: androidx.appcompat.app.AlertDialog
            abschnitte.forEach { abschnitt ->
                inhalt.addView(MaterialButton(context).apply {
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    text = buildString {
                        append(abschnitt.bezeichnung)
                        append(" · ${if (abschnitt.bearbeitungsStatus == TrassenManuellerAbschnittEntity.STATUS_FERTIG) "FERTIG" else "OFFEN"}")
                        append("\n")
                        append(if (abschnitt.stationStart != null) "Grenze 1 ✓" else "Grenze 1 offen")
                        append(" · ")
                        append(abschnitt.manuellerKmStatusKurz())
                        append(" · ")
                        append(if (abschnitt.stationEnde != null) "Grenze 2 ✓" else "Grenze 2 offen")
                    }
                    setOnClickListener { dialog.dismiss(); zeigeManuellenAbschnittInfo(abschnitt) }
                })
            }
            dialog = MaterialAlertDialogBuilder(context)
                .setTitle("Bestehende Abschnitte / Entwürfe")
                .setView(android.widget.ScrollView(context).apply { addView(inhalt) })
                .setNegativeButton("Schließen") { _, _ -> stoppeAbschnittGpsWarmup() }
                .create()
            dialog.show()
        }
    }

    private fun zeigeManuellenAbschnittInfo(abschnitt: TrassenManuellerAbschnittEntity) {
        val optionen = listOf("Fortsetzen / bearbeiten", "Details anzeigen", "Löschen")
        zeigeAuswahlMitHinweis(
            abschnitt.bezeichnung,
            "${if (abschnitt.bearbeitungsStatus == TrassenManuellerAbschnittEntity.STATUS_FERTIG) "Fertig" else "Offene Bearbeitung"} · ${abschnitt.manuellerKmStatusKurz()}",
            optionen
        ) { index ->
            when (index) {
                0 -> zeigeAbschnittBearbeitungsMenue(abschnitt.toEntwurf())
                1 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val punkte = withContext(Dispatchers.IO) { ServiceLocator.trassenDao.abschnittKmPunkte(abschnitt.id) }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(abschnitt.bezeichnung)
                            .setMessage(buildString {
                                appendLine("Technische TRA: ${abschnitt.achseName ?: "offen"}")
                                appendLine("Grenze 1: ${abschnitt.stationStart?.let(::format1) ?: "offen"} m")
                                appendLine("Grenze 2: ${abschnitt.stationEnde?.let(::format1) ?: "offen"} m")
                                appendLine("KM-Anker: ${abschnitt.kmAnkerMeter?.let(::formatKmMeter) ?: "offen"}")
                                appendLine("Richtung: ${abschnitt.kmRichtung ?: "offen"}")
                                appendLine("Kontrollanker: ${punkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER }}")
                                append("KM-Sprünge: ${punkte.count { it.typ == TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG }}")
                            })
                            .setPositiveButton("OK") { _, _ -> zeigeManuelleAbschnitteListe() }
                            .show()
                    }
                }
                2 -> MaterialAlertDialogBuilder(requireContext())
                    .setTitle("${abschnitt.bezeichnung} löschen?")
                    .setMessage("Nur der manuelle Abschnitt/Entwurf wird gelöscht. Die Roh-TRA bleibt unverändert.")
                    .setNegativeButton("Abbrechen") { _, _ -> zeigeManuelleAbschnitteListe() }
                    .setPositiveButton("Löschen") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            withContext(Dispatchers.IO) { ServiceLocator.trassenDao.manuellenAbschnittLoeschen(abschnitt.id) }
                            zeigeManuelleAbschnitteListe()
                        }
                    }.show()
            }
        }
    }


    private fun zeigeGrafischenPunktWaehler(
        titel: String,
        festeAchseId: Long?,
        onAuswahl: (GrafikPunktAuswahl) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val grafikAchsen = withContext(Dispatchers.IO) { ladeRohGrafikAchsen() }
                .filter { festeAchseId == null || it.achseId == festeAchseId }
            if (grafikAchsen.isEmpty()) {
                Toast.makeText(requireContext(), "Keine passende TRA-Geometrie für die Grafikauswahl vorhanden.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val context = requireContext()
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
            }
            container.addView(TextView(context).apply {
                text = titel
                textSize = 18f
                textStyleBold()
            })
            container.addView(TextView(context).apply {
                text = "Zoomen und ungefähr auf die gewünschte Stelle tippen. Der Punkt rastet automatisch auf der TRA-Achse ein."
                textSize = 12f
                setPadding(0, 0, 0, dp(4))
            })
            val grafik = TrassenGrafikView(context).apply {
                setAchsen(grafikAchsen)
                setSichtbareTypen(grafikAchsen.map { it.achsTyp }.toSet())
            }
            container.addView(grafik, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            val btn = MaterialButton(context).apply { text = "Abbrechen" }
            container.addView(btn)
            val dialog = android.app.Dialog(context).apply {
                requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                setContentView(container)
            }
            btn.setOnClickListener { dialog.dismiss() }
            grafik.setOnAchsePunktRequested { achse, station ->
                MaterialAlertDialogBuilder(context)
                    .setTitle("Punkt übernehmen?")
                    .setMessage("${achse.label}\nStation ${format1(station)} m\n\nDer Fingertipp wurde auf die Achse eingerastet.")
                    .setNegativeButton("Nochmal", null)
                    .setPositiveButton("Übernehmen") { _, _ ->
                        dialog.dismiss()
                        onAuswahl(GrafikPunktAuswahl(achse, station))
                    }
                    .show()
            }
            ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                v.setPadding(dp(10) + bars.left, dp(6) + bars.top, dp(10) + bars.right, dp(6) + bars.bottom)
                grafik.post { grafik.fitToContent() }
                insets
            }
            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                ViewCompat.requestApplyInsets(container)
                grafik.post { grafik.fitToContent() }
            }
            dialog.show()
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun frageBekanntenKmUndVerarbeite(
        entwurf: ManuellerAbschnittEntwurf,
        typ: String,
        station: Double,
        vorgeschlagenKm: Double?,
        achsabstandM: Double?,
        seite: TrassenNaheBerechner.Seite?,
        punktLabel: String,
        kmHinweis: String?,
        messung: AbschnittGpsMessung?,
        richtungAutomatisch: Boolean,
        punktNotiz: String?,
        onVorVerarbeitung: (() -> Unit)? = null,
        onZurueck: (() -> Unit)? = null
    ) {
        val context = requireContext()
        val kmEdit = EditText(context).apply {
            hint = "Bekannter km, z. B. 23,987"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            vorgeschlagenKm?.let { setText(formatKmMeter(it)) }
            selectAll()
        }
        val seiteText = when (seite) {
            TrassenNaheBerechner.Seite.LINKS -> "links"
            TrassenNaheBerechner.Seite.RECHTS -> "rechts"
            TrassenNaheBerechner.Seite.AUF_ACHSE -> "auf der Achse"
            TrassenNaheBerechner.Seite.UNBEKANNT, null -> "Seite unbekannt"
        }
        val inhalt = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(TextView(context).apply {
                text = buildString {
                    appendLine("Punkt: $punktLabel")
                    appendLine("Zielachse: ${entwurf.bezeichnung}")
                    appendLine("TRA-Station: ${format1(station)} m")
                    achsabstandM?.let { appendLine("Abstand: ${format1(it)} m · $seiteText") }
                    if (vorgeschlagenKm != null) appendLine("KM aus Datei: ${formatKmMeter(vorgeschlagenKm)} · bitte prüfen")
                    kmHinweis?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                    if (achsabstandM != null && achsabstandM > 50.0) {
                        appendLine("Hinweis: großer Abstand zur Zielachse – Zuordnung prüfen.")
                    }
                    append("Mast-/Punktbezeichnungen werden niemals als Strecken-km interpretiert.")
                }
                textSize = 13f
                setPadding(0, 0, 0, dp(10))
            })
            addView(kmEdit)
        }
        val dlg = MaterialAlertDialogBuilder(context)
            .setTitle(if (typ == TrassenAbschnittKmPunktEntity.TYP_ANKER) "KM-Anker übernehmen" else "Kontrollanker übernehmen")
            .setView(inhalt)
            .setNegativeButton("Zurück") { _, _ -> onZurueck?.invoke() }
            .setPositiveButton("Übernehmen", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val km = parseKmMeter(kmEdit.text?.toString().orEmpty())
                if (km == null) {
                    Toast.makeText(context, "Bitte den echten bekannten km eingeben.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dlg.dismiss()
                onVorVerarbeitung?.invoke()
                verarbeiteKmPunkt(
                    entwurf = entwurf,
                    typ = typ,
                    station = station,
                    km = km,
                    messung = messung,
                    achsabstandM = achsabstandM,
                    richtungAutomatisch = richtungAutomatisch,
                    punktNotiz = punktNotiz
                )
            }
        }
        dlg.show()
    }

    private fun zeigeVermarkungsKmPunktWaehler(
        entwurf: ManuellerAbschnittEntwurf,
        typ: String,
        richtungAutomatisch: Boolean
    ) {
        val zielId = entwurf.achseId
        if (zielId == null) {
            Toast.makeText(requireContext(), "Für diesen Abschnitt ist keine feste TRA gespeichert.", Toast.LENGTH_LONG).show()
            zeigeAbschnittBearbeitungsMenue(entwurf)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val (rawAlle, festpunkte) = withContext(Dispatchers.IO) { ladeRohGrafikAchsen() to ladeGrafikVermessungspunkte() }
            val zielRaw = rawAlle.firstOrNull { it.achseId == zielId }
            if (zielRaw == null) {
                Toast.makeText(requireContext(), "Die gespeicherte Ziel-TRA ist nicht mehr vorhanden.", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (festpunkte.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Keine Vermarkungspunkte")
                    .setMessage("Keine Fest-, Mast- oder Absteckpunkte vorhanden. Bitte den KM-Anker per GPS setzen.")
                    .setPositiveButton("GPS") { _, _ -> zeigeKmPunktSetzen(entwurf, typ, richtungAutomatisch) }
                    .setNegativeButton("Zurück") { _, _ -> zeigeAbschnittBearbeitungsMenue(entwurf) }
                    .show()
                return@launch
            }

            val context = requireContext()
            val darstellung = rawAlle.map { a -> if (a.achseId == zielId) a.copy(label = entwurf.bezeichnung) else a }
            val sichtbarRaw = mutableSetOf(zielId)
            val sichtbarePunktArten = festpunkte.map { it.art }.toMutableSet()
            val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val kopf = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(2), dp(4), dp(2)) }
            val btnZurueck = MaterialButton(context).apply { text = "Zurück"; minWidth = 0; minimumWidth = 0 }
            val titelText = TextView(context).apply {
                text = "KM-Anker · ${entwurf.bezeichnung}"; textSize = 17f; textStyleBold(); gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnAnzeige = MaterialButton(context).apply { text = "Anzeige"; minWidth = 0; minimumWidth = 0 }
            kopf.addView(btnZurueck); kopf.addView(titelText); kopf.addView(btnAnzeige); container.addView(kopf)
            container.addView(TextView(context).apply {
                text = "Zielachse ist fest: ${entwurf.bezeichnung}. Vermarkungspunkt antippen. Weitere Achsen können über Anzeige nur zur Orientierung eingeblendet werden."
                textSize = 12f; setPadding(dp(8), 0, dp(8), dp(4))
            })
            val grafik = TrassenGrafikView(context).apply {
                setAchsen(darstellung); setFestpunkte(festpunkte); setFestpunkteSichtbar(true)
                setSichtbareTypen(darstellung.map { it.achsTyp }.toSet()); setSichtbareAchseIds(sichtbarRaw.toSet())
                setSichtbarePunktArten(sichtbarePunktArten.toSet()); setAktiveAchseId(zielId); setLegendeSichtbar(false)
            }
            container.addView(grafik, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            lateinit var dialog: android.app.Dialog

            fun zeigeAnzeige() {
                val liste = darstellung.sortedWith(compareBy<TrassenGrafikAchse> { it.label }.thenBy { it.name })
                val achsenLabels = liste.map { if (it.achseId == zielId) "✓ Ziel: ${entwurf.bezeichnung}" else it.toGrafikAuswahlText(liste) }
                val punktKonfiguration = listOf("GVP" to "Festpunkte / Masten", "AP" to "Absteckpunkte", "WEICHENPUNKT" to "Weichenpunkte")
                    .filter { (art, _) -> festpunkte.any { it.art == art } }
                val labels = achsenLabels.toMutableList()
                val punktIndex = mutableMapOf<Int, String>()
                punktKonfiguration.forEach { (art, text) -> punktIndex[labels.size] = art; labels += "$text (${festpunkte.count { it.art == art }})" }
                val tempRaw = sichtbarRaw.toMutableSet(); val tempPunkte = sichtbarePunktArten.toMutableSet()
                val checked = BooleanArray(labels.size) { idx ->
                    if (idx < liste.size) liste[idx].achseId in tempRaw else punktIndex[idx]?.let { it in tempPunkte } == true
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle("Anzeige · Orientierung")
                    .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                        if (which < liste.size) {
                            val id = liste[which].achseId
                            if (id == zielId) tempRaw.add(id) else if (isChecked) tempRaw.add(id) else tempRaw.remove(id)
                        } else punktIndex[which]?.let { art -> if (isChecked) tempPunkte.add(art) else tempPunkte.remove(art) }
                    }
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Übernehmen") { _, _ ->
                        tempRaw.add(zielId); sichtbarRaw.clear(); sichtbarRaw.addAll(tempRaw)
                        sichtbarePunktArten.clear(); sichtbarePunktArten.addAll(tempPunkte)
                        grafik.setSichtbareAchseIds(sichtbarRaw.toSet()); grafik.setSichtbarePunktArten(sichtbarePunktArten.toSet())
                    }.show()
            }

            fun punktWaehlen(punkt: TrassenGrafikFestpunkt) {
                val projektion = projiziereVermessungspunktAufAchse(punkt, zielRaw)
                if (projektion == null) {
                    Toast.makeText(context, "Der Punkt konnte nicht auf ${entwurf.bezeichnung} projiziert werden.", Toast.LENGTH_LONG).show(); return
                }
                grafik.setAnkerProjektion(TrassenGrafikProjektion(punkt.x, punkt.y, projektion.projX, projektion.projY))
                val zielStrecke = entwurf.strecke?.trimToNull() ?: zielRaw.strecke?.trimToNull()
                val punktStrecke = punkt.strecke?.trimToNull()
                val streckePasst = zielStrecke == null || punktStrecke == null || zielStrecke.equals(punktStrecke, ignoreCase = true)
                val vorgeschlagenKm = punkt.kmMeter?.takeIf { streckePasst }
                val kmHinweis = when {
                    punkt.kmMeter != null && !streckePasst -> "Datei-km ${formatKmMeter(punkt.kmMeter)} gehört laut Punkt zu Strecke ${punktStrecke ?: "?"} und wird nicht automatisch vorbelegt."
                    punkt.kmMeter != null -> "Der vorhandene Datei-km wird vorgeschlagen und muss bestätigt werden."
                    else -> "Für diesen Punkt ist kein echter km gespeichert. Bitte den bekannten km eingeben."
                }
                val notiz = buildString {
                    append("Vermarkungspunkt ${punkt.label.ifBlank { punkt.punktId ?: "?" }}")
                    punkt.quelleDatei?.takeIf { it.isNotBlank() }?.let { append(" · Quelle $it") }
                    append(" · ${format1(projektion.abstandM)} m ${projektion.seite.alsSeiteText()}")
                }
                frageBekanntenKmUndVerarbeite(
                    entwurf = entwurf, typ = typ, station = projektion.station, vorgeschlagenKm = vorgeschlagenKm,
                    achsabstandM = projektion.abstandM, seite = projektion.seite,
                    punktLabel = punkt.label.ifBlank { punkt.punktId ?: "Vermessungspunkt" }, kmHinweis = kmHinweis,
                    messung = null, richtungAutomatisch = richtungAutomatisch, punktNotiz = notiz,
                    onVorVerarbeitung = { dialog.dismiss() }
                )
            }

            grafik.setOnFestpunkteInfoRequested { kandidaten ->
                val eindeutig = kandidaten.distinctBy { p ->
                    val xCm = kotlin.math.round(p.x * 100f).toInt(); val yCm = kotlin.math.round(p.y * 100f).toInt()
                    "${p.label.ifBlank { p.punktId ?: p.art }.trim().uppercase(Locale.GERMANY)}|$xCm|$yCm"
                }
                when {
                    eindeutig.size == 1 -> punktWaehlen(eindeutig.first())
                    eindeutig.isNotEmpty() -> MaterialAlertDialogBuilder(context).setTitle("Welchen Punkt als KM-Anker?")
                        .setItems(eindeutig.map { it.toGrafikPunktAuswahlText() }.toTypedArray()) { _, which -> punktWaehlen(eindeutig[which]) }
                        .setNegativeButton("Abbrechen", null).show()
                }
            }

            dialog = android.app.Dialog(context).apply { requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); setContentView(container); setCancelable(true) }
            btnZurueck.setOnClickListener { dialog.dismiss(); zeigeAbschnittBearbeitungsMenue(entwurf) }
            btnAnzeige.setOnClickListener { zeigeAnzeige() }
            ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()); v.setPadding(bars.left, bars.top, bars.right, bars.bottom); grafik.post { grafik.fitToContent() }; insets
            }
            dialog.setOnShowListener {
                dialog.window?.let { w -> WindowCompat.setDecorFitsSystemWindows(w, false); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
                ViewCompat.requestApplyInsets(container); grafik.post { grafik.fitToContent() }
            }
            dialog.show(); dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun TrassenNaheBerechner.Seite.alsSeiteText(): String = when (this) {
        TrassenNaheBerechner.Seite.LINKS -> "links"
        TrassenNaheBerechner.Seite.RECHTS -> "rechts"
        TrassenNaheBerechner.Seite.AUF_ACHSE -> "auf Achse"
        TrassenNaheBerechner.Seite.UNBEKANNT -> "unbekannt"
    }

    private fun projiziereVermessungspunktAufAchse(
        punkt: TrassenGrafikFestpunkt,
        achse: TrassenGrafikAchse
    ): VermarkungsProjektion? {
        if (achse.punkte.size < 2 || !punkt.x.isFinite() || !punkt.y.isFinite()) return null
        var beste: VermarkungsProjektion? = null
        for (i in 0 until achse.punkte.lastIndex) {
            val a = achse.punkte[i]
            val b = achse.punkte[i + 1]
            val vx = (b.x - a.x).toDouble()
            val vy = (b.y - a.y).toDouble()
            val len2 = vx * vx + vy * vy
            if (len2 <= 0.000001) continue
            val wx = (punkt.x - a.x).toDouble()
            val wy = (punkt.y - a.y).toDouble()
            val t = ((wx * vx + wy * vy) / len2).coerceIn(0.0, 1.0)
            val projX = a.x.toDouble() + t * vx
            val projY = a.y.toDouble() + t * vy
            val dx = punkt.x.toDouble() - projX
            val dy = punkt.y.toDouble() - projY
            val abstand = kotlin.math.sqrt(dx * dx + dy * dy)
            val cross = vx * wy - vy * wx
            val seite = when {
                abstand < 0.25 -> TrassenNaheBerechner.Seite.AUF_ACHSE
                kotlin.math.abs(cross) < 0.0001 -> TrassenNaheBerechner.Seite.UNBEKANNT
                cross > 0.0 -> TrassenNaheBerechner.Seite.LINKS
                cross < 0.0 -> TrassenNaheBerechner.Seite.RECHTS
                else -> TrassenNaheBerechner.Seite.UNBEKANNT
            }
            val station = a.station + (b.station - a.station) * t
            val kandidat = VermarkungsProjektion(
                station = station,
                abstandM = abstand,
                seite = seite,
                projX = projX.toFloat(),
                projY = projY.toFloat()
            )
            if (kandidat.abstandM < (beste?.abstandM ?: Double.POSITIVE_INFINITY)) beste = kandidat
        }
        return beste
    }

    private suspend fun ladeGrafikVermessungspunkte(): List<TrassenGrafikFestpunkt> {
        val dao = ServiceLocator.trassenDao
        val gvp = dao.alleGvpPunkte()
            .filter { it.xDbRef.isFinite() && it.yDbRef.isFinite() }
            .map {
                TrassenGrafikFestpunkt(
                    label = it.punktName,
                    art = "GVP",
                    x = it.xDbRef.toFloat(),
                    y = it.yDbRef.toFloat(),
                    kmMeter = it.kmMeter,
                    strecke = it.strecke,
                    hoeheM = it.hoeheM,
                    typ = it.typ,
                    quelleDatei = it.quelleDatei,
                    punktId = it.klasse
                )
            }
        val absteck = dao.alleAbsteckpunkte()
            .filter { it.xDbRef.isFinite() && it.yDbRef.isFinite() }
            .map {
                val istWeichenpunkt = Regex("(?i)^(WA|WEZ|WE)[_\\s-]*\\d+[A-Z]?").containsMatchIn(it.punktName.trim())
                TrassenGrafikFestpunkt(
                    label = it.punktName,
                    art = if (istWeichenpunkt) "WEICHENPUNKT" else "AP",
                    x = it.xDbRef.toFloat(),
                    y = it.yDbRef.toFloat(),
                    kmMeter = it.kmMeter,
                    strecke = it.strecke,
                    gleis = it.gleis,
                    hoeheM = it.hoeheM,
                    typ = it.typ,
                    quelleDatei = it.quelleDatei,
                    punktId = it.punktName
                )
            }
        return (gvp + absteck).distinctBy { punkt ->
            val xCm = kotlin.math.round(punkt.x * 100f).toInt()
            val yCm = kotlin.math.round(punkt.y * 100f).toInt()
            val identitaet = punkt.punktId?.trimToNull() ?: punkt.label.trim()
            "${identitaet.uppercase(Locale.GERMANY)}|$xCm|$yCm"
        }
    }

    private suspend fun ladeRohGrafikAchsen(): List<TrassenGrafikAchse> {
        val dao = ServiceLocator.trassenDao
        val achsen = dao.alleAchsen()
        val punkteNachAchse = dao.alleSamplepunkte()
            .filter { it.xDbRef.isFinite() && it.yDbRef.isFinite() }
            .groupBy { it.achseId }
        val ankerNachAchse = dao.alleKmAnker().groupBy { it.achseName to it.quelleDatei }
        return achsen.mapNotNull { achse ->
            val raw = punkteNachAchse[achse.id].orEmpty().sortedBy { it.station }
            if (raw.size < 2) return@mapNotNull null
            val anker = ankerNachAchse[achse.name to achse.quelleDatei].orEmpty().sortedBy { it.punktNummer }
            val bewertung = TrassenKmStatusLogik.bewerte(
                raw.map { TrassenKmStatusLogik.Punkt(it.station, it.kmMeter.takeIf { _ -> it.kmQuelle != null }) },
                anker.map { TrassenKmStatusLogik.Anker(it.punktNummer, it.station, it.kmMeter) }
            )
            val richtungQuelle = achse.name.richtungsRohwertAusAchsenName()
                ?: achse.quelleDatei.richtungsRohwertAusAchsenName()
                ?: achse.richtung?.trim()?.takeIf { it.isNotBlank() }
            val richtungEffektiv = achse.richtung.richtungAlsKennziffer()
                ?: richtungQuelle.richtungAlsKennziffer()
            TrassenGrafikAchse(
                achseId = achse.id,
                name = achse.name,
                label = achse.toGrafikLabel(),
                achsTyp = achse.achsTyp,
                strecke = achse.effektiveStrecke(),
                gleis = achse.gleis,
                richtung = richtungEffektiv,
                richtungQuelle = richtungQuelle,
                quelleDatei = achse.quelleDatei,
                dateiSignatur = achse.dateiSignatur,
                notiz = achse.notiz,
                istManuell = false,
                kmStatus = bewertung.status,
                kmStatusGrund = bewertung.grund,
                manuelleAnker = anker.map { TrassenGrafikKmAnker(it.punktNummer, it.station, it.kmMeter) },
                punkte = raw.map { TrassenGrafikPunkt(it.station, it.xDbRef.toFloat(), it.yDbRef.toFloat(), it.kmMeter.takeIf { _ -> it.kmQuelle != null }) }
            )
        }
    }

    private fun TrassenManuellerAbschnittEntity.manuellerKmStatusKurz(): String {
        return when {
            kmAnkerStation != null && kmAnkerMeter != null && kmStationVorzeichen != null && stationStart != null && stationEnde != null -> "ORANGE · manuell kilometriert"
            kmAnkerStation != null && kmAnkerMeter != null -> "ROT · manueller Abschnitt noch unvollstaendig"
            kmPunkt1Meter != null && kmPunkt2Station != null && kmPunkt2Meter != null && stationStart != null -> {
                val kal = TrassenKmStatusLogik.kalibrierungAus(
                    TrassenKmStatusLogik.Anker(1, stationStart, kmPunkt1Meter),
                    TrassenKmStatusLogik.Anker(2, kmPunkt2Station, kmPunkt2Meter)
                )
                if (kal.plausibel) "ORANGE · manuell kalibriert" else "ROT · unplausibel"
            }
            else -> "ROT · keine belastbare KM"
        }
    }

    private fun parseKmMeter(text: String): Double? {
        val sauber = text.trim().lowercase(Locale.GERMANY)
            .removePrefix("km")
            .trim()
            .replace(' ', '.')
            .replace(',', '.')
        val wert = sauber.toDoubleOrNull() ?: return null
        return when {
            wert < 0.0 -> null
            wert < 1000.0 -> wert * 1000.0
            else -> wert
        }
    }

    private fun formatKmMeter(kmMeter: Double): String = String.format(Locale.GERMANY, "%.3f", kmMeter / 1000.0)

    private fun distanzMeter(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val ergebnis = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, ergebnis)
        return ergebnis[0].toDouble()
    }

    private fun TextView.textStyleBold() {
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }


    private fun zeigeGrafischePruefung() {
        txtStatus?.text = "Grafische Übersicht wird geladen…"
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dao = ServiceLocator.trassenDao
                    val achsen = dao.alleAchsen()
                    val punkteNachAchse = dao.alleSamplepunkte()
                        .filter { punkt -> punkt.xDbRef.isFinite() && punkt.yDbRef.isFinite() }
                        .groupBy { it.achseId }
                    val ankerNachAchse = dao.alleKmAnker()
                        .groupBy { it.achseName to it.quelleDatei }
                    val manuelleAbschnitte = dao.alleManuellenAbschnitte()
                    val abschnittKmPunkteNachId = dao.alleAbschnittKmPunkte().groupBy { it.abschnittId }

                    val basisGrafikAchsen = achsen.mapNotNull { achse ->
                        val rawPunkte = punkteNachAchse[achse.id]
                            .orEmpty()
                            .sortedBy { it.station }
                        if (rawPunkte.size < 2) return@mapNotNull null

                        val punkte = rawPunkte.map { punkt ->
                            TrassenGrafikPunkt(
                                station = punkt.station,
                                x = punkt.xDbRef.toFloat(),
                                y = punkt.yDbRef.toFloat(),
                                kmMeter = punkt.kmMeter.takeIf { punkt.kmQuelle != null }
                            )
                        }
                        val anker = ankerNachAchse[achse.name to achse.quelleDatei]
                            .orEmpty()
                            .sortedBy { it.punktNummer }
                        val bewertung = TrassenKmStatusLogik.bewerte(
                            punkte = rawPunkte.map { TrassenKmStatusLogik.Punkt(it.station, it.kmMeter.takeIf { _ -> it.kmQuelle != null }) },
                            manuelleAnker = anker.map { TrassenKmStatusLogik.Anker(it.punktNummer, it.station, it.kmMeter) }
                        )
                        val richtungQuelle = achse.name.richtungsRohwertAusAchsenName()
                            ?: achse.quelleDatei.richtungsRohwertAusAchsenName()
                            ?: achse.richtung?.trim()?.takeIf { it.isNotBlank() }
                        val richtungEffektiv = achse.richtung.richtungAlsKennziffer()
                            ?: richtungQuelle.richtungAlsKennziffer()

                        TrassenGrafikAchse(
                            achseId = achse.id,
                            name = achse.name,
                            label = achse.toGrafikLabel(),
                            achsTyp = achse.achsTyp,
                            strecke = achse.effektiveStrecke(),
                            gleis = achse.gleis,
                            richtung = richtungEffektiv,
                            richtungQuelle = richtungQuelle,
                            quelleDatei = achse.quelleDatei,
                            dateiSignatur = achse.dateiSignatur,
                            notiz = achse.notiz,
                            istManuell = achse.notiz?.contains("manuell", ignoreCase = true) == true,
                            kmStatus = bewertung.status,
                            kmStatusGrund = bewertung.grund,
                            manuelleAnker = anker.map {
                                TrassenGrafikKmAnker(
                                    punktNummer = it.punktNummer,
                                    station = it.station,
                                    kmMeter = it.kmMeter
                                )
                            },
                            punkte = punkte
                        )
                    }

                    val basisById = basisGrafikAchsen.associateBy { it.achseId }
                    val achsenById = achsen.associateBy { it.id }
                    val abschnittGrafikAchsen = manuelleAbschnitte.mapNotNull { abschnitt ->
                        val achseId = abschnitt.aktuelleAchseId?.takeIf { id -> achsenById.containsKey(id) }
                            ?: achsen.firstOrNull { it.name == abschnitt.achseName && it.quelleDatei == abschnitt.quelleDatei }?.id
                            ?: return@mapNotNull null
                        val roh = punkteNachAchse[achseId].orEmpty().sortedBy { it.station }
                        if (roh.size < 2) return@mapNotNull null
                        val rohGrafik = basisById[achseId] ?: return@mapNotNull null
                        val rohPunkteGrafik = roh.map { punkt ->
                            TrassenGrafikPunkt(
                                station = punkt.station,
                                x = punkt.xDbRef.toFloat(),
                                y = punkt.yDbRef.toFloat(),
                                kmMeter = punkt.kmMeter.takeIf { punkt.kmQuelle != null }
                            )
                        }
                        val segment = segmentFuerAbschnittGrafik(rohPunkteGrafik, abschnitt.stationStart, abschnitt.stationEnde)
                        if (segment.size < 2) return@mapNotNull null

                        val kmPunkte = abschnittKmPunkteNachId[abschnitt.id].orEmpty()
                        val basisStatus = rohGrafik.kmStatus
                        val status = when {
                            abschnitt.kmAnkerStation != null && abschnitt.kmAnkerMeter != null && abschnitt.kmStationVorzeichen != null -> TrassenKmStatusLogik.STATUS_MANUELL
                            basisStatus == TrassenKmStatusLogik.STATUS_AUTOMATISCH -> TrassenKmStatusLogik.STATUS_AUTOMATISCH
                            else -> TrassenKmStatusLogik.STATUS_FEHLT
                        }
                        val grund = when (status) {
                            TrassenKmStatusLogik.STATUS_MANUELL -> "Betrieblicher Abschnitt · KM-Anker + Richtung"
                            TrassenKmStatusLogik.STATUS_AUTOMATISCH -> "Automatische KM der Geometrie"
                            else -> "Abschnitt ohne belastbare KM"
                        }
                        val extraAnker = buildList {
                            val hatPrimaerDatensatz = kmPunkte.any { it.typ == TrassenAbschnittKmPunktEntity.TYP_ANKER }
                            if (!hatPrimaerDatensatz && abschnitt.kmAnkerStation != null && abschnitt.kmAnkerMeter != null) {
                                add(TrassenGrafikKmAnker(1, abschnitt.kmAnkerStation, abschnitt.kmAnkerMeter))
                            }
                            kmPunkte.filter { it.typ != TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG && it.kmMeter != null }
                                .sortedBy { it.station }
                                .forEach { p -> add(TrassenGrafikKmAnker(size + 1, p.station, p.kmMeter!!)) }
                        }
                        TrassenGrafikAchse(
                            achseId = -abschnitt.id.coerceAtLeast(1L),
                            name = abschnitt.achseName ?: rohGrafik.name,
                            label = abschnitt.bezeichnung,
                            achsTyp = abschnitt.achsTyp,
                            strecke = abschnitt.strecke,
                            gleis = abschnitt.gleis,
                            richtung = abschnitt.richtung,
                            richtungQuelle = abschnitt.richtung,
                            quelleDatei = abschnitt.quelleDatei ?: rohGrafik.quelleDatei,
                            dateiSignatur = abschnitt.dateiSignatur,
                            notiz = "Betrieblicher Abschnitt",
                            istManuell = true,
                            kmStatus = status,
                            kmStatusGrund = grund,
                            manuelleAnker = extraAnker,
                            manuellerAbschnittId = abschnitt.id,
                            abschnittStartStation = abschnitt.stationStart,
                            abschnittEndeStation = abschnitt.stationEnde,
                            punkte = segment.map { gp ->
                                val kmErgebnis = ManuelleAbschnittKmLogik.kmFuerStation(abschnitt, gp.station, kmPunkte)
                                val rohKm = roh.firstOrNull { kotlin.math.abs(it.station - gp.station) < 0.001 }?.let { p -> p.kmMeter.takeIf { p.kmQuelle != null } }
                                val km = when {
                                    kmErgebnis.kmMeter != null -> kmErgebnis.kmMeter
                                    status == TrassenKmStatusLogik.STATUS_AUTOMATISCH -> rohKm
                                    else -> null
                                }
                                gp.copy(kmMeter = km)
                            }
                        )
                    }
                    val verwendeteRawIds = manuelleAbschnitte.mapNotNull { it.aktuelleAchseId }.toSet()
                    val basisGrafikAchsenDarstellung = basisGrafikAchsen.map { raw ->
                        if (raw.achseId in verwendeteRawIds) raw.copy(neutralDarstellung = true, labelSichtbar = false) else raw
                    }
                    val grafikAchsen = basisGrafikAchsenDarstellung + abschnittGrafikAchsen

                    val gvp = dao.alleGvpPunkte()
                        .filter { it.xDbRef.isFinite() && it.yDbRef.isFinite() }
                        .map {
                            TrassenGrafikFestpunkt(
                                label = it.punktName,
                                art = "GVP",
                                x = it.xDbRef.toFloat(),
                                y = it.yDbRef.toFloat(),
                                kmMeter = it.kmMeter,
                                strecke = it.strecke,
                                hoeheM = it.hoeheM,
                                typ = it.typ,
                                quelleDatei = it.quelleDatei,
                                punktId = it.klasse
                            )
                        }
                    val ap = dao.alleAbsteckpunkte()
                        .filter { it.xDbRef.isFinite() && it.yDbRef.isFinite() }
                        .map {
                            val istWeichenpunkt = Regex("(?i)^(WA|WEZ|WE)[_\\s-]*\\d+[A-Z]?").containsMatchIn(it.punktName.trim())
                            TrassenGrafikFestpunkt(
                                label = it.punktName,
                                art = if (istWeichenpunkt) "WEICHENPUNKT" else "AP",
                                x = it.xDbRef.toFloat(),
                                y = it.yDbRef.toFloat(),
                                kmMeter = it.kmMeter,
                                strecke = it.strecke,
                                gleis = it.gleis,
                                hoeheM = it.hoeheM,
                                typ = it.typ,
                                quelleDatei = it.quelleDatei,
                                punktId = it.punktName
                            )
                        }
                    val punkteOhneDubletten = (gvp + ap).distinctBy { punkt ->
                        val xCm = kotlin.math.round(punkt.x * 100f).toInt()
                        val yCm = kotlin.math.round(punkt.y * 100f).toInt()
                        // KF.TXT und KOR koennen denselben physischen Punkt mit unterschiedlicher
                        // Anzeige-Bezeichnung liefern (z. B. KF-Lage "2050488", KOR-PunktNr "77.00002").
                        // Die technische Punkt-ID ist dort identisch und eignet sich fuer eine
                        // konservative Grafik-Dublettenunterdrueckung. Unterschiedliche IDs an
                        // gleicher Koordinate (z. B. WA6 und WA7) bleiben bewusst getrennt.
                        val identitaet = punkt.punktId?.trimToNull() ?: punkt.label.trim()
                        "${identitaet.uppercase(Locale.GERMANY)}|$xCm|$yCm"
                    }
                    grafikAchsen to punkteOhneDubletten
                }
            }.onSuccess { (grafikAchsen, festpunkte) ->
                txtStatus?.text = "Grafische Übersicht: ${grafikAchsen.size} Achse(n) geladen."
                if (grafikAchsen.isEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Grafische Übersicht")
                        .setMessage("Keine berechneten Achspunkte vorhanden. Erst Streckendaten importieren und Daten berechnen.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@onSuccess
                }

                val context = requireContext()
                val anzeigeAchsen = grafikAchsen.distinctBy { it.achseId }
                val alleAchseIds = anzeigeAchsen.map { it.achseId }.toMutableSet()
                val alleTypen = grafikAchsen.map { it.achsTyp }.toSet()
                val punktArtenVorhanden = festpunkte.map { it.art }.distinct().toSet()
                fun rawIdFuerAnzeigeAchse(a: TrassenGrafikAchse): Long? {
                    if (a.manuellerAbschnittId == null && a.achseId > 0) return a.achseId
                    return anzeigeAchsen.firstOrNull { raw ->
                        raw.manuellerAbschnittId == null && raw.achseId > 0 && raw.name == a.name && raw.quelleDatei == a.quelleDatei
                    }?.achseId
                }
                val gespeicherteRaw = letzteGrafikSichtbareRohAchseIds
                val sichtbareAchseIds = if (!gespeicherteRaw.isNullOrEmpty()) {
                    anzeigeAchsen.filter { rawIdFuerAnzeigeAchse(it) in gespeicherteRaw }.map { it.achseId }.toMutableSet()
                        .takeIf { it.isNotEmpty() } ?: alleAchseIds.toMutableSet()
                } else alleAchseIds.toMutableSet()
                val sichtbarePunktArten = (letzteGrafikSichtbarePunktArten?.intersect(punktArtenVorhanden)?.toMutableSet()
                    ?.takeIf { it.isNotEmpty() } ?: punktArtenVorhanden.toMutableSet())

                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
                }

                val kopf = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                }
                val btnZurueck = MaterialButton(context).apply {
                    text = "Zurück"
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(10), 0, dp(10), 0)
                }
                val title = TextView(context).apply {
                    text = "Grafische Übersicht"
                    textSize = 18f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, dp(8), 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val btnAnzeige = MaterialButton(context).apply {
                    text = "Anzeige"
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(10), 0, dp(10), 0)
                }
                kopf.addView(btnZurueck)
                kopf.addView(title)
                kopf.addView(btnAnzeige)
                container.addView(kopf)

                lateinit var dialog: android.app.Dialog
                val grafik = TrassenGrafikView(context).apply {
                    setAchsen(grafikAchsen)
                    setFestpunkte(festpunkte)
                    setFestpunkteSichtbar(true)
                    setSichtbareTypen(alleTypen)
                    setSichtbareAchseIds(sichtbareAchseIds.toSet())
                    setSichtbarePunktArten(sichtbarePunktArten.toSet())
                    setLegendeSichtbar(false)
                    setOnAchseInfoRequested { achse ->
                        val zusatz = if (achse.manuellerAbschnittId != null) {
                            "\n\nManueller Abschnitt #${achse.manuellerAbschnittId} · Bearbeitung über Hauptmenü → Strecken / Gleise / Weichen bearbeiten."
                        } else ""
                        MaterialAlertDialogBuilder(context)
                            .setTitle(achse.label.takeIf { it.isNotBlank() } ?: "Achse")
                            .setMessage(achse.toGrafikInfoText() + zusatz)
                            .setPositiveButton("Schließen", null)
                            .show()
                    }
                    setOnFestpunkteInfoRequested { kandidaten ->
                        fun zeigePunkt(punkt: TrassenGrafikFestpunkt) {
                            MaterialAlertDialogBuilder(context)
                                .setTitle(punkt.label.ifBlank { "Vermessungspunkt" })
                                .setMessage(punkt.toGrafikPunktInfoText())
                                .setPositiveButton("Schließen", null)
                                .show()
                        }

                        val eindeutig = kandidaten.distinctBy { punkt ->
                            val xCm = kotlin.math.round(punkt.x * 100f).toInt()
                            val yCm = kotlin.math.round(punkt.y * 100f).toInt()
                            val fachId = punkt.label.ifBlank { punkt.punktId ?: punkt.art }.trim().uppercase(Locale.GERMANY)
                            "$fachId|$xCm|$yCm"
                        }
                        if (eindeutig.size == 1) {
                            zeigePunkt(eindeutig.first())
                        } else if (eindeutig.isNotEmpty()) {
                            val labels = eindeutig.map { it.toGrafikPunktAuswahlText() }.toTypedArray()
                            MaterialAlertDialogBuilder(context)
                                .setTitle("Welchen Punkt?")
                                .setItems(labels) { _, which -> zeigePunkt(eindeutig[which]) }
                                .setNegativeButton("Abbrechen", null)
                                .show()
                        }
                    }
                    setOnAchsenLongPressRequested { kandidaten ->
                        fun info(achse: TrassenGrafikAchse) {
                            val zusatz = if (achse.manuellerAbschnittId != null) {
                                "\n\nBetrieblicher Abschnitt #${achse.manuellerAbschnittId}"
                            } else {
                                "\n\nTechnische Roh-TRA: ${achse.name}"
                            }
                            MaterialAlertDialogBuilder(context)
                                .setTitle(achse.label.takeIf { it.isNotBlank() } ?: "Achse")
                                .setMessage(achse.toGrafikInfoText() + zusatz)
                                .setPositiveButton("Schließen", null).show()
                        }

                        fun rawZuAchse(achse: TrassenGrafikAchse): TrassenGrafikAchse? {
                            if (achse.manuellerAbschnittId == null) return achse.takeIf { it.achseId > 0 }
                            return grafikAchsen.firstOrNull { raw ->
                                raw.manuellerAbschnittId == null && raw.achseId > 0 &&
                                    raw.name == achse.name && raw.quelleDatei == achse.quelleDatei
                            }
                        }

                        fun menue(achse: TrassenGrafikAchse) {
                            val abschnittId = achse.manuellerAbschnittId
                            val optionen = mutableListOf("Neuen Abschnitt")
                            if (abschnittId != null) optionen += "Abschnitt bearbeiten"
                            optionen += "Informationen"
                            if (rawZuAchse(achse)?.achsTyp != TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG) optionen += "Roh-TRA technisch zuordnen"

                            MaterialAlertDialogBuilder(context)
                                .setTitle(achse.label.takeIf { it.isNotBlank() } ?: "Achse")
                                .setItems(optionen.toTypedArray()) { _, which ->
                                    when (optionen[which]) {
                                        "Neuen Abschnitt" -> {
                                            dialog.dismiss()
                                            zeigeNeuenAbschnittTypAuswahl()
                                        }
                                        "Abschnitt bearbeiten" -> viewLifecycleOwner.lifecycleScope.launch {
                                            val entity = withContext(Dispatchers.IO) { abschnittId?.let { ServiceLocator.trassenDao.manuellerAbschnittMitId(it) } }
                                            if (entity != null) {
                                                dialog.dismiss()
                                                zeigeAbschnittBearbeitungsMenue(entity.toEntwurf())
                                            }
                                        }
                                        "Informationen" -> info(achse)
                                        "Roh-TRA technisch zuordnen" -> rawZuAchse(achse)?.let { raw ->
                                            zeigeGrafikAchseBearbeiten(raw) {
                                                dialog.dismiss()
                                                zeigeGrafischePruefung()
                                            }
                                        }
                                    }
                                }
                                .setNegativeButton("Abbrechen", null)
                                .show()
                        }

                        val eindeutig = kandidaten.distinctBy { it.achseId }
                        if (eindeutig.size == 1) {
                            menue(eindeutig.first())
                        } else if (eindeutig.isNotEmpty()) {
                            MaterialAlertDialogBuilder(context)
                                .setTitle("Welche Achse / welcher Abschnitt?")
                                .setItems(eindeutig.map { it.toGrafikAuswahlText(eindeutig) }.toTypedArray()) { _, which -> menue(eindeutig[which]) }
                                .setNegativeButton("Abbrechen", null).show()
                        }
                    }
                }
                container.addView(
                    grafik,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                )

                fun zeigeAnzeigeAuswahl() {
                    val achsenListe = anzeigeAchsen.sortedWith(
                        compareBy<TrassenGrafikAchse> { it.achsTyp.anzeigeSortierung() }
                            .thenBy { it.label }
                            .thenBy { it.name }
                    )
                    val punktKonfiguration = listOf(
                        "GVP" to "Festpunkte / Masten",
                        "AP" to "Absteckpunkte",
                        "WEICHENPUNKT" to "Weichenpunkte"
                    ).filter { (art, _) -> festpunkte.any { it.art == art } }

                    val labels = mutableListOf<String>()
                    val achsenZuIndex = mutableMapOf<Int, Long>()
                    val punktArtZuIndex = mutableMapOf<Int, String>()
                    achsenListe.forEach { achse ->
                        val index = labels.size
                        labels += achse.toGrafikAuswahlText(achsenListe)
                        achsenZuIndex[index] = achse.achseId
                    }
                    punktKonfiguration.forEach { (art, text) ->
                        val index = labels.size
                        val anzahl = festpunkte.count { it.art == art }
                        labels += "$text ($anzahl)"
                        punktArtZuIndex[index] = art
                    }

                    val tempAchsen = sichtbareAchseIds.toMutableSet()
                    val tempPunkte = sichtbarePunktArten.toMutableSet()
                    val checked = BooleanArray(labels.size) { index ->
                        achsenZuIndex[index]?.let { it in tempAchsen }
                            ?: punktArtZuIndex[index]?.let { it in tempPunkte }
                            ?: false
                    }

                    MaterialAlertDialogBuilder(context)
                        .setTitle("Anzeige")
                        .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                            achsenZuIndex[which]?.let { id ->
                                if (isChecked) tempAchsen.add(id) else tempAchsen.remove(id)
                            }
                            punktArtZuIndex[which]?.let { art ->
                                if (isChecked) tempPunkte.add(art) else tempPunkte.remove(art)
                            }
                        }
                        .setNegativeButton("Abbrechen", null)
                        .setNeutralButton("Alle anzeigen") { _, _ ->
                            sichtbareAchseIds.clear()
                            sichtbareAchseIds.addAll(alleAchseIds)
                            sichtbarePunktArten.clear()
                            sichtbarePunktArten.addAll(punktArtenVorhanden)
                            letzteGrafikSichtbareRohAchseIds = sichtbareAchseIds.mapNotNull { id -> anzeigeAchsen.firstOrNull { it.achseId == id }?.let(::rawIdFuerAnzeigeAchse) }.toSet()
                            letzteGrafikSichtbarePunktArten = sichtbarePunktArten.toSet()
                            grafik.setSichtbareAchseIds(sichtbareAchseIds.toSet())
                            grafik.setSichtbarePunktArten(sichtbarePunktArten.toSet())
                            grafik.post { grafik.fitToContent() }
                        }
                        .setPositiveButton("Übernehmen") { _, _ ->
                            sichtbareAchseIds.clear()
                            sichtbareAchseIds.addAll(tempAchsen)
                            sichtbarePunktArten.clear()
                            sichtbarePunktArten.addAll(tempPunkte)
                            letzteGrafikSichtbareRohAchseIds = sichtbareAchseIds.mapNotNull { id -> anzeigeAchsen.firstOrNull { it.achseId == id }?.let(::rawIdFuerAnzeigeAchse) }.toSet()
                            letzteGrafikSichtbarePunktArten = sichtbarePunktArten.toSet()
                            grafik.setSichtbareAchseIds(sichtbareAchseIds.toSet())
                            grafik.setSichtbarePunktArten(sichtbarePunktArten.toSet())
                        }
                        .show()
                }

                dialog = android.app.Dialog(context).apply {
                    requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                    setContentView(container)
                    setCancelable(true)
                }
                btnZurueck.setOnClickListener { dialog.dismiss() }
                btnAnzeige.setOnClickListener { zeigeAnzeigeAuswahl() }

                ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                    val bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                    )
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    grafik.post { grafik.fitToContent() }
                    insets
                }

                dialog.setOnShowListener {
                    dialog.window?.let { window ->
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                        val surfaceColor = MaterialColors.getColor(container, com.google.android.material.R.attr.colorSurface)
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                        window.statusBarColor = surfaceColor
                        window.navigationBarColor = surfaceColor
                        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(surfaceColor))
                        window.decorView.setBackgroundColor(surfaceColor)
                        window.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    ViewCompat.requestApplyInsets(container)
                    grafik.post { grafik.fitToContent() }
                }
                dialog.show()
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }.onFailure { throwable ->
                txtStatus?.text = "Grafische Prüfung konnte nicht geladen werden: ${throwable.message ?: throwable.javaClass.simpleName}"
                Toast.makeText(requireContext(), "Grafische Prüfung konnte nicht geladen werden.", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun stelleGrafikAchseOriginalWiederHer(
        achse: TrassenGrafikAchse,
        onWiederhergestellt: () -> Unit
    ) {
        val context = requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle("Original wiederherstellen?")
            .setMessage("Die manuelle Korrektur fuer ${achse.label} wird geloescht. Die Geometrie bleibt unveraendert, aber Typ/Strecke/Gleis/Richtung werden wieder aus dem Original-Achsnamen gebildet.\n\nOriginal: ${achse.name}")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Wiederherstellen") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        val originalTypAusName = achse.name.achsTypAusOriginalNameFallback(achse.achsTyp)
                        val originalTyp = achse.quelleDatei.achsTypAusOriginalNameFallback(originalTypAusName)
                        val originalStrecke = achse.name.streckeAusAchsenName() ?: achse.quelleDatei.streckeAusAchsenName()
                        val originalGleis = if (originalTyp == TrassenAchseEntity.ACHSTYP_GLEIS) {
                            achse.name.gleisAusAchsenName() ?: achse.quelleDatei.gleisAusAchsenName()
                        } else null
                        val originalRichtung = if (originalTyp == TrassenAchseEntity.ACHSTYP_STRECKE) {
                            achse.name.richtungAlsKennzifferAusName() ?: achse.quelleDatei.richtungAlsKennzifferAusName()
                        } else null
                        ServiceLocator.trassenDao.achsKorrekturLoeschen(achse.name, achse.quelleDatei)
                        ServiceLocator.trassenDao.achseMetadatenAktualisieren(
                            achseId = achse.achseId,
                            achsTyp = originalTyp,
                            strecke = originalStrecke,
                            gleis = originalGleis,
                            richtung = originalRichtung,
                            notiz = null
                        )
                    }
                    if (ok > 0) {
                        Toast.makeText(context, "Original wiederhergestellt.", Toast.LENGTH_SHORT).show()
                        statusAktualisieren(mitToast = false)
                        onWiederhergestellt()
                    } else {
                        Toast.makeText(context, "Original konnte nicht wiederhergestellt werden.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun zeigeAchsKorrekturen() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { ServiceLocator.trassenDao.alleAchsKorrekturen() } }
                .onSuccess { korrekturen ->
                    val context = requireContext()
                    val text = if (korrekturen.isEmpty()) {
                        "Noch keine manuellen Achs-Korrekturen gespeichert.\n\nKorrekturen entstehen in der Grafischen Pruefung: Linie antippen → Bearbeiten → Speichern."
                    } else {
                        korrekturen.toAchsKorrekturenText()
                    }
                    val textView = TextView(context).apply {
                        setText(text)
                        textSize = 14f
                        setTextIsSelectable(true)
                        setPadding(36, 24, 36, 24)
                    }
                    val scroll = android.widget.ScrollView(context).apply { addView(textView) }
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Manuelle Achs-Korrekturen")
                        .setView(scroll)
                        .setNegativeButton("Schließen", null)
                        .setNeutralButton("CSV anzeigen") { _, _ ->
                            zeigeAchsKorrekturenCsv(korrekturen)
                        }
                        .show()
                }
                .onFailure { throwable ->
                    Toast.makeText(
                        requireContext(),
                        "Achs-Korrekturen konnten nicht geladen werden: ${throwable.message ?: throwable.javaClass.simpleName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun zeigeAchsKorrekturenCsv(korrekturen: List<TrassenAchseKorrekturEntity>) {
        val context = requireContext()
        val csv = korrekturen.toAchsKorrekturenCsv()
        val textView = TextView(context).apply {
            text = csv.ifBlank { "Keine Korrekturen vorhanden." }
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(28, 20, 28, 20)
        }
        val scroll = android.widget.ScrollView(context).apply { addView(textView) }
        MaterialAlertDialogBuilder(context)
            .setTitle("Achsen_Korrekturen.csv")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .show()
    }


    private fun zeigeGrafikAchseBearbeiten(
        achse: TrassenGrafikAchse,
        onGespeichert: () -> Unit
    ) {
        val context = requireContext()
        if (achse.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG) {
            MaterialAlertDialogBuilder(context)
                .setTitle("WV-Altdaten")
                .setMessage("Weichenverbindungen bleiben lesbar, werden aber nicht als neuer Achstyp angeboten oder neu angelegt.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val typWerte = listOf(
            TrassenAchseEntity.ACHSTYP_STRECKE,
            TrassenAchseEntity.ACHSTYP_GLEIS,
            TrassenAchseEntity.ACHSTYP_WEICHE,
            TrassenAchseEntity.ACHSTYP_UNBEKANNT
        )
        val typTexte = listOf("Strecke", "Gleis", "Weiche", "Unbekannt")
        val richtungWerte = listOf("", "0", "1", "2")
        val richtungTexte = listOf(
            "keine",
            "0 = eingleisige Strecke",
            "1 = km steigend",
            "2 = km absteigend"
        )

        fun indexOder0(liste: List<String>, wert: String?): Int {
            val i = liste.indexOf(wert)
            return if (i >= 0) i else 0
        }

        val typSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, typTexte)
            setSelection(indexOder0(typWerte, achse.achsTyp))
        }
        val streckeEdit = EditText(context).apply {
            hint = "Streckennummer, z. B. 5528"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(achse.strecke ?: achse.name.streckeAusAchsenName().orEmpty())
        }
        val gleisEdit = EditText(context).apply {
            hint = "Gleis, z. B. 302 oder 21"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(achse.gleis ?: achse.name.gleisAusAchsenName().orEmpty())
        }
        val richtungSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, richtungTexte)
            setSelection(indexOder0(richtungWerte, achse.richtung ?: achse.name.richtungAlsKennzifferAusName()))
        }
        val hinweis = TextView(context).apply {
            text = "ACHTUNG: Diese technische Zuordnung gilt für die gesamte importierte Roh-TRA. Sie ist nicht zum Benennen einzelner Gleis-/Streckenabschnitte gedacht. Die Vermessungs-Geometrie bleibt unverändert."
            textSize = 13f
            setPadding(0, dp(8), 0, dp(8))
        }

        fun updateFelderFuerTyp() {
            val typ = typWerte[typSpinner.selectedItemPosition.coerceIn(0, typWerte.lastIndex)]
            val istStrecke = typ == TrassenAchseEntity.ACHSTYP_STRECKE
            val istGleis = typ == TrassenAchseEntity.ACHSTYP_GLEIS
            richtungSpinner.isEnabled = istStrecke
            richtungSpinner.alpha = if (istStrecke) 1f else 0.45f
            gleisEdit.isEnabled = istGleis
            gleisEdit.alpha = if (istGleis) 1f else 0.45f
        }

        typSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateFelderFuerTyp()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        updateFelderFuerTyp()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), 0)
            addView(hinweis)
            addView(TextView(context).apply { text = "Typ" })
            addView(typSpinner)
            addView(TextView(context).apply { text = "Strecke"; setPadding(0, dp(10), 0, 0) })
            addView(streckeEdit)
            addView(TextView(context).apply { text = "Richtung/Kennziffer"; setPadding(0, dp(10), 0, 0) })
            addView(richtungSpinner)
            addView(TextView(context).apply { text = "Gleis"; setPadding(0, dp(10), 0, 0) })
            addView(gleisEdit)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Roh-TRA technisch zuordnen")
            .setMessage("Gilt für die gesamte importierte Roh-TRA.\nOriginal: ${achse.name}")
            .setView(layout)
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("Aus Original") { _, _ -> }
            .setPositiveButton("Speichern") { _, _ -> }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
                val typIndex = indexOder0(typWerte, achse.achsTyp)
                typSpinner.setSelection(typIndex)
                streckeEdit.setText(achse.name.streckeAusAchsenName() ?: achse.quelleDatei.streckeAusAchsenName() ?: achse.strecke.orEmpty())
                gleisEdit.setText(achse.name.gleisAusAchsenName() ?: achse.quelleDatei.gleisAusAchsenName() ?: achse.gleis.orEmpty())
                richtungSpinner.setSelection(
                    indexOder0(
                        richtungWerte,
                        achse.name.richtungAlsKennzifferAusName() ?: achse.quelleDatei.richtungAlsKennzifferAusName()
                    )
                )
                updateFelderFuerTyp()
            }
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                val typNeu = typWerte[typSpinner.selectedItemPosition.coerceIn(0, typWerte.lastIndex)]
                val streckeNeu = streckeEdit.text?.toString()?.trimToNull()
                val gleisNeu = if (typNeu == TrassenAchseEntity.ACHSTYP_GLEIS) {
                    gleisEdit.text?.toString()?.trimToNull()
                } else {
                    null
                }
                val richtungNeu = if (typNeu == TrassenAchseEntity.ACHSTYP_STRECKE) {
                    richtungWerte[richtungSpinner.selectedItemPosition.coerceIn(0, richtungWerte.lastIndex)].trimToNull()
                } else {
                    null
                }

                if (typNeu == TrassenAchseEntity.ACHSTYP_STRECKE && streckeNeu == null) {
                    Toast.makeText(context, "Bei Strecke bitte eine Streckennummer eintragen.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (typNeu == TrassenAchseEntity.ACHSTYP_STRECKE && richtungNeu == null) {
                    Toast.makeText(context, "Bei Strecke bitte Richtung 0, 1 oder 2 auswählen.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val jetzt = System.currentTimeMillis()
                val notiz = "manuell in Grafik geändert · " +
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY).format(Date(jetzt))
                viewLifecycleOwner.lifecycleScope.launch {
                    val aktualisiert = withContext(Dispatchers.IO) {
                        ServiceLocator.trassenDao.achsKorrekturErsetzen(
                            TrassenAchseKorrekturEntity(
                                achseName = achse.name,
                                quelleDatei = achse.quelleDatei,
                                dateiSignatur = achse.dateiSignatur,
                                originalAchsTyp = achse.achsTyp,
                                originalStrecke = achse.name.streckeAusAchsenName() ?: achse.strecke,
                                originalGleis = achse.name.gleisAusAchsenName() ?: achse.gleis,
                                originalRichtung = achse.name.richtungAlsKennzifferAusName() ?: achse.richtung,
                                achsTypNeu = typNeu,
                                streckeNeu = streckeNeu,
                                gleisNeu = gleisNeu,
                                richtungNeu = richtungNeu,
                                erstelltAmEpochMs = jetzt,
                                aktualisiertAmEpochMs = jetzt,
                                notiz = notiz
                            )
                        )
                        ServiceLocator.trassenDao.achseMetadatenAktualisieren(
                            achseId = achse.achseId,
                            achsTyp = typNeu,
                            strecke = streckeNeu,
                            gleis = gleisNeu,
                            richtung = richtungNeu,
                            notiz = notiz
                        )
                    }
                    if (aktualisiert > 0) {
                        Toast.makeText(context, "Achse gespeichert. Grafik wird neu geladen.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        statusAktualisieren(mitToast = false)
                        onGespeichert()
                    } else {
                        Toast.makeText(context, "Achse konnte nicht gespeichert werden.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun ladeBetriebsstellenVorschlaegeAusAssets(): List<BetriebsstellenVorschlag> {
        val map = linkedMapOf<String, BetriebsstellenVorschlag>()

        fun add(codeRaw: String?, nameRaw: String?, typRaw: String?) {
            val code = codeRaw?.replace(" ", "")?.trim().orEmpty().uppercase(Locale.GERMANY)
            val name = nameRaw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
            val typ = typRaw?.trim()?.takeIf { it.isNotBlank() }
            if (code.isBlank() || name.isBlank()) return
            val display = buildString {
                append(code)
                append(" · ")
                append(name)
                typ?.let { append(" · ").append(it) }
            }
            map.putIfAbsent(code, BetriebsstellenVorschlag(code, name, typ, display))
        }

        runCatching {
            requireContext().assets.open("TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json")
                .bufferedReader().use { reader ->
                    val root = JSONObject(reader.readText())
                    val arr = root.optJSONArray("betriebsstellen")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            add(
                                obj.optString("rl100Code"),
                                obj.optString("rl100Langname"),
                                obj.optString("typKurz")
                            )
                        }
                    }
                }
        }

        runCatching {
            requireContext().assets.open("TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json")
                .bufferedReader().use { reader ->
                    val root = JSONObject(reader.readText())
                    val arr = root.optJSONArray("eintraege")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            add(obj.optString("code"), obj.optString("name"), "Bf")
                        }
                    }
                }
        }

        return map.values.sortedWith(compareBy<BetriebsstellenVorschlag> { it.name }.thenBy { it.code })
    }

    private fun bindeBetriebsstellenVorschlaege(
        input: AutoCompleteTextView,
        vorschlaege: List<BetriebsstellenVorschlag>
    ) {
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
                        val query = constraint?.toString().orEmpty().trim()
                        val q = query.uppercase(Locale.GERMANY)
                        val result = vorschlaege.asSequence()
                            .filter { BahnhofSucheUtil.enthaeltTreffer(query, it.code, it.name, it.display) }
                            .sortedWith(
                                compareBy<BetriebsstellenVorschlag> { v ->
                                    val code = v.code.uppercase(Locale.GERMANY)
                                    val name = v.name.uppercase(Locale.GERMANY)
                                    when {
                                        q.isNotBlank() && code == q -> 0
                                        q.isNotBlank() && code.startsWith(q) -> 1
                                        q.isNotBlank() && name.startsWith(q) -> 2
                                        else -> 3
                                    }
                                }.thenBy { it.code.length }
                                    .thenBy { it.code }
                                    .thenBy { it.name }
                            )
                            .take(50)
                            .map { it.display }
                            .toList()
                        return FilterResults().apply {
                            values = result
                            count = result.size
                        }
                    }

                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        items.clear()
                        @Suppress("UNCHECKED_CAST")
                        items.addAll(results?.values as? List<String> ?: emptyList())
                        notifyDataSetChanged()
                    }

                    override fun convertResultToString(resultValue: Any?): CharSequence = resultValue?.toString().orEmpty()
                }
            }
        }

        input.setAdapter(adapter)
        input.setOnItemClickListener { parent, _, position, _ ->
            val display = parent.getItemAtPosition(position)?.toString().orEmpty()
            val treffer = vorschlaege.firstOrNull { it.display == display }
            input.tag = treffer
            treffer?.let { BahnhofSucheUtil.speichereLetzteEingabe(requireContext(), it.display) }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val gewaehlt = input.tag as? BetriebsstellenVorschlag
                if (gewaehlt != null && gewaehlt.display != s?.toString()) input.tag = null
            }
        })
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !input.text.isNullOrBlank()) input.post { input.showDropDown() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statusAktualisieren(mitToast: Boolean) {
        txtStatus?.text = getString(R.string.import_export_v19_status_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { ServiceLocator.trassenRepository.ladeUebersicht() }
                .onSuccess { uebersicht ->
                    // Hauptmaske bleibt bewusst kompakt. Ausfuehrliche Details werden in einem eigenen Dialog geoeffnet.
                    txtStatus?.text = uebersicht.toStatusText(detailsAnzeigen = false)
                    if (mitToast) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.import_export_v19_status_refreshed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .onFailure { throwable ->
                    txtStatus?.text = getString(
                        R.string.import_export_v19_status_error,
                        throwable.message ?: throwable.javaClass.simpleName
                    )
                }
        }
    }

    private fun zeigeStatusDetailsDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { ServiceLocator.trassenRepository.ladeUebersicht() }
                .onSuccess { uebersicht ->
                    txtStatus?.text = uebersicht.toStatusText(detailsAnzeigen = false)
                    val context = requireContext()
                    val detailsText = TextView(context).apply {
                        text = uebersicht.toStatusText(detailsAnzeigen = true)
                        textSize = 14f
                        setTextIsSelectable(true)
                        setPadding(36, 24, 36, 24)
                    }
                    val scrollView = android.widget.ScrollView(context).apply {
                        addView(detailsText)
                    }
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Streckendaten Details")
                        .setView(scrollView)
                        .setPositiveButton("Schließen", null)
                        .show()
                }
                .onFailure { throwable ->
                    Toast.makeText(
                        requireContext(),
                        "Details konnten nicht geladen werden: ${throwable.message ?: throwable.javaClass.simpleName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun TrassenUebersicht.toStatusText(detailsAnzeigen: Boolean = false): String {
        if (istLeer && anzahlImportStatus == 0 && anzahlAchsKorrekturen == 0 && anzahlKmAnker == 0) {
            return getString(R.string.import_export_v19_status_empty)
        }

        val geometrieOk = anzahlAchsen > 0 && anzahlSamplepunkte > 0
        val gvpOk = anzahlGvpPunkte > 0
        val gpsOk = anzahlSamplepunkte > 0 && anzahlSamplepunkteMitGps >= anzahlSamplepunkte
        val kmStatusGesamt = kmAutomatischAchsen + kmManuellAchsen + kmFehltAchsen + kmUnplausibelAchsen

        val sb = StringBuilder()
        paketStatusZeile()?.let { sb.appendLine(it) }
        sb.appendLine(if (geometrieOk) "✓ Geometrie: $anzahlAchsen Achsen" else "! Geometrie: TRA/Achspunkte fehlen")
        if (kmStatusGesamt > 0) {
            sb.appendLine("KM-Status: Grün $kmAutomatischAchsen · Orange $kmManuellAchsen · Rot ${kmFehltAchsen + kmUnplausibelAchsen}")
        } else {
            sb.appendLine("! KM-Status: keine Achsen bewertet")
        }
        sb.appendLine(if (gvpOk) "✓ Festpunkte/GVP: $anzahlGvpPunkte" else "– Festpunkte/GVP: keine")
        sb.appendLine(if (gpsOk) "✓ GPS-Geometrie: $anzahlSamplepunkteMitGps/$anzahlSamplepunkte" else "! GPS-Geometrie: $anzahlSamplepunkteMitGps/$anzahlSamplepunkte")
        if (anzahlAbsteckpunkte > 0) sb.appendLine("✓ AP/KOR-Punkte: $anzahlAbsteckpunkte")
        if (anzahlKmAnker > 0) sb.appendLine("✓ Manuelle KM-Anker: $anzahlKmAnker")
        if (anzahlAchsKorrekturen > 0) sb.appendLine("✓ Achs-Korrekturen: $anzahlAchsKorrekturen")
        val weichenAnzahl = achsen.count { it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE }
        val wvAnzahl = achsen.count { it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG }
        val weichenWvAchsen = achsen.filter {
            it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE || it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG
        }
        val streckendatenZuordnungen = ladeStreckendatenZuordnungen()
        val betriebsstelleZugeordnet = weichenWvAchsen.count { achse ->
            findeStreckendatenZuordnung(achse, streckendatenZuordnungen) != null
        }
        val dbNameZugeordnet = weichenWvAchsen.count { achse ->
            ladeWeichenZuordnung(achse.name)?.istZugeordnet() == true
        }
        if (weichenAnzahl + wvAnzahl > 0) {
            sb.appendLine("✓ Weichen/WV: $weichenAnzahl W / $wvAnzahl WV · Bst $betriebsstelleZugeordnet/${weichenWvAchsen.size} · DB-Name $dbNameZugeordnet/${weichenWvAchsen.size}")
        }

        if (!detailsAnzeigen) {
            return sb.toString().trimEnd()
        }

        sb.appendLine()
        sb.appendLine("Details:")
        sb.appendLine("• Achsen: $anzahlAchsen · Achspunkte: $anzahlSamplepunkte")
        sb.appendLine("• GRA: $anzahlGraPunkte · GVP: $anzahlGvpPunkte")
        sb.appendLine("• KM-Achsen: Grün $kmAutomatischAchsen · Orange $kmManuellAchsen · Rot ${kmFehltAchsen + kmUnplausibelAchsen}")
        sb.appendLine("• Manuelle KM-Anker: $anzahlKmAnker")

        val weichen = achsen
            .filter { it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE }
            .sortedBy { it.name }
        val wvAchsen = achsen
            .filter { it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG }
            .sortedBy { it.name }
        if (weichen.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Weichen:")
            weichen.take(12).forEach { achse ->
                sb.appendLine(achse.toWeichenStatusLine())
            }
            if (weichen.size > 12) sb.appendLine("• … ${weichen.size - 12} weitere Weiche(n)")
        }
        if (wvAchsen.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Weichenverbindungen:")
            wvAchsen.take(12).forEach { achse ->
                sb.appendLine(achse.toWeichenStatusLine())
            }
            if (wvAchsen.size > 12) sb.appendLine("• … ${wvAchsen.size - 12} weitere WV")
        }
        if (weichen.isNotEmpty() || wvAchsen.isNotEmpty()) {
            sb.appendLine("• Hinweis: SW/WV-Dateinamen sind Roh-Achsnamen. DB-Weichen wie 52W06/52W26 koennen ueber Live-GPS manuell zugeordnet werden; Originalname bleibt erhalten.")
        }

        if (graGruppen.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(getString(R.string.import_export_v19_status_gra_groups))
            graGruppen.take(4).forEach { gruppe ->
                sb.appendLine(gruppe.toGraStatusLine())
            }
        }

        if (gvpGruppen.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(getString(R.string.import_export_v19_status_gvp_groups))
            gvpGruppen.take(4).forEach { gruppe ->
                sb.appendLine(gruppe.toGvpStatusLine())
            }
        }

        if (absteckpunktGruppen.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(getString(R.string.import_export_v19_status_kor_groups))
            absteckpunktGruppen.take(4).forEach { gruppe ->
                sb.appendLine(gruppe.toKorStatusLine())
            }
        }

        if (achsKorrekturen.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Manuelle Achs-Korrekturen:")
            achsKorrekturen.take(8).forEach { korrektur ->
                sb.appendLine("• ${korrektur.achseName} → ${korrektur.zielKurzText()}")
            }
            if (achsKorrekturen.size > 8) sb.appendLine("• … ${achsKorrekturen.size - 8} weitere Korrektur(en)")
        }

        if (achsen.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(getString(R.string.import_export_v19_status_axis_types))
            achsen.gruppiereNachTyp().forEach { (typ, anzahl) ->
                sb.appendLine("• ${typ.achsTypLabel()}: $anzahl")
            }

            sb.appendLine()
            sb.appendLine(getString(R.string.import_export_v19_status_axis_list))
            achsen.take(8).forEach { achse ->
                sb.appendLine(achse.toAchsenStatusLine())
            }
            if (achsen.size > 8) {
                sb.appendLine("• … ${achsen.size - 8} weitere Achse(n)")
            }
        }

        if (letzteStatus.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(getString(R.string.import_export_v19_status_last_imports))
            letzteStatus.take(4).forEach { status ->
                sb.appendLine(status.toStatusLine())
            }
        }
        return sb.toString().trimEnd()
    }

    private fun TrassenUebersicht.toBerechnungsPruefungText(): String {
        val achsdatenOk = anzahlAchsen > 0 && anzahlGraPunkte > 0
        val gvpOk = anzahlGvpPunkte > 0
        val gpsOk = anzahlSamplepunkte > 0 && anzahlSamplepunkteMitGps >= anzahlSamplepunkte
        val gleisKmOk = anzahlGleisSamplepunkte > 0 && anzahlGleisSamplepunkteMitKm >= anzahlGleisSamplepunkte
        val alleKmOk = anzahlSamplepunkte > 0 && anzahlSamplepunkteMitKm >= anzahlSamplepunkte
        return buildString {
            appendLine("Pruefung:")
            appendLine(if (achsdatenOk) "✓ Achsdaten vorhanden" else "! Achsdaten unvollstaendig")
            appendLine(if (gvpOk) "✓ GVP vorhanden" else "! GVP fehlt")
            appendLine(if (gpsOk) "✓ GPS vollstaendig" else "! GPS unvollstaendig: $anzahlSamplepunkteMitGps/$anzahlSamplepunkte")
            appendLine(
                if (gleisKmOk) {
                    if (alleKmOk) "✓ KM vollstaendig" else "✓ KM fuer Gleisachsen vollstaendig"
                } else {
                    "! KM fuer Gleisachsen unvollstaendig: $anzahlGleisSamplepunkteMitKm/$anzahlGleisSamplepunkte"
                }
            )
            append(if (anzahlAbsteckpunkte > 0) "✓ KOR vorhanden" else "– KOR optional")
        }
    }

    private fun List<TrassenAchseEntity>.gruppiereNachTyp(): List<Pair<String, Int>> {
        val reihenfolge = listOf(
            TrassenAchseEntity.ACHSTYP_GLEIS,
            TrassenAchseEntity.ACHSTYP_STRECKE,
            TrassenAchseEntity.ACHSTYP_WEICHE,
            TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG,
            TrassenAchseEntity.ACHSTYP_UNBEKANNT
        )
        val gruppen = groupingBy { it.achsTyp }.eachCount()
        return reihenfolge.mapNotNull { typ ->
            val count = gruppen[typ] ?: return@mapNotNull null
            typ to count
        }
    }

    private fun TrassenUebersicht.paketStatusZeile(): String? {
        val strecken = (achsen.mapNotNull { it.strecke?.trim() } +
            gvpGruppen.mapNotNull { it.strecke?.trim() } +
            graGruppen.mapNotNull { it.strecke?.trim() })
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        if (strecken.isEmpty()) return null

        val stand = letzteStatus.maxOfOrNull { it.importiertAmEpochMs }
            ?.let { SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(it)) }
            ?: "unbekannt"
        val streckenText = strecken.joinToString(" · ")
        return "Streckendaten: $streckenText · Stand $stand"
    }


    private fun TrassenAchseEntity.toGrafikLabel(): String {
        return when (achsTyp) {
            TrassenAchseEntity.ACHSTYP_GLEIS -> gleis?.takeIf { it.isNotBlank() }?.let { "Gl $it" } ?: name.gleisAusAchsenName()?.let { "Gl $it" } ?: name.toKurzerAchsenName()
            TrassenAchseEntity.ACHSTYP_STRECKE -> {
                val str = effektiveStrecke()
                val kennziffer = effektiveRichtungKennziffer()
                str?.let { streckenNummer ->
                    kennziffer?.let { "Str $streckenNummer-$it" } ?: "Str $streckenNummer"
                } ?: name.toKurzerAchsenName()
            }
            TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> name.toWeichenKurzName()
            TrassenAchseEntity.ACHSTYP_WEICHE -> name.toWeichenKurzName()
            else -> name.toKurzerAchsenName()
        }
    }

    private fun TrassenAchseEntity.effektiveStrecke(): String? =
        strecke?.trimToNull() ?: name.streckeAusAchsenName()

    private fun TrassenAchseEntity.effektiveRichtungKennziffer(): String? =
        richtung.richtungAlsKennziffer() ?: name.richtungAlsKennzifferAusName()

    private fun String.anzeigeSortierung(): Int = when (this) {
        TrassenAchseEntity.ACHSTYP_STRECKE -> 0
        TrassenAchseEntity.ACHSTYP_GLEIS -> 1
        TrassenAchseEntity.ACHSTYP_WEICHE -> 2
        TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> 3
        else -> 4
    }

    private fun TrassenGrafikAchse.toGrafikAuswahlText(alle: List<TrassenGrafikAchse>): String {
        val basis = label.takeIf { it.isNotBlank() } ?: name
        val gleicheLabels = alle.count { it.label == label && it.achseId != achseId }
        val richtungsQuelle = richtungQuelle?.trimToNull()
        val quelleZusatz = if (achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE && richtungsQuelle != null) {
            " · Quelle $richtungsQuelle"
        } else ""
        val nameZusatz = if (gleicheLabels > 0 && !name.equals(basis, ignoreCase = true)) " · $name" else ""
        return basis + quelleZusatz + nameZusatz
    }

    private fun TrassenGrafikFestpunkt.toGrafikPunktAuswahlText(): String {
        val km = kmMeter?.let { " · km ${formatKmMeter(it)}" }.orEmpty()
        val artText = when (art.uppercase(Locale.GERMANY)) {
            "GVP", "FESTPUNKT" -> "Festpunkt"
            "WEICHENPUNKT" -> "Weichenpunkt"
            else -> "Absteckpunkt"
        }
        return "${label.ifBlank { punktId ?: artText }}$km · $artText"
    }

    private fun TrassenGrafikFestpunkt.toGrafikPunktInfoText(): String = buildString {
        val artText = when (art.uppercase(Locale.GERMANY)) {
            "GVP", "FESTPUNKT" -> "Festpunkt / Mast"
            "WEICHENPUNKT" -> "Weichenpunkt"
            else -> "Absteckpunkt"
        }
        appendLine("Art: $artText")
        punktId?.takeIf { it.isNotBlank() && it != label }?.let { appendLine("Punkt-ID: $it") }
        strecke?.takeIf { it.isNotBlank() }?.let { appendLine("Strecke: $it") }
        gleis?.takeIf { it.isNotBlank() }?.let { appendLine("Gleis: $it") }
        kmMeter?.let { appendLine("KM aus Datei: ${formatKmMeter(it)}") }
        hoeheM?.let { appendLine("Höhe: ${format1(it)} m") }
        typ?.takeIf { it.isNotBlank() }?.let { appendLine("Typ/Code: $it") }
        appendLine("DB_REF: ${format1(x.toDouble())} / ${format1(y.toDouble())}")
        quelleDatei?.takeIf { it.isNotBlank() }?.let { append("Quelle: $it") }
    }.trimEnd()

    private fun TrassenGrafikAchse.toGrafikInfoText(): String {
        val stationVon = punkte.firstOrNull()?.station
        val stationBis = punkte.lastOrNull()?.station
        val kmWerte = punkte.mapNotNull { it.kmMeter }
        val kmText = if (kmWerte.isNotEmpty()) {
            val minKm = kmWerte.minOrNull()
            val maxKm = kmWerte.maxOrNull()
            when {
                minKm != null && maxKm != null && kotlin.math.abs(maxKm - minKm) >= 0.5 ->
                    "km ${formatKmMeter(minKm)} bis km ${formatKmMeter(maxKm)}"
                minKm != null -> "km ${formatKmMeter(minKm)}"
                else -> "nicht verknuepft"
            }
        } else {
            "nicht verknuepft"
        }
        val stationText = if (stationVon != null && stationBis != null) {
            "${format1(stationVon)} m bis ${format1(stationBis)} m"
        } else {
            "unbekannt"
        }
        val streckeText = strecke?.takeIf { it.isNotBlank() } ?: name.streckeAusAchsenName() ?: "-"
        val gleisText = gleis?.takeIf { it.isNotBlank() } ?: name.gleisAusAchsenName() ?: "-"
        val richtungKennziffer = richtung.richtungAlsKennziffer() ?: name.richtungAlsKennzifferAusName()
        val richtungQuelleText = richtungQuelle?.trimToNull()
            ?.takeIf { quelle -> richtungKennziffer != null && quelle != richtungKennziffer }
        val richtungText = if (achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE) {
            richtungKennziffer?.let { kennziffer ->
                richtungQuelleText?.let { "$kennziffer (Quelle: $it)" } ?: kennziffer
            } ?: "-"
        } else {
            "keine (nur Strecke hat 0/1/2)"
        }
        return buildString {
            appendLine("Typ: ${achsTyp.achsTypLabel()}")
            appendLine("Anzeige: ${label.takeIf { it.isNotBlank() } ?: name}")
            appendLine("Achse: $name")
            appendLine("Strecke: $streckeText")
            appendLine("Richtung/Kennziffer: $richtungText")
            appendLine("Gleis: $gleisText")
            appendLine("Punkte: ${punkte.size}")
            appendLine("Station: $stationText")
            val kmStatusText = when (kmStatus) {
                TrassenKmStatusLogik.STATUS_AUTOMATISCH -> "GRÜN · automatisch und plausibel"
                TrassenKmStatusLogik.STATUS_MANUELL -> "ORANGE · manuell kilometriert"
                TrassenKmStatusLogik.STATUS_FEHLT -> "ROT · keine belastbare KM"
                TrassenKmStatusLogik.STATUS_UNPLAUSIBEL -> "ROT · KM unplausibel"
                else -> kmStatus
            }
            appendLine("KM-Status: $kmStatusText")
            appendLine("KM-Status Grund: $kmStatusGrund")
            appendLine("KM: $kmText")
            if (manuelleAnker.isNotEmpty()) {
                appendLine("Manuelle KM-Anker: ${manuelleAnker.size}")
                manuelleAnker.sortedBy { it.punktNummer }.forEach { anker ->
                    appendLine("  P${anker.punktNummer}: km ${String.format(Locale.GERMANY, "%.3f", anker.kmMeter / 1000.0)} · Station ${format1(anker.station)} m")
                }
            }
            appendLine("Quelldatei: $quelleDatei")
            appendLine("Manuelle Achs-Korrektur: ${if (istManuell) "ja" else "nein"}")
            notiz?.takeIf { it.isNotBlank() }?.let { appendLine("Notiz: $it") }
            append("Quelle: berechnete Achspunkte aus Import")
        }
    }

    private fun String?.richtungAlsKennziffer(): String? {
        val wert = this?.trim()?.uppercase(Locale.GERMANY).orEmpty()
        return when (wert) {
            "0", "S" -> "0"
            "1", "L", "SL" -> "1"
            "2", "R" -> "2"
            else -> null
        }
    }

    private fun String.richtungAlsKennzifferAusName(): String? =
        richtungsRohwertAusAchsenName().richtungAlsKennziffer()

    private fun String.richtungsRohwertAusAchsenName(): String? {
        findeStreckeUndQuellrichtung(this)?.second?.let { return it }
        val upper = uppercase(Locale.GERMANY)
        Regex("\\d{3}[-_]\\d{3}(SL|[LRS])(?:[_\\s.-]|$)")
            .find(upper)?.groupValues?.getOrNull(1)?.let { return it }
        val tokens = upper
            .replace(Regex("[^A-Z0-9]+"), " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return when {
            tokens.contains("SL") -> "SL"
            tokens.contains("L") -> "L"
            tokens.contains("R") -> "R"
            tokens.contains("S") -> "S"
            tokens.contains("0") -> "0"
            tokens.contains("1") -> "1"
            tokens.contains("2") -> "2"
            else -> null
        }
    }

    private fun String.streckeAusAchsenName(): String? {
        findeStreckeUndQuellrichtung(this)?.first?.let { return it }
        Regex("(?i)(?<!\\d)([1-9]\\d{3})[_-]?(?:GL|GLEIS)[_\\s-]*\\d+[A-Z]?")
            .find(this)?.groupValues?.getOrNull(1)?.let { return it }
        if (Regex("(?i)(?:GL|GLEIS)[_\\s-]*\\d+[A-Z]?").containsMatchIn(this)) return null
        return Regex("(?<!\\d)([1-9]\\d{3})(?!\\d)")
            .findAll(this)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun String.gleisAusAchsenName(): String? =
        Regex("(?i)(?:GL|GLEIS)[_\\s-]*(\\d+[A-Z]?)").find(this)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("(?i)(?:^|[_\\s-])G[_\\s-]*(\\d+[A-Z]?)(?:[_\\s.-]|$)").find(this)?.groupValues?.getOrNull(1)?.trim()

    private fun String.achsTypAusOriginalNameFallback(fallback: String): String {
        val upper = uppercase(Locale.GERMANY)
        return when {
            Regex("(?i)(^|[/_\\s-])WVB[_\\s-]*\\d+[-_]\\d+").containsMatchIn(this) -> TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG
            Regex("(?i)(^|[/_\\s-])WV[_\\s-]*\\d+[-_]\\d+").containsMatchIn(this) -> TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG
            Regex("(?i)(^|[/_\\s-])SW[_\\s-]*\\d+W?").containsMatchIn(this) -> TrassenAchseEntity.ACHSTYP_WEICHE
            Regex("(?i)(^|[/_\\s-])W\\d+[A-Z]?").containsMatchIn(this) && upper.contains("WEICHE") -> TrassenAchseEntity.ACHSTYP_WEICHE
            Regex("(?i)(?:GL|GLEIS)[_\\s-]*\\d+[A-Z]?").containsMatchIn(this) -> TrassenAchseEntity.ACHSTYP_GLEIS
            Regex("(?i)(?:^|[_\\s-])G[_\\s-]*\\d+[A-Z]?(?:[_\\s.-]|$)").containsMatchIn(this) -> TrassenAchseEntity.ACHSTYP_GLEIS
            streckeAusAchsenName() != null && richtungAlsKennzifferAusName() != null -> TrassenAchseEntity.ACHSTYP_STRECKE
            else -> fallback
        }
    }

    private fun List<TrassenAchseKorrekturEntity>.toAchsKorrekturenText(): String = buildString {
        appendLine("Gespeicherte Korrekturen: $size")
        appendLine("Diese Ebene bleibt bei Neuimport derselben Achse erhalten. Die Original-Geometrie wird nicht veraendert.")
        appendLine()
        this@toAchsKorrekturenText.forEachIndexed { index: Int, k: TrassenAchseKorrekturEntity ->
            appendLine("${index + 1}. ${k.achseName}")
            appendLine("   Quelle: ${k.quelleDatei.ifBlank { "-" }}")
            appendLine("   Ziel: ${k.zielKurzText()} · Typ ${k.achsTypNeu.achsTypLabel()}")
            k.notiz?.takeIf { notizText: String -> notizText.isNotBlank() }?.let { notizText: String ->
                appendLine("   Notiz: $notizText")
            }
        }
    }.trimEnd()

    private fun List<TrassenAchseKorrekturEntity>.toAchsKorrekturenCsv(): String {
        fun esc(value: String?): String {
            val v = value.orEmpty()
            return if (v.contains(';') || v.contains('"') || v.contains('\n')) {
                "\"" + v.replace("\"", "\"\"") + "\""
            } else v
        }
        return buildString {
            appendLine("achseName;quelleDatei;originalTyp;originalStrecke;originalGleis;originalRichtung;neuTyp;neuStrecke;neuGleis;neuRichtung;notiz;aktualisiertEpochMs")
            this@toAchsKorrekturenCsv.forEach { k ->
                appendLine(listOf(
                    k.achseName,
                    k.quelleDatei,
                    k.originalAchsTyp,
                    k.originalStrecke,
                    k.originalGleis,
                    k.originalRichtung,
                    k.achsTypNeu,
                    k.streckeNeu,
                    k.gleisNeu,
                    k.richtungNeu,
                    k.notiz,
                    k.aktualisiertAmEpochMs.toString()
                ).joinToString(";") { esc(it) })
            }
        }
    }

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotBlank() }


    private fun String.toKurzerAchsenName(): String = this
        .substringBefore("_ER0")
        .substringBefore("_N00")
        .replace("_", " ")
        .trim()

    private fun TrassenAchseEntity.toWeichenVerwaltungLine(): String {
        val laengeText = if (stationVon != null && stationBis != null) {
            "${format1(stationBis - stationVon)} m"
        } else {
            "Laenge ?"
        }
        val zuordnung = ladeWeichenZuordnung(name)
        val typText = if (achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG) "WV" else "W"
        val nameText = if (zuordnung != null && zuordnung.istZugeordnet()) {
            "${name.toWeichenKurzName()} → ${zuordnung.anzeigeName()} · manuell"
        } else {
            "${name.toWeichenKurzName()} · nicht zugeordnet"
        }
        val lageText = zuordnung?.lage?.takeIf { it.isNotBlank() && it != "unbekannt" }?.let { " · $it" } ?: ""
        val waText = zuordnung?.waKmMeter?.let { " · WA ${formatKmOderStationMitLabel(it)}" } ?: ""
        return "$typText $nameText · $laengeText$lageText$waText"
    }

    private fun TrassenAchseEntity.toWeichenStatusLine(): String {
        val laengeText = if (stationVon != null && stationBis != null) {
            "${format1(stationBis - stationVon)} m"
        } else {
            "Laenge ?"
        }
        val elementeText = "$anzahlElemente El."
        val punkteText = if (anzahlSamplepunkte > 0) " · $anzahlSamplepunkte Pkt." else ""
        val zuordnung = ladeWeichenZuordnung(name)
        val betriebsstelle = findeStreckendatenZuordnung(this)?.let { " · Bst ${it.anzeigeName()}" } ?: " · Bst nicht zugeordnet"
        val dbText = if (zuordnung != null && zuordnung.istZugeordnet()) {
            " → ${zuordnung.anzeigeName()} · DB-Name manuell"
        } else {
            " · DB-Name offen"
        }
        val nameText = "${name.toWeichenKurzName()}$betriebsstelle$dbText"
        val lageText = zuordnung?.lage?.takeIf { it.isNotBlank() && it != "unbekannt" }?.let { " · $it" } ?: ""
        val waText = zuordnung?.waKmMeter?.let { " · WA ${formatKmOderStationMitLabel(it)}" } ?: ""
        return "• $nameText · $laengeText · $elementeText$punkteText$lageText$waText"
    }

    private fun TrassenAchseEntity.toAchsenStatusLine(): String {
        val teile = mutableListOf<String>()
        effektiveStrecke()?.let { teile += "Str $it" }
        gleis?.takeIf { it.isNotBlank() }?.let { teile += "Gl $it" }
        effektiveRichtungKennziffer()?.let { teile += "Richtung $it" }
        val station = if (stationVon != null && stationBis != null) {
            val laenge = stationBis - stationVon
            "${format1(laenge)} m"
        } else {
            null
        }
        station?.let { teile += it }
        teile += "$anzahlElemente El."
        if (anzahlSamplepunkte > 0) teile += "$anzahlSamplepunkte Pkt."
        val zusatz = if (teile.isEmpty()) "" else " – ${teile.joinToString(" | ")}"
        return "• [${achsTyp.achsTypLabel()}] $name$zusatz"
    }

    private fun GraUebersichtRow.toGraStatusLine(): String {
        val streckeText = strecke?.takeIf { it.isNotBlank() } ?: "ohne Strecke"
        val gleisText = gleis?.takeIf { it.isNotBlank() } ?: "ohne Gleis"
        val kmText = if (kmVon != null && kmBis != null) {
            "${formatKmOderStation(kmVon)}–${formatKmOderStation(kmBis)}"
        } else {
            "ohne km/Station"
        }
        val hoeheText = if (hoeheMin != null && hoeheMax != null) {
            "H ${format1(hoeheMin)}–${format1(hoeheMax)} m"
        } else {
            "ohne Hoehe"
        }
        return "• $quelleDatei – Str $streckeText / Gl $gleisText – $anzahl Punkte – $kmText – $hoeheText"
    }

    private fun GvpUebersichtRow.toGvpStatusLine(): String {
        val streckeText = strecke?.takeIf { it.isNotBlank() } ?: "ohne Strecke"
        val kmText = if (kmVon != null && kmBis != null) {
            "km ${formatKm(kmVon)}–${formatKm(kmBis)}"
        } else {
            "ohne km"
        }
        return "• Strecke $streckeText – $anzahl Punkte – $kmText"
    }

    private fun AbsteckpunktUebersichtRow.toKorStatusLine(): String {
        val gleisText = gleis?.takeIf { it.isNotBlank() } ?: "ohne Gleis"
        val vonBis = if (!punktVon.isNullOrBlank() && !punktBis.isNullOrBlank()) {
            "Punkte $punktVon–$punktBis"
        } else {
            "ohne Punktbereich"
        }
        return "• $gleisText – $anzahl Punkte – $vonBis – $quelleDatei"
    }


    private fun List<TrassenNaheBerechner.Ergebnis>.toNaheStatusText(
        liveLat: Double? = null,
        liveLon: Double? = null,
        accuracyM: Float? = null,
        fixCount: Int? = null,
        durationMs: Long? = null,
        bestAccuracyM: Float? = null,
        achsenById: Map<Long, TrassenAchseEntity> = emptyMap()
    ): String {
        val alleKandidaten = this
        val erster = alleKandidaten.firstOrNull() ?: return getString(R.string.import_export_v19_nahe_live_no_axis_short)
        val genauigkeit = accuracyM?.toDouble()
        val besteGleisAchse = alleKandidaten.firstOrNull { it.achsTyp == TrassenAchseEntity.ACHSTYP_GLEIS }
        val naechstesAnderesGleis = besteGleisAchse?.let { bestes ->
            alleKandidaten
                .filter { it.achsTyp == TrassenAchseEntity.ACHSTYP_GLEIS && it.achseId != bestes.achseId }
                .minByOrNull { it.abstandM }
        }
        val gleisToleranz = maxOf(3.0, genauigkeit ?: 5.0)
        val naheGleise = besteGleisAchse?.let { bestes ->
            alleKandidaten.filter {
                it.achsTyp == TrassenAchseEntity.ACHSTYP_GLEIS &&
                    it.abstandM <= bestes.abstandM + gleisToleranz
            }
        }.orEmpty()
        val mehrdeutig = naheGleise.size > 1

        return buildString {
            appendLine(getString(R.string.import_export_v19_nahe_test_title))
            appendLine(erster.toKompakteErgebnisZeile())
            if (liveLat != null && liveLon != null) {
                val gpsZusatz = buildString {
                    accuracyM?.let { append(" · Genauigkeit ${format1(it.toDouble())} m") }
                    if (fixCount != null && durationMs != null) {
                        append(" · ${fixCount} Fixes / ${durationMs / 1000}s")
                    }
                    bestAccuracyM?.let { append(" · bester Fix ${format1(it.toDouble())} m") }
                }
                appendLine("GPS: ${formatGps(liveLat)}, ${formatGps(liveLon)}$gpsZusatz")
            }
            appendLine()

            if (mehrdeutig) {
                appendLine("Hinweis: Gleis nicht eindeutig innerhalb der GPS-Toleranz.")
                naheGleise.take(3).forEachIndexed { index, kandidat ->
                    appendLine("${index + 1}. ${kandidat.toKompakteErgebnisZeile()}")
                }
            } else if (besteGleisAchse != null && erster.achseId == besteGleisAchse.achseId) {
                appendLine("Gleis wahrscheinlich eindeutig.")
                naechstesAnderesGleis?.let { anderes ->
                    appendLine("Naechstes anderes Gleis: ${anderes.toKompakteErgebnisZeile()}")
                }
            }

            appendLine()
            appendLine("Details:")
            appendLine("Achse: ${erster.achseName}")
            appendLine("Station: ${format1(erster.station)} m")
            appendLine("Proj. GPS: ${formatGps(erster.projLat)}, ${formatGps(erster.projLon)}")
            erster.kmQuelle?.takeIf { it.isNotBlank() }?.let { appendLine("KM-Quelle: ${it.toKurzeKmQuelle()}") }
            appendLine("Test neutral: Baustelle/Unterbaustelle filtert nicht.")

            val naheWeiche = alleKandidaten
                .filter { it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHE || it.achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG }
                .minByOrNull { it.abstandM }
            if (naheWeiche != null && naheWeiche.abstandM <= 80.0) {
                appendLine("Nahe Weiche/WV: ${naheWeiche.toWeichenNaheZeile(achsenById)}")
            }

            val weitere = alleKandidaten.drop(1).filter { it.achsTyp != TrassenAchseEntity.ACHSTYP_STRECKE }
            if (weitere.isNotEmpty()) {
                appendLine()
                appendLine("Weitere nahe Achsen:")
                weitere.take(4).forEachIndexed { index, kandidat ->
                    appendLine("${index + 2}. ${kandidat.toKompakteErgebnisZeile()}")
                }
            }

            val streckenInfo = alleKandidaten.drop(1).filter { it.achsTyp == TrassenAchseEntity.ACHSTYP_STRECKE }
            if (streckenInfo.isNotEmpty()) {
                appendLine()
                appendLine("Streckenachsen nur Info:")
                streckenInfo.take(2).forEachIndexed { index, kandidat ->
                    appendLine("S${index + 1}. ${kandidat.toKompakteErgebnisZeile()}")
                }
            }
        }.trimEnd()
    }

    private fun TrassenNaheBerechner.Ergebnis.toWeichenNaheZeile(
        achsenById: Map<Long, TrassenAchseEntity> = letzteWeichenAchsenById
    ): String {
        val typText = if (achsTyp == TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG) "WV" else "W"
        val zuordnung = ladeWeichenZuordnung(achseName)
        val achse = achsenById[achseId]
        val betriebsstelle = achse?.let { findeStreckendatenZuordnung(it)?.anzeigeName() }
        val nameText = if (zuordnung != null && zuordnung.istZugeordnet()) {
            "${zuordnung.anzeigeName()} (Roh: ${achseName.toWeichenKurzName()}${betriebsstelle?.let { ", Bst $it" } ?: ""})"
        } else {
            "${achseName.toWeichenKurzName()}${betriebsstelle?.let { " · Bst $it" } ?: " · Bst nicht zugeordnet"} · DB-Name offen"
        }
        val meterText = weichenMeterLangText(achse)
        val kmText = kmMeter?.let { " · ${formatKmOderStationMitLabel(it)}" } ?: ""
        val lageText = zuordnung?.lage?.takeIf { it.isNotBlank() && it != "unbekannt" }?.let { " · $it" } ?: ""
        val waText = zuordnung?.waKmMeter?.let { " · WA ${formatKmOderStationMitLabel(it)}" } ?: ""
        return "$typText $nameText · $meterText$kmText · Abstand ${format1(abstandM)} m$lageText$waText"
    }

    private fun TrassenNaheBerechner.Ergebnis.toWeicheZuordnenButtonText(): String {
        val achse = letzteWeichenAchsenById[achseId]
        val kurzName = weichenAnzeigeName()
        val meterText = weichenMeterKurzText(achse)
        return "Weiche zuordnen: $kurzName · $meterText"
    }

    private fun TrassenNaheBerechner.Ergebnis.weichenMeterKurzText(achse: TrassenAchseEntity?): String {
        val laenge = achse?.achsenLaengeM()
        val stationVon = achse?.stationVon ?: 0.0
        return if (laenge != null && laenge > 0.0) {
            val position = (station - stationVon).coerceIn(0.0, laenge)
            val bisWe = (laenge - position).coerceAtLeast(0.0)
            "WA/0 ${format1(position)} m · WE/L ${format1(bisWe)} m"
        } else {
            "WA/0 ${format1(station.coerceAtLeast(0.0))} m"
        }
    }

    private fun TrassenNaheBerechner.Ergebnis.weichenMeterLangText(achse: TrassenAchseEntity?): String {
        val laenge = achse?.achsenLaengeM()
        val stationVon = achse?.stationVon ?: 0.0
        return if (laenge != null && laenge > 0.0) {
            val position = (station - stationVon).coerceIn(0.0, laenge)
            val bisWe = (laenge - position).coerceAtLeast(0.0)
            "Station ${format1(position)} / ${format1(laenge)} m · bis WA/0,000 ${format1(position)} m · bis WE/Länge ${format1(bisWe)} m"
        } else {
            "Station ${format1(station)} m · bis WA/0,000 ${format1(station.coerceAtLeast(0.0))} m"
        }
    }

    private fun TrassenAchseEntity.achsenLaengeM(): Double? {
        val von = stationVon
        val bis = stationBis
        return if (von != null && bis != null && bis > von) bis - von else null
    }

    private fun TrassenNaheBerechner.Ergebnis.toKompakteErgebnisZeile(): String {
        val gleisText = when (achsTyp) {
            TrassenAchseEntity.ACHSTYP_WEICHE -> "W ${weichenAnzeigeName()}"
            TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> "WV ${weichenAnzeigeName()}"
            else -> gleis?.takeIf { it.isNotBlank() }?.let { "Gl $it" } ?: achsTyp.achsTypLabel()
        }
        val kmText = kmMeter?.let { formatKmOderStationMitLabel(it).replace("km ", "km ") } ?: "km ?"
        val gvpText = gvpKurzText()?.let { " · GVP $it" } ?: ""
        val seiteText = when (seite) {
            TrassenNaheBerechner.Seite.LINKS -> " links"
            TrassenNaheBerechner.Seite.RECHTS -> " rechts"
            TrassenNaheBerechner.Seite.AUF_ACHSE -> ""
            TrassenNaheBerechner.Seite.UNBEKANNT -> ""
        }
        val qualitaetText = when (qualitaet) {
            TrassenNaheBerechner.Qualitaet.GRUEN -> "gruen"
            TrassenNaheBerechner.Qualitaet.GELB -> "gelb"
            TrassenNaheBerechner.Qualitaet.ORANGE -> "orange"
            TrassenNaheBerechner.Qualitaet.ROT -> "rot"
        }
        return "$gleisText · $kmText$gvpText · Abstand ${format1(abstandM)} m$seiteText · $qualitaetText"
    }

    private fun TrassenNaheBerechner.Ergebnis.weichenAnzeigeName(): String {
        val zuordnung = ladeWeichenZuordnung(achseName)
        return if (zuordnung != null && zuordnung.istZugeordnet()) {
            zuordnung.anzeigeName()
        } else {
            achseName.toWeichenKurzName()
        }
    }

    private fun TrassenNaheBerechner.Ergebnis.gvpKurzText(): String? {
        val quelle = kmQuelle ?: return null
        val marker = "Festpunkte "
        val start = quelle.indexOf(marker)
        if (start < 0) return null
        return quelle.substring(start + marker.length)
            .substringBefore(" (")
            .substringBefore(";")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun String.toKurzeKmQuelle(): String {
        val gvp = substringAfter("Festpunkte ", missingDelimiterValue = "")
            .substringBefore(" (")
            .trim()
        return if (gvp.isNotBlank()) "GVP $gvp" else this
    }

    private fun TrassenNaheBerechner.Ergebnis.toKurzStatusBlock(): String {
        val streckeText = strecke?.takeIf { it.isNotBlank() } ?: "ohne Strecke"
        val gleisText = gleis?.takeIf { it.isNotBlank() } ?: "ohne Gleis"
        val kmText = kmMeter?.let { formatKmOderStationMitLabel(it) } ?: "km noch nicht verknuepft"
        val kmQuelleText = kmQuelle?.takeIf { it.isNotBlank() }
        val kmOriginalText = kmOriginalMeter
            ?.takeIf { original -> kmMeter != null && kotlin.math.abs(original - kmMeter) >= 5.0 }
            ?.let { original -> formatKmOderStationMitLabel(original) }
        val seiteText = when (seite) {
            TrassenNaheBerechner.Seite.LINKS -> "links"
            TrassenNaheBerechner.Seite.RECHTS -> "rechts"
            TrassenNaheBerechner.Seite.AUF_ACHSE -> "auf Achse"
            TrassenNaheBerechner.Seite.UNBEKANNT -> "unbekannt"
        }
        val qualitaetText = when (qualitaet) {
            TrassenNaheBerechner.Qualitaet.GRUEN -> "gruen"
            TrassenNaheBerechner.Qualitaet.GELB -> "gelb"
            TrassenNaheBerechner.Qualitaet.ORANGE -> "orange"
            TrassenNaheBerechner.Qualitaet.ROT -> "rot"
        }
        return buildString {
            appendLine("Achse: $achseName")
            appendLine("Typ: ${achsTyp.achsTypLabel()}")
            appendLine("Strecke/Gleis: $streckeText / $gleisText")
            appendLine("Station: ${format1(station)} m")
            appendLine("KM: $kmText")
            kmQuelleText?.let {
                appendLine("KM-Quelle: $it")
                appendLine("KM-Hinweis: Abstand/Gleis kommt aus der TRA-Gleisachse; der KM-Wert ist hier nur aus GVP/Festpunkten abgeleitet.")
            }
            kmOriginalText?.let { appendLine("KM roh/GRA: $it") }
            appendLine("Abstand: ${format1(abstandM)} m ($seiteText)")
            appendLine("Qualitaet: $qualitaetText")
            append("Proj. GPS: ${formatGps(projLat)}, ${formatGps(projLon)}")
        }
    }

    private fun naheKurzZeile(ergebnis: TrassenNaheBerechner.Ergebnis): String {
        val streckeText = ergebnis.strecke?.takeIf { it.isNotBlank() } ?: "-"
        val gleisText = ergebnis.gleis?.takeIf { it.isNotBlank() } ?: "-"
        val kmText = ergebnis.kmMeter?.let { " · ${formatKmOderStationMitLabel(it)}" } ?: ""
        val kmQuelleText = ergebnis.kmQuelle?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        return "[${ergebnis.achsTyp}] ${ergebnis.achseName} · Str $streckeText / Gl $gleisText · ${format1(ergebnis.abstandM)} m · St ${format1(ergebnis.station)}$kmText$kmQuelleText"
    }

    private fun TrassenNaheBerechner.Ergebnis.toNaheStatusText(
        liveLat: Double? = null,
        liveLon: Double? = null,
        accuracyM: Float? = null
    ): String {
        val streckeText = strecke?.takeIf { it.isNotBlank() } ?: "ohne Strecke"
        val gleisText = gleis?.takeIf { it.isNotBlank() } ?: "ohne Gleis"
        val kmText = kmMeter?.let { formatKmOderStationMitLabel(it) } ?: "km noch nicht verknuepft"
        val kmQuelleText = kmQuelle?.takeIf { it.isNotBlank() }
        val kmOriginalText = kmOriginalMeter
            ?.takeIf { original -> kmMeter != null && kotlin.math.abs(original - kmMeter) >= 5.0 }
            ?.let { original -> formatKmOderStationMitLabel(original) }
        val seiteText = when (seite) {
            TrassenNaheBerechner.Seite.LINKS -> "links"
            TrassenNaheBerechner.Seite.RECHTS -> "rechts"
            TrassenNaheBerechner.Seite.AUF_ACHSE -> "auf Achse"
            TrassenNaheBerechner.Seite.UNBEKANNT -> "unbekannt"
        }
        val qualitaetText = when (qualitaet) {
            TrassenNaheBerechner.Qualitaet.GRUEN -> "gruen"
            TrassenNaheBerechner.Qualitaet.GELB -> "gelb"
            TrassenNaheBerechner.Qualitaet.ORANGE -> "orange"
            TrassenNaheBerechner.Qualitaet.ROT -> "rot"
        }
        return buildString {
            appendLine(getString(R.string.import_export_v19_nahe_test_title))
            if (liveLat != null && liveLon != null) {
                val accText = accuracyM?.let { " · Genauigkeit ${format1(it.toDouble())} m" } ?: ""
                appendLine("Live-GPS: ${formatGps(liveLat)}, ${formatGps(liveLon)}$accText")
            }
            appendLine("Achse: $achseName")
            appendLine("Typ: ${achsTyp.achsTypLabel()}")
            appendLine("Strecke/Gleis: $streckeText / $gleisText")
            appendLine("Station: ${format1(station)} m")
            appendLine("KM: $kmText")
            kmQuelleText?.let {
                appendLine("KM-Quelle: $it")
                appendLine("KM-Hinweis: Abstand/Gleis kommt aus der TRA-Gleisachse; der KM-Wert ist hier nur aus GVP/Festpunkten abgeleitet.")
            }
            kmOriginalText?.let { appendLine("KM roh/GRA: $it") }
            appendLine("Abstand: ${format1(abstandM)} m ($seiteText)")
            appendLine("Qualitaet: $qualitaetText")
            appendLine("Proj. GPS: ${formatGps(projLat)}, ${formatGps(projLon)}")
            appendLine()
            appendLine(getString(R.string.import_export_v19_nahe_test_hint_after))
        }.trimEnd()
    }

    private fun formatKm(kmMeter: Double): String {
        val km = kmMeter / 1000.0
        return String.format(Locale.GERMANY, "%.3f", km)
    }

    private fun formatKmOderStationMitLabel(kmOderStation: Double): String {
        return if (kmOderStation >= 10_000.0) {
            "km ${formatKm(kmOderStation)}"
        } else {
            "GRA-Station ${format1(kmOderStation)} m"
        }
    }

    private fun formatKmOderStation(kmMeter: Double): String {
        return if (kmMeter >= 10_000.0) {
            "km ${formatKm(kmMeter)}"
        } else {
            "Station ${format1(kmMeter)} m"
        }
    }

    private fun String.toWeichenKurzName(): String {
        return removeSuffix("_ER0")
            .removePrefix("SW_")
            .removePrefix("WV_")
            .substringBefore("_ER0")
            .trim('_')
    }

    private fun String.achsTypLabel(): String = when (this) {
        TrassenAchseEntity.ACHSTYP_GLEIS -> "Gleis"
        TrassenAchseEntity.ACHSTYP_STRECKE -> "Strecke"
        TrassenAchseEntity.ACHSTYP_WEICHE -> "Weiche"
        TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG -> "WV"
        else -> "Unbekannt"
    }

    private fun format1(value: Double): String = String.format(Locale.GERMANY, "%.1f", value)

    private fun formatGps(value: Double): String = String.format(Locale.US, "%.8f", value)

    private fun TrassenImportStatusEntity.toStatusLine(): String {
        val zeit = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(importiertAmEpochMs))
        val details = when {
            status == "FEHLER" -> meldung ?: status
            datenTyp == "GPS" -> getString(R.string.import_export_v19_status_line_gps, anzahlSamplepunkte, anzahlGvpPunkte, anzahlAbsteckpunkte)
            anzahlAchsen > 0 -> getString(R.string.import_export_v19_status_line_axes, anzahlAchsen, anzahlElemente)
            datenTyp == "GRA" && anzahlSamplepunkte > 0 -> getString(R.string.import_export_v19_status_line_gra, anzahlSamplepunkte)
            anzahlGvpPunkte > 0 -> getString(R.string.import_export_v19_status_line_gvp, anzahlGvpPunkte)
            anzahlAbsteckpunkte > 0 -> getString(R.string.import_export_v19_status_line_kor, anzahlAbsteckpunkte)
            else -> meldung ?: status
        }
        return "• $zeit – $datenTyp – $details"
    }

    private data class LiveNaheResult(
        val messung: LiveGpsMessung,
        val kandidaten: List<TrassenNaheBerechner.Ergebnis>,
        val achsenById: Map<Long, TrassenAchseEntity>
    )

    private data class ManuellerAbschnittEntwurf(
        val id: Long = 0,
        val achsTyp: String,
        val bezeichnung: String,
        val strecke: String? = null,
        val gleis: String? = null,
        val richtung: String? = null,
        val achseId: Long? = null,
        val achseName: String? = null,
        val quelleDatei: String? = null,
        val dateiSignatur: String? = null,
        val stationStart: Double? = null,
        val stationEnde: Double? = null,
        val startQuelle: String? = null,
        val bekannteLaengeM: Double? = null,
        val kmRichtung: String? = null,
        val kmStationVorzeichen: Int? = null,
        val kmAnkerStation: Double? = null,
        val kmAnkerMeter: Double? = null,
        val kmPunkt1Meter: Double? = null,
        val kmPunkt2Station: Double? = null,
        val kmPunkt2Meter: Double? = null,
        val startGpsLat: Double? = null,
        val startGpsLon: Double? = null,
        val startGpsAccuracyM: Float? = null,
        val startGpsStreuungM: Double? = null,
        val startAchsabstandM: Double? = null,
        val punkt2GpsLat: Double? = null,
        val punkt2GpsLon: Double? = null,
        val punkt2GpsAccuracyM: Float? = null,
        val punkt2GpsStreuungM: Double? = null,
        val punkt2AchsabstandM: Double? = null,
        val endeQuelle: String? = null,
        val bearbeitungsStatus: String = TrassenManuellerAbschnittEntity.STATUS_OFFEN,
        val erstelltAmEpochMs: Long? = null
    )

    private data class TraFreiraumErgebnis(
        val erlaubt: Boolean,
        val stationVon: Double? = null,
        val stationBis: Double? = null,
        val quelleVon: String? = null,
        val quelleBis: String? = null,
        val meldung: String
    )

    private data class VermarkungsProjektion(
        val station: Double,
        val abstandM: Double,
        val seite: TrassenNaheBerechner.Seite,
        val projX: Float,
        val projY: Float
    )

    private data class WeichenWvOrientierung(
        val label: String,
        val station: Double,
        val distanzAbStartM: Double,
        val querAbstandM: Double
    )

    private data class StreckenObjektPunktUi(
        val gps: MaterialButton,
        val km: EditText,
        val status: TextView,
        var kmWirdAktualisiert: Boolean = false
    )

    private data class StreckenObjektAchsenTreffer(
        val label: String,
        val achseId: Long,
        val achseName: String?,
        val hatKm: Boolean,
        val abstandSumme: Double
    )

    private data class AbschnittGpsMessung(
        val latitude: Double,
        val longitude: Double,
        val accuracyM: Float?,
        val bestAccuracyM: Float?,
        val streuungM: Double?,
        val fixCount: Int,
        val usedFixCount: Int,
        val durationMs: Long
    ) {
        fun alsQualitaetsText(): String = buildString {
            appendLine("Fixes: $fixCount · verwendet: $usedFixCount")
            appendLine("Genauigkeit Median: ${accuracyM?.let { String.format(Locale.GERMANY, "±%.1f m", it) } ?: "?"}")
            appendLine("Beste Genauigkeit: ${bestAccuracyM?.let { String.format(Locale.GERMANY, "±%.1f m", it) } ?: "?"}")
            append("Streuung der besten Fixes: ${streuungM?.let { String.format(Locale.GERMANY, "%.1f m", it) } ?: "?"}")
        }.trimEnd()
    }

    private data class GrafikPunktAuswahl(
        val achse: TrassenGrafikAchse,
        val station: Double
    )

    private data class LiveGpsMessung(
        val latitude: Double,
        val longitude: Double,
        val accuracyM: Float?,
        val bestAccuracyM: Float?,
        val fixCount: Int,
        val usedFixCount: Int,
        val durationMs: Long
    )

    private data class ZuordnungsGruppeUi(
        val titel: String,
        val betriebsstelleInput: AutoCompleteTextView,
        val vorschlaege: List<BetriebsstellenVorschlag>,
        val checkboxen: List<CheckBox>
    ) {
        fun ausgewaehlteBetriebsstelle(): BetriebsstellenVorschlag? {
            val ausTag = betriebsstelleInput.tag as? BetriebsstellenVorschlag
            if (ausTag != null) return ausTag
            val raw = betriebsstelleInput.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) return null
            val norm = BahnhofSucheUtil.normalisiereSuchtext(raw)
            val exakt = vorschlaege.filter { v ->
                norm == BahnhofSucheUtil.normalisiereSuchtext(v.code) ||
                    norm == BahnhofSucheUtil.normalisiereSuchtext(v.name) ||
                    norm == BahnhofSucheUtil.normalisiereSuchtext(v.display)
            }
            return exakt.singleOrNull()
        }
    }

    private data class BetriebsstellenVorschlag(
        val code: String,
        val name: String,
        val typ: String?,
        val display: String
    )

    private data class ZipObjektZuordnung(
        val kuerzel: String,
        val name: String
    )

    private enum class ZipDateiTyp {
        TRA,
        GRA,
        TRG,
        KOR,
        CSV,
        TXT,
        KF_TXT,
        KF_RAW,
        KV,
        GVP_TXT,
        PDF
    }

    private enum class ZipObjektTyp {
        STRECKENACHSE,
        BAHNHOFS_GLEIS,
        WEICHE,
        WEICHENVERBINDUNG,
        PUNKTE_ODER_DOKUMENT,
        UNBEKANNT
    }

    private fun normalisiereZipRohName(raw: String): String = raw
        .replace(Regex("_ER0.*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("_N00.*", RegexOption.IGNORE_CASE), "")
        .substringBeforeLast('.')
        .trim('_', ' ', '-')

    private data class StreckendatenZipDatei(
        val pfad: String,
        val dateiname: String,
        val typ: ZipDateiTyp,
        val objektTyp: ZipObjektTyp,
        val strecken: List<String>,
        val rohName: String,
        val gleis: String?,
        val variante: String?,
        val wvWeichen: List<String>,
        val bytes: ByteArray
    ) {
        private fun normalisierterRohName(): String = rohName
            .replace(Regex("_ER0.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("_N00.*", RegexOption.IGNORE_CASE), "")
            .substringBeforeLast('.')
            .trim('_', ' ', '-')

        val rawKey: String get() = "$objektTyp|${normalisierterRohName()}"

        fun sollImportiertWerden(ausgewaehlteObjekte: Set<String>): Boolean {
            if (objektTyp != ZipObjektTyp.BAHNHOFS_GLEIS &&
                objektTyp != ZipObjektTyp.WEICHE &&
                objektTyp != ZipObjektTyp.WEICHENVERBINDUNG
            ) return true
            // Bahnhofsobjekte werden nur importiert, wenn sie in der Vorschau bewusst ausgewählt wurden.
            if (ausgewaehlteObjekte.isEmpty()) return false
            return rawKey in ausgewaehlteObjekte
        }

        fun anzeigeText(): String {
            val streckeText = strecken.firstOrNull()?.let { " · Str $it" } ?: ""
            return when (objektTyp) {
                ZipObjektTyp.BAHNHOFS_GLEIS -> {
                    val gl = gleis ?: Regex("(?i)(?:GL|GLEIS)[_\\s-]*(\\d+[A-Z]?)").find(rohName)
                        ?.groupValues?.getOrNull(1)
                    "Gl ${gl ?: normalisierterRohName()}$streckeText"
                }
                ZipObjektTyp.WEICHENVERBINDUNG -> {
                    val kurz = Regex("(?i)(WVB|WV)[_\\s-]*(\\d+)[-_](\\d+)").find(rohName)
                        ?.let { "WVB ${it.groupValues[2]}-${it.groupValues[3]}" }
                        ?: normalisierterRohName()
                    val weichenText = if (wvWeichen.isNotEmpty()) " · W ${wvWeichen.joinToString("/")}" else ""
                    "$kurz$streckeText$weichenText"
                }
                ZipObjektTyp.WEICHE -> {
                    val kurz = Regex("(?i)(?:SW|W)[_\\s-]*(\\d+W?)").find(rohName)
                        ?.let { "W ${it.groupValues[1]}" }
                        ?: normalisierterRohName()
                    "$kurz$streckeText"
                }
                ZipObjektTyp.STRECKENACHSE -> "Strecke ${normalisierterRohName()}$streckeText"
                ZipObjektTyp.PUNKTE_ODER_DOKUMENT -> "Punkte ${normalisierterRohName()}$streckeText"
                ZipObjektTyp.UNBEKANNT -> "Objekt ${normalisierterRohName()}$streckeText"
            }
        }
    }

    private data class StreckendatenZipScan(
        val zipName: String,
        val dateien: List<StreckendatenZipDatei>,
        val pdfPfade: List<String>
    ) {
        fun strecken(): List<String> = dateien.flatMap { it.strecken }.distinct().sorted()

        fun zuordnungsObjekte(): List<StreckendatenZipDatei> {
            return dateien
                .filter { it.typ == ZipDateiTyp.TRA }
                .filter {
                    it.objektTyp == ZipObjektTyp.BAHNHOFS_GLEIS ||
                        it.objektTyp == ZipObjektTyp.WEICHE ||
                        it.objektTyp == ZipObjektTyp.WEICHENVERBINDUNG
                }
                .distinctBy { it.rawKey }
                .sortedWith(compareBy<StreckendatenZipDatei> { it.objektTyp.name }.thenBy { it.rohName })
        }

        fun anzahl(typ: ZipDateiTyp): Int = dateien.count { it.typ == typ }
        fun anzahlObjekt(objektTyp: ZipObjektTyp): Int = dateien.count { it.typ == ZipDateiTyp.TRA && it.objektTyp == objektTyp }

        fun toKurzStatusText(): String {
            return buildString {
                appendLine("Streckendaten-ZIP gelesen: $zipName")
                appendLine("Strecken: ${strecken().joinToString().ifBlank { "nicht erkannt" }}")
                appendLine("TRA: ${anzahl(ZipDateiTyp.TRA)} · GRA: ${anzahl(ZipDateiTyp.GRA)} · TRG: ${anzahl(ZipDateiTyp.TRG)}")
                appendLine("KOR: ${anzahl(ZipDateiTyp.KOR)} · CSV: ${anzahl(ZipDateiTyp.CSV)} · TXT: ${anzahl(ZipDateiTyp.TXT)} · KF.TXT/KF: ${anzahl(ZipDateiTyp.KF_TXT)}/${anzahl(ZipDateiTyp.KF_RAW)}")
                appendLine("Gleise: ${anzahlObjekt(ZipObjektTyp.BAHNHOFS_GLEIS)} · Weichen: ${anzahlObjekt(ZipObjektTyp.WEICHE)} · WV: ${anzahlObjekt(ZipObjektTyp.WEICHENVERBINDUNG)}")
                if (pdfPfade.isNotEmpty()) appendLine("PDF: ${pdfPfade.size} gefunden, wird nicht importiert")
                append("Weiter mit Vorschau/Zuordnung, danach Daten berechnen.")
            }
        }

        private fun String?.richtungAlsKennziffer(): String? {
            val wert = this?.trim()?.uppercase(Locale.GERMANY).orEmpty()
            return when (wert) {
                "0", "S" -> "0"
                "1", "L", "SL" -> "1"
                "2", "R" -> "2"
                else -> null
            }
        }

        fun toVorschauText(): String {
            val streckenText = strecken().joinToString().ifBlank { "nicht sicher erkannt" }
            val streckenAchsen = dateien.filter { it.typ == ZipDateiTyp.TRA && it.objektTyp == ZipObjektTyp.STRECKENACHSE }
            val varianten = streckenAchsen.map { achse ->
                val str = achse.strecken.firstOrNull() ?: "?"
                val richtungText = achse.variante.richtungAlsKennziffer()?.let { kennziffer ->
                    "-$kennziffer · Richtung $kennziffer" + achse.variante?.let { " (Quelle: $it)" }.orEmpty()
                }.orEmpty()
                "• ${achse.rohName} → Strecke $str$richtungText"
            }
            val wv = zuordnungsObjekte().filter { it.objektTyp == ZipObjektTyp.WEICHENVERBINDUNG }
            val weichenAusWv = wv.flatMap { it.wvWeichen.map { w -> "W$w" } }.distinct().sorted()
            return buildString {
                appendLine("ZIP: $zipName")
                appendLine()
                appendLine("Gefundene Strecken: $streckenText")
                if (varianten.isNotEmpty()) {
                    appendLine()
                    appendLine("Streckenachsen / Richtungen:")
                    varianten.take(12).forEach { appendLine(it) }
                    if (varianten.size > 12) appendLine("• … ${varianten.size - 12} weitere")
                }
                appendLine()
                appendLine("Gefundene Dateien:")
                appendLine("• TRA: ${anzahl(ZipDateiTyp.TRA)}")
                appendLine("• GRA/TRG: ${anzahl(ZipDateiTyp.GRA)}/${anzahl(ZipDateiTyp.TRG)}")
                appendLine("• KOR: ${anzahl(ZipDateiTyp.KOR)}")
                appendLine("• CSV/TXT: ${anzahl(ZipDateiTyp.CSV)}/${anzahl(ZipDateiTyp.TXT)}")
                appendLine("• KF.TXT/KF: ${anzahl(ZipDateiTyp.KF_TXT)}/${anzahl(ZipDateiTyp.KF_RAW)}")
                appendLine("• PDF: ${pdfPfade.size} gefunden, nicht importiert")
                appendLine()
                appendLine("Bahnhofsobjekte:")
                appendLine("• Gleise: ${anzahlObjekt(ZipObjektTyp.BAHNHOFS_GLEIS)}")
                appendLine("• Weichen: ${anzahlObjekt(ZipObjektTyp.WEICHE)}")
                appendLine("• Weichenverbindungen: ${anzahlObjekt(ZipObjektTyp.WEICHENVERBINDUNG)}")
                if (weichenAusWv.isNotEmpty()) appendLine("• aus WV abgeleitete Weichen: ${weichenAusWv.joinToString()}")
                appendLine()
                append("Hinweis: Kennziffer 0/1/2 wird nur bei Streckengleisen genutzt. Gleise/Weichen/WV bekommen Betriebsstelle/Bahnhof, aber keine Kennziffer.")
            }.trimEnd()
        }
    }

    private data class ImportErgebnis(
        val dateiname: String,
        val ok: Boolean,
        val meldung: String
    )
}
