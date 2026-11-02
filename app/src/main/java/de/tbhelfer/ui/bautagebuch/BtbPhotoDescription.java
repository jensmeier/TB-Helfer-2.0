package de.tbhelfer.ui.bautagebuch;

/** Display text only. Both persisted photo fields retain their separate meaning. */
public final class BtbPhotoDescription {
    private BtbPhotoDescription() {}

    public static String forBtb(String shortText, String longText) {
        String caption = shortText == null ? "" : shortText.trim();
        String detail = longText == null ? "" : longText.trim();
        if (detail.isEmpty()) return caption;
        if (caption.isEmpty() || caption.equalsIgnoreCase(detail)) return detail;
        return caption + " – " + detail;
    }
}
