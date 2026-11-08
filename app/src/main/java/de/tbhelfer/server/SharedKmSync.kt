package de.tbhelfer.server

import android.content.Context
import androidx.room.withTransaction
import de.tbhelfer.datenbank.*
import de.tbhelfer.vermessung.ManuelleAbschnittKmLogik
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Durable per-contribution requests against the existing shared KM catalogue. */
class SharedKmSync(private val context: Context, private val database: TBDatenbank) {
    private val dao = database.trassenDao()
    private val api = SurveySyncApi()

    suspend fun synchronize(token: String) = processMutex.withLock { synchronizeLocked(token) }

    private suspend fun synchronizeLocked(token: String) {
        val owner = de.tbhelfer.server.recovery.RecoveryProtocol.owner(token)
        val journal = database.trackSyncJournalDao()
        for (axis in dao.alleAchsen()) {
            val signature = axis.dateiSignatur?.lowercase()?.takeIf { it.matches(Regex("[a-f0-9]{24,64}")) } ?: continue
            val key = "${signature}:${axis.id}"
            val state = JSONObject(journal.load(owner, "km:$key") ?: "{}")
            suspend fun persist() { journal.save(TrackSyncJournal(owner, "km:$key", state.toString())) }
            val binding = state.optJSONObject("binding") ?: try {
                api.resolve(token, signature).also { state.put("binding", it); persist() }
            } catch (error: SurveySyncApi.Failure) {
                if (error.status == 404) continue else throw error
            }
            val axisId = binding.getString("axis_id")
            val contributions = state.optJSONObject("contributions") ?: JSONObject().also { state.put("contributions", it) }
            // Retry EXACT bytes/IDs/versions after a timeout or process death.
            for (entryKey in contributions.keys().asSequence().toList()) {
                val entry = contributions.getJSONObject(entryKey)
                val pending = entry.optJSONObject("pending") ?: continue
                val result = api.save(token, axisId, pending)
                entry.put("version", result.getLong("entity_version"))
                    .put("last_local", entry.getString("pending_local")).remove("pending")
                binding.put("axis_version", result.getLong("axis_version"))
                persist()
            }
            val remote = api.state(token, axisId)
            val sources = remote.getJSONArray("sources")
            val source = (0 until sources.length()).map { sources.getJSONObject(it) }
                .singleOrNull { it.getString("id") == binding.getString("source_segment_id") &&
                    it.getString("sha256") == binding.getString("sha256") }
                ?: error("KM-Original wurde geändert; lokale Beiträge bleiben erhalten.")
            if (source.getLong("current_version") != binding.getLong("source_version"))
                error("KM-Quellversion wurde geändert; Zuordnung muss geprüft werden.")
            val incoming = remote.getJSONArray("sections")
            database.withTransaction {
                for (index in 0 until incoming.length()) {
                    val item = incoming.getJSONObject(index)
                    if (item.optString("source_original_id") != binding.getString("source_original_id")) continue
                    val id = item.getString("id")
                    val ownedKey = contributions.keys().asSequence().firstOrNull { contributions.getJSONObject(it).optString("id") == id }
                    if (ownedKey == "anchors") {
                        val entry = contributions.getJSONObject(ownedKey)
                        check(entry.getLong("version") == item.getLong("current_version")) {
                            "Gemeinsame GPS-Kalibrierung wurde parallel geändert; lokale Anker bleiben erhalten."
                        }
                        continue
                    }
                    val localId = ownedKey?.removePrefix("section:")?.toLongOrNull()
                    val old = localId?.let { dao.manuellerAbschnittMitId(it) }
                    val oldEntry = ownedKey?.let { contributions.getJSONObject(it) }
                    if (old != null && oldEntry?.optLong("version") == item.getLong("current_version")) continue
                    if (old != null && oldEntry?.optString("last_local") != old.toString()+dao.abschnittKmPunkte(old.id).toString())
                        error("Manueller KM-Abschnitt wurde gleichzeitig lokal und auf dem Server geändert.")
                    if (item.getString("segment_status") != "ACTIVE") {
                        if (old != null) dao.manuellenAbschnittLoeschen(old.id)
                        oldEntry?.put("inactive", true)?.put("version", item.getLong("current_version"))
                        continue
                    }
                    val from = item.getDouble("station_from_m")
                    val to = item.getDouble("station_to_m")
                    val kmFrom = item.getDouble("km_from_m")
                    val kmTo = item.getDouble("km_to_m")
                    require(listOf(from,to,kmFrom,kmTo).all { it.isFinite() } && from < to) { "Ungültiger gemeinsamer KM-Abschnitt." }
                    val template = old ?: TrassenManuellerAbschnittEntity(achsTyp=axis.achsTyp,
                        bezeichnung="${axis.name} – gemeinsame KM")
                    val received = template.copy(id=old?.id ?: 0L,
                        achseName=axis.name, quelleDatei=axis.quelleDatei, aktuelleAchseId=axis.id, dateiSignatur=axis.dateiSignatur,
                        achsTyp=axis.achsTyp, bezeichnung=old?.bezeichnung ?: "${axis.name} – gemeinsame KM",
                        strecke=axis.strecke, gleis=axis.gleis, richtung=axis.richtung,
                        stationStart=from, stationEnde=to, startQuelle="GRAFIK", endeQuelle="GRAFIK",
                        kmAnkerStation=from, kmAnkerMeter=kmFrom, kmPunkt1Meter=kmFrom,
                        kmPunkt2Station=to, kmPunkt2Meter=kmTo,
                        kmStationVorzeichen=if(kmTo>=kmFrom)1 else -1,
                        bearbeitungsStatus=TrassenManuellerAbschnittEntity.STATUS_FERTIG,
                        erstelltAmEpochMs=old?.erstelltAmEpochMs ?: System.currentTimeMillis())
                    val savedId = if (old == null) dao.manuellenAbschnittEinfuegen(received) else {
                        dao.manuellenAbschnittAktualisieren(received); old.id
                    }
                    // The server's linear calibration and all anchor metadata are kept.
                    // Old control points must not silently override a newer calibration.
                    dao.abschnittKmPunkteLoeschen(savedId)
                    val remoteAnchors = item.getJSONArray("anchors")
                    for (anchorIndex in 0 until remoteAnchors.length()) {
                        val anchor = remoteAnchors.getJSONObject(anchorIndex)
                        fun number(field: String): Double? = if (anchor.isNull(field)) null else anchor.optDouble(field).takeIf { it.isFinite() }
                        dao.abschnittKmPunktErsetzen(TrassenAbschnittKmPunktEntity(abschnittId=savedId,
                            typ=TrassenAbschnittKmPunktEntity.TYP_KONTROLLANKER,
                            station=anchor.getDouble("station_m"), kmMeter=anchor.getDouble("km_meter"),
                            gpsLat=number("gps_lat"), gpsLon=number("gps_lon"),
                            gpsAccuracyM=number("accuracy_m")?.toFloat(), gpsStreuungM=number("gps_spread_m")))
                    }
                    val saved = received.copy(id=savedId)
                    contributions.put("section:$savedId", JSONObject().put("id", id)
                        .put("version", item.getLong("current_version"))
                        .put("last_local", saved.toString()+dao.abschnittKmPunkte(savedId).toString())
                        .put("anchors", item.getJSONArray("anchors")))
                }
                binding.put("axis_version", remote.getLong("axis_version"))
                persist()
            }
            val anchors = dao.kmAnkerFuerAchse(axis.name, axis.quelleDatei).sortedBy { it.station }
            val drafts = mutableListOf<Triple<String, String, JSONObject>>()
            if (anchors.isNotEmpty() && (anchors.size != 2 || anchors.any { it.gpsAccuracyM == null })) {
                ErrorLogStore.record(context, ErrorHistory.Operation.FULL_SYNC,
                    "KM-Anker bleiben lokal: Für den gemeinsamen GPS-Beitrag sind zwei Anker mit bekannter Genauigkeit erforderlich.")
            }
            if (anchors.size == 2 && anchors.all { it.gpsAccuracyM != null }) {
                val json = JSONArray()
                for (anchor in anchors) {
                    val kind = "GPS"
                    val item = JSONObject().put("id", uuid("$key:anchor:${anchor.punktNummer}"))
                        .put("kind", kind).put("station_m", anchor.station).put("km_meter", anchor.kmMeter)
                    if (kind == "GPS") item.put("gps_lat", anchor.gpsLat).put("gps_lon", anchor.gpsLon)
                        .put("accuracy_m", anchor.gpsAccuracyM!!.toDouble())
                    json.put(item)
                }
                drafts += Triple("anchors", anchors.toString(), JSONObject().put("anchors", json)
                    .put("station_from_m", anchors.first().station).put("station_to_m", anchors.last().station)
                    .put("km_from_m", anchors.first().kmMeter).put("km_to_m", anchors.last().kmMeter))
            }
            val sections = dao.alleManuellenAbschnitte().filter { it.aktuelleAchseId == axis.id }
            for (section in sections.filter { it.bearbeitungsStatus == TrassenManuellerAbschnittEntity.STATUS_FERTIG }) {
                val start = section.stationStart ?: continue
                val end = section.stationEnde ?: continue
                val points = dao.abschnittKmPunkte(section.id)
                val from = minOf(start, end)
                val to = maxOf(start, end)
                val kmFrom = ManuelleAbschnittKmLogik.kmFuerStation(section, from, points).kmMeter ?: continue
                val kmTo = ManuelleAbschnittKmLogik.kmFuerStation(section, to, points).kmMeter ?: continue
                // The existing shared endpoint is linear. Never flatten a KM jump.
                if (points.any { it.typ == TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG }) {
                    ErrorLogStore.record(context, ErrorHistory.Operation.FULL_SYNC, "KM_SPRUNG: gemeinsamer linearer KM-Vertrag unterstützt diesen Abschnitt nicht")
                    continue
                }
                val json = JSONArray()
                for ((index, value) in listOf(from to kmFrom, to to kmTo).withIndex()) json.put(JSONObject()
                    .put("id", uuid("$key:section:${section.id}:$index")).put("kind", "MANUAL")
                    .put("station_m", value.first).put("km_meter", value.second))
                for (point in points) {
                    val km = point.kmMeter ?: continue
                    val anchor = JSONObject().put("id", uuid("$key:section:${section.id}:point:${point.id}"))
                        .put("kind", if (point.gpsLat != null && point.gpsLon != null && point.gpsAccuracyM != null) "GPS" else "MANUAL")
                        .put("station_m", point.station).put("km_meter", km)
                    if (anchor.getString("kind") == "GPS") anchor.put("gps_lat", point.gpsLat)
                        .put("gps_lon", point.gpsLon).put("accuracy_m", point.gpsAccuracyM!!.toDouble())
                    json.put(anchor)
                }
                drafts += Triple("section:${section.id}", section.toString()+points.toString(), JSONObject()
                    .put("anchors", json).put("station_from_m", from).put("station_to_m", to)
                    .put("km_from_m", kmFrom).put("km_to_m", kmTo).put("note", section.notiz.orEmpty()))
            }
            for ((localKey, fingerprint, draft) in drafts) {
                val entry = contributions.optJSONObject(localKey) ?: JSONObject().put("id", UUID.randomUUID().toString())
                    .also { contributions.put(localKey, it) }
                if (entry.optString("last_local") == fingerprint) continue
                val originalAnchors = entry.optJSONArray("anchors")
                if (originalAnchors != null && (0 until originalAnchors.length()).any {
                    originalAnchors.getJSONObject(it).optString("kind") !in setOf("GPS", "MANUAL")
                }) {
                    ErrorLogStore.record(context, ErrorHistory.Operation.FULL_SYNC,
                        "Geänderter KM-Abschnitt enthält GVP-/Mast-Referenzen: lokale Änderung bleibt erhalten; fachliche Auflösung im Portal erforderlich.")
                    continue
                }
                val body = JSONObject(draft.toString())
                    .put("contribution_id", entry.getString("id"))
                    .put("base_version", binding.getLong("axis_version"))
                    .put("source_segment_id", binding.getString("source_segment_id"))
                    .put("source_version", binding.getLong("source_version"))
                    .put("source_original_id", binding.getString("source_original_id"))
                    .put("source_sha256", binding.getString("sha256"))
                    .put("client_change_id", UUID.randomUUID().toString())
                if (entry.has("version")) body.put("segment_id", entry.getString("id")).put("segment_version", entry.getLong("version"))
                entry.put("pending", body).put("pending_local", fingerprint)
                persist()
                val result = api.save(token, axisId, body)
                entry.put("version", result.getLong("entity_version")).put("last_local", fingerprint).remove("pending")
                binding.put("axis_version", result.getLong("axis_version"))
                persist()
            }

        }
    }

    private companion object { val processMutex = Mutex() }

    private fun uuid(value: String): String = UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
}
