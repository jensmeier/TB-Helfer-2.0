package de.tbhelfer.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BtbTeamMediaSync
import de.tbhelfer.datenbank.CollabLocalBridge
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.datenbank.OfflineSyncRepository
import de.tbhelfer.einstellungen.SyncEinstellungenSpeicher
import java.util.concurrent.TimeUnit

/**
 * Schonender Hintergrund-Sync.
 *
 * Zeitplan und Netzart sind gerätelokal einstellbar. Bewusst manuelle Sync-Aktionen werden in der
 * UI behandelt; dieser Worker ist ausschließlich der automatische/entprellte Hintergrundpfad und
 * hält sich deshalb strikt an die gewählte Netzregel.
 */
class SyncWorker(
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
                    SyncStatusSpeicher.erfolgMerken(applicationContext)
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
                    SyncStatusSpeicher.erfolgMerken(applicationContext)
                    return Result.success()
                }

                val collabRepository = ServiceLocator.collabSyncRepository
                val localBridge = ServiceLocator.collabLocalBridge
                val btbMedia = BtbTeamMediaSync(applicationContext)
                val notifications = mutableListOf<CollabBenachrichtigungen.Ereignis>()

                // 1) Serverkontext + fremde Änderungen holen. Damit kennen wir die gültige
                //    TB-Gruppe und Bereichsberechtigung und laden bereits vorhandene Attachments.
                when (val first = collabRepository.synchronizeAll(token)) {
                    CollabSyncRepository.FullSyncResult.SessionInvalid -> {
                        tokenStore.clear()
                        return Result.success()
                    }
                    is CollabSyncRepository.FullSyncResult.Error -> return Result.retry()
                    is CollabSyncRepository.FullSyncResult.Success -> notifications += first.summary.notifications
                }

                // 2) Eingehende BTB-/Mängel-/Abnahme-Stände in die bestehenden Fachspeicher spiegeln.
                runCatching { localBridge.applyServerStateToLocal() }
                    .getOrElse { return Result.retry() }

                // BTB-Bilder der eigenen verantwortlichen TB-Gruppe ebenfalls übernehmen und bereits
                // vorhandene lokale Bilder dieses BTB hochladen. Bilder anderer TB-Gruppen sind im
                // Server-Feed nicht sichtbar und können daher hier nicht vermischt werden.
                val firstBtbMedia = when (val media = btbMedia.synchronize(token)) {
                    BtbTeamMediaSync.Result.SessionInvalid -> {
                        tokenStore.clear()
                        return Result.success()
                    }
                    BtbTeamMediaSync.Result.Unsupported -> null
                    is BtbTeamMediaSync.Result.Error -> return Result.retry()
                    is BtbTeamMediaSync.Result.Success -> media.summary
                }

                // 3) Lokale Text-/Statusänderungen in die idempotente Upload-Queue übernehmen.
                val exported = runCatching { localBridge.exportLocalChanges() }
                    .getOrElse { return Result.retry() }

                // 4) Neue Entities müssen zuerst ihre feste Server-UUID erhalten, bevor Bilder
                //    an BTB/DEFECT/ACCEPTANCE angehängt werden können.
                if (exported.queuedOrUpdated > 0) {
                    when (val second = collabRepository.synchronizeAll(token)) {
                        CollabSyncRepository.FullSyncResult.SessionInvalid -> {
                            tokenStore.clear()
                            return Result.success()
                        }
                        is CollabSyncRepository.FullSyncResult.Error -> return Result.retry()
                        is CollabSyncRepository.FullSyncResult.Success -> notifications += second.summary.notifications
                    }
                    runCatching { localBridge.applyServerStateToLocal() }
                        .getOrElse { return Result.retry() }
                }

                // 5) Nach dem Text-Upload nochmals BTB-Medien versuchen. Das deckt insbesondere einen
                //    ganz neuen BTB ab, dessen Server-UUID erst in Schritt 4 entstanden ist.
                val secondBtbMedia = when (val media = btbMedia.synchronize(token)) {
                    BtbTeamMediaSync.Result.SessionInvalid -> {
                        tokenStore.clear()
                        return Result.success()
                    }
                    BtbTeamMediaSync.Result.Unsupported -> null
                    is BtbTeamMediaSync.Result.Error -> return Result.retry()
                    is BtbTeamMediaSync.Result.Success -> media.summary
                }

                // 6) Mängel-/Abnahmebilder bei JEDEM Lauf versuchen. Das ist absichtlich nicht an
                //    queuedOrUpdated gekoppelt: ein Netzabbruch darf ein Bild nicht dauerhaft verlieren.
                val attachmentUpload = when (val result = localBridge.uploadLocalAttachments(token)) {
                    CollabLocalBridge.AttachmentUploadResult.SessionInvalid -> {
                        tokenStore.clear()
                        return Result.success()
                    }
                    CollabLocalBridge.AttachmentUploadResult.Unsupported -> null
                    is CollabLocalBridge.AttachmentUploadResult.Error -> return Result.retry()
                    is CollabLocalBridge.AttachmentUploadResult.Success -> result.summary
                }

                // 7) Neue ATTACHMENT-Changes einmal holen/ACKen und sofort lokal anwenden.
                val btbUploaded = (firstBtbMedia?.uploaded ?: 0) + (secondBtbMedia?.uploaded ?: 0)
                if (btbUploaded > 0 || (attachmentUpload?.uploaded ?: 0) > 0) {
                    when (val third = collabRepository.synchronizeAll(token)) {
                        CollabSyncRepository.FullSyncResult.SessionInvalid -> {
                            tokenStore.clear()
                            return Result.success()
                        }
                        is CollabSyncRepository.FullSyncResult.Error -> return Result.retry()
                        is CollabSyncRepository.FullSyncResult.Success -> notifications += third.summary.notifications
                    }
                    runCatching { localBridge.applyServerStateToLocal() }
                        .getOrElse { return Result.retry() }
                    when (val media = btbMedia.synchronize(token)) {
                        BtbTeamMediaSync.Result.SessionInvalid -> {
                            tokenStore.clear()
                            return Result.success()
                        }
                        is BtbTeamMediaSync.Result.Error -> return Result.retry()
                        else -> Unit
                    }
                }

                notifications
                    .distinctBy { "${it.entityType}|${it.changedByName.orEmpty()}" }
                    .forEach { event -> CollabBenachrichtigungen.anzeigen(applicationContext, event) }

                // Offline-Status für die Baustellenkarten auffrischen. Fehler beim reinen
                // Anzeigenamen/Teamzähler dürfen einen ansonsten erfolgreichen Daten-Sync nicht kippen.
                runCatching { refreshWorksiteDisplayState(token) }
                SyncStatusSpeicher.erfolgMerken(applicationContext)
                Result.success()
            }
        }
    }

    private suspend fun refreshWorksiteDisplayState(token: String) {
        val contexts = ServiceLocator.worksiteBindingRepository.contextsWithBindings()
        val teamApi = TeamManagementApi()
        contexts.forEach { item ->
            val worksiteId = item.context.worksiteServerId
            val localRootId = item.localWorksite?.id
            if (localRootId != null) {
                TeamDisplayCache.bindLocalRoot(applicationContext, localRootId, worksiteId)
            }
            when (val result = teamApi.loadTeam(token, worksiteId)) {
                is TeamManagementApi.TeamResult.Success ->
                    TeamDisplayCache.remember(applicationContext, result.team, localRootId)
                else -> {
                    if (localRootId != null) {
                        TeamDisplayCache.markSuccessfulSync(applicationContext, localRootId, worksiteId)
                    } else {
                        TeamDisplayCache.markSuccessfulSync(applicationContext, worksiteId)
                    }
                }
            }
        }
    }

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
            val appContext = context.applicationContext
            val settings = SyncEinstellungenSpeicher.laden(appContext)
            val workManager = WorkManager.getInstance(appContext)
            if (settings.zeitplan == SyncEinstellungenSpeicher.Zeitplan.MANUELL) {
                workManager.cancelUniqueWork(UNIQUE_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val repeatHours = when (settings.zeitplan) {
                SyncEinstellungenSpeicher.Zeitplan.MANUELL -> return
                SyncEinstellungenSpeicher.Zeitplan.STUENDLICH -> 1L
                SyncEinstellungenSpeicher.Zeitplan.TAEGLICH -> 24L
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(repeatHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
