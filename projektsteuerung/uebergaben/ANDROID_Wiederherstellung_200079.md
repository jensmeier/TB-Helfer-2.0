# Android 1.3.29 / 200079 – kontrollierter Bestandswechsel

Der Screenshotfehler SERVER_RECOVERY_REBASE_REQUIRED hat einen Android-Ablauf erhalten. Serverstand a471996610a27b2d718f9835bf9c07930f29858f wird unverändert vorausgesetzt. Keine server/**-Änderung und kein NAS-Konfigurationswechsel.

## Verhalten

1. Alle vorhandenen HttpURLConnection-Erzeugungen laufen über RecoveryHttp. Authentifizierte /api/v1-Aufrufe prüfen vor tatsächlichem I/O den aktuellen Server-Recovery-Stand. Direkte Antwortstreams und Headerzugriffe gehören ebenfalls zur Sperre. Fachanfragen an eine inzwischen geänderte aktivierte Serveradresse werden verworfen.
2. Eine neue Recovery-ID sperrt normale Fachzugriffe und führt in den Wiederherstellungsbereich. Vor der Sicherung werden die alten Fachoberflächen beendet sowie Worker/Poller abgebrochen. Der laufende Prozess bleibt für die Sicherung seiner aktuellen Einstellungen erhalten; das Geräte-Token wird nicht gelöscht. Der notwendige Prozessneustart folgt erst nach Sicherung und ausdrücklicher Auswahl.
3. Zwei Room-Datenbanken werden jeweils transaktional logisch exportiert. Verfügbare interne und appbezogene externe Dateien einschließlich Originaldateien werden kopiert; Geschäftseinstellungen werden mit Typinformationen aus dem aktuellen SharedPreferences-Speicher gesichert; commit wartet zusätzlich auf ausstehende apply-Schreibvorgänge. Ein Verzeichnis-Lesefehler bricht die Sicherung ab. Private ZIP-Datei wird abgeschlossen, synchron auf Datenträger geschrieben und mittels CRC sowie Datei-SHA256 geprüft. Gerätetoken/Keystore werden nicht exportiert. Der öffentliche Mein-Ordner bleibt erhalten und wird nicht zum neuen automatischen Sync-Quellbestand.
4. Der Server wird in einem ausdrücklich nur lesenden, auf eine Recovery-ID begrenzten Bereich abgefragt: Geräteanmeldung, zugängliche Baustellen/Berechtigungen, eigene TB-Gruppen-BTBs, Baustellenbaum, Mängel, Abnahmen, gemeinsamer Strecken-/Bahnhofskatalog. Das Ergebnis liegt getrennt im Journal; kein DAO-Import, Cursor-Ack oder Fachupload im Vergleich. Dateien und Vermessungsdetails gehören erst zum anschließenden normalen Sync.
5. Vorgemerkte lokale Änderungen einschließlich Konflikten und unbestätigten Übertragungen sind sichtbar. Eindeutig gefundene Servereinträge werden mit Inhalt und, soweit vorhanden, Versionsabweichung gezeigt. Nicht gefundene Einträge werden ausdrücklich nicht als bestätigte Löschung ausgegeben.
6. Nur die ausdrückliche Auswahl „Serverbestand neu einlesen“ schreibt eine neue lokale Generation. Vorher werden der aktuelle lokale Bestand erneut gesichert, die unveränderten vorgemerkten Änderungen und Geräte-/Serverstand geprüft und ein aktueller Serververgleich erfolgreich abgeschlossen. Dateien/Datenbanken/Einstellungen der alten Generation werden weder verschoben noch gelöscht. Es gibt keine automatische Zusammenführung alter Ergänzungen.
7. Die Auswahl wird atomar gespeichert. Anschließend beendet eine nicht exportierte Activity in einem separaten Prozess den alten Hauptprozess und öffnet die App neu. Room, Dateispeicher und Geschäftseinstellungen der neuen Generation sind getrennt; Geräteanmeldung und aktivierte Serveradresse bleiben global. Erst dieser neue Bestand darf regulär synchronisieren. Ein initialer Vollabgleich wird angefordert.
8. Unter Einstellungen → Wartung & Diagnose → Gesicherter Handybestand können zur aktuellen Gerätebindung gehörende abgeschlossene Sicherungen angesehen und nach Hashprüfung bewusst per FileProvider exportiert werden. Die Pfadfreigabe umfasst nur Wiederherstellungsordner, keine allgemeinen Rootpfade.

## Fehlerverhalten und Grenzen

- Netzwerkfehler, unzugänglicher Speicher, unvollständige Sicherung, abgelehnter Serverzugriff, geänderte Anmeldung oder erneute Recovery-ID führen zu keinem Bestandswechsel. Bei einer fehlgeschlagenen Sicherung werden ausschließlich die vier bekannten Kopie-/Manifestdateien dieses neu angelegten Versuchs entfernt; ursprüngliche Handydaten und ältere abgeschlossene Sicherungen bleiben erhalten.
- Ein bereits ausgewählter Bestandswechsel wird im alten Prozess niemals als gültiger neuer Netzwerkbestand behandelt; vor weiterem I/O ist der Neustart erforderlich.
- Bei einer anderen Gerätebindung in einer bereits getrennten Generation wird der automatische Zugriff auf den vorherigen Handybestand verweigert. Eine aufgehobene Gerätefreigabe wird hier nicht durch neue Schlüssel oder eine geänderte NAS-Konfiguration umgangen.
- Der Vergleich ist eine Sicht auf die berechtigten Daten zum Abrufzeitpunkt, keine serverweite Transaktion. Weitere Änderungen werden anschließend durch den normalen Versions-/Delta-Vertrag verarbeitet. Bei erneuter Wiederherstellung sperrt der Headervertrag neue Fachanfragen wieder.
- Alte lokale Ergänzungen verbleiben im erhaltenen Handybestand und in der Sicherung. Die automatische oder komfortable fachliche Übernahme einzelner Ergänzungen in den frischen Bestand ist nicht Teil dieses Blocks.
- Der Export ist eine private Datensicherung mit SQL-Schema/typisierten JSON-Zeilen und Originaldateien, keine direkt im normalen Sync importierbare Outbox und kein installierbares Paket.

## Technische Prüfung

- Getesteter Runtime-Quellstand: `d3df3108fd66920c7c1bc1bfc93d5817eacedf33`.
- CI: [39947580253](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39947580253), Android-Compiler sowie testDebugUnitTest PASS, lintDebug PASS. Tatsächliche JUnit-XML: 88 Tests, 0 failures/errors/skipped. RecoveryPolicyTest 5, RecoveryHttpTest 6, DeviceAuthorizationTest 4 Methoden PASS.
- Lint-XML: 0 Fehler, 391 Warnungen; keine Warnung in neuen recovery-Dateien.
- Fünf historische Source-Checks 13/19/83/85/86 bleiben rot. Der Android-Compiler-/Unit-/Lint-Job ist erfolgreich; keine Checks deaktiviert/mit alten Versionszeichenfolgen erfüllt.
- XML-/direkte HTTP-Erzeugungs-/Worker-/Startbarrier-/No-APK-Workflow-Quellchecks PASS. Diese prüfen keine echten Android-Dateizugriffe oder Prozessneustarts.
- Workflow nutzt testDebugUnitTest und lintDebug, ohne assemble und ohne APK-Upload; das heruntergeladene Artefakt enthält nur Prüfberichte. Keine APK erzeugt.
- Installation, Archiv-/Namespace-/Neustartablauf, Pixel/Poco und Live-NAS-/Portal-E2E bleiben ungetestet. Die Gesamtprüfung ist daher nicht als funktional abgeschlossen freigegeben.

## Geräteabnahme (offen)

- Aktualisierung über GitHub Desktop und Android Studio über die bestehende Installation, keine Deinstallation und keine App-Daten löschen.
- Aktiver TB_RECOVERY_ID: Sperre muss vor Fachupload greifen. Vorbereitung darf nur GET-Vergleichsaufrufe mit aktueller Kennung ausführen; keine alten Mutationen oder Acks senden.
- Handy mit lokalen Baustellen/BTBs, vorgemerkten Helferänderungen und Originalfotos: Sicherung prüfen, lokale/Server-Inhalte ansehen, erst bewusst neuen Bestand wählen. Alte Dateien, DBs und vorgemerkte Änderungen müssen erhalten sein.
- Vor der Auswahl abbrechen, App beenden, Netz verlieren, Speicherplatz unterschreiten: keine neue Generation und keine erfolgreiche Sync-Quittierung.
- Prozessabbruch während Sicherung sowie direkt nach atomarer Auswahl: unvollständige Sicherung darf nicht zur Auswahl führen; nach Auswahl muss der nächste Hauptprozess den neuen Bestand verwenden.
- Recovery-ID während des Vergleichs oder erneut nach der Auswahl ändern: Vergleich/Sync muss wieder sperren; keine alten Uploads automatisch wiederholen.
- Frischer Bestand: bekannte Strecke/Bahnhof suchen, Katalog/Bestand nachladen, zugängliche Baustellen und eigene TB-Gruppe öffnen. Original-/Final-PDF- und Helferrechte unverändert prüfen.
- Helfer angemeldet/abgemeldet, Teilnahme beendet/fortgesetzt, anderer TB/andere Gruppe, Portal-Logout gegenüber Gerätefreigabe und Offline-Rückkehr gehören weiter zur offenen Praxisabnahme. Kein erfolgreicher Pixel-/Poco-/NAS-E2E-Nachweis wird behauptet.
