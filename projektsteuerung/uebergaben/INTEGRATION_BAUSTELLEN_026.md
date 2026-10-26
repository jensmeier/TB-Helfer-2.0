# AND-BIDIR-026 – Umsetzung und Abnahme

26.10.2026. Gemeinsame Entwicklung auf ausdrücklichen Nutzerauftrag; diese Eigenprüfung ersetzt keine unabhängige Review.

## Android
Version 1.3.32/200082. Dauerhafte Anlage-/Namensaufträge, automatische Erstbindung ohne Helfer, lokale Node-Keys, Haupt-TB-Vorschau, Kontext-/Teamfeed, Rechte-/Teamwechsel-Schutz und ehrliche Pending-Anzeige im Quellstand implementiert. Bestehenden lokalen Entwurf nicht löschen, App nicht neu aktivieren und keine gleichnamige Portalbaustelle als Ersatz anlegen.

## Server/NAS-Übernahme
Geänderte Runtime-Dateien im Serverbranch:
- server/tbserver/worksites.py
- server/tbserver/capabilities.py
- server/tbserver/sync.py
- server/templates/v1_worksites.html
- server/templates/v1_worksite.html

Keine neue DB-Migration. Erst nach gesondertem Deploymentauftrag den geprüften Serverstand übernehmen/API neu starten und anschließend Portal neu öffnen. Hier wurde nichts auf NAS installiert. Neue Capability worksite_head_update_v1 sowie PATCH und SSE danach praktisch prüfen. Normale BTB-/Foto-/PDF-Lifecycleverträge unverändert.

## Ausgeführte lokale Prüfung
- python -m unittest discover -s server/tests -p test_worksite_head.py -v: 14/14 PASS. Produktionshandler, Versionsschutz, Receipt-Replay bei verlorener Antwort, Firmen-/Mitgliedschafts-/Rechtefilter und Pydantic-Payloadgrenzen; SQLite-Adapter statt PostgreSQL, Audit/Eventaufrufe erfasst statt echte Change-Hub-Laufzeit.
- node server/checks/158_worksite_list_live_regression.cjs: PASS. Produktions-JS für Single-flight/queued refresh, Typfilter, Debounce, Reconnect/Focus/Intervall/Cleanup und Basisversion beim Umbenennen.
- Python-Syntax der drei geänderten Runtime-Module PASS.
- Fehlversuche im neuen Testharness (Dataclass-Dekorator und Cursor.fetchall) korrigiert; danach vollständiger gezielter Testlauf bestanden. Keine Assertions abgeschwächt.
- Android-SDK/Gradle-/Kotlin-Compiler fehlen lokal; Abhängigkeiten nicht abrufbar. KOTLIN/ROOM, UNIT, LINT, APK-BUILD OFFEN. Kein 200081-Nachweis wird als 200082-Erfolg ausgegeben.
- Keine GitHub Actions gestartet ([skip ci]), Budget unverändert. Kein belegter aktueller Minutenverbrauch, keine errechnete Ersparnis. Keine APK/Veröffentlichung/NAS-Änderung.

## Gemeinsame Geräteabnahme – alle OFFEN
1. Haupt-Handy: lokale Baustelle ohne Helfer -> Eintrag mit Unterbaustelle und ohne Uhrzeit/KM -> PDF-Entwurf -> genau eine identische Server-ID im Portal.
2. Offline-Neuanlage samt Eintrag -> Neustart -> online; Antwort nach erfolgreichem POST gezielt verlieren; währenddessen umbenennen; keine Dublette, Node-Key unverändert.
3. Neue Portalbaustelle für denselben Nutzer bei offener App; neue Unterbaustellen und aktiver Team-BTB erscheinen ohne Neuaktivierung.
4. Namen/Unterbaustellen in beide Richtungen; offene Portal-Liste und Detailansicht ziehen nach. Gleichzeitige Namensänderung bleibt 409, lokale Eingabe erhalten.
5. Einladung am Handy UND Portal; Helfer nimmt bewusst an; richtiges Team/BTB/Bereich. Kein automatischer Beitritt allein durch Einladung.
6. Helfer entfernen/Team wechseln, auch mit Dirty-/Pending-/Offline-Daten. Alte Daten lesbar und erhalten, keine Zuordnung zum neuen Team; Rechte auch nach Offline-Neustart prüfen.
7. Regression der geschützten BTB-SYNC-002, BTB-UI-001, PORTAL-LIVE-001, UPD-001 sowie Schicht Ende/Abschluss und Original-/Foto-/Dirty-/409-/Recovery-Schutz auf Pixel/Poco.
8. Pending/Konflikt/fehlender Serverstand zeigt keinen pauschalen Vollständigkeitserfolg.

Android Studio: erst compileDebugKotlin/testDebugUnitTest/lintDebug, dann bei Erfolg assembleDebug und APK zusammen mit projektsteuerung/releases/TB-Helfer_1.3.32_200082.md. Installierter Altstand laut vorherigem Screenshot1.3.31/200081; Byteidentität/NAS-Commit nicht erneut verifiziert. Siehe gemeinsamer Vertrag ../vertraege/BAUSTELLEN_SYNC_026.md.
