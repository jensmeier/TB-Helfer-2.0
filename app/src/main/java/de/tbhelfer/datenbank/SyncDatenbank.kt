package de.tbhelfer.datenbank

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Separate lokale Sync-Datenbank.
 *
 * Absichtlich nicht Teil von TBDatenbank: Damit kann der Server-/Offline-Sync weiterentwickelt
 * werden, ohne die bestehende Baustellen- und Vermessungsdatenbank oder deren Migrationen zu
 * gefährden. Erst nach erfolgreichem Download werden fachliche Daten kontrolliert übernommen.
 */
@Database(
    entities = [
        SyncRouteEntity::class,
        SyncStationEntity::class,
        SyncSectionEntity::class,
        SyncObjectEntity::class,
        SyncPendingChangeEntity::class,
        SyncTombstoneEntity::class,
        SyncStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SyncDatenbank : RoomDatabase() {

    abstract fun syncDao(): SyncDao

    companion object {
        fun erstellen(context: Context): SyncDatenbank = Room.databaseBuilder(
            context.applicationContext,
            SyncDatenbank::class.java,
            "tbhelfer-sync.db"
        ).build()
    }
}
