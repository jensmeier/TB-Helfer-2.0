package de.tbhelfer.vermessung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

class GvpDateiParserRawKfTest {

    private val charset = Charset.forName("windows-1252")

    @Test
    fun `5362 PS1 aus roher KF behaelt Strecke Station und Zone3 Rohkoordinaten`() {
        val bytes = rohKf(
            version = "VERSION 7.0",
            row = Row(
                kfNummer = 40.00300,
                rechtswert = 3_597_538.6261,
                hochwert = 5_280_599.5563,
                hoehe = 721.8565,
                station = 70_698.1786,
                lage = "1070698",
                part = "PS1",
                strecke = "5362",
                rikz = "5"
            )
        )

        val result = GvpDateiParser.parse("5362_km70,6-81,5_PS1-PS3_20260619_V2.kf", bytes)
        val punkt = result.punkte.single()

        assertEquals("1070698", punkt.punktName)
        assertEquals("PS1", punkt.typ)
        assertEquals("5362", punkt.strecke)
        assertEquals(70_698.1786, punkt.kmMeter!!, 0.00001)
        assertEquals(3_597_538.6261, punkt.xDbRef, 0.00001)
        assertEquals(5_280_599.5563, punkt.yDbRef, 0.00001)
        assertEquals(721.8565, punkt.hoeheM!!, 0.00001)
        assertEquals("DB_REF_GK_3", punkt.koordinatensystem)
        assertNull(punkt.lat)
        assertNull(punkt.lon)
        assertTrue(result.warnungen.any { it.contains("Roh-KF 7.0 direkt importiert") })
    }

    @Test
    fun `VERSION 7_0 R und GVPV Station werden direkt gelesen`() {
        val bytes = rohKf(
            version = "VERSION 7.0  R",
            row = Row(
                kfNummer = 5.40600,
                rechtswert = 4_403_282.57605,
                hochwert = 5_407_261.69976,
                hoehe = 416.4690,
                station = 54_319.9350,
                lage = "54/6",
                part = "GVPV",
                strecke = "5300",
                rikz = "5"
            )
        )

        val punkt = GvpDateiParser.parse("Festpkt_5300_054-059_DHDN.kf", bytes).punkte.single()

        assertEquals("54/6", punkt.punktName)
        assertEquals("GVPV", punkt.typ)
        assertEquals("5300", punkt.strecke)
        assertEquals(54_319.9350, punkt.kmMeter!!, 0.00001)
        assertEquals(416.4690, punkt.hoeheM!!, 0.00001)
    }

    @Test
    fun `Harburg Platzhalterstation null wird nicht als km null erfunden`() {
        val bytes = rohKf(
            row = Row(
                kfNummer = 77.00051,
                rechtswert = 4_405_010.5870,
                hochwert = 5_403_770.3040,
                hoehe = 412.5310,
                station = 0.0,
                lage = "50/51",
                part = "GVPV",
                strecke = "5300",
                rikz = "1"
            )
        )

        val result = GvpDateiParser.parse("5300 km 51-53 GE+WE Harburg_DBREF2016.kf", bytes)
        val punkt = result.punkte.single()

        assertEquals("5300", punkt.strecke)
        assertNull(punkt.kmMeter)
        assertTrue(result.warnungen.any { it.contains("ohne plausible Station") })
    }

    @Test
    fun `Sentinelwerte und Strecke 0000 bleiben fehlend statt falsche KM zu erzeugen`() {
        val bytes = rohKf(
            row = Row(
                kfNummer = 7.00011,
                rechtswert = 4_459_039.0800,
                hochwert = 5_335_216.9630,
                hoehe = 1e40,
                station = 1e40,
                lage = "30005",
                part = "",
                strecke = "0000",
                rikz = "0"
            )
        )

        val result = GvpDateiParser.parse("Bbf_Pasing_FPF_Gesamt_mit_Gl30-40_neu_DBREF.kf", bytes)
        val punkt = result.punkte.single()

        assertNull(punkt.strecke)
        assertNull(punkt.kmMeter)
        assertNull(punkt.hoeheM)
        assertTrue(result.warnungen.any { it.contains("ohne plausible Strecke") })
    }

    private data class Row(
        val kfNummer: Double,
        val rechtswert: Double,
        val hochwert: Double,
        val hoehe: Double,
        val station: Double,
        val lage: String,
        val zusatz: String = "",
        val part: String,
        val strecke: String,
        val rikz: String
    )

    private fun rohKf(
        version: String = "VERSION 7.0",
        row: Row
    ): ByteArray {
        val bytes = ByteArray(204)
        text(bytes, 40, 34, version)
        text(bytes, 97, 5, "00000")

        val base = 102
        double(bytes, base, row.kfNummer)
        double(bytes, base + 8, row.rechtswert)
        double(bytes, base + 16, row.hochwert)
        double(bytes, base + 24, row.hoehe)
        double(bytes, base + 32, row.station)
        text(bytes, base + 40, 16, row.lage)
        text(bytes, base + 56, 14, row.zusatz)
        text(bytes, base + 70, 4, row.part)
        text(bytes, base + 74, 1, "0")
        text(bytes, base + 83, 14, "TEST")
        text(bytes, base + 97, 4, row.strecke)
        text(bytes, base + 101, 1, row.rikz)
        return bytes
    }

    private fun double(bytes: ByteArray, offset: Int, value: Double) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putDouble(offset, value)
    }

    private fun text(bytes: ByteArray, offset: Int, length: Int, value: String) {
        val encoded = value.toByteArray(charset)
        System.arraycopy(encoded, 0, bytes, offset, minOf(length, encoded.size))
    }
}
