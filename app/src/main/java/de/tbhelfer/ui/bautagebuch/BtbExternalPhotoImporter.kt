package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.datenbank.FotoRepository
import de.tbhelfer.ui.kamera.FotoSpeicher
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Importiert Galerie-/Datei-Fotos als eigene App-Kopie in exakt einen modernen BTB. */
object BtbExternalPhotoImporter {

    data class Result(
        val imported: List<FotoEntity>,
        val failed: Int
    )

    suspend fun importieren(
        context: Context,
        uris: List<Uri>,
        baustelleId: Long,
        baustelleName: String,
        datumIso: String,
        bildtyp: Bildtyp,
        btbId: String,
        repository: FotoRepository
    ): Result {
        if (uris.isEmpty() || btbId.isBlank()) return Result(emptyList(), uris.size)
        val imported = mutableListOf<FotoEntity>()
        var failed = 0
        uris.forEachIndexed { index, uri ->
            val one = runCatching {
                importOne(
                    context = context,
                    uri = uri,
                    baustelleId = baustelleId,
                    baustelleName = baustelleName,
                    datumIso = datumIso,
                    bildtyp = bildtyp,
                    btbId = btbId,
                    repository = repository,
                    index = index
                )
            }.getOrNull()
            if (one == null) failed++ else imported += one
        }
        return Result(imported, failed)
    }

    private suspend fun importOne(
        context: Context,
        uri: Uri,
        baustelleId: Long,
        baustelleName: String,
        datumIso: String,
        bildtyp: Bildtyp,
        btbId: String,
        repository: FotoRepository,
        index: Int
    ): FotoEntity {
        val displayName = queryDisplayName(context, uri)
        val originalBase = displayName.substringBeforeLast('.', displayName)
            .trim().takeIf { it.isNotBlank() } ?: "Externes Foto"
        val rawExt = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase(Locale.ROOT)
        val directExt = when {
            rawExt in setOf("jpg", "jpeg", "png", "webp") -> rawExt
            mime == "image/jpeg" -> "jpg"
            mime == "image/png" -> "png"
            mime == "image/webp" -> "webp"
            else -> null
        }
        val targetExt = directExt ?: "jpg"
        val safeBase = originalBase
            .replace(Regex("[^\\p{L}\\p{N}._ -]+"), "_")
            .trim().replace(Regex("\\s+"), "_")
            .take(48).ifBlank { "Foto" }
        val generatedName = "import_${System.currentTimeMillis()}_${index + 1}_${safeBase}.$targetExt"
        val target = FotoSpeicher.zielDateiFuerBildtyp(
            context,
            baustelleName,
            bildtyp,
            datumIso,
            generatedName
        )
        target.parentFile?.mkdirs()

        try {
            if (directExt != null) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().buffered(128 * 1024).use { output ->
                        input.copyTo(output, 128 * 1024)
                    }
                } ?: error("Bild konnte nicht gelesen werden")
            } else {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: error("Bildformat konnte nicht gelesen werden")
                FileOutputStream(target).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)) {
                        error("Bild konnte nicht als JPEG gespeichert werden")
                    }
                }
                bitmap.recycle()
            }

            if (!target.exists() || target.length() <= 0L) error("Leere Bilddatei")

            val exif = runCatching { ExifInterface(target) }.getOrNull()
            val latLon = exif?.latLong
            val altitude = exif?.getAltitude(Double.NaN)?.takeIf { it.isFinite() }
            val created = exifDateEpochMs(exif) ?: System.currentTimeMillis()

            FotoSpeicher.erstelleThumbnailWennMoeglich(target)
            val entity = FotoEntity(
                baustelleId = baustelleId,
                baustellenKnotenId = null,
                dateipfad = target.absolutePath,
                gpsLat = latLon?.getOrNull(0) ?: 0.0,
                gpsLon = latLon?.getOrNull(1) ?: 0.0,
                gpsAltM = altitude,
                km = null,
                kmSuffix = 0,
                kmManuell = false,
                achsabstandM = null,
                indikator = null,
                firma = "",
                bildtyp = bildtyp,
                kurztext = originalBase.take(120),
                notiz = null,
                objektKennung = null,
                objektBereich = null,
                erstelltAmEpochMs = created
            )
            val id = repository.einfuegenFuerBtb(entity, btbId)
            return entity.copy(id = id)
        } catch (t: Throwable) {
            runCatching { FotoSpeicher.thumbnailDateiFuer(target).delete() }
            runCatching { target.delete() }
            throw t
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        val fallback = "extern_${System.currentTimeMillis()}.jpg"
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun exifDateEpochMs(exif: ExifInterface?): Long? {
        val raw = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        val dt = runCatching {
            LocalDateTime.parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
        }.getOrNull() ?: return null
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
