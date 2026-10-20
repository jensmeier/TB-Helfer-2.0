package de.tbhelfer.server

import org.junit.Assert.*
import org.junit.Test

class CatalogInventoryTest {
    private fun row(segments: List<CatalogInventorySegment> = emptyList()) = CatalogInventoryRow(
        "axis-id", "WEICHE", "Weiche 117", "5501", "MHO", "Holzkirchen", "19", "W:117",
        "KEINE_GEOMETRIE", segments
    )

    @Test fun zeroIsValidAndMetersBecomeKilometers() {
        assertEquals("km 0,000–2,000 (automatisch)", CatalogInventorySegment("AUTOMATISCH", 0.0, 2000.0).description())
    }

    @Test fun separateSegmentsDoNotBridgeMissingKilometers() {
        val text = row(listOf(CatalogInventorySegment("AUTOMATISCH", 1000.0, 2000.0),
            CatalogInventorySegment("MANUELL", 4000.0, 5000.0))).details()
        assertTrue(text.contains("Abschnitt 1: km 1,000–2,000"))
        assertTrue(text.contains("Abschnitt 2: km 4,000–5,000"))
        assertFalse(text.contains("km 1,000–5,000"))
    }

    @Test fun unreliableStatusNeverDisplaysNumericRange() {
        assertEquals("Daten vorhanden, KM-Zuordnung nicht belastbar.",
            CatalogInventorySegment("UNPLAUSIBEL", 1.0, 9000.0).description())
        assertFalse(CatalogInventorySegment("NEUER_STATUS", 0.0, 1000.0).description().contains("0,000"))
    }

    @Test fun missingAndNonfiniteBoundariesAreUnknown() {
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals("Daten vorhanden, ohne KM-Zuordnung.",
                CatalogInventorySegment("AUTOMATISCH", value, 1000.0).description())
        }
    }

    @Test fun reversedManualRangeRetainsManualQuality() {
        assertEquals("km 4,000–5,000 (manuell)", CatalogInventorySegment("MANUELL", 5000.0, 4000.0).description())
    }

    @Test fun searchUsesObjectAndStationIdentity() {
        val item = row()
        for (query in listOf(" w:117 ", "HOLZKIRCHEN", "mho", "5501", "19", "weiche")) assertTrue(item.matches(query))
        assertFalse(item.matches("anderer Bahnhof"))
        assertEquals("axis-id", item.id)
        assertTrue(item.details().contains("Objekt: W:117"))
    }

    @Test fun missingGeometryIsNotAnEmptyInventory() {
        assertTrue(row().details().contains("Keine Geometrie vorhanden."))
        assertEquals("1 Weiche", CatalogInventory(listOf(row())).summary())
        assertEquals("2 Weichen", CatalogInventory(listOf(row(), row().copy(id = "second"))).summary())
    }

    @Test fun emptyInventoryHasExplicitMessage() {
        assertEquals("Keine gemeinsamen Vermessungsdaten vorhanden.", CatalogInventory(emptyList()).summary())
    }
}
