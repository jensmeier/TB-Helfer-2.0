package de.tbhelfer.datenbank

import androidx.room.withTransaction

/**
 * Verknüpft eine lokale Hauptbaustelle eindeutig mit einer Server-Baustelle.
 *
 * Wichtig: Es wird absichtlich NICHT nach Namen automatisch zusammengeführt. Zwei Baustellen
 * können gleich heißen; deshalb muss die Verknüpfung einmal bewusst gewählt werden.
 */
class WorksiteBindingRepository(
    private val syncDatabase: SyncDatenbank,
    private val localWorksiteDao: BaustellenKnotenDao
) {
    private val dao: SyncDao get() = syncDatabase.syncDao()

    data class BoundContext(
        val context: SyncWorksiteContextEntity,
        val binding: SyncWorksiteBindingEntity?,
        val localWorksite: BaustellenKnotenEntity?
    )

    suspend fun contextsWithBindings(): List<BoundContext> {
        val contexts = dao.worksiteContexts()
        val bindingsByServer = dao.worksiteBindings().associateBy { it.worksiteServerId }
        return contexts.map { context ->
            val binding = bindingsByServer[context.worksiteServerId]
            BoundContext(
                context = context,
                binding = binding,
                localWorksite = binding?.localRootKnotenId?.let { localWorksiteDao.laden(it) }
            )
        }
    }

    suspend fun localMainWorksites(): List<BaustellenKnotenEntity> =
        localWorksiteDao.alleHauptbaustellenSofort()

    suspend fun bindingForLocalRoot(localRootKnotenId: Long): SyncWorksiteBindingEntity? =
        dao.worksiteBindingForLocalRoot(localRootKnotenId)

    suspend fun contextForLocalRoot(localRootKnotenId: Long): SyncWorksiteContextEntity? {
        val binding = dao.worksiteBindingForLocalRoot(localRootKnotenId) ?: return null
        return dao.worksiteContext(binding.worksiteServerId)
    }

    suspend fun bind(localRootKnotenId: Long, worksiteServerId: String) {
        val local = localWorksiteDao.laden(localRootKnotenId)
            ?: throw IllegalArgumentException("Lokale Baustelle wurde nicht gefunden.")
        if (local.parentId != null || local.typ != BaustellenKnotenTyp.HAUPT) {
            throw IllegalArgumentException("Nur eine Hauptbaustelle kann mit dem Server verbunden werden.")
        }
        dao.worksiteContext(worksiteServerId)
            ?: throw IllegalArgumentException("Server-Baustelle ist noch nicht auf dem Gerät bekannt.")

        syncDatabase.withTransaction {
            // 1:1 auf diesem Gerät: keine lokale Baustelle und keine Server-Baustelle doppelt binden.
            dao.deleteWorksiteBindingForLocalRoot(localRootKnotenId)
            dao.deleteWorksiteBindingForServer(worksiteServerId)
            dao.upsertWorksiteBinding(
                SyncWorksiteBindingEntity(
                    localRootKnotenId = localRootKnotenId,
                    worksiteServerId = worksiteServerId
                )
            )
        }
    }

    suspend fun unbindServer(worksiteServerId: String) {
        dao.deleteWorksiteBindingForServer(worksiteServerId)
    }

    suspend fun unbindLocal(localRootKnotenId: Long) {
        dao.deleteWorksiteBindingForLocalRoot(localRootKnotenId)
    }
}
