# TB-Helfer – aktueller Projektstand

Stand: 2026-09-16

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen in `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. aktiven Feature-Branch, offenen Draft-PR und neuesten relevanten Commit

`main` bleibt stabil. Wesentliche Änderungen zuerst im Feature-Branch/Draft-PR.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiv: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft/mergeable.
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- letzter vollständig bestätigter BTB-Kernstand: Android **200039**.
- Android **200044**: Room/KSP-Startfix auf dem Poco bestätigt.
- Android **200045**: direkter Android-Bildwähler im Arbeitseintrag praktisch bestätigt.
- Android **200047**: lokaler Build erfolgreich; Mitternacht `23:59 -> 00:30` praktisch bestätigt; Haupt-/Unterbaustellenanzeige wurde danach vom Nutzer als wieder passend gemeldet.
- aktueller Buildkandidat: Android **200048**.

## Normaler Entwicklungsablauf
Source of Truth ist GitHub.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen/builden/testen

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200048 – Bilder-Button + Roh-KF/Festpunkte/KM

### 1. BTB-Arbeitseintrag – Bilder-Button
Der nackte kleine Bild-Icon-Button aus 200045 funktionierte technisch, war aber im Dialog optisch zu unklar.

200048 verwendet deshalb einen kompakten Material-Outlook-Button:
- Text: **`Bilder`**;
- vorhandenes Bildsymbol links im Button;
- Tippen öffnet weiterhin **direkt den Android-Bildwähler** für Bilder;
- kein Kamera-Zwischenmenü und kein eigener WhatsApp-Pfad;
- vorhandene Zuordnung bleibt unverändert: stabile `btbId`, Firma, Unterbaustelle/Ort, Arbeitstext, Uhrzeit und KM/KM-Bereich;
- Material-Theme liefert Kontur, Text/Icon und Ripple, daher keine fest codierten Hell-/Dunkelfarben und damit Tag-/Nacht-tauglich.

Betroffene Datei:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt`

### 2. Roh-KF direkt als Festpunkt-/KM-Quelle
Der ZIP-Scanner erkannte rohe `.kf` bereits als `KF_RAW`, importierte sie früher aber nicht und meldete nur sinngemäß `KF-Original erkannt; falls vorhanden wird KF.TXT verwendet`.

Seit 200046/200047 ist der bestehende GVP/Festpunktpfad erweitert:
- rohe KF **Version 7.0 / 7.0 R** werden direkt gelesen;
- Datensatzgröße 102 Byte, Little-Endian-Doubles;
- Punktnummer, Rechtswert, Hochwert, Höhe, Station, Lagebezeichnung, Zusatzfeld, PArt, VArt, Status/Metadaten, Strecke und RiKz werden übernommen;
- `1e40` bzw. `abs(Wert) >= 1e30` gelten als Fehlwert;
- Strecke `0000` wird nicht als reale Strecke verwendet;
- Station `0.0` wird nicht als belastbare KM-Quelle verwendet;
- PS0/PS1/PS2/PS3/PS4/GVPV usw. bleiben erhalten;
- Originaldateien werden nicht verändert;
- DB_REF/GK-Streifen 4 nutzt den bestehenden WGS84-Wandler;
- GK-Streifen 3, z. B. Strecke 5362, bleibt mit Original-Rechts-/Hochwerten erhalten, damit keine falsche Zone-4-GPS-Umrechnung erzwungen wird.

Der generierte Build-Quellpfad routet `KF_RAW` in denselben bestehenden `importiereZipGvpDatei(...)`-/`gvpImportSpeichern(...)`-Pfad wie KF.TXT/KV/GVP.

Betroffene Dateien:
- `app/src/main/java/de/tbhelfer/vermessung/GvpDateiParser.kt`
- `app/src/test/java/de/tbhelfer/vermessung/GvpDateiParserRawKfTest.kt`
- `app/build.gradle.kts`

### 3. Gefundener zweiter KM-Fehler: GVP war fälschlich von GRA abhängig
Beim erneuten Vergleich des bestehenden KM-Pfads wurde ein weiterer echter Fehler gefunden:

`TrassenRepository.graMitAchspunktenVerknuepfen()` brach bisher sofort ab, wenn **keine GRA-Punkte** vorhanden waren. Die darunter bereits vorhandene GVP/Festpunkt-Kalibrierung wurde dann gar nicht mehr ausgeführt.

Das ist fachlich falsch: **TRA + ausreichend passende Festpunkte/KF mit Strecke und KM muss auch ohne GRA als KM-Quelle funktionieren.**

