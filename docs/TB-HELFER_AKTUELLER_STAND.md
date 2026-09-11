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
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- `compose.yaml` baut/mountet den produktiven Server aus diesem Stage-Ordner.
- produktive Secrets bleiben ausschließlich in `./server/.env`.
- letzter bestätigter Stand: PostgreSQL healthy, API/cloudflared gestartet, **72 OpenAPI-Pfade**, `DB_TEST=OK`, Schema 10.
- seit diesem bestätigten Stand wurden in der aktuellen Entwicklungsrunde **keine NAS-/DB-Änderungen** durchgeführt.

Wichtig: Der Feature-Branch enthält inzwischen neueren Servercode, unter anderem die stabile Multi-BTB-ID in `server/app_parts/15_part.py`. Dieser Code entstand **nach** dem letzten produktiven Server-Rollout und ist deshalb noch nicht produktiv bestätigt.

## Backup / Migration 010
Produktives Pre-Schema-10-Backup:
- `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`
- mit `pg_restore -l` geprüft: **`BACKUP_PRUEFUNG=OK`**.

Verifizierte Git-Blob-SHAs für den damaligen Schema-10-Rollout:
- `010_worksite_invitations_company_profile.sql` → `d3b78c723de0c5ee0b2a0121957d81513e240a86`
- `02_verify_schema10.sql` → `100b1214a81990ed84a187b243f94b745ee742ab`

Migration 010 lief mit `ON_ERROR_STOP`, endete mit `INSERT 0 1` und `COMMIT`; Verifikation endete mit **`SCHEMA_10_OK`**.

## Persönliches Nutzerprofil / Firma – Schema 11 vorbereitet, nicht produktiv
Verbindliche Trennung:
- **Firma** zentral: Firmenname, Anschrift, Firmentelefon, Firmen-E-Mail, Firmenlogo.
- **Mein Profil** pro Server-Benutzer: Name, persönliche Telefonnummer, persönliche E-Mail-Adresse.
- persönliche Kontaktdaten dürfen für Benutzer derselben Firma sichtbar sein, aber Firmen- und persönliche Kontaktdaten bleiben getrennt.

Branch-Vorbereitung:
- `server/migrations/011_user_contact_profile.sql`
  - `tb.users.phone`
  - `tb.users.profile_version`
  - Schema 11 `011_user_contact_profile`
- `server/checks/05_verify_schema11.sql` → erwartet `SCHEMA_11_OK`
- `server/checks/06_verify_schema11_source.py`
- `server/app_parts/18_part.py`
  - `GET /api/v1/user/profile`
  - `PUT /api/v1/user/profile`
- Android `UserProfileApi.kt`
- separater Offline-Cache `PersoenlichesNutzerProfilSpeicher`
- Android-UI `Nutzer / Firma` getrennt in **Mein Profil** / **Firma**.

**Schema 11 wurde noch nicht auf dem NAS ausgeführt. Produktiv bleibt Schema 10.**

## Firmen / Teams / Einladungen
Praktisch bereits bestätigt:
- Testfirma `Gtu`: zwei Nutzer / zwei aktive Testgeräte.
- Firmenprofil/Logo funktioniert produktiv.
- Helfer-/TB-Einladung funktioniert praktisch.
- Teams ausschließlich innerhalb derselben Firma.
- Ersteller einer Server-Baustelle ist erster verantwortlicher TB.
- weitere verantwortliche TBs und Helfer treten per sicherer Baustelleneinladung bei.
- Helfer können `area_scope` erhalten.
- Aktivierungs-QR und Baustellen-Einladungs-QR sind getrennte Abläufe.
- technische Beziehungen verwenden UUIDs/Team-IDs, nie sichtbare Namen.

Android:
- `QR scannen` liegt direkt in der Baustellenliste.
- `Nutzer hinzufügen` liegt direkt im Baustellen-Langdruckmenü.
- Auswahl: verantwortlicher TB oder Helfer; Helfer-Bereich kann eingeschränkt werden.
- Rolle kommt aus der serverseitigen Einladung; Empfänger wählt sie nicht erneut.
- gleichnamige lokale Baustellen werden nicht blind zusammengeführt.

