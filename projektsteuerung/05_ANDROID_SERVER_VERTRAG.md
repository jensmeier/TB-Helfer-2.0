# TB-HELFER – ANDROID/SERVER VERTRAG
Stand08.11.2026 – unabhängige Gesamtprüfung.
Geprüfter SERVER-Fachstand `b213bcbfe8d5fcf49c17e71108b8be04167f4d7f`; ANDROID-Fachstand `a8401775a3f607594df91468e728ee3f6d3a1ec4`.
Nachfolgende Dokumentationscommits verändern diese geprüften Fachstände nicht.
[Vollständiger Prüfbericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [Unveränderte vorherige Fassungen](archiv/GESAMTPRUEFUNG_2026-11-08/README.md).

Fachliche Regeln: [Projektregeln](../docs/TB-HELFER_PROJEKTREGELN.md). Detaillierte bestehende Verträge unter `vertraege/` bleiben gültig; der folgende Abgleich ist IST/ZIEL, keine parallele API-Neudefinition.
| Bereich | Gemeinsame Vorgabe | Tatsächlicher Prüfstand |
|---|---|---|
| Identität/Version | Stabile UUID, ursprüngliche Basisversion, idempotente Clientänderung | Grundmechanismen vorhanden; Trassendaten-409-Neuversuch verletzt fachliche Konflikterhaltung |
| Firma/Team/Bereich | Keine fremden Firmendaten; Helfer nur erlaubter Bereich | Neue Abweichung AUDIT-1108-01: track_data-Liste/Detail prüft Fachrecht/Bereich nicht |
| Abschluss/PDF | DB-Status/Dateihash/Datei konsistent; Haupt-TB finalisiert | Neue Abweichung AUDIT-1108-02: Cleanup nach Commit kann Datei zurückrollen |
| Trassendaten | Änderungen objektbezogen, fremde Daten unverändert | WorksiteTrackDataApi gleicht lokalen Gesamtbestand ab und löscht Fehlende; TRACK_KM_1103 |
| Gemeinsame KM | Vorhandene Quelle/Achse per UUID/Hash/Version, Anker/Genauigkeit/versionierte Beiträge | /api/v1/survey-catalog/axes/{axis_id}/km-sync serverseitig; Androidconsumer fehlt |
| Originale | Rohdatei unverändert, Bearbeitung getrennt | Baustellensnapshot enthält Samples/Punkte, keine Rohdateien; from-file-Originalpfad mobil nicht angeschlossen |
| Delta | Typbezogen nachladen, Cursor/Tombstones, Offline-Nachholung | BTB/Datei/Baustelle vorhanden; DEFECT/ACCEPTANCE/TRACK_DATA/globaler Survey fehlen im Android-Defaultfeed |
| Helfer | Schichtende erst nach Datenbestätigung; PDF nur Haupt-TB | Codepfade vorhanden; praktische beide-Richtungen-Abnahme offen |
| Fotos | Original lokal, kleine Version/Thumbnail/Metadaten, neueServerfotos<=2.000.000Bytes | Kleine Transportpfade vorhanden; echter Transfer/Originalhash auf Geräten offen |
| PDF/Listen | DreiBilder/Reihe, gespeicherte Fotoanzahl, Unterbaustellen; Word erhalten; XLSX portabel | Renderer6 und Export1108B vorhanden, gezielte Tests; aktuelle Gesamt-E2E offen |
| Logo | Serverfreigabe40–400pt | Android clamp250 weiterhin AbweichungADMIN-PDF-011 |
| Version/Update | Tatsächliche Appversion melden, Update mit kompatiblem Zertifikat | Header fehlen; wechselnde CI-Debugzertifikate, installierte Signatur offen |

WorksiteTrackDataSync: LEAD sendet, Nicht-LEAD liest; nicht als symmetrischen gemeinsamen Katalogsync beschreiben. Lokale manuelle KM-Samples können bei späterem Vollsync übertragen werden; ursprüngliche Anker/Genauigkeit/Beitragsidentität und unmittelbare dauerhafte Auslösung fehlen.
Die serverseitige PDF-Teamleiterprüfung ist bereits implementiert; keine erneute Aufgabe „final-pdf nur mit btb.view“ anlegen. Geschützte Bestätigungen aus [vorherigem Vertrag](archiv/GESAMTPRUEFUNG_2026-11-08/05_ANDROID_SERVER_VERTRAG.md) erhalten.


## Entwicklungsauftrag GESAMT-1108 – ausdrücklich freigegeben
Der Nutzer hat nach der Gesamtprüfung mit „okay alles bauen“ die Umsetzung der dort beschriebenen Korrekturen beauftragt. Die vorherige Beschränkung dieses Auftrags auf Prüfung ist damit für diesen Entwicklungsblock aufgehoben.
Umfang: Server-Rechte und PDF-Commit/Cleanup, bekannte manuelle KM-Katalogfehler; Android-Trassendatenabgleich/atomarer Empfang/gemeinsamer KM-Vertrag und Originaltransport; Fachdelta, Logo400, Versionsheader, 16-Stunden-Frist; fachliche Prüfpflege und revisionsgenauer Querabgleich. Bestehende Architektur/IDs/Originale/Bestätigungen erhalten.
Gezielt testen, erforderliche Builds ausführen und Android-APK nur zusammen mit exakt passender Release-MD bereitstellen. Signaturkontinuität muss geprüft werden; kein erfundener Ersatz für einen fehlenden vorhandenen privaten Signierschlüssel. Keine NAS-Übernahme, produktive Migration/Datenmutation oder SSH beauftragt. Kein Budget erhöhen. Einzeltests/Build/CI/Installation/E2E getrennt nachweisen.
