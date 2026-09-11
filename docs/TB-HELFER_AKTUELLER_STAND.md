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
- in der aktuellen Entwicklungsrunde wurden **keine NAS-/DB-Änderungen** durchgeführt.

Wichtig: Der Feature-Branch enthält neueren Servercode als der produktive Stage-Ordner, insbesondere stabile Multi-BTB-IDs in `server/app_parts/15_part.py`. Dieser Branch-Code ist noch nicht produktiv ausgerollt.

### Letztes produktives Backup / Schema 10
- Backup: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`
- geprüft mit `pg_restore -l`: **`BACKUP_PRUEFUNG=OK`**.
- Schema-10-Migration lief mit `ON_ERROR_STOP`, `INSERT 0 1`, `COMMIT`.
- Verifikation: **`SCHEMA_10_OK`**.
- damalige Git-Blob-SHAs:
  - `010_worksite_invitations_company_profile.sql` → `d3b78c723de0c5ee0b2a0121957d81513e240a86`
  - `02_verify_schema10.sql` → `100b1214a81990ed84a187b243f94b745ee742ab`

## Persönliches Nutzerprofil / Firma – Schema 11 vorbereitet, nicht produktiv
Verbindliche Trennung:
- **Firma** zentral: Firmenname, Anschrift, Firmentelefon, Firmen-E-Mail, Firmenlogo.
- **Mein Profil** pro Server-Benutzer: Name, persönliche Telefonnummer, persönliche E-Mail-Adresse.
- persönliche Kontaktdaten dürfen für Benutzer derselben Firma sichtbar sein; andere Firmen erhalten sie nicht.

Branch-Vorbereitung:
- `server/migrations/011_user_contact_profile.sql`
- `server/checks/05_verify_schema11.sql` → erwartet `SCHEMA_11_OK`
- `server/checks/06_verify_schema11_source.py`
- `server/app_parts/18_part.py`
  - `GET /api/v1/user/profile`
  - `PUT /api/v1/user/profile`
- Android `UserProfileApi.kt`
- separater Offline-Cache `PersoenlichesNutzerProfilSpeicher`
- Android-UI trennt **Mein Profil** / **Firma**.

**Schema 11 wurde noch nicht auf dem NAS ausgeführt. Produktiv bleibt Schema 10.**

# Firmen / Teams / Einladungen
Praktisch bereits bestätigt:
- Testfirma `Gtu`: zwei Nutzer / zwei aktive Testgeräte.
- Firmenprofil/Logo funktioniert produktiv.
- Helfer-/TB-Einladung funktioniert praktisch.
- Zusammenarbeit ausschließlich innerhalb derselben Firma.
- Ersteller einer Server-Baustelle ist erster verantwortlicher TB.
- weitere verantwortliche TBs und Helfer treten per sicherer Baustelleneinladung bei.
- Helfer können `area_scope` erhalten.
- Aktivierungs-QR und Baustellen-Einladungs-QR sind getrennte Abläufe.
- technische Beziehungen verwenden UUIDs/Team-IDs, nicht sichtbare Namen.

Android:
- `QR scannen` direkt in der Baustellenliste.
- `Nutzer hinzufügen` direkt im Baustellen-Langdruckmenü.
- Rolle kommt aus der serverseitigen Einladung.
- gleichnamige lokale Baustellen werden nicht blind zusammengeführt.

## Baustellenkarte / Team- und Sync-Status
Im Branch umgesetzt:
- `TeamDisplayCache` hält lokalen Root ↔ Server-Worksite, TB-/Helferanzahl und letzten erfolgreichen Baustellen-Sync.
- Hauptbaustellenkarte kann z. B. `2 TB · 4 Helfer · Zuletzt synchronisiert: heute 14:32` anzeigen.
- Provisionierung, QR-Beitritt und Hintergrundsync aktualisieren den Cache.

Noch nicht praktisch mit Android 200023 geprüft.

# BTB

## Verbindliche BTB-Identität
Die frühere technische Identität `Team + Datum + TAG/NACHT` ist abgelöst.

Für neue Berichte gilt:
- BTB entsteht **nur bewusst über `Neuer BTB`**.
- bloßes Öffnen erzeugt keinen Entwurf.
- jeder neue BTB erhält eine stabile **`btbId` (UUID)**.
- Datum, Arbeitsbeginn/-ende und TAG/NACHT sind Attribute/Anzeige, nicht technischer Schlüssel.
- mehrere BTBs desselben TB-Teams am selben Kalendertag sind erlaubt.
- Helfer und verantwortlicher TB desselben Teams bearbeiten dieselbe `btbId`.
- andere verantwortliche TB-Teams derselben Baustelle bleiben getrennt.
- historische `bautagebuch_tag.json`, `bautagebuch_nacht.json` und `bautagebuch.json` bleiben lesbar.

Lokaler Speicher:
- neue Datei: `btb_<btbId>.json`
- `.active_btb.json` hält den aktiv geöffneten Bericht.
- `neueBtbIdVorbereiten()` erzeugt Identität/Pointer; erst Speichern legt den fachlichen Bericht an.
- `verlaufFuerBaustelle()` listet tatsächlich vorhandene moderne + historische BTBs.

## BTB über Mitternacht – jetzt zentral abgesichert
`BautagebuchEintrag` leitet bei fehlendem `endeDatumIso` automatisch den Folgetag ab, wenn die Arbeitszeit über Mitternacht läuft.

Beispiel:
- Beginn `2026-09-10 22:00`
- Ende `06:00`
- abgeleitetes `endeDatumIso = 2026-09-11`.

Normale Tageszeiten behalten `endeDatumIso = null`, sofern kein separates Enddatum nötig ist. Ein ausdrücklich gesetztes Enddatum wird nicht überschrieben.

Neu vorhanden:
- `app/src/test/java/de/tbhelfer/ui/bautagebuch/BautagebuchEintragEnddatumTest.kt`
  - Nacht über Mitternacht → Folgetag
  - normaler Tag → kein separates Enddatum
  - explizites Enddatum bleibt erhalten.

**Der Unit-Test ist angelegt, aber Android 200023 wurde noch nicht lokal per Gradle ausgeführt.**

## Blauer aktueller-BTB-Schnellwechsel – sichtbarer Altpfad korrigiert
Der große historische `BautagebuchFragment.kt` enthält intern weiterhin den alten 7-Tage-TAG/NACHT-Dialog. Um die sehr große Datei nicht unsicher komplett zu ersetzen, wird dessen Click-Listener jetzt nach Aufbau der Fragment-View kontrolliert durch die neue Multi-BTB-Bindung ersetzt.

Neue Datei:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbSchnellwechselInstaller.kt`

