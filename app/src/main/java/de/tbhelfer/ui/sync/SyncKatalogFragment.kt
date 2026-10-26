package de.tbhelfer.ui.sync

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.text.InputFilter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.OfflineSyncRepository
import de.tbhelfer.datenbank.SyncObjectEntity
import de.tbhelfer.datenbank.SyncSectionEntity
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.SyncCatalogApi
import de.tbhelfer.server.SyncDetailApi
import de.tbhelfer.server.SyncStatusSpeicher
import de.tbhelfer.server.SyncWorker
import de.tbhelfer.ui.activation.AktivierungActivity
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class SyncKatalogFragment : Fragment(R.layout.fragment_sync_katalog) {

    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var routesContainer: LinearLayout
    private lateinit var stationsContainer: LinearLayout
    private lateinit var routesTitle: TextView
    private lateinit var stationsTitle: TextView
    private lateinit var refreshButton: Button
    private lateinit var conflictButton: Button
    private lateinit var searchButton: Button
    private var fullManualSyncRunning: Boolean = false
    private var lastCatalogAttempt: String = ""
    private var lastErrorLogId: String? = null

    private val offlineRepository: OfflineSyncRepository
        get() = ServiceLocator.offlineSyncRepository

    private val offlineDialogs: SyncOfflineDialogs by lazy(LazyThreadSafetyMode.NONE) {
        SyncOfflineDialogs(this, ServiceLocator.syncOfflineEditor)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.txtSyncStatus)
        progress = view.findViewById(R.id.progressSync)
        routesContainer = view.findViewById(R.id.routesContainer)
        stationsContainer = view.findViewById(R.id.stationsContainer)
        routesTitle = view.findViewById(R.id.txtRoutesTitle)
        stationsTitle = view.findViewById(R.id.txtStationsTitle)
        refreshButton = view.findViewById(R.id.btnSyncRefresh)
        conflictButton = view.findViewById(R.id.btnSyncConflicts)
        searchButton = view.findViewById(R.id.btnSyncInventorySearch)
        searchButton.setOnClickListener { showInventorySearch() }

        refreshButton.setOnClickListener { loadCatalog() }
        conflictButton.setOnClickListener {
            offlineDialogs.showConflicts { refreshConflictButton() }
        }
        view.findViewById<View>(R.id.btnSyncErrorLog).setOnClickListener {
            de.tbhelfer.ui.einstellungen.diagnose.ErrorLogDialog.show(this, lastErrorLogId)
        }
        startLiveDiagnostics(view.findViewById(R.id.txtSyncLiveDiagnostics))
        loadCatalog()
    }

    private fun startLiveDiagnostics(panel: TextView) {
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var server = "Server wird geprüft …"
                var checkedAt: Long? = null
                launch {
                    while (isActive) {
                        server = withContext(Dispatchers.IO) {
                            val result = de.tbhelfer.server.SyncServerProbe.check(app)
                            if (!result.startsWith("TB-Server erreichbar") && !result.startsWith("Serverprüfung pausiert"))
                                de.tbhelfer.server.ErrorLogStore.record(app, de.tbhelfer.server.ErrorHistory.Operation.CONNECTION, result)
                            result
                        }
                        checkedAt = de.tbhelfer.server.SyncActivityMonitor.nowMs()
                        delay(30_000L)
                    }
                }
                launch {
                    while (isActive) {
                        val now = de.tbhelfer.server.SyncActivityMonitor.nowMs()
                        val age = checkedAt?.let { " · geprüft vor " + de.tbhelfer.server.SyncActivityMonitor.duration(now - it) }.orEmpty()
                        val pendingBtb = de.tbhelfer.server.SelectiveSyncStore.btbScopes(app).size
                        val pendingRoots = (de.tbhelfer.server.StructureSyncStore.roots(app) +
                            de.tbhelfer.server.WorksiteLocalSyncStore.roots(app)).size
                        panel.text = server + age + "\n\n" + de.tbhelfer.server.SyncActivityMonitor.describe(now) +
                            "\n\nVorgemerkt (BTB/Struktur): $pendingBtb BTB · $pendingRoots Baustellen/Strukturen" +
                            "\nLetzter erfolgreicher Lauf: " + SyncStatusSpeicher.anzeige(app)
                        delay(1_000L)
                    }
                }
            }
        }
    }

    private fun loadCatalog() {
        if (fullManualSyncRunning) return
        val tokenStore = SecureTokenStore(requireContext())
        val token = tokenStore.load()
        if (token.isNullOrBlank()) {
            openActivation()
            return
        }

        lastCatalogAttempt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date())
        setLoading(true)
        statusText.text = getString(R.string.sync_loading)

        viewLifecycleOwner.lifecycleScope.launch {
            val run = de.tbhelfer.server.SyncActivityMonitor.begin("Vorbereitung")
            try {
                de.tbhelfer.server.SyncActivityMonitor.phase(run, "Katalog abrufen")
                val result = withContext(Dispatchers.IO) { SyncCatalogApi().loadCatalog(token) }
                if (!isAdded) return@launch
                when (result) {
                    is SyncCatalogApi.Result.Success -> handleOnlineCatalog(result.catalog, token, tokenStore, run)
                    SyncCatalogApi.Result.SessionInvalid -> handleInvalidSession(tokenStore)
                    is SyncCatalogApi.Result.Error -> handleCatalogError(result.message)
                }
            } finally { de.tbhelfer.server.SyncActivityMonitor.finish(run) }
        }
    }

    private suspend fun handleOnlineCatalog(
        catalog: SyncCatalogApi.Catalog,
        token: String,
        tokenStore: SecureTokenStore,
        run: Long
    ) {
        de.tbhelfer.server.SyncActivityMonitor.phase(run, "Katalog lokal speichern")
        val cacheSaved = withContext(Dispatchers.IO) {
            runCatching { offlineRepository.saveCatalog(catalog) }.isSuccess
        }
        if (!isAdded) return

        setLoading(false)
        renderCatalog(catalog)

        if (!cacheSaved) {
            showErrorLink(de.tbhelfer.server.ErrorLogStore.record(requireContext(), de.tbhelfer.server.ErrorHistory.Operation.CATALOG, "Katalog konnte lokal nicht gespeichert werden"))
            statusText.text = getString(R.string.sync_cache_warning, statusText.text.toString())
            refreshConflictButton()
            return
        }

        // 0.6.0 kennt die neuen Change-/Mutation-Endpunkte noch nicht. Der bereits getestete
        // Katalog-/Offline-Download bleibt dort bewusst aktiv, ohne unnötige 404-Aufrufe.
        if (!supportsIncrementalSync(catalog.serverVersion)) {
            refreshConflictButton()
            return
        }

        setLoading(true)
        de.tbhelfer.server.SyncActivityMonitor.phase(run, "Katalogänderungen senden und empfangen")
        val syncResult = withContext(Dispatchers.IO) { offlineRepository.synchronize(token) }
        if (!isAdded) return
        setLoading(false)

        when (syncResult) {
            OfflineSyncRepository.FullSyncResult.SessionInvalid -> handleInvalidSession(tokenStore)
            is OfflineSyncRepository.FullSyncResult.Error -> {
                showErrorLink(de.tbhelfer.server.ErrorLogStore.record(requireContext(), de.tbhelfer.server.ErrorHistory.Operation.CATALOG, syncResult.message))
                statusText.text = getString(
                    R.string.sync_incremental_warning,
                    statusText.text.toString(),
                    syncResult.message
                )
                refreshConflictButton()
            }
            is OfflineSyncRepository.FullSyncResult.Success -> {
                val summary = syncResult.summary
                statusText.text = getString(
                    R.string.sync_incremental_ok,
                    statusText.text.toString(),
                    summary.pulledChanges,
                    summary.uploadedChanges,
                    summary.pendingChanges,
                    summary.conflicts
                )
                setConflictCount(summary.conflicts)
                startFullManualSync()
            }
        }
    }

    /**
     * Bewusster NAS-Sync: Nach dem sichtbaren Katalog-/Konfliktlauf wird derselbe vollständige
     * Worker wie im Hintergrund gestartet. Im manuellen Modus ignoriert er den automatischen
     * Zeitplan und die WLAN/Mobilfunk-Präferenz; eine echte Internetverbindung bleibt erforderlich.
     */
    private fun startFullManualSync() {
        if (!isAdded || fullManualSyncRunning) return
        fullManualSyncRunning = true
        progress.visibility = View.VISIBLE
        refreshButton.isEnabled = false
        searchButton.isEnabled = false
        statusText.text = buildString {
            append(statusText.text.toString())
            append("\nBaustelle, BTB, Mängel, Abnahmen und Bilder werden synchronisiert …")
        }

        val workId = SyncWorker.enqueueManual(requireContext())
        val workInfo = WorkManager.getInstance(requireContext().applicationContext)
            .getWorkInfoByIdLiveData(workId)

        workInfo.observe(viewLifecycleOwner) { info ->
            if (info == null || !isAdded) return@observe
            when (info.state) {
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED -> {
                    progress.visibility = View.VISIBLE
                    refreshButton.isEnabled = false
                    statusText.text = "Synchronisation wartet auf Netzwerk oder einen vorherigen Auftrag …"
                }
                WorkInfo.State.RUNNING -> {
                    progress.visibility = View.VISIBLE
                    refreshButton.isEnabled = false
                    statusText.text = "Synchronisation läuft: Baustelle, BTB, Mängel, Abnahmen und Bilder …"
                }
                WorkInfo.State.SUCCEEDED -> {
                    showErrorLink(null)
                    fullManualSyncRunning = false
                    setLoading(false)
                    val pending = de.tbhelfer.server.WorksiteLocalSyncStore.pending(requireContext()) ||
                        de.tbhelfer.server.StructureSyncStore.roots(requireContext()).isNotEmpty() ||
                        de.tbhelfer.server.SelectiveSyncStore.hasPending(requireContext())
                    statusText.text = if (pending) "Lokal gespeichert · Übertragungen noch ausstehend"
                        else "Synchronisationslauf beendet · ${SyncStatusSpeicher.anzeige(requireContext())}"
                    refreshConflictButton()
                }
                WorkInfo.State.FAILED -> {
                    fullManualSyncRunning = false
                    setLoading(false)
                    val message = info.outputData.getString(SyncWorker.OUTPUT_MESSAGE)
                        ?.takeIf { it.isNotBlank() }
                        ?: "Synchronisation fehlgeschlagen."
                    val id = info.outputData.getString(SyncWorker.OUTPUT_ERROR_LOG_ID)?.takeIf { it.isNotBlank() }
                        ?: de.tbhelfer.server.ErrorLogStore.record(requireContext(), de.tbhelfer.server.ErrorHistory.Operation.FULL_SYNC, message)
                    showErrorLink(id)
                    statusText.text = if (id != null) "Synchronisation unvollständig – Fehler ansehen" else "Synchronisation unvollständig: $message"
                    refreshConflictButton()
                }
                WorkInfo.State.CANCELLED -> {
                    fullManualSyncRunning = false
                    setLoading(false)
                    statusText.text = "Synchronisation wurde beendet."
                }
            }
        }
    }

    private fun showErrorLink(id: String?) {
        lastErrorLogId = id
        view?.findViewById<View>(R.id.btnSyncErrorLog)?.visibility = if (id == null) View.GONE else View.VISIBLE
    }

    private suspend fun handleCatalogError(message: String) {
        val logContext = requireContext().applicationContext
        val id = withContext(Dispatchers.IO) {
            de.tbhelfer.server.ErrorLogStore.record(logContext, de.tbhelfer.server.ErrorHistory.Operation.CATALOG, message)
        }
        if (!isAdded) return
        showErrorLink(id)
        val cached = withContext(Dispatchers.IO) {
            runCatching { offlineRepository.loadCachedCatalog() }.getOrNull()
        }
        if (!isAdded) return
        setLoading(false)
        if (cached != null) {
            renderCachedCatalog(cached)
            statusText.text = getString(
                R.string.sync_cached_refresh_failed,
                statusText.text.toString(), lastCatalogAttempt, message
            )
            refreshConflictButton()
        } else {
            renderError(message)
            setConflictCount(0)
        }
    }

    private fun renderCatalog(catalog: SyncCatalogApi.Catalog) {
        val serverText = catalog.serverVersion?.let { "Server $it" } ?: "Server verbunden"
        statusText.text = getString(R.string.sync_connected, serverText)
        renderCatalogItems(catalog)
    }

    private fun renderCachedCatalog(cached: OfflineSyncRepository.CachedCatalog) {
        val dateText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(cached.savedAtEpochMs))
        statusText.text = getString(R.string.sync_offline_cached, dateText)
        renderCatalogItems(cached.catalog, cached = true)
    }

    private fun renderError(message: String) {
        statusText.text = getString(R.string.sync_error, message)
        routesContainer.removeAllViews()
        stationsContainer.removeAllViews()
        routesTitle.text = getString(R.string.sync_routes)
        stationsTitle.text = getString(R.string.sync_stations)
        routesContainer.addView(createMutedText(getString(R.string.sync_inventory_unknown)))
        stationsContainer.addView(createMutedText(getString(R.string.sync_inventory_unknown)))
    }

    private fun renderCatalogItems(catalog: SyncCatalogApi.Catalog, cached: Boolean = false) {
        routesTitle.text = getString(
            if (cached) R.string.sync_cached_routes_count else R.string.sync_routes_count,
            catalog.routes.size
        )
        stationsTitle.text = getString(
            if (cached) R.string.sync_cached_stations_count else R.string.sync_stations_count,
            catalog.stations.size
        )

        routesContainer.removeAllViews()
        routesContainer.addView(
            createCatalogItem(
                "＋ Strecke ergänzen",
                "Firmenübergreifend zum gemeinsamen Katalog hinzufügen"
            ) {
                offlineDialogs.showCreateRoute { loadCatalog() }
            }
        )
        if (catalog.routes.isEmpty()) {
            routesContainer.addView(createMutedText(getString(if (cached) R.string.sync_no_cached_routes else R.string.sync_no_routes)))
        } else {
            catalog.routes.forEach { route ->
                val title = buildString {
                    append(route.routeNumber.ifBlank { "Strecke" })
                    route.name?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                }
                val detail = "${route.sectionCount} Abschnitte · ${route.objectCount} Objekte"
                routesContainer.addView(createCatalogItem(title, detail) { showRouteActions(route) })
            }
        }

        stationsContainer.removeAllViews()
        stationsContainer.addView(
            createCatalogItem(
                "＋ Bahnhof ergänzen",
                "Firmenübergreifend zum gemeinsamen Katalog hinzufügen"
            ) {
                offlineDialogs.showCreateStation { loadCatalog() }
            }
        )
        if (catalog.stations.isEmpty()) {
            stationsContainer.addView(createMutedText(getString(if (cached) R.string.sync_no_cached_stations else R.string.sync_no_stations)))
        } else {
            catalog.stations.forEach { station ->
                val title = buildString {
                    append(station.name.ifBlank { "Bahnhof" })
                    station.shortName?.takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(")") }
                }
                val detail = buildString {
                    station.stationCode?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                    append(station.objectCount).append(" Objekte")
                }
                stationsContainer.addView(createCatalogItem(title, detail) { showStationActions(station) })
            }
        }
    }

    private fun showInventorySearch() {
        if (fullManualSyncRunning) return
        val input = EditText(requireContext()).apply {
            hint = "Streckennummer, Bahnhofname oder Kürzel"
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(100))
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Strecke / Bahnhof auf dem Server suchen")
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Suchen") { _, _ ->
                val query = input.text.toString().trim()
                if (query.length < 2) {
                    Toast.makeText(requireContext(), "Bitte mindestens zwei Zeichen eingeben.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val tokenStore = SecureTokenStore(requireContext())
                val token = tokenStore.load()
                if (token.isNullOrBlank()) {
                    openActivation()
                    return@setPositiveButton
                }
                setLoading(true)
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { SyncCatalogApi().lookupStations(token, query) }
                    if (!isAdded) return@launch
                    setLoading(false)
                    when (result) {
                        SyncCatalogApi.LookupResult.SessionInvalid -> handleInvalidSession(tokenStore)
                        SyncCatalogApi.LookupResult.Unsupported -> {
                            statusText.text = "Der Server unterstützt die gemeinsame Bestandssuche noch nicht. Die gespeicherte Katalogliste bleibt erhalten."
                        }
                        is SyncCatalogApi.LookupResult.Error -> {
                            statusText.text = "Bestandssuche fehlgeschlagen: ${result.message}"
                        }
                        is SyncCatalogApi.LookupResult.Success -> showInventorySearchResults(result)
                    }
                }
            }
            .show()
    }

    private fun showInventorySearchResults(result: SyncCatalogApi.LookupResult.Success) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        result.routes.forEach { route ->
            val label = "Strecke ${route.number}" + (route.name?.let { " · $it" } ?: "")
            labels += label + if (route.dataAvailable) " · Vermessungsdaten vorhanden" else " · Katalogtreffer"
            actions += {
                CatalogInventoryDialog.newInstance(label, routeNumber = route.number)
                    .show(childFragmentManager, "catalog_inventory")
            }
        }
        result.stations.forEach { station ->
            val label = "${station.name} (${station.code})"
            labels += label + if (station.dataAvailable) " · Vermessungsdaten vorhanden" else " · Referenz / Katalog"
            actions += {
                CatalogInventoryDialog.newInstance(label, stationCode = station.code)
                    .show(childFragmentManager, "catalog_inventory")
            }
        }
        val warnings = result.warnings.joinToString("\n")
        // Search has its own dialog/status: it never relabels an old cache as an online list.
        val title = "Server-Suchergebnisse (${labels.size})"
        if (labels.isEmpty()) {
            AlertDialog.Builder(requireContext()).setTitle(title)
                .setMessage(listOf("Keine Treffer für diese Suche. Der lokale Bestand wurde nicht verändert.", warnings)
                    .filter { it.isNotBlank() }.joinToString("\n"))
                .setPositiveButton(android.R.string.ok, null).show()
        } else {
            AlertDialog.Builder(requireContext()).setTitle(title)
                .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
                .setNegativeButton(android.R.string.cancel, null).show()
            if (warnings.isNotBlank()) Toast.makeText(requireContext(), warnings, Toast.LENGTH_LONG).show()
        }
    }

    private fun showRouteActions(route: SyncCatalogApi.RouteItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(route.routeNumber.ifBlank { "Strecke" })
            .setItems(arrayOf("Abschnitte öffnen", "Strecke korrigieren", "Vorhandene Daten anzeigen")) { _, which ->
                when (which) {
                    0 -> openRouteSelection(route)
                    1 -> offlineDialogs.showEditRoute(route) { loadCatalog() }
                    2 -> CatalogInventoryDialog.newInstance(route.routeNumber, routeNumber = route.routeNumber)
                        .show(childFragmentManager, "catalog_inventory")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showStationActions(station: SyncCatalogApi.StationItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(station.name.ifBlank { "Bahnhof" })
            .setItems(arrayOf("Gleise / Weichen / Objekte öffnen", "Bahnhof korrigieren", "Vorhandene Daten anzeigen")) { _, which ->
                when (which) {
                    0 -> openStationSelection(station)
                    1 -> offlineDialogs.showEditStation(station) { loadCatalog() }
                    2 -> CatalogInventoryDialog.newInstance(station.name, stationCode = station.stationCode)
                        .show(childFragmentManager, "catalog_inventory")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openRouteSelection(route: SyncCatalogApi.RouteItem) {
        val tokenStore = SecureTokenStore(requireContext())
        val token = tokenStore.load()
        if (token.isNullOrBlank()) {
            openActivation()
            return
        }

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { offlineRepository.loadSectionsForRoute(route.id) }
            val result = withContext(Dispatchers.IO) { SyncDetailApi().loadRouteSections(token, route.id) }
            if (!isAdded) return@launch
            setLoading(false)

            when (result) {
                is SyncDetailApi.Result.Success -> showRouteSelection(route, result.value, cached)
                SyncDetailApi.Result.SessionInvalid -> handleInvalidSession(tokenStore)
                is SyncDetailApi.Result.Error -> showCachedRouteOrError(route, cached, result.message)
            }
        }
    }

    private fun openStationSelection(station: SyncCatalogApi.StationItem) {
        val tokenStore = SecureTokenStore(requireContext())
        val token = tokenStore.load()
        if (token.isNullOrBlank()) {
            openActivation()
            return
        }

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { offlineRepository.loadObjectsForStation(station.id) }
            val result = withContext(Dispatchers.IO) { SyncDetailApi().loadStationObjects(token, station.id) }
            if (!isAdded) return@launch
            setLoading(false)

            when (result) {
                is SyncDetailApi.Result.Success -> showStationSelection(station, result.value, cached)
                SyncDetailApi.Result.SessionInvalid -> handleInvalidSession(tokenStore)
                is SyncDetailApi.Result.Error -> showCachedStationOrError(station, cached, result.message)
            }
        }
    }

    private fun showRouteSelection(
        route: SyncCatalogApi.RouteItem,
        available: List<SyncDetailApi.SectionItem>,
        cached: List<SyncSectionEntity>
    ) {
        if (available.isEmpty()) {
            if (cached.isNotEmpty()) {
                offlineDialogs.showSections(cached) { refreshConflictButton() }
            } else {
                Toast.makeText(requireContext(), R.string.sync_no_sections_for_route, Toast.LENGTH_LONG).show()
            }
            return
        }

        val cachedIds = cached.mapTo(hashSetOf()) { it.serverId }
        val checked = BooleanArray(available.size) { index -> available[index].id in cachedIds }
        val labels = available.map(::sectionLabel).toTypedArray()
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sync_route_download_title, route.routeNumber))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sync_save_offline) { _, _ ->
                val selected = available.filterIndexed { index, _ -> checked[index] }
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { offlineRepository.saveSelectedSections(route.id, selected) }
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.sync_sections_saved, selected.size),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        if (cached.isNotEmpty()) {
            builder.setNeutralButton(R.string.sync_edit_offline) { _, _ ->
                offlineDialogs.showSections(cached) { refreshConflictButton() }
            }
        }
        builder.show()
    }

    private fun showStationSelection(
        station: SyncCatalogApi.StationItem,
        available: List<SyncDetailApi.ObjectItem>,
        cached: List<SyncObjectEntity>
    ) {
        if (available.isEmpty()) {
            offlineDialogs.showObjectsAndCreate(station, cached) {
                refreshConflictButton()
                loadCatalog()
            }
            return
        }

        val cachedIds = cached.mapTo(hashSetOf()) { it.serverId }
        val checked = BooleanArray(available.size) { index -> available[index].id in cachedIds }
        val labels = available.map(::objectLabel).toTypedArray()
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sync_station_download_title, station.name))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sync_save_offline) { _, _ ->
                val selected = available.filterIndexed { index, _ -> checked[index] }
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { offlineRepository.saveSelectedStationObjects(station.id, selected) }
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.sync_objects_saved, selected.size),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        builder.setNeutralButton("Bearbeiten / ergänzen") { _, _ ->
            val selectedOrCached = if (cached.isNotEmpty()) cached else emptyList()
            offlineDialogs.showObjectsAndCreate(station, selectedOrCached) {
                refreshConflictButton()
                loadCatalog()
            }
        }
        builder.show()
    }

    private fun showCachedRouteOrError(
        route: SyncCatalogApi.RouteItem,
        cached: List<SyncSectionEntity>,
        message: String
    ) {
        if (cached.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.sync_error, message), Toast.LENGTH_LONG).show()
            return
        }
        statusText.text = getString(R.string.sync_detail_refresh_failed, message)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sync_route_offline_title, route.routeNumber))
            .setItems(cached.map(::cachedSectionLabel).toTypedArray()) { _, _ ->
                offlineDialogs.showSections(cached) { refreshConflictButton() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCachedStationOrError(
        station: SyncCatalogApi.StationItem,
        cached: List<SyncObjectEntity>,
        message: String
    ) {
        if (cached.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.sync_error, message), Toast.LENGTH_LONG).show()
            return
        }
        statusText.text = getString(R.string.sync_detail_refresh_failed, message)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sync_station_offline_title, station.name))
            .setItems(cached.map(::cachedObjectLabel).toTypedArray()) { _, _ ->
                offlineDialogs.showObjects(cached) { refreshConflictButton() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshConflictButton() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { offlineRepository.conflictCount() }
            if (isAdded) setConflictCount(count)
        }
    }

    private fun setConflictCount(count: Int) {
        conflictButton.visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) conflictButton.text = getString(R.string.sync_conflicts_button, count)
    }

    private fun supportsIncrementalSync(version: String?): Boolean {
        val values = version?.trim()?.split('.')?.map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        } ?: return false
        val major = values.getOrElse(0) { 0 }
        val minor = values.getOrElse(1) { 0 }
        val patch = values.getOrElse(2) { 0 }
        return major > 0 || minor > 6 || (minor == 6 && patch >= 1)
    }

    private fun sectionLabel(item: SyncDetailApi.SectionItem): String = buildString {
        append(item.designation?.takeIf { it.isNotBlank() } ?: "Abschnitt")
        kmRange(item.kmMinM ?: item.kmFromM, item.kmMaxM ?: item.kmToM)?.let { append(" · ").append(it) }
    }

    private fun cachedSectionLabel(item: SyncSectionEntity): String = buildString {
        append(item.designation?.takeIf { it.isNotBlank() } ?: "Abschnitt")
        kmRange(item.kmMinM ?: item.kmFromM, item.kmMaxM ?: item.kmToM)?.let { append(" · ").append(it) }
    }

    private fun objectLabel(item: SyncDetailApi.ObjectItem): String = buildString {
        append(item.designation?.takeIf { it.isNotBlank() } ?: item.objectType)
        if (!item.designation.isNullOrBlank()) append(" · ").append(item.objectType)
        item.kmM?.let { append(" · ").append(formatKm(it)) }
    }

    private fun cachedObjectLabel(item: SyncObjectEntity): String = buildString {
        append(item.designation?.takeIf { it.isNotBlank() } ?: item.objectType)
        if (!item.designation.isNullOrBlank()) append(" · ").append(item.objectType)
        item.kmM?.let { append(" · ").append(formatKm(it)) }
    }

    private fun kmRange(fromM: Double?, toM: Double?): String? {
        if (fromM == null && toM == null) return null
        if (fromM != null && toM != null && kotlin.math.abs(fromM - toM) > 0.0001) {
            return "${formatKm(fromM)}–${formatKm(toM)}"
        }
        return formatKm(fromM ?: toM!!)
    }

    private fun formatKm(meters: Double): String =
        String.format(Locale.GERMANY, "km %.3f", meters / 1000.0)

    private fun handleInvalidSession(tokenStore: SecureTokenStore) {
        setLoading(false)
        tokenStore.clear()
        Toast.makeText(requireContext(), R.string.activation_session_revoked, Toast.LENGTH_LONG).show()
        openActivation()
    }

    private fun createCatalogItem(title: String, detail: String, onClick: () -> Unit): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(14), dp(11), dp(14), dp(11))

            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)

            addView(TextView(context).apply {
                text = title
                setTextAppearance(R.style.TextAppearance_TBHelfer_Body)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            })
            addView(TextView(context).apply {
                text = detail
                alpha = 0.68f
                setTextAppearance(R.style.TextAppearance_TBHelfer_Caption)
                setPadding(0, dp(2), 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun createMutedText(value: String): TextView = TextView(requireContext()).apply {
        text = value
        alpha = 0.68f
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setTextAppearance(R.style.TextAppearance_TBHelfer_Body)
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        refreshButton.isEnabled = !loading
        searchButton.isEnabled = !loading
    }

    private fun openActivation() {
        startActivity(Intent(requireContext(), AktivierungActivity::class.java))
        requireActivity().finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}



