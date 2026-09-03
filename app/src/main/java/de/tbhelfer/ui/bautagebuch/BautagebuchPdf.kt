package de.tbhelfer.ui.bautagebuch

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.datenbank.BaustellenKnotenEntity
import de.tbhelfer.datenbank.BaustellenKnotenTyp
import de.tbhelfer.datenbank.Bildtyp
import de.tbhelfer.datenbank.FotoRepository
import de.tbhelfer.einstellungen.BtbPdfEinstellungenSpeicher
import de.tbhelfer.util.ImageExifUtil
import de.tbhelfer.ui.kamera.FotoOptionalFelderSpeicher
import de.tbhelfer.ui.einstellungen.nutzer.NutzerSpeicher
import de.tbhelfer.ui.suche.BtbSpeicher
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.flow.first
// Keine runBlocking in UI-/IO-Workflows: PDF-Erstellung läuft ohnehin in Coroutines.

class BautagebuchPdf(
    private val context: Context,
    private val fotoRepository: FotoRepository
) {

    private val isoFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun shiftWindowEpochMs(datum: LocalDate, start: String?, ende: String?): Pair<Long, Long> {
        fun parseMinutes(s: String?): Int? {
            val t = s?.trim().orEmpty()
            val p = t.split(":")
            val h = p.getOrNull(0)?.toIntOrNull() ?: return null
            val m = p.getOrNull(1)?.toIntOrNull() ?: return null
            return h * 60 + m
        }

        val zone = ZoneId.systemDefault()
        val dayStart = datum.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextDayStart = datum.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val sMin = parseMinutes(start)
        val eMin = parseMinutes(ende)
        if (sMin == null || eMin == null) return Pair(dayStart, nextDayStart)

        // Wenn Ende < Start -> Schicht geht über Mitternacht
        val from = dayStart + sMin * 60L * 1000L
        val to = if (eMin >= sMin) {
            dayStart + eMin * 60L * 1000L
        } else {
            nextDayStart + eMin * 60L * 1000L
        }
        return Pair(from, to)
    }

    private fun parseMinutes(t: String?): Int? {
        val s = t?.trim().orEmpty()
        val p = s.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun dateRangeText(datum: LocalDate, start: String?, ende: String?): String {
        val deDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val sMin = parseMinutes(start)
        val eMin = parseMinutes(ende)
        if (sMin == null || eMin == null) return datum.format(deDate)

        val crosses = eMin <= sMin
        if (!crosses) return datum.format(deDate)

        val endDate = datum.plusDays(1)
        return if (endDate.month == datum.month && endDate.year == datum.year) {
            datum.format(DateTimeFormatter.ofPattern("dd.")) + "/" + endDate.format(deDate)
        } else {
            "${datum.format(deDate)} – ${endDate.format(deDate)}"
        }
    }

    /**
     * Dateisystem-sicherer Schicht-Key für Ordner/Dateiname.
     * - Tagesschicht: YYYY_MM_DD
     * - Nachtschicht: YYYY_MM_DD-DD (Ende-Tag als 2-stellig)
     */
    private fun schichtKey(datum: LocalDate, start: String?, ende: String?): String {
        val base = datum.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
        val sMin = parseMinutes(start)
        val eMin = parseMinutes(ende)
        if (sMin == null || eMin == null) return base
        val crosses = eMin <= sMin
        if (!crosses) return base
        val endDay = datum.plusDays(1).dayOfMonth.toString().padStart(2, '0')
        return "$base-$endDay"
    }

    private fun nextGeaendertIndex(outDir: File, safeBaustellenName: String, schichtKey: String): Int {
        // Sucht vorhandene Dateien wie: <safe>_geaendert_<key>.pdf (Legacy) oder <safe>_geaendert3_<key>.pdf
        val escSafe = Regex.escape(safeBaustellenName)
        val escKey = Regex.escape(schichtKey)
        val r = Regex("""^${escSafe}_geaendert(\d*)_${escKey}\.pdf$""", RegexOption.IGNORE_CASE)

        var maxIdx = 0
        outDir.listFiles()
            ?.filter { it.isFile }
            ?.forEach { f ->
                val mm = r.matchEntire(f.name) ?: return@forEach
                val digits = mm.groups[1]?.value.orEmpty()
                val idx = digits.toIntOrNull() ?: 1 // Legacy: ohne Zahl => 1
                if (idx > maxIdx) maxIdx = idx
            }
        return (maxIdx + 1).coerceAtLeast(1)
    }

    private fun pdfDateiname(
        baustelleName: String,
        schichtKey: String,
        geaendert: Boolean,
        geaendertIndex: Int? = null
    ): String {
        val safe = safeFolderName(baustelleName)
        return if (geaendert) {
            val idx = (geaendertIndex ?: 1).coerceAtLeast(1)
            "${safe}_geaendert${idx}_${schichtKey}.pdf"
        } else {
            "${safe}_${schichtKey}.pdf"
        }
    }

    /**
     * Dateisystem-sicherer Schicht-Key für Namen/Ordner.
     * - Tagesschicht: YYYY_MM_DD
     * - Nachtschicht (Ende <= Start): YYYY_MM_DD-DD (nur End-Tag)
     */
    private fun schichtKey(entry: BautagebuchEintrag): String {
        val datum = LocalDate.parse(entry.datumIso, isoFmt)
        val base = entry.datumIso.replace('-', '_') // YYYY_MM_DD

        val s = entry.startUhrzeit?.trim().orEmpty()
        val e = entry.endeUhrzeit?.trim().orEmpty()
        if (s.isBlank() || e.isBlank()) return base

        val start = runCatching { LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull() ?: return base
        val end = runCatching { LocalTime.parse(e, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull() ?: return base

        // Ende <= Start => über Mitternacht
        if (!end.isAfter(start)) {
            val endDay = datum.plusDays(1).dayOfMonth.toString().padStart(2, '0')
            return "$base-$endDay"
        }
        return base
    }

    suspend fun erstellenPdf(entry: BautagebuchEintrag, asDraft: Boolean = false): File {
        val datum = LocalDate.parse(entry.datumIso, isoFmt)
        val skey = schichtKey(entry)
        val safeBaustellenName = safeFolderName(entry.baustelleName)
        val pdfNameOriginal = "${safeBaustellenName}_${skey}.pdf"
        val pdfNameEntwurf = "${safeBaustellenName}_entwurf_${skey}.pdf"
        val excludeGeaendertRegex = Regex(
            """^${Regex.escape(safeBaustellenName)}_geaendert\d*_${Regex.escape(skey)}\.pdf$""",
            RegexOption.IGNORE_CASE
        )

        // Fotos in der Schicht (inkl. über Mitternacht)
        val fotosAll = if (entry.includeFotos) {
            val all = fotoRepository.fotosFuerBaustelleOnce(entry.baustelleId)
            all.let { list ->
                val (from, to) = shiftWindowEpochMs(datum, entry.startUhrzeit, entry.endeUhrzeit)
                // BTB-Regel: maßgeblich ist das Schicht-Zeitfenster.
                // Fotos können vor dem Anlegen/Öffnen des BTB aufgenommen worden sein;
                // sie müssen trotzdem auswählbar und im PDF nutzbar sein.
                list.filter { it.erstelltAmEpochMs in from until to }
            }
                .sortedBy { it.erstelltAmEpochMs }
        } else emptyList()

        // Foto-Auswahl: Default = alle Fotos in der Schicht. Persistiert werden nur abgewählte IDs (JSON).
        val excludedByType = BtbPhotoSelectionStore.loadExcluded(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        )
        val fotos = fotosAll.filter { f ->
            val ex = excludedByType[f.bildtyp.name]
            ex == null || !ex.contains(f.id)
        }

        // BTB PDFs (optional)
        val btbs: List<File> = if (entry.includeBtbs) {
            val dir = BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)
            dir.listFiles()
                ?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
                // Nicht das/die aktuelle(n) BTB-PDF(s) erneut in die Liste aufnehmen
                ?.filter { it.name != pdfNameOriginal && it.name != pdfNameEntwurf && !excludeGeaendertRegex.matches(it.name) }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } else emptyList()

        val pdf = PdfDocument()

        // --- Baseline (Original) für Änderungs-Markierung im PDF ---
        //  Wird per "Tagesabschluss + PDF" im PDF-Dialog gesetzt (bautagebuch_*_original.json).
        val baselineInfo = BautagebuchSpeicher.baselineInfo(context, entry.baustelleName, datum, entry.schicht)
        val baselineEntry: BautagebuchEintrag? = if (baselineInfo.exists) {
            BautagebuchSpeicher.ladenBaseline(context, entry.baustelleName, datum, entry.schicht)
        } else null

        data class SectionChange(
            val changed: Boolean,
            val redKeys: Set<String>,
            val removedKeys: Set<String> = emptySet(),
            val editedFromByNewKey: Map<String, String> = emptyMap()
        )

        fun wsNorm(s: String): String = s.trim().replace(Regex("\\s+"), " ")

        // Marker für Unterbaustellen im Text (wird von der UI vor dem Speichern gesetzt):
        //   "@ORT=<knotenId>"   (0 = Nur Hauptbaustelle)
        // Optional auch inline: "@ORT=123 <Text>" (Marker gilt für diese Zeile + nachfolgende)
        fun stripLeadingBullets(sIn: String): String {
            var s = sIn.trimStart()
            while (s.isNotEmpty()) {
                val c = s.first()
                if (c == '•' || c == '·' || c == '-' || c == '–' || c == '—' || c == '*' || c == '▪') {
                    s = s.drop(1).trimStart()
                    continue
                }
                break
            }
            return s
        }

        fun looksLikeLeadingTime(sIn: String): Boolean {
            val s = stripLeadingBullets(sIn).trim()
            return Regex("""^\d{1,2}:\d{2}(?:\s*[-–]\s*\(?\s*\d{1,2}:\d{2}\s*\)?)?(?:\s+.*)?$""").matches(s)
        }

        fun looksLikeTimeColonSplit(prefixIn: String, tailIn: String): Boolean {
            val prefix = prefixIn.trim()
            val tail = tailIn.trim()
            if (!Regex("""^\d{1,2}$""").matches(prefix)) return false
            return Regex(
                """^\d{2}(?:\s*[-–]\s*\(?\s*\d{1,2}:\d{2}\s*\)?)?(?:\s+.*)?$"""
            ).matches(tail)
        }

        val reOrtMarker = Regex("""^@ORT\s*=\s*(\d+)\s*(.*)$""", RegexOption.IGNORE_CASE)
        val reGeneralMarker = Regex("""^@ALLGEMEIN\b\s*(.*)$""", RegexOption.IGNORE_CASE)
        fun applyOrtMarker(lineRaw: String, currentOrtId: Long): Pair<Long, String?> {
            val s0 = stripLeadingBullets(lineRaw).trim()
            val m = reOrtMarker.matchEntire(s0)
            if (m != null) {
                val id = m.groupValues[1].toLongOrNull() ?: currentOrtId
                val rest = m.groupValues.getOrNull(2).orEmpty().trim()
                return id to rest.takeIf { it.isNotBlank() }
            }
            return currentOrtId to s0
        }

        fun applyGeneralMarker(lineRaw: String?): Pair<Boolean, String?> {
            val s0 = stripLeadingBullets(lineRaw.orEmpty()).trim()
            val m = reGeneralMarker.matchEntire(s0)
            if (m != null) {
                val rest = m.groupValues.getOrNull(1).orEmpty().trim()
                return true to rest.takeIf { it.isNotBlank() }
            }
            return false to s0.takeIf { it.isNotBlank() }
        }

        fun buildWeatherKeySet(e: BautagebuchEintrag): Set<String> {
            fun normalizeNiedForPdf(raw: String): String {
                var t = raw.trim()
                if (t.equals("trocken (kein Niederschlag)", ignoreCase = true)) t = "trocken"
                t = t.replace("(leicht)", "leicht")
                    .replace("(mäßig)", "mäßig")
                    .replace("(stark)", "stark")
                t = t.replace(Regex("\\s+"), " ").trim()
                return t
            }
            val wb = e.weather
            val slots = if (wb != null && wb.slots.isNotEmpty()) {
                wb.slots
            } else e.weatherSlots

            return slots.map { s ->
                val time = wsNorm(s.uhrzeit).take(5)
                val temp = s.tempC?.let { "${it}°C" } ?: "-"
                val bew = wsNorm(s.bewoelkung ?: "-")
                val nied = normalizeNiedForPdf(s.niederschlag ?: "-")
                "$time|$temp|$bew|$nied"
            }.toSet()
        }

        fun buildPersonalKeySet(e: BautagebuchEintrag): Set<String> {
            fun fmt(von: String?, bis: String?, name: String): String {
                val v = von?.trim().orEmpty()
                val b = bis?.trim().orEmpty()
                return when {
                    v.isNotBlank() && b.isNotBlank() -> "${v} - ${b} $name"
                    v.isNotBlank() && b.isBlank() -> "${v} - $name"
                    v.isBlank() && b.isNotBlank() -> "- ${b} $name"
                    else -> name
                }
            }
            val out = LinkedHashSet<String>()
            if (e.firmen.isNotEmpty()) {
                e.firmen
                    .filter { it.visibleInPersonal && !it.isDeleted }
                    .forEach { f ->
                        val hdr = wsNorm(fmt(f.personalVon, f.personalBis, f.name))
                        val lns = f.personal.map { p -> wsNorm("${p.anzahl ?: ""} ${p.rolle}".trim()) }.filter { it.isNotBlank() }
                        if (lns.isEmpty()) return@forEach
                        out.add("H||$hdr")
                        lns.forEach { ln -> out.add("B||$hdr||$ln") }
                    }
            } else {
                val hdr = ""
                val lns = e.personal.map { wsNorm(it) }.filter { it.isNotBlank() }
                lns.forEach { ln -> out.add("B||$hdr||$ln") }
            }
            return out
        }

        fun buildMaschinenKeySet(e: BautagebuchEintrag): Set<String> {
            fun fmt(von: String?, bis: String?, name: String): String {
                val v = von?.trim().orEmpty()
                val b = bis?.trim().orEmpty()
                return when {
                    v.isNotBlank() && b.isNotBlank() -> "${v} - ${b} $name"
                    v.isNotBlank() && b.isBlank() -> "${v} - $name"
                    v.isBlank() && b.isNotBlank() -> "- ${b} $name"
                    else -> name
                }
            }
            val out = LinkedHashSet<String>()
            if (e.firmen.isNotEmpty()) {
                e.firmen
                    .filter { it.visibleInMaschinen && !it.isDeleted }
                    .forEach { f ->
                        val hdr = wsNorm(fmt(f.maschinenVon, f.maschinenBis, f.name))
                        val lns = f.geraete.map { g -> wsNorm("${g.anzahl ?: ""} ${g.bezeichnung}".trim()) }.filter { it.isNotBlank() }
                        if (lns.isEmpty()) return@forEach
                        out.add("H||$hdr")
                        lns.forEach { ln -> out.add("B||$hdr||$ln") }
                    }
            } else {
                val hdr = ""
                val lns = e.geraete.map { wsNorm(it) }.filter { it.isNotBlank() }
                lns.forEach { ln -> out.add("B||$hdr||$ln") }
            }
            return out
        }

        fun buildArbeitenKeySet(e: BautagebuchEintrag): Set<String> {
            val out = LinkedHashSet<String>()
            fun parseTimeOrNull(s: String?): LocalTime? {
                val t = s?.trim().orEmpty()
                if (t.isBlank()) return null
                return runCatching { LocalTime.parse(t, timeFmt) }.getOrNull()
            }
            // Firmen-Leistungen
            e.firmen
                .filter { it.leistungen.isNotEmpty() }
                .forEach { firm ->
                    val firmName = wsNorm(firm.name)
                    var ortId = 0L
                    firm.leistungen.forEach { lz ->
                        val (newOrt, rest) = applyOrtMarker(lz.text, ortId)
                        ortId = newOrt
                        val txt = wsNorm(rest ?: "")
                        if (txt.isBlank()) return@forEach
                        val t = parseTimeOrNull(lz.uhrzeit)
                        val line = if (t != null) "${t.format(timeFmt)} $txt" else txt
                        out.add("$firmName||$ortId||$line")
                    }
                }

            // Manuelle Notizen (multiline)
            val reTime = Regex("""^([0-9]{1,2}:[0-9]{2})(?:\s*[-–]\s*\(?\s*([0-9]{1,2}:[0-9]{2})\s*\)?\s*)?\s+(.+)$""")
            var lastTime: LocalTime = LocalTime.MIN
            var ortId: Long = 0L
            val firmCanonicalByLower = e.firmen
                .map { wsNorm(it.name) }
                .filter { it.isNotBlank() }
                .associateBy { it.lowercase(Locale.ROOT) }

            fun splitFirma(txt0: String, forceGeneral: Boolean = false): Pair<String?, String> {
                val txt = wsNorm(txt0)
                if (forceGeneral) return null to txt
                if (looksLikeLeadingTime(txt)) return null to txt
                val idxC = txt.indexOf(':')
                if (idxC <= 0) return null to txt
                val prefix = wsNorm(txt.substring(0, idxC))
                val tail = wsNorm(txt.substring(idxC + 1))
                if (prefix.isBlank() || tail.isBlank()) return null to txt
                if (looksLikeTimeColonSplit(prefix, tail)) return null to txt
                val canon = firmCanonicalByLower[prefix.lowercase(Locale.ROOT)] ?: prefix
                return canon to tail
            }

            e.notizen.orEmpty()
                .lines()
                .forEach { rawLine ->
                    val (newOrt, cleaned0) = applyOrtMarker(rawLine, ortId)
                    ortId = newOrt
                    val (forcedGeneral, cleaned) = applyGeneralMarker(cleaned0)
                    val effectiveOrtId = if (forcedGeneral) 0L else ortId
                    val kmData = de.tbhelfer.util.KmAnzeige.zerlegen(cleaned)
                    val kmLabel = de.tbhelfer.util.KmAnzeige.bereich(kmData.kmVon, kmData.kmBis, inKlammern = true)
                    val line0 = stripLeadingBullets(kmData.text.trim())
                    if (line0.isBlank()) return@forEach

                    val m = reTime.matchEntire(line0)
                    if (m != null) {
                        val tStartStr = m.groupValues[1]
                        val tEndStr = m.groupValues.getOrNull(2).orEmpty().trim().takeIf { it.isNotBlank() }
                        val rest = m.groupValues[3].trim()

                        val tStart = runCatching { LocalTime.parse(tStartStr, DateTimeFormatter.ofPattern("H:mm")) }
                            .getOrNull()?.let { LocalTime.of(it.hour, it.minute) } ?: lastTime
                        val tEnd = tEndStr?.let {
                            runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("H:mm")) }
                                .getOrNull()?.let { t -> LocalTime.of(t.hour, t.minute) }
                        }

                        lastTime = tStart

                        val (firma, txt2) = splitFirma(rest, forceGeneral = forcedGeneral)
                        if (txt2.isBlank()) return@forEach
                        val finalOrtId = if (firma == null) 0L else effectiveOrtId

                        val prefix = if (tEnd != null) "${tStart.format(timeFmt)}–${tEnd.format(timeFmt)}" else tStart.format(timeFmt)
                        val display = wsNorm(listOfNotNull(prefix, kmLabel, txt2).joinToString(" "))
                        out.add("${firma ?: ""}||$finalOrtId||$display")
                    } else {
                        val (firma, txt2) = splitFirma(line0, forceGeneral = forcedGeneral)
                        if (txt2.isBlank()) return@forEach
                        val finalOrtId = if (firma == null) 0L else effectiveOrtId
                        val display = wsNorm(listOfNotNull(kmLabel, txt2).joinToString(" "))
                        out.add("${firma ?: ""}||$finalOrtId||$display")
                    }
                }

            return out
        }

        fun buildStandardSectionKeySet(e: BautagebuchEintrag, block: SectionBlock): Set<String> {
            val out = LinkedHashSet<String>()
            val timeIn = DateTimeFormatter.ofPattern("H:mm")
            fun normHHmm(s: String): String? = runCatching { LocalTime.parse(s.trim(), timeIn).format(timeFmt) }.getOrNull()

            fun parseStartLabel(raw: String?): String? {
                val s = raw?.trim().orEmpty()
                if (s.isBlank()) return null
                val m = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*\(?\s*(\d{1,2}:\d{2})\s*\)?$""").matchEntire(s)
                if (m != null) {
                    val a = normHHmm(m.groupValues[1]) ?: return null
                    val b = normHHmm(m.groupValues[2]) ?: return null
                    return "$a–$b"
                }
                return normHHmm(s)
            }

            val firmCanonicalByLower = e.firmen
                .map { wsNorm(it.name) }
                .filter { it.isNotBlank() }
                .associateBy { it.lowercase(Locale.ROOT) }
            fun splitFirma(txt: String, forceGeneral: Boolean = false): Pair<String?, String> {
                val normalized = wsNorm(txt)
                if (forceGeneral) return null to normalized
                if (looksLikeLeadingTime(normalized)) return null to normalized
                val idx = normalized.indexOf(':')
                if (idx <= 0) return null to normalized
                val prefix = wsNorm(normalized.substring(0, idx))
                val tail = wsNorm(normalized.substring(idx + 1))
                if (prefix.isBlank() || tail.isBlank()) return null to normalized
                if (looksLikeTimeColonSplit(prefix, tail)) return null to normalized
                val canon = firmCanonicalByLower[prefix.lowercase(Locale.ROOT)] ?: prefix
                return canon to tail
            }

            var ortId: Long = 0L
            block.items.forEach { it2 ->
                val (newOrt, cleaned0) = applyOrtMarker(it2.text, ortId)
                ortId = newOrt
                val (forcedGeneral, cleaned) = applyGeneralMarker(cleaned0)
                val effectiveOrtId = if (forcedGeneral) 0L else ortId
                val rawTxt = cleaned?.trim().orEmpty()
                if (rawTxt.isBlank()) return@forEach

                val (firm, txt2) = splitFirma(rawTxt, forceGeneral = forcedGeneral)
                if (txt2.isBlank()) return@forEach
                val label = parseStartLabel(it2.uhrzeit)
                val line = if (!label.isNullOrBlank()) wsNorm("$label $txt2") else wsNorm(txt2)
                val finalOrtId = if (firm == null) 0L else effectiveOrtId
                out.add("${firm ?: ""}||$finalOrtId||$line")
            }
            return out
        }

        fun computeChange(cur: Set<String>, base: Set<String>): SectionChange {
            val added = (cur - base).toMutableSet()
            val removed = (base - cur).toMutableSet()
            if (added.isEmpty() && removed.isEmpty()) return SectionChange(changed = false, redKeys = emptySet())

            fun lineBucket(key: String): String {
                if (key.startsWith("H||")) return key
                if (key.startsWith("B||")) {
                    val p = key.split("||", limit = 3)
                    val hdr = p.getOrNull(1).orEmpty()
                    val line = p.getOrNull(2).orEmpty()
                    val prefix = Regex("""^(\d{2}:\d{2}(?:–\d{2}:\d{2})?)\b""").find(line)?.groupValues?.getOrNull(1).orEmpty()
                    return "B||$hdr||$prefix"
                }
                val parts = key.split("||", limit = 3)
                if (parts.size >= 3) {
                    val prefix = Regex("""^(\d{2}:\d{2}(?:–\d{2}:\d{2})?)\b""").find(parts[2])?.groupValues?.getOrNull(1).orEmpty()
                    return parts[0] + "||" + parts[1] + "||" + prefix
                }
                return key
            }

            fun lineBody(key: String): String {
                val raw = key.substringAfterLast("||", key)
                return raw.replace(Regex("""^(\d{2}:\d{2}(?:–\d{2}:\d{2})?)\s+"""), "").trim()
            }
            fun scopeKey(key: String): String {
                val parts = key.split("||", limit = 3)
                return if (parts.size >= 3) parts[0] + "||" + parts[1] else lineBucket(key)
            }
            fun similarityScore(a: String, b: String): Int {
                if (a.isBlank() || b.isBlank()) return 0
                if (a == b) return 10_000
                var prefix = 0
                val max = minOf(a.length, b.length)
                while (prefix < max && a[prefix] == b[prefix]) prefix++
                if (a.contains(b) || b.contains(a)) return 5_000 + minOf(a.length, b.length)
                return prefix
            }

            val removedByBucket = linkedMapOf<String, MutableList<String>>()
            removed.forEach { removedByBucket.getOrPut(lineBucket(it)) { mutableListOf() }.add(it) }

            val editedFromByNewKey = linkedMapOf<String, String>()
            val matchedRemoved = mutableSetOf<String>()
            added.toList().forEach { addKey ->
                val bucketCandidates = removedByBucket[lineBucket(addKey)].orEmpty().filter { it !in matchedRemoved }
                var match = bucketCandidates.firstOrNull()
                if (match == null) {
                    val scoped = removed
                        .asSequence()
                        .filter { it !in matchedRemoved }
                        .filter { scopeKey(it) == scopeKey(addKey) }
                        .map { it to similarityScore(lineBody(it), lineBody(addKey)) }
                        .filter { it.second >= 4 }
                        .maxByOrNull { it.second }
                    match = scoped?.first
                }
                if (match != null) {
                    matchedRemoved += match
                    editedFromByNewKey[addKey] = match
                }
            }

            val actualRemoved = removed.filter { it !in matchedRemoved }.toSet()
            return SectionChange(
                changed = true,
                redKeys = added,
                removedKeys = actualRemoved,
                editedFromByNewKey = editedFromByNewKey
            )
        }

        val weatherChange = baselineEntry?.let { computeChange(buildWeatherKeySet(entry), buildWeatherKeySet(it)) }
            ?: SectionChange(changed = false, redKeys = emptySet())
        val personalChange = baselineEntry?.let { computeChange(buildPersonalKeySet(entry), buildPersonalKeySet(it)) }
            ?: SectionChange(changed = false, redKeys = emptySet())
        val maschinenChange = baselineEntry?.let { computeChange(buildMaschinenKeySet(entry), buildMaschinenKeySet(it)) }
            ?: SectionChange(changed = false, redKeys = emptySet())
        val arbeitenChange = baselineEntry?.let { computeChange(buildArbeitenKeySet(entry), buildArbeitenKeySet(it)) }
            ?: SectionChange(changed = false, redKeys = emptySet())
        val abnahmenChange = baselineEntry?.let { computeChange(buildStandardSectionKeySet(entry, entry.abnahmen), buildStandardSectionKeySet(it, it.abnahmen)) }
            ?: SectionChange(changed = false, redKeys = emptySet())
        val einweisungChange = baselineEntry?.let { computeChange(buildStandardSectionKeySet(entry, entry.einweisungUnterweisung), buildStandardSectionKeySet(it, it.einweisungUnterweisung)) }
            ?: SectionChange(changed = false, redKeys = emptySet())
        val besonderheitenChange = baselineEntry?.let { computeChange(buildStandardSectionKeySet(entry, entry.besonderheiten), buildStandardSectionKeySet(it, it.besonderheiten)) }
            ?: SectionChange(changed = false, redKeys = emptySet())
        val maengelChange = baselineEntry?.let { computeChange(buildStandardSectionKeySet(entry, entry.maengelSchaeden), buildStandardSectionKeySet(it, it.maengelSchaeden)) }
            ?: SectionChange(changed = false, redKeys = emptySet())

        // ---- PDF Layout (A4 Hochformat) ----
        val pageW = 595
        val pageH = 842
        val marginL = 40f
        val marginR = 40f
        val marginTop = 44f
        val marginBottom = 52f
        val contentW = pageW - marginL - marginR
        val boxGap = 10f  // einheitlicher Abstand zwischen Rahmen

        val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val pBaustelleHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15.4f
            typeface = Typeface.DEFAULT_BOLD
            isUnderlineText = true
        }
        val pH = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13.2f; typeface = Typeface.DEFAULT_BOLD }
        val pT = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.0f }
        val pSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.2f; typeface = Typeface.DEFAULT_BOLD }
        val pTCompact = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.0f }
        val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.0f; color = Color.DKGRAY }
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 1f }
        val pBox = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 245, 245); style = Paint.Style.FILL }
        val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f }

        // Farben für Änderungs-Markierung (Rot)
        val pHRed = Paint(pH).apply { color = Color.RED }
        val pTRed = Paint(pT).apply { color = Color.RED }
        val pTCompactRed = Paint(pTCompact).apply { color = Color.RED }
        val pSmallRed = Paint(pSmall).apply { color = Color.RED }
        val pSubRed = Paint(pSub).apply { color = Color.RED }
        // Kind-Unterbaustellen: etwas kleiner und klarer eingerückt als der Elternbereich.
        val pSubChild = Paint(pSub).apply { textSize = 9.8f }
        val pSubChildRed = Paint(pSubChild).apply { color = Color.RED }

        // Draft label
        val pDraftLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.0f; typeface = Typeface.DEFAULT_BOLD; color = Color.DKGRAY }

        // Colors for removed-marking (gray)
        val pTGrey = Paint(pT).apply { color = Color.DKGRAY }
        val pTCompactGrey = Paint(pTCompact).apply { color = Color.DKGRAY }

        fun timeOfFoto(f: de.tbhelfer.datenbank.FotoEntity): String {
            return runCatching {
                val dt = java.time.Instant.ofEpochMilli(f.erstelltAmEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                dt.format(timeFmt)
            }.getOrDefault("--:--")
        }
        fun kmText(f: de.tbhelfer.datenbank.FotoEntity): String {
            val km = f.km ?: return "KM –"
            return "KM ${de.tbhelfer.util.KmAnzeige.wert(km)}"
        }

        fun firstNonBlank(vararg s: String?): String {
            return s.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        }

        // BTB/PDF: Foto-Text-Overrides (nur fürs PDF, Foto-Original bleibt unverändert)
        val textOverrides = BtbPhotoTextOverrideStore.loadOverrides(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        )
        val startOverrides = BtbPhotoStartOverrideStore.loadOverrides(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        )
        val kmOverrides = BtbPhotoKmOverrideStore.loadOverrides(
            context,
            entry.baustelleId,
            entry.datumIso,
            entry.startUhrzeit,
            entry.endeUhrzeit,
            entry.schicht
        )
        fun hmToMin(hm: String): Int? {
            val p = hm.split(":")
            if (p.size != 2) return null
            val h = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }
        fun minToHm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
        fun normalizeHm(hm: String): String? {
            val min = hmToMin(hm) ?: return null
            return minToHm(min)
        }
        fun parsePhotoTimeOverride(raw: String?): Pair<Int?, Int?>? {
            val s = raw?.trim().orEmpty()
            if (s.isBlank()) return null
            normalizeHm(s)?.let { one ->
                val m = hmToMin(one) ?: return null
                return m to null
            }
            val match = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})$""").matchEntire(s)
                ?: return null
            val start = normalizeHm(match.groupValues[1])?.let { hmToMin(it) } ?: return null
            val end = normalizeHm(match.groupValues[2])?.let { hmToMin(it) } ?: return null
            return start to end
        }
        fun wallTimeEpochNear(refEpochMs: Long, minuteOfDay: Int): Long {
            val zone = ZoneId.systemDefault()
            val ref = java.time.Instant.ofEpochMilli(refEpochMs).atZone(zone)
            val base = ref.toLocalDate()
            return listOf(-1L, 0L, 1L)
                .map { delta ->
                    base.plusDays(delta)
                        .atStartOfDay(zone)
                        .plusMinutes(minuteOfDay.toLong())
                        .toInstant()
                        .toEpochMilli()
                }
                .minBy { kotlin.math.abs(it - refEpochMs) }
        }
        // BTB-Regel: Override (falls gesetzt) -> sonst notiz -> sonst kurztext
        fun btbTextOfFoto(f: de.tbhelfer.datenbank.FotoEntity): String {
            val ov = textOverrides[f.id]?.trim().orEmpty()
            if (ov.isNotBlank()) return ov
            return f.kurztext.trim()
        }

        fun effectiveRangeFor(ids: List<Long>, autoStartEpochMs: Long, autoEndEpochMs: Long): Pair<String, String> {
            val manualRanges = ids.asSequence()
                .mapNotNull { startOverrides[it] }
                .mapNotNull { parsePhotoTimeOverride(it) }
                .toList()
            val manualStartEpoch = manualRanges.mapNotNull { it.first?.let { min -> wallTimeEpochNear(autoStartEpochMs, min) } }.minOrNull()
            val manualEndEpoch = manualRanges.mapNotNull { it.second?.let { min -> wallTimeEpochNear(autoEndEpochMs, min) } }.maxOrNull()
            val startEpoch = manualStartEpoch?.let { minOf(it, autoStartEpochMs) } ?: autoStartEpochMs
            val endEpoch = manualEndEpoch?.let { maxOf(it, autoEndEpochMs) } ?: autoEndEpochMs
            val start = java.time.Instant.ofEpochMilli(startEpoch).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
            val end = java.time.Instant.ofEpochMilli(endEpoch).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
            return start to end
        }

        fun groupFotoLinesForBtb(fotos: List<de.tbhelfer.datenbank.FotoEntity>): List<String> {
            data class Row(val time: String, val text: String, val id: Long, val epochMs: Long)

            val rows = fotos.mapNotNull { f ->
                val txt = btbTextOfFoto(f).trim()
                if (txt.isBlank()) null else Row(timeOfFoto(f), txt, f.id, f.erstelltAmEpochMs)
            }

            return rows
                .groupBy { it.text }
                .entries
                .map { (txt, list) ->
                    val sorted = list.sortedBy { it.epochMs }
                    val (minT, maxT) = effectiveRangeFor(sorted.map { it.id }, sorted.first().epochMs, sorted.last().epochMs)
                    if (minT == maxT) "$minT $txt" else "$minT–$maxT $txt"
                }
                .sortedBy { it.take(5) }
        }

        fun pdfDisplayLabel(raw: String): String {
            return raw.trim()
                .removePrefix("—")
                .trim()
                .replace(Regex("""BKK\s*gr\s*\.?\s*(\d+)""", RegexOption.IGNORE_CASE)) { m -> "BKK gr.${m.groupValues[1]}" }
            .replace(Regex("""(KS\s*\d+(?:\.\d+)*)(?:\.)?\s*(Anbindung)""", RegexOption.IGNORE_CASE)) { m -> "${m.groupValues[1].replace(" ", "")} ${m.groupValues[2]}" }
                .replace(Regex("""\s*–\s*"""), " – ")
                .replace(Regex("""[ \t]{2,}"""), " ")
                .trim()
        }

        class Writer {
            var pageNo = 1
            private var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            var canvas: Canvas = page.canvas
            var y = marginTop
            private var activeFrameTitle: String? = null
            private var activeFrameTop: Float? = null

            private fun footer() {
                val footerLeft = "${entry.baustelleName}  •  ${dateRangeText(datum, entry.startUhrzeit, entry.endeUhrzeit)}"
                canvas.drawText(footerLeft.take(70), marginL, (pageH - 22).toFloat(), pSmall)
                val footerRight = "Seite $pageNo"
                val w = pSmall.measureText(footerRight)
                canvas.drawText(footerRight, (pageW - marginR - w), (pageH - 22).toFloat(), pSmall)
            }

            fun newPage() {
                closeActiveFrameAt(pageH - marginBottom)
                footer()
                pdf.finishPage(page)
                pageNo += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                canvas = page.canvas
                y = marginTop
                activeFrameTitle?.let { title ->
                    beginSectionFrame(title, continuation = true)
                }
            }

            private fun closeActiveFrameAt(bottomY: Float) {
                val top = activeFrameTop ?: return
                val bottom = bottomY.coerceAtLeast(top + 24f).coerceAtMost(pageH - marginBottom)
                canvas.drawRect(marginL, top, pageW - marginR, bottom, pBorder)
            }

            fun beginSectionFrame(title: String, continuation: Boolean = false) {
                activeFrameTitle = title
                activeFrameTop = y - 15f
                if (continuation) {
                    boxTitle("$title (Fortsetzung)")
                    y += 4f
                    activeFrameTop = (activeFrameTop ?: marginTop)
                }
            }

            fun endSectionFrame() {
                val bottom = (y + 6f).coerceAtMost(pageH - marginBottom)
                closeActiveFrameAt(bottom)
                activeFrameTitle = null
                activeFrameTop = null
                y = bottom + 4f
            }

            fun ensure(space: Float) {
                if (y + space > pageH - marginBottom) newPage()
            }

            fun hr() {
                ensure(16f)
                canvas.drawLine(marginL, y, (pageW - marginR), y, pLine)
                y += 14f
            }

            fun textWrapped(text: String, paint: Paint, indent: Float = 0f, extraLineGap: Float = 0f) {
                val x = marginL + indent
                val maxW = contentW - indent
                val lineH = (paint.textSize * 1.22f) + extraLineGap

                val paragraphs = text.split("\n")
                paragraphs.forEachIndexed { idx, para ->
                    val words = para.trim().split(Regex("\\s+"))
                    var line = ""
                    words.forEach { w ->
                        if (w.isBlank()) return@forEach
                        val test = if (line.isEmpty()) w else "$line $w"
                        if (paint.measureText(test) <= maxW) {
                            line = test
                        } else {
                            ensure(lineH)
                            canvas.drawText(line, x, y, paint)
                            y += lineH
                            line = w
                        }
                    }
                    if (line.isNotEmpty()) {
                        ensure(lineH)
                        canvas.drawText(line, x, y, paint)
                        y += lineH
                    }
                    if (idx < paragraphs.lastIndex) y += (lineH * 0.12f)
                }
            }

            fun heading(text: String) {
                ensure(26f)
                canvas.drawText(text, marginL, y, pTitle)
                y += 24f
            }

            fun boxTitle(text: String, titlePaint: Paint = pH) {
                val cleanText = pdfDisplayLabel(text)
                ensure(26f)
                val rect = RectF(marginL, y - 15f, (pageW - marginR), y + 8f)
                canvas.drawRoundRect(rect, 6f, 6f, pBox)
                canvas.drawText(cleanText, marginL + 8f, y, titlePaint)
                y += 18f
            }

            fun subBoxTitle(text: String, titlePaint: Paint = pSub, indent: Float = 0f) {
                val cleanText = pdfDisplayLabel(text)
                ensure(26f)
                val x0 = marginL + indent
                val rect = RectF(x0, y - 15f, (pageW - marginR), y + 8f)
                canvas.drawRoundRect(rect, 5f, 5f, pBox)
                canvas.drawText(cleanText, x0 + 8f, y, titlePaint)
                y += 18f
            }

            fun bulletMain(line: String) {
                ensure(18f)
                canvas.drawText("•", marginL, y, pT)
                textWrapped(line, pT, indent = 14f)
            }

            fun bulletWrapped(line: String, indent: Float, paint: Paint = pT) {
                if (line.isBlank()) return
                // Bullet auf der ersten Zeile, Text eingerückt und umbrochen
                ensure((paint.textSize * 1.22f) + 6f)
                canvas.drawText("•", marginL + indent, y, paint)
                textWrapped(line, paint, indent = indent + 14f)
            }

            fun bulletSub(line: String) {
                // Dateiname/Details: kleiner & eingerückt
                if (line.isBlank()) return
                ensure(16f)
                canvas.drawText("↳", marginL + 14f, y, pSmall)
                textWrapped(line, pSmall, indent = 28f)
            }

            fun finish() {
                footer()
                pdf.finishPage(page)
            }
        }

        val w = Writer()

        fun keepWithNextRows(minHeight: Float) {
            // Überschrift/Firma/Unterbaustelle nicht allein am Seitenende stehen lassen.
            w.ensure(minHeight)
        }

        fun renderRemovedBlock(change: SectionChange, decode: (String) -> String) {
            val removed = change.removedKeys
                .map { decode(it).trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            if (removed.isEmpty()) return

            w.y += 2f
            w.textWrapped("Änderungen (entfernt):", pSmall, indent = 8f)
            removed.forEach { ln ->
                w.bulletWrapped("(entfernt) $ln", indent = 8f, paint = pTGrey)
            }
            w.y += 4f
        }


        // --- Kopf (Nutzer + Logo) ---
        val profil = NutzerSpeicher.laden(context)
        val nutzerDir = NutzerSpeicher.ordner(context)
        val logoFile = profil?.logoDateiname?.let { File(nutzerDir, it) }?.takeIf { it.exists() }

        w.heading("BAUTAGEBUCH")

        if (asDraft) {
            w.textWrapped("ENTWURF (ohne Tagesabschluss)", pDraftLabel)
        }

        var logoBottom = w.y
        var logoW = 0f
        val logoGap = 12f

        // Logo rechts oben (wenn vorhanden) – groß wie im Mock
        if (logoFile != null) {
            val bmp = BitmapFactory.decodeFile(logoFile.absolutePath)
            if (bmp != null) {
                val targetW = 180
                val scale = targetW.toFloat() / bmp.width.toFloat()
                val targetH = (bmp.height * scale).toInt().coerceAtLeast(1).coerceAtMost(120)
                val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)

                val x = (pageW - marginR - targetW).toFloat()
                val yLogo = (marginTop - 6f).coerceAtLeast(10f)
                w.canvas.drawBitmap(scaled, x, yLogo, null)

                logoBottom = yLogo + targetH
                logoW = targetW.toFloat()
            }
        }

        // --- Baustellenbaum / Unterbaustellen für PDF ---
        val baumRepo = ServiceLocator.baustellenBaumRepository
        val rootKnoten = runCatching { baumRepo.hauptNachAltBaustelleId(entry.baustelleId) }.getOrNull()
        val rootId = rootKnoten?.id
        val alleKnoten: List<BaustellenKnotenEntity> = if (rootId != null) {
            runCatching { baumRepo.alleKnotenZuRoot(rootId).first() }.getOrDefault(emptyList())
        } else emptyList()

        fun normalizeDisplayText(raw: String): String {
            return raw.trim()
                .replace(Regex("""BKK\s*gr\s*\.?\s*(\d+)""", RegexOption.IGNORE_CASE)) { m -> "BKK gr.${m.groupValues[1]}" }
                .replace(Regex("""(KS\s*\d+(?:\.\d+)*)(?:\.)?\s*(Anbindung)""", RegexOption.IGNORE_CASE)) { m -> "${m.groupValues[1].replace(" ", "")} ${m.groupValues[2]}" }
                .replace(Regex("""\s*–\s*"""), " – ")
                .replace(Regex("""[ \t]{2,}"""), " ")
                .trim()
        }

        fun formatKnotenLabel(k: BaustellenKnotenEntity): String {
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
                BaustellenKnotenTyp.ALLGEMEIN_OBJEKT -> {
                    k.knotenName?.trim().takeIf { !it.isNullOrBlank() } ?: "Allgemein / Objekt"
                }
                BaustellenKnotenTyp.HAUPT -> entry.baustelleName
            }

            val km = de.tbhelfer.util.KmAnzeige.bereich(k.kmVon, k.kmBis, inKlammern = true)
                ?.let { " $it" }.orEmpty()
            return normalizeDisplayText(base + km)
        }

        fun sortKeyForNode(k: BaustellenKnotenEntity): Triple<Double, String, Long> {
            val km = k.kmVon ?: Double.MAX_VALUE
            val name = (k.knotenName ?: k.bahnhofName ?: k.streckenkennung ?: "").trim().lowercase()
            return Triple(km, name, k.erstelltAmEpochMs)
        }

        fun normalizeOrtAnzeigeName(raw: String): String {
            val cleaned = raw.trim().removePrefix("—").trim()
            return when {
                cleaned.isBlank() -> "Hauptbaustelle"
                cleaned.equals("Nur Hauptbaustelle", ignoreCase = true) -> "Hauptbaustelle"
                cleaned.endsWith("Nur Hauptbaustelle", ignoreCase = true) -> "Hauptbaustelle"
                else -> normalizeDisplayText(cleaned)
            }
        }

        val childrenByParent = alleKnoten
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }

        val orderedKnotenWithDepth = ArrayList<Pair<BaustellenKnotenEntity, Int>>()
        fun dfs(parentId: Long, depth: Int) {
            val kids = childrenByParent[parentId].orEmpty()
                .sortedWith(compareBy<BaustellenKnotenEntity> { sortKeyForNode(it).first }
                    .thenBy { sortKeyForNode(it).second }
                    .thenBy { sortKeyForNode(it).third })
            for (k in kids) {
                orderedKnotenWithDepth.add(k to depth)
                dfs(k.id, depth + 1)
            }
        }
        if (rootId != null) dfs(rootId, 0)

        val ortLabelById: Map<Long, String> = LinkedHashMap<Long, String>().apply {
            put(0L, "Hauptbaustelle")
            if (rootId != null) put(rootId, "Hauptbaustelle")
            orderedKnotenWithDepth.forEach { (k, _) -> put(k.id, normalizeOrtAnzeigeName(formatKnotenLabel(k))) }
        }

        val ortOrderById: Map<Long, Int> = HashMap<Long, Int>().apply {
            put(0L, 0)
            if (rootId != null) put(rootId, 0)
            orderedKnotenWithDepth.forEachIndexed { idx, (k, _) -> put(k.id, idx + 1) }
        }

        val ortDepthById: Map<Long, Int> = HashMap<Long, Int>().apply {
            put(0L, 0)
            if (rootId != null) put(rootId, 0)
            orderedKnotenWithDepth.forEach { (k, depth) -> put(k.id, depth.coerceAtLeast(0)) }
        }

        val ortParentById: Map<Long, Long?> = HashMap<Long, Long?>().apply {
            put(0L, null)
            if (rootId != null) put(rootId, null)
            orderedKnotenWithDepth.forEach { (k, depth) ->
                put(k.id, k.parentId?.takeIf { depth > 0 && it != rootId })
            }
        }

        fun ortParentLabel(oid: Long): String? = ortParentById[oid]
            ?.let { parentId -> ortLabelById[parentId] }
            ?.takeIf { it.isNotBlank() && !it.equals("Hauptbaustelle", ignoreCase = true) }

        val datumAnzeige = dateRangeText(datum, entry.startUhrzeit, entry.endeUhrzeit)
        w.textWrapped(entry.baustelleName, pBaustelleHeader)
        w.textWrapped("Datum: $datumAnzeige", pT)
        w.textWrapped("Zeit: ${entry.startUhrzeit ?: "-"} – ${entry.endeUhrzeit ?: "-"}", pT)

        // etwas Luft vor Wetter/Boxen
        w.y += 6f

        val byTypeAllSelected = fotos.groupBy { it.bildtyp }

        // --- Foto-Anhang Auswahl + Bildnummern (für "Bild 1–3" bzw. "Bild 1+3" in den Abschnitten) ---
        // Reihenfolge im Anhang (und damit auch für die Bildnummern):
        val anhangOrder = listOf(
            Bildtyp.ABNAHMEN,
            Bildtyp.EINWEISUNG_UNTERWEISUNG,
            Bildtyp.BESONDERHEITEN,
            Bildtyp.MAENGEL_SCHAEDEN,
            Bildtyp.FOTODOKUMENTATION
        )

        // Fotos sind hier bereits nach Schicht + Exclude-Store gefiltert (siehe oben).
        val fotosForAppendix = anhangOrder
            .flatMap { typ -> byTypeAllSelected[typ].orEmpty().sortedBy { it.erstelltAmEpochMs } }
            .take(entry.maxFotosInPdf.coerceAtLeast(1))

        val selectedFotosOrdered = fotosForAppendix
        val selectedFotoIds = selectedFotosOrdered.map { it.id }.toSet()

        val fotoNum = LinkedHashMap<Long, Int>().apply {
            var n = 1
            selectedFotosOrdered.forEach { f ->
                this[f.id] = n
                n += 1
            }
        }

        // Ab hier ist die PDF-/BTB-Liste die führende Liste:
        // Haupttext und Anhang verwenden dieselben Fotos, dieselben Nummern und dieselbe Reihenfolge.
        val byType = selectedFotosOrdered.groupBy { it.bildtyp }

        // ortLabelById / ortOrderById sind oben aus dem Baustellenbaum gebaut.


// --- BTB Inhalte (1:1) ---
fun renderWeather() {
    // Wetter als kompakte Box: gleiche Breite wie eine Spalte (wie Personal)
    val gap = 12f
    val desiredW = (contentW - gap) / 2f
    val availW = if (logoW > 0f && w.y < logoBottom) (contentW - logoW - logoGap) else contentW
    var boxW = desiredW.coerceAtMost(availW)
    val x0 = marginL
    val pad = 6f

    fun wrapAt(text: String, paint: Paint, x: Float, maxW: Float) {
        val lineH = paint.textSize * 1.05f
        val paragraphs = text.split("\n")
        for (para in paragraphs) {
            val words = para.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            var line = ""
            for (wrd in words) {
                val test = if (line.isEmpty()) wrd else "$line $wrd"
                if (paint.measureText(test) <= maxW) {
                    line = test
                } else {
                    w.ensure(lineH)
                    w.canvas.drawText(line, x, w.y, paint)
                    w.y += lineH
                    line = wrd
                }
            }
            if (line.isNotEmpty()) {
                w.ensure(lineH)
                w.canvas.drawText(line, x, w.y, paint)
                w.y += lineH
            }
            w.y += lineH * 0.12f
        }
    }

    fun ellipsize(text: String, paint: Paint, maxW: Float): String {
        var s = text
        if (paint.measureText(s) <= maxW) return s
        val ell = "…"
        var hi = s.length
        var lo = 0
        // Binary search for max length that fits
        while (lo < hi) {
            val mid = (lo + hi) / 2
            val cand = s.take(mid).trimEnd() + ell
            if (paint.measureText(cand) <= maxW) lo = mid + 1 else hi = mid
        }
        val k = max(0, lo - 1)
        return s.take(k).trimEnd() + ell
    }

    fun normalizeNiedForPdf(raw: String): String {
        var t = raw.trim()
        if (t.equals("trocken (kein Niederschlag)", ignoreCase = true)) t = "trocken"
        // Klammern kürzen (passt besser in die Tabelle)
        t = t.replace("(leicht)", "leicht")
            .replace("(mäßig)", "mäßig")
            .replace("(stark)", "stark")
        // Mehrfach-Spaces raus
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    fun drawCell(text: String, x: Float, y: Float, paint: Paint, maxW: Float) {
        w.canvas.drawText(ellipsize(text, paint, maxW), x, y, paint)
    }

    fun drawCellFitNoEllipsize(text: String, x: Float, y: Float, basePaint: Paint, maxW: Float, minSize: Float = 7.0f) {
        val t = text
        if (t.isBlank()) {
            w.canvas.drawText(t, x, y, basePaint)
            return
        }
        // Text passt? dann normal zeichnen
        if (basePaint.measureText(t) <= maxW) {
            w.canvas.drawText(t, x, y, basePaint)
            return
        }
        // Sonst: nur diese Zelle kleiner machen (Auto-Fit)
        val p = Paint(basePaint)
        val orig = p.textSize
        var wTxt = p.measureText(t)
        // proportional skalieren
        var newSize = (orig * (maxW / wTxt)).coerceAtLeast(minSize)
        p.textSize = newSize
        var guard = 0
        while (p.measureText(t) > maxW && p.textSize > minSize && guard < 10) {
            p.textSize -= 0.2f
            guard += 1
        }
        w.canvas.drawText(t, x, y, p)
    }

    // Wetterdaten vorbereiten (für Auto-Breite bei sehr langem Niederschlag)
    val wb = entry.weather
    val slots = if (wb != null && wb.slots.isNotEmpty()) {
        wb.slots.map { s ->
            Triple(
                s.uhrzeit.take(10),
                s.tempC?.let { "${it}°C" } ?: "-",
                (s.bewoelkung ?: "-") to normalizeNiedForPdf(s.niederschlag ?: "-")
            )
        }
    } else if (entry.weatherSlots.isNotEmpty()) {
        entry.weatherSlots.map { s ->
            Triple(
                s.uhrzeit.take(10),
                s.tempC?.let { "${it}°C" } ?: "-",
                (s.bewoelkung ?: "-") to normalizeNiedForPdf(s.niederschlag ?: "-")
            )
        }
    } else emptyList()

    // Wenn Niederschlag inkl. mm/m² extrem lang ist: Wetter-Rahmen nach rechts erweitern (nur wenn nötig)
    if (slots.isNotEmpty()) {
        val padTmp = pad
        val innerW0 = boxW - 2 * padTmp
        val gapX0 = 4f
        val colZeitW0 = 34f
        val colTempW0 = 32f
        var colBewW0 = 66f
        var colNiedW0 = innerW0 - colZeitW0 - colTempW0 - colBewW0 - 3 * gapX0
        val minNiedW0 = 78f
        if (colNiedW0 < minNiedW0) {
            val deficit = (minNiedW0 - colNiedW0)
            colBewW0 = (colBewW0 - deficit).coerceAtLeast(54f)
            colNiedW0 = innerW0 - colZeitW0 - colTempW0 - colBewW0 - 3 * gapX0
        }
        val minNiedSize = 7.0f
        val pNiedMin = Paint(pTCompact).apply { textSize = minNiedSize }
        val maxNeed = slots.maxOfOrNull { r -> pNiedMin.measureText(r.third.second) } ?: 0f
        if (maxNeed > colNiedW0) {
            val extra = (maxNeed - colNiedW0) + 8f
            val newBoxW = (boxW + extra).coerceAtMost(availW)
            if (newBoxW > boxW + 0.5f) {
                boxW = newBoxW
            }
        }
    }

    // Header
    w.ensure(26f)
    val top = w.y - 15f
    val headerRect = RectF(x0, top, x0 + boxW, w.y + 8f)
    w.canvas.drawRoundRect(headerRect, 6f, 6f, pBox)
    w.canvas.drawText("Wetter", x0 + pad, w.y, if (weatherChange.changed) pHRed else pH)
    w.y += 16f

    // Stand/Quelle (kompakt, in Boxbreite umbrechen)
    val origin: String? = wb?.locationName?.takeIf { it.isNotBlank() }
        ?: wb?.stationName?.takeIf { it.isNotBlank() }
        ?: (wb?.lat?.let { lat -> wb.lon?.let { lon -> String.format(java.util.Locale.ROOT, "%.5f, %.5f", lat, lon) } })
    val originLine = origin?.let {
        if (wb?.locationName?.isNullOrBlank() == false) "Ort: $it"
        else if (wb?.stationName?.isNullOrBlank() == false) "Station: $it"
        else "Koordinaten: $it"
    }
    val stand = wb?.standEpochMs?.let {
        runCatching {
            java.time.Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(timeFmt)
        }.getOrNull()
    }
    val src = wb?.source?.name
    val standLine = listOfNotNull(
        originLine,
        stand?.let { "Stand: $it" },
        src?.let { "Quelle: $it" }
    ).joinToString("   ")
    if (standLine.isNotBlank()) {
        wrapAt(standLine, pSmall, x0 + pad, boxW - 2 * pad)
        w.y += 2f
    }
    if (slots.isNotEmpty()) {
        // Tabelle in Boxbreite (sehr kompakt, gleichmäßige Spaltenabstände)
        w.ensure(16f)
        val innerW = boxW - 2 * pad
        val gapX = 4f

        val labelZeit = "Zeit"
        val labelTemp = "Temp"
        val labelBew = "Bewölkung"
        val labelNied = "Niederschlag"
        // Spalten kompakt + optisch gleichmäßiger: feste Grundbreiten, Rest für Niederschlag
        val colZeitW = 34f
        val colTempW = 32f
        var colBewW = 66f

        var colNiedW = innerW - colZeitW - colTempW - colBewW - 3 * gapX
        val minNiedW = 78f
        if (colNiedW < minNiedW) {
            val deficit = (minNiedW - colNiedW)
            colBewW = (colBewW - deficit).coerceAtLeast(54f)
            colNiedW = innerW - colZeitW - colTempW - colBewW - 3 * gapX
        }

        val colZeitX = x0 + pad
        val colTempX = colZeitX + colZeitW + gapX
        val colBewX = colTempX + colTempW + gapX
        val colNiedX = colBewX + colBewW + gapX

        w.y += 7f

        val headerY = w.y
        drawCell(labelZeit, colZeitX, headerY, pSmall, colZeitW)
        drawCell(labelTemp, colTempX, headerY, pSmall, colTempW)
        drawCell(labelBew, colBewX, headerY, pSmall, colBewW)
        drawCell(labelNied, colNiedX, headerY, pSmall, colNiedW)

        // Kein grauer Trennstrich: nur etwas Luft zwischen Header und Daten
        w.y += 9f

        val rowH = 11.0f
        slots.forEach { row ->
            w.ensure(rowH)
            val lineY = w.y
            val key = "${row.first.take(5)}|${row.second}|${row.third.first}|${row.third.second}"
            val rowPaint = if (weatherChange.redKeys.contains(key)) pTCompactRed else pTCompact
            drawCell(row.first.take(5), colZeitX, lineY, rowPaint, colZeitW)
            drawCell(row.second, colTempX, lineY, rowPaint, colTempW)
            drawCell(row.third.first, colBewX, lineY, rowPaint, colBewW)
            drawCellFitNoEllipsize(row.third.second, colNiedX, lineY, rowPaint, colNiedW, minSize = 7.0f)
            w.y += rowH
        }
    } else {
        w.canvas.drawText("–", x0 + pad, w.y, pT)
        w.y += 18f
    }

    // Removed rows are not visible in main table, so show them as small changes block
    val removedRows = weatherChange.removedKeys
        .mapNotNull { key ->
            val parts = key.split("|")
            if (parts.size >= 4) parts[0] + "  " + parts[1] + "  " + parts[2] + "  " + parts[3] else null
        }
        .distinct()
        .sorted()
    if (removedRows.isNotEmpty()) {
        w.y += 4f
        wrapAt("Änderungen (entfernt):", pSmall, x0 + pad, boxW - 2 * pad)
        removedRows.forEach { ln ->
            w.ensure((pTCompactGrey.textSize * 1.22f) + 6f)
            w.canvas.drawText("•", x0 + pad, w.y, pTCompactGrey)
            wrapAt("(entfernt) " + ln, pTCompactGrey, x0 + pad + 8f, boxW - (2 * pad + 8f))
        }
        w.y += 2f
    }

    val bottom = w.y + 6f
    w.canvas.drawRect(x0, top, x0 + boxW, bottom, pBorder)
    // Einheitlicher Abstand zwischen Rahmen: der nächste Rahmen startet i.d.R. bei (y - 15f)
    // => wir addieren hier +15f, damit der sichtbare Abstand exakt boxGap ist.
    val afterFrame = bottom + boxGap + 15f
    val afterLogo = if (logoBottom > 0f) (logoBottom + 6f) else afterFrame
    w.y = max(afterFrame, afterLogo)
}

fun renderFirmaGrouped(
    title: String,
    change: SectionChange,
    firms: List<FirmaBlock>,
    include: (FirmaBlock) -> Boolean,
    lines: (FirmaBlock) -> List<String>
) {
    w.boxTitle(title, if (change.changed) pHRed else pH)
    val visible = firms.filter(include)
    val hasAny = visible.any { lines(it).any { l -> l.isNotBlank() } }
    if (!hasAny) {
        w.textWrapped("–", pT, indent = 8f)
        w.y += 8f
        return
    }

    fun headerFor(f: FirmaBlock): String {
        fun fmt(von: String?, bis: String?, name: String): String {
            val v = von?.trim().orEmpty()
            val b = bis?.trim().orEmpty()
            return when {
                v.isNotBlank() && b.isNotBlank() -> "${v} - ${b} $name"
                v.isNotBlank() && b.isBlank() -> "${v} - $name"
                v.isBlank() && b.isNotBlank() -> "- ${b} $name"
                else -> name
            }
        }

        return when {
            title.startsWith("Personal") -> fmt(f.personalVon, f.personalBis, f.name)
            title.startsWith("Maschinen") -> fmt(f.maschinenVon, f.maschinenBis, f.name)
            else -> f.name
        }
    }

    visible.forEach { f ->
        val lns = lines(f).filter { it.isNotBlank() }
        if (lns.isEmpty()) return@forEach
        w.textWrapped(headerFor(f), pH, indent = 8f)
        lns.forEach { ln ->
            w.bulletMain(ln)
            w.y += 2f
        }
        w.y += 4f
    }
    w.y += 6f
}

fun renderArbeiten() {
    w.boxTitle("4. Ausgeführte Arbeiten", if (arbeitenChange.changed) pHRed else pH)

// Firmen: case-insensitive "kanonischer" Name aus entry.firmen, ansonsten so wie getippt.
// Für das Parsing gilt: jeder Prefix vor ':' zählt als Firma (z.B. "abc:", "Leonard:"), auch nach Bullet-Zeichen.
val firmCanonicalByLower: Map<String, String> = entry.firmen
    .map { it.name.trim() }
    .filter { it.isNotBlank() }
    .associateBy { it.lowercase(Locale.ROOT) }

fun canonFirm(name: String?): String? {
    val t = name?.trim().orEmpty()
    if (t.isBlank()) return null
    return firmCanonicalByLower[t.lowercase(Locale.ROOT)] ?: t
}



    data class PdfEvent(
        val sortTime: LocalTime,
        val kind: Int,
        val seq: Int,
        val ortId: Long,
        var firma: String?,
        val line: String
    )


    fun parseTimeOrNull(s: String?): LocalTime? {
        val t = s?.trim().orEmpty()
        if (t.isBlank()) return null
        return runCatching { LocalTime.parse(t, timeFmt) }.getOrNull()
    }

    fun manualEventsFromMultiline(raw: String?, kind: Int, seqBase: Int): List<PdfEvent> {
        // Single time: "HH:mm Text" | Span: "HH:mm–HH:mm Text" (oder mit '-')
        val reTime = Regex("""^([0-9]{1,2}:[0-9]{2})(?:\s*[-–]\s*\(?\s*([0-9]{1,2}:[0-9]{2})\s*\)?\s*)?\s+(.+)$""")
        var lastTime: LocalTime = LocalTime.MIN
        var seq = seqBase
        var ortId: Long = 0L

        val out = ArrayList<PdfEvent>()

        fun splitFirma(txt0: String, forceGeneral: Boolean = false): Pair<String?, String> {
            val txt = stripLeadingBullets(txt0).trim()
            if (forceGeneral) return null to txt
            if (looksLikeLeadingTime(txt)) return null to txt
            val idxC = txt.indexOf(':')
            if (idxC <= 0) return null to txt
            val prefix = txt.substring(0, idxC).trim()
            val tail = txt.substring(idxC + 1).trim()
            if (prefix.isBlank() || tail.isBlank()) return null to txt
            if (looksLikeTimeColonSplit(prefix, tail)) return null to txt
            val canon = firmCanonicalByLower[prefix.lowercase(Locale.ROOT)] ?: prefix
            return canon to tail
        }


        raw.orEmpty()
            .lines()
            .forEach { rawLine ->
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

                    val tStart = runCatching { LocalTime.parse(tStartStr, DateTimeFormatter.ofPattern("H:mm")) }
                        .getOrNull()?.let { LocalTime.of(it.hour, it.minute) } ?: lastTime
                    val tEnd = tEndStr?.let {
                        runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("H:mm")) }
                            .getOrNull()?.let { t -> LocalTime.of(t.hour, t.minute) }
                    }

                    lastTime = tStart

                    val (firma, txt2) = splitFirma(rest, forceGeneral = forcedGeneral)
                    if (txt2.isBlank()) return@forEach
                    val finalOrtId = if (firma == null) 0L else effectiveOrtId

                    val prefix = if (tEnd != null) "${tStart.format(timeFmt)}–${tEnd.format(timeFmt)}" else tStart.format(timeFmt)
                    val display = listOfNotNull(prefix, kmLabel, txt2).joinToString(" ")
                    out.add(PdfEvent(sortTime = tStart, kind = kind, seq = seq++, ortId = finalOrtId, firma = firma, line = display))
                } else {
                    val (firma, txt2) = splitFirma(line, forceGeneral = forcedGeneral)
                    if (txt2.isBlank()) return@forEach
                    val finalOrtId = if (firma == null) 0L else effectiveOrtId
                    // Notiz ohne Uhrzeit -> hängt am letzten Zeit-Event
                    val display = listOfNotNull(kmLabel, txt2).joinToString(" ")
                    out.add(PdfEvent(sortTime = lastTime, kind = kind, seq = seq++, ortId = finalOrtId, firma = firma, line = display))
                }
            }

        return out
    }

    fun manualEventsFromSection(block: SectionBlock, kind: Int, seqBase: Int): List<PdfEvent> {
        var lastTime: LocalTime = LocalTime.MIN
        var seq = seqBase
        var ortId: Long = 0L

        fun splitFirmaSection(txt0: String, forceGeneral: Boolean = false): Pair<String?, String> {
            val txt = stripLeadingBullets(txt0).trim()
            if (forceGeneral) return null to txt
            if (looksLikeLeadingTime(txt)) return null to txt
            val idxC = txt.indexOf(':')
            if (idxC <= 0) return null to txt
            val prefix = txt.substring(0, idxC).trim()
            val tail = txt.substring(idxC + 1).trim()
            if (prefix.isBlank() || tail.isBlank()) return null to txt
            if (looksLikeTimeColonSplit(prefix, tail)) return null to txt
            val canon = firmCanonicalByLower[prefix.lowercase(Locale.ROOT)] ?: prefix
            return canon to tail
        }

        return block.items
            .mapNotNull { it2 ->
                val raw = it2.text.trim()
                if (raw.isBlank()) return@mapNotNull null

                // Ort kann als Marker im Text kommen: "@ORT=123" (0 = Nur Hauptbaustelle)
                val (newOrt, rest0) = applyOrtMarker(raw, ortId)
                ortId = newOrt
                val (forcedGeneral, rest1) = applyGeneralMarker(rest0)
                val effectiveOrtId = if (forcedGeneral) 0L else ortId

                // applyOrtMarker()/applyGeneralMarker() können null zurückgeben (wenn eine Zeile nur Marker enthält)
                val txt = rest1?.trim().orEmpty()
                if (txt.isBlank()) return@mapNotNull null

                val t = parseTimeOrNull(it2.uhrzeit)
                if (t != null) lastTime = t
                val (firma, txt2) = splitFirmaSection(txt, forceGeneral = forcedGeneral)
                val finalTxt = txt2.trim()
                if (finalTxt.isBlank()) return@mapNotNull null
                val finalOrtId = if (firma == null) 0L else effectiveOrtId

                val kmLabel = de.tbhelfer.util.KmAnzeige.bereich(it2.kmVon, it2.kmBis, inKlammern = true)
                val lineParts = listOfNotNull(t?.format(timeFmt), kmLabel, finalTxt)
                PdfEvent(
                    sortTime = t ?: lastTime,
                    kind = kind,
                    seq = seq++,
                    ortId = finalOrtId,
                    firma = firma,
                    line = lineParts.joinToString(" ")
                )
            }
    }

    fun leistungEvents(kind: Int, seqBase: Int): List<PdfEvent> {
        var seq = seqBase
        val out = ArrayList<PdfEvent>()
        entry.firmen
            .filter { it.leistungen.isNotEmpty() }
            .forEach { firm ->
                var ortId: Long = 0L
                firm.leistungen.forEach { lz ->
                    val (newOrt, rest) = applyOrtMarker(lz.text, ortId)
                    ortId = newOrt
                    val txt = rest?.trim().orEmpty()
                    if (txt.isBlank()) return@forEach
                    val t = parseTimeOrNull(lz.uhrzeit)
                    val line = if (t != null) "${t.format(timeFmt)} $txt" else txt
                    out.add(PdfEvent(sortTime = t ?: LocalTime.MIN, kind = kind, seq = seq++, ortId = ortId, firma = firm.name.trim(), line = line))
                }
            }
        return out
    }

    fun fotoEventsForType(bt: Bildtyp, kind: Int, seqBase: Int): List<PdfEvent> {
        data class Row(
            val firma: String?,
            val ortId: Long,
            val time: String,
            val text: String,
            val km: Double?,
            val id: Long,
            val epochMs: Long
        )

        val rows = byType[bt].orEmpty().mapNotNull { f ->
            val txt = btbTextOfFoto(f).trim()
            if (txt.isBlank()) return@mapNotNull null
            val firm = canonFirm(f.firma)
            Row(
                firma = firm,
                ortId = f.baustellenKnotenId ?: 0L,
                time = timeOfFoto(f),
                text = txt,
                km = f.km,
                id = f.id,
                epochMs = f.erstelltAmEpochMs
            )
        }

        fun bildSuffix(numsIn: List<Int>): String {
            if (numsIn.isEmpty()) return ""
            val nums = numsIn.distinct().sorted()
            val runs = ArrayList<Pair<Int, Int>>()
            var a = nums.first()
            var b = a
            for (n in nums.drop(1)) {
                if (n == b + 1) b = n
                else {
                    runs.add(a to b)
                    a = n
                    b = n
                }
            }
            runs.add(a to b)
            val label = runs.joinToString("+") { (x, y) -> if (x == y) "$x" else "$x–$y" }
            return " [Bild $label]"
        }
        var seq = seqBase
        val out = ArrayList<PdfEvent>()

        val grouped = rows.groupBy { Triple(it.firma, it.ortId, it.text) }
grouped.entries.forEach { (k, list) ->
    val (firma, ortId, txt) = k
    val sorted = list.sortedBy { it.epochMs }
    if (sorted.isEmpty()) return@forEach
    val first = sorted.first()
    val last = sorted.last()
    val (minT, maxT) = effectiveRangeFor(sorted.map { it.id }, first.epochMs, last.epochMs)
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
    out.add(PdfEvent(sortT, kind, seq++, ortId, firma, line))
}


        return out.sortedWith(compareBy<PdfEvent> { it.sortTime }.thenBy { it.seq })
    }

    fun assignFirma(events: List<PdfEvent>): List<PdfEvent> {
        events.forEach { e ->
            e.firma = canonFirm(e.firma)
        }
        return events
    }
    fun drawBulletWrappedPartial(newLine: String, oldLine: String, indent: Float) {
            fun diffSegments(oldText: String, newText: String): List<Pair<String, Boolean>> {
                if (oldText == newText) return listOf(newText to false)
                var prefixLen = 0
                val minLen = minOf(oldText.length, newText.length)
                while (prefixLen < minLen && oldText[prefixLen] == newText[prefixLen]) prefixLen++
                while (prefixLen > 0 && prefixLen < newText.length && !oldText[prefixLen - 1].isWhitespace() && !newText[prefixLen - 1].isWhitespace()) prefixLen--
                var suffixLen = 0
                while (
                    suffixLen < oldText.length - prefixLen &&
                    suffixLen < newText.length - prefixLen &&
                    oldText[oldText.length - 1 - suffixLen] == newText[newText.length - 1 - suffixLen]
                ) suffixLen++
                while (
                    suffixLen > 0 &&
                    newText.length - suffixLen > prefixLen &&
                    !newText[newText.length - suffixLen].isWhitespace() &&
                    !oldText[oldText.length - suffixLen].isWhitespace()
                ) suffixLen--
                val prefix = newText.substring(0, prefixLen)
                val changed = newText.substring(prefixLen, newText.length - suffixLen)
                val suffix = newText.substring(newText.length - suffixLen)
                return buildList {
                    if (prefix.isNotEmpty()) add(prefix to false)
                    if (changed.isNotEmpty()) add(changed to true)
                    if (suffix.isNotEmpty()) add(suffix to false)
                }.ifEmpty { listOf(newText to true) }
            }

            fun tokens(seg: String): List<String> = Regex("""\S+|\s+""").findAll(seg).map { it.value }.toList()

            val xBullet = marginL + indent
            val xStart = xBullet + 14f
            val maxX = pageW - marginR
            val lineH = maxOf(pT.textSize, pTRed.textSize) * 1.22f
            w.ensure(lineH + 6f)
            w.canvas.drawText("•", xBullet, w.y, pT)
            var x = xStart
            var y = w.y
            val pTRedStrong = Paint(pTRed).apply { isFakeBoldText = true }
            diffSegments(oldLine, newLine).forEach { (seg, isChanged) ->
                val paint = if (isChanged) pTRedStrong else pT
                tokens(seg).forEach { token ->
                    var tk = token
                    if (tk.isBlank() && x == xStart) return@forEach
                    val width = paint.measureText(tk)
                    if (!tk.isBlank() && x + width > maxX && x > xStart) {
                        y += lineH
                        w.ensure(lineH + 4f)
                        x = xStart
                        tk = tk.trimStart()
                        if (tk.isBlank()) return@forEach
                    }
                    w.canvas.drawText(tk, x, y, paint)
                    x += paint.measureText(tk)
                }
            }
            w.y = y + lineH + 1f
        }

    fun drawFirmaGrouped(events: List<PdfEvent>, emptyText: String) {
        if (events.isEmpty()) {
            w.textWrapped(emptyText, pT, indent = 8f)
            w.y += 8f
            return
        }

        val normalized = assignFirma(events.sortedWith(compareBy<PdfEvent> { it.sortTime }.thenBy { it.kind }.thenBy { it.seq }))
        val groups = normalized.groupBy { it.firma }

        val firmKeys = groups.keys.filterNotNull().map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val hasFirmHeaders = firmKeys.isNotEmpty() || groups[null].orEmpty().isNotEmpty()

        val orderedKeys = buildList<String?> {
            add(null) // Allgemein zuerst
            addAll(firmKeys.sortedBy { k -> groups[k]!!.minOf { it.sortTime } })
        }

        orderedKeys.forEach { firmName ->
            val list = groups[firmName].orEmpty()
            if (list.isEmpty()) return@forEach

            val firmKey = firmName?.trim().orEmpty()
            val firmHasRed = list.any { e ->
                val k = "${firmKey}||${e.ortId}||${wsNorm(e.line)}"
                arbeitenChange.redKeys.contains(k)
            }

            if (hasFirmHeaders) {
                keepWithNextRows(58f)
                val header = firmName ?: context.getString(R.string.btb_company_general)
                w.textWrapped(header, if (firmHasRed) pHRed else pH, indent = 8f)
            }

            val byOrt = list.groupBy { it.ortId }
            val showOrtHeaders = byOrt.isNotEmpty()

            val ortIdsOrdered = byOrt.keys.sortedWith(
                compareBy<Long> { ortOrderById[it] ?: Int.MAX_VALUE }.thenBy { it }
            )

            var lastPrintedParent: String? = null
            ortIdsOrdered.forEach { oid ->
                val items = byOrt[oid].orEmpty()
                if (items.isEmpty()) return@forEach

                val ortHasRed = items.any { e ->
                    val k = "${firmKey}||${e.ortId}||${wsNorm(e.line)}"
                    arbeitenChange.redKeys.contains(k)
                }

                val baseOrtIndent = if (hasFirmHeaders) 16f else 8f
                val depth = (ortDepthById[oid] ?: 0).coerceAtLeast(0)
                val parentLabel = ortParentLabel(oid)
                val ortIndent = baseOrtIndent + if (depth > 0) 26f else 0f
                val bulletIndent = if (showOrtHeaders) (ortIndent + 8f) else (if (hasFirmHeaders) 16f else 8f)

                if (showOrtHeaders) {
                    if (depth > 0 && !parentLabel.isNullOrBlank() && parentLabel != lastPrintedParent) {
                        keepWithNextRows(44f)
                        w.subBoxTitle(parentLabel, pSub, indent = baseOrtIndent)
                        lastPrintedParent = parentLabel
                    }
                    keepWithNextRows(44f)
                    val label = ortLabelById[oid] ?: "Ort $oid"
                    w.subBoxTitle(label, if (depth > 0) { if (ortHasRed) pSubChildRed else pSubChild } else { if (ortHasRed) pSubRed else pSub }, indent = ortIndent)
                    if (depth == 0) lastPrintedParent = label
                }

                items.sortedWith(compareBy<PdfEvent> { it.sortTime }.thenBy { it.kind }.thenBy { it.seq })
                    .forEach { e ->
                        val key = "${firmKey}||${e.ortId}||${wsNorm(e.line)}"
                        val editedFrom = arbeitenChange.editedFromByNewKey[key]
                        when {
                            editedFrom != null -> {
                                val oldLine = editedFrom.substringAfterLast("||", "")
                                drawBulletWrappedPartial(e.line, oldLine, indent = bulletIndent)
                            }
                            arbeitenChange.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
                            else -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pT)
                        }
                        w.y += 0f
                    }
                w.y += 2f
            }
            w.y += 4f
        }
        w.y += 6f
    }

    val events = ArrayList<PdfEvent>()

    // 1) Firmen-Leistungen (falls genutzt)
    events += leistungEvents(kind = 0, seqBase = 0)

    // 2) Manuelle Einträge aus Feld "4. Ausgeführte Arbeiten" (entry.notizen)
    events += manualEventsFromMultiline(entry.notizen, kind = 1, seqBase = 10_000)

    // 3) Fotos: FOTODOKUMENTATION
    events += fotoEventsForType(Bildtyp.FOTODOKUMENTATION, kind = 2, seqBase = 20_000)

    drawFirmaGrouped(events, emptyText = "Keine Arbeiten eingetragen.")

    renderRemovedBlock(arbeitenChange) { key ->
        val i1 = key.indexOf("||")
        val i2 = if (i1 >= 0) key.indexOf("||", i1 + 2) else -1
        if (i1 >= 0 && i2 >= 0) {
            val firma = wsNorm(key.substring(0, i1))
            val oid = key.substring(i1 + 2, i2).trim().toLongOrNull() ?: 0L
            val line = wsNorm(key.substring(i2 + 2))
            val ort = ortLabelById[oid].orEmpty()
            val head = when {
                firma.isNotBlank() && ort.isNotBlank() -> "$firma: $ort"
                firma.isNotBlank() -> "$firma:"
                ort.isNotBlank() -> ort
                else -> ""
            }
            if (head.isBlank()) line else "$head $line".trim()
        } else wsNorm(key)
    }
}

