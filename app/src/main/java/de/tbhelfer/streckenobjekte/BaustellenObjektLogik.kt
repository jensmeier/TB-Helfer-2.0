package de.tbhelfer.streckenobjekte

import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import java.util.Locale

enum class BaustellenObjektArt {
    GLEIS,
    WEICHE,
    DKW_KREUZUNG,
    WEICHENVERBINDUNG,
    SONSTIGES
}

object BaustellenObjektLogik {

    fun artAusKennung(raw: String?): BaustellenObjektArt {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return BaustellenObjektArt.SONSTIGES
        val upper = text.uppercase(Locale.GERMANY)
            .replace('–', '-')
            .replace(Regex("\\s+"), " ")

        return when {
            upper.startsWith("WV") || upper.startsWith("WVB") || upper.startsWith("WEICHENVERBINDUNG") ->
                BaustellenObjektArt.WEICHENVERBINDUNG
            upper.startsWith("DKW") || upper.startsWith("KR ") || upper == "KR" ||
                upper.startsWith("KREUZUNG") || upper.startsWith("DKW/KREUZUNG") ->
                BaustellenObjektArt.DKW_KREUZUNG
            upper.startsWith("WEICHE") || Regex("^W\\s*[-/:]?[0-9A-Z]").containsMatchIn(upper) ->
                BaustellenObjektArt.WEICHE
            upper.startsWith("GL") || upper.startsWith("GLEIS") -> BaustellenObjektArt.GLEIS
            else -> BaustellenObjektArt.SONSTIGES
        }
    }

    fun art(knoten: BaustellenKnotenEntity?): BaustellenObjektArt = when {
        knoten == null -> BaustellenObjektArt.SONSTIGES
        knoten.typ != BaustellenKnotenTyp.BAHNHOF -> BaustellenObjektArt.SONSTIGES
        else -> artAusKennung(knoten.gleisOderWeiche ?: knoten.knotenName)
    }

    fun unterbaustellenName(knoten: BaustellenKnotenEntity?): String? {
        knoten ?: return null
        return when (knoten.typ) {
            BaustellenKnotenTyp.BAHNHOF -> {
                val station = knoten.bahnhofName?.trim()?.takeIf { it.isNotBlank() }
                val objectKey = knoten.gleisOderWeiche?.trim()?.takeIf { it.isNotBlank() }?.let(::kurzKennung)
                val name = knoten.knotenName?.trim()?.takeIf {
                    it.isNotBlank() && !it.equals("Bahnhof", true) && !it.equals(objectKey, true) && !it.equals(station, true)
                }
                listOfNotNull(station?.let { "Bahnhof $it" } ?: "Bahnhof unbekannt", objectKey, name).joinToString(" · ")
            }
            BaustellenKnotenTyp.FREIE_STRECKE ->
                knoten.knotenName?.trim()?.takeIf { it.isNotBlank() }?.let(::kurzKennung)
                    ?: knoten.streckenkennung?.trim()?.takeIf { it.isNotBlank() }?.let { "Freie Strecke $it" }
                    ?: "Freie Strecke"
            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT ->
                knoten.knotenName?.trim()?.takeIf { it.isNotBlank() }?.let(::kurzKennung)
                    ?: "Allgemein / Objekt"
            BaustellenKnotenTyp.HAUPT -> knoten.hauptbaustellenName
        }
    }

    fun normalisierteObjektKennung(raw: String?): String {
        val upper = raw?.trim().orEmpty().uppercase(Locale.GERMANY)
            .replace('–', '-')
            .replace(Regex("\\s+"), "")
        return upper.replaceFirst(
            Regex("^(WEICHENVERBINDUNG|DKW/KREUZUNG|KREUZUNG|WEICHE|GLEIS|WVB|DKW|WV|GL|W)"), ""
        ).trim('-', '/', ':', ' ')
    }

    private fun kurzKennung(raw: String): String {
        val s = raw.trim()
        return if (s.length <= 7 && s.all { it.isLetterOrDigit() || it == ' ' || it == '/' || it == '-' }) {
            s.uppercase(Locale.GERMANY)
        } else s
    }
}


