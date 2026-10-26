package de.tbhelfer.datenbank

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Lokale ID-Brücke zwischen dem Baustellenbaum und stabilen Server-Knoten-Keys. */
@Dao
interface WorksiteNodeSyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SyncWorksiteNodeBindingEntity)

    @Query("SELECT * FROM sync_worksite_node_binding WHERE localKnotenId = :localKnotenId LIMIT 1")
    suspend fun forLocalNode(localKnotenId: Long): SyncWorksiteNodeBindingEntity?

    @Query("SELECT * FROM sync_worksite_node_binding WHERE worksiteServerId = :worksiteServerId AND clientNodeKey = :clientNodeKey LIMIT 1")
    suspend fun forClientKey(worksiteServerId: String, clientNodeKey: String): SyncWorksiteNodeBindingEntity?

    @Query("SELECT * FROM sync_worksite_node_binding WHERE worksiteServerId = :worksiteServerId ORDER BY boundAtEpochMs, localKnotenId")
    suspend fun forWorksite(worksiteServerId: String): List<SyncWorksiteNodeBindingEntity>

    @Query("UPDATE sync_worksite_node_binding SET worksiteServerId = :serverId WHERE localRootKnotenId = :rootId AND worksiteServerId = :localNamespace")
    suspend fun bindLocalKeys(rootId: Long, localNamespace: String, serverId: String)

    @Query("DELETE FROM sync_worksite_node_binding WHERE localKnotenId = :localKnotenId")
    suspend fun deleteForLocalNode(localKnotenId: Long)

    @Query("DELETE FROM sync_worksite_node_binding WHERE worksiteServerId = :worksiteServerId AND clientNodeKey = :clientNodeKey")
    suspend fun deleteForClientKey(worksiteServerId: String, clientNodeKey: String)

    @Query("DELETE FROM sync_worksite_node_binding WHERE worksiteServerId = :worksiteServerId")
    suspend fun deleteForWorksite(worksiteServerId: String)
}

