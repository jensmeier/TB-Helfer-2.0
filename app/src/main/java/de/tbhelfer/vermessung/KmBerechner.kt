package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.StreckenpunktEntity
import de.tbhelfer.datenbank.StreckenpunktTyp
import kotlin.math.hypot
import kotlin.math.max

/**
 * km-Logik für TB-Helfer.
 *
 * Neuer Zielstand:
 * - Live-Rechnung nur auf bereits gespeicherten Punkten.
 * - Weichen werden hier nicht automatisch berechnet.
 * - Aus gespeicherten Anker-/Formpunkten wird eine berechnete Achse aufgebaut.
 * - Zwischen Punkten darf die Achse grob als Kurve interpoliert werden.
 * - Indikator: grün <= 1 m, gelb <= 3 m, rot > 3 m.
 */
class KmBerechner(
    private val sicherSchwelleM: Double = 1.0,
    private val gelbSchwelleM: Double = 3.0
) {
    enum class Indikator { GRUEN, GELB, ROT }

    data class Ergebnis(
        val indikator: Indikator,
        val km: Double?,
        val kmSuffix: Int?,
        val achsabstandM: Double?,
        val istWeichenmodus: Boolean,
        val inWeichenbereich: Boolean = false,
        val weichenbereichAbstandM: Double? = null
    )

    private data class Knoten(
        val punkt: StreckenpunktEntity,
        val xy: GeoMath.XY,
        val istKmAnker: Boolean
    )

    private data class AchsSample(
        val xy: GeoMath.XY,
        val km: Double,
        val kmSuffix: Int,
        val istInterpoliert: Boolean
    )

    fun berechne(punkte: List<StreckenpunktEntity>, latDeg: Double, lonDeg: Double): Ergebnis {
        val weichenPunkte = punkte
            .filter { it.typ.name.startsWith("WEICHE_") }
            .sortedWith(compareBy<StreckenpunktEntity> { it.km }.thenBy { it.kmSuffix }.thenBy { it.id })
        val basisPunkte = punkte
            .filter { !it.typ.name.startsWith("WEICHE_") }
            .sortedWith(compareBy<StreckenpunktEntity> { it.km }.thenBy { it.kmSuffix }.thenBy { it.id })

        if (basisPunkte.size < 2) {
            return berechneWeichenbereich(weichenPunkte, latDeg, lonDeg)
        }

        val ref = GeoMath.Ref(basisPunkte.first().gpsLat, basisPunkte.first().gpsLon)
        val liveXY = GeoMath.toXY(latDeg, lonDeg, ref)
        val knoten = basisPunkte.map {
            Knoten(
                punkt = it,
                xy = GeoMath.toXY(it.gpsLat, it.gpsLon, ref),
                istKmAnker = istKmAnker(it)
            )
        }
        val geglaetteteKnoten = glatteKnoten(knoten)
        val samples = baueAchseSamples(geglaetteteKnoten)
        if (samples.size < 2) {
            return Ergebnis(Indikator.ROT, null, null, null, istWeichenmodus = false)
        }

        var bestDist = Double.MAX_VALUE
        var bestKm: Double? = null
        var bestSuffix: Int? = null

        for (i in 0 until samples.lastIndex) {
            val a = samples[i]
            val b = samples[i + 1]
            val proj = GeoMath.projectPointToSegment(liveXY, a.xy, b.xy)
            if (proj.dist < bestDist) {
                bestDist = proj.dist
                bestKm = a.km + proj.t * (b.km - a.km)
                bestSuffix = if (proj.t < 0.5) a.kmSuffix else b.kmSuffix
            }
        }

        if (bestKm == null) {
            return Ergebnis(Indikator.ROT, null, null, null, istWeichenmodus = false)
        }

        val indikator = when {
            bestDist <= sicherSchwelleM -> Indikator.GRUEN
            bestDist <= gelbSchwelleM -> Indikator.GELB
            else -> Indikator.ROT
        }

        return Ergebnis(
            indikator = indikator,
            km = bestKm,
            kmSuffix = bestSuffix,
            achsabstandM = bestDist,
            istWeichenmodus = false
        )
    }


    /**
     * TB-Helfer-Logik:
     * - EXAKT / "Anker" = fachlicher km-Anker
     * - HILFS / "Trassenpunkt" = Geometrie-Stützpunkt
     *
     * Ein km-Anker darf die Achse fachlich kalibrieren, soll aber die Geometrie
     * nicht blind "festnageln", wenn das GPS seitlich z.B. 3 m daneben lag.
     * Deshalb werden km-Anker in der Geometrie schwächer gewichtet und mit der
     * lokalen Nachbarschaft geglättet.
     */
    private fun istKmAnker(punkt: StreckenpunktEntity): Boolean {
        if (punkt.typ == StreckenpunktTyp.EXAKT) return true
        val text = punkt.typFreitext?.trim()?.lowercase() ?: return false
        return text.contains("anker") || text.contains("km-start") || text.contains("km anker")
    }

    private fun glatteKnoten(knoten: List<Knoten>): List<Knoten> {
        if (knoten.size < 3) return knoten
        return knoten.mapIndexed { index, current ->
            val geglaettet = when {
                index == 0 && current.istKmAnker && knoten.size >= 3 -> {
                    val n1 = knoten[1].xy
                    val n2 = knoten[2].xy
                    GeoMath.XY(
                        x = current.xy.x * 0.30 + n1.x * 0.45 + n2.x * 0.25,
                        y = current.xy.y * 0.30 + n1.y * 0.45 + n2.y * 0.25
                    )
                }
                index == knoten.lastIndex && current.istKmAnker && knoten.size >= 3 -> {
                    val p1 = knoten[index - 1].xy
                    val p2 = knoten[index - 2].xy
                    GeoMath.XY(
                        x = current.xy.x * 0.30 + p1.x * 0.45 + p2.x * 0.25,
                        y = current.xy.y * 0.30 + p1.y * 0.45 + p2.y * 0.25
                    )
                }
                index == 0 || index == knoten.lastIndex -> current.xy
                current.istKmAnker -> {
                    val prev = knoten[index - 1].xy
                    val next = knoten[index + 1].xy
                    GeoMath.XY(
                        x = current.xy.x * 0.20 + prev.x * 0.40 + next.x * 0.40,
                        y = current.xy.y * 0.20 + prev.y * 0.40 + next.y * 0.40
                    )
                }
                else -> {
                    val prev = knoten[index - 1].xy
                    val next = knoten[index + 1].xy
                    GeoMath.XY(
                        x = current.xy.x * 0.60 + prev.x * 0.20 + next.x * 0.20,
                        y = current.xy.y * 0.60 + prev.y * 0.20 + next.y * 0.20
                    )
                }
            }
            current.copy(xy = geglaettet)
        }
    }

    private fun baueAchseSamples(knoten: List<Knoten>): List<AchsSample> {
        if (knoten.size < 2) return emptyList()
        if (knoten.size == 2) return baueGeradenSamples(knoten[0], knoten[1], istInterpoliert = true)

        val samples = mutableListOf<AchsSample>()
        for (i in 0 until knoten.lastIndex) {
            val p0 = knoten.getOrElse(i - 1) { spiegele(knoten[i], knoten[i + 1]) }
            val p1 = knoten[i]
            val p2 = knoten[i + 1]
            val p3 = knoten.getOrElse(i + 2) { spiegele(knoten[i + 1], knoten[i]) }
            val segmentSamples = baueKurvenSamples(p0, p1, p2, p3)
            if (segmentSamples.isEmpty()) continue
            if (samples.isEmpty()) {
                samples += segmentSamples
            } else {
                samples += segmentSamples.drop(1)
            }
        }
        return samples
    }

    private fun baueGeradenSamples(a: Knoten, b: Knoten, istInterpoliert: Boolean): List<AchsSample> {
        val dist = distanz(a.xy, b.xy)
        val steps = max(8, (dist / 5.0).toInt())
        val result = ArrayList<AchsSample>(steps + 1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            result += AchsSample(
                xy = lerp(a.xy, b.xy, t),
                km = a.punkt.km + t * (b.punkt.km - a.punkt.km),
                kmSuffix = if (t < 0.5) a.punkt.kmSuffix else b.punkt.kmSuffix,
                istInterpoliert = istInterpoliert
            )
        }
        return result
    }

    private fun baueKurvenSamples(p0: Knoten, p1: Knoten, p2: Knoten, p3: Knoten): List<AchsSample> {
        val dist = distanz(p1.xy, p2.xy)
        val steps = max(12, (dist / 4.0).toInt())
        val result = ArrayList<AchsSample>(steps + 1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            val xy = catmullRom(p0.xy, p1.xy, p2.xy, p3.xy, t)
            result += AchsSample(
                xy = xy,
                km = p1.punkt.km + t * (p2.punkt.km - p1.punkt.km),
                kmSuffix = if (t < 0.5) p1.punkt.kmSuffix else p2.punkt.kmSuffix,
                istInterpoliert = true
            )
        }
        return result
    }


    private fun berechneWeichenbereich(punkte: List<StreckenpunktEntity>, latDeg: Double, lonDeg: Double): Ergebnis {
        val wa = punkte.firstOrNull { it.typ == StreckenpunktTyp.WEICHE_WA }
        val we = punkte.firstOrNull { it.typ == StreckenpunktTyp.WEICHE_WS }
        if (wa == null || we == null) {
            return Ergebnis(Indikator.ROT, null, null, null, istWeichenmodus = true)
        }
        val wez = punkte.firstOrNull { it.typ == StreckenpunktTyp.WEICHE_WAZ }
        val ref = GeoMath.Ref(wa.gpsLat, wa.gpsLon)
        val liveXY = GeoMath.toXY(latDeg, lonDeg, ref)
        val waXY = GeoMath.toXY(wa.gpsLat, wa.gpsLon, ref)
        val weXY = GeoMath.toXY(we.gpsLat, we.gpsLon, ref)
        val wezXY = wez?.let { GeoMath.toXY(it.gpsLat, it.gpsLon, ref) }

        val proj = GeoMath.projectPointToSegment(liveXY, waXY, weXY)
        val km = wa.km + proj.t * (we.km - wa.km)
        val stammDist = proj.dist

        val toleranzM = 4.0
        val bereichDist = if (wezXY != null) {
            distanzZurGebuffertenDreiecksFlaeche(liveXY, waXY, weXY, wezXY, toleranzM)
        } else {
            max(0.0, stammDist - toleranzM)
        }
        val inBereich = bereichDist <= 0.0
        val indikator = when {
            bereichDist <= sicherSchwelleM -> Indikator.GRUEN
            bereichDist <= gelbSchwelleM -> Indikator.GELB
            else -> Indikator.ROT
        }

        return Ergebnis(
            indikator = indikator,
            km = km,
            kmSuffix = wa.kmSuffix,
            achsabstandM = stammDist,
            istWeichenmodus = true,
            inWeichenbereich = inBereich,
            weichenbereichAbstandM = bereichDist
        )
    }

    private fun distanzZurGebuffertenDreiecksFlaeche(
        p: GeoMath.XY,
        a: GeoMath.XY,
        b: GeoMath.XY,
        c: GeoMath.XY,
        toleranzM: Double
    ): Double {
        val inDreieck = punktInDreieck(p, a, b, c)
        val minEdge = minOf(
            GeoMath.projectPointToSegment(p, a, b).dist,
            GeoMath.projectPointToSegment(p, b, c).dist,
            GeoMath.projectPointToSegment(p, c, a).dist
        )
        val roh = if (inDreieck) 0.0 else minEdge
        return max(0.0, roh - toleranzM)
    }

    private fun punktInDreieck(p: GeoMath.XY, a: GeoMath.XY, b: GeoMath.XY, c: GeoMath.XY): Boolean {
        fun sign(p1: GeoMath.XY, p2: GeoMath.XY, p3: GeoMath.XY): Double {
            return (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y)
        }
        val d1 = sign(p, a, b)
        val d2 = sign(p, b, c)
        val d3 = sign(p, c, a)
        val hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0)
        val hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0)
        return !(hasNeg && hasPos)
    }

    private fun catmullRom(p0: GeoMath.XY, p1: GeoMath.XY, p2: GeoMath.XY, p3: GeoMath.XY, t: Double): GeoMath.XY {
        val t2 = t * t
        val t3 = t2 * t
        val x = 0.5 * ((2.0 * p1.x) + (-p0.x + p2.x) * t + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2 + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3)
        val y = 0.5 * ((2.0 * p1.y) + (-p0.y + p2.y) * t + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2 + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3)
        return GeoMath.XY(x, y)
    }

    private fun spiegele(pivot: Knoten, other: Knoten): Knoten {
        val mirrored = GeoMath.XY(
            x = pivot.xy.x + (pivot.xy.x - other.xy.x),
            y = pivot.xy.y + (pivot.xy.y - other.xy.y)
        )
        return pivot.copy(xy = mirrored)
    }

    private fun lerp(a: GeoMath.XY, b: GeoMath.XY, t: Double): GeoMath.XY = GeoMath.XY(
        x = a.x + (b.x - a.x) * t,
        y = a.y + (b.y - a.y) * t
    )

    private fun distanz(a: GeoMath.XY, b: GeoMath.XY): Double = hypot(b.x - a.x, b.y - a.y)
}
