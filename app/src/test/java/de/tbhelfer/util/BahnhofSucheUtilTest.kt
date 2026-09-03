package de.tbhelfer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BahnhofSucheUtilTest {

    private val code = "MPE"
    private val name = "München-Pasing"
    private val display = "MPE · München-Pasing"

    @Test
    fun `kuerzel findet betriebsstelle`() {
        assertTrue(BahnhofSucheUtil.enthaeltTreffer("MPE", code, name, display))
        assertTrue(BahnhofSucheUtil.enthaeltTreffer("mp", code, name, display))
    }

    @Test
    fun `name und teiltext finden kuerzel rueckwaerts`() {
        assertTrue(BahnhofSucheUtil.enthaeltTreffer("Pasing", code, name, display))
        assertTrue(BahnhofSucheUtil.enthaeltTreffer("münchen-p", code, name, display))
    }

    @Test
    fun `fremde betriebsstelle bleibt draussen`() {
        assertFalse(BahnhofSucheUtil.enthaeltTreffer("Hamburg", code, name, display))
    }
}
