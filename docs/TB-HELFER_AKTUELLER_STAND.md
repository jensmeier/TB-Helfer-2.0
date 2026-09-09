# TB-Helfer – aktueller Projektstand

Stand: 2026-09-09

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Sie ergänzt `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. den aktuell offenen Draft-PR und dessen neuesten Commit

Nach jeder wesentlichen Änderung/Entscheidung ist diese Datei im aktiven Feature-Branch zu aktualisieren. `main` bleibt stabil.

## Repository / aktiver Entwicklungsstand
- Repository: `jensmeier/TB-Helfer-2.0`
- stabiler Branch: `main`
- aktiver Feature-Branch: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`
- tatsächlicher Server-0.6.0-Quellstand wurde am 2026-09-09 aus der vom NAS gesicherten `server.zip` übernommen und auf dieser Basis zu 0.7.0 erweitert.
- `main` wurde durch diese Arbeiten nicht verändert.

## Produktiver Stand auf der Synology
- NAS/Synology bleibt Source of Truth.
- produktive API läuft seit 2026-09-09 als **0.7.0**.
- produktive PostgreSQL-Datenbank läuft auf **Schema 9** (`009_worksite_collaboration`).
- Schema 8 und 9 wurden kontrolliert mit Backup, Preflight, `ON_ERROR_STOP` und read-only Nachkontrolle eingespielt.
- produktiver Servercode wird aktuell aus `/volume1/docker/tb-helfer/server_0.7_stage` gebaut/eingebunden.
- die bestehende produktive Secret-Datei bleibt unter `./server/.env`; sie wurde nicht in den Stage-Ordner und nicht nach GitHub kopiert.
- der frühere produktive 0.6.0-Serverordner wurde vor Umschaltung zusätzlich nach `/volume1/docker/tb-helfer/backup/server_pre_0.7_20260909` kopiert.
- die vorherige Compose-Konfiguration wurde ebenfalls im Backup-Ordner gesichert.
- Cloudflare API und Cloudflare Admin Access wurden von außen über Mobilfunk erfolgreich geprüft.
- `main` bleibt unverändert/stabil.

### Produktives DB-Backup vor Migration 008/009
Unmittelbar vor der Migration wurde ein PostgreSQL-Custom-Dump erzeugt und mit `pg_restore -l` erfolgreich geprüft:
- Datei: `tb_helfer_pre_0.7_20260909_201605.dump`
- ursprüngliche Kopie im persistenten PostgreSQL-Volume
- zweite Kopie außerhalb des Datenbankordners unter `/volume1/docker/tb-helfer/backup/`
- Prüfung: `BACKUP_PRUEFUNG=OK`

### Schema-7-Preflight
Der aktuelle read-only Preflight aus dem Feature-Branch wurde auf dem echten produktiven Schema 7 ausgeführt.
Ergebnis vollständig grün:
- Schema 7 vorhanden
- alle 12 Pflichtobjekte vorhanden
- beide Pflichtfunktionen vorhanden
- alle 34 Pflichtspalten vorhanden
- PostGIS-Geometrietyp vorhanden
- kein `false`

### Migration 008
`008_incremental_sync_mutations.sql` wurde mit `psql -v ON_ERROR_STOP=1` produktiv ausgeführt.
Ergebnis:
- kein `ERROR`
- Schema 8 eingetragen
- `tb.sync_device_state`, `tb.route_section_versions`, `tb.sync_mutation_receipts` vorhanden
- `routes.current_version` und `stations.current_version` vorhanden

NAS-Datei vor Ausführung bytegenau gegen GitHub geprüft.
Git-Blob-SHA: `062d827ade9f5f9578b816ee5ae86bcdc627dc66`.

### Migration 009
`009_worksite_collaboration.sql` wurde mit `psql -v ON_ERROR_STOP=1 -f /tb/scripts/009_worksite_collaboration.sql` produktiv ausgeführt.
Ergebnis:
- kein `ERROR`
- `INSERT 0 1`
- `COMMIT`
- Schema 9 eingetragen

NAS-Datei vor Ausführung bytegenau gegen GitHub geprüft.
Git-Blob-SHA: `1e33aa95af8e20f6368f351f3687c174c0e9fd46`.

