# SHARED-KM-1103 – gemeinsamer KM-Beitrag vom Handy

Stand 03.11.2026. Serverauftrag vor Runtimeänderung in 0f674771009cb6a6c91f0465916e3232cc77a9fa gesichert.

## Geltungsbereich
Manuelle KM gehören zum vorhandenen GLOBAL-Achsenkatalog auf dem Server. Angemeldete Nutzer/Geräte anderer Firmen können denselben Bestand abrufen und versioniert ergänzen. Keine Baustellen-/LEAD-Bindung für gemeinsame Referenzdaten. Firmen-BTB, Fotos, Mängel und private Originaldateien bleiben unter ihren bisherigen Rechten. Kein öffentlicher anonymer Schreib-/Lesezugang. Keine neue parallele Ablage und keine neu erfundene Geometrie/Achse.

Serverflag `survey_shared_km_sync_v1`. Basis `/api/v1/survey-catalog`.

| Methode/Pfad | Vertrag |
|---|---|
| GET `/axes/{axis_id}/km-sync` | Authentifizierter Snapshot einer GLOBAL-Achse: axis_id/axis_version, aktive ORIGINAL_TRA-Quellen (id/current_version/source_original_id/sha256), manuelle Abschnitte inklusive INACTIVE-Tombstones und übertragener Rohanker. Keine Autoren-/Firmenmetadaten, freien Metadaten, Speicherpfade oder Originaldatei-Inhalte. |
| POST `/axes/{axis_id}/km-sync` | Einen vollständigen KM-Beitrag auf genau dieser bestehenden Achse anlegen/ändern. Keine Achsenwahl nach Nähe/Name, keine Gesamtbestandssynchronisation. |
| POST `/axes/{axis_id}/km-sync/{segment_id}/deactivate` | Bewusste versionierte Deaktivierung genau dieses manuellen Beitrags. Originalquelle bleibt aktiv; Historie/Anker bleiben vorhanden. Keine implizite Wiederaktivierung. |
| GET `/changes?after=…&limit=…` | Vorhandener gemeinsamer Katalogfeed, eigener dauerhafter Androidcursor erforderlich. Erst nach lokaler dauerhafter Anwendung fortschreiben; bei Pending/Conflict lokale Fassung erhalten. Nicht der firmengebundene BTB-Feed. |

Sicherheit: vorhandenes current_portal_user/require_csrf. Geräte-Bearer über bestehenden current_device_user; Browsercookie braucht gültiges CSRF-Paar. Keine Adminpflicht für diese drei neuen Routen; Admin-Managementrouten unverändert geschützt. COMPANY-Achsen werden durch diesen gemeinsamen Vertrag nicht veröffentlicht, auch nicht bei eigener Firma.

## Speichern
Pflichtfelder:
- `client_change_id`: pro Nutzer eindeutig und über Timeout/Retry stabil; anderer Request mit derselben Kennung →409. Fingerprint bindet Route/Aktion/Achse/gesamten Inhalt.
- `contribution_id`: einmal erzeugte UUID, bleibt über Bearbeitung/Umbenennung identisch. Neuanlage ohne segment_id/segment_version. Änderung mit segment_id gleich contribution_id und beim Bearbeiten bekannter segment_version.
- `base_version`: bekannte Achsenversion; `source_segment_id`+`source_version`: ausdrücklich gewählte ORIGINAL_TRA-Quelle derselben Achse; `source_original_id`+`source_sha256`: Original-UUID und vollständiger SHA256, keine Namens-/Hashpräfixsuche.
- `station_from_m`, `station_to_m`: interne Station auf der kanonischen Server-Quellgeometrie, aufsteigend. `km_from_m`, `km_to_m`: Bahn-KM in Metern, dürfen fallen und echt0 enthalten. Keine Vermischung mit lokaler Room-ID oder Dateistation vor normalisierten Stationssprüngen.
- `anchors`:2..100 stabile UUIDs, kind MANUAL/GPS/GVP/MAST, station_m+km_meter. Jeder Anker innerhalb des Abschnitts; alle Anker müssen dieselbe Kalibrierung mit maximal5m Restabweichung erklären. Mindestens20m Stationsabstand, mindestens1m KM-Differenz, Faktor0,5..1,5. Abschnitt nur innerhalb des belegten Ankerbereichs; keine neue Extrapolation. Widersprüchliche Überlappung mit aktivem manuellem Abschnitt →409, kein Überschreiben.
- GPS/MAST: x_dbref+y_dbref oder gps_lat+gps_lon. WGS84 wird mit PostGIS in die eindeutig aus der Quellgeometrie abgeleitete DB_REF-Zone transformiert; gleichzeitig gelieferte DB_REF-Werte dürfen maximal0,5m abweichen. GPS zusätzlich accuracy_m; optional gps_spread_m. Rohwerte/Qualität und Lotfuß/Achsabstand bleiben erhalten. Lotprojektion auf exakt gewählter Achse, keine Klemme hinter dem Achsende; mehrdeutiger Lotfuß/Abstand>100m →Fehler entsprechend bestehendem GVP-Projektor. Genauigkeit wird bewahrt, nicht als Vermessungsfreigabe ausgegeben.
- GVP: `gvp_ref` mit asset_id/asset_version/point_index/km_meter, serverseitig aufgelöst aus aktivem GLOBAL-GVP/KF/KV-Bestand und rechtwinklig auf die gewählte Achse projiziert. Kein Vertrauen auf mitgesendete GVP-Koordinaten. MANUAL-Anker erhalten keine GPS-/GVP-Quellenbehauptung.
- Optional note und confirm_length_difference, bestehende Längen-/Quell-/Überlappungsvalidierung bleibt wirksam. Numerische Messungen endlich, keine Bool-/Stringkonvertierung. Unbekannte Top-Level- oder Ankerfelder werden abgewiesen.