Verdrahtung:
- `HauptActivity` installiert den Listener über rekursive `FragmentLifecycleCallbacks`.
- Activity-Restore wird zusätzlich berücksichtigt, falls das BTB-Fragment schon aufgebaut wurde.

Verhalten des blauen Balkens:
- Quelle ausschließlich `BautagebuchSpeicher.verlaufFuerBaustelle()`.
- nur tatsächlich vorhandene BTBs werden angezeigt.
- keine künstlichen Tag-/Nacht-Leerzeilen.
- mehrere BTBs am selben Tag bleiben jeweils eigene Auswahlzeilen.
- Anzeige enthält Datum/Zeitraum, Arbeitszeit und Entwurf/Abschluss-/Versionsstatus.
- aktive `btbId` wird als `aktuell` markiert.
- moderner Bericht wird vor Öffnen über seine konkrete `btbId` aktiviert.
- neuer BTB entsteht weiterhin ausschließlich über den grünen `+`-Ablauf.
- vor dem Wechsel wird Fokus beendet und 1,7 s auf den vorhandenen 1,5-s-Autosave gewartet, damit unmittelbar vorher geänderte Eingaben nicht durch Navigation verloren gehen.

### Historischer Datum/Schicht-Dialog
`showSchichtTypDialog()` existiert im großen Fragment aus Kompatibilitätsgründen weiterhin. Nicht vorhandene Tag-/Nacht-Berichte sind dort aber nicht mehr anklickbar/sichtbar:
- neue View `ExistingBtbShiftButton.kt`
- `dialog_btb_open_shift.xml` verwendet diese View.
- sobald der Altcode den Status `btb_status_new_empty` setzt, wird die betreffende Schaltfläche `GONE` und deaktiviert.

