package de.tbhelfer.collab

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Separate lokale Datenbank fuer firmengebundene Baustellen-Zusammenarbeit.
 *
 * BTB bleibt gruppenbezogen (verantwortlicher TB + seine Helfer).
 * Maengel und Abnahmen werden baustellenweit synchronisiert; die Serverrechte
 * begrenzen Helfer auf ihren zugewiesenen Arbeitsbereich.
 */
@Entity(
    tableName = "collab_worksite",
    indices = [Index(value = ["companyId"]), Index(value = ["groupId"])]
)
data class CollabWorksiteEntity(
    @PrimaryKey val serverId: String,
    val name: String,
    val designation: String? = null,
    val worksiteType: String? = null,
    val status: String? = null,
    val companyId: Long? = null,
    val groupId: String,
    val groupLabel: String? = null,
    val leadUserId: String? = null,
    val leadName: String? = null,
    val memberRole: String,
    val areaScopeJson: String = "{}",
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collab_binding",
    indices = [Index(value = ["serverWorksiteId"], unique = true)]
)
data class CollabBindingEntity(
    @PrimaryKey val localBaustelleId: Long,
    val serverWorksiteId: String,
    val boundAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collab_btb",
    indices = [
        Index(value = ["worksiteServerId", "groupId"]),
        Index(value = ["clientKey"], unique = false),
        Index(value = ["isDeleted"])
    ]
)
data class CollabBtbEntity(
    @PrimaryKey val serverId: String,
    val worksiteServerId: String,
    val groupId: String,
    val clientKey: String,
    val payloadJson: String,
    val entityVersion: Long,
    val isDeleted: Boolean = false,
    val changedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collab_defect",
    indices = [
        Index(value = ["worksiteServerId"]),
        Index(value = ["clientKey"]),
        Index(value = ["status"]),
        Index(value = ["isDeleted"])
    ]
)
data class CollabDefectEntity(
    @PrimaryKey val serverId: String,
    val worksiteServerId: String,
    val clientKey: String,
    val areaScopeJson: String = "{}",
    val status: String = "OFFEN",
    val payloadJson: String,
    val entityVersion: Long,
    val isDeleted: Boolean = false,
    val changedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collab_acceptance",
    indices = [
        Index(value = ["worksiteServerId"]),
        Index(value = ["clientKey"]),
        Index(value = ["status"]),
        Index(value = ["isDeleted"])
    ]
)
data class CollabAcceptanceEntity(
    @PrimaryKey val serverId: String,
    val worksiteServerId: String,
    val clientKey: String,
    val areaScopeJson: String = "{}",
    val status: String = "ENTWURF",
    val payloadJson: String,
    val entityVersion: Long,
    val isDeleted: Boolean = false,
    val changedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collab_attachment",
    indices = [Index(value = ["entityType", "entityServerId"]), Index(value = ["worksiteServerId"])]
)
data class CollabAttachmentEntity(
    @PrimaryKey val serverId: String,
    val worksiteServerId: String,
    val entityType: String,
    val entityServerId: String,
    val filename: String? = null,
    val mimeType: String? = null,
    val sha256: String? = null,
    val sizeBytes: Long = 0,
    val localPath: String? = null,
    val downloaded: Boolean = false,
    val changedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collab_pending_mutation",
    indices = [
        Index(value = ["worksiteServerId", "state", "createdAtEpochMs"]),
        Index(value = ["entityType", "entityServerId"])
    ]
)
data class CollabPendingMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientChangeId: String,
    val worksiteServerId: String,
    val entityType: String,
    val entityServerId: String? = null,
    val clientEntityKey: String,
    val baseVersion: Long? = null,
    val operation: String = OP_UPSERT,
    val groupId: String? = null,
    val statusValue: String? = null,
    val areaScopeJson: String = "{}",
    val payloadJson: String = "{}",
    val state: String = STATE_PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_UPLOADING = "UPLOADING"
        const val STATE_FAILED = "FAILED"
        const val STATE_CONFLICT = "CONFLICT"
        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"
        const val TYPE_BTB = "BTB"
        const val TYPE_DEFECT = "DEFECT"
        const val TYPE_ACCEPTANCE = "ACCEPTANCE"
    }
}

@Entity(tableName = "collab_sync_state", primaryKeys = ["worksiteServerId"])
data class CollabSyncStateEntity(
    val worksiteServerId: String,
    val lastSequenceNo: Long = 0,
    val lastSuccessfulSyncEpochMs: Long? = null,
    val lastError: String? = null
)
