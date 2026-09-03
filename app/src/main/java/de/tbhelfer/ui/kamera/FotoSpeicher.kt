package de.tbhelfer.ui.kamera

import android.content.Context
import android.os.Environment
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.util.ImageExifUtil

object FotoSpeicher {

    private val dateiFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    private fun sichererOrdnerName(input: String): String {
        // Windows/Android kompatibel: keine Schrägstriche, keine exotischen Sonderzeichen
        val cleaned = input.trim()
            .replace("/", "-")
            .replace("\\", "-")
            .replace(Regex("[^a-zA-Z0-9 _.-]"), "")
        return if (cleaned.isBlank()) "UnbekannteBaustelle" else cleaned
    }



private fun ordnerNameFuerBildtyp(bildtyp: Bildtyp): String {
    return when (bildtyp) {
        Bildtyp.MAENGEL_SCHAEDEN -> "Mängel"
        Bildtyp.ABNAHMEN -> "Abnahmen"
        else -> "Bilder"
    }
}

fun ordnerFuerKategorie(context: Context, baustellenname: String, kategorie: String): File {
    val basis = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        ?: throw IllegalStateException("Kein externer App-Ordner verfügbar")

    val ordner = File(basis, "TB-Helfer/${sichererOrdnerName(baustellenname)}/$kategorie")
    if (!ordner.exists()) ordner.mkdirs()
    return ordner
}

fun ordnerFuerDatumKategorie(context: Context, baustellenname: String, kategorie: String, datumIso: String): File {
    val basisOrdner = ordnerFuerKategorie(context, baustellenname, kategorie)
    val datumOrdner = File(basisOrdner, datumIso)
    if (!datumOrdner.exists()) datumOrdner.mkdirs()
    return datumOrdner
}

fun zielDateiFuerBildtyp(context: Context, baustellenname: String, bildtyp: Bildtyp, datumIso: String, originalName: String): File {
    val kategorie = ordnerNameFuerBildtyp(bildtyp)
    val ordner = ordnerFuerDatumKategorie(context, baustellenname, kategorie, datumIso)
    // Kollisionen vermeiden
    var target = File(ordner, originalName)
    if (!target.exists()) return target
    val dot = originalName.lastIndexOf('.')
    val base = if (dot > 0) originalName.substring(0, dot) else originalName
    val ext = if (dot > 0) originalName.substring(dot) else ""
    var i = 2
    while (target.exists()) {
        target = File(ordner, "${base}($i)$ext")
        i++
    }
    return target
}

    /**
     * Thumbnail-Datei neben dem Original.
     * Beispiel: foto_2026-02-04_12-00-00.jpg -> foto_2026-02-04_12-00-00_thumb.jpg
     */
    fun thumbnailDateiFuer(original: File): File {
        val name = original.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ".jpg"
        return File(original.parentFile, "${base}_thumb$ext")
    }

    /**
     * Erzeugt (oder überschreibt) ein kleines JPEG-Thumbnail.
     * Wird beim Speichern eines Fotos einmalig auf IO erstellt, damit Listen butterweich scrollen.
     */
    fun erstelleThumbnailWennMoeglich(original: File, maxDimPx: Int = 512, jpegQualitaet: Int = 82) {
        try {
            if (!original.exists()) return
            val target = thumbnailDateiFuer(original)

            val bmp = ImageExifUtil.decodeSampledBitmapRespectingExif(
                path = original.absolutePath,
                reqW = maxDimPx,
                reqH = maxDimPx,
                preferredConfig = android.graphics.Bitmap.Config.RGB_565
            ) ?: return

            target.parentFile?.mkdirs()
            java.io.FileOutputStream(target).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, jpegQualitaet, out)
            }
            bmp.recycle()
        } catch (_: Throwable) {
            // Thumbnail ist ein Performance-Feature -> niemals den Speichervorgang scheitern lassen.
        }
    }

    /** Root: TB-Helfer/<Baustelle>/ (enthält Bilder, Mängel, Abnahmen, ...) */
    fun ordnerFuerBaustelleRoot(context: Context, baustellenname: String): File {
        val basis = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IllegalStateException("Kein externer App-Ordner verfügbar")
        val ordner = File(basis, "TB-Helfer/${sichererOrdnerName(baustellenname)}")
        if (!ordner.exists()) ordner.mkdirs()
        return ordner
    }

    /** Neue Foto-Datei (Capture-Ziel). Initial immer in Kategorie "Bilder". */
    fun neueFotoDatei(context: Context, baustellenname: String, datumIso: String): File {
        val ordner = ordnerFuerDatumKategorie(context, baustellenname, "Bilder", datumIso)
        val name = "foto_${LocalDateTime.now().format(dateiFormat)}.jpg"
        return File(ordner, name)
    }

    /**
     * Alle Fotos einer Baustelle aus allen Kategorien (Bilder/Mängel/Abnahmen/...).
     * - Thumbnails (*_thumb.jpg) werden ignoriert.
     */
    fun alleFotos(context: Context, baustellenname: String): List<File> {
        val root = ordnerFuerBaustelleRoot(context, baustellenname)
        if (!root.exists()) return emptyList()

        return root.walkTopDown()
            .filter { it.isFile }
            .filter { f ->
                val ext = f.extension.lowercase()
                (ext == "jpg" || ext == "jpeg" || ext == "png") && !f.name.contains("_thumb")
            }
            .sortedByDescending { it.lastModified() }
            .toList()
    }
}
