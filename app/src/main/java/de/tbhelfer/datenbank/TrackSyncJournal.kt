package de.tbhelfer.datenbank

import androidx.room.*

/** Same database/transaction as the imported geometry and KM sections. */
@Entity(tableName = "track_sync_journal", primaryKeys = ["owner", "entryKey"])
data class TrackSyncJournal(val owner: String, val entryKey: String, val payload: String)

@Dao
interface TrackSyncJournalDao {
    @Query("SELECT payload FROM track_sync_journal WHERE owner=:owner AND entryKey=:key")
    suspend fun load(owner: String, key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: TrackSyncJournal)
}
