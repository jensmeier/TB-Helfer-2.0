package de.tbhelfer.server

import de.tbhelfer.einstellungen.BtbPdfLogoLayoutSpeicher
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Optionales Schema-12-API fuer die firmenweite BTB/PDF-Logo-Darstellung. */
class CompanyPdfLogoApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    sealed interface Result {
        data class Success(val layout: BtbPdfLogoLayoutSpeicher.Einstellungen) : Result
        data object SchemaRequired : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun load(token: String): Result {
        val c = open("GET")
        return try {
            authorize(c, token)
            val code = c.responseCode
            val body = readBody(c, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parse(body)?.let(Result::Success)
                    ?: Result.Error("PDF-Logo-Profil konnte nicht gelesen werden.")
                HttpURLConnection.HTTP_NOT_FOUND,
                HttpURLConnection.HTTP_CONFLICT -> Result.SchemaRequired
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        Result.SessionInvalid else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "PDF-Logo-Profil konnte nicht geladen werden.")
        } finally {
            c.disconnect()
        }
    }

    fun save(token: String, layout: BtbPdfLogoLayoutSpeicher.Einstellungen): Result {
        val cfg = BtbPdfLogoLayoutSpeicher.normalize(layout)
        val c = open("PUT")
        return try {
            authorize(c, token)
            writeJson(
                c,
                JSONObject()
                    .put("logo_width_pt", cfg.logoWidthPt)
                    .put("offset_x_pt", cfg.offsetXPt)
                    .put("offset_y_pt", cfg.offsetYPt)
                    .put("show_company_name", cfg.showCompanyName)
                    .put("show_company_email", cfg.showCompanyEmail)
                    .put("show_company_phone", cfg.showCompanyPhone)
                    .put("contacts_in_image", cfg.contactsInImage)
            )
            val code = c.responseCode
            val body = readBody(c, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> parse(body)?.let(Result::Success)
                    ?: Result.Error("Gespeichertes PDF-Logo-Profil konnte nicht gelesen werden.")
                HttpURLConnection.HTTP_NOT_FOUND,
                HttpURLConnection.HTTP_CONFLICT -> Result.SchemaRequired
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (DeviceAuthorization.sessionInvalid(code, runCatching { JSONObject(body).optString("detail") }.getOrDefault("")))
                        Result.SessionInvalid else Result.Error(errorMessage(body, code))
                else -> Result.Error(errorMessage(body, code))
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "PDF-Logo-Profil konnte nicht gespeichert werden.")
        } finally {
            c.disconnect()
        }
    }

    private fun parse(body: String): BtbPdfLogoLayoutSpeicher.Einstellungen? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val j = root.optJSONObject("layout") ?: root
        return BtbPdfLogoLayoutSpeicher.normalize(
            BtbPdfLogoLayoutSpeicher.Einstellungen(
                logoWidthPt = j.optInt("logo_width_pt", 90),
                offsetXPt = j.optInt("offset_x_pt", 0),
                offsetYPt = j.optInt("offset_y_pt", 0),
                showCompanyName = j.optBoolean("show_company_name", true),
                showCompanyEmail = j.optBoolean("show_company_email", true),
                showCompanyPhone = j.optBoolean("show_company_phone", true),
                contactsInImage = j.optBoolean("contacts_in_image", false),
                serverVersion = j.optLong("layout_version", 0L)
            )
        )
    }

    private fun open(method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + "/api/v1/company/profile/pdf-logo-layout").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

    private fun authorize(c: HttpURLConnection, token: String) {
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/json")
    }

    private fun writeJson(c: HttpURLConnection, json: JSONObject) {
        OutputStreamWriter(c.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }
    }

    private fun readBody(c: HttpURLConnection, code: Int): String {
        val stream: InputStream = if (code in 200..299) c.inputStream else c.errorStream ?: return ""
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

    private fun errorMessage(body: String, code: Int): String {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return detail.ifBlank { "Serverfehler ($code)." }
    }
}

