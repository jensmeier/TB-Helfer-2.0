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
- aktueller Android-Buildkandidat: **200048** plus Live-KM-Hotfix im selben versionCode.
- Produktivserver/NAS unverändert auf Schema 10.

## Normaler Entwicklungsablauf
Source of Truth ist GitHub.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen
4. bei KM-/Room-Änderungen `Clean Project`
5. Build/Run und Gerätetest

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200048 – bestätigte Teile
### BTB / Bildauswahl
- kompakter Material-Button **`Bilder`** im Arbeitseintrag;
- direkter Android-Bildwähler, kein Kamera-Zwischenmenü;
- bestehende Zuordnung über stabile `btbId`, Firma, Haupt-/Unterbaustelle, Arbeitstext, Uhrzeit und KM bleibt erhalten;
- Material-Theme für Tag/Nacht;
- BTB-Mitternachtsfall `23:59 -> 00:30` praktisch bestätigt;
- Haupt-/Unterbaustellenanzeige wurde nach 200047 wieder als passend gemeldet.

### Roh-KF / Festpunkte / KM
- rohe KF Version 7.0 / 7.0 R wird direkt gelesen;
- 102-Byte-Datensätze, Little Endian;
- Punktnummer, DB_REF-Rechts-/Hochwert, Höhe, Station/KM, PArt, Strecke, RiKz und Rohinformationen bleiben erhalten;
- `1e40`, Strecke `0000` und Station `0.0` erzeugen keine falschen KM;
- `KF_RAW` läuft über den bestehenden GVP/Festpunktpfad;
- GVP/Festpunkt-Kalibrierung läuft auch ohne GRA;
- vorhandene GVP-KM-Logik und manuelle 2-Punkt-Kalibrierung bleiben erhalten, keine Parallelberechnung.

### Praktischer Test 200048
- Android-Studio-Build erfolgreich; bekannte Warnungen, keine Buildfehler.
- grafische Streckendatenübersicht zeigt nach Roh-KF-/KM-Berechnung wieder fortlaufende KM-Werte entlang der TRA, z. B. ca. km 76,6 bis 77,2.
- **Live-KM in der Kamera war trotzdem fehlerhaft:** auf Poco und Pixel blieb der angezeigte KM trotz Standortänderung gleich.

## Live-KM-Hotfix – gefundene Ursache
Die KM-Werte selbst waren nicht das Problem. Der Fehler lag in der GPS-Lage der TRA-Samplepunkte.

Die Strecke 5362 enthält DB_REF/Gauss-Krüger-Rechtswerte im **Streifen 3**, z. B.:
- Rechtswert `3 597 538,6261`
- Hochwert `5 280 599,5563`

`DbRefKoordinatenwandler` war bisher fest auf:
- False Easting `4 500 000`
- Mittelmeridian `12°`
- also GK-/DB_REF-Streifen 4

eingestellt.

Damit wurde ein Streifen-3-Punkt rechnerisch an eine völlig falsche GPS-Position gelegt. Beispiel für den obigen 5362-Punkt:
- korrekt mit Streifen 3 ungefähr `47.6564 / 10.2974`
- mit der alten festen Streifen-4-Rechnung ungefähr `47.0434 / 0.1166`

Folge:
- grafische TRA/KM-Darstellung aus DB_REF konnte korrekt aussehen;
- GPS-Liveposition konnte aber nicht korrekt auf diese TRA projiziert werden;
- Kamera fiel dadurch auf alte/feste KM-Pfade zurück bzw. bekam keinen echten bewegten TRA-Treffer;
- deshalb blieb derselbe KM auf mehreren Geräten stehen.

## Live-KM-Hotfix – Umsetzung
Betroffene Datei:
- `app/src/main/java/de/tbhelfer/vermessung/DbRefKoordinatenwandler.kt`

Umgesetzt:
- neue automatische Streifenerkennung aus dem Millionenanteil des Rechtswerts;
- 3.x Mio. -> GK-Streifen 3 / Mittelmeridian 9° / False Easting 3 500 000;
- 4.x Mio. -> GK-Streifen 4 / 12° / 4 500 000;
- 5.x Mio. -> GK-Streifen 5 / 15° / 5 500 000;
- vorhandener Methodenname `dbRef2016Zone4ZuWgs84OrNull(...)` bleibt als Kompatibilitätswrapper bestehen, erkennt intern aber jetzt den tatsächlichen Streifen;
- zusätzliche Plausibilitätsprüfung für Deutschland verhindert offensichtlich falsche GPS-Ergebnisse;
- dadurch profitieren bestehende TRA-Importe und `gpsKoordinatenNachberechnen()` ohne neue Parallelstruktur.

