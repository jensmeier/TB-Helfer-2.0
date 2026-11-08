package de.tbhelfer.server

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
import java.security.MessageDigest

/**
 * Transport für abgeleitete mobile Streckendaten.
 *
 * Server 1.0 wird über den kanonischen track_data_v1-Vertrag reconciled. Ein alter 0.7.x-Server
 * ohne diesen Endpunkt wird nur noch gelesen; schreibende Bulk-Ersetzungen sind gesperrt. Rohdateien (TRA/GRA/KF/KOR/...) werden hier ausdrücklich nie übertragen.
 */
class WorksiteTrackDataApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Dataset(
        val datasetKey: String,
        val datasetKind: String,
        val routeKey: String?,
        val quelleDatei: String,
        val achseName: String,
        val payloadJson: String,
        val currentVersion: Long = 0L,
        val serverId: String? = null,
        val routeNumber: String? = null,
        val trackKey: String? = null,
        val localAxisId: Long? = null
    )

    sealed interface Result {
        data class Success(val datasets: List<Dataset>) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    sealed interface PutResult {
        data object Success : PutResult
        data object Unsupported : PutResult
        data object SessionInvalid : PutResult
        data class Error(val message: String) : PutResult
    }

    fun load(token: String, worksiteId: String): Result =
        when (val canonical = loadCanonical(token, worksiteId)) {
            Result.Unsupported -> legacyLoad(token, worksiteId)
            else -> canonical
        }

    fun replace(token: String, worksiteId: String, datasets: List<Dataset>): PutResult {
        val current = when (val result = loadCanonical(token, worksiteId)) {
            Result.Unsupported -> return PutResult.Unsupported
            Result.SessionInvalid -> return PutResult.SessionInvalid
            is Result.Error -> return PutResult.Error(result.message)
            is Result.Success -> result.datasets
        }
        return when (val result = reconcileCanonical(token, worksiteId, current, datasets)) {
            ReconcileResult.Success -> PutResult.Success
            ReconcileResult.Unsupported -> PutResult.Unsupported
            ReconcileResult.SessionInvalid -> PutResult.SessionInvalid
            ReconcileResult.Conflict -> PutResult.Error("Streckendaten wurden parallel geändert. Beide Fassungen bleiben erhalten; bitte Konflikt prüfen.")
            is ReconcileResult.Error -> PutResult.Error(result.message)
        }
    }

    /** Canonical business state, excluding transport version and server ID. */
    fun fingerprint(item: Dataset): String {
        val payload = enrichedPayload(item)
        return canonical(JSONObject().put("kind", item.datasetKind.uppercase())
            .put("route", desiredRouteNumber(item, payload) ?: JSONObject.NULL)
            .put("track", desiredTrackKey(item, payload) ?: JSONObject.NULL).put("payload", payload))
    }

    private fun loadCanonical(token: String, worksiteId: String): Result {
        val connection = openConnection(
            "/api/v1/worksites/${encode(worksiteId)}/track-data",
            "GET"
        )
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parseCanonical(body)
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = detailMessage(body)
                    if (detail.isBlank() || detail.equals("Not Found", true)) {
                        Result.Unsupported
                    } else {
                        Result.Error(detail)
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> Result.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) Result.SessionInvalid
                    else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Streckendaten konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCanonical(body: String): Result {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return Result.Error("Serverantwort der Streckendaten ist ungültig.")
        val array = root.optJSONArray("track_data") ?: return Result.Error("Unvollständiger Streckendaten-Snapshot; lokale Daten bleiben erhalten.")
        val datasets = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val key = item.optString("dataset_key").trim()
                val id = item.optString("id").trim()
                require(key.isNotBlank() && id.isNotBlank()) { "Streckendatensatz ohne Identität." }

                val payload = item.optJSONObject("payload")
                    ?.let { JSONObject(it.toString()) }
                    ?: JSONObject()
                val kind = item.optString("dataset_kind", "AXIS").trim().uppercase()
                val routeNumber = item.nullableString("route_number")
                val trackKey = item.nullableString("track_key")

                add(
                    Dataset(
                        datasetKey = key,
                        datasetKind = kind,
                        routeKey = routeNumber?.let { route ->
                            trackKey?.let { "$route-$it" } ?: route
                        },
                        quelleDatei = payload.nullableString("quelle_datei")
                            ?: if (kind == "STRECKENPUNKTE") "ANDROID_STRECKENPUNKTE" else "",
                        achseName = payload.nullableString("name")
                            ?: if (kind == "STRECKENPUNKTE") "STRECKENPUNKTE" else key,
                        payloadJson = payload.toString(),
                        currentVersion = item.optLong("version", 1L).coerceAtLeast(1L),
                        serverId = id,
                        routeNumber = routeNumber,
                        trackKey = trackKey
                    )
                )
            }
        }
        return Result.Success(datasets)
    }

    private sealed interface ReconcileResult {
        data object Success : ReconcileResult
        data object Unsupported : ReconcileResult
        data object SessionInvalid : ReconcileResult
        data object Conflict : ReconcileResult
        data class Error(val message: String) : ReconcileResult
    }

    private fun reconcileCanonical(
        token: String,
        worksiteId: String,
        current: List<Dataset>,
        desired: List<Dataset>
    ): ReconcileResult {
        val currentByKey = current.associateBy { it.datasetKey }
        require(desired.map { it.datasetKey }.distinct().size == desired.size) { "Doppelte Streckendaten-Identität." }

        for (item in desired) {
            val server = currentByKey[item.datasetKey]
            val bodyPayload = enrichedPayload(item)
            val routeNumber = desiredRouteNumber(item, bodyPayload)
            val trackKey = desiredTrackKey(item, bodyPayload)

            if (
                server != null &&
                server.datasetKind.equals(item.datasetKind, true) &&
                server.routeNumber == routeNumber &&
                server.trackKey == trackKey &&
                jsonEquivalent(server.payloadJson, bodyPayload.toString())
            ) {
                continue
            }

            val body = JSONObject()
                .put("dataset_kind", item.datasetKind.uppercase())
                .put("route_number", routeNumber ?: JSONObject.NULL)
                .put("track_key", trackKey ?: JSONObject.NULL)
                .put("payload", bodyPayload)

            val mutation = if (server == null) {
                if (item.currentVersion > 0L) return ReconcileResult.Conflict
                body.put("dataset_key", item.datasetKey)
                sendMutation(
                    token,
                    "/api/v1/worksites/${encode(worksiteId)}/track-data",
                    "POST",
                    mutationId(worksiteId, "create", item.datasetKey, 0L, body),
                    body
                )
            } else {
                val id = server.serverId
                    ?: return ReconcileResult.Error("Server-ID für Streckendatensatz fehlt.")
                // The caller supplies the version last applied locally. Never rebase
                // a dirty edit onto a freshly fetched server version after a conflict.
                if (item.currentVersion < 1L || item.currentVersion != server.currentVersion ||
                    item.serverId != id) return ReconcileResult.Conflict
                body.put("base_version", item.currentVersion)
                sendMutation(
                    token,
                    "/api/v1/track-data/${encode(id)}",
                    "PATCH",
                    mutationId(
                        worksiteId,
                        "update",
                        item.datasetKey,
                        server.currentVersion,
                        body
                    ),
                    body
                )
            }

            when (mutation) {
                MutationResult.Unsupported -> return ReconcileResult.Unsupported
                MutationResult.SessionInvalid -> return ReconcileResult.SessionInvalid
                MutationResult.Conflict -> return ReconcileResult.Conflict
                is MutationResult.Error -> {
                    if (
                        mutation.message == "TRACK_DATA_NOT_FOUND" ||
                        mutation.message == "TRACK_DATA_KEY_EXISTS"
                    ) {
                        return ReconcileResult.Conflict
                    }
                    return ReconcileResult.Error(mutation.message)
                }
                MutationResult.Success -> Unit
            }
        }

        // Missing local items are not deletion commands. Dedicated, versioned
        // deletion actions must carry an explicit tombstone; never prune a snapshot.

        return ReconcileResult.Success
    }

    private sealed interface MutationResult {
        data object Success : MutationResult
        data object Unsupported : MutationResult
        data object SessionInvalid : MutationResult
        data object Conflict : MutationResult
        data class Error(val message: String) : MutationResult
    }

    private fun sendMutation(
        token: String,
        path: String,
        method: String,
        clientChangeId: String,
        body: JSONObject
    ): MutationResult {
        val connection = openConnection(path, method).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            authorize(connection, token)
            connection.setRequestProperty("X-Client-Change-Id", clientChangeId)
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(body.toString())
            }
            val code = connection.responseCode
            val response = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK,
                HttpURLConnection.HTTP_CREATED,
                HttpURLConnection.HTTP_NO_CONTENT -> MutationResult.Success
                HttpURLConnection.HTTP_CONFLICT -> MutationResult.Conflict
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = detailMessage(response)
                    if (detail.isBlank() || detail.equals("Not Found", true)) {
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
                error.message ?: "Streckendaten konnten nicht zum Server übertragen werden."
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun enrichedPayload(item: Dataset): JSONObject {
        val payload = runCatching { JSONObject(item.payloadJson) }.getOrElse { JSONObject() }
        if (!payload.has("quelle_datei") && item.quelleDatei.isNotBlank()) {
            payload.put("quelle_datei", item.quelleDatei)
        }
        if (!payload.has("name") && item.achseName.isNotBlank()) {
            payload.put("name", item.achseName)
        }
        return payload
    }

    private fun desiredRouteNumber(item: Dataset, payload: JSONObject): String? =
        item.routeNumber
            ?: payload.nullableString("strecke")
            ?: item.routeKey?.substringBefore('-')?.takeIf { it.isNotBlank() }

    private fun desiredTrackKey(item: Dataset, payload: JSONObject): String? =
        item.trackKey ?: payload.nullableString("gleis")

    private fun mutationId(
        worksiteId: String,
        operation: String,
        datasetKey: String,
        baseVersion: Long,
        body: JSONObject
    ): String {
        val seed = listOf(
            worksiteId,
            operation,
            datasetKey,
            baseVersion.toString(),
            canonical(body)
        ).joinToString("|")
        return "android-track-" + sha256(seed)
    }

    private fun jsonEquivalent(left: String, right: String): Boolean {
        val leftJson = runCatching { JSONObject(left) }.getOrNull() ?: return false
        val rightJson = runCatching { JSONObject(right) }.getOrNull() ?: return false
        return canonical(leftJson) == canonical(rightJson)
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
        is Number -> java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // -------------------- Legacy 0.7.x fallback --------------------

    private fun legacyLoad(token: String, worksiteId: String): Result {
        val connection = legacyOpen(worksiteId, "GET", token)
        return try {
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parseLegacy(body)
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        Result.SessionInvalid else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Streckendaten konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLegacy(body: String): Result {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return Result.Error("Serverantwort der Streckendaten ist ungültig.")
        val array = root.optJSONArray("datasets") ?: JSONArray()
        val datasets = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val key = item.optString("dataset_key").trim()
                if (key.isBlank()) continue
                add(
                    Dataset(
                        datasetKey = key,
                        datasetKind = item.optString("dataset_kind", "AXIS").trim().uppercase(),
                        routeKey = item.nullableString("route_key"),
                        quelleDatei = item.optString("quelle_datei"),
                        achseName = item.optString("achse_name"),
                        payloadJson = item.optJSONObject("payload")?.toString() ?: "{}",
                        currentVersion = item.optLong("current_version", 0L)
                    )
                )
            }
        }
        return Result.Success(datasets)
    }

    private fun legacyOpen(
        worksiteId: String,
        method: String,
        token: String
    ): HttpURLConnection =
        openConnection(
            "/api/v1/collab/worksites/${encode(worksiteId)}/track-data",
            method
        ).apply { authorize(this, token) }

    private fun openConnection(path: String, method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 90_000
            useCaches = false
        }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        if (code == HttpURLConnection.HTTP_NO_CONTENT) return ""
        val stream: InputStream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: return ""
        }
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use {
            it.readText()
        }
    }

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

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun JSONObject.nullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotBlank() }
    }
}

