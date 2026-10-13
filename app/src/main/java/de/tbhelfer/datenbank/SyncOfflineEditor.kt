package de.tbhelfer.datenbank

import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Kleine Bearbeitungsschicht für bereits heruntergeladene Serverobjekte.
 * Änderungen werden sofort lokal sichtbar und zusätzlich in die Upload-Queue geschrieben.
 * Mehrere lokale Änderungen desselben Objekts vor dem Upload werden zusammengefasst.
 */
class SyncOfflineEditor(
    private val dao: SyncDao
) {
    suspend fun createRoute(routeNumber: String, name: String?): String {
        val id = UUID.randomUUID().toString()
        val normalizedNumber = routeNumber.trim()
        require(normalizedNumber.isNotBlank()) { "ROUTE_NUMBER_REQUIRED" }
        val payload = JSONObject()
            .put("route_number", normalizedNumber)
            .putNullable("name", name?.trim()?.takeIf { it.isNotBlank() })
            .put("data_status", "MANUAL")
        dao.enqueueChange(
            SyncPendingChangeEntity(
                objectKind = SyncPendingChangeEntity.KIND_ROUTE,
                serverId = id,
                baseVersion = null,
                operation = SyncPendingChangeEntity.OP_UPSERT,
                payloadJson = payload.toString()
            )
        )
        dao.upsertRoutes(
            listOf(
                SyncRouteEntity(
                    serverId = id,
                    routeNumber = normalizedNumber,
                    name = name?.trim()?.takeIf { it.isNotBlank() },
                    entityVersion = 0,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        )
        return id
    }

    suspend fun updateRoute(serverId: String, routeNumber: String, name: String?): Long {
        val current = dao.route(serverId) ?: error("ROUTE_NOT_CACHED")
        val normalizedNumber = routeNumber.trim()
        require(normalizedNumber.isNotBlank()) { "ROUTE_NUMBER_REQUIRED" }
        val payload = JSONObject()
            .put("route_number", normalizedNumber)
            .putNullable("name", name?.trim()?.takeIf { it.isNotBlank() })
            .putNullable("description", current.description)
        val queueId = replacePending(
            kind = SyncPendingChangeEntity.KIND_ROUTE,
            serverId = serverId,
            currentVersion = current.entityVersion,
            operation = SyncPendingChangeEntity.OP_UPSERT,
            payloadJson = payload.toString()
        )
        dao.upsertRoutes(
            listOf(
                current.copy(
                    routeNumber = normalizedNumber,
                    name = name?.trim()?.takeIf { it.isNotBlank() },
                    isDeleted = false,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        )
        return queueId
    }

    suspend fun createStation(stationCode: String?, name: String, shortName: String?): String {
        val id = UUID.randomUUID().toString()
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "STATION_NAME_REQUIRED" }
        val payload = JSONObject()
            .putNullable("station_code", stationCode?.trim()?.takeIf { it.isNotBlank() })
            .put("name", normalizedName)
            .putNullable("short_name", shortName?.trim()?.takeIf { it.isNotBlank() })
            .put("data_status", "MANUAL")
        dao.enqueueChange(
            SyncPendingChangeEntity(
                objectKind = SyncPendingChangeEntity.KIND_STATION,
                serverId = id,
                baseVersion = null,
                operation = SyncPendingChangeEntity.OP_UPSERT,
                payloadJson = payload.toString()
            )
        )
        dao.upsertStations(
            listOf(
                SyncStationEntity(
                    serverId = id,
                    stationCode = stationCode?.trim()?.takeIf { it.isNotBlank() },
                    name = normalizedName,
                    shortName = shortName?.trim()?.takeIf { it.isNotBlank() },
                    entityVersion = 0,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        )
        return id
    }

    suspend fun updateStation(
        serverId: String,
        stationCode: String?,
        name: String,
        shortName: String?
    ): Long {
        val current = dao.station(serverId) ?: error("STATION_NOT_CACHED")
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "STATION_NAME_REQUIRED" }
        val payload = JSONObject()
            .putNullable("station_code", stationCode?.trim()?.takeIf { it.isNotBlank() })
            .put("name", normalizedName)
            .putNullable("short_name", shortName?.trim()?.takeIf { it.isNotBlank() })
            .putNullable("description", current.description)
        val queueId = replacePending(
            kind = SyncPendingChangeEntity.KIND_STATION,
            serverId = serverId,
            currentVersion = current.entityVersion,
            operation = SyncPendingChangeEntity.OP_UPSERT,
            payloadJson = payload.toString()
        )
        dao.upsertStations(
            listOf(
                current.copy(
                    stationCode = stationCode?.trim()?.takeIf { it.isNotBlank() },
                    name = normalizedName,
                    shortName = shortName?.trim()?.takeIf { it.isNotBlank() },
                    isDeleted = false,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        )
        return queueId
    }

    suspend fun createObject(
        stationServerId: String?,
        routeServerId: String?,
        objectType: String,
        designation: String,
        kmM: Double?
    ): String {
        require(!stationServerId.isNullOrBlank() || !routeServerId.isNullOrBlank()) {
            "OBJECT_PARENT_REQUIRED"
        }
        val normalizedName = designation.trim()
        require(normalizedName.isNotBlank()) { "OBJECT_DESIGNATION_REQUIRED" }
        val id = UUID.randomUUID().toString()
        val payload = JSONObject()
            .putNullable("route_id", routeServerId)
            .putNullable("station_id", stationServerId)
            .put("object_type", objectType.trim().uppercase())
            .put("designation", normalizedName)
            .putNullable("km_m", kmM)
            .put("data_status", "MANUAL")
        dao.enqueueChange(
            SyncPendingChangeEntity(
                objectKind = SyncPendingChangeEntity.KIND_OBJECT,
                serverId = id,
                baseVersion = null,
                operation = SyncPendingChangeEntity.OP_UPSERT,
                payloadJson = payload.toString()
            )
        )
        dao.upsertObjects(
            listOf(
                SyncObjectEntity(
                    serverId = id,
                    routeServerId = routeServerId,
                    stationServerId = stationServerId,
                    objectType = objectType.trim().uppercase(),
                    designation = normalizedName,
                    kmM = kmM,
                    dataStatus = "MANUAL",
                    entityVersion = 0,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        )
        return id
    }

    suspend fun updateSection(
        serverId: String,
        designation: String?,
        kmFromM: Double?,
        kmToM: Double?
    ): Long {
        val current = dao.section(serverId) ?: error("SECTION_NOT_CACHED")
        val payload = JSONObject()
            .put("route_id", current.routeServerId)
            .putNullable("designation", designation?.trim()?.takeIf { it.isNotBlank() })
            .putNullable("km_from_m", kmFromM)
            .putNullable("km_to_m", kmToM)
            .put("data_status", "MANUAL")

        val queueId = replacePending(
            kind = SyncPendingChangeEntity.KIND_SECTION,
            serverId = serverId,
            currentVersion = current.entityVersion,
            operation = SyncPendingChangeEntity.OP_UPSERT,
            payloadJson = payload.toString()
        )

        val minKm = when {
            kmFromM != null && kmToM != null -> min(kmFromM, kmToM)
            else -> kmFromM ?: kmToM
        }
        val maxKm = when {
            kmFromM != null && kmToM != null -> max(kmFromM, kmToM)
            else -> kmFromM ?: kmToM
        }
        dao.upsertSections(
            listOf(
                current.copy(
                    designation = designation?.trim()?.takeIf { it.isNotBlank() },
                    kmFromM = kmFromM,
                    kmToM = kmToM,
                    kmMinM = minKm,
                    kmMaxM = maxKm,
                    dataStatus = "MANUAL",
                    isDeleted = false,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        )
        return queueId
    }

    suspend fun deleteSection(serverId: String): Long {
        val current = dao.section(serverId) ?: error("SECTION_NOT_CACHED")
        val queueId = replacePending(
            kind = SyncPendingChangeEntity.KIND_SECTION,
            serverId = serverId,
            currentVersion = current.entityVersion,
            operation = SyncPendingChangeEntity.OP_DELETE,
            payloadJson = JSONObject().put("route_id", current.routeServerId).toString()
        )
        dao.markSectionDeleted(serverId, current.entityVersion)
        return queueId
    }

    suspend fun updateObject(
        serverId: String,
        designation: String,
        kmM: Double?
    ): Long {
        val current = dao.objectById(serverId) ?: error("OBJECT_NOT_CACHED")
        val payload = JSONObject()
            .putNullable("route_id", current.routeServerId)
            .putNullable("station_id", current.stationServerId)
            .put("object_type", current.objectType)
            .put("designation", designation.trim())
            .putNullable("km_m", kmM)
            .put("data_status", "MANUAL")
        current.propertiesJson?.takeIf { it.isNotBlank() }?.let {
            runCatching { JSONObject(it) }.getOrNull()?.let { properties -> payload.put("properties", properties) }
        }

        val queueId = replacePending(
            kind = SyncPendingChangeEntity.KIND_OBJECT,
            serverId = serverId,
            currentVersion = current.entityVersion,
            operation = SyncPendingChangeEntity.OP_UPSERT,
            payloadJson = payload.toString()
        )
        dao.upsertObjects(
            listOf(
                current.copy(
                    designation = designation.trim(),
                    kmM = kmM,
                    dataStatus = "MANUAL",
                    isDeleted = false,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        )
        return queueId
    }

    suspend fun deleteObject(serverId: String): Long {
        val current = dao.objectById(serverId) ?: error("OBJECT_NOT_CACHED")
        val payload = JSONObject()
            .putNullable("route_id", current.routeServerId)
            .putNullable("station_id", current.stationServerId)
        val queueId = replacePending(
            kind = SyncPendingChangeEntity.KIND_OBJECT,
            serverId = serverId,
            currentVersion = current.entityVersion,
            operation = SyncPendingChangeEntity.OP_DELETE,
            payloadJson = payload.toString()
        )
        dao.markObjectDeleted(serverId, current.entityVersion)
        return queueId
    }

    suspend fun conflicts(): List<SyncPendingChangeEntity> = dao.conflicts()

    /**
     * Verwirft die lokale Konfliktänderung und übernimmt den Serverstand aus der 409-Antwort.
     */
    suspend fun acceptServerVersion(conflictId: Long): Boolean {
        val conflict = dao.pendingChange(conflictId) ?: return false
        if (conflict.state != SyncPendingChangeEntity.STATE_CONFLICT) return false
        val error = conflict.lastError?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        val server = error.optJSONObject("server_entity") ?: return false
        val now = System.currentTimeMillis()

        when (conflict.objectKind) {
            SyncPendingChangeEntity.KIND_ROUTE -> {
                val existing = conflict.serverId?.let { dao.route(it) } ?: return false
                dao.upsertRoutes(
                    listOf(
                        existing.copy(
                            routeNumber = server.optString("route_number").ifBlank { existing.routeNumber },
                            name = server.optNullableString("name"),
                            description = server.optNullableString("description"),
                            entityVersion = server.optLong("current_version", existing.entityVersion),
                            isDeleted = false,
                            updatedAtEpochMs = now
                        )
                    )
                )
            }

            SyncPendingChangeEntity.KIND_STATION -> {
                val existing = conflict.serverId?.let { dao.station(it) } ?: return false
                dao.upsertStations(
                    listOf(
                        existing.copy(
                            stationCode = server.optNullableString("station_code"),
                            name = server.optString("name").ifBlank { existing.name },
                            shortName = server.optNullableString("short_name"),
                            description = server.optNullableString("description"),
                            entityVersion = server.optLong("current_version", existing.entityVersion),
                            isDeleted = false,
                            updatedAtEpochMs = now
                        )
                    )
                )
            }

            SyncPendingChangeEntity.KIND_SECTION -> {
                val existing = conflict.serverId?.let { dao.section(it) } ?: return false
                dao.upsertSections(
                    listOf(
                        existing.copy(
                            routeServerId = server.optString("route_id").ifBlank { existing.routeServerId },
                            designation = server.optNullableString("designation"),
                            kmFromM = server.optNullableDouble("km_from_m"),
                            kmToM = server.optNullableDouble("km_to_m"),
                            kmMinM = server.optNullableDouble("km_min_m"),
                            kmMaxM = server.optNullableDouble("km_max_m"),
                            dataStatus = server.optNullableString("data_status") ?: existing.dataStatus,
                            geometryJson = server.optJsonText("geom") ?: existing.geometryJson,
                            entityVersion = server.optLong("current_version", existing.entityVersion),
                            isDeleted = server.has("deleted_at") && !server.isNull("deleted_at"),
                            updatedAtEpochMs = now
                        )
                    )
                )
            }

            SyncPendingChangeEntity.KIND_OBJECT -> {
                val existing = conflict.serverId?.let { dao.objectById(it) } ?: return false
                dao.upsertObjects(
                    listOf(
                        existing.copy(
                            routeServerId = server.optNullableString("route_id"),
                            stationServerId = server.optNullableString("station_id"),
                            objectType = server.optString("object_type").ifBlank { existing.objectType },
                            designation = server.optNullableString("designation") ?: existing.designation,
                            kmM = server.optNullableDouble("km_m"),
                            dataStatus = server.optNullableString("data_status") ?: existing.dataStatus,
                            geometryJson = server.optJsonText("geom") ?: existing.geometryJson,
                            propertiesJson = server.optJsonText("properties") ?: existing.propertiesJson,
                            entityVersion = server.optLong("current_version", existing.entityVersion),
                            isDeleted = server.has("deleted_at") && !server.isNull("deleted_at"),
                            updatedAtEpochMs = now
                        )
                    )
                )
            }
            else -> return false
        }
        dao.deletePendingChange(conflictId)
        return true
    }

    /**
     * Behält die lokale Änderung, setzt aber ihre Basis auf die aktuelle Serverversion.
     * Beim nächsten Sync wird sie erneut versucht. Diese Aktion sollte nur nach bewusster
     * Entscheidung des Nutzers angeboten werden.
     */
    suspend fun keepLocalAndRetry(conflictId: Long): Boolean {
        val conflict = dao.pendingChange(conflictId) ?: return false
        if (conflict.state != SyncPendingChangeEntity.STATE_CONFLICT) return false
        val error = conflict.lastError?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        val serverVersion = error.optLong("server_version", -1L)
        if (serverVersion < 0) return false
        dao.rebaseConflict(conflictId, serverVersion)
        return true
    }

    private suspend fun replacePending(
        kind: String,
        serverId: String,
        currentVersion: Long,
        operation: String,
        payloadJson: String
    ): Long {
        val previous = dao.pendingForEntity(kind, serverId)
        val baseVersion = previous?.baseVersion ?: currentVersion.takeIf { it > 0 }
        if (previous != null) dao.deletePendingChange(previous.id)
        return dao.enqueueChange(
            SyncPendingChangeEntity(
                objectKind = kind,
                serverId = serverId,
                baseVersion = baseVersion,
                operation = operation,
                payloadJson = payloadJson
            )
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
        put(name, value ?: JSONObject.NULL)
        return this
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getDouble(name) }.getOrNull()
    }

    private fun JSONObject.optJsonText(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return opt(name)?.toString()?.takeIf { it.isNotBlank() }
    }
}
