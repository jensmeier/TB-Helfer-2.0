package de.tbhelfer.datenbank

import android.content.Context
import android.util.Base64
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.CollabSyncApi
import de.tbhelfer.ui.bautagebuch.BautagebuchEintrag
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.SchichtTyp
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Medienbrücke für den gemeinsamen BTB eines verantwortlichen TB-Teams.
 *
 * Text/Fachdaten laufen über [CollabLocalBridge]. Diese Klasse überträgt zusätzlich die Fotos des
 * konkreten BTB-Zeitfensters als Attachments. Die Zuordnung zum Server-Entity verwendet exakt
 * denselben stabilen clientEntityKey/btbId wie der Textsync. Datum und TAG/NACHT sind nur noch
 * Legacy-/Anzeigeattribute und dürfen mehrere BTBs desselben Tages nicht mehr vermischen.
 */
class BtbTeamMediaSync(context: Context) {
    private val appContext = context.applicationContext
    private val bindings get() = ServiceLocator.worksiteBindingRepository
    private val collab get() = ServiceLocator.collabSyncRepository
    private val fotos get() = ServiceLocator.fotoRepository
    private val importedPrefs = appContext.getSharedPreferences(PREF_IMPORTED, Context.MODE_PRIVATE)

    data class Summary(val uploaded: Int = 0, val alreadyPresent: Int = 0, val imported: Int = 0)

    sealed interface Result {
        data class Success(val summary: Summary) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    suspend fun synchronize(token: String, api: CollabSyncApi = CollabSyncApi()): Result {
        var uploaded = 0
        var present = 0
        var imported = 0

        val bound = bindings.contextsWithBindings().filter { it.binding != null && it.localWorksite != null }
        for (item in bound) {
            val local = item.localWorksite ?: continue
            val server = item.context
            val localFotos = fotos.fotosFuerBaustelleOnce(local.altBaustelleId)
            val localPaths = localFotos.mapTo(hashSetOf()) { runCatching { File(it.dateipfad).canonicalPath }.getOrDefault(it.dateipfad) }
            val serverBtbs = collab.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_BTB)
                .filter { !it.isDeleted && it.groupId == server.groupId }
                .mapNotNull { entity -> entity.clientEntityKey?.let { it to entity } }
                .toMap()

            // Upload: exakt dasselbe stabile BTB-Schlüsselmodell wie CollabLocalBridge verwenden.
            // Altbestände besitzen im Verlauf bereits einen stabilen synthetischen legacy:* Key.
            val verlauf = BautagebuchSpeicher.verlaufFuerBaustelle(appContext, local.altBaustelleId, local.hauptbaustellenName)
            for (v in verlauf) {
                val key = v.btbId?.takeIf { it.isNotBlank() }
                    ?: "legacy:${v.datum}:${v.schicht.name}"
                val parent = serverBtbs[key] ?: continue
                val (fromMs, untilMs) = shiftWindow(v.entry)
                val btbFotos = localFotos.filter { it.erstelltAmEpochMs in fromMs until untilMs }
                for (foto in btbFotos) {
                    val file = File(foto.dateipfad)
                    if (!file.exists() || !file.isFile) continue
                    val uploadName = encodedPhotoFilename(foto, file.name)
                    when (
                        val result = collab.uploadAttachment(
                            token = token,
                            worksiteServerId = server.worksiteServerId,
                            parentEntityType = SyncCollabPendingChangeEntity.TYPE_BTB,
                            parentEntityId = parent.serverId,
                            file = file,
                            uploadFilename = uploadName,
                            mimeType = CollabSyncRepository.mimeTypeFor(file),
                            api = api
                        )
                    ) {
                        CollabSyncRepository.LocalAttachmentUploadResult.SessionInvalid -> return Result.SessionInvalid
                        CollabSyncRepository.LocalAttachmentUploadResult.Unsupported -> return Result.Unsupported
                        is CollabSyncRepository.LocalAttachmentUploadResult.Error -> return Result.Error(result.message)
                        is CollabSyncRepository.LocalAttachmentUploadResult.Success -> {
                            if (result.uploaded) uploaded++ else present++
                        }
                    }
                }
            }

