# TB-Helfer 2.0 – dauerhafter Projektstatus

**Stand:** 2026-09-20  
**Branch:** `feature/tb-server-1.0`  
**Draft-PR:** #6 – TB-Server 1.0 – gemeinsame Serverbasis  
**Geprüfter Quellstand vor Tracker-Update:** `25d111ff093ac860eec9a9c5abb64caa5adff253`  
**Android:** 1.3.2 / versionCode 200052  
**Server:** 1.0.0-dev / staging

## Verbindliche Nutzung

- Vor einer Änderung zuerst diesen Tracker **und** die verbindlichen Projektregeln/den aktuellen GitHub-Stand prüfen.
- `☑` bedeutet: vom Benutzer bzw. durch praktischen Test bestätigt. Solche Punkte werden **nicht nebenbei neu gebaut oder verändert**.
- `Nicht ohne Grund ändern = JA` bedeutet: nur bei reproduzierbarem Fehler, neuer belegter Anforderung oder ausdrücklichem Benutzerwunsch anfassen.
- Neue Probleme, Entscheidungen, Tests und wesentliche Neuerungen werden in **Excel und MD mit derselben ID und demselben Inhalt** nachgeführt.
- Geheimwerte (Cloudflare-Tunnel-Token, Passwörter, Geräte-Bearer, Aktivierungs-Keys usw.) werden niemals in diesen Tracker oder GitHub geschrieben.

## Token-Regel

- **Cloudflare Tunnel-Token:** wichtig für `cloudflared`, solange derselbe Tunnel weiterverwendet wird. Auf dem NAS als Secret/.env behalten oder bei Bedarf rotieren; **nicht nach GitHub übernehmen**.
- **Alte Geräte-/Bearer-/Aktivierungs-Tokens des 0.7-Servers:** nicht migrieren. Server 1.0 bindet Geräte sauber neu über den 30 Tage gültigen Einmal-QR.

## App