200048 korrigiert nur diesen Frühabbruch:
- GVP/Festpunkte bleiben eine eigenständige KM-Quelle;
- nur wenn keine Achsen oder keine Achssamplepunkte vorhanden sind, wird weiterhin abgebrochen;
- bei leerer GRA läuft die vorhandene `kmMitGvpFestpunktenKalibrieren(...)` trotzdem;
- die bestehende robuste GVP-Logik bleibt unverändert: gleiche Strecke, mindestens 4 belastbare Festpunkte, räumliche Zuordnung zur TRA, Median/Robustfilter und `kmQuelle = GVP`;
- manuelle 2-Punkt-Kalibrierung behält weiterhin Vorrang und wird danach erneut angewendet;
- bewusst keine zweite KM-Berechnungslösung gebaut.

Betroffene Datei:
- `app/src/main/java/de/tbhelfer/datenbank/TrassenRepository.kt`

Bei der Umsetzung wurden zwei unbeabsichtigte Nebendifferenzen im großen Repository-File sofort wieder zurückgenommen; der Netto-Unterschied gegenüber dem vorherigen Stand betrifft nur den GVP-ohne-GRA-Frühabbruch und die zugehörige Statusmeldung.

### 4. Buildschutz / Version
`app/build.gradle.kts`:
- `versionCode = 200048`;
- vorhandener Room/KSP-Guard für `TBDatenbank_Impl` bleibt aktiv;
- zusätzlicher Source-Guard prüft vor dem Löschen der generierten Datenbankkopie, dass die 200048-GVP-ohne-GRA-Korrektur im kanonischen `TrassenRepository.kt` vorhanden ist;
- CRLF/LF-Normalisierung des generierten Source-Patchers aus 200047 bleibt aktiv.

## Teststatus Android 200048
Bereits vorhanden:
- `GvpDateiParserRawKfTest.kt` mit synthetischen 102-Byte-KF-Datensätzen für:
  1. Strecke 5362 / PS1 mit echter Station;
  2. `VERSION 7.0 R` / GVPV 5300;
  3. Harburg Station `0.0` -> keine KM-Quelle;
  4. Pasing `1e40` + Strecke `0000` -> fehlend statt falscher KM.
- Quellvergleich bestätigt: `importiereZipGvpDatei(...)` liest Bytes und ruft `GvpDateiParser.parse(dateiname, bytes)` auf; Roh-KF wird dadurch nicht als Text behandelt.
- Quellvergleich bestätigt: GVP-KM-Zuordnung arbeitet mit Original-DB_REF-Koordinaten und kann daher für GK-Streifen 3 die KM-Kalibrierung durchführen, auch wenn dort noch keine WGS84-Koordinaten vorhanden sind.
- Netto-Diff von `TrassenRepository.kt` nach Korrektur auf den beabsichtigten GVP-ohne-GRA-Block begrenzt.

Noch ausstehend:
- lokaler Android-Studio-Build 200048;
- Unit-Testlauf auf dem Windows-PC;
- Gerätetest des neuen `Bilder`-Buttons in Tag und Nacht;
- praktischer Import eines vorhandenen Vermessungs-/Absteckungsbestands mit roher KF;
- danach `Daten berechnen` und Kontrolle, ob `GVP-Kalibrierung` wieder Samplepunkte mit KM erzeugt;
- anschließend Live-KM-Test auf der passenden Strecke.

GitHub Actions bleibt derzeit kein verlässlicher Buildindikator, weil die PR-Runs wiederholt ohne verwertbare Runner-Schritte/Logs enden. Lokaler Android-Studio-Build ist deshalb der maßgebliche nächste Buildtest.

## Android 200047 – bestätigter BTB-Fix
200047 korrigierte drei Punkte:
- Windows-CRLF/LF konnte den `KF_RAW`-Buildanker nicht mehr brechen;
- Firmen-Hauptbaustelle wird intern explizit mit `@ORT=0` gespeichert; moderne Inline-Ortsmarker gelten nur pro Zeile, alte Standalone-Ortsmarker bleiben als Legacy unterstützt;
- normales Öffnen verwendet zuerst `BtbActiveContext`/aktive `btbId`, sodass ein Nacht-BTB über Mitternacht nicht wegen des neuen Kalendertags verloren geht; `selectedDate` wird aus dem aktiven BTB wiederhergestellt.

Praktisch bestätigt:
- Android-Studio-Build 200047 erfolgreich, nur bekannte Warnungen;
- Nacht-BTB `23:59 -> 00:30` blieb vollständig und im selben BTB-Kontext;
- nach weiterem Gerätetest meldete der Nutzer die BTB-Haupt-/Unterbaustellenanzeige wieder als passend.

Noch als allgemeine Abschlusskontrolle offen: nach `BTB Abschluss` weiterhin prüfen, dass eine Reaktivierung nur bewusst über `BTB-Verlauf -> BTB bearbeiten` erfolgt.

