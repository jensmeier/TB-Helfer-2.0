package de.tbhelfer.datenbank

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class FotoRepository(
    private val dao: FotoDao,
    context: Context
) {
    private val appContext = context.applicationContext

    fun alleFotos(): Flow<List<FotoEntity>> = dao.alleFotos().flowOn(Dispatchers.IO)

    suspend fun fotoMitId(id: Long): FotoEntity? = withContext(Dispatchers.IO) { dao.fotoMitId(id) }

    suspend fun fotosMitIds(ids: List<Long>): List<FotoEntity> = withContext(Dispatchers.IO) { dao.fotosMitIds(ids) }

    /** Nur DB löschen (Dateien bleiben bestehen). */
    suspend fun loeschenMitIds(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        val anzahl = dao.loeschenMitIds(ids)
        if (anzahl > 0) FotoVermessungsSnapshotSpeicher.loeschen(appContext, ids)
        anzahl
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

    suspend fun einfuegen(entity: FotoEntity): Long = withContext(Dispatchers.IO) {
        val pending = FotoVermessungsSnapshotSpeicher.passendesPending(appContext, entity)
        val manuell = entity.km?.let {
            FotoVermessungsSnapshotSpeicher.kmIstManuell(entity, pending)
        } ?: false
        val finalEntity = entity.copy(kmManuell = entity.km != null && manuell)
        val id = dao.einfuegen(finalEntity)
        FotoVermessungsSnapshotSpeicher.speichereFuerFoto(appContext, id, finalEntity, pending)
        id
    }

    /** Notiz für mehrere Fotos setzen (null/leer = zurück auf Kurztext). */
    suspend fun updateNotizMitIds(ids: List<Long>, notiz: String?): Int = withContext(Dispatchers.IO) {
        dao.updateNotizMitIds(ids, notiz)
    }

    suspend fun loeschen(entity: FotoEntity): Int = withContext(Dispatchers.IO) {
        val anzahl = dao.loeschen(entity)
        if (anzahl > 0) FotoVermessungsSnapshotSpeicher.loeschen(appContext, entity.id)
        anzahl
    }

    suspend fun alleFirmen(): List<String> = withContext(Dispatchers.IO) { dao.alleFirmen() }

    suspend fun alleKurztexte(): List<String> = withContext(Dispatchers.IO) { dao.alleKurztexte() }
}
