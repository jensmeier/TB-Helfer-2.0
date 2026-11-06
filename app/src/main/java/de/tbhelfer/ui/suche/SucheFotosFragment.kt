package de.tbhelfer.ui.suche

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import de.tbhelfer.util.ShareFiles
import android.content.ClipData
import kotlinx.coroutines.CancellationException
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import de.tbhelfer.util.requireViewByIdCompat
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.util.AtomicDeleteHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView

/**
 * Ergebnisliste der Foto-Suche (Multi-Select: Teilen/Löschen).
 *
 * Hinweis: Die Sortierung (Neueste/Älteste) wird über [SucheViewModel] gesteuert.
 */
class SucheFotosFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var textLeer: TextView
    private lateinit var tvCount: TextView
    private lateinit var btnSort: MaterialButton
    private var btnToCart: MaterialButton? = null

    private lateinit var selectionBar: LinearLayout
    private lateinit var tvSelection: TextView
    private lateinit var btnCancelSel: MaterialButton
    private lateinit var btnShareSel: MaterialButton
    private lateinit var btnDeleteFileSel: MaterialButton
    private lateinit var btnDeleteDbSel: MaterialButton
    private lateinit var btnSelectAllSel: MaterialButton
    private lateinit var btnInvertSel: MaterialButton
    private lateinit var btnZipSel: MaterialButton

    private lateinit var adapter: SucheFotoAdapter

    private val ansichtSpeicher by lazy { ServiceLocator.ansichtEinstellungenSpeicher }

    private val viewModel: SucheViewModel by activityViewModels {
        SucheViewModel.Fabrik(
            requireContext().applicationContext,
            ServiceLocator.baustellenRepository,
            ServiceLocator.fotoRepository,
            ServiceLocator.streckenpunkteRepository
        )
    }

    private var selectionMode: Boolean = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_suche_liste, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.requireViewByIdCompat(R.id.recycler)
        textLeer = view.requireViewByIdCompat(R.id.textLeer)
        tvCount = view.requireViewByIdCompat(R.id.tvCount)
        btnSort = view.requireViewByIdCompat(R.id.btnSort)
        // Sortierung/Filter wird oben im SucheFragment gesteuert – hier ausblenden
        btnSort.isGone = true
        btnToCart = view.findViewById(R.id.btnToCart)
        btnToCart?.isGone = true

        selectionBar = view.requireViewByIdCompat(R.id.selectionBar)
        tvSelection = view.requireViewByIdCompat(R.id.tvSelection)
        btnCancelSel = view.requireViewByIdCompat(R.id.btnCancelSel)
        btnShareSel = view.requireViewByIdCompat(R.id.btnShareSel)
        btnDeleteFileSel = view.requireViewByIdCompat(R.id.btnDeleteFileSel)
        btnDeleteDbSel = view.requireViewByIdCompat(R.id.btnDeleteDbSel)
        // Atomare Löschung: DB+Datei+Thumbnail in einem Schritt
        btnDeleteDbSel.isGone = true
        btnSelectAllSel = view.requireViewByIdCompat(R.id.btnSelectAllSel)
        btnInvertSel = view.requireViewByIdCompat(R.id.btnInvertSel)
        btnZipSel = view.requireViewByIdCompat(R.id.btnZipSel)

        adapter = SucheFotoAdapter(
            onClick = { item ->
                // normal click only when not in selection mode
                navigateToDetail(item.fotoId)
            },
            onLongClick = {
                // adapter toggles selection internally; we just update UI
                selectionMode = true
                updateSelectionUi()
            },
            onSelectionChanged = { _ ->
                viewModel.setSelectedFotos(adapter.getSelectedIds().toSet())
                updateSelectionUi()
            }
        )

        val glm = GridLayoutManager(requireContext(), 2)
        recycler.layoutManager = glm
        recycler.setHasFixedSize(true)
        recycler.setItemViewCacheSize(8)

        recycler.adapter = adapter

        // Grid-Spalten aus Einstellungen (1/2/3)
        viewLifecycleOwner.lifecycleScope.launch {
            ansichtSpeicher.einstellungen.collectLatest { cfg ->
                val wanted = cfg.sucheGridSpalten.coerceIn(1, 3)
                if (glm.spanCount != wanted) glm.spanCount = wanted
            }
        }

        // Auswahl aus dem Sammelkorb übernehmen (z.B. wenn im anderen Tab ausgewählt / geleert wurde)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedFotoIds.collectLatest { ids ->
                adapter.setSelectedIdsFromViewModel(ids)
                selectionMode = ids.isNotEmpty()
                updateSelectionUi()
            }
        }

        // btnSort listener removed (handled in SucheFragment)
        btnSort.setOnClickListener { /* handled in SucheFragment */ }
        updateSortButton()

        btnCancelSel.setOnClickListener {
            selectionMode = false
            adapter.setSelectionMode(false)
            viewModel.clearFotoSelection()
            updateSelectionUi()
        }

        btnShareSel.setOnClickListener { shareSelected() }
        btnDeleteFileSel.setOnClickListener { confirmDeleteAtomar() }
        btnSelectAllSel.setOnClickListener {
            selectionMode = true
            adapter.selectAll()
            viewModel.setSelectedFotos(adapter.getSelectedIds().toSet())
            updateSelectionUi()
        }
        btnInvertSel.setOnClickListener {
            selectionMode = true
            adapter.invertSelection()
            viewModel.setSelectedFotos(adapter.getSelectedIds().toSet())
            updateSelectionUi()
        }
        btnZipSel.setOnClickListener { showZipDialog() }

        // Observe results
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fotosGefiltert.collectLatest { fotos ->
                val list = withContext(Dispatchers.Default) {
                    fotos.map { f ->
                        val titel = f.kurztext.takeIf { it.isNotBlank() }
                            ?: SucheFotoFormatter.labelBildtyp(f.bildtyp)

                        val zeile1 = buildString {
                            append(SucheFotoFormatter.formatZeit(f.erstelltAmEpochMs))
                            append(" · ")
                            append(f.km?.let { String.format(Locale.GERMANY, "%.3f", it) } ?: "–")
                            append(" km")
                        }

                        val zeile2 = listOfNotNull(
                            f.firma.takeIf { it.isNotBlank() },
                            f.objektKennung?.takeIf { it.isNotBlank() },
                            f.indikator?.name
                        ).joinToString(" · ")

                        SucheFotoItem(
                            fotoId = f.id,
                            dateiPfad = f.dateipfad,
                            titel = titel,
                            zeile1 = zeile1,
                            zeile2 = zeile2,
                            bildtypLabel = SucheFotoFormatter.labelBildtyp(f.bildtyp)
                        )
                    }
                }

                adapter.submitList(list)
                updateEmptyState(list.isEmpty())
                tvCount.text = list.size.toString()
                updateSelectionUi()
            }
        }
    }

    private fun updateSortButton() {
        val isDesc = viewModel.currentFilter().sortDesc
        btnSort.text = if (isDesc) "Neueste" else "Älteste"
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        textLeer.isVisible = isEmpty
        recycler.isGone = isEmpty
    }

    private fun updateSelectionUi() {
        val count = adapter.selectedCount
        val total = adapter.totalCount

        selectionBar.isVisible = selectionMode
        if (!selectionMode) {
            tvSelection.text = ""
            return
        }

        tvSelection.text = getString(R.string.suche_auswahl_count, count)

        // Aktionen nur aktivieren, wenn tatsächlich etwas ausgewählt ist
        val hasSel = count > 0
        btnShareSel.isEnabled = hasSel
        btnDeleteFileSel.isEnabled = hasSel
        btnZipSel.isEnabled = hasSel

        // Hilfsaktionen
        val hasItems = total > 0
        btnSelectAllSel.isEnabled = hasItems
        btnInvertSel.isEnabled = hasItems
    }

    
    private fun showZipDialog() {
        if (adapter.selectedCount <= 0) return

        val hasBtbSel = viewModel.selectedBtbPaths.value.isNotEmpty()
        val options = if (hasBtbSel) {
            arrayOf(
                "Nur Fotos (Auswahl)",
                "Korb: Fotos (Auswahl) + BTB (Auswahl)"
            )
        } else {
            arrayOf("Nur Fotos (Auswahl)")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("ZIP-Export")
            .setItems(options) { _, which ->
                val includeBtbs = hasBtbSel && which == 1
                exportSelectedAsZip(includeBtbsFromKorb = includeBtbs)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportSelectedAsZip(includeBtbsFromKorb: Boolean) {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return
        val ctx = context ?: return
        val btbs = if (includeBtbsFromKorb) selectedBtbsFromKorb() else emptyList()
        val baustellen = viewModel.baustellen.value
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (zip, stats) = withContext(Dispatchers.IO) {
                    val fotos = ServiceLocator.fotoRepository.fotosMitIds(ids.toList())
                    ExportUtils.createZip(
                        context = ctx,
                        fotos = fotos,
                        btbs = btbs,
                        baustellen = baustellen,
                        zipName = if (includeBtbsFromKorb) "tbhelfer_export_fotos_btb_${System.currentTimeMillis()}.zip"
                        else "tbhelfer_export_fotos_${System.currentTimeMillis()}.zip"
                    )
                }
                view?.let { Snackbar.make(it,
                    "ZIP erstellt: ${stats.fotosExportiert} Fotos, ${stats.btbsExportiert} BTB, fehlend: ${stats.fehlend}",
                    Snackbar.LENGTH_LONG).show() }
                ExportUtils.shareSingleFile(ctx, zip, "application/zip", ctx.getString(R.string.app_name))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                view?.let { Snackbar.make(it, "ZIP konnte nicht erstellt oder geteilt werden. Bitte Dateien und freien Speicher prüfen.", Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private fun selectedBtbsFromKorb(): List<BtbDatei> {
        val sel = viewModel.selectedBtbPaths.value
        if (sel.isEmpty()) return emptyList()

        // Primär aus der aktuellen, gefilterten Liste auflösen
        val byPath = viewModel.btbsGefiltert.value.associateBy { it.file.absolutePath }
        return sel.mapNotNull { byPath[it] }
    }


    private fun navigateToDetail(fotoId: Long) {
        val args = bundleOf(FotoDetailFragment.ARG_FOTO_ID to fotoId)
        findNavController().navigate(R.id.fotoDetailFragment, args)
    }

    private fun shareSelected() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val fotos = withContext(Dispatchers.IO) {
                    ServiceLocator.fotoRepository.fotosMitIds(ids.toList())
                }
                // Fail the whole selection visibly instead of silently omitting unreadable photos.
                val uris = fotos.map { ShareFiles.uri(File(it.dateipfad)) }
                if (uris.isEmpty()) {
                    view?.let { Snackbar.make(it, R.string.suche_keine_datei_fuer_share, Snackbar.LENGTH_LONG).show() }
                    return@launch
                }
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
                    clipData = ClipData.newRawUri("Fotos", uris.first()).apply {
                        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, ctx.getString(R.string.app_name)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                view?.let { Snackbar.make(it, "Fotos konnten nicht geteilt werden. Bitte Dateien und freien Speicher prüfen.", Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private fun confirmDeleteAtomar() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return

        val title = getString(R.string.suche_delete_atomic_title)
        val msg = getString(R.string.suche_delete_atomic_msg)

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteSelectedAtomar()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSelectedAtomar() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val (res, staged) = AtomicDeleteHelper.deleteAtomar(
                context = requireContext().applicationContext,
                fotoIds = ids.toList(),
                btbFiles = emptyList()
            )

            selectionMode = false
            adapter.setSelectionMode(false)
            viewModel.clearFotoSelection()
            updateSelectionUi()

            if (staged == null) {
                Snackbar.make(requireView(), R.string.suche_delete_failed, Snackbar.LENGTH_LONG).show()
                viewModel.setFilter { it }
                return@launch
            }

            val localStaged = staged
            val snack = Snackbar.make(
                requireView(),
                getString(R.string.suche_deleted_atomic, res.dbDeleted, res.missingFiles, res.skipped),
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.action_undo) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        AtomicDeleteHelper.undo(requireContext().applicationContext, localStaged)
                        // Re-emit: Filter no-op (führt combine neu aus)
                        viewModel.setFilter { it }
                    }
                }

            snack.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    // Wenn nicht per Action (Undo) geschlossen: endgültig löschen
                    if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            AtomicDeleteHelper.finalizeDelete(localStaged)
                        }
                    }
                }
            })
            snack.show()

            // Re-emit: Filter no-op (führt combine neu aus)
            viewModel.setFilter { it }
        }
    }
}