## Baustellenkarte / Team- und Sync-Status
Im Branch umgesetzt:
- `TeamDisplayCache` hält je Server-Baustelle:
  - lokalen Root ↔ Server-Worksite,
  - Anzahl verantwortliche TBs,
  - Anzahl Helfer,
  - letzten erfolgreichen Baustellen-Sync.
- Hauptbaustellenkarte kann z. B. anzeigen:
  - `2 TB · 4 Helfer · Zuletzt synchronisiert: heute 14:32`
  - oder `Noch nicht mit Server synchronisiert`.
- Provisionierung, QR-Beitritt und Hintergrundsync aktualisieren diesen Cache.

Noch nicht lokal mit Android 200023 praktisch geprüft.

# BTB

## Neue verbindliche BTB-Identität
Die frühere technische Identität `Team + Datum + TAG/NACHT` ist abgelöst.

Für neue Berichte gilt:
- ein BTB entsteht **nur bewusst über `Neuer BTB`**.
- bloßes Öffnen des Bautagebuchs erzeugt keinen leeren Entwurf.
- jeder neue BTB erhält eine stabile **`btbId` (UUID)**.
- Datum, Arbeitsbeginn/-ende und TAG/NACHT sind Attribute/Anzeige, kein technischer Schlüssel.
- mehrere BTBs desselben TB-Teams am selben Kalendertag sind ausdrücklich erlaubt.
- Helfer und verantwortlicher TB desselben Teams bearbeiten dieselbe `btbId`.
- andere verantwortliche TB-Teams derselben Baustelle bleiben getrennt.
- historische `bautagebuch_tag.json`, `bautagebuch_nacht.json` und `bautagebuch.json` bleiben lesbar.

Lokaler Speicher:
- neue Datei: `btb_<btbId>.json`
- `.active_btb.json` hält den aktiv geöffneten Bericht.
- `neueBtbIdVorbereiten()` erzeugt nur Identität/Pointer; erst ein Speichervorgang legt den fachlichen Bericht an.
- `verlaufFuerBaustelle()` listet tatsächlich vorhandene moderne + historische BTBs.

## BTB-Abschluss / PDF-Versionierung
Im Branch umgesetzt:
- erster erfolgreicher Abschluss = **V1**.
- spätere Änderung + erneuter Abschluss = **V2, V3, ...**.
- frühere Abschlussdateien bleiben erhalten.
- neue Dateinamen enthalten stabile BTB-Identität + Dokumentversion und dürfen bei mehreren BTBs desselben Tages nicht kollidieren.
- neue Version vergleicht gegen die vorherige **bestätigte** Abschlussversion.
- Entwurf/abgebrochener PDF-Lauf verschiebt die Vergleichsbasis nicht.
- Änderungen gegenüber der vorherigen Abschlussversion können rot markiert werden.
- Verlauf zeigt `Abgeschlossen · Vn` bzw. `Geändert seit Vn · nächster Abschluss Vn+1`.

Wesentliche Dateien:
- `BautagebuchModels.kt`
- `BautagebuchSpeicher.kt`
- `BtbDokumentIdentitaet.kt`
- `BtbDokumentVersionierung.kt`
- `BtbVerlaufFragment.kt`
- Unit-Testquellen `BtbDokumentIdentitaetTest.kt` / `BtbDokumentVersionierungTest.kt`.

Diese Tests sind im Quellstand vorhanden, wurden für Android 200023 in dieser Sitzung aber **nicht durch einen echten lokalen Gradle-Lauf bestätigt**.

## Multi-BTB auf dem Server
Datenbankschema 9/10 ist bereits grundsätzlich Multi-BTB-tauglich:
- `tb.collab_btb_entries.client_entry_id` ist der Client-Schlüssel.
- eindeutige DB-Regel: **`UNIQUE (group_id, client_entry_id)`**.
- es existiert keine gewollte Eindeutigkeit auf Datum + Schicht.
- deshalb ist für stabile `btbId` **keine eigene DB-Migration erforderlich**.

