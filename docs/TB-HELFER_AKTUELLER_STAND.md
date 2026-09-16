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
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`
- aktueller Android-Buildkandidat: **200050**
- letzter fachlicher Code-/Check-Stand der 200050-Änderungen: `c576dd4151f1ff9eb39c167c468ed3000cc589bb`; danach nur Doku-Aktualisierungen
- Produktivserver/NAS unverändert auf API **0.7.0**, PostgreSQL **Schema 10**

## Verbindliche Entwicklungsstrategie

Ab jetzt gilt die Reihenfolge aus `docs/TB-HELFER_ROADMAP.md`:

**Android zuerst stabil und vollständig machen, danach produktiven Server/NAS ausbauen.**

Nur wenn Android zwingend einen noch fehlenden Serververtrag benötigt, wird die Android-Seite vorbereitet und die produktive Serveränderung explizit in die spätere Serverphase verschoben. Keine produktive Migration nur für einen Zwischenstand.

---

# Android 200050 – aktueller Kandidat

## Anlass / Benutzerwunsch

Gerätetest von 200049 auf dem Poco zeigte:
- Live-KM, Foto-Maske und 10/30-m-Ampel funktionieren;
- der direkte `Bilder`-Pfad im BTB-Punkt 4 funktioniert;
- die drei Dialogaktionen waren nach dem ersten Horizontal-Fix zwar in einer Zeile, auf dem schmalen Poco aber als `Bil… | Abbre… | Hinzu…` abgeschnitten;
- Punkte 5–8 sollen fachlich und optisch genauso wie Punkt 4 funktionieren;
- der gesamte gemeinsame BTB, einschließlich Punkte 5–8 und deren Bilder, soll zwischen verantwortlichem TB und Helfer synchronisiert werden;
- Firmenlogo im PDF soll unabhängig vom Logoformat immer auf weißem Hintergrund erscheinen;
- Helfergerät soll die freigegebene Baustellen-/Unterbaustellenstruktur des verantwortlichen TB erhalten, nicht dieselbe Struktur manuell neu anlegen müssen.

## BTB-Punkte 4–8 – gleicher Bilder-Pfad

Betroffene Dateien:
- `app/build.gradle.kts`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt`

Umgesetzt:
- der generierte BTB-Section-Hook aktiviert den direkten `Bilder`-Pfad jetzt für **jeden Abschnitt mit Bildtyp**, nicht mehr nur `FOTODOKUMENTATION`;
- damit verwenden die BTB-Punkte 4–8 denselben Dialog-/Galeriepfad:
  - 4 Ausgeführte Arbeiten -> `FOTODOKUMENTATION`
  - 5 Abnahmen -> `ABNAHMEN`
  - 6 Einweisung/Unterweisung -> `EINWEISUNG_UNTERWEISUNG`
  - 7 Besonderheiten -> `BESONDERHEITEN`
  - 8 Mängel/Schäden -> `MAENGEL_SCHAEDEN`
- `BtbEntryPhotoActionBinder.bind(...)` erhält nun den konkreten `Bildtyp` und reicht ihn als `filterBildtyp` an den vorhandenen Bildauswahl-/Fotoimportpfad weiter;
- es wurde **kein zweiter Foto- oder Overlaypfad** gebaut.

## Drei Dialogbuttons gleich groß und lesbar

Im gemeinsamen Eintragsdialog werden `Bilder`, `Abbrechen`, `Hinzufügen` jetzt:
- horizontal gehalten, Stacking abgeschaltet;
- auf dieselbe Höhe von 48 dp gesetzt;
- gleich breit über drei Gewichte verteilt;
- zentriert;
- mit kompaktem horizontalen Padding und 10,5-sp-Schrift dargestellt;
- einzeilig gehalten.

Ziel: auf dem Poco vollständige bzw. deutlich besser lesbare Beschriftung statt drei unterschiedlich breiter/trunkierter Buttons. Praktischer Gerätetest steht aus.

## Punkte 5–8 gehören bereits zum selben BTB-Snapshot

`BautagebuchSpeicher` serialisiert bereits gemeinsam im BTB-JSON:
- `abnahmen`
- `einweisungUnterweisung`
- `besonderheiten`
- `maengelSchaeden`

Der BTB-Auto-Sync ist deshalb **nicht nur ein Punkt-4-Sync**: Nach erfolgreichem lokalen Speichern wird der komplette BTB-Snapshot mit derselben stabilen `btbId` über den vorhandenen `SofortSync` angestoßen. Punkte 5–8 Text/Fachdaten laufen durch denselben Team-BTB-Sync.

## BTB-Bilder-Sync 200050 – moderne Fotos strikt nach `btbId`

