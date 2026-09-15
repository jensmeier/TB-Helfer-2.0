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
- Android **200045**: kompakter Bildwähler im BTB-Arbeitseintrag funktioniert praktisch.
- Android **200046** konnte auf Windows nicht gebaut werden, weil der Build-Source-Patch für `KF_RAW` durch CRLF/LF-Zeilenumbrüche seinen mehrzeiligen Anker nicht fand.
- aktueller Buildkandidat: Android **200047**.

## Normaler Entwicklungsablauf
Source of Truth ist GitHub.

Windows-PC:
1. GitHub Desktop `Fetch origin`
2. `Pull origin`
3. Android Studio öffnen/builden/testen

Die alten `.github/scripts/patch_200035...200038*.ps1` sind nur historische Hilfsmittel und nicht mehr der normale Entwicklungsweg.

## Android 200047 – Buildfix + BTB Hauptbaustelle + Mitternacht
### Gemeldete Fehler
Beim Gerätetest wurden drei konkrete Probleme sichtbar:
1. Build 200046 brach in `:app:generateBtb200043Sources` ab mit `Android 200046: KF_RAW-Importanker wurde nicht gefunden`.
2. Ein manueller Arbeitseintrag für Firma `bbg` in `Hauptbaustelle` erschien anschließend nicht zuverlässig am richtigen Ort; Unterbaustellen-/Fotoeinträge funktionierten dagegen.
3. Bei Nacht-BTBs besteht ein echter Kontextfehler über Mitternacht: Beim normalen Öffnen suchte die BTB-Seite erneut nur am aktuellen Kalendertag nach `TAG/NACHT`, statt den bereits aktiven BTB über den Pointer/`btbId` zu laden.

### Ursache Build 200046
Die Build-Source-Brücke arbeitet mit exakten mehrzeiligen Stringankern. Unter Windows konnte `ImportExportFragment.kt` mit CRLF ausgecheckt sein, während der Gradle-Anker LF enthielt. Fachlich identischer Quelltext wurde dadurch als „Anker nicht gefunden“ bewertet.

### Fix Build 200047
- generierte Quellpatches normalisieren gelesene Dateien intern von CRLF auf LF;
- dadurch ist der vorhandene `KF_RAW -> importiereZipGvpDatei(...)`-Patch nicht mehr vom Windows-Zeilenumbruch abhängig;
- `versionCode = 200047`, `versionName = 1.3`;
- Room/KSP-Guard aus 200044 bleibt erhalten.

### Ursache `bbg / Hauptbaustelle`
Im Abschnittseditor werden technische Ortsmarker intern als `@ORT=<id>` gespeichert. Für eine Unterbaustelle wurde ein Inline-Marker geschrieben; für `Hauptbaustelle` mit Orts-ID `0` wurde bisher **kein** Marker geschrieben.

Der Parser hielt gleichzeitig die zuletzt gelesene Orts-ID fest. Damit konnte z. B. ein vorheriges `@ORT=<Schacht1>` auf die nachfolgende unmarkierte `bbg`-Hauptbaustellenzeile weiterwirken. Das erklärt, warum der Text scheinbar verschwand oder unter einer anderen Ortsgruppe/PDF-Struktur landete.

### Fix Hauptbaustelle 200047
- Firmenzeilen erhalten künftig auch für die Hauptbaustelle explizit `@ORT=0`;
- moderner Inline-`@ORT` gilt beim Parsen nur noch für **diese eine Zeile**;
- historische eigene Markerzeilen `@ORT=<id>` bleiben weiterhin als Legacy-Gruppenmarker unterstützt;
- dadurch können auch bereits gespeicherte moderne Hauptbaustellenzeilen ohne Marker wieder korrekt als Hauptbaustelle interpretiert werden, wenn davor nur ein moderner Inline-Unterbaustellenmarker stand;
- `@ALLGEMEIN` bleibt separat und unverändert.

### Ursache 23:59 / 00:00
Die eigentliche Schichtzeitrechnung kann einen normalen Nachtbereich wie `18:00–02:30` bzw. `23:59–00:00` bereits über den Folgetag abbilden. Der Fehler lag stattdessen beim **Laden des aktiven BTB**:
- beim Öffnen der BTB-Seite wurde standardmäßig `selectedDate = LocalDate.now()` verwendet;
- anschließend wurden für „heute“ TAG und NACHT gesucht und der zuletzt geänderte Eintrag gewählt;
- ein aktiver Nacht-BTB, der am Vortag begonnen hatte und nach 00:00 noch lief, konnte dadurch aus dem sichtbaren Kontext fallen;
- das widersprach der Projektregel `aktuelle Baustelle -> aktiver BTB-Pointer -> btbId`.

