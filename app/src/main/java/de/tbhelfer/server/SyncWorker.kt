package de.tbhelfer.server

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.OfflineSyncRepository
import java.util.concurrent.TimeUnit

/**
 * Schonender Hintergrund-Sync. Der manuelle Button bleibt der sofortige Sync;
 * dieser Worker sorgt nur dafür, dass ein Gerät mit Internet später selbständig nachzieht.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.initialisieren(applicationContext)
        val tokenStore = SecureTokenStore(applicationContext)
        val token = tokenStore.load() ?: return Result.success()

        return when (val result = ServiceLocator.offlineSyncRepository.synchronize(token)) {
            OfflineSyncRepository.FullSyncResult.SessionInvalid -> {
                tokenStore.clear()
                Result.success()
            }
            is OfflineSyncRepository.FullSyncResult.Error -> Result.retry()
            is OfflineSyncRepository.FullSyncResult.Success -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "tbhelfer-server-sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
