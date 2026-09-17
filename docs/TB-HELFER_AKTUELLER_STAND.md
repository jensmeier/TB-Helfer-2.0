# TB-Helfer – aktueller Projektstand

Stand: 2026-09-17

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen in `docs/TB-HELFER_PROJEKTREGELN.md`, die Arbeitsweise in `docs/TB-HELFER_PROJEKTANWEISUNG.md`, die geplante Reihenfolge in `docs/TB-HELFER_ROADMAP.md`. Der neue Server-Zielvertrag steht in `docs/TB-SERVER_1_0_ZIELMODELL.md`.

## Start jedes Chats / jeder Änderung

Zuerst prüfen/lesen:
1. `docs/TB-HELFER_PROJEKTANWEISUNG.md`
2. `docs/TB-HELFER_PROJEKTREGELN.md`
3. diese Datei
4. `docs/TB-SERVER_1_0_ZIELMODELL.md`, solange Server 1.0 aktiv entwickelt wird
5. aktiven Feature-Branch
6. offenen Draft-PR
7. neuesten relevanten Commit
8. Chat-Übergabe nur ergänzend

GitHub-Code und aktiver Branch sind Source of Truth. `main` stabil halten.

## Repository / Branch / PR

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- Vorgänger-Feature-Branch: `feature/admin-offline-sync`
- neuer aktiver Entwicklungsbranch: **`feature/tb-server-1.0`**
- Ausgangspunkt des neuen Branches: `d7fbc677b33ad9e3b3780f44223c28913bf0a4a3`
- bisheriger Draft-PR: `#5 – Admin + Offline-Sync Grundlage`
- neuer Draft-PR für Server 1.0 wird separat geführt
- Android-Testclient: Buildkandidat **200050**
- letzter vom Benutzer bestätigter Android-Studio-Build: grün, nur Warnungen
- Produktivserver/NAS weiterhin unverändert: API **0.7.0**, PostgreSQL **Schema 10**

## Strategiewechsel – verbindlich ab 17.09.2026

Die bisherige Strategie `Android zuerst, Server danach` wird beendet.

Neue Reihenfolge:

1. **TB-Server 1.0 vollständig als stabile gemeinsame Grundlage bauen**;
2. Server-End-to-End mit Testclients/Portal prüfen;
3. produktiven NAS-Stand nach Backup kontrolliert auf Server 1.0 umstellen;
4. danach Android systematisch gegen den festen Serververtrag anbinden;
5. anschließend Poco↔Pixel↔Portal-Praxistests und UI-Bereinigung.

Grund: Mehrere Android-Probleme waren keine isolierten UI-/Clientfehler, sondern Folgen eines noch unvollständigen gemeinsamen Servermodells für Unterbaustellen, Rechte, BTB-Lebenszyklus, aktiven Team-BTB, Outbox/Pull-Reihenfolge, Bilder und Streckendaten.

## Testdaten / Resetentscheidung

Der Benutzer hat ausdrücklich freigegeben:

- der derzeitige Serverbestand ist Testbestand;
- Testbenutzer, Testbaustellen und sonstige Testfachdaten dürfen beim TB-Server-1.0-Cutover verworfen werden;
- es ist keine aufwendige fachliche Migration dieses Testbestands nötig;
- **Originale Vermessungsdaten auf dem NAS bleiben unverändert und dürfen nicht gelöscht/überschrieben werden.**

Trotz dieser Freigabe gilt weiterhin die Projektregel: Vor jeder produktiven NAS-/PostgreSQL-Migration oder größeren Datenänderung vollständiges Backup erstellen und verifizieren.

## TB-Server 1.0 – Zielarchitektur

Vollständig dokumentiert in `docs/TB-SERVER_1_0_ZIELMODELL.md`.

Kernentscheidungen:

- modularer Monolith statt unnötiger Microservices;
- PostgreSQL/PostGIS + NAS-Dateispeicher;
- stabile `/api/v1`-API;
- Capability-Endpunkt statt Client-Abfragen nach internen Schemanummern;
- unveränderliche nummerierte Migrationen mit Prüfsummen;
- getrennte Fachmodule für Auth, Firmen, Benutzer, Geräte, Baustellen, Rechte, Teams, BTB, Dateien, Mängel, Abnahmen, Streckendaten, Releases, Portal, Admin, Audit und Backup;
- Change-Feed/Eventlog statt wiederholtem Vollsync;
- Idempotenz über `client_change_id`;
- lokale Client-Outbox vor Server-Pull;
- Live-Ereignissignal für offene Clients über WebSocket/SSE plus normaler Pull beim Start/Resume;
- große Dateien chunkweise statt riesiger Einzelrequests.

