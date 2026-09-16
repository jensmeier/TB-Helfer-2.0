package de.tbhelfer.ui.kamera

import de.tbhelfer.datenbank.Indikator
import de.tbhelfer.vermessung.TrassenNaheBerechner
import kotlin.math.max

/**
 * Einheitliche KM-/Achsabstandsampel fuer Kamera und Fotoerfassung.
 *
 * Fachregel 200049:
 * - bis 10 m: gruen
 * - ueber 10 bis 30 m: gelb
 * - ueber 30 m: rot
 *
 * Die gemeldete GPS-Genauigkeit wird vorsichtig beruecksichtigt: maximal 10 m der
 * horizontalen Genauigkeit werden vom seitlichen Achsabstand abgezogen. Damit macht
 * ein schlechter GPS-Fix niemals einen weit entfernten Treffer unbegrenzt "gut".
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
        val accuracy = gpsAccuracyM
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.coerceAtMost(10.0)
            ?: 0.0
        val effektiv = max(0.0, abstand - accuracy)
        val indikator = when {
            effektiv <= 10.0 -> Indikator.GRUEN
            effektiv <= 30.0 -> Indikator.GELB
            else -> Indikator.ROT
        }
        return Bewertung(indikator, abstand, gpsAccuracyM, effektiv)
    }

    fun trassenQualitaet(achsabstandM: Double, gpsAccuracyM: Double?): TrassenNaheBerechner.Qualitaet =
        when (bewerten(achsabstandM, gpsAccuracyM).indikator) {
            Indikator.GRUEN -> TrassenNaheBerechner.Qualitaet.GRUEN
            Indikator.GELB -> TrassenNaheBerechner.Qualitaet.GELB
            Indikator.ROT -> TrassenNaheBerechner.Qualitaet.ROT
        }
}
