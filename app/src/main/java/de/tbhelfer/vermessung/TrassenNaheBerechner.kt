package de.tbhelfer.vermessung

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * GPS -> naechste importierte Trassenachse.
 *
 * Die Berechnung liefert mehrere nahe Kandidaten, damit in Bahnhofsbereichen
 * parallele Gleise/Weichen sauber bewertet werden koennen.
 * Patch44 ergaenzt eine konservative Anschluss-Suche an echten TRA-Achsenden.
 */
object TrassenNaheBerechner {

    data class AchsInfo(
        val achseId: Long,
        val name: String,
        val achsTyp: String,
        val strecke: String?,
        val gleis: String?,
        val richtung: String?
    )

    data class Punkt(
        val id: Long,
        val achseId: Long,
        val station: Double,
        val kmMeter: Double?,
        val kmQuelle: String? = null,
        val lat: Double,
        val lon: Double,
        val hoeheM: Double?,
        val richtungDeg: Double?
    )

    data class Ergebnis(
        val achseId: Long,
        val achseName: String,
        val achsTyp: String,
        val strecke: String?,
        val gleis: String?,
        val richtung: String?,
        val station: Double,
        val kmMeter: Double?,
        val abstandM: Double,
        val seite: Seite,
        val qualitaet: Qualitaet,
        val projLat: Double,
        val projLon: Double,
        val vonPunktId: Long,
        val bisPunktId: Long,
        /**
         * Patch 20: Optionaler Hinweis, aus welcher Quelle der angezeigte km-Wert stammt.
         * Beispiel: "GVP lokal". Null bedeutet: normaler Sample-/GRA-km.
         */
        val kmQuelle: String? = null,
        /**
         * Patch 20: Rohwert vor einer lokalen GVP-Korrektur, damit falsche GRA-Streckungen
         * im Live-Test sichtbar werden.
         */
        val kmOriginalMeter: Double? = null
    )

    enum class Seite { LINKS, RECHTS, AUF_ACHSE, UNBEKANNT }
    enum class Qualitaet { GRUEN, GELB, ORANGE, ROT }

    data class AnschlussKandidat(
        val achseId: Long,
        val achseName: String,
        val achsTyp: String,
        val abstandM: Double
    )

    /**
     * Sucht nur am tatsaechlichen Anfang/Ende einer TRA-Achse nach nahen Anschlussachsen.
     * Ein blosses geometrisches Kreuzen mitten auf zwei Achsen gilt bewusst NICHT als Anschluss.
     */
    fun findeAnschlussAchsenAmAchsende(
        achseId: Long,
        stationNaheEnde: Double,
        achsen: List<AchsInfo>,
        punkte: List<Punkt>,
        maxAbstandM: Double = 5.0,
        limit: Int = 5
    ): List<AnschlussKandidat> {
        val achsPunkte = punkte.filter { it.achseId == achseId }.sortedBy { it.station }
        if (achsPunkte.size < 2) return emptyList()

        val start = achsPunkte.first()
        val ende = achsPunkte.last()
        val endpunkt = if (abs(stationNaheEnde - start.station) <= abs(stationNaheEnde - ende.station)) start else ende
        val andereAchsen = achsen.filter { it.achseId != achseId }
        if (andereAchsen.isEmpty()) return emptyList()

        return findeNaechsteAchsen(
            lat = endpunkt.lat,
            lon = endpunkt.lon,
            achsen = andereAchsen,
            punkte = punkte,
            maxAbstandM = maxAbstandM,
            limit = limit.coerceAtLeast(1),
            gleisVorrang = false
        ).map {
            AnschlussKandidat(
                achseId = it.achseId,
                achseName = it.achseName,
                achsTyp = it.achsTyp,
                abstandM = it.abstandM
            )
        }.sortedBy { it.abstandM }
    }

    /**
     * Sucht die passendste Achse zur Live-Position.
     *
     * Prioritaet fuer Kamera/BTB:
     * 1) echte Gleisachsen bis 120 m
     * 2) Weichen / Weichenverbindungen bis 80 m
     * 3) Streckenachsen nur als Fallback
     */
    fun findeNaechsteAchse(
        lat: Double,
        lon: Double,
        achsen: List<AchsInfo>,
        punkte: List<Punkt>,
        maxAbstandM: Double = 250.0,
        gleisVorrang: Boolean = true
    ): Ergebnis? {
        val kandidaten = findeNaechsteAchsen(
            lat = lat,
            lon = lon,
            achsen = achsen,
            punkte = punkte,
            maxAbstandM = maxAbstandM,
            limit = 1,
            gleisVorrang = gleisVorrang
        )
        return kandidaten.firstOrNull()
    }

    /**
     * Liefert mehrere nahe Achsen. Bei gleisVorrang=false ist die erste Position
     * rein geometrisch der naechste Treffer. Bei gleisVorrang=true wird erst eine
     * fachliche Gleis-/Weichen-Prioritaet angewendet.
     */
    fun findeNaechsteAchsen(
        lat: Double,
        lon: Double,
        achsen: List<AchsInfo>,
        punkte: List<Punkt>,
        maxAbstandM: Double = 250.0,
        limit: Int = 6,
        gleisVorrang: Boolean = true
    ): List<Ergebnis> {
        val kandidaten = berechneAlleKandidaten(
            lat = lat,
            lon = lon,
            achsen = achsen,
            punkte = punkte,
            maxAbstandM = maxAbstandM
        )
        if (kandidaten.isEmpty()) return emptyList()

        val gewaehlt = if (gleisVorrang) waehleMitGleisVorrang(kandidaten) else kandidaten.minByOrNull { it.abstandM }
        val nachAbstand = kandidaten.sortedBy { it.abstandM }
        val sortiert = buildList<Ergebnis> {
            gewaehlt?.let { add(it) }
            nachAbstand.forEach { kandidat ->
                if (none { it.achseId == kandidat.achseId }) add(kandidat)
            }
        }
        return sortiert.take(limit.coerceAtLeast(1))
    }

