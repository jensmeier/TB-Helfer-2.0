# TB-Helfer – aktueller Projektstand

Stand: 2026-09-10

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen zusätzlich in `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. offenen Draft-PR und neuesten Commit

`main` bleibt stabil. Wesentliche Änderungen zuerst im Feature-Branch/Draft-PR und danach diesen Stand aktualisieren.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiv: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`
- PR bleibt Draft bis zum erfolgreichen Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test.
- Android im Feature-Branch: `versionCode 200020`, `versionName 1.3`.

## Produktiver Synology-Stand
- NAS/Synology ist Source of Truth.
- API: **0.7.0**.
- PostgreSQL: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- `compose.yaml` baut und mountet `app.py`, `templates`, `app_parts`, `data` aus `./server_schema10_stage`.
- produktive Secrets bleiben ausschließlich in `./server/.env`.
- Projekt-Neuerstellung im Container Manager: **Exit Code 0**.
- PostgreSQL healthy, API und cloudflared gestartet.
- laufende API: **72 OpenAPI-Pfade**.
- geprüft vorhanden:
  - `/api/v1/collab/worksites`
  - `/api/v1/collab/invitations/redeem`
  - `/api/v1/company/profile`
  - `/api/v1/collab/worksites/{worksite_id}/media`
- direkter API-Container-DB-Test: **`DB_TEST=OK`**.
- API liest **`SCHEMA=10 010_worksite_invitations_company_profile`**.
- Admin-Systemstatus: API OK 0.7.0, PostgreSQL OK, Schema 10.

## Backup / Migration 010
Produktives Pre-Schema-10-Backup:
- `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`
- mit `pg_restore -l` geprüft: **`BACKUP_PRUEFUNG=OK`**.

Vor Migration wurden NAS-SQL-Dateien nach CRLF-Normalisierung gegen GitHub geprüft:
- `010_worksite_invitations_company_profile.sql` → `d3b78c723de0c5ee0b2a0121957d81513e240a86`
- `02_verify_schema10.sql` → `100b1214a81990ed84a187b243f94b745ee742ab`

Migration 010 lief mit `ON_ERROR_STOP`, endete mit `INSERT 0 1` und `COMMIT`. Anschließende Prüfung endete mit **`SCHEMA_10_OK`**. Für den aktuellen Rollout keine weitere DB-Migration starten.

## Firmen / Teams / Einladungen – praktisch bestätigt
- Testfirma `Gtu`: 2 Nutzer, 2 aktive Geräte.
- Firmenprofil/Logo funktioniert produktiv.
- Helfer-/TB-Einladung funktioniert praktisch.
- Testbaustelle `Immenstadt`: 1 TB-Gruppe, 2 Nutzer.
- Teams ausschließlich innerhalb derselben Firma.
- Ersteller der Server-Baustelle ist erster verantwortlicher TB.
- weitere verantwortliche TBs treten per sicherer Baustelleneinladung bei.
- Helfer werden in die Gruppe ihres verantwortlichen TB eingeladen und können `area_scope` erhalten.
- sichtbare UI: `Verantwortliche TBs`, `Helfer einladen`, `Weiteren TB einladen`.
- keine sichtbaren A/B/C/D-Gruppenbezeichnungen.
- Aktivierungs-QR und Baustellen-Einladungs-QR bleiben getrennt.
- Namen sind nie technische Schlüssel; UUIDs/Team-IDs bleiben maßgeblich.

## BTB – verbindliche Logik
Fachliche BTB-Identität:
**internes TB-Team + Datum + Schicht**.

- pro Baustelle/TB-Team/Datum/Schicht genau ein gemeinsamer BTB.
- Helfer schreiben in den BTB ihres verantwortlichen TB.
- mehrere Helfer/Geräte desselben Teams dürfen denselben BTB ergänzen.
- andere verantwortliche TB-Teams bleiben getrennt.
- TAG/NACHT bleiben getrennt.
- Arbeitsbereiche, Gleise, Weichen, KM und Abschnitte sind Inhalt, nicht BTB-Identität.
- keine künstlichen `-2/-3/-4`-Berichte derselben Team-/Tag-/Schicht-Kombination.

Beispielanzeige:
- `BTB Jens Zinkel · 05.06.2026 · Tag`
- `BTB Max Müller · 05.06.2026 · Nacht`

## Praktischer BTB-Zwei-Geräte-Befund 2026-09-10
Produktive DB-Prüfung für `Immenstadt`:
- vier BTB-Einträge liegen in `tb.collab_btb_entries`.
- darunter `2026-09-10:NACHT` mit der auf dem Poco geschriebenen Testnotiz.
- alle geprüften BTBs gehören zur TB-Gruppe von `jens poco`.
- damit ist **Poco → API → PostgreSQL** für BTB bestätigt.
- `tb.collab_device_state`: Pixel und Poco jeweils `handy_cursor=6`, `server_cursor=6`.
- damit hat auch das Pixel den vollständigen Collaboration-Feed abgeholt.

