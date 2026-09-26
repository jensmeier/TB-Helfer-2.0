package de.tbhelfer.server

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistiert die vom Server vergebenen _tb_android_slot-Identitaeten je BTB.
 *
 * Die sichtbaren Android-Modelle duerfen Eintraege weiterhin als Listen behandeln. Dieser Store
 * haelt parallel die stabilen Server-Slots, damit ein Server-Rundlauf Luecken wie abnahme:0,
 * abnahme:2 nicht durch neue Array-Indizes in abnahme:0, abnahme:1 umnummeriert.
 */
object BtbEntrySlotStore {
    private const val PREFS = "tbhelfer_btb_entry_slots_v1"

    fun remember(context: Context, clientBtbId: String, slotMap: JSONObject?) {
        if (clientBtbId.isBlank() || slotMap == null) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(clientBtbId), slotMap.toString())
            .apply()
    }

    fun load(context: Context, clientBtbId: String?): JSONObject {
        val id = clientBtbId?.trim().orEmpty()
        if (id.isBlank()) return JSONObject()
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(id), null)
            ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    /**
     * Mischt die gespeicherten Slots in den ausgehenden BTB-Payload. Die API entfernt das
     * Android-interne Hilfsobjekt vor fachlichen Header-/Entry-Vergleichen wieder.
     */
    fun injectInto(context: Context, clientBtbId: String?, payload: JSONObject) {
        val slots = load(context, clientBtbId)
        if (slots.length() > 0) {
            payload.put(Server1BtbApi.LOCAL_ENTRY_SLOTS_KEY, JSONObject(slots.toString()))
        }
    }

    fun remove(context: Context, clientBtbId: String?) {
        val id = clientBtbId?.trim().orEmpty()
        if (id.isBlank()) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(id))
            .apply()
    }

    /** Liest eine Slotliste aus dem persistierten JSON. */
    fun slots(map: JSONObject?, key: String): List<String> {
        val array = map?.optJSONArray(key) ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf { it.isNotBlank() }
        }
    }

    private fun key(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
}