Betroffene Dateien:
- `app/src/main/java/de/tbhelfer/datenbank/BtbTeamMediaSync.kt`
- `app/src/main/java/de/tbhelfer/datenbank/FotoRepository.kt`

Bei der Prüfung wurde ein älterer Medienpfad gefunden, der BTB-Fotos beim Upload noch über das Arbeitszeitfenster auswählte. Das widersprach der bereits verbindlichen modernen Regel `Foto -> exakt dieselbe btbId`.

Korrigiert:
- moderner BTB-Upload nimmt nur Foto-IDs aus `BtbFotoZuordnungSpeicher.fotoIdsFuerBtb(..., btbId)`;
- modern einem BTB zugeordnete Fotos dürfen nicht mehr in den Legacy-Zeitfenster-Fallback rutschen;
- nur echte Alt-BTBs ohne `btbId` dürfen weiterhin Datum/Schicht/Zeitfenster verwenden;
- Bildtyp wird im vorhandenen kompakten BTBP1-Metadatenformat übertragen und beim Import wieder hergestellt;
- ein auf dem Helfer/TB-Gerät importiertes modernes Serverfoto wird mit `einfuegenFuerBtb(..., parentKey)` wieder exakt derselben `btbId` zugeordnet;
- `FotoRepository` besitzt für den Hintergrund-Sync jetzt `fotosFuerBaustelleSyncOnce(...)`, bewusst **ohne** den UI-spezifischen `BtbFotoQueryScope`;
- dadurch kann der Hintergrund-Sync alle BTBs einer Baustelle verarbeiten, auch wenn im UI gerade nur ein anderer BTB geöffnet ist.

Noch nicht praktisch bestätigt:
- Poco -> Pixel / Pixel -> Poco für jeden Bildtyp 4–8;
- Offline -> Netz wieder da;
- zwei überlappende moderne BTBs dürfen keine Bilder vermischen.

## Source-Contract 200050

Neu/erweitert:
- `server/checks/14_verify_android_btb_sections_logo_source.py`
- `.github/workflows/android.yml`

Der statische Check sichert u. a.:
- gemeinsamer Bilder-Pfad für alle Bildtypen 4–8;
- Weitergabe des konkreten Bildtyps;
- gleich breite Dreier-Buttonleiste;
- Serialisierung der vier zusätzlichen BTB-Felder;
- moderner Medienupload strikt nach `btbId`;
- Legacy-Fallback schließt moderne Fotos aus;
- importierte moderne Fotos erhalten wieder dieselbe `btbId`;
- Hintergrund-Sync verwendet einen ungescopten Foto-Leseweg;
- weißer PDF-Hintergrund wird vor dem Logo-Bitmap gezeichnet.

Android-Compile und Gerätetest bleiben erforderlich.

---

# Firmenlogo / PDF

## Android-PDF

