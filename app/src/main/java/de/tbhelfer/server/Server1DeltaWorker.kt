package de.tbhelfer.server

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background-Fallback fuer den Server->Android-Delta-Pull.
 *
 * Foreground-Long-Polling laeuft ueber [Server1DeltaPoller]. Dieser Worker stellt sicher, dass
 * App-Start und Netzrueckkehr auch nach laengerem Ausfall einen persistent geplanten Catch-up
 * besitzen.
 */
class Server1DeltaWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (de.tbhelfer.server.recovery.RecoveryWorkspace.blocked()) return Result.failure()

        return when (
            val result = Server1DeltaSync(applicationContext).synchronize(
                firstWaitSeconds = 0,
                maxPages = 50
            )
        ) {
            is Server1DeltaSync.Result.Success ->
                if (result.caughtUp) Result.success() else Result.retry()
            Server1DeltaSync.Result.Unsupported -> Result.success()
            Server1DeltaSync.Result.SessionInvalid -> Result.success()
            is Server1DeltaSync.Result.Error -> {
                ErrorLogStore.record(applicationContext, ErrorHistory.Operation.RECEIVE, result.message)
                Result.retry()
            }
        }
    }

    companion object {
        private const val UNIQUE_NAME = "tbhelfer-server1-delta"

        fun schedule(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<Server1DeltaWorker>(15L, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                "$UNIQUE_NAME-fallback", androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueue(context: Context) {
            val appContext = context.applicationContext
            val request = OneTimeWorkRequestBuilder<Server1DeltaWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS
                )
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}



