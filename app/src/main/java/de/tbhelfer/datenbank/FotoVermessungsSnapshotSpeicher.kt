package de.tbhelfer.datenbank

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/**
 * Teststand 009: unveraenderlicher Vermessungs-Schnappschuss zum Aufnahmezeitpunkt.
 *
 * Der Foto-Datensatz selbst bleibt kompatibel zum bestaetigten Room-v19-Schema.
 * Zusatzdaten, die spaeter fuer BTB/PDF benoetigt werden (GPS-Genauigkeit, Strecke,
 * Achse und KM-Quelle), werden fotoId-bezogen persistent gespeichert.
 *
 * Damit bleibt auch bei einem Prozess-Neustart zwischen Kamera und Foto-Maske der
 * Aufnahme-Kontext erhalten. Eine spaetere Schema-/Sync-Migration kann diese Daten
 * verlustfrei in den globalen Foto-/NAS-Bestand uebernehmen.
 */
object FotoVermessungsSnapshotSpeicher {

    private const val PREFS = "tbhelfer_foto_vermessung_snapshot_v1"
    private const val KEY_PENDING = "pending"
    private const val FOTO_PREFIX = "foto_"
    private const val MAX_PENDING_ALTER_MS = 12L * 60L * 60L * 1000L

    const val KM_QUELLE_ORIGINAL = "ORIGINAL"
    const val KM_QUELLE_MANUELL = "MANUELL"

    data class Pending(
        val baustelleId: Long,
        val gpsLat: Double,
        val gpsLon: Double,
        val gpsAccuracyM: Double?,
        val kmVorschlag: Double?,
        val kmQuelleRoh: String?,
        val strecke: String?,
        val achseId: Long?,
        val achseName: String?,
        val achseTyp: String?,
        val achsabstandM: Double?,
        val aufgenommenAmEpochMs: Long = System.currentTimeMillis()
    )

    data class Snapshot(
        val fotoId: Long,
        val gpsAccuracyM: Double?,
        val kmVorschlag: Double?,
        val kmGespeichert: Double?,
        val kmQuelle: String?,
        val kmQuelleRoh: String?,
        val strecke: String?,
        val achseId: Long?,
        val achseName: String?,
        val achseTyp: String?,
        val achsabstandM: Double?,
        val aufgenommenAmEpochMs: Long
    )

    fun speicherePending(context: Context, pending: Pending) {
        prefs(context).edit().putString(KEY_PENDING, pending.toJson().toString()).apply()
    }

    fun passendesPending(context: Context, foto: FotoEntity): Pending? {
        val raw = prefs(context).getString(KEY_PENDING, null) ?: return null
        val pending = runCatching { pendingAusJson(JSONObject(raw)) }.getOrNull() ?: return null
        val alter = System.currentTimeMillis() - pending.aufgenommenAmEpochMs
        if (alter !in 0..MAX_PENDING_ALTER_MS) return null
        if (pending.baustelleId != foto.baustelleId) return null

        // Die Koordinaten stammen beim normalen Kamera-Flow aus exakt demselben Fix.
        // Etwas Toleranz verhindert Probleme durch Format-/Rundungsunterschiede.
        if (abs(pending.gpsLat - foto.gpsLat) > 0.001 || abs(pending.gpsLon - foto.gpsLon) > 0.001) {
            return null
        }
        return pending
    }

    fun kmIstManuell(foto: FotoEntity, pending: Pending?): Boolean {
        val km = foto.km ?: return false

        // Ohne 009-Aufnahme-Snapshot bleibt das bisherige Verhalten fuer Alt-/Sonderflows
        // erhalten. Bei einem echten 009-Snapshot entscheidet dagegen die Herkunft des
        // Vorschlags und eine Nutzer-Aenderung - NICHT der Achsabstand. Ein originaler
        // App-Vorschlag bleibt also ORIGINAL, auch wenn die Foto-Maske wegen groesserem
        // Achsabstand zusaetzlich eine Pruefwarnung zeigt.
        if (pending == null) return foto.kmManuell

        val roh = pending.kmQuelleRoh.orEmpty().uppercase(Locale.GERMANY)
        if (roh.contains("MANUELL")) return true

        val vorschlag = pending.kmVorschlag ?: return true
        // Foto-Maske zeigt drei Nachkommastellen (1 m). Unterschiede groesser als
        // 0,5 m gelten deshalb sicher als bewusste manuelle Aenderung.
        return abs(km - vorschlag) > 0.0005
    }

