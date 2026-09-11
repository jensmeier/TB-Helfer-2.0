# TB-Helfer – aktueller Projektstand

Stand: 2026-09-11

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
- Android im Feature-Branch: `versionCode 200022`, `versionName 1.3`.

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

Migration 010 lief mit `ON_ERROR_STOP`, endete mit `INSERT 0 1` und `COMMIT`. Anschließende Prüfung endete mit **`SCHEMA_10_OK`**.

## Persönliches Nutzerprofil / Firma – vorbereitet für Schema 11
Verbindliche Trennung:
- **Firma** ist zentral für alle Nutzer derselben Firma: Firmenname, Anschrift, Firmentelefon, Firmen-E-Mail, Firmenlogo.
- **Persönliches Profil** gehört zum einzelnen Server-Benutzer: Name, persönliche Telefonnummer, persönliche E-Mail-Adresse.
- persönliche Kontaktdaten dürfen nicht als gerätespezifische Doppelpflege auseinanderlaufen; Server-Benutzer ist Source of Truth, Android hält nur einen Offline-Cache.

Aktueller Datenstand:
- `tb.users.email` ist bereits die persönliche Benutzer-E-Mail.
- ein persönliches Telefonfeld existiert produktiv in Schema 10 noch nicht.
- Firmen-Telefon und Firmen-E-Mail liegen bereits korrekt in `tb.company_profiles`.

Im Feature-Branch vorbereitet:
- Migration `server/migrations/011_user_contact_profile.sql`:
  - `tb.users.phone`
  - `tb.users.profile_version`
  - Schema-Version `11 · 011_user_contact_profile`
- read-only Prüfschema: `server/checks/05_verify_schema11.sql` → erwartet `SCHEMA_11_OK`.
- statischer Quellencheck: `server/checks/06_verify_schema11_source.py`.
- neuer API-Teil `server/app_parts/18_part.py`:
  - `GET /api/v1/user/profile`
  - `PUT /api/v1/user/profile`
  - bei noch nicht migriertem Server kontrolliert `USER_PROFILE_SCHEMA_REQUIRED` statt blindem DB-Zugriff.
- Android `UserProfileApi.kt` für persönliches Profil.
- separater Offline-Cache `PersoenlichesNutzerProfilSpeicher`; bestehender Firmenprofil-Cache bleibt unverändert.
- `Nutzer / Firma` ist im Android-UI jetzt sichtbar in **Mein Profil** und **Firma** getrennt.
- Name im persönlichen Profil bleibt serverseitige Identität/Anzeige und wird in dieser Maske nur angezeigt.
- persönliche Telefonnummer und persönliche E-Mail sind editierbar.
- Firmenfelder und Firmenlogo bleiben serverseitiger zentraler Read-only-Stand in Android.
- Android-Version dafür: **200022**.

Wichtig: **Schema 11 ist nur im Feature-Branch vorbereitet und wurde auf dem NAS noch nicht ausgeführt. Produktiv bleibt Schema 10.** Vor Schema-11-Migration wieder Backup, Hash-Prüfung der SQL-Dateien und anschließende read-only Verifikation durchführen.

## Firmen / Teams / Einladungen – praktisch bestätigt
- Testfirma `Gtu`: 2 Nutzer, 2 aktive Geräte.
- Firmenprofil/Logo funktioniert produktiv.
- Helfer-/TB-Einladung funktioniert praktisch.
- Testbaustelle `Immenstadt`: 1 TB-Gruppe, 2 Nutzer.
- Teams ausschließlich innerhalb derselben Firma.
- Ersteller der Server-Baustelle ist erster verantwortlicher TB.
- weitere verantwortliche TBs treten per sicherer Baustelleneinladung bei.
- Helfer werden in die Gruppe ihres verantwortlichen TB eingeladen und können `area_scope` erhalten.
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

## Praktischer BTB-Zwei-Geräte-Befund
Produktive DB-Prüfung für `Immenstadt`:
- vier BTB-Einträge liegen in `tb.collab_btb_entries`.
- darunter `2026-09-10:NACHT` mit der auf dem Poco geschriebenen Testnotiz.
- alle geprüften BTBs gehören zur TB-Gruppe von `jens poco`.
- damit ist **Poco → API → PostgreSQL** für BTB bestätigt.
- `tb.collab_device_state`: Pixel und Poco jeweils `handy_cursor=6`, `server_cursor=6`.
- damit hat auch das Pixel den vollständigen Collaboration-Feed abgeholt.

