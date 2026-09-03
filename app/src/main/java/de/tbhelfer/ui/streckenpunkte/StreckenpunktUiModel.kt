package de.tbhelfer.ui.streckenpunkte

import de.tbhelfer.datenbank.StreckenpunktEntity
import de.tbhelfer.datenbank.StreckenpunktTyp
import java.util.Locale

const val KM_OFFEN_WERT = -1.0

enum class ListenFilterUi {
    KONTEXT,
    ALLES,
    STRECKE,
    BAHNHOF
}

sealed class StreckenpunktListenEintrag {
    data class Header(
        val key: String,
        val text: String
    ) : StreckenpunktListenEintrag()

    data class Punkt(
        val item: StreckenpunktListenItem
    ) : StreckenpunktListenEintrag()
}

data class StreckenpunktListenItem(
    val punkt: StreckenpunktEntity,
    val kmText: String,
    val kmHinweisText: String?,
    val typText: String,
    val kontextText: String
)

fun hatKm(punkt: StreckenpunktEntity): Boolean = punkt.km >= 0.0

fun formatKmText(km: Double, kmSuffix: Int): String {
    val basis = String.format(Locale.GERMANY, "%.3f km", km)
    return if (kmSuffix in 1..2) "$basis -$kmSuffix" else basis
}

fun defaultTypText(typ: StreckenpunktTyp): String = when (typ) {
    StreckenpunktTyp.EXAKT -> "Anker"
    StreckenpunktTyp.HILFS -> "Hilfspunkt"
    StreckenpunktTyp.WEICHE_WA -> "WA"
    StreckenpunktTyp.WEICHE_WS -> "WE"
    StreckenpunktTyp.WEICHE_WAZ -> "WEZ"
}

fun formatTypAnzeige(typ: StreckenpunktTyp, typFreitext: String?): String {
    return typFreitext?.trim().takeUnless { it.isNullOrBlank() } ?: defaultTypText(typ)
}

fun mapNormalTypFromInput(roh: String): Pair<StreckenpunktTyp, String?> {
    val text = roh.trim()
    if (text.isBlank()) return StreckenpunktTyp.HILFS to "Trassenpunkt"

    val normalized = text.lowercase(Locale.GERMANY)
    return when {
        normalized.contains("anker") || normalized.contains("exakt") -> StreckenpunktTyp.EXAKT to "Anker"
        normalized.contains("trassen") -> StreckenpunktTyp.HILFS to "Trassenpunkt"
        normalized.contains("hilf") -> StreckenpunktTyp.HILFS to "Hilfspunkt"
        else -> StreckenpunktTyp.HILFS to text
    }
}

fun istKmPflicht(typ: StreckenpunktTyp, typFreitext: String?): Boolean {
    return when (typ) {
        StreckenpunktTyp.WEICHE_WA,
        StreckenpunktTyp.WEICHE_WS,
        StreckenpunktTyp.WEICHE_WAZ -> true
        StreckenpunktTyp.EXAKT -> true
        StreckenpunktTyp.HILFS -> false
    }
}

fun kontextLabelFuerPunkt(punkt: StreckenpunktEntity): String {
    return when {
        !punkt.streckennummer.isNullOrBlank() -> "Strecke ${punkt.streckennummer}"
        !punkt.bahnhof.isNullOrBlank() && !punkt.weiche.isNullOrBlank() -> "Bahnhof ${punkt.bahnhof} · Weiche ${punkt.weiche}"
        !punkt.bahnhof.isNullOrBlank() && !punkt.gleis.isNullOrBlank() -> "Bahnhof ${punkt.bahnhof} · Gleis ${punkt.gleis}"
        !punkt.bahnhof.isNullOrBlank() -> "Bahnhof ${punkt.bahnhof}"
        else -> "Ohne Kontext"
    }
}
