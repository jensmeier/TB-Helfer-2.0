package de.tbhelfer.datenbank

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutes(items: List<SyncRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStations(items: List<SyncStationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSections(items: List<SyncSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertObjects(items: List<SyncObjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstones(items: List<SyncTombstoneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(item: SyncStateEntity)

    @Insert
    suspend fun enqueueChange(item: SyncPendingChangeEntity): Long

    @Query("SELECT * FROM sync_route WHERE isDeleted = 0 ORDER BY routeNumber COLLATE NOCASE")
    suspend fun routes(): List<SyncRouteEntity>

    @Query("SELECT * FROM sync_station WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE")
    suspend fun stations(): List<SyncStationEntity>

    @Query("SELECT * FROM sync_route WHERE serverId = :serverId LIMIT 1")
    suspend fun route(serverId: String): SyncRouteEntity?

    @Query("SELECT * FROM sync_station WHERE serverId = :serverId LIMIT 1")
    suspend fun station(serverId: String): SyncStationEntity?

    @Query("SELECT * FROM sync_section WHERE serverId = :serverId LIMIT 1")
    suspend fun section(serverId: String): SyncSectionEntity?

    @Query("SELECT * FROM sync_object WHERE serverId = :serverId LIMIT 1")
    suspend fun objectById(serverId: String): SyncObjectEntity?

    @Query("SELECT * FROM sync_section WHERE routeServerId = :routeServerId AND isDeleted = 0 ORDER BY kmMinM, kmMaxM, designation")
    suspend fun sectionsForRoute(routeServerId: String): List<SyncSectionEntity>

    @Query("SELECT * FROM sync_object WHERE routeServerId = :routeServerId AND isDeleted = 0 ORDER BY objectType, designation")
    suspend fun objectsForRoute(routeServerId: String): List<SyncObjectEntity>

    @Query("SELECT * FROM sync_object WHERE stationServerId = :stationServerId AND isDeleted = 0 ORDER BY objectType, designation")
    suspend fun objectsForStation(stationServerId: String): List<SyncObjectEntity>

    @Query("UPDATE sync_route SET isDeleted = 1, entityVersion = :entityVersion, updatedAtEpochMs = :nowEpochMs WHERE serverId = :serverId")
    suspend fun markRouteDeleted(serverId: String, entityVersion: Long, nowEpochMs: Long = System.currentTimeMillis())

    @Query("UPDATE sync_station SET isDeleted = 1, entityVersion = :entityVersion, updatedAtEpochMs = :nowEpochMs WHERE serverId = :serverId")
    suspend fun markStationDeleted(serverId: String, entityVersion: Long, nowEpochMs: Long = System.currentTimeMillis())

    @Query("UPDATE sync_section SET isDeleted = 1, entityVersion = :entityVersion, updatedAtEpochMs = :nowEpochMs WHERE serverId = :serverId")
    suspend fun markSectionDeleted(serverId: String, entityVersion: Long, nowEpochMs: Long = System.currentTimeMillis())

    @Query("UPDATE sync_object SET isDeleted = 1, entityVersion = :entityVersion, updatedAtEpochMs = :nowEpochMs WHERE serverId = :serverId")
    suspend fun markObjectDeleted(serverId: String, entityVersion: Long, nowEpochMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_section WHERE routeServerId = :routeServerId")
    suspend fun clearSectionsForRoute(routeServerId: String)

    @Query("DELETE FROM sync_object WHERE stationServerId = :stationServerId")
    suspend fun clearObjectsForStation(stationServerId: String)

    @Query("SELECT COUNT(*) FROM sync_section WHERE routeServerId = :routeServerId AND isDeleted = 0")
    suspend fun sectionCountForRoute(routeServerId: String): Int

    @Query("SELECT COUNT(*) FROM sync_object WHERE stationServerId = :stationServerId AND isDeleted = 0")
    suspend fun objectCountForStation(stationServerId: String): Int

    @Query("SELECT * FROM sync_pending_change WHERE state IN ('PENDING', 'FAILED') ORDER BY createdAtEpochMs, id")
    suspend fun pendingChanges(): List<SyncPendingChangeEntity>

    @Query("SELECT COUNT(*) FROM sync_pending_change WHERE state IN ('PENDING', 'FAILED')")
    suspend fun pendingChangeCount(): Int

    @Query("SELECT * FROM sync_pending_change WHERE state = 'CONFLICT' ORDER BY updatedAtEpochMs DESC")
    suspend fun conflicts(): List<SyncPendingChangeEntity>

    @Query("SELECT COUNT(*) FROM sync_pending_change WHERE state = 'CONFLICT'")
    suspend fun conflictCount(): Int

    @Query("UPDATE sync_pending_change SET state = :state, attempts = :attempts, lastError = :lastError, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateChangeState(
        id: Long,
        state: String,
        attempts: Int,
        lastError: String?,
        updatedAtEpochMs: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM sync_pending_change WHERE id = :id")
    suspend fun deletePendingChange(id: Long)

    @Query("SELECT * FROM sync_state WHERE `key` = :key LIMIT 1")
    suspend fun state(key: String): SyncStateEntity?

    @Query("DELETE FROM sync_route")
    suspend fun clearRoutes()

    @Query("DELETE FROM sync_station")
    suspend fun clearStations()
}