Feature-Branch-Server:
- `server/app_parts/15_part.py` verwendet `client_entity_key` als technische BTB-ID.
- leerer Key → `BTB_ID_REQUIRED`.
- abweichender Payload-`btbId` → `BTB_ID_MISMATCH`.
- Payload erhält denselben `btbId`.
- parallele Helferänderungen werden versionsgeschützt zusammengeführt.
- Altbestände mit historischen Keys bleiben kompatibel.

Neu vorbereitete Prüfungen:
- `server/checks/07_verify_multi_btb_schema10.sql`
  - **read-only**
  - kontrolliert vorhandenes Schema 10 und `UNIQUE (group_id, client_entry_id)`
  - lehnt eine Datum-/Schicht-Unique-Regel ab
  - erwarteter Marker: `MULTI_BTB_SCHEMA10_OK`
- `server/checks/08_verify_multi_btb_source.py`
  - prüft DB-/Server-/Android-Quellvertrag für stabile `btbId`
  - kontrolliert zusätzlich, dass Android die für die WLAN-/Mobilfunkprüfung nötige `ACCESS_NETWORK_STATE`-Berechtigung besitzt
  - erwarteter Marker: `MULTI_BTB_SOURCE_OK`.

Diese beiden neuen Prüfungen sind **vorbereitet, aber noch nicht auf dem NAS bzw. in einem lokalen Checkout ausgeführt**.

Der stabile Multi-BTB-Servercode wurde im Branch am 11.09.2026 nach dem letzten produktiven Rollout ergänzt. Vor dem echten Pixel/Poco-Multi-BTB-Test braucht der produktive Schema-10-Server daher später ein kontrolliertes **Server-Code-Update**, aber keine Multi-BTB-DB-Migration.

## BTB-Bilder / lokale Foto-Hilfsdaten
Server-Medienzuordnung:
- `CollabLocalBridge` verwendet `btbId` als `client_entity_key`.
- `BtbTeamMediaSync` verwendet denselben stabilen Key für BTB-Attachments.
- der frühere Medien-Key `Datum:TAG/NACHT` ist im Branch entfernt.

Lokale Foto-Hilfsspeicher sind jetzt ebenfalls pro BTB isoliert:
- `BtbPhotoInstanzKey.kt` liest `.active_btb.json` und akzeptiert den Pointer nur, wenn **Baustelle, Datum und Schicht** zum angefragten Bericht passen.
- `BtbPhotoSelectionStore.kt`
- `BtbPhotoTextOverrideStore.kt`
- `BtbPhotoStartOverrideStore.kt`
- `BtbPhotoKmOverrideStore.kt`

Für neue Berichte enthalten deren Dateinamen einen `_btb_<btbId-token>_`-Teil. Dadurch können Auswahl/Text/Start/KM eines zweiten BTBs am gleichen Tag den ersten Bericht nicht mehr über denselben Datum-/Schicht-/Zeit-Key überschreiben oder beim `Neuer BTB`-Start löschen.

Altbestände ohne passende stabile `btbId` verwenden weiterhin das historische Dateischema.

Statischer Prüfer vorbereitet:
- `server/checks/09_verify_android_multi_btb_photo_state_source.py`
- kontrolliert zusätzlich, dass der aktive Pointer nur für das passende Datum + die passende Schicht verwendet wird
- erwarteter Marker: `ANDROID_MULTI_BTB_PHOTO_STATE_OK`.
- auch dieser Check wurde noch nicht tatsächlich ausgeführt.

## Bekannte noch offene BTB-UI-Punkte
### Blauer aktueller-BTB-Schnellwechsel
`BautagebuchFragment.kt` enthält noch einen **alten 7-Tage-Dialog**:
- erzeugt künstlich TAG/NACHT-Zeilen für jeden Tag,
- verwendet Zustand `NEW_EMPTY`,
- kann `Neu (leer)` anzeigen,
- wechselt primär über Datum + Schicht statt über den ausgewählten stabilen `btbId`.

