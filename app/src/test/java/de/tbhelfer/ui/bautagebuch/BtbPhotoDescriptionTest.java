package de.tbhelfer.ui.bautagebuch;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BtbPhotoDescriptionTest {
    @Test public void captionWithoutLongText() {
        assertEquals("Schwellenwechsel", BtbPhotoDescription.forBtb("Schwellenwechsel", null));
    }
    @Test public void bothTextsRemainVisible() {
        assertEquals("Schwellenwechsel – 12 Schwellen ersetzt", BtbPhotoDescription.forBtb("Schwellenwechsel", "12 Schwellen ersetzt"));
    }
    @Test public void identicalTextsAreNotRepeated() {
        assertEquals("Schwellenwechsel", BtbPhotoDescription.forBtb("Schwellenwechsel", "Schwellenwechsel"));
    }
    @Test public void legacyLongTextWithoutCaption() {
        assertEquals("Details", BtbPhotoDescription.forBtb(null, "Details"));
    }
    @Test public void absentTexts() { assertEquals("", BtbPhotoDescription.forBtb(null, null)); }
    @Test public void trimInput() { assertEquals("Kurz – Lang", BtbPhotoDescription.forBtb(" Kurz ", " Lang ")); }
}
