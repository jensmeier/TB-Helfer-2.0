package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.server.TeamDisplayCache
import de.tbhelfer.server.UserDisplayCache
import de.tbhelfer.ui.einstellungen.nutzer.NutzerSpeicher

/**
 * Eine gemeinsame Quelle für die menschenlesbare BTB-Dokumentidentität.
 *
 * Technische Beziehungen bleiben immer bei Server-/Gruppen-/BTB-UUIDs. Datum, Uhrzeit und eine
 * abgeleitete Tag-/Nacht-Anzeige sind nur menschenlesbare Attribute und keine technischen Schlüssel.
 */
object BtbDokumentIdentitaet {

    data class Identitaet(
        val firmenname: String?,
        val baustelleName: String,
        val verantwortlicherTb: String,
        val verantwortlicherToken: String,
        val btbId: String?,
        val btbToken: String,
        val datumIso: String,
        val endeDatumIso: String?,
        val startUhrzeit: String?,
        val endeUhrzeit: String?,
        val dokumentVersion: Int,
        val abschlussEpochMs: Long? = null,
        val abschlussBenutzer: String? = null,
        // Legacy-/Anzeigeinformationen bleiben für Altbestände erhalten.
        val schicht: SchichtTyp,
        val schichtAnzeige: String,
        val schichtToken: String,
        val worksiteServerId: String?,
        val groupId: String?
    ) {
        val zeitraumAnzeige: String
            get() {
                val date = if (!endeDatumIso.isNullOrBlank() && endeDatumIso != datumIso) "$datumIso – $endeDatumIso" else datumIso
                val time = listOf(startUhrzeit, endeUhrzeit).filter { !it.isNullOrBlank() }.joinToString("–")
                return if (time.isBlank()) date else "$date · $time"
            }

        val anzeigeTitel: String
            get() = "BTB $verantwortlicherTb · $zeitraumAnzeige"

        /**
         * Merge-sichere Basis. Zwei verschiedene BTBs desselben Tages dürfen nie kollidieren.
         * Für Altbestände ohne UUID bleibt der historische Schichttoken Bestandteil der Basis.
         */
        val dateiBasis: String
            get() {
                val timeToken = listOf(startUhrzeit, endeUhrzeit)
                    .filter { !it.isNullOrBlank() }
                    .joinToString("-") { safeToken(it.orEmpty()).ifBlank { "Zeit" } }
                    .ifBlank { schichtToken }
                return "BTB_${datumIso}_${timeToken}_${verantwortlicherToken}_${btbToken}"
            }

        fun pdfDateiname(version: Int? = null): String {
            val v = version ?: dokumentVersion.takeIf { it > 0 }
            return if (v != null) "${dateiBasis}_V$v.pdf" else "$dateiBasis.pdf"
        }

        fun docxDateiname(version: Int? = null): String {
            val v = version ?: dokumentVersion.takeIf { it > 0 }
            return if (v != null) "${dateiBasis}_V$v.docx" else "$dateiBasis.docx"
        }
    }

    /**
     * Löst die sichtbare Dokumentidentität auf und bereitet gleichzeitig die nächste belastbare
     * Dokumentversion vor. Die eigentliche Bestätigung geschieht erst, wenn die erwartete Vn-PDF
     * auf dem Datenträger gefunden wird; Entwürfe oder abgebrochene PDF-Läufe zählen daher nicht.
     */
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

        val responsible = ownTeam?.leadDisplayName
            ?: ownTeam?.leadNameSnapshot
            ?: syncContext?.leadNameSnapshot
            ?: syncContext?.groupLabel
            ?: UserDisplayCache.userName(appContext)
            ?: "TB"

        val shiftDisplay = when (entry.schicht) {
            SchichtTyp.TAG -> "Tag"
            SchichtTyp.NACHT -> "Nacht"
        }
        val shiftToken = entry.schicht.name
        val stableBtbToken = BtbDokumentVersionierung.dokumentToken(entry)

        val base = Identitaet(
            firmenname = NutzerSpeicher.laden(appContext)?.firmenname?.trim()?.takeIf { it.isNotBlank() },
            baustelleName = entry.baustelleName,
            verantwortlicherTb = responsible.trim().ifBlank { "TB" },
            verantwortlicherToken = safeToken(responsible).ifBlank { "TB" },
            btbId = entry.btbId,
            btbToken = stableBtbToken,
            datumIso = entry.datumIso,
            endeDatumIso = entry.endeDatumIso,
            startUhrzeit = entry.startUhrzeit,
            endeUhrzeit = entry.endeUhrzeit,
            dokumentVersion = entry.dokumentVersion.coerceAtLeast(0),
            abschlussEpochMs = entry.letzterAbschlussEpochMs,
            abschlussBenutzer = entry.letzterAbschlussBenutzer,
            schicht = entry.schicht,
            schichtAnzeige = shiftDisplay,
            schichtToken = shiftToken,
            worksiteServerId = syncContext?.worksiteServerId,
            groupId = ownTeam?.groupId ?: syncContext?.groupId
        )

        val prepared = BtbDokumentVersionierung.vorbereiten(
            context = appContext,
            entry = entry,
            expectedPdfName = { version -> base.pdfDateiname(version) },
            benutzer = base.verantwortlicherTb
        )
        return base.copy(
            dokumentVersion = prepared.naechsteVersion,
            abschlussEpochMs = prepared.abschlussEpochMs,
            abschlussBenutzer = prepared.benutzer
        )
    }

    fun safeToken(value: String): String = value
        .trim()
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_', '.', ' ')
        .take(100)
}
