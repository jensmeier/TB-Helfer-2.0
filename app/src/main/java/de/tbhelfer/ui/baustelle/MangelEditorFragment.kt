package de.tbhelfer.ui.baustelle

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import de.tbhelfer.util.KmAnzeige
import de.tbhelfer.util.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Android 200060:
 * Eigene Vollbildseite fuer "Mangel bearbeiten".
 *
 * Fachlich getrennt:
 * - Unterbaustelle = echte Baustellenknoten-Zuordnung.
 * - Ort / KM / Bereich = optionaler freier Text ("KM 76,235", "Modulgebäude", ...).
 */
class MangelEditorFragment : Fragment(R.layout.fragment_mangel_editor) {

    private data class KnotenAuswahl(
        val id: Long,
        val label: String,
        val istHaupt: Boolean
    )

    private var hauptKnotenId: Long = -1L
    private var altBaustelleId: Long = -1L
    private var baustelleName: String = ""
    private var mangelKey: String = ""

    private var item: MaengellisteExport.MangelItem? = null
    private var status: MangelStatusSpeicher.Wert = MangelStatusSpeicher.Wert()
    private var knotenAuswahl: List<KnotenAuswahl> = emptyList()
    private var selectedKnotenId: Long? = null

    private lateinit var acUnterbaustelle: MaterialAutoCompleteTextView
    private lateinit var etOrtKmBereich: TextInputEditText
    private lateinit var acFirma: MaterialAutoCompleteTextView
    private lateinit var etBeschreibung: TextInputEditText
    private lateinit var etKommentar: TextInputEditText
    private lateinit var btnSpeichern: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hauptKnotenId = arguments?.getLong(ARG_HAUPT_KNOTEN_ID, -1L) ?: -1L
        altBaustelleId = arguments?.getLong(ARG_ALT_BAUSTELLE_ID, -1L) ?: -1L
        baustelleName = arguments?.getString(ARG_BAUSTELLE_NAME).orEmpty()
        mangelKey = arguments?.getString(ARG_MANGEL_KEY).orEmpty()

        acUnterbaustelle = view.findViewById(R.id.acMangelUnterbaustelle)
        etOrtKmBereich = view.findViewById(R.id.etMangelOrtKmBereich)
        acFirma = view.findViewById(R.id.acMangelFirma)
        etBeschreibung = view.findViewById(R.id.etMangelBeschreibung)
        etKommentar = view.findViewById(R.id.etMangelKommentar)
        btnSpeichern = view.findViewById(R.id.btnMangelEditorSpeichern)

        view.findViewById<MaterialButton>(R.id.btnMangelEditorAbbrechen).setOnClickListener {
            findNavController().popBackStack()
        }
        btnSpeichern.setOnClickListener { speichern() }

        if (altBaustelleId <= 0L || mangelKey.isBlank()) {
            Toast.makeText(requireContext(), "Mangel konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
            return
        }

        lade()
    }

    private fun lade() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext().applicationContext
            val loaded = withContext(Dispatchers.IO) {
                val lokal = MaengellisteExport.ladeItems(ctx, altBaustelleId, baustelleName)
                    .firstOrNull { it.key == mangelKey }

                val basis = lokal ?: SyncMangelSpeicher.alle(ctx, altBaustelleId)
                    .firstOrNull { it.mangelKey == mangelKey }
                    ?.let { s ->
                        MaengellisteExport.MangelItem(
                            key = s.mangelKey,
                            datumIso = s.datumIso,
                            zeit = s.zeit,
                            baustelle = baustelleName,
                            bereich = s.bereich,
                            km = s.km,
                            firma = s.firma,
                            beschreibung = s.beschreibung,
                            gps = s.gps,
                            fotos = emptyList(),
                            btbBildNr = s.btbBildNr
                        )
                    }

                val wert = MangelStatusSpeicher.laden(ctx, mangelKey)
                val edited = basis?.copy(
                    bereich = wert.bereichOverride ?: basis.bereich,
                    km = wert.kmOverride ?: basis.km,
                    firma = wert.firmaOverride ?: basis.firma,
                    beschreibung = wert.beschreibungOverride ?: basis.beschreibung
                )

                val knoten = if (hauptKnotenId > 0L) {
                    ServiceLocator.baustellenBaumRepository.alleKnotenZuRootSofort(hauptKnotenId)
                } else {
                    emptyList()
                }
                val firmen = ServiceLocator.fotoRepository.alleFirmen()

                Triple(edited, wert, Pair(knoten, firmen))
            }

