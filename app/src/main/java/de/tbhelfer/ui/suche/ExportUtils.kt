package de.tbhelfer.ui.suche

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import de.tbhelfer.datenbank.BaustelleEntity
import de.tbhelfer.datenbank.FotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportUtils {

    data class ExportStats(
        val fotosExportiert: Int,
        val btbsExportiert: Int,
        val fehlend: Int
    )

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun sanitizeSegment(input: String): String {
        val cleaned = input.trim().ifBlank { "Unbekannt" }
        // verbotene Pfadzeichen ersetzen
        return cleaned.replace(Regex("[\\\\/:*?\\\"<>|]"), "_")
    }

    
    private fun relativePathFromMain(context: Context, file: File): String {
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val main = if (base != null) File(base, "TB-Helfer") else null
        val abs = file.absolutePath
        val mainAbs = main?.absolutePath
        return if (!mainAbs.isNullOrBlank() && abs.startsWith(mainAbs)) {
            "TB-Helfer/" + abs.removePrefix(mainAbs).trimStart('/', '\\')
        } else {
            // Fallback: nur Dateiname (keine absoluten Pfade exportieren)
            file.name
        }
    }

private fun fotoDate(entity: FotoEntity): LocalDate =
        Instant.ofEpochMilli(entity.erstelltAmEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()

    fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".dateianbieter", file)

    fun shareSingleFile(context: Context, file: File, mimeType: String, title: String = "Teilen") {
        val uri = fileUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun shareMultiple(context: Context, uris: List<Uri>, title: String = "Teilen") {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    suspend fun createZip(
        context: Context,
        fotos: List<FotoEntity>,
        btbs: List<BtbDatei>,
        baustellen: List<BaustelleEntity>,
        zipName: String = "TB_Export_${System.currentTimeMillis()}.zip"
    ): Pair<File, ExportStats> {

        // Defensive: ZIP-Export kann sehr teuer sein (IO + Kompression).
        // Auch wenn die Caller meist schon Dispatchers.IO verwenden, erzwingen wir es hier,
        // damit es bei neuen Call-Sites nicht versehentlich auf dem Main-Thread läuft.
        return withContext(Dispatchers.IO) {

        val baustelleById = baustellen.associateBy({ it.id }, { it })
        val outFile = File(context.cacheDir, zipName)

        var fotosOk = 0
        var btbsOk = 0
        var missing = 0

        val manifest = JSONObject().apply {
            put("createdAt", Instant.now().toString())
            put("photos", JSONArray())
            put("btb", JSONArray())
        }

        // Für doppelte Dateinamen
        val usedZipPaths = HashSet<String>()

        fun uniqueZipPath(path: String): String {
            if (usedZipPaths.add(path)) return path
            val dot = path.lastIndexOf('.')
            val base = if (dot > 0) path.substring(0, dot) else path
            val ext = if (dot > 0) path.substring(dot) else ""
            var i = 2
            while (true) {
                val candidate = "$base($i)$ext"
                if (usedZipPaths.add(candidate)) return candidate
                i++
            }
        }

        // Größerer Buffer beschleunigt große Exporte spürbar.
        ZipOutputStream(outFile.outputStream().buffered(64 * 1024)).use { zos ->
            // Fotos
            for (f in fotos) {
                val src = File(f.dateipfad)
                if (!src.exists()) {
                    missing++
                    continue
                }

                val b = baustelleById[f.baustelleId]
                val baustellenName = sanitizeSegment(b?.baustellenname ?: "Unbekannt")
                val strecke = sanitizeSegment(b?.streckennummer ?: "Strecke_unbekannt")
                val date = dateFmt.format(fotoDate(f))

                val folder = "Fotos/$baustellenName/$strecke/$date"
                val zipPath = uniqueZipPath("$folder/${sanitizeSegment(src.name)}")

                FileInputStream(src).buffered(64 * 1024).use { fis ->
                    zos.putNextEntry(ZipEntry(zipPath))
                    fis.copyTo(zos, 64 * 1024)
                    zos.closeEntry()
                }

                fotosOk++

                val obj = JSONObject().apply {
                    put("id", f.id)
                    put("zipPath", zipPath)
                    put("sourcePath", relativePathFromMain(context, src))
                    put("baustelleId", f.baustelleId)
                    put("baustelleName", b?.baustellenname ?: JSONObject.NULL)
                    put("streckennummer", b?.streckennummer ?: JSONObject.NULL)
                    put("km", f.km ?: JSONObject.NULL)
                    put("kmSuffix", f.kmSuffix)
                    put("kmManuell", f.kmManuell)
                    put("achsabstandM", f.achsabstandM ?: JSONObject.NULL)
                    put("gpsLat", f.gpsLatEffektiv ?: JSONObject.NULL)
                    put("gpsLon", f.gpsLonEffektiv ?: JSONObject.NULL)
                    put("gpsAltM", f.gpsAltM ?: JSONObject.NULL)
                    put("createdAtEpochMs", f.aufnahmeEpochEffektiv ?: JSONObject.NULL)
                    put("bildtyp", f.bildtyp.toString())
                    put("kurztext", f.kurztext)
                    put("notiz", f.notiz ?: JSONObject.NULL)
                    put("firma", f.firma ?: JSONObject.NULL)
                    put("objektKennung", f.objektKennung ?: JSONObject.NULL)
                    put("objektBereich", f.objektBereich ?: JSONObject.NULL)
                    put("indikator", f.indikator?.name ?: JSONObject.NULL)
                }
                manifest.getJSONArray("photos").put(obj)
            }

            // BTB
            for (b in btbs) {
                val src = b.file
                if (!src.exists()) {
                    missing++
                    continue
                }
                val baustellenName = sanitizeSegment(b.baustellenName)
                val strecke = sanitizeSegment(b.streckennummer ?: "Strecke_unbekannt")
                val date = dateFmt.format(b.datum)

                val folder = "BTB/$baustellenName/$strecke/$date"
                val zipPath = uniqueZipPath("$folder/${sanitizeSegment(src.name)}")

                FileInputStream(src).buffered(64 * 1024).use { fis ->
                    zos.putNextEntry(ZipEntry(zipPath))
                    fis.copyTo(zos, 64 * 1024)
                    zos.closeEntry()
                }

                btbsOk++

                val obj = JSONObject().apply {
                    put("zipPath", zipPath)
                    put("sourcePath", relativePathFromMain(context, src))
                    put("fileName", src.name)
                    put("baustellenName", b.baustellenName)
                    put("streckennummer", b.streckennummer ?: JSONObject.NULL)
                    put("datum", dateFmt.format(b.datum))
                }
                manifest.getJSONArray("btb").put(obj)
            }

            // manifest.json (am Ende)
            val bytes = manifest.toString(2).toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(bytes)
            zos.closeEntry()
        }

        outFile to ExportStats(fotosOk, btbsOk, missing)
        }
    }
}

