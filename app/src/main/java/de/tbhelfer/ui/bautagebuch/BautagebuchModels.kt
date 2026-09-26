package de.tbhelfer.ui.bautagebuch

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Datenmodell für einen Bautagebuch-Eintrag.
 *
 * Wichtige Leitlinie:
 * - Single Source of Truth ist der persistierte Eintrag (JSON-Datei).
 * - UI schreibt Änderungen in dieses Modell, PDF/Export lesen ausschließlich daraus.
 */

enum class SchichtTyp {
    TAG,
    NACHT
}

/**
 * Fachlicher Lebenszyklus eines modernen BTB.
 *
 * ENTWURF: noch nie erfolgreich abgeschlossen.
 * ABGESCHLOSSEN: aktueller Datensatz entspricht dem letzten Abschluss; genau ein Abschluss-PDF ist gültig.
 * NACHBEARBEITUNG: nach einem Abschluss wurden Daten tatsächlich geändert; das alte PDF ist ungültig/gelöscht.
 */
enum class BtbAbschlussStatus {
    ENTWURF,
    ABGESCHLOSSEN,
    NACHBEARBEITUNG
}

/** Quelle der Wetterdaten (für UI/Statusanzeige). */
enum class WeatherSource {
    AUTO,
    MANUAL,
    CACHE
}

data class WetterSlot(
    val uhrzeit: String,
    val tempC: Int? = null,
    val bewoelkung: String? = null,
    val niederschlag: String? = null,
    val niederschlagMm: Double? = null
)

data class WeatherBlock(
    val standEpochMs: Long? = null,
    val source: WeatherSource = WeatherSource.AUTO,
    val locationName: String? = null,
    val stationName: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val slots: List<WetterSlot> = emptyList()
)

data class SectionItem(
    /**
     * Stabile fachliche Identität dieses BTB-Eintrags.
     *
     * Bei Server-BTBs entspricht sie dem vorhandenen Android-/Server-Slot
     * (_tb_android_slot). Sie bleibt bei einem Wechsel zwischen Punkt 4–8 erhalten.
     */
    val clientEntryKey: String? = null,
    val uhrzeit: String? = null,
    val text: String,
    val kmVon: Double? = null,
    val kmBis: Double? = null,
    /** Stabile BTB-interne Firmenzuordnung; null = Allgemein. */
    val firmaId: String? = null,
    /** Nur Darstellung/Fallback. Die Identität ist firmaId. */
    val firmaName: String? = null,
    /**
     * Kanonische geräteübergreifende Unterbaustellenreferenz.
     * Wert = clientNodeKey des Worksite-Node-Bindings; null = Hauptbaustelle.
     */
    val worksiteNodeKey: String? = null
)

data class SectionBlock(
    val items: List<SectionItem> = emptyList()
)

data class FirmaBlock(
    val firmaId: String,
    val name: String,
    val personalVon: String? = null,
    val personalBis: String? = null,
    val maschinenVon: String? = null,
    val maschinenBis: String? = null,
    val visibleInPersonal: Boolean = true,
    val visibleInMaschinen: Boolean = true,
    val isDeleted: Boolean = false,
    val personal: List<PersonalZeile> = emptyList(),
    val geraete: List<GeraetZeile> = emptyList(),
    val leistungen: List<LeistungZeile> = emptyList()
)

data class PersonalZeile(
    val anzahl: Int? = null,
    val rolle: String
)

data class GeraetZeile(
    val anzahl: Int? = null,
    val bezeichnung: String
)

data class LeistungZeile(
    val uhrzeit: String? = null,
    val text: String
)

data class FotoRef(
    val fotoId: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val km: Double? = null,
    val objektkennung: String? = null,
    val zusatzinfo: String? = null,
    val erstelltAmEpochMs: Long? = null,
    val bildtyp: String? = null,
    val indikator: String? = null
)

