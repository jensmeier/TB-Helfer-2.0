package de.tbhelfer.ui.bautagebuch

/**
 * Datenmodell für einen Bautagebuch-Eintrag.
 *
 * Wichtige Leitlinie:
 * - Single Source of Truth ist der persistierte Eintrag (JSON-Datei).
 * - UI schreibt Änderungen in dieses Modell, PDF/Export lesen ausschließlich daraus.
 */

/**
 * Historische/anzeigende Schichtklassifikation.
 *
 * Seit dem neuen BTB-Modell ist TAG/NACHT kein technischer Schlüssel mehr. Die Klassifikation
 * bleibt für Altbestände und eine optionale Anzeige erhalten und kann aus der Arbeitszeit
 * abgeleitet werden.
 */
enum class SchichtTyp {
    TAG,
    NACHT
}

/** Quelle der Wetterdaten (für UI/Statusanzeige). */
enum class WeatherSource {
    AUTO,
    MANUAL,
    CACHE
}

/** Strukturierter Wetter-Eintrag (für PDF-Tabellen-Ausgabe). */
data class WetterSlot(
    /** Uhrzeit im Format HH:mm */
    val uhrzeit: String,
    /** Temperatur in °C (optional) */
    val tempC: Int? = null,
    /** Bewölkung / Zustand (Dropdown) */
    val bewoelkung: String? = null,
    /** Niederschlag (Dropdown/Text) */
    val niederschlag: String? = null,
    /** Niederschlagsmenge in mm (entspricht l/m²) */
    val niederschlagMm: Double? = null
)

/** Wetterblock als Bestandteil des BTB (3 Slots + Metadaten). */
data class WeatherBlock(
    val standEpochMs: Long? = null,
    val source: WeatherSource = WeatherSource.AUTO,
    /** Optional: Ortsname (Priorität 1 im PDF). */
    val locationName: String? = null,
    /** Optional: Stationsname (Priorität 2 im PDF). */
    val stationName: String? = null,
    /** Optional: Koordinaten (Priorität 3 im PDF). */
    val lat: Double? = null,
    val lon: Double? = null,
    val slots: List<WetterSlot> = emptyList()
)

/** Einheitlicher Eintrag für Tabellen/Abschnitte (optional mit Uhrzeit). */
data class SectionItem(
    val uhrzeit: String? = null,
    val text: String,
    /** Optionaler KM-Wert nur fuer den manuellen BTB-Eintrag. */
    val kmVon: Double? = null,
    val kmBis: Double? = null
)

data class SectionBlock(
    val items: List<SectionItem> = emptyList()
)

/**
 * Firma-Block (gemeinsame Quelle für Personal + Geräte).
 *
 * Die UI kann Personal/Geräte getrennt darstellen, aber die Firma ist identisch.
 */
data class FirmaBlock(
    /** Stabiler Key (UUID als String empfohlen). */
    val firmaId: String,
    val name: String,
    /** Zeitfenster (optional, HH:mm). */
    val personalVon: String? = null,
    val personalBis: String? = null,
    val maschinenVon: String? = null,
    val maschinenBis: String? = null,
    /**
     * Sichtbarkeit der Firma in den beiden PM-Sektionen.
     *
     * UX-Vorgabe: Das Löschen einer Firma soll nur in der jeweiligen Sektion entfernen,
     * nicht automatisch in beiden.
     */
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

/** Referenz auf ein Foto (für PDF-Anhang). */
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

    /**
     * Stabile fachliche BTB-ID. Neue BTBs erhalten beim bewussten Anlegen eine UUID.
     * Altbestände können null sein und werden beim Einlesen/Sync über einen stabilen Legacy-Key
     * identifiziert, ohne ihre historischen Dateien umzubenennen.
     */
    val btbId: String? = null,

    /** ISO-Arbeitsbeginn-Datum: yyyy-MM-dd. */
    val datumIso: String,

    /**
     * Optionales ISO-Enddatum. Bei null gilt für Altbestände/Tageseinsätze [datumIso].
     * Damit kann ein BTB sauber über Mitternacht laufen.
     */
    val endeDatumIso: String? = null,

    /** Nur Anzeige-/Legacy-Klassifikation, nicht mehr Teil der BTB-Identität. */
    val schicht: SchichtTyp = SchichtTyp.TAG,

    // Kopf: optional, damit ein BTB historisch konsistent bleibt
    val projektNameSnapshot: String? = null,

    // Legacy / bestehende UI-Felder
    val strecke: String? = null,
    val startUhrzeit: String? = null,
    val endeUhrzeit: String? = null,

    /** Legacy-Feld (freier Text). */
    val wetter: String? = null,

    /**
     * Neue Wetterstruktur.
     * Hinweis: weatherSlots bleibt zusätzlich erhalten, weil bestehende UI/PDF Teile es bereits nutzen.
     */
    val weather: WeatherBlock? = null,

    /**
     * Abwärtskompatibel: bestehender Code nutzt dieses Feld in UI/PDF.
     * Langfristig kann alles über [weather] laufen.
     */
    val weatherSlots: List<WetterSlot> = emptyList(),

    /** Firmenblöcke (Personal/Geräte/Leistungen). */
    val firmen: List<FirmaBlock> = emptyList(),

    /**
     * Legacy-Felder (wurden früher als einfache Textlisten geführt).
     * Bleiben für Abwärtskompatibilität bestehen, bis die UI vollständig auf [firmen] umgestellt ist.
     */
    val personal: List<String> = emptyList(),
    val geraete: List<String> = emptyList(),
    val notizen: String? = null,

    /** Standardabschnitte */
    val abnahmen: SectionBlock = SectionBlock(),
    val einweisungUnterweisung: SectionBlock = SectionBlock(),
    val besonderheiten: SectionBlock = SectionBlock(),
    val maengelSchaeden: SectionBlock = SectionBlock(),

    /** PDF-Anhang */
    val includeFotos: Boolean = true,
    val includeBtbs: Boolean = true,

    /** Max. Anzahl Fotos im PDF (Default 60, kann über Nutzer-Einstellungen vorbelegt werden). */
    val maxFotosInPdf: Int = 60,

    /** Auswahl: welche Fotos ins PDF sollen (BlockKey -> Foto-Ids). */
    val pdfAuswahl: Map<String, List<Long>> = emptyMap(),

    /** Foto-Referenzen (optional – wird vom PDF-Generator genutzt). */
    val fotos: List<FotoRef> = emptyList(),

    /**
     * Wenn true, wurde der Eintrag fachlich geprüft/abgeschlossen.
     * Änderungen nach dem Abschluss dürfen den historischen Abschluss nicht zerstören; beim
     * nächsten Dokumentabschluss wird eine neue Dokumentversion erzeugt.
     */
    val istFertig: Boolean = false,

    /** Letzte abgeschlossene PDF/DOCX-Version; 0 = noch nie abgeschlossen. */
    val dokumentVersion: Int = 0,
    val letzterAbschlussEpochMs: Long? = null,
    val letzterAbschlussBenutzer: String? = null,

    val erstelltAmEpochMs: Long = System.currentTimeMillis(),
    val geaendertAmEpochMs: Long = System.currentTimeMillis()
)
