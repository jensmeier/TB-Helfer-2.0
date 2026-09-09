# TB-Helfer – aktueller Projektstand

Stand: 2026-09-10

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
- `main` bleibt unverändert/stabil.
- Schema-10-/Einladungs-/Firmenprofil-Code liegt ausschließlich im Feature-Branch und ist noch nicht produktiv migriert.

## Produktiver Stand auf der Synology
- NAS/Synology ist Source of Truth.
- produktive API: **0.7.0**.
- produktive PostgreSQL-Datenbank: **Schema 9** (`009_worksite_collaboration`).
- produktiver Servercode wird aktuell aus `/volume1/docker/tb-helfer/server_0.7_stage` gebaut/eingebunden.
- produktive Secret-Datei bleibt unter `./server/.env`; keine Secrets wurden in Stage/GitHub kopiert.
- Cloudflare API und Cloudflare Admin Access wurden von außen über Mobilfunk erfolgreich geprüft.
- API, PostgreSQL, Schema 9, Bahnhof-Autofill und Admin-Dashboard wurden produktiv erfolgreich getestet.

### Backups / bisherige produktive Migrationen
Vor Migration 008/009 wurde ein PostgreSQL-Custom-Dump erstellt und mit `pg_restore -l` geprüft:
- `tb_helfer_pre_0.7_20260909_201605.dump`
- zweite Kopie unter `/volume1/docker/tb-helfer/backup/`
- Prüfung: `BACKUP_PRUEFUNG=OK`

Migrationen produktiv erfolgreich:
- 008 `008_incremental_sync_mutations`
- 009 `009_worksite_collaboration`

Read-only Schema-9-Nachkontrolle war vollständig sauber:
- Pflichtobjekte/-spalten/-trigger vorhanden
- Integritätschecks `violations=0`
- Attachment-Parent-Prüfung `violations=0`

**Migration 010 ist NICHT produktiv ausgeführt.** Vor jeder produktiven Schema-10-Migration muss erneut ein aktuelles, geprüftes Datenbankbackup erstellt werden.

## Produktive API 0.7 / Schema 9
Vorhanden und geprüft:
- allgemeiner Offline-Sync: Katalog, selektive Details, Changes, ACK, Mutations, Basisversion/409, Idempotenz, Tombstones
- Collaboration: Context, Changes, ACK, Mutations, Attachments
- Firmenbindung auf Server-/DB-Ebene
- gruppenbezogener BTB
- baustellenweite Mängel/Abnahmen im berechtigten Bereich
- Bilder/Abnahme-Anlagen als Attachments
- endgültige Status geschützt
- Benutzer-Aufbewahrungsoptionen und temporäres Spooling
- bestehende Bahnhofsdaten werden verwendet; keine neue Bahnhofsliste

## Android-Stand – produktiv getestete Basis
- Android `versionCode 200018`, `versionName 1.3` im Draft-Branch.
- der bisherige 200018-Basisstand wurde lokal in Android Studio erfolgreich als Debug-APK gebaut (`BUILD SUCCESSFUL`).
- dieselbe Basis-APK wurde auf beiden Test-Handys als Update installiert.
- vorhandene lokale Baustellen-/Vermessungsdaten blieben erhalten.
- beide Geräte starten fehlerfrei und verbinden sich mit Server 0.7.0 / Schema 9.
- separate Room-Datenbank `tbhelfer-sync.db`; bestehende Baustellen-/Vermessungsdatenbank bleibt unangetastet.
- Upload-Queue, 409-Grundlage, Tombstones, Hintergrund-Sync und Benachrichtigungen sind vorbereitet.

**Die neue Schema-10-Android-Erweiterung wurde nach der Implementierung noch nicht lokal in Android Studio gebaut/installiert.** GitHub Actions ist weiterhin kein Ersatz für den lokalen Android-Studio-Build; der aktuelle CI-Lauf zum neuen Head endete als Failure ohne verwertbaren lokalen Buildnachweis.

