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

/**
 * Read bridge for canonical Server-1.0 BTBs.
 *
 * Server 1.0 owns the team lifecycle (one active BTB per team). Android maps a
 * canonical BTB snapshot into the existing collaboration cache so the established
 * local BTB import path can be reused without creating a parallel storage model.
 */
class Server1BtbApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Snapshot(
        val serverId: String,
        val teamId: String,
        val clientEntityKey: String,
        val status: String,
        val payloadJson: String,
        val entityVersion: Long,
        val createdAtText: String?,
        val updatedAtText: String?
    )

    sealed interface Result {
        data class Success(val items: List<Snapshot>, val activeBtbId: String?) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun loadTeamBtbs(token: String, worksiteId: String, teamId: String): Result {
        val connection = openConnection(
            "/api/v1/worksites/${encode(worksiteId)}/teams/${encode(teamId)}/btbs"
        )
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return Result.Error("BTB-Liste konnte nicht gelesen werden.")
                    val list = root.optJSONArray("btbs") ?: JSONArray()
                    val snapshots = mutableListOf<Snapshot>()
                    for (index in 0 until list.length()) {
                        val row = list.optJSONObject(index) ?: continue
                        val id = row.optString("id").trim()
                        if (id.isBlank()) continue
                        when (val detail = loadBtb(token, id, teamId)) {
                            DetailResult.Unsupported -> return Result.Unsupported
                            DetailResult.SessionInvalid -> return Result.SessionInvalid
                            is DetailResult.Error -> return Result.Error(detail.message)
                            is DetailResult.Success -> snapshots += detail.snapshot
                        }
                    }
                    Result.Success(
                        items = snapshots,
                        activeBtbId = root.nullableString("active_btb_id")
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> Result.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) Result.SessionInvalid
                    else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "Server-1.0-BTBs konnten nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private sealed interface DetailResult {
        data class Success(val snapshot: Snapshot) : DetailResult
        data object Unsupported : DetailResult
        data object SessionInvalid : DetailResult
        data class Error(val message: String) : DetailResult
    }

    private fun loadBtb(token: String, btbId: String, expectedTeamId: String): DetailResult {
        val connection = openConnection("/api/v1/btbs/${encode(btbId)}")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return DetailResult.Error("BTB-Detail konnte nicht gelesen werden.")
                    val btb = root.optJSONObject("btb")
                        ?: return DetailResult.Error("BTB-Kopfdaten fehlen.")
                    val teamId = btb.optString("team_id").trim()
                    if (teamId.isBlank() || teamId != expectedTeamId) {
                        return DetailResult.Error("BTB-Teamzuordnung ist inkonsistent.")
                    }
                    DetailResult.Success(snapshotFromDetail(root, btb, teamId))
                }
                HttpURLConnection.HTTP_NOT_FOUND -> DetailResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> DetailResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) DetailResult.SessionInvalid
                    else DetailResult.Error(errorMessage(body, code))
                else -> DetailResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            DetailResult.Error(error.message ?: "Server-1.0-BTB-Detail konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun snapshotFromDetail(root: JSONObject, btb: JSONObject, teamId: String): Snapshot {
        val entries = root.optJSONArray("entries") ?: JSONArray()
        var bridgePayload: JSONObject? = null
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            if (entry.optString("section") == ANDROID_SECTION) {
                bridgePayload = entry.optJSONObject("payload")
                break
            }
        }

        val header = btb.optJSONObject("header") ?: JSONObject()
        val serverId = btb.optString("id").trim()
        val clientKey = bridgePayload?.optString("btbId")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: header.optString(ANDROID_CLIENT_KEY).trim().takeIf { it.isNotBlank() }
            ?: serverId

        val payload = bridgePayload?.let { JSONObject(it.toString()) }
            ?: minimalPayload(btb, header, clientKey, entries)

        if (payload.optString("btbId").isBlank()) payload.put("btbId", clientKey)
        if (payload.optString("datumIso").isBlank()) {
            btb.nullableString("work_date")?.let { payload.put("datumIso", it) }
        }
        if (payload.optString("schicht").isBlank()) {
            payload.put("schicht", header.optString("schicht").ifBlank { "TAG" })
        }
        payload.put("fotos", JSONArray())
        payload.put("pdfAuswahl", JSONObject())

        return Snapshot(
            serverId = serverId,
            teamId = teamId,
            clientEntityKey = clientKey,
            status = if (btb.optString("status").uppercase() == "FINALIZED") "FERTIG" else "ENTWURF",
            payloadJson = payload.toString(),
            entityVersion = btb.optLong("version", 1L).coerceAtLeast(1L),
            createdAtText = btb.nullableString("created_at"),
            updatedAtText = btb.nullableString("updated_at")
        )
    }

    private fun minimalPayload(
        btb: JSONObject,
        header: JSONObject,
        clientKey: String,
        entries: JSONArray
    ): JSONObject = JSONObject()
        .put("btbId", clientKey)
        .put("datumIso", btb.opt("work_date") ?: JSONObject.NULL)
        .put("schicht", header.optString("schicht").ifBlank { "TAG" })
        .put("projektNameSnapshot", header.opt("projektNameSnapshot") ?: JSONObject.NULL)
        .put("strecke", header.opt("strecke") ?: JSONObject.NULL)
        .put("startUhrzeit", timePart(btb.nullableString("started_at")) ?: JSONObject.NULL)
        .put("endeUhrzeit", timePart(btb.nullableString("ended_at")) ?: JSONObject.NULL)
        .put("notizen", header.opt("notizen") ?: JSONObject.NULL)
        .put("serverEntries", JSONArray(entries.toString()))
        .put("fotos", JSONArray())
        .put("pdfAuswahl", JSONObject())

    private fun timePart(value: String?): String? {
        val raw = value ?: return null
        val marker = raw.indexOf('T')
        if (marker < 0 || marker + 1 >= raw.length) return null
        return raw.substring(marker + 1).take(5).takeIf { it.length == 5 }
    }

    private fun openConnection(path: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
        }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: return ""
        }
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
        }
    }

    private fun errorMessage(body: String, code: Int): String =
        detailMessage(body).ifBlank { "Serverfehler ($code)." }

    private fun detailMessage(body: String): String {
        val raw = runCatching { JSONObject(body).opt("detail") }.getOrNull()
        return when (raw) {
            is String -> raw.trim()
            is JSONObject -> raw.optString("code").trim().ifBlank { raw.toString() }
            else -> raw?.toString()?.trim().orEmpty()
        }
    }

    private fun isInvalidDevice(body: String): Boolean =
        detailMessage(body) in setOf("DEVICE_REVOKED", "USER_DISABLED", "USER_EXPIRED")

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    companion object {
        const val ANDROID_SECTION = "TB_HELFER_ANDROID_V1"
        const val ANDROID_CLIENT_KEY = "tb_helfer_btb_id"
    }
}