### Read-only Nachkontrolle nach Schema 9
`server/checks/01_verify_schema9.sql` wurde auf der produktiven DB ausgeführt.
Ergebnis vollständig sauber:
- Schema 8 und 9 vorhanden
- alle 14 Pflichtobjekte `ok=t`
- alle 12 Pflichtspalten `ok=t`
- alle 12 Pflichttrigger `ok=t`
- alle 13 Integritätsprüfungen `violations=0`
- Attachment-Parent-Prüfung `violations=0`
- Abschluss `COMMIT`

## Produktive API 0.7 – Rollout und Smoke-Tests
Der API-Dienst wurde kontrolliert auf den vorbereiteten Stage-Ordner umgestellt.

Aktuelle Compose-Logik für `api`:
- Build-Kontext: `./server_0.7_stage`
- produktive `.env`: weiterhin `./server/.env`
- bestehende Daten-Mounts bleiben: `storage`, `logs`, `backup`
- 0.7-Mounts: `app.py`, `templates`, `app_parts`, `data` aus `server_0.7_stage`
- Port bleibt `8089:8080`
- Cloudflare-Dienst und Netzwerkstruktur bleiben unverändert.

Vor der Umschaltung wurde bestätigt, dass das vorhandene API-Image bereits exakt die benötigten Runtime-Abhängigkeiten besitzt:
- FastAPI 0.116.1
- Uvicorn 0.35.0
- psycopg 3.2.9
- Jinja2 3.1.6
- ItsDangerous 2.2.0
- qrcode 8.2
- PostgreSQL-Client 17.11

Projekt-Neuerstellung über Synology Container Manager endete mit `Exit Code: 0`; DB wurde healthy, Cloudflare und API starteten.

Im produktiven `tb-helfer-api`-Container erfolgreich geprüft:
- OpenAPI-Titel: `TB-Helfer API`
- Version: **0.7.0**
- OpenAPI enthält 63 Pfade
- folgende 8 Kernendpunkte sind vorhanden:
  - `/api/v1/sync/catalog`
  - `/api/v1/sync/changes`
  - `/api/v1/sync/ack`
  - `/api/v1/sync/mutations`
  - `/api/v1/collab/context`
  - `/api/v1/collab/changes`
  - `/api/v1/collab/ack`
  - `/api/v1/collab/mutations`
- `app_parts/01_part.py` ff. ist im Container vorhanden.
- `server/data` ist im Container vorhanden mit den beiden verbindlichen Bahnhofsdaten:
  - `TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
  - `TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`

### Produktiver DB-Zugriff aus API 0.7
Direkt aus dem laufenden API-Container erfolgreich geprüft:
- Verbindung zu Datenbank `tb_helfer` funktioniert.
- Schema-Versionen 8 und 9 werden korrekt gelesen.
- Ausgabe endete mit `DB_TEST=OK`.

### Admin-Smoke-Test
Über die produktive Admin-Oberfläche erfolgreich geprüft:
- Anzeige `API: OK · 0.7.0`
- `PostgreSQL: OK`
- `Schema: 9 · 009_worksite_collaboration`
- vorhandene Datenbank wird gelesen.
- Bahnhof-Autofill funktioniert mit der bestehenden TB-Helfer-Datenbasis, u. a. Suchtreffer für München-Pasing/MPB.
- keine neue Bahnhofsliste wurde eingeführt.

### Cloudflare extern geprüft
Über Mobilfunk ohne lokalen NAS-/LAN-Zugriff erfolgreich geprüft:
- `https://api.tb-helfer.de/api/v1/sync/catalog` antwortet ohne Geräte-Token mit `TOKEN_MISSING`; damit ist Cloudflare -> API 0.7 bestätigt.
- `https://admin.tb-helfer.de/admin` führt über Cloudflare Access zur TB-Helfer-Admin-Anmeldung.
- Cloudflare-Anmeldung wurde erfolgreich durchlaufen.
- TB-Helfer-Admin-Anmeldung funktioniert.
- produktives Admin-Dashboard wird angezeigt.
- die Android-App zeigt bereits `Server 0.7.0 · verbunden`.
- `/` und `/openapi.json` sind nicht die maßgeblichen Produktivpfade; die App-/Admin-Pfade funktionieren.

