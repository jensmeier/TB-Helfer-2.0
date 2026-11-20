package de.tbhelfer.datenbank

import android.content.Context
import android.os.Environment
import de.tbhelfer.server.BtbServerStateStore
import de.tbhelfer.server.SofortSync
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbActiveContext
import de.tbhelfer.ui.bautagebuch.BtbDokumentVersionierung
import de.tbhelfer.ui.bautagebuch.BtbFotoQueryScope
import de.tbhelfer.ui.bautagebuch.BtbFotoRenderMarkierung
import de.tbhelfer.ui.bautagebuch.BtbFotoZuordnungSpeicher
import de.tbhelfer.ui.bautagebuch.BtbOriginalFotoSpeicher
import de.tbhelfer.ui.bautagebuch.BtbPhotoContextOverrideStore
import de.tbhelfer.ui.kamera.FotoSpeicher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import de.tbhelfer.ui.bautagebuch.BtbPhotoMetadatenStore
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class FotoRepository(
    private val dao: FotoDao,
    private val appContext: Context? = null
) {
    fun alleFotos(): Flow<List<FotoEntity>> = dao.alleFotos()
        .combine(BtbPhotoMetadatenStore.changes) { fotos, _ -> appContext?.let { BtbPhotoContextOverrideStore.applyToFotos(it, fotos) } ?: fotos }
        .flowOn(Dispatchers.IO)

    suspend fun fotoMitId(id: Long): FotoEntity? = withContext(Dispatchers.IO) {
        val raw = dao.fotoMitId(id) ?: return@withContext null
        appContext?.let { BtbPhotoContextOverrideStore.applyToFoto(it, raw) } ?: raw
    }

    suspend fun fotosMitIds(ids: List<Long>): List<FotoEntity> = withContext(Dispatchers.IO) {
        val raw = dao.fotosMitIds(ids)
        appContext?.let { BtbPhotoContextOverrideStore.applyToFotos(it, raw) } ?: raw
    }

    private suspend fun invalidiereFotoAbschlussWennNoetig(
        context: Context,
        baustelleId: Long,
        btbId: String,
        alleFotos: List<FotoEntity>? = null
    ) {
        if (BtbServerStateStore.state(context, btbId)?.readOnly == true) return
        val active = BtbActiveContext.laden(context, baustelleId)
        val entry = when {
            active?.btbId == btbId -> active.entry
            else -> {
                val baustelle = de.tbhelfer.ServiceLocator.baustellenRepository.baustelleMitIdOnce(baustelleId)
                val name = baustelle?.baustellenname.orEmpty()
                BautagebuchSpeicher.ladenById(context, baustelleId, name, btbId)
            }
        } ?: return
        val fotos = alleFotos ?: dao.fotosFuerBaustelleOnce(baustelleId)
        BtbOriginalFotoSpeicher.migriereAusBestehendemOriginalWennFehlt(context, entry, fotos)
        if (BtbOriginalFotoSpeicher.hatOriginal(context, btbId)) {
            BtbDokumentVersionierung.loescheAktuellesAbschlussDokument(context, entry)
        }
    }

    suspend fun loeschenMitIds(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        val ctx = appContext
        val beforeDelete = dao.fotosMitIds(ids)
        val affected = if (ctx != null) {
            beforeDelete.mapNotNull { foto ->
                BtbFotoZuordnungSpeicher.btbId(ctx, foto.id)?.let { btbId -> foto.baustelleId to btbId }
            }.distinct()
        } else emptyList()

        if (ctx != null) {
            affected.forEach { (baustelleId, btbId) ->
                invalidiereFotoAbschlussWennNoetig(ctx, baustelleId, btbId)
            }
        }

        val result = dao.loeschenMitIds(ids)
        if (ctx != null && result > 0) {
            beforeDelete.forEach { foto ->
                val btbId = BtbFotoZuordnungSpeicher.btbId(ctx, foto.id)
                BtbPhotoContextOverrideStore.clearForPhoto(ctx, btbId, foto.id)
                BtbFotoZuordnungSpeicher.entfernen(ctx, foto.id)
                BtbPhotoMetadatenStore.forget(ctx, foto.id)
                loescheAppFotoDateiWennUnbenutzt(ctx, foto)
            }
            if (affected.isNotEmpty()) {
                affected.forEach { (baustelleId, btbId) ->
                    runCatching { SofortSync.btbAnstossen(ctx, baustelleId, btbId) }
                }
            } else {
                runCatching { SofortSync.anstossen(ctx) }
            }
        }
        result
    }

    fun fotosFuerBaustelle(baustelleId: Long): Flow<List<FotoEntity>> = dao.fotosFuerBaustelle(baustelleId)
        .combine(BtbPhotoMetadatenStore.changes) { fotos, _ -> appContext?.let { BtbPhotoContextOverrideStore.applyToFotos(it, fotos) } ?: fotos }
        .flowOn(Dispatchers.IO)

    /**
     * UI-Leseweg: solange ein moderner BTB geoeffnet ist, begrenzt BtbFotoQueryScope die
     * sichtbaren Fotos strikt auf dessen btbId. Das schuetzt Vorschau, Editor und PDF.
     */
    suspend fun fotosFuerBaustelleOnce(baustelleId: Long): List<FotoEntity> = withContext(Dispatchers.IO) {
        val raw = dao.fotosFuerBaustelleOnce(baustelleId)
        val scoped = appContext?.let { ctx -> BtbFotoQueryScope.filter(ctx, baustelleId, raw) } ?: raw
        val corrected = appContext?.let { ctx -> BtbPhotoContextOverrideStore.applyToFotos(ctx, scoped) } ?: scoped
        BtbFotoRenderMarkierung.anwenden(corrected)
    }

    /**
     * Hintergrund-Sync-Leseweg: bewusst ohne BtbFotoQueryScope. Der Team-Sync iteriert ueber
     * mehrere BTBs einer Baustelle und muss deshalb alle lokalen Fotos sehen koennen; die
     * eigentliche moderne Auswahl erfolgt dort anschliessend strikt ueber Foto -> btbId.
     */
    suspend fun fotosFuerBaustelleSyncOnce(baustelleId: Long): List<FotoEntity> = withContext(Dispatchers.IO) {
        val raw = dao.fotosFuerBaustelleOnce(baustelleId)
        val corrected = appContext?.let { ctx -> BtbPhotoContextOverrideStore.applyToFotos(ctx, raw) } ?: raw
        BtbFotoRenderMarkierung.anwenden(corrected)
    }

    fun fotosFuerUnterknoten(baustelleId: Long, knotenId: Long): Flow<List<FotoEntity>> =
        dao.fotosFuerBaustelle(baustelleId)
            .combine(BtbPhotoMetadatenStore.changes) { fotos, _ ->
                (appContext?.let { BtbPhotoContextOverrideStore.applyToFotos(it, fotos) } ?: fotos)
                    .filter { it.baustellenKnotenId == knotenId }
            }
            .flowOn(Dispatchers.IO)

    suspend fun fotosFuerUnterknotenOnce(baustelleId: Long, knotenId: Long): List<FotoEntity> = withContext(Dispatchers.IO) {
        val raw = dao.fotosFuerBaustelleOnce(baustelleId)
        (appContext?.let { BtbPhotoContextOverrideStore.applyToFotos(it, raw) } ?: raw)
            .filter { it.baustellenKnotenId == knotenId }
    }

    /** Bind a new photo immediately by its short caption, never by capture time or another BTB. */
    private suspend fun bindNewPhoto(context: Context, foto: FotoEntity, id: Long, btbId: String) = withContext(Dispatchers.IO) {
        val active = BtbActiveContext.laden(context, foto.baustelleId) ?: return@withContext
        if (active.btbId != btbId) return@withContext
        val root = de.tbhelfer.ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(foto.baustelleId)
        val nodeKey = foto.baustellenKnotenId?.takeIf { it > 0L }?.let { localId ->
            root?.let { de.tbhelfer.ServiceLocator.worksiteStructureSync.ensureClientNodeKey(it.id, localId) }
                ?: return@withContext
        }
        de.tbhelfer.ui.bautagebuch.BtbPhotoEntryMatcher.find(active.entry, foto.bildtyp, foto.firma,
            nodeKey, foto.kurztext)?.let { BtbPhotoMetadatenStore.bind(context, btbId, id, it) }
    }

    suspend fun einfuegen(entity: FotoEntity): Long {
        val id = withContext(Dispatchers.IO) { dao.einfuegen(entity) }
        appContext?.let { ctx ->
            entity.btbMetadaten?.let {
                BtbPhotoMetadatenStore.source(ctx, id, it.lat, it.lon, it.epoch, it.accuracyM)
            }
            var activeBtbId: String? = null
            runCatching {
                val active = BtbActiveContext.laden(ctx, entity.baustelleId)
                activeBtbId = active?.btbId
                if (!activeBtbId.isNullOrBlank()) {
                    BtbFotoZuordnungSpeicher.zuordnen(ctx, id, activeBtbId)
                    bindNewPhoto(ctx, entity, id, activeBtbId!!)
                    val alleFotos = dao.fotosFuerBaustelleOnce(entity.baustelleId)
                    invalidiereFotoAbschlussWennNoetig(ctx, entity.baustelleId, activeBtbId!!, alleFotos)
                }
            }
            if (!activeBtbId.isNullOrBlank()) {
                runCatching { SofortSync.btbAnstossen(ctx, entity.baustelleId, activeBtbId!!) }
            } else {
                runCatching { SofortSync.anstossen(ctx) }
            }
        }
        return id
    }

    /**
     * Import-/Sonderpfad: ordnet das Foto ausdrücklich der übergebenen stabilen BTB-ID zu.
     * Datum/Uhrzeit entscheiden bei modernen BTBs nicht über die Zugehörigkeit.
     */
    suspend fun einfuegenFuerBtb(entity: FotoEntity, btbId: String, triggerSync: Boolean = true, clientEntryKey: String? = null): Long {
        val stableId = btbId.trim()
        if (stableId.isBlank()) return einfuegen(entity)
        val id = withContext(Dispatchers.IO) { dao.einfuegen(entity) }
        appContext?.let { ctx ->
            entity.btbMetadaten?.let {
                BtbPhotoMetadatenStore.source(ctx, id, it.lat, it.lon, it.epoch, it.accuracyM)
            }
            withContext(Dispatchers.IO) {
                BtbFotoZuordnungSpeicher.zuordnen(ctx, id, stableId)
                if (!clientEntryKey.isNullOrBlank()) BtbPhotoMetadatenStore.bind(ctx, stableId, id, clientEntryKey)
                else bindNewPhoto(ctx, entity, id, stableId)
                val alleFotos = dao.fotosFuerBaustelleOnce(entity.baustelleId)
                invalidiereFotoAbschlussWennNoetig(ctx, entity.baustelleId, stableId, alleFotos)
            }
            if (triggerSync) {
                runCatching { SofortSync.btbAnstossen(ctx, entity.baustelleId, stableId) }
            }
        }
        return id
    }

    /**
     * Corrections are durable in Room and BTB overrides until a versioned
     * server acknowledgement. No original bytes or photo ids are changed.
     * An immutable finalized BTB cannot be edited without entering REWORK.
     */
    suspend fun updateBeschriftung(id: Long, firma: String, kurztext: String, notiz: String?): Boolean =
        withContext(Dispatchers.IO) {
            val foto = dao.fotoMitId(id) ?: return@withContext false
            val normalizedFirma = firma.trim().take(200)
            val normalizedKurztext = kurztext.trim().take(500)
            val normalizedNotiz = notiz?.trim()?.take(2000)?.takeIf(String::isNotBlank)
            if (foto.firma == normalizedFirma && foto.kurztext == normalizedKurztext &&
                foto.notiz == normalizedNotiz) return@withContext true
            val ctx = appContext
            val btbId = ctx?.let { BtbFotoZuordnungSpeicher.btbId(it, id) }
            if (ctx != null && btbId != null) {
                check(BtbServerStateStore.state(ctx, btbId)?.readOnly != true) {
                    "BTB ist schreibgeschützt. Bitte den BTB erst zur Nachbearbeitung öffnen."
                }
                invalidiereFotoAbschlussWennNoetig(ctx, foto.baustelleId, btbId)
                val previous = BtbPhotoContextOverrideStore.load(ctx, btbId)[id]
                BtbPhotoContextOverrideStore.setOverride(
                    ctx, btbId, id,
                    (previous ?: BtbPhotoContextOverrideStore.ContextOverride(
                        firma = foto.firma, objektKennung = foto.objektKennung,
                        objektBereich = foto.objektBereich
                    )).copy(firma = normalizedFirma, kurztext = normalizedKurztext,
                        notiz = normalizedNotiz ?: "")
                )
            }
            check(dao.updateBeschriftung(id, normalizedFirma, normalizedKurztext, normalizedNotiz) == 1) {
                "Foto wurde während der Bearbeitung entfernt."
            }
            if (ctx != null) {
                BtbPhotoMetadatenStore.notifyChanges()
                if (btbId != null) SofortSync.btbAnstossen(ctx, foto.baustelleId, btbId)
                else SofortSync.anstossen(ctx)
            }
            true
        }

    suspend fun updateNotizMitIds(ids: List<Long>, notiz: String?): Int = withContext(Dispatchers.IO) {
        dao.updateNotizMitIds(ids, notiz)
    }

    /**
     * Korrigiert die echte Unterbaustellen-Zuordnung vorhandener Fotos.
     * null bedeutet Hauptbaustelle. Vorhandene BTB-Kontext-Overrides werden mitgezogen,
     * damit der korrigierte Ort nicht beim naechsten Lesevorgang wieder ueberschrieben wird.
     */
    suspend fun updateBaustellenKnotenMitIds(ids: List<Long>, knotenId: Long?): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0
        val ctx = appContext
        val before = dao.fotosMitIds(ids)
        if (before.isEmpty()) return@withContext 0

        val targetOrtId = knotenId ?: 0L
        val dbChangedIds = before
            .filter { it.baustellenKnotenId != knotenId }
            .map { it.id }

        // Nur eine fachlich echte Ortsaenderung darf einen bereits abgeschlossenen BTB
        // invalidieren. Ein reines "Speichern" des Mangel-Editors mit unveraenderter
        // Unterbaustelle ist ein No-op und darf kein Abschluss-PDF loeschen.
        val effectiveChanged = mutableListOf<Pair<FotoEntity, String>>()

        if (ctx != null) {
            before.forEach { foto ->
                val btbId = BtbFotoZuordnungSpeicher.btbId(ctx, foto.id) ?: return@forEach
                val existing = BtbPhotoContextOverrideStore.load(ctx, btbId)[foto.id]
                val effectiveBefore = if (existing != null) {
                    existing.ortId?.takeIf { it > 0L }
                } else {
                    foto.baustellenKnotenId
                }

                if (effectiveBefore != knotenId) {
                    val override = existing?.copy(ortId = targetOrtId)
                        ?: BtbPhotoContextOverrideStore.ContextOverride(
                            firma = foto.firma,
                            objektKennung = foto.objektKennung,
                            objektBereich = foto.objektBereich,
                            ortId = targetOrtId
                        )
                    BtbPhotoContextOverrideStore.setOverride(ctx, btbId, foto.id, override)
                    effectiveChanged += foto to btbId
                }
            }
        }

        val dbResult = if (dbChangedIds.isNotEmpty()) {
            dao.updateBaustellenKnotenMitIds(dbChangedIds, knotenId)
        } else {
            0
        }

        if (ctx != null) {
            effectiveChanged
                .map { (foto, btbId) -> foto.baustelleId to btbId }
                .distinct()
                .forEach { (baustelleId, btbId) ->
                    invalidiereFotoAbschlussWennNoetig(ctx, baustelleId, btbId)
                }

            if (effectiveChanged.isNotEmpty()) {
                effectiveChanged
                    .map { (foto, btbId) -> foto.baustelleId to btbId }
                    .distinct()
                    .forEach { (baustelleId, btbId) ->
                        runCatching { SofortSync.btbAnstossen(ctx, baustelleId, btbId) }
                    }
            } else if (dbResult > 0) {
                runCatching { SofortSync.anstossen(ctx) }
            }
        }

        (dbChangedIds + effectiveChanged.map { it.first.id }).distinct().size
    }

    suspend fun loeschen(entity: FotoEntity): Int = withContext(Dispatchers.IO) {
        val ctx = appContext
        val btbId = ctx?.let { BtbFotoZuordnungSpeicher.btbId(it, entity.id) }
        if (ctx != null && !btbId.isNullOrBlank()) {
            invalidiereFotoAbschlussWennNoetig(ctx, entity.baustelleId, btbId)
        }
        val result = dao.loeschen(entity)
        if (ctx != null && result > 0) {
            BtbPhotoContextOverrideStore.clearForPhoto(ctx, btbId, entity.id)
            BtbFotoZuordnungSpeicher.entfernen(ctx, entity.id)
            BtbPhotoMetadatenStore.forget(ctx, entity.id)
            loescheAppFotoDateiWennUnbenutzt(ctx, entity)
            if (!btbId.isNullOrBlank()) {
                runCatching { SofortSync.btbAnstossen(ctx, entity.baustelleId, btbId) }
            } else {
                runCatching { SofortSync.anstossen(ctx) }
            }
        }
        result
    }

    private suspend fun loescheAppFotoDateiWennUnbenutzt(context: Context, foto: FotoEntity) {
        if (dao.anzahlMitDateipfad(foto.dateipfad) > 0) return
        val root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return
        val file = File(foto.dateipfad)
        val rootPath = runCatching { root.canonicalFile.absolutePath }.getOrNull() ?: return
        val filePath = runCatching { file.canonicalFile.absolutePath }.getOrNull() ?: return
        if (filePath != rootPath && !filePath.startsWith(rootPath + File.separator)) return
        runCatching { FotoSpeicher.thumbnailDateiFuer(file).delete() }
        runCatching { file.delete() }
    }

    suspend fun alleFirmen(): List<String> = withContext(Dispatchers.IO) { dao.alleFirmen() }

    suspend fun alleKurztexte(): List<String> = withContext(Dispatchers.IO) { dao.alleKurztexte() }
}


