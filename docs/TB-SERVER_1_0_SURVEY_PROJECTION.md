# TB-Server 1.0 – TRA-Verdichtung und Vermessungspunkt-Projektion

Stand: 2026-09-17

Dieser Baustein erzeugt aus den bereits geparsten, unveränderlichen Vermessungsoriginalen neu aufbaubare Ableitungen. Originaldateien und `tb.survey_parse_catalog` werden dabei nicht verändert.

## 1. TRA-Verdichtung

Die serverseitige Rohgeometrie übernimmt bewusst die vorhandene Android-Logik aus `TraDateiParser.kt`:

- Sampleabstand: 2,0 m;
- Gerade: analytisch;
- Kreisbogen: analytisch aus Richtung und Radius;
- Übergangsbogen: numerische Integration mit 0,5-m-Schritt;
- echte TRA-Knoten bleiben exakte Segmentenden;
- unbekannte Elementtypen werden linear zwischen den Knoten interpoliert.

Damit bleibt die Parser-/Rohgeometrie fachlich parallel zu Android, statt eine zweite Interpretation der Originaldatei zu erfinden.

## 2. Kontinuierliche geometrische Chainage

Reale Projektdateien zeigen, dass die lokale TRA-Station nicht immer reine geometrische Weglänge ist. Es gibt Nullweg-Elemente mit `raw_type=6`, bei denen Start- und Endkoordinate praktisch identisch sind, die Station aber um einen diskreten Wert springt. Diese Werte treten in mehreren Beständen auch als KM-Sprünge in den zugehörigen GRA-Daten auf.

Darum existiert zusätzlich `survey_tra_chainage.py`. Diese Schicht verändert **nicht** das Parsergebnis. Sie erzeugt nur eine abgeleitete, kontinuierliche geometrische Station für Projektion und Stationsraum-Merge.

Ein TRA-Element wird nur als belegte Stationsdiskontinuität behandelt, wenn gleichzeitig gilt:

- `raw_type == 6`;
- absoluter Stationssprung mindestens 0,001 m;
- geometrische Sehne zwischen beiden Knoten höchstens 0,01 m;
- Stationssprung und gespeicherte Elementlänge stimmen bis 0,01 m überein.

Dann wird der Sprung aus allen folgenden abgeleiteten Stationen herausgerechnet und das betreffende Element für die geometrische Verdichtung auf Länge 0 gesetzt. Die Rohstation bleibt im Parserkatalog unverändert und wird im abgeleiteten Payload zusätzlich als `source_station_m` nachvollziehbar gehalten.

Die resultierende Basis heißt verbindlich:

`CONTINUOUS_GEOMETRY_CHAINAGE`

Reale lokale Gegenprüfung über den deduplizierten TRA-Bestand:

- 55 unterschiedliche TRA-Dateien nach SHA-256;
- 8 belegte Typ-6-/Nullweg-Sprünge in 4 unterschiedlichen Dateien;
- alle 9 physisch gefundenen Typ-6-Elemente einschließlich einer Dateidublette erfüllten die konservativen Kriterien;
- Beispiele: 5302 `+2,120 m`, `+73,660 m`, `+19,3963 m`, `-2,0221 m`; 5300/8771 `-6,09559 m`; 5504 `+6,351 m`, `-28,948 m`; 5524 `-12,562482 m`.

Die Werte `+2,120`, `+73,660`, `-6,09559`, `+6,351` und `-28,948` sind zusätzlich in realen GRA-Sprungmustern belegt. Die übrigen Typ-6-Werte werden deshalb nicht als allgemeine GRA-Regel behauptet, sondern nur als durch die TRA selbst belegte Quellstationsdiskontinuität behandelt.

## 3. DB_REF/GK -> WGS84

`survey_geometry.py` portiert die bestehende Android-Umrechnung aus `DbRefKoordinatenwandler.kt` einschließlich der DB_REF->ETRS89-Helmertparameter.

Unterstützt werden die aus dem Rechtswert erkannten GK-Streifen 3, 4 und 5. Damit können insbesondere vorhandene rohe KF-Punkte im Streifen 3 mit TRA-Geometrien verglichen werden, ohne ihre ursprünglichen DB_REF-Koordinaten zu verändern.

WGS84 ist hier ausschließlich eine abgeleitete Rechenkoordinate. Die Originalkoordinaten bleiben im Parsergebnis erhalten.

## 4. Punkt -> TRA-Projektion