Das widerspricht der neuen Regel **nur tatsächlich angelegte BTBs anzeigen** und ist bei mehreren BTBs desselben Tages mehrdeutig.

Vor dem Multi-BTB-Gerätetest muss dieser Schnellwechsel auf `verlaufFuerBaustelle()` umgestellt werden:
- nur existierende BTBs,
- jeder Bericht einzeln mit Datum + Arbeitszeit + Status,
- bei modernen Berichten konkrete `btbId` aktivieren,
- keine künstlichen Tag-/Nacht-Platzhalter.

Die getrennte Seite `BtbVerlaufFragment` arbeitet bereits nach diesem Prinzip und kann als Vorlage dienen.

### Datums-/Schichtdialog
Auch `showSchichtTypDialog()` enthält noch alte `Neu (leer)`-Annahmen. Neuer Bericht soll ausschließlich über den grünen `+` / `Neuer BTB` entstehen. Dieser Pfad muss beim Schnellwechsel-Umbau mit geprüft werden.

### BTB über Mitternacht
Die Zeitlogik erkennt z. B. `22:00–06:00` als Nacht/über Mitternacht. Beim Start eines neuen BTB wird `endeDatumIso` derzeit jedoch nicht an jeder Stelle ausdrücklich gesetzt. Vor dem finalen Geräte-/Exporttest muss geprüft werden, dass PDF, Verlauf und Export den Endtag überall korrekt erhalten.

# Synchronisation Android 200023

## Allgemeine Einstellungen
Gerätelokal im Branch:
- Zeitplan:
  - `Manuell`
  - `Stündlich`
  - `Täglich`
- Netz:
  - `Nur WLAN`
  - `Nur Mobilfunk`
  - `WLAN + Mobilfunk`.

`SyncEinstellungenSpeicher.kt` persistiert die Auswahl.
`SyncWorker.schedule()` erstellt/entfernt den periodischen WorkManager-Job.
Der automatische Worker prüft die aktive Transportart.

Beim statischen Review wurde ein echter Laufzeitfehler behoben:
- `AndroidManifest.xml` besitzt jetzt `android.permission.ACCESS_NETWORK_STATE`.
- notwendig für `ConnectivityManager.activeNetwork/getNetworkCapabilities` bei der WLAN-/Mobilfunk-Prüfung.

## Letzter Sync / Startmenü
- `SyncStatusSpeicher.kt` hält den letzten erfolgreichen allgemeinen Server-Sync.
- Einstellungen zeigen Zeitplan, Netzregel und letzten Sync-Stand.
- Startmenüeintrag heißt **`Mit NAS synchronisieren`** statt `Daten vom NAS`.
- letzter bekannter Sync-Stand wird dort angezeigt und beim Wiederaufrufen aktualisiert.

## Manueller vollständiger NAS-Sync
`SyncWorker` besitzt einen manuellen One-Time-Modus.
Der bewusste manuelle Lauf nutzt dieselbe vollständige Pipeline wie der Hintergrundsync:
1. Katalog/Offline-Stand
2. inkrementelle Queue
3. Baustellen-/Team-/Collaboration-Feed
4. BTB-Text
5. BTB-Bilder
6. Mängel / Abnahmen
7. Attachments
8. abschließender Pull/ACK + Team-/Sync-Status.

`SyncKatalogFragment` startet nach seinem sichtbaren Katalog-/Konflikt-Vorlauf diesen manuellen Worker und zeigt dessen Status.

Der manuelle Lauf ignoriert bewusst den automatischen Zeitplan und die WLAN/Mobilfunk-Präferenz, weil der Nutzer ihn ausdrücklich gestartet hat; irgendeine Internetverbindung bleibt erforderlich.

Der sichtbare Katalog-Vorlauf und der Worker führen den allgemeinen Inkrementalsync derzeit einmal redundant aus. Die Operationen sind idempotent; später kann das nur zur Laufzeitoptimierung zusammengezogen werden.

