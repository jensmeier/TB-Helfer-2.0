package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.SyncWorksiteContextEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Context-only reconciliation. Never performs a global BTB/photo/track-data sync. */
class WorksiteContextSync(context: Context) {
    private val app = context.applicationContext
    sealed interface Result {
        data class Success(val items: List<TeamManagementApi.WorksiteContext>) : Result
        data class Error(val message: String) : Result
        data object SessionInvalid : Result
        data object Unsupported : Result
    }
    suspend fun prepare(token: String, roots: Set<Long>): Result = mutex.withLock {
        try {
            ServiceLocator.initialisieren(app)
            for (rootId in roots + WorksiteLocalSyncStore.roots(app)) {
                val root = ServiceLocator.baustellenKnotenDao.laden(rootId) ?: continue
                if (ServiceLocator.worksiteBindingRepository.bindingForLocalRoot(rootId) != null) continue
                val request = WorksiteLocalSyncStore.createRequest(app, rootId, root.hauptbaustellenName)
                check(request.getString("owner") == BtbLifecycleOutbox.owner(app)) { "Baustellenauftrag gehört zu einer anderen Gerätefreigabe." }
                if (!request.has("server")) {
                    val (code, body) = sendRequest(token, "POST", "/api/v1/worksites",
                        JSONObject().put("name", request.getString("name")), request.getString("id"))
                    failure(code, body)?.let {
                        if (it is Result.Error) WorksiteLocalSyncStore.problem(app, rootId, it.message)
                        return@withLock it
                    }
                    check(body.optString("id").isNotBlank()) { "Server lieferte keine Baustellen-ID." }
                    WorksiteLocalSyncStore.rememberCreated(app, rootId, body.getString("id"), body.getLong("version"), body.getString("name"))
                }
            }
            // Persisted response IDs are bound BEFORE bootstrap can create any local roots.
            val refreshed = refreshLocked(token)
            if (refreshed !is Result.Success) return@withLock refreshed
            for (rootId in WorksiteLocalSyncStore.roots(app)) {
                val binding = ServiceLocator.worksiteBindingRepository.bindingForLocalRoot(rootId) ?: continue
                val head = WorksiteLocalSyncStore.headRequest(app, rootId, binding.worksiteServerId) ?: continue
                check(head.getString("owner") == BtbLifecycleOutbox.owner(app)) { "Namensänderung gehört zu einer anderen Gerätefreigabe." }
                val (code, body) = sendRequest(token, "PATCH", "/api/v1/worksites/${binding.worksiteServerId}",
                    JSONObject().put("name", head.getString("name")).put("base_version", head.getLong("base_version")), head.getString("id"))
                failure(code, body)?.let {
                    if (it is Result.Error) WorksiteLocalSyncStore.problem(app, rootId, it.message)
                    return@withLock it
                }
                WorksiteLocalSyncStore.confirmHead(app, rootId, head, body.getLong("version"))
            }
            refreshed
        } catch (e: Exception) {
            val message = e.message ?: "Baustellenübertragung ausstehend."
            WorksiteLocalSyncStore.roots(app).forEach { WorksiteLocalSyncStore.problem(app, it, message) }
            Result.Error(message)
        }
    }
    suspend fun refresh(token: String): Result = mutex.withLock {
        try { refreshLocked(token) } catch (e: Exception) { Result.Error(e.message ?: "Baustellenkontext konnte nicht aktualisiert werden.") }
    }
    private suspend fun refreshLocked(token: String): Result {
        val remote = when (val r = TeamManagementApi().loadWorksiteContexts(token)) {
            is TeamManagementApi.WorksiteContextResult.Success -> r.items
            is TeamManagementApi.WorksiteContextResult.Error -> return Result.Error(r.message)
            TeamManagementApi.WorksiteContextResult.Unsupported -> return Result.Unsupported
            TeamManagementApi.WorksiteContextResult.SessionInvalid -> return Result.SessionInvalid
        }
        val previous = ServiceLocator.collabSyncRepository.worksiteContexts().associateBy { it.worksiteServerId }
        for (item in remote) {
            val before = previous[item.worksiteId]
            if (before == null || before.groupId != item.groupId || before.memberRole != item.memberRole || before.areaScopeJson != item.areaScopeJson)
                WorksiteLocalSyncStore.markInitial(app, item.worksiteId)
        }
        for ((id, before) in previous) {
            val after = remote.firstOrNull { it.worksiteId == id }
            if (after == null || before.groupId != after.groupId || before.memberRole != after.memberRole || before.areaScopeJson != after.areaScopeJson) {
                val binding = ServiceLocator.worksiteBindingRepository.bindingForServer(id)
                val local = binding?.let { ServiceLocator.baustellenKnotenDao.laden(it.localRootKnotenId) }
                if (local != null) SelectiveSyncStore.btbScopes(app).filter { it.localBaustelleId == local.altBaustelleId }
                    .forEach { WorksiteLocalSyncStore.blockBtb(app, it.clientBtbId) }
            }
        }
        WorksiteAccessStore.replace(app, remote)
        ServiceLocator.collabSyncRepository.replaceWorksiteContexts(remote.map { item ->
            SyncWorksiteContextEntity(worksiteServerId=item.worksiteId, name=item.name, designation=item.designation,
                status=item.status, groupId=item.groupId, memberRole=item.memberRole, areaScopeJson=item.areaScopeJson,
                groupLabel=item.groupLabel, leadUserId=item.leadUserId, leadNameSnapshot=item.leadNameSnapshot)
        })
        for ((root, created) in WorksiteLocalSyncStore.createdRequests(app)) {
            check(created.getString("owner") == BtbLifecycleOutbox.owner(app)) { "Baustellenauftrag gehört zu einer anderen Gerätefreigabe." }
            val id = created.getString("server")
            if (remote.none { it.worksiteId == id }) return Result.Error("Angelegte Baustelle ist noch nicht im berechtigten Serverkontext verfügbar.")
            ServiceLocator.worksiteBindingRepository.bind(root, id)
            WorksiteLocalSyncStore.rememberBasis(app, root, id, created.getLong("version"), created.getString("createdName"))
            val local = ServiceLocator.baustellenKnotenDao.laden(root)
            if (local != null && local.hauptbaustellenName != created.getString("createdName"))
                WorksiteLocalSyncStore.rename(app, root, local.hauptbaustellenName)
            WorksiteLocalSyncStore.finishCreate(app, root)
        }
        for (bound in ServiceLocator.worksiteBindingRepository.contextsWithBindings()) {
            val root = bound.localWorksite ?: continue
            val item = remote.firstOrNull { it.worksiteId == bound.context.worksiteServerId } ?: continue
            when (val team = TeamManagementApi().loadTeam(token, item.worksiteId)) {
                is TeamManagementApi.TeamResult.Success -> TeamDisplayCache.remember(app, team.team, root.id)
                is TeamManagementApi.TeamResult.Error -> return Result.Error(team.message)
                TeamManagementApi.TeamResult.SessionInvalid -> return Result.SessionInvalid
                TeamManagementApi.TeamResult.Unsupported -> return Result.Unsupported
            }
            if (WorksiteLocalSyncStore.desired(app, root.id) != null) {
                if (item.version > 0 && item.name != null)
                    WorksiteLocalSyncStore.seedMissingBasis(app, root.id, item.worksiteId, item.version, item.name)
                continue
            }
            val name = item.name?.takeIf { it.isNotBlank() } ?: continue
            if (item.version <= 0) continue
            // Incoming name never queues an outgoing mutation.
            ServiceLocator.baustellenBaumRepository.hauptbaustelleUmbenennen(root.id, name, fromServer=true,
                onApplied = { WorksiteLocalSyncStore.rememberBasis(app, root.id, item.worksiteId, item.version, name) })
        }
        return Result.Success(remote)
    }
    private fun sendRequest(token: String, method: String, path: String, payload: JSONObject, id: String): Pair<Int, JSONObject> {
        val c = de.tbhelfer.server.recovery.RecoveryHttp.open(URL(ServerConfig.BASE_URL.trimEnd('/') + path))
        try {
            c.requestMethod=method; c.connectTimeout=10000; c.readTimeout=30000; c.doOutput=true
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("X-Client-Change-Id", id)
            c.outputStream.writer(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to JSONObject(body.ifBlank { "{}" })
        } finally { c.disconnect() }
    }
    private fun failure(code: Int, body: JSONObject): Result? = when {
        code in 200..299 -> null
        code == HttpURLConnection.HTTP_UNAUTHORIZED -> Result.SessionInvalid
        code == 403 && body.optString("detail") in setOf("DEVICE_REVOKED", "USER_DISABLED", "USER_EXPIRED") -> Result.SessionInvalid
        code == 409 -> Result.Error("Baustellenname: Versionskonflikt. Lokale Änderung bleibt erhalten; Konflikt prüfen.")
        code == 404 || code == 405 -> Result.Error("Baustellenübertragung ausstehend: passender Serverstand erforderlich.")
        else -> Result.Error("Baustellenübertragung: ${body.opt("detail") ?: code}")
    }
    companion object { val mutex = Mutex() }
}
