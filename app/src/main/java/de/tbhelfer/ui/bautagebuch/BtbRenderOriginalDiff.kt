package de.tbhelfer.ui.bautagebuch

import android.content.Context
import java.time.LocalDate

/**
 * Baut nur fuer den PDF-Lauf eine Darstellungsfassung des aktuellen BTB.
 *
 * Gelöschte Inhalte existieren naturgemäß nicht mehr im aktuellen Datensatz. Damit der bestehende
 * Renderer sie deutlich als rote Änderung anzeigen kann, werden echte Löschungen aus dem
 * unveränderlichen ersten Original temporär als "(entfernt) ..." ergänzt. Der persistierte BTB
 * wird dabei niemals verändert.
 */
object BtbRenderOriginalDiff {

    // Unsichtbares Abschlusszeichen verhindert, dass der bestehende Inline-Diff nur den Präfix
    // "(entfernt)" rot färbt; visuell bleibt die gewünschte Zeile unverändert.
    private const val DIFF_END = "\u200B"

    fun aufbereiten(context: Context, current: BautagebuchEintrag): BautagebuchEintrag {
        if (current.btbId.isNullOrBlank()) return current
        val date = runCatching { LocalDate.parse(current.datumIso) }.getOrNull() ?: return current
        val original = BautagebuchSpeicher.ladenBaseline(
            context,
            current.baustelleId,
            current.baustelleName,
            date,
            current.schicht
        ) ?: return current

        val legacyWorkMode = current.arbeiten.items.isEmpty() && original.arbeiten.items.isEmpty()
        val removedNotes = if (legacyWorkMode) {
            echteLoeschungen(
                original.notizen.orEmpty().lines().filter { fachText(it).isNotBlank() },
                current.notizen.orEmpty().lines().filter { fachText(it).isNotBlank() },
                ::fachText,
                ::zeitPraefix
            )
        } else {
            emptyList()
        }
        val renderedNotes = if (legacyWorkMode) {
            appendLines(current.notizen, removedNotes.map(::entferntZeile))
        } else {
            current.notizen
        }

        val originalWork = if (original.arbeiten.items.isNotEmpty()) {
            original.arbeiten
        } else {
            legacyNotesAsSection(original.notizen)
        }
        val currentWork = if (current.arbeiten.items.isNotEmpty()) {
            current.arbeiten
        } else {
            legacyNotesAsSection(current.notizen)
        }

        val renderedFirmen = current.firmen.map { curFirm ->
            val origFirm = original.firmen.firstOrNull { it.firmaId == curFirm.firmaId }
                ?: original.firmen.firstOrNull { it.name.trim().equals(curFirm.name.trim(), ignoreCase = true) }
                ?: return@map curFirm
            val removed = echteLoeschungen(
                origFirm.leistungen,
                curFirm.leistungen,
                { l -> fachText(l.text) },
                { l -> l.uhrzeit?.trim().orEmpty() }
            )
            if (removed.isEmpty()) curFirm else curFirm.copy(
                leistungen = curFirm.leistungen + removed.map { old ->
                    old.copy(text = entferntZeile(old.text))
                }
            )
        }

        return current.copy(
            notizen = renderedNotes,
            arbeiten = renderBlock(originalWork, currentWork),
            firmen = renderedFirmen,
            abnahmen = renderBlock(original.abnahmen, current.abnahmen),
            einweisungUnterweisung = renderBlock(original.einweisungUnterweisung, current.einweisungUnterweisung),
            besonderheiten = renderBlock(original.besonderheiten, current.besonderheiten),
            maengelSchaeden = renderBlock(original.maengelSchaeden, current.maengelSchaeden)
        )
    }

    private fun legacyNotesAsSection(raw: String?): SectionBlock = SectionBlock(
        raw.orEmpty().lines().mapNotNull { line ->
            val text = line.trim()
            if (text.isBlank()) null else SectionItem(text = text)
        }
    )

    private fun renderBlock(original: SectionBlock, current: SectionBlock): SectionBlock {
        val removed = echteLoeschungen(
            original.items,
            current.items,
            { item -> blockKey(item) },
            { item -> item.uhrzeit?.trim().orEmpty() }
        )
        if (removed.isEmpty()) return current
        return current.copy(
            items = current.items + removed.map { old ->
                old.copy(text = entferntZeile(old.text))
            }
        )
    }

