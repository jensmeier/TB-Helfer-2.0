TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.23
BUILD: 200073
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- BTB-SYNC-002-Schutz gegen Datenverlust im selektiven Android-Sync.
- Dirty-BTB wird vor jedem Server-Snapshot-Apply zuerst aus dem lokalen Fachstand in Pending/Outbox gesichert.

VERBESSERT:
- SelectiveSyncWorker lädt bei einem dirty bekannten BTB keinen Server-Snapshot mehr vor dem lokalen Export.
- Serverversion und Konflikte werden erst im bestehenden Mutation-/Versionspfad geprüft.
- Server-Snapshot wird erst nach bestätigtem Upload angewendet.
- Ein während des Uploads neu entstandener Autosave bleibt dirty und erzwingt einen weiteren selektiven Lauf.
- Lokaler BTB-Save und Server-Apply sind pro BTB serialisiert; ein paralleler Autosave kann nicht mehr zwischen Dirty-Prüfung und Snapshot-Dateischreibzugriff verloren gehen.
- CollabLocalBridge überspringt BTB-Snapshot-Apply, solange für denselben BTB ein Pending oder Dirty-Stand existiert.

BEHOBEN:
- P0 BTB-SYNC-002: Pixel-Eingaben konnten bisher durch einen älteren Server-Snapshot überschrieben werden, bevor sie in die Upload-Outbox gelangten.
- Praxisfolge Pixel ABC → Portal ohne ABC → Portal XYZ → Pixel XYZ und ABC verloren wird durch die neue Reihenfolge verhindert.

UNVERÄNDERT:
- Keine server/**-Dateien geändert.
- Server/DB-Vertrag bleibt unverändert.
- PORTAL-LIVE-001 ist ein separater SERVER-Punkt: Source-Fix vorhanden, Deployment/E2E offen.
- UPD-001 bleibt BESTÄTIGT.
- BTB-SYNC-001-Fixes aus 1.3.21/1.3.22 bleiben enthalten.

QUELLSTAND:
- Android-Branch: feature/tb-android-2.0
- Vorgängerstand: 1.3.22 / 200072
- BTB-SYNC-002 Projektsteuerung: vor Codeänderung gesichert
- Server-Lifecycle/DB-Vertrag für BTB 26.09: zuvor kanonisch bestätigt

TESTSTATUS:
- SOURCE-FIX UMGESETZT
- BUILD OFFEN
- INSTALLATION OFFEN
- PIXEL↔PORTAL E2E OFFEN
- POCO E2E OFFEN

NÄCHSTER TEST:
- 1.3.23 / 200073 frisch bauen.
- APK + genau diese Release-MD gemeinsam im Portal veröffentlichen.
- Pixel aktualisieren.
- Test 1: Pixel trägt einen eindeutigen Text ein → Portal muss ihn nach Sync erhalten.
- Test 2: Danach Portaländerung durchführen → Pixel muss sie erhalten.
- Test 3: Während laufendem Upload erneut auf Pixel ändern → der neuere lokale Stand darf nicht still durch den bestätigten älteren Server-Snapshot verschwinden.
- Erst nach bestandenem Pixel↔Portal-Test Poco erneut mit demselben aktiven BTB prüfen.