## Bestätigter BTB-Kernstand
- stabile fachliche `btbId`; Datum/Uhrzeit sind Attribute, nicht Identität;
- mehrere BTBs desselben Teams am selben Kalendertag möglich;
- ein BTB darf über Mitternacht laufen, ohne automatisch einen neuen BTB zu erzeugen;
- kanonischer aktueller Lesekontext: aktuelle Baustelle -> aktiver BTB-Pointer -> `btbId`;
- nach erfolgreichem Abschluss wird aktive `btbId` gelöst; Reaktivierung nur bewusst über Verlauf;
- erster erfolgreicher Abschluss ist unveränderliches Original;
- spätere fachliche Änderungen werden gegen dieses Original rot dargestellt; Rückkehr zum Original wieder schwarz;
- Kopfdaten werden im PDF nicht rot markiert;
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF;
- moderne BTB-Fotos strikt nach derselben `btbId`; Legacy-Zeitfenster nur für Altbestand ohne `btbId`;
- Foto-ID-Menge des ersten Abschlusses gehört zum unveränderlichen Original;
- gleiche Foto-Texte im selben Firmen-/Ortskontext können gruppiert werden;
- Firmenlogo/Kontaktdaten/PDF-Vorschau aus 200039 praktisch bestätigt.

## Technische Schuld: generierte Source-Brücke
Seit 200043 wird für den kleinen BTB-Hook ein generierter App-Quellbaum verwendet. 200044 hält die kanonischen Room-Datenbankquellen separat für KSP sichtbar und prüft `TBDatenbank_Impl`.

Aktuell werden über diese Brücke auch die kleinen 200047-BTB-Korrekturen und das `KF_RAW -> importiereZipGvpDatei(...)`-Routing in die generierte Quellkopie eingesetzt. Parser und Repository bleiben die bestehenden kanonischen Lösungen. Die Source-Brücke soll später sauber zurückgebaut werden, aber nicht parallel zum laufenden KM/Festpunkt-Fehlerblock.

## Foto-Overlay
Der vorhandene `OverlayRenderer` brennt bei Kamera-Fotos Informationen dauerhaft in die App-JPG-Datei ein: Baustelle/Unterbaustelle, Strecke/Bahnhof, Gleis/Weiche/Objekt, KM, GPS, Bereich und Datum/Uhrzeit. Keine parallele zweite Overlay-Lösung bauen.

Noch offen: praktisch auf Pixel/Poco prüfen und nur bei echtem Bedarf optisch nachschärfen.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- Secrets ausschließlich in `./server/.env`; keine Geheimwerte in GitHub/Doku/Logs.
- PostgreSQL/API/cloudflared zuletzt bestätigt gesund; 72 OpenAPI-Pfade, Schema 10.
- letzter bestätigter Produktiv-Backupstand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals `BACKUP_PRUEFUNG=OK`.
- Schema 11 persönliche Nutzerkontakte: vorbereitet, nicht produktiv migriert.
- Schema 12 PDF-Logo-Layout: vorbereitet, nicht produktiv migriert.
- vor produktiven Schema-11-/Schema-12-Schritten frisches verifiziertes DB-Backup erstellen.

## Teams / Baustellen / Sync – weiter offen
- Zusammenarbeit nur innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs gemäß Projektregeln baustellenweit.
- Zwei-Geräte-/Mehrhelfer-Synchronisation und Konflikttests bleiben offen.
- vollständiger Unterbaustellen-/Streckendaten-Sync bleibt offen.
- serverseitiges Entfernen/Verlassen bleibt offen.

## Nächster sinnvoller Arbeitsschritt
1. Windows: `Fetch origin -> Pull origin -> Clean Project -> Build/Run` und prüfen, dass **200048** gebaut wird.
2. `Bilder`-Button im Arbeitseintrag in Tag- und Nachtansicht prüfen; Tippen muss direkt den Android-Bildwähler öffnen und die vorhandene Zuordnung muss erhalten bleiben.
3. Danach denselben Vermessungs-/Absteckungsbestand mit roher `.kf` erneut importieren. Die Roh-KF darf nicht mehr als `nicht direkt importiert` erscheinen, sondern muss echte `GVP/Festpunkte` melden.
4. `Daten berechnen` ausführen. Erwartung: bei passender TRA/Strecke und mindestens vier belastbaren KF-Festpunkten erscheint eine **GVP-Kalibrierung > 0 Punkte** und Samplepunkte erhalten KM.
5. Falls GVP-Kalibrierung `0` bleibt: Import-/Statusanzeige und betroffene Strecke/TRA als Screenshot sichern; dann Strecke-/Achsen-Matching und Koordinatensystem gezielt prüfen, ohne neue Parallelberechnung zu bauen.
6. Erst nach erfolgreicher Festpunkt-/KM-Kalibrierung Live-KM prüfen.
7. Danach Foto-Overlay praktisch prüfen und Einstellungen systematisch aufräumen.

## Noch offene ältere Abschlussprüfungen
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren;
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren;
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
