package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.TeamDisplayCache
import de.tbhelfer.ui.einstellungen.nutzer.NutzerSpeicher
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Normalisiert den kompletten Baustellenexport für das langfristige Laptop-Archiv.
 *
 * Zielstruktur: TB-Helfer/<Baustelle>/...
 * Mehrere ZIPs unterschiedlicher Geräte/Personen dürfen später in denselben TB-Helfer-Ordner
 * entpackt werden. Unterschiedliche Medien erhalten deshalb inhaltsbasierte stabile Namen;
 * derselbe fachliche BTB erhält auf allen Geräten denselben menschenlesbaren Zielpfad.
 */
object MergeSafeBaustellenExport {

    suspend fun normalisieren(
        context: Context,
        sourceZip: File,
        localRootKnotenId: Long,
        baustelleName: String,
        schicht: SchichtTyp? = null
    ): File {
        if (!sourceZip.exists()) return sourceZip
        val appContext = context.applicationContext
        val syncContext = runCatching {
            ServiceLocator.worksiteBindingRepository.contextForLocalRoot(localRootKnotenId)
        }.getOrNull()
        val team = syncContext?.worksiteServerId?.let { TeamDisplayCache.ownTeam(appContext, it) }
        val responsible = team?.leadDisplayName
            ?: team?.leadNameSnapshot
            ?: syncContext?.leadNameSnapshot
            ?: syncContext?.groupId?.takeIf { it.isNotBlank() }?.let { "TB_${it.take(8)}" }
            ?: "TB_lokal"
        val responsibleToken = safeToken(responsible)
        val company = NutzerSpeicher.laden(appContext)?.firmenname?.trim().orEmpty()
        val worksiteToken = safeFolder(baustelleName)

        val out = File(
            sourceZip.parentFile ?: appContext.cacheDir,
            sourceZip.nameWithoutExtension.removeSuffix("_Archiv") + "_Archiv.zip"
        )
        val temp = File(out.absolutePath + ".tmp")

        ZipFile(sourceZip).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            val acceptanceFolders = acceptanceFolderMap(zip, entries)
            val mediaTargets = mediaTargetMap(zip, entries)
            val targetPaths = linkedMapOf<String, String>()

            for (entry in entries) {
                val raw = cleanPath(entry.name)
                targetPaths[raw] = targetPath(
                    rawPath = raw,
                    worksiteToken = worksiteToken,
                    responsibleToken = responsibleToken,
                    acceptanceFolders = acceptanceFolders,
                    mediaTargets = mediaTargets,
                    forcedShift = schicht
                )
            }

            ZipOutputStream(temp.outputStream().buffered()).use { zos ->
                val written = hashSetOf<String>()
                for (entry in entries) {
                    val raw = cleanPath(entry.name)
                    val target = targetPaths[raw] ?: continue
                    if (!written.add(target.lowercase(Locale.ROOT))) continue
                    var bytes = zip.getInputStream(entry).use { it.readBytes() }

                    if (target.endsWith(".csv", true) || target.endsWith(".txt", true)) {
                        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
                        if (text != null) {
                            bytes = rewriteTextReferences(text, raw, mediaTargets).toByteArray(Charsets.UTF_8)
                        }
                    } else if (target.endsWith(".docx", true) && "/BTB/" in target) {
                        val shift = shiftFromBtbName(target)
                        bytes = rewriteBtbDocx(bytes, company, responsible, shift)
                    }

                    zos.putNextEntry(ZipEntry(target).apply { time = entry.time })
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }

        if (out.exists()) out.delete()
        if (!temp.renameTo(out)) {
            temp.copyTo(out, overwrite = true)
            temp.delete()
        }
        return out
    }

    private fun acceptanceFolderMap(zip: ZipFile, entries: List<ZipEntry>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        entries.filter { cleanPath(it.name).contains("/Abnahmen/") && it.name.endsWith(".json", true) }
            .forEach { entry ->
                val path = cleanPath(entry.name)
                val parent = path.substringBeforeLast('/', "")
                val folder = parent.substringAfterLast('/')
                if (!folder.startsWith("Abnahme_", true)) return@forEach
                val id = runCatching {
                    JSONObject(zip.getInputStream(entry).use { it.reader(Charsets.UTF_8).readText() })
                        .optString("id").trim()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
                result[parent] = "${folder}_${safeToken(id).take(8)}"
            }
        return result
    }

    private fun mediaTargetMap(zip: ZipFile, entries: List<ZipEntry>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        entries.forEach { entry ->
            val path = cleanPath(entry.name)
            if (!isMediaPath(path)) return@forEach
            val digest = zip.getInputStream(entry).use { sha256(it.readBytes()) }.take(16)
            val original = path.substringAfterLast('/')
            val ext = original.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".${it.lowercase()}" }.orEmpty()
            val stem = original.substringBeforeLast('.', original)
            val readable = safeToken(stem).take(48).ifBlank { "Datei" }
            result[path] = "${readable}_${digest}${ext}"
        }
        return result
    }

    private fun isMediaPath(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        if ("/bilder/" in lower) return isMediaExtension(lower)
        if ("/abnahmen/" in lower && ("/fotos/" in lower || "/objekt/" in lower || "/anlagen/" in lower)) {
            return isMediaExtension(lower)
        }
        return false
    }

    private fun isMediaExtension(path: String): Boolean =
        listOf(".jpg", ".jpeg", ".png", ".webp", ".pdf").any { path.endsWith(it) }

    private fun targetPath(
        rawPath: String,
        worksiteToken: String,
        responsibleToken: String,
        acceptanceFolders: Map<String, String>,
        mediaTargets: Map<String, String>,
        forcedShift: SchichtTyp?
    ): String {
        var inner = rawPath
        if (inner.startsWith("TB-Helfer/", true)) inner = inner.substringAfter('/')
        else if ('/' in inner) inner = inner.substringAfter('/')

        var path = "TB-Helfer/$worksiteToken/$inner"

        // Stabile Acceptance-ID an den bisher nur lokal nummerierten Ordner anhängen.
        for ((oldParent, stableFolder) in acceptanceFolders) {
            val oldInner = if (oldParent.startsWith("TB-Helfer/", true)) oldParent.substringAfter('/')
                else oldParent.substringAfter('/', oldParent)
            val oldFull = "TB-Helfer/$worksiteToken/$oldInner"
            if (path == oldFull || path.startsWith("$oldFull/")) {
                val parent = oldFull.substringBeforeLast('/')
                path = parent + "/" + stableFolder + path.removePrefix(oldFull)
                break
            }
        }

        mediaTargets[rawPath]?.let { stableName ->
            path = path.substringBeforeLast('/') + "/" + stableName
        }

        if ("/BTB/" in path) {
            path = renameBtb(path, responsibleToken, forcedShift)
        }
        return path.replace("//", "/")
    }

    /**
     * Der Gesamtexport enthält die Schicht bereits im Dateinamen. Der einzelne
     * Verlaufsexport nutzt aus Kompatibilitätsgründen noch BTB_<Datum>.pdf/docx;
     * dort wird die fachlich bekannte Schicht explizit mitgegeben.
     */
    private fun renameBtb(path: String, responsibleToken: String, forcedShift: SchichtTyp?): String {
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("pdf", "docx")) return path

        val withShift = Regex("(?i)^BTB_(\\d{4}-\\d{2}-\\d{2})_(TAG|NACHT|Tag|Nacht)(?:_[^.]*)?\\.(pdf|docx)$")
            .matchEntire(name)
        if (withShift != null) {
            val date = withShift.groupValues[1]
            val shift = withShift.groupValues[2].uppercase(Locale.ROOT)
            val newName = "BTB_${date}_${shift}_${responsibleToken}.${ext}"
            return path.substringBeforeLast('/') + "/" + newName
        }

        val withoutShift = Regex("(?i)^BTB_(\\d{4}-\\d{2}-\\d{2})\\.(pdf|docx)$")
            .matchEntire(name)
        if (withoutShift != null && forcedShift != null) {
            val date = withoutShift.groupValues[1]
            val newName = "BTB_${date}_${forcedShift.name}_${responsibleToken}.${ext}"
            return path.substringBeforeLast('/') + "/" + newName
        }

        return path
    }