            val loadedItem = loaded.first
            if (loadedItem == null) {
                Toast.makeText(requireContext(), "Mangel nicht mehr vorhanden.", Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
                return@launch
            }

            item = loadedItem
            status = loaded.second
            richteOberflaecheEin(loadedItem, loaded.third.first, loaded.third.second)
        }
    }

    private fun richteOberflaecheEin(
        item: MaengellisteExport.MangelItem,
        knoten: List<BaustellenKnotenEntity>,
        firmen: List<String>
    ) {
        val root = requireView()
        root.findViewById<TextView>(R.id.tvMangelEditorUntertitel).text =
            listOfNotNull(
                item.beschreibung.takeIf { it.isNotBlank() && it != "-" },
                formatDatumZeit(item.datumIso, item.zeit)
            ).joinToString("\n")

        root.findViewById<TextView>(R.id.tvMangelEditorBaustelle).text =
            "Baustelle: " + baustelleName.ifBlank { item.baustelle.ifBlank { "Baustelle" } }

        val statusView = root.findViewById<TextView>(R.id.tvMangelEditorStatus)
        statusView.text = MaengellisteExport.statusText(status.status).uppercase()
        val statusColor = MaengellisteExport.statusColor(status.status)
        statusView.setTextColor(statusColor)
        statusView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(Color.argb(24, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor)))
            setStroke(dp(1), statusColor)
        }

        knotenAuswahl = baueKnotenAuswahl(knoten)
        val labels = knotenAuswahl.map { it.label }
        acUnterbaustelle.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        )
        acUnterbaustelle.setOnItemClickListener { _, _, position, _ ->
            selectedKnotenId = knotenAuswahl.getOrNull(position)?.id
        }

        val currentNodeId = bestimmeAktuellenKnoten(item)
        val selected = knotenAuswahl.firstOrNull { it.id == currentNodeId }
            ?: knotenAuswahl.firstOrNull { auswahl ->
                normalisiere(auswahl.label) == normalisiere(item.bereich)
            }
            ?: knotenAuswahl.firstOrNull { it.istHaupt }
            ?: knotenAuswahl.firstOrNull()

        selectedKnotenId = selected?.id
        acUnterbaustelle.setText(selected?.label.orEmpty(), false)

        etOrtKmBereich.setText(
            when {
                status.kmOverride != null -> status.kmOverride
                item.km == "-" -> ""
                else -> item.km
            }
        )
        acFirma.setText(item.firma.takeUnless { it == "-" }.orEmpty(), false)
        acFirma.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                firmen.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
            )
        )
        etBeschreibung.setText(item.beschreibung.takeUnless { it == "-" }.orEmpty())
        etKommentar.setText(status.kommentar)

        zeigeFotos(item)

        val gesperrt = status.status == MangelStatusSpeicher.Status.ERLEDIGT
        if (gesperrt) {
            acUnterbaustelle.isEnabled = false
            etOrtKmBereich.isEnabled = false
            acFirma.isEnabled = false
            etBeschreibung.isEnabled = false
            etKommentar.isEnabled = false
            btnSpeichern.isEnabled = false
            root.findViewById<TextView>(R.id.tvMangelEditorGesperrt).visibility = View.VISIBLE
        }
    }

    private fun baueKnotenAuswahl(knoten: List<BaustellenKnotenEntity>): List<KnotenAuswahl> {
        if (knoten.isEmpty()) {
            return listOf(
                KnotenAuswahl(
                    id = hauptKnotenId.takeIf { it > 0L } ?: 0L,
                    label = "Hauptbaustelle · " + baustelleName.ifBlank { "Baustelle" },
                    istHaupt = true
                )
            )
        }

        return knoten
            .sortedWith(
                compareBy<BaustellenKnotenEntity> { if (it.typ == BaustellenKnotenTyp.HAUPT) 0 else 1 }
                    .thenBy { labelFuerKnoten(it).lowercase() }
            )
            .map { k ->
                KnotenAuswahl(
                    id = k.id,
                    label = if (k.typ == BaustellenKnotenTyp.HAUPT) {
                        "Hauptbaustelle · " + k.hauptbaustellenName
                    } else {
                        labelFuerKnoten(k)
                    },
                    istHaupt = k.typ == BaustellenKnotenTyp.HAUPT
                )
            }
    }

    private fun labelFuerKnoten(k: BaustellenKnotenEntity): String {
        val basis = k.knotenName?.trim().takeUnless { it.isNullOrBlank() }
            ?: listOfNotNull(k.bahnhofName, k.gleisOderWeiche)
                .joinToString(" – ")
                .takeIf { it.isNotBlank() }
            ?: k.streckenkennung?.trim().orEmpty()
        val km = KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
            ?.let { " " + it }
            .orEmpty()
        return (basis + km).ifBlank { "Unterbaustelle" }
    }

    private fun bestimmeAktuellenKnoten(item: MaengellisteExport.MangelItem): Long? {
        status.knotenIdOverride?.let { return it }
        item.hauptFoto?.baustellenKnotenId?.let { return it }
        if (item.bereich.equals("Hauptbaustelle", ignoreCase = true)) {
            return hauptKnotenId.takeIf { it > 0L } ?: 0L
        }
        return null
    }

    private fun zeigeFotos(item: MaengellisteExport.MangelItem) {
        val container = requireView().findViewById<LinearLayout>(R.id.containerMangelEditorFotos)
        container.removeAllViews()
        requireView().findViewById<TextView>(R.id.tvMangelEditorFotosTitel).text =
            "Fotos (" + item.fotos.size + ")"

        if (item.fotos.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "Keine Fotos zugeordnet"
                textSize = 13f
                setPadding(dp(4))
            }
            container.addView(empty)
            return
        }

        item.fotos.forEach { mf ->
            val foto = mf.foto
            val box = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4))
                layoutParams = LinearLayout.LayoutParams(dp(158), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    findNavController().navigate(
                        R.id.fotoDetailFragment,
                        Bundle().apply {
                            putLong(de.tbhelfer.ui.suche.FotoDetailFragment.ARG_FOTO_ID, foto.id)
                        }
                    )
                }
            }

            val image = ImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(118))
            }
            ThumbnailLoader.loadInto(
                image,
                foto.dateipfad,
                android.R.drawable.ic_menu_report_image,
                560,
                420
            )
            box.addView(image)
            box.addView(metaText(MaengellisteExport.fotoArtText(mf.art), true))
            MaengellisteExport.fotoKmText(foto).takeIf { it.isNotBlank() }?.let {
                box.addView(metaText("KM: " + it))
            }
            foto.info1Wert?.trim()?.takeIf { it.isNotBlank() }?.let {
                box.addView(metaText("GVP/Mast: " + it))
            }
            MaengellisteExport.fotoGpsText(foto).takeIf { it.isNotBlank() }?.let {
                box.addView(metaText("GPS: " + it))
            }
            container.addView(box)
        }
    }

    private fun metaText(value: String, bold: Boolean = false): TextView =
        TextView(requireContext()).apply {
            text = value
            textSize = 11f
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun speichern() {
        val current = item ?: return
        if (status.status == MangelStatusSpeicher.Status.ERLEDIGT) return

        val selected = knotenAuswahl.firstOrNull { it.id == selectedKnotenId }
            ?: knotenAuswahl.firstOrNull()
        if (selected == null) {
            Toast.makeText(requireContext(), "Bitte Unterbaustelle auswählen.", Toast.LENGTH_LONG).show()
            return
        }

        val ortKmBereich = etOrtKmBereich.text?.toString()?.trim().orEmpty()
        val firma = acFirma.text?.toString()?.trim().orEmpty()
        val beschreibung = etBeschreibung.text?.toString()?.trim().orEmpty()
        val kommentar = etKommentar.text?.toString()?.trim().orEmpty()
        val fotoIds = current.fotos.map { it.foto.id }.distinct()

        btnSpeichern.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val fotoKnotenId = selected.id.takeIf { !selected.istHaupt && it > 0L }

                withContext(Dispatchers.IO) {
                    val nodeAenderungNoetig = current.fotos.any {
                        it.foto.baustellenKnotenId != fotoKnotenId
                    }
                    if (fotoIds.isNotEmpty() && nodeAenderungNoetig) {
                        ServiceLocator.fotoRepository.updateBaustellenKnotenMitIds(fotoIds, fotoKnotenId)
                    }
                }

                val neuerStatus = status.copy(
                    knotenIdOverride = selected.id,
                    bereichOverride = if (selected.istHaupt) "Hauptbaustelle" else selected.label,
                    kmOverride = ortKmBereich,
                    firmaOverride = firma.takeIf { it.isNotBlank() },
                    beschreibungOverride = beschreibung.takeIf { it.isNotBlank() },
                    kommentar = kommentar
                )
                MangelStatusSpeicher.speichern(requireContext(), mangelKey, neuerStatus)

                Toast.makeText(requireContext(), "Mangel gespeichert", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } catch (t: Throwable) {
                btnSpeichern.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    t.message?.takeIf { it.isNotBlank() } ?: "Mangel konnte nicht gespeichert werden.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun formatDatumZeit(datumIso: String, zeit: String): String {
        val datum = runCatching {
            LocalDate.parse(datumIso).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }.getOrDefault(datumIso)
        return listOf(datum, zeit).filter { it.isNotBlank() && it != "-" }.joinToString(" · ")
    }

    private fun normalisiere(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ARG_HAUPT_KNOTEN_ID = "hauptKnotenId"
        const val ARG_ALT_BAUSTELLE_ID = "altBaustelleId"
        const val ARG_BAUSTELLE_NAME = "baustelleName"
        const val ARG_MANGEL_KEY = "mangelKey"
    }
}
