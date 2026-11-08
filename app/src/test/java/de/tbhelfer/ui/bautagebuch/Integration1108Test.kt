package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class Integration1108Test {
    @Test fun exportPrefersAvailableOriginalForSameAttachment() {
        val result = LocalExportManifest.select(listOf(
            LocalExportManifest.Photo(1, "a", "/preview", true, true),
            LocalExportManifest.Photo(2, "a", "/original", true, false),
            LocalExportManifest.Photo(3, "b", "/missing", false, false)))
        assertEquals(listOf(2L), result.primaryIds)
        assertEquals(mapOf(1L to 2L, 2L to 2L), result.primaryById)
        assertEquals(1, result.missing)
    }
    @Test fun exportKeepsDistinctAttachmentsAndUsesPreviewWhenOriginalMissing() {
        val result = LocalExportManifest.select(listOf(
            LocalExportManifest.Photo(1, "a", "/missing", false, false),
            LocalExportManifest.Photo(2, "a", "/preview", true, true),
            LocalExportManifest.Photo(3, "b", "/other", true, false),
            LocalExportManifest.Photo(4, null, "/legacy", true, false),
            LocalExportManifest.Photo(5, null, "/legacy", true, false)))
        assertEquals(listOf(2L, 3L, 4L), result.primaryIds)
        assertEquals(2L, result.primaryById[1L])
        assertEquals(4L, result.primaryById[5L])
        assertEquals(0, result.missing)
    }
    @Test fun weatherRejectsWrongDayAndInvalidCoordinatesButPreservesZero() {
        val time = LocalDateTime.of(2026, 11, 8, 7, 0)
        assertEquals(0, BtbWeatherPolicy.nearestIndex(listOf(time), time.plusMinutes(30)))
        assertNull(BtbWeatherPolicy.nearestIndex(listOf(time), time.plusDays(1)))
        assertEquals(0.0 to 0.0, BtbWeatherPolicy.coordinates(0.0, 0.0))
        assertNull(BtbWeatherPolicy.coordinates(Double.NaN, 11.0))
        assertNull(BtbWeatherPolicy.coordinates(91.0, 11.0))
    }
    @Test fun weatherDoesNotInventMissingRainOrClouds() {
        assertNull(BtbWeatherPolicy.rain(null, null))
        assertNull(BtbWeatherPolicy.cloud(null))
        assertEquals("trocken", BtbWeatherPolicy.rain(61, 0.0))
        assertEquals("Regen (leicht)", BtbWeatherPolicy.rain(61, null))
        assertEquals("wolkig", BtbWeatherPolicy.cloud(65))
    }
}
