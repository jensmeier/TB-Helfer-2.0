# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-26

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| REF-DATA-003 | P0 | SERVER/PORTAL/ANDROID | Reset + 40/40-Neuimport + WVB + Search/Availability/Geometry bestätigt; Pasing-KM fehlt fachlich erwartbar und wird später per Android-Start/Ende manuell gesetzt | Jetzt Portal-Multi-Hit prüfen; danach Android-Manuellabschnitt-Vertrag/Sync für Start-Ende-KM praktisch schließen; Problemfenster nur bei echtem REVIEW_REQUIRED-Fall |
| REF-DATA-002 | P0 | SERVER/ANDROID | Achsen-Hover/Klick + ca. 2000x Tiefzoom + KOR-Parser-v3 auf Synology praktisch bestätigt; übrige REF-DATA-002-Gesamtpunkte separat weiterführen | bestätigten Teilblock schützen; nur verbleibende Import-/Android-E2E-Punkte weiterbearbeiten |
| REF-DATA-001 | P0 | SERVER/ANDROID | Datei-zuerst-Analyse + bestätigter Global/Firma-Import source-seitig fertig; Synology-E2E/Android offen | 4 Runtime-Dateien deployen → echte TRA/KF-Analyse + Import testen → danach Android-Consumer |
| ADMIN-PDF-011 | P1 | ANDROID | SERVER/Admin 40..400 pt + X/Y + Preview am 29.09.2026 abgenommen; Android clamp aktuell noch 250 | ANDROID-Projekt: Clamp/Vertrag auf 40..400 übernehmen und Geräte-PDF praktisch prüfen |
| PDF-RUNTIME-009 | P0 | SERVER | Ursache bestätigt; Android-naher Serverrenderer v5 source-seitig fertig, Runtime noch nicht bestätigt | 4 Serverdateien übernehmen → API neu starten → neuen Serverabschluss gegen 22.09 + Handy-Entwurf visuell prüfen |
| ADMIN-DASH-008 | P1 | SERVER | Physische Synology-Laufwerkstemperaturen in °C source-seitig umgesetzt; Runtime noch nicht geprüft | Serverdateien + Compose auf Synology übernehmen → API/Projekt recreaten → Laufwerk 1/2 gegen DSM-Werte vergleichen |
| ADMIN-DASH-006 | P1 | SERVER | Bahnhofsdaten-Mount und MIH-Suche praktisch bestätigt; Dashboard-Gesamtretest noch offen | Übersicht/Warnungsquittierung noch praktisch prüfen |
| DEVICE-INFO-001 | P1 | ANDROID | Geräte-App-Version wird nach App-Update serverseitig nicht neu gemeldet | Android soll bei normalen authentifizierten Kontakten `X-TB-App-Version` + `X-TB-App-Build` mitsenden; SERVER ist vorbereitet |
| ADMIN-STRUCTURE-004 | P1 | SERVER | Firmenzentrierte Admin-Struktur source-seitig fertig | geänderte Serverdateien auf Synology übernehmen → API neu starten → Firmenworkspace praktisch testen |
| ADMIN-FINISH-001 | P1 | SERVER | Admin-Quellblock inkl. Storage-Deduplizierung fertig, aber noch nicht deployed/runtime-geprüft | Synology deployen → API neu starten → Übersicht/Firma/PDF/Bahnhof/Trassierungsimport smoke-testen |
| AND-BUILD-200075 | P0 | ANDROID | Generatorfehler source-seitig mit 606aa3ad korrigiert; erfolgreicher Build 1.3.25/200075 noch nicht nachgewiesen | lokalen/Android-Studio-Build erneut ausführen; APK erst nach Erfolg freigeben |
| BTB-EDIT-001 | P1 | GEMEINSAM | SERVER Source + Migration016 auf Synology vorhanden; ANDROID Entry/Node + lokale Foto-Metadaten integriert; Android nutzt Server-Foto-PATCH/link_version noch nicht | API+Worker neu starten → Portal-Smoke; Android Foto-PATCH-Clientpfad schließen → Build 200075 → gemeinsamer E2E |
| PORTAL-BTB-UI-001 | P1 | SERVER | Live-Sync bestätigt; BTB im Portal muss fachlich/optisch nachgearbeitet werden | konkrete Portal-BTB-Anpassungen gezielt umsetzen und E2E prüfen |
| PORTAL-WEATHER-001 | P1 | SERVER/ANDROID | Android 0-mm→null-Fehler bestätigt; tempC wird source-seitig serialisiert, Praxisverluststelle offen | Android 0-mm erhalten; Server-Header/Portal-Payload für tempC separat vergleichen |
| PORTAL-PART-001 | P1 | SERVER | gelöschte/nicht mehr aktive Helfer sollen aktuelle BTB-Ansicht nicht unnötig belegen | Teilnehmerquelle/Lifecycle prüfen; Anzeige filtern ohne historische Nachweise zu löschen |
| DEV-META-001 | P2 | SERVER | `tb.devices.app_version/app_build` bleiben nach App-Update ohne Neuaktivierung auf altem Aktivierungsstand | später Runtime-Metadaten-Updatevertrag definieren |
| BTB-SYNC-001 | P0 | ANDROID | historischer Pointer-Guard source-seitig korrigiert und im aktuellen Quellstand enthalten | Poco/HELPER mit aktuellem erfolgreichen Build praktisch prüfen; keinen bestätigten Fix zurückbauen |
| NET-001 | P1 | GEMEINSAM | Pixel synchronisiert wieder; Ursache des früheren App-DNS-Fehlers ungeklärt | nur bei erneuter Reproduktion vertiefen |
| E2E-001 | P0 | GEMEINSAM | Haupt-TB-Handy ↔ Server/Portal Kernsync bestätigt | als Nächstes HELPER/Poco; Finalize/PDF und BTB-EDIT separat prüfen |
| IMG-001 | P1 | GEMEINSAM | Quellaudit: Android lädt Volloriginal hoch und nutzt Server-Renditions nicht vollständig | nach stabilem Hauptgerät-/Helfer-Sync Bildtransport-Zielmodell umsetzen und E2E prüfen |
| DEV-001 | P0 | ANDROID | Poco-Verlustpfad source-seitig auf historischen Pointer-Guard eingegrenzt/korrigiert | aktuellen erfolgreichen Build auf Poco/HELPER praktisch prüfen; kein Reset |
| AND-001 | P0 | ANDROID | Pixel↔Portal inkl. Parallel-Autosave bestätigt | Poco/HELPER-E2E durchführen |
| SRV-001 | P1 | SERVER | Migration 015 nicht praktisch bestätigt | Deploy + Runtime-Probe |
| SYNC-001 | P2 | GEMEINSAM | vollständiger 15×15 Last-/Delta-E2E offen; Delta-Wait 255/255 als Teiltest bestanden | Gesamtlast nach Hauptgerät-/Helfer-E2E und nach RAM-/NAS-Ausbau wiederholen |
| DOC-001 | P1 | STEUERUNG | neue Struktur lokal nach Pull verifizieren | alte Top-Level-Ordner müssen weg sein |