fun renderStandardSection(
    title: String,
    change: SectionChange,
    block: SectionBlock,
    emptyText: String,
    fotoType: Bildtyp
) {
    w.boxTitle(title, if (change.changed) pHRed else pH)

    data class PdfEvent(
        val sortTime: LocalTime,
        val kind: Int,
        val seq: Int,
        val ortId: Long,
        var firma: String?,
        val line: String
    )
    val timeIn = java.time.format.DateTimeFormatter.ofPattern("H:mm")
    fun normHHmm(s: String): String? = runCatching { LocalTime.parse(s.trim(), timeIn).format(timeFmt) }.getOrNull()

    fun parseStartTimeAndLabel(raw: String?): Pair<LocalTime?, String?> {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null to null
        val m = Regex("""^(\d{1,2}:\d{2})\s*[-–]\s*\(?\s*(\d{1,2}:\d{2})\s*\)?$""").matchEntire(s)
        if (m != null) {
            val a = normHHmm(m.groupValues[1]) ?: return null to null
            val b = normHHmm(m.groupValues[2]) ?: return null to null
            val sortT = runCatching { LocalTime.parse(a, timeFmt) }.getOrNull()
            return sortT to "$a–$b"
        }
        val a = normHHmm(s) ?: return null to null
        val sortT = runCatching { LocalTime.parse(a, timeFmt) }.getOrNull()
        return sortT to a
    }
val firmCanonicalByLower: Map<String, String> = entry.firmen
    .map { it.name.trim() }
    .filter { it.isNotBlank() }
    .associateBy { it.lowercase(Locale.ROOT) }

fun canonFirm(name: String): String {
    val t = name.trim()
    if (t.isBlank()) return t
    return firmCanonicalByLower[t.lowercase(Locale.ROOT)] ?: t
}

fun splitFirma(txtIn: String, forceGeneral: Boolean = false): Pair<String?, String> {
    val txt = stripLeadingBullets(txtIn).trim()
    if (forceGeneral) return null to txt.trim()
    if (looksLikeLeadingTime(txt)) return null to txt.trim()
    val idx = txt.indexOf(':')
    if (idx <= 0) return null to txt.trim()
    val prefix = txt.substring(0, idx).trim()
    val tail = txt.substring(idx + 1).trim()
    if (prefix.isBlank() || tail.isBlank()) return null to txt.trim()
    if (looksLikeTimeColonSplit(prefix, tail)) return null to txt.trim()
    val canon = firmCanonicalByLower[prefix.lowercase(Locale.ROOT)] ?: prefix
    return canon to tail
}

    fun manualEvents(block: SectionBlock, kind: Int, seqBase: Int): List<PdfEvent> {
        var lastTime: LocalTime = LocalTime.MIN
        var seq = seqBase
        val out = ArrayList<PdfEvent>()
        var ortId: Long = 0L
        block.items.forEach { it2 ->
            val (newOrt, cleaned0) = applyOrtMarker(it2.text, ortId)
            ortId = newOrt
            val (forcedGeneral, cleaned) = applyGeneralMarker(cleaned0)
            val effectiveOrtId = if (forcedGeneral) 0L else ortId
            val rawTxt = cleaned?.trim().orEmpty()
            if (rawTxt.isBlank()) return@forEach

            val (firm, txt2) = splitFirma(rawTxt, forceGeneral = forcedGeneral)
            if (txt2.isBlank()) return@forEach
            val finalOrtId = if (firm == null) 0L else effectiveOrtId

            val (t, label) = parseStartTimeAndLabel(it2.uhrzeit)
            if (t != null) lastTime = t

            val sortT = t ?: lastTime
            val kmLabel = de.tbhelfer.util.KmAnzeige.bereich(it2.kmVon, it2.kmBis, inKlammern = true)
            val line = listOfNotNull(label?.takeIf { it.isNotBlank() }, kmLabel, txt2).joinToString(" ")
            out.add(PdfEvent(sortT, kind, seq++, finalOrtId, firm, line))
        }
        return out
    }

    fun fotoEvents(bt: Bildtyp, kind: Int, seqBase: Int): List<PdfEvent> {
        data class Row(
            val firma: String?,
            val ortId: Long,
            val time: String,
            val text: String,
            val km: Double?,
            val id: Long,
            val epochMs: Long
        )

        val rows = byType[bt].orEmpty().mapNotNull { f ->
            val txt = btbTextOfFoto(f).trim()
            if (txt.isBlank()) return@mapNotNull null
            val firm = f.firma.trim().takeIf { it.isNotBlank() }?.let { canonFirm(it) }
            Row(
                firma = firm,
                ortId = f.baustellenKnotenId ?: 0L,
                time = timeOfFoto(f),
                text = txt,
                km = f.km,
                id = f.id,
                epochMs = f.erstelltAmEpochMs
            )
        }

        fun bildSuffix(numsIn: List<Int>): String {
            if (numsIn.isEmpty()) return ""
            val nums = numsIn.distinct().sorted()
            val runs = ArrayList<Pair<Int, Int>>()
            var a = nums.first()
            var b = a
            for (n in nums.drop(1)) {
                if (n == b + 1) b = n
                else {
                    runs.add(a to b)
                    a = n
                    b = n
                }
            }
            runs.add(a to b)
            val label = runs.joinToString("+") { (x, y) -> if (x == y) "$x" else "$x–$y" }
            return " [Bild $label]"
        }
        var seq = seqBase
        val out = ArrayList<PdfEvent>()

        val grouped = rows.groupBy { Triple(it.firma, it.ortId, it.text) }
grouped.entries.forEach { (k, list) ->
    val (firma, ortId, txt) = k
    val sorted = list.sortedBy { it.epochMs }
    if (sorted.isEmpty()) return@forEach
    val first = sorted.first()
    val last = sorted.last()
    val (minT, maxT) = effectiveRangeFor(sorted.map { it.id }, first.epochMs, last.epochMs)
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
    out.add(PdfEvent(sortT, kind, seq++, ortId, firma, line))
}


        return out.sortedWith(compareBy<PdfEvent> { it.sortTime }.thenBy { it.seq })
    }

    fun assignFirma(events: List<PdfEvent>): List<PdfEvent> {
        events.forEach { e ->
            e.firma = e.firma?.let { canonFirm(it) }?.takeIf { it.isNotBlank() }
        }
        return events
    }

    fun drawBulletWrappedPartial(newLine: String, oldLine: String, indent: Float) {
            fun diffSegments(oldText: String, newText: String): List<Pair<String, Boolean>> {
                if (oldText == newText) return listOf(newText to false)
                var prefixLen = 0
                val minLen = minOf(oldText.length, newText.length)
                while (prefixLen < minLen && oldText[prefixLen] == newText[prefixLen]) prefixLen++
                while (prefixLen > 0 && prefixLen < newText.length && !oldText[prefixLen - 1].isWhitespace() && !newText[prefixLen - 1].isWhitespace()) prefixLen--
                var suffixLen = 0
                while (
                    suffixLen < oldText.length - prefixLen &&
                    suffixLen < newText.length - prefixLen &&
                    oldText[oldText.length - 1 - suffixLen] == newText[newText.length - 1 - suffixLen]
                ) suffixLen++
                while (
                    suffixLen > 0 &&
                    newText.length - suffixLen > prefixLen &&
                    !newText[newText.length - suffixLen].isWhitespace() &&
                    !oldText[oldText.length - suffixLen].isWhitespace()
                ) suffixLen--
                val prefix = newText.substring(0, prefixLen)
                val changed = newText.substring(prefixLen, newText.length - suffixLen)
                val suffix = newText.substring(newText.length - suffixLen)
                return buildList {
                    if (prefix.isNotEmpty()) add(prefix to false)
                    if (changed.isNotEmpty()) add(changed to true)
                    if (suffix.isNotEmpty()) add(suffix to false)
                }.ifEmpty { listOf(newText to true) }
            }

            fun tokens(seg: String): List<String> = Regex("""\S+|\s+""").findAll(seg).map { it.value }.toList()

            val xBullet = marginL + indent
            val xStart = xBullet + 14f
            val maxX = pageW - marginR
            val lineH = maxOf(pT.textSize, pTRed.textSize) * 1.22f
            w.ensure(lineH + 6f)
            w.canvas.drawText("•", xBullet, w.y, pT)
            var x = xStart
            var y = w.y
            val pTRedStrong = Paint(pTRed).apply { isFakeBoldText = true }
            diffSegments(oldLine, newLine).forEach { (seg, isChanged) ->
                val paint = if (isChanged) pTRedStrong else pT
                tokens(seg).forEach { token ->
                    var tk = token
                    if (tk.isBlank() && x == xStart) return@forEach
                    val width = paint.measureText(tk)
                    if (!tk.isBlank() && x + width > maxX && x > xStart) {
                        y += lineH
                        w.ensure(lineH + 4f)
                        x = xStart
                        tk = tk.trimStart()
                        if (tk.isBlank()) return@forEach
                    }
                    w.canvas.drawText(tk, x, y, paint)
                    x += paint.measureText(tk)
                }
            }
            w.y = y + lineH + 1f
        }

    fun drawFirmaGrouped(events: List<PdfEvent>, emptyText: String) {
            if (events.isEmpty()) {
                w.textWrapped(emptyText, pT, indent = 8f)
                w.y += 8f
                return
            }

            val normalized = assignFirma(events.sortedWith(compareBy<PdfEvent> { it.sortTime }.thenBy { it.kind }.thenBy { it.seq }))
            val groups = normalized.groupBy { it.firma }

            val firmKeys = groups.keys.filterNotNull().map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val hasFirmHeaders = firmKeys.isNotEmpty() || groups[null].orEmpty().isNotEmpty()

            val orderedKeys = buildList<String?> {
                add(null) // Allgemein zuerst
                addAll(firmKeys.sortedBy { k -> groups[k]!!.minOf { it.sortTime } })
            }

            orderedKeys.forEach { firmName ->
                val list = groups[firmName].orEmpty()
                if (list.isEmpty()) return@forEach

                val firmKey = firmName?.trim().orEmpty()
                val firmHasRed = list.any { e ->
                    val k = "${firmKey}||${e.ortId}||${wsNorm(e.line)}"
                    change.redKeys.contains(k)
                }

                if (hasFirmHeaders) {
                    keepWithNextRows(58f)
                    val header = firmName ?: context.getString(R.string.btb_company_general)
                    w.textWrapped(header, if (firmHasRed) pHRed else pH, indent = 8f)
                }

                val byOrt = list.groupBy { it.ortId }
                val showOrtHeaders = byOrt.isNotEmpty()

                val ortIdsOrdered = byOrt.keys.sortedWith(
                    compareBy<Long> { ortOrderById[it] ?: Int.MAX_VALUE }.thenBy { it }
                )

                var lastPrintedParent: String? = null
                ortIdsOrdered.forEach { oid ->
                    val items = byOrt[oid].orEmpty()
                    if (items.isEmpty()) return@forEach

                    val ortHasRed = items.any { e ->
                        val k = "${firmKey}||${e.ortId}||${wsNorm(e.line)}"
                        change.redKeys.contains(k)
                    }

                    val baseOrtIndent = if (hasFirmHeaders) 16f else 8f
                    val depth = (ortDepthById[oid] ?: 0).coerceAtLeast(0)
                    val parentLabel = ortParentLabel(oid)
                    val ortIndent = baseOrtIndent + if (depth > 0) 26f else 0f
                    val bulletIndent = if (showOrtHeaders) (ortIndent + 8f) else (if (hasFirmHeaders) 16f else 8f)

                    if (showOrtHeaders) {
                        if (depth > 0 && !parentLabel.isNullOrBlank() && parentLabel != lastPrintedParent) {
                            keepWithNextRows(44f)
                            w.subBoxTitle(parentLabel, pSub, indent = baseOrtIndent)
                            lastPrintedParent = parentLabel
                        }
                        keepWithNextRows(44f)
                        val label = ortLabelById[oid] ?: "Ort $oid"
                        w.subBoxTitle(label, if (depth > 0) { if (ortHasRed) pSubChildRed else pSubChild } else { if (ortHasRed) pSubRed else pSub }, indent = ortIndent)
                        if (depth == 0) lastPrintedParent = label
                    }

                    items.sortedWith(compareBy<PdfEvent> { it.sortTime }.thenBy { it.kind }.thenBy { it.seq })
                        .forEach { e ->
                            val key = "${firmKey}||${e.ortId}||${wsNorm(e.line)}"
                            val editedFrom = change.editedFromByNewKey[key]
                            when {
                                editedFrom != null -> {
                                    val oldLine = editedFrom.substringAfterLast("||", "")
                                    drawBulletWrappedPartial(e.line, oldLine, indent = bulletIndent)
                                }
                                change.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
                                else -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pT)
                            }
                            w.y += 0f
                        }
                    w.y += 2f
                }
                w.y += 4f
            }
            w.y += 6f
        }

    val events = ArrayList<PdfEvent>()
    events += manualEvents(block, kind = 0, seqBase = 0)
    events += fotoEvents(fotoType, kind = 1, seqBase = 10_000)

    drawFirmaGrouped(events, emptyText)

    renderRemovedBlock(change) { key ->
        val i1 = key.indexOf("||")
        val i2 = if (i1 >= 0) key.indexOf("||", i1 + 2) else -1
        if (i1 >= 0 && i2 >= 0) {
            val firma = wsNorm(key.substring(0, i1))
            val oid = key.substring(i1 + 2, i2).trim().toLongOrNull() ?: 0L
            val line = wsNorm(key.substring(i2 + 2))
            val ort = ortLabelById[oid].orEmpty()
            val head = when {
                firma.isNotBlank() && ort.isNotBlank() -> "$firma: $ort"
                firma.isNotBlank() -> "$firma:"
                ort.isNotBlank() -> ort
                else -> ""
            }
            if (head.isBlank()) line else "$head $line".trim()
        } else wsNorm(key)
    }
}

    fun renderPersonalMaschinenColumns() {
        val gap = 12f
        val colW = (contentW - gap) / 2f
        val xL = marginL
        val xR = marginL + colW + gap
        val pad = 6f
        val headerH = 22f

        // kompakter für Box-Inhalte (Personal/Maschinen)
        val pSubBox = Paint(pSub).apply { textSize = 9.6f; typeface = Typeface.DEFAULT_BOLD }
        val pTBox = Paint(pTCompact).apply { textSize = 9.4f }
        val pSubBoxRed = Paint(pSubBox).apply { color = Color.RED }
        val pTBoxRed = Paint(pTBox).apply { color = Color.RED }
        val pTBoxGrey = Paint(pTBox).apply { color = Color.DKGRAY }

        // grobe Platzreserve; bei Bedarf auf neue Seite springen
        w.ensure(220f)
        val topY = w.y

        fun wrapAt(text: String, paint: Paint, x: Float, maxW: Float, startY: Float): Float {
            var yLocal = startY
            val lineH = paint.textSize * 1.04f
            val paragraphs = text.split("\n")
            for (para in paragraphs) {
                val words = para.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                var line = ""
                for (wrd in words) {
                    val test = if (line.isEmpty()) wrd else "$line $wrd"
                    if (paint.measureText(test) <= maxW) {
                        line = test
                    } else {
                        w.canvas.drawText(line, x, yLocal, paint)
                        yLocal += lineH
                        line = wrd
                    }
                }
                if (line.isNotEmpty()) {
                    w.canvas.drawText(line, x, yLocal, paint)
                    yLocal += lineH
                }
                yLocal += lineH * 0.02f
            }
            return yLocal
        }

        fun headerFor(title: String, f: FirmaBlock): String {
            fun fmt(von: String?, bis: String?, name: String): String {
                val v = von?.trim().orEmpty()
                val b = bis?.trim().orEmpty()
                return when {
                    v.isNotBlank() && b.isNotBlank() -> "${v} - ${b} $name"
                    v.isNotBlank() && b.isBlank() -> "${v} - $name"
                    v.isBlank() && b.isNotBlank() -> "- ${b} $name"
                    else -> name
                }
            }
            return when {
                title.startsWith("Personal") -> fmt(f.personalVon, f.personalBis, f.name)
                title.startsWith("Maschinen") -> fmt(f.maschinenVon, f.maschinenBis, f.name)
                else -> f.name
            }
        }

        fun drawBox(
            title: String,
            x: Float,
            change: SectionChange,
            linesProvider: () -> List<Pair<String, List<String>>>
        ): Float {
            val top = topY - 15f
            // Header-Fill
            val headerRect = RectF(x, topY - 15f, x + colW, topY + 6f)
            w.canvas.drawRect(headerRect, pBox)
            w.canvas.drawText(title, x + pad, topY, if (change.changed) pHRed else pH)

            var yLocal = topY + 14f
            val groups = linesProvider()
    
            if (groups.isEmpty()) {
                w.canvas.drawText("–", x + pad, yLocal, pT)
                yLocal += 18f
            } else {
                groups.forEach { (hdr0, lns0) ->
                    val hdr = wsNorm(hdr0)
                    val lns = lns0.map { wsNorm(it) }.filter { it.isNotBlank() }

                    val headerKey = "H||$hdr"
                    val headerIsRed = change.redKeys.contains(headerKey) || lns.any { ln ->
                        change.redKeys.contains("B||$hdr||$ln")
                    }

                    if (hdr.isNotBlank()) {
                        yLocal = wrapAt(hdr, if (headerIsRed) pSubBoxRed else pSubBox, x + pad, colW - 2 * pad, yLocal)
                    }

                    lns.forEach { ln ->
                        val bulletKey = "B||$hdr||$ln"
                        val lp = if (change.redKeys.contains(bulletKey)) pTBoxRed else pTBox
                        // Bullet
                        w.canvas.drawText("•", x + pad, yLocal, lp)
                        yLocal = wrapAt(ln, lp, x + pad + 8f, colW - (2 * pad + 8f), yLocal)
                        yLocal += 0f
                    }
                    yLocal += 0f
                }
            }

            // Removed items (not visible in main list)
            val removedLines = change.removedKeys
                .mapNotNull { key ->
                    when {
                        key.startsWith("B||") -> {
                            val parts = key.split("||", limit = 3)
                            if (parts.size >= 3) {
                                val hdr = wsNorm(parts[1])
                                val ln = wsNorm(parts[2])
                                if (hdr.isBlank()) ln else (hdr + ": " + ln)
                            } else null
                        }
                        key.startsWith("H||") -> {
                            val hdr = wsNorm(key.removePrefix("H||"))
                            if (hdr.isNotBlank()) hdr else null
                        }
                        else -> null
                    }
                }
                .distinct()
                .sorted()
            if (removedLines.isNotEmpty()) {
                yLocal += 4f
                yLocal = wrapAt("Änderungen (entfernt):", pSmall, x + pad, colW - 2 * pad, yLocal)
                removedLines.forEach { ln ->
                    w.canvas.drawText("•", x + pad, yLocal, pTBoxGrey)
                    yLocal = wrapAt("(entfernt) " + ln, pTBoxGrey, x + pad + 8f, colW - (2 * pad + 8f), yLocal)
                }
                yLocal += 2f
            }

            val bottom = yLocal + 3f
            w.canvas.drawRect(x, top, x + colW, bottom, pBorder)
            return bottom
        }

        val bottomL = drawBox("Personal", xL, personalChange) {
            if (entry.firmen.isNotEmpty()) {
                entry.firmen
                    .filter { it.visibleInPersonal }
                    .mapNotNull { f ->
                        val lns = f.personal.map { p -> "${p.anzahl ?: ""} ${p.rolle}".trim() }.filter { it.isNotBlank() }
                        if (lns.isEmpty()) null else headerFor("Personal", f) to lns
                    }
            } else {
                val lns = entry.personal.filter { it.isNotBlank() }
                if (lns.isEmpty()) emptyList() else listOf("" to lns)
            }
        }

        val bottomR = drawBox("Maschinen/Geräte", xR, maschinenChange) {
            if (entry.firmen.isNotEmpty()) {
                entry.firmen
                    .filter { it.visibleInMaschinen }
                    .mapNotNull { f ->
                        val lns = f.geraete.map { g -> "${g.anzahl ?: ""} ${g.bezeichnung}".trim() }.filter { it.isNotBlank() }
                        if (lns.isEmpty()) null else headerFor("Maschinen/Geräte", f) to lns
                    }
            } else {
                val lns = entry.geraete.filter { it.isNotBlank() }
                if (lns.isEmpty()) emptyList() else listOf("" to lns)
            }
        }

        w.y = max(bottomL, bottomR) + boxGap + 15f
    }

    // 1 Wetter
    renderWeather()

    // 2–3 Personal + Maschinen immer 2-spaltig
    renderPersonalMaschinenColumns()

    fun renderFramedFull(title: String, minKeepWithTitle: Float = 90f, block: () -> Unit) {
        // V9a: V9-Druckmodell behalten, aber V8-Rahmenlogik für Abschnitt 4–8 wieder aktivieren.
        // Writer schließt den laufenden Rahmen bei Seitenwechsel sauber und beginnt auf
        // der Folgeseite mit "<Abschnitt> (Fortsetzung)".
        // Wetter/Personal/Maschinen bleiben unverändert.
        w.ensure(minKeepWithTitle)
        w.beginSectionFrame(title)
        block()
        w.endSectionFrame()
        w.y += boxGap + 8f
    }

    // 4 Leistungen (aus firmen)
    renderFramedFull("4. Ausgeführte Arbeiten", minKeepWithTitle = 110f) { renderArbeiten() }

    // 5–8 Standardabschnitte (zusätzlich: Foto-Texte je Bildtyp gruppiert)
    renderFramedFull("5. Abnahmen") { renderStandardSection("5. Abnahmen", abnahmenChange, entry.abnahmen, "Keine Abnahmen durchgeführt.", Bildtyp.ABNAHMEN) }
    renderFramedFull("6. Einweisung/Unterweisung") { renderStandardSection("6. Einweisung/Unterweisung", einweisungChange, entry.einweisungUnterweisung, "Keine Einweisung/Unterweisung durchgeführt.", Bildtyp.EINWEISUNG_UNTERWEISUNG) }
    renderFramedFull("7. Besonderheiten") { renderStandardSection("7. Besonderheiten", besonderheitenChange, entry.besonderheiten, "Keine Besonderheiten.", Bildtyp.BESONDERHEITEN) }
    renderFramedFull("8. Mängel/Schäden") { renderStandardSection("8. Mängel/Schäden", maengelChange, entry.maengelSchaeden, "Keine Mängel/Schäden gemeldet.", Bildtyp.MAENGEL_SCHAEDEN) }

    w.y = w.y.coerceAtLeast(marginTop)

    fun renderSignatureBlock() {
        val deDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val sigDate = datum.format(deDate)
        val ort = profil?.anschrift1?.trim()?.takeIf { it.isNotBlank() }
        val name = profil?.firmenname?.trim()?.takeIf { it.isNotBlank() }

        w.ensure(64f)
        w.y += 2f
    
        val line1 = if (!ort.isNullOrBlank()) "$ort, $sigDate" else sigDate
        w.textWrapped(line1, pT)
        if (!name.isNullOrBlank()) w.textWrapped(name, pT)

        w.y += 18f
        val sigW = 240f
        val yLine = w.y
        w.canvas.drawLine(marginL, yLine, marginL + sigW, yLine, pBorder)
        w.y += 16f
        w.textWrapped("Unterschrift", pSmall)
        w.y += 6f
    }

    renderSignatureBlock()

