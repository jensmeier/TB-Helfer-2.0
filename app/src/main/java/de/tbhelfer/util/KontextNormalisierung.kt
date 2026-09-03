package de.tbhelfer.util

import java.util.Locale

object KontextNormalisierung {
    private val NON_ALNUM = Regex("[^\\p{L}\\p{Nd}]")
    private val STRECKE_MIT_RICHTUNG = Regex("""^(\d{4})(?:\s*[-/]\s*[012])?$""")

    fun normalizeBasis(text: String?): String {
        return text
            ?.trim()
            .orEmpty()
            .uppercase(Locale.GERMANY)
            .replace(NON_ALNUM, "")
    }

    fun normalizeBahnhof(text: String?): String = normalizeBasis(text)

    /**
     * Eine DB-Streckennummer wird fachlich ueber ihre vierstellige Basis verglichen.
     * Richtungs-/Kennziffern wie 5362-1 oder 5362/2 gehoeren zur selben Strecke 5362;
     * die Richtungspruefung erfolgt dort, wo sie benoetigt wird, separat.
     *
     * Das ist fuer die Live-KM-Anzeige wichtig: Eine Baustelle kann mit "5362"
     * angelegt sein, waehrend importierte Vermessungsdaten als "5362-1" vorliegen.
     */
    fun normalizeStrecke(text: String?): String {
        val raw = text?.trim().orEmpty()
        if (raw.isBlank()) return ""
        STRECKE_MIT_RICHTUNG.matchEntire(raw)?.let { match ->
            return match.groupValues[1]
        }
        return normalizeBasis(raw)
    }

    fun normalizeGleis(text: String?): String {
        var value = normalizeBasis(text)
        if (value.startsWith("GLEIS")) value = value.removePrefix("GLEIS")
        if (value.startsWith("GL")) value = value.removePrefix("GL")
        return value
    }

    fun normalizeWeiche(text: String?): String {
        var value = normalizeBasis(text)
        if (value.startsWith("WEICHE")) value = value.removePrefix("WEICHE")
        if (value.startsWith("WEZ")) return value
        if (value.startsWith("WA")) return value
        if (value.startsWith("WE")) return value
        if (value.startsWith("W") && value.length > 1) value = value.removePrefix("W")
        return value
    }

    fun sameBahnhof(left: String?, right: String?): Boolean {
        val l = normalizeBahnhof(left)
        val r = normalizeBahnhof(right)
        return l.isNotBlank() && l == r
    }

    fun sameStrecke(left: String?, right: String?): Boolean {
        val l = normalizeStrecke(left)
        val r = normalizeStrecke(right)
        return l.isNotBlank() && l == r
    }

    fun sameGleis(left: String?, right: String?): Boolean {
        val l = normalizeGleis(left)
        val r = normalizeGleis(right)
        return l.isNotBlank() && l == r
    }

    fun sameWeiche(left: String?, right: String?): Boolean {
        val l = normalizeWeiche(left)
        val r = normalizeWeiche(right)
        return l.isNotBlank() && l == r
    }

    fun gleisSuchbegriffe(text: String?): List<String> {
        val roh = text?.trim().orEmpty()
        if (roh.isBlank()) return emptyList()
        val norm = normalizeGleis(roh)
        return listOfNotNull(
            roh,
            norm.takeIf { it.isNotBlank() },
            norm.takeIf { it.isNotBlank() }?.let { "GL$it" },
            norm.takeIf { it.isNotBlank() }?.let { "Gleis $it" }
        )
    }
}
