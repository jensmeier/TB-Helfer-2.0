## Portal-BTB 24.09.2026 – Foto-Metadaten, Dedupe und Abschluss-Syncbefund

- Live-Test nach Portal-V5:
  - reproduzierbarer Fehler beim Löschen einer Firma: `can't access property "id", editingEntry is null`;
  - Portal zeigte den BTB weiterhin als `Aktiv`, obwohl der Benutzer den BTB auf Android gegen 19:20 lokal abgeschlossen hatte;
  - Fotoansicht sollte die bereits auf Android sichtbaren Fachinformationen inklusive GPS wieder anzeigen.
- **Firma-Löschfehler behoben:** die Firmen-ID/Entry-ID wird vor dem Schließen des Dialogs lokal gesichert; nach `closeEntryEditor()` wird nicht mehr auf das auf `null` gesetzte `editingEntry` zugegriffen.
- **Foto-Metadaten ohne Neu-Upload:** Androids vorhandenes `BTBP1__`-Binärformat wurde gegen `BtbTeamMediaSync` geprüft und im Portal-Template kompatibel decodiert:
  - Aufnahmezeit;
  - GPS Lat/Lon;
  - Höhe/Achsabstand soweit vorhanden;
  - KM;
  - Bildtyp;
  - Indikator;
  - Firma;
  - Kurztext;
  - Objektkennung/Bereich.
- Foto-Karten zeigen Kurztext als Titel, Foto-Nummer/Größe, Zeit · KM · Firma, Ort/Bereich und anklickbare GPS-Koordinaten zu Google Maps.
- Fotos werden zusätzlich ihrem fachlichen Punkt 4–8 zugeordnet:
  - `FOTODOKUMENTATION -> 4. Ausgeführte Arbeiten`
  - `ABNAHMEN -> 5. Abnahmen`
  - `EINWEISUNG_UNTERWEISUNG -> 6. Einweisung / Unterweisung`
  - `BESONDERHEITEN -> 7. Besonderheiten`
  - `MAENGEL_SCHAEDEN -> 8. Mängel / Schäden`
- Deduplizierung:
  - Foto-Liste dedupliziert identische Serverdateien bevorzugt über `sha256`;
  - exakt identische Fachzeilen werden in der normalen Anzeige nur einmal gerendert;
  - ein identischer Portal-Neueintrag wird mit `Ein identischer Eintrag ist bereits vorhanden.` blockiert;
  - Foto-Ereignis wird nicht ein zweites Mal unter 4–8 ausgegeben, wenn Zeit + Kurztext bereits als Fachzeile repräsentiert sind;
  - Alt-Dubletten werden nicht automatisch aus der DB gelöscht.
- **Abschlussstatus-Befund:** Die Portal-Pille `Aktiv` kommt direkt aus Server-`btb.status=ACTIVE`; das Portal erfindet den Status nicht. Android 1.3.17/200067 setzt beim lokalen Abschluss über `BtbDokumentVersionierung.abschlussBestaetigen(...)` die lokale BTB-Datei auf abgeschlossen, ruft anschließend in `BtbModernLifecycleInstaller.closeCurrentBtb(...)` aber noch den generischen `SofortSync.anstossen(...)` statt des neuen selektiven `SofortSync.btbAnstossen(localBaustelleId, clientBtbId)` auf. Praktisch ist der Abschluss dieses BTB daher auf dem Server noch nicht bestätigt.
- SERVER ändert den Android-Code nicht. Android-Handoff ergänzt: der Abschluss muss nach erfolgreichem lokalen Abschluss den konkreten BTB erneut als Dirty-Scope markieren und selektiv bis `FINALIZED` bestätigen.
- Template-JavaScript syntaktisch geprüft: **OK**. Source-Check `97_verify_portal_btb_photo_meta_dedupe_source.py` + CI-Eintrag ergänzt.
- **Status: PORTAL-FIX QUELLSTAND FERTIG / LIVE-REDEPLOY OFFEN; ANDROID-ABSCHLUSS-SYNC E2E/BUGFIX OFFEN.**
- Keine DB-Migration. Migration 014 weiterhin nicht deployen.
- `STATUS.xlsx` weiterhin nicht blind überschrieben.

## Portal-BTB 24.09.2026 – Hinzufügen/Löschen ergänzt

- Benutzerfeedback aus Live-Portal: vorhandene Einträge waren bearbeitbar, aber neue Fachzeilen konnten nicht bequem hinzugefügt und bestehende nicht direkt gelöscht werden.
- Bestehende Server-API geprüft: `POST /api/v1/btbs/{btb_id}/entries`, `PATCH .../entries/{id}` und `DELETE .../entries/{id}` sind bereits vorhanden; kein neuer Backend-Endpunkt und keine DB-Migration notwendig.
- Android-Vertrag geprüft: Android verarbeitet nur granulare, verwaltete Einträge über `_tb_android_managed=true` + `_tb_android_slot`. Deshalb erzeugt das Portal **keine eigenen Sonderdatensätze**, sondern exakt kompatible Slots:
  - Firma: `firma:<firmaId>`
  - Personal: `firma:<firmaId>:personal:<index>`
  - Gerät: `firma:<firmaId>:geraet:<index>`
  - Arbeiten: `firma:<firmaId>:arbeit:<index>`
  - Allgemeine Arbeiten: bestehender interner `notizen:0`-Slot, weiterhin sichtbar unter Punkt 4
  - Abnahmen: `abnahme:<index>`
  - Einweisung/Unterweisung: `einweisung:<index>`
  - Besonderheiten: `besonderheit:<index>`
  - Mängel/Schäden: `mangel:<index>`
- Portal-V5-Quellstand:
  - `+ Firma` in Punkt 3;
  - pro Firma `+ Personal` bzw. `+ Gerät`;
  - in Punkten 4–8 jeweils `+ Eintrag`;
  - sichtbare `Bearbeiten`- und `Löschen`-Aktionen pro Fachzeile;
  - Firmenlöschung warnt und entfernt zugehörige Personal-/Geräte-/Arbeitszeilen mit;
  - allgemeine Alt-/Notizzeilen unter Punkt 4 lassen sich einzeln löschen, ohne den gesamten Mehrzeilen-Servereintrag zu entfernen.
- Keine Roh-JSON-Oberfläche wieder eingeführt.
- Template-JavaScript syntaktisch geprüft: **OK**.
- Source-Check `96_verify_portal_btb_crud_source.py` + CI-Eintrag ergänzt.
- **Status: QUELLSTAND FERTIG / LIVE-REDEPLOY + CRUD-SICHTPRÜFUNG OFFEN.**
- Keine DB-/Migration-/Android-Codeänderung. Migration 014 weiterhin nicht deployen.
- `STATUS.xlsx` weiterhin nicht blind überschrieben.

## Portal-Sichtprüfung 24.09.2026 – kein eigener Notiz-Abschnitt

- Benutzer hat per Live-Screenshot bestätigt: Portal zeigte fälschlich `Weitere Angaben / Notizen` mit `test`, `test`, `@ORT=3 • Schwerbau: test`.
- Android-Fachlogik geprüft: `BtbDruckModellBuilder` nimmt `entry.notizen` ausdrücklich über `manualEventsFromMultiline(...)` in **4. Ausgeführte Arbeiten** auf; `notizen` ist damit nur ein interner Speicher-/Legacybegriff und kein eigener BTB-Abschnitt.
- Portal korrigiert:
  - kein sichtbarer Abschnitt `Weitere Angaben / Notizen` mehr;
  - Serversektionen `NOTIZEN` und `ALLGEMEIN` werden nur für die Darstellung in **4. Ausgeführte Arbeiten** eingemischt;
  - mehrzeilige Alttexte werden zeilenweise dargestellt;
  - interne `@ORT=<id>`-Marker werden in der normalen Anzeige ausgeblendet; der eigentliche Text bleibt erhalten;
  - Persistenz/Datenvertrag bleibt unverändert.
- Template-JavaScript syntaktisch geprüft: **OK**.
- Source-Check 95 ergänzt: separater Notizblock ist verboten; Legacy-Notizen müssen in Punkt 4 gerendert werden.
- **Status: QUELLSTAND FERTIG / LIVE-REDEPLOY + SICHTPRÜFUNG OFFEN.**
- Foto-GPS-/Metadatenanzeige bleibt der unmittelbar folgende Portalblock; vorhandene `BTBP1__`-Metadaten können dafür rückwirkend ausgewertet werden.
- `STATUS.xlsx` weiterhin nicht blind überschrieben.

## Portal-Sichtprüfung 24.09.2026 – BTB V3 nach Benutzerfeedback

- Zweite Live-Sichtprüfung per Screenshots bestätigt weitere konkrete UX-Anforderungen.
- Bedeutung der bisherigen kleinen Zahl im Abschnittskopf: reine Anzahl der granularen Server-Einträge; für den Arbeitsablauf nicht hilfreich und deshalb entfernt.
- Verbindliches Portal-Layout jetzt:
  - `1. Berichtszeit` einklappbar;
  - `2. Wetter` einklappbar und aus dem vorhandenen Android-Header `tb_helfer_android_v1.wetter / weather / weatherSlots` lesbar;
  - `3. Personal / Geräte`: zwei nebeneinander liegende, getrennt einklappbare Bereiche (mobil untereinander);
  - `4. Ausgeführte Arbeiten`, `5. Abnahmen`, `6. Einweisung / Unterweisung`, `7. Besonderheiten`, `8. Mängel / Schäden` **immer sichtbar**, auch ohne Einträge;
  - weitere Angaben/Notizen und Fotos einklappbar.
- Fehler in den bisherigen Section-Mappings korrigiert: Android/Server verwenden `EINWEISUNG_UNTERWEISUNG` und `MANGEL_SCHADEN`; Portal nutzt jetzt exakt dieselben Schlüssel.
- Sämtliche sichtbaren Roh-JSON-/Sortierungs-/Versions-/Dateinamen-Diagnosefelder aus der normalen Portaloberfläche entfernt.
- Fachliche Bearbeitungsdialoge bleiben; beim Speichern werden unbekannte/interne Payload-Felder unverändert mitgeführt.
- Kopfdatenbearbeitung erhält den bestehenden `current.header` unverändert; Wetter-/Android-Metadaten werden dadurch nicht überschrieben.
- Fotos zeigen nur Foto-Nummer, Aufnahmezeit, Größe und Vorschau; technischer Originaldateiname ist nicht mehr Teil der normalen Ansicht.
- Wenn Punkt 4 nach diesem Deploy leer erscheint, obwohl Android dort Einträge zeigt, ist das kein Darstellungsfehler mehr, sondern ein gezielt zu prüfender Android→Server-`ARBEIT`-Syncbefund.
- Template-JavaScript syntaktisch geprüft: **OK**. Source-Checks 37/93/94/95 auf den neuen UI-Vertrag konsolidiert.
- **Status: PORTAL V3 QUELLSTAND FERTIG / LIVE-REDEPLOY + SICHTPRÜFUNG OFFEN.**
- Keine DB-/API-/Android-Codeänderung. Migration 014 weiterhin nicht deployen.
- `STATUS.xlsx` weiterhin nicht blind überschrieben.

## Portal-Sichtprüfung 24.09.2026 – BTB-Ansicht nach Android-Vorbild nachgebessert

- Benutzer hat die erste lesbare Portal-BTB-Version praktisch auf Synology geprüft und mehrere konkrete Darstellungsfehler per Screenshot bestätigt.
- Reproduzierte Portalprobleme:
  - `Schicht Ende` und `0 fertig · 0 arbeitet` wurden angezeigt, obwohl auf Synology bewusst nur das neue Template, aber noch nicht Migration 014 + neuer Teilnehmer-Backendvertrag installiert waren.
  - `FIRMA`-Metadaten wurden als eigene sichtbare Rohdaten angezeigt, einschließlich technischer Felder wie `isDeleted`, `visibleInPersonal`, `visibleInMaschinen`.
  - Personal und Geräte erschienen doppelt: strukturierte Android-Zeilen plus zusätzliche Legacy-Zusammenfassungen aus `personal-frei:*` / `geraet-frei:*`.
  - `Bearbeiten` öffnete weiterhin Sortierung/Version/Roh-JSON statt einer fachlichen Eingabemaske wie in Android.
  - BTB-Fotos zeigten lange technische Dateinamen als Hauptüberschrift.
- Quellfix:
  - Teilnehmer-/Schichtkarte und Schichtende-Aktionen werden nur noch angezeigt, wenn die BTB-API tatsächlich `participants` **und** `current_user` liefert. Damit täuscht ein isoliert aktualisiertes Template den noch nicht deployten Migration-014-Vertrag nicht mehr vor.
  - `FIRMA` ist nur noch Gruppierungs-/Metadatenquelle und wird nicht als eigene Rohdaten-Sektion gerendert.
  - Personal/Geräte werden nach Firma gruppiert, Firmenzeit sichtbar kompakt dargestellt und Android-Legacy-Zusammenfassungen bei vorhandenen strukturierten Zeilen nur in der **Anzeige** dedupliziert. Persistierte Daten bleiben unverändert.
  - `Bearbeiten` öffnet jetzt einen Portal-Dialog mit fachlichen Feldern:
    - Firma: Name + Personal-/Gerätezeiten;
    - Personal: Anzahl + Rolle;
    - Geräte: Anzahl + Bezeichnung;
    - Arbeiten/Abnahmen/Einweisung/Besonderheiten/Mängel/Notizen: Uhrzeit/KM/Text, soweit vorhanden.
  - Sortierung/Version/JSON bleiben nur unter `Technische Details` zur Diagnose.
  - Status `ACTIVE/REWORK/FINALIZED` wird als `Aktiv/Nachbearbeitung/Abgeschlossen` angezeigt; BTB-ID ist nur noch kleine technische Zusatzinfo.
  - Fotos werden als `Foto 1`, `Foto 2`, ... mit Aufnahmezeit/Dateigröße dargestellt; technischer Originalname liegt hinter `Dateiinformation`.
- Daten-/Syncvertrag wurde nicht verändert; keine DB-/API-/Android-Codeänderung.
- Template-JavaScript wurde gegen den aktuellen Branch syntaktisch kompiliert: **OK**.
- Source-Check `95_verify_portal_btb_android_like_editor_source.py` + CI-Eintrag ergänzt.
- **Status: QUELLSTAND FERTIG / ERNEUTER LIVE-DEPLOY DER EINEN TEMPLATE-DATEI + SICHTPRÜFUNG OFFEN.**
- `STATUS.xlsx` weiterhin nicht blind überschrieben.

## Praxistest + Portalansicht 24.09.2026 – Android-BTB im Portal lesbar

- Android **1.3.17 / 200067** ist laut Android-Branch installiert (`feature/tb-android-2.0` HEAD `d7869a2d`).
- Praktischer Teilnachweis durch Benutzer: auf dem Android-Gerät eingegebener BTB-Testtext ist serverseitig angekommen und im Portal-BTB sichtbar.
- Dieser Nachweis bestätigt **Android → Server → Portal für den geänderten BTB-Inhalt praktisch**, aber noch nicht die vollständige Selektivität. API-Logprüfung, dass dabei keine Katalog-/Baum-/Track-/Mangel-/Abnahme-Vollpfade liefen, bleibt offen.
- Benutzerfeedback zur Portalansicht: bisherige Standarddarstellung mit Sortierung, Version und Roh-JSON ist im Arbeitsalltag zu unübersichtlich und der eingegangene Text schwer auffindbar.
- Portal-BTB-Quellstand deshalb auf eine **lesbare App-nahe Abschnittsdarstellung** umgestellt:
  - Einträge werden nach fachlichen Abschnitten gruppiert;
  - feste Bezeichnungen u. a. `Personal`, `Geräte / Maschinen`, `Ausgeführte Arbeiten`, `Abnahmen`, `Einweisung / Unterweisung`, `Besonderheiten`, `Mängel / Schäden`, `Notizen`;
  - Android-interne Felder `_tb_android_*` werden in der normalen Ansicht ausgeblendet;
  - `@ALLGEMEIN •`-Präfixe werden nur für die Anzeige bereinigt, die gespeicherten Daten bleiben unverändert;
  - Sortierung, Version und JSON erscheinen nur noch hinter `Bearbeiten`;
  - Berichtsdatum/Beginn/Ende stehen als kompakte Übersicht oben;
  - technischer JSON-Bearbeitungsweg bleibt erhalten und verändert den Sync-/Datenvertrag nicht.
- Bestehende Rollenlogik `Schicht Ende` / Haupt-TB-Finalisierung / PDF-Sperre wurde nicht ersetzt.
- Source-Check `94_verify_portal_btb_readable_source.py` ergänzt und im Server-CI eingetragen; Connector-Source-Assertions bestanden.
- **Status: PORTAL-LESBARKEIT QUELLSTAND FERTIG / LIVE-DEPLOY + PRAKTISCHE SICHTPRÜFUNG OFFEN.**
- Kein `app/**` im SERVER-Projekt geändert.
- `STATUS.xlsx` weiterhin nicht blind überschrieben; GitHub-Server-XLSX ist neuer als der Android-XLSX-Stand und die binäre Datei liegt in diesem Arbeitsweg nicht sicher editierbar vor.

## Quellblock 24.09.2026 – Portal für Schichtende vorbereitet

- Portal-BTB-Seite auf den neuen Teilnehmer-/Teamleitervertrag angepasst.
- Helfer sieht bei aktivem BTB **`Schicht Ende`**; nach serverseitigem `FINISHED` nur **`Schicht fortsetzen`**.
- Nach `FINISHED` werden Kopfdaten-/Eintrags-/Foto-Bearbeitung im Portal ausgeblendet; der Server-Guard bleibt zusätzlich maßgeblich.
- Helfer erhält im Portal keine Abschluss-PDF-Aktion.
- Haupt-TB (`current_user.is_team_lead`) sieht:
  - Teilnehmerübersicht mit `ARBEITET / FERTIG / NICHT BETEILIGT`;
  - Pflege `Nicht beteiligt` / `Als beteiligt markieren`;
  - `BTB endgültig abschließen`;
  - Abschluss-PDF und Nachbearbeitung.
- Bei offenen Helfern zeigt das Portal vor dem endgültigen Abschluss die Namen und verlangt eine bewusste Bestätigung; erst dann wird `allow_unfinished=true` gesendet.
- Source-Check `93_verify_portal_btb_shift_end_source.py` ergänzt und im Server-CI-Workflow eingetragen.
- Connector-Source-Assertions: **OK**.
- **Status: SERVER + PORTAL QUELLSTAND FERTIG / ANDROID + MIGRATION 014 + SYNOLGY-E2E OFFEN.**
- Weiterhin noch kein Deployment auf Synology, damit der bestehende Android-Client nicht gegen den neuen Abschlussvertrag läuft.

## Quellblock 24.09.2026 – BTB-Schichtende / Teamleiter-Finalisierung

- Auftrag aus SYNC-003/SYNC-004 serverseitig als erster Baustein umgesetzt.
- Neue Migration **014**: `tb.btb_participant_states` mit `WORKING`, `FINISHED`, `NOT_PARTICIPATING`, Versions-/Zeitstempel und Indizes.
- Neue Server-API:
  - `GET /api/v1/btbs/{btb_id}/participants`
  - `POST /api/v1/btbs/{btb_id}/shift-finish`
  - `POST /api/v1/btbs/{btb_id}/shift-resume`
  - `PATCH /api/v1/btbs/{btb_id}/participants/{user_id}` für Teamleiter (`WORKING` / `NOT_PARTICIPATING`).
- Bei neuem BTB werden aktuelle Teammitglieder in den Teilnehmerstatus übernommen; später hinzugekommene aktive Teammitglieder werden beim Statuszugriff/Fertigstellen/Finalisieren nachgezogen.
- Ein Helfer mit serverseitigem `FINISHED` kann BTB-Kopf, Einträge und BTB-Fotoänderungen nicht weiter schreiben. `shift-resume` öffnet den eigenen Beitrag vor dem finalen Teamabschluss wieder.
- **Nur `teams.lead_user_id`** darf den gemeinsamen BTB finalisieren oder einen abgeschlossenen BTB reaktivieren.
- Beim Finalisieren wird der Teamleiter selbst als `FINISHED` geführt. Offene Helfer erzeugen standardmäßig `BTB_PARTICIPANTS_NOT_FINISHED`; bewusster Ausnahmeabschluss über `allow_unfinished=true` wird im Audit dokumentiert.
- PDF-Sicherheit geschlossen:
  - `GET /api/v1/btbs/{id}/final-pdf` verlangt Teamleitung;
  - `GET /api/v1/files/{id}`, Dateiliste und `/content` respektieren bei `BTB_FINAL_PDF` ebenfalls den Teamleiter-Guard;
  - normaler Upload darf den reservierten Zweck `BTB_FINAL_PDF` nicht setzen.
- Neue Capabilities: `btb_participant_shift_end_v1=true`, `btb_pdf_team_lead_only_v1=true`.
- CI auf Migration 14 und Source-Check `92_verify_btb_shift_end_source.py` aktualisiert. Connector-Source-Assertions bestanden.
- GitHub Actions lief am aktuellen Quellstand **ohne Runner-Steps** in Failure; keine Logs/ausgeführten Tests vorhanden. Das ist **kein Runtime-/CI-Testnachweis** und wird nicht als bestanden ausgegeben.
- **Status: QUELLSTAND FERTIG / DEPLOYMENT + MIGRATION 014 + PORTAL/ANDROID + E2E OFFEN.**
- Noch **nicht auf Synology deployen**, bis Portal/Android `Schicht Ende`, Teilnehmerstatus und Teamleiter-Finalisierung unterstützen; sonst würden bestehende Clients beim Abschluss erwartungsgemäß in den neuen Guard laufen.
- `STATUS.xlsx` bleibt in diesem Schritt unverändert: Android-XLSX ist älter als die Server-XLSX, aber die binäre GitHub-Datei liegt hier nicht sicher editierbar/materialisiert vor; kein blindes Überschreiben.

# TB-Helfer – aktueller Projektstand

## Laufzeitbefund 24.09.2026 – Background-Worker startet wiederholt neu

- Praktisch beobachtet: `tb-helfer-v1-worker-1` war erneut erst wenige Minuten aktiv, während API/DB/Monitor/Cloudflare bereits seit vielen Stunden liefen. Das ist ein neuer Laufzeitfehler und nicht der bereits erledigte Restore-Target-Bind-Mount-Fehler PRB-015.
- Ein zuvor gezeigter Worker-Trace enthielt `psycopg.OperationalError` / PostgreSQL `Connection refused`. Im aktuellen Quellstand konnte ein solcher temporärer Infrastrukturfehler den dauerhaften Workerprozess tatsächlich beenden:
  - `recover_stale_jobs()` wurde beim Start ohne Retry aufgerufen;
  - `run_one()` rief `claim_job()` außerhalb einer äußeren Fehlerbarriere auf;
  - Fehler beim Setzen von DONE/PENDING/FAILED konnten ebenfalls aus `run_one()` herauslaufen;
  - Stale-Recovery lief bisher nur beim Workerstart.
- Quellfix `d687a449499cf5070760727e5090c46a963a27e2`:
  - Worker wartet bei Start-/DB-Fehlern intern und beendet nicht den Containerprozess;
  - jede normale Worker-Iteration besitzt eine äußere Fehlerbarriere mit Trace-Logging;
  - Job-Abschluss-/Fehlerstatus werden bis zu fünfmal lokal wiederholt;
  - Stale-Recovery wird zusätzlich alle 60 Sekunden geprüft;
  - Heartbeat bleibt unabhängig aktiv.
- Regression-Check 41 wurde erweitert: `0013fa37e84a2f422927d14c56ec0e773524ee9a`.
- **Keine DB-Migration und keine Compose-Änderung.** `restart: unless-stopped` bleibt korrekt und dient nur noch als letzte Rückfallebene.
- **Laufzeitdiagnose bestätigt:** Der exportierte Log zeigt 23.09. 18:41 `database system is shutting down`; die Datenbank meldet als Startzeit 23.09. 18:42:18 und wurde damit zu diesem Zeitpunkt tatsächlich neu gestartet. Am 24.09. 07:02 trat zusätzlich ein `ConnectionTimeout` in `claim_job()` auf, obwohl PostgreSQL seit 18:42 durchlief. 20/20 direkte DB-Pings waren anschließend erfolgreich.
- **Fix jetzt deployt:** `jobs.py` wurde auf Synology aktualisiert und der Worker neu gestartet. Im laufenden Container wurden alle vier Robustheitsmarker mit `OK` bestätigt: `WORKER_ERROR_RETRY_SECONDS`, `JOB_STATE_UPDATE_ATTEMPTS`, äußere Loop-Retry-Barriere und Startup-DB-Retry. PID 1 war beim ersten Nachtest frisch gestartet.
- **Langzeittest offen:** Worker über längere Zeit beobachten und mindestens einen Background-Job praktisch ausführen. Bei erneutem Neustart neuen Worker-Trace plus DB-/Container-Log derselben Uhrzeit sichern; erst dann weitere Ursache eingrenzen.
- Sollte der Worker danach trotzdem neu starten, ist der neue Worker-Trace zu prüfen; externe Prozessabbrüche wie OOM/Kill wären dann separat nachzuweisen.
- `STATUS.xlsx` bleibt wegen des bekannten Binär-/Versionsstands unangetastet.


Stand: 2026-09-23

Maßgeblich sind aktueller GitHub-Code im aktiven Branch, `docs/TB-HELFER_PROJEKTREGELN.md`, diese Datei, `docs/TB-SERVER_1_0_ZIELMODELL.md` und `docs/TB-SERVER_1_0_CUTOVER.md`.

## Repository / aktive Entwicklung

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiver Branch: **`feature/tb-server-1.0`**
- Draft-PR: **#6 – TB-Server 1.0 – gemeinsame Serverbasis**
- PR-Basis: `feature/admin-offline-sync`

## Arbeitsblock 23.09.2026 – Reihenfolge 1 → 2 → 4 → 3

Benutzerauftrag in dieser Reihenfolge umgesetzt/geprüft. Quellstand dieses Blocks vor Statusaktualisierung: **`3045b965651794abbd4f83fdee375c81116774dc`**. Android bleibt auf **`3ea67aa1542a47f4057dca38014a5e73f1e6717f`**.

1. **Benutzer-Gültigkeit – ERLEDIGT / PRAKTISCH BESTÄTIGT**
   - Admin bietet `3 Monate`, `6 Monate`, `1 Jahr`, `Manuell` und weiterhin optional keinen Ablauf.
   - Keine Uhrzeiteingabe mehr. Manuell wählt nur den letzten gültigen Kalendertag.
   - Neuer API-Vertrag `access_valid_through`; serverseitige Umrechnung mit `ZoneInfo("Europe/Berlin")` auf **Folgetag 00:01 Uhr**, anschließend UTC-Speicherung in der bestehenden Spalte `access_valid_until`.
   - Keine DB-Migration erforderlich. Alte `access_valid_until`-Clients bleiben kompatibel.
   - Benutzerliste zeigt nur den letzten gültigen Tag, nicht den technischen UTC-Zeitstempel.
   - **Praktisch bestätigt am 23.09.2026:** Benutzer-Gültigkeit wurde nach Deployment getestet und vom Benutzer mit „passt und klappt“ bestätigt. Dieser Bedien-/Ablaufpfad ist damit geschützt.

