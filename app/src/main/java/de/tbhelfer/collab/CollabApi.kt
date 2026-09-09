package de.tbhelfer.collab

import de.tbhelfer.server.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class CollabApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Worksite(
        val id: String,
        val name: String,
        val designation: String?,
        val worksiteType: String?,
        val status: String?,
        val companyId: Long?,
        val groupId: String,
        val groupLabel: String?,
        val leadUserId: String?,
        val leadName: String?,
        val memberRole: String,
        val areaScopeJson: String
    )

    data class ServerChange(
        val sequenceNo: Long,
        val entityType: String,
        val entityId: String,
        val operation: String,
        val entityVersion: Long,
        val groupId: String?,
        val areaScopeJson: String,
        val dataJson: String
    )

    data class ChangePage(
        val nextSequenceNo: Long,
        val latestSequenceNo: Long,
        val hasMore: Boolean,
        val changes: List<ServerChange>
    )

    data class MutationRequest(
        val clientChangeId: String,
        val worksiteId: String,
        val entityType: String,
        val operation: String,
        val entityId: String?,
        val clientEntityKey: String,
        val baseVersion: Long?,
        val groupId: String?,
        val status: String?,
        val areaScopeJson: String,
        val payloadJson: String
    )

    data class MutationApplied(
        val entityId: String?,
        val entityVersion: Long?,
        val sequenceNo: Long?,
        val serverEntityJson: String?
    )

    sealed interface Result<out T> {
        data class Success<T>(val value: T) : Result<T>
        data object Unsupported : Result<Nothing>
        data object SessionInvalid : Result<Nothing>
        data class Error(val message: String) : Result<Nothing>
    }

    sealed interface MutationResult {
        data class Applied(val value: MutationApplied) : MutationResult
        data class Conflict(
            val detail: String?,
            val entityId: String?,
            val serverVersion: Long?,
            val serverEntityJson: String?
        ) : MutationResult
        data object Unsupported : MutationResult
        data object SessionInvalid : MutationResult
        data class Error(val message: String) : MutationResult
    }

    fun loadContext(token: String): Result<List<Worksite>> {
        val connection = open("/api/v1/collab/context", "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = JSONObject(body)
                    val array = root.optJSONArray("worksites") ?: JSONArray()
                    val items = buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val id = item.optString("worksite_id")
                            val groupId = item.optString("group_id")
                            if (id.isBlank() || groupId.isBlank()) continue
                            add(
                                Worksite(
                                    id = id,
                                    name = item.optString("name").ifBlank { item.optString("designation").ifBlank { "Baustelle" } },
                                    designation = item.optNullableString("designation"),
                                    worksiteType = item.optNullableString("worksite_type"),
                                    status = item.optNullableString("status"),
                                    companyId = item.optNullableLong("company_id"),
                                    groupId = groupId,
                                    groupLabel = item.optNullableString("group_label"),
                                    leadUserId = item.optNullableString("lead_user_id"),
                                    leadName = item.optNullableString("lead_name_snapshot"),
                                    memberRole = item.optString("member_role").ifBlank { "HELPER" },
                                    areaScopeJson = item.optJsonText("area_scope") ?: "{}"
                                )
                            )
                        }
                    }
                    Result.Success(items)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> Result.SessionInvalid
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    fun loadChanges(token: String, worksiteId: String, after: Long, limit: Int = 100): Result<ChangePage> {
        val path = "/api/v1/collab/changes?worksite_id=${encode(worksiteId)}&after=${after.coerceAtLeast(0)}&limit=${limit.coerceIn(1, 250)}"
        val connection = open(path, "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = JSONObject(body)
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
                                    entityVersion = item.optLong("entity_version", 0),
                                    groupId = item.optNullableString("group_id"),
                                    areaScopeJson = item.optJsonText("area_scope") ?: "{}",
                                    dataJson = item.optJsonText("data") ?: "{}"
                                )
                            )
                        }
                    }
                    Result.Success(
                        ChangePage(
                            nextSequenceNo = root.optLong("next_sequence_no", after),
                            latestSequenceNo = root.optLong("latest_sequence_no", after),
                            hasMore = root.optBoolean("has_more", false),
                            changes = changes
                        )
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> Result.SessionInvalid
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    fun acknowledge(token: String, worksiteId: String, sequenceNo: Long): Result<Unit> {
        val connection = open("/api/v1/collab/ack", "POST")
        return try {
            authorize(connection, token)
            writeJson(
                connection,
                JSONObject()
                    .put("worksite_id", worksiteId)
                    .put("sequence_no", sequenceNo.coerceAtLeast(0))
            )
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> Result.Success(Unit)
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> Result.SessionInvalid
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    fun mutate(token: String, mutation: MutationRequest): MutationResult {
        val connection = open("/api/v1/collab/mutations", "POST")
        return try {
            authorize(connection, token)
            val request = JSONObject()
                .put("client_change_id", mutation.clientChangeId)
                .put("worksite_id", mutation.worksiteId)
                .put("entity_type", mutation.entityType)
                .put("operation", mutation.operation)
                .put("client_entity_key", mutation.clientEntityKey)
                .put("area_scope", parseObject(mutation.areaScopeJson))
                .put("payload", parseObject(mutation.payloadJson))
            mutation.entityId?.let { request.put("entity_id", it) }
            mutation.baseVersion?.let { request.put("base_version", it) }
            mutation.groupId?.let { request.put("group_id", it) }
            mutation.status?.let { request.put("status", it) }
            writeJson(connection, request)

            val code = connection.responseCode
            val body = readBody(connection, code)
            val root = body.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            when (code) {
                HttpURLConnection.HTTP_OK -> MutationResult.Applied(
                    MutationApplied(
                        entityId = root?.optNullableString("entity_id"),
                        entityVersion = root?.optNullableLong("entity_version"),
                        sequenceNo = root?.optNullableLong("sequence_no"),
                        serverEntityJson = root?.optJsonText("server_entity")
                    )
                )
                HttpURLConnection.HTTP_CONFLICT -> MutationResult.Conflict(
                    detail = root?.optNullableString("detail"),
                    entityId = root?.optNullableString("entity_id"),
                    serverVersion = root?.optNullableLong("server_version"),
                    serverEntityJson = root?.optJsonText("server_entity")
                )
                HttpURLConnection.HTTP_NOT_FOUND -> MutationResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> MutationResult.SessionInvalid
                else -> MutationResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            MutationResult.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 25_000
            useCaches = false
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun writeJson(connection: HttpURLConnection, value: JSONObject) {
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(value.toString()) }
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) connection.inputStream else connection.errorStream ?: return ""
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader -> reader.readText() }
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return detail.ifBlank { "Serverfehler ($code)." }
    }

    private fun parseObject(value: String): JSONObject = runCatching { JSONObject(value) }.getOrElse { JSONObject() }
    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

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
}
