# TB-Helfer – aktueller Projektstand

Stand: 2026-09-09

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
- letzter fachlicher Stand vor diesem Übergabe-Update: `a6d0a57d98fc60870ec9bcae6e10dc988ee4d2ac`
- `main` wurde durch die aktuellen Arbeiten nicht verändert.

## Produktiver Stand auf der Synology
- NAS/Synology bleibt Source of Truth.
- produktive API: `0.6.0`
- produktives DB-Schema: `7` (`007_admin_crud_sync_delete`)
- Migrationen `008` und `009` wurden produktiv NICHT ausgeführt.
- Keine produktive Migration ohne unmittelbar vorher erzeugtes/geprüftes Backup.
- Produktiven NAS-Stand nicht aus GitHub-Annahmen ersetzen.

## Android-Stand
- Android `versionCode 200018` ist im Draft-Branch vorbereitet.
- 200018 wurde lokal erfolgreich kompiliert, als Update installiert und gegen produktive API 0.6.0 auf Grundfunktionen geprüft; die danach hinzugekommenen Härtungen müssen vor dem Zwei-Geräte-Test erneut lokal gebaut werden.
- separate Room-Datenbank `tbhelfer-sync.db`; bestehende Baustellen-/Vermessungsdatenbank bleibt unangetastet.
- Room-Migrationspfad der Sync-Datenbank ist 1 -> 2 -> 3 vorhanden; Worksite-Bindings und Collab-Cache werden getrennt von der bestehenden Fachdatenbank geführt.
- Offline-Katalog, selektiver Download, Upload-Queue, Version/Basisversion, 409-Grundlage, Tombstones und Hintergrund-Sync sind vorbereitet.
- Unterbrochene Uploads im Zustand `UPLOADING` werden beim nächsten Sync erneut aufgenommen; Idempotenz über `client_change_id` verhindert serverseitige Doppelanwendung.
- Gemeinsame BTB-Textänderungen erhalten jetzt die gerätelokalen `fotos`- und `pdfAuswahl`-Referenzen; diese lokalen Medien werden weiterhin nicht in den Server-Payload/Hash übernommen und erzeugen keinen Sync-Echo.
- Mängelfoto-KM kann beim Sync zusätzlich im üblichen Format `70+698` ausgewertet werden.
- Benachrichtigungseinstellungen pro Gerät: `Text + Ton` / `nur Ton` / `aus`.

## Verbindliches Team-/Rechtemodell
- Baustellen-Teams ausschließlich innerhalb derselben Firma.
- Eine Baustelle kann TB-Gruppen A/B/C/D/... haben.
- Jeder TB kann Hilfsnutzer haben.
- BTB bleibt strikt gruppenbezogen: A + seine Hilfsnutzer teilen BTB A; B + seine Hilfsnutzer BTB B usw. Kein Vermischen zwischen A/B/C/D.
- Mängel und Abnahmen werden baustellenweit zwischen den TB-Gruppen synchronisiert, Hilfsnutzer jedoch nur innerhalb ihres berechtigten Bereichs.
- Bilder/Anlagen werden mit synchronisiert.
- endgültige Status werden nicht wieder geöffnet.

## Letzte Rechte-/Sync-Härtungen
- Commit `0f3f215b027bfcd06875c3c04c2a09d9d99116f0`: Firmenbindung/Teamzuordnung serverseitig gehärtet.
  - Firmenwechsel darf keine alten firmenübergreifenden Teamzuordnungen zurücklassen.
  - Nur aktive Benutzer derselben Firma dürfen Gruppenmitglieder/Leads sein.
  - Statusfelder für BTB, Mängel und Abnahmen sind eingeschränkt.
- Commit `2ad7c9254fe000828f2b6b41b226f54ca2c7d38f`: Mängel-/Abnahme-Bereich beim Android-Sync präzisiert.
  - Ein Haupt-TB mit `{all:true}` erzeugt für einzelne Mängel/Abnahmen nach Möglichkeit einen engeren Bereich aus vorhandenen Fachinformationen (Bereich/Gleis/Weiche/KM).
  - Ein ausdrücklich eingeschränkter Hilfsnutzer-Bereich wird niemals erweitert.
  - Der lokale Sync-Cache unterstützt einen eigenen `areaScopeJson` je synchronisiertem Vorgang.
- Commit `778f76b59419dbd3df29f8ca6537411971f89ed6`: Migration 009 auf Datenbankebene weiter gehärtet.
  - BTB-Gruppe muss zur selben Baustelle gehören.
  - Attachment-Parent muss existieren und zur selben Baustelle gehören.
  - Change-Log-Gruppe darf nicht zu einer anderen Baustelle gehören.
  - `ERLEDIGT`, `ABGENOMMEN` und `NICHT_ABGENOMMEN` werden zusätzlich per DB-Trigger gegen Wiederöffnung geschützt.
  - Sperrung/Deaktivierung/Rollenwechsel eines Benutzers löst unzulässige aktive Teamzuordnungen.
  - JSON-/Versions-/Größen-/Sequenzwerte werden stärker validiert.
