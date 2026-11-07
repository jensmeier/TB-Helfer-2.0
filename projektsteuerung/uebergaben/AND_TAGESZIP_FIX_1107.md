# AND-TAGESZIP-1107 – bestätigter Lifecycle-Absturz

## Befund
Installiert laut Nutzer: 1.3.41/200091. Tages-ZIP schließt App, auch bei neuem BTB ohne Bilder.
Pixel-Logcat bestätigt wiederholt IllegalStateException in BtbVerlaufFragment.teileTagesabschlussZip Zeile248: requireContext auf abgelöstem Fragment im catch(Throwable). Coroutine Cancelling. Ein Protokoll enthält Rotation vor dem Crash; Rotation nicht für alle Versuche belegt. Kein OutOfMemoryError. Der ursprüngliche abgefangene Fehler fehlt im Altlog.

## Änderung
Auftrag87aab774, Runtime7fb98cc28227d93c34104991695075c41a444c36; Version1.3.42/200092.
- Fragment-eigenes ViewModel hält Auftrag, Ergebnis und Fehler über Konfigurationswechsel; nur Application-Kontext im Export.
- View-Lifecycle sammelt Zustand nur RESUMED. Ergebnis wird synchron einmalig übernommen, bevor Android-Teilen startet; keine erneute Freigabe nach Resume.
- Mehrfachstart während Export oder wartendem Ergebnis gesperrt.
- CancellationException weitergereicht; nach Export erneute Aktivitätsprüfung der Coroutine. Keine Fehlertoast-Verarbeitung auf abgelöstem Fragment.
- Sichtbarer Status für Lauf/Erfolg/Fehler. Fehler im bestehenden lokalen Protokoll klassifiziert. Logcat enthält ursprüngliche Exception-Typen und Stackpositionen/Cause-Kette begrenzt, keine Rohmeldungen/Dateipfade/BTB-Inhalte.
- Laden des Verlaufs an View-Lifecycle gebunden; Application-Kontext vor Coroutine erfasst.
- Keine Änderung an ZIP-Inhalten/Gesamtmedien, Foto-/BTB-Zuordnung, PDF, DB, Outbox oder Server/API.
- Kein WorkManager-Hintergrundexport: Verlassen/Entfernen des Verlaufs bzw. Prozessende kann Auftrag beenden. Nach Prozessende neu starten. Rotation und vorübergehender Hintergrund sind vom Prozessende zu unterscheiden.

## Prüfgrenzen
Sechs Coroutine-Tests: Doppelstart; Ergebnis ohne UI behalten/einmalig übernehmen; Cancel ohne Fehler; ursprünglicher Fehler + Retry; intern geschluckter Cancel ohne Erfolgszustand; nächster Export nach Übergabe.
Das sind JVM-Kerntests, keine instrumentierten Fragment-/Rotationstests. Kein adb/SDK/Emulator lokal vorhanden.

Quellvertragsjob128332244354: ursprüngliche Logs gelesen. Bekannte Altfehler13/19/83/85/86 weiterhin. Zusätzlich03 erwartet den direkten Exportaufruf in BtbVerlaufFragment, jetzt in TagesZipViewModel: veralteter Dateimarker, Fachaufruf erhalten. Keine Prüfung deaktiviert/abgeschwächt und keine server/**-Datei verändert. Gesamte CI deshalb ROT. Anpassung von03 an die Delegation getrennt durch zuständiges Projekt erforderlich.

## Geräteabnahme offen
1. Update mit bisherigem Signaturschlüssel, keine Deinstallation/Datenlöschung.
2. Tagesexport ohne/mit Fotos: Teilen öffnet; ZIP am Empfänger öffnen und Inhalte prüfen.
3. Während Export mehrfach drehen: ein Auftrag, Ergebnis am aktiven Verlauf.
4. Home/Hintergrund/Rückkehr: kein Hintergrunddialog, Ergebnis bei Rückkehr.
5. Mehrfachklick: kein paralleler Export.
6. Zurück/Verlauf verlassen: kein Crash; neu öffnen und erneut exportieren.
7. Echter Exportfehler: sichtbare Meldung, Fehlerprotokoll und sichere Logcat-Ursache; erneuter Versuch.
8. PDF-/Foto-/Sync-Regression auf Pixel und Poco. Keine Behauptung E2E BESTÄTIGT.


## Nachweis 07.11.2026
[CI42348785185](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/42348785185), Androidjob128332244601 SUCCESS. testDebugUnitTest inkl. Kotlin/KSP/Java PASS4m55s:224 Tests,0 Fehler/Fehlschläge/übersprungen,6/6 TagesZipAuftragTest PASS. lintDebug PASS1m22s:0 Error/Fatal,390 Warnings,keine neue TagesZip-Issue. assembleDebug PASS2m31s, Manifest/Signatur/Releasepaar PASS. Berichtsartefakt13564688485 heruntergeladen und ausgewertet; SHA25699360be79369d689324742d100aabe5605ba220c1fa21a2ed61ab08d46bc8104.
APK26946383Bytes, SHA2561e570bc32ef58112aa44ca4bcda02f998d374cde89303562ab58e32b301312d2, Manifestde.tbhelfer/1.3.42/200092. Artefakt13564214957 enthält APK+MD desselben Baucommits; Download und Hash verifiziert. Signaturv1/v2 gültig; Debugzertifikatbf29007fcb0bc69854696b0cd61b95d2fc03822a5a7d8fc24b97414258fe9a6d weicht von200091 ab. Installierte Signatur unbekannt, bei Konflikt bisherigen lokalen Schlüssel verwenden, niemals deinstallieren.
Status BUILD BESTANDEN / UNIT UND LINT BESTANDEN / GESAMT-CI ROT / INSTALLATION UND GERÄTE-E2E OFFEN. Ein gezielter CI-Lauf, keine Wiederholung/Budgetänderung. Server unverändert6ea0d987; kein Deployment.
