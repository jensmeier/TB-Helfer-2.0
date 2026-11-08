package de.tbhelfer

import android.content.Context
import de.tbhelfer.datenbank.TBDatenbank
import de.tbhelfer.datenbank.SyncDatenbank
import de.tbhelfer.datenbank.OfflineSyncRepository
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.datenbank.CollabLocalBridge
import de.tbhelfer.datenbank.WorksiteBindingRepository
import de.tbhelfer.datenbank.WorksiteStructureSync
import de.tbhelfer.datenbank.WorksiteTrackDataSync
import de.tbhelfer.datenbank.SyncOfflineEditor
import de.tbhelfer.einstellungen.AktuelleBaustelleSpeicher
import de.tbhelfer.datenbank.StreckenpunkteRepository
import de.tbhelfer.datenbank.BaustellenRepository
import de.tbhelfer.datenbank.BaustellenBaumRepository
import de.tbhelfer.datenbank.BaustelleDao
import de.tbhelfer.datenbank.StreckenpunktDao
import de.tbhelfer.datenbank.BaustellenKnotenDao
import de.tbhelfer.datenbank.FotoDao
import de.tbhelfer.datenbank.FotoRepository
import de.tbhelfer.datenbank.TrassenDao
import de.tbhelfer.datenbank.TrassenRepository
import de.tbhelfer.server.SofortSync
import de.tbhelfer.ui.einstellungen.overlay.OverlayEinstellungenSpeicher
import de.tbhelfer.einstellungen.AnsichtEinstellungenSpeicher
import de.tbhelfer.einstellungen.KameraEinstellungenSpeicher
import de.tbhelfer.einstellungen.BtbPdfEinstellungenSpeicher

