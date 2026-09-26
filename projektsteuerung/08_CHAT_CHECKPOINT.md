# TB-HELFER 2.0 – CHAT CHECKPOINT

Stand: 2026-09-26

Letzter gesicherter Arbeitsblock: **Projektsteuerung vereinheitlicht und verifiziert**.

- Zentrale: `projektsteuerung/`
- alte Top-Level-Ordner `projektstatus/`, `releases/`, `uebergaben/` entfernt
- branch-spezifische Projektregeln nach dem Umzug kontrolliert und auf denselben Einstieg korrigiert
- kein Android-/Server-Fachcode in diesem Schritt geändert
- Android-Fachstand vor Steuerungsumbau: `d272f8a7586cce3ea1eda29100473f7af5d540bb`
- Server-Fachstand vor Steuerungsumbau: `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`
- Android 1.3.19 / 200069: Build/Install/E2E offen
- Server 1.0.0-dev: Migration 015 Runtime-E2E offen
- nächster gemeinsamer Technikpunkt: IMG-001 Bildtransport
- weiterer P0: Poco bekommt keine Daten

Neuer Chat: direkt `projektsteuerung/00_START_HIER.md` lesen und Live-HEADs prüfen.

## Arbeitsentscheidung / Testnachtrag 26.09.2026

- Delta-Wait-Kapazitätsteiltest praktisch: 255 getrennte Portal-Sessions, 255/255 erfolgreich, 0 Fehler.
- Testsession-Reste kontrolliert bereinigt; abschließend 0 aktive Python-Test-Sessions.
- Das ist **nicht** der vollständige 15×15-E2E.
- Neue verbindliche Reihenfolge: zuerst Haupt-TB-Handy ↔ Server/Portal, danach HELPER/Poco, danach weitere Fach-E2Es.
- Vollständigen Lasttest erst nach funktionaler Stabilität und nach geplantem 8-GB-RAM-Ausbau bzw. NAS-Wechsel erneut durchführen.
- Keine Performanceänderungen auf Verdacht.

## Gerätebefund NET-001 – Testbaustelle

- Hauptgerät: Testbaustelle angelegt.
- Sync scheitert unmittelbar mit `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.
- Fachsync ist damit in diesem Lauf noch nicht geprüft.
- Nächster Schritt: öffentliche Domain/API-Erreichbarkeit und danach Geräte-DNS/Netz prüfen.
- Keine Codeänderung auf Verdacht.


## IMG-001 Quellaudit 26.09.2026

- Quellaudit abgeschlossen; kein Fachcode geändert.
- Android lädt BTB-/Mangel-/Abnahmefotos aktuell aus dem lokalen Originalpfad hoch.
- Server speichert empfangene Bytes unverändert; APP-Volloriginal auf NAS ist daher Folge des Android-Uploads.
- Server-Renditions existieren: `thumb` 320 px / `preview` 1280 px.
- Android-Dateiclient nutzt diese Renditions nicht und lädt fehlende Bilder über `/content`.
- Lokales Android-Thumbnail 512 px/Qualität 82 existiert bereits, ist aber nicht in den Online-Sync eingebunden.
- Metadatenlücken: GPS-Genauigkeit nicht persistent; Mangel-/Abnahmefoto-Upload strukturell unvollständig.
- Detailnachweis: `projektsteuerung/uebergaben/IMG-001_BILDTRANSPORT_AUDIT_2026-09-26.md`.
- E2E-PRIO-001 bleibt maßgeblich: NET-001 → Hauptgerät↔Portal → HELPER/Poco → danach IMG-001-Implementierung.

## NET-001 Nachtest – Browser erreichbar
- Auf demselben Hauptgerät sind `https://www.tb-helfer.de` und `https://api-v1.tb-helfer.de/health/live` im Browser erreichbar.
- Öffentliche DNS/API-Erreichbarkeit ist damit grundsätzlich vorhanden.
- Der vorherige App-Fehler kann transient oder app-/prozessspezifisch gewesen sein.
- Nächster Schritt: in der App denselben Sync mit `Neu laden` erneut auslösen und die genaue Ausgabe prüfen.
- Keine Codeänderung auf Verdacht.

