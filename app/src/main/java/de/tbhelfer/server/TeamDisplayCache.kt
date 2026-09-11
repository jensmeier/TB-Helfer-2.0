package de.tbhelfer.server

import android.content.Context

/**
 * Kleiner Offline-Cache für die menschenlesbare Baustellen-Teamdarstellung.
 * Technische Beziehungen verwenden weiterhin ausschließlich Server-/Gruppen-UUIDs.
 */
object TeamDisplayCache {
    private const val PREF = "tb_helfer_team_display_v1"

    data class OwnTeam(
        val worksiteId: String,
        val groupId: String,
        val leadDisplayName: String?,
        val leadNameSnapshot: String?,
        val responsibleTbCount: Int,
        val helperCount: Int,
        val lastSuccessfulSyncEpochMs: Long?
    )

    fun remember(context: Context, team: TeamManagementApi.TeamOverview) {
        val own = team.groups.firstOrNull { it.groupId == team.currentGroupId }
        val responsibleTbCount = team.groups
            .mapNotNull { it.leadUserId?.takeIf(String::isNotBlank) }
            .distinct()
            .size
            .takeIf { it > 0 }
            ?: team.groups.size
        val helperCount = team.groups
            .flatMap { it.members }
            .filter { it.memberRole.equals("HELPER", ignoreCase = true) }
            .map { it.userId }
            .distinct()
            .size

        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString("group:${team.worksiteId}", team.currentGroupId)
            .putString("display:${team.worksiteId}", own?.leadDisplayName)
            .putString("snapshot:${team.worksiteId}", own?.leadNameSnapshot)
            .putInt("tb_count:${team.worksiteId}", responsibleTbCount)
            .putInt("helper_count:${team.worksiteId}", helperCount)
            .putLong("last_sync:${team.worksiteId}", System.currentTimeMillis())
            .apply()
    }

    fun markSuccessfulSync(context: Context, worksiteId: String, epochMs: Long = System.currentTimeMillis()) {
        if (worksiteId.isBlank()) return
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_sync:$worksiteId", epochMs)
            .apply()
    }

    fun ownTeam(context: Context, worksiteId: String): OwnTeam? {
        if (worksiteId.isBlank()) return null
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val group = p.getString("group:$worksiteId", null)?.takeIf { it.isNotBlank() } ?: return null
        val last = p.getLong("last_sync:$worksiteId", 0L).takeIf { it > 0L }
        return OwnTeam(
            worksiteId = worksiteId,
            groupId = group,
            leadDisplayName = p.getString("display:$worksiteId", null)?.takeIf { it.isNotBlank() },
            leadNameSnapshot = p.getString("snapshot:$worksiteId", null)?.takeIf { it.isNotBlank() },
            responsibleTbCount = p.getInt("tb_count:$worksiteId", 0).coerceAtLeast(0),
            helperCount = p.getInt("helper_count:$worksiteId", 0).coerceAtLeast(0),
            lastSuccessfulSyncEpochMs = last
        )
    }
}
