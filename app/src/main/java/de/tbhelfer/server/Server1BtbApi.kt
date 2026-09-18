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
import java.util.UUID

/**
 * Read bridge for canonical Server-1.0 BTBs.
 *
 * Server 1.0 owns the team lifecycle (one active BTB per team). Android maps a
 * canonical BTB snapshot into the existing collaboration cache so the established
 * local BTB import path can be reused without creating a parallel storage model.
 */
class Server1BtbApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Snapshot(
        val serverId: String,
        val teamId: String,
        val clientEntityKey: String,
        val status: String,
        val payloadJson: String,
        val entityVersion: Long,
        val createdAtText: String?,
        val updatedAtText: String?
    )

    sealed interface Result {
        data class Success(val items: List<Snapshot>, val activeBtbId: String?) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    sealed interface MutationResult {
        data class Applied(val snapshot: Snapshot) : MutationResult
        data class Conflict(val currentVersion: Long?, val detail: String) : MutationResult
        data object Unsupported : MutationResult
        data object SessionInvalid : MutationResult
        data class Error(val message: String) : MutationResult
    }

    private data class Detail(
        val snapshot: Snapshot,
        val btb: JSONObject,
        val header: JSONObject,
        val bridgeEntryId: String?,
        val bridgeEntryVersion: Long?,
        val bridgePayload: JSONObject?
    )

    fun loadTeamBtbs(token: String, worksiteId: String, teamId: String): Result {
        val connection = openConnection(
            "/api/v1/worksites/${encode(worksiteId)}/teams/${encode(teamId)}/btbs"
        )
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return Result.Error("BTB-Liste konnte nicht gelesen werden.")
                    val list = root.optJSONArray("btbs") ?: JSONArray()
                    val snapshots = mutableListOf<Snapshot>()
                    for (index in 0 until list.length()) {
                        val row = list.optJSONObject(index) ?: continue
                        val id = row.optString("id").trim()
                        if (id.isBlank()) continue
                        when (val detail = loadBtb(token, id, teamId)) {
                            DetailResult.Unsupported -> return Result.Unsupported
                            DetailResult.SessionInvalid -> return Result.SessionInvalid
                            is DetailResult.Error -> return Result.Error(detail.message)
                            is DetailResult.Success -> snapshots += detail.detail.snapshot
                        }
                    }
                    Result.Success(
                        items = snapshots,
                        activeBtbId = root.nullableString("active_btb_id")
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> Result.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) Result.SessionInvalid
                    else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server-1.0-BTBs konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun applyPending(token: String, change: SyncCollabPendingChangeEntity): MutationResult {
        if (!change.operation.equals(SyncCollabPendingChangeEntity.OP_UPSERT, ignoreCase = true)) {
            return MutationResult.Error("Server-1.0-BTB-Löschen wird in diesem Android-Schritt noch nicht übertragen.")
        }
        if (!change.entityType.equals(SyncCollabPendingChangeEntity.TYPE_BTB, ignoreCase = true)) {
            return MutationResult.Error("Nicht unterstützter Server-1.0-BTB-Typ: ${change.entityType}")
        }

        val teamId = change.groupId?.trim().orEmpty()
        if (teamId.isBlank()) return MutationResult.Error("Team-Zuordnung für BTB fehlt.")
        val desired = runCatching { JSONObject(change.payloadJson) }.getOrElse { JSONObject() }
        if (desired.optString("btbId").isBlank()) {
            change.clientEntityKey?.takeIf { it.isNotBlank() }?.let { desired.put("btbId", it) }
        }
        val clientKey = desired.optString("btbId").trim()
            .ifBlank { change.clientEntityKey?.trim().orEmpty() }
        if (clientKey.isBlank()) return MutationResult.Error("Lokale BTB-Identität fehlt.")

        val desiredStatus = change.status?.uppercase().orEmpty().ifBlank { "ENTWURF" }
        if (desiredStatus !in setOf("ENTWURF", "FERTIG")) {
            return MutationResult.Error("Unbekannter lokaler BTB-Status: $desiredStatus")
        }

        var detail = if (change.serverId.isNullOrBlank()) {
            when (val created = createBtb(token, change, teamId, clientKey, desired, desiredStatus)) {
                is DetailMutationResult.Success -> created.detail
                is DetailMutationResult.Conflict ->
                    return MutationResult.Conflict(created.currentVersion, created.detail)
                DetailMutationResult.Unsupported -> return MutationResult.Unsupported
                DetailMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                is DetailMutationResult.Error -> return MutationResult.Error(created.message)
            }
        } else {
            when (val loaded = loadBtb(token, change.serverId, teamId)) {
                DetailResult.Unsupported -> return MutationResult.Unsupported
                DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
                is DetailResult.Error -> return MutationResult.Error(loaded.message)
                is DetailResult.Success -> loaded.detail
            }
        }

        val baseVersion = change.baseVersion
        val lastChangeId = detail.header.optString(ANDROID_LAST_CHANGE_ID)
        val alreadyDesired = jsonEquivalent(detail.bridgePayload, desired)
        if (baseVersion != null &&
            detail.snapshot.entityVersion != baseVersion &&
            lastChangeId != change.clientChangeId &&
            !alreadyDesired
        ) {
            return MutationResult.Conflict(detail.snapshot.entityVersion, "VERSION_CONFLICT")
        }

        if (desiredStatus == "ENTWURF" && detail.btb.optString("status").uppercase() == "FINALIZED") {
            when (
                val reopened = jsonMutation(
                    token,
                    "/api/v1/btbs/${encode(detail.snapshot.serverId)}/reopen",
                    "POST",
                    subChangeId(change.clientChangeId, "reopen"),
                    JSONObject()
                )
            ) {
                JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                is JsonMutationResult.Conflict ->
                    return MutationResult.Conflict(reopened.currentVersion, reopened.detail)
                is JsonMutationResult.Error -> return MutationResult.Error(reopened.message)
                is JsonMutationResult.Success -> Unit
            }
            detail = when (val reloaded = loadBtb(token, detail.snapshot.serverId, teamId)) {
                DetailResult.Unsupported -> return MutationResult.Unsupported
                DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
                is DetailResult.Error -> return MutationResult.Error(reloaded.message)
                is DetailResult.Success -> reloaded.detail
            }
        }

        val mergedHeader = desiredHeader(detail.header, desired, clientKey, change.clientChangeId)
        val headerNeedsUpdate =
            !jsonEquivalent(detail.header, mergedHeader) ||
                detail.btb.nullableString("work_date") != desired.nullableString("datumIso")

        if (headerNeedsUpdate) {
            val headerBody = JSONObject()
                .put("base_version", detail.snapshot.entityVersion)
                .put("work_date", desired.opt("datumIso") ?: JSONObject.NULL)
                .put("header", mergedHeader)
            combinedDateTime(desired.nullableString("datumIso"), desired.nullableString("startUhrzeit"))
                ?.let { headerBody.put("started_at", it) }
            combinedDateTime(
                desired.nullableString("endeDatumIso") ?: desired.nullableString("datumIso"),
                desired.nullableString("endeUhrzeit")
            )?.let { headerBody.put("ended_at", it) }

            when (
                val updated = jsonMutation(
                    token,
                    "/api/v1/btbs/${encode(detail.snapshot.serverId)}/header",
                    "PATCH",
                    subChangeId(change.clientChangeId, "header"),
                    headerBody
                )
            ) {
                JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                is JsonMutationResult.Conflict ->
                    return MutationResult.Conflict(updated.currentVersion, updated.detail)
                is JsonMutationResult.Error -> return MutationResult.Error(updated.message)
                is JsonMutationResult.Success -> Unit
            }
            detail = when (val reloaded = loadBtb(token, detail.snapshot.serverId, teamId)) {
                DetailResult.Unsupported -> return MutationResult.Unsupported
                DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
                is DetailResult.Error -> return MutationResult.Error(reloaded.message)
                is DetailResult.Success -> reloaded.detail
            }
        }

        if (!jsonEquivalent(detail.bridgePayload, desired)) {
            if (detail.bridgeEntryId == null) {
                val entryBody = JSONObject()
                    .put("id", deterministicEntryId(clientKey))
                    .put("section", ANDROID_SECTION)
                    .put("sort_order", 0)
                    .put("payload", desired)
                when (
                    val created = jsonMutation(
                        token,
                        "/api/v1/btbs/${encode(detail.snapshot.serverId)}/entries",
                        "POST",
                        subChangeId(change.clientChangeId, "entry-create"),
                        entryBody
                    )
                ) {
                    JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                    JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                    is JsonMutationResult.Conflict ->
                        return MutationResult.Conflict(created.currentVersion, created.detail)
                    is JsonMutationResult.Error -> return MutationResult.Error(created.message)
                    is JsonMutationResult.Success -> Unit
                }
            } else {
                val entryVersion = detail.bridgeEntryVersion
                    ?: return MutationResult.Error("Server-Version des Android-BTB-Eintrags fehlt.")
                val entryBody = JSONObject()
                    .put("base_version", entryVersion)
                    .put("sort_order", 0)
                    .put("payload", desired)
                when (
                    val updated = jsonMutation(
                        token,
                        "/api/v1/btbs/${encode(detail.snapshot.serverId)}/entries/${encode(detail.bridgeEntryId)}",
                        "PATCH",
                        subChangeId(change.clientChangeId, "entry-update"),
                        entryBody
                    )
                ) {
                    JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                    JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                    is JsonMutationResult.Conflict ->
                        return MutationResult.Conflict(updated.currentVersion, updated.detail)
                    is JsonMutationResult.Error -> return MutationResult.Error(updated.message)
                    is JsonMutationResult.Success -> Unit
                }
            }
            detail = when (val reloaded = loadBtb(token, detail.snapshot.serverId, teamId)) {
                DetailResult.Unsupported -> return MutationResult.Unsupported
                DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
                is DetailResult.Error -> return MutationResult.Error(reloaded.message)
                is DetailResult.Success -> reloaded.detail
            }
        }

        if (desiredStatus == "FERTIG" && detail.btb.optString("status").uppercase() != "FINALIZED") {
            when (
                val finalized = jsonMutation(
                    token,
                    "/api/v1/btbs/${encode(detail.snapshot.serverId)}/finalize",
                    "POST",
                    subChangeId(change.clientChangeId, "finalize"),
                    JSONObject()
                )
            ) {
                JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                is JsonMutationResult.Conflict ->
                    return MutationResult.Conflict(finalized.currentVersion, finalized.detail)
                is JsonMutationResult.Error -> return MutationResult.Error(finalized.message)
                is JsonMutationResult.Success -> Unit
            }
        }

        val finalDetail = when (val reloaded = loadBtb(token, detail.snapshot.serverId, teamId)) {
            DetailResult.Unsupported -> return MutationResult.Unsupported
            DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
            is DetailResult.Error -> return MutationResult.Error(reloaded.message)
            is DetailResult.Success -> reloaded.detail
        }
        return MutationResult.Applied(finalDetail.snapshot)
    }

    private sealed interface DetailMutationResult {
        data class Success(val detail: Detail) : DetailMutationResult
        data class Conflict(val currentVersion: Long?, val detail: String) : DetailMutationResult
        data object Unsupported : DetailMutationResult
        data object SessionInvalid : DetailMutationResult
        data class Error(val message: String) : DetailMutationResult
    }

    private fun createBtb(
        token: String,
        change: SyncCollabPendingChangeEntity,
        teamId: String,
        clientKey: String,
        desired: JSONObject,
        desiredStatus: String
    ): DetailMutationResult {
        val header = desiredHeader(JSONObject(), desired, clientKey, change.clientChangeId)
            .put(ANDROID_LIFECYCLE, desiredStatus)
        val body = JSONObject()
            .put("team_id", teamId)
            .put("work_date", desired.opt("datumIso") ?: JSONObject.NULL)
            .put("header", header)
        combinedDateTime(desired.nullableString("datumIso"), desired.nullableString("startUhrzeit"))
            ?.let { body.put("started_at", it) }
        combinedDateTime(
            desired.nullableString("endeDatumIso") ?: desired.nullableString("datumIso"),
            desired.nullableString("endeUhrzeit")
        )?.let { body.put("ended_at", it) }

        val created = jsonMutation(
            token,
            "/api/v1/worksites/${encode(change.worksiteServerId)}/btbs",
            "POST",
            subChangeId(change.clientChangeId, "create"),
            body
        )
        val serverId = when (created) {
            JsonMutationResult.Unsupported -> return DetailMutationResult.Unsupported
            JsonMutationResult.SessionInvalid -> return DetailMutationResult.SessionInvalid
            is JsonMutationResult.Conflict ->
                return DetailMutationResult.Conflict(created.currentVersion, created.detail)
            is JsonMutationResult.Error -> return DetailMutationResult.Error(created.message)
            is JsonMutationResult.Success -> created.body.optString("id").trim()
                .takeIf { it.isNotBlank() }
                ?: return DetailMutationResult.Error("Server lieferte keine BTB-ID.")
        }
        return when (val detail = loadBtb(token, serverId, teamId)) {
            DetailResult.Unsupported -> DetailMutationResult.Unsupported
            DetailResult.SessionInvalid -> DetailMutationResult.SessionInvalid
            is DetailResult.Error -> DetailMutationResult.Error(detail.message)
            is DetailResult.Success -> DetailMutationResult.Success(detail.detail)
        }
    }

    private sealed interface DetailResult {
        data class Success(val detail: Detail) : DetailResult
        data object Unsupported : DetailResult
        data object SessionInvalid : DetailResult
        data class Error(val message: String) : DetailResult
    }

    private fun loadBtb(token: String, btbId: String, expectedTeamId: String): DetailResult {
        val connection = openConnection("/api/v1/btbs/${encode(btbId)}")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return DetailResult.Error("BTB-Detail konnte nicht gelesen werden.")
                    val btb = root.optJSONObject("btb")
                        ?: return DetailResult.Error("BTB-Kopfdaten fehlen.")
                    val teamId = btb.optString("team_id").trim()
                    if (teamId.isBlank() || teamId != expectedTeamId) {
                        return DetailResult.Error("BTB-Teamzuordnung ist inkonsistent.")
                    }
                    DetailResult.Success(detailFromJson(root, btb, teamId))
                }
                HttpURLConnection.HTTP_NOT_FOUND -> DetailResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> DetailResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) DetailResult.SessionInvalid
                    else DetailResult.Error(errorMessage(body, code))
                else -> DetailResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            DetailResult.Error(error.message ?: "Server-1.0-BTB-Detail konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun detailFromJson(root: JSONObject, btb: JSONObject, teamId: String): Detail {
        val entries = root.optJSONArray("entries") ?: JSONArray()
        var bridgePayload: JSONObject? = null
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            if (entry.optString("section") == ANDROID_SECTION) {
                bridgePayload = entry.optJSONObject("payload")
                break
            }
        }

        val header = btb.optJSONObject("header") ?: JSONObject()
        val serverId = btb.optString("id").trim()
        val clientKey = bridgePayload?.optString("btbId")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: header.optString(ANDROID_CLIENT_KEY).trim().takeIf { it.isNotBlank() }
            ?: serverId

        val payload = bridgePayload?.let { JSONObject(it.toString()) }
            ?: minimalPayload(btb, header, clientKey, entries)

        if (payload.optString("btbId").isBlank()) payload.put("btbId", clientKey)
        if (payload.optString("datumIso").isBlank()) {
            btb.nullableString("work_date")?.let { payload.put("datumIso", it) }
        }
        if (payload.optString("schicht").isBlank()) {
            payload.put("schicht", header.optString("schicht").ifBlank { "TAG" })
        }
        payload.put("fotos", JSONArray())
        payload.put("pdfAuswahl", JSONObject())

        val bridgeEntry = (0 until entries.length())
            .mapNotNull { entries.optJSONObject(it) }
            .firstOrNull { it.optString("section") == ANDROID_SECTION }

        val snapshot = Snapshot(
            serverId = serverId,
            teamId = teamId,
            clientEntityKey = clientKey,
            status = if (btb.optString("status").uppercase() == "FINALIZED") "FERTIG" else "ENTWURF",
            payloadJson = payload.toString(),
            entityVersion = btb.optLong("version", 1L).coerceAtLeast(1L),
            createdAtText = btb.nullableString("created_at"),
            updatedAtText = btb.nullableString("updated_at")
        )
        return Detail(
            snapshot = snapshot,
            btb = JSONObject(btb.toString()),
            header = JSONObject(header.toString()),
            bridgeEntryId = bridgeEntry?.nullableString("id"),
            bridgeEntryVersion = bridgeEntry?.optLong("version")?.takeIf { it > 0L },
            bridgePayload = bridgePayload?.let { JSONObject(it.toString()) }
        )
    }

    private fun minimalPayload(
        btb: JSONObject,
        header: JSONObject,
        clientKey: String,
        entries: JSONArray
    ): JSONObject = JSONObject()
        .put("btbId", clientKey)
        .put("datumIso", btb.opt("work_date") ?: JSONObject.NULL)
        .put("schicht", header.optString("schicht").ifBlank { "TAG" })
        .put("projektNameSnapshot", header.opt("projektNameSnapshot") ?: JSONObject.NULL)
        .put("strecke", header.opt("strecke") ?: JSONObject.NULL)
        .put("startUhrzeit", timePart(btb.nullableString("started_at")) ?: JSONObject.NULL)
        .put("endeUhrzeit", timePart(btb.nullableString("ended_at")) ?: JSONObject.NULL)
        .put("notizen", header.opt("notizen") ?: JSONObject.NULL)
        .put("serverEntries", JSONArray(entries.toString()))
        .put("fotos", JSONArray())
        .put("pdfAuswahl", JSONObject())

    private fun timePart(value: String?): String? {
        val raw = value ?: return null
        val marker = raw.indexOf('T')
        if (marker < 0 || marker + 1 >= raw.length) return null
        return raw.substring(marker + 1).take(5).takeIf { it.length == 5 }
    }

    private fun openConnection(path: String, method: String = "GET"): HttpURLConnection =
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

    private sealed interface JsonMutationResult {
        data class Success(val body: JSONObject) : JsonMutationResult
        data class Conflict(val currentVersion: Long?, val detail: String) : JsonMutationResult
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
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
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
                HttpURLConnection.HTTP_CONFLICT -> {
                    val raw = runCatching { JSONObject(response).opt("detail") }.getOrNull()
                    val obj = raw as? JSONObject
                    JsonMutationResult.Conflict(
                        currentVersion = obj?.optLong("current_version")?.takeIf { it > 0L },
                        detail = when (raw) {
                            is String -> raw
                            is JSONObject -> raw.optString("code").ifBlank { raw.toString() }
                            else -> "CONFLICT"
                        }
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> JsonMutationResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> JsonMutationResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(response)) JsonMutationResult.SessionInvalid
                    else JsonMutationResult.Error(errorMessage(response, code))
                else -> JsonMutationResult.Error(errorMessage(response, code))
            }
        } catch (error: Exception) {
            JsonMutationResult.Error(error.message ?: "Server-1.0-BTB-Änderung ist fehlgeschlagen.")
        } finally {
            connection.disconnect()
        }
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

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() }
    }

    private fun desiredHeader(
        existing: JSONObject,
        desired: JSONObject,
        clientKey: String,
        clientChangeId: String
    ): JSONObject = JSONObject(existing.toString())
        .put(ANDROID_CLIENT_KEY, clientKey)
        .put(ANDROID_LAST_CHANGE_ID, clientChangeId)
        .put(ANDROID_LIFECYCLE, desired.optString("abschlussStatus").ifBlank {
            if (desired.optBoolean("istFertig", false) || desired.optInt("dokumentVersion", 0) > 0) "FERTIG" else "ENTWURF"
        })
        .put("schicht", desired.optString("schicht").ifBlank { "TAG" })
        .put("projektNameSnapshot", desired.opt("projektNameSnapshot") ?: JSONObject.NULL)
        .put("strecke", desired.opt("strecke") ?: JSONObject.NULL)
        .put("notizen", desired.opt("notizen") ?: JSONObject.NULL)

    private fun combinedDateTime(date: String?, time: String?): String? {
        val d = date?.trim().orEmpty()
        val t = time?.trim().orEmpty()
        if (!d.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
        if (!t.matches(Regex("\\d{1,2}:\\d{2}"))) return null
        val parts = t.split(':')
        return "$dT${parts[0].padStart(2, '0')}:${parts[1]}:00"
    }

    private fun deterministicEntryId(clientKey: String): String =
        UUID.nameUUIDFromBytes("tbhelfer-btb:$clientKey".toByteArray(Charsets.UTF_8)).toString()

    private fun subChangeId(base: String, suffix: String): String =
        (base + "-" + suffix).take(200)

    private fun jsonEquivalent(left: JSONObject?, right: JSONObject?): Boolean {
        if (left == null || right == null) return left == null && right == null
        return canonical(left) == canonical(right)
    }

    private fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = mutableListOf<String>()
            val iterator = value.keys()
            while (iterator.hasNext()) keys += iterator.next()
            keys.sorted().joinToString(prefix = "{", postfix = "}") { key ->
                JSONObject.quote(key) + ":" + canonical(value.opt(key))
            }
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") {
            canonical(value.opt(it))
        }
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    companion object {
        const val ANDROID_SECTION = "TB_HELFER_ANDROID_V1"
        const val ANDROID_CLIENT_KEY = "tb_helfer_btb_id"
        const val ANDROID_LAST_CHANGE_ID = "tb_helfer_last_change_id"
        const val ANDROID_LIFECYCLE = "tb_helfer_lifecycle"
    }
}
