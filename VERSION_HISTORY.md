# TB-Helfer 2.0 – Versionshistorie

Diese Datei dokumentiert die auf dem Handy testbaren Entwicklungsstände.

## Regel

- Sichtbare Version: `2.0 (NNN)`.
- `NNN` wird nur erhöht, wenn ein neuer Stand auf Pixel/Poco getestet werden soll.
- Reine interne Hilfs- oder Aufräumcommits erhöhen die Nummer nicht.
- Der Android-`versionCode` ist monoton und verwendet für TB-Helfer 2.0 das Schema `200NNN`, damit Updates über ältere APKs ohne Downgrade möglich bleiben.
- Git bleibt die technische Detailhistorie; diese Datei beschreibt die fachlichen Unterschiede zwischen den Testständen.

## 2.0 (003)

Android versionCode: `200003`

Achse-zuerst-Auswahl und sichtbare Fachpunkte:

- Für WA/WE/WEZ und DKW A/B/C/D wird zuerst ausdrücklich die Ziel-TRA gewählt.
- Danach kann der Punkt über GVP/Festpunkt, echten geometrischen TRA-Anschluss oder GPS festgelegt werden.
- Ein GVP besitzt keinen festen Achsbezug; er wird nur auf die vorher gewählte Ziel-TRA projiziert.
- Wenn am gewünschten Weichen-/DKW-Punkt kein GVP vorhanden ist, kann ein erkannter echter TRA-Anschluss verwendet werden.
- Mehrere mögliche TRA-Anschlüsse werden zur Auswahl angeboten; die App wählt keinen Ast heimlich.
- Die Grafikauswahl besitzt wieder eine Anzeigeauswahl für Achsen und Punktarten.
- Bereits gesetzte WA/WE/WEZ bzw. A/B/C/D bleiben beim Setzen des nächsten Punkts als größere beschriftete Marker sichtbar.
- Grenze 1 und Grenze 2 werden als größere G1/G2-Marker angezeigt; beim Setzen der zweiten Grenze bleibt die erste sichtbar.
- Auto-Zoom konzentriert sich auf die gewählte Achse, direkt angeschlossene Achsen und Punkte im örtlichen Umfeld statt auf alle GVP der Baustelle.
- Keine Room-Migration.

Status: in Entwicklung / CI-Prüfung vor Handytest.

## 2.0 (002)

Android versionCode: `200002`

GVP-Auswahl für Weichen und DKW direkt in der grafischen Übersicht:

- `Vermarkung wählen` öffnet die grafische TRA-/GVP-Ansicht statt einer langen Textliste.
- WA/WE/WEZ und DKW A/B/C/D verwenden dieselben GVP/Festpunkte; die Rolle ergibt sich aus dem zuvor gedrückten Feld.
- Gewählter GVP wird temporär blau markiert.
- Vorhandener Datei-km des GVP wird übernommen.
- Die GVP-Kennung und Quelldatei werden am Weichen-/DKW-Punkt mitgespeichert, damit später nachvollziehbar bleibt, welcher Vermarkungspunkt verwendet wurde.
- Eine eindeutig nächste TRA darf automatisch vorgeschlagen werden.
- Bei eng benachbarten oder weiter entfernten TRA wird nicht geraten; die möglichen Achsen werden zur Auswahl angeboten.
- Keine Room-Migration.

Status: CI vollständig grün; Handytest zeigte, dass die Achse vor dem Punkt ausdrücklich wählbar sein muss.

## 2.0 (001)

Android versionCode: `200001`

Ausgangspunkt für die nachvollziehbaren 2.0-Teststände:

- GVP/Festpunkte werden nach einem neuen Streckendatenimport standardmäßig wieder in der grafischen Übersicht sichtbar.
- Betriebsstellen-Suche im Weichen-/DKW-Editor mit Kürzel- und Namenssuche in beide Richtungen, inklusive Teiltreffern.
- Die beim ZIP-Import gewählte Betriebsstelle wird als letzte Betriebsstelle übernommen und beim Anlegen einer neuen Weiche/DKW vorausgefüllt; sie bleibt editierbar.
- Bereits früher gespeicherte ZIP-Zuordnungen werden über ihren Zeitstempel ebenfalls für die Vorbelegung verwendet.
- Bestehende Kilometrierungs-, Weichen-/DKW- und Anschlusslogik aus `kilometrierung-v1` bleibt Grundlage.

Status: CI vollständig grün; erster 2.0-Handytest durchgeführt.
