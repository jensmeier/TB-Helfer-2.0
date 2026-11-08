package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.util.Base64
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.BtbTeamMediaSync
import de.tbhelfer.datenbank.CollabLocalBridge
import de.tbhelfer.datenbank.CollabSyncRepository
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.Server1AndroidSyncCoordinator
import de.tbhelfer.server.TeamDisplayCache
import de.tbhelfer.server.WorksiteMediaApi
import de.tbhelfer.ui.abnahme.AbnahmeExport
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import de.tbhelfer.ui.baustelle.MaengellisteExport
import de.tbhelfer.ui.baustelle.MangelStatusSpeicher
import de.tbhelfer.ui.baustelle.SyncMangelSpeicher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Fachliche Schlussstufe des merge-sicheren Baustellenexports.
 *
 * Der Server hält Bild + Metadaten als Baustellenbestand; CSV/XLSX werden NICHT als konkurrierende
 * Dateien zwischen Handys synchronisiert. Vor einem Online-Gesamtexport wird zuerst der aktuelle
 * Collaboration-Stand abgeglichen. Danach werden aus dem gemeinsamen Bestand erzeugt:
 *
 * - Bilderliste_gesamt.xlsx (ohne Gesamt-CSV),
 * - Mängel/<Datum>/Mangel_<stabile-ID>/{JSON,PDF,Fotos},
 * - Maengelliste/Maengelliste_Gesamt.{csv,pdf},
 * - Abnahmen/<Datum>/Abnahme_<Nr>_<stabile-ID>/{JSON,PDF,Fotos,Anlagen},
 * - Abnahmeliste/Abnahmeliste_Gesamt.{csv,pdf}.
 *
 * Verantwortliche TBs erhalten über den Server-Medienkatalog Bilder aller TB-Teams derselben
 * Baustelle. Helfer bleiben auf die serverseitig erlaubte Gruppe bzw. ihren Bereich begrenzt.
 */
object GesamtMedienArchiv {

    private data class BinaryPlan(
        val path: String,
        val localFile: File? = null,
        val serverItem: WorksiteMediaApi.MediaItem? = null,
        val bytes: ByteArray? = null
    )

    private data class PhotoMeta(
        val epochMs: Long,
        val art: String,
        val bereich: String,
        val km: String,
        val firma: String,
        val beschreibung: String,
        val gps: String
    )

    private data class TimedRow(
        val epochMs: Long,
        val hash: String,
        val row: GesamtBilderlisteXlsx.Row
    )

    private data class ShiftImageList(
        val path: String,
        val bytes: ByteArray
    )

    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val shortDateFmt = DateTimeFormatter.ofPattern("dd.MM.yy")

