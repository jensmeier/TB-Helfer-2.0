# TB-Server 1.0 – Trassendaten-Katalogvertrag

Stand: 2026-09-29  
Arbeitsstand: `feature/tb-server-1.0`

## Zweck

Der Server führt Vermessungs-/Trassierungsdaten nicht mehr als „eine ZIP = ein Bahnhof/eine Strecke“, sondern als versionierten fachlichen Verbund aus unveränderten Originalquellen, logischen Achsen und einzelnen Quellsegmenten.

Originaldateien werden nicht überschrieben oder physisch zusammenkopiert. Die gemeinsame sichtbare Geometrie entsteht aus dem Katalog.

## Persistenz

Migration `017_survey_reference_catalog.sql` ergänzt den versionierten Katalog; Migration `018_survey_object_identity.sql` erweitert die Achsen um eine stabile fachliche Objektidentität.

- `survey_catalog_assets`: ausgewertete Originalquellen/Dateiarten;
- `survey_catalog_axes`: stabile fachliche Achsen (Strecke/Betriebsstelle/Gleis/Richtung/Objekt-ID);
- `survey_catalog_segments`: Original- oder manuelle Geometrieanteile;
- `survey_catalog_conflicts`: nur uneindeutige Auto-Merge-Fälle;
- `survey_catalog_history`: versionierter Change-Cursor.

Rohdateien bleiben in `survey_originals` mit SHA-256 und bestehendem Zugriffsschutz.

### Weiche / Weichenverbindung

Für Bahnhofsobjekte wird nicht mehr nur räumliche Nähe verwendet:

- einzelne Weiche: `object_key = W:<Nummer>`, z. B. `W:27`;
- Weichenverbindung: `object_key = WVB:<A>-<B>`, z. B. `WVB:14-22`;
- WVB-Endpunkte werden kanonisch sortiert, damit `14-22` und `22-14` dieselbe fachliche Identität ergeben;
- verschiedene bekannte `object_key` werden nicht allein wegen räumlicher Nähe als dieselbe Achse behandelt;
- bei Bahnhofsdaten ist `station_code + object_key` eine starke Identität. Eine eigene Streckennummer ist für Weiche/WVB nicht zwingend erforderlich.

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
- Fehlt Gleis/Richtung, darf bei nicht widersprechender Strecke/Betriebsstelle ein räumlich enger X/Y-Kandidat in den Geometrievergleich aufgenommen werden. Der Geometrievergleich – nicht die Nähe allein – entscheidet; mehrere gleich plausible Achsen bleiben Prüffall.

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
- `GET /api/v1/survey-catalog/conflicts/{id}`: verständliche Diagnose inklusive beiden Segmenten, Quellen, Messwerten und ausgedünnter Vergleichsgeometrie;
- `POST /api/v1/survey-catalog/conflicts/{id}/resolve`.

Die Admin-Oberfläche zeigt offene Prüffälle mit **„Problem ansehen“**. Das Detailfenster legt neue und vorhandene Geometrie gemeinsam übereinander, erklärt den technischen Konfliktgrund auf Deutsch und zeigt die relevanten Messwerte. Entscheidungen bleiben versioniert/auditierbar.

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

`POST /api/v1/survey-catalog/reindex` (Admin) legt den Background-Job `SURVEY_CATALOG_REINDEX` an. Damit werden bereits vorhandene geparste Originale nach Migration 017/018 in den Katalog eingelesen. Migration 018 enthält zusätzlich einen konservativen Best-Effort-Backfill für bereits vorhandene eindeutige Weichen-/WVB-Anzeigenamen.

## Lifecycle / Firmen-Purge

Der bestehende Produktionsschutz bleibt unverändert: Firmen mit geschützten Vermessungsoriginalen werden vom normalen produktiven Firmen-Purge blockiert. Für zulässige Staging/Test-Purges werden Migration-017-Katalogreferenzen vor den Firmen-Originalen in FK-sicherer Reihenfolge bereinigt. COMPANY-Katalogobjekte der Firma und von deren Rohoriginalen abhängige Katalogsegmente/-assets werden entfernt; unabhängige globale Beiträge ohne Rohoriginal-Abhängigkeit bleiben bestehen.

## Capability-Erkennung

Der bestehende `GET /api/v1/capabilities` kündigt den Vertrag explizit an:

- `survey_reference_catalog_v1`;
- `survey_reference_contributions_v1`;
- `survey_reference_change_feed_v1`.

Clients verwenden diese Flags, bevor sie die neuen Survey-Katalogpfade aktivieren.

## Portal-Unterbau vor der Trassendatenübersicht

Der bestehende Admin-Import enthält jetzt den notwendigen Unterbau für die spätere `Trassendatenübersicht`:

- erkannte Strecke/Gleis/Objekt-ID werden nach dem Rendern erneut als sichtbares Decision-Prefill abgesichert;
- Weiche/WVB besitzen ein eigenes Objektfeld statt das Gleisfeld zu missbrauchen;
- bei deckungsgleichen/nahen TRA-Achsen wird ein Mehrfachtreffer angeboten, analog zum bestehenden Android-Prinzip;
- offene Konflikte sind anklickbar und werden im Diagnosefenster geometrisch verglichen.

Die eigenständige `Trassendatenübersicht` folgt erst nach erfolgreichem Synology-/Pasing-Runtime-Gate dieses Unterbaus.

## Nachgelagerte Stufen

1. SERVER/Import-Portal-Unterbau – source-seitig umgesetzt, Runtime Migration 018 noch offen.
2. Portal – eigenständige Trassendatenübersicht, Suche/Zoom/Bestandsverwaltung.
3. Android – Verfügbarkeitsindex, Download, Beiträge/Änderungen und manueller GPS-Pfad.

`app/**` wird im SERVER-Projekt nicht geändert.

## Upgradepfad Parser v4 / bestehender Katalog

Mit der Weichen-/WVB-Objektidentität ändert sich das Parser-Payload-Schema. Deshalb gilt ab diesem Stand:

- `PARSER_VERSION = tb-survey-parser-4`;
- ein fehlender Parse-Cache, eine ältere Parser-Version oder ein abweichender `source_sha256` gilt beim Katalog-Reindex als veraltet;
- `reindex_all_originals()` reparst solche Originale intern aus der unveränderten Raw-Datei und aktualisiert nur `survey_parse_catalog`;
- bereits vorhandene `ORIGINAL_TRA`-Segmente werden **nicht** erneut angelegt;
- bei `ALREADY_INDEXED` darf ausschließlich fehlende fachliche Identität fill-only ergänzt werden;
- vorhandene `object_key`-Werte, z. B. nach Admin-Korrektur, werden nicht überschrieben;
- bei Achstyp-Konflikt wird keine automatische Identitätsanreicherung vorgenommen;
- eine erfolgreiche Anreicherung wird mit `ALREADY_INDEXED_ENRICHED`, History-Operation `ENRICH` und Audit `SURVEY_CATALOG_AXIS_IDENTITY_ENRICH` dokumentiert.

Damit kann ein bereits vor Parser v4 importierter WVB-Bestand nach Migration 018 per Reindex auf die neue Objektidentität gebracht werden, ohne Geometrie zu duplizieren.
