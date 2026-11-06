package de.tbhelfer.datenbank

import android.content.Context
import android.util.Base64
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.BtbServerStateStore
import de.tbhelfer.server.CollabSyncApi
import de.tbhelfer.server.Server1FileApi
import de.tbhelfer.server.Server1BtbApi
import de.tbhelfer.server.Server1BtbPdfApi
import de.tbhelfer.ui.bautagebuch.BautagebuchEintrag
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbAbschlussPdfFinder
import de.tbhelfer.ui.bautagebuch.BtbAbschlussStatus
import de.tbhelfer.ui.bautagebuch.BtbDokumentVersionierung
import de.tbhelfer.ui.bautagebuch.BtbFotoZuordnungSpeicher
import de.tbhelfer.ui.bautagebuch.BtbPhotoContextOverrideStore
import de.tbhelfer.ui.bautagebuch.BtbPhotoKmOverrideStore
import de.tbhelfer.ui.bautagebuch.BtbPhotoTextOverrideStore
import de.tbhelfer.ui.bautagebuch.BtbPhotoMetadatenStore
import de.tbhelfer.ui.bautagebuch.SchichtTyp
import de.tbhelfer.ui.suche.BtbSpeicher
import de.tbhelfer.util.MobileImageSync
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Medienbrücke für den gemeinsamen BTB eines verantwortlichen TB-Teams.
 *
 * Text/Fachdaten laufen über [CollabLocalBridge]. Diese Klasse überträgt zusätzlich die Fotos des
 * konkreten BTB. Moderne BTBs verwenden ausschließlich die stabile btbId-Zuordnung; nur echte
 * Altbestände ohne btbId dürfen noch über Datum/Schicht/Zeitfenster zusammengeführt werden.
 */
class BtbTeamMediaSync(context: Context) {
    private val appContext = context.applicationContext
    private val bindings get() = ServiceLocator.worksiteBindingRepository
    private val collab get() = ServiceLocator.collabSyncRepository
    private val fotos get() = ServiceLocator.fotoRepository
    private val btbApi = Server1BtbApi()
    private val importedPrefs = appContext.getSharedPreferences(PREF_IMPORTED, Context.MODE_PRIVATE)

    data class Summary(
        val uploaded: Int = 0,
        val alreadyPresent: Int = 0,
        val imported: Int = 0,
        val pdfImported: Int = 0
    )

    sealed interface Result {
        data class Success(val summary: Summary) : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    suspend fun synchronize(
        token: String,
        api: CollabSyncApi = CollabSyncApi(),
        server1Api: Server1FileApi? = null,
        server1PdfApi: Server1BtbPdfApi? = null,
        worksiteServerIds: Set<String>? = null,
        btbServerIds: Set<String>? = null,
        allowUploads: Boolean = true,
        importMedia: Boolean = true
    ): Result = MediaSyncGate.run {
        synchronizeLocked(token, api, server1Api, server1PdfApi, worksiteServerIds,
            btbServerIds, allowUploads, importMedia)
    }

