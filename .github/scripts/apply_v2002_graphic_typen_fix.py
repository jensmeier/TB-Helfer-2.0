from pathlib import Path

path = Path("app/src/main/java/de/tbhelfer/ui/importexport/ImportExportFragment.kt")
text = path.read_text(encoding="utf-8")
marker = "setSichtbareTypen(setOf("
if marker not in text:
    old = '''                    setFestpunkteSichtbar(true)
                    setSichtbarePunktArten(setOf("GVP", "FESTPUNKT"))
                    setLegendeSichtbar(false)
'''
    new = '''                    setFestpunkteSichtbar(true)
                    setSichtbarePunktArten(setOf("GVP", "FESTPUNKT"))
                    setSichtbareTypen(
                        setOf(
                            TrassenAchseEntity.ACHSTYP_GLEIS,
                            TrassenAchseEntity.ACHSTYP_WEICHE,
                            TrassenAchseEntity.ACHSTYP_WEICHENVERBINDUNG,
                            TrassenAchseEntity.ACHSTYP_STRECKE,
                            TrassenAchseEntity.ACHSTYP_UNBEKANNT
                        )
                    )
                    setLegendeSichtbar(false)
'''
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"sichtbare Typen: erwartet 1 Treffer, gefunden {count}")
    text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")