## Verbindliches Team-/Rechtemodell
- Baustellen-Teams ausschließlich innerhalb derselben Firma.
- Der Admin verwaltet Benutzer/Firmen/Geräte und Speicheroptionen, aber nicht die laufende tägliche Baustellen-Zuordnung.
- Der Ersteller einer Baustelle ist ihr erster verantwortlicher TB.
- Weitere verantwortliche TBs treten per sicherer Link-/Code-/später QR-Einladung bei; der Admin ist dafür nicht erforderlich.
- Sichtbare Bezeichnung: **`Verantwortliche TBs`** mit echten Namen, keine sichtbaren A/B/C/D-Gruppen.
- Bei jedem verantwortlichen TB: **`Helfer einladen`**.
- Zusätzlich: **`Weiteren TB einladen`**.
- Helfer werden genau dem Team des einladenden verantwortlichen TB zugeordnet und erhalten ihren freigegebenen Arbeitsbereich.
- Benutzer derselben Firma ohne angenommene Einladung zu dieser konkreten Baustelle erhalten keine Collaboration-Daten dieser Baustelle.
- Mängel/Abnahmen/Bilder/Benachrichtigungen werden nur an berechtigte Mitglieder dieser einen Baustelle verteilt; keine firmenweite Verteilung.
- Helfer sehen Mängel/Abnahmen nur innerhalb ihres freigegebenen Bereichs.

## Gleichnamige Benutzer – verbindliche Identitätsregel
Menschenlesbare Namen sind **niemals** technische Identitäten.

- Benutzeridentität = Server-UUID.
- TB-Teamidentität = `worksite_groups.id` / Gruppen-UUID.
- Zwei Benutzer dürfen denselben echten Namen besitzen.
- Zwei verantwortliche TBs mit demselben Namen auf derselben Baustelle werden sichtbar stabil unterschieden, z. B. `Max Mustermann 1` und `Max Mustermann 2`.
- Schema 10 speichert dafür `lead_name_ordinal` im TB-Team. Die Nummer ist nur Anzeige-/Exporthilfe und nicht der Schlüssel für Datenbeziehungen.
- BTB, Einladungen, Sync und Berechtigungen dürfen nie über den sichtbaren Namen zusammengeführt werden.
- Für das spätere merge-sichere Archiv bleibt ebenfalls eine stabile interne Benutzer-/Teamkennung maßgeblich; der sichtbare Name allein genügt nicht als Kollisionsschutz.

## Schema 10 – im Feature-Branch implementiert, noch nicht produktiv
Neue Migration:
- `server/migrations/010_worksite_invitations_company_profile.sql`

Neue Schema-Prüfung:
- `server/checks/02_verify_schema10.sql`

Migration 010 ergänzt:
- `worksite_groups.lead_name_ordinal` inklusive Backfill, Constraint und konkurenzsicherer Vergabe für gleichnamige verantwortliche TBs
- `tb.worksite_invitations`
- `tb.worksite_invitation_redemptions`
- `tb.company_profiles`
- Registrierung als Schema 10 `010_worksite_invitations_company_profile`

Einladungsregeln:
- Einladungscode enthält eine sichtbare Code-ID plus Geheimteil; in PostgreSQL wird nur der PBKDF2-Hash gespeichert.
- Standard: einmalig verwendbar und 7 Tage gültig; serverseitig maximal 30 Tage.
- `Helfer einladen`: Zielgruppe ist zwingend die eigene TB-Gruppe des einladenden verantwortlichen TB.
- `Weiteren TB einladen`: beim Einlösen wird eine neue eigene TB-Gruppe für den eingeladenen verantwortlichen TB angelegt.
- Einlösen erfordert aktiven Benutzer, gültiges aktiviertes Gerät und dieselbe Firma wie die Baustelle.
- DB-Trigger prüfen zusätzlich Firmen- und Baustellenbindung.
- Bereits bestehende Mitgliedschaft derselben Baustelle wird nicht durch eine Einladung still in eine andere Gruppe verschoben.

## Schema-10-Server-API – im Feature-Branch implementiert
Neue Erweiterung:
- `server/app_parts/14_part.py`

Neue/erweiterte Endpunkte:
- `GET /api/v1/collab/context` mit zusätzlicher sichtbarer Lead-Nummer/-Bezeichnung
- `GET /api/v1/collab/worksites/{worksite_id}/team`
- `POST /api/v1/collab/worksites/{worksite_id}/invitations`
- `POST /api/v1/collab/invitations/redeem`
- `POST /api/v1/collab/invitations/{invitation_id}/revoke`
- `GET /api/v1/company/profile`
- `GET /api/v1/company/profile/logo`
- Admin: `GET/POST /admin/companies/{company_id}/profile`

**Produktive Deployment-Reihenfolge ist verbindlich:**
1. aktuelles PostgreSQL-Backup erstellen und mit `pg_restore -l` prüfen
2. Migration 010 mit `psql -v ON_ERROR_STOP=1` ausführen
3. `server/checks/02_verify_schema10.sql` erfolgreich ausführen
4. erst danach den neuen Servercode mit `14_part.py` produktiv aktivieren/rebuilden
5. API/Admin extern prüfen

