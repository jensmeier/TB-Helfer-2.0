package de.tbhelfer.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechSessionStateTest {
    @Test fun `five seconds silence after speaking`() {
        val s=SpeechSessionState(0);s.speech();s.partial("Pittrich");s.endSpeech(1000)
        assertFalse(s.due(4000));assertFalse(s.due(5999));assertTrue(s.due(6000))
    }
    @Test fun `new speech resets pause without losing text`() {
        val s=SpeechSessionState(0);s.speech();s.segment("Pittrich");s.endSpeech(1000)
        s.speech();s.partial("Schwellen gewechselt")
        assertFalse(s.due(9000));assertEquals("Pittrich Schwellen gewechselt",s.text())
        s.endSpeech(9000);assertTrue(s.due(14000))
    }
    @Test fun `final corrects interim instead of duplicating`() {
        val s=SpeechSessionState(0);s.partial("Pit");s.partial("Pit rich");s.segment("Pittrich")
        assertEquals("Pittrich",s.text())
    }
    @Test fun `empty final preserves interim text`() {
        val s=SpeechSessionState(0);s.partial("Schwellen gewechselt");s.segment("")
        assertEquals("Schwellen gewechselt",s.text())
    }
    @Test fun `ending again does not reset silence clock`() {
        val s=SpeechSessionState(0);s.speech();s.endSpeech(1000);s.endSpeech(4000)
        assertTrue(s.due(6000))
    }
    @Test fun `startup and maximum recording are bounded`() {
        val s=SpeechSessionState(0);assertFalse(s.due(9999));assertTrue(s.due(10000))
        val speaking=SpeechSessionState(0);speaking.speech();assertFalse(speaking.due(20000));assertTrue(speaking.due(300000))
    }
    @Test fun `late callbacks cannot modify stopped recording`() {
        val s=SpeechSessionState(0);s.partial("Erhalten");s.stop();s.partial("Falsch");s.segment("Falsch");s.speech()
        assertEquals("Erhalten",s.text());assertFalse(s.due(400000))
    }
    @Test fun `separate identical spoken segments are not silently discarded`() {
        val s=SpeechSessionState(0);s.segment("Ja");s.segment("Ja")
        assertEquals("Ja Ja",s.text())
    }
}
