package de.tbhelfer.collab

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Eigene DB fuer Baustellen-Zusammenarbeit. Damit bleiben bestehende Baustellen-,
 * Vermessungs- und der bereits getestete NAS-Katalog-Sync voneinander getrennt.
 */
@Database(
    entities = [
        CollabWorksiteEntity::class,
        CollabBindingEntity::class,
        CollabBtbEntity::class,
        CollabDefectEntity::class,
        CollabAcceptanceEntity::class,
        CollabAttachmentEntity::class,
        CollabPendingMutationEntity::class,
        CollabSyncStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CollabDatenbank : RoomDatabase() {
    abstract fun dao(): CollabDao

    companion object {
        fun erstellen(context: Context): CollabDatenbank = Room.databaseBuilder(
            context.applicationContext,
            CollabDatenbank::class.java,
            "tbhelfer-collab.db"
        ).build()
    }
}
