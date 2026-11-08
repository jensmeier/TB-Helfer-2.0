# SERVER-PERF-1108 – Statistik, BTB-Messung und Containerprüfung

Stand 08.11.2026. Basis Server ff3c8c97dc8fe0722f18221c4134d3448f9aa91e, Android 47a8fd5873bc87c982fc0569b9583bf7ae2caa79, Auftragscommit 60b8f967c1277c71b8776f79f569c9c0b04de9d0. Server PR6 Draft, Android PR11 offen.

## Umsetzung

- Die API bündelt HTTP- und 5xx-Zähler sowie Bytezahlen je UTC-Stunde. Ein Hintergrundtask schreibt ungefähr alle zehn Sekunden einen Batch und leert die offenen Zähler beim regulären Herunterfahren. Die bestehende Stundentabelle und Health-Ausnahme gelten weiter. Zeitstempel entsprechen dem Ende der Anfrage. Die SQL-Schreibphase hat drei Sekunden Statement-Timeout. Ein fehlgeschlagener Batch wird einmal pro Fehlerphase protokolliert und nicht wiederholt, weil nach einem Verbindungsabbruch beim Commit eine Wiederholung doppelt zählen könnte. Anzeige verzögert sich um das Schreibintervall. Bei Prozessabsturz oder DB-Ausfall können noch nicht gespeicherte Statistikwerte verloren gehen. Fachliche Daten laufen über ihre bisherigen Transaktionen.
- `btb_finalize_timing` im API-Log misst erfolgreiche neue Abschlüsse. `prepare_ms` umfasst Verbindung, Laden und Vorbereitung. `lock_read_ms` ist die Zeit zum Lesen und Sperren des BTB und bereits in `prepare_ms` enthalten. `render_ms` misst den Renderer. `persist_ms` umfasst Hash, Dateiinstallation, DB-Änderungen, Commit und Aufräumen. Dazu kommen `total_ms` und `pdf_bytes`. Keine Benutzer- oder Baustellenkennung in der Zeile. Receipt-Replay und bestehendes PDF ohne Neurendern erzeugen keine Timingzeile.
- Drei Runtime-Dateien `server/tbserver/metrics.py`, `main.py`, `btb.py`. Zusammen über den bestehenden NAS-Weg übernehmen, nach Backup gemäß Auftrag API-Prozess neu starten. Keine DB-Migration und keine Compose-Änderung.

## Android-Quellvergleich

| Pfad | Ergebnis |
|---|---|
| Haupt-TB Abschluss | Bisheriger synchroner POST mit `allow_unfinished`, bestehender Mutationsquittung und PDF-Status. PDF entsteht vor bestätigtem Abschluss. Android-Lifecycle behält Abschluss-/Helferrollen. |
| Abschluss-PDF | Android liest `final_pdf` und dessen Prüfsumme und lädt über `/api/v1/btbs/{id}/final-pdf`. PDF-Renderer Version 6 und drei Fotospalten bleiben der Referenzstand. |
| Delta / Datei | `Server1DeltaApi` nutzt `/api/v1/sync/changes/wait`, maximal 30 Sekunden Wartezeit plus 15 Sekunden Lesezeitreserve. Cursorfelder `next_after`, `head`, `caught_up` unverändert. Datei-Upload/Download/Renditions behalten bestehende Routen und Grenzen. |
| ZIP und Wetter | ZIP speichern/teilen im Verlauf 1.3.43 ist lokal für den gewählten BTB und hängt nicht am HTTP-Messblock. Wetter und Portal/PDF entsprechen dem Integrationsstand 1108. Keine neue Wetterdienst- oder Geräteabnahme. |

Die Android-Bauübergabe für 1.3.43/200093 nennt 228 Unit-Tests. Das ist der vorliegende Buildnachweis, kein neuer Lauf dieses Serverblocks. Reale Abnahme Pixel/Poco und NAS weiterhin offen.

## Compose-Abgleich und Vorschläge

