package de.tbhelfer.datenbank

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BaustellenKnotenDao {

    @Query("SELECT * FROM baustellen_knoten WHERE typ = 'HAUPT' AND parentId IS NULL ORDER BY erstelltAmEpochMs DESC")
    fun alleHauptbaustellen(): Flow<List<BaustellenKnotenEntity>>

    @Query("SELECT * FROM baustellen_knoten WHERE typ = 'HAUPT' AND parentId IS NULL")
    suspend fun alleHauptbaustellenSofort(): List<BaustellenKnotenEntity>

    @Query("SELECT * FROM baustellen_knoten WHERE id = :id LIMIT 1")
    suspend fun laden(id: Long): BaustellenKnotenEntity?

    @Query("SELECT * FROM baustellen_knoten WHERE id = :id LIMIT 1")
    fun ladenFlow(id: Long): Flow<BaustellenKnotenEntity?>

    /**
     * Merge nur bei EXAKTEM Namen (Groß/Klein zählt!).
     */
    @Query("SELECT * FROM baustellen_knoten WHERE hauptbaustellenName = :name AND typ = 'HAUPT' AND parentId IS NULL LIMIT 1")
    suspend fun hauptNachExaktemNamen(name: String): BaustellenKnotenEntity?

    @Query("SELECT * FROM baustellen_knoten WHERE parentId = :parentId ORDER BY erstelltAmEpochMs DESC")
    fun kinderVon(parentId: Long): Flow<List<BaustellenKnotenEntity>>

    @Query("SELECT * FROM baustellen_knoten WHERE parentId = :parentId ORDER BY erstelltAmEpochMs DESC")
    suspend fun kinderVonSofort(parentId: Long): List<BaustellenKnotenEntity>

    @Query("SELECT * FROM baustellen_knoten")
    fun alleKnotenFlow(): Flow<List<BaustellenKnotenEntity>>

    @Query("SELECT * FROM baustellen_knoten WHERE rootId = :rootId")
    fun alleKnotenZuRootFlow(rootId: Long): Flow<List<BaustellenKnotenEntity>>

    @Query("SELECT * FROM baustellen_knoten WHERE rootId = :rootId")
    suspend fun alleKnotenZuRootSofort(rootId: Long): List<BaustellenKnotenEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun einfuegen(knoten: BaustellenKnotenEntity): Long

    @Update
    suspend fun aktualisieren(knoten: BaustellenKnotenEntity)

    /** Pfad nach oben (inklusive Startknoten) - für Breadcrumbs. */
    @Query(
        """
        WITH RECURSIVE pfad(id, parentId) AS (
            SELECT id, parentId FROM baustellen_knoten WHERE id = :startId
            UNION ALL
            SELECT k.id, k.parentId FROM baustellen_knoten k
            JOIN pfad p ON k.id = p.parentId
        )
        SELECT b.* FROM baustellen_knoten b
        JOIN pfad p ON b.id = p.id
        """
    )
    suspend fun pfadBisRoot(startId: Long): List<BaustellenKnotenEntity>

    /** Löscht einen ganzen Teilbaum (Knoten + alle Kinder/Unterkinder). */
    @Query(
        """
        WITH RECURSIVE t(id) AS (
            SELECT :knotenId
            UNION ALL
            SELECT k.id FROM baustellen_knoten k
            JOIN t ON k.parentId = t.id
        )
        DELETE FROM baustellen_knoten WHERE id IN (SELECT id FROM t)
        """
    )
    suspend fun loescheTeilbaum(knotenId: Long)
}
