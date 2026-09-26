package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.BtbServerStateStore
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

        val entry = BautagebuchSpeicher.ladenById(appContext, baustelleId, name, btbId) ?: return null

        // BTB-SYNC-001 / Vertrag 26.09.2026:
        // Bei servergebundenen BTBs ist der kanonische Server-Lifecycle vorrangig.
        // Ein lokales/stales Abschluss-PDF oder eine alte Baseline darf einen vom Server
        // weiterhin als aktiv bestaetigten BTB niemals aus dem Active-Pointer entfernen.
        val serverState = BtbServerStateStore.state(appContext, btbId)
        if (serverState != null) {
            if (serverState.active) {
                return Result(baustelleId, name, btbId, entry)
            }

            // Den servergebundenen Pointer erst loesen, wenn der Server den BTB
            // eindeutig FINALIZED und nicht mehr aktiv bestaetigt hat.
            if (serverState.serverFinalReadOnly) {
                BtbActivePointerControl.nachAbschlussInaktivMerken(
                    appContext,
                    baustelleId,
                    name,
                    btbId
                )
                return null
            }

            // Fuer sonstige servergebundene Zwischenstaende entscheidet kein lokaler
            // PDF-/Baseline-/Abschlussstatus ueber den Team-Lifecycle.
            return Result(baustelleId, name, btbId, entry)
        }

        // Nur fuer rein lokale/Legacy-BTBs ohne Server-Snapshot bleibt die bisherige
        // Abschlusslogik maßgeblich.
        val inactiveId = BtbActivePointerControl.nachAbschlussInaktiveBtbId(
            appContext, baustelleId, name
        )
        if (inactiveId == btbId) return null

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
            // Serververtrag 25.09.2026: Abschluss/Lifecycle wird ausschließlich explizit
            // ueber shift-finish/shift-resume/finalize bzw. den Server-Delta-Pull transportiert.
            // Ein reiner Leseweg darf deshalb KEINEN zusaetzlichen Vollsync mehr anstossen.
            return null
        }
        return Result(baustelleId, name, btbId, entry)
    }
}
