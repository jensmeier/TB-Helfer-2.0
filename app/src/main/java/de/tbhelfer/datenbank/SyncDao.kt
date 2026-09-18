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

    // UPLOADING wird absichtlich erneut gelesen. Wenn App/Netz/Sitzung nach dem Zustandswechsel
    // abbricht, darf die Änderung nicht dauerhaft aus der Queue verschwinden. Der Server wendet
    // Wiederholungen über client_change_id idempotent an.
    @Query("SELECT * FROM sync_pending_change WHERE state IN ('PENDING', 'FAILED', 'UPLOADING') ORDER BY createdAtEpochMs, id")
    suspend fun pendingChanges(): List<SyncPendingChangeEntity>

    @Query("SELECT * FROM sync_pending_change WHERE objectKind = :objectKind AND serverId = :serverId AND state IN ('PENDING','FAILED','CONFLICT') ORDER BY id DESC LIMIT 1")
    suspend fun pendingForEntity(objectKind: String, serverId: String): SyncPendingChangeEntity?

    @Query("SELECT COUNT(*) FROM sync_pending_change WHERE state IN ('PENDING', 'FAILED', 'UPLOADING')")
    suspend fun pendingChangeCount(): Int

    @Query("SELECT * FROM sync_pending_change WHERE state = 'CONFLICT' ORDER BY updatedAtEpochMs DESC")
    suspend fun conflicts(): List<SyncPendingChangeEntity>

    @Query("SELECT COUNT(*) FROM sync_pending_change WHERE state = 'CONFLICT'")
    suspend fun conflictCount(): Int

    @Query("SELECT * FROM sync_pending_change WHERE id = :id LIMIT 1")
    suspend fun pendingChange(id: Long): SyncPendingChangeEntity?

    @Query("UPDATE sync_pending_change SET state = :state, attempts = :attempts, lastError = :lastError, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateChangeState(
        id: Long,
        state: String,
        attempts: Int,
        lastError: String?,
        updatedAtEpochMs: Long = System.currentTimeMillis()
    )

    @Query("UPDATE sync_pending_change SET baseVersion = :baseVersion, state = 'PENDING', attempts = 0, lastError = NULL, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun rebaseConflict(id: Long, baseVersion: Long, updatedAtEpochMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_pending_change WHERE id = :id")
    suspend fun deletePendingChange(id: Long)

    @Query("SELECT * FROM sync_state WHERE `key` = :key LIMIT 1")
    suspend fun state(key: String): SyncStateEntity?

    @Query("DELETE FROM sync_route")
    suspend fun clearRoutes()

    @Query("DELETE FROM sync_station")
    suspend fun clearStations()

    // ---------------------------------------------------------------------
    // Baustellen-Zusammenarbeit ab Server 0.7.0
    // ---------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorksiteContexts(items: List<SyncWorksiteContextEntity>)

    @Query("DELETE FROM sync_worksite_context")
    suspend fun clearWorksiteContexts()

    @Query("SELECT * FROM sync_worksite_context ORDER BY COALESCE(name, designation, worksiteServerId) COLLATE NOCASE")
    suspend fun worksiteContexts(): List<SyncWorksiteContextEntity>

    @Query("SELECT * FROM sync_worksite_context WHERE worksiteServerId = :worksiteServerId LIMIT 1")
    suspend fun worksiteContext(worksiteServerId: String): SyncWorksiteContextEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorksiteBinding(item: SyncWorksiteBindingEntity)

    @Query("SELECT * FROM sync_worksite_binding WHERE localRootKnotenId = :localRootKnotenId LIMIT 1")
    suspend fun worksiteBindingForLocalRoot(localRootKnotenId: Long): SyncWorksiteBindingEntity?

    @Query("SELECT * FROM sync_worksite_binding WHERE worksiteServerId = :worksiteServerId LIMIT 1")
    suspend fun worksiteBindingForServer(worksiteServerId: String): SyncWorksiteBindingEntity?

    @Query("SELECT * FROM sync_worksite_binding ORDER BY boundAtEpochMs DESC")
    suspend fun worksiteBindings(): List<SyncWorksiteBindingEntity>

    @Query("DELETE FROM sync_worksite_binding WHERE localRootKnotenId = :localRootKnotenId")
    suspend fun deleteWorksiteBindingForLocalRoot(localRootKnotenId: Long)

    @Query("DELETE FROM sync_worksite_binding WHERE worksiteServerId = :worksiteServerId")
    suspend fun deleteWorksiteBindingForServer(worksiteServerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollabEntities(items: List<SyncCollabEntity>)

    @Query("SELECT * FROM sync_collab_entity WHERE serverId = :serverId LIMIT 1")
    suspend fun collabEntity(serverId: String): SyncCollabEntity?

    @Query("SELECT * FROM sync_collab_entity WHERE worksiteServerId = :worksiteServerId AND entityType = :entityType AND isDeleted = 0 ORDER BY updatedAtEpochMs DESC")
    suspend fun collabEntities(worksiteServerId: String, entityType: String): List<SyncCollabEntity>

    @Query("SELECT * FROM sync_collab_entity WHERE worksiteServerId = :worksiteServerId AND entityType = :entityType AND isDeleted = 1 ORDER BY updatedAtEpochMs DESC")
    suspend fun deletedCollabEntities(worksiteServerId: String, entityType: String): List<SyncCollabEntity>

    @Query("UPDATE sync_collab_entity SET isDeleted = 1, entityVersion = :entityVersion, updatedAtEpochMs = :nowEpochMs WHERE serverId = :serverId")
    suspend fun markCollabDeleted(serverId: String, entityVersion: Long, nowEpochMs: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollabAttachments(items: List<SyncCollabAttachmentEntity>)

    @Query("SELECT * FROM sync_collab_attachment WHERE serverId = :serverId LIMIT 1")
    suspend fun collabAttachment(serverId: String): SyncCollabAttachmentEntity?

    @Query("SELECT * FROM sync_collab_attachment WHERE parentEntityId = :parentEntityId AND isDeleted = 0 ORDER BY updatedAtEpochMs")
    suspend fun collabAttachmentsForEntity(parentEntityId: String): List<SyncCollabAttachmentEntity>

    @Query("SELECT * FROM sync_collab_attachment WHERE worksiteServerId = :worksiteServerId AND parentEntityType IN ('DEFECT','ACCEPTANCE') AND isDeleted = 0 ORDER BY updatedAtEpochMs")
    suspend fun collabFachAttachments(worksiteServerId: String): List<SyncCollabAttachmentEntity>

    @Query("UPDATE sync_collab_attachment SET isDeleted = 1, updatedAtEpochMs = :nowEpochMs WHERE serverId = :serverId")
    suspend fun markCollabAttachmentDeleted(
        serverId: String,
        nowEpochMs: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM sync_collab_attachment WHERE worksiteServerId = :worksiteServerId AND isDeleted = 0 AND isDownloaded = 0 ORDER BY updatedAtEpochMs")
    suspend fun collabAttachmentsNotDownloaded(worksiteServerId: String): List<SyncCollabAttachmentEntity>

    @Query("UPDATE sync_collab_attachment SET localPath = :localPath, mimeType = COALESCE(:mimeType, mimeType), isDownloaded = 1, updatedAtEpochMs = :nowEpochMs WHERE serverId = :serverId")
    suspend fun markCollabAttachmentDownloaded(
        serverId: String,
        localPath: String,
        mimeType: String?,
        nowEpochMs: Long = System.currentTimeMillis()
    )

    @Insert
    suspend fun enqueueCollabChange(item: SyncCollabPendingChangeEntity): Long

    // Wie beim allgemeinen Sync werden unterbrochene UPLOADING-Einträge beim nächsten Lauf
    // erneut versucht. client_change_id schützt den Server vor einer Doppelanwendung.
    @Query("SELECT * FROM sync_collab_pending WHERE worksiteServerId = :worksiteServerId AND state IN ('PENDING','FAILED','UPLOADING') ORDER BY createdAtEpochMs, id")
    suspend fun collabPendingChanges(worksiteServerId: String): List<SyncCollabPendingChangeEntity>

    @Query("SELECT * FROM sync_collab_pending WHERE worksiteServerId = :worksiteServerId AND entityType = :entityType AND clientEntityKey = :clientEntityKey AND state IN ('PENDING','FAILED','CONFLICT') ORDER BY id DESC LIMIT 1")
    suspend fun collabPendingForClientKey(
        worksiteServerId: String,
        entityType: String,
        clientEntityKey: String
    ): SyncCollabPendingChangeEntity?

    @Query("SELECT * FROM sync_collab_pending WHERE worksiteServerId = :worksiteServerId AND entityType = :entityType AND clientEntityKey = :clientEntityKey AND state IN ('PENDING','FAILED','CONFLICT','UPLOADING') ORDER BY id DESC LIMIT 1")
    suspend fun collabPendingAnyForClientKey(
        worksiteServerId: String,
        entityType: String,
        clientEntityKey: String
    ): SyncCollabPendingChangeEntity?

    @Query("SELECT COUNT(*) FROM sync_collab_pending WHERE state IN ('PENDING','FAILED','UPLOADING')")
    suspend fun collabPendingCount(): Int

    @Query("SELECT COUNT(*) FROM sync_collab_pending WHERE state = 'CONFLICT'")
    suspend fun collabConflictCount(): Int

    @Query("SELECT * FROM sync_collab_pending WHERE state = 'CONFLICT' ORDER BY updatedAtEpochMs DESC")
    suspend fun collabConflicts(): List<SyncCollabPendingChangeEntity>

    @Query("UPDATE sync_collab_pending SET state = :state, attempts = :attempts, lastError = :lastError, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateCollabChangeState(
        id: Long,
        state: String,
        attempts: Int,
        lastError: String?,
        updatedAtEpochMs: Long = System.currentTimeMillis()
    )

    @Query("UPDATE sync_collab_pending SET serverId = COALESCE(:serverId, serverId), baseVersion = COALESCE(:baseVersion, baseVersion), groupId = COALESCE(:groupId, groupId), status = :status, areaScopeJson = :areaScopeJson, payloadJson = :payloadJson, operation = 'UPSERT', state = 'PENDING', attempts = 0, lastError = NULL, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun replaceCollabPendingUpsert(
        id: Long,
        serverId: String?,
        baseVersion: Long?,
        groupId: String?,
        status: String?,
        areaScopeJson: String,
        payloadJson: String,
        updatedAtEpochMs: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM sync_collab_pending WHERE id = :id")
    suspend fun deleteCollabPendingChange(id: Long)
}
