package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.widget.TBHelferWidgetUpdater
import java.io.File
import java.time.LocalDate

/**
 * Kleine Lebenszyklus-Hilfe fuer den modernen BTB.
 *
 * Ein abgeschlossener BTB bleibt vollstaendig gespeichert, ist danach aber nicht mehr aktiv.
 * Die Marker-Dateien verhindern, dass historische Datum/Schicht-Ladewege denselben abgeschlossenen
 * BTB beim naechsten Oeffnen ungefragt wieder zum aktiven Bericht machen. Gleichzeitig muss ein
 * bewusst ueber "BTB bearbeiten" reaktivierter Abschluss weiter editierbar bleiben.
 */
object BtbActivePointerControl {

    private const val ACTIVE_FILE = ".active_btb.json"
    private const val INACTIVE_AFTER_COMPLETION_FILE = ".inactive_after_completion"
    private const val CONSCIOUS_REOPEN_FILE = ".conscious_reopen_after_completion"

    private fun rootDir(
        context: Context,
        baustelleId: Long?,
        baustelleName: String
    ): File {
        val dayDir = BautagebuchSpeicher.ordner(
            context,
            baustelleId,
            baustelleName,
            LocalDate.now()
        )
        return dayDir.parentFile ?: dayDir
    }

    fun aktiveBtbLoesen(
        context: Context,
        baustelleId: Long?,
        baustelleName: String
    ) {
        runCatching { File(rootDir(context, baustelleId, baustelleName), ACTIVE_FILE).delete() }
        runCatching { TBHelferWidgetUpdater.updateAll(context) }
    }

    fun nachAbschlussInaktivMerken(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        btbId: String
    ) {
        val id = btbId.trim()
        if (id.isBlank()) return
        val root = rootDir(context, baustelleId, baustelleName)
        runCatching { File(root, INACTIVE_AFTER_COMPLETION_FILE).writeText(id, Charsets.UTF_8) }
        runCatching { File(root, CONSCIOUS_REOPEN_FILE).delete() }
        aktiveBtbLoesen(context, baustelleId, baustelleName)
    }

    /**
     * Ein abgeschlossener BTB darf nur durch die ausdrueckliche Verlauf-Aktion wieder aktiv werden.
     * Der Marker unterscheidet diesen Benutzerwillen von einem noch stale vorhandenen Pointer auf
     * einem Helfergeraet, das den Abschluss gerade vom Server erhalten hat.
     */
    fun bewusstReaktivieren(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        btbId: String
    ) {
        val id = btbId.trim()
        if (id.isBlank()) return
        val root = rootDir(context, baustelleId, baustelleName)
        runCatching { File(root, INACTIVE_AFTER_COMPLETION_FILE).delete() }
        runCatching { File(root, CONSCIOUS_REOPEN_FILE).writeText(id, Charsets.UTF_8) }
        runCatching { TBHelferWidgetUpdater.updateAll(context) }
    }

    fun istBewusstReaktiviert(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        btbId: String
    ): Boolean {
        val id = btbId.trim()
        if (id.isBlank()) return false
        val file = File(rootDir(context, baustelleId, baustelleName), CONSCIOUS_REOPEN_FILE)
        return runCatching { file.takeIf { it.exists() }?.readText(Charsets.UTF_8)?.trim() == id }
            .getOrDefault(false)
    }

    fun nachAbschlussInaktiveBtbId(
        context: Context,
        baustelleId: Long?,
        baustelleName: String
    ): String? {
        val file = File(rootDir(context, baustelleId, baustelleName), INACTIVE_AFTER_COMPLETION_FILE)
        return runCatching { file.takeIf { it.exists() }?.readText(Charsets.UTF_8)?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun inaktivMarkerLoeschen(
        context: Context,
        baustelleId: Long?,
        baustelleName: String
    ) {
        runCatching { File(rootDir(context, baustelleId, baustelleName), INACTIVE_AFTER_COMPLETION_FILE).delete() }
    }

    fun reaktivierungsMarkerLoeschen(
        context: Context,
        baustelleId: Long?,
        baustelleName: String
    ) {
        runCatching { File(rootDir(context, baustelleId, baustelleName), CONSCIOUS_REOPEN_FILE).delete() }
    }
}
