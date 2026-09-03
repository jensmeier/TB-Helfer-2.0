package de.tbhelfer.datenbank

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BaustelleDao {

    @Query("SELECT * FROM baustelle ORDER BY erstelltAmEpochMs DESC")
    fun alleBaustellen(): Flow<List<BaustelleEntity>>

    @Query("SELECT * FROM baustelle WHERE id = :id LIMIT 1")
    fun baustelleMitId(id: Long): Flow<BaustelleEntity?>

    /** Einmaliger Abruf ohne Flow (für Detail-/Einmal-Reads). */
    @Query("SELECT * FROM baustelle WHERE id = :id LIMIT 1")
    suspend fun baustelleMitIdOnce(id: Long): BaustelleEntity?

    /**
     * Für das Zusammenführen (Merge) gleicher Hauptbaustellennamen.
     * Vergleich EXAKT (nach trim()).
     */
    @Query("SELECT * FROM baustelle WHERE baustellenname = :name LIMIT 1")
    suspend fun findeNachExaktemNamen(name: String): BaustelleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun einfuegen(entity: BaustelleEntity): Long

    @Update
    suspend fun aktualisieren(entity: BaustelleEntity)

    @Delete
    suspend fun loeschen(entity: BaustelleEntity): Int
}
