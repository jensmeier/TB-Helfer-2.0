package de.tbhelfer.vermessung

import java.util.Locale

/** Fachliche UI-Kategorie fuer importierte Vermessungspunkte. */
object VermessungspunktKategorie {
    const val GVP = "GVP"
    const val FESTPUNKT = "FESTPUNKT"

    fun grafikArt(originalTyp: String?, quelleDatei: String?): String {
        val typ = originalTyp?.trim()?.uppercase(Locale.GERMANY)?.takeIf { it.isNotBlank() }
        val quelle = quelleDatei.orEmpty().lowercase(Locale.GERMANY)
        val istKfQuelle = quelle.endsWith(".kf") || quelle.endsWith(".kf.txt")

        return when {
            typ == "PS4" || typ?.startsWith("GVP") == true -> GVP
            typ in setOf("PS0", "PS1", "PS2", "PS3", "GPSC", "HBH", "PPB") -> FESTPUNKT
            istKfQuelle -> FESTPUNKT
            // Bestehende GVP-/TXT-Importe ohne Original-PArt sollen sich durch 006
            // nicht veraendern. Unbekannte rohe KF-Punkte werden oben bereits als
            // Festpunkt eingeordnet.
            else -> GVP
        }
    }
}