| REF-DATA-005 | P2 | SERVER/PORTAL | Fachlicher Korrekturworkflow BESTÄTIGT: Warnung→Bestätigung→AKTUALISIERT, IDs/SHA/Raw eindeutig, Audit vorhanden, Search/Availability/Geometry MGHN korrekt (12 Achsen/12 Segmente/0 Review) | Nur roten Warnrahmen nach letztem Template-Deploy visuell bestätigen; danach Punkt vollständig schließen |
| REF-DATA-006 | P1 | SERVER/PORTAL | /admin/v1/survey-overview lädt, aber Coverage-Request endet mit Browser-NetworkError; KPI leer, Gesamtansicht nicht geladen | Coverage-Funktion direkt im API-Terminal read-only ausführen, Trace/Ursache feststellen; erst danach gezielt patchen |
Keine bestätigten Punkte wieder als offen anlegen.


## REF-DATA-007 – offen
- Neue Summary-/Lazy-Dateien auf Synology deployen und Runtime prüfen.
- Echte Längen-/KM-Zahlen des aktuellen 39-Achsen-Bestands gegen Runtime ausgeben und plausibilisieren.
- Auffälligen `manual-segments` OVERLAP→SUPERSEDED-Pfad vor produktiver Handy-KM-Nutzung reproduzierbar testen; kein Vorab-Fix.
- Android muss später `TrassenManuellerAbschnittEntity` im Status FERTIG auf `MANUAL_TRA_SECTION` abbilden; SERVER ändert kein `app/**`.
- Danach Detailverwaltung: Bahnhof/Strecke/Achse/Original ansehen, Zuordnung/Bezeichnung korrigieren und Fachkatalog mit Survey-Bestand verknüpfen.
- `Katalog-Historie` später als verständliche `Änderungshistorie` statt primär rohem JSON darstellen.


