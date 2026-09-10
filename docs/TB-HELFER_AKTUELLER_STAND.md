# TB-Helfer – aktueller Projektstand

Stand: 2026-09-10

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Sie ergänzt `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. den aktuell offenen Draft-PR und dessen neuesten Commit

Nach jeder wesentlichen Änderung/Entscheidung ist diese Datei im aktiven Feature-Branch zu aktualisieren. `main` bleibt stabil.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- stabiler Branch: `main`
- aktiver Feature-Branch: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`
- PR bleibt Draft bis der echte Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test erfolgreich ist.
- `main` bleibt unverändert/stabil.
- Android im Feature-Branch: `versionCode 200019`, `versionName 1.3`.
- Schema-10-/Einladungs-/Firmenprofil-/Team-BTB-Code liegt nur im Feature-Branch und ist noch nicht produktiv migriert.

## Produktiver Stand auf der Synology
- NAS/Synology ist Source of Truth.
- produktive API: **0.7.0**.
- produktive PostgreSQL-Datenbank: **Schema 9** (`009_worksite_collaboration`).
- produktiver Servercode: `/volume1/docker/tb-helfer/server_0.7_stage`.
- produktive Secret-Datei bleibt `./server/.env`; keine Secrets wurden in Stage/GitHub kopiert.
- Cloudflare API und Admin Access wurden extern über Mobilfunk erfolgreich geprüft.
- API, PostgreSQL, Schema 9, Bahnhof-Autofill und Admin-Dashboard wurden produktiv erfolgreich getestet.

### Produktive Backups / Migrationen
Vor Migration 008/009 wurde ein PostgreSQL-Custom-Dump erstellt und mit `pg_restore -l` geprüft:
- `tb_helfer_pre_0.7_20260909_201605.dump`
- zweite Kopie unter `/volume1/docker/tb-helfer/backup/`
- Prüfung: `BACKUP_PRUEFUNG=OK`

Produktiv erfolgreich:
- 008 `008_incremental_sync_mutations`
- 009 `009_worksite_collaboration`

Read-only Schema-9-Nachkontrolle:
- Pflichtobjekte/-spalten/-trigger vorhanden
- Integritätschecks `violations=0`
- Attachment-Parent-Prüfung `violations=0`

**Migration 010 ist NICHT produktiv ausgeführt.** Vor jeder produktiven Schema-10-Migration muss erneut ein aktuelles PostgreSQL-Backup erstellt und mit `pg_restore -l` geprüft werden.

## Verbindliche Server-/Datenregeln
- NAS ist Source of Truth.
- Originale Vermessungsdaten bleiben unverändert.
- nur benötigte Daten gehen offline aufs Handy.
- Cloudflare = normale App-Synchronisation; Tailscale = Wartung.
- keine firmenübergreifenden Baustellen-Teams.
- Server-Speicheroptionen sind Aufbewahrungsoptionen, keine Arbeitsfreigabe.
- nicht dauerhaft gespeicherte Daten dürfen temporär zur Synchronisation gespult werden.
- Sync: selektiver Download, Offline-Arbeit, Upload-Queue, Versionierung, 409-Konflikte, Tombstones und Hintergrund-Sync.