## Rollen / Rechte / Chef

Firmenrollen:

- `ADMIN` – Systemadministrator;
- `CHEF` – Firmenleitung;
- `USER` – normaler Firmenbenutzer.

CHEF:

- sieht automatisch alle Baustellen der eigenen Firma;
- mehrere CHEFs pro Firma möglich;
- Standard: lesen, PDF/Export, BTB/Mängel/Abnahmen/Bilder ansehen;
- Schreib-/Abschlussrechte erweiterbar.

Baustellenrollen:

- `RESPONSIBLE_TB`;
- `TB`;
- `HELPER`;
- `READ_ONLY`.

Rechte sind serverseitig kanonisch, werden aber **auch direkt auf dem Handy und im Portal verwaltet**. Rechtevorlagen plus individuelle Abweichungen pro Nutzer/Baustelle sind vorgesehen.

## Baustellenmodell

Baustellenweit gemeinsam:

- Hauptbaustelle;
- Unterbaustellen/kompletter Baum;
- Strecke/Gleis/Weichen/Objekte;
- benötigte mobile Streckendaten;
- Mängel;
- Abnahmen;
- Bilderliste gesamt;
- Mängelliste gesamt;
- Archive/Exporte.

Baustelle gehört genau einer Firma. Interne Zuordnung ausschließlich über stabile UUIDs, nicht sichtbare Namen.

Löschen:

- berechtigter Baustellenbenutzer löst Löschung aus;
- Server erzeugt Tombstone;
- andere Geräte entfernen dieselbe Baustelle;
- Papierkorb/Wiederherstellungsfrist vor endgültiger physischer Löschung;
- endgültige Löschung nur über kontrollierten Löschprozess.

## BTB 1.0

BTB bleibt pro verantwortlichem TB-Team getrennt.

Server hält pro Team explizit `active_btb_id`.

Ein verantwortlicher TB und seine Helfer bearbeiten denselben aktiven BTB. Andere verantwortliche TB-Teams derselben Baustelle führen eigene BTBs.

Wichtigste Änderung gegenüber dem heutigen Clientmodell:

**BTB wird serverseitig nicht mehr nur als ein großes überschreibbares JSON behandelt.**

Granulare Einträge mit eigener UUID/Version für:

- Kopf;
- Personal/Firmen;
- Maschinen/Geräte;
- Wetter;
- Abschnitt 4;
- Abschnitt 5;
- Abschnitt 6;
- Abschnitt 7;
- Abschnitt 8;
- Bilder/Anhänge.

Damit können Nutzer 1 und Helfer A gleichzeitig unterschiedliche BTB-Einträge bearbeiten, ohne sich gegenseitig den gesamten BTB zu überschreiben.

Lebenszyklus:

- `ACTIVE` → sichtbarer Text `BTB aktiv`;
- `FINALIZED` → abgeschlossen;
- `REWORK` → bewusste Nachbearbeitung derselben `btbId`;
- `DELETED` → Tombstone/Papierkorb.

Erster erfolgreicher Abschluss friert den unveränderlichen Original-Snapshot ein. Abschluss löst `active_btb_id`. Bewusste Nachbearbeitung reaktiviert dieselbe `btbId`.

## Sync-Ziel

Nicht `alle 5 Minuten alles synchronisieren`.

Normaler Ablauf:

1. lokal speichern;
2. in derselben lokalen Transaktion Outbox-Eintrag erzeugen;
3. bei Netz sofort hochladen;
4. Server speichert idempotent;
5. Server erzeugt Event;
6. andere offene Geräte/Portal erhalten Signal und laden nur geänderte Datensätze.

Fallback-Pull bei Start/Resume/Öffnen der Baustelle/des BTB. Periodischer Hintergrundsync nur als Sicherheitsnetz.

## TB-Helfer Portal

Portal wird vollwertiger Fachclient gegen dieselbe API.

Je nach Recht:

- Baustellen öffnen;
- BTB erstellen/bearbeiten/abschließen/nachbearbeiten;
- Punkte 4–8;
- Bilder vom PC hinzufügen;
- Mängel/Abnahmen bearbeiten/abschließen;
- PDF/DOCX/Archive;
- Rechteverwaltung;
- Streckendaten.

