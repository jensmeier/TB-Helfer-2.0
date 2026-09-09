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

## Produktiver Stand auf der Synology
- NAS/Synology ist Source of Truth.
- produktive API: **0.7.0**.
- produktive PostgreSQL-Datenbank: **Schema 9** (`009_worksite_collaboration`).
- produktiver Servercode wird aktuell aus `/volume1/docker/tb-helfer/server_0.7_stage` gebaut/eingebunden.
- produktive Secret-Datei bleibt unter `./server/.env`; keine Secrets wurden in Stage/GitHub kopiert.
- Cloudflare API und Cloudflare Admin Access wurden von außen über Mobilfunk erfolgreich geprüft.
- API, PostgreSQL, Schema 9, Bahnhof-Autofill und Admin-Dashboard wurden produktiv erfolgreich getestet.

### Backups / Migrationen
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

## Produktive API 0.7
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

## Android-Stand
- Android `versionCode 200018`, `versionName 1.3` im Draft-Branch.
- aktueller Build wurde lokal in Android Studio erfolgreich als Debug-APK gebaut (`BUILD SUCCESSFUL`).
- dieselbe APK wurde auf beiden Test-Handys als Update installiert.
- vorhandene lokale Baustellen-/Vermessungsdaten blieben erhalten.
- beide Geräte starten fehlerfrei und verbinden sich mit Server 0.7.0.
- separate Room-Datenbank `tbhelfer-sync.db`; bestehende Baustellen-/Vermessungsdatenbank bleibt unangetastet.
- Upload-Queue, 409-Grundlage, Tombstones, Hintergrund-Sync und Benachrichtigungen sind vorbereitet.

## Verbindliches Team-/Rechtemodell – aktueller Stand
- Baustellen-Teams ausschließlich innerhalb derselben Firma.
- Der Admin verwaltet Benutzer/Firmen/Geräte und Speicheroptionen, aber nicht die laufende tägliche Baustellen-Zuordnung.
- Der Ersteller einer Baustelle ist ihr erster verantwortlicher TB.
- Weitere verantwortliche TBs treten per sicherer QR-/Link-/Code-Einladung bei; der Admin ist dafür nicht erforderlich.
- Sichtbare Bezeichnung: **`Verantwortliche TBs`** mit echten Namen, keine sichtbaren A/B/C/D-Gruppen.
- Bei jedem verantwortlichen TB: **`Helfer einladen`**.
- Zusätzlich: **`Weiteren TB einladen`**.
- Helfer werden genau dem Team des einladenden verantwortlichen TB zugeordnet und erhalten ihren freigegebenen Arbeitsbereich.
- Benutzer derselben Firma ohne angenommene Einladung zu dieser konkreten Baustelle erhalten keine Collaboration-Daten dieser Baustelle.
- Mängel/Abnahmen/Bilder/Benachrichtigungen werden nur an berechtigte Mitglieder dieser einen Baustelle verteilt; keine firmenweite Verteilung.
- Helfer sehen Mängel/Abnahmen nur innerhalb ihres freigegebenen Bereichs.

## Verbindliche BTB-Logik – korrigiert am 2026-09-10
Die frühere Idee, mehrere Berichte desselben TB am selben Tag nur über `-2/-3/-4` zu unterscheiden, ist verworfen.

Fachliche Identität eines BTB:
**verantwortlicher TB / internes TB-Team + Baustellenabschnitt bzw. Arbeitsbereich + Datum + Schicht**.

Regeln:
- Pro identischer Kombination gibt es genau **einen** gemeinsamen BTB.
- Ein Helfer erzeugt keinen eigenen BTB. Seine Texte, Zeiten, Fotos usw. werden in den BTB seines verantwortlichen TB synchronisiert.
- 30 Helfer eines verantwortlichen TB können gemeinsam in genau einen TAG-BTB schreiben.
- Ein anderer verantwortlicher TB hat seinen eigenen BTB, z. B. NACHT.
- Ein weiterer BTB am selben Datum entsteht nur, wenn sich verantwortlicher TB, Abschnitt/Arbeitsbereich oder Schicht unterscheidet.
- Bei sehr großen Baustellen (z. B. 100 km) können mehrere Abschnitte parallel jeweils ihren eigenen BTB haben.
- BTB bleibt technisch über stabile IDs zugeordnet; menschenlesbare Bezeichnung ist nur Anzeige/Export.

Beispiele Anzeige:
- `BTB Jens Zinkel · Abschnitt West · 05.06.2026 · Tag`
- `BTB Max Müller · km 50–100 · 05.06.2026 · Nacht`

Beispiele Export:
- `BTB_Jens_Zinkel_Abschnitt_West_2026-06-05_TAG.pdf`
- `BTB_Jens_Zinkel_Abschnitt_West_2026-06-05_TAG.docx`
- `BTB_Max_Mueller_km_50-100_2026-06-05_NACHT.pdf`
- `BTB_Max_Mueller_km_50-100_2026-06-05_NACHT.docx`

