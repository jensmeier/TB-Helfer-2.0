package de.tbhelfer.collab

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CollabDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorksites(items: List<CollabWorksiteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBinding(item: CollabBindingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBtb(item: CollabBtbEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDefect(item: CollabDefectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAcceptance(item: CollabAcceptanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachment(item: CollabAttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(item: CollabSyncStateEntity)

    @Insert
    suspend fun enqueue(item: CollabPendingMutationEntity): Long

    @Query("SELECT * FROM collab_worksite ORDER BY name COLLATE NOCASE")
    suspend fun worksites(): List<CollabWorksiteEntity>

    @Query("SELECT * FROM collab_worksite WHERE serverId=:serverId LIMIT 1")
    suspend fun worksite(serverId: String): CollabWorksiteEntity?

    @Query("DELETE FROM collab_worksite")
    suspend fun clearWorksites()

    @Query("SELECT * FROM collab_binding WHERE localBaustelleId=:localBaustelleId LIMIT 1")
    suspend fun bindingForLocal(localBaustelleId: Long): CollabBindingEntity?

    @Query("SELECT * FROM collab_binding WHERE serverWorksiteId=:serverWorksiteId LIMIT 1")
    suspend fun bindingForServer(serverWorksiteId: String): CollabBindingEntity?

    @Query("SELECT * FROM collab_btb WHERE worksiteServerId=:worksiteId AND groupId=:groupId AND isDeleted=0 ORDER BY changedAtEpochMs DESC")
    suspend fun btb(worksiteId: String, groupId: String): List<CollabBtbEntity>

    @Query("SELECT * FROM collab_defect WHERE worksiteServerId=:worksiteId AND isDeleted=0 ORDER BY changedAtEpochMs DESC")
    suspend fun defects(worksiteId: String): List<CollabDefectEntity>

    @Query("SELECT * FROM collab_acceptance WHERE worksiteServerId=:worksiteId AND isDeleted=0 ORDER BY changedAtEpochMs DESC")
    suspend fun acceptances(worksiteId: String): List<CollabAcceptanceEntity>

    @Query("SELECT * FROM collab_btb WHERE serverId=:serverId LIMIT 1")
    suspend fun btbById(serverId: String): CollabBtbEntity?

    @Query("SELECT * FROM collab_defect WHERE serverId=:serverId LIMIT 1")
    suspend fun defectById(serverId: String): CollabDefectEntity?

    @Query("SELECT * FROM collab_acceptance WHERE serverId=:serverId LIMIT 1")
    suspend fun acceptanceById(serverId: String): CollabAcceptanceEntity?

    @Query("UPDATE collab_btb SET isDeleted=1,entityVersion=:version,changedAtEpochMs=:now WHERE serverId=:serverId")
    suspend fun markBtbDeleted(serverId: String, version: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE collab_defect SET isDeleted=1,entityVersion=:version,changedAtEpochMs=:now WHERE serverId=:serverId")
    suspend fun markDefectDeleted(serverId: String, version: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE collab_acceptance SET isDeleted=1,entityVersion=:version,changedAtEpochMs=:now WHERE serverId=:serverId")
    suspend fun markAcceptanceDeleted(serverId: String, version: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM collab_pending_mutation WHERE state IN ('PENDING','FAILED') ORDER BY createdAtEpochMs,id")
    suspend fun pending(): List<CollabPendingMutationEntity>

    @Query("SELECT * FROM collab_pending_mutation WHERE id=:id LIMIT 1")
    suspend fun pendingById(id: Long): CollabPendingMutationEntity?

    @Query("SELECT * FROM collab_pending_mutation WHERE worksiteServerId=:worksiteId AND entityType=:entityType AND clientEntityKey=:clientKey AND state IN ('PENDING','FAILED') ORDER BY id DESC LIMIT 1")
    suspend fun pendingForClientKey(worksiteId: String, entityType: String, clientKey: String): CollabPendingMutationEntity?

    @Query("SELECT COUNT(*) FROM collab_pending_mutation WHERE state IN ('PENDING','FAILED')")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM collab_pending_mutation WHERE state='CONFLICT'")
    suspend fun conflictCount(): Int

    @Query("UPDATE collab_pending_mutation SET state=:state,attempts=:attempts,lastError=:error,updatedAtEpochMs=:now WHERE id=:id")
    suspend fun updatePendingState(id: Long, state: String, attempts: Int, error: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE collab_pending_mutation SET entityServerId=:serverId,baseVersion=:version,state='PENDING',attempts=0,lastError=NULL,updatedAtEpochMs=:now WHERE id=:id")
    suspend fun rebasePending(id: Long, serverId: String?, version: Long?, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM collab_pending_mutation WHERE id=:id")
    suspend fun deletePending(id: Long)

    @Query("SELECT * FROM collab_sync_state WHERE worksiteServerId=:worksiteId LIMIT 1")
    suspend fun state(worksiteId: String): CollabSyncStateEntity?
}
