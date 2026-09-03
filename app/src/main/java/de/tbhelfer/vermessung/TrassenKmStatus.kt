package de.tbhelfer.vermessung

import kotlin.math.abs

/**
 * Einheitliche KM-Statuslogik fuer importierte und manuell kilometrierte Achsen.
 *
 * Fachliche Statusfarben in TB-Helfer:
 * - GRUEN  = belastbare automatische/originale KM vorhanden
 * - ORANGE = vom Benutzer manuell hergestellte oder geaenderte KM
 * - ROT    = TRA-Geometrie vorhanden, aber keine belastbare KM (auch unplausible KM)
 * - GRAU   = Objekt ohne Vermessungsgeometrie; wird ausserhalb dieser TRA-Logik vergeben
 *
 * Die alte 2-Punkt-Kalibrierung bleibt als Kompatibilitaetsweg erhalten. Der aktuelle
 * manuelle Standard ist jedoch: ein belastbarer KM-Anker + bekannte KM-Richtung auf
 * einer vorhandenen TRA-Geometrie. Ein zweiter Anker ist nur Kontrolle/Fallback.
 */
object TrassenKmStatusLogik {

    const val STATUS_AUTOMATISCH = "AUTOMATISCH"
    const val STATUS_MANUELL = "MANUELL"
    const val STATUS_FEHLT = "FEHLT"

    /**
     * Altstatus aus frueheren Patches. Neue Bewertungen liefern bei vorhandener TRA
     * fuer unplausible KM STATUS_FEHLT, damit die Achse fachlich ROT dargestellt wird.
     */
    const val STATUS_UNPLAUSIBEL = "UNPLAUSIBEL"

    data class Punkt(
        val station: Double,
        val kmMeter: Double?
    )

    data class Anker(
        val punktNummer: Int,
        val station: Double,
        val kmMeter: Double
    )

    data class Kalibrierung(
        val station1: Double,
        val km1Meter: Double,
        val station2: Double,
        val km2Meter: Double,
        val stationAbstandM: Double,
        val kmAbstandM: Double,
        val faktorKmProAchsMeter: Double,
        val steigend: Boolean,
        val plausibel: Boolean,
        val hinweis: String?
    ) {
        fun kmFuerStation(station: Double): Double {
            return km1Meter + (station - station1) * faktorKmProAchsMeter
        }
    }

    data class Bewertung(
        val status: String,
        val grund: String,
        val kmVonMeter: Double? = null,
        val kmBisMeter: Double? = null,
        val ankerAnzahl: Int = 0,
        val kalibrierung: Kalibrierung? = null
    )

    /**
     * Bewertung des aktuellen manuellen Standardfalls:
     * ein KM-Anker auf der TRA plus Richtung gegen die TRA-Station (+1/-1).
     *
     * Fachlich gilt der Abschnitt erst dann als ORANGE/manuell kilometriert, wenn neben
     * Anker und Richtung auch beide Grenzen des gueltigen Bereichs gesetzt sind. Ein
     * offener Entwurf darf gespeichert und vorlaeufig berechnet werden, bleibt aber ROT.
     */
    fun bewerteManuellenAbschnitt(
        kmAnkerStation: Double?,
        kmAnkerMeter: Double?,
        kmStationVorzeichen: Int?,
        stationStart: Double? = null,
        stationEnde: Double? = null
    ): Bewertung {
        val ankerGueltig = kmAnkerStation?.isFinite() == true && kmAnkerMeter?.isFinite() == true
        if (!ankerGueltig) {
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "Kein belastbarer manueller KM-Anker",
                ankerAnzahl = 0
            )
        }

