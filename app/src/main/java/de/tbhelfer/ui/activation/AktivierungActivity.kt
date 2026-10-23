package de.tbhelfer.ui.activation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import de.tbhelfer.R
import de.tbhelfer.server.ActivationApi
import de.tbhelfer.server.CompanyProfileSync
import de.tbhelfer.server.DeviceIdentity
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.ServerConfig
import de.tbhelfer.server.TeamManagementApi
import de.tbhelfer.server.UserDisplayCache
import de.tbhelfer.ui.HauptActivity
import de.tbhelfer.ui.KeyboardAutoScroll
import de.tbhelfer.ui.SafeAreaInsets
import de.tbhelfer.ui.style.UiStyleApplier
import de.tbhelfer.ui.sync.WorksiteInvitationLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AktivierungActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(de.tbhelfer.server.recovery.RecoveryWorkspace.wrap(newBase))
    }

    private lateinit var keyInput: EditText
    private lateinit var scanButton: Button
    private lateinit var activateButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView

    private lateinit var tokenStore: SecureTokenStore
    private var pendingInvitation: WorksiteInvitationLink.Credential? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (de.tbhelfer.server.recovery.RecoveryWorkspace.blocked()) {
            startActivity(Intent(this, de.tbhelfer.server.recovery.RecoveryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }

        tokenStore = SecureTokenStore(this)
        pendingInvitation = WorksiteInvitationLink.parse(intent?.data?.toString().orEmpty())

        SafeAreaInsets.install(this)
        setContentView(R.layout.activity_aktivierung)
        UiStyleApplier.install(this)
        KeyboardAutoScroll.install(this)

        keyInput = findViewById(R.id.activationKeyInput)
        scanButton = findViewById(R.id.activationScanButton)
        activateButton = findViewById(R.id.activationActivateButton)
        progress = findViewById(R.id.activationProgress)
        statusText = findViewById(R.id.activationStatus)

        scanButton.setOnClickListener { scanQrCode() }
        activateButton.setOnClickListener {
            activateWithCredential(
                ActivationCredential(
                    key = keyInput.text?.toString().orEmpty(),
                    serverBaseUrl = null
                )
            )
        }

        val deepLinkActivation =
            extractActivationCredential(intent?.data?.toString().orEmpty())
        if (deepLinkActivation != null) {
            keyInput.setText(deepLinkActivation.key)
            activateWithCredential(deepLinkActivation)
            return
        }

        val existingToken = tokenStore.load()
        if (!existingToken.isNullOrBlank()) {
            val invitation = pendingInvitation
            if (invitation != null) {
                redeemInvitation(existingToken, invitation)
            } else {
                openMainApp()
            }
            return
        }

        if (pendingInvitation != null) {
            setStatus(getString(R.string.collab_invite_activation_required))
        }
    }

    private fun scanQrCode() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(this, options)

        setStatus(getString(R.string.activation_scanner_starting))
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue.orEmpty()
                val credential = extractActivationCredential(raw)
                if (credential == null) {
                    setStatus(getString(R.string.activation_invalid_qr), isError = true)
                } else {
                    keyInput.setText(credential.key)
                    activateWithCredential(credential)
                }
            }
            .addOnCanceledListener {
                setStatus("")
            }
            .addOnFailureListener {
                setStatus(getString(R.string.activation_scanner_error), isError = true)
            }
    }

    private fun activateWithCredential(credential: ActivationCredential) {
        val key = credential.key.trim()
        if (key.length < 16) {
            setStatus(getString(R.string.activation_key_too_short), isError = true)
            return
        }

        setBusy(true)
        setStatus(getString(R.string.activation_connecting))

        lifecycleScope.launch {
            val activationBaseUrl = credential.serverBaseUrl ?: ServerConfig.BASE_URL
            val activationHost = runCatching { Uri.parse(activationBaseUrl).host }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: activationBaseUrl
            setStatus(getString(R.string.activation_connecting_server, activationHost))
            val result = withContext(Dispatchers.IO) {
                ActivationApi(activationBaseUrl).activate(
                    key = key,
                    deviceUid = DeviceIdentity.stableUid(this@AktivierungActivity),
                    deviceName = DeviceIdentity.deviceName(),
                    osVersion = DeviceIdentity.osVersion(),
                    appVersion = DeviceIdentity.appVersion(this@AktivierungActivity)
                )
            }

            when (result) {
                is ActivationApi.LoginResult.Success -> {
                    val stored = runCatching { tokenStore.save(result.token) }.isSuccess
                    if (!stored) {
                        setBusy(false)
                        setStatus(getString(R.string.activation_token_save_error), isError = true)
                        return@launch
                    }

                    credential.serverBaseUrl?.let { serverBaseUrl ->
                        ServerConfig.rememberActivatedServer(
                            applicationContext,
                            serverBaseUrl
                        )
                    }

                    // Nur nicht geheime Anzeigedaten cachen. Technische Identität und Rechte bleiben
                    // weiterhin ausschließlich beim Server-Token und den Server-UUIDs.
                    UserDisplayCache.rememberName(applicationContext, result.userName)

                    // Firmenstammdaten/Logo sofort nach erfolgreicher Aktivierung lokal cachen.
                    // Ein Profilfehler blockiert die Aktivierung nicht: Offline-Betrieb und der
                    // persönliche Geräte-Token bleiben gültig, späterer Baustellen-Sync versucht erneut.
                    val profileResult = withContext(Dispatchers.IO) {
                        CompanyProfileSync.synchronize(
                            applicationContext,
                            result.token,
                            TeamManagementApi()
                        )
                    }
                    if (profileResult is CompanyProfileSync.Result.Error) {
                        Toast.makeText(
                            this@AktivierungActivity,
                            getString(R.string.collab_company_profile_error, profileResult.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    Toast.makeText(
                        this@AktivierungActivity,
                        getString(R.string.activation_success, result.userName ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                    val invitation = pendingInvitation
                    if (invitation != null) {
                        redeemInvitation(result.token, invitation)
                    } else {
                        openMainApp()
                    }
                }

                ActivationApi.LoginResult.AlreadyBound -> {
                    setBusy(false)
                    setStatus(getString(R.string.activation_already_bound), isError = true)
                }

                ActivationApi.LoginResult.InvalidKey -> {
                    setBusy(false)
                    setStatus(
                        getString(R.string.activation_invalid_key_server, activationHost),
                        isError = true
                    )
                }

                ActivationApi.LoginResult.UserDisabled -> {
                    setBusy(false)
                    setStatus(getString(R.string.activation_user_disabled), isError = true)
                }

                ActivationApi.LoginResult.DeviceInUse -> {
                    setBusy(false)
                    setStatus(getString(R.string.activation_device_in_use), isError = true)
                }

                is ActivationApi.LoginResult.Error -> {
                    setBusy(false)
                    setStatus(
                        getString(R.string.activation_server_error, result.message),
                        isError = true
                    )
                }
            }
        }
    }

    private fun redeemInvitation(
        token: String,
        invitation: WorksiteInvitationLink.Credential
    ) {
        setBusy(true)
        setStatus(getString(R.string.collab_redeem_loading))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (invitation) {
                    is WorksiteInvitationLink.Credential.ServerToken ->
                        TeamManagementApi().redeemServerInvitation(token, invitation.value)
                    is WorksiteInvitationLink.Credential.LegacyCode ->
                        TeamManagementApi().redeemInvitation(token, invitation.value)
                }
            }
            when (result) {
                is TeamManagementApi.RedeemResult.Success -> {
                    pendingInvitation = null
                    Toast.makeText(
                        this@AktivierungActivity,
                        R.string.collab_redeem_success,
                        Toast.LENGTH_LONG
                    ).show()
                    openMainApp()
                }
                TeamManagementApi.RedeemResult.Unsupported -> {
                    Toast.makeText(
                        this@AktivierungActivity,
                        R.string.collab_team_unavailable,
                        Toast.LENGTH_LONG
                    ).show()
                    openMainApp()
                }
                TeamManagementApi.RedeemResult.SessionInvalid -> {
                    tokenStore.clear()
                    setBusy(false)
                    setStatus(getString(R.string.collab_session_invalid), isError = true)
                }
                is TeamManagementApi.RedeemResult.Error -> {
                    Toast.makeText(
                        this@AktivierungActivity,
                        getString(R.string.collab_invite_error, result.message),
                        Toast.LENGTH_LONG
                    ).show()
                    openMainApp()
                }
            }
        }
    }

    private data class ActivationCredential(
        val key: String,
        val serverBaseUrl: String?
    )

    private fun extractActivationCredential(raw: String): ActivationCredential? {
        val text = raw.trim()
        if (text.startsWith("TBH-", ignoreCase = true)) {
            return ActivationCredential(text.uppercase(), null)
        }

        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("tbhelfer", ignoreCase = true)) return null
        if (!uri.host.equals("activate", ignoreCase = true)) return null

        val key = uri.getQueryParameter("key")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val rawServer = uri.getQueryParameter("server")?.trim()?.takeIf { it.isNotBlank() }
        val server = if (rawServer == null) {
            null
        } else {
            runCatching { ServerConfig.activationServer(rawServer) }.getOrNull()
                ?: return null
        }
        return ActivationCredential(key, server)
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        scanButton.isEnabled = !busy
        activateButton.isEnabled = !busy
        keyInput.isEnabled = !busy
    }

    private fun setStatus(message: String, isError: Boolean = false) {
        statusText.text = message
        statusText.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        statusText.setTextColor(
            getColor(if (isError) android.R.color.holo_red_dark else android.R.color.darker_gray)
        )
    }

    private fun openMainApp() {
        startActivity(Intent(this, HauptActivity::class.java))
        finish()
    }
}
