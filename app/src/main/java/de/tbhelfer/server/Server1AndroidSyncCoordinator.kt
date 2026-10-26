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
        val dirtyScopes = SelectiveSyncStore.btbScopes(appContext)
        val roots = StructureSyncStore.roots(appContext) + WorksiteLocalSyncStore.roots(appContext) +
            ServiceLocator.worksiteBindingRepository.localMainWorksites()
                .filter { root -> dirtyScopes.any { it.localBaustelleId == root.altBaustelleId } }.map { it.id }
        val remoteContexts = when (val r = WorksiteContextSync(appContext).prepare(token, roots)) {
            is WorksiteContextSync.Result.Success -> r.items
            is WorksiteContextSync.Result.Error -> return Result.Error(r.message)
            WorksiteContextSync.Result.Unsupported -> return Result.Unsupported
            WorksiteContextSync.Result.SessionInvalid -> return Result.SessionInvalid
        }
        val cachedContexts = remoteContexts

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
        val acceptanceDeleteEnabled = capabilities.supports("acceptance_delete_v1")
        val filesEnabled = capabilities.supports("files_v1")
        val uploadsEnabled = filesEnabled && capabilities.supports("chunk_upload_v1")

        var worksiteNodesUploaded = 0
        if (treeEnabled) {
            when (val tree = structureSync.synchronizeChanged(token)) {
                de.tbhelfer.datenbank.WorksiteStructureSync.Result.Unsupported -> return Result.Error("Baustellenbaum-Endpunkt nicht verfügbar.")
                de.tbhelfer.datenbank.WorksiteStructureSync.Result.SessionInvalid -> return Result.SessionInvalid
                is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Error -> return Result.Error(tree.message)
                is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Success -> worksiteNodesUploaded = tree.summary.queuedOrUpdated
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
            // Lifecycle/Rollen/Slots zuerst primen, OHNE lokale Fachinhalte zu ueberschreiben.
            // So erzeugen historische FINALIZED-BTBs beim anschliessenden Recovery-Export
            // keine neue Mutation und vorhandene Server-Slots bleiben stabil.
            runCatching {
                bridge.primeBtbServerState(remoteContexts.mapTo(linkedSetOf()) { it.worksiteId })
            }.getOrElse {
                return Result.Error(
                    it.message ?: "BTB-Serverstatus konnte vor dem Recovery-Export nicht vorbereitet werden."
                )
            }
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

        if (acceptanceDeleteEnabled) {
            runCatching { bridge.queueServer1AcceptanceDeletes() }
                .getOrElse {
                    return Result.Error(
                        it.message
                            ?: "Lokale Abnahme-Löschungen konnten nicht für Server 1.0 vorbereitet werden."
                    )
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

                    // Foto-Upload ist seit Vertrag 25.09.2026 KEIN Lifecycle-Trigger.
                    // shift-finish/shift-resume/finalize werden ausschliesslich explizit aufgerufen.
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


