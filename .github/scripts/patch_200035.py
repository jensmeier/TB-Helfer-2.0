from pathlib import Path
import traceback

SOURCE = Path("app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt")
RESULT = Path(".github/patch_200035_result.txt")


def patch() -> None:
    s = SOURCE.read_text(encoding="utf-8")

    # Idempotent: a successful rerun is harmless.
    if "forceRed: Boolean = false" in s and "renderRemovedPhotoBlock" in s:
        RESULT.write_text("PATCH_200035_ALREADY_APPLIED\n", encoding="utf-8")
        return

    def once(old: str, new: str, label: str) -> None:
        nonlocal s
        count = s.count(old)
        if count != 1:
            raise RuntimeError(f"{label}: expected 1 occurrence, found {count}")
        s = s.replace(old, new, 1)

    def exact(old: str, new: str, expected: int, label: str) -> None:
        nonlocal s
        count = s.count(old)
        if count != expected:
            raise RuntimeError(f"{label}: expected {expected} occurrences, found {count}")
        s = s.replace(old, new)

    once(
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
'''        // Moderne BTBs: die stabile btbId ist stärker als Datum/Uhrzeit.
        // Nur echte Legacy-BTBs ohne btbId verwenden weiterhin das historische Zeitfenster.
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

    once(
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

        // Foto-Bestand des ersten erfolgreichen Abschlusses ist Teil des unveränderlichen Originals.
        // Die IDs reichen für die Diff-Entscheidung; Original-JPGs werden nicht verändert.
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

    exact(
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
        val forceRed: Boolean = false
    )
''',
        2,
        "PdfEvent forceRed"
    )

    exact(
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
        "photo Row isNew"
    )

    exact(
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
        2,
        "photo Row construction"
    )

    exact(
'''        val grouped = rows.groupBy { Triple(it.firma, it.ortId, it.text) }
grouped.entries.forEach { (k, list) ->
    val (firma, ortId, txt) = k
''',
'''        // Original und Nachtrag mit gleichem Text nicht vermischen: der Originalanteil bleibt
        // schwarz, der nach dem ersten Abschluss hinzugekommene Anteil wird rot.
        val grouped = rows.groupBy { Pair(Triple(it.firma, it.ortId, it.text), it.isNew) }
grouped.entries.forEach { (k, list) ->
    val (baseKey, isNewGroup) = k
    val (firma, ortId, txt) = baseKey
''',
        2,
        "split original/new photo groups"
    )

    exact(
'''    out.add(PdfEvent(sortT, kind, seq++, ortId, firma, line))
''',
'''    out.add(
        PdfEvent(
            sortTime = sortT,
            kind = kind,
            seq = seq++,
            ortId = ortId,
            firma = firma,
            line = line,
            forceRed = isNewGroup
        )
    )
''',
        2,
        "photo PdfEvent forceRed"
    )

    once(
'''                            arbeitenChange.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
''',
'''                            e.forceRed -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
                            arbeitenChange.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
''',
        "point4 force-red row"
    )

    once(
'''                                change.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
''',
'''                                e.forceRed -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
                                change.redKeys.contains(key) -> w.bulletWrapped(e.line, indent = bulletIndent, paint = pTRed)
''',
        "section force-red row"
    )

    # Fachliche Löschungen sind Änderungen gegenüber dem ersten Original und deshalb rot.
    once(
'''            removed.forEach { ln ->
                w.bulletWrapped("(entfernt) $ln", indent = 8f, paint = pTGrey)
            }
''',
'''            removed.forEach { ln ->
                w.bulletWrapped("(entfernt) $ln", indent = 8f, paint = pTRed)
            }
''',
        "removed fachliche rows red"
    )

    once(
'''        // ortLabelById / ortOrderById sind oben aus dem Baustellenbaum gebaut.


// --- BTB Inhalte (1:1) ---
''',
'''        // ortLabelById / ortOrderById sind oben aus dem Baustellenbaum gebaut.

        /**
         * War ein Foto beim ersten Abschluss im BTB und wird später aus der BTB-Auswahl entfernt,
         * darf seine fachliche Zeile im Folge-PDF nicht lautlos verschwinden. Das echte Foto bleibt
         * unverändert gespeichert; hier wird ausschließlich die frühere Referenz rot dokumentiert.
         */
        fun renderRemovedPhotoBlock(typ: Bildtyp) {
            val removed = entfernteFotoIdsForDiff
                .mapNotNull { id -> fotosAll.firstOrNull { it.id == id } }
                .filter { it.bildtyp == typ }
                .sortedBy { it.erstelltAmEpochMs }
                .map { f ->
                    val time = timeOfFoto(f)
                    val km = f.km?.let { de.tbhelfer.util.KmAnzeige.wert(it) }?.let { "KM $it" }
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
                .filter { it.isNotBlank() }
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

    once(
'''    drawFirmaGrouped(events, emptyText = "Keine Arbeiten eingetragen.")

    renderRemovedBlock(arbeitenChange) { key ->
''',
'''    drawFirmaGrouped(events, emptyText = "Keine Arbeiten eingetragen.")
    renderRemovedPhotoBlock(Bildtyp.FOTODOKUMENTATION)

    renderRemovedBlock(arbeitenChange) { key ->
''',
        "point4 removed photo rows"
    )

    once(
'''    drawFirmaGrouped(events, emptyText)

    renderRemovedBlock(change) { key ->
''',
'''    drawFirmaGrouped(events, emptyText)
    renderRemovedPhotoBlock(fotoType)

    renderRemovedBlock(change) { key ->
''',
        "section removed photo rows"
    )

    SOURCE.write_text(s, encoding="utf-8")
    RESULT.write_text("PATCH_200035_OK\n", encoding="utf-8")


try:
    patch()
except Exception:
    RESULT.write_text("PATCH_200035_FAILED\n" + traceback.format_exc(), encoding="utf-8")
    # Intentionally do not fail the shell here: the workflow commits the diagnostic file so the
    # exact failed assertion remains inspectable even when Actions logs are unavailable.
