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
import de.tbhelfer.datenbank.CollabSyncRepository
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
        val repository = ServiceLocator.offlineSyncRepository

        return when (val catalogResult = SyncCatalogApi().loadCatalog(token)) {
            SyncCatalogApi.Result.SessionInvalid -> {
                tokenStore.clear()
                Result.success()
            }
            is SyncCatalogApi.Result.Error -> Result.retry()
            is SyncCatalogApi.Result.Success -> {
                try {
                    repository.saveCatalog(catalogResult.catalog)
                } catch (_: Exception) {
                    return Result.retry()
                }

                val serverVersion = catalogResult.catalog.serverVersion

                // Server 0.6.0 unterstützt den bewährten Lese-/Offline-Sync, aber noch nicht
                // den inkrementellen Änderungsfeed. Dann sauber beenden statt 404 zu erzeugen.
                if (!supportsIncrementalSync(serverVersion)) {
                    return Result.success()
                }

                when (val result = repository.synchronize(token)) {
                    OfflineSyncRepository.FullSyncResult.SessionInvalid -> {
                        tokenStore.clear()
                        return Result.success()
                    }
                    is OfflineSyncRepository.FullSyncResult.Error -> return Result.retry()
                    is OfflineSyncRepository.FullSyncResult.Success -> Unit
                }

                // Ab 0.7.0 zusätzlich Baustellen-Team/BTB/Mängel/Abnahmen synchronisieren.
                if (!supportsCollabSync(serverVersion)) {
                    return Result.success()
                }

                when (val collabResult = ServiceLocator.collabSyncRepository.synchronizeAll(token)) {
                    CollabSyncRepository.FullSyncResult.SessionInvalid -> {
                        tokenStore.clear()
                        Result.success()
                    }
                    is CollabSyncRepository.FullSyncResult.Error -> Result.retry()
                    is CollabSyncRepository.FullSyncResult.Success -> {
                        collabResult.summary.notifications.forEach { event ->
                            CollabBenachrichtigungen.anzeigen(applicationContext, event)
                        }
                        Result.success()
                    }
                }
            }
        }
    }

    private fun supportsIncrementalSync(version: String?): Boolean {
        val (major, minor, patch) = parseVersion(version) ?: return false
        return major > 0 || minor > 6 || (minor == 6 && patch >= 1)
    }

    private fun supportsCollabSync(version: String?): Boolean {
        val (major, minor, _) = parseVersion(version) ?: return false
        return major > 0 || minor >= 7
    }

    private fun parseVersion(version: String?): Triple<Int, Int, Int>? {
        val values = version?.trim()?.split('.')?.map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        } ?: return null
        return Triple(
            values.getOrElse(0) { 0 },
            values.getOrElse(1) { 0 },
            values.getOrElse(2) { 0 }
        )
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
