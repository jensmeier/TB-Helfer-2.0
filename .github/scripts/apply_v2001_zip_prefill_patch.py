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
'''        val name: String,
        val strecken: List<String>
    ) {
''',
'''        val name: String,
        val strecken: List<String>,
        val geaendertAmEpochMs: Long
    ) {
''',
    "Zeitstempel in gespeicherter Streckendaten-Zuordnung"
)

replace_once(
'''                kuerzel = teile[0].trim(),
                name = teile[1].trim(),
                strecken = teile[2].split(",").map { it.trim() }.filter { it.isNotBlank() }
            )
        }
    }

    private fun findeStreckendatenZuordnung(
''',
'''                kuerzel = teile[0].trim(),
                name = teile[1].trim(),
                strecken = teile[2].split(",").map { it.trim() }.filter { it.isNotBlank() },
                geaendertAmEpochMs = teile.getOrNull(4)?.toLongOrNull() ?: 0L
            )
        }
    }

    private fun letzteBetriebsstelleAusStreckendatenImport(): String {
        val letzte = ladeStreckendatenZuordnungen()
            .filter { it.kuerzel.isNotBlank() || it.name.isNotBlank() }
            .maxByOrNull { it.geaendertAmEpochMs }
            ?: return ""
        return when {
            letzte.kuerzel.isNotBlank() && letzte.name.isNotBlank() -> "${letzte.kuerzel} · ${letzte.name}"
            letzte.kuerzel.isNotBlank() -> letzte.kuerzel
            else -> letzte.name
        }
    }

    private fun findeStreckendatenZuordnung(
''',
    "Letzte importierte Betriebsstelle ermitteln"
)

replace_once(
'''        val betriebsstelleStart = vorhanden?.betriebsstelle
            ?: prefillBetriebsstelle.takeIf { it.isNotBlank() }
            ?: BahnhofSucheUtil.ladeLetzteEingabe(context)
''',
'''        val betriebsstelleStart = vorhanden?.betriebsstelle
            ?: prefillBetriebsstelle.takeIf { it.isNotBlank() }
            ?: BahnhofSucheUtil.ladeLetzteEingabe(context).takeIf { it.isNotBlank() }
            ?: letzteBetriebsstelleAusStreckendatenImport()
''',
    "Vorbelegung um vorhandenen ZIP-Import erweitern"
)

path.write_text(text, encoding="utf-8")
