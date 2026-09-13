package de.tbhelfer.ui.bautagebuch

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * Übergangsbrücke für den bestehenden großen PDF-Renderer.
 *
 * Der Renderer liest historisch eine Baseline über Baustellenname + Datum + Schicht. Vor genau
 * einem Abschluss spiegeln wir deshalb den btbId-spezifischen Snapshot dorthin. Existiert für
 * den aktuellen btbId noch kein Abschluss, wird ein eventuell von einem anderen BTB stammender
 * Spiegel ausdrücklich entfernt, damit ein Erstabschluss nie fremde rote Änderungen bekommt.
 */
object BtbRendererBaselineBridge {

    private fun token(entry: BautagebuchEintrag): String = entry.btbId
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9_-]"), "_")
        ?.take(80)
        ?.ifBlank { "legacy" }
        ?: "legacy_${entry.datumIso}_${entry.schicht.name}".lowercase()

    fun prepare(context: Context, entry: BautagebuchEintrag) {
        val date = runCatching { LocalDate.parse(entry.datumIso) }.getOrNull() ?: return
        val specificDir = BautagebuchSpeicher.ordner(context, entry.baustelleId, entry.baustelleName, date)
        val specific = if (!entry.btbId.isNullOrBlank()) {
            File(specificDir, "btb_${token(entry)}_original.json")
        } else {
            File(specificDir, if (entry.schicht == SchichtTyp.NACHT) "bautagebuch_nacht_original.json" else "bautagebuch_tag_original.json")
        }

        val legacyDir = BautagebuchSpeicher.ordner(context, entry.baustelleName, date)
        val mirror = File(
            legacyDir,
            if (entry.schicht == SchichtTyp.NACHT) "bautagebuch_nacht_original.json" else "bautagebuch_tag_original.json"
        )

        runCatching {
            mirror.parentFile?.mkdirs()
            if (specific.exists()) specific.copyTo(mirror, overwrite = true)
            else if (mirror.exists()) mirror.delete()
        }
    }
}
