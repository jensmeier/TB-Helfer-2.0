from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")


# Version bump.
gradle_path = "app/build.gradle.kts"
gradle = read(gradle_path)
if "versionCode = 200004" not in gradle:
    raise SystemExit("expected versionCode 200004 not found")
gradle = gradle.replace(
    "// TB-Helfer 2.0: 200004 -> sichtbare Teststand-Nummer 004.\n        // Jeder neue auf dem Handy testbare Stand erhöht diesen Wert um 1.\n        versionCode = 200004",
    "// TB-Helfer 2.0: 200005 -> sichtbare Teststand-Nummer 005.\n        // Jeder neue auf dem Handy testbare Stand erhöht diesen Wert um 1.\n        versionCode = 200005",
    1,
)
write(gradle_path, gradle)

# Version history.
history_path = "VERSION_HISTORY.md"
history = read(history_path)
if "## 2.0 (005)" not in history:
    marker = "## 2.0 (004)"
    if marker not in history:
        raise SystemExit("004 history marker missing")
    entry = """## 2.0 (005)\n\nAndroid versionCode: `200005`\n\nWeichen-/DKW-Flächen folgen der gewählten TRA statt den Roh-GVP-Koordinaten:\n\n- GVP/Festpunkte bleiben reine Vermessungsreferenzen; ihre Originalkoordinaten werden nicht mehr als Eckpunkte der türkisfarbenen Objektfläche verwendet.\n- WA/WE/WEZ und A/B/C/D werden für die Darstellung ausschließlich aus gespeicherter Ziel-TRA + Station auf die TRA-Geometrie gesetzt.\n- Liegen zwei benachbarte Eckpunkte auf derselben TRA, folgt die türkisfarbene Kontur exakt den Zwischenpunkten dieser TRA und damit auch Bögen/Kurven.\n- Bei Punkten auf unterschiedlichen TRA werden die auf die jeweiligen TRA projizierten Eckpunkte verbunden; Roh-GVP-Koordinaten bleiben außen vor.\n- GPS-Punkte verwenden denselben Achse-zuerst-Weg: 20-s-GNSS-Messung, Prüfung gegen die gewählte TRA und Speicherung von GPS, Achse, Station und Abstand.\n- Blau bleibt temporäre Auswahl; gespeicherte Weichen/DKW bleiben türkis.\n- Keine Room-Migration.\n\nStatus: in Entwicklung / CI-Prüfung vor Handytest.\n\n"""
    history = history.replace(marker, entry + marker, 1)
write(history_path, history)

