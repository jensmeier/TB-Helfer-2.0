package de.tbhelfer.ui.abnahme

import android.content.Context
import android.net.Uri
import de.tbhelfer.ui.kamera.FotoSpeicher
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Strukturierte Abnahmen ohne Room-Migration.
 *
 * Die fachlichen Daten liegen als JSON in SharedPreferences. Bilder/PDFs liegen im
 * vorhandenen Baustellenordner unter Abnahmen/<Startdatum>/Abnahme_XXX_... .
 */
object AbnahmeSpeicher {

    enum class Ergebnis {
        ENTWURF,
        ABGENOMMEN,
        NICHT_ABGENOMMEN,
        ABGENOMMEN_MIT_RESTPUNKTEN,
        NOCH_ZU_PRUEFEN
    }

    enum class BildArt {
        AUSGANGSZUSTAND,
        AUSFUEHRUNG,
        MESSUNG,
        DETAIL,
        RESTPUNKT,
        PLAN_QUERPROFIL,
        SONSTIGES
    }

    enum class AnlageTyp { BILD, PDF }

    data class FotoRef(
        val fotoId: Long,
        val bildArt: BildArt = BildArt.SONSTIGES,
        val bemerkung: String = "",
        val erstelltAmEpochMs: Long = System.currentTimeMillis()
    )

    data class Anlage(
        val id: String = UUID.randomUUID().toString(),
        val typ: AnlageTyp,
        val originalPfad: String,
        val ausschnittPfad: String? = null,
        val bezeichnung: String = "",
        val bemerkung: String = "",
        val erstelltAmEpochMs: Long = System.currentTimeMillis()
    )