2. **Öffentliches Portal `www.tb-helfer.de` – ERLEDIGT / PRAKTISCH BESTÄTIGT**
   - App/API bleibt getrennt auf **`https://api-v1.tb-helfer.de`**.
   - Neue Konfiguration `TB_V1_PORTAL_PUBLIC_BASE_URL` → Server-`TB_PORTAL_PUBLIC_BASE_URL`.
   - Portal-Aktivierungslinks und Browser-Baustelleneinladungen verwenden die Portal-Basisadresse.
   - Root `/` leitet auf `/portal/v1/login`.
   - Synology-Compose enthält die neue API-Environment-Variable; lokale `.env` soll `TB_V1_PORTAL_PUBLIC_BASE_URL=https://www.tb-helfer.de` setzen.
   - Cloudflare-Tunnel `tb-helfer-nas`: zusätzlicher Public Hostname `www.tb-helfer.de` ist praktisch eingerichtet und extern erreichbar. Echter Benutzerlogin funktioniert; Portal-Start, Profil/Firmenkontakte und Baustellenliste laden. Die Testbaustelle `GE Immenstadt - Kempten` ist sichtbar und öffnbar. Die öffentliche Portalroute und der Login gelten damit als praktisch bestätigt; der Erstaktivierungslink bleibt nur als separater Bedienfall offen, falls noch nicht explizit getestet.

4. **Baustellen-QR-Einladungen – URSACHE BESTÄTIGT / ANDROID-FIX EXAKT VORBEREITET**
   - Server erzeugt korrekt `tbhelfer://worksite-invite?token=...`.
   - Androids allgemeiner `WorksiteInvitationLink` kann modernen Server-Token und Legacy bereits korrekt unterscheiden.
   - Der direkte Baustellenbutton `QR scannen` in `BaustellenTeamDirekt` verwendet jedoch noch einen eigenen Legacy-Parser und akzeptiert nur `tbhelfer://invite?code=TBI-...`; anschließend ruft er nur den Legacy-`redeemInvitation()`-Pfad auf.
   - Benötigter Android-Fix: dort `WorksiteInvitationLink.parse()` verwenden; `ServerToken` → `redeemServerInvitation()`, `LegacyCode` → `redeemInvitation()`. Moderne Tokens niemals uppercasen.
   - Nach Projekttrennung wurde **`app/**` im SERVER-Projekt nicht geändert**.
   - Verbindliche Android-Übergabe: `uebergaben/TB-ANDROID_QR_EINLADUNGSFIX_2026-09-23.md` (`45d55128…`). SERVER-Check 48 wurde mit Commit `66ae2adf…` erweitert und fordert nun auch im direkten Baustellen-Scanner `WorksiteInvitationLink.parse(...)`, modernen `redeemServerInvitation(...)`-Pfad, Legacy-Fallback und das Entfernen des parallelen `extractInvitationCode(...)`.

3. **Strecken/Bahnhöfe im Admin – QUELLSTAND FERTIG / DEPLOY+E2E OFFEN**
   - Neue getrennte Admin-Navigation **Strecken** und **Bahnhöfe**.
   - Neue Seiten `/admin/v1/routes` und `/admin/v1/stations` verwenden ausschließlich den vorhandenen gemeinsamen `/api/v1/catalog`.
   - Strecken: Anlage/Korrektur, Abschnitte, Streckenobjekte (Gleis/Weiche/Kreuzung/Bahnsteig/Signal/Sonstiges), Versionsanzeige.
   - Bahnhöfe/Betriebsstellen: Anlage/Korrektur und zugehörige Gleise/Weichen/Objekte.
   - Kein globales Löschen; vorhandene Katalog-Historie/Versionierung bleibt maßgeblich.
   - Browser-Katalogmutationen sind nun CSRF-geschützt; Geräte-Bearer bleibt über den bestehenden `require_csrf`-Bypass für DEVICE kompatibel.
   - Keine DB-Migration erforderlich.

**Teststatus:** GitHub-Actions-Serverlauf auf dem Zwischenstand endete erneut ohne ausgeführte Runner-Steps; daher kein Testnachweis. Praktischer Synology-/Cloudflare-/Android-E2E bleibt offen.  
**STATUS.xlsx:** nicht überschrieben, da die aktuelle GitHub-Binärdatei neuer ist als die hier bearbeitbare Library-Kopie; zuerst konfliktfreien Binärstand beschaffen.

**Deployment-Bestätigung 23.09.2026:** Der Benutzer hat die Deployment-Schritte 1–3 als erledigt bestätigt: aktueller SERVER-Stand auf Synology übernommen, `TB_V1_PORTAL_PUBLIC_BASE_URL=https://www.tb-helfer.de` in der lokalen `.env` gesetzt und API-Container mit aktualisierter Compose neu erstellt. Damit sind SRV-022/SRV-024 deployed, aber noch nicht fachlich E2E bestätigt; SRV-023 hat den externen Cloudflare-Routingtest bestanden; als nächstes bleiben echter Portal-Login und Portal-Aktivierungslink.


## Laufzeitbefund 23.09.2026 – Worker / Cloudflare nach Deployment

- Nach der API-Neuerstellung wurden zwei getrennte Laufzeitfehler reproduziert.
- **Worker:** Containerstart scheitert mit `Bind mount failed`, weil die normale Synology-Compose weiterhin `restore-target-storage-v1` mountete, obwohl das Restore-Target bewusst entfernt wurde. Quellfix entfernt alle Restore-Target-Variablen/Mounts aus dem normalen Worker. Der bewusst dauerhaft beibehaltene `restore-test-db` ist jetzt ausdrücklich wieder eigener persistenter Service in der Synology-Compose. Restore-to-Target bleibt ausschließlich im optionalen Wartungs-Overlay.
- **Cloudflare:** `cloudflared` startete nicht zuverlässig selbstständig. Quellfix setzt `restart: always` und entfernt das unnötige API-Health-`depends_on`; der Tunnel startet unabhängig und retryt den Origin selbstständig.
- Relevante Commits: Compose `b64432b798a4a1036a3292c3a550ced22d1d5211`, persistenter Restore-Test-Service `c747c1b3f47a8106316937ffb97508ef255c9e14`, Source-Checks `e4fdbad34db39e2c7a9030883054691b1c64ee82` / `9bb6001eab8830461f29df27d06b219f46a25068`, Deployment-Doku `4e68fd4a289d252570407bb60db488c1087caeb1`.
- **Praktischer Stand:** Aktualisierte Compose wurde auf Synology übernommen und das Projekt neu erstellt. Worker läuft wieder, `restore-test-db` ist grün und `cloudflared` läuft grün. Damit ist der Worker-Mountfehler praktisch behoben. Beim Tunnel ist der aktuelle Start bestätigt; ein echter NAS-/Docker-Neustarttest für automatischen Wiederanlauf bleibt offen.
- `STATUS.xlsx` bleibt weiterhin unangetastet, weil die aktuelle GitHub-Binärdatei nicht konfliktfrei bearbeitbar vorliegt.

## Fachentscheidung 24.09.2026 – Foto-Sync und Schichtende

- Mobiles Kameroriginal bleibt auf dem Aufnahme-Handy; Online-Server erhält nur kleine BTB-/Portal-Sync-Version + Thumbnail + vollständige Bildmetadaten/Bildlisten-Zuordnung.
- Richtwert: Sync-Version ca. 1920 px längste Kante / JPEG 80–82 %, Thumbnail ca. 512 px; Werte nach praktischem Qualitäts-/Lasttest anpassbar.
- Portalbilder: Portaldatei darf als serverseitige Quelldatei auf NAS bleiben; an mobile Geräte nur kleine Sync-Version/Thumbnail.
- Originale werden nach Schichtende manuell Handy → Laptop/USB → verantwortlicher TB übergeben. Für spätere sichere Zusammenführung ist ein Manifest mit stabilen Foto-/BTB-/Benutzer-IDs und Prüfsumme vorgesehen.
- BTB-Schichtende ist zweistufig:
  - jeder beteiligte Nutzer markiert nur den eigenen Beitrag als `FERTIG`;
  - nur verantwortlicher Haupt-TB/Teamleiter darf den gemeinsamen BTB endgültig abschließen.
- Haupt-TB sieht Status aller Beteiligten; offene Nutzer müssen vor Finalisierung sichtbar sein. Normalfall: alle Beteiligten fertig; Ausnahmeabschluss nur bewusst bestätigt.
- USB-Volloriginalübergabe blockiert den BTB-Abschluss nicht; Text/Fachdaten, Bildmetadaten und kleine Sync-Versionen müssen aber serverseitig bestätigt sein.
- Projektregeln und `docs/TB-SYNC_15x15_ZIELMODELL.md` entsprechend aktualisiert.
- Umsetzung in Server/Android/Portal noch offen; keine DB-/App-Codeänderung in diesem Schritt.

## Architekturprüfung 24.09.2026 – automatischer Delta-Sync / Lastziel 15×15

- Benutzeranforderung verbindlich präzisiert: Android-Zeitplan **Manuell / Stündlich / Täglich entfällt**. Normale kleine Fach-Deltas laufen automatisch; `Neu laden` bleibt nur Recovery/Vollabgleich.
- Lastmodell für Planung/Test: **15 verantwortliche TBs × 15 Helfer = 240 mobile Nutzer** plus **15 gleichzeitig offene Portal-/Laptop-Sitzungen = 255 aktive Clients**.
- Positiv vorhanden:
  - Server ist Source of Truth;
  - `tb.change_feed` mit `company_id/change_id`- und `worksite_id/change_id`-Index;
  - granulare BTB-IDs/Entry-IDs/Versionen;
  - BTB Entry-Änderungen erzeugen zusätzlich Eltern-`BTB`-Change;
  - Portal-BTB schreibt bereits über dieselben kanonischen API-Pfade und erzeugt damit Change-Events;
  - `GET /api/v1/btbs/{id}` erlaubt gezieltes Nachladen genau eines BTB.
- Aktuelle Skalierungsgrenzen:
  - Android Default ist heute stündlich; `SofortSync` startet noch den breiten Vollsync;
  - allgemeiner Android-`SyncChangeApi` ist bislang Katalog-orientiert und nutzt nicht den neuen Fach-`/api/v1/sync/changes`-Delta-Pfad;
  - aktueller `/api/v1/sync/events` pollt je Verbindung alle 2 s und öffnet dafür jedes Mal eine neue Psycopg-Verbindung;
  - bei 255 permanenten Clients wären das rechnerisch 127,5 DB-Verbindungsaufbauten/Queries pro Sekunde selbst im Leerlauf;
  - `database.py` besitzt aktuell keinen Connection Pool; Synology-API läuft aktuell als ein Uvicorn-Prozess;
  - der heutige Change-Feed ist worksite-basiert und muss für BTB-Fanout teambezogen werden, damit ein Team-BTB nicht alle 240 Geräte weckt.
- Zielarchitektur:
  - mobile Geräte: kein Dauer-SSE; inhaltsfreies Push-Wake + dauerhafter Change-Feed/Cursor + gezieltes Objekt-Nachladen;
  - Sicherheit: Pushverlust wird durch App-Resume, Netz-Rückkehr und kleinen periodischen Delta-Fallback aufgeholt;
  - BTB-Fanout nur an das Team des verantwortlichen TBs (1 TB + 15 Helfer);
  - Portal: Live-Signal über gemeinsamen Server-Notifier, nicht per Browser alle 2 s neue DB-Verbindung;
  - DB-Pooling und Burst/Jitter/Coalescing vor 15×15-Freigabe;
  - Fotos: optimierte Sync-Version + Thumbnail/Lazy-Download; Originalarchiv getrennt.
- Detailzielbild: `docs/TB-SYNC_15x15_ZIELMODELL.md`.
- Android-Übergabe: `uebergaben/TB-ANDROID_AUTOSYNC_15x15_2026-09-24.md`.
- **Status:** Architektur tragfähig mit den dokumentierten Nachrüstungen; heutiger Vollsync-/2s-SSE-Pfad ausdrücklich noch nicht für 255 Clients freigegeben.
- Keine DB-/API-/Android-Implementierung in diesem Prüfschritt. `STATUS.xlsx` weiterhin unangetastet.

## Neuer Integrationsauftrag 23.09.2026 – selektiver Sofort-Sync

- Benutzeranforderung: Eine lokale BTB-Änderung darf nicht mehr den gesamten Datenbestand synchronisieren. Beispiel: BTB-Textänderung → nur dieser BTB.
- Bestand geprüft: `BautagebuchSpeicher.speichern(...)` stößt heute zentral `SofortSync.anstossen(...)` an; `SofortSync` startet den normalen `SyncWorker`, der vor dem Server-1.0-Fachsync Katalog lädt und danach im `Server1AndroidSyncCoordinator` Baum, TrackData, BTB, Fachobjekte und Dateien verarbeitet.
- Das widerspricht nicht dem Servermodell, sondern dem noch nicht fertig umgesetzten Clientpfad: `docs/TB-SERVER_1_0_ZIELMODELL.md` Abschnitt 9 fordert bereits dauerhafte Outbox, sofortige gezielte Mutation, Sync-Event und objektbezogenes Nachladen über Cursor.
- Servergrundlage ist vorhanden: `GET /api/v1/sync/changes?after=...`, granulare BTB-Endpunkte, idempotente Mutationen. Bei jeder `BTB_ENTRY`-Änderung erzeugt der Server zusätzlich ein Eltern-`BTB`-Change-Event.
- Server advertised diesen bestehenden Vertrag nun explizit mit Capability **`sync_change_feed_v1=true`**. Keine DB-Migration.
- Projektregeln ergänzt: Sofort-Sync ist bereichsbezogen; Vollsync bleibt nur für `Neu laden`, Aktivierung/Reaktivierung, Beitritt, ausdrückliche manuelle Aktualisierung und Recovery.
- Verbindliche Android-Übergabe: `uebergaben/TB-ANDROID_SELEKTIVER_SYNC_2026-09-23.md`.
- Sicherheitsstrategie: bestehenden `SyncWorker`/Vollsync nicht ersetzen. Zuerst separaten persistenten selektiven Worker für BTB bauen und E2E bestätigen; danach Change-Feed-Pull auf anderen Geräten.
- SERVER-Commits: Capability `7c40fc6c…`, Check `3dea21e2…`, Regeln `8e560ae6…`, Übergabe `68ad212d…`.
- ANDROID bleibt aktuell `ac972afecf96504b2f13dd697895100cf4a27fee` / **1.3.16 (200066)**.
- **Umsetzung/Build/E2E im ANDROID-Projekt offen.**
- `STATUS.xlsx` weiterhin nicht blind überschrieben; aktuelle GitHub-Binärdatei ist neuer als die bearbeitbare Library-Kopie.

## Laufzeitbefund 23.09.2026 – QR-Erzeugung HTTP 500

- Nach Deployment des offenen Einladungsvertrags endete das QR-Erzeugen mit HTTP 500.
- Im aktuellen Quellstand eindeutig nachvollziehbarer DB-Fehler: `tb.worksite_invitations.permissions_override` und `area_scope` sind JSONB, wurden im Create-Pfad aber als rohe Python-`dict`-Objekte an Psycopg übergeben.
- Derselbe Fehler wäre anschließend beim Redeem erneut aufgetreten, weil die aus PostgreSQL gelesenen JSONB-Dicts wieder roh in `tb.worksite_memberships` geschrieben wurden.
- Quellfix: `Jsonb(payload.permissions_override)`, `Jsonb(invitation_scope)` sowie `Jsonb(invitation["permissions_override"])` / `Jsonb(invitation["area_scope"])`.
- Commit: `c8671dbdbecc301fe28212d45db7326b422fff81`; Regression-Guard: `6067a1af6dcb1afccf220b73c3ba99dadeef4df1`.
- **ERLEDIGT / PRAKTISCH BESTÄTIGT.** `worksites.py` wurde auf Synology übernommen; QR-Erzeugung und Redeem funktionieren nach dem JSONB-Fix. Keine Migration.

## Laufzeitbefund 23.09.2026 – Baustellen-QR-Erzeugung

- Android-Branch ist inzwischen **1.3.16 / 200066** auf `ac972afecf96504b2f13dd697895100cf4a27fee`; der direkte Scanner verwendet den gemeinsamen modernen `WorksiteInvitationLink`-Parser. Scanner-E2E ist noch offen.
- Praktischer Fehler beim Erzeugen: App zeigt nach `QR wird erzeugt …` **`Einladungen werden vom Server nicht unterstützt.`**
- Ursache aus Codevertrag bestätigt: `BaustellenTeamDirekt.createInvitation(...)` verwendet noch den Rolling-Update-/Legacy-Create-Pfad `POST /api/v1/collab/worksites/{id}/invitations`; Server 1.0 hatte diesen Pfad nicht. Gleichzeitig war `WorksiteInvitationCreate.invited_user_id` im Python-Modell Pflicht, obwohl die DB-Spalte bereits nullable ist und die Projektregel offene QR-/Link-/Code-Einladungen vorsieht.
- Serverfix ohne Migration:
  - `invited_user_id` im kanonischen Vertrag optional;
  - offene Einladung darf von jedem aktiven Benutzer derselben Firma eingelöst werden;
  - gezielte Einladung bleibt weiterhin an die Benutzer-ID gebunden;
  - bereits aktives Baustellenmitglied kann offenen QR nicht zum Rollenwechsel verwenden;
  - HELPER bleibt an das Team des einladenden TB gebunden; TB/RESPONSIBLE_TB erhält wie bisher eigenes Team;
  - `/api/v1/collab/worksites/{id}/invitations` ist nur ein Rolling-Update-Adapter für bereits installierte Android-Versionen und ruft intern `create_worksite_invitation(...)` auf;
  - Capability `worksite_open_invitations_v1` ergänzt.
- Quellcommits: `a56402882ea4a2092bd9d7400ef19f299fda10b6`, `bf9676f2b9d34f0e9fa4a804df0fbb845020aec8`, `cd9b1df4012f08626562f533bd8a0447127755bf`, Check `eb978139c5a30ac75236201e8b4605f6c7a1442a`.
- **Praktisch bestätigt am 23.09.2026:** Runtime-Dateien wurden auf Synology übernommen; QR-Erzeugung und anschließendes Einlösen mit Android 1.3.16 / 200066 funktionieren. Benutzerbestätigung: „okay läuft“.
- Keine DB-Migration, keine Änderung an DB-/Storage-/Backups-/Restore-Test-Daten.
- `STATUS.xlsx` weiterhin nicht blind überschrieben.

## Laufzeitbefund 23.09.2026 – Baustellenbaum / Portal-Medien

- SERVER-Quellfix-Stand vor dieser Dokuaktualisierung: **`909d970797a0c74aa6d60d5fdd34406b5bf77b93`**; ANDROID-Branch: **`3ea67aa1542a47f4057dca38014a5e73f1e6717f`**.
- Android-Syncfehler reproduziert: `POST /api/v1/worksites/{id}/nodes` lieferte HTTP 500. Ursache: Python-`dict` wurde in `worksites.py` ohne Psycopg-`Jsonb(...)` an die JSONB-Spalte `payload` übergeben. Quellfix: **`3100c080162a631f224aedacb1475a76d108e1be`**, Regression-Check: **`2dcf83ad94c71dc47bbbe4305c4226096f9a1ae7`**. **Auf Synology eingespielt; Android-E2E/201-Nachweis noch offen.**
- Separater Portalfehler reproduziert: `GET /api/v1/worksites/{id}/media` lieferte HTTP 500. Ursache: Medien-SQL verwendete `users.full_name`, die Server-1.0-Tabelle besitzt `display_name`. Quellfix: **`817c0b5dfa7589a7cdcc884f3408c4f5fbf9a488`**, Regression-Check: **`909d970797a0c74aa6d60d5fdd34406b5bf77b93`**.
- **Portal-Medienpfad praktisch BESTÄTIGT:** Nach Deployment laden Baustellenstruktur, Teams, Mitglieder und Mängel wieder; beim Testmangel sind **8 hochgeladene Fotos** im Portal sichtbar. Dieser konkrete 500er gilt damit als behoben.
- Der Android-Gesamtsync ist dadurch **noch nicht** bestätigt. Nächster Test: Handy `Neu laden`, anschließend im API-Log prüfen, dass `POST .../nodes` nicht mehr 500, sondern erfolgreich (bei Neuanlage 201) antwortet.
- `projektstatus/TB-HELFER_STATUS.xlsx` wird in diesem Schritt bewusst **nicht** überschrieben: Die aktuelle GitHub-XLSX ist neuer als die verfügbare bearbeitbare Library-Kopie. Zuerst konfliktfrei den aktuellen Binärstand beschaffen/konsolidieren.
- Funktionscommit globaler Katalog + Benutzeridentität: **`511bb5c00552b5a7e54d35d930ea982cf4629598`**
- Server-Automatik/E-Mail/Audit-Quellblock: **`dd0bf1c6b5640951e74494969b5032a5ada86e1d`** (Migration 012; danach nur Doku/Statuspflege)
- Systemstatus kompakt/live + Job-Fortschritt: Migration **013** ergänzt `progress_percent`/`progress_text`; Worker, Backup-/Restore-Test, Admin-Systemstatus und Release-Archivansicht wurden auf den neuen Bedienvertrag erweitert. Quellblock ab `8cdfe781…` bis `57b28a76…`; **DEPLOY + E2E OFFEN**.
- Restore-Test-/DRAFT-Download-Laufzeitfixes vom 21.09.2026: **`e58d9fca9840…`** (UUID-Pathparameter) + **`4c20bde651f0…`/`946a62501610…`** (ADMIN-Draft-Binary-Prüfung); dazu Source-Checks 42/40/87 nachgezogen.
- GitHub Actions für diesen Funktionscommit liefern weiterhin **keinen Testnachweis**: Android-Run `35524333713`, Server-PR-Run `35524333722` und Server-Push-Run `35524332208` endeten ohne zugewiesenen Runner (`runner_id=0`, `runner_name=""`, `steps=[]`). Das bleibt Infrastrukturproblem PRB-002 und ist kein belegter Fach-/Compilefehler.
- Android-Updateweg: der bereits bestätigte Server-Updatepfad wurde am **21.09.2026 erneut praktisch bestätigt**. Ein neu veröffentlichter Android-Release wurde vom Handy gefunden und installiert; Gerätebindung, Serveradresse und vorhandene lokale Baustellendaten blieben erhalten. Der konkrete installierte Zielbuild wurde in dieser Bestätigung nicht nochmals separat genannt. Der aktuelle Android-Quellbranch steht auf **1.3.7 / 200057**.

- Android Server-1.0 Fachsync Mängel/Abnahmen: capability-gesteuertes Laden sowie idempotentes Schreiben über die kanonischen `/api/v1/worksites/{id}/defects`, `/defects/{id}`, `/close`, `/acceptances/{id}` und `/finalize`-Verträge. Sichtbare, mit Mängeln/Abnahmen verknüpfte Server-1.0-Dateien werden über `files_v1` aufgelöst und in den vorhandenen Attachment-Cache heruntergeladen. Lokale Mängel-/Abnahme-Attachments werden resumierbar über `chunk_upload_v1` mit deterministischen Idempotenz-IDs hochgeladen; nicht mehr sichtbare/gelöschte Fach-Attachments werden nur bei nachweislich vollständigem Datei-Snapshot lokal tombstoned; bei der 1000-Dateien-Grenze wird konservativ nicht gelöscht. BTB bleibt getrennt offen.
- Produktivserver/NAS unverändert: API **0.7.0**, PostgreSQL **Schema 10**

Wichtige Server-1.0-Meilensteine:

