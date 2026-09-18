package de.tbhelfer.datenbank

import android.content.Context
import androidx.room.withTransaction
import de.tbhelfer.server.CollabBenachrichtigungen
import de.tbhelfer.server.CollabSyncApi
import de.tbhelfer.server.Server1FachSyncApi
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Offline-Schicht für die Baustellen-Zusammenarbeit.
 *
 * - BTB wird nur innerhalb der eigenen TB-Gruppe gespeichert/synchronisiert.
 * - Mängel und Abnahmen kommen serverseitig bereits auf den berechtigten Bereich gefiltert.
 * - Bilder/Anlagen werden als Attachments übertragen und nach einem sichtbaren ATTACHMENT-Change
 *   direkt nachgeladen.
 * - Lokale Änderungen bleiben in einer Queue, bis sie bestätigt oder als 409-Konflikt markiert sind.
 */
class CollabSyncRepository(
    context: Context,
    private val database: SyncDatenbank
) {
    private val appContext = context.applicationContext
    private val dao: SyncDao get() = database.syncDao()

    data class Summary(
        val supported: Boolean,
        val worksiteCount: Int,
        val pulledChanges: Int,
        val uploadedChanges: Int,
        val downloadedAttachments: Int,
        val pendingChanges: Int,
        val conflicts: Int,
        val notifications: List<CollabBenachrichtigungen.Ereignis>
    )

    sealed interface FullSyncResult {
        data class Success(val summary: Summary) : FullSyncResult
        data object SessionInvalid : FullSyncResult
        data class Error(val message: String) : FullSyncResult
    }

    sealed interface LocalAttachmentUploadResult {
        data class Success(
            val uploaded: Boolean,
            val serverId: String
        ) : LocalAttachmentUploadResult
        data object Unsupported : LocalAttachmentUploadResult
        data object SessionInvalid : LocalAttachmentUploadResult
        data class Error(val message: String) : LocalAttachmentUploadResult
    }

    private sealed interface WorksiteOutcome {
        data class Success(
            val supported: Boolean,
            val pulled: Int,
            val uploaded: Int,
            val downloadedAttachments: Int,
            val notifications: List<CollabBenachrichtigungen.Ereignis>
        ) : WorksiteOutcome
        data object SessionInvalid : WorksiteOutcome
        data class Error(val message: String) : WorksiteOutcome
    }

    suspend fun synchronizeAll(token: String, api: CollabSyncApi = CollabSyncApi()): FullSyncResult {
        val contexts = when (val result = api.loadContext(token)) {
            CollabSyncApi.ContextResult.Unsupported -> {
                return FullSyncResult.Success(
                    Summary(false, 0, 0, 0, 0, dao.collabPendingCount(), dao.collabConflictCount(), emptyList())
                )
            }
            CollabSyncApi.ContextResult.SessionInvalid -> return FullSyncResult.SessionInvalid
            is CollabSyncApi.ContextResult.Error -> return FullSyncResult.Error(result.message)
            is CollabSyncApi.ContextResult.Success -> result.items
        }

        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.clearWorksiteContexts()
            if (contexts.isNotEmpty()) {
                dao.upsertWorksiteContexts(
                    contexts.map { item ->
                        SyncWorksiteContextEntity(
                            worksiteServerId = item.worksiteId,
                            name = item.name,
                            designation = item.designation,
                            worksiteType = item.worksiteType,
                            status = item.status,
                            companyId = item.companyId,
                            groupId = item.groupId,
                            memberRole = item.memberRole,
                            areaScopeJson = item.areaScopeJson,
                            groupLabel = item.groupLabel,
                            leadUserId = item.leadUserId,
                            leadNameSnapshot = item.leadNameSnapshot,
                            updatedAtEpochMs = now
                        )
                    }
                )
            }
        }

        var pulled = 0
        var uploaded = 0
        var downloaded = 0
        var supported = true
        val notifications = mutableListOf<CollabBenachrichtigungen.Ereignis>()

        for (context in contexts) {
            when (val result = synchronizeWorksite(token, context.worksiteId, api)) {
                WorksiteOutcome.SessionInvalid -> return FullSyncResult.SessionInvalid
                is WorksiteOutcome.Error -> return FullSyncResult.Error(result.message)
                is WorksiteOutcome.Success -> {
                    supported = supported && result.supported
                    pulled += result.pulled
                    uploaded += result.uploaded
                    downloaded += result.downloadedAttachments
                    notifications += result.notifications
                }
            }
        }

        return FullSyncResult.Success(
            Summary(
                supported = supported,
                worksiteCount = contexts.size,
                pulledChanges = pulled,
                uploadedChanges = uploaded,
                downloadedAttachments = downloaded,
                pendingChanges = dao.collabPendingCount(),
                conflicts = dao.collabConflictCount(),
                notifications = notifications
            )
        )
    }

    suspend fun worksiteContexts(): List<SyncWorksiteContextEntity> = dao.worksiteContexts()

    suspend fun replaceWorksiteContexts(contexts: List<SyncWorksiteContextEntity>) {
        database.withTransaction {
            dao.clearWorksiteContexts()
            if (contexts.isNotEmpty()) {
                dao.upsertWorksiteContexts(contexts)
            }
        }
    }

    suspend fun entities(worksiteServerId: String, entityType: String): List<SyncCollabEntity> =
        dao.collabEntities(worksiteServerId, entityType.uppercase())

    /**
     * Stores a complete read-only Server-1.0 snapshot for one Fach entity type.
     * Missing previously cached rows are tombstoned locally so deleted/hidden
     * objects cannot survive a later full collection refresh.
     */
    suspend fun replaceServer1Snapshots(
        worksiteServerId: String,
        entityType: String,
        snapshots: List<Server1FachSyncApi.Snapshot>
    ) {
        val type = entityType.uppercase()
        require(type == "DEFECT" || type == "ACCEPTANCE") { "Unsupported Server-1.0 snapshot type: $type" }
        val now = System.currentTimeMillis()
        database.withTransaction {
            val incomingIds = snapshots.mapTo(hashSetOf()) { it.serverId }
            dao.collabEntities(worksiteServerId, type)
                .filter { it.serverId !in incomingIds }
                .forEach { old ->
                    dao.markCollabDeleted(old.serverId, old.entityVersion, now)
                }
            if (snapshots.isNotEmpty()) {
                dao.upsertCollabEntities(
                    snapshots.map { item ->
                        SyncCollabEntity(
                            serverId = item.serverId,
                            worksiteServerId = worksiteServerId,
                            groupId = null,
                            entityType = type,
                            clientEntityKey = item.clientEntityKey,
                            status = item.status,
                            areaScopeJson = item.areaScopeJson,
                            payloadJson = item.payloadJson,
                            entityVersion = item.entityVersion,
                            changedByName = null,
                            createdAtText = item.createdAtText,
                            updatedAtText = item.updatedAtText,
                            isDeleted = false,
                            updatedAtEpochMs = now
                        )
                    }
                )
            }
        }
    }

    data class Server1FachUploadSummary(
        val uploaded: Int,
        val failed: Int,
        val conflicts: Int
    )

    sealed interface Server1FachUploadResult {
        data class Success(val summary: Server1FachUploadSummary) : Server1FachUploadResult
        data object Unsupported : Server1FachUploadResult
        data object SessionInvalid : Server1FachUploadResult
    }

    /**
     * Flush only DEFECT/ACCEPTANCE changes from the existing collaboration outbox
     * through the canonical Server-1.0 resources. BTB remains queued for its own
     * migration step and is never sent through this path.
     */
    suspend fun synchronizeServer1FachPending(
        token: String,
        api: Server1FachSyncApi = Server1FachSyncApi()
    ): Server1FachUploadResult {
        var uploaded = 0
        var failed = 0
        var conflicts = 0

        for (context in dao.worksiteContexts()) {
            val pending = dao.collabPendingChanges(context.worksiteServerId)
                .filter {
                    it.entityType.uppercase() == SyncCollabPendingChangeEntity.TYPE_DEFECT ||
                        it.entityType.uppercase() == SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE
                }
            for (change in pending) {
                dao.updateCollabChangeState(
                    change.id,
                    SyncCollabPendingChangeEntity.STATE_UPLOADING,
                    change.attempts + 1,
                    null
                )
                when (val result = api.applyPending(token, change)) {
                    Server1FachSyncApi.MutationResult.Unsupported -> {
                        dao.updateCollabChangeState(
                            change.id,
                            SyncCollabPendingChangeEntity.STATE_PENDING,
                            change.attempts,
                            "Server-1.0-Fachmutation ist nicht verfügbar."
                        )
                        return Server1FachUploadResult.Unsupported
                    }
                    Server1FachSyncApi.MutationResult.SessionInvalid ->
                        return Server1FachUploadResult.SessionInvalid
                    is Server1FachSyncApi.MutationResult.Error -> {
                        dao.updateCollabChangeState(
                            change.id,
                            SyncCollabPendingChangeEntity.STATE_FAILED,
                            change.attempts + 1,
                            result.message
                        )
                        failed++
                    }
                    is Server1FachSyncApi.MutationResult.Conflict -> {
                        val detail = JSONObject()
                            .put("detail", result.detail)
                            .put("server_version", result.currentVersion ?: JSONObject.NULL)
                        dao.updateCollabChangeState(
                            change.id,
                            SyncCollabPendingChangeEntity.STATE_CONFLICT,
                            change.attempts + 1,
                            detail.toString()
                        )
                        conflicts++
                    }
                    is Server1FachSyncApi.MutationResult.Applied -> {
                        val type = change.entityType.uppercase()
                        val item = result.snapshot
                        dao.upsertCollabEntities(
                            listOf(
                                SyncCollabEntity(
                                    serverId = item.serverId,
                                    worksiteServerId = change.worksiteServerId,
                                    groupId = null,
                                    entityType = type,
                                    clientEntityKey = item.clientEntityKey,
                                    status = item.status,
                                    areaScopeJson = item.areaScopeJson,
                                    payloadJson = item.payloadJson,
                                    entityVersion = item.entityVersion,
                                    changedByName = null,
                                    createdAtText = item.createdAtText,
                                    updatedAtText = item.updatedAtText,
                                    isDeleted = false,
                                    updatedAtEpochMs = System.currentTimeMillis()
                                )
                            )
                        )
                        dao.deleteCollabPendingChange(change.id)
                        uploaded++
                    }
                }
            }
        }
        return Server1FachUploadResult.Success(
            Server1FachUploadSummary(uploaded = uploaded, failed = failed, conflicts = conflicts)
        )
    }

    suspend fun attachments(parentEntityId: String): List<SyncCollabAttachmentEntity> =
        dao.collabAttachmentsForEntity(parentEntityId)

    suspend fun enqueueMutation(
        worksiteServerId: String,
        entityType: String,
        operation: String,
        serverId: String? = null,
        clientEntityKey: String? = null,
        baseVersion: Long? = null,
        groupId: String? = null,
        status: String? = null,
        areaScopeJson: String = "{}",
        payloadJson: String = "{}"
    ): Long {
        return dao.enqueueCollabChange(
            SyncCollabPendingChangeEntity(
                clientChangeId = "android-${UUID.randomUUID()}",
                worksiteServerId = worksiteServerId,
                groupId = groupId,
                entityType = entityType.uppercase(),
                serverId = serverId,
                clientEntityKey = clientEntityKey,
                baseVersion = baseVersion,
                operation = operation.uppercase(),
                status = status,
                areaScopeJson = areaScopeJson,
                payloadJson = payloadJson
            )
        )
    }

    /**
     * Lädt eine lokale Datei genau einmal je Server-Vorgang hoch.
     *
     * Die SHA-256-Prüfung erfolgt gegen den bereits synchronisierten Attachment-Cache. Dadurch
     * wird dasselbe Bild bei jedem Hintergrundlauf nicht erneut hochgeladen. Nach erfolgreichem
     * Upload wird das Attachment sofort lokal als vorhanden/downloaded eingetragen, damit das
     * eigene Gerät die identische Datei nicht anschließend noch einmal vom Server lädt.
     */
    suspend fun uploadAttachment(
        token: String,
        worksiteServerId: String,
        parentEntityType: String,
        parentEntityId: String,
        file: File,
        uploadFilename: String = file.name,
        mimeType: String = mimeTypeFor(file),
        api: CollabSyncApi = CollabSyncApi()
    ): LocalAttachmentUploadResult {
        if (!file.exists() || !file.isFile) {
            return LocalAttachmentUploadResult.Error("Datei existiert nicht mehr: ${file.name}")
        }
        if (worksiteServerId.isBlank() || parentEntityId.isBlank()) {
            return LocalAttachmentUploadResult.Error("Server-Zuordnung für Bild fehlt.")
        }
        val type = parentEntityType.uppercase()
        if (type !in ENTITY_TYPES) {
            return LocalAttachmentUploadResult.Error("Unbekannter Attachment-Typ: $type")
        }

        val hash = runCatching { sha256(file) }
            .getOrElse { return LocalAttachmentUploadResult.Error(it.message ?: "Bild konnte nicht gelesen werden.") }
        val existing = dao.collabAttachmentsForEntity(parentEntityId)
            .firstOrNull { !it.isDeleted && it.sha256.equals(hash, ignoreCase = true) }
        if (existing != null) {
            return LocalAttachmentUploadResult.Success(uploaded = false, serverId = existing.serverId)
        }

        val bytes = runCatching { file.readBytes() }
            .getOrElse { return LocalAttachmentUploadResult.Error(it.message ?: "Bild konnte nicht gelesen werden.") }
        return when (
            val result = api.uploadAttachment(
                token = token,
                worksiteId = worksiteServerId,
                entityType = type,
                entityId = parentEntityId,
                filename = safeFilename(uploadFilename),
                mimeType = mimeType,
                bytes = bytes
            )
        ) {
            CollabSyncApi.AttachmentUploadResult.Unsupported -> LocalAttachmentUploadResult.Unsupported
            CollabSyncApi.AttachmentUploadResult.SessionInvalid -> LocalAttachmentUploadResult.SessionInvalid
            is CollabSyncApi.AttachmentUploadResult.Error -> LocalAttachmentUploadResult.Error(result.message)
            is CollabSyncApi.AttachmentUploadResult.Success -> {
                dao.upsertCollabAttachments(
                    listOf(
                        SyncCollabAttachmentEntity(
                            serverId = result.attachmentId,
                            worksiteServerId = worksiteServerId,
                            parentEntityType = type,
                            parentEntityId = parentEntityId,
                            filename = safeFilename(uploadFilename),
                            mimeType = mimeType,
                            sizeBytes = result.sizeBytes,
                            sha256 = result.sha256 ?: hash,
                            localPath = file.absolutePath,
                            isDownloaded = true,
                            isDeleted = false,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    )
                )
                LocalAttachmentUploadResult.Success(uploaded = true, serverId = result.attachmentId)
            }
        }
    }

    private suspend fun synchronizeWorksite(token: String, worksiteId: String, api: CollabSyncApi): WorksiteOutcome {
        val firstPull = pullChanges(token, worksiteId, api)
        when (firstPull) {
            is WorksiteOutcome.Error -> return firstPull
            WorksiteOutcome.SessionInvalid -> return firstPull
            is WorksiteOutcome.Success -> Unit
        }
        firstPull as WorksiteOutcome.Success
        if (!firstPull.supported) return firstPull

        val upload = uploadPending(token, worksiteId, api)
        when (upload) {
            is WorksiteOutcome.Error -> return upload
            WorksiteOutcome.SessionInvalid -> return upload
            is WorksiteOutcome.Success -> Unit
        }
        upload as WorksiteOutcome.Success

        var pulled = firstPull.pulled
        var uploaded = upload.uploaded
        var downloaded = firstPull.downloadedAttachments
        val notifications = (firstPull.notifications + upload.notifications).toMutableList()
        var supported = firstPull.supported && upload.supported

        if (supported && uploaded > 0) {
            when (val secondPull = pullChanges(token, worksiteId, api)) {
                WorksiteOutcome.SessionInvalid -> return WorksiteOutcome.SessionInvalid
                is WorksiteOutcome.Error -> return secondPull
                is WorksiteOutcome.Success -> {
                    supported = supported && secondPull.supported
                    pulled += secondPull.pulled
                    downloaded += secondPull.downloadedAttachments
                    notifications += secondPull.notifications
                }
            }
        }

        return WorksiteOutcome.Success(supported, pulled, uploaded, downloaded, notifications)
    }

    private suspend fun pullChanges(token: String, worksiteId: String, api: CollabSyncApi): WorksiteOutcome {
        var after = dao.state(sequenceKey(worksiteId))?.longValue ?: 0L
        var total = 0
        var downloaded = 0
        val notifications = mutableListOf<CollabBenachrichtigungen.Ereignis>()

        while (true) {
            when (val result = api.loadChanges(token, worksiteId, after)) {
                CollabSyncApi.PullResult.Unsupported -> {
                    return WorksiteOutcome.Success(false, total, 0, downloaded, notifications)
                }
                CollabSyncApi.PullResult.SessionInvalid -> return WorksiteOutcome.SessionInvalid
                is CollabSyncApi.PullResult.Error -> return WorksiteOutcome.Error(result.message)
                is CollabSyncApi.PullResult.Success -> {
                    val page = result.page
                    database.withTransaction {
                        for (change in page.changes) {
                            applyServerChange(worksiteId, change)
                            if (!change.fromThisDevice &&
                                change.operation.equals("CREATE", ignoreCase = true) &&
                                change.entityType.uppercase() in NOTIFY_TYPES
                            ) {
                                notifications += CollabBenachrichtigungen.Ereignis(
                                    entityType = change.entityType.uppercase(),
                                    changedByName = change.changedByName
                                )
                            }
                        }
                        after = page.nextSequenceNo
                        dao.upsertState(SyncStateEntity(key = sequenceKey(worksiteId), longValue = after))
                        dao.upsertState(SyncStateEntity(key = SyncStateEntity.KEY_LAST_SUCCESSFUL_SYNC, longValue = System.currentTimeMillis()))
                    }
                    total += page.changes.size

                    when (val ack = api.acknowledge(token, worksiteId, after)) {
                        CollabSyncApi.AckResult.SessionInvalid -> return WorksiteOutcome.SessionInvalid
                        is CollabSyncApi.AckResult.Error -> return WorksiteOutcome.Error(ack.message)
                        CollabSyncApi.AckResult.Unsupported -> return WorksiteOutcome.Success(false, total, 0, downloaded, notifications)
                        CollabSyncApi.AckResult.Success -> Unit
                    }

                    when (val resultDownload = downloadPendingAttachments(token, worksiteId, api)) {
                        DownloadOutcome.SessionInvalid -> return WorksiteOutcome.SessionInvalid
                        is DownloadOutcome.Success -> downloaded += resultDownload.count
                    }

                    if (!page.hasMore) {
                        return WorksiteOutcome.Success(true, total, 0, downloaded, notifications)
                    }
                }
            }
        }
    }

    private suspend fun uploadPending(token: String, worksiteId: String, api: CollabSyncApi): WorksiteOutcome {
        var uploaded = 0
        val changes = dao.collabPendingChanges(worksiteId)
        for (change in changes) {
            dao.updateCollabChangeState(
                id = change.id,
                state = SyncCollabPendingChangeEntity.STATE_UPLOADING,
                attempts = change.attempts + 1,
                lastError = null
            )
            when (val result = api.uploadMutation(token, change)) {
                CollabSyncApi.MutationResult.Unsupported -> {
                    dao.updateCollabChangeState(
                        change.id,
                        SyncCollabPendingChangeEntity.STATE_PENDING,
                        change.attempts,
                        "Server unterstützt Baustellen-Sync noch nicht."
                    )
                    return WorksiteOutcome.Success(false, 0, uploaded, 0, emptyList())
                }
                CollabSyncApi.MutationResult.SessionInvalid -> return WorksiteOutcome.SessionInvalid
                is CollabSyncApi.MutationResult.Error -> {
                    dao.updateCollabChangeState(
                        change.id,
                        SyncCollabPendingChangeEntity.STATE_FAILED,
                        change.attempts + 1,
                        result.message
                    )
                }
                is CollabSyncApi.MutationResult.Conflict -> {
                    val detail = JSONObject()
                        .put("detail", result.detail)
                        .put("server_version", result.serverVersion)
                    if (!result.serverEntityJson.isNullOrBlank()) {
                        detail.put("server_entity", runCatching { JSONObject(result.serverEntityJson) }.getOrElse { result.serverEntityJson })
                    }
                    dao.updateCollabChangeState(
                        change.id,
                        SyncCollabPendingChangeEntity.STATE_CONFLICT,
                        change.attempts + 1,
                        detail.toString()
                    )
                }
                is CollabSyncApi.MutationResult.Applied -> {
                    if (!result.serverEntityJson.isNullOrBlank() && !result.entityId.isNullOrBlank()) {
                        applyServerChange(
                            worksiteId,
                            CollabSyncApi.ChangeItem(
                                sequenceNo = result.sequenceNo ?: 0,
                                entityType = change.entityType,
                                entityId = result.entityId,
                                operation = "UPDATE",
                                entityVersion = result.entityVersion,
                                changedAt = null,
                                changedByName = null,
                                fromThisDevice = true,
                                groupId = change.groupId,
                                areaScopeJson = change.areaScopeJson,
                                dataJson = result.serverEntityJson
                            )
                        )
                    } else if (change.operation.equals(SyncCollabPendingChangeEntity.OP_DELETE, true) && !change.serverId.isNullOrBlank()) {
                        dao.markCollabDeleted(change.serverId, result.entityVersion ?: change.baseVersion ?: 0)
                    }
                    dao.deleteCollabPendingChange(change.id)
                    uploaded++
                }
            }
        }
        return WorksiteOutcome.Success(true, 0, uploaded, 0, emptyList())
    }

    private suspend fun applyServerChange(worksiteId: String, change: CollabSyncApi.ChangeItem) {
        val type = change.entityType.uppercase()
        if (type == "ATTACHMENT") {
            applyAttachmentChange(worksiteId, change)
            return
        }
        if (type !in ENTITY_TYPES) return

        if (change.operation.equals("DELETE", ignoreCase = true)) {
            val existing = dao.collabEntity(change.entityId)
            if (existing != null) {
                dao.upsertCollabEntities(
                    listOf(
                        existing.copy(
                            entityVersion = change.entityVersion ?: existing.entityVersion,
                            changedByName = change.changedByName ?: existing.changedByName,
                            isDeleted = true,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    )
                )
            } else {
                dao.upsertCollabEntities(
                    listOf(
                        SyncCollabEntity(
                            serverId = change.entityId,
                            worksiteServerId = worksiteId,
                            groupId = change.groupId,
                            entityType = type,
                            entityVersion = change.entityVersion ?: 0,
                            changedByName = change.changedByName,
                            isDeleted = true
                        )
                    )
                )
            }
            return
        }

        val data = runCatching { JSONObject(change.dataJson) }.getOrElse { JSONObject() }
        val existing = dao.collabEntity(change.entityId)
        dao.upsertCollabEntities(
            listOf(
                SyncCollabEntity(
                    serverId = change.entityId,
                    worksiteServerId = data.optString("worksite_id").ifBlank { worksiteId },
                    groupId = data.optNullableString("group_id") ?: change.groupId ?: existing?.groupId,
                    entityType = type,
                    clientEntityKey = data.optNullableString("client_entity_key") ?: existing?.clientEntityKey,
                    status = data.optNullableString("status") ?: existing?.status,
                    areaScopeJson = data.optJsonText("area_scope") ?: change.areaScopeJson.ifBlank { existing?.areaScopeJson ?: "{}" },
                    payloadJson = data.optJsonText("payload") ?: existing?.payloadJson ?: "{}",
                    entityVersion = change.entityVersion
                        ?: data.optNullableLong("current_version")
                        ?: existing?.entityVersion
                        ?: 0,
                    changedByName = change.changedByName ?: existing?.changedByName,
                    createdAtText = data.optNullableString("created_at") ?: existing?.createdAtText,
                    updatedAtText = data.optNullableString("updated_at") ?: change.changedAt ?: existing?.updatedAtText,
                    isDeleted = false,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        )
    }

    private suspend fun applyAttachmentChange(worksiteId: String, change: CollabSyncApi.ChangeItem) {
        val existing = dao.collabAttachment(change.entityId)
        if (change.operation.equals("DELETE", ignoreCase = true)) {
            if (existing != null) {
                dao.upsertCollabAttachments(listOf(existing.copy(isDeleted = true, updatedAtEpochMs = System.currentTimeMillis())))
            }
            return
        }

        val data = runCatching { JSONObject(change.dataJson) }.getOrElse { JSONObject() }
        val parentType = data.optString("entity_type").ifBlank { existing?.parentEntityType ?: return }
        val parentId = data.optString("entity_id").ifBlank { existing?.parentEntityId ?: return }
        dao.upsertCollabAttachments(
            listOf(
                SyncCollabAttachmentEntity(
                    serverId = change.entityId,
                    worksiteServerId = data.optNullableString("worksite_id") ?: worksiteId,
                    parentEntityType = parentType.uppercase(),
                    parentEntityId = parentId,
                    filename = data.optNullableString("filename")
                        ?: data.optNullableString("original_filename")
                        ?: existing?.filename,
                    mimeType = data.optNullableString("mime_type") ?: existing?.mimeType,
                    sizeBytes = data.optLong("size_bytes", existing?.sizeBytes ?: 0),
                    sha256 = data.optNullableString("sha256") ?: existing?.sha256,
                    localPath = existing?.localPath,
                    isDownloaded = existing?.isDownloaded ?: false,
                    isDeleted = false,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        )
    }

    private sealed interface DownloadOutcome {
        data class Success(val count: Int) : DownloadOutcome
        data object SessionInvalid : DownloadOutcome
    }

    private suspend fun downloadPendingAttachments(token: String, worksiteId: String, api: CollabSyncApi): DownloadOutcome {
        var count = 0
        for (attachment in dao.collabAttachmentsNotDownloaded(worksiteId)) {
            when (val result = api.downloadAttachment(token, attachment.serverId)) {
                CollabSyncApi.AttachmentResult.SessionInvalid -> return DownloadOutcome.SessionInvalid
                CollabSyncApi.AttachmentResult.Unsupported,
                is CollabSyncApi.AttachmentResult.Error -> Unit
                is CollabSyncApi.AttachmentResult.Success -> {
                    val safeName = safeFilename(attachment.filename ?: result.attachment.filename ?: "bild")
                    val dir = File(appContext.filesDir, "server_sync/collab/$worksiteId")
                    if (!dir.exists()) dir.mkdirs()
                    val target = File(dir, "${attachment.serverId}_$safeName")
                    runCatching {
                        val temp = File(target.absolutePath + ".tmp")
                        temp.writeBytes(result.attachment.bytes)
                        if (target.exists()) target.delete()
                        if (!temp.renameTo(target)) {
                            temp.copyTo(target, overwrite = true)
                            temp.delete()
                        }
                        dao.markCollabAttachmentDownloaded(
                            serverId = attachment.serverId,
                            localPath = target.absolutePath,
                            mimeType = result.attachment.mimeType
                        )
                        count++
                    }
                }
            }
        }
        return DownloadOutcome.Success(count)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeFilename(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._() -]+"), "_")
        .trim(' ', '.')
        .take(160)
        .ifBlank { "bild" }

    private fun sequenceKey(worksiteId: String) = "collab_sequence:$worksiteId"

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableLong(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getLong(name) }.getOrNull()
    }

    private fun JSONObject.optJsonText(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return opt(name)?.toString()?.takeIf { it.isNotBlank() }
    }

    companion object {
        private val ENTITY_TYPES = setOf("BTB", "DEFECT", "ACCEPTANCE")
        private val NOTIFY_TYPES = ENTITY_TYPES

        fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
