package de.tbhelfer.ui.baustelle

import android.content.Context
import org.json.JSONObject

/**
 * Status- und Zuordnungsspeicher fuer die Mängelliste.
 *
 * Fachregel ab Baustellen-Sync: ERLEDIGT ist endgültig. Ein erledigter Mangel darf weder über
 * den Statusdialog noch durch ein später zugeordnetes Foto wieder auf OFFEN/IN_ARBEIT gesetzt werden.
 */
object MangelStatusSpeicher {
    enum class Status { OFFEN, IN_ARBEIT, ERLEDIGT, ABGELEHNT }
    enum class FotoArt { MANGEL, IN_ARBEIT, ERLEDIGT }

    data class Wert(
        val status: Status = Status.OFFEN,
        val kommentar: String = "",
        val erledigtAmEpochMs: Long? = null,
        val nachweisFotoId: Long? = null
    )

    data class FotoZuordnung(
        val fotoId: Long,
        val mangelKey: String,
        val fotoArt: FotoArt = FotoArt.MANGEL,
        val erstelltAmEpochMs: Long = System.currentTimeMillis()
    )

    private const val PREF = "tb_helfer_maengel_status_v1"
    private const val PREF_FOTOS = "tb_helfer_maengel_fotos_v1"

    fun laden(context: Context, key: String): Wert {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key, null) ?: return Wert()
        return runCatching {
            val o = JSONObject(raw)
            Wert(
                status = runCatching { Status.valueOf(o.optString("status", Status.OFFEN.name)) }.getOrDefault(Status.OFFEN),
                kommentar = o.optString("kommentar", ""),
                erledigtAmEpochMs = if (o.has("erledigtAmEpochMs") && !o.isNull("erledigtAmEpochMs")) o.optLong("erledigtAmEpochMs") else null,
                nachweisFotoId = if (o.has("nachweisFotoId") && !o.isNull("nachweisFotoId")) o.optLong("nachweisFotoId") else null
            )
        }.getOrDefault(Wert())
    }

    fun speichern(context: Context, key: String, wert: Wert) {
        val alt = laden(context, key)
        val final = if (alt.status == Status.ERLEDIGT && wert.status != Status.ERLEDIGT) {
            alt.copy(
                kommentar = wert.kommentar.ifBlank { alt.kommentar },
                nachweisFotoId = wert.nachweisFotoId ?: alt.nachweisFotoId
            )
        } else {
            wert
        }

        val o = JSONObject().apply {
            put("status", final.status.name)
            put("kommentar", final.kommentar)
            if (final.erledigtAmEpochMs != null) put("erledigtAmEpochMs", final.erledigtAmEpochMs) else put("erledigtAmEpochMs", JSONObject.NULL)
            if (final.nachweisFotoId != null) put("nachweisFotoId", final.nachweisFotoId) else put("nachweisFotoId", JSONObject.NULL)
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(key, o.toString()).apply()
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
    }

    fun fotoArtZuStatus(art: FotoArt): Status = when (art) {
        FotoArt.MANGEL -> Status.OFFEN
        FotoArt.IN_ARBEIT -> Status.IN_ARBEIT
        FotoArt.ERLEDIGT -> Status.ERLEDIGT
    }

    fun statusNachFotoArt(old: Wert, art: FotoArt): Wert {
        if (old.status == Status.ERLEDIGT) return old
        return when (art) {
            FotoArt.MANGEL -> old.copy(status = if (old.status == Status.ABGELEHNT) old.status else Status.OFFEN)
            FotoArt.IN_ARBEIT -> old.copy(status = Status.IN_ARBEIT, erledigtAmEpochMs = null)
            FotoArt.ERLEDIGT -> old.copy(status = Status.ERLEDIGT, erledigtAmEpochMs = old.erledigtAmEpochMs ?: System.currentTimeMillis())
        }
    }

    fun fotoZuordnen(context: Context, mangelKey: String, fotoId: Long, fotoArt: FotoArt) {
        if (mangelKey.isBlank() || fotoId <= 0L) return
        val o = JSONObject().apply {
            put("fotoId", fotoId)
            put("mangelKey", mangelKey)
            put("fotoArt", fotoArt.name)
            put("erstelltAmEpochMs", System.currentTimeMillis())
        }
        context.getSharedPreferences(PREF_FOTOS, Context.MODE_PRIVATE)
            .edit()
            .putString(fotoId.toString(), o.toString())
            .apply()

        val old = laden(context, mangelKey)
        val updated = statusNachFotoArt(old, fotoArt)
            .copy(nachweisFotoId = if (fotoArt == FotoArt.ERLEDIGT) fotoId else old.nachweisFotoId)
        speichern(context, mangelKey, updated)
    }

    fun fotoZuordnung(context: Context, fotoId: Long): FotoZuordnung? {
        val raw = context.getSharedPreferences(PREF_FOTOS, Context.MODE_PRIVATE).getString(fotoId.toString(), null) ?: return null
        return parseZuordnung(raw)
    }

    fun fotoArtFuerFoto(context: Context, fotoId: Long): FotoArt? = fotoZuordnung(context, fotoId)?.fotoArt

    fun alleFotoZuordnungen(context: Context): List<FotoZuordnung> {
        return context.getSharedPreferences(PREF_FOTOS, Context.MODE_PRIVATE)
            .all
            .values
            .mapNotNull { it as? String }
            .mapNotNull { parseZuordnung(it) }
    }

    fun fotoZuordnungenFuerMangel(context: Context, mangelKey: String): List<FotoZuordnung> {
        return alleFotoZuordnungen(context).filter { it.mangelKey == mangelKey }
    }

    private fun parseZuordnung(raw: String): FotoZuordnung? {
        return runCatching {
            val o = JSONObject(raw)
            FotoZuordnung(
                fotoId = o.optLong("fotoId", -1L),
                mangelKey = o.optString("mangelKey", ""),
                fotoArt = runCatching { FotoArt.valueOf(o.optString("fotoArt", FotoArt.MANGEL.name)) }.getOrDefault(FotoArt.MANGEL),
                erstelltAmEpochMs = o.optLong("erstelltAmEpochMs", System.currentTimeMillis())
            )
        }.getOrNull()?.takeIf { it.fotoId > 0L && it.mangelKey.isNotBlank() }
    }
}
