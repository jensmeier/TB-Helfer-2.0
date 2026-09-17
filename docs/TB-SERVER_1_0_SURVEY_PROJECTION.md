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
6. die Projektion trifft die primäre TRA-Geometrie des Abschnitts;
7. der Achsabstand ist höchstens `reliable_distance_m` (standardmäßig 5 m).

Treffer innerhalb `max_distance_m`, die diese strengen Bedingungen nicht vollständig erfüllen, werden nur als `CONTROL_ANCHOR` gespeichert und beeinflussen die automatische grüne KM-Kalibrierung nicht.

Damit werden insbesondere folgende Fälle **nicht** ungeprüft grün:

- Punkt ohne bestätigte Strecke;
- Punkt auf einer sekundären, noch nicht robust stationsmäßig verschmolzenen TRA-Quelle;
- Punkt mehr als 5 m von der primären Achse entfernt;
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
3. rechnet Geometrie und Quellpunkte über die bestehende DB_REF-Transformation nach WGS84;
4. projiziert KM-/Kontrollpunkte auf die nächste verknüpfte TRA;
5. ersetzt ausschließlich Punkte, die zuvor von `survey-projection-v1` erzeugt wurden;
6. lässt manuelle Anker, KM-Sprünge und andere nicht von diesem Rebuild erzeugte Punkte unangetastet;
7. berechnet den Abschnitts-KM-Status neu;
8. erhöht die `track_data.version` optimistisch;
9. schreibt Change Feed, Audit und optional das vorhandene `X-Client-Change-Id`-Receipt.

Die automatisch erzeugten UUIDs sind deterministisch aus Abschnitt, Quelloriginal, Geometriequelle, Quellpunkt und KM-Wert abgeleitet.

## 7. Mehrere TRA-Quellen

Mehrere TRA-Quellen werden für die Suche bereits berücksichtigt. Wegen der noch offenen robusten Stationsraum-/Überlappungsverschmelzung darf jedoch nur ein zuverlässiger Treffer auf der **primären** Geometrie automatisch `ORIGINAL_ANCHOR` werden.

Treffer auf sekundären Geometrien bleiben `CONTROL_ANCHOR`. Erst wenn die Stationsräume mehrerer TRA-Quellen belastbar zusammengeführt sind, darf diese Schranke erweitert werden.

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
- robuste Stationsraum- und Überlappungsverschmelzung mehrerer TRA-Quellen;
- einseitig belegte GRA-Sprünge nur bei zusätzlicher realer Evidenz weiter automatisieren;
- Projektion/verdichtete Samples bei Bedarf performant cachen, ohne die Originale zu verändern;
- Android-Fachclient auf die neuen Server-1.0-Endpunkte umstellen.
