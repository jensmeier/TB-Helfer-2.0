# TB-Helfer 2.0 – ANDROID Übergabe Auto-/Delta-Sync 15×15

Stand: 24.09.2026  
Basis: `docs/TB-SYNC_15x15_ZIELMODELL.md`

## Verbindliche neue Benutzeranforderung

Die Android-Auswahl:
- Manuell
- Stündlich
- Täglich

soll entfernt werden.

Normaler Fachsync läuft automatisch als kleiner Delta-Sync. Niemand soll im Mehrgerätebetrieb regelmäßig „Synchronisieren“ drücken müssen.

`Neu laden` bleibt ausschließlich Recovery/Vollabgleich.

## Android Phase 1

1. `SyncEinstellungenSpeicher.Zeitplan` und die Zeitplan-Auswahl aus `EinstellungenFragment` entfernen.
2. Bestehenden periodischen **Vollsync** nicht als Auto-Ersatz einfach alle 15 Minuten laufen lassen.
3. Persistenten `SelectiveSyncWorker` + Dirty-Scope gemäß bestehender Übergabe `TB-ANDROID_SELEKTIVER_SYNC_2026-09-23.md` bauen.
4. Erstes Objekt: BTB.
5. `BautagebuchSpeicher` markiert genau den geänderten `btbId`.
6. Mehrere schnelle Änderungen desselben BTB coalescen/debouncen.
7. Nur den passenden Pending-BTB senden.
8. `Neu laden` ruft weiterhin den geschützten vollständigen `SyncWorker` auf.

## Android Phase 2

Nach Server-Push-/Change-Scope:
- Push-Wake registrieren;
- Push enthält keine Fachinhalte;
- Change-Cursor persistent speichern;
- Push/App-Resume/Netz-Rückkehr → Delta-Worker;
- `/api/v1/sync/changes` verwenden, nicht den Katalog-Changefeed als Ersatz;
- BTB-Event → nur `GET /api/v1/btbs/{id}`;
- Cursor erst nach erfolgreicher lokaler Anwendung fortschreiben;
- verpasster Push wird über Resume/periodischen Delta-Fallback aufgeholt.

## Lastziel

15 TB × 15 Helfer + 15 Portal = 255 aktive Clients als Planungsfall.

Mobile Geräte dürfen deshalb keinen dauerhaften 2-Sekunden-SSE-Poll verwenden.

## Medien

Später:
- Sync-Foto optimiert;
- Thumbnail;
- Lazy-Download;
- Originalarchiv getrennt.

## Zuständigkeit

`app/**` wird im ANDROID-Projekt umgesetzt. SERVER liefert Change-Feed, Push-Zielgruppen, DB-Pooling und Live-Notifier.

## Ergänzung 24.09.2026 – Fotos und Schichtende

### Fotos

- Mobiles Kameroriginal bleibt auf dem Aufnahmegerät.
- Normaler Online-Sync überträgt nur:
  - stabile Foto-ID und Metadaten;
  - kleine Sync-Version (Richtwert ca. 1920 px längste Kante, JPEG 80–82 %);
  - Thumbnail ca. 512 px.
- Kein automatischer Volloriginal-Upload aus der normalen Kamera-Syncstrecke.
- Bildlisten müssen trotzdem vollständig aus Metadaten aufgebaut werden.
- Portalbilder: Server/NAS darf die dort hochgeladene Quelldatei behalten; Android lädt nur kleine Sync-Version/Thumbnail.
- Späterer Originalexport Handy → Laptop/USB → Haupt-TB benötigt stabile Foto-/BTB-/Benutzer-ID und Manifest/Prüfsumme.

### Schichtende

Nicht den gemeinsamen BTB durch jeden Helfer finalisieren.

