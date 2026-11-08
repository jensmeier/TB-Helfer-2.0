package de.tbhelfer.server.recovery

import de.tbhelfer.server.ServerConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

object RecoveryProtocol {
    const val HEADER = "X-TB-Recovery-ID"
    private val readScope = ThreadLocal<String?>()
    @Volatile var onBlocked: (() -> Unit)? = null
    private var cached: Pair<Long, String?>? = null
    private var cachedBase: String? = null
    private val probeLock = Any()

    fun owner(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest((origin(URL(ServerConfig.BASE_URL)) + "\n" + token).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun origin(url: URL): String = URI(url.protocol.lowercase(), null, url.host.lowercase(),
        if (url.port == -1) url.defaultPort else url.port, null, null, null).toString()

    fun prepare(connection: HttpURLConnection) {
        if (!connection.url.path.startsWith("/api/v1/") ||
            connection.getRequestProperty("Authorization")?.startsWith("Bearer ") != true) return
        // A client created before a new activation must not send an old request to its old server.
        if (origin(connection.url) != origin(URL(ServerConfig.BASE_URL)))
            throw IOException("Die Serverzuordnung wurde geändert. Alte Anfrage wurde nicht gesendet.")
        connection.setRequestProperty("X-TB-App-Version", de.tbhelfer.BuildConfig.VERSION_NAME)
        connection.setRequestProperty("X-TB-App-Build", de.tbhelfer.BuildConfig.VERSION_CODE.toString())
        connection.instanceFollowRedirects = false
        if (RecoveryWorkspace.switchPending()) throw IOException("Bestandswechsel vorbereitet. App-Neustart erforderlich.")
        // Never accept a recovery header supplied by an old caller.
        connection.setRequestProperty(HEADER, "")
        val observed = probe()
        val review = readScope.get()
        if (review != null) {
            if (connection.requestMethod != "GET" || connection.doOutput) throw IOException("Der Wiederherstellungsvergleich ist nur lesend.")
            if (observed != review) throw IOException("Der Serverbestand hat sich erneut geändert. Vergleich neu starten.")
            connection.setRequestProperty(HEADER, review)
            return
        }
        val token = connection.getRequestProperty("Authorization").removePrefix("Bearer ")
        val binding = owner(token)
        if (RecoveryWorkspace.blocked() || !RecoveryPolicy.permits(observed, RecoveryWorkspace.acknowledged(binding), RecoveryWorkspace.ownerMatches(binding))) {
            if (observed != null) blocked(observed)
            throw IOException("Serverwiederherstellung: Handybestand zuerst sichern und Serverbestand vergleichen.")
        }
        if (observed != null) connection.setRequestProperty(HEADER, observed)
    }

    fun observeResponse(connection: HttpURLConnection, code: Int) {
        // Request properties cannot be queried after connect() on Android.
        if (code != 409 || origin(connection.url) != origin(URL(ServerConfig.BASE_URL)) ||
            !connection.url.path.startsWith("/api/v1/")) return
        val epoch = RecoveryPolicy.epoch(connection.getHeaderField(HEADER)) ?: return
        synchronized(probeLock) { cached = null }
        blocked(epoch)
    }

    private fun blocked(epoch: String) {
        RecoveryWorkspace.block(epoch)
        onBlocked?.invoke()
    }

    /** Public endpoint, no bearer token, no automatic redirects, no call through RecoveryHttp. */
    fun probe(force: Boolean = false): String? = synchronized(probeLock) {
        val base = ServerConfig.BASE_URL.trimEnd('/')
        if (!force && cachedBase == base) cached?.takeIf { System.currentTimeMillis() - it.first in 0..10_000 }?.let { return it.second }
        val connection = URL("$base/api/v1/capabilities").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            val epoch = when (val code = connection.responseCode) {
                200 -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(body)
                    require(json.optJSONObject("capabilities") != null) { "Ungültige Serverauskunft." }
                    RecoveryPolicy.epoch(if (json.isNull("recovery_id")) null else json.getString("recovery_id"))
                }
                404 -> null // Legacy server, still rejected if this workspace previously acknowledged an epoch.
                else -> throw IOException("Serverstatus konnte nicht geprüft werden (HTTP $code).")
            }
            cachedBase = base
            cached = System.currentTimeMillis() to epoch
            epoch
        } finally { connection.disconnect() }
    }

    /** The callback is synchronous on one IO thread: no coroutine suspension inside this scope. */
    fun <T> compare(epoch: String, block: () -> T): T {
        check(readScope.get() == null)
        readScope.set(RecoveryPolicy.epoch(epoch) ?: throw IOException("Wiederherstellungskennung fehlt."))
        return try { block() } finally { readScope.remove() }
    }
}
