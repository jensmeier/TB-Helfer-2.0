# TB-HELFER – GEMEINSAME KOMPATIBILITÄTSBASIS
Stand 20.11.2026, Auftrag KOMPAT-1120

## Geprüftes historisches Basispaar
Android: feature/tb-android-2.0, 1.3.46 / 200096, Fachcommit cda78c2f42fd7b1acd76d60612ce42d882662edf; Room-Instrumentierung 680ef37e9781eb4e4b0b8da04b5db7e6b2d42340.
Server: feature/tb-server-1.0, 1.0.0-dev / API v1 / PDF 6, Fachcode b17530d305797837d42f3049681f10cd919ea4b5; Testcommit b924da3e9bbff603a78ca55753cefb439f1ac607.
CI Android 42495975421, Room 42496070087, Server 42496224535 erfolgreich. Synology/Pixel/Poco und E01–E12 E2E OFFEN.

## Verbindlicher Prozess
- Vor jeder Weiterarbeit HEAD beider Branches prüfen. Nur tatsächlich getestete Commit-Paarungen als CI BESTANDEN melden; Builds, Installation und E2E getrennt.
- Fachcode Android ausschließlich im Androidbranch, Server ausschließlich im Serverbranch; gemeinsame 00–09-Projektsteuerung auf beiden Branches gleich pflegen. Keine blinden Merges, kein Zurücksetzen alter Historie.
- Bei jeder Fachcodeänderung Gegenbranch-Commit explizit im CI fixieren und Vertrag abgleichen; Versionsnamen und Tests nicht ohne Nachweis aktualisieren.
- Keine produktive Migration, NAS-Übernahme, APK-Installation oder neue kostenpflichtige Dienste ohne gesonderten Auftrag.
- Geschützt: Word, drei Fotos/Reihe, Fotoanzahl, Originalfotos/-dateien, BTB-Original, stabile IDs und historisch bestätigte Funktionen.
- Fehler niemals automatisch als erledigt markieren; E01–E12 bleiben bis zur echten Abnahme offen.

## Fehlerliste Audit 19.11.2026 (Quellcode-Befunde, E2E nicht ausgeführt)
F-01 KRITISCH SERVER: files.py Dateirückverschiebung nach bestätigtem DB-Commit bei fehlgeschlagener PDF-Bereinigung.
F-02 HOCH SERVER: track_data.py und survey_sections.py Schreibrechte ohne vollständige Bereichsprüfung.
F-03 MITTEL SERVER: sync.py Changefeed enthält potentiell team-/bereichsfremde Metadaten.
F-04 HOCH SERVER: survey_originals.py from-file ohne explizites Import-/Dateirecht.
F-05 HOCH SERVER: survey_originals.py firmenweiter Originaldownload auch bei engerer Baustellenfreigabe; fachliche Freigabe vorher beachten.
F-06 MITTEL SERVER: survey_sections.py LIMIT vor Bereichsfilter.
F-07 MITTEL ANDROID: SurveyOriginalOutbox.kt ein SHA-basierter Auftrag für mehrere Baustellenbindungen.
Bekannt offen: Room 20/21, Offline-16h, KM-Sprünge und GVP-/Mast-Bearbeitung, vollständiger Fachdelta/Globalfeed, E01–E12.
