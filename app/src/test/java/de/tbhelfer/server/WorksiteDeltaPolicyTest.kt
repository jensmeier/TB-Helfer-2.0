package de.tbhelfer.server

import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik
import org.junit.Assert.*
import org.junit.Test

class WorksiteDeltaPolicyTest {
    private fun event(site: String?, type: String = "WORKSITE_NODE", operation: String = "UPDATE") =
        Server1DeltaApi.Change(1, site, type, "stable-node", operation, 4)

    @Test fun nodeFeedIncludesStructureAndRetainsBtbMediaTypes() {
        assertTrue(Server1DeltaApi.DEFAULT_ENTITY_TYPES.containsAll(setOf("WORKSITE_NODE", "BTB", "BTB_ENTRY", "BTB_PARTICIPANT", "FILE")))
    }
    @Test fun mixedPageSelectsOnlyBoundStructureWorksitesAndDeduplicates() {
        assertEquals(setOf("mm"), WorksiteDeltaPolicy.nodeWorksites(listOf(event("mm"), event("mm"), event("bg"), event("files", "FILE"), event(null), event("")), setOf("mm", "files")))
    }
    @Test fun deleteUsesTheSameWorksiteScope() {
        assertEquals(setOf("mm"), WorksiteDeltaPolicy.nodeWorksites(listOf(event("mm", operation = "DELETE")), setOf("mm")))
    }
    @Test fun pureBtbPageDoesNotLoadAnyStructure() {
        assertTrue(WorksiteDeltaPolicy.nodeWorksites(listOf(event("mm", "BTB"), event("mm", "FILE")), setOf("mm")).isEmpty())
    }
    @Test fun applyProtectsOfflineNewerAndPendingEdits() {
        assertFalse(WorksiteDeltaPolicy.mayApply(10, null, false, true))
        assertFalse(WorksiteDeltaPolicy.mayApply(11, 10, false, true))
        assertFalse(WorksiteDeltaPolicy.mayApply(10, 10, true, true))
        assertFalse(WorksiteDeltaPolicy.mayApply(0, null, false, false))
        assertTrue(WorksiteDeltaPolicy.mayApply(0, null, false, true))
        assertTrue(WorksiteDeltaPolicy.mayApply(10, 10, false, true))
    }
    @Test fun unknownChangedAndDeletedBasesCannotBeRebasedAutomatically() {
        assertTrue(WorksiteDeltaPolicy.knownBasis(4, 4, false))
        assertFalse(WorksiteDeltaPolicy.knownBasis(null, 4, false))
        assertFalse(WorksiteDeltaPolicy.knownBasis(3, 4, false))
        assertFalse(WorksiteDeltaPolicy.knownBasis(4, 4, true))
        assertFalse(WorksiteDeltaPolicy.knownBasis(null, null, false))
    }
    @Test fun statusSeparatesLocalQueuedTransferringAndConfirmed() {
        assertTrue(BtbSyncPresentation.text(1, null, 0, 1, null).contains("Übertragung ausstehend"))
        assertTrue(BtbSyncPresentation.text(1, 1, 0, 1, null).contains("Übertragung läuft"))
        assertTrue(BtbSyncPresentation.text(0, null, 1, 1, null).contains("Vom Server bestätigt"))
        assertFalse(BtbSyncPresentation.text(0, null, 0, 0, null).contains("Vom Server bestätigt"))
    }
    @Test fun olderWorkerAndOldConfirmationDoNotConfirmNewerSave() {
        assertFalse(BtbSyncPresentation.text(2, 1, 1, 2, null).contains("Vom Server bestätigt"))
        assertFalse(BtbSyncPresentation.text(0, null, 1, 2, null).contains("Vom Server bestätigt"))
        assertTrue(BtbSyncPresentation.text(2, 2, 1, 2, "Versionskonflikt prüfen").contains("Versionskonflikt"))
    }
    private fun node(station: String, objectKey: String = "Gl 4") = BaustellenKnotenEntity(
        id = 41, rootId = 1, parentId = 1, hauptbaustellenName = "Baustelle", altBaustelleId = 9,
        typ = BaustellenKnotenTyp.BAHNHOF, knotenName = "Kurzname", bahnhofName = station, gleisOderWeiche = objectKey
    )
    @Test fun sameTrackAndShortNameRemainDistinctBetweenStations() {
        val mm = BaustellenObjektLogik.unterbaustellenName(node("MM"))!!
        val bg = BaustellenObjektLogik.unterbaustellenName(node("Berchtesgaden"))!!
        assertNotEquals(mm, bg)
        assertTrue(mm.contains("MM") && mm.contains("GL 4") && mm.contains("Kurzname"))
        assertTrue(bg.contains("Berchtesgaden") && bg.contains("GL 4"))
    }
    @Test fun renderingPreservesIdentityAndSwitchKind() {
        val n = node("MM", "W 4")
        val copy = n.copy()
        assertTrue(BaustellenObjektLogik.unterbaustellenName(n)!!.contains("W 4"))
        assertEquals(copy, n)
        assertTrue(BaustellenObjektLogik.unterbaustellenName(n.copy(knotenName = "W 4"))!!.contains("Bahnhof MM"))
        assertTrue(BaustellenObjektLogik.unterbaustellenName(n.copy(bahnhofName = null))!!.contains("Bahnhof unbekannt"))
    }
}