Gefundener Fehler:
- Pixel hatte trotz Server-Mitgliedschaft keine lokale Hauptbaustelle `Immenstadt`.
- der Server-BTB war im Sync-Cache vorhanden, konnte aber ohne lokale Baustellenbindung nicht in den normalen Pixel-BTB-Speicher geschrieben werden.

## Android 200020 – lokaler Baustellen-Bootstrap
Fix im Feature-Branch:
- `WorksiteBindingRepository.contextsWithBindings()` erzeugt für eine ungebundene Server-Baustelle automatisch einen lokalen HAUPT-Knoten mit dem Server-Baustellennamen und bindet ihn sofort.
- Automatik greift nur, wenn auf dem Gerät noch **keine gleichnamige lokale Hauptbaustelle** existiert.
- existiert bereits eine gleichnamige lokale Hauptbaustelle, wird weiterhin **nicht automatisch nach Namen zusammengeführt**; die bewusste manuelle Auswahl bleibt erhalten.
- dadurch kann `CollabLocalBridge.applyServerStateToLocal()` bereits heruntergeladene BTB/Mängel/Abnahmen in den normalen lokalen Fachspeicher übernehmen.
- `ServiceLocator` verwendet dafür `BaustellenBaumRepository` im `WorksiteBindingRepository`.
- Version wurde auf **200020** angehoben.
- Build/Installation/Praxis-Re-Test von 200020 stehen noch aus.

## Noch fehlender Baustellenstruktur-Sync
Wichtig: Der Root-Bootstrap löst den aktuellen BTB-Blocker, aber noch nicht den kompletten lokalen Baustellenbaum.

Aktueller Befund:
- Poco hat lokal Hauptbaustelle `Immenstadt` mit Unterbaustelle `5362-1`.
- `5362-1` enthält Strecke/KM.
- bisheriger Self-Service-Endpunkt speichert nur die Hauptbaustelle; Unterknoten werden nicht serverseitig gespeichert.
- deshalb kann das Pixel `5362-1` noch nicht automatisch nachbauen.

Nächster Implementierungsschritt:
- eigener serverseitiger Baustellenstruktur-Sync für benötigte Unterknoten.
- keine Freitext-/Namens-Tricks.
- selektiv nach Berechtigung/Arbeitsbereich laden.
- Original-Vermessungsdaten bleiben davon getrennt und unverändert.

## Mängel / Abnahmen / Bilder / Benachrichtigungen
- Mängel und Abnahmen baustellenweit zwischen berechtigten verantwortlichen TBs derselben Baustelle.
- Helfer nur innerhalb ihres freigegebenen Bereichs.
- Bilder/Attachments folgen derselben Mitgliedschafts-/Bereichsregel.
- Status `abgestellt/erledigt` bleibt endgültig.
- Benachrichtigungsmodi pro Gerät: Text + Ton / nur Ton / aus.

## Server-Speicheroptionen
Benutzeroptionen:
- Bilder speichern
- BTB speichern
- Mängel speichern
- Abnahmen speichern
- Alles speichern

Nicht dauerhaft gespeicherte Daten dürfen temporär zur Synchronisation auf dem Server liegen.

## Sync-Grundregeln
- selektiver Download
- Offline-Arbeit
- Upload-Queue
- Versionierung
- 409-Konflikte
- Tombstones
- Hintergrund-Sync
- NAS bleibt Source of Truth

