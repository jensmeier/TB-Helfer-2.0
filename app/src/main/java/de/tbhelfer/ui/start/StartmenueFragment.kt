@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package de.tbhelfer.ui.start

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.istStandaloneStreckenpunkteBaustelle
import de.tbhelfer.einstellungen.AktiveBaustelleValidator
import de.tbhelfer.einstellungen.AnsichtEinstellungenSpeicher
import de.tbhelfer.server.SyncStatusSpeicher
import de.tbhelfer.ui.bautagebuch.BtbActiveContext
import de.tbhelfer.ui.bautagebuch.BtbPatch200034Installer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class StartmenueFragment : Fragment(R.layout.fragment_startmenue) {

    private var aktuelleAnzeige: String? = null
    private var refreshMenu: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.titleText)
        title.text = "TB-Helfer"

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerMenu)
        val btnSettings = view.findViewById<Button>(R.id.btnSettingsShortcut)

        fun openBtbWithAction(action: String) {
            findNavController().navigate(
                R.id.action_start_to_bautagebuch,
                Bundle().apply { putString(BtbPatch200034Installer.ARG_AUTO_ACTION, action) }
            )
        }

        fun openCameraFromOverview() {
            viewLifecycleOwner.lifecycleScope.launch {
                val active = BtbActiveContext.laden(requireContext().applicationContext)
                if (active != null) {
                    findNavController().navigate(R.id.action_start_to_kamera)
                    return@launch
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Kein BTB aktiv")
                    .setMessage("Wie soll das Foto gespeichert werden?")
                    .setNegativeButton("Abbrechen", null)
                    .setNeutralButton("Nur Baustellenfoto") { _, _ ->
                        findNavController().navigate(R.id.action_start_to_kamera)
                    }
                    .setPositiveButton("BTB starten/öffnen") { _, _ ->
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("BTB auswählen")
                            .setItems(arrayOf("Neuen BTB starten", "Vorhandenen BTB öffnen")) { _, which ->
                                when (which) {
                                    0 -> openBtbWithAction(BtbPatch200034Installer.AUTO_NEW)
                                    else -> openBtbWithAction(BtbPatch200034Installer.AUTO_OPEN)
                                }
                            }
                            .setNegativeButton("Abbrechen", null)
                            .show()
                    }
                    .show()
            }
        }

        val adapter = StartmenueAdapter { ziel ->
            when (ziel) {
                StartmenueZiel.KAMERA_AKTUELLE_BAUSTELLE -> openCameraFromOverview()

                StartmenueZiel.BAUSTELLEN ->
                    findNavController().navigate(R.id.action_start_to_baustellen)

                StartmenueZiel.STRECKENPUNKTE ->
                    findNavController().navigate(R.id.action_start_to_streckenpunkte)

                StartmenueZiel.BAUTAGEBUCH ->
                    findNavController().navigate(R.id.action_start_to_bautagebuch)

                StartmenueZiel.SUCHE ->
                    findNavController().navigate(R.id.action_start_to_suche)

                StartmenueZiel.MEIN_ORDNER ->
                    findNavController().navigate(R.id.action_start_to_meinOrdner)

                StartmenueZiel.IMPORT_EXPORT ->
                    findNavController().navigate(R.id.action_start_to_importExport)

                StartmenueZiel.SERVER_SYNC ->
                    findNavController().navigate(R.id.action_start_to_serverSync)

                StartmenueZiel.EINSTELLUNGEN ->
                    findNavController().navigate(R.id.action_start_to_einstellungen)
            }
        }
        recycler.setHasFixedSize(true)
        recycler.setItemViewCacheSize(8)
        recycler.adapter = adapter

        fun applyMenuItems() {
            val zeile = if (aktuelleAnzeige.isNullOrBlank()) {
                "Aktuelle Baustelle"
            } else {
                "Aktuelle Baustelle\n(${aktuelleAnzeige})"
            }
            val syncStatus = SyncStatusSpeicher.anzeige(requireContext())

            adapter.setzeItems(
                listOf(
                    StartmenueItem(zeile, StartmenueZiel.KAMERA_AKTUELLE_BAUSTELLE),
                    StartmenueItem("Bautagebuch", StartmenueZiel.BAUTAGEBUCH),
                    StartmenueItem("Baustellen", StartmenueZiel.BAUSTELLEN),
                    StartmenueItem("Suche Bilder/BTB", StartmenueZiel.SUCHE),
                    StartmenueItem("Mein Ordner", StartmenueZiel.MEIN_ORDNER),
                    StartmenueItem("Streckendaten", StartmenueZiel.IMPORT_EXPORT),
                    StartmenueItem("Mit NAS synchronisieren\n($syncStatus)", StartmenueZiel.SERVER_SYNC)
                )
            )
        }

        refreshMenu = ::applyMenuItems
        applyMenuItems()

        btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_start_to_einstellungen)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.ansichtEinstellungenSpeicher.einstellungen.collectLatest { cfg ->
                adapter.setzeStyle(
                    buttonColor = cfg.menuButtonColor,
                    textColor = android.graphics.Color.WHITE,
                    textSizeSp = cfg.menuTextSizeSp
                )

                btnSettings.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(cfg.menuButtonColor)
                btnSettings.setTextColor(android.graphics.Color.WHITE)
                btnSettings.setTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    cfg.menuTextSizeSp.toFloat()
                )

                when (cfg.menuLayout) {
                    AnsichtEinstellungenSpeicher.MenuLayout.LISTE -> {
                        recycler.layoutManager = LinearLayoutManager(requireContext())
                    }

                    AnsichtEinstellungenSpeicher.MenuLayout.KACHEL -> {
                        val minTileWidthDp = 170f
                        val density = resources.displayMetrics.density
                        val minTileWidthPx = (minTileWidthDp * density)

                        recycler.post {
                            val widthPx = recycler.width.toFloat().coerceAtLeast(minTileWidthPx)
                            val span = (widthPx / minTileWidthPx).toInt().coerceIn(2, 3)

                            val existing = recycler.layoutManager as? GridLayoutManager
                            if (existing == null || existing.spanCount != span) {
                                recycler.layoutManager = GridLayoutManager(requireContext(), span)
                            }
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            AktiveBaustelleValidator.bereinigeWennUngueltig(
                speicher = ServiceLocator.aktuelleBaustelleSpeicher,
                baumRepo = ServiceLocator.baustellenBaumRepository,
                baustellenRepo = ServiceLocator.baustellenRepository
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val speicher = ServiceLocator.aktuelleBaustelleSpeicher
            val baumRepo = ServiceLocator.baustellenBaumRepository
            val bRepo = ServiceLocator.baustellenRepository

            launch {
                speicher.aktuellerKnotenId
                    .flatMapLatest { id ->
                        if (id == null) flowOf(null) else baumRepo.knotenMitIdFlow(id)
                    }
                    .collectLatest { knoten ->
                        if (knoten == null) {
                            aktuelleAnzeige = null
                            applyMenuItems()
                            return@collectLatest
                        }

                        val extra = knoten.knotenName?.takeIf { it.isNotBlank() }
                        aktuelleAnzeige =
                            if (extra != null) "${knoten.hauptbaustellenName} / $extra"
                            else knoten.hauptbaustellenName
                        applyMenuItems()
                    }
            }

            launch {
                speicher.aktuelleBaustelleId
                    .flatMapLatest { id ->
                        if (id == null) flowOf(null) else bRepo.baustelleMitId(id)
                    }
                    .collectLatest { b ->
                        if (b != null && aktuelleAnzeige.isNullOrBlank()) {
                            aktuelleAnzeige = b.baustellenname.takeUnless { istStandaloneStreckenpunkteBaustelle(it) }
                            applyMenuItems()
                        }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMenu?.invoke()
    }

    override fun onDestroyView() {
        refreshMenu = null
        super.onDestroyView()
    }
}
