# TB-Server 1.0 – TRA-Verdichtung und Vermessungspunkt-Projektion

Stand: 2026-09-17

Dieser Baustein erzeugt aus den bereits geparsten, unveränderlichen Vermessungsoriginalen neu aufbaubare Ableitungen. Originaldateien und `tb.survey_parse_catalog` werden dabei nicht verändert.

## 1. TRA-Verdichtung

Die serverseitige Geometrie übernimmt bewusst die vorhandene Android-Logik aus `TraDateiParser.kt`:

- Sampleabstand: 2,0 m;
- Gerade: analytisch;
- Kreisbogen: analytisch aus Richtung und Radius;
- Übergangsbogen: numerische Integration mit 0,5-m-Schritt;
- echte TRA-Knoten bleiben exakte Segmentenden;
- unbekannte Elementtypen werden linear zwischen den Knoten interpoliert.

Damit entsteht serverseitig dieselbe fachliche Grundlage wie auf Android, statt eine zweite Geometrieinterpretation zu erfinden.

## 2. DB_REF/GK -> WGS84

`survey_geometry.py` portiert die bestehende Android-Umrechnung aus `DbRefKoordinatenwandler.kt` einschließlich der DB_REF->ETRS89-Helmertparameter.

Unterstützt werden die aus dem Rechtswert erkannten GK-Streifen 3, 4 und 5. Damit können insbesondere vorhandene rohe KF-Punkte im Streifen 3 mit TRA-Geometrien verglichen werden, ohne ihre ursprünglichen DB_REF-Koordinaten zu verändern.

WGS84 ist hier ausschließlich eine abgeleitete Rechenkoordinate. Die Originalkoordinaten bleiben im Parsergebnis erhalten.

## 3. Punkt -> TRA-Projektion

Der Server projiziert koordinatenhaltige Punkte aus verknüpften Quellen der Rollen `KM` und `CONTROL` auf die verdichteten TRA-Samples.

Die Abstandsqualität folgt der vorhandenen Android-Regel:

- bis 2 m: `GRUEN`;
- über 2 bis 5 m: `GELB`;
- über 5 bis 15 m: `ORANGE`;
- über 15 m: `ROT` bzw. standardmäßig nicht übernommen.

Die Standardgrenzen des Rebuild-Endpunkts sind daher:

- `reliable_distance_m = 5.0`;
- `max_distance_m = 15.0`.

Beide Werte werden im erzeugten Punktpayload dokumentiert.

## 4. Wann wird ein automatischer Originalanker erzeugt?

Ein projizierter Punkt wird nur dann `ORIGINAL_ANCHOR`, wenn **alle** folgenden Bedingungen erfüllt sind:

1. die Quellrolle ist `KM`;
2. der Punkt besitzt einen endlichen KM-Wert;
3. der Punkt besitzt verwertbare DB_REF-X/Y-Koordinaten;
4. DB_REF/GK kann in WGS84 umgerechnet werden;
5. Punktstrecke und erwartete Abschnittsstrecke sind beide bekannt und stimmen überein;
6. die getroffene TRA besitzt einen belastbaren gemeinsamen Stationsraum;
7. der Achsabstand ist höchstens `reliable_distance_m` (standardmäßig 5 m).

Ein belastbarer gemeinsamer Stationsraum ist aktuell:

- die primäre TRA selbst (`PRIMARY`), oder
- eine sekundäre TRA mit geometrisch bestätigter Überlappung (`OVERLAP_CONFIRMED`), deren Mapping-Linie bis zur primären TRA ebenfalls belastbar ist.

Eine nur am Endpunkt angeschlossene TRA (`ENDPOINT_CONFIRMED`) darf bereits in den gemeinsamen Stationsraum abgebildet werden, bleibt für automatische Originalanker aber **nicht zuverlässig**. Dort erzeugt ein Treffer höchstens `CONTROL_ANCHOR`, weil am Übergang ein unbekannter Stationssprung möglich wäre.

Eine sekundäre TRA ohne belastbares Mapping wird nicht einmal mit ihrer lokalen Station als Kontrollanker in `tb.track_data_km_points` geschrieben. Lokale Stationsräume dürfen nicht vermischt werden.

Damit werden insbesondere folgende Fälle **nicht** ungeprüft grün:

- Punkt ohne bestätigte Strecke;
- Punkt auf einer nicht zugeordneten sekundären TRA;
- Punkt auf einer nur über Endpunktnähe fortgeschriebenen TRA;
- Punkt mehr als 5 m von der zugeordneten Achse entfernt;
- reine Kontrollquelle;
- GRA-Punkt ohne X/Y-Koordinate.