## NET-001 Reproduktion – App-only DNS-Fehler
- Browser auf demselben Hauptgerät: Portal + API-Health erreichbar.
- App `Neu laden`: gleicher Fehler `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.
- Damit allgemeine öffentliche DNS-/Cloudflare-Störung als alleinige Ursache ausgeschlossen.
- Nächster Schritt: Android-Netzpfad/Serverbasis/HTTP-Client prüfen; Server/DB nicht auf Verdacht ändern.

## NET-001 – Force-Stop + Mobilfunk ohne Änderung
- App vollständig gestoppt und neu gestartet: gleicher DNS-Fehler.
- WLAN aus, Mobilfunk aktiv: gleicher DNS-Fehler.
- WLAN-spezifische Ursache dadurch unwahrscheinlich.
- Nächster Schritt: Android `Privates DNS`, VPN und DNS-/Adblock-Filter prüfen.
- Keine App-Daten löschen, keine Neuaktivierung, keine Serveränderung auf Verdacht.

## Praxisnachtrag – Pixel synchronisiert, Poco Erstabgleich läuft
- Pixel hat inzwischen wieder mit dem NAS synchronisiert.
- Frühere App-DNS-Störung daher aktuell kein dauerhafter Blocker; Ursache weiterhin unbekannt.
- Poco-App gelöscht, neu installiert und neu aktiviert.
- Poco-Erstabgleich läuft nach >3 Minuten weiter.
- Nächster Schritt: anhand Android-Quellpfad klären, was beim Erstabgleich alles geladen wird und welche Phase die lange Laufzeit verursacht; keine weiteren Resets auf Verdacht.

## BTB-SYNC-001 – 26.09 fehlt/verschwindet
- Poco-Erstabgleich abgeschlossen; Baustelle herunterladen erfolgreich.
- Pixel: neuer BTB 26.09 angelegt.
- Portal: zeigt weiterhin BTBs 23./24./25.09, 26.09 fehlt.
- Poco: 26.09 erscheint kurz, dann „kein BTB vorhanden“.
- Nächster Schritt: zuerst Serverzustand/DB/API/Change-Feed prüfen, danach Android-Snapshot/Active-Pointer. Keine Änderung auf Verdacht.

## BTB-SYNC-001 Diagnose – Pixel 1.3.4 vs Poco 1.3.19
- Server-Geräteabfrage zeigt:
  - Pixel / zinkel.jens: TB-Helfer 1.3.4
  - Poco / poco: TB-Helfer 1.3.19
- Change-Feed zeigt für denselben BTB am 26.09 mehrere UPDATEs bis mindestens Version 19.
- Aktueller Android-Zielstand laut Projektsteuerung: 1.3.19 / 200069.
- Nächster Schritt: Pixel-Version angleichen bzw. 200069-Installation nachweisen; erst danach BTB-SYNC-001 erneut testen.

## Korrektur – Pixel-Version
- Benutzer bestätigt Pixel ebenfalls auf TB-Helfer 1.3.19.
- Serverwert 1.3.4 ist stale: `app_version/app_build` werden nur bei Geräteaktivierung geschrieben; normale Requests aktualisieren nur `last_seen_at`.
- Vorherige Schlussfolgerung „gemischte Android-Versionen“ verworfen.
- BTB-SYNC-001 weiter diagnostizieren: 26.09-BTB + Team-`active_btb_id` + API-Snapshot.

## BTB-SYNC-001 – Servereingang 26.09 bestätigt
- BTB-ID: `ba2144b7-9ede-4f53-abdc-1accd172e875`
- work_date: 2026-09-26
- Change-Feed: wiederholte UPDATEs, sichtbar mindestens Version 7–19.
- Pixel→Server ist damit nicht der fehlende Schritt.
- Nächster Diagnosepunkt: Team/`active_btb_id`/Status/Teilnehmer für exakt diesen BTB, danach Portal- und Poco-Leseweg.

## BTB-SYNC-001 – Server-Lifecycle korrekt

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

## BTB-SYNC-001 – Portal nach Reload korrekt
- Portal zeigt nach vollständigem Reload Team `pixeljens`, aktiven BTB `ba2144b7-9ede-4f53-abdc-1accd172e875`, Status ACTIVE, Datum 26.09.
- Pixel→Server→Portal ist für diesen BTB damit praktisch bestätigt.
- Restfehler ist Android/Poco: BTB kurz sichtbar, danach kein aktiver BTB.
- Nächster Schritt: Android Snapshot-/Active-Pointer-/UI-Pfad diagnostizieren.

## BTB-SYNC-001 – Poco erneut reproduziert nach Portal-Bestätigung
- Portal 26.09 ACTIVE bestätigt.
- Poco zeigt 26.09 kurz und verliert ihn unmittelbar wieder.
- Restursache ist damit auf Android/Poco eingegrenzt.
- Nächster Schritt: Active-Pointer/Marker/UI-Pfad im aktuellen Android-HEAD analysieren.

## BTB-SYNC-001 – Source-Ursache Active-Pointer
- Quellpfad isoliert:
  - `CollabLocalBridge.applyServerStateToLocal()` setzt bei `serverActive=true` den lokalen Active-Pointer korrekt.
  - `BtbModernLifecycleInstaller` fragt über `BtbUiSaveBridge.aktuell()` ungefähr alle 600 ms den aktiven BTB ab.
  - `BtbActiveContext.laden()` kann danach den gerade gesetzten Pointer wieder löschen, wenn `BtbDokumentVersionierung.status(...)=ABGESCHLOSSEN` ergibt.
  - Dieser lokale Dokumentstatus wird dort derzeit geprüft, **ohne einen bereits bestätigten `BtbServerStateStore.active=true` als Vorrang zu behandeln**.
- Das passt exakt zum Praxisbild: 26.09 erscheint kurz, nach etwa einer Sekunde „kein BTB vorhanden“.
- Serververtrag ist bereits kanonisch: solange Server-`active_btb_id` auf den BTB zeigt und Teilnehmer `WORKING` ist, darf ein lokaler/staler Abschlusszustand den Active-Pointer nicht lösen.
- Nächster Zuständigkeitswechsel: ANDROID muss `BtbActiveContext` korrigieren und danach Build/Install/Poco-E2E durchführen.
- SERVER-Seite bleibt unverändert; Server-Lifecycle/Portal sind bestätigt.

## IMG-PORTAL-001 – 4 Serverbilder, 1 Portalbild
- Neuer Befund am aktiven BTB 26.09.
- Server/NAS: 4 zugehörig wirkende Bilder.
- Portal: nur 1 Bild sichtbar.
- Nächster Schritt: Attachment-Zuordnung/Metadaten und BTB-API prüfen; danach erst Portal-Renderpfad.

## PORTAL-LIVE-001 – F5 nicht akzeptabel
- Nach manuellem Browser-Reload (F5) erscheinen die zuvor fehlenden BTB-Bilder vollständig im Portal.
- Damit ist die Bildzuordnung/Serverablage grundsätzlich vorhanden; der aktuelle Praxisfehler ist **fehlende Live-Aktualisierung der offenen Portalansicht**.
- Benutzeranforderung: Neue BTBs/Bilder/Fachdaten sollen in einer offenen Portalansicht automatisch sichtbar werden; F5 darf für normalen Betrieb nicht nötig sein.
- Bestehende Architekturregel gilt: offene Portalansichten dürfen live aktualisieren, aber nicht über 2-Sekunden-DB-Polling pro Browser. Vorhandener Change-Feed/SSE-/Long-Poll-Pfad ist zu verwenden.
- Nächster Server-Arbeitsblock: bestehende Portal-Templates und `/api/v1/sync/events`/`changes/wait` prüfen und gezielten Live-Refresh implementieren.

## PORTAL-LIVE-001 – Source-Fix gesichert
- Source-Fix Commit: `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- `v1_btb.html` und `v1_worksite.html` abonnieren jetzt den vorhandenen worksite-bezogenen SSE-Change-Feed `/api/v1/sync/events`.
- Relevante Änderungen werden entprellt und gezielt nachgeladen; auf der BTB-Seite insbesondere `BTB`, `BTB_ENTRY`, `BTB_PARTICIPANT` und `FILE`.
- Baustellenseite aktualisiert Teams/BTBs, Medien, Mängel, Abnahmen, Mitglieder und Baum abhängig vom Change-Typ.
- Kein 2-Sekunden-Polling; vorhandener gemeinsamer Change-Hub wird verwendet.
- Source-Assertions für EventSource/SSE-Pfad und gezielte Reload-Funktionen: **PASS**.
- Status bleibt: **QUELLSTAND FERTIG / DEPLOY OFFEN / E2E OFFEN**.
- Deployment nach Synology-Regel: GitHub Desktop → File Station → Container Manager.
- Danach E2E mit offenem Portal und neuem Handyfoto; F5 darf nicht mehr nötig sein.

