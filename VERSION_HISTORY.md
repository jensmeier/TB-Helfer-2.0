# TB-Helfer 2.0 – Versionshistorie

Diese Datei dokumentiert die auf dem Handy testbaren Entwicklungsstände.

## Regel

- Sichtbare Version: `2.0 (NNN)`.
- `NNN` wird nur erhöht, wenn ein neuer Stand auf Pixel/Poco getestet werden soll.
- Reine interne Hilfs- oder Aufräumcommits erhöhen die Nummer nicht.
- Der Android-`versionCode` ist monoton und verwendet für TB-Helfer 2.0 das Schema `200NNN`, damit Updates über ältere APKs ohne Downgrade möglich bleiben.
- Git bleibt die technische Detailhistorie; diese Datei beschreibt die fachlichen Unterschiede zwischen den Testständen.

## 2.0 (001)

Android versionCode: `200001`

Ausgangspunkt für die nachvollziehbaren 2.0-Teststände:

- GVP/Festpunkte sollen nach einem neuen Streckendatenimport standardmäßig wieder in der grafischen Übersicht sichtbar sein.
- Betriebsstellen-Suche im Weichen-/DKW-Editor mit Kürzel- und Namenssuche in beide Richtungen, inklusive Teiltreffern.
- Die beim ZIP-Import gewählte Betriebsstelle wird als letzte Betriebsstelle übernommen und beim Anlegen einer neuen Weiche/DKW vorausgefüllt; sie bleibt editierbar.
- Bestehende Kilometrierungs-, Weichen-/DKW- und Anschlusslogik aus `kilometrierung-v1` bleibt Grundlage.

Status: in Entwicklung / CI-Prüfung vor Handytest.
