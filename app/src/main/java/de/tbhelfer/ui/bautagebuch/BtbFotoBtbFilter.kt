package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.datenbank.FotoEntity

/** Ergänzt die bestehende manuelle Fotoauswahl temporär um die btbId-Zuordnung. */
object BtbFotoBtbFilter {

    suspend fun <T> mitFilter(
        context: Context,
        entry: BautagebuchEintrag,
        alleFotos: List<FotoEntity>,
        block: suspend () -> T
    ): T {
        val btbId = entry.btbId?.takeIf { it.isNotBlank() } ?: return block()
        val original = BtbPhotoSelectionStore.loadExcluded(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        ).mapValues { (_, ids) -> ids.toSet() }

        val augmented = original.mapValues { (_, ids) -> ids.toMutableSet() }.toMutableMap()
        alleFotos.forEach { foto ->
            val assigned = BtbFotoZuordnungSpeicher.btbId(context, foto.id)
            // Moderner BTB = strikt genau diese btbId. Auch unzugeordnete allgemeine Baustellenfotos
            // duerfen nicht ueber das alte Zeitfenster in den BTB hineinrutschen.
            if (assigned != btbId) {
                augmented.getOrPut(foto.bildtyp.name) { linkedSetOf() }.add(foto.id)
            }
        }

        BtbPhotoSelectionStore.saveExcluded(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht,
            augmented.mapValues { it.value.toSet() }
        )
        return try {
            block()
        } finally {
            BtbPhotoSelectionStore.saveExcluded(
                context,
                entry.baustelleId,
                entry.datumIso,
                entry.startUhrzeit,
                entry.endeUhrzeit,
                entry.schicht,
                original
            )
        }
    }

    fun fotosFuerBtb(
        context: Context,
        entry: BautagebuchEintrag,
        alleFotos: List<FotoEntity>,
        legacyFromEpochMs: Long? = null,
        legacyToEpochMs: Long? = null
    ): List<FotoEntity> {
        val id = entry.btbId?.takeIf { it.isNotBlank() }
        return alleFotos.filter { foto ->
            val assigned = BtbFotoZuordnungSpeicher.btbId(context, foto.id)
            if (id != null) {
                assigned == id
            } else {
                when {
                    !assigned.isNullOrBlank() -> false
                    legacyFromEpochMs != null && legacyToEpochMs != null -> foto.erstelltAmEpochMs in legacyFromEpochMs until legacyToEpochMs
                    else -> true
                }
            }
        }
    }
}
