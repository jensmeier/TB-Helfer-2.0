# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-26

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| BTB-EDIT-001 | P1 | GEMEINSAM | Einträge/Fotos müssen Punkt 4–8, Firma und Unterbaustelle nachträglich mit stabiler Node-Identität wechseln können | ANDROID Istmodell/@ORT/ortId/clientNodeKey prüfen; Vertrag festlegen; kein Delete/Create-Workaround |
| PORTAL-WEATHER-001 | P1 | GEMEINSAM | tempC fehlt im Praxis-BTB; eindeutiger 0-mm-Niederschlag darf nicht zu null werden | Android-Wetterwrite + Server/Portal-Payload getrennt diagnostizieren |
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

Keine bestätigten Punkte wieder als offen anlegen.
