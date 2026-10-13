package de.tbhelfer.server

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * Kleiner Capability-Client für den Server-1.0-Cutover.
 *
 * Alte 0.6/0.7-Server besitzen /api/v1/capabilities nicht. In diesem Fall wird
 * ausdrücklich Unsupported geliefert, damit der aufrufende Fachclient seinen
 * dokumentierten Legacy-Fallback verwenden kann. Netzwerk-/Serverfehler werden
 * dagegen nicht als "alter Server" fehlinterpretiert.
 */
class ServerCapabilitiesApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    private val cached = AtomicReference<Snapshot?>(null)

    data class Snapshot(
        val serverVersion: String?,
        val apiVersion: String?,
        val capabilities: Map<String, Boolean>,
        val loadedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        fun supports(key: String): Boolean = capabilities[key] == true
    }

    sealed interface Result {
        data class Success(val snapshot: Snapshot) : Result
        data object Unsupported : Result
        data class Error(val message: String) : Result
    }

    fun load(forceRefresh: Boolean = false): Result {
        if (!forceRefresh) {
            cached.get()?.takeIf {
                System.currentTimeMillis() - it.loadedAtEpochMs <= CACHE_TTL_MS
            }?.let { return Result.Success(it) }
        }

        val connection = openConnection("/api/v1/capabilities")
        return try {
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return Result.Error("Server-Capabilities konnten nicht gelesen werden.")
                    val raw = root.optJSONObject("capabilities")
                        ?: return Result.Error("Server lieferte keine Capability-Liste.")
                    val values = buildMap {
                        val keys = raw.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            put(key, raw.optBoolean(key, false))
                        }
                    }
                    val snapshot = Snapshot(
                        serverVersion = root.optString("server_version")
                            .trim()
                            .takeIf { it.isNotBlank() },
                        apiVersion = root.optString("api_version")
                            .trim()
                            .takeIf { it.isNotBlank() },
                        capabilities = values
                    )
                    cached.set(snapshot)
                    Result.Success(snapshot)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server-Capabilities konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun supports(key: String): Result {
        return when (val result = load()) {
            is Result.Success -> Result.Success(
                result.snapshot.copy(
                    capabilities = mapOf(key to result.snapshot.supports(key))
                )
            )
            Result.Unsupported -> Result.Unsupported
            is Result.Error -> result
        }
    }

    private fun openConnection(path: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: return ""
        }
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                buildString {
                    var line = reader.readLine()
                    while (line != null) {
                        append(line)
                        line = reader.readLine()
                    }
                }
            }
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching { JSONObject(body).optString("detail") }
            .getOrNull()
            .orEmpty()
        return detail.ifBlank { "Serverfehler ($code)." }
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
