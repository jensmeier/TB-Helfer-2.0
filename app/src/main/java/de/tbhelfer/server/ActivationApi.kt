package de.tbhelfer.server

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ActivationApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    sealed interface LoginResult {
        data class Success(val token: String, val userName: String?) : LoginResult
        data object AlreadyBound : LoginResult
        data object InvalidKey : LoginResult
        data object UserDisabled : LoginResult
        data object DeviceInUse : LoginResult
        data class Error(val message: String) : LoginResult
    }

    enum class SessionState {
        VALID,
        REVOKED,
        OFFLINE
    }

    data class SessionCheck(
        val state: SessionState,
        val userName: String? = null
    )

    fun activate(
        key: String,
        deviceUid: String,
        deviceName: String,
        osVersion: String,
        appVersion: String
    ): LoginResult {
        val connection = openConnection("/api/v1/device/login", "POST")
        return try {
            val body = JSONObject()
                .put("key", key.trim())
                .put("device_uid", deviceUid)
                .put("device_name", deviceName)
                .put("os_version", osVersion)
                .put("app_version", appVersion)
                .toString()

            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val response = readBody(connection, code)
            val json = response.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            val detail = json?.optString("detail").orEmpty()

            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val token = json?.optString("token").orEmpty()
                    if (token.isBlank()) {
                        LoginResult.Error("Serverantwort enthält kein Geräte-Token.")
                    } else {
                        val userName = json?.optJSONObject("user")?.optString("name")
                        LoginResult.Success(token, userName)
                    }
                }
                HttpURLConnection.HTTP_CONFLICT -> when (detail) {
                    "DEVICE_ALREADY_BOUND" -> LoginResult.AlreadyBound
                    "DEVICE_IN_USE" -> LoginResult.DeviceInUse
                    else -> LoginResult.Error(detail.ifBlank { "Gerätekonflikt." })
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> LoginResult.InvalidKey
                HttpURLConnection.HTTP_FORBIDDEN -> LoginResult.UserDisabled
                else -> LoginResult.Error(detail.ifBlank { "Serverfehler ($code)." })
            }
        } catch (error: Exception) {
            LoginResult.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Prüft den vorhandenen Geräte-Token und übernimmt nur den nicht geheimen Anzeigenamen.
     * Damit erhalten auch bereits früher aktivierte Geräte nach einem App-Update einen lokalen
     * Namen-Fallback für BTB-Anzeige/Export, ohne erneut aktiviert werden zu müssen.
     */
    fun checkSessionInfo(token: String): SessionCheck {
        val connection = openConnection("/api/v1/device/me", "GET")
        return try {
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val body = readBody(connection, code)
                    val json = body.takeIf { it.isNotBlank() }
                        ?.let { runCatching { JSONObject(it) }.getOrNull() }
                    val userName = json?.optJSONObject("user")
                        ?.optString("name")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    SessionCheck(SessionState.VALID, userName)
                }
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    val body = readBody(connection, code)
                    val detail = runCatching { JSONObject(body).optString("detail") }.getOrDefault("")
                    SessionCheck(if (DeviceAuthorization.sessionInvalid(code, detail))
                        SessionState.REVOKED else SessionState.OFFLINE)
                }
                else -> SessionCheck(SessionState.OFFLINE)
            }
        } catch (_: Exception) {
            SessionCheck(SessionState.OFFLINE)
        } finally {
            connection.disconnect()
        }
    }

    fun checkSession(token: String): SessionState = checkSessionInfo(token).state

    private fun openConnection(path: String, method: String): HttpURLConnection {
        return (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            useCaches = false
        }
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: return ""
        }
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
}
