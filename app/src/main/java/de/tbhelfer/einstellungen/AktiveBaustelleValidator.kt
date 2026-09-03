package de.tbhelfer.einstellungen

import de.tbhelfer.datenbank.BaustellenBaumRepository
import de.tbhelfer.datenbank.BaustellenRepository
import de.tbhelfer.datenbank.istStandaloneStreckenpunkteBaustelle
import kotlinx.coroutines.flow.first

object AktiveBaustelleValidator {

    suspend fun bereinigeWennUngueltig(
        speicher: AktuelleBaustelleSpeicher,
        baumRepo: BaustellenBaumRepository,
        baustellenRepo: BaustellenRepository
    ): Boolean {
        val altId = speicher.aktuelleBaustelleId.first()
        val aktuellerKnotenId = speicher.aktuellerKnotenId.first()
        val hauptId = speicher.aktuelleHauptbaustelleKnotenId.first()

        if (altId == null && aktuellerKnotenId == null && hauptId == null) return false

        val haupt = hauptId?.let { baumRepo.knotenMitId(it) }
        if (hauptId != null && haupt == null) {
            speicher.loeschen()
            return true
        }

        val aktuellerKnoten = aktuellerKnotenId?.let { baumRepo.knotenMitId(it) }
        if (aktuellerKnotenId != null && aktuellerKnoten == null) {
            if (haupt != null) {
                speicher.setzenNurHaupt(haupt.id, haupt.altBaustelleId)
            } else {
                speicher.loeschen()
            }
            return true
        }

        if (haupt != null && aktuellerKnoten != null && aktuellerKnoten.rootId != haupt.rootId) {
            speicher.setzenNurHaupt(haupt.id, haupt.altBaustelleId)
            return true
        }

        if (haupt != null) {
            if (altId != haupt.altBaustelleId) {
                if (aktuellerKnoten != null) {
                    speicher.setzenKnoten(
                        aktuellerKnotenId = aktuellerKnoten.id,
                        hauptKnotenId = haupt.id,
                        altBaustelleId = haupt.altBaustelleId
                    )
                } else {
                    speicher.setzenNurHaupt(haupt.id, haupt.altBaustelleId)
                }
                return true
            }
            return false
        }

        if (altId != null) {
            val legacy = baustellenRepo.baustelleMitIdOnce(altId)
            val hatBaum = baumRepo.hauptNachAltBaustelleId(altId) != null
            val istStandalone = legacy?.baustellenname?.let { istStandaloneStreckenpunkteBaustelle(it) } == true
            if (legacy == null || (!hatBaum && !istStandalone)) {
                speicher.loeschen()
                return true
            }
        }

        return false
    }
}
