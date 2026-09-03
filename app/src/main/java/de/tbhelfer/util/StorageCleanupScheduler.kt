package de.tbhelfer.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object StorageCleanupScheduler {

    private const val UNIQUE_PERIODIC_NAME = "tb_storage_cleanup_weekly"
    private const val UNIQUE_ONCE_NAME = "tb_storage_cleanup_once"

    /** Wöchentliches Cleanup (defensiv, mit Constraints). */
    fun scheduleWeekly(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val req = PeriodicWorkRequestBuilder<StorageCleanupWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
    }

    /** Manuelles einmaliges Cleanup (Einstellungen-Button). */
    fun runOnce(context: Context): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val req = OneTimeWorkRequestBuilder<StorageCleanupWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_ONCE_NAME, ExistingWorkPolicy.REPLACE, req)

        return req
    }
}
