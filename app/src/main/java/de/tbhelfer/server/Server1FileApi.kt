package de.tbhelfer.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.File
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

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

    data class UploadedFile(
        val fileId: String,
        val sizeBytes: Long,
        val sha256: String?,
        val mimeType: String?
    )

    sealed interface UploadResult {
        data class Success(val file: UploadedFile) : UploadResult
        data object Unsupported : UploadResult
        data object SessionInvalid : UploadResult
        data class Error(val message: String) : UploadResult
    }

    sealed interface ListResult {
        data class Success(val files: List<LinkedFile>, val completeSnapshot: Boolean) : ListResult
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
        val result = mutableListOf<LinkedFile>()
        val seenCursors = hashSetOf<String>()
        var cursor: String? = null
        var explicitPagination = false
        var explicitComplete = false
        var legacyComplete = true

        do {
            val path = buildString {
                append("/api/v1/worksites/")
                append(encode(worksiteId))
                append("/files?limit=")
                append(FILE_LIST_LIMIT)
                cursor?.let {
                    append("&cursor=")
                    append(encode(it))
                }
            }
            val listConnection = openConnection(path)
            try {
                authorize(listConnection, token)
                val code = listConnection.responseCode
                val body = readBody(listConnection, code)
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val root = runCatching { JSONObject(body) }.getOrNull()
                            ?: return ListResult.Error("Dateiliste konnte nicht gelesen werden.")
                        val array = root.optJSONArray("files") ?: JSONArray()

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

                        val hasPaginationProtocol =
                            root.has("snapshot_complete") || root.has("next_cursor")
                        if (hasPaginationProtocol) {
                            explicitPagination = true
                            val next = root.nullableString("next_cursor")
                            val complete = root.optBoolean("snapshot_complete", next == null)
                            if (complete && next != null) {
                                return ListResult.Error(
                                    "Server meldet gleichzeitig vollständigen Snapshot und nächsten Dateicursor."
                                )
                            }
                            if (!complete && next == null) {
                                return ListResult.Error(
                                    "Server meldet unvollständigen Dateisnapshot ohne nächsten Cursor."
                                )
                            }
                            if (next != null && !seenCursors.add(next)) {
                                return ListResult.Error(
                                    "Server wiederholt denselben Dateicursor."
                                )
                            }
                            explicitComplete = complete
                            cursor = next
                        } else {
                            if (explicitPagination) {
                                return ListResult.Error(
                                    "Server hat die Dateipagination während des Snapshots gewechselt."
                                )
                            }
                            // Rolling-Update-Schutz für alte files_v1-Server ohne Cursorprotokoll.
                            legacyComplete = array.length() < FILE_LIST_LIMIT
                            cursor = null
                        }
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> return ListResult.Unsupported
                    HttpURLConnection.HTTP_UNAUTHORIZED -> return ListResult.SessionInvalid
                    HttpURLConnection.HTTP_FORBIDDEN ->
                        return if (isInvalidDevice(body)) {
                            ListResult.SessionInvalid
                        } else {
                            ListResult.Error(errorMessage(body, code))
                        }
                    else -> return ListResult.Error(errorMessage(body, code))
                }
            } catch (error: Exception) {
                return ListResult.Error(
                    error.message ?: "Server-1.0-Dateiliste konnte nicht geladen werden."
                )
            } finally {
                listConnection.disconnect()
            }
        } while (cursor != null)

        return ListResult.Success(
            result,
            completeSnapshot = if (explicitPagination) explicitComplete else legacyComplete
        )
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

    fun uploadLinkedFile(
        token: String,
        worksiteId: String,
        parentEntityType: String,
        parentEntityId: String,
        filename: String,
        mimeType: String,
        file: File,
        sha256: String,
        capturedAtEpochMs: Long? = null,
        gpsLat: Double? = null,
        gpsLon: Double? = null,
        gpsAccuracyM: Double? = null,
        photoMetadata: JSONObject? = null
    ): UploadResult {
        if (!file.isFile) return UploadResult.Error("Datei existiert nicht mehr: ${file.name}")
        val parentType = parentEntityType.uppercase()
        if (parentType != "DEFECT" && parentType != "ACCEPTANCE" && parentType != "BTB") {
            return UploadResult.Error("Server-1.0-Dateilinktyp wird nicht unterstützt: $parentType")
        }

        val createChangeId = mutationId(
            "file-create",
            worksiteId,
            parentType,
            parentEntityId,
            filename,
            sha256
        )
        val createBody = JSONObject()
            .put("target_kind", targetKind(parentType, mimeType))
            .put("original_name", filename)
            .put("mime_type", mimeType)
            .put("expected_bytes", file.length())
            .put("expected_sha256", sha256)
            .put("chunk_size", DEFAULT_CHUNK_SIZE)

        val uploadId = when (
            val created = jsonMutation(
                token,
                "/api/v1/worksites/${encode(worksiteId)}/uploads",
                "POST",
                createChangeId,
                createBody
            )
        ) {
            JsonMutationResult.Unsupported -> return UploadResult.Unsupported
            JsonMutationResult.SessionInvalid -> return UploadResult.SessionInvalid
            is JsonMutationResult.Error -> return UploadResult.Error(created.message)
            is JsonMutationResult.Success -> created.body.optString("id").trim()
                .takeIf { it.isNotBlank() }
                ?: return UploadResult.Error("Server lieferte keine Upload-ID.")
        }

        val session = when (val status = loadUploadSession(token, uploadId)) {
            UploadSessionResult.Unsupported -> return UploadResult.Unsupported
            UploadSessionResult.SessionInvalid -> return UploadResult.SessionInvalid
            is UploadSessionResult.Error -> return UploadResult.Error(status.message)
            is UploadSessionResult.Success -> status.session
        }

        when (session.status) {
            "OPEN" -> {
                val chunkResult = uploadRemainingChunks(
                    token = token,
                    uploadId = uploadId,
                    file = file,
                    startOffset = session.receivedBytes,
                    chunkSize = session.chunkSize
                )
                when (chunkResult) {
                    UploadResult.Unsupported -> return UploadResult.Unsupported
                    UploadResult.SessionInvalid -> return UploadResult.SessionInvalid
                    is UploadResult.Error -> return chunkResult
                    is UploadResult.Success -> Unit // unreachable marker result
                }
            }
            "COMPLETED" -> Unit
            else -> return UploadResult.Error("Upload-Sitzung ist nicht offen: ${session.status}")
        }

        val completeId = mutationId("file-complete", createChangeId)
        val completeBody = JSONObject()
            .put("entity_type", parentType)
            .put("entity_id", parentEntityId)
            .put("purpose", "ATTACHMENT")
        capturedAtEpochMs?.let {
            completeBody.put("captured_at", java.time.Instant.ofEpochMilli(it).toString())
        }
        gpsLat?.takeIf { it in -90.0..90.0 }?.let { completeBody.put("gps_lat", it) }
        gpsLon?.takeIf { it in -180.0..180.0 }?.let { completeBody.put("gps_lon", it) }
        gpsAccuracyM?.takeIf { it >= 0.0 }?.let { completeBody.put("gps_accuracy_m", it) }
        photoMetadata?.let { completeBody.put("photo_metadata", JSONObject(it.toString())) }
        return when (
            val completed = jsonMutation(
                token,
                "/api/v1/uploads/${encode(uploadId)}/complete",
                "POST",
                completeId,
                completeBody
            )
        ) {
            JsonMutationResult.Unsupported -> UploadResult.Unsupported
            JsonMutationResult.SessionInvalid -> UploadResult.SessionInvalid
            is JsonMutationResult.Error -> UploadResult.Error(completed.message)
            is JsonMutationResult.Success -> {
                val fileJson = completed.body.optJSONObject("file")
                    ?: return UploadResult.Error("Server lieferte keine fertige Datei.")
                val fileId = fileJson.optString("id").trim()
                if (fileId.isBlank()) return UploadResult.Error("Server lieferte keine Datei-ID.")
                UploadResult.Success(
                    UploadedFile(
                        fileId = fileId,
                        sizeBytes = fileJson.optLong("byte_size", file.length()),
                        sha256 = fileJson.nullableString("sha256") ?: sha256,
                        mimeType = fileJson.nullableString("mime_type") ?: mimeType
                    )
                )
            }
        }
    }

    private data class UploadSession(
        val receivedBytes: Long,
        val chunkSize: Int,
        val status: String
    )

    private sealed interface UploadSessionResult {
        data class Success(val session: UploadSession) : UploadSessionResult
        data object Unsupported : UploadSessionResult
        data object SessionInvalid : UploadSessionResult
        data class Error(val message: String) : UploadSessionResult
    }

    private fun loadUploadSession(token: String, uploadId: String): UploadSessionResult {
        val connection = openConnection("/api/v1/uploads/${encode(uploadId)}")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return UploadSessionResult.Error("Upload-Status konnte nicht gelesen werden.")
                    UploadSessionResult.Success(
                        UploadSession(
                            receivedBytes = root.optLong("received_bytes", 0L).coerceAtLeast(0L),
                            chunkSize = root.optInt("chunk_size", DEFAULT_CHUNK_SIZE)
                                .coerceIn(1024 * 1024, MAX_CHUNK_SIZE),
                            status = root.optString("status").uppercase().ifBlank { "OPEN" }
                        )
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> UploadSessionResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> UploadSessionResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) UploadSessionResult.SessionInvalid
                    else UploadSessionResult.Error(errorMessage(body, code))
                else -> UploadSessionResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            UploadSessionResult.Error(error.message ?: "Upload-Status konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadRemainingChunks(
        token: String,
        uploadId: String,
        file: File,
        startOffset: Long,
        chunkSize: Int
    ): UploadResult {
        if (startOffset < 0L || startOffset > file.length()) {
            return UploadResult.Error("Server meldet einen ungültigen Upload-Offset.")
        }
        var offset = startOffset
        FileInputStream(file).use { input ->
            var remainingSkip = startOffset
            while (remainingSkip > 0L) {
                val skipped = input.skip(remainingSkip)
                if (skipped <= 0L) return UploadResult.Error("Datei konnte nicht bis zum Upload-Offset gelesen werden.")
                remainingSkip -= skipped
            }
            val buffer = ByteArray(chunkSize.coerceIn(1024 * 1024, MAX_CHUNK_SIZE))
            while (offset < file.length()) {
                val wanted = minOf(buffer.size.toLong(), file.length() - offset).toInt()
                val read = input.read(buffer, 0, wanted)
                if (read <= 0) return UploadResult.Error("Datei endete vor der erwarteten Größe.")

                val connection = openConnection(
                    "/api/v1/uploads/${encode(uploadId)}/chunk?offset=$offset",
                    method = "PUT",
                    contentType = "application/octet-stream"
                )
                try {
                    authorize(connection, token)
                    connection.setFixedLengthStreamingMode(read)
                    connection.outputStream.use { it.write(buffer, 0, read) }
                    val code = connection.responseCode
                    if (code != HttpURLConnection.HTTP_OK) {
                        val body = readBody(connection, code)
                        return when (code) {
                            HttpURLConnection.HTTP_NOT_FOUND -> UploadResult.Unsupported
                            HttpURLConnection.HTTP_UNAUTHORIZED -> UploadResult.SessionInvalid
                            HttpURLConnection.HTTP_FORBIDDEN ->
                                if (isInvalidDevice(body)) UploadResult.SessionInvalid
                                else UploadResult.Error(errorMessage(body, code))
                            else -> UploadResult.Error(errorMessage(body, code))
                        }
                    }
                } catch (error: Exception) {
                    return UploadResult.Error(error.message ?: "Datei-Chunk konnte nicht übertragen werden.")
                } finally {
                    connection.disconnect()
                }
                offset += read
            }
        }
        // Internal success marker: uploadLinkedFile performs the idempotent complete call next.
        return UploadResult.Success(UploadedFile(uploadId, file.length(), null, null))
    }

    private sealed interface JsonMutationResult {
        data class Success(val body: JSONObject) : JsonMutationResult
        data object Unsupported : JsonMutationResult
        data object SessionInvalid : JsonMutationResult
        data class Error(val message: String) : JsonMutationResult
    }

    private fun jsonMutation(
        token: String,
        path: String,
        method: String,
        clientChangeId: String,
        body: JSONObject
    ): JsonMutationResult {
        val connection = openConnection(path, method)
        return try {
            authorize(connection, token)
            connection.setRequestProperty("X-Client-Change-Id", clientChangeId)
            writeJson(connection, body)
            val code = connection.responseCode
            val response = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK,
                HttpURLConnection.HTTP_CREATED -> {
                    val root = response.takeIf { it.isNotBlank() }
                        ?.let { runCatching { JSONObject(it) }.getOrNull() }
                        ?: JSONObject()
                    JsonMutationResult.Success(root)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> JsonMutationResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> JsonMutationResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(response)) JsonMutationResult.SessionInvalid
                    else JsonMutationResult.Error(errorMessage(response, code))
                else -> JsonMutationResult.Error(errorMessage(response, code))
            }
        } catch (error: Exception) {
            JsonMutationResult.Error(error.message ?: "Server-1.0-Dateiänderung ist fehlgeschlagen.")
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
                            if (type != "DEFECT" && type != "ACCEPTANCE" && type != "BTB") continue
                            if (type == "BTB") {
                                val purpose = link.optString("purpose").trim().uppercase()
                                val mime = file.nullableString("mime_type").orEmpty()
                                if (purpose == "BTB_FINAL_PDF" || !mime.startsWith("image/", ignoreCase = true)) continue
                            }
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

    private fun openConnection(
        path: String,
        method: String = "GET",
        contentType: String = "application/json; charset=utf-8"
    ): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun writeJson(connection: HttpURLConnection, json: JSONObject) {
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }
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

    private fun targetKind(parentType: String, mimeType: String): String = when {
        parentType == "DEFECT" && mimeType.startsWith("image/", ignoreCase = true) -> "DEFECT_PHOTO"
        parentType == "ACCEPTANCE" && mimeType.startsWith("image/", ignoreCase = true) -> "ACCEPTANCE_PHOTO"
        parentType == "BTB" && mimeType.startsWith("image/", ignoreCase = true) -> "PHOTO"
        else -> "ATTACHMENT"
    }

    private fun mutationId(prefix: String, vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("\u001f").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "android-$prefix-${digest.take(48)}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    companion object {
        private const val DEFAULT_CHUNK_SIZE = 8 * 1024 * 1024
        private const val MAX_CHUNK_SIZE = 16 * 1024 * 1024
        private const val FILE_LIST_LIMIT = 1000
    }
}
