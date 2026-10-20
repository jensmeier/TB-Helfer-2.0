package de.tbhelfer.ui.sync

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Typeface
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import de.tbhelfer.server.CatalogInventory
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.SyncCatalogApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Nur lesend. Auswahl und Downloads bleiben in den bestehenden Katalogaktionen. */
class CatalogInventoryDialog : DialogFragment() {
    private var summary: TextView? = null
    private var search: EditText? = null
    private var list: LinearLayout? = null
    private var retry: Button? = null
    private var inventory: CatalogInventory? = null
    private var loadJob: Job? = null
    private var limit = PAGE_SIZE

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        summary = TextView(context).also { body.addView(it) }
        search = EditText(context).apply {
            hint = "Gleis, Weiche oder Datenart suchen"
            contentDescription = hint
            setSingleLine(true)
            isEnabled = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    limit = PAGE_SIZE
                    renderRows()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }.also { body.addView(it) }
        list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }.also { body.addView(it) }
        retry = Button(context).apply {
            text = "Erneut versuchen"
            setOnClickListener { loadInventory() }
        }.also { body.addView(it) }
        return AlertDialog.Builder(context)
            .setTitle("Vorhandene Daten · ${arguments?.getString("title").orEmpty()}")
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton("Schließen", null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        if (inventory == null) loadInventory() else renderInventory()
    }

    private fun loadInventory() {
        if (loadJob?.isActive == true) return
        val route = arguments?.getString("route")
        val station = arguments?.getString("station")
        if (route.isNullOrBlank() && station.isNullOrBlank()) {
            showError("Streckennummer oder eindeutiges Bahnhofskürzel fehlt. Bitte zuerst den Katalogeintrag korrigieren.")
            return
        }
        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            showError("Bitte zuerst das Gerät aktivieren.")
            return
        }
        summary?.text = "Gemeinsamer Bestand wird geladen …"
        retry?.visibility = android.view.View.GONE
        search?.isEnabled = false
        loadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { SyncCatalogApi().loadInventory(token, route, station) }
            // withContext propagiert auch nach blockierendem HTTP eine zwischenzeitliche Abbruchanforderung.
            if (!isAdded || dialog?.isShowing != true) return@launch
            when (result) {
                is SyncCatalogApi.InventoryResult.Success -> {
                    inventory = result.inventory
                    renderInventory()
                }
                SyncCatalogApi.InventoryResult.Unsupported -> showError("Dieser Server bietet die Bestandsübersicht noch nicht an.")
                SyncCatalogApi.InventoryResult.SessionInvalid -> showError("Gerätezugriff ist nicht gültig. Bitte Aktivierung prüfen.")
                is SyncCatalogApi.InventoryResult.Error -> showError(result.message)
            }
        }
    }

    private fun showError(message: String) {
        summary?.text = message
        list?.removeAllViews()
        search?.isEnabled = false
        retry?.visibility = android.view.View.VISIBLE
    }

    private fun renderInventory() {
        val data = inventory ?: return
        summary?.text = "Gemeinsamer Bestand (${data.rows.size})\n${data.summary()}"
        retry?.visibility = android.view.View.GONE
        search?.isEnabled = data.rows.isNotEmpty()
        renderRows()
    }

    private fun renderRows() {
        val container = list ?: return
        val data = inventory ?: return
        container.removeAllViews()
        val found = data.rows.filter { it.matches(search?.text?.toString().orEmpty()) }
        found.take(limit).forEach { row ->
            container.addView(TextView(requireContext()).apply {
                text = row.title()
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(4))
            })
            container.addView(TextView(requireContext()).apply { text = row.details() })
        }
        if (found.isEmpty() && data.rows.isNotEmpty()) container.addView(TextView(requireContext()).apply {
            text = "Keine passenden Objekte."
        })
        if (found.size > limit) container.addView(Button(requireContext()).apply {
            text = "Weitere anzeigen (${found.size - limit})"
            setOnClickListener { limit += PAGE_SIZE; renderRows() }
        })
    }

    override fun onDismiss(dialog: DialogInterface) {
        loadJob?.cancel()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        summary = null
        search = null
        list = null
        retry = null
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PAGE_SIZE = 40
        fun newInstance(title: String, routeNumber: String? = null, stationCode: String? = null) =
            CatalogInventoryDialog().apply {
                arguments = Bundle().apply {
                    putString("title", title)
                    putString("route", routeNumber)
                    putString("station", stationCode)
                }
            }
    }
}
