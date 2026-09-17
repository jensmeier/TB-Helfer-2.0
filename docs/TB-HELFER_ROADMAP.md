# TB-Helfer – Entwicklungsroadmap

Stand: 2026-09-17

Diese Roadmap beschreibt die verbindliche Reihenfolge ab dem Wechsel auf **TB-Server 1.0 zuerst**. Fachliche Detailregeln stehen in `docs/TB-HELFER_PROJEKTREGELN.md`, der aktuelle technische Stand in `docs/TB-HELFER_AKTUELLER_STAND.md`, die vollständige Serverarchitektur in `docs/TB-SERVER_1_0_ZIELMODELL.md`.

## Grundstrategie

**Zuerst TB-Server 1.0 als vollständige stabile Grundlage bauen, danach Android systematisch an den festen Serververtrag anbinden.**

Grund für die Richtungsänderung: Der bisherige Android-first-Weg führte wiederholt zu Zwischenlösungen, weil Hauptbaustelle, Unterbaustellen, BTB-Lebenszyklus, Rechte, Fotos und Streckendaten serverseitig noch nicht als einheitliches Modell vorhanden waren.

Android 200050 bleibt vorerst Testclient. Keine weiteren kosmetischen Android-Hotfixes außer echten Blockern, bis der Server-1.0-Vertrag steht und End-to-End testbar ist.

Der bisherige Serverbestand ist Testbestand. Benutzer, Baustellen und Testfachdaten dürfen beim 1.0-Cutover verworfen werden. **Originale Vermessungsdaten bleiben unverändert.** Vor jeder produktiven NAS-/DB-Änderung wird trotzdem ein vollständiges verifiziertes Backup erstellt.

---

# PHASE S0 – SERVER-1.0-VERTRAG UND SAUBERER ENTWICKLUNGSRAHMEN

- eigener Branch `feature/tb-server-1.0`;
- vollständiges Zielmodell in `docs/TB-SERVER_1_0_ZIELMODELL.md`;
- modularer Monolith statt Microservice-Split;
- stabile `/api/v1`-API;
- `/capabilities` statt Client-Abfragen nach DB-Schema;
- unveränderliche nummerierte Migrationen mit Prüfsummen;
- Test-/Staging-/Produktivtrennung;
- Preflight/Verify für Migrationen;
- expliziter Testdaten-Resetweg;
- keine Secrets in GitHub/Logs.

Abschlusskriterium: Server-Skelett kann frisch initialisiert, migriert, geprüft und ohne Android gestartet werden.

# PHASE S1 – FIRMA, BENUTZER, CHEF, PORTAL-LOGIN UND GERÄTE

- Firma als Pflichtbezug normaler Nutzer;
- stabile UUIDs, gleiche sichtbare Namen erlaubt;
- Firmenrollen `CHEF` und `USER`;
- mehrere CHEFs pro Firma möglich;
- CHEF sieht alle Baustellen der eigenen Firma;
- persönliche E-Mail/Telefon serverseitig synchronisierbar;
- Firmenstammdaten + Firmenlogo zentral;
- Portal-Login getrennt vom Geräte-Key;
- Einmal-Aktivierung, Passwort-Hash, Sessions, CSRF, Rate-Limit;
- Gerätebindung, Sperren, letzter Sync, App-Build, letzter Fehler;
- Firmenlogo/PDF-Layout serverseitig versioniert.

Abschlusskriterium: Admin kann Firma/CHEF/TB/Helfer anlegen; Nutzer kann sich im Portal anmelden; Gerät kann aktiviert/gesperrt werden.

# PHASE S2 – BAUSTELLEN, MITGLIEDSCHAFTEN, RECHTE UND TEAMS

