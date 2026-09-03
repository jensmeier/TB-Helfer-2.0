package de.tbhelfer.vermessung

import kotlin.math.abs

/**
 * Einheitliche KM-Status- und 2-Punkt-Kalibrierlogik fuer alle importierten Achsen.
 *
 * Statusfarben in der grafischen Pruefung:
 * - GRUEN  = automatische KM vorhanden und plausibel
 * - ORANGE = manuell mit zwei GPS-/Achsankern kalibriert
 * - ROT    = Geometrie vorhanden, aber keine belastbare KM-Kalibrierung
 * - GRAU   = KM-Werte vorhanden, aber unplausibel/widerspruechlich
 */
object TrassenKmStatusLogik {

    const val STATUS_AUTOMATISCH = "AUTOMATISCH"
    const val STATUS_MANUELL = "MANUELL"
    const val STATUS_FEHLT = "FEHLT"
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
                    grund = "Manuell mit 2 Punkten kalibriert",
                    kmVonMeter = minOf(kal.km1Meter, kal.km2Meter),
                    kmBisMeter = maxOf(kal.km1Meter, kal.km2Meter),
                    ankerAnzahl = anker.size,
                    kalibrierung = kal
                )
            } else {
                Bewertung(
                    status = STATUS_UNPLAUSIBEL,
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
            val suffix = if (anker.size == 1) " · 1/2 manueller Punkt gespeichert" else ""
            return Bewertung(
                status = STATUS_FEHLT,
                grund = "Keine KM-Kalibrierung$suffix",
                ankerAnzahl = anker.size
            )
        }

        val kmWerte = valide.mapNotNull { it.kmMeter }
        val kmMin = kmWerte.minOrNull()
        val kmMax = kmWerte.maxOrNull()

        if (valide.size < 2 || kmMin == null || kmMax == null || abs(kmMax - kmMin) < 0.5) {
            return Bewertung(
                status = STATUS_UNPLAUSIBEL,
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
                status = STATUS_UNPLAUSIBEL,
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
                    status = STATUS_UNPLAUSIBEL,
                    grund = "KM-Richtung wechselt innerhalb derselben Achse mehrfach",
                    kmVonMeter = kmMin,
                    kmBisMeter = kmMax,
                    ankerAnzahl = anker.size
                )
            }
        }

        return Bewertung(
            status = STATUS_AUTOMATISCH,
            grund = "Automatische KM vorhanden und plausibel",
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
