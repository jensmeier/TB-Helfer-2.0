# TB-Helfer – aktueller Projektstand

Stand: 2026-09-10

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Sie ergänzt `docs/TB-HELFER_PROJEKTREGELN.md`. Fachliche Grundsatzentscheidungen stehen in den Projektregeln; diese Datei hält den tatsächlich erreichten technischen Stand und den nächsten sicheren Schritt fest.

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
- **Migration 010 ist noch NICHT produktiv ausgeführt.**

### Letztes produktives Backup / Migration 008+009
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

Vor Schema 10 muss erneut ein **aktuelles** PostgreSQL-Backup erstellt und mit `pg_restore -l` geprüft werden.

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
- Admin verwaltet Benutzer, Firmen, Geräte, Firmenprofil/Logo und Speicheroptionen, aber nicht die tägliche Baustellen-Zuordnung.
- Ersteller einer Server-Baustelle ist erster verantwortlicher TB.
- weitere verantwortliche TBs treten per sicherer Baustelleneinladung bei.
- Helfer werden in genau das Team ihres verantwortlichen TB eingeladen und können einen `area_scope` erhalten.
- sichtbare Überschrift: `Verantwortliche TBs`.
- sichtbare Aktionen: `Helfer einladen` und `Weiteren TB einladen`.
- keine sichtbaren A/B/C/D-Teambezeichnungen.
- Aktivierungs-QR und Baustellen-Einladungs-QR sind getrennte Sicherheitsvorgänge.
- Einlösen erfordert aktiven Benutzer, aktiviertes Gerät und dieselbe Firma wie die Baustelle.
- gleichnamige Benutzer bleiben intern über UUIDs/Team-IDs getrennt; Schema 10 speichert dafür zusätzlich `lead_name_ordinal` für stabile sichtbare Zusätze.

## Mängel / Abnahmen / Bilder / Benachrichtigungen
- Mängel und Abnahmen sind baustellenweit zwischen berechtigten verantwortlichen TBs derselben konkreten Baustelle synchronisiert.
- Helfer sehen/bearbeiten sie nur in ihrem freigegebenen Bereich.
- Bilder/Attachments folgen derselben Mitgliedschafts-/Bereichsregel.
- endgültiger Status `abgestellt/erledigt` bleibt endgültig.
- Benachrichtigungsmodi pro Gerät: Text + Ton / nur Ton / aus.

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
- TBI-Code/Deep-Link/QR enthalten keine Benutzer-/Geräteschlüssel.
- serverseitig wird nur der sichere Hash des Geheimteils gespeichert.
- Standard einmalig verwendbar und 7 Tage gültig; serverseitig maximal 30 Tage.
- Helfer-Einladung bindet an die eigene TB-Gruppe des Einladenden und optional an `area_scope`.
- Lead-Einladung erzeugt beim Einlösen eine neue eigene TB-Gruppe.
- bestehende Mitgliedschaft wird nicht still in eine andere Gruppe verschoben.

## Schema-10-Server-API – Feature-Branch
Relevante Erweiterungen in `server/app_parts/14_part.py`, `15_part.py`, `16_part.py`, `17_part.py`:
- Collaboration-Context mit sichtbarer Lead-Bezeichnung/-Nummer
- Teamübersicht je Baustelle
- Einladung erstellen/einlösen/widerrufen
- Firmenprofil/Logo
- mobile Baustellenerstellung / Self-Service-Ursprung
- Team-BTB-Merge für mehrere Geräte/Helfer
- zentraler Baustellen-Medienbestand für Gesamtexporte

Verbindliche Produktiv-Reihenfolge:
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
- Profil/Logo werden nach erfolgreicher Geräteaktivierung best-effort geladen.
- `CompanyProfileSync.kt` spiegelt zentrale Daten in die bestehende lokale `NutzerSpeicher`-/`NutzerProfil`-Quelle.
- `maxFotosInPdf` bleibt lokale Einstellung.
- serverseitiges Firmenlogo wird lokal gecacht.
- Nutzerseite ist für zentrale Firmendaten auf Lesen/Aktualisieren umgestellt; kein freies lokales Überschreiben des zentralen Firmenprofils.
- auch bereits aktivierte Geräte ziehen den nicht geheimen Benutzernamen über `/device/me` als Anzeige-/BTB-Fallback nach.

Noch real zu testen: Firmenprofil-/Logo-Abruf auf echtem Gerät gegen produktives Schema 10.

## Android – Baustelle / Einladung / QR / Arbeitsbereich
Im Feature-Branch vorhanden:
- Teamübersicht `Verantwortliche TBs`
- `Helfer einladen`
- `Weiteren TB einladen`
- sicherer TBI-Code
- `tbhelfer://invite?...`-Deep-Link
- sichtbare QR-Karte für Baustelleneinladung
- QR-Scanner
- Helfer-Arbeitsbereich-Auswahl (`area_scope`)
- nach Einlösen erneute Baustellen-Synchronisation
- Aktivierungs- und Baustelleneinladung bleiben getrennt
- normaler Einstieg im Baustellen-Menü heißt verbindlich **`Nutzer hinzufügen`**.
- beim ersten `Nutzer hinzufügen` wird die lokale Hauptbaustelle automatisch und idempotent auf dem Server angelegt/verbunden; anschließend öffnet sich die Teamverwaltung.
- gegen den noch produktiven Schema-9-Server wird dieser Ablauf sauber als noch nicht unterstützt abgefangen; das wurde auf dem Handy bereits praktisch bestätigt.

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

