package de.tbhelfer.ui.abnahme

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import de.tbhelfer.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AbnahmelisteFragment : Fragment(R.layout.fragment_abnahmeliste) {

    private var filter: String = "ALLE"
    private var baustelleId: Long = -1L
    private var baustelleName: String = ""
    private var hauptKnotenId: Long = -1L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        baustelleId = arguments?.getLong(ARG_BAUSTELLE_ID, -1L) ?: -1L
        baustelleName = arguments?.getString(ARG_BAUSTELLE_NAME).orEmpty()
        hauptKnotenId = arguments?.getLong(ARG_HAUPT_KNOTEN_ID, -1L) ?: -1L
        filter = arguments?.getString(ARG_FILTER).orEmpty().ifBlank { "ALLE" }

        view.findViewById<TextView>(R.id.tvAbnahmeListeBaustelle).text = baustelleName
        view.findViewById<View>(R.id.btnNeueStrukturAbnahme).setOnClickListener { openEditor(null) }
        view.findViewById<View>(R.id.btnAbnahmeAlle).setOnClickListener { filter = "ALLE"; render(view) }
        view.findViewById<View>(R.id.btnFilterAbnahmeJa).setOnClickListener { filter = "JA"; render(view) }
        view.findViewById<View>(R.id.btnFilterAbnahmeNein).setOnClickListener { filter = "NEIN"; render(view) }
        view.findViewById<View>(R.id.btnFilterAbnahmePruefung).setOnClickListener { filter = "PRUEFUNG"; render(view) }
        render(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let(::render)
    }

    private fun render(root: View) {
        val all = if (baustelleId > 0L) AbnahmeSpeicher.alle(requireContext(), baustelleId) else emptyList()
        val count = AbnahmeSpeicher.Zaehler(
            ja = all.count { AbnahmeSpeicher.filterKey(it) == "JA" },
            nein = all.count { AbnahmeSpeicher.filterKey(it) == "NEIN" },
            inPruefung = all.count { AbnahmeSpeicher.filterKey(it) == "PRUEFUNG" }
        )
        (root.findViewById<MaterialButton>(R.id.btnFilterAbnahmeJa)).text = "Ja ${count.ja}"
        (root.findViewById<MaterialButton>(R.id.btnFilterAbnahmeNein)).text = "Nein ${count.nein}"
        (root.findViewById<MaterialButton>(R.id.btnFilterAbnahmePruefung)).text = "In Prüfung ${count.inPruefung}"

        val shown = all.filter { filter == "ALLE" || AbnahmeSpeicher.filterKey(it) == filter }
        val container = root.findViewById<LinearLayout>(R.id.containerAbnahmen)
        container.removeAllViews()
        root.findViewById<View>(R.id.tvKeineAbnahmen).visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        shown.forEach { a -> container.addView(cardFor(a)) }
    }

    private fun cardFor(a: AbnahmeSpeicher.Abnahme): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = dp(12).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(statusColor(a))
            setCardBackgroundColor(Color.TRANSPARENT)
            useCompatPadding = true
            isClickable = true
            isFocusable = true
            setOnClickListener { openEditor(a.id) }
        }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val title = TextView(requireContext()).apply {
            text = "Abnahme ${a.nummer.toString().padStart(3, '0')} · ${AbnahmeSpeicher.statusLabel(a).uppercase()}"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(statusColor(a))
            textSize = 14f
        }
        val body = TextView(requireContext()).apply {
            val km = de.tbhelfer.util.KmAnzeige.bereich(a.kmVon, a.kmBis, inKlammern = false)
                ?.let { " · $it" }.orEmpty()
            text = buildString {
                appendLine(a.bezeichnung.ifBlank { "Ohne Bezeichnung" })
                if (a.objekt.isNotBlank()) appendLine("Objekt: ${a.objekt}")
                if (a.unterKnotenName.isNotBlank()) append("Unterbaustelle: ${a.unterKnotenName}$km\n")
                if (a.firma.isNotBlank()) appendLine("Firma: ${a.firma}")
                append("Beginn: ${format(a.startEpochMs)} · Fotos: ${a.fotos.size} · Pläne: ${a.anlagen.size}")
            }.trim()
            textSize = 12.5f
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        val btnOpen = MaterialButton(requireContext()).apply {
            text = "Öffnen"
            minHeight = 0
            minWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { openEditor(a.id) }
        }
        val btnPdf = MaterialButton(requireContext()).apply {
            text = "PDF"
            minHeight = 0
            minWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val file = AbnahmeExport.erzeugeEinzelPdf(requireContext(), a)
                    AbnahmeExport.sharePdf(requireContext(), file)
                }
            }
        }
        row.addView(btnOpen)
        row.addView(btnPdf)
        box.addView(title)
        box.addView(body)
        box.addView(row)
        card.addView(box)
        return card
    }

    private fun openEditor(id: String?) {
        findNavController().navigate(R.id.abnahmeEditorFragment, Bundle().apply {
            putLong(ARG_BAUSTELLE_ID, baustelleId)
            putString(ARG_BAUSTELLE_NAME, baustelleName)
            putLong(ARG_HAUPT_KNOTEN_ID, hauptKnotenId)
            putString(ARG_ABNAHME_ID, id.orEmpty())
        })
    }

    private fun statusColor(abnahme: AbnahmeSpeicher.Abnahme): Int = when (AbnahmeSpeicher.filterKey(abnahme)) {
        "JA" -> Color.parseColor("#2E7D32")
        "NEIN" -> Color.parseColor("#C62828")
        else -> if (abnahme.ergebnis == AbnahmeSpeicher.Ergebnis.ABGENOMMEN_MIT_RESTPUNKTEN) {
            Color.parseColor("#EF6C00")
        } else {
            Color.parseColor("#616161")
        }
    }

    private fun format(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val ARG_BAUSTELLE_ID = "baustelleId"
        const val ARG_BAUSTELLE_NAME = "baustelleName"
        const val ARG_HAUPT_KNOTEN_ID = "hauptKnotenId"
        const val ARG_ABNAHME_ID = "abnahmeId"
        const val ARG_FILTER = "filter"
    }
}

