package de.tbhelfer.ui.baustelle

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.ui.bautagebuch.BautagebuchPdf
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbExportPaket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * BTB-Verlauf je Baustelle.
 * Grün: abgeschlossener BTB innerhalb 7 Tage, noch über App bearbeitbar.
 * Rot: älter als 7 Tage, nur PDF/Export anzeigen/teilen.
 */
class BtbVerlaufFragment : Fragment(R.layout.fragment_btb_verlauf) {

    private val deDateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private var hauptKnotenId: Long = -1L
    private var altBaustelleId: Long = -1L
    private var baustelleName: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hauptKnotenId = arguments?.getLong("hauptKnotenId", -1L) ?: -1L
        altBaustelleId = arguments?.getLong("altBaustelleId", -1L) ?: -1L
        baustelleName = arguments?.getString("baustelleName").orEmpty()

        view.findViewById<TextView>(R.id.tvBtbVerlaufTitle).text = "BTB-Verlauf"
        view.findViewById<TextView>(R.id.tvBtbVerlaufSubtitle).text = baustelleName.ifBlank { "Baustelle" }
        ladeVerlauf(view)
    }

    private fun ladeVerlauf(root: View) {
        val container = root.findViewById<LinearLayout>(R.id.containerBtbVerlauf)
        container.removeAllViews()
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                BautagebuchSpeicher.verlaufFuerBaustelle(
                    requireContext().applicationContext,
                    altBaustelleId.takeIf { it > 0L },
                    baustelleName
                )
            }
            if (items.isEmpty()) {
                container.addView(textView("Noch keine BTB-Daten vorhanden.", 16f, Color.DKGRAY, false))
                return@launch
            }
            items.forEach { item -> container.addView(cardFor(item)) }
        }
    }

    private fun cardFor(item: BautagebuchSpeicher.VerlaufEintrag): View {
        val ctx = requireContext()
        val green = item.bearbeitbar
        val bg = if (green) Color.parseColor("#E8F5E9") else Color.parseColor("#FFEBEE")
        val stroke = if (green) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        val status = if (green) {
            if (item.baselineExists) "Abgeschlossen · bearbeitbar · ${item.alterTage} Tage" else "Entwurf · bearbeitbar · ${item.alterTage} Tage"
        } else "Gesperrt · nur PDF/Export"
        val schicht = if (item.schicht.name == "NACHT") "Nacht" else "Tag"
        val zeit = listOf(item.entry.startUhrzeit, item.entry.endeUhrzeit)
            .filter { !it.isNullOrBlank() }
            .joinToString("–")
            .ifBlank { "ohne Zeit" }

        val card = MaterialCardView(ctx).apply {
            radius = dp(14).toFloat()
            strokeWidth = dp(2)
            setStrokeColor(stroke)
            setCardBackgroundColor(bg)
            useCompatPadding = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        inner.addView(textView("${item.datum.format(deDateFmt)} · $schicht · $zeit", 17f, Color.BLACK, true))
        inner.addView(textView(status, 13f, stroke, true))
        inner.addView(textView(if (item.pdfVorhanden) "PDF vorhanden" else "Kein Abschluss-PDF gefunden", 13f, Color.DKGRAY, false))

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        if (green) {
            row.addView(button("BTB bearbeiten") { bearbeite(item) })
        }
        row.addView(button("PDF öffnen") { oeffnePdf(item) })
        row.addView(button("ZIP teilen") { teileZip(item) })
        inner.addView(row)
        card.addView(inner)
        return card
    }

    private fun bearbeite(item: BautagebuchSpeicher.VerlaufEintrag) {
        lifecycleScope.launch {
            if (hauptKnotenId > 0L && altBaustelleId > 0L) {
                ServiceLocator.aktuelleBaustelleSpeicher.setzenNurHaupt(hauptKnotenId, altBaustelleId)
            }
            val b = Bundle().apply {
                putString("datumIso", item.datum.toString())
                putString("schichtTyp", item.schicht.name)
            }
            findNavController().navigate(R.id.bautagebuchFragment, b)
        }
    }

    private fun oeffnePdf(item: BautagebuchSpeicher.VerlaufEintrag) {
        val pdf = item.pdfDatei
        if (pdf == null || !pdf.exists()) {
            Toast.makeText(requireContext(), "Kein PDF gefunden", Toast.LENGTH_SHORT).show()
            return
        }
        BautagebuchPdf(requireContext(), ServiceLocator.fotoRepository).openPdf(pdf)
    }

    private fun teileZip(item: BautagebuchSpeicher.VerlaufEintrag) {
        val pdf = item.pdfDatei
        if (pdf == null || !pdf.exists()) {
            Toast.makeText(requireContext(), "Kein PDF für Export gefunden", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val fotos = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(item.entry.baustelleId)
                    BtbExportPaket.erstellen(requireContext().applicationContext, item.entry, pdf, fotos)
                }
                BtbExportPaket.shareZip(requireContext(), result.zipFile)
            } catch (_: Throwable) {
                Toast.makeText(requireContext(), "ZIP konnte nicht erstellt werden", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun button(text: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(requireContext()).apply {
            this.text = text
            isAllCaps = false
            textSize = 12f
            setPadding(dp(8), 0, dp(8), 0)
            minHeight = dp(36)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E66FF"))
            setTextColor(Color.WHITE)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
                topMargin = dp(10)
            }
        }
    }

    private fun textView(text: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
