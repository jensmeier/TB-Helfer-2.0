package de.tbhelfer.datenbank

import org.junit.Assert.*
import org.junit.Test

class FotoMetadatenTest {
    private fun foto() = FotoEntity(
        baustelleId = 1L, dateipfad = "original.jpg", gpsLat = 52.0, gpsLon = 13.0,
        firma = "Firma", bildtyp = Bildtyp.FOTODOKUMENTATION, kurztext = "Arbeit",
        erstelltAmEpochMs = 123456L
    )

    @Test fun legacyUsesOriginalCoordinates() {
        val f = foto()
        assertEquals(52.0, f.gpsLatEffektiv!!, 0.0)
        assertEquals(13.0, f.gpsLonEffektiv!!, 0.0)
        assertEquals(123456L, f.aufnahmeEpochEffektiv)
    }

    @Test fun clearedEffectiveGpsNeverResurrectsOriginal() {
        val f = foto().apply { btbMetadaten = AufnahmeMetadaten(null, null, 123456L, null) }
        assertNull(f.gpsLatEffektiv)
        assertNull(f.gpsLonEffektiv)
        assertEquals("–", f.gpsAnzeige)
        assertEquals(52.0, f.gpsLat, 0.0)
    }

    @Test fun explicitZeroCoordinatesRemainValid() {
        val f = foto().apply { btbMetadaten = AufnahmeMetadaten(0.0, 0.0, 0L, 0.0) }
        assertEquals(0.0, f.gpsLatEffektiv!!, 0.0)
        assertEquals(0.0, f.gpsLonEffektiv!!, 0.0)
        assertEquals(0L, f.aufnahmeEpochEffektiv)
        assertEquals(0.0, f.gpsAccuracyEffektiv!!, 0.0)
    }

    @Test fun unknownCaptureTimeDoesNotUseOriginalTime() {
        val f = foto().apply { btbMetadaten = AufnahmeMetadaten(null, null, null, null) }
        assertNull(f.aufnahmeEpochEffektiv)
        assertEquals(123456L, f.erstelltAmEpochMs)
    }

    @Test fun renderCopyPreservesEffectiveMetadata() {
        val original = foto().apply { btbMetadaten = AufnahmeMetadaten(51.0, 12.0, 654321L, 4.0) }
        val copy = original.copy(dateipfad = "render.jpg").mitMetadatenVon(original)
        assertEquals(original.btbMetadaten, copy.btbMetadaten)
        assertEquals("original.jpg", original.dateipfad)
        assertEquals(51.0, copy.gpsLatEffektiv!!, 0.0)
    }
}
