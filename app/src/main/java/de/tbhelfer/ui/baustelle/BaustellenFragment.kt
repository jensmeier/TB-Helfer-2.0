package de.tbhelfer.ui.baustelle

import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbExportPaket
import de.tbhelfer.ui.bautagebuch.MergeSafeBaustellenExport
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.TeamDisplayCache
import de.tbhelfer.server.TeamManagementApi
import de.tbhelfer.server.WorksiteDeletionApi
import de.tbhelfer.server.WorksiteProvisioningApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Liste der Hauptbaustellen. */
class BaustellenFragment : Fragment(R.layout.fragment_baustellen) {

    private val repo = ServiceLocator.baustellenBaumRepository
    private val fotoRepo = ServiceLocator.fotoRepository
    private val speicher = ServiceLocator.aktuelleBaustelleSpeicher
    private lateinit var adapter: BaustellenKnotenAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerBaustellen)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        adapter = BaustellenKnotenAdapter(
            mode = BaustellenKnotenAdapterMode.HAUPTLISTE,
            onKlick = { haupt ->
                findNavController().navigate(R.id.baustellenKnotenFragment, Bundle().apply { putLong("knotenId", haupt.id) })
            },
            onLangKlick = { haupt ->
                val items = arrayOf(
                    getString(R.string.action_edit),
                    getString(R.string.action_set_as_current_haupt),
                    getString(R.string.action_btb_history),
                    getString(R.string.action_maengel_list),
                    "Abnahmeliste öffnen",
                    "Nutzer hinzufügen",
                    "ZIP von-bis Datum teilen",
                    "ZIP komplette Baustelle teilen",
                    getString(R.string.btn_delete)
                )
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(haupt.hauptbaustellenName)
                    .setItems(items) { _, which ->
                        when (which) {
                            0 -> findNavController().navigate(R.id.baustelleNeuFragment, Bundle().apply { putLong("editKnotenId", haupt.id) })
                            1 -> viewLifecycleOwner.lifecycleScope.launch {
                                speicher.setzenNurHaupt(hauptKnotenId = haupt.id, altBaustelleId = haupt.altBaustelleId)
                            }
                            2 -> findNavController().navigate(R.id.btbVerlaufFragment, Bundle().apply {
                                putLong("hauptKnotenId", haupt.id); putLong("altBaustelleId", haupt.altBaustelleId); putString("baustelleName", haupt.hauptbaustellenName)
                            })
                            3 -> findNavController().navigate(R.id.maengellisteFragment, Bundle().apply {
                                putLong("hauptKnotenId", haupt.id); putLong("altBaustelleId", haupt.altBaustelleId); putString("baustelleName", haupt.hauptbaustellenName)
                            })
                            4 -> findNavController().navigate(R.id.abnahmelisteFragment, Bundle().apply {
                                putLong("baustelleId", haupt.altBaustelleId); putString("baustelleName", haupt.hauptbaustellenName); putLong("hauptKnotenId", haupt.id); putString("filter", "ALLE")
                            })
                            5 -> BaustellenTeamDirekt.oeffneEinladen(this, haupt)
                            6 -> zeigeZipVonBisDialog(haupt)
                            7 -> exportiereBaustellenZip(haupt, null, null)
                            8 -> zeigeKomplettLoeschenDialog(haupt)
                        }
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            },
            onMaengelKlick = { haupt ->
                findNavController().navigate(R.id.maengellisteFragment, Bundle().apply {
                    putLong("hauptKnotenId", haupt.id); putLong("altBaustelleId", haupt.altBaustelleId); putString("baustelleName", haupt.hauptbaustellenName)
                })
            },
            onAbnahmenKlick = { haupt, filter ->
                findNavController().navigate(R.id.abnahmelisteFragment, Bundle().apply {
                    putLong("baustelleId", haupt.altBaustelleId); putString("baustelleName", haupt.hauptbaustellenName); putLong("hauptKnotenId", haupt.id); putString("filter", filter)
                })
            }
        )
        recycler.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                de.tbhelfer.server.SelectiveSyncStore.revisions.collect {
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
            }
        }

        view.findViewById<View>(R.id.btnNeueBaustelle).setOnClickListener { findNavController().navigate(R.id.baustelleNeuFragment) }
        view.findViewById<View>(R.id.btnAktuelleAnzeigen).setOnClickListener { findNavController().navigate(R.id.aktuelleBaustelleFragment) }
        view.findViewById<View>(R.id.btnBaustellenQrScan).setOnClickListener { BaustellenTeamDirekt.qrScannen(this) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repo.alleHauptbaustellen(), repo.alleKnotenFlow(), speicher.aktuelleHauptbaustelleKnotenId, fotoRepo.alleFotos()) {
                        hauptListe, alleKnoten, aktuelleHauptId, alleFotos ->
                    val countByRoot: Map<Long, Pair<Int, Int>> = hauptListe.associate { root ->
                        root.id to (alleKnoten.count { it.parentId == root.id } to (alleKnoten.count { it.rootId == root.id } - 1).coerceAtLeast(0))
                    }
                    val bilderByBaustelleId = alleFotos.groupingBy { it.baustelleId }.eachCount()
                    val appContext = requireContext().applicationContext
                    val btbByRoot = withContext(Dispatchers.IO) {
                        hauptListe.associate { root -> root.id to BautagebuchSpeicher.anzahlBtbsFuerBaustelle(appContext, root.altBaustelleId, root.hauptbaustellenName) }
                    }
                    val maengelByRoot = withContext(Dispatchers.IO) {
                        hauptListe.associate { root -> root.id to MangelFachlogik.zaehlerFuerBaustelle(appContext, root.altBaustelleId, root.hauptbaustellenName) }
                    }
                    val abnahmenByRoot = withContext(Dispatchers.IO) {
                        hauptListe.associate { root -> root.id to AbnahmeSpeicher.zaehler(appContext, root.altBaustelleId) }
                    }
                    hauptListe.map { knoten ->
                        val (unter, gesamt) = countByRoot[knoten.id] ?: (0 to 0)
                        val mz = maengelByRoot[knoten.id] ?: MangelFachlogik.Zaehler()
                        val az = abnahmenByRoot[knoten.id] ?: AbnahmeSpeicher.Zaehler()
                        BaustellenKnotenUiModell(
                            knoten = knoten,
                            istAktuell = (aktuelleHauptId == knoten.id),
                            ebene = 0,
                            unterCount = unter,
                            gesamtCount = gesamt,
                            bilderCount = bilderByBaustelleId[knoten.altBaustelleId] ?: 0,
                            btbCount = btbByRoot[knoten.id] ?: 0,
                            mangelOffenCount = mz.offen,
                            mangelInArbeitCount = mz.inArbeit,
                            mangelErledigtCount = mz.erledigt,
                            mangelAbgelehntCount = mz.abgelehnt,
                            abnahmeJaCount = az.ja,
                            abnahmeNeinCount = az.nein,
                            abnahmePruefungCount = az.inPruefung,
                            unterLabel = UnterLabel.UNTERBAUSTELLEN,
                            istAufklappbar = false,
                            istAufgeklappt = false
                        )
                    }
                }.collect { adapter.submitList(it) }
            }
        }
    }

    /**
     * Alter interner Einstieg bleibt vorerst als Rückfallpfad im Quellstand erhalten.
     * Der normale Nutzerablauf verwendet BaustellenTeamDirekt und öffnet keine zusätzliche
     * Baustellen-Synchronisationsseite mehr.
     */
    @Suppress("unused")
    private fun provisionWorksiteAndOpenUserManagement(haupt: BaustellenKnotenEntity) {
        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Gerät ist noch nicht für den Server aktiviert.", Toast.LENGTH_LONG).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "Nutzerverwaltung wird vorbereitet …", Toast.LENGTH_SHORT).show()
            val outcome = withContext(Dispatchers.IO) {
                when (val created = WorksiteProvisioningApi().create(token, haupt)) {
                    WorksiteProvisioningApi.Result.SessionInvalid -> ProvisionOutcome.SessionInvalid
                    WorksiteProvisioningApi.Result.Unsupported -> ProvisionOutcome.Error("Dieser Serverstand unterstützt die Selbstanlage noch nicht.")
                    is WorksiteProvisioningApi.Result.Error -> ProvisionOutcome.Error(created.message)
                    is WorksiteProvisioningApi.Result.Success -> {
                        when (val sync = ServiceLocator.collabSyncRepository.synchronizeAll(token)) {
                            CollabSyncRepository.FullSyncResult.SessionInvalid -> ProvisionOutcome.SessionInvalid
                            is CollabSyncRepository.FullSyncResult.Error -> ProvisionOutcome.Error(sync.message)
                            is CollabSyncRepository.FullSyncResult.Success -> {
                                runCatching { ServiceLocator.worksiteBindingRepository.bind(haupt.id, created.value.worksiteId) }
                                    .fold(
                                        onSuccess = {
                                            when (val team = TeamManagementApi().loadTeam(token, created.value.worksiteId)) {
                                                is TeamManagementApi.TeamResult.Success -> TeamDisplayCache.remember(requireContext().applicationContext, team.team, haupt.id)
                                                else -> Unit
                                            }
                                            ProvisionOutcome.Success(created.value.wasCreated)
                                        },
                                        onFailure = { ProvisionOutcome.Error(it.message ?: "Lokale Verknüpfung fehlgeschlagen.") }
                                    )
                            }
                        }
                    }
                }
            }
            if (!isAdded) return@launch
            when (outcome) {
                ProvisionOutcome.SessionInvalid -> {
                    SecureTokenStore(requireContext()).clear()
                    Toast.makeText(requireContext(), "Gerätefreigabe ist nicht mehr gültig.", Toast.LENGTH_LONG).show()
                }
                is ProvisionOutcome.Error -> Toast.makeText(requireContext(), "Nutzerverwaltung konnte nicht geöffnet werden: ${outcome.message}", Toast.LENGTH_LONG).show()
                is ProvisionOutcome.Success -> {
                    Toast.makeText(
                        requireContext(),
                        if (outcome.created) "Baustelle wurde automatisch mit dem Server verbunden." else "Baustelle ist mit dem Server verbunden.",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(R.id.baustellenSyncFragment)
                }
            }
        }
    }

    private sealed interface ProvisionOutcome {
        data class Success(val created: Boolean) : ProvisionOutcome
        data object SessionInvalid : ProvisionOutcome
        data class Error(val message: String) : ProvisionOutcome
    }

    private val exportDateFmtDe: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private fun parseExportDatum(raw: String): LocalDate? {
        val s = raw.trim()
        if (s.isBlank()) return null
        return runCatching { LocalDate.parse(s) }.getOrNull() ?: runCatching { LocalDate.parse(s, exportDateFmtDe) }.getOrNull()
    }

    private fun zeigeZipVonBisDialog(haupt: BaustellenKnotenEntity) {
        val heute = LocalDate.now()
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val von = EditText(requireContext()).apply { hint = "von Datum, z.B. ${heute.format(exportDateFmtDe)}"; setSingleLine(true) }
        val bis = EditText(requireContext()).apply { hint = "bis Datum, z.B. ${heute.format(exportDateFmtDe)}"; setSingleLine(true) }
        box.addView(TextView(requireContext()).apply { text = "Datum leer lassen = offen. Format: TT.MM.JJJJ oder JJJJ-MM-TT" })
        box.addView(von); box.addView(bis)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("ZIP von-bis Datum").setView(box)
            .setPositiveButton("ZIP teilen") { _, _ ->
                val v = parseExportDatum(von.text?.toString().orEmpty())
                val b = parseExportDatum(bis.text?.toString().orEmpty())
                if (von.text?.toString()?.trim().orEmpty().isNotBlank() && v == null) {
                    Toast.makeText(requireContext(), "Von-Datum ungültig", Toast.LENGTH_LONG).show(); return@setPositiveButton
                }
                if (bis.text?.toString()?.trim().orEmpty().isNotBlank() && b == null) {
                    Toast.makeText(requireContext(), "Bis-Datum ungültig", Toast.LENGTH_LONG).show(); return@setPositiveButton
                }
                exportiereBaustellenZip(haupt, v, b)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    private fun zeigeExportFortschritt(): Pair<androidx.appcompat.app.AlertDialog, TextView> {
        val tv = TextView(requireContext()).apply { text = "Rest: 100 %"; textSize = 18f; setPadding(dp(24), dp(12), dp(24), dp(12)) }
        val dlg = MaterialAlertDialogBuilder(requireContext()).setTitle("ZIP wird erstellt").setMessage("Export läuft ...").setView(tv).setCancelable(false).create()
        dlg.show(); return dlg to tv
    }

    private fun starteCountdown(tv: TextView): Job = lifecycleScope.launch {
        for (p in 100 downTo 0) { tv.text = "Rest: $p %"; delay(85) }
    }

    private fun exportiereBaustellenZip(haupt: BaustellenKnotenEntity, von: LocalDate?, bis: LocalDate?) {
        lifecycleScope.launch {
            val (dlg, tv) = zeigeExportFortschritt(); val countdown = starteCountdown(tv)
            try {
                val (result, archiveZip) = withContext(Dispatchers.IO) {
                    val raw = BtbExportPaket.erstellenBaustellenExport(
                        context = requireContext().applicationContext,
                        baustelleId = haupt.altBaustelleId,
                        baustelleName = haupt.hauptbaustellenName,
                        vonDatum = von,
                        bisDatum = bis
                    )
                    raw to MergeSafeBaustellenExport.normalisieren(
                        context = requireContext().applicationContext,
                        sourceZip = raw.zipFile,
                        localRootKnotenId = haupt.id,
                        baustelleName = haupt.hauptbaustellenName
                    )
                }
                countdown.cancel(); tv.text = "Rest: 0 %"; dlg.dismiss()
                Toast.makeText(requireContext(), "TB-Helfer-Archiv erstellt: ${result.fotosExportiert} Bilder", Toast.LENGTH_SHORT).show()
                BtbExportPaket.shareZip(requireContext(), archiveZip)
            } catch (t: Throwable) {
                countdown.cancel(); dlg.dismiss()
                Toast.makeText(requireContext(), "ZIP konnte nicht erstellt werden: ${t.message ?: "Fehler"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun zeigeKomplettLoeschenDialog(haupt: BaustellenKnotenEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_delete_project_full_title))
            .setMessage(getString(R.string.dialog_delete_project_full_message, haupt.hauptbaustellenName))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ -> zeigeLetzteLoeschWarnung(haupt) }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    private fun zeigeLetzteLoeschWarnung(haupt: BaustellenKnotenEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_delete_project_final_title))
            .setMessage(getString(R.string.dialog_delete_project_final_message, haupt.hauptbaustellenName))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val outcome = loescheBaustelleKomplett(haupt)) {
                        WorksiteDeleteOutcome.Success -> Toast.makeText(
                            requireContext(), getString(R.string.dialog_delete_project_done), Toast.LENGTH_SHORT
                        ).show()
                        WorksiteDeleteOutcome.SessionInvalid -> {
                            SecureTokenStore(requireContext()).clear()
                            Toast.makeText(requireContext(), "Gerätefreigabe ist nicht mehr gültig. Baustelle wurde nicht gelöscht.", Toast.LENGTH_LONG).show()
                        }
                        is WorksiteDeleteOutcome.Error -> Toast.makeText(
                            requireContext(), outcome.message, Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    private sealed interface WorksiteDeleteOutcome {
        data object Success : WorksiteDeleteOutcome
        data object SessionInvalid : WorksiteDeleteOutcome
        data class Error(val message: String) : WorksiteDeleteOutcome
    }

    private suspend fun loescheBaustelleKomplett(haupt: BaustellenKnotenEntity): WorksiteDeleteOutcome {
        val appContext = requireContext().applicationContext
        val binding = withContext(Dispatchers.IO) {
            ServiceLocator.worksiteBindingRepository.bindingForLocalRoot(haupt.id)
        }

        // Rein lokale, nie mit dem Server verbundene Baustellen duerfen weiterhin lokal geloescht werden.
        if (binding == null) {
            return runCatching {
                WorksiteLocalDeleteService.delete(appContext, haupt.id)
            }.fold(
                onSuccess = { WorksiteDeleteOutcome.Success },
                onFailure = { WorksiteDeleteOutcome.Error(it.message ?: "Baustelle konnte lokal nicht gelöscht werden.") }
            )
        }

        // Bei einer gebundenen Baustelle gewinnt immer zuerst der Server-Tombstone. So kann ein
        // Netz-/Serverfehler keine nur lokal geloeschte Baustelle erzeugen, die spaeter wiederkommt.
        val token = SecureTokenStore(appContext).load()
            ?: return WorksiteDeleteOutcome.Error("Gerät ist nicht für den Server aktiviert. Baustelle wurde nicht gelöscht.")

        return when (val result = withContext(Dispatchers.IO) {
            WorksiteDeletionApi().delete(token, binding.worksiteServerId)
        }) {
            WorksiteDeletionApi.DeleteResult.Success -> runCatching {
                WorksiteLocalDeleteService.delete(appContext, haupt.id)
            }.fold(
                onSuccess = { WorksiteDeleteOutcome.Success },
                onFailure = { WorksiteDeleteOutcome.Error(it.message ?: "Server hat gelöscht, lokale Bereinigung ist fehlgeschlagen.") }
            )
            WorksiteDeletionApi.DeleteResult.Unsupported -> WorksiteDeleteOutcome.Error(
                "Der Server unterstützt gemeinsames Baustellen-Löschen noch nicht. Lokal wurde nichts gelöscht."
            )
            WorksiteDeletionApi.DeleteResult.SessionInvalid -> WorksiteDeleteOutcome.SessionInvalid
            is WorksiteDeletionApi.DeleteResult.Error -> WorksiteDeleteOutcome.Error(
                "Baustelle wurde nicht gelöscht: ${result.message}"
            )
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

