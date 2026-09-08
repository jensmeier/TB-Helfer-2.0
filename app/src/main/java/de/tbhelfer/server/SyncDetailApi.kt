package de.tbhelfer.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SyncDetailApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class SectionItem(
        val id: String,
        val routeId: String?,
        val designation: String?,
        val kmFromM: Double?,
        val kmToM: Double?,
        val kmMinM: Double?,
        val kmMaxM: Double?,
        val dataStatus: String?,
        val geometryJson: String?,
        val entityVersion: Long
    )

    data class ObjectItem(
        val id: String,
        val routeId: String?,
        val stationId: String?,
        val objectType: String,
        val designation: String?,
        val kmM: Double?,
        val dataStatus: String?,
        val geometryJson: String?,
        val propertiesJson: String?,
        val entityVersion: Long
    )

    sealed interface Result<out T> {
        data class Success<T>(val value: T) : Result<T>
        data object SessionInvalid : Result<Nothing>
        data class Error(val message: String) : Result<Nothing>
    }

    fun loadRouteSections(token: String, routeId: String): Result<List<SectionItem>> =
        loadList("/api/v1/sync/routes/$routeId/sections", token, "sections") { item ->
            SectionItem(
                id = item.optString("id"),
                routeId = item.optNullableString("route_id"),
                designation = item.optNullableString("designation"),
                kmFromM = item.optNullableDouble("km_from_m"),
                kmToM = item.optNullableDouble("km_to_m"),
                kmMinM = item.optNullableDouble("km_min_m"),
                kmMaxM = item.optNullableDouble("km_max_m"),
                dataStatus = item.optNullableString("data_status"),
                geometryJson = item.optJsonText("geom") ?: item.optJsonText("geometry"),
                entityVersion = item.optVersion()
            )
        }

    fun loadStationObjects(token: String, stationId: String): Result<List<ObjectItem>> =
        loadList("/api/v1/sync/stations/$stationId/objects", token, "objects") { item ->
            ObjectItem(
                id = item.optString("id"),
                routeId = item.optNullableString("route_id"),
                stationId = item.optNullableString("station_id"),
                objectType = item.optString("object_type").ifBlank { "OBJEKT" },
                designation = item.optNullableString("designation"),
                kmM = item.optNullableDouble("km_m"),
                dataStatus = item.optNullableString("data_status"),
                geometryJson = item.optJsonText("geom") ?: item.optJsonText("geometry"),
                propertiesJson = item.optJsonText("properties"),
                entityVersion = item.optVersion()
            )
        }

    private fun <T> loadList(
        path: String,
        token: String,
        preferredArrayName: String,
        mapper: (JSONObject) -> T
    ): Result<List<T>> {
        val connection = openConnection(path)
        return try {
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            val response = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val array = parseArray(response, preferredArrayName)
                        ?: return Result.Error("Serverantwort konnte nicht gelesen werden.")
                    val result = buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            if (item.optString("id").isBlank()) continue
                            add(mapper(item))
                        }
                    }
                    Result.Success(result)
                }

                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> Result.SessionInvalid

                else -> {
                    val detail = runCatching { JSONObject(response).optString("detail") }.getOrNull().orEmpty()
                    Result.Error(detail.ifBlank { "Serverfehler ($code)." })
                }
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseArray(body: String, preferredArrayName: String): JSONArray? {
        val text = body.trim()
        if (text.isBlank()) return JSONArray()
        if (text.startsWith("[")) return runCatching { JSONArray(text) }.getOrNull()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return root.optJSONArray(preferredArrayName)
            ?: root.optJSONArray("items")
            ?: root.optJSONArray("data")
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()
    }

    private fun JSONObject.optVersion(): Long = when {
        has("current_version") && !isNull("current_version") -> optLong("current_version", 0)
        has("entity_version") && !isNull("entity_version") -> optLong("entity_version", 0)
        else -> optLong("version", 0)
    }

    private fun JSONObject.optJsonText(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is JSONObject, is JSONArray -> value.toString()
            is String -> value.takeIf { it.isNotBlank() }
            else -> value?.toString()?.takeIf { it.isNotBlank() }
        }
    }

    private fun openConnection(path: String): HttpURLConnection {
        return (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
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
