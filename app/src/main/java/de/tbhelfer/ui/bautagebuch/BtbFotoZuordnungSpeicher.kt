package de.tbhelfer.ui.bautagebuch

import android.content.Context

/**
 * Stabile lokale Zuordnung Foto-ID -> btbId, ohne Änderung des bestehenden Room-Fotoschemas.
 * Mängel-/Abnahmebeziehungen bleiben davon unabhängig bestehen.
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

    fun entfernen(context: Context, fotoId: Long) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(fotoId.toString()).apply()
    }

    /**
     * Moderne zugeordnete Fotos dürfen nur im eigenen BTB erscheinen. Unzugeordnete Altbilder
     * werden als Legacy-Fallback zugelassen, bis sie einmal bewusst einem BTB zugeordnet wurden.
     */
    fun darfImBtbVerwendetWerden(context: Context, fotoId: Long, btbId: String?): Boolean {
        val assigned = btbId(context, fotoId) ?: return true
        return !btbId.isNullOrBlank() && assigned == btbId
    }
}
