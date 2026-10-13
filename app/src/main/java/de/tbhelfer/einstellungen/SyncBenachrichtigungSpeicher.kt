package de.tbhelfer.einstellungen

import android.content.Context

/**
 * Gerätespezifische Einstellung für neue synchronisierte BTB-/Mangel-/Abnahme-Einträge.
 *
 * Die Einstellung ist bewusst lokal: Jeder Nutzer kann auf jedem Gerät selbst wählen,
 * ob Text + Ton, nur Ton oder gar keine Benachrichtigung gewünscht ist.
 */
object SyncBenachrichtigungSpeicher {
    private const val PREFS = "tbhelfer_sync_benachrichtigungen"
    private const val KEY_MODUS = "modus"

    enum class Modus {
        TEXT_TON,
        NUR_TON,
        AUS
    }

    fun laden(context: Context): Modus {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODUS, Modus.TEXT_TON.name)
        return runCatching { Modus.valueOf(raw.orEmpty()) }.getOrDefault(Modus.TEXT_TON)
    }

    fun speichern(context: Context, modus: Modus) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODUS, modus.name)
            .apply()
    }
}
