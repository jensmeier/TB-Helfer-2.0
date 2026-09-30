# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-26

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| REF-DATA-003 | P0 | SERVER/PORTAL/ANDROID | Synology Reindex fehlerfrei; Auto-Merge praktisch gefunden: Str 5524-1 / 5524_SL_ER0_Gp2026_02_16.TRA = OVERLAP_CONFIRMED. Fachliche Richtigkeit noch offen | Jetzt alle Segmente von Str 5524-1 inkl. Quellen, Stationsbereiche und X/Y-Endpunkte vergleichen; danach Search/Availability/Geometry/Changes |
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

Keine bestätigten Punkte wieder als offen anlegen.
