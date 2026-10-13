package de.tbhelfer.ui.kamera

import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp

/** Gemeinsame Anzeige fuer Baustellenname und Strecke/Gleis in Kamera/Fotopfad. */
object FotoStreckenAnzeige {

    fun baustellenName(knoten: BaustellenKnotenEntity?, fallback: String?): String =
        knoten?.hauptbaustellenName?.trim()?.takeIf { it.isNotBlank() }
            ?: fallback?.trim()?.takeIf { it.isNotBlank() }
            ?: "Baustelle"

    fun streckeGleis(
        knoten: BaustellenKnotenEntity?,
        fallbackStrecke: String?,
        fallbackBahnhof: String?
    ): String {
        val nodeName = knoten?.knotenName?.trim().orEmpty()
        if (knoten?.typ == BaustellenKnotenTyp.FREIE_STRECKE) {
            // Bevorzugt die bereits fachlich verwendete Bezeichnung wie "5362-1".
            val direkt = nodeName
                .substringBefore(" (", nodeName)
                .trim()
                .takeIf { it.matches(Regex("^\\d{3,6}[-/]\\S.+|^\\d{3,6}[-/]\\S+$")) }
            if (direkt != null) return direkt

            val strecke = knoten.streckenkennung?.trim().orEmpty()
            val gleis = knoten.gleisOderWeiche?.trim().orEmpty()
            if (strecke.isNotBlank() && gleis.isNotBlank()) return "$strecke-$gleis"
            if (strecke.isNotBlank()) return strecke
        }

        val fallbackRoute = fallbackStrecke?.trim().orEmpty()
        if (fallbackRoute.isNotBlank()) return fallbackRoute

        val bhf = fallbackBahnhof?.trim().orEmpty()
        if (bhf.isNotBlank()) return bhf

        return "-"
    }
}
