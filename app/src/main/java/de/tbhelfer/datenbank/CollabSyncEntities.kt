package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokaler Cache für die Baustellen-Zusammenarbeit.
 *
 * BTB bleibt gruppenbezogen. Die Baustellenstruktur ist dagegen gemeinsame Baustellendaten:
 * Unterbaustellen und deren fachlicher Baum werden für alle Baustellenmitglieder gespiegelt.
 * Mängel/Abnahmen verwenden weiterhin ihre bestehenden Bereichs-/Rechteregeln.
 */
@Entity(
    tableName = "sync_worksite_context",
    indices = [Index(value = ["companyId"]), Index(value = ["groupId"])]
)
data class SyncWorksiteContextEntity(
    @PrimaryKey val worksiteServerId: String,
    val name: String? = null,
    val designation: String? = null,
    val worksiteType: String? = null,
    val status: String? = null,
    val companyId: Long? = null,
    val groupId: String,
    val memberRole: String,
    val areaScopeJson: String = "{}",
    val groupLabel: String? = null,
    val leadUserId: String? = null,
    val leadNameSnapshot: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Rein lokale Zuordnung: welche lokale Hauptbaustelle entspricht welcher Server-Baustelle.
 *
 * Die Tabelle ist absichtlich vom Serverkontext getrennt, weil der Serverkontext bei jedem Sync
 * neu geladen wird. Dadurch bleibt die Geräte-Zuordnung stabil und wird niemals versehentlich
 * zum Server hochgeladen.
 */
@Entity(
    tableName = "sync_worksite_binding",
    indices = [Index(value = ["worksiteServerId"], unique = true)]
)
data class SyncWorksiteBindingEntity(
    @PrimaryKey val localRootKnotenId: Long,
    val worksiteServerId: String,
    val boundAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Stabile geräteübergreifende Identität eines lokalen Unterbaustellen-Knotens.
 *
 * Lokale Room-IDs dürfen zwischen Poco/Pixel verschieden sein. Der clientNodeKey bleibt dagegen
 * gleich und ist damit die einzige technische Identität für Parent/Child-Zuordnung und Updates.
 * Die Hauptbaustelle selbst wird bereits über SyncWorksiteBindingEntity gebunden und braucht hier
 * keinen zusätzlichen Knoten.
 */
@Entity(
    tableName = "sync_worksite_node_binding",
    indices = [
        Index(value = ["localRootKnotenId"]),
        Index(value = ["worksiteServerId", "clientNodeKey"], unique = true)
    ]
)
data class SyncWorksiteNodeBindingEntity(
    @PrimaryKey val localKnotenId: Long,
    val localRootKnotenId: Long,
    val worksiteServerId: String,
    val clientNodeKey: String,
    val boundAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_collab_entity",
    indices = [
        Index(value = ["worksiteServerId", "entityType"]),
        Index(value = ["groupId"]),
        Index(value = ["clientEntityKey"]),
        Index(value = ["isDeleted"])
    ]
)
data class SyncCollabEntity(
    @PrimaryKey val serverId: String,
    val worksiteServerId: String,
    val groupId: String? = null,
    val entityType: String,
    val clientEntityKey: String? = null,
    val status: String? = null,
    val areaScopeJson: String = "{}",
    val payloadJson: String = "{}",
    val entityVersion: Long = 0,
    val changedByName: String? = null,
    val createdAtText: String? = null,
    val updatedAtText: String? = null,
    val isDeleted: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_collab_attachment",
    indices = [
        Index(value = ["worksiteServerId"]),
        Index(value = ["parentEntityId"]),
        Index(value = ["isDownloaded"]),
        Index(value = ["isDeleted"])
    ]
)
data class SyncCollabAttachmentEntity(
    @PrimaryKey val serverId: String,
    val worksiteServerId: String,
    val parentEntityType: String,
    val parentEntityId: String,
    val filename: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val sha256: String? = null,
    val localPath: String? = null,
    val isDownloaded: Boolean = false,
    val isDeleted: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_collab_pending",
    indices = [
        Index(value = ["worksiteServerId", "state", "createdAtEpochMs"]),
        Index(value = ["entityType", "serverId"]),
        Index(value = ["clientChangeId"], unique = true)
    ]
)
data class SyncCollabPendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientChangeId: String,
    val worksiteServerId: String,
    val groupId: String? = null,
    val entityType: String,
    val serverId: String? = null,
    val clientEntityKey: String? = null,
    val baseVersion: Long? = null,
    val operation: String,
    val status: String? = null,
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
        const val STATE_CONFLICT = "CONFLICT"
        const val STATE_FAILED = "FAILED"

        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"

        const val TYPE_BTB = "BTB"
        const val TYPE_DEFECT = "DEFECT"
        const val TYPE_ACCEPTANCE = "ACCEPTANCE"
        const val TYPE_WORKSITE_NODE = "WORKSITE_NODE"
    }
}
