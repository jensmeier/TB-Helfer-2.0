package de.tbhelfer.server

import de.tbhelfer.datenbank.BaustellenKnotenEntity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Erstellt die erste Server-Baustelle direkt aus einer lokalen Hauptbaustelle. */
class WorksiteProvisioningApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Created(
        val worksiteId: String,
        val groupId: String?,
        val wasCreated: Boolean
    )

    sealed interface Result {
        data class Success(val value: Created) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun create(token: String, local: BaustellenKnotenEntity): Result {
        return when (val canonical = createCanonical(token, local)) {
            Result.Unsupported -> legacyCreate(token, local)
            else -> canonical
        }
    }

    private fun createCanonical(token: String, local: BaustellenKnotenEntity): Result {
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/worksites")
            .let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty(
                "X-Client-Change-Id",
                "android-worksite-create-" + stableLocalKey(local)
            )
        }
        return try {
            val payload = JSONObject()
                .put("name", local.hauptbaustellenName)
                .putNullable("designation", local.knotenName)
                .putNullable("route_number", local.streckenkennung)
                .putNullable("station_name", local.bahnhofName)
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(payload.toString())
            }

            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK,
                HttpURLConnection.HTTP_CREATED -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return Result.Error("Serverantwort der Baustellenanlage ist ungültig.")
                    val id = root.optString("id").trim()
                    if (id.isBlank()) {
                        Result.Error("Server lieferte keine Baustellen-ID.")
                    } else {
                        Result.Success(
                            Created(
                                worksiteId = id,
                                groupId = resolveTeamId(token, id),
                                wasCreated = true
                            )
                        )
                    }
                }
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
            Result.Error(error.message ?: "Baustelle konnte nicht auf dem Server angelegt werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveTeamId(token: String, worksiteId: String): String? =
        when (val contexts = TeamManagementApi(baseUrl).loadWorksiteContexts(token)) {
            is TeamManagementApi.WorksiteContextResult.Success ->
                contexts.items.firstOrNull { it.worksiteId == worksiteId }?.groupId
            else -> null
        }

    // -------------------- Legacy 0.7.x fallback --------------------

    private fun legacyCreate(token: String, local: BaustellenKnotenEntity): Result {
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/collab/worksites")
            .let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            val payload = JSONObject()
                .put("client_worksite_key", "android:${local.id}:${local.altBaustelleId}")
                .put("name", local.hauptbaustellenName)
                .put("designation", local.knotenName ?: "")
                .put("route_number", local.streckenkennung ?: "")
                .put("station_name", local.bahnhofName ?: "")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(payload.toString())
            }
            val code = connection.responseCode
            val body = readBody(connection, code)
            val root = body.takeIf { it.isNotBlank() }
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val id = root?.optString("worksite_id").orEmpty()
                    if (id.isBlank()) {
                        Result.Error("Server lieferte keine Baustellen-ID.")
                    } else {
                        Result.Success(
                            Created(
                                worksiteId = id,
                                groupId = root?.optString("group_id")
                                    ?.takeIf { it.isNotBlank() },
                                wasCreated = root?.optBoolean("created", false) ?: false
                            )
                        )
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        Result.SessionInvalid else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Baustelle konnte nicht auf dem Server angelegt werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun stableLocalKey(local: BaustellenKnotenEntity): String {
        val seed = "${local.id}|${local.altBaustelleId}"
        return MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: return ""
        }
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
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

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)
}

