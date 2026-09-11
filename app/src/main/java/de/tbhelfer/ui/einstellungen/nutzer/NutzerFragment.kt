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
import de.tbhelfer.server.UserProfileApi
import de.tbhelfer.util.requireViewByIdCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Getrennte Anzeige von persoenlichem Benutzerprofil und zentralem Firmenprofil.
 *
 * - persoenliche E-Mail/Telefon: pro Server-Benutzer editierbar
 * - Firmenstammdaten/Logo: zentraler Serverstand, auf dem Geraet nur Offline-Cache
 */
class NutzerFragment : Fragment(R.layout.fragment_nutzer) {

    private lateinit var etPersName: TextInputEditText
    private lateinit var etPersTel: TextInputEditText
    private lateinit var etPersMail: TextInputEditText
    private lateinit var btnPersoenlichSpeichern: MaterialButton

    private lateinit var etFirma: TextInputEditText
    private lateinit var etA1: TextInputEditText
    private lateinit var etA2: TextInputEditText
    private lateinit var etTel: TextInputEditText
    private lateinit var etMail: TextInputEditText
    private lateinit var btnLogo: MaterialButton
    private lateinit var btnSpeichern: MaterialButton
    private var btnZurueck: MaterialButton? = null

    private val userProfileApi = UserProfileApi()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etPersName = view.requireViewByIdCompat(R.id.etPersName)
        etPersTel = view.requireViewByIdCompat(R.id.etPersTelefon)
        etPersMail = view.requireViewByIdCompat(R.id.etPersEmail)
        btnPersoenlichSpeichern = view.requireViewByIdCompat(R.id.btnPersoenlichSpeichern)

        etFirma = view.requireViewByIdCompat(R.id.etFirma)
        etA1 = view.requireViewByIdCompat(R.id.etAnschrift1)
        etA2 = view.requireViewByIdCompat(R.id.etAnschrift2)
        etTel = view.requireViewByIdCompat(R.id.etTelefon)
        etMail = view.requireViewByIdCompat(R.id.etEmail)
        btnLogo = view.requireViewByIdCompat(R.id.btnLogo)
        btnSpeichern = view.requireViewByIdCompat(R.id.btnSpeichern)

        btnZurueck = view.findViewById(R.id.btnZurueck)
        btnZurueck?.setOnClickListener { findNavController().navigateUp() }

        makeReadOnly(etPersName)
        listOf(etFirma, etA1, etA2, etTel, etMail).forEach(::makeReadOnly)

        btnLogo.isEnabled = false
        btnSpeichern.text = "Firmendaten vom Server aktualisieren"
        btnSpeichern.setOnClickListener { aktualisiereFirma() }
        btnPersoenlichSpeichern.setOnClickListener { speicherePersoenlich() }

        ladeFirma()
        ladePersoenlich()
    }

    private fun makeReadOnly(field: TextInputEditText) {
        field.isFocusable = false
        field.isFocusableInTouchMode = false
        field.isCursorVisible = false
        field.isLongClickable = true
    }

    private fun ladeFirma() {
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

    private fun ladePersoenlich() {
        PersoenlichesNutzerProfilSpeicher.laden(requireContext())?.let(::zeigePersoenlichesProfil)

        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            btnPersoenlichSpeichern.isEnabled = false
            return
        }

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { userProfileApi.load(token) }
            if (!isAdded) return@launch
            when (result) {
                is UserProfileApi.Result.Success -> {
                    val local = result.profile.toLocal()
                    withContext(Dispatchers.IO) { PersoenlichesNutzerProfilSpeicher.speichern(appContext, local) }
                    zeigePersoenlichesProfil(local)
                    btnPersoenlichSpeichern.isEnabled = true
                }
                UserProfileApi.Result.SchemaRequired -> {
                    btnPersoenlichSpeichern.isEnabled = false
                    Snackbar.make(requireView(), "Persönliche Telefonnummer wird mit dem nächsten Server-Schema freigeschaltet.", Snackbar.LENGTH_LONG).show()
                }
                UserProfileApi.Result.Unsupported -> {
                    btnPersoenlichSpeichern.isEnabled = false
                    Snackbar.make(requireView(), "Dieser Serverstand unterstützt das persönliche Profil noch nicht.", Snackbar.LENGTH_LONG).show()
                }
                UserProfileApi.Result.SessionInvalid -> {
                    SecureTokenStore(requireContext()).clear()
                    btnPersoenlichSpeichern.isEnabled = false
                    Snackbar.make(requireView(), "Gerätefreigabe ist nicht mehr gültig.", Snackbar.LENGTH_LONG).show()
                }
                is UserProfileApi.Result.Error -> Snackbar.make(
                    requireView(),
                    "Persönliches Profil konnte nicht geladen werden: ${result.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun zeigePersoenlichesProfil(profile: PersoenlichesNutzerProfil) {
        etPersName.setText(profile.name.orEmpty())
        etPersTel.setText(profile.telefon.orEmpty())
        etPersMail.setText(profile.email.orEmpty())
    }

    private fun speicherePersoenlich() {
        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            Snackbar.make(requireView(), "Gerät ist noch nicht für den Server aktiviert.", Snackbar.LENGTH_LONG).show()
            return
        }

        val email = etPersMail.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
        val phone = etPersTel.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
        if (email != null && ('@' !in email || email.any(Char::isWhitespace))) {
            Snackbar.make(requireView(), "Bitte eine gültige persönliche E-Mail-Adresse eingeben.", Snackbar.LENGTH_LONG).show()
            return
        }

        btnPersoenlichSpeichern.isEnabled = false
        btnPersoenlichSpeichern.text = "Wird gespeichert …"
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { userProfileApi.save(token, email, phone) }
            if (!isAdded) return@launch
            btnPersoenlichSpeichern.text = "Persönliche Daten speichern"
            when (result) {
                is UserProfileApi.Result.Success -> {
                    val local = result.profile.toLocal()
                    withContext(Dispatchers.IO) { PersoenlichesNutzerProfilSpeicher.speichern(appContext, local) }
                    zeigePersoenlichesProfil(local)
                    btnPersoenlichSpeichern.isEnabled = true
                    Snackbar.make(requireView(), "Persönliche Kontaktdaten wurden gespeichert.", Snackbar.LENGTH_SHORT).show()
                }
                UserProfileApi.Result.SchemaRequired -> Snackbar.make(
                    requireView(),
                    "Persönliches Profil ist auf dem Server noch nicht freigeschaltet.",
                    Snackbar.LENGTH_LONG
                ).show()
                UserProfileApi.Result.Unsupported -> Snackbar.make(
                    requireView(),
                    "Dieser Serverstand unterstützt das persönliche Profil noch nicht.",
                    Snackbar.LENGTH_LONG
                ).show()
                UserProfileApi.Result.SessionInvalid -> {
                    SecureTokenStore(requireContext()).clear()
                    Snackbar.make(requireView(), "Gerätefreigabe ist nicht mehr gültig.", Snackbar.LENGTH_LONG).show()
                }
                is UserProfileApi.Result.Error -> {
                    btnPersoenlichSpeichern.isEnabled = true
                    Snackbar.make(requireView(), "Speichern fehlgeschlagen: ${result.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun UserProfileApi.Profile.toLocal() = PersoenlichesNutzerProfil(
        userId = userId,
        name = fullName,
        telefon = phone,
        email = email,
        profileVersion = profileVersion
    )

    private fun aktualisiereFirma() {
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
            btnSpeichern.text = "Firmendaten vom Server aktualisieren"
            when (result) {
                CompanyProfileSync.Result.Success -> {
                    ladeFirma()
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
