package de.tbhelfer.ui.baustelle

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.Filter
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.util.BahnhofSucheUtil
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import de.tbhelfer.streckenobjekte.BaustellenObjektArt
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik
import de.tbhelfer.ui.sync.CatalogInventoryDialog
import de.tbhelfer.server.SyncCatalogApi
import de.tbhelfer.server.SecureTokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class BaustelleNeuFragment : Fragment() {

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

    private val repo = ServiceLocator.baustellenBaumRepository
    private val streckenRepo = ServiceLocator.streckenpunkteRepository
    private val trassenRepo = ServiceLocator.trassenRepository

    private var parentId: Long = -1L
    private var editKnotenId: Long = -1L
    private var editKnoten: BaustellenKnotenEntity? = null

    private lateinit var txtHauptInfo: TextView
    private lateinit var edtHauptbaustellenName: EditText
    private lateinit var edtUnterpunktName: EditText
    private lateinit var txtTypLabel: TextView

    private lateinit var rgTyp: RadioGroup
    private lateinit var gruppeFreie: LinearLayout
    private lateinit var gruppeBahnhof: LinearLayout
    private lateinit var gruppeAllgemein: LinearLayout

    // Freie Strecke
    private lateinit var edtStreckennummer: EditText
    private lateinit var spStreckenSuffix: Spinner
    private lateinit var edtKmVon: EditText
    private lateinit var edtKmBis: EditText
    private lateinit var txtStreckeJsonStatus: TextView

    // Bahnhof
    private lateinit var edtBahnhof: AutoCompleteTextView
    private lateinit var rgGleisWeiche: RadioGroup
    private lateinit var rbGleis: RadioButton
    private lateinit var rbWeiche: RadioButton
    private lateinit var rbDkwKreuzung: RadioButton
    private lateinit var rbWeichenverbindung: RadioButton
    private lateinit var edtGleisWeicheNr: AutoCompleteTextView
    private lateinit var txtAltWvHinweis: TextView
    private lateinit var btnStreckendatenOptional: Button
    private lateinit var txtBahnhofJsonStatus: TextView
    private lateinit var edtKmVonOptional: EditText
    private lateinit var edtKmBisOptional: EditText

    private lateinit var rbGrossbaustelle: RadioButton

    // Allgemein/Objekt
    private lateinit var edtKmVonAllg: EditText
    private lateinit var edtKmBisAllg: EditText

    private lateinit var btnSpeichern: Button

    private var bahnhofVorschlaege: List<BahnhofVorschlag> = emptyList()
    private var statusJob: Job? = null
    private var gleisVorschlagJob: Job? = null
    private var catalogLookupJob: Job? = null
    private var catalogLookupDialog: androidx.appcompat.app.AlertDialog? = null
    private var selectedServerStation: Pair<String, String>? = null
    private var formReady = false
    private var formApplying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentId = arguments?.getLong("parentId", -1L) ?: -1L
        editKnotenId = arguments?.getLong("editKnotenId", -1L) ?: -1L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        formReady = false
        val v = inflater.inflate(R.layout.fragment_baustelle_neu, container, false)

        // Insets (Notch/Statusbar): Inhalt nach unten schieben, damit der Hinweis immer lesbar ist.
        val baseTop = v.paddingTop
        val baseBottom = v.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
            insets
        }

        txtHauptInfo = v.findViewById(R.id.txtHauptInfo)
        edtHauptbaustellenName = v.findViewById(R.id.edtHauptbaustellenName)
        edtUnterpunktName = v.findViewById(R.id.edtUnterpunktName)
        txtTypLabel = v.findViewById(R.id.txtTypLabel)

        rgTyp = v.findViewById(R.id.rgTyp)
        rbGrossbaustelle = v.findViewById(R.id.rbGrossbaustelle)
        gruppeFreie = v.findViewById(R.id.gruppeFreieStrecke)
        gruppeBahnhof = v.findViewById(R.id.gruppeBahnhof)
        gruppeAllgemein = v.findViewById(R.id.gruppeAllgemein)

        edtStreckennummer = v.findViewById(R.id.edtStreckennummer)
        spStreckenSuffix = v.findViewById(R.id.spStreckenSuffix)
        edtKmVon = v.findViewById(R.id.edtKmVon)
        edtKmBis = v.findViewById(R.id.edtKmBis)
        txtStreckeJsonStatus = v.findViewById(R.id.txtStreckeJsonStatus)

        edtBahnhof = v.findViewById(R.id.edtBahnhofName)
        rgGleisWeiche = v.findViewById(R.id.rgGleisWeiche)
        rbGleis = v.findViewById(R.id.rbGleis)
        rbWeiche = v.findViewById(R.id.rbWeiche)
        rbDkwKreuzung = v.findViewById(R.id.rbDkwKreuzung)
        rbWeichenverbindung = v.findViewById(R.id.rbWeichenverbindung)
        edtGleisWeicheNr = v.findViewById(R.id.edtGleisWeicheNr)
        txtAltWvHinweis = v.findViewById(R.id.txtAltWvHinweis)
        btnStreckendatenOptional = v.findViewById(R.id.btnStreckendatenOptional)
        txtBahnhofJsonStatus = v.findViewById(R.id.txtBahnhofJsonStatus)
        edtKmVonOptional = v.findViewById(R.id.edtKmVonOptional)
        edtKmBisOptional = v.findViewById(R.id.edtKmBisOptional)

        // Default: Gleis
        if (rgGleisWeiche.checkedRadioButtonId == View.NO_ID) rbGleis.isChecked = true

        edtKmVonAllg = v.findViewById(R.id.edtKmVonAllgemein)
        edtKmBisAllg = v.findViewById(R.id.edtKmBisAllgemein)

        btnSpeichern = v.findViewById(R.id.btnSpeichern)

        // Bezeichnung nur bei Unterpunkten / Bearbeiten anzeigen
        edtUnterpunktName.visibility = if (parentId != -1L || editKnotenId != -1L) View.VISIBLE else View.GONE
        // Großbaustelle nur beim Root anlegen
        rbGrossbaustelle.visibility = if (parentId == -1L && editKnotenId == -1L) View.VISIBLE else View.GONE

        // Spinner: -0/-1/-2
        val suffixe = listOf("-0", "-1", "-2")
        spStreckenSuffix.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            suffixe
        )

        bahnhofVorschlaege = ladeBahnhofVorschlaegeAusAssets()
        bindeBahnhofVorschlaege()
        bindeJsonStatusUi()

        rgTyp.setOnCheckedChangeListener { _, checkedId ->
            invalidateCatalogLookup()
            if (formReady && !formApplying) {
                edtBahnhof.setText("", false)
                clearStationObject()
                edtKmVon.setText("")
                edtKmBis.setText("")
            }
            edtBahnhof.isEnabled = checkedId == R.id.rbBahnhof
            when (checkedId) {
                R.id.rbGrossbaustelle -> {
                    gruppeFreie.visibility = View.GONE
                    gruppeBahnhof.visibility = View.GONE
                    gruppeAllgemein.visibility = View.GONE
                }
                R.id.rbFreieStrecke -> {
                    gruppeFreie.visibility = View.VISIBLE
                    gruppeBahnhof.visibility = View.GONE
                    gruppeAllgemein.visibility = View.GONE
                }
                R.id.rbBahnhof -> {
                    gruppeFreie.visibility = View.GONE
                    gruppeBahnhof.visibility = View.VISIBLE
                    gruppeAllgemein.visibility = View.GONE
                }
                R.id.rbAllgemein -> {
                    gruppeFreie.visibility = View.GONE
                    gruppeBahnhof.visibility = View.GONE
                    gruppeAllgemein.visibility = View.VISIBLE
                }
            }
            aktualisiereStreckendatenButton()
            planeJsonStatusUpdate()
        }

        btnSpeichern.setOnClickListener { speichern() }
        btnStreckendatenOptional.setOnClickListener { oeffneOptionaleStreckendaten() }
        aktualisiereStreckendatenButton()
        updateJsonStatus()

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindCatalogSelection()

        if (editKnotenId != -1L) {
            ladeBearbeitung(restoreFields = savedInstanceState == null)
            return
        }

        if (parentId != -1L) {
            // Unterknoten: Hauptbaustellenname erben
            edtHauptbaustellenName.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                val parent = repo.knotenMitId(parentId)
                val root = parent?.rootId?.let { repo.knotenMitId(it) }
                txtHauptInfo.text = "Hauptbaustelle: ${root?.hauptbaustellenName ?: "—"}"
            }
        } else {
            txtHauptInfo.text = getString(R.string.ui_baustelle_neu_info_merge)
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        formReady = true
        edtBahnhof.isEnabled = rgTyp.checkedRadioButtonId == R.id.rbBahnhof
        if (rgTyp.checkedRadioButtonId == R.id.rbFreieStrecke) edtBahnhof.setText("", false)
        updateObjectNumberHint()
        planeJsonStatusUpdate()
    }

    override fun onDestroyView() {
        formReady = false
        invalidateCatalogLookup()
        statusJob?.cancel()
        gleisVorschlagJob?.cancel()
        super.onDestroyView()
    }

    private fun clearStationObject() {
        edtGleisWeicheNr.setText("", false)
        edtGleisWeicheNr.dismissDropDown()
        edtGleisWeicheNr.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, emptyList<String>()))
        edtKmVonOptional.setText("")
        edtKmBisOptional.setText("")
    }

    private fun updateObjectNumberHint() {
        edtGleisWeicheNr.hint = when (bahnhofObjektModus()) {
            "WEICHE" -> "Weichennummer, z. B. 117"
            "WEICHENVERBINDUNG" -> "Weichenverbindungsnummer, z. B. 1"
            "DKW_KREUZUNG" -> "DKW-/Kreuzungsnummer"
            else -> "Gleisnummer, z. B. 19 oder 302/303"
        }
    }

    private fun invalidateCatalogLookup() {
        catalogLookupJob?.cancel()
        catalogLookupJob = null
        catalogLookupDialog?.dismiss()
        catalogLookupDialog = null
    }

    private fun catalogSelectionStamp(): String = listOf(editKnotenId.toString(), parentId.toString(),
        rgTyp.checkedRadioButtonId.toString(),
        if (rgTyp.checkedRadioButtonId == R.id.rbFreieStrecke) holeStreckenkennung().orEmpty() else edtBahnhof.text?.toString().orEmpty()
    ).joinToString("\u0001")

    private fun strictStationCode(): String? {
        val raw = edtBahnhof.text?.toString().orEmpty().trim()
        selectedServerStation?.takeIf { it.first == raw }?.let { return it.second }
        findeBahnhofVorschlag(raw)?.let { return it.code }
        val normalized = BahnhofSucheUtil.normalisiereSuchtext(raw)
        if (bahnhofVorschlaege.any { it.nameNorm == normalized }) return null
        Regex(".*\\(([A-Za-z0-9_-]{1,50})\\)$").matchEntire(raw)?.let { return it.groupValues[1] }
        return raw.takeIf { it.matches(Regex("[A-Z0-9_-]{1,50}")) }
    }

    private fun bindCatalogSelection() {
        gruppeFreie.addView(Button(requireContext()).apply {
            text = "Vorhandene Daten anzeigen"
            setOnClickListener { openFormInventory() }
        }, minOf(2, gruppeFreie.childCount))
        gruppeBahnhof.addView(Button(requireContext()).apply {
            text = "Bahnhof auf Server suchen"
            setOnClickListener { lookupServerStation() }
        }, 1)
        gruppeBahnhof.addView(Button(requireContext()).apply {
            text = "Vorhandene Daten anzeigen"
            setOnClickListener { openFormInventory() }
        }, 2)
        childFragmentManager.setFragmentResultListener(CatalogInventoryDialog.RESULT_KEY, viewLifecycleOwner) { _, result ->
            if (result.getString("selectionStamp") != catalogSelectionStamp()) {
                toast("Auswahl ist nicht mehr aktuell. Bitte den Bestand erneut öffnen.")
                return@setFragmentResultListener
            }
            when (result.getString("action")) {
                "OBJECT" -> {
                    if (rgTyp.checkedRadioButtonId != R.id.rbBahnhof) return@setFragmentResultListener
                    val kind = result.getString("kind")
                    val number = result.getString("number")?.takeIf { it.isNotBlank() } ?: return@setFragmentResultListener
                    val radio = when (kind) {
                        "GLEIS" -> R.id.rbGleis
                        "WEICHE" -> R.id.rbWeiche
                        "WEICHENVERBINDUNG" -> R.id.rbWeichenverbindung
                        else -> return@setFragmentResultListener
                    }
                    val previous = formApplying
                    formApplying = true
                    try {
                        rgGleisWeiche.check(radio)
                        edtGleisWeicheNr.setText(number, false)
                    } finally { formApplying = previous }
                    toast("Objekt übernommen. KM bleiben unverändert.")
                }
                "KM" -> {
                    val from = result.getDouble("fromMeters", Double.NaN) / 1000.0
                    val to = result.getDouble("toMeters", Double.NaN) / 1000.0
                    val fromText = formatKm(from)
                    val toText = formatKm(to)
                    if (!from.isFinite() || !to.isFinite() || from > to || parseKm(fromText) == null || parseKm(toText) == null) {
                        toast("Dieser KM-Bereich passt nicht ins Eingabeformat 0.000–999.999. Es wurde nichts übernommen.")
                        return@setFragmentResultListener
                    }
                    when (rgTyp.checkedRadioButtonId) {
                        R.id.rbFreieStrecke -> { edtKmVon.setText(fromText); edtKmBis.setText(toText) }
                        R.id.rbBahnhof -> { edtKmVonOptional.setText(fromText); edtKmBisOptional.setText(toText) }
                        else -> return@setFragmentResultListener
                    }
                    toast("KM-Abschnitt übernommen.")
                }
            }
        }
    }

    private fun openFormInventory() {
        if (formApplying || !formReady) return
        val stationMode = rgTyp.checkedRadioButtonId == R.id.rbBahnhof
        val route = if (rgTyp.checkedRadioButtonId == R.id.rbFreieStrecke) holeStreckenkennung()?.substringBefore('-') else null
        val station = if (stationMode) strictStationCode() else null
        if (route == null && station == null) {
            toast(if (stationMode) "Bitte einen eindeutigen Bahnhof auswählen oder auf dem Server suchen." else "Bitte eine vierstellige Strecke auswählen.")
            return
        }
        if (childFragmentManager.isStateSaved || childFragmentManager.findFragmentByTag("worksite_inventory") != null) return
        CatalogInventoryDialog.newInstance(station ?: route.orEmpty(), routeNumber = route, stationCode = station,
            selectionStamp = catalogSelectionStamp(), allowObjectSelection = stationMode, allowKmSelection = true)
            .show(childFragmentManager, "worksite_inventory")
    }

    private fun lookupServerStation() {
        if (formApplying || !formReady) return
        if (rgTyp.checkedRadioButtonId != R.id.rbBahnhof) return
        val raw = edtBahnhof.text?.toString().orEmpty().trim()
        val query = strictStationCode()?.takeIf { it.length >= 2 } ?: findeBahnhofVorschlag(raw)?.name ?: raw
        if (query.length !in 2..100) { toast("Bitte 2 bis 100 Zeichen des Bahnhofs eingeben."); return }
        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) { toast("Bitte zuerst das Gerät aktivieren. Lokale Eingabe bleibt möglich."); return }
        invalidateCatalogLookup()
        val stamp = catalogSelectionStamp()
        toast("Bahnhofsuche wird geladen …")
        catalogLookupJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { SyncCatalogApi().lookupStations(token, query) }
            if (!isAdded || !viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) ||
                childFragmentManager.isStateSaved || stamp != catalogSelectionStamp()) return@launch
            when (result) {
                is SyncCatalogApi.LookupResult.Success -> {
                    if (result.warnings.isNotEmpty()) toast(result.warnings.joinToString("\n"))
                    if (result.stations.isEmpty()) { toast("Keine passenden Bahnhöfe gefunden. Lokale Eingabe bleibt möglich."); return@launch }
                    catalogLookupDialog = MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Bahnhof auswählen")
                        .setItems(result.stations.map { "${it.name} (${it.code})" + if (it.dataAvailable) " · Daten vorhanden" else "" }.toTypedArray()) { _, index ->
                            if (stamp != catalogSelectionStamp()) return@setItems
                            val station = result.stations[index]
                            val display = "${station.name} (${station.code})"
                            if (edtBahnhof.text?.toString() != display) clearStationObject()
                            selectedServerStation = display to station.code
                            edtBahnhof.setText(display, false)
                        }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
                SyncCatalogApi.LookupResult.Unsupported -> toast("Dieser Server bietet die Bahnhofsuche noch nicht an. Lokale Eingabe bleibt möglich.")
                SyncCatalogApi.LookupResult.SessionInvalid -> toast("Gerätezugriff ist nicht gültig. Bitte Aktivierung prüfen.")
                is SyncCatalogApi.LookupResult.Error -> toast(result.message)
            }
        }
    }

    private fun bindeJsonStatusUi() {
        edtStreckennummer.addTextChangedListener(einfacherWatcher { planeJsonStatusUpdate() })
        edtBahnhof.addTextChangedListener(einfacherWatcher {
            invalidateCatalogLookup()
            if (selectedServerStation?.first != edtBahnhof.text?.toString()) selectedServerStation = null
            if (formReady && !formApplying) clearStationObject()
            planeJsonStatusUpdate()
            aktualisiereGleisVorschlaege()
        })
        edtGleisWeicheNr.addTextChangedListener(einfacherWatcher { planeJsonStatusUpdate() })
        edtGleisWeicheNr.threshold = 0
        edtGleisWeicheNr.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && rgGleisWeiche.checkedRadioButtonId == R.id.rbGleis) {
                aktualisiereGleisVorschlaege(dropdownOeffnen = true)
            }
        }
        edtGleisWeicheNr.setOnClickListener {
            if (rgGleisWeiche.checkedRadioButtonId == R.id.rbGleis) aktualisiereGleisVorschlaege(dropdownOeffnen = true)
        }
        rgGleisWeiche.setOnCheckedChangeListener { _, _ ->
            if (formReady && !formApplying) clearStationObject()
            updateObjectNumberHint()
            aktualisiereStreckendatenButton()
            planeJsonStatusUpdate()
            aktualisiereGleisVorschlaege()
        }
        spStreckenSuffix.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = planeJsonStatusUpdate()
            override fun onNothingSelected(parent: AdapterView<*>?) = updateJsonStatus()
        }
    }

    private fun bindeBahnhofVorschlaege() {
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
        edtBahnhof.setAdapter(adapter)
        edtBahnhof.threshold = 1
        edtBahnhof.setOnItemClickListener { _, _, _, _ -> updateJsonStatus() }
        edtBahnhof.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !edtBahnhof.text.isNullOrBlank()) {
                edtBahnhof.post { edtBahnhof.showDropDown() }
            }
        }
        if (edtBahnhof.text.isNullOrBlank()) {
            val letzterBahnhof = BahnhofSucheUtil.ladeLetzteEingabe(requireContext())
            if (letzterBahnhof.isNotBlank()) {
                edtBahnhof.setText(letzterBahnhof, false)
                edtBahnhof.setSelection(edtBahnhof.text?.length ?: 0)
            }
        }
    }

    private fun einfacherWatcher(onChanged: (Editable?) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChanged(s)
    }

    private fun planeJsonStatusUpdate(delayMs: Long = 220L) {
        statusJob?.cancel()
        statusJob = viewLifecycleOwner.lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            updateStreckeJsonStatus()
            updateBahnhofJsonStatus()
        }
    }

    /**
     * Patch45ZV: Bei der Baustellenerstellung werden importierte und manuell
     * angelegte Gleise der gewählten Betriebsstelle gemeinsam angeboten.
     */
    private fun aktualisiereGleisVorschlaege(dropdownOeffnen: Boolean = false) {
        gleisVorschlagJob?.cancel()
        if (!::edtGleisWeicheNr.isInitialized || rgGleisWeiche.checkedRadioButtonId != R.id.rbGleis) return
        val raw = edtBahnhof.text?.toString().orEmpty().trim()
        if (raw.isBlank()) return
        val code = extrahiereBahnhofCode(raw, findeBahnhofVorschlag(raw)).ifBlank { raw.uppercase(Locale.GERMANY) }
        gleisVorschlagJob = viewLifecycleOwner.lifecycleScope.launch {
            val gleise = withContext(Dispatchers.IO) { ladeGleisVorschlaegeFuerBetriebsstelle(code) }
            if (!isAdded || rgTyp.checkedRadioButtonId != R.id.rbBahnhof ||
                rgGleisWeiche.checkedRadioButtonId != R.id.rbGleis || edtBahnhof.text?.toString().orEmpty().trim() != raw) return@launch
            edtGleisWeicheNr.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, gleise)
            )
            if (dropdownOeffnen && edtGleisWeicheNr.hasFocus() && gleise.isNotEmpty()) {
                edtGleisWeicheNr.post { edtGleisWeicheNr.showDropDown() }
            }
        }
    }

    private suspend fun ladeGleisVorschlaegeFuerBetriebsstelle(codeRaw: String): List<String> {
        val uebersicht = runCatching { trassenRepo.ladeUebersicht() }.getOrNull() ?: return emptyList()
        val zielCode = codeRaw.trim().uppercase(Locale.GERMANY)
        val zuordnungen = ladeStreckendatenZuordnungen()
        val nutztZuordnung = zuordnungen.isNotEmpty()
        val achsenById = uebersicht.achsen.associateBy { it.id }

        val importierte = uebersicht.achsen
            .filter { it.achsTyp == de.tbhelfer.datenbank.TrassenAchseEntity.ACHSTYP_GLEIS && !it.gleis.isNullOrBlank() }
            .filter { achse -> !nutztZuordnung || zuordnungen.any { it.passtZuAchse(achse, zielCode, "BAHNHOFS_GLEIS") } }
            .mapNotNull { it.gleis?.trim() }

        val manuell = runCatching { ServiceLocator.trassenDao.alleManuellenAbschnitte() }
            .getOrDefault(emptyList())
            .filter {
                it.achsTyp == de.tbhelfer.datenbank.TrassenAchseEntity.ACHSTYP_GLEIS &&
                    !it.gleis.isNullOrBlank() && it.aktuelleAchseId != null
            }
            .filter { abschnitt ->
                if (!nutztZuordnung) true else {
                    val rohAchse = abschnitt.aktuelleAchseId?.let(achsenById::get) ?: return@filter false
                    zuordnungen.any { it.passtZuAchseUnabhaengigVomTyp(rohAchse, zielCode) }
                }
            }
            .mapNotNull { it.gleis?.trim() }

        return (importierte + manuell)
            .distinctBy { normGleis(it) }
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
    }

    private fun updateJsonStatus() = planeJsonStatusUpdate(0L)

    private suspend fun updateStreckeJsonStatus() {
        val istAktiv = rgTyp.checkedRadioButtonId == R.id.rbFreieStrecke
        if (!istAktiv) {
            txtStreckeJsonStatus.visibility = View.GONE
            return
        }
        val kennung = holeStreckenkennung()
        if (kennung == null) {
            txtStreckeJsonStatus.visibility = View.GONE
            return
        }

        val pruefung = pruefeTrassendatenFreieStrecke(kennung)
        if (pruefung != null) {
            setzeStatusText(txtStreckeJsonStatus, pruefung.vorhanden, pruefung.text)
            return
        }

        val vorhanden = streckenRepo.hatDatenFuerStrecke(kennung)
        setzeStatusText(
            txtStreckeJsonStatus,
            vorhanden,
            if (vorhanden) "✓ Streckendaten vorhanden" else "✗ Keine Vermessungsdaten gefunden"
        )
    }

    private suspend fun updateBahnhofJsonStatus() {
        val istAktiv = rgTyp.checkedRadioButtonId == R.id.rbBahnhof
        if (!istAktiv) {
            txtBahnhofJsonStatus.visibility = View.GONE
            return
        }
        val raw = edtBahnhof.text?.toString().orEmpty().trim()
        if (raw.isBlank()) {
            txtBahnhofJsonStatus.visibility = View.GONE
            return
        }
        val code = extrahiereBahnhofCode(raw, findeBahnhofVorschlag(raw)).ifBlank { raw.uppercase(Locale.GERMANY) }
        val nr = edtGleisWeicheNr.text?.toString().orEmpty().trim()

        val pruefung = pruefeTrassendatenBahnhofGleis(code, nr)
        if (pruefung != null) {
            setzeStatusText(txtBahnhofJsonStatus, pruefung.vorhanden, pruefung.text)
            return
        }

        val vorhanden = when {
            nr.isBlank() -> streckenRepo.hatDatenFuerBahnhof(code)
            rgGleisWeiche.checkedRadioButtonId == R.id.rbWeiche -> streckenRepo.hatDatenFuerBahnhofWeiche(code, baueWeichenKennung(nr))
            rgGleisWeiche.checkedRadioButtonId == R.id.rbWeichenverbindung -> streckenRepo.hatDatenFuerBahnhofWeiche(code, baueWeichenKennung(nr))
            rgGleisWeiche.checkedRadioButtonId == R.id.rbDkwKreuzung -> streckenRepo.hatDatenFuerBahnhof(code)
            else -> streckenRepo.hatDatenFuerBahnhofGleis(code, baueGleisKennung(nr))
        }
        setzeStatusText(
            txtBahnhofJsonStatus,
            vorhanden,
            if (vorhanden) "✓ Bahnhofsdaten vorhanden" else "✗ Keine Vermessungsdaten gefunden"
        )
    }

    private suspend fun pruefeTrassendatenFreieStrecke(streckenkennung: String): DatenStatus? {
        val uebersicht = runCatching { trassenRepo.ladeUebersicht() }.getOrNull() ?: return null
        if (uebersicht.istLeer) return null

        val strecke = streckenkennung.substringBefore("-").trim()
        val kennziffer = streckenkennung.substringAfter("-", "").trim()
        val alleStreckenachsen = uebersicht.achsen.filter { achse ->
            achse.strecke?.trim() == strecke &&
                achse.achsTyp == de.tbhelfer.datenbank.TrassenAchseEntity.ACHSTYP_STRECKE
        }
        val passendeAchsen = alleStreckenachsen.filter { achse -> richtungPasstZuKennziffer(achse.richtung, kennziffer) }
        val bahnhofsObjekteZurStrecke = uebersicht.achsen.filter { achse ->
            achse.strecke?.trim() == strecke &&
                achse.achsTyp != de.tbhelfer.datenbank.TrassenAchseEntity.ACHSTYP_STRECKE
        }
        val gvp = uebersicht.gvpGruppen.firstOrNull { it.strecke?.trim() == strecke }
        val graAlle = uebersicht.graGruppen.filter { it.strecke?.trim() == strecke }
        val gra = graAlle.filter { gruppe -> graNamePasstZuKennziffer(gruppe.quelleDatei, gruppe.gleis, kennziffer) }
            .ifEmpty { if (passendeAchsen.isNotEmpty()) graAlle else emptyList() }

        if (alleStreckenachsen.isEmpty() && gvp == null && graAlle.isEmpty()) {
            return DatenStatus(false, "✗ Strecke $strecke nicht gefunden")
        }
        if (passendeAchsen.isEmpty()) {
            val varianten = alleStreckenachsen.mapNotNull { it.richtung?.trim()?.takeIf { r -> r.isNotBlank() } }.distinct().joinToString("/").ifBlank { "keine" }
            val bahnhofHinweis = if (bahnhofsObjekteZurStrecke.isNotEmpty()) {
                " · Bahnhofsdaten getrennt: ${bahnhofsObjekteZurStrecke.size} Objekt(e)"
            } else ""
            return DatenStatus(false, "⚠ Strecke $strecke vorhanden, aber keine passende Streckenachse fuer $streckenkennung · Richtungen: $varianten$bahnhofHinweis")
        }

        val kmVonEingabe = parseKm(edtKmVon.text?.toString().orEmpty())?.times(1000.0)
        val kmBisEingabe = parseKm(edtKmBis.text?.toString().orEmpty())?.times(1000.0)
        val kmVonPaket = listOfNotNull(gvp?.kmVon, gra.mapNotNull { it.kmVon }.minOrNull()).minOrNull()
        val kmBisPaket = listOfNotNull(gvp?.kmBis, gra.mapNotNull { it.kmBis }.maxOrNull()).maxOrNull()
        val kmOk = if (kmVonEingabe != null && kmBisEingabe != null && kmVonPaket != null && kmBisPaket != null) {
            kmVonEingabe >= kmVonPaket - 2.0 && kmBisEingabe <= kmBisPaket + 2.0
        } else true

        val kmText = if (kmVonPaket != null && kmBisPaket != null) {
            " · km ${formatKmKurz(kmVonPaket)}–${formatKmKurz(kmBisPaket)}"
        } else ""
        val achsenText = " · ${passendeAchsen.size} Streckenachse(n)"
        val bahnhofHinweis = if (bahnhofsObjekteZurStrecke.isNotEmpty()) {
            " · Bahnhofsdaten separat: ${bahnhofsObjekteZurStrecke.size}"
        } else ""
        return if (kmOk) {
            DatenStatus(true, "✓ Strecke $streckenkennung vorhanden$kmText$achsenText$bahnhofHinweis")
        } else {
            DatenStatus(false, "⚠ Strecke $streckenkennung vorhanden$kmText · Eingabe außerhalb")
        }
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

    private fun graNamePasstZuKennziffer(quelleDatei: String?, name: String?, kennziffer: String): Boolean {
        val text = listOfNotNull(quelleDatei, name).joinToString("_").uppercase(Locale.GERMANY)
        return when (kennziffer) {
            "0" -> Regex("(^|[_-])S([_-]|$)").containsMatchIn(text)
            "1" -> Regex("(^|[_-])(L|SL)([_-]|$)").containsMatchIn(text)
            "2" -> Regex("(^|[_-])R([_-]|$)").containsMatchIn(text)
            else -> true
        }
    }

    private suspend fun pruefeTrassendatenBahnhofGleis(code: String, nrRaw: String): DatenStatus? {
        val uebersicht = runCatching { trassenRepo.ladeUebersicht() }.getOrNull() ?: return null
        if (uebersicht.istLeer) return null

        val zuordnungen = ladeStreckendatenZuordnungen()
        val nutztZuordnung = zuordnungen.isNotEmpty()
        val zielCode = code.trim().uppercase(Locale.GERMANY)

        val alleGleisAchsen = uebersicht.achsen
            .filter { it.achsTyp == de.tbhelfer.datenbank.TrassenAchseEntity.ACHSTYP_GLEIS && !it.gleis.isNullOrBlank() }

        // Patch45d: Manuell festgelegte Gleisabschnitte (z. B. Gl 28 auf einer roten Rohachse)
        // muessen bei der Baustellen-/Unterbaustellen-Pruefung genauso als vorhandenes Gleis gelten.
        val alleManuellenGleisabschnitte = runCatching { ServiceLocator.trassenDao.alleManuellenAbschnitte() }
            .getOrDefault(emptyList())
            .filter {
                it.achsTyp == de.tbhelfer.datenbank.TrassenAchseEntity.ACHSTYP_GLEIS &&
                    !it.gleis.isNullOrBlank() &&
                    it.aktuelleAchseId != null
            }
        if (alleGleisAchsen.isEmpty() && alleManuellenGleisabschnitte.isEmpty()) return null

        val achsenById = uebersicht.achsen.associateBy { it.id }
        val gleisAchsen = if (nutztZuordnung) {
            alleGleisAchsen.filter { achse -> zuordnungen.any { it.passtZuAchse(achse, zielCode, "BAHNHOFS_GLEIS") } }
        } else {
            alleGleisAchsen
        }
        val manuelleGleisabschnitte = if (nutztZuordnung) {
            alleManuellenGleisabschnitte.filter { abschnitt ->
                val rohAchse = abschnitt.aktuelleAchseId?.let(achsenById::get) ?: return@filter false
                zuordnungen.any { it.passtZuAchseUnabhaengigVomTyp(rohAchse, zielCode) }
            }
        } else {
            alleManuellenGleisabschnitte
        }

        val manuelleGleisNamen = manuelleGleisabschnitte.mapNotNull { it.gleis?.trim() }.distinct()
        val globaleManuelleGleisNamen = alleManuellenGleisabschnitte.mapNotNull { it.gleis?.trim() }.distinct()
        val vorhandeneGleise = (gleisAchsen.mapNotNull { it.gleis?.trim() } + manuelleGleisNamen)
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        val globaleGleise = (alleGleisAchsen.mapNotNull { it.gleis?.trim() } + globaleManuelleGleisNamen)
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        if (nrRaw.isBlank()) {
            return if (vorhandeneGleise.isNotEmpty()) {
                DatenStatus(true, "✓ $zielCode: Gl ${vorhandeneGleise.joinToString("/")} zugeordnet")
            } else if (nutztZuordnung) {
                DatenStatus(false, "✗ Keine Gleise fuer $zielCode zugeordnet · global vorhanden: ${globaleGleise.joinToString("/")}")
            } else {
                DatenStatus(true, "✓ Vermessungsdaten vorhanden: Gl ${globaleGleise.joinToString("/")}")
            }
        }

        val objektModus = bahnhofObjektModus()
        if (objektModus == "DKW_KREUZUNG") {
            val vorhanden = uebersicht.achsen.isNotEmpty()
            return DatenStatus(
                vorhanden,
                if (vorhanden) "✓ TRA-Geometrie vorhanden · Achsen D–A / C–B werden nach den GPS-Punkten geprüft"
                else "✗ Keine TRA-Geometrie importiert · GPS-Punkte können trotzdem später gesetzt werden"
            )
        }
        if (objektModus == "WEICHE" || objektModus == "WEICHENVERBINDUNG") {
            val istWv = objektModus == "WEICHENVERBINDUNG"
            val zielAchsTyp = if (istWv) {
                de.tbhelfer.datenbank.TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG
            } else {
                de.tbhelfer.datenbank.TrassenAchseEntity.ACHSTYP_WEICHE
            }
            val zielZuordnungsTyp = if (istWv) "WEICHENVERBINDUNG" else "WEICHE"
            val typLabel = if (istWv) "WV" else "Weiche"
            val alleObjekte = uebersicht.achsen.filter { it.achsTyp == zielAchsTyp }
            val objekte = if (nutztZuordnung) {
                alleObjekte.filter { achse ->
                    zuordnungen.any { it.passtZuAchse(achse, zielCode, zielZuordnungsTyp) }
                }
            } else {
                alleObjekte
            }
            if (alleObjekte.isEmpty()) return DatenStatus(false, "✗ Keine ${typLabel}-Daten importiert")
            if (objekte.isEmpty() && nutztZuordnung) return DatenStatus(false, "✗ Keine ${typLabel} fuer $zielCode zugeordnet")

            val ziel = normWeiche(nrRaw)
            if (ziel.isBlank()) return DatenStatus(true, "✓ $zielCode: ${typLabel}-Daten vorhanden: ${objekte.size} Achse(n)")

            val passend = objekte.firstOrNull { achse ->
                val roh = normWeiche(achse.name)
                val dbName = normWeiche(ladeManuellenWeichennamen(achse.name))
                roh == ziel || dbName == ziel || roh.contains(ziel) || ziel.contains(roh)
            }
            return if (passend != null) {
                val db = ladeManuellenWeichennamen(passend.name)?.takeIf { it.isNotBlank() }
                val name = db ?: passend.name
                    .removePrefix("SW_")
                    .removePrefix("WVB_")
                    .removePrefix("WV_")
                    .removeSuffix("_ER0")
                DatenStatus(true, "✓ $zielCode: $typLabel $name vorhanden · ${String.format(Locale.GERMANY, "%.1f", (passend.stationBis ?: 0.0) - (passend.stationVon ?: 0.0))} m")
            } else {
                DatenStatus(false, "✗ $zielCode: $nrRaw nicht zugeordnet · $typLabel vorhanden: ${objekte.size}")
            }
        }

        val gesuchteGleise = gleisListeAusEingabe(nrRaw)
        if (gesuchteGleise.isEmpty()) {
            return DatenStatus(false, "⚠ Gleisangabe unklar. Möglich: 302, 303 oder beide")
        }
        if (vorhandeneGleise.isEmpty() && nutztZuordnung) {
            return DatenStatus(false, "✗ Keine Gleise fuer $zielCode zugeordnet · global vorhanden: ${globaleGleise.joinToString("/")}")
        }
        val fehlend = gesuchteGleise.filter { gesucht -> vorhandeneGleise.none { normGleis(it) == normGleis(gesucht) } }
        val manuellGetroffen = gesuchteGleise.filter { gesucht -> manuelleGleisNamen.any { normGleis(it) == normGleis(gesucht) } }
        return if (fehlend.isEmpty()) {
            val zusatz = if (manuellGetroffen.isNotEmpty()) {
                " · manuell angelegt: Gl ${manuellGetroffen.joinToString("+")}"
            } else {
                " · KM/GVP nutzbar"
            }
            DatenStatus(true, "✓ $zielCode: Gl ${gesuchteGleise.joinToString("+")} vorhanden$zusatz")
        } else {
            DatenStatus(false, "✗ $zielCode: Gl ${fehlend.joinToString("+")} fehlt · zugeordnet: ${vorhandeneGleise.joinToString("/")}")
        }
    }

    private data class StreckendatenZuordnung(
        val rawName: String,
        val typ: String,
        val kuerzel: String,
        val name: String,
        val strecken: List<String>
    ) {
        fun passtZuAchse(
            achse: de.tbhelfer.datenbank.TrassenAchseEntity,
            zielCode: String,
            zielTyp: String
        ): Boolean {
            if (!typ.equals(zielTyp, ignoreCase = true)) return false
            val ziel = zielCode.trim().uppercase(Locale.GERMANY)
            val codeOk = kuerzel.uppercase(Locale.GERMANY) == ziel ||
                name.uppercase(Locale.GERMANY).contains(ziel) ||
                ziel.contains(kuerzel.uppercase(Locale.GERMANY).takeIf { it.isNotBlank() } ?: "#")
            if (!codeOk) return false
            val achsName = normObjektNameLocal(achse.name)
            val raw = normObjektNameLocal(rawName)
            if (achsName == raw || achsName.contains(raw) || raw.contains(achsName)) return true
            if (zielTyp == "BAHNHOFS_GLEIS") {
                val gleis = achse.gleis?.let { normGleisLocal(it) } ?: return false
                val rawGleis = Regex("(?i)(?:GL|GLEIS)[_\\s-]*(\\d+[A-Z]?)").find(rawName)?.groupValues?.getOrNull(1)?.let { normGleisLocal(it) }
                val streckeOk = strecken.isEmpty() || achse.strecke.isNullOrBlank() || strecken.any { it == achse.strecke?.trim() }
                if (rawGleis != null && rawGleis == gleis && streckeOk) return true
            }
            return false
        }

        /**
         * Patch45d: Ein manueller Gleisabschnitt kann auf einer Rohachse liegen, die im ZIP
         * urspruenglich als STRECKE statt als BAHNHOFS_GLEIS zugeordnet war. Fuer die
         * Betriebsstellen-Pruefung zaehlt deshalb die Zuordnung der Rohachse zum Bahnhof,
         * unabhaengig vom urspruenglichen Objekttyp.
         */
        fun passtZuAchseUnabhaengigVomTyp(
            achse: de.tbhelfer.datenbank.TrassenAchseEntity,
            zielCode: String
        ): Boolean {
            val ziel = zielCode.trim().uppercase(Locale.GERMANY)
            val codeOk = kuerzel.uppercase(Locale.GERMANY) == ziel ||
                name.uppercase(Locale.GERMANY).contains(ziel) ||
                ziel.contains(kuerzel.uppercase(Locale.GERMANY).takeIf { it.isNotBlank() } ?: "#")
            if (!codeOk) return false

            val achsName = normObjektNameLocal(achse.name)
            val raw = normObjektNameLocal(rawName)
            if (achsName == raw || achsName.contains(raw) || raw.contains(achsName)) return true

            val achseStrecke = achse.strecke?.trim()
            return strecken.isNotEmpty() && !achseStrecke.isNullOrBlank() && strecken.any { it == achseStrecke }
        }

        private fun normGleisLocal(raw: String): String = raw.trim().trimStart('0').ifBlank { "0" }

        private fun normObjektNameLocal(raw: String): String = raw
            .replace(Regex("_ER0.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("_N00.*", RegexOption.IGNORE_CASE), "")
            .uppercase(Locale.GERMANY)
            .replace(Regex("[^A-Z0-9]+"), "")
            .trim()
    }

    private fun ladeStreckendatenZuordnungen(): List<StreckendatenZuordnung> {
        val prefs = requireContext().getSharedPreferences("tbhelfer_streckendaten_zuordnung", android.content.Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            val keyTeile = key.split("|", limit = 2)
            if (keyTeile.size < 2) return@mapNotNull null
            val teile = (value as? String)?.split("|") ?: return@mapNotNull null
            if (teile.size < 4) return@mapNotNull null
            StreckendatenZuordnung(
                rawName = keyTeile[1],
                typ = teile[3],
                kuerzel = teile[0].trim(),
                name = teile[1].trim(),
                strecken = teile[2].split(",").map { it.trim() }.filter { it.isNotBlank() }
            )
        }
    }

    private fun normObjektName(raw: String): String = raw
        .replace(Regex("_ER0.*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("_N00.*", RegexOption.IGNORE_CASE), "")
        .uppercase(Locale.GERMANY)
        .replace(Regex("[^A-Z0-9]+"), "")
        .trim()

    private data class DatenStatus(val vorhanden: Boolean, val text: String)

    private fun setzeStatusText(view: TextView, vorhanden: Boolean, text: String) {
        view.visibility = View.VISIBLE
        view.text = text
        val colorRes = if (vorhanden) R.color.tb_green else android.R.color.holo_red_dark
        view.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun gleisListeAusEingabe(raw: String): List<String> {
        val t = raw.trim().lowercase(Locale.GERMANY)
        if (t == "beide" || t == "302/303" || t == "302+303") return listOf("302", "303")
        return Regex("\\d+").findAll(raw).map { it.value.trimStart('0').ifBlank { "0" } }.distinct().toList()
    }

    private fun normGleis(raw: String): String = raw.trim().trimStart('0').ifBlank { "0" }

    private fun normWeiche(raw: String?): String {
        var value = raw?.trim().orEmpty().uppercase(Locale.GERMANY).replace(Regex("[^A-Z0-9]"), "")
        if (value.startsWith("WEICHE")) value = value.removePrefix("WEICHE")
        if (value.startsWith("SW")) value = value.removePrefix("SW")
        if (value.startsWith("WVB")) value = value.removePrefix("WVB")
        if (value.startsWith("WV")) value = value.removePrefix("WV")
        if (value.startsWith("W") && value.length > 1 && value.getOrNull(1)?.isDigit() == true) value = value.removePrefix("W")
        return value
    }

    private fun ladeManuellenWeichennamen(rohName: String): String? {
        val gespeichert = requireContext()
            .getSharedPreferences("tbhelfer_weichen_zuordnung", android.content.Context.MODE_PRIVATE)
            .getString(rohName, null)
            ?: return null
        return gespeichert.split("|").firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun formatKmKurz(kmMeter: Double): String = String.format(Locale.GERMANY, "%.3f", kmMeter / 1000.0)

    private fun filterBahnhofVorschlaege(raw: String): List<BahnhofVorschlag> {
        val norm = BahnhofSucheUtil.normalisiereSuchtext(raw)
        if (norm.isBlank()) return emptyList()
        return bahnhofVorschlaege
            .asSequence()
            .filter { it.suchBlob.contains(norm) }
            .sortedWith(
                compareBy<BahnhofVorschlag> { v ->
                    when {
                        v.codeNorm == norm -> 0
                        v.codeNorm.startsWith(norm) -> 1
                        v.nameNorm.startsWith(norm) -> 2
                        else -> 3
                    }
                }.thenBy { it.code.length }
                    .thenBy { it.code }
                    .thenBy { it.name }
            )
            .take(25)
            .toList()
    }

    private fun findeBahnhofVorschlag(raw: String): BahnhofVorschlag? {
        val norm = BahnhofSucheUtil.normalisiereSuchtext(raw)
        if (norm.isBlank()) return null
        bahnhofVorschlaege.filter { it.codeNorm == norm || it.displayNorm == norm }.singleOrNull()?.let { return it }
        return bahnhofVorschlaege.filter { it.nameNorm == norm }.singleOrNull()
    }

    private fun extrahiereBahnhofCode(raw: String, vorschlag: BahnhofVorschlag?): String {
        vorschlag?.let { return it.code }
        val text = raw.trim()
        val direct = text.uppercase(Locale.GERMANY)
        if (direct.length in 2..8 && direct.all { it.isLetterOrDigit() }) return direct
        val klammerStart = text.lastIndexOf('(')
        val klammerEnde = text.lastIndexOf(')')
        if (klammerStart >= 0 && klammerEnde > klammerStart) {
            val code = text.substring(klammerStart + 1, klammerEnde).trim().uppercase(Locale.GERMANY)
            if (code.isNotBlank()) return code
        }
        return text.uppercase(Locale.GERMANY)
    }

    private fun ladeBahnhofVorschlaegeAusAssets(): List<BahnhofVorschlag> {
        val map = linkedMapOf<String, BahnhofVorschlag>()
        fun add(code: String?, name: String?) {
            val c = code?.replace(" ", "")?.trim().orEmpty().uppercase(Locale.GERMANY)
            val n = name?.trim().orEmpty()
            if (c.isBlank() || n.isBlank()) return
            map.putIfAbsent(c, BahnhofVorschlag(code = c, name = n, display = "$n ($c)"))
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

    private fun istGleisWeicheEingabeGueltig(nr: String): Boolean {
        val text = nr.trim()
        if (text.isBlank()) return false
        return when (bahnhofObjektModus()) {
            "WEICHE", "WEICHENVERBINDUNG" -> text.matches(Regex("[0-9A-Za-zÄÖÜäöü /-]+"))
            "DKW_KREUZUNG" -> text.matches(Regex("[0-9A-Za-zÄÖÜäöü /-]+"))
            else -> text.equals("beide", ignoreCase = true) || gleisListeAusEingabe(text).isNotEmpty()
        }
    }

    private fun baueGleisKennung(nr: String): String = nr.trim()

    private fun baueWeichenKennung(nr: String): String = nr.trim()

    private fun ladeBearbeitung(restoreFields: Boolean = true) {
        formApplying = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val knoten = repo.knotenMitId(editKnotenId)
                if (knoten == null) {
                    toast("Baustelle nicht gefunden.")
                    findNavController().navigateUp()
                    return@launch
                }
                editKnoten = knoten
                parentId = knoten.parentId ?: -1L

                rbGrossbaustelle.visibility = View.GONE

                if (knoten.typ == BaustellenKnotenTyp.HAUPT) {
                    txtHauptInfo.text = "Hauptbaustelle bearbeiten"
                    edtHauptbaustellenName.visibility = View.VISIBLE
                    if (restoreFields) edtHauptbaustellenName.setText(knoten.hauptbaustellenName)
                    edtUnterpunktName.visibility = View.GONE
                    txtTypLabel.visibility = View.GONE
                    rgTyp.visibility = View.GONE
                    gruppeFreie.visibility = View.GONE
                    gruppeBahnhof.visibility = View.GONE
                    gruppeAllgemein.visibility = View.GONE
                    return@launch
                }

                edtHauptbaustellenName.visibility = View.GONE
                edtUnterpunktName.visibility = View.VISIBLE
                txtTypLabel.visibility = View.VISIBLE
                rgTyp.visibility = View.VISIBLE

                val root = repo.knotenMitId(knoten.rootId)
                txtHauptInfo.text = "Hauptbaustelle: ${root?.hauptbaustellenName ?: knoten.hauptbaustellenName}"
                if (!restoreFields) { updateJsonStatus(); return@launch }
                edtUnterpunktName.setText(knoten.knotenName.orEmpty())

                when (knoten.typ) {
                    BaustellenKnotenTyp.FREIE_STRECKE -> {
                        rgTyp.check(R.id.rbFreieStrecke)
                        val parts = knoten.streckenkennung.orEmpty().split("-")
                        if (parts.size == 2) {
                            edtStreckennummer.setText(parts[0])
                            val suffix = "-${parts[1]}"
                            val suffixAdapter = spStreckenSuffix.adapter
                            val pos = (0 until suffixAdapter.count).firstOrNull {
                                suffixAdapter.getItem(it)?.toString() == suffix
                            } ?: -1
                            if (pos >= 0) spStreckenSuffix.setSelection(pos)
                        }
                        edtKmVon.setText(formatKm(knoten.kmVon))
                        edtKmBis.setText(formatKm(knoten.kmBis))
                    }

                    BaustellenKnotenTyp.BAHNHOF -> {
                        rgTyp.check(R.id.rbBahnhof)
                        val bahnhofRaw = knoten.bahnhofName.orEmpty()
                        val display = findeBahnhofVorschlag(bahnhofRaw)?.display ?: bahnhofRaw
                        edtBahnhof.setText(display, false)

                        val glw = knoten.gleisOderWeiche.orEmpty().trim()
                        val (art, nummer) = parseGleisWeicheKennung(glw)
                        when (art) {
                            "WV" -> {
                                rbWeichenverbindung.isChecked = true
                            }
                            "W" -> rbWeiche.isChecked = true
                            "DKW" -> rbDkwKreuzung.isChecked = true
                            else -> rbGleis.isChecked = true
                        }
                        aktualisiereStreckendatenButton()
                        edtGleisWeicheNr.setText(nummer)

                        edtKmVonOptional.setText(formatKm(knoten.kmVon))
                        edtKmBisOptional.setText(formatKm(knoten.kmBis))
                    }

                    BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> {
                        rgTyp.check(R.id.rbAllgemein)
                        edtKmVonAllg.setText(formatKm(knoten.kmVon))
                        edtKmBisAllg.setText(formatKm(knoten.kmBis))
                    }

                    BaustellenKnotenTyp.HAUPT -> Unit
                }
                updateJsonStatus()
            } finally {
                formApplying = false
            }
        }
    }

    private fun speichern() {
        if (editKnotenId != -1L) {
            speichernBearbeitung()
            return
        }

        val checked = rgTyp.checkedRadioButtonId
        if (checked == R.id.rbGrossbaustelle) {
            if (parentId != -1L) {
                toast("Großbaustelle kann nur als Hauptbaustelle angelegt werden.")
                return
            }
            val hauptName = edtHauptbaustellenName.text.toString().trim()
            if (hauptName.isBlank()) {
                toast("Baustellenname ist Pflicht.")
                return
            }
            speichernNeueHauptbaustelleContainer(hauptName)
            return
        }

        val typ = when (checked) {
            R.id.rbFreieStrecke -> BaustellenKnotenTyp.FREIE_STRECKE
            R.id.rbBahnhof -> BaustellenKnotenTyp.BAHNHOF
            R.id.rbAllgemein -> BaustellenKnotenTyp.ALLGEMEIN_OBJEKT
            else -> null
        }

        if (typ == null) {
            toast("Bitte Freie Strecke, Bahnhof oder Allgemein/Objekt auswählen.")
            return
        }

        val hauptName = if (parentId == -1L) edtHauptbaustellenName.text.toString().trim() else ""
        if (parentId == -1L && hauptName.isBlank()) {
            toast("Baustellenname ist Pflicht.")
            return
        }

        val unterName: String? = if (edtUnterpunktName.visibility == View.VISIBLE) {
            edtUnterpunktName.text.toString().trim().takeIf { it.isNotBlank() }
        } else {
            null
        }

        if (parentId == -1L) {
            speichernNeueHauptbaustelle(hauptName = hauptName, typ = typ)
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    erstelleUnterknotenNeu(
                        parentId = parentId,
                        hauptName = null,
                        unterName = unterName,
                        typ = typ
                    )
                    findNavController().navigateUp()
                } catch (t: Throwable) {
                    toast("Fehler: ${t.message ?: t::class.java.simpleName}")
                }
            }
        }
    }

    private fun speichernBearbeitung() {
        val current = editKnoten ?: return

        if (current.typ == BaustellenKnotenTyp.HAUPT) {
            val neuerName = edtHauptbaustellenName.text.toString().trim()
            if (neuerName.isBlank()) {
                toast("Baustellenname ist Pflicht.")
                return
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    repo.hauptbaustelleUmbenennen(current.id, neuerName)
                    findNavController().navigateUp()
                } catch (t: Throwable) {
                    toast("Fehler: ${t.message ?: t::class.java.simpleName}")
                }
            }
            return
        }

        val typ = when (rgTyp.checkedRadioButtonId) {
            R.id.rbFreieStrecke -> BaustellenKnotenTyp.FREIE_STRECKE
            R.id.rbBahnhof -> BaustellenKnotenTyp.BAHNHOF
            R.id.rbAllgemein -> BaustellenKnotenTyp.ALLGEMEIN_OBJEKT
            else -> null
        }

        if (typ == null) {
            toast("Bitte Freie Strecke, Bahnhof oder Allgemein/Objekt auswählen.")
            return
        }

        val unterName = edtUnterpunktName.text.toString().trim().takeIf { it.isNotBlank() }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aktualisiereUnterknoten(
                    knotenId = editKnotenId,
                    unterName = unterName,
                    typ = typ
                )
                findNavController().navigateUp()
            } catch (t: Throwable) {
                toast("Fehler: ${t.message ?: t::class.java.simpleName}")
            }
        }
    }

    private fun speichernNeueHauptbaustelleContainer(hauptName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val exist = repo.hauptFindenKanonisch(hauptName)
                if (exist != null) {
                    navigiereZuKnoten(exist.id)
                    return@launch
                }
                val haupt = repo.hauptErstellenOderZusammenfuehren(hauptName)
                navigiereZuKnoten(haupt.id)
            } catch (t: Throwable) {
                toast("Fehler: ${t.message ?: t::class.java.simpleName}")
            }
        }
    }

    private fun speichernNeueHauptbaustelle(hauptName: String, typ: BaustellenKnotenTyp) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val exist = repo.hauptFindenKanonisch(hauptName)
                if (exist != null) {
                    val (unter, gesamt) = repo.countsFuerHaupt(exist.id)

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.dialog_merge_title))
                        .setMessage(getString(R.string.dialog_merge_message, exist.hauptbaustellenName, unter, gesamt))
                        .setPositiveButton(getString(R.string.btn_merge)) { _, _ ->
                            // Zusammenführen: Unterknoten direkt unter bestehender Hauptbaustelle anlegen
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    erstelleUnterknotenNeu(
                                        parentId = exist.id,
                                        hauptName = null,
                                        unterName = null,
                                        typ = typ
                                    )
                                    navigiereZuKnoten(exist.id)
                                } catch (t: Throwable) {
                                    toast("Fehler: ${t.message ?: t::class.java.simpleName}")
                                }
                            }
                        }
                        .setNeutralButton(getString(R.string.btn_create_new)) { _, _ ->
                            // Neu anlegen: neuer Root + Unterknoten
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    val knoten = erstelleUnterknotenNeu(
                                        parentId = null,
                                        hauptName = hauptName,
                                        unterName = null,
                                        typ = typ
                                    )
                                    navigiereZuKnoten(knoten.rootId)
                                } catch (t: Throwable) {
                                    toast("Fehler: ${t.message ?: t::class.java.simpleName}")
                                }
                            }
                        }
                        .setNegativeButton(getString(R.string.btn_cancel), null)
                        .setCancelable(true)
                        .show()

                    return@launch
                }

                val knoten = erstelleUnterknotenNeu(
                    parentId = null,
                    hauptName = hauptName,
                    unterName = null,
                    typ = typ
                )
                navigiereZuKnoten(knoten.rootId)

            } catch (t: Throwable) {
                toast("Fehler: ${t.message ?: t::class.java.simpleName}")
            }
        }
    }

    private fun navigiereZuKnoten(rootId: Long) {
        val bundle = Bundle().apply { putLong("knotenId", rootId) }
        val opts = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.baustellenFragment, false)
            .build()
        findNavController().navigate(R.id.baustellenKnotenFragment, bundle, opts)
    }

    private suspend fun erstelleUnterknotenNeu(
        parentId: Long?,
        hauptName: String?,
        unterName: String?,
        typ: BaustellenKnotenTyp
    ) = when (typ) {
        BaustellenKnotenTyp.FREIE_STRECKE -> {
            val streckenkennung = holeStreckenkennung()
                ?: errorResult("Streckenkennung (XXXX-0/-1/-2) ist Pflicht.")
            val kmVon = parseKmPflicht(edtKmVon.text.toString(), "Km von") ?: errorResult("")
            val kmBis = parseKmPflicht(edtKmBis.text.toString(), "Km bis") ?: errorResult("")
            if (kmVon > kmBis) errorResult("Km von darf nicht größer als Km bis sein.")

            if (parentId == null) {
                repo.neueBaustelleUnterHaupt(
                    hauptbaustellenName = hauptName!!,
                    knotenName = unterName,
                    typ = typ,
                    streckenkennung = streckenkennung,
                    kmVon = kmVon,
                    kmBis = kmBis,
                    bahnhofName = null,
                    gleisOderWeiche = null
                )
            } else {
                repo.unterknotenErstellen(
                    parentId = parentId,
                    knotenName = unterName,
                    typ = typ,
                    streckenkennung = streckenkennung,
                    kmVon = kmVon,
                    kmBis = kmBis,
                    bahnhofName = null,
                    gleisOderWeiche = null
                )
            }
        }

        BaustellenKnotenTyp.BAHNHOF -> {
            val bahnhofRaw = edtBahnhof.text.toString().trim()
            val bahnhof = extrahiereBahnhofCode(bahnhofRaw, findeBahnhofVorschlag(bahnhofRaw))
            BahnhofSucheUtil.speichereLetzteEingabe(requireContext(), findeBahnhofVorschlag(bahnhofRaw)?.display ?: bahnhofRaw)
            val nr = edtGleisWeicheNr.text.toString().trim()
            if (bahnhof.isBlank()) errorResult("Bahnhofname ist Pflicht.")
            if (nr.isBlank()) errorResult("Bezeichnung ist Pflicht.")
            if (!istGleisWeicheEingabeGueltig(nr)) errorResult("Nummer ist ungültig. Möglich: 302, 303, 302/303, beide, W 26, DKW 15 oder Kreuzung 3.")
            val el = baueGleisWeicheKennung(nr)

            val (kmVon, kmBis) = parseKmOptional(
                edtKmVonOptional.text.toString(),
                edtKmBisOptional.text.toString()
            )

            if (parentId == null) {
                repo.neueBaustelleUnterHaupt(
                    hauptbaustellenName = hauptName!!,
                    knotenName = unterName,
                    typ = typ,
                    streckenkennung = null,
                    kmVon = kmVon,
                    kmBis = kmBis,
                    bahnhofName = bahnhof,
                    gleisOderWeiche = el
                )
            } else {
                repo.unterknotenErstellen(
                    parentId = parentId,
                    knotenName = unterName,
                    typ = typ,
                    streckenkennung = null,
                    kmVon = kmVon,
                    kmBis = kmBis,
                    bahnhofName = bahnhof,
                    gleisOderWeiche = el
                )
            }
        }

        BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> {
            val (kmVon, kmBis) = parseKmOptional(
                edtKmVonAllg.text.toString(),
                edtKmBisAllg.text.toString()
            )

            if (parentId == null) {
                repo.neueBaustelleUnterHaupt(
                    hauptbaustellenName = hauptName!!,
                    knotenName = unterName,
                    typ = typ,
                    streckenkennung = null,
                    kmVon = kmVon,
                    kmBis = kmBis,
                    bahnhofName = null,
                    gleisOderWeiche = null
                )
            } else {
                repo.unterknotenErstellen(
                    parentId = parentId,
                    knotenName = unterName,
                    typ = typ,
                    streckenkennung = null,
                    kmVon = kmVon,
                    kmBis = kmBis,
                    bahnhofName = null,
                    gleisOderWeiche = null
                )
            }
        }

        BaustellenKnotenTyp.HAUPT -> error("Nicht erlaubt")
    }

    private suspend fun aktualisiereUnterknoten(
        knotenId: Long,
        unterName: String?,
        typ: BaustellenKnotenTyp
    ) {
        when (typ) {
            BaustellenKnotenTyp.FREIE_STRECKE -> {
                val streckenkennung = holeStreckenkennung()
                    ?: errorResult("Streckenkennung (XXXX-0/-1/-2) ist Pflicht.")
                val kmVon = parseKmPflicht(edtKmVon.text.toString(), "Km von") ?: errorResult("")
                val kmBis = parseKmPflicht(edtKmBis.text.toString(), "Km bis") ?: errorResult("")
                if (kmVon > kmBis) errorResult("Km von darf nicht größer als Km bis sein.")

                repo.knotenAktualisieren(
                    knotenId = knotenId,
                    knotenName = unterName,
                    typ = typ,
                    streckenkennung = streckenkennung,
                    kmVon = kmVon,
                    kmBis = kmBis,
                    bahnhofName = null,
                    gleisOderWeiche = null
                )
            }

            BaustellenKnotenTyp.BAHNHOF -> {
                val bahnhofRaw = edtBahnhof.text.toString().trim()
                val bahnhof = extrahiereBahnhofCode(bahnhofRaw, findeBahnhofVorschlag(bahnhofRaw))
                BahnhofSucheUtil.speichereLetzteEingabe(requireContext(), findeBahnhofVorschlag(bahnhofRaw)?.display ?: bahnhofRaw)
                val nr = edtGleisWeicheNr.text.toString().trim()
                if (bahnhof.isBlank()) errorResult("Bahnhofname ist Pflicht.")
                if (nr.isBlank()) errorResult("Bezeichnung ist Pflicht.")
                if (!istGleisWeicheEingabeGueltig(nr)) errorResult("Nummer ist ungültig. Möglich: 302, 303, 302/303, beide, W 26, DKW 15 oder Kreuzung 3.")
                val el = baueGleisWeicheKennung(nr)
                val (kmVon, kmBis) = parseKmOptional(
                    edtKmVonOptional.text.toString(),
                    edtKmBisOptional.text.toString()
                )

                repo.knotenAktualisieren(
                    knotenId = knotenId,
                    knotenName = unterName,
                    typ = typ,
                    streckenkennung = null,
                    kmVon = kmVon,
                    kmBis = kmBis,
                    bahnhofName = bahnhof,
                    gleisOderWeiche = el
                )
            }

            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> {
                val (kmVon, kmBis) = parseKmOptional(
                    edtKmVonAllg.text.toString(),
                    edtKmBisAllg.text.toString()
                )

                repo.knotenAktualisieren(
                    knotenId = knotenId,
                    knotenName = unterName,
                    typ = typ,
                    streckenkennung = null,
                    kmVon = kmVon,
                    kmBis = kmBis,
                    bahnhofName = null,
                    gleisOderWeiche = null
                )
            }

            BaustellenKnotenTyp.HAUPT -> error("Nicht erlaubt")
        }
    }

    /**
     * Erwartet: Streckennummer (4 Ziffern) + Dropdown -0/-1/-2.
     */
    private fun holeStreckenkennung(): String? {
        val nr = edtStreckennummer.text.toString().trim()
        if (!nr.matches(Regex("\\d{4}"))) return null
        val suffix = spStreckenSuffix.selectedItem?.toString() ?: return null
        val kennung = "$nr$suffix"
        return if (kennung.matches(Regex("\\d{4}-(0|1|2)"))) kennung else null
    }

    /**
     * Km-Format: NNN.NNN (nur Punkt, genau 3 Nachkommastellen).
     */
    private fun parseKm(text: String): Double? {
        val t = text.trim()
        if (t.isBlank()) return null
        if (!t.matches(Regex("\\d{1,3}\\.\\d{3}"))) return null
        return t.toDoubleOrNull()
    }

    private fun parseKmPflicht(text: String, feld: String): Double? {
        val km = parseKm(text)
        if (km == null) {
            toast("$feld ist Pflicht (Format: 1.233)")
        }
        return km
    }

    private fun parseKmOptional(vonText: String, bisText: String): Pair<Double?, Double?> {
        val von = parseKm(vonText)
        val bis = parseKm(bisText)

        if (vonText.trim().isNotBlank() && von == null) {
            errorResult("Km von ist ungültig (Format: 1.233)")
        }
        if (bisText.trim().isNotBlank() && bis == null) {
            errorResult("Km bis ist ungültig (Format: 1.233)")
        }
        if (von == null && bis != null) {
            errorResult("Bitte zuerst Km von eingeben.")
        }
        if (von != null && bis == null) {
            return Pair(von, von)
        }
        if (von != null && bis != null && von > bis) {
            errorResult("Km von darf nicht größer als Km bis sein.")
        }
        return Pair(von, bis)
    }

    private fun formatKm(km: Double?): String {
        return km?.let { String.format(Locale.US, "%.3f", it) }.orEmpty()
    }

    private fun bahnhofObjektModus(): String = when (rgGleisWeiche.checkedRadioButtonId) {
        R.id.rbWeiche -> "WEICHE"
        R.id.rbDkwKreuzung -> "DKW_KREUZUNG"
        R.id.rbWeichenverbindung -> "WEICHENVERBINDUNG"
        else -> "GLEIS"
    }

    private fun baueGleisWeicheKennung(nummer: String): String {
        val nr = nummer.trim()
        val vorhandeneArt = BaustellenObjektLogik.artAusKennung(nr)
        return when (bahnhofObjektModus()) {
            "WEICHE" -> if (vorhandeneArt == BaustellenObjektArt.WEICHE) nr else "W $nr"
            "DKW_KREUZUNG" -> if (vorhandeneArt == BaustellenObjektArt.DKW_KREUZUNG) nr else "DKW/Kreuzung $nr"
            "WEICHENVERBINDUNG" -> if (vorhandeneArt == BaustellenObjektArt.WEICHENVERBINDUNG) nr else "WV $nr"
            else -> if (vorhandeneArt == BaustellenObjektArt.GLEIS) nr else "Gl $nr"
        }.trim()
    }

    private fun parseGleisWeicheKennung(value: String): Pair<String, String> {
        val glw = value.trim()
        if (glw.isBlank()) return "Gl" to ""
        val art = BaustellenObjektLogik.artAusKennung(glw)
        val nummer = BaustellenObjektLogik.normalisierteObjektKennung(glw)
        return when (art) {
            BaustellenObjektArt.WEICHENVERBINDUNG -> "WV" to nummer
            BaustellenObjektArt.WEICHE -> "W" to nummer
            BaustellenObjektArt.DKW_KREUZUNG -> "DKW" to nummer
            BaustellenObjektArt.GLEIS -> "Gl" to nummer
            BaustellenObjektArt.SONSTIGES -> "Gl" to glw
        }
    }

    private fun aktualisiereStreckendatenButton() {
        if (!::btnStreckendatenOptional.isInitialized) return
        val istBahnhof = rgTyp.checkedRadioButtonId == R.id.rbBahnhof
        val sichtbar = istBahnhof &&
            (bahnhofObjektModus() == "WEICHE" || bahnhofObjektModus() == "DKW_KREUZUNG")
        btnStreckendatenOptional.visibility = if (sichtbar) View.VISIBLE else View.GONE
        txtAltWvHinweis.visibility = View.GONE
    }

    private fun oeffneOptionaleStreckendaten() {
        val objektModus = bahnhofObjektModus()
        if (objektModus != "WEICHE" && objektModus != "DKW_KREUZUNG") return

        val betriebsstelleRaw = edtBahnhof.text?.toString()?.trim().orEmpty()
        val betriebsstelle = extrahiereBahnhofCode(betriebsstelleRaw, findeBahnhofVorschlag(betriebsstelleRaw))
        val objekt = baueGleisWeicheKennung(edtGleisWeicheNr.text?.toString().orEmpty())
        if (betriebsstelle.isBlank() || objekt.isBlank()) {
            toast("Bitte zuerst Betriebsstelle und Bezeichnung eingeben.")
            return
        }

        val args = Bundle().apply {
            putBoolean("autoOpenStreckenObjektEditor", true)
            putString("prefillBetriebsstelle", betriebsstelle)
            putString("prefillObjekt", objekt)
            putString("prefillObjektTyp", objektModus)
        }
        findNavController().navigate(R.id.importExportFragment, args)
    }


    private fun errorResult(msg: String): Nothing {
        if (msg.isNotBlank()) toast(msg)
        throw IllegalArgumentException(msg.ifBlank { "Ungültige Eingabe" })
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}

