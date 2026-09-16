package de.tbhelfer.vermessung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DbRefKoordinatenwandlerTest {

    @Test
    fun `5362 zone3 wird nach WGS84 statt mit zone4 offset umgerechnet`() {
        val pos = DbRefKoordinatenwandler.dbRef2016ZuWgs84OrNull(
            3_597_538.6261,
            5_280_599.5563
        )

        assertNotNull(pos)
        pos!!
        assertEquals(47.6564, pos.lat, 0.002)
        assertEquals(10.2974, pos.lon, 0.002)
    }

    @Test
    fun `historischer zone4 methodenname erkennt ebenfalls zone3 korrekt`() {
        val pos = DbRefKoordinatenwandler.dbRef2016Zone4ZuWgs84OrNull(
            3_597_538.6261,
            5_280_599.5563
        )

        assertNotNull(pos)
        assertEquals(10.2974, pos!!.lon, 0.002)
    }

    @Test
    fun `unplausibler streifen wird abgelehnt`() {
        assertNull(
            DbRefKoordinatenwandler.dbRef2016ZuWgs84OrNull(
                1_597_538.6261,
                5_280_599.5563
            )
        )
    }
}
