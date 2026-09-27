# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-27

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| AND-BUILD-200076 | P0 | ANDROID | `Locale`-Compilefehler reproduziert und source-seitig behoben; Generatoranker 14/14 PASS; noch kein erfolgreicher APK-Build | Git Pull → Android Studio Build 200076 erneut; danach Pixel installieren |
| AND-VER-001 | P1 | ANDROID | sichtbare Versions-/Buildanzeige auf Startseite fehlt | dynamisch aus BuildConfig anzeigen; Wartungsformat angleichen |
| BTB-FINAL-001 | P0 | GEMEINSAM | Android/Portal teilen denselben kanonischen Finalize-Lifecycle; Source-Pfad passend | E2E Android→Portal/Poco und Portal→Android/Poco inkl. Abschluss-PDF |
| E2E-HELPER-001 | P0 | ANDROID | HELPER/Poco mit aktuellem erfolgreichen Build noch nicht abschließend geprüft | 200076 auf Poco installieren und Lifecycle-/Daten-Sync testen |
| BTB-EDIT-001 | P1 | GEMEINSAM | Server Source + Migration016 vorhanden; Android Entry/Node + Foto-Metadaten-PATCH source-seitig integriert | Build 200076 → gemeinsamer Portal/Pixel-E2E für Entry- und Foto-Umhängen |
| IMG-001 | P1 | ANDROID | kleine Sync-JPEGs + preview-Download source-seitig für BTB/Mangel/Abnahme/Bild-Anlagen umgesetzt | Original-/Sync-/Preview-Größe, keine Dubletten und Handy→Handy praktisch prüfen |
| WEATHER-001 | P1 | ANDROID | 0,0-mm-Erhalt + mm-Anzeige App/PDF/Druckmodell source-seitig umgesetzt | Android/API/Portal/PDF-Roundtrip mit 0,0 mm + tempC |
| PORTAL-BTB-UI-001 | P1 | SERVER | bestätigter Live-Sync bleibt geschützt; weitere fachlich/optische Portal-BTB-Nacharbeit separat | nur konkrete noch offene Portal-BTB-Anpassungen im SERVER-Projekt bearbeiten |
| PORTAL-PART-001 | P1 | SERVER | nicht mehr aktive Helfer sollen aktuelle BTB-Ansicht nicht unnötig belegen; Historie muss erhalten bleiben | Teilnehmerquelle/Lifecycle im SERVER-Projekt prüfen |
| NET-001 | P1 | GEMEINSAM | früherer App-DNS-Fehler aktuell nicht mehr dauerhaft reproduziert | nur bei erneuter Reproduktion vertiefen |
| SRV-001 | P1 | SERVER | Migration015/Rendition-Metadaten-Runtime-Nachweis als eigener Altpunkt nicht vollständig dokumentiert | SERVER-Projekt entscheidet anhand aktuellem Deploystand, ohne Android-Ersatzfix |
| DEV-META-001 | P2 | SERVER | Geräte-`app_version/app_build` sind Aktivierungs-Snapshot und kein Runtime-Versionsnachweis | später Runtime-Metadatenvertrag definieren |
| SYNC-001 | P2 | GEMEINSAM | vollständiger 15×15 Last-/Delta-E2E offen; Delta-Wait 255/255 Teiltest bestanden | nach funktionalem Geräte-E2E und RAM-/NAS-Ausbau Gesamtlast wiederholen |
| DOC-001 | P2 | STEUERUNG | lokale Arbeitskopien müssen nach neuer Projektstruktur aktuell gehalten werden | bei nächstem lokalen Arbeitsblock Git Pull und alte Top-Level-Ordner nicht neu anlegen |

Geschlossen/geschützt und deshalb **nicht** als offen führen:
- BTB-SYNC-002: **BESTÄTIGT**.
- BTB-UI-001: **BESTÄTIGT**.
- PORTAL-LIVE-001: **BESTÄTIGT**.
- UPD-001: **BESTÄTIGT**.

Keine bestätigten Punkte wieder als offen anlegen.
