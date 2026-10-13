package de.tbhelfer.ui.bautagebuch

import android.content.Context

/**
 * Leichte lokale Zusatzbeziehung Foto -> btbId ohne Room-Migration.
 *
 * Moderne BTB-Fotos werden beim Erfassen genau einem BTB zugeordnet. Historische Fotos ohne
 * Zuordnung bleiben absichtlich ohne Eintrag und koennen ueber den Legacy-Zeitfenster-Fallback
 * weiter verwendet werden.
 */
object BtbFotoZuordnungSpeicher {

    private const val PREFS = "btb_foto_zuordnung"

    fun zuordnen(context: Context, fotoId: Long, btbId: String?) {
        if (fotoId <= 0L) return
        val id = btbId?.trim().orEmpty()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (id.isBlank()) prefs.edit().remove(fotoId.toString()).apply()
        else prefs.edit().putString(fotoId.toString(), id).apply()
    }

    fun btbId(context: Context, fotoId: Long): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(fotoId.toString(), null)
            ?.takeIf { it.isNotBlank() }

    fun gehoertZu(context: Context, fotoId: Long, btbId: String): Boolean =
        btbId(context, fotoId) == btbId

    /**
     * IDs moderner Fotos, die bereits eindeutig einem ANDEREN BTB zugeordnet sind.
     * Diese duerfen weder im Zaehler noch in der Fotoauswahl des aktuellen BTB auftauchen.
     */
    fun fotoIdsFremderBtbs(context: Context, aktuellerBtbId: String?): Set<Long> {
        val current = aktuellerBtbId?.trim().orEmpty()
        if (current.isBlank()) return emptySet()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries.mapNotNull { (key, value) ->
            val fotoId = key.toLongOrNull() ?: return@mapNotNull null
            val assigned = value as? String ?: return@mapNotNull null
            fotoId.takeIf { assigned.isNotBlank() && assigned != current }
        }.toSet()
    }

    /** Alle explizit dieser btbId zugeordneten modernen Foto-IDs. */
    fun fotoIdsFuerBtb(context: Context, btbId: String?): Set<Long> {
        val current = btbId?.trim().orEmpty()
        if (current.isBlank()) return emptySet()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries.mapNotNull { (key, value) ->
            val fotoId = key.toLongOrNull() ?: return@mapNotNull null
            val assigned = value as? String ?: return@mapNotNull null
            fotoId.takeIf { assigned == current }
        }.toSet()
    }

    fun entfernen(context: Context, fotoId: Long) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(fotoId.toString()).apply()
    }

    /**
     * Loest beim bewussten Loeschen eines BTB nur die Zusatzbeziehung. Die echten Foto-Dateien und
     * Room-Eintraege bleiben absichtlich in der Baustelle erhalten und werden nicht mitgeloescht.
     */
    fun btbLoesen(context: Context, btbId: String?): Int {
        val target = btbId?.trim().orEmpty()
        if (target.isBlank()) return 0
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keys = prefs.all.entries.mapNotNull { (key, value) ->
            key.takeIf { (value as? String) == target }
        }
        if (keys.isEmpty()) return 0
        val edit = prefs.edit()
        keys.forEach(edit::remove)
        edit.apply()
        return keys.size
    }

    /**
     * Unzugeordnete Altbilder bleiben erlaubt. Sobald ein Bild modern zugeordnet ist, darf es nur
     * noch im exakt passenden BTB verwendet werden.
     */
    fun darfImBtbVerwendetWerden(context: Context, fotoId: Long, btbId: String?): Boolean {
        val assigned = btbId(context, fotoId) ?: return true
        return !btbId.isNullOrBlank() && assigned == btbId
    }
}
