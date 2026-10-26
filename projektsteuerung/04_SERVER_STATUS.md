# TB-HELFER 2.0 – SERVER STATUS

## AND-ERRORLOG-026 – lokales Fehlerprotokoll, 26.10.2026
**QUELLSTAND 1.3.34/200084 / JAVA-KERNTEST BESTANDEN / ANDROID-BUILD UND GERÄTE-E2E OFFEN.** Nutzer bestätigt Umsetzung des vorgeschlagenen lokalen Fehlerprotokolls, Auftrag vor Codeänderung in f617aef gesichert. Basis Android16dc829 / Server65a14b1.
- Einstellungen > Wartung & Diagnose > Fehlerprotokoll; maximal200 Fehlergruppen, erste/letzte Meldung, Wiederholungszähler, Vorgang/Bereich/Build und sicherer technischer Code. Bekannte lokale Baustellenkennung wird beim Öffnen lokal aufgelöst. Neustartfeste JSON-Präferenzen im bestehenden Handy-/Recoverybestand, keine neue DB oder Migration.
- Detail-/Gesamtkopie, Android-Teilen nur auf Nutzeraktion; bestätigtes Löschen ausschließlich der Diagnosepräferenzen. Neue Fehler können danach neue Einträge erzeugen. Sync-Fehlerlink öffnet die gespeicherte Worker-ID ohne zusätzliche Zählung; historisches Protokoll wird bei späterem Erfolg nicht geleert.
- Privacy durch feste Klassifikation statt Speicherung von Rohmeldungen: keine Tokens/Passwörter/URLs/Rohantworten/Stacktraces/Dateinamen/BTB-Inhalte. Unbekannte Meldung neutral. Exporte enthalten auch keine lokal aufgelösten Baustellennamen. Vor Installation aufgetretene Fehler werden nicht nachträglich rekonstruiert.
- Erfasste Quellen: Vollsync, ausgewählte selektive BTB-/Struktur-/Medienfehler, Delta-Poller/-Worker, Katalogfehler, sichtbare Verbindungsprüfung sowie Wartungs-Updateprüfung/-download. Kein universeller Android-/Gradle-Crashlogger. Fehlerhistorie zählt Meldungen, nicht zwingend eindeutige HTTP-Requests.
- Tatsächlich lokal ausgeführt: Produktions-Java-Kern und Regression kompiliert (java com.sun.tools.javac.Main), **26 Prüfungen PASS** für Aggregation, stabile IDs,250 Kontexte/200er-Grenze, Sortierung, sichere Kernfeldwiederherstellung, Buildtrennung/Uhr-Rücksprung und Export ohne Testgeheimnisse. XML beider Layouts geparst. Kotlin-Strukturprüfung ist kein Build.
- **Kotlin/Java-Android-Interop, Gradle/KSP/Unit/Lint/APK, echte SharedPreferences-/Neustart-/UI-/Teilen-/Löschtests OFFEN.** Android-SDK/Gradle-Abhängigkeiten fehlen lokal; keine alten CI-/Installationsnachweise auf200084 übertragen. Keine APK/Veröffentlichung/Actions/NAS-/Produktivänderung; Budget unverändert, kein neuer Minutenverbrauchsnachweis.
- Frühere files.view-/DNS-Fehler sind durch das Protokoll nicht als behoben bestätigt. Nutzer-/Helferrechte, Retry/Cursor, RecoveryHttp/-Protokoll und Servercode bleiben unverändert. Neue Release-MD [TB-Helfer_1.3.34_200084.md](releases/TB-Helfer_1.3.34_200084.md); Übergabe/Abnahme [FEHLERPROTOKOLL_026.md](uebergaben/FEHLERPROTOKOLL_026.md). Kein neues Serverdeployment für diese Funktion.

