package de.tbhelfer.datenbank

import android.content.Context
import de.tbhelfer.server.SofortSync
import de.tbhelfer.ui.bautagebuch.BautagebuchSpeicher
import de.tbhelfer.ui.bautagebuch.BtbFotoZuordnungSpeicher
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
        val result = dao.loeschenMitIds(ids)
        appContext?.let { ctx -> ids.forEach { BtbFotoZuordnungSpeicher.entfernen(ctx, it) } }
        result
    }

    fun fotosFuerBaustelle(baustelleId: Long): Flow<List<FotoEntity>> = dao.fotosFuerBaustelle(baustelleId).flowOn(Dispatchers.IO)

    suspend fun fotosFuerBaustelleOnce(baustelleId: Long): List<FotoEntity> = withContext(Dispatchers.IO) {
        dao.fotosFuerBaustelleOnce(baustelleId)
    }

    fun fotosFuerUnterknoten(baustelleId: Long, knotenId: Long): Flow<List<FotoEntity>> =
        dao.fotosFuerUnterknoten(baustelleId, knotenId).flowOn(Dispatchers.IO)

    suspend fun fotosFuerUnterknotenOnce(baustelleId: Long, knotenId: Long): List<FotoEntity> = withContext(Dispatchers.IO) {
        dao.fotosFuerUnterknotenOnce(baustelleId, knotenId)
    }

    /**
     * Ein neu gespeichertes Bild wird – sofern die Baustelle gerade einen modernen BTB geöffnet
     * hat – genau dieser btbId zugeordnet. Die Zuordnung liegt separat vom bestehenden Room-Schema,
     * deshalb ist hierfür keine Foto-DB-Migration nötig.
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
        val result = dao.loeschen(entity)
        appContext?.let { BtbFotoZuordnungSpeicher.entfernen(it, entity.id) }
        result
    }

    suspend fun alleFirmen(): List<String> = withContext(Dispatchers.IO) { dao.alleFirmen() }

    suspend fun alleKurztexte(): List<String> = withContext(Dispatchers.IO) { dao.alleKurztexte() }
}