| ID | Funktion / Thema | Status | Haken | Stand / Ergebnis | Nächster Schritt | Version / Commit | Nicht ohne Grund ändern | Letzte Prüfung | Notiz |
|---|---|---|:---:|---|---|---|:---:|---|---|
| APP-001 | Strecken-/Vermessungsberechnung – bestehende funktionierende Fachlogik | BESTÄTIGT | ☑ | Bestehende funktionierende Komponenten und bereits bestätigte Berechnungslogik bleiben Referenz; keine parallele Ersatzlösung bauen. | Nur bei reproduzierbarem Fehler oder ausdrücklich neuer Fachanforderung ändern. | 1.3 / versionCode 200050 | JA | 2026-09-20 | Schutzregel: Was als funktionierend bestätigt ist, nicht nebenbei umbauen. |
| APP-002 | Offline-Arbeit / lokale Baustellendaten | UMGESETZT | ☑ | Android arbeitet offline mit lokalen Kopien; Server synchronisiert nur benötigte Daten. | Im Pixel/Poco-E2E gegen Server 1.0 praktisch bestätigen. | 1.3 / versionCode 200050 | JA | 2026-09-20 |  |
| APP-003 | Server-1.0-Synckern | UMGESETZT / TEST OFFEN | ☐ | Gemeinsamer Server1AndroidSyncCoordinator für UI und Hintergrund; kanonische /api/v1-Pfade sind im Quellstand weitgehend umgesetzt. | Pixel + Poco E2E: Teams, Baum, Mängel, Abnahmen, Dateien, BTB, Fotos, PDF, Hintergrund-Sync. | 1.3 / versionCode 200050 | NEIN | 2026-09-20 |  |
| APP-004 | Mangelstatus ABGELEHNT ↔ REJECTED | UMGESETZT / TEST OFFEN | ☐ | Quellstand synchronisiert ABGELEHNT verlustfrei mit Serverstatus REJECTED. | Auf zwei Geräten praktisch testen. | 1.3 / versionCode 200050 | JA | 2026-09-20 | Fachregel steht fest. |
| APP-005 | Abnahme löschen / Cross-Device-Tombstone | UMGESETZT / TEST OFFEN | ☐ | Offline-Löschwunsch, Server-DELETE, Tombstone, Audit, Change-Feed und Cross-Device-Entfernung sind implementiert. | Auf Pixel löschen → Poco synchronisieren → Verschwinden bestätigen. | 1.3 / versionCode 200050 | JA | 2026-09-20 |  |
| APP-006 | Geräteaktivierung per QR – Serveradresse aus QR übernehmen | BUILD-RETEST OFFEN | ☐ | Ursache des 200051-Buildabbruchs behoben: fehlende Imports für JSONArray und SyncWorksiteContextEntity. Quellstand 200052 behält QR-Zielserveranzeige und api-v1-Debug-Fallback bei. Der frühere Geräteaktivierungsfehler bleibt bis zum erfolgreichen aktuellen APK-Test fachlich offen. | Android 200052 bauen/installieren; frischen Server-1.0-QR scannen. Erfolg: Build grün, danach device/login = 200 im v1-API-Log und kein device/login im alten API-Log. | 1.3.2 / versionCode 200052 | JA | 2026-09-20 | Kein weiterer devices.py-Umbau vor diesem APK-Test. |
| APP-007 | local.properties als Serverkonfiguration | NUR ENTWICKLER-FALLBACK | ☑ | Nicht Teil des normalen Nutzerablaufs. Nutzer bekommt Serveradresse über Aktivierungs-QR. | So belassen; nur für Entwickler-/Legacy-Testzwecke verwenden. | 1.3 / versionCode 200050 | JA | 2026-09-20 |  |
| APP-008 | Baustelleneinladung QR/Link/Code | UMGESETZT / TEST OFFEN | ☐ | Deep-Link, Parser, Einlösen und automatisches Einlösen nach Geräteaktivierung sind im Quellstand vorhanden. | Nach erfolgreicher Gerätebindung praktisch testen. | 1.3 / versionCode 200050 | JA | 2026-09-20 |  |
| APP-009 | BTB – ein PDF / unveränderliches Original / Nachbearbeitung | UMGESETZT / E2E OFFEN | ☐ | Server- und Android-Vertrag ist implementiert; erstes Original bleibt unverändert, aktuelles Abschluss-PDF wird ersetzt. | Echte PostgreSQL-/Android-E2E-Prüfung inkl. Fotos und Reaktivierung. | 1.3 / versionCode 200050 | JA | 2026-09-20 |  |
| APP-010 | Aktuelle Baustelle / mehrere Baustellen lokal | FACHREGEL FEST | ☑ | Mehrere Baustellen dürfen lokal vorhanden sein; genau eine ist aktuell; Daten dürfen sich nicht vermischen. | Im Zwei-Geräte-E2E erneut bestätigen. | 1.3 / versionCode 200050 | JA | 2026-09-20 |  |

## Server

