# BTB-INTEGRATION-031 – Umsetzung und offene Abnahme

Stand: 31.10.2026. Android vorbereitet als1.3.35/200085; benötigt gemeinsamen Serverstand. Kein Produktiv-Deployment und keine Gerätestests durchgeführt.

## Umgesetzt

- Android-PDF drei Fotos je Zeile; Server-PDF ebenfalls drei mit190pt Bildbereich; Portal drei gleich breite Karten über die verfügbare Breite. Bilder bleiben per Vergrößerung erreichbar.
- Strukturierte Einträge (Firma/Unterbaustelle) werden für Android-PDF und Word nur im Drucksnapshot in die vorhandene Gruppierung umgesetzt. Persistierte IDs und Texte bleiben unverändert. Allgemein behält den gewählten Ort. Unbekannte Doppelpunktpräfixe erzeugen keine Firma.
- Fotozuordnung: vorhandene BTB-Eintrags-ID hat Vorrang. Fehlende Zuordnung darf bei genau einem passenden Eintrag desselben BTB/Punkts/Firma/Orts und gleicher normalisierter Beschreibung ergänzt werden. Kein bloßer Textabgleich über Firmen/Orte/BTBs hinweg. Explizit gelöste Zuordnung bleibt gelöst. Bildnummern am Eintrag statt doppelter Textzeilen.
- Android: Löschen eines manuellen Eintrags wird sofort gespeichert; Bilder bleiben erhalten und werden gelöst. Firmen-/Orts-/Punktwechsel halten die Eintragsbindung.
- Verlauf bietet dem Haupt-TB die ausdrückliche Nachbearbeitung abgeschlossener Berichte. Server setzt REWORK ohne eine andere aktive Schicht zu ersetzen. Portal lässt diese Nachbearbeitung ebenfalls zu. Der Originalnachweis bleibt erhalten; eine inhaltliche Änderung invalidiert das aktuelle Abschluss-PDF.
- Abschluss lädt keine fremden Medien mehr vorab herunter, überträgt aber weiter eigene ausstehende Inhalte/Fotos. Worker berücksichtigt die echte Netzrichtlinie statt WLAN mit UNMETERED gleichzusetzen, Retry frühestens10s. Erfolg bleibt an Serverbestätigung gebunden.
- Gesamtbilderliste nutzt bestätigte Foto-/Serverbindung für Original/Sync-Duplikate und Linkmetadaten für aktuelle Firmen-/Orts-/Textangaben. Ein unvollständiger Katalog oder fehlende geplante Bilddatei führt zu einem klaren Exportfehler statt einer scheinbar vollständigen Tabelle mit toten Links. Kein Eingriff in Bestandsdateien.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis | Aussagegrenze |
|---|---|---|
| test_btb_integration_031.py |22/22 PASS| Produktionsfunktionen mit kontrollierten DB-/Transaktionsgrenzen |
|159_btb_integration_031.cjs|7/7 PASS| Produktiver Portal-Editierguard in Node-VM |
| test_worksite_head.py|14/14 PASS| isolierte Handler-/SQLite-Grenzen |
|132_verify_permission_guards.py|11/11 PASS| vorhandene Berechtigungsregression |
|138_verify_file_endpoint_rights.py|11/11 PASS| vorhandene Datei-Rechteregression |
|152_portal_btb_input_regression.py|PASS| Python+JavaScript, u.a. Wetter-/Zeit-/Eingabeabläufe |
|153_entry_photo_shift_regression.py|PASS| Binding, versionierte Fotoänderung, GPS-Löschung, Zeitgrenzen |
|158_worksite_list_live_regression.cjs|PASS| Listensynchronisation in Node-VM |
| Produktions-PDF-Referenztest|PASS| echte PDF erzeugt, drei Karten in einer physischen Zeile, Nummern/KM0/GPS-Links |
| Python-Syntax aller tbserver-Module|PASS| Syntax, kein Import-/HTTP-/DB-Betrieb |
| BtbIntegration031Test.kt|8 Tests ergänzt, NICHT ausgeführt| kein Kotlin-/Gradle-/Android-SDK vorhanden |

## Noch offen – keine Funktionsfreigabe behaupten

- Android compileDebugKotlin, testDebugUnitTest, KSP/Lint und APK-Build mit vorhandener Projekttoolchain.
- Installation als Update, kein Löschen lokaler Daten. Echte Haupt-TB/Helfer-Geräte, Neustart, offline/online, Serverbestätigung, Versionskonflikte, doppelte Abschlussklicks und Helfer-Schreibschutz prüfen.
- PostgreSQL/HTTP-/NAS-E2E mit beiden neuen Branchständen. Abschlussdauer vorher/nachher messen; keine Zeitgarantie aus Quellcode ableiten.
- Sieben unterschiedliche Testaufnahmen: zweimal synchronisieren und exportieren, genau sieben Tages-/Gesamtzeilen und sieben Dateien, jeder Link öffnet sein Bild. Bereits vorhandene Duplikate wurden nicht automatisch gelöscht oder im realen Bestand nachgewiesen bereinigt.
- Falsche Firma korrigieren; Ort und BTB-Punkt wechseln; Text/Uhrzeit/KM ändern; Eintrag löschen; Server/Android/PDF/Word vergleichen. Zugeordnete Fotos müssen folgen, Originaldateien bleiben erhalten.
- Schwellenwechsel mit mehreren passenden Fotos, anderer Firma/Ort/Punkt und zwei identischen Zieltexten testen. Nur der eindeutige passende Kontext bindet automatisch.
- Neue Schicht starten, altes BTB nachbearbeiten/abschließen: neue aktive Schicht bleibt unverändert. Helfer beendet nur Teilnahme; Haupt-TB schließt Bericht.
- Wetterabweichung vor/nach asynchroner Bestätigung bleibt als separater Gerätefall offen. Kein Wetter-Liveabruf oder erfundener Korrekturerfolg.

## Übernahme

Code liegt getrennt in feature/tb-android-2.0 und feature/tb-server-1.0. Beide Stände gemeinsam auf Testumgebung übernehmen und obige Abnahme durchführen. Keine Actions, APK-Veröffentlichung, Kontolöschung oder NAS-Übernahme durch diesen Arbeitsblock. Release-MD: projektsteuerung/releases/TB-Helfer_1.3.35_200085.md auf Android-Branch; DOWNLOAD_URL leer.
