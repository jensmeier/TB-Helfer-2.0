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
 * Liest den serverseitigen Gesamt-Medienkatalog einer konkreten Baustelle.
 *
 * LEADs erhalten dabei Bild-Metadaten aller TB-Teams derselben Baustelle; Helfer bleiben
 * serverseitig auf ihre eigene Gruppe bzw. ihren area_scope begrenzt. BTB-Texte fremder Teams
 * werden nicht ausgeliefert.
 */
class WorksiteMediaApi(private val baseUrl: String = ServerConfig.BASE_URL) {

    data class MediaItem(
        val attachmentId: String,
        val entityType: String,
        val parentEntityId: String,
        val filename: String?,
        val mimeType: String?,
        val sha256: String?,
        val sizeBytes: Long,
        val createdAt: String?,
        val uploadedByUserId: String?,
        val uploadedByName: String?,
        val responsibleGroupId: String?,
        val responsibleTb: String?,
        val parentStatus: String?,
        val areaScopeJson: String,
        val parentPayloadJson: String
    )

    data class DownloadedMedia(
        val bytes: ByteArray,
        val mimeType: String?,
        val filename: String?
    )

    sealed interface CatalogResult {
        data class Success(val items: List<MediaItem>) : CatalogResult
        data object Unsupported : CatalogResult
        data object SessionInvalid : CatalogResult
        data class Error(val message: String) : CatalogResult
    }

    sealed interface DownloadResult {
        data class Success(val media: DownloadedMedia) : DownloadResult
        data object SessionInvalid : DownloadResult
        data object NotFound : DownloadResult
        data class Error(val message: String) : DownloadResult
    }

    fun loadCatalog(token: String, worksiteId: String): CatalogResult {
        val connection = openConnection("/api/v1/collab/worksites/${encode(worksiteId)}/media")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return CatalogResult.Error("Medienkatalog konnte nicht gelesen werden.")
                    val array = root.optJSONArray("media") ?: JSONArray()
                    val items = buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val id = item.optString("attachment_id")
                            if (id.isBlank()) continue
                            add(
                                MediaItem(
                                    attachmentId = id,
                                    entityType = item.optString("entity_type").uppercase(),
                                    parentEntityId = item.optString("parent_entity_id"),
                                    filename = item.optNullableString("filename"),
                                    mimeType = item.optNullableString("mime_type"),
                                    sha256 = item.optNullableString("sha256"),
                                    sizeBytes = item.optLong("size_bytes", 0L),
                                    createdAt = item.optNullableString("created_at"),
                                    uploadedByUserId = item.optNullableString("uploaded_by_user_id"),
                                    uploadedByName = item.optNullableString("uploaded_by_name"),
                                    responsibleGroupId = item.optNullableString("responsible_group_id"),
                                    responsibleTb = item.optNullableString("responsible_tb"),
                                    parentStatus = item.optNullableString("parent_status"),
                                    areaScopeJson = item.optJSONObject("area_scope")?.toString() ?: "{}",
                                    parentPayloadJson = item.optJSONObject("parent_payload")?.toString() ?: "{}"
                                )
                            )
                        }
                    }
                    CatalogResult.Success(items)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> CatalogResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> CatalogResult.SessionInvalid
                else -> CatalogResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            CatalogResult.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    fun download(token: String, attachmentId: String): DownloadResult {
        val connection = openConnection("/api/v1/collab/attachments/${encode(attachmentId)}")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> DownloadResult.Success(
                    DownloadedMedia(
                        bytes = connection.inputStream.use { it.readBytes() },
                        mimeType = connection.contentType,
                        filename = contentDispositionFilename(connection.getHeaderField("Content-Disposition"))
                    )
                )
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> DownloadResult.SessionInvalid
                HttpURLConnection.HTTP_NOT_FOUND -> DownloadResult.NotFound
                else -> DownloadResult.Error(errorMessage(readBody(connection, code), code))
            }
        } catch (error: Exception) {
            DownloadResult.Error(error.message ?: "Bild konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 45_000
            useCaches = false
        }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) connection.inputStream else connection.errorStream ?: return ""
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
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return detail.ifBlank { "Serverfehler ($code)." }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun contentDispositionFilename(raw: String?): String? {
        val value = raw.orEmpty()
        val marker = "filename=\""
        val start = value.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val from = start + marker.length
        val end = value.indexOf('"', from)
        return if (end > from) value.substring(from, end) else null
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }
}
