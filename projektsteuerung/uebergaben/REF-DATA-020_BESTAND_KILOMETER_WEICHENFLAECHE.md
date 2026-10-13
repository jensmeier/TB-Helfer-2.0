# REF-DATA-020 – aktueller Bestand, Kilometerbezug, Weichenfläche

Stand: 05.10.2026. Rolle SERVER, Branch `feature/tb-server-1.0`, PR6 bleibt Draft.
Basis: e5aa6cde14048c28f44fb5fe23cd7808f5465b31. Android423f378 bleibt unverändert.

## Benutzerbestätigungen

Benutzer bestätigt Neustart mit vorhandenem Datenbestand sowie die drei angefragten Praxistests: Gl999 finden, WA/WE speichern/wieder öffnen, Anzeigeauswahl behalten. Diese Punkte sind geschützt. Zwei-Browser-Konflikte, vollständiges Archiv/Restore, Last und Geräte-Netzwerk-Sync sind dadurch nicht abgenommen.

## Befund und Verhalten

Das Anschlussnetz hatte bisher nur `REFERENCE_ONLY`/`UNCHANGED_REFERENCE`, keine berechneten Endpunktkilometer. Die daneben stehende Qualitätsanzeige einer Originalachse ist kein Kilometerwert des abgegrenzten Netzobjekts.

Das aktuelle Gl1 ist weiterhin dasselbe UUID-Netzobjekt mit versionierten Anfang/Ende-Knoten. In der Ansicht „Aktueller Bestand“ werden Originalachsen in Karte und Objektliste ausgeblendet. „Mit Originalachsen“ blendet die Grundlagen wieder ein. Vorhandene lokale Anzeigepräferenzen bleiben erhalten; bei ihnen einmal bewusst „Aktueller Bestand“ wählen. Ohne gespeicherte Präferenz ist dies die Vorgabe. Bahnhofsdialoge zeigen aktuellen Bestand vor den eingeklappten Originalen. Beim aktiven Setzen eines Punktes auf eine Linie erscheinen Originalachsen vorübergehend als Auswahlhilfe. Normale Kartenklicks bevorzugen Netzobjekte.

Im Objekteditor: Positionsstatus, eigener Kilometerbezug und begründete Kilometeranzeige je Anfang/Ende beziehungsweise WA/WE/WEZ. Geometrie und Kilometer können unterschiedliche Quellen verwenden. Neue Kilometerreferenz verlangt ausdrücklich die Quellenwahl; während offener Wahl ist Speichern blockiert. Neuberechnung erfolgt bei Prüfen und Speichern, nicht als unveränderte Übernahme alter Werte. Gespeicherte Netzleseantworten berechnen Kilometer gegen den aktuellen Kalibrierungsstand erneut. Quellen-/Versionsänderungen führen zu einem Prüfhinweis beziehungsweise fehlenden aktuellen Werten. Ungeprüfte km_observations bleiben getrennte historische Beobachtungen, niemals automatische Kalibrierung.

## Additiver Vertrag, schema_version 1

Neue optionale Eingabefelder am OBJECT:

| Feld | Inhalt | Weglassen bei bestehendem Objekt |
|---|---|---|
| km_reference | null oder {axis_id, axis_version, segment_id, segment_version} | bisherigen Bezug erhalten |
| path_sections | geordnete Liste von Quellenbezügen plus role, station_from_m, station_to_m | bisherigen Verlauf erhalten |
| position_status | PROVISIONAL oder CONFIRMED | bisherigen Status erhalten |

`km_reference:null` verwendet wieder die jeweiligen Punktbindungen. `path_sections:[]` verwendet die bisherige Herleitung aus den Punktquellen. Neue Objekte sind ohne Statusangabe PROVISIONAL. Alte Idempotenz-Fingerprints bleiben beim Weglassen der Felder identisch. Ein neuer Client darf diese Felder erst gegen den aktualisierten Server senden; ältere Server können sie ablehnen. Stabile IDs, Basisversion, Previewtoken, affected_objects und atomarer Delta-Batch gelten unverändert.

`kilometers` und `selection_area` sind ausschließlich abgeleitete Lesefelder und werden nicht als Eingabefelder akzeptiert. Quelle, Kalibrierung und Geometrie werden damit nicht überschrieben.