- Cutover-Regel: `64bb798e37f6a220fbd1715859a813a359a32eee`
- Dateien/Chunkupload: `959a53459455062def10608df4ca4c4cd1b0a179`
- Mängel/Abnahmen: `40249785879b82714b7d2df822e9e52fb7fb750c`
- Vermessungsoriginale/Track: `a84c3607512a61145eba85309aa80bc2d6f015f0`
- Replay-sicherer Fachkern: `e9f16e335badbd9f036405024f89aa47fcb598fe`
- Geräteaktivierung/Bearer: `2bede07a590521210dc7c351582363565451c4a0`, `19da0daee77ce1f121ba5994fe91a5bd84d0d626`
- Vermessungsparser/Katalog: `3e974e7bd61c0241b69951e763d8eca9e2660e15`
- Raw-KF-Parität: `9378069be8c70fc89696c8c7fbf1b9b051474204`
- Parser-v2-/Fixture-Stand: `31cbd83b02523522f113c97dac412b3de3c829bf`
- Abschnittsquellen/KM-Kalibrierung: `1bf2e36271f54c8945653561bc7b46d95131165d`
- dedizierter Survey-Section-Schreibpfad: `4899fb433ef4a4fd2366691dc4fb088ed38f634d`, `c9f4816e643fbd403fdecfd694caf054458d460c`
- TRA-Verdichtung/Punktprojektion v1: `a89537d2469e8aae893bc2002d3c4c20042cbf17`
- reale KM-Konsistenzprüfung: `011c4e27025800a5bfcc1600bf78a5bbf58adffb`, `3603f1ea907afe1d205f86c27b54288a18adf560`
- reale Fixture-Dokumentation: `f46dec73a4fb2261896dc5f8e5a4aa0b9f917065`
- konservativer GRA-KM-Sprung-Rebuild: `e4d1741caf9661d19faa6beda4493dee37cdaf57`, Router `cd0f6725266cdf3f0bb8a7acb9c2bc6e3a29599b`, Capability `30a4971cd1eff3c4914bfd47f5f49aaa049f6850`, Source-Check `8259ef53cd8676f26ed7a4528d0b8a7c21e4d138`
- erster TRA-Stationsraum-Merge: `827a9a2090a6ae0c2faed77bccfef2264bf6ebab`
- gehärtete Mapping-Vertrauenskette: `f1cd189b0a4afdca0c64ac86a0877c080f3df9d0`
- Punktprojektion v2 über gemappte sekundäre TRA: `25f7a35a3bb9cbe8eecb2069f4f774981c19c8de`
- Capability `survey_station_merge_v1`: `ff903b387bcdaf5e3d7b610d08fa745605d00de4`
- CI-Vertrag Stationsmerge/GRA: `a33669b3f342f0c0d179ee6eb5a22c79cbf0cf31`
- Source-/Regressionstest Multi-TRA: `5be63d57fa854f6d89fc14648d689acc10cb7862`, erweitert `8cb5dd522fe2b65c74995606b127ecb4201f1621`
- Projektion invalidiert abhängige Auto-GRA-Sprünge: `a662c821414885700d7c2e67155175cb22de4914`
- kontinuierliche TRA-Chainage: `6441b408a5953e603b7078e4d5adb3daf43bae41`
- Stationsraum-Merge v2 auf kontinuierlicher Chainage: `37f4c87f43cbc10e133e4cbc3870ac04a614027d`
- Punktprojektion v3 auf kontinuierlicher Chainage: `a030054e6c0db7c26039270b2030b68de8c31161`
- GRA-Sprung v2 auf aktuellem Chainage-/Projektionsstand: `2f11d2d14276cc91f02bb1bf5ab30cc8f3cb6714`
- Regression positive/negative TRA-Stationsdiskontinuitäten: `69effc49acf55b4b9abc6d4136b089a784853540`
- Detaildokumentation Chainage/Projektion: `e2701359efca8ff50220c452633b44ba7dba7c0d`
- reale Typ-6-/Nullweg-Verifikation dokumentiert: `21c192ed4ad717e3d1f85fca31f85c6b398d2e49`
- Release-Katalog / Client-Kompatibilitätsvertrag: öffentliche Statusabfrage + ADMIN-Verwaltung; serverseitiger streamender Binärupload/-download mit SHA-256, atomarem Dateitausch und Admin-Weboberfläche ist jetzt ebenfalls umgesetzt
- BTB-Abschluss-PDF / Ein-PDF-/Original-/Nachbearbeitungsmodell: in diesem Stand serverseitig umgesetzt; stabiler PDF-Pfad pro `btbId`, unveränderlicher erster Original-Snapshot inkl. Foto-Basis, Reaktivierung ohne automatische Ungültigkeit
- Zentrales Firmenprofil / Firmenlogo / PDF-Logo-Layout: in diesem Stand auf die vorhandenen Server-1.0-Firmen-/Dateifelder umgesetzt; Android-UUID-Kompatibilität korrigiert, PDF-Renderer auf v2 mit zentralem Branding angehoben
- erste native Server-1.0-Portal-/Admin-Weboberfläche: Login/Portal-Start, Firmenliste/-anlage sowie Firmenprofil/Logo/PDF-Layout mit CSRF-geschützter API-Anbindung und Browser-Vorschau
- Portal-Fachoberfläche v1: Baustellenübersicht/-detail, Struktur-CRUD, Mitgliederrechte/Teamansicht, Team-BTBs, BTB-Bearbeitung/Abschluss-PDF/Foto-Chunkupload sowie Mängel-/Abnahme-Bearbeitung auf denselben `/api/v1`-Verträgen; Browser-Mutationen senden CSRF + `X-Client-Change-Id`
- gezielte Server-1.0-Baustelleneinladungen: gleiches Unternehmen, eingeladener Zielbenutzer, gehashter Einmalcode, Widerruf/Ablauf/Einmalannahme; Helfer werden dem Team des einladenden verantwortlichen TB zugeordnet
- Einladung Code/Link/QR: App-URI `tbhelfer://worksite-invite`, Browser-Link mit Token nur im URL-Fragment, SVG-QR in der einmaligen Create-Antwort, `Cache-Control: no-store`; Mutation Receipt/Audit bleiben tokenfrei
- Android Server-1.0-Einladungen: stabiler `worksite-invite`-Deep-Link, QR-/Copy-Paste-Parser mit case-sensitivem Server-Token, automatisches Einlösen nach Geräteaktivierung sowie gezielte Kandidaten-/Create-Anbindung mit Legacy-`TBI`-/Legacy-Create-Fallback nur bei fehlendem Server-1.0-Endpunkt
- Android Teamdarstellung Server 1.0: `loadTeam()` baut die bestehende UI aus `/worksites/{id}`, `/device/me`, `/members` und `/teams`; die Auswahl erfolgt jetzt über `worksite_teams_v1`, Legacy nur bei fehlender Capability
- Android Capability-Client: `/api/v1/capabilities` wird kurzzeitig im Prozess gecacht; `worksite_teams_v1` und `worksite_invitations_v1` steuern den Cutover, Netzwerkfehler werden nicht als Legacy-Server missverstanden
- Einladung-only-Mitgliedschaft gehärtet: generischer direkter Member-Add-Pfad entfernt; bestehende Helfer können über einen eigenen replay-sicheren Teamwechselvertrag zwischen aktiven TB-Teams verschoben werden, ohne neue Mitgliedschaft oder historische BTBs umzuschreiben
- Admin-Systemstatus/Speicher v1: API/Webseite für Migrationen, DB-Größe, Storage-Belegung, Firmen-/Nutzer-/Geräte-/Baustellen-/Dateizahlen, Backup-Läufe und Background Jobs ohne Geheimnisse/interne Payloads
- Background-Job-/Backup-Basis: `FOR UPDATE SKIP LOCKED`, Retry/Stale-Recovery, getrennte DATABASE/FILES/FULL-Backups, SHA-256, atomare Dateien und ADMIN-Backupaufträge
- Restore-Test + sicherer Test-Reset: Restore nur gegen getrennte Testdatenbank/Testablage; Test-/Staging-Reset nur nach verifiziertem höchstens 24h altem FULL-Backup und harter Environment-/Bestätigungssperre
- optionaler Restore-Staging-Compose-Overlay: getrennte Restore-Test-DB, getrennte Restore-Target-DB und eigener Restore-Target-Storage; Restore-to-Target bleibt standardmäßig deaktiviert und außerhalb des normalen Serverstarts
- System-Automatik/E-Mail/Audit v1: deduplizierte Zeitplan-Slots, automatische DATABASE-/FILES-/FULL-Backups, automatische Retention, tägliche/wöchentliche/monatliche Systemberichte, SMTP-Test, Warnschwellen, ablaufende Nutzer und Auditübersicht; SMTP-Passwort bleibt ausschließlich Server-Secret

## Backup-Vorabnahme vor Migration 011

Stand **21.09.2026**:
- Auf Synology-Staging wurde über den vorhandenen Systemstatus-/Background-Job-Pfad ein **FULL-Backup** erfolgreich ausgeführt.
- Der Benutzer hat diesen Schritt ausdrücklich als **funktionierend** bestätigt.
- Dieser bestätigte FULL-Backup-Pfad bleibt geschützt und wird nicht ohne reproduzierbaren Fehler oder ausdrücklichen Änderungswunsch umgebaut.
- Der frühere HTTP-500-/UUID-Fehler ist praktisch überwunden: Restore-Test-Jobs werden inzwischen eingereiht und vom Worker verarbeitet.
- Der zwischenzeitliche Worker-Environment-Fehler `TB_RESTORE_TEST_DB_NAME_MISSING` wurde reproduziert und behoben: die Restore-Test-/Restore-Target-Variablen sind dauerhaft im Synology-Staging-Worker hinterlegt; Source-Check 82 schützt den Deploymentvertrag.
- **Restore-Test am 21.09.2026 praktisch BESTÄTIGT:** derselbe getrennte Restore-Test wurde nach der Worker-Neuerstellung erfolgreich ausgeführt. Background Job `RESTORE_TEST` steht auf `DONE`, der zugehörige Backup-Lauf `RESTORE_TEST` auf `OK`. Die laufende Live-Datenbank wurde dabei nicht verändert.
- Damit sind FULL-Backup und getrennte Restore-Test-Wiederherstellung praktisch bestätigt.
- **Backup-Retention am 21.09.2026 praktisch bestätigt:** manueller `BACKUP_RETENTION`-Job lief mit Versuch 1 auf `DONE`. Bei aktueller Policy (30 Tage, mindestens 3 je Backupart) wurden die jungen Backups erwartungsgemäß nicht entfernt.
- Erster Restore-to-Target-Versuch erreichte `pg_restore`, scheiterte aber reproduzierbar mit `PG_RESTORE_FAILED_EXIT_1`. Target-Cleanup funktionierte: das `tb`-Schema war danach wieder leer.
- Ursache praktisch eingegrenzt: `postgis/postgis:17-3.5` bringt im frischen Target bereits `fuzzystrmatch`, `postgis`, `postgis_tiger_geocoder` und `postgis_topology` mit; derselbe FULL-Dump enthält diese Extensions ebenfalls (plus `pgcrypto`). Restore-Test funktioniert bereits mit `--clean --if-exists`, Restore-to-Target lief dagegen mit `clean=False`.
- Quellfix `5bd9e070deb7…`: Restore-to-Target nutzt nach den bestehenden Trenn-/Freshness-Prüfungen ebenfalls den sauberen Clean-Restore; Source-Check 43 angepasst (`0893b8204d59…`).
- Praktischer Wiederholungslauf bestätigt, dass dieser PostGIS-Fix wirkt: Restore-to-Target lief über `pg_restore` und die Datenbankverifikation hinaus.
- Neuer reproduzierbarer Fehler erst beim Datei-Target: `OSError [Errno 16] Device or resource busy: /tb/restore-target-v1`. Ursache: der Code versuchte den Root des Docker-Bind-Mounts per `target.rmdir()` zu entfernen.
- Quellfix `de1c70ba042d…`: Bind-Mount-Root bleibt bestehen; nach bereits bestandener Leerheitsprüfung werden die Restore-Dateiinhalte mit `copytree(..., dirs_exist_ok=True)` in den leeren Target-Mount kopiert. Source-Check 43 verbietet jetzt `target.rmdir()` (`03f2130c0d03…`).
- **Restore-to-Target am 21.09.2026 praktisch BESTÄTIGT:** derselbe FULL-Lauf wurde erfolgreich in die getrennte Target-Umgebung wiederhergestellt. CLI `OK`, Restore-Lauf `RESTORE_TARGET / OK`, Ziel-DB `tb_helfer_v1_restore_target`, Ziel-Storage `/tb/restore-target-v1`; DB-Prüfung 10 Migrationen/latest 10/5 Kerntabellen; 2 Dateien mit 34.267.460 Byte wiederhergestellt.
- Damit ist die **Backup-/Recovery-Abnahme vollständig bestanden**: FULL-Backup, Restore-Test, Retention und Restore-to-Target sind praktisch bestätigt. Dieser Pfad ist geschützt.
- Migration 011 wurde zu diesem Zeitpunkt **noch nicht** auf Staging ausgeführt.

## Laufzeitbefunde 21.09.2026 – Restore-Test und Release-Draft-Prüfung

- Restore-Infrastruktur auf Synology ist angelegt: getrennte `restore-test-db`, `restore-target-db` und Worker laufen; Live-API und Live-DB blieben dabei erreichbar.
- Der frühere Klickfehler `UUID` ist behoben: Restore-Test-Jobs werden inzwischen erfolgreich erstellt.
- Die früheren Restore-Test-Fehler `TB_RESTORE_TEST_DB_NAME_MISSING` waren ein Deployment-/Container-Environment-Problem. Quellfix `1dcfc4586592…` führt die Restore-Test-/Restore-Target-Variablen dauerhaft im Synology-Staging-Worker; `bbee4d4de82f…` schützt dies per Source-Check.
- Nach Übernahme der aktualisierten `compose.yml` und Neuerstellung ausschließlich des Workers waren die `TB_RESTORE_*`-Variablen praktisch vorhanden.
- **Abschluss 21.09.2026:** neuer Restore-Test-Job lief mit Versuch 1 auf `DONE`; der korrespondierende Restore-Test-Lauf steht auf `OK`. Damit ist der getrennte Restore-Test des bestätigten FULL-Backups praktisch bestanden.
- Release 1.3.6 / Build 200056 ist im Admin aktuell ein **DRAFT**. Android 1.3.4 zeigt deshalb korrekt „aktuell“: DRAFT-Releases dürfen nicht über den öffentlichen Kompatibilitätsvertrag angeboten werden.
- Der Admin-Button `Download prüfen` eines DRAFTs zeigte jedoch auf den öffentlichen Downloadvertrag und erhielt deshalb korrekt `RELEASE_NOT_FOUND`. Quellfix: eigener ADMIN-geschützter GET-Binary-Pfad für DRAFT-Prüfung; öffentlicher Download bleibt unverändert auf `PUBLISHED` beschränkt.
- **Updateweg nach Veröffentlichung am 21.09.2026 erneut praktisch bestätigt:** Admin-Binary-Download funktioniert, veröffentlichter Release wird vom Handy erkannt und installiert; danach blieben Gerätebindung, Serveradresse und vorhandene lokale Baustellendaten erhalten. Der bestehende Updatevertrag bleibt damit geschützt.

Nächster Laufzeitschritt:
1. bestätigte FULL-/Restore-Test-/Retention-/Restore-to-Target-Pfade nicht weiter verändern;
2. Migrationen **011 + 012 + 013** kontrolliert auf Synology-Staging ausrollen;
3. anschließend `/health/ready` prüfen: migrations=ok / **13 von 13**;
4. danach System-Automatik/SMTP praktisch prüfen;
5. anschließend Katalog-/Profil-E2E und den gemeinsamen Pixel/Poco-Server-1.0-E2E fortsetzen.
## Server 1.0 – kompakter Systemstatus / Live-Jobs / Release-Archiv

Quellstand: **FERTIG / DEPLOY + E2E OFFEN**. Die bestehende, praktisch bestätigte Backup-/Recovery-Logik wurde nicht ersetzt; ergänzt wurden Fortschritts-/Anzeige- und Administrationsfunktionen.

Umgesetzt:
- Migration `013_job_progress.sql` ergänzt `tb.background_jobs.progress_percent` und `progress_text`;
- der Worker schreibt echte Arbeitsschritte für DATABASE/FILES/FULL-Backup, Restore-Test, Retention und Systembericht; `DONE` wird im Admin als **Fertig**, `RUNNING` als **Läuft**, `PENDING` als **Wartet**, `FAILED` als **Fehlgeschlagen** dargestellt;
- laufende Vorgänge zeigen Prozentbalken und aktuellen Arbeitsschritt; Systemstatus pollt während aktiver Vorgänge schneller und im Leerlauf langsamer;
- rohe interne `last_error`-Texte bleiben serverseitig; die Oberfläche erhält nur sichere deutsche Fehlerbeschreibungen;
- fertige/fehlgeschlagene Background-Job-Einträge können einzeln oder gesammelt aus dem Job-Verlauf entfernt werden; wartende/laufende Jobs sind serverseitig vor Löschung geschützt;
- Warnungen können quittiert werden, ohne Audit- oder Messdaten zu löschen; neue Fehler werden wieder sichtbar;
- Backups, Vorgänge und Audit-Nachweis sind kompakt zusammengeführt; Automatik/E-Mail, Firmen und bald ablaufende Nutzer sind einklappbar;
- `RETIRED` Releases bleiben als nachvollziehbare Update-Historie erhalten, werden aber standardmäßig als Archiv ausgeblendet und über **Archivierte anzeigen** eingeblendet; Drafts bleiben vollständig löschbar.

Schutzregeln:
- Audit wird durch Aufräumen niemals gelöscht;
- bestätigte FULL-/Restore-Test-/Retention-/Restore-to-Target-Pfade bleiben unverändert;
- veröffentlichte/zurückgezogene Releases werden nicht hart gelöscht;
- keine Secrets oder rohen internen Jobfehler werden im Browser ausgegeben.

Quellprüfung:
- die neuen/angepassten Marker für Progress, sichere Job-Löschung, Warnungsquittierung, Live-UI und Release-Archiv wurden direkt gegen den aktuellen GitHub-Inhalt geprüft: **25/25 erfüllt**;
- der neue Source-Check `91_verify_system_status_live_ui_source.py` sowie Checks 39/41/42/90 wurden auf den neuen Vertrag angepasst;
- GitHub Actions auf dem aktuellen Quellstand liefern weiterhin **keinen belastbaren Testnachweis**, weil die Jobs ohne Runner-Steps enden; **Synology-E2E bleibt offen**.

Nächster Schritt dieses Blocks:
1. Serverquellstand inkl. Migrationen **011, 012 und 013** kontrolliert auf Synology-Staging übernehmen;
2. Migrationen anwenden und `/health/ready` auf **13/13** prüfen;
3. Systemstatus praktisch prüfen: Live-Prozent bei einem Backup, deutsche Status-/Fehlertexte, Job-Verlauf löschen/quittieren, kompakte Bereiche;
4. App-Versionen prüfen: zurückgezogene Releases standardmäßig verborgen, über **Archivierte anzeigen** sichtbar;
5. anschließend SMTP/Automatik und Katalog-/Profil-E2E fortsetzen.

## Server 1.0 – Backup-Automatik, E-Mail-Berichte und Audit

Quellstand: **FERTIG / DEPLOY + E2E OFFEN**. Dieser Block wurde noch nicht auf Synology-Staging ausgerollt. Der praktisch bestätigte FULL-Backup-Pfad wurde nicht ersetzt oder umgebaut.

Umgesetzt:
- Migration `012_system_automation.sql` ergänzt deduplizierte Zeitplan-Slots, damit Worker-Neustarts oder mehrere Worker keinen automatischen Auftrag doppelt erzeugen;
- automatische Backups nutzen ausschließlich die vorhandenen `BACKUP_DATABASE`/`BACKUP_FILES`/`BACKUP_FULL`-Jobs;
- automatische Retention nutzt denselben vorhandenen sicheren Retention-Pfad; Aufbewahrungstage und Mindestanzahl je Backupart sind nun im Admin einstellbar;
- E-Mail-Systemberichte können täglich, wöchentlich oder monatlich mit Zeitzone/Uhrzeit/Wochentag/Monatstag geplant werden;
- manueller `Testbericht senden` läuft ebenfalls als Background Job;
- Systemstatus zeigt SMTP-Bereitschaft, aber niemals das SMTP-Passwort;
- SMTP-Host/Port/User/Absender/TLS/SSL kommen aus Server-Environment; das Passwort bleibt ausschließlich `.env`/Secret;
- Systemstatus zeigt bald ablaufende Nutzer, konfigurierbare Speicherwarnschwelle und die letzten Audit-/Sicherheitsereignisse;
- neue Capabilities `system_automation_v1` und `system_email_reports_v1`;
- Der CI-Vertrag erwartet auf frischer Testdatenbank nun den Gesamtstand bis Migration 013; Migration 012 bleibt der Automatik-/Zeitplan-Baustein, Migration 013 ergänzt ausschließlich den Job-Fortschritt. Zusätzlich werden die Migration-011-Katalogtabellen sowie Source-Checks 90 und 91 geprüft.

Betroffene Kern-Dateien:
- `server/v1_migrations/012_system_automation.sql`;
- `server/tbserver/system_settings.py`, `automation.py`, `notifications.py`, `jobs.py`, `system_admin.py`, `backup.py`, `config.py`, `capabilities.py`;
- `server/templates/v1_system_status.html`;
- `server/docker-compose.v1.yml`, `server/docker-compose.synology-staging.v1.yml`;
- `server/checks/90_verify_system_automation_email_source.py`;
- `.github/workflows/tb-server-v1.yml`.

Tests:
- lokaler Python-Syntaxcheck der drei neu angelegten Module `system_settings.py`, `automation.py` und `notifications.py`: **bestanden**;
- bestehende Backup-/Retention-/Systemstatus-Source-Checks wurden gegen die geänderten Marker/Schutzregeln geprüft; keine absichtliche Lockerung;
- vollständiger Source-Check 90 / Fresh-PostgreSQL-Lauf ist **noch nicht ausgeführt**; GitHub Actions bleibt wegen PRB-002 kein belastbarer Testnachweis, solange keine Runner-Steps laufen;
- Migration 012, automatische Jobs und echter SMTP-Versand auf Synology: **E2E OFFEN**.

Sicherer nächster Ablauf:
1. FULL-Backup, Restore-Test, Retention und Restore-to-Target sind praktisch bestätigt; Recovery ist abgeschlossen und geschützt.
2. Aktuellen Serverbranch deployen und Migrationen **011 + 012 + 013** anwenden; `/health/ready` muss migrations=ok / 13 von 13 melden.
3. Systemstatus öffnen, Automatikwerte speichern, SMTP-Secret ausschließlich auf Synology setzen und Testbericht versenden.
4. Einen automatischen Backup-/Retention-Slot praktisch prüfen.
5. Danach Katalog-/Profil-E2E und Pixel/Poco-Server-1.0-E2E fortsetzen.

## Server 1.0 + Android 200055 – globaler Katalog und Benutzeridentität

Quellstand: **FERTIG / DEPLOY + E2E OFFEN**. Der Stagingserver wurde für diesen Block noch nicht migriert; auf Pixel/Poco ist weiterhin der bestätigte Build 200054 installiert.

Umgesetzt:
- neue Migration `011_global_catalog_identity.sql` mit firmenübergreifenden Tabellen für Strecken, Bahnhöfe/Betriebsstellen, Abschnitte sowie Gleise/Weichen/Objekte;
- unveränderliche Katalog-Historie mit Version, Vorher/Nachher, Bearbeiter und Firma;
- Katalogschreiben über `/api/v1/catalog/mutations` mit Idempotenz, Basisversion und 409-Konflikt; **globales DELETE ist gesperrt**;
- eigener Katalog-Changefeed `/api/v1/catalog/changes`; der bestehende Baustellen-/BTB-Sync unter `/api/v1/sync` bleibt unberührt;
- Android verwendet den vorhandenen Offline-Katalogcache weiter, kann Strecken/Bahnhöfe sowie z. B. `Gleis 28`, Weichen/Kreuzungen/weitere Objekte lokal ergänzen und Katalogeinträge korrigieren;
- Pending-/Konfliktstände werden beim Server-Snapshot nicht aus dem lokalen Cache verdrängt;
- neu auf anderen Geräten erzeugte Abschnitte/Objekte können über den Changefeed auch ohne vorherigen lokalen Datensatz angelegt werden;
- Android-Katalogtransport ist auf `/api/v1/catalog` umgestellt; keine neue Android-Room-Datenbank wurde eingeführt;
- Serverprofil `/api/v1/user/profile` ergänzt den bereits vorhandenen Android-Profilpfad für persönliche E-Mail/Telefon; `/api/v1/user/directory` liefert persönliche Kontakte ausschließlich innerhalb derselben Firma;
- Admin-Neuanlage verwendet Firma, Vorname, Nachname, Rolle und optionale Zugriffsgültigkeit; der Server erzeugt einen vorläufigen eindeutigen Login;
- erste Portal-Aktivierung: Benutzer setzt eigenen Login, Anzeigenamen, Passwort, persönliche E-Mail und Telefon;
- Admin-Bearbeitung erhält stabile Benutzer-ID/Gerätebindung und blockiert Firmenwechsel bei vorhandener Baustellen-/Teamhistorie;
- Portal bietet eigenes Profil und firmeninternes Kontaktverzeichnis; Admin besitzt eine vollständige Katalog-Historienseite;
- Android-Quellversion auf **1.3.5 / 200055** angehoben.

Betroffene Kern-Dateien:
- `server/v1_migrations/011_global_catalog_identity.sql`
- `server/tbserver/catalog.py`, `profile.py`, `admin.py`, `portal.py`, `admin_web.py`, `main.py`, `capabilities.py`
- `server/templates/v1_admin_users.html`, `v1_portal_activate.html`, `v1_portal_home.html`, `v1_catalog_history.html`
- `app/.../server/SyncCatalogApi.kt`, `SyncDetailApi.kt`, `SyncChangeApi.kt`
- `app/.../datenbank/SyncOfflineEditor.kt`, `OfflineSyncRepository.kt`
- `app/.../ui/sync/SyncOfflineDialogs.kt`, `SyncKatalogFragment.kt`
- `server/checks/89_verify_identity_global_catalog_source.py`

Tests dieses Quellblocks:
- statischer Source-Vertragscheck für Migration, API-Routen, Delete-Sperre, Basisversion, Benutzeridentität, Android-Endpunkte, Editorpfade und Pending-Erhalt: **bestanden**;
- GitHub Actions: drei Läufe auf Funktionscommit `511bb5c0…` wurden zwar als `failure` beendet, führten aber **keinen einzigen Step** aus; daher kein Build-/Testnachweis;
- Android-APK-Build: **noch nicht ausgeführt**;
- PostgreSQL-Migration 011: **noch nicht auf Staging ausgeführt**;
- echte API-/Pixel-/Poco-E2E-Prüfung: **OFFEN**.

Nächster praktischer Schritt:
1. Backup-/Recovery-Abnahme ist vollständig bestätigt; diesen Pfad nicht weiter verändern.
2. Branch auf Synology deployen und Migrationen 011 + 012 + 013 ausführen; `/health/ready` muss danach migrations=ok und 13/13 melden.
3. Kompakten Systemstatus/Live-Job-Fortschritt sowie Server-Automatik/SMTP-Test und Admin-Neuanlage + Portal-Erstaktivierung + Profil/Verzeichnis mit Testbenutzern prüfen.
4. Android 1.3.5 / 200055 bauen/installieren.
5. Auf Pixel/Poco denselben globalen Eintrag ergänzen/korrigieren (z. B. `Gleis 28`) und Historie/Versionskonflikt prüfen.
6. Danach erst Status auf BESTÄTIGT/ERLEDIGT setzen.

## Admin – vollständiges Löschen/Purge für Test-/Administrationsfälle

Auf ausdrücklichen Benutzerwunsch wird zusätzlich zum bestehenden sicheren Soft-Delete ein klar getrennter **Komplett löschen**-Pfad vorbereitet.

Quellstand:
- Release-Draft: vollständiges Löschen des Draft-Datensatzes plus ggf. hochgeladener Draft-Binärdatei; veröffentlichte/zurückgezogene Releases bleiben unveränderlich bzw. nur zurückziehbar.
- Benutzer: `Sicher löschen` bleibt das bestätigte Soft-Delete. Zusätzlich `Komplett löschen` entfernt Benutzeridentität, Portal-/Aktivierungsdaten, Geräte und direkte Zuordnungen. Bei vorhandener BTB-Historie als Teamleitung stoppt der Server den Purge, damit nicht beiläufig historische Baustellendaten verloren gehen.
- Gerät: kompletter Geräte-Datensatz samt Geräte-Token-/Synczustand kann entfernt werden.
- Baustelle: kompletter Server-Datensatz einschließlich Teams, BTBs, Mängel, Abnahmen, baustellenbezogener Dateien und Upload-Zwischenstände kann entfernt werden.
- Firma: vollständiger Firmen-Purge löscht in staging/test zugehörige Benutzer, Geräte, Baustellen, Dateien und Firmen-Vermessungsoriginale. In production wird der Firmen-Purge bei vorhandenen Vermessungsoriginalen blockiert, damit die verbindliche Originaldaten-Schutzregel nicht ausgehebelt wird.
- Alle Komplett-Purge-Aktionen sind ADMIN/CSRF-geschützt und verlangen zusätzlich die Texteingabe `ENDGUELTIG LOESCHEN`.

Betroffene neue/angepasste Dateien:
- `server/tbserver/admin_purge.py`
- `server/tbserver/main.py`
- `server/templates/v1_releases.html`
- `server/templates/v1_admin_users.html`
- `server/templates/v1_admin_companies.html`
- `server/templates/v1_admin_devices.html`
- `server/templates/v1_worksites.html`
- `server/checks/88_verify_admin_permanent_purge_source.py`

Status: **Auf Synology-Staging praktisch bestätigt.** Der Benutzer bestätigt den vollständigen Admin-Purge mit „funktioniert super“. Release-Draft, Benutzer, Gerät, Baustelle und Firma lassen sich über die getrennten Komplett-Löschpfade entfernen; die Pflichttexteingabe `ENDGUELTIG LOESCHEN` bleibt aktiv. Der bestehende sichere Benutzer-Soft-Delete bleibt separat erhalten. Der Production-Schutz für echte Vermessungsoriginale bleibt verbindlich und wurde nicht durch einen destruktiven Produktionstest aufgehoben.

## Android 200054 – Server-Update praktisch verteilt

