package de.tbhelfer.datenbank

import androidx.room.withTransaction
import de.tbhelfer.server.SyncCatalogApi
import de.tbhelfer.server.SyncDetailApi

/**
 * Lokale Offline-Schicht zwischen Server-API und UI.
 *
 * Der Katalog sowie gezielt ausgewählte Streckenabschnitte und Bahnhofobjekte werden getrennt
 * von der bestehenden fachlichen Datenbank gespeichert. Upload-Queue, Tombstones und Konflikte
 * können dadurch schrittweise ergänzt werden, ohne vorhandene Baustellendaten zu gefährden.
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

    suspend fun saveCatalog(catalog: SyncCatalogApi.Catalog, nowEpochMs: Long = System.currentTimeMillis()) {
        val routes = catalog.routes.map { route ->
            SyncRouteEntity(
                serverId = route.id,
                routeNumber = route.routeNumber,
                name = route.name,
                description = route.description,
                sectionCount = route.sectionCount,
                objectCount = route.objectCount,
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
                downloadedAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        }

        database.withTransaction {
            dao.clearRoutes()
            dao.clearStations()
            if (routes.isNotEmpty()) dao.upsertRoutes(routes)
            if (stations.isNotEmpty()) dao.upsertStations(stations)
            dao.upsertState(
                SyncStateEntity(
                    key = SyncStateEntity.KEY_LAST_CATALOG_SYNC,
                    longValue = nowEpochMs
                )
            )
            dao.upsertState(
                SyncStateEntity(
                    key = SyncStateEntity.KEY_LAST_SUCCESSFUL_SYNC,
                    longValue = nowEpochMs
                )
            )
            dao.upsertState(
                SyncStateEntity(
                    key = SyncStateEntity.KEY_SERVER_VERSION,
                    textValue = catalog.serverVersion
                )
            )
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
                objectCount = route.objectCount
            )
        }
        val stations = dao.stations().map { station ->
            SyncCatalogApi.StationItem(
                id = station.serverId,
                stationCode = station.stationCode,
                name = station.name,
                shortName = station.shortName,
                description = station.description,
                objectCount = station.objectCount
            )
        }
        return CachedCatalog(
            catalog = SyncCatalogApi.Catalog(
                serverVersion = serverVersion,
                routes = routes,
                stations = stations
            ),
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
        database.withTransaction {
            dao.clearSectionsForRoute(routeServerId)
            if (entities.isNotEmpty()) dao.upsertSections(entities)
            dao.upsertState(
                SyncStateEntity(
                    key = SyncStateEntity.KEY_LAST_SUCCESSFUL_SYNC,
                    longValue = nowEpochMs
                )
            )
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
        database.withTransaction {
            dao.clearObjectsForStation(stationServerId)
            if (entities.isNotEmpty()) dao.upsertObjects(entities)
            dao.upsertState(
                SyncStateEntity(
                    key = SyncStateEntity.KEY_LAST_SUCCESSFUL_SYNC,
                    longValue = nowEpochMs
                )
            )
        }
    }

    suspend fun loadSectionsForRoute(routeServerId: String): List<SyncSectionEntity> =
        dao.sectionsForRoute(routeServerId)

    suspend fun loadObjectsForStation(stationServerId: String): List<SyncObjectEntity> =
        dao.objectsForStation(stationServerId)

    suspend fun sectionCountForRoute(routeServerId: String): Int =
        dao.sectionCountForRoute(routeServerId)

    suspend fun objectCountForStation(stationServerId: String): Int =
        dao.objectCountForStation(stationServerId)

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

    suspend fun pendingChangeCount(): Int = dao.pendingChangeCount()
}
