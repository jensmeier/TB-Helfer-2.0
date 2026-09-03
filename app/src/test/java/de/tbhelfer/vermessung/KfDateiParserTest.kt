package de.tbhelfer.vermessung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

class KfDateiParserTest {

    @Test
    fun ps4MitStreckeUndStation_wirdDirektAlsVermessungspunktImportiert() {
        val bytes = kfDatei(
            punkt(
                kfPktNr = 40.00050,
                rechtswert = 3_597_325.6975,
                hochwert = 5_280_170.6747,
                hoehe = 722.7069,
                station = 71_177.6163,
                lage = "71/55",
                zusatz = "Gelbe Kappe",
                pArt = "PS4",
                strecke = "5362",
                riKz = "5"
            )
        )

        val result = KfDateiParser.parse("Str5362_km71-81,5_PS4_20260619.kf", bytes)
        val p = result.punkte.single()

        assertEquals("71/55", p.punktName)
        assertEquals("PS4", p.typ)
        assertEquals("5362", p.strecke)
        assertEquals(71_177.6163, p.kmMeter!!, 0.000001)
        assertEquals(3_597_325.6975, p.xDbRef, 0.000001)
        assertEquals(5_280_170.6747, p.yDbRef, 0.000001)
        assertEquals(722.7069, p.hoeheM!!, 0.000001)
        assertEquals("40.00050", p.klasse)
        assertTrue(p.rawLine.orEmpty().contains("ZUSATZ=Gelbe Kappe"))
        assertTrue(p.rawLine.orEmpty().contains("RIKZ=5"))
    }

    @Test
    fun sentinelHoehe_bleibtNull_stationBleibtVerwendbar() {
        val bytes = kfDatei(
            punkt(
                kfPktNr = 5.40010,
                rechtswert = 4_403_135.9940,
                hochwert = 5_407_195.9940,
                hoehe = 1e40,
                station = 54_482.0,
                lage = "540010",
                pArt = "PS2",
                strecke = "5300",
                riKz = "5"
            ),
            headerVersion = "VERSION 7.0  R"
        )

        val result = KfDateiParser.parse("Festpkt_5300_054-059_DHDN.kf", bytes)
        val p = result.punkte.single()

        assertNull(p.hoeheM)
        assertEquals(54_482.0, p.kmMeter!!, 0.001)
        assertTrue(result.warnungen.any { it.contains("Hoehe=NULL") })
    }

    @Test
    fun stationNull_oderSentinel_wirdNieAlsKilometrierungVerwendet() {
        val harburg = punkt(
            kfPktNr = 77.00051,
            rechtswert = 4_405_010.5870,
            hochwert = 5_403_770.3040,
            hoehe = 412.5310,
            station = 0.0,
            lage = "50/51",
            pArt = "GVPV",
            strecke = "5300",
            riKz = "1"
        )
        val pasing = punkt(
            kfPktNr = 7.00011,
            rechtswert = 4_459_039.0800,
            hochwert = 5_335_216.9630,
            hoehe = 529.2500,
            station = 1e40,
            lage = "30005",
            pArt = "",
            strecke = "0000",
            riKz = "0"
        )

        val result = KfDateiParser.parse("mischbestand.kf", kfDatei(harburg, pasing))

        assertEquals(2, result.punkte.size)
        assertNull(result.punkte[0].kmMeter)
        assertNull(result.punkte[1].kmMeter)
        assertNull(result.punkte[1].strecke)
        assertTrue(result.warnungen.any { it.contains("Station 0.0") })
        assertTrue(result.warnungen.any { it.contains("1e40") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun falscheDatensatzlaenge_wirdAbgelehnt() {
        KfDateiParser.parse("kaputt.kf", ByteArray(205))
    }

    private fun kfDatei(vararg punkte: ByteArray, headerVersion: String = "VERSION 7.0"): ByteArray {
        val header = ByteArray(102)
        schreibeText(header, 40, 34, headerVersion)
        schreibeText(header, 97, 5, "00000")
        return buildList {
            add(header)
            addAll(punkte)
        }.fold(ByteArray(0)) { acc, teil -> acc + teil }
    }

    private fun punkt(
        kfPktNr: Double,
        rechtswert: Double,
        hochwert: Double,
        hoehe: Double,
        station: Double,
        lage: String,
        zusatz: String = "",
        pArt: String,
        strecke: String,
        riKz: String
    ): ByteArray {
        val b = ByteArray(102)
        schreibeDouble(b, 0, kfPktNr)
        schreibeDouble(b, 8, rechtswert)
        schreibeDouble(b, 16, hochwert)
        schreibeDouble(b, 24, hoehe)
        schreibeDouble(b, 32, station)
        schreibeText(b, 40, 16, lage)
        schreibeText(b, 56, 14, zusatz)
        schreibeText(b, 70, 4, pArt)
        b[74] = 0x0E
        b[75] = 0x03
        b[77] = 0x05
        schreibeText(b, 83, 14, "1O00")
        schreibeText(b, 97, 4, strecke)
        schreibeText(b, 101, 1, riKz)
        return b
    }

    private fun schreibeDouble(bytes: ByteArray, offset: Int, value: Double) {
        ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value)
    }

    private fun schreibeText(bytes: ByteArray, offset: Int, length: Int, value: String) {
        val roh = value.toByteArray(Charset.forName("windows-1252"))
        roh.copyInto(bytes, offset, 0, minOf(length, roh.size))
        for (i in offset + minOf(length, roh.size) until offset + length) bytes[i] = ' '.code.toByte()
    }
}