Damit kann dieser Altpfad keinen künstlichen `Neu (leer)`-Bericht mehr als auswählbares Ziel anbieten. Neue Berichte bleiben beim grünen `+`.

Technische Restschuld:
- die alten privaten Methoden/Enums in `BautagebuchFragment.kt` (`ReportState.NEW_EMPTY`, alter 7-Tage-Dialog) sind noch im Quelltext, aber der blaue Balken nutzt sie zur Laufzeit nicht mehr.
- bei einer späteren Zerlegung des sehr großen Fragments sollen diese toten Altpfade endgültig entfernt und die neue Bindung direkt ins Fragment verschoben werden.

Statischer Quellprüfer vorbereitet:
- `server/checks/10_verify_android_btb_quick_switch_source.py`
- erwartet `ANDROID_BTB_QUICK_SWITCH_OK`.
- prüft u. a. `verlaufFuerBaustelle`, `aktiveBtbId`, `aktivierenBtb`, Weitergabe von `btbId`, Activity-Installation sowie das Ausblenden von `btb_status_new_empty`.
- **noch nicht in einem lokalen Checkout ausgeführt**.

## BTB-Abschluss / PDF-Versionierung
Im Branch umgesetzt:
- erster erfolgreicher Abschluss = **V1**.
- spätere Änderung + erneuter Abschluss = **V2, V3, ...**.
- frühere Abschlussdateien bleiben erhalten.
- neue Dateinamen enthalten stabile BTB-Identität + Dokumentversion.
- neue Version vergleicht gegen die vorherige bestätigte Abschlussversion.
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

## BTB-Bilder / lokale Foto-Hilfsdaten
Server-Medienzuordnung:
- `CollabLocalBridge` verwendet `btbId` als `client_entity_key`.
- `BtbTeamMediaSync` verwendet denselben stabilen Key für BTB-Attachments.

Lokale Foto-Hilfsspeicher sind ebenfalls pro BTB isoliert:
- `BtbPhotoInstanzKey.kt`
- `BtbPhotoSelectionStore.kt`
- `BtbPhotoTextOverrideStore.kt`
- `BtbPhotoStartOverrideStore.kt`
- `BtbPhotoKmOverrideStore.kt`.

`BtbPhotoInstanzKey` akzeptiert `.active_btb.json` nur, wenn Datum und Schicht zum angefragten Bericht passen. Neue Hilfsdateien enthalten einen `_btb_<btbId-token>_`-Teil; Altbestände ohne passende ID verwenden weiter das historische Dateischema.

Statischer Prüfer vorbereitet:
- `server/checks/09_verify_android_multi_btb_photo_state_source.py`
- erwartet `ANDROID_MULTI_BTB_PHOTO_STATE_OK`
- noch nicht tatsächlich ausgeführt.

# Multi-BTB auf dem Server
Datenbankschema 9/10 ist bereits grundsätzlich Multi-BTB-tauglich:
- `tb.collab_btb_entries.client_entry_id` ist der Client-Schlüssel.
- eindeutige DB-Regel: **`UNIQUE (group_id, client_entry_id)`**.
- keine gewollte Eindeutigkeit auf Datum + Schicht.
- für stabile `btbId` ist deshalb **keine eigene DB-Migration erforderlich**.

