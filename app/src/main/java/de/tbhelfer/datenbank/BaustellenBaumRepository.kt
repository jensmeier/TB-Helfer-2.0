package de.tbhelfer.datenbank

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import de.tbhelfer.server.StructureSyncStore

class BaustellenBaumRepository(
    private val knotDao: BaustellenKnotenDao,
    private val altRepo: BaustellenRepository,
    private val onStructureChanged: (Long) -> Unit = {}
) {

    fun alleHauptbaustellen(): Flow<List<BaustellenKnotenEntity>> = knotDao.alleHauptbaustellen().flowOn(Dispatchers.IO)

    fun alleKnotenFlow(): Flow<List<BaustellenKnotenEntity>> = knotDao.alleKnotenFlow().flowOn(Dispatchers.IO)

    fun alleKnotenZuRoot(rootId: Long): Flow<List<BaustellenKnotenEntity>> =
        knotDao.alleKnotenZuRootFlow(rootId).flowOn(Dispatchers.IO)

    suspend fun alleKnotenZuRootSofort(rootId: Long): List<BaustellenKnotenEntity> =
        withContext(Dispatchers.IO) { knotDao.alleKnotenZuRootSofort(rootId) }

    fun kinderVon(parentId: Long): Flow<List<BaustellenKnotenEntity>> = knotDao.kinderVon(parentId).flowOn(Dispatchers.IO)

    suspend fun knotenMitId(id: Long): BaustellenKnotenEntity? = withContext(Dispatchers.IO) { knotDao.laden(id) }

    fun knotenMitIdFlow(id: Long): Flow<BaustellenKnotenEntity?> = knotDao.ladenFlow(id).flowOn(Dispatchers.IO)

    /** Hauptbaustelle finden (kanonisch, d.h. trim + Mehrfach-Leerzeichen + ignoreCase). */
    suspend fun hauptFindenKanonisch(name: String): BaustellenKnotenEntity? {
        return withContext(Dispatchers.IO) {
            val target = name.kanonisch()
            if (target.isBlank()) return@withContext null
            knotDao.alleHauptbaustellenSofort()
                .firstOrNull { it.hauptbaustellenName.kanonisch() == target }
        }
    }

    /** Counts für Dialog/Anzeige (Unterbaustellen + Gesamt). */
    suspend fun countsFuerHaupt(rootId: Long): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            val alle = knotDao.alleKnotenZuRootSofort(rootId)
            val unter = alle.count { it.parentId == rootId }
            val gesamt = (alle.count { it.rootId == rootId } - 1).coerceAtLeast(0)
            unter to gesamt
        }
    }

    /** Breadcrumb-Pfad (Root -> ... -> Start). */
    suspend fun pfadVonRootBis(startId: Long): List<BaustellenKnotenEntity> {
        return withContext(Dispatchers.IO) { knotDao.pfadBisRoot(startId).reversed() }
    }

    /** Alias (älterer Name) */
    suspend fun pfadBisRoot(startId: Long): List<BaustellenKnotenEntity> = pfadVonRootBis(startId)

    /**
     * Fallback: Wenn nur die alte BaustelleId bekannt ist (legacy), aber kein Haupt-Knoten gesetzt wurde,
     * können wir die zugehörige Hauptbaustelle über altBaustelleId finden.
     */
    suspend fun hauptNachAltBaustelleId(altBaustelleId: Long): BaustellenKnotenEntity? {
        return withContext(Dispatchers.IO) {
            knotDao.alleHauptbaustellenSofort().firstOrNull { it.altBaustelleId == altBaustelleId }
        }
    }

    /**
     * Hauptbaustelle holen oder erstellen.
     * Merge/Zusammenführen passiert NUR bei EXAKTEM Namen (nach trim()).
     */
    suspend fun hauptErstellenOderZusammenfuehren(baustellenname: String): BaustellenKnotenEntity {
        return withContext(Dispatchers.IO) {
            val name = baustellenname.trim()
            require(name.isNotBlank()) { "Baustellenname ist Pflicht." }

            val vorhanden = knotDao.hauptNachExaktemNamen(name)
            if (vorhanden != null) return@withContext vorhanden

            val altId = altRepo.hauptbaustelleAltHolenOderErstellen(name)

            // Erst insert mit rootId=0, danach rootId auf id setzen.
            val vorlaeufig = BaustellenKnotenEntity(
                rootId = 0,
                parentId = null,
                hauptbaustellenName = name,
                knotenName = null,
                altBaustelleId = altId,
                typ = BaustellenKnotenTyp.HAUPT
            )

            val id = knotDao.einfuegen(vorlaeufig)
            val fix = vorlaeufig.copy(id = id, rootId = id)
            knotDao.aktualisieren(fix)
            fix
        }
    }

    private fun String.kanonisch(): String {
        // trim + Mehrfachleerzeichen zusammenziehen + lowercase
        return trim().replace(Regex("\\s+"), " ").lowercase()
    }

    /**
     * Neue Baustelle laut Vorgabe:
     * - Hauptbaustellenname (Freitext)
     * - danach Unterbaustelle/Objekt unter Haupt (Freie Strecke / Bahnhof / Allgemein)
     */
    suspend fun neueBaustelleUnterHaupt(
        hauptbaustellenName: String,
        knotenName: String?,
        typ: BaustellenKnotenTyp,
        streckenkennung: String?,
        kmVon: Double?,
        kmBis: Double?,
        bahnhofName: String?,
        gleisOderWeiche: String?
    ): BaustellenKnotenEntity {
        val haupt = hauptErstellenOderZusammenfuehren(hauptbaustellenName)
        return unterknotenErstellen(
            parent = haupt,
            knotenName = knotenName,
            typ = typ,
            streckenkennung = streckenkennung,
            kmVon = kmVon,
            kmBis = kmBis,
            bahnhofName = bahnhofName,
            gleisOderWeiche = gleisOderWeiche
        )
    }

    suspend fun unterknotenErstellen(
        parentId: Long,
        knotenName: String?,
        typ: BaustellenKnotenTyp,
        streckenkennung: String?,
        kmVon: Double?,
        kmBis: Double?,
        bahnhofName: String?,
        gleisOderWeiche: String?
    ): BaustellenKnotenEntity {
        val parent = withContext(Dispatchers.IO) { knotDao.laden(parentId) } ?: error("Parent-Knoten nicht gefunden")
        return unterknotenErstellen(parent, knotenName, typ, streckenkennung, kmVon, kmBis, bahnhofName, gleisOderWeiche)
    }

    suspend fun unterknotenErstellen(
        parent: BaustellenKnotenEntity,
        knotenName: String?,
        typ: BaustellenKnotenTyp,
        streckenkennung: String?,
        kmVon: Double?,
        kmBis: Double?,
        bahnhofName: String?,
        gleisOderWeiche: String?
    ): BaustellenKnotenEntity {
        // DAO-Arbeit auf IO
        return withContext(Dispatchers.IO) { StructureSyncStore.mutationMutex.withLock {
        require(parent.typ != BaustellenKnotenTyp.HAUPT || parent.parentId == null) {
            "Parent-Knoten ungültig."
        }

        val rootId = if (parent.parentId == null && parent.typ == BaustellenKnotenTyp.HAUPT) parent.id else parent.rootId
        val hauptName = parent.hauptbaustellenName

        val optionalName = knotenName?.trim().takeUnless { it.isNullOrBlank() }

        val knoten = when (typ) {
            BaustellenKnotenTyp.FREIE_STRECKE -> {
                val kennung = streckenkennung?.trim().orEmpty()
                require(kennung.matches(Regex("\\d{4}-[0-2]"))) {
                    "Freie Strecke: Kennung muss XXXX-X sein (X=0..2)."
                }
                require(kmVon != null && kmBis != null) { "Freie Strecke: Km von/bis sind Pflicht." }
                require(kmVon <= kmBis) { "Km von muss kleiner/gleich Km bis sein." }

                BaustellenKnotenEntity(
                    rootId = rootId,
                    parentId = parent.id,
                    hauptbaustellenName = hauptName,
                    knotenName = optionalName,
                    altBaustelleId = parent.altBaustelleId,
                    typ = BaustellenKnotenTyp.FREIE_STRECKE,
                    streckenkennung = kennung,
                    kmVon = kmVon,
                    kmBis = kmBis
                )
            }

            BaustellenKnotenTyp.BAHNHOF -> {
                val bhf = bahnhofName?.trim().orEmpty()
                val glw = gleisOderWeiche?.trim().orEmpty()
                require(bhf.isNotBlank()) { "Bahnhofname ist Pflicht." }
                require(glw.isNotBlank()) { "Gleis/Weiche ist Pflicht." }

                val von = kmVon
                val bis = kmBis ?: kmVon

                if (von != null && bis != null) {
                    require(von <= bis) { "Km von muss kleiner/gleich Km bis sein." }
                }

                BaustellenKnotenEntity(
                    rootId = rootId,
                    parentId = parent.id,
                    hauptbaustellenName = hauptName,
                    knotenName = optionalName,
                    altBaustelleId = parent.altBaustelleId,
                    typ = BaustellenKnotenTyp.BAHNHOF,
                    bahnhofName = bhf,
                    gleisOderWeiche = glw,
                    kmVon = von,
                    kmBis = bis
                )
            }

            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> {
                // Kein extra Feld mehr: Name ist optional (kann leer sein).
                // Wenn leer, wird der Knoten später über Typ/Km-Bereich erkennbar.
                val objName = optionalName

                val von = kmVon
                val bis = kmBis ?: kmVon

                if (von != null && bis != null) {
                    require(von <= bis) { "Km von muss kleiner/gleich Km bis sein." }
                }

                BaustellenKnotenEntity(
                    rootId = rootId,
                    parentId = parent.id,
                    hauptbaustellenName = hauptName,
                    knotenName = objName,
                    altBaustelleId = parent.altBaustelleId,
                    typ = BaustellenKnotenTyp.ALLGEMEIN_OBJEKT,
                    kmVon = von,
                    kmBis = bis
                )
            }

            BaustellenKnotenTyp.HAUPT -> error("Unterknoten darf nicht HAUPT sein")
        }

        onStructureChanged(rootId)
        val id = knotDao.einfuegen(knoten)
        knoten.copy(id = id)
        } }
    }

    suspend fun loescheTeilbaum(knotenId: Long) {
        withContext(Dispatchers.IO) { StructureSyncStore.mutationMutex.withLock {
            val node = knotDao.laden(knotenId) ?: return@withLock
            if (node.typ != BaustellenKnotenTyp.HAUPT) onStructureChanged(node.rootId)
            knotDao.loescheTeilbaum(knotenId)
        } }
        // Hinweis: Die alte BaustelleEntity löschen wir hier NICHT automatisch,
        // damit Streckenpunkte/Fotos nicht aus Versehen verschwinden.
    }

    /** Hauptbaustelle selbst umbenennen (inkl. aller Knoten derselben Root). */
    suspend fun hauptbaustelleUmbenennen(rootId: Long, neuerName: String) {
        withContext(Dispatchers.IO) {
            val root = knotDao.laden(rootId) ?: error("Hauptbaustelle nicht gefunden")
            require(root.typ == BaustellenKnotenTyp.HAUPT && root.parentId == null) {
                "Nur Hauptbaustellen können hier bearbeitet werden."
            }

            val cleanName = neuerName.trim()
            require(cleanName.isNotBlank()) { "Baustellenname ist Pflicht." }

            val konflikt = knotDao.alleHauptbaustellenSofort()
                .firstOrNull { it.id != root.id && it.hauptbaustellenName.kanonisch() == cleanName.kanonisch() }
            require(konflikt == null) { "Eine Hauptbaustelle mit diesem Namen existiert bereits." }

            val alle = knotDao.alleKnotenZuRootSofort(root.id)
            alle.forEach { knoten ->
                knotDao.aktualisieren(knoten.copy(hauptbaustellenName = cleanName))
            }

            altRepo.hauptbaustelleNameAktualisieren(root.altBaustelleId, cleanName)
        }
    }

    /**
     * Vollständiges Bearbeiten eines Unterknotens.
     * HAUPT wird hier bewusst nicht geändert (Root wird in dieser UI nicht bearbeitet).
     */
    suspend fun knotenAktualisieren(
        knotenId: Long,
        knotenName: String?,
        typ: BaustellenKnotenTyp,
        streckenkennung: String?,
        kmVon: Double?,
        kmBis: Double?,
        bahnhofName: String?,
        gleisOderWeiche: String?
    ) {
        withContext(Dispatchers.IO) { StructureSyncStore.mutationMutex.withLock {
            val alt = knotDao.laden(knotenId) ?: error("Knoten nicht gefunden")
            require(alt.typ != BaustellenKnotenTyp.HAUPT) { "Hauptbaustelle hier nicht bearbeitbar." }

            val cleanName = knotenName?.trim()?.takeIf { it.isNotBlank() }

            val neu = when (typ) {
                BaustellenKnotenTyp.FREIE_STRECKE -> {
                    val kennung = streckenkennung?.trim().orEmpty()
                    require(kennung.matches(Regex("\\d{4}-[0-2]"))) {
                        "Freie Strecke: Kennung muss XXXX-X sein (X=0..2)."
                    }
                    require(kmVon != null && kmBis != null) { "Freie Strecke: Km von/bis sind Pflicht." }
                    require(kmVon <= kmBis) { "Km von muss kleiner/gleich Km bis sein." }

                    alt.copy(
                        knotenName = cleanName,
                        typ = BaustellenKnotenTyp.FREIE_STRECKE,
                        streckenkennung = kennung,
                        kmVon = kmVon,
                        kmBis = kmBis,
                        bahnhofName = null,
                        gleisOderWeiche = null
                    )
                }

                BaustellenKnotenTyp.BAHNHOF -> {
                    val bhf = bahnhofName?.trim().orEmpty()
                    val glw = gleisOderWeiche?.trim().orEmpty()
                    require(bhf.isNotBlank()) { "Bahnhofname ist Pflicht." }
                    require(glw.isNotBlank()) { "Gleis/Weiche ist Pflicht." }

                    val von = kmVon
                    val bis = kmBis ?: kmVon
                    if (von != null && bis != null) {
                        require(von <= bis) { "Km von muss kleiner/gleich Km bis sein." }
                    }

                    alt.copy(
                        knotenName = cleanName,
                        typ = BaustellenKnotenTyp.BAHNHOF,
                        streckenkennung = null,
                        kmVon = von,
                        kmBis = bis,
                        bahnhofName = bhf,
                        gleisOderWeiche = glw
                    )
                }

                BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> {
                    val von = kmVon
                    val bis = kmBis ?: kmVon
                    if (von != null && bis != null) {
                        require(von <= bis) { "Km von muss kleiner/gleich Km bis sein." }
                    }

                    alt.copy(
                        knotenName = cleanName,
                        typ = BaustellenKnotenTyp.ALLGEMEIN_OBJEKT,
                        streckenkennung = null,
                        kmVon = von,
                        kmBis = bis,
                        bahnhofName = null,
                        gleisOderWeiche = null
                    )
                }

                BaustellenKnotenTyp.HAUPT -> error("Nicht erlaubt")
            }

            if (alt != neu) onStructureChanged(alt.rootId)
            knotDao.aktualisieren(neu)
        } }
    }

    /** Rückwärtskompatibel: Bearbeiten nur des optionalen Namens. */
    suspend fun knotenUmbenennen(knotenId: Long, neuerName: String?) {
        withContext(Dispatchers.IO) { StructureSyncStore.mutationMutex.withLock {
            val k = knotDao.laden(knotenId) ?: return@withLock
            if (k.typ == BaustellenKnotenTyp.HAUPT) return@withLock

            val clean = neuerName?.trim()?.takeIf { it.isNotBlank() }
            if (k.knotenName != clean) onStructureChanged(k.rootId)
            knotDao.aktualisieren(k.copy(knotenName = clean))
        } }
    }

    // Alias
    suspend fun loeschenTeilbaum(knotenId: Long) = loescheTeilbaum(knotenId)
}

