# REF-DATA-015 – Luftbild, Gleis-/Weichenzuordnung und Testquellen

Stand: 04.10.2026. Freigabe vor Code im Vertragscommit `c4e7524aa1753b98d04b91fc87309c10691fd59b`. SERVER/Portal, kein Android-Code. Keine NAS-Daten in dieser Sitzung verändert.

## Bedienung

- In der Trassendatenübersicht den Bahnhof auswählen/einpassen. Unter **Hintergrund** Luftbildanbieter wählen, bei Bedarf Deckkraft ändern. Ohne Auswahl findet kein Luftbildabruf statt. Der vorhandene Suchfilter findet importierte Bahnhöfe/Achsen; er ist keine weltweite Ortssuche.
- **Luftbild: Anbieter vorschlagen** verwendet grobe Landesausdehnungen. Bei Überschneidung am Landesrand muss BY/SN/BW ausdrücklich gewählt werden. Noch nicht hinterlegte Regionen bleiben ohne Bild. Näher als 12 km Ausschnittbreite zoomen. Zoom/Pan bewegen Bild und Geometrie gemeinsam; Originalachsen werden nicht verschoben/skaliert. Fehlgeschlagene Abrufe haben einen sichtbaren Hinweis und Wiederholen-Schaltfläche.
- Achse öffnen → **Gleis / Weiche zuordnen …**. Editor bleibt über der Karte, damit Kartenklicks möglich sind. Originalquelle ausdrücklich wählen. Bahnhof über die vorhandene gemeinsame Bahnhofsreferenz suchen/auswählen.
- **Gleisabschnitt**: Nummer, Anfang, Ende; Punkte auf der gewählten Achse anklicken oder geometrische Achsposition in Metern eingeben. **Zuordnung prüfen** zeigt den tatsächlichen Abschnitt und seine Länge; erst **Zuordnung speichern** schreibt.
- **Weiche**: WA und WE auf derselben Originalquelle, WEZ auf einer ausdrücklich gewählten anderen Achse. Der Server bestimmt den Anschluss des Abzweigs am WA, prüft Mehrdeutigkeit und maximal 2 m Anschlussabstand. Stamm und Zweig behalten getrennte Längen. Fehlende Abzweiggeometrie wird nicht erfunden. Die 2-m-Prüfung ist ein technischer Plausibilitätsfilter, kein Vermessungsnachweis.
- Gespeicherte Zuordnungen können geladen, geändert, deaktiviert und wieder aktiviert werden. **Neu / Stand laden** öffnet eine neue Zuordnung auf derselben Achse. Die Historie hält Versionen und vollständige Vorher-/Nachher-Metadaten fest.
- Violett gestrichelt zeigt gespeicherte Zuordnungen. Der Gleisname wird am sichtbaren zugeordneten Abschnitt angezeigt. Die übrige Originalachse behält die Streckenbezeichnung, soweit bekannt. Die Trefferliste behält die ursprüngliche Achsidentität; die Kennzahlen zählen weiterhin Quellachsen, nicht die zusätzlichen Arbeitszuordnungen. Bei geänderter/archivierter Quelle erscheint ein Prüfhinweis.
- Das ist eine bestätigte Arbeits-/Anzeigezuordnung, **keine amtliche Bahnhofsgrenze**. Luftbildaufnahmen können älter als der heutige Gleiszustand sein. Drei Punkte ersetzen keine fehlende Trassierungsgeometrie.

## Testquellen zurücksetzen

**Testquellen …** → nach Datei/Bahnhof/Strecke bzw. neuem Erstimport-Paketnamen suchen → konkrete Dateien markieren → **Zurücksetzen prüfen**. Auswahl bleibt beim Blättern erhalten; maximal 100 Quellen pro Vorgang. Vorschau nennt Dateien, Abschnitte und betroffene Achsen. Aktive Arbeitszuordnungen auf den ausgewählten Quellen müssen vorher ausdrücklich deaktiviert werden. Fremde/private Katalogachsen sperren den Vorgang.

