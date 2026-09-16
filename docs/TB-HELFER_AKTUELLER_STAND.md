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
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft.
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- aktueller Android-Buildkandidat: **200049**.
- letzter geprüfter Branch-Head vor dem BTB-Auto-Sync-Änderungscommit: `188230491dc6ec8a3250500491aa61ca02a95b56` (`fix: keep BTB dialog actions horizontal`).
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

## Android 200049 – Kamera/KM/Foto + BTB-Bilder-Button

### BTB-Arbeitseintrag: `Bilder` auf Dialog-Buttonhöhe
Betroffene Datei:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt`

Umgesetzt:
- `Bilder` sitzt als neutraler AlertDialog-Button in derselben Dialog-Buttonleiste wie `Abbrechen` und `Hinzufügen`.
- Das Bildsymbol verwendet `?attr/colorOnSurface` und bleibt damit Tag-/Nacht-tauglich.
- Ein Tipp öffnet weiterhin direkt den vorhandenen Android-Bildwähler; kein Kamera-Zwischenmenü und kein zweiter Foto-/Overlaypfad.
- Vor dem Bildwähler werden Arbeitstext, Zeit und KM/KM-Bereich validiert und der BTB-Zwischenstand gespeichert.
- Die stabile `btbId`, Firma, Ort/Unterbaustelle, Arbeitstext, Zeit und KM-Werte werden an den vorhandenen Fotoimport übergeben.
- Der vorhandene `OnShowListener` des BTB-Dialogs für `Hinzufügen` bleibt unangetastet. Der `Bilder`-Klickhandler wird über den neutralen Button gesetzt, ohne einen zweiten konkurrierenden `OnShowListener` einzubauen.

### Live-KM in der Kamera größer
Betroffene Dateien:
- `app/src/main/java/de/tbhelfer/ui/kamera/KameraStatusTextView.kt`
- `app/src/main/res/layout/fragment_kamera.xml`

Umgesetzt:
- das vorhandene Kamera-Statusfeld verwendet `KameraStatusTextView`;
- KM-Tokens wie `km 75,007` werden im Livebild ca. 1,65× größer und fett dargestellt;
- vorhandene adaptive Textfarbe und Schattierung bleiben bestehen.

### Einheitliche 10/30-m-Ampel + GPS-Genauigkeit
Betroffene Dateien:
- `app/src/main/java/de/tbhelfer/ui/kamera/FotoKmQualitaet.kt`
- `app/src/test/java/de/tbhelfer/ui/kamera/FotoKmQualitaetTest.kt`
- generierter Kamera-/Foto-Hook in `app/build.gradle.kts`

Verbindliche Logik:
- Achsabstand `<= 10 m` -> **grün**;
- Achsabstand `> 10 m bis <= 30 m` -> **gelb**;
- Achsabstand `> 30 m` -> **rot**;
- der automatisch berechnete KM bleibt grundsätzlich bis einschließlich 30 m aktiv;
- GPS-Genauigkeit wird zusätzlich berücksichtigt, darf einen Treffer aber niemals künstlich verbessern;
- ein ansonsten grüner Treffer wird bei gemeldeter horizontaler GPS-Ungenauigkeit über 10 m auf gelb herabgestuft;
- ein Achsabstand über 30 m bleibt immer rot.

Korrektur vom 16.09.2026:
Die erste 200049-Fassung zog bis zu 10 m GPS-Ungenauigkeit vom Achsabstand ab. Dadurch hätte z. B. ein real berechneter Abstand von 35 m auf gelb verbessert werden können. Das widersprach der festen 30-m-Regel und wurde in `FotoKmQualitaet.kt` korrigiert. Der neue Unit-Test prüft 10/30-m-Grenzen sowie, dass schlechte GPS-Genauigkeit keinen Abstand verbessert.

### Kamera -> Foto-Maske
Der generierte 200049-Quellhook in `app/build.gradle.kts` erweitert den vorhandenen Pfad, ohne eine Parallelstruktur zu bauen:
- Kamera reicht die horizontale GPS-Genauigkeit an `FotoErfassenFragment` weiter;
- Kamera und Foto-Maske verwenden dieselbe `FotoKmQualitaet`;
- gelber Bereich behält den automatisch berechneten KM;
- roter Bereich fordert zur Prüfung/manuellen Eingabe auf;
- GVP-/Mast-/AP-/KOR-Berechnung wird nicht verändert.

### Baustellen-/Streckenanzeige im Fotopfad
Neue gemeinsame Hilfslogik:
- `app/src/main/java/de/tbhelfer/ui/kamera/FotoStreckenAnzeige.kt`

Ziel/Umsetzung:
- Hauptbaustellenname und Strecke/Gleis werden für Foto-Maske, gespeicherte Metadaten und Overlay aus derselben Logik gebildet;
- bei freier Strecke wird eine vorhandene fachliche Bezeichnung wie `5362-1` bevorzugt;
- `Strecke/Gleis` ersetzt die missverständliche Anzeige `Strecke/Bahnhof` im betroffenen Fotopfad;
- falsche Hilfsnamen wie `Umbau Heideck` sollen nicht anstelle der Hauptbaustelle erscheinen, wenn der Hauptbaustellenname am Knoten verfügbar ist;
- keine zweite Overlay-Lösung.

### KM im eingebrannten JPG größer
Der vorhandene `OverlayRenderer` bleibt der einzige Renderpfad.

200049 ergänzt dort nur:
- KM-Zeile ca. 1,55× größer;
- KM-Zeile fett;
- GPS bleibt wie bisher kleiner/ruhiger;
- vorhandene Overlay-Farbe wird auch auf die KM-Spezialschrift angewendet.

Praktisch zu vergleichen:
`Live-Kamera -> Foto erfassen -> eingebranntes JPG -> Foto-Detail -> PDF`.
KM, GPS, Baustelle, Strecke/Gleis und Firma müssen in allen Stufen zueinander passen.

## Teststatus Android 200049
### Statisch geprüft
- `Bilder` ist als neutraler Button derselben AlertDialog-Buttonleiste implementiert.
- Der bestehende positive Dialogbutton behält seinen eigenen `OnShowListener`; es wurde kein konkurrierender zweiter Listener eingeführt.
- 200049-Kamera-/Foto-Patchanker sind im aktuellen Quellstand vorhanden.
- `FotoErfassenFragment` besitzt die vom Patch verwendeten Imports/Funktionen (`View`, `Color`, `Locale`, `themeColor`).
- `TrassenNaheBerechner.Ergebnis` unterstützt `copy(qualitaet = ...)` und die verwendeten Qualitätswerte.
- neue 10/30-m-Grenztests sind versioniert.

### Lokale Buildversuche vom 16.09.2026
1. Erster Windows-/Android-Studio-Build von 200049 erreichte `:app:compileDebugKotlin` und scheiterte mit genau einem Fehler im generierten `FotoErfassenFragment`: `Unresolved reference 'colorError'`.
2. Ein zunächst versuchter Kompatibilitätsalias unter `app/src/main/java/com/google/android/material/R/attr/` war technisch falsch: der neue Kotlin-Packagepfad überschattete den echten Material-`R.attr`-Namensraum. Dadurch erschienen beim nächsten Build 12 Folgefehler für zuvor gültige Attribute wie `colorSurface`, `colorOnSurface` und `materialButtonStyle`.
3. Dieser Alias wurde vollständig wieder entfernt (`89230b953221fa5591ccef3e8a81b04ac6e75101`).
4. Der 200049-Generator in `app/build.gradle.kts` verwendet für die rote Warnfarbe jetzt direkt `android.R.attr.colorError`. Das Android-Attribut ist ab API 23 verfügbar und passt damit zum `minSdk = 23`; der echte Material-`R.attr`-Namensraum bleibt unangetastet.
5. Nach diesem Fix ist ein erneuter lokaler `Clean Project -> Build Project` erforderlich. Das Ergebnis steht noch aus.

GitHub Actions ist derzeit weiterhin kein verlässlicher Compilerindikator: aktuelle und bereits vorherige PR-Runs enden mit `failure`, aber der Build-Job besitzt **keine ausgeführten Steps und keine verwertbaren Logs**. Dieses Verhalten trat bereits vor den 200049-Änderungen auf. Der maßgebliche Buildtest bleibt deshalb Android Studio auf dem Windows-PC.

### Nächster lokaler Build-/Gerätetest
1. GitHub Desktop `Fetch origin -> Pull origin`.
2. Android Studio `Clean Project`.
3. Unit-Tests + Debug-Build ausführen.
4. Poco/Pixel installieren, App-Daten nicht unnötig löschen.
5. Kamera auf passender Strecke öffnen und Live-KM/GPS bei Bewegung prüfen.
6. Abstand/Ampel plausibilisieren: <=10 m grün, 10–30 m gelb, >30 m rot.
7. Foto speichern und Foto-Maske prüfen: aktueller KM bleibt bis 30 m automatisch übernommen.
8. Eingebranntes JPG öffnen: KM größer/fett; GPS und Strecke/Gleis korrekt.
9. Foto-Detail und PDF kontrollieren.
10. BTB-Arbeitseintrag öffnen: `Bilder`, `Abbrechen`, `Hinzufügen` müssen in derselben Buttonleiste stehen; `Bilder` muss direkt den Bildwähler öffnen.
11. Tag- und Nachtansicht des Bildsymbols prüfen.

## Live-KM / Roh-KF – bestätigte Grundlage aus 200048
- rohe KF Version 7.0 / 7.0 R wird direkt gelesen;
- 102-Byte-Datensätze, Little Endian;
- Punktnummer, DB_REF-Rechts-/Hochwert, Höhe, Station/KM, PArt, Strecke, RiKz und Rohinformationen bleiben erhalten;
- `1e40`, Strecke `0000` und Station `0.0` erzeugen keine falsche KM;
- `KF_RAW` läuft über den bestehenden GVP/Festpunktpfad;
- GVP/Festpunkt-Kalibrierung läuft auch ohne GRA;
- vorhandene GVP-KM-Logik und manuelle 2-Punkt-Kalibrierung bleiben erhalten, keine Parallelberechnung.

Die GPS-Lage der TRA-Samplepunkte für DB_REF/Gauss-Krüger wurde korrigiert:
- 3.x Mio. Rechtswert -> GK-Streifen 3 / Mittelmeridian 9° / False Easting 3 500 000;
- 4.x Mio. -> Streifen 4 / 12° / 4 500 000;
- 5.x Mio. -> Streifen 5 / 15° / 5 500 000.

Der bestehende Kompatibilitätsaufruf `dbRef2016Zone4ZuWgs84OrNull(...)` erkennt intern den tatsächlichen Streifen. `Daten berechnen` kann vorhandene TRA-Samplepunkte neu mit GPS-Lage versehen; lokale Daten müssen dafür nicht pauschal gelöscht werden.

Der Live-KM wurde nach diesem Hotfix im Gerätetest wieder als funktionsfähig gemeldet. KM/Festpunkt-Logik deshalb nicht weiter verändern, solange kein reproduzierbarer Fehler auftritt.

## Bestätigter BTB-Kernstand
- stabile fachliche `btbId`; Datum/Uhrzeit sind Attribute, nicht Identität;
- mehrere BTBs desselben Teams am selben Kalendertag möglich;
- ein BTB darf über Mitternacht laufen;
- kanonischer Lesekontext: aktuelle Baustelle -> aktiver BTB-Pointer -> `btbId`;
- Nacht-BTB `23:59 -> 00:30` praktisch bestätigt;
- nach erfolgreichem Abschluss wird die aktive `btbId` gelöst;
- Reaktivierung nur bewusst über `BTB-Verlauf -> BTB bearbeiten`;
- erster erfolgreicher Abschluss ist unveränderliches Original;
- spätere fachliche Änderungen werden gegen dieses Original rot dargestellt;
- Kopfdaten werden im PDF nicht rot markiert;
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF;
- moderne BTB-Fotos strikt nach derselben `btbId`;
- Legacy-Zeitfenster nur für Altbestand ohne `btbId`;
- Foto-ID-Menge des ersten Abschlusses gehört zum unveränderlichen Original;
- Firmenlogo/Kontaktdaten/PDF-Vorschau aus dem bisherigen Stand praktisch bestätigt.

## BTB-Auto-Sync nach lokalem Speichern – umgesetzt 16.09.2026
Betroffene Dateien:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt`
- `server/checks/13_verify_android_btb_auto_sync_source.py`
- `.github/workflows/android.yml`