| ID | Funktion / Thema | Status | Haken | Stand / Ergebnis | Nächster Schritt | Version / Commit | Nicht ohne Grund ändern | Letzte Prüfung | Notiz |
|---|---|---|:---:|---|---|---|:---:|---|---|
| SRV-001 | Synology Staging Server 1.0 | LÄUFT | ☑ | PostgreSQL, API und Worker laufen auf Synology; Serverstatus erreichbar. | Weiter als Testumgebung verwenden; noch kein produktiver Cutover. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-002 | Datenbank-Migrationen | BESTÄTIGT | ☑ | Synology zeigt Migrationen 10 / 10, latest 10 / 10, pending keine. | Bei neuen Migrationen Zähler aktualisieren und erneut prüfen. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-003 | Cloudflare Staging api-v1.tb-helfer.de | BESTÄTIGT | ☑ | Öffentlicher HTTPS-Stagingpfad ist aktiv; /health/ready wurde erfolgreich gegen Server 1.0 geprüft. | Bis Cutover als Staging beibehalten. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-004 | Admin-Übersicht Server 1.0 | BESTÄTIGT | ☑ | Neue Übersicht läuft praktisch auf Synology: 24h Traffic/Fehler, Laufzeit, Speicher, Bestände, Probleme/Warnungen. | Nur gezielte Ergänzungen; bestehende bestätigte Anzeige nicht nebenbei neu bauen. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-005 | Benutzer Soft-Delete + gleichen Login neu verwenden | BESTÄTIGT | ☑ | Migration 010 aktiv; Benutzer löschen, aus Liste entfernen und Login wieder anlegen wurde praktisch bestätigt. | Im Gesamttest noch einmal mit Gerätebindung kombinieren. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-006 | Aktivierungskarte QR / PDF | BESTÄTIGT | ☑ | Karte mit Key, QR, PDF/Teilen wird erzeugt. QR enthält den neuen öffentlichen Server 1.0. | Nach aktuellem Android-Build erfolgreichen Login 200 prüfen. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-007 | Aktivierungs-Key Lebensdauer | FACHREGEL FEST / SERVER KORRIGIERT | ☑ | 30 Tage gültig, nur einmal verwendbar. Neue Karte darf bereits ausgegebene offene Keys nicht allein dadurch ungültig machen. Nach erster erfolgreichen Bindung werden andere offene Keys widerrufen. | E2E mit aktuellem APK bestätigen; danach nicht mehr verändern. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-008 | Gerätebindung Android → Server 1.0 | FEHLER LOKALISIERT / BUILD-RETEST OFFEN | ☐ | Neuer Server zeigt 0 aktive Geräte. Alter Handy-Test landete noch beim alten Server. Der erste 200051-Neubuild scheiterte lokal an zwei fehlenden Imports; Quellstand 200052 ist korrigiert. | Android 200052 erfolgreich bauen/installieren und prüfen: POST /api/v1/device/login muss im v1-API-Log mit 200 erscheinen; im alten Server darf derselbe Versuch nicht auftauchen. | 1.0.0-dev / staging | NEIN | 2026-09-20 |  |
| SRV-009 | Cloudflare Tunnel-Token | SECRET / BEIBEHALTEN | ☑ | Der Tunnel-Token authentifiziert cloudflared am bestehenden Tunnel. Er ist wichtig, solange derselbe Tunnel tb-helfer-nas weiterverwendet wird. Er gehört NICHT in GitHub, Excel, MD, Logs oder Screenshots. | Auf NAS/.env bzw. Secret belassen; beim Umzug ggf. denselben Token sicher übernehmen oder bei Bedarf in Cloudflare rotieren. | 1.0.0-dev / staging | JA | 2026-09-20 | Kein Geheimwert wird im Tracker gespeichert. |
| SRV-010 | Alte Geräte-/Bearer-/Aktivierungs-Tokens vom Server 0.7 | NICHT MIGRIEREN | ☑ | Diese Tokens gehören zur alten Serverdatenbank/alten Bindung und sind nicht die dauerhafte Serveridentität für 1.0. | Geräte auf Server 1.0 sauber per neuem 30-Tage-QR aktivieren; alte Tokens nach Cutover auslaufen/widerrufen. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-011 | Alter Server 0.7.x | ROLLBACK / NOCH NICHT ABSCHALTEN | ☐ | Alter Server arbeitet noch und beantwortet aktuell weiterhin App-Sync. Er bleibt bis bestandenem Server-1.0-E2E als Rückfallmöglichkeit bestehen. | Erst nach bestandenem E2E + Backup + Cloudflare-Cutover kontrolliert stoppen. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-012 | Backup / Retention / Restore-to-Target | UMGESETZT / PRAKTISCH OFFEN | ☐ | Backup-/Restore-Mechanismen sind vorbereitet; Restore soll immer in getrennte Ziel-DB/Storage erfolgen. | Backup → Retention → Restore-Test → Restore-to-Target praktisch ausführen. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-013 | Produktiver NAS-Cutover | GESPERRT BIS E2E | ☐ | Kein produktiver Cutover ohne vollständiges verifiziertes Backup und bestandene Android-/Server-/Restore-Tests. | Erst am Ende Cloudflare-Routen produktiv auf 1.0 umstellen und alten Server stoppen. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| SRV-014 | Originale Vermessungsdaten | FACHREGEL FEST | ☑ | Ab erstem produktiven Import bleiben TRA/GRA/KF/KOR/KV/GVP unverändert. Bearbeitete Daten werden getrennt gespeichert. | Nie durch Reset/Update überschreiben oder löschen. | 1.0.0-dev / staging | JA | 2026-09-20 |  |

