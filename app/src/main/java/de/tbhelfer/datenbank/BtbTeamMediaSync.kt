package de.tbhelfer.datenbank

import android.content.Context
import android.util.Base64
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.CollabSyncApi
import de.tbhelfer.ui.bautagebuch.BautagebuchEintrag
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.SchichtTyp
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Medienbrücke für den gemeinsamen BTB eines verantwortlichen TB-Teams.
 *
 * Text/Fachdaten laufen weiterhin über [CollabLocalBridge]. Diese Klasse überträgt zusätzlich
 * die Fotos der jeweiligen Schicht als BTB-Attachments. Dadurch erhält der verantwortliche TB
 * auch Bilder seiner Helfer und umgekehrt, ohne BTB anderer verantwortlicher TBs zu vermischen.
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

            // Upload: genau die Fotos, deren Aufnahmezeit in die konkrete Tag-/Nachtschicht fällt.
            val verlauf = BautagebuchSpeicher.verlaufFuerBaustelle(appContext, local.altBaustelleId, local.hauptbaustellenName)
            for (v in verlauf) {
                val key = "${v.datum}:${v.schicht.name}"
                val parent = serverBtbs[key] ?: continue
                val (fromMs, untilMs) = shiftWindow(v.entry)
                val shiftFotos = localFotos.filter { it.erstelltAmEpochMs in fromMs until untilMs }
                for (foto in shiftFotos) {
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

    private fun encodedPhotoFilename(foto: FotoEntity, original: String): String {
        val meta = JSONObject()
            .put("epoch", foto.erstelltAmEpochMs)
            .put("typ", foto.bildtyp.name)
            .put("firma", foto.firma)
            .put("kurztext", foto.kurztext)
            .put("notiz", foto.notiz ?: "")
            .put("lat", foto.gpsLat)
            .put("lon", foto.gpsLon)
            .putNullable("alt", foto.gpsAltM)
            .putNullable("km", foto.km)
            .put("kmSuffix", foto.kmSuffix)
            .put("kmManuell", foto.kmManuell)
            .putNullable("achsabstand", foto.achsabstandM)
            .put("indikator", foto.indikator?.name ?: "")
            .put("objektKennung", foto.objektKennung ?: "")
            .put("objektBereich", foto.objektBereich ?: "")
            .put("info1", foto.info1Wert ?: "")
            .put("info2", foto.info2Wert ?: "")
            .put("info3", foto.info3Wert ?: "")
            .put("info4", foto.info4Wert ?: "")
            .put("info5", foto.info5Wert ?: "")
        val encoded = Base64.encodeToString(
            meta.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val cleanOriginal = original.replace(Regex("[^A-Za-z0-9._()-]+"), "_").take(70).ifBlank { "bild.jpg" }
        return "BTBPHOTO__${encoded}__${cleanOriginal}"
    }

    private fun decodePhotoMetadata(filename: String?): JSONObject? {
        val raw = filename.orEmpty()
        if (!raw.startsWith("BTBPHOTO__")) return null
        val encoded = raw.removePrefix("BTBPHOTO__").substringBefore("__")
        if (encoded.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(bytes, Charsets.UTF_8))
        }.getOrNull()
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
    }
}