Der Server projiziert koordinatenhaltige Punkte aus verknüpften Quellen der Rollen `KM` und `CONTROL` auf die verdichteten TRA-Samples der **kontinuierlichen geometrischen Chainage**.

Die Abstandsqualität folgt der vorhandenen Android-Regel:

- bis 2 m: `GRUEN`;
- über 2 bis 5 m: `GELB`;
- über 5 bis 15 m: `ORANGE`;
- über 15 m: `ROT` bzw. standardmäßig nicht übernommen.

Die Standardgrenzen des Rebuild-Endpunkts sind daher:

- `reliable_distance_m = 5.0`;
- `max_distance_m = 15.0`.

Beide Werte werden im erzeugten Punktpayload dokumentiert.

## 5. Wann wird ein automatischer Originalanker erzeugt?

Ein projizierter Punkt wird nur dann `ORIGINAL_ANCHOR`, wenn **alle** folgenden Bedingungen erfüllt sind:

1. die Quellrolle ist `KM`;
2. der Punkt besitzt einen endlichen KM-Wert;
3. der Punkt besitzt verwertbare DB_REF-X/Y-Koordinaten;
4. DB_REF/GK kann in WGS84 umgerechnet werden;
5. Punktstrecke und erwartete Abschnittsstrecke sind beide bekannt und stimmen überein;
6. die getroffene TRA besitzt einen belastbaren gemeinsamen Stationsraum auf `CONTINUOUS_GEOMETRY_CHAINAGE`;
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

## 6. GRA bleibt als Koordinatenquelle getrennt

Die derzeit geparsten GRA-Datensätze enthalten KM-/Höhenwerte, aber keine X/Y-Koordinate. Deshalb erzeugt die geometrische Punktprojektion aus GRA allein **keinen** automatischen Originalanker.

Das bleibt absichtlich so: Eine GRA->TRA-Punktzuordnung wird nicht aus Dateinamen, ähnlichen Zahlenfolgen oder bloßer Reihenfolge geraten. GRA wird nur für den separat belegten KM-Sprung-Rebuild verwendet, wenn Achsidentität und geometrisch projizierte KF/GVP-Anker den Sprung zusätzlich bestätigen.

## 7. Rebuild automatische Originalanker

`POST /api/v1/survey-sections/{dataset_id}/rebuild-auto-anchors`

Payload mindestens:

- `base_version`;
- optional `reliable_distance_m`;
- optional `max_distance_m`.

Der Vorgang:

1. lädt die verknüpften Originale und Parsergebnisse;
2. normalisiert nur für die Rechenschicht belegte TRA-Quellstationsdiskontinuitäten zu `CONTINUOUS_GEOMETRY_CHAINAGE`;
3. verdichtet alle projektierbaren TRA-Geometrien auf dieser Chainage auf 2 m;
4. bildet belastbare überlappende TRA-Stationsräume auf den Stationsraum der primären Geometrie ab;
5. rechnet Geometrie und Quellpunkte über die bestehende DB_REF-Transformation nach WGS84;
6. projiziert KM-/Kontrollpunkte auf die nächste verknüpfte TRA;
7. überführt die lokale geometrische Chainage über das Stationsmapping in den gemeinsamen Abschnittsstationsraum;
8. ersetzt alle früher von `survey-projection-v*` erzeugten Punkte;
9. verwirft zusätzlich automatisch erzeugte `survey-gra-jump-v*`-Punkte, weil deren Sprungstation vom bisherigen Projektions-/Stationsraum abhängt;
10. erhält manuelle Anker, manuelle KM-Sprünge und andere unabhängige Fachpunkte unverändert;
11. berechnet den Abschnitts-KM-Status mit dem jetzt gültigen Punktbestand neu;
12. erhöht die `track_data.version` optimistisch;
13. schreibt Change Feed, Audit und optional das vorhandene `X-Client-Change-Id`-Receipt.

Die Projektionslogik läuft jetzt als **`survey-projection-v3`**. Die automatisch erzeugten UUIDs sind deterministisch aus Projektionsversion, Abschnitt, Quelloriginal, Geometriequelle, Quellpunkt und KM-Wert abgeleitet. Beim v3-Rebuild werden vorhandene v1-/v2-Projektionspunkte gezielt ersetzt, damit keine alten und neuen Stationsbasen gleichzeitig wirksam bleiben.