    fun speichereFuerFoto(context: Context, fotoId: Long, foto: FotoEntity, pending: Pending?) {
        val manuell = foto.km?.let { kmIstManuell(foto, pending) } ?: false
        val quelle = foto.km?.let { if (manuell) KM_QUELLE_MANUELL else KM_QUELLE_ORIGINAL }
        val snapshot = Snapshot(
            fotoId = fotoId,
            gpsAccuracyM = pending?.gpsAccuracyM,
            kmVorschlag = pending?.kmVorschlag,
            kmGespeichert = foto.km,
            kmQuelle = quelle,
            kmQuelleRoh = pending?.kmQuelleRoh,
            strecke = pending?.strecke,
            achseId = pending?.achseId,
            achseName = pending?.achseName,
            achseTyp = pending?.achseTyp,
            achsabstandM = pending?.achsabstandM ?: foto.achsabstandM,
            aufgenommenAmEpochMs = pending?.aufgenommenAmEpochMs ?: foto.erstelltAmEpochMs
        )
        prefs(context).edit()
            .putString(FOTO_PREFIX + fotoId, snapshot.toJson().toString())
            .remove(KEY_PENDING)
            .apply()
    }

    fun laden(context: Context, fotoId: Long): Snapshot? {
        val raw = prefs(context).getString(FOTO_PREFIX + fotoId, null) ?: return null
        return runCatching { snapshotAusJson(JSONObject(raw)) }.getOrNull()
    }

    fun loeschen(context: Context, fotoId: Long) {
        prefs(context).edit().remove(FOTO_PREFIX + fotoId).apply()
    }

    fun loeschen(context: Context, fotoIds: Collection<Long>) {
        if (fotoIds.isEmpty()) return
        prefs(context).edit().also { editor ->
            fotoIds.forEach { editor.remove(FOTO_PREFIX + it) }
        }.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun Pending.toJson() = JSONObject().apply {
        put("baustelleId", baustelleId)
        put("gpsLat", gpsLat)
        put("gpsLon", gpsLon)
        putNullable("gpsAccuracyM", gpsAccuracyM)
        putNullable("kmVorschlag", kmVorschlag)
        putNullable("kmQuelleRoh", kmQuelleRoh)
        putNullable("strecke", strecke)
        putNullable("achseId", achseId)
        putNullable("achseName", achseName)
        putNullable("achseTyp", achseTyp)
        putNullable("achsabstandM", achsabstandM)
        put("aufgenommenAmEpochMs", aufgenommenAmEpochMs)
    }

    private fun Snapshot.toJson() = JSONObject().apply {
        put("fotoId", fotoId)
        putNullable("gpsAccuracyM", gpsAccuracyM)
        putNullable("kmVorschlag", kmVorschlag)
        putNullable("kmGespeichert", kmGespeichert)
        putNullable("kmQuelle", kmQuelle)
        putNullable("kmQuelleRoh", kmQuelleRoh)
        putNullable("strecke", strecke)
        putNullable("achseId", achseId)
        putNullable("achseName", achseName)
        putNullable("achseTyp", achseTyp)
        putNullable("achsabstandM", achsabstandM)
        put("aufgenommenAmEpochMs", aufgenommenAmEpochMs)
    }

    private fun pendingAusJson(o: JSONObject) = Pending(
        baustelleId = o.getLong("baustelleId"),
        gpsLat = o.getDouble("gpsLat"),
        gpsLon = o.getDouble("gpsLon"),
        gpsAccuracyM = o.optDoubleOrNull("gpsAccuracyM"),
        kmVorschlag = o.optDoubleOrNull("kmVorschlag"),
        kmQuelleRoh = o.optStringOrNull("kmQuelleRoh"),
        strecke = o.optStringOrNull("strecke"),
        achseId = o.optLongOrNull("achseId"),
        achseName = o.optStringOrNull("achseName"),
        achseTyp = o.optStringOrNull("achseTyp"),
        achsabstandM = o.optDoubleOrNull("achsabstandM"),
        aufgenommenAmEpochMs = o.optLong("aufgenommenAmEpochMs", System.currentTimeMillis())
    )

    private fun snapshotAusJson(o: JSONObject) = Snapshot(
        fotoId = o.getLong("fotoId"),
        gpsAccuracyM = o.optDoubleOrNull("gpsAccuracyM"),
        kmVorschlag = o.optDoubleOrNull("kmVorschlag"),
        kmGespeichert = o.optDoubleOrNull("kmGespeichert"),
        kmQuelle = o.optStringOrNull("kmQuelle"),
        kmQuelleRoh = o.optStringOrNull("kmQuelleRoh"),
        strecke = o.optStringOrNull("strecke"),
        achseId = o.optLongOrNull("achseId"),
        achseName = o.optStringOrNull("achseName"),
        achseTyp = o.optStringOrNull("achseTyp"),
        achsabstandM = o.optDoubleOrNull("achsabstandM"),
        aufgenommenAmEpochMs = o.optLong("aufgenommenAmEpochMs", 0L)
    )

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)
}
