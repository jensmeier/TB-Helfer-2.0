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

/** Transport für gruppenbezogenen BTB- und baustellenweiten Mängel-/Abnahme-Sync ab Server 0.7.0. */
class CollabSyncApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class WorksiteContext(
        val worksiteId: String,
        val name: String?,
        val designation: String?,
        val worksiteType: String?,
        val status: String?,
        val companyId: Long?,
        val groupId: String,
        val memberRole: String,
        val areaScopeJson: String,
        val groupLabel: String?,
        val leadUserId: String?,
        val leadNameSnapshot: String?
    )

    data class ChangeItem(
        val sequenceNo: Long,
        val entityType: String,
        val entityId: String,
        val operation: String,
        val entityVersion: Long?,
        val changedAt: String?,
        val changedByName: String?,
        val fromThisDevice: Boolean,
        val groupId: String?,
        val areaScopeJson: String,
        val dataJson: String
    )

    data class ChangePage(
        val nextSequenceNo: Long,
        val latestSequenceNo: Long,
        val hasMore: Boolean,
        val changes: List<ChangeItem>
    )

    data class DownloadedAttachment(
        val bytes: ByteArray,
        val mimeType: String?,
        val filename: String?
    )

    sealed interface ContextResult {
        data class Success(val items: List<WorksiteContext>) : ContextResult
        data object Unsupported : ContextResult
        data object SessionInvalid : ContextResult
        data class Error(val message: String) : ContextResult
    }

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

    sealed interface AttachmentResult {
        data class Success(val attachment: DownloadedAttachment) : AttachmentResult
        data object Unsupported : AttachmentResult
        data object SessionInvalid : AttachmentResult
        data class Error(val message: String) : AttachmentResult
    }

    sealed interface AttachmentUploadResult {
        data class Success(
            val attachmentId: String,
            val sequenceNo: Long?,
            val sha256: String?,
            val sizeBytes: Long
        ) : AttachmentUploadResult
        data object Unsupported : AttachmentUploadResult
        data object SessionInvalid : AttachmentUploadResult
        data class Error(val message: String) : AttachmentUploadResult
    }

    fun loadContext(token: String): ContextResult {
        val connection = openConnection("/api/v1/collab/context", "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return ContextResult.Error("Serverantwort konnte nicht gelesen werden.")
                    val array = root.optJSONArray("worksites") ?: JSONArray()
                    val items = buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val worksiteId = item.optString("worksite_id")
                            val groupId = item.optString("group_id")
                            if (worksiteId.isBlank() || groupId.isBlank()) continue
                            add(
                                WorksiteContext(
                                    worksiteId = worksiteId,
                                    name = item.optNullableString("name"),
                                    designation = item.optNullableString("designation"),
                                    worksiteType = item.optNullableString("worksite_type"),
                                    status = item.optNullableString("status"),
                                    companyId = item.optNullableLong("company_id"),
                                    groupId = groupId,
                                    memberRole = item.optString("member_role").ifBlank { "HELPER" },
                                    areaScopeJson = item.optJsonText("area_scope") ?: "{}",
                                    groupLabel = item.optNullableString("group_label"),
                                    leadUserId = item.optNullableString("lead_user_id"),
                                    leadNameSnapshot = item.optNullableString("lead_name_snapshot")
                                )
                            )
                        }
                    }
                    ContextResult.Success(items)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> ContextResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        ContextResult.SessionInvalid else ContextResult.Error(errorMessage(body, code))
                else -> ContextResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            ContextResult.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    fun loadChanges(token: String, worksiteId: String, after: Long, limit: Int = 100): PullResult {
        val path = "/api/v1/collab/changes?worksite_id=${encode(worksiteId)}&after=${after.coerceAtLeast(0)}&limit=${limit.coerceIn(1, 250)}"
        val connection = openConnection(path, "GET")
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
                                ChangeItem(
                                    sequenceNo = item.optLong("sequence_no", 0),
                                    entityType = item.optString("entity_type"),
                                    entityId = entityId,
                                    operation = item.optString("operation"),
                                    entityVersion = item.optNullableLong("entity_version"),
                                    changedAt = item.optNullableString("changed_at"),
                                    changedByName = item.optNullableString("changed_by_name"),
                                    fromThisDevice = item.optBoolean("from_this_device", false),
                                    groupId = item.optNullableString("group_id"),
                                    areaScopeJson = item.optJsonText("area_scope") ?: "{}",
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

    fun acknowledge(token: String, worksiteId: String, sequenceNo: Long): AckResult {
        val connection = openConnection("/api/v1/collab/ack", "POST")
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

    fun uploadMutation(token: String, change: SyncCollabPendingChangeEntity): MutationResult {
        val connection = openConnection("/api/v1/collab/mutations", "POST")
        return try {
            authorize(connection, token)
            val payload = jsonObjectOrEmpty(change.payloadJson)
            val request = JSONObject()
                .put("client_change_id", change.clientChangeId)
                .put("worksite_id", change.worksiteServerId)
                .put("entity_type", change.entityType)
                .put("operation", change.operation)
                .put("area_scope", effectiveAreaScope(change, payload))
                .put("payload", payload)
            if (!change.groupId.isNullOrBlank()) request.put("group_id", change.groupId)
            if (!change.serverId.isNullOrBlank()) request.put("entity_id", change.serverId)
            if (!change.clientEntityKey.isNullOrBlank()) request.put("client_entity_key", change.clientEntityKey)
            if (change.baseVersion != null) request.put("base_version", change.baseVersion)
            if (!change.status.isNullOrBlank()) request.put("status", change.status)
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
                    if (detail.isNullOrBlank() || detail.equals("Not Found", ignoreCase = true)) MutationResult.Unsupported
                    else MutationResult.Error(detail)
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

    fun downloadAttachment(token: String, attachmentId: String): AttachmentResult {
        val connection = openConnection("/api/v1/collab/attachments/${encode(attachmentId)}", "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    AttachmentResult.Success(
                        DownloadedAttachment(
                            bytes = bytes,
                            mimeType = connection.contentType,
                            filename = contentDispositionFilename(connection.getHeaderField("Content-Disposition"))
                        )
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> AttachmentResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    val body = readBody(connection, code)
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        AttachmentResult.SessionInvalid else AttachmentResult.Error(errorMessage(body, code))
                }
                else -> AttachmentResult.Error(errorMessage(readBody(connection, code), code))
            }
        } catch (error: Exception) {
            AttachmentResult.Error(error.message ?: "Bild konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun uploadAttachment(
        token: String,
        worksiteId: String,
        entityType: String,
        entityId: String,
        filename: String,
        mimeType: String,
        bytes: ByteArray
    ): AttachmentUploadResult {
        val connection = openConnection(
            "/api/v1/collab/attachments/${encode(entityType)}/${encode(entityId)}",
            "POST",
            contentType = mimeType
        )
        return try {
            authorize(connection, token)
            connection.setRequestProperty("X-Worksite-ID", worksiteId)
            connection.setRequestProperty("X-Filename", filename)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val body = readBody(connection, code)
            val root = body.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val id = root?.optString("attachment_id").orEmpty()
                    if (id.isBlank()) AttachmentUploadResult.Error("Server lieferte keine Bild-ID.")
                    else AttachmentUploadResult.Success(
                        attachmentId = id,
                        sequenceNo = root?.optNullableLong("sequence_no"),
                        sha256 = root?.optNullableString("sha256"),
                        sizeBytes = root?.optLong("size_bytes", bytes.size.toLong()) ?: bytes.size.toLong()
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> AttachmentUploadResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        AttachmentUploadResult.SessionInvalid else AttachmentUploadResult.Error(errorMessage(body, code))
                else -> AttachmentUploadResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            AttachmentUploadResult.Error(error.message ?: "Bild konnte nicht hochgeladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun openConnection(path: String, method: String, contentType: String = "application/json; charset=utf-8"): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }

    private fun writeJson(connection: HttpURLConnection, json: JSONObject) {
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }
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

    private fun jsonObjectOrEmpty(text: String): JSONObject = runCatching { JSONObject(text) }.getOrElse { JSONObject() }

    /**
     * Ein Haupt-TB besitzt typischerweise den Scope {all:true}. Für einen einzelnen Mangel oder
     * eine Abnahme wäre das zu grob: sonst sähen alle Hilfsnutzer den Vorgang, obwohl er z. B. nur
     * Gleis 28 betrifft. Deshalb wird bei einem baustellenweiten Ausgangsscope aus den bereits
     * vorhandenen Fachdaten ein engerer Entity-Scope erzeugt. Ein explizit eingeschränkter Scope
     * (Hilfsnutzer) wird niemals erweitert oder ersetzt; der Server prüft ihn zusätzlich.
     */
    private fun effectiveAreaScope(change: SyncCollabPendingChangeEntity, payload: JSONObject): JSONObject {
        val requested = jsonObjectOrEmpty(change.areaScopeJson)
        val type = change.entityType.uppercase()
        if (type != SyncCollabPendingChangeEntity.TYPE_DEFECT &&
            type != SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE
        ) return requested

        val explicitlyRestricted = requested.has("all") && !requested.optBoolean("all", false)
        if (explicitlyRestricted) return requested

        val labels = linkedSetOf<String>()
        when (type) {
            SyncCollabPendingChangeEntity.TYPE_DEFECT -> addScopeLabel(labels, payload.optString("bereich"))
            SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE -> {
                addScopeLabel(labels, payload.optString("unterKnotenName"))
                addScopeLabel(labels, payload.optString("objekt"))
            }
        }

        val kmRange = when (type) {
            SyncCollabPendingChangeEntity.TYPE_DEFECT -> parseKmDisplayRange(payload.optString("km"))
            SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE -> {
                val from = payload.optNullableDouble("kmVon")?.let(::kmValueToMeters)
                val to = payload.optNullableDouble("kmBis")?.let(::kmValueToMeters)
                normalizeKmRange(from, to)
            }
            else -> null
        }

        if (labels.isEmpty() && kmRange == null) return requested

        return JSONObject().apply {
            put("all", false)
            if (labels.isNotEmpty()) {
                put("labels", JSONArray(labels.toList()))
                put("label", labels.joinToString(", "))
            }
            if (kmRange != null) {
                put("km_from_m", kmRange.first)
                put("km_to_m", kmRange.second)
            }
        }
    }

    private fun addScopeLabel(target: MutableSet<String>, raw: String?) {
        val value = raw.orEmpty().trim()
        if (value.isBlank()) return
        val normalized = value.lowercase()
        if (normalized in GENERIC_SCOPE_LABELS) return

        value.split(Regex("[,;|/]+"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach(target::add)

        TRACK_SCOPE_REGEX.findAll(value).forEach { match -> target.add(match.value.trim()) }
        SWITCH_SCOPE_REGEX.findAll(value).forEach { match -> target.add(match.value.trim()) }
    }

    private fun parseKmDisplayRange(raw: String?): Pair<Double, Double>? {
        val text = raw.orEmpty().trim()
        if (text.isBlank() || text == "-") return null

        val plusValues = KM_PLUS_REGEX.findAll(text).mapNotNull { match ->
            val km = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val rest = match.groupValues.getOrNull(2)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            km * 1000.0 + rest
        }.toList()
        if (plusValues.isNotEmpty()) return normalizeKmRange(plusValues.first(), plusValues.getOrNull(1))

        val decimalValues = KM_DECIMAL_REGEX.findAll(text).mapNotNull { match ->
            match.value.replace(',', '.').toDoubleOrNull()?.let(::kmValueToMeters)
        }.toList()
        if (decimalValues.isEmpty()) return null
        return normalizeKmRange(decimalValues.first(), decimalValues.getOrNull(1))
    }

    private fun kmValueToMeters(value: Double): Double =
        if (kotlin.math.abs(value) < 1000.0) value * 1000.0 else value

    private fun normalizeKmRange(from: Double?, to: Double?): Pair<Double, Double>? {
        val first = from ?: to ?: return null
        val second = to ?: first
        return if (first <= second) first to second else second to first
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()
    }

    private fun contentDispositionFilename(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return value.substringAfter("filename=", "").trim().trim('"').takeIf { it.isNotBlank() }
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

    private companion object {
        val GENERIC_SCOPE_LABELS = setOf(
            "-",
            "hauptbaustelle",
            "baustelle",
            "btb abschnitt 8"
        )
        val TRACK_SCOPE_REGEX = Regex("(?i)\\b(?:Gleis|Gl\\.?)\\s*[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*\\b")
        val SWITCH_SCOPE_REGEX = Regex("(?i)\\b(?:Weiche\\s*[A-Za-z0-9]+|W\\s*\\d+[A-Za-z0-9.-]*)\\b")
        val KM_PLUS_REGEX = Regex("(\\d+)\\+(\\d+(?:[.,]\\d+)?)")
        val KM_DECIMAL_REGEX = Regex("\\d+(?:[.,]\\d+)?")
    }
}

