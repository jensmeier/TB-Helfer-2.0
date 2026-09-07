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
import de.tbhelfer.server.DeviceIdentity
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.ui.HauptActivity
import de.tbhelfer.ui.KeyboardAutoScroll
import de.tbhelfer.ui.SafeAreaInsets
import de.tbhelfer.ui.style.UiStyleApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AktivierungActivity : AppCompatActivity() {
    private lateinit var keyInput: EditText
    private lateinit var scanButton: Button
    private lateinit var activateButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView

    private lateinit var tokenStore: SecureTokenStore
    private val api = ActivationApi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenStore = SecureTokenStore(this)
        if (!tokenStore.load().isNullOrBlank()) {
            openMainApp()
            return
        }

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
            activateWithKey(keyInput.text?.toString().orEmpty())
        }

        extractActivationKey(intent?.data?.toString().orEmpty())?.let { deepLinkKey ->
            keyInput.setText(deepLinkKey)
            activateWithKey(deepLinkKey)
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
                val key = extractActivationKey(raw)
                if (key == null) {
                    setStatus(getString(R.string.activation_invalid_qr), isError = true)
                } else {
                    keyInput.setText(key)
                    activateWithKey(key)
                }
            }
            .addOnCanceledListener {
                setStatus("")
            }
            .addOnFailureListener {
                setStatus(getString(R.string.activation_scanner_error), isError = true)
            }
    }

    private fun activateWithKey(rawKey: String) {
        val key = rawKey.trim()
        if (key.length < 16) {
            setStatus(getString(R.string.activation_key_too_short), isError = true)
            return
        }

        setBusy(true)
        setStatus(getString(R.string.activation_connecting))

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                api.activate(
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
                    Toast.makeText(
                        this@AktivierungActivity,
                        getString(R.string.activation_success, result.userName ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                    openMainApp()
                }

                ActivationApi.LoginResult.AlreadyBound -> {
                    setBusy(false)
                    setStatus(getString(R.string.activation_already_bound), isError = true)
                }

                ActivationApi.LoginResult.InvalidKey -> {
                    setBusy(false)
                    setStatus(getString(R.string.activation_invalid_key), isError = true)
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

    private fun extractActivationKey(raw: String): String? {
        val text = raw.trim()
        if (text.startsWith("TBH-", ignoreCase = true)) return text.uppercase()

        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("tbhelfer", ignoreCase = true)) return null
        if (!uri.host.equals("activate", ignoreCase = true)) return null
        return uri.getQueryParameter("key")?.trim()?.takeIf { it.isNotBlank() }
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