Der erste echte Updateversuch von **Android 1.3.3 / 200053 auf 1.3.4 / 200054** wurde praktisch durchgeführt. Server 1.0 zeigt ANDROID 1.3.4 / Build 200054 als **PUBLISHED** mit hinterlegter Binärdatei und SHA-256. Die beiden Testgeräte Pixel und Poco zeigen anschließend jeweils die installierte Version **1.3.4 / 200054**; auf einem Gerät meldet die manuelle Prüfung bereits **„TB-Helfer ist aktuell“**.

Damit ist die normale Updateverteilung über den vorhandenen Server-Release-Katalog praktisch bestätigt. Noch separat zu prüfen sind auf beiden Geräten: bestehende Geräteaktivierung/gespeicherte Serveradresse sowie Erhalt der lokalen Baustellendaten. Auf dem zweiten Gerät soll einmal die manuelle Updateprüfung ausgeführt werden. Ein rotes Pflichtupdate bleibt ein späterer eigener Test.

Die neue **App-Versionen**-Komfortoberfläche ist inzwischen auf dem Synology-Stagingserver sichtbar und praktisch geöffnet: Release-Datei (.md/.txt), APK-Auswahl, Neu/Verbessert/Behoben sowie editierbare Drafts werden angezeigt. SRV-016 gilt damit für Deployment/Bedienoberfläche als bestätigt.

## Server-Release-Admin – Release-Datei + APK in einem Ablauf

Die App-Versionen-Seite wurde für den ersten echten 200053→200054-Update-Test vereinfacht. Statt erst einen leeren Draft anzulegen und die APK später in der Release-Liste suchen zu müssen, gibt es jetzt oben sichtbar **Release-Datei auswählen** und **APK/Binärdatei auswählen**.

Release-Dateien (`.md`/`.txt`) können Version, Build, Mindestbuild, Pflichtupdate und die Blöcke **Neu / Verbessert / Behoben** enthalten. Die Angaben werden beim Einlesen in die Serverfelder übernommen und bleiben vor der Veröffentlichung editierbar. Eine ausgewählte APK wird beim Anlegen des Drafts direkt über den bereits vorhandenen SHA-256-geschützten Binärupload gespeichert. Bestehende Drafts können Metadaten weiter per PATCH bearbeiten, eine neue Release-Datei einlesen, die APK ersetzen/entfernen und eine aktuelle Release-Datei wieder herunterladen.

Es gibt weiterhin **nur einen** Release-Katalog und **keinen** zweiten Metadatenspeicher: die Datei ist Import-/Exporthilfe, kanonisch bleiben die vorhandenen `tb.releases`-Felder. Android muss dafür nicht erneut geändert werden; die App liest weiterhin `release_notes` aus demselben Serververtrag.

Quellprüfung: `server/checks/87_verify_release_admin_package_ui.py`. Praktischer Server-UI-/200054-Update-E2E bleibt bis zum Deployment und Handytest offen.

## Android 200054 – erster echter Server-Update-Test vorbereitet

Android **1.3.3 / versionCode 200053** wurde nach dem Coroutine-Compile-Fix am 2026-09-20 erfolgreich gebaut und anschließend auf **Pixel und Poco** installiert. Damit ist 200053 die praktisch bestätigte Ausgangsbasis für den ersten echten App-Update-E2E.

Für den Update-Test wird der Quellstand auf **1.3.4 / versionCode 200054** angehoben. Fachlich bleibt der Updater unverändert; zusätzlich wird der beim erfolgreichen 200053-Build sichtbare Deprecation-Warnhinweis für `android.R.color.primary_text_light` entfernt, indem die ursprüngliche Textfarbe des Update-Statusfelds wiederverwendet wird. Das ist keine Änderung an QR-/Geräteaktivierung, Sync, BTB oder Datenhaltung.

Nächster praktischer Test:
1. 200054 lokal bauen.
2. Im Server-1.0-Admin unter **App-Versionen** ein ANDROID-Release **1.3.4 / 200054** anlegen, APK hochladen und kurze Release Notes eintragen.
3. Zunächst **kein Pflichtupdate** setzen, damit auf beiden Handys das normale blaue Update-Symbol geprüft wird.
4. Release veröffentlichen.
5. Pixel + Poco müssen das Update erkennen, herunterladen, per SHA-256/Dateigröße prüfen und an den Android-Paketinstaller übergeben.
6. Danach kontrollieren: Geräte-Token, gespeicherte Serveradresse und lokale Baustellendaten sind weiterhin vorhanden.

Erst nach diesem Test gilt der Server-Updateweg als E2E bestätigt.

## Android 200053 – Server-gesteuerter Updater (Quellstand fertig, Build/Installation bestätigt, Update-E2E offen)

Der vorhandene Server-1.0-Releasevertrag wird jetzt direkt in Android verwendet; es entsteht **keine zweite Update-Infrastruktur**.

Umgesetzt:
- Startbildschirm prüft `/api/v1/client-compatibility` unaufdringlich; ohne Update bleibt das Symbol verborgen.
- normales Update = blaues `↻`; Pflicht-/wichtiges Update = rotes `↻`.
- Antippen zeigt aktuelle/neue Version und serverseitige Release Notes und bietet `Aktualisieren`.
- `Einstellungen → Wartung & Diagnose` besitzt dauerhaft `Nach Updates suchen`, Status/Version, Release Notes und den Installationsbutton.
- APK-Download nutzt den veröffentlichten Serverpfad, akzeptiert produktiv nur HTTPS, prüft serverseitige Dateigröße/SHA-256 und öffnet anschließend den Android-Paketinstaller.
- Server-Public-Release-Payload liefert dafür zusätzlich `sha256` und `byte_size`; die Admin-Release-Seite erklärt Release Notes und blau/rot.
- Android wurde auf **1.3.3 / versionCode 200053** angehoben.

Praktisch bestätigt: 200053 wurde erfolgreich gebaut und auf Pixel + Poco installiert. Der echte Updateweg **200053 → 200054** wurde anschließend über Server 1.0 auf **beiden Handys** durchgeführt. Pixel und Poco laufen auf **1.3.4 / 200054** und melden bei manueller Prüfung jeweils **„TB-Helfer ist aktuell“**. Damit ist die normale Server-Updateverteilung E2E bestätigt. Separat offen bleibt nur der kurze Datenerhalt-Spotcheck für Gerätebindung/Serveradresse und vorhandene lokale Baustellendaten.

### Compile-Fix nach lokalem Buildtest 2026-09-20 12:25

Der erste lokale Build von Android **1.3.3 / 200053** erreichte `:app:compileDebugKotlin`, scheiterte dort aber mit vier Meldungen in `WartungDiagnoseFragment.kt`. Die eigentliche Ursache war ein fehlender Import von `kotlinx.coroutines.launch`; dadurch war `viewLifecycleOwner.lifecycleScope.launch { ... }` nicht auflösbar und die Aufrufe der suspend-Funktionen `AppUpdateManager.check()` und `downloadApk()` wurden anschließend nur als Folgefehler gemeldet.

Korrigiert wurde ausschließlich dieser Coroutine-Import. Die Update-Fachlogik, Geräteaktivierung, Serveradresse, Geräte-Token, Sync- und BTB-Logik wurden nicht verändert. Source-Check 86 prüft den Import ausdrücklich mit. **Praktischer Build-Retest bestanden:** der Build lief am 2026-09-20 erfolgreich durch und 200053 wurde danach auf Pixel + Poco installiert.

## Android 200052 – Compile-Fix nach lokalem Buildtest 2026-09-20

Der lokale Android-Build von 200051 scheiterte in `:app:compileDebugKotlin` mit genau 8 Folgefehlern. Ursache waren zwei fehlende Imports im kanonischen Quellbaum, der durch `generateBtb200043Sources` in den generierten Build-Quellbaum kopiert wird:

- `Server1FileApi.kt`: `org.json.JSONArray` fehlte; dadurch war `root.optJSONArray("files") ?: JSONArray()` nicht kompilierbar.
- `BaustellenSyncFragment.kt`: `de.tbhelfer.datenbank.SyncWorksiteContextEntity` fehlte; dadurch waren der Typ sowie die daraus abgeleiteten Zugriffe `memberRole`, `name` und `designation` nicht auflösbar.

Korrigiert wurde ausschließlich diese Compile-Ursache; keine Fachlogik wurde geändert. Der neue eindeutig unterscheidbare Buildkandidat ist **Android 1.3.2 / versionCode 200052**. Source-Check `server/checks/85_verify_android_200052_compile_imports.py` schützt beide Imports, die dazugehörigen Entity-Felder und den Versionsstand.

Praktischer Build-Retest ist noch offen: GitHub Desktop aktualisieren und `assembleDebug` erneut ausführen. Erst ein erfolgreicher APK-Build ist ein Buildnachweis; GitHub Actions ohne Runner-Steps gelten weiterhin nicht als Test.

## Praktisch bestätigt am 2026-09-20 – Android-Geräteaktivierung

Android **1.3.2 / versionCode 200052** wurde nach dem Compile-Fix erfolgreich gebaut/installiert. Der Benutzer hat die Geräteaktivierung mit einem frischen **Server-1.0-QR** praktisch bestätigt: der QR-Code funktioniert und das Gerät lässt sich aktivieren. Die Aktivierungs-/Serveradresslogik ist damit geschützt und wird nicht ohne reproduzierbaren Fehler wieder umgebaut.

Nächster sinnvoller Android-Schritt ist die vorhandene Server-1.0-Release-Infrastruktur tatsächlich im Client zu nutzen. Der Server besitzt bereits `/api/v1/client-compatibility`, Release-Katalog, Admin-Binärupload, Veröffentlichung und öffentlichen APK-Download. Es soll **keine zweite Update-Infrastruktur** entstehen. Ziel: APK einmal in der Admin-Oberfläche hochladen/veröffentlichen; Pixel und Poco erkennen die neue Version in der App, laden sie über HTTPS und öffnen nach Integritätsprüfung den Android-Paketinstaller.

Zusätzlich gewünscht: Benutzerdaten im Admin nachträglich bearbeiten. Der bestehende Benutzervertrag wird um einen sicheren Änderungsweg erweitert, ohne stabile Benutzer-ID oder bestehende Gerätebindung unnötig zu ersetzen. Für abgelaufene Geräte-Aktivierungs-QRs existiert technisch bereits die Erzeugung eines neuen 30-Tage-Einmal-Keys; die Admin-Oberfläche soll diesen Ablauf künftig klar als **QR erneuern / neuen QR erzeugen** darstellen.

## Verbindliche Strategie

Serverbasis vollständig bauen und testen, danach kontrollierter NAS-Cutover mit vorher geprüftem Backup, anschließend Android systematisch auf `/api/v1` umstellen. Der derzeitige Server-Fachdatenbestand ist Testbestand und darf beim 1.0-Cutover zurückgesetzt werden. GitHub-Code, Konfiguration/Secrets und Backups bleiben erhalten.

**Ab dem ersten echten produktiven Vermessungsimport sind Originaldateien unveränderlich und dürfen durch spätere Updates/Resets nicht gelöscht oder überschrieben werden.** Bearbeitete, geparste und berechnete Daten liegen getrennt und bleiben neu erzeugbar.

## TB-Server 1.0 – umgesetzt

### Foundation / Fachkern

Vorhanden sind Migration Runner mit SHA-256-Prüfung/Advisory-Lock, Firmen/Benutzer/Portal, Baustellenbaum, Rechteprofile, Teams, granularer BTB, Live-Sync, Dateien/Chunkupload, Mängel, Abnahmen, Track-Arbeitsdaten, Change Feed, Audit und `X-Client-Change-Id`-Receipts. Replay-sicher sind die relevanten Fachmutationen für Track, Mängel, Abnahmen, BTB, Baustellenstruktur/Mitglieder und Upload-Lebenszyklus.

### Geräteaktivierung / Android-Bearer

Der getestete Android-Vertrag bleibt kompatibel: `POST /api/v1/device/login`, danach `Authorization: Bearer <token>` und `GET /api/v1/device/me`. Aktivierungs-Key und Geräte-Token werden nur gehasht gespeichert; maximal ein aktives Gerät je Benutzer wird per DB-Index erzwungen. Admin kann Geräte freigeben oder blockieren. `/api/v1/admin/*` und `/api/v1/portal/*` bleiben Portal-Session + CSRF; die Fach-API akzeptiert zusätzlich Geräte-Bearer.

### Dateien / Bilder / Chunkupload

Resumierbarer sequenzieller Upload mit Offset-/Größen-/SHA-256-Prüfung, fsync, atomarem Abschluss, Datei-Verknüpfungen und Replay-Sicherheit ist vorhanden. Geräte-Bearer erzeugt `tb.files.source=APP`, Portal-Session `PORTAL`; bei Geräteauth enthält das Audit `actor_device_id`, aber keine Geheimwerte.

### Unveränderliche Vermessungsoriginale / Track-Arbeitsdaten

Originalimport prüft Größe + SHA-256 und legt atomar unter `reference/survey-originals/<uuid>/...` ab. Es gibt keine PATCH-/PUT-/DELETE-Endpunkte und kein SQL-Update/Delete für Originale. Veränderte Arbeitsdaten liegen separat in `tb.track_data` mit Optimistic Locking und Tombstones.

### Vermessungsparser / Parse-Katalog

Serverseitiger Parservertrag basiert auf den vorhandenen Android-Parsern:

- TRA: binäre ER0-Struktur, 0x48-Header, 78 Byte pro Knoten;
- GRA: 36-Byte-Header, 36 Byte pro Punkt;
- GVP/Festpunkte: GVP-Text, rohe KF 7.0 / 7.0 R mit 102-Byte-Datensätzen, KF.TXT und KV;
- KOR: nur die belegte `.kor.txt`-Variante; rohe/unbekannte `.kor` bleiben `UNSUPPORTED`;
- komplette Null-Stationen werden nicht als belastbare KM-Quelle verwendet;
- DB_REF/GK-Streifen 3 bleibt als Quellkoordinate erhalten; GPS ist nur abgeleitet.

Migration `003_survey_parse_catalog.sql` enthält `tb.survey_parse_catalog` als neu aufbaubaren Parse-Stand. Vor jedem Parse werden Dateigröße und SHA-256 des Originals erneut geprüft.

Parser v2 wurde gegen den vorhandenen realen Absteckungsbestand verifiziert. Nach SHA-256-Deduplizierung wurden 55 TRA, 45 GRA, 4 rohe KF, 1 KV, 2 KF.TXT, 1 GVP-Text und 2 KOR.TXT ohne Parserfehler gelesen; zwei rohe/unbekannte KOR bleiben wie vorgesehen `UNSUPPORTED`. Die rohe KF Strecke 5362 mit 75 Festpunkten wird direkt gelesen, Zone-3-Koordinaten bleiben `DB_REF_GK_3`.

Die Dateinamenmetadaten sind date-safe: vorangestellte ISO-Datumsbestandteile werden vor der Streckensuche maskiert. TRA/GRA übernehmen additiv die vorhandenen Android-Regeln für Strecke, Gleis, Quellrichtung und Achstyp.

Details: `docs/TB-SERVER_1_0_SURVEY_FIXTURE_VERIFICATION.md`.

### Vermessungsabschnitte / KM-Kalibrierung

Migration `004_survey_sections.sql` ergänzt die bestehenden `track_data`-Arbeitsdaten:

- `tb.track_data_sources`: mehrere unveränderliche Originalquellen je `SURVEY_SECTION`, Rolle/Reihenfolge/optional `reverse_geometry`;
- `tb.track_data_km_points`: `ORIGINAL_ANCHOR`, `MANUAL_ANCHOR`, `CONTROL_ANCHOR`, `KM_JUMP`.

Die dedizierte Fach-API nutzt `track.edit`, `X-Client-Change-Id`, Change Feed, Audit und Optimistic Locking. Der generische `track-data`-Create-/Update-Pfad darf `dataset_kind=SURVEY_SECTION` nicht schreiben.

Farbregel:

- belastbare automatische Originalanker -> `AUTOMATISCH/GRUEN`;
- manuelle 2-Punkt-Kalibrierung oder ein Anker plus bekannte Richtung -> `MANUELL/ORANGE`;
- Geometrie ohne belastbare oder mit widersprüchlicher KM-Kalibrierung -> `ROT`;
- keine Geometrie -> `GRAU`.

KM-Sprünge werden diskret berücksichtigt und nicht in den kontinuierlichen Faktor eingerechnet. Alle beteiligten Anker werden gegen die resultierende KM-Funktion geprüft; maximale zulässige Ankerabweichung ist 5 m. Darüber -> `UNPLAUSIBEL/ROT`.

### TRA-Rohgeometrie / kontinuierliche geometrische Chainage

Die Rohgeometrie bleibt Android-parallel: 2,0-m-Samples, Gerade/Kreisbogen analytisch, Übergangsbogen mit 0,5-m-Integration.

Neu ist eine getrennte **abgeleitete** Chainage-Schicht `survey_tra_chainage.py`. Reale TRA-Dateien enthalten Nullweg-Elemente mit `raw_type=6`, praktisch identischer Start-/Endkoordinate und diskretem Stationssprung. Diese Werte dürfen nicht als zusätzliche geometrische Gleislänge verdichtet werden.

Eine Diskontinuität wird nur erkannt, wenn gleichzeitig gilt:

- `raw_type=6`;
- Sprungbetrag >= 0,001 m;
- geometrische Sehne <= 0,01 m;
- Stationssprung und gespeicherte Elementlänge stimmen bis 0,01 m überein.

Der Parserpayload bleibt unverändert. Nur für Berechnungen wird der kumulierte Sprung aus allen folgenden Stationen entfernt und das Nullwegelement auf abgeleitete Länge 0 gesetzt. Diese Stationsbasis heißt `CONTINUOUS_GEOMETRY_CHAINAGE`.

Reale Gegenprüfung des deduplizierten Bestands:

- 55 unterschiedliche TRA-Dateien;
- 8 belegte Typ-6-/Nullweg-Sprünge in 4 unterschiedlichen Dateien;
- Beispiele: 5302 `+2,120`, `+73,660`, `+19,3963`, `-2,0221 m`; 5300/8771 `-6,09559 m`; 5504 `+6,351` und `-28,948 m`; 5524 `-12,562482 m`;
- mehrere Werte stimmen unabhängig mit realen GRA-Sprungpaaren überein.

Details: `docs/TB-SERVER_1_0_SURVEY_REAL_FIXTURE_VERIFICATION.md` und `docs/TB-SERVER_1_0_SURVEY_PROJECTION.md`.

### DB_REF / Punktprojektion v3

Die vorhandene Android-Transformation DB_REF/GK -> ETRS89/WGS84 wurde mit denselben Helmertparametern portiert und erkennt GK-Streifen 3, 4 und 5.

`POST /api/v1/survey-sections/{dataset_id}/rebuild-auto-anchors` projiziert koordinatenhaltige `KM`-/`CONTROL`-Quellen auf die verdichteten TRA-Geometrien. Die Projektionslogik läuft jetzt als **`survey-projection-v3`** und verwendet ausschließlich `CONTINUOUS_GEOMETRY_CHAINAGE`.

Automatischer `ORIGINAL_ANCHOR` erfordert:

- Quelle `KM`;
- bestätigte Strecke;
- Achsabstand <= 5 m;
- und einen belastbaren gemeinsamen Stationsraum der getroffenen TRA.

Alte `survey-projection-v*`-Punkte und automatisch erzeugte `survey-gra-jump-v*`-Punkte werden beim Projektions-Rebuild entfernt. Manuelle Anker und manuelle KM-Sprünge bleiben erhalten.

### Mehrere TRA-Quellen / Stationsraum-Merge v2

`survey_station_merge.py` läuft jetzt als **`survey-station-merge-v2`** und verwendet kontinuierliche geometrische Chainage.

Die lokale geometrische Chainage einer TRA wird als abgeleitete lineare Abbildung in einen gemeinsamen Stationsraum überführt:

`merged_station = station_sign * local_chainage + station_offset`.

Vor einer Zusammenführung müssen Strecke und zusätzlich Gleis oder Richtung zusammenpassen.

`OVERLAP_CONFIRMED` ist belastbar bei:

- Fit-Treffer max. 1,0 m geometrischer Abstand;
- mindestens 5 Treffer;
- mindestens 50 m Überlappung;
- maximal 0,5 m Stations-Fit-Restfehler;
- geprüfter Vorwärts-/Rückwärtsabbildung;
- belastbarer Mapping-Vorgeschichte.

`ENDPOINT_CONFIRMED` bis 5 m Endpunktabstand ist nur diagnostisch und besitzt `reliable_for_auto_anchor=false`.

Reale Harburg -> 8771 Verifikation / Strecke 5300 bleibt stabil:

- R: 107 Fit-Treffer, ca. 1058,226 m Überlappung, Offset ca. `+1188,999027 m`, max. Restfehler ca. `0,000240 m`;
- S: 163 Fit-Treffer, ca. 1615,748 m Überlappung, Offset ca. `-0,035731 m`, max. Restfehler ca. `0,000244 m`.

Der reale `-6,09559-m`-Typ-6-Sprung im 8771-S-Bestand liegt außerhalb der Harburg-Überlappung. Die Überlappung bleibt numerisch stabil, während die weitere Stationierung nach dem Sprung jetzt korrekt kontinuierlich bleibt.

### Konservativer GRA-KM-Sprung-Rebuild v2

`POST /api/v1/survey-sections/{dataset_id}/rebuild-gra-jumps`

Das reale GRA-Muster `Parameter 1000 -> 0` bei praktisch gleicher Höhe bleibt nur Kandidat; automatische Übernahme erfordert weiterhin bestätigte Achsidentität und geometrisch projizierte Originalanker auf beiden Seiten. Beide unabhängig ermittelten Sprungstationen dürfen höchstens 2 m auseinanderliegen. `allow_one_sided=false` bleibt Default.

Seit **`survey-gra-jump-v2`** muss die Section zusätzlich eine aktuelle `survey-projection-v3` mit `station_basis=CONTINUOUS_GEOMETRY_CHAINAGE` besitzen. Dadurch wird verhindert, dass ein Sprung gegen eine alte diskontinuierliche TRA-Quellstation oder eine veraltete Projektion berechnet wird.

`reverse_geometry=true` der primären Geometrie wird weiterhin konservativ nicht automatisch behandelt.

### Reale Punktprojektions-/KM-Verifikation

Gessertshausen 5302:

- rohe KF: 124 verwertbare Punkte mit Strecke/KM;
- 5302L: 46 Punkte <=5 m zur TRA, Median ca. 3,566 m, Maximum ca. 4,372 m;
- 5302R: 45 Punkte <=5 m, Median ca. 3,489 m, Maximum ca. 3,963 m;
- DB_REF-Direktprojektion vs. WGS84-Projektion: maximale Stationsdifferenz ca. 0,029 m;
- GRA-Sprung `17626,340 -> 17700,000` = +73,660 m.

8771 / Strecke 5300 R:

- rohe KF: 120 KM-Punkte;
- 119/120 liegen innerhalb 5 m einer der R/S-TRA;
- GRA-Sprung `55606,095653 -> 55600,000063` = ca. -6,095590 m.

Harburg 5300:

- rohe KF: 86 plausible Punkte, KM-/Stationsspalte vollständig 0.00000;
- diese Datei bleibt zu Recht keine automatische KM-Quelle.

Historische Sprungstationszahlen aus der vorherigen Projektionsbasis sind in der Real-Fixture-Dokumentation als solche gekennzeichnet. Nach v3 werden Sprungstationen neu gegen kontinuierliche Chainage berechnet; alte Auto-GRA-Punkte werden invalidiert.

### Release-Binärupload / Download

Der vorhandene Release-Katalog kann jetzt neben externen Download-URLs auch serverseitige Binärdateien verwalten.

- `PUT /api/v1/admin/releases/{release_id}/binary` streamt einen Draft in den Server-Storage, maximal 512 MiB;
- optionales `X-Release-SHA256` wird serverseitig gegen die während des Streams berechnete Prüfsumme verifiziert;
- stabiler interner Storage-Key: `releases/<platform>/<release_uuid>/package.bin`;
- Dateitausch nutzt Tempdatei + atomaren Replace + Rollback;
- veröffentlichen ist nur mit internem Binary oder externer Download-URL zulässig;
- veröffentlichtes Binary ist über `GET /api/v1/releases/{release_id}/download` verfügbar;
- Draft-Binaries können entfernt/ersetzt werden, veröffentlichte Releases bleiben unveränderlich;
- öffentliche und bereinigte Admin-Payloads geben keinen internen Storage-Key aus.

Admin-Webseite: `GET /admin/v1/releases`.

Capability: `release_binary_v1=true`.

Details: `docs/TB-SERVER_1_0_RELEASES.md`.

### BTB-Abschluss-PDF / Ein-PDF-/Originalmodell

Der Server setzt jetzt die verbindliche BTB-Regel um, dass eine `btbId` höchstens ein aktuell gültiges Abschluss-PDF besitzt.

- erster erfolgreicher Abschluss speichert den unveränderlichen Original-Snapshot;
- der Original-Snapshot enthält auch den beim ersten Abschluss tatsächlich zugeordneten Foto-Bestand;
- Reaktivieren eines abgeschlossenen BTB setzt nur den Team-Zeiger wieder auf dieselbe `btbId`; Status und PDF bleiben zunächst gültig;
- erst eine tatsächlich gespeicherte Kopf-/Fach-/Fotoänderung setzt den BTB auf `REWORK` und invalidiert das bisherige Abschluss-PDF;
- erneuter Abschluss ersetzt dasselbe stabile PDF derselben `btbId`, niemals das Original;
- fachlich neue/geänderte Einträge und neue/geänderte Fotos werden gegenüber dem ersten Original rot dargestellt; entfernte Originaleinträge/-fotos erscheinen rot als `(entfernt)`;
- Datum/Arbeitszeit und die übrigen Kopfdaten bleiben im PDF schwarz;
- wenn ein reaktivierter abgeschlossener BTB ohne Änderung wieder abgeschlossen wird, wird nur `active_btb_id` gelöst; das bestehende gültige PDF wird nicht neu erzeugt;
- beim vollständigen BTB-Löschen werden Original-Snapshot und BTB-PDF entfernt, echte Baustellenfotos bleiben erhalten und werden lediglich von der `btbId` gelöst;
- neue BTB-Fotos über den bestehenden Uploadpfad invalidieren ein gültiges Abschluss-PDF ebenfalls erst bei der tatsächlichen neuen Bildzuordnung.

Servermodule:

- `server/tbserver/btb_documents.py` – stabiler Storage-Key, Snapshot-/Foto-Helfer, atomarer PDF-Tausch und Rollback;
- `server/tbserver/btb_pdf.py` – ReportLab-Renderer mit Original-Diff;
- `server/tbserver/btb.py` – Abschluss, Reaktivierung, Download und Foto-Lösen;
- `server/tbserver/files.py` – BTB-Fotoänderungen an denselben Rework-Vertrag angebunden.

Capability: `btb_finalize_with_pdf_v1=true`.

Details: `docs/TB-SERVER_1_0_BTB_PDF.md`.

### Firmenprofil / Firmenlogo / PDF-Logo-Layout

