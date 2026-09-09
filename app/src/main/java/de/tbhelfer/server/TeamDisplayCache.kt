package de.tbhelfer.server

import android.content.Context

/**
 * Kleiner Offline-Cache für die menschenlesbare Bezeichnung des eigenen verantwortlichen TB.
 * Technische Beziehungen verwenden weiterhin ausschließlich Server-/Gruppen-UUIDs.
 */
object TeamDisplayCache {
    private const val PREF = "tb_helfer_team_display_v1"

    data class OwnTeam(
        val worksiteId: String,
        val groupId: String,
        val leadDisplayName: String?,
        val leadNameSnapshot: String?
    )

    fun remember(context: Context, team: TeamManagementApi.TeamOverview) {
        val own = team.groups.firstOrNull { it.groupId == team.currentGroupId }
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString("group:${team.worksiteId}", team.currentGroupId)
            .putString("display:${team.worksiteId}", own?.leadDisplayName)
            .putString("snapshot:${team.worksiteId}", own?.leadNameSnapshot)
            .apply()
    }

    fun ownTeam(context: Context, worksiteId: String): OwnTeam? {
        if (worksiteId.isBlank()) return null
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val group = p.getString("group:$worksiteId", null)?.takeIf { it.isNotBlank() } ?: return null
        return OwnTeam(
            worksiteId = worksiteId,
            groupId = group,
            leadDisplayName = p.getString("display:$worksiteId", null)?.takeIf { it.isNotBlank() },
            leadNameSnapshot = p.getString("snapshot:$worksiteId", null)?.takeIf { it.isNotBlank() }
        )
    }
}
