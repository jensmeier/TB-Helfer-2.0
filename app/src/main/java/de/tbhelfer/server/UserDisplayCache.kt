package de.tbhelfer.server

import android.content.Context

/**
 * Nicht geheimer Offline-Cache für menschenlesbare Benutzerdaten.
 * Technische Identitäten/Rechte bleiben ausschließlich beim Server-Token und den UUIDs.
 */
object UserDisplayCache {
    private const val PREFS = "tbhelfer_user_display_v1"
    private const val KEY_NAME = "user_name"

    fun rememberName(context: Context, name: String?) {
        val value = name?.trim()?.takeIf { it.isNotBlank() }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (value == null) remove(KEY_NAME) else putString(KEY_NAME, value)
            }
            .apply()
    }

    fun userName(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
}