Befund Android 200020:
- Pixel hatte zunächst trotz Server-Mitgliedschaft keine lokale Hauptbaustelle `Immenstadt`.
- der 200020-Root-Bootstrap hat danach `Immenstadt` auf dem Pixel automatisch lokal angelegt und gebunden: **praktisch bestätigt**.
- die neue lokale Hauptbaustelle wurde jedoch nicht automatisch als aktuelle Baustelle gesetzt.
- deshalb arbeitete der normale BTB-Bildschirm weiter mit der zuvor ausgewählten Baustelle; der Nutzer konnte in der neuen `Immenstadt` nicht unmittelbar arbeiten und vorhandene Poco-BTBs wirkten unsichtbar.

## Android 200021/200022 – vereinfachter Baustellen-/QR-Ablauf
Der direkte Baustellen-/QR-Ablauf aus 200021 bleibt in 200022 enthalten:
- `Baustellen-Synchronisation` ist aus dem normalen **Einstellungen**-Menü entfernt.
- Synchronisation bleibt technisch/Hintergrundfunktion; der Nutzer muss dafür nicht mehr in ein separates Einstellungsmenü.
- in der normalen **Baustellenliste** gibt es direkt den Button **`QR scannen`**.
- der gescannte Baustellen-QR entscheidet serverseitig selbst, ob der Beitritt als `HELPER` oder `LEAD`/verantwortlicher TB erfolgt; keine zusätzliche Rollenauswahl beim Empfänger.
- nach erfolgreichem QR-Beitritt werden Collaboration-Daten synchronisiert, die lokale Hauptbaustelle aufgebaut/gebunden und direkt **als aktuelle Baustelle gesetzt**.
- bei einer bereits vorhandenen gleichnamigen lokalen Hauptbaustelle wird aus Sicherheitsgründen nicht blind nach Namen zusammengeführt; stattdessen erscheint unmittelbar ein lokaler Auswahl-Dialog.
- der Langdruck-Menüpunkt **`Nutzer hinzufügen`** bleibt auf der Baustellenseite. Er öffnet keinen separaten Sync-Bildschirm mehr.
- dort gibt es direkt die Auswahl:
  - `Nutzer (verantwortlichen TB) hinzufügen`
  - `Helfer hinzufügen`
- für Helfer bleibt die Bereichsfreigabe (`Gesamte Baustelle` oder Bereich/km) erhalten.
- der erzeugte QR wird direkt angezeigt und kann über **`Teilen / senden`** weitergegeben werden.
- zentrale Implementierung: `app/src/main/java/de/tbhelfer/ui/baustelle/BaustellenTeamDirekt.kt`.
- 200021/200022 sind noch nicht lokal vollständig gebaut/installiert/getestet.

Die alte `BaustellenSyncFragment`-Implementierung bleibt vorerst technisch im Quellstand als Diagnose-/Rückfallpfad, ist aber kein normaler Benutzereinstieg mehr.

## Sync-Matrix aus dem Praxismodell
Verbindliche Interpretation der aktuell besprochenen Matrix:

### Verantwortlicher TB A ↔ verantwortlicher TB B derselben Baustelle
- Baustelle inkl. freigegebener Unterbaustellen: gemeinsamer Baustellenstand.
- benötigte Strecken-/Arbeitsdaten: über NAS/Server synchronisiert, Originaldaten bleiben unverändert; keine blind kopierten Originalpakete.
- Mängel/Schäden und Abnahmen: baustellenweit innerhalb der Berechtigung gemeinsam.
- dazugehörige Bilder: gemeinsam innerhalb derselben Berechtigung.
- **BTB nicht zwischen TB A und TB B vermischen.** Jeder verantwortliche TB hat seinen eigenen Team-BTB.

### Verantwortlicher TB ↔ Helfer desselben TB-Teams
- Baustelle inkl. freigegebener Unterbaustellen: Helfer erhält den benötigten/berechtigten Stand.
- benötigte Streckendaten: selektiv nach Berechtigung/Arbeitsbereich.
- BTB: **gemeinsam in beide Richtungen innerhalb desselben TB-Teams**.
- Mängel/Schäden und Abnahmen: innerhalb der Helfer-Berechtigung synchronisiert.
- Helferbilder müssen zum verantwortlichen TB/Server hochgehen; Download anderer Bilder zum Helfer nur soweit für seinen Bereich/Arbeitsablauf nötig.

