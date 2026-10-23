package de.tbhelfer.datenbank

import de.tbhelfer.server.WorksiteNodeApi
import de.tbhelfer.streckenobjekte.WorksiteObjectIdentity
import org.json.JSONObject
import java.util.UUID
import android.content.Context
import de.tbhelfer.server.StructureSyncStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * Schreiben benutzt weiterhin die vorhandene Offline-Outbox. Server 1.0 transportiert diese
 * Änderungen über den kanonischen worksite_tree_v1-Vertrag; Offlinebetrieb, Idempotenz und stabile
 * Client-Keys bleiben erhalten, ohne den alten /collab-Transport weiterzuführen.
 */
class WorksiteStructureSync(
    private val syncDatabase: SyncDatenbank,
    private val localWorksiteDao: BaustellenKnotenDao,
    private val bindingRepository: WorksiteBindingRepository,
    private val collabRepository: CollabSyncRepository,
    private val api: WorksiteNodeApi = WorksiteNodeApi(),
    private val appContext: Context
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
    suspend fun prepareLocalChanges(token: String, worksiteServerIds: Set<String>? = null): Result {
        val contexts = bindingRepository.contextsWithBindings().filter {
            worksiteServerIds == null || it.context.worksiteServerId in worksiteServerIds
        }.distinctBy { it.context.worksiteServerId }
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
                        if (StructureSyncStore.generation(appContext, root.id) == 0L &&
                            StructureSyncStore.versions(appContext, bound.context.worksiteServerId).isNotEmpty() &&
                            !hasPendingNodes(bound.context.worksiteServerId)) continue
                        StructureSyncStore.mutationMutex.withLock {
                            val problem = validateLocalDiffs(bound.context.worksiteServerId, root, snapshot.nodes)
                            if (problem != null) return Result.Error(problem)
                            queued += queueLocalDiffs(bound.context.worksiteServerId, root, snapshot.nodes)
                        }

                    }
                }
            }
        }

        if (contexts.isNotEmpty() && supported == 0) return Result.Unsupported
        return Result.Success(Summary(supported, queued, applied))
    }

    /**
     * Liefert/erzeugt den stabilen Worksite-Node-Key für einen lokalen Unterknoten.
     *
     * Room-Long-IDs bleiben rein lokal. Neue BTB-Referenzen dürfen ausschließlich diesen
     * clientNodeKey dauerhaft synchronisieren.
     */
    suspend fun ensureClientNodeKey(
        localRootKnotenId: Long,
        localKnotenId: Long
    ): String? = StructureSyncStore.mutationMutex.withLock {
        if (localKnotenId <= 0L || localKnotenId == localRootKnotenId) return@withLock null
        nodeBindingDao.forLocalNode(localKnotenId)?.let { return@withLock it.clientNodeKey }

        val bound = bindingRepository.contextsWithBindings()
            .firstOrNull { it.localWorksite?.id == localRootKnotenId }
            ?: return@withLock null
        val localNode = localWorksiteDao.laden(localKnotenId) ?: return@withLock null
        if (localNode.rootId != localRootKnotenId || localNode.typ == BaustellenKnotenTyp.HAUPT) {
            return@withLock null
        }

        val item = SyncWorksiteNodeBindingEntity(
            localKnotenId = localKnotenId,
            localRootKnotenId = localRootKnotenId,
            worksiteServerId = bound.context.worksiteServerId,
            clientNodeKey = "wsn-${UUID.randomUUID()}"
        )
        nodeBindingDao.upsert(item)
        item.clientNodeKey
    }

    /** Rückauflösung eines stabilen Node-Keys auf die lokale Room-ID dieses Geräts. */
    suspend fun localNodeIdForClientKey(
        localRootKnotenId: Long,
        clientNodeKey: String?
    ): Long? {
        val key = clientNodeKey?.trim().orEmpty()
        if (key.isBlank()) return null
        val bound = bindingRepository.contextsWithBindings()
            .firstOrNull { it.localWorksite?.id == localRootKnotenId }
            ?: return null
        val binding = nodeBindingDao.forClientKey(bound.context.worksiteServerId, key) ?: return null
        return binding.localKnotenId.takeIf { binding.localRootKnotenId == localRootKnotenId }
    }

    /** Shared by full and selective sync. Conflicts remain persistent; no blind rebase. */
    suspend fun synchronizeChanged(token: String, worksiteServerIds: Set<String>? = null): Result = processMutex.withLock {
        val contexts = bindingRepository.contextsWithBindings().filter {
            it.binding != null && it.localWorksite != null &&
                (worksiteServerIds == null || it.context.worksiteServerId in worksiteServerIds)
        }.distinctBy { it.context.worksiteServerId }
        var uploaded = 0
        var applied = 0
        for (bound in contexts) {
            val root = bound.localWorksite ?: continue
            val ids = setOf(bound.context.worksiteServerId)
            val generation = StructureSyncStore.generation(appContext, root.id)
            val before = StructureSyncStore.mutationMutex.withLock { localWorksiteDao.alleKnotenZuRootSofort(root.id) }
            when (val prepare = prepareLocalChanges(token, ids)) {
                is Result.Success -> Unit
                else -> return@withLock prepare
            }
            // CONFLICT is intentionally excluded by the DAO upload query, but must still block acknowledgement.
            if (hasConflicts(bound.context.worksiteServerId)) return@withLock Result.Error("Unterbaustellen: Versionskonflikt prüfen. Lokaler Stand bleibt erhalten.")
            when (val upload = collabRepository.synchronizeServer1WorksiteNodePending(
                token, worksiteServerIds = ids,
                onConfirmed = { worksiteId, node, expectedPayload ->
                    // Reload may already contain somebody else's later edit. That is no basis for
                    // overwriting it with our local payload on the next prepare.
                    if (!node.isDeleted && payloadEquivalent(expectedPayload, node.payloadJson)) {
                        val versions = StructureSyncStore.versions(appContext, worksiteId).toMutableMap()
                        versions[node.clientNodeKey] = node.currentVersion
                        StructureSyncStore.confirm(appContext, worksiteId, versions)
                    }
                }
            )) {
                CollabSyncRepository.Server1WorksiteNodeUploadResult.SessionInvalid -> return@withLock Result.SessionInvalid
                CollabSyncRepository.Server1WorksiteNodeUploadResult.Unsupported -> return@withLock Result.Unsupported
                is CollabSyncRepository.Server1WorksiteNodeUploadResult.Success -> {
                    uploaded += upload.uploaded
                    if (upload.conflicts > 0) return@withLock Result.Error("Unterbaustellen: Versionskonflikt prüfen. Lokaler Stand bleibt erhalten.")
                    if (upload.failed > 0) return@withLock Result.Error("Unterbaustellen noch nicht vollständig übertragen. Erneuter Versuch folgt.")
                }
            }
            // A previous immutable pending operation may have represented an older local edit.
            // Queue that newer edit against the just-confirmed basis before any server apply.
            when (val latest = prepareLocalChanges(token, ids)) {
                is Result.Success -> Unit
                else -> return@withLock latest
            }
            if (hasPendingNodes(bound.context.worksiteServerId))
                return@withLock Result.Error("Neuere Unterbaustellen-Änderungen bleiben vorgemerkt; erneuter Abgleich folgt.")
            // Local saving remains possible during HTTP I/O. Never apply over a newer local tree.
            if (before != localWorksiteDao.alleKnotenZuRootSofort(root.id))
                return@withLock Result.Error("Unterbaustellen lokal weiterbearbeitet; erneuter Abgleich erforderlich.")
            when (val result = applyServerStateToLocal(token, ids, mapOf(root.id to generation))) {
                is Result.Success -> applied += result.summary.appliedNodes
                else -> return@withLock result
            }
        }
        Result.Success(Summary(contexts.size, uploaded, applied))
    }

    private suspend fun hasConflicts(worksiteId: String): Boolean = syncDao.collabConflicts().any {
        it.worksiteServerId == worksiteId && it.entityType == SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE
    }

    /** A delta only reads the affected worksites, retaining local dirty/pending changes. */
    suspend fun applyServerStateToLocal(
        token: String,
        worksiteServerIds: Set<String>? = null,
        expectedGenerations: Map<Long, Long> = emptyMap()
    ): Result {
        val contexts = bindingRepository.contextsWithBindings().filter {
            worksiteServerIds == null || it.context.worksiteServerId in worksiteServerIds
        }.distinctBy { it.context.worksiteServerId }
        var supported = 0
        var applied = 0
        for (bound in contexts) {
            val root = bound.localWorksite ?: continue
            val before = StructureSyncStore.mutationMutex.withLock { localWorksiteDao.alleKnotenZuRootSofort(root.id) }
            when (val snapshot = api.load(token, bound.context.worksiteServerId)) {
                WorksiteNodeApi.Result.Unsupported -> return Result.Unsupported
                WorksiteNodeApi.Result.SessionInvalid -> return Result.SessionInvalid
                is WorksiteNodeApi.Result.Error -> return Result.Error(snapshot.message)
                is WorksiteNodeApi.Result.Success -> StructureSyncStore.mutationMutex.withLock {
                    val dirty = StructureSyncStore.generation(appContext, root.id)
                    val expected = expectedGenerations[root.id]
                    if (!de.tbhelfer.server.WorksiteDeltaPolicy.mayApply(dirty, expected,
                        hasPendingNodes(bound.context.worksiteServerId), before == localWorksiteDao.alleKnotenZuRootSofort(root.id))) {
                        return Result.Error("Unterbaustellen lokal vorgemerkt oder im Konflikt; Serverstand noch nicht übernommen.")
                    }
                    supported++
                    when (val apply = applySnapshot(bound.context.worksiteServerId, root, snapshot.nodes)) {
                        is ApplyResult.Error -> return Result.Error(apply.message)
                        is ApplyResult.Success -> applied += apply.count
                    }
                    StructureSyncStore.confirm(appContext, bound.context.worksiteServerId,
                        snapshot.nodes.associate { it.clientNodeKey to it.currentVersion })
                    if (expected != null && !StructureSyncStore.clear(appContext, root.id, expected))
                        return Result.Error("Unterbaustellen-Bestätigung konnte nicht gespeichert werden.")
                }
            }
        }
        return Result.Success(Summary(supported, 0, applied))
    }

    private suspend fun hasPendingNodes(worksiteId: String): Boolean =
        syncDao.collabPendingChanges(worksiteId).any { it.entityType == SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE } || hasConflicts(worksiteId)

    private suspend fun validateLocalDiffs(worksiteId: String, root: BaustellenKnotenEntity, nodes: List<WorksiteNodeApi.Node>): String? {
        val versions = StructureSyncStore.versions(appContext, worksiteId)
        val local = localWorksiteDao.alleKnotenZuRootSofort(root.id).associateBy { it.id }
        val bindings = nodeBindingDao.forWorksite(worksiteId)
        val keys = bindings.associateBy { it.localKnotenId }
        val remote = nodes.associateBy { it.clientNodeKey }
        for (binding in bindings) {
            val node = local[binding.localKnotenId]
            val server = remote[binding.clientNodeKey]
            val pending = syncDao.collabPendingAnyForClientKey(worksiteId, SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE, binding.clientNodeKey)
            if (pending != null) continue // original mutation/base/version must be retried unchanged
            val parent = node?.parentId?.takeUnless { it == root.id }?.let { keys[it]?.clientNodeKey }
            val same = node != null && server != null && !server.isDeleted && payloadEquivalent(payloadFor(node, parent, server.payloadJson), server.payloadJson)
            if (same || (node == null && (server == null || server.isDeleted))) continue
            if (server == null && versions[binding.clientNodeKey] == null) continue // new local key
            if (!de.tbhelfer.server.WorksiteDeltaPolicy.knownBasis(versions[binding.clientNodeKey], server?.currentVersion, server?.isDeleted ?: true))
                return "Unterbaustellen: abweichender oder unbekannter Server-Basisstand (${binding.clientNodeKey}). Lokalen Stand erhalten und Konflikt prüfen."
        }
        return null
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
        val ordered = localNodes.sortedWith(
            compareBy<BaustellenKnotenEntity> { depth(it, localById, localRoot.id) }
                .thenBy { it.erstelltAmEpochMs }
                .thenBy { it.id }
        )
        var queued = 0

        for (node in ordered) {
            val binding = bindingsByLocal[node.id] ?: continue
            val pending = syncDao.collabPendingAnyForClientKey(
                worksiteServerId,
                SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE,
                binding.clientNodeKey
            )

            if (pending != null) continue

            // Ein Server-Tombstone gewinnt gegen eine noch vorhandene lokale Kopie. So kann ein
            // auf einem anderen TB-Gerät gelöschter Unterpunkt nicht unbemerkt wieder auferstehen.
            if (deletedServer.containsKey(binding.clientNodeKey)) {
                continue
            }

            val parentKey = node.parentId
                ?.takeUnless { it == localRoot.id }
                ?.let { bindingsByLocal[it]?.clientNodeKey }
            val server = activeServer[binding.clientNodeKey]
            val payload = payloadFor(node, parentKey, server?.payloadJson)
            if (server != null && server.status.equals(STATUS_ACTIVE, true) && payloadEquivalent(payload, server.payloadJson)) {
                // Same payload needs no new mutation. Existing pending/conflict was retained above.
                continue
            }

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
            if (pending != null) continue
            if (server == null) {
                nodeBindingDao.deleteForClientKey(worksiteServerId, binding.clientNodeKey)
                continue
            }
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
                val binding = nodeBindingDao.forClientKey(worksiteServerId, server.clientNodeKey)
                val existing = binding?.let { localWorksiteDao.laden(it.localKnotenId) }

                // Ein lokaler DELETE darf bei einem reinen Versionskonflikt nicht durch den noch
                // aktiven Serverstand sofort wieder lokal angelegt werden. Eine Konfliktlösung
                // erfordert eine bewusste Entscheidung statt automatischer neuer Basisversion.
                if (binding != null && existing == null) {
                    val pending = syncDao.collabPendingAnyForClientKey(
                        worksiteServerId,
                        SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE,
                        server.clientNodeKey
                    )
                    if (pending?.operation.equals(SyncCollabPendingChangeEntity.OP_DELETE, ignoreCase = true)) {
                        iterator.remove()
                        progress = true
                        continue
                    }
                }

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

                val createdAt = payload.optNullableLong("created_at_epoch_ms")
                    ?: existing?.erstelltAmEpochMs
                    ?: System.currentTimeMillis()
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
                    bahnhofName = if (type == BaustellenKnotenTyp.FREIE_STRECKE) null else
                        (payload.optNullableString("station_name") ?: payload.optNullableString("station_code")),
                    gleisOderWeiche = WorksiteObjectIdentity.localKey(payload.optNullableString("track_or_switch"), payload.optNullableString("object_type")),
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
            val pending = syncDao.collabPendingAnyForClientKey(
                worksiteServerId,
                SyncCollabPendingChangeEntity.TYPE_WORKSITE_NODE,
                server.clientNodeKey
            )
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

    private fun payloadFor(node: BaustellenKnotenEntity, parentKey: String?, serverPayload: String? = null): String =
        JSONObject().apply {
            if (parentKey == null) put("parent_client_node_key", JSONObject.NULL) else put("parent_client_node_key", parentKey)
            put("node_type", node.typ.name)
            putNullable("node_name", node.knotenName)
            putNullable("route_key", node.streckenkennung)
            putNullable("km_from", node.kmVon)
            putNullable("km_to", node.kmBis)
            putNullable("station_name", node.bahnhofName.takeUnless { node.typ == BaustellenKnotenTyp.FREIE_STRECKE })
            if (node.typ == BaustellenKnotenTyp.FREIE_STRECKE) put("station_code", JSONObject.NULL)
            if (node.typ == BaustellenKnotenTyp.BAHNHOF) {
                val remote = serverPayload?.let { runCatching { JSONObject(it) }.getOrNull() }
                val objectType = WorksiteObjectIdentity.outgoingType(node.gleisOderWeiche,
                    remote?.optNullableString("track_or_switch"), remote?.optNullableString("object_type"))
                putNullable("object_type", objectType)
                putNullable("track_or_switch", WorksiteObjectIdentity.wireKey(node.gleisOderWeiche, objectType))
            } else putNullable("track_or_switch", node.gleisOderWeiche)
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
            (local.optString("node_type") == BaustellenKnotenTyp.FREIE_STRECKE.name ||
                local.optNullableString("station_name") == (server.optNullableString("station_name") ?: server.optNullableString("station_code"))) &&
            WorksiteObjectIdentity.equivalent(local.optNullableString("track_or_switch"),
                server.optNullableString("track_or_switch"), server.optNullableString("object_type"))
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
        private val processMutex = Mutex()
        private const val STATUS_ACTIVE = "ACTIVE"
        private const val ALL_SCOPE = "{\"all\":true}"
    }
}


