package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.ServiceLocator
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

    suspend fun prepareAccess(localBaustelleId: Long, clientBtbId: String): PrepareResult {
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

        when (
            val exported = bridge.exportBtbChange(
                localBaustelleId = localBaustelleId,
                clientBtbId = clientBtbId
            )
        ) {
            CollabLocalBridge.BtbExportResult.NotBound ->
                return PrepareResult.Error("Baustelle ist noch nicht mit dem Server verbunden.")
            CollabLocalBridge.BtbExportResult.NotFound ->
                return PrepareResult.Error("Der lokale BTB wurde nicht gefunden.")
            CollabLocalBridge.BtbExportResult.ReadOnly -> Unit
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
                        return PrepareResult.SessionInvalid
                    }
                    CollabSyncRepository.Server1BtbUploadResult.Unsupported ->
                        return PrepareResult.Error("Der Server unterstützt den BTB-Lifecycle nicht.")
                    is CollabSyncRepository.Server1BtbUploadResult.Success -> {
                        if (upload.conflicts > 0) {
                            return PrepareResult.Conflict("BTB wurde parallel geändert. Bitte zuerst aktualisieren.")
                        }
                        if (upload.failed > 0) {
                            return PrepareResult.Error("BTB-Inhalt konnte nicht vollständig übertragen werden.")
                        }
                    }
                }
            }
        }

        val entity = collab.entities(
            context.worksiteServerId,
            SyncCollabPendingChangeEntity.TYPE_BTB
        ).firstOrNull { it.clientEntityKey == clientBtbId }
            ?: return PrepareResult.Error("Der BTB ist auf dem Server noch nicht verfügbar.")

        return when (
            val access = api.loadBtbAccess(
                token = token,
                worksiteId = context.worksiteServerId,
                teamId = context.groupId,
                serverBtbId = entity.serverId
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
                remember(context.worksiteServerId, access.access)
                PrepareResult.Success(access.access)
            }
        }
    }

    suspend fun shiftFinish(
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
        val prepared = prepareAccess(localBaustelleId, clientBtbId)
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
                if (worksiteId.isNotBlank()) remember(worksiteId, result.access)
                ActionResult.Success(result.access)
            }
        }
    }

    private suspend fun remember(
        worksiteServerId: String,
        access: Server1BtbApi.Access
    ) {
        if (worksiteServerId.isBlank()) return
        collab.upsertServer1BtbSnapshot(worksiteServerId, access.snapshot)
        bridge.applyServerStateToLocal()
    }
}
