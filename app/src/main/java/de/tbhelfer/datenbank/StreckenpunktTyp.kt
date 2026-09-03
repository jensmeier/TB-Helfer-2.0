package de.tbhelfer.datenbank

/**
 * Typ eines Streckenpunkts (Vermessung/Referenz).
 *
 * - EXAKT: Punkt mit sicherer km-Angabe (z.B. km-Stein/Vermarkung)
 * - HILFS: Punkt nur zur Achsverbesserung (keine amtliche km-Angabe)
 * - WEICHE_WA: Weichenanfang
 * - WEICHE_WS: Weichenende Stamm
 * - WEICHE_WAZ: Weichenende Abzweig
 */
enum class StreckenpunktTyp {
    EXAKT,
    HILFS,
    WEICHE_WA,
    WEICHE_WS,
    WEICHE_WAZ
}
