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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbAbschlussStatus
import de.tbhelfer.ui.bautagebuch.BtbActivePointerControl
import de.tbhelfer.ui.bautagebuch.BtbDeleteSyncCoordinator
import de.tbhelfer.ui.bautagebuch.BtbDokumentIdentitaet
import de.tbhelfer.ui.bautagebuch.BtbDokumentVersionierung
import de.tbhelfer.ui.bautagebuch.BtbExportPaket
import de.tbhelfer.ui.bautagebuch.BtbPdfViewerActivity
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
            BtbAbschlussStatus.ENTWURF -> Color.parseColor("#1565C0")
        }
        // "ENTWURF" bleibt nur eine interne technische Zustandsbezeichnung. Fuer den Benutzer
        // existiert ein real angelegter gemeinsamer BTB bereits und wird deshalb als aktiv gezeigt.
        val statusText = when (status) {
            BtbAbschlussStatus.ABGESCHLOSSEN -> "Abgeschlossen · erneut bearbeitbar"
            BtbAbschlussStatus.NACHBEARBEITUNG -> "Nachbearbeitung · Abschluss-PDF ungültig"
            BtbAbschlussStatus.ENTWURF -> "BTB aktiv"
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
        row.addView(button("ZIP teilen") { teileTagesabschlussZip(item) })
        inner.addView(row)

        val deleteRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        deleteRow.addView(button("BTB löschen", Color.parseColor("#C62828")) { bestaetigeLoeschen(item) })
        inner.addView(deleteRow)

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
                withContext(Dispatchers.IO) {
                    BautagebuchSpeicher.aktivierenBtb(
                        appContext,
                        altBaustelleId.takeIf { it > 0L },
                        baustelleName,
                        id,
                        item.datum,
                        item.schicht
                    )
                    // Nur diese ausdrueckliche Verlauf-Aktion darf einen abgeschlossenen BTB mit
                    // derselben btbId wieder aktiv machen. Ein remote empfangener Abschluss auf
                    // einem anderen Handy besitzt diesen Marker bewusst nicht.
                    BtbActivePointerControl.bewusstReaktivieren(
                        appContext,
                        altBaustelleId.takeIf { it > 0L },
                        baustelleName,
                        id
                    )
                }
            }
            findNavController().navigate(
                R.id.bautagebuchFragment,
                Bundle().apply {
                    putString("datumIso", item.datum.toString())
                    putString("schichtTyp", item.schicht.name)
                    putString("btbId", item.btbId)
                    putLong("baustelleId", altBaustelleId)
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

    private fun teileTagesabschlussZip(item: BautagebuchSpeicher.VerlaufEintrag) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "Tagesabschluss-ZIP wird erstellt …", Toast.LENGTH_SHORT).show()
            try {
                val export = BtbExportPaket.erstellenBaustellenExport(
                    requireContext().applicationContext,
                    item.entry.baustelleId,
                    item.entry.baustelleName,
                    item.datum,
                    item.datum
                )
                BtbExportPaket.shareZip(requireContext(), export.zipFile)
            } catch (error: Throwable) {
                Toast.makeText(requireContext(), "ZIP konnte nicht erstellt werden: ${error.message ?: "Fehler"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun bestaetigeLoeschen(item: BautagebuchSpeicher.VerlaufEintrag) {
        val id = item.entry.btbId?.trim().orEmpty()
        if (id.isBlank() || id.startsWith("legacy:")) {
            Toast.makeText(requireContext(), "Dieser Alt-BTB kann hier noch nicht vollständig gelöscht werden.", Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("BTB im Team löschen?")
            .setMessage(
                "Dieser BTB wird mit derselben BTB-ID auf diesem Gerät und über die Synchronisation auch auf den anderen Geräten deines BTB-Teams gelöscht. " +
                    "Ohne Internet wird die Löschung vorgemerkt und später übertragen.\n\n" +
                    "Original-/Abschlussstand und BTB-PDFs werden entfernt. Die echten Baustellenfotos bleiben erhalten und werden nur von diesem BTB gelöst."
            )
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("BTB löschen") { _, _ -> loesche(item) }
            .show()
    }

    private fun loesche(item: BautagebuchSpeicher.VerlaufEintrag) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    BtbDeleteSyncCoordinator.deleteUserRequested(
                        requireContext().applicationContext,
                        item.entry
                    )
                }
            }
            result.onSuccess { info ->
                val syncText = if (info.serverSyncVorgemerkt) {
                    " · Löschung für das BTB-Team synchronisiert/vorgemerkt"
                } else {
                    " · nur lokale Baustelle (keine Server-Zuordnung)"
                }
                Toast.makeText(
                    requireContext(),
                    "BTB gelöscht · ${info.local.geloesteFotos} Foto(s) bleiben in der Baustelle erhalten$syncText",
                    Toast.LENGTH_LONG
                ).show()
                view?.let(::ladeVerlauf)
            }.onFailure { error ->
                Toast.makeText(requireContext(), "BTB konnte nicht gelöscht werden: ${error.message ?: "Fehler"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun button(text: String, color: Int = Color.parseColor("#1E66FF"), onClick: () -> Unit): MaterialButton =
        MaterialButton(requireContext()).apply {
            this.text = text
            isAllCaps = false
            textSize = 12f
            setPadding(dp(8), 0, dp(8), 0)
            minHeight = dp(36)
            backgroundTintList = ColorStateList.valueOf(color)
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
