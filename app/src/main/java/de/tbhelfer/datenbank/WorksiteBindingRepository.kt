package de.tbhelfer.datenbank

import androidx.room.withTransaction

/**
 * Verknüpft eine lokale Hauptbaustelle eindeutig mit einer Server-Baustelle.
 *
 * Wichtig: Bereits vorhandene lokale Baustellen werden absichtlich NICHT allein anhand ihres
 * Namens automatisch mit einer Server-Baustelle zusammengeführt. Zwei Baustellen können gleich
 * heißen; in diesem Fall bleibt die bewusste manuelle Auswahl erhalten.
 *
 * Neu beigetretene Server-Baustellen erhalten aber automatisch einen lokalen HAUPT-Knoten, wenn
 * auf dem Gerät noch keine gleichnamige Hauptbaustelle existiert. Damit können bereits geladene
 * BTB/Mängel/Abnahmen sofort in den normalen lokalen Fachspeicher übernommen werden, ohne dass
 * der Helfer zuerst eine fachlich fremde vorhandene Baustelle auswählen muss.
 */
class WorksiteBindingRepository(
    private val syncDatabase: SyncDatenbank,
    private val localWorksiteDao: BaustellenKnotenDao,
    private val localWorksiteTree: BaustellenBaumRepository
) {
    private val dao: SyncDao get() = syncDatabase.syncDao()

    data class BoundContext(
        val context: SyncWorksiteContextEntity,
        val binding: SyncWorksiteBindingEntity?,
        val localWorksite: BaustellenKnotenEntity?
    )

    suspend fun contextsWithBindings(): List<BoundContext> {
        val contexts = dao.worksiteContexts()
        bootstrapMissingLocalRoots(contexts)
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

    /**
     * Legt für eine frisch beigetretene Server-Baustelle einen lokalen Container an.
     *
     * Sicherheitsregel: Sobald bereits eine gleichnamige lokale Hauptbaustelle existiert, wird
     * weder automatisch gebunden noch eine Dublette erzeugt. Dann entscheidet der Benutzer im
     * bestehenden Zuordnungsdialog bewusst, welche lokale Baustelle gemeint ist.
     */
    private suspend fun bootstrapMissingLocalRoots(contexts: List<SyncWorksiteContextEntity>) {
        if (contexts.isEmpty()) return

        var bindings = dao.worksiteBindings()
        val boundServerIds = bindings.mapTo(hashSetOf()) { it.worksiteServerId }
        var localRoots = localWorksiteDao.alleHauptbaustellenSofort()

        for (context in contexts) {
            if (context.worksiteServerId in boundServerIds) continue

            val serverName = context.name?.trim().orEmpty()
            if (serverName.isBlank()) continue

            val canonical = serverName.canonicalWorksiteName()
            val sameNameExists = localRoots.any {
                it.hauptbaustellenName.canonicalWorksiteName() == canonical
            }
            if (sameNameExists) continue

            val created = localWorksiteTree.hauptErstellenOderZusammenfuehren(serverName)
            val alreadyBound = dao.worksiteBindingForLocalRoot(created.id)
            if (alreadyBound == null) {
                bind(created.id, context.worksiteServerId)
                boundServerIds += context.worksiteServerId
                bindings = dao.worksiteBindings()
            }
            localRoots = localWorksiteDao.alleHauptbaustellenSofort()
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

    private fun String.canonicalWorksiteName(): String =
        trim().replace(Regex("\\s+"), " ").lowercase()
}