- Commit `caaa8de7c8390e831462f33462323c9ea7781dae`: unterbrochene Android-Uploads werden sicher wiederaufgenommen.
  - gilt für allgemeinen inkrementellen Sync und den Baustellen-Collab-Sync.
- Commit `6ad5c6b7d909fcda14b01c481c2cfbc995887969`: Migration 008 validiert Versions-, JSON- und HTTP-Metadaten strenger.
- Commit `0651507416d034fe1383c8b1b9a5912eac2f5578`: read-only Preflight für Schema 7 vor Migration 008/009.
- Commit `4943913462781af38ac9df4d21003236d1d85f8c`: read-only Nachkontrolle für Schema 9 und Integritätsverletzungen.
- Commit `ab98ef1c69539b771c918ff8da145ad68270758a`: verbindlicher Android-200018/Server-0.7-API-Vertrag dokumentiert.
  - Kontext, Changes, ACK, Mutationen, 409, Attachments und Idempotenz sind festgelegt.
  - der Change-Log-Cursor muss auch über nicht sichtbare/fremde Änderungen fortschreiten, damit z. B. BTB A nicht an BTB B hängen bleibt.
- Commit `a6d0a57d98fc60870ec9bcae6e10dc988ee4d2ac`: lokale BTB-Medien beim Import geschützt.
  - eingehender gemeinsamer BTB-Inhalt überschreibt nicht mehr die gerätelokalen Foto-/PDF-Referenzen.
  - der kanonische Sync-Hash bleibt medienfrei und verhindert Echo-Uploads.
  - `km` im Format `70+698` wird für synchronisierte Mängelfotos korrekt in Dezimal-km überführt.

## Server-0.7-Vorbereitung
- `server/migrations/008_incremental_sync_mutations.sql` ist im Draft-Branch vorhanden und gehärtet.
- `server/migrations/009_worksite_collaboration.sql` ist im Draft-Branch vorhanden und gehärtet.
- `server/checks/00_preflight_schema7_for_008_009.sql` prüft die Voraussetzungen vor einer Migration read-only.
- `server/checks/01_verify_schema9.sql` prüft Schema 9 und Integrität nach einer Testmigration read-only.
- `server/API_0.7_VERTRAG.md` beschreibt den verbindlichen Serververtrag, den Android 200018 bereits erwartet.
- GitHub-Suche sowie Prüfung der vorhandenen Feature-Branches haben bestätigt: Der vollständige produktive API-Quellstand 0.6.0 war bisher nicht im Repository abgelegt.
- Im GitHub-Ordner `server/` liegen deshalb weiterhin noch nicht die produktiven API-/Docker-/Runtime-Dateien aus `/volume1/docker/tb-helfer/server/`.
- Server 0.7 darf weiterhin NICHT aus Annahmen oder als neuer fremder Server-Unterbau geschrieben werden.

## Nächster notwendiger Schritt
Den tatsächlichen produktiven Quellstand aus
`/volume1/docker/tb-helfer/server/`
in den aktiven Feature-Branch übernehmen, ohne Secrets.

Vor Übernahme ausschließen/niemals committen:
- `.env` / `*.env`
- Passwörter
- Cloudflare-/Tunnel-Token
- SMTP-Zugangsdaten
- Signing-Keys / private Schlüssel
- sonstige Secrets

Danach:
1. echten Server-0.6.0-Code gegen `server/API_0.7_VERTRAG.md` und Migration 008/009 abgleichen,
2. vollständiges Server-0.7-API-Paket im vorhandenen Server-Framework implementieren,
3. Preflight + Migrationen zunächst auf Backup/Testkopie prüfen,
4. Android 200018 mit den neuen Queue-/BTB-Härtungen erneut lokal bauen,
5. echten Zwei-Geräte-Test durchführen,
6. BTB-Gruppentrennung, Mängel-/Abnahme-Bereich, Bilder, 409, Tombstones und unterbrochene Uploads Ende-zu-Ende testen,
7. erst nach erfolgreichem Test und unmittelbar vorherigem Backup produktive NAS-Migration erwägen.

## Bahnhofsdaten – nicht neu erfinden
Vorhandene Datenbasis verwenden:
- `app/src/main/assets/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
- `app/src/main/assets/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`
- `app/src/main/java/de/tbhelfer/util/BahnhofSucheUtil.kt`

Server-Admin soll daraus Bahnhofscode/Name automatisch vervollständigen.

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
- BTB bleibt TB-gruppenbezogen.
- Mängel/Abnahmen baustellenweit im berechtigten Bereich.
- Server-Speicheroptionen pro Benutzer bleiben vorgesehen: Bilder, BTB, Mängel, Abnahmen, Alles.
- temporäres Server-Spooling ist auch ohne dauerhafte Speicherung erlaubt.
- `main` stabil halten; große Änderungen über Feature-Branch/Draft-PR.

## Sitzungsregel für ChatGPT
Bei einem neuen Chat nicht aus einer alten Chat-Zusammenfassung allein weiterarbeiten. Zuerst GitHub prüfen und mindestens Projektregeln, diese Statusdatei und den aktiven Draft-PR lesen. Bereits getroffene Entscheidungen nicht erneut erfinden oder abfragen. Nach substanziellen Änderungen diesen Projektstand im selben Feature-Branch aktualisieren.
