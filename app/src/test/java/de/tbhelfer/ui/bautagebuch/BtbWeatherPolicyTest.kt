package de.tbhelfer.ui.bautagebuch

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class BtbWeatherPolicyTest {
    @Test fun sampleHoursIncludeActualShiftStartAndEnd() {
        assertEquals(listOf("07:00", "11:30", "16:00"), BtbWeatherPolicy.shiftSlots("07:00", "16:00"))
        assertEquals(listOf("22:00", "02:00", "06:00"), BtbWeatherPolicy.shiftSlots("22:00", "06:00"))
        assertTrue(BtbWeatherPolicy.shiftSlots("unbekannt", "16:00").isEmpty())
    }

    @Test fun measuredZeroRainIsDryEvenWhenWeatherCodeIsMissing() {
        assertEquals("trocken", BtbWeatherPolicy.rain(null, 0.0))
        assertNull(BtbWeatherPolicy.rain(null, null))
    }

    @Test fun cloudPercentageDoesNotInventUnknownConditions() {
        assertEquals("leicht bewölkt", BtbWeatherPolicy.cloud(50))
        assertEquals("bedeckt", BtbWeatherPolicy.cloud(100))
        assertNull(BtbWeatherPolicy.cloud(null))
    }

    @Test fun overnightShiftUsesClosestActualDateAndHour() {
        val times = listOf(
            LocalDateTime.parse("2026-11-20T23:00:00"),
            LocalDateTime.parse("2026-11-21T01:00:00")
        )
        assertEquals(1, BtbWeatherPolicy.nearestIndex(times, LocalDateTime.parse("2026-11-21T00:45:00")))
        assertNull(BtbWeatherPolicy.nearestIndex(times, LocalDateTime.parse("2026-11-22T03:00:00")))
    }
}
