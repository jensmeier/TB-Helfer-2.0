package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object BautagebuchSpeicher {

    private val isoFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Cache: BaustelleId -> Top-Level Ordnername (damit Umbenennen der Baustelle die Daten nicht "versteckt")
    private val folderCacheById = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private fun tbRoot(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IllegalStateException("Kein externer App-Ordner verfügbar")
        val root = File(base, "TB-Helfer")
        if (!root.exists()) root.mkdirs()
        return root
    }

    private fun ordnerByTop(context: Context, topFolder: String, datum: LocalDate): File {
        val root = tbRoot(context)
        val dir = File(root, "$topFolder/Bautagebuch/${datum.format(isoFmt)}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun readBaustelleIdFromJson(f: File): Long? {
        return runCatching {
            val txt = f.readText(Charsets.UTF_8)
            val id = JSONObject(txt).optLong("baustelleId", 0L)
            id.takeIf { it != 0L }
        }.getOrNull()
    }

    private fun findTopFolderForBaustelleId(context: Context, baustelleId: Long): String? {
        folderCacheById[baustelleId]?.let { cached ->
            val root = tbRoot(context)
            if (File(root, cached).exists()) return cached
            folderCacheById.remove(baustelleId)
        }

        val root = tbRoot(context)
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val candidates = listOf("bautagebuch_tag.json", "bautagebuch_nacht.json", "bautagebuch.json")

        for (top in dirs) {
            val btbDir = File(top, "Bautagebuch")
            if (!btbDir.exists()) continue

            val dateDirs = btbDir.listFiles()?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.take(12)
                ?: emptyList()

            for (dd in dateDirs) {
                for (fn in candidates) {
                    val f = File(dd, fn)
                    if (!f.exists()) continue
                    val id = readBaustelleIdFromJson(f) ?: continue
                    if (id == baustelleId) {
                        folderCacheById[baustelleId] = top.name
                        return top.name
                    }
                }
            }
        }

        return null
    }

    /**
     * A1: Top-Ordner immer über Baustellen-ID, damit Umbenennen nichts "versteckt".
     *
     * Migration/Kompatibilität:
     * - Wenn bereits ein alter Ordner (z.B. nach Name) existiert, versuchen wir ihn einmalig auf
     *   den kanonischen Ordner "baustelle_<id>" umzubenennen.
     * - Falls das Umbenennen fehlschlägt, wird der gefundene Ordner weiter benutzt.
     */
    private fun resolveTopFolderName(context: Context, baustelleId: Long?, baustelleName: String): String {
        val id = baustelleId ?: 0L
        if (id <= 0L) return safeFolderName(baustelleName)

        val canonical = "baustelle_${id}"
        val root = tbRoot(context)
        val canonicalDir = File(root, canonical)
        if (canonicalDir.exists()) return canonical

        // Fallback: alten Ordner finden (z.B. name-basiert) und nach Möglichkeit migrieren.
        val found = findTopFolderForBaustelleId(context, id)
        if (found != null && found != canonical) {
            val oldDir = File(root, found)
            if (oldDir.exists()) {
                runCatching {
                    if (!oldDir.renameTo(canonicalDir)) {
                        // Rename kann fehlschlagen (unterschiedliche Mounts). Dann weiter alten Ordner nutzen.
                        folderCacheById[id] = found
                        return found
                    }
                }
                if (canonicalDir.exists()) return canonical
            }
        }

        // Default: kanonischer Ordner (wird bei ordnerByTop() angelegt)
        return canonical
    }

    /**
     * Ordner: Pictures/TB-Helfer/<Baustelle>/Bautagebuch/<yyyy-MM-dd>/
     *
     * Wichtig: Wenn [baustelleId] gesetzt ist, wird bei umbenannter Baustelle der bestehende Ordner
     * (aus alten Einträgen) wiedergefunden.
     */
    fun ordner(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate): File {
        val top = resolveTopFolderName(context, baustelleId, baustelleName)
        return ordnerByTop(context, top, datum)
    }

    /** Backward-compatible: Ordner über Baustellen-Namen. */
    fun ordner(context: Context, baustelleName: String, datum: LocalDate): File =
        ordner(context, null, baustelleName, datum)

private fun dateiname(schicht: SchichtTyp): String = when (schicht) {
    SchichtTyp.TAG -> "bautagebuch_tag.json"
    SchichtTyp.NACHT -> "bautagebuch_nacht.json"
}

private fun datei(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): File {
    val dir = ordner(context, baustelleId, baustelleName, datum)
    return File(dir, dateiname(schicht))
}

private fun datei(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): File =
    datei(context, null, baustelleName, datum, schicht)

/** Legacy-Datei (ältere Versionen ohne Tag/Nacht-Trennung). */
private fun legacyDatei(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate): File {
    val dir = ordner(context, baustelleId, baustelleName, datum)
    return File(dir, "bautagebuch.json")
}

private fun legacyDatei(context: Context, baustelleName: String, datum: LocalDate): File =
    legacyDatei(context, null, baustelleName, datum)

/** Baseline-Snapshot (wird nur bei Long-Press "PDF erstellen" gesetzt). */
private fun originalDatei(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): File {
    val dir = ordner(context, baustelleId, baustelleName, datum)
    val name = when (schicht) {
        SchichtTyp.TAG -> "bautagebuch_tag_original.json"
        SchichtTyp.NACHT -> "bautagebuch_nacht_original.json"
    }
    return File(dir, name)
}

private fun originalDatei(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): File =
    originalDatei(context, null, baustelleName, datum, schicht)

private fun legacyOriginalDatei(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate): File {
    val dir = ordner(context, baustelleId, baustelleName, datum)
    return File(dir, "bautagebuch_original.json")
}

private fun legacyOriginalDatei(context: Context, baustelleName: String, datum: LocalDate): File =
    legacyOriginalDatei(context, null, baustelleName, datum)

// Backward compatible: ohne Schicht => TAG
private fun datei(context: Context, baustelleName: String, datum: LocalDate): File =
    datei(context, baustelleName, datum, SchichtTyp.TAG)

private fun originalDatei(context: Context, baustelleName: String, datum: LocalDate): File =
    originalDatei(context, baustelleName, datum, SchichtTyp.TAG)

    data class BaselineInfo(
        val exists: Boolean,
        val changedSinceBaseline: Boolean,
        val baselineLastModifiedEpochMs: Long?
    )

    
fun baselineInfo(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BaselineInfo {
    val current = datei(context, baustelleId, baustelleName, datum, schicht)
    val original = originalDatei(context, baustelleId, baustelleName, datum, schicht)

    // Legacy-Fallback nur für TAG
    val currentRead = if (!current.exists() && schicht == SchichtTyp.TAG) legacyDatei(context, baustelleId, baustelleName, datum) else current
    val originalRead = if (!original.exists() && schicht == SchichtTyp.TAG) legacyOriginalDatei(context, baustelleId, baustelleName, datum) else original

    val exists = originalRead.exists()
    val baselineTs = originalRead.takeIf { it.exists() }?.lastModified()
    val changed = exists && currentRead.exists() &&
        runCatching { currentRead.readText(Charsets.UTF_8) != originalRead.readText(Charsets.UTF_8) }.getOrDefault(false)
    return BaselineInfo(exists = exists, changedSinceBaseline = changed, baselineLastModifiedEpochMs = baselineTs)
}

// Backward compatible
fun baselineInfo(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BaselineInfo =
    baselineInfo(context, null, baustelleName, datum, schicht)


// Backward compatible
fun baselineInfo(context: Context, baustelleName: String, datum: LocalDate): BaselineInfo =
    baselineInfo(context, baustelleName, datum, SchichtTyp.TAG)

    /**
     * Setzt die Baseline einmalig (ohne overwrite). Rückgabe true, wenn Baseline neu erstellt wurde.
     * Erwartung: bautagebuch.json existiert bereits (z.B. nach speichern()).
     */
    
fun baselineSetzenWennNichtVorhanden(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): Boolean {
    val current = datei(context, baustelleId, baustelleName, datum, schicht)
    val original = originalDatei(context, baustelleId, baustelleName, datum, schicht)

    // Legacy-Fallback nur für TAG
    val currentRead = if (!current.exists() && schicht == SchichtTyp.TAG) legacyDatei(context, baustelleId, baustelleName, datum) else current

    if (!currentRead.exists()) return false
    if (original.exists()) return false
    runCatching { currentRead.copyTo(original, overwrite = false) }
    return original.exists()
}

// Backward compatible
fun baselineSetzenWennNichtVorhanden(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): Boolean =
    baselineSetzenWennNichtVorhanden(context, null, baustelleName, datum, schicht)


// Backward compatible
fun baselineSetzenWennNichtVorhanden(context: Context, baustelleName: String, datum: LocalDate): Boolean =
    baselineSetzenWennNichtVorhanden(context, baustelleName, datum, SchichtTyp.TAG)

    
fun laden(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BautagebuchEintrag? {
    val f = datei(context, baustelleId, baustelleName, datum, schicht)
    val readFile = if (!f.exists() && schicht == SchichtTyp.TAG) legacyDatei(context, baustelleId, baustelleName, datum) else f
    if (!readFile.exists()) return null
    val json = runCatching { readFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
    return runCatching { fromJson(JSONObject(json)) }.getOrNull()?.let { e ->
        if (e.schicht != schicht) e.copy(schicht = schicht) else e
    }
}

// Backward compatible
fun laden(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BautagebuchEintrag? =
    laden(context, null, baustelleName, datum, schicht)


// Backward compatible
fun laden(context: Context, baustelleName: String, datum: LocalDate): BautagebuchEintrag? =
    laden(context, baustelleName, datum, SchichtTyp.TAG)


    /**
     * Laedt die Baseline (Original-Snapshot) fuer Aenderungsvergleich im PDF.
     * Datei: bautagebuch_<tag|nacht>_original.json (oder Legacy: bautagebuch_original.json)
     */
    fun ladenBaseline(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ): BautagebuchEintrag? {
        val f = originalDatei(context, baustelleId, baustelleName, datum, schicht)
        val readFile = if (!f.exists() && schicht == SchichtTyp.TAG) legacyOriginalDatei(context, baustelleId, baustelleName, datum) else f
        if (!readFile.exists()) return null
        val json = runCatching { readFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return runCatching { fromJson(JSONObject(json)) }.getOrNull()?.let { e ->
            if (e.schicht != schicht) e.copy(schicht = schicht) else e
        }
    }

    // Backward compatible
    fun ladenBaseline(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BautagebuchEintrag? =
        ladenBaseline(context, null, baustelleName, datum, schicht)

    // Backward compatible
    fun ladenBaseline(context: Context, baustelleName: String, datum: LocalDate): BautagebuchEintrag? =
        ladenBaseline(context, baustelleName, datum, SchichtTyp.TAG)

    fun speichern(context: Context, entry: BautagebuchEintrag) {
        val datum = LocalDate.parse(entry.datumIso, isoFmt)
        val f = datei(context, entry.baustelleId, entry.baustelleName, datum, entry.schicht)
        val json = toJson(entry).toString(2)
        f.writeText(json, Charsets.UTF_8)
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
    }

    private fun toJson(e: BautagebuchEintrag): JSONObject = JSONObject().apply {
        put("baustelleName", e.baustelleName)
        put("baustelleId", e.baustelleId)
        put("datumIso", e.datumIso)
        put("schicht", e.schicht.name)
        put("projektNameSnapshot", e.projektNameSnapshot)
        put("strecke", e.strecke)
        put("startUhrzeit", e.startUhrzeit)
        put("endeUhrzeit", e.endeUhrzeit)
        put("wetter", e.wetter)
        put("maxFotosInPdf", e.maxFotosInPdf)

        // Neuer Wetterblock (mit Stand/Quelle)
        put("weather", e.weather?.let { wb ->
            JSONObject().apply {
                put("standEpochMs", wb.standEpochMs)
                put("source", wb.source.name)
                put("locationName", wb.locationName)
                put("stationName", wb.stationName)
                put("lat", wb.lat)
                put("lon", wb.lon)
                put("slots", JSONArray().apply {
                    wb.slots.forEach { slot ->
                        put(JSONObject().apply {
                            put("uhrzeit", slot.uhrzeit)
                            put("tempC", slot.tempC)
                            put("bewoelkung", slot.bewoelkung)
                            put("niederschlag", slot.niederschlag)
                            put("niederschlagMm", slot.niederschlagMm)
                        })
                    }
                })
            }
        })
        put("weatherSlots", JSONArray().apply {
            e.weatherSlots.forEach { slot ->
                put(JSONObject().apply {
                    put("uhrzeit", slot.uhrzeit)
                    put("tempC", slot.tempC)
                    put("bewoelkung", slot.bewoelkung)
                    put("niederschlag", slot.niederschlag)
                    put("niederschlagMm", slot.niederschlagMm)
                })
            }
        })

        // Firmen (Personal/Geräte/Leistungen)
        put("firmen", JSONArray().apply {
            e.firmen.forEach { f ->
                put(JSONObject().apply {
                    put("firmaId", f.firmaId)
                    put("name", f.name)
                    put("personalVon", f.personalVon)
                    put("personalBis", f.personalBis)
                    put("maschinenVon", f.maschinenVon)
                    put("maschinenBis", f.maschinenBis)
                    put("visibleInPersonal", f.visibleInPersonal)
                    put("visibleInMaschinen", f.visibleInMaschinen)
                    put("isDeleted", f.isDeleted)
                    put("personal", JSONArray().apply {
                        f.personal.forEach { p ->
                            put(JSONObject().apply {
                                put("anzahl", p.anzahl)
                                put("rolle", p.rolle)
                            })
                        }
                    })
                    put("geraete", JSONArray().apply {
                        f.geraete.forEach { g ->
                            put(JSONObject().apply {
                                put("anzahl", g.anzahl)
                                put("bezeichnung", g.bezeichnung)
                            })
                        }
                    })
                    put("leistungen", JSONArray().apply {
                        f.leistungen.forEach { l ->
                            put(JSONObject().apply {
                                put("uhrzeit", l.uhrzeit)
                                put("text", l.text)
                            })
                        }
                    })
                })
            }
        })

        // Standardabschnitte
        fun sectionToJson(sb: SectionBlock): JSONArray = JSONArray().apply {
            sb.items.forEach { it ->
                put(JSONObject().apply {
                    put("uhrzeit", it.uhrzeit)
                    put("text", it.text)
                    put("kmVon", it.kmVon)
                    put("kmBis", it.kmBis)
                })
            }
        }
        put("abnahmen", sectionToJson(e.abnahmen))
        put("einweisungUnterweisung", sectionToJson(e.einweisungUnterweisung))
        put("besonderheiten", sectionToJson(e.besonderheiten))
        put("maengelSchaeden", sectionToJson(e.maengelSchaeden))

        // Legacy (falls noch irgendwo genutzt)
        put("personal", JSONArray(e.personal))
        put("geraete", JSONArray(e.geraete))
        put("notizen", e.notizen)
        put("includeFotos", e.includeFotos)
        put("includeBtbs", e.includeBtbs)

        // Abschlussstatus ("Fertig")
        put("istFertig", e.istFertig)

put("pdfAuswahl", JSONObject().apply {
    e.pdfAuswahl.forEach { (k, ids) ->
        put(k, JSONArray().apply { ids.forEach { put(it) } })
    }
})
        put("erstelltAmEpochMs", e.erstelltAmEpochMs)
        put("geaendertAmEpochMs", e.geaendertAmEpochMs)

        // Foto-Referenzen
        put("fotos", JSONArray().apply {
            e.fotos.forEach { fr ->
                put(JSONObject().apply {
                    put("fotoId", fr.fotoId)
                    put("lat", fr.lat)
                    put("lon", fr.lon)
                    put("km", fr.km)
                    put("objektkennung", fr.objektkennung)
                    put("zusatzinfo", fr.zusatzinfo)
                    put("erstelltAmEpochMs", fr.erstelltAmEpochMs)
                    put("bildtyp", fr.bildtyp)
                    put("indikator", fr.indikator)
                })
            }
        })
    }

    private fun fromJson(o: JSONObject): BautagebuchEintrag {
        fun arr(key: String): List<String> {
            if (!o.has(key) || o.isNull(key)) return emptyList()
            val a = o.getJSONArray(key)
            val out = ArrayList<String>(a.length())
            for (i in 0 until a.length()) out.add(a.optString(i).orEmpty())
            return out.filter { it.isNotBlank() }
        }

        fun slotsFromArray(arr: JSONArray?): List<WetterSlot> {
            if (arr == null) return emptyList()
            val out = ArrayList<WetterSlot>(arr.length())
            for (i in 0 until arr.length()) {
                val so = arr.optJSONObject(i) ?: continue
                val u = so.optString("uhrzeit").orEmpty()
                if (u.isBlank()) continue
                val t = if (so.has("tempC") && !so.isNull("tempC")) so.optInt("tempC") else null
                val b = so.optString("bewoelkung").takeUnless { it.isBlank() }
                val n = so.optString("niederschlag").takeUnless { it.isBlank() }
                val mm = if (so.has("niederschlagMm") && !so.isNull("niederschlagMm")) so.optDouble("niederschlagMm") else null
                out.add(WetterSlot(uhrzeit = u, tempC = t, bewoelkung = b, niederschlag = n, niederschlagMm = mm))
            }
            return out
        }

        fun slots(): List<WetterSlot> {
            if (!o.has("weatherSlots") || o.isNull("weatherSlots")) return emptyList()
            return slotsFromArray(o.optJSONArray("weatherSlots"))
        }

        fun weatherBlock(): WeatherBlock? {
            if (!o.has("weather") || o.isNull("weather")) return null
            val w = o.optJSONObject("weather") ?: return null
            val stand = if (w.has("standEpochMs") && !w.isNull("standEpochMs")) w.optLong("standEpochMs") else null
            val source = runCatching {
                WeatherSource.valueOf(w.optString("source", WeatherSource.AUTO.name))
            }.getOrDefault(WeatherSource.AUTO)
            val locationName = w.optString("locationName").takeUnless { it.isBlank() }
            val stationName = w.optString("stationName").takeUnless { it.isBlank() }
            val lat = if (w.has("lat") && !w.isNull("lat")) w.optDouble("lat") else null
            val lon = if (w.has("lon") && !w.isNull("lon")) w.optDouble("lon") else null
            val slotsArr = w.optJSONArray("slots")
            return WeatherBlock(
                standEpochMs = stand,
                source = source,
                locationName = locationName,
                stationName = stationName,
                lat = lat,
                lon = lon,
                slots = slotsFromArray(slotsArr)
            )
        }

        fun sectionFromArray(arr: JSONArray?): SectionBlock {
            if (arr == null) return SectionBlock()
            val items = ArrayList<SectionItem>(arr.length())
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                val text = it.optString("text").orEmpty().trim()
                if (text.isBlank()) continue
                val uhr = it.optString("uhrzeit").takeUnless { s -> s.isBlank() }
                val kmVon = if (it.has("kmVon") && !it.isNull("kmVon")) it.optDouble("kmVon").takeUnless { v -> v.isNaN() } else null
                val kmBis = if (it.has("kmBis") && !it.isNull("kmBis")) it.optDouble("kmBis").takeUnless { v -> v.isNaN() } else null
                items.add(SectionItem(uhrzeit = uhr, text = text, kmVon = kmVon, kmBis = kmBis))
            }
            return SectionBlock(items)
        }

        fun firmenList(): List<FirmaBlock> {
            val arr = o.optJSONArray("firmen") ?: return emptyList()
            val out = ArrayList<FirmaBlock>(arr.length())
            for (i in 0 until arr.length()) {
                val fo = arr.optJSONObject(i) ?: continue
                val id = fo.optString("firmaId").orEmpty()
                val name = fo.optString("name").orEmpty()
                if (id.isBlank() || name.isBlank()) continue

                fun personalList(): List<PersonalZeile> {
                    val pa = fo.optJSONArray("personal") ?: return emptyList()
                    val pOut = ArrayList<PersonalZeile>(pa.length())
                    for (pi in 0 until pa.length()) {
                        val po = pa.optJSONObject(pi) ?: continue
                        val rolle = po.optString("rolle").orEmpty().trim()
                        if (rolle.isBlank()) continue
                        val anzahl = if (po.has("anzahl") && !po.isNull("anzahl")) po.optInt("anzahl") else null
                        pOut.add(PersonalZeile(anzahl = anzahl, rolle = rolle))
                    }
                    return pOut
                }

                fun geraeteList(): List<GeraetZeile> {
                    val ga = fo.optJSONArray("geraete") ?: return emptyList()
                    val gOut = ArrayList<GeraetZeile>(ga.length())
                    for (gi in 0 until ga.length()) {
                        val go = ga.optJSONObject(gi) ?: continue
                        val bez = go.optString("bezeichnung").orEmpty().trim()
                        if (bez.isBlank()) continue
                        val anzahl = if (go.has("anzahl") && !go.isNull("anzahl")) go.optInt("anzahl") else null
                        gOut.add(GeraetZeile(anzahl = anzahl, bezeichnung = bez))
                    }
                    return gOut
                }

                fun leistungenList(): List<LeistungZeile> {
                    val la = fo.optJSONArray("leistungen") ?: return emptyList()
                    val lOut = ArrayList<LeistungZeile>(la.length())
                    for (li in 0 until la.length()) {
                        val lo = la.optJSONObject(li) ?: continue
                        val text = lo.optString("text").orEmpty().trim()
                        if (text.isBlank()) continue
                        val uhr = lo.optString("uhrzeit").takeUnless { s -> s.isBlank() }
                        lOut.add(LeistungZeile(uhrzeit = uhr, text = text))
                    }
                    return lOut
                }

                out.add(
                    FirmaBlock(
                        firmaId = id,
                        name = name,
                        personalVon = fo.optString("personalVon").takeUnless { it.isBlank() },
                        personalBis = fo.optString("personalBis").takeUnless { it.isBlank() },
                        maschinenVon = fo.optString("maschinenVon").takeUnless { it.isBlank() },
                        maschinenBis = fo.optString("maschinenBis").takeUnless { it.isBlank() },
                        visibleInPersonal = fo.optBoolean("visibleInPersonal", true),
                        visibleInMaschinen = fo.optBoolean("visibleInMaschinen", true),
                        isDeleted = fo.optBoolean("isDeleted", false),
                        personal = personalList(),
                        geraete = geraeteList(),
                        leistungen = leistungenList()
                    )
                )
            }
            return out
        }

        fun fotosList(): List<FotoRef> {
            val arr = o.optJSONArray("fotos") ?: return emptyList()
            val out = ArrayList<FotoRef>(arr.length())
            for (i in 0 until arr.length()) {
                val fo = arr.optJSONObject(i) ?: continue
                val id = fo.optLong("fotoId", -1)
                if (id <= 0) continue
                out.add(
                    FotoRef(
                        fotoId = id,
                        lat = if (fo.has("lat") && !fo.isNull("lat")) fo.optDouble("lat") else null,
                        lon = if (fo.has("lon") && !fo.isNull("lon")) fo.optDouble("lon") else null,
                        km = if (fo.has("km") && !fo.isNull("km")) fo.optDouble("km") else null,
                        objektkennung = fo.optString("objektkennung").takeUnless { it.isBlank() },
                        zusatzinfo = fo.optString("zusatzinfo").takeUnless { it.isBlank() },
                        erstelltAmEpochMs = if (fo.has("erstelltAmEpochMs") && !fo.isNull("erstelltAmEpochMs")) fo.optLong("erstelltAmEpochMs") else null,
                        bildtyp = fo.optString("bildtyp").takeUnless { it.isBlank() },
                        indikator = fo.optString("indikator").takeUnless { it.isBlank() }
                    )
                )
            }
            return out
        }



fun pdfAuswahlMap(): Map<String, List<Long>> {
    if (!o.has("pdfAuswahl") || o.isNull("pdfAuswahl")) return emptyMap()
    val obj = o.optJSONObject("pdfAuswahl") ?: return emptyMap()
    val out = LinkedHashMap<String, List<Long>>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        val arr = obj.optJSONArray(k) ?: continue
        val ids = ArrayList<Long>(arr.length())
        for (i in 0 until arr.length()) {
            ids.add(arr.optLong(i))
        }
        out[k] = ids.distinct()
    }
    return out
}

        return BautagebuchEintrag(
            baustelleName = o.optString("baustelleName"),
            baustelleId = o.optLong("baustelleId"),
            datumIso = o.optString("datumIso"),
            schicht = runCatching { SchichtTyp.valueOf(o.optString("schicht", SchichtTyp.TAG.name)) }
                .getOrDefault(SchichtTyp.TAG),
            projektNameSnapshot = o.optString("projektNameSnapshot").takeUnless { it.isBlank() },
            strecke = o.optString("strecke").takeUnless { it.isBlank() },
            startUhrzeit = o.optString("startUhrzeit").takeUnless { it.isBlank() },
            endeUhrzeit = o.optString("endeUhrzeit").takeUnless { it.isBlank() },
            wetter = o.optString("wetter").takeUnless { it.isBlank() },
            weather = weatherBlock(),
            weatherSlots = slots(),
            maxFotosInPdf = o.optInt("maxFotosInPdf", 60),
            pdfAuswahl = pdfAuswahlMap(),

            firmen = firmenList(),
            abnahmen = sectionFromArray(o.optJSONArray("abnahmen")),
            einweisungUnterweisung = sectionFromArray(o.optJSONArray("einweisungUnterweisung")),
            besonderheiten = sectionFromArray(o.optJSONArray("besonderheiten")),
            maengelSchaeden = sectionFromArray(o.optJSONArray("maengelSchaeden")),

            personal = arr("personal"),
            geraete = arr("geraete"),
            notizen = o.optString("notizen").takeUnless { it.isBlank() },
            includeFotos = o.optBoolean("includeFotos", true),
            includeBtbs = o.optBoolean("includeBtbs", true),
            fotos = fotosList(),
            istFertig = o.optBoolean("istFertig", false),
            erstelltAmEpochMs = o.optLong("erstelltAmEpochMs", System.currentTimeMillis()),
            geaendertAmEpochMs = o.optLong("geaendertAmEpochMs", o.optLong("erstelltAmEpochMs", System.currentTimeMillis()))
        )
    }


    data class VerlaufEintrag(
        val datum: LocalDate,
        val schicht: SchichtTyp,
        val entry: BautagebuchEintrag,
        val baselineExists: Boolean,
        val pdfVorhanden: Boolean,
        val pdfDatei: File?,
        val bearbeitbar: Boolean,
        val alterTage: Long
    )

    /**
     * BTB-Verlauf einer Baustelle.
     * Grün/Bearbeitbar: abgeschlossen (Baseline vorhanden) und maximal 7 Tage alt.
     * Rot/Gesperrt: abgeschlossen und älter als 7 Tage.
     */
    fun verlaufFuerBaustelle(context: Context, baustelleId: Long?, baustelleName: String): List<VerlaufEintrag> {
        val top = resolveTopFolderName(context, baustelleId, baustelleName)
        val btbRoot = File(tbRoot(context), "$top/Bautagebuch")
        if (!btbRoot.exists() || !btbRoot.isDirectory) return emptyList()

        val today = LocalDate.now()
        val out = ArrayList<VerlaufEintrag>()
        val currentNames = mapOf(
            "bautagebuch_tag.json" to SchichtTyp.TAG,
            "bautagebuch_nacht.json" to SchichtTyp.NACHT,
            "bautagebuch.json" to SchichtTyp.TAG
        )

        btbRoot.listFiles()?.filter { it.isDirectory }?.forEach { dateDir ->
            val datum = runCatching { LocalDate.parse(dateDir.name, isoFmt) }.getOrNull() ?: return@forEach
            currentNames.forEach { (fileName, schicht) ->
                val f = File(dateDir, fileName)
                if (!f.exists()) return@forEach
                val entry = runCatching { fromJson(JSONObject(f.readText(Charsets.UTF_8))) }.getOrNull()
                    ?.let { if (it.schicht != schicht) it.copy(schicht = schicht) else it }
                    ?: return@forEach
                val bi = baselineInfo(context, baustelleId, baustelleName, datum, schicht)
                val age = java.time.temporal.ChronoUnit.DAYS.between(datum, today).coerceAtLeast(0L)
                val pdf = neuestePdfFuerVerlauf(context, baustelleId, baustelleName, datum, schicht)
                out.add(
                    VerlaufEintrag(
                        datum = datum,
                        schicht = schicht,
                        entry = entry,
                        baselineExists = bi.exists,
                        pdfVorhanden = pdf != null,
                        pdfDatei = pdf,
                        bearbeitbar = age <= 7,
                        alterTage = age
                    )
                )
            }
        }

        return out.distinctBy { it.datum.toString() + "_" + it.schicht.name }
            .sortedWith(compareByDescending<VerlaufEintrag> { it.datum }.thenBy { it.schicht.name })
    }

    private fun neuestePdfFuerVerlauf(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ): File? {
        val id = baustelleId ?: 0L
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        val top = if (id > 0L) "baustelle_${id}" else safeFolderName(baustelleName)
        val dir = File(base, "TB-Helfer/$top/BTB/${datum.format(isoFmt)}")
        if (!dir.exists()) return null
        val dateKey = datum.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
        val shiftKey = if (schicht == SchichtTyp.NACHT) {
            "$dateKey-${datum.plusDays(1).dayOfMonth.toString().padStart(2, '0')}"
        } else dateKey
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("pdf", true) && !it.name.contains("_entwurf_", ignoreCase = true) && it.name.contains(shiftKey) }
            ?.maxByOrNull { it.lastModified() }
    }


    /** Öffentliche Variante für sichere Ordnernamen (für Legacy-Aufrufer). */
    fun sichererName(input: String): String = safeFolderName(input)

    private fun safeFolderName(input: String): String {
        // gleiche Logik wie in Export/Import utils (vereinfachte Version)
        return input.trim()
            .replace("..", ".")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(80)
            .ifBlank { "Unbekannt" }
    }


    /**
     * Anzahl vorhandener BTB-Entwürfe/Abschlüsse dieser Baustelle.
     * Gezählt werden aktuelle BTB-Dateien pro Schicht (Tag/Nacht) plus Legacy-Dateien,
     * aber keine Baseline-/Original- oder Archivdateien.
     */
    fun anzahlBtbsFuerBaustelle(context: Context, baustelleId: Long?, baustelleName: String): Int {
        val top = resolveTopFolderName(context, baustelleId, baustelleName)
        val btbRoot = File(tbRoot(context), "$top/Bautagebuch")
        if (!btbRoot.exists() || !btbRoot.isDirectory) return 0

        val currentNames = setOf("bautagebuch_tag.json", "bautagebuch_nacht.json", "bautagebuch.json")
        return btbRoot.walkTopDown()
            .filter { it.isFile && it.name in currentNames }
            .count()
    }

    /** Löscht lokale BTB-Dateien dieser Baustelle aus dem App-Ordner. */
    fun loescheAlleBtbsFuerBaustelle(context: Context, baustelleId: Long?, baustelleName: String) {
        val top = resolveTopFolderName(context, baustelleId, baustelleName)
        val dir = File(tbRoot(context), top)
        if (dir.exists()) runCatching { dir.deleteRecursively() }
        baustelleId?.let { folderCacheById.remove(it) }
    }

    
    fun loeschen(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp) {
        runCatching { datei(context, baustelleId, baustelleName, datum, schicht).delete() }
        runCatching { originalDatei(context, baustelleId, baustelleName, datum, schicht).delete() }

        // Legacy-Dateien entfernen, damit TAG nicht wieder den alten Stand lädt
        if (schicht == SchichtTyp.TAG) {
            runCatching { legacyDatei(context, baustelleId, baustelleName, datum).delete() }
            runCatching { legacyOriginalDatei(context, baustelleId, baustelleName, datum).delete() }
        }
    }

    // Backward compatible
    fun loeschen(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp) =
        loeschen(context, null, baustelleName, datum, schicht)

    /**
     * Sicheres "Leeren": verschiebt aktuelle + Baseline-Dateien in ein Archiv-Unterverzeichnis,
     * damit ein versehentliches Zurücksetzen rückgängig gemacht werden kann.
     */
    private fun hasMeaningfulContent(e: BautagebuchEintrag): Boolean {
        fun hasText(s: String?): Boolean = !s.isNullOrBlank()

        val weatherHas = e.weatherSlots.any {
            hasText(it.uhrzeit) || it.tempC != null || hasText(it.bewoelkung) || hasText(it.niederschlag) || it.niederschlagMm != null
        } || (e.weather?.slots?.any {
            hasText(it.uhrzeit) || it.tempC != null || hasText(it.bewoelkung) || hasText(it.niederschlag) || it.niederschlagMm != null
        } == true)

        val firmenHas = e.firmen.any { f ->
            !f.isDeleted && (
                hasText(f.name) || hasText(f.personalVon) || hasText(f.personalBis) || hasText(f.maschinenVon) || hasText(f.maschinenBis) ||
                    f.personal.any { it.anzahl != null || hasText(it.rolle) } ||
                    f.geraete.any { it.anzahl != null || hasText(it.bezeichnung) } ||
                    f.leistungen.any { hasText(it.uhrzeit) || hasText(it.text) }
                )
        }

        val sectionsHas = e.abnahmen.items.isNotEmpty() || e.einweisungUnterweisung.items.isNotEmpty() ||
            e.besonderheiten.items.isNotEmpty() || e.maengelSchaeden.items.isNotEmpty()

        val legacyHas = e.personal.any { it.isNotBlank() } || e.geraete.any { it.isNotBlank() }

        return weatherHas || firmenHas || sectionsHas || legacyHas || hasText(e.notizen)
    }

    fun archivierenUndLeeren(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): Boolean {
        // Wenn der Entwurf faktisch leer ist, kein Archiv-Müll erzeugen
        val currentEntry = runCatching { laden(context, baustelleId, baustelleName, datum, schicht) }.getOrNull()
        if (currentEntry == null || !hasMeaningfulContent(currentEntry)) {
            loeschen(context, baustelleId, baustelleName, datum, schicht)
            return false
        }

        val dir = ordner(context, baustelleId, baustelleName, datum)
        val archivDir = File(dir, "Archiv")
        if (!archivDir.exists()) archivDir.mkdirs()
        val ts = System.currentTimeMillis()

        fun moveToArchiv(src: File) {
            if (!src.exists()) return
            val ext = src.extension
            val base = src.nameWithoutExtension
            val dst = File(archivDir, "${base}_archiv_${ts}.${ext}")
            runCatching {
                // rename innerhalb desselben Volumes
                if (!src.renameTo(dst)) {
                    src.copyTo(dst, overwrite = true)
                    src.delete()
                }
            }
        }

        moveToArchiv(datei(context, baustelleId, baustelleName, datum, schicht))
        moveToArchiv(originalDatei(context, baustelleId, baustelleName, datum, schicht))

        if (schicht == SchichtTyp.TAG) {
            moveToArchiv(legacyDatei(context, baustelleId, baustelleName, datum))
            moveToArchiv(legacyOriginalDatei(context, baustelleId, baustelleName, datum))
        }

        // Danach wirklich leeren
        loeschen(context, baustelleId, baustelleName, datum, schicht)
        return true
    }

}