// --- Foto-Anhang (nur ausgewählte Fotos) ---
if (selectedFotosOrdered.isNotEmpty()) {
    val cfg = BtbPdfEinstellungenSpeicher(context).einstellungen.first()

    fun rasterDims(r: BtbPdfEinstellungenSpeicher.FotoRaster): Pair<Int, Int> = when (r) {
        BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_3x3 -> 3 to 3
        BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_2x6 -> 2 to 6
        BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_1x6 -> 1 to 6
        BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_1x1 -> 1 to 1
    }

    fun decodeScaled(path: String, targetW: Int, targetH: Int): Bitmap? {
        return ImageExifUtil.decodeSampledBitmapRespectingExif(
            path = path,
            reqW = targetW.coerceAtLeast(1),
            reqH = targetH.coerceAtLeast(1),
            preferredConfig = Bitmap.Config.RGB_565
        )
    }

    fun mapsUrl(lat: Double, lon: Double): String =
        "https://maps.google.com/?q=$lat,$lon&z=19"
    val optionalLabelsByBaustelle = HashMap<Long, List<String>>()
    for (baustelleId in selectedFotosOrdered.map { it.baustelleId }.distinct()) {
        optionalLabelsByBaustelle[baustelleId] = runCatching {
            FotoOptionalFelderSpeicher.laden(context, baustelleId).asList().take(3)
        }.getOrDefault(listOf("Info1", "Info2", "Info3"))
    }
    fun metaLines(f: de.tbhelfer.datenbank.FotoEntity, optionalLabels: List<String>): List<String> {
        val out = ArrayList<String>(8)

        // Reihenfolge: KM – Zeit – Ort – Firma – Text – GPS – Dateiname – Datum
        if (cfg.zeigeKm) {
            val v = f.km?.let { de.tbhelfer.util.KmAnzeige.wert(it) } ?: "–"
            out.add("KM: $v")
        }
        if (cfg.zeigeUhrzeit) {
            out.add("Zeit: ${timeOfFoto(f)}")
        }

        val ortId = f.baustellenKnotenId ?: 0L
        val ort = ortLabelById[ortId]
        if (ortId != 0L && !ort.isNullOrBlank()) {
            out.add("Ort: $ort")
        }

        val firma = f.firma.trim()
        if (firma.isNotBlank()) {
            out.add("Firma: $firma")
        }

        if (cfg.zeigeKurztext) {
            val t = f.kurztext.trim().ifBlank { "–" }
            out.add("Text: $t")
        }
        val alleOptionalLabels = listOf(
            "GVP / Mast",
            "AP / KOR",
            optionalLabels.getOrNull(0)?.trim().orEmpty().ifBlank { "Info1" },
            optionalLabels.getOrNull(1)?.trim().orEmpty().ifBlank { "Info2" },
            optionalLabels.getOrNull(2)?.trim().orEmpty().ifBlank { "Info3" }
        )
        listOf(f.info1Wert, f.info2Wert, f.info3Wert, f.info4Wert, f.info5Wert).forEachIndexed { index, value ->
            value?.trim()?.takeIf { it.isNotBlank() }?.let {
                val label = alleOptionalLabels.getOrNull(index).orEmpty().ifBlank { "Info${index + 1}" }
                out.add("$label: $it")
            }
        }
        if (cfg.zeigeGps) {
            out.add(
                if (f.gpsLat == 0.0 && f.gpsLon == 0.0) "GPS: –"
                else "GPS: ${String.format(Locale.US, "%.6f", f.gpsLat)}, ${String.format(Locale.US, "%.6f", f.gpsLon)}"
            )
        }
        // V6: Dateiname im PDF-Anhang nicht mehr ausgeben.
        // Die Datei steht in Bilderliste_Schicht/Gesamt; im PDF macht sie die Karten unnötig hoch
        // und kann Überschriften zu dicht an die vorherige Bildreihe schieben.
        if (cfg.zeigeDatum) {
            val dt = runCatching {
                java.time.Instant.ofEpochMilli(f.erstelltAmEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrDefault(entry.datumIso)
            out.add("Datum: $dt")
        }
        return out
    }

    val pFotoTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f; typeface = Typeface.DEFAULT_BOLD }
    val gap = 8f
    val mixedGap = 18f
    val textYPad = 10f
    val lineH = (pSmall.textSize * 1.25f)
    val appendixTopAfterTitle = marginTop + 24f

    val (gridCols, gridRows) = rasterDims(cfg.raster)
    val fixedCellW = (contentW - gap * (gridCols - 1)) / gridCols
    val fixedCellH = (((pageH - marginBottom) - appendixTopAfterTitle) - gap * (gridRows - 1)) / gridRows

    // RASTER_2x6 wird ab Patch45ZK als automatische Bildformat-Anordnung verwendet:
    // 2 Querformatbilder nebeneinander, 3 Hochformatbilder nebeneinander,
    // gemischte Reihe mit breiter Querformat- und schmalerer Hochformatkarte.
    val autoFotoLayout = cfg.raster == BtbPdfEinstellungenSpeicher.FotoRaster.RASTER_2x6
    val autoRowH = 315f

    val ausrichtungQuerCache = HashMap<Long, Boolean>()
    fun istQuerformat(f: de.tbhelfer.datenbank.FotoEntity): Boolean {
        return ausrichtungQuerCache.getOrPut(f.id) {
            val probe = decodeScaled(f.dateipfad, 96, 96)
            if (probe == null) {
                true
            } else {
                val result = probe.width >= probe.height
                probe.recycle()
                result
            }
        }
    }

    fun breakLongToken(token: String, maxW: Float): List<String> {
        if (pSmall.measureText(token) <= maxW) return listOf(token)
        val out = ArrayList<String>()
        var i = 0
        while (i < token.length) {
            var end = i + 1
            var lastBreak = -1
            while (end <= token.length) {
                val sub = token.substring(i, end)
                if (pSmall.measureText(sub) > maxW) break
                val ch = token[end - 1]
                if (ch == '/' || ch == '?' || ch == '&' || ch == '=' || ch == '-' || ch == '_' || ch == ',') {
                    lastBreak = end
                }
                end += 1
            }
            var cut = (end - 1).coerceAtLeast(i + 1)
            if (lastBreak > i + 1 && lastBreak < end) cut = lastBreak
            out.add(token.substring(i, cut))
            i = cut
        }
        return out
    }

    fun wrapLine(line: String, maxW: Float): List<String> {
        val tokens0 = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val tokens = ArrayList<String>()
        tokens0.forEach { t -> tokens.addAll(breakLongToken(t, maxW)) }

        val out = ArrayList<String>()
        var cur = ""
        for (t in tokens) {
            val test = if (cur.isEmpty()) t else "$cur $t"
            if (pSmall.measureText(test) <= maxW) {
                cur = test
            } else {
                if (cur.isNotEmpty()) out.add(cur)
                cur = t
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    fun drawPhotoCell(
        f: de.tbhelfer.datenbank.FotoEntity,
        x: Float,
        y: Float,
        cellW: Float,
        cellH: Float
    ) {
        // Cell background
        val rect = RectF(x, y, x + cellW, y + cellH)
        w.canvas.drawRoundRect(rect, 6f, 6f, pBox)

        val n = fotoNum[f.id] ?: 0
        w.canvas.drawText("Bild $n", x + 8f, y + 16f, pFotoTitle)

        val maxW = cellW - 16f
        val optionalLabels = optionalLabelsByBaustelle[f.baustelleId] ?: listOf("Info1", "Info2", "Info3")
        val wrappedMeta = metaLines(f, optionalLabels)
            .flatMap { line -> wrapLine(line, maxW) }

        // Für die Metadaten unten ausreichend Platz reservieren. Das Foto selbst wird
        // anschließend proportional in den verbleibenden Bereich eingepasst.
        val maxMetaLines = ((cellH * 0.40f) / lineH).toInt().coerceAtLeast(3)
        val visibleMeta = wrappedMeta.take(maxMetaLines)
        val metaH = visibleMeta.size * lineH + textYPad + 6f

        val imgTop = y + 22f
        val imgBottom = (y + cellH - metaH).coerceAtLeast(imgTop + 48f)
        val imgSlot = RectF(x + 6f, imgTop, x + cellW - 6f, imgBottom)

        val bmp = decodeScaled(
            f.dateipfad,
            imgSlot.width().toInt().coerceAtLeast(1),
            imgSlot.height().toInt().coerceAtLeast(1)
        )
        if (bmp != null) {
            // Seitenverhältnis immer beibehalten: kein Breitziehen und kein Abschneiden.
            val scale = minOf(
                imgSlot.width() / bmp.width.toFloat(),
                imgSlot.height() / bmp.height.toFloat()
            )
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            val left = imgSlot.centerX() - drawW / 2f
            val top = imgSlot.centerY() - drawH / 2f
            val drawRect = RectF(left, top, left + drawW, top + drawH)
            w.canvas.drawBitmap(bmp, null, drawRect, null)
            bmp.recycle()
        } else {
            w.canvas.drawText("(Bild fehlt)", x + 10f, imgTop + 30f, pSmall)
        }

        // Metadata
        var ty = imgSlot.bottom + textYPad
        visibleMeta.forEach { part ->
            if (ty > (y + cellH - 6f)) return@forEach
            w.canvas.drawText(part, x + 8f, ty, pSmall)
            ty += lineH
        }
    }

    data class FotoZeile(
        val fotos: List<de.tbhelfer.datenbank.FotoEntity>,
        val typ: String,
        val hoehe: Float
    )

    fun naechsteAutoZeile(
        fotos: List<de.tbhelfer.datenbank.FotoEntity>,
        start: Int
    ): FotoZeile {
        val first = fotos[start]
        val firstQuer = istQuerformat(first)

        if (firstQuer) {
            val second = fotos.getOrNull(start + 1)
            if (second == null) return FotoZeile(listOf(first), "EINZEL_QUER", autoRowH)
            return if (istQuerformat(second)) {
                FotoZeile(listOf(first, second), "QUER_QUER", autoRowH)
            } else {
                FotoZeile(listOf(first, second), "GEMISCHT", autoRowH)
            }
        }

        val second = fotos.getOrNull(start + 1)
        if (second != null && istQuerformat(second)) {
            return FotoZeile(listOf(first, second), "GEMISCHT", autoRowH)
        }

        val hoch = ArrayList<de.tbhelfer.datenbank.FotoEntity>(3)
        var i = start
        while (i < fotos.size && hoch.size < 3 && !istQuerformat(fotos[i])) {
            hoch.add(fotos[i])
            i += 1
        }
        return when (hoch.size) {
            3 -> FotoZeile(hoch, "HOCH_3", autoRowH)
            2 -> FotoZeile(hoch, "HOCH_2", autoRowH)
            else -> FotoZeile(hoch, "EINZEL_HOCH", autoRowH)
        }
    }

    fun drawAutoZeile(row: FotoZeile, rowY: Float) {
        when (row.typ) {
            "QUER_QUER" -> {
                val cw = (contentW - gap) / 2f
                drawPhotoCell(row.fotos[0], marginL, rowY, cw, row.hoehe)
                drawPhotoCell(row.fotos[1], marginL + cw + gap, rowY, cw, row.hoehe)
            }
            "HOCH_3" -> {
                val cw = (contentW - gap * 2f) / 3f
                row.fotos.forEachIndexed { idx, foto ->
                    drawPhotoCell(foto, marginL + idx * (cw + gap), rowY, cw, row.hoehe)
                }
            }
            "HOCH_2" -> {
                val cw = (contentW - gap) / 2f
                drawPhotoCell(row.fotos[0], marginL, rowY, cw, row.hoehe)
                drawPhotoCell(row.fotos[1], marginL + cw + gap, rowY, cw, row.hoehe)
            }
            "GEMISCHT" -> {
                val usable = contentW - mixedGap
                val querW = usable * 0.62f
                val hochW = usable - querW
                val firstQuer = istQuerformat(row.fotos[0])
                if (firstQuer) {
                    drawPhotoCell(row.fotos[0], marginL, rowY, querW, row.hoehe)
                    drawPhotoCell(row.fotos[1], marginL + querW + mixedGap, rowY, hochW, row.hoehe)
                } else {
                    drawPhotoCell(row.fotos[0], marginL, rowY, hochW, row.hoehe)
                    drawPhotoCell(row.fotos[1], marginL + hochW + mixedGap, rowY, querW, row.hoehe)
                }
            }
            "EINZEL_HOCH" -> {
                val cw = contentW * 0.46f
                val x = marginL + (contentW - cw) / 2f
                drawPhotoCell(row.fotos[0], x, rowY, cw, row.hoehe)
            }
            else -> { // EINZEL_QUER
                val cw = contentW * 0.68f
                val x = marginL + (contentW - cw) / 2f
                drawPhotoCell(row.fotos[0], x, rowY, cw, row.hoehe)
            }
        }
    }

    var appendixStarted = false
    var lastAppendixTypeTitle: String? = null
    var lastAppendixParentTitle: String? = null

    fun appendixOrtLabel(raw: String): String {
        val cleaned = raw.trim().removePrefix("—").trim()
        return when {
            cleaned.isBlank() -> "Hauptbaustelle"
            cleaned.equals("Nur Hauptbaustelle", ignoreCase = true) -> "Hauptbaustelle"
            cleaned.endsWith("Nur Hauptbaustelle", ignoreCase = true) -> "Hauptbaustelle"
            else -> cleaned
        }
    }

    fun beginAppendixGroup(
        typeTitle: String,
        ortTitleRaw: String,
        forceNewPage: Boolean,
        firstRowHeight: Float,
        depth: Int = 0,
        parentTitleRaw: String? = null
    ) {
        val ortTitle = appendixOrtLabel(ortTitleRaw)
        val parentTitle = parentTitleRaw?.let { appendixOrtLabel(it) }
            ?.takeIf { it.isNotBlank() && !it.equals("Hauptbaustelle", ignoreCase = true) }

        // V6 Layout-Regel:
        // - neue Unterüberschrift niemals an die vorherige Bildkarte kleben lassen
        // - Überschrift + mindestens eine Bildreihe zusammenhalten
        // - große Anhang-Überschrift nur am Typ-Start/neuer Seite wiederholen
        val preGroupGap = if (appendixStarted && !forceNewPage) 18f else 0f
        val typeTitleNeeded = if (lastAppendixTypeTitle != typeTitle) 26f else 0f
        val parentTitleNeeded = if (depth > 0 && parentTitle != null && parentTitle != lastAppendixParentTitle) 26f else 0f
        val needed = preGroupGap + typeTitleNeeded + parentTitleNeeded + 26f + 10f + firstRowHeight
        var newPageStarted = false
        if (!appendixStarted || forceNewPage || w.y + needed > pageH - marginBottom) {
            w.newPage()
            appendixStarted = true
            newPageStarted = true
            lastAppendixParentTitle = null
        } else if (preGroupGap > 0f) {
            w.y += preGroupGap
        }

        var drewTypeTitle = false
        if (newPageStarted || lastAppendixTypeTitle != typeTitle) {
            w.boxTitle("Anhang – $typeTitle")
            lastAppendixTypeTitle = typeTitle
            lastAppendixParentTitle = null
            drewTypeTitle = true
        }

        // Wenn die Unterbaustellen-Überschrift mit der ersten Bildreihe nicht mehr passt,
        // sauber auf neue Seite gehen. So bleibt die Überschrift nie allein unten stehen.
        if (w.y + parentTitleNeeded + 26f + 10f + firstRowHeight > pageH - marginBottom) {
            w.newPage()
            w.boxTitle("Anhang – $typeTitle")
            lastAppendixTypeTitle = typeTitle
            lastAppendixParentTitle = null
            drewTypeTitle = true
        }

        if (drewTypeTitle) {
            // Abstand zwischen großer Anhang-Überschrift und Unterbaustelle.
            w.y += 8f
        }

        if (depth > 0 && parentTitle != null && parentTitle != lastAppendixParentTitle) {
            w.subBoxTitle(parentTitle, pSub, indent = 0f)
            lastAppendixParentTitle = parentTitle
            w.y += 2f
        }
        val childIndent = if (depth > 0) 26f else 0f
        w.subBoxTitle(ortTitle, if (depth > 0) pSubChild else pSub, indent = childIndent)
        if (depth == 0) lastAppendixParentTitle = ortTitle
        // Sichtbarer Abstand zwischen Unterüberschrift und erster Bildreihe.
        w.y += 10f
    }

    fun drawGridFlow(
        typeTitle: String,
        ortTitle: String,
        fotosInSection: List<de.tbhelfer.datenbank.FotoEntity>,
        forceNewPageForTypeStart: Boolean,
        depth: Int = 0,
        parentTitle: String? = null
    ) {
        val fotosSorted = fotosInSection.sortedBy { fotoNum[it.id] ?: Int.MAX_VALUE }
        if (fotosSorted.isEmpty()) return

        var index = 0
        var firstPageOfThisGroup = true
        while (index < fotosSorted.size) {
            val firstRowHeight = if (autoFotoLayout) {
                naechsteAutoZeile(fotosSorted, index).hoehe
            } else {
                fixedCellH
            }

            beginAppendixGroup(
                typeTitle = typeTitle,
                ortTitleRaw = ortTitle,
                forceNewPage = forceNewPageForTypeStart && firstPageOfThisGroup,
                firstRowHeight = firstRowHeight,
                depth = depth,
                parentTitleRaw = parentTitle
            )
            firstPageOfThisGroup = false

            while (index < fotosSorted.size) {
                if (autoFotoLayout) {
                    val row = naechsteAutoZeile(fotosSorted, index)
                    if (w.y + row.hoehe > pageH - marginBottom) break
                    val rowY = w.y
                    drawAutoZeile(row, rowY)
                    index += row.fotos.size
                    w.y = rowY + row.hoehe + gap
                } else {
                    if (w.y + fixedCellH > pageH - marginBottom) break
                    val rowY = w.y
                    for (col in 0 until gridCols) {
                        if (index >= fotosSorted.size) break
                        val x = marginL + col * (fixedCellW + gap)
                        drawPhotoCell(fotosSorted[index], x, rowY, fixedCellW, fixedCellH)
                        index += 1
                    }
                    w.y = rowY + fixedCellH + gap
                }
            }
        }
    }

    // Fotos nach Bildtyp als eigene Anhänge (Reihenfolge wie BTB/PDF-Bildnummern).
    // Jede neue Bildtyp-Gruppe startet auf einem neuen Blatt. Innerhalb der Fotodokumentation
    // dürfen mehrere Unterbaustellen hintereinander auf derselben Seite stehen – getrennt durch Überschrift.
    fun anhangTitel(typ: Bildtyp): String = when (typ) {
        Bildtyp.ABNAHMEN -> "Abnahmen"
        Bildtyp.EINWEISUNG_UNTERWEISUNG -> "Einweisung / Unterweisung"
        Bildtyp.BESONDERHEITEN -> "Besonderheiten"
        Bildtyp.MAENGEL_SCHAEDEN -> "Mängel / Schäden"
        Bildtyp.FOTODOKUMENTATION -> "Fotodokumentation"
    }

    val groupedByType = selectedFotosOrdered.groupBy { it.bildtyp }

    anhangOrder.forEach { typ ->
        val list = groupedByType[typ].orEmpty()
        if (list.isEmpty()) return@forEach

        val byOrt = list.groupBy { it.baustellenKnotenId ?: 0L }
        val ortIdsOrdered = byOrt.keys.sortedWith(
            compareBy<Long> { ortOrderById[it] ?: Int.MAX_VALUE }.thenBy { it }
        )

        ortIdsOrdered.forEachIndexed { ortIndex, oid ->
            val ortFotos = byOrt[oid].orEmpty()
            if (ortFotos.isEmpty()) return@forEachIndexed

            val ortTitle = ortLabelById[oid] ?: "Ort $oid"
            val depth = (ortDepthById[oid] ?: 0).coerceAtLeast(0)
            val parentTitle = ortParentLabel(oid)

            drawGridFlow(
                typeTitle = anhangTitel(typ),
                ortTitle = ortTitle,
                fotosInSection = ortFotos,
                forceNewPageForTypeStart = ortIndex == 0,
                depth = depth,
                parentTitle = parentTitle
            )
        }
    }

}

w.finish()

        // --- Baseline + "geaendert"-Flag ---
        // Hinweis: Die Baseline (bautagebuch_original.json) wird NICHT automatisch beim PDF-Erstellen gesetzt,
        // sondern bewusst per "Tagesabschluss + PDF" im UI.
        val geaendert = baselineInfo.exists && baselineInfo.changedSinceBaseline

        // --- Ziel: App-Pictures/TB-Helfer/<Baustelle>/BTB/<datumIso>/ ---
        val outDir = BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)
        val geaendertIndex = if (geaendert) nextGeaendertIndex(outDir, safeBaustellenName, skey) else null
        val file = if (asDraft) {
            File(outDir, safeBaustellenName + "_entwurf_" + skey + ".pdf")
        } else {
            File(outDir, pdfDateiname(entry.baustelleName, skey, geaendert, geaendertIndex))
        }
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }


    private fun safeFolderName(input: String): String {
        return input.trim()
            .replace("..", ".")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(80)
            .ifBlank { "Unbekannt" }
    }

    /**
     * Löscht ein eventuell vorhandenes Entwurf-PDF für genau diese Schicht (Datum+Zeitfenster).
     */
    fun deleteEntwurfPdf(entry: BautagebuchEintrag): Boolean {
        val safe = safeFolderName(entry.baustelleName)
        val skey = schichtKey(entry)
        val outDir = BtbSpeicher.btbsFuerBaustelle(context, entry.baustelleId, entry.datumIso)
        val f = File(outDir, safe + "_entwurf_" + skey + ".pdf")
        return runCatching {
            if (f.exists()) f.delete() else false
        }.getOrDefault(false)
    }


fun openPdf(file: File) {
    val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".dateianbieter", file)
    val openIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(openIntent)
}

fun sharePdf(file: File) {

        // Manifest nutzt ${applicationId}.dateianbieter
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".dateianbieter", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "PDF teilen"))
    }
}