    private fun blockKey(item: SectionItem): String = buildString {
        append(fachText(item.text))
        append('|')
        append(item.kmVon?.toString().orEmpty())
        append('|')
        append(item.kmBis?.toString().orEmpty())
    }

    /**
     * Unterscheidet echte Löschung von normaler Bearbeitung: Ein weggefallener Originaleintrag,
     * zu dem gleichzeitig ein plausibel geänderter neuer Eintrag existiert, wird als Änderung
     * behandelt und nicht zusätzlich als Löschung ausgegeben.
     */
    private fun <T> echteLoeschungen(
        original: List<T>,
        current: List<T>,
        textOf: (T) -> String,
        timeOf: (T) -> String
    ): List<T> {
        val originalNorm = original.map { normalize(textOf(it)) }
        val currentNorm = current.map { normalize(textOf(it)) }
        val currentRemaining = current.indices
            .filter { currentNorm[it] !in originalNorm }
            .toMutableSet()

        val removedIndices = original.indices.filter { originalNorm[it] !in currentNorm }
        val result = mutableListOf<T>()

        removedIndices.forEach { oi ->
            val oldText = originalNorm[oi]
            val oldTime = normalize(timeOf(original[oi]))

            val match = currentRemaining
                .map { ci ->
                    val newText = currentNorm[ci]
                    val newTime = normalize(timeOf(current[ci]))
                    val score = when {
                        oldTime.isNotBlank() && oldTime == newTime -> 20_000 + similarity(oldText, newText)
                        else -> similarity(oldText, newText)
                    }
                    ci to score
                }
                .filter { (_, score) -> score >= 4 }
                .maxByOrNull { it.second }

            if (match != null) {
                currentRemaining.remove(match.first)
            } else {
                result += original[oi]
            }
        }
        return result
    }

    private fun similarity(a: String, b: String): Int {
        if (a.isBlank() || b.isBlank()) return 0
        if (a == b) return 10_000
        if (a.contains(b) || b.contains(a)) return 5_000 + minOf(a.length, b.length)
        var prefix = 0
        val max = minOf(a.length, b.length)
        while (prefix < max && a[prefix] == b[prefix]) prefix++
        return prefix
    }

    private fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /** Marker/Bullets dienen der Darstellung/Ort-Zuordnung und nicht der Ähnlichkeitsprüfung. */
    private fun fachText(raw: String): String {
        var text = raw.trim()
        text = text.replace(Regex("^[-•·*▪]+\\s*"), "")
        text = text.replace(Regex("^@ORT\\s*=\\s*\\d+\\s*", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("^@ALLGEMEIN\\b\\s*", RegexOption.IGNORE_CASE), "")
        return normalize(text)
    }

    private fun zeitPraefix(raw: String): String =
        Regex("^(?:[-•·*▪]+\\s*)?(?:@ORT\\s*=\\s*\\d+\\s*)?(?:@ALLGEMEIN\\b\\s*)?(\\d{1,2}:\\d{2})", RegexOption.IGNORE_CASE)
            .find(raw.trim())?.groupValues?.getOrNull(1).orEmpty()

    /** Ort-/Allgemein-Marker bleiben vor dem sichtbaren Entfernt-Text erhalten. */
    private fun entferntZeile(raw: String): String {
        val trimmed = raw.trim()
        val marker = Regex("^(@ORT\\s*=\\s*\\d+|@ALLGEMEIN\\b)\\s*(.*)$", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed)
        return if (marker != null) {
            val prefix = marker.groupValues[1]
            val body = marker.groupValues[2].trim()
            "$prefix (entfernt) $body$DIFF_END"
        } else {
            "(entfernt) $trimmed$DIFF_END"
        }
    }

    private fun appendLines(current: String?, additions: List<String>): String? {
        if (additions.isEmpty()) return current
        val base = current.orEmpty().trimEnd()
        return buildString {
            if (base.isNotBlank()) {
                append(base)
                append('\n')
            }
            append(additions.joinToString("\n"))
        }
    }
}
