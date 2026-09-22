package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.SofortSync
import kotlinx.coroutines.flow.first

/**
 * Eine gemeinsame Quelle fuer den aktuell aktiven modernen BTB einer Baustelle.
 *
 * Wichtig: Nicht Datum/Uhrzeit/Schicht entscheiden, sondern der aktive Pointer -> btbId.
 * Dadurch lesen Kamera, BTB-Kopf und Folgefunktionen immer denselben Datensatz.
 */
object BtbActiveContext {

    data class Result(
        val baustelleId: Long,
        val baustelleName: String,
        val btbId: String,
        val entry: BautagebuchEintrag
    )

    /** Liest den aktiven BTB der aktuell gesetzten Baustelle. */
    suspend fun laden(context: Context): Result? {
        val baustelleId = ServiceLocator.aktuelleBaustelleSpeicher.aktuelleBaustelleId.first() ?: return null
        return laden(context, baustelleId)
    }

    suspend fun laden(context: Context, baustelleId: Long): Result? {
        if (baustelleId <= 0L) return null
        val appContext = context.applicationContext
        val baustelle = ServiceLocator.baustellenRepository.baustelleMitIdOnce(baustelleId) ?: return null
        val name = baustelle.baustellenname.trim()
        if (name.isBlank()) return null

        val btbId = BautagebuchSpeicher.aktiveBtbId(appContext, baustelleId, name)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        // Ein lokal oder von einem Teamgeraet abgeschlossener BTB darf nicht ueber einen stale
        // Pointer weiter als "aktiv" erscheinen. Nur die ausdrueckliche Verlauf-Aktion
        // "BTB bearbeiten" setzt bewusstReaktivieren(...) und erlaubt denselben Abschluss wieder.
        val inactiveId = BtbActivePointerControl.nachAbschlussInaktiveBtbId(
            appContext, baustelleId, name
        )
        if (inactiveId == btbId) return null

        val entry = BautagebuchSpeicher.ladenById(appContext, baustelleId, name, btbId) ?: return null

        // Nicht die Legacy-/Transportfelder entscheiden ueber "geschlossen", sondern der
        // tatsaechliche Dokumentstatus. NACHBEARBEITUNG und ENTWURF muessen aktiv bleiben;
        // sonst verschwindet ein bewusst geoeffneter BTB waehrend Autosave/Sync wieder.
        val abgeschlossen =
            BtbDokumentVersionierung.status(appContext, entry) == BtbAbschlussStatus.ABGESCHLOSSEN
        if (abgeschlossen && !BtbActivePointerControl.istBewusstReaktiviert(
                appContext,
                baustelleId,
                name,
                btbId
            )
        ) {
            BtbActivePointerControl.nachAbschlussInaktivMerken(
                appContext,
                baustelleId,
                name,
                btbId
            )
            // Lokaler Abschluss wurde gerade direkt in die BTB-Datei geschrieben und laeuft nicht
            // noch einmal durch BautagebuchSpeicher.speichern(). Deshalb hier denselben bestehenden
            // SofortSync anstossen. Bei einem remote empfangenen Abschluss ist der Aufruf harmlos:
            // die Hash-/Queue-Logik erkennt, dass keine neue lokale Fachaenderung hochzuladen ist.
            runCatching { SofortSync.anstossen(appContext) }
            return null
        }
        return Result(baustelleId, name, btbId, entry)
    }
}
