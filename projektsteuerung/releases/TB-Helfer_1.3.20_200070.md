TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.20
BUILD: 200070
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Neuer eindeutig höherer Android-Build für den BTB-SYNC-001-Praxisnachtest.
- Versionierung ist ab jetzt strikt fortlaufend: neue APKs verwenden keine bereits belegte Buildnummer erneut.

VERBESSERT:
- Servergebundene BTBs richten ihren aktiven lokalen Kontext jetzt konsequent nach dem kanonischen Server-Lifecycle.
- Lokale PDF-/Baseline-/Abschlussartefakte dürfen einen serverseitig weiterhin aktiven BTB nicht mehr aus dem Active-Pointer entfernen.

BEHOBEN:
- BTB-SYNC-001: `BtbActiveContext.laden()` berücksichtigt `BtbServerStateStore.active=true` vor lokalem `ABGESCHLOSSEN`-/PDF-/Baseline-Status.
- Bei `active=true` bleibt der aktive BTB erhalten.
- Erst bei serverseitig `FINALIZED` und `active=false` darf der servergebundene Active-Pointer gelöst werden.
- Rein lokale/Legacy-BTBs ohne Server-Snapshot behalten die bisherige lokale Abschlusslogik.
- Der zuvor korrigierte Pixel-Wechsel zwischen `BTB Abschluss` und PDF-Anzeige bleibt Bestandteil dieses Quellstands.
- Die zuvor gemeldeten Kotlin-Compilefehler in `BautagebuchPdf.kt` und `CollabLocalBridge.kt` bleiben korrigiert.

UNVERÄNDERT:
- Keine `server/**`-Dateien für BTB-SYNC-001 geändert.
- Keine Poco-Daten löschen, keine Neuaktivierung und keine NAS-Änderung für diesen Test.
- Bestehender Geräteaktivierungs- und Updateweg bleibt erhalten.

QUELLSTAND:
- Android-Branch: feature/tb-android-2.0
- BTB-SYNC-001 Pointer-Fix: f57c532863d2f92b81729b9af1466b57a22f8723
- Versionierungsentscheidung dokumentiert: 32f57241a9717a6748d427af53eae3a593ad656b
- Server-Vertrags-/Diagnosecommit: e71585cb21fa13c01f184036337d3ea68725f37e

TESTSTATUS:
- QUELLSTAND FERTIG
- BUILD OFFEN
- INSTALLATION OFFEN
- PIXEL/POCO-E2E OFFEN

NÄCHSTER TEST:
- Android Studio: aktuellen Branch pullen.
- 1.3.20 / 200070 bauen.
- APK auf Pixel und Poco installieren.
- Denselben BTB vom 26.09 erneut öffnen.
- Poco: BTB muss sichtbar und aktiv bleiben; kein Rückfall nach ca. 1 Sekunde auf „kein BTB vorhanden“.
- Pixel: Haupt-TB-Rolle und Abschlussbutton müssen stabil bleiben.
- Danach Server/Portal/Poco-Zustand desselben BTB vergleichen.
