package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.BuildConfig
import java.net.URI

/**
 * Zentraler Server-Endpunkt.
 *
 * Nach einer QR-Geräteaktivierung wird der öffentliche HTTPS-Server lokal gespeichert und
 * von allen nachfolgend erzeugten Server-Clients verwendet. BuildConfig bleibt nur Fallback
 * für Entwickler-/Legacy-Fälle ohne bereits gespeicherte Serveridentität.
 */
object ServerConfig {
    private const val PREFS = "tbhelfer_server_config"
    private const val KEY_BASE_URL = "activated_server_base_url"

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val BASE_URL: String
        get() = storedBaseUrl() ?: BuildConfig.SERVER_BASE_URL.trim().trimEnd('/')

    fun activationServer(raw: String): String = normalizeHttpsBaseUrl(raw)

    fun rememberActivatedServer(context: Context, raw: String): String {
        val normalized = normalizeHttpsBaseUrl(raw)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, normalized)
            .apply()
        appContext = context.applicationContext
        return normalized
    }

    fun storedBaseUrl(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }

    private fun storedBaseUrl(): String? =
        appContext?.let { storedBaseUrl(it) }

    private fun normalizeHttpsBaseUrl(raw: String): String {
        val value = raw.trim().trimEnd('/')
        require(value.isNotBlank()) { "Serveradresse fehlt." }
        val uri = runCatching { URI(value) }
            .getOrElse { throw IllegalArgumentException("Serveradresse ist ungültig.") }
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Aktivierungsserver muss HTTPS verwenden."
        }
        require(!uri.host.isNullOrBlank()) { "Serveradresse enthält keinen Host." }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Serveradresse enthält unzulässige Bestandteile."
        }
        require(uri.path.isNullOrBlank() || uri.path == "/") {
            "Serveradresse darf keinen Pfad enthalten."
        }
        return URI(
            "https",
            null,
            uri.host.lowercase(),
            uri.port,
            null,
            null,
            null
        ).toString().trimEnd('/')
    }
}
