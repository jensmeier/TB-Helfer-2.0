package de.tbhelfer.ui.bautagebuch

import android.content.Context
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoEntity
import de.tbhelfer.ui.abnahme.AbnahmeSpeicher
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

/** Baut aus Datenbank-/Foto-Daten genau eine fachliche BTB-Struktur. */
object BtbDruckModellBuilder {
    private val isoFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val deDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val timeInFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    private data class PrintEvent(
        val sortTime: LocalTime,
        val kind: Int,
        val seq: Int,
        val ortId: Long,
        val firma: String?,
        val line: String
    )

    private data class OrtInfo(
        val labels: Map<Long, String>,
        val order: Map<Long, Int>,
        val depth: Map<Long, Int>,
        val parent: Map<Long, Long?>
    )

    suspend fun build(
        context: Context,
        entry: BautagebuchEintrag,
        selectedFotosOrdered: List<FotoEntity>,
        fotoNum: Map<Long, Int>,
        textOverrides: Map<Long, String> = emptyMap(),
        startOverrides: Map<Long, String> = emptyMap(),
        kmOverrides: Map<Long, BtbPhotoKmOverrideStore.KmOverride> = emptyMap()
    ): BtbDruckModell {
        val datum = runCatching { LocalDate.parse(entry.datumIso, isoFmt) }.getOrDefault(LocalDate.now())
        val ortInfo = buildOrtInfo(entry)
        val firmCanonicalByLower = entry.firmen
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .associateBy { it.lowercase(Locale.ROOT) }

        fun canonFirm(name: String?): String? {
            val t = name?.trim().orEmpty()
            if (t.isBlank()) return null
            return firmCanonicalByLower[t.lowercase(Locale.ROOT)] ?: t
        }

        fun eventGroups(eventsIn: List<PrintEvent>): List<BtbDruckFirma> {
            if (eventsIn.isEmpty()) return emptyList()
            val events = eventsIn
                .map { it.copy(firma = canonFirm(it.firma)) }
                .sortedWith(compareBy<PrintEvent> { it.sortTime }.thenBy { it.kind }.thenBy { it.seq })

            val byFirma = events.groupBy { it.firma }
            val firmKeys = byFirma.keys.filterNotNull().map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val orderedFirms = buildList<String?> {
                if (byFirma[null].orEmpty().isNotEmpty()) add(null)
                addAll(firmKeys.sortedBy { key -> byFirma[key]?.minOfOrNull { it.sortTime } ?: LocalTime.MIN })
            }

            return orderedFirms.mapNotNull { firma ->
                val list = byFirma[firma].orEmpty()
                if (list.isEmpty()) return@mapNotNull null
                val byOrt = list.groupBy { it.ortId }
                val orte = byOrt.keys
                    .sortedWith(compareBy<Long> { ortInfo.order[it] ?: Int.MAX_VALUE }.thenBy { it })
                    .mapNotNull { oid ->
                        val items = byOrt[oid].orEmpty()
                            .sortedWith(compareBy<PrintEvent> { it.sortTime }.thenBy { it.kind }.thenBy { it.seq })
                            .map { it.line }
                            .filter { it.isNotBlank() }
                        if (items.isEmpty()) null else {
                            val parentId = ortInfo.parent[oid]
                            val parentName = parentId?.let { ortInfo.labels[it] }
                                ?.takeIf { it.isNotBlank() && !it.equals("Hauptbaustelle", ignoreCase = true) }
                            BtbDruckOrt(
                                name = ortInfo.labels[oid] ?: "Ort $oid",
                                eintraege = items,
                                depth = (ortInfo.depth[oid] ?: 0).coerceAtLeast(0),
                                parentName = parentName
                            )
                        }
                    }
                if (orte.isEmpty()) null else BtbDruckFirma(firma ?: "Allgemein", orte)
            }
        }

        val btbFotosByType = selectedFotosOrdered.groupBy { it.bildtyp }

        val strukturierteAbnahmeEvents: List<PrintEvent> = AbnahmeSpeicher.alle(context, entry.baustelleId)
            .filter { a ->
                listOfNotNull(a.startEpochMs, a.geaendertAmEpochMs, a.abgeschlossenAmEpochMs)
                    .map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    .any { it == datum }
            }
            .mapIndexed { index, a ->
                val zonedDates = listOfNotNull(a.abgeschlossenAmEpochMs, a.geaendertAmEpochMs, a.startEpochMs)
                val relevantEpoch = zonedDates.firstOrNull {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == datum
                } ?: a.startEpochMs
                val zeit = Instant.ofEpochMilli(relevantEpoch).atZone(ZoneId.systemDefault()).toLocalTime()
                val line = buildString {
                    append("Abnahme ${a.nummer.toString().padStart(3, '0')}: ")
                    append(a.bezeichnung.ifBlank { "ohne Bezeichnung" })
                    if (a.objekt.isNotBlank()) append(" · ${a.objekt}")
                    append(" · ${AbnahmeSpeicher.statusLabel(a)}")
                    append(" · ${a.fotos.size} Foto(s), ${a.anlagen.size} Planunterlage(n)")
                    if (a.restpunkte.isNotBlank()) append(" · Restpunkte vorhanden")
                }
                PrintEvent(
                    sortTime = zeit,
                    kind = 0,
                    seq = 5_000 + index,
                    ortId = a.unterKnotenId ?: 0L,
                    firma = a.firma.takeIf { it.isNotBlank() },
                    line = line
                )
            }

        val abschnitte = listOf(
            BtbDruckAbschnitt(
                titel = "4. Ausgeführte Arbeiten",
                leerText = "Keine Arbeiten eingetragen.",
                firmen = eventGroups(
                    leistungEvents(entry, seqBase = 0) +
                        manualEventsFromMultiline(entry.notizen, kind = 1, seqBase = 10_000, firmCanonicalByLower = firmCanonicalByLower) +
                        fotoEvents(
                            fotos = btbFotosByType[Bildtyp.FOTODOKUMENTATION].orEmpty(),
                            fotoNum = fotoNum,
                            textOverrides = textOverrides,
                            startOverrides = startOverrides,
                            kmOverrides = kmOverrides,
                            kind = 2,
                            seqBase = 20_000,
                            firmCanonicalByLower = firmCanonicalByLower
                        )
                )
            ),
            standardAbschnitt(
                titel = "5. Abnahmen",
                leerText = "Keine Abnahmen durchgeführt.",
                block = entry.abnahmen,
                fotoType = Bildtyp.ABNAHMEN,
                fotosByType = btbFotosByType,
                fotoNum = fotoNum,
                textOverrides = textOverrides,
                startOverrides = startOverrides,
                kmOverrides = kmOverrides,
                firmCanonicalByLower = firmCanonicalByLower,
                extraEvents = strukturierteAbnahmeEvents,
                eventGroups = { events -> eventGroups(events) }
            ),
            standardAbschnitt(
                titel = "6. Einweisung/Unterweisung",
                leerText = "Keine Einweisung/Unterweisung durchgeführt.",
                block = entry.einweisungUnterweisung,
                fotoType = Bildtyp.EINWEISUNG_UNTERWEISUNG,
                fotosByType = btbFotosByType,
                fotoNum = fotoNum,
                textOverrides = textOverrides,
                startOverrides = startOverrides,
                kmOverrides = kmOverrides,
                firmCanonicalByLower = firmCanonicalByLower,
                eventGroups = { events -> eventGroups(events) }
            ),
            standardAbschnitt(
                titel = "7. Besonderheiten",
                leerText = "Keine Besonderheiten.",
                block = entry.besonderheiten,
                fotoType = Bildtyp.BESONDERHEITEN,
                fotosByType = btbFotosByType,
                fotoNum = fotoNum,
                textOverrides = textOverrides,
                startOverrides = startOverrides,
                kmOverrides = kmOverrides,
                firmCanonicalByLower = firmCanonicalByLower,
                eventGroups = { events -> eventGroups(events) }
            ),
            standardAbschnitt(
                titel = "8. Mängel/Schäden",
                leerText = "Keine Mängel/Schäden gemeldet.",
                block = entry.maengelSchaeden,
                fotoType = Bildtyp.MAENGEL_SCHAEDEN,
                fotosByType = btbFotosByType,
                fotoNum = fotoNum,
                textOverrides = textOverrides,
                startOverrides = startOverrides,
                kmOverrides = kmOverrides,
                firmCanonicalByLower = firmCanonicalByLower,
                eventGroups = { events -> eventGroups(events) }
            )
        )

        return BtbDruckModell(
            baustelle = entry.baustelleName,
            datumAnzeige = dateRangeText(datum, entry.startUhrzeit, entry.endeUhrzeit),
            zeitAnzeige = "${entry.startUhrzeit ?: "-"} – ${entry.endeUhrzeit ?: "-"}",
            wetter = buildWetterLines(entry),
            wetterInfo = buildWetterInfoLine(entry),
            personal = buildPersonal(entry),
            maschinen = buildMaschinen(entry),
            abschnitte = abschnitte
        )
    }

