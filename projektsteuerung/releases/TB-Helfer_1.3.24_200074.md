TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.24
BUILD: 200074
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- BTB-UI-001: rollenabhängige Abschlussleiste wird jetzt ausschließlich aus dem kanonischen Server-/Rollenstatus gesetzt.

VERBESSERT:
- Haupt-TB im aktiven servergebundenen BTB sieht stabil beide Aktionen:
  - PDF Entwurf
  - BTB Abschluss
- HELPER sieht keinen PDF-Entwurf, sondern nur seine rollenbezogene Schichtaktion.
- FINALIZED/read-only bleibt auf die erlaubte PDF-Ansicht beschränkt.
- Vor dem Rollen-/Lifecycle-Zweig werden PDF-/Abschlussbuttons nicht mehr pauschal sichtbar geschaltet.

BEHOBEN:
- Pixel-Haupt-TB: Buttonleiste sprang zwischen nur „BTB Abschluss“ und „PDF Entwurf + BTB Abschluss“.
- Ursache war ein widersprüchlicher UI-Pfad im 600-ms-Lifecycle-Watcher:
  zunächst beide Buttons VISIBLE, danach im Haupt-TB-Zweig PDF Entwurf wieder GONE.
- Der Haupt-TB-Zweig setzt PDF Entwurf jetzt dauerhaft VISIBLE.
- Die Sichtbarkeit wird pro Zyklus nur noch in genau einem Rollen-/Lifecycle-Zweig festgelegt.

UNVERÄNDERT:
- Keine server/**-Dateien geändert.
- BTB-SYNC-002 Outbox-/Dirty-Schutz aus 1.3.23 bleibt enthalten.
- UPD-001 APK-Metadatenprüfung bleibt enthalten.
- Keine App-Daten löschen, keine Geräte neu aktivieren, keine NAS-Änderung.

QUELLSTAND:
- Android-Branch: feature/tb-android-2.0
- BTB-UI-001 Fix: 7fdb4dad2e72515dcda958c00bc2065c38d9f5da
- Versionierungs-/Releasecommit: 446312c8c09a99ed71950fe3b08adc53070ddc91
- Vorgängerstand: 1.3.23 / 200073

TESTSTATUS:
- SOURCE-FIX UMGESETZT
- SOURCE-CHECK PASST
- BUILD OFFEN
- INSTALLATION OFFEN
- PIXEL-UI-E2E OFFEN
- POCO/HELPER-E2E OFFEN

NÄCHSTER TEST:
- 1.3.24 / 200074 frisch bauen.
- APK + genau diese Release-MD gemeinsam im Portal verwenden.
- Pixel aktualisieren.
- Haupt-TB im aktiven BTB prüfen:
  PDF Entwurf + BTB Abschluss müssen dauerhaft gleichzeitig sichtbar bleiben.
- Keine Wechsel mehr zwischen 1-Button- und 2-Button-Zustand.
- HELPER/Poco anschließend separat prüfen.
