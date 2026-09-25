TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.18
BUILD: 200068
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

BEHOBEN:
- Bilderliste_gesamt.xlsx und Bilderliste_gesamt.csv verwenden jetzt wirklich alle lokalen Fotos der Baustelle über alle BTBs und Tage; sie werden nicht mehr auf den aktuell geöffneten BTB bzw. den gewählten Exportzeitraum reduziert
- Der Baustellenexport nutzt dafür bewusst den ungescopten Foto-Leseweg; strukturierte Abnahmefotos werden in der Gesamt-Liste weiterhin korrekt separat behandelt
- Ein echter BTB-Abschluss wird nach dem lokalen Abschluss jetzt gezielt über den selektiven BTB-Delta-Sync an den Server gemeldet
- BtbModernLifecycleInstaller verwendet nach erfolgreichem Abschluss nicht mehr den allgemeinen SofortSync.anstossen(...)
- Stattdessen wird genau der abgeschlossene BTB über SofortSync.btbAnstossen(..., btbId) als Dirty-Scope markiert
- Dadurch kann der SelectiveSyncWorker den Abschlussstatus FERTIG für genau diese btbId hochladen
- Der Server soll dadurch nicht mehr auf ACTIVE stehen bleiben, obwohl der BTB auf dem Handy bereits abgeschlossen ist

UNVERÄNDERT:
- Lokaler BTB-Abschluss, PDF-Erzeugung, Original-/Nachbearbeitungsvertrag und Active-Pointer-Logik bleiben unverändert
- Der bewusste vollständige Neu-laden-/Recovery-Sync bleibt unverändert
- 15×15-Delta-Sync Phase 1 aus 1.3.17 / 200067 bleibt erhalten
- QR-Fix und BTB-Bild-/Verschieben-Funktionen bleiben enthalten

SERVERVERTRAG:
- Kein Server-Workaround und keine Portal-Sonderlogik
- Portal zeigt weiterhin den kanonischen Serverstatus
- Bestehender BTB-Serververtrag wird unverändert verwendet
- Server-HEAD geprüft: bdb506b8d024dd7771e86f176d85ff422e1030ee

TESTSTATUS:
- Ursache im aktuellen Android-Code reproduziert
- Source-Fix umgesetzt
- Source-Checks bestanden: Abschluss verwendet SofortSync.btbAnstossen(...) mit saved.baustelleId + saved.btbId; allgemeiner SofortSync.anstossen(...) ist aus diesem Abschlussblock entfernt
- Android-Build 200068 laut Benutzer am 24.09.2026 bereits erstellt; APK wurde in den Server-Release übernommen
- Installation und Server-E2E weiterhin offen
