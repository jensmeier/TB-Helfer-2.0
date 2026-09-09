package de.tbhelfer.datenbank

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        SyncStateEntity::class,
        SyncWorksiteContextEntity::class,
        SyncCollabEntity::class,
        SyncCollabAttachmentEntity::class,
        SyncCollabPendingChangeEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SyncDatenbank : RoomDatabase() {

    abstract fun syncDao(): SyncDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_worksite_context` (
                        `worksiteServerId` TEXT NOT NULL,
                        `name` TEXT,
                        `designation` TEXT,
                        `worksiteType` TEXT,
                        `status` TEXT,
                        `companyId` INTEGER,
                        `groupId` TEXT NOT NULL,
                        `memberRole` TEXT NOT NULL,
                        `areaScopeJson` TEXT NOT NULL,
                        `groupLabel` TEXT,
                        `leadUserId` TEXT,
                        `leadNameSnapshot` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`worksiteServerId`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_worksite_context_companyId` ON `sync_worksite_context` (`companyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_worksite_context_groupId` ON `sync_worksite_context` (`groupId`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_collab_entity` (
                        `serverId` TEXT NOT NULL,
                        `worksiteServerId` TEXT NOT NULL,
                        `groupId` TEXT,
                        `entityType` TEXT NOT NULL,
                        `clientEntityKey` TEXT,
                        `status` TEXT,
                        `areaScopeJson` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `entityVersion` INTEGER NOT NULL,
                        `changedByName` TEXT,
                        `createdAtText` TEXT,
                        `updatedAtText` TEXT,
                        `isDeleted` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`serverId`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_entity_worksiteServerId_entityType` ON `sync_collab_entity` (`worksiteServerId`, `entityType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_entity_groupId` ON `sync_collab_entity` (`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_entity_clientEntityKey` ON `sync_collab_entity` (`clientEntityKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_entity_isDeleted` ON `sync_collab_entity` (`isDeleted`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_collab_attachment` (
                        `serverId` TEXT NOT NULL,
                        `worksiteServerId` TEXT NOT NULL,
                        `parentEntityType` TEXT NOT NULL,
                        `parentEntityId` TEXT NOT NULL,
                        `filename` TEXT,
                        `mimeType` TEXT,
                        `sizeBytes` INTEGER NOT NULL,
                        `sha256` TEXT,
                        `localPath` TEXT,
                        `isDownloaded` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`serverId`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_attachment_worksiteServerId` ON `sync_collab_attachment` (`worksiteServerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_attachment_parentEntityId` ON `sync_collab_attachment` (`parentEntityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_attachment_isDownloaded` ON `sync_collab_attachment` (`isDownloaded`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_attachment_isDeleted` ON `sync_collab_attachment` (`isDeleted`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_collab_pending` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `clientChangeId` TEXT NOT NULL,
                        `worksiteServerId` TEXT NOT NULL,
                        `groupId` TEXT,
                        `entityType` TEXT NOT NULL,
                        `serverId` TEXT,
                        `clientEntityKey` TEXT,
                        `baseVersion` INTEGER,
                        `operation` TEXT NOT NULL,
                        `status` TEXT,
                        `areaScopeJson` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `lastError` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_pending_worksiteServerId_state_createdAtEpochMs` ON `sync_collab_pending` (`worksiteServerId`, `state`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_collab_pending_entityType_serverId` ON `sync_collab_pending` (`entityType`, `serverId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_collab_pending_clientChangeId` ON `sync_collab_pending` (`clientChangeId`)")
            }
        }

        fun erstellen(context: Context): SyncDatenbank = Room.databaseBuilder(
            context.applicationContext,
            SyncDatenbank::class.java,
            "tbhelfer-sync.db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
