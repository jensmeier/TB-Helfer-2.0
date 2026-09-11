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
- Android im Feature-Branch: **`versionCode 200023`**, `versionName 1.3`.

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
- **Seit dem letzten produktiven Test wurden keine weiteren NAS-/DB-Änderungen durchgeführt.**

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
- `Nutzer / Firma` ist im Android-UI sichtbar in **Mein Profil** und **Firma** getrennt.
- Name im persönlichen Profil bleibt serverseitige Identität/Anzeige und wird in dieser Maske nur angezeigt.
- persönliche Telefonnummer und persönliche E-Mail sind editierbar.
- Firmenfelder und Firmenlogo bleiben serverseitiger zentraler Read-only-Stand in Android.

Wichtig: **Schema 11 ist nur im Feature-Branch vorbereitet und wurde auf dem NAS noch nicht ausgeführt. Produktiv bleibt Schema 10.** Vor Schema-11-Migration wieder Backup, Hash-Prüfung der SQL-Dateien und anschließende read-only Verifikation durchführen.

## Firmen / Teams / Einladungen – praktisch bestätigt
- Testfirma `Gtu`: 2 Nutzer, 2 aktive Geräte.
- Firmenprofil/Logo funktioniert produktiv.
- Helfer-/TB-Einladung funktioniert praktisch.
- Testbaustelle `Immenstadt`: mindestens eine praktisch getestete TB-Gruppe mit mehreren Nutzern.
- Teams ausschließlich innerhalb derselben Firma.
- Ersteller der Server-Baustelle ist erster verantwortlicher TB.
- weitere verantwortliche TBs treten per sicherer Baustelleneinladung bei.
- Helfer werden in die Gruppe ihres verantwortlichen TB eingeladen und können `area_scope` erhalten.
- keine sichtbaren A/B/C/D-Gruppenbezeichnungen.
- Aktivierungs-QR und Baustellen-Einladungs-QR bleiben getrennt.
- Namen sind nie technische Schlüssel; UUIDs/Team-IDs bleiben maßgeblich.

## BTB – aktuelle verbindliche Identität
Die frühere technische Identität `Team + Datum + TAG/NACHT` ist **abgelöst**.

Für neue BTBs gilt:
- ein BTB wird nur bewusst über **`Neuer BTB`** angelegt.
- das bloße Öffnen erzeugt keinen leeren Entwurf.
- jeder bewusst angelegte BTB erhält eine **stabile eigene `btbId` (UUID)**.
- `datumIso`, Arbeitsbeginn, Arbeitsende und `TAG/NACHT` sind Attribute/Anzeige und **kein technischer Schlüssel** mehr.
- mehrere echte BTBs desselben TB-Teams am selben Kalendertag sind erlaubt, z. B. `07:00–12:00` und `18:00–22:00`.
- ein BTB kann sauber über Mitternacht laufen; dafür gibt es zusätzlich `endeDatumIso`.
- Helfer und verantwortlicher TB desselben Teams bearbeiten dieselbe stabile BTB-ID.
- andere verantwortliche TB-Teams derselben Baustelle bleiben strikt getrennt.
- historische `bautagebuch_tag.json`, `bautagebuch_nacht.json` und sehr alte `bautagebuch.json` bleiben lesbar und bearbeitbar.

Wesentliche Dateien:
- `BautagebuchModels.kt`
- `BautagebuchSpeicher.kt`
- `BtbVerlaufFragment.kt`
- `BtbDokumentIdentitaet.kt`
- `BtbDokumentVersionierung.kt`

