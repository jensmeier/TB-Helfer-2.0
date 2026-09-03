package de.tbhelfer.ui.meinordner

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import de.tbhelfer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MeinOrdnerTextFragment : Fragment(R.layout.fragment_mein_ordner_text) {

    companion object {
        const val ARG_REL_FILE = "relFile"
    }

    private lateinit var pathText: TextView
    private lateinit var nameText: TextView
    private lateinit var editor: EditText
    private lateinit var btnSave: Button
    private lateinit var btnRename: Button
    private lateinit var btnDelete: Button

    private var relativeDatei: String = ""
    private var datei: File = MeinOrdnerDateiManager.rootOrdner()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        relativeDatei = arguments?.getString(ARG_REL_FILE).orEmpty()
        datei = MeinOrdnerDateiManager.ordnerFuerRelativenPfad(relativeDatei)

        pathText = view.findViewById(R.id.tvTextPath)
        nameText = view.findViewById(R.id.tvTextName)
        editor = view.findViewById(R.id.etTextEditor)
        btnSave = view.findViewById(R.id.btnSaveText)
        btnRename = view.findViewById(R.id.btnRenameText)
        btnDelete = view.findViewById(R.id.btnDeleteText)

        aktualisiereHeader()
        ladeText()

        btnSave.setOnClickListener { speichern() }
        btnRename.setOnClickListener { umbenennenDialog() }
        btnDelete.setOnClickListener { loeschenDialog() }
    }

    private fun aktualisiereHeader() {
        nameText.text = datei.name
        pathText.text = MeinOrdnerDateiManager.sichtbarerPfad(MeinOrdnerDateiManager.relativZuRoot(datei))
    }

    private fun ladeText() {
        viewLifecycleOwner.lifecycleScope.launch {
            editor.setText(withContext(Dispatchers.IO) {
                MeinOrdnerDateiManager.textLesen(datei)
            })
        }
    }

    private fun speichern() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { MeinOrdnerDateiManager.textSpeichern(datei, editor.text.toString()) }.isSuccess
            }
            toast(if (ok) "Text gespeichert." else "Speichern fehlgeschlagen.")
        }
    }

    private fun umbenennenDialog() {
        val input = EditText(requireContext()).apply { setText(datei.name) }
        AlertDialog.Builder(requireContext())
            .setTitle("Text umbenennen")
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val neueDatei = withContext(Dispatchers.IO) {
                        runCatching { MeinOrdnerDateiManager.umbenennen(datei, input.text.toString()) }.getOrNull()
                    }
                    if (neueDatei == null) {
                        toast("Umbenennen fehlgeschlagen.")
                    } else {
                        datei = neueDatei
                        aktualisiereHeader()
                        toast("Umbenannt.")
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun loeschenDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("In Papierkorb verschieben")
            .setMessage("${datei.name} in den Papierkorb verschieben?")
            .setPositiveButton("Ja") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        MeinOrdnerDateiManager.inPapierkorbVerschieben(datei)
                    }
                    if (ok) {
                        toast("In Papierkorb verschoben.")
                        findNavController().navigateUp()
                    } else {
                        toast("Löschen fehlgeschlagen.")
                    }
                }
            }
            .setNegativeButton("Nein", null)
            .show()
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }
}