Erst die Bestätigung setzt Katalogquellen und zugehörige Abschnitte inaktiv; nur Achsen ohne verbleibende aktive/zu prüfende Abschnitte werden archiviert. Originaldateien, Parse-Daten, Historie und Bautagebücher bleiben erhalten. Änderungen nach der Vorschau führen zum Konflikt statt zu einer veralteten Ausführung.

Unter derselben Dateiauswahl gibt es **Wiederherstellen prüfen**. Das stellt die vorherigen Quell-/Abschnittsstatus wieder her. Bereits zuvor archivierte Achsen bleiben archiviert. Arbeitszuordnungen werden nicht automatisch reaktiviert; sie sind separat zu prüfen. Ein Reimport identischer Dateien/automatischer Neuindex reaktiviert zurückgesetzte Quellen nicht verdeckt: dafür Wiederherstellen benutzen. Alte Importe besitzen keine belastbare Paketkennung; der Dateiname eines Bahnhofs ist kein Eigentumsnachweis. Neue Originale erhalten Erstimport-Paketkennung/-name, bereits vorhandene identische Originale behalten ihre Herkunft.

## 5302-0 und Importdiagnose

Vollständige ORIGINAL_TRA-Segmente besitzen Rohstationsgrenzen. Bei belegten Type-6-Nullweg-Stationssprüngen stimmen diese nicht mit der kontinuierlichen geometrischen Stationierung überein. Vollständige Originale werden deshalb vollständig verdichtet; manuelle und echte Teilabschnitte bleiben streng zugeschnitten. Originale bleiben unverändert, kein pauschales Clamping.

Prüfung mit dem privat bereitgestellten Original-ZIP:

| Quelle | Darstellbare Punkte | Geometrische Achsposition (m) |
|---|---:|---|
| 5302S_ER0.TRA | 16497 | 4597.6468 bis 37506.845793 |
| 250919_5302L_ER0.TRA | 3470 | 7518.069611 bis 14433.718144 |
| 250919_5302R_ER0.TRA | 3545 | 7409.480184 bis 14470.670874 |
| 5340S_ER0.TRA | 444 | -440 bis 437.633212 |

5302S war vorher nach Rohgrenzenzuschnitt leer. Vier Sprünge ergeben zusammen 93.1542 m. Physische Originallänge nutzt die normalisierte Stationierung. Der KM-Editor erhält wirksame geometrische Quellgrenzen separat vom unveränderten Originaldatensatz. Importvorschau weist Sprünge mit beiden Grenzen aus und kennzeichnet aus Quelldaten abgeleitete Gleisnamen als räumlich zu bestätigen. Private Originaldateien sind nicht im Commit enthalten.

## Technischer Vertrag / Android-Übergabe

Alle neuen Routen unter `/api/v1/survey-catalog/management/workspace`, ausschließlich Admin; POST mit bestehendem Admin-CSRF-Schutz:

| Methode/Pfad | Zweck |
|---|---|
| GET `/axes/{axis_id}` | Quellsegmente mit Version, WGS84-Koordinaten und paralleler Stationsliste; Achse und alle Arbeitszuordnungen |
| GET `/axes/{axis_id}?include_geometry=false` | Wirksame Quellgrenzen ohne Koordinatentransformation für den KM-Editor |
| POST `/axes/{axis_id}/preview` | Assignment prüfen, abgeleitete Pfade/Längen und Preview-Token |
| POST `/axes/{axis_id}` | Assignment anlegen/ändern/deaktivieren, idempotent |
| GET `/sources?q=…&offset=…` | GLOBAL-Quellen, 100 pro Seite, Herkunft soweit vorhanden und Resetstatus |
| POST `/reset/preview` | Aktueller Umfang/Abhängigkeiten und Bestandsfingerprint |
| POST `/reset` | Bestätigtes RESET oder RESTORE, idempotent |

