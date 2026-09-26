TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.21
BUILD: 200071
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Update-Binärprüfung: Die heruntergeladene APK wird vor dem Android-Installer gegen den Server-Releaseeintrag geprüft.
- Package-ID, versionName und versionCode müssen exakt zum Release passen.

VERBESSERT:
- Falsche oder alte APKs können nicht mehr unter einer neueren Release-Nummer unbemerkt installiert werden.
- Die Updateanzeige basiert weiterhin auf der tatsächlich eingebauten Android-Version.

BEHOBEN:
- UPD-001: Schutz gegen die Schleife „Update installiert → App zeigt weiter alte Version → Update weiter verfügbar“.
- Bei einer Release/APK-Abweichung wird die Installation blockiert und die APK gelöscht.
- Fehlermeldung nennt die Version/Buildnummer in der APK und die vom Server erwartete Version/Buildnummer.
- BTB-SYNC-001 Active-Pointer-Fix bleibt enthalten: serverseitig active=true hat Vorrang vor lokalen PDF-/Baseline-/Abschlusszuständen.
- Pixel ACTIVE↔PDF-Flackerfix und die vorherigen Kotlin-Compilefixes bleiben enthalten.

UNVERÄNDERT:
- Keine server/**-Fachdatei geändert.
- Keine App-Daten löschen, keine Geräte neu aktivieren, keine NAS-Änderung für diesen Fix.
- Server-Releasekatalog bleibt Quelle für verfügbare Releases; installierter Stand kommt aus dem Android-Package.

QUELLSTAND:
- Android-Branch: feature/tb-android-2.0
- UPD-001 Diagnose-Doku: 44b7c2caa50a9d9299b01daa100a30ac5f25ab43
- BTB-SYNC-001 Pointer-Fix: f57c532863d2f92b81729b9af1466b57a22f8723
- Server-Vertrags-/Diagnosecommit: e71585cb21fa13c01f184036337d3ea68725f37e

TESTSTATUS:
- QUELLSTAND IN ARBEIT
- BUILD OFFEN
- INSTALLATION OFFEN
- UPDATE-E2E OFFEN
- PIXEL/POCO-E2E OFFEN

NÄCHSTER TEST:
- 1.3.21 / 200071 frisch bauen.
- Im Portal genau diese neue APK zusammen mit dieser Release-MD verwenden.
- Release veröffentlichen.
- Von installiertem 1.3.19/200069 oder 1.3.20/200070 auf 1.3.21/200071 aktualisieren.
- Danach muss „Nach Update suchen“ 1.3.21 (200071) als aktuell zeigen und der Startseiten-Updatebutton verschwinden.
