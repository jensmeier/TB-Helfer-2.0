# AND-FOTO-SHARE-ZIP-1106 – Untersuchung
Stand06.11.2026. Android49c5429d9692d92498df57898ddb25b5bba2ed67 (Quellversion1.3.40/200090), Server6ea0d987bebc67c346fca24714e4e1b9a8c852ea; PR11 offen, PR6 Draft offen. Installierter APK-Build nicht unabhängig bestätigt. Screenshotpfad generated/source/btb200043 ist ein historischer Generatortaskname, keine installierte Buildnummer.

## Rückmeldung
Fotos in Suche paarweise (188 angezeigt), linkes Bild teilbar, rechtes führt laut Nutzer zum App-Absturz. Tagesabschluss-ZIP ebenfalls Absturz, kein nutzbares ZIP. Nutzer nennt05.06.26; sichtbarer Verlauf zeigt05.11.2026, dieser Tag ist vorläufig gemeint. Build-Screenshot zeigt BUILD SUCCESSFUL mit sieben Warnungen, keinen Laufzeit-Stacktrace.

## Belegte Quellbefunde
1. CollabSyncRepository speichert heruntergeladene Vorschauen in context.filesDir/server_sync/collab/<worksite>. dateipfade.xml enthält weder diese files-path-Freigabe noch einen passenden begrenzten Share-Cache. FotoDetailFragment.openExtern/shareExtern ruft FileProvider.getUriForFile ohne Ausnahmebehandlung auf; ebenso Auswahlteilen in SucheFotosFragment. Bei Datei im genannten internen Ordner scheitert URI-Erzeugung an fehlender Root. Lokaler XML-/Pfadcheck bestätigt fehlende Freigabe. Kontext7/AndroidX-FileProvider-Dokumentation konsultiert. Das ist ein konkreter Codefehler, der zum berichteten links/rechts-Verhalten passt; die tatsächlichen Pfade des gezeigten Paars und dessen Crashtrace liegen noch nicht vor.
2. SucheViewModel erhält FotoRepository.alleFotos/FotoDao-Bestand und filtert/sortiert ohne kanonische Medienidentitätszusammenführung. Die vorausgegangene Exportbereinigung in GesamtMedienArchiv gilt nicht für diese Suche. BtbTeamMediaSync verhindert neue Eigenimport-Dubletten über gespeicherte Server-ID→lokale-ID und Dateipfad, bereinigt vorhandene doppelte Datensätze jedoch nicht. Lokales Original plus Download ist eine plausible Erklärung des Paars, kein am Nutzerbestand bewiesener Befund. Keine Löschung nach Zeit/Text/Bildähnlichkeit veranlassen.
3. Verlauf-ZIP verwendet den gemeinsamen Exportkern einschließlich Vorab-Sync, Roharchiv, Normalisierung, Gesamtmedien und Archivprüfung. Der gewählte Tag grenzt BTB-Dokumente ein; Medien umfassen vertragsgemäß die gesamte Baustelle. Mehrere Binär-/Hash-/Normalisierungsschritte verwenden readBytes; das ist ein Speicher-/Laufzeitrisiko, kein Beweis eines konkreten OutOfMemoryError. Der Handler fängt Throwable, verwendet im Fehlerfall aber erneut requireContext; Fragmentablösung ist ein weiterer möglicher Fehlerpfad. Keine Crashursache behaupten, bevor Logcat vorliegt.
4. Kein server/**-Eingriff, keine Daten-/Original-/Zuordnungsänderung, keine Versionsanhebung und kein neuer Build. Bestätigte Sprachfunktionen unberührt.

## Nachweise und Grenzen
Live-HEADs, PRs, Einstieg00, Regeln und Steuerungsdateien01–08 gelesen; relevante Such-/Foto-/Freigabe-/Export-/Sync-/Gradle-Dateien an gepinntem HEAD geprüft. Lokaler XML-Pfadcheck und Aufrufstellenprüfung, kein Android-Runtime-Test. Kein adb verfügbar/kein angeschlossenes Gerät in dieser Umgebung. Frühere196 Tests sind kein Nachweis dieser Fehlerbehebung. Status: QUELLBEFUNDE DOKUMENTIERT / GERÄTEURSACHE TEILWEISE OFFEN / NICHT BEHOBEN.

## Nächster notwendiger Schritt
Vom Nutzergerät aktuelle Version/Build und Logcat des rechten Foto-Teilen sowie des ZIP-Exports getrennt erfassen (FATAL EXCEPTION/Caused by; bei Prozesskill auch System-/Speichermeldung). In Foto-Details bereits sichtbare Pfade des linken/rechten Bildes vergleichen. Danach begrenzte sichere Share-Freigabe/-Staging und Fehlerbehandlung, identitätsbasierte Anzeige/Import-Reparatur ohne Datenverlust sowie anhand Stacktrace gezielte ZIP-Korrektur. Keine globale Rootfreigabe, kein App-Reset, keine produktiven Daten löschen.

## Pixel-Logcat06.11.2026 – zwei Laufzeitfehler bestätigt
Status: URSACHEN TEILWEISE BESTÄTIGT / NICHT BEHOBEN.
- Nutzerprotokoll Google-Pixel-8-Android-17_2026-11-06_144127.logcat gelesen:8262 Meldungen. Rohlog nicht ins Repository übernehmen.
- 14:40:33 Europe/Berlin: IllegalArgumentException in FotoDetailFragment.shareExtern:205, FileProvider.getUriForFile. Datei liegt unter internem files/recovery-workspaces/<generation>/files/server_sync/collab/<worksite>/<file>_mobile_preview.jpg. Fehlende Freigabe damit am Gerät bewiesen; nur server_sync im unverschachtelten filesDir freizugeben wäre für diesen Bestand unzureichend. Begrenztes sicheres Share-Staging oder exakt passende Freigabe unter Wahrung der Recovery-Grenzen prüfen, keine globale Rootfreigabe.
- 14:40:38: ANR beim Fokuswechsel nach dem Share-Crash; kein eigenständiger ZIP-Stacktrace daraus ableiten.
- 14:41:04: zweiter FATAL EXCEPTION beim Systemstart des SpeechWidgetProvider. ClassCastException: RecoveryWorkspace.WorkspaceContext cannot be cast to android.app.ContextImpl, in ActivityThread.handleReceiver. TBHelferApp.attachBaseContext wickelt den Application-Basiskontext global in RecoveryWorkspace.wrap. Zusammenhang im Quellcode belegt; Korrektur muss Android-Systemkontext und private Speicherumleitung trennen, ohne Recovery-/Offlinebestand zurückzusetzen.
- Log beweist heruntergeladene Vorschau als geteilte Datei, nicht die Herkunft/Identität des linken Originalbildes. Dubletten-Zuordnung weiter prüfen.
- Kein eindeutiger ZIP-Export-Stacktrace, kein OutOfMemoryError in diesem Protokoll. Zweiter Crash kann laufende App-Arbeit abbrechen; konkrete Zuordnung zur ZIP-Aktion noch offen.
- Nächster Schritt: beide belegten Fehler gezielt korrigieren, eigene Medienidentität im Sync prüfen; danach ZIP erneut testen und nur bei verbleibendem Fehler neues Protokoll anfordern. Kein Code-/Build-/Installationserfolg aus dieser Logauswertung.


## AND-FOTO-SYNC-1106 – vertiefter Quellbefund vor Korrektur
- Auftrag77121b60. Voll- und FILE-Delta-Abgleich verwerfen vorhandene eigene Uploaddateien allein wegen fehlender Preview-Endung; beide Wege korrigieren.
- Zweiter konkreter Ablauf: Server1AndroidSyncCoordinator lädt BTB-Snapshots vor BtbTeamMediaSync. Letzterer lädt neue Bilder hoch und merkt deren Zuordnung, prüft danach Portal-Unlinks aber gegen denselben älteren photosJson-Snapshot. Fehlt das gerade hochgeladene Bild dort, entfernt er die neue BTB-Zuordnung und Server-ID→Foto-ID wieder. Damit ist ein Quellpfad für verlorene Eigenidentität und spätere Kopien belegt; das konkrete Fotopaar am Pixel bleibt ohne DB-Paarvergleich unbewiesen.
- Korrekturgrenze: fehlender Snapshot-Eintrag allein darf keinen Unlink beweisen. Zusätzlich explizit entfernten/geänderten Dateilink prüfen; Originaldatei erhalten. Gleichzeitige Medienläufe serialisieren. Lokale Dateiwiederverwendung ausschließlich über bestehende stabile Zuordnung im selben BTB/Baustellenkontext oder bytegleichen SHA256; bei fehlender Datei kleine Servervorschau weiterhin laden.
- Bestehende uneindeutige Doppelpaare nicht automatisch löschen/zusammenführen. Share-/Widget-/ZIP-Fehler bleiben separat offen. Tests müssen Upload→alter Snapshot→erneuter Abgleich sowie echten Unlink und fremde Bilder abdecken.

## AND-FOTO-SYNC-1106 – Korrektur zur Prüfung
Status: QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / UNIT-LINT-CI OFFEN / E2E OFFEN.
- Auftrag77121b60, vertiefter Befund28bfb6aa. Android-Mediensync verwendet vorhandene Dateien über stabile BTB-/Baustellen-/Teamzuordnung oder geprüften Datei-SHA256; Vollabgleich und FILE-Delta nutzen denselben Resolver. Fehlende Portal-/Helferbilder bleiben Preview-Downloads.
- Ein alter BTB-Snapshot darf eine gerade hochgeladene Fotoidentität nicht entfernen: Unlink verlangt zusätzlich einen entfernten/geänderten Dateilink. Eigene neue Uploads und normale Importe laufen mit Datei-Refresh unter gemeinsamem Coroutine-Mutex.
- 17 JVM-Regressionsfälle ergänzt (Dateireuse, Cacheverlust, fremde Bilder, Hashänderung, alter Snapshot/Unlink, Parallelität/Abbruch). Neun lokale Integrations-Source-Checks PASS; noch kein JVM-/Gradle-/Gerätenachweis dieser Änderung.
- Keine bestehende Dublette gelöscht, keine Room-/API-/Server-/Versionsänderung und kein APK. Share-/Widget-Crashes sowie konkrete ZIP-Ursache bleiben separat offen. Bereits verlorene Zuordnungen nicht durch Bildähnlichkeit rekonstruieren.
- Nächster Schritt: gezielte Android-CI mit Unit/Lint; danach Nutzergerät testen. Aktueller Quellversionswert unverändert1.3.40/200090, keine auslieferbare neue APK erzeugt.