Assignment: `client_change_id`, `base_version`, optional `record_id`/`record_version`, `kind=TRACK|SWITCH`, `name`, `station_code`, `station_name`, `anchors`, `active`, beim Speichern `preview_token`. Anker enthalten `axis_id`, `axis_version`, `segment_id`, `segment_version`, `station_m`. Reihenfolge TRACK=[Anfang,Ende], SWITCH=[WA,WE,WEZ]. Quelle muss ein aktives GLOBAL-Originalsegment sein; keine automatische Auswahl benachbarter Gleise. Überschneidende aktive Gleisintervalle auf derselben Quelle werden zurückgewiesen.

Persistenz: `survey_catalog_axes.metadata.workspace_records` auf der Anfangs-/WA-Achse. Record enthält UUID, Version, Aktivstatus, Name/Betriebsstelle, Quellen-/Achsreferenzen, DBREF-Anker, abgeleitete Pfadreferenzen/Längen, `boundary_type=USER_ASSIGNMENT`, `provenance=ADMIN_EXISTING_AXIS`, Anschlussabstand. Originalgeometrie wird nicht kopiert oder verändert. Abgeleitete WGS84-Pfade kommen additiv als `assignments` in `coverage/{axis_id}/geometry`. Änderung erzeugt AXIS-Historie/Audit. Android muss diese Records später ausdrücklich darstellen/verarbeiten; die bestehenden Android-Builds sind dadurch nicht automatisch angepasst. Keine neuen mobilen Schreibrechte und keine GPS-Neuanlage in diesem Serverblock.

Reset: `original_ids`, `mode=RESET|RESTORE`, `client_change_id`, beim Bestätigen `preview_token`. Marker `metadata.workspace_reset` hält vorherigen Status auf Quelle/Abschnitt; `workspace_reset_status` hält den vorherigen Achsstatus. Transaktionen, Zeilensperren und gemeinsam mit dem Neuindex genutzte Advisory-Sperre schützen parallele Vorgänge. Mutation-Receipts verhindern doppelte Wiederholungen; Wiederverwendung einer ID für anderen Inhalt wird abgewiesen.

## Luftbildanbieter und Abruf

WMS 1.1.1, EPSG:4326 mit Reihenfolge lon/lat im BBOX. Das passt zur bestehenden linearen Kartenprojektion. Direkter Browser-Bildabruf ohne API-Schlüssel/Abrechnungskonto; keine Speicherung/Kachelmassendownloads und kein Server-Proxy. Maximal 1600×1600 Pixel, 350 ms Entprellung, 15 s Fehlergrenze, Schutz gegen veraltete Antworten. Keine Livebild-/Offlinezusage. Fremdanbieter erhält die für den Bildabruf nötige Anfrage.

| Land | Dienst / Layer | Quellenhinweis |
|---|---|---|
| BY | `https://geoservices.bayern.de/od/wms/dop/v1/dop20` / `by_dop20c` | Bayerische Vermessungsverwaltung · CC BY 4.0 |
| SN | `https://geodienste.sachsen.de/wms_geosn_dop-rgb/guest` / `sn_dop_020` | GeoSN · dl-de/by-2-0 |
| BW | `https://owsproxy.lgl-bw.de/owsproxy/ows/WMS_LGL-BW_ATKIS_DOP_20_C` / `IMAGES_DOP_20_RGB` | LGL-BW (Jahr des Bezugs) · dl-de/by-2-0 |

Amtliche Produkt-/Metadatenquellen, am 04.10.2026 abgeglichen:
- https://geodatenonline.bayern.de/geodatenonline/seiten/wms_dop20cm
- https://www.geodaten.sachsen.de/rechtsgrundlagen-und-nutzungsbedingungen-4509.html
- https://www.landesvermessung.sachsen.de/luftbild-produkte-4982.html
- GeoSN-Dienstregistrierung GD-SVC-SpecOGC #708 nennt URL und Layer `sn_dop_020`.
- https://metadaten.geoportal-bw.de/geonetwork/srv/api/records/4927a198-e9ce-da9a-a5ef-c11f21ff173f (URL, Layer, Versionen, EPSG:4326 und Lizenz).

