# TB-Server 1.0 – Releases und Client-Kompatibilität

Stand: 2026-09-20

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
- `PUT /api/v1/admin/releases/{release_id}/binary`
- `DELETE /api/v1/admin/releases/{release_id}/binary`
- `POST /api/v1/admin/releases/{release_id}/publish`
- `POST /api/v1/admin/releases/{release_id}/retire`

Öffentlicher serverseitiger Binärdownload:

- `GET /api/v1/releases/{release_id}/download`

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
- `download_url` akzeptiert für externe Ziele nur absolute `http`-/`https`-URLs.
- Ein Draft kann alternativ genau eine aktuelle serverseitige Binärdatei besitzen. Sie liegt unter dem stabilen internen Storage-Key `releases/<platform>/<release_uuid>/package.bin`.
- Der Binärupload wird streamend geschrieben, nicht als vollständiger Request in den Arbeitsspeicher geladen; Obergrenze 512 MiB.
- Optional kann der Admin `X-Release-SHA256` senden. Bei Abweichung wird die Binärdatei verworfen.
- Dateitausch erfolgt über temporäre Datei + atomaren Replace + Rollback-Datei, damit ein DB-Fehler keinen scheinbar passenden Release hinterlässt.
- Server-Binärdatei und externe Download-URL sind alternative Quellen. Ein Binärupload löscht eine vorhandene externe URL; eine neue externe URL ist erst zulässig, nachdem ein Draft-Binary entfernt wurde.
- Ein Release kann erst veröffentlicht werden, wenn eine serverseitige Binärdatei oder eine externe Download-URL vorhanden ist. Bei internem Binary wird zusätzlich geprüft, dass die Datei im Storage tatsächlich existiert.
- Nach Veröffentlichung darf die Binärdatei nicht mehr über die Admin-API ersetzt oder gelöscht werden; dafür ist ein neuer Release-Draft anzulegen.
- Öffentliche Release-Antworten und die bereinigte Admin-Liste geben interne `storage_key`-Pfade nicht aus.
- Für ein serverseitiges Binary wird öffentlich nur der sichere relative Downloadendpunkt geliefert.
- Der öffentliche Binärdownload liefert nur Releases mit Status `PUBLISHED`.

## Android-Updatevertrag

- Android fragt beim Start unaufdringlich `GET /api/v1/client-compatibility` mit eigener Buildnummer/Version ab.
- Ohne Update bleibt auf dem Startbildschirm **kein Update-Symbol** sichtbar.
- Optionales Update: blaues Update-Symbol; Pflichtupdate: rotes Update-Symbol.
- Unter **Einstellungen → Wartung & Diagnose** bleibt `Nach Updates suchen` als manueller Kontrollweg sichtbar.
- `release_notes` werden dort als „Was ist neu?“ angezeigt.
- Für serverseitig hochgeladene Binärdateien liefert der öffentliche Release-Payload zusätzlich `sha256` und `byte_size`. Android prüft beides vor dem Paketinstaller.
- Android lädt die APK über den veröffentlichten HTTPS-Downloadvertrag. Die eigentliche Installation bleibt eine Android-Systemaktion; beim ersten Mal kann die Freigabe „Apps aus dieser Quelle installieren“ erforderlich sein.
- Ein normales In-Place-Update darf die gespeicherte Gerätebindung, Serveridentität und lokale Baustellendaten nicht löschen.

## Admin-Weboberfläche

`GET /admin/v1/releases` bietet eine dünne Server-1.0-Weboberfläche auf denselben Release-Endpunkten:

- Draft anlegen;
- externe URL setzen oder serverseitige Binärdatei hochladen;
- SHA-256 vor dem Upload im Browser berechnen und als `X-Release-SHA256` mitsenden;
- Draft-Binary entfernen;
- Release veröffentlichen;
- veröffentlichten Release zurückziehen;
- öffentlichen Download prüfen.

## Capabilities

Aktiv:

- `release_catalog_v1=true`
- `release_binary_v1=true`
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

`server/checks/40_verify_tb_server_1_release_binary_source.py` prüft zusätzlich:

- streamenden Admin-Binärupload und 512-MiB-Grenze;
- SHA-256-Prüfung;
- atomaren Storage-/Rollback-Vertrag;
- Veröffentlichung nur mit vorhandenem Downloadziel;
- öffentliches Binary nur für `PUBLISHED`;
- keine internen Storage-Keys in Public-/Admin-Payloads;
- Admin-Webroute und Upload-/Publish-Oberfläche;
- Capability `release_binary_v1`.

Die vollständige PostgreSQL-/FastAPI-Laufzeitprüfung bleibt wegen des bekannten GitHub-Runner-Problems und der aktuell fehlenden lokalen PostgreSQL-Instanz noch offen.
