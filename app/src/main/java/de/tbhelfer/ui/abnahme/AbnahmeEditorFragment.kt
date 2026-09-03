package de.tbhelfer.ui.abnahme

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.ui.kamera.KameraFragment
import de.tbhelfer.util.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AbnahmeEditorFragment : Fragment(R.layout.fragment_abnahme_editor) {

    private lateinit var current: AbnahmeSpeicher.Abnahme
    private var loaded = false
    private var applying = false
    private var pendingPlanPath: String? = null
    /**
     * Die ID des einmal angelegten Vorgangs muss auch dann erhalten bleiben, wenn
     * Android die Editor-View beim Wechsel zur Kamera zerstört und neu erstellt.
     * Ohne diese ID würde jedes Zurückkehren fälschlich eine neue Abnahme anlegen.
     */
    private var currentAbnahmeId: String? = null
    private var unterOptionen: List<BaustellenKnotenEntity> = emptyList()
    private var autoSaveRunnable: Runnable? = null

    private lateinit var tvTitel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var acUnter: MaterialAutoCompleteTextView
    private lateinit var etBezeichnung: TextInputEditText
    private lateinit var etObjekt: TextInputEditText
    private lateinit var etFirma: TextInputEditText
    private lateinit var etWeitereFirmen: TextInputEditText
    private lateinit var etAbnehmender: TextInputEditText
    private lateinit var etAnwesende: TextInputEditText
    private lateinit var etKmVon: TextInputEditText
    private lateinit var etKmBis: TextInputEditText
    private lateinit var acErgebnis: MaterialAutoCompleteTextView
    private lateinit var btnErgebnisAbgenommen: MaterialButton
    private lateinit var btnErgebnisNichtAbgenommen: MaterialButton
    private lateinit var btnErgebnisRestpunkte: MaterialButton
    private lateinit var tilRestpunkte: TextInputLayout
    private lateinit var etRestpunkte: TextInputEditText
    private lateinit var etKommentar: TextInputEditText
    private lateinit var containerAnlagen: LinearLayout
    private lateinit var containerFotos: LinearLayout
    private lateinit var btnDraft: MaterialButton
    private lateinit var btnPdf: MaterialButton
    private lateinit var btnFinish: MaterialButton
    private lateinit var btnReopen: MaterialButton
    private lateinit var btnDelete: MaterialButton

    private val ergebnisListe = listOf(
        AbnahmeSpeicher.Ergebnis.ENTWURF,
        AbnahmeSpeicher.Ergebnis.ABGENOMMEN,
        AbnahmeSpeicher.Ergebnis.NICHT_ABGENOMMEN,
        AbnahmeSpeicher.Ergebnis.ABGENOMMEN_MIT_RESTPUNKTEN,
        AbnahmeSpeicher.Ergebnis.NOCH_ZU_PRUEFEN
    )

    private val planCamera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPlanPath
        pendingPlanPath = null
        if (ok && !path.isNullOrBlank() && File(path).exists()) navigateCrop(path)
        else path?.let { runCatching { File(it).delete() } }
    }

    private val planImagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null || !loaded) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val target = AbnahmeSpeicher.neuePlanOriginalDatei(requireContext(), current, extensionFor(uri, "jpg"))
            val ok = withContext(Dispatchers.IO) { runCatching { AbnahmeSpeicher.kopiereUri(requireContext(), uri, target) }.isSuccess }
            if (ok) navigateCrop(target.absolutePath) else Toast.makeText(requireContext(), "Bild konnte nicht übernommen werden", Toast.LENGTH_LONG).show()
        }
    }

    private val planPdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null || !loaded) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            saveFromFields(showMessage = false)
            val target = AbnahmeSpeicher.neuePlanOriginalDatei(requireContext(), current, "pdf")
            val ok = withContext(Dispatchers.IO) { runCatching { AbnahmeSpeicher.kopiereUri(requireContext(), uri, target) }.isSuccess }
            if (ok) {
                current = AbnahmeSpeicher.anlageHinzufuegen(
                    requireContext(), current.id,
                    AbnahmeSpeicher.Anlage(
                        typ = AbnahmeSpeicher.AnlageTyp.PDF,
                        originalPfad = target.absolutePath,
                        bezeichnung = "Planunterlage PDF"
                    )
                ) ?: current
                renderAnlagen()
                showAnlageEditDialog(current.anlagen.last())
            } else Toast.makeText(requireContext(), "PDF konnte nicht übernommen werden", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPlanPath = savedInstanceState?.getString(STATE_PENDING_PLAN)
        currentAbnahmeId = savedInstanceState?.getString(STATE_ABNAHME_ID)
            ?.takeIf { it.isNotBlank() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_PLAN, pendingPlanPath)
        val id = if (::current.isInitialized) current.id else currentAbnahmeId
        outState.putString(STATE_ABNAHME_ID, id)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupResultButtons()
        setupButtons(view)
        setupAutoSave(view)

        viewLifecycleOwner.lifecycleScope.launch {
            val baustelleId = arguments?.getLong(AbnahmelisteFragment.ARG_BAUSTELLE_ID, -1L) ?: -1L
            val baustelleName = arguments?.getString(AbnahmelisteFragment.ARG_BAUSTELLE_NAME).orEmpty()
            val hauptKnotenId = arguments?.getLong(AbnahmelisteFragment.ARG_HAUPT_KNOTEN_ID, -1L)?.takeIf { it > 0L }
            // Vorrang: bereits laufender Editor -> gespeicherter Fragment-Zustand -> Navigationsargument.
            // So bleibt genau derselbe Vorgang beim Kamera-/Plan-Rückweg erhalten.
            val id = when {
                ::current.isInitialized -> current.id
                !currentAbnahmeId.isNullOrBlank() -> currentAbnahmeId.orEmpty()
                else -> arguments?.getString(AbnahmelisteFragment.ARG_ABNAHME_ID).orEmpty()
            }

            val existing = AbnahmeSpeicher.laden(requireContext(), id)
            if (existing != null) {
                current = existing
            } else {
                val activeKnotenId = runCatching { ServiceLocator.aktuelleBaustelleSpeicher.aktuellerKnotenId.first() }.getOrNull()
                val activeKnoten = activeKnotenId
                    ?.let { runCatching { ServiceLocator.baustellenBaumRepository.knotenMitId(it) }.getOrNull() }
                    ?.takeIf { hauptKnotenId == null || it.rootId == hauptKnotenId }
                current = AbnahmeSpeicher.neu(
                    requireContext(), baustelleId, baustelleName,
                    hauptKnotenId = hauptKnotenId,
                    unterKnotenId = activeKnoten?.id,
                    unterKnotenName = activeKnoten?.let(::knotenLabel).orEmpty()
                )
            }
            currentAbnahmeId = current.id
            // Das vorhandene Bundle wird aktualisiert, damit auch ein späteres Backstack-Restore
            // die konkrete Abnahme statt eines leeren "Neu"-Aufrufs öffnet.
            arguments?.putString(AbnahmelisteFragment.ARG_ABNAHME_ID, current.id)
            loadUnterbaustellen(current.hauptKnotenId ?: hauptKnotenId)
            loaded = true
            applyCurrent()
        }
    }

    override fun onResume() {
        super.onResume()
        if (loaded) {
            AbnahmeSpeicher.laden(requireContext(), current.id)?.let { current = it }
            renderAnlagen()
            renderFotos()
            updateStateUi()
        }
    }

    override fun onPause() {
        if (loaded && !applying && !current.istEndgueltigAbgeschlossen) saveFromFields(showMessage = false)
        super.onPause()
    }

    override fun onDestroyView() {
        autoSaveRunnable?.let { view?.removeCallbacks(it) }
        autoSaveRunnable = null
        super.onDestroyView()
    }

    private fun bindViews(view: View) {
        tvTitel = view.findViewById(R.id.tvAbnahmeEditorTitel)
        tvStatus = view.findViewById(R.id.tvAbnahmeEditorStatus)
        acUnter = view.findViewById(R.id.acAbnahmeUnterbaustelle)
        etBezeichnung = view.findViewById(R.id.etAbnahmeBezeichnung)
        etObjekt = view.findViewById(R.id.etAbnahmeObjekt)
        etFirma = view.findViewById(R.id.etAbnahmeFirma)
        etWeitereFirmen = view.findViewById(R.id.etAbnahmeWeitereFirmen)
        etAbnehmender = view.findViewById(R.id.etAbnahmeAbnehmender)
        etAnwesende = view.findViewById(R.id.etAbnahmeAnwesende)
        etKmVon = view.findViewById(R.id.etAbnahmeKmVon)
        etKmBis = view.findViewById(R.id.etAbnahmeKmBis)
        acErgebnis = view.findViewById(R.id.acAbnahmeErgebnis)
        btnErgebnisAbgenommen = view.findViewById(R.id.btnErgebnisAbgenommen)
        btnErgebnisNichtAbgenommen = view.findViewById(R.id.btnErgebnisNichtAbgenommen)
        btnErgebnisRestpunkte = view.findViewById(R.id.btnErgebnisRestpunkte)
        tilRestpunkte = view.findViewById(R.id.tilAbnahmeRestpunkte)
        etRestpunkte = view.findViewById(R.id.etAbnahmeRestpunkte)
        etKommentar = view.findViewById(R.id.etAbnahmeKommentar)
        containerAnlagen = view.findViewById(R.id.containerAbnahmeAnlagen)
        containerFotos = view.findViewById(R.id.containerAbnahmeFotos)
        btnDraft = view.findViewById(R.id.btnAbnahmeEntwurfSpeichern)
        btnPdf = view.findViewById(R.id.btnAbnahmePdfVorschau)
        btnFinish = view.findViewById(R.id.btnAbnahmeAbschliessen)
        btnReopen = view.findViewById(R.id.btnAbnahmeWiederOeffnen)
        btnDelete = view.findViewById(R.id.btnAbnahmeLoeschen)
    }

    private fun setupResultButtons() {
        btnErgebnisAbgenommen.setOnClickListener {
            selectResult(AbnahmeSpeicher.Ergebnis.ABGENOMMEN)
        }
        btnErgebnisNichtAbgenommen.setOnClickListener {
            selectResult(AbnahmeSpeicher.Ergebnis.NICHT_ABGENOMMEN)
        }
        btnErgebnisRestpunkte.setOnClickListener {
            selectResult(AbnahmeSpeicher.Ergebnis.ABGENOMMEN_MIT_RESTPUNKTEN)
        }
        updateResultButtons(AbnahmeSpeicher.Ergebnis.ENTWURF)
    }

    private fun selectResult(result: AbnahmeSpeicher.Ergebnis) {
        if (!loaded || current.istEndgueltigAbgeschlossen) return
        acErgebnis.setText(AbnahmeSpeicher.ergebnisLabel(result), false)
        tilRestpunkte.visibility = if (result == AbnahmeSpeicher.Ergebnis.ABGENOMMEN_MIT_RESTPUNKTEN) View.VISIBLE else View.GONE
        updateResultButtons(result)
        btnFinish.isEnabled = true
    }

    private fun updateResultButtons(result: AbnahmeSpeicher.Ergebnis) {
        val primary = MaterialColors.getColor(btnErgebnisAbgenommen, androidx.appcompat.R.attr.colorPrimary)
        val surface = MaterialColors.getColor(btnErgebnisAbgenommen, com.google.android.material.R.attr.colorSurface)
        val onSurface = MaterialColors.getColor(btnErgebnisAbgenommen, com.google.android.material.R.attr.colorOnSurface)
        fun style(button: MaterialButton, selected: Boolean) {
            button.backgroundTintList = ColorStateList.valueOf(if (selected) primary else surface)
            button.setTextColor(if (selected) Color.WHITE else onSurface)
            button.strokeColor = ColorStateList.valueOf(if (selected) primary else Color.LTGRAY)
            button.strokeWidth = dp(1)
        }
        style(btnErgebnisAbgenommen, result == AbnahmeSpeicher.Ergebnis.ABGENOMMEN)
        style(btnErgebnisNichtAbgenommen, result == AbnahmeSpeicher.Ergebnis.NICHT_ABGENOMMEN)
        style(btnErgebnisRestpunkte, result == AbnahmeSpeicher.Ergebnis.ABGENOMMEN_MIT_RESTPUNKTEN)
    }

    private fun setupAutoSave(root: View) {
        val fields = listOf(
            acUnter, etBezeichnung, etObjekt, etFirma, etWeitereFirmen, etAbnehmender,
            etAnwesende, etKmVon, etKmBis, acErgebnis, etRestpunkte, etKommentar
        )
        fields.forEach { edit ->
            edit.doAfterTextChanged {
                if (!loaded || applying || current.istEndgueltigAbgeschlossen) return@doAfterTextChanged
                autoSaveRunnable?.let(root::removeCallbacks)
                autoSaveRunnable = Runnable {
                    if (loaded && !applying && !current.istEndgueltigAbgeschlossen) saveFromFields(showMessage = false)
                }.also { root.postDelayed(it, 650L) }
            }
        }
    }

    private fun setupButtons(view: View) {
        view.findViewById<View>(R.id.btnPlanAufnehmen).setOnClickListener {
            if (!loaded) return@setOnClickListener
            saveFromFields(showMessage = false)
            val target = AbnahmeSpeicher.neuePlanOriginalDatei(requireContext(), current)
            pendingPlanPath = target.absolutePath
            val uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".dateianbieter", target)
            planCamera.launch(uri)
        }
        view.findViewById<View>(R.id.btnPlanBildWaehlen).setOnClickListener {
            if (loaded) { saveFromFields(showMessage = false); planImagePicker.launch(arrayOf("image/*")) }
        }
        view.findViewById<View>(R.id.btnPlanPdfWaehlen).setOnClickListener {
            if (loaded) planPdfPicker.launch(arrayOf("application/pdf"))
        }
        view.findViewById<View>(R.id.btnAbnahmeFotoHinzufuegen).setOnClickListener { showFotoArtDialog() }
        btnDraft.setOnClickListener { saveFromFields(showMessage = true) }
        btnPdf.setOnClickListener {
            if (!loaded) return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                saveFromFields(showMessage = false)
                val file = AbnahmeExport.erzeugeEinzelPdf(requireContext(), current)
                openPdf(file)
            }
        }
        btnFinish.setOnClickListener { finishAbnahme() }
        btnReopen.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Abnahme wieder öffnen?")
                .setMessage("Die Abnahme wird wieder bearbeitbar. Die PDF wird nach der nächsten Änderung neu erstellt.")
                .setPositiveButton("Wieder öffnen") { _, _ ->
                    current = AbnahmeSpeicher.wiederOeffnen(requireContext(), current)
                    applyCurrent()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
        btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Abnahme löschen?")
                .setMessage("Die strukturierte Abnahme wird gelöscht. Zugeordnete Kamerafotos bleiben in der Fotodatenbank erhalten.")
                .setPositiveButton("Löschen") { _, _ ->
                    AbnahmeSpeicher.loeschen(requireContext(), current, dateienLoeschen = false)
                    findNavController().navigateUp()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    private suspend fun loadUnterbaustellen(rootId: Long?) {
        unterOptionen = if (rootId != null) {
            runCatching { ServiceLocator.baustellenBaumRepository.alleKnotenZuRoot(rootId).first() }
                .getOrDefault(emptyList())
                .filter { it.id != rootId }
        } else emptyList()
        val labels = unterOptionen.map(::knotenLabel)
        acUnter.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels))
        acUnter.setOnClickListener { acUnter.showDropDown() }
    }

    private fun applyCurrent() {
        applying = true
        try {
            tvTitel.text = "Abnahme ${current.nummer.toString().padStart(3, '0')}"
            acUnter.setText(current.unterKnotenName, false)
            etBezeichnung.setText(current.bezeichnung)
            etObjekt.setText(current.objekt)
            etFirma.setText(current.firma)
            etWeitereFirmen.setText(current.weitereFirmen)
            etAbnehmender.setText(current.abnehmender)
            etAnwesende.setText(current.anwesende)
            etKmVon.setText(current.kmVon?.let { String.format(Locale.US, "%.3f", it) }.orEmpty())
            etKmBis.setText(current.kmBis?.let { String.format(Locale.US, "%.3f", it) }.orEmpty())
            acErgebnis.setText(AbnahmeSpeicher.ergebnisLabel(current.ergebnis), false)
            updateResultButtons(current.ergebnis)
            etRestpunkte.setText(current.restpunkte)
            etKommentar.setText(current.kommentar)
            tilRestpunkte.visibility = if (current.ergebnis == AbnahmeSpeicher.Ergebnis.ABGENOMMEN_MIT_RESTPUNKTEN) View.VISIBLE else View.GONE
            renderAnlagen()
            renderFotos()
            updateStateUi()
        } finally { applying = false }
    }

    private fun saveFromFields(showMessage: Boolean): Boolean {
        if (!loaded) return false
        val kmVonRaw = etKmVon.text?.toString().orEmpty().trim()
        val kmBisRaw = etKmBis.text?.toString().orEmpty().trim()
        val kmVon = kmVonRaw.replace(',', '.').toDoubleOrNull()
        val kmBis = kmBisRaw.replace(',', '.').toDoubleOrNull()
        if ((kmVonRaw.isNotBlank() && kmVon == null) || (kmBisRaw.isNotBlank() && kmBis == null)) {
            if (showMessage) Toast.makeText(requireContext(), "KM-Wert ist ungültig", Toast.LENGTH_LONG).show()
            return false
        }
        val unterName = acUnter.text?.toString().orEmpty().trim()
        val unter = unterOptionen.firstOrNull { knotenLabel(it).equals(unterName, true) }
        val ergebnis = ergebnisListe.firstOrNull { AbnahmeSpeicher.ergebnisLabel(it).equals(acErgebnis.text?.toString().orEmpty(), true) }
            ?: current.ergebnis
        current = AbnahmeSpeicher.speichern(
            requireContext(),
            current.copy(
                unterKnotenId = unter?.id ?: current.unterKnotenId,
                unterKnotenName = unterName,
                bezeichnung = etBezeichnung.text?.toString().orEmpty().trim(),
                objekt = etObjekt.text?.toString().orEmpty().trim(),
                firma = etFirma.text?.toString().orEmpty().trim(),
                weitereFirmen = etWeitereFirmen.text?.toString().orEmpty().trim(),
                abnehmender = etAbnehmender.text?.toString().orEmpty().trim(),
                anwesende = etAnwesende.text?.toString().orEmpty().trim(),
                kmVon = kmVon,
                kmBis = kmBis,
                ergebnis = ergebnis,
                restpunkte = etRestpunkte.text?.toString().orEmpty().trim(),
                kommentar = etKommentar.text?.toString().orEmpty().trim()
            )
        )
        if (showMessage) Toast.makeText(requireContext(), "Entwurf gespeichert", Toast.LENGTH_SHORT).show()
        updateStateUi()
        return true
    }

    private fun finishAbnahme() {
        if (!saveFromFields(showMessage = false)) return
        val selected = ergebnisListe.firstOrNull { AbnahmeSpeicher.ergebnisLabel(it).equals(acErgebnis.text?.toString().orEmpty(), true) }
            ?: AbnahmeSpeicher.Ergebnis.ENTWURF
        val missing = buildList {
            if (current.unterKnotenName.isBlank()) add("Unterbaustelle")
            if (current.bezeichnung.isBlank()) add("Bezeichnung")
            if (current.abnehmender.isBlank()) add("Abnehmender")
            if (selected == AbnahmeSpeicher.Ergebnis.ENTWURF) add("Ergebnis")
            if (selected == AbnahmeSpeicher.Ergebnis.ABGENOMMEN_MIT_RESTPUNKTEN && current.restpunkte.isBlank()) add("Restpunkte")
        }
        if (missing.isNotEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Angaben fehlen")
                .setMessage(missing.joinToString("\n") { "• $it" })
                .setPositiveButton("OK", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Abnahme speichern")
            .setMessage("Ergebnis: ${AbnahmeSpeicher.ergebnisLabel(selected)}\n\nAbgenommen und Nicht abgenommen werden abgeschlossen. Restpunkte und Noch zu prüfen bleiben bearbeitbar.")
            .setPositiveButton("Speichern") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    current = AbnahmeSpeicher.abschliessen(requireContext(), current, selected)
                    AbnahmeExport.erzeugeEinzelPdf(requireContext(), current)
                    applyCurrent()
                    Toast.makeText(requireContext(), "Abnahme gespeichert", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showFotoArtDialog() {
        if (!loaded) return
        if (!saveFromFields(showMessage = false)) return
        val values = AbnahmeSpeicher.BildArt.values()
        val labels = values.map(AbnahmeSpeicher::bildArtLabel).toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bildart wählen")
            .setItems(labels) { _, which -> openCameraFor(values[which]) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun openCameraFor(art: AbnahmeSpeicher.BildArt) {
        findNavController().navigate(R.id.kameraFragment, Bundle().apply {
            putBoolean(KameraFragment.ARG_ABNAHME_MODE, true)
            putString(KameraFragment.ARG_ABNAHME_ID, current.id)
            putString(KameraFragment.ARG_ABNAHME_BILD_ART, art.name)
            putLong(KameraFragment.ARG_ABNAHME_BAUSTELLE_ID, current.baustelleId)
            current.hauptKnotenId?.let { putLong(KameraFragment.ARG_ABNAHME_HAUPT_KNOTEN_ID, it) }
            putString(KameraFragment.ARG_ABNAHME_BAUSTELLE_NAME, current.baustelleName)
            current.unterKnotenId?.let { putLong(KameraFragment.ARG_NACHWEIS_KNOTEN_ID, it) }
            putString(KameraFragment.ARG_PREFILL_FIRMA, current.firma)
            putString(KameraFragment.ARG_PREFILL_KURZTEXT, current.bezeichnung.ifBlank { current.objekt }.ifBlank { "Abnahme" })
            putString(KameraFragment.ARG_PREFILL_NOTIZ, "")
            current.kmVon?.let { putDouble(KameraFragment.ARG_PREFILL_KM, it) }
        })
    }

    private fun renderAnlagen() {
        if (!loaded) return
        containerAnlagen.removeAllViews()
        if (current.anlagen.isEmpty()) {
            containerAnlagen.addView(TextView(requireContext()).apply { text = "Noch keine Plan-/Objektunterlage hinterlegt."; textSize = 12f })
            return
        }
        current.anlagen.sortedBy { it.erstelltAmEpochMs }.forEach { anlage ->
            val card = MaterialCardView(requireContext()).apply { radius = dp(10).toFloat(); strokeWidth = dp(1); useCompatPadding = true }
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(7), dp(8), dp(7)) }
            val image = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(80), dp(70)); scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
            if (anlage.typ == AbnahmeSpeicher.AnlageTyp.BILD) {
                val path = anlage.ausschnittPfad?.takeIf { File(it).exists() } ?: anlage.originalPfad
                ThumbnailLoader.loadInto(image, path, android.R.drawable.ic_menu_gallery, 240, 200)
            } else image.setImageResource(android.R.drawable.ic_menu_save)
            val text = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(9) }
                text = buildString {
                    appendLine(anlage.bezeichnung.ifBlank { if (anlage.typ == AbnahmeSpeicher.AnlageTyp.PDF) "Planunterlage PDF" else "Planausschnitt" })
                    if (anlage.bemerkung.isNotBlank()) append(anlage.bemerkung)
                }.trim(); textSize = 12.5f
            }
            val btn = MaterialButton(requireContext()).apply { this.text = "⋮"; minWidth = 0; minHeight = 0; setPadding(dp(10), 0, dp(10), 0); setOnClickListener { showAnlageMenu(anlage) } }
            row.addView(image); row.addView(text); row.addView(btn); card.addView(row); containerAnlagen.addView(card)
        }
    }

    private fun showAnlageMenu(anlage: AbnahmeSpeicher.Anlage) {
        if (current.istEndgueltigAbgeschlossen) {
            openFile(
                File(anlage.ausschnittPfad?.takeIf { File(it).exists() } ?: anlage.originalPfad),
                if (anlage.typ == AbnahmeSpeicher.AnlageTyp.PDF) "application/pdf" else "image/*"
            )
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(anlage.bezeichnung.ifBlank { "Planunterlage" })
            .setItems(arrayOf("Bezeichnung/Bemerkung ändern", "Öffnen", "Aus Abnahme entfernen")) { _, which ->
                when (which) {
                    0 -> showAnlageEditDialog(anlage)
                    1 -> openFile(File(anlage.ausschnittPfad?.takeIf { File(it).exists() } ?: anlage.originalPfad), if (anlage.typ == AbnahmeSpeicher.AnlageTyp.PDF) "application/pdf" else "image/*")
                    2 -> {
                        current = AbnahmeSpeicher.anlageEntfernen(requireContext(), current.id, anlage.id, dateienLoeschen = false) ?: current
                        renderAnlagen()
                    }
                }
            }.show()
    }

    private fun showAnlageEditDialog(anlage: AbnahmeSpeicher.Anlage) {
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(4), dp(18), 0) }
        val name = EditText(requireContext()).apply { hint = "Bezeichnung"; setText(anlage.bezeichnung) }
        val note = EditText(requireContext()).apply { hint = "Bemerkung (optional)"; setText(anlage.bemerkung); minLines = 2 }
        box.addView(name); box.addView(note)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Plan-/Objektunterlage")
            .setView(box)
            .setPositiveButton("Speichern") { _, _ ->
                current = AbnahmeSpeicher.anlageBearbeiten(requireContext(), current.id, anlage.copy(bezeichnung = name.text.toString().trim(), bemerkung = note.text.toString().trim())) ?: current
                renderAnlagen()
            }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun renderFotos() {
        if (!loaded) return
        containerFotos.removeAllViews()
        if (current.fotos.isEmpty()) {
            containerFotos.addView(TextView(requireContext()).apply { text = "Noch keine Fotos dieser Abnahme zugeordnet."; textSize = 12f })
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val entities = ServiceLocator.fotoRepository.fotosMitIds(current.fotos.map { it.fotoId }).associateBy { it.id }
            if (!isAdded) return@launch
            containerFotos.removeAllViews()
            current.fotos.sortedBy { entities[it.fotoId]?.erstelltAmEpochMs ?: it.erstelltAmEpochMs }.forEach { ref ->
                val foto = entities[ref.fotoId] ?: return@forEach
                val card = MaterialCardView(requireContext()).apply { radius = dp(10).toFloat(); strokeWidth = dp(1); useCompatPadding = true }
                val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(7), dp(8), dp(7)) }
                val image = ImageView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(dp(86), dp(76)); scaleType = ImageView.ScaleType.CENTER_CROP }
                ThumbnailLoader.loadInto(image, foto.dateipfad, android.R.drawable.ic_menu_gallery, 260, 220)
                val text = TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(9) }
                    text = buildString {
                        appendLine(AbnahmeSpeicher.bildArtLabel(ref.bildArt))
                        appendLine(formatEpoch(foto.erstelltAmEpochMs))
                        val note = ref.bemerkung.ifBlank { foto.notiz.orEmpty() }
                        if (note.isNotBlank()) append(note)
                    }.trim(); textSize = 12.5f
                }
                val btn = MaterialButton(requireContext()).apply { this.text = "⋮"; minWidth = 0; minHeight = 0; setPadding(dp(10), 0, dp(10), 0); setOnClickListener { showFotoEditDialog(ref, foto.notiz.orEmpty()) } }
                row.addView(image); row.addView(text); row.addView(btn); card.addView(row); containerFotos.addView(card)
            }
        }
    }

    private fun showFotoEditDialog(ref: AbnahmeSpeicher.FotoRef, fallbackNote: String) {
        if (current.istEndgueltigAbgeschlossen) {
            Toast.makeText(requireContext(), "Abnahme ist abgeschlossen. Für Änderungen zuerst wieder öffnen.", Toast.LENGTH_LONG).show()
            return
        }
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), 0) }
        val spinner = Spinner(requireContext())
        val arts = AbnahmeSpeicher.BildArt.values()
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arts.map(AbnahmeSpeicher::bildArtLabel))
        spinner.setSelection(arts.indexOf(ref.bildArt).coerceAtLeast(0))
        val note = EditText(requireContext()).apply { hint = "Bemerkung zum Foto (optional)"; setText(ref.bemerkung.ifBlank { fallbackNote }); minLines = 3 }
        box.addView(spinner); box.addView(note)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Foto bearbeiten")
            .setView(box)
            .setPositiveButton("Speichern") { _, _ ->
                current = AbnahmeSpeicher.fotoBearbeiten(requireContext(), current.id, ref.copy(bildArt = arts[spinner.selectedItemPosition], bemerkung = note.text.toString().trim())) ?: current
                renderFotos()
            }
            .setNeutralButton("Entfernen") { _, _ ->
                current = AbnahmeSpeicher.fotoEntfernen(requireContext(), current.id, ref.fotoId) ?: current
                renderFotos()
            }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun updateStateUi() {
        tvStatus.text = buildString {
            append(AbnahmeSpeicher.statusLabel(current))
            append(" · zuletzt geändert ${formatEpoch(current.geaendertAmEpochMs)}")
            current.abgeschlossenAmEpochMs?.let { append(" · abgeschlossen ${formatEpoch(it)}") }
        }
        val locked = current.istEndgueltigAbgeschlossen
        setFormEnabled(!locked)
        btnReopen.visibility = if (locked) View.VISIBLE else View.GONE
        btnFinish.visibility = if (locked) View.GONE else View.VISIBLE
        btnFinish.isEnabled = !locked && current.ergebnis != AbnahmeSpeicher.Ergebnis.ENTWURF && current.ergebnis != AbnahmeSpeicher.Ergebnis.NOCH_ZU_PRUEFEN
        btnDraft.visibility = if (locked) View.GONE else View.VISIBLE
    }

    private fun setFormEnabled(enabled: Boolean) {
        listOf<View>(acUnter, etBezeichnung, etObjekt, etFirma, etWeitereFirmen, etAbnehmender, etAnwesende, etKmVon, etKmBis, acErgebnis, etRestpunkte, etKommentar).forEach { it.isEnabled = enabled }
        btnErgebnisAbgenommen.isEnabled = enabled
        btnErgebnisNichtAbgenommen.isEnabled = enabled
        btnErgebnisRestpunkte.isEnabled = enabled
        view?.findViewById<View>(R.id.btnPlanAufnehmen)?.isEnabled = enabled
        view?.findViewById<View>(R.id.btnPlanBildWaehlen)?.isEnabled = enabled
        view?.findViewById<View>(R.id.btnPlanPdfWaehlen)?.isEnabled = enabled
        view?.findViewById<View>(R.id.btnAbnahmeFotoHinzufuegen)?.isEnabled = enabled
    }

    private fun navigateCrop(path: String) {
        findNavController().navigate(R.id.planCropFragment, Bundle().apply {
            putString(PlanCropFragment.ARG_ABNAHME_ID, current.id)
            putString(PlanCropFragment.ARG_ORIGINAL_PATH, path)
        })
    }

    private fun openPdf(file: File) = openFile(file, "application/pdf")

    private fun openFile(file: File, mime: String) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".dateianbieter", file)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure { Toast.makeText(requireContext(), "Keine passende App zum Öffnen gefunden", Toast.LENGTH_LONG).show() }
    }

    private fun extensionFor(uri: Uri, fallback: String): String {
        val mime = requireContext().contentResolver.getType(uri).orEmpty()
        return when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg" -> "jpg"
            else -> fallback
        }
    }

    private fun knotenLabel(k: BaustellenKnotenEntity): String = when {
        !k.knotenName.isNullOrBlank() -> k.knotenName!!.trim()
        !k.gleisOderWeiche.isNullOrBlank() -> k.gleisOderWeiche!!.trim()
        !k.bahnhofName.isNullOrBlank() -> k.bahnhofName!!.trim()
        !k.streckenkennung.isNullOrBlank() -> k.streckenkennung!!.trim()
        else -> "Unterbaustelle ${k.id}"
    }

    private fun formatEpoch(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val STATE_PENDING_PLAN = "pendingPlanPath"
        private const val STATE_ABNAHME_ID = "currentAbnahmeId"
    }
}
