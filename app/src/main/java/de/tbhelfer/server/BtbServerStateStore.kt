package de.tbhelfer.server

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistenter, vom Server bestaetigter BTB-Lifecycle-/Rollenstand.
 *
 * Der lokale BTB bleibt fuer Offline-Arbeit erhalten. Sobald der Server einen BTB als
 * FINALIZED + nicht aktiv bestaetigt, ist dieser Stand jedoch fuer Schreibrechte verbindlich.
 * Ebenso sperrt ein FINISHED/NOT_PARTICIPATING-Teilnehmerstatus weitere Fachmutationen dieses
 * Teilnehmers, bis der Server wieder WORKING meldet.
 */
object BtbServerStateStore {
    private const val PREFS = "tbhelfer_btb_server_state_v1"

    data class Participant(
        val userId: String,
        val displayName: String?,
        val status: String,
        val isTeamLead: Boolean
    )

    data class State(
        val clientBtbId: String,
        val serverBtbId: String,
        val worksiteServerId: String,
        val teamId: String,
        val serverStatus: String,
        val active: Boolean,
        val isTeamLead: Boolean,
        val participantStatus: String?,
        val canFinalize: Boolean,
        val canViewPdf: Boolean,
        val participants: List<Participant>,
        val photosJson: String
    ) {
        val serverFinalReadOnly: Boolean
            get() = serverStatus.equals("FINALIZED", true) && !active

        val participantReadOnly: Boolean
            get() = !isTeamLead && (
                participantStatus.equals("FINISHED", true) ||
                    participantStatus.equals("NOT_PARTICIPATING", true)
                )

        val readOnly: Boolean
            get() = serverFinalReadOnly || participantReadOnly
    }

    fun rememberSnapshot(
        context: Context,
        worksiteServerId: String,
        serverBtbId: String,
        teamId: String,
        clientBtbId: String,
        payloadJson: String
    ) {
        if (clientBtbId.isBlank() || serverBtbId.isBlank()) return
        val payload = runCatching { JSONObject(payloadJson) }.getOrElse { JSONObject() }
        val currentUser = payload.optJSONObject(Server1BtbApi.SERVER_CURRENT_USER_KEY) ?: JSONObject()
        val participants = payload.optJSONArray(Server1BtbApi.SERVER_PARTICIPANTS_KEY) ?: JSONArray()
        val photos = payload.optJSONArray(Server1BtbApi.SERVER_PHOTOS_KEY) ?: JSONArray()

        val state = JSONObject()
            .put("client_btb_id", clientBtbId)
            .put("server_btb_id", serverBtbId)
            .put("worksite_server_id", worksiteServerId)
            .put("team_id", teamId)
            .put(
                "server_status",
                payload.optString(Server1BtbApi.SERVER_STATUS_KEY).trim().uppercase()
            )
            .put(
                "active",
                payload.optBoolean(Server1BtbApi.SERVER_ACTIVE_KEY, false)
            )
            .put("current_user", currentUser)
            .put("participants", participants)
            .put("photos", photos)
            .put("updated_at_epoch_ms", System.currentTimeMillis())

        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("entries:" + key(clientBtbId), (payload.optJSONObject(Server1BtbApi.SERVER_ENTRY_IDS_KEY) ?: JSONObject()).toString())
            .putString(key(clientBtbId), state.toString())
            .apply()
    }

    fun entryId(context: Context, clientBtbId: String, clientEntryKey: String): String? {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("entries:" + key(clientBtbId), null) ?: return null
        val value = runCatching { JSONObject(raw).optString(clientEntryKey) }.getOrNull()
        return value?.let { runCatching { java.util.UUID.fromString(it).toString() }.getOrNull() }
    }

    fun entryKey(context: Context, clientBtbId: String, serverEntryId: String): String? {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("entries:" + key(clientBtbId), null) ?: return null
        val map = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return map.keys().asSequence().firstOrNull { map.optString(it) == serverEntryId }
    }

    fun state(context: Context, clientBtbId: String?): State? {
        val id = clientBtbId?.trim().orEmpty()
        if (id.isBlank()) return null
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(id), null)
            ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val current = root.optJSONObject("current_user") ?: JSONObject()
        val participantsArray = root.optJSONArray("participants") ?: JSONArray()
        val participants = (0 until participantsArray.length()).mapNotNull { index ->
            val p = participantsArray.optJSONObject(index) ?: return@mapNotNull null
            val userId = p.optString("user_id").trim()
            if (userId.isBlank()) return@mapNotNull null
            Participant(
                userId = userId,
                displayName = p.optString("display_name").trim().takeIf { it.isNotBlank() },
                status = p.optString("status").trim().uppercase().ifBlank { "WORKING" },
                isTeamLead = p.optBoolean("is_team_lead", false)
            )
        }
        return State(
            clientBtbId = id,
            serverBtbId = root.optString("server_btb_id").trim(),
            worksiteServerId = root.optString("worksite_server_id").trim(),
            teamId = root.optString("team_id").trim(),
            serverStatus = root.optString("server_status").trim().uppercase(),
            active = root.optBoolean("active", false),
            isTeamLead = current.optBoolean("is_team_lead", false),
            participantStatus = current.optString("participant_status")
                .trim()
                .uppercase()
                .takeIf { it.isNotBlank() && it != "NULL" },
            canFinalize = current.optBoolean("can_finalize_btb", false),
            canViewPdf = current.optBoolean("can_view_btb_pdf", false),
            participants = participants,
            photosJson = (root.optJSONArray("photos") ?: JSONArray()).toString()
        )
    }

    fun photoMetadata(context: Context, clientBtbId: String?, fileId: String): JSONObject? {
        val state = state(context, clientBtbId) ?: return null
        val photos = runCatching { JSONArray(state.photosJson) }.getOrNull() ?: return null
        for (index in 0 until photos.length()) {
            val item = photos.optJSONObject(index) ?: continue
            if (item.optString("id").trim() == fileId) {
                return JSONObject(item.toString())
            }
        }
        return null
    }

    fun rememberPhotoUpdate(
        context: Context,
        clientBtbId: String?,
        fileId: String,
        linkVersion: Long,
        photoMetadata: JSONObject
    ) {
        val id = clientBtbId?.trim().orEmpty()
        if (id.isBlank() || fileId.isBlank() || linkVersion <= 0L) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(key(id), null) ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val photos = root.optJSONArray("photos") ?: return
        var changed = false
        for (index in 0 until photos.length()) {
            val item = photos.optJSONObject(index) ?: continue
            if (item.optString("id").trim() != fileId) continue
            item.put("link_version", linkVersion)
            item.put("photo_metadata", JSONObject(photoMetadata.toString()))
            changed = true
            break
        }
        if (!changed) return
        root.put("updated_at_epoch_ms", System.currentTimeMillis())
        prefs.edit().putString(key(id), root.toString()).apply()
    }

    fun remove(context: Context, clientBtbId: String?) {
        val id = clientBtbId?.trim().orEmpty()
        if (id.isBlank()) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(id))
            .apply()
    }

    private fun key(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
}

