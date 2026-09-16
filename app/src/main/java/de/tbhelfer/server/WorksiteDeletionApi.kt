package de.tbhelfer.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Schema-13-Endpunkte fuer bewusstes baustellenweites Loeschen. */
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
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/collab/worksites/${encode(worksiteId)}")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 12_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> DeleteResult.Success
                HttpURLConnection.HTTP_NOT_FOUND -> DeleteResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> DeleteResult.SessionInvalid
                else -> DeleteResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            DeleteResult.Error(error.message ?: "Baustelle konnte auf dem Server nicht geloescht werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun list(token: String): ListResult {
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/collab/worksite-deletions")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return ListResult.Error("Serverantwort der Baustellenloeschungen ist ungueltig.")
                    val array = root.optJSONArray("deletions") ?: JSONArray()
                    val result = buildList {
                        for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i) ?: continue
                            val id = item.optString("worksite_id").trim()
                            if (id.isBlank()) continue
                            add(Tombstone(id, item.optString("worksite_name").trim().takeIf { it.isNotBlank() }))
                        }
                    }
                    ListResult.Success(result)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> ListResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> ListResult.SessionInvalid
                else -> ListResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            ListResult.Error(error.message ?: "Baustellenloeschungen konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return detail.ifBlank { "Serverfehler HTTP $code" }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
