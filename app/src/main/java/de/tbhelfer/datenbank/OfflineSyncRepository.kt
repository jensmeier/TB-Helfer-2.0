package de.tbhelfer.datenbank

import androidx.room.withTransaction
import de.tbhelfer.server.SyncCatalogApi

/**
 * Lokale Offline-Schicht zwischen Server-API und UI.
 *
 * Phase 1 speichert den vollständigen Strecken-/Bahnhofskatalog. Später werden hier auch
 * selektive Abschnitts-/Objekt-Downloads, Upload-Queue, Tombstones und Konflikte gebündelt.
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

        // /sync/catalog ist ein Vollbestand. Deshalb Cache atomar ersetzen: Was auf dem
        // Server nicht mehr im Katalog steht, soll auch lokal nicht als verfügbar erscheinen.
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
