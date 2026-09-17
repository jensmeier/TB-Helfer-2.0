# TB-Server 1.0 – reale Vermessungsdaten: Projektion/KM-Verifikation

Stand: 2026-09-17

Diese Prüfung verwendet die im Projekt bereitgestellten realen Absteckungs-/Vermessungsbestände. Die Originaldateien wurden nur gelesen; es wurden keine Originale verändert oder in GitHub übernommen.

## Ziel

Nach Parser-v2, TRA-2-m-Verdichtung und DB_REF/GK->WGS84-Projektion wurde geprüft, ob echte KF/GVP-Punkte geometrisch belastbar auf die vorhandenen TRA-Achsen fallen und ob die daraus gebildete KM-Kalibrierung fachlich konsistent bleibt.

Wichtig: Ein äußerer 2-Punkt-Fit allein reicht nicht. Reale Daten enthalten KM-Sprünge. Eine scheinbar plausible Gesamtsteigung kann deshalb trotzdem zwischen den Ankern um viele Meter falsch sein.

## Gessertshausen 5302

Verwendete reale Dateien u. a.:

- `250919_5302L_ER0.TRA`
- `250919_5302R_ER0.TRA`
- `250919_5302L_O00_kmLinie.GRA`
- `250919_5302R_O00_kmLinie.GRA`
- `GVP_5302_15,0-18,0_DBREF2016_4.Streifen.kf`

Die rohe KF 7.0 enthält 124 verwertbare Punkte mit Strecke 5302 und KM-Wert.

### Projektion auf 5302L

Mit der serverseitig übernommenen 2-m-TRA-Verdichtung und derselben DB_REF/GK->WGS84-Transformation wie Android liegen **46** GVP-Punkte innerhalb 5 m der Achse `250919_5302L_ER0.TRA`.

- Median Achsabstand: ca. **3,566 m**
- größter Abstand dieser 46 Treffer: ca. **4,372 m**
- die lokale DB_REF-Direktprojektion und die WGS84-Projektion unterscheiden sich bei der Station in dieser Prüfung um höchstens ca. **0,029 m**.

Die GRA enthält ein reales KM-Sprungpaar:

- vor Sprung: **17626,340 m**
- nach Sprung: **17700,000 m**
- Sprung: **+73,660 m**

Aus den geometrisch projizierten KF/GVP-Ankern ergibt sich der Sprung auf der TRA bei ungefähr **Station 12986,913 m**. Die unabhängig von der linken und rechten Nachbarschaft berechneten Stationswerte unterscheiden sich nur um ca. **0,001 m**.

Mit modelliertem Sprung ergibt die gesamte Ankerreihe:

- kontinuierlicher Faktor ca. **0,999263**
- maximale Ankerabweichung ca. **0,549 m**.

Ohne KM-Sprung kann ein äußerer 2-Punkt-Fit weiterhin formal im bisherigen Plausibilitätsbereich 0,5..1,5 liegen, während Zwischenanker um mehr als 60 m falsch werden. Deshalb darf dieser Fall nicht grün werden.

### Projektion auf 5302R

Auf `250919_5302R_ER0.TRA` liegen **45** GVP-Punkte innerhalb 5 m.

- Median Achsabstand: ca. **3,489 m**
- größter Abstand dieser 45 Treffer: ca. **3,963 m**
- derselbe GRA-KM-Sprung `17626,340 -> 17700,000` wird bei ungefähr **Station 13025,899 m** verortet;
- die beidseitigen Stationsschätzungen unterscheiden sich nur um ca. **0,001 m**;
- kontinuierlicher Faktor mit Sprung: ca. **1,000528**;
- maximale Ankerabweichung: ca. **0,396 m**.

Damit bestätigt der reale Bestand sowohl die 5-m-Zuverlässigkeitsgrenze für diese GVP-Punkte als auch die Notwendigkeit, KM-Sprünge separat vom kontinuierlichen Faktor zu behandeln.

## Strecke 5300 – Bestand 8771

Verwendete reale Dateien u. a.:

- `5300_054-059_DHDN_R.Tra`
- `5300_054-059_DHDN_S.Tra`
- `5300_054-059_DHDN_R.Gra`
- `Festpkt_5300_054-059_DHDN.kf`

Die rohe KF enthält 120 Punkte mit KM-Werten. In der geometrischen Gegenprüfung liegen 119/120 Punkte innerhalb 5 m einer der beiden verknüpften TRA-Achsen.

Für die R-Achse zeigt die GRA ein weiteres reales Sprungpaar:

- vor Sprung: **55606,095653 m**
- nach Sprung: **55600,000063 m**
- Sprung: ca. **-6,095590 m**.

Aus den projizierten GVP-Ankern wird die Sprungstation bei ca. **4305,411 m** geschätzt. Die Schätzung aus beiden Seiten unterscheidet sich um ca. **0,403 m**. Mit diesem Sprung liegt die maximale Ankerabweichung bei ca. **0,517 m**.

Das bestätigt, dass reale KM-Sprünge nicht nur positiv und groß sein können; auch negative Sprünge im einstelligen Meterbereich kommen im vorhandenen Projektbestand vor.

## Harburg 5300

Die rohe Datei `5300 km 51-53 GE+WE Harburg_DBREF2016.kf` enthält 86 plausible Punkte und Strecke 5300, aber die KM-/Stationsspalte ist vollständig `0.00000`. Der vorhandene Parser verwirft diese Nullstationen daher zu Recht als KM-Quelle.

Folge: Aus dieser KF allein darf keine automatische grüne KM-Kalibrierung erzeugt werden. Die vorhandenen TRA/GRA-Daten müssen für diesen Bestand separat fachlich zusammengeführt werden; eine KM-Zuordnung darf nicht aus Dateinamen geraten werden.

