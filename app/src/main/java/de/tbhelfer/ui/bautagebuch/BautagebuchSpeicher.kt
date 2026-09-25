package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.os.Environment
import de.tbhelfer.server.SofortSync
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Lokaler BTB-Speicher.
 *
 * Neue Berichte werden nicht mehr über Datum+TAG/NACHT identifiziert, sondern über eine stabile
 * BTB-ID. Altbestände mit bautagebuch_tag.json / bautagebuch_nacht.json bleiben vollständig
 * lesbar und können weiter bearbeitet werden.
 */
object BautagebuchSpeicher {

    private val isoFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val folderCacheById = java.util.concurrent.ConcurrentHashMap<Long, String>()

    /**
     * Lokales Speichern bleibt immer der erste und verbindliche Schritt. Der anschließende
     * Server-Sync ist nur ein entprellter WorkManager-Trigger und darf einen erfolgreichen
     * lokalen BTB-Speichervorgang niemals nachträglich scheitern lassen.
     */
    private fun syncNachLokalemSpeichernAnstossen(
        context: Context,
        baustelleId: Long,
        btbId: String?
    ) {
        runCatching {
            if (!btbId.isNullOrBlank()) {
                SofortSync.btbAnstossen(
                    context = context.applicationContext,
                    localBaustelleId = baustelleId,
                    clientBtbId = btbId
                )
            } else {
                // Dokumentierter Legacy-Fallback ohne stabile BTB-ID.
                SofortSync.anstossen(context.applicationContext)
            }
        }
    }

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

    private fun readBaustelleIdFromJson(f: File): Long? = runCatching {
        val id = JSONObject(f.readText(Charsets.UTF_8)).optLong("baustelleId", 0L)
        id.takeIf { it != 0L }
    }.getOrNull()