Ein Punkt mit ausdrücklich abweichender Strecke wird vollständig verworfen und im Projektionsbericht als `skipped_route_mismatch` gezählt.

## 5. GRA bleibt als Koordinatenquelle getrennt

Die derzeit geparsten GRA-Datensätze enthalten KM-/Höhenwerte, aber keine X/Y-Koordinate. Deshalb erzeugt die geometrische Punktprojektion aus GRA allein **keinen** automatischen Originalanker.

Das bleibt absichtlich so: Eine GRA->TRA-Punktzuordnung wird nicht aus Dateinamen, ähnlichen Zahlenfolgen oder bloßer Reihenfolge geraten. GRA wird nur für den separat belegten KM-Sprung-Rebuild verwendet, wenn Achsidentität und geometrisch projizierte KF/GVP-Anker den Sprung zusätzlich bestätigen.

## 6. Rebuild automatische Originalanker

`POST /api/v1/survey-sections/{dataset_id}/rebuild-auto-anchors`

Payload mindestens:

- `base_version`;
- optional `reliable_distance_m`;
- optional `max_distance_m`.

Der Vorgang:

1. lädt die verknüpften Originale und Parsergebnisse;
2. verdichtet alle projektierbaren TRA-Geometrien auf 2 m;
3. bildet belastbare überlappende TRA-Stationsräume auf den Stationsraum der primären Geometrie ab;
4. rechnet Geometrie und Quellpunkte über die bestehende DB_REF-Transformation nach WGS84;
5. projiziert KM-/Kontrollpunkte auf die nächste verknüpfte TRA;
6. überführt die lokale TRA-Station über das Stationsmapping in den gemeinsamen Abschnittsstationsraum;
7. ersetzt alle früher von `survey-projection-v*` erzeugten Punkte;
8. lässt manuelle Anker, KM-Sprünge und andere Generatoren unangetastet;
9. berechnet den Abschnitts-KM-Status neu;
10. erhöht die `track_data.version` optimistisch;
11. schreibt Change Feed, Audit und optional das vorhandene `X-Client-Change-Id`-Receipt.

Die Projektionslogik ist jetzt `survey-projection-v2`. Die automatisch erzeugten UUIDs sind deterministisch aus Projektionsversion, Abschnitt, Quelloriginal, Geometriequelle, Quellpunkt und KM-Wert abgeleitet. Beim v2-Rebuild werden vorhandene v1-Projektionspunkte gezielt ersetzt, damit keine alten und neuen Stationsräume gleichzeitig wirksam bleiben.

## 7. Mehrere TRA-Quellen / Stationsraum-Merge

`survey_station_merge.py` bildet mehrere fachlich kompatible TRA-Geometrien auf einen gemeinsamen Stationsraum ab. Die Originalstationen bleiben unverändert; gespeichert wird nur die abgeleitete lineare Abbildung

`merged_station = station_sign * local_station + station_offset`.

Vor einer Zusammenführung müssen Strecke und zusätzlich Gleis oder Richtung fachlich zusammenpassen. Danach gelten zwei Nachweisstufen:

### `OVERLAP_CONFIRMED` – belastbar

Die sekundäre TRA wird mit bereits gemappten TRA-Geometrien geometrisch verglichen:

- TRA-Basis: dieselben 2-m-Samples wie die Projektion;
- Fit-Raster: etwa 10 m;
- maximaler geometrischer Abstand eines Fit-Treffers: **1,0 m**;
- mindestens **5** Treffer;
- mindestens **50 m** geometrisch belegte Überlappung;
- maximaler Stations-Fit-Restfehler: **0,5 m**;
- Vorwärts- und Rückwärtsrichtung (`station_sign = +1/-1`) werden geprüft.

Ein `OVERLAP_CONFIRMED`-Mapping darf automatische Originalanker liefern, **wenn auch seine Mapping-Vorgeschichte belastbar ist**. Ein späterer Overlap darf also nicht eine zuvor nur geschätzte Endpunktverbindung nachträglich scheinbar zuverlässig machen.

Bei mehreren möglichen Vorgängern wird eine belastbare Überlappung bevorzugt. Damit kann eine spätere TRA direkt an einen zuverlässigen früheren Abschnitt anbinden, auch wenn eine dazwischenliegende Quelle nur schwach oder gar nicht zugeordnet werden konnte.

### `ENDPOINT_CONFIRMED` – nur diagnostisch/Kontrollanker

Wenn keine belastbare Überlappung existiert, darf bei gleicher Achsidentität ein Abstand der benachbarten TRA-Endpunkte bis **5 m** zur Fortführung des Stationsraums verwendet werden. Dabei wird die geometrische Lücke als Stationsfortschritt angesetzt.

