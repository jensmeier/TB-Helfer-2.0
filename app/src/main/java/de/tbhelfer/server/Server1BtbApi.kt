package de.tbhelfer.server

import de.tbhelfer.datenbank.SyncCollabPendingChangeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Granulare Android-Brücke für den kanonischen Server-1.0-BTB.
 *
 * Der Server speichert keinen Android-Gesamt-BTB als einen einzigen Fachdatensatz. Android-Kopfwerte
 * liegen in einem klar abgegrenzten Header-Namespace, während Firmen, Personal, Geräte, Arbeiten,
 * Abnahmen, Einweisungen, Besonderheiten, Mängel und Notizen einzelne versionierte btb_entries sind.
 *
 * Der kurzzeitig verwendete Gesamt-Bridge-Eintrag TB_HELFER_ANDROID_V1 wird nur noch gelesen und
 * nach erfolgreicher Granularisierung entfernt. Fremde Portal-Einträge bleiben unangetastet.
 */
class Server1BtbApi(
    private val baseUrl: String = ServerConfig.BASE_URL
) {
    data class Snapshot(
        val serverId: String,
        val teamId: String,
        val clientEntityKey: String,
        val status: String,
        val payloadJson: String,
        val entityVersion: Long,
        val createdAtText: String?,
        val updatedAtText: String?,
        val baseStateJson: String,
        val requiresGranularMigration: Boolean
    )

    sealed interface Result {
        data class Success(val items: List<Snapshot>, val activeBtbId: String?) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    sealed interface MutationResult {
        data class Applied(val snapshot: Snapshot) : MutationResult
        data class Deleted(val serverId: String, val entityVersion: Long) : MutationResult
        data class Conflict(val currentVersion: Long?, val detail: String) : MutationResult
        data object Unsupported : MutationResult
        data object SessionInvalid : MutationResult
        data class Error(val message: String) : MutationResult
    }

    data class Participant(
        val userId: String,
        val displayName: String?,
        val status: String,
        val isTeamLead: Boolean
    )

    data class Access(
        val snapshot: Snapshot,
        val active: Boolean,
        val isTeamLead: Boolean,
        val participantStatus: String?,
        val canFinalize: Boolean,
        val canViewPdf: Boolean,
        val participants: List<Participant>
    )

    sealed interface AccessResult {
        data class Success(val access: Access) : AccessResult
        data object Unsupported : AccessResult
        data object SessionInvalid : AccessResult
        data class Error(val message: String) : AccessResult
    }

    sealed interface LifecycleResult {
        data class Success(val access: Access) : LifecycleResult
        data class ParticipantsNotFinished(val participants: List<Participant>) : LifecycleResult
        data class Conflict(val code: String) : LifecycleResult
        data object Unsupported : LifecycleResult
        data object SessionInvalid : LifecycleResult
        data class Error(val message: String) : LifecycleResult
    }

    private data class ManagedEntry(
        val id: String,
        val section: String,
        val slot: String,
        val sortOrder: Int,
        val payload: JSONObject,
        val version: Long
    ) {
        fun stateJson(): JSONObject = JSONObject()
            .put("section", section)
            .put("sort_order", sortOrder)
            .put("payload", JSONObject(payload.toString()))
    }

    private data class DesiredEntry(
        val id: String,
        val section: String,
        val slot: String,
        val sortOrder: Int,
        val payload: JSONObject
    ) {
        fun stateJson(): JSONObject = JSONObject()
            .put("section", section)
            .put("sort_order", sortOrder)
            .put("payload", JSONObject(payload.toString()))
    }

    private data class Detail(
        val snapshot: Snapshot,
        val btb: JSONObject,
        val header: JSONObject,
        val ownedHeaderState: JSONObject,
        val managedEntries: Map<String, ManagedEntry>,
        val legacyBridgeEntry: ManagedEntry?,
        val activeBtbId: String?
    )

    private data class TeamIndex(val activeBtbId: String?, val ids: List<String>)

    private sealed interface TeamIndexResult {
        data class Success(val index: TeamIndex) : TeamIndexResult
        data object Unsupported : TeamIndexResult
        data object SessionInvalid : TeamIndexResult
        data class Error(val message: String) : TeamIndexResult
    }

    fun loadTeamBtbs(token: String, worksiteId: String, teamId: String): Result {
        val index = when (val result = loadTeamIndex(token, worksiteId, teamId)) {
            TeamIndexResult.Unsupported -> return Result.Unsupported
            TeamIndexResult.SessionInvalid -> return Result.SessionInvalid
            is TeamIndexResult.Error -> return Result.Error(result.message)
            is TeamIndexResult.Success -> result.index
        }
        val snapshots = mutableListOf<Snapshot>()
        for (id in index.ids) {
            when (val detail = loadBtb(token, id, teamId, index.activeBtbId)) {
                DetailResult.Unsupported -> return Result.Unsupported
                DetailResult.SessionInvalid -> return Result.SessionInvalid
                is DetailResult.Error -> return Result.Error(detail.message)
                is DetailResult.Success -> snapshots += detail.detail.snapshot
            }
        }
        return Result.Success(snapshots, index.activeBtbId)
    }

    fun loadBtbAccess(
        token: String,
        worksiteId: String,
        teamId: String,
        serverBtbId: String
    ): AccessResult {
        val index = when (val result = loadTeamIndex(token, worksiteId, teamId)) {
            TeamIndexResult.Unsupported -> return AccessResult.Unsupported
            TeamIndexResult.SessionInvalid -> return AccessResult.SessionInvalid
            is TeamIndexResult.Error -> return AccessResult.Error(result.message)
            is TeamIndexResult.Success -> result.index
        }
        return when (val loaded = loadBtb(token, serverBtbId, teamId, index.activeBtbId)) {
            DetailResult.Unsupported -> AccessResult.Unsupported
            DetailResult.SessionInvalid -> AccessResult.SessionInvalid
            is DetailResult.Error -> AccessResult.Error(loaded.message)
            is DetailResult.Success -> AccessResult.Success(accessFromDetail(loaded.detail))
        }
    }

    fun shiftFinish(
        token: String,
        worksiteId: String,
        teamId: String,
        serverBtbId: String
    ): LifecycleResult = lifecycleMutation(
        token = token,
        worksiteId = worksiteId,
        teamId = teamId,
        serverBtbId = serverBtbId,
        suffix = "shift-finish",
        path = "/api/v1/btbs/${encode(serverBtbId)}/shift-finish",
        body = JSONObject()
    )

    fun shiftResume(
        token: String,
        worksiteId: String,
        teamId: String,
        serverBtbId: String
    ): LifecycleResult = lifecycleMutation(
        token = token,
        worksiteId = worksiteId,
        teamId = teamId,
        serverBtbId = serverBtbId,
        suffix = "shift-resume",
        path = "/api/v1/btbs/${encode(serverBtbId)}/shift-resume",
        body = JSONObject()
    )

    fun finalizeBtb(
        token: String,
        worksiteId: String,
        teamId: String,
        serverBtbId: String,
        allowUnfinished: Boolean
    ): LifecycleResult = lifecycleMutation(
        token = token,
        worksiteId = worksiteId,
        teamId = teamId,
        serverBtbId = serverBtbId,
        suffix = if (allowUnfinished) "finalize-override" else "finalize",
        path = "/api/v1/btbs/${encode(serverBtbId)}/finalize",
        body = JSONObject().put("allow_unfinished", allowUnfinished)
    )

    fun applyPending(
        token: String,
        change: SyncCollabPendingChangeEntity,
        baseStateJson: String? = null
    ): MutationResult {
        if (!change.entityType.equals(SyncCollabPendingChangeEntity.TYPE_BTB, ignoreCase = true)) {
            return MutationResult.Error("Nicht unterstützter Server-1.0-BTB-Typ: ${change.entityType}")
        }
        if (change.operation.equals(SyncCollabPendingChangeEntity.OP_DELETE, ignoreCase = true)) {
            return deletePending(token, change)
        }
        if (!change.operation.equals(SyncCollabPendingChangeEntity.OP_UPSERT, ignoreCase = true)) {
            return MutationResult.Error("Nicht unterstützte Server-1.0-BTB-Operation: ${change.operation}")
        }

        val teamId = change.groupId?.trim().orEmpty()
        if (teamId.isBlank()) return MutationResult.Error("Team-Zuordnung für BTB fehlt.")
        val desired = runCatching { JSONObject(change.payloadJson) }.getOrElse { JSONObject() }
        if (desired.optString("btbId").isBlank()) {
            change.clientEntityKey?.takeIf { it.isNotBlank() }?.let { desired.put("btbId", it) }
        }
        val clientKey = desired.optString("btbId").trim()
            .ifBlank { change.clientEntityKey?.trim().orEmpty() }
        if (clientKey.isBlank()) return MutationResult.Error("Lokale BTB-Identität fehlt.")

        val desiredStatus = change.status?.uppercase().orEmpty().ifBlank { "ENTWURF" }
        if (desiredStatus !in setOf("ENTWURF", "FERTIG")) {
            return MutationResult.Error("Unbekannter lokaler BTB-Status: $desiredStatus")
        }

        var detail = if (change.serverId.isNullOrBlank()) {
            when (val created = createBtb(token, change, teamId, clientKey, desired)) {
                is DetailMutationResult.Success -> created.detail
                is DetailMutationResult.Conflict ->
                    return MutationResult.Conflict(created.currentVersion, created.detail)
                DetailMutationResult.Unsupported -> return MutationResult.Unsupported
                DetailMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                is DetailMutationResult.Error -> return MutationResult.Error(created.message)
            }
        } else {
            val active = when (val index = loadTeamIndex(token, change.worksiteServerId, teamId)) {
                TeamIndexResult.Unsupported -> return MutationResult.Unsupported
                TeamIndexResult.SessionInvalid -> return MutationResult.SessionInvalid
                is TeamIndexResult.Error -> return MutationResult.Error(index.message)
                is TeamIndexResult.Success -> index.index.activeBtbId
            }
            when (val loaded = loadBtb(token, change.serverId, teamId, active)) {
                DetailResult.Unsupported -> return MutationResult.Unsupported
                DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
                is DetailResult.Error -> return MutationResult.Error(loaded.message)
                is DetailResult.Success -> loaded.detail
            }
        }

        val parsedBase = baseStateJson?.let { runCatching { JSONObject(it) }.getOrNull() }

        if (
            desiredStatus == "ENTWURF" &&
            detail.btb.optString("status").uppercase() == "FINALIZED" &&
            detail.activeBtbId != detail.snapshot.serverId
        ) {
            when (
                val reopened = jsonMutation(
                    token,
                    "/api/v1/btbs/${encode(detail.snapshot.serverId)}/reopen",
                    "POST",
                    subChangeId(change.clientChangeId, "reopen"),
                    JSONObject()
                )
            ) {
                JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                is JsonMutationResult.Conflict ->
                    return MutationResult.Conflict(reopened.currentVersion, reopened.detail)
                is JsonMutationResult.Error -> return MutationResult.Error(reopened.message)
                is JsonMutationResult.Success -> Unit
            }
            detail = when (val reloaded = loadBtb(token, detail.snapshot.serverId, teamId, detail.snapshot.serverId)) {
                DetailResult.Unsupported -> return MutationResult.Unsupported
                DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
                is DetailResult.Error -> return MutationResult.Error(reloaded.message)
                is DetailResult.Success -> reloaded.detail
            }
        }

        val desiredHeaderState = headerStateFromLocal(desired, clientKey)
        val baseHeaderState = parsedBase?.optJSONObject("header")
        val localHeaderChanged = baseHeaderState == null || !jsonEquivalent(desiredHeaderState, baseHeaderState)

        if (localHeaderChanged) {
            if (
                baseHeaderState != null &&
                !jsonEquivalent(detail.ownedHeaderState, baseHeaderState) &&
                !jsonEquivalent(detail.ownedHeaderState, desiredHeaderState)
            ) {
                return MutationResult.Conflict(detail.snapshot.entityVersion, "BTB_HEADER_CONFLICT")
            }
            if (
                baseHeaderState == null &&
                change.serverId != null &&
                change.baseVersion != null &&
                detail.snapshot.entityVersion != change.baseVersion &&
                !jsonEquivalent(detail.ownedHeaderState, desiredHeaderState)
            ) {
                return MutationResult.Conflict(detail.snapshot.entityVersion, "BTB_HEADER_VERSION_CONFLICT")
            }
            if (!jsonEquivalent(detail.ownedHeaderState, desiredHeaderState)) {
                val body = JSONObject()
                    .put("base_version", detail.snapshot.entityVersion)
                    .put("work_date", desired.nullableValue("datumIso"))
                    .put(
                        "started_at",
                        combinedDateTime(desired.nullableString("datumIso"), desired.nullableString("startUhrzeit"))
                            ?: JSONObject.NULL
                    )
                    .put(
                        "ended_at",
                        combinedDateTime(
                            desired.nullableString("endeDatumIso") ?: desired.nullableString("datumIso"),
                            desired.nullableString("endeUhrzeit")
                        ) ?: JSONObject.NULL
                    )
                    .put("header", mergedHeader(detail.header, desired, clientKey, change.clientChangeId))
                when (
                    val updated = jsonMutation(
                        token,
                        "/api/v1/btbs/${encode(detail.snapshot.serverId)}/header",
                        "PATCH",
                        subChangeId(change.clientChangeId, "header"),
                        body
                    )
                ) {
                    JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                    JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                    is JsonMutationResult.Conflict ->
                        return MutationResult.Conflict(updated.currentVersion, updated.detail)
                    is JsonMutationResult.Error -> return MutationResult.Error(updated.message)
                    is JsonMutationResult.Success -> Unit
                }
                detail = when (val reloaded = loadBtb(token, detail.snapshot.serverId, teamId, detail.activeBtbId)) {
                    DetailResult.Unsupported -> return MutationResult.Unsupported
                    DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
                    is DetailResult.Error -> return MutationResult.Error(reloaded.message)
                    is DetailResult.Success -> reloaded.detail
                }
            }
        }

        val desiredEntries = desiredEntries(
            desired,
            change.worksiteServerId,
            teamId,
            clientKey
        ).associateBy { it.slot }
        val baseEntries = parsedBase?.optJSONObject("entries") ?: JSONObject()
        val currentEntries = detail.managedEntries

        for ((slot, desiredEntry) in desiredEntries) {
            val baseEntry = baseEntries.optJSONObject(slot)
            val currentEntry = currentEntries[slot]
            val desiredState = desiredEntry.stateJson()

            if (baseEntry != null) {
                val baseState = entryStateFromBase(baseEntry)
                if (jsonEquivalent(desiredState, baseState)) continue
                if (currentEntry == null) {
                    return MutationResult.Conflict(detail.snapshot.entityVersion, "BTB_ENTRY_DELETED_CONFLICT:$slot")
                }
                val currentState = currentEntry.stateJson()
                if (!jsonEquivalent(currentState, baseState) && !jsonEquivalent(currentState, desiredState)) {
                    return MutationResult.Conflict(currentEntry.version, "BTB_ENTRY_CONFLICT:$slot")
                }
                if (!jsonEquivalent(currentState, desiredState)) {
                    if (currentEntry.section != desiredEntry.section) {
                        return MutationResult.Conflict(currentEntry.version, "BTB_ENTRY_SECTION_CONFLICT:$slot")
                    }
                    when (
                        val updated = jsonMutation(
                            token,
                            "/api/v1/btbs/${encode(detail.snapshot.serverId)}/entries/${encode(currentEntry.id)}",
                            "PATCH",
                            subChangeId(change.clientChangeId, "entry-update-${slotToken(slot)}"),
                            JSONObject()
                                .put("base_version", currentEntry.version)
                                .put("sort_order", desiredEntry.sortOrder)
                                .put("payload", desiredEntry.payload)
                        )
                    ) {
                        JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                        JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                        is JsonMutationResult.Conflict ->
                            return MutationResult.Conflict(updated.currentVersion, updated.detail)
                        is JsonMutationResult.Error -> return MutationResult.Error(updated.message)
                        is JsonMutationResult.Success -> Unit
                    }
                }
            } else {
                if (currentEntry != null && !jsonEquivalent(currentEntry.stateJson(), desiredState)) {
                    return MutationResult.Conflict(currentEntry.version, "BTB_ENTRY_CREATE_CONFLICT:$slot")
                }
                if (currentEntry == null) {
                    when (
                        val created = jsonMutation(
                            token,
                            "/api/v1/btbs/${encode(detail.snapshot.serverId)}/entries",
                            "POST",
                            subChangeId(change.clientChangeId, "entry-create-${slotToken(slot)}"),
                            JSONObject()
                                .put("id", desiredEntry.id)
                                .put("section", desiredEntry.section)
                                .put("sort_order", desiredEntry.sortOrder)
                                .put("payload", desiredEntry.payload)
                        )
                    ) {
                        JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                        JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                        is JsonMutationResult.Conflict ->
                            return MutationResult.Conflict(created.currentVersion, created.detail)
                        is JsonMutationResult.Error -> return MutationResult.Error(created.message)
                        is JsonMutationResult.Success -> Unit
                    }
                }
            }
        }

        val baseSlots = mutableListOf<String>()
        val baseKeys = baseEntries.keys()
        while (baseKeys.hasNext()) baseSlots += baseKeys.next()
        for (slot in baseSlots) {
            if (slot in desiredEntries) continue
            val baseEntry = baseEntries.optJSONObject(slot) ?: continue
            val currentEntry = currentEntries[slot] ?: continue
            if (!jsonEquivalent(currentEntry.stateJson(), entryStateFromBase(baseEntry))) {
                return MutationResult.Conflict(currentEntry.version, "BTB_ENTRY_DELETE_CONFLICT:$slot")
            }
            when (
                val deleted = jsonMutation(
                    token,
                    "/api/v1/btbs/${encode(detail.snapshot.serverId)}/entries/${encode(currentEntry.id)}",
                    "DELETE",
                    subChangeId(change.clientChangeId, "entry-delete-${slotToken(slot)}"),
                    JSONObject()
                )
            ) {
                JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                is JsonMutationResult.Conflict ->
                    return MutationResult.Conflict(deleted.currentVersion, deleted.detail)
                is JsonMutationResult.Error -> return MutationResult.Error(deleted.message)
                is JsonMutationResult.Success -> Unit
            }
        }

        detail.legacyBridgeEntry?.let { bridge ->
            when (
                val deleted = jsonMutation(
                    token,
                    "/api/v1/btbs/${encode(detail.snapshot.serverId)}/entries/${encode(bridge.id)}",
                    "DELETE",
                    subChangeId(change.clientChangeId, "legacy-bridge-delete"),
                    JSONObject()
                )
            ) {
                JsonMutationResult.Unsupported -> return MutationResult.Unsupported
                JsonMutationResult.SessionInvalid -> return MutationResult.SessionInvalid
                is JsonMutationResult.Conflict ->
                    return MutationResult.Conflict(deleted.currentVersion, deleted.detail)
                is JsonMutationResult.Error -> return MutationResult.Error(deleted.message)
                is JsonMutationResult.Success -> Unit
            }
        }

        val activeBeforeFinalize = when (val index = loadTeamIndex(token, change.worksiteServerId, teamId)) {
            TeamIndexResult.Unsupported -> return MutationResult.Unsupported
            TeamIndexResult.SessionInvalid -> return MutationResult.SessionInvalid
            is TeamIndexResult.Error -> return MutationResult.Error(index.message)
            is TeamIndexResult.Success -> index.index.activeBtbId
        }
        val statusBeforeFinalize = when (
            val reloaded = loadBtb(token, detail.snapshot.serverId, teamId, activeBeforeFinalize)
        ) {
            DetailResult.Unsupported -> return MutationResult.Unsupported
            DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
            is DetailResult.Error -> return MutationResult.Error(reloaded.message)
            is DetailResult.Success -> reloaded.detail
        }

        // Serververtrag 25.09.2026: Fachinhalt und Lifecycle sind getrennt.
        // Ein normaler Dirty-BTB darf niemals implizit /finalize aufrufen. Helfer verwenden
        // shift-finish/shift-resume; nur der Haupt-TB finalisiert nach expliziter Benutzeraktion.
        // desiredStatus=FERTIG bleibt nur fuer Alt-Queues lesbar und wird hier NICHT in einen
        // Lifecycle-Aufruf uebersetzt.

        val finalIndex = when (val index = loadTeamIndex(token, change.worksiteServerId, teamId)) {
            TeamIndexResult.Unsupported -> return MutationResult.Unsupported
            TeamIndexResult.SessionInvalid -> return MutationResult.SessionInvalid
            is TeamIndexResult.Error -> return MutationResult.Error(index.message)
            is TeamIndexResult.Success -> index.index
        }
        val finalDetail = when (
            val reloaded = loadBtb(token, detail.snapshot.serverId, teamId, finalIndex.activeBtbId)
        ) {
            DetailResult.Unsupported -> return MutationResult.Unsupported
            DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
            is DetailResult.Error -> return MutationResult.Error(reloaded.message)
            is DetailResult.Success -> reloaded.detail
        }
        return MutationResult.Applied(finalDetail.snapshot)
    }

    private fun deletePending(
        token: String,
        change: SyncCollabPendingChangeEntity
    ): MutationResult {
        val serverId = change.serverId?.trim().orEmpty()
        val teamId = change.groupId?.trim().orEmpty()
        if (serverId.isBlank()) {
            return MutationResult.Error("Server-ID für BTB-Löschung fehlt.")
        }
        if (teamId.isBlank()) {
            return MutationResult.Error("Team-Zuordnung für BTB-Löschung fehlt.")
        }

        val active = when (val index = loadTeamIndex(token, change.worksiteServerId, teamId)) {
            TeamIndexResult.Unsupported -> return MutationResult.Unsupported
            TeamIndexResult.SessionInvalid -> return MutationResult.SessionInvalid
            is TeamIndexResult.Error -> return MutationResult.Error(index.message)
            is TeamIndexResult.Success -> index.index.activeBtbId
        }

        val current = when (val loaded = loadBtb(token, serverId, teamId, active)) {
            DetailResult.Unsupported -> {
                return MutationResult.Deleted(
                    serverId,
                    (change.baseVersion ?: 0L).coerceAtLeast(0L) + 1L
                )
            }
            DetailResult.SessionInvalid -> return MutationResult.SessionInvalid
            is DetailResult.Error -> return MutationResult.Error(loaded.message)
            is DetailResult.Success -> loaded.detail
        }

        val baseVersion = change.baseVersion
        if (baseVersion != null && current.snapshot.entityVersion != baseVersion) {
            return MutationResult.Conflict(
                current.snapshot.entityVersion,
                "VERSION_CONFLICT"
            )
        }

        return when (
            val deleted = jsonMutation(
                token,
                "/api/v1/btbs/${encode(serverId)}",
                "DELETE",
                subChangeId(change.clientChangeId, "delete"),
                JSONObject()
            )
        ) {
            JsonMutationResult.SessionInvalid -> MutationResult.SessionInvalid
            JsonMutationResult.Unsupported -> MutationResult.Deleted(
                serverId,
                current.snapshot.entityVersion + 1L
            )
            is JsonMutationResult.Conflict ->
                MutationResult.Conflict(deleted.currentVersion, deleted.detail)
            is JsonMutationResult.Error -> MutationResult.Error(deleted.message)
            is JsonMutationResult.Success -> MutationResult.Deleted(
                serverId,
                current.snapshot.entityVersion + 1L
            )
        }
    }

    private sealed interface DetailMutationResult {
        data class Success(val detail: Detail) : DetailMutationResult
        data class Conflict(val currentVersion: Long?, val detail: String) : DetailMutationResult
        data object Unsupported : DetailMutationResult
        data object SessionInvalid : DetailMutationResult
        data class Error(val message: String) : DetailMutationResult
    }

    private fun createBtb(
        token: String,
        change: SyncCollabPendingChangeEntity,
        teamId: String,
        clientKey: String,
        desired: JSONObject
    ): DetailMutationResult {
        val body = JSONObject()
            .put("team_id", teamId)
            .put("work_date", desired.nullableValue("datumIso"))
            .put(
                "started_at",
                combinedDateTime(desired.nullableString("datumIso"), desired.nullableString("startUhrzeit"))
                    ?: JSONObject.NULL
            )
            .put(
                "ended_at",
                combinedDateTime(
                    desired.nullableString("endeDatumIso") ?: desired.nullableString("datumIso"),
                    desired.nullableString("endeUhrzeit")
                ) ?: JSONObject.NULL
            )
            .put("header", mergedHeader(JSONObject(), desired, clientKey, change.clientChangeId))

        val created = jsonMutation(
            token,
            "/api/v1/worksites/${encode(change.worksiteServerId)}/btbs",
            "POST",
            subChangeId(change.clientChangeId, "create"),
            body
        )
        val serverId = when (created) {
            JsonMutationResult.Unsupported -> return DetailMutationResult.Unsupported
            JsonMutationResult.SessionInvalid -> return DetailMutationResult.SessionInvalid
            is JsonMutationResult.Conflict ->
                return DetailMutationResult.Conflict(created.currentVersion, created.detail)
            is JsonMutationResult.Error -> return DetailMutationResult.Error(created.message)
            is JsonMutationResult.Success -> created.body.optString("id").trim()
                .takeIf { it.isNotBlank() }
                ?: return DetailMutationResult.Error("Server lieferte keine BTB-ID.")
        }
        return when (val detail = loadBtb(token, serverId, teamId, serverId)) {
            DetailResult.Unsupported -> DetailMutationResult.Unsupported
            DetailResult.SessionInvalid -> DetailMutationResult.SessionInvalid
            is DetailResult.Error -> DetailMutationResult.Error(detail.message)
            is DetailResult.Success -> DetailMutationResult.Success(detail.detail)
        }
    }

    private sealed interface DetailResult {
        data class Success(val detail: Detail) : DetailResult
        data object Unsupported : DetailResult
        data object SessionInvalid : DetailResult
        data class Error(val message: String) : DetailResult
    }

    private fun loadTeamIndex(token: String, worksiteId: String, teamId: String): TeamIndexResult {
        val connection = openConnection(
            "/api/v1/worksites/${encode(worksiteId)}/teams/${encode(teamId)}/btbs"
        )
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return TeamIndexResult.Error("BTB-Liste konnte nicht gelesen werden.")
                    val rows = root.optJSONArray("btbs") ?: JSONArray()
                    val ids = buildList {
                        for (index in 0 until rows.length()) {
                            rows.optJSONObject(index)?.optString("id")?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?.let(::add)
                        }
                    }
                    TeamIndexResult.Success(
                        TeamIndex(root.nullableString("active_btb_id"), ids)
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> TeamIndexResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> TeamIndexResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) TeamIndexResult.SessionInvalid
                    else TeamIndexResult.Error(errorMessage(body, code))
                else -> TeamIndexResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            TeamIndexResult.Error(error.message ?: "Server-1.0-BTB-Liste konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun loadBtb(
        token: String,
        btbId: String,
        expectedTeamId: String,
        activeBtbId: String?
    ): DetailResult {
        val connection = openConnection("/api/v1/btbs/${encode(btbId)}")
        return try {
            authorize(connection, token)
            val code = connection.responseCode
            val body = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return DetailResult.Error("BTB-Detail konnte nicht gelesen werden.")
                    val btb = root.optJSONObject("btb")
                        ?: return DetailResult.Error("BTB-Kopfdaten fehlen.")
                    val teamId = btb.optString("team_id").trim()
                    if (teamId.isBlank() || teamId != expectedTeamId) {
                        return DetailResult.Error("BTB-Teamzuordnung ist inkonsistent.")
                    }
                    DetailResult.Success(detailFromJson(root, btb, teamId, activeBtbId))
                }
                HttpURLConnection.HTTP_NOT_FOUND -> DetailResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> DetailResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(body)) DetailResult.SessionInvalid
                    else DetailResult.Error(errorMessage(body, code))
                else -> DetailResult.Error(errorMessage(body, code))
            }
        } catch (error: Exception) {
            DetailResult.Error(error.message ?: "Server-1.0-BTB-Detail konnte nicht geladen werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun detailFromJson(
        root: JSONObject,
        btb: JSONObject,
        teamId: String,
        activeBtbId: String?
    ): Detail {
        val entries = root.optJSONArray("entries") ?: JSONArray()
        val managed = linkedMapOf<String, ManagedEntry>()
        var legacyBridge: ManagedEntry? = null
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val id = entry.optString("id").trim()
            if (id.isBlank()) continue
            val section = entry.optString("section").trim()
            val payload = entry.optJSONObject("payload") ?: JSONObject()
            val version = entry.optLong("version", 1L).coerceAtLeast(1L)
            if (section == LEGACY_ANDROID_SECTION) {
                legacyBridge = ManagedEntry(
                    id,
                    section,
                    LEGACY_ANDROID_SECTION,
                    entry.optInt("sort_order", 0),
                    JSONObject(payload.toString()),
                    version
                )
                continue
            }
            if (!payload.optBoolean(MANAGED_KEY, false)) continue
            val slot = payload.optString(SLOT_KEY).trim()
            if (slot.isBlank()) continue
            managed[slot] = ManagedEntry(
                id,
                section,
                slot,
                entry.optInt("sort_order", 0),
                JSONObject(payload.toString()),
                version
            )
        }

        val header = btb.optJSONObject("header") ?: JSONObject()
        val legacyPayload = legacyBridge?.payload
        val serverId = btb.optString("id").trim()
        val clientKey = header.optJSONObject(ANDROID_META_KEY)?.nullableString("client_btb_id")
            ?: header.nullableString(ANDROID_CLIENT_KEY)
            ?: legacyPayload?.nullableString("btbId")
            ?: serverId

        val localPayload = if (managed.isEmpty() && legacyPayload != null) {
            JSONObject(legacyPayload.toString())
        } else {
            localPayloadFromGranular(btb, header, managed, clientKey)
        }
        canonicalizeLocalPayload(localPayload, btb, header, clientKey, activeBtbId == serverId)
        localPayload.put(SERVER_ACTIVE_KEY, activeBtbId == serverId)
        localPayload.put(
            SERVER_CURRENT_USER_KEY,
            root.optJSONObject("current_user")?.let { JSONObject(it.toString()) } ?: JSONObject()
        )
        localPayload.put(
            SERVER_PARTICIPANTS_KEY,
            root.optJSONArray("participants")?.let { JSONArray(it.toString()) } ?: JSONArray()
        )
        localPayload.put(
            SERVER_PHOTOS_KEY,
            root.optJSONArray("photos")?.let { JSONArray(it.toString()) } ?: JSONArray()
        )
        val finalPdf = root.optJSONObject("final_pdf")
        localPayload.put(SERVER_STATUS_KEY, btb.optString("status").uppercase())
        localPayload.put(SERVER_FINAL_PDF_AVAILABLE_KEY, finalPdf != null)
        if (finalPdf != null) {
            finalPdf.nullableString("sha256")
                ?.let { localPayload.put(SERVER_FINAL_PDF_SHA256_KEY, it) }
        } else {
            localPayload.remove(SERVER_FINAL_PDF_SHA256_KEY)
        }

        val ownedHeaderState = headerStateFromServer(btb, header, clientKey)
        val baseState = JSONObject()
            .put("header", ownedHeaderState)
            .put("entries", JSONObject().apply {
                managed.forEach { (slot, entry) ->
                    put(
                        slot,
                        JSONObject()
                            .put("id", entry.id)
                            .put("version", entry.version)
                            .put("section", entry.section)
                            .put("sort_order", entry.sortOrder)
                            .put("payload", entry.payload)
                    )
                }
            })

        val status = if (
            btb.optString("status").uppercase() == "FINALIZED" &&
            activeBtbId != serverId
        ) "FERTIG" else "ENTWURF"

        val snapshot = Snapshot(
            serverId = serverId,
            teamId = teamId,
            clientEntityKey = clientKey,
            status = status,
            payloadJson = localPayload.toString(),
            entityVersion = btb.optLong("version", 1L).coerceAtLeast(1L),
            createdAtText = btb.nullableString("created_at"),
            updatedAtText = btb.nullableString("updated_at"),
            baseStateJson = baseState.toString(),
            requiresGranularMigration = legacyBridge != null
        )
        return Detail(
            snapshot,
            JSONObject(btb.toString()),
            JSONObject(header.toString()),
            ownedHeaderState,
            managed,
            legacyBridge,
            activeBtbId
        )
    }

    private fun localPayloadFromGranular(
        btb: JSONObject,
        header: JSONObject,
        entries: Map<String, ManagedEntry>,
        clientKey: String
    ): JSONObject {
        val local = androidHeaderMeta(header)
        local.put("btbId", clientKey)
        val slotMap = JSONObject()

        data class SlottedJson(val sort: Int, val slot: String, val value: JSONObject)
        data class SlottedText(val sort: Int, val slot: String, val value: String)
        data class FirmParts(
            var base: JSONObject,
            var baseSlot: String? = null,
            val personal: MutableList<SlottedJson> = mutableListOf(),
            val devices: MutableList<SlottedJson> = mutableListOf(),
            val work: MutableList<SlottedJson> = mutableListOf()
        )

        val firms = linkedMapOf<String, FirmParts>()
        val abnahmen = mutableListOf<SlottedJson>()
        val einweisung = mutableListOf<SlottedJson>()
        val besonderheiten = mutableListOf<SlottedJson>()
        val maengel = mutableListOf<SlottedJson>()
        val freePersonal = mutableListOf<SlottedText>()
        val freeDevices = mutableListOf<SlottedText>()
        val freeWork = mutableListOf<SlottedJson>()
        val notes = mutableListOf<SlottedText>()

        fun firm(id: String): FirmParts = firms.getOrPut(id) {
            FirmParts(JSONObject().put("firmaId", id))
        }
        fun putSlots(key: String, slots: List<String>) {
            if (slots.isNotEmpty()) {
                slotMap.put(key, JSONArray().apply { slots.forEach { slot -> put(slot) } })
            }
        }

        entries.values.forEach { entry ->
            val payload = cleanManagedPayload(entry.payload)
            when (entry.section.uppercase()) {
                SECTION_FIRMA -> {
                    val id = payload.nullableString("firmaId") ?: return@forEach
                    firm(id).apply {
                        base = JSONObject(payload.toString()).put("firmaId", id)
                        baseSlot = entry.slot
                    }
                }
                SECTION_PERSONAL -> {
                    val firmId = payload.nullableString("firmaId")
                    if (firmId == null) {
                        payload.nullableString("text")?.let {
                            freePersonal += SlottedText(entry.sortOrder, entry.slot, it)
                        }
                    } else {
                        firm(firmId).personal += SlottedJson(
                            entry.sortOrder,
                            entry.slot,
                            JSONObject(payload.toString()).apply { remove("firmaId") }
                        )
                    }
                }
                SECTION_GERAET -> {
                    val firmId = payload.nullableString("firmaId")
                    if (firmId == null) {
                        payload.nullableString("text")?.let {
                            freeDevices += SlottedText(entry.sortOrder, entry.slot, it)
                        }
                    } else {
                        firm(firmId).devices += SlottedJson(
                            entry.sortOrder,
                            entry.slot,
                            JSONObject(payload.toString()).apply { remove("firmaId") }
                        )
                    }
                }
                SECTION_ARBEIT -> {
                    val firmId = payload.nullableString("firmaId")
                    if (firmId == null) {
                        freeWork += SlottedJson(entry.sortOrder, entry.slot, payload)
                    } else {
                        firm(firmId).work += SlottedJson(
                            entry.sortOrder,
                            entry.slot,
                            JSONObject(payload.toString()).apply { remove("firmaId") }
                        )
                    }
                }
                SECTION_ABNAHME -> abnahmen += SlottedJson(entry.sortOrder, entry.slot, payload)
                SECTION_EINWEISUNG -> einweisung += SlottedJson(entry.sortOrder, entry.slot, payload)
                SECTION_BESONDERHEIT -> besonderheiten += SlottedJson(entry.sortOrder, entry.slot, payload)
                SECTION_MANGEL -> maengel += SlottedJson(entry.sortOrder, entry.slot, payload)
                SECTION_NOTIZEN -> payload.nullableString("text")?.let {
                    notes += SlottedText(entry.sortOrder, entry.slot, it)
                }
            }
        }

        local.put("firmen", JSONArray().apply {
            firms.forEach { (firmId, parts) ->
                val personal = parts.personal.sortedBy { it.sort }
                val devices = parts.devices.sortedBy { it.sort }
                val work = parts.work.sortedBy { it.sort }
                put(
                    JSONObject(parts.base.toString())
                        .put("personal", JSONArray().apply { personal.forEach { put(it.value) } })
                        .put("geraete", JSONArray().apply { devices.forEach { put(it.value) } })
                        .put("leistungen", JSONArray().apply { work.forEach { put(it.value) } })
                )
                parts.baseSlot?.let { putSlots("firma.base:$firmId", listOf(it)) }
                putSlots("firma.personal:$firmId", personal.map { it.slot })
                putSlots("firma.geraete:$firmId", devices.map { it.slot })
                putSlots("firma.arbeit:$firmId", work.map { it.slot })
            }
        })

        fun jsonArray(values: List<SlottedJson>): JSONArray = JSONArray().apply {
            values.sortedBy { it.sort }.forEach { put(it.value) }
        }
        fun slots(values: List<SlottedJson>): List<String> =
            values.sortedBy { it.sort }.map { it.slot }

        local.put("arbeiten", jsonArray(freeWork))
        local.put("abnahmen", jsonArray(abnahmen))
        local.put("einweisungUnterweisung", jsonArray(einweisung))
        local.put("besonderheiten", jsonArray(besonderheiten))
        local.put("maengelSchaeden", jsonArray(maengel))
        local.put("personal", JSONArray().apply {
            freePersonal.sortedBy { it.sort }.forEach { put(it.value) }
        })
        local.put("geraete", JSONArray().apply {
            freeDevices.sortedBy { it.sort }.forEach { put(it.value) }
        })

        putSlots("arbeiten", slots(freeWork))
        putSlots("abnahmen", slots(abnahmen))
        putSlots("einweisungUnterweisung", slots(einweisung))
        putSlots("besonderheiten", slots(besonderheiten))
        putSlots("maengelSchaeden", slots(maengel))
        putSlots("personal", freePersonal.sortedBy { it.sort }.map { it.slot })
        putSlots("geraete", freeDevices.sortedBy { it.sort }.map { it.slot })

        if (notes.isNotEmpty()) {
            val ordered = notes.sortedBy { it.sort }
            local.put("notizen", ordered.joinToString("\n") { it.value })
            putSlots("notizen", ordered.map { it.slot })
        }
        if (slotMap.length() > 0) local.put(LOCAL_ENTRY_SLOTS_KEY, slotMap)
        return local
    }

    private fun canonicalizeLocalPayload(
        local: JSONObject,
        btb: JSONObject,
        header: JSONObject,
        clientKey: String,
        isActive: Boolean
    ) {
        local.put("btbId", clientKey)
        btb.nullableString("work_date")?.let { local.put("datumIso", it) }
        val endedDate = datePart(btb.nullableString("ended_at"))
        val workDate = btb.nullableString("work_date")
        if (endedDate != null && endedDate != workDate) local.put("endeDatumIso", endedDate)
        else local.remove("endeDatumIso")
        timePart(btb.nullableString("started_at"))?.let { local.put("startUhrzeit", it) }
        timePart(btb.nullableString("ended_at"))?.let { local.put("endeUhrzeit", it) }
        if (local.optString("schicht").isBlank()) {
            local.put("schicht", header.optString("schicht").ifBlank { "TAG" })
        }

        val finalizedClosed = btb.optString("status").uppercase() == "FINALIZED" && !isActive
        local.put("istFertig", finalizedClosed)
        if (btb.optString("status").uppercase() == "FINALIZED") {
            local.put("dokumentVersion", maxOf(1, local.optInt("dokumentVersion", 0)))
        }
        epochMillis(btb.nullableString("finalized_at"))?.let { local.put("letzterAbschlussEpochMs", it) }
        epochMillis(btb.nullableString("created_at"))?.let { local.put("erstelltAmEpochMs", it) }
        epochMillis(btb.nullableString("updated_at"))?.let { local.put("geaendertAmEpochMs", it) }
        if (!local.has("includeFotos")) local.put("includeFotos", true)
        if (!local.has("includeBtbs")) local.put("includeBtbs", true)
        if (!local.has("maxFotosInPdf")) local.put("maxFotosInPdf", 60)
        local.put("fotos", JSONArray())
        if (!local.has("pdfAuswahl")) local.put("pdfAuswahl", JSONObject())
    }

    private fun desiredEntries(
        desired: JSONObject,
        worksiteId: String,
        teamId: String,
        clientKey: String
    ): List<DesiredEntry> {
        val out = mutableListOf<DesiredEntry>()
        val remembered = desired.optJSONObject(LOCAL_ENTRY_SLOTS_KEY) ?: JSONObject()
        val usedSlots = linkedSetOf<String>()

        fun rememberedSlot(key: String, index: Int): String? =
            remembered.optJSONArray(key)
                ?.optString(index)
                ?.trim()
                ?.takeIf { it.isNotBlank() && it !in usedSlots }

        fun nextSlot(prefix: String): String {
            var max = -1
            val keys = remembered.keys()
            while (keys.hasNext()) {
                val array = remembered.optJSONArray(keys.next()) ?: continue
                for (i in 0 until array.length()) {
                    val slot = array.optString(i)
                    if (slot.startsWith("$prefix:")) {
                        slot.substringAfterLast(':').toIntOrNull()?.let { max = maxOf(max, it) }
                    }
                }
            }
            while (true) {
                max += 1
                val candidate = "$prefix:$max"
                if (candidate !in usedSlots) return candidate
            }
        }

        fun chooseSlot(key: String, index: Int, prefix: String, fixed: String? = null): String {
            val slot = fixed
                ?.takeIf { it.isNotBlank() && it !in usedSlots }
                ?: rememberedSlot(key, index)
                ?: nextSlot(prefix)
            usedSlots += slot
            return slot
        }

        fun add(section: String, slot: String, sort: Int, raw: JSONObject) {
            val payload = JSONObject(raw.toString())
                .apply { remove(LOCAL_ENTRY_SLOTS_KEY) }
                .put(MANAGED_KEY, true)
                .put(SLOT_KEY, slot)
            out += DesiredEntry(
                deterministicEntryId(worksiteId, teamId, clientKey, slot),
                section,
                slot,
                sort,
                payload
            )
        }

        val firms = desired.optJSONArray("firmen") ?: JSONArray()
        for (firmIndex in 0 until firms.length()) {
            val firm = firms.optJSONObject(firmIndex) ?: continue
            val firmId = firm.nullableString("firmaId") ?: "legacy-$firmIndex"
            val base = JSONObject(firm.toString())
            val personal = base.optJSONArray("personal") ?: JSONArray()
            val devices = base.optJSONArray("geraete") ?: JSONArray()
            val work = base.optJSONArray("leistungen") ?: JSONArray()
            base.remove("personal")
            base.remove("geraete")
            base.remove("leistungen")
            base.put("firmaId", firmId)
            add(
                SECTION_FIRMA,
                chooseSlot("firma.base:$firmId", 0, "firma", "firma:$firmId"),
                firmIndex,
                base
            )

            for (index in 0 until personal.length()) {
                val item = personal.optJSONObject(index) ?: continue
                add(
                    SECTION_PERSONAL,
                    chooseSlot("firma.personal:$firmId", index, "firma:$firmId:personal"),
                    index,
                    JSONObject(item.toString()).put("firmaId", firmId)
                )
            }
            for (index in 0 until devices.length()) {
                val item = devices.optJSONObject(index) ?: continue
                add(
                    SECTION_GERAET,
                    chooseSlot("firma.geraete:$firmId", index, "firma:$firmId:geraet"),
                    index,
                    JSONObject(item.toString()).put("firmaId", firmId)
                )
            }
            for (index in 0 until work.length()) {
                val item = work.optJSONObject(index) ?: continue
                add(
                    SECTION_ARBEIT,
                    chooseSlot("firma.arbeit:$firmId", index, "firma:$firmId:arbeit"),
                    index,
                    JSONObject(item.toString()).put("firmaId", firmId)
                )
            }
        }

        val freePersonal = desired.optJSONArray("personal") ?: JSONArray()
        for (index in 0 until freePersonal.length()) {
            val value = freePersonal.optString(index).trim()
            if (value.isNotBlank()) {
                add(
                    SECTION_PERSONAL,
                    chooseSlot("personal", index, "personal-frei"),
                    index,
                    JSONObject().put("text", value)
                )
            }
        }
        val freeDevices = desired.optJSONArray("geraete") ?: JSONArray()
        for (index in 0 until freeDevices.length()) {
            val value = freeDevices.optString(index).trim()
            if (value.isNotBlank()) {
                add(
                    SECTION_GERAET,
                    chooseSlot("geraete", index, "geraet-frei"),
                    index,
                    JSONObject().put("text", value)
                )
            }
        }

        addArrayEntries(desired, "arbeiten", SECTION_ARBEIT, "arbeit-frei", remembered, usedSlots, out, worksiteId, teamId, clientKey)
        addArrayEntries(desired, "abnahmen", SECTION_ABNAHME, "abnahme", remembered, usedSlots, out, worksiteId, teamId, clientKey)
        addArrayEntries(desired, "einweisungUnterweisung", SECTION_EINWEISUNG, "einweisung", remembered, usedSlots, out, worksiteId, teamId, clientKey)
        addArrayEntries(desired, "besonderheiten", SECTION_BESONDERHEIT, "besonderheit", remembered, usedSlots, out, worksiteId, teamId, clientKey)
        addArrayEntries(desired, "maengelSchaeden", SECTION_MANGEL, "mangel", remembered, usedSlots, out, worksiteId, teamId, clientKey)

        desired.nullableString("notizen")?.takeIf { it.isNotBlank() }?.let {
            val slot = remembered.optJSONArray("notizen")?.optString(0)?.trim()
                ?.takeIf { value -> value.isNotBlank() && value !in usedSlots }
                ?: "notizen:0"
            usedSlots += slot
            add(SECTION_NOTIZEN, slot, 0, JSONObject().put("text", it))
        }
        return out
    }

    private fun addArrayEntries(
        desired: JSONObject,
        key: String,
        section: String,
        slotPrefix: String,
        remembered: JSONObject,
        usedSlots: MutableSet<String>,
        out: MutableList<DesiredEntry>,
        worksiteId: String,
        teamId: String,
        clientKey: String
    ) {
        val array = desired.optJSONArray(key) ?: JSONArray()
        val rememberedSlots = remembered.optJSONArray(key)
        var maxSuffix = -1
        if (rememberedSlots != null) {
            for (i in 0 until rememberedSlots.length()) {
                val slot = rememberedSlots.optString(i)
                if (slot.startsWith("$slotPrefix:")) {
                    slot.substringAfterLast(':').toIntOrNull()?.let { maxSuffix = maxOf(maxSuffix, it) }
                }
            }
        }
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            var slot = rememberedSlots?.optString(index)?.trim()
                ?.takeIf { it.isNotBlank() && it !in usedSlots }
            if (slot == null) {
                do {
                    maxSuffix += 1
                    slot = "$slotPrefix:$maxSuffix"
                } while (slot in usedSlots)
            }
            val resolvedSlot = slot ?: continue
            usedSlots += resolvedSlot
            out += DesiredEntry(
                deterministicEntryId(worksiteId, teamId, clientKey, resolvedSlot),
                section,
                resolvedSlot,
                index,
                JSONObject(item.toString())
                    .put(MANAGED_KEY, true)
                    .put(SLOT_KEY, resolvedSlot)
            )
        }
    }

    private fun headerStateFromLocal(desired: JSONObject, clientKey: String): JSONObject =
        JSONObject()
            .put("work_date", desired.nullableValue("datumIso"))
            .put("startUhrzeit", desired.nullableValue("startUhrzeit"))
            .put("endeDatumIso", desired.nullableValue("endeDatumIso"))
            .put("endeUhrzeit", desired.nullableValue("endeUhrzeit"))
            .put("meta", headerMetaFromLocal(desired, clientKey))

    private fun headerStateFromServer(
        btb: JSONObject,
        header: JSONObject,
        clientKey: String
    ): JSONObject =
        JSONObject()
            .put("work_date", btb.nullableValue("work_date"))
            .put("startUhrzeit", timePart(btb.nullableString("started_at")) ?: JSONObject.NULL)
            .put(
                "endeDatumIso",
                datePart(btb.nullableString("ended_at"))
                    ?.takeIf { it != btb.nullableString("work_date") }
                    ?: JSONObject.NULL
            )
            .put("endeUhrzeit", timePart(btb.nullableString("ended_at")) ?: JSONObject.NULL)
            .put("meta", androidHeaderMeta(header).apply {
                put("client_btb_id", clientKey)
                remove("last_change_id")
            })

    private fun headerMetaFromLocal(desired: JSONObject, clientKey: String): JSONObject {
        val meta = JSONObject().put("client_btb_id", clientKey)
        for (key in HEADER_META_KEYS) {
            if (desired.has(key) && !desired.isNull(key)) meta.put(key, desired.opt(key))
        }
        return meta
    }

    private fun androidHeaderMeta(header: JSONObject): JSONObject {
        val nested = header.optJSONObject(ANDROID_META_KEY)
            ?.let { JSONObject(it.toString()) }
            ?: JSONObject()
        if (!nested.has("client_btb_id")) {
            header.nullableString(ANDROID_CLIENT_KEY)?.let { nested.put("client_btb_id", it) }
        }
        for (key in HEADER_META_KEYS) {
            if (!nested.has(key) && header.has(key) && !header.isNull(key)) {
                nested.put(key, header.opt(key))
            }
        }
        return nested
    }

    private fun mergedHeader(
        existing: JSONObject,
        desired: JSONObject,
        clientKey: String,
        clientChangeId: String
    ): JSONObject =
        JSONObject(existing.toString())
            .put(
                ANDROID_META_KEY,
                headerMetaFromLocal(desired, clientKey)
                    .put("last_change_id", clientChangeId)
            )
            .put(ANDROID_CLIENT_KEY, clientKey)
            .put(ANDROID_LAST_CHANGE_ID, clientChangeId)

    private fun entryStateFromBase(base: JSONObject): JSONObject =
        JSONObject()
            .put("section", base.optString("section"))
            .put("sort_order", base.optInt("sort_order", 0))
            .put("payload", base.optJSONObject("payload") ?: JSONObject())

    private fun cleanManagedPayload(raw: JSONObject): JSONObject =
        JSONObject(raw.toString()).apply {
            remove(MANAGED_KEY)
            remove(SLOT_KEY)
        }

    private fun jsonArraySorted(values: List<Pair<Int, JSONObject>>): JSONArray =
        JSONArray().apply {
            values.sortedBy { it.first }.forEach { put(it.second) }
        }

    private fun deterministicEntryId(
        worksiteId: String,
        teamId: String,
        clientKey: String,
        slot: String
    ): String =
        UUID.nameUUIDFromBytes(
            "tbhelfer-btb:$worksiteId:$teamId:$clientKey:$slot".toByteArray(Charsets.UTF_8)
        ).toString()

    private fun slotToken(slot: String): String =
        UUID.nameUUIDFromBytes(slot.toByteArray(Charsets.UTF_8)).toString().replace("-", "")

    private fun openConnection(path: String, method: String = "GET"): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

    private fun authorize(connection: HttpURLConnection, token: String) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
    }

    private sealed interface JsonMutationResult {
        data class Success(val body: JSONObject) : JsonMutationResult
        data class Conflict(val currentVersion: Long?, val detail: String) : JsonMutationResult
        data object Unsupported : JsonMutationResult
        data object SessionInvalid : JsonMutationResult
        data class Error(val message: String) : JsonMutationResult
    }

    private fun accessFromDetail(detail: Detail): Access {
        val payload = runCatching { JSONObject(detail.snapshot.payloadJson) }.getOrElse { JSONObject() }
        val current = payload.optJSONObject(SERVER_CURRENT_USER_KEY) ?: JSONObject()
        val participantsJson = payload.optJSONArray(SERVER_PARTICIPANTS_KEY) ?: JSONArray()
        val participants = (0 until participantsJson.length()).mapNotNull { index ->
            val item = participantsJson.optJSONObject(index) ?: return@mapNotNull null
            val userId = item.optString("user_id").trim()
            if (userId.isBlank()) return@mapNotNull null
            Participant(
                userId = userId,
                displayName = item.nullableString("display_name"),
                status = item.optString("status").trim().uppercase().ifBlank { "WORKING" },
                isTeamLead = item.optBoolean("is_team_lead", false)
            )
        }
        return Access(
            snapshot = detail.snapshot,
            active = payload.optBoolean(SERVER_ACTIVE_KEY, false),
            isTeamLead = current.optBoolean("is_team_lead", false),
            participantStatus = current.nullableString("participant_status")?.uppercase(),
            canFinalize = current.optBoolean("can_finalize_btb", false),
            canViewPdf = current.optBoolean("can_view_btb_pdf", false),
            participants = participants
        )
    }

    private fun lifecycleMutation(
        token: String,
        worksiteId: String,
        teamId: String,
        serverBtbId: String,
        suffix: String,
        path: String,
        body: JSONObject
    ): LifecycleResult {
        val connection = openConnection(path, "POST")
        return try {
            authorize(connection, token)
            connection.setRequestProperty(
                "X-Client-Change-Id",
                "android-lifecycle-${UUID.randomUUID()}-$suffix".take(200)
            )
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val response = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK,
                HttpURLConnection.HTTP_CREATED,
                HttpURLConnection.HTTP_NO_CONTENT -> {
                    when (val fresh = loadBtbAccess(token, worksiteId, teamId, serverBtbId)) {
                        is AccessResult.Success -> LifecycleResult.Success(fresh.access)
                        AccessResult.Unsupported -> LifecycleResult.Unsupported
                        AccessResult.SessionInvalid -> LifecycleResult.SessionInvalid
                        is AccessResult.Error -> LifecycleResult.Error(fresh.message)
                    }
                }
                HttpURLConnection.HTTP_CONFLICT -> {
                    val detail = runCatching { JSONObject(response).opt("detail") }.getOrNull()
                    val objectDetail = detail as? JSONObject
                    val codeText = when (detail) {
                        is String -> detail
                        is JSONObject -> detail.optString("code").ifBlank { detail.toString() }
                        else -> "CONFLICT"
                    }
                    if (codeText == "BTB_PARTICIPANTS_NOT_FINISHED") {
                        val array = objectDetail?.optJSONArray("participants") ?: JSONArray()
                        val open = (0 until array.length()).mapNotNull { index ->
                            val item = array.optJSONObject(index) ?: return@mapNotNull null
                            val userId = item.optString("user_id").trim()
                            if (userId.isBlank()) return@mapNotNull null
                            Participant(
                                userId = userId,
                                displayName = item.nullableString("display_name"),
                                status = item.optString("status").trim().uppercase().ifBlank { "WORKING" },
                                isTeamLead = item.optBoolean("is_team_lead", false)
                            )
                        }
                        LifecycleResult.ParticipantsNotFinished(open)
                    } else {
                        LifecycleResult.Conflict(codeText)
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> LifecycleResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> LifecycleResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(response)) LifecycleResult.SessionInvalid
                    else LifecycleResult.Error(errorMessage(response, code))
                else -> LifecycleResult.Error(errorMessage(response, code))
            }
        } catch (error: Exception) {
            LifecycleResult.Error(error.message ?: "BTB-Lifecycle konnte nicht synchronisiert werden.")
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonMutation(
        token: String,
        path: String,
        method: String,
        clientChangeId: String,
        body: JSONObject
    ): JsonMutationResult {
        val connection = openConnection(path, method)
        return try {
            authorize(connection, token)
            connection.setRequestProperty("X-Client-Change-Id", clientChangeId)
            if (method != "DELETE") {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val response = readBody(connection, code)
            when (code) {
                HttpURLConnection.HTTP_OK,
                HttpURLConnection.HTTP_CREATED,
                HttpURLConnection.HTTP_NO_CONTENT -> {
                    val root = response.takeIf { it.isNotBlank() }
                        ?.let { runCatching { JSONObject(it) }.getOrNull() }
                        ?: JSONObject()
                    JsonMutationResult.Success(root)
                }
                HttpURLConnection.HTTP_CONFLICT -> {
                    val raw = runCatching { JSONObject(response).opt("detail") }.getOrNull()
                    val obj = raw as? JSONObject
                    JsonMutationResult.Conflict(
                        currentVersion = obj?.optLong("current_version")?.takeIf { it > 0L },
                        detail = when (raw) {
                            is String -> raw
                            is JSONObject -> raw.optString("code").ifBlank { raw.toString() }
                            else -> "CONFLICT"
                        }
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> JsonMutationResult.Unsupported
                HttpURLConnection.HTTP_UNAUTHORIZED -> JsonMutationResult.SessionInvalid
                HttpURLConnection.HTTP_FORBIDDEN ->
                    if (isInvalidDevice(response)) JsonMutationResult.SessionInvalid
                    else JsonMutationResult.Error(errorMessage(response, code))
                else -> JsonMutationResult.Error(errorMessage(response, code))
            }
        } catch (error: Exception) {
            JsonMutationResult.Error(error.message ?: "Server-1.0-BTB-Änderung ist fehlgeschlagen.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        if (code == HttpURLConnection.HTTP_NO_CONTENT) return ""
        val stream: InputStream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: return ""
        }
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
        }
    }

    private fun errorMessage(body: String, code: Int): String =
        detailMessage(body).ifBlank { "Serverfehler ($code)." }

    private fun detailMessage(body: String): String {
        val raw = runCatching { JSONObject(body).opt("detail") }.getOrNull()
        return when (raw) {
            is String -> raw.trim()
            is JSONObject -> raw.optString("code").trim().ifBlank { raw.toString() }
            else -> raw?.toString()?.trim().orEmpty()
        }
    }

    private fun isInvalidDevice(body: String): Boolean =
        detailMessage(body) in setOf("DEVICE_REVOKED", "USER_DISABLED", "USER_EXPIRED")

    private fun datePart(value: String?): String? =
        value?.takeIf { it.length >= 10 }?.substring(0, 10)

    private fun timePart(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val marker = value.indexOf('T')
        if (marker < 0 || marker + 6 > value.length) return null
        return value.substring(marker + 1, marker + 6)
    }

    private fun epochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                java.time.LocalDateTime.parse(value)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
    }

    private fun combinedDateTime(date: String?, time: String?): String? {
        val d = date?.trim().orEmpty()
        val t = time?.trim().orEmpty()
        if (!d.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
        if (!t.matches(Regex("\\d{1,2}:\\d{2}"))) return null
        val parts = t.split(':')
        return "$d" + "T" + parts[0].padStart(2, '0') + ":" + parts[1] + ":00"
    }

    private fun subChangeId(base: String, suffix: String): String =
        (base + "-" + suffix).take(200)

    private fun jsonEquivalent(left: JSONObject?, right: JSONObject?): Boolean {
        if (left == null || right == null) return left == null && right == null
        return canonical(left) == canonical(right)
    }

    private fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = mutableListOf<String>()
            val iterator = value.keys()
            while (iterator.hasNext()) keys += iterator.next()
            keys.sorted().joinToString(prefix = "{", postfix = "}") { key ->
                JSONObject.quote(key) + ":" + canonical(value.opt(key))
            }
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") {
            canonical(value.opt(it))
        }
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() }
    }

    private fun JSONObject.nullableValue(key: String): Any =
        if (!has(key) || isNull(key) || optString(key).isBlank()) JSONObject.NULL else opt(key)

    companion object {
        const val ANDROID_META_KEY = "tb_helfer_android_v1"
        const val MANAGED_KEY = "_tb_android_managed"
        const val SLOT_KEY = "_tb_android_slot"
        const val SERVER_STATUS_KEY = "_server1_btb_status"
        const val SERVER_FINAL_PDF_AVAILABLE_KEY = "_server1_final_pdf_available"
        const val SERVER_FINAL_PDF_SHA256_KEY = "_server1_final_pdf_sha256"
        const val SERVER_ACTIVE_KEY = "_server1_btb_active"
        const val SERVER_CURRENT_USER_KEY = "_server1_current_user"
        const val SERVER_PARTICIPANTS_KEY = "_server1_participants"
        const val SERVER_PHOTOS_KEY = "_server1_photos"
        const val LOCAL_ENTRY_SLOTS_KEY = "_tb_android_entry_slots"

        const val LEGACY_ANDROID_SECTION = "TB_HELFER_ANDROID_V1"
        const val ANDROID_CLIENT_KEY = "tb_helfer_btb_id"
        const val ANDROID_LAST_CHANGE_ID = "tb_helfer_last_change_id"

        const val SECTION_FIRMA = "FIRMA"
        const val SECTION_PERSONAL = "PERSONAL"
        const val SECTION_GERAET = "GERAET"
        const val SECTION_ARBEIT = "ARBEIT"
        const val SECTION_ABNAHME = "ABNAHME"
        const val SECTION_EINWEISUNG = "EINWEISUNG_UNTERWEISUNG"
        const val SECTION_BESONDERHEIT = "BESONDERHEIT"
        const val SECTION_MANGEL = "MANGEL_SCHADEN"
        const val SECTION_NOTIZEN = "NOTIZEN"

        private val HEADER_META_KEYS = listOf(
            "schicht",
            "projektNameSnapshot",
            "strecke",
            "wetter",
            "weather",
            "weatherSlots",
            "maxFotosInPdf",
            "includeFotos",
            "includeBtbs",
            "dokumentVersion",
            "letzterAbschlussBenutzer",
            "pdfAuswahl"
        )
    }
}