    private fun rewriteTextReferences(text: String, csvPath: String, mediaTargets: Map<String, String>): String {
        var out = text
        val date = Regex("\\d{4}-\\d{2}-\\d{2}").find(csvPath)?.value
        val candidates = mediaTargets.entries.filter { date == null || date in it.key }
        val byOldName = candidates.groupBy { it.key.substringAfterLast('/') }
        byOldName.forEach { (oldName, values) ->
            val unique = values.map { it.value }.distinct()
            if (unique.size == 1) out = out.replace(oldName, unique.first())
        }
        return out
    }

    private fun rewriteBtbDocx(bytes: ByteArray, company: String, responsible: String, shift: String): ByteArray {
        return runCatching {
            val output = ByteArrayOutputStream()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                ZipOutputStream(output).use { zout ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        val data = zin.readBytes()
                        val rewritten = if (entry.name == "word/document.xml") {
                            injectDocxHeader(data.toString(Charsets.UTF_8), company, responsible, shift)
                                .toByteArray(Charsets.UTF_8)
                        } else data
                        zout.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                        zout.write(rewritten)
                        zout.closeEntry()
                        entry = zin.nextEntry
                    }
                }
            }
            output.toByteArray()
        }.getOrDefault(bytes)
    }

    private fun injectDocxHeader(xml: String, company: String, responsible: String, shift: String): String {
        if (xml.contains("Verantwortlicher TB:")) return xml
        val marker = "BAUTAGEBUCH"
        val markerPos = xml.indexOf(marker)
        if (markerPos < 0) return xml
        val paragraphEnd = xml.indexOf("</w:p>", markerPos)
        if (paragraphEnd < 0) return xml
        val insertPos = paragraphEnd + "</w:p>".length
        val lines = buildList {
            if (company.isNotBlank()) add("Firma: $company")
            add("Verantwortlicher TB: $responsible")
            if (shift.isNotBlank()) add("Schicht: $shift")
        }
        val extra = lines.joinToString("") { line ->
            "<w:p><w:r><w:t>${xmlEscape(line)}</w:t></w:r></w:p>"
        }
        return xml.substring(0, insertPos) + extra + xml.substring(insertPos)
    }

    private fun shiftFromBtbName(path: String): String = when {
        "_NACHT_" in path.uppercase(Locale.ROOT) -> "Nacht"
        "_TAG_" in path.uppercase(Locale.ROOT) -> "Tag"
        else -> ""
    }

    private fun cleanPath(value: String): String = value.replace('\\', '/').trimStart('/')

    private fun safeFolder(value: String): String = safeToken(value).ifBlank { "Baustelle" }

    private fun safeToken(value: String): String = value
        .trim()
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_', '.', ' ')
        .take(100)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