## Bahnhofsdaten – nicht neu erfinden
Vorhandene Datenbasis:
- `app/src/main/assets/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
- `app/src/main/assets/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`
- `app/src/main/java/de/tbhelfer/util/BahnhofSucheUtil.kt`

Server/Admin müssen aus diesen Beständen Bahnhofscode/Name vervollständigen. Keine neue Bahnhofsliste einführen.

## Verbindliches Team-/Rechtemodell
- Der Admin verwaltet Benutzer, Firmen, Geräte, Firmenprofil/Logo und Speicheroptionen, aber **nicht** die tägliche Baustellen-Zuordnung.
- Der Ersteller einer Baustelle ist ihr erster verantwortlicher TB.
- Weitere verantwortliche TBs treten per sicherer Baustelleneinladung bei.
- Helfer werden durch einen verantwortlichen TB in genau dessen Team eingeladen.
- sichtbare Überschrift: **`Verantwortliche TBs`**.
- sichtbare Aktionen: **`Helfer einladen`** und **`Weiteren TB einladen`**.
- keine sichtbaren A/B/C/D-Teambezeichnungen.
- Baustellenbeitritt ist Self-Service über Einladung, nicht über manuelles Admin-Zuweisen.
- Aktivierungs-QR und Baustellen-Einladungs-QR bleiben getrennte Sicherheitsvorgänge.
- Einladung enthält keinen Benutzer-/Geräteschlüssel, sondern einen kurzlebigen/widerrufbaren Einladungstoken.
- Einlösen erfordert aktiven Benutzer, gültiges aktiviertes Gerät und dieselbe Firma wie die Baustelle.
- Benutzer derselben Firma ohne Mitgliedschaft dieser konkreten Baustelle erhalten keine Collaboration-Daten dieser Baustelle.

## Gleichnamige Benutzer / stabile Identität
- Benutzeridentität = Server-UUID.
- TB-Teamidentität = `worksite_groups.id` / Gruppen-UUID.
- sichtbare Namen sind niemals technische Schlüssel.
- zwei Benutzer dürfen denselben echten Namen haben.
- gleichnamige verantwortliche TBs derselben Baustelle werden sichtbar stabil z. B. als `Max Mustermann 1` und `Max Mustermann 2` unterschieden.
- Schema 10 speichert dafür `lead_name_ordinal`.
- BTB, Einladungen, Sync und Berechtigungen dürfen nie über den Namen zusammengeführt werden.

## Mängel / Abnahmen / Bilder / Benachrichtigungen
- Mängel und Abnahmen sind baustellenweit zwischen berechtigten verantwortlichen TBs dieser konkreten Baustelle synchronisiert.
- Helfer sehen/bearbeiten sie nur in ihrem freigegebenen Bereich.
- Bilder/Attachments folgen derselben Mitgliedschafts-/Bereichsregel.
- Benachrichtigungen gehen nur an berechtigte Mitglieder dieser Baustelle bzw. ihres Bereichs.
- endgültiger Status `abgestellt/erledigt` bleibt endgültig.
- keine firmenweite Verteilung an Benutzer, die der Baustelle nicht beigetreten sind.

## Schema 10 – im Feature-Branch implementiert, noch nicht produktiv
Migration:
- `server/migrations/010_worksite_invitations_company_profile.sql`

Prüfungen:
- `server/checks/02_verify_schema10.sql`
- `server/checks/03_verify_schema10_source.py`
- `server/checks/04_test_team_btb_merge.py`

Schema 10 ergänzt unter anderem:
- `worksite_groups.lead_name_ordinal`
- `tb.worksite_invitations`
- `tb.worksite_invitation_redemptions`
- `tb.company_profiles`
- mobile Baustellen-Ursprünge / Self-Service-Erstellung

Einladungsregeln:
- sichtbare Code-ID + Geheimteil; serverseitig nur sicherer Hash des Geheimteils.
- Standard einmalig verwendbar und 7 Tage gültig; serverseitig maximal 30 Tage.
- `Helfer einladen` bindet an die eigene TB-Gruppe des Einladenden und optional an `area_scope`.
- `Weiteren TB einladen` erzeugt beim Einlösen eine neue eigene TB-Gruppe des eingeladenen verantwortlichen TB.
- bestehende Mitgliedschaft wird nicht still in eine andere Gruppe verschoben.

## Schema-10-Server-API – Feature-Branch
Relevante Erweiterungen in `server/app_parts/14_part.py`, `15_part.py`, `16_part.py`:
- Collaboration-Context mit sichtbarer Lead-Bezeichnung/-Nummer
- Teamübersicht je Baustelle
- Einladung erstellen/einlösen/widerrufen
- Firmenprofil/Logo
- mobile Baustellenerstellung / Self-Service-Ursprung
- Team-BTB-Merge für mehrere Geräte/Helfer

Verbindliche spätere Produktiv-Reihenfolge:
1. aktuelles PostgreSQL-Backup erstellen
2. `pg_restore -l` erfolgreich prüfen
3. Migration 010 mit `psql -v ON_ERROR_STOP=1`
4. `server/checks/02_verify_schema10.sql`
5. erst danach neuen Servercode aktivieren/rebuilden
6. API/Admin extern prüfen

Neuer Servercode darf nicht vor Migration 010 produktiv aktiviert werden, weil er Schema-10-Felder/-Tabellen voraussetzt.

## Zentrales Firmenprofil / Logo – Feature-Branch
Server:
- Firmenname/Anschrift/PLZ/Ort/Telefon/E-Mail/Webseite zentral pro Firma
- Logo PNG/JPG/WEBP, maximal 5 MB
- Logo-Datei im kontrollierten Server-Storage; DB speichert Metadaten/Pfad/Hash/Version
- Admin-Firmenansicht verlinkt auf `Firmenprofil / Logo`

Android:
- Profil/Logo werden nach erfolgreicher Geräteaktivierung best-effort vom Server geladen.
- `CompanyProfileSync.kt` spiegelt die zentralen Daten in die bestehende lokale `NutzerSpeicher`-/`NutzerProfil`-Quelle.
- dadurch bleiben vorhandene PDF-/BTB-Funktionen offline nutzbar.
- `maxFotosInPdf` bleibt lokale Einstellung.
- serverseitiges Firmenlogo wird lokal gecacht.
- Baustellen-Einladung überschreibt das Benutzer-/Firmenprofil nicht.
- volle Aktivierungs-/Geräteschlüssel werden nicht offen im normalen Nutzerprofil angezeigt; vorgesehen sind Support-/Geräte-ID und höchstens maskierter Key-Hinweis.

Noch real zu testen: Firmenprofil-/Logo-Abruf auf echtem Gerät gegen Schema 10.

## Android – Einladung / QR / Arbeitsbereich
Im Feature-Branch vorhanden:
- Teamübersicht mit `Verantwortliche TBs`
- `Helfer einladen`
- `Weiteren TB einladen`
- sicherer TBI-Code
- `tbhelfer://invite?...`-Deep-Link
- sichtbare QR-Karte für Baustelleneinladung
- QR-Scanner
- Helfer-Arbeitsbereich-Auswahl (`area_scope`)
- nach Einlösen erneute Baustellen-Synchronisation
- Aktivierungs- und Baustelleneinladung bleiben getrennt

