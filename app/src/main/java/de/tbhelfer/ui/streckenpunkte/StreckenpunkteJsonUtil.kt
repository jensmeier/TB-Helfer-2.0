package de.tbhelfer.ui.streckenpunkte

import android.content.Context
import de.tbhelfer.datenbank.StreckenpunktEntity
import de.tbhelfer.datenbank.StreckenpunktTyp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StreckenpunktExportSession(
    val dateiName: String,
    val absolutePath: String,
    val exportiertAmEpochMs: Long,
    val baustellenname: String,
    val punktAnzahl: Int,
    val gpsAnzahl: Int,
    val formatVersion: Int,
    val dateiVorhanden: Boolean
)

object StreckenpunkteJsonUtil {

    private const val EXPORT_INDEX_DATEI = "export_index.json"
    private const val EXPORT_FORMAT_VERSION = 3

    fun exportiereJson(
        context: Context,
        baustellenname: String?,
        punkte: List<StreckenpunktEntity>,
        exportLabel: String? = null
    ): File {
        val jetzt = System.currentTimeMillis()
        val sortiertePunkte = punkte.sortedWith(compareBy<StreckenpunktEntity> { if (hatKm(it)) it.km else Double.MAX_VALUE }.thenBy { it.kmSuffix })
        val gpsAnzahl = sortiertePunkte.count { hatGps(it.gpsLat, it.gpsLon) }
        val root = baueExportRoot(
            baustellenname = baustellenname,
            exportiertAmEpochMs = jetzt,
            punkte = sortiertePunkte,
            gpsAnzahl = gpsAnzahl,
            exportKontext = exportLabel
        )

        val datei = exportDatei(context, exportLabel ?: baustellenname, suffix = null)
        datei.writeText(root.toString(2))
        speichereExportSession(
            context = context,
            session = StreckenpunktExportSession(
                dateiName = datei.name,
                absolutePath = datei.absolutePath,
                exportiertAmEpochMs = jetzt,
                baustellenname = baustellenname?.ifBlank { "-" } ?: "-",
                punktAnzahl = sortiertePunkte.size,
                gpsAnzahl = gpsAnzahl,
                formatVersion = EXPORT_FORMAT_VERSION,
                dateiVorhanden = true
            )
        )
        return datei
    }

    fun exportiereSessionErneut(context: Context, session: StreckenpunktExportSession): File {
        val quelle = File(session.absolutePath)
        require(quelle.exists()) { "Exportdatei nicht gefunden: ${session.absolutePath}" }

        val jetzt = System.currentTimeMillis()
        val root = JSONObject(quelle.readText())
        val meta = root.optJSONObject("meta") ?: JSONObject().also { root.put("meta", it) }
        meta.put("baustelle", session.baustellenname)
        meta.put("exportiertAm", isoZeit(jetzt))
        meta.put("exportiertAmEpochMs", jetzt)
        meta.put("punktAnzahl", session.punktAnzahl)
        meta.put("mitGpsAnzahl", session.gpsAnzahl)
        root.put("version", maxOf(root.optInt("version", EXPORT_FORMAT_VERSION), EXPORT_FORMAT_VERSION))

        val ziel = exportDatei(context, session.baustellenname, suffix = "erneut")
        ziel.writeText(root.toString(2))

        speichereExportSession(
            context = context,
            session = session.copy(
                dateiName = ziel.name,
                absolutePath = ziel.absolutePath,
                exportiertAmEpochMs = jetzt,
                formatVersion = root.optInt("version", EXPORT_FORMAT_VERSION),
                dateiVorhanden = true
            )
        )
        return ziel
    }


