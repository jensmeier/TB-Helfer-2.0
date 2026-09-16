# TB-Helfer – aktueller Projektstand

Stand: 2026-09-17

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen in `docs/TB-HELFER_PROJEKTREGELN.md`, die Arbeitsweise in `docs/TB-HELFER_PROJEKTANWEISUNG.md`, die geplante Reihenfolge in `docs/TB-HELFER_ROADMAP.md`.

## Start jedes Chats / jeder Änderung

Zuerst prüfen/lesen:
1. `docs/TB-HELFER_PROJEKTANWEISUNG.md`
2. `docs/TB-HELFER_PROJEKTREGELN.md`
3. diese Datei
4. aktiven Feature-Branch
5. offenen Draft-PR
6. neuesten relevanten Commit
7. Chat-Übergabe nur ergänzend

GitHub-Code und aktiver Branch sind Source of Truth. `main` stabil halten.

## Repository / Branch / PR

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiver Feature-Branch: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft
- `main`: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`
- Branch-Head vor dieser Dokumentationsaktualisierung: `c92b83ec9fd514c1c865d321999e18ce32af11e8`
- aktueller Android-Buildkandidat im Gradle-Stand: **200050**
- Produktivserver/NAS weiterhin unverändert: API **0.7.0**, PostgreSQL **Schema 10**
- vorbereiteter nächster Serverstand im Feature-Branch: API **0.7.2 / Schema 13**

## Aktuelle Entwicklungsentscheidung

Der Benutzer hat Schema 13 jetzt ausdrücklich für den nächsten produktiven Serverstand freigegeben, mit zwei zusätzlichen verbindlichen Anforderungen:

1. benötigte **mobile/abgeleitete Streckendaten vom Handy** sollen über den Server auf andere Baustellengeräte gelangen;
2. wenn eine **servergebundene Baustelle komplett gelöscht** wird, muss sie auch serverseitig verschwinden und auf anderen Geräten über einen Tombstone lokal entfernt werden.

Produktive Migration trotzdem **nur nach frischem verifiziertem Backup**. Bis dahin bleibt der NAS auf Schema 10.

---

# Fachliches Sync-Modell

## Baustellenweit gemeinsam

Innerhalb derselben Baustellenmitgliedschaft sollen gemeinsam verfügbar sein:
- Hauptbaustelle;
- Unterbaustellen / kompletter `BaustellenKnoten`-Baum;
- benötigte mobile Streckendaten;
- Abnahmen;
- Mängel;
- baustellenweite Bilder-/Mängellisten.

## BTB getrennt pro verantwortlichem TB-Team

- Jeder verantwortliche TB besitzt mit seinen Helfern seinen eigenen gemeinsamen BTB-Bereich.
- Helfer desselben TB-Teams bearbeiten dieselbe stabile `btbId`.
- Andere verantwortliche TB-Teams besitzen eigene BTBs.
- Datum/Uhrzeit/Tag/Nacht sind Attribute, nicht Identität.
- sichtbarer Status eines laufenden Berichtes: `BTB aktiv`, nicht `Entwurf`.

Der praktische Poco↔Pixel-BTB-Sync ist weiterhin **nicht als stabil bestätigt**. Vor weiteren BTB-Hotfixes zuerst Baustellenstruktur/Serverbasis stabilisieren.

---

# Schema 13 – Baustellenstruktur

Bereits im Branch gebaut:
- `tb.collab_worksite_nodes` mit stabiler geräteübergreifender `client_node_key`;
- Parent/Child-Zuordnung ohne lokale Room-IDs;
- LEAD lädt lokale Unterbaustellen hoch;
- Helfer übernimmt den autoritativen Serverbaum;
- gelöschte Unterbaustellen werden als Tombstones erhalten;
- `WorksiteStructureSync` ist in den normalen Android-Sync integriert;
- Server-Basisendpunkt: `/api/v1/collab/worksites/{worksite_id}/nodes`.

Der frühere Test „Poco hat Unterbaustellen, Pixel nur Hauptbaustelle“ ist damit fachlich erklärt: produktiv läuft noch Schema 10 und kennt den Schema-13-Endpunkt nicht.

---

# Schema 13 – mobile Streckendaten

Neue Umsetzung vom 17.09.2026:

## Sicherheits-/Datenregel

**Originale Vermessungsdateien auf dem NAS bleiben unverändert.**
Der neue Pfad lädt keine TRA/GRA/KF/KOR-Rohdateien hoch und überschreibt keine Originale.

Synchronisiert wird ein separater, baustellenbezogener mobiler Arbeitsstand:
- `TrassenAchseEntity`-Metadaten;
- bereits berechnete `TrassenSamplePointEntity`-Punkte inkl. GPS/KM/KM-Quelle;
- lokale `StreckenpunktEntity`-Punkte;
- Zuordnung über Server-Baustelle und stabile Dataset-Keys.

Damit kann ein Helfer die auf dem TB-Handy bereits berechnete/aufbereitete Achse verwenden, ohne die ursprünglichen Rohdateien erneut importieren zu müssen.

Noch nicht enthalten:
- vollständige Roh-GRA/GVP/KOR/KF-Dateiinhalte;
- separate Historie aller manuellen KM-Anker/Korrekturen. Der aktuelle berechnete KM-Stand liegt bereits in den synchronisierten Samplepunkten. Falls Helfer später selbst diese Kalibrierung weiterbearbeiten sollen, muss diese Bearbeitungshistorie zusätzlich synchronisiert werden.

## Server

`server/migrations/013_worksite_structure_sync.sql` enthält jetzt zusätzlich `tb.mobile_track_data` mit:
- `source_worksite_id`;
- `dataset_key`;
- `dataset_kind = AXIS | STRECKENPUNKTE`;
- optionalem `route_key`;
- JSON-Payload und Versionsnummer;
- Soft-Delete.

`server/app_parts/21_part.py` setzt den vorbereiteten Serverstand auf **0.7.2** und bietet:
- `GET /api/v1/collab/worksites/{worksite_id}/track-data`
- `PUT /api/v1/collab/worksites/{worksite_id}/track-data`

Schreiben darf nur `LEAD`; Mitglieder dürfen den Snapshot lesen.

## Android

Neu:
- `WorksiteTrackDataApi.kt`
- `WorksiteTrackDataSync.kt`
- Service in `ServiceLocator`
- `Schema13Worker.kt`
- `SofortSync` stößt zusätzlich den Schema-13-Worker an.

LEAD: lokaler mobiler Streckendatenstand → Server.
Helfer: Server-Snapshot → lokale Fachdatenbank der gebundenen Baustelle.
Andere lokale Baustellen bleiben unangetastet.

---

# Schema 13 – Baustelle komplett löschen

Neue verbindliche Semantik:

- Eine **rein lokale, nie servergebundene Baustelle** kann weiterhin nur lokal gelöscht werden.
- Eine **servergebundene Baustelle** wird nicht mehr zuerst lokal gelöscht.
- Ablauf:
  1. Server-Binding bestimmen;
  2. Server-DELETE ausführen;
  3. Server erzeugt `worksite_delete_tombstones` und setzt `worksites.deleted_at`;
  4. erst nach Server-Erfolg lokale Dateien/BTBs/Legacy-Baustelle/Teilbaum löschen und Binding lösen;
  5. andere Geräte laden den Tombstone firmenbezogen und löschen ihre lokale Kopie ebenfalls.
- Bei Server-/Netz-/Sessionfehler bleibt die lokale Baustelle bestehen. Damit kann sie nicht später aus dem Server wiederauferstehen.
- Die Serverzeile bleibt als Soft-Delete-/Auditanker erhalten, taucht aber im normalen Collaboration-Kontext nicht mehr auf.

Neue Dateien/Pfade:
- `server/app_parts/21_part.py`
- `app/src/main/java/de/tbhelfer/server/WorksiteDeletionApi.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/WorksiteLocalDeleteService.kt`
- `BaustellenFragment.kt` verwendet bei gebundenen Baustellen Server-first Delete.
- `Schema13Worker.kt` verarbeitet Remote-Tombstones.
- Tombstone-Auflösung benutzt die persistente Server↔Root-Bindung und hängt nicht davon ab, dass die gelöschte Baustelle noch in `/collab/context` sichtbar ist.

---

# BTB / Punkte 4–8 / Buttons

Weiterhin im Branch:
- Punkte 4–8 verwenden denselben direkten Bilderpfad mit passendem Bildtyp;
- moderne Bilder werden strikt derselben `btbId` zugeordnet;
- eigene feste Dreierzeile `Bilder | Abbrechen | Hinzufügen`, je 1/3 Breite;
- Abschluss/Nachbearbeitung/Löschen-Tombstone für BTB ist implementiert, aber der gesamte Zwei-Geräte-Lebenszyklus ist praktisch noch nicht zuverlässig bestätigt.

Beim letzten Nutzer-Test funktionierte der Gesamt-Sync nicht zuverlässig. Die Analyse zeigte zusätzlich zur fehlenden Schema-13-Baustellenstruktur Probleme in der BTB-Sync-Orchestrierung (lokaler Outbox-Schutz/Pull-Reihenfolge/aktiver Team-BTB). Diese Punkte erst nach stabiler Baustellen-/Schema-13-Basis erneut systematisch angehen.

---

# Kamera / KM / Foto

Praktisch bestätigt aus 200049/200050-Grundlage:
- Live-KM funktioniert;
- Live-KM größer/fett;
- Foto-Erfassen zeigt passenden KM/GPS/Strecke-Gleis-Kontext;
- Qualitätsampel `<=10 m grün`, `>10–30 m gelb`, `>30 m rot`;
- GPS-Ungenauigkeit darf nur verschlechtern, nie verbessern;
- automatischer KM bleibt im gelben Bereich erhalten.

Nicht erneut verändern, solange kein reproduzierbarer Fehler vorliegt:
- KF/GVP/Festpunkt-Logik;
- DB_REF/GK-Streifen 3/4/5;
- vorhandene KM-Projektion.

Offen:
- Foto-Aufdruck/Overlay-Einstellungen bleiben teilweise nicht persistent.

---

# Firmenlogo / PDF

Bereits gebaut/vorgesehen:
- Firmenlogo zentral serverseitig und auf Geräten gecacht;
- bevorzugtes Format PNG;
- PDF zeichnet hinter dem Logo weißen Untergrund;
- JPEG/WebP ebenfalls im vorhandenen Downloadpfad möglich;
- SVG derzeit nicht für den Bitmap-PDF-Pfad vorgesehen;
- Schema 10 besitzt Firmenprofil-/Logo-Metadaten;
- Schema 12 für firmenweites PDF-Logo-Layout ist vorbereitet, aber noch nicht produktiv.

---

# Tests / Checks

Source-Checks im Workflow:
- bestehende Schema-/BTB-Checks;
- `17_verify_schema13_source.py` – Schema-13-Basis Unterbaustellen;
- `18_verify_android_worksite_structure_source.py` – Android-Strukturvertrag;
- neu `19_verify_schema13_track_delete_source.py` – mobile Streckendaten + Server-first Baustellenlöschung.

**Noch nicht bestätigt:**
- aktueller Head lokal in Android Studio kompiliert;
- Poco/Pixel mit Schema-13-Server getestet;
- produktive Migration ausgeführt.

GitHub Actions war zuletzt als Compilernachweis unzuverlässig; lokaler Android-Studio-Build bleibt maßgeblich.

---

# Nächste Schritte – verbindliche Reihenfolge

1. aktuellen Branch auf Windows/Poco lokal bauen; Compilerfehler zuerst beheben;
2. vor produktiver Serveränderung frisches PostgreSQL/NAS-Backup erstellen und verifizieren;
3. Migrationsfolge 10 → 13 technisch prüfen; insbesondere entscheiden, ob 11/12 gleichzeitig produktiv aktiviert oder weiterhin nur vorbereitet bleiben;
4. Schema-13-Migration einspielen;
5. Servercode mit `21_part.py` aktivieren, API-Version **0.7.2** prüfen;
6. frische Testbaustelle auf Poco anlegen, mit Unterbaustellen und mobilen Streckendaten;
7. Pixel beitreten lassen: Hauptbaustelle + Unterbaustellen + Streckendaten müssen automatisch erscheinen;
8. Unterbaustelle auf Poco ändern/löschen und Pixel prüfen;
9. komplette servergebundene Baustelle auf Poco löschen: Server-Kontext muss sie verlieren, Pixel muss sie über Tombstone ebenfalls lokal löschen;
10. danach BTB-Sync-Grundlogik erneut sauber prüfen: Outbox vor Pull, gemeinsamer aktiver BTB pro TB-Team, Abschluss/Löschen/Poco↔Pixel.

Keine produktive NAS-Migration ohne vorher bestätigtes Backup.
