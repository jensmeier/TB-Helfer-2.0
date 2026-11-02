package de.tbhelfer.server

import android.content.Context
import androidx.work.*
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.SyncCollabPendingChangeEntity
import de.tbhelfer.server.recovery.RecoveryWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Durable explicit lifecycle commands. Read current state BEFORE replaying an ambiguous POST. */
class BtbLifecycleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (RecoveryWorkspace.blocked()) return Result.failure()
        return try {
            if (BtbLifecycleProcessor.process(applicationContext, inputData.getString("btb_id"))) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private val immediateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Durable retry is enqueued before an optional immediate attempt. Never cancel an active POST. */
        fun enqueue(context: Context, replaceWaiting: Boolean = false, onlyBtbId: String? = null) {
            val ctx = RecoveryWorkspace.wrap(context.applicationContext)
            if (RecoveryWorkspace.blocked()) return
            for (intent in BtbLifecycleOutbox.all(ctx).filter { onlyBtbId == null || it.btbId == onlyBtbId }) {
                val request = OneTimeWorkRequestBuilder<BtbLifecycleWorker>()
                    .setInputData(workDataOf("btb_id" to intent.btbId))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
                WorkManager.getInstance(ctx).enqueueUniqueWork("tbhelfer-btb-lifecycle:${intent.btbId}",
                    ExistingWorkPolicy.KEEP, request)
                // A click or network return need not wait for a WorkManager backoff timer.
                // The same mutex, intent lookup and changeId protect both execution paths.
                if (replaceWaiting && BtbLifecycleOutbox.interactive.add(intent.btbId)) {
                    immediateScope.launch {
                        try {
                            BtbLifecycleProcessor.process(ctx, intent.btbId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            BtbLifecycleOutbox.problem(ctx, intent, "Abgleich unterbrochen; automatischer Wiederholungsversuch folgt.")
                        } finally {
                            BtbLifecycleOutbox.interactive.remove(intent.btbId)
                        }
                    }
                }
            }
        }
    }
}

/** Read the durable intent after acquiring the common sync lock, so a second runner cannot replay a completed command. */
internal object BtbLifecycleProcessor {
    suspend fun process(applicationContext: Context, onlyBtbId: String? = null): Boolean = BtbLifecycleOutbox.processMutex.withLock {
        val ctx = RecoveryWorkspace.wrap(applicationContext)
        if (RecoveryWorkspace.blocked()) return@withLock false
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val network = de.tbhelfer.einstellungen.SyncEinstellungenSpeicher.laden(ctx).netz
        val allowed = when (network) {
            de.tbhelfer.einstellungen.SyncEinstellungenSpeicher.Netz.NUR_WLAN -> caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            de.tbhelfer.einstellungen.SyncEinstellungenSpeicher.Netz.NUR_MOBILFUNK -> caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
            else -> caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true || caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }
        if (!allowed) {
            BtbLifecycleOutbox.all(ctx).filter { onlyBtbId == null || it.btbId == onlyBtbId }.forEach {
                BtbLifecycleOutbox.problem(ctx, it, "Warte auf die in den Einstellungen erlaubte Internetverbindung.")
            }
            return@withLock true
        }
        ServiceLocator.initialisieren(ctx)
        val owner = BtbLifecycleOutbox.owner(ctx) ?: run {
            BtbLifecycleOutbox.all(ctx).filter { onlyBtbId == null || it.btbId == onlyBtbId }.forEach {
                BtbLifecycleOutbox.problem(ctx, it, "Gerätefreigabe prüfen; Abschlussauftrag bleibt gespeichert.")
            }
            return@withLock false
        }
        var retry = false
        for (intent in BtbLifecycleOutbox.all(ctx).filter { onlyBtbId == null || it.btbId == onlyBtbId }) {
            if (intent.owner != owner) {
                BtbLifecycleOutbox.problem(ctx, intent, "Gerätebindung hat sich geändert; Auftrag prüfen.")
                continue
            }
            BtbLifecycleOutbox.running.add(intent.btbId)
            BtbLifecycleOutbox.phase(intent, BtbLifecycleOutbox.Phase.PREPARING)
            try {
                val coordinator = BtbLifecycleCoordinator(ctx)
                val before = SelectiveSyncStore.btbScopes(ctx).firstOrNull { it.clientBtbId == intent.btbId }
                val prepared = coordinator.prepareAccess(intent.localId, intent.btbId,
                    uploadContent = intent.target != BtbLifecyclePolicy.Target.SHIFT_RESUME,
                    onPhase = { BtbLifecycleOutbox.phase(intent, it) })
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
                BtbLifecycleOutbox.phase(intent, BtbLifecycleOutbox.Phase.VERIFYING)
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
                    BtbLifecycleOutbox.problem(ctx, intent, "Neue lokale Änderung wartet noch auf Übertragung.")
                    retry = true
                    continue
                }
                if (alreadyConfirmed) {
                    coordinator.applyConfirmed(access)
                    BtbLifecycleOutbox.remove(ctx, intent)
                    continue
                }
                BtbLifecycleOutbox.phase(intent, BtbLifecycleOutbox.Phase.SENDING)
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
                BtbLifecycleOutbox.phase(intent, null)
                BtbLifecycleOutbox.running.remove(intent.btbId)
            }
        }
        retry
    }

}
