package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.SyncCollabPendingChangeEntity
import de.tbhelfer.server.SofortSync
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Verbindet das fachliche BTB-Loeschen mit der vorhandenen Collaboration-Queue.
 *
 * Ein moderner BTB wird lokal sofort entfernt, seine echten Foto-Dateien bleiben erhalten.
 * Wenn die Baustelle mit dem Server verbunden ist, wird fuer exakt dieselbe btbId zusaetzlich
 * ein dauerhaftes Loeschvorhaben gemerkt. Das verhindert, dass ein noch auf dem Server
 * vorhandener BTB zwischen Poco/Pixel wieder "aufersteht".
 *
 * Der bestehende Serververtrag ab 0.7.0 kennt bereits DELETE-Mutationen; es wird kein zweiter
 * Netzwerkpfad aufgebaut. Offline bleibt das Vorhaben lokal liegen und wird beim naechsten
 * erfolgreichen Pull in einen DELETE-Auftrag mit der aktuellen Serverversion ueberfuehrt.
 */
object BtbDeleteSyncCoordinator {

    data class UserDeleteResult(
        val local: BtbDeleteService.Ergebnis,
        val serverSyncVorgemerkt: Boolean
    )

    data class ReconcileSummary(
        val lokalDurchServerGeloescht: Int = 0,
        val serverDeletesEingereiht: Int = 0,
        val erledigteVorhaben: Int = 0
    )

    private data class DeleteIntent(
        val worksiteServerId: String,
        val groupId: String,
        val btbId: String
    )

    private const val PREFS = "btb_delete_sync_intents_v1"
    private const val TYPE = SyncCollabPendingChangeEntity.TYPE_BTB

    /**
     * Benutzeraktion aus dem Verlauf. Erst wird ein eventuelles Server-Loeschvorhaben dauerhaft
     * vorgemerkt, danach lokal geloescht. So darf ein App-Abbruch zwischen beiden Schritten den
     * BTB nicht spaeter vom Server wieder einspielen.
     */
    suspend fun deleteUserRequested(context: Context, entry: BautagebuchEintrag): UserDeleteResult {
        val appContext = context.applicationContext
        val btbId = entry.btbId?.trim().orEmpty()
        require(btbId.isNotBlank() && !btbId.startsWith("legacy:", ignoreCase = true)) {
            "BTB-ID fehlt oder ist noch ein Alt-BTB."
        }

        val serverIntent = resolveIntentForLocalEntry(entry)
        if (serverIntent != null) {
            saveIntent(appContext, serverIntent)
            // Wenn die Server-Entity bereits im Cache bekannt ist, kann DELETE sofort vorbereitet
            // werden. Andernfalls bleibt das Intent bis zum naechsten erfolgreichen Pull bestehen.
            prepareDeleteIfPossible(serverIntent)
        }

        val local = BtbDeleteService.loeschen(appContext, entry)
        if (serverIntent != null) {
            runCatching { SofortSync.anstossen(appContext) }
        }
        return UserDeleteResult(local, serverIntent != null)
    }

