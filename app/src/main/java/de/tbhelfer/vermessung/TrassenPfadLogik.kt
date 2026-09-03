package de.tbhelfer.vermessung

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

/**
 * UI- und DB-unabhaengige Pfadwahl ueber verbundene TRA-Achsen.
 *
 * Die Logik entscheidet an Weichen/DKW niemals selbst einen Ast. Sie liefert den naechsten
 * Entscheidungspunkt in aktueller Laufrichtung und alle dort fachlich moeglichen Optionen.
 * Der Benutzer kann auf derselben Achse bleiben oder explizit auf eine angeschlossene TRA
 * wechseln. Die zurueckgelegte Strecke wird aus den TRA-Stationen aufsummiert und kann damit
 * spaeter fuer eine durchgehende manuelle Kilometrierung ueber mehrere Achsen verwendet werden.
 *
 * Es werden keine Daten gespeichert und keine Room-Tabellen benoetigt.
 */
object TrassenPfadLogik {

    const val STATION_RICHTUNG_STEIGEND = 1
    const val STATION_RICHTUNG_FALLEND = -1

    enum class OptionsTyp {
        AUF_ACHSE_BLEIBEN,
        ACHSE_WECHSELN
    }

    /** Stabiler, millimetergenau gerundeter Schluessel eines erkannten Anschlusses. */
    data class AnschlussSchluessel(
        val aAchseId: Long,
        val aStationMm: Long,
        val bAchseId: Long,
        val bStationMm: Long,
        val xMm: Long,
        val yMm: Long
    )

    data class Segment(
        val achseId: Long,
        val achseName: String,
        val vonStation: Double,
        val bisStation: Double
    ) {
        val laengeM: Double get() = abs(bisStation - vonStation)
    }

    data class Zustand(
        val achseId: Long,
        val achseName: String,
        val station: Double,
        /** +1 = in Richtung steigender TRA-Station, -1 = fallend. */
        val stationRichtung: Int,
        val wegSeitStartM: Double = 0.0,
        val segmente: List<Segment> = emptyList(),
        val passierteAnschluesse: Set<AnschlussSchluessel> = emptySet()
    )

    data class Option(
        val typ: OptionsTyp,
        val nachAchseId: Long,
        val nachAchseName: String,
        val nachStation: Double,
        val nachStationRichtung: Int,
        /** Nur bei ACHSE_WECHSELN gesetzt. */
        val anschluss: AnschlussSchluessel? = null,
        /** Geometrische Restabweichung des erkannten Anschlusses; nicht Teil der Pfadlaenge. */
        val anschlussAbstandM: Double = 0.0
    )

    data class Entscheidung(
        val achseId: Long,
        val achseName: String,
        val station: Double,
        /** Weg auf der aktuellen TRA vom Zustand bis zum Entscheidungspunkt. */
        val entfernungM: Double,
        val optionen: List<Option>,
        /** Alle Anschluesse desselben physischen Knotens, damit er nicht sofort erneut angeboten wird. */
        val anschluesseAmKnoten: Set<AnschlussSchluessel>
    )

    data class Pfad(
        val segmente: List<Segment>,
        val gesamtLaengeM: Double
    ) {
        val achsFolge: List<Long>
            get() = segmente.fold(mutableListOf<Long>()) { acc, segment ->
                if (acc.lastOrNull() != segment.achseId) acc += segment.achseId
                acc
            }

        fun kmAmEnde(ankerKmMeter: Double, kmVorzeichenImPfad: Int): Double {
            require(ankerKmMeter.isFinite()) { "Anker-KM muss endlich sein." }
            require(kmVorzeichenImPfad == 1 || kmVorzeichenImPfad == -1) {
                "KM-Vorzeichen im Pfad muss +1 oder -1 sein."
            }
            return ankerKmMeter + kmVorzeichenImPfad * gesamtLaengeM
        }
    }

    fun starte(
        achsen: List<TrassenAnschlussLogik.Achse>,
        achseId: Long,
        station: Double,
        stationRichtung: Int
    ): Zustand {
        require(station.isFinite()) { "Startstation muss endlich sein." }
        pruefeRichtung(stationRichtung)
        val achse = achsen.firstOrNull { it.id == achseId }
            ?: throw IllegalArgumentException("Startachse $achseId nicht gefunden.")
        val grenzen = stationGrenzen(achse)
            ?: throw IllegalArgumentException("Startachse $achseId hat keine gueltigen Stationen.")
        require(station >= grenzen.first - 0.01 && station <= grenzen.second + 0.01) {
            "Startstation liegt ausserhalb der TRA-Achse."
        }
        return Zustand(
            achseId = achse.id,
            achseName = achse.name,
            station = station,
            stationRichtung = stationRichtung
        )
    }

