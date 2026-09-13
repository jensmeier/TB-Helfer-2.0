package de.tbhelfer.ui.bautagebuch

import de.tbhelfer.util.ThumbnailLoader
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Auswahl der Fotos fürs PDF (Checkbox).
 *
 * Default: alle Fotos des konkreten BTB sind ausgewählt. Moderne, eindeutig einem anderen BTB
 * zugeordnete Fotos werden weder angezeigt noch mitgezählt. Historische Fotos ohne btbId bleiben
 * über den bisherigen Zeitfenster-Fallback auswählbar.
 * Persistiert werden nur abgewählte IDs pro Bildtyp (JSON via BtbPhotoSelectionStore).
 */
class BtbPdfAuswahlFragment : Fragment(R.layout.fragment_btb_pdf_auswahl) {

    private val isoFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private data class Block(
        val sectionTitle: String,
        val bildtyp: Bildtyp,
        val fotos: List<FotoEntity>
    ) {
        val blockKey: String = bildtyp.name
    }

    private lateinit var tvSub: TextView
    private lateinit var llSections: LinearLayout
    private lateinit var tvSelected: TextView
    private lateinit var btnBack: MaterialButton

    private var maxFotos: Int = 60

    // Auswahl: blockKey -> mutable set of selected foto ids
    private val selection = linkedMapOf<String, LinkedHashSet<Long>>()

    private var autosaveJob: Job? = null

    private var baustelleIdArg: Long = 0L
    private var baustelleNameArg: String = ""
    private var datumIsoArg: String = ""
    private var shiftStartArg: String? = null
    private var shiftEndArg: String? = null
    private var filterBildtyp: String? = null
    private var schichtTypArg: SchichtTyp = SchichtTyp.TAG