    fun exportiereAutomatikSession(
        context: Context,
        baustellenname: String?,
        exportLabel: String?,
        snapshot: AutomatikSessionSnapshot
    ): File {
        val exportiertAm = System.currentTimeMillis()
        val root = JSONObject().apply {
            put("version", 1)
            put(
                "meta",
                JSONObject().apply {
                    put("modul", "streckenpunkte_automatik")
                    put("baustelle", baustellenname ?: "-")
                    exportLabel?.takeIf { it.isNotBlank() }?.let { put("kontext", it) }
                    put("exportiertAm", isoZeit(exportiertAm))
                    put("exportiertAmEpochMs", exportiertAm)
                }
            )
            put(
                "session",
                JSONObject().apply {
                    put("sessionTitel", snapshot.sessionTitel)
                    put("startedAtEpochMs", snapshot.startedAtEpochMs)
                    put("stoppedAtEpochMs", snapshot.stoppedAtEpochMs)
                    put("gestartetAm", isoZeit(snapshot.startedAtEpochMs))
                    put("gestopptAm", isoZeit(snapshot.stoppedAtEpochMs))
                    put("streckennummer", snapshot.streckennummer)
                    put("bahnhof", snapshot.bahnhof)
                    put("gleis", snapshot.gleis)
                    put("weiche", snapshot.weiche)
                    put("trackpunktAnzahl", snapshot.trackpunkte.size)
                    put("unsichereTrackpunktAnzahl", snapshot.unsichereTrackpunkte.size)
                    put("ankerAnzahl", snapshot.anker.size)
                    put("gesamtDistanzM", snapshot.gesamtDistanzM)
                    put("gpsFixAnzahl", snapshot.gpsFixAnzahl)
                    put("gruenAnzahl", snapshot.gruenAnzahl)
                    put("gelbAnzahl", snapshot.gelbAnzahl)
                    put("rotAnzahl", snapshot.rotAnzahl)
                }
            )
            put(
                "trackpunkte",
                JSONArray().apply {
                    snapshot.trackpunkte.forEach { punkt ->
                        put(
                            JSONObject().apply {
                                put("lat", punkt.lat)
                                put("lon", punkt.lon)
                                punkt.altM?.let { put("altM", it) }
                                punkt.accuracyM?.let { put("accuracyM", it) }
                                put("epochMs", punkt.epochMs)
                                punkt.kmVorschlag?.let { put("kmVorschlag", it) }
                                punkt.kmSuffixVorschlag?.let { put("kmSuffixVorschlag", it) }
                                punkt.indikator?.let { put("indikator", it.name) }
                                punkt.achsabstandM?.let { put("achsabstandM", it) }
                                put("istWeichenmodus", punkt.istWeichenmodus)
                                punkt.headingDeg?.let { put("headingDeg", it) }
                                punkt.schritteSeitStart?.let { put("schritteSeitStart", it) }
                                put("istUnsicher", punkt.istUnsicher)
                                punkt.fortschrittM?.let { put("fortschrittM", it) }
                                punkt.seitenabstandM?.let { put("seitenabstandM", it) }
                            }
                        )
                    }
                }
            )
            put(
                "unsichereTrackpunkte",
                JSONArray().apply {
                    snapshot.unsichereTrackpunkte.forEach { punkt ->
                        put(
                            JSONObject().apply {
                                put("lat", punkt.lat)
                                put("lon", punkt.lon)
                                punkt.altM?.let { put("altM", it) }
                                punkt.accuracyM?.let { put("accuracyM", it) }
                                put("epochMs", punkt.epochMs)
                                punkt.kmVorschlag?.let { put("kmVorschlag", it) }
                                punkt.kmSuffixVorschlag?.let { put("kmSuffixVorschlag", it) }
                                punkt.indikator?.let { put("indikator", it.name) }
                                punkt.achsabstandM?.let { put("achsabstandM", it) }
                                put("istWeichenmodus", punkt.istWeichenmodus)
                                punkt.headingDeg?.let { put("headingDeg", it) }
                                punkt.schritteSeitStart?.let { put("schritteSeitStart", it) }
                                put("istUnsicher", punkt.istUnsicher)
                                punkt.fortschrittM?.let { put("fortschrittM", it) }
                                punkt.seitenabstandM?.let { put("seitenabstandM", it) }
                            }
                        )
                    }
                }
            )
            put(
                "anker",
                JSONArray().apply {
                    snapshot.anker.forEach { anker ->
                        put(
                            JSONObject().apply {
                                put("km", anker.km)
                                put("kmSuffix", anker.kmSuffix)
                                put("bezeichnung", anker.bezeichnung)
                                anker.notiz?.let { put("notiz", it) }
                                put("lat", anker.lat)
                                put("lon", anker.lon)
                                anker.altM?.let { put("altM", it) }
                                put("epochMs", anker.epochMs)
                            }
                        )
                    }
                }
            )
        }

        val datei = exportDatei(context, exportLabel ?: baustellenname, suffix = "automatik")
        datei.writeText(root.toString(2))
        return datei
    }