## BTB Abschluss / Dokumentversionen
Aktueller Branchstand:
- erster erfolgreicher Abschluss = **V1**.
- nach späterer Änderung und erneutem Abschluss entstehen **V2, V3, ...**.
- frühere Abschlussdateien werden nicht überschrieben.
- Dateinamen enthalten bei neuen BTBs einen stabilen BTB-ID-Token und die Dokumentversion; mehrere BTBs desselben Tages können dadurch nicht kollidieren.
- der Vergleich für die rote Änderungsmarkierung läuft gegen die **vorherige bestätigte Abschlussversion**, nicht dauerhaft gegen eine einmalige alte Tagesbaseline.
- dafür existiert `BtbDokumentVersionierung.kt` mit einem persistenten Pending-Snapshot:
  1. vor PDF-Erzeugung Snapshot + erwarteter Vn-Dateiname merken,
  2. nur wenn diese Abschluss-PDF tatsächlich existiert, Abschluss bestätigen,
  3. Snapshot wird dann neue Vergleichsbasis,
  4. Entwurf/Fehler/Abbruch kann die Abschlussbasis nicht verschieben.
- Metafelder (`istFertig`, `dokumentVersion`, Abschlusszeit/-benutzer, Änderungszeit) lösen selbst keine fachliche Änderungsmarkierung aus.
- der Verlauf zeigt `Abgeschlossen · Vn` bzw. einen geänderten Zustand und bleibt erneut bearbeitbar.
- Unit-Tests für stabile BTB-Dateinamen und Versionsfolge wurden ergänzt.

Noch offen bei BTB:
- vollständiger lokaler Android-Build des aktuellen Stands.
- PDF/DOCX-Versionierung auf einem echten Gerät praktisch prüfen: V1 erzeugen → ändern → V2 erzeugen → rote Markierung und beide Dateien prüfen.
- Collaboration-Servermodell später vollständig von den historischen Tages-/Schichtschlüsseln auf die stabile `btbId` ausrichten; produktiver Server bleibt derzeit unverändert.

## Praktischer BTB-Zwei-Geräte-Befund vor dem Multi-BTB-Umbau
Produktive DB-Prüfung für `Immenstadt`:
- mehrere BTB-Einträge lagen in `tb.collab_btb_entries`.
- darunter `2026-09-10:NACHT` mit der auf dem Poco geschriebenen Testnotiz.
- geprüfte BTBs gehörten zur TB-Gruppe von `jens poco`.
- damit war **Poco → API → PostgreSQL** für BTB bestätigt.
- `tb.collab_device_state`: Pixel und Poco jeweils `handy_cursor=6`, `server_cursor=6`.
- damit hatte auch das Pixel den Collaboration-Feed abgeholt.

Dieser Befund bleibt historisch wichtig, beschreibt aber noch das alte Tages-/Schicht-Servermodell und ist **nicht** die neue Android-BTB-Identität.

## Android 200021–200023 – Baustellen-/QR-/Sync-UX
Direkter Baustellen-/QR-Ablauf:
- eine normale Einstellungsseite `Baustellen-Synchronisation` ist weiterhin **nicht** vorgesehen.
- in der normalen Baustellenliste gibt es direkt **`QR scannen`**.
- der QR enthält die serverseitig festgelegte Rolle (`HELPER` oder `LEAD`); der Empfänger wählt sie nicht erneut.
- `Nutzer hinzufügen` bleibt im Langdruck-Menü der Baustelle und bietet direkt:
  - `Nutzer (verantwortlichen TB) hinzufügen`
  - `Helfer hinzufügen`
- für Helfer bleibt die Bereichsfreigabe (`Gesamte Baustelle` oder Bereich/km) erhalten.
- QR kann direkt angezeigt und über **`Teilen / senden`** weitergegeben werden.
- nach Beitritt werden Collaboration-Daten synchronisiert und der lokale Root aufgebaut/gebunden.
- eine bereits aktive andere Baustelle wird nicht ungefragt ersetzt; **Öffnen / Später** bleibt die Bedienregel.
- bei gleichnamiger lokaler Hauptbaustelle wird nicht blind per Name zusammengeführt; es erscheint eine bewusste lokale Auswahl.

Team-/Sync-Anzeige auf Baustellenkarten:
- `TeamDisplayCache` speichert je Server-Baustelle:
  - Anzahl verantwortliche TBs,
  - Anzahl Helfer,
  - letzten erfolgreichen Baustellen-Sync,
  - Zuordnung lokaler Root ↔ Server-Worksite.
