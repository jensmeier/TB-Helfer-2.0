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
- Android **200044** startet nach dem Room/KSP-Fix wieder auf dem Poco.
- Android **200045**: kompakter Bildwähler im BTB-Arbeitseintrag funktioniert praktisch; eine kurz beobachtete durcheinander wirkende BTB-Anzeige war anschließend nicht mehr reproduzierbar, vorhandene Einträge waren wieder sichtbar. Deshalb keine weitere BTB-Änderung ohne reproduzierbaren Fehler.
- aktueller Buildkandidat: Android **200046 – direkter Roh-KF-7.0-Import für Festpunkte/KM-Quellen**.

## Normaler Entwicklungsablauf
Source of Truth ist GitHub.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen/builden/testen

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200046 – KM/Festpunkte: Roh-KF direkt importieren
### Gemeldetes Fehlerbild
- Vermessungs-/Absteckungsdaten lassen sich einlesen.
- Festpunkte mit vorhandener Kilometrierung fehlen danach bzw. sind nicht mehr sichtbar.
- KM-Berechnung funktioniert dadurch nicht wie früher.

### Gefundene Ursache
Der ZIP-Scanner erkennt rohe `.kf`-Dateien bereits als `KF_RAW`, der eigentliche Importpfad hat sie aber bisher ausdrücklich **nicht importiert** und nur gemeldet:

`nicht direkt importiert: KF-Original erkannt; falls vorhanden wird KF.TXT verwendet`

Damit fehlen gerade in Beständen, bei denen die belastbare Strecke/Station nur oder besser im binären KF-Original steckt, die Festpunkte als KM-Quelle. Das passt zum gemeldeten Fehlerbild.

Die frühere vollständige Vermessungsdatenprüfung im Projekt bestätigt:
- rohe KF-Dateien sind KF Version 7.0 / 7.0 R mit festen 102-Byte-Datensätzen;
- numerische Werte sind Little-Endian-Doubles;
- echte Station/KM, Strecke und PArt sind direkt im Rohdatensatz enthalten;
- `1e40` ist ein Fehlwert/Sentinel;
- Station `0.0` darf nicht blind als echte km 0 verwendet werden;
- rohe KF sollen direkt importiert und gegenüber `.kf.txt` bevorzugt werden, wenn die Originaldaten vorhanden sind.

### Umsetzung 200046
`GvpDateiParser` kann jetzt rohe KF 7.0 direkt lesen:
- Headerprüfung: Dateigröße Vielfaches von 102 Byte und `VERSION 7.0` / `VERSION 7.0  R` im Header;
- pro Punkt werden KF-Punktnummer, Rechtswert, Hochwert, Höhe, Station, Lagebezeichnung, Zusatzfeld, PArt, VArt, Statusbytes, Metadaten, Strecke und RiKz gelesen;
- `abs(Wert) >= 1e30` wird als fehlend behandelt;
- Strecke `0000` wird nicht als reale Strecke übernommen;
- Station `0.0` wird nicht als belastbare KM-Quelle übernommen;
- PArt wird unverändert erhalten, also u. a. `PS0`, `PS1`, `PS2`, `PS3`, `PS4`, `GVPV`, `GPSC`, `HBH`, `PPB`;
- der vollständige 102-Byte-Punktdatensatz wird zusätzlich als Hex im `rawLine` erhalten, damit Status-/Zusatzinformationen nicht verloren gehen;
- DB_REF/GK-Streifen 4 nutzt den vorhandenen Zone-4-WGS84-Wandler;
- DB_REF/GK-Streifen 3 (z. B. Strecke 5362) bleibt mit den Original-Rechts-/Hochwerten erhalten und wird für die bestehende TRA/GVP-KM-Zuordnung verwendet; dafür wird noch keine falsche GPS-Umrechnung über den Zone-4-Wandler erzwungen.