## Server-0.7 – implementierte API
### Allgemeiner Offline-Sync
- `GET /api/v1/sync/catalog`
- selektive Strecken-/Bahnhofsdetails
- `GET /api/v1/sync/changes`
- `POST /api/v1/sync/ack`
- `POST /api/v1/sync/mutations`
- Basisversion/HTTP 409
- idempotente Mutation-Receipts über `client_change_id`
- Tombstones aus dem Change-Log
- mobile Schreibrechte nur für SECTION/OBJECT
- eigener ACK-Cursor `tb.sync_device_state`; vorhandenes `tb.device_sync_state` bleibt unangetastet
- Strecken und Bahnhöfe liefern `current_version`
- mobiler SECTION-/OBJECT-UPSERT erzwingt serverseitig `data_status=MANUAL`; manuelle Änderungen können nicht als `VERIFIED_ORIGINAL`/grün hochgeladen werden

### Baustellen-Zusammenarbeit
- `GET /api/v1/collab/context`
- `GET /api/v1/collab/changes`
- `POST /api/v1/collab/ack`
- `POST /api/v1/collab/mutations`
- Attachment Upload/Download
- Firmenbindung auf Server- und DB-Ebene
- getrennte TB-Teams mit stabiler interner `group_id`; A/B/C/D soll nicht mehr als sichtbare Benutzerbezeichnung verwendet werden
- BTB strikt TB-Team-bezogen
- Mängel/Abnahmen sind baustellenweit **nur für Mitglieder der konkreten Baustelle** sichtbar; keine firmenweite Verteilung
- Hilfsnutzer erhalten Mängel/Abnahmen nur innerhalb ihres berechtigten Arbeitsbereichs
- Bilder/Abnahme-Anlagen als Attachments
- endgültige Status gegen Wiederöffnung geschützt
- Cursor schreitet auch über unsichtbare Änderungen fort
- idempotente Collab-Mutationen und Schutz gegen parallele Doppelanlagen
- Attachment-SHA-Deduplizierung
- Benutzer-Aufbewahrungsoptionen und temporäres Spooling

### Admin
- bestehende 0.6-Admin-Funktionen erhalten
- Firmen-/Benutzer-/Geräte-/Strecken-/Bahnhofs-/Baustellenverwaltung erhalten
- Server-Speicheroptionen pro Benutzer: Bilder, BTB, Mängel, Abnahmen; „Alles“ wird über alle Einzeloptionen abgebildet
- Bahnhof-Autofill ausschließlich aus den zwei vorhandenen TB-Helfer-Datenbeständen
- neue verbindliche Entscheidung: Der Admin soll **nicht** im täglichen Betrieb 50 Baustellen/100 Benutzer manuell miteinander verknüpfen müssen. Baustellen-Teambeitritt wird als Self-Service über sichere Einladungen/QR/Link umgesetzt.

## Android-Stand
- Android `versionCode 200018` liegt im Draft-Branch.
- der Branch-Stand `a641c3d` wurde am 2026-09-09 lokal in Android Studio als Debug-APK neu gebaut; Build endete mit `BUILD SUCCESSFUL`.
- dieselbe aktuelle 200018-APK wurde anschließend auf **beiden Test-Handys als Update** installiert; vorhandene lokale Baustellen-/Vermessungsdaten blieben erhalten.
- auf beiden Handys startet die App fehlerfrei und der Zugriff auf Server 0.7.0 funktioniert.
- separate Room-Datenbank `tbhelfer-sync.db`; bestehende Baustellen-/Vermessungsdatenbank bleibt unangetastet.
- Room-Migrationspfad 1 -> 2 -> 3 vorhanden.
- Offline-Katalog, selektiver Download, Upload-Queue, Basisversion, 409, Tombstones und Hintergrund-Sync vorbereitet.
- `UPLOADING`-Einträge werden nach App-/Netzabbruch erneut versucht; `client_change_id` schützt vor Doppelanwendung.
- BTB-Import erhält gerätelokale `fotos`-/`pdfAuswahl`-Referenzen und erzeugt keinen Medien-Sync-Echo.
- Mängelfoto-KM versteht zusätzlich `70+698`.
- Hilfsnutzer-Scope wird niemals erweitert; Haupt-TB-Scope wird für einzelne Mängel/Abnahmen soweit möglich aus Gleis/Weiche/KM eingegrenzt.
- manueller und Hintergrund-Sync übertragen Attachments.
- Benachrichtigungen pro Gerät: `Text + Ton` / `nur Ton` / `aus`.

