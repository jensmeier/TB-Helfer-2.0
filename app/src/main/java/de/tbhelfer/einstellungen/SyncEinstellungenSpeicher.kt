package de.tbhelfer.einstellungen

import android.content.Context

/**
 * Gerätespezifische Netzregel für den automatischen selektiven Server-Sync.
 *
 * Der frühere Zeitplan MANUELL/STÜNDLICH/TÄGLICH ist bewusst entfernt:
 * Fachänderungen werden ereignisgetrieben und möglichst klein synchronisiert.
 * Ein vollständiger Abgleich bleibt eine bewusste Recovery-Aktion ("Neu laden").
 */
object SyncEinstellungenSpeicher {
    private const val PREFS = "tbhelfer_sync_einstellungen_v1"
    private const val LEGACY_KEY_ZEITPLAN = "zeitplan"
    private const val KEY_NETZ = "netz"

    enum class Netz {
        NUR_WLAN,
        NUR_MOBILFUNK,
        WLAN_UND_MOBILFUNK
    }

    data class Einstellungen(
        val netz: Netz
    )

    fun laden(context: Context): Einstellungen {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val netz = runCatching {
            Netz.valueOf(prefs.getString(KEY_NETZ, Netz.WLAN_UND_MOBILFUNK.name).orEmpty())
        }.getOrDefault(Netz.WLAN_UND_MOBILFUNK)
        return Einstellungen(netz)
    }

    fun speichern(context: Context, einstellungen: Einstellungen) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(LEGACY_KEY_ZEITPLAN)
            .putString(KEY_NETZ, einstellungen.netz.name)
            .apply()
    }

    fun netzLabel(value: Netz): String = when (value) {
        Netz.NUR_WLAN -> "Nur WLAN"
        Netz.NUR_MOBILFUNK -> "Nur Mobilfunk"
        Netz.WLAN_UND_MOBILFUNK -> "WLAN + Mobilfunk"
    }
}
