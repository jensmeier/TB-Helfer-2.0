package de.tbhelfer.ui.sync

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.OfflineSyncRepository
import de.tbhelfer.datenbank.SyncObjectEntity
import de.tbhelfer.datenbank.SyncSectionEntity
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.SyncCatalogApi
import de.tbhelfer.server.SyncDetailApi
import de.tbhelfer.ui.activation.AktivierungActivity
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncKatalogFragment : Fragment(R.layout.fragment_sync_katalog) {

    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var routesContainer: LinearLayout
    private lateinit var stationsContainer: LinearLayout
    private lateinit var routesTitle: TextView
    private lateinit var stationsTitle: TextView
    private lateinit var refreshButton: Button

    private val offlineRepository: OfflineSyncRepository
        get() = ServiceLocator.offlineSyncRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.txtSyncStatus)
        progress = view.findViewById(R.id.progressSync)
        routesContainer = view.findViewById(R.id.routesContainer)
        stationsContainer = view.findViewById(R.id.stationsContainer)
        routesTitle = view.findViewById(R.id.txtRoutesTitle)
        stationsTitle = view.findViewById(R.id.txtStationsTitle)
        refreshButton = view.findViewById(R.id.btnSyncRefresh)

        refreshButton.setOnClickListener { loadCatalog() }
        loadCatalog()
    }

    private fun loadCatalog() {
        val tokenStore = SecureTokenStore(requireContext())
        val token = tokenStore.load()
        if (token.isNullOrBlank()) {
            openActivation()
            return
        }

        setLoading(true)
        statusText.text = getString(R.string.sync_loading)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SyncCatalogApi().loadCatalog(token)
            }

            if (!isAdded) return@launch

            when (result) {
                is SyncCatalogApi.Result.Success -> {
                    val cacheSaved = withContext(Dispatchers.IO) {
                        runCatching { offlineRepository.saveCatalog(result.catalog) }.isSuccess
                    }
                    if (!isAdded) return@launch
                    setLoading(false)
                    renderCatalog(result.catalog)
                    if (!cacheSaved) {
                        statusText.text = getString(
                            R.string.sync_cache_warning,
                            statusText.text.toString()
                        )
                    }
                }

                SyncCatalogApi.Result.SessionInvalid -> handleInvalidSession(tokenStore)

                is SyncCatalogApi.Result.Error -> {
                    val cached = withContext(Dispatchers.IO) {
                        runCatching { offlineRepository.loadCachedCatalog() }.getOrNull()
                    }
                    if (!isAdded) return@launch
                    setLoading(false)
                    if (cached != null) {
                        renderCachedCatalog(cached)
                    } else {
                        renderError(result.message)
                    }
                }
            }
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
        renderCatalogItems(cached.catalog)
    }

    private fun renderError(message: String) {
        statusText.text = getString(R.string.sync_error, message)
        routesContainer.removeAllViews()
        stationsContainer.removeAllViews()
        routesTitle.text = getString(R.string.sync_routes_count, 0)
        stationsTitle.text = getString(R.string.sync_stations_count, 0)
    }

    private fun renderCatalogItems(catalog: SyncCatalogApi.Catalog) {
        routesTitle.text = getString(R.string.sync_routes_count, catalog.routes.size)
        stationsTitle.text = getString(R.string.sync_stations_count, catalog.stations.size)

        routesContainer.removeAllViews()
        if (catalog.routes.isEmpty()) {
            routesContainer.addView(createMutedText(getString(R.string.sync_no_routes)))
        } else {
            catalog.routes.forEach { route ->
                val title = buildString {
                    append(route.routeNumber.ifBlank { "Strecke" })
                    route.name?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                }
                val detail = "${route.sectionCount} Abschnitte · ${route.objectCount} Objekte"
                routesContainer.addView(createCatalogItem(title, detail) {
                    openRouteSelection(route)
                })
            }
        }

        stationsContainer.removeAllViews()
        if (catalog.stations.isEmpty()) {
            stationsContainer.addView(createMutedText(getString(R.string.sync_no_stations)))
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
                stationsContainer.addView(createCatalogItem(title, detail) {
                    openStationSelection(station)
                })
            }
        }
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
            val cached = withContext(Dispatchers.IO) {
                offlineRepository.loadSectionsForRoute(route.id)
            }
            val result = withContext(Dispatchers.IO) {
                SyncDetailApi().loadRouteSections(token, route.id)
            }
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
            val cached = withContext(Dispatchers.IO) {
                offlineRepository.loadObjectsForStation(station.id)
            }
            val result = withContext(Dispatchers.IO) {
                SyncDetailApi().loadStationObjects(token, station.id)
            }
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
            Toast.makeText(requireContext(), R.string.sync_no_sections_for_route, Toast.LENGTH_LONG).show()
            return
        }

        val cachedIds = cached.mapTo(hashSetOf()) { it.serverId }
        val checked = BooleanArray(available.size) { index -> available[index].id in cachedIds }
        val labels = available.map(::sectionLabel).toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sync_route_download_title, route.routeNumber))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sync_save_offline) { _, _ ->
                val selected = available.filterIndexed { index, _ -> checked[index] }
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        offlineRepository.saveSelectedSections(route.id, selected)
                    }
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.sync_sections_saved, selected.size),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun showStationSelection(
        station: SyncCatalogApi.StationItem,
        available: List<SyncDetailApi.ObjectItem>,
        cached: List<SyncObjectEntity>
    ) {
        if (available.isEmpty()) {
            Toast.makeText(requireContext(), R.string.sync_no_objects_for_station, Toast.LENGTH_LONG).show()
            return
        }

        val cachedIds = cached.mapTo(hashSetOf()) { it.serverId }
        val checked = BooleanArray(available.size) { index -> available[index].id in cachedIds }
        val labels = available.map(::objectLabel).toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sync_station_download_title, station.name))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sync_save_offline) { _, _ ->
                val selected = available.filterIndexed { index, _ -> checked[index] }
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        offlineRepository.saveSelectedStationObjects(station.id, selected)
                    }
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.sync_objects_saved, selected.size),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .show()
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
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sync_route_offline_title, route.routeNumber))
            .setItems(cached.map(::cachedSectionLabel).toTypedArray(), null)
            .setPositiveButton(android.R.string.ok, null)
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
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sync_station_offline_title, station.name))
            .setItems(cached.map(::cachedObjectLabel).toTypedArray(), null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun sectionLabel(item: SyncDetailApi.SectionItem): String = buildString {
        append(item.designation?.takeIf { it.isNotBlank() } ?: "Abschnitt")
        kmRange(item.kmMinM ?: item.kmFromM, item.kmMaxM ?: item.kmToM)?.let {
            append(" · ").append(it)
        }
    }

    private fun cachedSectionLabel(item: SyncSectionEntity): String = buildString {
        append(item.designation?.takeIf { it.isNotBlank() } ?: "Abschnitt")
        kmRange(item.kmMinM ?: item.kmFromM, item.kmMaxM ?: item.kmToM)?.let {
            append(" · ").append(it)
        }
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
    }

    private fun openActivation() {
        startActivity(Intent(requireContext(), AktivierungActivity::class.java))
        requireActivity().finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