    private suspend fun synchronizeLocked(
        token: String,
        api: CollabSyncApi,
        server1Api: Server1FileApi?,
        server1PdfApi: Server1BtbPdfApi?,
        worksiteServerIds: Set<String>?,
        btbServerIds: Set<String>?,
        allowUploads: Boolean,
        importMedia: Boolean
    ): Result {
        var uploaded = 0
        var present = 0
        var imported = 0
        var pdfImported = 0

        val bound = bindings.contextsWithBindings().filter {
            it.binding != null &&
                it.localWorksite != null &&
                (worksiteServerIds == null || it.context.worksiteServerId in worksiteServerIds)
        }
        for (item in bound) {
            val local = item.localWorksite ?: continue
            val server = item.context
            val localFotos = fotos.fotosFuerBaustelleSyncOnce(local.altBaustelleId)
            val localPaths = localFotos.mapTo(hashSetOf()) { runCatching { File(it.dateipfad).canonicalPath }.getOrDefault(it.dateipfad) }
            val serverBtbs = collab.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_BTB)
                .filter {
                    !it.isDeleted &&
                        it.groupId == server.groupId &&
                        (btbServerIds == null || it.serverId in btbServerIds)
                }
                .mapNotNull { entity -> entity.clientEntityKey?.let { it to entity } }
                .toMap()

            // Upload: bei modernen BTBs ist die explizite Foto->btbId-Zuordnung verbindlich.
            // Nur historische BTBs ohne btbId verwenden weiterhin den Zeitfenster-Fallback;
            // bereits modern zugeordnete Fotos duerfen dabei nicht in einen Legacy-BTB rutschen.
            val verlauf = BautagebuchSpeicher.verlaufFuerBaustelle(
                appContext,
                local.altBaustelleId,
                local.hauptbaustellenName
            )
            val verlaufByBtbId = verlauf.mapNotNull { v ->
                v.btbId?.takeIf { it.isNotBlank() && !it.startsWith("legacy:") }?.let { it to v }
            }.toMap()
            if (allowUploads) for (v in verlauf) {
                val stableBtbId = v.btbId?.takeIf { it.isNotBlank() }
                val key = stableBtbId ?: "legacy:${v.datum}:${v.schicht.name}"
                val parent = serverBtbs[key] ?: continue
                val serverState = BtbServerStateStore.state(appContext, key)
                if (parent.status.equals("FERTIG", true) || serverState?.readOnly == true) {
                    continue
                }
                val btbFotos = if (stableBtbId != null) {
                    val fotoIds = BtbFotoZuordnungSpeicher.fotoIdsFuerBtb(appContext, stableBtbId)
                    localFotos.filter { it.id in fotoIds }
                } else {
                    val (fromMs, untilMs) = shiftWindow(v.entry)
                    localFotos.filter { foto ->
                        foto.erstelltAmEpochMs in fromMs until untilMs &&
                            BtbFotoZuordnungSpeicher.btbId(appContext, foto.id) == null
                    }
                }

                val contextOverrides = if (stableBtbId != null) {
                    BtbPhotoContextOverrideStore.load(appContext, stableBtbId)
                } else {
                    emptyMap()
                }
                val textOverrides = BtbPhotoTextOverrideStore.loadOverrides(
                    appContext,
                    v.entry.baustelleId,
                    v.entry.datumIso,
                    v.entry.startUhrzeit,
                    v.entry.endeUhrzeit,
                    v.entry.schicht
                )
                val kmOverrides = BtbPhotoKmOverrideStore.loadOverrides(
                    appContext,
                    v.entry.baustelleId,
                    v.entry.datumIso,
                    v.entry.startUhrzeit,
                    v.entry.endeUhrzeit,
                    v.entry.schicht
                )

                for (foto in btbFotos) {
                    var bindingIntent = BtbPhotoMetadatenStore.pending(appContext, key, foto.id)
                    var requestedEntryId = bindingIntent?.clientEntryKey?.let {
                        BtbServerStateStore.entryId(appContext, key, it)
                            ?: return Result.Error("Foto-Eintrag ist noch nicht vom Server bestätigt. Bitte BTB synchronisieren.")
                    }
                    val original = File(foto.dateipfad)
                    if (!original.exists() || !original.isFile) continue

                    val contextOverride = contextOverrides[foto.id]
                    val effectiveFoto = applyContextOverride(foto, contextOverride)
                    val worksiteNodeKey = contextOverride?.worksiteNodeKey
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: effectiveFoto.baustellenKnotenId?.let { localNodeId ->
                            ServiceLocator.worksiteStructureSync.ensureClientNodeKey(
                                localRootKnotenId = local.id,
                                localKnotenId = localNodeId
                            )
                        }
                    val textOverride = textOverrides[foto.id]?.trim()?.takeIf { it.isNotBlank() }
                    val kmOverride = kmOverrides[foto.id]
                    val effectiveText = textOverride ?: effectiveFoto.kurztext
                    val effectiveKm = kmOverride?.kmVon ?: effectiveFoto.km
                    val effectiveForFilename = effectiveFoto.copy(
                        kurztext = effectiveText,
                        km = effectiveKm
                    ).mitMetadatenVon(effectiveFoto)
                    val metadata = structuredPhotoMetadata(
                        foto = effectiveFoto,
                        firmaId = contextOverride?.firmaId,
                        worksiteNodeKey = worksiteNodeKey,
                        kurztext = effectiveText,
                        km = effectiveKm
                    )

                    if (bindingIntent == null && BtbPhotoMetadatenStore.entryKey(appContext, key, foto.id) == null &&
                        BtbPhotoMetadatenStore.autoBindingAllowed(appContext, key, foto.id)) {
                        val match = de.tbhelfer.ui.bautagebuch.BtbPhotoEntryMatcher.find(
                            v.entry, effectiveFoto.bildtyp, effectiveFoto.firma, worksiteNodeKey, effectiveText, contextOverride?.firmaId)
                        if (match != null) {
                            BtbPhotoMetadatenStore.bind(appContext, key, foto.id, match)
                            bindingIntent = BtbPhotoMetadatenStore.pending(appContext, key, foto.id)
                            requestedEntryId = BtbServerStateStore.entryId(appContext, key, match)
                                ?: return Result.Error("Foto-Eintrag wartet noch auf Serverbestätigung.")
                        }
                    }

                    // Bereits bekannte Server-Fotos niemals wegen der neuen kleinen Sync-Datei
                    // nochmals hochladen. So entstehen beim Umstieg keine Duplikate.
                    val mappedServerId = serverAttachmentIdForLocalPhoto(foto.id, key)
                    if (mappedServerId != null) {
                        val serverPhoto = BtbServerStateStore.photoMetadata(
                            appContext,
                            key,
                            mappedServerId
                        )
                        if (serverPhoto != null && server1Api != null) {
                            val desiredPatch = localPhotoOverridePatch(
                                foto = effectiveFoto,
                                contextOverride = contextOverride,
                                worksiteNodeKey = worksiteNodeKey,
                                textOverride = textOverride,
                                kmOverride = kmOverride
                            )
                            if (bindingIntent != null) {
                                desiredPatch.put("btbEntryId", requestedEntryId ?: JSONObject.NULL)
                            } else if (contextOverride != null || textOverride != null) {
                                val currentMeta = serverPhoto.optJSONObject("photo_metadata")
                                if (currentMeta != null && !currentMeta.isNull("btbEntryId")) desiredPatch.put("btbEntryId", JSONObject.NULL)
                            }
                            if (requestedEntryId != null) {
                                listOf("firma", "firmaId", "worksiteNodeKey", "typ", "kurztext").forEach { desiredPatch.remove(it) }
                            }
                            val patch = changedPhotoPatch(serverPhoto, desiredPatch)
                            if (patch.length() > 0) {
                                val baseVersion = serverPhoto.optLong("link_version", 0L)
                                if (baseVersion <= 0L) {
                                    return Result.Error("Server-Foto besitzt keine gültige Link-Version.")
                                }
                                when (
                                    val patched = btbApi.updatePhotoMetadata(
                                        token = token,
                                        serverBtbId = parent.serverId,
                                        fileId = mappedServerId,
                                        baseVersion = baseVersion,
                                        photoMetadata = patch
                                    )
                                ) {
                                    Server1BtbApi.PhotoUpdateResult.Unsupported -> return Result.Unsupported
                                    Server1BtbApi.PhotoUpdateResult.SessionInvalid -> return Result.SessionInvalid
                                    is Server1BtbApi.PhotoUpdateResult.Error ->
                                        return Result.Error(patched.message)
                                    is Server1BtbApi.PhotoUpdateResult.Conflict ->
                                        return Result.Error(
                                            "BTB-Foto wurde parallel geändert. Bitte synchronisieren und erneut versuchen."
                                        )
                                    is Server1BtbApi.PhotoUpdateResult.Success ->
                                        BtbServerStateStore.rememberPhotoUpdate(
                                            appContext,
                                            key,
                                            mappedServerId,
                                            patched.linkVersion,
                                            patched.photoMetadata
                                        )
                                }
                            }
                            val confirmed = BtbServerStateStore.photoMetadata(appContext, key, mappedServerId) ?: serverPhoto
                            if (!rememberEffectivePhoto(local.id, key, foto.id, confirmed))
                                return Result.Error("Foto-Unterbaustelle ist lokal noch nicht verfügbar. Bitte synchronisieren.")
                            BtbPhotoMetadatenStore.acknowledge(appContext, key, foto.id, bindingIntent)
                            BtbPhotoContextOverrideStore.clearIfUnchanged(appContext, key, foto.id, contextOverride)
                            if (confirmed.optJSONObject("photo_metadata")?.optString("kurztext") == textOverride)
                                BtbPhotoTextOverrideStore.clearIfUnchanged(appContext, v.entry, foto.id, textOverride)
                            BtbPhotoMetadatenStore.notifyChanges()
                        }
                        present++
                        continue
                    }

                    // Das Volloriginal bleibt ausschließlich auf dem Aufnahmegerät.
                    // Zum Server geht eine separate, EXIF-orientierte JPEG-Sync-Version.
                    val syncFile = MobileImageSync.prepare(appContext, original, "btb_${foto.id}")
                        ?: return Result.Error("Foto konnte nicht für den Server verkleinert werden: ${original.name}")
                    val uploadName = encodedPhotoFilename(
                        effectiveForFilename,
                        MobileImageSync.syncFilename(original.name)
                    )
                    when (
                        val result = collab.uploadAttachment(
                            token = token,
                            worksiteServerId = server.worksiteServerId,
                            parentEntityType = SyncCollabPendingChangeEntity.TYPE_BTB,
                            parentEntityId = parent.serverId,
                            file = syncFile,
                            uploadFilename = uploadName,
                            mimeType = "image/jpeg",
                            capturedAtEpochMs = foto.aufnahmeEpochEffektiv,
                            gpsLat = foto.gpsLatEffektiv,
                            gpsLon = foto.gpsLonEffektiv,
                            gpsAccuracyM = foto.gpsAccuracyEffektiv,
                            btbEntryId = requestedEntryId,
                            photoMetadata = metadata,
                            api = api,
                            server1Api = server1Api
                        )
                    ) {
                        CollabSyncRepository.LocalAttachmentUploadResult.SessionInvalid -> return Result.SessionInvalid
                        CollabSyncRepository.LocalAttachmentUploadResult.Unsupported -> return Result.Unsupported
                        is CollabSyncRepository.LocalAttachmentUploadResult.Error -> return Result.Error(result.message)
                        is CollabSyncRepository.LocalAttachmentUploadResult.Success -> {
                            rememberImportedPhoto(result.serverId, foto.id, key)
                            if (result.uploaded) {
                                uploaded++
                            } else present++
                        }
                    }
                }
                // Medienupload aendert nur Fotos. Der Lifecycle wird seit Vertrag 25.09.2026
                // ausschliesslich ueber shift-finish/shift-resume/finalize gesteuert.
            }

            if (!importMedia) continue

            // Import: heruntergeladene BTB-Fotos als normale lokale Fotos verfügbar machen.
            // Eigene Uploads zeigen bereits auf eine vorhandene lokale Datei und werden nicht dupliziert.
            // Moderne Server-BTBs ordnen importierte Fotos wieder exakt derselben clientEntityKey/btbId zu.
            for ((parentKey, parent) in serverBtbs) {
                for ((serverId, localId) in importedMappingsForBtb(parentKey)) {
                    val meta = BtbServerStateStore.photoMetadata(appContext, parentKey, serverId) ?: continue
                    if (!rememberEffectivePhoto(local.id, parentKey, localId, meta))
                        return Result.Error("Foto-Unterbaustelle ist lokal noch nicht verfügbar. Bitte synchronisieren.")
                }
                for (attachment in collab.attachments(parent.serverId)) {
                    if (attachment.isDeleted || !attachment.isDownloaded) continue
                    val path = attachment.localPath?.takeIf { it.isNotBlank() } ?: continue
                    val file = File(path)
                    if (!file.exists() || !file.isFile) continue
                    val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                    if (canonical in localPaths) {
                        val own = localFotos.firstOrNull { foto ->
                            runCatching { File(foto.dateipfad).canonicalPath }
                                .getOrDefault(foto.dateipfad) == canonical
                        }
                        if (own != null) {
                            rememberImportedPhoto(attachment.serverId, own.id, parentKey)
                        } else {
                            rememberImported(attachment.serverId, true)
                        }
                        continue
                    }
                    if (wasImported(attachment.serverId)) continue
                    val serverPhoto = BtbServerStateStore.photoMetadata(
                        appContext,
                        parentKey,
                        attachment.serverId
                    )
                    val meta = mergedPhotoMetadata(
                        serverPhoto = serverPhoto,
                        legacy = decodePhotoMetadata(attachment.filename)
                    )
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
                        erstelltAmEpochMs = if (meta.isNull("epoch")) 0L else meta.optLong("epoch", 0L)
                    )
                    val localFotoId = if (parentKey.startsWith("legacy:")) {
                        fotos.einfuegen(entity)
                    } else {
                        fotos.einfuegenFuerBtb(entity, parentKey, triggerSync = false)
                    }
                    rememberImportedPhoto(attachment.serverId, localFotoId, parentKey)
                    if (serverPhoto != null && !rememberEffectivePhoto(local.id, parentKey, localFotoId, serverPhoto))
                        return Result.Error("Foto-Unterbaustelle ist lokal noch nicht verfügbar. Bitte synchronisieren.")
                    localPaths += canonical
                    imported++
                }
            }

