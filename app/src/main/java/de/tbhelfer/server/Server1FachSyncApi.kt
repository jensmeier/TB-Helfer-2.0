package de.tbhelfer.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Read-only bridge for the first Android Server-1.0 Fachsync step.
 *
 * Defects and acceptances are loaded from the canonical Server-1.0 resources.
 * Upload/update/delete remain deliberately outside this client until their
 * idempotent mutation mapping is migrated as a separate step.
 */
class Server1FachSyncApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Snapshot(
        val serverId: String,
        val clientEntityKey: String,
        val status: String?,
        val areaScopeJson: String,
        val payloadJson: String,
        val entityVersion: Long,
        val createdAtText: String?,
        val updatedAtText: String?
    )

    sealed interface Result {
        data class Success(val items: List<Snapshot>) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun loadDefects(token: String, worksiteId: String): Result =
        loadCollection(token, "/api/v1/worksites/${encode(worksiteId)}/defects", "defects", EntityKind.DEFECT)

    fun loadAcceptances(token: String, worksiteId: String): Result =
        loadCollection(token, "/api/v1/worksites/${encode(worksiteId)}/acceptances", "acceptances", EntityKind.ACCEPTANCE)

    private fun loadCollection(
        token: String,
        path: String,
        arrayKey: String,
        kind: EntityKind
    ): Result {
        val connection = openConnection(path)
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return Result.Error("Serverantwort konnte nicht gelesen werden.")
                    val array = root.optJSONArray(arrayKey) ?: JSONArray()
                    val items = buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            parseSnapshot(item, kind)?.let(::add)
                        }
                    }
                    Result.Success(items)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> Result.SessionInvalid
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server-1.0-Fachdaten konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSnapshot(item: JSONObject, kind: EntityKind): Snapshot? {
        val serverId = item.optString("id").trim()
        if (serverId.isBlank()) return null
        val payload = item.optJSONObject("payload") ?: JSONObject()
        val key = when (kind) {
            EntityKind.DEFECT -> payload.optString("key").trim()
            EntityKind.ACCEPTANCE -> payload.optString("id").trim()
        }.ifBlank { serverId }

        val status = when (kind) {
            EntityKind.DEFECT -> when (item.optString("status").uppercase()) {
                "OPEN" -> "OFFEN"
                "IN_PROGRESS" -> "IN_ARBEIT"
                "CLOSED" -> "ERLEDIGT"
                else -> item.optString("status").trim().takeIf { it.isNotBlank() }
            }
            EntityKind.ACCEPTANCE -> item.optString("result").trim().takeIf { it.isNotBlank() }
        }

        if (kind == EntityKind.ACCEPTANCE && status != null && !payload.has("ergebnis")) {
            payload.put("ergebnis", status)
        }

        return Snapshot(
            serverId = serverId,
            clientEntityKey = key,
            status = status,
            areaScopeJson = item.opt("area_scope")?.toString() ?: "{}",
            payloadJson = payload.toString(),
            entityVersion = item.optLong("version", 1L).coerceAtLeast(1L),
            createdAtText = item.nullableString("created_at"),
            updatedAtText = item.nullableString("updated_at")
        )
    }

    private fun openConnection(path: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
        }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: return ""
        }
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val raw = runCatching { JSONObject(body).opt("detail") }.getOrNull()
        val detail = when (raw) {
            is String -> raw
            is JSONObject -> raw.optString("code").ifBlank { raw.toString() }
            else -> raw?.toString().orEmpty()
        }
        return detail.ifBlank { "Serverfehler ($code)." }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() }
    }

    private enum class EntityKind { DEFECT, ACCEPTANCE }
}
