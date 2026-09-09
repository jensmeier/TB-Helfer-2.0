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

Diese Merge-Sicherheit gilt **für alle Exportarten**, nicht nur BTB:
- Bilder dürfen nicht nur Kamera-Namen wie `IMG_1234.jpg` verwenden; sie brauchen stabile eindeutige IDs/Exportnamen.
- Mängel, Abnahmen und deren Anlagen benötigen ebenfalls stabile collision-sichere Pfade.
- Bei tatsächlich gleichen sichtbaren Benutzernamen bleibt intern eine stabile nicht geheime Benutzer-/Team-ID maßgeblich; nur im Kollisionsfall kann eine kurze Kennung zusätzlich im Exportnamen erscheinen.

Ziel: ZIPs von Jens, Max und weiteren Beteiligten können über Monate/Jahre in denselben `TB-Helfer`-Archivordner entpackt werden, ohne dass fachlich verschiedene Dateien verloren gehen oder versehentlich überschrieben werden.

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

Für die weitere BTB-Umsetzung ist **kein eigener Abschnittsschlüssel für die BTB-Identität nötig**. Schema 9 besitzt bereits `group_id`, `entry_date` und `shift`; die nächste Implementierung muss diese Kombination mit `worksite_id` als fachliche BTB-Zuordnung behandeln.

## Nächste sichere Schritte
1. Self-Service-Einladungen auf dem Feature-Branch implementieren (Migration 010 + Server-API).
2. Android-UI ergänzen: `Verantwortliche TBs`, `Helfer einladen`, `Weiteren TB einladen`, QR/Link/Code.
3. Benutzer-/Firmenprofil-Autofill und Firmenlogo ergänzen.
4. BTB-Anzeige sowie PDF- und DOCX-Erzeugung auf `Baustelle + TB-Team + Datum + Schicht` umstellen; Helfereinträge müssen in denselben BTB ihres verantwortlichen TB aggregieren.
5. Gesamtexport/ZIP merge-sicher machen: stabiler `TB-Helfer/<Baustelle>/...`-Pfad und collision-sichere Namen für BTB, Bilder, Mängel und Abnahmen.
6. neuen Android-Build lokal bauen und auf beiden Testgeräten installieren.
7. echten Zwei-Geräte-/Mehrhelfer-Test durchführen: Firmenbindung, Einladungen, getrennte TB-BTBs, Helferaggregation, Tag/Nacht, Merge-Export, Mängel/Abnahmen/Bilder, 409, Tombstones, Netzabbruch/Wiederaufnahme, Benachrichtigungen.
8. Draft-PR #5 bis zum erfolgreichen Ende-zu-Ende-Test Draft lassen; `main` vorher nicht ändern.

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
- ZIP-/Laptop-Archiv muss merge-sicher sein und auf `TB-Helfer/<Baustelle>/...` zusammenführbar bleiben.
- Baustellen-/Teambeitritt per Self-Service-Einladung, nicht per täglicher Admin-Zuordnung.
- Benutzer-/Firmenstammdaten kommen nach Aktivierung aus dem Serverprofil; Firmenlogo zentral serverseitig.
- Server-Speicheroptionen bleiben vorgesehen, sind aber keine Arbeitsfreigabe.
- temporäres Server-Spooling bleibt erlaubt.
- `main` stabil halten; große Änderungen über Feature-Branch/Draft-PR.

## Sitzungsregel für ChatGPT
Bei einem neuen Chat nicht aus alten Chat-Zusammenfassungen allein weiterarbeiten. Zuerst Projektregeln, diese Datei, Draft-PR und neuesten Commit in GitHub prüfen. Bei Widersprüchen gilt der produktive NAS-Stand für tatsächlich laufenden Server/DB-Stand, die Projektregeln für fachliche Entscheidungen und der Feature-Branch für noch nicht produktive Entwicklung.
