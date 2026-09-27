# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-26

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| AND-BUILD-200075 | P2 | ANDROID | historischer 1.3.25/200075-Stand: Generatorfix vorhanden, aber kein erfolgreicher APK-Build; durch neuen Fachcode nicht mehr aktueller Zielbuild | nicht erneut ausliefern; aktueller Zielbuild ist 200076 |
| BTB-EDIT-001 | P1 | GEMEINSAM | SERVER Source+Migration016 vorhanden; Android Entry/Node + Foto-Metadaten-PATCH source-seitig integriert | Build 200076 → Portal/Pixel gemeinsamer E2E für Entry- und Foto-Umhängen |
| PORTAL-WEATHER-001 | P1 | GEMEINSAM | Android 0-mm→null-Fehler bestätigt; tempC wird source-seitig serialisiert, Runtime-Verluststelle offen | Android 0-mm erhalten; Server-Header/Portal-Payload für tempC separat vergleichen |
| PORTAL-LIVE-001 | P0 | SERVER | SSE/Change-Feed Source-Fix vorhanden; laufendes NAS braucht noch F5 | SERVER deployen + Portal ohne F5 E2E; Android nicht ändern |
| REL-001 | P0 | ANDROID | 200070 wurde bereits für Updateversuch verwendet | nächster Build 1.3.21 / 200071; weitere Builds strikt hochzählen |
| DEV-META-001 | P2 | SERVER | `tb.devices.app_version/app_build` bleiben nach App-Update ohne Neuaktivierung auf altem Aktivierungsstand | später Runtime-Metadaten-Updatevertrag definieren; kein Blocker für BTB-SYNC-001 |
| BTB-SYNC-001 | P0 | ANDROID | zweiter historischer Pointer-Guard als konkrete Ursache gefunden; 1.3.22 Source-Fix committed | Build 200072 → Pixel/Poco → BTB 26.09 E2E |
| NET-001 | P1 | GEMEINSAM | Pixel synchronisiert wieder; Ursache des vorherigen App-DNS-Fehlers ungeklärt | nur bei erneuter Reproduktion vertiefen; jetzt Fach-E2E fortsetzen |
| E2E-001 | P0 | GEMEINSAM | Haupt-TB-Handy ↔ Server/Portal Kernsync bestätigt | als Nächstes HELPER/Poco; Finalize/PDF separat weiterprüfen |
| IMG-001 | P1 | GEMEINSAM | Quellaudit bestätigt: Android lädt Volloriginal hoch und nutzt Server-Renditions nicht | nach stabilem Hauptgerät-/Helfer-Sync ANDROID: Sync-JPEG + preview/thumb + Metadaten; SERVER: Migration015/Renditions E2E |
| DEV-001 | P0 | ANDROID | Poco-Verlustpfad auf historischen 450-ms-Pointer-Guard eingegrenzt | 1.3.22/200072 installieren und bestätigen; kein Reset |
| AND-001 | P0 | ANDROID | Pixel↔Portal inkl. Parallel-Autosave bestätigt | Poco/HELPER-E2E durchführen |
| SRV-001 | P1 | SERVER | Migration 015 nicht praktisch bestätigt | Deploy + Runtime-Probe |
| SYNC-001 | P2 | GEMEINSAM | vollständiger 15×15 Last-/Delta-E2E offen; Delta-Wait 255/255 als Teiltest bestanden | Gesamtlast nach Hauptgerät-/Helfer-E2E und nach RAM-/NAS-Ausbau wiederholen |
| DOC-001 | P1 | STEUERUNG | neue Struktur lokal nach Pull verifizieren | alte Top-Level-Ordner müssen weg sein |

| INT-PHOTO-001 | P1 | ANDROID | kleine Sync-JPEGs + preview-Download source-seitig für BTB/Mangel/Abnahme umgesetzt | Build 200076 → Original-/Sync-/Preview-Größe und keine Dubletten praktisch prüfen |
| BTB-FINAL-001 | P0 | GEMEINSAM | Android/Portal teilen denselben Finalize-Lifecycle; Code in 200076 nicht verändert | E2E Android→Portal/Poco und Portal→Android/Poco inkl. Abschluss-PDF |
| WEATHER-001 | P1 | ANDROID | 0,0-mm-Erhalt + mm-Anzeige App/PDF/Druckmodell source-seitig umgesetzt | Build 200076 → Android/API/Portal/PDF Roundtrip mit 0,0 mm und Temperatur |

| AND-BUILD-200076 | P0 | ANDROID | 1.3.26/200076 Quellstand umgesetzt; Sourcechecks + Generatoranker PASS; kein Buildnachweis | Android Studio Build ausführen; danach Pixel installieren und E2E-Matrix abarbeiten |

Keine bestätigten Punkte wieder als offen anlegen.
