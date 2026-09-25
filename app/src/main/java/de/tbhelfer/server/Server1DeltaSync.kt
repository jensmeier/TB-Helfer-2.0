package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BtbTeamMediaSync
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wendet den gefilterten Server-1.0-Changefeed als kleine Android-Deltas an.
 *
 * Kein Katalog-, Baum-, Strecken-, Mängel- oder Abnahme-Vollsync. Ein Changefeed-Batch lädt nur
 * die betroffenen Baustellen-/Team-BTB-Snapshots und deren Datei-/Medienstand nach. Der Cursor
 * wird erst NACH erfolgreicher lokaler Anwendung committed.
 */
class Server1DeltaSync(context: Context) {
    private val appContext = context.applicationContext
    private val api = Server1DeltaApi()
    private val btbApi = Server1BtbApi()
    private val fileApi = Server1FileApi()
    private val pdfApi = Server1BtbPdfApi()

    sealed interface Result {
        data class Success(
            val pages: Int,
            val changes: Int,
            val cursor: Long,
            val caughtUp: Boolean
        ) : Result
        data object SessionInvalid : Result
        data object Unsupported : Result
        data class Error(val message: String) : Result
    }

    suspend fun synchronize(
        firstWaitSeconds: Int = 0,
        maxPages: Int = 20
    ): Result = processMutex.withLock {
        synchronizeLocked(firstWaitSeconds, maxPages)
    }

    private suspend fun synchronizeLocked(
        firstWaitSeconds: Int,
        maxPages: Int
    ): Result {
        ServiceLocator.initialisieren(appContext)
        val tokenStore = SecureTokenStore(appContext)
        val token = tokenStore.load() ?: return Result.Error("Gerät ist noch nicht für den Server aktiviert.")

        val capabilities = when (val caps = ServerCapabilitiesApi().load()) {
            ServerCapabilitiesApi.Result.Unsupported -> return Result.Unsupported
            is ServerCapabilitiesApi.Result.Error -> return Result.Error(caps.message)
            is ServerCapabilitiesApi.Result.Success -> caps.snapshot
        }
        if (!capabilities.supports("sync_change_wait_v1")) return Result.Unsupported

        var cursor = Server1DeltaCursorStore.load(appContext)
        var pages = 0
        var totalChanges = 0
        var caughtUp = false

        while (pages < maxPages) {
            val waitSeconds = if (pages == 0) firstWaitSeconds else 0
            val page = when (
                val result = api.waitForChanges(
                    token = token,
                    after = cursor,
                    waitSeconds = waitSeconds
                )
            ) {
                Server1DeltaApi.Result.Unsupported -> return Result.Unsupported
                Server1DeltaApi.Result.SessionInvalid -> {
                    tokenStore.clear()
                    return Result.SessionInvalid
                }
                is Server1DeltaApi.Result.Error -> return Result.Error(result.message)
                is Server1DeltaApi.Result.Success -> result.page
            }

            if (page.nextAfter < cursor) {
                return Result.Error("Server-Delta-Cursor ist rückwärts gelaufen.")
            }

            if (page.changes.isNotEmpty()) {
                when (val applied = applyPage(token, page)) {
                    ApplyResult.Success -> Unit
                    ApplyResult.SessionInvalid -> {
                        tokenStore.clear()
                        return Result.SessionInvalid
                    }
                    ApplyResult.Unsupported -> return Result.Unsupported
                    is ApplyResult.Error -> return Result.Error(applied.message)
                }
            }

            // Verbindlich erst nach vollstaendig erfolgreicher Verarbeitung dieses Batches.
            if (!Server1DeltaCursorStore.commit(appContext, page.nextAfter)) {
                return Result.Error("Server-Delta-Cursor konnte lokal nicht gespeichert werden.")
            }
            cursor = page.nextAfter
            pages++
            totalChanges += page.changes.size
            caughtUp = page.caughtUp

            if (page.caughtUp || page.nextAfter >= page.head) break
            if (page.nextAfter == 0L && page.changes.isEmpty()) break
        }

        SyncStatusSpeicher.erfolgMerken(appContext)
        return Result.Success(pages, totalChanges, cursor, caughtUp)
    }