Neuer Test:
- `app/src/test/java/de/tbhelfer/vermessung/DbRefKoordinatenwandlerTest.kt`
- prüft den echten 5362-Streifen-3-Punkt auf ungefähr `47.6564 / 10.2974`;
- prüft den historischen Kompatibilitätsaufruf;
- prüft Ablehnung eines unplausiblen Streifens.

Wichtig: bestehende lokale Daten müssen **nicht gelöscht** werden. `Daten berechnen` führt zuerst `gpsKoordinatenNachberechnen()` und danach die KM-/Stationsverknüpfung aus; damit werden bereits importierte TRA-Samplepunkte mit der korrigierten GPS-Lage neu gespeichert.

## Nächster Handtest Live-KM
1. GitHub Desktop `Fetch origin -> Pull origin`.
2. Android Studio `Clean Project -> Build/Run`.
3. App-Daten nicht löschen.
4. Streckendaten öffnen und **`Daten berechnen`** erneut ausführen.
5. Kamera mit passendem Strecken-/Gleiskontext öffnen.
6. Kontrollieren, dass die angezeigten GPS-Koordinaten bei Bewegung tatsächlich wechseln.
7. Kontrollieren, dass der Live-KM mit der Bewegung mitläuft und nicht auf einem festen Wert stehen bleibt.
8. Ein Foto speichern; derselbe aktuelle KM muss in die Foto-Maske und – bei aktivem Overlay – dauerhaft ins JPG übernommen werden.

Wenn GPS-Koordinaten wechseln, aber KM weiterhin fest bleibt, als nächsten Schritt nur noch Segment-/Kontextauswahl prüfen. Wenn bereits die GPS-Koordinaten nicht wechseln, Standortquelle/GNSS prüfen. Nicht wieder an KF/GVP-Kalibrierung drehen, solange die grafische KM-Linie korrekt bleibt.

## Gradle-Hinweis
Der lokale 200048-Build war erfolgreich. Gradle meldet zusätzlich beim Task `verifyRoomGeneratedImplementation`, dass `Task.project` zur Ausführungszeit nicht configuration-cache-kompatibel ist. Das ist aktuell **kein APK-/Room-Fehler** und nicht Ursache des Live-KM-Problems. Der Guard soll später separat cache-kompatibel gemacht werden.

## Bestätigter BTB-Kernstand
- stabile fachliche `btbId`; Datum/Uhrzeit sind Attribute, nicht Identität;
- mehrere BTBs desselben Teams am selben Kalendertag möglich;
- ein BTB darf über Mitternacht laufen;
- kanonischer Lesekontext: aktuelle Baustelle -> aktiver BTB-Pointer -> `btbId`;
- nach erfolgreichem Abschluss wird die aktive `btbId` gelöst;
- Reaktivierung nur bewusst über `BTB-Verlauf -> BTB bearbeiten`;
- erster erfolgreicher Abschluss ist unveränderliches Original;
- spätere fachliche Änderungen werden gegen dieses Original rot dargestellt;
- Kopfdaten werden im PDF nicht rot markiert;
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF;
- moderne BTB-Fotos strikt nach derselben `btbId`;
- Legacy-Zeitfenster nur für Altbestand ohne `btbId`;
- Foto-ID-Menge des ersten Abschlusses gehört zum unveränderlichen Original;
- gleiche Foto-Texte im selben Firmen-/Ortskontext können gruppiert werden;
- Firmenlogo/Kontaktdaten/PDF-Vorschau aus 200039 praktisch bestätigt.

## Foto-Overlay
Der vorhandene `OverlayRenderer` brennt Informationen dauerhaft in das App-JPG ein:
- Baustelle/Unterbaustelle;
- Strecke/Bahnhof;
- Gleis/Weiche/Objekt;
- KM;
- GPS;
- Bereich;
- Datum/Uhrzeit.

Keine zweite Overlay-Lösung bauen. Sobald Live-KM bestätigt ist, Foto-Overlay praktisch prüfen und nur bei echtem Bedarf optisch nachschärfen.

## Technische Schuld
Seit 200043 existiert für kleine BTB-/Import-Hooks eine generierte Source-Brücke. 200044 hält die kanonischen Room-Datenbankquellen separat für KSP sichtbar und prüft `TBDatenbank_Impl`. Die Brücke soll später sauber zurückgebaut werden, aber nicht parallel zum laufenden Live-KM-Test.

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
Nach erfolgreichem Live-KM-Test:
1. Foto-Overlay praktisch prüfen.
2. Gradle-Configuration-Cache-Warnung des Room-Guards separat bereinigen.
3. Einstellungen systematisch auf Funktion, Doppelungen und Reihenfolge prüfen.
4. Wetter um Niederschlagsmenge ergänzen; nach erstem Abschluss keine automatische Wetteränderung.
5. Mängelaufnahme vereinfachen.

## Noch offene ältere Abschlussprüfungen
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren;
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren;
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
