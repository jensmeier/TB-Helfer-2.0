# Streckenpunkte Patch 2

## Enthalten
- Streckenpunkt erweitert um:
  - `streckennummer`
  - `bahnhof`
  - `gleis`
  - `weiche`
- Room DB von Version 8 auf 9
- Migration `8 -> 9`
- Suche im Streckenpunkte-Screen aktiviert
- Statistikzeile aktiviert
- Skizze oben zeigt jetzt die gefilterten Treffer
- Testdaten-Import aktiviert
- JSON-Export aktiviert
- Dialog für neuen/bestehenden Punkt um Strecke/Bahnhof/Gleis/Weiche erweitert
- JSON-Testdaten auf Version 2 angehoben

## Hinweis
Die Migration `8 -> 9` ergänzt nur neue TEXT-Spalten in `streckenpunkt`.
Bestehende Punkte bleiben erhalten.

## Geänderte Dateien
- `java/de/tbhelfer/datenbank/StreckenpunktEntity.kt`
- `java/de/tbhelfer/datenbank/StreckenpunkteRepository.kt`
- `java/de/tbhelfer/datenbank/TBDatenbank.kt`
- `java/de/tbhelfer/ui/streckenpunkte/StreckenpunkteViewModel.kt`
- `java/de/tbhelfer/ui/streckenpunkte/StreckenpunkteFragment.kt`
- `java/de/tbhelfer/ui/streckenpunkte/StreckenpunkteAdapter.kt`
- `java/de/tbhelfer/ui/streckenpunkte/StreckenpunkteJsonUtil.kt`
- `res/layout/dialog_neuer_streckenpunkt.xml`
- `res/values/strings.xml`
- `assets/streckenpunkte/testdaten_strecke_5900.json`
