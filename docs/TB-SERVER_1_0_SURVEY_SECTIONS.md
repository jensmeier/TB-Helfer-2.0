# TB-Server 1.0 – Vermessungsabschnitte und KM-Kalibrierung

Stand: 2026-09-17

Diese Datei dokumentiert die serverseitige Ableitung für zusammengeführte Vermessungsabschnitte. Maßgeblich bleiben die unveränderlichen Vermessungsoriginale und der Parse-Katalog; diese Ableitung darf jederzeit neu erzeugt werden.

## Grundsatz

Originale `TRA`, `GRA`, `KF`, `KV`, `GVP`, `KOR` usw. werden niemals überschrieben. Ein fachlicher Vermessungsabschnitt liegt als `tb.track_data` mit `dataset_kind=SURVEY_SECTION` vor und verweist über `tb.track_data_sources` auf ein oder mehrere Originale.

Die neue Ableitung ist bewusst von den Originaldateien getrennt:

- `tb.survey_originals`: unveränderliche Datei und SHA-256;
- `tb.survey_parse_catalog`: neu aufbaubares Parsergebnis;
- `tb.track_data`: versionierter/bearbeitbarer fachlicher Abschnitt;
- `tb.track_data_sources`: mehrere Originalquellen je Abschnitt;
- `tb.track_data_km_points`: KM-Anker, Kontrollanker und KM-Sprünge.

`track_data.source_original_id` bleibt als Kompatibilitätszeiger auf die erste Geometriequelle erhalten. Die vollständige Quellenmenge steht ausschließlich in `tb.track_data_sources`.

## Quellenrollen

Unterstützte Rollen:

- `GEOMETRY`: TRA-Geometrie; muss im Parse-Katalog erfolgreich als `TRA_ER0_BINARY` vorliegen;
- `KM`: KM-/Stationsquelle, z. B. GRA/KF/GVP nach erfolgreichem Parserlauf;
- `HEIGHT`: Höhenquelle;
- `CONTROL`: Kontroll-/Festpunktquelle;
- `OTHER`: zusätzliche Originalquelle ohne automatische fachliche Interpretation.

Mehrere `GEOMETRY`-Quellen können in einer festen Reihenfolge verknüpft werden. Für jede Quelle kann `reverse_geometry=true` gesetzt werden, ohne das Original zu verändern.

## Geometrie-Zusammenführung

Der Stand 1.0 prüft bei mehreren TRA-Quellen die Endpunkte in der angegebenen Reihenfolge. Die Lücke zwischen dem Ende der einen und dem Anfang der nächsten Quelle wird in DB_REF berechnet:

- bis 5 m: `CONNECTED`;
- über 5 m bis 20 m: `WARNING`;
- über 20 m: `OPEN`;
- fehlende auswertbare Endpunkte: `UNVERIFIED`.

Das ist ein Verbindungs-/Diagnoseschritt, noch keine vollständige geometrische Verschmelzung. Überlappungen, konkurrierende TRA-Stände und die spätere 2-m-Verdichtung werden in einem eigenen Ableitungsschritt behandelt.

## KM-Punkte

`tb.track_data_km_points` kennt:

- `ORIGINAL_ANCHOR`: belastbarer, bereits auf eine TRA-Station projizierter Original-/Absteckungs-KM;
- `MANUAL_ANCHOR`: vom Benutzer gesetzter KM-Anker;
- `CONTROL_ANCHOR`: Kontrollpunkt, der nicht automatisch die Hauptkalibrierung bestimmt;
- `KM_JUMP`: diskreter KM-Sprung mit KM vor und nach dem Sprung.

Wichtig: Ein `ORIGINAL_ANCHOR` ist erst dann zulässig, wenn die Zuordnung zur TRA-Station fachlich/geometrisch bereits bestimmt ist. Der aktuelle Stand rät **nicht** aus Dateinamen oder ähnlichen Stationsnummern eine GRA/KF/GVP→TRA-Zuordnung. Diese Projektion ist der nächste Rechenschritt.

## Farb-/Statusregel

Die langfristige Projektregel bleibt erhalten:

- `GRUEN / AUTOMATISCH`: mindestens zwei belastbare `ORIGINAL_ANCHOR` ergeben eine plausible Kalibrierung;
- `ORANGE / MANUELL`: manuelle 2-Punkt-Kalibrierung oder ein Anker plus bekannte KM-Richtung;
- `ROT / FEHLT|UNPLAUSIBEL`: TRA-Geometrie ist vorhanden, aber keine belastbare KM-Kalibrierung;
- `GRAU / KEINE_GEOMETRIE`: keine Geometrie verknüpft.

Für die Plausibilitätsprüfung müssen zwei Anker mindestens 20 m auseinanderliegen. Der kontinuierliche Faktor zwischen KM und TRA-Station muss betragsmäßig zwischen 0,5 und 1,5 liegen. KM-Sprünge werden aus der kontinuierlichen Kalibrierung herausgerechnet und danach diskret wieder angewendet.

Ein einzelner Anker plus `STEIGEND`/`FALLEND` ist nur verwendbar, wenn Start und Ende des Abschnitts bekannt sind. Die Richtungsangabe bezieht sich wie in der Android-Logik auf die Laufrichtung Start → Ende; daraus wird das Vorzeichen gegen die TRA-Station abgeleitet.

## API

Neu unter `/api/v1`:

- `GET /worksites/{worksite_id}/survey-sections`;
- `POST /worksites/{worksite_id}/survey-sections`;
- `GET /survey-sections/{dataset_id}`;
- `PATCH /survey-sections/{dataset_id}`.

Schreibzugriffe benötigen `track.edit`, unterstützen Portal-Session + CSRF sowie Android-Geräte-Bearer über den bestehenden Fach-API-Vertrag und sind mit `X-Client-Change-Id` replay-sicher.

Änderungen laufen über die vorhandene `track_data.version` und liefern bei veralteter Basisversion `VERSION_CONFLICT`. Change Feed/Audit verwenden weiterhin das bestehende Track-Datenmodell.

## Noch offen

Der nächste fachliche Rechenschritt ist die belastbare Projektion echter KM-/Festpunkte aus `GRA`, `KF`, `KV`, `GVP` usw. auf die TRA-Geometrie. Erst daraus dürfen automatisch `ORIGINAL_ANCHOR` entstehen. Danach folgen TRA-Verdichtung auf etwa 2 m, DB_REF→WGS84 und eine robuste Überlappungs-/Abschnittsverschmelzung.