TB-Server 1.0 nutzt für die zentralen Firmendaten bewusst die bereits in Migration 001 vorhandenen Felder `tb.companies.settings`, `pdf_layout` und `logo_file_id`; eine parallele Legacy-`company_profiles`-Tabelle oder neue Migration ist dafür nicht nötig.

Mobile/Allgemein:

- `GET /api/v1/company/profile`;
- `GET /api/v1/company/profile/logo`;
- `GET /api/v1/company/profile/pdf-logo-layout`;
- `PUT /api/v1/company/profile/pdf-logo-layout`.

Admin:

- `GET /api/v1/admin/companies/{company_id}/profile`;
- `PUT /api/v1/admin/companies/{company_id}/profile`;
- `PUT /api/v1/admin/companies/{company_id}/logo`;
- `DELETE /api/v1/admin/companies/{company_id}/logo`.

Logo-Regeln:

- PNG/JPEG/WEBP nur per Magic-Byte-Erkennung;
- maximal 5 MiB;
- stabiler Storage-Key `companies/<company_uuid>/profile/logo`;
- SHA-256 und MIME serverseitig ermittelt;
- temporärer Dateitausch + Rollback bei DB-Fehlern;
- Geräte laden das Logo derselben Firma und cachen lokal.

Das bestehende firmenweite Android-PDF-Layout bleibt kompatibel: Breite 40–250 pt, X -120..120, Y -60..180, Anzeigen von Name/E-Mail/Telefon und `contacts_in_image`, jeweils mit monotoner `layout_version`.

Der Server-BTB-PDF-Renderer läuft dafür jetzt als `tb-server-btb-pdf-2` und verwendet Firmenlogo/Layout direkt aus der Firma der Baustelle. Firmenkopf/Kopfdaten bleiben unabhängig vom fachlichen Original-Diff schwarz.

Capabilities:

- `company_profile_v1=true`;
- `company_logo_v1=true`;
- `company_pdf_logo_layout_v1=true`.

Android `TeamManagementApi.CompanyProfile.companyId` wurde auf `String` umgestellt, weil TB-Server 1.0 Firmen-UUIDs liefert; alte numerische IDs bleiben als String lesbar.

Server-1.0-Weboberfläche auf demselben Fachvertrag:

- `GET /portal/v1/login` und `GET /portal/v1`;
- `GET /admin/v1/companies`;
- `GET /admin/v1/companies/{company_id}/branding`.

Die Admin-Seite kann Firmen anlegen, Stammdaten pflegen, Logo hochladen/löschen, PDF-Logo-Layout ändern und den Firmenkopf im Browser vorab positionieren. Schreibzugriffe laufen ausschließlich über Portal-Session + `X-CSRF-Token`; Geräte-/Bearer-Geheimnisse werden nicht ins HTML geschrieben.

Capability zusätzlich: `company_branding_admin_ui_v1=true`.

Details: `docs/TB-SERVER_1_0_COMPANY_BRANDING.md`.

### Portal-Fachoberfläche v1

Die native Server-1.0-Weboberfläche deckt jetzt auch den ersten Fachclient-Bereich ab:

- `GET /portal/v1/worksites` – rollen-/mitgliedschaftsgesteuerte Baustellenliste plus Neuanlage;
- `GET /portal/v1/worksites/{worksite_id}` – Baustellendetail mit Struktur, Mitgliedern, Teams, Team-BTBs, Mängeln und Abnahmen;
- `GET /portal/v1/btbs/{btb_id}` – Kopfdaten/Facheinträge, Reaktivierung, Abschluss, Abschluss-PDF und Löschen.

Die Oberfläche ruft ausschließlich die bestehenden Fachendpunkte auf. Rechte bleiben serverseitig in `worksite_context`/den Fachroutern verbindlich; das Web blendet nicht erlaubte Aktionen zusätzlich aus.

Die gemeinsame Browser-API-Hilfe setzt bei Nicht-GET-Aufrufen automatisch `X-CSRF-Token` sowie `X-Client-Change-Id`. Bearer-/Gerätegeheimnisse werden nicht in Browser-Templates eingebaut.

Aktuell direkt möglich: Strukturknoten anlegen/bearbeiten/löschen, Rechteprofil/Bereich bestehender Mitglieder ändern, gezielte Baustelleneinladungen erzeugen/widerrufen/annehmen, Team-BTB starten, BTB-Kopf/Facheinträge bearbeiten, BTB-Fotos per bestehendem SHA-256-/Chunkupload verknüpfen/lösen, BTB reaktivieren/finalisieren/PDF öffnen/löschen sowie Mängel und Abnahmen anlegen/bearbeiten/abschließen.

Capability: `portal_worksite_fach_ui_v1=true`.

Details: `docs/TB-SERVER_1_0_PORTAL_FACH_UI.md`.

### Android-Einladung Server 1.0

Android versteht jetzt denselben Einladungsvertrag wie Server/Portal:

- `tbhelfer://worksite-invite?token=...` ist im Manifest registriert;
- moderner Server-Token bleibt case-sensitive und wird niemals uppercased;
- Portal-Link `/portal/v1/invitations/redeem#token=...` und roher Token können ebenfalls eingefügt/eingescannt werden;
- Legacy `TBI-...` und `tbhelfer://invite?code=...` bleiben während der Umstellung kompatibel;
- `TeamManagementApi.redeemServerInvitation()` nutzt `POST /api/v1/worksites/invitations/redeem` mit Geräte-Bearer und eigener `X-Client-Change-Id`;
- `TeamManagementApi.loadServerInvitationCandidates()` lädt konkrete aktive Firmenbenutzer, die noch kein aktives Baustellenmitglied sind;
- `TeamManagementApi.createServerInvitation()` erzeugt gezielte Einladungen gegen `POST /api/v1/worksites/{worksite_id}/invitations` mit eigener `X-Client-Change-Id`;
- Android wählt vor dem Erzeugen eine konkrete Person aus; Helfer behalten den vorhandenen Bereichsdialog und werden als `HELPER/HELPER_STANDARD` eingeladen, weitere verantwortliche TBs als `RESPONSIBLE_TB/RESPONSIBLE_TB`;
- nur wenn der neue Kandidaten-Endpunkt 404 liefert, wird während der Umstellung auf den bisherigen Legacy-Create-Pfad zurückgefallen; fachliche Server-1.0-Fehler erzeugen keine ungezielte Legacy-Einladung;
- die erste Server-1.0-Create-Antwort liefert Einmalcode/App-/Browser-Link; QR wird lokal aus der App-URI erzeugt, Teilen verwendet bevorzugt den Browser-Link;
- fachliche 403-Einladungsfehler werden nicht fälschlich als ungültige Gerätesession behandelt;
- bei noch nicht aktiviertem Gerät bleibt die Einladung bis nach erfolgreicher Aktivierung erhalten und wird anschließend automatisch eingelöst;
- Baustellen-Sync-QR-Scanner und manuelle Eingabe verwenden denselben zentralen Parser.

Zusätzlich lädt `TeamManagementApi.loadTeam()` den Server-1.0-Teamstand aus Worksite-, Device-Me-, Member- und Team-Endpunkten, wenn `worksite_teams_v1=true` gemeldet wird. `RESPONSIBLE_TB/TB` werden für die bestehende Android-UI als `LEAD` abgebildet; `currentGroupId` und Area-Scope stammen aus der aktuellen Membership. Fehlt der Capability-Endpunkt oder die Capability, bleibt der Legacy-Collab-Teamendpunkt als Übergang erhalten; Netzwerkfehler führen nicht zu einem stillen Legacy-Fallback.

Unit-Test: `WorksiteInvitationLinkTest`. Android CI führt künftig `:app:testDebugUnitTest` vor den Builds aus.

Zusätzliche Vertragschecks: `server/checks/48_verify_android_server_1_invitation_source.py`, `server/checks/50_verify_android_server_1_invitation_create_source.py` und `server/checks/51_verify_android_server_1_team_source.py`.

Details: `docs/TB-ANDROID_SERVER_1_0_INVITATIONS.md`.

### Admin-Systemstatus / Speicher v1

`GET /api/v1/admin/system/status` liefert ausschließlich für Portal-ADMIN den Systemzustand ohne Geheimnisse oder interne Job-Payloads:

- Server-/Migrationsstand;
- PostgreSQL-Datenbankgröße;
- Firmen/Nutzer/aktive Geräte/Baustellen/BTBs/Dateien;
- offene Mängel und nicht finalisierte Abnahmen;
- erfasste Dateigröße;
- Storage- und Backup-Storage-Belegung;
- letzte Backup-Läufe ohne internen Storage-Key;
- letzte Background Jobs ohne Payload/`last_error`;
- whitelisted, nicht geheime `server_settings`.

Webseite: `GET /admin/v1/system`.

Neben der read-only Statusanzeige können ADMINs DATABASE-/FILES-/FULL-Backup-Jobs und Restore-Tests einreihen. Die eigentliche Ausführung erfolgt im separaten Background-Worker.

Capability: `admin_system_status_v1=true`.

Details: `docs/TB-SERVER_1_0_SYSTEM_STATUS.md`.

### Background Jobs / Backup v1

Der DB-gestützte Worker verwendet die bereits vorhandene Tabelle `tb.background_jobs`.

- Claim über `FOR UPDATE SKIP LOCKED`;
- maximal fünf Versuche mit Backoff;
- verwaiste `RUNNING`-Jobs werden nach 30 Minuten wiederhergestellt;
- Jobs: `BACKUP_DATABASE`, `BACKUP_FILES`, `BACKUP_FULL`, `RESTORE_TEST`;
- Workerstart: `python -m tbserver.jobs worker`.

`TB_BACKUP_ROOT` muss außerhalb von `STORAGE_ROOT` liegen.

Backuparten:

- DATABASE: PostgreSQL-17-`pg_dump` im Custom-Format, Passwort nur über `PGPASSWORD`;
- FILES: kompletter TB-Storage als `.tar.gz`;
- FULL: `database.dump` + `files.tar.gz` + Manifest mit inneren SHA-256-Werten.

Alle finalen Backupdateien erhalten Größe + SHA-256 in `tb.backup_runs`. `POST /api/v1/admin/system/backups` reiht idempotent einen Backup-Job ein.

Capabilities: `background_jobs_v1=true`, `backup_v1=true`.

### Restore-Test / Test-Reset

`POST /api/v1/admin/system/backups/{backup_run_id}/restore-test` stellt einen `RESTORE_TEST`-Job ein.

Für DATABASE/FULL ist eine getrennte Restore-Test-Datenbank über `TB_RESTORE_TEST_DB_*` Pflicht. Ihr Datenbankname darf nicht dem Live-`DB_NAME` entsprechen. Der Worker löscht ausschließlich dort das `tb`-Schema, führt `pg_restore` aus und verifiziert danach Migrationsregister + Kerntabellen.

FILES/FULL werden nur in eine temporäre Restore-Test-Ablage unterhalb des Backup-Roots extrahiert. Archive mit absoluten/`..`-Pfaden, Symlinks/Hardlinks oder Device-Dateien werden abgelehnt; FULL prüft zusätzlich Layout und beide inneren SHA-256-Werte gegen `manifest.json`.

Test-/Staging-Reset ist ausschließlich als CLI vorhanden:

`python -m tbserver.reset_test reset-test-data --backup-run-id <uuid> --confirm RESET-TEST-DATA:<uuid>`

Schutz:

- nur `development|test|staging`;
- erfolgreicher FULL-Backup-Lauf Pflicht;
- Backup höchstens 24 Stunden alt;
- Backupgröße + SHA-256 erneut verifiziert;
- dynamische Bestätigung mit Backup-UUID;
- Backup-Root außerhalb Storage;
- danach frische Server-1.0-Migrationen und leerer Test-Storage.

Capabilities: `restore_test_v1=true`, `test_data_reset_v1=true`.

Ein produktiver Restore wird damit **nicht** freigegeben.

Details: `docs/TB-SERVER_1_0_BACKUP_RESTORE.md`.

### Kontrollierter Restore-to-Target v1

Produktiver Restore wird nicht als In-place-Webaktion gebaut. `server/tbserver/restore_target.py` stellt ausschließlich ein verifiziertes FULL-Backup in eine getrennte, frische Ziel-Datenbank und einen getrennten leeren Ziel-Storage wieder her.

Sicherheitsgrenzen:

- zusätzliche ENV-Freigabe `TB_ALLOW_RESTORE_TARGET=YES`;
- dynamische CLI-Bestätigung `RESTORE-TARGET:<backup_uuid>:<target_db_name>`;
- Ziel-DB-Name ungleich Live-`DB_NAME`;
- Ziel-DB ohne vorhandenes `tb`-Schema;
- Ziel-Storage disjunkt von Live-Storage und Backup-Root sowie leer;
- FULL-Manifest + innere/äußere SHA-256 werden geprüft;
- `pg_restore` läuft ohne `--clean` gegen das frische Ziel;
- Fehler räumen nur den getrennten Zielzustand wieder auf;
- Live-DB und Live-Storage werden nicht verändert.

Migration 005 erlaubt `RESTORE_TARGET` im Backup-/Restore-Laufprotokoll. Capability: `restore_target_v1=true`.

Der eigentliche Produktionswechsel bleibt ein separater Cutover nach Prüfung der wiederhergestellten Zielinstanz.

### Releases / Client-Kompatibilität

Der Release-Katalog liefert die stabile Client-Kompatibilität und kann neben externen Download-URLs auch serverseitige Binärdateien verwalten.

Öffentliche Endpunkte:

- `GET /api/v1/client-compatibility`;
- `GET /api/v1/releases/{platform}/latest`;
- `GET /api/v1/releases/{release_id}/download` für veröffentlichte interne Binärdateien.

ADMIN:

- Release-Draft anlegen/ändern;
- internes Binary per `PUT /api/v1/admin/releases/{release_id}/binary` streamen;
- Draft-Binary entfernen;
- veröffentlichen/zurückziehen;
- Admin-Webseite `GET /admin/v1/releases`.

Der Binärupload ist auf 512 MiB begrenzt, kann `X-Release-SHA256` prüfen und verwendet Tempdatei + atomaren Replace + Rollback. Ein Release kann nur mit internem Binary oder externer Download-URL veröffentlicht werden. Veröffentlichte Release-Metadaten/Binaries werden nicht still überschrieben. Öffentliche und bereinigte Admin-Payloads geben keinen internen Storage-Key aus.

Capabilities: `release_catalog_v1=true`, `release_binary_v1=true`, `client_compatibility_v1=true`.

Details: `docs/TB-SERVER_1_0_RELEASES.md`.

### Docker-/NAS-Staging-Basis v1

`server/docker-compose.v1.yml` beschreibt jetzt den reproduzierbaren Server-1.0-Stack:

- PostGIS/PostgreSQL 17 mit persistentem DB-Pfad;
- einmaliger `migrate`-Dienst vor API/Worker;
- API mit getrenntem persistentem `STORAGE_ROOT` und `TB_BACKUP_ROOT`;
- eigener Background-Worker auf demselben Image;
- Ready-/DB-Healthchecks;
- Default-API-Bindung `127.0.0.1:8081`;
- DB-Passwort nur über externe Compose-Variable `TB_V1_DB_PASSWORD`.

Standardpfade für lokale/Staging-Nutzung: `database-v1`, `storage-v1`, `backups-v1`. Für Synology werden diese bewusst auf getrennte persistente NAS-Pfade gesetzt.

Restore-Test/Restore-to-Target sind keine automatischen Compose-Startdienste, sondern bleiben explizite Wartungsaktionen.

Details: `docs/TB-SERVER_1_0_DEPLOYMENT.md`.

### Backup-Retention v1

Backups besitzen jetzt eine sichere Aufbewahrungsregel:

- `TB_BACKUP_RETENTION_DAYS`, Default 30;
- `TB_BACKUP_RETENTION_MIN_PER_KIND`, Default 3;
- Ranking getrennt für DATABASE/FILES/FULL;
- nur alte Backups oberhalb der Mindestanzahl werden Kandidaten;
- `BACKUP_RETENTION` läuft im Background Worker;
- ADMIN kann die Prüfung über `POST /api/v1/admin/system/backups/retention` einreihen;
- Migration 006 ergänzt `PURGED`, damit entfernte Artefakte nicht als fehlgeschlagene Backups erscheinen;
- DB-Referenz wird vor dem physischen Löschen entfernt, sodass ein Löschfehler höchstens eine unreferenzierte Datei zurücklässt.

Capability: `backup_retention_v1=true`.

### Einladung Code / Link / QR v1

Der vorhandene gezielte Einladungsvertrag besitzt jetzt drei Darstellungsformen derselben kurzlebigen Einladung:

- Rohcode für Copy/Paste;
- App-URI `tbhelfer://worksite-invite?token=...`;
- Browser-Link `/portal/v1/invitations/redeem#token=...`;
- SVG-QR-Code auf Basis der App-URI.

Sicherheitsgrenzen:

- Rohcode, Link und QR werden nur in der ersten erfolgreichen Create-Antwort erzeugt;
- der Browser-Link trägt den Token ausschließlich im Fragment, nicht in Query/Path;
- die Portal-Einlöseseite liest das Fragment clientseitig und entfernt es anschließend aus der sichtbaren URL;
- Annahme bleibt eine bewusste Benutzeraktion;
- Create-Antwort setzt `Cache-Control: no-store`;
- Mutation Receipt und Audit speichern weder Rohcode noch Link/QR;
- ein idempotenter Replay gibt diese Geheimdarstellungen nicht erneut aus.

Die reine URI-/Fragment-/QR-Helferlogik wurde lokal gegen einen Token mit Leerzeichen/Sonderzeichen geprüft; URL-Decoding ergibt wieder exakt den Originaltoken und der QR-Helfer erzeugt ein SVG-Data-URL.

Capability: `worksite_invitation_qr_v1=true`.

Android-`QR scannen` wird später gegen exakt diese App-URI angebunden; es entsteht kein paralleler Einladungsvertrag.

### Einladung-only-Mitgliedschaft / Helfer-Teamwechsel

Die verbindliche Projektregel „ohne angenommene Einladung keine Baustellen-Zusammenarbeitsdaten“ wird jetzt auch im API-Vertrag selbst durchgesetzt.

- der alte direkte `POST /api/v1/worksites/{worksite_id}/members`-Pfad wurde entfernt;
- der Baustellenersteller bleibt weiterhin korrekt der erste verantwortliche TB;
- weitere Mitglieder entstehen nur über den sicheren Einladungs-/Redeem-Vertrag;
- `GET /api/v1/worksites/{worksite_id}/members` liefert zusätzlich die aktuelle Team-/TB-Zuordnung;
- `POST /api/v1/worksites/{worksite_id}/members/{member_user_id}/team` verschiebt ausschließlich bestehende `HELPER`;
- Zielteam muss aktiv und derselben Baustelle zugeordnet sein;
- alte Teamzuordnung erhält `removed_at`, die neue Zuordnung wird aktiviert;
- die Mitgliedschaft bleibt dieselbe UUID; ihre Sync-Version wird erhöht und ein Change-Event/Audit geschrieben;
- historische BTBs des bisherigen Teams bleiben unverändert.

Portal: vorhandene Helfer zeigen ihr aktuelles Team und können bei `members.manage` über „Team wechseln“ einem anderen aktiven TB-Team zugeordnet werden.

Capability: `worksite_team_transfer_v1=true`.

### Restore-Staging-Compose-Overlay

`server/docker-compose.restore-test.v1.yml` ergänzt getrennte Restore-Test-/Restore-Target-Datenbanken und einen eigenen Ziel-Storage nur für bewusst gestartete Staging-Tests. Der normale Compose-Start bleibt frei von Restore-to-Target-Zielen; Freigabe-Default ist `NO`. Source-Check 49 prüft diese Trennung.

## Tests / Checks

Workflow-Zielstand:

- Migrationen 001 + 002 + 003 + 004 + 005 + 006;
- `latest_known=6`, `latest_applied=6`;
- Kerntabellen u. a. `survey_parse_catalog`, `track_data_sources`, `track_data_km_points`;
- Source-/Vertragschecks 20–52;
- Health/Capabilities/Auth-Checks;
- erwartete Capabilities enthalten zusätzlich `survey_station_merge_v1`, `survey_gra_jump_detection_v1`, `release_catalog_v1`, `release_binary_v1`, `client_compatibility_v1`, `btb_finalize_with_pdf_v1`, `company_profile_v1`, `company_logo_v1` und `company_pdf_logo_layout_v1`, `company_branding_admin_ui_v1`, `portal_worksite_fach_ui_v1`, `worksite_invitations_v1`, `worksite_invitation_qr_v1`, `worksite_team_transfer_v1`, `admin_system_status_v1`, `background_jobs_v1`, `backup_v1`, `backup_retention_v1`, `restore_test_v1`, `test_data_reset_v1` und `restore_target_v1`.

Lokal bzw. gegen bereitgestellte Fixtures erfolgreich geprüft:

- Python-Syntax/AST der neuen/angepassten Vermessungsmodule im aufgebauten Source-Check;
- Raw-KF-Parität und Parser-v2-Realbestand;
- TRA-2-m-Rohverdichtung und DB_REF/GK->WGS84;
- automatische/manuelle KM-Kalibrierung inkl. KM-Sprung;
- All-Anchor-Konsistenzprüfung;
- GRA-Kandidatenfindung und beidseitige Sprungstationsschätzung;
- reale Harburg/8771-Überlappungsfits R/S;
- synthetischer Multi-TRA-Regressionsfall: primäre TRA + zuverlässig überlappende sekundäre TRA + nur per Endpunkt anschließende TRA;
- sekundäre Overlap-TRA kann einen automatischen `ORIGINAL_ANCHOR` im gemeinsamen Stationsraum liefern;
- Endpunkt-Mapping bleibt Kontrollanker;
- alte Projektionsversionen und automatisch erzeugte GRA-Sprünge werden als stationsabhängige Ableitungen erkannt;
- positive Typ-6-Diskontinuität `+73,660 m` wird ohne künstliche stationäre 2-m-Samples normalisiert;
- negative Typ-6-Diskontinuität `-6,09559 m` wird korrekt normalisiert;
- kompletter realer TRA-Bestand lokal erneut gescannt: 57 physische TRA-Dateien, 55 unterschiedliche SHA-256; 9 physische Typ-6-Fundstellen / 8 nach Deduplizierung; keine TRA-Parsefehler in dieser Gegenprüfung.