# Replace persistent object geometry builder.
fragment_path = "app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt"
fragment = read(fragment_path)
pattern = re.compile(
    r"    private fun streckenObjektFlaechen\(\n.*?\n    private fun TextView\.textStyleBold\(\)",
    re.S,
)
new_function = r'''    private fun streckenObjektFlaechen(
        objekte: List<StreckenObjektSpeicher.Objekt>,
        achsen: List<TrassenGrafikAchse>
    ): List<TrassenGrafikObjektFlaeche> {
        val achsenById = achsen.groupBy { it.achseId }

        fun punktAufAchse(achse: TrassenGrafikAchse, station: Double): Pair<Float, Float>? {
            val ps = achse.punkte.sortedBy { it.station }
            if (ps.isEmpty()) return null
            if (ps.size == 1) return ps.first().x to ps.first().y
            if (station <= ps.first().station) return ps.first().x to ps.first().y
            if (station >= ps.last().station) return ps.last().x to ps.last().y
            for (i in 0 until ps.lastIndex) {
                val a = ps[i]
                val z = ps[i + 1]
                if (station + 1e-9 < a.station || station - 1e-9 > z.station) continue
                val ds = z.station - a.station
                val t = if (kotlin.math.abs(ds) < 1e-9) 0.0 else ((station - a.station) / ds).coerceIn(0.0, 1.0)
                return (a.x + (z.x - a.x) * t.toFloat()) to
                    (a.y + (z.y - a.y) * t.toFloat())
            }
            return ps.minByOrNull { kotlin.math.abs(it.station - station) }?.let { it.x to it.y }
        }

        fun passendeAchse(p: StreckenObjektSpeicher.Punkt): TrassenGrafikAchse? {
            val achseId = p.achseId ?: return null
            val station = p.station ?: return null
            val kandidaten = achsenById[achseId].orEmpty()
            return kandidaten.firstOrNull { achse ->
                val ps = achse.punkte
                ps.size >= 2 && station >= ps.minOf { it.station } - 0.01 && station <= ps.maxOf { it.station } + 0.01
            } ?: kandidaten.firstOrNull { it.punkte.isNotEmpty() }
        }

        fun eckpunktAufTra(p: StreckenObjektSpeicher.Punkt): TrassenGrafikObjektPunkt? {
            val station = p.station ?: return null
            val achse = passendeAchse(p) ?: return null
            val xy = punktAufAchse(achse, station) ?: return null
            return TrassenGrafikObjektPunkt(
                rolle = p.rolle.uppercase(Locale.GERMANY),
                x = xy.first,
                y = xy.second
            )
        }

        fun segmentZwischen(
            von: StreckenObjektSpeicher.Punkt,
            bis: StreckenObjektSpeicher.Punkt
        ): List<TrassenGrafikObjektPunkt> {
            val vonEcke = eckpunktAufTra(von) ?: return emptyList()
            val bisEcke = eckpunktAufTra(bis) ?: return emptyList()
            val achseId = von.achseId
            val vonStation = von.station
            val bisStation = bis.station
            if (achseId == null || achseId != bis.achseId || vonStation == null || bisStation == null) {
                return listOf(vonEcke, bisEcke)
            }

            val achse = passendeAchse(von) ?: return listOf(vonEcke, bisEcke)
            val minStation = minOf(vonStation, bisStation)
            val maxStation = maxOf(vonStation, bisStation)
            val innen = achse.punkte
                .filter { it.station > minStation + 1e-6 && it.station < maxStation - 1e-6 }
                .sortedBy { it.station }
                .let { if (bisStation >= vonStation) it else it.reversed() }

            return buildList {
                add(vonEcke)
                innen.forEach { p ->
                    add(TrassenGrafikObjektPunkt(rolle = "", x = p.x, y = p.y))
                }
                add(bisEcke)
            }
        }

        return objekte.mapNotNull { objekt ->
            val rollen = if (objekt.typ == StreckenObjektSpeicher.Typ.WEICHE) {
                listOf("WA", "WE", "WEZ")
            } else {
                listOf("A", "B", "C", "D")
            }
            val byRole = objekt.punkte.associateBy { it.rolle.uppercase(Locale.GERMANY) }
            val ecken = rollen.mapNotNull { rolle ->
                val p = byRole[rolle] ?: return@mapNotNull null
                val ecke = eckpunktAufTra(p) ?: return@mapNotNull null
                p to ecke
            }
            if (ecken.size != rollen.size) return@mapNotNull null

            val cx = ecken.map { it.second.x.toDouble() }.average()
            val cy = ecken.map { it.second.y.toDouble() }.average()
            val geordnet = ecken.sortedBy { (_, p) ->
                kotlin.math.atan2(p.y.toDouble() - cy, p.x.toDouble() - cx)
            }

            val kontur = mutableListOf<TrassenGrafikObjektPunkt>()
            for (i in geordnet.indices) {
                val von = geordnet[i].first
                val bis = geordnet[(i + 1) % geordnet.size].first
                val segment = segmentZwischen(von, bis)
                if (segment.size < 2) return@mapNotNull null
                // Das Segmentende ist zugleich der Start des nächsten Segments.
                kontur += segment.dropLast(1)
            }
            if (kontur.size < rollen.size) return@mapNotNull null

            TrassenGrafikObjektFlaeche(
                id = objekt.id,
                label = objekt.bezeichnung,
                punkte = kontur
            )
        }
    }

    private fun TextView.textStyleBold()'''
fragment, count = pattern.subn(new_function, fragment, count=1)
if count != 1:
    raise SystemExit(f"streckenObjektFlaechen replacement count={count}")
write(fragment_path, fragment)

# Only real object corners get a dot/label. Intermediate TRA samples shape the contour silently.
view_path = "app/src/main/java/de/tbhelfer/ui/importexport/TrassenGrafikView.kt"
view = read(view_path)
old = """            flaeche.punkte.forEach { punkt ->\n                val x = sx(punkt.x, b)\n                val y = sy(punkt.y, b)\n                canvas.drawCircle(x, y, dpF(5f), objektFlaechePunktPaint)"""
new = """            flaeche.punkte.filter { it.rolle.isNotBlank() }.forEach { punkt ->\n                val x = sx(punkt.x, b)\n                val y = sy(punkt.y, b)\n                canvas.drawCircle(x, y, dpF(5f), objektFlaechePunktPaint)"""
if old not in view:
    raise SystemExit("object-corner drawing block not found")
view = view.replace(old, new, 1)
write(view_path, view)

print("TB-Helfer 2.0 (005) patch applied successfully")
