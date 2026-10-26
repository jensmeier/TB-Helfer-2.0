package de.tbhelfer.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BtbTeamMediaSync
import de.tbhelfer.datenbank.CollabLocalBridge
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.einstellungen.SyncEinstellungenSpeicher
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.withLock

/**
 * Persistenter selektiver Delta-Worker.
 *
 * Phase 1 synchronisiert ausschließlich Dirty-BTBs. Er lädt keinen Katalog, keinen kompletten
 * Baustellenbaum, keine Streckendaten, Mängel, Abnahmen oder globale Dateilisten.
 */
class SelectiveSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = BtbLifecycleOutbox.processMutex.withLock {
        val run = SyncActivityMonitor.begin("Senden")
        try { doWorkLocked(run) } finally { SyncActivityMonitor.finish(run) }
    }

    private suspend fun doWorkLocked(run: Long): Result {
        if (de.tbhelfer.server.recovery.RecoveryWorkspace.blocked()) return Result.failure()

        ServiceLocator.initialisieren(applicationContext)
        val scopes = SelectiveSyncStore.btbScopes(applicationContext)
        val roots = StructureSyncStore.roots(applicationContext)
        if (scopes.isEmpty() && roots.isEmpty() && !WorksiteLocalSyncStore.pending(applicationContext)) return Result.success()

        val settings = SyncEinstellungenSpeicher.laden(applicationContext)
        if (!networkAllowed(settings.netz)) {
            return retryAutomatic()
        }

        val tokenStore = SecureTokenStore(applicationContext)
        val token = tokenStore.load() ?: return Result.success()

        SyncActivityMonitor.phase(run, "Serverfunktionen prüfen")
        val capabilities = when (val result = ServerCapabilitiesApi().load()) {
            ServerCapabilitiesApi.Result.Unsupported -> return Result.success()
            is ServerCapabilitiesApi.Result.Error -> return retryAutomatic()
            is ServerCapabilitiesApi.Result.Success -> result.snapshot
        }
        if (
            scopes.isNotEmpty() && (!capabilities.supports("btb_live_sync_v1") ||
            !capabilities.supports("btb_entries_v1") ||
            !capabilities.supports("btb_general_work_entries_v1") ||
            !capabilities.supports("btb_photo_metadata_v1"))
        ) {
            return Result.success()
        }

        val scopeRoots = ServiceLocator.worksiteBindingRepository.localMainWorksites()
            .filter { root -> scopes.any { it.localBaustelleId == root.altBaustelleId } }.map { it.id }.toSet()
        val needsBinding = (roots + scopeRoots).any {
            ServiceLocator.worksiteBindingRepository.bindingForLocalRoot(it) == null
        }
        if (needsBinding || WorksiteLocalSyncStore.pending(applicationContext)) {
        SyncActivityMonitor.phase(run, "Baustelle mit Server verbinden / Namen senden")
        when (val context = WorksiteContextSync(applicationContext).prepare(token, roots + scopeRoots)) {
            WorksiteContextSync.Result.SessionInvalid -> { tokenStore.clear(); return Result.success() }
            is WorksiteContextSync.Result.Error -> {
                roots.forEach { StructureSyncStore.problem(applicationContext, it, context.message, StructureSyncStore.generation(applicationContext, it)) }
                scopes.forEach { SelectiveSyncStore.problem(applicationContext, it, context.message) }
                return retryAutomatic()
            }
            WorksiteContextSync.Result.Unsupported -> return retryAutomatic()
            is WorksiteContextSync.Result.Success -> Unit
        }

        }
        val bridge = ServiceLocator.collabLocalBridge
        val collab = ServiceLocator.collabSyncRepository
        val btbApi = Server1BtbApi()

        // Kein Team-/Worksite-Vollscan: jeder Dirty-Scope wird exakt aus dem lokalen Fachstand
        // exportiert. Bekannte Server-BTBs werden im bestehenden Mutation-/Versionspfad geprüft;
        // neue lokale BTBs laufen weiterhin über den idempotenten Create-Pfad.

        var retryNeeded = false
        val blockedRoots = mutableSetOf<Long>()
        val bindings = ServiceLocator.worksiteBindingRepository.contextsWithBindings()
        for ((index, root) in roots.withIndex()) {
            SyncActivityMonitor.phase(run, "Unterbaustelle/Struktur ${index + 1} von ${roots.size}")
            val bound = bindings.firstOrNull { it.binding != null && it.localWorksite?.id == root }
            if (bound == null || !capabilities.supports("worksite_tree_v1")) {
                blockedRoots += root
                retryNeeded = true
                continue
            }
            val treeGeneration = StructureSyncStore.generation(applicationContext, root)
            StructureSyncStore.transferring(root, true)
            try {
            when (val tree = ServiceLocator.worksiteStructureSync.synchronizeChanged(token, setOf(bound.context.worksiteServerId))) {
                de.tbhelfer.datenbank.WorksiteStructureSync.Result.SessionInvalid -> {
                    tokenStore.clear()
                    return Result.success()
                }
                is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Success -> Unit
                else -> {
                    val message = if (tree is de.tbhelfer.datenbank.WorksiteStructureSync.Result.Error) tree.message
                        else "Strukturabgleich nicht verfügbar"
                    StructureSyncStore.problem(applicationContext, root, message, treeGeneration)
                    blockedRoots += root
                    retryNeeded = true
                }
            }
            } finally { StructureSyncStore.transferring(root, false) }
        }

        for ((index, scope) in scopes.withIndex()) {
            SyncActivityMonitor.phase(run, "BTB ${index + 1} von ${scopes.size} · Einträge und Bilder")
            SelectiveSyncStore.transferring(scope, true)
            try {
            val boundItem = ServiceLocator.worksiteBindingRepository.contextsWithBindings()
                .firstOrNull {
                    it.binding != null &&
                        it.localWorksite?.altBaustelleId == scope.localBaustelleId
                }
            if (boundItem == null) {
                retryNeeded = true
                continue
            }
            if (boundItem.localWorksite?.id in blockedRoots) {
                SelectiveSyncStore.problem(applicationContext, scope, "Strukturabgleich ausstehend oder im Konflikt")
                retryNeeded = true
                continue
            }
            val pendingBtb = ServiceLocator.syncDatenbank.syncDao().collabPendingAnyForClientKey(
                boundItem.context.worksiteServerId, de.tbhelfer.datenbank.SyncCollabPendingChangeEntity.TYPE_BTB, scope.clientBtbId)
            if (pendingBtb?.state == de.tbhelfer.datenbank.SyncCollabPendingChangeEntity.STATE_CONFLICT) {
                SelectiveSyncStore.problem(applicationContext, scope, "Versionskonflikt prüfen")
                continue
            }
            if (BtbLifecycleOutbox.get(applicationContext, scope.clientBtbId) != null &&
                BtbServerStateStore.state(applicationContext, scope.clientBtbId)?.readOnly == true) {
                // Never erase a late local save while a lifecycle confirmation is unresolved.
                SelectiveSyncStore.problem(applicationContext, scope, "Abschlussstatus und lokale Änderungen prüfen")
                continue
            }
            // BTB-SYNC-002: Bei einem Dirty-BTB darf niemals zuerst ein Server-Snapshot
            // lokal angewendet werden. Der lokale Stand wird zuerst persistent in die Outbox
            // exportiert; Serverversion/Conflict werden anschließend im Mutation-Pfad geprüft.
            val exported = try {
                bridge.exportBtbChange(
                    localBaustelleId = scope.localBaustelleId,
                    clientBtbId = scope.clientBtbId
                )
            } catch (error: Exception) {
                ErrorLogStore.record(applicationContext, ErrorHistory.Operation.SEND, error.message.orEmpty(), ErrorHistory.ContextKind.SITE, scope.localBaustelleId)
                retryNeeded = true
                continue
            }

            when (exported) {
                CollabLocalBridge.BtbExportResult.NotBound -> {
                    retryNeeded = true
                }

                CollabLocalBridge.BtbExportResult.NotFound -> {
                    if (!SelectiveSyncStore.removeBtbIfUnchanged(applicationContext, scope)) {
                        retryNeeded = true
                    }
                }

                CollabLocalBridge.BtbExportResult.ReadOnly -> {
                    // Server bestaetigt FINALIZED/Teilnehmer-FINISHED: keine alte Mutation
                    // wiederbeleben. Bridge hat Pending + Dirty bereits gezielt aufgeraeumt.
                    SelectiveSyncStore.clearBtb(
                        applicationContext,
                        scope.localBaustelleId,
                        scope.clientBtbId
                    )
                }

                is CollabLocalBridge.BtbExportResult.Success -> {
                    when (
                        val upload = collab.synchronizeServer1BtbPending(
                            token = token,
                            api = btbApi,
                            worksiteServerId = exported.worksiteServerId,
                            clientBtbId = scope.clientBtbId
                        )
                    ) {
                        CollabSyncRepository.Server1BtbUploadResult.SessionInvalid -> {
                            tokenStore.clear()
                            return Result.success()
                        }

                        CollabSyncRepository.Server1BtbUploadResult.Unsupported -> {
                            return Result.success()
                        }

                        is CollabSyncRepository.Server1BtbUploadResult.Success -> {
                            if (upload.conflicts > 0) {
                                // Konflikt bleibt bewusst persistent für gezielte Recovery.
                                SelectiveSyncStore.problem(applicationContext, scope, "Versionskonflikt prüfen")
                                continue
                            }
                            if (upload.failed > 0) {
                                SelectiveSyncStore.problem(applicationContext, scope, "Übertragung fehlgeschlagen; erneuter Versuch folgt")
                                retryNeeded = true
                                continue
                            }

                            val currentServerBtbId = collab.entities(
                                exported.worksiteServerId,
                                de.tbhelfer.datenbank.SyncCollabPendingChangeEntity.TYPE_BTB
                            ).firstOrNull { it.clientEntityKey == scope.clientBtbId }?.serverId

                            if (currentServerBtbId.isNullOrBlank()) {
                                retryNeeded = true
                                continue
                            }
                            when (
                                val media = BtbTeamMediaSync(applicationContext).synchronize(
                                    token = token,
                                    server1Api = Server1FileApi(),
                                    server1PdfApi = Server1BtbPdfApi(),
                                    worksiteServerIds = setOf(exported.worksiteServerId),
                                    btbServerIds = setOf(currentServerBtbId),
                                    allowUploads = true
                                )
                            ) {
                                BtbTeamMediaSync.Result.SessionInvalid -> {
                                    tokenStore.clear()
                                    return Result.success()
                                }
                                BtbTeamMediaSync.Result.Unsupported -> return Result.success()
                                is BtbTeamMediaSync.Result.Error -> {
                                    ErrorLogStore.record(applicationContext, ErrorHistory.Operation.SEND, media.message, ErrorHistory.ContextKind.SITE, scope.localBaustelleId)
                                    retryNeeded = true
                                    continue
                                }
                                is BtbTeamMediaSync.Result.Success -> Unit
                            }

                            if (!SelectiveSyncStore.removeBtbIfUnchanged(applicationContext, scope)) {
                                retryNeeded = true
                                continue
                            }
                            val applied = runCatching {
                                bridge.applyServerStateToLocal(
                                    worksiteServerIds = setOf(exported.worksiteServerId),
                                    btbServerIds = setOf(currentServerBtbId),
                                    includeOtherEntities = false
                                )
                            }
                            if (applied.isFailure) {
                                // Bestätigter Upload darf bei lokalem Apply-Fehler nicht als
                                // abgeschlossen verloren gehen. Re-run ist idempotent; queueIfChanged
                                // erkennt den bereits bestätigten Fachstand.
                                SelectiveSyncStore.markBtbDirty(
                                    applicationContext,
                                    scope.localBaustelleId,
                                    scope.clientBtbId
                                )
                                retryNeeded = true
                                continue
                            }

                            SelectiveSyncStore.confirmBtbIfClean(applicationContext, scope)
                            if (
                                SelectiveSyncStore.isBtbDirty(
                                    applicationContext,
                                    scope.localBaustelleId,
                                    scope.clientBtbId
                                )
                            ) {
                                // Ein Autosave kam nach dem bestätigten Upload. Der Apply-Pfad
                                // schützt ihn per BTB-Mutationslock; KEEP darf aber keinen Folgelauf
                                // verschlucken, deshalb diesen Worker explizit erneut planen.
                                retryNeeded = true
                            }
                        }
                    }
                }
            }
            } finally {
                SelectiveSyncStore.transferring(scope, false)
            }
        }

        if (retryNeeded || WorksiteLocalSyncStore.pending(applicationContext) || StructureSyncStore.roots(applicationContext).isNotEmpty()) return retryAutomatic()

        if (!SelectiveSyncStore.hasPending(applicationContext)) {
            SyncStatusSpeicher.erfolgMerken(applicationContext)
        }
        return Result.success()
    }

    /**
     * Dirty-Scope darf auch nach laengerem Netz-/Serverausfall nicht ohne geplanten Lauf liegen.
     * WorkManager begrenzt den Backoff plattformseitig; Result.retry() plant deshalb so lange
     * weiter, bis der persistente Scope erfolgreich verarbeitet oder kanonisch read-only wird.
     */
    private fun retryAutomatic(): Result = Result.retry()

    private fun networkAllowed(net: SyncEinstellungenSpeicher.Netz): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return when (net) {
            SyncEinstellungenSpeicher.Netz.NUR_WLAN -> wifi
            SyncEinstellungenSpeicher.Netz.NUR_MOBILFUNK -> cellular
            SyncEinstellungenSpeicher.Netz.WLAN_UND_MOBILFUNK -> wifi || cellular
        }
    }

    companion object {
        private const val UNIQUE_NAME = "tbhelfer-selective-sync"
        private const val DEBOUNCE_SECONDS = 2L

        fun enqueue(context: Context) {
            val appContext = context.applicationContext
            val net = SyncEinstellungenSpeicher.laden(appContext).netz
            val requiredNetwork = when (net) {
                SyncEinstellungenSpeicher.Netz.NUR_WLAN -> NetworkType.UNMETERED
                SyncEinstellungenSpeicher.Netz.NUR_MOBILFUNK,
                SyncEinstellungenSpeicher.Netz.WLAN_UND_MOBILFUNK -> NetworkType.CONNECTED
            }
            val request = OneTimeWorkRequestBuilder<SelectiveSyncWorker>()
                .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(requiredNetwork)
                        .build()
                )
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }

        fun enqueueIfPending(context: Context) {
            if (SelectiveSyncStore.hasPending(context.applicationContext) || WorksiteLocalSyncStore.pending(context.applicationContext) || StructureSyncStore.roots(context.applicationContext).isNotEmpty()) {
                enqueue(context.applicationContext)
            }
        }
    }
}




