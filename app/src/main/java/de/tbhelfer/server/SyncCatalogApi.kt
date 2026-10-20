package de.tbhelfer.server

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

    sealed interface InventoryResult {
        data class Success(val inventory: CatalogInventory) : InventoryResult
        data object Unsupported : InventoryResult
        data object SessionInvalid : InventoryResult
        data class Error(val message: String) : InventoryResult
    }

    fun loadInventory(token: String, routeNumber: String? = null, stationCode: String? = null): InventoryResult {
        val route = routeNumber?.trim()?.takeIf { it.isNotBlank() }
        val station = stationCode?.trim()?.takeIf { it.isNotBlank() }
        if (route == null && station == null) return InventoryResult.Error("Bitte Strecke oder Bahnhof mit Kürzel auswählen.")
        if ((route?.length ?: 0) > 80 || (station?.length ?: 0) > 50)
            return InventoryResult.Error("Strecken- oder Bahnhofkennung ist zu lang.")
        val query = listOfNotNull(route?.let { "route_number=" + URLEncoder.encode(it, "UTF-8") },
            station?.let { "station_code=" + URLEncoder.encode(it, "UTF-8") }).joinToString("&")
        var connection: HttpURLConnection? = null
        return try {
            val request = openConnection("/api/v1/catalog/inventory?$query")
            connection = request
            request.setRequestProperty("Authorization", "Bearer $token")
            request.setRequestProperty("Accept", "application/json")
            val code = request.responseCode
            val response = readBody(request, code)
            val json = response.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    if (json == null || json.optString("scope") != "GLOBAL" || json.optJSONArray("rows") == null)
                        InventoryResult.Error("Bestandsantwort konnte nicht gelesen werden.")
                    else InventoryResult.Success(parseInventory(json))
                }
                404, 405, 501 -> InventoryResult.Unsupported
                401, 403 -> InventoryResult.SessionInvalid
                else -> InventoryResult.Error(json?.optNullableString("detail") ?: "Bestand konnte nicht geladen werden ($code).")
            }
        } catch (_: Exception) {
            InventoryResult.Error("Bestand konnte nicht geladen werden. Bitte Verbindung prüfen und erneut versuchen.")
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseInventory(json: JSONObject): CatalogInventory {
        val items = requireNotNull(json.optJSONArray("rows"))
        val rows = buildList {
            for (index in 0 until items.length()) {
                val item = requireNotNull(items.optJSONObject(index))
                val parts = item.optJSONArray("segments")
                val segments = buildList {
                    for (i in 0 until (parts?.length() ?: 0)) {
                        val part = requireNotNull(parts?.optJSONObject(i))
                        add(CatalogInventorySegment(part.optNullableString("status") ?: "UNBEKANNT",
                            part.finiteNumber("km_from_m"), part.finiteNumber("km_to_m")))
                    }
                }
                add(CatalogInventoryRow(
                    id = requireNotNull(item.optNullableString("id")),
                    kind = item.optNullableString("kind") ?: "UNBEKANNT",
                    label = item.optNullableString("label") ?: "Weitere Daten",
                    routeNumber = item.optNullableString("route_number"), stationCode = item.optNullableString("station_code"),
                    stationName = item.optNullableString("station_name"), trackKey = item.optNullableString("track_key"),
                    objectKey = item.optNullableString("object_key"), status = item.optNullableString("status") ?: "UNBEKANNT",
                    segments = segments
                ))
            }
        }
        return CatalogInventory(rows)
    }

    private fun JSONObject.finiteNumber(key: String): Double? = (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

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

