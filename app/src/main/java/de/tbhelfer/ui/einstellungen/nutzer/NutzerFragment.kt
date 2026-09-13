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
import de.tbhelfer.server.UserDisplayCache
import de.tbhelfer.server.UserProfileApi
import de.tbhelfer.util.requireViewByIdCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Getrennte Anzeige von persönlichem Benutzerprofil und zentralem Firmenprofil.
 *
 * Persönliche Telefonnummer/E-Mail werden immer gerätelokal gespeichert. Sobald der Server
 * Schema 11 / user/profile unterstützt, wird derselbe lokale Stand zusätzlich serverseitig
 * synchronisiert. Firmenstammdaten bleiben dagegen ausschließlich zentraler Serverstand.
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

    private fun lokalesProfilMitNamensFallback(): PersoenlichesNutzerProfil {
        val local = PersoenlichesNutzerProfilSpeicher.laden(requireContext())
        val fallbackName = UserDisplayCache.userName(requireContext())
        return (local ?: PersoenlichesNutzerProfil()).copy(
            name = local?.name?.takeIf { it.isNotBlank() } ?: fallbackName
        )
    }

    private fun ladePersoenlich() {
        val local = lokalesProfilMitNamensFallback()
        PersoenlichesNutzerProfilSpeicher.speichern(requireContext(), local)
        zeigePersoenlichesProfil(local)
        btnPersoenlichSpeichern.isEnabled = true

        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) return

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { userProfileApi.load(token) }
            if (!isAdded) return@launch
            when (result) {
                is UserProfileApi.Result.Success -> {
                    // Leere Serverfelder dürfen einen bereits lokal eingegebenen Kontakt nicht löschen.
                    val server = result.profile.toLocal()
                    val merged = server.copy(
                        name = server.name?.takeIf { it.isNotBlank() }
                            ?: UserDisplayCache.userName(appContext)
                            ?: local.name,
                        telefon = server.telefon?.takeIf { it.isNotBlank() } ?: local.telefon,
                        email = server.email?.takeIf { it.isNotBlank() } ?: local.email
                    )
                    withContext(Dispatchers.IO) { PersoenlichesNutzerProfilSpeicher.speichern(appContext, merged) }
                    zeigePersoenlichesProfil(merged)
                }
                UserProfileApi.Result.SchemaRequired,
                UserProfileApi.Result.Unsupported -> {
                    // Schema 10: lokale Eingabe bleibt voll nutzbar; Server-Sync kommt später.
                    btnPersoenlichSpeichern.isEnabled = true
                }
                UserProfileApi.Result.SessionInvalid -> {
                    SecureTokenStore(requireContext()).clear()
                    btnPersoenlichSpeichern.isEnabled = true
                    Snackbar.make(requireView(), "Gerätefreigabe ist nicht mehr gültig. Persönliche Daten bleiben lokal gespeichert.", Snackbar.LENGTH_LONG).show()
                }
                is UserProfileApi.Result.Error -> {
                    btnPersoenlichSpeichern.isEnabled = true
                    // Offline/Serverfehler darf die lokale Profilpflege nicht blockieren.
                }
            }
        }
    }

    private fun zeigePersoenlichesProfil(profile: PersoenlichesNutzerProfil) {
        etPersName.setText(
            profile.name?.takeIf { it.isNotBlank() }
                ?: UserDisplayCache.userName(requireContext()).orEmpty()
        )
        etPersTel.setText(profile.telefon.orEmpty())
        etPersMail.setText(profile.email.orEmpty())
    }

    private fun speicherePersoenlich() {
        val email = etPersMail.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
        val phone = etPersTel.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
        if (email != null && ('@' !in email || email.any { it.isWhitespace() })) {
            Snackbar.make(requireView(), "Bitte eine gültige persönliche E-Mail-Adresse eingeben.", Snackbar.LENGTH_LONG).show()
            return
        }

        val appContext = requireContext().applicationContext
        val previous = PersoenlichesNutzerProfilSpeicher.laden(appContext)
        val local = (previous ?: PersoenlichesNutzerProfil()).copy(
            name = previous?.name?.takeIf { it.isNotBlank() }
                ?: UserDisplayCache.userName(appContext),
            telefon = phone,
            email = email
        )
        PersoenlichesNutzerProfilSpeicher.speichern(appContext, local)
        zeigePersoenlichesProfil(local)

        val token = SecureTokenStore(requireContext()).load()
        if (token.isNullOrBlank()) {
            Snackbar.make(requireView(), "Persönliche Kontaktdaten wurden auf diesem Handy gespeichert.", Snackbar.LENGTH_SHORT).show()
            return
        }

        btnPersoenlichSpeichern.isEnabled = false
        btnPersoenlichSpeichern.text = "Wird gespeichert …"
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { userProfileApi.save(token, email, phone) }
            if (!isAdded) return@launch
            btnPersoenlichSpeichern.text = "Persönliche Daten speichern"
            btnPersoenlichSpeichern.isEnabled = true
            when (result) {
                is UserProfileApi.Result.Success -> {
                    val server = result.profile.toLocal()
                    val merged = server.copy(
                        name = server.name?.takeIf { it.isNotBlank() }
                            ?: UserDisplayCache.userName(appContext)
                            ?: local.name,
                        telefon = server.telefon?.takeIf { it.isNotBlank() } ?: local.telefon,
                        email = server.email?.takeIf { it.isNotBlank() } ?: local.email
                    )
                    withContext(Dispatchers.IO) { PersoenlichesNutzerProfilSpeicher.speichern(appContext, merged) }
                    zeigePersoenlichesProfil(merged)
                    Snackbar.make(requireView(), "Persönliche Kontaktdaten wurden lokal und auf dem Server gespeichert.", Snackbar.LENGTH_SHORT).show()
                }
                UserProfileApi.Result.SchemaRequired,
                UserProfileApi.Result.Unsupported -> Snackbar.make(
                    requireView(),
                    "Persönliche Kontaktdaten wurden auf diesem Handy gespeichert. Server-Sync folgt mit dem neuen Serverstand.",
                    Snackbar.LENGTH_LONG
                ).show()
                UserProfileApi.Result.SessionInvalid -> {
                    SecureTokenStore(requireContext()).clear()
                    Snackbar.make(requireView(), "Lokal gespeichert. Gerätefreigabe für Server-Sync ist nicht mehr gültig.", Snackbar.LENGTH_LONG).show()
                }
                is UserProfileApi.Result.Error -> Snackbar.make(
                    requireView(),
                    "Lokal gespeichert. Server-Sync derzeit nicht möglich: ${result.message}",
                    Snackbar.LENGTH_LONG
                ).show()
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