Umgesetzt:
- Nach jedem **erfolgreichen lokalen BTB-Schreibvorgang** wird der vorhandene `SofortSync` angestoßen; moderner `btbId`-Pfad und Legacy-BTB-Pfad sind abgedeckt.
- Die lokale JSON-Datei und beim modernen BTB auch der aktive Pointer werden zuerst geschrieben. Erst danach wird der Sync eingeplant.
- Der Sync-Trigger läuft über die bereits vorhandene WorkManager-/`SyncWorker`-Pipeline. Es gibt keinen zweiten BTB-Netzwerkpfad.
- `SofortSync` verwendet weiterhin Unique Work mit `ExistingWorkPolicy.KEEP` und `NetworkType.CONNECTED`: mehrere nahe Speicheraktionen werden zusammengeführt; ohne Netz bleibt der WorkManager-Auftrag bis zu einer passenden Verbindung stehen.
- Das Einplanen ist mit `runCatching` vom lokalen Speichern entkoppelt. Ein WorkManager-/Sync-Fehler darf einen bereits erfolgreich lokal gespeicherten BTB nicht nachträglich als fehlgeschlagen behandeln.
- `SyncWorker` respektiert weiterhin die Geräteeinstellungen. Bei explizit gewähltem Zeitplan `MANUELL` wird kein automatischer Upload erzwungen; WLAN/Mobilfunk-Regeln bleiben erhalten.
- Der bestehende vollständige Sync umfasst Team-BTB und `BtbTeamMediaSync`; der neue Hook startet deshalb auch den vorhandenen BTB-Bilder-Sync, ohne eine parallele Medienlösung zu bauen.
- Eingehende Server-Snapshots werden in `CollabLocalBridge` weiterhin direkt in die lokalen Zieldateien gespiegelt und rufen **nicht** `BautagebuchSpeicher.speichern(...)` auf. Damit entsteht kein Serverimport -> Auto-Sync -> Reupload-Kreis.