- Baustelle gehört exakt einer Firma;
- kompletter Unterbaustellen-/Strecke-Gleis-/Objektbaum mit stabilen UUIDs;
- Rechte serverseitig kanonisch, aber auf Handy und Portal bedienbar;
- Rechtevorlagen: Verantwortlicher TB, Helfer Standard, Helfer erweitert, Nur Lesen;
- individuelle Abweichungen pro Benutzer/Baustelle;
- Rechte u. a. für BTB erstellen/bearbeiten/abschließen/löschen, Bilder, Mängel, Abnahmen, Struktur, Streckendaten, Export;
- Vertretungsregel: CHEF oder berechtigter TB kann Verantwortung übertragen;
- mehrere verantwortliche TB-Teams pro Baustelle;
- andere TB-Teams bleiben beim BTB getrennt.

Abschlusskriterium: Nutzer A erstellt Baustelle/Unterbaustellen; Helfer sieht denselben Baum; Rechte werden serverseitig durchgesetzt.

# PHASE S3 – SYNC-KERN

- lokale Client-Outbox als verbindlicher Schreibschutz;
- `client_change_id` für Idempotenz;
- Server-Eventlog/Change-Cursor;
- `changes since X` statt Vollsync;
- Live-Ereignissignal über WebSocket/SSE für geöffnete Clients;
- Pull bei Start/Resume/Baustelle/BTB;
- periodischer Hintergrundlauf nur als Sicherheitsnetz;
- Konflikte auf Datensatz-/Eintragsebene;
- kein stilles Überschreiben kompletter Fachobjekte.

Abschlusskriterium: Zwei Testclients können offline/online Änderungen erzeugen, ohne Verlust oder Dubletten.

# PHASE S4 – GRANULARER BTB 1.0

- BTB pro verantwortlichem TB-Team;
- Server hält `active_btb_id` pro Team;
- sichtbarer Status `BTB aktiv`, nicht `Entwurf`;
- BTB nicht mehr nur als ein großes überschreibbares JSON;
- eigene IDs/Versionen für Kopf, Personal, Maschinen, Wetter und einzelne Einträge 4–8;
- Nutzer 1 und Helfer A können gleichzeitig unterschiedliche Einträge ändern;
- erster erfolgreicher Abschluss friert Original-Snapshot unveränderlich ein;
- Abschluss löst `active_btb_id`;
- bewusste Nachbearbeitung reaktiviert dieselbe `btbId`;
- Tombstone-Löschung auf allen Teamgeräten;
- aktuelle Abschluss-PDFs serverseitig gespeichert.

Abschlusskriterium: Erstellen → gemeinsam bearbeiten → gleichzeitig bearbeiten → abschließen → nachbearbeiten → erneut abschließen → löschen funktioniert mit zwei Testclients.

# PHASE S5 – DATEIEN, BILDER UND CHUNKUPLOAD

- Dateiobjekte mit UUID, Firma, Baustelle, Fachbezug, MIME, Größe, SHA-256, Storage-Key, Autor, Zeit, Löschstatus;
- Bilder nur einmal hochladen;
- Thumbnails getrennt;
- große Uploads in 8–16-MiB-Chunks mit Wiederaufnahme;
- Portalbilder ohne erfundenes GPS;
- echte Baustellenfotos bleiben beim BTB-Löschen erhalten, wenn fachlich vorgesehen;
- NAS-Speicher UUID-basiert statt sichtbare Namen als Schlüssel.

Abschlusskriterium: Foto/Datei über einen Client hochladen, auf anderem Client sehen, große Uploads fortsetzen.

# PHASE S6 – MÄNGEL UND ABNAHMEN

- baustellenweite stabile UUIDs;
- Status, Autor, Version, Kommentare, Bilder;
- individuelle Rechte für erstellen/bearbeiten/abschließen;
- Offline-/Konfliktregeln;
- Gesamtlisten baustellenweit;
- später SKV/GPS erweiterbar.

# PHASE S7 – STRECKEN- UND VERMESSUNGSDATEN

