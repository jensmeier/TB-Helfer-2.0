# Streckenpunkte Patch für TB-Helfer1.6

Dieses ZIP ist als **Android-Studio-Patch** aufgebaut.

## Ziel
Der Patch verbessert ausschließlich den Bereich **Streckenpunkte**:
- Suche nach km / Bezeichnung / Strecke / Bahnhof / Gleis / Weiche
- erweiterter Dialog für neue Punkte
- km-Suffix direkt auswählbar
- Typ auswählbar
- einfache grafische Skizze oben im Bildschirm
- Testdaten-Import aus `assets/streckenpunkte/testdaten_strecke_5900.json`
- JSON-Export der gespeicherten Punkte

## Einbau
1. ZIP in eine Kopie deines Projekts entpacken
2. in den Projekt-Root legen, sodass `app/src/main/...` überschrieben bzw. ergänzt wird
3. Gradle Sync laufen lassen
4. App neu bauen

## Hinweis
- Es wurde **keine neue Room-Spalte** eingeführt.
- Dadurch ist **keine neue Datenbank-Migration** nötig.
- Strecke/Bahnhof/Gleis/Weiche werden im Patch als `objektKennung` zusammengeführt.

## Enthaltene Dateien
- `app/src/main/java/de/tbhelfer/ui/streckenpunkte/...`
- `app/src/main/res/layout/...`
- `app/src/main/res/values/streckenpunkte_patch_strings.xml`
- `app/src/main/assets/streckenpunkte/testdaten_strecke_5900.json`
