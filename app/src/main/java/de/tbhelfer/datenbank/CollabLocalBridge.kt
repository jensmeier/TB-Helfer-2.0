package de.tbhelfer.datenbank

import android.content.Context
import de.tbhelfer.server.BtbEntrySlotStore
import de.tbhelfer.server.BtbServerStateStore
import de.tbhelfer.server.CollabSyncApi
import de.tbhelfer.server.SelectiveSyncStore
import de.tbhelfer.server.Server1BtbApi
import de.tbhelfer.server.Server1FileApi
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import de.tbhelfer.ui.abnahme.AbnahmeDeleteIntentStore
import de.tbhelfer.ui.baustelle.MaengellisteExport
import de.tbhelfer.ui.baustelle.MangelStatusSpeicher
import de.tbhelfer.ui.baustelle.SyncMangelSpeicher
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbActivePointerControl
import de.tbhelfer.ui.bautagebuch.BtbAbschlussStatus
import de.tbhelfer.ui.bautagebuch.BtbDokumentVersionierung
import de.tbhelfer.ui.bautagebuch.SchichtTyp
import de.tbhelfer.util.MobileImageSync
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Brücke zwischen den bestehenden lokalen Fachspeichern und dem Server-Sync.
 *
 * BTB bleibt strikt gruppenbezogen. Neue BTBs werden über ihre stabile BTB-ID synchronisiert;
 * Datum und TAG/NACHT sind nur noch Attribute. Dadurch können mehrere Berichte am selben Tag
 * innerhalb desselben TB-Teams unabhängig voneinander existieren.
 */
