package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.widget.TBHelferWidgetUpdater
import java.io.File
import java.time.LocalDate

/**
 * Kleine Lebenszyklus-Hilfe fuer den modernen BTB.
 *
 * Ein abgeschlossener BTB bleibt vollstaendig gespeichert, ist danach aber nicht mehr aktiv.
 * Die Marker-Datei verhindert, dass historische Datum/Schicht-Ladewege denselben abgeschlossenen
 * BTB beim naechsten Oeffnen ungefragt wieder zum aktiven Bericht machen.
 */
object BtbActivePointerControl {

    private const val ACTIVE_FILE = ".active_btb.json"
    private const val INACTIVE_AFTER_COMPLETION_FILE = ".inactive_after_completion"

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
        aktiveBtbLoesen(context, baustelleId, baustelleName)
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
}