## Bahnhofsdaten – nicht neu erfinden
Verbindliche Bestände:
- `app/src/main/assets/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
- `app/src/main/assets/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`
- `app/src/main/java/de/tbhelfer/util/BahnhofSucheUtil.kt`

Server/Admin müssen daraus Bahnhofscode/Name vervollständigen. Keine neue Bahnhofsliste einführen.

## BTB-PDF / Export
- direkter lokaler BTB-PDF-Name kanonisch: `BTB_<Datum>_<TAG|NACHT>_<VerantwortlicherTB>.pdf`.
- erneutes Erzeugen desselben fachlichen BTB aktualisiert denselben Zielpfad.
- sichtbarer Kopf: Baustelle, Firma, verantwortlicher TB, Datum, Schicht, Zeit.
- Test-PDF `BTB_2026-09-10_NACHT_jens_poco.pdf` praktisch geprüft.
- historische PDFs werden nicht zwangsumbenannt.
- langfristiges Laptop-Archiv: `TB-Helfer/<Baustelle>/...`.
- mehrere Geräte-ZIPs müssen merge-sicher in denselben Zielordner entpackbar sein.
- Bilder/Anlagen erhalten stabile/inhaltsbasierte Kennungen.
- `Bilderliste_gesamt.xlsx` + CSV sowie Mängel-/Abnahme-Gesamtlisten sind im Feature-Branch vorhanden; echter Mehrgeräte-Test steht aus.

## Android-Buildstatus
- Android 200019 wurde mehrfach lokal erfolgreich gebaut und installiert.
- letzter dokumentierter 200019-Build nach BTB-PDF-Abschluss: `BUILD SUCCESSFUL in 14s`, 69 Tasks.
- Android **200020** enthält den neuen lokalen Hauptbaustellen-Bootstrap und ist **noch nicht praktisch gebaut/getestet**.
- GitHub Actions ist für diesen Stand nicht maßgeblich; lokale Android-Studio-Builds sind die Autorität.
- Compilerwarnungen bleiben im separaten Backlog.

## Testgeräte
- Poco: Xiaomi `23013PC75G`, Android 15 / SDK 35
- Pixel: Google Pixel 8, Android 17 / SDK 37
- beide Testnutzer gehören zur Firma `Gtu`.
- `susi zinkel` / Firma `privat` gehört nicht zum Same-Company-Test.

## Vermessung / Streckendaten – eigener offener Hauptblock
Bereits vorhanden: lokale TRA/GRA/KOR/KF/Absteckdaten, Trassenachsen, KM-Punkte, KM-Anker, Korrekturen, GPS-Näheberechnung, Trassengrafik sowie allgemeiner serverseitiger Strecken-/Bahnhofs-/Abschnitt-Sync.

Noch offen:
- exakt trennen: lokal vs. serverseitig vorhanden.
- Originaldateien unverändert dauerhaft auf NAS.
- berechnete/bearbeitete Ebene getrennt versionieren.
- selektiver Download nach Bahnhof/Gleis/Weiche/DKW/KM-Bereich.
- TRA, GRA, rohe KF, KOR und benötigte weitere Formate serverseitig katalogisieren.
- echte TRA-Geometrie-Kilometrierung, GPS-KM-Anker, Zwischenpunkte, angrenzende Gleise, Weichen/DKW/Kreuzungen Ende-zu-Ende testen.
- nur geänderte/berechnete Abschnitte hochladen.
- 409-Konflikte für konkurrierende Abschnittsbearbeitung.

Farben:
- Grün = belastbare Originaldaten/KM
- Orange = manuell geändert/hergestellt
- Rot = TRA ohne belastbare KM
- Grau = ohne Vermessungsgeometrie
- Blau = Auswahl

## Nächste sichere Schritte
1. **Keine neue produktive DB-Migration und keinen Server-Rebuild starten.** Schema 10/API 0.7 laufen produktiv.
2. Android **200020** lokal bauen.
3. zunächst als Update auf dem Pixel installieren.
4. Baustellen-Synchronisation öffnen und prüfen, dass `Immenstadt` automatisch lokal angelegt und an die Server-Baustelle gebunden wird.
5. prüfen, ob der bereits gecachte `2026-09-10:NACHT`-BTB einschließlich Poco-Testnotiz auf dem Pixel erscheint.
6. auf Pixel einen BTB-Eintrag ergänzen, synchronisieren und auf Poco prüfen.
7. danach den Unterbaustellen-/Baustellenstruktur-Sync für `5362-1` bauen.
8. anschließend Mängel/Abnahmen/Bilder/Benachrichtigungen, Offline/409/Tombstones/Netzabbruch und Merge-Export mit beiden Geräten testen.
9. danach den separaten Vermessungsblock fertigstellen.
10. PR #5 erst nach erfolgreichem Ende-zu-Ende-Test aus Draft nehmen.

## Nicht erneut entscheiden / nicht zurückbauen
- NAS Source of Truth.
- Originaldaten unverändert.
- selektive Offline-Daten.
- Cloudflare für normalen Sync; Tailscale nur Wartung.
- keine firmenübergreifenden Teams.
- keine firmenweite Mängel-/Abnahmeverteilung.
- Self-Service-Baustellenbeitritt statt täglicher Admin-Zuordnung.
- `Verantwortliche TBs` mit echten Namen; keine sichtbaren A/B/C/D-Gruppen.
- Helfer schreiben in den BTB ihres verantwortlichen TB.
- BTB-Identität = Team + Datum + Schicht.
- zentraler Firmenprofil-/Logo-Stand vom Server.
- merge-sicheres Archiv `TB-Helfer/<Baustelle>/...`.
- `main` stabil; große Änderungen im Feature-Branch/Draft-PR.
