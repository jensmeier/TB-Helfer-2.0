package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BtbTeamMediaSync
import de.tbhelfer.datenbank.CollabLocalBridge
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.datenbank.SyncWorksiteContextEntity
import de.tbhelfer.ui.bautagebuch.BtbDeleteSyncCoordinator

/**
 * Gemeinsamer Server-1.0-Fachsync für UI und Hintergrund-Worker.
 *
 * Der Cutover wird ausschließlich über /api/v1/capabilities gesteuert. Alte 0.6-/0.7-
 * Versionsschwellen gehören nur noch zum Legacy-Fallback und entscheiden nie über diesen Pfad.
 */
class Server1AndroidSyncCoordinator(
    context: Context,
    private val teamApi: TeamManagementApi = TeamManagementApi(),
    private val capabilitiesApi: ServerCapabilitiesApi = ServerCapabilitiesApi(),
    private val fachApi: Server1FachSyncApi = Server1FachSyncApi(),
    private val btbApi: Server1BtbApi = Server1BtbApi(),
    private val fileApi: Server1FileApi = Server1FileApi(),
    private val pdfApi: Server1BtbPdfApi = Server1BtbPdfApi()
) {
    private val appContext = context.applicationContext
    private val collab get() = ServiceLocator.collabSyncRepository
    private val bridge get() = ServiceLocator.collabLocalBridge
    private val structureSync get() = ServiceLocator.worksiteStructureSync
    private val trackDataSync get() = ServiceLocator.worksiteTrackDataSync

    data class Summary(
        val worksiteCount: Int,
        val fachUploaded: Int,
        val btbUploaded: Int,
        val attachmentsUploaded: Int,
        val worksiteNodesUploaded: Int,
        val trackDataUploaded: Int,
        val trackDataApplied: Int,
        val btbMediaUploaded: Int,
        val btbMediaImported: Int,
        val btbPdfImported: Int
    )

    sealed interface Result {
        data class Success(val summary: Summary) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    suspend fun synchronize(token: String): Result {
        val remoteContexts = when (val result = teamApi.loadWorksiteContexts(token)) {
            TeamManagementApi.WorksiteContextResult.Unsupported -> return Result.Unsupported
            TeamManagementApi.WorksiteContextResult.SessionInvalid -> return Result.SessionInvalid
            is TeamManagementApi.WorksiteContextResult.Error -> return Result.Error(result.message)
            is TeamManagementApi.WorksiteContextResult.Success -> result.items
        }

        val now = System.currentTimeMillis()
        val cachedContexts = remoteContexts.map { item ->
            SyncWorksiteContextEntity(
                worksiteServerId = item.worksiteId,
                name = item.name,
                designation = item.designation,
                worksiteType = null,
                status = item.status,
                companyId = null,
                groupId = item.groupId,
                memberRole = item.memberRole,
                areaScopeJson = item.areaScopeJson,
                groupLabel = item.groupLabel,
                leadUserId = item.leadUserId,
                leadNameSnapshot = item.leadNameSnapshot,
                updatedAtEpochMs = now
            )
        }

        runCatching { collab.replaceWorksiteContexts(cachedContexts) }
            .getOrElse {
                return Result.Error(
                    it.message ?: "Server-1.0-Baustellenkontext konnte lokal nicht gespeichert werden."
                )
            }

        val capabilities = when (val result = capabilitiesApi.load()) {
            is ServerCapabilitiesApi.Result.Success -> result.snapshot
            ServerCapabilitiesApi.Result.Unsupported -> {
                return Result.Success(
                    Summary(cachedContexts.size, 0, 0, 0, 0, 0, 0, 0, 0, 0)
                )
            }
            is ServerCapabilitiesApi.Result.Error -> return Result.Error(result.message)
        }

        val treeEnabled = capabilities.supports("worksite_tree_v1")
        val trackDataEnabled = capabilities.supports("track_data_v1")
        val btbEnabled = capabilities.supports("btb_live_sync_v1")
        val fachEnabled =
            capabilities.supports("defects_v1") || capabilities.supports("acceptances_v1")
        val filesEnabled = capabilities.supports("files_v1")
        val uploadsEnabled = filesEnabled && capabilities.supports("chunk_upload_v1")

        var worksiteNodesUploaded = 0
        if (treeEnabled) {
            var treeSettled = false
            repeat(3) {
                if (treeSettled) return@repeat
                when (val prepare = structureSync.prepareLocalChanges(token)) {
                    de.tbhelfer.datenbank.WorksiteStructureSync.Result.Unsupported ->
                        return Result.Error(
                            "Server meldet worksite_tree_v1, der Baustellenbaum-Endpunkt ist aber nicht erreichbar."
                        )
                    de.tbhelfer.datenbank.WorksiteStructureSync.Result.SessionInvalid ->
                        return Result.SessionInvalid
                    is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Error ->
                        return Result.Error(prepare.message)
                    is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Success -> Unit
                }

                when (val upload = collab.synchronizeServer1WorksiteNodePending(token)) {
                    CollabSyncRepository.Server1WorksiteNodeUploadResult.Unsupported ->
                        return Result.Error(
                            "Server meldet worksite_tree_v1, die Baustellenbaum-Schreibendpunkte sind aber nicht erreichbar."
                        )
                    CollabSyncRepository.Server1WorksiteNodeUploadResult.SessionInvalid ->
                        return Result.SessionInvalid
                    is CollabSyncRepository.Server1WorksiteNodeUploadResult.Success -> {
                        worksiteNodesUploaded += upload.uploaded
                        if (upload.failed > 0) {
                            return Result.Error(
                                "Baustellenbaum-Änderungen konnten nicht vollständig übertragen werden."
                            )
                        }
                        if (upload.conflicts == 0) {
                            treeSettled = true
                            return@repeat
                        }
                        // Konflikt nicht lokal überschreiben: prepareLocalChanges lädt den frischen
                        // Snapshot und rebased dieselbe ausdrückliche LEAD-Änderung auf dessen Version.
                    }
                }
                if (treeSettled) return@repeat
            }

            if (!treeSettled) {
                return Result.Error(
                    "Baustellenbaum konnte nach mehreren parallelen Änderungen nicht konfliktfrei synchronisiert werden."
                )
            }

            when (val apply = structureSync.applyServerStateToLocal(token)) {
                de.tbhelfer.datenbank.WorksiteStructureSync.Result.Unsupported ->
                    return Result.Error(
                        "Server meldet worksite_tree_v1, der Baustellenbaum-Snapshot ist aber nicht erreichbar."
                    )
                de.tbhelfer.datenbank.WorksiteStructureSync.Result.SessionInvalid ->
                    return Result.SessionInvalid
                is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Error ->
                    return Result.Error(apply.message)
                is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Success -> Unit
            }
        }

        var trackDataUploaded = 0
        var trackDataApplied = 0
        if (trackDataEnabled) {
            when (val track = trackDataSync.synchronize(token)) {
                de.tbhelfer.datenbank.WorksiteTrackDataSync.Result.Unsupported ->
                    return Result.Error(
                        "Server meldet track_data_v1, der kanonische Streckendatenvertrag ist aber nicht erreichbar."
                    )
                de.tbhelfer.datenbank.WorksiteTrackDataSync.Result.SessionInvalid ->
                    return Result.SessionInvalid
                is de.tbhelfer.datenbank.WorksiteTrackDataSync.Result.Error ->
                    return Result.Error(track.message)
                is de.tbhelfer.datenbank.WorksiteTrackDataSync.Result.Success -> {
                    trackDataUploaded += track.summary.uploadedDatasets
                    trackDataApplied += track.summary.appliedDatasets
                }
            }
        }

        if (btbEnabled) {
            refreshBtbSnapshots(token, remoteContexts)?.let { return it }
            reconcileBtbDeletes(token, remoteContexts)?.let { return it }
        }

        if (fachEnabled) {
            refreshFachSnapshots(token, remoteContexts, capabilities)?.let { return it }
        }

        // Die Dateiliste ist ein gemeinsamer Server-1.0-Vertrag. Sie darf nicht von Mängel-
        // Capabilities abhängen, weil sie auch BTB-Fotos enthält.
        if (filesEnabled && (fachEnabled || btbEnabled)) {
            when (
                val result = collab.synchronizeServer1Attachments(
                    token,
                    remoteContexts.map { it.worksiteId },
                    fileApi
                )
            ) {
                CollabSyncRepository.Server1AttachmentSyncResult.Unsupported ->
                    return Result.Error(
                        "Server meldet files_v1, der kanonische Datei-Endpunkt ist aber nicht erreichbar."
                    )
                CollabSyncRepository.Server1AttachmentSyncResult.SessionInvalid ->
                    return Result.SessionInvalid
                is CollabSyncRepository.Server1AttachmentSyncResult.Error ->
                    return Result.Error(result.message)
                is CollabSyncRepository.Server1AttachmentSyncResult.Success -> Unit
            }
        }

        if (fachEnabled || btbEnabled) {
            runCatching { bridge.exportLocalChanges() }
                .getOrElse {
                    return Result.Error(
                        it.message ?: "Lokale Baustellendaten konnten nicht für Server 1.0 vorbereitet werden."
                    )
                }
            runCatching { bridge.applyServerStateToLocal() }
                .getOrElse {
                    return Result.Error(
                        it.message ?: "Server-1.0-Daten konnten lokal nicht übernommen werden."
                    )
                }
        }

        var fachUploaded = 0
        if (fachEnabled) {
            when (val upload = collab.synchronizeServer1FachPending(token, fachApi)) {
                CollabSyncRepository.Server1FachUploadResult.SessionInvalid ->
                    return Result.SessionInvalid
                CollabSyncRepository.Server1FachUploadResult.Unsupported ->
                    return Result.Error(
                        "Server meldet Mängel-/Abnahme-Capabilities, die Schreibendpunkte sind aber nicht erreichbar."
                    )
                is CollabSyncRepository.Server1FachUploadResult.Success -> {
                    fachUploaded += upload.summary.uploaded
                    if (upload.summary.uploaded > 0) {
                        refreshFachSnapshots(token, remoteContexts, capabilities)?.let { return it }
                        runCatching { bridge.applyServerStateToLocal() }
                            .getOrElse {
                                return Result.Error(
                                    it.message ?: "Bestätigte Server-1.0-Fachdaten konnten lokal nicht übernommen werden."
                                )
                            }
                    }
                }
            }
        }

        var btbUploaded = 0
        if (btbEnabled) {
            when (val upload = collab.synchronizeServer1BtbPending(token, btbApi)) {
                CollabSyncRepository.Server1BtbUploadResult.SessionInvalid ->
                    return Result.SessionInvalid
                CollabSyncRepository.Server1BtbUploadResult.Unsupported ->
                    return Result.Error(
                        "Server meldet btb_live_sync_v1, die BTB-Schreibendpunkte sind aber nicht erreichbar."
                    )
                is CollabSyncRepository.Server1BtbUploadResult.Success -> {
                    btbUploaded += upload.uploaded
                    if (upload.uploaded > 0) {
                        refreshBtbSnapshots(token, remoteContexts)?.let { return it }
                        runCatching { bridge.applyServerStateToLocal() }
                            .getOrElse {
                                return Result.Error(
                                    it.message ?: "Bestätigte Server-1.0-BTBs konnten lokal nicht übernommen werden."
                                )
                            }
                    }
                }
            }
        }

        var attachmentsUploaded = 0
        if (uploadsEnabled && fachEnabled) {
            when (
                val upload = bridge.uploadLocalAttachments(
                    token,
                    server1Api = fileApi
                )
            ) {
                CollabLocalBridge.AttachmentUploadResult.SessionInvalid ->
                    return Result.SessionInvalid
                CollabLocalBridge.AttachmentUploadResult.Unsupported ->
                    return Result.Error(
                        "Server meldet Datei-/Chunk-Capabilities, der Uploadvertrag ist aber nicht erreichbar."
                    )
                is CollabLocalBridge.AttachmentUploadResult.Error ->
                    return Result.Error(upload.message)
                is CollabLocalBridge.AttachmentUploadResult.Success ->
                    attachmentsUploaded += upload.summary.uploaded
            }
        }

        var btbMediaUploaded = 0
        var btbMediaImported = 0
        var btbPdfImported = 0
        if (uploadsEnabled && btbEnabled) {
            val media = BtbTeamMediaSync(appContext)
            when (
                val result = media.synchronize(
                    token = token,
                    server1Api = fileApi,
                    server1PdfApi = if (
                        capabilities.supports("btb_finalize_with_pdf_v1")
                    ) pdfApi else null
                )
            ) {
                BtbTeamMediaSync.Result.SessionInvalid -> return Result.SessionInvalid
                BtbTeamMediaSync.Result.Unsupported ->
                    return Result.Error(
                        "Server meldet Datei-/BTB-Capabilities, der BTB-Medienvertrag ist aber nicht erreichbar."
                    )
                is BtbTeamMediaSync.Result.Error -> return Result.Error(result.message)
                is BtbTeamMediaSync.Result.Success -> {
                    btbMediaUploaded += result.summary.uploaded
                    btbMediaImported += result.summary.imported
                    btbPdfImported += result.summary.pdfImported

                    if (result.summary.uploaded > 0) {
                        when (val lifecycle = collab.synchronizeServer1BtbPending(token, btbApi)) {
                            CollabSyncRepository.Server1BtbUploadResult.SessionInvalid ->
                                return Result.SessionInvalid
                            CollabSyncRepository.Server1BtbUploadResult.Unsupported ->
                                return Result.Error(
                                    "BTB-Fotos wurden übertragen, der Abschluss-Lifecycle ist aber nicht erreichbar."
                                )
                            is CollabSyncRepository.Server1BtbUploadResult.Success -> {
                                btbUploaded += lifecycle.uploaded
                                if (lifecycle.uploaded > 0) {
                                    refreshBtbSnapshots(token, remoteContexts)?.let { return it }
                                    runCatching { bridge.applyServerStateToLocal() }
                                        .getOrElse {
                                            return Result.Error(
                                                it.message
                                                    ?: "BTB-Abschluss nach Foto-Sync konnte lokal nicht bestätigt werden."
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }
        }

        return Result.Success(
            Summary(
                worksiteCount = cachedContexts.size,
                fachUploaded = fachUploaded,
                btbUploaded = btbUploaded,
                attachmentsUploaded = attachmentsUploaded,
                worksiteNodesUploaded = worksiteNodesUploaded,
                trackDataUploaded = trackDataUploaded,
                trackDataApplied = trackDataApplied,
                btbMediaUploaded = btbMediaUploaded,
                btbMediaImported = btbMediaImported,
                btbPdfImported = btbPdfImported
            )
        )
    }

    private suspend fun reconcileBtbDeletes(
        token: String,
        contexts: List<TeamManagementApi.WorksiteContext>
    ): Result? {
        repeat(3) {
            val reconcile = runCatching {
                BtbDeleteSyncCoordinator.reconcileAfterSuccessfulPull(appContext)
            }.getOrElse {
                return Result.Error(
                    it.message ?: "BTB-Löschungen konnten nicht mit Server 1.0 abgeglichen werden."
                )
            }

            val hasPendingDelete = collab.hasPendingServer1BtbDelete()
            if (!hasPendingDelete && reconcile.serverDeletesEingereiht <= 0) return null

            when (val upload = collab.synchronizeServer1BtbPending(token, btbApi)) {
                CollabSyncRepository.Server1BtbUploadResult.SessionInvalid ->
                    return Result.SessionInvalid
                CollabSyncRepository.Server1BtbUploadResult.Unsupported ->
                    return Result.Error(
                        "Server meldet btb_live_sync_v1, die BTB-Löschung ist aber nicht erreichbar."
                    )
                is CollabSyncRepository.Server1BtbUploadResult.Success -> Unit
            }
            refreshBtbSnapshots(token, contexts)?.let { return it }
        }

        runCatching { BtbDeleteSyncCoordinator.reconcileAfterSuccessfulPull(appContext) }
            .getOrElse {
                return Result.Error(
                    it.message ?: "BTB-Löschstatus konnte nicht abschließend geprüft werden."
                )
            }

        return if (collab.hasPendingServer1BtbDelete()) {
            Result.Error(
                "BTB-Löschung konnte nach mehreren Serveränderungen noch nicht bestätigt werden."
            )
        } else {
            null
        }
    }

    private suspend fun refreshBtbSnapshots(
        token: String,
        contexts: List<TeamManagementApi.WorksiteContext>
    ): Result? {
        for (context in contexts
            .filter { it.groupId.isNotBlank() }
            .distinctBy { it.worksiteId + "|" + it.groupId }
        ) {
            when (val result = btbApi.loadTeamBtbs(token, context.worksiteId, context.groupId)) {
                Server1BtbApi.Result.Unsupported ->
                    return Result.Error(
                        "Server meldet btb_live_sync_v1, der kanonische BTB-Endpunkt ist aber nicht erreichbar."
                    )
                Server1BtbApi.Result.SessionInvalid -> return Result.SessionInvalid
                is Server1BtbApi.Result.Error -> return Result.Error(result.message)
                is Server1BtbApi.Result.Success ->
                    collab.replaceServer1BtbSnapshots(
                        context.worksiteId,
                        context.groupId,
                        result.items
                    )
            }
        }
        return null
    }

    private suspend fun refreshFachSnapshots(
        token: String,
        contexts: List<TeamManagementApi.WorksiteContext>,
        capabilities: ServerCapabilitiesApi.Snapshot
    ): Result? {
        for (context in contexts) {
            if (capabilities.supports("defects_v1")) {
                when (val result = fachApi.loadDefects(token, context.worksiteId)) {
                    is Server1FachSyncApi.Result.Success ->
                        collab.replaceServer1Snapshots(
                            context.worksiteId,
                            "DEFECT",
                            result.items
                        )
                    Server1FachSyncApi.Result.Unsupported ->
                        return Result.Error(
                            "Server meldet defects_v1, der Mängel-Endpunkt ist aber nicht erreichbar."
                        )
                    Server1FachSyncApi.Result.SessionInvalid -> return Result.SessionInvalid
                    is Server1FachSyncApi.Result.Error -> return Result.Error(result.message)
                }
            }
            if (capabilities.supports("acceptances_v1")) {
                when (val result = fachApi.loadAcceptances(token, context.worksiteId)) {
                    is Server1FachSyncApi.Result.Success ->
                        collab.replaceServer1Snapshots(
                            context.worksiteId,
                            "ACCEPTANCE",
                            result.items
                        )
                    Server1FachSyncApi.Result.Unsupported ->
                        return Result.Error(
                            "Server meldet acceptances_v1, der Abnahme-Endpunkt ist aber nicht erreichbar."
                        )
                    Server1FachSyncApi.Result.SessionInvalid -> return Result.SessionInvalid
                    is Server1FachSyncApi.Result.Error -> return Result.Error(result.message)
                }
            }
        }
        return null
    }
}
