package de.tbhelfer.ui.abnahme

import android.content.Context
import org.json.JSONObject

/**
 * Dauerhafter Benutzer-Löschwunsch für strukturierte Abnahmen.
 *
 * Absichtlich getrennt vom eigentlichen Abnahme-Speicher: Nach dem lokalen Löschen muss die
 * Absicht offline weiterleben, bis Server 1.0 den Tombstone bestätigt hat. Aus bloßem
 * "lokal fehlt" darf niemals eine Server-Löschung abgeleitet werden.
 */
object AbnahmeDeleteIntentStore {
    data class Intent(
        val localBaustelleId: Long,
        val acceptanceId: String,
        val requestedAtEpochMs: Long
    )

    private const val PREF = "tb_helfer_abnahme_delete_intents_v1"

    @Synchronized
    fun merken(context: Context, localBaustelleId: Long, acceptanceId: String) {
        if (localBaustelleId <= 0L || acceptanceId.isBlank()) return
        val json = JSONObject()
            .put("localBaustelleId", localBaustelleId)
            .put("acceptanceId", acceptanceId)
            .put("requestedAtEpochMs", System.currentTimeMillis())
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(acceptanceId, json.toString())
            .commit()
    }

    fun fuerBaustelle(context: Context, localBaustelleId: Long): List<Intent> =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .all
            .values
            .mapNotNull { it as? String }
            .mapNotNull(::parse)
            .filter { it.localBaustelleId == localBaustelleId }
            .sortedBy { it.requestedAtEpochMs }

    @Synchronized
    fun bestaetigen(context: Context, acceptanceId: String) {
        if (acceptanceId.isBlank()) return
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(acceptanceId)
            .commit()
    }

    private fun parse(raw: String): Intent? = runCatching {
        val json = JSONObject(raw)
        Intent(
            localBaustelleId = json.optLong("localBaustelleId", -1L),
            acceptanceId = json.optString("acceptanceId", ""),
            requestedAtEpochMs = json.optLong("requestedAtEpochMs", 0L)
        )
    }.getOrNull()?.takeIf {
        it.localBaustelleId > 0L && it.acceptanceId.isNotBlank()
    }
}
