package de.tbhelfer.datenbank

import android.content.Context
import androidx.room.withTransaction
import de.tbhelfer.server.TrackMergePolicy
import de.tbhelfer.server.TrackSyncState
import de.tbhelfer.server.WorksiteTrackDataApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Spiegelt den fuer den mobilen Betrieb benoetigten Streckendatenstand einer Baustelle.
 *
 * LEAD: Dreiwegeabgleich mit dauerhaftem Ausgangsstand und Versionsschutz.
 * Helfer: berechtigter Empfang; lokale Änderungen bleiben erhalten.
 *
 * Originaldateien laufen über eine separate Outbox. Empfang und Journal sind atomar;
 * Achs-IDs, lokale Elemente und andere Baustellen bleiben erhalten.
 */
class WorksiteTrackDataSync(
    private val database: TBDatenbank,
    private val appContext: Context,
    private val trassenDao: TrassenDao,
    private val streckenpunktDao: StreckenpunktDao,
    private val bindingRepository: WorksiteBindingRepository,
    private val api: WorksiteTrackDataApi = WorksiteTrackDataApi()
) {
    data class Summary(val uploadedDatasets: Int, val appliedDatasets: Int)

    sealed interface Result {
        data class Success(val summary: Summary) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    suspend fun synchronize(token: String, worksiteIds: Set<String>? = null, receiveOnly: Boolean = false): Result = mutex.withLock {
        try { synchronizeLocked(token, worksiteIds, receiveOnly) }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { Result.Error(error.message ?: "Streckendaten bleiben erhalten; Abgleich fehlgeschlagen.") }
    }

    private suspend fun synchronizeLocked(token: String, worksiteIds: Set<String>?, receiveOnly: Boolean): Result {
        var uploaded = 0
        var applied = 0
        var supported = false
        val state = TrackSyncState(database, token)
        for (bound in bindingRepository.contextsWithBindings()) {
            val local = bound.localWorksite ?: continue
            val serverId = bound.context.worksiteServerId
            if (worksiteIds != null && serverId !in worksiteIds) continue
            val mayUpload = !receiveOnly && bound.context.memberRole.equals("LEAD", true)
            val baseline = state.load(serverId)
            val boundKeys = baseline.keys().asSequence().mapNotNull { key ->
                baseline.optJSONObject(key)?.optLong("local_axis_id", 0L)?.takeIf { it > 0L }?.let { it to key }
            }.toMap()
            fun bind(rows: List<WorksiteTrackDataApi.Dataset>) = rows.map { row ->
                row.localAxisId?.let(boundKeys::get)?.let { row.copy(datasetKey = it) } ?: row
            }
            val localRows = database.withTransaction { bind(buildLocalDatasets(serverId, local.altBaustelleId, baseline.has("ws:$serverId:streckenpunkte:v1"))) }
            val remoteRows = when (val get = api.load(token, serverId)) {
                WorksiteTrackDataApi.Result.Unsupported -> continue
                WorksiteTrackDataApi.Result.SessionInvalid -> return Result.SessionInvalid
                is WorksiteTrackDataApi.Result.Error -> return Result.Error(get.message)
                is WorksiteTrackDataApi.Result.Success -> get.datasets
            }.filter { it.datasetKind.uppercase() in setOf("AXIS", "STRECKENPUNKTE") }
            validateDatasets(remoteRows)
            val locals = localRows.associateBy { it.datasetKey }
            val remotes = remoteRows.associateBy { it.datasetKey }
            require(remotes.size == remoteRows.size) { "Server liefert doppelte Streckendaten-IDs." }
            val send = mutableListOf<WorksiteTrackDataApi.Dataset>()
            val receive = mutableListOf<WorksiteTrackDataApi.Dataset>()
            val accepted = mutableListOf<WorksiteTrackDataApi.Dataset>()
            for (key in locals.keys + remotes.keys) {
                val mine = locals[key]
                val remote = remotes[key]
                val base = baseline.optJSONObject(key)
                val action = TrackMergePolicy.decide(base?.optString("fingerprint"), mine?.let(api::fingerprint), remote?.let(api::fingerprint), mayUpload)
                when (action) {
                    TrackMergePolicy.Action.SAME -> if (remote != null) accepted += remote.copy(localAxisId = mine?.localAxisId)
                    TrackMergePolicy.Action.UPLOAD -> if (mine != null) send += mine.copy(
                        currentVersion = base?.optLong("version") ?: 0L,
                        serverId = base?.optString("id")?.takeIf { it.isNotBlank() })
                    TrackMergePolicy.Action.RECEIVE -> if (remote != null) receive += remote.copy(localAxisId = mine?.localAxisId)
                    TrackMergePolicy.Action.KEEP_LOCAL -> Unit
                    TrackMergePolicy.Action.REMOTE_REMOVED -> return Result.Error("Streckendaten sind auf dem Server nicht mehr sichtbar. Lokale Daten bleiben zur Prüfung erhalten: $key")
                    TrackMergePolicy.Action.CONFLICT -> return Result.Error("Parallele Streckendatenänderung: $key. Beide Fassungen bleiben erhalten; bitte Konflikt prüfen.")
                }
            }
            if (send.isNotEmpty()) {
                when (val result = api.replace(token, serverId, send)) {
                    WorksiteTrackDataApi.PutResult.Unsupported -> return Result.Unsupported
                    WorksiteTrackDataApi.PutResult.SessionInvalid -> return Result.SessionInvalid
                    is WorksiteTrackDataApi.PutResult.Error -> return Result.Error(result.message)
                    WorksiteTrackDataApi.PutResult.Success -> Unit
                }
                val confirmed = when (val get = api.load(token, serverId)) {
                    is WorksiteTrackDataApi.Result.Success -> get.datasets.associateBy { it.datasetKey }
                    WorksiteTrackDataApi.Result.SessionInvalid -> return Result.SessionInvalid
                    else -> return Result.Error("Bestätigung des Streckendaten-Uploads fehlt. Lokale Daten bleiben vorgemerkt.")
                }
                for (item in send) {
                    val remote = confirmed[item.datasetKey]
                        ?: return Result.Error("Hochgeladener Streckendatensatz fehlt in der Bestätigung.")
                    if (api.fingerprint(item) != api.fingerprint(remote)) return Result.Error("Streckendaten wurden nach dem Upload erneut geändert. Konflikt bleibt offen.")
                    accepted += remote.copy(localAxisId = item.localAxisId)
                }
                uploaded += send.size
            }
            database.withTransaction {
                val fresh = bind(buildLocalDatasets(serverId, local.altBaustelleId, baseline.has("ws:$serverId:streckenpunkte:v1"))).associateBy { it.datasetKey }
                for (item in receive) check(fresh[item.datasetKey]?.let(api::fingerprint) == locals[item.datasetKey]?.let(api::fingerprint)) {
                    "Streckendaten wurden während des Empfangs lokal geändert. Erneuter Abgleich erforderlich."
                }
                val received = applyServerDatasets(local.altBaustelleId, receive)
                applied += received.size
                accepted += received
            for (item in accepted) baseline.put(item.datasetKey, JSONObject()
                .put("fingerprint", api.fingerprint(item)).put("version", item.currentVersion)
                .put("id", item.serverId ?: JSONObject.NULL)
                .put("local_axis_id", item.localAxisId ?: JSONObject.NULL))
            state.save(serverId, baseline)
            }
            supported = true
        }
        return if (supported) Result.Success(Summary(uploaded, applied)) else Result.Unsupported
    }

    private fun validateDatasets(datasets: List<WorksiteTrackDataApi.Dataset>) {
        for (dataset in datasets) {
            val payload = JSONObject(dataset.payloadJson)
            val axis = dataset.datasetKind.equals("AXIS", true)
            val rows = payload.getJSONArray(if (axis) "samples" else "points")
            val pointKeys = mutableSetOf<String>()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                for (key in if (axis) listOf("station", "x_db_ref", "y_db_ref") else listOf("km", "gps_lat", "gps_lon")) {
                    require(row.has(key) && !row.isNull(key) && row.getDouble(key).isFinite()) { "Ungültiger Streckendatenwert: $key" }
                }
                if (!axis) {
                    StreckenpunktTyp.valueOf(row.getString("typ"))
                    require(row.has("erstellt_am_epoch_ms") && pointKeys.add("${row.getLong("erstellt_am_epoch_ms")}:${row.optInt("km_suffix")}:${row.getString("typ")}")) {
                        "Streckenpunkte haben keine eindeutige Identität; Empfang bleibt unverändert."
                    }
                }
            }
        }
    }

    private suspend fun buildLocalDatasets(
        worksiteServerId: String,
        baustelleId: Long,
        includeEmptyPoints: Boolean = false
    ): List<WorksiteTrackDataApi.Dataset> {
        val axes = trassenDao.alleAchsen().filter { it.baustelleId == baustelleId }
        val axisIds = axes.mapTo(hashSetOf()) { it.id }
        val samplesByAxis = trassenDao.alleSamplepunkte()
            .filter { it.achseId in axisIds }
            .groupBy { it.achseId }

        val result = axes.map { axis ->
            val payload = JSONObject()
                .put("name", axis.name)
                .putNullable("strecke", axis.strecke)
                .putNullable("gleis", axis.gleis)
                .putNullable("richtung", axis.richtung)
                .put("achs_typ", axis.achsTyp)
                .put("quelle_datei", axis.quelleDatei)
                .putNullable("datei_signatur", axis.dateiSignatur)
                .put("koordinatensystem", axis.koordinatensystem)
                .putNullable("station_von", axis.stationVon)
                .putNullable("station_bis", axis.stationBis)
                .put("import_status", axis.importStatus)
                .putNullable("notiz", axis.notiz)
                .put("samples", JSONArray().apply {
                    samplesByAxis[axis.id].orEmpty().sortedBy { it.station }.forEach { sample ->
                        put(
                            JSONObject()
                                .put("station", sample.station)
                                .putNullable("km_meter", sample.kmMeter)
                                .putNullable("km_quelle", sample.kmQuelle)
                                .put("x_db_ref", sample.xDbRef)
                                .put("y_db_ref", sample.yDbRef)
                                .putNullable("lat", sample.lat)
                                .putNullable("lon", sample.lon)
                                .putNullable("hoehe_m", sample.hoeheM)
                                .putNullable("richtung_deg", sample.richtungDeg)
                        )
                    }
                })
            val identity = listOf(
                axis.dateiSignatur.orEmpty(), axis.quelleDatei, axis.name,
                axis.strecke.orEmpty(), axis.gleis.orEmpty()
            ).joinToString("|")
            WorksiteTrackDataApi.Dataset(
                datasetKey = "ws:$worksiteServerId:axis:${sha256(identity)}",
                datasetKind = "AXIS",
                routeKey = axis.strecke?.let { route -> axis.gleis?.let { "$route-$it" } ?: route },
                quelleDatei = axis.quelleDatei,
                achseName = axis.name,
                payloadJson = payload.toString(),
                localAxisId = axis.id
            )
        }.toMutableList()

        val points = streckenpunktDao.ladeAlleOnce().filter { it.baustelleId == baustelleId }
        val pointsPayload = JSONObject().put("points", JSONArray().apply {
            points.sortedWith(compareBy<StreckenpunktEntity> { it.km }.thenBy { it.id }).forEach { p ->
                put(
                    JSONObject()
                        .put("km", p.km)
                        .put("km_suffix", p.kmSuffix)
                        .put("gps_lat", p.gpsLat)
                        .put("gps_lon", p.gpsLon)
                        .putNullable("gps_alt_m", p.gpsAltM)
                        .put("typ", p.typ.name)
                        .putNullable("typ_freitext", p.typFreitext)
                        .putNullable("streckennummer", p.streckennummer)
                        .putNullable("bahnhof", p.bahnhof)
                        .putNullable("gleis", p.gleis)
                        .putNullable("weiche", p.weiche)
                        .putNullable("objekt_kennung", p.objektKennung)
                        .put("bezeichnung", p.bezeichnung)
                        .putNullable("notiz", p.notiz)
                        .put("erstellt_am_epoch_ms", p.erstelltAmEpochMs)
                )
            }
        })
        if (points.isNotEmpty() || includeEmptyPoints) result += WorksiteTrackDataApi.Dataset(
            datasetKey = "ws:$worksiteServerId:streckenpunkte:v1",
            datasetKind = "STRECKENPUNKTE",
            routeKey = points.firstNotNullOfOrNull { it.streckennummer },
            quelleDatei = "ANDROID_STRECKENPUNKTE",
            achseName = "STRECKENPUNKTE",
            payloadJson = pointsPayload.toString()
        )
        return result
    }

    private suspend fun applyServerDatasets(
        baustelleId: Long,
        datasets: List<WorksiteTrackDataApi.Dataset>
    ): List<WorksiteTrackDataApi.Dataset> {
        // Caller validates the full response and holds one Room transaction.
        // Update axes in place; local source elements, original files and IDs survive.
        val applied = mutableListOf<WorksiteTrackDataApi.Dataset>()
        datasets.filter { it.datasetKind.equals("AXIS", true) }.forEach { dataset ->
            val root = runCatching { JSONObject(dataset.payloadJson) }.getOrNull() ?: return@forEach
            val axis = TrassenAchseEntity(
                baustelleId = baustelleId,
                name = root.optString("name").ifBlank { dataset.achseName },
                strecke = root.optNullableString("strecke"),
                gleis = root.optNullableString("gleis"),
                richtung = root.optNullableString("richtung"),
                achsTyp = root.optString("achs_typ").ifBlank { TrassenAchseEntity.ACHSTYP_UNBEKANNT },
                quelleDatei = root.optString("quelle_datei").ifBlank { dataset.quelleDatei },
                dateiSignatur = root.optNullableString("datei_signatur"),
                koordinatensystem = root.optString("koordinatensystem").ifBlank { TrassenAchseEntity.KOORD_DB_REF2016_4 },
                stationVon = root.optNullableDouble("station_von"),
                stationBis = root.optNullableDouble("station_bis"),
                importStatus = root.optString("import_status").ifBlank { TrassenAchseEntity.STATUS_IMPORTIERT },
                notiz = root.optNullableString("notiz")
            )
            val old = dataset.localAxisId?.let { trassenDao.achseMitId(it) }
                ?: trassenDao.alleAchsen().singleOrNull { it.baustelleId == baustelleId &&
                    it.name == axis.name && it.quelleDatei == axis.quelleDatei && it.dateiSignatur == axis.dateiSignatur }
            val axisId = if (old == null) trassenDao.achseEinfuegen(axis) else {
                trassenDao.achseAktualisieren(axis.copy(id = old.id, anzahlElemente = old.anzahlElemente,
                    importiertAmEpochMs = old.importiertAmEpochMs))
                trassenDao.samplepunkteFuerAchseLoeschen(old.id)
                old.id
            }
            val array = root.optJSONArray("samples") ?: JSONArray()
            val samples = buildList {
                for (i in 0 until array.length()) {
                    val s = array.optJSONObject(i) ?: continue
                    val station = s.optNullableDouble("station") ?: continue
                    val x = s.optNullableDouble("x_db_ref") ?: continue
                    val y = s.optNullableDouble("y_db_ref") ?: continue
                    add(
                        TrassenSamplePointEntity(
                            achseId = axisId,
                            station = station,
                            kmMeter = s.optNullableDouble("km_meter"),
                            kmQuelle = s.optNullableString("km_quelle"),
                            xDbRef = x,
                            yDbRef = y,
                            lat = s.optNullableDouble("lat"),
                            lon = s.optNullableDouble("lon"),
                            hoeheM = s.optNullableDouble("hoehe_m"),
                            richtungDeg = s.optNullableDouble("richtung_deg")
                        )
                    )
                }
            }
            if (samples.isNotEmpty()) trassenDao.samplePunkteEinfuegen(samples)
            applied += dataset.copy(localAxisId = axisId)
        }

        datasets.firstOrNull { it.datasetKind.equals("STRECKENPUNKTE", true) }?.let { dataset ->
            val array = JSONObject(dataset.payloadJson).getJSONArray("points")
            val previousPoints = streckenpunktDao.ladeAlleOnce().filter { it.baustelleId == baustelleId }
            val retainedIds = mutableSetOf<Long>()
            for (i in 0 until array.length()) {
                val p = array.optJSONObject(i) ?: continue
                val km = p.optNullableDouble("km") ?: continue
                val lat = p.optNullableDouble("gps_lat") ?: continue
                val lon = p.optNullableDouble("gps_lon") ?: continue
                val typ = runCatching { StreckenpunktTyp.valueOf(p.optString("typ")) }.getOrDefault(StreckenpunktTyp.EXAKT)
                val entity = StreckenpunktEntity(
                        baustelleId = baustelleId,
                        km = km,
                        kmSuffix = p.optInt("km_suffix", 0),
                        gpsLat = lat,
                        gpsLon = lon,
                        gpsAltM = p.optNullableDouble("gps_alt_m"),
                        typ = typ,
                        typFreitext = p.optNullableString("typ_freitext"),
                        streckennummer = p.optNullableString("streckennummer"),
                        bahnhof = p.optNullableString("bahnhof"),
                        gleis = p.optNullableString("gleis"),
                        weiche = p.optNullableString("weiche"),
                        objektKennung = p.optNullableString("objekt_kennung"),
                        bezeichnung = p.optString("bezeichnung"),
                        notiz = p.optNullableString("notiz"),
                        erstelltAmEpochMs = p.optLong("erstellt_am_epoch_ms", System.currentTimeMillis())
                    )
                val matches = previousPoints.filter { it.erstelltAmEpochMs == entity.erstelltAmEpochMs &&
                    it.kmSuffix == entity.kmSuffix && it.typ == entity.typ }
                require(matches.size <= 1) { "Lokale Streckenpunkt-Identität ist mehrdeutig." }
                val same = matches.singleOrNull()
                if (same == null) retainedIds += streckenpunktDao.einfuegen(entity)
                else {
                    streckenpunktDao.aktualisieren(entity.copy(id = same.id))
                    retainedIds += same.id
                }
            }
            previousPoints.filter { it.id !in retainedIds }.forEach { streckenpunktDao.loeschen(it) }
            applied += dataset
        }
        return applied
    }

    companion object { private val mutex = Mutex() }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf { !it.isNaN() && it.isFinite() }
    }
}
