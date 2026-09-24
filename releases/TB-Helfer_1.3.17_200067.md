TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.17
BUILD: 200067
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Persistenter selektiver BTB-Delta-Sync als Phase 1 des 15×15-Syncmodells
- Jeder gespeicherte BTB mit stabiler btbId markiert nur genau diesen BTB als Dirty-Scope
- Mehrere schnelle Änderungen desselben BTB werden über denselben persistenten Scope zusammengeführt
- Der Dirty-Scope überlebt App-/Prozessneustart und wird beim nächsten App-Start wieder aufgenommen
- Selektiver Worker exportiert und sendet nur den betroffenen BTB

GEÄNDERT:
- Auswahl Manuell / Stündlich / Täglich aus den Sync-Einstellungen entfernt
- Periodischer Vollsync über SyncWorker entfernt
- Periodischer Schema13-Zeitplan entfernt; Schema13 bleibt vorerst ereignisgetrieben
- Synchronisations-Einstellung zeigt jetzt Automatischer Delta-Sync + erlaubtes Netz
- Neu laden bleibt weiterhin der bewusste vollständige Recovery-/Vollabgleich
- Server-1.0-BTB-Pending-Upload kann jetzt gezielt nach worksiteServerId und clientBtbId gefiltert werden
- CollabLocalBridge besitzt einen dedizierten exportBtbChange(...)-Pfad und scannt dabei keine Mängel, Abnahmen oder andere BTBs

SICHERHEIT / ROBUSTHEIT:
- Dirty-Scope wird vor dem WorkManager-Trigger persistent gespeichert
- Ein Scope wird nur entfernt, wenn während des Uploads keine neuere Änderung desselben BTB hinzugekommen ist
- Temporäre Fehler führen zu begrenzten Retries; Konflikte erzwingen keinen stillen Vollsync
- Session-/Capability-Probleme lösen keinen automatischen globalen Vollabgleich aus
- Legacy-BTB ohne stabile btbId darf vorerst weiterhin den bestehenden Vollsync-Fallback verwenden

UNVERÄNDERT:
- Der bewusste Neu-laden-/Recovery-Pfad über SyncWorker.enqueueManual(...) bleibt vollständig erhalten
- QR-Einladungsfix aus 1.3.16 / 200066 bleibt enthalten
- Bestätigter BTB-Lifecycle-Fix aus 1.3.13 / 200063 bleibt geschützt
- BTB-Bildnachtrag, horizontale Bildreihe und Verschieben-Funktionen bleiben enthalten

SERVERVERTRAG:
- Nutzt bestehenden Server-1.0-BTB-Vertrag btb_live_sync_v1
- Kein neuer Server-Endpunkt für Phase 1 erforderlich
- Change-Feed-/Push-Pull-Phase 2 bleibt separat offen
- Schichtende-/Teilnehmerstatus-Vertrag aus der Übergabe bleibt für einen folgenden Android-Block offen

TESTSTATUS:
- Übergaben TB-ANDROID_AUTOSYNC_15x15_2026-09-24.md und TB-ANDROID_SELEKTIVER_SYNC_2026-09-23.md gelesen
- Direkte Source-Checks für Zeitplan-Entfernung, persistenten Dirty-Scope, gezielten BTB-Export, gefilterten Pending-Upload und App-Neustart-Wiederaufnahme bestanden
- Android-Build 200067 noch offen
- Installation und praktischer Pixel/Poco-/Server-E2E noch offen
