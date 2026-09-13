package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ui.suche.BtbSpeicher
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Abschluss-Lebenszyklus fuer einen stabilen BTB.
 *
 * Sichtbare V1/V2/V3 existieren nicht. Der ERSTE erfolgreiche Abschluss eines btbId wird als
 * unveraenderliches Original gespeichert. Alle spaeteren PDF-Abschluesse ersetzen nur das eine
 * aktuell gueltige PDF. Die rote Darstellung vergleicht IMMER gegen dieses erste Original.
 *
 * Zusaetzlich wird ein kleiner technischer Abschluss-Metadatensatz gepflegt. Er enthaelt nur den
 * Hash des Inhalts, aus dem das aktuell gueltige PDF erzeugt wurde. Dadurch kann die App erkennen,
 * ob nach einem spaeteren Abschluss erneut editiert wurde, ohne das Original jemals zu ersetzen.
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

    /** Unveraenderliches Original des ERSTEN erfolgreichen Abschlusses. */
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
     * Technische Metadaten des aktuell gueltigen Abschluss-PDFs. Kein Dokumentarchiv und keine
     * sichtbare Versionierung. Die Datei wird bei jedem erfolgreichen Abschluss aktualisiert.
     */
    private fun closureMetaFile(context: Context, entry: BautagebuchEintrag): File? {
        val dir = dateDir(context, entry) ?: return null
        return File(dir, "btb_${storageToken(entry)}_abschluss_meta.json")
    }

    /**
     * Uebergangsbruecke fuer den grossen bestehenden PDF-Renderer. Er liest historisch eine
     * baustellennamenbasierte Baseline. Gespiegelt wird immer nur das unveraenderliche Original
     * der aktuellen btbId.
     */
    private fun rendererBaselineMirrorFile(context: Context, entry: BautagebuchEintrag): File? {
        val d = datum(entry) ?: return null
        val dir = BautagebuchSpeicher.ordner(context, entry.baustelleName, d)
        val name = if (entry.schicht == SchichtTyp.NACHT) "bautagebuch_nacht_original.json" else "bautagebuch_tag_original.json"
        return File(dir, name)
    }

    private fun outputDir(context: Context, entry: BautagebuchEintrag): File =
        BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)

    /** Inhalt, der fachlich zum BTB gehoert. Abschluss-/UI-Metadaten duerfen den Vergleich nicht beeinflussen. */
    private fun vergleichsInhalt(source: JSONObject): String {
        val copy = JSONObject(source.toString())
        listOf(
            "abschlussStatus",
            "abschlussPdfDateiname",
            "abschlussInhaltHash",
            "originalAbschlussSnapshot",
            "letzterAbschlussSnapshot", // Legacy-Feld: enthaelt ab jetzt ebenfalls das Original
            "istFertig",
            "dokumentVersion",
            "ersterAbschlussEpochMs",
            "ersterAbschlussBenutzer",
            "letzterAbschlussEpochMs",
            "letzterAbschlussBenutzer",
            "geaendertAmEpochMs"
        ).forEach { copy.remove(it) }
        return copy.toString()
    }

    private fun inhaltHash(source: JSONObject): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(vergleichsInhalt(source).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun readJson(file: File?): JSONObject? = file
        ?.takeIf { it.exists() && it.isFile }
        ?.let { runCatching { JSONObject(it.readText(Charsets.UTF_8)) }.getOrNull() }

    private fun readMeta(context: Context, entry: BautagebuchEintrag): JSONObject? =
        readJson(closureMetaFile(context, entry))

    private fun writeMeta(context: Context, entry: BautagebuchEintrag, meta: JSONObject) {
        val file = closureMetaFile(context, entry) ?: return
        file.parentFile?.mkdirs()
        file.writeText(meta.toString(2), Charsets.UTF_8)
    }

    /** Rotvergleich: immer gegen den ERSTEN Abschluss, niemals gegen den letzten Abschluss. */
    fun istVomOriginalAbweichend(context: Context, entry: BautagebuchEintrag): Boolean {
        val baseline = readJson(baselineFile(context, entry)) ?: return false
        val current = readJson(currentFile(context, entry)) ?: return false
        return vergleichsInhalt(current) != vergleichsInhalt(baseline)
    }

    /** Legacy-API-Name; Semantik ist jetzt bewusst 'gegen Original'. */
    fun istSeitLetztemAbschlussGeaendert(context: Context, entry: BautagebuchEintrag): Boolean =
        istVomOriginalAbweichend(context, entry)

    fun status(context: Context, entry: BautagebuchEintrag): BtbAbschlussStatus {
        val baseline = readJson(baselineFile(context, entry)) ?: return BtbAbschlussStatus.ENTWURF
        val current = readJson(currentFile(context, entry)) ?: return BtbAbschlussStatus.ENTWURF
        val meta = readMeta(context, entry)

        if (meta != null) {
            val pdfGueltig = meta.optBoolean("pdfGueltig", false)
            val closureHash = meta.optString("abschlussInhaltHash")
            val hashPasst = closureHash.isNotBlank() && closureHash == inhaltHash(current)
            val pdf = BtbAbschlussPdfFinder.neueste(context, entry)
            return if (pdfGueltig && hashPasst && pdf != null && pdf.exists()) {
                BtbAbschlussStatus.ABGESCHLOSSEN
            } else {
                BtbAbschlussStatus.NACHBEARBEITUNG
            }
        }

        // Migration alter Daten: Original vorhanden, aber noch keine neue Abschluss-Metadatei.
        val changed = vergleichsInhalt(current) != vergleichsInhalt(baseline)
        val pdf = BtbAbschlussPdfFinder.neueste(context, entry)
        return if (!changed && pdf != null && pdf.exists()) {
            BtbAbschlussStatus.ABGESCHLOSSEN
        } else {
            BtbAbschlussStatus.NACHBEARBEITUNG
        }
    }

    /**
     * Bestaetigt einen erfolgreichen PDF-Abschluss.
     *
     * Beim ERSTEN Abschluss wird das Original einmalig geschrieben. Bei allen spaeteren Abschluessen
     * bleibt dieses Original unveraendert; nur aktuelles PDF + Abschluss-Hash werden ersetzt.
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
            val currentJson = JSONObject(current.readText(Charsets.UTF_8))

            // Das erste Original ist unveraenderlich. Nie overwrite=true verwenden.
            if (!baseline.exists()) {
                val original = JSONObject(currentJson.toString())
                original.put("abschlussStatus", BtbAbschlussStatus.ABGESCHLOSSEN.name)
                original.put("ersterAbschlussEpochMs", now)
                original.put("ersterAbschlussBenutzer", benutzer)
                original.put("istFertig", true)
                original.put("dokumentVersion", 0)
                baseline.parentFile?.mkdirs()
                baseline.writeText(original.toString(2), Charsets.UTF_8)
            }

            val originalSnapshot = JSONObject(baseline.readText(Charsets.UTF_8))
            val hash = inhaltHash(currentJson)
            val firstEpoch = originalSnapshot.optLong("ersterAbschlussEpochMs", now).takeIf { it > 0L } ?: now
            val firstUser = originalSnapshot.optString("ersterAbschlussBenutzer").takeIf { it.isNotBlank() } ?: benutzer

            writeMeta(
                context,
                entry,
                JSONObject().apply {
                    put("btbId", entry.btbId)
                    put("abschlussInhaltHash", hash)
                    put("abschlussPdfDateiname", pdfFile.name)
                    put("pdfGueltig", true)
                    put("letzterAbschlussEpochMs", now)
                    put("letzterAbschlussBenutzer", benutzer)
                }
            )

            currentJson.put("abschlussStatus", BtbAbschlussStatus.ABGESCHLOSSEN.name)
            currentJson.put("abschlussPdfDateiname", pdfFile.name)
            currentJson.put("abschlussInhaltHash", hash)
            currentJson.put("ersterAbschlussEpochMs", firstEpoch)
            currentJson.put("ersterAbschlussBenutzer", firstUser)
            currentJson.put("letzterAbschlussEpochMs", now)
            currentJson.put("letzterAbschlussBenutzer", benutzer)
            currentJson.put("originalAbschlussSnapshot", JSONObject(originalSnapshot.toString()))
            // Legacy-Feld bleibt lesbar, zeigt aber bewusst ebenfalls auf das ERSTE Original.
            currentJson.put("letzterAbschlussSnapshot", JSONObject(originalSnapshot.toString()))
            currentJson.put("istFertig", true)
            currentJson.put("dokumentVersion", 0)
            current.writeText(currentJson.toString(2), Charsets.UTF_8)

            spiegeleBaselineFuerRenderer(context, entry)
            true
        }.getOrDefault(false)
    }

    /**
     * Nach jedem Autosave: Original wieder in die aktuelle BTB-Datei einbetten und pruefen, ob
     * der Inhalt noch exakt dem aktuell gueltigen PDF-Abschluss entspricht. Sobald erstmals eine
     * echte Aenderung erkannt wurde, bleibt der Zustand NACHBEARBEITUNG bis zum naechsten expliziten
     * BTB-Abschluss, auch wenn der Nutzer spaeter wieder auf alte Werte zurueckgeht.
     */
    fun pflegeNachAutosave(context: Context, entry: BautagebuchEintrag) {
        val baseline = baselineFile(context, entry)?.takeIf { it.exists() } ?: return
        val current = currentFile(context, entry)?.takeIf { it.exists() } ?: return
        runCatching {
            val original = JSONObject(baseline.readText(Charsets.UTF_8))
            val currentJson = JSONObject(current.readText(Charsets.UTF_8))
            var meta = readMeta(context, entry)

            // Migration: Falls bereits ein Abschluss-PDF existiert, aber noch keine Meta-Datei,
            // behandeln wir den aktuell gespeicherten Inhalt einmalig als dessen Abschlussstand.
            if (meta == null) {
                val existingPdf = BtbAbschlussPdfFinder.neueste(context, entry)
                meta = JSONObject().apply {
                    put("btbId", entry.btbId)
                    put("abschlussInhaltHash", inhaltHash(currentJson))
                    put("abschlussPdfDateiname", existingPdf?.name ?: JSONObject.NULL)
                    put("pdfGueltig", existingPdf != null && existingPdf.exists())
                }
                writeMeta(context, entry, meta)
            }

            val wasValid = meta.optBoolean("pdfGueltig", false)
            val closureHash = meta.optString("abschlussInhaltHash")
            val changedSincePdf = closureHash.isBlank() || closureHash != inhaltHash(currentJson)
            val nowInvalid = !wasValid || changedSincePdf

            if (wasValid && changedSincePdf) {
                loescheAktuellesAbschlussDokument(context, entry)
                meta.put("pdfGueltig", false)
                meta.put("abschlussPdfDateiname", JSONObject.NULL)
                writeMeta(context, entry, meta)
            }

            val firstEpoch = original.optLong("ersterAbschlussEpochMs", 0L).takeIf { it > 0L }
            val firstUser = original.optString("ersterAbschlussBenutzer").takeIf { it.isNotBlank() }
            currentJson.put("originalAbschlussSnapshot", JSONObject(original.toString()))
            currentJson.put("letzterAbschlussSnapshot", JSONObject(original.toString()))
            currentJson.put("ersterAbschlussEpochMs", firstEpoch ?: JSONObject.NULL)
            currentJson.put("ersterAbschlussBenutzer", firstUser ?: JSONObject.NULL)
            currentJson.put("abschlussInhaltHash", meta.optString("abschlussInhaltHash").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            currentJson.put("abschlussStatus", if (nowInvalid) BtbAbschlussStatus.NACHBEARBEITUNG.name else BtbAbschlussStatus.ABGESCHLOSSEN.name)
            currentJson.put("istFertig", !nowInvalid)
            currentJson.put("abschlussPdfDateiname", if (nowInvalid) JSONObject.NULL else meta.optString("abschlussPdfDateiname").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            currentJson.put("letzterAbschlussEpochMs", meta.optLong("letzterAbschlussEpochMs", 0L).takeIf { it > 0L } ?: JSONObject.NULL)
            currentJson.put("letzterAbschlussBenutzer", meta.optString("letzterAbschlussBenutzer").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
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

    /** Legacy-Kompatibilitaet fuer vorhandenen UI-Code: abgeschlossen = 1, sonst 0. */
    fun finalisierteVersion(context: Context, entry: BautagebuchEintrag): Int =
        if (status(context, entry) == BtbAbschlussStatus.ABGESCHLOSSEN && aktuellesAbschlussPdf(context, entry) != null) 1 else 0

    /** Legacy-Test/API: im neuen Modell existiert nur ein aktuelles Dokument. */
    internal fun naechsteVersionAusDateinamen(dateinamen: List<String>, token: String, modellVersion: Int = 0): Int = 1

    /** Alte Pending-API bleibt als Kompatibilitaetsbruecke erhalten. */
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
}
