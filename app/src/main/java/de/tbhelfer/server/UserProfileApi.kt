package de.tbhelfer.server

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Persoenliche Kontaktdaten des aktuell aktivierten Benutzers. */
class UserProfileApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Profile(
        val userId: String,
        val fullName: String?,
        val email: String?,
        val phone: String?,
        val profileVersion: Long
    )

    sealed interface Result {
        data class Success(val profile: Profile) : Result
        data object SchemaRequired : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun load(token: String): Result = request(token, "GET", null)

    fun save(token: String, email: String?, phone: String?): Result = request(
        token,
        "PUT",
        JSONObject()
            .put("email", email?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("phone", phone?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
    )

    private fun request(token: String, method: String, body: JSONObject?): Result {
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/user/profile").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val text = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parseProfile(text)?.let(Result::Success)
                    ?: Result.Error("Nutzerprofil konnte nicht gelesen werden.")
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, errorDetail(text)))
                        Result.SessionInvalid else Result.Error("Profilzugriff verweigert (HTTP $code).")
                HttpURLConnection.HTTP_CONFLICT -> {
                    val detail = errorDetail(text)
                    when (detail) {
                        "USER_PROFILE_SCHEMA_REQUIRED" -> Result.SchemaRequired
                        "USER_EMAIL_ALREADY_USED" -> Result.Error("Diese E-Mail-Adresse wird bereits verwendet.")
                        else -> Result.Error(detail.ifBlank { "Nutzerprofil konnte nicht gespeichert werden." })
                    }
                }
                else -> Result.Error(errorDetail(text).ifBlank { "Serverfehler ($code)." })
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Nutzerprofil konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseProfile(body: String): Profile? {
        val root = runCatching { JSONObject(body).optJSONObject("profile") }.getOrNull() ?: return null
        val userId = root.optString("user_id").trim()
        if (userId.isBlank()) return null
        return Profile(
            userId = userId,
            fullName = root.nullableString("full_name"),
            email = root.nullableString("email"),
            phone = root.nullableString("phone"),
            profileVersion = root.optLong("profile_version", 1L).coerceAtLeast(1L)
        )
    }

    private fun errorDetail(body: String): String =
        runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()

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

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() }
    }
}