- NAS-Originale TRA/GRA/KF/KOR/KV/GVP unveränderlich;
- getrennte versionierte Arbeitsdaten;
- Achsen, Samples, KM-Quellen, manuelle Korrekturen, bearbeitete Abschnitte, Streckenpunkte;
- nur benötigte Daten auf Geräte;
- grafischer Bearbeitungsstand;
- Farbregeln grün/orange/rot/grau/blau;
- automatische Zusammenführung nur über stabile Strecken-/Gleis-/Geometrieidentität, nicht nur sichtbare Namen.

# PHASE S8 – TB-HELFER PORTAL

Portal ist vollwertiger Fachclient:

- gleiche Baustellen-/Rechtebasis wie Android;
- BTB erstellen/bearbeiten/abschließen/nachbearbeiten;
- Punkte 4–8;
- Bilder vom PC;
- Mängel/Abnahmen;
- PDF/DOCX/Archive;
- Rechteverwaltung soweit erlaubt;
- CHEF sieht alle Baustellen seiner Firma;
- keine mobile Live-GPS-/Kamera-GPS-Funktion;
- PC-Upload erfindet keine GPS-Daten.

# PHASE S9 – ADMINISTRATION / SYSTEMSTATUS / BACKUP

Admin getrennt vom Benutzerportal.

Übersicht:
- Speicher gesamt/belegt/frei;
- Speicher je Firma;
- Upload/Download 24 h;
- API-Uptime;
- DB-Status;
- Backupstatus;
- Syncqueue/Fehler;
- aktive Geräte/Clientversionen;
- Diagramme.

Zusätzlich:
- Firmen/Benutzer/Geräte;
- Dateien;
- Baustellenstatus;
- App-Versionen;
- Papierkorb;
- Löschcenter;
- Auditlog;
- Systemstatus;
- Restore-Test.

# PHASE S10 – RELEASES UND CLIENTKOMPATIBILITÄT

- Android/iOS/Windows getrennt;
- Version, Build, SHA-256, Release Notes;
- Mindestbuild;
- optional/Pflichtupdate;
- Capability-Kompatibilität;
- Android-APK-Download mit normalem Android-Installationsmodell;
- iOS über Store/TestFlight/MDM-Verweis.

# PHASE S11 – SERVER-1.0-END-TO-END UND CUTOVER

Vor NAS-Cutover:

1. vollständige Integrationschecks;
2. Firma + CHEF + TB + Helfer;
3. Portal + Geräteaktivierung;
4. Baustelle/Unterbaustellen/Rechte;
5. BTB Team A und Team B;
6. gleichzeitige Bearbeitung;
7. Bilder;
8. Abschluss/Nachbearbeitung/Löschen;
9. Mängel/Abnahmen;
10. Streckendaten;
11. Offline/Online;
12. Baustellenlöschung/Tombstone;
13. Backup/Restore;
14. Client-Kompatibilität.

Dann:

- produktiven Altstand sichern;
- Backup verifizieren;
- Testbestand darf verworfen werden;
- Original-Vermessungsdaten bleiben erhalten;
- TB-Server 1.0 frisch initialisieren;
- Admin/Firma/Testnutzer neu anlegen;
- Cloudflare/Tailscale-Verbindungen prüfen;
- End-to-End erneut testen.

---

# PHASE A – ANDROID NACH SERVER 1.0

Danach Android nicht mehr gegen Zwischenverträge flicken, sondern systematisch an Server 1.0 anbinden:

1. Auth/Aktivierung/Profil/Firma/Logo;
2. Baustellenbaum + Mitgliedschaften + Rechte;
3. Sync-Outbox/Event-Feed/Live-Refresh;
4. granularer BTB + Punkte 4–8 + Buttons;
5. Fotos/Dateien;
6. Mängel/Abnahmen;
7. Streckendaten;
8. PDF/Archive;
9. Einstellungen neu strukturieren und Persistenzfehler beseitigen;
10. Generated-Source-/Legacy-/CI-Bereinigung;
11. Poco↔Pixel↔Portal vollständiger Praxistest.

Bestehende bestätigte Android-KM/GVP/Festpunktlogik wird dabei nicht ohne reproduzierbaren Fehler verändert.