    private fun btbWindow(entry: BautagebuchEintrag): Pair<Long, Long> {
        val date = runCatching { LocalDate.parse(entry.datumIso, isoDate) }.getOrDefault(LocalDate.now())
        val start = entry.startUhrzeit?.trim()?.let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() } ?: LocalTime.MIN
        val end = entry.endeUhrzeit?.trim()?.let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() } ?: LocalTime.MAX
        val zone = ZoneId.systemDefault()
        val from = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        val toDate = if (end.isAfter(start)) date else date.plusDays(1)
        val to = toDate.atTime(end).atZone(zone).toInstant().toEpochMilli()
        return from to to
    }

    private fun shiftListFilename(entry: BautagebuchEintrag): String {
        val date = runCatching { LocalDate.parse(entry.datumIso, isoDate).format(shortDateFmt) }.getOrDefault(entry.datumIso)
        val start = entry.startUhrzeit?.trim()?.takeIf { it.isNotBlank() }?.replace(':', '-') ?: entry.schicht.name
        return "${date}_${start}_${BtbDokumentVersionierung.dokumentToken(entry)}_Bilderliste.xlsx"
    }

    /** Run before the raw documents are read; normalisation itself must not change their source. */
    suspend fun vorbereiten(context: Context, localRootKnotenId: Long) {
        val app = context.applicationContext
        val binding = ServiceLocator.worksiteBindingRepository.contextForLocalRoot(localRootKnotenId)
        val token = SecureTokenStore(app).load()
        if (binding != null && !token.isNullOrBlank()) {
            try {
                synchronizeBeforeExport(app, token)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Catalog/download errors below still prevent claiming an incomplete archive is complete.
            }
        }
    }

    suspend fun ergaenzen(
        context: Context,
        normalizedZip: File,
        localRootKnotenId: Long,
        baustelleName: String
    ): File = withContext(Dispatchers.IO) {
        if (!normalizedZip.exists() || !normalizedZip.isFile) return@withContext normalizedZip

        val appContext = context.applicationContext
        val rootNode = ServiceLocator.baustellenKnotenDao.laden(localRootKnotenId)
            ?: return@withContext normalizedZip
        val localBaustelleId = rootNode.altBaustelleId
        val syncContext = runCatching {
            ServiceLocator.worksiteBindingRepository.contextForLocalRoot(localRootKnotenId)
        }.getOrNull()
        val ownResponsible = syncContext?.worksiteServerId
            ?.let { TeamDisplayCache.ownTeam(appContext, it) }
            ?.let { it.leadDisplayName ?: it.leadNameSnapshot }
            ?: syncContext?.leadNameSnapshot.orEmpty()

        val token = SecureTokenStore(appContext).load()

        val mediaApi = WorksiteMediaApi()
        val catalogResult = if (!token.isNullOrBlank() && syncContext != null) {
            mediaApi.loadCatalog(token, syncContext.worksiteServerId)
        } else null
        when (catalogResult) {
            is WorksiteMediaApi.CatalogResult.Error -> error("Gesamtbilderexport nicht vollständig: ${catalogResult.message}")
            WorksiteMediaApi.CatalogResult.SessionInvalid -> error("Gesamtbilderexport: Anmeldung nicht mehr gültig.")
            else -> Unit
        }
        val catalogItems = (catalogResult as? WorksiteMediaApi.CatalogResult.Success)?.items.orEmpty()

        // Gesamtarchiv: auch Fotos anderer BTBs derselben Baustelle laden.
        val localFotos = ServiceLocator.fotoRepository.fotosFuerBaustelleSyncOnce(localBaustelleId)
            .filter { it.baustelleId == localBaustelleId && File(it.dateipfad).isFile }
        val localFileByHash = linkedMapOf<String, File>()
        val localFotoByHash = linkedMapOf<String, FotoEntity>()
        val localHashByFotoId = linkedMapOf<Long, String>()
        localFotos.forEach { foto ->
            val file = File(foto.dateipfad)
            val digest = runCatching { sha256(file.readBytes()) }.getOrNull() ?: return@forEach
            localFileByHash.putIfAbsent(digest, file)
            localFotoByHash.putIfAbsent(digest, foto)
            localHashByFotoId[foto.id] = digest
        }

        // A file hash identifies bytes, not a capture. Reuse the persisted server-photo binding.
        val linkedOriginalHashes = hashSetOf<String>()
        val mediaBridge = BtbTeamMediaSync(appContext)
        // Download paths are persisted attachment identities too. They cover previews that
        // predate the BTB import mapping, without guessing from time, GPS or similar pixels.
        val attachmentByPath = linkedMapOf<String, String>()
        for (parentId in catalogItems.map { it.parentEntityId }.distinct()) {
            for (attachment in ServiceLocator.collabSyncRepository.attachments(parentId)) {
                if (attachment.isDeleted) continue
                val path = attachment.localPath ?: continue
                attachmentByPath[File(path).canonicalPath] = attachment.serverId
            }
        }
        localFotos.forEach { foto ->
            val btbId = BtbFotoZuordnungSpeicher.btbId(appContext, foto.id)
            val serverId = btbId?.let { mediaBridge.serverAttachmentIdForLocalPhoto(foto.id, it) }
                ?: attachmentByPath[File(foto.dateipfad).canonicalPath]
                ?: return@forEach
            val serverItem = catalogItems.firstOrNull {
                it.attachmentId == serverId && it.mimeType?.startsWith("image/") == true
            } ?: return@forEach
            val serverHash = GesamtMedienIdentitaet.serverKey(serverItem.attachmentId, serverItem.sha256)
            val originalHash = localHashByFotoId[foto.id] ?: return@forEach
            linkedOriginalHashes.add(originalHash)
            localHashByFotoId[foto.id] = serverHash
            val candidate = File(foto.dateipfad)
            val previous = localFileByHash[serverHash]
            if (previous == null || (previous.name.endsWith("_mobile_preview.jpg") &&
                        !candidate.name.endsWith("_mobile_preview.jpg"))) {
                localFileByHash[serverHash] = candidate
                localFotoByHash[serverHash] = foto
            }
        }

        val localMaengel = MaengellisteExport.ladeItems(appContext, localBaustelleId, baustelleName)
        val syncMaengel = SyncMangelSpeicher.alle(appContext, localBaustelleId)
        val maengel = mergeMaengel(localMaengel, syncMaengel, baustelleName)
        val abnahmen = AbnahmeSpeicher.alle(appContext, localBaustelleId)

        val outTemp = File(normalizedZip.absolutePath + ".media.tmp")
        ZipFile(normalizedZip).use { source ->
            val entries = source.entries().asSequence().filterNot { it.isDirectory }.toList()
            val archiveRoot = archiveRoot(entries.map { cleanPath(it.name) }, baustelleName)

            val plans = mutableListOf<BinaryPlan>()
            val mangelPhotoPaths = linkedMapOf<Pair<String, Long>, String>()
            val acceptancePhotoPaths = linkedMapOf<Pair<String, Long>, String>()
            val mangelPathByHash = linkedMapOf<String, String>()
            val mangelFolderByServerId = linkedMapOf<String, String>()
            buildMangelPlans(
                context = appContext,
                archiveRoot = archiveRoot,
                baustelleName = baustelleName,
                maengel = maengel,
                syncMaengel = syncMaengel,
                plans = plans,
                pathByHash = mangelPathByHash,
                folderByServerId = mangelFolderByServerId,
                photoPaths = mangelPhotoPaths
            )

            val acceptancePathByHash = linkedMapOf<String, String>()
            buildAcceptancePlans(
                context = appContext,
                archiveRoot = archiveRoot,
                abnahmen = abnahmen,
                plans = plans,
                pathByHash = acceptancePathByHash,
                photoPaths = acceptancePhotoPaths
            )

            val serverRows = rowsFromServerCatalog(
                items = catalogItems,
                archiveRoot = archiveRoot,
                baustelleName = baustelleName,
                localFileByHash = localFileByHash,
                localFotoByHash = localFotoByHash,
                mangelPathByHash = mangelPathByHash,
                mangelFolderByServerId = mangelFolderByServerId,
                acceptancePathByHash = acceptancePathByHash,
                plans = plans
            )
            val serverHashes = serverRows.mapTo(hashSetOf()) { it.hash }
            val localRows = rowsFromLocal(
                localFotos = localFotos,
                archiveRoot = archiveRoot,
                baustelleName = baustelleName,
                responsible = ownResponsible,
                mangelPathByHash = mangelPathByHash,
                acceptancePathByHash = acceptancePathByHash,
                excludedHashes = serverHashes + linkedOriginalHashes,
                plans = plans
            )
            val orderedTimedRows = (serverRows + localRows)
                .sortedWith(compareBy<TimedRow> { it.epochMs }.thenBy { it.hash })
            val mediaRows = orderedTimedRows
                .mapIndexed { index, item -> item.row.copy(nr = index + 1) }
            val finalRowByHash = orderedTimedRows
                .mapIndexed { index, item -> item.hash to item.row.copy(nr = index + 1) }
                .toMap()

            // 200056: Jede reale Schicht erhält eine eigene XLSX. Erst in dieser letzten
            // Exportstufe stehen die endgültigen merge-sicheren Bildpfade fest, deshalb
            // werden auch die anklickbaren Links hier erzeugt.
            val shiftLists = runCatching {
                BautagebuchSpeicher.verlaufFuerBaustelle(appContext, localBaustelleId, baustelleName)
            }.getOrDefault(emptyList()).map { v ->
                val entry = v.entry
                val (from, to) = btbWindow(entry)
                val photos = BtbFotoBtbFilter.fotosFuerBtb(
                    context = appContext,
                    entry = entry,
                    alleFotos = localFotos,
                    legacyFromEpochMs = from,
                    legacyToEpochMs = to
                ).sortedBy { it.erstelltAmEpochMs }

                val rows = photos.mapNotNull { foto ->
                    val hash = localHashByFotoId[foto.id] ?: return@mapNotNull null
                    val finalRow = finalRowByHash[hash] ?: return@mapNotNull null
                    finalRow.copy(
                        kurzbeschreibung = foto.kurztext,
                        langtext = foto.notiz.orEmpty(),
                        relativePath = "../../${finalRow.relativePath}"
                    )
                }.let(GesamtBilderlisteXlsx::eindeutigNummeriert)

                ShiftImageList(
                    path = "$archiveRoot/BTB/${entry.datumIso}/${shiftListFilename(entry)}",
                    bytes = GesamtBilderlisteXlsx.bytes(rows, sheetName = "Bilder ${entry.datumIso}")
                )
            }

            val abnahmeCsv = AbnahmeExport.csvText(abnahmen)
            val abnahmePdf = AbnahmeExport.listenPdfBytes(appContext, baustelleName, abnahmen, "Gesamt")

            ZipOutputStream(outTemp.outputStream().buffered(64 * 1024)).use { out ->
                val written = linkedSetOf<String>()

                fun writeBytes(path: String, bytes: ByteArray) {
                    val key = path.lowercase(Locale.ROOT)
                    if (!written.add(key)) return
                    out.putNextEntry(ZipEntry(path))
                    out.write(bytes)
                    out.closeEntry()
                }

                fun writeDirectory(path: String) {
                    val normalized = if (path.endsWith('/')) path else "$path/"
                    val key = normalized.lowercase(Locale.ROOT)
                    if (!written.add(key)) return
                    out.putNextEntry(ZipEntry(normalized))
                    out.closeEntry()
                }

                // Bilder, Mängel und Abnahmen werden unten aus dem aktuellen Fachbestand neu
                // aufgebaut. So bleiben keine alten, inzwischen gelöschten Bilder im Endarchiv.
                entries.forEach { entry ->
                    val path = cleanPath(entry.name)
                    if (isRegeneratedPath(path, archiveRoot)) return@forEach
                    val key = path.lowercase(Locale.ROOT)
                    if (!written.add(key)) return@forEach
                    out.putNextEntry(ZipEntry(path).apply { time = entry.time })
                    source.getInputStream(entry).use { it.copyTo(out, 64 * 1024) }
                    out.closeEntry()
                }

                writeDirectory("$archiveRoot/Bilder/")
                writeDirectory("$archiveRoot/Mängel/")
                writeDirectory("$archiveRoot/Abnahmen/")

                plans.distinctBy { it.path.lowercase(Locale.ROOT) }.forEach planLoop@ { plan ->
                    if (written.contains(plan.path.lowercase(Locale.ROOT))) return@planLoop
                    val bytes = when {
                        plan.bytes != null -> plan.bytes
                        plan.localFile?.isFile == true -> runCatching { plan.localFile.readBytes() }.getOrNull()
                        plan.serverItem != null && !token.isNullOrBlank() -> when (val dl = mediaApi.download(token, plan.serverItem.attachmentId)) {
                            is WorksiteMediaApi.DownloadResult.Success -> dl.media.bytes
                            else -> null
                        }
                        else -> null
                    } ?: error("Bildexport unvollständig: ${plan.path.substringAfterLast('/')}. Erneut synchronisieren und exportieren.")
                    writeBytes(plan.path, bytes)
                }

                val csvBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                shiftLists.forEach { list -> writeBytes(list.path, list.bytes) }
                writeBytes(
                    "$archiveRoot/Bilderliste_gesamt.xlsx",
                    GesamtBilderlisteXlsx.bytes(mediaRows)
                )
                writeBytes("$archiveRoot/Maengelliste/Maengelliste_Gesamt.xlsx",
                    FachlistenXlsx.maengel(appContext, maengel) { key, id ->
                        mangelPhotoPaths[key to id]?.let { "../" + it.removePrefix("$archiveRoot/") }
                    })
                writeBytes("$archiveRoot/Abnahmeliste/Abnahmeliste_Gesamt.xlsx",
                    FachlistenXlsx.abnahmen(abnahmen) { key, id ->
                        acceptancePhotoPaths[key to id]?.let { "../" + it.removePrefix("$archiveRoot/") }
                    })
                writeBytes(
                    "$archiveRoot/Maengelliste/Maengelliste_Gesamt.csv",
                    csvBom + MaengellisteExport.csvText(appContext, maengel).toByteArray(Charsets.UTF_8)
                )
                writeBytes(
                    "$archiveRoot/Maengelliste/Maengelliste_Gesamt.pdf",
                    MaengellisteExport.pdfBytes(appContext, baustelleName, maengel, "Gesamt")
                )
                writeBytes(
                    "$archiveRoot/Abnahmeliste/Abnahmeliste_Gesamt.csv",
                    csvBom + abnahmeCsv.toByteArray(Charsets.UTF_8)
                )
                writeBytes(
                    "$archiveRoot/Abnahmeliste/Abnahmeliste_Gesamt.pdf",
                    abnahmePdf
                )
            }
        }

        if (normalizedZip.exists()) normalizedZip.delete()
        if (!outTemp.renameTo(normalizedZip)) {
            outTemp.copyTo(normalizedZip, overwrite = true)
            outTemp.delete()
        }
        normalizedZip
    }

    /** Best-effort Sofortabgleich speziell vor einem fertigen Baustellen-Gesamtexport. */
    private suspend fun synchronizeBeforeExport(context: Context, token: String) {
        when (
            val modern = Server1AndroidSyncCoordinator(
                context.applicationContext
            ).synchronize(token)
        ) {
            is Server1AndroidSyncCoordinator.Result.Success -> return
            Server1AndroidSyncCoordinator.Result.SessionInvalid -> return
            is Server1AndroidSyncCoordinator.Result.Error -> return
            Server1AndroidSyncCoordinator.Result.Unsupported -> Unit
        }

        // Ausschließlich alter 0.7.x-Fallback.
        val collab = ServiceLocator.collabSyncRepository
        val bridge = ServiceLocator.collabLocalBridge
        val btbMedia = BtbTeamMediaSync(context)

        when (collab.synchronizeAll(token)) {
            CollabSyncRepository.FullSyncResult.SessionInvalid -> return
            is CollabSyncRepository.FullSyncResult.Error -> return
            is CollabSyncRepository.FullSyncResult.Success -> Unit
        }
        bridge.applyServerStateToLocal()

        when (btbMedia.synchronize(token)) {
            BtbTeamMediaSync.Result.SessionInvalid -> return
            is BtbTeamMediaSync.Result.Error -> return
            else -> Unit
        }

        val exported = bridge.exportLocalChanges()
        if (exported.queuedOrUpdated > 0) {
            when (collab.synchronizeAll(token)) {
                CollabSyncRepository.FullSyncResult.SessionInvalid -> return
                is CollabSyncRepository.FullSyncResult.Error -> return
                is CollabSyncRepository.FullSyncResult.Success -> Unit
            }
            bridge.applyServerStateToLocal()
        }

        when (btbMedia.synchronize(token)) {
            BtbTeamMediaSync.Result.SessionInvalid -> return
            is BtbTeamMediaSync.Result.Error -> return
            else -> Unit
        }

        when (bridge.uploadLocalAttachments(token)) {
            CollabLocalBridge.AttachmentUploadResult.SessionInvalid -> return
            is CollabLocalBridge.AttachmentUploadResult.Error -> return
            else -> Unit
        }

        when (collab.synchronizeAll(token)) {
            CollabSyncRepository.FullSyncResult.SessionInvalid -> return
            is CollabSyncRepository.FullSyncResult.Error -> return
            is CollabSyncRepository.FullSyncResult.Success -> Unit
        }
        bridge.applyServerStateToLocal()
        btbMedia.synchronize(token)
    }

    private fun buildMangelPlans(
        context: Context,
        archiveRoot: String,
        baustelleName: String,
        maengel: List<MaengellisteExport.MangelItem>,
        syncMaengel: List<SyncMangelSpeicher.Eintrag>,
        plans: MutableList<BinaryPlan>,
        pathByHash: MutableMap<String, String>,
        folderByServerId: MutableMap<String, String>,
        photoPaths: MutableMap<Pair<String, Long>, String>
    ) {
        val syncByKey = syncMaengel.associateBy { it.mangelKey }
        maengel.forEach { item ->
            val sync = syncByKey[item.key]
            val stableId = sync?.serverEntityId?.takeIf { it.isNotBlank() }
                ?: sha256(item.key.toByteArray(Charsets.UTF_8)).take(16)
            val datum = item.datumIso.takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: "Ohne_Datum"
            val token = safeToken(stableId).take(12)
            val folder = "$archiveRoot/Mängel/$datum/Mangel_$token"
            sync?.serverEntityId?.let { folderByServerId[it] = folder }

            val fotoTargets = mutableListOf<String>()
            item.fotos.forEach fotoLoop@ { mf ->
                val file = File(mf.foto.dateipfad)
                if (!file.isFile) return@fotoLoop
                val digest = runCatching { sha256(file.readBytes()) }.getOrNull() ?: return@fotoLoop
                val target = "$folder/Fotos/${safeToken(mf.art.name)}_${digest.take(12)}.${extension(file.name, null)}"
                pathByHash[digest] = target
                photoPaths[item.key to mf.foto.id] = target
                fotoTargets += target.removePrefix("$archiveRoot/")
                plans += BinaryPlan(path = target, localFile = file)
            }

            val status = MangelStatusSpeicher.laden(context, item.key)
            val json = JSONObject()
                .put("mangelKey", item.key)
                .put("serverEntityId", sync?.serverEntityId ?: JSONObject.NULL)
                .put("datumIso", item.datumIso)
                .put("zeit", item.zeit)
                .put("baustelle", item.baustelle)
                .put("bereich", item.bereich)
                .put("km", item.km)
                .put("firma", item.firma)
                .put("beschreibung", item.beschreibung)
                .put("gps", item.gps)
                .put("status", status.status.name)
                .put("kommentar", status.kommentar)
                .put("erledigtAmEpochMs", status.erledigtAmEpochMs ?: JSONObject.NULL)
                .put("ersteller", sync?.changedByName ?: JSONObject.NULL)
                .put("fotos", JSONArray(fotoTargets))
                .toString(2)
                .toByteArray(Charsets.UTF_8)
            plans += BinaryPlan(path = "$folder/Mangel_$token.json", bytes = json)
            plans += BinaryPlan(
                path = "$folder/Mangel_$token.pdf",
                bytes = MaengellisteExport.pdfBytes(context, baustelleName, listOf(item), "Einzel")
            )
        }
    }

    private suspend fun buildAcceptancePlans(
        context: Context,
        archiveRoot: String,
        abnahmen: List<AbnahmeSpeicher.Abnahme>,
        plans: MutableList<BinaryPlan>,
        pathByHash: MutableMap<String, String>,
        photoPaths: MutableMap<Pair<String, Long>, String>
    ) {
        for (a in abnahmen) {
            val token = safeToken(a.id).take(8)
            val number = a.nummer.toString().padStart(3, '0')
            val folder = "$archiveRoot/Abnahmen/${a.startDatumIso}/Abnahme_${number}_$token"
            val fotos = ServiceLocator.fotoRepository.fotosMitIds(a.fotos.map { it.fotoId }).associateBy { it.id }
            val photoJson = JSONArray()

            a.fotos.forEach refLoop@ { ref ->
                val foto = fotos[ref.fotoId] ?: return@refLoop
                val file = File(foto.dateipfad)
                if (!file.isFile) return@refLoop
                val digest = runCatching { sha256(file.readBytes()) }.getOrNull() ?: return@refLoop
                val target = "$folder/Fotos/${safeToken(ref.bildArt.name)}_${digest.take(12)}.${extension(file.name, null)}"
                pathByHash[digest] = target
                plans += BinaryPlan(path = target, localFile = file)
                photoPaths[a.id to ref.fotoId] = target
                photoJson.put(
                    JSONObject()
                        .put("art", ref.bildArt.name)
                        .put("bemerkung", ref.bemerkung)
                        .put("datei", target.removePrefix("$archiveRoot/"))
                )
            }

            val attachmentJson = JSONArray()
            a.anlagen.forEach attachmentLoop@ { anlage ->
                val file = when {
                    anlage.typ == AbnahmeSpeicher.AnlageTyp.BILD &&
                        !anlage.ausschnittPfad.isNullOrBlank() && File(anlage.ausschnittPfad).isFile -> File(anlage.ausschnittPfad)
                    else -> File(anlage.originalPfad)
                }
                if (!file.isFile) return@attachmentLoop
                val digest = runCatching { sha256(file.readBytes()) }.getOrNull() ?: return@attachmentLoop
                val target = "$folder/Anlagen/${safeToken(anlage.id).take(12)}_${digest.take(12)}.${extensionAny(file.name)}"
                plans += BinaryPlan(path = target, localFile = file)
                if (anlage.typ == AbnahmeSpeicher.AnlageTyp.BILD && isImageFile(file)) {
                    pathByHash[digest] = target
                }
                attachmentJson.put(
                    JSONObject()
                        .put("typ", anlage.typ.name)
                        .put("bezeichnung", anlage.bezeichnung)
                        .put("bemerkung", anlage.bemerkung)
                        .put("datei", target.removePrefix("$archiveRoot/"))
                )
            }

            val json = acceptanceJson(a, photoJson, attachmentJson).toString(2).toByteArray(Charsets.UTF_8)
            plans += BinaryPlan(path = "$folder/Abnahme_${number}_$token.json", bytes = json)
            val pdf = runCatching { AbnahmeExport.erzeugeEinzelPdf(context, a) }.getOrNull()
            if (pdf?.isFile == true) {
                plans += BinaryPlan(path = "$folder/Abnahme_${number}_$token.pdf", localFile = pdf)
            }
        }
    }

    private fun acceptanceJson(a: AbnahmeSpeicher.Abnahme, photos: JSONArray, attachments: JSONArray): JSONObject =
        JSONObject()
            .put("id", a.id)
            .put("nummer", a.nummer)
            .put("baustelleName", a.baustelleName)
            .put("unterKnotenName", a.unterKnotenName)
            .put("bezeichnung", a.bezeichnung)
            .put("objekt", a.objekt)
            .put("firma", a.firma)
            .put("weitereFirmen", a.weitereFirmen)
            .put("abnehmender", a.abnehmender)
            .put("anwesende", a.anwesende)
            .putNullable("kmVon", a.kmVon)
            .putNullable("kmBis", a.kmBis)
            .put("kommentar", a.kommentar)
            .put("restpunkte", a.restpunkte)
            .put("ergebnis", a.ergebnis.name)
            .put("startEpochMs", a.startEpochMs)
            .putNullable("abgeschlossenAmEpochMs", a.abgeschlossenAmEpochMs)
            .put("fotos", photos)
            .put("anlagen", attachments)

    private fun mergeMaengel(
        local: List<MaengellisteExport.MangelItem>,
        sync: List<SyncMangelSpeicher.Eintrag>,
        baustelleName: String
    ): List<MaengellisteExport.MangelItem> {
        val result = linkedMapOf<String, MaengellisteExport.MangelItem>()
        local.forEach { result[it.key] = it }
        sync.forEach { s ->
            val existing = result[s.mangelKey]
            if (existing == null) {
                result[s.mangelKey] = MaengellisteExport.MangelItem(
                    key = s.mangelKey,
                    datumIso = s.datumIso,
                    zeit = s.zeit,
                    baustelle = baustelleName,
                    bereich = s.bereich,
                    km = s.km,
                    firma = s.firma,
                    beschreibung = s.beschreibung,
                    gps = s.gps,
                    fotos = emptyList(),
                    btbBildNr = s.btbBildNr
                )
            } else {
                result[s.mangelKey] = existing.copy(
                    datumIso = existing.datumIso.ifBlank { s.datumIso },
                    zeit = existing.zeit.ifBlank { s.zeit },
                    bereich = existing.bereich.takeUnless { it.isBlank() || it == "Hauptbaustelle" } ?: s.bereich,
                    km = existing.km.takeUnless { it.isBlank() || it == "-" } ?: s.km,
                    firma = existing.firma.takeUnless { it.isBlank() || it == "-" } ?: s.firma,
                    beschreibung = existing.beschreibung.takeUnless { it.isBlank() || it == "-" } ?: s.beschreibung,
                    gps = existing.gps.takeUnless { it.isBlank() || it == "-" } ?: s.gps,
                    btbBildNr = existing.btbBildNr.ifBlank { s.btbBildNr }
                )
            }
        }
        return result.values.sortedWith(
            compareBy<MaengellisteExport.MangelItem> { it.datumIso }
                .thenBy { it.zeit }
                .thenBy { it.beschreibung }
        )
    }

    private fun rowsFromServerCatalog(
        items: List<WorksiteMediaApi.MediaItem>,
        archiveRoot: String,
        baustelleName: String,
        localFileByHash: Map<String, File>,
        localFotoByHash: Map<String, FotoEntity>,
        mangelPathByHash: Map<String, String>,
        mangelFolderByServerId: Map<String, String>,
        acceptancePathByHash: Map<String, String>,
        plans: MutableList<BinaryPlan>
    ): List<TimedRow> {
        val unique = items
            .filter { it.mimeType?.startsWith("image/") == true }
            .groupBy { item ->
                item.sha256?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: "id:${item.attachmentId}"
            }
            .values
            .map { candidates ->
                candidates.sortedWith(
                    compareByDescending<WorksiteMediaApi.MediaItem> { semanticPriority(it) }
                        .thenByDescending { it.createdAt.orEmpty() }
                ).first()
            }

        return unique.map { item ->
            val hash = GesamtMedienIdentitaet.serverKey(item.attachmentId, item.sha256)
            val meta = mediaMeta(item)
            val localFoto = localFotoByHash[hash]
            val dateTime = Instant.ofEpochMilli(meta.epochMs).atZone(ZoneId.systemDefault())
            val date = dateTime?.toLocalDate()?.format(isoDate) ?: "ohne_Aufnahmezeit"
            val target = when (item.entityType.uppercase(Locale.ROOT)) {
                "DEFECT" -> mangelPathByHash[hash]
                    ?: mangelFolderByServerId[item.parentEntityId]?.let { folder ->
                        "$folder/Fotos/${safeToken(defectArt(item.filename))}_${hash.take(12)}.${extension(item.filename, item.mimeType)}"
                    }
                    ?: "$archiveRoot/Mängel/$date/Mangel_${safeToken(item.parentEntityId).take(12)}/Fotos/${safeToken(defectArt(item.filename))}_${hash.take(12)}.${extension(item.filename, item.mimeType)}"
                "ACCEPTANCE" -> acceptancePathByHash[hash]
                    ?: "$archiveRoot/Abnahmen/$date/Abnahme_${safeToken(item.parentEntityId).take(8)}/${acceptanceMediaFolder(item.filename)}/Abnahme_${hash.take(12)}.${extension(item.filename, item.mimeType)}"
                else -> "$archiveRoot/Bilder/$date/${safeToken(meta.art)}_${safeToken(localFoto?.kurztext ?: meta.beschreibung).take(24)}_${hash.take(12)}.${extension(item.filename, item.mimeType)}"
            }

            if (plans.none { it.path.equals(target, ignoreCase = true) }) {
                plans += BinaryPlan(
                    path = target,
                    localFile = localFileByHash[hash],
                    serverItem = if (localFileByHash[hash] == null) item else null
                )
            }

            TimedRow(
                epochMs = meta.epochMs,
                hash = hash,
                row = GesamtBilderlisteXlsx.Row(
                    nr = 0,
                    datum = date,
                    uhrzeit = dateTime?.toLocalTime()?.format(timeFmt).orEmpty(),
                    ersteller = item.uploadedByName.orEmpty(),
                    verantwortlicherTb = item.responsibleTb.orEmpty(),
                    art = meta.art,
                    baustelle = baustelleName,
                    bereich = meta.bereich,
                    km = meta.km,
                    firma = localFoto?.firma ?: meta.firma,
                    kurzbeschreibung = localFoto?.kurztext ?: meta.beschreibung,
                    langtext = localFoto?.notiz.orEmpty(),
                    status = displayStatus(item),
                    bilddatei = target.substringAfterLast('/'),
                    gps = meta.gps,
                    relativePath = target.removePrefix("$archiveRoot/")
                )
            )
        }
    }

    private fun rowsFromLocal(
        localFotos: List<FotoEntity>,
        archiveRoot: String,
        baustelleName: String,
        responsible: String,
        mangelPathByHash: Map<String, String>,
        acceptancePathByHash: Map<String, String>,
        excludedHashes: Set<String>,
        plans: MutableList<BinaryPlan>
    ): List<TimedRow> {
        val result = mutableListOf<TimedRow>()
        val seen = hashSetOf<String>()
        localFotos.sortedBy { it.erstelltAmEpochMs }.forEach { foto ->
            val file = File(foto.dateipfad)
            if (!file.isFile) return@forEach
            val hash = runCatching { sha256(file.readBytes()) }.getOrNull() ?: return@forEach
            if (hash in excludedHashes || !seen.add(hash)) return@forEach
            val dateTime = foto.aufnahmeEpochEffektiv?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
            val date = dateTime?.toLocalDate()?.format(isoDate) ?: "ohne_Aufnahmezeit"
            val target = mangelPathByHash[hash]
                ?: acceptancePathByHash[hash]
                ?: "$archiveRoot/Bilder/$date/${safeToken(bildtypLabel(foto.bildtyp))}_${safeToken(foto.kurztext).take(24)}_${hash.take(12)}.${extension(file.name, null)}"

            if (plans.none { it.path.equals(target, ignoreCase = true) }) {
                plans += BinaryPlan(path = target, localFile = file)
            }
            result += TimedRow(
                epochMs = foto.erstelltAmEpochMs,
                hash = hash,
                row = GesamtBilderlisteXlsx.Row(
                    nr = 0,
                    datum = date,
                    uhrzeit = dateTime?.toLocalTime()?.format(timeFmt).orEmpty(),
                    ersteller = "Nicht erfasst",
                    verantwortlicherTb = responsible,
                    art = when {
                        hash in mangelPathByHash -> "Mangel"
                        hash in acceptancePathByHash -> "Abnahme"
                        else -> bildtypLabel(foto.bildtyp)
                    },
                    baustelle = baustelleName,
                    bereich = foto.objektBereich.orEmpty(),
                    km = foto.km?.let { "KM ${de.tbhelfer.util.KmAnzeige.wert(it)}" }.orEmpty(),
                    firma = foto.firma,
                    kurzbeschreibung = foto.kurztext,
                    langtext = foto.notiz.orEmpty(),
                    status = "",
                    bilddatei = target.substringAfterLast('/'),
                    gps = foto.gpsAnzeige.takeUnless { it == "–" }.orEmpty(),
                    relativePath = target.removePrefix("$archiveRoot/")
                )
            )
        }
        return result
    }

    private fun semanticPriority(item: WorksiteMediaApi.MediaItem): Int = when (item.entityType.uppercase(Locale.ROOT)) {
        "DEFECT" -> 30 + when (defectArt(item.filename)) { "ERLEDIGT" -> 3; "IN_ARBEIT" -> 2; else -> 1 }
        "ACCEPTANCE" -> 20
        else -> 10
    }

    private fun mediaMeta(item: WorksiteMediaApi.MediaItem): PhotoMeta = when (item.entityType.uppercase(Locale.ROOT)) {
        "DEFECT" -> {
            val p = json(item.parentPayloadJson)
            val epoch = epochFromDateTime(p.optString("datumIso"), p.optString("zeit"))
                ?: parseServerTime(item.createdAt)
            PhotoMeta(
                epochMs = epoch,
                art = when (defectArt(item.filename)) {
                    "ERLEDIGT" -> "Mangel – erledigt"
                    "IN_ARBEIT" -> "Mangel – in Arbeit"
                    else -> "Mangel"
                },
                bereich = p.optString("bereich", "Hauptbaustelle"),
                km = p.optString("km", ""),
                firma = p.optString("firma", ""),
                beschreibung = p.optString("beschreibung", "Mangel"),
                gps = p.optString("gps", "")
            )
        }
        "ACCEPTANCE" -> {
            val p = json(item.parentPayloadJson)
            val epoch = p.optLong("startEpochMs", 0L).takeIf { it > 0 } ?: parseServerTime(item.createdAt)
            val token = semanticToken(item.filename, "ACCEPTANCE_FOTO")
            val isAnlage = item.filename.orEmpty().startsWith("ACCEPTANCE_ANLAGE__", ignoreCase = true)
            PhotoMeta(
                epochMs = epoch,
                art = when {
                    isAnlage -> "Abnahme – Anlage"
                    token.isBlank() -> "Abnahme"
                    else -> "Abnahme – ${humanToken(token)}"
                },
                bereich = p.optString("unterKnotenName", ""),
                km = p.optNullableDouble("kmVon")?.let { "KM ${de.tbhelfer.util.KmAnzeige.wert(it)}" }.orEmpty(),
                firma = p.optString("firma", ""),
                beschreibung = p.optString("bezeichnung").ifBlank { p.optString("objekt", "Abnahme") },
                gps = ""
            )
        }
        else -> structuredBtbPhoto(item) ?: decodeBtbPhoto(item.filename) ?: PhotoMeta(
            epochMs = parseServerTime(item.createdAt),
            art = "Baudokumentation",
            bereich = "",
            km = "",
            firma = "",
            beschreibung = item.filename.orEmpty(),
            gps = ""
        )
    }

    private fun structuredBtbPhoto(item: WorksiteMediaApi.MediaItem): PhotoMeta? {
        val m = json(item.photoMetadataJson)
        if (m.length() == 0) return null
        val lat = m.optNullableDouble("lat")
        val lon = m.optNullableDouble("lon")
        return PhotoMeta(
            epochMs = item.capturedAt?.let { parseServerTime(it) } ?: m.optLong("epoch", 0L).takeIf { it > 0L } ?: parseServerTime(item.createdAt),
            art = m.optString("typ", "FOTODOKUMENTATION"),
            bereich = m.optString("ort").ifBlank { m.optString("objektBereich").ifBlank { m.optString("worksiteNodeKey") } },
            km = m.optNullableDouble("km")?.let { "KM ${de.tbhelfer.util.KmAnzeige.wert(it)}" }.orEmpty(),
            firma = m.optString("firma"),
            beschreibung = m.optString("kurztext").ifBlank { m.optString("notiz") },
            gps = if (lat != null && lon != null) "$lat, $lon" else "")
    }

    private fun decodeBtbPhoto(filename: String?): PhotoMeta? {
        val raw = filename.orEmpty()
        if (!raw.startsWith("BTBP1__")) return null
        val encoded = raw.removePrefix("BTBP1__").substringBefore("__")
        if (encoded.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val epoch = input.readLong()
                val lat = input.readFloat().toDouble()
                val lon = input.readFloat().toDouble()
                input.readFloat() // altitude
                val km = input.readFloat()
                input.readFloat() // axis distance
                input.readShort() // km suffix
                input.readUnsignedByte() // flags
                val bildtypIndex = input.readUnsignedByte()
                input.readUnsignedByte() // indikator
                val firma = readTinyUtf(input)
                val kurztext = readTinyUtf(input)
                skipTinyUtf(input) // objektKennung
                val objektBereich = readTinyUtf(input)
                val typ = Bildtyp.values().getOrNull(bildtypIndex) ?: Bildtyp.FOTODOKUMENTATION
                PhotoMeta(
                    epochMs = epoch,
                    art = bildtypLabel(typ),
                    bereich = objektBereich,
                    km = if (km.isNaN()) "" else "KM ${de.tbhelfer.util.KmAnzeige.wert(km.toDouble())}",
                    firma = firma,
                    beschreibung = kurztext.ifBlank { "Bild" },
                    gps = if (lat == 0.0 && lon == 0.0) "" else "%.6f, %.6f".format(Locale.US, lat, lon)
                )
            }
        }.getOrNull()
    }

    private fun readTinyUtf(input: DataInputStream): String {
        val size = input.readUnsignedByte()
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun skipTinyUtf(input: DataInputStream) {
        val size = input.readUnsignedByte()
        if (size > 0) input.skipBytes(size)
    }

    private fun displayStatus(item: WorksiteMediaApi.MediaItem): String = when (item.entityType.uppercase(Locale.ROOT)) {
        "DEFECT" -> when (item.parentStatus?.uppercase(Locale.ROOT)) {
            "ERLEDIGT" -> "Erledigt"
            "IN_ARBEIT" -> "In Arbeit"
            "ABGELEHNT" -> "Abgelehnt"
            else -> "Offen"
        }
        "ACCEPTANCE" -> humanToken(item.parentStatus.orEmpty())
        else -> ""
    }

    private fun defectArt(filename: String?): String = semanticToken(filename, "DEFECT").ifBlank { "MANGEL" }

    private fun semanticToken(filename: String?, prefix: String): String {
        val raw = filename.orEmpty()
        if (!raw.startsWith("${prefix}__", ignoreCase = true)) return ""
        return raw.substringAfter("__", "").substringBefore("__", "").uppercase(Locale.ROOT)
    }

    private fun acceptanceMediaFolder(filename: String?): String =
        if (filename.orEmpty().startsWith("ACCEPTANCE_ANLAGE__", ignoreCase = true)) "Anlagen" else "Fotos"

    private fun humanToken(value: String): String = value
        .replace('_', ' ')
        .lowercase(Locale.GERMANY)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString() }

    private fun bildtypLabel(typ: Bildtyp): String = when (typ) {
        Bildtyp.FOTODOKUMENTATION -> "Baudokumentation"
        Bildtyp.MAENGEL_SCHAEDEN -> "Mangel"
        Bildtyp.ABNAHMEN -> "Abnahme"
        Bildtyp.EINWEISUNG_UNTERWEISUNG -> "Einweisung/Unterweisung"
        Bildtyp.BESONDERHEITEN -> "Besonderheit"
    }

    private fun epochFromDateTime(date: String?, time: String?): Long? {
        val d = date?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val t = time?.takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) } ?: "00:00"
            LocalDateTime.parse("$d ${t.padStart(5, '0')}", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun parseServerTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrElse {
            runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
                .getOrDefault(System.currentTimeMillis())
        }
    }

    private fun isRegeneratedPath(path: String, archiveRoot: String): Boolean {
        if (path.equals("$archiveRoot/Bilderliste_gesamt.csv", ignoreCase = true)) return true
        if (path.equals("$archiveRoot/Bilderliste_gesamt.xlsx", ignoreCase = true)) return true
        if (path.startsWith("$archiveRoot/Bilder/", ignoreCase = true)) return true
        if (path.contains("/BTB/", ignoreCase = true) && path.endsWith("_Bilderliste.xlsx", ignoreCase = true)) return true
        if (path.startsWith("$archiveRoot/Mängel/", ignoreCase = true)) return true
        if (path.startsWith("$archiveRoot/Abnahmen/", ignoreCase = true)) return true
        if (path.equals("$archiveRoot/Maengelliste/Maengelliste_Gesamt.xlsx", ignoreCase = true)) return true
        if (path.equals("$archiveRoot/Abnahmeliste/Abnahmeliste_Gesamt.xlsx", ignoreCase = true)) return true
        if (path.equals("$archiveRoot/Maengelliste/Maengelliste_Gesamt.csv", ignoreCase = true)) return true
        if (path.equals("$archiveRoot/Maengelliste/Maengelliste_Gesamt.pdf", ignoreCase = true)) return true
        if (path.equals("$archiveRoot/Abnahmeliste/Abnahmeliste_Gesamt.csv", ignoreCase = true)) return true
        if (path.equals("$archiveRoot/Abnahmeliste/Abnahmeliste_Gesamt.pdf", ignoreCase = true)) return true
        return false
    }

    private fun archiveRoot(paths: List<String>, baustelleName: String): String {
        val fromZip = paths.firstOrNull { it.startsWith("TB-Helfer/") }
            ?.split('/')
            ?.takeIf { it.size >= 3 }
            ?.getOrNull(1)
        return "TB-Helfer/${fromZip ?: safeToken(baustelleName).ifBlank { "Baustelle" }}"
    }

    private fun cleanPath(path: String): String = path.replace('\\', '/').trimStart('/')

    private fun isImageFile(file: File): Boolean = file.extension.lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp")

    private fun extension(filename: String?, mimeType: String?): String {
        val ext = filename.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in setOf("jpg", "jpeg", "png", "webp")) return if (ext == "jpeg") "jpg" else ext
        return when (mimeType?.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    private fun extensionAny(filename: String): String = filename
        .substringAfterLast('.', "bin")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "")
        .ifBlank { "bin" }
        .take(8)

    private fun safeToken(value: String): String = value
        .trim()
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_', '.', ' ')
        .take(100)
        .ifBlank { "Datei" }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun json(raw: String): JSONObject = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key) }.getOrNull()
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
        return this
    }
}




