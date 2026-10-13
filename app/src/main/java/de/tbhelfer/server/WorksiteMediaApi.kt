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
 * Baustellenweiter Bildkatalog für BTB, Mängel und Abnahmen.
 *
 * Server 1.0 liefert den kanonischen read-only Katalog. Verantwortliche TBs dürfen damit Bilder
 * aller Teams derselben Baustelle exportieren, ohne fremde BTB-Texte zu erhalten. Alte 0.7.x-
 * Server bleiben über den bisherigen /collab-Katalog kompatibel.
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

    private enum class Transport { UNKNOWN, SERVER1, LEGACY }

    @Volatile
    private var transport: Transport = Transport.UNKNOWN

    fun loadCatalog(token: String, worksiteId: String): CatalogResult {
        return when (val canonical = loadCatalogAt(
            token,
            "/api/v1/worksites/${encode(worksiteId)}/media",
            canonical = true
        )) {
            CatalogResult.Unsupported -> {
                when (val legacy = loadCatalogAt(
                    token,
                    "/api/v1/collab/worksites/${encode(worksiteId)}/media",
                    canonical = false
                )) {
                    is CatalogResult.Success -> {
                        transport = Transport.LEGACY
                        legacy
                    }
                    else -> legacy
                }
            }
            is CatalogResult.Success -> {
                transport = Transport.SERVER1
                canonical
            }
            else -> canonical
        }
    }

    fun download(token: String, attachmentId: String): DownloadResult =
        when (transport) {
            Transport.SERVER1 ->
                downloadAt(token, "/api/v1/files/${encode(attachmentId)}/content", canonical = true)
            Transport.LEGACY ->
                downloadAt(token, "/api/v1/collab/attachments/${encode(attachmentId)}", canonical = false)
            Transport.UNKNOWN ->
                DownloadResult.Error("Medienkatalog muss vor dem Bilddownload geladen werden.")
        }

    private fun loadCatalogAt(
        token: String,
        path: String,
        canonical: Boolean
    ): CatalogResult {
        val connection = openConnection(path, "application/json")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parseCatalog(body)
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    if (!canonical) {
                        CatalogResult.Unsupported
                    } else {
                        val detail = detailMessage(body)
                        if (detail.isBlank() || detail.equals("Not Found", true)) {
                            CatalogResult.Unsupported
                        } else {
                            CatalogResult.Error(detail)
                        }
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> CatalogResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    if (!canonical || isInvalidDevice(body)) {
                        CatalogResult.SessionInvalid
                    } else {
                        CatalogResult.Error(errorMessage(body, code))
                    }
                }
                else -> CatalogResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            CatalogResult.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCatalog(body: String): CatalogResult {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return CatalogResult.Error("Medienkatalog konnte nicht gelesen werden.")
        val array = root.optJSONArray("media") ?: JSONArray()
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("attachment_id").trim()
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
        return CatalogResult.Success(items)
    }

    private fun downloadAt(
        token: String,
        path: String,
        canonical: Boolean
    ): DownloadResult {
        val connection = openConnection(path, "application/octet-stream")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> DownloadResult.Success(
                    DownloadedMedia(
                        bytes = connection.inputStream.use { it.readBytes() },
                        mimeType = connection.contentType,
                        filename = contentDispositionFilename(
                            connection.getHeaderField("Content-Disposition")
                        )
                    )
                )
                HttpURLConnection.HTTP_UNAUTHORIZED -> DownloadResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    val body = readBody(connection, code)
                    if (!canonical || isInvalidDevice(body)) {
                        DownloadResult.SessionInvalid
                    } else {
                        DownloadResult.Error(errorMessage(body, code))
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> DownloadResult.NotFound
                else -> DownloadResult.Error(errorMessage(readBody(connection, code), code))
            }
        } catch (error: Exception) {
            DownloadResult.Error(error.message ?: "Bild konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String, accept: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 45_000
            useCaches = false
            setRequestProperty("Accept", accept)
        }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
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
        val detail = runCatching { JSONObject(body).opt("detail") }.getOrNull()
        return when (detail) {
            is String -> detail.trim()
            is JSONObject -> detail.optString("code").trim().ifBlank { detail.toString() }
            else -> detail?.toString()?.trim().orEmpty()
        }
    }

    private fun isInvalidDevice(body: String): Boolean =
        detailMessage(body) in setOf("DEVICE_REVOKED", "USER_DISABLED", "USER_EXPIRED")

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

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