Der vorhandene Importpfad wird wiederverwendet:
- `KF_RAW` läuft jetzt wie `KF_TXT`, `KV` und GVP-TXT in `importiereZipGvpDatei(...)`;
- Speicherung bleibt über `TrassenRepository.gvpImportSpeichern(...)`;
- die bestehende GVP-KM-Kalibrierung bleibt unverändert: Festpunkte mit Strecke + KM werden räumlich der TRA-Achse zugeordnet und können `kmQuelle = GVP` erzeugen;
- Originaldateien werden nicht verändert.

### Betroffene Dateien 200046
- `app/src/main/java/de/tbhelfer/vermessung/GvpDateiParser.kt`
- `app/build.gradle.kts`
- `app/src/test/java/de/tbhelfer/vermessung/GvpDateiParserRawKfTest.kt`
- `docs/TB-HELFER_AKTUELLER_STAND.md`

Hinweis zur bestehenden Build-Source-Brücke:
- Seit 200043 wird ein generierter App-Quellbaum verwendet, um den BTB-Foto-Hook einzusetzen.
- Deshalb wird die kleine `KF_RAW -> importiereZipGvpDatei`-Routingänderung aktuell ebenfalls beim Build in die generierte `ImportExportFragment.kt` eingesetzt.
- Das ist bewusst keine zweite Importlösung; der eigentliche Parser/Repository-Pfad bleibt der bestehende GVP/Festpunktpfad.
- Die Source-Brücke ist technische Schuld und soll später wieder entfernt werden, aber nicht parallel zu diesem Fehlerblock.

### Tests 200046
Neue Unit-Tests erzeugen synthetische echte 102-Byte-KF-7.0-Datensätze und prüfen:
1. Strecke 5362 / PS1: Station 70+698,179, Strecke 5362 und Rohkoordinaten werden korrekt gelesen; Zone-3-Punkt erhält bewusst keine falsche Zone-4-GPS-Umrechnung.
2. `VERSION 7.0  R` / GVPV 5300: echte Station und Höhe werden übernommen.
3. Harburg-Sonderfall: Station `0.0` wird zu `kmMeter = null`.
4. Pasing-Sonderfall: `1e40` und Strecke `0000` bleiben fehlend statt eine falsche Kilometrierung zu erzeugen.

Lokaler Android-Studio-Build und Unit-Testlauf stehen noch aus. GitHub Actions ist weiterhin kein belastbarer Buildindikator, solange die PR-Runs ohne verwertbare Runner-Schritte enden.

### Nächster Handtest 200046
1. GitHub Desktop `Fetch origin` → `Pull origin`.
2. Android Studio: Clean/Build und Unit-Tests; App als Update installieren, App-Daten nicht löschen.
3. In Vermessungs-/Streckendaten denselben Testbestand bzw. ZIP mit rohen KF-Dateien importieren.
4. Importstatus prüfen: rohe KF darf nicht mehr als „nicht direkt importiert“ erscheinen, sondern muss echte `GVP/Festpunkte` melden.
5. Für 5362 müssen die PS0/PS1/PS2/PS3- bzw. PS4-Punkte mit ihren vorhandenen Stationen wieder als Festpunktbestand vorhanden sein.
6. `Daten berechnen`/KM-Verknüpfung ausführen und prüfen, ob GVP-KM-Kalibrierung wieder Achsen/Punkte verknüpft.
7. Erst danach die Live-KM-Anzeige testen.

## Android 200045 – kompakte Bildauswahl im BTB-Arbeitseintrag
Umgesetzt:
- zwei große blaue Foto-Schaltflächen entfernt;
- ein kompaktes Bild-/Galerie-Symbol im Arbeitseintragsdialog;
- Tippen öffnet direkt den vorhandenen Android-Bildauswahlpfad für `image/*`;
- keine separate Kameraaktion in diesem Dialog;
- bestehende Zuordnung über stabile `btbId`, Firma, Unterbaustelle/Ort, Arbeitstext und KM/KM-Bereich bleibt erhalten;
- Symbolfarbe/Ripple folgen dem Material-/Android-Theme für Tag/Nacht.

