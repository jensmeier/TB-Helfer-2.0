package de.tbhelfer.einstellungen

import android.content.Context

/**
 * Gerätespezifische Regeln für den automatischen Server-Sync.
 *
 * Ein bewusst ausgelöster manueller Sync ist davon getrennt: Die UI darf bei abweichender
 * Netzregel warnen und eine bewusste Ausführung erlauben. Der Hintergrund-Worker hält sich strikt
 * an diese Einstellung.
 */
object SyncEinstellungenSpeicher {
    private const val PREFS = "tbhelfer_sync_einstellungen_v1"
    private const val KEY_ZEITPLAN = "zeitplan"
    private const val KEY_NETZ = "netz"

    enum class Zeitplan {
        MANUELL,
        STUENDLICH,
        TAEGLICH
    }

    enum class Netz {
        NUR_WLAN,
        NUR_MOBILFUNK,
        WLAN_UND_MOBILFUNK
    }

    data class Einstellungen(
        val zeitplan: Zeitplan,
        val netz: Netz
    )

    fun laden(context: Context): Einstellungen {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val zeitplan = runCatching {
            Zeitplan.valueOf(prefs.getString(KEY_ZEITPLAN, Zeitplan.STUENDLICH.name).orEmpty())
        }.getOrDefault(Zeitplan.STUENDLICH)
        val netz = runCatching {
            Netz.valueOf(prefs.getString(KEY_NETZ, Netz.WLAN_UND_MOBILFUNK.name).orEmpty())
        }.getOrDefault(Netz.WLAN_UND_MOBILFUNK)
        return Einstellungen(zeitplan, netz)
    }

    fun speichern(context: Context, einstellungen: Einstellungen) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ZEITPLAN, einstellungen.zeitplan.name)
            .putString(KEY_NETZ, einstellungen.netz.name)
            .apply()
    }

    fun zeitplanLabel(value: Zeitplan): String = when (value) {
        Zeitplan.MANUELL -> "Manuell"
        Zeitplan.STUENDLICH -> "Stündlich"
        Zeitplan.TAEGLICH -> "Täglich"
    }

    fun netzLabel(value: Netz): String = when (value) {
        Netz.NUR_WLAN -> "Nur WLAN"
        Netz.NUR_MOBILFUNK -> "Nur Mobilfunk"
        Netz.WLAN_UND_MOBILFUNK -> "WLAN + Mobilfunk"
    }
}