- `BaustellenKnotenAdapter` zeigt in der Hauptliste z. B.:
  - `2 TB · 4 Helfer · Zuletzt synchronisiert: heute 14:32`
  - ohne bekannten Serverstand: `Noch nicht mit Server synchronisiert`.
- `SyncWorker` frischt Teamdaten nach erfolgreichem Hintergrundsync nach.
- direkter Provision-/QR-Beitritt schreibt die lokale Root-Zuordnung und Teamdaten ebenfalls in den Cache.

## Android 200023 – allgemeine Synchronisation
Neu sichtbar und gerätelokal einstellbar:
- **Automatischer Sync:**
  - `Manuell`
  - `Stündlich`
  - `Täglich`
- **Erlaubtes Netz:**
  - `Nur WLAN`
  - `Nur Mobilfunk`
  - `WLAN + Mobilfunk`

Implementierung:
- `SyncEinstellungenSpeicher.kt` persistiert Zeitplan + Netzregel.
- `SyncWorker.schedule()` erstellt/entfernt den periodischen WorkManager-Job entsprechend dem Zeitplan.
- der Hintergrund-Worker prüft zusätzlich die tatsächlich aktive Transportart.
- `SyncStatusSpeicher.kt` hält den letzten erfolgreichen allgemeinen Server-Sync gerätelokal.
- Einstellungen enthalten jetzt den Button **`Synchronisation`** mit aktuellem Zeitplan, Netzregel und letztem Sync-Stand.
- Startmenüeintrag **`Daten vom NAS`** wurde in **`Mit NAS synchronisieren`** umbenannt und zeigt den letzten bekannten Sync-Stand.
- der Menüstatus wird beim Wiederaufrufen des Startmenüs aktualisiert.

Wichtig/offen:
- `SyncKatalogFragment` führt beim bewussten manuellen Aufruf weiterhin primär Katalog-/Offline-Inkrementalsync aus; dort ist der komplette Collaboration-Ablauf aus `SyncWorker` noch nicht zu einer gemeinsamen manuellen Gesamt-Sync-Routine refaktoriert.
- deshalb darf erst nach diesem Umbau behauptet werden, dass **ein einzelner manueller Button** immer Katalog + Baustellenstruktur + BTB + Mängel + Abnahmen + alle Attachments in genau einem gemeinsamen Lauf synchronisiert.
- die neue Netzregel blockiert den bewusst geöffneten manuellen Sync nicht still; sie steuert derzeit den automatischen Hintergrundpfad.

## Sync-Matrix aus dem Praxismodell
### Verantwortlicher TB A ↔ verantwortlicher TB B derselben Baustelle
- Baustelle inkl. freigegebener Unterbaustellen: gemeinsamer Baustellenstand.
- benötigte Strecken-/Arbeitsdaten: über NAS/Server synchronisiert, Originaldaten bleiben unverändert.
- Mängel/Schäden und Abnahmen: baustellenweit innerhalb der Berechtigung gemeinsam.
- dazugehörige Bilder: gemeinsam innerhalb derselben Berechtigung.
- **BTBs verschiedener verantwortlicher TB-Teams werden niemals vermischt.**

### Verantwortlicher TB ↔ Helfer desselben TB-Teams
- Baustelle inkl. freigegebener Unterbaustellen: Helfer erhält den benötigten/berechtigten Stand.
- benötigte Streckendaten: selektiv nach Berechtigung/Arbeitsbereich.
- BTB: **gemeinsam in beide Richtungen innerhalb desselben TB-Teams und derselben `btbId`**.
- Mängel/Schäden und Abnahmen: innerhalb der Helfer-Berechtigung synchronisiert.
- Helferbilder gehen zum verantwortlichen TB/Server; Download anderer Bilder zum Helfer nur soweit für Bereich/Arbeitsablauf nötig.