## AND-SYNC-ANZEIGE-026 – 26.10.2026, Quellstand 1.3.33/200083
**ANZEIGE IMPLEMENTIERT / JAVA-MONITORREGRESSION BESTANDEN / ANDROID-BUILD UND GERÄTETEST OFFEN.** Nutzer berichtet: Baustelle inzwischen im Portal vorhanden. Das ist ein positiver Teilnachweis, keine Bestätigung der installierten Version oder des gesamten Haupt-/Helfer-/Portal-E2E.
- Auf „Daten vom NAS“ eigener Livebereich: frische lesende Prüfung der tatsächlichen Capability-API mit JSON-Validierung; Offline/DNS/Timeout/HTTP-Fehler unterscheiden. Prüfung nur bei sichtbarer Seite ungefähr alle30s nach vorheriger Antwort, Zeitstempel/Alter sichtbar. Erreichbarkeit ist kein Login-/Berechtigungs- oder Vollständigkeitsnachweis.
- Vorbereitung, Vollabgleich, Senden und Empfang getrennt, mit realen Phasen aus den vorhandenen Laufwegen. Monotone Laufzeit/Schrittdauer sekündlich; normale Long-Poll-Wartezeit als normal bezeichnet. Nur bekannte vorgemerkte BTB-/Strukturaufträge zählen; keine behauptete globale Datei-Gesamtmenge. Fehler-/Konfliktanzeige bleibt separat erhalten.
- Keine erfundene ETA/Prozentzahl: **„Restzeit: noch nicht berechenbar.“** Kein laufender Vorgang bedeutet nicht „alle Daten synchronisiert“. Beendete/abgebrochene Läufe werden per finally aus dem Monitor entfernt; parallele Worker überschreiben einander nicht.
- Reine Beobachtung, keine Änderung an Retry, Cursor, RecoveryHttp/-Protokoll, Mutation, Rollen oder Servercode. Der reine Java-Monitor wird wegen der bestehenden Quellkopie explizit im generierten Java-Source-Set registriert; Kotlin-/Room-Quellen bleiben unverändert zugeordnet.
- Lokal tatsächlich kompiliert und ausgeführt: Java17-Compiler über java com.sun.tools.javac.Main, **13 Prüfungen PASS**, einschließlich800 paralleler Monitor-Lebenszyklen. Layout-XML geparst; strukturelle Kotlin-Klammerprüfung ist kein Kotlin-Build. Kotlin/Java-Android-Interop, Gradle/KSP, Unit/Lint, APK/Installation und echte Netz-/UI-Tests **OFFEN** (Android-SDK/Gradle-Abhängigkeiten fehlen). Keine Actions gestartet, Budget unverändert; keine NAS-/Produktivänderung oder Veröffentlichung.
- Neue passende Release-MD: projektsteuerung/releases/TB-Helfer_1.3.33_200083.md. Keine APK erzeugt. Server bleibt65a14b1; kein neues Serverdeployment für diese Anzeige. Screenshot erlaubt keinen Versions-/Buildnachweis. Details: [SYNC_ANZEIGE_026.md](uebergaben/SYNC_ANZEIGE_026.md). Frühere 200082-Integration bleibt enthalten, ihr Geräte-E2E weiterhin offen.