    private fun standardAbschnitt(
        titel: String,
        leerText: String,
        block: SectionBlock,
        fotoType: Bildtyp,
        fotosByType: Map<Bildtyp, List<FotoEntity>>,
        fotoNum: Map<Long, Int>,
        textOverrides: Map<Long, String>,
        startOverrides: Map<Long, String>,
        kmOverrides: Map<Long, BtbPhotoKmOverrideStore.KmOverride>,
        firmCanonicalByLower: Map<String, String>,
        extraEvents: List<PrintEvent> = emptyList(),
        eventGroups: (List<PrintEvent>) -> List<BtbDruckFirma>
    ): BtbDruckAbschnitt {
        val events = extraEvents + manualEventsFromSection(block, kind = 0, seqBase = 0, firmCanonicalByLower = firmCanonicalByLower) +
            fotoEvents(
                fotos = fotosByType[fotoType].orEmpty(),
                fotoNum = fotoNum,
                textOverrides = textOverrides,
                startOverrides = startOverrides,
                kmOverrides = kmOverrides,
                kind = 1,
                seqBase = 10_000,
                firmCanonicalByLower = firmCanonicalByLower
            )
        return BtbDruckAbschnitt(titel = titel, leerText = leerText, firmen = eventGroups(events))
    }

    private suspend fun buildOrtInfo(entry: BautagebuchEintrag): OrtInfo {
        val baumRepo = ServiceLocator.baustellenBaumRepository
        val rootKnoten = runCatching { baumRepo.hauptNachAltBaustelleId(entry.baustelleId) }.getOrNull()
        val rootId = rootKnoten?.id
        val alleKnoten: List<BaustellenKnotenEntity> = if (rootId != null) {
            runCatching { baumRepo.alleKnotenZuRoot(rootId).first() }.getOrDefault(emptyList())
        } else emptyList()

        val childrenByParent = alleKnoten.filter { it.parentId != null }.groupBy { it.parentId!! }
        val ordered = ArrayList<BaustellenKnotenEntity>()
        fun dfs(parentId: Long) {
            childrenByParent[parentId].orEmpty()
                .sortedWith(compareBy<BaustellenKnotenEntity> { it.kmVon ?: Double.MAX_VALUE }
                    .thenBy { (it.knotenName ?: it.bahnhofName ?: it.streckenkennung ?: "").trim().lowercase(Locale.ROOT) }
                    .thenBy { it.erstelltAmEpochMs })
                .forEach { k ->
                    ordered.add(k)
                    dfs(k.id)
                }
        }
        if (rootId != null) dfs(rootId)

        val labels = LinkedHashMap<Long, String>()
        val order = LinkedHashMap<Long, Int>()
        val depth = LinkedHashMap<Long, Int>()
        val parent = LinkedHashMap<Long, Long?>()
        labels[0L] = "Hauptbaustelle"
        order[0L] = 0
        depth[0L] = 0
        parent[0L] = null
        if (rootId != null) {
            labels[rootId] = "Hauptbaustelle"
            order[rootId] = 0
            depth[rootId] = 0
            parent[rootId] = null
        }
        val depthById = HashMap<Long, Int>()
        fun markDepth(parentId: Long, d: Int) {
            childrenByParent[parentId].orEmpty().forEach { k ->
                depthById[k.id] = d
                markDepth(k.id, d + 1)
            }
        }
        if (rootId != null) markDepth(rootId, 0)
        ordered.forEachIndexed { idx, k ->
            labels[k.id] = normalizeOrtAnzeigeName(formatKnotenLabel(entry, k))
            order[k.id] = idx + 1
            val d = depthById[k.id] ?: 0
            depth[k.id] = d
            parent[k.id] = k.parentId?.takeIf { d > 0 && it != rootId }
        }
        return OrtInfo(labels, order, depth, parent)
    }

