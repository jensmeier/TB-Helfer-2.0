package de.tbhelfer.ui.suche

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustelleEntity
import de.tbhelfer.util.AtomicDeleteHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SucheFragment : Fragment(R.layout.fragment_suche) {

    private val viewModel: SucheViewModel by activityViewModels {
        SucheViewModel.Fabrik(
            requireContext().applicationContext,
            ServiceLocator.baustellenRepository,
            ServiceLocator.fotoRepository,
            ServiceLocator.streckenpunkteRepository
        )
    }

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Defensive: avoid hard crash if layout out-of-sync
        val btnOpenFilter = view.findViewById<Button>(R.id.btnOpenFilter)
        val btnSort = view.findViewById<Button>(R.id.btnSort)
        val tvSummary = view.findViewById<TextView>(R.id.tvFilterSummary)
        val tabs = view.findViewById<TabLayout>(R.id.tabLayout)
        val pager = view.findViewById<ViewPager2>(R.id.viewPager)

        if (btnOpenFilter == null || btnSort == null || tvSummary == null || tabs == null || pager == null) {
            Snackbar.make(view, "fragment_suche.xml: IDs fehlen (btnOpenFilter/btnSort/...). Patch einspielen.", Snackbar.LENGTH_LONG).show()
            return
        }

        btnOpenFilter.setOnClickListener {
            SucheFilterBottomSheet.newInstance().show(childFragmentManager, "suche_filter")
        }

        // Sortieren + Typ als BottomSheet
        btnSort.setOnClickListener {
            SucheSortBottomSheet.newInstance().show(childFragmentManager, "suche_sort")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filter.collectLatest { f ->
                tvSummary.text = buildSummary(f, viewModel.baustellen.value)
            }
        }

        pager.adapter = SuchePagerAdapter(this)
        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = if (pos == 0) "Fotos" else "BTB"
        }.attach()

        // Korbbar
        val cartBar = view.findViewById<LinearLayout>(R.id.cartBar)
        val tvCart = view.findViewById<TextView>(R.id.tvCart)
        val btnCartShare = view.findViewById<Button>(R.id.btnCartShare)
        val btnCartZip = view.findViewById<Button>(R.id.btnCartZip)
        val btnCartDelete = view.findViewById<Button>(R.id.btnCartDelete)
        val btnCartClear = view.findViewById<Button>(R.id.btnCartClear)

        if (cartBar != null && tvCart != null && btnCartShare != null && btnCartZip != null && btnCartDelete != null && btnCartClear != null) {
            btnCartClear.setOnClickListener { viewModel.clearKorb() }
            btnCartShare.setOnClickListener { shareKorb() }
            btnCartZip.setOnClickListener { exportKorbZip() }
            btnCartDelete.setOnClickListener { confirmDeleteKorb() }

            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.korbCounts.collectLatest { (fotoCount, btbCount) ->
                    val visible = (fotoCount + btbCount) > 0
                    cartBar.isVisible = visible
                    if (visible) tvCart.text = "Korb: $fotoCount Fotos, $btbCount BTB"
                    btnCartShare.isEnabled = visible
                    btnCartZip.isEnabled = visible
                    btnCartDelete.isEnabled = visible
                    btnCartClear.isEnabled = visible
                }
            }
        }
    }

    private fun buildSummary(f: SucheFilter, baustellen: List<BaustelleEntity>): String {
        val parts = mutableListOf<String>()

        val bName = f.baustelleId?.let { id -> baustellen.firstOrNull { it.id == id }?.baustellenname }
        parts += if (bName != null) "Baustelle: $bName" else "Alle Baustellen"

        fun fmtEpoch(ms: Long): String =
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)

        val von = f.datumVonEpochMs
        val bis = f.datumBisEpochMs
        if (von != null || bis != null) {
            parts += when {
                von != null && bis != null -> "Datum: ${fmtEpoch(von)}–${fmtEpoch(bis)}"
                von != null -> "Datum ab: ${fmtEpoch(von)}"
                else -> "Datum bis: ${fmtEpoch(bis!!)}"
            }
        }

        if (f.kmVon != null || f.kmBis != null) {
            val kv = f.kmVon?.toString() ?: "…"
            val kb = f.kmBis?.toString() ?: "…"
            parts += "KM: $kv–$kb"
        }

        f.streckenNrQuery?.takeIf { it.isNotBlank() }?.let { parts += "Strecke: $it" }
        f.volltext?.takeIf { it.isNotBlank() }?.let { parts += "Text: $it" }

        if (f.nurBeiStreckenpunkten) parts += "nur Streckenpunkte"
        f.streckenpunktText?.takeIf { it.isNotBlank() }?.let { parts += "SP: $it" }

        return parts.joinToString(" · ")
    }

    private fun shareKorb() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (uris, missing) = withContext(Dispatchers.IO) {
                val out = mutableListOf<Uri>()
                var miss = 0

                val fotoIds = viewModel.selectedFotoIds.value.toList()
                if (fotoIds.isNotEmpty()) {
                    val fotos = ServiceLocator.fotoRepository.fotosMitIds(fotoIds)
                    fotos.forEach { f ->
                        val file = File(f.dateipfad)
                        if (file.exists()) out.add(ExportUtils.fileUri(requireContext(), file)) else miss++
                    }
                }

                viewModel.selectedBtbPaths.value.forEach { p ->
                    val file = File(p)
                    if (file.exists()) out.add(ExportUtils.fileUri(requireContext(), file)) else miss++
                }

                out to miss
            }

            if (uris.isEmpty()) {
                Snackbar.make(requireView(), "Keine Dateien im Korb gefunden.", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            if (missing > 0) {
                Snackbar.make(requireView(), "Hinweis: $missing Dateien fehlen.", Snackbar.LENGTH_LONG).show()
            }

            ExportUtils.shareMultiple(requireContext(), uris, "Korb teilen")
        }
    }

    private fun exportKorbZip() {
        val fotoIds = viewModel.selectedFotoIds.value
        val btbPaths = viewModel.selectedBtbPaths.value
        if (fotoIds.isEmpty() && btbPaths.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val (zip, stats) = withContext(Dispatchers.IO) {
                val fotos = if (fotoIds.isNotEmpty()) {
                    ServiceLocator.fotoRepository.fotosMitIds(fotoIds.toList())
                } else emptyList()

                val btbs = selectedBtbsFromPaths(btbPaths)

                ExportUtils.createZip(
                    context = requireContext(),
                    fotos = fotos,
                    btbs = btbs,
                    baustellen = viewModel.baustellen.value,
                    zipName = "tbhelfer_korb_${System.currentTimeMillis()}.zip"
                )
            }

            Snackbar.make(
                requireView(),
                "ZIP: ${stats.fotosExportiert} Fotos, ${stats.btbsExportiert} BTB, fehlend: ${stats.fehlend}",
                Snackbar.LENGTH_LONG
            ).show()

            ExportUtils.shareSingleFile(requireContext(), zip, "application/zip", getString(R.string.app_name))
        }
    }

    private fun selectedBtbsFromPaths(paths: Set<String>): List<BtbDatei> {
        if (paths.isEmpty()) return emptyList()
        val knownByPath = viewModel.btbsGefiltert.value.associateBy { it.file.absolutePath }
        return paths.mapNotNull { p ->
            knownByPath[p] ?: run {
                val file = File(p)
                if (!file.exists()) return@run null
                BtbDatei(
                    file = file,
                    baustellenName = "Unbekannt",
                    streckennummer = null,
                    datum = BtbSpeicher.dateFromLastModified(file)
                )
            }
        }
    }

    private fun confirmDeleteKorb() {
        val fotoCount = viewModel.selectedFotoIds.value.size
        val btbCount = viewModel.selectedBtbPaths.value.size
        if (fotoCount + btbCount <= 0) return

        AlertDialog.Builder(requireContext())
            .setTitle("Korb löschen?")
            .setMessage(
                "Auswahl: $fotoCount Fotos, $btbCount BTB\n\n" +
                    "Atomar: Datei + Thumbnail + DB in einem Schritt (mit Rückgängig)."
            )
            .setPositiveButton("Löschen") { _, _ ->
                deleteKorbAtomar()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteKorbAtomar() {
        val fotoIds = viewModel.selectedFotoIds.value.toList()
        val btbFiles = viewModel.selectedBtbPaths.value.map { File(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            val (res, staged) = AtomicDeleteHelper.deleteAtomar(
                context = requireContext().applicationContext,
                fotoIds = fotoIds,
                btbFiles = btbFiles
            )

            // UI: Korb sofort leeren (damit nichts doppelt angezeigt wird)
            viewModel.clearKorb()
            viewModel.refreshBtbs()

            if (staged == null) {
                Snackbar.make(requireView(), "Löschen fehlgeschlagen.", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val localStaged = staged
            val snack = Snackbar.make(
                requireView(),
                "Korb gelöscht: DB=${res.dbDeleted}, fehlend=${res.missingFiles}, übersprungen=${res.skipped}",
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
        }
    }
}
