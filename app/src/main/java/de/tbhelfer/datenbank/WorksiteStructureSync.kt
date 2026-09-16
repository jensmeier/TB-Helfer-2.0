package de.tbhelfer.datenbank

import de.tbhelfer.server.WorksiteNodeApi
import org.json.JSONObject
import java.util.UUID

/**
 * Synchronisiert die fachliche Baustellenstruktur unterhalb der Hauptbaustelle.
 *
 * Wichtige Trennung:
 * - die Hauptbaustelle selbst wird bereits über WorksiteBindingRepository gebunden;
 * - lokale Room-IDs bleiben rein lokal;
 * - jeder Unterknoten erhält einen stabilen clientNodeKey;
 * - BTB-Teamtrennung spielt hier bewusst keine Rolle: der Baum ist gemeinsame Baustellendaten;
 * - nur verantwortliche TBs (LEAD) exportieren Strukturänderungen, Helfer lesen den gemeinsamen Baum.
 *
 * Schreiben benutzt die vorhandene Collaboration-Queue. Dadurch bleiben Offlinebetrieb,
 * Idempotenz und der bestehende WorkManager-Pfad erhalten. Der Snapshot-Endpunkt wird nur zum
 * Abgleich der aktuellen Serverversionen und Tombstones verwendet.
 */