- Source-Check 33 inkl. reiner Statuslogik für aktuellen, optional veralteten, verpflichtend veralteten und nicht mehr unterstützten Client-Build.
- BTB-PDF-Renderer v1 lokal mit ReportLab erzeugt, als PNG gerendert und visuell geprüft: schwarzer Kopf, rote geänderte Fachinhalte und rote `(entfernt)`-Originalinhalte.
- Source-Check 35 ist als Syntax-/Vertragsprüfung für Firmenprofil, Logo, Layout, PDF-Branding, Capabilities und Android-UUID-Vertrag vorbereitet.
- Source-Check 36 ist als Syntax-/Vertragsprüfung für Portal-/Admin-Webrouten, Templates, CSRF und Branding-UI vorbereitet.
- Source-Check 37 ist als Vertragsprüfung für Baustellen-/Team-/BTB-/Mängel-/Abnahme-Portaloberfläche, CSRF, Browser-Idempotenz und BTB-Foto-Chunkupload vorbereitet.
- Source-Check 38 ist als Sicherheits-/Vertragsprüfung für gezielte Baustelleneinladungen inkl. gehashtem Einmalcode und tokenfreiem Mutation Receipt vorbereitet.
- Source-Check 39 ist als Syntax-/Vertragsprüfung für read-only Admin-Systemstatus/Storage inkl. Geheimnis-/Payload-Ausschluss vorbereitet.
- Source-Check 40 ist als Syntax-/Vertragsprüfung für Release-Binärupload/-download, SHA-256, atomaren Storage, Veröffentlichungsgrenzen und Admin-Weboberfläche vorbereitet.
- Source-Check 41 ist als Syntax-/Vertragsprüfung für Background Worker, Backup-Root-Trennung, DATABASE/FILES/FULL-Backup, SHA-256, Retry/Stale-Recovery und Admin-Backupauftrag vorbereitet.
- Source-Check 42 ist als Sicherheits-/Vertragsprüfung für getrennten Restore-Test, FULL-Manifest/SHA/Archivschutz und ausschließlich Development/Test/Staging erlaubten Backup-gesicherten Reset vorbereitet.
- Source-Check 43 ist als Sicherheits-/Vertragsprüfung für Restore-to-Target, frische getrennte Ziel-DB/Storage, explizite Freigabe/Bestätigung und fehlende HTTP-Exponierung vorbereitet.
- Source-Check 44 ist als Vertragsprüfung für Compose-Staging-Basis mit PostGIS 17, Migration-Service, getrennten Storage-/Backup-Mounts, API-Healthcheck und separatem Worker vorbereitet.
- Source-Check 45 ist als Vertragsprüfung für Backup-Retention, Mindestbestand je Art, PURGED-Status, idempotenten Admin-Auftrag und Compose-Policy vorbereitet.
- Source-Check 46 ist als Sicherheits-/Vertragsprüfung für Einladung-only-Mitgliedschaft, entfernten Direkt-Add-Pfad und replay-sicheren Helfer-Teamwechsel vorbereitet.
- Source-Check 47 ist als Syntax-/Vertrags-/Runtimeprüfung für App-URI, Fragment-Link, SVG-QR, `no-store`, tokenfreien Mutation Receipt und Portal-Fragmentübernahme vorbereitet.
- Source-Check 48 prüft den gemeinsamen Server-/Android-Vertrag für `worksite-invite`, case-sensitive Tokens, modernen Redeem-Endpunkt, Manifest-Deep-Link, Legacy-Fallback und Android-Unit-Test.
- Source-Check 49 prüft den optionalen Restore-Staging-Compose-Overlay, getrennte DB-/Storage-Ziele, externe Passwörter, fehlende Portfreigabe und standardmäßig deaktiviertes Restore-to-Target.
- Source-Check 50 prüft Android-Kandidatenladen und gezieltes Server-1.0-Einladungserzeugen, Client-Change-ID, Rollen-/Profilabbildung, Share-Link sowie den begrenzten Legacy-Fallback.
- Source-Check 51 prüft den Android-Teamaufbau aus Server-1.0-Worksite/Device-Me/Member/Team-Endpunkten sowie den ausschließlich bei Unsupported verwendeten Legacy-Team-Fallback.
- Source-Check 52 prüft den Android-Capability-Client sowie capability-gesteuerte Team-/Einladungsumschaltung ohne 0.6-/0.7-Versionsheuristik.
- Source-Check 53 prüft den Server-1.0-Kontext-only-Pfad über Worksites/Device-Me/Members.
- Source-Checks 54–56 härten Area-Scope, Datei-Elternsichtbarkeit und kanonisches Abnahme-Ergebnis.
- Source-Check 57 prüft den capability-gesteuerten Android-Lesesync für Mängel/Abnahmen inklusive lokaler Snapshot-Übernahme.
- Source-Check 58 prüft die idempotenten Android-Server-1.0-Schreibpfade für Mängel/Abnahmen, Outbox-Wiederverwendung, Konfliktzustände und endgültige Statusaktionen.
- Source-Check 59 prüft das Auflösen sichtbarer `files_v1`-Links auf Mängel/Abnahmen, den Download über `/files/{id}/content` und die Wiederverwendung des vorhandenen Attachment-Caches.
- Source-Check 60 prüft den resumierbaren Android-Chunk-Upload, Upload-Session-Recovery, `X-Client-Change-Id`, Complete-Link auf Mangel/Abnahme und die Weiterverwendung des Attachment-Caches.
- Source-Check 61 prüft, dass ein vollständiger sichtbarer `files_v1`-Snapshot nicht mehr sichtbare/gelöschte Mängel-/Abnahme-Dateien im lokalen Attachment-Cache tombstoned.
- Source-Check 62 verhindert gefährliches Tombstoning bei der aktuellen 1000-Dateien-Grenze: bei genau 1000 Listeneinträgen gilt der Snapshot als möglicherweise abgeschnitten und alte Cache-Einträge bleiben erhalten.
- Source-Check 63 prüft den capability-gesteuerten Android-Lesepfad für Team-BTB-Historie und BTB-Details sowie die Wiederverwendung des vorhandenen lokalen BTB-Importcaches.
- Source-Check 64 prüft den idempotenten Android-BTB-Schreibpfad mit bestehender Outbox, granularen BTB-Einträgen, Teamzuordnung, Versionskonflikten sowie `finalize`/`reopen`.
- Source-Check 65 erzwingt das granulare Server-1.0-Zielmodell und die automatische Migration des kurzzeitig verwendeten Gesamt-Bridge-Eintrags.
- Source-Check 66 prüft BTB-Foto-Download/-Upload über `files_v1`/`chunk_upload_v1`, BTB-Dateilinks und die erneute Finalisierung nach fotoinduzierter `REWORK`-Umschaltung.
- Source-Check 67 prüft den capability-gesteuerten Android-Download des serverseitigen Abschluss-PDFs, SHA-256, atomare lokale Ablage, lokale Invalidierung bei `REWORK` und dass dabei kein falsches Android-Erstoriginal erzeugt wird.
- Source-Check 68 prüft die vorhandene Offline-Löschabsicht/Outbox gegen den kanonischen `DELETE /api/v1/btbs/{id}`-Vertrag, Versions-Rebase, lokalen Tombstone und die Lösch-Reconciliation vor dem normalen BTB-Import.
- Source-Check 69 prüft den gemeinsamen capability-gesteuerten Server-1.0-Synckern für UI und Hintergrund-Worker, die unabhängige BTB/Fach/Datei-Capability-Auswertung und dass alte 0.6-/0.7-Versionsschwellen nur noch den Legacy-Fallback steuern.
- Source-Checks 57–60 sowie 63–68 wurden nach der Coordinator-Extraktion auf denselben gemeinsamen Synckern umgestellt; ihre fachlichen Vertragsprüfungen bleiben unverändert bestehen und prüfen nicht mehr die alte Codeposition im Fragment.
- Source-Check 70 prüft die stabile Cursor-Pagination der sichtbaren Server-1.0-Dateiliste, explizite Snapshot-Vollständigkeit und den Rolling-Update-Fallback auf die bisherige 1000-Dateien-Sicherheitsregel.
- Source-Check-Dateien 57–60/63–68 wurden nach der Coordinator-Extraktion vollständig neu ausgerichtet; dabei wurden versehentlich wörtlich gespeicherte `\\n`-Sequenzen und ein unvollständiger Check-68-Variablenumbau korrigiert. Check 59 prüft die 1000er-Grenze jetzt über `FILE_LIST_LIMIT` statt über ein nicht existentes hartes URL-Literal.
- Source-Check 71 prüft den kanonischen `worksite_tree_v1`-Cutover: stabile `external_key`/Client-Keys, Snapshot inklusive Tombstones, `base_version`-Konflikte, rekursives Unterbaum-Löschen, bestehende Offline-Outbox sowie begrenztes Konflikt-Rebase im gemeinsamen Server-1.0-Synckern.
- Source-Check 72 prüft den Android-Streckendaten-Cutover auf kanonisches Server-1.0-GET/POST/PATCH/DELETE mit `X-Client-Change-Id`, `base_version`, Reconcile-Schleife und ausdrücklich beibehaltenem 0.7.x-`/collab`-Fallback; Rohvermessungsdateien bleiben ausgeschlossen.
- Source-Check 73 prüft den Server-1.0-Baustellen-Lifecycle: idempotente kanonische Anlage, verantwortlicher-TB-only Soft-Delete mit `base_version`, generischer WORKSITE-Tombstone-Feed sowie explizite Android-0.7.x-Fallbacks.
- Source-Check 74 prüft den kanonischen baustellenweiten Medienkatalog: ausschließlich BTB-/Mangel-/Abnahme-Bilder, Cross-Team-Bildsicht nur für `RESPONSIBLE_TB`/CHEF/Admin, kein fremder BTB-Text, Area-Scope für Mängel/Abnahmen, kanonischer Datei-Download und expliziter 0.7.x-Fallback.
- Source-Check 75 stellt sicher, dass Portal und Android dieselben kanonischen Baustellenknotentypen und Payload-Felder verwenden; Parent-Zuordnung sowie Bearbeiten/Löschen sind im Portal versionsgesichert.
- Source-Check 76 prüft, dass Portal-Mängel und -Abnahmen dieselben fachlichen Payload-Schlüssel wie Android bearbeiten und Änderungen mit `base_version` schützen; JSON bleibt nur als additiver Expertenbereich erhalten.
- Source-Check 77 prüft Portal-Mangel-/Abnahmefotos über den bestehenden `worksite_media_v1`-Katalog und den resumierbaren `files_v1`/`chunk_upload_v1`-Vertrag einschließlich Android-kompatibler semantischer Dateinamen.
- Source-Check 78 prüft die verlustfreie Mangelstatus-Abbildung `ABGELEHNT ↔ REJECTED` inklusive Baseline, Migration 009, Server-API, Medienkatalog, Android-Sync und Portal.
- Source-Check 79 prüft die explizite Offline-Abnahme-Löschabsicht, `acceptance_delete_v1`, versionierten kanonischen DELETE, Finalize-Zusatzrecht, Server-Tombstone und die Bestätigung des lokalen Intents erst nach Servererfolg.
- Source-Check 80 prüft den Gegenweg: fremde `ACCEPTANCE`-Tombstones entfernen die lokale Abnahme ohne Echo-DELETE; vorhandene lokale Pending-Änderungen werden dabei nicht still verworfen.
- Source-Check 81 prüft den Portal-Abnahme-DELETE gegen denselben `acceptance_delete_v1`-Vertrag einschließlich `base_version` und der zusätzlichen Finalize-Berechtigung bei endgültig abgeschlossenen Abnahmen.

**Noch nicht ausgeführt:** Source-Checks 34–81 gegen den vollständigen neuen Branding-/Portal-/System-/Release-/Backup-/Restore-/Deployment-/Retention-/Teamwechsel-/QR-Stand (CI-Runner führt weiterhin keine Steps aus) sowie die vollständige PostgreSQL-/FastAPI-End-to-End-Kette für Import -> Parse -> Section -> Projection v3 -> GRA-Sprung v2 -> Kalibrierung. In der aktuellen Arbeitsumgebung stehen weder `postgres`/`psql` noch Docker/Podman zur Verfügung.

### Neue Admin-Übersicht Server 1.0 2026-09-19\n\n- `/admin/v1` ist jetzt die echte **Übersicht** statt einer Weiterleitung zur Firmenverwaltung.\n- Das neue Dashboard zeigt die vom Benutzer festgelegten Pflichtwerte: gesamter/freier NAS-Speicher, Upload der letzten 24 h, Download der letzten 24 h, Online-seit/API-Laufzeit, Probleme/Warnungen und ein 24-Stunden-Datenverkehrsdiagramm.\n- Zusätzlich werden aktive/alle Benutzer, aktive Geräte, Firmen, Baustellen, Dateien, App-Versionen, Vermessungsoriginale und Papierkorb-/Löschbestand als Schnellkennzahlen dargestellt.\n- `SystemMetricsMiddleware` füllt die bereits im Server-1.0-Basisschema vorhandene Tabelle `tb.system_metrics_hourly` mit stündlich aggregierten Request-/5xx-/Byte-Zählern. Request-Inhalte, Tokens und personenbezogene Payloads werden **nicht** gespeichert; Healthchecks werden von der Verkehrsmessung ausgenommen.\n- `/api/v1/admin/system/status` liefert zusätzlich `traffic_24h`, eine lückenfreie 24-Stunden-Serie, Prozessstart/Laufzeit, öffentliche Serveradresse, letzten erfolgreichen Backupstand und bewertete Issues.\n- Die Admin-Basisnavigation wurde modernisiert und trennt Übersicht, Benutzer, Firmen, Geräte, Baustellen, App-Versionen und Systemstatus klar. Bestehende Fachseiten verwenden weiter dieselben Server-1.0-Verträge.\n- Neuer Source-Check `86_verify_tb_server_1_admin_overview_source.py`; lokaler Marker-/Vertragscheck gegen den aktuellen GitHub-Inhalt war vollständig erfolgreich.\n- Die CI-Migrationserwartung wurde auf Migration **010** korrigiert und `system_metrics_hourly` in die verpflichtenden Kerntabellen aufgenommen.\n\n### Sicheres Benutzer-Löschen Server 1.0 2026-09-19\n\n- Der bisher fehlende Admin-Pfad `DELETE /api/v1/admin/users/{user_id}` ist implementiert.\n- Löschen ist ein Soft-Delete: Portal-Sessions/-Aktivierung, offene Geräteaktivierungen und aktive Geräte-Tokens werden ungültig; Geräte werden freigegeben, aktive Zuordnungen beendet, historische Fachreferenzen bleiben erhalten.\n- Administratoren und der eigene Admin-Benutzer können über diesen Pfad nicht gelöscht werden. Ein Teamleiter mit aktivem BTB muss den aktiven BTB zuerst abschließen/lösen.\n- Migration `010_user_soft_delete_login.sql` macht den normalisierten Login nur für nicht gelöschte Benutzer eindeutig, sodass ein Login nach sauberem Soft-Delete später wieder verwendet werden kann.\n- Die Benutzerseite besitzt dafür wieder eine sichtbare rote Aktion **Löschen** mit Sicherheitsabfrage.\n\n### Alte Admin-Bedienlogik als verbindliche Server-1.0-Referenz übernommen 2026-09-19

- Referenzscreenshots des bestehenden Admin 0.7 bestätigen die bewährten Bereiche: Übersicht, Strecken, Bahnhöfe, Benutzer, Firmen, Geräte, Dateien, Baustellen, App-Versionen, Papierkorb und Systemstatus.
- Die Projektregeln schreiben diese Admin-Bedienlogik jetzt ausdrücklich als Referenz fest. Server 1.0 darf technisch moderner sein, soll diese Fachabläufe aber nicht ohne Grund neu erfinden.
- Benutzer und Geräte werden in Server 1.0 wieder als getrennte Admin-Bereiche geführt. Die vorübergehende kombinierte Seite `Nutzer/Geräte` ist nur Entwicklungszwischenstand und nicht Ziel-UX.
- Die Benutzerseite verwendet wieder die Aktion **Aktivierungskarte**. Dafür gibt es jetzt eine dedizierte Seite `/admin/v1/users/{user_id}/activation-card`, optisch und fachlich am bestehenden Ablauf orientiert.
- Die Aktivierungskarte zeigt Benutzer, Firma, Zugriff, Klartext-Key, QR, Hinweise zur ersten Gerätebindung und bietet **Drucken / als PDF speichern**, direkten PDF-Download und **PDF teilen / versenden**.
- Erneutes Öffnen/Erzeugen der Aktivierungskarte widerruft nur vorherige noch unbenutzte Aktivierungs-Keys. Ein bereits gebundenes Gerät bleibt aktiv, bis es im getrennten Gerätebereich ausdrücklich **Freigeben** wird.
- Die Geräteverwaltung ist jetzt wieder eine eigene Seite `/admin/v1/devices` mit Benutzer, Firma, Geräte-ID, Status, letztem Kontakt und Freigabe-Aktion.
- Aktivierungs-Keys verwenden wieder ein gut lesbares `TBH-...`-Format statt eines technisch zufälligen URL-safe Tokens. Serverseitig bleibt ausschließlich der Hash gespeichert.

### Aktivierungs-QR ist jetzt selbstbeschreibend 2026-09-19

- Nach der Umstellung wurde vor dem Gerätebuild noch eine veraltete `teamApi`-Referenz in `AktivierungActivity` gefunden und behoben; der QR-Aktivierungsweg verwendet jetzt durchgängig neu erzeugte `TeamManagementApi`-Clients mit der nach erfolgreicher Aktivierung gespeicherten Serveridentität.
- Source-Check 84 schlägt jetzt zusätzlich fehl, falls diese veraltete Referenz wieder auftaucht.

- Die zwischenzeitliche Android-Staginglösung über `local.properties` wurde auf den vorgesehenen Gerätefluss zurückgeführt: `local.properties` bleibt nur Entwickler-Fallback und ist keine Benutzerkonfiguration.
- Der Geräte-Aktivierungs-QR/Deep-Link enthält jetzt **öffentliche HTTPS-Serveradresse + Einmal-Schlüssel**. Für das aktuelle Staging ist die Serveridentität `https://api-v1.tb-helfer.de`.
- Server 1.0 erhält die kanonische Adresse aus der lokalen Synology-Variable `TB_V1_PUBLIC_BASE_URL`; ohne konfigurierte öffentliche URL wird keine neue Aktivierungskarte ausgegeben.
- Android validiert die QR-Serveradresse auf HTTPS, aktiviert genau gegen diesen Server und speichert die Serveridentität **erst nach erfolgreicher Gerätebindung** lokal.
- `ServerConfig.BASE_URL` liest danach die gespeicherte Serveridentität; neu erzeugte Sync-/Fachclients verwenden damit automatisch denselben NAS-/Cloudflare-Server. BuildConfig bleibt nur Fallback.
- Eine erneute erfolgreiche Geräteaktivierung kann die gespeicherte Serveridentität bewusst ersetzen; ein ungültiger/manipulierter QR ändert sie nicht.
- Die Projektregeln dokumentieren diesen Ablauf jetzt verbindlich, damit Serveridentität/QR/PDF nicht wieder als Build- oder Chatwissen verloren gehen.

### Aktivierungskarte PDF + externer Cloudflare-Stagingpfad 2026-09-19

- Die Geräteaktivierung erzeugt jetzt zusätzlich zur QR-/Deep-Link-Antwort eine **nicht persistent gespeicherte PDF-Aktivierungskarte** mit Benutzer, Firma, Ablaufzeit, QR und Fallback-Schlüssel.
- Die Nutzer-/Geräte-Seite bietet `PDF herunterladen` und `PDF teilen / versenden`; wenn Web Share mit Dateien nicht verfügbar ist, fällt sie auf Download zum manuellen Versenden zurück.
- Aktivierungsantworten setzen `Cache-Control: no-store`; serverseitig bleibt nur der Hash des Einmal-Schlüssels gespeichert.
- Für Tests außerhalb des Heimnetzes ist der separate Cloudflare-Staginghostname **`api-v1.tb-helfer.de`** jetzt praktisch aktiv. Er läuft über den bestehenden gesunden Tunnel `tb-helfer-nas` auf `http://192.168.178.3:8081`; `GET https://api-v1.tb-helfer.de/health/ready` wurde erfolgreich mit `status=ok`, `database=up`, `migrations=ok`, `server_version=1.0.0-dev` geprüft.
- Der bestehende Produktivhostname `api.tb-helfer.de` und der alte 0.7-Server bleiben ausdrücklich unverändert; dies ist **kein produktiver Cutover**.
- Der externe Health-Test ist bestätigt. Für die normale Geräteaktivierung muss Android **nicht** mehr per `local.properties` auf Staging umgestellt werden; die Aktivierungskarte liefert `https://api-v1.tb-helfer.de` selbst. `local.properties` bleibt nur Entwickler-Fallback.

### Geräteaktivierung per QR im Server-1.0-Portal 2026-09-19

- Der bestehende Android-Aktivierungsweg war bereits QR-/Deep-Link-fähig (`tbhelfer://activate?key=...`), die neue Server-1.0-Nutzer-/Geräteseite hatte zunächst jedoch nur Aktivierungsschlüssel und URI als Text angezeigt.
- Die Server-1.0-Geräteaktivierung liefert jetzt zusätzlich einen einmaligen SVG-QR als Data-URL. Der QR enthält ausschließlich dieselbe einmalige Aktivierungs-URI; der Schlüssel wird serverseitig weiterhin nur gehasht gespeichert.
- Die Portal-Seite **Nutzer/Geräte** zeigt nach **Gerät aktivieren** eine Aktivierungskarte mit QR-Code, Ablaufzeit und Schlüssel nur als Fallback/Kopiermöglichkeit.
- Android scannt den QR weiterhin über den vorhandenen Aktivierungs-Scanner und verarbeitet denselben `tbhelfer://activate`-Deep-Link. Kein zweiter Aktivierungsweg wurde eingeführt.
- Neuer Source-Check `84_verify_device_activation_qr_source.py` prüft Server -> Portal -> Android durchgängig.

### NAS-Retest nach 30-Tage-Aktivierungsfix 2026-09-20

- Synology-Systemstatus praktisch bestätigt: Migrationen **10 / 10**, latest **10 / 10**, pending **keine**. Migration 010 ist damit angewendet.
- `devices.py` wurde auf dem NAS aktualisiert und der API-Container anschließend neu gestartet.
- Gerätebindung funktioniert trotzdem noch nicht; Bestandsanzeige bleibt bei **0 aktiven Geräten**. Damit ist der Fehler nicht mehr durch fehlende Migration 010 oder einen nicht neu gestarteten API-Prozess erklärt.
- Nächster zwingender Diagnosepunkt ist der konkrete Aktivierungsrequest vom Android-Gerät: Im API-Protokoll prüfen, ob beim Scan ein `POST /api/v1/device/login` auf **Server 1.0** ankommt. Kein Request bedeutet falscher/alter Android-Serverpfad bzw. alter APK-Stand; ein 401 auf Server 1.0 muss anschließend gegen den neuesten Datensatz in `tb.device_activation_tokens` (used/revoked/expires/token_hint) geprüft werden.

### Geräteaktivierung auf altes bewährtes Verhalten zurückgeführt 2026-09-20

- Fachliche Klarstellung aus dem Praxistest: Die bewährte 0.7-Logik bleibt maßgeblich. Ein Geräte-Aktivierungs-QR ist **30 Tage gültig** und **nur einmal verwendbar**.
- Das bloße erneute Öffnen/Erzeugen einer Aktivierungskarte darf einen bereits ausgegebenen, noch unbenutzten 30-Tage-Key nicht ungültig machen. Das bisherige Server-1.0-Verhalten (neue Karte widerruft sofort den vorherigen offenen Key) war fachlich falsch und konnte dazu führen, dass eine zuvor versendete Karte auf dem Handy sofort als ungültig erschien.
- Server 1.0 wurde korrigiert: neue Keys laufen standardmäßig 720 Stunden; mehrere vor der ersten Bindung ausgegebene offene Keys dürfen bis dahin bestehen. Bei der **ersten erfolgreichen Gerätebindung** wird der verwendete Key verbraucht und alle übrigen noch offenen Aktivierungs-Keys desselben Benutzers werden widerrufen.
- Benutzer löschen/deaktivieren widerruft offene Keys weiterhin sofort. Nach Gerätefreigabe ist eine neue Aktivierungskarte nötig.
- Für `Benutzer löschen -> denselben Login neu anlegen` bleibt Migration 010 zwingend: der Synology-Systemstatus muss nach Aktualisierung **10/10 Migrationen** zeigen. Bei 9/9 ist der alte globale Login-Unique-Index noch aktiv und der gleiche Login kann nicht sauber wiederverwendet werden.
- Praktischer Retest offen: aktuellen Branch auf Synology übernehmen, Migrationen anwenden/10 von 10 prüfen, API neu starten, Benutzer löschen -> gleicher Login neu -> Aktivierungskarte erzeugen -> QR sofort und später nochmals prüfen -> erfolgreiche Bindung -> derselbe QR muss danach ungültig sein.

### Android 200051 – QR-Zielserver sichtbar und Debug auf Server 1.0 vorbereitet 2026-09-20

- Android wurde auf **Version 1.3.1 / versionCode 200051** angehoben, damit beim Gerät eindeutig erkennbar ist, ob wirklich der neue Build installiert ist.
- Der Debug-Fallback ohne lokale Vorgabe zeigt im Feature-Branch jetzt auf `https://api-v1.tb-helfer.de`; `local.properties`/Gradle-Property dürfen Debug weiterhin gezielt überschreiben. Der Release-Fallback bleibt bis zum produktiven Cutover bewusst `https://api.tb-helfer.de`.
- Beim QR-Scan bleibt die Fachregel unverändert: `credential.serverBaseUrl` aus dem QR hat Vorrang vor `ServerConfig.BASE_URL` und wird direkt an `ActivationApi(activationBaseUrl)` übergeben.
- Die Aktivierungsseite zeigt beim Verbindungsversuch jetzt den tatsächlich verwendeten Zielhost, z. B. **„Verbinde mit api-v1.tb-helfer.de …“**. Wird der Key abgelehnt, nennt auch die Fehlermeldung den antwortenden Host. Damit ist ein versehentlicher Login gegen den alten Server sofort sichtbar.
- Source-Check 83 sichert Android 200051 + Server-1.0-Debugfallback; Source-Check 84 prüft jetzt zusätzlich explizit, dass der QR-Server wirklich an `ActivationApi` übergeben wird.
- Manuelle GitHub-Querprüfung des Quellstands: Version 200051, QR-Server-Vorrang, tatsächlicher `ActivationApi`-Zielserver, sichtbarer Zielhost und aktualisierte Source-Checks sind vorhanden.
- GitHub Actions **TB-Server #504 / ID 35493733756** und **Android #1141 / ID 35493733801** endeten erneut ohne ausgeführte Steps/Logs (`steps = null`, `logs_url = null`). Das bleibt das bekannte Runnerproblem und ist kein festgestellter Code-/Buildfehler.

### Praktischer E2E-Stand Benutzer/Geräteaktivierung 2026-09-20

- NAS-/Admin-Retest nach dem Admin-Neuaufbau ist praktisch bestätigt: Benutzer löschen, aus der aktiven Liste entfernen, denselben Login erneut anlegen, Aktivierungskarte erzeugen, QR anzeigen sowie Migration 010 mit **10/10 / pending keine** funktionieren.
- Aktivierungskarte/QR/PDF auf Server 1.0 funktionieren serverseitig. Geräte-Aktivierungs-Keys gelten fachlich **30 Tage** und **einmalig**; bloßes erneutes Erzeugen einer Karte widerruft andere offene Keys nicht.
- Der verbleibende Fehler liegt aktuell auf der installierten Android-Seite: Beim QR-Aktivierungsversuch erscheint `Aktivierungs-Key ungültig`, während das Log des alten 0.7-Servers zeitgleich `POST /api/v1/device/login -> 401` zeigt. Im neuen Server-1.0-Log fehlt der passende Geräte-Login.
- Damit ist die Ursache praktisch eingegrenzt: Die derzeit installierte APK sendet den Geräte-Login noch an den alten Server. Der neue Server-1.0-Key ist dort unbekannt. **Kein weiterer Umbau an `devices.py` vor dem Test eines aktuellen Android-Builds.**
- Nächster Android-Erfolgstest: aktueller Build aus `feature/tb-server-1.0` auf Testgerät installieren, frischen Server-1.0-QR scannen; im Log von `tb-helfer-v1-api-1` muss `POST /api/v1/device/login` mit 200 erscheinen, im alten `tb-helfer-api` für denselben Versuch kein Geräte-Login.

### Admin-API komplett neu aufgebaut und quergeprüft 2026-09-19

- `server/tbserver/admin.py` wurde im Feature-Branch vollständig neu aufgebaut statt weiter punktuell geflickt.
- Admin-Sessionzugriff verwendet durchgängig `admin["user_id"]`; der beobachtete Benutzer-Löschfehler `KeyError: 'id'` ist damit beseitigt.
- Schreibende Admin-Pfade bleiben CSRF-geschützt. Firmenanlage, Benutzeranlage, Portal-Aktivierung und Benutzer-Soft-Delete schreiben jetzt Audit-Einträge ohne Geheimwerte.
- Benutzer-Soft-Delete widerruft Browser-Sessions, Portalzugang, offene Geräteaktivierungen und Gerätezugänge; `auth_token_hash` und `auth_token_hint` werden entfernt, Geräte auf `REVOKED` gesetzt und laufende Benutzer-/Teamzuordnungen beendet. Offene Baustelleneinladungen an den Benutzer **und von ihm erzeugte** offene Einladungen werden widerrufen.
- Benutzer mit aktivem BTB als Teamleitung bleiben vor Löschung geschützt (`USER_HAS_ACTIVE_BTB`). Admin-Benutzer und der eigene Admin-Account können über diesen Pfad nicht gelöscht werden.
- Portal-Aktivierung wird für bereits aktivierte Benutzer nicht nochmals als unbrauchbarer Token ausgegeben (`PORTAL_ALREADY_ACTIVATED`); die Benutzeroberfläche blendet den Aktivierungsbutton dann aus.
- Querprüfung zu Migration 010 ergab einen notwendigen Login-Fix: `portal.py` filtert die Loginname-Suche jetzt ebenfalls auf `u.deleted_at IS NULL`, damit ein nach Soft-Delete wiederverwendeter Login den neuen aktiven Benutzer erreicht.
- Source-Check 85 wurde erweitert: Python-AST-Parsing plus Querchecks für Admin-Session-Key, Audit, Token-Widerruf, Login-Wiederverwendung und Benutzeroberfläche.
- Manuelle GitHub-Querprüfung gegen Basisschema, Migration 002, Migration 010, `portal.py`, `devices.py` und `v1_admin_users.html`: **25/25 Prüfmarker erfüllt, 0 fehlend**.
- Praktischer NAS-Retest nach Pull/Übernahme und API-Neustart: Benutzer löschen, aus Liste entfernen, denselben Login neu anlegen und Aktivierungskarte erzeugen sind bestätigt. Offen bleibt ausschließlich die Android-Gerätebindung gegen den richtigen Server-1.0-Endpunkt.

- Nach dem Admin-Neuaufbau endeten die aktuellen GitHub-Actions-Läufe **TB-Server 1.0 #482 / ID 35453481227** und **Android CI #1124 / ID 35453481198** erneut ohne ausgeführte Schritte (`steps = null`, `logs_url = null`). Das ist weiterhin das bekannte Runner-/Actions-Infrastrukturproblem; aus diesen Läufen lässt sich kein fachlicher Build-/Testfehler ableiten.

### Praktischer NAS-Retest Admin-Dashboard / Aktivierungs-Lock 2026-09-19