Feature-Branch-Server:
- `server/app_parts/15_part.py` verwendet `client_entity_key` als technische BTB-ID.
- leerer Key → `BTB_ID_REQUIRED`.
- abweichender Payload-`btbId` → `BTB_ID_MISMATCH`.
- Payload erhält denselben `btbId`.
- parallele Helferänderungen werden versionsgeschützt zusammengeführt.
- Altbestände mit historischen Keys bleiben kompatibel.

Vorbereitete Prüfungen:
- `server/checks/07_verify_multi_btb_schema10.sql` → erwartet `MULTI_BTB_SCHEMA10_OK`
- `server/checks/08_verify_multi_btb_source.py` → erwartet `MULTI_BTB_SOURCE_OK`
- beide noch nicht auf NAS/lokal ausgeführt.

Vor Pixel/Poco-Multi-BTB-Sync braucht der produktive Schema-10-Server ein kontrolliertes **Server-Code-Update**, aber keine Multi-BTB-DB-Migration.

# Synchronisation Android 200023
Gerätelokal im Branch:
- Zeitplan: `Manuell` / `Stündlich` / `Täglich`.
- Netz: `Nur WLAN` / `Nur Mobilfunk` / `WLAN + Mobilfunk`.
- `SyncEinstellungenSpeicher.kt` persistiert die Auswahl.
- `SyncWorker.schedule()` verwaltet den periodischen WorkManager-Job.
- automatischer Worker prüft die aktive Transportart.
- `AndroidManifest.xml` enthält `android.permission.ACCESS_NETWORK_STATE`.

Startmenü / Status:
- Eintrag `Mit NAS synchronisieren`.
- `SyncStatusSpeicher.kt` hält den letzten erfolgreichen allgemeinen Server-Sync.
- Einstellungen und Startmenü können den letzten Sync-Stand anzeigen.

Manueller vollständiger NAS-Sync:
1. Katalog/Offline-Stand
2. inkrementelle Queue
3. Baustellen-/Team-/Collaboration-Feed
4. BTB-Text
5. BTB-Bilder
6. Mängel / Abnahmen
7. Attachments
8. abschließender Pull/ACK + Team-/Sync-Status.

Der bewusste manuelle Lauf ignoriert Zeitplan und WLAN/Mobilfunk-Präferenz; eine Internetverbindung bleibt erforderlich.

WorkManager: `2.10.0`.

# Collaboration-Matrix
## Verantwortlicher TB ↔ verantwortlicher TB derselben Baustelle
Gemeinsam innerhalb derselben Baustelle/Berechtigung:
- Baustellenstand / freigegebene Struktur
- benötigte Strecken-/Arbeitsdaten
- Mängel/Schäden
- Abnahmen
- dazugehörige Bilder.

Nicht gemeinsam:
- BTBs verschiedener verantwortlicher TB-Teams werden niemals vermischt.

## Verantwortlicher TB ↔ Helfer desselben TB-Teams
- Baustelle/Unterbaustellen im berechtigten Bereich.
- Streckendaten selektiv nach Bereich.
- BTB gemeinsam in beide Richtungen innerhalb derselben `btbId`.
- Mängel/Abnahmen innerhalb der Helfer-Berechtigung.
- Helferbilder gehen zum Server/verantwortlichen TB; Download anderer Bilder nur soweit benötigt/berechtigt.

# Noch fehlende größere Blöcke
## Baustellenstruktur-Sync
Root-Bootstrap existiert, vollständiger lokaler Baustellenbaum fehlt serverseitig.
Praktischer Befund:
- Poco: Hauptbaustelle `Immenstadt` + Unterbaustelle `5362-1` mit Strecke/KM.
- Server-Self-Service speichert bislang nur den Root.
- anderes Gerät kann `5362-1` noch nicht automatisch nachbauen.

Ziel: stabiler Struktur-Sync mit IDs, Berechtigungs-/Bereichsfilter und getrennten unveränderten Original-Vermessungsdaten.

