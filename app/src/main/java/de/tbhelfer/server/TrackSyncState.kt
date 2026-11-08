package de.tbhelfer.server

import de.tbhelfer.datenbank.TBDatenbank
import de.tbhelfer.datenbank.TrackSyncJournal
import de.tbhelfer.server.recovery.RecoveryProtocol
import org.json.JSONObject

/** Baseline commits atomically with the main Room database. */
class TrackSyncState(database: TBDatenbank, token: String) {
    private val dao = database.trackSyncJournalDao()
    private val owner = RecoveryProtocol.owner(token)
    suspend fun load(worksiteId: String): JSONObject = JSONObject(dao.load(owner, "track:$worksiteId") ?: "{}")
    suspend fun save(worksiteId: String, records: JSONObject) {
        dao.save(TrackSyncJournal(owner, "track:$worksiteId", records.toString()))
    }
}