### Fix Mitternacht 200047
- beim normalen Öffnen wird jetzt zuerst `BtbActiveContext` verwendet;
- damit entscheidet die aktive `btbId`, nicht der neue Kalendertag;
- ein Nacht-BTB vom 15.09. bleibt nach 00:00 am 16.09. derselbe BTB;
- beim Laden wird `selectedDate` wieder aus `entry.datumIso` des aktiven BTB gesetzt, bevor weitere Speicherung/Autosave erfolgt;
- damit kann derselbe `btbId` nicht nur wegen Mitternacht versehentlich im Folgetagskontext weitergebaut werden;
- nach bewusstem Abschluss und gelöstem Aktiv-Pointer darf das normale Öffnen nicht automatisch irgendeinen heutigen modernen BTB als aktiven Bericht auswählen.

### Betroffene Datei 200047
- `app/build.gradle.kts`
- diese Statusdatei

Hinweis: Wegen der seit 200043 vorhandenen Build-Source-Brücke werden die kleinen Änderungen am sehr großen `BautagebuchFragment.kt` weiterhin beim Build in die generierte Quellkopie eingesetzt. Diese Brücke ist technische Schuld und soll später sauber zurückgebaut werden; sie wird nicht parallel zu diesem Fehlerblock groß umgebaut.

### Teststatus 200047
- Projektregeln, aktueller Stand, Feature-Branch, Draft-PR #5 und Branch-Head vor Änderung geprüft.
- Screenshots des Gerätetests ausgewertet; Hauptbaustellen-/Unterbaustellen-Gruppierung und Abschluss-/Nachbearbeitungs-PDF verglichen.
- Quellcode geprüft: `buildManualLine()` schrieb für Firmen-Hauptbaustelle bisher keinen `@ORT=0`-Marker; `parseManualItems()` ließ Inline-Ortszustand über Zeilen weiterlaufen.
- Quellcode geprüft: `ladeEintragFallsVorhanden(autoPickSchichtForToday = true)` suchte bisher am aktuellen Kalendertag TAG/NACHT statt zuerst den aktiven `btbId`-Kontext zu verwenden.
- Quellcode geprüft: das Foto-/Schicht-Zeitfenster behandelt Ende < Start bereits als Folgetag; `23:59 -> 00:00` ist dort grundsätzlich ein Mitternachtsfenster.
- Build-Patches besitzen jetzt zusätzliche `check(...)`-Guards für Hauptbaustellenmarker, Ortsparser, Aktiv-BTB-Ladeweg und BTB-Datum.
- GitHub Actions Run zum Code-Commit lief erneut als `failure`, liefert aber weiterhin keine verwertbaren Job-Logs (`BlobNotFound`); deshalb bleibt der lokale Android-Studio-Build der maßgebliche nächste Test.
- lokaler Windows-/Android-Studio-Build 200047 und Gerätetest stehen noch aus.

### Nächster Handtest 200047
1. GitHub Desktop `Fetch origin` -> `Pull origin`.
2. Android Studio `Build -> Clean Project`, danach Build/Run.
3. Prüfen, dass **200047** gebaut/installiert wird.
4. BTB-Arbeiten: Firma `bbg` -> `Hauptbaustelle` -> eindeutigen Text eingeben; danach zusätzlich einen anderen Text in `Schacht1`/Unterbaustelle eingeben. Beide müssen gleichzeitig in der richtigen Ortsgruppe bleiben.
5. PDF-Entwurf prüfen: Hauptbaustellen- und Unterbaustellentext dürfen nicht gegenseitig verschwinden/verschoben werden.
6. Nacht-BTB mit Start am Vortag und Ende nach 00:00 öffnen bzw. App/BTB-Seite nach Mitternacht erneut öffnen: es muss dieselbe `btbId`/derselbe Bericht mit vollständigen Daten bleiben.
7. Nach `BTB Abschluss` prüfen: ohne bewusstes `BTB bearbeiten` aus dem Verlauf darf kein abgeschlossener BTB automatisch als aktiver Entwurf wieder auftauchen.
8. Danach denselben Vermessungs-ZIP/KF-Bestand importieren und den 200046-Roh-KF-Test fortsetzen.

## Android 200046 – KM/Festpunkte: Roh-KF direkt importieren
### Gefundene Ursache
Der ZIP-Scanner erkannte rohe `.kf` bereits als `KF_RAW`, der eigentliche Importpfad importierte sie aber nicht und meldete nur `nicht direkt importiert: KF-Original erkannt; falls vorhanden wird KF.TXT verwendet`.

Damit fehlten bei Beständen, deren belastbare Strecke/Station im binären KF-Original steckt, wichtige Festpunkte als KM-Quelle.

