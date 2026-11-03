package de.tbhelfer.speech

import de.tbhelfer.ui.bautagebuch.*
import org.junit.Assert.*
import org.junit.Test

class SpeechDraftPolicyTest {
    private val firms=listOf("a" to "Pittrich","b" to "Schwerbau")
    private fun report()=BautagebuchEintrag("Test",1L,btbId="btb-1",datumIso="2026-11-03",
        wetter="vorhandenes Wetter",firmen=listOf(FirmaBlock("a","Pittrich")),
        besonderheiten=SectionBlock(listOf(SectionItem(clientEntryKey="old",text="Erhalten"))),maxFotosInPdf=7)
    @Test fun `firma und punkt ohne vorgeschaltete reihenfolge`() {
        assertEquals(4 to "a",SpeechDraftPolicy.suggest("Schwellen gewechselt durch Pittrich",firms))
        assertEquals(7 to "a",SpeechDraftPolicy.suggest("Pittrich Besonderheiten Zufahrt gesperrt",firms))
    }
    @Test fun `nur firma bleibt offen`() {
        assertEquals(null to "a",SpeechDraftPolicy.suggest("Pittrich",firms))
        assertFalse(SpeechDraftPolicy.hasDescription("Pittrich","Pittrich"))
        assertFalse(SpeechDraftPolicy.hasDescription("Arbeiten Pittrich","Pittrich"))
    }
    @Test fun `kein firmenraten oder teilworttreffer`() {
        assertEquals(4 to null,SpeechDraftPolicy.suggest("Schwellenwechsel",firms))
        assertEquals(null to null,SpeechDraftPolicy.suggest("Pittricher macht das",firms))
        assertNull(SpeechDraftPolicy.suggest("Pittrich und Schwerbau arbeiten",firms).second)
    }
    @Test fun `mehrdeutiger abschnitt und gleichnamige firmen bleiben offen`() {
        assertNull(SpeechDraftPolicy.suggest("Mangel bei Abnahme Pittrich",firms).first)
        assertNull(SpeechDraftPolicy.suggest("Pittrich",listOf("a" to "Pittrich","b" to "PITTRICH")).second)
    }
    @Test fun `umlaute und unklare ortsangaben werden nicht zu kilometern`() {
        assertEquals(8 to "a",SpeechDraftPolicy.suggest("Mängel Pittrich von da bis da",firms))
        assertTrue(SpeechDraftPolicy.hasDescription("Schwellen gewechselt von da bis da",null))
    }
    @Test fun `offline ohne netz online nur mit freigabe und netz`() {
        assertEquals(SpeechDraftPolicy.Route.OFFLINE,SpeechDraftPolicy.route(true,false,false))
        assertEquals(SpeechDraftPolicy.Route.OFFLINE,SpeechDraftPolicy.route(true,true,true))
        assertEquals(SpeechDraftPolicy.Route.BLOCKED,SpeechDraftPolicy.route(false,true,false))
        assertEquals(SpeechDraftPolicy.Route.BLOCKED,SpeechDraftPolicy.route(false,false,true))
        assertEquals(SpeechDraftPolicy.Route.ONLINE,SpeechDraftPolicy.route(false,true,true))
    }
    @Test fun `zaehler unterscheiden zugeordnet offen und bewusst allgemein`() {
        val d=SpeechDraft("id","owner",1,"Site","btb","Text",4,"a")
        assertEquals(4,SpeechDraftPolicy.bucket(d))
        assertNull(SpeechDraftPolicy.bucket(d.copy(firmId=null)))
        assertNull(SpeechDraftPolicy.bucket(d.copy(section=null)))
        assertEquals(4,SpeechDraftPolicy.bucket(d.copy(firmId="")))
    }
    @Test fun `bestaetigung fuegt genau einen eintrag hinzu und erhaelt bestand`() {
        val before=report();val item=SectionItem(clientEntryKey="draft-1",text="Neue Arbeit",firmaId="a",worksiteNodeKey="node-1")
        val after=SpeechDraftPolicy.append(before,4,item)
        assertEquals(listOf(item),after.arbeiten.items)
        assertEquals(before.besonderheiten,after.besonderheiten)
        assertEquals(before.firmen,after.firmen)
        assertEquals(before.wetter,after.wetter)
        assertEquals(7,after.maxFotosInPdf)
        assertEquals(before.btbId,after.btbId)
        assertEquals(after,SpeechDraftPolicy.append(after,4,item))
    }
    @Test(expected=IllegalArgumentException::class) fun `retry mit abweichendem inhalt ueberschreibt nichts`() {
        val item=SectionItem(clientEntryKey="draft-1",text="Erster Inhalt")
        SpeechDraftPolicy.append(SpeechDraftPolicy.append(report(),4,item),4,item.copy(text="Anderer Inhalt"))
    }
    @Test(expected=IllegalArgumentException::class) fun `retry verschiebt bestehenden eintrag nicht heimlich`() {
        val item=SectionItem(clientEntryKey="draft-1",text="Inhalt")
        SpeechDraftPolicy.append(SpeechDraftPolicy.append(report(),4,item),8,item)
    }
    @Test(expected=IllegalArgumentException::class) fun `kein leerer eintrag`() {
        SpeechDraftPolicy.append(report(),4,SectionItem(clientEntryKey="draft-1",text=" "))
    }
    @Test fun `kein start im hintergrund waehrend vorbereitung oder berechtigungsdialog`() {
        assertTrue(SpeechDraftPolicy.mayStart(true,false,false,false))
        assertFalse(SpeechDraftPolicy.mayStart(false,false,false,false))
        assertFalse(SpeechDraftPolicy.mayStart(true,true,false,false))
        assertFalse(SpeechDraftPolicy.mayStart(true,false,true,false))
        assertFalse(SpeechDraftPolicy.mayStart(true,false,false,true))
    }
    @Test fun `alle fuenf abschnitte getrennt erreichbar`() {
        for(point in 4..8) {
            val item=SectionItem(clientEntryKey="draft-$point",text="Text $point")
            val updated=SpeechDraftPolicy.append(report(),point,item)
            assertTrue(SpeechDraftPolicy.items(updated,point).contains(item))
            assertTrue(SpeechDraftPolicy.contains(updated,item.clientEntryKey!!))
        }
    }
}