Praktischer Test:
- Bildsymbol wurde auf dem Gerät angezeigt und öffnete die Bildauswahl.
- Der BTB wirkte nach einem Wechsel kurz unvollständig/durcheinander, vorhandene `bbg`-/Unterbaustellen-/KM-Einträge waren anschließend wieder sichtbar und der Nutzer meldete „es funktioniert plötzlich“.
- Solange dieser Zustand nicht reproduzierbar ist, wird am BTB-Lade-/Speicherpfad nichts weiter verändert.

## Android 200043/200044 – Room/KSP-Regressionsfix
200043 führte den Foto-Hook direkt im Dialog `Eintrag für <Firma>` ein, ersetzte dafür aber den normalen Android-Kotlin-Source-Set durch eine generierte Build-Kopie. Dadurch kompilierte Kotlin, Room/KSP erzeugte jedoch `TBDatenbank_Impl` nicht; die App stürzte erst beim Start ab.

200044 korrigierte das:
- `src/main/java/de/tbhelfer/datenbank` bleibt als kanonischer Kotlin-Source-Ordner für KSP sichtbar;
- der Datenbankordner wird aus der generierten BTB-Quellkopie entfernt, damit keine doppelten Klassen entstehen;
- Guard `verifyRoomGeneratedImplementation` prüft, ob `TBDatenbank_Impl.*` unter `build/generated/ksp` erzeugt wurde;
- der anfängliche Guardfehler `$$implicitReceiver_Project ... this is null` wurde über `project.layout` behoben.

Praktischer Stand: 200044 startete danach auf dem Poco wieder. Dieser Room/KSP-Schutz bleibt erhalten.

## Bestätigter BTB-Kernstand 200035–200039
- stabile fachliche `btbId`; Datum/Uhrzeit sind Attribute, nicht Identität;
- mehrere BTBs desselben Teams am selben Kalendertag möglich;
- erster erfolgreicher Abschluss = unveränderliches Original;
- spätere fachliche Änderungen gegen dieses Original; neue/geänderte Inhalte rot, entfernte Originalinhalte rot `(entfernt)`;
- Kopfdaten bleiben im PDF schwarz;
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF;
- moderne BTB-Fotos strikt nach derselben `btbId`; Legacy-Zeitfenster nur ohne `btbId`;
- Foto-ID-Menge des ersten Abschlusses gehört zum unveränderlichen Original;
- gleiche Foto-Texte im selben Firmen-/Ortskontext können gruppiert werden;
- 24h-TimePicker für BTB-Zeit, Personal und Geräte/Maschinen;
- abgeschlossene BTBs lassen sich bewusst wieder öffnen und danach erneut abschließen;
- Firmenlogo/Kontaktdaten/PDF-Vorschau wurden in 200039 praktisch bestätigt.

## Foto-Overlay
Der vorhandene `OverlayRenderer` brennt bei Kamera-Fotos Informationen dauerhaft in die App-JPG-Datei ein, u. a. Baustelle/Unterbaustelle, Strecke/Bahnhof, Gleis/Weiche/Objekt, KM, GPS, Bereich und Datum/Uhrzeit.

Noch offen: praktisch auf Pixel/Poco prüfen und nur bei echtem Bedarf optisch nachschärfen. Keine parallele zweite Overlay-Lösung bauen.

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

## Danach geplante Arbeiten
Nach erfolgreichem 200046-KM/Festpunkt-Test:
1. Foto-Overlay praktisch prüfen und ggf. optisch verbessern.
2. Einstellungen systematisch auf Funktion, Doppelungen und sinnvolle Reihenfolge prüfen/aufräumen.
3. Wetter um Niederschlagsmenge in mm bzw. l/m² ergänzen; nach erstem Abschluss keine automatische Wetteränderung.
4. Mängelaufnahme vereinfachen: `Was? → Wo/KM? → Beschreibung`, aktueller KM als Vorschlag.

## Noch offene ältere Abschlussprüfungen
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren;
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren;
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
