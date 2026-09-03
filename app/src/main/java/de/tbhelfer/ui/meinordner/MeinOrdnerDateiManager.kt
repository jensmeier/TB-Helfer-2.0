package de.tbhelfer.ui.meinordner

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MeinOrdnerAnsicht {
    LISTE,
    KARTEN
}

enum class MeinOrdnerSortierung {
    NAME_AUF,
    NAME_AB,
    DATUM_NEU,
    DATUM_ALT
}

enum class MeinOrdnerTyp {
    ORDNER,
    TEXT,
    BILD,
    PDF,
    SONSTIGES
}

data class MeinOrdnerBaustelleRef(
    val id: Long?,
    val name: String?
)

data class MeinOrdnerEintrag(
    val datei: File,
    val typ: MeinOrdnerTyp,
    val anzeigeName: String,
    val relativePfad: String,
    val istSuchTreffer: Boolean,
    val zusatzText: String?,
    val zuletztGeaendertMs: Long,
    val baustelleRef: MeinOrdnerBaustelleRef? = null,
    val papierkorbWrapper: File? = null,
    val originalRelativerOrdner: String? = null
)

object MeinOrdnerDateiManager {
    private const val ROOT_REL = "TB-Helfer/Mein Ordner"
    private const val PAPIERKORB_NAME = ".papierkorb"
    private const val FOLDER_META_NAME = ".tb_folder_meta.json"
    private const val TRASH_META_NAME = ".tb_trash_meta.json"

