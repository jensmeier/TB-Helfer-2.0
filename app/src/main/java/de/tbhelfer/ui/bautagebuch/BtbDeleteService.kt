package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ui.suche.BtbSpeicher
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Bewusstes vollstaendiges Loeschen eines fachlichen BTB.
 *
 * Geloescht werden BTB-Datensatz, unveraenderliches Original, Abschluss-Metadaten und alle
 * app-eigenen PDF/DOCX-Dateien dieses BTB. Echte Baustellenfotos bleiben dagegen erhalten; ihre
 * Foto->btbId-Zuordnung wird nur geloest. Damit kann ein BTB entfernt werden, ohne versehentlich
 * Beweis-/Baustellenfotos physisch zu vernichten.
 */
object BtbDeleteService {

    data class Ergebnis(
        val geloeschteDateien: Int,
        val geloesteFotos: Int,
        val neuerAktiverBtbId: String?
    )

    fun loeschen(context: Context, entry: BautagebuchEintrag): Ergebnis {
        val appContext = context.applicationContext
        val btbId = entry.btbId?.trim().orEmpty()
        require(btbId.isNotBlank()) { "BTB-ID fehlt" }
        require(!btbId.startsWith("legacy:", ignoreCase = true)) {
            "Dieser Alt-BTB muss zuerst im neuen BTB-Format gespeichert werden."
        }

        val datum = LocalDate.parse(entry.datumIso)
        val dateDir = BautagebuchSpeicher.ordner(
            appContext,
            entry.baustelleId,
            entry.baustelleName,
            datum
        )
        val token = safeToken(btbId)
        var deleted = 0

        fun delete(file: File?) {
            if (file != null && file.exists() && file.isFile && runCatching { file.delete() }.getOrDefault(false)) {
                deleted += 1
            }
        }

        // BTB-Datensatz + unveraenderliches Original + Abschluss-Metadaten.
        delete(File(dateDir, "btb_${token}.json"))
        delete(File(dateDir, "btb_${token}_original.json"))
        delete(File(dateDir, "btb_${token}_abschluss_meta.json"))

        // Alle app-eigenen Dokumente dieses fachlichen BTB, inkl. eventuell vorhandenem Entwurf.
        val documentToken = BtbDokumentVersionierung.dokumentToken(entry)
        BtbSpeicher.btbsFuerBaustelle(appContext, entry.baustelleId, entry.datumIso)
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .filter {
                val n = it.name
                BtbAbschlussPdfFinder.passtZuToken(n, documentToken) ||
                    n.contains(documentToken, ignoreCase = true)
            }
            .forEach(::delete)

        // Unveraenderliche Foto-Basis des ersten Abschlusses entfernen, die Fotos selbst behalten.
        delete(File(File(appContext.filesDir, "btb_original_fotos"), "${token}.json"))
        val detached = BtbFotoZuordnungSpeicher.btbLoesen(appContext, btbId)

        // Wenn genau dieser BTB aktiv war, Pointer entfernen und danach den neuesten Rest-BTB aktivieren.
        val btbRoot = dateDir.parentFile
        val pointer = btbRoot?.let { File(it, ".active_btb.json") }
        if (pointer?.exists() == true) {
            val pointsToDeleted = runCatching {
                JSONObject(pointer.readText(Charsets.UTF_8)).optString("btbId") == btbId
            }.getOrDefault(false)
            if (pointsToDeleted) delete(pointer)
        }

        val remaining = BautagebuchSpeicher.verlaufFuerBaustelle(
            appContext,
            entry.baustelleId,
            entry.baustelleName
        ).firstOrNull()
        val nextId = remaining?.btbId?.takeIf { !it.startsWith("legacy:") }
        if (remaining != null && !nextId.isNullOrBlank()) {
            BautagebuchSpeicher.aktivierenBtb(
                appContext,
                entry.baustelleId,
                entry.baustelleName,
                nextId,
                remaining.datum,
                remaining.schicht
            )
        }

        return Ergebnis(deleted, detached, nextId)
    }

    private fun safeToken(raw: String): String = raw.lowercase()
        .replace(Regex("[^a-z0-9_-]"), "_")
        .take(80)
        .ifBlank { "btb" }
}
