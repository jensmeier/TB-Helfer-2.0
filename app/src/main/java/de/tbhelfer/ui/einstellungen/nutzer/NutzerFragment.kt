package de.tbhelfer.ui.einstellungen.nutzer

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import de.tbhelfer.R
import de.tbhelfer.util.requireViewByIdCompat

class NutzerFragment : Fragment(R.layout.fragment_nutzer) {

    private lateinit var etFirma: TextInputEditText
    private lateinit var etA1: TextInputEditText
    private lateinit var etA2: TextInputEditText
    private lateinit var etTel: TextInputEditText
    private lateinit var etMail: TextInputEditText

    private lateinit var btnLogo: MaterialButton
    private lateinit var btnSpeichern: MaterialButton
    // optional: user may remove the back button from the layout
    private var btnZurueck: MaterialButton? = null

    private var logoDateiname: String? = null

    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val name = NutzerSpeicher.importiereLogo(requireContext(), uri)
        if (name != null) {
            logoDateiname = name
            btnLogo.text = "Logo: $name"
            Snackbar.make(requireView(), "Logo gespeichert", Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(requireView(), "Logo konnte nicht gespeichert werden", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etFirma = view.requireViewByIdCompat(R.id.etFirma)
        etA1 = view.requireViewByIdCompat(R.id.etAnschrift1)
        etA2 = view.requireViewByIdCompat(R.id.etAnschrift2)
        etTel = view.requireViewByIdCompat(R.id.etTelefon)
        etMail = view.requireViewByIdCompat(R.id.etEmail)

        btnLogo = view.requireViewByIdCompat(R.id.btnLogo)
        btnSpeichern = view.requireViewByIdCompat(R.id.btnSpeichern)

        // If the button exists, wire it up. If not, do nothing (prevents crash).
        btnZurueck = view.findViewById(R.id.btnZurueck)
        btnZurueck?.setOnClickListener { findNavController().navigateUp() }

        btnLogo.setOnClickListener { pickLogo.launch("image/*") }
        btnSpeichern.setOnClickListener { speichern() }

        laden()
    }

    private fun laden() {
        val p = NutzerSpeicher.laden(requireContext())
        if (p != null) {
            etFirma.setText(p.firmenname.orEmpty())
            etA1.setText(p.anschrift1.orEmpty())
            etA2.setText(p.anschrift2.orEmpty())
            etTel.setText(p.telefon.orEmpty())
            etMail.setText(p.email.orEmpty())
            logoDateiname = p.logoDateiname
            if (!p.logoDateiname.isNullOrBlank()) {
                btnLogo.text = "Logo: ${p.logoDateiname}"
            }
        }
    }

    private fun speichern() {
        val vorher = NutzerSpeicher.laden(requireContext())
        val profil = NutzerProfil(
            firmenname = etFirma.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
            anschrift1 = etA1.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
            anschrift2 = etA2.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
            telefon = etTel.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
            email = etMail.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
            logoDateiname = logoDateiname,
            // Patch45m: Das PDF-Fotolimit wird jetzt unter BTB / PDF bearbeitet.
            maxFotosInPdf = vorher?.maxFotosInPdf ?: 60
        )
        NutzerSpeicher.speichern(requireContext(), profil)
        Snackbar.make(requireView(), "Nutzer gespeichert", Snackbar.LENGTH_SHORT).show()
    }
}
