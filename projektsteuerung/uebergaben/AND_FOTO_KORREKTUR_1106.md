# Foto-Sync, Teilen und Receiver – 06.11.2026

## Umfang und Ursachen
Basis Android 0b1951a0063a96eb6521d038ae92e772ba7c608d, Server 6ea0d987bebc67c346fca24714e4e1b9a8c852ea. Auftrag vor weiterer Implementierung in 2d31ea8 gesichert. Funktionscommit ca7803266a272099ac09c5f6e47599411190733f.

Die vorhandene Sync-Korrektur 0b1951a0 wurde weiter geprüft: Voll- und FILE-Delta dürfen vorhandene eigene Bilder über stabile Baustellen-/BTB-/Teamzuordnung oder geprüften SHA256 wiederverwenden; fehlende Portal-/Helferbilder benötigen weiterhin Preview-Downloads. Ein älterer photosJson-Snapshot allein beweist keinen Unlink. Gemeinsames MediaSyncGate serialisiert Upload/Import und Datei-Refresh. Serverdateivertrag files.py/image_renditions.py am aktuellen Server-HEAD gegengeprüft: SHA256 der Uploadbytes, stabile Datei-ID und getrennte Preview-Rendition vorhanden. Kein Serverumbau.

## Zusätzliche Korrekturen
- FotoDetailFragment: Öffnen und Teilen fangen Datei-/Provider-/Intentfehler ab; CancellationException wird weitergereicht.
- SucheFotosFragment: Mehrfachteilen nutzt denselben Pfad und ClipData für alle URIs. Nicht lesbare Dateien führen zu einer sichtbaren Fehlermeldung statt stillem Weglassen. Foto-Auswahl-ZIP fängt Erstellung-/Teilenfehler ab; Kontext und Auswahl werden vor IO festgehalten.
- ShareFiles: bestehende freigegebene Pfade bleiben ohne Kopie. Intern geschützte Fotos werden auf IO als einzelne, unveränderte Dateien in einen generation-getrennten Übergabeordner unter raw.cacheDir/share-outgoing kopiert; FileProvider erhält ausschließlich diese schmale zusätzliche cache-path-Freigabe. Keine globale interne Rootfreigabe.
- ShareStaging: getrennte UUID-Verzeichnisse verhindern Überschreiben gleichnamiger Fotos. Kopierfehler räumen die angefangene Kopie auf; Original bleibt erhalten. Fünf JVM-Fälle für Originalerhalt, gleiche Namen, fehlende/leere Quelle und blockiertes Ziel.
- TBHelferApp: attachBaseContext erhält den rohen Android-Systemkontext. Nur dieselben sechs zuvor umgeleiteten privaten Speicherzugriffe delegieren an RecoveryWorkspace. Generation, Registry, Dateipfade und Datenbankbenennung unverändert; keine Bestandsmigration oder Rücksetzung.

## ZIP und Bestandskonflikte
Keine eigenständige Ursache des gemeldeten Verlauf-ZIP-Absturzes bewiesen. BtbExportPaket/ExportUtils besitzen uniquePath/uniqueZipPath; MergeSafeBaustellenExport sperrt doppelte Zielnamen; GesamtMedienArchiv dedupliziert Ausgabepfade. Zwei gleich aussehende Fotos allein beweisen keinen ZipException. Speicherlast durch readBytes bleibt ein möglicher, nicht am Gerät nachgewiesener Folgeprüfpunkt. Die konkrete Verlauf-ZIP-Aktion erneut nach Installation prüfen, bei erneutem Fehler den zugehörigen Crashtrace auswerten.

Bestehende Dubletten nicht gelöscht oder zusammengeführt. Empfehlung: erst Server-Datei-ID/lokale Foto-ID/BTB-Verknüpfungen des konkreten Paars vergleichen; nur eindeutige Identität zusammenführen, sämtliche Eintrags-/BTB-Bezüge erhalten. Unterschiedliche Zuordnungen sichtbar zur Auswahl stellen. Keine Rekonstruktion nach Text, Uhrzeit oder Bildähnlichkeit.

## Abnahme am Gerät (offen)
1. Bestehenden Datenbestand als Update mit bisheriger Signierung behalten.
2. Eigenes neues Foto aufnehmen, zweimal synchronisieren: ein Foto, Originalpfad bleibt erhalten.
3. Portal-/Helferfoto empfangen, Einzelbild öffnen/teilen und Mehrfachauswahl teilen.
4. Widget nach App-Neustart und Launcher-Update starten, bestehende Entwürfe/BTBs/Offlinebestand prüfen.
5. Gemeldeten Verlauf-ZIP und Foto-Auswahl-ZIP wiederholen; unlesbare Datei muss eine Meldung statt Prozessabsturz liefern.
6. Echten Portal-Unlink, parallelen Upload/Datei-Delta und Recovery-Generation am Gerät prüfen.

Keine APK, Versionserhöhung, Installation, NAS-Übernahme oder server/**-Änderung. Beide bestehenden PRs melden gegenüber main mergeable=false; die Branches wurden für diesen Fehlerblock nicht zusammengeführt. Empfehlung für später: Integration beider Arbeitsbranches getrennt mit Vertrags-/Gerätetests planen.

## Technische Nachweise
CI https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/42232622122 , Runtimecommit ca7803266a272099ac09c5f6e47599411190733f. Androidjob127942055768 SUCCESS: testDebugUnitTest inklusive Kompilierung BUILD SUCCESSFUL4m13s; lintDebug BUILD SUCCESSFUL1m39s. Tatsächlich heruntergeladene XML:218 Tests,0 Fehler/Fehlschläge/übersprungen; LocalMediaReuse17/17 und ShareStaging5/5 PASS. Lint390 Warning,0 Error/Fatal; keine Issue in den aktuell ergänzten Kotlin-Dateien. Artefakt13509833054, ZIP-SHA25690bb68b4f73a4fe726f56900c38bbadf0c79541b7d33aedbfe6b4c1216152d11.
Gesamt-CI FAILURE: Quellvertragsjob127942055476 mit bekannten Checks13/19/83/85/86 (alte Schreibpfad-/DELETE-/Versions-/Update-Textanker); Original-Logs gelesen. Keine Tests abgeschwächt. Vorlauf42231292601 für0b1951a0 ebenfalls Androidjob SUCCESS, Gesamt-CI FAILURE.
Lokale XML-/Speicherdelegations-/Share-Aufrufstellenprüfung PASS. Kein adb/Emulator verfügbar, keine echte Broadcast-/FileProvider-/Recovery-/Pixel-/Poco-/NAS-E2E-Prüfung. Source-/JVM-Nachweis ersetzt keinen Android-Systemtest.