PDF **und DOCX** sollen dieselbe Benennungs- und Zuordnungslogik verwenden. Der Dokumentkopf soll mindestens enthalten:
- Firma
- Baustelle
- verantwortlicher TB
- Abschnitt/Arbeitsbereich soweit vorhanden
- Datum
- Schicht
- optional das zentral hinterlegte Firmenlogo

Historische bestehende BTBs werden nicht rückwirkend umbenannt; neue BTBs verwenden die neue Logik.

## Benutzer-/Firmenprofil – verbindliche UX-Entscheidung
- Nach Geräteaktivierung kommen vorhandene Benutzer-/Firmenstammdaten automatisch vom Server.
- Benutzeranzeige mindestens: Name, E-Mail soweit vorhanden, Firma, Zugriffsstatus, Ablaufdatum/Restlaufzeit.
- Firmenprofil zentral: Firmenname, Anschrift, Telefon, E-Mail, Firmenlogo.
- Firmenlogo wird einmal serverseitig gepflegt und an Benutzer derselben Firma verteilt/gecached.
- Supportansicht zeigt nur nicht geheime Geräte-/Supportkennung und höchstens einen maskierten Key-Hinweis.
- Aktivierungs-QR und Baustellen-QR bleiben getrennt: Aktivierung lädt Profil; Baustellen-QR fügt nur Baustellen-/Team-Mitgliedschaft hinzu.

## Geplante Self-Service-Einladung – noch nicht produktiv
Der produktive Schema-9-Stand enthält Teams/Mitglieder, aber noch kein Einladungssystem. Nächster Serverbaustein ist eine neue Migration **010**, ohne die produktive Migration 009 umzuschreiben.

Geplant:
- Einladungstabelle mit gehashtem Token, `worksite_id`, Einladendem, Einladungsart, optionalem Ziel-Team/Bereich, Ablauf, Nutzungsgrenze, Widerruf
- `Weiteren TB einladen` -> neuer verantwortlicher TB mit eigenem internem TB-Team/BTB-Bereich
- `Helfer einladen` -> Aufnahme in das bestehende TB-Team des Einladenden mit Bereichsfreigabe
- QR, Link und kurzer Code sind Darstellungen derselben sicheren Einladung
- Server prüft: aktiver Benutzer, gültiges Gerät, gleiche Firma, Einladung gültig/nicht widerrufen/nicht abgelaufen

Für die BTB-Identität muss bei der weiteren Umsetzung der **Baustellenabschnitt/Arbeitsbereich** neben `entry_date` und `shift` sauber mitgeführt werden. Schema 9 besitzt bereits `group_id`, `entry_date`, `shift` und `payload`; für die endgültige eindeutige BTB-Zuordnung ist im Rahmen des nächsten Bausteins zu prüfen, ob ein eigener stabiler Abschnittsschlüssel ergänzt werden soll.

## Nächste sichere Schritte
1. Self-Service-Einladungen und BTB-Abschnittsidentität auf dem Feature-Branch implementieren (Migration 010 + Server-API).
2. Android-UI ergänzen: `Verantwortliche TBs`, `Helfer einladen`, `Weiteren TB einladen`, QR/Link/Code.
3. Benutzer-/Firmenprofil-Autofill und Firmenlogo ergänzen.
4. BTB-Anzeige sowie PDF- und DOCX-Erzeugung auf die neue Identität/Namenslogik umstellen; Helfereinträge müssen in denselben BTB ihres verantwortlichen TB aggregieren.
5. neuen Android-Build lokal bauen und auf beiden Testgeräten installieren.
6. echten Zwei-Geräte-/Mehrhelfer-Test durchführen: Firmenbindung, Einladungen, getrennte TB-BTBs, Helferaggregation, Tag/Nacht, mehrere Abschnitte, Mängel/Abnahmen/Bilder, 409, Tombstones, Netzabbruch/Wiederaufnahme, Benachrichtigungen.
7. Draft-PR #5 bis zum erfolgreichen Ende-zu-Ende-Test Draft lassen; `main` vorher nicht ändern.

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
- BTB-Identität = TB-Team + Abschnitt/Arbeitsbereich + Datum + Schicht.
- Baustellen-/Teambeitritt per Self-Service-Einladung, nicht per täglicher Admin-Zuordnung.
- Benutzer-/Firmenstammdaten kommen nach Aktivierung aus dem Serverprofil; Firmenlogo zentral serverseitig.
- Server-Speicheroptionen bleiben vorgesehen, sind aber keine Arbeitsfreigabe.
- temporäres Server-Spooling bleibt erlaubt.
- `main` stabil halten; große Änderungen über Feature-Branch/Draft-PR.

## Sitzungsregel für ChatGPT
Bei einem neuen Chat nicht aus alten Chat-Zusammenfassungen allein weiterarbeiten. Zuerst Projektregeln, diese Datei, Draft-PR und neuesten Commit in GitHub prüfen. Bei Widersprüchen gilt der produktive NAS-Stand für tatsächlich laufenden Server/DB-Stand, die Projektregeln für fachliche Entscheidungen und der Feature-Branch für noch nicht produktive Entwicklung.
