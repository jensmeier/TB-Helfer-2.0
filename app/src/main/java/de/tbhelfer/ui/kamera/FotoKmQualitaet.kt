package de.tbhelfer.ui.kamera

import de.tbhelfer.datenbank.Indikator
import de.tbhelfer.vermessung.TrassenNaheBerechner

/**
 * Einheitliche KM-/Achsabstandsampel fuer Kamera und Fotoerfassung.
 *
 * Fachregel 200049:
 * - bis 10 m Achsabstand: gruen
 * - ueber 10 bis 30 m Achsabstand: gelb
 * - ueber 30 m Achsabstand: rot
 *
 * Die GPS-Genauigkeit wird zusaetzlich bewertet, darf einen Treffer aber niemals
 * besser machen. Bei mehr als 10 m gemeldeter horizontaler Ungenauigkeit wird ein
 * ansonsten gruener Treffer deshalb auf gelb herabgestuft. Der harte 30-m-Grenzwert
 * bleibt allein vom tatsaechlich berechneten Achsabstand abhaengig, damit >30 m immer
 * rot bleibt und bis einschliesslich 30 m der automatische km grundsaetzlich erhalten
 * werden kann.
 */
object FotoKmQualitaet {

    data class Bewertung(
        val indikator: Indikator,
        val achsabstandM: Double?,
        val gpsAccuracyM: Double?,
        val wirksamerAbstandM: Double?
    )

    fun bewerten(
        achsabstandM: Double?,
        gpsAccuracyM: Double?,
        fallback: Indikator = Indikator.ROT
    ): Bewertung {
        val abstand = achsabstandM?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return Bewertung(fallback, achsabstandM, gpsAccuracyM, null)
        val accuracy = gpsAccuracyM?.takeIf { it.isFinite() && it >= 0.0 }

        val abstandsIndikator = when {
            abstand <= 10.0 -> Indikator.GRUEN
            abstand <= 30.0 -> Indikator.GELB
            else -> Indikator.ROT
        }
        val indikator = when {
            abstandsIndikator == Indikator.ROT -> Indikator.ROT
            abstandsIndikator == Indikator.GRUEN && accuracy != null && accuracy > 10.0 -> Indikator.GELB
            else -> abstandsIndikator
        }
        return Bewertung(indikator, abstand, accuracy, abstand)
    }

    fun trassenQualitaet(achsabstandM: Double, gpsAccuracyM: Double?): TrassenNaheBerechner.Qualitaet =
        when (bewerten(achsabstandM, gpsAccuracyM).indikator) {
            Indikator.GRUEN -> TrassenNaheBerechner.Qualitaet.GRUEN
            Indikator.GELB -> TrassenNaheBerechner.Qualitaet.GELB
            Indikator.ROT -> TrassenNaheBerechner.Qualitaet.ROT
        }
}