## Nutzer/Helfer entfernen / Baustelle verlassen
Noch kein fertiger mobiler serverautoritärer Endpunkt. Deshalb bewusst kein rein lokales Entfernen.
Ziel:
- Mitgliedschaft serverseitig widerrufen/verlassen
- historische Beiträge erhalten
- lokale Daten danach nur lesbar
- keine weitere Bearbeitung/Synchronisation.

## Mängel / Abnahmen / Benachrichtigungen
- baustellenweit zwischen berechtigten verantwortlichen TBs.
- Helfer nur im freigegebenen Bereich.
- Bilder/Attachments folgen derselben Berechtigung.
- endgültige Status bleiben endgültig.
- Benachrichtigungsmodi pro Gerät: Text + Ton / nur Ton / aus.

# Server-Speicheroptionen
Pro Benutzer vorgesehen/vorhanden:
- Bilder speichern
- BTB speichern
- Mängel speichern
- Abnahmen speichern
- Alles speichern.

Nicht dauerhaft gespeicherte Daten dürfen temporär für Synchronisation auf dem Server liegen.

# Bahnhofsdaten – nicht neu erfinden
Vorhandene Bestände:
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
- Bilder/Anlagen brauchen stabile eindeutige Kennungen.

Noch zu prüfen:
- ältere Pfade in `BtbExportPaket`/Gesamtexport auf Tag-/Nacht-Kollisionen; zwei unterschiedliche `btbId` dürfen nie denselben ZIP-Zielpfad erhalten.

# Vermessung / Streckendaten – eigener offener Hauptblock
Vorhanden sind lokale TRA/GRA/KOR/KF-/Absteckdaten, Trassenachsen, KM-Punkte, KM-Anker, Korrekturen, GPS-Näheberechnung, Trassengrafik und allgemeiner Strecken-/Bahnhofs-/Abschnitt-Sync.

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
- Android 200020 Root-Bootstrap `Immenstadt` auf Pixel bestätigt.

Aktueller Stand:
- Android **200023 noch nicht lokal vollständig kompiliert/installiert/getestet**.
- GitHub Actions liefert aktuell keinen verwertbaren Android-Build; Runs scheitern vor dem ersten Workflow-Step (`steps=[]`).
- daraus darf weder `Build erfolgreich` noch ein Android-Quellfehler abgeleitet werden.
- lokale Android-Studio-/Gradle-Builds bleiben maßgeblich.

Vorbereitete, noch nicht ausgeführte neue Prüfungen:
- `07_verify_multi_btb_schema10.sql` → `MULTI_BTB_SCHEMA10_OK`
- `08_verify_multi_btb_source.py` → `MULTI_BTB_SOURCE_OK`
- `09_verify_android_multi_btb_photo_state_source.py` → `ANDROID_MULTI_BTB_PHOTO_STATE_OK`
- `10_verify_android_btb_quick_switch_source.py` → `ANDROID_BTB_QUICK_SWITCH_OK`
- Unit-Test `BautagebuchEintragEnddatumTest.kt`.

