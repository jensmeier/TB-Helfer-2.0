package de.tbhelfer.server

import de.tbhelfer.datenbank.SyncCollabPendingChangeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime

class WorksiteNodeApi(private val baseUrl: String = ServerConfig.BASE_URL) {
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

    sealed interface MutationResult {
        data class Applied(val node: Node) : MutationResult
        data class Deleted(val serverId: String, val currentVersion: Long) : MutationResult
        data class Conflict(val currentVersion: Long?, val detail: String) : MutationResult
        data object Unsupported : MutationResult
        data object SessionInvalid : MutationResult
        data class Error(val message: String) : MutationResult
    }

    fun load(token: String, worksiteId: String): Result {
        val connection = openConnection(
            "/api/v1/worksites/${encode(worksiteId)}/nodes?include_deleted=true",
            "GET"
        )
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parse(body)
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> Result.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) Result.SessionInvalid
                    else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Baustellenstruktur konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun applyPending(token: String, change: SyncCollabPendingChangeEntity): MutationResult {
        if (!change.entityType.equals(SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE, true)) {
            return MutationResult.Error("Nicht unterstützter Baustellenstruktur-Typ: ${change.entityType}")
        }
        return when {
            change.operation.equals(SyncCollabPendingChangeEntity.OP_DELETE, true) ->
                deletePending(token, change)
            change.operation.equals(SyncCollabPendingChangeEntity.OP_UPSERT, true) ->
                upsertPending(token, change)
            else -> MutationResult.Error("Nicht unterstützte Struktur-Operation: ${change.operation}")
        }
    }

    private fun upsertPending(token: String, change: SyncCollabPendingChangeEntity): MutationResult {
        val payload = runCatching { JSONObject(change.payloadJson) }.getOrNull()
            ?: return MutationResult.Error("Lokaler Baustellenknoten ist ungültig.")
        val clientKey = change.clientEntityKey?.trim().orEmpty()
        if (clientKey.isBlank()) return MutationResult.Error("Stabiler Baustellenknoten-Key fehlt.")
        val nodeType = payload.optString("node_type").trim()
        if (nodeType.isBlank()) return MutationResult.Error("Baustellenknoten-Typ fehlt.")

        val parentId = when (
            val parent = resolveParentId(
                token, change.worksiteServerId, payload.nullableString("parent_client_node_key")
            )
        ) {
            is ParentResult.Success -> parent.serverId
            ParentResult.Unsupported -> return MutationResult.Unsupported
            ParentResult.SessionInvalid -> return MutationResult.SessionInvalid
            is ParentResult.Error -> return MutationResult.Error(parent.message)
        }

        val serverId = change.serverId?.trim()?.takeIf { it.isNotBlank() }
        val request = JSONObject()
            .put("parent_id", parentId ?: JSONObject.NULL)
            .put("node_kind", nodeType)
            .put("name", displayName(payload, nodeType))
            .put("route_number", payload.nullableValue("route_key"))
            .put("track_key", payload.nullableValue("track_or_switch"))
            .put("payload", payload)

        val mutation = if (serverId == null) {
            request.put("external_key", clientKey).put("sort_order", 0)
            sendMutation(
                token, "/api/v1/worksites/${encode(change.worksiteServerId)}/nodes",
                "POST", change.clientChangeId, request
            )
        } else {
            val baseVersion = change.baseVersion
                ?: return MutationResult.Error("Server-Version für Baustellenknoten fehlt.")
            request.put("base_version", baseVersion)
            sendMutation(
                token,
                "/api/v1/worksites/${encode(change.worksiteServerId)}/nodes/${encode(serverId)}",
                "PATCH", change.clientChangeId, request
            )
        }

        val resultingId = when (mutation) {
            MutationResponse.Unsupported -> return MutationResult.Unsupported
            MutationResponse.SessionInvalid -> return MutationResult.SessionInvalid
            is MutationResponse.Conflict ->
                return MutationResult.Conflict(mutation.currentVersion, mutation.detail)
            is MutationResponse.Error -> return MutationResult.Error(mutation.message)
            is MutationResponse.Success ->
                mutation.body.optString("id").trim().ifBlank { serverId.orEmpty() }
        }
        return reloadNode(
            token, change.worksiteServerId,
            resultingId.takeIf { it.isNotBlank() }, clientKey
        )
    }