- Die neue Server-1.0-Übersicht läuft praktisch auf der Synology und zeigt reale Werte: 24-h-Requests/5xx, Datenverkehrsdiagramm, Prozessstart/Laufzeit, NAS-Speicher, Bestandszahlen, Probleme/Warnungen und letzte Aktivität.
- Der API-Container-Dateistand wurde direkt geprüft: `/app/tbserver/devices.py` enthält beim Aktivierungs-SELECT `FOR UPDATE OF u`.
- Der danach exportierte API-Log enthält **keinen neuen Geräteaktivierungsversuch nach 17:27:27**; alle protokollierten Aktivierungs-500er stammen noch vom alten `FOR UPDATE`-Fehler. Die Dateiprüfung im laufenden Container beweist daher nicht, dass der bereits gestartete Uvicorn-Prozess die geänderte Python-Datei neu geladen hat. Vor dem nächsten Aktivierungstest muss die API einmal neu gestartet werden; danach frischen Log prüfen.
- Derselbe Log hat zusätzlich einen unabhängigen Benutzer-Löschfehler gezeigt: `delete_user()` griff auf `admin["id"]` zu, die Portalsession liefert aber `user_id`. Behoben auf `admin["user_id"]`; Source-Check 85 schützt den Schlüssel jetzt explizit.

### Geräteaktivierung: PostgreSQL-Lock-Fix 2026-09-19

- Der praktische Synology-Test der Server-1.0-Aktivierungskarte erreichte den API-Endpunkt, scheiterte aber mit HTTP 500.
- Ursache laut API-Protokoll: `psycopg.errors.FeatureNotSupported: FOR UPDATE cannot be applied to the nullable side of an outer join` in `issue_device_activation()`. Die Abfrage verband `tb.users` per `LEFT JOIN` mit `tb.companies` und verwendete ein unqualifiziertes `FOR UPDATE`.
- Korrektur: `FOR UPDATE OF u` sperrt ausschließlich den für die Aktivierung maßgeblichen Benutzer-Datensatz; die optionale Firmenzeile wird nicht gelockt.
- Der bestehende Source-Check `84_verify_device_activation_qr_source.py` sichert den qualifizierten Lock jetzt zusätzlich ab.
- Auf der Synology wurde separat bestätigt, dass `qrcode` und `reportlab` im API-Container importierbar sind (`QR/PDF OK`). Ein fehlendes QR-/PDF-Paket war nicht die Ursache.
- Praktischer Retest nach Übernahme des aktuellen Branch-Standes auf die Synology ist noch offen: Aktivierungskarte muss Key, QR und PDF ohne HTTP 500 erzeugen.

### Android-Debug gegen Synology-Staging 2026-09-19

- Der praktische Geräte-E2E wäre sonst gegen den falschen Server gelaufen: `ServerConfig.BASE_URL` war weiterhin fest auf `https://api.tb-helfer.de` gesetzt.
- Android liest den Serverendpunkt jetzt über `BuildConfig.SERVER_BASE_URL`.
- Für **Debug** kann der Endpunkt lokal aus `local.properties` (`tb.server.base.url`), alternativ Gradle-Property `tbServerBaseUrl` oder `TB_SERVER_BASE_URL`, gesetzt werden.
- Der **Release-Build** bleibt unabhängig von lokalen Debugwerten fest auf `https://api.tb-helfer.de`.
- Klartext-HTTP wird nur im Debug-Build und nur bei expliziter `http://`-Debugadresse über einen Manifest-Placeholder erlaubt; Release setzt `usesCleartextTraffic=false`.
- `local.properties` ist bereits gitignoriert und bleibt lokale Maschinenkonfiguration.
- Neuer Source-Check `83_verify_android_staging_server_config_source.py` prüft diese Trennung; TB-Server- und Android-CI referenzieren ihn.
- Für den aktuellen Admin-Übersicht-/Metrik-Stand am Head `5fa07366f60c365446d7cf6e82763d4607980282` endeten TB-Server **#462 / ID 35446883603** und Android CI **#1113 / ID 35446883605** erneut ohne ausgeführte Schritte. Beide Jobs liefern `steps = null`, `logs_url = null`; damit wurde weiterhin kein Workflow-Schritt, Source-Check, Unit-Test oder Android-Build gestartet. Das ist das bekannte Runner-/Actions-Infrastrukturproblem und kein festgestellter fachlicher Server-/Android-Testfehler.

### BTB-Granularitätskorrektur

Der kurzzeitig eingeführte Android-Gesamt-Bridge-Eintrag `TB_HELFER_ANDROID_V1` bleibt ausschließlich Migrationsquelle. Neue Server-1.0-Schreibvorgänge zerlegen den BTB in einzelne versionierte Entries; vorhandene Bridge-Einträge werden automatisch zur Migration eingereiht und nach erfolgreicher Granularisierung gelöscht. Per-Entry-Basiszustände liegen nur im lokalen Sync-State und ermöglichen das Zusammenführen unabhängiger Eintragsänderungen.\n\n### Android-BTB-Abschluss-PDF-Übernahme

- `btb_finalize_with_pdf_v1` steuert den neuen Android-PDF-Pfad.
- Finalisierte Server-BTBs tragen ihren echten Serverstatus sowie PDF-Verfügbarkeit/SHA nur als technische Sync-Metadaten.
- Android lädt `GET /api/v1/btbs/{btb_id}/final-pdf`, prüft PDF-Magic und bekannte SHA-256 und schreibt atomar in den vorhandenen `BtbSpeicher`-Ordner.
- Der vorhandene Verlauf/Viewer öffnet damit dasselbe aktuelle PDF, ohne zweite Ablagelogik.
- Ein Server-PDF erzeugt bewusst **keine** lokale Original-Baseline. Das unveränderliche erste Original bleibt serverseitig maßgeblich.
- Bei echtem Server-`ACTIVE/REWORK` wird nur ein zuvor gesetzter Server-Abschlussmarker invalidiert; bloßes Reaktivieren eines weiterhin `FINALIZED`-BTBs behält das gültige PDF.

### Android-BTB-Löschung auf Server 1.0

- Der bestehende `BtbDeleteSyncCoordinator` bleibt alleinige Offline-/Benutzer-Löschlogik.
- Moderne DELETE-Outbox-Einträge werden über `DELETE /api/v1/btbs/{btb_id}` mit derselben `X-Client-Change-Id`-Idempotenz übertragen.
- Vor dem DELETE werden Teamzuordnung und Basisversion gegen den aktuellen Server-BTB geprüft; Versionskonflikte werden über den vorhandenen Rebase des ausdrücklichen Benutzer-Löschwunsches behandelt.
- Bestätigte Löschungen tombstonen den lokalen Sync-Cache sofort.
- Die Reconciliation läuft vor dem normalen BTB-Import und verhindert dadurch ein Wiederauftauchen eines lokal bewusst gelöschten Team-BTBs.

### Gemeinsamer Android-Server-1.0-Synckern

- `Server1AndroidSyncCoordinator` enthält jetzt den vorhandenen modernen Kontext-, Mängel-/Abnahme-, BTB-, Datei-, Foto-, PDF- und Löschablauf genau einmal.
- Baustellen-Sync-UI und `SyncWorker` verwenden denselben Coordinator.
- BTB-, Fach- und Datei-Capabilities werden unabhängig ausgewertet; BTB-Sync hängt nicht mehr davon ab, dass zusätzlich Mängel oder Abnahmen aktiviert sind.
- Der Hintergrund-Worker versucht Server 1.0 vor `supportsIncrementalSync`/`supportsCollabSync`. Diese Versionsprüfungen entscheiden nur noch über den alten Legacy-Fallback.
- Der bestehende Offline-/Vermessungs-Sync bleibt im Worker erhalten und wird bei kompatiblem Legacy-Katalog weiterhin ausgeführt.

### Server-1.0-Dateipagination

- `GET /api/v1/worksites/{worksite_id}/files` unterstützt jetzt einen opaken Cursor über die stabile Sortierung `(created_at DESC, id DESC)`.
- Sichtbarkeitsfilterung wird beim serverseitigen Scannen berücksichtigt; eine kurze sichtbare Seite bedeutet daher nicht mehr fälschlich „Ende“.
- Neue Antworten liefern `next_cursor` und `snapshot_complete`.
- Android folgt allen Cursor-Seiten und tombstoned alte Cache-Einträge erst nach explizit vollständigem Snapshot.
- Bei einem alten Server ohne Cursorfelder bleibt die bisherige 1000er-Grenze als sicherer Rolling-Update-Fallback aktiv.
- Capability `file_cursor_pagination_v1` dokumentiert den neuen Vertrag.

### Android-Baustellenbaum auf Server 1.0

- Die vorhandene `WorksiteStructureSync`-Fach-/Merge-Logik bleibt bestehen; ersetzt wurde nur der alte Collaboration-Transport.
- Android liest `GET /api/v1/worksites/{id}/nodes?include_deleted=true` und verwendet `external_key` bzw. einen stabil aus der Server-UUID abgeleiteten Fallback als geräteübergreifenden Client-Key.
- Der Snapshot enthält aktive Knoten und Tombstones, Parent-Client-Key und aktuelle Version.
- Create/PATCH/DELETE laufen idempotent über `X-Client-Change-Id`; PATCH und DELETE prüfen `base_version` und liefern bei Konkurrenz `VERSION_CONFLICT`.
- Das Löschen eines Elternknotens tombstoned serverseitig rekursiv den kompletten Unterbaum.
- Bei Versionskonflikten wird die ausdrückliche lokale LEAD-Änderung höchstens drei Runden gegen den frischen Server-Snapshot rebased; erst ein konfliktfreier Stand wird lokal angewendet.
- `Server1AndroidSyncCoordinator` führt Prepare -> Outbox-Upload -> autoritativen Snapshot capability-gesteuert über `worksite_tree_v1` aus.
- Der Legacy-Worker behält seinen alten Strukturpfad nur für den dokumentierten alten Collaboration-Fallback.

### Android-Streckendaten auf `track_data_v1`

- `WorksiteTrackDataSync` bleibt die fachliche LEAD/Helfer-Logik und synchronisiert weiterhin ausschließlich bereits geparste/berechnete mobile Datensätze.
- `WorksiteTrackDataApi` nutzt auf Server 1.0 jetzt `GET/POST /api/v1/worksites/{id}/track-data` sowie versioniertes `PATCH/DELETE /api/v1/track-data/{id}`.
- Der gewünschte vollständige LEAD-Snapshot wird clientseitig gegen die kanonischen Datensätze reconciled; unveränderte Datensätze erzeugen keine Mutation.
- Mutationen verwenden deterministische `X-Client-Change-Id`-Werte; Versionskonflikte werden mit frischem Snapshot höchstens drei Runden neu abgeglichen.
- Ein alter 0.7.x-Server ohne kanonischen Endpunkt fällt explizit auf den bisherigen Bulk-`/api/v1/collab/.../track-data`-Vertrag zurück.
- TRA/GRA/KF/KOR-Originaldateien werden von diesem Android-Pfad weiterhin niemals hochgeladen oder verändert.
- `Server1AndroidSyncCoordinator` führt `track_data_v1` jetzt auch beim manuellen Sync aus. `Schema13Worker` überspringt diesen Teil auf Server 1.0 und behält ihn nur für den Legacy-0.7-Fallback; damit verwenden manueller und Hintergrund-Sync denselben `track_data_v1`-Pfad ohne parallele Doppeluploads.

### Server-1.0-Baustellenanlage und -löschung

- Android legt neue Server-1.0-Baustellen jetzt idempotent über `POST /api/v1/worksites` mit stabilem `X-Client-Change-Id` an; der alte `/collab/worksites`-Pfad bleibt nur 0.7.x-Fallback.
- Server 1.0 besitzt jetzt `DELETE /api/v1/worksites/{id}?base_version=...`; nur die Baustellenrolle `RESPONSIBLE_TB` darf diese operative Löschung ausführen.
- Die Löschung ist ein Soft-Delete (`TRASHED` + `deleted_at` + Versionssprung), kein physisches Löschen von Fachdaten oder Vermessungsoriginalen.
- Der bestehende generische `tb.deletion_tombstones`-Mechanismus trägt `WORKSITE`-Tombstones; `GET /api/v1/worksites/deletions` verteilt sie firmenweit an berechtigte Geräte.
- Android prüft vor dem Delete die aktuelle Serverversion, rebased bei Konkurrenz höchstens drei Runden und löscht lokal weiterhin erst nach bestätigtem Servererfolg.
- `Schema13Worker` kann denselben `WorksiteDeletionApi` weiterverwenden; auf Server 1.0 nimmt dieser automatisch den kanonischen Tombstone-Feed, auf 0.7.x den alten Fallback.

### Server-1.0-Gesamtmedienkatalog

- `GET /api/v1/worksites/{id}/media` erzeugt die Bilderliste direkt aus `tb.files`, `tb.file_links` und den kanonischen BTB-/Mangel-/Abnahme-Objekten; keine zweite Medien-Tabelle wurde eingeführt.
- Der Katalog enthält ausschließlich Bilddateien aus BTB, Mängeln und Abnahmen – identisch zur bisherigen fachlichen Gesamtbilderlogik.
- `RESPONSIBLE_TB`, CHEF und ADMIN dürfen BTB-Bilder anderer Teams derselben Baustelle sehen und herunterladen; fremde BTB-Texte bleiben verborgen (`parent_payload={}`).
- Normale TB-/Helfer-Benutzer sehen BTB-Bilder nur ihrer Teams. Mangel-/Abnahmebilder bleiben zusätzlich durch bestehende Fachrechte und `area_scope` begrenzt.
- Die Cross-Team-Ausnahme ist ausschließlich beim Bilddownload opt-in; normale Datei-Liste und Datei-Metadaten erhalten dadurch keine erweiterten Fremdteam-Rechte.
- Android `WorksiteMediaApi` nutzt kanonischen Medienkatalog + `GET /api/v1/files/{id}/content`; alte 0.7.x-Server bleiben über den bisherigen `/collab`-Katalog kompatibel.
- Vor einem Online-Gesamtexport läuft auf Server 1.0 zuerst der gemeinsame `Server1AndroidSyncCoordinator`; nur bei echtem Legacy-Server folgt der alte Collaboration-Sync.

### Portal-/Android-Baustellenbaum vereinheitlicht

- Das Portal erzeugt keine alten freien Typwerte `SECTION/TRACK/SWITCH/STATION/OTHER` mehr.
- Portal und Android verwenden gemeinsam `FREIE_STRECKE`, `BAHNHOF` und `ALLGEMEIN_OBJEKT`.
- Parent-Knoten können im Portal gewählt werden; dadurch lässt sich derselbe echte Unterbaustellenbaum wie auf Android pflegen.
- Strecke, km von/bis, Bahnhof und Gleis/Weiche landen im bereits kanonischen Android-Payload (`route_key`, `km_from`, `km_to`, `station_name`, `track_or_switch`).
- Portal-PATCH/DELETE senden die geladene Knoten-Version als `base_version`; konkurrierende Änderungen werden dadurch nicht still überschrieben.
- Das Löschen weist im Portal jetzt ausdrücklich darauf hin, dass der Server den kompletten Unterbaum tombstoned.

### Portal-Fachformulare für Mängel und Abnahmen

- Mängel werden im Portal jetzt über Beschreibung, Bereich, km/Lage, Firma, Status, Strukturelement und Kommentar bearbeitet.
- Abnahmen verwenden dieselben Android-Felder für Bezeichnung, Objekt, Firmen, Abnehmenden, Anwesende, km von/bis, Kommentar, Restpunkte und Zwischenergebnis.
- Bearbeiten erfolgt im jeweiligen Formular und sendet die geladene `base_version`; die bisherigen vollständigen Roh-JSON-`prompt()`-Dialoge entfallen.
- Unbekannte oder spätere Zusatzfelder bleiben erhalten: ein aufklappbarer JSON-Expertenbereich wird additiv mit den Fachfeldern gemerged.
- Portal-Titel werden aus `beschreibung` (Mangel) bzw. `bezeichnung/objekt` (Abnahme) abgeleitet – identisch zur bestehenden Android-Server-1.0-Abbildung.

### Portal-Fotos für Mängel und Abnahmen

- Der vorhandene `worksite_media_v1`-Katalog liefert Mangel-/Abnahmebilder direkt in die jeweiligen Portal-Karten; kein zweiter Medienendpunkt wurde eingeführt.
- Neue Fotos werden über denselben resumierbaren `POST /uploads -> PUT /chunk -> POST /complete`-Vertrag wie BTB-Fotos übertragen.
- Portal-Mangelfotos tragen `DEFECT__MANGEL__...`, Abnahmefotos `ACCEPTANCE_FOTO__SONSTIGES__...`; Androids vorhandene Importlogik erkennt diese Präfixe bereits.
- Sichtbarkeit und Uploadberechtigung bleiben vollständig beim bestehenden Server-Rechte-/Area-Scope-Vertrag.
- Abnahmen ohne Strukturelement speichern keinen künstlichen `—`-Knotennamen mehr; Portal-Bearbeitung zwingt den Abnahmestatus außerdem nicht mehr unnötig auf `IN_PROGRESS`.

### Mangelstatus ABGELEHNT / REJECTED

- Androids bestehender Status `ABGELEHNT` besitzt jetzt das kanonische Server-1.0-Gegenstück `REJECTED`.
- Fresh-Install-Baseline und Migration `009_defect_rejected_status.sql` erlauben `REJECTED` in `tb.defects.status`.
- Der Status bleibt – wie in Android – normal bearbeitbar und ist **nicht** mit dem endgültigen `ERLEDIGT/CLOSED` gleichgesetzt.
- Android schreibt `ABGELEHNT -> REJECTED` und liest `REJECTED -> ABGELEHNT` zurück.
- Medienkatalog und Portal zeigen denselben Status; das Portal kann einen offenen Mangel direkt auf „Abgelehnt“ setzen.
- Damit entfällt der bisherige explizite Server-1.0-Syncfehler für abgelehnte Mängel.

### Android-Abnahme-Löschung auf Server 1.0

- Der vorhandene Android-Löschbutton erzeugt vor dem lokalen Entfernen einen dauerhaften `AbnahmeDeleteIntentStore`-Eintrag.
- Ein Server-DELETE wird **nur** aus diesem ausdrücklichen Benutzerwunsch abgeleitet; eine neu vom anderen Gerät kommende, lokal noch fehlende Abnahme kann dadurch niemals versehentlich gelöscht werden.
- Nach aktuellem Server-Snapshot wird der Intent mit Server-ID und `base_version` in die bestehende Outbox überführt.
- Server 1.0 bietet capability-gesteuert `acceptance_delete_v1` und `DELETE /api/v1/acceptances/{id}?base_version=...`.
- Normale Abnahmen erfordern `acceptance.edit`; für bereits endgültig abgeschlossene Abnahmen ist zusätzlich `acceptance.finalize` nötig, passend zum stärkeren Eingriff.
- Die Serverlöschung ist ein Soft-Delete mit `DELETED`, Versionssprung, Change-Feed, Audit und `ACCEPTANCE`-Tombstone. Zugeordnete Dateien/Fotos werden nicht physisch vernichtet.
- Erst nach bestätigtem Server-DELETE werden Outbox und lokaler Lösch-Intent entfernt.
- Fremde Server-Tombstones werden ebenfalls lokal angewendet: löscht Pixel eine Abnahme, entfernt Poco sie beim nächsten vollständigen Sync. Der lokale Server-Tombstone-Pfad erzeugt ausdrücklich keinen neuen Lösch-Intent.
- Das Portal nutzt denselben versionsgesicherten DELETE. Der Löschbutton wird nur bei `acceptance.edit` angeboten; endgültige Abnahmen zusätzlich nur mit `acceptance.finalize`.

### Fach-DELETE-Abgrenzung

- Abnahmen besitzen in Android einen echten Benutzer-Löschvorgang; deshalb ist der vollständige Server-1.0-DELETE-/Tombstone-Lifecycle umgesetzt.
- Für Mängel existiert im aktuellen Android-Fachablauf **kein** entsprechender Benutzer-Löschvorgang. `Server1FachSyncApi` erzeugt daher bewusst keinen künstlichen Mangel-DELETE.
- Mangelstatus wie `ERLEDIGT` und `ABGELEHNT` werden stattdessen kanonisch als Statusänderungen synchronisiert.
- Mangel-DELETE wird nicht künstlich ergänzt, solange dafür keine dokumentierte Fachfunktion/Benutzeraktion existiert.

### Dauerhafter Projektstatus-Tracker 2026-09-20

- Neuer dauerhaft gepflegter Arbeits-/Abnahmetracker unter `projektstatus/TB-HELFER_STATUS.md`; eine gleichnamige Excel-Datei wird parallel als bearbeitbare Benutzeransicht geführt.
- Tracker trennt App, Server, Probleme, Planung und Erledigt und verwendet stabile IDs sowie `☑/☐`.
- Ein bestätigter Punkt mit `☑` und/oder `Nicht ohne Grund ändern = JA` wird nicht beiläufig neu gebaut. Änderung nur bei reproduzierbarem Fehler, neuer belegter Fachanforderung oder ausdrücklichem Benutzerwunsch.
- Geheimwerte werden ausdrücklich nicht in Tracker/GitHub geschrieben.
- Cloudflare-Tunnel-Token: als NAS-Secret wichtig, solange derselbe Tunnel weiterverwendet wird; nicht nach GitHub übernehmen. Alte Geräte-/Bearer-/Aktivierungs-Tokens des 0.7-Servers werden nicht migriert; Geräte werden auf Server 1.0 neu aktiviert.

### Geräteaktivierung – Ursache praktisch eingegrenzt 2026-09-20

- Das Log des alten 0.7-Servers zeigt bei den aktuellen QR-Aktivierungsversuchen mehrfach `POST /api/v1/device/login` mit `401 Unauthorized`.
- Damit ist bestätigt, dass die derzeit installierte Android-App den Aktivierungsversuch noch an den alten Server sendet. Der neue Server-1.0-Key ist dort unbekannt; deshalb erscheint in der App die Meldung, der Aktivierungs-Key sei ungültig.
- Der nächste Schritt ist **kein weiterer Server-`devices.py`-Umbau**, sondern ein aktueller Android-Build aus `feature/tb-server-1.0`, Installation auf einem Testgerät und erneuter Scan eines frischen Server-1.0-QR.
- Erfolgskriterium: `POST /api/v1/device/login` erscheint im Log von `tb-helfer-v1-api-1` mit 200; im alten `tb-helfer-api` erscheint für denselben Aktivierungsversuch kein `device/login`.
- Erst nach bestätigter Gerätebindung folgen Pixel/Poco-Fach-E2E und danach Backup/Restore/Cutover.

## Bekannte/offene Punkte

- vollständige Import -> Parse -> Survey-Section -> Punktprojektion v3 -> GRA-Sprung v2 -> Kalibrierung gegen eine echte PostgreSQL-/FastAPI-Testinstanz ausführen;
- weitere reale Mehr-TRA-Fälle prüfen, besonders `reverse_geometry`, echte rückläufige Stationierung und nicht überlappende Anschlussstücke;
- einseitig belegte GRA-Sprünge nur bei zusätzlicher realer Evidenz automatisieren; Default bleibt `false`;
- Projektion/verdichtete Samples bei Bedarf performant cachen, ohne Originale zu verändern;
- rohe/unbekannte KOR nur bei belastbar belegtem Format ergänzen;
- Der Android-Server-1.0-Fachcutover ist im Quellstand weitgehend abgeschlossen und muss jetzt praktisch getestet werden: Baustellenkontext/Teams/Einladungen, Unterbaustellenbaum, mobile `track_data_v1`-Arbeitsdaten, Mängel, Abnahmen inklusive expliziter Offline-Löschung/Tombstones, Dateien/Fotos, granularer Team-BTB, BTB-Fotos und serverseitiges Abschluss-PDF laufen capability-gesteuert über kanonische `/api/v1`-Verträge. Dokumentierte 0.7.x-`/collab`-Pfade bleiben ausschließlich Legacy-Fallback. Offen bleibt die praktische Android-/PostgreSQL-E2E-Prüfung;
- BTB-Abschluss-PDF-Lebenszyklus noch gegen echte PostgreSQL-/FastAPI-Testinstanz end-to-end prüfen;
- Android-QR-Scanner/Intent-Handling sowie gezieltes Erzeugen/Einlösen von Server-1.0-Baustelleneinladungen sind im Quellstand umgesetzt; UI und Hintergrund verwenden für den modernen Baustellen-/Fachsync nun denselben capability-gesteuerten Coordinator; die grundlegenden Portal-Fachformulare sind jetzt an die Android-Payloads angeglichen; praktische E2E-Prüfung und weitere Komfortdetails bleiben offen;
- Compose-Staging-Stack läuft praktisch auf der Synology; offen bleiben LAN-/Portal-/Android-E2E sowie Backup-Retention mit echten Alt-Backups und Restore-to-Target gegen getrennte PostgreSQL-/Storage-Ziele, danach separaten NAS-Cutover abnehmen;
- vollständige Integrations-/E2E-Tests und produktiver NAS-Cutover.

## Nächster sinnvoller Arbeitsschritt

1. Die neue Release-Komfortoberfläche aus GitHub auf Server 1.0 deployen: zur Laufzeit erforderlich ist `server/templates/v1_releases.html`; `server/checks/87_verify_release_admin_package_ui.py` ist der passende Source-Check. Da `Dockerfile.v1` die Templates ins Image kopiert, muss der API-Container danach neu gebaut/erstellt werden.
2. Auf Pixel und Poco noch kurz prüfen, dass nach dem Update Geräteaktivierung/Serveradresse und vorhandene lokale Baustellendaten erhalten sind; das ist nur ein Abschlusscheck, keine geplante Codeänderung.
3. Danach **Benutzerdaten bearbeiten + klarer QR-Erneuern-Ablauf** umsetzen.
5. Anschließend Pixel + Poco Server-1.0-Fach-E2E fortsetzen: Einladung/Team, Unterbaustellenbaum, `track_data_v1`, Mangel inkl. `ABGELEHNT`, Abnahme inkl. Löschen/Tombstone, Dateien/Fotos, Team-BTB, BTB-Fotos, Abschluss-PDF und Hintergrund-Sync.
6. Server-Staging vollständig prüfen: Backup -> Retention -> Restore-Test -> Restore-to-Target.
7. Danach Vermessungskette mit realen Daten end-to-end ausführen.
8. **Erst nach bestandenen Android-/Server-/Backup-/Restore-Akzeptanztests** produktiven Cutover durchführen und alten API-0.7-Server kontrolliert stoppen.

**Kein produktiver NAS-Cutover ohne vollständiges, verifiziertes Backup und bestandenen End-to-End-Test.**


### Portal-BTB V7 – getrennte Firmen, editierbares Wetter, Fotos nach Punkten 4–8 (24.09.2026)