    data class Abnahme(
        val id: String = UUID.randomUUID().toString(),
        val nummer: Int,
        val baustelleId: Long,
        val baustelleName: String,
        val hauptKnotenId: Long? = null,
        val unterKnotenId: Long? = null,
        val unterKnotenName: String = "",
        val bezeichnung: String = "",
        val objekt: String = "",
        val firma: String = "",
        val weitereFirmen: String = "",
        val abnehmender: String = "",
        val anwesende: String = "",
        val kmVon: Double? = null,
        val kmBis: Double? = null,
        val kommentar: String = "",
        val restpunkte: String = "",
        val ergebnis: Ergebnis = Ergebnis.ENTWURF,
        val startEpochMs: Long = System.currentTimeMillis(),
        val geaendertAmEpochMs: Long = System.currentTimeMillis(),
        val abgeschlossenAmEpochMs: Long? = null,
        val wiederGeoeffnetAmEpochMs: Long? = null,
        val fotos: List<FotoRef> = emptyList(),
        val anlagen: List<Anlage> = emptyList()
    ) {
        val istEndgueltigAbgeschlossen: Boolean
            get() = abgeschlossenAmEpochMs != null &&
                (ergebnis == Ergebnis.ABGENOMMEN || ergebnis == Ergebnis.NICHT_ABGENOMMEN)

        val startDatumIso: String
            get() = Instant.ofEpochMilli(startEpochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }

    data class Zaehler(
        val ja: Int = 0,
        val nein: Int = 0,
        val inPruefung: Int = 0
    )

    private const val PREF = "tb_helfer_abnahmen_v1"
    private const val KEY_BEREINIGUNG_LEERE_ENTWUERFE_V1 = "__bereinigung_leere_entwuerfe_v1"

    @Synchronized
    fun neu(
        context: Context,
        baustelleId: Long,
        baustelleName: String,
        hauptKnotenId: Long? = null,
        unterKnotenId: Long? = null,
        unterKnotenName: String = ""
    ): Abnahme {
        val next = (alle(context, baustelleId).maxOfOrNull { it.nummer } ?: 0) + 1
        val wert = Abnahme(
            nummer = next,
            baustelleId = baustelleId,
            baustelleName = baustelleName,
            hauptKnotenId = hauptKnotenId,
            unterKnotenId = unterKnotenId,
            unterKnotenName = unterKnotenName
        )
        speichern(context, wert)
        return wert
    }

    fun laden(context: Context, id: String): Abnahme? {
        if (id.isBlank()) return null
        val raw = prefs(context).getString(id, null) ?: return null
        return parse(raw)
    }

    fun alle(context: Context, baustelleId: Long? = null): List<Abnahme> {
        bereinigeLeereEntwuerfeEinmal(context)
        return prefs(context).all.values
            .mapNotNull { it as? String }
            .mapNotNull(::parse)
            .filter { baustelleId == null || it.baustelleId == baustelleId }
            .sortedWith(compareByDescending<Abnahme> { it.geaendertAmEpochMs }.thenByDescending { it.nummer })
    }

    /**
     * Entfernt einmalig nur die durch den früheren Kamera-Rückweg entstandenen,
     * vollständig leeren Entwürfe. Vorgänge mit irgendeinem Inhalt, Foto, Plan,
     * KM-Wert oder Beteiligten bleiben unangetastet.
     */
    @Synchronized
    fun bereinigeLeereEntwuerfeEinmal(context: Context): Int {
        val p = prefs(context)
        if (p.getBoolean(KEY_BEREINIGUNG_LEERE_ENTWUERFE_V1, false)) return 0

        val leere = p.all.values
            .mapNotNull { it as? String }
            .mapNotNull(::parse)
            .filter(::istVollstaendigLeererEntwurf)

        val editor = p.edit()
        leere.forEach { editor.remove(it.id) }
        editor.putBoolean(KEY_BEREINIGUNG_LEERE_ENTWUERFE_V1, true)
        editor.commit()

        leere.forEach { a ->
            runCatching { abnahmeOrdner(context, a).deleteRecursively() }
        }
        return leere.size
    }

    private fun istVollstaendigLeererEntwurf(a: Abnahme): Boolean {
        return a.ergebnis == Ergebnis.ENTWURF &&
            a.abgeschlossenAmEpochMs == null &&
            a.fotos.isEmpty() &&
            a.anlagen.isEmpty() &&
            a.bezeichnung.isBlank() &&
            a.objekt.isBlank() &&
            a.firma.isBlank() &&
            a.weitereFirmen.isBlank() &&
            a.abnehmender.isBlank() &&
            a.anwesende.isBlank() &&
            a.kmVon == null &&
            a.kmBis == null &&
            a.kommentar.isBlank() &&
            a.restpunkte.isBlank()
    }

    fun zaehler(context: Context, baustelleId: Long): Zaehler {
        var ja = 0
        var nein = 0
        var pruefung = 0
        alle(context, baustelleId).forEach { a ->
            when (filterKey(a)) {
                "JA" -> ja++
                "NEIN" -> nein++
                else -> pruefung++
            }
        }
        return Zaehler(ja, nein, pruefung)
    }

    @Synchronized
    fun speichern(context: Context, abnahme: Abnahme): Abnahme {
        val now = System.currentTimeMillis()
        val previous = laden(context, abnahme.id)
        val protected = if (previous?.istEndgueltigAbgeschlossen == true) {
            // Ein endgültiger Abschluss darf weder durch die UI noch durch einen späteren
            // Sync wieder geöffnet oder in ein anderes Ergebnis zurückgesetzt werden.
            // Zusätzliche synchronisierte Medien/Metadaten dürfen trotzdem ergänzt werden.
            abnahme.copy(
                ergebnis = previous.ergebnis,
                abgeschlossenAmEpochMs = previous.abgeschlossenAmEpochMs,
                wiederGeoeffnetAmEpochMs = previous.wiederGeoeffnetAmEpochMs
            )
        } else {
            abnahme
        }
        val normalized = protected.copy(geaendertAmEpochMs = now)
        val json = toJson(normalized).toString(2)
        prefs(context).edit().putString(normalized.id, json).commit()
        runCatching {
            val target = jsonDatei(context, normalized)
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(json, Charsets.UTF_8)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
        return normalized
    }

    @Synchronized
    fun abschliessen(context: Context, abnahme: Abnahme, ergebnis: Ergebnis): Abnahme {
        val now = System.currentTimeMillis()
        val final = ergebnis == Ergebnis.ABGENOMMEN || ergebnis == Ergebnis.NICHT_ABGENOMMEN
        return speichern(
            context,
            abnahme.copy(
                ergebnis = ergebnis,
                abgeschlossenAmEpochMs = if (final) now else null
            )
        )
    }

    /**
     * Kompatibilitätsfunktion für ältere Aufrufer. Endgültige Abnahmen werden nicht mehr
     * wieder geöffnet; der unveränderte gespeicherte Stand wird zurückgegeben.
     */
    @Deprecated("Endgültige Abnahmen dürfen nicht wieder geöffnet werden")
    @Synchronized
    fun wiederOeffnen(context: Context, abnahme: Abnahme): Abnahme = laden(context, abnahme.id) ?: abnahme

    @Synchronized
    fun fotoZuordnen(
        context: Context,
        abnahmeId: String,
        fotoId: Long,
        bildArt: BildArt,
        bemerkung: String = ""
    ): Abnahme? {
        val a = laden(context, abnahmeId) ?: return null
        val list = a.fotos.filterNot { it.fotoId == fotoId } + FotoRef(
            fotoId = fotoId,
            bildArt = bildArt,
            bemerkung = bemerkung,
            erstelltAmEpochMs = System.currentTimeMillis()
        )
        return speichern(context, a.copy(fotos = list))
    }

    @Synchronized
    fun fotoBearbeiten(context: Context, abnahmeId: String, ref: FotoRef): Abnahme? {
        val a = laden(context, abnahmeId) ?: return null
        return speichern(context, a.copy(fotos = a.fotos.map { if (it.fotoId == ref.fotoId) ref else it }))
    }

    @Synchronized
    fun fotoEntfernen(context: Context, abnahmeId: String, fotoId: Long): Abnahme? {
        val a = laden(context, abnahmeId) ?: return null
        return speichern(context, a.copy(fotos = a.fotos.filterNot { it.fotoId == fotoId }))
    }

    @Synchronized
    fun anlageHinzufuegen(context: Context, abnahmeId: String, anlage: Anlage): Abnahme? {
        val a = laden(context, abnahmeId) ?: return null
        return speichern(context, a.copy(anlagen = a.anlagen + anlage))
    }

    @Synchronized
    fun anlageBearbeiten(context: Context, abnahmeId: String, anlage: Anlage): Abnahme? {
        val a = laden(context, abnahmeId) ?: return null
        return speichern(context, a.copy(anlagen = a.anlagen.map { if (it.id == anlage.id) anlage else it }))
    }

    @Synchronized
    fun anlageEntfernen(context: Context, abnahmeId: String, anlageId: String, dateienLoeschen: Boolean = false): Abnahme? {
        val a = laden(context, abnahmeId) ?: return null
        val target = a.anlagen.firstOrNull { it.id == anlageId }
        if (dateienLoeschen && target != null) {
            runCatching { File(target.originalPfad).delete() }
            target.ausschnittPfad?.let { runCatching { File(it).delete() } }
        }
        return speichern(context, a.copy(anlagen = a.anlagen.filterNot { it.id == anlageId }))
    }

    @Synchronized
    fun loeschen(context: Context, abnahme: Abnahme, dateienLoeschen: Boolean = false) {
        prefs(context).edit().remove(abnahme.id).commit()
        if (dateienLoeschen) {
            runCatching { abnahmeOrdner(context, abnahme).deleteRecursively() }
        } else {
            runCatching { jsonDatei(context, abnahme).delete() }
            runCatching { pdfDatei(context, abnahme).delete() }
        }
    }

    fun abnahmeOrdner(context: Context, abnahme: Abnahme): File {
        val datum = abnahme.startDatumIso.ifBlank { LocalDate.now().toString() }
        val dateRoot = FotoSpeicher.ordnerFuerDatumKategorie(context, abnahme.baustelleName, "Abnahmen", datum)
        // Der Ordnername bleibt stabil, auch wenn Objekt oder Bezeichnung später geändert werden.
        val ordner = File(dateRoot, "Abnahme_${abnahme.nummer.toString().padStart(3, '0')}")
        if (!ordner.exists()) ordner.mkdirs()
        return ordner
    }

    fun fotoOrdner(context: Context, abnahme: Abnahme): File = File(abnahmeOrdner(context, abnahme), "Fotos").apply { mkdirs() }
    fun objektOrdner(context: Context, abnahme: Abnahme): File = File(abnahmeOrdner(context, abnahme), "Objekt").apply { mkdirs() }
    fun pdfDatei(context: Context, abnahme: Abnahme): File = File(abnahmeOrdner(context, abnahme), "Abnahme_${abnahme.nummer.toString().padStart(3, '0')}.pdf")
    fun jsonDatei(context: Context, abnahme: Abnahme): File = File(abnahmeOrdner(context, abnahme), "Abnahme_${abnahme.nummer.toString().padStart(3, '0')}.json")

    fun neueFotoDatei(context: Context, abnahme: Abnahme): File {
        val stamp = java.time.LocalDateTime.now().toString().replace(':', '-').replace('.', '-')
        return File(fotoOrdner(context, abnahme), "Bild_$stamp.jpg")
    }

    fun neuePlanOriginalDatei(context: Context, abnahme: Abnahme, extension: String = "jpg"): File {
        val stamp = System.currentTimeMillis()
        return File(objektOrdner(context, abnahme), "Plan_Original_$stamp.${extension.trimStart('.').ifBlank { "jpg" }}")
    }

    fun neuePlanAusschnittDatei(context: Context, abnahme: Abnahme): File {
        val stamp = System.currentTimeMillis()
        return File(objektOrdner(context, abnahme), "Plan_Ausschnitt_$stamp.jpg")
    }

    fun kopiereUri(context: Context, uri: Uri, target: File): File {
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Datei konnte nicht geöffnet werden" }
            target.outputStream().buffered(64 * 1024).use { output -> input.copyTo(output, 64 * 1024) }
        }
        return target
    }

    fun ergebnisLabel(ergebnis: Ergebnis): String = when (ergebnis) {
        Ergebnis.ENTWURF -> "Entwurf"
        Ergebnis.ABGENOMMEN -> "Abgenommen"
        Ergebnis.NICHT_ABGENOMMEN -> "Nicht abgenommen"
        Ergebnis.ABGENOMMEN_MIT_RESTPUNKTEN -> "Abgenommen mit Restpunkten"
        Ergebnis.NOCH_ZU_PRUEFEN -> "Noch zu prüfen"
    }

    fun bildArtLabel(art: BildArt): String = when (art) {
        BildArt.AUSGANGSZUSTAND -> "Ausgangszustand"
        BildArt.AUSFUEHRUNG -> "Ausführung"
        BildArt.MESSUNG -> "Messung"
        BildArt.DETAIL -> "Detail"
        BildArt.RESTPUNKT -> "Restpunkt"
        BildArt.PLAN_QUERPROFIL -> "Plan / Querprofil"
        BildArt.SONSTIGES -> "Sonstiges"
    }

    /**
     * Ein vorgemerktes Ergebnis zählt bis zum tatsächlichen Abschluss weiterhin als
     * "In Prüfung". Erst [abgeschlossenAmEpochMs] macht Ja/Nein wirksam.
     */
    fun filterKey(abnahme: Abnahme): String = when {
        !abnahme.istEndgueltigAbgeschlossen -> "PRUEFUNG"
        abnahme.ergebnis == Ergebnis.ABGENOMMEN -> "JA"
        abnahme.ergebnis == Ergebnis.NICHT_ABGENOMMEN -> "NEIN"
        else -> "PRUEFUNG"
    }

    fun statusLabel(abnahme: Abnahme): String = when {
        abnahme.ergebnis == Ergebnis.ENTWURF -> "Entwurf"
        !abnahme.istEndgueltigAbgeschlossen && abnahme.ergebnis == Ergebnis.ABGENOMMEN ->
            "In Prüfung – Ergebnis vorgemerkt: Abgenommen"
        !abnahme.istEndgueltigAbgeschlossen && abnahme.ergebnis == Ergebnis.NICHT_ABGENOMMEN ->
            "In Prüfung – Ergebnis vorgemerkt: Nicht abgenommen"
        else -> ergebnisLabel(abnahme.ergebnis)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun sicherSegment(raw: String): String {
        val v = raw.trim()
            .replace("/", "-")
            .replace("\\", "-")
            .replace(Regex("[^a-zA-Z0-9ÄÖÜäöüß _.-]"), "")
            .replace(Regex("\\s+"), "_")
            .take(48)
        return v.ifBlank { "Abnahme" }
    }

    private fun toJson(a: Abnahme): JSONObject = JSONObject().apply {
        put("id", a.id)
        put("nummer", a.nummer)
        put("baustelleId", a.baustelleId)
        put("baustelleName", a.baustelleName)
        putNullable("hauptKnotenId", a.hauptKnotenId)
        putNullable("unterKnotenId", a.unterKnotenId)
        put("unterKnotenName", a.unterKnotenName)
        put("bezeichnung", a.bezeichnung)
        put("objekt", a.objekt)
        put("firma", a.firma)
        put("weitereFirmen", a.weitereFirmen)
        put("abnehmender", a.abnehmender)
        put("anwesende", a.anwesende)
        putNullable("kmVon", a.kmVon)
        putNullable("kmBis", a.kmBis)
        put("kommentar", a.kommentar)
        put("restpunkte", a.restpunkte)
        put("ergebnis", a.ergebnis.name)
        put("startEpochMs", a.startEpochMs)
        put("geaendertAmEpochMs", a.geaendertAmEpochMs)
        putNullable("abgeschlossenAmEpochMs", a.abgeschlossenAmEpochMs)
        putNullable("wiederGeoeffnetAmEpochMs", a.wiederGeoeffnetAmEpochMs)
        put("fotos", JSONArray().apply {
            a.fotos.forEach { f ->
                put(JSONObject().apply {
                    put("fotoId", f.fotoId)
                    put("bildArt", f.bildArt.name)
                    put("bemerkung", f.bemerkung)
                    put("erstelltAmEpochMs", f.erstelltAmEpochMs)
                })
            }
        })
        put("anlagen", JSONArray().apply {
            a.anlagen.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("typ", d.typ.name)
                    put("originalPfad", d.originalPfad)
                    putNullable("ausschnittPfad", d.ausschnittPfad)
                    put("bezeichnung", d.bezeichnung)
                    put("bemerkung", d.bemerkung)
                    put("erstelltAmEpochMs", d.erstelltAmEpochMs)
                })
            }
        })
    }

    private fun parse(raw: String): Abnahme? = runCatching {
        val o = JSONObject(raw)
        val fotos = o.optJSONArray("fotos")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val f = arr.optJSONObject(i) ?: continue
                    val id = f.optLong("fotoId", -1L)
                    if (id <= 0L) continue
                    add(
                        FotoRef(
                            fotoId = id,
                            bildArt = runCatching { BildArt.valueOf(f.optString("bildArt", BildArt.SONSTIGES.name)) }.getOrDefault(BildArt.SONSTIGES),
                            bemerkung = f.optString("bemerkung", ""),
                            erstelltAmEpochMs = f.optLong("erstelltAmEpochMs", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.orEmpty()
        val anlagen = o.optJSONArray("anlagen")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    val path = d.optString("originalPfad", "")
                    if (path.isBlank()) continue
                    add(
                        Anlage(
                            id = d.optString("id", UUID.randomUUID().toString()),
                            typ = runCatching { AnlageTyp.valueOf(d.optString("typ", AnlageTyp.BILD.name)) }.getOrDefault(AnlageTyp.BILD),
                            originalPfad = path,
                            ausschnittPfad = d.optNullableString("ausschnittPfad"),
                            bezeichnung = d.optString("bezeichnung", ""),
                            bemerkung = d.optString("bemerkung", ""),
                            erstelltAmEpochMs = d.optLong("erstelltAmEpochMs", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.orEmpty()
        Abnahme(
            id = o.optString("id", UUID.randomUUID().toString()),
            nummer = o.optInt("nummer", 1),
            baustelleId = o.optLong("baustelleId", -1L),
            baustelleName = o.optString("baustelleName", "Baustelle"),
            hauptKnotenId = o.optNullableLong("hauptKnotenId"),
            unterKnotenId = o.optNullableLong("unterKnotenId"),
            unterKnotenName = o.optString("unterKnotenName", ""),
            bezeichnung = o.optString("bezeichnung", ""),
            objekt = o.optString("objekt", ""),
            firma = o.optString("firma", ""),
            weitereFirmen = o.optString("weitereFirmen", ""),
            abnehmender = o.optString("abnehmender", ""),
            anwesende = o.optString("anwesende", ""),
            kmVon = o.optNullableDouble("kmVon"),
            kmBis = o.optNullableDouble("kmBis"),
            kommentar = o.optString("kommentar", ""),
            restpunkte = o.optString("restpunkte", ""),
            ergebnis = runCatching { Ergebnis.valueOf(o.optString("ergebnis", Ergebnis.ENTWURF.name)) }.getOrDefault(Ergebnis.ENTWURF),
            startEpochMs = o.optLong("startEpochMs", System.currentTimeMillis()),
            geaendertAmEpochMs = o.optLong("geaendertAmEpochMs", System.currentTimeMillis()),
            abgeschlossenAmEpochMs = o.optNullableLong("abgeschlossenAmEpochMs"),
            wiederGeoeffnetAmEpochMs = o.optNullableLong("wiederGeoeffnetAmEpochMs"),
            fotos = fotos,
            anlagen = anlagen
        )
    }.getOrNull()?.takeIf { it.baustelleId > 0L && it.id.isNotBlank() }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key)

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
