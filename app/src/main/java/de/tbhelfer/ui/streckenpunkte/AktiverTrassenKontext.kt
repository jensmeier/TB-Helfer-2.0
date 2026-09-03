package de.tbhelfer.ui.streckenpunkte

import de.tbhelfer.datenbank.BaustelleEntity
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import java.util.Locale
import de.tbhelfer.util.KontextNormalisierung
import de.tbhelfer.streckenobjekte.BaustellenObjektArt
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik

enum class TrassenKontextArt {
    STRECKE,
    BAHNHOF_GLEIS,
    BAHNHOF_WEICHE
}

data class AktiverTrassenKontext(
    val art: TrassenKontextArt,
    val streckennummer: String? = null,
    val bahnhof: String? = null,
    val gleis: String? = null,
    val weiche: String? = null,
    val kmVon: Double? = null,
    val kmBis: Double? = null,
    val labelKurz: String,
    val labelLang: String,
    val objektKennung: String? = null
)

object AktiverTrassenKontextResolver {

    fun ausPfad(pfad: List<BaustellenKnotenEntity>, fallback: BaustelleEntity? = null): AktiverTrassenKontext? {
        val kontextKnoten = pfad.asReversed().firstOrNull {
            it.typ == BaustellenKnotenTyp.FREIE_STRECKE || it.typ == BaustellenKnotenTyp.BAHNHOF
        }
        return when {
            kontextKnoten != null -> ausKnoten(kontextKnoten)
            fallback != null -> ausLegacy(fallback)
            else -> null
        }
    }

    fun ausLegacy(entity: BaustelleEntity?): AktiverTrassenKontext? {
        if (entity == null) return null

        entity.streckennummer?.trim()?.takeIf { it.isNotBlank() }?.let { strecke ->
            return AktiverTrassenKontext(
                art = TrassenKontextArt.STRECKE,
                streckennummer = strecke,
                labelKurz = strecke,
                labelLang = "Freie Strecke $strecke",
                objektKennung = strecke
            )
        }

        val bahnhof = entity.bahnhof?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val gleis = entity.gleis?.trim().takeUnless { it.isNullOrBlank() }
        val weiche = entity.weiche?.trim().takeUnless { it.isNullOrBlank() }

        return when {
            !weiche.isNullOrBlank() -> AktiverTrassenKontext(
                art = TrassenKontextArt.BAHNHOF_WEICHE,
                bahnhof = bahnhof,
                weiche = weiche,
                labelKurz = "$bahnhof · $weiche",
                labelLang = "Bahnhof $bahnhof · Weiche $weiche",
                objektKennung = weiche
            )
            !gleis.isNullOrBlank() -> AktiverTrassenKontext(
                art = TrassenKontextArt.BAHNHOF_GLEIS,
                bahnhof = bahnhof,
                gleis = gleis,
                labelKurz = "$bahnhof · $gleis",
                labelLang = "Bahnhof $bahnhof · Gleis $gleis",
                objektKennung = gleis
            )
            else -> AktiverTrassenKontext(
                art = TrassenKontextArt.BAHNHOF_GLEIS,
                bahnhof = bahnhof,
                labelKurz = bahnhof,
                labelLang = "Bahnhof $bahnhof",
                objektKennung = bahnhof
            )
        }
    }