    private fun normalizeDisplayText(raw: String): String {
        return raw.trim()
            // Schreibweise aus UI/DB vereinheitlichen: "BKKgr.2" -> "BKK gr.2"
            .replace(Regex("""BKK\s*gr\s*\.?\s*(\d+)""", RegexOption.IGNORE_CASE)) { m -> "BKK gr.${m.groupValues[1]}" }
            .replace(Regex("""(KS\s*\d+(?:\.\d+)*)(?:\.)?\s*(Anbindung)""", RegexOption.IGNORE_CASE)) { m -> "${m.groupValues[1].replace(" ", "")} ${m.groupValues[2]}" }
            // Nur fehlende Leerzeichen an Bereichsstrichen in Namen korrigieren; KM-Bereiche wie 5.000–5.200 bleiben unverändert.
            .replace(Regex("""\s*–\s*"""), " – ")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()
    }

    private fun formatKnotenLabel(entry: BautagebuchEintrag, k: BaustellenKnotenEntity): String {
        val base = when (k.typ) {
            BaustellenKnotenTyp.FREIE_STRECKE -> {
                val n = k.knotenName?.trim().orEmpty()
                val sk = k.streckenkennung?.trim().orEmpty()
                when {
                    n.isNotBlank() -> n
                    sk.isNotBlank() -> "Freie Strecke $sk"
                    else -> "Freie Strecke"
                }
            }
            BaustellenKnotenTyp.BAHNHOF -> {
                val bhf = k.bahnhofName?.trim().orEmpty()
                val glw = k.gleisOderWeiche?.trim().orEmpty()
                val n = k.knotenName?.trim().orEmpty()
                when {
                    n.isNotBlank() -> n
                    bhf.isNotBlank() && glw.isNotBlank() -> "Bahnhof $bhf – $glw"
                    bhf.isNotBlank() -> "Bahnhof $bhf"
                    else -> "Bahnhof"
                }
            }
            BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> k.knotenName?.trim().takeIf { !it.isNullOrBlank() } ?: "Allgemein / Objekt"
            BaustellenKnotenTyp.HAUPT -> entry.baustelleName
        }
        val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
            ?.let { " $it" }.orEmpty()
        return normalizeDisplayText(base + km)
    }

