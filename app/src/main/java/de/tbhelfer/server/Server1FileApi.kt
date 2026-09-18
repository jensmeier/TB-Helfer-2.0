package de.tbhelfer.server

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Read side of the canonical Server-1.0 file contract.
 *
 * The worksite file list intentionally contains no hidden storage keys. Links are
 * resolved through /files/{id}; only DEFECT/ACCEPTANCE links are exposed to the
 * Android collaboration cache in this migration step.
 */
class Server1FileApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class LinkedFile(
        val fileId: String,
        val worksiteId: String,
        val parentEntityType: String,
        val parentEntityId: String,
        val filename: String?,
        val mimeType: String?,
        val sizeBytes: Long,
        val sha256: String?
    )

    data class DownloadedFile(
        val bytes: ByteArray,
        val mimeType: String?
    )

    sealed interface ListResult {
        data class Success(val files: List<LinkedFile>) : ListResult
        data object Unsupported : ListResult
        data object SessionInvalid : ListResult
        data class Error(val message: String) : ListResult
    }

    sealed interface ContentResult {
        data class Success(val file: DownloadedFile) : ContentResult
        data object Unsupported : ContentResult
        data object SessionInvalid : ContentResult
        data class Error(val message: String) : ContentResult
    }

    fun listLinkedFiles(token: String, worksiteId: String): ListResult {
        val path = "/api/v1/worksites/${encode(worksiteId)}/files?limit=1000"
        val listConnection = openConnection(path)
        return try {
            authorize(listConnection, token)
            val code = listConnection.responseCode
            val body = readBody(listConnection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return ListResult.Error("Dateiliste konnte nicht gelesen werden.")
                    val array = root.optJSONArray("files")
                        ?: return ListResult.Success(emptyList())
                    val result = mutableListOf<LinkedFile>()
                    for (index in 0 until array.length()) {
                        val file = array.optJSONObject(index) ?: continue
                        val fileId = file.optString("id").trim()
                        if (fileId.isBlank()) continue
                        when (val metadata = loadMetadata(token, worksiteId, fileId, file)) {
                            MetadataResult.Unsupported -> return ListResult.Unsupported
                            MetadataResult.SessionInvalid -> return ListResult.SessionInvalid
                            is MetadataResult.Error -> return ListResult.Error(metadata.message)
                            is MetadataResult.Success -> result += metadata.files
                        }
                    }
                    ListResult.Success(result)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> ListResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> ListResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) ListResult.SessionInvalid
                    else ListResult.Error(errorMessage(body, code))
                else -> ListResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            ListResult.Error(error.message ?: "Server-1.0-Dateiliste konnte nicht geladen werden.")
        } finally {
            listConnection.disconnect()
        }
    }

    fun downloadContent(token: String, fileId: String): ContentResult {
        val connection = openConnection("/api/v1/files/${encode(fileId)}/content")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> ContentResult.Success(
                    DownloadedFile(
                        bytes = connection.inputStream.use { it.readBytes() },
                        mimeType = connection.contentType
                    )
                )
                HttpURLConnection.HTTP_NOT_FOUND -> ContentResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> ContentResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    val body = readBody(connection, code)
                    if (isInvalidDevice(body)) ContentResult.SessionInvalid
                    else ContentResult.Error(errorMessage(body, code))
                }
                else -> ContentResult.Error(errorMessage(readBody(connection, code), code))
            }
        } catch (error: Exception) {
            ContentResult.Error(error.message ?: "Server-1.0-Datei konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private sealed interface MetadataResult {
        data class Success(val files: List<LinkedFile>) : MetadataResult
        data object Unsupported : MetadataResult
        data object SessionInvalid : MetadataResult
        data class Error(val message: String) : MetadataResult
    }

    private fun loadMetadata(
        token: String,
        worksiteId: String,
        fileId: String,
        listItem: JSONObject
    ): MetadataResult {
        val connection = openConnection("/api/v1/files/${encode(fileId)}")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return MetadataResult.Error("Dateimetadaten konnten nicht gelesen werden.")
                    val file = root.optJSONObject("file") ?: listItem
                    val links = root.optJSONArray("links")
                    val linked = mutableListOf<LinkedFile>()
                    if (links != null) {
                        for (index in 0 until links.length()) {
                            val link = links.optJSONObject(index) ?: continue
                            val type = link.optString("entity_type").trim().uppercase()
                            if (type != "DEFECT" && type != "ACCEPTANCE") continue
                            val entityId = link.optString("entity_id").trim()
                            if (entityId.isBlank()) continue
                            linked += LinkedFile(
                                fileId = fileId,
                                worksiteId = worksiteId,
                                parentEntityType = type,
                                parentEntityId = entityId,
                                filename = file.nullableString("original_name"),
                                mimeType = file.nullableString("mime_type"),
                                sizeBytes = file.optLong("byte_size", 0L),
                                sha256 = file.nullableString("sha256")
                            )
                        }
                    }
                    MetadataResult.Success(linked)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> MetadataResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> MetadataResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) MetadataResult.SessionInvalid
                    else MetadataResult.Error(errorMessage(body, code))
                else -> MetadataResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            MetadataResult.Error(error.message ?: "Server-1.0-Dateimetadaten konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
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

    private fun JSONObject.nullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotBlank() }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