Direkte Capabilities-/Bildabrufe aus der Arbeitsumgebung scheiterten an Timeout/HTTP403 bzw. dem Webabruf. Deshalb ist die tatsächliche Erreichbarkeit und lagerichtige Darstellung in allen drei Ländern **noch live zu prüfen**, nicht als bestätigt markiert. Google-Screenshots oder Google-Karten werden nicht eingebunden.

## Übernahme auf Synology

Für den bestehenden Staging-Bind-Mount-Aufbau, über GitHub Desktop und File Station:

| GitHub-Datei | Ziel relativ zu `/volume1/docker/tb-helfer-v1` |
|---|---|
| `server/tbserver/survey_catalog.py` | `tbserver/survey_catalog.py` |
| `server/tbserver/survey_workspace.py` (neu) | `tbserver/survey_workspace.py` |
| `server/tbserver/admin_survey_import.py` | `tbserver/admin_survey_import.py` |
| `server/templates/v1_admin_survey_overview.html` | `templates/v1_admin_survey_overview.html` |
| `server/v1_migrations/020_survey_workspace_reset.sql` (neu) | `v1_migrations/020_survey_workspace_reset.sql` |

Vor der DB-Migration vorhandenen verifizierten Backupstand verwenden. Die Migration erweitert ausschließlich erlaubte Assetstatus um INACTIVE; sie setzt keine Testquelle zurück. Vorherige Migrationen nicht ändern. `.env`, Storage-/Datenbank-/Backup-Verzeichnisse nicht überschreiben. Kein Image-Rebuild bei bestehenden Bind-Mounts, da keine Dependencies geändert wurden.

Nach dem Datei-Upload im Terminal des **API-Containers**:

```sh
python -m tbserver.migrations status
python -m tbserver.migrations up
python -m tbserver.migrations status
```

Erwartet: Migration020 angewendet, danach `latest_applied: 20` und `pending: []`. Bei Fehlern nicht blind fortsetzen, Ausgabe prüfen. Anschließend API und Worker im Container Manager neu starten; Browser mit Strg+F5 laden. Kein NAS-SSH nötig. Keinen Testreset automatisch ausführen.

## Prüfung und offene Abnahme

- 80 Python-Tests in Checks120–126 PASS: tatsächliche Produktionsfunktionen/Pydantic, Storage-Doubles; darunter16 neue Workspace- und6 Stationsgrenzen/Neuindex-Tests. Bestehende58 Tests weiterhin PASS.
- Node119 PASS: tatsächliche Template-Funktionen, DOM-/Transport-/Image-Doubles; u.a. WMS-BBOX, Opt-in/Fehler/Retry, unvollständige Anker, Preview-Invalidierung, CSRF, Doppelklickschutz, Resetvorschau ohne Mutation und Konfliktbehandlung. Kein visueller Browsernachweis.
- Python-Syntaxprüfung PASS. Read-only Original-ZIP-Reproduktion wie oben PASS. Keine privaten Rohdaten committed.
- Nicht durchgeführt: echter FastAPI-/PostgreSQL-/PostGIS-E2E, Migration auf NAS, visueller Browserlauf, reale Landesdienstbilder, Android-Gerät. Chromium fehlte; der zuvor versuchte Download war umgebungsbedingt blockiert.
- Nächste Live-Abnahme:5302-0 sichtbar ohne Reimport; Luftbild BY/SN/BW bei bekannter Position; Gleisabschnitt anlegen/ändern/deaktivieren; Stamm/Abzweig mit separaten Längen; zweiter Browser erzeugt Versionskonflikt; ausgewählte Testquelle vor/nach Reset und Restore prüfen, ungewählte Quelle und Originalhash unverändert. Kennzahlen/Alt-KM/GVP und schnelle Übersicht gegen bisherigen bestätigten Stand vergleichen.