## WorkManager / Android-Grundlage
- WorkManager: `2.10.0`.
- Android 200023 verwendet dafür passende One-Time-/Periodic-Work-APIs.
- Android 200023 wurde **noch nicht lokal vollständig gebaut/installiert/getestet**.

# Collaboration-Matrix

## Verantwortlicher TB ↔ verantwortlicher TB derselben Baustelle
Gemeinsam innerhalb derselben Baustelle/Berechtigung:
- Baustellenstand / freigegebene Struktur,
- benötigte Strecken-/Arbeitsdaten,
- Mängel/Schäden,
- Abnahmen,
- dazugehörige Bilder.

Nicht gemeinsam:
- BTBs verschiedener verantwortlicher TB-Teams werden niemals vermischt.

## Verantwortlicher TB ↔ Helfer desselben TB-Teams
- Baustelle/Unterbaustellen nur im berechtigten Bereich.
- Streckendaten selektiv nach Bereich.
- BTB gemeinsam in beide Richtungen innerhalb derselben `btbId`.
- Mängel/Abnahmen innerhalb der Helfer-Berechtigung.
- Helferbilder gehen zum Server/verantwortlichen TB; Download anderer Bilder nur soweit benötigt/berechtigt.

# Noch fehlende größere Server-/Sync-Blöcke

## Baustellenstruktur-Sync
Root-Bootstrap existiert, aber der vollständige lokale Baustellenbaum fehlt noch serverseitig.
Praktischer Befund:
- Poco: Hauptbaustelle `Immenstadt` + Unterbaustelle `5362-1` mit Strecke/KM.
- Server-Self-Service speichert bislang nur den Root.
- anderes Gerät kann `5362-1` deshalb noch nicht automatisch nachbauen.

Ziel:
- eigener serverseitiger Struktur-Sync,
- stabile IDs statt Namens-Tricks,
- selektiv nach Berechtigung/Bereich,
- Original-Vermessungsdaten getrennt und unverändert.

## Nutzer/Helfer entfernen / Baustelle verlassen
Noch kein fertiger mobiler serverautoritärer Endpunkt.
Deshalb bewusst **kein** rein lokales Entfernen gebaut.
Ziel:
- Mitgliedschaft serverseitig widerrufen/verlassen,
- historische Beiträge erhalten,
- heruntergeladene Daten danach lokal nur noch lesbar,
- keine weitere Bearbeitung/Synchronisation.

# Mängel / Abnahmen / Benachrichtigungen
- Mängel/Abnahmen baustellenweit zwischen berechtigten verantwortlichen TBs derselben Baustelle.
- Helfer nur im freigegebenen Bereich.
- Bilder/Attachments folgen derselben Berechtigung.
- endgültige Status bleiben endgültig und werden durch älteren Sync nicht wieder geöffnet.
- Benachrichtigungsmodi pro Gerät: Text + Ton / nur Ton / aus.

# Server-Speicheroptionen
Pro Benutzer vorgesehen/vorhanden:
- Bilder speichern
- BTB speichern
- Mängel speichern
- Abnahmen speichern
- Alles speichern.

Nicht dauerhaft gespeicherte Daten dürfen temporär für die Synchronisation auf dem Server liegen.