    private fun normalizeOrtAnzeigeName(raw: String): String {
        val cleaned = raw.trim().removePrefix("—").trim()
        return when {
            cleaned.isBlank() -> "Hauptbaustelle"
            cleaned.equals("Nur Hauptbaustelle", ignoreCase = true) -> "Hauptbaustelle"
            cleaned.endsWith("Nur Hauptbaustelle", ignoreCase = true) -> "Hauptbaustelle"
            else -> normalizeDisplayText(cleaned)
        }
    }

    private fun parseMinutes(t: String?): Int? {
        val p = t?.trim().orEmpty().split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun dateRangeText(datum: LocalDate, start: String?, ende: String?): String {
        val sMin = parseMinutes(start)
        val eMin = parseMinutes(ende)
        if (sMin == null || eMin == null || eMin > sMin) return datum.format(deDateFmt)
        val endDate = datum.plusDays(1)
        return if (endDate.month == datum.month && endDate.year == datum.year) {
            datum.format(DateTimeFormatter.ofPattern("dd.")) + "/" + endDate.format(deDateFmt)
        } else {
            datum.format(deDateFmt) + " – " + endDate.format(deDateFmt)
        }
    }

    private fun buildWetterInfoLine(entry: BautagebuchEintrag): String? {
        val wb = entry.weather ?: return null
        val origin: String? = wb.locationName?.takeIf { it.isNotBlank() }
            ?: wb.stationName?.takeIf { it.isNotBlank() }
            ?: (wb.lat?.let { lat -> wb.lon?.let { lon -> String.format(Locale.ROOT, "%.5f, %.5f", lat, lon) } })
        val originLine = origin?.let {
            when {
                !wb.locationName.isNullOrBlank() -> "Ort: $it"
                !wb.stationName.isNullOrBlank() -> "Station: $it"
                else -> "Koordinaten: $it"
            }
        }
        val stand = wb.standEpochMs?.let { epoch ->
            runCatching {
                Instant.ofEpochMilli(epoch)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(timeFmt)
            }.getOrNull()
        }
        return listOfNotNull(
            originLine,
            stand?.let { "Stand: $it" },
            "Quelle: ${wb.source.name}"
        ).joinToString("   ").takeIf { it.isNotBlank() }
    }

    private fun buildWetterLines(entry: BautagebuchEintrag): List<String> {
        val slots = entry.weather?.slots?.takeIf { it.isNotEmpty() } ?: entry.weatherSlots
        if (slots.isEmpty()) return listOf(entry.wetter?.takeIf { it.isNotBlank() } ?: "Keine Wetterdaten")
        return slots.map { s ->
            listOfNotNull(
                s.uhrzeit.takeIf { it.isNotBlank() },
                s.tempC?.let { "$it°C" },
                s.bewoelkung?.takeIf { it.isNotBlank() },
                s.niederschlag?.takeIf { it.isNotBlank() }?.replace("(", "")?.replace(")", "")
            ).joinToString(" ")
        }
    }

    private fun buildPersonal(entry: BautagebuchEintrag): List<BtbDruckFirmaListe> {
        val structured = entry.firmen
            .filter { !it.isDeleted && it.visibleInPersonal && it.personal.isNotEmpty() }
            .mapNotNull { f ->
                val head = firmaHead(f.personalVon, f.personalBis, f.name)
                val zeilen = f.personal.mapNotNull { p ->
                    val rolle = p.rolle.trim()
                    if (rolle.isBlank()) null else listOfNotNull(p.anzahl?.toString(), rolle).joinToString(" ")
                }
                if (zeilen.isEmpty()) null else BtbDruckFirmaListe(head, zeilen)
            }
        if (structured.isNotEmpty()) return structured
        return entry.personal.mapNotNull { parseLegacyPmLine(it) }
    }

    private fun buildMaschinen(entry: BautagebuchEintrag): List<BtbDruckFirmaListe> {
        val structured = entry.firmen
            .filter { !it.isDeleted && it.visibleInMaschinen && it.geraete.isNotEmpty() }
            .mapNotNull { f ->
                val head = firmaHead(f.maschinenVon, f.maschinenBis, f.name)
                val zeilen = f.geraete.mapNotNull { g ->
                    val bez = g.bezeichnung.trim()
                    if (bez.isBlank()) null else listOfNotNull(g.anzahl?.toString(), bez).joinToString(" ")
                }
                if (zeilen.isEmpty()) null else BtbDruckFirmaListe(head, zeilen)
            }
        if (structured.isNotEmpty()) return structured
        return entry.geraete.mapNotNull { parseLegacyPmLine(it) }
    }

    private fun firmaHead(von: String?, bis: String?, name: String): String {
        val zeit = listOfNotNull(von?.takeIf { it.isNotBlank() }, bis?.takeIf { it.isNotBlank() }).joinToString(" - ")
        return listOf(zeit, name.trim()).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun parseLegacyPmLine(raw: String): BtbDruckFirmaListe? {
        val t = raw.trim()
        if (t.isBlank()) return null
        val parts = t.split(" - ")
        return if (parts.size >= 3) {
            val head = parts.take(parts.size - 1).joinToString(" - ")
            val item = parts.last().trim()
            if (item.isBlank()) null else BtbDruckFirmaListe(head, listOf(item))
        } else {
            BtbDruckFirmaListe(t, emptyList()).takeIf { false }
        }
    }

    private fun stripLeadingBullets(sIn: String): String = sIn.trim().replace(Regex("""^[•\-]+\s*"""), "").trim()

    private fun looksLikeLeadingTime(sIn: String): Boolean = Regex("""^\d{1,2}:\d{2}\b""").containsMatchIn(sIn.trim())

    private fun looksLikeTimeColonSplit(prefixIn: String, tailIn: String): Boolean {
        val p = prefixIn.trim()
        val t = tailIn.trim()
        return p.matches(Regex("""\d{1,2}""")) && t.matches(Regex("""\d{2}.*"""))
    }

    private fun applyOrtMarker(lineRaw: String?, currentOrtId: Long): Pair<Long, String?> {
        val raw = lineRaw.orEmpty()
        val re = Regex("""^@ORT\s*=\s*(\d+)\s*(.*)$""", RegexOption.IGNORE_CASE)
        val m = re.find(raw.trim()) ?: return currentOrtId to raw
        val id = m.groupValues.getOrNull(1)?.toLongOrNull() ?: currentOrtId
        val rest = m.groupValues.getOrNull(2).orEmpty().trim().takeIf { it.isNotBlank() }
        return id to rest
    }

    private fun applyGeneralMarker(lineRaw: String?): Pair<Boolean, String?> {
        val raw = lineRaw.orEmpty()
        val forced = raw.contains("@ALLGEMEIN", ignoreCase = true)
        return forced to raw.replace("@ALLGEMEIN", "", ignoreCase = true).trim().takeIf { it.isNotBlank() }
    }

    private fun splitFirma(txtIn: String, forceGeneral: Boolean, firmCanonicalByLower: Map<String, String>): Pair<String?, String> {
        val txt = stripLeadingBullets(txtIn)
        if (forceGeneral) return null to txt
        if (looksLikeLeadingTime(txt)) return null to txt
        val idx = txt.indexOf(':')
        if (idx <= 0) return null to txt
        val prefix = txt.substring(0, idx).trim()
        val tail = txt.substring(idx + 1).trim()
        if (prefix.isBlank() || tail.isBlank()) return null to txt
        if (looksLikeTimeColonSplit(prefix, tail)) return null to txt
        return (firmCanonicalByLower[prefix.lowercase(Locale.ROOT)] ?: prefix) to tail
    }

    private fun parseTimeOrNull(s: String?): LocalTime? = runCatching { LocalTime.parse(s?.trim().orEmpty(), timeFmt) }.getOrNull()

    private fun normHHmm(s: String): String? = runCatching { LocalTime.parse(s.trim(), timeInFmt).format(timeFmt) }.getOrNull()

    private fun parseStartTimeAndLabel(raw: String?): Pair<LocalTime?, String?> {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null to null
        val m = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*\(?\s*(\d{1,2}:\d{2})\s*\)?$""").matchEntire(s)
        if (m != null) {
            val a = normHHmm(m.groupValues[1]) ?: return null to null
            val b = normHHmm(m.groupValues[2]) ?: return null to null
            return runCatching { LocalTime.parse(a, timeFmt) }.getOrNull() to "$a–$b"
        }
        val a = normHHmm(s) ?: return null to null
        return runCatching { LocalTime.parse(a, timeFmt) }.getOrNull() to a
    }

    private fun manualEventsFromMultiline(
        raw: String?,
        kind: Int,
        seqBase: Int,
        firmCanonicalByLower: Map<String, String>
    ): List<PrintEvent> {
        val reTime = Regex("""^([0-9]{1,2}:[0-9]{2})(?:\s*[-–]\s*\(?\s*([0-9]{1,2}:[0-9]{2})\s*\)?\s*)?\s+(.+)$""")
        var lastTime = LocalTime.MIN
        var seq = seqBase
        var ortId = 0L
        val out = ArrayList<PrintEvent>()
        raw.orEmpty().lines().forEach { rawLine ->
            val (newOrt, cleaned0) = applyOrtMarker(rawLine, ortId)
            ortId = newOrt
            val (forcedGeneral, cleaned) = applyGeneralMarker(cleaned0)
            val effectiveOrtId = if (forcedGeneral) 0L else ortId
            val kmData = de.tbhelfer.util.KmAnzeige.zerlegen(cleaned)
            val kmLabel = de.tbhelfer.util.KmAnzeige.bereich(kmData.kmVon, kmData.kmBis, inKlammern = true)
            val line = stripLeadingBullets(kmData.text.trim())
            if (line.isBlank()) return@forEach
            val m = reTime.matchEntire(line)
            if (m != null) {
                val tStartStr = m.groupValues[1]
                val tEndStr = m.groupValues.getOrNull(2).orEmpty().trim().takeIf { it.isNotBlank() }
                val rest = m.groupValues[3].trim()
                val tStart = runCatching { LocalTime.parse(tStartStr, timeInFmt) }.getOrNull()?.let { LocalTime.of(it.hour, it.minute) } ?: lastTime
                val tEnd = tEndStr?.let { runCatching { LocalTime.parse(it, timeInFmt) }.getOrNull()?.let { t -> LocalTime.of(t.hour, t.minute) } }
                lastTime = tStart
                val (firma, txt2) = splitFirma(rest, forcedGeneral, firmCanonicalByLower)
                if (txt2.isBlank()) return@forEach
                val finalOrtId = if (firma == null) 0L else effectiveOrtId
                val prefix = if (tEnd != null) "${tStart.format(timeFmt)}–${tEnd.format(timeFmt)}" else tStart.format(timeFmt)
                val display = listOfNotNull(prefix, kmLabel, txt2).joinToString(" ")
                out.add(PrintEvent(tStart, kind, seq++, finalOrtId, firma, display))
            } else {
                val (firma, txt2) = splitFirma(line, forcedGeneral, firmCanonicalByLower)
                if (txt2.isBlank()) return@forEach
                val finalOrtId = if (firma == null) 0L else effectiveOrtId
                val display = listOfNotNull(kmLabel, txt2).joinToString(" ")
                out.add(PrintEvent(lastTime, kind, seq++, finalOrtId, firma, display))
            }
        }
        return out
    }

    private fun manualEventsFromSection(
        block: SectionBlock,
        kind: Int,
        seqBase: Int,
        firmCanonicalByLower: Map<String, String>
    ): List<PrintEvent> {
        var lastTime = LocalTime.MIN
        var seq = seqBase
        var ortId = 0L
        val out = ArrayList<PrintEvent>()
        block.items.forEach { item ->
            val (newOrt, cleaned0) = applyOrtMarker(item.text, ortId)
            ortId = newOrt
            val (forcedGeneral, cleaned) = applyGeneralMarker(cleaned0)
            val effectiveOrtId = if (forcedGeneral) 0L else ortId
            val rawTxt = cleaned?.trim().orEmpty()
            if (rawTxt.isBlank()) return@forEach
            val (firm, txt2) = splitFirma(rawTxt, forcedGeneral, firmCanonicalByLower)
            if (txt2.isBlank()) return@forEach
            val finalOrtId = if (firm == null) 0L else effectiveOrtId
            val (t, label) = parseStartTimeAndLabel(item.uhrzeit)
            if (t != null) lastTime = t
            val kmLabel = de.tbhelfer.util.KmAnzeige.bereich(item.kmVon, item.kmBis, inKlammern = true)
            val line = listOfNotNull(label?.takeIf { it.isNotBlank() }, kmLabel, txt2).joinToString(" ")
            out.add(PrintEvent(t ?: lastTime, kind, seq++, finalOrtId, firm, line))
        }
        return out
    }

    private fun leistungEvents(entry: BautagebuchEintrag, seqBase: Int): List<PrintEvent> {
        var seq = seqBase
        val out = ArrayList<PrintEvent>()
        entry.firmen.filter { it.leistungen.isNotEmpty() }.forEach { firm ->
            var ortId = 0L
            firm.leistungen.forEach { lz ->
                val (newOrt, rest) = applyOrtMarker(lz.text, ortId)
                ortId = newOrt
                val txt = rest?.trim().orEmpty()
                if (txt.isBlank()) return@forEach
                val t = parseTimeOrNull(lz.uhrzeit)
                val line = if (t != null) "${t.format(timeFmt)} $txt" else txt
                out.add(PrintEvent(t ?: LocalTime.MIN, 0, seq++, ortId, firm.name.trim(), line))
            }
        }
        return out
    }

    private fun fotoEvents(
        fotos: List<FotoEntity>,
        fotoNum: Map<Long, Int>,
        textOverrides: Map<Long, String>,
        startOverrides: Map<Long, String>,
        kmOverrides: Map<Long, BtbPhotoKmOverrideStore.KmOverride>,
        kind: Int,
        seqBase: Int,
        firmCanonicalByLower: Map<String, String>
    ): List<PrintEvent> {
        data class Row(val firma: String?, val ortId: Long, val text: String, val km: Double?, val id: Long, val epochMs: Long)
        val rows = fotos.mapNotNull { f ->
            val txt = (textOverrides[f.id]?.trim().takeIf { !it.isNullOrBlank() } ?: f.kurztext.trim())
            if (txt.isBlank()) return@mapNotNull null
            val firma = f.firma.trim().takeIf { it.isNotBlank() }?.let { firmCanonicalByLower[it.lowercase(Locale.ROOT)] ?: it }
            Row(firma, f.baustellenKnotenId ?: 0L, txt, f.km, f.id, f.erstelltAmEpochMs)
        }
        var seq = seqBase
        val out = ArrayList<PrintEvent>()
        rows.groupBy { Triple(it.firma, it.ortId, it.text) }.entries.forEach { (key, list) ->
            val (firma, ortId, txt) = key
            val sorted = list.sortedBy { it.epochMs }
            if (sorted.isEmpty()) return@forEach
            val first = sorted.first()
            val last = sorted.last()
            val (minT, maxT) = effectiveRangeFor(sorted.map { it.id }, first.epochMs, last.epochMs, startOverrides)
            val nums = sorted.mapNotNull { fotoNum[it.id] }
            val manualKm = sorted.asSequence().mapNotNull { kmOverrides[it.id] }.firstOrNull()
            val kmPart = if (manualKm != null) {
                de.tbhelfer.util.KmAnzeige.bereich(manualKm.kmVon, manualKm.kmBis, inKlammern = true)
                    ?.let { " $it" }.orEmpty()
            } else {
                val kmVals = sorted.mapNotNull { it.km }
                if (kmVals.isNotEmpty()) {
                    de.tbhelfer.util.KmAnzeige.bereich(kmVals.minOrNull(), kmVals.maxOrNull(), inKlammern = true)
                        ?.let { " $it" }.orEmpty()
                } else ""
            }
            val timePart = if (minT == maxT) minT else "$minT–$maxT"
            val line = "$timePart$kmPart $txt" + bildSuffix(nums)
            val sortT = runCatching { LocalTime.parse(minT, timeFmt) }.getOrNull() ?: LocalTime.MIN
            out.add(PrintEvent(sortT, kind, seq++, ortId, firma, line))
        }
        return out.sortedWith(compareBy<PrintEvent> { it.sortTime }.thenBy { it.seq })
    }

    private fun bildSuffix(numsIn: List<Int>): String {
        if (numsIn.isEmpty()) return ""
        val nums = numsIn.distinct().sorted()
        val runs = ArrayList<Pair<Int, Int>>()
        var a = nums.first()
        var b = a
        for (n in nums.drop(1)) {
            if (n == b + 1) b = n else {
                runs.add(a to b)
                a = n
                b = n
            }
        }
        runs.add(a to b)
        return " [Bild " + runs.joinToString("+") { (x, y) -> if (x == y) "$x" else "$x–$y" } + "]"
    }

    private fun hmToMin(hm: String): Int? {
        val p = hm.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun minToHm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    private fun normalizeHm(hm: String): String? = hmToMin(hm)?.let { minToHm(it) }

    private fun parsePhotoTimeOverride(raw: String?): Pair<Int?, Int?>? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null
        normalizeHm(s)?.let { return (hmToMin(it) ?: return null) to null }
        val m = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})$""").matchEntire(s) ?: return null
        return (normalizeHm(m.groupValues[1])?.let { hmToMin(it) }) to (normalizeHm(m.groupValues[2])?.let { hmToMin(it) })
    }

    private fun wallTimeEpochNear(refEpochMs: Long, minuteOfDay: Int): Long {
        val zone = ZoneId.systemDefault()
        val ref = Instant.ofEpochMilli(refEpochMs).atZone(zone)
        val base = ref.toLocalDate()
        return listOf(-1L, 0L, 1L).map { delta ->
            base.plusDays(delta).atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()
        }.minBy { kotlin.math.abs(it - refEpochMs) }
    }

    private fun effectiveRangeFor(ids: List<Long>, autoStartEpochMs: Long, autoEndEpochMs: Long, startOverrides: Map<Long, String>): Pair<String, String> {
        val manualRanges = ids.mapNotNull { startOverrides[it] }.mapNotNull { parsePhotoTimeOverride(it) }
        val manualStartEpoch = manualRanges.mapNotNull { it.first?.let { min -> wallTimeEpochNear(autoStartEpochMs, min) } }.minOrNull()
        val manualEndEpoch = manualRanges.mapNotNull { it.second?.let { min -> wallTimeEpochNear(autoEndEpochMs, min) } }.maxOrNull()
        val startEpoch = manualStartEpoch?.let { minOf(it, autoStartEpochMs) } ?: autoStartEpochMs
        val endEpoch = manualEndEpoch?.let { maxOf(it, autoEndEpochMs) } ?: autoEndEpochMs
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(startEpoch).atZone(zone).toLocalTime().format(timeFmt) to
            Instant.ofEpochMilli(endEpoch).atZone(zone).toLocalTime().format(timeFmt)
    }
}
