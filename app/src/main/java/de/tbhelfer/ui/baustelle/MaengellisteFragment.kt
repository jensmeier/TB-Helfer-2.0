package de.tbhelfer.ui.baustelle

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.ui.kamera.KameraFragment
import de.tbhelfer.util.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Patch 34: Mängelliste als Vorgangsliste.
 * - 1 Mangel = 1 Vorgang / Key
 * - mehrere Fotos je Mangel möglich
 * - Fotoarten: Mangel / In Arbeit / Erledigt
 * - Button heißt "Foto hinzufügen" statt "Nachweisfoto"
 *
 * Baustellen-Sync: Server-Mängel ohne bereits vorhandenes lokales Foto/BTB-Element werden aus
 * [SyncMangelSpeicher] in dieselbe Liste eingeblendet. Gleiche Keys werden niemals doppelt gezeigt.
 */
class MaengellisteFragment : Fragment(R.layout.fragment_maengelliste) {

    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val deFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private var altBaustelleId: Long = -1L
    private var hauptKnotenId: Long = -1L
    private var baustelleName: String = ""
    private var aktuelleItems: List<MaengellisteExport.MangelItem> = emptyList()
    private var syncUrheber: Map<String, String> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hauptKnotenId = arguments?.getLong("hauptKnotenId", -1L) ?: -1L
        altBaustelleId = arguments?.getLong("altBaustelleId", -1L) ?: -1L
        baustelleName = arguments?.getString("baustelleName").orEmpty()