## Probleme

| ID | Funktion / Thema | Status | Haken | Stand / Ergebnis | Nächster Schritt | Version / Commit | Nicht ohne Grund ändern | Letzte Prüfung | Notiz |
|---|---|---|:---:|---|---|---|:---:|---|---|
| PRB-001 | Android-Aktivierung landet am alten Server | OFFEN – BUILD-RETEST ZUERST | ☐ | Altes Serverlog zeigte POST /api/v1/device/login mit 401. Der daraufhin vorbereitete Build 200051 kompilierte lokal nicht, weil in Server1FileApi JSONArray und in BaustellenSyncFragment SyncWorksiteContextEntity nicht importiert waren. Beide Imports sind in 200052 korrigiert. | Android 200052 erfolgreich bauen; danach beim Scan muss die App sichtbar „Verbinde mit api-v1.tb-helfer.de …“ anzeigen. Dann alten und neuen API-Log parallel prüfen. | 1.3.2 / versionCode 200052 | NEIN | 2026-09-20 | Höchste aktuelle Priorität. |
| PRB-002 | GitHub Actions Runner | OFFEN / INFRASTRUKTUR | ☐ | Neueste CI-Läufe erhalten wieder keinen Runner/keine Steps. Deshalb kein belastbarer Build-/Testnachweis aus Actions. | Nicht als Fachfehler behandeln; lokale/echte E2E-Tests fortsetzen und Runner später separat reparieren. | GitHub Actions | NEIN | 2026-09-20 |  |
| PRB-003 | Alte 0.7 API liefert für neuere App-Pfade 404 | BEKANNT / DURCH CUTOVER ZU LÖSEN | ☐ | Im alten Serverlog erscheinen u.a. 404 für nodes, track-data, worksite-deletions und user/profile. | Nicht den alten Server auf neue 1.0-Fachpfade nachrüsten; aktuellen App-Build sauber auf Server 1.0 aktivieren. | 0.7.x | JA | 2026-09-20 |  |
| PRB-005 | Android 200051 Compilefehler | QUELLSTAND KORRIGIERT / BUILD RETEST OFFEN | ☐ | Lokaler Build scheiterte mit 8 Folgefehlern: fehlender JSONArray-Import in Server1FileApi und fehlender SyncWorksiteContextEntity-Import in BaustellenSyncFragment. | Android 200052 lokal mit assembleDebug erneut bauen; nur ein grüner Build schließt diesen Punkt. | 1.3.2 / versionCode 200052 | NEIN | 2026-09-20 | Source-Check 85 schützt die beiden Importursachen. |
| PRB-004 | Vermessungskette echte E2E-Daten | OFFEN | ☐ | Import → Parse → Survey-Section → Punktprojektion v3 → GRA-Sprung v2 → Kalibrierung noch nicht vollständig gegen echte PostgreSQL/FastAPI-Testinstanz geprüft. | Nach Geräte-/Sync-E2E mit realen Vermessungsdaten ausführen. | 1.0.0-dev / staging | NEIN | 2026-09-20 |  |

## Planung