    private fun deletePending(token: String, change: SyncCollabPendingChangeEntity): MutationResult {
        val serverId = change.serverId?.trim().orEmpty()
        if (serverId.isBlank()) return MutationResult.Error("Server-ID für Baustellenknoten-Löschung fehlt.")
        val baseVersion = change.baseVersion
            ?: return MutationResult.Error("Server-Version für Baustellenknoten-Löschung fehlt.")
        return when (
            val result = sendMutation(
                token,
                "/api/v1/worksites/${encode(change.worksiteServerId)}/nodes/${encode(serverId)}?base_version=$baseVersion",
                "DELETE", change.clientChangeId, null
            )
        ) {
            MutationResponse.Unsupported -> MutationResult.Deleted(serverId, baseVersion + 1L)
            MutationResponse.SessionInvalid -> MutationResult.SessionInvalid
            is MutationResponse.Conflict ->
                MutationResult.Conflict(result.currentVersion, result.detail)
            is MutationResponse.Error ->
                if (result.message == "NODE_NOT_FOUND") {
                    MutationResult.Deleted(serverId, baseVersion + 1L)
                } else MutationResult.Error(result.message)
            is MutationResponse.Success -> MutationResult.Deleted(serverId, baseVersion + 1L)
        }
    }

    private sealed interface ParentResult {
        data class Success(val serverId: String?) : ParentResult
        data object Unsupported : ParentResult
        data object SessionInvalid : ParentResult
        data class Error(val message: String) : ParentResult
    }

    private fun resolveParentId(
        token: String, worksiteId: String, parentClientKey: String?
    ): ParentResult {
        if (parentClientKey == null) return ParentResult.Success(null)
        return when (val snapshot = load(token, worksiteId)) {
            Result.Unsupported -> ParentResult.Unsupported
            Result.SessionInvalid -> ParentResult.SessionInvalid
            is Result.Error -> ParentResult.Error(snapshot.message)
            is Result.Success -> {
                val parent = snapshot.nodes.firstOrNull {
                    !it.isDeleted && it.clientNodeKey == parentClientKey
                } ?: return ParentResult.Error(
                    "Übergeordneter Baustellenknoten ist noch nicht auf dem Server vorhanden."
                )
                ParentResult.Success(parent.serverId)
            }
        }
    }