Das reicht **nicht** für automatische grüne KM-Anker. Ein unbekannter Stationssprung am Übergang kann ohne Überlappung nicht ausgeschlossen werden. Deshalb besitzt dieses Mapping `reliable_for_auto_anchor=false`.

### Reale Verifikation Harburg -> 8771 / Strecke 5300

Die Logik wurde gegen die hochgeladenen echten TRA-Dateien geprüft:

**R-Achse**

- Harburg: `2026-03-25_5300_050-053_R_ER0.TRA`;
- Folgebestand: `5300_054-059_DHDN_R.Tra`;
- 107 Fit-Treffer;
- belegte Überlappung ca. **1058,226 m**;
- Stationsabbildung: `station_sign=+1`, Offset ca. **+1188,999027 m**;
- maximaler Stations-Restfehler ca. **0,000240 m**.

**S-Achse**

- Harburg: `2025-04-25_5300_050-053_S_ER0.TRA`;
- Folgebestand: `5300_054-059_DHDN_S.Tra`;
- 163 Fit-Treffer;
- belegte Überlappung ca. **1615,748 m**;
- Stationsabbildung: `station_sign=+1`, Offset ca. **-0,035731 m**;
- maximaler Stations-Restfehler ca. **0,000244 m**.

Damit ist belegt, dass echte aufeinanderfolgende Projektbestände zwar geometrisch dieselbe Achse überlappen können, aber nicht zwingend denselben lokalen TRA-Stationsnullpunkt verwenden. Die Abbildung wird deshalb aus Geometrie bestimmt und nicht aus Dateinamen oder KM-Bereichen geraten.

## 8. GRA-KM-Sprünge

Reale Projektbestände zeigen wiederholt eine GRA-Markierung an KM-Sprüngen: zwei aufeinanderfolgende KM-Werte bei praktisch gleicher Höhe, der erste mit Parameter ungefähr `1000`, der folgende mit Parameter `0`. Das wurde u. a. bei 5302, 5300 und 5504 gefunden. Dieses Projektmuster wird **nicht** als allgemeine GRA-Formatspezifikation vorausgesetzt.

Dafür existiert der getrennte Rebuild:

`POST /api/v1/survey-sections/{dataset_id}/rebuild-gra-jumps`

Payload:

- `base_version`;
- optional `allow_one_sided`, Standard **`false`**.

Automatisch erzeugt wird ein `KM_JUMP` nur, wenn Strecke bestätigt ist, zusätzlich Gleis oder Richtung zur primären TRA passt und genügend projizierte `ORIGINAL_ANCHOR` den Sprung geometrisch stützen. Standardmäßig sind Anker **auf beiden Seiten** erforderlich; die daraus unabhängig berechneten Sprungstationen dürfen höchstens 2 m auseinanderliegen.

Einseitige Extrapolation ist nur eine explizite Option und standardmäßig aus. Bei `reverse_geometry=true` der primären Geometrie bricht dieser Rebuild derzeit konservativ ab, statt die Vorher-/Nachher-Semantik zu raten.

Der Rebuild ersetzt ausschließlich frühere von `survey-gra-jump-v*` erzeugte Punkte. Manuelle `KM_JUMP` bleiben unverändert. Ein gleichwertiger manueller Sprung verhindert einen automatisch erzeugten Doppelpunkt.

Die nachfolgende Kalibrierung prüft zusätzlich **alle** beteiligten Anker. Liegt die maximale Ankerabweichung über 5 m, wird nicht grün/orange kalibriert, sondern `ROT/UNPLAUSIBEL` gemeldet. Damit kann ein fehlender KM-Sprung nicht mehr durch einen zufällig plausiblen äußeren 2-Punkt-Fit verdeckt werden.

Reale Verifikation: `docs/TB-SERVER_1_0_SURVEY_REAL_FIXTURE_VERIFICATION.md`.

## 9. Noch offen

- komplette Import->Parse->Abschnitt->Projektion->GRA-Sprung->Kalibrierung-Kette gegen PostgreSQL/API ausführen;
- weitere reale Mehr-TRA-Fälle, insbesondere umgekehrte Geometrierichtung und nicht überlappende Anschlussstücke, gegen das Stationsmapping prüfen;
- einseitig belegte GRA-Sprünge nur bei zusätzlicher realer Evidenz weiter automatisieren;
- Projektion/verdichtete Samples bei Bedarf performant cachen, ohne die unveränderlichen Originale zu verändern;
- Android-Fachclient auf die neuen Server-1.0-Endpunkte umstellen.
