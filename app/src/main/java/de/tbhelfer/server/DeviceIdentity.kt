package de.tbhelfer.server

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object DeviceIdentity {
    fun stableUid(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
        val source = "${context.packageName}:$androidId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        if (manufacturer.isBlank()) return model.ifBlank { "Android-Gerät" }
        if (model.startsWith(manufacturer, ignoreCase = true)) return model
        return "$manufacturer $model".trim()
    }

    fun osVersion(): String = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    fun appVersion(context: Context): String {
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "TB-Helfer ${info.versionName ?: "?"}"
        }.getOrElse { "TB-Helfer" }
    }
}
