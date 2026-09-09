package de.tbhelfer.collab

import androidx.room.withTransaction
import org.json.JSONObject
import java.util.UUID

class CollabRepository(
    private val database: CollabDatenbank
) {
    private val dao: CollabDao get() = database.dao()

    data class SyncSummary(
        val pulled: Int,
        val uploaded: Int,
        val pending: Int,
        val conflicts: Int,
        val lastSequenceNo: Long
    )

    sealed interface SyncResult {
        data class Success(val summary: SyncSummary) : SyncResult
        data object Unsupported : SyncResult
        data object SessionInvalid : SyncResult
        data class Error(val message: String) : SyncResult
    }

    suspend fun refreshContext(token: String, api: CollabApi = CollabApi()): SyncResult {
        return when (val result = api.loadContext(token)) {
            CollabApi.Result.Unsupported -> SyncResult.Unsupported
            CollabApi.Result.SessionInvalid -> SyncResult.SessionInvalid
            is CollabApi.Result.Error -> SyncResult.Error(result.message)
            is CollabApi.Result.Success -> {
                val now = System.currentTimeMillis()
                val entities = result.value.map { item ->
                    CollabWorksiteEntity(
                        serverId = item.id,
                        name = item.name,
                        designation = item.designation,
                        worksiteType = item.worksiteType,
                        status = item.status,
                        companyId = item.companyId,
                        groupId = item.groupId,
                        groupLabel = item.groupLabel,
                        leadUserId = item.leadUserId,
                        leadName = item.leadName,
                        memberRole = item.memberRole,
                        areaScopeJson = item.areaScopeJson,
                        updatedAtEpochMs = now
                    )
                }
                database.withTransaction {
                    dao.clearWorksites()
                    if (entities.isNotEmpty()) dao.upsertWorksites(entities)
                }
                SyncResult.Success(
                    SyncSummary(
                        pulled = 0,
                        uploaded = 0,
                        pending = dao.pendingCount(),
                        conflicts = dao.conflictCount(),
                        lastSequenceNo = 0
                    )
                )
            }
        }
    }

    suspend fun worksites(): List<CollabWorksiteEntity> = dao.worksites()

    suspend fun bindLocalWorksite(localBaustelleId: Long, serverWorksiteId: String) {
        require(dao.worksite(serverWorksiteId) != null) { "Server-Baustelle ist lokal noch nicht bekannt." }
        dao.upsertBinding(CollabBindingEntity(localBaustelleId, serverWorksiteId))
    }

    suspend fun bindingForLocal(localBaustelleId: Long): CollabBindingEntity? = dao.bindingForLocal(localBaustelleId)

    suspend fun btbForWorksite(worksiteId: String): List<CollabBtbEntity> {
        val ws = dao.worksite(worksiteId) ?: return emptyList()
        return dao.btb(worksiteId, ws.groupId)
    }

    suspend fun defectsForWorksite(worksiteId: String): List<CollabDefectEntity> = dao.defects(worksiteId)
    suspend fun acceptancesForWorksite(worksiteId: String): List<CollabAcceptanceEntity> = dao.acceptances(worksiteId)

    suspend fun enqueueUpsert(
        worksiteId: String,
        entityType: String,
        clientEntityKey: String,
        entityServerId: String?,
        baseVersion: Long?,
        payloadJson: String,
        status: String? = null,
        areaScopeJson: String = "{}",
        groupId: String? = null
    ): Long {
        val type = entityType.uppercase()
        val existing = dao.unsentPendingForClientKey(worksiteId, type, clientEntityKey)
        if (existing != null) {
            return dao.upsertPending(
                existing.copy(
                    entityServerId = entityServerId ?: existing.entityServerId,
                    baseVersion = baseVersion ?: existing.baseVersion,
                    payloadJson = payloadJson,
                    statusValue = status,
                    areaScopeJson = areaScopeJson,
                    groupId = groupId ?: existing.groupId,
                    operation = CollabPendingMutationEntity.OP_UPSERT,
                    lastError = null,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
        return dao.enqueue(
            CollabPendingMutationEntity(
                clientChangeId = UUID.randomUUID().toString(),
                worksiteServerId = worksiteId,
                entityType = type,
                entityServerId = entityServerId,
                clientEntityKey = clientEntityKey,
                baseVersion = baseVersion,
                operation = CollabPendingMutationEntity.OP_UPSERT,
                groupId = groupId,
                statusValue = status,
                areaScopeJson = areaScopeJson,
                payloadJson = payloadJson
            )
        )
    }

    suspend fun enqueueDelete(
        worksiteId: String,
        entityType: String,
        clientEntityKey: String,
        entityServerId: String,
        baseVersion: Long,
        areaScopeJson: String = "{}",
        groupId: String? = null
    ): Long = dao.enqueue(
        CollabPendingMutationEntity(
            clientChangeId = UUID.randomUUID().toString(),
            worksiteServerId = worksiteId,
            entityType = entityType.uppercase(),
            entityServerId = entityServerId,
            clientEntityKey = clientEntityKey,
            baseVersion = baseVersion,
            operation = CollabPendingMutationEntity.OP_DELETE,
            groupId = groupId,
            areaScopeJson = areaScopeJson,
            payloadJson = "{}"
        )
    )

    suspend fun syncWorksite(token: String, worksiteId: String, api: CollabApi = CollabApi()): SyncResult {
        var pulled = 0
        var uploaded = 0
        var cursor = dao.state(worksiteId)?.lastSequenceNo ?: 0L

        when (val pull = pullAll(token, worksiteId, cursor, api)) {
            PullOutcome.Unsupported -> return SyncResult.Unsupported
            PullOutcome.SessionInvalid -> return SyncResult.SessionInvalid
            is PullOutcome.Error -> return SyncResult.Error(pull.message)
            is PullOutcome.Success -> {
                pulled += pull.count
                cursor = pull.cursor
            }
        }

        val pending = dao.pending().filter { it.worksiteServerId == worksiteId }
        for (item in pending) {
            dao.updatePendingState(
                id = item.id,
                state = CollabPendingMutationEntity.STATE_UPLOADING,
                attempts = item.attempts + 1,
                error = null
            )
            val request = CollabApi.MutationRequest(
                clientChangeId = item.clientChangeId,
                worksiteId = item.worksiteServerId,
                entityType = item.entityType,
                operation = item.operation,
                entityId = item.entityServerId,
                clientEntityKey = item.clientEntityKey,
                baseVersion = item.baseVersion,
                groupId = item.groupId,
                status = item.statusValue,
                areaScopeJson = item.areaScopeJson,
                payloadJson = item.payloadJson
            )
            when (val result = api.mutate(token, request)) {
                CollabApi.MutationResult.Unsupported -> {
                    dao.updatePendingState(item.id, CollabPendingMutationEntity.STATE_PENDING, item.attempts, "Server unterstützt Baustellen-Sync noch nicht.")
                    return SyncResult.Unsupported
                }
                CollabApi.MutationResult.SessionInvalid -> return SyncResult.SessionInvalid
                is CollabApi.MutationResult.Error -> {
                    dao.updatePendingState(item.id, CollabPendingMutationEntity.STATE_FAILED, item.attempts + 1, result.message)
                }
                is CollabApi.MutationResult.Conflict -> {
                    val error = JSONObject()
                        .put("detail", result.detail)
                        .put("entity_id", result.entityId)
                        .put("server_version", result.serverVersion)
                    result.serverEntityJson?.let { error.put("server_entity", runCatching { JSONObject(it) }.getOrElse { it }) }
                    dao.updatePendingState(item.id, CollabPendingMutationEntity.STATE_CONFLICT, item.attempts + 1, error.toString())
                }
                is CollabApi.MutationResult.Applied -> {
                    result.value.serverEntityJson?.let { applySnapshot(item.entityType, it) }
                    dao.deletePending(item.id)
                    uploaded++
                }
            }
        }

        if (uploaded > 0) {
            when (val pull = pullAll(token, worksiteId, cursor, api)) {
                PullOutcome.Unsupported -> Unit
                PullOutcome.SessionInvalid -> return SyncResult.SessionInvalid
                is PullOutcome.Error -> return SyncResult.Error(pull.message)
                is PullOutcome.Success -> {
                    pulled += pull.count
                    cursor = pull.cursor
                }
            }
        }

        dao.upsertState(
            CollabSyncStateEntity(
                worksiteServerId = worksiteId,
                lastSequenceNo = cursor,
                lastSuccessfulSyncEpochMs = System.currentTimeMillis(),
                lastError = null
            )
        )
        return SyncResult.Success(
            SyncSummary(
                pulled = pulled,
                uploaded = uploaded,
                pending = dao.pendingCount(),
                conflicts = dao.conflictCount(),
                lastSequenceNo = cursor
            )
        )
    }

    private sealed interface PullOutcome {
        data class Success(val count: Int, val cursor: Long) : PullOutcome
        data object Unsupported : PullOutcome
        data object SessionInvalid : PullOutcome
        data class Error(val message: String) : PullOutcome
    }

    private suspend fun pullAll(token: String, worksiteId: String, start: Long, api: CollabApi): PullOutcome {
        var cursor = start
        var total = 0
        do {
            when (val result = api.loadChanges(token, worksiteId, cursor)) {
                CollabApi.Result.Unsupported -> return PullOutcome.Unsupported
                CollabApi.Result.SessionInvalid -> return PullOutcome.SessionInvalid
                is CollabApi.Result.Error -> return PullOutcome.Error(result.message)
                is CollabApi.Result.Success -> {
                    database.withTransaction {
                        result.value.changes.forEach { applyServerChange(worksiteId, it) }
                        cursor = result.value.nextSequenceNo
                        dao.upsertState(
                            CollabSyncStateEntity(
                                worksiteServerId = worksiteId,
                                lastSequenceNo = cursor,
                                lastSuccessfulSyncEpochMs = System.currentTimeMillis()
                            )
                        )
                    }
                    total += result.value.changes.size
                    when (val ack = api.acknowledge(token, worksiteId, cursor)) {
                        CollabApi.Result.Unsupported -> return PullOutcome.Unsupported
                        CollabApi.Result.SessionInvalid -> return PullOutcome.SessionInvalid
                        is CollabApi.Result.Error -> return PullOutcome.Error(ack.message)
                        is CollabApi.Result.Success -> Unit
                    }
                    if (!result.value.hasMore) break
                }
            }
        } while (true)
        return PullOutcome.Success(total, cursor)
    }

    private suspend fun applyServerChange(worksiteId: String, change: CollabApi.ServerChange) {
        val type = change.entityType.uppercase()
        if (type == "ATTACHMENT") return // Metadaten/Dateidownload folgt im naechsten Integrationsschritt.
        if (change.operation.equals("DELETE", ignoreCase = true)) {
            when (type) {
                CollabPendingMutationEntity.TYPE_BTB -> dao.markBtbDeleted(change.entityId, change.entityVersion)
                CollabPendingMutationEntity.TYPE_DEFECT -> dao.markDefectDeleted(change.entityId, change.entityVersion)
                CollabPendingMutationEntity.TYPE_ACCEPTANCE -> dao.markAcceptanceDeleted(change.entityId, change.entityVersion)
            }
            return
        }
        applySnapshot(type, change.dataJson, worksiteId, change.entityId, change.entityVersion, change.groupId, change.areaScopeJson)
    }

    private suspend fun applySnapshot(
        entityType: String,
        rawJson: String,
        fallbackWorksiteId: String? = null,
        fallbackEntityId: String? = null,
        fallbackVersion: Long = 0,
        fallbackGroupId: String? = null,
        fallbackAreaScopeJson: String = "{}"
    ) {
        val root = runCatching { JSONObject(rawJson) }.getOrElse { return }
        val type = entityType.uppercase()
        val id = root.optString("id").ifBlank { fallbackEntityId ?: return }
        val worksiteId = root.optString("worksite_id").ifBlank { fallbackWorksiteId ?: return }
        val version = root.optLong("current_version", fallbackVersion)
        val clientKey = root.optString("client_entity_key").ifBlank {
            when (type) {
                CollabPendingMutationEntity.TYPE_BTB -> root.optString("client_entry_id")
                CollabPendingMutationEntity.TYPE_DEFECT -> root.optString("client_defect_key")
                CollabPendingMutationEntity.TYPE_ACCEPTANCE -> root.optString("client_acceptance_id")
                else -> ""
            }
        }
        if (clientKey.isBlank()) return
        val payload = root.opt("payload")?.toString() ?: "{}"
        val now = System.currentTimeMillis()
        when (type) {
            CollabPendingMutationEntity.TYPE_BTB -> {
                val groupId = root.optString("group_id").ifBlank { fallbackGroupId ?: return }
                dao.upsertBtb(
                    CollabBtbEntity(
                        serverId = id,
                        worksiteServerId = worksiteId,
                        groupId = groupId,
                        clientKey = clientKey,
                        payloadJson = payload,
                        entityVersion = version,
                        changedAtEpochMs = now
                    )
                )
            }
            CollabPendingMutationEntity.TYPE_DEFECT -> dao.upsertDefect(
                CollabDefectEntity(
                    serverId = id,
                    worksiteServerId = worksiteId,
                    clientKey = clientKey,
                    areaScopeJson = root.opt("area_scope")?.toString() ?: fallbackAreaScopeJson,
                    status = root.optString("status").ifBlank { "OFFEN" },
                    payloadJson = payload,
                    entityVersion = version,
                    changedAtEpochMs = now
                )
            )
            CollabPendingMutationEntity.TYPE_ACCEPTANCE -> dao.upsertAcceptance(
                CollabAcceptanceEntity(
                    serverId = id,
                    worksiteServerId = worksiteId,
                    clientKey = clientKey,
                    areaScopeJson = root.opt("area_scope")?.toString() ?: fallbackAreaScopeJson,
                    status = root.optString("status").ifBlank { "ENTWURF" },
                    payloadJson = payload,
                    entityVersion = version,
                    changedAtEpochMs = now
                )
            )
        }
    }
}