            // Import: heruntergeladene BTB-Fotos als normale lokale Fotos verfügbar machen.
            // Eigene Uploads zeigen bereits auf eine vorhandene lokale Datei und werden nicht dupliziert.
            for (parent in serverBtbs.values) {
                for (attachment in collab.attachments(parent.serverId)) {
                    if (attachment.isDeleted || !attachment.isDownloaded) continue
                    val path = attachment.localPath?.takeIf { it.isNotBlank() } ?: continue
                    val file = File(path)
                    if (!file.exists() || !file.isFile) continue
                    val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                    if (canonical in localPaths) {
                        rememberImported(attachment.serverId, true)
                        continue
                    }
                    if (wasImported(attachment.serverId)) continue
                    val meta = decodePhotoMetadata(attachment.filename) ?: continue
                    val typ = runCatching { Bildtyp.valueOf(meta.optString("typ")) }
                        .getOrDefault(Bildtyp.FOTODOKUMENTATION)
                    val entity = FotoEntity(
                        baustelleId = local.altBaustelleId,
                        baustellenKnotenId = null,
                        dateipfad = file.absolutePath,
                        gpsLat = meta.optNullableDouble("lat") ?: 0.0,
                        gpsLon = meta.optNullableDouble("lon") ?: 0.0,
                        gpsAltM = meta.optNullableDouble("alt"),
                        km = meta.optNullableDouble("km"),
                        kmSuffix = meta.optInt("kmSuffix", 0),
                        kmManuell = meta.optBoolean("kmManuell", false),
                        achsabstandM = meta.optNullableDouble("achsabstand"),
                        indikator = meta.optString("indikator").takeIf { it.isNotBlank() }
                            ?.let { runCatching { Indikator.valueOf(it) }.getOrNull() },
                        firma = meta.optString("firma", ""),
                        bildtyp = typ,
                        kurztext = meta.optString("kurztext", "Synchronisiertes BTB-Foto"),
                        notiz = meta.optString("notiz").takeIf { it.isNotBlank() },
                        objektKennung = meta.optString("objektKennung").takeIf { it.isNotBlank() },
                        objektBereich = meta.optString("objektBereich").takeIf { it.isNotBlank() },
                        info1Wert = meta.optString("info1").takeIf { it.isNotBlank() },
                        info2Wert = meta.optString("info2").takeIf { it.isNotBlank() },
                        info3Wert = meta.optString("info3").takeIf { it.isNotBlank() },
                        info4Wert = meta.optString("info4").takeIf { it.isNotBlank() },
                        info5Wert = meta.optString("info5").takeIf { it.isNotBlank() },
                        erstelltAmEpochMs = meta.optLong("epoch", System.currentTimeMillis())
                    )
                    fotos.einfuegen(entity)
                    rememberImported(attachment.serverId, true)
                    localPaths += canonical
                    imported++
                }
            }
        }
        return Result.Success(Summary(uploaded, present, imported))
    }

    private fun shiftWindow(entry: BautagebuchEintrag): Pair<Long, Long> {
        val date = runCatching { LocalDate.parse(entry.datumIso) }.getOrElse { LocalDate.now() }
        val defaultStart = if (entry.schicht == SchichtTyp.NACHT) LocalTime.of(18, 0) else LocalTime.of(6, 0)
        val defaultEnd = if (entry.schicht == SchichtTyp.NACHT) LocalTime.of(6, 0) else LocalTime.of(18, 0)
        val fmt = DateTimeFormatter.ofPattern("H:mm")
        val start = entry.startUhrzeit?.trim()?.let { runCatching { LocalTime.parse(it, fmt) }.getOrNull() } ?: defaultStart
        val end = entry.endeUhrzeit?.trim()?.let { runCatching { LocalTime.parse(it, fmt) }.getOrNull() } ?: defaultEnd
        val zone = ZoneId.systemDefault()
        val from = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        val endDate = if (!end.isAfter(start)) date.plusDays(1) else date
        var until = endDate.atTime(end).atZone(zone).toInstant().toEpochMilli()
        if (until <= from) until = from + 12L * 60L * 60L * 1000L
        return from to until
    }

