package de.tbhelfer.ui.bautagebuch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BtbAbschlussPdfFinderTest {

    @Test
    fun `gekuerzter dokumenttoken wird im versionierten pdf erkannt`() {
        val token = "12345678-1234-1234"
        val name = "BTB_2026-09-12_13_00-22_00_jens_poco_${token}_V1.pdf"

        assertTrue(BtbAbschlussPdfFinder.passtZuToken(name, token))
        assertEquals(1, BtbAbschlussPdfFinder.versionAusDateiname(name))
    }

    @Test
    fun `andere btb id wird nicht verwechselt`() {
        val token = "12345678-1234-1234"
        val name = "BTB_2026-09-12_13_00-22_00_jens_poco_aaaaaaaa-bbbb-cccc_V2.pdf"

        assertFalse(BtbAbschlussPdfFinder.passtZuToken(name, token))
    }

    @Test
    fun `hoechere dokumentversion wird erkannt`() {
        assertEquals(3, BtbAbschlussPdfFinder.versionAusDateiname("BTB_test_token_V3.pdf"))
    }
}
