package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ui.suche.BtbSpeicher
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Kompatibilitätsname für den neuen Abschluss-Lebenszyklus.
 *
 * Sichtbare V1/V2/V3 existieren nicht mehr. Ein BTB behält seine btbId und besitzt höchstens
 * ein aktuelles Abschluss-PDF. Für die rote Änderungsdarstellung wird der letzte erfolgreiche
 * Abschluss als interner Snapshot behalten. Neue/geänderte Inhalte werden gegen diesen Snapshot
 * verglichen; gelöschte Inhalte kann der bestehende PDF-Diff als "(entfernt)" ausgeben.
 */
object BtbDokumentVersionierung {

    data class Vorbereitung(
        val naechsteVersion: Int,
        val abschlussEpochMs: Long,
        val benutzer: String
    )

    private fun storageToken(entry: BautagebuchEintrag): String {
        val raw = entry.btbId?.takeIf { it.isNotBlank() }
            ?: "legacy_${entry.datumIso}_${entry.schicht.name}"
        return raw.lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .take(80)
            .ifBlank { "legacy" }
    }

    fun dokumentToken(entry: BautagebuchEintrag): String = entry.btbId
        ?.let(BtbDokumentIdentitaet::safeToken)
        ?.take(18)
        ?.takeIf { it.isNotBlank() }
        ?: "ALT_${entry.datumIso}_${entry.schicht.name}"

    private fun datum(entry: BautagebuchEintrag): LocalDate? =
        runCatching { LocalDate.parse(entry.datumIso) }.getOrNull()

    private fun dateDir(context: Context, entry: BautagebuchEintrag): File? {
        val d = datum(entry) ?: return null
        return BautagebuchSpeicher.ordner(context, entry.baustelleId, entry.baustelleName, d)
    }

    private fun currentFile(context: Context, entry: BautagebuchEintrag): File? {
        val dir = dateDir(context, entry) ?: return null
        val id = entry.btbId?.takeIf { it.isNotBlank() }
        if (id != null) return File(dir, "btb_${storageToken(entry)}.json")
        val modern = File(dir, if (entry.schicht == SchichtTyp.NACHT) "bautagebuch_nacht.json" else "bautagebuch_tag.json")
        if (modern.exists()) return modern
        return if (entry.schicht == SchichtTyp.TAG) File(dir, "bautagebuch.json") else modern
    }

    private fun baselineFile(context: Context, entry: BautagebuchEintrag): File? {
        val dir = dateDir(context, entry) ?: return null
        val id = entry.btbId?.takeIf { it.isNotBlank() }
        if (id != null) return File(dir, "btb_${storageToken(entry)}_original.json")
        val modern = File(dir, if (entry.schicht == SchichtTyp.NACHT) "bautagebuch_nacht_original.json" else "bautagebuch_tag_original.json")
        if (modern.exists() || entry.schicht == SchichtTyp.NACHT) return modern
        val veryOld = File(dir, "bautagebuch_original.json")
        return if (veryOld.exists()) veryOld else modern
    }

    /**
     * Übergangsbrücke für den großen bestehenden PDF-Renderer. Er liest historisch eine
     * baustellennamenbasierte Baseline. Vor dem Rendern spiegeln wir ausschließlich den Snapshot
     * des gerade aktiven btbId dorthin; die fachliche Quelle bleibt btbId-spezifisch.
     */
    private fun rendererBaselineMirrorFile(context: Context, entry: BautagebuchEintrag): File? {
        val d = datum(entry) ?: return null
        val dir = BautagebuchSpeicher.ordner(context, entry.baustelleName, d)
        val name = if (entry.schicht == SchichtTyp.NACHT) "bautagebuch_nacht_original.json" else "bautagebuch_tag_original.json"
        return File(dir, name)
    }

