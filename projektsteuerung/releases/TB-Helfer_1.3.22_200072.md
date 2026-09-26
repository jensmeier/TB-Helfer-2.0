TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.22
BUILD: 200072
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Zweiter BTB-SYNC-001-Laufzeitfix für den tatsächlich noch aktiven historischen 200033-Pointer-Guard.
- Moderner server-/rollenbewusster BTB-Lifecycle wird beim Fragment zuletzt gebunden.

VERBESSERT:
- Für servergebundene BTBs respektieren jetzt beide Active-Pointer-Lese-/Guard-Pfade den kanonischen Serverzustand.
- Der alte 200033-Guard darf einen serverseitig aktiven BTB nicht mehr wegen eines lokalen Abschlussmarkers löschen.
- Der moderne BTB-Abschluss-/Schicht-Handler überschreibt den historischen lokalen Abschlusslistener zuverlässig.

BEHOBEN:
- Poco: Ursache des weiterhin verschwindenden 26.09-BTB im Quellpfad gefunden: BtbPatch200033Installer.startActivePointerGuard() konnte den Pointer weiterhin alle 450 ms über einen lokalen inactive-after-completion-Marker löschen, obwohl BtbActiveContext bereits server-aware war.
- Pixel: parallele alte/neue Lifecycle-Handler konnten weiterhin gegeneinander arbeiten; ModernLifecycleInstaller wird nun nach Patch200033 gebunden.
- BTB-SYNC-001 Fix aus 1.3.21 bleibt enthalten.
- UPD-001 APK-Metadatenprüfung aus 1.3.21 bleibt enthalten.

UNVERÄNDERT:
- Keine server/**-Dateien geändert.
- Keine Poco-Daten löschen, keine Neuaktivierung und keine NAS-Änderung.
- Rein lokale/Legacy-BTBs ohne Server-Snapshot behalten das historische 200033-Marker-Verhalten.

QUELLSTAND:
- Android-Branch: feature/tb-android-2.0
- Vorgängerstand 1.3.21 / 200071 Update-E2E: BESTÄTIGT
- Server-Lifecycle für BTB 26.09: zuvor ACTIVE bestätigt
- Server-Vertrags-/Diagnosecommit: e71585cb21fa13c01f184036337d3ea68725f37e

TESTSTATUS:
- SOURCE-FIX UMGESETZT
- BUILD OFFEN
- INSTALLATION OFFEN
- PIXEL/POCO-BTB-E2E OFFEN

NÄCHSTER TEST:
- 1.3.22 / 200072 frisch bauen.
- APK + genau diese Release-MD gemeinsam im Portal verwenden.
- Pixel und Poco aktualisieren.
- Pixel: BTB Abschluss muss stabil stehen und Haupt-TB-Rolle behalten.
- Poco: 26.09-BTB muss dauerhaft sichtbar/aktiv bleiben und darf nicht auf „kein BTB vorhanden“ zurückfallen.
- Nichts am Poco löschen/neu anmelden und nichts am NAS ändern.