    fun ladeExportSessions(context: Context): List<StreckenpunktExportSession> {
        val exportDir = exportVerzeichnis(context)
        val bekannteDateien = exportDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            ?.associateBy { it.absolutePath }
            .orEmpty()

        val sessionsAusIndex = ladeExportSessionsAusIndex(exportDir).map { session ->
            session.copy(dateiVorhanden = bekannteDateien.containsKey(session.absolutePath))
        }

        if (sessionsAusIndex.isNotEmpty()) {
            return sessionsAusIndex.sortedByDescending { it.exportiertAmEpochMs }
        }

        return bekannteDateien.values.mapNotNull { parseSessionAusDatei(it) }
            .sortedByDescending { it.exportiertAmEpochMs }
    }

    fun baueObjektKennung(bereich: String?, objekt: String?): String? {
        val links = bereich?.trim().orEmpty()
        val rechts = objekt?.trim().orEmpty()
        return when {
            links.isBlank() && rechts.isBlank() -> null
            links.isBlank() -> rechts
            rechts.isBlank() -> links
            else -> "$links · $rechts"
        }
    }

    private fun baueExportRoot(
        baustellenname: String?,
        exportiertAmEpochMs: Long,
        punkte: List<StreckenpunktEntity>,
        gpsAnzahl: Int,
        exportKontext: String?
    ): JSONObject {
        val root = JSONObject()
        root.put("version", EXPORT_FORMAT_VERSION)
        root.put(
            "meta",
            JSONObject().apply {
                put("modul", "streckenpunkte")
                put("baustelle", baustellenname ?: "-")
                exportKontext?.takeIf { it.isNotBlank() }?.let { put("kontext", it) }
                put("exportiertAm", isoZeit(exportiertAmEpochMs))
                put("exportiertAmEpochMs", exportiertAmEpochMs)
                put("punktAnzahl", punkte.size)
                put("mitGpsAnzahl", gpsAnzahl)
                put("streckenAnzahl", punkte.mapNotNull { it.streckennummer }.distinct().size)
                put("bahnhoefeAnzahl", punkte.mapNotNull { it.bahnhof }.distinct().size)
            }
        )
        root.put(
            "zusammenfassung",
            JSONObject().apply {
                put("typen", jsonTypZaehler(punkte))
                val punkteMitKm = punkte.filter(::hatKm)
                put("kmMin", punkteMitKm.minOfOrNull { it.km })
                put("kmMax", punkteMitKm.maxOfOrNull { it.km })
            }
        )

        val array = JSONArray()
        punkte.forEach { punkt ->
            val obj = JSONObject()
            obj.put("id", punkt.id)
            if (hatKm(punkt)) obj.put("km", punkt.km) else obj.put("km", JSONObject.NULL)
            obj.put("kmSuffix", punkt.kmSuffix)
            obj.put("typ", punkt.typ.name)
            punkt.typFreitext?.let { obj.put("typFreitext", it) }
            punkt.streckennummer?.let { obj.put("streckennummer", it) }
            punkt.bahnhof?.let { obj.put("bahnhof", it) }
            punkt.gleis?.let { obj.put("gleis", it) }
            punkt.weiche?.let { obj.put("weiche", it) }
            obj.put("bezeichnung", punkt.bezeichnung)
            punkt.objektKennung?.let { obj.put("objektKennung", it) }
            punkt.notiz?.let { obj.put("notiz", it) }
            obj.put("gpsLat", punkt.gpsLat)
            obj.put("gpsLon", punkt.gpsLon)
            punkt.gpsAltM?.let { obj.put("gpsAltM", it) }
            obj.put("erstelltAmEpochMs", punkt.erstelltAmEpochMs)
            obj.put(
                "bereich",
                JSONObject().apply {
                    put("typ", exportBereichTyp(punkt))
                    put("streckennummer", punkt.streckennummer)
                    put("bahnhof", punkt.bahnhof)
                    put("gleis", punkt.gleis)
                    put("weiche", punkt.weiche)
                }
            )
            array.put(obj)
        }
        root.put("streckenpunkte", array)
        return root
    }

    private fun jsonTypZaehler(punkte: List<StreckenpunktEntity>): JSONObject {
        val result = JSONObject()
        StreckenpunktTyp.values().forEach { typ ->
            result.put(typ.name, punkte.count { it.typ == typ })
        }
        return result
    }

    private fun hatKm(punkt: StreckenpunktEntity): Boolean = punkt.km >= 0.0

    private fun exportBereichTyp(punkt: StreckenpunktEntity): String = when {
        !punkt.weiche.isNullOrBlank() -> "weiche"
        !punkt.gleis.isNullOrBlank() -> "gleis"
        !punkt.bahnhof.isNullOrBlank() -> "bahnhof"
        !punkt.streckennummer.isNullOrBlank() -> "strecke"
        else -> "frei"
    }

