from pathlib import Path

path = Path("app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt")
s = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 occurrence, found {count}")
    s = s.replace(old, new, 1)


def replace_count(old: str, new: str, expected: int, label: str) -> None:
    global s
    count = s.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} occurrences, found {count}")
    s = s.replace(old, new)


replace_once(
'''        // Fotos in der Schicht (inkl. über Mitternacht)
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
''',
'''        // Moderne BTBs werden ausschließlich über ihre stabile btbId gefiltert. Das Arbeitszeitfenster
        // ist dafür nur Anzeige und darf ein explizit zugeordnetes Foto niemals wieder verlieren.
        // Nur echte Legacy-BTBs ohne btbId behalten den historischen Zeitfenster-Fallback.
        val modernBtbId = entry.btbId?.trim().orEmpty()
        val fotosAll = if (entry.includeFotos) {
            val all = fotoRepository.fotosFuerBaustelleOnce(entry.baustelleId)
            val scoped = if (modernBtbId.isNotBlank()) {
                all.filter { BtbFotoZuordnungSpeicher.btbId(context, it.id) == modernBtbId }
            } else {
                val (from, to) = shiftWindowEpochMs(datum, entry.startUhrzeit, entry.endeUhrzeit)
                all.filter { it.erstelltAmEpochMs in from until to }
            }
            scoped.sortedBy { it.erstelltAmEpochMs }
        } else emptyList()
''',
"modern photo scope"
)

replace_once(
'''        val fotos = fotosAll.filter { f ->
            val ex = excludedByType[f.bildtyp.name]
            ex == null || !ex.contains(f.id)
        }

        // BTB PDFs (optional)
''',
'''        val fotos = fotosAll.filter { f ->
            val ex = excludedByType[f.bildtyp.name]
            ex == null || !ex.contains(f.id)
        }

        // Foto-Diff gehört fachlich zum unveränderlichen ersten BTB-Abschluss.
        // IDs reichen für neu/entfernt; die sichtbare Zeile wird weiter aus dem Foto selbst aufgebaut.
        val hatFotoOriginalForDiff = BtbOriginalFotoSpeicher.hatOriginal(context, entry.btbId)
        val originalFotoIdsForDiff = if (hatFotoOriginalForDiff) {
            BtbOriginalFotoSpeicher.originalIds(context, entry.btbId)
        } else emptySet()
        val aktuelleFotoIdsForDiff = fotos.map { it.id }.toSet()
        val neueFotoIdsForDiff = if (hatFotoOriginalForDiff) {
            aktuelleFotoIdsForDiff - originalFotoIdsForDiff
        } else emptySet()
        val entfernteFotoIdsForDiff = if (hatFotoOriginalForDiff) {
            originalFotoIdsForDiff - aktuelleFotoIdsForDiff
        } else emptySet()

        // BTB PDFs (optional)
''',
"photo diff sets"
)

replace_count(
'''    data class PdfEvent(
        val sortTime: LocalTime,
        val kind: Int,
        val seq: Int,
        val ortId: Long,
        var firma: String?,
        val line: String
    )
''',
'''    data class PdfEvent(
        val sortTime: LocalTime,
        val kind: Int,
        val seq: Int,
        val ortId: Long,
        var firma: String?,
        val line: String,
        val fotoIds: Set<Long> = emptySet(),
        val forceRed: Boolean = false
    )
''',
2,
"PdfEvent photo diff fields"
)

replace_count(
'''            val id: Long,
            val epochMs: Long
        )
''',
'''            val id: Long,
            val epochMs: Long,
            val isNew: Boolean
        )
''',
2,
"photo row isNew"
)

replace_once(
'''                id = f.id,
                epochMs = f.erstelltAmEpochMs
            )
        }

        fun bildSuffix(numsIn: List<Int>): String {
''',
'''                id = f.id,
                epochMs = f.erstelltAmEpochMs,
                isNew = hatFotoOriginalForDiff && f.id in neueFotoIdsForDiff
            )
        }

        fun bildSuffix(numsIn: List<Int>): String {
''',
"arbeiten row construction"
)

# The standard-section photo row has the same tail after its own Row constructor.
replace_once(
'''                id = f.id,
                epochMs = f.erstelltAmEpochMs
            )
        }

        fun bildSuffix(numsIn: List<Int>): String {
''',
'''                id = f.id,
                epochMs = f.erstelltAmEpochMs,
                isNew = hatFotoOriginalForDiff && f.id in neueFotoIdsForDiff
            )
        }

        fun bildSuffix(numsIn: List<Int>): String {
''',
"standard row construction"
)

