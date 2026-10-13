# TB-Server 1.0 – Vermessungsparser: Verifikation mit realen Projektdateien

Stand: 2026-09-17

Dieses Dokument hält die Verifikation des serverseitigen Vermessungsparsers gegen vorhandene reale TB-Helfer-Projektdateien fest. Die Testdateien selbst werden **nicht** in GitHub übernommen. Originaldateien bleiben unverändert; geprüft wird nur lesend gegen lokale Kopien aus der Projekt-/Library-Ablage.

## 1. Ziel

Vor der Abschnittszusammenführung/KM-Kalibrierung muss der Serverparser nicht nur synthetische Testfälle, sondern die tatsächlich im Projekt vorkommenden Formate lesen. Dabei gilt weiterhin:

- Android-Parser sind die fachliche Referenz für bereits unterstützte Formate;
- unbekannte Formate werden nicht heuristisch erfunden;
- Vermessungsoriginale werden durch Parsing niemals verändert;
- Parserdaten sind abgeleitet und wiederaufbaubar;
- TRA-2-m-Verdichtung und DB_REF→WGS84 bleiben getrennte abgeleitete Rechenschritte.

## 2. Verwendeter realer Datenbestand

Geprüft wurde der vorhandene Projektbestand aus `Absteckunggesammt.zip` sowie zusätzlich die rohe KF-Datei `5362_km70,6-81,5_PS1-PS3_20260619_V2.kf`.

Aus verschachtelten ZIPs wurden für die Prüfung identische Dateien per SHA-256 dedupliziert. Dadurch wurden keine mehrfach enthaltenen Archivkopien mehrfach als eigenständige Formate gezählt.

Einzigartige relevante Testdateien:

- 55 × TRA
- 45 × GRA
- 4 × rohe KF innerhalb des Gesamtarchivs
- 1 × KV
- 2 × KF.TXT
- 1 × GVP-Text
- 2 × KOR.TXT
- 2 × rohe/unbekannte KOR
- zusätzlich 1 × rohe KF Strecke 5362 außerhalb des Gesamtarchivs

Die Dateien decken u. a. Strecken-, Bahnhofsgleis-, Weichen-/Weichenverbindungs- und Festpunktdaten aus mehreren vorhandenen Projektbeständen ab.

## 3. Ergebnis

Alle unterstützten eindeutigen Dateien konnten mit dem Serverparser ohne Parserfehler gelesen werden:

| Format | Ergebnis |
| --- | --- |
| TRA | 55/55 als `TRA_ER0_BINARY` gelesen |
| GRA | 45/45 als `GRA_BINARY` gelesen |
| rohe KF im Gesamtarchiv | 4/4 als `KF_7_BINARY` gelesen |
| zusätzliche rohe KF 5362 | als `KF_7_BINARY` gelesen |
| KV | 1/1 als `KV_TEXT` gelesen |
| KF.TXT | 2/2 als `KF_TEXT` gelesen |
| GVP-Text | 1/1 als `GVP_TEXT` gelesen |
| KOR.TXT | 2/2 als `KOR_TEXT` gelesen |
| rohe/unbekannte KOR | 2/2 bewusst `UNSUPPORTED` (`KOR_RAW_NO_TEXT_PARSER`) |

Die zusätzliche rohe KF Strecke 5362 enthält 75 verwertbare Festpunkte. Alle 75 besitzen eine plausible Station; die Punktarten umfassen PS0, PS1, PS2 und PS3. Die Rechtswerte liegen im DB_REF/GK-Streifen 3 und werden deshalb korrekt als `DB_REF_GK_3` erhalten; es wird weiterhin keine erfundene GPS-Transformation angewendet.

## 4. Gefundene reale Abweichung: Datum im Dateinamen

Die reale Dateisammlung hat eine Schwäche des bisherigen Server-Parsers sichtbar gemacht: Dateinamen wie

- `2025-04-25_MHRG_Gleis_10_...`
- `2026-03-25_5300_050-053_R_...`

wurden bei der einfachen Streckennummernsuche teilweise fälschlich mit `2025` bzw. `2026` als Strecke klassifiziert.

In 18 eindeutigen realen GRA-Dateinamen wurde dadurch die Streckenmetadaten-Erkennung korrigiert.

Parser v2 maskiert deshalb Datumsbestandteile `YYYY-MM-DD`, `YYYY_MM_DD` und `YYYY.MM.DD` vor der Streckensuche und übernimmt zusätzlich die bereits in Android vorhandenen Dateinamenregeln für:

- Strecke;
- Bahnhofsgleis/Gleis;
- Quellrichtung S/L/SL/R;
- interne Richtung 0/1/2;
- Achstyp Strecke/Gleis/Weiche/Weichenverbindung;
- vorhandene Sonderzuordnung `5302L -> Gleis 302` und `5302R -> Gleis 303`;
- `km5300` als belegten Streckenhinweis, ohne ein vorangestelltes Datum als Strecke zu missverstehen.

Die Metadaten werden additiv in den TRA-/GRA-Parse-Payload geschrieben. Die binäre Interpretation der Originaldatei selbst wird dadurch nicht verändert.

## 5. Tests

Lokal ausgeführt und erfolgreich:

- Python-Syntaxprüfung des Parser-v2-Standes;
- bestehende vier Raw-KF-Paritätsfälle;
- zusätzliche Dateinamen-Metadatentests für datumspräfixierte Dateien;
- Parse-Lauf über alle oben aufgeführten eindeutigen realen Projektdateien.

Das bekannte GitHub-Actions-/Runnerproblem besteht unabhängig davon weiter: die letzten Server-Workflows wurden vor dem ersten Step beendet. Die lokale Verifikation darf deshalb nicht als ausgeführter GitHub-Actions-/PostgreSQL-Test bezeichnet werden.

## 6. Folgerung / nächster Schritt

Der Parserblock ist für die derzeit belegten realen Formate ausreichend verifiziert. Rohe unbekannte KOR-Dateien bleiben bewusst unentschlüsselt und unverändert als Original erhalten.

Nächster fachlicher Block ist die **Abschnittszusammenführung/KM-Kalibrierung** auf Basis der getrennt gespeicherten Parse-Daten. Dabei müssen belastbare Original-KM-Quellen, manuell kalibrierte Abschnitte und nicht kalibrierbare Bereiche klar getrennt bleiben.