    private fun ausKnoten(knoten: BaustellenKnotenEntity): AktiverTrassenKontext? {
        return when (knoten.typ) {
            BaustellenKnotenTyp.FREIE_STRECKE -> {
                val strecke = knoten.streckenkennung?.trim().takeUnless { it.isNullOrBlank() } ?: return null
                AktiverTrassenKontext(
                    art = TrassenKontextArt.STRECKE,
                    streckennummer = strecke,
                    kmVon = knoten.kmVon,
                    kmBis = knoten.kmBis,
                    labelKurz = strecke,
                    labelLang = buildString {
                        append("Freie Strecke ")
                        append(strecke)
                        val kmText = formatKmBereich(knoten.kmVon, knoten.kmBis)
                        if (kmText != null) {
                            append(" · ")
                            append(kmText)
                        }
                    },
                    objektKennung = strecke
                )
            }

            BaustellenKnotenTyp.BAHNHOF -> {
                val bahnhof = knoten.bahnhofName?.trim().takeUnless { it.isNullOrBlank() } ?: return null
                val glw = knoten.gleisOderWeiche?.trim().takeUnless { it.isNullOrBlank() } ?: return null
                val istWeiche = istWeichenKennung(glw)
                if (istWeiche) {
                    AktiverTrassenKontext(
                        art = TrassenKontextArt.BAHNHOF_WEICHE,
                        bahnhof = bahnhof,
                        weiche = glw,
                        kmVon = knoten.kmVon,
                        kmBis = knoten.kmBis,
                        labelKurz = "$bahnhof · $glw",
                        labelLang = buildString {
                            append("Bahnhof ")
                            append(bahnhof)
                            append(" · Weiche ")
                            append(glw)
                            val kmText = formatKmBereich(knoten.kmVon, knoten.kmBis)
                            if (kmText != null) {
                                append(" · ")
                                append(kmText)
                            }
                        },
                        objektKennung = glw
                    )
                } else {
                    AktiverTrassenKontext(
                        art = TrassenKontextArt.BAHNHOF_GLEIS,
                        bahnhof = bahnhof,
                        gleis = glw,
                        kmVon = knoten.kmVon,
                        kmBis = knoten.kmBis,
                        labelKurz = "$bahnhof · $glw",
                        labelLang = buildString {
                            append("Bahnhof ")
                            append(bahnhof)
                            append(" · Gleis ")
                            append(glw)
                            val kmText = formatKmBereich(knoten.kmVon, knoten.kmBis)
                            if (kmText != null) {
                                append(" · ")
                                append(kmText)
                            }
                        },
                        objektKennung = glw
                    )
                }
            }

            else -> null
        }
    }

    private fun formatKmBereich(kmVon: Double?, kmBis: Double?): String? {
        val von = kmVon?.let { String.format(Locale.GERMANY, "%.3f", it) }
        val bis = kmBis?.let { String.format(Locale.GERMANY, "%.3f", it) }
        return when {
            von != null && bis != null -> "km $von bis $bis"
            von != null -> "ab km $von"
            bis != null -> "bis km $bis"
            else -> null
        }
    }

    private fun istWeichenKennung(value: String): Boolean {
        return when (BaustellenObjektLogik.artAusKennung(value)) {
            BaustellenObjektArt.WEICHE, BaustellenObjektArt.DKW_KREUZUNG, BaustellenObjektArt.WEICHENVERBINDUNG -> true
            else -> false
        }
    }
}


object TrassenKontextFilter {

    fun filterPunkte(punkte: List<de.tbhelfer.datenbank.StreckenpunktEntity>, kontext: AktiverTrassenKontext?): List<de.tbhelfer.datenbank.StreckenpunktEntity> {
        if (kontext == null) return emptyList()
        return punkte.filter { punkt ->
            when (kontext.art) {
                TrassenKontextArt.STRECKE -> matchesStrecke(punkt.streckennummer, kontext.streckennummer)
                TrassenKontextArt.BAHNHOF_GLEIS ->
                    matchesBahnhof(punkt.bahnhof, kontext.bahnhof) &&
                        matchesGleis(punkt.gleis, kontext.gleis)
                TrassenKontextArt.BAHNHOF_WEICHE ->
                    matchesBahnhof(punkt.bahnhof, kontext.bahnhof) &&
                        matchesWeiche(punkt.weiche, kontext.weiche)
            }
        }.sortedWith(compareBy<de.tbhelfer.datenbank.StreckenpunktEntity> { it.km }.thenBy { it.kmSuffix }.thenBy { it.id })
    }

    private fun matchesNullableIgnoreCase(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.equals(right, ignoreCase = true)
    }

    private fun matchesBahnhof(left: String?, right: String?): Boolean =
        KontextNormalisierung.sameBahnhof(left, right)

    private fun matchesStrecke(left: String?, right: String?): Boolean =
        KontextNormalisierung.sameStrecke(left, right)

    private fun matchesGleis(left: String?, right: String?): Boolean =
        KontextNormalisierung.sameGleis(left, right)

    private fun matchesWeiche(left: String?, right: String?): Boolean =
        KontextNormalisierung.sameWeiche(left, right)
}