## Wiederkehrendes GRA-Muster bei KM-Sprüngen

In mehreren realen GRA-Dateien tritt an Sprungstellen dasselbe auffällige Muster auf:

- zwei aufeinanderfolgende KM-Werte mit identischer Höhe;
- erster Datensatz mit Parameterwert ungefähr `1000`;
- unmittelbar folgender Datensatz mit Parameterwert `0`;
- die KM-Differenz entspricht dem beobachteten Sprung.

Beispiele im vorhandenen Bestand:

- 5302 L/R: `14897,880 -> 14900,000` (+2,120 m) und `17626,340 -> 17700,000` (+73,660 m);
- 5300 DHDN R: `55606,095653 -> 55600,000063` (-6,095590 m);
- 5504: u. a. `5793,647999 -> 5799,998999` (+6,351 m) und `9428,9482 -> 9400,0000` (-28,9482 m).

Dieses Muster ist durch mehrere reale Projektdateien belegt, wird aber nicht als allgemeine externe GRA-Formatspezifikation behauptet. Eine automatische Verwendung wird deshalb zusätzlich durch Achsidentität und die geometrisch projizierte Ankerreihe abgesichert.

## Konsequenz für Server 1.0

Die KM-Kalibrierung wurde verschärft:

- `GRUEN/AUTOMATISCH` oder `ORANGE/MANUELL` darf nicht mehr nur aus dem äußersten Ankerpaar entstehen;
- alle beteiligten Anker werden gegen die resultierende Kalibrierung geprüft;
- maximale zulässige Ankerabweichung: **5 m**;
- bei größerer Abweichung wird der Abschnitt `ROT/UNPLAUSIBEL` und weist auf einen möglichen nicht modellierten KM-Sprung oder eine falsche Achszuordnung hin;
- ein korrekt modellierter KM-Sprung wird diskret berücksichtigt und nicht in den kontinuierlichen Faktor hineingerechnet.

Der Source-Check `31_verify_tb_server_1_survey_sections_source.py` enthält einen aus dem realen Gessertshausen-5302L-Bestand destillierten Regressionstest: ohne den 73,660-m-Sprung rot/unplausibel, mit Sprung grün und <1 m maximale Ankerabweichung.

## Automatische, konservative GRA-Sprungerkennung

`server/tbserver/survey_gra_jumps.py` ergänzt nun den getrennten Rebuild:

`POST /api/v1/survey-sections/{dataset_id}/rebuild-gra-jumps`

Der Rebuild darf einen GRA-Kandidaten nur als erzeugten `KM_JUMP` übernehmen, wenn:

1. die GRA als `KM`-Quelle verknüpft und erfolgreich als `GRA_BINARY` geparst ist;
2. das in den realen Dateien wiederholt beobachtete Muster `Parameter 1000 -> 0` bei praktisch gleicher Höhe vorliegt;
3. Strecke bestätigt ist;
4. zusätzlich Gleis **oder** Richtung zwischen GRA und primärer TRA bestätigt ist;
5. genügend bereits geometrisch projizierte `ORIGINAL_ANCHOR` vorhanden sind;
6. die Sprungstation unabhängig aus Ankern vor und nach dem Sprung bestimmbar ist;
7. beide Stationsschätzungen höchstens **2 m** auseinanderliegen.

Einseitige Extrapolation ist technisch vorhanden, aber standardmäßig **ausgeschaltet** (`allow_one_sided=false`). Damit wird z. B. der frühe 5302-Sprung `14897,880 -> 14900,000`, für den im vorhandenen KF-Bereich keine Anker auf beiden Seiten liegen, beim normalen Rebuild nur als nicht platzierter Kandidat geführt und nicht automatisch übernommen.

Reale Gegenprüfung des neuen Builders mit `allow_one_sided=false`:

- Gessertshausen 5302L: 2 GRA-Kandidaten, davon 1 beidseitig bestätigt; erzeugt `+73,660 m` bei Station ca. `12986,913`, Seitenabweichung ca. `0,001 m`;
- Gessertshausen 5302R: 2 Kandidaten, davon 1 bestätigt; erzeugt `+73,660 m` bei Station ca. `13025,898`, Seitenabweichung < `0,001 m`;
- 8771 / 5300 R: 1 Kandidat, bestätigt; erzeugt `-6,095590 m` bei Station ca. `4305,411`, Seitenabweichung ca. `0,403 m`.

Der Rebuild ersetzt nur zuvor von `survey-gra-jump-v*` erzeugte Punkte. Manuelle KM-Sprünge bleiben erhalten und verhindern einen gleichwertigen automatisch erzeugten Doppelpunkt. Bei umgekehrter primärer Geometrie wird derzeit bewusst mit `GRA_JUMP_REVERSED_PRIMARY_GEOMETRY_NOT_SUPPORTED` abgebrochen, statt Vorher/Nachher zu raten.

## Noch offen

1. Die reale Kette Import -> Parse -> Survey-Section -> Punktprojektion -> GRA-Sprung-Rebuild -> Kalibrierung vollständig gegen PostgreSQL/API ausführen.
2. Einseitig belegte Sprünge nur dann weiter automatisieren, wenn zusätzliche reale Bestände einen sicheren Nachweis erlauben; Standard bleibt vorerst `false`.
3. Robuste Stationsraum-/Überlappungsverschmelzung mehrerer TRA-Quellen schließen.
4. GitHub Actions ist dafür derzeit weiterhin nicht verwendbar, weil der TB-Server-Job schon vor dem ersten Step ohne Runner endet.
