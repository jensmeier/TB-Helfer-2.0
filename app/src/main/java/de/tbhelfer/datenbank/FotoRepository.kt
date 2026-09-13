package de.tbhelfer.datenbank

import android.content.Context
import de.tbhelfer.server.SofortSync
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbDokumentVersionierung
import de.tbhelfer.ui.bautagebuch.BtbFotoRenderMarkierung
import de.tbhelfer.ui.bautagebuch.BtbFotoZuordnungSpeicher
import de.tbhelfer.ui.bautagebuch.BtbOriginalFotoSpeicher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class FotoRepository(
    private val dao: FotoDao,
    private val appContext: Context? = null
) {
    fun alleFotos(): Flow<List<FotoEntity>> = dao.alleFotos().flowOn(Dispatchers.IO)

    suspend fun fotoMitId(id: Long): FotoEntity? = withContext(Dispatchers.IO) { dao.fotoMitId(id) }

    suspend fun fotosMitIds(ids: List<Long>): List<FotoEntity> = withContext(Dispatchers.IO) { dao.fotosMitIds(ids) }

    /** Nur DB löschen (Dateien bleiben bestehen). */
    suspend fun loeschenMitIds(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        val ctx = appContext
        val affected = if (ctx != null) {
            ids.mapNotNull { fotoId -> BtbFotoZuordnungSpeicher.btbId(ctx, fotoId) }.distinct()
        } else emptyList()
        val result = dao.loeschenMitIds(ids)
        ctx?.let { app ->
            affected.forEach { btbId ->
                if (BtbOriginalFotoSpeicher.hatOriginal(app, btbId)) {
                    BautagebuchSpeicher.ladenById(app, null, "", btbId)?.let { entry ->
                        BtbDokumentVersionierung.loescheAktuellesAbschlussDokument(app, entry)
                    }
                }
            }
            ids.forEach { BtbFotoZuordnungSpeicher.entfernen(app, it) }
        }
        result
    }

    fun fotosFuerBaustelle(baustelleId: Long): Flow<List<FotoEntity>> = dao.fotosFuerBaustelle(baustelleId).flowOn(Dispatchers.IO)

    suspend fun fotosFuerBaustelleOnce(baustelleId: Long): List<FotoEntity> = withContext(Dispatchers.IO) {
        // Nur während eines PDF-Abschlusslaufs können einzelne Foto-Pfade auf temporäre Kopien
        // mit roter Änderungsmarkierung zeigen. Room-Daten und Original-JPG bleiben unverändert.
        BtbFotoRenderMarkierung.anwenden(dao.fotosFuerBaustelleOnce(baustelleId))
    }

    fun fotosFuerUnterknoten(baustelleId: Long, knotenId: Long): Flow<List<FotoEntity>> =
        dao.fotosFuerUnterknoten(baustelleId, knotenId).flowOn(Dispatchers.IO)

    suspend fun fotosFuerUnterknotenOnce(baustelleId: Long, knotenId: Long): List<FotoEntity> = withContext(Dispatchers.IO) {
        dao.fotosFuerUnterknotenOnce(baustelleId, knotenId)
    }

    /**
     * Ein neu gespeichertes Bild wird – sofern die Baustelle gerade einen modernen BTB geöffnet
     * hat – genau dieser btbId zugeordnet. Ist dieser BTB bereits einmal abgeschlossen worden,
     * ist das bisherige Abschluss-PDF ab diesem neuen Foto nicht mehr gültig.
     */
    suspend fun einfuegen(entity: FotoEntity): Long {
        val id = withContext(Dispatchers.IO) { dao.einfuegen(entity) }
        appContext?.let { ctx ->
            runCatching {
                val activeBtbId = BautagebuchSpeicher.aktiveBtbId(
                    ctx,
                    entity.baustelleId,
                    ""
                )
                if (!activeBtbId.isNullOrBlank()) {
                    BtbFotoZuordnungSpeicher.zuordnen(ctx, id, activeBtbId)
                    if (BtbOriginalFotoSpeicher.hatOriginal(ctx, activeBtbId)) {
                        BautagebuchSpeicher.ladenById(ctx, entity.baustelleId, "", activeBtbId)?.let { entry ->
                            BtbDokumentVersionierung.loescheAktuellesAbschlussDokument(ctx, entry)
                        }
                    }
                }
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
        val result = dao.loeschen(entity)
        ctx?.let { app ->
            if (!btbId.isNullOrBlank() && BtbOriginalFotoSpeicher.hatOriginal(app, btbId)) {
                BautagebuchSpeicher.ladenById(app, entity.baustelleId, "", btbId)?.let { entry ->
                    BtbDokumentVersionierung.loescheAktuellesAbschlussDokument(app, entry)
                }
            }
            BtbFotoZuordnungSpeicher.entfernen(app, entity.id)
        }
        result
    }

    suspend fun alleFirmen(): List<String> = withContext(Dispatchers.IO) { dao.alleFirmen() }

    suspend fun alleKurztexte(): List<String> = withContext(Dispatchers.IO) { dao.alleKurztexte() }
}
