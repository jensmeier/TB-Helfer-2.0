package de.tbhelfer.ui.bautagebuch

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.util.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Auswahl und Verwaltung der Fotos fuer einen konkreten BTB.
 * Moderne BTBs werden strikt ueber ihre stabile btbId abgegrenzt; Datum/Uhrzeit ist nur
 * fuer echte Altbestaende ohne btbId ein Fallback.
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

    private data class ImportTarget(
        val bildtyp: Bildtyp,
        val firma: String?,
        val ortId: Long,
        val ortLabel: String,
        val text: String,
        val time: String?,
        val kmVon: Double?,
        val kmBis: Double?
    )

    private lateinit var tvSub: TextView
    private lateinit var llSections: LinearLayout
    private lateinit var tvSelected: TextView
    private lateinit var btnBack: MaterialButton
    private lateinit var btnImportPhotos: MaterialButton

    private var maxFotos: Int = 60
    private val selection = linkedMapOf<String, LinkedHashSet<Long>>()
    private var autosaveJob: Job? = null

    private var currentBlocks: List<Block> = emptyList()
    private var currentEntry: BautagebuchEintrag? = null
    private var activeBtbIdArg: String? = null
    private var hasOriginalFotoBasis: Boolean = false
    private var originalFotoIds: Set<Long> = emptySet()
    private var textOverrides: Map<Long, String> = emptyMap()
    private var kmOverrides: Map<Long, BtbPhotoKmOverrideStore.KmOverride> = emptyMap()
    private var importTargets: List<ImportTarget> = emptyList()
    private var ortLabels: Map<Long, String> = mapOf(0L to "Hauptbaustelle")
    private var pendingImportTarget: ImportTarget? = null

    private var baustelleIdArg: Long = 0L
    private var baustelleNameArg: String = ""
    private var datumIsoArg: String = ""
    private var shiftStartArg: String? = null
    private var shiftEndArg: String? = null
    private var filterBildtyp: String? = null
    private var schichtTypArg: SchichtTyp = SchichtTyp.TAG

    private val allIdsByTypeInShift = linkedMapOf<String, List<Long>>()

    private val importPhotosLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val target = pendingImportTarget
        pendingImportTarget = null
        if (uris.isNotEmpty()) importExternalPhotos(uris, target)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvSub = view.findViewById(R.id.tvSubHeader)
        llSections = view.findViewById(R.id.llSections)
        tvSelected = view.findViewById(R.id.tvSelectedCount)
        btnBack = view.findViewById(R.id.btnBack)
        btnImportPhotos = view.findViewById(R.id.btnImportPhotos)

        btnBack.setOnClickListener {
            lifecycleScope.launch {
                persistNow()
                findNavController().popBackStack()
            }
        }
        btnImportPhotos.setOnClickListener { launchPhotoImport(null) }

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
                Snackbar.make(requireView(), "Ungueltiges Datum", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val appContext = requireContext().applicationContext
            val fallbackEntry = withContext(Dispatchers.IO) {
                BautagebuchSpeicher.laden(appContext, baustelleIdArg, baustelleNameArg, datum, schichtTypArg)
            }
            val activeBtbId = withContext(Dispatchers.IO) {
                BtbPhotoInstanzKey.activeBtbId(
                    appContext,
                    baustelleIdArg,
                    datumIsoArg,
                    schichtTypArg
                ) ?: fallbackEntry?.btbId
            }
            activeBtbIdArg = activeBtbId
            val effectiveEntry = withContext(Dispatchers.IO) {
                activeBtbId?.takeIf { it.isNotBlank() }?.let {
                    BautagebuchSpeicher.ladenById(appContext, baustelleIdArg, baustelleNameArg, it)
                } ?: fallbackEntry
            }
            currentEntry = effectiveEntry
            maxFotos = effectiveEntry?.maxFotosInPdf ?: 60
            importTargets = withContext(Dispatchers.IO) { buildImportTargets(effectiveEntry) }
            ortLabels = buildMap {
                put(0L, "Hauptbaustelle")
                importTargets.forEach { put(it.ortId, it.ortLabel) }
            }

            tvSub.text = "$baustelleNameArg • $datumIsoArg • Schicht: ${shiftStartArg ?: "-"}–${shiftEndArg ?: "-"}"

            val fotosFuerBtb = withContext(Dispatchers.IO) {
                val all = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(baustelleIdArg)
                if (!activeBtbId.isNullOrBlank()) {
                    all.filter { BtbFotoZuordnungSpeicher.btbId(appContext, it.id) == activeBtbId }
                        .sortedBy { it.erstelltAmEpochMs }
                } else {
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
            }

            val blocks = buildBlocks(fotosFuerBtb, filterBildtyp)
            rebuildAllIds(blocks)

            val excluded = withContext(Dispatchers.IO) {
                BtbPhotoSelectionStore.loadExcluded(
                    appContext,
                    baustelleIdArg,
                    datumIsoArg,
                    shiftStartArg,
                    shiftEndArg,
                    schichtTypArg
                )
            }

            hasOriginalFotoBasis = withContext(Dispatchers.IO) {
                BtbOriginalFotoSpeicher.hatOriginal(appContext, activeBtbId)
            }
            originalFotoIds = withContext(Dispatchers.IO) {
                BtbOriginalFotoSpeicher.originalIds(appContext, activeBtbId)
            }
            textOverrides = withContext(Dispatchers.IO) {
                BtbPhotoTextOverrideStore.loadOverrides(
                    appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg, schichtTypArg
                )
            }
            kmOverrides = withContext(Dispatchers.IO) {
                BtbPhotoKmOverrideStore.loadOverrides(
                    appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg, schichtTypArg
                )
            }

            selection.clear()
            blocks.forEach { block ->
                val excludedIds = excluded[block.blockKey] ?: emptySet()
                selection[block.blockKey] = LinkedHashSet<Long>().apply {
                    block.fotos.forEach { foto -> if (foto.id !in excludedIds) add(foto.id) }
                }
            }
            currentBlocks = blocks
            render(currentBlocks)
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { persistNow() }
    }

    private fun launchPhotoImport(target: ImportTarget?) {
        val btbId = activeBtbIdArg?.trim().orEmpty()
        if (btbId.isBlank()) {
            Snackbar.make(requireView(), "Kein aktiver BTB fuer den Fotoimport", Snackbar.LENGTH_LONG).show()
            return
        }
        pendingImportTarget = target
        importPhotosLauncher.launch(arrayOf("image/*"))
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = lifecycleScope.launch {
            delay(300)
            persistNow()
        }
    }

    private suspend fun persistNow() {
        val totalSel = selection.values.sumOf { it.size }
        if (totalSel > maxFotos) return
        withContext(Dispatchers.IO) {
            val existing = BtbPhotoSelectionStore.loadExcluded(
                requireContext(),
                baustelleIdArg,
                datumIsoArg,
                shiftStartArg,
                shiftEndArg,
                schichtTypArg
            )
            for ((typeKey, allIds) in allIdsByTypeInShift) {
                val selected = selection[typeKey] ?: linkedSetOf()
                val excluded = allIds.filterNot { it in selected }.toSet()
                if (excluded.isEmpty()) existing.remove(typeKey)
                else existing[typeKey] = excluded.toMutableSet()
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

    private suspend fun buildImportTargets(entry: BautagebuchEintrag?): List<ImportTarget> {
        if (entry == null) return emptyList()
        val knownFirms = entry.firmen
            .asSequence()
            .filter { !it.isDeleted }
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        val parsed = BtbManualEntryPhotoTargetParser.parse(entry.notizen, knownFirms)
        if (parsed.isEmpty()) return emptyList()

        val nodeCache = hashMapOf<Long, BaustellenKnotenEntity?>()
        suspend fun node(id: Long): BaustellenKnotenEntity? {
            if (id <= 0L) return null
            if (nodeCache.containsKey(id)) return nodeCache[id]
            val value = ServiceLocator.baustellenBaumRepository.knotenMitId(id)
            nodeCache[id] = value
            return value
        }

        return parsed.map { target ->
            val knoten = node(target.ortId)
            val useNodeKm = target.kmVon == null
            ImportTarget(
                bildtyp = Bildtyp.FOTODOKUMENTATION,
                firma = target.firma,
                ortId = target.ortId,
                ortLabel = if (target.ortId == 0L) "Hauptbaustelle" else knoten?.let(::displayOrt) ?: "Ort ${target.ortId}",
                text = target.text,
                time = target.time,
                kmVon = target.kmVon ?: knoten?.kmVon,
                kmBis = if (useNodeKm) knoten?.kmBis else target.kmBis
            )
        }
    }

    private fun displayOrt(k: BaustellenKnotenEntity): String {
        val base = k.knotenName?.trim()?.takeIf { it.isNotBlank() } ?: when (k.typ) {
            BaustellenKnotenTyp.FREIE_STRECKE -> k.streckenkennung?.trim()?.takeIf { it.isNotBlank() }
                ?.let { "Freie Strecke $it" } ?: "Freie Strecke"
            BaustellenKnotenTyp.BAHNHOF -> {
                val bhf = k.bahnhofName?.trim().orEmpty()
                val glw = k.gleisOderWeiche?.trim().orEmpty()
                when {
                    bhf.isNotBlank() && glw.isNotBlank() -> "Bahnhof $bhf – $glw"
                    bhf.isNotBlank() -> "Bahnhof $bhf"
                    else -> "Bahnhof"
                }
            }
            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> "Allgemein / Objekt"
            BaustellenKnotenTyp.HAUPT -> k.hauptbaustellenName
        }
        val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
            ?.let { " $it" }.orEmpty()
        return base + km
    }

    private fun buildBlocks(fotos: List<FotoEntity>, filterBildtyp: String?): List<Block> {
        val sectionsAll = listOf(
            "4. Ausgefuehrte Arbeiten" to Bildtyp.FOTODOKUMENTATION,
            "5. Abnahmen" to Bildtyp.ABNAHMEN,
            "6. Einweisung/Unterweisung" to Bildtyp.EINWEISUNG_UNTERWEISUNG,
            "7. Besonderheiten" to Bildtyp.BESONDERHEITEN,
            "8. Maengel/Schaeden" to Bildtyp.MAENGEL_SCHAEDEN
        )
        val sections = if (filterBildtyp.isNullOrBlank()) sectionsAll
        else sectionsAll.filter { it.second.name == filterBildtyp }
        return sections.mapNotNull { (title, typ) ->
            val list = fotos.filter { it.bildtyp == typ }.sortedBy { it.erstelltAmEpochMs }
            when {
                list.isNotEmpty() -> Block(title, typ, list)
                !filterBildtyp.isNullOrBlank() -> Block(title, typ, emptyList())
                typ == Bildtyp.FOTODOKUMENTATION && importTargets.isNotEmpty() -> Block(title, typ, emptyList())
                else -> null
            }
        }
    }

    private fun rebuildAllIds(blocks: List<Block>) {
        allIdsByTypeInShift.clear()
        blocks.forEach { allIdsByTypeInShift[it.blockKey] = it.fotos.map(FotoEntity::id) }
    }

    private fun render(blocks: List<Block>) {
        llSections.removeAllViews()
        val fotoNumberById = blocks
            .flatMap { it.fotos }
            .distinctBy { it.id }
            .sortedBy { it.erstelltAmEpochMs }
            .mapIndexed { index, foto -> foto.id to (index + 1) }
            .toMap()

        blocks.forEach { block ->
            llSections.addView(TextView(requireContext()).apply {
                text = block.sectionTitle
                textSize = 16f
                setPadding(0, 18, 0, 8)
            })

            val blockView = layoutInflater.inflate(R.layout.item_btb_pdf_block, llSections, false)
            val tvTitle = blockView.findViewById<TextView>(R.id.tvTitle)
            val tvCounts = blockView.findViewById<TextView>(R.id.tvCounts)
            val llFotos = blockView.findViewById<LinearLayout>(R.id.llFotos)
            val header = blockView.findViewById<View>(R.id.llHeader)

            tvTitle.text = when {
                block.bildtyp == Bildtyp.FOTODOKUMENTATION && importTargets.isNotEmpty() ->
                    "${importTargets.size} Arbeitseinträge · ${block.fotos.size} Fotos"
                block.fotos.isEmpty() -> "Noch keine Fotos"
                block.fotos.size == 1 -> "1 Foto"
                else -> "${block.fotos.size} Fotos"
            }
            updateCounts(tvCounts, block)
            llFotos.removeAllViews()

            if (block.bildtyp == Bildtyp.FOTODOKUMENTATION && importTargets.isNotEmpty()) {
                addImportTargetRows(llFotos)
            }

            if (block.fotos.isEmpty()) {
                llFotos.addView(TextView(requireContext()).apply {
                    text = "Noch keine Fotos zugeordnet"
                    setPadding(8, 10, 8, 10)
                    alpha = 0.70f
                })
            } else {
                block.fotos.forEach { foto ->
                    val displayNumber = fotoNumberById[foto.id] ?: 0
                    val row = layoutInflater.inflate(R.layout.item_btb_pdf_photo_row, llFotos, false)
                    val img = row.findViewById<ImageView>(R.id.imgThumb)
                    val tvPrimary = row.findViewById<TextView>(R.id.tvPhotoPrimary)
                    val tvMeta = row.findViewById<TextView>(R.id.tvPhotoMeta)
                    val tvDetail = row.findViewById<TextView>(R.id.tvPhotoDetail)
                    val cb = row.findViewById<MaterialCheckBox>(R.id.cb)
                    val btnDelete = row.findViewById<android.widget.ImageButton>(R.id.btnDelete)

                    tvPrimary.text = "Foto $displayNumber | ${photoDisplayText(foto)}"
                    tvMeta.text = photoMetaText(foto)
                    val detail = photoDetailText(foto)
                    tvDetail.text = detail
                    tvDetail.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
                    ThumbnailLoader.loadInto(
                        imageView = img,
                        filePath = foto.dateipfad,
                        placeholderResId = android.R.drawable.ic_menu_report_image,
                        reqW = 320,
                        reqH = 320
                    )

                    val open = View.OnClickListener { showPhotoDialog(foto, displayNumber) }
                    img.setOnClickListener(open)
                    tvPrimary.setOnClickListener(open)
                    tvMeta.setOnClickListener(open)
                    tvDetail.setOnClickListener(open)

                    val set = selection[block.blockKey] ?: LinkedHashSet()
                    cb.setOnCheckedChangeListener(null)
                    cb.isChecked = foto.id in set
                    cb.setOnCheckedChangeListener { _, isChecked ->
                        val totalBefore = selection.values.sumOf { it.size }
                        val selected = selection.getOrPut(block.blockKey) { LinkedHashSet() }
                        if (isChecked) {
                            if (totalBefore >= maxFotos) {
                                cb.isChecked = false
                                Snackbar.make(requireView(), "Limit erreicht ($maxFotos)", Snackbar.LENGTH_SHORT).show()
                                return@setOnCheckedChangeListener
                            }
                            selected.add(foto.id)
                        } else {
                            selected.remove(foto.id)
                        }
                        updateCounts(tvCounts, block)
                        updateSelectedTotal()
                        invalidateClosureForPhotoChange()
                        scheduleAutosave()
                    }

                    btnDelete.setOnClickListener { confirmPhotoAction(foto, displayNumber) }
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

    private fun addImportTargetRows(parent: LinearLayout) {
        val ctx = requireContext()
        fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

        parent.addView(TextView(ctx).apply {
            text = "Fotos direkt zu Arbeitseintrag"
            textSize = 13.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(10), dp(8), dp(4))
        })
        parent.addView(TextView(ctx).apply {
            text = "Mit „Foto +“ werden Firma, Ort/Unterbaustelle, Text und KM dieses Eintrags uebernommen."
            textSize = 11.5f
            alpha = 0.70f
            setPadding(dp(8), 0, dp(8), dp(8))
        })

        importTargets.forEach { target ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
            }
            val texts = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            texts.addView(TextView(ctx).apply {
                text = "${target.firma ?: "Allgemein"} · ${target.ortLabel}"
                textSize = 12.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            texts.addView(TextView(ctx).apply {
                text = targetMeta(target)
                textSize = 12f
                alpha = 0.78f
                maxLines = 2
            })
            row.addView(texts)
            row.addView(MaterialButton(ctx).apply {
                text = "Foto +"
                isAllCaps = false
                setIconResource(android.R.drawable.ic_menu_gallery)
                iconPadding = dp(4)
                contentDescription = "Fotos zu ${target.text} hinzufuegen"
                setOnClickListener { launchPhotoImport(target) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
            parent.addView(row)
        }

        parent.addView(TextView(ctx).apply {
            text = "Zugeordnete Fotos"
            textSize = 12f
            alpha = 0.65f
            setPadding(dp(8), dp(8), dp(8), dp(2))
        })
    }

    private fun targetMeta(target: ImportTarget): String {
        val parts = mutableListOf<String>()
        target.time?.takeIf { it.isNotBlank() }?.let(parts::add)
        targetKmText(target)?.let(parts::add)
        parts.add(target.text)
        return parts.joinToString(" · ")
    }

    private fun targetKmText(target: ImportTarget): String? {
        val von = target.kmVon ?: return null
        val bis = target.kmBis
        val a = String.format(Locale.GERMANY, "%.3f", von)
        return if (bis != null && kotlin.math.abs(bis - von) > 0.0000001) {
            "KM $a-${String.format(Locale.GERMANY, "%.3f", bis)}"
        } else "KM $a"
    }

    private fun importExternalPhotos(uris: List<Uri>, target: ImportTarget?) {
        val btbId = activeBtbIdArg?.trim().orEmpty()
        if (btbId.isBlank() || uris.isEmpty()) return
        val targetType = target?.bildtyp ?: filterBildtyp
            ?.let { runCatching { Bildtyp.valueOf(it) }.getOrNull() }
            ?: Bildtyp.FOTODOKUMENTATION
        val appContext = requireContext().applicationContext
        btnImportPhotos.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val imported = BtbExternalPhotoImporter.importieren(
                    context = appContext,
                    uris = uris,
                    baustelleId = baustelleIdArg,
                    baustelleName = baustelleNameArg,
                    datumIso = datumIsoArg,
                    bildtyp = targetType,
                    btbId = btbId,
                    repository = ServiceLocator.fotoRepository,
                    importContext = target?.let {
                        BtbExternalPhotoImporter.ImportContextData(
                            firma = it.firma,
                            baustellenKnotenId = it.ortId.takeIf { id -> id > 0L },
                            kurztext = it.text,
                            km = it.kmVon
                        )
                    }
                )
                if (target != null && imported.imported.isNotEmpty()) {
                    val ids = imported.imported.map(FotoEntity::id)
                    target.time?.takeIf { it.isNotBlank() }?.let { time ->
                        BtbPhotoStartOverrideStore.setOverrideForIds(
                            appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                            schichtTypArg, ids, time
                        )
                    }
                    target.kmVon?.let { kmVon ->
                        BtbPhotoKmOverrideStore.setOverrideForIds(
                            appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                            schichtTypArg, ids, BtbPhotoKmOverrideStore.KmOverride(kmVon, target.kmBis)
                        )
                    }
                }
                imported
            }
            if (!isAdded || view == null) return@launch
            btnImportPhotos.isEnabled = true
            if (result.imported.isEmpty()) {
                Snackbar.make(requireView(), "Keine Fotos importiert", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            target?.kmVon?.let { kmVon ->
                val override = BtbPhotoKmOverrideStore.KmOverride(kmVon, target.kmBis)
                kmOverrides = kmOverrides.toMutableMap().apply {
                    result.imported.forEach { put(it.id, override) }
                }
            }

            val existing = currentBlocks.flatMap { it.fotos }
            currentBlocks = buildBlocks((existing + result.imported).distinctBy { it.id }, filterBildtyp)
            rebuildAllIds(currentBlocks)
            val targetSelection = selection.getOrPut(targetType.name) { LinkedHashSet() }
            var notSelectedByLimit = 0
            result.imported.forEach { foto ->
                if (selection.values.sumOf { it.size } < maxFotos) targetSelection.add(foto.id)
                else notSelectedByLimit++
            }
            persistNow()
            render(currentBlocks)
            val message = buildString {
                append("${result.imported.size} Foto")
                if (result.imported.size != 1) append("s")
                append(" importiert")
                target?.let { append(" zu „${it.text.take(45)}“") }
                if (result.failed > 0) append(" • ${result.failed} fehlgeschlagen")
                if (notSelectedByLimit > 0) append(" • $notSelectedByLimit wegen PDF-Limit nicht ausgewaehlt")
            }
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showPhotoDialog(foto: FotoEntity, displayNumber: Int) {
        val ctx = requireContext()
        val dp = { value: Int -> (value * resources.displayMetrics.density).toInt() }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), 0)
        }
        val image = ImageView(ctx).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(360)
            )
        }
        ThumbnailLoader.loadInto(image, foto.dateipfad, android.R.drawable.ic_menu_report_image, 1600, 1600)
        root.addView(image)
        root.addView(TextView(ctx).apply {
            text = buildString {
                append(photoDisplayText(foto))
                append("\n${photoMetaText(foto)}")
                photoDetailText(foto).takeIf { it.isNotBlank() }?.let { append("\n$it") }
                append("\n${photoGpsText(foto)}")
            }
            textSize = 14f
            setPadding(dp(8), dp(10), dp(8), dp(4))
        })

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Foto $displayNumber")
            .setView(root)
            .setNegativeButton("Schliessen", null)
            .setPositiveButton("Bearbeiten") { _, _ -> showPhotoEditDialog(foto, displayNumber) }
            .show()
    }

    private fun showPhotoEditDialog(foto: FotoEntity, displayNumber: Int) {
        val ctx = requireContext()
        val dp = { value: Int -> (value * resources.displayMetrics.density).toInt() }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), 0)
        }
        root.addView(TextView(ctx).apply {
            text = if (hasOriginalFotoBasis && foto.id in originalFotoIds) {
                "Originalfoto: Das Bild bleibt unveraendert. Korrekturen gelten fuer diesen BTB/PDF."
            } else {
                "Nachtrag/aktuelles Foto: Korrekturen gelten fuer diesen BTB/PDF."
            }
            textSize = 12f
            alpha = 0.75f
            setPadding(0, 0, 0, dp(8))
        })

        fun field(hint: String, value: String, numeric: Boolean = false): EditText = EditText(ctx).also { edit ->
            edit.hint = hint
            edit.setText(value)
            if (numeric) {
                edit.inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            edit.setSelectAllOnFocus(false)
            root.addView(edit, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        val text = field("BTB/PDF-Text", photoDisplayText(foto))
        val currentKm = kmOverrides[foto.id]
        val kmVon = field(
            "KM von",
            currentKm?.kmVon?.let(::formatKmInput) ?: foto.km?.let(::formatKmInput).orEmpty(),
            numeric = true
        )
        val kmBis = field("KM bis (optional)", currentKm?.kmBis?.let(::formatKmInput).orEmpty(), numeric = true)
        val firma = field("Firma", foto.firma)
        val objekt = field("Objekt / Gleis / Weiche", foto.objektKennung.orEmpty())
        val bereich = field("Bereich", foto.objektBereich.orEmpty())

        var selectedOrtId = foto.baustellenKnotenId ?: 0L
        if (importTargets.isNotEmpty()) {
            root.addView(TextView(ctx).apply {
                text = "Arbeitseintrag / Ort"
                textSize = 12.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(3))
            })
            val currentTarget = findMatchingTarget(foto)
            val btnTarget = MaterialButton(ctx).apply {
                isAllCaps = false
                setIconResource(android.R.drawable.ic_menu_gallery)
                text = currentTarget?.let(::targetButtonText)
                    ?: "Arbeitseintrag zuordnen"
            }
            btnTarget.setOnClickListener {
                val labels = importTargets.map(::targetButtonText).toTypedArray()
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("Foto einem Arbeitseintrag zuordnen")
                    .setItems(labels) { _, which ->
                        val target = importTargets[which]
                        selectedOrtId = target.ortId
                        text.setText(target.text)
                        firma.setText(target.firma.orEmpty())
                        kmVon.setText(target.kmVon?.let(::formatKmInput).orEmpty())
                        kmBis.setText(target.kmBis?.let(::formatKmInput).orEmpty())
                        btnTarget.text = targetButtonText(target)
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
            root.addView(btnTarget, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            root.addView(TextView(ctx).apply {
                text = "Damit kann ein bereits unter Allgemein gelandetes Foto nachtraeglich dem richtigen Eintrag zugeordnet werden."
                textSize = 11.5f
                alpha = 0.68f
                setPadding(0, dp(2), 0, 0)
            })
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Foto $displayNumber bearbeiten")
            .setView(root)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                fun parseKm(raw: String): Double? = raw.trim().replace(',', '.').takeIf { it.isNotBlank() }?.toDoubleOrNull()
                val vonRaw = kmVon.text?.toString().orEmpty().trim()
                val bisRaw = kmBis.text?.toString().orEmpty().trim()
                val von = parseKm(vonRaw)
                val bis = parseKm(bisRaw)
                if ((vonRaw.isNotBlank() && von == null) || (bisRaw.isNotBlank() && bis == null)) {
                    Snackbar.make(requireView(), "KM ungueltig, z. B. 3,805", Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (bis != null && von == null) {
                    Snackbar.make(requireView(), "Bei KM bis bitte auch KM von eingeben", Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val cleanedText = text.text?.toString().orEmpty().trim()
                val textOverride = cleanedText.takeIf { it.isNotBlank() && it != foto.kurztext.trim() }
                val kmOverride = von?.let { BtbPhotoKmOverrideStore.KmOverride(it, bis) }
                val contextOverride = BtbPhotoContextOverrideStore.ContextOverride(
                    firma = firma.text?.toString().orEmpty().trim(),
                    objektKennung = objekt.text?.toString().orEmpty().trim().takeIf { it.isNotBlank() },
                    objektBereich = bereich.text?.toString().orEmpty().trim().takeIf { it.isNotBlank() },
                    ortId = selectedOrtId
                )
                val appContext = ctx.applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        BtbPhotoTextOverrideStore.setOverrideForIds(
                            appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                            schichtTypArg, listOf(foto.id), textOverride
                        )
                        BtbPhotoKmOverrideStore.setOverrideForIds(
                            appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                            schichtTypArg, listOf(foto.id), kmOverride
                        )
                        BtbPhotoContextOverrideStore.setOverride(
                            appContext, activeBtbIdArg, foto.id, contextOverride
                        )
                    }
                    textOverrides = textOverrides.toMutableMap().apply {
                        if (textOverride == null) remove(foto.id) else put(foto.id, textOverride)
                    }
                    kmOverrides = kmOverrides.toMutableMap().apply {
                        if (kmOverride == null) remove(foto.id) else put(foto.id, kmOverride)
                    }
                    val updated = foto.copy(
                        baustellenKnotenId = selectedOrtId.takeIf { it > 0L },
                        firma = contextOverride.firma,
                        objektKennung = contextOverride.objektKennung,
                        objektBereich = contextOverride.objektBereich
                    )
                    currentBlocks = currentBlocks.map { block ->
                        block.copy(fotos = block.fotos.map { if (it.id == foto.id) updated else it })
                    }
                    invalidateClosureForPhotoChange()
                    render(currentBlocks)
                    dialog.dismiss()
                    Snackbar.make(requireView(), "Foto $displayNumber aktualisiert", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun findMatchingTarget(foto: FotoEntity): ImportTarget? {
        val ortId = foto.baustellenKnotenId ?: 0L
        val displayText = photoDisplayText(foto).trim()
        return importTargets.firstOrNull {
            it.ortId == ortId &&
                it.firma.orEmpty().equals(foto.firma.trim(), ignoreCase = true) &&
                it.text.equals(displayText, ignoreCase = true)
        } ?: importTargets.firstOrNull {
            it.ortId == ortId && it.firma.orEmpty().equals(foto.firma.trim(), ignoreCase = true)
        }
    }

    private fun targetButtonText(target: ImportTarget): String = buildString {
        append(target.firma ?: "Allgemein")
        append(" · ")
        append(target.ortLabel)
        append(" · ")
        append(target.text.take(55))
    }

    private fun photoDisplayText(f: FotoEntity): String =
        textOverrides[f.id]?.trim()?.takeIf { it.isNotBlank() }
            ?: f.kurztext.trim().takeIf { it.isNotBlank() }
            ?: f.notiz?.trim()?.takeIf { it.isNotBlank() }
            ?: "ohne Text"

    private fun photoKmText(f: FotoEntity): String {
        val override = kmOverrides[f.id]
        val von = override?.kmVon ?: f.km ?: return "KM -"
        val bis = override?.kmBis
        fun fmt(v: Double): String = String.format(Locale.GERMANY, "%.3f", v)
        return if (bis != null && kotlin.math.abs(bis - von) > 0.0000001) {
            "KM ${fmt(von)}-${fmt(bis)}"
        } else {
            "KM ${fmt(von)}"
        }
    }

    private fun photoMetaText(f: FotoEntity): String {
        val firma = f.firma.trim().takeIf { it.isNotBlank() } ?: "ohne Firma"
        return listOf(timeOfFoto(f), photoKmText(f), firma).joinToString(" | ")
    }

    private fun photoDetailText(f: FotoEntity): String {
        val out = mutableListOf<String>()
        val ortId = f.baustellenKnotenId ?: 0L
        val ort = ortLabels[ortId] ?: if (ortId == 0L) "Hauptbaustelle" else "Ort $ortId"
        out.add("Ort: $ort")
        f.objektKennung?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
        f.objektBereich?.trim()?.takeIf { it.isNotBlank() }?.let { out.add("Bereich $it") }
        val note = f.notiz?.trim().orEmpty()
        if (note.isNotBlank() && !note.equals(photoDisplayText(f), ignoreCase = true)) out.add(note)
        if (hasOriginalFotoBasis) out.add(if (f.id in originalFotoIds) "Originalfoto" else "Nachtrag")
        return out.joinToString(" | ")
    }

    private fun photoGpsText(f: FotoEntity): String = if (f.gpsLat == 0.0 && f.gpsLon == 0.0) {
        "GPS: nicht im Bild vorhanden"
    } else {
        "GPS: ${String.format(Locale.US, "%.6f", f.gpsLat)}, ${String.format(Locale.US, "%.6f", f.gpsLon)}"
    }

    private fun formatKmInput(value: Double): String = String.format(Locale.GERMANY, "%.3f", value)

    private fun invalidateClosureForPhotoChange() {
        if (!hasOriginalFotoBasis) return
        val entry = currentEntry ?: return
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            BtbDokumentVersionierung.invalidiereAbschlussWegenExternerAenderung(appContext, entry)
        }
    }

    private fun removePhotoFromCurrentBtb(f: FotoEntity, displayNumber: Int) {
        selection.getOrPut(f.bildtyp.name) { LinkedHashSet() }.remove(f.id)
        render(currentBlocks)
        invalidateClosureForPhotoChange()
        scheduleAutosave()
        Snackbar.make(requireView(), "Foto $displayNumber aus BTB/PDF entfernt", Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmPhotoAction(f: FotoEntity, displayNumber: Int) {
        val isProtectedOriginal = hasOriginalFotoBasis && f.id in originalFotoIds
        val base = "${photoDisplayText(f)}\n${photoMetaText(f)}"
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Foto $displayNumber entfernen / loeschen?")
            .setNegativeButton("Abbrechen", null)

        if (isProtectedOriginal) {
            builder
                .setMessage(
                    "$base\n\nDieses Foto gehoert zum unveraenderlichen ersten Abschluss. " +
                        "Es kann nur aus dem aktuellen BTB/PDF entfernt werden; das Original bleibt nachvollziehbar."
                )
                .setPositiveButton("Aus BTB entfernen") { _, _ -> removePhotoFromCurrentBtb(f, displayNumber) }
        } else {
            builder
                .setMessage(
                    "$base\n\nNur aus BTB/PDF entfernen: Das App-Foto bleibt erhalten.\n\n" +
                        "Foto vollstaendig loeschen: App-Foto und Datensatz werden entfernt. " +
                        "Ein urspruenglich aus Galerie/Dateien importiertes Original ausserhalb der App bleibt unveraendert."
                )
                .setNeutralButton("Nur aus BTB/PDF") { _, _ -> removePhotoFromCurrentBtb(f, displayNumber) }
                .setPositiveButton("Foto vollstaendig loeschen") { _, _ -> deletePhotoPermanently(f, displayNumber) }
        }
        builder.show()
    }

    private fun deletePhotoPermanently(f: FotoEntity, displayNumber: Int) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                val result = ServiceLocator.fotoRepository.loeschen(f)
                if (result > 0) {
                    currentEntry?.takeIf { hasOriginalFotoBasis }?.let { entry ->
                        BtbDokumentVersionierung.invalidiereAbschlussWegenExternerAenderung(appContext, entry)
                    }
                    BtbPhotoTextOverrideStore.setOverrideForIds(
                        appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                        schichtTypArg, listOf(f.id), null
                    )
                    BtbPhotoStartOverrideStore.setOverrideForIds(
                        appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                        schichtTypArg, listOf(f.id), null
                    )
                    BtbPhotoKmOverrideStore.setOverrideForIds(
                        appContext, baustelleIdArg, datumIsoArg, shiftStartArg, shiftEndArg,
                        schichtTypArg, listOf(f.id), null
                    )
                }
                result
            }
            if (!isAdded || view == null) return@launch
            if (deleted <= 0) {
                Snackbar.make(requireView(), "Foto konnte nicht geloescht werden", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            selection[f.bildtyp.name]?.remove(f.id)
            allIdsByTypeInShift[f.bildtyp.name] = allIdsByTypeInShift[f.bildtyp.name].orEmpty()
                .filterNot { it == f.id }
            textOverrides = textOverrides - f.id
            kmOverrides = kmOverrides - f.id
            currentBlocks = currentBlocks.mapNotNull { block ->
                val remaining = block.fotos.filterNot { it.id == f.id }
                if (remaining.isEmpty() && filterBildtyp.isNullOrBlank() && block.bildtyp != Bildtyp.FOTODOKUMENTATION) null
                else block.copy(fotos = remaining)
            }
            persistNow()
            render(currentBlocks)
            Snackbar.make(requireView(), "Foto $displayNumber vollstaendig geloescht", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateCounts(tvCounts: TextView, b: Block) {
        val total = b.fotos.size
        val sel = selection[b.blockKey]?.size ?: 0
        tvCounts.text = "📷 $total / ✅ $sel"
    }

    private fun updateSelectedTotal() {
        val totalSel = selection.values.sumOf { it.size }
        tvSelected.text = "Ausgewaehlt: $totalSel / $maxFotos"
    }

    private fun timeOfFoto(f: FotoEntity): String = runCatching {
        java.time.Instant.ofEpochMilli(f.erstelltAmEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(timeFmt)
    }.getOrDefault("--:--")

    private fun shiftWindowEpochMs(datum: LocalDate, start: String?, ende: String?): Pair<Long, Long> {
        fun parseMinutes(s: String?): Int? {
            val p = s?.trim().orEmpty().split(":")
            val h = p.getOrNull(0)?.toIntOrNull() ?: return null
            val m = p.getOrNull(1)?.toIntOrNull() ?: return null
            return h * 60 + m
        }
        val zone = ZoneId.systemDefault()
        val dayStart = datum.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextDayStart = datum.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sMin = parseMinutes(start)
        val eMin = parseMinutes(ende)
        if (sMin == null || eMin == null) return dayStart to nextDayStart
        val from = dayStart + sMin * 60L * 1000L
        val to = if (eMin >= sMin) dayStart + eMin * 60L * 1000L
        else nextDayStart + eMin * 60L * 1000L
        return Pair(from, to)
    }
}
