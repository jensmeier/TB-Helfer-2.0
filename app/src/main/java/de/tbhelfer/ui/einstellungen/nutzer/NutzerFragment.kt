package de.tbhelfer.ui.einstellungen.nutzer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import de.tbhelfer.R
import de.tbhelfer.server.CompanyProfileSync
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.util.requireViewByIdCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Anzeige des zentralen Firmenprofils.
 *
 * Firmenstammdaten und Firmenlogo werden serverseitig gepflegt und auf dem Gerät nur
 * gespiegelt/gecached. Dadurch können PDF/BTB auch offline weiterarbeiten, ohne dass einzelne
 * Geräte voneinander abweichende Firmenprofile erzeugen.
 */
class NutzerFragment : Fragment(R.layout.fragment_nutzer) {

    private lateinit var etFirma: TextInputEditText
    private lateinit var etA1: TextInputEditText
    private lateinit var etA2: TextInputEditText
    private lateinit var etTel: TextInputEditText
    private lateinit var etMail: TextInputEditText

    private lateinit var btnLogo: MaterialButton
    private lateinit var btnSpeichern: MaterialButton
    private var btnZurueck: MaterialButton? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etFirma = view.requireViewByIdCompat(R.id.etFirma)
        etA1 = view.requireViewByIdCompat(R.id.etAnschrift1)
        etA2 = view.requireViewByIdCompat(R.id.etAnschrift2)
        etTel = view.requireViewByIdCompat(R.id.etTelefon)
        etMail = view.requireViewByIdCompat(R.id.etEmail)
        btnLogo = view.requireViewByIdCompat(R.id.btnLogo)
        btnSpeichern = view.requireViewByIdCompat(R.id.btnSpeichern)

        btnZurueck = view.findViewById(R.id.btnZurueck)
        btnZurueck?.setOnClickListener { findNavController().navigateUp() }

        listOf(etFirma, etA1, etA2, etTel, etMail).forEach { field ->
            field.isFocusable = false
            field.isFocusableInTouchMode = false
            field.isCursorVisible = false
            field.isLongClickable = true
        }

        btnLogo.isEnabled = false
        btnSpeichern.text = "Vom Server aktualisieren"
        btnSpeichern.setOnClickListener { aktualisieren() }

        laden()
    }

    private fun laden() {
        val p = NutzerSpeicher.laden(requireContext())
        etFirma.setText(p?.firmenname.orEmpty())
        etA1.setText(p?.anschrift1.orEmpty())
        etA2.setText(p?.anschrift2.orEmpty())
        etTel.setText(p?.telefon.orEmpty())
        etMail.setText(p?.email.orEmpty())
        btnLogo.text = when {
            p?.logoDateiname?.startsWith("firma_server_logo.") == true -> "Firmenlogo: vom Server geladen"
            !p?.logoDateiname.isNullOrBlank() -> "Firmenlogo: lokaler Altbestand"
            else -> "Firmenlogo: nicht hinterlegt"
        }
    }

    private fun aktualisieren() {
        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            Snackbar.make(requireView(), "Gerät ist noch nicht für den Server aktiviert.", Snackbar.LENGTH_LONG).show()
            return
        }

        btnSpeichern.isEnabled = false
        btnSpeichern.text = "Wird aktualisiert …"
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CompanyProfileSync.synchronize(appContext, token)
            }
            if (!isAdded) return@launch
            btnSpeichern.isEnabled = true
            btnSpeichern.text = "Vom Server aktualisieren"
            when (result) {
                CompanyProfileSync.Result.Success -> {
                    laden()
                    Snackbar.make(requireView(), "Firmenprofil wurde vom Server aktualisiert.", Snackbar.LENGTH_SHORT).show()
                }
                CompanyProfileSync.Result.Unsupported -> Snackbar.make(
                    requireView(),
                    "Dieser Serverstand stellt das zentrale Firmenprofil noch nicht bereit.",
                    Snackbar.LENGTH_LONG
                ).show()
                CompanyProfileSync.Result.SessionInvalid -> Snackbar.make(
                    requireView(),
                    "Gerätefreigabe ist nicht mehr gültig.",
                    Snackbar.LENGTH_LONG
                ).show()
                is CompanyProfileSync.Result.Error -> Snackbar.make(
                    requireView(),
                    "Firmenprofil konnte nicht aktualisiert werden: ${result.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
}
