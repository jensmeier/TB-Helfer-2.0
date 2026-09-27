# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-26

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| AND-BUILD-200075 | P0 | ANDROID | Sourcegenerator-Fix 606aa3a committed; alter 200047-Anker entfernt | 200075 erneut lokal bauen; nächsten Fehler nur reproduzierbar weiterverfolgen |
| BTB-EDIT-001 | P1 | ANDROID | Diagnose abgeschlossen; Server section-/photo-PATCH source-seitig vorhanden; Android-Modell festgelegt | SectionItem + Node-Key + Foto-Metadaten + Editpfad implementieren; Identität erhalten |
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

| INT-PHOTO-001 | P1 | GEMEINSAM | Handy-Volloriginal muss lokal bleiben; normaler Sync nur kleine Bildversion/Renditions | Android Upload/Download gegen Server preview/thumb prüfen; Abweichung je Zuständigkeit trennen |
| BTB-FINAL-001 | P0 | GEMEINSAM | BTB-Abschluss muss Android↔Server↔Portal automatisch in beide Richtungen konvergieren | Finalize-, Change-Feed- und Delta-Apply-Pfade beider Richtungen source-seitig vergleichen, dann E2E festlegen |
| WEATHER-001 | P1 | GEMEINSAM | Temperatur + Regenmenge in mm müssen verlustfrei und sichtbar sein; 0.0 mm ≠ null | Android Wetter-Slots/Header gegen Server-Payload und Portal-Renderer vergleichen |

Keine bestätigten Punkte wieder als offen anlegen.
