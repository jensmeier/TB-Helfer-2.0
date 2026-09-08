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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.R
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.SyncCatalogApi
import de.tbhelfer.ui.activation.AktivierungActivity
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
            setLoading(false)

            when (result) {
                is SyncCatalogApi.Result.Success -> renderCatalog(result.catalog)
                SyncCatalogApi.Result.SessionInvalid -> {
                    tokenStore.clear()
                    Toast.makeText(requireContext(), R.string.activation_session_revoked, Toast.LENGTH_LONG).show()
                    openActivation()
                }
                is SyncCatalogApi.Result.Error -> {
                    statusText.text = getString(R.string.sync_error, result.message)
                    routesContainer.removeAllViews()
                    stationsContainer.removeAllViews()
                    routesTitle.text = getString(R.string.sync_routes_count, 0)
                    stationsTitle.text = getString(R.string.sync_stations_count, 0)
                }
            }
        }
    }

    private fun renderCatalog(catalog: SyncCatalogApi.Catalog) {
        val serverText = catalog.serverVersion?.let { "Server $it" } ?: "Server verbunden"
        statusText.text = getString(R.string.sync_connected, serverText)

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
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sync_route_selected, route.routeNumber),
                        Toast.LENGTH_SHORT
                    ).show()
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
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sync_station_selected, station.name),
                        Toast.LENGTH_SHORT
                    ).show()
                })
            }
        }
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