Nicht auf dem Portal:

- mobile Live-GPS-Aufnahme;
- Handy-Kamera-GPS.

PC-Bilder erhalten kein künstliches GPS.

Portal-Login ist getrennt vom Geräte-Key. Ziel: Benutzerkennung/E-Mail + Passwort nach sicherer Erstaktivierung; später Passkey/TOTP ergänzbar.

## Dateien / Firmenordner / Cloudflare

Jede Firma hat unabhängig von Benutzerzahl einen eigenen logischen Speicherbereich mit UUID-basierten Storage-Keys.

Große Binärdaten nicht als große PostgreSQL-BLOBs speichern. DB hält Metadaten, Hash, Zuordnung und Storage-Key.

Cloudflare Tunnel ist Transportweg, nicht Datenspeicher. Datenverkehr klein halten durch:

- Change Feed;
- nur geänderte Datensätze;
- Bilder nur einmal hochladen;
- Thumbnails;
- 8–16-MiB-Chunks für große Dateien/Archive;
- keine unnötigen Vollsyncs.

## Strecken-/Vermessungsdaten

Originale TRA/GRA/KF/KOR/KV/GVP bleiben unverändert.

Separat versionierte Arbeitsdaten:

- Achsen;
- Samples;
- KM/KM-Quelle;
- manuelle Korrekturen;
- bearbeitete Abschnitte;
- Streckenpunkte;
- grafischer Bearbeitungsstand.

Nur benötigte Daten werden auf Geräte übertragen.

## Administration 1.0

Admin und Benutzerportal bleiben getrennt.

Admin-Ziele:

- Übersicht Speicher gesamt/belegt/frei;
- Speicher je Firma;
- Upload/Download 24 h;
- API-Uptime;
- DB-/Storage-/Backupstatus;
- Syncqueue/Fehler;
- aktive Geräte/Clientversionen;
- Diagramme;
- Firmen/Benutzer/Geräte;
- Dateien;
- Baustellenstatus;
- App-Versionen;
- Papierkorb;
- Löschcenter;
- Audit;
- Systemstatus.

## Releases / Updates

Releaseverwaltung getrennt nach Plattform:

- Android;
- Windows optional später;
- iOS über Store/TestFlight/MDM-Verweis.

Release enthält Version, Build, SHA-256, Mindestbuild, optional/Pflichtupdate, Release Notes und Downloadziel.

## Backup / Restore

Backup umfasst PostgreSQL + Dateispeicher. Restore muss praktisch getestet werden.

Neues/ersetztes Handy kann nach Aktivierung Baustellen aus dem Serverbestand wiederherstellen.

## Android-Stand während Serverbau

Android 200050 bleibt vorerst Testclient.

Bereits vorhandene/teilweise bestätigte Funktionen werden nicht unnötig umgebaut:

- Live-KM;
- KF/GVP/Festpunktlogik;
- 10/30-m-Qualitätsampel;
- Kamera-/Foto-KM;
- Punkte 4–8 Bilderpfad;
- feste Buttonzeile `Bilder | Abbrechen | Hinzufügen`;
- Firmenlogo mit weißem PDF-Hintergrund.

Bekannte Android-Restthemen werden nach Server 1.0 systematisch bearbeitet:

- stabile Serverbindung Baustellen/Unterbaustellen;
- neuer granularer BTB-Sync;
- Einstellungen/Foto-Aufdruck-Persistenz;
- Rechte-UI;
- Fotos/Mängel/Abnahmen;
- Generated-Source-/Legacy-/CI-Bereinigung.

## Nächster sinnvoller Arbeitsschritt

1. neuen Draft-PR für `feature/tb-server-1.0` eröffnen;
2. vorhandenen Server 0.7.x gegen das Zielmodell inventarisieren: behalten / umbauen / entfernen;
3. neues Server-1.0-Schema als frischen Initialschema-Entwurf erstellen;
4. Migration Runner + Capabilities + Auth/Firma/Benutzer-Grundlage bauen;
5. danach die Servermodule in der Reihenfolge aus `TB-HELFER_ROADMAP.md` vollständig umsetzen;
6. erst nach vollständigen Server-Integrationschecks produktiven NAS-Reset/Cutover durchführen.

Keine produktive NAS-Migration ohne vorher bestätigtes Backup, auch wenn der aktuelle Fachbestand verworfen werden darf.
