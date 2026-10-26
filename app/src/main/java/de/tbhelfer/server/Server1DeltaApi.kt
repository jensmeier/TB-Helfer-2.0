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
 * Gefilterter Server-1.0-Changefeed fuer automatische Android-Deltas.
 *
 * Der Cursor ist global monoton. Android speichert nextAfter erst, nachdem alle aus der Antwort
 * abgeleiteten Fachobjekte erfolgreich lokal aktualisiert wurden.
 */
class Server1DeltaApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Change(
        val changeId: Long,
        val worksiteId: String?,
        val entityType: String,
        val entityId: String,
        val operation: String,
        val entityVersion: Long?
    )

    data class Page(
        val changes: List<Change>,
        val nextAfter: Long,
        val head: Long,
        val caughtUp: Boolean
    )

    sealed interface Result {
        data class Success(val page: Page) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun waitForChanges(
        token: String,
        after: Long,
        waitSeconds: Int,
        limit: Int = 200,
        entityTypes: Set<String> = DEFAULT_ENTITY_TYPES
    ): Result {
        val types = entityTypes
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")
        val path = buildString {
            append("/api/v1/sync/changes/wait?after=")
            append(after.coerceAtLeast(0L))
            append("&limit=")
            append(limit.coerceIn(1, 500))
            append("&wait_seconds=")
            append(waitSeconds.coerceIn(0, 30))
            if (types.isNotBlank()) {
                append("&entity_types=")
                append(encode(types))
            }
        }
        val connection = (URL(baseUrl.trimEnd('/') + path).let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = (waitSeconds.coerceIn(0, 30) + 15) * 1000
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parsePage(body)
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> Result.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) Result.SessionInvalid
                    else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server-Delta konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePage(body: String): Result {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return Result.Error("Server-Delta konnte nicht gelesen werden.")
        val changesJson = root.optJSONArray("changes") ?: JSONArray()
        val changes = (0 until changesJson.length()).mapNotNull { index ->
            val item = changesJson.optJSONObject(index) ?: return@mapNotNull null
            val type = item.optString("entity_type").trim().uppercase()
            val entityId = item.optString("entity_id").trim()
            if (type.isBlank() || entityId.isBlank()) return@mapNotNull null
            Change(
                changeId = item.optLong("change_id", 0L),
                worksiteId = item.optString("worksite_id").trim().takeIf { it.isNotBlank() },
                entityType = type,
                entityId = entityId,
                operation = item.optString("operation").trim().uppercase(),
                entityVersion = item.optLong("entity_version", Long.MIN_VALUE)
                    .takeUnless { it == Long.MIN_VALUE }
            )
        }
        val nextAfter = root.optLong("next_after", -1L)
        val head = root.optLong("head", -1L)
        if (nextAfter < 0L || head < 0L) {
            return Result.Error("Server-Delta enthält keinen gültigen Cursor.")
        }
        return Result.Success(
            Page(
                changes = changes,
                nextAfter = nextAfter,
                head = head,
                caughtUp = root.optBoolean("caught_up", nextAfter >= head)
            )
        )
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

    private fun errorMessage(body: String, code: Int): String =
        detailMessage(body).ifBlank { "Serverfehler ($code)." }

    private fun detailMessage(body: String): String {
        val raw = runCatching { JSONObject(body).opt("detail") }.getOrNull()
        return when (raw) {
            is String -> raw.trim()
            is JSONObject -> raw.optString("code").trim().ifBlank { raw.toString() }
            else -> raw?.toString()?.trim().orEmpty()
        }
    }

    private fun isInvalidDevice(body: String): Boolean =
        detailMessage(body) in setOf("DEVICE_REVOKED", "USER_DISABLED", "USER_EXPIRED")

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    companion object {
        val DEFAULT_ENTITY_TYPES = setOf("BTB", "BTB_ENTRY", "BTB_PARTICIPANT", "FILE", "WORKSITE_NODE", "WORKSITE", "WORKSITE_MEMBERSHIP", "WORKSITE_INVITATION")
    }
}