replace_count(
'''        val grouped = rows.groupBy { Triple(it.firma, it.ortId, it.text) }
grouped.entries.forEach { (k, list) ->
    val (firma, ortId, txt) = k
''',
'''        // Original- und nachträgliche Fotos mit gleichem Text bewusst nicht zu einer Zeile
        // verschmelzen: so kann der Originalanteil schwarz und nur der neue Anteil rot bleiben.
        val grouped = rows.groupBy { Pair(Triple(it.firma, it.ortId, it.text), it.isNew) }
grouped.entries.forEach { (k, list) ->
    val (baseKey, isNewGroup) = k
    val (firma, ortId, txt) = baseKey
''',
2,
"split original/new photo groups"
)

replace_count(
'''    out.add(PdfEvent(sortT, kind, seq++, ortId, firma, line))
''',
'''    val eventFotoIds = sorted.map { it.id }.toSet()
    out.add(
        PdfEvent(
            sortTime = sortT,
            kind = kind,
            seq = seq++,
            ortId = ortId,
            firma = firma,
            line = line,
            fotoIds = eventFotoIds,
            forceRed = isNewGroup
        )
    )
''',
2,
"photo PdfEvent creation"
)

replace_count(
'''                arbeitenChange.redKeys.contains(k)
''',
'''                e.forceRed || arbeitenChange.redKeys.contains(k)
''',
2,
"arbeiten red group headers"
)
replace_once(
'''                            arbeitenChange.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
''',
'''                            e.forceRed -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
                            arbeitenChange.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
''',
"arbeiten force-red row"
)

replace_count(
'''                    change.redKeys.contains(k)
''',
'''                    e.forceRed || change.redKeys.contains(k)
''',
2,
"standard red group headers"
)
replace_once(
'''                                change.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
''',
'''                                e.forceRed -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
                                change.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
''',
"standard force-red row"
)

replace_once(
'''            removed.forEach { ln ->
                w.bulletWrapped("(entfernt) $ln", indent = 8f, paint = pTGrey)
            }
''',
'''            removed.forEach { ln ->
                w.bulletWrapped("(entfernt) $ln", indent = 8f, paint = pTRed)
            }
''',
"fachliche removals red"
)

replace_once(
'''        // ortLabelById / ortOrderById sind oben aus dem Baustellenbaum gebaut.


// --- BTB Inhalte (1:1) ---
''',
'''        // ortLabelById / ortOrderById sind oben aus dem Baustellenbaum gebaut.

        /**
         * Fotos, die beim ersten Abschluss zum BTB gehörten und später aus der Auswahl entfernt
         * wurden, dürfen im Folge-PDF nicht lautlos verschwinden. Solange das echte Baustellenfoto
         * noch vorhanden ist, kann die frühere fachliche Zeile vollständig lesbar rekonstruiert
         * werden. Das Original-JPG selbst wird dabei nicht verändert oder gelöscht.
         */
        fun renderRemovedPhotoBlock(typ: Bildtyp) {
            val removed = entfernteFotoIdsForDiff
                .mapNotNull { id -> fotosAll.firstOrNull { it.id == id } }
                .filter { it.bildtyp == typ }
                .sortedBy { it.erstelltAmEpochMs }
                .map { f ->
                    val time = timeOfFoto(f)
                    val km = f.km?.let { de.tbhelfer.util.KmAnzeige.wert(it) }
                        ?.let { "KM $it" }
                    val text = btbTextOfFoto(f).trim().ifBlank { "Foto" }
                    val firma = f.firma.trim()
                    val ort = ortLabelById[f.baustellenKnotenId ?: 0L].orEmpty()
                    val head = when {
                        firma.isNotBlank() && ort.isNotBlank() -> "$firma: $ort"
                        firma.isNotBlank() -> "$firma:"
                        ort.isNotBlank() -> ort
                        else -> ""
                    }
                    val body = listOfNotNull(time, km, text).joinToString(" ")
                    if (head.isBlank()) body else "$head $body".trim()
                }
                .distinct()

            if (removed.isEmpty()) return
            w.y += 2f
            w.textWrapped("Änderungen (entfernt):", pSmall, indent = 8f)
            removed.forEach { line ->
                w.bulletWrapped("(entfernt) $line", indent = 8f, paint = pTRed)
            }
            w.y += 4f
        }


// --- BTB Inhalte (1:1) ---
''',
"removed photo renderer"
)

replace_once(
'''    drawFirmaGrouped(events, emptyText = "Keine Arbeiten eingetragen.")

    renderRemovedBlock(arbeitenChange) { key ->
''',
'''    drawFirmaGrouped(events, emptyText = "Keine Arbeiten eingetragen.")
    renderRemovedPhotoBlock(Bildtyp.FOTODOKUMENTATION)

    renderRemovedBlock(arbeitenChange) { key ->
''',
"point4 removed photos"
)

replace_once(
'''    drawFirmaGrouped(events, emptyText)

    renderRemovedBlock(change) { key ->
''',
'''    drawFirmaGrouped(events, emptyText)
    renderRemovedPhotoBlock(fotoType)

    renderRemovedBlock(change) { key ->
''',
"standard removed photos"
)

path.write_text(s, encoding="utf-8")
print("PATCH_200035_OK")
