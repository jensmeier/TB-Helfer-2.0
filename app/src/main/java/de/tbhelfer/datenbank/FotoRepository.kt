package de.tbhelfer.datenbank

import android.content.Context
import android.os.Environment
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class FotoRepository(
    private val dao: FotoDao,
    private val appContext: Context? = null
) {
    fun alleFotos(): Flow<List<FotoEntity>> = dao.alleFotos()
        .map { fotos -> appContext?.let { BtbPhotoContextOverrideStore.applyToFotos(it, fotos) } ?: fotos }
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
                loescheAppFotoDateiWennUnbenutzt(ctx, foto)
            }
            runCatching { SofortSync.anstossen(ctx) }
        }
        result
    }

    fun fotosFuerBaustelle(baustelleId: Long): Flow<List<FotoEntity>> = dao.fotosFuerBaustelle(baustelleId)
        .map { fotos -> appContext?.let { BtbPhotoContextOverrideStore.applyToFotos(it, fotos) } ?: fotos }
        .flowOn(Dispatchers.IO)

    suspend fun fotosFuerBaustelleOnce(baustelleId: Long): List<FotoEntity> = withContext(Dispatchers.IO) {
        val raw = dao.fotosFuerBaustelleOnce(baustelleId)
        val scoped = appContext?.let { ctx -> BtbFotoQueryScope.filter(ctx, baustelleId, raw) } ?: raw
        val corrected = appContext?.let { ctx -> BtbPhotoContextOverrideStore.applyToFotos(ctx, scoped) } ?: scoped
        BtbFotoRenderMarkierung.anwenden(corrected)
    }

    fun fotosFuerUnterknoten(baustelleId: Long, knotenId: Long): Flow<List<FotoEntity>> =
        dao.fotosFuerUnterknoten(baustelleId, knotenId)
            .map { fotos -> appContext?.let { BtbPhotoContextOverrideStore.applyToFotos(it, fotos) } ?: fotos }
            .flowOn(Dispatchers.IO)

    suspend fun fotosFuerUnterknotenOnce(baustelleId: Long, knotenId: Long): List<FotoEntity> = withContext(Dispatchers.IO) {
        val raw = dao.fotosFuerUnterknotenOnce(baustelleId, knotenId)
        appContext?.let { BtbPhotoContextOverrideStore.applyToFotos(it, raw) } ?: raw
    }

    suspend fun einfuegen(entity: FotoEntity): Long {
        val id = withContext(Dispatchers.IO) { dao.einfuegen(entity) }
        appContext?.let { ctx ->
            runCatching {
                val active = BtbActiveContext.laden(ctx, entity.baustelleId)
                val activeBtbId = active?.btbId
                if (!activeBtbId.isNullOrBlank()) {
                    BtbFotoZuordnungSpeicher.zuordnen(ctx, id, activeBtbId)
                    val alleFotos = dao.fotosFuerBaustelleOnce(entity.baustelleId)
                    invalidiereFotoAbschlussWennNoetig(ctx, entity.baustelleId, activeBtbId, alleFotos)
                }
            }
            runCatching { SofortSync.anstossen(ctx) }
        }
        return id
    }

    /**
     * Import-/Sonderpfad: ordnet das Foto ausdrücklich der übergebenen stabilen BTB-ID zu.
     * Datum/Uhrzeit entscheiden bei modernen BTBs nicht über die Zugehörigkeit.
     */
    suspend fun einfuegenFuerBtb(entity: FotoEntity, btbId: String): Long {
        val stableId = btbId.trim()
        if (stableId.isBlank()) return einfuegen(entity)
        val id = withContext(Dispatchers.IO) { dao.einfuegen(entity) }
        appContext?.let { ctx ->
            withContext(Dispatchers.IO) {
                BtbFotoZuordnungSpeicher.zuordnen(ctx, id, stableId)
                val alleFotos = dao.fotosFuerBaustelleOnce(entity.baustelleId)
                invalidiereFotoAbschlussWennNoetig(ctx, entity.baustelleId, stableId, alleFotos)
            }
            runCatching { SofortSync.anstossen(ctx) }
        }
        return id
    }

    suspend fun updateNotizMitIds(ids: List<Long>, notiz: String?): Int = withContext(Dispatchers.IO) {
        dao.updateNotizMitIds(ids, notiz)
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
            loescheAppFotoDateiWennUnbenutzt(ctx, entity)
            runCatching { SofortSync.anstossen(ctx) }
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
