package de.tbhelfer.server

/** Pure decisions shared by the actual delta and structural apply paths. */
object WorksiteDeltaPolicy {
    fun nodeWorksites(changes: List<Server1DeltaApi.Change>, boundIds: Set<String>): Set<String> = changes
        .filter { it.entityType == "WORKSITE_NODE" }
        .mapNotNull { it.worksiteId?.takeIf(String::isNotBlank) }.toSet().intersect(boundIds)

    fun mayApply(dirty: Long, expected: Long?, pending: Boolean, unchanged: Boolean): Boolean =
        (dirty == 0L || dirty == expected) && !pending && unchanged

    fun knownBasis(known: Long?, remote: Long?, deleted: Boolean): Boolean =
        !deleted && remote != null && known == remote
}

object BtbSyncPresentation {
    fun text(dirty: Long, running: Long?, confirmed: Long, latest: Long, problem: String?): String {
        val state = when {
            dirty != 0L && problem != null -> problem
            dirty != 0L && running == dirty -> "Übertragung läuft"
            dirty != 0L -> "Übertragung ausstehend"
            running != null -> "Übertragung läuft"
            confirmed > 0L && confirmed == latest -> "Vom Server bestätigt"
            else -> "Serverbestätigung ausstehend"
        }
        return "Lokal gespeichert · $state"
    }
}
