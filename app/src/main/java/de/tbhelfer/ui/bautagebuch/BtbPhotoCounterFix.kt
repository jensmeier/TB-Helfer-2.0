package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.view.View
import com.google.android.material.button.MaterialButton
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.Bildtyp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Korrigiert die noch historische Fotozaehler-Anzeige im grossen BautagebuchFragment.
 *
 * Der alte UI-Pfad kennt nur Datum/Schicht/Zeitfenster. Moderne Fotos besitzen zusaetzlich eine
 * eindeutige btbId. Deshalb muessen Zaehler UND Nenner dieselbe fachliche Menge verwenden:
 * - explizit dem aktuellen BTB zugeordnete Fotos,
 * - historische unzugeordnete Fotos weiterhin als Legacy-Fallback,
 * - explizit anderen BTBs zugeordnete Fotos niemals.
 */
object BtbPhotoCounterFix {

    suspend fun refresh(context: Context, root: View, entry: BautagebuchEintrag) {
        val datum = runCatching { LocalDate.parse(entry.datumIso) }.getOrNull() ?: return
        val appContext = context.applicationContext

        val labels = withContext(Dispatchers.IO) {
            val all = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(entry.baustelleId)
            val (from, to) = shiftWindowEpochMs(datum, entry.startUhrzeit, entry.endeUhrzeit)
            val eligible = all.asSequence()
                .filter { it.erstelltAmEpochMs in from until to }
                .filter {
                    BtbFotoZuordnungSpeicher.darfImBtbVerwendetWerden(
                        appContext,
                        it.id,
                        entry.btbId
                    )
                }
                .toList()

            val excluded = BtbPhotoSelectionStore.loadExcluded(
                appContext,
                entry.baustelleId,
                entry.datumIso,
                entry.startUhrzeit,
                entry.endeUhrzeit,
                entry.schicht
            )

            Bildtyp.entries.associateWith { typ ->
                val ids = eligible.asSequence()
                    .filter { it.bildtyp == typ }
                    .map { it.id }
                    .toList()
                val total = ids.size
                if (total == 0) {
                    "📷 0"
                } else {
                    val excludedForType = excluded[typ.name] ?: emptySet()
                    val selected = ids.count { it !in excludedForType }
                    "📷 $selected/$total"
                }
            }
        }

        fun set(id: Int, typ: Bildtyp, compact: Boolean = false) {
            val text = labels[typ] ?: "📷 0"
            root.findViewById<MaterialButton>(id)?.text = if (compact) text.replace("📷 ", "📷") else text
        }

        set(R.id.btnFotosArbeiten, Bildtyp.FOTODOKUMENTATION)
        set(R.id.btnFotosAbnahmen, Bildtyp.ABNAHMEN, compact = true)
        set(R.id.btnFotosEinweisung, Bildtyp.EINWEISUNG_UNTERWEISUNG)
        set(R.id.btnFotosBesonderheiten, Bildtyp.BESONDERHEITEN)
        set(R.id.btnFotosMaengel, Bildtyp.MAENGEL_SCHAEDEN, compact = true)
    }

    private fun shiftWindowEpochMs(datum: LocalDate, start: String?, ende: String?): Pair<Long, Long> {
        fun parseMinutes(value: String?): Int? {
            val parts = value?.trim().orEmpty().split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }

        val zone = ZoneId.systemDefault()
        val dayStart = datum.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextDayStart = datum.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val startMin = parseMinutes(start)
        val endMin = parseMinutes(ende)
        if (startMin == null || endMin == null) return dayStart to nextDayStart

        val from = dayStart + startMin * 60_000L
        val to = if (endMin >= startMin) {
            dayStart + endMin * 60_000L
        } else {
            nextDayStart + endMin * 60_000L
        }
        return from to to
    }
}
