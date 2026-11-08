package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import de.tbhelfer.ui.baustelle.MaengellisteExport
import de.tbhelfer.ui.baustelle.MangelStatusSpeicher
import java.time.Instant
import java.time.ZoneId

/** One row per existing domain record; photos are a separate, keyed child table. */
object FachlistenXlsx {
    private fun c(text: String, width: Int = 22, format: ExportTabelleXlsx.Format = ExportTabelleXlsx.Format.TEXT) = ExportTabelleXlsx.Column(text, width, format)
    private fun v(text: Any?) = ExportTabelleXlsx.Cell(text?.toString().orEmpty())
    private fun timestamp(epoch: Long?) = epoch?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString() }.orEmpty()
    private val photoColumns = listOf(c("Vorgang-ID", 38), c("Foto-ID", 18), c("Fotoart"), c("Bemerkung", 45), c("Bilddatei", 18))
    private fun photo(id: String, fotoId: Long, art: String, note: String, target: String?) = listOf(v(id), v(fotoId), v(art), v(note),
        ExportTabelleXlsx.Cell(if(target.isNullOrBlank()) "Nicht im Export" else "Bild öffnen", target))

    fun maengel(context: Context, items: List<MaengellisteExport.MangelItem>, target: (String, Long) -> String?): ByteArray {
        val columns = listOf(c("Nr.", 7, ExportTabelleXlsx.Format.NUMBER), c("Status", 18), c("Datum", 12, ExportTabelleXlsx.Format.DATE),
            c("Uhrzeit", 10, ExportTabelleXlsx.Format.TIME), c("Firma"), c("Unterbaustelle / Gleis", 32), c("KM", 16), c("Beschreibung", 48),
            c("Kommentar", 38), c("Erledigt am", 20, ExportTabelleXlsx.Format.DATETIME), c("Fotos", 8, ExportTabelleXlsx.Format.NUMBER),
            c("Fotos im Export", 14, ExportTabelleXlsx.Format.NUMBER), c("Baustelle", 28), c("BTB-Bildnummer", 18), c("Vorgang-ID", 38))
        val rows = items.mapIndexed { i, item ->
            val state = MangelStatusSpeicher.laden(context, item.key)
            listOf(v(i+1), v(MaengellisteExport.statusText(state.status)), v(item.datumIso), v(item.zeit), v(item.firma), v(item.bereich), v(item.km),
                v(item.beschreibung), v(state.kommentar), v(timestamp(state.erledigtAmEpochMs)), v(item.fotos.size),
                v(item.fotos.count { !target(item.key, it.foto.id).isNullOrBlank() }), v(item.baustelle), v(item.btbBildNr), v(item.key))
        }
        val photos = items.flatMap { item -> item.fotos.map { f -> photo(item.key, f.foto.id, MaengellisteExport.fotoArtText(f.art), f.foto.notiz.orEmpty(), target(item.key, f.foto.id)) } }
        return ExportTabelleXlsx.bytes(listOf(ExportTabelleXlsx.Sheet("Mängel", columns, rows), ExportTabelleXlsx.Sheet("Fotos", photoColumns, photos)))
    }

    fun abnahmen(items: List<AbnahmeSpeicher.Abnahme>, target: (String, Long) -> String?): ByteArray {
        val columns = listOf(c("Nummer", 9, ExportTabelleXlsx.Format.NUMBER), c("Ergebnis / Status", 26), c("Beginn", 20, ExportTabelleXlsx.Format.DATETIME),
            c("Abschluss", 20, ExportTabelleXlsx.Format.DATETIME), c("Firma"), c("Unterbaustelle / Gleis", 32), c("Bezeichnung", 36), c("Objekt", 28),
            c("KM von", 14, ExportTabelleXlsx.Format.KM), c("KM bis", 14, ExportTabelleXlsx.Format.KM), c("Abnehmender"), c("Anwesende", 30),
            c("Restpunkte", 42), c("Kommentar", 42), c("Fotos", 8, ExportTabelleXlsx.Format.NUMBER), c("Fotos im Export", 14, ExportTabelleXlsx.Format.NUMBER),
            c("Planunterlagen", 14, ExportTabelleXlsx.Format.NUMBER), c("Weitere Firmen", 28), c("Baustelle", 28), c("Vorgang-ID", 38))
        val sorted = items.sortedBy { it.nummer }
        val rows = sorted.map { a -> listOf(v(a.nummer), v(AbnahmeSpeicher.statusLabel(a)), v(timestamp(a.startEpochMs)), v(timestamp(a.abgeschlossenAmEpochMs)),
            v(a.firma), v(a.unterKnotenName), v(a.bezeichnung), v(a.objekt), v(a.kmVon), v(a.kmBis), v(a.abnehmender), v(a.anwesende), v(a.restpunkte),
            v(a.kommentar), v(a.fotos.size), v(a.fotos.count { !target(a.id, it.fotoId).isNullOrBlank() }), v(a.anlagen.size), v(a.weitereFirmen), v(a.baustelleName), v(a.id)) }
        val photos = sorted.flatMap { a -> a.fotos.map { f -> photo(a.id, f.fotoId, AbnahmeSpeicher.bildArtLabel(f.bildArt), f.bemerkung, target(a.id, f.fotoId)) } }
        return ExportTabelleXlsx.bytes(listOf(ExportTabelleXlsx.Sheet("Abnahmen", columns, rows), ExportTabelleXlsx.Sheet("Fotos", photoColumns, photos)))
    }
}
