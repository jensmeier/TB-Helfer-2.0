package de.tbhelfer.ui.suche

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.datenbank.FotoVermessungsSnapshotSpeicher
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
            currentFoto = foto

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
            val vermessung = withContext(Dispatchers.IO) {
                FotoVermessungsSnapshotSpeicher.laden(requireContext().applicationContext, foto.id)
            }

            val sb = StringBuilder()
            sb.append("Baustelle: ").append(baustelle?.baustellenname ?: "-")

            BaustellenObjektLogik.unterbaustellenName(knoten)?.let {
                sb.append("\nUnterbaustelle: ").append(it)
            }
            foto.objektBereich?.takeIf { it.isNotBlank() }?.let {
                sb.append("\nBereich: ").append(it)
            }

            sb.append("\nZeit: ").append(SucheFotoFormatter.formatZeit(foto.erstelltAmEpochMs))
            sb.append("\nKM: ").append(foto.km?.let { String.format(Locale.GERMANY, "%.3f", it) } ?: "–")
            vermessung?.kmQuelle?.takeIf { it.isNotBlank() }?.let {
                sb.append("\nKM-Quelle: ").append(it)
            }
            vermessung?.strecke?.takeIf { it.isNotBlank() }?.let {
                sb.append("\nStrecke: ").append(it)
            }
            vermessung?.achseName?.takeIf { it.isNotBlank() }?.let {
                sb.append("\nAchse: ").append(it)
            }
            sb.append("\nBildtyp: ").append(SucheFotoFormatter.labelBildtyp(foto.bildtyp))

            foto.firma.takeIf { it.isNotBlank() }?.let { sb.append("\nFirma: ").append(it) }
            foto.objektKennung?.takeIf { it.isNotBlank() }?.let { sb.append("\nObjekt: ").append(it) }

            sb.append("\nGPS: ")
                .append(String.format(Locale.US, "%.6f, %.6f", foto.gpsLat, foto.gpsLon))
            vermessung?.gpsAccuracyM?.let {
                sb.append("\nGPS-Genauigkeit: ±")
                    .append(String.format(Locale.GERMANY, "%.1f m", it))
            }
            (vermessung?.achsabstandM ?: foto.achsabstandM)?.let {
                sb.append("\nAchsabstand: ")
                    .append(String.format(Locale.GERMANY, "%.1f m", it))
            }

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
                    sb.append("\n")
                        .append(optionalNames.getOrNull(index).orEmpty().ifBlank { "Info${index + 1}" })
                        .append(": ")
                        .append(it)
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
                val lat = foto.gpsLat
                val lon = foto.gpsLon
                if (lat == 0.0 && lon == 0.0) {
                    Toast.makeText(requireContext(), "Kein GPS gespeichert", Toast.LENGTH_SHORT).show()
                } else {
                    val geo = Uri.parse("geo:${lat},${lon}?q=${lat},${lon}")
                    startActivity(Intent(Intent.ACTION_VIEW, geo))
                }
            }

            btnCopy.setOnClickListener {
                val clipText = tvMeta.text?.toString().orEmpty()
                val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Foto", clipText))
                Toast.makeText(requireContext(), "Kopiert", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openExtern(file: File) {
        val ctx = requireContext()
        val uri: Uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.dateianbieter",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.app_name)))
    }

    private fun shareExtern(file: File) {
        val ctx = requireContext()
        val uri: Uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.dateianbieter",
            file
        )

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Teilen"))
    }
}
