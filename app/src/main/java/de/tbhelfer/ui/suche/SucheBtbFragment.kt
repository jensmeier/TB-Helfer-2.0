package de.tbhelfer.ui.suche

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import de.tbhelfer.util.requireViewByIdCompat
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.util.AtomicDeleteHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SucheBtbFragment : Fragment() {

    private val viewModel: SucheViewModel by activityViewModels {
        SucheViewModel.Fabrik(
            requireContext().applicationContext,
            ServiceLocator.baustellenRepository,
            ServiceLocator.fotoRepository,
            ServiceLocator.streckenpunkteRepository
        )
    }

    private lateinit var recycler: RecyclerView
    private lateinit var textLeer: TextView
    private lateinit var tvCount: TextView
    private lateinit var btnSort: MaterialButton
    private lateinit var btnToCart: MaterialButton

    private lateinit var selectionBar: LinearLayout
    private lateinit var tvSelection: TextView
    private lateinit var btnCancelSel: MaterialButton
    private lateinit var btnShareSel: MaterialButton
    private lateinit var btnDeleteFileSel: MaterialButton
    private lateinit var btnDeleteDbSel: MaterialButton
    private lateinit var btnSelectAllSel: MaterialButton
    private lateinit var btnInvertSel: MaterialButton
    private lateinit var btnZipSel: MaterialButton

    private lateinit var adapter: BtbAdapter

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
        btnToCart = view.requireViewByIdCompat(R.id.btnToCart)

        selectionBar = view.requireViewByIdCompat(R.id.selectionBar)
        tvSelection = view.requireViewByIdCompat(R.id.tvSelection)
        btnCancelSel = view.requireViewByIdCompat(R.id.btnCancelSel)
        btnShareSel = view.requireViewByIdCompat(R.id.btnShareSel)
        btnDeleteFileSel = view.requireViewByIdCompat(R.id.btnDeleteFileSel)
        btnDeleteDbSel = view.requireViewByIdCompat(R.id.btnDeleteDbSel)
        btnSelectAllSel = view.requireViewByIdCompat(R.id.btnSelectAllSel)
        btnInvertSel = view.requireViewByIdCompat(R.id.btnInvertSel)
        btnZipSel = view.requireViewByIdCompat(R.id.btnZipSel)

        // BTB hat keine DB-Löschung -> Button ausblenden
        btnDeleteDbSel.isGone = true

        adapter = BtbAdapter(
            onClick = { item ->
                // normal click only when not in selection mode
                oeffnenPdf(item)
            },
            onLongClick = {
                selectionMode = true
                updateSelectionUi()
            },
            onSelectionChanged = {
                updateSelectionUi()
            }
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        // Auswahl aus dem Sammelkorb übernehmen (z.B. wenn im anderen Tab ausgewählt / geleert wurde)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedBtbPaths.collectLatest { paths ->
                adapter.setSelectedPathsFromViewModel(paths)
                selectionMode = paths.isNotEmpty()
                updateSelectionUi()
            }
        }

        btnToCart.setOnClickListener {
            val paths = adapter.getAllPaths().toSet()
            viewModel.addBtbsToKorb(paths)
            Snackbar.make(view, "In Korb: ${paths.size} BTB", Snackbar.LENGTH_SHORT).show()
        }

        btnSort.setOnClickListener {
            viewModel.setFilter { it.copy(sortDesc = !it.sortDesc) }
            updateSortButton()
        }
        updateSortButton()

        btnCancelSel.setOnClickListener {
            selectionMode = false
            adapter.setSelectionMode(false)
            viewModel.clearBtbSelection()
            updateSelectionUi()
        }

        btnShareSel.setOnClickListener { shareSelectedBtbs() }
        btnDeleteFileSel.setOnClickListener { confirmDeleteFiles() }

        btnSelectAllSel.setOnClickListener {
            selectionMode = true
            adapter.selectAll()
            viewModel.setSelectedBtbs(adapter.getSelectedFiles().map { it.absolutePath }.toSet())
            updateSelectionUi()
        }

        btnInvertSel.setOnClickListener {
            selectionMode = true
            adapter.invertSelection()
            viewModel.setSelectedBtbs(adapter.getSelectedFiles().map { it.absolutePath }.toSet())
            updateSelectionUi()
        }

        btnZipSel.setOnClickListener { showZipDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.btbsGefiltert.collectLatest { liste ->
                adapter.submitList(liste)
                textLeer.isVisible = liste.isEmpty()
                recycler.isGone = liste.isEmpty()
                textLeer.text = "Keine BTB-PDFs gefunden."
                tvCount.text = liste.size.toString()
                updateSelectionUi()
            }
        }
    }

    private fun updateSortButton() {
        val isDesc = viewModel.currentFilter().sortDesc
        btnSort.text = if (isDesc) "Neueste" else "Älteste"
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

        val hasSel = count > 0
        btnShareSel.isEnabled = hasSel
        btnDeleteFileSel.isEnabled = hasSel
        btnZipSel.isEnabled = hasSel

        val hasItems = total > 0
        btnSelectAllSel.isEnabled = hasItems
        btnInvertSel.isEnabled = hasItems
    }

    private fun oeffnenPdf(item: BtbDatei) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.dateianbieter",
            item.file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "BTB öffnen"))
    }

    private fun shareSelectedBtbs() {
        val files = adapter.getSelectedFiles()
        if (files.isEmpty()) return

        val uris = files.mapNotNull { f ->
            if (!f.exists()) null else FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.dateianbieter",
                f
            )
        }

        if (uris.isEmpty()) {
            Snackbar.make(requireView(), R.string.suche_keine_datei_fuer_share, Snackbar.LENGTH_LONG).show()
            return
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.app_name)))
    }

    private fun confirmDeleteFiles() {
        val files = adapter.getSelectedFiles()
        if (files.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.suche_delete_file_title))
            .setMessage(getString(R.string.suche_delete_file_msg))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteSelectedFiles(files)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSelectedFiles(files: List<File>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (res, staged) = AtomicDeleteHelper.deleteAtomar(
                context = requireContext().applicationContext,
                fotoIds = emptyList(),
                btbFiles = files
            )

            selectionMode = false
            adapter.setSelectionMode(false)
            updateSelectionUi()

            if (staged == null) {
                Snackbar.make(requireView(), R.string.suche_delete_failed, Snackbar.LENGTH_LONG).show()
                viewModel.refreshBtbs()
                return@launch
            }

            val localStaged = staged

            val deletedCount = (res.requested - res.skipped).coerceAtLeast(0)
            val snack = Snackbar.make(
                requireView(),
                getString(R.string.suche_deleted_files, deletedCount),
                Snackbar.LENGTH_LONG
            ).setAction(R.string.action_undo) {
                viewLifecycleOwner.lifecycleScope.launch {
                    AtomicDeleteHelper.undo(requireContext().applicationContext, localStaged)
                    viewModel.refreshBtbs()
                }
            }

            snack.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            AtomicDeleteHelper.finalizeDelete(localStaged)
                            viewModel.refreshBtbs()
                        }
                    }
                }
            })

            snack.show()
            viewModel.refreshBtbs()
        }
    }

    private fun showZipDialog() {
        if (adapter.selectedCount <= 0) return

        val options = arrayOf(
            "Nur BTB (Auswahl)",
            "BTB (Auswahl) + Fotos (gefiltert)"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("ZIP-Export")
            .setItems(options) { _, which ->
                exportSelectedAsZip(includeFotos = (which == 1))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportSelectedAsZip(includeFotos: Boolean) {
        val btbs = adapter.getSelectedFiles()
        if (btbs.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val inputs = mutableListOf<ZipExporter.ZipInput>()

                btbs.forEach { f ->
                    inputs.add(ZipExporter.ZipInput(f, "BTB/${f.name}"))
                }

                if (includeFotos) {
                    viewModel.fotosGefiltert.value.forEach { foto ->
                        val f = File(foto.dateipfad)
                        inputs.add(ZipExporter.ZipInput(f, "Fotos/${f.name}"))
                    }
                }

                ZipExporter.createZip(
                    requireContext(),
                    inputs,
                    namePrefix = if (includeFotos) "tbhelfer_export_btb_fotos" else "tbhelfer_export_btb"
                )
            }

            if (result.zip == null) {
                Snackbar.make(requireView(), R.string.suche_zip_nix, Snackbar.LENGTH_LONG).show()
                return@launch
            }

            Snackbar.make(
                requireView(),
                getString(R.string.suche_zip_done, result.exported, result.missing),
                Snackbar.LENGTH_LONG
            ).show()

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.dateianbieter",
                result.zip
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.app_name)))
        }
    }
}