        if (kmStationVorzeichen == null) {
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "KM-Anker vorhanden, KM-Richtung noch offen",
                ankerAnzahl = 1
            )
        }
        if (kmStationVorzeichen !in setOf(-1, 1)) {
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "KM-Richtung ist unplausibel",
                ankerAnzahl = 1
            )
        }

        val start = stationStart?.takeIf { it.isFinite() }
        val ende = stationEnde?.takeIf { it.isFinite() }
        if (start == null || ende == null) {
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "KM-Anker und Richtung vorhanden, gueltiger Bereich noch offen",
                ankerAnzahl = 1
            )
        }
        if (abs(ende - start) < 0.01) {
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "Gueltiger KM-Bereich ist zu kurz oder unplausibel",
                ankerAnzahl = 1
            )
        }

        val ankerStation = requireNotNull(kmAnkerStation)
        val ankerKm = requireNotNull(kmAnkerMeter)
        if (ankerStation < minOf(start, ende) - 0.01 || ankerStation > maxOf(start, ende) + 0.01) {
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "KM-Anker liegt ausserhalb des gueltigen Bereichs",
                ankerAnzahl = 1
            )
        }

        fun kmFuer(station: Double): Double = ankerKm + kmStationVorzeichen * (station - ankerStation)
        val kmStart = kmFuer(start)
        val kmEnde = kmFuer(ende)

        return Bewertung(
            status = STATUS_MANUELL,
            grund = "Manuell mit KM-Anker, Richtung und gueltigem Bereich kilometriert",
            kmVonMeter = minOf(kmStart, kmEnde),
            kmBisMeter = maxOf(kmStart, kmEnde),
            ankerAnzahl = 1
        )
    }

    /**
     * Kompatibilitaetsbewertung fuer automatische KM und alte manuelle 2-Punkt-Anker.
     */
    fun bewerte(punkte: List<Punkt>, manuelleAnker: List<Anker>): Bewertung {
        val anker = manuelleAnker
            .filter { it.station.isFinite() && it.kmMeter.isFinite() }
            .distinctBy { it.punktNummer }
            .sortedBy { it.punktNummer }

        if (anker.size >= 2) {
            val kal = kalibrierungAus(anker[0], anker[1])
            return if (kal.plausibel) {
                Bewertung(
                    status = STATUS_MANUELL,
                    grund = "Manuell mit 2 Punkten kalibriert (Kompatibilitaet)",
                    kmVonMeter = minOf(kal.km1Meter, kal.km2Meter),
                    kmBisMeter = maxOf(kal.km1Meter, kal.km2Meter),
                    ankerAnzahl = anker.size,
                    kalibrierung = kal
                )
            } else {
                Bewertung(
                    status = STATUS_FEHLT,
                    grund = kal.hinweis ?: "Manuelle KM-Kalibrierung unplausibel",
                    kmVonMeter = minOf(kal.km1Meter, kal.km2Meter),
                    kmBisMeter = maxOf(kal.km1Meter, kal.km2Meter),
                    ankerAnzahl = anker.size,
                    kalibrierung = kal
                )
            }
        }

        val valide = punkte
            .filter { it.station.isFinite() && it.kmMeter?.isFinite() == true }
            .sortedBy { it.station }

        if (valide.isEmpty()) {
            val suffix = if (anker.size == 1) " · 1 alter 2-Punkt-Anker gespeichert" else ""
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "Keine belastbare KM-Kalibrierung$suffix",
                ankerAnzahl = anker.size
            )
        }

        val kmWerte = valide.mapNotNull { it.kmMeter }
        val kmMin = kmWerte.minOrNull()
        val kmMax = kmWerte.maxOrNull()

        if (valide.size < 2 || kmMin == null || kmMax == null || abs(kmMax - kmMin) < 0.5) {
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "KM-Werte vorhanden, aber keine nutzbare KM-Spanne",
                kmVonMeter = kmMin,
                kmBisMeter = kmMax,
                ankerAnzahl = anker.size
            )
        }

        val stationSpanne = abs(valide.last().station - valide.first().station)
        val kmSpanne = abs((valide.last().kmMeter ?: 0.0) - (valide.first().kmMeter ?: 0.0))
        if (stationSpanne > 5.0 && kmSpanne / stationSpanne !in 0.2..5.0) {
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "KM-Spanne passt nicht plausibel zur Achslaenge",
                kmVonMeter = kmMin,
                kmBisMeter = kmMax,
                ankerAnzahl = anker.size
            )
        }

        val differenzen = valide.zipWithNext().mapNotNull { (a, b) ->
            val ka = a.kmMeter ?: return@mapNotNull null
            val kb = b.kmMeter ?: return@mapNotNull null
            val d = kb - ka
            d.takeIf { abs(it) >= 0.05 }
        }
        if (differenzen.isNotEmpty()) {
            val positiv = differenzen.count { it > 0.0 }
            val negativ = differenzen.count { it < 0.0 }
            val gegensinn = minOf(positiv, negativ)
            val grenze = maxOf(2, (differenzen.size * 0.10).toInt() + 1)
            if (gegensinn >= grenze) {
                return Bewertung(
                    status = STATUS_FEHLT,
                    grund = "KM-Richtung wechselt innerhalb derselben Achse mehrfach",
                    kmVonMeter = kmMin,
                    kmBisMeter = kmMax,
                    ankerAnzahl = anker.size
                )
            }
        }

        return Bewertung(
            status = STATUS_AUTOMATISCH,
            grund = "Automatische/originale KM vorhanden und plausibel",
            kmVonMeter = kmMin,
            kmBisMeter = kmMax,
            ankerAnzahl = anker.size
        )
    }

    fun kalibrierungAus(a: Anker, b: Anker): Kalibrierung {
        val stationDelta = b.station - a.station
        val kmDelta = b.kmMeter - a.kmMeter
        val stationAbstand = abs(stationDelta)
        val kmAbstand = abs(kmDelta)
        val faktor = if (abs(stationDelta) < 0.000001) Double.NaN else kmDelta / stationDelta

        val hinweis = when {
            !a.station.isFinite() || !b.station.isFinite() || !a.kmMeter.isFinite() || !b.kmMeter.isFinite() ->
                "Nicht endliche Werte in der Kalibrierung"
            stationAbstand < 20.0 ->
                "Punkte zu nah beieinander: mindestens 20 m, empfohlen 50 m oder mehr"
            kmAbstand < 1.0 ->
                "KM-Differenz zwischen den Punkten ist zu klein"
            !faktor.isFinite() ->
                "Kalibrierfaktor konnte nicht berechnet werden"
            abs(faktor) !in 0.5..1.5 ->
                "KM-Differenz und Achsabstand sind nicht plausibel zueinander"
            else -> null
        }

        return Kalibrierung(
            station1 = a.station,
            km1Meter = a.kmMeter,
            station2 = b.station,
            km2Meter = b.kmMeter,
            stationAbstandM = stationAbstand,
            kmAbstandM = kmAbstand,
            faktorKmProAchsMeter = faktor,
            steigend = faktor > 0.0,
            plausibel = hinweis == null,
            hinweis = hinweis
        )
    }
}