    private fun outputDir(context: Context, entry: BautagebuchEintrag): File =
        BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)

    fun status(context: Context, entry: BautagebuchEintrag): BtbAbschlussStatus {
        val baseline = baselineFile(context, entry)?.takeIf { it.exists() } ?: return BtbAbschlussStatus.ENTWURF
        val current = currentFile(context, entry)?.takeIf { it.exists() } ?: return BtbAbschlussStatus.ENTWURF
        val changed = runCatching {
            val c = JSONObject(current.readText(Charsets.UTF_8))
            val b = JSONObject(baseline.readText(Charsets.UTF_8))
            vergleichsInhalt(c) != vergleichsInhalt(b)
        }.getOrDefault(true)
        return if (changed) BtbAbschlussStatus.NACHBEARBEITUNG else BtbAbschlussStatus.ABGESCHLOSSEN
    }

    fun istSeitLetztemAbschlussGeaendert(context: Context, entry: BautagebuchEintrag): Boolean =
        status(context, entry) == BtbAbschlussStatus.NACHBEARBEITUNG

    /**
     * Speichert den letzten erfolgreichen Abschluss als interne Vergleichsbasis und zusätzlich
     * eingebettet im aktuellen BTB-JSON. Das PDF selbst bleibt genau eine Datei pro btbId.
     */
    fun abschlussBestaetigen(
        context: Context,
        entry: BautagebuchEintrag,
        pdfFile: File,
        benutzer: String
    ): Boolean {
        if (!pdfFile.exists() || pdfFile.length() <= 0L) return false
        val current = currentFile(context, entry)?.takeIf { it.exists() } ?: return false
        val baseline = baselineFile(context, entry) ?: return false
        return runCatching {
            val now = System.currentTimeMillis()
            val snapshot = JSONObject(current.readText(Charsets.UTF_8))
            snapshot.put("abschlussStatus", BtbAbschlussStatus.ABGESCHLOSSEN.name)
            snapshot.put("abschlussPdfDateiname", pdfFile.name)
            snapshot.put("letzterAbschlussEpochMs", now)
            snapshot.put("letzterAbschlussBenutzer", benutzer)
            snapshot.put("istFertig", true)
            snapshot.put("dokumentVersion", 0)

            baseline.parentFile?.mkdirs()
            baseline.writeText(snapshot.toString(2), Charsets.UTF_8)

            val currentJson = JSONObject(current.readText(Charsets.UTF_8))
            currentJson.put("abschlussStatus", BtbAbschlussStatus.ABGESCHLOSSEN.name)
            currentJson.put("abschlussPdfDateiname", pdfFile.name)
            currentJson.put("letzterAbschlussEpochMs", now)
            currentJson.put("letzterAbschlussBenutzer", benutzer)
            currentJson.put("letzterAbschlussSnapshot", JSONObject(snapshot.toString()))
            currentJson.put("istFertig", true)
            currentJson.put("dokumentVersion", 0)
            current.writeText(currentJson.toString(2), Charsets.UTF_8)

            spiegeleBaselineFuerRenderer(context, entry)
            true
        }.getOrDefault(false)
    }

    /**
     * Autosave schreibt den aktuellen Modellstand neu. Diese Methode hängt anschließend den
     * letzten Abschluss-Snapshot wieder in dieselbe BTB-Datei ein und invalidiert bei echter
     * Nachbearbeitung das alte Abschluss-PDF.
     */
    fun pflegeNachAutosave(context: Context, entry: BautagebuchEintrag) {
        val baseline = baselineFile(context, entry)?.takeIf { it.exists() } ?: return
        val current = currentFile(context, entry)?.takeIf { it.exists() } ?: return
        runCatching {
            val snapshot = JSONObject(baseline.readText(Charsets.UTF_8))
            val currentJson = JSONObject(current.readText(Charsets.UTF_8))
            val changed = vergleichsInhalt(currentJson) != vergleichsInhalt(snapshot)
            currentJson.put("letzterAbschlussSnapshot", JSONObject(snapshot.toString()))
            currentJson.put("abschlussStatus", if (changed) BtbAbschlussStatus.NACHBEARBEITUNG.name else BtbAbschlussStatus.ABGESCHLOSSEN.name)
            currentJson.put("letzterAbschlussEpochMs", snapshot.optLong("letzterAbschlussEpochMs", 0L).takeIf { it > 0L } ?: JSONObject.NULL)
            currentJson.put("letzterAbschlussBenutzer", snapshot.optString("letzterAbschlussBenutzer").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            if (changed) {
                currentJson.put("istFertig", false)
                currentJson.put("abschlussPdfDateiname", JSONObject.NULL)
                loescheAktuellesAbschlussDokument(context, entry)
            } else {
                currentJson.put("istFertig", true)
                val name = snapshot.optString("abschlussPdfDateiname").takeIf { it.isNotBlank() }
                currentJson.put("abschlussPdfDateiname", name ?: JSONObject.NULL)
            }
            currentJson.put("dokumentVersion", 0)
            current.writeText(currentJson.toString(2), Charsets.UTF_8)
        }
    }

    fun spiegeleBaselineFuerRenderer(context: Context, entry: BautagebuchEintrag) {
        val baseline = baselineFile(context, entry)?.takeIf { it.exists() } ?: return
        val mirror = rendererBaselineMirrorFile(context, entry) ?: return
        runCatching {
            mirror.parentFile?.mkdirs()
            baseline.copyTo(mirror, overwrite = true)
        }
    }

    fun loescheAktuellesAbschlussDokument(context: Context, entry: BautagebuchEintrag) {
        val token = dokumentToken(entry)
        outputDir(context, entry).listFiles().orEmpty().forEach { file ->
            if (!file.isFile) return@forEach
            if (file.name.contains("_entwurf_", ignoreCase = true)) return@forEach
            val isBtb = file.name.contains("_${token}_", ignoreCase = true) ||
                file.name.contains("_${token}.", ignoreCase = true)
            if (isBtb && (file.extension.equals("pdf", true) || file.extension.equals("docx", true))) {
                runCatching { file.delete() }
            }
        }
    }

    fun aktuellesAbschlussPdf(context: Context, entry: BautagebuchEintrag): File? {
        if (status(context, entry) != BtbAbschlussStatus.ABGESCHLOSSEN) return null
        return BtbAbschlussPdfFinder.neueste(context, entry)
    }

    /** Legacy-Kompatibilität für vorhandenen UI-Code: abgeschlossen = 1, sonst 0. */
    fun finalisierteVersion(context: Context, entry: BautagebuchEintrag): Int =
        if (status(context, entry) == BtbAbschlussStatus.ABGESCHLOSSEN && aktuellesAbschlussPdf(context, entry) != null) 1 else 0

    /** Legacy-Test/API: im neuen Modell existiert nur ein aktuelles Dokument. */
    internal fun naechsteVersionAusDateinamen(dateinamen: List<String>, token: String, modellVersion: Int = 0): Int = 1

    /** Alte Pending-API bleibt als No-op erhalten, bis alle Altaufrufer entfernt sind. */
    fun synchronisiereBestaetigtenAbschluss(context: Context, entry: BautagebuchEintrag) {
        pflegeNachAutosave(context, entry)
    }

    /** Alte Vorbereitungs-API: keine sichtbaren Versionen mehr. */
    fun vorbereiten(
        context: Context,
        entry: BautagebuchEintrag,
        expectedPdfName: (Int) -> String,
        benutzer: String
    ): Vorbereitung = Vorbereitung(1, System.currentTimeMillis(), benutzer.trim().ifBlank { "TB" })

    private fun vergleichsInhalt(source: JSONObject): String {
        val copy = JSONObject(source.toString())
        listOf(
            "abschlussStatus",
            "abschlussPdfDateiname",
            "letzterAbschlussSnapshot",
            "istFertig",
            "dokumentVersion",
            "letzterAbschlussEpochMs",
            "letzterAbschlussBenutzer",
            "geaendertAmEpochMs"
        ).forEach { copy.remove(it) }
        return copy.toString()
    }
}
