from pathlib import Path

path = Path("app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: erwartet genau 1 Treffer, gefunden {count}")
    text = text.replace(old, new, 1)

replace_once(
    '            text = "WA/WE/WEZ können per Vermarkungspunkt oder GPS gesetzt werden. km bleibt optional; vorhandener Datei-km wird übernommen."',
    '            text = "Weichenpunkte WA/WE/WEZ und DKW-Punkte A/B/C/D können per Vermarkungspunkt oder GPS gesetzt werden. km bleibt optional; vorhandener Datei-km wird übernommen."',
    "Hinweistext"
)

replace_once(
'''                val direktPassend = festpunkte.filter { punkt ->
                    rollenRegex.containsMatchIn(punkt.label.trim()) ||
                        rollenRegex.containsMatchIn(punkt.punktId.orEmpty().trim())
                }
                val kandidaten = direktPassend.ifEmpty {
                    festpunkte.filter { it.art.equals("WEICHENPUNKT", ignoreCase = true) }
                }.sortedWith(compareBy<TrassenGrafikFestpunkt> { it.label }.thenBy { it.punktId.orEmpty() })
''',
'''                val direktPassend = festpunkte.filter { punkt ->
                    rollenRegex.containsMatchIn(punkt.label.trim()) ||
                        rollenRegex.containsMatchIn(punkt.punktId.orEmpty().trim())
                }
                val kandidaten = if (typ == StreckenObjektSpeicher.Typ.WEICHE) {
                    direktPassend.ifEmpty {
                        festpunkte.filter { it.art.equals("WEICHENPUNKT", ignoreCase = true) }
                    }
                } else {
                    val gvp = festpunkte.filter {
                        it.art.equals("GVP", ignoreCase = true) ||
                            it.art.equals("FESTPUNKT", ignoreCase = true)
                    }
                    val passendeGvp = gvp.filter { punkt ->
                        rollenRegex.containsMatchIn(punkt.label.trim()) ||
                            rollenRegex.containsMatchIn(punkt.punktId.orEmpty().trim())
                    }
                    passendeGvp.ifEmpty { gvp }
                }.sortedWith(compareBy<TrassenGrafikFestpunkt> { it.label }.thenBy { it.punktId.orEmpty() })
''',
    "DKW-GVP-Kandidaten"
)

replace_once(
'''            val vermarkung = if (typ == StreckenObjektSpeicher.Typ.WEICHE) MaterialButton(context).apply {
                text = "Vermarkung wählen"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
            } else null
''',
'''            val vermarkung = MaterialButton(context).apply {
                text = "Vermarkung wählen"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
            }
''',
    "Vermarkungsbutton fuer DKW"
)

path.write_text(text, encoding="utf-8")