    private fun reloadNode(
        token: String, worksiteId: String, serverId: String?, clientKey: String
    ): MutationResult {
        return when (val snapshot = load(token, worksiteId)) {
            Result.Unsupported -> MutationResult.Unsupported
            Result.SessionInvalid -> MutationResult.SessionInvalid
            is Result.Error -> MutationResult.Error(snapshot.message)
            is Result.Success -> {
                val node = snapshot.nodes.firstOrNull {
                    (!serverId.isNullOrBlank() && it.serverId == serverId) ||
                        it.clientNodeKey == clientKey
                } ?: return MutationResult.Error(
                    "Bestätigter Baustellenknoten fehlt im Server-Snapshot."
                )
                MutationResult.Applied(node)
            }
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
                if (serverId.isBlank()) continue
                val key = item.optString("client_node_key").trim()
                    .ifBlank { item.optString("external_key").trim() }
                    .ifBlank { "server-$serverId" }
                val payload = item.optJSONObject("payload")
                    ?.let { JSONObject(it.toString()) } ?: JSONObject()
                payload.put("parent_client_node_key", item.nullableValue("parent_client_node_key"))
                payload.put("node_type", item.optString("node_kind"))
                if (!payload.has("node_name")) payload.put("node_name", item.nullableValue("name"))
                if (!payload.has("route_key")) payload.put("route_key", item.nullableValue("route_number"))
                if (!payload.has("track_or_switch")) {
                    payload.put("track_or_switch", item.nullableValue("track_key"))
                }
                if (!payload.has("created_at_epoch_ms")) {
                    epochMillis(item.nullableString("created_at"))
                        ?.let { payload.put("created_at_epoch_ms", it) }
                }
                val deleted = item.optBoolean("is_deleted", false)
                add(
                    Node(
                        serverId, key, if (deleted) "DELETED" else "ACTIVE",
                        payload.toString(),
                        item.optLong("current_version", item.optLong("version", 1L))
                            .coerceAtLeast(1L),
                        deleted
                    )
                )
            }
        }
        return Result.Success(nodes)
    }

    private sealed interface MutationResponse {
        data class Success(val body: JSONObject) : MutationResponse
        data class Conflict(val currentVersion: Long?, val detail: String) : MutationResponse
        data object Unsupported : MutationResponse
        data object SessionInvalid : MutationResponse
        data class Error(val message: String) : MutationResponse
    }

    private fun sendMutation(
        token: String, path: String, method: String, clientChangeId: String, body: JSONObject?
    ): MutationResponse {
        val connection = openConnection(path, method)
        return try {
            authorize(connection, token)
            connection.setRequestProperty("X-Client-Change-Id", clientChangeId)
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val response = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK,
                HttpURLConnection.HTTP_CREATED,
                HttpURLConnection.HTTP_NO_CONTENT -> MutationResponse.Success(
                    response.takeIf { it.isNotBlank() }
                        ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
                )
                HttpURLConnection.HTTP_CONFLICT -> {
                    val detail = runCatching { JSONObject(response).opt("detail") }.getOrNull()
                    val obj = detail as? JSONObject
                    MutationResponse.Conflict(
                        obj?.optLong("current_version")?.takeIf { it > 0L },
                        when (detail) {
                            is String -> detail
                            is JSONObject -> detail.optString("code").ifBlank { detail.toString() }
                            else -> "VERSION_CONFLICT"
                        }
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = detailMessage(response)
                    if (detail.isBlank() || detail.equals("Not Found", true)) {
                        MutationResponse.Unsupported
                    } else MutationResponse.Error(detail)
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> MutationResponse.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(response)) MutationResponse.SessionInvalid
                    else MutationResponse.Error(errorMessage(response, code))
                else -> MutationResponse.Error(errorMessage(response, code))
            }
        } catch (error: Exception) {
            MutationResponse.Error(
                error.message ?: "Baustellenstruktur konnte nicht zum Server übertragen werden."
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String, method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 30_000
            useCaches = false
        }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        if (code == HttpURLConnection.HTTP_NO_CONTENT) return ""
        val input: InputStream = if (code in 200..299) {
            connection.inputStream
        } else connection.errorStream ?: return ""
        return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
    }

    private fun displayName(payload: JSONObject, nodeType: String): String =
        payload.nullableString("node_name")
            ?: payload.nullableString("station_name")
            ?: payload.nullableString("route_key")
            ?: nodeType

    private fun errorMessage(body: String, code: Int): String =
        detailMessage(body).ifBlank { "Serverfehler HTTP $code" }

    private fun detailMessage(body: String): String {
        val detail = runCatching { JSONObject(body).opt("detail") }.getOrNull()
        return when (detail) {
            is String -> detail.trim()
            is JSONObject -> detail.optString("code").trim().ifBlank { detail.toString() }
            else -> detail?.toString()?.trim().orEmpty()
        }
    }

    private fun isInvalidDevice(body: String): Boolean =
        detailMessage(body) in setOf("DEVICE_REVOKED", "USER_DISABLED", "USER_EXPIRED")

    private fun epochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun JSONObject.nullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotBlank() }
    }

    private fun JSONObject.nullableValue(name: String): Any =
        if (!has(name) || isNull(name)) JSONObject.NULL else opt(name)
}