## BTB-PDF / Verlauf / Export – aktuell getestet
- `BtbVerlaufFragment.kt` zeigt den verantwortlichen TB menschenlesbar an.
- `MergeSafeBaustellenExport` normalisiert Einzel- und Gesamtexporte auf stabile Team-/Schichtnamen.
- DOCX-Archivkopf enthält Firma, verantwortlichen TB und Schicht.
- Mediennamen im langfristigen Archiv erhalten inhaltsbasierte stabile Hash-Suffixe.
- strukturierte Abnahmeordner erhalten zur Kollisionssicherheit eine stabile ID-Komponente.
- Zielstruktur des langfristigen Laptop-Archivs: `TB-Helfer/<Baustelle>/...`.
- **Direkte lokale BTB-PDF-Erzeugung ist jetzt ebenfalls umgestellt und praktisch getestet.**
- `BautagebuchPdf.kt` löst `BtbDokumentIdentitaet` auf und erzeugt den kanonischen Namen `BTB_<Datum>_<TAG|NACHT>_<VerantwortlicherTB>.pdf`.
- erneutes Erzeugen desselben fachlichen BTB aktualisiert denselben kanonischen Zielpfad statt neue `geaendert2/geaendert3`-Dateien anzulegen.
- sichtbarer PDF-Kopf enthält Baustelle, Firma, verantwortlichen TB, Datum, Schicht und Zeit; das bestehende lokale Firmenlogo bleibt nutzbar.
- Test-PDF am 2026-09-10: `BTB_2026-09-10_NACHT_jens_poco.pdf`; Kopf/2 Seiten praktisch geprüft, Layout ohne erkennbare Überlagerung/Abschneidung.
- im Test stand `Firma: -`, erwartungsgemäß weil das zentrale Firmenprofil erst nach Schema-10-Rollout produktiv abrufbar ist.
- Commit des getesteten PDF-Abschlusses: `4cca0b9697d3b75c31f538898abe318706753a1d` (`android: finalize canonical BTB PDF`).
- historische bestehende PDFs werden nicht zwangsumbenannt.

## Gesamtbilderliste / Mängel- / Abnahme-Archiv
Im Feature-Branch vorhanden:
- `Bilderliste_gesamt.xlsx` als Excel-Hauptliste mit Tabellenformat, Filter/Sortierung, eingefrorener Kopfzeile und anklickbaren relativen Bildpfaden.
- zusätzlich `Bilderliste_gesamt.csv`.
- Ziel ist eine baustellenweite Liste aller berechtigten Bilder: Baudokumentation, BTB-/Mängel-/Erledigt-/Abnahmebilder usw. mit Art/Status/Ersteller/verantwortlichem TB.
- neuer strukturierter Ordner `Mängel` analog `Abnahmen`.
- `Maengelliste_Gesamt.csv/.pdf` und `Abnahmeliste_Gesamt.csv/.pdf` werden aus dem aktuellen Baustellenbestand erzeugt.
- serverseitiger Baustellen-Medienbestand synchronisiert fachliche Medien-Metadaten; XLSX/CSV werden nicht als konkurrierende Dateien zwischen Handys hin- und hergeschoben.
- praktischer Einzelgeräte-ZIP-Test: 27 exportierte Bilder, 27 Excel-Zeilen und 27 gültige interne Bildlinks; 9 eindeutige strukturierte Mängel. Echte Mehrbenutzer-Gesamtlisten müssen nach Schema 10 noch mit Pixel/Poco getestet werden.

## Merge-sicheres Laptop-Archiv
Verbindliches Ziel:
- `TB-Helfer/<Baustelle>/...`
- mehrere ZIPs unterschiedlicher Geräte/Personen müssen ohne Datenverlust in denselben Zielordner entpackbar sein.
- derselbe fachliche Datensatz soll denselben stabilen Zielpfad erhalten.
- unterschiedliche Datensätze dürfen nicht kollidieren.
- Bilder/Anlagen verwenden stabile/inhaltsbasierte Kennungen.
- BTBs verwenden Datum + Schicht + verantwortlichen TB; gleichnamige TBs werden durch stabile sichtbare Ordinale unterschieden.

Noch real zu testen: Zwei Geräte derselben Baustelle exportieren über längeren Zeitraum in denselben Laptop-Hauptordner, ohne Datenverlust oder falsches Überschreiben.

