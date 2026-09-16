package de.tbhelfer.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Schema-13-Transport fuer abgeleitete mobile Streckendaten einer Baustelle.
 * Rohdateien (TRA/GRA/KF/KOR/...) werden hier absichtlich nie uebertragen.
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
        val currentVersion: Long = 0L
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

    fun load(token: String, worksiteId: String): Result {
        val connection = open(worksiteId, "GET", token)
        return try {
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parse(body)
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> Result.SessionInvalid
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Streckendaten konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    fun replace(token: String, worksiteId: String, datasets: List<Dataset>): PutResult {
        val connection = open(worksiteId, "PUT", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            val array = JSONArray()
            datasets.forEach { item ->
                array.put(
                    JSONObject()
                        .put("dataset_key", item.datasetKey)
                        .put("dataset_kind", item.datasetKind)
                        .put("route_key", item.routeKey ?: JSONObject.NULL)
                        .put("quelle_datei", item.quelleDatei)
                        .put("achse_name", item.achseName)
                        .put("payload", runCatching { JSONObject(item.payloadJson) }.getOrElse { JSONObject() })
                )
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(JSONObject().put("datasets", array).toString())
            }
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> PutResult.Success
                HttpURLConnection.HTTP_NOT_FOUND -> PutResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> PutResult.SessionInvalid
                else -> PutResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            PutResult.Error(error.message ?: "Streckendaten konnten nicht hochgeladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(worksiteId: String, method: String, token: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + "/api/v1/collab/worksites/${encode(worksiteId)}/track-data")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 90_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun parse(body: String): Result {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return Result.Error("Serverantwort der Streckendaten ist ungueltig.")
        val array = root.optJSONArray("datasets") ?: JSONArray()
        val datasets = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val key = item.optString("dataset_key").trim()
                if (key.isBlank()) continue
                add(
                    Dataset(
                        datasetKey = key,
                        datasetKind = item.optString("dataset_kind", "AXIS").trim().uppercase(),
                        routeKey = item.optString("route_key").trim().takeIf { it.isNotBlank() },
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