## REF-DATA-007 – nach Summary-Runtime noch offen
- Dashboard `Bestand` im Browser prüfen: neue Trassenkennzahlen sichtbar und plausibel.
- `Trassendatenübersicht` im Browser prüfen: schneller Einstieg, Auswahl einer Achse lädt Detailgeometrie nach.
- Werte 128.911 km gesamt / 116.579 km automatisch / 12.332 km ohne KM fachlich an konkreten Achsen plausibilisieren.
- Danach Detailverwaltung für Bahnhof/Strecke/Achse/Originaldatei weiterbauen.
- Android→Server `MANUAL_TRA_SECTION` bleibt späterer eigener E2E-Block.


## REF-DATA-007 – Browser-Timeout 524 am 02.10.2026
- Benutzerbeleg: /api/v1/survey-catalog/coverage/summary zeigt Cloudflare 524, Zeit 15:54:39 UTC / 17:54:39 Europe/Berlin.
- Status: **HTTP-/BROWSER-E2E FAIL / ROOT CAUSE OFFEN**. Browser und Cloudflare laut Fehlerseite erreichbar; Origin-Antwort nicht rechtzeitig erhalten. Tatsächliche Requestdauer nicht gemessen.
- Bisheriger Summary-Helper-PASS bleibt Funktionsnachweis, ist ohne Zeitmessung kein Performance-PASS.
- Nächster Nachweis: read-only Summary im API-Container mit perf_counter und faulthandler (30-Sekunden-Grenze für separaten Diagnoseprozess). Danach Befund-abhängig Berechnung/DB-Wartezeit oder HTTP/Auth/Proxy weiter eingrenzen.
- Keine Code-/NAS-/Cloudflare-/Android-Änderung und keine neue erfolgreiche Laufzeitmessung in diesem Chatblock.


## REF-DATA-007 – nächstes Gate nach GVP-Quellfix
- GVP-Index lokal getestet; server/tbserver/survey_catalog.py noch auf NAS übernehmen und API neu starten.
- Helper-Laufzeit und unveränderte Kennzahlen bestätigen, anschließend Summary-HTTP/Dashboard/Trassenübersicht authentifiziert testen.
- Bei verbleibender Verzögerung erst Restprofil/DB-Abfragen messen. Lokaler 91,5x-Algorithmusvergleich ersetzt keinen Synology-Test.
- Bestandsbegrenzter Zoom, Detailfenster, Bearbeiten/Archiv und übrige Server-Gesamtprüfung bleiben separate offene Blöcke; Android unverändert.


## REF-DATA-007 – Summary und Dashboard im Benutzer-Nachtest bestätigt (02.10.2026)
- Benutzer meldet „kam ca. nach 2 sek“ und zeigt den erfolgreichen authentifizierten Browseraufruf von `/api/v1/survey-catalog/coverage/summary` sowie die befüllten Dashboard-Kacheln. Zuvor gemeldete Deployment-Durchführung wird damit durch Runtime-/Browser-Ergebnis ergänzt.
- Summary unverändert: 39 Achsen, 40 aktive Segmente, 2 Betriebsstellen, 7 Strecken, 14 Gleise, 4 Weichen, 7 WVB; total_length_m=128911.129, known_km_length_m=116578.85, automatic_km_length_m=116578.85, manual_km_length_m=0, manual_mobile_length_m=0, without_km_length_m=12332.28, unplausible_length_m=0; FEHLT=28, AUTOMATISCH=12. Parallele Gleise zählen separat.
- Dashboard zeigt dazu passend 128,91 km gesamt / 116,58 km belastbar / 116,58 km automatisch / 0,00 km manuell / 12,33 km ohne belastbare KM sowie 2 Betriebsstellen / 14 Gleise / 7 Strecken.
- Status dieses Teilblocks: GVP-Quellfix 687f77a33daaa100e10556300761e91ac717480f auf Synology im HTTP-/Browser-Nachtest erfolgreich; kein 524 im gezeigten Aufruf. Etwa 2 Sekunden sind eine Benutzerbeobachtung, keine instrumentierte Messung oder Lasttest. Die früheren offenen Summary-/Dashboard-Gates sind durch diesen Nachweis erledigt.
- Weiter offen: `/admin/v1/survey-overview` mit tatsächlichem Einstieg und nachgeladener Achsgeometrie prüfen. Der Screenshot zeigt das Dashboard, nicht diesen Kartenablauf. Fachliche KM-Plausibilisierung je Achse und umfangreicher Last-/E2E-Test bleiben separat offen.
- Quellprüfung der aktuellen Übersicht: Deutschlandgrenzen fest vorgegeben; Standard-Coverage lädt Übersichtspunkte, ausgewählte Achse lädt Geometrie separat. Bestandszoom, tiefer Detailzoom, überlagerndes Detailfenster und vereinbarte Verwaltung bleiben der nächste freigegebene Umbau. Noch keine neue Runtime-Änderung in diesem Nachtestblock; Portal danach, Android zuletzt.
