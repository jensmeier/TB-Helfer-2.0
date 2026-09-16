# TB-Helfer – aktueller Projektstand

Stand: 2026-09-16

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen in `docs/TB-HELFER_PROJEKTREGELN.md`, die Arbeitsweise in `docs/TB-HELFER_PROJEKTANWEISUNG.md`, die geplante Reihenfolge in `docs/TB-HELFER_ROADMAP.md`.

## Start jedes Chats / jeder Änderung

Zuerst prüfen/lesen:
1. `docs/TB-HELFER_PROJEKTANWEISUNG.md`
2. `docs/TB-HELFER_PROJEKTREGELN.md`
3. diese Datei
4. aktiven Feature-Branch
5. offenen Draft-PR
6. neuesten relevanten Commit
7. Chat-Übergabe nur ergänzend

GitHub-Code und aktive Branches sind Source of Truth. `main` stabil halten.

## Repository / Branch / PR

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiver Feature-Branch: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft/mergeable
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`
- aktueller Android-Buildkandidat: **200049**
- letzter fachlicher Code-Commit: `d878c715a3534fb542546b4d4d4e039f47dc37fc` – `feat: sync BTB automatically after local save`
- danach Dokumentation: Projektanweisung und Android-first-Roadmap
- Roadmap-Commit vor diesem Status-Update: `0cffea87604ba3a78227f597a0a731fc7af7ce2b`
- Produktivserver/NAS unverändert auf API **0.7.0**, PostgreSQL **Schema 10**

## Verbindliche Entwicklungsstrategie

Ab jetzt gilt die Reihenfolge aus `docs/TB-HELFER_ROADMAP.md`:

**Android zuerst stabil und vollständig machen, danach produktiven Server/NAS ausbauen.**

Nur wenn Android zwingend einen noch fehlenden Serververtrag benötigt, wird die Android-Seite vorbereitet und die produktive Serveränderung explizit in die spätere Serverphase verschoben. Keine produktive Migration nur für einen Zwischenstand.

## Android 200049 – aktueller Praxisstand

### Lokaler Build

Der lokale Android-Studio-Build von 200049 war nach dem endgültigen `colorError`-Fix erfolgreich. Die vorherigen Material-`R.attr`-Folgefehler kamen von einem falschen Kompatibilitätsalias; dieser Alias wurde vollständig entfernt. Der Generator verwendet für die rote Warnfarbe direkt `android.R.attr.colorError`.

**Wichtig:** Nach den danach hinzugekommenen Commits für horizontalen BTB-Button und BTB-Auto-Sync ist ein neuer lokaler `Fetch -> Pull -> Clean -> Build` erforderlich.

### Praktischer Gerätetest 16.09.2026

Vom Benutzer bestätigt:
- Live-KM funktioniert;
- Live-KM ist größer/fett;
- Foto-Erfassen-Maske mit KM/GPS/Strecke passt;
- 10/30-m-Ampel passt;
- automatischer KM bleibt im gelben Bereich erhalten;
- Baustellen-/Strecke-Gleis-Anzeige in der Foto-Maske war passend.

Nicht passend / offen:
- gespeichertes JPG hatte keinen eingebrannten Daten-Aufdruck;
- Ursache ist sehr wahrscheinlich zuerst die Einstellungsseite: Foto-Overlay-/Aufdruck-Schalter springen nach `Zurück` und erneutem Öffnen wieder auf `aus`;
- die Einstellungen müssen deshalb vor Änderungen am Renderer systematisch repariert werden.

BTB-Arbeitseintrag:
- `Bilder` funktionierte und öffnete den gewünschten Bildpfad;
- im ersten Gerätetest wurden `Bilder`, `Abbrechen`, `Hinzufügen` wegen Android-ButtonBar-Stacking noch vertikal dargestellt;
- Fix `188230491dc6ec8a3250500491aa61ca02a95b56` erzwingt die horizontale Aktionsleiste;
- dieser horizontale Fix ist noch praktisch auf dem Gerät nachzutesten.

## Kamera / KM / Vermessung – stabile Grundlage

200048/200049 Grundlage beibehalten, solange kein reproduzierbarer Fehler auftritt:
- rohe KF 7.0 / 7.0 R direkt lesbar;
- 102-Byte-Datensätze, Little Endian;
- Punktnummer, DB_REF, Höhe, Station/KM, PArt, Strecke und Rohinfos bleiben erhalten;
- `KF_RAW` läuft durch den bestehenden GVP/Festpunktpfad;
- GVP/Festpunkt-Kalibrierung funktioniert auch ohne GRA;
- keine zweite KM-Berechnung neben der bestehenden Logik;
- manuelle 2-Punkt-Kalibrierung bleibt erhalten;
- GK-Streifen 3/4/5 werden für DB_REF-GPS-Lage erkannt;
- `Daten berechnen` kann vorhandene TRA-Samplepunkte neu mit GPS-Lage versehen.

Verbindliche Kamera-KM-Qualität:
- `<= 10 m` grün;
- `> 10 m bis <= 30 m` gelb;
- `> 30 m` rot;
- GPS-Ungenauigkeit darf nur verschlechtern, nie verbessern;
- schlechte GPS-Genauigkeit kann grün auf gelb herabstufen;
- >30 m bleibt immer rot.

Unit-Test `FotoKmQualitaetTest` deckt die Grenzwerte und GPS-Abwertung ab.

## Foto-/Overlay-Pfad

Vorhandener `OverlayRenderer` bleibt der einzige Renderpfad. Keine zweite Overlay-Lösung bauen.

200049 enthält bereits:
- größere/fette KM-Zeile im JPG-Overlay;
- konsistente Hauptbaustellen-/Strecke-Gleis-Hilfslogik;
- Zielanzeige wie `Strecke/Gleis: 5362-1` statt unpassendem `Strecke/Bahnhof: -`, wenn entsprechende Struktur vorhanden ist.

Aktuell blockiert die fehlerhafte Speicherung der Foto-Aufdruck-Einstellungen die praktische Prüfung des eingebrannten JPGs.

## Einstellungen – bestätigter nächster Android-Fehlerblock

Die aktuelle Hauptstruktur ist noch alt (`Nutzer/Firma`, `Synchronisation`, `Benachrichtigungen`, `Anzeige & Overlay`, `Kamera/Foto`, `BTB/PDF`, eigenes Widget, Wartung/Diagnose).

Bestätigter Fehler:
- Foto-Aufdruck-/Overlay-Schalter bleiben nach Verlassen der Einstellungsseite nicht zuverlässig gespeichert.

Nächster Einstellungsblock laut Roadmap:
- jede Einstellung auf Laden/Speichern/Neustart/Wirkung prüfen;
- Listener-/Initialisierungsfehler beheben;
- doppelte/tote Einstellungen entfernen;
- Hauptstruktur neu ordnen in:
  1. Profil & Firma
  2. Anzeige
  3. Kamera & Fotos
  4. BTB & PDF
  5. Synchronisation
  6. Benachrichtigungen
  7. Wartung & Diagnose
- Widget unter Anzeige;
- `Overlay` im Benutzer-UI als `Foto-Aufdruck` verständlicher benennen.

Gerätespezifisch bleiben u. a. Theme, Widget, Fotoqualität, Foto-Aufdruck und Anzeigeoptionen. Firmenstammdaten/Logo, Identität, Baustellen-/Teamdaten und später firmenweites PDF-Layout sind Serverdaten.

## BTB-Kernstand – verbindlich

- BTB ist **nicht baustellenweit gemeinsam**;
- jeder verantwortliche TB besitzt mit seinen Helfern seinen eigenen gemeinsamen BTB-Bereich;
- andere verantwortliche TB-Teams auf derselben Baustelle bleiben getrennt;
- stabile dauerhafte `btbId` ist die Identität;
- Datum/Uhrzeit/Tag/Nacht sind Attribute;
- mehrere BTBs pro Kalendertag erlaubt;
- BTB darf über Mitternacht laufen;
- kanonischer Kontext: aktuelle Baustelle -> aktiver BTB-Pointer -> `btbId`;
- nach erfolgreichem Abschluss aktive `btbId` lösen;
- Reaktivierung nur bewusst über `BTB-Verlauf -> BTB bearbeiten`;
- erster erfolgreicher Abschluss ist unveränderliches Original;
- spätere fachliche Änderungen werden immer gegen dieses Original verglichen und rot dargestellt;
- Kopfdaten bleiben im PDF schwarz;
- moderne Fotos gehören nur bei exakt gleicher `btbId` zum modernen BTB;
- allgemeines Baustellenfoto ohne `btbId` darf nicht über Zeitfenster hineinrutschen;
- Legacy-Zeitfenster nur für historische Daten ohne `btbId`.

## BTB-Auto-Sync – gebaut, Praxistest offen

Commit: `d878c715a3534fb542546b4d4d4e039f47dc37fc`.

Umgesetzt:
- nach erfolgreichem lokalen BTB-Schreiben wird der vorhandene `SofortSync` angestoßen;
- moderner `btbId`-Pfad und Legacy-Pfad abgedeckt;
- lokale Datei und aktiver Pointer werden zuerst geschrieben;
- Sync-Trigger ist vom lokalen Speichererfolg entkoppelt;
- vorhandene WorkManager-/`SyncWorker`-/Team-BTB-/`BtbTeamMediaSync`-Pipeline wird wiederverwendet;
- kein zweiter Netzwerkpfad;
- `ExistingWorkPolicy.KEEP` bündelt nahe Trigger;
- Netzwerk-Constraint bleibt bestehen;
- Serverimport über `CollabLocalBridge` ruft den lokalen Speicherhook nicht auf und erzeugt daher keinen Import->Upload-Kreis;
- Source-Contract `server/checks/13_verify_android_btb_auto_sync_source.py` ist in Android CI eingebunden.

Noch praktisch testen:
1. aktueller Branch lokal bauen;
2. Poco -> offenen BTB ändern/speichern -> ohne manuellen Sync auf Pixel prüfen;
3. Pixel -> Poco;
4. offline speichern -> Netz wieder da -> Nachlieferung;
5. Abschluss/Verlauf/Reaktivierung prüfen;
6. mehrere TB-/Helfer-Konstellationen später als eigener Block.

Hinweis: Wenn die Sync-Einstellung ausdrücklich auf `Manuell` steht, erzwingt der automatische Worker keinen Upload.

## Baustellen-/Unterbaustellen-Sync – offen

Bekanntes Praxisproblem:
- Poco kann Strukturen/Unterbaustellen kennen, die Pixel noch nicht hat.

Ziel:
- Hauptbaustelle -> Unterbaustellen -> Strecke/Gleis/Objekte;
- Beispiele `5362-1`, `Durchlass`;
- verantwortlicher TB erhält vollständige gemeinsame Struktur;
- Helfer nur seinen freigegebenen Bereich;
- stabile IDs;
- keine Vermischung zwischen mehreren lokal vorhandenen Baustellen.

Android-Datenmodell/UI zuerst fertigstellen; fehlende Serververträge danach in der Serverphase ergänzen.

## PDF / Export – offene Prüfungen

Noch offen bzw. erneut zu kontrollieren:
- gleiche fachliche Fotos im PDF sinnvoll gruppieren;
- Original-/Nachtragsgruppen sauber trennen;
- neue/geänderte Fotozeilen und Anhang konsistent rot;
- entfernte Originalinhalte rot `(entfernt) ...`;
- Strukturüberschriften schwarz;
- pro `btbId` genau ein aktuelles Abschluss-PDF;
- Tagesabschluss-ZIP;
- Zeitraum-ZIP;
- Gesamtarchiv mit PDF/DOCX/Bildern;
- DOCX langfristig dieselbe Original-/Rotlogik wie PDF.

Weitere ältere Wünsche sind vollständig in `docs/TB-HELFER_ROADMAP.md` einsortiert, darunter TimePicker, Dunkelmodus-Kontrast im BTB-Startdialog, Abschluss-Hinweis, Spracheingabe, vereinfachte Mangelaufnahme, SKV/GPS, `Mein Ordner`, Windows/Laptop und Vermessungs-/Streckendaten-Ausbau.

## Produktiver Synology-/Serverstand – unverändert

- NAS/Synology ist Source of Truth;
- API produktiv: **0.7.0**;
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`;
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`;
- Secrets ausschließlich außerhalb von GitHub/Dokumentation;
- Schema 11 persönliche Nutzerkontakte vorbereitet, nicht produktiv;
- Schema 12 PDF-Logo-Layout vorbereitet, nicht produktiv;
- vor jeder produktiven Migration frisches verifiziertes Backup.

Produktive Serverarbeit wird nach der Android-Phase fortgesetzt; genaue Serverreihenfolge siehe Roadmap S0–S7.

## Technische Schuld / Buildsystem

- Seit 200043 existiert eine generierte Source-Brücke für mehrere Android-Patches; später sauber zurückbauen;
- `verifyRoomGeneratedImplementation` schützt vor APK ohne Room-Implementierung, erzeugt aber eine bekannte Configuration-Cache-Warnung;
- GitHub Actions ist derzeit kein verlässlicher Compilerindikator: aktuelle und frühere Runs enden teilweise mit `failure`, obwohl der Job keine ausgeführten Steps/keine verwertbaren Logs enthält;
- maßgeblicher Buildtest bleibt derzeit Android Studio auf dem Windows-PC;
- CI/Generated-Source-Bereinigung erfolgt erst nach funktionaler Stabilisierung.

## Nächster sinnvoller Arbeitsschritt

Roadmap **A0**:
1. GitHub Desktop `Fetch origin -> Pull origin`;
2. Android Studio `Clean Project -> Build`;
3. auf Poco/Pixel installieren, App-Daten nicht unnötig löschen;
4. horizontalen BTB-Button prüfen;
5. BTB-Auto-Sync Poco -> Pixel und Pixel -> Poco ohne manuellen Sync testen;
6. einmal offline speichern und nach Netzrückkehr prüfen;
7. Ergebnis hier dokumentieren.

Danach Roadmap **A1 Einstellungen reparieren/neu strukturieren**, anschließend **A2 Foto-Aufdruck/Fotos**.