    /**
     * Liefert den naechsten Anschlussknoten in aktueller Laufrichtung.
     *
     * Ist die aktuelle TRA hinter dem Knoten noch vorhanden, wird AUF_ACHSE_BLEIBEN als eigene
     * Option angeboten. Fuer einen Wechsel auf eine Partnerachse werden alle dort moeglichen
     * Laufrichtungen angeboten. Trifft der Anschluss die Partnerachse mitten im Segment, koennen
     * deshalb zwei Wechseloptionen zur selben TRA entstehen.
     */
    fun naechsteEntscheidung(
        achsen: List<TrassenAnschlussLogik.Achse>,
        anschluesse: List<TrassenAnschlussLogik.Anschluss>,
        zustand: Zustand,
        stationEpsilonM: Double = 0.01,
        knotenStationToleranzM: Double = 0.05,
        knotenAbstandToleranzM: Double = 2.5
    ): Entscheidung? {
        pruefeRichtung(zustand.stationRichtung)
        require(stationEpsilonM >= 0.0 && stationEpsilonM.isFinite())
        require(knotenStationToleranzM >= 0.0 && knotenStationToleranzM.isFinite())
        require(knotenAbstandToleranzM >= 0.0 && knotenAbstandToleranzM.isFinite())

        val achsenNachId = achsen.associateBy { it.id }
        val aktuelleAchse = achsenNachId[zustand.achseId] ?: return null
        val aktuelleGrenzen = stationGrenzen(aktuelleAchse) ?: return null

        data class Kandidat(
            val anschluss: TrassenAnschlussLogik.Anschluss,
            val position: TrassenAnschlussLogik.Position,
            val schluessel: AnschlussSchluessel,
            val delta: Double
        )

        val kandidaten = anschluesse.mapNotNull { anschluss ->
            val position = anschluss.positionFuer(zustand.achseId) ?: return@mapNotNull null
            val schluessel = schluessel(anschluss)
            if (schluessel in zustand.passierteAnschluesse) return@mapNotNull null
            val delta = (position.station - zustand.station) * zustand.stationRichtung
            if (delta < -stationEpsilonM) return@mapNotNull null
            Kandidat(anschluss, position, schluessel, delta.coerceAtLeast(0.0))
        }
        if (kandidaten.isEmpty()) return null

        val erster = kandidaten.minBy { it.delta }
        val knoten = kandidaten.filter { kandidat ->
            abs(kandidat.position.station - erster.position.station) <= knotenStationToleranzM &&
                distanz(
                    kandidat.anschluss.x,
                    kandidat.anschluss.y,
                    erster.anschluss.x,
                    erster.anschluss.y
                ) <= knotenAbstandToleranzM
        }

        // An einem realen Weichen-/DKW-Knoten koennen durch die paarweise Geometrieerkennung
        // auch Anschluesse zwischen zwei Partnerachsen entstehen. Nach einer Nutzerentscheidung
        // muss deshalb der komplette physische Knoten als passiert gelten, nicht nur die Paare,
        // die die aktuell befahrene Achse enthalten.
        val alleAnschluesseAmKnoten = anschluesse.filter { anschluss ->
            distanz(
                anschluss.x,
                anschluss.y,
                erster.anschluss.x,
                erster.anschluss.y
            ) <= knotenAbstandToleranzM
        }

        val entscheidungsStation = erster.position.station
        val optionen = mutableListOf<Option>()

        val kannAufAktuellerAchseWeiter = if (zustand.stationRichtung > 0) {
            aktuelleGrenzen.second - entscheidungsStation > stationEpsilonM
        } else {
            entscheidungsStation - aktuelleGrenzen.first > stationEpsilonM
        }
        if (kannAufAktuellerAchseWeiter) {
            optionen += Option(
                typ = OptionsTyp.AUF_ACHSE_BLEIBEN,
                nachAchseId = zustand.achseId,
                nachAchseName = zustand.achseName,
                nachStation = entscheidungsStation,
                nachStationRichtung = zustand.stationRichtung
            )
        }

        knoten.forEach { kandidat ->
            val partner = kandidat.anschluss.partnerFuer(zustand.achseId) ?: return@forEach
            val partnerAchse = achsenNachId[partner.achseId] ?: return@forEach
            val partnerGrenzen = stationGrenzen(partnerAchse) ?: return@forEach
            moeglicheRichtungenVomAnschluss(
                station = partner.station,
                grenzen = partnerGrenzen,
                stationEpsilonM = stationEpsilonM
            ).forEach { richtung ->
                val option = Option(
                    typ = OptionsTyp.ACHSE_WECHSELN,
                    nachAchseId = partner.achseId,
                    nachAchseName = partner.achseName,
                    nachStation = partner.station,
                    nachStationRichtung = richtung,
                    anschluss = kandidat.schluessel,
                    anschlussAbstandM = kandidat.anschluss.abstandM
                )
                val schonVorhanden = optionen.any { vorhanden ->
                    vorhanden.typ == option.typ &&
                        vorhanden.nachAchseId == option.nachAchseId &&
                        vorhanden.nachStationRichtung == option.nachStationRichtung &&
                        abs(vorhanden.nachStation - option.nachStation) <= knotenStationToleranzM
                }
                if (!schonVorhanden) optionen += option
            }
        }

        if (optionen.isEmpty()) return null
        val sortierteOptionen = optionen.sortedWith(
            compareBy<Option> { if (it.typ == OptionsTyp.AUF_ACHSE_BLEIBEN) 0 else 1 }
                .thenBy { it.nachAchseName }
                .thenBy { it.nachStationRichtung }
        )

        return Entscheidung(
            achseId = zustand.achseId,
            achseName = zustand.achseName,
            station = entscheidungsStation,
            entfernungM = abs(entscheidungsStation - zustand.station),
            optionen = sortierteOptionen,
            anschluesseAmKnoten = alleAnschluesseAmKnoten.map(::schluessel).toSet()
        )
    }

