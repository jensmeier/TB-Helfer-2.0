# Streckenpunkte Patch 3 – Automatik-Modus „Track + KM-Anker"

## Inhalt
- Automatik starten mit optionalen Session-Stammdaten
- Live-GPS läuft im Streckenpunkte-Screen
- alle 10 m wird ein Trackpunkt in der Session gespeichert
- KM-Vorschlag aus `KmBerechner` wird live angezeigt
- KM-Anker kann mit aktueller GPS-Position gesetzt werden
- jeder KM-Anker wird zusätzlich als normaler Streckenpunkt gespeichert
- bei `Stop + Speichern` wird eine eigene JSON-Datei für die Session exportiert
- die Skizze zeigt im Automatik-Betrieb einen Live-Marker

## Export
Die Automatik-Session wird gespeichert unter:
`app external files / exports / streckenpunkte_track_<baustelle>_<zeit>.json`

## Hinweise
- Keine Datenbankmigration nötig
- Standortberechtigung muss in Android erlaubt sein
- Für sinnvolle KM-Vorschläge werden mindestens 2 Streckenpunkte mit GPS benötigt