class CollabLocalBridge(
    context: Context,
    private val syncDatabase: SyncDatenbank,
    private val bindingRepository: WorksiteBindingRepository,
    private val collabRepository: CollabSyncRepository,
    private val fotoRepository: FotoRepository
) {
    private val appContext = context.applicationContext
    private val dao: SyncDao get() = syncDatabase.syncDao()

    data class ExportSummary(
        val worksites: Int,
        val queuedOrUpdated: Int,
        val btb: Int,
        val defects: Int,
        val acceptances: Int
    )

    sealed interface BtbExportResult {
        data class Success(
            val worksiteServerId: String,
            val queuedOrUpdated: Boolean
        ) : BtbExportResult

        data object NotBound : BtbExportResult
        data object NotFound : BtbExportResult
        data object ReadOnly : BtbExportResult
    }

    data class ImportSummary(
        val worksites: Int,
        val btbWritten: Int,
        val defectStatusesApplied: Int,
        val acceptancesWritten: Int,
        val attachmentsImported: Int = 0
    )

    data class AttachmentUploadSummary(
        val worksites: Int,
        val uploaded: Int,
        val alreadyPresent: Int
    )

    sealed interface AttachmentUploadResult {
        data class Success(val summary: AttachmentUploadSummary) : AttachmentUploadResult
        data object Unsupported : AttachmentUploadResult
        data object SessionInvalid : AttachmentUploadResult
        data class Error(val message: String) : AttachmentUploadResult
    }

    /**
     * Exportiert ausschließlich genau einen lokalen BTB in die bestehende Collaboration-Outbox.
     *
     * Kein Scan von Mängeln, Abnahmen, Baustellenbaum oder anderen BTBs. Die bestehende
     * queueIfChanged-/Hash-/Sanitizing-Logik bleibt maßgeblich.
     */
    suspend fun exportBtbChange(
        localBaustelleId: Long,
        clientBtbId: String
    ): BtbExportResult {
        if (clientBtbId.isBlank()) return BtbExportResult.NotFound

        val item = bindingRepository.contextsWithBindings()
            .firstOrNull { bound ->
                bound.binding != null &&
                    bound.localWorksite?.altBaustelleId == localBaustelleId
            }
            ?: return BtbExportResult.NotBound

        val local = item.localWorksite ?: return BtbExportResult.NotBound
        val server = item.context
        val serverState = BtbServerStateStore.state(appContext, clientBtbId)
        check(serverState?.contextReadOnly != true && !de.tbhelfer.server.WorksiteLocalSyncStore.btbBlocked(appContext, clientBtbId)) {
            "Team/Berechtigung geändert: ausstehende lokale BTB-Daten bleiben zur Prüfung erhalten."
        }
        if (serverState?.readOnly == true) {
            collabRepository.clearServer1BtbPending(server.worksiteServerId, clientBtbId)
            SelectiveSyncStore.clearBtb(appContext, localBaustelleId, clientBtbId)
            return BtbExportResult.ReadOnly
        }
        val localName = local.hauptbaustellenName
        val entry = BautagebuchSpeicher.ladenById(
            appContext,
            localBaustelleId,
            localName,
            clientBtbId
        ) ?: return BtbExportResult.NotFound
        val datum = runCatching { LocalDate.parse(entry.datumIso) }.getOrNull()
            ?: return BtbExportResult.NotFound
        val source = findBtbJson(
            localBaustelleId,
            localName,
            datum,
            entry.schicht,
            clientBtbId
        ) ?: return BtbExportResult.NotFound

        val payloadObject = runCatching {
            JSONObject(source.readText(Charsets.UTF_8))
        }.getOrElse { JSONObject() }
        if (payloadObject.optString("btbId").isBlank()) {
            payloadObject.put("btbId", clientBtbId)
        }
        BtbEntrySlotStore.injectInto(appContext, clientBtbId, payloadObject)
        val payload = sanitizeBtbPayload(
            payloadObject.toString(),
            localBaustelleId,
            localName
        )
        val lifecycleStatus = BtbDokumentVersionierung.status(appContext, entry)
        val syncStatus = if (lifecycleStatus == BtbAbschlussStatus.ABGESCHLOSSEN) {
            "FERTIG"
        } else {
            "ENTWURF"
        }

        val queued = queueIfChanged(
            worksiteServerId = server.worksiteServerId,
            entityType = SyncCollabPendingChangeEntity.TYPE_BTB,
            clientKey = clientBtbId,
            groupId = server.groupId,
            status = syncStatus,
            areaScopeJson = server.areaScopeJson,
            payloadJson = payload
        )
        return BtbExportResult.Success(server.worksiteServerId, queued)
    }

    suspend fun exportLocalChanges(): ExportSummary {
        val bound = bindingRepository.contextsWithBindings().filter { it.binding != null && it.localWorksite != null }
        var total = 0
        var btbCount = 0
        var defectCount = 0
        var acceptanceCount = 0

        for (item in bound) {
            item.binding ?: continue
            val local = item.localWorksite ?: continue
            val server = item.context
            val localBaustelleId = local.altBaustelleId
            val localName = local.hauptbaustellenName

            for (verlauf in BautagebuchSpeicher.verlaufFuerBaustelle(appContext, localBaustelleId, localName)) {
                val clientKey = verlauf.btbId?.takeIf { it.isNotBlank() }
                    ?: "legacy:${verlauf.datum}:${verlauf.schicht.name}"
                val serverState = BtbServerStateStore.state(appContext, clientKey)
                if (serverState?.contextReadOnly == true || de.tbhelfer.server.WorksiteLocalSyncStore.btbBlocked(appContext, clientKey)) {
                    // Historical local data and its pending mutations survive team/access changes.
                    continue
                }
                if (serverState?.readOnly == true) {
                    collabRepository.clearServer1BtbPending(server.worksiteServerId, clientKey)
                    SelectiveSyncStore.clearBtb(appContext, localBaustelleId, clientKey)
                    continue
                }
                val source = findBtbJson(localBaustelleId, localName, verlauf.datum, verlauf.schicht, clientKey) ?: continue
                val payloadObject = runCatching { JSONObject(source.readText(Charsets.UTF_8)) }.getOrElse { JSONObject() }
                if (payloadObject.optString("btbId").isBlank()) payloadObject.put("btbId", clientKey)
                BtbEntrySlotStore.injectInto(appContext, clientKey, payloadObject)
                val payload = sanitizeBtbPayload(payloadObject.toString(), localBaustelleId, localName)
                val lifecycleStatus = BtbDokumentVersionierung.status(appContext, verlauf.entry)
                val syncStatus = if (lifecycleStatus == BtbAbschlussStatus.ABGESCHLOSSEN) {
                    "FERTIG"
                } else {
                    // ENTWURF und NACHBEARBEITUNG sind beide aktiv/bearbeitbar.
                    // Legacy-Felder duerfen einen nachbearbeiteten BTB nicht erneut finalisieren.
                    "ENTWURF"
                }
                if (queueIfChanged(
                        worksiteServerId = server.worksiteServerId,
                        entityType = SyncCollabPendingChangeEntity.TYPE_BTB,
                        clientKey = clientKey,
                        groupId = server.groupId,
                        status = syncStatus,
                        areaScopeJson = server.areaScopeJson,
                        payloadJson = payload
                    )
                ) {
                    total++
                    btbCount++
                }
            }

            val syncDefects = SyncMangelSpeicher.alle(appContext, localBaustelleId).associateBy { it.mangelKey }
            val localDefects = MaengellisteExport.ladeItems(appContext, localBaustelleId, localName)
            val localDefectKeys = localDefects.mapTo(hashSetOf()) { it.key }
            for (defect in localDefects) {
                val status = MangelStatusSpeicher.laden(appContext, defect.key)
                val cached = syncDefects[defect.key]
                val payload = if (cached != null) syncDefectPayload(cached.payloadJson, status)
                else localDefectPayload(defect, localName, status)

                if (queueIfChanged(
                        worksiteServerId = server.worksiteServerId,
                        entityType = SyncCollabPendingChangeEntity.TYPE_DEFECT,
                        clientKey = defect.key,
                        groupId = null,
                        status = status.status.name,
                        areaScopeJson = server.areaScopeJson,
                        payloadJson = payload
                    )
                ) {
                    total++
                    defectCount++
                }
            }

            for (defect in syncDefects.values.filter { it.mangelKey !in localDefectKeys }) {
                val status = MangelStatusSpeicher.laden(appContext, defect.mangelKey)
                val payload = syncDefectPayload(defect.payloadJson, status)
                if (queueIfChanged(
                        worksiteServerId = server.worksiteServerId,
                        entityType = SyncCollabPendingChangeEntity.TYPE_DEFECT,
                        clientKey = defect.mangelKey,
                        groupId = null,
                        status = status.status.name,
                        areaScopeJson = server.areaScopeJson,
                        payloadJson = payload
                    )
                ) {
                    total++
                    defectCount++
                }
            }

            val acceptances = AbnahmeSpeicher.alle(appContext, localBaustelleId)
            for (acceptance in acceptances) {
                val payload = acceptancePayload(acceptance, localName)
                if (queueIfChanged(
                        worksiteServerId = server.worksiteServerId,
                        entityType = SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE,
                        clientKey = acceptance.id,
                        groupId = null,
                        status = acceptance.ergebnis.name,
                        areaScopeJson = server.areaScopeJson,
                        payloadJson = payload
                    )
                ) {
                    total++
                    acceptanceCount++
                }
            }
        }
        return ExportSummary(bound.size, total, btbCount, defectCount, acceptanceCount)
    }

    data class AcceptanceDeleteQueueSummary(
        val queued: Int,
        val alreadyQueued: Int,
        val alreadyGoneOrLocalOnly: Int
    )

    /**
     * Überführt nur ausdrücklich gespeicherte Benutzer-Löschwünsche in die Server-1.0-Outbox.
     * Diese Methode darf erst nach einem vollständigen aktuellen Acceptance-Snapshot laufen.
     */
    suspend fun queueServer1AcceptanceDeletes(): AcceptanceDeleteQueueSummary {
        val bound = bindingRepository.contextsWithBindings()
            .filter { it.binding != null && it.localWorksite != null }
        var queued = 0
        var alreadyQueued = 0
        var gone = 0

        for (item in bound) {
            val local = item.localWorksite ?: continue
            val server = item.context
            val serverByKey = collabRepository.entities(
                server.worksiteServerId,
                SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE
            ).mapNotNull { entity ->
                entity.clientEntityKey?.takeIf { it.isNotBlank() }?.let { it to entity }
            }.toMap()

            for (intent in AbnahmeDeleteIntentStore.fuerBaustelle(appContext, local.altBaustelleId)) {
                val pending = dao.collabPendingAnyForClientKey(
                    server.worksiteServerId,
                    SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE,
                    intent.acceptanceId
                )
                val serverEntity = serverByKey[intent.acceptanceId]

                if (serverEntity == null) {
                    // Nie hochgeladen oder bereits anderweitig gelöscht. Eine alte UPSERT/DELETE-
                    // Queue darf die lokale Benutzerlöschung nicht wieder auferstehen lassen.
                    if (pending != null) dao.deleteCollabPendingChange(pending.id)
                    AbnahmeDeleteIntentStore.bestaetigen(appContext, intent.acceptanceId)
                    gone++
                    continue
                }

                if (pending?.operation.equals(
                        SyncCollabPendingChangeEntity.OP_DELETE,
                        ignoreCase = true
                    )
                ) {
                    alreadyQueued++
                    continue
                }

                if (pending != null) dao.deleteCollabPendingChange(pending.id)
                collabRepository.enqueueMutation(
                    worksiteServerId = server.worksiteServerId,
                    entityType = SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE,
                    operation = SyncCollabPendingChangeEntity.OP_DELETE,
                    serverId = serverEntity.serverId,
                    clientEntityKey = intent.acceptanceId,
                    baseVersion = serverEntity.entityVersion,
                    groupId = null,
                    status = serverEntity.status,
                    areaScopeJson = serverEntity.areaScopeJson,
                    payloadJson = "{}"
                )
                queued++
            }
        }

        return AcceptanceDeleteQueueSummary(queued, alreadyQueued, gone)
    }

    suspend fun uploadLocalAttachments(
        token: String,
        api: CollabSyncApi = CollabSyncApi(),
        server1Api: Server1FileApi? = null
    ): AttachmentUploadResult {
        val bound = bindingRepository.contextsWithBindings().filter { it.binding != null && it.localWorksite != null }
        var uploaded = 0
        var present = 0

        for (item in bound) {
            val local = item.localWorksite ?: continue
            val server = item.context
            val localBaustelleId = local.altBaustelleId
            val localName = local.hauptbaustellenName

            val serverDefects = collabRepository.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_DEFECT)
                .mapNotNull { entity -> entity.clientEntityKey?.let { it to entity } }.toMap()
            for (defect in MaengellisteExport.ladeItems(appContext, localBaustelleId, localName)) {
                val serverEntity = serverDefects[defect.key] ?: continue
                for (mf in defect.fotos) {
                    val file = File(mf.foto.dateipfad)
                    if (!file.exists() || !file.isFile) continue
                    val knownServerId = serverAttachmentForLocalPhoto(serverEntity.serverId, mf.foto.id)
                    if (knownServerId != null) {
                        present++
                        continue
                    }
                    val syncFile = MobileImageSync.prepare(
                        appContext,
                        file,
                        "defect_${mf.foto.id}"
                    ) ?: return AttachmentUploadResult.Error(
                        "Mängelfoto konnte nicht für den Server verkleinert werden: ${file.name}"
                    )
                    val uploadName = semanticFilename(
                        "DEFECT",
                        mf.art.name,
                        MobileImageSync.syncFilename(file.name)
                    )
                    when (val result = collabRepository.uploadAttachment(
                        token = token,
                        worksiteServerId = server.worksiteServerId,
                        parentEntityType = SyncCollabPendingChangeEntity.TYPE_DEFECT,
                        parentEntityId = serverEntity.serverId,
                        file = syncFile,
                        uploadFilename = uploadName,
                        mimeType = "image/jpeg",
                        capturedAtEpochMs = mf.foto.erstelltAmEpochMs,
                        gpsLat = mf.foto.gpsLat,
                        gpsLon = mf.foto.gpsLon,
                        api = api,
                        server1Api = server1Api
                    )) {
                        CollabSyncRepository.LocalAttachmentUploadResult.SessionInvalid -> return AttachmentUploadResult.SessionInvalid
                        CollabSyncRepository.LocalAttachmentUploadResult.Unsupported -> return AttachmentUploadResult.Unsupported
                        is CollabSyncRepository.LocalAttachmentUploadResult.Error -> return AttachmentUploadResult.Error(result.message)
                        is CollabSyncRepository.LocalAttachmentUploadResult.Success -> {
                            if (result.uploaded) uploaded++ else present++
                            rememberAttachmentPhoto(result.serverId, mf.foto.id)
                        }
                    }
                }
            }

            val serverAcceptances = collabRepository.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE)
                .mapNotNull { entity -> entity.clientEntityKey?.let { it to entity } }.toMap()
            for (acceptance in AbnahmeSpeicher.alle(appContext, localBaustelleId)) {
                val serverEntity = serverAcceptances[acceptance.id] ?: continue
                val fotoEntities = fotoRepository.fotosMitIds(acceptance.fotos.map { it.fotoId }).associateBy { it.id }
                for (ref in acceptance.fotos) {
                    val foto = fotoEntities[ref.fotoId] ?: continue
                    val file = File(foto.dateipfad)
                    if (!file.exists() || !file.isFile) continue
                    val knownServerId = serverAttachmentForLocalPhoto(serverEntity.serverId, foto.id)
                    if (knownServerId != null) {
                        present++
                        continue
                    }
                    val syncFile = MobileImageSync.prepare(
                        appContext,
                        file,
                        "acceptance_foto_${foto.id}"
                    ) ?: return AttachmentUploadResult.Error(
                        "Abnahmefoto konnte nicht für den Server verkleinert werden: ${file.name}"
                    )
                    val uploadName = semanticFilename(
                        "ACCEPTANCE_FOTO",
                        ref.bildArt.name,
                        MobileImageSync.syncFilename(file.name)
                    )
                    when (val result = collabRepository.uploadAttachment(
                        token = token,
                        worksiteServerId = server.worksiteServerId,
                        parentEntityType = SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE,
                        parentEntityId = serverEntity.serverId,
                        file = syncFile,
                        uploadFilename = uploadName,
                        mimeType = "image/jpeg",
                        capturedAtEpochMs = foto.erstelltAmEpochMs,
                        gpsLat = foto.gpsLat,
                        gpsLon = foto.gpsLon,
                        api = api,
                        server1Api = server1Api
                    )) {
                        CollabSyncRepository.LocalAttachmentUploadResult.SessionInvalid -> return AttachmentUploadResult.SessionInvalid
                        CollabSyncRepository.LocalAttachmentUploadResult.Unsupported -> return AttachmentUploadResult.Unsupported
                        is CollabSyncRepository.LocalAttachmentUploadResult.Error -> return AttachmentUploadResult.Error(result.message)
                        is CollabSyncRepository.LocalAttachmentUploadResult.Success -> {
                            if (result.uploaded) uploaded++ else present++
                            rememberAttachmentPhoto(result.serverId, foto.id)
                        }
                    }
                }
                for (anlage in acceptance.anlagen) {
                    val file = when {
                        anlage.typ == AbnahmeSpeicher.AnlageTyp.BILD &&
                            !anlage.ausschnittPfad.isNullOrBlank() &&
                            File(anlage.ausschnittPfad).exists() -> File(anlage.ausschnittPfad)
                        else -> File(anlage.originalPfad)
                    }
                    if (!file.exists() || !file.isFile) continue

                    val legacyUploadName = semanticFilename(
                        "ACCEPTANCE_ANLAGE",
                        anlage.typ.name,
                        file.name
                    )
                    val uploadFile = if (anlage.typ == AbnahmeSpeicher.AnlageTyp.BILD) {
                        MobileImageSync.prepare(
                            appContext,
                            file,
                            "acceptance_anlage_${acceptance.id}_${anlage.id}"
                        ) ?: return AttachmentUploadResult.Error(
                            "Abnahme-Anlagenbild konnte nicht für den Server verkleinert werden: ${file.name}"
                        )
                    } else {
                        file
                    }
                    val uploadName = if (anlage.typ == AbnahmeSpeicher.AnlageTyp.BILD) {
                        semanticFilename(
                            "ACCEPTANCE_ANLAGE",
                            anlage.typ.name,
                            MobileImageSync.syncFilename(file.name)
                        )
                    } else {
                        legacyUploadName
                    }
                    // Nur alte Bild-Anlagen mit dem bisherigen Original-Dateinamen beim
                    // Umstieg auf *_sync.jpg erkennen. PDF-/sonstige Anlagen bleiben weiterhin
                    // hashbasiert und können bei geänderten Bytes normal neu hochgeladen werden.
                    if (
                        anlage.typ == AbnahmeSpeicher.AnlageTyp.BILD &&
                        legacyUploadName != uploadName &&
                        serverLinkedAttachmentByFilename(
                            serverEntity.serverId,
                            setOf(legacyUploadName)
                        ) != null
                    ) {
                        present++
                        continue
                    }

                    when (val result = collabRepository.uploadAttachment(
                        token = token,
                        worksiteServerId = server.worksiteServerId,
                        parentEntityType = SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE,
                        parentEntityId = serverEntity.serverId,
                        file = uploadFile,
                        uploadFilename = uploadName,
                        mimeType = if (anlage.typ == AbnahmeSpeicher.AnlageTyp.BILD) {
                            "image/jpeg"
                        } else {
                            CollabSyncRepository.mimeTypeFor(uploadFile)
                        },
                        api = api,
                        server1Api = server1Api
                    )) {
                        CollabSyncRepository.LocalAttachmentUploadResult.SessionInvalid -> return AttachmentUploadResult.SessionInvalid
                        CollabSyncRepository.LocalAttachmentUploadResult.Unsupported -> return AttachmentUploadResult.Unsupported
                        is CollabSyncRepository.LocalAttachmentUploadResult.Error -> return AttachmentUploadResult.Error(result.message)
                        is CollabSyncRepository.LocalAttachmentUploadResult.Success -> {
                            if (result.uploaded) uploaded++ else present++
                            rememberAttachmentLinked(result.serverId)
                        }
                    }
                }
            }
        }
        return AttachmentUploadResult.Success(AttachmentUploadSummary(bound.size, uploaded, present))
    }

    /**
     * Uebernimmt nur Rollen/Lifecycle/Slot-Metadaten aus dem bereits geladenen Servercache.
     * Lokale BTB-Fachinhalte werden dabei NICHT ueberschrieben. Dieser Schritt muss bei einem
     * Recovery-Vollsync vor exportLocalChanges laufen, damit alte FINALIZED-BTBs keine neue
     * Outbox-Mutation erzeugen und vorhandene _tb_android_slot-Werte erhalten bleiben.
     */
    suspend fun primeBtbServerState(
        worksiteServerIds: Set<String>? = null
    ): Int {
        val bound = bindingRepository.contextsWithBindings().filter {
            it.binding != null &&
                it.localWorksite != null &&
                (worksiteServerIds == null || it.context.worksiteServerId in worksiteServerIds)
        }
        var primed = 0
        for (item in bound) {
            val local = item.localWorksite ?: continue
            val server = item.context
            val localBaustelleId = local.altBaustelleId
            val btbItems = collabRepository.entities(
                server.worksiteServerId,
                SyncCollabPendingChangeEntity.TYPE_BTB
            ).filter { it.groupId == server.groupId }

            for (entity in btbItems) {
                val key = entity.clientEntityKey ?: continue
                val payload = runCatching { JSONObject(entity.payloadJson) }.getOrNull() ?: continue
                BtbServerStateStore.rememberSnapshot(
                    appContext,
                    worksiteServerId = server.worksiteServerId,
                    serverBtbId = entity.serverId,
                    teamId = entity.groupId ?: server.groupId,
                    clientBtbId = key,
                    payloadJson = entity.payloadJson
                )
                BtbEntrySlotStore.remember(
                    appContext,
                    key,
                    payload.optJSONObject(Server1BtbApi.LOCAL_ENTRY_SLOTS_KEY)
                )
                val serverFinal = entity.status.equals("FERTIG", ignoreCase = true) &&
                    payload.optString(Server1BtbApi.SERVER_STATUS_KEY)
                        .equals("FINALIZED", ignoreCase = true) &&
                    !payload.optBoolean(Server1BtbApi.SERVER_ACTIVE_KEY, false)
                if (serverFinal) {
                    collabRepository.clearServer1BtbPending(server.worksiteServerId, key)
                    SelectiveSyncStore.clearBtb(appContext, localBaustelleId, key)
                }
                primed++
            }
        }
        return primed
    }

    suspend fun applyServerStateToLocal(
        worksiteServerIds: Set<String>? = null,
        btbServerIds: Set<String>? = null,
        includeOtherEntities: Boolean = true
    ): ImportSummary {
        val bound = bindingRepository.contextsWithBindings().filter {
            it.binding != null &&
                it.localWorksite != null &&
                (worksiteServerIds == null || it.context.worksiteServerId in worksiteServerIds)
        }
        var btbWritten = 0
        var defectStatuses = 0
        var acceptanceWritten = 0
        var attachmentsImported = 0

        for (item in bound) {
            val local = item.localWorksite ?: continue
            val server = item.context
            val localBaustelleId = local.altBaustelleId
            val localName = local.hauptbaustellenName

            val btbItems = collabRepository.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_BTB)
                .filter {
                    it.groupId == server.groupId &&
                        (btbServerIds == null || it.serverId in btbServerIds)
                }
            for (entity in btbItems) {
                val key = entity.clientEntityKey ?: continue
                val payload = runCatching { JSONObject(entity.payloadJson) }.getOrNull() ?: continue

                BtbServerStateStore.rememberSnapshot(
                    appContext,
                    worksiteServerId = server.worksiteServerId,
                    serverBtbId = entity.serverId,
                    teamId = entity.groupId ?: server.groupId,
                    clientBtbId = key,
                    payloadJson = entity.payloadJson
                )
                BtbEntrySlotStore.remember(
                    appContext,
                    key,
                    payload.optJSONObject(Server1BtbApi.LOCAL_ENTRY_SLOTS_KEY)
                )

                val serverActive = payload.optBoolean(Server1BtbApi.SERVER_ACTIVE_KEY, false)
                val serverFinal = entity.status.equals("FERTIG", ignoreCase = true) &&
                    payload.optString(Server1BtbApi.SERVER_STATUS_KEY).equals("FINALIZED", ignoreCase = true) &&
                    !serverActive
                if (serverFinal) {
                    // Kanonisch bestaetigter historischer BTB: alte Auto-Dirty-/Pending-Reste
                    // duerfen keinen neuen Header-/Entry-/Foto-Upload mehr provozieren.
                    collabRepository.clearServer1BtbPending(server.worksiteServerId, key)
                    SelectiveSyncStore.clearBtb(appContext, localBaustelleId, key)
                } else if (
                    dao.collabPendingForClientKey(server.worksiteServerId, entity.entityType, key) != null ||
                    SelectiveSyncStore.isBtbDirty(appContext, localBaustelleId, key)
                ) {
                    // Unbestätigte lokale Fachänderung hat Vorrang vor einem Snapshot-Apply.
                    // Der Serverstand bleibt im Cache/BtbServerStateStore bekannt, darf aber die
                    // lokale BTB-Datei erst nach Upload-/Conflict-Abschluss ersetzen.
                    continue
                }

                payload.remove(Server1BtbApi.SERVER_CURRENT_USER_KEY)
                payload.remove(Server1BtbApi.SERVER_PARTICIPANTS_KEY)
                payload.remove(Server1BtbApi.SERVER_PHOTOS_KEY)
                payload.remove(Server1BtbApi.SERVER_ENTRY_IDS_KEY)
                payload.remove(Server1BtbApi.LOCAL_ENTRY_SLOTS_KEY)
                val datum = payload.optString("datumIso").takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
                val schicht = runCatching { SchichtTyp.valueOf(payload.optString("schicht", SchichtTyp.TAG.name)) }
                    .getOrDefault(SchichtTyp.TAG)

                payload.put("baustelleId", localBaustelleId)
                payload.put("baustelleName", localName)
                payload.put("btbId", payload.optString("btbId").takeIf { it.isNotBlank() } ?: key)
                payload.put("fotos", JSONArray())
                payload.put("pdfAuswahl", JSONObject())
                val canonicalPayload = payload.toString()

                val existingMediaSource = findBtbJson(localBaustelleId, localName, datum, schicht, key)
                val localPayload = mergeLocalBtbMedia(existingMediaSource, payload)
                val target = File(
                    BautagebuchSpeicher.ordner(appContext, localBaustelleId, localName, datum),
                    "btb_${safeBtbFileToken(payload.optString("btbId", key))}.json"
                )

                val appliedToLocal = SelectiveSyncStore.withBtbMutationLock(localBaustelleId, key) {
                    // Zweite Dirty-Prüfung im selben Lock wie der Dateischreibzugriff schließt
                    // das Rennen "Autosave nach erster Prüfung, aber vor Snapshot-Write".
                    if (!serverFinal && SelectiveSyncStore.isBtbDirty(appContext, localBaustelleId, key)) {
                        false
                    } else {
                        BautagebuchSpeicher.bewahreEintragszeitFormat(localPayload, target)
                        if (writeIfDifferent(target, localPayload.toString(2))) btbWritten++

                        // Server ist fuer den Team-Lifecycle kanonisch. Ein aktiver Server-BTB wird auf
                        // allen Geraeten derselben Baustelle aktiv; ein bestaetigtes FINALIZED loest nur
                        // dann den lokalen Pointer, wenn genau dieser BTB dort noch aktiv war.
                        if (serverActive) {
                            BtbActivePointerControl.inaktivMarkerLoeschen(
                                appContext,
                                localBaustelleId,
                                localName
                            )
                            BtbActivePointerControl.reaktivierungsMarkerLoeschen(
                                appContext,
                                localBaustelleId,
                                localName
                            )
                            BautagebuchSpeicher.aktivierenBtb(
                                appContext,
                                localBaustelleId,
                                localName,
                                key,
                                datum,
                                schicht
                            )
                        } else if (
                            serverFinal &&
                            BautagebuchSpeicher.aktiveBtbId(
                                appContext,
                                localBaustelleId,
                                localName
                            ) == key
                        ) {
                            BtbActivePointerControl.nachAbschlussInaktivMerken(
                                appContext,
                                localBaustelleId,
                                localName,
                                key
                            )
                        }
                        true
                    }
                }
                if (!appliedToLocal) continue

                rememberHash(server.worksiteServerId, entity.entityType, key, stableHash(entity.status, canonicalPayload))
            }

            if (!includeOtherEntities) continue

            val defectItems = collabRepository.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_DEFECT)
            val activeDefectServerIds = defectItems.mapTo(hashSetOf()) { it.serverId }
            for (entity in defectItems) {
                val key = entity.clientEntityKey ?: continue
                if (dao.collabPendingForClientKey(server.worksiteServerId, entity.entityType, key) != null) continue
                val payload = runCatching { JSONObject(entity.payloadJson) }.getOrElse { JSONObject() }
                val incomingStatus = runCatching {
                    MangelStatusSpeicher.Status.valueOf(entity.status ?: payload.optString("status", "OFFEN"))
                }.getOrDefault(MangelStatusSpeicher.Status.OFFEN)
                val old = MangelStatusSpeicher.laden(appContext, key)
                val newValue = if (old.status == MangelStatusSpeicher.Status.ERLEDIGT) old else old.copy(
                    status = incomingStatus,
                    kommentar = payload.optString("kommentar", old.kommentar),
                    erledigtAmEpochMs = payload.optNullableLong("erledigtAmEpochMs") ?: old.erledigtAmEpochMs,
                    bereichOverride = payload.optString("bereich").trim().takeIf { it.isNotBlank() },
                    kmOverride = if (payload.has("km") && !payload.isNull("km")) payload.optString("km", "").trim() else old.kmOverride,
                    firmaOverride = payload.optString("firma").trim().takeIf { it.isNotBlank() },
                    beschreibungOverride = payload.optString("beschreibung").trim().takeIf { it.isNotBlank() }
                )
                MangelStatusSpeicher.speichern(appContext, key, newValue)
                SyncMangelSpeicher.speichern(
                    appContext,
                    SyncMangelSpeicher.Eintrag(
                        localBaustelleId = localBaustelleId,
                        worksiteServerId = server.worksiteServerId,
                        serverEntityId = entity.serverId,
                        mangelKey = key,
                        datumIso = payload.optString("datumIso", ""),
                        zeit = payload.optString("zeit", ""),
                        bereich = payload.optString("bereich", "Hauptbaustelle").ifBlank { "Hauptbaustelle" },
                        km = payload.optString("km", "-").ifBlank { "-" },
                        firma = payload.optString("firma", "-").ifBlank { "-" },
                        beschreibung = payload.optString("beschreibung", "-").ifBlank { "-" },
                        gps = payload.optString("gps", "-").ifBlank { "-" },
                        btbBildNr = payload.optString("btbBildNr", ""),
                        payloadJson = syncDefectPayload(entity.payloadJson, newValue),
                        changedByName = entity.changedByName
                    )
                )
                defectStatuses++
                attachmentsImported += importDefectAttachments(entity, key, payload, localBaustelleId)
                val canonical = syncDefectPayload(entity.payloadJson, MangelStatusSpeicher.laden(appContext, key))
                rememberHash(server.worksiteServerId, entity.entityType, key, stableHash(MangelStatusSpeicher.laden(appContext, key).status.name, canonical))
            }
            SyncMangelSpeicher.bereinigeNichtMehrVorhandene(appContext, localBaustelleId, server.worksiteServerId, activeDefectServerIds)

            val acceptanceItems = collabRepository.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE)
            for (entity in acceptanceItems) {
                val key = entity.clientEntityKey ?: continue
                if (dao.collabPendingForClientKey(server.worksiteServerId, entity.entityType, key) != null) continue
                val parsed = acceptanceFromPayload(
                    entity.payloadJson, key, localBaustelleId, localName, local.id, entity.status
                ) ?: continue
                val old = AbnahmeSpeicher.laden(appContext, parsed.id)
                if (old?.istEndgueltigAbgeschlossen == true && !parsed.istEndgueltigAbgeschlossen) continue
                val saved = AbnahmeSpeicher.speichern(appContext, mergeAcceptanceMedia(old, parsed))
                acceptanceWritten++
                attachmentsImported += importAcceptanceAttachments(entity, saved)
                val finalSaved = AbnahmeSpeicher.laden(appContext, saved.id) ?: saved
                rememberHash(server.worksiteServerId, entity.entityType, key, stableHash(finalSaved.ergebnis.name, acceptancePayload(finalSaved, localName)))
            }

            val deletedAcceptanceItems = collabRepository.deletedEntities(
                server.worksiteServerId,
                SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE
            )
            for (entity in deletedAcceptanceItems) {
                val key = entity.clientEntityKey ?: continue
                // Lokale noch nicht bestätigte Änderungen niemals still durch einen fremden
                // Tombstone verwerfen. Der bestehende Konflikt-/Outboxpfad bleibt maßgeblich.
                if (
                    dao.collabPendingAnyForClientKey(
                        server.worksiteServerId,
                        SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE,
                        key
                    ) != null
                ) continue

                val localAcceptance = AbnahmeSpeicher.laden(appContext, key) ?: continue
                AbnahmeSpeicher.loeschenVomServer(appContext, localAcceptance)
                acceptanceWritten++
            }
        }
        return ImportSummary(bound.size, btbWritten, defectStatuses, acceptanceWritten, attachmentsImported)
    }

    private suspend fun importDefectAttachments(
        entity: SyncCollabEntity,
        mangelKey: String,
        payload: JSONObject,
        localBaustelleId: Long
    ): Int {
        var imported = 0
        val gps = parseGps(payload.optString("gps", ""))
        val createdAt = defectEpoch(payload)
        val firma = payload.optString("firma", "").takeUnless { it == "-" }.orEmpty()
        val description = payload.optString("beschreibung", "").takeUnless { it == "-" }.orEmpty().ifBlank { "Mangel" }
        val km = parseKmDisplay(payload.optString("km", ""))
        for (attachment in collabRepository.attachments(entity.serverId)) {
            val path = attachment.localPath?.takeIf { attachment.isDownloaded && File(it).exists() } ?: continue
            if (dao.state(attachmentImportKey(attachment.serverId)) != null) continue
            if (!(attachment.mimeType?.startsWith("image/") == true || isImageFile(path))) continue
            val art = parseDefectFotoArt(attachment.filename)
            val before = MangelStatusSpeicher.laden(appContext, mangelKey)
            val fotoId = fotoRepository.einfuegen(
                FotoEntity(
                    baustelleId = localBaustelleId,
                    baustellenKnotenId = null,
                    dateipfad = path,
                    gpsLat = gps.first,
                    gpsLon = gps.second,
                    gpsAltM = null,
                    km = km,
                    kmSuffix = 0,
                    kmManuell = false,
                    achsabstandM = null,
                    indikator = null,
                    firma = firma,
                    bildtyp = Bildtyp.MAENGEL_SCHAEDEN,
                    kurztext = description,
                    notiz = "Synchronisiertes ${MaengellisteExport.fotoArtText(art)}-Foto",
                    erstelltAmEpochMs = createdAt
                )
            )
            MangelStatusSpeicher.fotoZuordnen(appContext, mangelKey, fotoId, art)
            MangelStatusSpeicher.speichern(appContext, mangelKey, before)
            rememberAttachmentPhoto(attachment.serverId, fotoId)
            imported++
        }
        return imported
    }

    private suspend fun importAcceptanceAttachments(entity: SyncCollabEntity, acceptance: AbnahmeSpeicher.Abnahme): Int {
        var imported = 0
        var current = acceptance
        for (attachment in collabRepository.attachments(entity.serverId)) {
            val path = attachment.localPath?.takeIf { attachment.isDownloaded && File(it).exists() } ?: continue
            if (dao.state(attachmentImportKey(attachment.serverId)) != null) continue
            val filename = attachment.filename.orEmpty()
            if (filename.startsWith("ACCEPTANCE_ANLAGE__", ignoreCase = true) || attachment.mimeType == "application/pdf") {
                val typ = parseAcceptanceAnlageTyp(filename, attachment.mimeType)
                val anlageId = "server:${attachment.serverId}"
                if (current.anlagen.none { it.id == anlageId }) {
                    current = AbnahmeSpeicher.anlageHinzufuegen(
                        appContext,
                        current.id,
                        AbnahmeSpeicher.Anlage(
                            id = anlageId,
                            typ = typ,
                            originalPfad = path,
                            ausschnittPfad = null,
                            bezeichnung = if (typ == AbnahmeSpeicher.AnlageTyp.PDF) "Synchronisierte Planunterlage PDF" else "Synchronisierte Plan-/Objektunterlage",
                            bemerkung = ""
                        )
                    ) ?: current
                }
                rememberAttachmentLinked(attachment.serverId)
                imported++
                continue
            }
            if (attachment.mimeType?.startsWith("image/") == true || isImageFile(path)) {
                val art = parseAcceptanceBildArt(filename)
                val fotoId = fotoRepository.einfuegen(
                    FotoEntity(
                        baustelleId = current.baustelleId,
                        baustellenKnotenId = current.unterKnotenId,
                        dateipfad = path,
                        gpsLat = 0.0,
                        gpsLon = 0.0,
                        gpsAltM = null,
                        km = current.kmVon,
                        kmSuffix = 0,
                        kmManuell = false,
                        achsabstandM = null,
                        indikator = null,
                        firma = current.firma,
                        bildtyp = Bildtyp.ABNAHMEN,
                        kurztext = current.bezeichnung.ifBlank { current.objekt }.ifBlank { "Abnahme" },
                        notiz = "Synchronisiertes Abnahmefoto",
                        erstelltAmEpochMs = System.currentTimeMillis()
                    )
                )
                current = AbnahmeSpeicher.fotoZuordnen(appContext, current.id, fotoId, art, "") ?: current
                rememberAttachmentPhoto(attachment.serverId, fotoId)
                imported++
            }
        }
        return imported
    }

    private suspend fun queueIfChanged(
        worksiteServerId: String,
        entityType: String,
        clientKey: String,
        groupId: String?,
        status: String?,
        areaScopeJson: String,
        payloadJson: String
    ): Boolean {
        val type = entityType.uppercase()
        val hash = stableHash(status, payloadJson)
        val markerKey = markerKey(worksiteServerId, type, clientKey)
        if (dao.state(markerKey)?.textValue == hash) return false
        val serverEntity = dao.collabEntities(worksiteServerId, type).firstOrNull { it.clientEntityKey == clientKey }
        val pending = dao.collabPendingForClientKey(worksiteServerId, type, clientKey)
        if (pending != null) {
            dao.replaceCollabPendingUpsert(
                id = pending.id,
                serverId = serverEntity?.serverId ?: pending.serverId,
                baseVersion = serverEntity?.entityVersion ?: pending.baseVersion,
                groupId = groupId ?: pending.groupId,
                status = status,
                areaScopeJson = areaScopeJson,
                payloadJson = payloadJson
            )
        } else {
            collabRepository.enqueueMutation(
                worksiteServerId = worksiteServerId,
                entityType = type,
                operation = SyncCollabPendingChangeEntity.OP_UPSERT,
                serverId = serverEntity?.serverId,
                clientEntityKey = clientKey,
                baseVersion = serverEntity?.entityVersion,
                groupId = groupId,
                status = status,
                areaScopeJson = areaScopeJson,
                payloadJson = payloadJson
            )
        }
        rememberHash(worksiteServerId, type, clientKey, hash)
        return true
    }

    private fun localDefectPayload(defect: MaengellisteExport.MangelItem, localName: String, status: MangelStatusSpeicher.Wert): String = JSONObject()
        .put("key", defect.key).put("datumIso", defect.datumIso).put("zeit", defect.zeit)
        .put("baustelle", localName).put("bereich", defect.bereich).put("km", defect.km)
        .put("firma", defect.firma).put("beschreibung", defect.beschreibung).put("gps", defect.gps)
        .put("btbBildNr", defect.btbBildNr).put("kommentar", status.kommentar)
        .putNullable("erledigtAmEpochMs", status.erledigtAmEpochMs).put("fotos", JSONArray()).toString()

    private fun syncDefectPayload(raw: String, status: MangelStatusSpeicher.Wert): String {
        val payload = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        payload.put("kommentar", status.kommentar)
        payload.putNullable("erledigtAmEpochMs", status.erledigtAmEpochMs)
        status.bereichOverride?.takeIf { it.isNotBlank() }?.let { payload.put("bereich", it) }
        status.kmOverride?.let { payload.put("km", it) }
        status.firmaOverride?.takeIf { it.isNotBlank() }?.let { payload.put("firma", it) }
        status.beschreibungOverride?.takeIf { it.isNotBlank() }?.let { payload.put("beschreibung", it) }
        payload.put("fotos", JSONArray())
        return payload.toString()
    }

    private fun findBtbJson(
        baustelleId: Long,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp,
        btbId: String? = null
    ): File? {
        val dir = BautagebuchSpeicher.ordner(appContext, baustelleId, baustelleName, datum)
        if (!btbId.isNullOrBlank() && !btbId.startsWith("legacy:")) {
            val instance = File(dir, "btb_${safeBtbFileToken(btbId)}.json")
            if (instance.exists()) return instance
        }
        val preferred = File(dir, if (schicht == SchichtTyp.NACHT) "bautagebuch_nacht.json" else "bautagebuch_tag.json")
        if (preferred.exists()) return preferred
        if (schicht == SchichtTyp.TAG) {
            val legacy = File(dir, "bautagebuch.json")
            if (legacy.exists()) return legacy
        }
        return null
    }

    private fun safeBtbFileToken(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9_-]"), "_")
        .take(80)
        .ifBlank { sha256(value).take(24) }

    private fun sanitizeBtbPayload(raw: String, baustelleId: Long, baustelleName: String): String {
        val payload = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        payload.put("baustelleId", baustelleId)
        payload.put("baustelleName", baustelleName)
        payload.put("fotos", JSONArray())
        payload.put("pdfAuswahl", JSONObject())
        return payload.toString()
    }

    private fun mergeLocalBtbMedia(source: File?, canonical: JSONObject): JSONObject {
        val merged = JSONObject(canonical.toString())
        val existing = source?.takeIf { it.exists() && it.isFile }
            ?.let { runCatching { JSONObject(it.readText(Charsets.UTF_8)) }.getOrNull() } ?: return merged
        existing.optJSONArray("fotos")?.let { merged.put("fotos", JSONArray(it.toString())) }
        existing.optJSONObject("pdfAuswahl")?.let { merged.put("pdfAuswahl", JSONObject(it.toString())) }
        return merged
    }

    private fun acceptancePayload(a: AbnahmeSpeicher.Abnahme, localName: String): String = JSONObject()
        .put("id", a.id).put("nummer", a.nummer).put("baustelleName", localName)
        .put("unterKnotenName", a.unterKnotenName).put("bezeichnung", a.bezeichnung).put("objekt", a.objekt)
        .put("firma", a.firma).put("weitereFirmen", a.weitereFirmen).put("abnehmender", a.abnehmender)
        .put("anwesende", a.anwesende).putNullable("kmVon", a.kmVon).putNullable("kmBis", a.kmBis)
        .put("kommentar", a.kommentar).put("restpunkte", a.restpunkte).put("ergebnis", a.ergebnis.name)
        .put("startEpochMs", a.startEpochMs).putNullable("abgeschlossenAmEpochMs", a.abgeschlossenAmEpochMs)
        .put("fotoRefs", JSONArray()).put("anlagen", JSONArray()).toString()

    private fun acceptanceFromPayload(
        payloadJson: String,
        clientKey: String,
        localBaustelleId: Long,
        localName: String,
        localRootKnotenId: Long,
        status: String?
    ): AbnahmeSpeicher.Abnahme? {
        val o = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return null
        val ergebnis = runCatching {
            AbnahmeSpeicher.Ergebnis.valueOf(status ?: o.optString("ergebnis", AbnahmeSpeicher.Ergebnis.ENTWURF.name))
        }.getOrDefault(AbnahmeSpeicher.Ergebnis.ENTWURF)
        val completed = o.optNullableLong("abgeschlossenAmEpochMs")
        val isFinal = ergebnis == AbnahmeSpeicher.Ergebnis.ABGENOMMEN || ergebnis == AbnahmeSpeicher.Ergebnis.NICHT_ABGENOMMEN
        return AbnahmeSpeicher.Abnahme(
            id = o.optString("id").ifBlank { clientKey }, nummer = o.optInt("nummer", 1),
            baustelleId = localBaustelleId, baustelleName = localName, hauptKnotenId = localRootKnotenId,
            unterKnotenId = null, unterKnotenName = o.optString("unterKnotenName", ""),
            bezeichnung = o.optString("bezeichnung", ""), objekt = o.optString("objekt", ""),
            firma = o.optString("firma", ""), weitereFirmen = o.optString("weitereFirmen", ""),
            abnehmender = o.optString("abnehmender", ""), anwesende = o.optString("anwesende", ""),
            kmVon = o.optNullableDouble("kmVon"), kmBis = o.optNullableDouble("kmBis"),
            kommentar = o.optString("kommentar", ""), restpunkte = o.optString("restpunkte", ""),
            ergebnis = ergebnis, startEpochMs = o.optLong("startEpochMs", System.currentTimeMillis()),
            abgeschlossenAmEpochMs = if (isFinal) completed ?: System.currentTimeMillis() else null,
            wiederGeoeffnetAmEpochMs = null, fotos = emptyList(), anlagen = emptyList()
        )
    }

    private fun mergeAcceptanceMedia(old: AbnahmeSpeicher.Abnahme?, incoming: AbnahmeSpeicher.Abnahme): AbnahmeSpeicher.Abnahme =
        if (old == null) incoming else incoming.copy(unterKnotenId = old.unterKnotenId, fotos = old.fotos, anlagen = old.anlagen)

    private fun parseDefectFotoArt(filename: String?): MangelStatusSpeicher.FotoArt =
        runCatching { MangelStatusSpeicher.FotoArt.valueOf(semanticToken(filename, "DEFECT")) }
            .getOrDefault(MangelStatusSpeicher.FotoArt.MANGEL)

    private fun parseAcceptanceBildArt(filename: String?): AbnahmeSpeicher.BildArt =
        runCatching { AbnahmeSpeicher.BildArt.valueOf(semanticToken(filename, "ACCEPTANCE_FOTO")) }
            .getOrDefault(AbnahmeSpeicher.BildArt.SONSTIGES)

    private fun parseAcceptanceAnlageTyp(filename: String?, mimeType: String?): AbnahmeSpeicher.AnlageTyp =
        runCatching { AbnahmeSpeicher.AnlageTyp.valueOf(semanticToken(filename, "ACCEPTANCE_ANLAGE")) }
            .getOrDefault(if (mimeType == "application/pdf") AbnahmeSpeicher.AnlageTyp.PDF else AbnahmeSpeicher.AnlageTyp.BILD)

    private fun semanticToken(filename: String?, prefix: String): String {
        val raw = filename.orEmpty()
        if (!raw.startsWith("${prefix}__", ignoreCase = true)) return ""
        return raw.substringAfter("__", "").substringBefore("__", "").uppercase()
    }

    private fun semanticFilename(prefix: String, token: String, originalName: String): String =
        "${prefix}__${token.uppercase()}__${originalName.ifBlank { "datei" }}"

    private fun parseGps(raw: String): Pair<Double, Double> {
        val values = raw.split(',').map { it.trim() }
        if (values.size >= 2) {
            val lat = values[0].replace(',', '.').toDoubleOrNull()
            val lon = values[1].replace(',', '.').toDoubleOrNull()
            if (lat != null && lon != null) return lat to lon
        }
        return 0.0 to 0.0
    }

    private fun parseKmDisplay(raw: String): Double? {
        val text = raw.trim().removePrefix("KM").trim().substringBefore('–').substringBefore('-').trim()
        val plus = Regex("(\\d+)\\+(\\d+(?:[.,]\\d+)?)").find(text)
        if (plus != null) {
            val km = plus.groupValues[1].toDoubleOrNull()
            val meter = plus.groupValues[2].replace(',', '.').toDoubleOrNull()
            if (km != null && meter != null) return km + meter / 1000.0
        }
        return text.replace(',', '.').toDoubleOrNull()
    }

    private fun defectEpoch(payload: JSONObject): Long {
        val datum = payload.optString("datumIso", "")
        val zeit = payload.optString("zeit", "")
        return runCatching {
            val d = LocalDate.parse(datum)
            val t = LocalTime.parse(zeit.takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) } ?: "00:00")
            d.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    }

    private fun isImageFile(path: String): Boolean = when (File(path).extension.lowercase()) {
        "jpg", "jpeg", "png", "webp" -> true
        else -> false
    }

    private suspend fun serverAttachmentForLocalPhoto(
        parentEntityId: String,
        localFotoId: Long
    ): String? {
        for (attachment in collabRepository.attachments(parentEntityId)) {
            val state = dao.state(attachmentImportKey(attachment.serverId))
            if (state?.longValue == localFotoId && !attachment.isDeleted) {
                return attachment.serverId
            }
        }
        return null
    }

    private suspend fun serverLinkedAttachmentByFilename(
        parentEntityId: String,
        filenames: Set<String>
    ): String? {
        val expected = filenames.filter { it.isNotBlank() }.toSet()
        if (expected.isEmpty()) return null
        for (attachment in collabRepository.attachments(parentEntityId)) {
            if (attachment.isDeleted || attachment.filename !in expected) continue
            val state = dao.state(attachmentImportKey(attachment.serverId))
            if (state?.textValue == "linked") return attachment.serverId
        }
        return null
    }

    private suspend fun rememberAttachmentPhoto(serverAttachmentId: String, localFotoId: Long) {
        dao.upsertState(SyncStateEntity(key = attachmentImportKey(serverAttachmentId), longValue = localFotoId))
    }

    private suspend fun rememberAttachmentLinked(serverAttachmentId: String) {
        dao.upsertState(SyncStateEntity(key = attachmentImportKey(serverAttachmentId), textValue = "linked"))
    }

    private fun attachmentImportKey(serverAttachmentId: String): String = "collab_attachment_import:$serverAttachmentId"

    private fun writeIfDifferent(target: File, content: String): Boolean {
        target.parentFile?.mkdirs()
        if (target.exists() && runCatching { target.readText(Charsets.UTF_8) }.getOrNull() == content) return false
        val temp = File(target.absolutePath + ".sync.tmp")
        temp.writeText(content, Charsets.UTF_8)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return true
    }

    private suspend fun rememberHash(worksiteId: String, type: String, clientKey: String, hash: String) {
        dao.upsertState(SyncStateEntity(key = markerKey(worksiteId, type, clientKey), textValue = hash))
    }

    private fun markerKey(worksiteId: String, type: String, clientKey: String): String =
        "collab_local_hash:$worksiteId:$type:${sha256(clientKey).take(24)}"

    private fun stableHash(status: String?, payload: String): String = sha256((status ?: "") + "\n" + payload)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
        return this
    }

    private fun JSONObject.optNullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
    private fun JSONObject.optNullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key)
}


