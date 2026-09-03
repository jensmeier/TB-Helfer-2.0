package de.tbhelfer.vermessung

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Gemeinsame, UI-unabhaengige Anschlusslogik fuer TRA-Achsen.
 *
 * Ein Anschluss entsteht, wenn ein Ende einer TRA-Achse innerhalb der Toleranz auf eine
 * andere TRA-Achse projiziert werden kann. Die Partnerachse darf dabei mitten in einem
 * Segment getroffen werden (typischer Fall bei Weichen/Abzweigen).
 *
 * Das Ergebnis enthaelt die Station auf beiden Achsen. Damit kann dieselbe Erkennung
 * spaeter sowohl fuer Grafik/Diagnose als auch fuer eine kontrollierte KM-Fortsetzung
 * ueber Weichen, Weichenverbindungen und DKW/Kreuzungen verwendet werden.
 */
object TrassenAnschlussLogik {

    data class Punkt(
        val station: Double,
        val x: Double,
        val y: Double
    )

    data class Achse(
        val id: Long,
        val name: String,
        val achsTyp: String? = null,
        val punkte: List<Punkt>
    )

    data class Position(
        val achseId: Long,
        val achseName: String,
        val achsTyp: String?,
        val station: Double,
        val x: Double,
        val y: Double,
        val istAchsEnde: Boolean
    )

    data class Anschluss(
        val a: Position,
        val b: Position,
        /** Abstand zwischen erkanntem Achsende und Projektion auf die Partnerachse. */
        val abstandM: Double
    ) {
        val x: Double get() = (a.x + b.x) / 2.0
        val y: Double get() = (a.y + b.y) / 2.0

        fun enthaeltAchse(achseId: Long): Boolean = a.achseId == achseId || b.achseId == achseId

        fun positionFuer(achseId: Long): Position? = when (achseId) {
            a.achseId -> a
            b.achseId -> b
            else -> null
        }

        fun partnerFuer(achseId: Long): Position? = when (achseId) {
            a.achseId -> b
            b.achseId -> a
            else -> null
        }
    }

    /**
     * Expliziter Uebergangskandidat. Bei einer Weiche mit mehreren Aesten werden mehrere
     * Kandidaten geliefert; die Fachlogik waehlt nicht heimlich einen Ast aus.
     */
    data class Fortsetzung(
        val von: Position,
        val nach: Position,
        val abstandM: Double
    )

    fun findeAnschluesse(
        achsen: List<Achse>,
        toleranzM: Double = 2.5
    ): List<Anschluss> {
        require(toleranzM > 0.0 && toleranzM.isFinite()) { "Anschlusstoleranz muss positiv sein." }

        val gueltigeAchsen = achsen
            .filter { it.punkte.isNotEmpty() }
            .map { it.copy(punkte = it.punkte.filter(::istGueltigerPunkt).sortedBy(Punkt::station)) }
            .filter { it.punkte.isNotEmpty() }

        val roh = mutableListOf<Anschluss>()

        gueltigeAchsen.forEach { quelle ->
            achsEnden(quelle).forEach { ende ->
                gueltigeAchsen.forEach partnerLoop@ { partner ->
                    if (partner.id == quelle.id || partner.punkte.size < 2) return@partnerLoop
                    val projektion = projiziereAufAchse(ende, partner) ?: return@partnerLoop
                    if (projektion.abstandM > toleranzM) return@partnerLoop

                    val quellePos = Position(
                        achseId = quelle.id,
                        achseName = quelle.name,
                        achsTyp = quelle.achsTyp,
                        station = ende.station,
                        x = ende.x,
                        y = ende.y,
                        istAchsEnde = true
                    )
                    val partnerPos = Position(
                        achseId = partner.id,
                        achseName = partner.name,
                        achsTyp = partner.achsTyp,
                        station = projektion.station,
                        x = projektion.x,
                        y = projektion.y,
                        istAchsEnde = projektion.istAchsEnde
                    )
                    roh += kanonisch(quellePos, partnerPos, projektion.abstandM)
                }
            }
        }

        // Derselbe reale Anschluss wird oft von beiden beteiligten Achsen erkannt.
        // Nach minimalem Abstand sortieren und geometrisch zusammenfassen, ohne zwei
        // verschiedene Anschluesse desselben Achsenpaares an entfernten Stellen zu verlieren.
        val zusammen = mutableListOf<Anschluss>()
        roh.sortedBy { it.abstandM }.forEach { kandidat ->
            val doppelt = zusammen.any { vorhanden ->
                gleichesAchsenpaar(vorhanden, kandidat) &&
                    distanz(vorhanden.x, vorhanden.y, kandidat.x, kandidat.y) <= toleranzM
            }
            if (!doppelt) zusammen += kandidat
        }

        return zusammen.sortedWith(
            compareBy<Anschluss> { minOf(it.a.achseId, it.b.achseId) }
                .thenBy { maxOf(it.a.achseId, it.b.achseId) }
                .thenBy { it.a.station }
                .thenBy { it.b.station }
        )
    }