Betroffene Datei:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfHeaderRenderer.kt`

200050 zeichnet vor dem Firmenlogo ausdrücklich ein weißes Rechteck in exakt der Logo-Zielgröße. Damit funktionieren auch transparente Logos unabhängig vom PDF-Viewer zuverlässig auf weißem Hintergrund.

## Empfohlenes Logoformat

Für den Server wird **PNG** als Standard empfohlen:
- Rasterbild, von Androids aktuellem `BitmapFactory`-Pfad direkt lesbar;
- transparente PNGs sind erlaubt;
- PDF bekommt trotzdem bewusst einen weißen Hintergrund;
- sinnvoll ist ein sauberes, ausreichend hoch aufgelöstes Firmenlogo, grob etwa 1000–2000 px breit; unnötig riesige Dateien vermeiden.

Unterstützt sind im bestehenden Android-Downloadpfad außerdem:
- `image/jpeg` / JPG
- `image/webp`

**SVG ist im aktuellen Android-PDF-Pfad nicht als Server-Logoformat vorgesehen**, weil der bestehende Renderer ein Bitmap über `BitmapFactory.decodeFile(...)` liest.

## Was auf den Server gehört

Das produktive Schema 10 besitzt bereits `tb.company_profiles` mit Logo-Metadaten:
- `logo_storage_path`
- `logo_mime_type`
- `logo_sha256`
- `logo_version`
- `profile_version`

Fachliches Ziel:
- Original-Logo als Datei/Objekt auf dem Server speichern, vorzugsweise PNG;
- PostgreSQL speichert Pfad, MIME-Typ, Hash und Version, nicht zwingend das Bild als BLOB;
- Geräte derselben Firma laden/cachen dieselbe Datei;
- BTB/PDF verwendet den lokalen Cache;
- Schema 12 betrifft später das **firmenweite Layout** des Logos/PDF-Kopfs, nicht die grundlegende Logo-Datei.

Für den 200050-PDF-Fix ist **keine produktive Servermigration erforderlich**.

---

# Baustelle / Unterbaustellen auf Helfergerät

Aktuelles Praxisproblem:
- verantwortlicher TB/Poco kennt z. B. `5362-1` und `Durchlass`;
- Helfer/Pixel kennt teilweise nur die Hauptbaustelle.

Das ist **kein versteckter Server-Schalter**, der nur aktiviert werden müsste.

Verbindliches Zielmodell aus den Projektregeln:
- verantwortlicher TB legt/verbinden die Baustelle und gemeinsame Struktur;
- weiterer TB/Helfer tritt über Einladung/QR bei;
- verantwortliche TBs erhalten die für sie vorgesehene gemeinsame Struktur;
- Helfer erhält automatisch nur den freigegebenen Teilbaum;
- Helfer soll gemeinsame Unterbaustellen **nicht manuell neu anlegen müssen**;
- stabile IDs statt bloßer sichtbarer Namen.

Schema 10 und Team-/Einladungslogik besitzen bereits Gruppen-/Berechtigungsgrundlagen einschließlich `area_scope`, aber die vollständige Synchronisation des lokalen `BaustellenKnoten`-Baums (Hauptbaustelle -> Unterbaustelle -> Strecke/Gleis -> Objekte) ist noch nicht fertig.

Roadmap:
- Android-Datenmodell/Importdarstellung im Block A4 fertigstellen;
- fehlenden Serververtrag danach in der Serverphase S2 ergänzen;
- produktiven Server jetzt nicht nur für diesen Zwischenstand migrieren.

---

# Android 200049 – bestätigte Grundlage

Der lokale Android-Studio-Build von 200049 war nach dem endgültigen `colorError`-Fix erfolgreich.

Vom Benutzer praktisch bestätigt:
- Live-KM funktioniert und ist größer/fett;
- Foto-Erfassen-Maske mit KM/GPS/Strecke passt;
- 10/30-m-Ampel passt;
- automatischer KM bleibt im gelben Bereich erhalten;
- Baustellen-/Strecke-Gleis-Anzeige in der Foto-Maske war passend;
- direkter Bilderpfad von Punkt 4 funktioniert.

Noch offen aus 200049:
- Foto-Aufdruck-/Overlay-Schalter springen nach `Zurück` und erneutem Öffnen wieder auf `aus`;
- deshalb ist der eingebrannte JPG-Aufdruck praktisch noch nicht bestätigt;
- Einstellungen werden als eigener Android-Block A1 systematisch repariert und neu strukturiert.

## Kamera / KM / Vermessung – stabile Grundlage

Beibehalten, solange kein reproduzierbarer Fehler auftritt:
- rohe KF 7.0 / 7.0 R direkt lesbar;
- 102-Byte-Datensätze, Little Endian;
- Punktnummer, DB_REF, Höhe, Station/KM, PArt, Strecke und Rohinfos bleiben erhalten;
- `KF_RAW` läuft durch den bestehenden GVP/Festpunktpfad;
- GVP/Festpunkt-Kalibrierung funktioniert auch ohne GRA;
- keine zweite KM-Berechnung neben der bestehenden Logik;
- manuelle 2-Punkt-Kalibrierung bleibt erhalten;
- GK-Streifen 3/4/5 werden für DB_REF-GPS-Lage erkannt.

Verbindliche Kamera-KM-Qualität:
- `<= 10 m` grün;
- `> 10 m bis <= 30 m` gelb;
- `> 30 m` rot;
- GPS-Ungenauigkeit darf nur verschlechtern, nie verbessern.

---

# BTB-Kernstand / Auto-Sync

Verbindlich:
- BTB ist nicht baustellenweit gemeinsam;
- jeder verantwortliche TB besitzt mit seinen Helfern seinen eigenen gemeinsamen BTB-Bereich;
- andere verantwortliche TB-Teams auf derselben Baustelle bleiben getrennt;
- stabile dauerhafte `btbId` ist die Identität;
- mehrere BTBs pro Kalendertag erlaubt;
- BTB darf über Mitternacht laufen;
- nach erfolgreichem Abschluss aktive `btbId` lösen;
- Reaktivierung nur bewusst über `BTB-Verlauf -> BTB bearbeiten`;
- erster erfolgreicher Abschluss ist unveränderliches Original;
- moderne Fotos gehören nur bei exakt gleicher `btbId` zum modernen BTB;
- allgemeines Baustellenfoto ohne `btbId` darf nicht über Zeitfenster hineinrutschen;
- Legacy-Zeitfenster nur für historische Daten ohne `btbId`.

BTB-Auto-Sync seit `d878c715a3534fb542546b4d4d4e039f47dc37fc`:
- lokal zuerst speichern;
- danach vorhandenen `SofortSync` anstoßen;
- moderner `btbId`- und Legacy-Pfad abgedeckt;
- vorhandene WorkManager-/`SyncWorker`-/Team-BTB-/Medien-Pipeline wird wiederverwendet;
- kein zweiter Netzwerkpfad;
- Offline-/Netzregeln bleiben bestehen;
- Serverimport erzeugt keinen Import->Upload-Kreis.

Noch praktisch testen:
1. Poco -> Pixel ohne manuellen Sync;
2. Pixel -> Poco;
3. offline speichern -> Netz wieder da;
4. Punkte 4–8 inkl. jeweiliger Bilder;
5. Abschluss/Verlauf/Reaktivierung;
6. mehrere BTB-/Helfer-Konstellationen.

Wenn die Sync-Einstellung ausdrücklich auf `Manuell` steht, erzwingt der automatische Worker keinen Upload.

---

# Einstellungen – nächster Android-Fehlerblock

Bestätigter Fehler:
- Foto-Aufdruck-/Overlay-Schalter bleiben nach Verlassen der Seite nicht zuverlässig gespeichert.

Geplante neue Hauptstruktur:
1. Profil & Firma
2. Anzeige
3. Kamera & Fotos
4. BTB & PDF
5. Synchronisation
6. Benachrichtigungen
7. Wartung & Diagnose

Widget unter Anzeige; `Overlay` im Benutzer-UI verständlicher als `Foto-Aufdruck` benennen.

Vor dem Redesign jede Einstellung auf Laden, Speichern, Zurück, App-Neustart und tatsächliche Wirkung prüfen.

---

# PDF / Export – weitere offene Prüfungen

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

Weitere ältere Wünsche sind in `docs/TB-HELFER_ROADMAP.md` einsortiert.

---

# Produktiver Synology-/Serverstand – unverändert

- NAS/Synology ist Source of Truth;
- API produktiv: **0.7.0**;
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`;
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`;
- Secrets ausschließlich außerhalb von GitHub/Dokumentation;
- Schema 11 persönliche Nutzerkontakte vorbereitet, nicht produktiv;
- Schema 12 PDF-Logo-Layout vorbereitet, nicht produktiv;
- vor jeder produktiven Migration frisches verifiziertes Backup.

Produktive Serverarbeit wird nach der Android-Phase fortgesetzt.

---

# Technische Schuld / Buildsystem

- Seit 200043 existiert eine generierte Source-Brücke; später sauber zurückbauen;
- `verifyRoomGeneratedImplementation` schützt vor APK ohne Room-Implementierung, erzeugt aber eine bekannte Configuration-Cache-Warnung;
- GitHub Actions ist derzeit kein verlässlicher Compilerindikator: Runs enden teilweise ohne ausgeführte Build-Steps/Logs;
- maßgeblicher Buildtest bleibt derzeit Android Studio auf dem Windows-PC;
- CI/Generated-Source-Bereinigung erfolgt erst nach funktionaler Stabilisierung.

---

# Nächster sinnvoller Arbeitsschritt

Android **200050** lokal prüfen:
1. GitHub Desktop `Fetch origin -> Pull origin`;
2. Android Studio `Clean Project -> Build`;
3. auf Poco installieren, App-Daten nicht löschen;
4. Punkte 4, 5, 6, 7 und 8 jeweils öffnen: `Bilder | Abbrechen | Hinzufügen` müssen gleich groß/lesbar in einer Zeile erscheinen;
5. in jedem Punkt mindestens einen Testeintrag + Bild mit passendem Bildtyp speichern;
6. ohne manuellen Sync auf Pixel prüfen, danach Gegenrichtung;
7. einmal offline speichern und nach Netzrückkehr prüfen;
8. PDF mit Firmenlogo prüfen: Logo muss auf weißem Untergrund stehen;
9. Ergebnis dokumentieren.

Danach gemäß Roadmap A1 Einstellungen reparieren/neu strukturieren; Baustellen-/Unterbaustellen-Sync bleibt A4 und produktive Servererweiterung danach S2.

## Doku-Hinweis

Die fachlichen Grundregeln selbst wurden durch 200050 nicht geändert: Punkte 4–8, stabile `btbId`, Helfer-/TB-Teammodell, zentrale Firmenstammdaten und berechtigungsabhängige Baustellenstruktur waren bereits in `docs/TB-HELFER_PROJEKTREGELN.md` festgelegt. 200050 setzt diese vorhandenen Regeln weiter technisch um.
