# TB-Server 1.0 – Cutover- und Reset-Regeln

Stand: 2026-09-17

Dieses Dokument konkretisiert den Umstieg vom aktuellen Testserver auf TB-Server 1.0.

## 1. Aktueller Datenstatus

Der derzeitige Serverbestand ist Testbestand.

Nach aktuellem Projektstand existieren auf dem Server noch keine produktiven Vermessungs-/Streckendatensätze, die beim TB-Server-1.0-Cutover erhalten oder migriert werden müssen.

Damit dürfen beim kontrollierten Neuaufbau insbesondere zurückgesetzt/gelöscht werden:

- Testbenutzer;
- Testfirmen;
- Testgerätebindungen;
- Testbaustellen und Unterbaustellen;
- Test-TB-Teams und Testberechtigungen;
- Test-BTBs, Testfotos, Testmängel und Testabnahmen;
- Test-Syncqueues, Change-Feeds und Tombstones;
- serverseitige Test-/Leerbereiche für TRA, KF, GRA, KOR, KV, GVP und sonstige Vermessungsdaten;
- serverseitige Testkatalogeinträge für Strecken-/Bahnhofs-/Vermessungsdaten, soweit sie keine produktiven Daten darstellen;
- sonstige TB-Helfer-Testdateien im bisherigen Server-Dateispeicher.

Quellcode, GitHub-Versionierung, Konfiguration/Secrets und Backups sind davon nicht betroffen. Statische fachliche Quelldaten, die aus dem Repository oder einer definierten Importquelle neu erzeugt werden können, werden bei TB-Server 1.0 kontrolliert neu eingespielt statt aus einem Testserverzustand übernommen.

## 2. Sicherheitsregel vor dem Reset

Obwohl der Bestand als Testbestand verworfen werden darf, gilt weiterhin:

1. vor jeder produktiven NAS-/PostgreSQL-Änderung vollständiges Backup erstellen;
2. Backup auf Lesbarkeit/Vollständigkeit prüfen;
3. alten Serverstand während des 1.0-Cutovers zunächst als Rückfallmöglichkeit behalten;
4. neuen Server 1.0 mit frischer Datenbank/frischem TB-Helfer-Dateibereich aufbauen;
5. erst nach bestandenem End-to-End-Test auf den neuen Server umschalten;
6. alten Testbestand erst danach endgültig entfernen.

## 2a. Restore-Strategie

Ein Restore wird nicht direkt in die laufende produktive Datenbank geschrieben.

TB-Server 1.0 stellt ein verifiziertes FULL-Backup zunächst in eine getrennte Ziel-Datenbank und einen getrennten Ziel-Storage wieder her. Diese Zielinstanz wird offline/testweise geprüft. Erst wenn Datenbank, Dateien, Migrationen und Anwendung dort konsistent sind, erfolgt ein eigener kontrollierter Cutover.

Damit bleiben während des Restore-Vorgangs die bisher laufende Instanz und ihre Dateien als Rückfallmöglichkeit unverändert.

## 3. Regel ab dem ersten produktiven Vermessungsimport

Mit dem ersten echten produktiven Import ändert sich die Regel verbindlich:

- Originale TRA/GRA/KF/KOR/KV/GVP und sonstige Vermessungsoriginale bleiben unverändert;
- mobile oder Portal-Bearbeitung überschreibt diese Originale niemals;
- berechnete, manuell bearbeitete oder zusammengeführte Daten werden getrennt als Arbeitsdaten/versionierte Ableitungen gespeichert;
- produktive Originale dürfen bei späteren Serverupdates nicht durch einen Reset gelöscht werden;
- spätere Migrationen müssen produktive Daten erhalten oder kontrolliert transformieren.

## 3a. Technische Staging-/Cutover-Basis

Der vorbereitete Stack liegt in `server/docker-compose.v1.yml` und trennt PostgreSQL, API, Background-Worker, Fach-Storage und Backup-Root. Migrationen laufen vor API/Worker als eigener einmaliger Dienst.

Für den NAS-Cutover werden neue, getrennte persistente Pfade für Datenbank, Fach-Storage und Backups verwendet. Der alte Server bleibt bis nach bestandenem End-to-End-Test als Rückfallmöglichkeit erhalten.

Cloudflare wird erst nach erfolgreicher Staging-/Akzeptanzprüfung auf den neuen API-Dienst umgeschaltet. Tailscale bleibt Wartung/Administration.

## 4. TB-Server-1.0-Baseline

Der erste produktive TB-Server-1.0-Stand wird deshalb aus einer sauberen Baseline aufgebaut und nicht durch das Mitschleppen des bisherigen Testschemas definiert.

Ziel:

- frisches fachliches Schema;
- klare UUID-basierte Identitäten;
- saubere Modulgrenzen;
- unveränderliche nummerierte Migrationen ab der 1.0-Baseline;
- expliziter Testdaten-Reset nur für Entwicklungs-/Testsysteme;
- kein automatischer Reset eines später produktiven Systems.
