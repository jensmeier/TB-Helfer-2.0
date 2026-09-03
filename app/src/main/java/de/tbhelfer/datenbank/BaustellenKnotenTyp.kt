package de.tbhelfer.datenbank

/**
 * Typ eines Baustellen-Knotens.
 *
 * - HAUPT: Nur der Sammelpunkt (Baustellenname). Darunter hängen Unterbaustellen.
 * - FREIE_STRECKE: Unterbaustelle mit Streckenkennung und Km von/bis.
 * - BAHNHOF: Unterbaustelle mit Bahnhof + Gleis/Weiche-Kennung.
 * - ALLGEMEIN_OBJEKT: Allgemeiner Unterknoten/Objekt (z.B. Kabelkanal, Schacht, Kabelquerung).
 */
enum class BaustellenKnotenTyp {
    HAUPT,
    FREIE_STRECKE,
    BAHNHOF,
    ALLGEMEIN_OBJEKT
}
