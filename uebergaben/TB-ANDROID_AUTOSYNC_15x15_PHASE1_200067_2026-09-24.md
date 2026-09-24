# TB-Helfer 2.0 – ANDROID → SERVER Übergabe Auto-/Delta-Sync 15×15 Phase 1

Stand: 24.09.2026  
ANDROID-Branch: `feature/tb-android-2.0`  
ANDROID-Fachstand vor dieser Übergabe: `bb8776b2ed67067792b7f37a8b7cc4293d63ca1c`  
ANDROID-Version: **1.3.17 / 200067**  
SERVER geprüft: `feature/tb-server-1.0` @ `c102e840f534af9959d3aaf9a17a48b9f221fbf6`

## Umgesetzt

Phase 1 aus:
- `uebergaben/TB-ANDROID_AUTOSYNC_15x15_2026-09-24.md`
- `uebergaben/TB-ANDROID_SELEKTIVER_SYNC_2026-09-23.md`

### Zeitplan / Vollsync

- `SyncEinstellungenSpeicher.Zeitplan` vollständig entfernt.
- UI-Auswahl Manuell / Stündlich / Täglich entfernt.
- `SyncWorker.schedule(...)` legt keinen periodischen Vollsync mehr an und entfernt Alt-PeriodicWork.
- `Schema13Worker.schedule(...)` entfernt ebenfalls den alten periodischen Zeitplan.
- `Neu laden` / `SyncWorker.enqueueManual(...)` bleibt bewusst der vollständige Recovery-/Vollabgleich.

### Persistenter BTB-Dirty-Scope

Neu:
- `SelectiveSyncStore.kt`
- `SelectiveSyncWorker.kt`

Scope:
`BTB(localBaustelleId, clientBtbId)`

Eigenschaften:
- Scope wird vor WorkManager-Trigger persistent gespeichert.
- gleiche btbId wird generationstreu zusammengeführt.
- Scope überlebt Prozess-/App-Neustart.
- App-Start ruft `SelectiveSyncWorker.enqueueIfPending(...)` auf.
- Worker verwendet `ExistingWorkPolicy.KEEP` + 2-s-Debounce.
- Kommt während des Uploads eine neue Änderung desselben BTB, verhindert die Generation das Löschen des neueren Dirty-Standes.

### Selektiver BTB-Export/Upload

`BautagebuchSpeicher.speichern(...)`:
- stabile btbId → `SofortSync.btbAnstossen(...)`
- Legacy ohne stabile btbId → bestehender Vollsync-Fallback

`CollabLocalBridge.exportBtbChange(...)`:
- löst nur die betroffene gebundene Baustelle;
- lädt nur die eine btbId;
- benutzt bestehendes BTB-Sanitizing, Hashing und `queueIfChanged`;
- scannt keine anderen BTBs, Mängel oder Abnahmen.

`CollabSyncRepository.synchronizeServer1BtbPending(...)`:
- optionale Filter `worksiteServerId` und `clientBtbId`;
- bestehende Applied-/Base-State-/Conflict-Logik bleibt erhalten.

`SelectiveSyncWorker`:
- prüft `btb_live_sync_v1`;
- kein Katalogabruf;
- kein `Server1AndroidSyncCoordinator.synchronize(...)`;
- keine Baum-/Track-/Defect-/Acceptance-/globale Datei-Synchronisation;
- Konflikt erzwingt keinen stillen Vollsync;
- temporäre Fehler: begrenzter WorkManager-Retry, Dirty-Scope bleibt persistent.

## Source-Checks

Direkt gegen aktuellen Android-Code geprüft: **BESTANDEN**

- kein `Zeitplan` mehr in den betroffenen Sync-Einstellungen/Workern;
- keine periodischen SyncWorker-/Schema13-WorkRequests mehr;
- Dirty-Scope persistent;
- selektiver BTB-Export vorhanden;
- Pending-Upload filterbar;
- Worker BTB-only;
- Capability-Check `btb_live_sync_v1`;
- Prozessneustart-Wiederaufnahme;
- Version 1.3.17 / 200067;
- Release-/CI-Artefaktpfad 200067.

## Buildstatus

- **QUELLSTAND FERTIG**
- **SOURCE-CHECKS BESTANDEN**
- **ANDROID-BUILD ERFOLGREICH** – lokaler Android-Studio-Build 24.09.2026 15:04, 16 Warnungen, keine Build-Fehler
- **INSTALLATION OFFEN**
- **PIXEL/POCO-/SERVER-E2E OFFEN**

Der lokale Android-Studio-Build ist erfolgreich abgeschlossen. Gebauter App-Quellstand: `19c2757fb0244cfd75e989566c61aa2ea94c1cde`. GitHub Actions bleibt dafür kein zusätzlicher Testnachweis.

## SERVER-E2E nach Android-Build

1. Nur BTB-Text ändern.
2. Autosave abwarten.
3. API-Log darf nur BTB-bezogene Mutation/Detailpfade zeigen.
4. Keine Katalog-/Baum-/Track-/Defect-/Acceptance-Vollabfragen durch diesen BTB-Sofortlauf.
5. Zwei schnelle Änderungen desselben BTB müssen erhalten bleiben.
6. Netz aus → BTB ändern → App schließen → Netz an/App starten → Dirty-Scope muss übertragen werden.
7. Änderung BTB A darf BTB B nicht hochladen.
8. Mangel-/Abnahme-Pending darf durch BTB-Sofortlauf nicht gesendet werden.
9. `Neu laden` muss weiterhin den vollständigen Sync ausführen.

## Noch offen / nicht in Phase 1

- Phase 2: `/api/v1/sync/changes`, persistenter Change-Cursor, Push/App-Resume/Netz-Rückkehr, gezielter GET eines geänderten BTB.
- Medien: kleine Sync-Version, Thumbnail, Lazy-Download, Originalexport.
- Rollenvertrag Schichtende:
  - Helfer `Schicht Ende` / `Schicht fortsetzen`;
  - Teilnehmerstatus;
  - Haupt-TB allein finalisiert;
  - PDF-Rechte;
  - vor `shift-finish` selektiven BTB-/Bild-Sync vollständig bestätigen.

**Migration 014 / neuer Schichtende-Serververtrag weiterhin nicht allein wegen dieses Phase-1-Android-Commits deployen. Gemeinsamen Android-/Portal-/Serverstand zuerst zusammen E2E prüfen.**
