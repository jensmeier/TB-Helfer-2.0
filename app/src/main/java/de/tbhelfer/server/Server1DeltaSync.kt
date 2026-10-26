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
        if (
            !capabilities.supports("sync_change_feed_v1") ||
            !capabilities.supports("sync_change_wait_v1")
        ) return Result.Unsupported

        // Periodic authoritative context refresh also detects revocation events which are
        // intentionally filtered from the removed user's feed. No global media scan.
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastContextRefreshMs == 0L || now - lastContextRefreshMs >= 60_000L) {
        when (val context = WorksiteContextSync(appContext).refresh(token)) {
            is WorksiteContextSync.Result.Success -> lastContextRefreshMs = now
            is WorksiteContextSync.Result.Error -> return Result.Error(context.message)
            WorksiteContextSync.Result.SessionInvalid -> return Result.SessionInvalid
            WorksiteContextSync.Result.Unsupported -> return Result.Unsupported
        }
        }
        val initial = WorksiteLocalSyncStore.initial(appContext)
        if (initial.isNotEmpty()) {
            val bootstrap = Server1DeltaApi.Page(initial.keys.flatMap { id -> listOf(
                Server1DeltaApi.Change(0, id, "WORKSITE_NODE", id, "REFRESH", null),
                Server1DeltaApi.Change(0, id, "BTB_PARTICIPANT", id, "REFRESH", null)
            ) }, 0, 0, true)
            when (val applied = applyPage(token, bootstrap)) {
                ApplyResult.Success -> WorksiteLocalSyncStore.confirmInitial(appContext, initial)
                is ApplyResult.Error -> return Result.Error(applied.message)
                ApplyResult.SessionInvalid -> return Result.SessionInvalid
                ApplyResult.Unsupported -> return Result.Unsupported
            }
        }
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
        val contextEvents = page.changes.filter {
            it.entityType in setOf("WORKSITE", "WORKSITE_MEMBERSHIP", "WORKSITE_INVITATION")
        }
        if (contextEvents.isNotEmpty()) {
            when (val context = WorksiteContextSync(appContext).refresh(token)) {
                is WorksiteContextSync.Result.Success -> lastContextRefreshMs = android.os.SystemClock.elapsedRealtime()
                is WorksiteContextSync.Result.Error -> return ApplyResult.Error(context.message)
                WorksiteContextSync.Result.SessionInvalid -> return ApplyResult.SessionInvalid
                WorksiteContextSync.Result.Unsupported -> return ApplyResult.Unsupported
            }
        }
        val affectedWorksites = page.changes
            .mapNotNull { it.worksiteId?.takeIf(String::isNotBlank) }
            .toSet()
        if (affectedWorksites.isEmpty()) return ApplyResult.Success

        // Nur lokal gebundene Baustellen sind fuer dieses Android-Geraet Fachobjekte.
        // Sichtbare, aber hier nicht gebundene Firmenbaustellen duerfen den globalen Cursor
        // nicht blockieren; ein spaeteres Binding macht ohnehin einen Recovery-/Initialabgleich.
        val bound = ServiceLocator.worksiteBindingRepository.contextsWithBindings()
            .filter {
                it.binding != null &&
                    it.localWorksite != null &&
                    it.context.worksiteServerId in affectedWorksites
            }
        if (bound.isEmpty()) return ApplyResult.Success

        val nodeWorksites = WorksiteDeltaPolicy.nodeWorksites(page.changes, bound.map { it.context.worksiteServerId }.toSet()) +
            contextEvents.mapNotNull { it.worksiteId }.toSet().intersect(bound.map { it.context.worksiteServerId }.toSet())
        if (nodeWorksites.isNotEmpty()) {
            when (val nodes = ServiceLocator.worksiteStructureSync.applyServerStateToLocal(token, nodeWorksites)) {
                de.tbhelfer.datenbank.WorksiteStructureSync.Result.Unsupported -> return ApplyResult.Unsupported
                de.tbhelfer.datenbank.WorksiteStructureSync.Result.SessionInvalid -> return ApplyResult.SessionInvalid
                is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Error -> return ApplyResult.Error(nodes.message)
                is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Success -> Unit
            }
        }

        val contextsByWorksite = bound.groupBy { it.context.worksiteServerId }
        val targetBtbIds = linkedMapOf<String, MutableSet<String>>()
        val needsActiveFallback = linkedSetOf<String>()

        fun target(worksiteId: String, btbId: String) {
            if (btbId.isBlank()) return
            targetBtbIds.getOrPut(worksiteId) { linkedSetOf() }.add(btbId)
        }

        // BTB-Events tragen bereits die parent-btbId. ENTRY/PARTICIPANT werden serverseitig
        // zusammen mit einem BTB-Event emittiert; falls die Paarung an einer Seitengrenze
        // getrennt wurde, wird nur der aktive BTB dieser Baustelle als Fallback nachgeladen.
        page.changes.forEach { change ->
            val worksiteId = change.worksiteId ?: return@forEach
            if (worksiteId !in contextsByWorksite) return@forEach
            when (change.entityType) {
                "BTB" -> target(worksiteId, change.entityId)
                "BTB_ENTRY", "BTB_PARTICIPANT", "WORKSITE", "WORKSITE_MEMBERSHIP", "WORKSITE_INVITATION" -> needsActiveFallback += worksiteId
            }
        }

        // FILE-Events werden ueber /files/{id} exakt auf ihre sichtbare BTB-Verknuepfung
        // aufgeloest. Bei einem Portal-Unlink liefert der lokale Attachment-Cache den alten
        // Parent zusaetzlich, damit genau dieser BTB seine Zuordnung entfernen kann.
        for (change in page.changes.filter { it.entityType == "FILE" }) {
            val worksiteId = change.worksiteId ?: continue
            if (worksiteId !in contextsByWorksite) continue
            when (
                val refreshed = ServiceLocator.collabSyncRepository.refreshServer1File(
                    token = token,
                    worksiteId = worksiteId,
                    fileId = change.entityId,
                    api = fileApi
                )
            ) {
                de.tbhelfer.datenbank.CollabSyncRepository.Server1FileRefreshResult.SessionInvalid ->
                    return ApplyResult.SessionInvalid
                is de.tbhelfer.datenbank.CollabSyncRepository.Server1FileRefreshResult.Error ->
                    return ApplyResult.Error(refreshed.message)
                is de.tbhelfer.datenbank.CollabSyncRepository.Server1FileRefreshResult.Success ->
                    refreshed.affectedBtbServerIds.forEach { target(worksiteId, it) }
            }
        }

        val refreshedBtbIds = linkedSetOf<String>()
        val refreshedWorksites = linkedSetOf<String>()

        for ((worksiteId, contexts) in contextsByWorksite) {
            val directIds = targetBtbIds[worksiteId].orEmpty().toMutableSet()
            val context = contexts.firstOrNull { it.context.groupId.isNotBlank() }?.context ?: continue

            for (btbId in directIds) {
                when (
                    val access = btbApi.loadBtbAccess(
                        token = token,
                        worksiteId = worksiteId,
                        teamId = context.groupId,
                        serverBtbId = btbId
                    )
                ) {
                    Server1BtbApi.AccessResult.Unsupported -> return ApplyResult.Unsupported
                    Server1BtbApi.AccessResult.SessionInvalid -> return ApplyResult.SessionInvalid
                    is Server1BtbApi.AccessResult.Error -> return ApplyResult.Error(access.message)
                    is Server1BtbApi.AccessResult.Success -> {
                        ServiceLocator.collabSyncRepository.upsertServer1BtbSnapshot(
                            worksiteId,
                            access.access.snapshot
                        )
                        refreshedBtbIds += access.access.snapshot.serverId
                        refreshedWorksites += worksiteId
                    }
                }
            }

            if (worksiteId in needsActiveFallback && directIds.isEmpty()) {
                when (
                    val access = btbApi.loadActiveBtbAccess(
                        token = token,
                        worksiteId = worksiteId,
                        teamId = context.groupId
                    )
                ) {
                    Server1BtbApi.AccessResult.Unsupported -> return ApplyResult.Unsupported
                    Server1BtbApi.AccessResult.SessionInvalid -> return ApplyResult.SessionInvalid
                    is Server1BtbApi.AccessResult.Error -> {
                        // Kein aktiver BTB ist bei einem zeitlich ueberholten Teilnehmer-/Entry-
                        // Event nach Finalize legitim. Die folgende BTB-FINALIZE-Seite ist dann
                        // der kanonische Zustand; dieser Event muss den Cursor nicht blockieren.
                        if (!access.message.contains("Kein aktiver BTB", ignoreCase = true)) {
                            return ApplyResult.Error(access.message)
                        }
                    }
                    is Server1BtbApi.AccessResult.Success -> {
                        ServiceLocator.collabSyncRepository.upsertServer1BtbSnapshot(
                            worksiteId,
                            access.access.snapshot
                        )
                        refreshedBtbIds += access.access.snapshot.serverId
                        refreshedWorksites += worksiteId
                    }
                }
            }
        }

        if (refreshedBtbIds.isNotEmpty()) {
            runCatching {
                ServiceLocator.collabLocalBridge.applyServerStateToLocal(
                    worksiteServerIds = refreshedWorksites,
                    btbServerIds = refreshedBtbIds,
                    includeOtherEntities = false
                )
            }.getOrElse {
                return ApplyResult.Error(
                    it.message ?: "Server-BTB-Delta konnte lokal nicht angewendet werden."
                )
            }

            when (
                val media = BtbTeamMediaSync(appContext).synchronize(
                    token = token,
                    server1Api = fileApi,
                    server1PdfApi = pdfApi,
                    worksiteServerIds = refreshedWorksites,
                    btbServerIds = refreshedBtbIds,
                    allowUploads = false
                )
            ) {
                BtbTeamMediaSync.Result.Unsupported -> return ApplyResult.Unsupported
                BtbTeamMediaSync.Result.SessionInvalid -> return ApplyResult.SessionInvalid
                is BtbTeamMediaSync.Result.Error -> return ApplyResult.Error(media.message)
                is BtbTeamMediaSync.Result.Success -> Unit
            }
        }

        return ApplyResult.Success
    }

    companion object {
        private val processMutex = Mutex()
        private var lastContextRefreshMs = 0L
    }
}