## Aktueller Integrationsblock AND-BIDIR-026 – 26.10.2026
**QUELLSTAND IMPLEMENTIERT / SERVER-HANDLER- UND PORTAL-TESTS BESTANDEN / ANDROID-BUILD UND GERÄTE-/NAS-E2E OFFEN.** Dieser Abschnitt ersetzt für AND-BIDIR-026 die frühere Angabe „Korrektur offen“; historische Abschnitte unten bleiben als Verlauf erhalten.
- Ausdrücklicher Nutzerauftrag zur Entwicklung beider Seiten. Basis Android6a0e7e4 / Server923cf93, Auftragscommits Android21ce70e / Server1f925bd. Androidziel jetzt **1.3.32/200082** mit passender Release-MD; installierter Altstand laut vorherigem Screenshot1.3.31/200081, keine Installation200082 bestätigt.
- Android: dauerhafte idempotente Neuanlage ohne Helfer, Antwort-/Binding-Recovery, lokale Node-Keys, Haupt-TB-Entwurfsvorschau, versionierte Namensoutbox, Kontext-/Teamempfang und Rechte-/Dirty-Schutz. Gebundene normale BTB-Sends bleiben selektiv. Lokale Pending-Daten nach Teamwechsel bleiben erhalten und werden nicht ins neue Team übertragen.
- Server: worksite_head_update_v1, PATCH /api/v1/worksites/{id} für name+base_version; Rechte/409/Receipt-Replay geschützt. Portal-Umbenennen und automatische Liste über autorisierten, typgefilterten SSE; Reconnect/Focus/Intervall und Schutz vor verspäteten Listenantworten.
- Lokal tatsächlich ausgeführt: **14/14 Python-Handler-/SQL-Fälle PASS** (Produktionsfunktionen, SQLite-Adapter); **Node-VM-Portalregression PASS**; Python-Syntax der geänderten Runtime-Module PASS. Testharnessfehler vor finalem PASS korrigiert, keine Assertions abgeschwächt. Kein PostgreSQL-/FastAPI-HTTP-/Android-/NAS-E2E.
- Android-SDK, Gradle-Cache und Kotlin-Compiler lokal nicht verfügbar; Abhängigkeiten nicht abrufbar. **KOTLIN/ROOM, UNIT, LINT, APK-BUILD, INSTALLATION OFFEN**. Frühere 200081-CI gilt nicht für200082. Keine APK/Veröffentlichung, keine Actions gestartet ([skip ci]); Budget unverändert, tatsächlicher aktueller Minutenverbrauch nicht erhoben. Keine Migration, kein NAS-/Produktivzugriff oder Deployment.
- Geschützte BTB-SYNC-002/BTB-UI-001/PORTAL-LIVE-001/UPD-001 bleiben Vorgabe. Gezielte Ergänzung der Read-only-/Pending-Behandlung bei Team-/Rechtewechsel und der Vorschau für lokalen Haupt-TB ausdrücklich Teil dieses Auftrags. Bestehende IDs, Originalfotos, Abschluss-PDFs und DB-Inhalte nicht verändert. Unabhängige Review und praktische Regression weiterhin offen.
- Übergabe/Dateiliste/Abnahme: [INTEGRATION_BAUSTELLEN_026.md](uebergaben/INTEGRATION_BAUSTELLEN_026.md); Vertrag [BAUSTELLEN_SYNC_026.md](vertraege/BAUSTELLEN_SYNC_026.md). Zuerst Android compile/unit/lint; anschließend passender Serverstand und200082 auf Pixel/Poco testen. Serverübernahme erfordert separaten Deploymentauftrag. Baustelle behalten, keine doppelte Portalneuanlage/Neuaktivierung.

Stand: 2026-09-26
Branch: `feature/tb-server-1.0`
Fach-HEAD vor Projektsteuerungscommit: `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`
Draft-PR: #6
Version: **1.0.0-dev**
Quellmigrationen: **001–015**

- Migration 014 Teilnehmer-Schichtstatus praktisch bestätigt.
- Migration 015 strukturierte Foto-Metadaten: Quellstand vorhanden, Deployment/Runtime-E2E offen.
- Rollen-/Schichtvertrag, FINALIZED-read-only und Teamlead-Finalize verbindlich.
- Delta-Wait/15×15-Quellstand vorhanden; Last-E2E offen.
- Finalize-OOM/502-Fix praktisch bestätigt und geschützt.

Bildpfad: Mobile Aufnahmen laut 15×15 nur kleine Sync-Version + Thumbnail + Metadaten. Portal-Uploads dürfen serverseitige Quelldatei bleiben. Vor Serveränderung zuerst Android-Upload auditieren.

Nächste Schritte: Hauptgerät↔Server/Portal E2E → HELPER/Poco → IMG-001 Android-Implementierung → Migration 015/Rendition-E2E; Gesamtlast später gemäß E2E-PRIO-001.

Historischer Tracker: `projektsteuerung/archiv/SERVER_STATUS_ALT_2026-09-26.md`

## Praxisnachtrag – Delta-Wait 255 / neue Testreihenfolge

Praktischer Staging-Kapazitätsteiltest für `/api/v1/sync/changes/wait`:
- 255 getrennte Portal-Sessions vorbereitet;
- 255/255 Requests erfolgreich, 0 Fehler;
- Wall 16,43 s, Median 14,28 s, Maximum 15,79 s;
- Testsession-Cleanup meldete zunächst 253/255; über gezielte DB-Kontrolle wurden 7 noch aktive Python-Test-Sessions gefunden und ausschließlich diese Test-Sessions auf `revoked_at` gesetzt;
- anschließende Kontrolle: 0 aktive Python-Test-Sessions.

Status: **KAPAZITÄTSTEILTEST BESTANDEN**, aber **kein vollständiger 15×15-E2E**.

Neue Server-Reihenfolge:
1. verantwortliches TB-Handy ↔ Server/Portal funktional E2E;
2. HELPER/Poco-Syncfehler reproduzierbar eingrenzen und beheben;
3. danach weitere gemeinsame Fach-E2Es;
4. vollständige Lastwiederholung nach 8-GB-RAM-Ausbau bzw. auf neuem NAS.

