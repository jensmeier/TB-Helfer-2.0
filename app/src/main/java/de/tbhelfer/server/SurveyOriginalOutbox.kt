package de.tbhelfer.server

import android.content.Context
import android.util.AtomicFile
import de.tbhelfer.datenbank.TrassenDao
import de.tbhelfer.datenbank.WorksiteBindingRepository
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Immutable raw bytes and a durable upload queue; never replace a source by derived samples. */
object SurveyOriginalOutbox {
    internal fun deliveryKey(hash: String, localWorksiteId: Long?): String =
        localWorksiteId?.takeIf { it > 0L }?.let { "$hash:$it" } ?: hash

    @Synchronized fun retain(context: Context, name: String, bytes: ByteArray, localWorksiteId: Long? = null) {
        val extension = name.substringAfterLast('.', "").uppercase()
        if (extension !in setOf("TRA", "GRA", "TRG", "KOR", "KF", "KV", "CSV", "TXT")) return
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "survey-originals").apply { mkdirs() }
        val target = File(directory, "$hash.bin")
        if (!target.exists()) {
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            try { stream.write(bytes); atomic.finishWrite(stream) }
            catch (error: Exception) { atomic.failWrite(stream); throw error }
        }
        val preferences = context.getSharedPreferences("survey_original_queue", Context.MODE_PRIVATE)
        // Raw bytes are content-addressed, but delivery is per worksite.
        val queueKey = deliveryKey(hash, localWorksiteId)
        if (!preferences.contains(queueKey)) check(preferences.edit().putString(queueKey, JSONObject()
            .put("name", File(name).name).put("kind", extension).put("local_worksite_id", localWorksiteId ?: JSONObject.NULL).toString()).commit())
        TrackSyncWorker.enqueue(context)
    }

    suspend fun upload(context: Context, token: String, dao: TrassenDao, bindings: WorksiteBindingRepository) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val network = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        // Original archives are large; small track/KM deltas remain independent.
        if (network?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) != true) return
        val queue = context.getSharedPreferences("survey_original_queue", Context.MODE_PRIVATE)
        val sent = context.getSharedPreferences("survey_original_sent_${de.tbhelfer.server.recovery.RecoveryProtocol.owner(token)}", Context.MODE_PRIVATE)
        val axes = dao.alleAchsen()
        val contexts = bindings.contextsWithBindings()
        for ((queueKey, raw) in queue.all) {
            if (sent.contains(queueKey) || raw !is String) continue
            val hash = queueKey.substringBefore(':')
            val metadata = JSONObject(raw)
            val sourceName = metadata.getString("name")
            val explicitWorksite = metadata.optLong("local_worksite_id", 0L).takeIf { it > 0L }
            // A known import worksite is authoritative. Never mix other axes with it.
            val matching = if (explicitWorksite != null) setOf(explicitWorksite) else
                axes.filter { it.dateiSignatur?.let(hash::startsWith) == true }
                    .mapNotNull { it.baustelleId }.toSet()
            val targets = contexts.filter { it.localWorksite?.altBaustelleId in matching }
                .distinctBy { it.context.worksiteServerId }
            if (targets.size != 1) continue // no silent assignment to an unrelated worksite
            val target = targets.single()
            val file = File(context.filesDir, "survey-originals/$hash.bin")
            val pendingFiles = context.getSharedPreferences("survey_original_files_${de.tbhelfer.server.recovery.RecoveryProtocol.owner(token)}", Context.MODE_PRIVATE)
            val fileId = pendingFiles.getString(queueKey, null) ?: run {
            val uploaded = Server1FileApi().uploadLinkedFile(token, target.context.worksiteServerId,
                "WORKSITE", target.context.worksiteServerId, sourceName, "application/octet-stream", file, hash)
            val uploadedId = when (uploaded) {
                is Server1FileApi.UploadResult.Success -> uploaded.file.fileId
                is Server1FileApi.UploadResult.Error -> error(uploaded.message)
                else -> error("Vermessungsoriginal konnte noch nicht übertragen werden.")
            }
            check(pendingFiles.edit().putString(queueKey, uploadedId).commit())
            uploadedId
            }
            val body = JSONObject().put("file_id", fileId).put("data_kind", metadata.getString("kind"))
                .put("catalog_scope", "GLOBAL")
            val original = try {
                SurveySyncApi().request(token, "/api/v1/survey-originals/from-file", body).getString("id")
            } catch (error: SurveySyncApi.Failure) {
                if (error.status == 409 && error.detail?.optString("code") in setOf(
                    "SURVEY_ORIGINAL_GLOBAL_ALREADY_AVAILABLE", "SURVEY_ORIGINAL_ALREADY_IMPORTED"))
                    error.detail!!.getString("original_id") else throw error
            }
            val parsed = SurveySyncApi().request(token,
                "/api/v1/worksites/${SurveySyncApi.encode(target.context.worksiteServerId)}/survey-originals/${SurveySyncApi.encode(original)}/parse", JSONObject())
            check(parsed.optString("status") == "PARSED") {
                "Vermessungsoriginal gespeichert; Parser meldet ${parsed.optString("status")}: ${parsed.optString("error_code")}."
            }
            check(sent.edit().putString(queueKey, original).commit()) { "Originalbestätigung konnte nicht gespeichert werden." }
        }
    }
}
