package de.tbhelfer.vermessung

import de.tbhelfer.datenbank.TrassenAchseEntity
import de.tbhelfer.datenbank.TrassenSamplePointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrassenAnschlussDbAdapterTest {

    @Test
    fun realeTraEntities_werdenMitStationenUndTypenUebernommen() {
        val haupt = TrassenAchseEntity(
            id = 11,
            name = "Gleis 1",
            achsTyp = TrassenAchseEntity.ACHSTYP_GLEIS,
            quelleDatei = "GL1.tra"
        )
        val weiche = TrassenAchseEntity(
            id = 22,
            name = "W 10",
            achsTyp = TrassenAchseEntity.ACHSTYP_WEICHE,
            quelleDatei = "W10.tra"
        )
        val punkte = listOf(
            punkt(11, 0.0, 0.0, 0.0),
            punkt(11, 100.0, 100.0, 0.0),
            punkt(22, 0.0, 50.0, 15.0),
            punkt(22, 15.0, 50.0, 0.5)
        )

        val anschluss = TrassenAnschlussDbAdapter.findeAnschluesse(
            achsen = listOf(haupt, weiche),
            samplepunkte = punkte,
            toleranzM = 1.0
        ).single()

        val hauptPos = anschluss.positionFuer(11)!!
        val weichenPos = anschluss.positionFuer(22)!!
        assertEquals(50.0, hauptPos.station, 0.001)
        assertEquals(TrassenAchseEntity.ACHSTYP_GLEIS, hauptPos.achsTyp)
        assertFalse(hauptPos.istAchsEnde)
        assertEquals(15.0, weichenPos.station, 0.001)
        assertEquals(TrassenAchseEntity.ACHSTYP_WEICHE, weichenPos.achsTyp)
        assertTrue(weichenPos.istAchsEnde)
    }

    private fun punkt(
        achseId: Long,
        station: Double,
        x: Double,
        y: Double
    ): TrassenSamplePointEntity = TrassenSamplePointEntity(
        achseId = achseId,
        station = station,
        xDbRef = x,
        yDbRef = y
    )
}