Der aktuelle Android-Build ist auf beiden Testgeräten bereit. Vor dem echten Zwei-Geräte-Ende-zu-Ende-Test muss nun die fehlende Self-Service-Einladung ergänzt werden; eine manuelle Admin-Zuordnung gilt nicht mehr als Zielmodell.

## Verbindliches Team-/Rechtemodell
- Baustellen-Teams ausschließlich innerhalb derselben Firma.
- Der Ersteller einer Baustelle ist ihr erster verantwortlicher TB.
- Weitere verantwortliche TBs treten dieser konkreten Baustelle über QR-/Link-/Code-Einladung bei; der Admin ist dafür nicht erforderlich.
- Sichtbar heißt der Bereich **`Verantwortliche TBs`** und zeigt echte Namen, z. B. `Jens Zinkel`, `Max Müller`; keine sichtbaren A/B/C/D-Bezeichnungen.
- Bei jedem verantwortlichen TB gibt es **`Helfer einladen`**; zusätzlich gibt es **`Weiteren TB einladen`** für einen weiteren verantwortlichen TB mit eigenem BTB-Team.
- Jeder verantwortliche TB kann eigene Hilfsnutzer per Team-Einladung aufnehmen und deren Arbeitsbereich begrenzen.
- Benutzer derselben Firma ohne angenommene Einladung zu dieser Baustelle erhalten keine Baustellen-Zusammenarbeitsdaten.
- BTB bleibt ausschließlich innerhalb des jeweiligen TB-Teams gemeinsam.
- Sichtbarer/Export-BTB-Name enthält TB-Name + Datum, z. B. `BTB_Jens Zinkel_2026.06.05`; mehrere BTBs desselben Tages erhalten `-2`, `-3`, `-4` usw. Intern bleibt die stabile ID maßgeblich.
- Mängel/Abnahmen werden nur zwischen berechtigten Mitgliedern **dieser einen Baustelle** synchronisiert; nicht an alle Benutzer einer Firma.
- Hilfsnutzer sehen Mängel/Abnahmen nur in ihrem berechtigten Bereich.
- Bilder werden entsprechend derselben Baustellen-/Bereichsberechtigung mitsynchronisiert.
- endgültige Status bleiben endgültig.
- Einladungen enthalten keine Benutzer-Keys/Passwörter, sondern widerrufbare/zeitlich begrenzte Tokens.

## Benutzer-/Firmenprofil – neue verbindliche UX-Entscheidung
- Nach erfolgreicher Geräteaktivierung werden Benutzer- und Firmenstammdaten aus dem Serverprofil automatisch in der App angezeigt; keine doppelte manuelle Pflege am Handy.
- Benutzeranzeige mindestens: Name, E-Mail soweit vorhanden, Firma, Zugriffsstatus, Ablaufdatum bzw. Restlaufzeit.
- Firmenanzeige aus zentralem Firmenprofil: Firmenname, Anschrift, Telefon, E-Mail und Firmenlogo.
- Firmenlogo wird einmal serverseitig gepflegt und an alle Geräte derselben Firma verteilt/gecached.
- Für Support zeigt die App eine nicht geheime Support-/Gerätekennung und höchstens einen **maskierten** Key-Hinweis (z. B. letzte 4 Zeichen); kein vollständiger Aktivierungsschlüssel offen in der Profilansicht.
- Baustellen-QR und Aktivierungs-QR haben getrennte Aufgaben: Aktivierung lädt Benutzer/Firmenprofil; Baustellen-QR ändert keine Stammdaten, sondern fügt nur die Baustellen-/Team-Mitgliedschaft hinzu.

## Geplante Self-Service-Einladung – noch nicht produktiv
Der vorhandene Schema-9-Stand enthält Teams und Mitglieder, aber noch kein Einladungssystem. Geplanter nächster Baustein:
- neue Migration **010** statt Änderung der bereits produktiven Migration 009
- Einladungstabelle mit gehashtem Token, `worksite_id`, Einladendem, Einladungsart, optionalem Ziel-Team/Bereich, Ablauf, Nutzungsgrenze und Widerruf
- Einladungsarten mindestens:
  - `weiteren TB zur Baustelle einladen` → erzeugt beim Einlösen ein eigenes TB-Team für den neuen verantwortlichen TB
  - `Helfer einladen` → ordnet den Benutzer dem bestehenden TB-Team und seinem freigegebenen Bereich zu
