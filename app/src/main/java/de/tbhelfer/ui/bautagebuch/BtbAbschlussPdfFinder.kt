package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ui.suche.BtbSpeicher
import java.io.File

/**
 * Findet die neueste tatsaechlich vorhandene Abschluss-PDF fuer genau einen BTB.
 *
 * Moderne PDF-Dateien enthalten aus Lesbarkeitsgruenden nur den gekuerzten stabilen
 * Dokumenttoken und nicht die komplette btbId. Deshalb darf der Verlauf nicht nach der
 * vollstaendigen UUID suchen.
 */
object BtbAbschlussPdfFinder {

    private val versionRegex = Regex("""_V(\d+)\.pdf$""", RegexOption.IGNORE_CASE)

    fun neueste(context: Context, entry: BautagebuchEintrag): File? {
        if (entry.btbId.isNullOrBlank()) return null

        val token = BtbDokumentVersionierung.dokumentToken(entry)
        return BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            .filterNot { it.name.contains("_entwurf_", ignoreCase = true) }
            .filter { file ->
                file.name.contains("_${token}_", ignoreCase = true) ||
                    file.name.contains("_${token}.", ignoreCase = true)
            }
            .maxWithOrNull(
                compareBy<File> { versionAusDateiname(it.name) }
                    .thenBy { it.lastModified() }
            )
    }

    internal fun versionAusDateiname(name: String): Int =
        versionRegex.find(name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: if (name.endsWith(".pdf", ignoreCase = true)) 1 else 0
}
