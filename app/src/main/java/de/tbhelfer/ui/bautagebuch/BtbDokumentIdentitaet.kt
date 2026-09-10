package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.TeamDisplayCache
import de.tbhelfer.ui.einstellungen.nutzer.NutzerSpeicher

/**
 * Eine gemeinsame Quelle für die menschenlesbare BTB-Dokumentidentität.
 *
 * Technische Beziehungen bleiben immer bei Server-/Gruppen-UUIDs. Diese Klasse erzeugt nur
 * Anzeige- und Exportwerte, damit Verlauf, PDF, DOCX und ZIP dieselben Namen verwenden.
 */
object BtbDokumentIdentitaet {

    data class Identitaet(
        val firmenname: String?,
        val baustelleName: String,
        val verantwortlicherTb: String,
        val verantwortlicherToken: String,
        val datumIso: String,
        val schicht: SchichtTyp,
        val schichtAnzeige: String,
        val schichtToken: String,
        val worksiteServerId: String?,
        val groupId: String?
    ) {
        val anzeigeTitel: String
            get() = "BTB $verantwortlicherTb · $datumIso · $schichtAnzeige"

        val dateiBasis: String
            get() = "BTB_${datumIso}_${schichtToken}_${verantwortlicherToken}"

        fun pdfDateiname(): String = "$dateiBasis.pdf"
        fun docxDateiname(): String = "$dateiBasis.docx"
    }

    suspend fun aufloesen(context: Context, entry: BautagebuchEintrag): Identitaet {
        val appContext = context.applicationContext
        val root = runCatching {
            ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(entry.baustelleId)
        }.getOrNull()
        val syncContext = root?.id?.let { rootId ->
            runCatching { ServiceLocator.worksiteBindingRepository.contextForLocalRoot(rootId) }.getOrNull()
        }
        val ownTeam = syncContext?.worksiteServerId?.let { worksiteId ->
            TeamDisplayCache.ownTeam(appContext, worksiteId)
        }

        // Namen sind ausschließlich Anzeige. Die UUIDs darunter bleiben die technische Identität.
        val responsible = ownTeam?.leadDisplayName
            ?: ownTeam?.leadNameSnapshot
            ?: syncContext?.leadNameSnapshot
            ?: syncContext?.groupLabel
            ?: "TB"

        val shiftDisplay = when (entry.schicht) {
            SchichtTyp.TAG -> "Tag"
            SchichtTyp.NACHT -> "Nacht"
        }
        val shiftToken = entry.schicht.name

        return Identitaet(
            firmenname = NutzerSpeicher.laden(appContext)?.firmenname?.trim()?.takeIf { it.isNotBlank() },
            baustelleName = entry.baustelleName,
            verantwortlicherTb = responsible.trim().ifBlank { "TB" },
            verantwortlicherToken = safeToken(responsible).ifBlank { "TB" },
            datumIso = entry.datumIso,
            schicht = entry.schicht,
            schichtAnzeige = shiftDisplay,
            schichtToken = shiftToken,
            worksiteServerId = syncContext?.worksiteServerId,
            groupId = ownTeam?.groupId ?: syncContext?.groupId
        )
    }

    fun safeToken(value: String): String = value
        .trim()
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_', '.', ' ')
        .take(100)
}
