package de.tbhelfer.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Kleiner Snapshot-Endpunkt für den gemeinsamen Baustellenbaum.
 *
 * Schreiben läuft bewusst über die vorhandene Collaboration-Mutationsqueue. Dieser Transport
 * liest nur den autoritativen Serverstand inklusive Tombstones, damit lokale Room-IDs niemals
 * zwischen Geräten geraten und gelöschte Unterbaustellen nicht wieder auferstehen.
 */
class WorksiteNodeApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Node(
        val serverId: String,
        val clientNodeKey: String,
        val status: String?,
        val payloadJson: String,
        val currentVersion: Long,
        val isDeleted: Boolean
    )

    sealed interface Result {
        data class Success(val nodes: List<Node>) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun load(token: String, worksiteId: String): Result {
        val path = "/api/v1/collab/worksites/${encode(worksiteId)}/nodes"
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parse(body)
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> Result.SessionInvalid
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Baustellenstruktur konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): Result {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return Result.Error("Serverantwort der Baustellenstruktur ist ungültig.")
        val array = root.optJSONArray("nodes") ?: JSONArray()
        val nodes = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val serverId = item.optString("id").trim()
                val key = item.optString("client_node_key").trim()
                if (serverId.isBlank() || key.isBlank()) continue
                add(
                    Node(
                        serverId = serverId,
                        clientNodeKey = key,
                        status = item.optNullableString("status"),
                        payloadJson = item.opt("payload")?.toString()?.takeIf { it.isNotBlank() } ?: "{}",
                        currentVersion = item.optLong("current_version", 0L),
                        isDeleted = item.optBoolean("is_deleted", false)
                    )
                )
            }
        }
        return Result.Success(nodes)
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        if (input == null) return ""
        return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
    }

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return detail.ifBlank { "Serverfehler HTTP $code" }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }
}