Keine Server-Performanceänderung aus diesem Test ableiten, solange kein reproduzierbarer funktionaler Engpass vorliegt.


## IMG-001 Quellaudit
Status: **SERVER-RENDITIONS VORHANDEN / ANDROID-NUTZUNG FEHLT**

- Upload-Complete speichert empfangene Bytes unverändert; kein heimlicher Server-Downscale von APP-Uploads.
- APP-Volloriginale auf dem NAS sind aktuell Folge des Android-Uploads.
- `image_renditions.py`: `thumb` 320×320/JPEG72 und `preview` 1280×1280/JPEG82, gecacht und speicherbegrenzt.
- Dateiliste und Metadaten liefern Rendition-URLs.
- Für den Kernfehler ist kein Server-Ersatzumbau vorgesehen. Android soll kleine Sync-Dateien senden und mobile Renditions nutzen.
- Migration 015/Rendition-/Metadaten-Runtime-E2E bleibt offen.

## BTB-SYNC-001 Praxisfehler 26.09
- Pixel hat neuen BTB 26.09 angelegt.
- Portal zeigt weiterhin nur bis 25.09.
- Poco sieht 26.09 kurz, anschließend „kein BTB vorhanden“.
- Serverdiagnose hat Vorrang: Team-`active_btb_id`, BTB-Status/Teamzuordnung, `client_entity_key`, Versionen, Change-Feed und Team-BTB-API prüfen. Keine DB-/API-Änderung auf Verdacht.

### BTB-SYNC-001 Diagnose – kein Serverfix vor Versionsgleichstand
DB-Diagnose zeigt Pixel mit Android-App `1.3.4` und Poco mit `1.3.19`. Change-Feed enthält mehrere Updates desselben BTB bis mindestens Version 19. Server-/DB-Änderungen bleiben gesperrt, bis der Pixel auf dem aktuellen Android-Zielstand getestet wird.

### Geräte-Metadaten app_version sind Aktivierungs-Snapshot
Quellprüfung:
- `/api/v1/device/login` schreibt `app_version` und `app_build` in `tb.devices`.
- `current_device_user` aktualisiert bei normalen Requests nur `last_seen_at`.
- Nach einem normalen APK-Update ohne Neuaktivierung bleibt daher die alte App-Version in `tb.devices`.

Folge: `tb.devices.app_version` darf aktuell nicht als Runtime-Versionsnachweis für E2E verwendet werden. Kein Serverfix daraus ohne separaten Auftrag.

### BTB-SYNC-001 – Server-Existenz bestätigt / Update-Serie
- 26.09-BTB `ba2144b7-9ede-4f53-abdc-1accd172e875` existiert serverseitig.
- Change-Feed zeigt in kurzer Folge Updates bis mindestens Version 19.
- Kein Nachweis eines fehlenden Pixel-Uploads; Ursache liegt nach aktuellem Stand hinter dem Server-Eingang bzw. in Lifecycle/Read-Modell.
- `active_btb_id`, Teamzuordnung, Status und Teilnehmerzustände müssen noch direkt für diesen BTB geprüft werden.

### BTB-SYNC-001 – DB-Lifecycle bestätigt

- Direkter DB-Check für BTB `ba2144b7-9ede-4f53-abdc-1accd172e875`:
  - Baustelle: `GE Immenstadt - Kempten`
  - Team: `pixeljens`
  - Team-Lead: `zinkel.Jens`
  - `teams.active_btb_id` == BTB-ID
  - `active_date=2026-09-26`
  - `active_status=ACTIVE`
  - BTB selbst: `status=ACTIVE`, `version=19`
  - Client-BTB-ID vorhanden
  - Poco = HELPER / participant `WORKING`
  - zinkel.Jens = LEAD / participant `WORKING`
- Damit ist der kanonische Server-Lifecycle für den 26.09-BTB korrekt. Ursache liegt jetzt im Portal-/Android-Lese-/Anwendungspfad, nicht in fehlendem `active_btb_id` oder Teilnehmerstatus.

Kein Server-DB-Fix auf Verdacht.

### BTB-SYNC-001 Portal-Read bestätigt
Portal-Neuladen zeigt den 26.09-BTB korrekt als ACTIVE und verlinkt denselben `active_btb_id`. Kein weiterer Server-/Portal-Fix aus diesem Befund.



