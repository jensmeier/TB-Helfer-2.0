# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-26

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| AND-BUILD-200075 | P0 | ANDROID | Generatorfehler source-seitig mit 606aa3ad korrigiert; erfolgreicher Build 1.3.25/200075 noch nicht nachgewiesen | lokalen/Android-Studio-Build erneut ausführen; APK erst nach Erfolg freigeben |
| BTB-EDIT-001 | P1 | GEMEINSAM | SERVER Entry-/Foto-PATCH + Portal-Editor fertig; ANDROID Entry/Node + lokale Foto-Metadaten integriert; Android nutzt Server-Foto-PATCH/link_version noch nicht | Android Foto-PATCH-Clientpfad schließen → Build 200075 → Server Migration016/Deploy → gemeinsamer Eintrag-/Foto-E2E |
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