Antwort: axis_id/axis_version, segment_id/entity_version, scope GLOBAL, status ACTIVE und Requestfingerprint. Receipt ist Bestätigung genau dieser Operation; spätere Revisionen über Snapshot/Feed abholen.409 lässt die lokale Fassung unbestätigt bestehen. Keine automatische Neubasierung auf die frisch gelesene Serverversion.

Deaktivierung: base_version, segment_version, client_change_id, source_segment_id/source_version, source_original_id/source_sha256. Antwort status INACTIVE und bestätigte Versionen. Unbekannter oder nicht mehr aktiver manueller Beitrag bleibt Fehler; identischer erfolgreicher Retry liefert Receipt.

## Feed-Vollständigkeit
Katalogfeed liest vor dem ersten History-SELECT unter `LOCK TABLE tb.survey_catalog_history IN SHARE MODE`. Das wartet vorhandene INSERT-Schreibtransaktionen ab und verhindert neue History-INSERTs bis zum Ende des kurzen Lesetransaktionsblocks. Dadurch darf keine größere sichtbare bigserial-ID eine kleinere uncommittete History-ID überholen. Kein Netzwerk innerhalb dieser Transaktion. PostgreSQL-Commit/Rollback-/Lock-/Durchsatztest vor Freigabe erforderlich; lokale SQL-Doubles sind dafür kein Beweis. Bereits früher übersprungene Cursor werden dadurch nicht rückwirkend repariert. Kein ungefragter Vollreload.

## Verbindlicher Android-Anschluss – noch nicht implementiert
1. Persistente Zuordnung der bestehenden lokalen Achse/Dateisignatur zu Serverachse, Quellsegment, Original-UUID, vollständigem Hash und Versionen. Bei gleicher Quelle/gleichem Namen/überlagerten Achsen keine Vermutung. Unbekannte Originale über bestehenden Import-/Parse-/GLOBAL-Katalogweg übernehmen; neue Endpunkte laden keine Originaldateien hoch.
2. Stabile Beitrags-/Anker-UUIDs und dauerhafte Outbox gemeinsam mit lokaler Speicherung. States Lokal gespeichert / Übertragung ausstehend / Server bestätigt / Konflikt. Unvollständige Entwürfe und Ein-Anker- bzw. nicht sicher zuordenbare Altbestände bleiben erhalten und dürfen nicht als hochgeladen erscheinen.
3. Bestehende stationStart/stationEnde, kmAnkerStation/kmAnkerMeter, Punkt2 sowie Rohpunkte auf kanonische Serverstationen abbilden. Bei fallender lokaler Laufrichtung Start/Ende und die zugehörigen KM korrekt umsortieren. Ein-Anker-plus-Richtung-Altmodell nicht ungeprüft in zwei Messanker umdeuten; lokale Funktionen bleiben verfügbar.
4. Vorhandene GPS-Lat/Lon, Genauigkeit und Streuung mitgeben. GVP/Mast nur mit belegter Quelle/Version. Bestehende manuelle Abschnitte/KM-Punkttabellen vollständig inventarisieren; keine Löschung bei unklarer Zuordnung.
5. Gezielter Sendetrigger nach Speichern; Wiederholung derselben Operation nach Prozessende/Netzrückkehr. Den destruktiven WorksiteTrackDataApi.reconcileCanonical-Sollbestandvergleich für gemeinsame Daten nicht verwenden. Keine fehlend→DELETE-Logik, kein blindes409-Rebase.
6. Gemeinsamer Katalogbootstrap und eigener feed cursor; Änderungen anderer Firmen anwenden, eigene Pending-Fassung nicht überschreiben. INACTIVE als Tombstone derselben UUID. ManuellOrange, belastbarImportiertGrün. Keine Freigabe der mobilen Funktion allein anhand Capability; erst Adapter/Outbox/Empfang und Gerätetest fertigstellen.

Serverrolle hat keine app/**-Änderungen vorgenommen. Nutzerablauf Handy→Server→Portal→anderes Firmenhandy erst nach diesem Androidblock und gemeinsamem E2E vollständig.
