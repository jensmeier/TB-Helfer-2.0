# AND-SYNC-ANZEIGE-026 – 26.10.2026

Basis Android9b083ab, Server65a14b1; Auftrag vor Implementierung in2a3c81a gesichert. Nutzer berichtet Baustelle im Portal angekommen. Das ist ein positiver Teilnachweis; Screenshot zeigt keine lesbare installierte Version und beweist nicht den vollständigen E2E.

## Umsetzung
Auf „Daten vom NAS“ ein eigener, themefähiger Diagnosebereich oberhalb der bisherigen Statuszeile. Anzeige sekündlich während sichtbarer Seite. Erreichbarkeitsprobe nur bei sichtbarer Seite; GET /api/v1/capabilities mit echtem JSON-/Capabilities-Nachweis,4s Connect-/Read-Timeout und30s Abstand nach Abschluss. Offline, DNS, Timeout, HTTP-Antwort und unerwartete API-Antwort unterschieden. Ältere Messung wird mit ihrem Alter bezeichnet. Kein Token/Server-URL in der Anzeige, keine pauschale Behauptung einer dauerhaften Verbindung oder gültigen Anmeldung.

Livephasen aus UI-Vorbereitung, vollständigem Sync, selektivem Senden und Deltaempfang. Worker erhalten getrennte monotone Lauf- und Schrittzeiten. Normaler Long-Poll ist erkennbar; bekannte BTB-/Strukturscopes werden als solche gezählt. Keine globale Dateianzahl, Prozentzahl oder ETA ohne vollständigen Mengennachweis; „Restzeit: noch nicht berechenbar“. Kein aktiver Monitorlauf bedeutet nicht alle Daten übertragen. Vorhandene Fehler-/Konfliktanzeige bleibt separat bestehen.

Monitor ist ein reines Java-Modell ohne Android-/Netzabhängigkeiten, keine Steuerung des Sync. finally entfernt abgeschlossene/abgebrochene Läufe; parallele Vorgänge und verspätete Phasenrückmeldungen getrennt. Da der vorhandene Buildgenerator normale Javaquellen leert und eine Quellkopie erzeugt, wird dieselbe generierte Ausgabe zusätzlich als Java-Source-Verzeichnis für javac registriert. Keine Änderung an geschützten Generatorankern oder Datenbank-Kotlin-/KSP-Zuordnung.

## Tatsächliche Prüfung
- Java17-Compiler via `java com.sun.tools.javac.Main -encoding UTF-8 -d /tmp/tb-sync-monitor-tests app/src/main/java/de/tbhelfer/server/SyncActivityMonitor.java tools/SyncActivityMonitorRegression.java`; danach `java -cp /tmp/tb-sync-monitor-tests de.tbhelfer.server.SyncActivityMonitorRegression`: PASS,13 Prüfungen einschließlich800 paralleler Monitor-Lebenszyklen. Zuerst fehlte lediglich das javac-Startprogramm im PATH; der vorhandene Java-Compiler wurde anschließend über sein Modul erfolgreich aufgerufen.
- Getrennte Start-/Schrittdauer, gleiche Phase ohne Zeitreset, parallele Läufe, gezieltes Entfernen, späte Callback-Abweisung, echte Leerlaufmeldung, keine erfundene ETA/Prozent, Zeitformat/Grenzen geprüft.
- Layout-XML geparst; heruntergeladene Kotlinquellen strukturell auf Klammern geprüft. Das ist KEIN Kotlin-/Gradle-/Android-Nachweis.
- **Android-Kompilierung einschließlich Kotlin/Java-Interop und generierter Source-Registrierung, KSP, Unit, Lint, APK, Installation, UI-/Netz-/Geräte-E2E OFFEN.** Android-SDK/Gradle-Abhängigkeiten fehlen. Keine vorherige CI auf200083 übertragen.
- Keine Actions gestartet ([skip ci]), kein Budget geändert, kein neuer Verbrauchs-/Ersparnisnachweis. Keine Serverdatei, RecoveryHttp/-Protokoll, API-Mutation, Datenbank, NAS oder Produktivdaten geändert.

## Abnahme
1. Daten-vom-NAS-Seite öffnen; aktuelle TB-Serverantwort und Prüfalter erkennen. Keine zusätzliche Probe nach Verlassen der Seite.
2. Internet aus: Offline-/Fehlerhinweis bei nächster Probe. Wieder online: neue erfolgreiche Messung. DNSfehler, HTTP-Fehler und Timeout gesondert prüfen.
3. Neu laden: Vorbereitung, Baustellen/Teams, Struktur, BTB, Bilder/PDF mit real wachsender Laufzeit sehen. Bei einem lange dauernden Schritt wächst dessen Dauer; keine vorgetäuschte Restzeit.
4. BTB ändern und gleichzeitig Serveränderungen empfangen: getrennte Sende-/Empfangszeilen. Normaler25s-Long-Poll bleibt als normales Warten erkennbar.
5. Seitenwechsel, Prozessende/Neustart, Abbruch und Retry: keine festhängende Aktivitätszeile, keine Doppel-Zähler. Vorgemerkte Scopes bleiben sichtbar, bestehende Fehler-/Konfliktanzeige wird nicht ersetzt.
6. Android Studio zuerst compileDebugKotlin/testDebugUnitTest/lintDebug, danach APK1.3.33/200083 als Update mit passender Release-MD. Keine Deinstallation/Neuaktivierung/Datenlöschung. Für diese Anzeige ist kein neues Serverdeployment nötig;200082-Gesamt-E2E bleibt separat offen.
