package de.tbhelfer.datenbank

import androidx.room.withTransaction
import de.tbhelfer.server.SyncCatalogApi
import de.tbhelfer.server.SyncChangeApi
import de.tbhelfer.server.SyncDetailApi
import org.json.JSONObject

/**
 * Lokale Offline-Schicht zwischen Server-API und UI.
 *
 * Der Katalog sowie gezielt ausgewählte Streckenabschnitte und Bahnhofobjekte werden getrennt
 * von der bestehenden fachlichen Datenbank gespeichert. Ab Server 0.6.1 kommen inkrementelle
 * Änderungen, Tombstones, Upload-Queue und Versionskonflikte hinzu.
 */
class OfflineSyncRepository(
    private val database: SyncDatenbank
) {
    private val dao: SyncDao
        get() = database.syncDao()

    data class CachedCatalog(
        val catalog: SyncCatalogApi.Catalog,
        val savedAtEpochMs: Long
    )

    data class SyncSummary(
        val incrementalSupported: Boolean,
        val pulledChanges: Int,
        val uploadedChanges: Int,
        val pendingChanges: Int,
        val conflicts: Int,
        val lastSequenceNo: Long
    )

    sealed interface FullSyncResult {
        data class Success(val summary: SyncSummary) : FullSyncResult
        data object SessionInvalid : FullSyncResult
        data class Error(val message: String) : FullSyncResult
    }

    private sealed interface PullOutcome {
        data class Success(val supported: Boolean, val count: Int, val lastSequenceNo: Long) : PullOutcome
        data object SessionInvalid : PullOutcome
        data class Error(val message: String) : PullOutcome
    }

    private sealed interface UploadOutcome {
        data class Success(val supported: Boolean, val count: Int) : UploadOutcome
        data object SessionInvalid : UploadOutcome
        data class Error(val message: String) : UploadOutcome
    }

    suspend fun saveCatalog(catalog: SyncCatalogApi.Catalog, nowEpochMs: Long = System.currentTimeMillis()) {
        val routes = catalog.routes.map { route ->
            SyncRouteEntity(
                serverId = route.id,
                routeNumber = route.routeNumber,
                name = route.name,
                description = route.description,
                sectionCount = route.sectionCount,
                objectCount = route.objectCount,
                entityVersion = route.entityVersion,
                downloadedAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        }
        val stations = catalog.stations.map { station ->
            SyncStationEntity(
                serverId = station.id,
                stationCode = station.stationCode,
                name = station.name,
                shortName = station.shortName,
                description = station.description,
                objectCount = station.objectCount,
                entityVersion = station.entityVersion,
                downloadedAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        }

        // Lokale Pending-/Konfliktänderungen dürfen von einem frischen globalen
        // Server-Snapshot nicht überschrieben werden. Sie bleiben sichtbar, bis der Upload
        // erfolgreich ist oder der Konflikt bewusst aufgelöst wurde.
        val localChanges = (dao.pendingChanges() + dao.conflicts())
            .filter { it.operation == SyncPendingChangeEntity.OP_UPSERT }
        val localRouteIds = localChanges
            .filter { it.objectKind == SyncPendingChangeEntity.KIND_ROUTE }
            .mapNotNull { it.serverId }
            .toSet()
        val localStationIds = localChanges
            .filter { it.objectKind == SyncPendingChangeEntity.KIND_STATION }
            .mapNotNull { it.serverId }
            .toSet()
        val preservedRoutes = localRouteIds.mapNotNull { dao.route(it) }
        val preservedStations = localStationIds.mapNotNull { dao.station(it) }
        val mergedRoutes = (routes.filterNot { it.serverId in localRouteIds } + preservedRoutes)
            .distinctBy { it.serverId }
        val mergedStations = (stations.filterNot { it.serverId in localStationIds } + preservedStations)
            .distinctBy { it.serverId }

        database.withTransaction {
            dao.clearRoutes()
            dao.clearStations()
            if (mergedRoutes.isNotEmpty()) dao.upsertRoutes(mergedRoutes)
            if (mergedStations.isNotEmpty()) dao.upsertStations(mergedStations)
            dao.upsertState(SyncStateEntity(key = SyncStateEntity.KEY_LAST_CATALOG_SYNC, longValue = nowEpochMs))
            dao.upsertState(SyncStateEntity(key = SyncStateEntity.KEY_LAST_SUCCESSFUL_SYNC, longValue = nowEpochMs))
            dao.upsertState(SyncStateEntity(key = SyncStateEntity.KEY_SERVER_VERSION, textValue = catalog.serverVersion))
        }
    }

    suspend fun loadCachedCatalog(): CachedCatalog? {
        val savedAt = dao.state(SyncStateEntity.KEY_LAST_CATALOG_SYNC)?.longValue ?: return null
        val serverVersion = dao.state(SyncStateEntity.KEY_SERVER_VERSION)?.textValue
        val routes = dao.routes().map { route ->
            SyncCatalogApi.RouteItem(
                id = route.serverId,
                routeNumber = route.routeNumber,
                name = route.name,
                description = route.description,
                sectionCount = route.sectionCount,
                objectCount = route.objectCount,
                entityVersion = route.entityVersion
            )
        }
        val stations = dao.stations().map { station ->
            SyncCatalogApi.StationItem(
                id = station.serverId,
                stationCode = station.stationCode,
                name = station.name,
                shortName = station.shortName,
                description = station.description,
                objectCount = station.objectCount,
                entityVersion = station.entityVersion
            )
        }
        return CachedCatalog(
            catalog = SyncCatalogApi.Catalog(serverVersion = serverVersion, routes = routes, stations = stations),
            savedAtEpochMs = savedAt
        )
    }

    suspend fun saveSelectedSections(
        routeServerId: String,
        items: List<SyncDetailApi.SectionItem>,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        val entities = items.map { item ->
            SyncSectionEntity(
                serverId = item.id,
                routeServerId = item.routeId ?: routeServerId,
                designation = item.designation,
                kmFromM = item.kmFromM,
                kmToM = item.kmToM,
                kmMinM = item.kmMinM,
                kmMaxM = item.kmMaxM,
                dataStatus = item.dataStatus,
                geometryJson = item.geometryJson,
                entityVersion = item.entityVersion,
                downloadedAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        }
        val localChanges = (dao.pendingChanges() + dao.conflicts())
            .filter {
                it.objectKind == SyncPendingChangeEntity.KIND_SECTION &&
                    it.operation == SyncPendingChangeEntity.OP_UPSERT
            }
        val localIds = localChanges.mapNotNull { it.serverId }.toSet()
        val preserved = localIds.mapNotNull { dao.section(it) }
            .filter { it.routeServerId == routeServerId }
        val merged = (entities.filterNot { it.serverId in localIds } + preserved)
            .distinctBy { it.serverId }

        database.withTransaction {
            dao.clearSectionsForRoute(routeServerId)
            if (merged.isNotEmpty()) dao.upsertSections(merged)
            dao.upsertState(SyncStateEntity(key = SyncStateEntity.KEY_LAST_SUCCESSFUL_SYNC, longValue = nowEpochMs))
        }
    }

    suspend fun saveSelectedStationObjects(
        stationServerId: String,
        items: List<SyncDetailApi.ObjectItem>,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        val entities = items.map { item ->
            SyncObjectEntity(
                serverId = item.id,
                routeServerId = item.routeId,
                stationServerId = item.stationId ?: stationServerId,
                objectType = item.objectType,
                designation = item.designation,
                kmM = item.kmM,
                dataStatus = item.dataStatus,
                geometryJson = item.geometryJson,
                propertiesJson = item.propertiesJson,
                entityVersion = item.entityVersion,
                downloadedAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        }
        val localChanges = (dao.pendingChanges() + dao.conflicts())
            .filter {
                it.objectKind == SyncPendingChangeEntity.KIND_OBJECT &&
                    it.operation == SyncPendingChangeEntity.OP_UPSERT
            }
        val localIds = localChanges.mapNotNull { it.serverId }.toSet()
        val preserved = localIds.mapNotNull { dao.objectById(it) }
            .filter { it.stationServerId == stationServerId }
        val merged = (entities.filterNot { it.serverId in localIds } + preserved)
            .distinctBy { it.serverId }

        database.withTransaction {
            dao.clearObjectsForStation(stationServerId)
            if (merged.isNotEmpty()) dao.upsertObjects(merged)
            dao.upsertState(SyncStateEntity(key = SyncStateEntity.KEY_LAST_SUCCESSFUL_SYNC, longValue = nowEpochMs))
        }
    }

    suspend fun loadSectionsForRoute(routeServerId: String): List<SyncSectionEntity> = dao.sectionsForRoute(routeServerId)
    suspend fun loadObjectsForStation(stationServerId: String): List<SyncObjectEntity> = dao.objectsForStation(stationServerId)
    suspend fun sectionCountForRoute(routeServerId: String): Int = dao.sectionCountForRoute(routeServerId)
    suspend fun objectCountForStation(stationServerId: String): Int = dao.objectCountForStation(stationServerId)

    suspend fun enqueueChange(
        objectKind: String,
        serverId: String?,
        baseVersion: Long?,
        operation: String,
        payloadJson: String
    ): Long = dao.enqueueChange(
        SyncPendingChangeEntity(
            objectKind = objectKind,
            serverId = serverId,
            baseVersion = baseVersion,
            operation = operation,
            payloadJson = payloadJson
        )
    )

    /**
     * Voller Sync-Zyklus: erst Serveränderungen holen, dann lokale Queue hochladen,
     * anschließend eigene Serveränderungen wieder einlesen. Server 0.6.0 und älter bleiben
     * kompatibel; dort wird der inkrementelle Teil einfach übersprungen.
     */
    suspend fun synchronize(token: String, api: SyncChangeApi = SyncChangeApi()): FullSyncResult {
        val firstPull = pullChanges(token, api)
        when (firstPull) {
            PullOutcome.SessionInvalid -> return FullSyncResult.SessionInvalid
            is PullOutcome.Error -> return FullSyncResult.Error(firstPull.message)
            is PullOutcome.Success -> Unit
        }

        val upload = uploadPendingChanges(token, api)
        when (upload) {
            UploadOutcome.SessionInvalid -> return FullSyncResult.SessionInvalid
            is UploadOutcome.Error -> return FullSyncResult.Error(upload.message)
            is UploadOutcome.Success -> Unit
        }

        var pulled = (firstPull as PullOutcome.Success).count
        var lastSequence = firstPull.lastSequenceNo
        var supported = firstPull.supported || (upload as UploadOutcome.Success).supported

        if (supported && upload.count > 0) {
            when (val secondPull = pullChanges(token, api)) {
                PullOutcome.SessionInvalid -> return FullSyncResult.SessionInvalid
                is PullOutcome.Error -> return FullSyncResult.Error(secondPull.message)
                is PullOutcome.Success -> {
                    pulled += secondPull.count
                    lastSequence = secondPull.lastSequenceNo
                    supported = supported || secondPull.supported
                }
            }
        }

        return FullSyncResult.Success(
            SyncSummary(
                incrementalSupported = supported,
                pulledChanges = pulled,
                uploadedChanges = upload.count,
                pendingChanges = dao.pendingChangeCount(),
                conflicts = dao.conflictCount(),
                lastSequenceNo = lastSequence
            )
        )
    }

    private suspend fun pullChanges(token: String, api: SyncChangeApi): PullOutcome {
        var after = dao.state(SyncStateEntity.KEY_LAST_CHANGE_SEQUENCE)?.longValue ?: 0L
        var total = 0
        var supported = true

        while (true) {
            when (val result = api.loadChanges(token, after)) {
                SyncChangeApi.PullResult.Unsupported -> {
                    supported = false
                    return PullOutcome.Success(false, total, after)
                }
                SyncChangeApi.PullResult.SessionInvalid -> return PullOutcome.SessionInvalid
                is SyncChangeApi.PullResult.Error -> return PullOutcome.Error(result.message)
                is SyncChangeApi.PullResult.Success -> {
                    val page = result.page
                    database.withTransaction {
                        page.changes.forEach { applyServerChange(it) }
                        after = page.nextSequenceNo
                        dao.upsertState(SyncStateEntity(key = SyncStateEntity.KEY_LAST_CHANGE_SEQUENCE, longValue = after))
                        dao.upsertState(SyncStateEntity(key = SyncStateEntity.KEY_LAST_SUCCESSFUL_SYNC, longValue = System.currentTimeMillis()))
                    }
                    total += page.changes.size
                    when (val ack = api.acknowledge(token, after)) {
                        SyncChangeApi.AckResult.SessionInvalid -> return PullOutcome.SessionInvalid
                        is SyncChangeApi.AckResult.Error -> return PullOutcome.Error(ack.message)
                        SyncChangeApi.AckResult.Unsupported -> supported = false
                        SyncChangeApi.AckResult.Success -> Unit
                    }
                    if (!page.hasMore) return PullOutcome.Success(supported, total, after)
                }
            }
        }
    }

    private suspend fun uploadPendingChanges(token: String, api: SyncChangeApi): UploadOutcome {
        var uploaded = 0
        var supported = true
        val changes = dao.pendingChanges()
        for (change in changes) {
            dao.updateChangeState(
                id = change.id,
                state = SyncPendingChangeEntity.STATE_UPLOADING,
                attempts = change.attempts + 1,
                lastError = null
            )
            when (val result = api.uploadMutation(token, change)) {
                SyncChangeApi.MutationResult.Unsupported -> {
                    supported = false
                    dao.updateChangeState(change.id, SyncPendingChangeEntity.STATE_PENDING, change.attempts, "Server unterstützt Upload noch nicht.")
                    break
                }
                SyncChangeApi.MutationResult.SessionInvalid -> return UploadOutcome.SessionInvalid
                is SyncChangeApi.MutationResult.Error -> {
                    dao.updateChangeState(change.id, SyncPendingChangeEntity.STATE_FAILED, change.attempts + 1, result.message)
                }
                is SyncChangeApi.MutationResult.Conflict -> {
                    val conflictJson = JSONObject()
                        .put("detail", result.detail)
                        .put("server_version", result.serverVersion)
                    if (!result.serverEntityJson.isNullOrBlank()) {
                        conflictJson.put("server_entity", runCatching { JSONObject(result.serverEntityJson) }.getOrElse { result.serverEntityJson })
                    }
                    dao.updateChangeState(change.id, SyncPendingChangeEntity.STATE_CONFLICT, change.attempts + 1, conflictJson.toString())
                }
                is SyncChangeApi.MutationResult.Applied -> {
                    applyMutationResult(change, result)
                    dao.deletePendingChange(change.id)
                    uploaded++
                }
            }
        }
        return UploadOutcome.Success(supported, uploaded)
    }

    private suspend fun applyMutationResult(change: SyncPendingChangeEntity, result: SyncChangeApi.MutationResult.Applied) {
        val entityId = result.entityId ?: change.serverId ?: return
        if (change.operation == SyncPendingChangeEntity.OP_DELETE) {
            applyDelete(change.objectKind, entityId, result.entityVersion ?: change.baseVersion ?: 0L)
            return
        }
        if (!result.serverEntityJson.isNullOrBlank()) {
            applyServerChange(
                SyncChangeApi.ServerChange(
                    sequenceNo = result.sequenceNo ?: 0L,
                    entityType = change.objectKind,
                    entityId = entityId,
                    operation = "UPDATE",
                    entityVersion = result.entityVersion,
                    changedAt = null,
                    dataJson = result.serverEntityJson
                )
            )
        }
    }

    private suspend fun applyServerChange(change: SyncChangeApi.ServerChange) {
        val type = change.entityType.uppercase()
        val version = change.entityVersion ?: 0L
        if (change.operation.equals("DELETE", ignoreCase = true)) {
            applyDelete(type, change.entityId, version)
            return
        }

        val data = runCatching { JSONObject(change.dataJson) }.getOrElse { JSONObject() }
        val now = System.currentTimeMillis()
        when (type) {
            SyncPendingChangeEntity.KIND_ROUTE -> {
                val existing = dao.route(change.entityId)
                val routeNumber = data.optString("route_number").ifBlank { existing?.routeNumber ?: return }
                dao.upsertRoutes(
                    listOf(
                        SyncRouteEntity(
                            serverId = change.entityId,
                            routeNumber = routeNumber,
                            name = data.optNullableString("name") ?: existing?.name,
                            description = data.optNullableString("description") ?: existing?.description,
                            sectionCount = existing?.sectionCount ?: 0,
                            objectCount = existing?.objectCount ?: 0,
                            entityVersion = version.takeIf { it > 0 } ?: data.optLong("current_version", existing?.entityVersion ?: 0),
                            downloadedAtEpochMs = existing?.downloadedAtEpochMs ?: now,
                            updatedAtEpochMs = now
                        )
                    )
                )
            }

            SyncPendingChangeEntity.KIND_STATION -> {
                val existing = dao.station(change.entityId)
                val name = data.optString("name").ifBlank { existing?.name ?: return }
                dao.upsertStations(
                    listOf(
                        SyncStationEntity(
                            serverId = change.entityId,
                            stationCode = data.optNullableString("station_code") ?: existing?.stationCode,
                            name = name,
                            shortName = data.optNullableString("short_name") ?: existing?.shortName,
                            description = data.optNullableString("description") ?: existing?.description,
                            objectCount = existing?.objectCount ?: 0,
                            entityVersion = version.takeIf { it > 0 } ?: data.optLong("current_version", existing?.entityVersion ?: 0),
                            downloadedAtEpochMs = existing?.downloadedAtEpochMs ?: now,
                            updatedAtEpochMs = now
                        )
                    )
                )
            }

            SyncPendingChangeEntity.KIND_SECTION -> {
                val existing = dao.section(change.entityId)
                val routeId = data.optString("route_id").ifBlank { existing?.routeServerId ?: return }
                dao.upsertSections(
                    listOf(
                        SyncSectionEntity(
                            serverId = change.entityId,
                            routeServerId = routeId,
                            designation = data.optNullableString("designation") ?: existing?.designation,
                            kmFromM = data.optNullableDouble("km_from_m") ?: existing?.kmFromM,
                            kmToM = data.optNullableDouble("km_to_m") ?: existing?.kmToM,
                            kmMinM = data.optNullableDouble("km_min_m") ?: existing?.kmMinM,
                            kmMaxM = data.optNullableDouble("km_max_m") ?: existing?.kmMaxM,
                            dataStatus = data.optNullableString("data_status") ?: existing?.dataStatus,
                            geometryJson = data.optJsonText("geom") ?: data.optJsonText("geometry") ?: existing?.geometryJson,
                            entityVersion = version.takeIf { it > 0 } ?: data.optLong("current_version", existing?.entityVersion ?: 0),
                            downloadedAtEpochMs = existing?.downloadedAtEpochMs ?: now,
                            updatedAtEpochMs = now
                        )
                    )
                )
            }

            SyncPendingChangeEntity.KIND_OBJECT -> {
                val existing = dao.objectById(change.entityId)
                val routeId = data.optNullableString("route_id") ?: existing?.routeServerId
                val stationId = data.optNullableString("station_id") ?: existing?.stationServerId
                if (routeId.isNullOrBlank() && stationId.isNullOrBlank()) return
                val objectType = data.optString("object_type").ifBlank { existing?.objectType ?: "OTHER" }
                val designation = data.optNullableString("designation") ?: existing?.designation
                dao.upsertObjects(
                    listOf(
                        SyncObjectEntity(
                            serverId = change.entityId,
                            routeServerId = routeId,
                            stationServerId = stationId,
                            objectType = objectType,
                            designation = designation,
                            kmM = data.optNullableDouble("km_m") ?: existing?.kmM,
                            dataStatus = data.optNullableString("data_status") ?: existing?.dataStatus,
                            geometryJson = data.optJsonText("geom") ?: data.optJsonText("geometry") ?: existing?.geometryJson,
                            propertiesJson = data.optJsonText("properties") ?: existing?.propertiesJson,
                            entityVersion = version.takeIf { it > 0 } ?: data.optLong("current_version", existing?.entityVersion ?: 0),
                            downloadedAtEpochMs = existing?.downloadedAtEpochMs ?: now,
                            updatedAtEpochMs = now
                        )
                    )
                )
            }
        }
    }

    private suspend fun applyDelete(type: String, serverId: String, version: Long) {
        val now = System.currentTimeMillis()
        when (type.uppercase()) {
            SyncPendingChangeEntity.KIND_ROUTE -> dao.markRouteDeleted(serverId, version, now)
            SyncPendingChangeEntity.KIND_STATION -> dao.markStationDeleted(serverId, version, now)
            SyncPendingChangeEntity.KIND_SECTION -> dao.markSectionDeleted(serverId, version, now)
            SyncPendingChangeEntity.KIND_OBJECT -> dao.markObjectDeleted(serverId, version, now)
            else -> return
        }
        dao.upsertTombstones(
            listOf(
                SyncTombstoneEntity(
                    objectKind = type.uppercase(),
                    serverId = serverId,
                    entityVersion = version,
                    deletedAtEpochMs = now
                )
            )
        )
    }

    suspend fun pendingChangeCount(): Int = dao.pendingChangeCount()
    suspend fun conflictCount(): Int = dao.conflictCount()

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()
    }

    private fun JSONObject.optJsonText(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is JSONObject -> value.toString()
            is org.json.JSONArray -> value.toString()
            is String -> value.takeIf { it.isNotBlank() }
            else -> value?.toString()?.takeIf { it.isNotBlank() }
        }
    }
}
