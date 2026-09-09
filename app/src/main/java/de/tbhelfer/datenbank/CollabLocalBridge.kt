package de.tbhelfer.datenbank

import android.content.Context
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import de.tbhelfer.ui.baustelle.MaengellisteExport
import de.tbhelfer.ui.baustelle.MangelStatusSpeicher
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.SchichtTyp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Brücke zwischen den bestehenden lokalen Fachspeichern und dem neuen Server-Sync.
 *
 * Sie ersetzt die vorhandenen lokalen Datenmodelle NICHT. Stattdessen werden vorhandene BTB,
 * Mängel und Abnahmen in die Sync-Queue gespiegelt. Eingehende Serverstände werden dort, wo die
 * bestehende App bereits ein sicheres Importziel besitzt, wieder in den lokalen Fachspeicher
 * übernommen.
 *
 * Mängel von anderen Geräten bleiben zusätzlich im Collab-Cache. Für vollständig neue Mängel
 * fehlt im alten Mängelmodell noch ein eigener nicht-fotobasierter Persistenzspeicher; deshalb
 * wird hier nur der Status eines bereits lokal bekannten Mangel-Keys zurückgespiegelt. Die
 * vollständige Anzeige neuer Server-Mängel wird separat an die Mängelliste angebunden.
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

    data class ImportSummary(
        val worksites: Int,
        val btbWritten: Int,
        val defectStatusesApplied: Int,
        val acceptancesWritten: Int
    )

    suspend fun exportLocalChanges(): ExportSummary {
        val bound = bindingRepository.contextsWithBindings().filter { it.binding != null && it.localWorksite != null }
        var total = 0
        var btbCount = 0
        var defectCount = 0
        var acceptanceCount = 0

        for (item in bound) {
            val binding = item.binding ?: continue
            val local = item.localWorksite ?: continue
            val server = item.context
            val localBaustelleId = local.altBaustelleId
            val localName = local.hauptbaustellenName

            for (verlauf in BautagebuchSpeicher.verlaufFuerBaustelle(appContext, localBaustelleId, localName)) {
                val source = findBtbJson(localBaustelleId, localName, verlauf.datum, verlauf.schicht) ?: continue
                val payload = sanitizeBtbPayload(source.readText(Charsets.UTF_8), localBaustelleId, localName)
                val clientKey = "${verlauf.datum}:${verlauf.schicht.name}"
                if (queueIfChanged(
                        worksiteServerId = server.worksiteServerId,
                        entityType = SyncCollabPendingChangeEntity.TYPE_BTB,
                        clientKey = clientKey,
                        groupId = server.groupId,
                        status = if (verlauf.entry.istFertig) "FERTIG" else "ENTWURF",
                        areaScopeJson = server.areaScopeJson,
                        payloadJson = payload
                    )
                ) {
                    total++
                    btbCount++
                }
            }

            val defects = MaengellisteExport.ladeItems(appContext, localBaustelleId, localName)
            for (defect in defects) {
                val status = MangelStatusSpeicher.laden(appContext, defect.key)
                val payload = JSONObject()
                    .put("key", defect.key)
                    .put("datumIso", defect.datumIso)
                    .put("zeit", defect.zeit)
                    .put("baustelle", localName)
                    .put("bereich", defect.bereich)
                    .put("km", defect.km)
                    .put("firma", defect.firma)
                    .put("beschreibung", defect.beschreibung)
                    .put("gps", defect.gps)
                    .put("btbBildNr", defect.btbBildNr)
                    .put("kommentar", status.kommentar)
                    .putNullable("erledigtAmEpochMs", status.erledigtAmEpochMs)
                    .put("fotos", JSONArray().apply {
                        defect.fotos.forEach { mf ->
                            put(JSONObject()
                                .put("localFotoId", mf.foto.id)
                                .put("fotoArt", mf.art.name)
                                .put("filename", File(mf.foto.dateipfad).name)
                                .put("erstelltAmEpochMs", mf.foto.erstelltAmEpochMs)
                            )
                        }
                    })
                    .toString()

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

    suspend fun applyServerStateToLocal(): ImportSummary {
        val bound = bindingRepository.contextsWithBindings().filter { it.binding != null && it.localWorksite != null }
        var btbWritten = 0
        var defectStatuses = 0
        var acceptanceWritten = 0

        for (item in bound) {
            val local = item.localWorksite ?: continue
            val server = item.context
            val localBaustelleId = local.altBaustelleId
            val localName = local.hauptbaustellenName

            val btbItems = collabRepository.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_BTB)
                .filter { it.groupId == server.groupId }
            for (entity in btbItems) {
                val key = entity.clientEntityKey ?: continue
                if (dao.collabPendingForClientKey(server.worksiteServerId, entity.entityType, key) != null) continue
                val payload = runCatching { JSONObject(entity.payloadJson) }.getOrNull() ?: continue
                val datum = payload.optString("datumIso").takeIf { it.isNotBlank() }?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                } ?: continue
                val schicht = runCatching {
                    SchichtTyp.valueOf(payload.optString("schicht", SchichtTyp.TAG.name))
                }.getOrDefault(SchichtTyp.TAG)

                payload.put("baustelleId", localBaustelleId)
                payload.put("baustelleName", localName)
                payload.put("fotos", JSONArray())
                payload.put("pdfAuswahl", JSONObject())

                val target = File(
                    BautagebuchSpeicher.ordner(appContext, localBaustelleId, localName, datum),
                    if (schicht == SchichtTyp.NACHT) "bautagebuch_nacht.json" else "bautagebuch_tag.json"
                )
                if (writeIfDifferent(target, payload.toString(2))) btbWritten++
                rememberHash(server.worksiteServerId, entity.entityType, key, stableHash(entity.status, payload.toString()))
            }

            val localDefectKeys = MaengellisteExport.ladeItems(appContext, localBaustelleId, localName)
                .mapTo(hashSetOf()) { it.key }
            val defectItems = collabRepository.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_DEFECT)
            for (entity in defectItems) {
                val key = entity.clientEntityKey ?: continue
                if (key !in localDefectKeys) continue
                if (dao.collabPendingForClientKey(server.worksiteServerId, entity.entityType, key) != null) continue
                val payload = runCatching { JSONObject(entity.payloadJson) }.getOrElse { JSONObject() }
                val incomingStatus = runCatching {
                    MangelStatusSpeicher.Status.valueOf(entity.status ?: payload.optString("status", "OFFEN"))
                }.getOrDefault(MangelStatusSpeicher.Status.OFFEN)
                val old = MangelStatusSpeicher.laden(appContext, key)
                val newValue = if (old.status == MangelStatusSpeicher.Status.ERLEDIGT) old else old.copy(
                    status = incomingStatus,
                    kommentar = payload.optString("kommentar", old.kommentar),
                    erledigtAmEpochMs = payload.optNullableLong("erledigtAmEpochMs") ?: old.erledigtAmEpochMs
                )
                MangelStatusSpeicher.speichern(appContext, key, newValue)
                defectStatuses++
                rememberHash(server.worksiteServerId, entity.entityType, key, stableHash(entity.status, entity.payloadJson))
            }

            val acceptanceItems = collabRepository.entities(server.worksiteServerId, SyncCollabPendingChangeEntity.TYPE_ACCEPTANCE)
            for (entity in acceptanceItems) {
                val key = entity.clientEntityKey ?: continue
                if (dao.collabPendingForClientKey(server.worksiteServerId, entity.entityType, key) != null) continue
                val parsed = acceptanceFromPayload(
                    payloadJson = entity.payloadJson,
                    clientKey = key,
                    localBaustelleId = localBaustelleId,
                    localName = localName,
                    localRootKnotenId = local.id,
                    status = entity.status
                ) ?: continue

                val old = AbnahmeSpeicher.laden(appContext, parsed.id)
                if (old?.istEndgueltigAbgeschlossen == true && !parsed.istEndgueltigAbgeschlossen) continue
                AbnahmeSpeicher.speichern(appContext, parsed)
                acceptanceWritten++
                rememberHash(server.worksiteServerId, entity.entityType, key, stableHash(entity.status, acceptancePayload(parsed, localName)))
            }
        }

        return ImportSummary(bound.size, btbWritten, defectStatuses, acceptanceWritten)
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

        val serverEntity = dao.collabEntities(worksiteServerId, type)
            .firstOrNull { it.clientEntityKey == clientKey }
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

    private fun findBtbJson(
        baustelleId: Long,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ): File? {
        val dir = BautagebuchSpeicher.ordner(appContext, baustelleId, baustelleName, datum)
        val preferred = File(dir, if (schicht == SchichtTyp.NACHT) "bautagebuch_nacht.json" else "bautagebuch_tag.json")
        if (preferred.exists()) return preferred
        if (schicht == SchichtTyp.TAG) {
            val legacy = File(dir, "bautagebuch.json")
            if (legacy.exists()) return legacy
        }
        return null
    }

    private fun sanitizeBtbPayload(raw: String, baustelleId: Long, baustelleName: String): String {
        val payload = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        payload.put("baustelleId", baustelleId)
        payload.put("baustelleName", baustelleName)
        // Lokale Foto-IDs sind auf anderen Geräten bedeutungslos. Bilder von Mängeln/Abnahmen
        // werden separat als Attachments synchronisiert.
        payload.put("fotos", JSONArray())
        payload.put("pdfAuswahl", JSONObject())
        return payload.toString()
    }

    private fun acceptancePayload(a: AbnahmeSpeicher.Abnahme, localName: String): String = JSONObject()
        .put("id", a.id)
        .put("nummer", a.nummer)
        .put("baustelleName", localName)
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
        .put("fotoRefs", JSONArray().apply {
            a.fotos.forEach { f ->
                put(JSONObject()
                    .put("localFotoId", f.fotoId)
                    .put("bildArt", f.bildArt.name)
                    .put("bemerkung", f.bemerkung)
                    .put("erstelltAmEpochMs", f.erstelltAmEpochMs)
                )
            }
        })
        .put("anlagen", JSONArray().apply {
            a.anlagen.forEach { d ->
                put(JSONObject()
                    .put("id", d.id)
                    .put("typ", d.typ.name)
                    .put("filename", File(d.originalPfad).name)
                    .put("bezeichnung", d.bezeichnung)
                    .put("bemerkung", d.bemerkung)
                    .put("erstelltAmEpochMs", d.erstelltAmEpochMs)
                )
            }
        })
        .toString()

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
        val isFinal = ergebnis == AbnahmeSpeicher.Ergebnis.ABGENOMMEN ||
            ergebnis == AbnahmeSpeicher.Ergebnis.NICHT_ABGENOMMEN

        return AbnahmeSpeicher.Abnahme(
            id = o.optString("id").ifBlank { clientKey },
            nummer = o.optInt("nummer", 1),
            baustelleId = localBaustelleId,
            baustelleName = localName,
            hauptKnotenId = localRootKnotenId,
            unterKnotenId = null,
            unterKnotenName = o.optString("unterKnotenName", ""),
            bezeichnung = o.optString("bezeichnung", ""),
            objekt = o.optString("objekt", ""),
            firma = o.optString("firma", ""),
            weitereFirmen = o.optString("weitereFirmen", ""),
            abnehmender = o.optString("abnehmender", ""),
            anwesende = o.optString("anwesende", ""),
            kmVon = o.optNullableDouble("kmVon"),
            kmBis = o.optNullableDouble("kmBis"),
            kommentar = o.optString("kommentar", ""),
            restpunkte = o.optString("restpunkte", ""),
            ergebnis = ergebnis,
            startEpochMs = o.optLong("startEpochMs", System.currentTimeMillis()),
            abgeschlossenAmEpochMs = if (isFinal) completed ?: System.currentTimeMillis() else null,
            wiederGeoeffnetAmEpochMs = null,
            fotos = emptyList(),
            anlagen = emptyList()
        )
    }

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
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
        return this
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key)
}