## BTB-SYNC-002 – asymmetrischer Sync / lokale Eingabe verloren
- Praxisbefund am aktiven BTB 26.09:
  - Eingabe/Änderung auf dem Pixel erscheint **nicht** im Portal.
  - Änderung im Portal kommt anschließend auf dem Pixel an.
  - Beim Eintreffen des Portalstands wird die zuvor auf dem Pixel eingegebene Änderung überschrieben/gelöscht.
- Das ist ein P0-Datenverlust-/Konfliktfehler. Bis zur Ursache keine weiteren produktiven BTB-Eingaben für diesen Testdatensatz erzwingen.
- Vor Änderung prüfen:
  1. ob die Pixel-Eingabe lokal bereits gespeichert ist;
  2. ob dafür eine Android-Outbox/BTB-Pending-Mutation entsteht;
  3. ob diese Mutation zum Server gesendet oder durch einen vorherigen Server-Snapshot verworfen wird;
  4. Server-BTB-Version/Entry-Versionen und Change-Feed vor/nach Pixel- und Portaländerung;
  5. Merge-/Conflict-Regel für dieselbe BTB-Sektion bzw. denselben Slot.
- Nächster Schritt: aktuellen Android- und Server-Quellpfad diagnostizieren, bevor irgendein Fix gebaut wird.

