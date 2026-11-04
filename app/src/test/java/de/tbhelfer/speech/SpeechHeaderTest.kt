package de.tbhelfer.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechHeaderTest {
    private val firms=listOf("p" to "pittrich", "g" to "GTU Mobility")
    private fun parse(text:String)=SpeechHeader.prepare(SpeechDraft("id","owner",1,"site","btb",text),firms)
    @Test fun userExample() {
        val d=parse("Firma pittrich, Besonderheiten 13Uhr-15Uhr, Kilometer 13.800 bis km 13,900 und dann der Text")
        assertEquals("p",d.firmId);assertEquals(7,d.section);assertEquals("13:00",d.timeFrom);assertEquals("15:00",d.timeTo)
        assertEquals("13,800",d.kmFrom);assertEquals("13,900",d.kmTo);assertEquals("und dann der Text",d.text)
    }
    @Test fun screenshotTypoAndDescriptionDoNotConflict() {
        val raw="Firma pittrich Besondenheiten von 12 Uhr bis 14 Uhr Kilometer 33,8 bis Kilometer 34,50 Schwellen gewechselt"
        val d=parse(raw)
        assertEquals(7,d.section);assertEquals("12:00",d.timeFrom);assertEquals("14:00",d.timeTo)
        assertEquals("33,800",d.kmFrom);assertEquals("34,500",d.kmTo);assertEquals("Schwellen gewechselt",d.text);assertEquals(raw,d.originalText)
    }
    @Test fun textMarkerProtectsAllBodyNumbers() {
        val d=parse("Firma pittrich Punkt sieben Text: Abnahme um 14 Uhr Kilometer 13,900 kontrolliert")
        assertEquals(7,d.section);assertEquals("",d.timeFrom);assertEquals("",d.kmFrom)
        assertEquals("Abnahme um 14 Uhr Kilometer 13,900 kontrolliert",d.text)
    }
    @Test fun firstUnknownPhraseStopsParsing() {
        val d=parse("Arbeiten Schwellen um 13 Uhr bei Kilometer 12,3 gewechselt")
        assertEquals("",d.timeFrom);assertEquals("",d.kmFrom);assertEquals("Schwellen um 13 Uhr bei Kilometer 12,3 gewechselt",d.text)
    }
    @Test fun missingNumbersRemainAbsent() {val d=parse("Arbeiten Schwellen gewechselt");assertEquals("",d.timeFrom);assertEquals("",d.kmFrom)}
    @Test fun minutesAndNightShiftPreserveDirection() {
        val d=parse("Besonderheiten 22:30 bis 06:15 KM 13,9 bis 13,8 Text: Nachtarbeit")
        assertEquals("22:30",d.timeFrom);assertEquals("06:15",d.timeTo);assertEquals("13,900",d.kmFrom);assertEquals("13,800",d.kmTo);assertNull(SpeechHeader.validation(d))
    }
    @Test fun shortHourRange() {val d=parse("Besonderheiten 13–15 Uhr Text: Kontrolle");assertEquals("13:00",d.timeFrom);assertEquals("15:00",d.timeTo)}
    @Test fun invalidTimeNotRemovedOrClamped() {val d=parse("Besonderheiten 25 Uhr bis 26 Uhr Kontrolle");assertEquals("",d.timeFrom);assertTrue(d.text.contains("25 Uhr"));assertTrue(d.parseNotice.isNotBlank())}
    @Test fun incompleteTimeRangeNotPartiallyConsumed() {val d=parse("Arbeiten 13 Uhr bis später Kontrolle");assertEquals("",d.timeFrom);assertTrue(d.text.startsWith("13 Uhr"));assertTrue(d.parseNotice.isNotBlank())}
    @Test fun malformedKmNotPartiallyConsumed() {val d=parse("Arbeiten km 13.800,900 Kontrolle");assertEquals("",d.kmFrom);assertTrue(d.text.startsWith("km 13.800,900"))}
    @Test fun incompleteKmRange() {val d=parse("Arbeiten km 13,8 bis km Kontrolle");assertEquals("",d.kmFrom);assertTrue(d.text.startsWith("km 13,8"))}
    @Test fun repeatedValuesRemainForReview() {val d=parse("Arbeiten 13 Uhr 14 Uhr Kontrolle");assertEquals("13:00",d.timeFrom);assertTrue(d.text.startsWith("14 Uhr"));assertTrue(d.parseNotice.isNotBlank())}
    @Test fun conflictingSectionsStayUnassigned() {val d=parse("Arbeiten Besonderheiten Kontrolle");assertNull(d.section);assertTrue(d.parseNotice.isNotBlank())}
    @Test fun unknownFirmNotDeleted() {val d=parse("Firma unbekannt Besonderheiten 13 Uhr Kontrolle");assertNull(d.firmId);assertTrue(d.text.startsWith("Firma unbekannt"))}
    @Test fun explicitAliasAndMultipleWordFirm() {
        val d=SpeechHeader.prepare(SpeechDraft("id","o",1,"s","b","Firma pitt rich Besonderheiten Text: Kontrolle"),firms,listOf("p" to "pitt rich"))
        assertEquals("p",d.firmId);assertEquals("Kontrolle",d.text)
        assertEquals("g",parse("Firma GTU Mobility Abnahmen Text: Kontrolle").firmId)
    }
    @Test fun unambiguousCompanyTypo() {val d=parse("Firma pittrichh Besonderheiten Kontrolle");assertTrue(d.text.startsWith("Firma")) /* conservative prefix-extension rejection */}
    @Test fun reviewedDraftNeverReparsed() {
        val d=parse("Besonderheiten 13 Uhr Kontrolle").copy(text="Arbeiten 16 Uhr",timeFrom="",section=5)
        assertEquals(d,SpeechHeader.prepare(d,firms))
    }
    @Test fun invalidEditedValuesBlockCommit() {
        val d=parse("Arbeiten Kontrolle")
        assertNotNull(SpeechHeader.validation(d.copy(timeFrom="24:00")))
        assertNotNull(SpeechHeader.validation(d.copy(kmFrom="13,8000")))
        assertNotNull(SpeechHeader.validation(d.copy(timeTo="15:00")))
        assertNotNull(SpeechHeader.validation(d.copy(kmTo="13,8")))
        assertNull(SpeechHeader.validation(d.copy(timeFrom="13:00",kmFrom="13.800")))
    }
    @Test fun noDescriptionInvented() {val d=parse("Firma pittrich Besonderheiten 13 Uhr");assertEquals("",d.text);assertFalse(SpeechDraftPolicy.hasDescription(d.text,"pittrich"))}
    @Test fun signedKmAndSingleTime() {val d=parse("Arbeiten 07 Uhr KM -0,250 Kontrolle");assertEquals("07:00",d.timeFrom);assertEquals("-0,250",d.kmFrom)}
    @Test fun sectionNamesFourThroughEight() {
        listOf("Arbeiten","Abnahmen","Unterweisung","Besondenheiten","Mängel").forEachIndexed { i,s -> assertEquals(i+4,parse("$s Text: Kontrolle").section) }
    }
    @Test fun ambiguousBareKmDigitsRemainVisible() {val d=parse("Arbeiten km 13800 Kontrolle");assertEquals("",d.kmFrom);assertTrue(d.parseNotice.isNotBlank())}
    @Test fun splitKmDigitsRemainVisible() {val d=parse("Arbeiten km 13 800 Kontrolle");assertEquals("",d.kmFrom);assertTrue(d.parseNotice.isNotBlank())}
    @Test fun singularSectionNames() {assertEquals(4,parse("Arbeit 13 Uhr Kontrolle").section);assertEquals(7,parse("Besonderheit 13 Uhr Kontrolle").section)}
    @Test fun spokenMinutes() {val d=parse("Arbeiten 13 Uhr 15 bis 14 Uhr 30 Kontrolle");assertEquals("13:15",d.timeFrom);assertEquals("14:30",d.timeTo)}
}
