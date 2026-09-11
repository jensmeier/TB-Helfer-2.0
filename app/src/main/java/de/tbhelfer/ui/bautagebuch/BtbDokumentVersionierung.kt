package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ui.suche.BtbSpeicher
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Persistente Dokumentversionierung fuer BTBs.
 *
 * Der PDF-Renderer kennt historisch nur "Entwurf" oder "Abschluss" und schreibt das Dokument erst
 * ganz am Ende. Deshalb darf die Vergleichsbasis nicht schon vor dem erfolgreichen Schreiben auf
 * den neuen Stand verschoben werden.
 *
 * Ablauf:
 * 1. Vor jedem PDF-Lauf wird ein Snapshot des aktuellen BTB plus der erwartete Vn-Dateiname gemerkt.
 * 2. Beim naechsten Aufruf wird geprueft, ob genau diese Abschluss-PDF wirklich existiert.
 * 3. Nur dann wird der Snapshot zur neuen Vergleichsbasis und Vn als bestaetigt gespeichert.
 * 4. Existiert die erwartete PDF nicht (Entwurf/Fehler/Abbruch), wird der Pending-Snapshot verworfen.
 *
 * Dadurch vergleicht V2 gegen V1, V3 gegen V2 usw., ohne einen Entwurf als Abschluss zu werten.
 */
object BtbDokumentVersionierung {

    data class Vorbereitung(
        val naechsteVersion: Int,
        val abschlussEpochMs: Long,
        val benutzer: String
    )

    private const val META_SUFFIX = "_pending_meta.json"
    private const val SNAPSHOT_SUFFIX = "_pending_snapshot.json"

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

    private fun pendingMetaFile(context: Context, entry: BautagebuchEintrag): File? =
        dateDir(context, entry)?.let { File(it, ".btb_${storageToken(entry)}$META_SUFFIX") }

    private fun pendingSnapshotFile(context: Context, entry: BautagebuchEintrag): File? =
        dateDir(context, entry)?.let { File(it, ".btb_${storageToken(entry)}$SNAPSHOT_SUFFIX") }