Tests/Absicherung:
- neuer statischer Source-Contract `13_verify_android_btb_auto_sync_source.py` prüft Import/Hook, Reihenfolge „lokal schreiben -> Sync“, modernen und Legacy-Pfad, WorkManager-KEEP/Netz-Constraint sowie die Loop-Sperre im Serverimport;
- der Check ist in `.github/workflows/android.yml` aufgenommen;
- Android-Compile und echter Zwei-Geräte-Test bleiben erforderlich, weil GitHub Actions im aktuellen Projektzustand bereits vor dieser Änderung unzuverlässig ohne verwertbare Build-Steps/Logs ausfiel.

Nächster Praxistest:
1. Poco und Pixel in derselben Baustelle/demselben TB-Team und mit aktivem Gerätesync verwenden.
2. Auf Poco einen offenen BTB mit stabiler `btbId` ändern und den automatischen lokalen Speicherstatus abwarten.
3. Ohne manuellen Sync prüfen, ob Pixel denselben BTB/`btbId` beim nächsten Serverlauf erhält; anschließend Gegenrichtung Pixel -> Poco prüfen.
4. Zusätzlich einmal offline speichern, danach Netz wiederherstellen und prüfen, dass der wartende WorkManager-Sync den Stand nachliefert.
5. Einen abgeschlossenen BTB prüfen: er bleibt im Verlauf; nur bewusstes `BTB bearbeiten` reaktiviert dieselbe `btbId`.

