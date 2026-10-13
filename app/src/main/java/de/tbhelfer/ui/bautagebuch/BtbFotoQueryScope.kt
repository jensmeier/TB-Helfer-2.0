package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.datenbank.FotoEntity

/**
 * Begrenzter Lese-Filter fuer den aktuell geoeffneten modernen BTB.
 *
 * Historische Fragmentteile fragen weiterhin alle Baustellenfotos ab und filtern teils nur nach
 * Datum/Uhrzeit. Solange ein moderner BTB geoeffnet ist, darf dieser Altpfad jedoch nur Fotos der
 * exakt aktiven btbId sehen. Allgemeine Baustellenfotos ohne btbId und Fotos anderer BTBs bleiben
 * dadurch aus BTB-Vorschau, Abschnittseditor und BTB-PDF heraus.
 */
object BtbFotoQueryScope {

    private data class Scope(
        val owner: String,
        val baustelleId: Long,
        val btbId: String
    )

    @Volatile
    private var scope: Scope? = null

    fun setzen(owner: String, baustelleId: Long, btbId: String?) {
        val id = btbId?.trim().orEmpty()
        if (owner.isBlank() || baustelleId <= 0L || id.isBlank()) {
            loeschen(owner)
            return
        }
        scope = Scope(owner = owner, baustelleId = baustelleId, btbId = id)
    }

    fun loeschen(owner: String) {
        val current = scope ?: return
        if (current.owner == owner) scope = null
    }

    fun filter(context: Context, baustelleId: Long, fotos: List<FotoEntity>): List<FotoEntity> {
        val current = scope ?: return fotos
        if (current.baustelleId != baustelleId) return fotos
        val allowed = BtbFotoZuordnungSpeicher.fotoIdsFuerBtb(context.applicationContext, current.btbId)
        return fotos.filter { it.id in allowed }
    }
}
