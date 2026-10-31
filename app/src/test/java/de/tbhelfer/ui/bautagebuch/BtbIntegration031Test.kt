package de.tbhelfer.ui.bautagebuch

import de.tbhelfer.datenbank.Bildtyp
import org.junit.Assert.*
import org.junit.Test

class BtbIntegration031Test {
    private val item = SectionItem(clientEntryKey = "e", text = "Schwellenwechsel", firmaId = "f",
        firmaName = "Pittrich", worksiteNodeKey = "MHH-W550")
    private fun entry(items: List<SectionItem> = listOf(item)) = BautagebuchEintrag(
        baustelleName = "Test", baustelleId = 1L, datumIso = "2026-10-31",
        firmen = listOf(FirmaBlock(firmaId = "f", name = "Pittrich")), arbeiten = SectionBlock(items))

    @Test fun structuredCompanyNodeAndAllPictureNumbersReachPrintSnapshot() {
        val printed = BtbPrintSnapshot.renderItem(item, mapOf("f" to "Pittrich"), mapOf("MHH-W550" to 12L), mapOf("e" to listOf(3, 1, 2)))
        assertEquals("@ORT=12 Pittrich: Schwellenwechsel [Bild 1, 2, 3]", printed.text)
        assertEquals("Schwellenwechsel", item.text)
        assertEquals(item.clientEntryKey, printed.clientEntryKey)
    }
    @Test fun colonInTextIsPreserved() {
        val printed = BtbPrintSnapshot.renderItem(item.copy(text = "1stab 13:02"), mapOf("f" to "Pittrich"), mapOf("MHH-W550" to 12L))
        assertEquals("@ORT=12 Pittrich: 1stab 13:02", printed.text)
    }
    @Test fun generalCompanyCanStillHaveANode() {
        val printed = BtbPrintSnapshot.renderItem(item.copy(firmaId = null, firmaName = null), emptyMap(), mapOf("MHH-W550" to 12L))
        assertEquals("@ORT=12 @ALLGEMEIN Schwellenwechsel", printed.text)
    }
    @Test fun unknownNodeIsNotSilentlyMovedToMainSite() {
        try { BtbPrintSnapshot.renderItem(item, emptyMap(), emptyMap()); fail("Missing node accepted") }
        catch (_: IllegalStateException) { }
    }
    @Test fun sameCaptionMatchesOnlyTheSameCompanyAndNode() {
        assertEquals("e", BtbPhotoEntryMatcher.find(entry(), Bildtyp.FOTODOKUMENTATION, "pittrich", "MHH-W550", " SCHWELLENWECHSEL "))
        assertNull(BtbPhotoEntryMatcher.find(entry(), Bildtyp.FOTODOKUMENTATION, "Lst Werner", "MHH-W550", "Schwellenwechsel"))
        assertNull(BtbPhotoEntryMatcher.find(entry(), Bildtyp.FOTODOKUMENTATION, "Pittrich", "HOFHAM", "Schwellenwechsel"))
    }
    @Test fun duplicateCaptionsRequireExplicitChoice() {
        assertNull(BtbPhotoEntryMatcher.find(entry(listOf(item, item.copy(clientEntryKey = "other"))), Bildtyp.FOTODOKUMENTATION, "Pittrich", "MHH-W550", "Schwellenwechsel"))
    }
    @Test fun differentBtbSectionDoesNotMatch() {
        assertNull(BtbPhotoEntryMatcher.find(entry(), Bildtyp.ABNAHMEN, "Pittrich", "MHH-W550", "Schwellenwechsel"))
    }
    @Test fun differentCompanyIdsNeverMatchByName() {
        assertNull(BtbPhotoEntryMatcher.find(entry(), Bildtyp.FOTODOKUMENTATION, "Pittrich", "MHH-W550", "Schwellenwechsel", "other-firm"))
    }
}
