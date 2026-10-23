package de.tbhelfer.server

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import de.tbhelfer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

object AppUpdateManager {

    data class ReleaseInfo(
        val id: String,
        val versionName: String,
        val buildNumber: Int,
        val mandatory: Boolean,
        val downloadUrl: String,
        val releaseNotes: String?,
        val sha256: String?,
        val byteSize: Long?
    )

    sealed interface CheckResult {
        data class Current(val serverStatus: String) : CheckResult
        data class Available(val release: ReleaseInfo, val required: Boolean) : CheckResult
        data class Error(val message: String) : CheckResult
    }

    sealed interface DownloadResult {
        data class Success(val file: File) : DownloadResult
        data class Error(val message: String) : DownloadResult
    }

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val version = URLEncoder.encode(BuildConfig.VERSION_NAME, Charsets.UTF_8.name())
        val url = URL(
            ServerConfig.BASE_URL.trimEnd('/') +
                "/api/v1/client-compatibility?platform=ANDROID" +
                "&build_number=${BuildConfig.VERSION_CODE}" +
                "&version_name=$version"
        )
        val connection = (url.let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            val body = readBody(connection, code)
            if (code !in 200..299) {
                return@withContext CheckResult.Error(
                    JSONObject(body.ifBlank { "{}" }).optString("detail")
                        .ifBlank { "Updateprüfung fehlgeschlagen ($code)." }
                )
            }

            val root = JSONObject(body)
            if (!root.optBoolean("update_available", false)) {
                return@withContext CheckResult.Current(
                    root.optString("status").ifBlank { "SUPPORTED_CURRENT" }
                )
            }

            val release = root.optJSONObject("release")
                ?: return@withContext CheckResult.Error("Server meldet ein Update ohne Release-Daten.")
            val build = release.optInt("build_number", -1)
            val id = release.optString("id").trim()
            val versionName = release.optString("version_name").trim()
            val downloadUrl = release.optString("download_url").trim()
            if (id.isBlank() || versionName.isBlank() || build <= 0 || downloadUrl.isBlank()) {
                return@withContext CheckResult.Error("Release-Daten des Servers sind unvollständig.")
            }

            val info = ReleaseInfo(
                id = id,
                versionName = versionName,
                buildNumber = build,
                mandatory = release.optBoolean("mandatory", false),
                downloadUrl = downloadUrl,
                releaseNotes = release.optString("release_notes")
                    .trim()
                    .takeIf { it.isNotBlank() },
                sha256 = release.optString("sha256")
                    .trim()
                    .takeIf { it.isNotBlank() },
                byteSize = release.optLong("byte_size", -1L).takeIf { it >= 0L }
            )
            CheckResult.Available(
                release = info,
                required = root.optBoolean("update_required", false) || info.mandatory
            )
        } catch (error: Exception) {
            CheckResult.Error(error.message ?: "Server für Updateprüfung nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    fun currentVersionLabel(): String =
        "TB-Helfer ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    fun ensureInstallPermission(activity: Activity): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
            return false
        }
        return true
    }

    suspend fun downloadApk(context: Context, release: ReleaseInfo): DownloadResult =
        withContext(Dispatchers.IO) {
            val rawUrl = absoluteDownloadUrl(release.downloadUrl)
            if (!rawUrl.startsWith("https://") && !(BuildConfig.DEBUG && rawUrl.startsWith("http://"))) {
                return@withContext DownloadResult.Error("Update-Download muss HTTPS verwenden.")
            }

            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: return@withContext DownloadResult.Error("Download-Ordner ist nicht verfügbar.")
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return@withContext DownloadResult.Error("Download-Ordner konnte nicht angelegt werden.")
            }

            val safeVersion = release.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(targetDir, "TB-Helfer-$safeVersion-${release.buildNumber}.apk")
            val temp = File(targetDir, target.name + ".part")
            temp.delete()

            val connection = (URL(rawUrl).let { de.tbhelfer.server.recovery.RecoveryHttp.open(it) }).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                useCaches = false
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
            }

            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = readBody(connection, code).take(400)
                    return@withContext DownloadResult.Error(
                        detail.ifBlank { "APK-Download fehlgeschlagen ($code)." }
                    )
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            written += count
                        }
                        output.fd.sync()
                    }
                }

                release.byteSize?.let { expected ->
                    if (written != expected) {
                        temp.delete()
                        return@withContext DownloadResult.Error(
                            "APK-Größe stimmt nicht: $written statt $expected Byte."
                        )
                    }
                }

                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                release.sha256?.let { expected ->
                    if (!expected.equals(actualSha, ignoreCase = true)) {
                        temp.delete()
                        return@withContext DownloadResult.Error(
                            "APK-Prüfsumme stimmt nicht. Update wurde verworfen."
                        )
                    }
                }

                target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }

                validateDownloadedApk(context, target, release)?.let { validationError ->
                    target.delete()
                    return@withContext DownloadResult.Error(validationError)
                }

                DownloadResult.Success(target)
            } catch (error: Exception) {
                temp.delete()
                DownloadResult.Error(error.message ?: "APK konnte nicht geladen werden.")
            } finally {
                connection.disconnect()
            }
        }

    /**
     * Schutz gegen einen inkonsistenten Release-Draft:
     * Server-Metadaten und die tatsächlich hochgeladene APK müssen exakt zusammenpassen.
     * Sonst würde Android eine alte APK installieren und anschließend dasselbe Update erneut
     * angeboten bekommen.
     */
    private fun validateDownloadedApk(
        context: Context,
        apk: File,
        release: ReleaseInfo
    ): String? {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            }
        }.getOrNull()
            ?: return "APK konnte nicht als Android-Paket gelesen werden. Update wurde verworfen."

        val apkPackage = packageInfo.packageName.orEmpty()
        val apkVersionName = packageInfo.versionName.orEmpty()
        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        if (apkPackage != context.packageName) {
            return "APK gehört nicht zu TB-Helfer: Paket '${apkPackage}' statt '${context.packageName}'. Update wurde verworfen."
        }

        if (
            apkVersionName != release.versionName ||
            apkVersionCode != release.buildNumber.toLong()
        ) {
            return "APK passt nicht zum Release: Datei enthält TB-Helfer " +
                "${apkVersionName} (${apkVersionCode}), Server erwartet " +
                "${release.versionName} (${release.buildNumber}). Update wurde verworfen."
        }

        return null
    }

    fun launchInstaller(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.dateianbieter",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    private fun absoluteDownloadUrl(raw: String): String {
        val value = raw.trim()
        return when {
            value.startsWith("https://") || value.startsWith("http://") -> value
            value.startsWith("/") -> ServerConfig.BASE_URL.trimEnd('/') + value
            else -> ServerConfig.BASE_URL.trimEnd('/') + "/" + value
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

