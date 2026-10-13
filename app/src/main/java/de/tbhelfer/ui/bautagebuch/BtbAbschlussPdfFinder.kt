package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ui.suche.BtbSpeicher
import java.io.File

/** Findet das eine aktuelle Abschluss-PDF für genau eine btbId. */
object BtbAbschlussPdfFinder {

    private val legacyVersionRegex = Regex("""_V(\d+)\.pdf$""", RegexOption.IGNORE_CASE)

    fun neueste(context: Context, entry: BautagebuchEintrag): File? {
        if (entry.btbId.isNullOrBlank()) return null
        val token = BtbDokumentVersionierung.dokumentToken(entry)
        val files = BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            .filterNot { it.name.contains("_entwurf_", ignoreCase = true) }
            .filter { passtZuToken(it.name, token) }

        // Neues Modell: Datei ohne _Vn ist die einzige aktuelle Abschlussdatei.
        files.filterNot { legacyVersionRegex.containsMatchIn(it.name) }
            .maxByOrNull { it.lastModified() }
            ?.let { return it }

        // Übergang: vorhandene 200023/200024-Vn-Dateien bleiben lesbar, bis der BTB
        // erstmals im neuen Modell nachbearbeitet/abgeschlossen wird.
        return files.maxWithOrNull(
            compareBy<File> { legacyVersionAusDateiname(it.name) }
                .thenBy { it.lastModified() }
        )
    }

    internal fun passtZuToken(name: String, token: String): Boolean =
        name.contains("_${token}_", ignoreCase = true) ||
            name.contains("_${token}.", ignoreCase = true)

    internal fun legacyVersionAusDateiname(name: String): Int =
        legacyVersionRegex.find(name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: if (name.endsWith(".pdf", ignoreCase = true)) 1 else 0

    /** Alte Test/API-Bezeichnung bleibt vorerst erhalten. */
    internal fun versionAusDateiname(name: String): Int = legacyVersionAusDateiname(name)
}
