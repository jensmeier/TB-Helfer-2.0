package de.tbhelfer.streckenobjekte

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dauerhafte, bewusst kleine Speicherung fuer manuell erfasste Weichen und DKW/Kreuzungen.
 *
 * Die importierte TRA bleibt unveraendert. Gespeichert werden nur die betrieblichen Objektpunkte,
 * eine optionale TRA-Zuordnung und der Status, ob auf dieser TRA eine verwendbare Kilometrierung
 * vorhanden ist. Die Struktur kann spaeter ohne Aenderung der Fachlogik auf den Server synchronisiert
 * oder in eine eigene Room-Tabelle ueberfuehrt werden.
 */
object StreckenObjektSpeicher {

    enum class Typ { WEICHE, DKW_KREUZUNG }

    data class Punkt(
        val rolle: String,
        val lat: Double? = null,
        val lon: Double? = null,
        val accuracyM: Float? = null,
        val km: Double? = null,
        val kmManuell: Boolean = false,
        val achseId: Long? = null,
        val achseName: String? = null,
        val station: Double? = null,
        val achsabstandM: Double? = null,
        val vermarkungName: String? = null,
        val vermarkungArt: String? = null,
        val vermarkungQuelleDatei: String? = null,
        val vermarkungX: Double? = null,
        val vermarkungY: Double? = null
    )

    data class Objekt(
        val id: String = UUID.randomUUID().toString(),
        val betriebsstelle: String,
        val bezeichnung: String,
        val typ: Typ,
        val punkte: List<Punkt> = emptyList(),
        /** WEICHE: WA-WE oder WA-WEZ; DKW/Kreuzung: D-A oder C-B. */
        val traAchsenPaar: String? = null,
        val traAchseId: Long? = null,
        val traAchseName: String? = null,
        val traHatKm: Boolean = false,
        val aktualisiertAmEpochMs: Long = System.currentTimeMillis()
    )

    data class NaherPunkt(
        val objekt: Objekt,
        val punkt: Punkt,
        val abstandM: Double
    )

    private const val PREFS = "strecken_objekte_v1"
    private const val KEY_JSON = "objekte_json"
    private val lock = Any()