    private fun waehleMitGleisVorrang(kandidaten: List<Ergebnis>): Ergebnis? {
        kandidaten
            .filter { it.achsTyp == "GLEIS" && it.abstandM <= 120.0 }
            .minByOrNull { it.abstandM }
            ?.let { return it }

        kandidaten
            .filter { (it.achsTyp == "WEICHE" || it.achsTyp == "WEICHENVERBINDUNG") && it.abstandM <= 80.0 }
            .minByOrNull { it.abstandM }
            ?.let { return it }

        return kandidaten.minByOrNull { it.abstandM }
    }

    private fun berechneAlleKandidaten(
        lat: Double,
        lon: Double,
        achsen: List<AchsInfo>,
        punkte: List<Punkt>,
        maxAbstandM: Double
    ): List<Ergebnis> {
        if (!lat.isFinite() || !lon.isFinite()) return emptyList()
        if (punkte.size < 2 || achsen.isEmpty()) return emptyList()

        val achsenById = achsen.associateBy { it.achseId }
        val kandidaten = mutableListOf<Ergebnis>()

        punkte
            .groupBy { it.achseId }
            .forEach { (achseId, achsPunkteRaw) ->
                val achse = achsenById[achseId] ?: return@forEach
                val achsPunkte = achsPunkteRaw.sortedBy { it.station }
                if (achsPunkte.size < 2) return@forEach

                var bestProAchse: Ergebnis? = null
                for (i in 0 until achsPunkte.lastIndex) {
                    val a = achsPunkte[i]
                    val b = achsPunkte[i + 1]
                    val segment = projektAufSegment(lat, lon, a, b) ?: continue
                    if (segment.abstandM > maxAbstandM) continue
                    val kandidat = Ergebnis(
                        achseId = achse.achseId,
                        achseName = achse.name,
                        achsTyp = achse.achsTyp,
                        strecke = achse.strecke,
                        gleis = achse.gleis,
                        richtung = achse.richtung,
                        station = segment.station,
                        kmMeter = segment.kmMeter,
                        kmQuelle = segment.kmQuelle,
                        abstandM = segment.abstandM,
                        seite = segment.seite,
                        qualitaet = qualitaet(segment.abstandM),
                        projLat = segment.projLat,
                        projLon = segment.projLon,
                        vonPunktId = a.id,
                        bisPunktId = b.id
                    )
                    val bisher = bestProAchse
                    if (bisher == null || kandidat.abstandM < bisher.abstandM) {
                        bestProAchse = kandidat
                    }
                }
                bestProAchse?.let { kandidaten += it }
            }

        return kandidaten
    }

    private data class SegmentErgebnis(
        val station: Double,
        val kmMeter: Double?,
        val kmQuelle: String?,
        val abstandM: Double,
        val seite: Seite,
        val projLat: Double,
        val projLon: Double
    )

    private fun projektAufSegment(lat: Double, lon: Double, a: Punkt, b: Punkt): SegmentErgebnis? {
        val scale = MeterScale(lat)
        val ax = scale.x(a.lon, lon)
        val ay = scale.y(a.lat, lat)
        val bx = scale.x(b.lon, lon)
        val by = scale.y(b.lat, lat)
        val qx = 0.0
        val qy = 0.0

        val vx = bx - ax
        val vy = by - ay
        val len2 = vx * vx + vy * vy
        if (len2 <= 0.000001) return null

        val rawT = ((qx - ax) * vx + (qy - ay) * vy) / len2
        val t = rawT.coerceIn(0.0, 1.0)
        val px = ax + t * vx
        val py = ay + t * vy
        val abstand = hypot(qx - px, qy - py)

        val station = lerp(a.station, b.station, t)
        val km = if (a.kmMeter != null && b.kmMeter != null) lerp(a.kmMeter, b.kmMeter, t) else null
        val kmQuelle = if (km != null && a.kmQuelle == b.kmQuelle) a.kmQuelle else null
        val projLat = lat + py / scale.mPerDegLat
        val projLon = lon + px / scale.mPerDegLon

        val cross = vx * (qy - ay) - vy * (qx - ax)
        val seite = when {
            abstand < 0.25 || abs(cross) < 0.0001 -> Seite.AUF_ACHSE
            cross > 0.0 -> Seite.LINKS
            cross < 0.0 -> Seite.RECHTS
            else -> Seite.UNBEKANNT
        }

        return SegmentErgebnis(
            station = station,
            kmMeter = km,
            kmQuelle = kmQuelle,
            abstandM = abstand,
            seite = seite,
            projLat = projLat,
            projLon = projLon
        )
    }

    private fun qualitaet(abstandM: Double): Qualitaet = when {
        abstandM <= 2.0 -> Qualitaet.GRUEN
        abstandM <= 5.0 -> Qualitaet.GELB
        abstandM <= 15.0 -> Qualitaet.ORANGE
        else -> Qualitaet.ROT
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private class MeterScale(lat0: Double) {
        val mPerDegLat: Double = 111_320.0
        val mPerDegLon: Double = max(1.0, 111_320.0 * cos(Math.toRadians(lat0)))
        fun x(lon: Double, lon0: Double): Double = (lon - lon0) * mPerDegLon
        fun y(lat: Double, lat0: Double): Double = (lat - lat0) * mPerDegLat
    }
}