    private fun findTopFolderForBaustelleId(context: Context, baustelleId: Long): String? {
        folderCacheById[baustelleId]?.let { cached ->
            val root = tbRoot(context)
            if (File(root, cached).exists()) return cached
            folderCacheById.remove(baustelleId)
        }

        val root = tbRoot(context)
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (top in dirs) {
            val btbDir = File(top, "Bautagebuch")
            if (!btbDir.exists()) continue
            val dateDirs = btbDir.listFiles()?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }?.take(24) ?: emptyList()
            for (dd in dateDirs) {
                val candidates = dd.listFiles()?.filter { f ->
                    f.isFile && (f.name == "bautagebuch_tag.json" || f.name == "bautagebuch_nacht.json" ||
                        f.name == "bautagebuch.json" || (f.name.startsWith("btb_") && f.name.endsWith(".json") &&
                        !f.name.contains("_original") && !f.name.contains("_v")))
                }.orEmpty()
                for (f in candidates) {
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

    private fun resolveTopFolderName(context: Context, baustelleId: Long?, baustelleName: String): String {
        val id = baustelleId ?: 0L
        if (id <= 0L) return safeFolderName(baustelleName)
        val canonical = "baustelle_${id}"
        val root = tbRoot(context)
        val canonicalDir = File(root, canonical)
        if (canonicalDir.exists()) return canonical
        val found = findTopFolderForBaustelleId(context, id)
        if (found != null && found != canonical) {
            val oldDir = File(root, found)
            if (oldDir.exists()) {
                runCatching {
                    if (!oldDir.renameTo(canonicalDir)) {
                        folderCacheById[id] = found
                        return found
                    }
                }
                if (canonicalDir.exists()) return canonical
            }
        }
        return canonical
    }

    fun ordner(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate): File {
        val top = resolveTopFolderName(context, baustelleId, baustelleName)
        return ordnerByTop(context, top, datum)
    }

    fun ordner(context: Context, baustelleName: String, datum: LocalDate): File =
        ordner(context, null, baustelleName, datum)

    private fun legacyDateiname(schicht: SchichtTyp): String = when (schicht) {
        SchichtTyp.TAG -> "bautagebuch_tag.json"
        SchichtTyp.NACHT -> "bautagebuch_nacht.json"
    }

    private fun legacyDatei(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ): File = File(ordner(context, baustelleId, baustelleName, datum), legacyDateiname(schicht))

    private fun sehrAlteLegacyDatei(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate): File =
        File(ordner(context, baustelleId, baustelleName, datum), "bautagebuch.json")

    private fun legacyOriginalDatei(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ): File {
        val name = when (schicht) {
            SchichtTyp.TAG -> "bautagebuch_tag_original.json"
            SchichtTyp.NACHT -> "bautagebuch_nacht_original.json"
        }
        return File(ordner(context, baustelleId, baustelleName, datum), name)
    }

    private fun sehrAlteLegacyOriginalDatei(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate): File =
        File(ordner(context, baustelleId, baustelleName, datum), "bautagebuch_original.json")

    private fun safeBtbToken(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9_-]"), "_")
        .take(80)
        .ifBlank { UUID.randomUUID().toString() }

    private fun instanzDatei(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        btbId: String
    ): File = File(ordner(context, baustelleId, baustelleName, datum), "btb_${safeBtbToken(btbId)}.json")

    private fun instanzOriginalDatei(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        btbId: String
    ): File = File(ordner(context, baustelleId, baustelleName, datum), "btb_${safeBtbToken(btbId)}_original.json")

    private fun btbRootDir(context: Context, baustelleId: Long?, baustelleName: String): File {
        val top = resolveTopFolderName(context, baustelleId, baustelleName)
        val dir = File(tbRoot(context), "$top/Bautagebuch")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun activePointerFile(context: Context, baustelleId: Long?, baustelleName: String): File =
        File(btbRootDir(context, baustelleId, baustelleName), ".active_btb.json")

    private data class ActivePointer(val btbId: String, val datumIso: String, val schicht: SchichtTyp)

    private fun readActivePointer(context: Context, baustelleId: Long?, baustelleName: String): ActivePointer? = runCatching {
        val f = activePointerFile(context, baustelleId, baustelleName)
        if (!f.exists()) return@runCatching null
        val o = JSONObject(f.readText(Charsets.UTF_8))
        ActivePointer(
            btbId = o.optString("btbId").takeIf { it.isNotBlank() } ?: return@runCatching null,
            datumIso = o.optString("datumIso").takeIf { it.isNotBlank() } ?: return@runCatching null,
            schicht = runCatching { SchichtTyp.valueOf(o.optString("schicht")) }.getOrDefault(SchichtTyp.TAG)
        )
    }.getOrNull()

    private fun writeActivePointer(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        btbId: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ) {
        val f = activePointerFile(context, baustelleId, baustelleName)
        f.writeText(JSONObject().apply {
            put("btbId", btbId)
            put("datumIso", datum.format(isoFmt))
            put("schicht", schicht.name)
        }.toString(2), Charsets.UTF_8)
    }

    fun aktiveBtbId(context: Context, baustelleId: Long?, baustelleName: String): String? =
        readActivePointer(context, baustelleId, baustelleName)?.btbId

    fun aktivierenBtb(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        btbId: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ) = writeActivePointer(context, baustelleId, baustelleName, btbId, datum, schicht)

    /** Erzeugt nur die Identität für einen bewusst gestarteten BTB. Noch keine BTB-Datei. */
    fun neueBtbIdVorbereiten(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ): String {
        val id = UUID.randomUUID().toString()
        writeActivePointer(context, baustelleId, baustelleName, id, datum, schicht)
        return id
    }

    private fun parseEntryFile(file: File): BautagebuchEintrag? {
        if (!file.exists()) return null
        val json = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return runCatching { fromJson(JSONObject(json)) }.getOrNull()
    }

    private fun instanceFiles(dateDir: File): List<File> = dateDir.listFiles()?.filter { f ->
        f.isFile && f.name.startsWith("btb_") && f.name.endsWith(".json") &&
            !f.name.contains("_original") && !Regex("_v\\d+_").containsMatchIn(f.name)
    }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun ladenById(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        btbId: String
    ): BautagebuchEintrag? {
        val root = btbRootDir(context, baustelleId, baustelleName)
        root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }?.forEach { dd ->
            val datum = runCatching { LocalDate.parse(dd.name, isoFmt) }.getOrNull() ?: return@forEach
            val f = instanzDatei(context, baustelleId, baustelleName, datum, btbId)
            parseEntryFile(f)?.let { e ->
                // Reiner Leseweg: Das Laden eines BTB darf niemals den globalen aktiven
                // BTB-Pointer veraendern. Aktivierung erfolgt ausschliesslich ueber
                // aktivierenBtb()/neueBtbIdVorbereiten() nach einer bewussten Benutzeraktion.
                return e.copy(btbId = e.btbId ?: btbId)
            }
        }
        return null
    }

    data class BaselineInfo(
        val exists: Boolean,
        val changedSinceBaseline: Boolean,
        val baselineLastModifiedEpochMs: Long?
    )

    private data class ResolvedFiles(val current: File, val original: File, val btbId: String?)

    private fun resolveFilesForContext(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ): ResolvedFiles {
        val pointer = readActivePointer(context, baustelleId, baustelleName)
        if (pointer != null && pointer.datumIso == datum.format(isoFmt) && pointer.schicht == schicht) {
            val current = instanzDatei(context, baustelleId, baustelleName, datum, pointer.btbId)
            if (current.exists()) {
                return ResolvedFiles(current, instanzOriginalDatei(context, baustelleId, baustelleName, datum, pointer.btbId), pointer.btbId)
            }
        }
        val newest = instanceFiles(ordner(context, baustelleId, baustelleName, datum))
            .firstOrNull { parseEntryFile(it)?.schicht == schicht }
        if (newest != null) {
            val entry = parseEntryFile(newest)
            val id = entry?.btbId
            if (!id.isNullOrBlank()) {
                return ResolvedFiles(newest, instanzOriginalDatei(context, baustelleId, baustelleName, datum, id), id)
            }
        }
        var current = legacyDatei(context, baustelleId, baustelleName, datum, schicht)
        var original = legacyOriginalDatei(context, baustelleId, baustelleName, datum, schicht)
        if (!current.exists() && schicht == SchichtTyp.TAG) current = sehrAlteLegacyDatei(context, baustelleId, baustelleName, datum)
        if (!original.exists() && schicht == SchichtTyp.TAG) original = sehrAlteLegacyOriginalDatei(context, baustelleId, baustelleName, datum)
        return ResolvedFiles(current, original, null)
    }

    fun baselineInfo(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BaselineInfo {
        val files = resolveFilesForContext(context, baustelleId, baustelleName, datum, schicht)
        val exists = files.original.exists()
        val baselineTs = files.original.takeIf { it.exists() }?.lastModified()
        val changed = exists && files.current.exists() && runCatching {
            files.current.readText(Charsets.UTF_8) != files.original.readText(Charsets.UTF_8)
        }.getOrDefault(false)
        return BaselineInfo(exists, changed, baselineTs)
    }

    fun baselineInfo(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BaselineInfo =
        baselineInfo(context, null, baustelleName, datum, schicht)

    fun baselineInfo(context: Context, baustelleName: String, datum: LocalDate): BaselineInfo =
        baselineInfo(context, baustelleName, datum, SchichtTyp.TAG)

    fun baselineSetzenWennNichtVorhanden(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): Boolean {
        val files = resolveFilesForContext(context, baustelleId, baustelleName, datum, schicht)
        if (!files.current.exists() || files.original.exists()) return false
        runCatching { files.current.copyTo(files.original, overwrite = false) }
        return files.original.exists()
    }

    fun baselineSetzenWennNichtVorhanden(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): Boolean =
        baselineSetzenWennNichtVorhanden(context, null, baustelleName, datum, schicht)

    fun baselineSetzenWennNichtVorhanden(context: Context, baustelleName: String, datum: LocalDate): Boolean =
        baselineSetzenWennNichtVorhanden(context, baustelleName, datum, SchichtTyp.TAG)

    fun laden(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BautagebuchEintrag? {
        val pointer = readActivePointer(context, baustelleId, baustelleName)
        if (pointer != null && pointer.datumIso == datum.format(isoFmt) && pointer.schicht == schicht) {
            val e = ladenById(context, baustelleId, baustelleName, pointer.btbId)
            if (e != null) return e
        }
        val newest = instanceFiles(ordner(context, baustelleId, baustelleName, datum))
            .mapNotNull { parseEntryFile(it) }
            .firstOrNull { it.schicht == schicht }
        if (newest != null && !newest.btbId.isNullOrBlank()) {
            // Reiner Leseweg. Historie, Vortag-Uebernahme und Statusabfragen duerfen
            // den aktiven BTB nicht im Hintergrund umschalten.
            return newest
        }

        var f = legacyDatei(context, baustelleId, baustelleName, datum, schicht)
        if (!f.exists() && schicht == SchichtTyp.TAG) f = sehrAlteLegacyDatei(context, baustelleId, baustelleName, datum)
        val legacy = parseEntryFile(f) ?: return null
        return if (legacy.schicht != schicht) legacy.copy(schicht = schicht) else legacy
    }

    fun laden(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BautagebuchEintrag? =
        laden(context, null, baustelleName, datum, schicht)

    fun laden(context: Context, baustelleName: String, datum: LocalDate): BautagebuchEintrag? =
        laden(context, baustelleName, datum, SchichtTyp.TAG)

    fun ladenBaseline(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ): BautagebuchEintrag? {
        val f = resolveFilesForContext(context, baustelleId, baustelleName, datum, schicht).original
        return parseEntryFile(f)?.let { e -> if (e.schicht != schicht) e.copy(schicht = schicht) else e }
    }

    fun ladenBaseline(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp): BautagebuchEintrag? =
        ladenBaseline(context, null, baustelleName, datum, schicht)

    fun ladenBaseline(context: Context, baustelleName: String, datum: LocalDate): BautagebuchEintrag? =
        ladenBaseline(context, baustelleName, datum, SchichtTyp.TAG)

    /**
     * Speichert nur einen existierenden oder bewusst vorbereiteten BTB.
     * Dadurch kann ein Autosave beim bloßen Öffnen des Bautagebuchs keinen leeren Entwurf anlegen.
     */
    fun speichern(context: Context, entry: BautagebuchEintrag) {
        val datum = runCatching { LocalDate.parse(entry.datumIso, isoFmt) }.getOrNull() ?: return
        val pointer = readActivePointer(context, entry.baustelleId, entry.baustelleName)
        val explicitId = entry.btbId?.takeIf { it.isNotBlank() }
        val pointerMatches = pointer != null && pointer.datumIso == entry.datumIso && pointer.schicht == entry.schicht
        val id = explicitId ?: pointer?.btbId?.takeIf { pointerMatches }

        if (!id.isNullOrBlank()) {
            val normalized = entry.copy(btbId = id)
            val f = instanzDatei(context, entry.baustelleId, entry.baustelleName, datum, id)
            f.writeText(toJson(normalized).toString(2), Charsets.UTF_8)
            writeActivePointer(context, entry.baustelleId, entry.baustelleName, id, datum, normalized.schicht)
            de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
            syncNachLokalemSpeichernAnstossen(context, normalized.baustelleId, id)
            return
        }

        // Altbestand darf weiter gespeichert werden. Ohne vorhandene Datei wird bewusst NICHT neu angelegt.
        var legacy = legacyDatei(context, entry.baustelleId, entry.baustelleName, datum, entry.schicht)
        if (!legacy.exists() && entry.schicht == SchichtTyp.TAG) {
            val veryOld = sehrAlteLegacyDatei(context, entry.baustelleId, entry.baustelleName, datum)
            if (veryOld.exists()) legacy = veryOld
        }
        if (!legacy.exists()) return
        legacy.writeText(toJson(entry).toString(2), Charsets.UTF_8)
        de.tbhelfer.widget.TBHelferWidgetUpdater.updateAll(context)
        syncNachLokalemSpeichernAnstossen(context, entry.baustelleId, null)
    }

    private fun toJson(e: BautagebuchEintrag): JSONObject = JSONObject().apply {
        put("baustelleName", e.baustelleName)
        put("baustelleId", e.baustelleId)
        put("btbId", e.btbId)
        put("datumIso", e.datumIso)
        put("endeDatumIso", e.endeDatumIso)
        put("schicht", e.schicht.name)
        put("projektNameSnapshot", e.projektNameSnapshot)
        put("strecke", e.strecke)
        put("startUhrzeit", e.startUhrzeit)
        put("endeUhrzeit", e.endeUhrzeit)
        put("wetter", e.wetter)
        put("maxFotosInPdf", e.maxFotosInPdf)
        put("weather", e.weather?.let { wb -> JSONObject().apply {
            put("standEpochMs", wb.standEpochMs); put("source", wb.source.name)
            put("locationName", wb.locationName); put("stationName", wb.stationName)
            put("lat", wb.lat); put("lon", wb.lon)
            put("slots", JSONArray().apply { wb.slots.forEach { slot -> put(JSONObject().apply {
                put("uhrzeit", slot.uhrzeit); put("tempC", slot.tempC); put("bewoelkung", slot.bewoelkung)
                put("niederschlag", slot.niederschlag); put("niederschlagMm", slot.niederschlagMm)
            }) } })
        } })
        put("weatherSlots", JSONArray().apply { e.weatherSlots.forEach { slot -> put(JSONObject().apply {
            put("uhrzeit", slot.uhrzeit); put("tempC", slot.tempC); put("bewoelkung", slot.bewoelkung)
            put("niederschlag", slot.niederschlag); put("niederschlagMm", slot.niederschlagMm)
        }) } })
        put("firmen", JSONArray().apply { e.firmen.forEach { f -> put(JSONObject().apply {
            put("firmaId", f.firmaId); put("name", f.name)
            put("personalVon", f.personalVon); put("personalBis", f.personalBis)
            put("maschinenVon", f.maschinenVon); put("maschinenBis", f.maschinenBis)
            put("visibleInPersonal", f.visibleInPersonal); put("visibleInMaschinen", f.visibleInMaschinen)
            put("isDeleted", f.isDeleted)
            put("personal", JSONArray().apply { f.personal.forEach { p -> put(JSONObject().apply { put("anzahl", p.anzahl); put("rolle", p.rolle) }) } })
            put("geraete", JSONArray().apply { f.geraete.forEach { g -> put(JSONObject().apply { put("anzahl", g.anzahl); put("bezeichnung", g.bezeichnung) }) } })
            put("leistungen", JSONArray().apply { f.leistungen.forEach { l -> put(JSONObject().apply { put("uhrzeit", l.uhrzeit); put("text", l.text) }) } })
        }) } })
        fun sectionToJson(sb: SectionBlock): JSONArray = JSONArray().apply { sb.items.forEach { item -> put(JSONObject().apply {
            put("uhrzeit", item.uhrzeit); put("text", item.text); put("kmVon", item.kmVon); put("kmBis", item.kmBis)
        }) } }
        put("arbeiten", sectionToJson(e.arbeiten))
        put("abnahmen", sectionToJson(e.abnahmen))
        put("einweisungUnterweisung", sectionToJson(e.einweisungUnterweisung))
        put("besonderheiten", sectionToJson(e.besonderheiten))
        put("maengelSchaeden", sectionToJson(e.maengelSchaeden))
        put("personal", JSONArray(e.personal)); put("geraete", JSONArray(e.geraete)); put("notizen", e.notizen)
        put("includeFotos", e.includeFotos); put("includeBtbs", e.includeBtbs); put("istFertig", e.istFertig)
        put("dokumentVersion", e.dokumentVersion)
        put("letzterAbschlussEpochMs", e.letzterAbschlussEpochMs)
        put("letzterAbschlussBenutzer", e.letzterAbschlussBenutzer)
        put("pdfAuswahl", JSONObject().apply { e.pdfAuswahl.forEach { (k, ids) -> put(k, JSONArray().apply { ids.forEach { put(it) } }) } })
        put("erstelltAmEpochMs", e.erstelltAmEpochMs); put("geaendertAmEpochMs", e.geaendertAmEpochMs)
        put("fotos", JSONArray().apply { e.fotos.forEach { fr -> put(JSONObject().apply {
            put("fotoId", fr.fotoId); put("lat", fr.lat); put("lon", fr.lon); put("km", fr.km)
            put("objektkennung", fr.objektkennung); put("zusatzinfo", fr.zusatzinfo); put("erstelltAmEpochMs", fr.erstelltAmEpochMs)
            put("bildtyp", fr.bildtyp); put("indikator", fr.indikator)
        }) } })
    }

    private fun fromJson(o: JSONObject): BautagebuchEintrag {
        fun arr(key: String): List<String> {
            if (!o.has(key) || o.isNull(key)) return emptyList()
            val a = o.getJSONArray(key)
            return (0 until a.length()).map { a.optString(it).orEmpty() }.filter { it.isNotBlank() }
        }
        fun slotsFromArray(a: JSONArray?): List<WetterSlot> {
            if (a == null) return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val so = a.optJSONObject(i) ?: return@mapNotNull null
                val u = so.optString("uhrzeit").orEmpty(); if (u.isBlank()) return@mapNotNull null
                WetterSlot(
                    uhrzeit = u,
                    tempC = if (so.has("tempC") && !so.isNull("tempC")) so.optInt("tempC") else null,
                    bewoelkung = so.optString("bewoelkung").takeUnless { it.isBlank() },
                    niederschlag = so.optString("niederschlag").takeUnless { it.isBlank() },
                    niederschlagMm = if (so.has("niederschlagMm") && !so.isNull("niederschlagMm")) so.optDouble("niederschlagMm") else null
                )
            }
        }
        fun weatherBlock(): WeatherBlock? {
            val w = o.optJSONObject("weather") ?: return null
            return WeatherBlock(
                standEpochMs = if (w.has("standEpochMs") && !w.isNull("standEpochMs")) w.optLong("standEpochMs") else null,
                source = runCatching { WeatherSource.valueOf(w.optString("source", WeatherSource.AUTO.name)) }.getOrDefault(WeatherSource.AUTO),
                locationName = w.optString("locationName").takeUnless { it.isBlank() },
                stationName = w.optString("stationName").takeUnless { it.isBlank() },
                lat = if (w.has("lat") && !w.isNull("lat")) w.optDouble("lat") else null,
                lon = if (w.has("lon") && !w.isNull("lon")) w.optDouble("lon") else null,
                slots = slotsFromArray(w.optJSONArray("slots"))
            )
        }
        fun sectionFromArray(a: JSONArray?): SectionBlock {
            if (a == null) return SectionBlock()
            return SectionBlock((0 until a.length()).mapNotNull { i ->
                val x = a.optJSONObject(i) ?: return@mapNotNull null
                val text = x.optString("text").trim(); if (text.isBlank()) return@mapNotNull null
                SectionItem(
                    uhrzeit = x.optString("uhrzeit").takeUnless { it.isBlank() }, text = text,
                    kmVon = if (x.has("kmVon") && !x.isNull("kmVon")) x.optDouble("kmVon").takeUnless { it.isNaN() } else null,
                    kmBis = if (x.has("kmBis") && !x.isNull("kmBis")) x.optDouble("kmBis").takeUnless { it.isNaN() } else null
                )
            })
        }
        fun firmenList(): List<FirmaBlock> {
            val a = o.optJSONArray("firmen") ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val f = a.optJSONObject(i) ?: return@mapNotNull null
                val id = f.optString("firmaId"); val name = f.optString("name")
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                val p = f.optJSONArray("personal")
                val g = f.optJSONArray("geraete")
                val l = f.optJSONArray("leistungen")
                FirmaBlock(
                    firmaId = id, name = name,
                    personalVon = f.optString("personalVon").takeUnless { it.isBlank() },
                    personalBis = f.optString("personalBis").takeUnless { it.isBlank() },
                    maschinenVon = f.optString("maschinenVon").takeUnless { it.isBlank() },
                    maschinenBis = f.optString("maschinenBis").takeUnless { it.isBlank() },
                    visibleInPersonal = f.optBoolean("visibleInPersonal", true),
                    visibleInMaschinen = f.optBoolean("visibleInMaschinen", true),
                    isDeleted = f.optBoolean("isDeleted", false),
                    personal = (0 until (p?.length() ?: 0)).mapNotNull { pi -> p?.optJSONObject(pi) }.mapNotNull { po ->
                        val rolle = po.optString("rolle").trim(); if (rolle.isBlank()) null else PersonalZeile(if (po.has("anzahl") && !po.isNull("anzahl")) po.optInt("anzahl") else null, rolle)
                    },
                    geraete = (0 until (g?.length() ?: 0)).mapNotNull { gi -> g?.optJSONObject(gi) }.mapNotNull { go ->
                        val bez = go.optString("bezeichnung").trim(); if (bez.isBlank()) null else GeraetZeile(if (go.has("anzahl") && !go.isNull("anzahl")) go.optInt("anzahl") else null, bez)
                    },
                    leistungen = (0 until (l?.length() ?: 0)).mapNotNull { li -> l?.optJSONObject(li) }.mapNotNull { lo ->
                        val text = lo.optString("text").trim(); if (text.isBlank()) null else LeistungZeile(lo.optString("uhrzeit").takeUnless { it.isBlank() }, text)
                    }
                )
            }
        }
        fun fotosList(): List<FotoRef> {
            val a = o.optJSONArray("fotos") ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val f = a.optJSONObject(i) ?: return@mapNotNull null
                val id = f.optLong("fotoId", -1); if (id <= 0) return@mapNotNull null
                FotoRef(
                    fotoId = id,
                    lat = if (f.has("lat") && !f.isNull("lat")) f.optDouble("lat") else null,
                    lon = if (f.has("lon") && !f.isNull("lon")) f.optDouble("lon") else null,
                    km = if (f.has("km") && !f.isNull("km")) f.optDouble("km") else null,
                    objektkennung = f.optString("objektkennung").takeUnless { it.isBlank() },
                    zusatzinfo = f.optString("zusatzinfo").takeUnless { it.isBlank() },
                    erstelltAmEpochMs = if (f.has("erstelltAmEpochMs") && !f.isNull("erstelltAmEpochMs")) f.optLong("erstelltAmEpochMs") else null,
                    bildtyp = f.optString("bildtyp").takeUnless { it.isBlank() },
                    indikator = f.optString("indikator").takeUnless { it.isBlank() }
                )
            }
        }
        fun pdfAuswahlMap(): Map<String, List<Long>> {
            val obj = o.optJSONObject("pdfAuswahl") ?: return emptyMap()
            val out = LinkedHashMap<String, List<Long>>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next(); val a = obj.optJSONArray(k) ?: continue
                out[k] = (0 until a.length()).map { a.optLong(it) }.distinct()
            }
            return out
        }

        return BautagebuchEintrag(
            baustelleName = o.optString("baustelleName"),
            baustelleId = o.optLong("baustelleId"),
            btbId = o.optString("btbId").takeUnless { it.isBlank() },
            datumIso = o.optString("datumIso"),
            endeDatumIso = o.optString("endeDatumIso").takeUnless { it.isBlank() },
            schicht = runCatching { SchichtTyp.valueOf(o.optString("schicht", SchichtTyp.TAG.name)) }.getOrDefault(SchichtTyp.TAG),
            projektNameSnapshot = o.optString("projektNameSnapshot").takeUnless { it.isBlank() },
            strecke = o.optString("strecke").takeUnless { it.isBlank() },
            startUhrzeit = o.optString("startUhrzeit").takeUnless { it.isBlank() },
            endeUhrzeit = o.optString("endeUhrzeit").takeUnless { it.isBlank() },
            wetter = o.optString("wetter").takeUnless { it.isBlank() },
            weather = weatherBlock(),
            weatherSlots = slotsFromArray(o.optJSONArray("weatherSlots")),
            maxFotosInPdf = o.optInt("maxFotosInPdf", 60),
            pdfAuswahl = pdfAuswahlMap(),
            firmen = firmenList(),
            arbeiten = sectionFromArray(o.optJSONArray("arbeiten")),
            abnahmen = sectionFromArray(o.optJSONArray("abnahmen")),
            einweisungUnterweisung = sectionFromArray(o.optJSONArray("einweisungUnterweisung")),
            besonderheiten = sectionFromArray(o.optJSONArray("besonderheiten")),
            maengelSchaeden = sectionFromArray(o.optJSONArray("maengelSchaeden")),
            personal = arr("personal"), geraete = arr("geraete"),
            notizen = o.optString("notizen").takeUnless { it.isBlank() },
            includeFotos = o.optBoolean("includeFotos", true), includeBtbs = o.optBoolean("includeBtbs", true),
            fotos = fotosList(), istFertig = o.optBoolean("istFertig", false),
            dokumentVersion = o.optInt("dokumentVersion", 0).coerceAtLeast(0),
            letzterAbschlussEpochMs = if (o.has("letzterAbschlussEpochMs") && !o.isNull("letzterAbschlussEpochMs")) o.optLong("letzterAbschlussEpochMs") else null,
            letzterAbschlussBenutzer = o.optString("letzterAbschlussBenutzer").takeUnless { it.isBlank() },
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
        val alterTage: Long,
        val btbId: String? = entry.btbId
    )

    fun verlaufFuerBaustelle(context: Context, baustelleId: Long?, baustelleName: String): List<VerlaufEintrag> {
        val top = resolveTopFolderName(context, baustelleId, baustelleName)
        val root = File(tbRoot(context), "$top/Bautagebuch")
        if (!root.exists() || !root.isDirectory) return emptyList()
        val today = LocalDate.now()
        val out = ArrayList<VerlaufEintrag>()

        root.listFiles()?.filter { it.isDirectory }?.forEach { dateDir ->
            val date = runCatching { LocalDate.parse(dateDir.name, isoFmt) }.getOrNull() ?: return@forEach
            instanceFiles(dateDir).forEach { f ->
                val e = parseEntryFile(f) ?: return@forEach
                val id = e.btbId ?: return@forEach
                val original = instanzOriginalDatei(context, baustelleId, baustelleName, date, id)
                val age = java.time.temporal.ChronoUnit.DAYS.between(date, today).coerceAtLeast(0L)
                val pdf = neuestePdfFuerVerlauf(context, baustelleId, baustelleName, e)
                out += VerlaufEintrag(date, e.schicht, e, original.exists(), pdf != null, pdf, age <= 7, age, id)
            }

            listOf(
                Triple("bautagebuch_tag.json", SchichtTyp.TAG, "legacy:${date}:TAG"),
                Triple("bautagebuch_nacht.json", SchichtTyp.NACHT, "legacy:${date}:NACHT"),
                Triple("bautagebuch.json", SchichtTyp.TAG, "legacy:${date}:ALT")
            ).forEach { (name, shift, legacyId) ->
                val f = File(dateDir, name)
                if (!f.exists()) return@forEach
                // bautagebuch.json nicht zusätzlich zählen, wenn moderner TAG-Altbestand vorhanden ist.
                if (name == "bautagebuch.json" && File(dateDir, "bautagebuch_tag.json").exists()) return@forEach
                val raw = parseEntryFile(f) ?: return@forEach
                val e = if (raw.schicht != shift) raw.copy(schicht = shift) else raw
                val original = if (name == "bautagebuch.json") File(dateDir, "bautagebuch_original.json") else legacyOriginalDatei(context, baustelleId, baustelleName, date, shift)
                val age = java.time.temporal.ChronoUnit.DAYS.between(date, today).coerceAtLeast(0L)
                val pdf = neuestePdfFuerVerlauf(context, baustelleId, baustelleName, e)
                out += VerlaufEintrag(date, shift, e, original.exists(), pdf != null, pdf, age <= 7, age, legacyId)
            }
        }

        return out.distinctBy { it.btbId ?: "${it.datum}_${it.schicht}_${it.entry.erstelltAmEpochMs}" }
            .sortedWith(compareByDescending<VerlaufEintrag> { it.entry.datumIso }
                .thenByDescending { it.entry.startUhrzeit.orEmpty() }
                .thenByDescending { it.entry.erstelltAmEpochMs })
    }

    private fun neuestePdfFuerVerlauf(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        entry: BautagebuchEintrag
    ): File? {
        // Android 200036: Moderne Abschluss-PDFs immer Ã¼ber denselben kanonischen Finder
        // auflÃ¶sen. Dessen Token entspricht dem tatsÃ¤chlich verwendeten Dateinamen.
        if (!entry.btbId.isNullOrBlank()) {
            BtbAbschlussPdfFinder.neueste(context, entry)
                ?.takeIf { it.exists() }
                ?.let { return it }
        }
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        val top = if ((baustelleId ?: 0L) > 0L) "baustelle_${baustelleId}" else safeFolderName(baustelleName)
        val date = runCatching { LocalDate.parse(entry.datumIso, isoFmt) }.getOrNull() ?: return null
        val dir = File(base, "TB-Helfer/$top/BTB/${date.format(isoFmt)}")
        if (!dir.exists()) return null
        val idToken = entry.btbId?.let(::safeBtbToken)
        val all = dir.listFiles()?.filter { it.isFile && it.extension.equals("pdf", true) && !it.name.contains("_entwurf_", true) }.orEmpty()
        if (!idToken.isNullOrBlank()) {
            all.filter { it.name.contains(idToken, true) }.maxByOrNull { it.lastModified() }?.let { return it }
        }
        val dateKey = date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
        return all.filter { it.name.contains(dateKey) }.maxByOrNull { it.lastModified() }
    }
    fun sichererName(input: String): String = safeFolderName(input)

    private fun safeFolderName(input: String): String = input.trim()
        .replace("..", ".")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(80)
        .ifBlank { "Unbekannt" }

    fun anzahlBtbsFuerBaustelle(context: Context, baustelleId: Long?, baustelleName: String): Int =
        verlaufFuerBaustelle(context, baustelleId, baustelleName).size

    fun loescheAlleBtbsFuerBaustelle(context: Context, baustelleId: Long?, baustelleName: String) {
        val top = resolveTopFolderName(context, baustelleId, baustelleName)
        val dir = File(tbRoot(context), top)
        if (dir.exists()) runCatching { dir.deleteRecursively() }
        baustelleId?.let { folderCacheById.remove(it) }
    }

    fun loeschen(context: Context, baustelleId: Long?, baustelleName: String, datum: LocalDate, schicht: SchichtTyp) {
        val pointer = readActivePointer(context, baustelleId, baustelleName)
        if (pointer != null && pointer.datumIso == datum.format(isoFmt) && pointer.schicht == schicht) {
            runCatching { instanzDatei(context, baustelleId, baustelleName, datum, pointer.btbId).delete() }
            runCatching { instanzOriginalDatei(context, baustelleId, baustelleName, datum, pointer.btbId).delete() }
            runCatching { activePointerFile(context, baustelleId, baustelleName).delete() }
        }
        runCatching { legacyDatei(context, baustelleId, baustelleName, datum, schicht).delete() }
        runCatching { legacyOriginalDatei(context, baustelleId, baustelleName, datum, schicht).delete() }
        if (schicht == SchichtTyp.TAG) {
            runCatching { sehrAlteLegacyDatei(context, baustelleId, baustelleName, datum).delete() }
            runCatching { sehrAlteLegacyOriginalDatei(context, baustelleId, baustelleName, datum).delete() }
        }
    }

    fun loeschen(context: Context, baustelleName: String, datum: LocalDate, schicht: SchichtTyp) =
        loeschen(context, null, baustelleName, datum, schicht)

    /**
     * Historischer API-Name aus der UI. Seit dem Multi-BTB-Modell wird beim Start eines neuen BTB
     * der vorhandene Bericht NICHT mehr archiviert/geleert. Stattdessen wird nur eine neue stabile
     * BTB-ID vorbereitet. Der nächste explizite Speichervorgang schreibt in eine neue Datei.
     */
    fun archivierenUndLeeren(
        context: Context,
        baustelleId: Long?,
        baustelleName: String,
        datum: LocalDate,
        schicht: SchichtTyp
    ): Boolean {
        neueBtbIdVorbereiten(context, baustelleId, baustelleName, datum, schicht)
        return false
    }
}
