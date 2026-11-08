package de.tbhelfer.ui.bautagebuch

/** Presentation only: never changes a node identity or a stored report. */
object PdfOrtsLabel {
    fun basis(typ: String, name: String?, bahnhof: String?, gleis: String?, strecke: String?): String {
        val n = name.orEmpty().trim()
        val station = bahnhof.orEmpty().trim()
        val track = gleis.orEmpty().trim()
        val route = strecke.orEmpty().trim()
        if (typ == "HAUPT") return "Hauptbaustelle"
        if (typ == "FREIE_STRECKE") {
            val base = n.ifBlank { if(route.isBlank()) "Freie Strecke" else "Freie Strecke $route" }
            return if(route.isNotBlank() && !base.contains(route, true)) "$base · Strecke $route" else base
        }
        val parts = mutableListOf<String>()
        if(station.isNotBlank()) parts += "Bahnhof $station"
        if(track.isNotBlank()) parts += track
        val full = parts.joinToString(" – ")
        // Compare complete labels, not substrings: W 1 must not disappear behind W 10.
        val normalizedName = n.removePrefix("Bahnhof ").trim()
        val redundant = n.isBlank() || n.equals(full, true) || normalizedName.equals(station, true) || n.equals(track, true)
        if(!redundant) parts += n
        return parts.joinToString(" – ").ifBlank { if(typ == "BAHNHOF") "Bahnhof" else "Allgemein / Objekt" }
    }
}