            // Portal-Unlink: nur die BTB-Zuordnung entfernen, das lokale Originalfoto behalten.
            for ((parentKey, parent) in serverBtbs) {
                val state = BtbServerStateStore.state(appContext, parentKey) ?: continue
                val photos = runCatching { org.json.JSONArray(state.photosJson) }.getOrNull()
                val activeServerPhotoIds = (0 until (photos?.length() ?: 0)).mapNotNull { index ->
                    photos?.optJSONObject(index)?.optString("id")?.trim()?.takeIf { it.isNotBlank() }
                }.toSet()
                importedMappingsForBtb(parentKey)
                    .filter { it.first !in activeServerPhotoIds }
                    .forEach { (serverFileId, localFotoId) ->
                        // The snapshot may predate an upload in this very run. Absence alone
                        // is not an unlink. Require the file-link cache to confirm it as well.
                        val attachment = collab.attachment(serverFileId)
                        val sameScope = attachment?.worksiteServerId == server.worksiteServerId
                        if (LocalMediaReuse.confirmedUnlink(
                                snapshotContainsPhoto = false,
                                attachmentKnown = sameScope,
                                attachmentDeleted = attachment?.isDeleted == true,
                                sameParent = attachment?.parentEntityId == parent.serverId &&
                                    attachment?.parentEntityType.equals(SyncCollabPendingChangeEntity.TYPE_BTB, true)
                            )) {
                            // A later local re-assignment must not be undone by an old parent.
                            if (BtbFotoZuordnungSpeicher.btbId(appContext, localFotoId) == parentKey) {
                                BtbFotoZuordnungSpeicher.entfernen(appContext, localFotoId)
                            }
                            forgetImportedPhoto(serverFileId)
                        }
                    }
            }

