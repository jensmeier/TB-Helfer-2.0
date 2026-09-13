package de.tbhelfer.ui.baustelle

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
import de.tbhelfer.ui.bautagebuch.BautagebuchPdf
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbAbschlussPdfFinder
import de.tbhelfer.ui.bautagebuch.BtbDokumentIdentitaet
import de.tbhelfer.ui.bautagebuch.BtbDokumentVersionierung
import de.tbhelfer.ui.bautagebuch.BtbExportPaket
import de.tbhelfer.ui.bautagebuch.MergeSafeBaustellenExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * BTB-Verlauf je Baustelle.
 *
 * Seit dem Multi-BTB-Modell werden ausschließlich tatsächlich angelegte BTBs angezeigt. Datum
 * und eine optionale Tag-/Nacht-Klassifikation sind nur noch Attribute; die stabile BTB-ID ist
 * maßgeblich. Abgeschlossene BTBs dürfen später erneut bearbeitet und als neue Dokumentversion
 * abgeschlossen werden.
 */
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
                var items = BautagebuchSpeicher.verlaufFuerBaustelle(
                    appContext,
                    altBaustelleId.takeIf { it > 0L },
                    baustelleName
                )
                // Ein erfolgreich geschriebenes Vn wird erst dann als Abschlussbasis übernommen,
                // wenn die erwartete PDF wirklich vorhanden ist. Beim Öffnen des Verlaufs können
                // wir diese Bestätigung ohne Risiko nachziehen und danach frisch einlesen.
                items.forEach { BtbDokumentVersionierung.synchronisiereBestaetigtenAbschluss(appContext, it.entry) }
                items = BautagebuchSpeicher.verlaufFuerBaustelle(
                    appContext,
                    altBaustelleId.takeIf { it > 0L },
                    baustelleName
                )
                val lead = items.firstOrNull()?.entry?.let { entry ->
                    runCatching { BtbDokumentIdentitaet.aufloesen(appContext, entry).verantwortlicherTb }.getOrNull()
                } ?: "TB"
                lead to items
            }
            verantwortlicherTb = data.first
            if (data.second.isEmpty()) {
                container.addView(textView("Noch keine BTB-Daten vorhanden.", 16f, Color.DKGRAY, false))
                return@launch
            }
            data.second.forEach { item -> container.addView(cardFor(item)) }
        }
    }

    private fun pdfFuer(item: BautagebuchSpeicher.VerlaufEintrag): File? =
        BtbAbschlussPdfFinder.neueste(requireContext(), item.entry)
            ?: item.pdfDatei?.takeIf { it.exists() }

    private fun cardFor(item: BautagebuchSpeicher.VerlaufEintrag): View {
        val ctx = requireContext()
        val confirmedVersion = BtbDokumentVersionierung.finalisierteVersion(ctx, item.entry)
        val version = maxOf(
            confirmedVersion,
            item.entry.dokumentVersion,
            if (item.baselineExists) 1 else 0
        )
        val changed = version > 0 && BtbDokumentVersionierung.istSeitLetztemAbschlussGeaendert(ctx, item.entry)
        val finished = version > 0 || item.entry.istFertig
        val pdf = pdfFuer(item)
        val bg = when {
            changed -> Color.parseColor("#FFF3E0")
            finished -> Color.parseColor("#E8F5E9")
            else -> Color.parseColor("#F5F5F5")
        }
        val stroke = when {
            changed -> Color.parseColor("#EF6C00")
            finished -> Color.parseColor("#2E7D32")
            else -> Color.parseColor("#616161")
        }
        val status = when {
            changed -> "Geändert seit V${version.coerceAtLeast(1)} · nächster Abschluss V${version.coerceAtLeast(1) + 1}"
            finished -> "Abgeschlossen · V${version.coerceAtLeast(1)} · erneut bearbeitbar"
            else -> "Entwurf"
        }
        val startDate = runCatching { LocalDate.parse(item.entry.datumIso) }.getOrDefault(item.datum)
        val endDate = item.entry.endeDatumIso?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        val time = listOf(item.entry.startUhrzeit, item.entry.endeUhrzeit)
            .filter { !it.isNullOrBlank() }
            .joinToString("–")
            .ifBlank { "ohne Zeit" }
        val dateLabel = if (endDate != null && endDate != startDate) {
            "${startDate.format(deDateFmt)} – ${endDate.format(deDateFmt)}"
        } else {
            startDate.format(deDateFmt)
        }

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
        inner.addView(textView(status, 13f, stroke, true))
        inner.addView(textView(if (pdf != null) "PDF vorhanden" else "Noch kein Abschluss-PDF", 13f, Color.DKGRAY, false))

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
            val b = Bundle().apply {
                putString("datumIso", item.datum.toString())
                putString("schichtTyp", item.schicht.name)
                putString("btbId", item.btbId)
            }
            findNavController().navigate(R.id.bautagebuchFragment, b)
        }
    }

    private fun oeffnePdf(item: BautagebuchSpeicher.VerlaufEintrag) {
        val pdf = pdfFuer(item)
        if (pdf == null || !pdf.exists()) {
            Toast.makeText(requireContext(), "Kein PDF gefunden", Toast.LENGTH_SHORT).show()
            return
        }
        BautagebuchPdf(requireContext(), ServiceLocator.fotoRepository).openPdf(pdf)
    }

    private fun teileZip(item: BautagebuchSpeicher.VerlaufEintrag) {
        val pdf = pdfFuer(item)
        if (pdf == null || !pdf.exists()) {
            Toast.makeText(requireContext(), "Kein PDF für Export gefunden", Toast.LENGTH_SHORT).show()
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
                    } else {
                        result.zipFile
                    }
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