Grund: der neue Collaboration-Context liest `lead_name_ordinal`; neuer Servercode darf deshalb nicht vor der DB-Migration produktiv aktiviert werden.

## Zentrales Firmenprofil / Logo – im Feature-Branch implementiert
Server:
- zentral pro `company_id`: Anschrift 1/2, PLZ, Ort, Telefon, E-Mail, Webseite
- Logo als PNG/JPG/WEBP, maximal 5 MB
- Logo-Datei liegt im Server-Storage; DB enthält nur kontrollierten Pfad, MIME, SHA und Versionsnummer
- Admin-Firmenübersicht verlinkt auf **`Firmenprofil / Logo`**
- neue Vorlage `server/templates/company_profile.html`

Android:
- `TeamManagementApi.kt` lädt Firmenprofil und Logo.
- `CompanyProfileSync.kt` spiegelt das zentrale Firmenprofil in die bereits bestehende lokale `NutzerSpeicher`-/`NutzerProfil`-Quelle.
- bestehende BTB-/PDF-Funktionen können damit weiterhin dieselbe lokale Quelle offline verwenden.
- `maxFotosInPdf` bleibt eine lokale Nutzer-Einstellung und wird durch das Firmenprofil nicht überschrieben.
- serverseitig geladenes Logo wird lokal als `firma_server_logo.*` gecacht.

Noch zu prüfen/ergänzen:
- neuer Android-Code muss lokal gebaut werden.
- Firmenprofil-/Logo-Abruf muss auf echtem Gerät gegen Schema 10 getestet werden.
- Profil-Synchronisation erfolgt derzeit beim Baustellen-Sync; zusätzlich soll der produktive E2E-Test prüfen, ob der gewünschte direkte Abruf unmittelbar nach Geräteaktivierung noch ergänzt werden muss.

## Android – `Verantwortliche TBs / Helfer einladen` im Feature-Branch implementiert
Neue API-Klasse:
- `app/src/main/java/de/tbhelfer/server/TeamManagementApi.kt`

Erweiterte Ansicht:
- `BaustellenSyncFragment.kt`
- `fragment_baustellen_sync.xml`
- `collab_strings.xml`

Funktionen:
- pro Server-Baustelle alle verantwortlichen TB-Gruppen und Helfer anzeigen
- eigene Gruppe markieren
- nur `LEAD` sieht `Helfer einladen` und `Weiteren TB einladen`
- Helfer können Teamdaten sehen, aber keine Mitglieder einladen
- Einladung wird als sicherer TBI-Code plus `tbhelfer://invite?...`-Link erzeugt und über Android Teilen weitergegeben
- Einladung kann alternativ manuell per TBI-Code eingelöst werden
- nach erfolgreichem Einlösen wird die Baustellen-Synchronisation erneut gestartet

Deep-Link:
- Manifest akzeptiert zusätzlich `tbhelfer://invite?code=...`
- `AktivierungActivity` verarbeitet den Einladungslink
- bereits aktiviertes Gerät löst die Einladung direkt ein
- noch nicht aktiviertes Gerät muss zuerst mit seinem persönlichen Aktivierungsschlüssel aktiviert werden; danach wird die bereits erkannte Baustelleneinladung eingelöst
- Aktivierungs- und Baustelleneinladung bleiben getrennte Sicherheitsvorgänge

Noch offen:
- sichtbare QR-Karte/QR-Scanner für Baustelleneinladungen; Link und TBI-Code verwenden bereits dieselbe sichere Einladung und bilden die Grundlage dafür.
- komfortable Auswahl eines engeren Helfer-Arbeitsbereichs beim Einladen; Server-/DB-Prüfungen für `area_scope` sind bereits vorhanden.

## Verbindliche BTB-Logik – final korrigiert am 2026-09-10
Die vorherigen Ideen `-2/-3/-4` sowie eine eigene BTB-Identität pro Baustellenabschnitt sind verworfen.

Fachliche BTB-Identität innerhalb einer Baustelle:
**verantwortlicher TB / internes TB-Team + Datum + Schicht**.