Neuer Rollenvertrag:
- jeder beteiligte Nutzer: eigener Status `ARBEITET` / `FERTIG` (optional `NICHT_BETEILIGT`);
- Aktion in App: `Schicht fertig` / `Beitrag fertig`;
- davor selektiven Sync der letzten Fachdaten und kleinen Bild-Versionen sicherstellen;
- `FERTIG` beendet nur diesen Nutzerbeitrag;
- eigener Beitrag darf bis zum finalen Teamabschluss bewusst wieder geöffnet werden;
- nur verantwortlicher Haupt-TB/Teamleiter besitzt `BTB endgültig abschließen`;
- Haupt-TB sieht alle Teilnehmerzustände;
- Normalfall: alle beteiligten Nutzer fertig;
- offene Nutzer bei Ausnahmeabschluss sichtbar nennen und ausdrückliche Bestätigung verlangen;
- USB-Volloriginale sind kein technisches Finalize-Kriterium.

Bestehende Server-1.0-BTB-Finalize-/Original-Snapshot-/PDF-Regeln dürfen dabei nicht ersetzt werden.

## Ergänzung – rollenabhängige BTB-Aktionen

Für die spätere Android-Umsetzung verbindlich:

### HELFER
- `btnPdfFinal` darf fachlich nicht mehr `BTB Abschluss` auslösen.
- sichtbare Abschlussaktion heißt **`Schicht Ende`**.
- `Schicht Ende`:
  1. lokalen BTB speichern;
  2. selektiven Sync abwarten;
  3. kleine Bildrenditionen/Metadaten bestätigen;
  4. eigenen Teilnehmerstatus serverseitig auf `FERTIG` setzen.
- offline: `Schichtende vorgemerkt / Synchronisation ausstehend`; noch nicht als serverseitig fertig ausgeben.
- danach Eingabe read-only; bewusste Aktion `Schicht fortsetzen` bis Haupt-TB finalisiert.
- `btnPdf` / PDF-Entwurf ausblenden.
- Abschluss-PDF/BTB-PDF-Viewer aus BTB-Verlauf ausblenden.
- **Originalbilder exportieren** bleibt als eigenständiger Exportweg verfügbar.

### HAUPT-TB / TEAMLEITER
- maßgeblich ist die serverseitige Teamleitung (`teams.lead_user_id`);
- PDF-Entwurf und Abschluss-PDF bleiben verfügbar;
- Teilnehmerstatus anzeigen;
- alleinige Aktion `BTB endgültig abschließen`.

Nicht nur UI prüfen: Server wird den Abschluss-PDF-Zugriff ebenfalls auf Teamleiter/PDF-Recht begrenzen.

## SERVER-Vertrag umgesetzt – Schichtende (24.09.2026)

Der SERVER-Quellstand stellt jetzt bereit:

- Capability `btb_participant_shift_end_v1=true`
- Capability `btb_pdf_team_lead_only_v1=true`
- `GET /api/v1/btbs/{btbId}/participants`
- `POST /api/v1/btbs/{btbId}/shift-finish`
- `POST /api/v1/btbs/{btbId}/shift-resume`
- `PATCH /api/v1/btbs/{btbId}/participants/{userId}` nur Haupt-TB, Payload `{"status":"WORKING"}` oder `{"status":"NOT_PARTICIPATING"}`
- `GET /api/v1/btbs/{btbId}` liefert zusätzlich `participants` und `current_user.is_team_lead / participant_status / can_view_btb_pdf / can_finalize_btb`.
- `POST /api/v1/btbs/{btbId}/finalize`: nur tatsächlicher `teams.lead_user_id`; mit offenen `WORKING`-Teilnehmern 409 `BTB_PARTICIPANTS_NOT_FINISHED`. Bewusster Ausnahmeabschluss: Body `{"allow_unfinished":true}`.
- Helfer mit `FINISHED` erhalten bei weiteren BTB-Schreibversuchen 409 `BTB_PARTICIPANT_FINISHED`, bis `shift-resume` erfolgreich war.
- Abschluss-PDF ist serverseitig nur für den Teamleiter lesbar, auch nicht über den generischen Datei-Content-Pfad.