## Android-Build-/Teststatus
- Android 200019 wurde am 2026-09-10 mehrfach lokal erfolgreich gebaut und als Update auf dem Testhandy installiert.
- erster erfolgreicher 200019-Build nach QR-Compilefix: `BUILD SUCCESSFUL in 1m 24s`, 69 Tasks.
- `Nutzer hinzufügen`-UX wurde danach gebaut und auf dem Handy gegen Schema 9 praktisch geprüft; Unsupported-Fall wurde korrekt abgefangen.
- nach dem direkten BTB-PDF-Abschluss wurde der richtige Arbeitsbranch `feature/admin-offline-sync` nochmals gebaut: **`BUILD SUCCESSFUL in 14s`, 69 Tasks (1 ausgeführt, 68 up-to-date)**.
- das zugehörige kanonische PDF wurde auf echtem Handy erzeugt und geprüft.
- 15 nicht blockierende Compilerwarnungen sind separat in `docs/ANDROID_WARNINGS_BACKLOG_2026-09-10.md` dokumentiert und werden später systematisch bereinigt.
- GitHub Actions ist weiterhin nicht maßgeblich für den Android-Build; lokale Android-Studio-Builds sind für diesen Entwicklungsstand die Autorität.

## Testgeräte
- Poco: Xiaomi `23013PC75G`, Android 15 / SDK 35
- Pixel: Google Pixel 8, Android 17 / SDK 37
- beide vorgesehenen Testnutzer gehören zur Firma `Gtu`.
- `susi zinkel` / Firma `privat` gehört nicht zum beabsichtigten Same-Company-Test.

## Streckendaten / Vermessung – eigener noch offener Hauptblock
Bereits vorhanden sind umfangreiche lokale Android-Funktionen für TRA-/GRA-/KOR-/KF-/Absteckdaten, Trassenachsen, KM-Punkte, KM-Anker, Korrekturen, GPS-Näheberechnung und Trassengrafik sowie der allgemeine serverseitige Strecken-/Bahnhofs-/Abschnitt-Sync.

Noch vollständig fertigzustellen und Ende-zu-Ende zu prüfen:
- exakter Bestand: welche Vermessungsdaten liegen nur lokal, welche bereits serverseitig.
- unveränderte Originaldateien dauerhaft auf NAS halten; berechnete/bearbeitete Ebene strikt getrennt versionieren.
- selektiver Download nach fachlicher Auswahl, z. B. Bahnhof + Gleise + Weichen/DKW oder KM-Bereich, statt komplette Strecke.
- TRA, GRA, rohe KF, KOR und tatsächlich benötigte weitere Vermessungsformate serverseitig katalogisieren/zuordnen.
- echte TRA-Geometrie-Kilometrierung, GPS-KM-Anker, Zwischenpunkte, angrenzende Gleise, Weichen/DKW/Kreuzungen vollständig testen.
- Farbregeln: Grün belastbare Original-KM, Orange manuell geändert, Rot TRA ohne belastbare KM, Grau ohne Vermessungsgeometrie, Blau Auswahl.
- nur geänderte/berechnete Abschnitte hochladen, nicht komplette Originalpakete.
- Versionierung/409-Konflikte für konkurrierende Abschnittsbearbeitung.
- Mehrgeräte-Test: Pixel/Poco laden unterschiedliche Teilbereiche, synchronisieren Änderungen und erweitern Bereiche später ohne Neuimport.
- Langzeittest: vorhandene Bereiche nach Monaten/Jahren wieder abrufen und mit neuen Bereichen ergänzen.

## Nächste sichere Schritte
1. **Kein produktives Schema 10 ohne neues, geprüftes Backup.**
2. Auf der Synology ein aktuelles PostgreSQL-Custom-Backup direkt vor Schema 10 erstellen.
3. dieses Backup mit `pg_restore -l` prüfen und erst bei erfolgreicher Prüfung weitermachen.
4. Migration 010 mit `psql -v ON_ERROR_STOP=1` ausführen.
5. `server/checks/02_verify_schema10.sql` erfolgreich ausführen.
6. erst danach den neuen Schema-10-Servercode aktivieren/rebuilden.
7. API/Admin/Cloudflare extern prüfen; Firmenprofil/Logo pflegen und auf Pixel/Poco abrufen.
8. `Nutzer hinzufügen` real testen: erste lokale Baustelle automatisch serverseitig anlegen/verbinden, keine Dublette bei Wiederholung.
9. QR-/Link-/Code-Einladung mit zwei Testnutzern derselben Firma testen; Helfer-`area_scope`, weiterer verantwortlicher TB und gleichnamige TBs prüfen.
10. Team-BTB mit zwei Geräten/Helfern testen; anderer verantwortlicher TB bleibt getrennt, TAG/NACHT getrennt.
11. Mängel/Abnahmen/Bilder/Gesamtlisten/Benachrichtigungen mit mehreren Benutzern testen.
12. Offline/Upload-Queue/409/Tombstones/Netzabbruch/Wiederaufnahme testen.
13. Merge-Export von mehreren Geräten in denselben `TB-Helfer/<Baustelle>/...`-Laptopordner testen.
14. danach den separaten Streckendaten-/Vermessungsblock vollständig fertigstellen und mit mehreren Geräten testen.
15. anschließend 15 Android-Warnungen/sonstiges Aufräumen abarbeiten, vollständigen Endbuild durchführen und PR #5 erst nach erfolgreichem Ende-zu-Ende-Test aus Draft nehmen.

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