## Verbindliche BTB-Logik
Fachliche BTB-Identität innerhalb einer Baustelle:
**internes TB-Team + Datum + Schicht**.

Regeln:
- pro Baustelle, verantwortlichem TB-Team, Datum und Schicht genau ein gemeinsamer BTB.
- Helfer erzeugen keinen eigenen BTB; ihre Texte, Zeiten, Fotos usw. werden in den BTB ihres verantwortlichen TB synchronisiert.
- Arbeitsbereiche, Gleise, Weichen, KM und Abschnitte sind Inhalt, nicht BTB-Identität.
- keine künstlichen `-2/-3/-4`-Berichte für dieselbe Team-/Tag-/Schicht-Kombination.
- technische Zuordnung bleibt über stabile IDs; menschenlesbarer Name ist Anzeige/Export.

Anzeige:
- `BTB Jens Zinkel · 05.06.2026 · Tag`
- `BTB Max Müller · 05.06.2026 · Nacht`

Export:
- `BTB_2026-06-05_TAG_Jens_Zinkel.pdf`
- `BTB_2026-06-05_TAG_Jens_Zinkel.docx`
- `BTB_2026-06-05_NACHT_Max_Mueller.pdf`
- `BTB_2026-06-05_NACHT_Max_Mueller.docx`

Bei gleichnamigen TBs darf der stabile sichtbare Zusatz verwendet werden, z. B. `Max_Mustermann_1` / `Max_Mustermann_2`.

Dokumentkopf soll mindestens enthalten:
- Firma
- Baustelle
- verantwortlicher TB
- Datum
- Schicht

## Team-BTB / Medien-Sync – Feature-Branch
- Server-Merge führt Beiträge mehrerer Geräte/Helfer desselben TB-Teams in denselben Team-BTB zusammen.
- Retries dürfen fachliche Einträge nicht duplizieren.
- autoritativer Snapshot darf bewusst gelöschte alte Einträge nicht wiederherstellen.
- BTB-Medien werden teambezogen übertragen; lokale Medien werden bei eingehendem Text-Sync nicht unnötig gelöscht.
- `server/checks/04_test_team_btb_merge.py` prüft die reinen Server-Merge-Helfer deterministisch.