### Umsetzung
`GvpDateiParser` kann rohe KF 7.0 / 7.0 R direkt lesen:
- 102-Byte-Datensätze, Little-Endian-Doubles;
- KF-Punktnummer, Rechtswert, Hochwert, Höhe, Station, Lagebezeichnung, Zusatzfeld, PArt, VArt, Statusbytes, Metadaten, Strecke und RiKz;
- `abs(Wert) >= 1e30` als fehlend;
- Strecke `0000` nicht als reale Strecke;
- Station `0.0` nicht als belastbare KM-Quelle;
- Original-PArt bleibt erhalten (`PS0` bis `PS4`, `GVPV`, `GPSC`, `HBH`, `PPB`, ...);
- vollständiger Rohdatensatz zusätzlich verlustarm im `rawLine` erhalten;
- Zone-4-Bestand nutzt vorhandenen DB_REF-WGS84-Wandler;
- 5362/GK-Streifen 3 wird nicht fälschlich als Zone 4 umgerechnet; Rohkoordinaten bleiben für TRA/GVP-KM-Zuordnung erhalten;
- `KF_RAW` nutzt den bestehenden GVP/Festpunkt-Speicherpfad und die vorhandene GVP-KM-Kalibrierung.

### Tests 200046
`GvpDateiParserRawKfTest.kt` enthält synthetische 102-Byte-KF-Datensätze für:
1. 5362 / PS1 mit echter Station/Strecke;
2. `VERSION 7.0 R` / GVPV 5300;
3. Harburg Station `0.0` -> keine KM-Quelle;
4. Pasing `1e40` + Strecke `0000` -> fehlend statt falscher KM.

Der praktische Test war durch den oben beschriebenen Windows-Buildankerfehler blockiert und wird mit 200047 fortgesetzt.

## Android 200045 – kompakter Bildwähler
- zwei große Foto-Schaltflächen im Arbeitseintragsdialog entfernt;
- ein kompaktes Bild-/Galerie-Symbol öffnet den vorhandenen Android-Bildauswahlpfad;
- bestehende Zuordnung über stabile `btbId`, Firma, Unterbaustelle/Ort, Arbeitstext und KM bleibt erhalten;
- praktische Bildauswahl auf dem Gerät bestätigt.

## Android 200043/200044 – Room/KSP
200043 führte die generierte BTB-Quellkopie ein und verursachte dadurch zunächst fehlendes `TBDatenbank_Impl`.

200044 korrigierte:
- `src/main/java/de/tbhelfer/datenbank` bleibt kanonischer Kotlin-Source-Ordner für KSP;
- Datenbankordner wird aus der generierten Kopie entfernt, damit keine Doppelklassen entstehen;
- `verifyRoomGeneratedImplementation` prüft nach Kotlin-Compile, dass `TBDatenbank_Impl` erzeugt wurde.

Praktisch bestätigt: 200044 startete wieder auf dem Poco.

## Bestätigte BTB-Kernregeln
- stabile fachliche `btbId`; Datum/Uhrzeit sind Attribute, nicht Identität;
- mehrere BTBs desselben Teams am selben Kalendertag möglich;
- ein BTB darf über Mitternacht laufen, ohne dass automatisch ein neuer BTB entsteht;
- kanonischer aktueller Lesekontext: aktuelle Baustelle -> aktiver BTB-Pointer -> `btbId`;
- nach erfolgreichem Abschluss wird aktive `btbId` gelöst; Reaktivierung nur bewusst über Verlauf `BTB bearbeiten`;
- erster erfolgreicher Abschluss ist unveränderliches Original;
- spätere fachliche Änderungen werden gegen dieses Original rot dargestellt; Rückkehr zum Original wieder schwarz;
- Kopfdaten werden im PDF nicht rot markiert;
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF;
- moderne BTB-Fotos strikt nach derselben `btbId`; Legacy-Zeitfenster nur für Altbestand ohne `btbId`;
- Foto-ID-Menge des ersten Abschlusses gehört zum unveränderlichen Original;
- gleiche Foto-Texte im selben Firmen-/Ortskontext können gruppiert werden.

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

## Danach geplante Arbeiten
Nach erfolgreichem 200047-/KM-Test:
1. Foto-Overlay praktisch prüfen und ggf. optisch verbessern.
2. Einstellungen systematisch auf Funktion, Doppelungen und sinnvolle Reihenfolge prüfen/aufräumen.
3. Wetter um Niederschlagsmenge in mm bzw. l/m² ergänzen; nach erstem Abschluss keine automatische Wetteränderung.
4. Mängelaufnahme vereinfachen: `Was? -> Wo/KM? -> Beschreibung`, aktueller KM als Vorschlag.

## Noch offene Abschlussprüfungen
- neues Abschluss-PDF mit gruppierten gleichnamigen Fotos nochmals kontrollieren;
- Rot-Markierung bei gemischter Original/Nachtrag-Fotogruppe kontrollieren;
- Zeitraum-ZIP und Gesamt-Archiv nach aktuellem Stand nochmals auf PDF/DOCX/Bilder prüfen.
