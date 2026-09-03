package de.tbhelfer.ui.suche

import de.tbhelfer.datenbank.Bildtyp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object SucheFotoFormatter {

    fun formatDatum(epochMs: Long): String {
        val dt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        return dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }


    private val dtf: DateTimeFormatter = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY)

    /** Anzeige-Label für Bildtyp (BTB-konform). */
    fun typLabel(typ: Bildtyp?): String {
        return when (typ) {
            Bildtyp.MAENGEL_SCHAEDEN -> "Mängel/Schäden"
            Bildtyp.ABNAHMEN -> "Abnahmen"
            Bildtyp.EINWEISUNG_UNTERWEISUNG -> "Einweisung/Unterweisung"
            Bildtyp.BESONDERHEITEN -> "Besonderheiten"
            Bildtyp.FOTODOKUMENTATION -> "Fotodokumentation"
            else -> "Foto"
        }
    }

    /** Backward compatible: einige Klassen rufen noch labelBildtyp(...) auf. */
    fun labelBildtyp(typ: Bildtyp?): String = typLabel(typ)

    /** Zeit-Format für Listen/Details. */
    fun formatZeit(epochMs: Long): String {
        return dtf.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    }
}