class WorksiteStructureSync(
    private val syncDatabase: SyncDatenbank,
    private val localWorksiteDao: BaustellenKnotenDao,
    private val bindingRepository: WorksiteBindingRepository,
    private val collabRepository: CollabSyncRepository,
    private val api: WorksiteNodeApi = WorksiteNodeApi()
) {
    private val syncDao = syncDatabase.syncDao()
    private val nodeBindingDao = syncDatabase.worksiteNodeSyncDao()

    data class Summary(
        val supportedWorksites: Int,
        val queuedOrUpdated: Int,
        val appliedNodes: Int
    )

    sealed interface Result {
        data class Success(val summary: Summary) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    /**
     * Vor dem normalen Collaboration-Upload aufrufen.
     *
     * LEAD: lokale Änderungen werden gegen den aktuellen Server-Snapshot in die bestehende
     * Mutation-Queue geschrieben. Helfer: Server-Snapshot wird sofort lokal angewendet.
     */
    suspend fun prepareLocalChanges(token: String): Result {
        val contexts = bindingRepository.contextsWithBindings()
        var supported = 0
        var queued = 0
        var applied = 0

        for (bound in contexts) {
            val root = bound.localWorksite ?: continue
            when (val snapshot = api.load(token, bound.context.worksiteServerId)) {
                WorksiteNodeApi.Result.Unsupported -> Unit
                WorksiteNodeApi.Result.SessionInvalid -> return Result.SessionInvalid
                is WorksiteNodeApi.Result.Error -> return Result.Error(snapshot.message)
                is WorksiteNodeApi.Result.Success -> {
                    supported++
                    if (bound.context.memberRole.equals("LEAD", ignoreCase = true)) {
                        queued += queueLocalDiffs(
                            worksiteServerId = bound.context.worksiteServerId,
                            localRoot = root,
                            serverNodes = snapshot.nodes
                        )
                    } else {
                        when (val apply = applySnapshot(bound.context.worksiteServerId, root, snapshot.nodes)) {
                            is ApplyResult.Error -> return Result.Error(apply.message)
                            is ApplyResult.Success -> applied += apply.count
                        }
                    }
                }
            }
        }

        if (contexts.isNotEmpty() && supported == 0) return Result.Unsupported
        return Result.Success(Summary(supported, queued, applied))
    }

    /** Nach einem möglichen Upload nochmals laden, damit Poco und Pixel denselben Baum sehen. */
    suspend fun applyServerStateToLocal(token: String): Result {
        val contexts = bindingRepository.contextsWithBindings()
        var supported = 0
        var applied = 0

        for (bound in contexts) {
            val root = bound.localWorksite ?: continue
            when (val snapshot = api.load(token, bound.context.worksiteServerId)) {
                WorksiteNodeApi.Result.Unsupported -> Unit
                WorksiteNodeApi.Result.SessionInvalid -> return Result.SessionInvalid
                is WorksiteNodeApi.Result.Error -> return Result.Error(snapshot.message)
                is WorksiteNodeApi.Result.Success -> {
                    supported++
                    when (val apply = applySnapshot(bound.context.worksiteServerId, root, snapshot.nodes)) {
                        is ApplyResult.Error -> return Result.Error(apply.message)
                        is ApplyResult.Success -> applied += apply.count
                    }
                }
            }
        }

        if (contexts.isNotEmpty() && supported == 0) return Result.Unsupported
        return Result.Success(Summary(supported, 0, applied))
    }

    private suspend fun queueLocalDiffs(
        worksiteServerId: String,
        localRoot: BaustellenKnotenEntity,
        serverNodes: List<WorksiteNodeApi.Node>
    ): Int {
        val localNodes = localWorksiteDao.alleKnotenZuRootSofort(localRoot.id)
            .filter { it.id != localRoot.id && it.typ != BaustellenKnotenTyp.HAUPT }
        val localById = localNodes.associateBy { it.id }

        // Erst allen lokalen Knoten einen stabilen Key geben. Danach können Parent-Keys sicher
        // referenziert werden, unabhängig davon, welche lokale Long-ID ein anderes Gerät benutzt.
        val bindingsByLocal = nodeBindingDao.forWorksite(worksiteServerId)
            .associateBy { it.localKnotenId }
            .toMutableMap()
        for (node in localNodes) {
            if (bindingsByLocal[node.id] != null) continue
            val binding = SyncWorksiteNodeBindingEntity(
                localKnotenId = node.id,
                localRootKnotenId = localRoot.id,
                worksiteServerId = worksiteServerId,
                clientNodeKey = "wsn-${UUID.randomUUID()}"
            )
            nodeBindingDao.upsert(binding)
            bindingsByLocal[node.id] = binding
        }

        val activeServer = serverNodes.filterNot { it.isDeleted }.associateBy { it.clientNodeKey }
        val deletedServer = serverNodes.filter { it.isDeleted }.associateBy { it.clientNodeKey }
        val ordered = localNodes.sortedWith(compareBy<BaustellenKnotenEntity> { depth(it, localById, localRoot.id) }.thenBy { it.erstelltAmEpochMs }.thenBy { it.id })
        var queued = 0

        for (node in ordered) {
            val binding = bindingsByLocal[node.id] ?: continue
            // Ein Server-Tombstone gewinnt gegen eine noch vorhandene lokale Kopie. So kann ein
            // auf einem anderen TB-Gerät gelöschter Unterpunkt nicht unbemerkt wieder auferstehen.
            if (deletedServer.containsKey(binding.clientNodeKey)) continue

            val parentKey = node.parentId
                ?.takeUnless { it == localRoot.id }
                ?.let { bindingsByLocal[it]?.clientNodeKey }
            val payload = payloadFor(node, parentKey)
            val server = activeServer[binding.clientNodeKey]
            if (server != null && server.status.equals(STATUS_ACTIVE, true) && payloadEquivalent(payload, server.payloadJson)) {
                continue
            }

            val pending = syncDao.collabPendingAnyForClientKey(
                worksiteServerId,
                SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE,
                binding.clientNodeKey
            )
            if (pending != null) {
                syncDao.replaceCollabPendingUpsert(
                    id = pending.id,
                    serverId = server?.serverId,
                    baseVersion = server?.currentVersion,
                    groupId = null,
                    status = STATUS_ACTIVE,
                    areaScopeJson = ALL_SCOPE,
                    payloadJson = payload
                )
            } else {
                collabRepository.enqueueMutation(
                    worksiteServerId = worksiteServerId,
                    entityType = SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE,
                    operation = SyncCollabPendingChangeEntity.OP_UPSERT,
                    serverId = server?.serverId,
                    clientEntityKey = binding.clientNodeKey,
                    baseVersion = server?.currentVersion,
                    groupId = null,
                    status = STATUS_ACTIVE,
                    areaScopeJson = ALL_SCOPE,
                    payloadJson = payload
                )
            }
            queued++
        }

        // Mapping vorhanden, lokaler Knoten aber weg -> echter lokaler DELETE. Noch nie auf den
        // Server gelangte Knoten werden nur aus der Queue/Zuordnung entfernt.
        val currentLocalIds = localNodes.mapTo(hashSetOf()) { it.id }
        val allBindings = nodeBindingDao.forWorksite(worksiteServerId)
        for (binding in allBindings) {
            if (binding.localRootKnotenId != localRoot.id || binding.localKnotenId in currentLocalIds) continue
            val server = activeServer[binding.clientNodeKey]
            val pending = syncDao.collabPendingAnyForClientKey(
                worksiteServerId,
                SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE,
                binding.clientNodeKey
            )
            if (server == null) {
                if (pending != null) syncDao.deleteCollabPendingChange(pending.id)
                nodeBindingDao.deleteForClientKey(worksiteServerId, binding.clientNodeKey)
                continue
            }
            if (pending != null) syncDao.deleteCollabPendingChange(pending.id)
            collabRepository.enqueueMutation(
                worksiteServerId = worksiteServerId,
                entityType = SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE,
                operation = SyncCollabPendingChangeEntity.OP_DELETE,
                serverId = server.serverId,
                clientEntityKey = binding.clientNodeKey,
                baseVersion = server.currentVersion,
                status = STATUS_ACTIVE,
                areaScopeJson = ALL_SCOPE,
                payloadJson = "{}"
            )
            queued++
        }
        return queued
    }

    private sealed interface ApplyResult {
        data class Success(val count: Int) : ApplyResult
        data class Error(val message: String) : ApplyResult
    }

    private suspend fun applySnapshot(
        worksiteServerId: String,
        localRoot: BaustellenKnotenEntity,
        serverNodes: List<WorksiteNodeApi.Node>
    ): ApplyResult {
        val active = serverNodes.filterNot { it.isDeleted }.toMutableList()
        var applied = 0

        // Parent vor Kind. Der Server speichert bewusst stabile Client-Keys statt fremde lokale
        // Room-IDs. Mehrere Runden lösen beliebig tiefe Unterbaustellen auf.
        while (active.isNotEmpty()) {
            var progress = false
            val iterator = active.iterator()
            while (iterator.hasNext()) {
                val server = iterator.next()
                val payload = runCatching { JSONObject(server.payloadJson) }.getOrNull()
                    ?: return ApplyResult.Error("Ungültiger Baustellenknoten vom Server: ${server.clientNodeKey}")
                val parentKey = payload.optNullableString("parent_client_node_key")
                val parentId = if (parentKey == null) {
                    localRoot.id
                } else {
                    val parentBinding = nodeBindingDao.forClientKey(worksiteServerId, parentKey) ?: continue
                    val parent = localWorksiteDao.laden(parentBinding.localKnotenId) ?: continue
                    parent.id
                }

                val typeName = payload.optString("node_type").trim()
                val type = runCatching { BaustellenKnotenTyp.valueOf(typeName) }.getOrNull()
                    ?: return ApplyResult.Error("Unbekannter Baustellenknoten-Typ: $typeName")
                if (type == BaustellenKnotenTyp.HAUPT) {
                    return ApplyResult.Error("Server darf keinen zweiten Hauptbaustellen-Knoten liefern.")
                }

                val binding = nodeBindingDao.forClientKey(worksiteServerId, server.clientNodeKey)
                val existing = binding?.let { localWorksiteDao.laden(it.localKnotenId) }
                val createdAt = payload.optNullableLong("created_at_epoch_ms") ?: existing?.erstelltAmEpochMs ?: System.currentTimeMillis()
                val target = BaustellenKnotenEntity(
                    id = existing?.id ?: 0,
                    rootId = localRoot.id,
                    parentId = parentId,
                    hauptbaustellenName = localRoot.hauptbaustellenName,
                    knotenName = payload.optNullableString("node_name"),
                    altBaustelleId = localRoot.altBaustelleId,
                    typ = type,
                    streckenkennung = payload.optNullableString("route_key"),
                    kmVon = payload.optNullableDouble("km_from"),
                    kmBis = payload.optNullableDouble("km_to"),
                    bahnhofName = payload.optNullableString("station_name"),
                    gleisOderWeiche = payload.optNullableString("track_or_switch"),
                    erstelltAmEpochMs = createdAt
                )

                val localId = if (existing == null) {
                    localWorksiteDao.einfuegen(target)
                } else {
                    if (existing != target) localWorksiteDao.aktualisieren(target)
                    existing.id
                }
                nodeBindingDao.upsert(
                    SyncWorksiteNodeBindingEntity(
                        localKnotenId = localId,
                        localRootKnotenId = localRoot.id,
                        worksiteServerId = worksiteServerId,
                        clientNodeKey = server.clientNodeKey,
                        boundAtEpochMs = binding?.boundAtEpochMs ?: System.currentTimeMillis()
                    )
                )
                applied++
                iterator.remove()
                progress = true
            }
            if (!progress) {
                val keys = active.joinToString { it.clientNodeKey }
                return ApplyResult.Error("Baustellenstruktur enthält nicht auflösbare Parent-Zuordnungen: $keys")
            }
        }

        // Tombstones erst nach dem aktiven Baum anwenden. Das löscht auch lokale Unterkinder;
        // anschließend werden alle dadurch verwaisten ID-Bindings bereinigt.
        for (server in serverNodes.filter { it.isDeleted }) {
            val binding = nodeBindingDao.forClientKey(worksiteServerId, server.clientNodeKey) ?: continue
            val existing = localWorksiteDao.laden(binding.localKnotenId)
            if (existing != null && existing.id != localRoot.id) {
                localWorksiteDao.loescheTeilbaum(existing.id)
                applied++
            }
        }
        for (binding in nodeBindingDao.forWorksite(worksiteServerId)) {
            if (localWorksiteDao.laden(binding.localKnotenId) == null) {
                nodeBindingDao.deleteForLocalNode(binding.localKnotenId)
            }
        }
        return ApplyResult.Success(applied)
    }

    private fun payloadFor(node: BaustellenKnotenEntity, parentKey: String?): String =
        JSONObject().apply {
            if (parentKey == null) put("parent_client_node_key", JSONObject.NULL) else put("parent_client_node_key", parentKey)
            put("node_type", node.typ.name)
            putNullable("node_name", node.knotenName)
            putNullable("route_key", node.streckenkennung)
            putNullable("km_from", node.kmVon)
            putNullable("km_to", node.kmBis)
            putNullable("station_name", node.bahnhofName)
            putNullable("track_or_switch", node.gleisOderWeiche)
            put("created_at_epoch_ms", node.erstelltAmEpochMs)
        }.toString()

    private fun payloadEquivalent(localJson: String, serverJson: String): Boolean {
        val local = runCatching { JSONObject(localJson) }.getOrNull() ?: return false
        val server = runCatching { JSONObject(serverJson) }.getOrNull() ?: return false
        return local.optNullableString("parent_client_node_key") == server.optNullableString("parent_client_node_key") &&
            local.optString("node_type") == server.optString("node_type") &&
            local.optNullableString("node_name") == server.optNullableString("node_name") &&
            local.optNullableString("route_key") == server.optNullableString("route_key") &&
            local.optNullableDouble("km_from") == server.optNullableDouble("km_from") &&
            local.optNullableDouble("km_to") == server.optNullableDouble("km_to") &&
            local.optNullableString("station_name") == server.optNullableString("station_name") &&
            local.optNullableString("track_or_switch") == server.optNullableString("track_or_switch")
    }

    private fun depth(
        node: BaustellenKnotenEntity,
        localById: Map<Long, BaustellenKnotenEntity>,
        rootId: Long
    ): Int {
        var current = node
        var depth = 0
        val seen = hashSetOf<Long>()
        while (current.parentId != null && current.parentId != rootId && seen.add(current.id)) {
            current = localById[current.parentId] ?: break
            depth++
        }
        return depth
    }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableLong(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getLong(name) }.getOrNull()
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()
    }

    companion object {
        private const val STATUS_ACTIVE = "ACTIVE"
        private const val ALL_SCOPE = "{\"all\":true}"
    }
}