    @Suppress("DEPRECATION")
    fun rootOrdner(): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(docs, ROOT_REL).apply { mkdirs() }
    }

    fun papierkorbOrdner(): File = File(rootOrdner(), PAPIERKORB_NAME).apply { mkdirs() }

    fun relativZuRoot(file: File): String {
        val root = rootOrdner().canonicalFile
        val target = file.canonicalFile
        val rootPath = root.path.replace('\\', '/').trimEnd('/')
        val targetPath = target.path.replace('\\', '/')
        return when {
            targetPath == rootPath -> ""
            targetPath.startsWith("$rootPath/") -> targetPath.removePrefix("$rootPath/")
            else -> file.name
        }
    }

    fun ordnerFuerRelativenPfad(relativerPfad: String?): File {
        val root = rootOrdner().canonicalFile
        val clean = relativerPfad.orEmpty().trim().replace('\\', '/')
            .removePrefix("/")
            .removeSuffix("/")
        if (clean.isBlank()) return root
        val candidate = File(root, clean).canonicalFile
        return if (candidate.path.startsWith(root.path)) candidate else root
    }

    fun istTextDatei(file: File): Boolean = file.extension.lowercase(Locale.ROOT) in setOf("txt", "md")

    fun listOrdner(ordner: File, sortierung: MeinOrdnerSortierung): List<MeinOrdnerEintrag> {
        ordner.mkdirs()
        val files = ordner.listFiles().orEmpty()
            .filter { sichtbareDatei(it) }
            .mapNotNull { createEntry(it, istSuchTreffer = false) }
        return sortiere(files, sortierung)
    }

    fun suche(ordner: File, suchtext: String, sortierung: MeinOrdnerSortierung): List<MeinOrdnerEintrag> {
        val q = suchtext.trim()
        if (q.isBlank()) return listOrdner(ordner, sortierung)
        val treffer = mutableListOf<MeinOrdnerEintrag>()
        durchlaufe(ordner) { file ->
            if (!sichtbareDatei(file)) return@durchlaufe
            val entry = createEntry(file, istSuchTreffer = true) ?: return@durchlaufe
            if (passtZuSuche(entry, q, file)) {
                treffer += entry
            }
        }
        return sortiere(treffer, sortierung)
    }

    fun listPapierkorb(sortierung: MeinOrdnerSortierung, suchtext: String): List<MeinOrdnerEintrag> {
        val query = suchtext.trim()
        val wrapper = papierkorbOrdner().listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { createTrashEntry(it) }
            .filter {
                query.isBlank() || it.anzeigeName.contains(query, true) ||
                    it.zusatzText.orEmpty().contains(query, true)
            }
        return sortiere(wrapper, sortierung)
    }

    fun leseOrdnerMeta(ordner: File): MeinOrdnerBaustelleRef? {
        val meta = File(ordner, FOLDER_META_NAME)
        if (!meta.exists()) return null
        return runCatching {
            val obj = JSONObject(meta.readText(StandardCharsets.UTF_8))
            MeinOrdnerBaustelleRef(
                id = obj.optLong("baustelleId").takeIf { it > 0L },
                name = obj.optString("baustelleName").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    fun schreibeOrdnerMeta(ordner: File, ref: MeinOrdnerBaustelleRef?) {
        val meta = File(ordner, FOLDER_META_NAME)
        if (ref == null || (ref.id == null && ref.name.isNullOrBlank())) {
            if (meta.exists()) meta.delete()
            return
        }
        val obj = JSONObject().apply {
            put("baustelleId", ref.id ?: JSONObject.NULL)
            put("baustelleName", ref.name ?: JSONObject.NULL)
        }
        meta.writeText(obj.toString(2), StandardCharsets.UTF_8)
    }

    fun ordnerAnlegen(parent: File, name: String, ref: MeinOrdnerBaustelleRef?): File {
        val clean = saubererName(name)
        require(clean.isNotBlank()) { "Ordnername ist leer." }
        val ziel = eindeutigerDateiname(parent, clean, null)
        ziel.mkdirs()
        schreibeOrdnerMeta(ziel, ref)
        return ziel
    }

    fun textDateiAnlegen(parent: File, name: String, initialText: String = ""): File {
        val cleanBase = saubererName(name).ifBlank { "Text" }
        val normalized = if (cleanBase.contains('.')) cleanBase else "$cleanBase.txt"
        val ziel = eindeutigerDateiname(parent, normalized.substringBeforeLast('.'), normalized.substringAfterLast('.', "txt"))
        ziel.parentFile?.mkdirs()
        ziel.writeText(initialText, StandardCharsets.UTF_8)
        return ziel
    }

    fun textLesen(file: File): String =
        if (!file.exists()) "" else file.readText(StandardCharsets.UTF_8)

    fun textSpeichern(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text, StandardCharsets.UTF_8)
    }

    fun umbenennen(datei: File, neuerName: String): File {
        val clean = saubererName(neuerName)
        require(clean.isNotBlank()) { "Name ist leer." }
        val ziel = if (datei.isDirectory) {
            eindeutigerDateiname(datei.parentFile ?: rootOrdner(), clean, null)
        } else {
            val ext = datei.extension.takeIf { it.isNotBlank() }
            val withoutExt = if (clean.endsWith(".${ext.orEmpty()}", ignoreCase = true) && ext != null) {
                clean.removeSuffix(".$ext")
            } else clean
            eindeutigerDateiname(datei.parentFile ?: rootOrdner(), withoutExt, ext)
        }
        moveDatei(datei, ziel)
        return ziel
    }

    fun importDatei(context: Context, input: InputStream, originalName: String, zielOrdner: File): File {
        val ext = originalName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
        val base = originalName.substringBeforeLast('.', originalName).ifBlank { "Import" }
        val ziel = eindeutigerDateiname(zielOrdner, saubererName(base), ext)
        ziel.parentFile?.mkdirs()
        input.use { src ->
            ziel.outputStream().use { out -> src.copyTo(out) }
        }
        return ziel
    }

    fun inPapierkorbVerschieben(datei: File): Boolean {
        if (!datei.exists()) return false
        val wrapper = File(papierkorbOrdner(), "trash_${System.currentTimeMillis()}_${datei.nameWithoutExtension}")
        wrapper.mkdirs()
        val meta = JSONObject().apply {
            put("originalName", datei.name)
            put("originalParent", relativZuRoot(datei.parentFile ?: rootOrdner()))
            put("deletedAt", System.currentTimeMillis())
            put("isDirectory", datei.isDirectory)
        }
        File(wrapper, TRASH_META_NAME).writeText(meta.toString(2), StandardCharsets.UTF_8)
        val payload = File(wrapper, datei.name)
        return moveDatei(datei, payload)
    }

    fun wiederherstellenAusPapierkorb(wrapper: File): Boolean {
        val meta = leseTrashMeta(wrapper) ?: return false
        val payload = wrapper.listFiles().orEmpty().firstOrNull { it.name != TRASH_META_NAME } ?: return false
        val zielParent = ordnerFuerRelativenPfad(meta.originalParent).apply { mkdirs() }
        val ziel = if (payload.isDirectory) {
            eindeutigerDateiname(zielParent, payload.name, null)
        } else {
            eindeutigerDateiname(zielParent, payload.nameWithoutExtension, payload.extension.takeIf { it.isNotBlank() })
        }
        val ok = moveDatei(payload, ziel)
        if (ok) wrapper.deleteRecursively()
        return ok
    }

    fun endgueltigLoeschenAusPapierkorb(wrapper: File): Boolean =
        wrapper.deleteRecursively()

    fun formatDatum(ms: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(ms))

    fun sichtbarerPfad(relativ: String): String {
        val clean = relativ.replace('\\', '/').trim('/').ifBlank { "Root" }
        return "Documents/TB-Helfer/Mein Ordner/$clean".replace("/Root", "")
    }

    fun istBildDatei(file: File): Boolean = file.extension.lowercase(Locale.ROOT) in setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif"
    )

    private fun sichtbareDatei(file: File): Boolean {
        if (file.name.startsWith(".")) return false
        return file.name != PAPIERKORB_NAME && file.name != FOLDER_META_NAME && file.name != TRASH_META_NAME
    }

    private fun passtZuSuche(entry: MeinOrdnerEintrag, q: String, file: File): Boolean {
        if (entry.anzeigeName.contains(q, true)) return true
        if (entry.relativePfad.contains(q, true)) return true
        if (entry.zusatzText.orEmpty().contains(q, true)) return true
        if (entry.typ == MeinOrdnerTyp.TEXT) {
            val txt = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull().orEmpty()
            if (txt.contains(q, true)) return true
        }
        return false
    }

    private fun createEntry(file: File, istSuchTreffer: Boolean): MeinOrdnerEintrag? {
        val rel = relativZuRoot(file)
        return if (file.isDirectory) {
            val ref = leseOrdnerMeta(file)
            val subtitle = buildString {
                append("Ordner")
                ref?.name?.takeIf { it.isNotBlank() }?.let {
                    append(" · ")
                    append(it)
                }
                if (istSuchTreffer) {
                    append(" · ")
                    append(rel)
                }
            }
            MeinOrdnerEintrag(
                datei = file,
                typ = MeinOrdnerTyp.ORDNER,
                anzeigeName = file.name,
                relativePfad = rel,
                istSuchTreffer = istSuchTreffer,
                zusatzText = subtitle,
                zuletztGeaendertMs = file.lastModified(),
                baustelleRef = ref
            )
        } else {
            val typ = when {
                istTextDatei(file) -> MeinOrdnerTyp.TEXT
                istBildDatei(file) -> MeinOrdnerTyp.BILD
                file.extension.equals("pdf", true) -> MeinOrdnerTyp.PDF
                else -> MeinOrdnerTyp.SONSTIGES
            }
            MeinOrdnerEintrag(
                datei = file,
                typ = typ,
                anzeigeName = file.name,
                relativePfad = rel,
                istSuchTreffer = istSuchTreffer,
                zusatzText = if (istSuchTreffer) rel else null,
                zuletztGeaendertMs = file.lastModified()
            )
        }
    }

    private fun createTrashEntry(wrapper: File): MeinOrdnerEintrag? {
        val meta = leseTrashMeta(wrapper) ?: return null
        val payload = wrapper.listFiles().orEmpty().firstOrNull { it.name != TRASH_META_NAME } ?: return null
        val typ = if (payload.isDirectory) MeinOrdnerTyp.ORDNER else when {
            istTextDatei(payload) -> MeinOrdnerTyp.TEXT
            istBildDatei(payload) -> MeinOrdnerTyp.BILD
            payload.extension.equals("pdf", true) -> MeinOrdnerTyp.PDF
            else -> MeinOrdnerTyp.SONSTIGES
        }
        val subtitle = buildString {
            append("Papierkorb")
            meta.originalParent.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(sichtbarerPfad(it))
            }
            append(" · ")
            append(formatDatum(meta.deletedAtMs))
        }
        return MeinOrdnerEintrag(
            datei = payload,
            typ = typ,
            anzeigeName = meta.originalName ?: payload.name,
            relativePfad = relativZuRoot(payload),
            istSuchTreffer = false,
            zusatzText = subtitle,
            zuletztGeaendertMs = meta.deletedAtMs,
            papierkorbWrapper = wrapper,
            originalRelativerOrdner = meta.originalParent
        )
    }

    private fun sortiere(
        list: List<MeinOrdnerEintrag>,
        sortierung: MeinOrdnerSortierung
    ): List<MeinOrdnerEintrag> {
        val comparator = when (sortierung) {
            MeinOrdnerSortierung.NAME_AUF -> compareBy<MeinOrdnerEintrag> { it.typ != MeinOrdnerTyp.ORDNER }
                .thenBy { it.anzeigeName.lowercase(Locale.ROOT) }
            MeinOrdnerSortierung.NAME_AB -> compareBy<MeinOrdnerEintrag> { it.typ != MeinOrdnerTyp.ORDNER }
                .thenByDescending { it.anzeigeName.lowercase(Locale.ROOT) }
            MeinOrdnerSortierung.DATUM_NEU -> compareBy<MeinOrdnerEintrag> { it.typ != MeinOrdnerTyp.ORDNER }
                .thenByDescending { it.zuletztGeaendertMs }
                .thenBy { it.anzeigeName.lowercase(Locale.ROOT) }
            MeinOrdnerSortierung.DATUM_ALT -> compareBy<MeinOrdnerEintrag> { it.typ != MeinOrdnerTyp.ORDNER }
                .thenBy { it.zuletztGeaendertMs }
                .thenBy { it.anzeigeName.lowercase(Locale.ROOT) }
        }
        return list.sortedWith(comparator)
    }

    private fun durchlaufe(root: File, block: (File) -> Unit) {
        root.listFiles().orEmpty().forEach { child ->
            if (!sichtbareDatei(child)) return@forEach
            block(child)
            if (child.isDirectory) durchlaufe(child, block)
        }
    }

    private fun leseTrashMeta(wrapper: File): TrashMeta? {
        val file = File(wrapper, TRASH_META_NAME)
        if (!file.exists()) return null
        return runCatching {
            val obj = JSONObject(file.readText(StandardCharsets.UTF_8))
            TrashMeta(
                originalName = obj.optString("originalName").takeIf { it.isNotBlank() },
                originalParent = obj.optString("originalParent"),
                deletedAtMs = obj.optLong("deletedAt")
            )
        }.getOrNull()
    }

    private data class TrashMeta(
        val originalName: String?,
        val originalParent: String,
        val deletedAtMs: Long
    )

    private fun saubererName(name: String): String =
        name.trim().replace(Regex("[\\/:*?\"<>|]"), "_")

    private fun eindeutigerDateiname(parent: File, base: String, ext: String?): File {
        val safeBase = saubererName(base).ifBlank { "Eintrag" }
        val safeExt = ext?.trim()?.trimStart('.')?.takeIf { it.isNotBlank() }
        var idx = 0
        while (true) {
            val suffix = if (idx == 0) "" else " ($idx)"
            val name = if (safeExt == null) "$safeBase$suffix" else "$safeBase$suffix.$safeExt"
            val candidate = File(parent, name)
            if (!candidate.exists()) return candidate
            idx++
        }
    }

    private fun moveDatei(src: File, dst: File): Boolean {
        dst.parentFile?.mkdirs()
        if (src.renameTo(dst)) return true
        return try {
            copyRecursively(src, dst)
            src.deleteRecursively()
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun copyRecursively(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles().orEmpty().forEach { child ->
                copyRecursively(child, File(dst, child.name))
            }
        } else {
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
