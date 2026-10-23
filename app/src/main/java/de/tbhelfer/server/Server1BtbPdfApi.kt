package de.tbhelfer.server

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Lädt ausschließlich das aktuell gültige serverseitige BTB-Abschluss-PDF. */
class Server1BtbPdfApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class PdfFile(
        val bytes: ByteArray,
        val mimeType: String?
    )

    sealed interface Result {
        data class Success(val file: PdfFile) : Result
        data object NotAvailable : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun downloadFinalPdf(token: String, btbId: String): Result {
        val connection = (URL(
            baseUrl.trimEnd('/') + "/api/v1/btbs/" + encode(btbId) + "/final-pdf"
        ).let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 60_000
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/pdf")
        }

        return try {
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.size < PDF_MAGIC.size ||
                        !bytes.copyOfRange(0, PDF_MAGIC.size).contentEquals(PDF_MAGIC)
                    ) {
                        Result.Error("Server lieferte kein gültiges BTB-PDF.")
                    } else {
                        Result.Success(PdfFile(bytes, connection.contentType))
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Result.NotAvailable
                HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Result.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> Result.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    val body = readBody(connection)
                    if (isInvalidDevice(body)) Result.SessionInvalid
                    else Result.Error(errorMessage(body, code))
                }
                else -> Result.Error(errorMessage(readBody(connection), code))
            }
        } catch (error: Exception) {
            Result.Error(error.message ?: "BTB-Abschluss-PDF konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection): String {
        val stream: InputStream = connection.errorStream ?: return ""
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

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    companion object {
        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    }
}