## BTB-Verlauf / Export – neuester Entwicklungsstand
Aktuell im Feature-Branch:
- `BtbVerlaufFragment.kt` zeigt den verantwortlichen TB im Verlauf menschenlesbar an.
- Anzeige bleibt fachlich `BTB <verantwortlicher TB> · <Datum> · <Schicht>`; technische Zuordnung bleibt UUID-/Gruppen-basiert.
- einzelnes `ZIP teilen` aus dem BTB-Verlauf wird jetzt ebenfalls durch `MergeSafeBaustellenExport` normalisiert.
- dabei wird die bekannte `SchichtTyp` explizit übergeben, weil das ältere Einzel-Exportpaket intern noch `BTB_<Datum>.pdf/.docx` erzeugt.
- `MergeSafeBaustellenExport` erkennt deshalb sowohl `BTB_<Datum>_<TAG|NACHT>.*` als auch das ältere `BTB_<Datum>.*` und erzeugt den stabilen Team-/Schichtnamen.
- DOCX wird beim merge-sicheren Export um Firma, verantwortlichen TB und Schicht im Dokumentkopf ergänzt.
- Mediennamen im langfristigen Archiv erhalten inhaltsbasierte stabile Hash-Suffixe.
- strukturierte Abnahmeordner erhalten zur Kollisionssicherheit eine stabile ID-Komponente.
- Zielstruktur des langfristigen Laptop-Archivs: `TB-Helfer/<Baustelle>/...`.
- `server/checks/03_verify_schema10_source.py` schützt jetzt zusätzlich den Einzel-BTB-Normalisierungspfad und die Verwendung im BTB-Verlauf als statischen Source-Contract.

Noch offen in diesem BTB-Block:
- die **direkt lokal erzeugte/angezeigte PDF-Datei selbst** verwendet intern in `BautagebuchPdf.kt` noch die ältere lokale Dateinamenslogik; der merge-sichere ZIP-/Laptop-Export ist bereits team-/schichtbezogen.
- der sichtbare PDF-Hauptkopf in `BautagebuchPdf.kt` muss noch direkt um Firma/verantwortlichen TB/Schicht erweitert werden; DOCX-Archivkopf ist bereits normalisiert.
- historische bestehende PDFs werden nicht zwangsumbenannt.

## Merge-sicheres Laptop-Archiv
Verbindliches Ziel:
- `TB-Helfer/<Baustelle>/...`
- mehrere ZIPs unterschiedlicher Geräte/Personen müssen ohne Datenverlust in denselben Zielordner entpackbar sein.
- derselbe fachliche Datensatz soll denselben stabilen Zielpfad erhalten.
- unterschiedliche Datensätze dürfen nicht kollidieren.
- Bilder/Anlagen verwenden inhaltsbasierte stabile Namen.
- Abnahmen verwenden stabile interne ID-Komponenten.
- BTBs verwenden Datum + Schicht + verantwortlichen TB; gleichnamige TBs werden durch stabile sichtbare Ordinale unterschieden.

## Android-Build-/Teststatus
- ältere produktiv getestete Basis wurde auf Pixel und Poco installiert und verbindet sich mit API 0.7 / Schema 9.
- eine frühere Schema-10-Feature-Fassung wurde am 2026-09-10 lokal in Android Studio erfolgreich gebaut: `BUILD SUCCESSFUL in 42s`, 67 Tasks (19 ausgeführt, 48 up-to-date).
- der erste lokale Build der aktuellen Android-200019-Fassung am 2026-09-10 um ca. 12:31 schlug in `BaustellenSyncFragment.kt` mit 8 Compilerfehlern fehl.
- Ursache war kein Architektur-/Sync-Fehler, sondern zwei konkrete QR-UI-Compilefehler: falscher Package-Pfad der bereits eingebundenen Google-Code-Scanner-Klassen (`com.google.android.gms.mlkit.codescanner` statt `com.google.mlkit.vision.codescanner`) sowie die nicht vorhandene Framework-Ressource `android.R.string.close`.
- Fix im Feature-Branch: Scanner-Imports auf `com.google.mlkit.vision.codescanner.*` korrigiert und Dialog-Schließen auf die vorhandene `android.R.string.cancel`-Ressource umgestellt; Commit `60e42fe3ce732f7ffb5e638d562224e328f0fe03`.
- **Nach diesem Fix wurde noch kein neuer lokaler Android-Studio-Build bestätigt.** Der nächste Build ist daher maßgeblich.
- GitHub Actions ist weiterhin nicht maßgeblich für den Android-Build; aktuelle Läufe scheitern in der bekannten CI-Infrastruktur vor verwertbaren Build-Schritten.

