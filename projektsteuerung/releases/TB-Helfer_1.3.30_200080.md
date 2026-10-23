TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.30
BUILD: 200080
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Unterbaustellen (WORKSITE_NODE) im schnellen Änderungsabgleich zwischen Portal und Android.
- Getrennte Anzeige für lokal gespeichert, ausstehende/laufende Übertragung, Serverbestätigung und Konflikte.
- Reaktiver Übertragungsstatus der Unterbaustellen in der Baustellenliste.

VERBESSERT:
- Bahnhof/Name bzw. Kürzel und Gleis/Weiche bleiben auch bei vorhandenem Kurzname in Unterbaustellen-, BTB- und Fotoangaben eindeutig erkennbar.
- Beim Speichern werden weiterhin gezielt der betroffene BTB und seine Bilder synchronisiert; geänderte Struktur wird zusätzlich baustellenbezogen abgeglichen.
- Vorhandene Knotenschlüssel, lokale IDs, Zuordnungen und zusätzliche Serverfelder bleiben erhalten.

BEHOBEN:
- WORKSITE_NODE-Ereignisse werden nicht mehr ungeprüft beim Fortschreiben des schnellen Änderungs-Cursors übersprungen.
- Fehlende Server-BTB-ID startet keinen breiten Bildabgleich.
- Ältere Übertragungen bestätigen keine neueren lokalen Änderungen.
- Vorhandene Konflikte werden im selektiven Abgleich nicht durch automatischen Neuabgleich übergangen.
- Lokal vorgemerkte Helfer-Strukturänderungen werden durch einen rein lesenden Abgleich nicht überschrieben oder als übertragen bestätigt.

UNVERÄNDERT:
- Offline-Outbox, Versionskonfliktschutz und kontrollierter Wiederherstellungsablauf aus 1.3.29 bleiben erhalten.
- Keine server/**- oder NAS-Konfigurationsänderung; keine neue sichtbare Versionsanzeige.

TESTSTATUS:
- 1.3.30 / 200080 ist für den nächsten lokalen Android-Studio-Build vorbereitet.
- Versions-/Build-/Dateinamen- und Pflichtfeldprüfung bestanden.
- Vorheriger Runtime-Quellstand 60e8dca5ae509bd6444302d13025996f9283f35b unter 1.3.29 / 200079: Kotlin-Kompilierung und 98 Unit-Tests bestanden (0 Fehler/Fehlschläge/übersprungen); lintDebug bestanden (0 Fehler, 391 Warnungen). CI-Lauf 39996754175.
- Diese bisherigen Nachweise sind kein APK-Buildnachweis für 200080. Für 200080 wurde hier weder eine APK erzeugt noch ein neuer Gradle-/CI-Lauf gestartet.
- Fünf bekannte historische Quellvertragsprüfungen (13/19/83/85/86) bleiben offen.
- Installation, Updatefähigkeit sowie Pixel-/Poco-/NAS-/Portal-E2E weiterhin offen.

NÄCHSTER TEST:
- Android-Branch in GitHub Desktop aktualisieren und in Android Studio bauen.
- APK-Manifest vor Weitergabe auf Version 1.3.30 und Build 200080 kontrollieren.
- Nur die tatsächlich daraus erzeugte 200080-APK mit dieser Release-MD gemeinsam verwenden; keine ältere APK umbenennen.
- Geräteprüfmatrix: projektsteuerung/uebergaben/ANDROID_WORKSITE_NODE_023.md.
- Kein Deployment oder Release-Upload aus diesem Korrekturauftrag.
