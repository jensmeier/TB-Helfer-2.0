package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.TrassenAbschnittKmPunktEntity
import de.tbhelfer.datenbank.TrassenManuellerAbschnittEntity
import kotlin.math.abs

/** Reine KM-Berechnung fuer manuelle Abschnitte, bewusst ohne Android-Abhaengigkeiten. */
object ManuelleAbschnittKmLogik {

    data class Ergebnis(
        val kmMeter: Double?,
        val quelle: String,
        val hinweis: String? = null
    )

    /**
     * Berechnet KM aus einem beliebigen bekannten Anker und der Richtung entlang der TRA-Station.
     * KM-Spruenge werden als echte diskrete Spruenge behandelt und verzerren die lineare Strecke nicht.
     */
    fun kmFuerStation(
        abschnitt: TrassenManuellerAbschnittEntity,
        station: Double,
        punkte: List<TrassenAbschnittKmPunktEntity> = emptyList()
    ): Ergebnis {
        val ankerStation = abschnitt.kmAnkerStation
            ?: abschnitt.stationStart?.takeIf { abschnitt.kmPunkt1Meter != null }
        val ankerKm = abschnitt.kmAnkerMeter ?: abschnitt.kmPunkt1Meter
        val stationVorzeichen = abschnitt.kmStationVorzeichen

        if (ankerStation != null && ankerKm != null && stationVorzeichen != null && stationVorzeichen != 0) {
            var km = ankerKm + stationVorzeichen * (station - ankerStation)
            val spruenge = punkte
                .filter { it.typ == TrassenAbschnittKmPunktEntity.TYP_KM_SPRUNG }
                .filter { it.kmMeter != null && it.kmNachSprungMeter != null }
                .sortedBy { it.station }
            val laufStationVorzeichen = laufStationVorzeichen(abschnitt) ?: 1
            for (sprung in spruenge) {
                // kmMeter/kmNachSprungMeter werden in Laufrichtung Start -> Ende gespeichert.
                // Fuer die Berechnung gegen steigende TRA-Station wird der Sprung bei ruecklaeufiger
                // Abschnittsrichtung entsprechend umgedreht.
                val deltaLaufrichtung = (sprung.kmNachSprungMeter ?: continue) - (sprung.kmMeter ?: continue)
                val deltaBeiSteigenderStation = deltaLaufrichtung * laufStationVorzeichen
                when {
                    ankerStation < sprung.station && station >= sprung.station -> km += deltaBeiSteigenderStation
                    ankerStation > sprung.station && station <= sprung.station -> km -= deltaBeiSteigenderStation
                }
            }
            return Ergebnis(km, "MANUELL_ABSCHNITT")
        }

        // Rueckwaertskompatibilitaet fuer alte 2-Punkt-Abschnitte.
        val s1 = abschnitt.stationStart
        val s2 = abschnitt.kmPunkt2Station
        val k1 = abschnitt.kmPunkt1Meter
        val k2 = abschnitt.kmPunkt2Meter
        if (s1 != null && s2 != null && k1 != null && k2 != null) {
            val kal = TrassenKmStatusLogik.kalibrierungAus(
                TrassenKmStatusLogik.Anker(1, s1, k1),
                TrassenKmStatusLogik.Anker(2, s2, k2)
            )
            return if (kal.plausibel) {
                Ergebnis(kal.kmFuerStation(station), "MANUELL_ABSCHNITT")
            } else {
                Ergebnis(null, "MANUELL_ABSCHNITT_UNPLAUSIBEL", kal.hinweis)
            }
        }

        return Ergebnis(null, "MANUELL_ABSCHNITT_OFFEN", "Noch kein belastbarer KM-Anker mit Richtung vorhanden.")
    }

    /** Leitet +1/-1 fuer KM gegen TRA-Station aus zwei bekannten KM-Ankern ab. */
    fun stationVorzeichenAusZweiAnkern(
        station1: Double,
        km1: Double,
        station2: Double,
        km2: Double
    ): Int? {
        if (abs(station2 - station1) < 0.001 || abs(km2 - km1) < 0.001) return null
        return if ((station2 - station1) * (km2 - km1) > 0.0) 1 else -1
    }

    /**
     * Nutzerangabe STEIGEND/FALLEND gilt fuer Start -> Ende. Daraus wird das Vorzeichen gegen
     * steigende TRA-Station abgeleitet, sobald die Abschnittsrichtung geometrisch bekannt ist.
     */
    fun stationVorzeichenAusAbschnittsRichtung(
        stationStart: Double?,
        stationEndeOderWeitererPunkt: Double?,
        kmRichtung: String?
    ): Int? {
        val start = stationStart ?: return null
        val ziel = stationEndeOderWeitererPunkt ?: return null
        if (abs(ziel - start) < 0.001) return null
        val laufVorzeichen = if (ziel > start) 1 else -1
        return when (kmRichtung) {
            TrassenManuellerAbschnittEntity.KM_RICHTUNG_STEIGEND -> laufVorzeichen
            TrassenManuellerAbschnittEntity.KM_RICHTUNG_FALLEND -> -laufVorzeichen
            else -> null
        }
    }


    /** +1/-1 fuer die geometrische Laufrichtung Start -> Ende auf der TRA-Station. */
    fun laufStationVorzeichen(abschnitt: TrassenManuellerAbschnittEntity): Int? {
        val start = abschnitt.stationStart
        val ende = abschnitt.stationEnde
        if (start != null && ende != null && abs(ende - start) >= 0.001) return if (ende > start) 1 else -1

        val anker = abschnitt.kmAnkerStation
        if (start != null && anker != null && abs(anker - start) >= 0.001) return if (anker > start) 1 else -1

        val kmVorzeichen = abschnitt.kmStationVorzeichen
        return when {
            kmVorzeichen == null -> null
            abschnitt.kmRichtung == TrassenManuellerAbschnittEntity.KM_RICHTUNG_STEIGEND -> kmVorzeichen
            abschnitt.kmRichtung == TrassenManuellerAbschnittEntity.KM_RICHTUNG_FALLEND -> -kmVorzeichen
            else -> null
        }
    }

    fun kmRichtungAusStationVorzeichen(
        stationStart: Double?,
        stationZielInLaufrichtung: Double?,
        kmStationVorzeichen: Int?
    ): String? {
        val start = stationStart ?: return null
        val ziel = stationZielInLaufrichtung ?: return null
        val kmSign = kmStationVorzeichen ?: return null
        if (abs(ziel - start) < 0.001) return null
        val laufSign = if (ziel > start) 1 else -1
        return if (kmSign == laufSign) {
            TrassenManuellerAbschnittEntity.KM_RICHTUNG_STEIGEND
        } else {
            TrassenManuellerAbschnittEntity.KM_RICHTUNG_FALLEND
        }
    }

    fun erwarteteAbweichungM(
        abschnitt: TrassenManuellerAbschnittEntity,
        station: Double,
        bekannterKmMeter: Double,
        punkte: List<TrassenAbschnittKmPunktEntity> = emptyList()
    ): Double? = kmFuerStation(abschnitt, station, punkte).kmMeter?.let { bekannterKmMeter - it }
}
