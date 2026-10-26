# AND-ERRORLOG-026 – 26.10.2026

Basis Android16dc829 / Server65a14b1, Auftragf617aef. Ziel1.3.34/200084. Nutzer genehmigt ausdrücklich das lokale Fehlerprotokoll. Keine Server-/Rechtekorrektur in diesem Block.

## Bedienung
Einstellungen > Wartung & Diagnose > Fehlerprotokoll. Maximal200 Fehlergruppen, nach letzter Meldung absteigend. Gruppierung nach Fehlercode, Vorgang, lokalem Baustellenbezug und Build. Eine Wiederholung aktualisiert Zähler und letzten Zeitpunkt, bewahrt ersten Zeitpunkt und die ID für direkte Links. „Meldungen“ zählt erfasste Fehlerberichte, nicht garantiert eindeutige HTTP-Requests.

Eintrag antippen: verständliche Meldung, technische Codes, Bereich/Vorgang, Zeiten, Meldungszähler, Build und ggf. lokal aufgelöste aktuelle Baustellenbezeichnung. Der Name wird nicht im Protokoll gespeichert. Einzelkopie/-teilen über Detaildialog; Gesamtexport über Teilen bzw. Mehr > Alles kopieren. Mehr > Protokoll löschen verlangt Bestätigung und leert ausschließlich eigene Diagnosepräferenzen. Neue Hintergrundfehler können danach neue Einträge erzeugen. Dialoge sind an den View-Lifecycle gebunden.

Syncfehlerlink öffnet die vom manuellen Worker gespeicherte ID; kein zweiter Logeintrag allein wegen der Darstellung. Nach einem erfolgreichen neuen Lauf verschwindet der aktuelle Fehlerlink, die Historie bleibt. Ohne aktive Datei-/Baustellenzuordnung steht dies offen in den technischen Details. Die Liste rekonstruiert keine Ereignisse vor Installation dieser Version.

## Speicherung und Datenschutz
Reiner Java-Kern ErrorHistory: feste Operation-/Fehlercode-/Kontext-Enums, Zeiten, Zähler, numerischer Build und optional lokale numerische Baustellenkennung; zufällige Diagnose-ID. Kein Throwable, Stacktrace, Rohantwort, URL, Token, Passwort, Benutzername, Datei-/Fotoinhalt oder BTB-Text wird persistiert. Bekannte Klassen z.B. DNS/Timeout/files.view/HTTP-Status/Versionskonflikt werden aus einer temporären Fehlermeldung erkannt; Unbekanntes wird neutral klassifiziert. Eingabeprüfung auf8192 Zeichen begrenzt.

ErrorLogStore schreibt eine auf200 Gruppen begrenzte JSON-Liste synchron unter eigenem SharedPreferences-Namen im bestehenden RecoveryWorkspace. Gleichzeitige Meldungen lesen/ändern/schreiben unter demselben Lock; fehlerhafte Diagnosepersistenz darf den bestehenden Sync nicht abbrechen. Speicherung ist lokal zum Handybestand und über normale Neustarts erhalten. Keine neue Room-Tabelle, Migration, Netzübertragung oder Service. Einträge werden nicht als „behoben“ markiert, nur weil eine spätere allgemeine Serverprüfung erfolgreich ist.

Export/Kopieren werden ausschließlich durch Nutzeraktion ausgelöst. Export verwendet ausschließlich die sicheren Kernfelder; auch lokal aufgelöste Baustellennamen werden nicht exportiert. Die normalen fachlichen Daten, Rechte, Transport-/Retry-/Recovery-/Cursorregeln sind unverändert.

## Erfasste Fehlerstellen
- Vollsync-Retry/manuelle Fehlerausgabe mit stabiler Log-ID in WorkManager-Daten.
- Selektive BTB-Probleme (lokaler Baustellenbezug), Strukturprobleme (Root-Bezug), Export-/Medienfehler des selektiven Workers.
- Delta-Hintergrundworker und Vordergrundpoller.
- Katalogfehler/fehlgeschlagene lokale Katalogspeicherung und Fehler der sichtbaren Verbindungsprobe. Normales Warten/erwartete Recoverypause sind keine protokollierten Fehler.
- Wartungs-Updateprüfung und APK-Downloadfehler. Kein universeller Android-/Gradle-/Logcat-Crashsammler.

## Tatsächlicher Nachweis
`java com.sun.tools.javac.Main -encoding UTF-8 -d /tmp/tb-error-history-tests app/src/main/java/de/tbhelfer/server/ErrorHistory.java tools/ErrorHistoryRegression.java`
`java -cp /tmp/tb-error-history-tests de.tbhelfer.server.ErrorHistoryRegression`

PASS:26 Prüfungen, einschließlich250 unterschiedlicher Kontextmeldungen zur200er-Grenze, Wiederholungsaggregation, Sortierung/ID/Buildtrennung, sicherer Wiederherstellung der Kernfelder, Uhr-Rücksprung und Nichtweitergabe von Test-Tokens, URLs, E-Mail-/Datei-/BTB-Inhalten. Das ist ein Produktionsmodelltest, KEIN echter Android-Speicher-/UI-/HTTP-/Geräte-E2E. XML-Layouts geparst, strukturelle Kotlinprüfung durchgeführt.

**OFFEN:** Android-Kotlin/Java-Interop, Gradle/KSP/Unit/Lint/APK, echtes Schreiben/Wiederlesen nach Prozess-/Geräteneustart, Anzeige/Link/Copy/Chooser/Löschen, parallele Workerzugriffe mit realen SharedPreferences und Fehlerfall bei nicht beschreibbarem Speicher. Android-SDK/Gradle-Abhängigkeiten fehlen lokal. Keine CI/Actions gestartet ([skip ci]), Budget unverändert, kein neuer Minutenverbrauchsnachweis, keine APK/Veröffentlichung/NAS-/Produktivänderung.

## Abnahme auf Pixel/Poco
1. Build/Unit/Lint, anschließend APK1.3.34/200084 + passende Release-MD als Update installieren.
2. Neue DNS-/files.view-Fehlermeldung auslösen bzw. vorhandenen fehlgeschlagenen Sync wiederholen; Fehlerlink und richtige Klasse/Zeit/Vorgang prüfen. Keine Rechte blind freigeben.
3. Wiederholung -> ein gruppierter Eintrag mit höherem Meldungszähler; Neustart -> weiterhin gespeichert. Bekannte Baustelle auflösen; bei unbekanntem Bezug keinen Namen erfinden.
4. Einzel-/Gesamtkopie und Android-Teilen prüfen, auf vertrauliche Inhalte kontrollieren. Löschen bestätigen -> nur Historie leer; neue Fehler dürfen anschließend wieder erscheinen.
5. Erfolgreicher Sync leert die Historie nicht; andere geschützte Sync-/BTB-/Update-/Recovery-Funktionen praktisch regressionsprüfen.
