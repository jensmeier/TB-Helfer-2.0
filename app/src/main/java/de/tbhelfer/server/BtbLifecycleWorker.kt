package de.tbhelfer.server

import android.content.Context
import androidx.work.*
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.SyncCollabPendingChangeEntity
import de.tbhelfer.server.recovery.RecoveryWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Durable explicit lifecycle commands. Read current state BEFORE replaying an ambiguous POST. */
class BtbLifecycleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = BtbLifecycleOutbox.processMutex.withLock {
        val ctx = RecoveryWorkspace.wrap(applicationContext)
        if (RecoveryWorkspace.blocked()) return@withLock Result.failure()
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val network = de.tbhelfer.einstellungen.SyncEinstellungenSpeicher.laden(ctx).netz
        val allowed = when (network) {
            de.tbhelfer.einstellungen.SyncEinstellungenSpeicher.Netz.NUR_WLAN -> caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            de.tbhelfer.einstellungen.SyncEinstellungenSpeicher.Netz.NUR_MOBILFUNK -> caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
            else -> caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true || caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }
        if (!allowed) return@withLock Result.retry()
        ServiceLocator.initialisieren(ctx)
        val owner = BtbLifecycleOutbox.owner(ctx) ?: return@withLock Result.success()
        var retry = false
        for (intent in BtbLifecycleOutbox.all(ctx)) {
            if (intent.owner != owner) {
                BtbLifecycleOutbox.problem(ctx, intent, "Gerätebindung hat sich geändert; Auftrag prüfen.")
                continue
            }
            BtbLifecycleOutbox.running.add(intent.btbId)
            try {
                val coordinator = BtbLifecycleCoordinator(ctx)
                val before = SelectiveSyncStore.btbScopes(ctx).firstOrNull { it.clientBtbId == intent.btbId }
                val prepared = coordinator.prepareAccess(intent.localId, intent.btbId,
                    uploadContent = intent.target != BtbLifecyclePolicy.Target.SHIFT_RESUME)
                val access = when (prepared) {
                    is BtbLifecycleCoordinator.PrepareResult.Success -> prepared.access
                    else -> {
                        val message = when (prepared) {
                            is BtbLifecycleCoordinator.PrepareResult.Error -> prepared.message
                            is BtbLifecycleCoordinator.PrepareResult.Conflict -> prepared.message
                            else -> "Gerätefreigabe prüfen."
                        }
                        BtbLifecycleOutbox.problem(ctx, intent, message)
                        retry = true
                        continue
                    }
                }
                val pending = ServiceLocator.syncDao.collabPendingAnyForClientKey(
                    BtbServerStateStore.state(ctx, intent.btbId)?.worksiteServerId.orEmpty(),
                    SyncCollabPendingChangeEntity.TYPE_BTB, intent.btbId)
                if (pending != null && intent.target != BtbLifecyclePolicy.Target.SHIFT_RESUME) {
                    BtbLifecycleOutbox.problem(ctx, intent, "Einträge noch nicht bestätigt oder im Konflikt.")
                    retry = true
                    continue
                }
                val finalized = access.snapshot.status.equals("FERTIG", true) && !access.active
                val alreadyConfirmed = BtbLifecyclePolicy.confirmed(intent.target, finalized, access.participantStatus)
                // A FINISHED/finalized server snapshot cannot acknowledge still-local edits.
                if (alreadyConfirmed && before != null && intent.target != BtbLifecyclePolicy.Target.SHIFT_RESUME) {
                    BtbLifecycleOutbox.problem(ctx, intent, "Serverstatus bestätigt; lokale Änderungen müssen geprüft werden.")
                    continue
                }
                if (before != null && intent.target != BtbLifecyclePolicy.Target.SHIFT_RESUME && !SelectiveSyncStore.removeBtbIfUnchanged(ctx, before)) {
                    BtbLifecycleOutbox.problem(ctx, intent, "Während der Übertragung erneut geändert; weiterer Abgleich folgt.")
                    retry = true
                    continue
                }
                if (intent.target != BtbLifecyclePolicy.Target.SHIFT_RESUME && SelectiveSyncStore.isBtbDirty(ctx, intent.localId, intent.btbId)) {
                    retry = true
                    continue
                }
                if (alreadyConfirmed) {
                    coordinator.applyConfirmed(access)
                    BtbLifecycleOutbox.remove(ctx, intent)
                    continue
                }
                when (val action = coordinator.sendPrepared(access, intent)) {
                    is BtbLifecycleCoordinator.ActionResult.Success -> {
                        val confirmed = BtbLifecyclePolicy.confirmed(intent.target,
                            action.access.snapshot.status.equals("FERTIG", true) && !action.access.active,
                            action.access.participantStatus)
                        if (confirmed && (intent.target == BtbLifecyclePolicy.Target.SHIFT_RESUME || !SelectiveSyncStore.isBtbDirty(ctx, intent.localId, intent.btbId))) {
                            BtbLifecycleOutbox.remove(ctx, intent)
                        }
                        else {
                            BtbLifecycleOutbox.problem(ctx, intent, "Server hat den Zielstatus noch nicht bestätigt.")
                            retry = true
                        }
                    }
                    is BtbLifecycleCoordinator.ActionResult.ParticipantsNotFinished -> {
                        BtbLifecycleOutbox.problem(ctx, intent, "Schicht noch aktiv: " +
                            action.participants.joinToString(", ") { it.displayName ?: it.userId }, true)
                        retry = true
                    }
                    else -> {
                        val message = when (action) {
                            is BtbLifecycleCoordinator.ActionResult.Error -> action.message
                            is BtbLifecycleCoordinator.ActionResult.Conflict -> action.message
                            else -> "Gerätefreigabe prüfen."
                        }
                        BtbLifecycleOutbox.problem(ctx, intent, message)
                        retry = true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                BtbLifecycleOutbox.problem(ctx, intent, error.message ?: "Übertragung fehlgeschlagen.")
                retry = true
            } finally {
                BtbLifecycleOutbox.running.remove(intent.btbId)
            }
        }
        if (retry) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context, replaceWaiting: Boolean = false) {
            val ctx = RecoveryWorkspace.wrap(context.applicationContext)
            if (BtbLifecycleOutbox.all(ctx).isEmpty() || RecoveryWorkspace.blocked()) return
            val network = de.tbhelfer.einstellungen.SyncEinstellungenSpeicher.laden(ctx).netz
            val required = NetworkType.CONNECTED // Worker enforces the selected transport, including metered Wi-Fi.
            val request = OneTimeWorkRequestBuilder<BtbLifecycleWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(required).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("tbhelfer-btb-lifecycle",
                if (replaceWaiting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

