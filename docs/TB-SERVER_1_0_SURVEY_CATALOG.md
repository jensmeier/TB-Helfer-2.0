# TB-Server 1.0 – Trassendaten-Katalogvertrag

Stand: 2026-09-29  
Arbeitsstand: `feature/tb-server-1.0`

## Zweck

Der Server führt Vermessungs-/Trassierungsdaten nicht mehr als „eine ZIP = ein Bahnhof/eine Strecke“, sondern als versionierten fachlichen Verbund aus unveränderten Originalquellen, logischen Achsen und einzelnen Quellsegmenten.

Originaldateien werden nicht überschrieben oder physisch zusammenkopiert. Die gemeinsame sichtbare Geometrie entsteht aus dem Katalog.

## Persistenz

Migration `017_survey_reference_catalog.sql` ergänzt:

- `survey_catalog_assets`: ausgewertete Originalquellen/Dateiarten;
- `survey_catalog_axes`: stabile fachliche Achsen (Strecke/Betriebsstelle/Gleis/Richtung);
- `survey_catalog_segments`: Original- oder manuelle Geometrieanteile;
- `survey_catalog_conflicts`: nur uneindeutige Auto-Merge-Fälle;
- `survey_catalog_history`: versionierter Change-Cursor.

Rohdateien bleiben in `survey_originals` mit SHA-256 und bestehendem Zugriffsschutz.

## Scope

Katalog-Sicht:

- `GLOBAL`: von allen angemeldeten Nutzern les-/änderbar;
- `COMPANY`: nur Admin bzw. Nutzer derselben Firma.

Eine Firmen-Rohdatei darf mit `catalog_scope=GLOBAL` abgeleitete Geometrie in den gemeinsamen Katalog liefern. Dadurch wird die Rohdatei selbst **nicht** firmenübergreifend freigegeben.

Normale Nutzer erhalten bei GLOBAL-Antworten keine fremden Bearbeiter-/Firmenidentitäten. Technische Herkunft, Quelltyp, Version und Qualitätsstatus bleiben sichtbar; vollständige Bearbeiter-/Auditdaten sind Admin-Sicht.

## Originalupload

`POST /api/v1/survey-originals/from-file`

Zusätzliche Felder:

- `catalog_scope`: `COMPANY` oder `GLOBAL`;
- `station_code`;
- `station_name`;
- `source_date`.

Fehlen Strecke/Betriebsstelle im mobilen Request, übernimmt der Server vorhandene `route_number`/`station_code`/`station_name` der zugehörigen Baustelle als Fallback. Ein bytegleiches bereits vorhandenes GLOBAL-Original wird als bereits verfügbar gemeldet; private Originale anderer Firmen werden weder gesucht noch offengelegt.

Aktive Baustellenmitgliedschaft genügt für den Vermessungsbeitrag; die bestehende Datei-/Firmenzuordnung bleibt zwingend. Der Parser darf von jedem aktiven Baustellenmitglied ausgeführt werden. `source_date` ist das fachliche Bestandsdatum und bleibt von `imported_at` getrennt; der Admin-Import akzeptiert es ebenfalls explizit.

## Suche / Verfügbarkeit

`GET /api/v1/survey-catalog/search?q=...`

Sucht u. a. nach Streckennummer, Betriebsstellen-Code/-Name, Gleis und Achsbezeichnung.

`GET /api/v1/survey-catalog/availability?route_number=...&station_code=...`

Liefert:

- vorhanden/teilweise/fehlt;
- Achsen und aktive/review Segmente;
- vorhandene Datenarten;
- Original/manuelle Quellen;
- KM-Qualität je Achse/Segment.

KM-Semantik:

- `AUTOMATISCH / GRUEN`: explizite KM-GRA oder robuste GVP-Kalibrierung;
- `MANUELL / ORANGE`: bewusst manuell gesetzte KM;
- `UNPLAUSIBEL / GRAU`: KM/GVP vorhanden, aber nicht robust verwendbar;
- `FEHLT / ROT`: Geometrie vorhanden, keine belastbare KM.

GVP folgt dem bestätigten Vertrag: gleiche Strecke, mindestens 4 verwertbare Treffer, maximal 120 m Achsabstand, Medianoffset und ±80 m Ausreißerfilter.

Bei mehreren Quellsegmenten wird eine KM-GRA nicht blind auf alle Segmente übertragen; ohne eindeutigen Quellenbezug bleibt die Zuordnung konservativ.

## Geometrie

`GET /api/v1/survey-catalog/geometry`

Filter:

- Strecke;
- Betriebsstelle;
- Bounding Box;
- maximale Punktzahl je Segment;
- Segmentlimit.

Antwort enthält aktive Quellsegmente, Herkunft, Versionsstand, KM-Qualität und für die Darstellung ausgedünnte Geometriepunkte. Originale bleiben separat nachvollziehbar.

## Automatischer Merge

Automatisch verbunden werden nur ausreichend eindeutige Fälle.

Berücksichtigt werden insbesondere:

- Strecke/Betriebsstelle/Gleis/Achstyp;
- X/Y-Überlappung;
- Endpunktabstand;
- Tangente/Richtung am Anschluss;
- Quell-/Bestandsdatum;
- KM/Station soweit belastbar.

Aktuelle Grenzwerte im Serververtrag:

- Überlappung: <= 1,0 m;
- Endpunktanschluss: <= 5,0 m;
- Richtungsfehler am Anschluss: <= 20°;
- nahe aber nicht passende Geometrie bis 20 m erzeugt Prüffall statt stiller Verbindung.

Neue Quelle und alte Quelle bleiben getrennte Segmente. Eine neuere Quelle darf einen älteren Abschnitt nur dann als bevorzugt ablösen, wenn **der komplette bestehende Abschnitt** geometrisch innerhalb der Merge-Toleranz von der neuen Quelle abgedeckt ist. Eine bloße hohe Teilüberdeckung (z. B. 80/90 %) genügt ausdrücklich nicht. Teilverlängerungen lassen den nicht ersetzten Alt-/Manuellbereich aktiv bestehen.

## Nutzerbeiträge

`POST /api/v1/survey-catalog/manual-segments`

Typen:

- `MANUAL_TRA_SECTION`: fachlicher Abschnitt auf einer bestehenden Roh-TRA;
- `MANUAL_GPS`: freie manuelle GPS-Geometrie für den späteren Android-Recorder.

Antwortstatus:

- `ADDED`;
- `MERGED`;
- `REVIEW_REQUIRED`.

`PATCH /api/v1/survey-catalog/manual-segments/{id}`

Versionierte Änderung mit `base_version` + `client_change_id`.

`DELETE /api/v1/survey-catalog/manual-segments/{id}`

Keine physische Löschung: Beitrag wird `INACTIVE`, Historie bleibt bestehen.

`PATCH /api/v1/survey-catalog/axes/{id}`

Fachliche Achskorrektur ist ebenfalls versioniert. GLOBAL darf von angemeldeten Nutzern korrigiert werden; COMPANY nur durch Nutzer derselben Firma/Admin. Die Rohquelle bleibt unverändert.

## Konflikte

Uneindeutige Geometrie wird nicht geraten.

Kandidat: `REVIEW_REQUIRED`; bisheriger aktiver Stand bleibt nutzbar.

Server erzeugt einen Konfliktdatensatz und einen Background-Job `SURVEY_CONFLICT_EMAIL`. E-Mail nutzt den bestehenden SMTP-/Alert-Weg und enthält keine Secrets.

Admin-API:

- `GET /api/v1/survey-catalog/conflicts`;
- `POST /api/v1/survey-catalog/conflicts/{id}/resolve`.

Mögliche Entscheidungen:

- `KEEP_EXISTING`;
- `ACCEPT_CANDIDATE`;
- `KEEP_BOTH`;
- `CONNECT`.

## Change-Feed

`GET /api/v1/survey-catalog/changes?after=...`

liefert versionierte Änderungen an Asset/Achse/Segment/Konflikt. COMPANY-Daten werden firmenisoliert; GLOBAL ist gemeinsam sichtbar.

Die Antwort enthält `next_after`. Dieser Scan-Cursor läuft auch über für den aktuellen Nutzer unsichtbare COMPANY-Änderungen weiter. Clients verwenden für den Folgerequest immer `next_after` – auch wenn `changes=[]` – damit spätere globale/eigene Änderungen nicht hinter fremden privaten Änderungen verhungern.

## Backfill

`POST /api/v1/survey-catalog/reindex` (Admin) legt den Background-Job `SURVEY_CATALOG_REINDEX` an. Damit werden bereits vorhandene geparste Originale nach Migration 017 in den Katalog eingelesen.

## Nachgelagerte Stufen

Dieser Vertrag ist die Serverbasis.

1. SERVER – dieser Block.
2. Portal – Trassendatenübersicht, Suche/Zoom/Prüffälle.
3. Android – Verfügbarkeitsindex, Download, Beiträge/Änderungen und manueller GPS-Pfad.

Portal-Templates und `app/**` gehören ausdrücklich nicht zu diesem Serverblock.