## Nutzer/Helfer aus Baustelle entfernen – noch offen
Der gewünschte Bedienpunkt `Nutzer/Helfer entfernen` soll ebenfalls direkt bei der Baustelle liegen, nicht in einem separaten Sync-Menü.

Aktueller Serverstand bietet Einladen, Einlösen und Einladung widerrufen, aber noch **keinen fertigen mobilen Endpunkt zum Entfernen einer bestehenden Baustellen-Mitgliedschaft**. Deshalb wurde kein gefährliches rein lokales Löschen eingebaut.

Nächster Serverpunkt:
- Mitgliedschaft serverseitig entfernen/widerrufen.
- Helfer: verantwortlicher TB darf Helfer seiner TB-Gruppe entfernen.
- Verhalten für das Entfernen eines anderen verantwortlichen TBs muss mit den bestehenden Gruppen-/Datenregeln sicher umgesetzt werden; vorhandene BTB-/Mängel-/Abnahmedaten dürfen dadurch nicht versehentlich gelöscht werden.
- danach direkte Auswahl `Nutzer/Helfer entfernen` im selben Baustellen-Menü.

## Noch fehlender Baustellenstruktur-Sync
Wichtig: Der Root-Bootstrap löst nur die Hauptbaustelle, noch nicht den kompletten lokalen Baustellenbaum.

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
- Android 200020 wurde auf dem Pixel praktisch getestet: Root-Bootstrap `Immenstadt` funktioniert; fehlendes automatisches `aktuell` wurde erkannt.
- Android **200022** enthält direkten Baustellen-/QR-Ablauf plus getrenntes persönliches Nutzerprofil und ist noch nicht lokal gebaut/getestet.
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
1. **Produktiven NAS-Stand vorerst auf Schema 10/API 0.7 lassen.** Schema 11 ist nur vorbereitet.
2. Android **200022** lokal kompilieren; noch keine App-Daten löschen.
3. statischen Schema-11-Quellencheck ausführen.
4. vor produktiver Schema-11-Migration ein neues DB-Backup erzeugen und mit `pg_restore -l` prüfen.
5. Migration `011_user_contact_profile.sql` und `05_verify_schema11.sql` nach CRLF-Normalisierung gegen die GitHub-Blob-Hashes prüfen.
6. erst danach Migration 011 mit `ON_ERROR_STOP` ausführen und `SCHEMA_11_OK` bestätigen.
7. neuen Serverstand mit `18_part.py` in einen separaten Stage-Ordner übernehmen und kontrolliert neu bauen/starten; `server/.env` unangetastet lassen.
8. danach Android 200022 als Update auf Pixel/Poco installieren und unter `Nutzer / Firma` die Trennung **Mein Profil / Firma** testen.
9. anschließend QR-/Baustellen-Ablauf und BTB Poco ↔ Pixel erneut testen.
10. danach Unterbaustellen-/Baustellenstruktur-Sync für `5362-1` bauen.
11. danach serverseitige Mitgliedschafts-Entfernung und direkte `Nutzer/Helfer entfernen`-Aktion ergänzen.
12. anschließend Mängel/Abnahmen/Bilder/Benachrichtigungen, Offline/409/Tombstones/Netzabbruch und Merge-Export mit beiden Geräten testen.
13. danach den separaten Vermessungsblock fertigstellen.
14. PR #5 erst nach erfolgreichem Ende-zu-Ende-Test aus Draft nehmen.

## Nicht erneut entscheiden / nicht zurückbauen
- NAS Source of Truth.
- Originaldaten unverändert.
- selektive Offline-Daten.
- Cloudflare für normalen Sync; Tailscale nur Wartung.
- keine firmenübergreifenden Teams.
- keine firmenweite Mängel-/Abnahmeverteilung.
- Self-Service-Baustellenbeitritt statt täglicher Admin-Zuordnung.
- Baustellen-Team/QR gehört in die Baustellenansicht, nicht als eigener normaler Einstellungs-Menüpunkt.
- `Verantwortliche TBs` mit echten Namen; keine sichtbaren A/B/C/D-Gruppen.
- Helfer schreiben in den BTB ihres verantwortlichen TB.
- BTB-Identität = Team + Datum + Schicht.
- zentraler Firmenprofil-/Logo-Stand vom Server.
- persönliche E-Mail/Telefon gehören zum einzelnen Server-Benutzer und nicht zum Firmenprofil.
- merge-sicheres Archiv `TB-Helfer/<Baustelle>/...`.
- `main` stabil; große Änderungen im Feature-Branch/Draft-PR.
