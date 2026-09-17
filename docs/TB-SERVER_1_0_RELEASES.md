# TB-Server 1.0 – Releases und Client-Kompatibilität

Stand: 2026-09-18

Dieser Baustein setzt den bereits im Zielmodell vorgesehenen stabilen Client-Kompatibilitätsvertrag um. Er verwendet die schon in Migration `001_server_1_0_base.sql` vorhandene Tabelle `tb.releases`; keine bestehende Migration wird nachträglich verändert.

## Ziel

Android, spätere iOS-/Windows-Clients und das Portal sollen nicht aus PostgreSQL-Schemanummern oder frei interpretierten Serverversionsstrings ableiten, ob sie weiterarbeiten dürfen. Der Server liefert stattdessen einen expliziten Kompatibilitätsstatus.

Öffentlicher Read-Vertrag:

- `GET /api/v1/client-compatibility`
- `GET /api/v1/releases/{platform}/latest`

Administrative Verwaltung:

- `GET /api/v1/admin/releases`
- `POST /api/v1/admin/releases`
- `PATCH /api/v1/admin/releases/{release_id}`
- `POST /api/v1/admin/releases/{release_id}/publish`
- `POST /api/v1/admin/releases/{release_id}/retire`

Die Admin-Schreibpfade bleiben Portal-Session + CSRF + `ADMIN` vorbehalten. Geräte-Bearer erhalten keinen Zugriff auf `/api/v1/admin/*`.

## Plattformen und Status

Unterstützte Plattformwerte sind:

- `ANDROID`
- `IOS`
- `WINDOWS`

Der Kompatibilitätsstatus ist bewusst explizit:

- `NO_PUBLISHED_RELEASE`: noch kein veröffentlichter Release für die Plattform;
- `UNKNOWN_BUILD`: Client hat keine Buildnummer gemeldet;
- `SUPPORTED_CURRENT`: Build ist mindestens auf dem neuesten veröffentlichten Stand;
- `SUPPORTED_UPDATE_AVAILABLE`: Build ist unterstützt, neuerer optionaler Release vorhanden;
- `UPDATE_REQUIRED`: neuerer als verpflichtend markierter Release vorhanden;
- `UNSUPPORTED_BUILD`: Build liegt unter `minimum_supported_build`.

Zusätzlich liefert der Endpunkt die Booleans `supported`, `update_available` und `update_required` sowie `minimum_supported_build`, `latest_build` und `latest_version`.

## Sicherheits- und Datenregeln

- Veröffentlichte Releases sind hinsichtlich ihrer Release-Metadaten unveränderlich; Änderungen erfolgen über einen neuen Draft/Release statt stiller Umschreibung eines veröffentlichten Stands.
- `minimum_supported_build` darf nicht größer als die Buildnummer des Releases sein.
- Für Android ist beim Veröffentlichen eine Buildnummer Pflicht.
- `download_url` akzeptiert nur absolute `http`-/`https`-URLs.
- Öffentliche Release-Antworten geben interne `storage_key`- oder SHA-/Serverpfade nicht aus.
- Der aktuelle Baustein verwaltet Release-Metadaten und externe Downloadziele. Ein serverseitiger APK-/Binär-Upload wird separat gebaut und erst danach als eigener Capability-Stand freigegeben.

## Capabilities

Neu:

- `release_catalog_v1=true`
- `client_compatibility_v1=true`

Damit kann Android den Server-1.0-Vertrag künftig über Capabilities erkennen und muss nicht mehr anhand einer 0.6-/0.7-Versionsschwelle raten.

## Tests

`server/checks/33_verify_tb_server_1_releases_source.py` prüft:

- Python-Syntax der neuen Module;
- vorhandene Release-Tabelle aus Migration 001;
- alle öffentlichen/Admin-Endpunkte;
- Admin-/CSRF-Vertragsmarker;
- keine internen Storage-Felder im öffentlichen Release-Payload;
- die Statuslogik für aktuellen, optional veralteten, verpflichtend veralteten und nicht mehr unterstützten Build;
- Workflow-/Capability-Vertrag.

Die vollständige PostgreSQL-/FastAPI-Laufzeitprüfung bleibt wegen des bekannten GitHub-Runner-Problems und der aktuell fehlenden lokalen PostgreSQL-Instanz noch offen.
