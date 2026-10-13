package de.tbhelfer.server

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SyncCatalogApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class RouteItem(
        val id: String,
        val routeNumber: String,
        val name: String?,
        val description: String?,
        val sectionCount: Long,
        val objectCount: Long,
        val entityVersion: Long = 0
    )

    data class StationItem(
        val id: String,
        val stationCode: String?,
        val name: String,
        val shortName: String?,
        val description: String?,
        val objectCount: Long,
        val entityVersion: Long = 0
    )

    data class Catalog(
        val serverVersion: String?,
        val routes: List<RouteItem>,
        val stations: List<StationItem>
    )

    sealed interface Result {
        data class Success(val catalog: Catalog) : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun loadCatalog(token: String): Result {
        val connection = openConnection("/api/v1/catalog")
        return try {
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            val response = readBody(connection, code)
            val json = response.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }

            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    if (json == null) {
                        Result.Error("Serverantwort konnte nicht gelesen werden.")
                    } else {
                        Result.Success(parseCatalog(json))
                    }
                }

                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> Result.SessionInvalid

                else -> {
                    val detail = json?.optString("detail").orEmpty()
                    Result.Error(detail.ifBlank { "Serverfehler ($code)." })
                }
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCatalog(json: JSONObject): Catalog {
        val routesJson = json.optJSONArray("routes")
        val routes = buildList {
            if (routesJson != null) {
                for (index in 0 until routesJson.length()) {
                    val item = routesJson.optJSONObject(index) ?: continue
                    add(
                        RouteItem(
                            id = item.optString("id"),
                            routeNumber = item.optString("route_number"),
                            name = item.optNullableString("name"),
                            description = item.optNullableString("description"),
                            sectionCount = item.optLong("section_count", 0),
                            objectCount = item.optLong("object_count", 0),
                            entityVersion = item.optLong("current_version", 0)
                        )
                    )
                }
            }
        }

        val stationsJson = json.optJSONArray("stations")
        val stations = buildList {
            if (stationsJson != null) {
                for (index in 0 until stationsJson.length()) {
                    val item = stationsJson.optJSONObject(index) ?: continue
                    add(
                        StationItem(
                            id = item.optString("id"),
                            stationCode = item.optNullableString("station_code"),
                            name = item.optString("name"),
                            shortName = item.optNullableString("short_name"),
                            description = item.optNullableString("description"),
                            objectCount = item.optLong("object_count", 0),
                            entityVersion = item.optLong("current_version", 0)
                        )
                    )
                }
            }
        }

        return Catalog(
            serverVersion = json.optNullableString("server_version"),
            routes = routes,
            stations = stations
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun openConnection(path: String): HttpURLConnection {
        return (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
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
