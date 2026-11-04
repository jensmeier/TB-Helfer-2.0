package de.tbhelfer.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechMatchingTest {
    private val firms=listOf("p" to "Pittrich", "w" to "LST Werner")
    @Test fun `spoken points four through eight take precedence`() {
        listOf("vier","fünf","sechs","sieben","acht").forEachIndexed { index,word ->
            assertEquals(index+4,SpeechMatching.section("Punkt $word Pittrich Mangel bei Abnahme"))
            assertEquals(index+4,SpeechMatching.section("Punkt ${index+4} Pittrich Mangel bei Abnahme"))
        }
    }
    @Test fun `contradictory explicit points remain open`() {
        assertNull(SpeechMatching.section("Punkt vier oder Punkt acht"))
        assertNull(SpeechMatching.section("Mangel bei Abnahme"))
        assertNull(SpeechMatching.section("Punkt vierzehn"))
    }
    @Test fun `work and category synonyms`() {
        assertEquals(4,SpeechMatching.section("Pittrich einschottern"))
        assertEquals(5,SpeechMatching.section("Abnahmen Werner"))
        assertEquals(6,SpeechMatching.section("Unterweisungen erfolgt"))
        assertEquals(7,SpeechMatching.section("Besonderheit Zufahrt gesperrt"))
        assertEquals(8,SpeechMatching.section("Beschädigung am Geländer"))
    }
    @Test fun `similar firm spelling and split words are proposed`() {
        assertEquals("p",SpeechMatching.firm("Pitrich Schwellenwechsel",firms)?.id)
        assertEquals("p",SpeechMatching.firm("Pit rich Einschottern",firms)?.id)
        assertEquals("w",SpeechMatching.firm("LST Werner Prüfung",firms)?.id)
    }
    @Test fun `no suffix or short generic guessing`() {
        assertNull(SpeechMatching.firm("Pittricher arbeitet",firms))
        assertNull(SpeechMatching.firm("ABC",listOf("1" to "ABD")))
        assertNull(SpeechMatching.firm("arbeiten",firms))
    }
    @Test fun `near tied and duplicated companies remain open`() {
        assertNull(SpeechMatching.firm("Pitrich",listOf("a" to "Pittrich","b" to "Pietrich")))
        assertNull(SpeechMatching.firm("Pittrich",listOf("a" to "Pittrich","b" to "Pittrich")))
        assertNull(SpeechMatching.firm("Pittrich LST Werner",firms))
    }
    @Test fun `only existing company aliases apply`() {
        assertEquals("p",SpeechMatching.firm("Firma Peter",firms,listOf("p" to "Peter"))?.id)
        assertNull(SpeechMatching.firm("Firma Peter",firms,listOf("deleted" to "Peter")))
        assertNull(SpeechMatching.firm("Firma Peter",firms,listOf("p" to "Peter","w" to "Peter")))
    }
    @Test fun `alias never replaces another exact company`() {
        assertNull(SpeechMatching.firm("LST Werner",firms,listOf("p" to "LST Werner")))
    }
    @Test fun `empty numeric or category aliases rejected`() {
        listOf("", "  ","12","Punkt acht","Arbeiten","Mängel").forEach { assertFalse(it,SpeechMatching.validAlias(it)) }
        assertTrue(SpeechMatching.validAlias("Pit rich"))
    }
}