Regeln:
- Pro Baustelle, verantwortlichem TB, Datum und Schicht gibt es genau **einen** gemeinsamen BTB.
- Ein Helfer erzeugt keinen eigenen BTB. Seine Texte, Zeiten, Fotos usw. werden in den BTB seines verantwortlichen TB synchronisiert.
- Arbeitsbereiche, Gleise, Weichen, KM und Abschnitte sind **Inhalt des BTB** und nicht Teil seiner Identität.
- Ein verantwortlicher TB kann mit vielen Helfern gleichzeitig an mehreren Stellen einer sehr großen Baustelle arbeiten; alle Einträge dieser Schicht gehören trotzdem in seinen einen BTB dieses Tages.
- Beispiel: Jens Zinkel + 30 Helfer arbeiten TAG, Max Müller + 30 Helfer arbeiten NACHT. Für einen Kalendertag entstehen genau zwei fachliche BTBs: Jens TAG und Max NACHT.
- Bei 30 Arbeitstagen entstehen entsprechend 30 TAG-BTBs von Jens und 30 NACHT-BTBs von Max.
- Kein künstlicher `-2/-3/-4`-Bericht für denselben verantwortlichen TB, Tag und dieselbe Schicht.
- BTB bleibt technisch über stabile IDs zugeordnet; menschenlesbare Bezeichnung ist nur Anzeige/Export.

Beispiele Anzeige:
- `BTB Jens Zinkel · 05.06.2026 · Tag`
- `BTB Max Müller · 05.06.2026 · Nacht`

Beispiele Export:
- `BTB_2026-06-05_TAG_Jens_Zinkel.pdf`
- `BTB_2026-06-05_TAG_Jens_Zinkel.docx`
- `BTB_2026-06-05_NACHT_Max_Mueller.pdf`
- `BTB_2026-06-05_NACHT_Max_Mueller.docx`

Bei gleichnamigen verantwortlichen TBs darf der sichtbare Zusatz verwendet werden:
- `BTB_2026-06-05_TAG_Max_Mustermann_1.pdf`
- `BTB_2026-06-05_TAG_Max_Mustermann_2.pdf`

Die interne UUID/Team-ID bleibt trotzdem maßgeblich und muss beim späteren Exportdesign als Kollisionsschutz berücksichtigt werden.

PDF **und DOCX** verwenden dieselbe Benennungs- und Zuordnungslogik. Der Dokumentkopf soll mindestens enthalten:
- Firma
- Baustelle
- verantwortlicher TB
- Datum
- Schicht
- optional das zentral hinterlegte Firmenlogo

Arbeitsbereiche/Abschnitte werden aus den fachlichen BTB-Einträgen dargestellt bzw. zusammengefasst, nicht zur Erzeugung eines zweiten BTB verwendet.

Historische bestehende BTBs werden nicht rückwirkend umbenannt; neue BTBs verwenden die neue Logik.

## Merge-sicheres Langzeitarchiv auf dem Laptop
Das Ziel ist ein langfristig wachsender gemeinsamer Ordner **`TB-Helfer`** auf dem Laptop.

Grundstruktur:
- jeder App-Gesamtexport enthält `TB-Helfer/<Baustelle>/...`
- Pasing-Export ergänzt `TB-Helfer/Pasing/...`
- späterer Gessertshausen-Export ergänzt `TB-Helfer/Gessertshausen/...`
- Exporte verschiedener Benutzer derselben Baustelle werden in denselben Baustellenordner entpackt und müssen sich ohne fachliche Dateikollision ergänzen

Für BTB bedeutet das:
- Jens' TAG-BTB und Max' NACHT-BTB haben wegen Datum + Schicht + verantwortlichem TB unterschiedliche stabile Dateinamen.
- Wird derselbe logische BTB erneut exportiert, verwendet er denselben Zielpfad und aktualisiert denselben Bericht statt eine zweite Kopie mit Zufallsnamen anzulegen.
- Unterschiedliche logische BTBs dürfen niemals denselben Zielpfad erhalten.
- gleichnamige verantwortliche TBs dürfen nicht nur über den Namen unterschieden werden; intern bleibt Team-/Benutzer-ID der Kollisionsschutz.

Diese Merge-Sicherheit gilt **für alle Exportarten**, nicht nur BTB:
- Bilder dürfen nicht nur Kamera-Namen oder exportabhängige laufende Nummern wie `B001` verwenden; sie brauchen stabile eindeutige IDs/Exportnamen.
- Mängel, Abnahmen und deren Anlagen benötigen ebenfalls stabile collision-sichere Pfade.

Ziel: ZIPs von Jens, Max und weiteren Beteiligten können über Monate/Jahre in denselben `TB-Helfer`-Archivordner entpackt werden, ohne dass fachlich verschiedene Dateien verloren gehen oder versehentlich überschrieben werden.