/**
 * Sehr einfacher ServiceLocator, damit das Projekt ohne DI (Hilt/Koin) übersichtlich bleibt.
 * Später können wir das problemlos austauschen.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val datenbank: TBDatenbank by lazy {
        TBDatenbank.erstellen(appContext)
    }

    /**
     * Eigene Datenbank für NAS-/Cloudflare-Cache und ausstehende Sync-Änderungen.
     * Die bestehende Baustellen-/Vermessungsdatenbank bleibt dadurch unangetastet.
     */
    val syncDatenbank: SyncDatenbank by lazy {
        SyncDatenbank.erstellen(appContext)
    }

    val syncDao by lazy {
        syncDatenbank.syncDao()
    }

    val offlineSyncRepository: OfflineSyncRepository by lazy {
        OfflineSyncRepository(syncDatenbank)
    }

    val collabSyncRepository: CollabSyncRepository by lazy {
        CollabSyncRepository(appContext, syncDatenbank)
    }

    val syncOfflineEditor: SyncOfflineEditor by lazy {
        SyncOfflineEditor(syncDao)
    }

    val baustelleDao: BaustelleDao by lazy {
        datenbank.baustelleDao()
    }

    val baustellenKnotenDao: BaustellenKnotenDao by lazy {
        datenbank.baustellenKnotenDao()
    }

    val streckenpunktDao: StreckenpunktDao by lazy {
        datenbank.streckenpunktDao()
    }

    val fotoDao: FotoDao by lazy {
        datenbank.fotoDao()
    }

    val trassenDao: TrassenDao by lazy {
        datenbank.trassenDao()
    }

    val baustellenRepository: BaustellenRepository by lazy {
        BaustellenRepository(baustelleDao)
    }

    val baustellenBaumRepository: BaustellenBaumRepository by lazy {
        BaustellenBaumRepository(baustellenKnotenDao, baustellenRepository,
            mayApplyServerHead = { rootId -> de.tbhelfer.server.WorksiteLocalSyncStore.desired(appContext, rootId) == null },
            onRootCreated = { rootId ->
                de.tbhelfer.server.WorksiteLocalSyncStore.markCreate(appContext, rootId)
                de.tbhelfer.server.StructureSyncStore.mark(appContext, rootId)
                de.tbhelfer.server.SelectiveSyncWorker.enqueue(appContext)
            },
            onHeadChanged = { rootId, name, oldName ->
                de.tbhelfer.server.WorksiteLocalSyncStore.rename(appContext, rootId, name, oldName)
                de.tbhelfer.server.SelectiveSyncWorker.enqueue(appContext)
            },
            onStructureChanged = { rootId ->
                de.tbhelfer.server.StructureSyncStore.mark(appContext, rootId)
                runCatching { de.tbhelfer.server.SelectiveSyncWorker.enqueue(appContext) }
            })
    }

    val worksiteBindingRepository: WorksiteBindingRepository by lazy {
        WorksiteBindingRepository(
            syncDatabase = syncDatenbank,
            localWorksiteDao = baustellenKnotenDao,
            localWorksiteTree = baustellenBaumRepository,
            mayBootstrap = { !de.tbhelfer.server.WorksiteLocalSyncStore.hasCreation(appContext) },
            onBindingChanged = {
                // Entprellter bestehender WorkManager-Pfad: nach Provisioning/QR-Beitritt nicht nur
                // die Server-Hülle, sondern auch die Unterbaustellen zeitnah synchronisieren.
                SofortSync.anstossen(appContext)
                de.tbhelfer.server.TrackSyncWorker.enqueue(appContext)
            }
        )
    }

    val worksiteStructureSync: WorksiteStructureSync by lazy {
        WorksiteStructureSync(
            syncDatabase = syncDatenbank,
            localWorksiteDao = baustellenKnotenDao,
            bindingRepository = worksiteBindingRepository,
            collabRepository = collabSyncRepository,
            appContext = appContext
        )
    }

    val worksiteTrackDataSync: WorksiteTrackDataSync by lazy {
        WorksiteTrackDataSync(
            database = datenbank,
            appContext = appContext,
            trassenDao = trassenDao,
            streckenpunktDao = streckenpunktDao,
            bindingRepository = worksiteBindingRepository
        )
    }

    val streckenpunkteRepository: StreckenpunkteRepository by lazy {
        StreckenpunkteRepository(streckenpunktDao) { de.tbhelfer.server.TrackSyncWorker.enqueue(appContext) }
    }

    val fotoRepository: FotoRepository by lazy {
        FotoRepository(fotoDao, appContext)
    }

    val collabLocalBridge: CollabLocalBridge by lazy {
        CollabLocalBridge(
            context = appContext,
            syncDatabase = syncDatenbank,
            bindingRepository = worksiteBindingRepository,
            collabRepository = collabSyncRepository,
            fotoRepository = fotoRepository
        )
    }

    val trassenRepository: TrassenRepository by lazy {
        TrassenRepository(trassenDao) { de.tbhelfer.server.TrackSyncWorker.enqueue(appContext) }
    }

    val aktuelleBaustelleSpeicher: AktuelleBaustelleSpeicher by lazy {
        AktuelleBaustelleSpeicher(appContext)
    }

    val overlayEinstellungenSpeicher: OverlayEinstellungenSpeicher by lazy {
        OverlayEinstellungenSpeicher(appContext)
    }

    val ansichtEinstellungenSpeicher: AnsichtEinstellungenSpeicher by lazy {
        AnsichtEinstellungenSpeicher(appContext)
    }

    val kameraEinstellungenSpeicher: KameraEinstellungenSpeicher by lazy {
        KameraEinstellungenSpeicher(appContext)
    }

    val btbPdfEinstellungenSpeicher: BtbPdfEinstellungenSpeicher by lazy {
        BtbPdfEinstellungenSpeicher(appContext)
    }

    fun initialisieren(context: Context) {
        appContext = context.applicationContext
        de.tbhelfer.server.TrackSyncWorker.schedule(appContext)
    }
}


