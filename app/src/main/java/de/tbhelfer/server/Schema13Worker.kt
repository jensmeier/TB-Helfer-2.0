package de.tbhelfer.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.WorksiteTrackDataSync
import de.tbhelfer.einstellungen.SyncEinstellungenSpeicher
import de.tbhelfer.ui.baustelle.WorksiteLocalDeleteService
import java.util.concurrent.TimeUnit

/**
 * Schema-13-Ergaenzung zum bestehenden SyncWorker.
 *
 * Sie bleibt absichtlich separat, bis der alte grosse SyncWorker konsolidiert wird: mobile
 * Streckendaten und Baustellen-Loeschtombstones koennen so getestet werden, ohne BTB/Maengel/
 * Abnahmen erneut umzubauen.
 */
class Schema13Worker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.initialisieren(applicationContext)
        val settings = SyncEinstellungenSpeicher.laden(applicationContext)
        if (settings.zeitplan == SyncEinstellungenSpeicher.Zeitplan.MANUELL) return Result.success()
        if (!networkAllowed(settings.netz)) return Result.success()

        val tokenStore = SecureTokenStore(applicationContext)
        val token = tokenStore.load() ?: return Result.success()

        when (val track = ServiceLocator.worksiteTrackDataSync.synchronize(token)) {
            WorksiteTrackDataSync.Result.SessionInvalid -> {
                tokenStore.clear()
                return Result.success()
            }
            is WorksiteTrackDataSync.Result.Error -> return Result.retry()
            else -> Unit
        }

        when (val deletions = WorksiteDeletionApi().list(token)) {
            WorksiteDeletionApi.ListResult.Unsupported -> Unit
            WorksiteDeletionApi.ListResult.SessionInvalid -> {
                tokenStore.clear()
                return Result.success()
            }
            is WorksiteDeletionApi.ListResult.Error -> return Result.retry()
            is WorksiteDeletionApi.ListResult.Success -> {
                val bindings = ServiceLocator.worksiteBindingRepository.contextsWithBindings()
                    .associateBy { it.context.worksiteServerId }
                deletions.tombstones.forEach { tombstone ->
                    val localRoot = bindings[tombstone.worksiteId]?.localWorksite?.id ?: return@forEach
                    runCatching {
                        WorksiteLocalDeleteService.delete(applicationContext, localRoot)
                    }.getOrElse { return Result.retry() }
                }
            }
        }
        return Result.success()
    }

    private fun networkAllowed(net: SyncEinstellungenSpeicher.Netz): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return when (net) {
            SyncEinstellungenSpeicher.Netz.NUR_WLAN -> wifi
            SyncEinstellungenSpeicher.Netz.NUR_MOBILFUNK -> cellular
            SyncEinstellungenSpeicher.Netz.WLAN_UND_MOBILFUNK -> wifi || cellular
        }
    }

    companion object {
        private const val PERIODIC_NAME = "tbhelfer-schema13-periodic"
        private const val NOW_NAME = "tbhelfer-schema13-now"

        fun schedule(context: Context) {
            val appContext = context.applicationContext
            val settings = SyncEinstellungenSpeicher.laden(appContext)
            val wm = WorkManager.getInstance(appContext)
            if (settings.zeitplan == SyncEinstellungenSpeicher.Zeitplan.MANUELL) {
                wm.cancelUniqueWork(PERIODIC_NAME)
                return
            }
            val hours = when (settings.zeitplan) {
                SyncEinstellungenSpeicher.Zeitplan.MANUELL -> return
                SyncEinstellungenSpeicher.Zeitplan.STUENDLICH -> 1L
                SyncEinstellungenSpeicher.Zeitplan.TAEGLICH -> 24L
            }
            val request = PeriodicWorkRequestBuilder<Schema13Worker>(hours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<Schema13Worker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
