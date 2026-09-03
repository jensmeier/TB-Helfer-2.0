package de.tbhelfer.ui.baustelle

import android.content.Context

/** Gemeinsame Fachlogik für Mängelliste und Baustellenkarte. */
object MangelFachlogik {
    data class Zaehler(
        val offen: Int = 0,
        val inArbeit: Int = 0,
        val erledigt: Int = 0,
        val abgelehnt: Int = 0
    )

    suspend fun zaehlerFuerBaustelle(context: Context, baustelleId: Long, baustelleName: String): Zaehler {
        val items = MaengellisteExport.ladeItems(context, baustelleId, baustelleName)
        var offen = 0
        var inArbeit = 0
        var erledigt = 0
        var abgelehnt = 0
        items.forEach { item ->
            when (MangelStatusSpeicher.laden(context, item.key).status) {
                MangelStatusSpeicher.Status.OFFEN -> offen++
                MangelStatusSpeicher.Status.IN_ARBEIT -> inArbeit++
                MangelStatusSpeicher.Status.ERLEDIGT -> erledigt++
                MangelStatusSpeicher.Status.ABGELEHNT -> abgelehnt++
            }
        }
        return Zaehler(offen, inArbeit, erledigt, abgelehnt)
    }
}
