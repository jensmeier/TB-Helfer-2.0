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
- aktueller Head: `2ad7c9254fe000828f2b6b41b226f54ca2c7d38f`
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
- 200018 wurde lokal erfolgreich kompiliert, als Update installiert und gegen produktive API 0.6.0 auf Grundfunktionen geprüft.
- separate Room-Datenbank `tbhelfer-sync.db`; bestehende Baustellen-/Vermessungsdatenbank bleibt unangetastet.
- Offline-Katalog, selektiver Download, Upload-Queue, Version/Basisversion, 409-Grundlage, Tombstones und Hintergrund-Sync sind vorbereitet.
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

## Server-0.7-Vorbereitung
- `server/migrations/008_incremental_sync_mutations.sql` ist im Draft-Branch vorhanden.
- `server/migrations/009_worksite_collaboration.sql` ist im Draft-Branch vorhanden und gehärtet.
- Im GitHub-Ordner `server/` liegt derzeit noch NICHT der vollständige produktive API-Quellstand 0.6.0; vorhanden sind im Wesentlichen README, Bahnhofsdaten und Migrationen.
- Deshalb darf Server 0.7 NICHT aus Annahmen neu geschrieben werden.

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

Erst danach:
1. echten Server-0.6.0-Code gegen Migration 008/009 abgleichen,
2. vollständiges Server-0.7-API-Paket im Feature-Branch bauen,
3. lokal/isoliert testen,
4. echten Zwei-Geräte-Test durchführen,
5. 409-Konflikte/Tombstones/Attachments Ende-zu-Ende testen,
6. erst nach Backup produktive NAS-Migration erwägen.

## Bahnhofsdaten – nicht neu erfinden
Vorhandene Datenbasis verwenden:
- `app/src/main/assets/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
- `app/src/main/assets/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`
- `app/src/main/java/de/tbhelfer/util/BahnhofSucheUtil.kt`

Server-Admin soll daraus Bahnhofscode/Name automatisch vervollständigen.

## Vermessungs-/Kilometrierungsfarben
- Grün = belastbare Originaldaten/KM
- Orange = manuell geändert/hergestellt
- Rot = TRA ohne belastbare KM
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