# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-26

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| DEV-META-001 | P2 | SERVER | `tb.devices.app_version/app_build` bleiben nach App-Update ohne Neuaktivierung auf altem Aktivierungsstand | später Runtime-Metadaten-Updatevertrag definieren; kein Blocker für BTB-SYNC-001 |
| BTB-SYNC-001 | P0 | ANDROID | Pixel→Server→Portal für 26.09 bestätigt; Poco zeigt BTB kurz und verliert ihn wieder | Android Server-Snapshot → Cache → Active-Pointer → UI prüfen |
| NET-001 | P1 | GEMEINSAM | Pixel synchronisiert wieder; Ursache des vorherigen App-DNS-Fehlers ungeklärt | nur bei erneuter Reproduktion vertiefen; jetzt Fach-E2E fortsetzen |
| E2E-001 | P0 | GEMEINSAM | Verantwortliches TB-Handy muss zuerst mit Server/Portal sauber funktionieren | echte Fachkette Handy ↔ Server ↔ Portal reproduzierbar prüfen |
| IMG-001 | P1 | GEMEINSAM | Quellaudit bestätigt: Android lädt Volloriginal hoch und nutzt Server-Renditions nicht | nach stabilem Hauptgerät-/Helfer-Sync ANDROID: Sync-JPEG + preview/thumb + Metadaten; SERVER: Migration015/Renditions E2E |
| DEV-001 | P0 | ANDROID | Poco-Erstabgleich abgeschlossen und Baustelle geladen; BTB 26.09 verschwindet nach kurzem Anzeigen | gemeinsam mit BTB-SYNC-001 diagnostizieren; kein weiterer Reset |
| AND-001 | P0 | ANDROID | 1.3.19 / 200069 Build/Install/E2E offen | Build → Pixel → Poco |
| SRV-001 | P1 | SERVER | Migration 015 nicht praktisch bestätigt | Deploy + Runtime-Probe |
| SYNC-001 | P2 | GEMEINSAM | vollständiger 15×15 Last-/Delta-E2E offen; Delta-Wait 255/255 als Teiltest bestanden | Gesamtlast nach Hauptgerät-/Helfer-E2E und nach RAM-/NAS-Ausbau wiederholen |
| DOC-001 | P1 | STEUERUNG | neue Struktur lokal nach Pull verifizieren | alte Top-Level-Ordner müssen weg sein |

Keine bestätigten Punkte wieder als offen anlegen.
