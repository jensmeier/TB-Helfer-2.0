package de.tbhelfer.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.WorksiteTrackDataSync
import de.tbhelfer.einstellungen.SyncEinstellungenSpeicher
import de.tbhelfer.ui.baustelle.WorksiteLocalDeleteService

/**
 * Schema-13-Ergaenzung zum bestehenden SyncWorker.
 *
 * Sie bleibt für Legacy-0.7-Streckendaten und Baustellen-Loeschtombstones separat. Auf Server 1.0
 * übernimmt der gemeinsame Server1AndroidSyncCoordinator track_data_v1, damit manueller und
 * Hintergrund-Sync denselben kanonischen Pfad verwenden und keine parallelen Doppeluploads laufen.
 */
class Schema13Worker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.initialisieren(applicationContext)
        val settings = SyncEinstellungenSpeicher.laden(applicationContext)
        if (!networkAllowed(settings.netz)) return Result.success()

        val tokenStore = SecureTokenStore(applicationContext)
        val token = tokenStore.load() ?: return Result.success()

        val server1TrackData = when (val caps = ServerCapabilitiesApi().load()) {
            is ServerCapabilitiesApi.Result.Success ->
                caps.snapshot.supports("track_data_v1")
            ServerCapabilitiesApi.Result.Unsupported -> false
            is ServerCapabilitiesApi.Result.Error -> return Result.retry()
        }

        if (!server1TrackData) {
            when (val track = ServiceLocator.worksiteTrackDataSync.synchronize(token)) {
                WorksiteTrackDataSync.Result.SessionInvalid -> {
                    tokenStore.clear()
                    return Result.success()
                }
                is WorksiteTrackDataSync.Result.Error -> return Result.retry()
                else -> Unit
            }
        }

        when (val deletions = WorksiteDeletionApi().list(token)) {
            WorksiteDeletionApi.ListResult.Unsupported -> Unit
            WorksiteDeletionApi.ListResult.SessionInvalid -> {
                tokenStore.clear()
                return Result.success()
            }
            is WorksiteDeletionApi.ListResult.Error -> return Result.retry()
            is WorksiteDeletionApi.ListResult.Success -> {
                deletions.tombstones.forEach { tombstone ->
                    // Der normale Server-Kontext blendet eine geloeschte Baustelle schon aus.
                    // Deshalb muss der Tombstone direkt ueber die persistente lokale Binding-Tabelle
                    // aufgeloest werden und darf nicht von contextsWithBindings() abhaengen.
                    val localRoot = ServiceLocator.worksiteBindingRepository
                        .localRootForServer(tombstone.worksiteId)?.id ?: return@forEach
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
            // Der alte MANUELL/STÜNDLICH/TÄGLICH-Zeitplan ist entfernt. Schema-13-Daten
            // werden nur noch ereignisgetrieben angestoßen, bis ihr eigener Delta-Pfad steht.
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(PERIODIC_NAME)
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