data class BautagebuchEintrag(
    val baustelleName: String,
    val baustelleId: Long,

    /** Dauerhafte fachliche Identität. Sie bleibt bei Bearbeitung/Abschluss immer gleich. */
    val btbId: String? = null,

    /** ISO-Arbeitsbeginn-Datum: yyyy-MM-dd. */
    val datumIso: String,

    /** Optionales Enddatum; wird bei Mitternachtswechsel automatisch abgeleitet. */
    var endeDatumIso: String? = null,

    /** Nur Anzeige-/Legacy-Klassifikation, nie technische Identität. */
    val schicht: SchichtTyp = SchichtTyp.TAG,

    val projektNameSnapshot: String? = null,
    val strecke: String? = null,
    val startUhrzeit: String? = null,
    val endeUhrzeit: String? = null,

    val wetter: String? = null,
    val weather: WeatherBlock? = null,
    val weatherSlots: List<WetterSlot> = emptyList(),

    val firmen: List<FirmaBlock> = emptyList(),
    val personal: List<String> = emptyList(),
    val geraete: List<String> = emptyList(),

    /** Punkt 4 – allgemeine ausgeführte Arbeiten ohne zwingende Firmenzuordnung. */
    val arbeiten: SectionBlock = SectionBlock(),

    /** Historische freie Notizen bleiben separat lesbar; neue Punkt-4-Arbeiten landen nicht hier. */
    val notizen: String? = null,

    val abnahmen: SectionBlock = SectionBlock(),
    val einweisungUnterweisung: SectionBlock = SectionBlock(),
    val besonderheiten: SectionBlock = SectionBlock(),
    val maengelSchaeden: SectionBlock = SectionBlock(),

    val includeFotos: Boolean = true,
    val includeBtbs: Boolean = true,
    val maxFotosInPdf: Int = 60,
    val pdfAuswahl: Map<String, List<Long>> = emptyMap(),
    val fotos: List<FotoRef> = emptyList(),

    /**
     * Neues Ein-PDF-Modell. Der letzte Abschluss-Snapshot liegt als JSON im selben BTB-Datensatz.
     * Er ist ausschließlich Vergleichsbasis für rote Änderungen und wird erst nach erfolgreichem
     * Abschluss auf den aktuellen Stand gesetzt.
     */
    val abschlussStatus: BtbAbschlussStatus = BtbAbschlussStatus.ENTWURF,
    val letzterAbschlussSnapshotJson: String? = null,
    val abschlussPdfDateiname: String? = null,
    val letzterAbschlussEpochMs: Long? = null,
    val letzterAbschlussBenutzer: String? = null,

    /**
     * Legacy-Felder bleiben lesbar, damit vorhandene 200023/200024-BTBs nicht verloren gehen.
     * Für neue BTBs ist [abschlussStatus] maßgeblich; sichtbare V1/V2/V3 werden nicht mehr geführt.
     */
    val istFertig: Boolean = false,
    val dokumentVersion: Int = 0,

    val erstelltAmEpochMs: Long = System.currentTimeMillis(),
    val geaendertAmEpochMs: Long = System.currentTimeMillis()
) {
    init {
        if (endeDatumIso.isNullOrBlank()) {
            endeDatumIso = abgeleitetesEnddatum(datumIso, startUhrzeit, endeUhrzeit)
        }
    }

    companion object {
        private val flexibleTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

        private fun abgeleitetesEnddatum(datumIso: String, start: String?, ende: String?): String? {
            val datum = runCatching { LocalDate.parse(datumIso) }.getOrNull() ?: return null
            val von = start?.trim()?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalTime.parse(it, flexibleTimeFmt) }.getOrNull() }
                ?: return null
            val bis = ende?.trim()?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalTime.parse(it, flexibleTimeFmt) }.getOrNull() }
                ?: return null
            return if (!bis.isAfter(von)) datum.plusDays(1).toString() else null
        }
    }
}