## BTB-SYNC-002 – Source-Ursache bestätigt
- **Source-Ursache gefunden im aktuellen Android-HEAD `ef7077923e2917ac615b9f3b5143ac2638fb92ac`:**
  - `BautagebuchSpeicher.speichern()` speichert die Pixel-Änderung lokal und markiert den BTB nur im `SelectiveSyncStore` als dirty.
  - `SelectiveSyncWorker` lädt bei bereits bekanntem Server-BTB **zuerst** `loadBtbAccess(...)`, speichert den Server-Snapshot und ruft **vor** `exportBtbChange(...)` bereits `bridge.applyServerStateToLocal(...)` auf.
  - Zu diesem Zeitpunkt existiert noch keine `collabPending`-Mutation für die neue Pixel-Änderung; der Guard in `applyServerStateToLocal()` kann sie daher nicht schützen.
  - Ergebnis: der ältere/andere Serverstand überschreibt die lokal gespeicherte Pixel-Änderung, erst danach wird exportiert. Damit wird der überschriebenen Serverstand statt der Pixel-Änderung hochgeladen.
- Das erklärt exakt den Praxisbefund: Pixel→Portal fehlt; Portal→Pixel kommt an und entfernt die Pixel-Eingabe.
- Server/API/DB sind für diesen Fehler nicht zu ändern.
- Nächster Arbeitsblock gehört in ANDROID: SelectiveSyncWorker-Reihenfolge korrigieren, anschließend Build/Install und denselben 26.09-BTB mit Pixel↔Portal erneut testen.
- SERVER bleibt unverändert.


## PORTAL-LIVE-001 – nächster Serverblock 26.09.2026
- Benutzer bestätigt: Pixel↔Portal läuft aktuell in beide Richtungen.
- Jetzt gewünscht: Portal automatisch aktualisieren, damit F5 nicht mehr nötig ist.
- Source-Fix existiert bereits im Server-Branch, Commit `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- Kein neuer Fachcode nötig, bevor dieser Stand deployed und praktisch getestet wurde.
- Nach Deploy E2E: Portal geöffnet lassen → Pixel-Text oder Foto ändern → Portal muss ohne F5 nachziehen.