`kilometers={status, endpoints:[...]}`:
- status COMPLETE = alle Rollen haben einen Wert mit gemeinsamem Streckenbezug; PARTIAL = einige Werte vorhanden oder unterschiedliche Bezüge; UNKNOWN = keine; INACTIVE = deaktiviert. COMPLETE bestätigt die Endpunkte, keine lückenlose Innenkilometrierung.
- Je Rolle: role, node_id, node_version, km_m oder null, status MANUAL/AUTOMATIC/UNKNOWN, reason; soweit vorhanden route_number, reference, station_m, projection_distance_m, method, calibration{id,version} beziehungsweise matched_gvp.
- Gründe: NO_REFERENCE, SOURCE_CHANGED, ROUTE_MISSING, ROUTE_MISMATCH, OUTSIDE_REFERENCE, AMBIGUOUS_POSITION, AMBIGUOUS_CALIBRATION, NO_CALIBRATION.
- Eigene identische Originalquelle: direkte Achsposition. Andere ausdrücklich gewählte Quelle: Projektion auf deren vollständige Linie, maximal30m seitlich, keine Fortsetzung vor/nach deren Enden. Nahe gleichwertige Positionen an Schleifen werden verworfen. Der seitliche Abstand steht in der Vorschau. Keine automatische Auswahl aufgrund geometrischer Überlagerung.
- Vorhandene MANUAL_TRA_SECTION-Kalibrierung derselben Originalquelle gilt nur innerhalb ihres Intervalls, einschließlich fallender Kilometer. Widersprüchliche überlappende Werte werden verworfen. Sonst bestehende robuste GVP-Offsetlogik verwenden. Reine GRA-Min/Max-Bereiche beweisen keine punktbezogene Abbildung und werden hier nicht als exakte Kilometer ausgegeben. Bestehende Qualitätsanzeigen bleiben unverändert.
- GVP-Projektion wird erst benötigt, wenn am Punkt keine manuelle Kalibrierung greift; Kontext/Quellenergebnisse werden innerhalb eines Requests geteilt. Keine gemessene NAS-Laufzeitverbesserung behaupten.

Verlaufabschnitte:
- role TRACK/LINE bzw. MAIN/BRANCH; Reihenfolge je Weg relevant, beide Stationsrichtungen möglich, maximal32 Einträge.
- Jede Quelle global, aktiv, ORIGINAL_TRA und versioniert; Zuschnitt innerhalb der Quelle. Anschlüsse zwischen Teilstücken und zum Anfang/Ende höchstens0,5m Abstand, sonst expliziter Fehler. Keine erfundene lange Verbindung durch eine Lücke. Kurvenzwischenpunkte bleiben erhalten.
- Ohne explizite Teilstücke: bestehende gemeinsame Quelle/Abzweigherleitung; unbelegter Verlauf bleibt gestrichelte manuelle Näherung.
- Beim Verschieben auf derselben versionierten Quelle folgen unverändert übernommene erste/letzte Verlaufsgrenzen automatisch, auch an gemeinsam verbundenen Objekten und für alte Clients. Beim Wechsel zu freien Punkten oder anderen Quellen die Abschnittsgrenze ausdrücklich anpassen; Server blockiert veraltete Grenzen, statt unbemerkt falschen Verlauf zu speichern.
- Aktive Kilometer-/Verlaufsreferenzen schützen die Originale auch im quellselektiven und allgemeinen Testreset. Archiv und Historie behalten die Referenzen.

Weichenfläche:
- `selection_area` GeoJSON-artiges Polygon mit WGS84-Ring, half_width_m1.5, purpose SELECTION.
- Äußere konvexe Hülle der beidseitigen1,5m-Korridore entlang WA→WE und WA→WEZ; keine runden Endkappen. Dreieck-/trapezähnliche Aufweitung abhängig von der tatsächlichen Geometrie. Die Fläche einschließlich ihres Innenbereichs ist anklickbar.
- Beschriftung/Identität bleiben ohne Kilometer nutzbar. Punktbeschriftungen erhalten Kilometer, sofern berechnet; kein willkürlicher einzelner Kilometer für die gesamte Fläche. Keine betriebliche Sicherheitsgrenze.