    fun anschluesseFuerAchse(
        achseId: Long,
        anschluesse: List<Anschluss>
    ): List<Anschluss> = anschluesse.filter { it.enthaeltAchse(achseId) }

    fun fortsetzungenVon(
        achseId: Long,
        anschluesse: List<Anschluss>,
        naheStation: Double? = null,
        stationToleranzM: Double = 5.0
    ): List<Fortsetzung> {
        require(stationToleranzM >= 0.0 && stationToleranzM.isFinite()) {
            "Stationstoleranz darf nicht negativ sein."
        }
        return anschluesse.mapNotNull { anschluss ->
            val von = anschluss.positionFuer(achseId) ?: return@mapNotNull null
            if (naheStation != null && abs(von.station - naheStation) > stationToleranzM) {
                return@mapNotNull null
            }
            val nach = anschluss.partnerFuer(achseId) ?: return@mapNotNull null
            Fortsetzung(von = von, nach = nach, abstandM = anschluss.abstandM)
        }.sortedWith(compareBy<Fortsetzung> { it.von.station }.thenBy { it.nach.achseName })
    }

    private fun achsEnden(achse: Achse): List<Punkt> = when (achse.punkte.size) {
        0 -> emptyList()
        1 -> listOf(achse.punkte.first())
        else -> listOf(achse.punkte.first(), achse.punkte.last())
    }

    private data class Projektion(
        val station: Double,
        val x: Double,
        val y: Double,
        val abstandM: Double,
        val istAchsEnde: Boolean
    )

    private fun projiziereAufAchse(punkt: Punkt, achse: Achse): Projektion? {
        var beste: Projektion? = null
        for (i in 0 until achse.punkte.lastIndex) {
            val p1 = achse.punkte[i]
            val p2 = achse.punkte[i + 1]
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 1e-12) {
                0.0
            } else {
                (((punkt.x - p1.x) * dx + (punkt.y - p1.y) * dy) / len2).coerceIn(0.0, 1.0)
            }
            val x = p1.x + t * dx
            val y = p1.y + t * dy
            val station = p1.station + t * (p2.station - p1.station)
            val abstand = distanz(punkt.x, punkt.y, x, y)
            val istEnde = (i == 0 && t <= 1e-6) || (i == achse.punkte.lastIndex - 1 && t >= 1.0 - 1e-6)
            val kandidat = Projektion(station, x, y, abstand, istEnde)
            if (beste == null || kandidat.abstandM < beste.abstandM) beste = kandidat
        }
        return beste
    }

    private fun kanonisch(a: Position, b: Position, abstandM: Double): Anschluss =
        if (a.achseId <= b.achseId) Anschluss(a, b, abstandM) else Anschluss(b, a, abstandM)

    private fun gleichesAchsenpaar(a: Anschluss, b: Anschluss): Boolean =
        a.a.achseId == b.a.achseId && a.b.achseId == b.b.achseId

    private fun istGueltigerPunkt(p: Punkt): Boolean =
        p.station.isFinite() && p.x.isFinite() && p.y.isFinite()

    private fun distanz(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