    private sealed interface ApplyResult {
        data object Success : ApplyResult
        data object Unsupported : ApplyResult
        data object SessionInvalid : ApplyResult
        data class Error(val message: String) : ApplyResult
    }

    private suspend fun applyPage(
        token: String,
        page: Server1DeltaApi.Page
    ): ApplyResult {
        val affectedWorksites = page.changes
            .mapNotNull { it.worksiteId?.takeIf(String::isNotBlank) }
            .toSet()
        if (affectedWorksites.isEmpty()) return ApplyResult.Success

        val contexts = ServiceLocator.collabSyncRepository.worksiteContexts()
            .filter {
                it.worksiteServerId in affectedWorksites &&
                    it.groupId.isNotBlank()
            }

        // BTB_ENTRY/BTB_PARTICIPANT/FILE tragen im Feed nicht zwingend die parent-btbId.
        // Deshalb laden wir nur die betroffene Baustelle + genau die sichtbare Team-BTB-Liste
        // neu, niemals alle Baustellen/Fachobjekte.
        for (context in contexts.distinctBy { it.worksiteServerId + "|" + it.groupId }) {
            when (
                val result = btbApi.loadTeamBtbs(
                    token,
                    context.worksiteServerId,
                    context.groupId
                )
            ) {
                Server1BtbApi.Result.Unsupported -> return ApplyResult.Unsupported
                Server1BtbApi.Result.SessionInvalid -> return ApplyResult.SessionInvalid
                is Server1BtbApi.Result.Error -> return ApplyResult.Error(result.message)
                is Server1BtbApi.Result.Success -> {
                    ServiceLocator.collabSyncRepository.replaceServer1BtbSnapshots(
                        context.worksiteServerId,
                        context.groupId,
                        result.items
                    )
                }
            }
        }

        val fileWorksites = affectedWorksites.filter { id ->
            page.changes.any {
                it.worksiteId == id &&
                    it.entityType in setOf("FILE", "BTB", "BTB_ENTRY", "BTB_PARTICIPANT")
            }
        }
        if (fileWorksites.isNotEmpty()) {
            when (
                val files = ServiceLocator.collabSyncRepository.synchronizeServer1Attachments(
                    token = token,
                    worksiteIds = fileWorksites,
                    api = fileApi
                )
            ) {
                de.tbhelfer.datenbank.CollabSyncRepository.Server1AttachmentSyncResult.Unsupported ->
                    return ApplyResult.Unsupported
                de.tbhelfer.datenbank.CollabSyncRepository.Server1AttachmentSyncResult.SessionInvalid ->
                    return ApplyResult.SessionInvalid
                is de.tbhelfer.datenbank.CollabSyncRepository.Server1AttachmentSyncResult.Error ->
                    return ApplyResult.Error(files.message)
                is de.tbhelfer.datenbank.CollabSyncRepository.Server1AttachmentSyncResult.Success -> Unit
            }
        }

        runCatching { ServiceLocator.collabLocalBridge.applyServerStateToLocal() }
            .getOrElse {
                return ApplyResult.Error(
                    it.message ?: "Server-BTB-Delta konnte lokal nicht angewendet werden."
                )
            }

        when (
            val media = BtbTeamMediaSync(appContext).synchronize(
                token = token,
                server1Api = fileApi,
                server1PdfApi = pdfApi,
                worksiteServerIds = affectedWorksites
            )
        ) {
            BtbTeamMediaSync.Result.Unsupported -> return ApplyResult.Unsupported
            BtbTeamMediaSync.Result.SessionInvalid -> return ApplyResult.SessionInvalid
            is BtbTeamMediaSync.Result.Error -> return ApplyResult.Error(media.message)
            is BtbTeamMediaSync.Result.Success -> Unit
        }

        return ApplyResult.Success
    }

    companion object {
        private val processMutex = Mutex()
    }
}
