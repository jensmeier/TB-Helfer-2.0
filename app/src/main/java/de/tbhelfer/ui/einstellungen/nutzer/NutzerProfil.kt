package de.tbhelfer.ui.einstellungen.nutzer

data class NutzerProfil(
    val firmenname: String? = null,
    val anschrift1: String? = null,
    val anschrift2: String? = null,
    val telefon: String? = null,
    val email: String? = null,
    /** Dateiname im Ordner TB-Helfer/Einstellungen/Nutzer/ (frei wählbar) */
    val logoDateiname: String? = null,
    /**
     * Default-Limit für Fotos, die beim BTB/Bautagebuch PDF automatisch mit eingefügt werden.
     * (Wird ab Patch45m unter Einstellungen → BTB / PDF eingestellt.)
     */
    val maxFotosInPdf: Int = 60
)
