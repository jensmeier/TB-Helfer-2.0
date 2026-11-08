package de.tbhelfer.server

import android.content.Context
import androidx.work.*
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.WorksiteTrackDataSync

/** Persistent, network-constrained work; each accepted edit has a queued successor. */
class TrackSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (de.tbhelfer.server.recovery.RecoveryWorkspace.blocked()) return Result.failure()
        ServiceLocator.initialisieren(applicationContext)
        val token = SecureTokenStore(applicationContext).load() ?: return Result.success()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var failed = false
            suspend fun attempt(block: suspend () -> Unit) {
                try { block() }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) {
                    failed = true
                    ErrorLogStore.record(applicationContext, ErrorHistory.Operation.FULL_SYNC,
                        error.message ?: "Streckendatenabgleich fehlgeschlagen")
                }
            }
            attempt {
                when (val result = ServiceLocator.worksiteTrackDataSync.synchronize(token)) {
                    is WorksiteTrackDataSync.Result.Error -> error(result.message)
                    WorksiteTrackDataSync.Result.SessionInvalid -> error("Anmeldung abgelaufen")
                    else -> Unit
                }
            }
            attempt { SharedKmSync(applicationContext, ServiceLocator.datenbank).synchronize(token) }
            // Raw uploads cannot delay the small track/KM changes above.
            attempt { SurveyOriginalOutbox.upload(applicationContext, token, ServiceLocator.trassenDao,
                ServiceLocator.worksiteBindingRepository) }
            // A just-uploaded original can now resolve; do not wait for the periodic fallback.
            attempt { SharedKmSync(applicationContext, ServiceLocator.datenbank).synchronize(token) }
            if (failed) Result.retry() else Result.success()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrackSyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("track-data-periodic-v1", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueue(context: Context): Operation {
            val request = OneTimeWorkRequestBuilder<TrackSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            return WorkManager.getInstance(context).enqueueUniqueWork("track-data-sync-v1", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
