package de.tbhelfer.server

import de.tbhelfer.server.recovery.RecoveryHttp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Existing shared catalogue contract. Failed/conflicting writes are not rebased. */
class SurveySyncApi {
    class Failure(val status: Int, val detail: JSONObject?, message: String) : java.io.IOException(message)
    fun request(token: String, path: String, body: JSONObject? = null): JSONObject {
        val connection = RecoveryHttp.open(URL(ServerConfig.BASE_URL.trimEnd('/') + path))
        connection.requestMethod = if (body == null) "GET" else "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 90_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                body.optString("client_change_id").takeIf { it.isNotBlank() }?.let {
                    connection.setRequestProperty("X-Client-Change-Id", it)
                }
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) throw Failure(code, json.optJSONObject("detail"),
                json.opt("detail")?.toString() ?: "Vermessungsabgleich HTTP $code")
            require(text.isNotBlank()) { "Leere Vermessungsantwort." }
            return json
        } finally { connection.disconnect() }
    }
    fun resolve(token: String, signature: String): JSONObject =
        request(token, "/api/v1/survey-catalog/km-sync/resolve?source_signature=${encode(signature)}")
    fun state(token: String, axis: String): JSONObject =
        request(token, "/api/v1/survey-catalog/axes/${encode(axis)}/km-sync")
    fun save(token: String, axis: String, body: JSONObject): JSONObject =
        request(token, "/api/v1/survey-catalog/axes/${encode(axis)}/km-sync", body)
    companion object {
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
