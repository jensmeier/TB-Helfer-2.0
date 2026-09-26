# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-26

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| INT-001 | P0 | GEMEINSAM | Android Draft-PR #7 ist gegen aktuellen Server-Branch mergeable=false; Branches stark divergiert | Android-Arbeitsblock abschließen → SERVER integriert aktuellen Serverstand in Android-Branch/PR und löst gemeinsame Projektsteuerungs-Konflikte gezielt |
| BTB-SYNC-002 | P0 | ANDROID | Pixel↔Portal Basissync mit 1.3.23/200073 in beide Richtungen bestätigt | Parallel-Autosave-/Conflict-Test bleibt offen; danach Poco separat |
| PORTAL-BTB-UI-001 | P1 | SERVER | Live-Sync bestätigt; BTB im Portal muss fachlich/optisch nachgearbeitet werden | konkrete Portal-BTB-Anpassungen festlegen, dann gezielt umsetzen |
| BTB-EDIT-001 | P1 | GEMEINSAM | SERVER Source fertig; ANDROID Entry/Node fertig, Foto-Zuordnung lokal stabil gespeichert; Server-Foto-PATCH/link_version im Android-HEAD noch nicht genutzt | Branches integrieren; danach Android-Foto-PATCH separat schließen, dann gemeinsamer E2E |
| PORTAL-WEATHER-001 | P1 | SERVER/ANDROID | Portal kann tempC anzeigen, Praxis-BTB zeigt aber keine Temperaturen; 0-Niederschlag darf nicht als unbekannt/null verschwinden | aktuellen Header-Payload + Android-Wetterwrite prüfen, dann Ursache gezielt beheben |
| PORTAL-PART-001 | P1 | SERVER | gelöschte/nicht mehr aktive Helfer sollen aktuelle BTB-Ansicht nicht unnötig belegen | Teilnehmerquelle/Lifecycle prüfen; Anzeige filtern ohne historische Nachweise zu löschen |
| DEV-META-001 | P2 | SERVER | `tb.devices.app_version/app_build` bleiben nach App-Update ohne Neuaktivierung auf altem Aktivierungsstand | später Runtime-Metadaten-Updatevertrag definieren; kein Blocker für BTB-SYNC-001 |
| BTB-SYNC-001 | P0 | ANDROID | Source-Ursache eingegrenzt: BtbActiveContext kann server-active Pointer wegen lokalem ABGESCHLOSSEN lösen | Android-Fix auf feature/tb-android-2.0, Build/Install, Poco-E2E |
| NET-001 | P1 | GEMEINSAM | Pixel synchronisiert wieder; Ursache des vorherigen App-DNS-Fehlers ungeklärt | nur bei erneuter Reproduktion vertiefen; jetzt Fach-E2E fortsetzen |
| E2E-001 | P0 | GEMEINSAM | Verantwortliches TB-Handy muss zuerst mit Server/Portal sauber funktionieren | echte Fachkette Handy ↔ Server ↔ Portal reproduzierbar prüfen |
| IMG-001 | P1 | GEMEINSAM | Quellaudit bestätigt: Android lädt Volloriginal hoch und nutzt Server-Renditions nicht | nach stabilem Hauptgerät-/Helfer-Sync ANDROID: Sync-JPEG + preview/thumb + Metadaten; SERVER: Migration015/Renditions E2E |
| DEV-001 | P0 | ANDROID | Poco-Erstabgleich abgeschlossen und Baustelle geladen; BTB 26.09 verschwindet nach kurzem Anzeigen | gemeinsam mit BTB-SYNC-001 diagnostizieren; kein weiterer Reset |
| AND-001 | P0 | ANDROID | 1.3.19 / 200069 Build/Install/E2E offen | Build → Pixel → Poco |
| SRV-001 | P1 | SERVER | Migration 015 nicht praktisch bestätigt | Deploy + Runtime-Probe |
| SYNC-001 | P2 | GEMEINSAM | vollständiger 15×15 Last-/Delta-E2E offen; Delta-Wait 255/255 als Teiltest bestanden | Gesamtlast nach Hauptgerät-/Helfer-E2E und nach RAM-/NAS-Ausbau wiederholen |
| DOC-001 | P1 | STEUERUNG | neue Struktur lokal nach Pull verifizieren | alte Top-Level-Ordner müssen weg sein |

Keine bestätigten Punkte wieder als offen anlegen.