    // Für Persistenz: komplette Liste pro Typ (nur innerhalb der Schicht UND dieses BTB)
    private val allIdsByTypeInShift = linkedMapOf<String, List<Long>>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvSub = view.findViewById(R.id.tvSubHeader)
        llSections = view.findViewById(R.id.llSections)
        tvSelected = view.findViewById(R.id.tvSelectedCount)
        btnBack = view.findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            lifecycleScope.launch {
                persistNow()
                findNavController().popBackStack()
            }
        }

        lifecycleScope.launch {
            val args = requireArguments()
            baustelleIdArg = args.getLong("baustelleId")
            baustelleNameArg = args.getString("baustelleName").orEmpty()
            datumIsoArg = args.getString("datumIso").orEmpty()
            shiftStartArg = args.getString("shiftStart")
            shiftEndArg = args.getString("shiftEnd")
            filterBildtyp = args.getString("filterBildtyp")
            val st = args.getString("schichtTyp") ?: SchichtTyp.TAG.name
            schichtTypArg = runCatching { SchichtTyp.valueOf(st) }.getOrDefault(SchichtTyp.TAG)

            val datum = runCatching { LocalDate.parse(datumIsoArg, isoFmt) }.getOrNull()
            if (datum == null) {
                Snackbar.make(requireView(), "Ungültiges Datum", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val appContext = requireContext().applicationContext

            // maxFotos kommt aus BTB-Eintrag (Settings) – fallback 60
            val entry = withContext(Dispatchers.IO) {
                BautagebuchSpeicher.laden(requireContext(), baustelleNameArg, datum, schichtTypArg)
            }
            maxFotos = entry?.maxFotosInPdf ?: 60

            // Technische Identität kommt aus dem aktiven stabilen BTB-Pointer. Der sichtbare Name,
            // Datum und TAG/NACHT allein dürfen bei mehreren BTBs am selben Tag nicht entscheiden.
            val activeBtbId = withContext(Dispatchers.IO) {
                BtbPhotoInstanzKey.activeBtbId(
                    appContext,
                    baustelleIdArg,
                    datumIsoArg,
                    schichtTypArg
                ) ?: entry?.btbId
            }

            tvSub.text = "$baustelleNameArg • $datumIsoArg • Schicht: ${shiftStartArg ?: "-"}–${shiftEndArg ?: "-"}"

            val fotosInShift = withContext(Dispatchers.IO) {
                val all = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(baustelleIdArg)
                val (from, to) = shiftWindowEpochMs(datum, shiftStartArg, shiftEndArg)
                all.asSequence()
                    .filter { it.erstelltAmEpochMs in from until to }
                    .filter {
                        BtbFotoZuordnungSpeicher.darfImBtbVerwendetWerden(
                            appContext,
                            it.id,
                            activeBtbId
                        )
                    }
                    .sortedBy { it.erstelltAmEpochMs }
                    .toList()
            }

            // Build blocks by section
            val blocks = buildBlocks(fotosInShift, filterBildtyp)

            // Für Persistenz: pro Typ nur die fachlich zu diesem BTB gehörenden IDs.
            allIdsByTypeInShift.clear()
            blocks.forEach { b ->
                allIdsByTypeInShift[b.blockKey] = b.fotos.map { it.id }
            }

            // Load excluded store
            val excluded = withContext(Dispatchers.IO) {
                BtbPhotoSelectionStore.loadExcluded(
                    requireContext(),
                    baustelleIdArg,
                    datumIsoArg,
                    shiftStartArg,
                    shiftEndArg,
                    schichtTypArg
                )
            }

            // Selection = all - excluded (Default = all selected)
            selection.clear()
            blocks.forEach { b ->
                val allIds = b.fotos.map { it.id }
                val ex = excluded[b.blockKey] ?: emptySet()
                val sel = LinkedHashSet<Long>()
                allIds.forEach { id -> if (!ex.contains(id)) sel.add(id) }
                selection[b.blockKey] = sel
            }

            render(blocks)
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { persistNow() }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = lifecycleScope.launch {
            delay(300)
            persistNow()
        }
    }

    private suspend fun persistNow() {
        // Enforce maxFotos: wenn zu viele selektiert, nicht speichern
        val totalSel = selection.values.sumOf { it.size }
        if (totalSel > maxFotos) return

        withContext(Dispatchers.IO) {
            // MERGE-Strategie:
            // Der Screen kann je nach filterBildtyp nur einen Abschnitt zeigen.
            // Daher dürfen wir nicht die gesamte Datei überschreiben, sonst gehen
            // die excluded anderer Abschnitte verloren (Chips "springen").
            val existing = BtbPhotoSelectionStore.loadExcluded(
                requireContext(),
                baustelleIdArg,
                datumIsoArg,
                shiftStartArg,
                shiftEndArg,
                schichtTypArg
            )

            // Update nur die Typen, die in diesem Screen vorhanden sind (allIdsByTypeInShift).
            // Fremd-BTB-Fotos sind hier absichtlich gar nicht enthalten.
            for ((typeKey, allIds) in allIdsByTypeInShift) {
                val sel = selection[typeKey] ?: linkedSetOf()
                val ex = allIds.filterNot { sel.contains(it) }.toSet()

                if (ex.isEmpty()) {
                    existing.remove(typeKey)
                } else {
                    existing[typeKey] = ex.toMutableSet()
                }
            }

            BtbPhotoSelectionStore.saveExcluded(
                requireContext(),
                baustelleIdArg,
                datumIsoArg,
                shiftStartArg,
                shiftEndArg,
                schichtTypArg,
                existing.mapValues { it.value.toSet() }
            )
        }
    }

    private fun buildBlocks(fotos: List<FotoEntity>, filterBildtyp: String?): List<Block> {
        val sectionsAll = listOf(
            Pair("4. Ausgeführte Arbeiten", Bildtyp.FOTODOKUMENTATION),
            Pair("5. Abnahmen", Bildtyp.ABNAHMEN),
            Pair("6. Einweisung/Unterweisung", Bildtyp.EINWEISUNG_UNTERWEISUNG),
            Pair("7. Besonderheiten", Bildtyp.BESONDERHEITEN),
            Pair("8. Mängel/Schäden", Bildtyp.MAENGEL_SCHAEDEN)
        )

        val sections = if (filterBildtyp.isNullOrBlank()) {
            sectionsAll
        } else {
            sectionsAll.filter { it.second.name == filterBildtyp }
        }

        val out = ArrayList<Block>()
        for ((title, typ) in sections) {
            val list = fotos.filter { it.bildtyp == typ }.sortedBy { it.erstelltAmEpochMs }
            if (list.isNotEmpty()) {
                out.add(Block(title, typ, list))
            } else {
                // Bei Filter zeigen wir die Section trotzdem an
                if (!filterBildtyp.isNullOrBlank()) out.add(Block(title, typ, emptyList()))
            }
        }
        return out
    }

    private fun render(blocks: List<Block>) {
        llSections.removeAllViews()

        blocks.forEach { b ->
            // Section header
            val tv = TextView(requireContext()).apply {
                text = b.sectionTitle
                textSize = 16f
                setPadding(0, 18, 0, 8)
                setTextColor(resources.getColor(android.R.color.black, null))
            }
            llSections.addView(tv)

            val blockView = layoutInflater.inflate(R.layout.item_btb_pdf_block, llSections, false)
            val tvTitle = blockView.findViewById<TextView>(R.id.tvTitle)
            val tvCounts = blockView.findViewById<TextView>(R.id.tvCounts)
            val llFotos = blockView.findViewById<LinearLayout>(R.id.llFotos)
            val header = blockView.findViewById<View>(R.id.llHeader)

            tvTitle.text = timeRangeText(b.fotos)
            updateCounts(tvCounts, b)

            llFotos.removeAllViews()
            if (b.fotos.isEmpty()) {
                val empty = TextView(requireContext()).apply {
                    text = "Keine Fotos"
                    setPadding(0, 8, 0, 8)
                }
                llFotos.addView(empty)
            } else {
                b.fotos.forEach { f ->
                    val row = layoutInflater.inflate(R.layout.item_btb_pdf_photo_row, llFotos, false)
                    val img = row.findViewById<android.widget.ImageView>(R.id.imgThumb)
                    val tvTime = row.findViewById<TextView>(R.id.tvTime)
                    val cb = row.findViewById<MaterialCheckBox>(R.id.cb)

                    tvTime.text = timeOfFoto(f)
                    ThumbnailLoader.loadInto(
                        imageView = img,
                        filePath = f.dateipfad,
                        placeholderResId = android.R.drawable.ic_menu_report_image,
                        reqW = 320,
                        reqH = 320
                    )

                    val set = selection[b.blockKey] ?: LinkedHashSet()
                    cb.setOnCheckedChangeListener(null)
                    cb.isChecked = set.contains(f.id)
                    cb.setOnCheckedChangeListener { _, isChecked ->
                        val totalBefore = selection.values.sumOf { it.size }
                        val set2 = selection.getOrPut(b.blockKey) { LinkedHashSet() }

                        if (isChecked) {
                            if (totalBefore >= maxFotos) {
                                cb.isChecked = false
                                Snackbar.make(requireView(), "Limit erreicht ($maxFotos)", Snackbar.LENGTH_SHORT).show()
                                return@setOnCheckedChangeListener
                            }
                            set2.add(f.id)
                        } else {
                            set2.remove(f.id)
                        }

                        updateCounts(tvCounts, b)
                        updateSelectedTotal()
                        scheduleAutosave()
                    }

                    llFotos.addView(row)
                }
            }

            header.setOnClickListener {
                llFotos.visibility = if (llFotos.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }

            llSections.addView(blockView)
        }

        updateSelectedTotal()
    }

    private fun updateCounts(tvCounts: TextView, b: Block) {
        val total = b.fotos.size
        val sel = selection[b.blockKey]?.size ?: 0
        tvCounts.text = "📷 $total / ✅ $sel"
    }

    private fun updateSelectedTotal() {
        val totalSel = selection.values.sumOf { it.size }
        tvSelected.text = "Ausgewählt: $totalSel / $maxFotos"
    }

    private fun timeOfFoto(f: FotoEntity): String {
        return runCatching {
            val dt = java.time.Instant.ofEpochMilli(f.erstelltAmEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
            dt.format(timeFmt)
        }.getOrDefault("--:--")
    }

    private fun timeRangeText(fotos: List<FotoEntity>): String {
        if (fotos.isEmpty()) return "--:--"
        val first = timeOfFoto(fotos.first())
        val last = timeOfFoto(fotos.last())
        return if (first == last) first else "$first–$last"
    }

    private fun shiftWindowEpochMs(datum: LocalDate, start: String?, ende: String?): Pair<Long, Long> {
        fun parseMinutes(s: String?): Int? {
            val t = s?.trim().orEmpty()
            val p = t.split(":")
            val h = p.getOrNull(0)?.toIntOrNull() ?: return null
            val m = p.getOrNull(1)?.toIntOrNull() ?: return null
            return h * 60 + m
        }

        val zone = ZoneId.systemDefault()
        val dayStart = datum.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextDayStart = datum.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val sMin = parseMinutes(start)
        val eMin = parseMinutes(ende)
        if (sMin == null || eMin == null) return Pair(dayStart, nextDayStart)

        val from = dayStart + sMin * 60L * 1000L
        val to = if (eMin >= sMin) {
            dayStart + eMin * 60L * 1000L
        } else {
            nextDayStart + eMin * 60L * 1000L
        }
        return Pair(from, to)
    }
}
