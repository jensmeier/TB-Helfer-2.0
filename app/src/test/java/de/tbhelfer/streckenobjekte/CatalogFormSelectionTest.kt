package de.tbhelfer.streckenobjekte

import de.tbhelfer.server.CatalogInventoryRow
import de.tbhelfer.server.CatalogInventorySegment
import org.junit.Assert.*
import org.junit.Test

class CatalogFormSelectionTest {
    @Test fun wvbPrefixesDoNotLeakIntoNumber() {
        for (raw in listOf("WVB 1", "WVB:1", "WV 1", "Weichenverbindung 1")) {
            assertEquals("1", WorksiteObjectIdentity.number(raw))
            assertEquals("WEICHENVERBINDUNG", WorksiteObjectIdentity.type(raw))
        }
        assertEquals("WV1", WorksiteObjectIdentity.number("WVB WV1"))
    }

    @Test fun explicitSwitchTypeSurvivesBareNumber() {
        val local = WorksiteObjectIdentity.localKey("117", "WEICHE")
        assertEquals("W 117", local)
        assertTrue(WorksiteObjectIdentity.equivalent(local, "117", "WEICHE"))
    }

    @Test fun explicitWvbTypeSurvivesBareNumber() {
        val local = WorksiteObjectIdentity.localKey("1", "WEICHENVERBINDUNG")
        assertEquals("WV 1", local)
        assertTrue(WorksiteObjectIdentity.equivalent(local, "1", "WEICHENVERBINDUNG"))
    }

    @Test fun explicitTrackTypeIsVisibleToExistingLocalObjectLogic() {
        val local = WorksiteObjectIdentity.localKey("19", "GLEIS")
        assertEquals("Gl 19", local)
        assertEquals(BaustellenObjektArt.GLEIS, BaustellenObjektLogik.artAusKennung(local))
        assertTrue(WorksiteObjectIdentity.equivalent(local, "19", "GLEIS"))
    }

    @Test fun legacyAliasesAreNotNewSyncChanges() {
        assertTrue(WorksiteObjectIdentity.equivalent("WV 1", "WVB:1", null))
        assertTrue(WorksiteObjectIdentity.equivalent("Gl 19", "19", null))
        assertTrue(WorksiteObjectIdentity.equivalent("W 117", "Weiche 117", null))
    }

    @Test fun wirePrefixRemainsRecognizableByPortalEditor() {
        assertEquals("WVB 1", WorksiteObjectIdentity.wireKey("WV 1", "WEICHENVERBINDUNG"))
        assertEquals("W 117", WorksiteObjectIdentity.wireKey("Weiche 117", "WEICHE"))
        assertTrue(WorksiteObjectIdentity.equivalent("WV 1", "WVB 1", "WEICHENVERBINDUNG"))
    }

    @Test fun sameNumberWithDifferentTypeIsDifferentObject() {
        assertFalse(WorksiteObjectIdentity.equivalent("19", "19", "WEICHE"))
        assertFalse(WorksiteObjectIdentity.equivalent("W 1", "1", "WEICHENVERBINDUNG"))
    }

    @Test fun unknownFutureTypeIsNotSilentlyDowngraded() {
        assertEquals("117", WorksiteObjectIdentity.localKey("117", "NEUER_TYP"))
        assertTrue(WorksiteObjectIdentity.equivalent("117", "117", "NEUER_TYP"))
        assertEquals("NEUER_TYP", WorksiteObjectIdentity.outgoingType("117", "117", "NEUER_TYP"))
    }

    private fun row() = CatalogInventoryRow("id", "WEICHENVERBINDUNG", "Verbindung", "5501", "MHO",
        "Holzkirchen", "falsches Gleis", "WVB:1", "KEINE_GEOMETRIE", emptyList(), selectable = true)

    @Test fun inventoryPrefersStableObjectKeyOverTrackKey() {
        assertEquals("1", row().selectionNumber())
        assertEquals("WEICHENVERBINDUNG", row().kind)
    }

    @Test fun referenceAssetsAreNotSelectableAsTrack() {
        assertNull(row().copy(selectable = false).selectionNumber())
        assertNull(row().copy(kind = "GVP").selectionNumber())
    }

    @Test fun onlyReliableCompleteRangesAreSelectable() {
        assertNull(CatalogInventorySegment("UNPLAUSIBEL", 0.0, 9000.0).rangeMeters())
        assertNull(CatalogInventorySegment("MANUELL", null, 1000.0).rangeMeters())
        assertNull(CatalogInventorySegment("AUTOMATISCH", Double.NaN, 1000.0).rangeMeters())
        assertEquals(0.0 to 2000.0, CatalogInventorySegment("AUTOMATISCH", 0.0, 2000.0).rangeMeters())
    }

    @Test fun selectingOneSegmentDoesNotCombineDisjointSegments() {
        val a = CatalogInventorySegment("AUTOMATISCH", 1000.0, 2000.0)
        val b = CatalogInventorySegment("MANUELL", 4000.0, 5000.0)
        assertEquals(1000.0 to 2000.0, a.rangeMeters())
        assertEquals(4000.0 to 5000.0, b.rangeMeters())
    }
}
