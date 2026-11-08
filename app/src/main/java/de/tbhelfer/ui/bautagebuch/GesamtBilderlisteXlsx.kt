package de.tbhelfer.ui.bautagebuch

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Kleine XLSX-Erzeugung ohne Apache POI.
 *
 * Die Datei ist fuer den Langzeitordner auf dem Laptop gedacht: Filter-/Sortierpfeile,
 * eingefrorene Kopfzeile, lesbare Spaltenbreiten, Zebra-Zeilen und anklickbare relative
 * Bildpfade. Die Gesamtliste wird ausschließlich als XLSX ausgeliefert.
 */
object GesamtBilderlisteXlsx {

    data class Row(
        val nr: Int,
        val datum: String,
        val uhrzeit: String,
        val ersteller: String,
        val verantwortlicherTb: String,
        val art: String,
        val baustelle: String,
        val bereich: String,
        val km: String,
        val firma: String,
        val kurzbeschreibung: String,
        val langtext: String,
        val status: String,
        val bilddatei: String,
        val gps: String,
        /** Relativer Pfad ausgehend von Bilderliste_gesamt.xlsx im Baustellenordner. */
        val relativePath: String
    )

    /** Original und Sync-Kopie mit demselben Exportziel nur einmal auffuehren. */
    internal fun eindeutigNummeriert(rows: List<Row>): List<Row> {
        val seen = hashSetOf<String>()
        return rows.filter { it.relativePath.isBlank() || seen.add(it.relativePath) }
            .mapIndexed { index, row -> row.copy(nr = index + 1) }
    }

    private val headers = listOf(
        "Nr.", "Datum", "Uhrzeit", "Ersteller", "Verantwortlicher TB", "Art",
        "Baustelle", "Bereich/Gleis", "KM", "Firma", "Kurzbeschreibung", "Langtext / BTB-Text",
        "Status", "Bilddatei", "GPS"
    )

    fun csv(rows: List<Row>): String = buildString {
        appendLine(headers.joinToString(";") { csvCell(it) })
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.nr.toString(), row.datum, row.uhrzeit, row.ersteller,
                    row.verantwortlicherTb, row.art, row.baustelle, row.bereich, row.km,
                    row.firma, row.kurzbeschreibung, row.langtext, row.status, row.bilddatei, row.gps
                ).joinToString(";") { csvCell(it) }
            )
        }
    }

    fun bytes(rows: List<Row>, sheetName: String = "Bilder gesamt"): ByteArray {
        val columns = headers.mapIndexed { i, title ->
            ExportTabelleXlsx.Column(title, listOf(7, 12, 10, 20, 20, 20, 24, 32, 16, 22, 36, 48, 26, 18, 25)[i],
                when(i) { 0 -> ExportTabelleXlsx.Format.NUMBER; 1 -> ExportTabelleXlsx.Format.DATE; 2 -> ExportTabelleXlsx.Format.TIME; else -> ExportTabelleXlsx.Format.TEXT })
        }
        val data = rows.map { r ->
            val values = listOf(r.nr.toString(), r.datum, r.uhrzeit, r.ersteller, r.verantwortlicherTb,
                r.art, r.baustelle, r.bereich, r.km, r.firma, r.kurzbeschreibung, r.langtext,
                r.status, if(r.relativePath.isBlank()) "Nicht im Export" else "Bild öffnen", r.gps)
            values.mapIndexed { i, value -> ExportTabelleXlsx.Cell(value, when(i) {
                13 -> r.relativePath.takeIf { it.isNotBlank() }
                14 -> gpsTarget(r.gps)
                else -> null
            }) }
        }
        return ExportTabelleXlsx.bytes(listOf(ExportTabelleXlsx.Sheet(sheetName, columns, data)))
    }

    internal fun gpsTarget(raw: String): String? {
        val parts = raw.split(',').map { it.trim().toDoubleOrNull() }
        if(parts.size != 2) return null
        val lat = parts[0] ?: return null
        val lon = parts[1] ?: return null
        if(!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return "https://www.google.com/maps?q=$lat,$lon"
    }
    private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
}
