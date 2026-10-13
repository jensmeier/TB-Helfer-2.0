package de.tbhelfer.datenbank

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokaler NAS/Cloudflare-Cache. Die bestehenden fachlichen Tabellen bleiben davon getrennt,
 * damit Offline-Sync und Konfliktauflösung schrittweise eingeführt werden können.
 */
@Entity(
    tableName = "sync_route",
    indices = [Index(value = ["routeNumber"]), Index(value = ["isDeleted"])]
)
data class SyncRouteEntity(
    @PrimaryKey val serverId: String,
    val routeNumber: String,
    val name: String? = null,
    val description: String? = null,
    val sectionCount: Long = 0,
    val objectCount: Long = 0,
    val entityVersion: Long = 0,
    val isDeleted: Boolean = false,
    val downloadedAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_station",
    indices = [Index(value = ["stationCode"]), Index(value = ["name"]), Index(value = ["isDeleted"])]
)
data class SyncStationEntity(
    @PrimaryKey val serverId: String,
    val stationCode: String? = null,
    val name: String,
    val shortName: String? = null,
    val description: String? = null,
    val objectCount: Long = 0,
    val entityVersion: Long = 0,
    val isDeleted: Boolean = false,
    val downloadedAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_section",
    indices = [
        Index(value = ["routeServerId"]),
        Index(value = ["routeServerId", "kmMinM", "kmMaxM"]),
        Index(value = ["isDeleted"])
    ]
)
data class SyncSectionEntity(
    @PrimaryKey val serverId: String,
    val routeServerId: String,
    val designation: String? = null,
    val kmFromM: Double? = null,
    val kmToM: Double? = null,
    val kmMinM: Double? = null,
    val kmMaxM: Double? = null,
    val dataStatus: String? = null,
    val geometryJson: String? = null,
    val entityVersion: Long = 0,
    val isDeleted: Boolean = false,
    val downloadedAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_object",
    indices = [
        Index(value = ["routeServerId"]),
        Index(value = ["stationServerId"]),
        Index(value = ["objectType", "designation"]),
        Index(value = ["isDeleted"])
    ]
)
data class SyncObjectEntity(
    @PrimaryKey val serverId: String,
    val routeServerId: String? = null,
    val stationServerId: String? = null,
    val objectType: String,
    val designation: String? = null,
    val kmM: Double? = null,
    val dataStatus: String? = null,
    val geometryJson: String? = null,
    val propertiesJson: String? = null,
    val entityVersion: Long = 0,
    val isDeleted: Boolean = false,
    val downloadedAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_pending_change",
    indices = [
        Index(value = ["state", "createdAtEpochMs"]),
        Index(value = ["objectKind", "serverId"])
    ]
)
data class SyncPendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objectKind: String,
    val serverId: String? = null,
    val baseVersion: Long? = null,
    val operation: String,
    val payloadJson: String,
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

        const val KIND_ROUTE = "ROUTE"
        const val KIND_STATION = "STATION"
        const val KIND_SECTION = "SECTION"
        const val KIND_OBJECT = "OBJECT"
    }
}

@Entity(
    tableName = "sync_tombstone",
    primaryKeys = ["objectKind", "serverId"],
    indices = [Index(value = ["deletedAtEpochMs"])]
)
data class SyncTombstoneEntity(
    val objectKind: String,
    val serverId: String,
    val entityVersion: Long,
    val deletedAtEpochMs: Long
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val longValue: Long? = null,
    val textValue: String? = null
) {
    companion object {
        const val KEY_LAST_CATALOG_SYNC = "last_catalog_sync"
        const val KEY_LAST_SUCCESSFUL_SYNC = "last_successful_sync"
        const val KEY_LAST_CHANGE_SEQUENCE = "last_change_sequence"
        const val KEY_SERVER_VERSION = "server_version"
    }
}
