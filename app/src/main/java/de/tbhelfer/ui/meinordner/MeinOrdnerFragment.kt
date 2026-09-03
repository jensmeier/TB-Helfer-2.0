package de.tbhelfer.ui.meinordner

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MeinOrdnerFragment : Fragment(R.layout.fragment_mein_ordner) {

    companion object {
        const val ARG_REL_PATH = "relPath"
        const val ARG_TRASH_MODE = "trashMode"
    }

    private lateinit var recycler: RecyclerView
    private lateinit var titleText: TextView
    private lateinit var pathText: TextView
    private lateinit var emptyText: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnView: Button
    private lateinit var btnSort: Button
    private lateinit var btnTrash: Button
    private lateinit var btnNeu: Button
    private lateinit var btnCreateFirst: Button
    private lateinit var searchView: SearchView

    private lateinit var adapter: MeinOrdnerAdapter
    private lateinit var einstellungen: MeinOrdnerEinstellungenSpeicher

    private var relativePfad: String = ""
    private var trashMode: Boolean = false
    private var currentOrdner: File = MeinOrdnerDateiManager.rootOrdner()
    private var aktuellerSuchtext: String = ""
    private var hatInitialLeerDialogGezeigt = false
    private var bekannteBaustellen: List<Pair<Long, String>> = emptyList()

    private val bildPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importiereUri(uri)
    }
    private val pdfPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importiereUri(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        relativePfad = arguments?.getString(ARG_REL_PATH).orEmpty()
        trashMode = arguments?.getBoolean(ARG_TRASH_MODE, false) == true
        currentOrdner = if (trashMode) MeinOrdnerDateiManager.papierkorbOrdner() else MeinOrdnerDateiManager.ordnerFuerRelativenPfad(relativePfad)
        einstellungen = MeinOrdnerEinstellungenSpeicher(requireContext())

        titleText = view.findViewById(R.id.tvTitle)
        pathText = view.findViewById(R.id.tvPath)
        emptyText = view.findViewById(R.id.tvEmpty)
        btnBack = view.findViewById(R.id.btnBack)
        btnView = view.findViewById(R.id.btnViewMode)
        btnSort = view.findViewById(R.id.btnSort)
        btnTrash = view.findViewById(R.id.btnTrash)
        btnNeu = view.findViewById(R.id.btnNeu)
        btnCreateFirst = view.findViewById(R.id.btnCreateFirst)
        searchView = view.findViewById(R.id.searchMeinOrdner)
        recycler = view.findViewById(R.id.recyclerMeinOrdner)

        adapter = MeinOrdnerAdapter(
            onClick = { eintrag -> onEintragClick(eintrag) },
            onMore = { anchor, eintrag -> zeigeEintragMenue(anchor, eintrag) }
        )
        recycler.adapter = adapter
        setzeLayoutManager()
        aktualisiereAnsichtsButtons()

        btnBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigateUp()
            }
        }
        btnTrash.setOnClickListener {
            findNavController().navigate(
                R.id.meinOrdnerFragment,
                bundleOf(ARG_TRASH_MODE to true)
            )
        }
        btnView.setOnClickListener {
            einstellungen.ansicht = if (einstellungen.ansicht == MeinOrdnerAnsicht.LISTE) {
                MeinOrdnerAnsicht.KARTEN
            } else {
                MeinOrdnerAnsicht.LISTE
            }
            setzeLayoutManager()
            aktualisiereAnsichtsButtons()
        }
        btnSort.setOnClickListener { zeigeSortierMenue(it) }
        btnNeu.setOnClickListener { zeigeNeuMenue(it) }
        btnCreateFirst.setOnClickListener { zeigeOrdnerDialog(null, null) }

        searchView.queryHint = "Suche (Name + Textinhalt)"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                aktuellerSuchtext = query.orEmpty()
                ladeInhalt()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                aktuellerSuchtext = newText.orEmpty()
                ladeInhalt()
                return true
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            bekannteBaustellen = ladeBaustellenOptionen()
        }

        aktualisiereHeader()
        ladeInhalt()
    }

    private fun aktualisiereHeader() {
        titleText.text = if (trashMode) "Mein Ordner · Papierkorb" else "Mein Ordner"
        pathText.text = if (trashMode) {
            "Documents/TB-Helfer/Mein Ordner/.papierkorb"
        } else {
            MeinOrdnerDateiManager.sichtbarerPfad(relativePfad)
        }
        val isRoot = !trashMode && relativePfad.isBlank()
        btnBack.visibility = if (isRoot) View.INVISIBLE else View.VISIBLE
        btnNeu.visibility = if (trashMode) View.GONE else View.VISIBLE
        btnCreateFirst.visibility = View.GONE
        btnTrash.visibility = if (trashMode) View.GONE else View.VISIBLE
    }

    private fun aktualisiereAnsichtsButtons() {
        btnView.text = if (einstellungen.ansicht == MeinOrdnerAnsicht.LISTE) "Liste" else "Karten"
        btnSort.text = when (einstellungen.sortierung) {
            MeinOrdnerSortierung.NAME_AUF -> "Name A-Z"
            MeinOrdnerSortierung.NAME_AB -> "Name Z-A"
            MeinOrdnerSortierung.DATUM_NEU -> "Datum neu"
            MeinOrdnerSortierung.DATUM_ALT -> "Datum alt"
        }
    }

    private fun setzeLayoutManager() {
        recycler.layoutManager = if (einstellungen.ansicht == MeinOrdnerAnsicht.LISTE || trashMode) {
            LinearLayoutManager(requireContext())
        } else {
            GridLayoutManager(requireContext(), 2)
        }
    }

    private fun ladeInhalt() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                if (trashMode) {
                    MeinOrdnerDateiManager.listPapierkorb(einstellungen.sortierung, aktuellerSuchtext)
                } else if (aktuellerSuchtext.isBlank()) {
                    MeinOrdnerDateiManager.listOrdner(currentOrdner, einstellungen.sortierung)
                } else {
                    MeinOrdnerDateiManager.suche(currentOrdner, aktuellerSuchtext, einstellungen.sortierung)
                }
            }
            adapter.submitList(items)
            val leer = items.isEmpty()
            emptyText.visibility = if (leer) View.VISIBLE else View.GONE
            btnCreateFirst.visibility = if (leer && !trashMode && relativePfad.isBlank()) View.VISIBLE else View.GONE
            emptyText.text = when {
                trashMode -> "Papierkorb ist leer."
                aktuellerSuchtext.isNotBlank() -> "Keine Treffer gefunden."
                relativePfad.isBlank() -> "Noch kein Ordner vorhanden."
                else -> "Ordner ist leer."
            }
            if (leer && !trashMode && relativePfad.isBlank() && !hatInitialLeerDialogGezeigt && aktuellerSuchtext.isBlank()) {
                hatInitialLeerDialogGezeigt = true
                zeigeOrdnerDialog(null, null)
            }
        }
    }

    private fun onEintragClick(eintrag: MeinOrdnerEintrag) {
        if (trashMode) {
            zeigePapierkorbDialog(eintrag)
            return
        }
        when (eintrag.typ) {
            MeinOrdnerTyp.ORDNER -> {
                findNavController().navigate(
                    R.id.meinOrdnerFragment,
                    bundleOf(ARG_REL_PATH to eintrag.relativePfad)
                )
            }
            MeinOrdnerTyp.TEXT -> {
                findNavController().navigate(
                    R.id.action_meinOrdner_to_textEditor,
                    bundleOf(MeinOrdnerTextFragment.ARG_REL_FILE to eintrag.relativePfad)
                )
            }
            else -> dateiOeffnen(eintrag.datei)
        }
    }

    private fun zeigeNeuMenue(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Ordner anlegen")
        popup.menu.add(0, 2, 1, "Text anlegen")
        popup.menu.add(0, 3, 2, "Bild importieren")
        popup.menu.add(0, 4, 3, "PDF importieren")
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> zeigeOrdnerDialog(null, null)
                2 -> zeigeTextErstellenDialog()
                3 -> bildPicker.launch(arrayOf("image/*"))
                4 -> pdfPicker.launch(arrayOf("application/pdf"))
            }
            true
        }
        popup.show()
    }

    private fun zeigeSortierMenue(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Name A-Z")
        popup.menu.add(0, 2, 1, "Name Z-A")
        popup.menu.add(0, 3, 2, "Datum neu")
        popup.menu.add(0, 4, 3, "Datum alt")
        popup.setOnMenuItemClickListener {
            einstellungen.sortierung = when (it.itemId) {
                1 -> MeinOrdnerSortierung.NAME_AUF
                2 -> MeinOrdnerSortierung.NAME_AB
                3 -> MeinOrdnerSortierung.DATUM_NEU
                else -> MeinOrdnerSortierung.DATUM_ALT
            }
            aktualisiereAnsichtsButtons()
            ladeInhalt()
            true
        }
        popup.show()
    }

    private fun zeigeEintragMenue(anchor: View, eintrag: MeinOrdnerEintrag) {
        if (trashMode) {
            zeigePapierkorbDialog(eintrag)
            return
        }
        val popup = PopupMenu(requireContext(), anchor)
        var order = 0
        popup.menu.add(0, 1, order++, "Öffnen")
        popup.menu.add(0, 2, order++, if (eintrag.typ == MeinOrdnerTyp.ORDNER) "Umbenennen / Baustelle" else "Umbenennen")
        if (eintrag.typ != MeinOrdnerTyp.ORDNER) {
            popup.menu.add(0, 3, order++, "Exportieren / teilen")
        }
        popup.menu.add(0, 4, order, "In Papierkorb")
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> onEintragClick(eintrag)
                2 -> if (eintrag.typ == MeinOrdnerTyp.ORDNER) {
                    zeigeOrdnerDialog(eintrag.datei, eintrag.baustelleRef)
                } else {
                    zeigeUmbenennenDialog(eintrag.datei)
                }
                3 -> dateiTeilen(eintrag.datei)
                4 -> inPapierkorbVerschieben(eintrag.datei)
            }
            true
        }
        popup.show()
    }

    private fun zeigePapierkorbDialog(eintrag: MeinOrdnerEintrag) {
        val wrapper = eintrag.papierkorbWrapper ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(eintrag.anzeigeName)
            .setMessage("Wiederherstellen oder endgültig löschen?")
            .setPositiveButton("Wiederherstellen") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        MeinOrdnerDateiManager.wiederherstellenAusPapierkorb(wrapper)
                    }
                    toast(if (ok) "Wiederhergestellt." else "Wiederherstellen fehlgeschlagen.")
                    ladeInhalt()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("Endgültig löschen") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        MeinOrdnerDateiManager.endgueltigLoeschenAusPapierkorb(wrapper)
                    }
                    toast(if (ok) "Endgültig gelöscht." else "Löschen fehlgeschlagen.")
                    ladeInhalt()
                }
            }
            .show()
    }

    private fun zeigeOrdnerDialog(vorhanden: File?, vorhandeneRef: MeinOrdnerBaustelleRef?) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }
        val nameInput = EditText(requireContext()).apply {
            hint = "Ordnername"
            setText(vorhanden?.name.orEmpty())
        }
        val baustelleInput = androidx.appcompat.widget.AppCompatAutoCompleteTextView(requireContext()).apply {
            hint = "Baustelle (optional)"
            setText(vorhandeneRef?.name.orEmpty())
            val names = listOf("") + bekannteBaustellen.map { it.second }
            setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names))
            threshold = 0
            setOnClickListener { showDropDown() }
        }
        container.addView(nameInput)
        container.addView(baustelleInput)

        AlertDialog.Builder(requireContext())
            .setTitle(if (vorhanden == null) "Ordner anlegen" else "Ordner bearbeiten")
            .setView(container)
            .setPositiveButton("Speichern") { _, _ ->
                val selectedName = baustelleInput.text?.toString()?.trim().orEmpty()
                val ref = bekannteBaustellen.firstOrNull { it.second.equals(selectedName, true) }
                    ?.let { MeinOrdnerBaustelleRef(it.first, it.second) }
                    ?: selectedName.takeIf { it.isNotBlank() }?.let { MeinOrdnerBaustelleRef(null, it) }
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            if (vorhanden == null) {
                                MeinOrdnerDateiManager.ordnerAnlegen(currentOrdner, nameInput.text.toString(), ref)
                            } else {
                                val neuerOrdner = if (nameInput.text.toString().trim() != vorhanden.name) {
                                    MeinOrdnerDateiManager.umbenennen(vorhanden, nameInput.text.toString())
                                } else vorhanden
                                MeinOrdnerDateiManager.schreibeOrdnerMeta(neuerOrdner, ref)
                            }
                        }.isSuccess
                    }
                    toast(if (ok) "Ordner gespeichert." else "Ordner konnte nicht gespeichert werden.")
                    ladeInhalt()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun zeigeTextErstellenDialog() {
        val input = EditText(requireContext()).apply { hint = "Textname" }
        AlertDialog.Builder(requireContext())
            .setTitle("Text anlegen")
            .setView(input)
            .setPositiveButton("Erstellen") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val file = withContext(Dispatchers.IO) {
                        runCatching { MeinOrdnerDateiManager.textDateiAnlegen(currentOrdner, input.text.toString()) }.getOrNull()
                    }
                    if (file == null) {
                        toast("Text konnte nicht erstellt werden.")
                    } else {
                        findNavController().navigate(
                            R.id.action_meinOrdner_to_textEditor,
                            bundleOf(MeinOrdnerTextFragment.ARG_REL_FILE to MeinOrdnerDateiManager.relativZuRoot(file))
                        )
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun zeigeUmbenennenDialog(datei: File) {
        val input = EditText(requireContext()).apply { setText(datei.name) }
        AlertDialog.Builder(requireContext())
            .setTitle("Umbenennen")
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { MeinOrdnerDateiManager.umbenennen(datei, input.text.toString()) }.isSuccess
                    }
                    toast(if (ok) "Umbenannt." else "Umbenennen fehlgeschlagen.")
                    ladeInhalt()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun inPapierkorbVerschieben(datei: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("In Papierkorb verschieben")
            .setMessage("${datei.name} in den Papierkorb verschieben?")
            .setPositiveButton("Ja") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        MeinOrdnerDateiManager.inPapierkorbVerschieben(datei)
                    }
                    toast(if (ok) "In Papierkorb verschoben." else "Verschieben fehlgeschlagen.")
                    ladeInhalt()
                }
            }
            .setNegativeButton("Nein", null)
            .show()
    }

    private fun importiereUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val displayName = holeDateinamen(uri) ?: "Import_${System.currentTimeMillis()}"
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        MeinOrdnerDateiManager.importDatei(requireContext(), input, displayName, currentOrdner)
                    } ?: error("Import fehlgeschlagen")
                }.isSuccess
            }
            toast(if (ok) "Datei importiert." else "Import fehlgeschlagen.")
            ladeInhalt()
        }
    }

    private fun holeDateinamen(uri: Uri): String? {
        requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun dateiOeffnen(file: File) {
        val mime = when {
            MeinOrdnerDateiManager.istBildDatei(file) -> "image/*"
            file.extension.equals("pdf", true) -> "application/pdf"
            MeinOrdnerDateiManager.istTextDatei(file) -> "text/plain"
            else -> "*/*"
        }
        val uri = runCatching {
            FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".dateianbieter", file)
        }.getOrNull()
        if (uri == null) {
            toast("Datei konnte nicht geöffnet werden.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { toast("Kein passender Viewer gefunden.") }
    }



    private fun dateiTeilen(file: File) {
        val mime = when {
            MeinOrdnerDateiManager.istBildDatei(file) -> "image/*"
            file.extension.equals("pdf", true) -> "application/pdf"
            MeinOrdnerDateiManager.istTextDatei(file) -> "text/plain"
            else -> "*/*"
        }
        val uri = runCatching {
            FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".dateianbieter", file)
        }.getOrNull()
        if (uri == null) {
            toast("Datei konnte nicht exportiert werden.")
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Datei exportieren")) }
            .onFailure { toast("Exportieren fehlgeschlagen.") }
    }

    private suspend fun ladeBaustellenOptionen(): List<Pair<Long, String>> {
        val repo = ServiceLocator.baustellenBaumRepository
        val haupt = repo.alleHauptbaustellen().first()
        return haupt.map { it.id to it.hauptbaustellenName }
            .sortedBy { it.second.lowercase(Locale.ROOT) }
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }
}