## Gradle-Hinweis / technische Schuld
- `verifyRoomGeneratedImplementation` schützt weiterhin davor, eine APK ohne `TBDatenbank_Impl` zu installieren.
- Der Task erzeugt lokal eine Configuration-Cache-Warnung wegen `Task.project` zur Ausführungszeit. Das ist getrennte technische Schuld und kein belegter Kamera-/KM-Fehler.
- Seit 200043 existiert für kleine BTB-/Import-Hooks eine generierte Source-Brücke. 200044 hält die kanonischen Room-Datenbankquellen separat für KSP sichtbar. Die Brücke soll später sauber zurückgebaut werden, aber nicht parallel zum laufenden 200049-Gerätetest.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- Secrets ausschließlich in `./server/.env`; keine Geheimwerte in GitHub/Doku/Logs.
- PostgreSQL/API/cloudflared zuletzt bestätigt gesund; Schema 10 bleibt produktiv.
- Schema 11 persönliche Nutzerkontakte: vorbereitet, nicht produktiv migriert.
- Schema 12 PDF-Logo-Layout: vorbereitet, nicht produktiv migriert.
- vor produktiven Schema-11-/Schema-12-Schritten frisches verifiziertes DB-Backup erstellen.

## Teams / Baustellen / Sync – nächste Schritte
1. ✅ **BTB lokal speichern -> vorhandenen Sofort-Sync anstoßen** ist umgesetzt; lokale Speicherung bleibt zuerst und Fehler im Sync blockieren sie nicht.
2. Poco/Pixel im selben TB-Team praktisch testen: offener BTB muss mit derselben `btbId` in beide Richtungen aktualisiert werden; zusätzlich Offline -> Netz wieder da testen.
3. Abgeschlossener BTB bleibt abgeschlossen und erscheint beim anderen Gerät im Verlauf; nur `BTB bearbeiten` reaktiviert exakt dieselbe `btbId`.
4. **Unterbaustellen-/Streckenstruktur synchronisieren.** Verantwortliche TBs erhalten die gemeinsame Baustellenstruktur, Helfer nur den freigegebenen Bereich. Beispiel: `5362-1` / `Durchlass` müssen auf dem Helfergerät entsprechend der Berechtigung verfügbar sein.
5. Danach Einstellungen systematisch auf Funktion, Doppelungen und Reihenfolge prüfen.
6. Danach weitere NAS-Sync-Blöcke für Mängel, Abnahmen, Streckendaten und Bilderlisten vervollständigen.

## Noch offene ältere Abschlussprüfungen
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren;
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren;
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen;
- Zwei-Geräte-/Mehrhelfer-Konflikttests;
- serverseitiges Entfernen/Verlassen;
- später NAS-Sync für Mängel, Abnahmen, Streckendaten und Bilderlisten vervollständigen.