    /**
     * Der Attachment-Dateiname wird auf Android UND Server auf 160 Zeichen begrenzt.
     * Das frühere Base64-JSON konnte dadurch mitten im Metadatenblock abgeschnitten werden.
     *
     * BTBP1 verwendet deshalb ein kompaktes Binärformat mit bewusst kurzen Textfeldern. Der
     * komplette Name bleibt auch mit Umlauten sicher unter der Servergrenze; fachlich wichtige
     * Aufnahmeparameter (Zeit, GPS, KM, Typ, Indikator, Firma/Kurztext/Objekt) reisen mit.
     */
    private fun encodedPhotoFilename(foto: FotoEntity, original: String): String {
        val buffer = ByteArrayOutputStream(96)
        DataOutputStream(buffer).use { out ->
            out.writeLong(foto.erstelltAmEpochMs)
            out.writeFloat(foto.gpsLat.toFloat())
            out.writeFloat(foto.gpsLon.toFloat())
            out.writeFloat(foto.gpsAltM?.toFloat() ?: Float.NaN)
            out.writeFloat(foto.km?.toFloat() ?: Float.NaN)
            out.writeFloat(foto.achsabstandM?.toFloat() ?: Float.NaN)
            out.writeShort(foto.kmSuffix.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
            out.writeByte(if (foto.kmManuell) 1 else 0)
            out.writeByte(foto.bildtyp.ordinal.coerceIn(0, 254))
            out.writeByte(foto.indikator?.ordinal?.coerceIn(0, 254) ?: 255)
            writeTinyUtf(out, foto.firma, 12)
            writeTinyUtf(out, foto.kurztext, 20)
            writeTinyUtf(out, foto.objektKennung.orEmpty(), 10)
            writeTinyUtf(out, foto.objektBereich.orEmpty(), 8)
        }
        val encoded = Base64.encodeToString(
            buffer.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val cleanOriginal = original
            .replace(Regex("[^A-Za-z0-9._()-]+"), "_")
            .take(22)
            .ifBlank { "bild.jpg" }
        return "${PHOTO_PREFIX_V1}${encoded}__${cleanOriginal}".take(MAX_ATTACHMENT_FILENAME_LENGTH)
    }

    private fun decodePhotoMetadata(filename: String?): JSONObject? {
        val raw = filename.orEmpty()
        if (raw.startsWith(PHOTO_PREFIX_V1)) {
            val encoded = raw.removePrefix(PHOTO_PREFIX_V1).substringBefore("__")
            if (encoded.isBlank()) return null
            return runCatching {
                val bytes = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                    val epoch = input.readLong()
                    val lat = input.readFloat().toDouble()
                    val lon = input.readFloat().toDouble()
                    val alt = input.readFloat()
                    val km = input.readFloat()
                    val achs = input.readFloat()
                    val kmSuffix = input.readShort().toInt()
                    val flags = input.readUnsignedByte()
                    val bildtypIndex = input.readUnsignedByte()
                    val indikatorIndex = input.readUnsignedByte()
                    val firma = readTinyUtf(input)
                    val kurztext = readTinyUtf(input)
                    val objektKennung = readTinyUtf(input)
                    val objektBereich = readTinyUtf(input)

                    JSONObject()
                        .put("epoch", epoch)
                        .put("lat", lat)
                        .put("lon", lon)
                        .putNullable("alt", alt.takeUnless(Float::isNaN)?.toDouble())
                        .putNullable("km", km.takeUnless(Float::isNaN)?.toDouble())
                        .putNullable("achsabstand", achs.takeUnless(Float::isNaN)?.toDouble())
                        .put("kmSuffix", kmSuffix)
                        .put("kmManuell", flags and 1 != 0)
                        .put("typ", Bildtyp.values().getOrNull(bildtypIndex)?.name ?: Bildtyp.FOTODOKUMENTATION.name)
                        .put("indikator", Indikator.values().getOrNull(indikatorIndex)?.name ?: "")
                        .put("firma", firma)
                        .put("kurztext", kurztext.ifBlank { "Synchronisiertes BTB-Foto" })
                        .put("objektKennung", objektKennung)
                        .put("objektBereich", objektBereich)
                }
            }.getOrNull()
        }

        // Rückwärtskompatibilität für bereits hochgeladene frühe Entwicklungsstände.
        if (!raw.startsWith(PHOTO_PREFIX_LEGACY)) return null
        val encoded = raw.removePrefix(PHOTO_PREFIX_LEGACY).substringBefore("__")
        if (encoded.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(bytes, Charsets.UTF_8))
        }.getOrNull()
    }

    private fun writeTinyUtf(out: DataOutputStream, value: String, maxBytes: Int) {
        var text = value.trim()
        while (text.isNotEmpty() && text.toByteArray(Charsets.UTF_8).size > maxBytes) {
            text = text.dropLast(1)
        }
        val bytes = text.toByteArray(Charsets.UTF_8)
        out.writeByte(bytes.size)
        out.write(bytes)
    }

    private fun readTinyUtf(input: DataInputStream): String {
        val size = input.readUnsignedByte()
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun wasImported(serverAttachmentId: String): Boolean = importedPrefs.getBoolean(serverAttachmentId, false)
    private fun rememberImported(serverAttachmentId: String, value: Boolean) {
        importedPrefs.edit().putBoolean(serverAttachmentId, value).apply()
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
        return this
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key) }.getOrNull()
    }

    companion object {
        private const val PREF_IMPORTED = "tb_helfer_btb_media_import_v1"
        private const val PHOTO_PREFIX_V1 = "BTBP1__"
        private const val PHOTO_PREFIX_LEGACY = "BTBPHOTO__"
        private const val MAX_ATTACHMENT_FILENAME_LENGTH = 160
    }
}