## Testgeräte
- Poco: Xiaomi `23013PC75G`, Android 15 / SDK 35
- Pixel: Google Pixel 8, Android 17 / SDK 37
- beide vorgesehenen Testnutzer gehören zur Firma `Gtu`.
- `susi zinkel` / Firma `privat` gehört nicht zum beabsichtigten Same-Company-Test.

## Nächste sichere Schritte
1. **Kein produktives Schema 10 ohne neues, geprüftes Backup.**
2. lokalen Feature-Branch auf den aktuellen GitHub-Head holen und Android 200019 erneut in Android Studio bauen; dabei zuerst prüfen, ob die 8 QR-Scanner-Compilerfehler vollständig verschwunden sind.
3. falls der Build weitere echte Compilerfehler zeigt, diese einzeln am aktuellen Branchstand beheben und dokumentieren.
4. danach direkte lokale PDF-Erzeugung auf Team-/Datum-/Schicht-Dateinamen und Dokumentkopf `Firma + Baustelle + verantwortlicher TB + Datum + Schicht` umstellen, ohne historische PDFs zu beschädigen.
5. erneut lokal bauen.
6. Build zunächst auf einem Testgerät als Update gegen noch produktives Schema 9 starten und Regressionen prüfen; danach zweites Gerät.
7. Schema-10-Migration/Checks zuerst auf sicherer Test-/Backup-Basis durchspielen.
8. Link-/Code-/QR-Einladung mit zwei Testnutzern derselben Firma gegen Schema-10-Testinstanz prüfen.
9. Firmenprofil/Logo, Helfer-`area_scope`, weitere verantwortliche TBs und gleichnamige TBs prüfen.
10. Team-BTB testen: zwei Geräte/Helfer schreiben in denselben Team-BTB; anderer verantwortlicher TB bleibt vollständig getrennt; TAG/NACHT bleiben getrennt.
11. Merge-Export von mehreren Geräten auf denselben `TB-Helfer/<Baustelle>/...`-Ordner testen.
12. Mängel/Abnahmen/Bilder, endgültige Status, 409, Tombstones, Netzabbruch/Wiederaufnahme und Benachrichtigungen testen.
13. erst nach erfolgreichen Tests neues produktives DB-Backup erstellen, Migration 010 ausführen, Schema-10-Check ausführen und danach Servercode aktivieren.
14. PR #5 bis zum erfolgreichen Ende-zu-Ende-Test Draft lassen; `main` vorher nicht ändern.

## Vermessungs-/Kilometrierungsfarben
- Grün = belastbare Originaldaten/KM
- Orange = manuell geändert/hergestellt
- Rot = TRA vorhanden, aber keine belastbare KM
- Grau = ohne Vermessungsgeometrie
- Blau = Auswahl/temporäre Markierung

## Nicht erneut entscheiden / nicht zurückbauen
- NAS Source of Truth.
- Originaldaten unverändert.
- selektive Offline-Daten.
- Cloudflare Sync, Tailscale Wartung.
- keine firmenübergreifenden Teams.
- keine firmenweite Mängel-/Abnahmeverteilung.
- Self-Service-Baustellenbeitritt statt täglicher Admin-Zuordnung.
- `Verantwortliche TBs` mit echten Namen; keine sichtbaren A/B/C/D-Gruppen.
- Helfer schreiben in den BTB ihres verantwortlichen TB.
- BTB-Identität = Team + Datum + Schicht.
- Namen sind nie technische Schlüssel; UUIDs/Team-IDs bleiben maßgeblich.
- zentraler Firmenprofil-/Logo-Stand vom Server.
- merge-sicheres Archiv `TB-Helfer/<Baustelle>/...`.
- `main` stabil; große Änderungen im Feature-Branch/Draft-PR.

## Sitzungsregel für ChatGPT
Bei einem neuen Chat nicht aus alten Chat-Zusammenfassungen allein weiterarbeiten. Zuerst Projektregeln, diese Datei, Draft-PR und neuesten Commit in GitHub prüfen. Bei Widersprüchen gilt der produktive NAS-Stand für tatsächlich laufenden Server/DB-Stand, die Projektregeln für fachliche Entscheidungen und der Feature-Branch für noch nicht produktive Entwicklung.