Wichtig für Android-Reihenfolge:
1. letzten lokalen BTB-Stand speichern;
2. selektiven BTB-Sync vollständig bestätigen;
3. kleine Bild-Sync-Versionen/Metadaten bestätigen (sobald Medienphase umgesetzt ist);
4. **erst dann** `shift-finish` senden;
5. nach erfolgreichem Server-`FINISHED` UI read-only schalten.

Bei Offline/Fehler nur lokal `Schichtende vorgemerkt / Sync ausstehend`; nicht so tun, als sei der Serverstatus bereits `FERTIG`.

Der Serverblock ist bewusst **noch nicht deployt**. Android/Portal-Vertrag zuerst fertigstellen, dann gemeinsame kontrollierte Migration 014 + E2E.

## Portal-Referenz umgesetzt – Android kann dieselbe Rollenlogik übernehmen

SERVER/Portal-Quellstand vom 24.09.2026:

- Portal liest `current_user.is_team_lead` und `participant_status` direkt aus `GET /api/v1/btbs/{id}`.
- Helfer:
  - `Schicht Ende` → `POST /shift-finish`;
  - bei `FINISHED` read-only;
  - `Schicht fortsetzen` → `POST /shift-resume`;
  - keine Abschluss-PDF-Aktion.
- Haupt-TB:
  - Teilnehmerübersicht;
  - `NOT_PARTICIPATING` / `WORKING` für Helfer pflegen;
  - endgültiger Abschluss;
  - bei offenen `WORKING`-Helfern Namen anzeigen + bewusste Bestätigung für `allow_unfinished=true`.
- Portal-Source-Check: `server/checks/93_verify_portal_btb_shift_end_source.py`.

Android soll fachlich **denselben Ablauf** abbilden, aber vor `shift-finish` zusätzlich den selektiven lokalen BTB-/Bild-Sync sicher abschließen.

## Praktischer Befund 24.09.2026 – lokaler BTB-Abschluss erreicht Server nicht zuverlässig

Live-Befund mit installiertem Android **1.3.17 / 200067**:
- Benutzer hat den BTB gegen **19:20 lokal abgeschlossen**;
- Android zeigt den lokalen Abschlusszustand;
- Portal liest denselben Server-BTB später weiterhin als **`ACTIVE`**.

Quellursache / Integrationslücke:
- `BtbModernLifecycleInstaller.closeCurrentBtb(...)` speichert zunächst den BTB;
- `BtbDokumentVersionierung.abschlussBestaetigen(...)` schreibt anschließend direkt `abschlussStatus=ABGESCHLOSSEN` / `istFertig=true` in die lokale BTB-Datei;
- danach wird aktuell nur `SofortSync.anstossen(appContext)` verwendet;
- der neue Phase-1-Vertrag für einen konkreten BTB wäre dagegen `SofortSync.btbAnstossen(appContext, saved.baustelleId, saved.btbId)`.

ANDROID-Auftrag:
1. nach **erfolgreichem** `abschlussBestaetigen` den gerade abgeschlossenen stabilen BTB explizit mit `btbAnstossen(...)` als Dirty-Scope markieren;
2. erst den tatsächlich abgeschlossenen Dateistand exportieren (`status=FERTIG`);
3. Server-`/finalize` bestätigen und erst danach den Serverabschluss als erfolgreich betrachten;
4. generischen Vollsync nicht als notwendige Abschlusslogik verwenden;
5. Offline: Abschluss lokal darf bestehen, aber UI muss Server-Sync ausstehend kenntlich machen, bis Finalize bestätigt ist;
6. Test: lokalen Abschluss durchführen, **ohne** manuellen Syncbutton; Portal muss anschließend denselben BTB als `FINALIZED/Abgeschlossen` sehen.

SERVER-Seite erfindet bei diesem Befund keinen Abschlussstatus. `ACTIVE` im Portal ist der aktuell vom Server gespeicherte Zustand.
