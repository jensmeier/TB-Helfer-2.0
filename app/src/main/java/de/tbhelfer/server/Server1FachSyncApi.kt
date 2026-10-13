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

/**
 * Canonical Android bridge for Server-1.0 defects and acceptances.
 *
 * Read operations use the complete area-scoped collections. Mutations reuse the
 * existing local collaboration outbox and send its stable clientChangeId through
 * X-Client-Change-Id. BTB and file transfer deliberately stay outside this class.
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

    sealed interface MutationResult {
        data class Applied(val snapshot: Snapshot) : MutationResult
        data class Deleted(val serverId: String, val entityVersion: Long) : MutationResult
        data class Conflict(val currentVersion: Long?, val detail: String) : MutationResult
        data object Unsupported : MutationResult
        data object SessionInvalid : MutationResult
        data class Error(val message: String) : MutationResult
    }

    fun loadDefects(token: String, worksiteId: String): Result =
        loadCollection(token, "/api/v1/worksites/${encode(worksiteId)}/defects", "defects", EntityKind.DEFECT)

    fun loadAcceptances(token: String, worksiteId: String): Result =
        loadCollection(token, "/api/v1/worksites/${encode(worksiteId)}/acceptances", "acceptances", EntityKind.ACCEPTANCE)

    fun applyPending(token: String, change: SyncCollabPendingChangeEntity): MutationResult {
        if (change.operation.equals(SyncCollabPendingChangeEntity.OP_DELETE, ignoreCase = true)) {
            return when (change.entityType.uppercase()) {
                SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE -> deleteAcceptance(token, change)
                SyncCollabPendingChangeEntity.TYPE_DEFECT ->
                    MutationResult.Error("Mangel-Löschen besitzt derzeit keinen Android-Benutzer-Löschvorgang.")
                else -> MutationResult.Error(
                    "Nicht unterstützter Server-1.0-Fachtyp: ${change.entityType}"
                )
            }
        }
        if (!change.operation.equals(SyncCollabPendingChangeEntity.OP_UPSERT, ignoreCase = true)) {
            return MutationResult.Error(
                "Nicht unterstützte Server-1.0-Fachoperation: ${change.operation}"
            )
        }
        return when (change.entityType.uppercase()) {
            SyncCollabPendingChangeEntity.TYPE_DEFECT -> mutateDefect(token, change)
            SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE -> mutateAcceptance(token, change)
            else -> MutationResult.Error("Nicht unterstützter Server-1.0-Fachtyp: ${change.entityType}")
        }
    }

    private fun mutateDefect(token: String, change: SyncCollabPendingChangeEntity): MutationResult {
        val desired = change.status?.uppercase().orEmpty().ifBlank { "OFFEN" }
        if (desired !in setOf("OFFEN", "IN_ARBEIT", "ABGELEHNT", "ERLEDIGT")) {
            return MutationResult.Error("Unbekannter Mangelstatus: $desired")
        }

        val fachPayload = jsonObjectOrEmpty(change.payloadJson)
        val areaScope = jsonObjectOrEmpty(change.areaScopeJson)
        val title = fachPayload.optString("beschreibung").trim().takeIf { it.isNotBlank() }
        val existingId = change.serverId?.takeIf { it.isNotBlank() }

        if (existingId == null) {
            val createBody = JSONObject()
                .put("title", title ?: JSONObject.NULL)
                .put("payload", fachPayload)
                .put("area_scope", areaScope)
            val created = sendMutation(
                token = token,
                path = "/api/v1/worksites/${encode(change.worksiteServerId)}/defects",
                method = "POST",
                clientChangeId = change.clientChangeId,
                body = createBody,
                kind = EntityKind.DEFECT
            )
            if (created !is MutationResult.Applied) return created
            return when (desired) {
                "OFFEN" -> created
                "IN_ARBEIT", "ABGELEHNT" -> updateDefect(
                    token,
                    created.snapshot.serverId,
                    created.snapshot.entityVersion,
                    fachPayload,
                    areaScope,
                    title,
                    if (desired == "ABGELEHNT") "REJECTED" else "IN_PROGRESS",
                    derivedChangeId(change.clientChangeId, "status")
                )
                else -> closeDefect(
                    token,
                    created.snapshot.serverId,
                    created.snapshot.entityVersion,
                    derivedChangeId(change.clientChangeId, "close")
                )
            }
        }

        val baseVersion = change.baseVersion
            ?: return MutationResult.Error("Server-Version für Mangeländerung fehlt.")

        if (desired == "ERLEDIGT") {
            val updated = updateDefect(
                token,
                existingId,
                baseVersion,
                fachPayload,
                areaScope,
                title,
                null,
                change.clientChangeId
            )
            if (updated !is MutationResult.Applied) return updated
            return closeDefect(
                token,
                existingId,
                updated.snapshot.entityVersion,
                derivedChangeId(change.clientChangeId, "close")
            )
        }

        return updateDefect(
            token,
            existingId,
            baseVersion,
            fachPayload,
            areaScope,
            title,
            when (desired) {
                "IN_ARBEIT" -> "IN_PROGRESS"
                "ABGELEHNT" -> "REJECTED"
                else -> "OPEN"
            },
            change.clientChangeId
        )
    }

    private fun updateDefect(
        token: String,
        serverId: String,
        baseVersion: Long,
        fachPayload: JSONObject,
        areaScope: JSONObject,
        title: String?,
        serverStatus: String?,
        clientChangeId: String
    ): MutationResult {
        val body = JSONObject()
            .put("base_version", baseVersion)
            .put("title", title ?: JSONObject.NULL)
            .put("payload", fachPayload)
            .put("area_scope", areaScope)
        if (serverStatus != null) body.put("status", serverStatus)
        return sendMutation(
            token,
            "/api/v1/defects/${encode(serverId)}",
            "PATCH",
            clientChangeId,
            body,
            EntityKind.DEFECT
        )
    }

    private fun closeDefect(
        token: String,
        serverId: String,
        baseVersion: Long,
        clientChangeId: String
    ): MutationResult = sendMutation(
        token,
        "/api/v1/defects/${encode(serverId)}/close",
        "POST",
        clientChangeId,
        JSONObject().put("base_version", baseVersion),
        EntityKind.DEFECT
    )

    private fun deleteAcceptance(
        token: String,
        change: SyncCollabPendingChangeEntity
    ): MutationResult {
        val serverId = change.serverId?.takeIf { it.isNotBlank() }
            ?: return MutationResult.Error("Server-ID für Abnahme-Löschung fehlt.")
        val baseVersion = change.baseVersion
            ?: return MutationResult.Error("Server-Version für Abnahme-Löschung fehlt.")

        val path = "/api/v1/acceptances/${encode(serverId)}?base_version=$baseVersion"
        val connection = openConnection(path, "DELETE").apply {
            // DELETE trägt die Version im Query und absichtlich keinen JSON-Body.
            doOutput = false
        }
        return try {
            authorize(connection, token)
            connection.setRequestProperty("X-Client-Change-Id", change.clientChangeId)
            val code = connection.responseCode
            val response = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_NO_CONTENT ->
                    MutationResult.Deleted(serverId, baseVersion + 1L)
                HttpURLConnection.HTTP_CONFLICT -> {
                    val root = runCatching { JSONObject(response) }.getOrNull()
                    val detail = root?.opt("detail")
                    val detailObject = detail as? JSONObject
                    MutationResult.Conflict(
                        currentVersion = detailObject?.optLong("current_version")
                            ?.takeIf { it > 0L },
                        detail = when (detail) {
                            is String -> detail
                            is JSONObject -> detail.optString("code").ifBlank { detail.toString() }
                            else -> "VERSION_CONFLICT"
                        }
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = detailMessage(response)
                    if (detail.isBlank() || detail.equals("Not Found", ignoreCase = true)) {
                        MutationResult.Unsupported
                    } else {
                        MutationResult.Error(detail)
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> MutationResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(response)) MutationResult.SessionInvalid
                    else MutationResult.Error(errorMessage(response, code))
                else -> MutationResult.Error(errorMessage(response, code))
            }
        } catch (error: Exception) {
            MutationResult.Error(
                error.message ?: "Server-1.0-Abnahme konnte nicht gelöscht werden."
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun mutateAcceptance(token: String, change: SyncCollabPendingChangeEntity): MutationResult {
        val desired = change.status?.uppercase().orEmpty().ifBlank { "ENTWURF" }
        val draftResults = setOf("ENTWURF", "ABGENOMMEN_MIT_RESTPUNKTEN", "NOCH_ZU_PRUEFEN")
        val finalResults = setOf("ABGENOMMEN", "NICHT_ABGENOMMEN")
        if (desired !in draftResults && desired !in finalResults) {
            return MutationResult.Error("Unbekanntes Abnahmeergebnis: $desired")
        }

        val fachPayload = jsonObjectOrEmpty(change.payloadJson)
        val areaScope = jsonObjectOrEmpty(change.areaScopeJson)
        val title = fachPayload.optString("bezeichnung").trim()
            .ifBlank { fachPayload.optString("objekt").trim() }
            .takeIf { it.isNotBlank() }
        val existingId = change.serverId?.takeIf { it.isNotBlank() }

        if (existingId == null) {
            val createResult = if (desired in finalResults) "ENTWURF" else desired
            val created = sendMutation(
                token = token,
                path = "/api/v1/worksites/${encode(change.worksiteServerId)}/acceptances",
                method = "POST",
                clientChangeId = change.clientChangeId,
                body = JSONObject()
                    .put("title", title ?: JSONObject.NULL)
                    .put("payload", fachPayload)
                    .put("area_scope", areaScope)
                    .put("result", createResult),
                kind = EntityKind.ACCEPTANCE
            )
            if (created !is MutationResult.Applied) return created
            return if (desired in finalResults) {
                finalizeAcceptance(
                    token,
                    created.snapshot.serverId,
                    created.snapshot.entityVersion,
                    desired,
                    derivedChangeId(change.clientChangeId, "finalize")
                )
            } else {
                created
            }
        }

        val baseVersion = change.baseVersion
            ?: return MutationResult.Error("Server-Version für Abnahmeänderung fehlt.")

        if (desired in finalResults) {
            val updated = updateAcceptance(
                token,
                existingId,
                baseVersion,
                fachPayload,
                areaScope,
                title,
                result = null,
                clientChangeId = change.clientChangeId
            )
            if (updated !is MutationResult.Applied) return updated
            return finalizeAcceptance(
                token,
                existingId,
                updated.snapshot.entityVersion,
                desired,
                derivedChangeId(change.clientChangeId, "finalize")
            )
        }

        return updateAcceptance(
            token,
            existingId,
            baseVersion,
            fachPayload,
            areaScope,
            title,
            desired,
            change.clientChangeId
        )
    }

    private fun updateAcceptance(
        token: String,
        serverId: String,
        baseVersion: Long,
        fachPayload: JSONObject,
        areaScope: JSONObject,
        title: String?,
        result: String?,
        clientChangeId: String
    ): MutationResult {
        val body = JSONObject()
            .put("base_version", baseVersion)
            .put("title", title ?: JSONObject.NULL)
            .put("payload", fachPayload)
            .put("area_scope", areaScope)
        if (result != null) body.put("result", result)
        return sendMutation(
            token,
            "/api/v1/acceptances/${encode(serverId)}",
            "PATCH",
            clientChangeId,
            body,
            EntityKind.ACCEPTANCE
        )
    }

    private fun finalizeAcceptance(
        token: String,
        serverId: String,
        baseVersion: Long,
        result: String,
        clientChangeId: String
    ): MutationResult = sendMutation(
        token,
        "/api/v1/acceptances/${encode(serverId)}/finalize",
        "POST",
        clientChangeId,
        JSONObject()
            .put("base_version", baseVersion)
            .put("result", result),
        EntityKind.ACCEPTANCE
    )

    private fun loadCollection(
        token: String,
        path: String,
        arrayKey: String,
        kind: EntityKind
    ): Result {
        val connection = openConnection(path, "GET")
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
                HttpURLConnection.HTTP_UNAUTHORIZED -> Result.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) Result.SessionInvalid else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server-1.0-Fachdaten konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun sendMutation(
        token: String,
        path: String,
        method: String,
        clientChangeId: String,
        body: JSONObject,
        kind: EntityKind
    ): MutationResult {
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
                    val root = runCatching { JSONObject(response) }.getOrNull()
                        ?: return MutationResult.Error("Serverantwort konnte nicht gelesen werden.")
                    val snapshot = parseSnapshot(root, kind)
                        ?: return MutationResult.Error("Server lieferte keine vollständige Fachentität.")
                    MutationResult.Applied(snapshot)
                }
                HttpURLConnection.HTTP_CONFLICT -> {
                    val root = runCatching { JSONObject(response) }.getOrNull()
                    val detail = root?.opt("detail")
                    val detailObject = detail as? JSONObject
                    MutationResult.Conflict(
                        currentVersion = detailObject?.optLong("current_version")
                            ?.takeIf { it > 0L },
                        detail = when (detail) {
                            is String -> detail
                            is JSONObject -> detail.optString("code").ifBlank { detail.toString() }
                            else -> "VERSION_CONFLICT"
                        }
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = detailMessage(response)
                    if (detail.isBlank() || detail.equals("Not Found", ignoreCase = true)) {
                        MutationResult.Unsupported
                    } else {
                        MutationResult.Error(detail)
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> MutationResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(response)) MutationResult.SessionInvalid
                    else MutationResult.Error(errorMessage(response, code))
                else -> MutationResult.Error(errorMessage(response, code))
            }
        } catch (error: Exception) {
            MutationResult.Error(error.message ?: "Server-1.0-Fachänderung konnte nicht übertragen werden.")
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
                "REJECTED" -> "ABGELEHNT"
                "CLOSED" -> "ERLEDIGT"
                else -> item.optString("status").trim().takeIf { it.isNotBlank() }
            }
            EntityKind.ACCEPTANCE -> item.optString("result").trim().takeIf { it.isNotBlank() }
        }

        if (kind == EntityKind.ACCEPTANCE && status != null) {
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

    private fun openConnection(path: String, method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
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

    private fun jsonObjectOrEmpty(raw: String): JSONObject =
        runCatching { JSONObject(raw) }.getOrElse { JSONObject() }

    private fun derivedChangeId(base: String, suffix: String): String =
        (base + "-" + suffix).take(200)

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() }
    }

    private enum class EntityKind { DEFECT, ACCEPTANCE }
}