| ID | Funktion / Thema | Status | Haken | Stand / Ergebnis | Nächster Schritt | Version / Commit | Nicht ohne Grund ändern | Letzte Prüfung | Notiz |
|---|---|---|:---:|---|---|---|:---:|---|---|
| PLAN-001 | Aktuellen Android-Debug-Build erstellen/installieren | NÄCHSTER SCHRITT | ☐ | Build 200051 zeigte 8 Kotlin-Fehler, die auf zwei fehlende Imports zurückgingen. Quellstand 200052 ist korrigiert und durch Source-Check 85 gegen diesen Rückfall abgesichert. | GitHub Desktop aktualisieren, Debug-APK 1.3.2 / 200052 bauen. Erst bei grünem Build auf genau einem Testgerät installieren. | 1.3.2 / versionCode 200052 | NEIN | 2026-09-20 |  |
| PLAN-002 | QR-Gerätebindung neu testen | GEPLANT | ☐ | Nach erfolgreichem Build/Installation von Android 200052 frischen Benutzer/30-Tage-QR auf Server 1.0 verwenden. | Neues Serverlog: POST /api/v1/device/login = 200; alter Server: kein device/login für diesen Versuch. | 1.0.0-dev / staging | NEIN | 2026-09-20 |  |
| PLAN-003 | Pixel + Poco Server-1.0 E2E | GEPLANT | ☐ | Gemeinsame echte Testbaustelle auf zwei Geräten. | Einladung/Team, Unterbaustellen, Mängel/Abgelehnt, Abnahmen/Löschen, Fotos, BTB, PDF, Hintergrund-Sync vollständig abhaken. | 1.3 / versionCode 200050 | NEIN | 2026-09-20 |  |
| PLAN-004 | Backup/Restore-Abnahme | GEPLANT | ☐ | Vor produktivem Cutover zwingend. | Backup prüfen, Retention, getrennten Restore-Test und Restore-to-Target durchführen. | 1.0.0-dev / staging | NEIN | 2026-09-20 |  |
| PLAN-005 | Server 0.7 abschalten | SPÄTER | ☐ | Erst nach erfolgreichem Server-1.0-E2E und produktivem Cloudflare-Cutover. | Kontrolliert stoppen, Rollback-Backup zunächst behalten. | 0.7.x → 1.0 | JA | 2026-09-20 |  |

## Erledigt

| ID | Funktion / Thema | Status | Haken | Stand / Ergebnis | Nächster Schritt | Version / Commit | Nicht ohne Grund ändern | Letzte Prüfung | Notiz |
|---|---|---|:---:|---|---|---|:---:|---|---|
| DONE-001 | Server-1.0 Staging auf Synology gestartet | ERLEDIGT | ☑ | DB, API und Worker laufen; Healthcheck ok. | — | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| DONE-002 | Migration 010 angewendet | ERLEDIGT | ☑ | 10 / 10, keine Pending-Migration. | — | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| DONE-003 | Neue Admin-Übersicht | ERLEDIGT | ☑ | Praktisch auf Synology bestätigt. | — | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| DONE-004 | Benutzer löschen / Login wiederverwenden | ERLEDIGT | ☑ | Praktisch bestätigt. | — | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| DONE-005 | Aktivierungskarte QR/PDF erzeugen | ERLEDIGT | ☑ | Serverseite funktioniert. | Android-Bindung separat noch offen. | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| DONE-006 | Cloudflare HTTPS Staging api-v1 | ERLEDIGT | ☑ | Öffentlicher Server-1.0-Healthcheck bestätigt. | — | 1.0.0-dev / staging | JA | 2026-09-20 |  |
| DONE-007 | Projektstatus-Tracker in GitHub verankert | ERLEDIGT | ☑ | projektstatus/TB-HELFER_STATUS.md und .xlsx sind Bestandteil des Feature-Branches; Projektregeln verlangen Prüfung vor Änderungen und parallele Pflege. | Bei jeder wesentlichen Änderung beide Tracker und docs/TB-HELFER_AKTUELLER_STAND.md nachziehen. | geprüfter Quellstand c7760059fb5f | JA | 2026-09-20 | Keine Geheimwerte in Tracker aufnehmen. |