# Betroffene Dateien der letzten Entwicklungsrunde
Unter anderem:
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `app/src/main/java/de/tbhelfer/ui/HauptActivity.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchModels.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbSchnellwechselInstaller.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/ExistingBtbShiftButton.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentIdentitaet.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDokumentVersionierung.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoInstanzKey.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoSelectionStore.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoTextOverrideStore.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoStartOverrideStore.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoKmOverrideStore.kt`
- `app/src/main/res/layout/dialog_btb_open_shift.xml`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BtbVerlaufFragment.kt`
- `app/src/main/java/de/tbhelfer/datenbank/CollabLocalBridge.kt`
- `app/src/main/java/de/tbhelfer/datenbank/BtbTeamMediaSync.kt`
- `app/src/main/java/de/tbhelfer/server/TeamDisplayCache.kt`
- `app/src/main/java/de/tbhelfer/server/SyncStatusSpeicher.kt`
- `app/src/main/java/de/tbhelfer/server/SyncWorker.kt`
- `app/src/main/java/de/tbhelfer/einstellungen/SyncEinstellungenSpeicher.kt`
- `server/app_parts/15_part.py`
- `server/checks/07_verify_multi_btb_schema10.sql`
- `server/checks/08_verify_multi_btb_source.py`
- `server/checks/09_verify_android_multi_btb_photo_state_source.py`
- `server/checks/10_verify_android_btb_quick_switch_source.py`
- `app/src/test/java/de/tbhelfer/ui/bautagebuch/BautagebuchEintragEnddatumTest.kt`.

# Testgeräte
- Poco: Xiaomi `23013PC75G`, Android 15 / SDK 35
- Pixel: Google Pixel 8, Android 17 / SDK 37
- beide Testnutzer in Firma `Gtu`.
- `susi zinkel` / Firma `privat` gehört nicht zum Same-Company-Test.

# Nächste sichere Schritte
1. **Produktiven NAS-Stand vorerst auf API 0.7 / Schema 10 lassen.**
2. Android **200023 lokal bauen**: mindestens `assembleDebug` + Unit-Tests; Compiler-/Ressourcenfehler im Feature-Branch beheben.
3. Dabei insbesondere `BtbSchnellwechselInstaller`, `ExistingBtbShiftButton` und `BautagebuchEintragEnddatumTest` validieren.
4. Pixel/Poco als Update installieren, **keine App-Daten löschen**.
5. lokal zwei BTBs am selben Tag testen, einschließlich gleicher/überlappender Zeiten, blauer Schnellwechsel und getrennten Foto-Auswahlen/Overrides.
6. Mitternachts-BTB `22:00–06:00` prüfen: Verlauf/PDF/Export zeigen den Folgetag korrekt.
7. V1 erzeugen → ändern → V2 erzeugen; prüfen: V1 bleibt, V2 neu, Änderungen rot, Verlauf korrekt.
8. ältere `BtbExportPaket`-/Gesamtexportpfade auf Multi-BTB-Dateikollisionen prüfen und ggf. korrigieren.
9. Sync-UX prüfen: Manuell/Stündlich/Täglich, Netzregel, Startmenü, vollständiger manueller Sync, Baustellenkarte Teamzahl/letzter Sync.
10. Vor produktivem Multi-BTB-Servercode-Rollout **ein neues verifiziertes DB-Backup** anlegen; dann read-only `07_verify_multi_btb_schema10.sql` ausführen und `MULTI_BTB_SCHEMA10_OK` verlangen.
11. Danach neueren Servercode in separatem Stage-Ordner auf bestehendem Schema 10 ausrollen; keine Multi-BTB-DB-Migration.
12. anschließend Pixel ↔ Poco Multi-BTB-Text **und BTB-Bilder** je `btbId` testen.
13. danach Baustellenstruktur-Sync (`5362-1`) bauen.
14. danach serverseitiges Entfernen/Verlassen von Baustellenmitgliedschaften ergänzen.
15. anschließend Mängel/Abnahmen/Attachments/409/Tombstones/Netzabbruch/Benachrichtigungen Ende-zu-Ende testen.
16. Schema 11 erst mit eigenem Backup/Hashprüfung/Verifikation produktiv migrieren, wenn der Nutzerprofilblock praktisch dran ist.
17. danach Vermessungs-/Kilometrierungsblock fertigstellen.
18. PR #5 erst nach erfolgreichem Ende-zu-Ende-Test aus Draft nehmen.

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
- neuer BTB nur bewusst über `Neuer BTB`; keine auswählbaren künstlichen `Neu (leer)`-Berichte.
- Firmenprofil zentral; persönliche E-Mail/Telefon gehören zum einzelnen Server-Benutzer.
- merge-sicheres Archiv `TB-Helfer/<Baustelle>/...`.
- `main` stabil; große Änderungen im Feature-Branch/Draft-PR.
