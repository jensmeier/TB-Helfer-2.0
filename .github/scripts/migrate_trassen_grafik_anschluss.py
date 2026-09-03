from pathlib import Path

path = Path("app/src/main/java/de/tbhelfer/ui/importexport/TrassenGrafikView.kt")
text = path.read_text(encoding="utf-8")

old_import = "import de.tbhelfer.vermessung.TrassenKmStatusLogik\n"
new_import = (
    "import de.tbhelfer.vermessung.TrassenAnschlussLogik\n"
    "import de.tbhelfer.vermessung.TrassenKmStatusLogik\n"
)
if "import de.tbhelfer.vermessung.TrassenAnschlussLogik\n" not in text:
    if text.count(old_import) != 1:
        raise SystemExit("Erwartete Importstelle fuer TrassenKmStatusLogik nicht eindeutig gefunden.")
    text = text.replace(old_import, new_import, 1)

start_marker = "    private fun berechneAnschlussKnoten(achsen: List<TrassenGrafikAchse>): List<TrassenGrafikKnoten> {\n"
end_marker = "    private fun distanzZuAchse(px: Float, py: Float, achse: TrassenGrafikAchse): Float {\n"
next_marker = "    private fun abstand(x1: Float, y1: Float, x2: Float, y2: Float): Float {\n"

if text.count(start_marker) != 1 or text.count(end_marker) != 1 or text.count(next_marker) != 1:
    raise SystemExit("Anschlussfunktionen in TrassenGrafikView nicht eindeutig gefunden.")

start = text.index(start_marker)
old_distanz_start = text.index(end_marker, start)
abstand_start = text.index(next_marker, old_distanz_start)

new_function = '''    private fun berechneAnschlussKnoten(achsen: List<TrassenGrafikAchse>): List<TrassenGrafikKnoten> {
        if (achsen.size < 2) return emptyList()
        val toleranzM = 2.5f
        val fachAchsen = achsen.map { achse ->
            TrassenAnschlussLogik.Achse(
                id = achse.achseId,
                name = achse.label.takeIf(String::isNotBlank) ?: achse.name,
                achsTyp = achse.achsTyp,
                punkte = achse.punkte.map { punkt ->
                    TrassenAnschlussLogik.Punkt(
                        station = punkt.station,
                        x = punkt.x.toDouble(),
                        y = punkt.y.toDouble()
                    )
                }
            )
        }

        val roh = TrassenAnschlussLogik.findeAnschluesse(
            achsen = fachAchsen,
            toleranzM = toleranzM.toDouble()
        ).map { anschluss ->
            TrassenGrafikKnoten(
                x = anschluss.x.toFloat(),
                y = anschluss.y.toFloat(),
                labels = listOf(anschluss.a.achseName, anschluss.b.achseName).distinct()
            )
        }

        // Mehrere paarweise Anschluesse koennen denselben realen Weichen-/DKW-Knoten
        // beschreiben. Fuer die Anzeige bleiben sie zu einem gemeinsamen Diamanten gebuendelt.
        val zusammen = mutableListOf<TrassenGrafikKnoten>()
        roh.forEach { kandidat ->
            val index = zusammen.indexOfFirst { abstand(it.x, it.y, kandidat.x, kandidat.y) <= toleranzM }
            if (index < 0) {
                zusammen += kandidat
            } else {
                val alt = zusammen[index]
                zusammen[index] = TrassenGrafikKnoten(
                    x = (alt.x + kandidat.x) / 2f,
                    y = (alt.y + kandidat.y) / 2f,
                    labels = (alt.labels + kandidat.labels).distinct()
                )
            }
        }
        return zusammen
    }

'''

# Ersetzt die alte Anschlussberechnung und entfernt nur den danach unbenutzten
# lokalen distanzZuAchse-Helfer. Der allgemeine Segmentabstands-Helfer bleibt erhalten.
text = text[:start] + new_function + text[abstand_start:]
path.write_text(text, encoding="utf-8")
print("TrassenGrafikView nutzt jetzt TrassenAnschlussLogik.")
