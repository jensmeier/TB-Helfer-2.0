package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BtbManualEntryPhotoTargetParserTest {

    @Test
    fun parsesCompanyLocationTimeKmAndText() {
        val raw = "@ORT=123 @KM=5.963 • 09:36 bbg: Test1.0"

        val target = BtbManualEntryPhotoTargetParser.parse(raw, listOf("bbg")).single()

        assertEquals("bbg", target.firma)
        assertEquals(123L, target.ortId)
        assertEquals("09:36", target.time)
        assertEquals("Test1.0", target.text)
        assertEquals(5.963, target.kmVon!!, 0.000001)
        assertNull(target.kmBis)
    }

    @Test
    fun generalEntryStaysOnMainWorksite() {
        val raw = "@ORT=123 @ALLGEMEIN • Allgemeiner Eintrag"

        val target = BtbManualEntryPhotoTargetParser.parse(raw, listOf("bbg")).single()

        assertNull(target.firma)
        assertEquals(0L, target.ortId)
        assertEquals("Allgemeiner Eintrag", target.text)
    }

    @Test
    fun parsesTimeAndKmRange() {
        val raw = "@ORT=77 @KM=3,805-3,920 • 8:05–09:30 BBG: Gleisquerung"

        val target = BtbManualEntryPhotoTargetParser.parse(raw, listOf("bbg")).single()

        assertEquals("bbg", target.firma)
        assertEquals(77L, target.ortId)
        assertEquals("08:05–09:30", target.time)
        assertEquals(3.805, target.kmVon!!, 0.000001)
        assertEquals(3.920, target.kmBis!!, 0.000001)
        assertEquals("Gleisquerung", target.text)
    }
}