- In **3. Personal / Geräte** ist der allgemeine Button **+ Firma** entfernt. Personal und Geräte/Maschinen besitzen jetzt jeweils ihren eigenen **+ Firma hinzufügen**-Pfad.
- Neu im Personalbereich angelegte Firmen sind nur dort sichtbar; neu im Geräte-/Maschinenbereich angelegte Firmen nur dort. Bereits vorhandene gemeinsame Firmen bleiben kompatibel und können weiterhin in beiden Bereichen erscheinen.
- Firmen werden schon direkt nach der Anlage angezeigt, auch wenn noch kein Personal- bzw. Maschineneintrag darunter angelegt wurde.
- **2. Wetter** ist im Portal jetzt über ein normales Fachformular bearbeitbar: Ort/Wetterstation, Zusatzangabe sowie beliebig viele Wetterzeiten mit Uhrzeit, Temperatur, Bewölkung, Niederschlag und Menge. Unbekannte vorhandene Android-Wetterfelder werden beim Speichern nicht absichtlich verworfen.
- Die Fotogalerie ist jetzt fachlich in der Reihenfolge **4. Ausgeführte Arbeiten**, **5. Abnahmen**, **6. Einweisung / Unterweisung**, **7. Besonderheiten**, **8. Mängel / Schäden** gruppiert. Vorhandene `BTBP1__`-Metadaten, GPS-Link, Deduplizierung und Foto-Lösen bleiben erhalten.
- Keine DB- oder Migrationsänderung. Für diesen Portalstand genügt erneut `server/templates/v1_btb.html` auf der Synology und ein Neustart nur von `tb-helfer-v1-api-1`.
- Source-Check **98** schützt die getrennten Firmenpfade, Wetterbearbeitung und Fotogruppierung. Zusätzlich wurde der eingebettete Portal-JavaScriptblock über Connector-Assertion syntaktisch geparst; dies ist ein Source-Check, kein Browser-/E2E-Nachweis.
- Diagnose zum weiterhin als **Aktiv** angezeigten Test-BTB: Android 1.3.17 / 200067 beendet ihn lokal und setzt den lokalen aktiven Zeiger zurück, ruft danach in `BtbModernLifecycleInstaller` aber weiterhin `SofortSync.anstossen(...)` auf. Der selektive BTB-Pfad wäre `SofortSync.btbAnstossen(..., btbId)`, der den BTB als dirty markiert und den `SelectiveSyncWorker` startet. Solange die Server-Finalisierung dadurch nicht angekommen ist, bleibt der autoritative Serverstatus korrekt `ACTIVE`. **app/** wurde im SERVER-Projekt nicht geändert; die Triggerkorrektur bleibt Aufgabe des ANDROID-Projekts.


### BTB-Sync E2E-Fund 25.09.2026 – neuer lokaler BTB blockiert, solange alter Server-BTB ACTIVE ist

- Praktisch reproduziert: Auf Android wurde ein neuer BTB für den 25.09. ab 07:00 angelegt. Im Portal bleibt der 24.09.-BTB weiterhin Aktiv, der neue 25.09.-BTB erscheint noch nicht.
- Serverseitig ist das Verhalten eindeutig: POST /api/v1/worksites/{worksite_id}/btbs verweigert einen zweiten aktiven Team-BTB mit 409 TEAM_ALREADY_HAS_ACTIVE_BTB, solange tb.teams.active_btb_id noch auf den alten BTB zeigt. Das ist die gewollte Ein-aktiver-BTB-Regel und kein Portal-Anzeigefehler.
- Der neue lokale BTB darf deshalb nicht gelöscht oder manuell ersetzt werden. Zuerst muss der alte 24.09.-BTB serverseitig sauber finalisiert werden; erst danach kann der neue BTB regulär angelegt/synchronisiert werden.
- GitHub-Projektstatus weist weiterhin Android 1.3.17 / 200067 als installiert aus. Android 1.3.18 / 200068 enthält zwar den korrigierten selektiven Abschluss-Trigger SofortSync.btbAnstossen(..., btbId), ist aber laut Status noch Build + Installation + E2E offen.
- Nächste Diagnose ohne Änderung auf Verdacht: installierte App-Version am Testgerät prüfen und API-Log der letzten BTB-Vorgänge auf POST .../btbs, /finalize und HTTP 409 kontrollieren. Keine DB-Korrektur und kein künstliches Setzen des Portalstatus.


### 25.09.2026 – Android 200068 installiert bestätigt / Admin-Portal-Sessionkollision

- Der Benutzer hat per Screenshot bestätigt, dass Android **1.3.18 / Build 200068** bereits installiert wurde. Damit ist die vorherige Annahme '200068 noch nicht installiert' überholt.
- Der 24.09.-BTB steht serverseitig dennoch noch auf ACTIVE; der lokal neue 25.09.-BTB ab 07:00 fehlt im Portal. Da der Server nur einen aktiven Team-BTB erlaubt, muss zuerst der alte Server-BTB sauber FINALIZED und active_btb_id gelöst werden. Für einen Abschluss, der vor Installation des 200068-Triggers lokal erfolgt ist, ist der bestehende **Neu laden**-Vollsync der vorgesehene Recovery-Weg; der neue lokale BTB bleibt erhalten.
- Zusätzlich ist die beobachtete Admin-/Portal-Umschaltung im selben Browser im Quellstand erklärt: Admin und normales Portal teilen derzeit `tb_portal_session` und `tb_portal_csrf` mit Cookie-Path `/`. Ein Login als anderer Nutzer überschreibt dadurch die aktive Browseridentität auch für bereits offene Admin-Tabs. Das ist ein echter Session-Isolationsmangel; Fix noch offen.


### 25.09.2026 – Zielbild Admin und Benutzerportal getrennt

- Verbindliche UX-Entscheidung des Benutzers: Das normale Portal ist für die normalen Nutzer. Der Adminbereich bleibt eine eigene Oberfläche/ein eigener Einstieg.
- Eine Anmeldung mit einem Admin-Konto soll direkt in den Adminbereich führen. Optional kann auf der Portal-/Login-Seite oben rechts nur ein kleiner Link/Button **Admin** zum Admin-Einstieg angeboten werden; die normalen Portal-Funktionen bleiben davon getrennt.
- Technisch werden Admin- und Portal-Sitzungen getrennt geführt (eigene Session-/CSRF-Cookies bzw. klar getrennte Session-Kontexte), damit im selben Browser gleichzeitig ein Admin und ein normaler Nutzer angemeldet sein können, ohne dass offene Tabs ihre Identität wechseln.
- Bestehende bestätigte Admin-Seiten/Funktionen werden nicht ersetzt oder neu erfunden; es wird nur die Sitzungs- und Einstiegs-Trennung ergänzt.
- Umsetzung bewusst nach dem aktuell laufenden BTB-Recovery-/Finalize-E2E, damit nicht zwei Fehlerbilder gleichzeitig verändert werden.


### 25.09.2026 – Live-Log: BTB-Syncblocker ist soft-gelöschte deterministische Entry-ID, nicht Finalize

- Der vollständige Synology-API-Log des Pixel-Recoverylaufs wurde gegen den aktuellen Server-/Android-Code geprüft. Die bisherige Vermutung „Finalize liefert BTB_NOT_ACTIVE“ trifft für diesen Lauf **nicht** zu: Im Log ist kein BTB-`/finalize`-Request vorhanden.
- Um 12:50:14 geht stattdessen ein `PATCH /api/v1/btbs/82f7c698-…/header` mit **409** zurück. `82f7c698…` ist der historische/finalisierte BTB; der Server lehnt Inhaltsänderungen an einem nicht aktiven BTB über `prepare_btb_content_change() -> _assert_active_for_edit()` mit `BTB_NOT_ACTIVE` ab. Warum Android diesen historischen BTB noch als lokal zu ändernden ENTWURF/Header-Pending behandelt, ist separat im ANDROID-Projekt zu korrigieren.
- Der eigentliche Blocker des aktiven 24.09.-BTB `5d5d865d…` folgt um 12:51:14: `POST /entries` endet mit **500 Internal Server Error**. PostgreSQL meldet `btb_entries_pkey` für die deterministische ID `6fafc067-91e4-36fd-aecf-ecd720270758` als bereits vorhanden.
- Historie im selben Log: genau diese Entry-ID wurde am 24.09. um 19:25:17 regulär per `DELETE /api/v1/btbs/5d5d865d…/entries/6fafc067…` mit 204 gelöscht. Der Server löscht BTB-Entries fachlich als Soft-Delete; die Primärschlüsselzeile bleibt deshalb erhalten. Android verwendet für granulare Slots bewusst deterministische UUIDs und sendet dieselbe ID bei Wiederanlage erneut. Der bisherige Server-`create_entry()`-Pfad führte dafür blind ein INSERT aus und erzeugte den DB-500.
- Weil Androids `Server1BtbApi.applyPending()` Header und granulare Entries **vor** dem Finalize synchronisiert, beendet dieser 500 den Lauf, bevor überhaupt ein BTB-`/finalize` gesendet wird. Der danach sichtbare 409 beim Anlegen des neuen 25.09.-BTB ist Folge davon: der 24.09.-BTB bleibt serverseitig aktiv.
- **Serverfix Quellstand:** `create_entry()` prüft explizite Entry-IDs jetzt vor dem INSERT. Eine soft-gelöschte ID desselben BTB wird atomar wieder aktiviert (`deleted_at = NULL`, Version +1, gewünschte Nutzdaten); ein bereits aktiver identischer Eintrag wird idempotent akzeptiert; eine echte ID-Kollision liefert kontrolliert `409 ENTRY_ID_CONFLICT` statt eines rohen PostgreSQL-500.
- Keine DB-Migration und kein manuelles Löschen/Ändern vorhandener BTB-Daten erforderlich. Neuer Source-Check **99** schützt diesen Vertrag. Source-Markerprüfung gegen den GitHub-Stand ist erfolgreich; NAS-/Android-E2E ist noch offen.
- Nächster Test: Serverfix deployen, Pixel 200068 erneut **Neu laden**, API-Log prüfen. Erwartet: kein `btb_entries_pkey`-500 mehr; danach muss der 24.09.-BTB bis zum Finalize gelangen und der 25.09.-BTB serverseitig angelegt werden können.


### 25.09.2026 – Migration 014 auf Synology-Staging praktisch bestätigt

- Im laufenden API-Container wurde vor der Migration `known=14`, `applied=13`, `pending=['014_btb_participant_shift_status.sql']`, `latest_applied=13` bestätigt.
- Direkt davor wurde ein FULL-Backup erfolgreich erstellt; Ausgabe `status='OK'` wurde vom Benutzer per Screenshot bestätigt.
- `python -m tbserver.migrations up` meldete anschließend `APPLIED 014_btb_participant_shift_status.sql`.
- Nachkontrolle: `known=14`, `applied=14`, `pending=[]`, `latest_known=14`, `latest_applied=14`. Damit existiert `tb.btb_participant_states` jetzt auf Synology-Staging.
- Nächster Schritt ist kein weiterer DB-Eingriff, sondern derselbe Pixel-200068-Recoverylauf. Dabei prüfen wir, ob der vorherige `UndefinedTable`-500 verschwunden ist und anschließend auch der bereits gebaute Soft-Delete-/Entry-ID-Fix greift.


### 25.09.2026 – BTB-Punkt 5 und Fach-Abnahmefotos sind getrennte Zuordnungen

- Neuer praktischer Befund: Im Android-BTB zeigt Punkt **5. Abnahmen** vier Bilder (`4/4`). Im Baustellenportal ist gleichzeitig eine neue Fach-Abnahme `OPEN / ENTWURF` angekommen, dort steht bei `Abnahme-Fotos` jedoch `Noch keine Fotos`.
- Das ist im aktuellen Datenvertrag nicht automatisch widersprüchlich: BTB-Fotos werden über `BtbTeamMediaSync` an den Parent `BTB`/die konkrete `btbId` gebunden. Der Bildtyp `ABNAHMEN` bezeichnet dabei nur die BTB-Fotokategorie für Punkt 5.
- Eigenständige Fach-Abnahmefotos werden separat aus `AbnahmeSpeicher.fotos` an den Parent `ACCEPTANCE` hochgeladen. Nur diese erscheinen im Portal direkt unter der jeweiligen Fach-Abnahme bei `Abnahme-Fotos`.
- Daher beweist `4/4` im BTB nicht, dass dieselben vier Dateien auch der Fach-Abnahme zugeordnet sind. Automatisches Cross-Linking wird nicht auf Verdacht eingeführt, weil mehrere Fach-Abnahmen pro BTB möglich sind und die Zuordnung sonst mehrdeutig wäre.
- Der hochgeladene Worker-Log zeigt nur temporäre PostgreSQL-Verbindungsfehler während eines DB-Neustarts und bestätigt ausdrücklich, dass der Worker weiterlebt und erneut versucht. Für den aktuellen `BTB_NOT_ACTIVE`-Syncpfad wird der **API-Log** direkt nach einem frischen `Neu laden` benötigt.


### 25.09.2026 – API-Log nach Entry-PK-Fix: 500 behoben, verbleibende 409 getrennt

- Der neue API-Log bestätigt den deployten Serverfix praktisch: Beim aktiven BTB `5d5d865d-59da-4195-8c5b-d9850ec7811a` läuft der zuvor abstürzende Granularpfad nun durch: Entry-POST `201 Created`, danach Entry-PATCH `200 OK` und Entry-DELETE `204 No Content`. Die frühere `btb_entries_pkey`-500-Ursache ist damit im Recoverylauf nicht mehr reproduziert.
- Direkt danach scheitert `POST /api/v1/btbs/5d5d865d-.../finalize` mit **409**. Solange dieser BTB nicht finalisiert wird, bleibt er der aktive Team-BTB; neue BTB-Erstellungen liefern deshalb weiter **409**. Der Access-Log enthält den HTTP-Status, aber nicht den 409-Response-Body; der exakte Finalize-Code ist deshalb noch separat zu bestimmen.
- Zusätzlich gibt es einen zweiten, unabhängigen Fehlerpfad mit dem historischen BTB `82f7c698-51a4-4b7f-a677-2f80e90879a5`: Android sendet weiterhin einen Header-PATCH auf diesen alten BTB (409) und versucht wiederholt dieselbe Datei-Upload-Session `2037e668-...` abzuschließen (409).
- Serverseitig ist das erwarteter Schutz: `prepare_btb_content_change()` verlangt für Inhalts-/Fotoänderungen, dass der BTB aktuell als `active_btb_id` des Teams gesetzt ist, andernfalls folgt `BTB_NOT_ACTIVE`. Dieser Schutz wird **nicht** gelockert, weil finalisierte Historie sonst still verändert würde.
- Der verbleibende historische Schreibversuch ist damit ein **Android-Sync-Thema**: `BtbTeamMediaSync` und der lokale BTB-Export dürfen serverseitig fertige/inaktive historische BTBs nicht ohne explizite Nachbearbeitung erneut als Header-/Medienänderung senden. Das ist im ANDROID-Projekt umzusetzen; SERVER ändert keine `app/**`-Dateien.
- Separater nächster SERVER-Diagnoseschritt für den aktiven `5d5d865d...`: Teilnehmerstatus und BTB-/Teamstatus prüfen, um den exakten Finalize-409 zu bestimmen. Keine Änderung auf Verdacht.
- `STATUS.xlsx` wurde bewusst **nicht** überschrieben: SERVER- und ANDROID-Branch haben unterschiedliche XLSX-Blobs; gemäß Integrator-Regel zuerst konfliktfrei konsolidieren.


### 25.09.2026 – Finalize-409 exakt bestimmt: Helferstatus WORKING blockiert korrekt

- Live-DB-Abfrage des aktiven BTB `5d5d865d-59da-4195-8c5b-d9850ec7811a` bestätigt: Team `880136ec-e107-486e-b354-89d1f4d9e51c` hat diesen BTB korrekt als `active_btb_id`, BTB-Status ist `ACTIVE`, Arbeitstag 24.09.2026, Version 44.
- Teilnehmerzustände: `poco poco` = **MITGLIED / WORKING**, `test.test` = **TEAMLEAD / WORKING**.
- Der Servercode erklärt den beobachteten 409 vollständig: `finalize_btb()` markiert den Teamlead innerhalb derselben DB-Transaktion zunächst FINISHED und prüft danach `unfinished_participants(... exclude_user_id=teamlead)`. Der noch WORKING gesetzte Helfer `poco poco` führt daher zu `409 BTB_PARTICIPANTS_NOT_FINISHED`. Weil die Transaktion bei diesem 409 zurückgerollt wird, bleibt der Teamlead in der späteren DB-Abfrage ebenfalls als WORKING sichtbar.
- Android 1.3.18 / 200068 verwendet die bereits vorhandenen Teilnehmerendpunkte `/shift-finish` und `/shift-resume` noch nicht. Beim BTB-Finalize sendet `Server1BtbApi` weiterhin nur `JSONObject()`; damit bleibt `allow_unfinished=false`.
- Konsequenz: Mit mindestens einem zweiten Teammitglied kann 200068 den BTB derzeit nicht regulär finalisieren, solange dessen Teilnehmerstatus serverseitig WORKING bleibt. Dadurch bleibt `active_btb_id` gesetzt und der neue 25.09.-BTB kann serverseitig nicht angelegt werden.
- **Keine Serverlockerung und keine manuelle SQL-Korrektur.** Nächster fachlich korrekter Schritt liegt im ANDROID-Projekt: Helfer-Schichtende an `shift-finish` anbinden; Teamlead bei offenen Teilnehmern klar anzeigen und optional nur nach bewusster Bestätigung den vorhandenen Server-Override `allow_unfinished=true` verwenden.
- Ziel-E2E danach: Poco -> Schicht Ende -> Teilnehmer FINISHED; Pixel/Teamlead -> BTB abschließen -> BTB FINALIZED + `active_btb_id=NULL`; anschließend neuer 25.09.-BTB erfolgreich serverseitig angelegt.


### 25.09.2026 – Portal-Recovery: Poco-Schichtstatus jetzt FERTIG

- Screenshot im normalen Benutzerportal als `poco`: aktiver BTB 24.09. ist sichtbar; Schichtstatus zeigt `poco = FERTIG` und `test.test = ARBEITET · Haupt-TB`. Der Button `Schicht fortsetzen` bestätigt, dass der eigene Helferstatus serverseitig FINISHED ist.
- Screenshot im Admin-Bereich zeigt denselben Schichtstatus, aber keinen Abschlussbutton. Das ist korrekt: der Admin ist nicht der verantwortliche Teamlead und darf den Teamlead-Lifecycle nicht stellvertretend ausführen.
- Der vorherige konkrete Finalize-Blocker `poco = WORKING` ist im Recoveryzustand damit gelöst. Nächster praktischer Schritt: als `test.test` im normalen Benutzerportal anmelden, aktiven 24.09.-BTB öffnen und `BTB endgültig abschließen` ausführen.
- Erwartung danach: BTB = FINALIZED, `active_btb_id = NULL`; anschließend Pixel 200068 `Neu laden`, sodass der lokale 25.09.-BTB serverseitig angelegt werden kann.
- Android-Schichtende bleibt dennoch als Produktfix offen; der Portal-Recoverytest ersetzt nicht die mobile Implementierung.


### 25.09.2026 – Finalize-Recovery erzeugt NetworkError + Cloudflare 502 Host Error

- Beim praktischen Portal-Recovery als Teamlead `test.test` wurde `BTB endgültig abschließen` ausgelöst. Das Portal meldete unmittelbar `NetworkError when attempting to fetch resource.`.
- Direkt danach lieferte `www.tb-helfer.de` eine Cloudflare-Seite `502 Bad gateway / Host Error` mit Browser=Working und Cloudflare=Working. Damit war der NAS-Origin/Portal-Backendpfad temporär nicht erreichbar; die Ursache ist noch **nicht** bewiesen.
- Der aktuelle Finalize-Pfad erzeugt das Abschluss-PDF synchron innerhalb des Requests. Deshalb muss der API-Log exakt zum Zeitpunkt des Ausfalls geprüft werden, bevor Code verändert wird. Möglich sind u. a. API-Prozessabbruch/Restart, DB-Verlust oder Fehler beim PDF-/Dateipfad; ohne Log keine Festlegung.
- Nächster Schritt: Container-Uptime von `tb-helfer-v1-api-1` prüfen und API-Protokoll um ca. 16:37 CEST auf `/api/v1/btbs/5d5d865d.../finalize`, Traceback, Prozessende/Neustart oder Speicherfehler untersuchen. Keine erneute Finalize-Schleife und kein DB-Eingriff auf Verdacht.


### 25.09.2026 – Synology nach Finalize-Versuch abgestürzt

- Nach dem Portal-Finalize-Versuch mit `NetworkError` und anschließendem Cloudflare-`502 Host Error` ist die Synology selbst ausgefallen und muss neu gestartet werden.
- Damit ist der aktuelle Schwerpunkt zunächst **Host-/Container-Recovery**, nicht Android- oder Servercodeänderung.
- Nach dem Reboot werden zuerst Synology/Container Manager, `tb-helfer-v1-db-1`, `tb-helfer-v1-api-1`, `tb-helfer-v1-worker-1`, `tb-helfer-v1-cloudflared-1` und die Migrationen geprüft. Migration 014 war vor dem Absturz praktisch als 14/14 bestätigt; dieser Stand muss nach dem Reboot weiterhin vorhanden sein.
- Der Reboot ist gleichzeitig der bisher offene praktische Autostart-Test für cloudflared (`PRB-016`).
- Erst wenn DB/API/Worker/Cloudflare stabil sind und die Logs/Uptime rund um den Ausfall gesichert wurden, wird der BTB-Finalize erneut getestet. Keine App-/Nutzerlöschung und keine DB-Korrektur auf Verdacht.


### 25.09.2026 – API-Log nach NAS-Ausfall ausgewertet: Finalize erreichte die API nicht

- Export `api-finalize-1637.log` vollständig geprüft. Zeitstempel sind UTC; `14:37Z` entspricht `16:37 CEST`.
- Bis `14:37:08Z` war die API noch gesund: Portal-/BTB-GETs und `/health/ready` liefen mit 200.
- Im **gesamten exportierten Log gibt es keinen einzigen `/finalize`-Request**. Der Klick im Portal wurde daher nicht als abgeschlossener HTTP-Request in Uvicorn geloggt. Das passt zum unmittelbaren Browser-`NetworkError` und Cloudflare-`502 Host Error` um `14:37:34Z`.
- Ab etwa `14:40Z` folgen ASGI-Exceptions; die konkrete Ursache in den Stacktraces ist fehlende DB-Erreichbarkeit: `psycopg.errors.ConnectionTimeout: connection timeout expired`. Später fällt `/health/ready` auf 503.
- Der API-Prozess startet um `14:53:22Z` erneut, wird kurz darauf sauber beendet und startet um `15:00:11Z` nochmals. Nach diesem Neustart bleibt die Readiness zunächst 503 und wird erst ab ca. `15:01:44Z` wieder 200.
- Schlussfolgerung: Für den Vorfall ist **kein Finalize-Codefehler bewiesen**. Bewiesen ist ein Origin-/DB-/Hostausfall, der den Finalizeversuch überholt hat. Der BTB-Lifecycle darf deshalb nicht auf Verdacht geändert werden.
- Nächste Beweise: `api-inspect.txt` (OOMKilled, ExitCode, RestartCount) und DB-Containerlog für ca. `16:35–16:55 CEST`. Erst danach Hostursache festlegen.


### 25.09.2026 – OOM-Root-Cause bestätigt und PDF-Fotopipeline speicherbegrenzt

- Hostdiagnose bestätigt den NAS-Ausfall eindeutig als **Out-of-Memory**: Swap war vollständig belegt; der Kernel startete den OOM-Killer und tötete den Uvicorn-Prozess.
- Praktischer Test am betroffenen BTB `5d5d865d-...`: **63,3 MiB** gespeicherte Bilddateien entsprechen grob **1290,9 MiB** entpackten RGBA-Pixeldaten. Damit ist die Größenordnung des RAM-Drucks durch die bisherigen Originalfotos im synchronen Finalize-PDF-Pfad bestätigt.
- Serverfix in `server/tbserver/btb_pdf.py`: Originalfotos werden beim PDF-Rendern **nicht verändert**, sondern einzeln als temporäre Arbeitskopie auf maximal **1600×1200 px** begrenzt und als JPEG gespeichert. ReportLab erhält nur diese begrenzten Renderkopien; der temporäre Ordner wird nach `doc.build()` entfernt.
- Für JPEG wird vor dem eigentlichen Thumbnail zusätzlich `draft()` verwendet, damit der Decoder nach Möglichkeit bereits kleiner dekodiert. Dadurch bleibt der Spitzenbedarf pro Foto begrenzt statt alle hochauflösenden Pixel in die PDF-Struktur zu tragen.
- Renderer-Version auf `tb-server-btb-pdf-3` angehoben. Source-/Smoke-Check 34 wurde um einen echten 3072×4080-Fototest erweitert und prüft die Begrenzung.
- **Originalbilder, Datenbank, BTB-Inhalt und Android bleiben unverändert.** Kein Image-Rebuild erforderlich; Pillow ist im vorhandenen Serverimage bereits über ReportLab vorhanden und wurde auf der Synology im Diagnose-Test praktisch verwendet.
- Isolierter Funktionstest des neuen Renderpfads lokal bestanden: synthetisches 3072×4080-JPEG wurde auf **904×1200 px** begrenzt und als JPEG geschrieben. Das bestätigt die eigentliche Begrenzungslogik; es ist **noch kein Server-E2E**.
- Der zugehörige GitHub-Actions-Lauf wurde zwar als fehlgeschlagen geführt, enthielt aber **keine ausgeführten Runner-Steps** und gilt daher gemäß Projektregel nicht als Testnachweis.
- Deploy/E2E offen: neue `btb_pdf.py` auf die Synology, nur API neu starten, Health prüfen, dann 24.09.-BTB einmal finalisieren. Ziel: FINALIZED + `active_btb_id=NULL` ohne OOM/502.


### 25.09.2026 – Finalize nach OOM-Fix praktisch bestätigt

- Der BTB vom 24.09.2026 wurde nach Deployment der speicherbegrenzten PDF-Fotopipeline erfolgreich finalisiert.
- Das Portal zeigt `FINALIZED 2026-09-24`; ein neuer BTB kann wieder gestartet werden. Der zuvor aktive Team-Slot ist damit freigegeben.
- Beim Abschluss trat kein erneuter 502-/OOM-Ausfall auf. PRB-025 ist praktisch bestätigt behoben.
- Nächster Test: auf Pixel 200068 `Neu laden` ausführen und prüfen, ob der bereits lokal vorhandene BTB vom 25.09.2026 serverseitig erscheint.


### 25.09.2026 – Pixel-Recovery bis neuer BTB 25.09 erfolgreich

- Nach dem erfolgreichen Finalize des 24.09.-BTB wurde auf Pixel 200068 erneut `Neu laden` ausgeführt.
- Praktischer Screenshot bestätigt: der bereits lokal vorhandene **25.09.2026**-BTB ist nun auf dem Server angekommen und im Portal als **Aktiv** sichtbar.
- Der Server hat für den neuen BTB beide Teammitglieder als Teilnehmer angelegt: `test.test` = Haupt-TB / ARBEITET, `poco` = ARBEITET. Damit ist die Server-Recovery-Kette vollständig bestätigt: **24.09 FINALIZED → active_btb_id frei → lokaler 25.09.-BTB wird serverseitig angelegt**.
- Kein weiterer Serverfix ist für diese konkrete Recovery-Kette nötig. Offen bleiben die bereits dokumentierten Android-Themen: historischer `BTB_NOT_ACTIVE`-Uploadpfad, echtes mobiles `Schicht Ende` und automatischer Server→Handy-Delta-Pull.
- `STATUS.xlsx` wird weiterhin nicht blind überschrieben, weil Server- und Android-Branch unterschiedliche Workbook-Stände besitzen.
