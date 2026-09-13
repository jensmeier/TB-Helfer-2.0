package de.tbhelfer.ui.baustelle

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
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
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbAbschlussStatus
import de.tbhelfer.ui.bautagebuch.BtbDokumentIdentitaet
import de.tbhelfer.ui.bautagebuch.BtbDokumentVersionierung
import de.tbhelfer.ui.bautagebuch.BtbExportPaket
import de.tbhelfer.ui.bautagebuch.BtbPdfViewerActivity
import de.tbhelfer.ui.bautagebuch.MergeSafeBaustellenExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Verlauf tatsächlich vorhandener BTBs; technische Auswahl immer über btbId. */
class BtbVerlaufFragment : Fragment(R.layout.fragment_btb_verlauf) {

    private val deDateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private var hauptKnotenId: Long = -1L
    private var altBaustelleId: Long = -1L
    private var baustelleName: String = ""
    private var verantwortlicherTb: String = "TB"

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
            val data = withContext(Dispatchers.IO) {
                val appContext = requireContext().applicationContext
                val items = BautagebuchSpeicher.verlaufFuerBaustelle(
                    appContext,
                    altBaustelleId.takeIf { it > 0L },
                    baustelleName
                )
                items.forEach { BtbDokumentVersionierung.pflegeNachAutosave(appContext, it.entry) }
                val refreshed = BautagebuchSpeicher.verlaufFuerBaustelle(
                    appContext,
                    altBaustelleId.takeIf { it > 0L },
                    baustelleName
                )
                val lead = refreshed.firstOrNull()?.entry?.let { entry ->
                    runCatching { BtbDokumentIdentitaet.aufloesen(appContext, entry).verantwortlicherTb }.getOrNull()
                } ?: "TB"
                lead to refreshed
            }
            verantwortlicherTb = data.first
            if (data.second.isEmpty()) {
                container.addView(textView("Noch keine BTB-Daten vorhanden.", 16f, Color.DKGRAY, false))
                return@launch
            }
            data.second.forEach { item -> container.addView(cardFor(item)) }
        }
    }

    private fun cardFor(item: BautagebuchSpeicher.VerlaufEintrag): View {
        val ctx = requireContext()
        val status = BtbDokumentVersionierung.status(ctx, item.entry)
        val pdf = BtbDokumentVersionierung.aktuellesAbschlussPdf(ctx, item.entry)

        val bg = when (status) {
            BtbAbschlussStatus.ABGESCHLOSSEN -> Color.parseColor("#E8F5E9")
            BtbAbschlussStatus.NACHBEARBEITUNG -> Color.parseColor("#FFF3E0")
            BtbAbschlussStatus.ENTWURF -> Color.parseColor("#F5F5F5")
        }
        val stroke = when (status) {
            BtbAbschlussStatus.ABGESCHLOSSEN -> Color.parseColor("#2E7D32")
            BtbAbschlussStatus.NACHBEARBEITUNG -> Color.parseColor("#EF6C00")
            BtbAbschlussStatus.ENTWURF -> Color.parseColor("#616161")
        }
        val statusText = when (status) {
            BtbAbschlussStatus.ABGESCHLOSSEN -> "Abgeschlossen · erneut bearbeitbar"
            BtbAbschlussStatus.NACHBEARBEITUNG -> "Nachbearbeitung · Abschluss-PDF ungültig"
            BtbAbschlussStatus.ENTWURF -> "Entwurf"
        }

        val startDate = runCatching { LocalDate.parse(item.entry.datumIso) }.getOrDefault(item.datum)
        val endDate = item.entry.endeDatumIso?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        val time = listOf(item.entry.startUhrzeit, item.entry.endeUhrzeit)
            .filter { !it.isNullOrBlank() }
            .joinToString("–")
            .ifBlank { "ohne Zeit" }
        val dateLabel = if (endDate != null && endDate != startDate) {
            "${startDate.format(deDateFmt)} – ${endDate.format(deDateFmt)}"
        } else startDate.format(deDateFmt)

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
        inner.addView(textView("BTB $verantwortlicherTb · $dateLabel", 17f, Color.BLACK, true))
        inner.addView(textView("Arbeitszeit: $time", 13f, Color.DKGRAY, false))
        inner.addView(textView(statusText, 13f, stroke, true))
        inner.addView(textView(if (pdf != null) "PDF vorhanden" else "Kein gültiges Abschluss-PDF", 13f, Color.DKGRAY, false))

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("BTB bearbeiten") { bearbeite(item) })
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
            val appContext = requireContext().applicationContext
            item.btbId?.takeIf { !it.startsWith("legacy:") }?.let { id ->
                BautagebuchSpeicher.aktivierenBtb(
                    appContext,
                    altBaustelleId.takeIf { it > 0L },
                    baustelleName,
                    id,
                    item.datum,
                    item.schicht
                )
            }
            findNavController().navigate(
                R.id.bautagebuchFragment,
                Bundle().apply {
                    putString("datumIso", item.datum.toString())
                    putString("schichtTyp", item.schicht.name)
                    putString("btbId", item.btbId)
                }
            )
        }
    }

    private fun oeffnePdf(item: BautagebuchSpeicher.VerlaufEintrag) {
        val pdf = BtbDokumentVersionierung.aktuellesAbschlussPdf(requireContext(), item.entry)
        if (pdf == null || !pdf.exists()) {
            Toast.makeText(requireContext(), "Kein gültiges Abschluss-PDF gefunden", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(requireContext(), BtbPdfViewerActivity::class.java).apply {
            putExtra(BtbPdfViewerActivity.EXTRA_FILE_PATH, pdf.absolutePath)
            putExtra(BtbPdfViewerActivity.EXTRA_TITLE, "BTB $verantwortlicherTb")
        })
    }

    private fun teileZip(item: BautagebuchSpeicher.VerlaufEintrag) {
        val pdf = BtbDokumentVersionierung.aktuellesAbschlussPdf(requireContext(), item.entry)
        if (pdf == null || !pdf.exists()) {
            Toast.makeText(requireContext(), "Kein gültiges Abschluss-PDF für Export vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val exportZip = withContext(Dispatchers.IO) {
                    val appContext = requireContext().applicationContext
                    val fotos = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(item.entry.baustelleId)
                    val result = BtbExportPaket.erstellen(appContext, item.entry, pdf, fotos)
                    if (hauptKnotenId > 0L) {
                        MergeSafeBaustellenExport.normalisieren(
                            context = appContext,
                            sourceZip = result.zipFile,
                            localRootKnotenId = hauptKnotenId,
                            baustelleName = baustelleName.ifBlank { item.entry.baustelleName },
                            schicht = item.schicht
                        )
                    } else result.zipFile
                }
                BtbExportPaket.shareZip(requireContext(), exportZip)
            } catch (_: Throwable) {
                Toast.makeText(requireContext(), "ZIP konnte nicht erstellt werden", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun button(text: String, onClick: () -> Unit): MaterialButton = MaterialButton(requireContext()).apply {
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

    private fun textView(text: String, size: Float, color: Int, bold: Boolean): TextView = TextView(requireContext()).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
