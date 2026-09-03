package de.tbhelfer.ui.kamera

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class FotoListeFragment : Fragment(R.layout.fragment_foto_liste) {

    private lateinit var recycler: RecyclerView
    private lateinit var textLeer: TextView
    private val adapter = FotoAdapter { foto -> oeffnen(foto.dateipfad) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.recyclerFotos)
        textLeer = view.findViewById(R.id.textLeer)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val baustelleId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first()
            if (baustelleId == null) {
                textLeer.text = "Keine aktuelle Baustelle gewählt."
                adapter.setzen(emptyList())
                textLeer.visibility = View.VISIBLE
                return@launch
            }

            // Single Source of Truth: Liste kommt aus Room (DB).
            // Filesystem wird nur für Existenz/Öffnen genutzt.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.fotoRepository.fotosFuerBaustelle(baustelleId).collect { fotos ->
                    val valid = fotos.filter { File(it.dateipfad).exists() }
                    adapter.setzen(valid)
                    textLeer.visibility = if (valid.isEmpty()) View.VISIBLE else View.GONE
                    if (valid.isEmpty()) {
                        textLeer.text = "Keine Fotos gefunden."
                    }
                }
            }
        }
    }

    private fun oeffnen(pfad: String) {
        try {
            val datei = File(pfad)
            if (!datei.exists()) {
                Toast.makeText(requireContext(), "Datei fehlt (Speicher ggf. bereinigen)", Toast.LENGTH_LONG).show()
                return
            }
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.dateianbieter",
                datei
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Foto öffnen"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Foto konnte nicht geöffnet werden: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