## Android-Übergabe (noch nicht implementiert)

Zielablauf: Originalimport → gemeinsames Gleis/Weiche festlegen → Grenzen, Verlauf, Kilometergrundlage und Objektversion synchronisieren → anderer Nutzer erhält denselben aktuellen Stand → Umbau als neue Version desselben Objekts.

Read-only Vergleich mit Android423f378: ImportExportFragment verwendet ManuelleAbschnittKmLogik.kmFuerStation, belegte Rohkilometer als Fallback und stellt verwendete Rohachsen neutral/ohne Label dar. Keine numerische Gleichheit aller Android-/Server-Kalibrierungen behaupten. Der nachfolgende Android-Block muss vorhandene Berechnungsgrundlagen auf den gemeinsamen Vertrag abbilden, Ergebnis/Quelle zusammen darstellen und mit echten Geräten abgleichen. Serverberechnete Lesefelder nicht ungeprüft als Eingabefelder zurücksenden.

Geräte müssen neben Netzwerkänderungen auch den Original-/KM-Feed verfolgen und betroffene Objekte neu lesen. Historische Netzfeed-Snapshots sind keine Garantie für heute noch gültige Kalibrierung. commit_cursor ist weiterhin kein Lesecursor. Offline keine alten berechneten Kilometer an einen neu verschobenen Punkt hängen; ungeklärte Änderung lokal sichtbar halten, Versionskonflikte auflösen. Kein app/** geändert, kein APK-Build.

## Geprüft und Grenzen

- Python127:21 Tests PASS (bestehende Mutation/Version/Receipt/Archiv/Auth- und Schutzprüfungen mit Storage-/Transaktions-Doubles).
- Python128:2 Tests PASS (bisherige Lesehelfer/History mit SQL-Doubles).
- Python129:11 neue Tests PASS: fallende KM, Verschieben/Roundtrip, ausdrücklicher Bezug/keine Extrapolation, Teilkalibrierung/GVP/Widerspruch, Previewänderung, stale Quelle, alte Clients/null, falsche Strecke/GRA-Bereich, mehrdeutige Projektion, mehrteilige Kurve/Lücken/Endpunkte, metrische Weichenhülle, gemeinsame Abschnittsgrenzen atomar nachführen.
- Node119: PASS, tatsächliches Template-JS mit DOM-/Transport-Doubles, alte Regressionen plus Bestandsansicht, KM-Begründungen, Wiederöffnen, Invalidation, offene Referenzauswahl, Flächentreffer.
- Python-Syntax PASS. Kein Live-FastAPI/PostgreSQL/PostGIS, kein visueller Browser-/Jinja-Rendernachweis, keine NAS-/Handy-Abnahme dieses neuen Blocks. Diese Pakete sind lokal nicht verfügbar.

## Synology-Übernahme

Über GitHub Desktop aktualisieren, vier Laufzeitdateien mit File Station ersetzen:

| Repository | Unter /volume1/docker/tb-helfer-v1 |
|---|---|
| server/tbserver/survey_network.py | tbserver/survey_network.py |
| server/tbserver/survey_workspace.py | tbserver/survey_workspace.py |
| server/tbserver/admin_purge.py | tbserver/admin_purge.py |
| server/templates/v1_admin_survey_overview.html | templates/v1_admin_survey_overview.html |

API über Container Manager neu starten, Browser Strg+F5. Keine neue Migration, weiterhin21/21. Kein Reset/Reindex; .env, Originale und Datenbank unangetastet lassen.

Praxistest: „Aktueller Bestand“ wählen → Gl1 öffnen → unter Kilometerbezug Str5340-0 und passende Originalquelle ausdrücklich auswählen → Prüfen. Anfang/Ende mit vorhandener Kalibrierung vergleichen. Ohne belastbare Kalibrierung konkreten Grund prüfen, nicht beliebige Werte eintragen. Speichern, wieder öffnen, Anfang versetzen und neu prüfen. Weiche innerhalb aufgeweiteter Fläche anklicken. Mehrteiligen Verlauf und gemeinsame Anschlussänderung anschließend prüfen. Android erst im Folgeschritt.