## Benutzer-/Firmenprofil – verbindliche UX-Entscheidung
- Nach Geräteaktivierung kommen vorhandene Benutzer-/Firmenstammdaten automatisch vom Server.
- Benutzeranzeige mindestens: Name, E-Mail soweit vorhanden, Firma, Zugriffsstatus, Ablaufdatum/Restlaufzeit.
- Firmenprofil zentral: Firmenname, Anschrift, Telefon, E-Mail, Firmenlogo.
- Firmenlogo wird einmal serverseitig gepflegt und an Benutzer derselben Firma verteilt/gecached.
- Supportansicht zeigt nur nicht geheime Geräte-/Supportkennung und höchstens einen maskierten Key-Hinweis.
- Aktivierungs-QR und Baustellen-QR bleiben getrennt: Aktivierung lädt Profil; Baustellen-QR fügt nur Baustellen-/Team-Mitgliedschaft hinzu.

## Nächste sichere Schritte
1. **Keine produktive Migration 010 ohne neues Backup.** Zuerst Schema-10-Migration/Prüfung auf einer sicheren Test-/Backup-Basis durchspielen.
2. neuen Android-Code lokal in Android Studio bauen; Compilefehler vor jeder NAS-Änderung beheben.
3. Link-/Code-Einladung mit zwei Testnutzern derselben Firma gegen eine Schema-10-Testinstanz prüfen.
4. engeren Helfer-Arbeitsbereich in der Einladungs-UI komfortabel auswählbar machen.
5. Baustellen-Einladungs-QR auf denselben TBI-Code/Deep-Link setzen.
6. BTB-Anzeige sowie PDF- und DOCX-Erzeugung auf `Baustelle + TB-Team + Datum + Schicht` umstellen; Helfereinträge müssen in denselben BTB ihres verantwortlichen TB aggregieren.
7. Gesamtexport/ZIP merge-sicher machen: stabiler `TB-Helfer/<Baustelle>/...`-Pfad und collision-sichere Namen für BTB, Bilder, Mängel und Abnahmen.
8. nach erfolgreichem Test neues produktives DB-Backup erstellen, Migration 010 mit `ON_ERROR_STOP` ausführen, Schema-10-Check ausführen und erst danach Servercode aktivieren.
9. neuen Android-Build auf beiden Testgeräten installieren.
10. echten Zwei-Geräte-/Mehrhelfer-Test durchführen: Firmenbindung, Einladungen, getrennte TB-BTBs, Helferaggregation, Tag/Nacht, gleichnamige TBs, Merge-Export, Mängel/Abnahmen/Bilder, 409, Tombstones, Netzabbruch/Wiederaufnahme, Benachrichtigungen.
11. Draft-PR #5 bis zum erfolgreichen Ende-zu-Ende-Test Draft lassen; `main` vorher nicht ändern.

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
- keine firmenweite Verteilung von Mängeln/Abnahmen.
- BTB bleibt TB-Team-bezogen.
- Helfer schreiben in den gemeinsamen BTB ihres verantwortlichen TB; kein eigener Helfer-BTB.
- BTB-Identität innerhalb einer Baustelle = TB-Team + Datum + Schicht; Abschnitt/Arbeitsbereich ist BTB-Inhalt, nicht BTB-Identität.
- sichtbare Benutzernamen sind nie technische Schlüssel; UUIDs/Team-IDs bleiben maßgeblich.
- ZIP-/Laptop-Archiv muss merge-sicher sein und auf `TB-Helfer/<Baustelle>/...` zusammenführbar bleiben.
- Baustellen-/Teambeitritt per Self-Service-Einladung, nicht per täglicher Admin-Zuordnung.
- Benutzer-/Firmenstammdaten kommen nach Aktivierung aus dem Serverprofil; Firmenlogo zentral serverseitig.
- Server-Speicheroptionen bleiben vorgesehen, sind aber keine Arbeitsfreigabe.
- temporäres Server-Spooling bleibt erlaubt.
- `main` stabil halten; große Änderungen über Feature-Branch/Draft-PR.

## Sitzungsregel für ChatGPT
Bei einem neuen Chat nicht aus alten Chat-Zusammenfassungen allein weiterarbeiten. Zuerst Projektregeln, diese Datei, Draft-PR und neuesten Commit in GitHub prüfen. Bei Widersprüchen gilt der produktive NAS-Stand für tatsächlich laufenden Server/DB-Stand, die Projektregeln für fachliche Entscheidungen und der Feature-Branch für noch nicht produktive Entwicklung.