Beide YAML-Dateien mit PyYAML geparst. Die zwölf Dienstdefinitionen haben dieselben Abhängigkeiten. Einzige Abweichung innerhalb der Dienstdefinitionen: gespeicherte compose.yml mit API-Limit 1g, GitHub-Vorlage mit `TB_V1_API_MEMORY_LIMIT` und 512m Rückfallwert. Effektive Laufzeitkonfiguration bleibt auf NAS zu prüfen.

| Dienste | Zweck und Bewertung |
|---|---|
| db, api | Anwendungsdaten und HTTP. Für den laufenden Betrieb erforderlich. |
| worker | Hintergrundaufträge unter anderem Import und Sicherung. Dauerbetrieb beibehalten, Backup und Importe zeitlich von der Hauptnutzung trennen. |
| gateway, cloudflared | Gateway routet API und Recovery. Cloudflared liefert den bisherigen externen Zugang. Beides für diesen Betriebsaufbau behalten. |
| monitor | Systemzustand und Meldungen. Erst bei messbarer Last Intervall prüfen. |
| migrate, recovery-init | Startaufgaben. Erfolgreich beendete Instanzen sind kein dauerhafter Prozess. Abhängigkeiten beibehalten. |
| recovery | Nimmt Wiederherstellungsaufträge an und meldet den Zustand auch während der Wartung. Beibehalten. |
| restore-test-db, restore-target-db | Separate PostgreSQL-Instanzen für Sicherungstest und Wiederherstellungsziel. Kandidaten für Betrieb bei Bedarf. Der geprüfte Recovery-Weg setzt sie voraus. Vor Umschaltung Start/Warten/Berechtigungen, automatische Testwiederherstellung, Unterbrechung und Rückkehr in einer Testinstanz prüfen. |
| system-export | Export von Image und Quellen für Systemsicherung. Eigenes Netzwerk, einziger Dienst mit Docker-Socket-Mount. Bedarfsgesteuerter Start erst nach Nachweis eines gleichwertigen Systemsicherungslaufs. |

Die Vorlage verwendet ein gemeinsames Server-Image. PostgreSQL-Clientwerkzeuge sind für Sicherungsfunktionen relevant. Ein Image-Split würde Varianten für Build, Übernahme und Wiederherstellung schaffen. Dafür ist aktuell kein messbarer Nutzen belegt.

## Nachweise und weiterer Prüfpunkt

29 lokale Tests bestanden (4 neue ASGI-/Statistikfälle mit DB-Schreibdouble, 22 vorhandene BTB-Regressionen, 3 Integration-1108-Regressionsfälle), plus PDF-Referenz mit physisch drei Spalten, Anzahlbegrenzung, KM 0 und GPS-Links. Python-Syntax geprüft. Der erste BTB-Testlauf im Teilauszug benötigte ein noch nicht geladenes unverändertes Fotometadaten-Modul. Nach Ergänzung war der Lauf vollständig erfolgreich.

PostgreSQL-Schreiblast, Container-Runtime, Postman und echte Geräte-/NAS-E2E wurden in diesem Block nicht ausgeführt. Keine neue GitHub-Actions-Ausführung ausgelöst. Keine produktiven Daten bearbeitet. Der bekannte nicht verifizierbare Binärabruf des Status-Excel bleibt offen.

Für die Messung auf einer Testinstanz einen kleinen und einen fotoreichen BTB abschließen und `btb_finalize_timing` vergleichen. Danach Foto-Upload und zweiten Client parallel prüfen. Zeiten, API-RAM, DB-Last und Neustarts erfassen. Der vorhandene 255-Client-Nachweis betrifft Long-Poll, nicht diesen Mischbetrieb. Erst auf Basis dieser Messung über eine kürzere DB-Sperrzeit und den bedarfsgesteuerten Restore-Stack entscheiden.

**SERVER-QUELLSTAND FERTIG / LOKALE PRÜFUNGEN BESTANDEN / DEPLOY UND E2E OFFEN.**