    fun wendeOptionAn(
        zustand: Zustand,
        entscheidung: Entscheidung,
        option: Option,
        segmentEpsilonM: Double = 1e-6
    ): Zustand {
        require(entscheidung.achseId == zustand.achseId) {
            "Entscheidung gehoert nicht zur aktuellen TRA-Achse."
        }
        require(option in entscheidung.optionen) { "Option gehoert nicht zu dieser Entscheidung." }
        pruefeRichtung(option.nachStationRichtung)

        val segment = Segment(
            achseId = zustand.achseId,
            achseName = zustand.achseName,
            vonStation = zustand.station,
            bisStation = entscheidung.station
        )
        val neueSegmente = if (segment.laengeM > segmentEpsilonM) {
            zustand.segmente + segment
        } else {
            zustand.segmente
        }

        return Zustand(
            achseId = option.nachAchseId,
            achseName = option.nachAchseName,
            station = option.nachStation,
            stationRichtung = option.nachStationRichtung,
            wegSeitStartM = zustand.wegSeitStartM + segment.laengeM,
            segmente = neueSegmente,
            passierteAnschluesse = zustand.passierteAnschluesse + entscheidung.anschluesseAmKnoten
        )
    }

    /** Schliesst den ausgewaehlten Pfad an einer Station der aktuellen TRA ab. */
    fun beendeBei(
        achsen: List<TrassenAnschlussLogik.Achse>,
        zustand: Zustand,
        zielStation: Double,
        stationEpsilonM: Double = 0.01
    ): Pfad? {
        if (!zielStation.isFinite()) return null
        pruefeRichtung(zustand.stationRichtung)
        val achse = achsen.firstOrNull { it.id == zustand.achseId } ?: return null
        val grenzen = stationGrenzen(achse) ?: return null
        if (zielStation < grenzen.first - stationEpsilonM || zielStation > grenzen.second + stationEpsilonM) {
            return null
        }
        val delta = (zielStation - zustand.station) * zustand.stationRichtung
        if (delta < -stationEpsilonM) return null

        val segment = Segment(
            achseId = zustand.achseId,
            achseName = zustand.achseName,
            vonStation = zustand.station,
            bisStation = zielStation
        )
        val segmente = if (segment.laengeM > 1e-6) zustand.segmente + segment else zustand.segmente
        return Pfad(
            segmente = segmente,
            gesamtLaengeM = zustand.wegSeitStartM + segment.laengeM
        )
    }

    private fun moeglicheRichtungenVomAnschluss(
        station: Double,
        grenzen: Pair<Double, Double>,
        stationEpsilonM: Double
    ): List<Int> = buildList {
        if (grenzen.second - station > stationEpsilonM) add(STATION_RICHTUNG_STEIGEND)
        if (station - grenzen.first > stationEpsilonM) add(STATION_RICHTUNG_FALLEND)
    }

    private fun stationGrenzen(achse: TrassenAnschlussLogik.Achse): Pair<Double, Double>? {
        val stationen = achse.punkte.map { it.station }.filter { it.isFinite() }
        if (stationen.isEmpty()) return null
        return (stationen.minOrNull() ?: return null) to (stationen.maxOrNull() ?: return null)
    }

    private fun schluessel(anschluss: TrassenAnschlussLogik.Anschluss): AnschlussSchluessel =
        AnschlussSchluessel(
            aAchseId = anschluss.a.achseId,
            aStationMm = mm(anschluss.a.station),
            bAchseId = anschluss.b.achseId,
            bStationMm = mm(anschluss.b.station),
            xMm = mm(anschluss.x),
            yMm = mm(anschluss.y)
        )

    private fun mm(wert: Double): Long = round(wert * 1000.0).toLong()

    private fun pruefeRichtung(richtung: Int) {
        require(richtung == STATION_RICHTUNG_STEIGEND || richtung == STATION_RICHTUNG_FALLEND) {
            "TRA-Station-Richtung muss +1 oder -1 sein."
        }
    }

    private fun distanz(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