## Nutzer/Helfer aus Baustelle entfernen – noch offen
Der gewünschte Bedienpunkt `Nutzer/Helfer entfernen` soll direkt bei der Baustelle liegen, nicht in einem separaten Sync-Menü.

Aktueller Serverstand bietet Einladen, Einlösen und Einladung widerrufen, aber noch **keinen fertigen mobilen Endpunkt zum Entfernen einer bestehenden Baustellen-Mitgliedschaft**. Deshalb wurde kein gefährliches rein lokales Löschen eingebaut.

Nächster Serverpunkt:
- Mitgliedschaft serverseitig entfernen/widerrufen.
- Helfer: verantwortlicher TB darf Helfer seiner TB-Gruppe entfernen.
- Verhalten für das Entfernen eines anderen verantwortlichen TBs sicher umsetzen; vorhandene historische BTB-/Mängel-/Abnahmedaten dürfen nicht gelöscht werden.
- danach direkte Auswahl `Nutzer/Helfer entfernen` im selben Baustellen-Menü.

## Noch fehlender Baustellenstruktur-Sync
Der Root-Bootstrap löst weiterhin nur die Hauptbaustelle, noch nicht den kompletten lokalen Baustellenbaum.

Aktueller Befund:
- Poco hat lokal Hauptbaustelle `Immenstadt` mit Unterbaustelle `5362-1`.
- `5362-1` enthält Strecke/KM.
- bisheriger Self-Service-Endpunkt speichert nur die Hauptbaustelle; Unterknoten werden nicht serverseitig gespeichert.
- deshalb kann ein anderes Gerät `5362-1` noch nicht automatisch nachbauen.

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
Für neue Multi-BTB-Berichte gilt:
- PDF/DOCX-Namen verwenden stabile BTB-Identität + Dokumentversion und dürfen bei mehreren BTBs desselben Tages nicht kollidieren.
- frühere V1/V2/... bleiben erhalten.
- historische alte PDFs werden nicht zwangsumbenannt.
- langfristiges Laptop-Archiv bleibt `TB-Helfer/<Baustelle>/...`.
- mehrere Geräte-ZIPs müssen merge-sicher in denselben Zielordner entpackbar sein.
- Bilder/Anlagen erhalten stabile/inhaltsbasierte Kennungen.
- `Bilderliste_gesamt.xlsx` + CSV sowie Mängel-/Abnahme-Gesamtlisten sind im Feature-Branch vorhanden; echter Mehrgeräte-Test steht aus.

Noch zu prüfen/vereinheitlichen:
- einzelne ältere Exportpfade in `BtbExportPaket` verwenden noch Tag-/Nacht-orientierte Anzeigenamen; sie dürfen bei neuen Multi-BTB-Exporten keine unterschiedlichen `btbId`-Dokumente auf denselben ZIP-Pfad reduzieren.

## Android-Buildstatus / Tests
Praktisch bestätigt:
- Android 200019 wurde mehrfach lokal erfolgreich gebaut und installiert.
- Android 200020: Root-Bootstrap `Immenstadt` auf Pixel praktisch bestätigt.

Aktueller Branch:
- Android **200023** ist noch **nicht lokal vollständig kompiliert/installiert/getestet**.
- neue Unit-Testquellen decken stabile BTB-Dateinamen und V1/V2/V3-Auswahlregeln ab; sie konnten in dieser Sitzung nicht durch einen echten Android-Gradle-Lauf bestätigt werden.
- GitHub Actions für aktuelle Heads schlagen weiterhin bereits vor dem ersten Workflow-Schritt fehl (`steps=[]`, kein nutzbarer Runnerlauf). Das ist kein fachlicher Buildfehler, aber auch **kein bestandener Build**.
- lokale Android-Studio-/Gradle-Builds bleiben für diesen Stand erforderlich.
- Compilerwarnungen bleiben im separaten Backlog.