    private fun exportVerzeichnis(context: Context): File {
        return File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
    }

    private fun exportDatei(context: Context, baustellenname: String?, suffix: String?): File {
        val dateiName = buildString {
            append("streckenpunkte_")
            append(slug(baustellenname ?: "baustelle"))
            append("_")
            append(SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date()))
            suffix?.takeIf { it.isNotBlank() }?.let {
                append("_")
                append(it)
            }
            append(".json")
        }
        return File(exportVerzeichnis(context), dateiName)
    }

    private fun speichereExportSession(context: Context, session: StreckenpunktExportSession) {
        val exportDir = exportVerzeichnis(context)
        val indexDatei = File(exportDir, EXPORT_INDEX_DATEI)
        val bestehend = ladeExportSessionsAusIndex(exportDir)
            .filterNot { it.absolutePath == session.absolutePath }
            .toMutableList()
        bestehend.add(0, session)

        val root = JSONObject()
        root.put("version", 1)
        val array = JSONArray()
        bestehend.sortedByDescending { it.exportiertAmEpochMs }.take(100).forEach { eintrag ->
            val obj = JSONObject()
            obj.put("dateiName", eintrag.dateiName)
            obj.put("absolutePath", eintrag.absolutePath)
            obj.put("exportiertAmEpochMs", eintrag.exportiertAmEpochMs)
            obj.put("baustellenname", eintrag.baustellenname)
            obj.put("punktAnzahl", eintrag.punktAnzahl)
            obj.put("gpsAnzahl", eintrag.gpsAnzahl)
            obj.put("formatVersion", eintrag.formatVersion)
            array.put(obj)
        }
        root.put("sessions", array)
        indexDatei.writeText(root.toString(2))
    }

    private fun ladeExportSessionsAusIndex(exportDir: File): List<StreckenpunktExportSession> {
        val indexDatei = File(exportDir, EXPORT_INDEX_DATEI)
        if (!indexDatei.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(indexDatei.readText())
            val array = root.optJSONArray("sessions") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        StreckenpunktExportSession(
                            dateiName = obj.optString("dateiName").ifBlank {
                                File(obj.optString("absolutePath")).name.ifBlank { "streckenpunkte.json" }
                            },
                            absolutePath = obj.optString("absolutePath"),
                            exportiertAmEpochMs = obj.optLong("exportiertAmEpochMs", 0L),
                            baustellenname = obj.optString("baustellenname").ifBlank { "-" },
                            punktAnzahl = obj.optInt("punktAnzahl", 0),
                            gpsAnzahl = obj.optInt("gpsAnzahl", 0),
                            formatVersion = obj.optInt("formatVersion", EXPORT_FORMAT_VERSION),
                            dateiVorhanden = File(obj.optString("absolutePath")).exists()
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseSessionAusDatei(datei: File): StreckenpunktExportSession? {
        return runCatching {
            val root = JSONObject(datei.readText())
            val meta = root.optJSONObject("meta")
            val array = root.optJSONArray("streckenpunkte")
                ?: root.optJSONArray("punkte")
                ?: JSONArray()
            val gpsAnzahl = if (meta != null) {
                meta.optInt("mitGpsAnzahl", -1)
            } else {
                -1
            }
            StreckenpunktExportSession(
                dateiName = datei.name,
                absolutePath = datei.absolutePath,
                exportiertAmEpochMs = meta?.optLong("exportiertAmEpochMs")
                    ?: datei.lastModified(),
                baustellenname = meta?.optString("baustelle")
                    ?: root.optString("baustelle").ifBlank { "-" },
                punktAnzahl = meta?.optInt("punktAnzahl") ?: array.length(),
                gpsAnzahl = if (gpsAnzahl >= 0) gpsAnzahl else countGps(array),
                formatVersion = root.optInt("version", 2),
                dateiVorhanden = true
            )
        }.getOrNull()
    }

    private fun countGps(array: JSONArray): Int {
        var count = 0
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (hatGps(obj.optDouble("gpsLat", 0.0), obj.optDouble("gpsLon", 0.0))) {
                count++
            }
        }
        return count
    }

    private fun hatGps(lat: Double, lon: Double): Boolean = lat != 0.0 || lon != 0.0

    private fun slug(text: String): String {
        return text.lowercase(Locale.GERMANY)
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "baustelle" }
    }

    private fun isoZeit(epochMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).format(Date(epochMs))
    }
}