    /**
     * Muss nach einem erfolgreichen Collaboration-Pull und vor dem normalen BTB-Import laufen.
     * 1) Server-seitig geloeschte BTBs werden auch lokal entfernt.
     * 2) Lokale Loeschvorhaben erhalten jetzt die aktuelle Server-UUID/-Version und werden als
     *    DELETE in die vorhandene Offline-Queue geschrieben.
     *
     * Rueckgabe serverDeletesEingereiht > 0 bedeutet: Worker soll die Queue noch in demselben Lauf
     * erneut synchronisieren, bevor normale Server-BTBs lokal gespiegelt werden.
     */
    suspend fun reconcileAfterSuccessfulPull(context: Context): ReconcileSummary {
        val appContext = context.applicationContext
        var localDeleted = 0
        var queued = 0
        var cleared = 0

        val bound = ServiceLocator.worksiteBindingRepository.contextsWithBindings()
            .filter { it.binding != null && it.localWorksite != null }

        // Erst echte Server-Tombstones lokal anwenden. Nur die eigene TB-Gruppe darf den
        // gruppenbezogenen BTB beeinflussen.
        for (item in bound) {
            val local = item.localWorksite ?: continue
            val server = item.context
            val deleted = ServiceLocator.syncDao.deletedCollabEntities(server.worksiteServerId, TYPE)
                .filter { it.groupId == server.groupId }
            if (deleted.isEmpty()) continue

            val entries = BautagebuchSpeicher.verlaufFuerBaustelle(
                appContext,
                local.altBaustelleId,
                local.hauptbaustellenName
            )
            val byId = entries.mapNotNull { row -> row.btbId?.let { it to row } }.toMap()
            for (entity in deleted) {
                val key = entity.clientEntityKey?.trim().orEmpty()
                if (key.isBlank() || key.startsWith("legacy:")) continue
                val pending = ServiceLocator.syncDao.collabPendingAnyForClientKey(server.worksiteServerId, TYPE, key)
                if ((pending != null && !pending.operation.equals(SyncCollabPendingChangeEntity.OP_DELETE, true)) ||
                    de.tbhelfer.server.SelectiveSyncStore.isBtbDirty(appContext, local.altBaustelleId, key)) {
                    ServiceLocator.collabLocalBridge.preservePendingBtbForConflict(
                        local.altBaustelleId, local.hauptbaustellenName, key, server.worksiteServerId)
                    continue
                }
                val row = byId[key]
                if (row != null) {
                    ServiceLocator.collabLocalBridge.applyConfirmedBtbDeletion(server.worksiteServerId, entity.serverId, entity.entityVersion)
                    if (BautagebuchSpeicher.ladenById(appContext, local.altBaustelleId, local.hauptbaustellenName, key) == null) localDeleted++
                }
                if (removeIntent(appContext, server.worksiteServerId, server.groupId, key)) {
                    cleared++
                }
            }
        }

        // Danach lokale Loeschvorhaben mit dem soeben aktualisierten Servercache versoehnen.
        for (stored in loadIntents(appContext)) {
            val contextRow = ServiceLocator.syncDao.worksiteContext(stored.worksiteServerId)
            val effectiveGroupId = stored.groupId.ifBlank { contextRow?.groupId.orEmpty() }
            val intent = stored.copy(groupId = effectiveGroupId)
            val result = prepareDeleteIfPossible(intent)
            when (result) {
                PrepareResult.QUEUED -> queued++
                PrepareResult.ALREADY_PENDING -> Unit
                PrepareResult.SERVER_ABSENT -> {
                    // Nach einem erfolgreichen Pull und ohne aktive Server-Entity ist der BTB
                    // serverseitig nicht vorhanden. Ein nur lokal/offline erzeugter und vor dem
                    // ersten Upload geloeschter BTB braucht dann keinen DELETE mehr.
                    if (removeIntent(appContext, stored.worksiteServerId, stored.groupId, stored.btbId)) {
                        cleared++
                    }
                }
                PrepareResult.WAIT_FOR_CONTEXT -> Unit
            }
        }

        return ReconcileSummary(localDeleted, queued, cleared)
    }

    private enum class PrepareResult {
        QUEUED,
        ALREADY_PENDING,
        SERVER_ABSENT,
        WAIT_FOR_CONTEXT
    }

