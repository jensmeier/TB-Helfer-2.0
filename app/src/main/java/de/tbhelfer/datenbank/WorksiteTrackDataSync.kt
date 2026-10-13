package de.tbhelfer.datenbank

import de.tbhelfer.server.WorksiteTrackDataApi
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Spiegelt den fuer den mobilen Betrieb benoetigten Streckendatenstand einer Baustelle.
 *
 * LEAD: lokale, bereits geparste/berechnete Daten -> Server.
 * Helfer: Server-Snapshot -> lokale Fachdatenbank.
 *
 * Rohdateien werden niemals gelesen oder hochgeladen. Ein Helfer-Snapshot ersetzt nur die
 * Streckendaten der gebundenen Baustelle; andere lokale Baustellen bleiben unangetastet.
 */
class WorksiteTrackDataSync(
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

    suspend fun synchronize(token: String): Result {
        var uploaded = 0
        var applied = 0
        var supported = false

        val contexts = bindingRepository.contextsWithBindings()
        for (bound in contexts) {
            val local = bound.localWorksite ?: continue
            val serverId = bound.context.worksiteServerId
            val isLead = bound.context.memberRole.equals("LEAD", ignoreCase = true)

            if (isLead) {
                val datasets = buildLocalDatasets(serverId, local.altBaustelleId)
                when (val put = api.replace(token, serverId, datasets)) {
                    WorksiteTrackDataApi.PutResult.Unsupported -> Unit
                    WorksiteTrackDataApi.PutResult.SessionInvalid -> return Result.SessionInvalid
                    is WorksiteTrackDataApi.PutResult.Error -> return Result.Error(put.message)
                    WorksiteTrackDataApi.PutResult.Success -> {
                        supported = true
                        uploaded += datasets.size
                    }
                }
            } else {
                when (val get = api.load(token, serverId)) {
                    WorksiteTrackDataApi.Result.Unsupported -> Unit
                    WorksiteTrackDataApi.Result.SessionInvalid -> return Result.SessionInvalid
                    is WorksiteTrackDataApi.Result.Error -> return Result.Error(get.message)
                    is WorksiteTrackDataApi.Result.Success -> {
                        supported = true
                        applied += applyServerDatasets(local.altBaustelleId, get.datasets)
                    }
                }
            }
        }

        return if (supported) Result.Success(Summary(uploaded, applied)) else Result.Unsupported
    }

    private suspend fun buildLocalDatasets(
        worksiteServerId: String,
        baustelleId: Long
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
                payloadJson = payload.toString()
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
        result += WorksiteTrackDataApi.Dataset(
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
    ): Int {
        // Helfer spiegeln fuer diese Baustelle den autoritativen Server-Snapshot. Achsen anderer
        // Baustellen und alle Originaldateien auf dem NAS sind hiervon nicht betroffen.
        trassenDao.alleAchsen()
            .filter { it.baustelleId == baustelleId }
            .forEach { trassenDao.achseLoeschen(it.id) }
        streckenpunktDao.loeschenFuerBaustelle(baustelleId)

        var count = 0
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
            val axisId = trassenDao.achseEinfuegen(axis)
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
            count++
        }

        datasets.firstOrNull { it.datasetKind.equals("STRECKENPUNKTE", true) }?.let { dataset ->
            val array = runCatching { JSONObject(dataset.payloadJson).optJSONArray("points") }.getOrNull() ?: JSONArray()
            for (i in 0 until array.length()) {
                val p = array.optJSONObject(i) ?: continue
                val km = p.optNullableDouble("km") ?: continue
                val lat = p.optNullableDouble("gps_lat") ?: continue
                val lon = p.optNullableDouble("gps_lon") ?: continue
                val typ = runCatching { StreckenpunktTyp.valueOf(p.optString("typ")) }.getOrDefault(StreckenpunktTyp.EXAKT)
                streckenpunktDao.einfuegen(
                    StreckenpunktEntity(
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
                )
            }
            count++
        }
        return count
    }

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
