package de.tbhelfer.server.recovery

import de.tbhelfer.server.ServerConfig
import de.tbhelfer.server.TeamManagementApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLEncoder

/** Writes only to the recovery journal, never to normal DAOs/cursors/outboxes. */
object RecoveryComparison {
    data class Result(val server: JSONObject, val description: String)
    fun load(token: String, epoch: String, saved: RecoveryArchive.Saved): Result = RecoveryProtocol.compare(epoch) {
        val me = get(token, "/api/v1/device/me")
        check(me.optJSONObject("user")?.optString("id")?.isNotBlank() == true) { "Geräteanmeldung konnte nicht bestätigt werden." }
        val catalog = get(token, "/api/v1/catalog")
        check(catalog.optJSONArray("routes") != null && catalog.optJSONArray("stations") != null) { "Strecken-/Bahnhofskatalog ist unvollständig." }
        val contexts = when (val result = TeamManagementApi().loadWorksiteContexts(token)) {
            is TeamManagementApi.WorksiteContextResult.Success -> result.items
            is TeamManagementApi.WorksiteContextResult.Error -> throw IOException(result.message)
            else -> throw IOException("Baustellen und Berechtigungen konnten nicht vollständig gelesen werden.")
        }
        val sites = JSONArray()
        var btbCount = 0
        for (context in contexts) {
            val site = JSONObject().put("name", context.name ?: context.designation ?: "Baustelle")
                .put("id", context.worksiteId).put("role", context.memberRole).put("team_id", context.groupId)
            val path = "/api/v1/worksites/${encode(context.worksiteId)}"
            site.put("nodes", get(token, "$path/nodes?include_deleted=true"))
            site.put("defects", get(token, "$path/defects"))
            site.put("acceptances", get(token, "$path/acceptances"))
            if (context.groupId.isBlank()) {
                site.put("btbs", JSONArray()).put("team_note", "Keine eigene TB-Gruppenzuordnung")
                sites.put(site)
                continue
            }
            val index = get(token, "$path/teams/${encode(context.groupId)}/btbs")
            val btbs = index.optJSONArray("btbs") ?: throw IOException("BTB-Liste fehlt im Serververgleich.")
            val details = JSONArray()
            for (i in 0 until btbs.length()) {
                val id = btbs.getJSONObject(i).getString("id")
                details.put(get(token, "/api/v1/btbs/${encode(id)}"))
                btbCount++
            }
            site.put("btbs", details)
            sites.put(site)
        }
        if (RecoveryProtocol.probe(force = true) != epoch) throw IOException("Der Server wurde während des Vergleichs erneut wiederhergestellt. Bitte neu starten.")
        val server = JSONObject().put("epoch", epoch).put("owner", saved.owner).put("catalog", catalog)
            .put("worksites", sites).put("created", System.currentTimeMillis())
        RecoveryArchive.atomicJson(File(RecoveryWorkspace.journal(), "${saved.id}/server.json"), server)
        val description = buildString {
            append("Server: ${contexts.size} zugängliche Baustellen, $btbCount Bautagebücher der eigenen TB-Gruppen.\n")
            append("Gemeinsamer Katalog: ${catalog.getJSONArray("routes").length()} Strecken, ${catalog.getJSONArray("stations").length()} Bahnhöfe.\n")
            contexts.forEach { append("\n${it.name ?: it.designation ?: "Baustelle"} · ${if (it.memberRole == "HELPER") "Helfer" else if (it.memberRole == "LEAD") "Verantwortlicher TB" else "Leseberechtigung"}") }
            append("\n\nDies ist ein lesender Vergleich. Dateien und Vermessungsdetails werden erst nach dem Wechsel regulär geladen. Lokale Ergänzungen werden nicht automatisch zusammengeführt.")
        }
        Result(server, description)
    }
    private fun get(token: String, path: String): JSONObject {
        val connection = RecoveryHttp.open(URL(ServerConfig.BASE_URL.trimEnd('/') + path))
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Serververgleich konnte nicht vollständig geladen werden (HTTP $code). Die Handydaten bleiben unverändert.")
            return JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { connection.disconnect() }
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