    fun alle(context: Context): List<Objekt> = synchronized(lock) {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null)
            .orEmpty()
        if (raw.isBlank()) return@synchronized emptyList()
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.toObjektOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
            .sortedWith(compareBy<Objekt> { it.betriebsstelle.lowercase(Locale.GERMANY) }
                .thenBy { it.typ.name }
                .thenBy { it.bezeichnung.lowercase(Locale.GERMANY) })
    }

    fun objektMitId(context: Context, id: String): Objekt? = alle(context).firstOrNull { it.id == id }

    fun findePassend(
        context: Context,
        betriebsstelle: String?,
        bezeichnung: String?,
        typ: Typ
    ): Objekt? {
        val b = normalizeBetriebsstelle(betriebsstelle)
        val o = BaustellenObjektLogik.normalisierteObjektKennung(bezeichnung)
        if (o.isBlank()) return null
        return alle(context)
            .filter { it.typ == typ }
            .filter { b.isBlank() || normalizeBetriebsstelle(it.betriebsstelle) == b }
            .firstOrNull { BaustellenObjektLogik.normalisierteObjektKennung(it.bezeichnung) == o }
    }

    fun hatVerwendbareKilometrierung(
        context: Context,
        betriebsstelle: String?,
        bezeichnung: String?,
        typ: Typ
    ): Boolean = findePassend(context, betriebsstelle, bezeichnung, typ)?.traHatKm == true

    fun speichern(context: Context, objekt: Objekt) = synchronized(lock) {
        val liste = alle(context).toMutableList()
        val index = liste.indexOfFirst { it.id == objekt.id }
        val neu = objekt.copy(aktualisiertAmEpochMs = System.currentTimeMillis())
        if (index >= 0) liste[index] = neu else liste += neu
        schreibe(context, liste)
    }

    fun loeschen(context: Context, id: String) = synchronized(lock) {
        schreibe(context, alle(context).filterNot { it.id == id })
    }

    fun findeNahePunkte(
        context: Context,
        lat: Double,
        lon: Double,
        maxAbstandM: Double = 5.0,
        ausgenommenObjektId: String? = null
    ): List<NaherPunkt> = alle(context)
        .asSequence()
        .filter { it.id != ausgenommenObjektId }
        .flatMap { objekt -> objekt.punkte.asSequence().map { punkt -> objekt to punkt } }
        .mapNotNull { (objekt, punkt) ->
            val pLat = punkt.lat ?: return@mapNotNull null
            val pLon = punkt.lon ?: return@mapNotNull null
            NaherPunkt(objekt, punkt, distanzM(lat, lon, pLat, pLon))
        }
        .filter { it.abstandM <= maxAbstandM }
        .sortedBy { it.abstandM }
        .toList()

    private fun schreibe(context: Context, objekte: List<Objekt>) {
        val arr = JSONArray()
        objekte.forEach { arr.put(it.toJson()) }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, arr.toString())
            .apply()
    }

    private fun Objekt.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("betriebsstelle", betriebsstelle)
        put("bezeichnung", bezeichnung)
        put("typ", typ.name)
        put("traAchsenPaar", traAchsenPaar)
        put("traAchseId", traAchseId)
        put("traAchseName", traAchseName)
        put("traHatKm", traHatKm)
        put("aktualisiertAmEpochMs", aktualisiertAmEpochMs)
        put("punkte", JSONArray().apply { punkte.forEach { put(it.toJson()) } })
    }

    private fun Punkt.toJson(): JSONObject = JSONObject().apply {
        put("rolle", rolle)
        put("lat", lat)
        put("lon", lon)
        put("accuracyM", accuracyM?.toDouble())
        put("km", km)
        put("kmManuell", kmManuell)
        put("achseId", achseId)
        put("achseName", achseName)
        put("station", station)
        put("achsabstandM", achsabstandM)
        put("vermarkungName", vermarkungName)
        put("vermarkungArt", vermarkungArt)
        put("vermarkungQuelleDatei", vermarkungQuelleDatei)
        put("vermarkungX", vermarkungX)
        put("vermarkungY", vermarkungY)
    }

    private fun JSONObject.toObjektOrNull(): Objekt? = runCatching {
        val typ = Typ.valueOf(getString("typ"))
        val arr = optJSONArray("punkte") ?: JSONArray()
        val punkte = buildList {
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.toPunktOrNull()?.let(::add)
        }
        Objekt(
            id = optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            betriebsstelle = optString("betriebsstelle"),
            bezeichnung = optString("bezeichnung"),
            typ = typ,
            punkte = punkte,
            traAchsenPaar = optNullableString("traAchsenPaar"),
            traAchseId = optNullableLong("traAchseId"),
            traAchseName = optNullableString("traAchseName"),
            traHatKm = optBoolean("traHatKm", false),
            aktualisiertAmEpochMs = optLong("aktualisiertAmEpochMs", System.currentTimeMillis())
        )
    }.getOrNull()

    private fun JSONObject.toPunktOrNull(): Punkt? = runCatching {
        Punkt(
            rolle = getString("rolle"),
            lat = optNullableDouble("lat"),
            lon = optNullableDouble("lon"),
            accuracyM = optNullableDouble("accuracyM")?.toFloat(),
            km = optNullableDouble("km"),
            kmManuell = optBoolean("kmManuell", false),
            achseId = optNullableLong("achseId"),
            achseName = optNullableString("achseName"),
            station = optNullableDouble("station"),
            achsabstandM = optNullableDouble("achsabstandM"),
            vermarkungName = optNullableString("vermarkungName"),
            vermarkungArt = optNullableString("vermarkungArt"),
            vermarkungQuelleDatei = optNullableString("vermarkungQuelleDatei"),
            vermarkungX = optNullableDouble("vermarkungX"),
            vermarkungY = optNullableDouble("vermarkungY")
        )
    }.getOrNull()

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun normalizeBetriebsstelle(value: String?): String {
        val raw = value?.trim().orEmpty().uppercase(Locale.GERMANY)
        if (raw.isBlank()) return ""
        Regex("\\(([A-Z0-9ÄÖÜ_-]{2,12})\\)")
            .find(raw)?.groupValues?.getOrNull(1)?.let { return normalizeText(it) }
        Regex("^([A-Z0-9ÄÖÜ_-]{2,12})\\s*(?:[·–-]|\\()")
            .find(raw)?.groupValues?.getOrNull(1)?.let { return normalizeText(it) }
        return normalizeText(raw)
    }

    private fun normalizeText(value: String?): String = value?.trim().orEmpty()
        .uppercase(Locale.GERMANY)
        .replace(Regex("\\s+"), "")

    private fun distanzM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
