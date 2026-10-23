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
import java.security.MessageDigest

/**
 * Baustellenweites Löschen mit Server-1.0-Soft-Delete/Tombstones.
 *
 * Der kanonische Pfad wird zuerst verwendet. Ein alter 0.7.x-Server darf weiterhin den
 * bisherigen /collab-Vertrag bedienen. Lokal wird erst nach bestätigtem Server-Delete gelöscht.
 */
class WorksiteDeletionApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Tombstone(val worksiteId: String, val worksiteName: String?)

    sealed interface DeleteResult {
        data object Success : DeleteResult
        data object Unsupported : DeleteResult
        data object SessionInvalid : DeleteResult
        data class Error(val message: String) : DeleteResult
    }

    sealed interface ListResult {
        data class Success(val tombstones: List<Tombstone>) : ListResult
        data object Unsupported : ListResult
        data object SessionInvalid : ListResult
        data class Error(val message: String) : ListResult
    }

    fun delete(token: String, worksiteId: String): DeleteResult {
        return when (val canonical = deleteCanonical(token, worksiteId)) {
            DeleteResult.Unsupported -> legacyDelete(token, worksiteId)
            else -> canonical
        }
    }

    fun list(token: String): ListResult {
        return when (val canonical = listCanonical(token)) {
            ListResult.Unsupported -> legacyList(token)
            else -> canonical
        }
    }

    private fun deleteCanonical(token: String, worksiteId: String): DeleteResult {
        repeat(3) {
            val version = when (val current = loadCurrentVersion(token, worksiteId)) {
                is VersionResult.Success -> current.version
                VersionResult.AlreadyDeleted -> return DeleteResult.Success
                VersionResult.Unsupported -> return DeleteResult.Unsupported
                VersionResult.SessionInvalid -> return DeleteResult.SessionInvalid
                is VersionResult.Error -> return DeleteResult.Error(current.message)
            }

            val connection = openConnection(
                "/api/v1/worksites/${encode(worksiteId)}?base_version=$version",
                "DELETE"
            )
            try {
                authorize(connection, token)
                connection.setRequestProperty(
                    "X-Client-Change-Id",
                    "android-worksite-delete-" + sha256(worksiteId)
                )
                val code = connection.responseCode
                val body = readBody(connection, code)
                when (code) {
                    HttpURLConnection.HTTP_OK,
                    HttpURLConnection.HTTP_NO_CONTENT -> return DeleteResult.Success
                    HttpURLConnection.HTTP_CONFLICT -> Unit
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        val detail = detailMessage(body)
                        if (detail == "WORKSITE_NOT_FOUND") {
                            when (val tombstones = listCanonical(token)) {
                                is ListResult.Success -> {
                                    if (tombstones.tombstones.any { it.worksiteId == worksiteId }) {
                                        return DeleteResult.Success
                                    }
                                    return DeleteResult.Error("Server-Baustelle wurde nicht gefunden.")
                                }
                                ListResult.SessionInvalid -> return DeleteResult.SessionInvalid
                                ListResult.Unsupported -> return DeleteResult.Unsupported
                                is ListResult.Error -> return DeleteResult.Error(tombstones.message)
                            }
                        }
                        if (detail.isBlank() || detail.equals("Not Found", true)) {
                            return DeleteResult.Unsupported
                        }
                        return DeleteResult.Error(detail)
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> return DeleteResult.SessionInvalid
                    HttpURLConnection.HTTP_FORBIDDEN ->
                        return if (isInvalidDevice(body)) {
                            DeleteResult.SessionInvalid
                        } else {
                            DeleteResult.Error(errorMessage(body, code))
                        }
                    else -> return DeleteResult.Error(errorMessage(body, code))
                }
            } catch (error: Exception) {
                return DeleteResult.Error(
                    error.message ?: "Baustelle konnte auf dem Server nicht gelöscht werden."
                )
            } finally {
                connection.disconnect()
            }
        }
        return DeleteResult.Error(
            "Baustelle konnte nach mehreren parallelen Änderungen nicht gelöscht werden."
        )
    }

    private sealed interface VersionResult {
        data class Success(val version: Long) : VersionResult
        data object AlreadyDeleted : VersionResult
        data object Unsupported : VersionResult
        data object SessionInvalid : VersionResult
        data class Error(val message: String) : VersionResult
    }

    private fun loadCurrentVersion(token: String, worksiteId: String): VersionResult {
        val connection = openConnection(
            "/api/v1/worksites/${encode(worksiteId)}",
            "GET"
        )
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return VersionResult.Error("Serverantwort der Baustelle ist ungültig.")
                    val worksite = root.optJSONObject("worksite")
                        ?: return VersionResult.Error("Server lieferte keine Baustelle.")
                    val version = worksite.optLong("version", 0L)
                    if (version <= 0L) {
                        VersionResult.Error("Server lieferte keine Baustellenversion.")
                    } else {
                        VersionResult.Success(version)
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = detailMessage(body)
                    when {
                        detail == "WORKSITE_NOT_FOUND" -> {
                            when (val deletions = listCanonical(token)) {
                                is ListResult.Success ->
                                    if (deletions.tombstones.any { it.worksiteId == worksiteId }) {
                                        VersionResult.AlreadyDeleted
                                    } else {
                                        VersionResult.Error("Server-Baustelle wurde nicht gefunden.")
                                    }
                                ListResult.SessionInvalid -> VersionResult.SessionInvalid
                                ListResult.Unsupported -> VersionResult.Unsupported
                                is ListResult.Error -> VersionResult.Error(deletions.message)
                            }
                        }
                        detail.isBlank() || detail.equals("Not Found", true) ->
                            VersionResult.Unsupported
                        else -> VersionResult.Error(detail)
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> VersionResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) VersionResult.SessionInvalid
                    else VersionResult.Error(errorMessage(body, code))
                else -> VersionResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            VersionResult.Error(error.message ?: "Baustelle konnte nicht geprüft werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun listCanonical(token: String): ListResult {
        val connection = openConnection("/api/v1/worksites/deletions", "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parseTombstones(body)
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = detailMessage(body)
                    if (detail.isBlank() || detail.equals("Not Found", true)) {
                        ListResult.Unsupported
                    } else {
                        ListResult.Error(detail)
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> ListResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) ListResult.SessionInvalid
                    else ListResult.Error(errorMessage(body, code))
                else -> ListResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            ListResult.Error(error.message ?: "Baustellenlöschungen konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTombstones(body: String): ListResult {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return ListResult.Error("Serverantwort der Baustellenlöschungen ist ungültig.")
        val array = root.optJSONArray("deletions") ?: JSONArray()
        val result = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("worksite_id").trim()
                if (id.isBlank()) continue
                add(
                    Tombstone(
                        id,
                        item.nullableString("worksite_name")
                    )
                )
            }
        }
        return ListResult.Success(result)
    }

    // -------------------- Legacy 0.7.x fallback --------------------

    private fun legacyDelete(token: String, worksiteId: String): DeleteResult {
        val connection = openConnection(
            "/api/v1/collab/worksites/${encode(worksiteId)}",
            "DELETE"
        )
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> DeleteResult.Success
                HttpURLConnection.HTTP_NOT_FOUND -> DeleteResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        DeleteResult.SessionInvalid else DeleteResult.Error(errorMessage(body, code))
                else -> DeleteResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            DeleteResult.Error(error.message ?: "Baustelle konnte auf dem Server nicht gelöscht werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun legacyList(token: String): ListResult {
        val connection = openConnection("/api/v1/collab/worksite-deletions", "GET")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parseTombstones(body)
                HttpURLConnection.HTTP_NOT_FOUND -> ListResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        ListResult.SessionInvalid else ListResult.Error(errorMessage(body, code))
                else -> ListResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            ListResult.Error(error.message ?: "Baustellenlöschungen konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String, method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 30_000
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

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun JSONObject.nullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotBlank() }
    }
}

