TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.32
BUILD: 200082
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Lokale Baustellenanlage erhält einen dauerhaften idempotenten Serverauftrag, ohne Helfereinladung.
- Automatischer Empfang von Baustellen-, Mitgliedschafts- und Einladungsänderungen im bestehenden Deltaweg.

VERBESSERT:
- Wiederaufnahme nach Neustart/Netzrückkehr, eingefrorene Anfragen und Server-ID-Sicherung vor lokaler Zuordnung.
- Baustellenumbenennung mit bestätigter Basisversion; Konflikte erhalten den lokalen Stand.
- Teamkontext wird bei Ereignissen und spätestens beim nächsten Kontext-Kontrolllauf in der laufenden App aktualisiert. Offline/ausgeschaltet keine sofortige Zustellung.
- Ausstehende Änderungen bei Team-/Rechtewechsel werden zur Prüfung erhalten, nicht in ein anderes Team umgehängt.

BEHOBEN:
- Stabile lokale Unterbaustellenkennung bereits vor Serverbindung: Einträge müssen nicht auf die erste Serveranlage warten.
- Lokale Haupt-TB-PDF-Vorschau auch im Entwurf; Helfer erhalten darüber keine PDF-Freigabe.
- Manueller Sync meldet einen beendeten Lauf bzw. offene Übertragungen statt pauschal vollständiger Synchronisierung.

SERVERVERTRAG:
- Passender Serverbranch mit worksite_head_update_v1 für Namensänderungen erforderlich.
- Bestehendes POST /api/v1/worksites; neuer PATCH /api/v1/worksites/{id} mit name, base_version, X-Client-Change-Id.
- Keine DB-Migration. Keine automatische Annahme einer Helfereinladung, keine Namenszusammenführung.
- Existierende Eintrags-, Foto-, Original-, Lifecycle-, Dirty-, 409- und Recovery-Regeln bleiben Grundlage.
- 16-Stunden-Autoabschluss und PDF-Layout sind separate Aufträge und hier nicht umgesetzt.

TESTSTATUS:
- Android-Quelländerung. KOTLIN-/ROOM-KOMPILIERUNG, UNIT, LINT, APK-BUILD UND INSTALLATION OFFEN.
- Lokal kein Android-SDK/Gradle-Cache/Kotlin-Compiler; Abhängigkeitsdownload in dieser Umgebung nicht verfügbar. Kein früheres 200081-Testergebnis auf 200082 übertragen.
- Server: 14 lokale Handler-/SQL-Regressionsfälle (SQLite-Adapter, echte Produktionshandler/-Berechtigungen/-Receipt-Funktionen) und Node-VM-Regression der neuen Portalcontroller bestanden. Das ist kein PostgreSQL-/HTTP-/Android-E2E.
- Keine APK erzeugt/veröffentlicht; DOWNLOAD_URL bleibt leer. Diese MD erst mit einer aus genau diesem Build erzeugten APK verwenden.
- Keine Actions gestartet, keine NAS-/Produktivdatenänderung, kein Deployment.

NÄCHSTER TEST:
- Kotlin/Room/Unit/Lint in Android Studio bzw. ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug.
- Danach APK 1.3.32/200082 mit dieser Release-MD bauen und als Update auf Pixel/Poco installieren.
- Bestehende lokale Baustelle behalten: Eintrag, PDF-Vorschau, genau eine Server-ID und automatische Portal-/Helferübernahme prüfen; Offline-Neuanlage/Neustart, verlorene POST-Antwort, Teamwechsel, Rechteentzug und Paralleländerung prüfen.
