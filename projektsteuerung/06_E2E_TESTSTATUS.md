# TB-HELFER 2.0 – E2E TESTSTATUS

Stand: 2026-09-27

| Bereich | Status | Nächster Test |
|---|---|---|
| Android 1.3.26 / 200076 Build | SOURCE-CHECK PASS / BUILD OFFEN | Git Pull → Android Studio Build 200076 |
| Android 1.3.26 / 200076 Installation | OFFEN | nach erfolgreichem Build zuerst Pixel |
| Build-Sourcegenerator | 14/14 ANKER PASS | beim echten Build erneut praktisch bestätigen |
| BTB-SYNC-002 Pixel↔Portal | **BESTÄTIGT** | geschützt; nur bei neuer Reproduktion öffnen |
| BTB-UI-001 Abschlussleiste | **BESTÄTIGT** | geschützt |
| UPD-001 Updatepfad | **BESTÄTIGT** | geschützt |
| PORTAL-LIVE-001 | **BESTÄTIGT** | geschützt; Portal aktualisiert ohne F5 |
| Haupt-TB-Handy ↔ Server/Portal Kernsync | **BESTÄTIGT** | Finalize/Medien als eigene E2Es |
| HELPER/Poco | OFFEN | 200076 installieren und aktuellen Lifecycle-/Daten-Sync prüfen |
| Foto Handy→Server | SOURCE UMGESETZT / E2E OFFEN | Original unverändert; Sync-JPEG max. 1920 px/JPEG82 messen |
| Foto Server→Handy/Handy→Handy | SOURCE UMGESETZT / E2E OFFEN | preview statt Vollinhalt auf zweitem Gerät nachweisen |
| Mangel-/Abnahmebilder | SOURCE UMGESETZT / E2E OFFEN | Größe + Metadaten prüfen; PDF-/Nichtbild-Anlage unverändert |
| BTB-Foto-Metadaten-PATCH | SOURCE UMGESETZT / E2E OFFEN | Punkt/Firma/Unterbaustelle/Text/KM ändern; ID/GPS/Aufnahmezeit erhalten |
| Wetter 0,0 mm | SOURCE UMGESETZT / E2E OFFEN | Android/Portal/PDF müssen 0,0 mm erhalten und anzeigen |
| Wetter tempC | SOURCE-TRANSPORT VORHANDEN / RUNTIME-PROBE OFFEN | identischen Wert Android/API/Portal vergleichen |
| BTB Finalize Android→Portal/Poco | SOURCE-PFAD PASSEND / E2E OFFEN | Android abschließen → beide Gegenstellen FINALIZED/read-only + PDF |
| BTB Finalize Portal→Android/Poco | SOURCE-PFAD PASSEND / E2E OFFEN | Portal abschließen → beide Gegenstellen FINALIZED/read-only + PDF |
| Migration 016 | **BESTÄTIGT ANGEWENDET** | gemeinsamer BTB-EDIT-Geräte-E2E |
| 15×15 Delta-/Lasttest | KAPAZITÄTSTEILTEST 255/255 BESTANDEN / GESAMT-E2E OFFEN | nach funktionalem Geräte-E2E und Hardwareausbau wiederholen |

Source-Check ≠ Build ≠ Installation ≠ E2E.

### Kapazitätsteiltest 26.09.2026

`sync/changes/wait` mit 255 getrennten Portal-Sessions: 255 erfolgreich, 0 Fehler; Wall 16,43 s, Median 14,28 s, Max 15,79 s. Testsession-Reste anschließend kontrolliert auf 0 bereinigt.

Dieser Nachweis bestätigt nur den Delta-Wait-Kapazitätsteil. 15 Teams, echte Geräteidentitäten, BTB-Fanout, Offline/Online, Medien, Konflikte und Recovery bleiben Bestandteile des offenen Gesamt-E2E.

### Gerätebefund NET-001

