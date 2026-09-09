package de.tbhelfer.server

import de.tbhelfer.datenbank.BaustellenKnotenEntity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/collab/worksites").openConnection() as HttpURLConnection).apply {
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
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val body = readBody(connection, code)
            val root = body.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val id = root?.optString("worksite_id").orEmpty()
                    if (id.isBlank()) Result.Error("Server lieferte keine Baustellen-ID.")
                    else Result.Success(
                        Created(
                            worksiteId = id,
                            groupId = root?.optString("group_id")?.takeIf { it.isNotBlank() },
                            wasCreated = root?.optBoolean("created", false) ?: false
                        )
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> Result.SessionInvalid
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Baustelle konnte nicht auf dem Server angelegt werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) connection.inputStream else connection.errorStream ?: return ""
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader -> reader.readText() }
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return detail.ifBlank { "Serverfehler ($code)." }
    }
}