    private suspend fun prepareDeleteIfPossible(intent: DeleteIntent): PrepareResult {
        val groupId = intent.groupId.trim()
        if (intent.worksiteServerId.isBlank() || groupId.isBlank()) return PrepareResult.WAIT_FOR_CONTEXT

        val dao = ServiceLocator.syncDao
        var pending = dao.collabPendingAnyForClientKey(intent.worksiteServerId, TYPE, intent.btbId)
        val serverEntity = dao.collabEntities(intent.worksiteServerId, TYPE)
            .firstOrNull { it.clientEntityKey == intent.btbId && it.groupId == groupId }

        if (pending?.operation.equals(SyncCollabPendingChangeEntity.OP_DELETE, ignoreCase = true)) {
            if (serverEntity == null) {
                // DELETE ist bereits unterwegs/fehlgeschlagen. Solange der Servercache noch keinen
                // bestaetigten Tombstone zeigt, bleibt das Intent bestehen und der Queue-Eintrag
                // wird vom normalen Retry erneut versucht.
                return PrepareResult.ALREADY_PENDING
            }

            val needsRebase = pending?.state == SyncCollabPendingChangeEntity.STATE_CONFLICT ||
                pending?.serverId != serverEntity.serverId ||
                pending?.baseVersion != serverEntity.entityVersion
            if (!needsRebase) return PrepareResult.ALREADY_PENDING

            // Der Benutzer hat bewusst "BTB loeschen" bestaetigt. Wurde derselbe BTB auf einem
            // anderen Teamgeraet danach noch gespeichert, bekommt DELETE die aktuelle
            // Serverversion und wird erneut eingereiht, statt dauerhaft als 409 haengen zu bleiben.
            pending?.let { dao.deleteCollabPendingChange(it.id) }
            pending = null
        }

        if (serverEntity == null) {
            // Ein noch nicht hochgeladener UPSERT darf nach bewusstem Loeschen nicht spaeter doch
            // auf dem Server entstehen. UPLOADING kann in einem parallelen Worker bereits unterwegs
            // sein; das persistente Intent bleibt deshalb bis zum naechsten Pull bestehen.
            if (pending != null) dao.deleteCollabPendingChange(pending.id)
            return PrepareResult.SERVER_ABSENT
        }

        if (pending != null) dao.deleteCollabPendingChange(pending.id)
        val serverContext = dao.worksiteContext(intent.worksiteServerId)
        ServiceLocator.collabSyncRepository.enqueueMutation(
            worksiteServerId = intent.worksiteServerId,
            entityType = TYPE,
            operation = SyncCollabPendingChangeEntity.OP_DELETE,
            serverId = serverEntity.serverId,
            clientEntityKey = intent.btbId,
            baseVersion = serverEntity.entityVersion,
            groupId = groupId,
            status = serverEntity.status,
            areaScopeJson = serverContext?.areaScopeJson ?: serverEntity.areaScopeJson,
            payloadJson = "{}"
        )
        return PrepareResult.QUEUED
    }

    private suspend fun resolveIntentForLocalEntry(entry: BautagebuchEintrag): DeleteIntent? {
        val roots = ServiceLocator.worksiteBindingRepository.localMainWorksites()
        val root = roots.firstOrNull { it.altBaustelleId == entry.baustelleId } ?: return null
        val binding = ServiceLocator.worksiteBindingRepository.bindingForLocalRoot(root.id) ?: return null
        val serverContext = ServiceLocator.syncDao.worksiteContext(binding.worksiteServerId)
        return DeleteIntent(
            worksiteServerId = binding.worksiteServerId,
            groupId = serverContext?.groupId.orEmpty(),
            btbId = entry.btbId?.trim().orEmpty()
        )
    }

    private fun saveIntent(context: Context, intent: DeleteIntent) {
        val json = JSONObject()
            .put("worksiteServerId", intent.worksiteServerId)
            .put("groupId", intent.groupId)
            .put("btbId", intent.btbId)
            .toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(prefKey(intent.worksiteServerId, intent.groupId, intent.btbId), json)
            .apply()
    }

    private fun loadIntents(context: Context): List<DeleteIntent> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.values.mapNotNull { raw ->
            val text = raw as? String ?: return@mapNotNull null
            runCatching {
                val json = JSONObject(text)
                DeleteIntent(
                    worksiteServerId = json.optString("worksiteServerId"),
                    groupId = json.optString("groupId"),
                    btbId = json.optString("btbId")
                )
            }.getOrNull()?.takeIf { it.worksiteServerId.isNotBlank() && it.btbId.isNotBlank() }
        }

    private fun removeIntent(context: Context, worksiteServerId: String, groupId: String, btbId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val candidates = prefs.all.entries.filter { (_, raw) ->
            val text = raw as? String ?: return@filter false
            runCatching {
                val json = JSONObject(text)
                json.optString("worksiteServerId") == worksiteServerId &&
                    json.optString("btbId") == btbId &&
                    (groupId.isBlank() || json.optString("groupId").isBlank() || json.optString("groupId") == groupId)
            }.getOrDefault(false)
        }
        if (candidates.isEmpty()) return false
        val editor = prefs.edit()
        candidates.forEach { editor.remove(it.key) }
        editor.apply()
        return true
    }

    private fun prefKey(worksiteServerId: String, groupId: String, btbId: String): String {
        val raw = "$worksiteServerId|$groupId|$btbId"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "delete_${hash.take(32)}"
    }
}