            // Abschluss-PDF: direkt aus dem kanonischen BTB-Endpunkt in denselben lokalen
            // BTB-Ordner spiegeln, den Verlauf/Viewer bereits verwenden.
            if (server1PdfApi != null) {
                for ((parentKey, parent) in serverBtbs) {
                    val localBtb = verlaufByBtbId[parentKey] ?: continue
                    val payload = runCatching { JSONObject(parent.payloadJson) }.getOrNull() ?: continue
                    val serverStatus = payload.optString(Server1BtbApi.SERVER_STATUS_KEY).uppercase()
                    // parent.status ist bereits der kanonische Lifecycle-Status aus
                    // Server1BtbApi: FERTIG nur wenn FINALIZED UND nicht mehr active_btb_id.
                    // Ein serverseitig noch als FINALIZED markierter, aber wieder aktiver BTB
                    // ist fuer Android ein ENTWURF/Nachbearbeitungs-BTB und darf nicht erneut
                    // durch das alte Abschluss-PDF "geschlossen" werden.
                    val snapshotFinished = parent.status.equals("FERTIG", ignoreCase = true)
                    val pdfAvailable = payload.optBoolean(
                        Server1BtbApi.SERVER_FINAL_PDF_AVAILABLE_KEY,
                        false
                    )
                    val expectedSha = payload.optString(
                        Server1BtbApi.SERVER_FINAL_PDF_SHA256_KEY
                    ).trim()

                    val canViewPdf = BtbServerStateStore.state(appContext, parentKey)?.canViewPdf == true
                    if (snapshotFinished && serverStatus == "FINALIZED" && pdfAvailable && canViewPdf) {
                        val marker = pdfImportMarker(parent.serverId, parent.entityVersion, expectedSha)
                        val existing = BtbAbschlussPdfFinder.neueste(appContext, localBtb.entry)
                        if (wasImported(marker) && existing?.isFile == true) continue

                        when (val result = server1PdfApi.downloadFinalPdf(token, parent.serverId)) {
                            Server1BtbPdfApi.Result.SessionInvalid -> return Result.SessionInvalid
                            Server1BtbPdfApi.Result.Unsupported -> return Result.Unsupported
                            Server1BtbPdfApi.Result.NotAvailable ->
                                return Result.Error("Server meldet ein Abschluss-PDF, liefert es aber nicht aus.")
                            is Server1BtbPdfApi.Result.Error -> return Result.Error(result.message)
                            is Server1BtbPdfApi.Result.Success -> {
                                if (expectedSha.isNotBlank()) {
                                    val actualSha = sha256(result.file.bytes)
                                    if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                                        return Result.Error("BTB-Abschluss-PDF hat eine abweichende SHA-256-Prüfsumme.")
                                    }
                                }
                                val dir = BtbSpeicher.btbsFuerBaustelle(
                                    appContext,
                                    localBtb.entry.baustelleId,
                                    localBtb.entry.datumIso
                                )
                                val tokenPart = BtbDokumentVersionierung.dokumentToken(localBtb.entry)
                                val target = File(dir, "BTB_Server_${tokenPart}.pdf")
                                val temp = File(target.absolutePath + ".tmp")
                                val stored = runCatching {
                                    temp.writeBytes(result.file.bytes)
                                    BtbDokumentVersionierung.loescheAktuellesAbschlussDokument(
                                        appContext,
                                        localBtb.entry
                                    )
                                    if (target.exists()) target.delete()
                                    if (!temp.renameTo(target)) {
                                        temp.copyTo(target, overwrite = true)
                                        temp.delete()
                                    }
                                    check(
                                        BtbDokumentVersionierung.serverAbschlussUebernehmen(
                                            appContext,
                                            localBtb.entry,
                                            target,
                                            expectedSha.takeIf { it.isNotBlank() }
                                        )
                                    ) { "Server-Abschluss konnte lokal nicht bestätigt werden." }
                                }
                                if (stored.isFailure) {
                                    temp.delete()
                                    return Result.Error(
                                        stored.exceptionOrNull()?.message
                                            ?: "BTB-Abschluss-PDF konnte lokal nicht gespeichert werden."
                                    )
                                }
                                rememberImported(marker, true)
                                pdfImported++
                            }
                        }
                    } else if (
                        !snapshotFinished &&
                        BtbDokumentVersionierung.hatServerAbschlussMarker(appContext, localBtb.entry)
                    ) {
                        BtbDokumentVersionierung.serverAbschlussInvalidieren(
                            appContext,
                            localBtb.entry
                        )
                    }
                }
            }
        }
        return Result.Success(Summary(uploaded, present, imported, pdfImported))
    }

    private fun pdfImportMarker(serverId: String, version: Long, sha256: String): String =
        "pdf:$serverId:" + sha256.ifBlank { version.toString() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

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
        val epoch = foto.aufnahmeEpochEffektiv ?: return original
        val lat = foto.gpsLatEffektiv ?: return original
        val lon = foto.gpsLonEffektiv ?: return original
        val buffer = ByteArrayOutputStream(96)
        DataOutputStream(buffer).use { out ->
            out.writeLong(epoch)
            out.writeFloat(lat.toFloat())
            out.writeFloat(lon.toFloat())
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

    /** Existing identity only; never infer ownership from filename, timestamp or appearance. */
    internal suspend fun knownLocalPhotoPath(serverId: String, btbId: String, localWorksiteId: Long): String? {
        val localId = importedPrefs.all[serverId] as? Long ?: return null
        if (importedPrefs.getString("btb:$serverId", null) != btbId) return null
        val photo = fotos.fotoMitId(localId) ?: return null
        if (photo.baustelleId != localWorksiteId) return null
        val boundBtb = BtbFotoZuordnungSpeicher.btbId(appContext, localId)
        if (boundBtb != btbId && !(btbId.startsWith("legacy:") && boundBtb == null)) return null
        return photo.dateipfad
    }

    private fun wasImported(serverAttachmentId: String): Boolean {
        val value = importedPrefs.all[serverAttachmentId]
        return value == true || value is Long
    }

    private fun rememberImported(serverAttachmentId: String, value: Boolean) {
        importedPrefs.edit().putBoolean(serverAttachmentId, value).apply()
    }

    private fun rememberImportedPhoto(serverAttachmentId: String, localFotoId: Long, btbId: String) {
        importedPrefs.edit()
            .putLong(serverAttachmentId, localFotoId)
            .putString("btb:$serverAttachmentId", btbId)
            .apply()
    }

    private fun importedMappingsForBtb(btbId: String): List<Pair<String, Long>> =
        importedPrefs.all.entries.mapNotNull { (key, value) ->
            if (key.startsWith("btb:")) return@mapNotNull null
            val localId = value as? Long ?: return@mapNotNull null
            val mappedBtb = importedPrefs.getString("btb:$key", null)
            if (mappedBtb == btbId) key to localId else null
        }

    private fun forgetImportedPhoto(serverAttachmentId: String) {
        importedPrefs.edit()
            .remove(serverAttachmentId)
            .remove("btb:$serverAttachmentId")
            .apply()
    }

    private fun applyContextOverride(
        foto: FotoEntity,
        value: BtbPhotoContextOverrideStore.ContextOverride?
    ): FotoEntity {
        value ?: return foto
        val nodeId = if (value.ortId != null) {
            value.ortId.takeIf { it > 0L }
        } else {
            foto.baustellenKnotenId
        }
        return foto.copy(
            baustellenKnotenId = nodeId,
            firma = value.firma.trim(),
            bildtyp = value.bildtyp ?: foto.bildtyp,
            objektKennung = value.objektKennung?.trim()?.takeIf { it.isNotBlank() },
            objektBereich = value.objektBereich?.trim()?.takeIf { it.isNotBlank() }
        ).mitMetadatenVon(foto)
    }

    private fun localPhotoOverridePatch(
        foto: FotoEntity,
        contextOverride: BtbPhotoContextOverrideStore.ContextOverride?,
        worksiteNodeKey: String?,
        textOverride: String?,
        kmOverride: BtbPhotoKmOverrideStore.KmOverride?
    ): JSONObject {
        val patch = JSONObject()
        if (contextOverride != null) {
            patch.putNullable("firma", contextOverride.firma.trim().takeIf { it.isNotBlank() })
            patch.putNullable("firmaId", contextOverride.firmaId?.trim()?.takeIf { it.isNotBlank() })
            patch.putNullable(
                "worksiteNodeKey",
                worksiteNodeKey?.trim()?.takeIf { it.isNotBlank() }
            )
            patch.put("typ", (contextOverride.bildtyp ?: foto.bildtyp).name)
            patch.putNullable(
                "objektKennung",
                contextOverride.objektKennung?.trim()?.takeIf { it.isNotBlank() }
            )
            patch.putNullable(
                "objektBereich",
                contextOverride.objektBereich?.trim()?.takeIf { it.isNotBlank() }
            )
        }
        textOverride?.trim()?.takeIf { it.isNotBlank() }?.let { patch.put("kurztext", it) }
        kmOverride?.kmVon?.takeIf { it.isFinite() }?.let { patch.put("km", it) }
        return patch
    }

    private fun changedPhotoPatch(serverPhoto: JSONObject, desired: JSONObject): JSONObject {
        if (desired.length() == 0) return JSONObject()
        val current = serverPhoto.optJSONObject("photo_metadata") ?: JSONObject()
        val patch = JSONObject()
        val keys = desired.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val wanted = desired.opt(key)
            val existing = if (current.has(key) && !current.isNull(key)) {
                current.opt(key)
            } else {
                JSONObject.NULL
            }
            if (!jsonScalarEquivalent(existing, wanted)) {
                patch.put(key, wanted)
            }
        }
        return patch
    }

    private fun jsonScalarEquivalent(left: Any?, right: Any?): Boolean {
        val leftNull = left == null || left === JSONObject.NULL
        val rightNull = right == null || right === JSONObject.NULL
        if (leftNull || rightNull) return leftNull && rightNull
        if (left is Number && right is Number) {
            return kotlin.math.abs(left.toDouble() - right.toDouble()) < 0.000001
        }
        return left.toString().trim() == right.toString().trim()
    }

    fun serverAttachmentIdForLocalPhoto(localFotoId: Long, btbId: String): String? =
        importedMappingsForBtb(btbId)
            .firstOrNull { (_, mappedLocalId) -> mappedLocalId == localFotoId }
            ?.first

    private fun structuredPhotoMetadata(
        foto: FotoEntity,
        firmaId: String?,
        worksiteNodeKey: String?,
        kurztext: String,
        km: Double?
    ): JSONObject = JSONObject()
        .putNullable("epoch", foto.aufnahmeEpochEffektiv)
        .putNullable("lat", foto.gpsLatEffektiv)
        .putNullable("lon", foto.gpsLonEffektiv)
        .putNullable("alt", foto.gpsAltM)
        .putNullable("km", km)
        .putNullable("achsabstand", foto.achsabstandM)
        .put("kmSuffix", foto.kmSuffix)
        .put("kmManuell", foto.kmManuell)
        .put("typ", foto.bildtyp.name)
        .put("indikator", foto.indikator?.name ?: "")
        .put("firma", foto.firma)
        .putNullable("firmaId", firmaId?.trim()?.takeIf { it.isNotBlank() })
        .putNullable("worksiteNodeKey", worksiteNodeKey?.trim()?.takeIf { it.isNotBlank() })
        .put("kurztext", kurztext)
        .put("objektKennung", foto.objektKennung ?: "")
        .put("objektBereich", foto.objektBereich ?: "")
        .put("notiz", foto.notiz ?: "")
        .put("info1", foto.info1Wert ?: "")
        .put("info2", foto.info2Wert ?: "")
        .put("info3", foto.info3Wert ?: "")
        .put("info4", foto.info4Wert ?: "")
        .put("info5", foto.info5Wert ?: "")

    private fun mergedPhotoMetadata(serverPhoto: JSONObject?, legacy: JSONObject?): JSONObject =
        BtbPhotoMetadatenStore.merge(serverPhoto, legacy).apply {
            if (optString("typ").isBlank()) put("typ", Bildtyp.FOTODOKUMENTATION.name)
            if (isNull("kurztext")) put("kurztext", "Synchronisiertes BTB-Foto")
        }

    private suspend fun rememberEffectivePhoto(localRootId: Long, btbId: String, fotoId: Long, photo: JSONObject): Boolean {
        val meta = photo.optJSONObject("photo_metadata") ?: JSONObject()
        val nodeKey = if (meta.isNull("worksiteNodeKey")) null else meta.optString("worksiteNodeKey").takeIf { it.isNotBlank() }
        val node = nodeKey?.let { ServiceLocator.worksiteStructureSync.localNodeIdForClientKey(localRootId, it) }
        if (nodeKey != null && node == null) return false
        BtbPhotoMetadatenStore.rememberServer(appContext, btbId, fotoId, photo, localNodeId = node)
        return true
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



