package de.tbhelfer.ui.bautagebuch

/**
 * Gemeinsames Druck-/Exportmodell für das Bautagebuch.
 *
 * Ziel: PDF, DOCX und spätere weitere Formate dürfen nicht mehr jeweils eigene
 * Sortierungen/Gruppierungen bauen. Dieses Modell ist die gemeinsame fachliche
 * Struktur. Renderer dürfen nur noch darstellen.
 */
data class BtbDruckModell(
    val baustelle: String,
    val datumAnzeige: String,
    val zeitAnzeige: String,
    val wetter: List<String>,
    /** Kompakte Zusatzzeile wie im PDF-Wetterblock, z. B. "Ort: Heideck   Stand: 11:31   Quelle: AUTO". */
    val wetterInfo: String? = null,
    val personal: List<BtbDruckFirmaListe>,
    val maschinen: List<BtbDruckFirmaListe>,
    val abschnitte: List<BtbDruckAbschnitt>
)

data class BtbDruckFirmaListe(
    val kopf: String,
    val zeilen: List<String>
)

data class BtbDruckAbschnitt(
    val titel: String,
    val leerText: String,
    val firmen: List<BtbDruckFirma>
)

data class BtbDruckFirma(
    val name: String,
    val orte: List<BtbDruckOrt>
)

data class BtbDruckOrt(
    val name: String,
    val eintraege: List<String>,
    /** 0 = direkt unter der Baustelle/Hauptbaustelle, 1+ = Kind einer Unterbaustelle. */
    val depth: Int = 0,
    /** Anzeigename des direkten Elternbereichs, wenn dieser Ort eingerückt werden soll. */
    val parentName: String? = null
)