# Bahnhofsdaten – nicht neu erfinden
Verbindliche vorhandene Bestände:
- `app/src/main/assets/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
- `app/src/main/assets/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`
- `app/src/main/java/de/tbhelfer/util/BahnhofSucheUtil.kt`.

# Export
Für neue Multi-BTB-Berichte:
- PDF/DOCX-Namen müssen stabile BTB-ID + Dokumentversion unterscheiden.
- V1/V2/... bleiben erhalten.
- historischer Altbestand wird nicht zwangsumbenannt.
- Laptop-Archiv bleibt `TB-Helfer/<Baustelle>/...`.
- mehrere Geräte-ZIPs müssen merge-sicher zusammenführbar sein.
- Bilder/Anlagen benötigen stabile eindeutige Kennungen.

Noch zu prüfen:
- einzelne ältere Pfade in `BtbExportPaket` sind noch Tag-/Nacht-orientiert und dürfen zwei unterschiedliche `btbId` nicht auf denselben ZIP-Zielpfad reduzieren.

# Vermessung / Streckendaten – eigener offener Hauptblock
Vorhanden sind u. a. lokale TRA/GRA/KOR/KF/Absteckdaten, Trassenachsen, KM-Punkte, KM-Anker, Korrekturen, GPS-Näheberechnung, Trassengrafik und allgemeiner Strecken-/Bahnhofs-/Abschnitt-Sync.

Noch offen:
- Original vs. bearbeitete Ebene strikt trennen.
- Originaldateien unverändert dauerhaft auf NAS.
- berechnete/bearbeitete Abschnitte getrennt versionieren.
- selektiver Download nach Bahnhof/Gleis/Weiche/DKW/KM-Bereich.
- relevante Originalformate serverseitig katalogisieren.
- echte TRA-Geometrie-Kilometrierung/GPS-KM-Anker/Zwischenpunkte/angrenzende Gleise/Weichen/DKW Ende-zu-Ende testen.
- nur geänderte/berechnete Abschnitte hochladen.
- 409-Konflikte für konkurrierende Abschnittsbearbeitung.

Farben:
- Grün = belastbare Originaldaten/KM
- Orange = manuell geändert/hergestellt
- Rot = TRA ohne belastbare KM
- Grau = ohne Vermessungsgeometrie
- Blau = Auswahl.

# Build-/Teststatus
Praktisch bestätigt aus älteren Builds:
- Android 200019 mehrfach lokal erfolgreich gebaut/installiert.
- Android 200020: Root-Bootstrap `Immenstadt` auf Pixel bestätigt.

Aktueller Stand:
- Android **200023** noch nicht lokal vollständig kompiliert/installiert/getestet.
- GitHub Actions liefert aktuell keinen verwertbaren Android-Build; die Runs scheitern vor dem ersten Workflow-Step (`steps=[]`).
- deshalb darf daraus weder `Build erfolgreich` noch ein Android-Quellfehler abgeleitet werden.
- lokale Android-Studio-/Gradle-Builds bleiben maßgeblich.

Vorbereitete, noch nicht ausgeführte neue Prüfungen:
- `07_verify_multi_btb_schema10.sql` → `MULTI_BTB_SCHEMA10_OK`
- `08_verify_multi_btb_source.py` → `MULTI_BTB_SOURCE_OK`
- `09_verify_android_multi_btb_photo_state_source.py` → `ANDROID_MULTI_BTB_PHOTO_STATE_OK`.

# Betroffene Dateien der letzten Entwicklungsrunde
Unter anderem:
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchModels.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentIdentitaet.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentVersionierung.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoInstanzKey.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoSelectionStore.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoTextOverrideStore.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoStartOverrideStore.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoKmOverrideStore.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BtbVerlaufFragment.kt`
- `app/src/main/java/de/tbhelfer/datenbank/CollabLocalBridge.kt`
- `app/src/main/java/de/tbhelfer/datenbank/BtbTeamMediaSync.kt`
- `app/src/main/java/de/tbhelfer/server/TeamDisplayCache.kt`
- `app/src/main/java/de/tbhelfer/server/SyncStatusSpeicher.kt`
- `app/src/main/java/de/tbhelfer/server/SyncWorker.kt`
- `app/src/main/java/de/tbhelfer/einstellungen/SyncEinstellungenSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/einstellungen/EinstellungenFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/start/StartmenueFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/sync/SyncKatalogFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BaustellenTeamDirekt.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BaustellenKnotenAdapter.kt`
- `server/app_parts/15_part.py`
- `server/checks/04_test_team_btb_merge.py`
- `server/checks/07_verify_multi_btb_schema10.sql`
- `server/checks/08_verify_multi_btb_source.py`
- `server/checks/09_verify_android_multi_btb_photo_state_source.py`.

