package de.tbhelfer.server

import de.tbhelfer.datenbank.SyncPendingChangeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transport für den inkrementellen NAS-Sync ab Server 0.6.1.
 *
 * Ältere Server ohne die neuen Endpunkte werden bewusst als Unsupported behandelt,
 * damit die bereits getesteten Katalog-/Offline-Funktionen weiter funktionieren.
 */
class SyncChangeApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class ServerChange(
        val sequenceNo: Long,
        val entityType: String,
        val entityId: String,
        val operation: String,
        val entityVersion: Long?,
        val changedAt: String?,
        val dataJson: String
    )

    data class ChangePage(
        val nextSequenceNo: Long,
        val latestSequenceNo: Long,
        val hasMore: Boolean,
        val changes: List<ServerChange>
    )

    sealed interface PullResult {
        data class Success(val page: ChangePage) : PullResult
        data object Unsupported : PullResult
        data object SessionInvalid : PullResult
        data class Error(val message: String) : PullResult
    }

    sealed interface AckResult {
        data object Success : AckResult
        data object Unsupported : AckResult
        data object SessionInvalid : AckResult
        data class Error(val message: String) : AckResult
    }

    sealed interface MutationResult {
        data class Applied(
            val entityId: String?,
            val entityVersion: Long?,
            val sequenceNo: Long?,
            val serverEntityJson: String?
        ) : MutationResult

        data class Conflict(
            val detail: String?,
            val serverVersion: Long?,
            val serverEntityJson: String?
        ) : MutationResult

        data object Unsupported : MutationResult
        data object SessionInvalid : MutationResult
        data class Error(val message: String) : MutationResult
    }

    fun loadChanges(token: String, after: Long, limit: Int = 100): PullResult {
        val safeLimit = limit.coerceIn(1, 500)
        val connection = openConnection("/api/v1/catalog/changes?after=${after.coerceAtLeast(0)}&limit=$safeLimit", "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return PullResult.Error("Serverantwort konnte nicht gelesen werden.")
                    val array = root.optJSONArray("changes") ?: JSONArray()
                    val changes = buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val entityId = item.optString("entity_id")
                            if (entityId.isBlank()) continue
                            add(
                                ServerChange(
                                    sequenceNo = item.optLong("sequence_no", 0),
                                    entityType = item.optString("entity_type"),
                                    entityId = entityId,
                                    operation = item.optString("operation"),
                                    entityVersion = item.optNullableLong("entity_version"),
                                    changedAt = item.optNullableString("changed_at"),
                                    dataJson = item.optJsonText("data") ?: "{}"
                                )
                            )
                        }
                    }
                    PullResult.Success(
                        ChangePage(
                            nextSequenceNo = root.optLong("next_sequence_no", after),
                            latestSequenceNo = root.optLong("latest_sequence_no", after),
                            hasMore = root.optBoolean("has_more", false),
                            changes = changes
                        )
                    )
                }

                HttpURLConnection.HTTP_NOT_FOUND -> PullResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        PullResult.SessionInvalid else PullResult.Error(errorMessage(body, code))
                else -> PullResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            PullResult.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    fun acknowledge(token: String, sequenceNo: Long): AckResult {
        val connection = openConnection("/api/v1/catalog/ack", "POST")
        return try {
            authorize(connection, token)
            writeJson(connection, JSONObject().put("sequence_no", sequenceNo.coerceAtLeast(0)))
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> AckResult.Success
                HttpURLConnection.HTTP_NOT_FOUND -> AckResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        AckResult.SessionInvalid else AckResult.Error(errorMessage(body, code))
                else -> AckResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            AckResult.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    fun uploadMutation(token: String, change: SyncPendingChangeEntity): MutationResult {
        val connection = openConnection("/api/v1/catalog/mutations", "POST")
        return try {
            authorize(connection, token)
            val payload = runCatching { JSONObject(change.payloadJson) }.getOrElse { JSONObject() }
            val clientChangeId = "android-${change.id}-${change.createdAtEpochMs}"
            val request = JSONObject()
                .put("client_change_id", clientChangeId)
                .put("entity_type", change.objectKind)
                .put("operation", change.operation)
                .put("payload", payload)
            if (!change.serverId.isNullOrBlank()) request.put("entity_id", change.serverId)
            if (change.baseVersion != null) request.put("base_version", change.baseVersion)
            writeJson(connection, request)

            val code = connection.responseCode
            val body = readBody(connection, code)
            val root = body.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            when (code) {
                HttpURLConnection.HTTP_OK -> MutationResult.Applied(
                    entityId = root?.optNullableString("entity_id"),
                    entityVersion = root?.optNullableLong("entity_version"),
                    sequenceNo = root?.optNullableLong("sequence_no"),
                    serverEntityJson = root?.optJsonText("server_entity")
                )

                HttpURLConnection.HTTP_CONFLICT -> MutationResult.Conflict(
                    detail = root?.optNullableString("detail"),
                    serverVersion = root?.optNullableLong("server_version"),
                    serverEntityJson = root?.optJsonText("server_entity")
                )

                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = root?.optNullableString("detail")
                    if (detail.isNullOrBlank() || detail.equals("Not Found", ignoreCase = true)) {
                        MutationResult.Unsupported
                    } else {
                        MutationResult.Error(detail)
                    }
                }

                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        MutationResult.SessionInvalid else MutationResult.Error(errorMessage(body, code))
                else -> MutationResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            MutationResult.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun openConnection(path: String, method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 25_000
            useCaches = false
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

    private fun writeJson(connection: HttpURLConnection, json: JSONObject) {
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(json.toString())
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return detail.ifBlank { "Serverfehler ($code)." }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableLong(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getLong(name) }.getOrNull()
    }

    private fun JSONObject.optJsonText(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is JSONObject, is JSONArray -> value.toString()
            is String -> value.takeIf { it.isNotBlank() }
            else -> value?.toString()?.takeIf { it.isNotBlank() }
        }
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
}