        view.findViewById<TextView>(R.id.tvMaengelSubtitle).text = baustelleName.ifBlank { "Baustelle" }
        view.findViewById<MaterialButton>(R.id.btnMaengelPdf).setOnClickListener { exportPdf() }
        view.findViewById<MaterialButton>(R.id.btnMaengelCsv).setOnClickListener { exportCsv() }
        lade(view)
    }

    private fun lade(root: View) {
        val container = root.findViewById<LinearLayout>(R.id.containerMaengel)
        container.removeAllViews()
        lifecycleScope.launch {
            val ctx = requireContext().applicationContext
            val (items, urheber) = withContext(Dispatchers.IO) {
                val lokal = MaengellisteExport.ladeItems(ctx, altBaustelleId, baustelleName)
                val lokaleKeys = lokal.mapTo(hashSetOf()) { it.key }
                val server = SyncMangelSpeicher.alle(ctx, altBaustelleId)
                val serverNur = server
                    .filter { it.mangelKey !in lokaleKeys }
                    .map { s ->
                        MaengellisteExport.MangelItem(
                            key = s.mangelKey,
                            datumIso = s.datumIso,
                            zeit = s.zeit,
                            baustelle = baustelleName,
                            bereich = s.bereich,
                            km = s.km,
                            firma = s.firma,
                            beschreibung = s.beschreibung,
                            gps = s.gps,
                            fotos = emptyList(),
                            btbBildNr = s.btbBildNr
                        )
                    }
                val kombiniert = (lokal + serverNur)
                    .map { item ->
                        val edit = MangelStatusSpeicher.laden(ctx, item.key)
                        item.copy(
                            bereich = edit.bereichOverride ?: item.bereich,
                            km = edit.kmOverride ?: item.km,
                            firma = edit.firmaOverride ?: item.firma,
                            beschreibung = edit.beschreibungOverride ?: item.beschreibung
                        )
                    }
                    .distinctBy { it.key }
                    .sortedWith(compareBy<MaengellisteExport.MangelItem> { it.datumIso }.thenBy { it.zeit }.thenBy { it.beschreibung })
                val by = server.mapNotNull { s -> s.changedByName?.takeIf { it.isNotBlank() }?.let { s.mangelKey to it } }.toMap()
                kombiniert to by
            }
            aktuelleItems = items
            syncUrheber = urheber
            if (items.isEmpty()) {
                container.addView(textView("Keine Mängel vorhanden.", 16f, secondaryTextColor(), false))
                return@launch
            }
            items.forEachIndexed { index, item -> container.addView(cardFor(index + 1, item)) }
        }
    }

    private fun cardFor(nr: Int, item: MaengellisteExport.MangelItem): View {
        val ctx = requireContext()
        val wert = MangelStatusSpeicher.laden(ctx, item.key)
        val (bg, stroke, label) = farben(wert.status)

        val card = MaterialCardView(ctx).apply {
            radius = dp(14).toFloat()
            strokeWidth = dp(2)
            setStrokeColor(stroke)
            setCardBackgroundColor(bg)
            useCompatPadding = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        inner.addView(textView("Mangel ${nr.toString().padStart(3, '0')} · $label", 17f, Color.BLACK, true))
        inner.addView(textView("${datumDe(item.datumIso)} · ${item.zeit}", 13f, Color.DKGRAY, false))
        inner.addView(textView("Baustelle: ${MaengellisteExport.anzeigeBaustelle(item)}", 13f, Color.DKGRAY, false))
        inner.addView(textView("Firma: ${item.firma}   KM: ${item.km}", 13f, Color.DKGRAY, false))
        MaengellisteExport.gvpMastText(item).takeIf { it.isNotBlank() }?.let {
            inner.addView(textView("GVP/Mast: $it", 13f, Color.DKGRAY, false))
        }
        syncUrheber[item.key]?.let { inner.addView(textView("Synchronisiert von: $it", 12f, Color.DKGRAY, false)) }
        inner.addView(textView(item.beschreibung, 15f, Color.BLACK, false))
        val countLine = "Fotos: Mangel ${MaengellisteExport.countArt(item, MangelStatusSpeicher.FotoArt.MANGEL)} · in Arbeit ${MaengellisteExport.countArt(item, MangelStatusSpeicher.FotoArt.IN_ARBEIT)} · erledigt ${MaengellisteExport.countArt(item, MangelStatusSpeicher.FotoArt.ERLEDIGT)}"
        inner.addView(textView(countLine, 13f, Color.DKGRAY, false))
        if (wert.kommentar.isNotBlank()) inner.addView(textView("Kommentar: ${wert.kommentar}", 13f, Color.DKGRAY, false))
        wert.erledigtAmEpochMs?.let { inner.addView(textView("Erledigt am: ${datumZeit(it)}", 13f, Color.parseColor("#2E7D32"), true)) }

        if (item.fotos.isNotEmpty()) {
            val totalFotos = item.fotos.size
            if (totalFotos > 3) {
                inner.addView(textView("Fotos 1–3 von $totalFotos →", 12f, Color.DKGRAY, false))
            }
            val previewScroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
            }
            val previewRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, dp(8), dp(2))
            }
            item.fotos.forEach { mf ->
                val box = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) }
                }
                val img = ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(105))
                }
                ThumbnailLoader.loadInto(img, mf.foto.dateipfad, android.R.drawable.ic_menu_report_image, 500, 350)
                box.addView(img)
                box.addView(textView(MaengellisteExport.fotoArtText(mf.art), 11f, Color.DKGRAY, false))
                MaengellisteExport.fotoKmText(mf.foto).takeIf { it.isNotBlank() }?.let {
                    box.addView(textView("KM: $it", 11f, Color.DKGRAY, false))
                }
                val gpsText = MaengellisteExport.fotoGpsText(mf.foto)
                if (gpsText.isNotBlank()) {
                    val gps = textView("GPS: $gpsText", 10.5f, Color.parseColor("#1565C0"), false).apply {
                        paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                        setOnClickListener { openGps(mf.foto.gpsLat, mf.foto.gpsLon) }
                    }
                    box.addView(gps)
                }
                previewRow.addView(box)
            }
            previewScroll.addView(previewRow)
            inner.addView(previewScroll)
        }

        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("Bearbeiten") { bearbeitenDialog(item) })
        row1.addView(button("Status") { statusDialog(item) })
        inner.addView(row1)
        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("Erledigt") { erledigtDialog(item) })
        row2.addView(button("Foto hinzufügen") { fotoHinzufuegenDialog(item) })
        inner.addView(row2)
        card.addView(inner)
        return card
    }

    private fun bearbeitenDialog(item: MaengellisteExport.MangelItem) {
        val ctx = requireContext()
        val old = MangelStatusSpeicher.laden(ctx, item.key)
        if (old.status == MangelStatusSpeicher.Status.ERLEDIGT) {
            Toast.makeText(ctx, "Erledigter Mangel ist fachlich abgeschlossen und nicht mehr änderbar.", Toast.LENGTH_LONG).show()
            return
        }

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        fun field(hint: String, value: String, multiline: Boolean = false): EditText =
            EditText(ctx).apply {
                this.hint = hint
                setText(value)
                if (multiline) {
                    minLines = 3
                    gravity = android.view.Gravity.TOP
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                } else {
                    setSingleLine(true)
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
            }

        val ort = field("Baustelle / Unterbaustelle", MaengellisteExport.anzeigeBaustelle(item))
        val km = field("KM", item.km)
        val firma = field("Firma", item.firma)
        val beschreibung = field("Beschreibung", item.beschreibung, multiline = true)
        val kommentar = field("Kommentar", old.kommentar, multiline = true)
        box.addView(ort)
        box.addView(km)
        box.addView(firma)
        box.addView(beschreibung)
        box.addView(kommentar)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Mangel bearbeiten")
            .setView(box)
            .setPositiveButton("Speichern") { _, _ ->
                val ortText = ort.text?.toString()?.trim().orEmpty()
                val kmText = km.text?.toString()?.trim().orEmpty()
                val firmaText = firma.text?.toString()?.trim().orEmpty()
                val beschreibungText = beschreibung.text?.toString()?.trim().orEmpty()
                val kommentarText = kommentar.text?.toString()?.trim().orEmpty()
                MangelStatusSpeicher.speichern(
                    ctx,
                    item.key,
                    old.copy(
                        bereichOverride = ortText.takeIf { it.isNotBlank() },
                        kmOverride = kmText.takeIf { it.isNotBlank() },
                        firmaOverride = firmaText.takeIf { it.isNotBlank() },
                        beschreibungOverride = beschreibungText.takeIf { it.isNotBlank() },
                        kommentar = kommentarText
                    )
                )
                view?.let { lade(it) }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun statusDialog(item: MaengellisteExport.MangelItem) {
        val werte = arrayOf("Offen", "In Arbeit", "Erledigt", "Abgelehnt")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Status ändern")
            .setItems(werte) { _, which ->
                if (which == 2) {
                    erledigtDialog(item)
                    return@setItems
                }
                val status = when (which) {
                    1 -> MangelStatusSpeicher.Status.IN_ARBEIT
                    3 -> MangelStatusSpeicher.Status.ABGELEHNT
                    else -> MangelStatusSpeicher.Status.OFFEN
                }
                val old = MangelStatusSpeicher.laden(requireContext(), item.key)
                val neu = old.copy(status = status, erledigtAmEpochMs = null)
                MangelStatusSpeicher.speichern(requireContext(), item.key, neu)
                view?.let { lade(it) }
            }
            .show()
    }

    private fun erledigtDialog(item: MaengellisteExport.MangelItem) {
        val existing = MangelStatusSpeicher.laden(requireContext(), item.key)
        val input = EditText(requireContext()).apply {
            hint = "Kommentar zur Erledigung"
            setText(existing.kommentar)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mangel erledigt markieren")
            .setView(input)
            .setPositiveButton("Erledigt") { _, _ ->
                val old = MangelStatusSpeicher.laden(requireContext(), item.key)
                MangelStatusSpeicher.speichern(
                    requireContext(),
                    item.key,
                    old.copy(
                        status = MangelStatusSpeicher.Status.ERLEDIGT,
                        kommentar = input.text?.toString()?.trim().orEmpty(),
                        erledigtAmEpochMs = System.currentTimeMillis()
                    )
                )
                view?.let { lade(it) }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun fotoHinzufuegenDialog(item: MaengellisteExport.MangelItem) {
        val werte = arrayOf(
            "Mangel / weitere Ansicht fotografieren",
            "In Arbeit fotografieren",
            "Erledigt / abgestellt fotografieren",
            "Vorhandenes Foto auswählen"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Foto hinzufügen")
            .setItems(werte) { _, which ->
                when (which) {
                    0 -> starteNeuesMangelFoto(item, MangelStatusSpeicher.FotoArt.MANGEL)
                    1 -> starteNeuesMangelFoto(item, MangelStatusSpeicher.FotoArt.IN_ARBEIT)
                    2 -> starteNeuesMangelFoto(item, MangelStatusSpeicher.FotoArt.ERLEDIGT)
                    3 -> vorhandenesFotoAuswaehlen(item)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun fotoArtDialog(title: String, callback: (MangelStatusSpeicher.FotoArt) -> Unit) {
        val werte = arrayOf("Mangel / weitere Ansicht", "In Arbeit", "Erledigt / abgestellt")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(werte) { _, which ->
                val art = when (which) {
                    1 -> MangelStatusSpeicher.FotoArt.IN_ARBEIT
                    2 -> MangelStatusSpeicher.FotoArt.ERLEDIGT
                    else -> MangelStatusSpeicher.FotoArt.MANGEL
                }
                callback(art)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun starteNeuesMangelFoto(item: MaengellisteExport.MangelItem, art: MangelStatusSpeicher.FotoArt) {
        lifecycleScope.launch {
            if (hauptKnotenId > 0L && altBaustelleId > 0L) {
                val mangelKnotenId = item.hauptFoto?.baustellenKnotenId
                if (mangelKnotenId != null && mangelKnotenId > 0L) {
                    ServiceLocator.aktuelleBaustelleSpeicher.setzenKnoten(
                        aktuellerKnotenId = mangelKnotenId,
                        hauptKnotenId = hauptKnotenId,
                        altBaustelleId = altBaustelleId
                    )
                } else {
                    ServiceLocator.aktuelleBaustelleSpeicher.setzenNurHaupt(hauptKnotenId, altBaustelleId)
                }
            }

            val artText = MaengellisteExport.fotoArtText(art)
            val bundle = Bundle().apply {
                putBoolean(KameraFragment.ARG_NACHWEIS_MODE, true)
                putString(KameraFragment.ARG_NACHWEIS_MANGEL_KEY, item.key)
                putString(KameraFragment.ARG_NACHWEIS_STATUS, MangelStatusSpeicher.fotoArtZuStatus(art).name)
                putString(KameraFragment.ARG_MANGEL_FOTO_ART, art.name)
                putString(KameraFragment.ARG_PREFILL_FIRMA, item.firma.takeIf { it != "-" }.orEmpty())
                putString(KameraFragment.ARG_PREFILL_KURZTEXT, item.beschreibung.take(80))
                putString(KameraFragment.ARG_PREFILL_NOTIZ, "$artText-Foto zu Mangel: ${item.beschreibung}")
                item.hauptFoto?.baustellenKnotenId?.let { putLong(KameraFragment.ARG_NACHWEIS_KNOTEN_ID, it) }
                (parseKm(item.km) ?: item.hauptFoto?.km)?.let { putDouble(KameraFragment.ARG_PREFILL_KM, it) }
            }
            Toast.makeText(requireContext(), "Kamera für Mangel-Foto vorbereitet", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.kameraFragment, bundle)
        }
    }

    private fun vorhandenesFotoAuswaehlen(item: MaengellisteExport.MangelItem) {
        lifecycleScope.launch {
            val fotos = withContext(Dispatchers.IO) {
                ServiceLocator.fotoRepository
                    .fotosFuerBaustelleOnce(altBaustelleId)
                    .sortedByDescending { it.erstelltAmEpochMs }
                    .take(120)
            }
            if (fotos.isEmpty()) {
                Toast.makeText(requireContext(), "Keine Fotos vorhanden", Toast.LENGTH_SHORT).show()
                return@launch
            }
            zeigeFotoAuswahlMitVorschau(
                title = "Vorhandenes Foto auswählen",
                fotos = fotos,
                onSelected = { foto ->
                    fotoArtDialog("Fotoart für ausgewähltes Foto") { art ->
                        MangelStatusSpeicher.fotoZuordnen(requireContext(), item.key, foto.id, art)
                        Toast.makeText(requireContext(), "Foto zugeordnet", Toast.LENGTH_SHORT).show()
                        view?.let { lade(it) }
                    }
                }
            )
        }
    }

    private fun zeigeFotoAuswahlMitVorschau(title: String, fotos: List<FotoEntity>, onSelected: (FotoEntity) -> Unit) {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply {
            isFillViewport = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val liste = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        scroll.addView(liste)
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton("Abbrechen", null)
            .create()

        fotos.forEach { foto ->
            val card = MaterialCardView(ctx).apply {
                radius = dp(10).toFloat()
                strokeWidth = dp(1)
                setStrokeColor(Color.parseColor("#BDBDBD"))
                setCardBackgroundColor(surfaceColor())
                useCompatPadding = true
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    dialog.dismiss()
                    onSelected(foto)
                }
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val thumb = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(dp(86), dp(86)).apply { rightMargin = dp(10) }
            }
            ThumbnailLoader.loadInto(thumb, foto.dateipfad, android.R.drawable.ic_menu_report_image, 360, 360)
            row.addView(thumb)
            val info = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            info.addView(textView(datumZeit(foto.erstelltAmEpochMs), 13f, onSurfaceColor(), true))
            info.addView(textView("Firma: ${foto.firma.ifBlank { "-" }}", 12f, secondaryTextColor(), false))
            info.addView(textView("Typ: ${bildtypText(foto.bildtyp)}", 12f, secondaryTextColor(), false))
            info.addView(textView("Text: ${foto.kurztext.ifBlank { foto.notiz.orEmpty() }.ifBlank { "-" }}", 12f, onSurfaceColor(), false))
            foto.km?.let { info.addView(textView("KM: ${de.tbhelfer.util.KmAnzeige.wert(it)}", 12f, secondaryTextColor(), false)) }
            row.addView(info)
            card.addView(row)
            liste.addView(card)
        }
        dialog.show()
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = exportDir()
                    val file = File(dir, "Maengelliste_${safe(baustelleName)}_${System.currentTimeMillis()}.csv")
                    file.writeText("\uFEFF" + MaengellisteExport.csvText(requireContext().applicationContext, aktuelleItems), Charsets.UTF_8)
                    file
                }
                shareFile(file, "text/csv", "Mängelliste CSV teilen")
            } catch (_: Throwable) {
                Toast.makeText(requireContext(), "CSV konnte nicht erstellt werden", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportPdf() {
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = exportDir()
                    val file = File(dir, "Maengelliste_${safe(baustelleName)}_${System.currentTimeMillis()}.pdf")
                    file.writeBytes(MaengellisteExport.pdfBytes(requireContext().applicationContext, baustelleName, aktuelleItems))
                    file
                }
                openFile(file, "application/pdf")
            } catch (_: Throwable) {
                Toast.makeText(requireContext(), "PDF konnte nicht erstellt werden", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportDir(): File {
        val base = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: requireContext().filesDir
        return File(base, "TB-Helfer-Export/Maengellisten").apply { mkdirs() }
    }

    private fun shareFile(file: File, mime: String, title: String) {
        val uri: Uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".dateianbieter", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    private fun openFile(file: File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".dateianbieter", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { shareFile(file, mime, "Datei teilen") }
    }

    private fun openGps(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return
        val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        val googleMaps = Intent(Intent.ACTION_VIEW, geo).apply { setPackage("com.google.android.apps.maps") }
        val pm = requireContext().packageManager
        if (googleMaps.resolveActivity(pm) != null) {
            startActivity(googleMaps)
        } else {
            val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
            startActivity(Intent(Intent.ACTION_VIEW, web))
        }
    }

    private fun parseKm(value: String): Double? {
        val raw = Regex("-?\\d+(?:[.,]\\d+)?").find(value)?.value ?: return null
        return raw.replace(',', '.').toDoubleOrNull()
    }

    private fun farben(status: MangelStatusSpeicher.Status): Triple<Int, Int, String> = when (status) {
        MangelStatusSpeicher.Status.OFFEN -> Triple(Color.parseColor("#FFEBEE"), Color.parseColor("#C62828"), "OFFEN")
        MangelStatusSpeicher.Status.IN_ARBEIT -> Triple(Color.parseColor("#FFF8E1"), Color.parseColor("#F9A825"), "IN ARBEIT")
        MangelStatusSpeicher.Status.ERLEDIGT -> Triple(Color.parseColor("#E8F5E9"), Color.parseColor("#2E7D32"), "ERLEDIGT")
        MangelStatusSpeicher.Status.ABGELEHNT -> Triple(Color.parseColor("#ECEFF1"), Color.parseColor("#607D8B"), "ABGELEHNT")
    }

    private fun button(text: String, onClick: () -> Unit): MaterialButton = MaterialButton(requireContext()).apply {
        this.text = text
        isAllCaps = false
        textSize = 11f
        minHeight = dp(34)
        setPadding(dp(6), 0, dp(6), 0)
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E66FF"))
        setTextColor(Color.WHITE)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(5); topMargin = dp(10) }
    }

    private fun textView(text: String, size: Float, color: Int, bold: Boolean): TextView = TextView(requireContext()).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        val ok = requireContext().theme.resolveAttribute(attr, tv, true)
        if (!ok) return fallback
        return if (tv.resourceId != 0) runCatching { requireContext().getColor(tv.resourceId) }.getOrDefault(fallback) else tv.data
    }

    private fun surfaceColor(): Int = themeColor(com.google.android.material.R.attr.colorSurface, Color.WHITE)
    private fun onSurfaceColor(): Int = themeColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    private fun secondaryTextColor(): Int = runCatching { requireContext().getColor(R.color.tb_text_secondary) }.getOrDefault(Color.DKGRAY)

    private fun datumDe(iso: String): String = runCatching { LocalDate.parse(iso, isoFmt).format(deFmt) }.getOrElse { iso }
    private fun datumZeit(epoch: Long): String = MaengellisteExport.datumZeit(epoch)
    private fun safe(s: String): String = s.trim().replace(Regex("[\\/:*?\"<>|]"), "_").take(60).ifBlank { "Baustelle" }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun bildtypText(bildtyp: Bildtyp): String = when (bildtyp) {
        Bildtyp.ABNAHMEN -> "Abnahme"
        Bildtyp.EINWEISUNG_UNTERWEISUNG -> "Einweisung / Unterweisung"
        Bildtyp.BESONDERHEITEN -> "Besonderheit"
        Bildtyp.MAENGEL_SCHAEDEN -> "Mängel / Schäden"
        Bildtyp.FOTODOKUMENTATION -> "Fotodokumentation"
    }
}