Erzeugte Punktpayloads dokumentieren ausdrücklich `geometry_station_basis=CONTINUOUS_GEOMETRY_CHAINAGE`, lokale geometrische Chainage, gemappte Abschnittsstation und den Status/Offset des Stationsmappings.

### Abhängigkeit zum GRA-Sprung-Rebuild

Ein automatisch erzeugter GRA-Sprung ist eine **abgeleitete Station**. Ändert ein erneuter Projektionslauf die TRA-Zuordnung, die Chainage, den Stationsoffset oder die Originalanker, ist die alte automatisch berechnete Sprungstation nicht mehr sicher gültig. Darum wird sie beim Projektions-Rebuild bewusst entfernt.

Verbindliche sichere Reihenfolge nach Änderungen an Geometrie/Quellen/Projektion:

1. `rebuild-auto-anchors`;
2. danach `rebuild-gra-jumps`;
3. anschließend den im Section-Payload neu berechneten Kalibrierstatus verwenden.

Zwischen Schritt 1 und 2 darf ein Abschnitt mit einem benötigten, aber noch nicht neu erzeugten GRA-Sprung vorübergehend `ROT/UNPLAUSIBEL` sein. Das ist beabsichtigt und verhindert, dass ein veralteter automatisch berechneter Sprung scheinbar einen grünen Zustand erhält. Manuell gesetzte `KM_JUMP` werden durch diesen Ablauf nicht gelöscht.

## 8. Mehrere TRA-Quellen / Stationsraum-Merge v2

`survey_station_merge.py` bildet mehrere fachlich kompatible TRA-Geometrien auf einen gemeinsamen Stationsraum ab. Seit `survey-station-merge-v2` wird dafür nicht mehr die möglicherweise diskontinuierliche Quellstation, sondern die abgeleitete `CONTINUOUS_GEOMETRY_CHAINAGE` verwendet.

Die Originalstationen bleiben unverändert. Gespeichert wird nur die abgeleitete lineare Abbildung

`merged_station = station_sign * local_chainage + station_offset`.

Vor einer Zusammenführung müssen Strecke und zusätzlich Gleis oder Richtung fachlich zusammenpassen. Danach gelten zwei Nachweisstufen:

### `OVERLAP_CONFIRMED` – belastbar

Die sekundäre TRA wird mit bereits gemappten TRA-Geometrien geometrisch verglichen:

- TRA-Basis: kontinuierliche 2-m-Chainage-Samples;
- Fit-Raster: etwa 10 m;
- maximaler geometrischer Abstand eines Fit-Treffers: **1,0 m**;
- mindestens **5** Treffer;
- mindestens **50 m** geometrisch belegte Überlappung;
- maximaler Stations-Fit-Restfehler: **0,5 m**;
- Vorwärts- und Rückwärtsrichtung (`station_sign = +1/-1`) werden geprüft.

Ein `OVERLAP_CONFIRMED`-Mapping darf automatische Originalanker liefern, **wenn auch seine Mapping-Vorgeschichte belastbar ist**. Ein späterer Overlap darf also nicht eine zuvor nur geschätzte Endpunktverbindung nachträglich scheinbar zuverlässig machen.

Bei mehreren möglichen Vorgängern wird eine belastbare Überlappung bevorzugt. Damit kann eine spätere TRA direkt an einen zuverlässigen früheren Abschnitt anbinden, auch wenn eine dazwischenliegende Quelle nur schwach oder gar nicht zugeordnet werden konnte.

Jedes Mapping dokumentiert zusätzlich die Anzahl der in seiner Quelle erkannten Stationsdiskontinuitäten.

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

Der im 8771-S-Bestand vorhandene `-6,09559-m`-Typ-6-Sprung liegt außerhalb dieser Harburg-Überlappung. Die belegte Überlappung bleibt deshalb numerisch identisch stabil; nach dem Sprung arbeitet die v2-Schicht jedoch auf der kontinuierlichen geometrischen Chainage statt auf der springenden Quellstation.

Damit ist belegt, dass echte aufeinanderfolgende Projektbestände zwar geometrisch dieselbe Achse überlappen können, aber nicht zwingend denselben lokalen TRA-Stationsnullpunkt verwenden. Die Abbildung wird deshalb aus Geometrie bestimmt und nicht aus Dateinamen oder KM-Bereichen geraten.

## 9. GRA-KM-Sprünge v2

