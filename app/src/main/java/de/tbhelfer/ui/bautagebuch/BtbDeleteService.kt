package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ui.suche.BtbSpeicher
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Lokales fachliches Loeschen eines modernen BTB.
 *
 * Geloescht werden BTB-Datensatz, unveraenderliches Original, Abschluss-Metadaten und alle
 * app-eigenen PDF/DOCX-Dateien dieses BTB. Echte Baustellenfotos bleiben dagegen erhalten; ihre
 * Foto->btbId-Zuordnung wird nur geloest. Damit kann ein BTB entfernt werden, ohne versehentlich
 * Beweis-/Baustellenfotos physisch zu vernichten.
 *
 * Diese Klasse loescht bewusst NUR lokal. Benutzerinitiierte Team-Loeschungen laufen ueber
 * [BtbDeleteSyncCoordinator], damit dieselbe btbId zusaetzlich ueber die vorhandene Offline-Queue
 * auf Server und andere Teamgeraete geloescht wird. Serverseitig empfangene Tombstones duerfen
 * diese lokale Funktion direkt benutzen, ohne einen Delete-Kreis zu erzeugen.
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

        // War genau dieser BTB aktiv, wird der Pointer entfernt. Danach bleibt bewusst KEIN anderer
        // Verlauf-BTB automatisch aktiv. Abgeschlossene/alte BTBs duerfen nur ueber
        // "BTB bearbeiten" bewusst reaktiviert werden.
        val btbRoot = dateDir.parentFile
        val pointer = btbRoot?.let { File(it, ".active_btb.json") }
        if (pointer?.exists() == true) {
            val pointsToDeleted = runCatching {
                JSONObject(pointer.readText(Charsets.UTF_8)).optString("btbId") == btbId
            }.getOrDefault(false)
            if (pointsToDeleted) delete(pointer)
        }

        // Ein eventuell vorhandener Abschluss-Inaktivmarker des geloeschten BTB ist ebenfalls
        // bedeutungslos. Er darf insbesondere keinen spaeteren BTB blockieren.
        BtbActivePointerControl.inaktivMarkerLoeschen(
            appContext,
            entry.baustelleId,
            entry.baustelleName
        )

        return Ergebnis(deleted, detached, null)
    }

    private fun safeToken(raw: String): String = raw.lowercase()
        .replace(Regex("[^a-z0-9_-]"), "_")
        .take(80)
        .ifBlank { "btb" }
}