- QR, teilbarer Link und kurzer Code sind nur verschiedene Darstellungen derselben sicheren Einladung
- Server prüft beim Einlösen: Benutzer aktiv, Gerät gültig, gleiche Firma, Einladung gültig/nicht widerrufen/nicht abgelaufen
- Worksite-Mängel/Abnahmen und Benachrichtigungen werden ausschließlich nach bestätigter Baustellenmitgliedschaft ausgeliefert

## Nächste sichere Schritte
Server 0.7, Schema 9, Cloudflare und Android 200018 auf beiden Testgeräten sind bestätigt. Noch offen:
1. Self-Service-Baustelleneinladungen auf dem Feature-Branch implementieren (Migration 010 + Server-API + Android-UI/QR/Link) und dabei die neuen sichtbaren Begriffe `Verantwortliche TBs`, `Helfer einladen`, `Weiteren TB einladen` verwenden.
2. Benutzer-/Firmenprofil-Autofill inkl. zentralem Firmenlogo, Zugriffsstatus/Ablauf und Supportkennung in Android/Server ergänzen.
3. danach neuen Android-Build lokal bauen und auf beiden Testgeräten installieren.
4. echten Zwei-Geräte-Ende-zu-Ende-Test ohne manuelle Admin-Zuordnung durchführen:
   - Baustelle durch verantwortlichen TB anlegen
   - weiteren TB per Einladung aufnehmen
   - Helfer per Team-Einladung aufnehmen
   - Firmenbindung
   - BTB strikt zwischen den TB-Teams getrennt
   - Mängel/Abnahmen nur für Mitglieder dieser Baustelle und Hilfsnutzer nur im Bereich
   - Bilder/Attachments
   - 409-Konflikt
   - Tombstones
   - Netzabbruch/Wiederaufnahme
   - Benachrichtigungen
5. Draft-PR #5 bis zum erfolgreichen Ende-zu-Ende-Test Draft lassen; `main` vorher nicht ändern.

## Bahnhofsdaten – nicht neu erfinden
Vorhandene Datenbasis:
- `app/src/main/assets/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
- `app/src/main/assets/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`
- `app/src/main/java/de/tbhelfer/util/BahnhofSucheUtil.kt`

## Vermessungs-/Kilometrierungsfarben
- Grün = belastbare Originaldaten/KM
- Orange = manuell geändert/hergestellt
- Rot = TRA vorhanden, aber keine belastbare KM
- Grau = ohne Vermessungsgeometrie
- Blau = Auswahl/temporäre Markierung

## Nicht erneut entscheiden / nicht zurückbauen
- NAS ist Source of Truth.
- Originale Vermessungsdaten bleiben unverändert.
- nur benötigte Daten offline aufs Handy.
- Cloudflare normale App-Synchronisation; Tailscale Wartung.
- keine firmenübergreifenden Teams.
- keine firmenweite Verteilung von Mängeln/Abnahmen; ausschließlich berechtigte Mitglieder der konkreten Baustelle.
- BTB bleibt TB-Team-bezogen und sichtbar personenbezogen benannt, technisch aber ID-basiert.
- Baustellen-/Teambeitritt im Normalbetrieb per Self-Service-Einladung, nicht per täglicher Admin-Zuordnung.
- Benutzer-/Firmenstammdaten kommen nach Aktivierung aus dem Serverprofil; Firmenlogo zentral serverseitig.
- Server-Speicheroptionen bleiben vorgesehen, sind aber keine Arbeitsfreigabe.
- temporäres Server-Spooling bleibt erlaubt.
- `main` stabil halten; große Änderungen über Feature-Branch/Draft-PR.

## Sitzungsregel für ChatGPT
Bei einem neuen Chat nicht aus alten Chat-Zusammenfassungen allein weiterarbeiten. Zuerst Projektregeln, diese Datei, Draft-PR und neuesten Commit in GitHub prüfen. Bei Widersprüchen gilt der produktive NAS-Stand für tatsächlich laufenden Server/DB-Stand, die Projektregeln für fachliche Entscheidungen und der Feature-Branch für noch nicht produktive Entwicklung.
