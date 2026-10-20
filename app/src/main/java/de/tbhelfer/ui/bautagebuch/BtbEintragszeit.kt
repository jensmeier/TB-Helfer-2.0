package de.tbhelfer.ui.bautagebuch

/** Getrennte Serverzeiten und historische Zeitspannen haben dieselbe Darstellung. */
object BtbEintragszeit {
    data class Bereich(val von: String?, val bis: String?) {
        val anzeige: String?
            get() = when {
                von != null && bis != null -> "$von–$bis"
                von != null -> von
                bis != null -> "–$bis"
                else -> null
            }
    }

    private val span = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*\(?\s*(\d{1,2}:\d{2})\s*\)?$""")
    private val single = Regex("""^(\d{1,2}):(\d{2})$""")
    private val endOnly = Regex("""^[-–]\s*(\d{1,2}:\d{2})$""")

    private fun zeit(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val match = single.matchEntire(value) ?: return value
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return value
        return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }

    /** Ein vorhandenes separates null loescht das Ende, auch bei einem Legacy-Beginnfeld. */
    fun lesen(von: String?, bis: String? = null, endeVorhanden: Boolean = bis != null): Bereich {
        val legacy = span.matchEntire(von?.trim().orEmpty())
        val legacyEnd = endOnly.matchEntire(von?.trim().orEmpty())
        return Bereich(
            if (legacyEnd != null) null else zeit(legacy?.groupValues?.get(1) ?: von),
            zeit(if (endeVorhanden) bis else legacy?.groupValues?.get(2) ?: legacyEnd?.groupValues?.get(1))
        )
    }
}