Reale Projektbestände zeigen wiederholt eine GRA-Markierung an KM-Sprüngen: zwei aufeinanderfolgende KM-Werte bei praktisch gleicher Höhe, der erste mit Parameter ungefähr `1000`, der folgende mit Parameter `0`. Das wurde u. a. bei 5302, 5300 und 5504 gefunden. Dieses Projektmuster wird **nicht** als allgemeine GRA-Formatspezifikation vorausgesetzt.

Dafür existiert der getrennte Rebuild:

`POST /api/v1/survey-sections/{dataset_id}/rebuild-gra-jumps`

Payload:

- `base_version`;
- optional `allow_one_sided`, Standard **`false`**.

Seit **`survey-gra-jump-v2`** ist zusätzlich zwingend erforderlich:

- im Section-Payload liegt eine aktuelle `survey-projection-v3` vor;
- deren `station_basis` ist `CONTINUOUS_GEOMETRY_CHAINAGE`;
- das aktuelle Stationsmapping enthält die primäre Geometrie und einen gültigen gemappten Stationsbereich.

Damit wird verhindert, dass ein KM-Sprung noch gegen die alte diskontinuierliche TRA-Quellstation oder gegen eine veraltete Projektion berechnet wird.

Automatisch erzeugt wird ein `KM_JUMP` nur, wenn Strecke bestätigt ist, zusätzlich Gleis oder Richtung zur primären TRA passt und genügend projizierte `ORIGINAL_ANCHOR` den Sprung geometrisch stützen. Standardmäßig sind Anker **auf beiden Seiten** erforderlich; die daraus unabhängig berechneten Sprungstationen dürfen höchstens 2 m auseinanderliegen.

Einseitige Extrapolation ist nur eine explizite Option und standardmäßig aus. Bei `reverse_geometry=true` der primären Geometrie bricht dieser Rebuild derzeit konservativ ab, statt die Vorher-/Nachher-Semantik zu raten.

Der GRA-Rebuild ersetzt ausschließlich frühere von `survey-gra-jump-v*` erzeugte Punkte. Manuelle `KM_JUMP` bleiben unverändert. Ein gleichwertiger manueller Sprung verhindert einen automatisch erzeugten Doppelpunkt.

Die nachfolgende Kalibrierung prüft zusätzlich **alle** beteiligten Anker. Liegt die maximale Ankerabweichung über 5 m, wird nicht grün/orange kalibriert, sondern `ROT/UNPLAUSIBEL` gemeldet. Damit kann ein fehlender KM-Sprung nicht mehr durch einen zufällig plausiblen äußeren 2-Punkt-Fit verdeckt werden.

Reale Verifikation: `docs/TB-SERVER_1_0_SURVEY_REAL_FIXTURE_VERIFICATION.md`.

## 10. Tests / Sicherheitsregeln

`server/checks/32_verify_tb_server_1_survey_projection_source.py` enthält zusätzlich zu den bisherigen Geometrie-, Projektions-, Multi-TRA- und GRA-Regressionen jetzt destillierte Tests für:

- positiven Nullweg-TRA-Sprung `+73,660 m`;
- negativen Nullweg-TRA-Sprung `-6,09559 m`;
- keine künstlichen 2-m-Samples auf einem Sprungelement;
- `survey-projection-v3` + `CONTINUOUS_GEOMETRY_CHAINAGE`;
- `survey-station-merge-v2`;
- `survey-gra-jump-v2` mit Pflicht auf aktuelle Projektion/Chainage;
- Invalidierung alter Projektions- und automatisch erzeugter GRA-Ableitungen bei Projektions-Rebuild.

Die letzten GitHub-Actions-Läufe konnten diese Tests weiterhin **nicht ausführen**: der Job endet vor dem ersten Step ohne Runner (`steps=[]`, `runner_id=0`). Das ist ein CI-Infrastrukturproblem und kein festgestellter fachlicher Server-Testfehler.

## 11. Noch offen

- komplette Import->Parse->Abschnitt->Projektion v3->GRA-Sprung v2->Kalibrierung-Kette gegen PostgreSQL/API ausführen;
- weitere reale Mehr-TRA-Fälle, insbesondere umgekehrte Geometrierichtung, rückläufige Stationierung und nicht überlappende Anschlussstücke, gegen das Stationsmapping prüfen;
- einseitig belegte GRA-Sprünge nur bei zusätzlicher realer Evidenz weiter automatisieren;
- Projektion/verdichtete Samples bei Bedarf performant cachen, ohne die unveränderlichen Originale zu verändern;
- Android-Fachclient auf die neuen Server-1.0-Endpunkte umstellen.