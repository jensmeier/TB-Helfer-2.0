package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BtbTeamMediaSync
import de.tbhelfer.datenbank.CollabLocalBridge
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.datenbank.SyncCollabPendingChangeEntity

/**
 * Rollenbewusster BTB-Lifecycle-Client fuer Android.
 *
 * Vor einer Schicht-/Abschlussaktion werden lokale Fachinhalte gezielt fuer genau diese btbId
 * hochgeladen. Der eigentliche Lifecycle wird anschliessend ausschliesslich ueber die festen
 * Server-1.0-Endpunkte gesteuert. Ein Helfer gelangt hier nie auf /finalize.
 */
class BtbLifecycleCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val bridge get() = ServiceLocator.collabLocalBridge
    private val collab get() = ServiceLocator.collabSyncRepository
    private val api = Server1BtbApi()

    sealed interface PrepareResult {
        data class Success(val access: Server1BtbApi.Access) : PrepareResult
        data object SessionInvalid : PrepareResult
        data class Conflict(val message: String) : PrepareResult
        data class Error(val message: String) : PrepareResult
    }

    sealed interface ActionResult {
        data class Success(val access: Server1BtbApi.Access) : ActionResult
        data class ParticipantsNotFinished(
            val participants: List<Server1BtbApi.Participant>
        ) : ActionResult
        data object SessionInvalid : ActionResult
        data class Conflict(val message: String) : ActionResult
        data class Error(val message: String) : ActionResult
    }

    suspend fun prepareAccess(
        localBaustelleId: Long,
        clientBtbId: String,
        uploadContent: Boolean = false
    ): PrepareResult {
        ServiceLocator.initialisieren(appContext)
        val tokenStore = SecureTokenStore(appContext)
        val token = tokenStore.load()
            ?: return PrepareResult.Error("Gerät ist noch nicht für den Server aktiviert.")

        val bound = ServiceLocator.worksiteBindingRepository.contextsWithBindings()
            .firstOrNull {
                it.binding != null &&
                    it.localWorksite?.altBaustelleId == localBaustelleId
            }
            ?: return PrepareResult.Error("Baustelle ist noch nicht eindeutig mit dem Server verbunden.")
        val context = bound.context

        suspend fun uploadPending(): PrepareResult? {
            return when (
                val exported = bridge.exportBtbChange(
                    localBaustelleId = localBaustelleId,
                    clientBtbId = clientBtbId
                )
            ) {
                CollabLocalBridge.BtbExportResult.NotBound ->
                    PrepareResult.Error("Baustelle ist noch nicht mit dem Server verbunden.")
                CollabLocalBridge.BtbExportResult.NotFound ->
                    PrepareResult.Error("Der lokale BTB wurde nicht gefunden.")
                CollabLocalBridge.BtbExportResult.ReadOnly -> null
                is CollabLocalBridge.BtbExportResult.Success -> {
                    when (
                        val upload = collab.synchronizeServer1BtbPending(
                            token = token,
                            api = api,
                            worksiteServerId = exported.worksiteServerId,
                            clientBtbId = clientBtbId
                        )
                    ) {
                        CollabSyncRepository.Server1BtbUploadResult.SessionInvalid -> {
                            tokenStore.clear()
                            PrepareResult.SessionInvalid
                        }
                        CollabSyncRepository.Server1BtbUploadResult.Unsupported ->
                            PrepareResult.Error("Der Server unterstützt den BTB-Lifecycle nicht.")
                        is CollabSyncRepository.Server1BtbUploadResult.Success -> when {
                            upload.conflicts > 0 ->
                                PrepareResult.Conflict("BTB wurde parallel geändert. Bitte zuerst aktualisieren.")
                            upload.failed > 0 ->
                                PrepareResult.Error("BTB-Inhalt konnte nicht vollständig übertragen werden.")
                            else -> null
                        }
                    }
                }
            }
        }

        var entity = collab.entities(
            context.worksiteServerId,
            SyncCollabPendingChangeEntity.TYPE_BTB
        ).firstOrNull { it.clientEntityKey == clientBtbId }

        // Ein neuer lokaler BTB muss einmal gezielt angelegt werden, bevor Rollen/Teilnehmer
        // serverseitig gelesen werden können.
        if (entity == null) {
            uploadPending()?.let { return it }
            entity = collab.entities(
                context.worksiteServerId,
                SyncCollabPendingChangeEntity.TYPE_BTB
            ).firstOrNull { it.clientEntityKey == clientBtbId }
                ?: return PrepareResult.Error("Der BTB ist auf dem Server noch nicht verfügbar.")
        }

        suspend fun loadAccess(): PrepareResult {
            return when (
                val access = api.loadBtbAccess(
                    token = token,
                    worksiteId = context.worksiteServerId,
                    teamId = context.groupId,
                    serverBtbId = entity!!.serverId
                )
            ) {
                Server1BtbApi.AccessResult.Unsupported ->
                    PrepareResult.Error("Der Server-BTB konnte nicht geladen werden.")
                Server1BtbApi.AccessResult.SessionInvalid -> {
                    tokenStore.clear()
                    PrepareResult.SessionInvalid
                }
                is Server1BtbApi.AccessResult.Error -> PrepareResult.Error(access.message)
                is Server1BtbApi.AccessResult.Success -> {
                    remember(context.worksiteServerId, access.access, applyLocal = false)
                    PrepareResult.Success(access.access)
                }
            }
        }

        val first = loadAccess()
        val access = (first as? PrepareResult.Success)?.access ?: return first

        if (!uploadContent) return first

        val writable = !access.snapshot.status.equals("FERTIG", true) &&
            (access.isTeamLead || access.participantStatus.equals("WORKING", true))
        if (!writable) {
            // FINISHED/NOT_PARTICIPATING und historisch FINALIZED bleiben bewusst read-only.
            return first
        }

        uploadPending()?.let { return it }

        // Vor Schichtende/Finalisierung muessen genau die Fotos dieses BTB serverseitig
        // vorhanden sein. Andere BTBs der Baustelle werden nicht angefasst.
        when (
            val media = BtbTeamMediaSync(appContext).synchronize(
                token = token,
                server1Api = Server1FileApi(),
                server1PdfApi = null,
                worksiteServerIds = setOf(context.worksiteServerId),
                btbServerIds = setOf(entity!!.serverId),
                allowUploads = true
            )
        ) {
            BtbTeamMediaSync.Result.SessionInvalid -> {
                tokenStore.clear()
                return PrepareResult.SessionInvalid
            }
            BtbTeamMediaSync.Result.Unsupported ->
                return PrepareResult.Error("BTB-Fotos können vom Server noch nicht verarbeitet werden.")
            is BtbTeamMediaSync.Result.Error -> return PrepareResult.Error(media.message)
            is BtbTeamMediaSync.Result.Success -> Unit
        }

        // Uploads können BTB-Version/Fotozustand geändert haben; Lifecycle immer gegen
        // den frisch gelesenen kanonischen Serverstand ausführen.
        return loadAccess()
    }

    suspend fun shiftFinish(
        localBaustelleId: Long,
        clientBtbId: String
    ): ActionResult {
        val prepared = prepareAccess(localBaustelleId, clientBtbId, uploadContent = true)
        val access = when (prepared) {
            is PrepareResult.Success -> prepared.access
            PrepareResult.SessionInvalid -> return ActionResult.SessionInvalid
            is PrepareResult.Conflict -> return ActionResult.Conflict(prepared.message)
            is PrepareResult.Error -> return ActionResult.Error(prepared.message)
        }
        if (access.isTeamLead) {
            return ActionResult.Error("Der Haupt-TB beendet den BTB über „BTB Abschluss“.")
        }
        return lifecycleAction(access) { token ->
            api.shiftFinish(
                token,
                BtbServerStateStore.state(appContext, clientBtbId)?.worksiteServerId.orEmpty(),
                access.snapshot.teamId,
                access.snapshot.serverId
            )
        }
    }

    suspend fun shiftResume(
        localBaustelleId: Long,
        clientBtbId: String
    ): ActionResult {
        val prepared = prepareAccess(localBaustelleId, clientBtbId)
        val access = when (prepared) {
            is PrepareResult.Success -> prepared.access
            PrepareResult.SessionInvalid -> return ActionResult.SessionInvalid
            is PrepareResult.Conflict -> return ActionResult.Conflict(prepared.message)
            is PrepareResult.Error -> return ActionResult.Error(prepared.message)
        }
        if (access.isTeamLead) {
            return ActionResult.Error("Der Haupt-TB hat keinen Helfer-Schichtstatus.")
        }
        return lifecycleAction(access) { token ->
            api.shiftResume(
                token,
                BtbServerStateStore.state(appContext, clientBtbId)?.worksiteServerId.orEmpty(),
                access.snapshot.teamId,
                access.snapshot.serverId
            )
        }
    }

    suspend fun finalize(
        localBaustelleId: Long,
        clientBtbId: String,
        allowUnfinished: Boolean
    ): ActionResult {
        val prepared = prepareAccess(localBaustelleId, clientBtbId, uploadContent = true)
        val access = when (prepared) {
            is PrepareResult.Success -> prepared.access
            PrepareResult.SessionInvalid -> return ActionResult.SessionInvalid
            is PrepareResult.Conflict -> return ActionResult.Conflict(prepared.message)
            is PrepareResult.Error -> return ActionResult.Error(prepared.message)
        }
        if (!access.isTeamLead || !access.canFinalize) {
            return ActionResult.Error("Nur der Haupt-TB darf den Bautagesbericht endgültig abschließen.")
        }
        return lifecycleAction(access) { token ->
            api.finalizeBtb(
                token,
                BtbServerStateStore.state(appContext, clientBtbId)?.worksiteServerId.orEmpty(),
                access.snapshot.teamId,
                access.snapshot.serverId,
                allowUnfinished
            )
        }
    }

    private suspend fun lifecycleAction(
        before: Server1BtbApi.Access,
        call: (String) -> Server1BtbApi.LifecycleResult
    ): ActionResult {
        val tokenStore = SecureTokenStore(appContext)
        val token = tokenStore.load() ?: return ActionResult.SessionInvalid
        return when (val result = call(token)) {
            Server1BtbApi.LifecycleResult.Unsupported ->
                ActionResult.Error("Der Server-Lifecycle ist nicht verfügbar.")
            Server1BtbApi.LifecycleResult.SessionInvalid -> {
                tokenStore.clear()
                ActionResult.SessionInvalid
            }
            is Server1BtbApi.LifecycleResult.Error -> ActionResult.Error(result.message)
            is Server1BtbApi.LifecycleResult.Conflict -> ActionResult.Conflict(result.code)
            is Server1BtbApi.LifecycleResult.ParticipantsNotFinished ->
                ActionResult.ParticipantsNotFinished(result.participants)
            is Server1BtbApi.LifecycleResult.Success -> {
                val worksiteId = BtbServerStateStore.state(
                    appContext,
                    result.access.snapshot.clientEntityKey
                )?.worksiteServerId
                    ?: BtbServerStateStore.state(
                        appContext,
                        before.snapshot.clientEntityKey
                    )?.worksiteServerId
                    .orEmpty()
                if (worksiteId.isNotBlank()) remember(worksiteId, result.access, applyLocal = true)
                ActionResult.Success(result.access)
            }
        }
    }

    private suspend fun remember(
        worksiteServerId: String,
        access: Server1BtbApi.Access,
        applyLocal: Boolean
    ) {
        if (worksiteServerId.isBlank()) return
        collab.upsertServer1BtbSnapshot(worksiteServerId, access.snapshot)
        if (applyLocal) {
            bridge.applyServerStateToLocal(
                worksiteServerIds = setOf(worksiteServerId),
                btbServerIds = setOf(access.snapshot.serverId),
                includeOtherEntities = false
            )
        } else {
            // Rollen/Lifecycle/Slots merken, aber lokale noch nicht hochgeladene Fachinhalte
            // vor shift-finish/finalize keinesfalls mit dem Server-Snapshot ueberschreiben.
            bridge.primeBtbServerState(setOf(worksiteServerId))
        }
    }
}
