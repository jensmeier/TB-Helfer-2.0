package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.util.Base64
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.server.SecureTokenStore
import de.tbhelfer.server.TeamDisplayCache
import de.tbhelfer.server.WorksiteMediaApi
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
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Zweite Stufe nach [MergeSafeBaustellenExport].
 *
 * - erzeugt Bilderliste_gesamt.xlsx + CSV aus dem serverseitigen Baustellen-Medienkatalog,
 * - nimmt fuer einen verantwortlichen TB Bilder ALLER TB-Teams derselben Baustelle auf,
 * - behaelt fuer Helfer die serverseitige Bereichsbegrenzung,
 * - erzeugt den strukturierten Ordner Mängel analog zu Abnahmen,
 * - ersetzt die Mängelliste_Gesamt durch eine Liste inklusive serverseitig synchronisierter
 *   Mängel ohne lokales Foto.
 *
 * Wenn der Server gerade nicht erreichbar ist, bleibt der Export offline moeglich und wird aus
 * den lokal vorhandenen Bildern/Fachdaten erzeugt. Ein Online-Gesamtexport ist jedoch die
 * autoritative Variante fuer den fertigen Laptop-Baustellenordner.
 */
object GesamtMedienArchiv {

    private data class BinaryPlan(
        val path: String,
        val localFile: File? = null,
        val sourceZipPath: String? = null,
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

    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun ergaenzen(
        context: Context,
        normalizedZip: File,
        localRootKnotenId: Long,
        baustelleName: String
    ): File = withContext(Dispatchers.IO) {
        if (!normalizedZip.exists() || !normalizedZip.isFile) return@withContext normalizedZip

        val appContext = context.applicationContext
        val rootNode = runCatching { ServiceLocator.baustellenKnotenDao.laden(localRootKnotenId) }.getOrNull()
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
        val catalogItems = (catalogResult as? WorksiteMediaApi.CatalogResult.Success)?.items.orEmpty()
        val catalogAuthoritative = catalogResult is WorksiteMediaApi.CatalogResult.Success

        val outTemp = File(normalizedZip.absolutePath + ".media.tmp")
        ZipFile(normalizedZip).use { source ->
            val entries = source.entries().asSequence().filterNot { it.isDirectory }.toList()
            val archiveRoot = archiveRoot(entries.map { cleanPath(it.name) }, baustelleName)

            val sourceImagePathsByHash = linkedMapOf<String, MutableList<String>>()
            entries.forEach { entry ->
                val path = cleanPath(entry.name)
                if (!isImagePath(path)) return@forEach
                val digest = runCatching { source.getInputStream(entry).use { sha256(it.readBytes()) } }.getOrNull()
                    ?: return@forEach
                sourceImagePathsByHash.getOrPut(digest) { mutableListOf() }.add(path)
            }

            val localFotos = ServiceLocator.fotoRepository.fotosFuerBaustelleOnce(localBaustelleId)
                .filter { it.baustelleId == localBaustelleId && File(it.dateipfad).isFile }
            val localFileByHash = linkedMapOf<String, File>()
            localFotos.forEach { foto ->
                val file = File(foto.dateipfad)
                val digest = runCatching { sha256(file.readBytes()) }.getOrNull() ?: return@forEach
                localFileByHash.putIfAbsent(digest, file)
            }

            val localMaengel = MaengellisteExport.ladeItems(appContext, localBaustelleId, baustelleName)
            val syncMaengel = SyncMangelSpeicher.alle(appContext, localBaustelleId)
            val maengel = mergeMaengel(localMaengel, syncMaengel, baustelleName)
            val syncMangelByKey = syncMaengel.associateBy { it.mangelKey }

            val binaryPlans = mutableListOf<BinaryPlan>()
            val mangelPhotoPathByHash = linkedMapOf<String, String>()
            val mangelFolderByServerId = linkedMapOf<String, String>()

            maengel.forEach { item ->
                val sync = syncMangelByKey[item.key]
                val stableId = sync?.serverEntityId?.takeIf { it.isNotBlank() }
                    ?: sha256(item.key.toByteArray(Charsets.UTF_8)).take(16)
                val datum = item.datumIso.takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: "Ohne_Datum"
                val folder = "$archiveRoot/Mängel/$datum/Mangel_${safeToken(stableId).take(12)}"
                sync?.serverEntityId?.let { mangelFolderByServerId[it] = folder }

                val fotoTargets = mutableListOf<String>()
                item.fotos.forEach { mf ->
                    val file = File(mf.foto.dateipfad)
                    if (!file.isFile) return@forEach
                    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@forEach
                    val digest = sha256(bytes)
                    val ext = extension(file.name, null)
                    val target = "$folder/Fotos/${safeToken(mf.art.name)}_${digest.take(12)}.$ext"
                    mangelPhotoPathByHash[digest] = target
                    fotoTargets += target.removePrefix("$archiveRoot/")
                    binaryPlans += BinaryPlan(path = target, localFile = file)
                }

                val status = MangelStatusSpeicher.laden(appContext, item.key)
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
                binaryPlans += BinaryPlan(path = "$folder/Mangel_${safeToken(stableId).take(12)}.json", bytes = json)
                binaryPlans += BinaryPlan(
                    path = "$folder/Mangel_${safeToken(stableId).take(12)}.pdf",
                    bytes = MaengellisteExport.pdfBytes(appContext, baustelleName, listOf(item), "Einzel")
                )
            }

            val mediaRows = if (catalogAuthoritative) {
                rowsFromServerCatalog(
                    items = catalogItems,
                    archiveRoot = archiveRoot,
                    baustelleName = baustelleName,
                    sourceImagePathsByHash = sourceImagePathsByHash,
                    localFileByHash = localFileByHash,
                    mangelPhotoPathByHash = mangelPhotoPathByHash,
                    mangelFolderByServerId = mangelFolderByServerId,
                    plans = binaryPlans
                )
            } else {
                rowsFromLocal(
                    localFotos = localFotos,
                    archiveRoot = archiveRoot,
                    baustelleName = baustelleName,
                    responsible = ownResponsible,
                    sourceImagePathsByHash = sourceImagePathsByHash,
                    mangelPhotoPathByHash = mangelPhotoPathByHash
                )
            }

            val skipExact = setOf(
                "$archiveRoot/Bilderliste_gesamt.csv".lowercase(Locale.ROOT),
                "$archiveRoot/Bilderliste_gesamt.xlsx".lowercase(Locale.ROOT),
                "$archiveRoot/Maengelliste/Maengelliste_Gesamt.csv".lowercase(Locale.ROOT),
                "$archiveRoot/Maengelliste/Maengelliste_Gesamt.pdf".lowercase(Locale.ROOT)
            )

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

                entries.forEach { entry ->
                    val path = cleanPath(entry.name)
                    if (path.lowercase(Locale.ROOT) in skipExact) return@forEach
                    if (path.startsWith("$archiveRoot/Mängel/", ignoreCase = true)) return@forEach
                    val key = path.lowercase(Locale.ROOT)
                    if (!written.add(key)) return@forEach
                    out.putNextEntry(ZipEntry(path).apply { time = entry.time })
                    source.getInputStream(entry).use { it.copyTo(out, 64 * 1024) }
                    out.closeEntry()
                }

                writeDirectory("$archiveRoot/Mängel/")

                binaryPlans.distinctBy { it.path.lowercase(Locale.ROOT) }.forEach { plan ->
                    if (written.contains(plan.path.lowercase(Locale.ROOT))) return@forEach
                    val bytes = when {
                        plan.bytes != null -> plan.bytes
                        plan.localFile?.isFile == true -> runCatching { plan.localFile.readBytes() }.getOrNull()
                        !plan.sourceZipPath.isNullOrBlank() -> source.getEntry(plan.sourceZipPath)?.let { e ->
                            runCatching { source.getInputStream(e).use { it.readBytes() } }.getOrNull()
                        }
                        plan.serverItem != null && !token.isNullOrBlank() -> when (val dl = mediaApi.download(token, plan.serverItem.attachmentId)) {
                            is WorksiteMediaApi.DownloadResult.Success -> dl.media.bytes
                            else -> null
                        }
                        else -> null
                    } ?: return@forEach
                    writeBytes(plan.path, bytes)
                }

                val csvBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                writeBytes(
                    "$archiveRoot/Bilderliste_gesamt.csv",
                    csvBom + GesamtBilderlisteXlsx.csv(mediaRows).toByteArray(Charsets.UTF_8)
                )
                writeBytes(
                    "$archiveRoot/Bilderliste_gesamt.xlsx",
                    GesamtBilderlisteXlsx.bytes(mediaRows)
                )
                writeBytes(
                    "$archiveRoot/Maengelliste/Maengelliste_Gesamt.csv",
                    csvBom + MaengellisteExport.csvText(appContext, maengel).toByteArray(Charsets.UTF_8)
                )
                writeBytes(
                    "$archiveRoot/Maengelliste/Maengelliste_Gesamt.pdf",
                    MaengellisteExport.pdfBytes(appContext, baustelleName, maengel, "Gesamt")
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
        return result.values.sortedWith(compareBy<MaengellisteExport.MangelItem> { it.datumIso }.thenBy { it.zeit }.thenBy { it.beschreibung })
    }

    private fun rowsFromServerCatalog(
        items: List<WorksiteMediaApi.MediaItem>,
        archiveRoot: String,
        baustelleName: String,
        sourceImagePathsByHash: Map<String, List<String>>,
        localFileByHash: Map<String, File>,
        mangelPhotoPathByHash: Map<String, String>,
        mangelFolderByServerId: Map<String, String>,
        plans: MutableList<BinaryPlan>
    ): List<GesamtBilderlisteXlsx.Row> {
        val unique = items
            .filter { it.mimeType?.startsWith("image/") == true }
            .groupBy { it.sha256?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank) ?: "id:${it.attachmentId}" }
            .values
            .map { candidates ->
                candidates.sortedWith(
                    compareByDescending<WorksiteMediaApi.MediaItem> { semanticPriority(it) }
                        .thenByDescending { it.createdAt.orEmpty() }
                ).first()
            }

        data class WithTime(val epoch: Long, val row: GesamtBilderlisteXlsx.Row)
        val result = mutableListOf<WithTime>()

        unique.forEach { item ->
            val hash = item.sha256?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
                ?: sha256(item.attachmentId.toByteArray(Charsets.UTF_8))
            val meta = mediaMeta(item)
            val date = Instant.ofEpochMilli(meta.epochMs).atZone(ZoneId.systemDefault()).toLocalDate().format(isoDate)
            val target = when (item.entityType.uppercase(Locale.ROOT)) {
                "DEFECT" -> {
                    mangelPhotoPathByHash[hash]
                        ?: mangelFolderByServerId[item.parentEntityId]?.let { folder ->
                            "$folder/Fotos/${safeToken(defectArt(item.filename))}_${hash.take(12)}.${extension(item.filename, item.mimeType)}"
                        }
                        ?: "$archiveRoot/Mängel/$date/Mangel_${safeToken(item.parentEntityId).take(12)}/Fotos/${safeToken(defectArt(item.filename))}_${hash.take(12)}.${extension(item.filename, item.mimeType)}"
                }
                "ACCEPTANCE" -> sourceImagePathsByHash[hash]
                    ?.firstOrNull { "/Abnahmen/" in it }
                    ?: "$archiveRoot/Abnahmen/$date/Abnahme_${safeToken(item.parentEntityId).take(12)}/Fotos/Abnahme_${hash.take(12)}.${extension(item.filename, item.mimeType)}"
                else -> sourceImagePathsByHash[hash]
                    ?.firstOrNull { "/Bilder/" in it }
                    ?: "$archiveRoot/Bilder/$date/${safeToken(meta.art)}_${safeToken(meta.beschreibung).take(32)}_${hash.take(12)}.${extension(item.filename, item.mimeType)}"
            }

            val sourceAlreadyHasTarget = sourceImagePathsByHash[hash]?.any { it.equals(target, ignoreCase = true) } == true
            val mangelPlanAlready = plans.any { it.path.equals(target, ignoreCase = true) }
            if (!sourceAlreadyHasTarget && !mangelPlanAlready) {
                val sourceZipPath = sourceImagePathsByHash[hash]?.firstOrNull()
                plans += BinaryPlan(
                    path = target,
                    localFile = localFileByHash[hash],
                    sourceZipPath = sourceZipPath,
                    serverItem = if (localFileByHash[hash] == null && sourceZipPath == null) item else null
                )
            }

            result += WithTime(
                meta.epochMs,
                GesamtBilderlisteXlsx.Row(
                    nr = 0,
                    datum = date,
                    uhrzeit = Instant.ofEpochMilli(meta.epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt),
                    ersteller = item.uploadedByName.orEmpty(),
                    verantwortlicherTb = item.responsibleTb.orEmpty(),
                    art = meta.art,
                    baustelle = baustelleName,
                    bereich = meta.bereich,
                    km = meta.km,
                    firma = meta.firma,
                    beschreibung = meta.beschreibung,
                    status = displayStatus(item),
                    bilddatei = target.substringAfterLast('/'),
                    gps = meta.gps,
                    relativePath = target.removePrefix("$archiveRoot/")
                )
            )
        }

        return result.sortedBy { it.epoch }.mapIndexed { index, item -> item.row.copy(nr = index + 1) }
    }

    private fun rowsFromLocal(
        localFotos: List<de.tbhelfer.datenbank.FotoEntity>,
        archiveRoot: String,
        baustelleName: String,
        responsible: String,
        sourceImagePathsByHash: Map<String, List<String>>,
        mangelPhotoPathByHash: Map<String, String>
    ): List<GesamtBilderlisteXlsx.Row> {
        return localFotos.sortedBy { it.erstelltAmEpochMs }.mapNotNull { foto ->
            val file = File(foto.dateipfad)
            if (!file.isFile) return@mapNotNull null
            val hash = runCatching { sha256(file.readBytes()) }.getOrNull() ?: return@mapNotNull null
            val path = mangelPhotoPathByHash[hash]
                ?: sourceImagePathsByHash[hash]?.firstOrNull()
                ?: return@mapNotNull null
            val z = Instant.ofEpochMilli(foto.erstelltAmEpochMs).atZone(ZoneId.systemDefault())
            GesamtBilderlisteXlsx.Row(
                nr = 0,
                datum = z.toLocalDate().format(isoDate),
                uhrzeit = z.toLocalTime().format(timeFmt),
                ersteller = "",
                verantwortlicherTb = responsible,
                art = bildtypLabel(foto.bildtyp),
                baustelle = baustelleName,
                bereich = foto.objektBereich.orEmpty(),
                km = foto.km?.let { "KM ${de.tbhelfer.util.KmAnzeige.wert(it)}" }.orEmpty(),
                firma = foto.firma,
                beschreibung = foto.notiz?.takeIf { it.isNotBlank() } ?: foto.kurztext,
                status = "",
                bilddatei = path.substringAfterLast('/'),
                gps = "%.6f, %.6f".format(Locale.US, foto.gpsLat, foto.gpsLon),
                relativePath = path.removePrefix("$archiveRoot/")
            )
        }.mapIndexed { index, row -> row.copy(nr = index + 1) }
    }

    private fun semanticPriority(item: WorksiteMediaApi.MediaItem): Int = when (item.entityType.uppercase(Locale.ROOT)) {
        "DEFECT" -> 30 + when (defectArt(item.filename)) { "ERLEDIGT" -> 3; "IN_ARBEIT" -> 2; else -> 1 }
        "ACCEPTANCE" -> 20
        else -> 10
    }

    private fun mediaMeta(item: WorksiteMediaApi.MediaItem): PhotoMeta = when (item.entityType.uppercase(Locale.ROOT)) {
        "DEFECT" -> {
            val p = json(item.parentPayloadJson)
            val epoch = epochFromDateTime(p.optString("datumIso"), p.optString("zeit")) ?: parseServerTime(item.createdAt)
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
            val km = p.optNullableDouble("kmVon")?.let { "KM ${de.tbhelfer.util.KmAnzeige.wert(it)}" }.orEmpty()
            val token = semanticToken(item.filename, "ACCEPTANCE_FOTO")
            PhotoMeta(
                epochMs = epoch,
                art = if (token.isBlank()) "Abnahme" else "Abnahme – ${token.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}",
                bereich = p.optString("unterKnotenName", ""),
                km = km,
                firma = p.optString("firma", ""),
                beschreibung = p.optString("bezeichnung").ifBlank { p.optString("objekt", "Abnahme") },
                gps = ""
            )
        }
        else -> decodeBtbPhoto(item.filename) ?: PhotoMeta(
            epochMs = parseServerTime(item.createdAt),
            art = "Baudokumentation",
            bereich = "",
            km = "",
            firma = "",
            beschreibung = item.filename.orEmpty(),
            gps = ""
        )
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
                input.skipBytes(readTinySize(input)) // objektKennung
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

    private fun readTinySize(input: DataInputStream): Int {
        val size = input.readUnsignedByte()
        return size.also { if (it > 0) Unit }
    }

    private fun displayStatus(item: WorksiteMediaApi.MediaItem): String = when (item.entityType.uppercase(Locale.ROOT)) {
        "DEFECT" -> when (item.parentStatus?.uppercase(Locale.ROOT)) {
            "ERLEDIGT" -> "Erledigt"
            "IN_ARBEIT" -> "In Arbeit"
            "ABGELEHNT" -> "Abgelehnt"
            else -> "Offen"
        }
        "ACCEPTANCE" -> item.parentStatus.orEmpty().replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        else -> ""
    }

    private fun defectArt(filename: String?): String = semanticToken(filename, "DEFECT").ifBlank { "MANGEL" }

    private fun semanticToken(filename: String?, prefix: String): String {
        val raw = filename.orEmpty()
        if (!raw.startsWith("${prefix}__", ignoreCase = true)) return ""
        return raw.substringAfter("__", "").substringBefore("__", "").uppercase(Locale.ROOT)
    }

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
        return runCatching { Instant.parse(raw).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }

    private fun archiveRoot(paths: List<String>, baustelleName: String): String {
        val fromZip = paths.firstOrNull { it.startsWith("TB-Helfer/") }
            ?.split('/')
            ?.takeIf { it.size >= 3 }
            ?.getOrNull(1)
        return "TB-Helfer/${fromZip ?: safeToken(baustelleName).ifBlank { "Baustelle" }}"
    }

    private fun cleanPath(path: String): String = path.replace('\\', '/').trimStart('/')

    private fun isImagePath(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
    }

    private fun extension(filename: String?, mimeType: String?): String {
        val ext = filename.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in setOf("jpg", "jpeg", "png", "webp")) return if (ext == "jpeg") "jpg" else ext
        return when (mimeType?.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

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
}