## Betroffene Dateien des aktuellen Android-Blocks
Wesentlich geändert/neu:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchModels.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentIdentitaet.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentVersionierung.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BtbVerlaufFragment.kt`
- `app/src/main/java/de/tbhelfer/server/TeamDisplayCache.kt`
- `app/src/main/java/de/tbhelfer/server/SyncStatusSpeicher.kt`
- `app/src/main/java/de/tbhelfer/server/SyncWorker.kt`
- `app/src/main/java/de/tbhelfer/einstellungen/SyncEinstellungenSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/einstellungen/EinstellungenFragment.kt`
- `app/src/main/res/layout/fragment_einstellungen.xml`
- `app/src/main/java/de/tbhelfer/ui/start/StartmenueFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BaustellenTeamDirekt.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BaustellenFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BaustellenKnotenAdapter.kt`
- `app/src/test/java/de/tbhelfer/ui/bautagebuch/BtbDokumentIdentitaetTest.kt`
- `app/src/test/java/de/tbhelfer/ui/bautagebuch/BtbDokumentVersionierungTest.kt`
- `app/build.gradle.kts`

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
1. **Produktiven NAS-Stand auf Schema 10/API 0.7 lassen.** Keine produktive Migration nur für den Android-Build.
2. Android **200023 lokal kompilieren** (`assembleDebug` + Unit-Tests; anschließend nach Möglichkeit Release/Lint).
3. Compiler-/Ressourcenfehler aus dem 200023-Build unmittelbar im Feature-Branch beheben und diese Datei aktualisieren.
4. auf Pixel/Poco als Update installieren, **keine App-Daten löschen**.
5. BTB praktisch testen:
   - zwei echte BTBs am selben Tag anlegen,
   - V1 abschließen,
   - einen Bericht ändern,
   - V2 abschließen,
   - prüfen: V1 bleibt, V2 neu, Änderungen rot, Verlauf korrekt.
6. Sync-UX praktisch prüfen:
   - Einstellungen `Manuell / Stündlich / Täglich`,
   - Netzregel,
   - Startmenü `Mit NAS synchronisieren`,
   - Baustellenkarte Teamzahl + letzter Sync.
7. manuellen NAS-Sync auf eine gemeinsame komplette Routine mit dem Collaboration-Ablauf des `SyncWorker` vereinheitlichen.
8. danach Multi-BTB-Syncservermodell auf stabile `btbId` anpassen, ohne Altbestände zu zerstören.
9. danach Unterbaustellen-/Baustellenstruktur-Sync für `5362-1` bauen.
10. danach serverseitige Mitgliedschafts-Entfernung und direkte `Nutzer/Helfer entfernen`-Aktion ergänzen.
11. anschließend Mängel/Abnahmen/Bilder/Benachrichtigungen, Offline/409/Tombstones/Netzabbruch und Merge-Export mit beiden Geräten testen.
12. Schema 11 erst nach eigenem Backup/Hashprüfung/Verifikation produktiv migrieren, wenn der Nutzerprofilblock praktisch dran ist.
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
- Baustellen-Team/QR gehört in die Baustellenansicht, nicht als eigener normaler `Baustellen-Synchronisation`-Menüpunkt.
- allgemeine gerätelokale Sync-Einstellungen dürfen unter `Einstellungen → Synchronisation` liegen.
- `Verantwortliche TBs` mit echten Namen; keine sichtbaren A/B/C/D-Gruppen.
- Helfer schreiben in den BTB ihres verantwortlichen TB.
- neue BTB-Identität = **stabile `btbId`**, nicht `Datum + TAG/NACHT`.
- mehrere BTBs desselben Tages sind erlaubt.
- zentraler Firmenprofil-/Logo-Stand vom Server.
- persönliche E-Mail/Telefon gehören zum einzelnen Server-Benutzer und nicht zum Firmenprofil.
- merge-sicheres Archiv `TB-Helfer/<Baustelle>/...`.
- `main` stabil; große Änderungen im Feature-Branch/Draft-PR.