# Testgeräte
- Poco: Xiaomi `23013PC75G`, Android 15 / SDK 35
- Pixel: Google Pixel 8, Android 17 / SDK 37
- beide Testnutzer in Firma `Gtu`.
- `susi zinkel` / Firma `privat` gehört nicht zum Same-Company-Test.

# Nächste sichere Schritte
1. **Produktiven NAS-Stand vorerst auf API 0.7 / Schema 10 lassen.**
2. Den alten blauen BTB-Schnellwechsel und `showSchichtTypDialog()` auf echte `btbId`-Berichte ohne `Neu (leer)` umstellen.
3. Beim `Neuer BTB`-Ablauf `endeDatumIso` für Mitternachtsberichte ausdrücklich absichern.
4. Danach Android **200023 lokal bauen**: mindestens `assembleDebug` + Unit-Tests; Compiler-/Ressourcenfehler im Feature-Branch beheben.
5. Pixel/Poco als Update installieren, **keine App-Daten löschen**.
6. lokal zwei BTBs am selben Tag testen, einschließlich gleicher/überlappender Zeiten und getrennten Foto-Auswahlen/Overrides.
7. V1 erzeugen → ändern → V2 erzeugen; prüfen: V1 bleibt, V2 neu, Änderungen rot, Verlauf korrekt.
8. Sync-UX prüfen: Manuell/Stündlich/Täglich, Netzregel, Startmenü, vollständiger manueller Sync, Baustellenkarte Teamzahl/letzter Sync.
9. Vor dem produktiven Multi-BTB-Servercode-Rollout **ein neues verifiziertes DB-Backup** anlegen, dann read-only `07_verify_multi_btb_schema10.sql` ausführen und `MULTI_BTB_SCHEMA10_OK` verlangen.
10. Danach neueren Servercode in separatem Stage-Ordner auf bestehendem Schema 10 ausrollen; dafür keine Multi-BTB-DB-Migration durchführen.
11. anschließend Pixel ↔ Poco Multi-BTB-Text **und BTB-Bilder** je `btbId` testen.
12. danach Baustellenstruktur-Sync (`5362-1`) bauen.
13. danach serverseitiges Entfernen/Verlassen von Baustellenmitgliedschaften ergänzen.
14. anschließend Mängel/Abnahmen/Attachments/409/Tombstones/Netzabbruch/Benachrichtigungen Ende-zu-Ende testen.
15. Schema 11 erst dann mit eigenem Backup/Hashprüfung/Verifikation produktiv migrieren, wenn der Nutzerprofilblock praktisch dran ist.
16. danach Vermessungs-/Kilometrierungsblock fertigstellen.
17. PR #5 erst nach erfolgreichem Ende-zu-Ende-Test aus Draft nehmen.

# Nicht erneut entscheiden / nicht zurückbauen
- NAS ist Source of Truth.
- Original-Vermessungsdaten bleiben unverändert.
- Offline-Daten werden selektiv geladen.
- Cloudflare für normalen App-Sync; Tailscale nur Wartung.
- keine firmenübergreifenden Teams.
- keine firmenweite Mängel-/Abnahmeverteilung ohne Baustellenmitgliedschaft.
- Baustellen-Team/QR direkt in der Baustellenansicht.
- allgemeine gerätelokale Sync-Einstellungen unter `Einstellungen → Synchronisation` sind okay.
- `Verantwortliche TBs` mit echten Namen; keine sichtbaren A/B/C/D-Gruppen.
- Helfer schreiben in den BTB ihres verantwortlichen TB.
- neue BTB-Identität = **stabile `btbId`**, nicht Datum + TAG/NACHT.
- mehrere BTBs desselben Tages sind erlaubt.
- neuer BTB nur bewusst über `Neuer BTB`; keine künstlichen `Neu (leer)`-Berichte.
- Firmenprofil zentral; persönliche E-Mail/Telefon gehören zum einzelnen Server-Benutzer.
- merge-sicheres Archiv `TB-Helfer/<Baustelle>/...`.
- `main` stabil; große Änderungen im Feature-Branch/Draft-PR.
