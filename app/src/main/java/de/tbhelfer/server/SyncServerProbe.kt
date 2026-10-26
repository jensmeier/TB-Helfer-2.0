package de.tbhelfer.server

import android.content.Context
import android.net.ConnectivityManager
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import org.json.JSONObject

/** Fresh read-only reachability check, never an authentication or sync-completion claim. */
object SyncServerProbe {
    fun check(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm?.activeNetwork == null) return "Handy offline · kein Netzwerk"
        if (de.tbhelfer.server.recovery.RecoveryWorkspace.blocked()) return "Serverprüfung pausiert · Wiederherstellung erforderlich"
        return try {
            val connection = de.tbhelfer.server.recovery.RecoveryHttp.open(
                URL(ServerConfig.BASE_URL.trimEnd('/') + "/api/v1/capabilities"))
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 4_000
                connection.readTimeout = 4_000
                connection.useCaches = false
                val code = connection.responseCode
                if (code in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val valid = runCatching { JSONObject(body).optJSONObject("capabilities") != null }.getOrDefault(false)
                    if (valid) "TB-Server erreichbar · HTTP $code" else "Serverantwort erhalten · API-Antwort unerwartet"
                } else "Server antwortet HTTP $code · Zugriff/Server prüfen"
            } finally { connection.disconnect() }
        } catch (_: SocketTimeoutException) { "Keine Serverantwort innerhalb des Prüftimeouts" }
        catch (_: UnknownHostException) { "Serveradresse konnte nicht aufgelöst werden (DNS)" }
        catch (_: Exception) { "Serverprüfung fehlgeschlagen · Verbindung oder Server prüfen" }
    }
}