Beim Hauptgerät nach Anlage einer Testbaustelle:
`Synchronisation unvollständig: Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.

Nachtest: beide öffentlichen Seiten sind auf demselben Hauptgerät im Browser erreichbar. Öffentliche DNS/API-Erreichbarkeit ist damit nachgewiesen; der App-Sync muss jetzt erneut getestet werden.

### NET-001 Browser-Nachtest
`www.tb-helfer.de` und `api-v1.tb-helfer.de/health/live` sind auf demselben Hauptgerät im Browser erreichbar. App-Retest offen.


### BTB-SYNC-001 Source-Fix
- Commit `f57c532863d2f92b81729b9af1466b57a22f8723`.
- Server-`active=true` überstimmt jetzt lokale Abschluss-/PDF-/Baseline-Zustände in `BtbActiveContext`.
- Server-`FINALIZED && !active` darf den Pointer lösen.
- Build wurde noch nicht ausgeführt; GitHub Actions lieferte für den aktuellen HEAD keinen Workflow-Run. Kein Build-/Installations-/E2E-Nachweis.


### Version 1.3.20 / 200070
- Quellwerte, Release-MD und CI-Artefaktname auf 1.3.20 / 200070 angehoben.
- Commit `f9a7421ec9265b73ca170d28a1946644527cbe5a`.
- GitHub Actions hat für diesen HEAD keinen Workflow-Run geliefert; daher weiterhin **BUILD OFFEN**.


### UPD-001 Diagnose
- Wartungsanzeige = eingebauter `BuildConfig`-Stand.
- Startbanner = Serververgleich gegen eingebauten `BuildConfig.VERSION_CODE`.
- Keine relevante Cacheursache im geprüften Pfad.
- APK-Metadaten werden weder beim Serverupload noch im Android-Installerpfad gegen Releasefelder validiert.
- Nächster Build für Schutzfix: 1.3.21 / 200071.


### UPD-001 Source-Fix 1.3.21 / 200071
- APK-Metadatenvalidierung source-seitig umgesetzt.
- Noch kein Buildnachweis.
- E2E-Erwartung: nach erfolgreicher 200071-Installation zeigt Wartung 1.3.21 (200071), Startseiten-Updatehinweis verschwindet.
- Negativtest: absichtlich falsche/alte APK unter 200071-Metadaten muss vor Installer blockiert werden.


### UPD-001 Praxis-E2E bestätigt
- Benutzer bestätigt 1.3.21 / 200071 Updatepfad als funktionierend.
- Release-MD + APK desselben Builds verwendet.
- Build/Installation/Update-E2E: **BESTÄTIGT**.
- BTB-SYNC-001 bleibt separater nächster Geräte-E2E.


### BTB-SYNC-001 E2E 1.3.21
- Pixel: Abschlussbutton flackert weiterhin.
- Poco: 26.09 kurz sichtbar, danach kein BTB vorhanden.
- Ergebnis: **NICHT BESTANDEN**.
- Update-E2E bleibt separat BESTÄTIGT.


### BTB-SYNC-002 Source-Fix 1.3.23
- Dirty-BTB wird jetzt vor jedem Server-Snapshot-Apply zuerst in Pending/Outbox exportiert.
- Vorgelagertes Snapshot-Apply im `SelectiveSyncWorker` entfernt.
- `CollabLocalBridge` blockiert BTB-Datei-Apply bei Pending oder Dirty.
- Lokaler Save und Server-Apply sind pro BTB serialisiert.
- Neuer Autosave während Upload bleibt dirty und erzwingt einen Folgelauf.
- Version **1.3.23 / 200073**.
- Status: **SOURCE-FIX UMGESETZT / BUILD OFFEN / INSTALLATION OFFEN / E2E OFFEN**.

Pflichttest:
1. Pixel speichert eindeutigen Text `ABC`;
2. Portal erhält `ABC`;
3. Portal ändert auf `XYZ`;
4. Pixel erhält `XYZ`;
5. zusätzlicher Pixel-Autosave während laufendem Upload darf nicht still verschwinden.


## BTB-SYNC-002 – bidirektionaler Basistest 1.3.23
**BENUTZER BESTÄTIGT:** Der BTB-Sync funktioniert im aktuellen Test in beide Richtungen:
- Pixel → Portal: **BESTANDEN**
- Portal → Pixel: **BESTANDEN**

Damit ist der normale bidirektionale Datenweg des BTB-SYNC-002-Fixes praktisch bestätigt. Nicht durch diese Aussage abgedeckt ist der zusätzliche Parallel-Autosave-/Konflikttest während eines laufenden Uploads; dieser bleibt offen. Poco/HELPER bleibt ebenfalls ein eigener E2E.


### BTB-UI-001 1.3.24 Praxis
- Pixel: **BESTANDEN**.
- PDF Entwurf + BTB Abschluss bleiben stabil sichtbar.
- Pixel-UI-E2E: **BESTÄTIGT**.
- Poco/HELPER: **OFFEN**.


### BTB-SYNC-002 Abschluss-E2E
- Pixel → Portal: **BESTANDEN**.
- Portal → Pixel: **BESTANDEN**.
- Parallel-Autosave-/Dirty-Schutz: **BESTANDEN**.
- Gesamtstatus BTB-SYNC-002: **BESTÄTIGT**.
- Nächster E2E: Poco/HELPER.

## Integrationsaudit 27.09.2026 – neue Pflichttests
| Bereich | Status | Nächster Test |
|---|---|---|
| Foto Android→Server | SOURCE-ABWEICHUNG BESTÄTIGT | nach Android-Fix Originalgröße vs. hochgeladene Sync-JPEG-Größe/Abmessungen vergleichen |
| Foto Server→Android/Handy→Handy | SOURCE-ABWEICHUNG BESTÄTIGT | nach Android-Fix nachweisen, dass `preview/thumb` statt `/content` geladen wird |
| BTB Finalize Android→Portal/anderes Handy | SOURCE-PFAD PASST / E2E OFFEN | Haupt-TB auf Android abschließen; offenes Portal + zweites Gerät müssen automatisch FINALIZED sehen; PDF prüfen |
| BTB Finalize Portal→Android/anderes Handy | SOURCE-PFAD PASST / E2E OFFEN | im Portal finalisieren; Android + zweites Gerät müssen automatisch FINALIZED/read-only sehen; PDF prüfen |
| Wetter 0 mm | ANDROID-FEHLER SOURCE-SEITIG BESTÄTIGT | nach Fix automatisches Wetter mit 0.0 mm speichern → Android/Portal/PDF müssen 0 mm zeigen |
| Wetter Temperatur | SOURCE-TRANSPORT PASST / RUNTIME-PROBE OFFEN | denselben BTB-Header auf Android/API/Portal mit identischem `tempC` vergleichen |


## Android 1.3.26 / 200076 – Teststatus
| Bereich | Status | Nächster Test |
|---|---|---|
| Build 1.3.26 / 200076 | SOURCE-CHECK PASS / BUILD OFFEN | Git Pull → Android Studio: testDebugUnitTest + assembleDebug/Release |
| Foto Handy→Server | SOURCE UMGESETZT / E2E OFFEN | neues Foto: lokales Original unverändert; Server-Syncdatei max. 1920 px/JPEG82 und deutlich kleiner prüfen |
| Foto Server→Handy | SOURCE UMGESETZT / E2E OFFEN | zweites Handy: mobile Datei muss aus serverseitiger preview-Rendition kommen, nicht aus Vollinhalt |
| Mangel-/Abnahmebilder | SOURCE UMGESETZT / E2E OFFEN | je ein neues Mangel-/Abnahmefoto übertragen und Größen/Metadaten prüfen; PDF-Anlage unverändert prüfen |
| BTB-Foto-Metadaten-PATCH | SOURCE UMGESETZT / E2E OFFEN | Punkt/Firma/Unterbaustelle/Kurztext/KM ändern; Foto-ID/GPS/Aufnahmezeit müssen erhalten bleiben |
| Wetter 0,0 mm | SOURCE UMGESETZT / E2E OFFEN | automatisches trockenes Wetter: Android + Portal + PDF müssen 0,0 mm erhalten/anzeigen |
| Wetter Temperatur | SOURCE-TRANSPORT UNVERÄNDERT | Android/API/Portal denselben tempC-Wert vergleichen |
| BTB Finalize Android→Portal/Poco | SOURCE-PFAD UNVERÄNDERT PASSEND / E2E OFFEN | Android abschließen → Portal + Poco automatisch FINALIZED/read-only + PDF |
| BTB Finalize Portal→Android/Poco | SOURCE-PFAD UNVERÄNDERT PASSEND / E2E OFFEN | Portal abschließen → Android + Poco automatisch FINALIZED/read-only + PDF |