    private fun outputDir(context: Context, entry: BautagebuchEintrag): File =
        BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)

    /** Hoechste tatsaechlich vorhandene Abschlussversion fuer genau diesen BTB. */
    fun finalisierteVersion(context: Context, entry: BautagebuchEintrag): Int {
        val token = dokumentToken(entry)
        val files = outputDir(context, entry).listFiles()
            ?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) && !it.name.contains("_entwurf_", ignoreCase = true) }
            .orEmpty()
        var maxVersion = 0
        files.forEach { file ->
            if (!file.name.contains("_${token}_", ignoreCase = true) &&
                !file.name.contains("_${token}.", ignoreCase = true)) return@forEach
            val version = Regex("""_V(\d+)\.pdf$""", RegexOption.IGNORE_CASE)
                .find(file.name)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 1
            if (version > maxVersion) maxVersion = version
        }
        return maxOf(maxVersion, entry.dokumentVersion.coerceAtLeast(0))
    }

    internal fun naechsteVersionAusDateinamen(dateinamen: List<String>, token: String, modellVersion: Int = 0): Int {
        var maxVersion = modellVersion.coerceAtLeast(0)
        dateinamen.forEach { name ->
            if (name.contains("_entwurf_", ignoreCase = true)) return@forEach
            if (!name.contains("_${token}_", ignoreCase = true) &&
                !name.contains("_${token}.", ignoreCase = true)) return@forEach
            val version = Regex("""_V(\d+)\.pdf$""", RegexOption.IGNORE_CASE)
                .find(name)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: if (name.endsWith(".pdf", ignoreCase = true)) 1 else 0
            if (version > maxVersion) maxVersion = version
        }
        return maxVersion + 1
    }

    /**
     * Bestaetigt einen Pending-Abschluss nur, wenn dessen erwartete PDF inzwischen wirklich liegt.
     * Diese Funktion ist idempotent und darf auch beim Oeffnen von Verlauf/Export aufgerufen werden.
     */
    fun synchronisiereBestaetigtenAbschluss(context: Context, entry: BautagebuchEintrag) {
        val metaFile = pendingMetaFile(context, entry) ?: return
        val snapshotFile = pendingSnapshotFile(context, entry) ?: return
        if (!metaFile.exists()) return

        val meta = runCatching { JSONObject(metaFile.readText(Charsets.UTF_8)) }.getOrNull()
        if (meta == null) {
            runCatching { metaFile.delete() }
            runCatching { snapshotFile.delete() }
            return
        }

        val expectedPdf = meta.optString("expectedPdf").trim()
        val version = meta.optInt("version", 0).coerceAtLeast(0)
        val closedAt = meta.optLong("abschlussEpochMs", 0L).takeIf { it > 0L }
        val user = meta.optString("benutzer").trim().takeIf { it.isNotBlank() }
        val success = expectedPdf.isNotBlank() && File(outputDir(context, entry), expectedPdf).exists()

        if (success && version > 0 && snapshotFile.exists()) {
            val snapshot = runCatching { JSONObject(snapshotFile.readText(Charsets.UTF_8)) }.getOrNull()
            if (snapshot != null) {
                applyAbschlussMeta(snapshot, version, closedAt, user, istFertig = true)
                baselineFile(context, entry)?.let { baseline ->
                    runCatching { baseline.writeText(snapshot.toString(2), Charsets.UTF_8) }
                }

                currentFile(context, entry)?.takeIf { it.exists() }?.let { current ->
                    runCatching {
                        val currentJson = JSONObject(current.readText(Charsets.UTF_8))
                        val sameContent = vergleichsInhalt(currentJson) == vergleichsInhalt(snapshot)
                        applyAbschlussMeta(currentJson, version, closedAt, user, istFertig = sameContent)
                        current.writeText(currentJson.toString(2), Charsets.UTF_8)
                    }
                }
            }
        }

        runCatching { metaFile.delete() }
        runCatching { snapshotFile.delete() }
    }

    /**
     * Bereitet den naechsten moeglichen Abschluss vor. Dasselbe Verfahren wird auch bei einem
     * Entwurf aufgerufen; weil dort die erwartete Vn-PDF nicht entsteht, wird der Snapshot spaeter
     * automatisch verworfen und kann keinen Abschluss vortaeuschen.
     */
    fun vorbereiten(
        context: Context,
        entry: BautagebuchEintrag,
        expectedPdfName: (Int) -> String,
        benutzer: String
    ): Vorbereitung {
        synchronisiereBestaetigtenAbschluss(context, entry)

        val token = dokumentToken(entry)
        val names = outputDir(context, entry).listFiles()?.map { it.name }.orEmpty()
        val next = naechsteVersionAusDateinamen(names, token, entry.dokumentVersion)
        val now = System.currentTimeMillis()
        val safeUser = benutzer.trim().ifBlank { "TB" }

        val current = currentFile(context, entry)
        val snapshot = pendingSnapshotFile(context, entry)
        val meta = pendingMetaFile(context, entry)
        if (current != null && current.exists() && snapshot != null && meta != null) {
            runCatching {
                current.copyTo(snapshot, overwrite = true)
                meta.writeText(
                    JSONObject().apply {
                        put("expectedPdf", expectedPdfName(next))
                        put("version", next)
                        put("abschlussEpochMs", now)
                        put("benutzer", safeUser)
                    }.toString(2),
                    Charsets.UTF_8
                )
            }
        }

        return Vorbereitung(next, now, safeUser)
    }

    private fun applyAbschlussMeta(
        json: JSONObject,
        version: Int,
        closedAt: Long?,
        user: String?,
        istFertig: Boolean
    ) {
        json.put("istFertig", istFertig)
        json.put("dokumentVersion", version)
        if (closedAt != null) json.put("letzterAbschlussEpochMs", closedAt)
        if (!user.isNullOrBlank()) json.put("letzterAbschlussBenutzer", user)
    }

    /** Metafelder duerfen die fachliche Aenderungserkennung nicht selbst ausloesen. */
    private fun vergleichsInhalt(source: JSONObject): String {
        val copy = JSONObject(source.toString())
        listOf(
            "istFertig",
            "dokumentVersion",
            "letzterAbschlussEpochMs",
            "letzterAbschlussBenutzer",
            "geaendertAmEpochMs"
        ).forEach(copy::remove)
        return copy.toString()
    }
}
