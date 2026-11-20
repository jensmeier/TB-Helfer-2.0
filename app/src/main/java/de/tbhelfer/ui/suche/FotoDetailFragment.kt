package de.tbhelfer.ui.suche

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.text.InputType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import de.tbhelfer.util.ShareFiles
import kotlinx.coroutines.CancellationException
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik
import de.tbhelfer.ui.kamera.FotoOptionalFelderSpeicher
import de.tbhelfer.util.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FotoDetailFragment : Fragment() {

    companion object {
        const val ARG_FOTO_ID = "fotoId"
    }

    private lateinit var ivFull: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvMeta: TextView
    private lateinit var btnOpen: Button
    private lateinit var btnShare: Button
    private lateinit var btnMaps: Button
    private lateinit var btnCopy: Button
    private lateinit var btnEdit: Button

    private var currentFoto: FotoEntity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_foto_detail, container, false)
        ivFull = v.findViewById(R.id.ivFull)
        tvTitle = v.findViewById(R.id.tvTitle)
        tvMeta = v.findViewById(R.id.tvMeta)
        btnOpen = v.findViewById(R.id.btnOpen)
        btnShare = v.findViewById(R.id.btnShare)
        btnMaps = v.findViewById(R.id.btnMaps)
        btnCopy = v.findViewById(R.id.btnCopy)
        btnEdit = v.findViewById(R.id.btnEdit)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fotoId = arguments?.getLong(ARG_FOTO_ID, -1L) ?: -1L
        if (fotoId <= 0L) {
            findNavController().popBackStack()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val fotoRepo = ServiceLocator.fotoRepository
            val baustRepo = ServiceLocator.baustellenRepository
            val baumRepo = ServiceLocator.baustellenBaumRepository

            val foto: FotoEntity = fotoRepo.fotoMitId(fotoId) ?: run {
                findNavController().popBackStack()
                return@launch
            }

            tvTitle.text = foto.kurztext.takeIf { it.isNotBlank() }
                ?: SucheFotoFormatter.labelBildtyp(foto.bildtyp)

            val file = File(foto.dateipfad)
            if (file.exists()) {
                // Nur downsampled (oder Thumbnail-Datei), niemals Full-Size decode auf Main.
                ThumbnailLoader.loadInto(
                    imageView = ivFull,
                    filePath = file.absolutePath,
                    placeholderResId = android.R.drawable.ic_menu_report_image,
                    reqW = 2048,
                    reqH = 2048
                )
            }

            // DB: immer IO (Repository kapselt, aber wir halten UI-Coroutine sauber).
            val baustelle = withContext(Dispatchers.IO) {
                baustRepo.baustelleMitIdOnce(foto.baustelleId)
            }
            val knoten = foto.baustellenKnotenId?.let { id ->
                withContext(Dispatchers.IO) { baumRepo.knotenMitId(id) }
            }

            val sb = StringBuilder()
            sb.append("Baustelle: ").append(baustelle?.baustellenname ?: "-")

            BaustellenObjektLogik.unterbaustellenName(knoten)?.let {
                sb.append("\nUnterbaustelle: ").append(it)
            }
            foto.objektBereich?.takeIf { it.isNotBlank() }?.let {
                sb.append("\nBereich: ").append(it)
            }

            sb.append("\nZeit: ").append(foto.aufnahmeEpochEffektiv?.let { SucheFotoFormatter.formatZeit(it) } ?: "–")
            sb.append("\nKM: ").append(foto.km?.let { String.format(Locale.GERMANY, "%.3f", it) } ?: "–")
            sb.append("\nBildtyp: ").append(SucheFotoFormatter.labelBildtyp(foto.bildtyp))

            foto.firma?.takeIf { it.isNotBlank() }?.let { sb.append("\nFirma: ").append(it) }
            foto.objektKennung?.takeIf { it.isNotBlank() }?.let { sb.append("\nObjekt: ").append(it) }

            sb.append("\nGPS: ")
                .append(foto.gpsAnzeige)

            foto.notiz?.takeIf { it.isNotBlank() }?.let { sb.append("\nNotiz: ").append(it) }
            foto.indikator?.let { sb.append("\nIndikator: ").append(it.name) }
            val optionalLabels = withContext(Dispatchers.IO) {
                FotoOptionalFelderSpeicher.laden(requireContext(), foto.baustelleId).asList()
            }
            val optionalNames = listOf(
                "GVP / Mast",
                "AP / KOR",
                optionalLabels.getOrNull(0).orEmpty().ifBlank { "Info1" },
                optionalLabels.getOrNull(1).orEmpty().ifBlank { "Info2" },
                optionalLabels.getOrNull(2).orEmpty().ifBlank { "Info3" }
            )
            val optionalValues = listOf(
                foto.info1Wert,
                foto.info2Wert,
                foto.info3Wert,
                foto.info4Wert,
                foto.info5Wert
            )
            optionalValues.forEachIndexed { index, value ->
                value?.takeIf { it.isNotBlank() }?.let {
                    sb.append("\n").append(optionalNames.getOrNull(index).orEmpty().ifBlank { "Info${index + 1}" }).append(": ").append(it)
                }
            }


            sb.append("\nPfad: ").append(foto.dateipfad)
            if (!file.exists()) {
                sb.append("\n(⚠ Datei nicht gefunden)")
            }

            tvMeta.text = sb.toString()

            btnOpen.setOnClickListener {
                if (file.exists()) openExtern(file)
                else Toast.makeText(requireContext(), "Datei nicht gefunden", Toast.LENGTH_SHORT).show()
            }

            btnShare.setOnClickListener {
                if (file.exists()) shareExtern(file)
                else Toast.makeText(requireContext(), "Datei nicht gefunden", Toast.LENGTH_SHORT).show()
            }

            btnMaps.setOnClickListener {
                val lat = foto.gpsLatEffektiv
                val lon = foto.gpsLonEffektiv
                if (lat == null || lon == null) {
                    Toast.makeText(requireContext(), "Kein GPS gespeichert", Toast.LENGTH_SHORT).show()
                } else {
                    val geo = Uri.parse("geo:${lat},${lon}?q=${lat},${lon}")
                    startActivity(Intent(Intent.ACTION_VIEW, geo))
                }
            }

            btnEdit.setOnClickListener { zeigeBearbeiten(foto) }
            btnCopy.setOnClickListener {
                val clipText = tvMeta.text?.toString().orEmpty()
                val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Foto", clipText))
                Toast.makeText(requireContext(), "Kopiert", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun zeigeBearbeiten(foto: FotoEntity) {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val fields = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }
        fun feld(label: String, value: String, multi: Boolean = false): EditText =
            EditText(requireContext()).apply {
                hint = label
                setText(value)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                    if (multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
                if (multi) minLines = 3
                fields.addView(this)
            }
        val firm = feld("Firma", foto.firma)
        val short = feld("Kurztext", foto.kurztext)
        val notes = feld("Langtext / Notiz", foto.notiz.orEmpty(), true)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Foto korrigieren")
            .setMessage("Originalbild und Aufnahmezeit bleiben unverändert. Änderungen werden beim nächsten BTB-Abgleich an den Server übertragen.")
            .setView(fields)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val newFirm = firm.text?.toString().orEmpty()
                    val newShort = short.text?.toString().orEmpty()
                    val newNotes = notes.text?.toString()
                    try {
                        val saved = ServiceLocator.fotoRepository.updateBeschriftung(foto.id, newFirm, newShort, newNotes)
                        if (saved) {
                            tvTitle.text = newShort.trim().ifBlank { SucheFotoFormatter.labelBildtyp(foto.bildtyp) }
                            Toast.makeText(requireContext(), "Foto lokal korrigiert. Serverabgleich wird angestoßen.", Toast.LENGTH_LONG).show()
                        } else Toast.makeText(requireContext(), "Foto nicht mehr vorhanden.", Toast.LENGTH_LONG).show()
                    } catch (error: Exception) {
                        Toast.makeText(requireContext(), error.message ?: "Änderung konnte nicht gespeichert werden.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun openExtern(file: File) = sendExtern(file, open = true)

    private fun shareExtern(file: File) = sendExtern(file, open = false)

    private fun sendExtern(file: File, open: Boolean) {
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uri = ShareFiles.uri(file)
                val intent = Intent(if (open) Intent.ACTION_VIEW else Intent.ACTION_SEND).apply {
                    if (open) setDataAndType(uri, "image/*") else {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                    }
                    clipData = ClipData.newRawUri("Foto", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, if (open) ctx.getString(R.string.app_name) else "Teilen"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(ctx, "Foto konnte nicht geöffnet oder geteilt werden. Bitte Datei und freien Speicher prüfen.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
