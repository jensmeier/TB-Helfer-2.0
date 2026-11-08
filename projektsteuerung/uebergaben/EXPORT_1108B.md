# EXPORT-1108B – Abschluss 08.11.2026

Auftrag: „alles bauen“. Word unverändert lassen; PDF-Unterbaustellen vollständig anzeigen.

## Umsetzung
Android 1.3.44/200094, Baucommit 2d2ac0ee3b2a815781f5cfee0f3f281a2a8ad74c auf feature/tb-android-2.0 (PR 11).
- PDF-Ortsanzeige enthält Bahnhof und Gleis/Weiche auch bei Kurzname; vorhandene Abschlussdateien werden nicht überschrieben.
- Bilderliste_gesamt.xlsx: kompakte Hauptliste (10 Spalten), separates Detailblatt, echte Datum-/Zeitwerte, Filter, fixierte Kopfzeile, Zeilenhöhen, Querformat und wiederholte Druckkopfzeile. Bilddateiname bleibt für das bestehende optionale Suchmakro erhalten.
- Relative Bildlinks inklusive Sonderzeichen; gesamte entpackte Ordnerstruktur gemeinsam verschieben. XLSX allein enthält keine Bilddateien.
- Mängelliste und Abnahmeliste als XLSX im Baustellenarchiv; ein Vorgang pro Zeile, zugeordnete Fotos auf separatem Blatt. Fehlende Exportdateien ausdrücklich markiert, keine erfundenen Links.
- Bilderliste_gesamt.csv entfernt; andere CSV/PDF erhalten. Beide Baustellenexportpfade berücksichtigt; Offline-Einzel-BTB behält seinen bisherigen Umfang.
- Word-Generator und Textaufbau unverändert geprüft. IDs, Daten, Sync, Rechte, Originalbilder, drei Fotospalten und Fotoauswahl geschützt.

Server feature/tb-server-1.0: geprüfter HEAD 2a97293445c0c51ff0d148b9f4ab29871b7804b9 (PR 6).
Vollständige Unterbaustellenanzeige bereits im Renderer vorhanden. Nur echter PDF-Regressionstest ergänzt, keine Server-Runtimeänderung für diesen Auftrag. Gleichnamige MBG-Unterbaustellen W 99W1/W 99W4 im erzeugten PDF geprüft. Parallel dokumentierte NAS-Abweichung bleibt offen; keine produktiven Änderungen.

## Nachweise
- BUILD BESTANDEN: CI 42479073804, Buildjob 128726829680 SUCCESS.
- SOURCE-TEST BESTANDEN: 233 Android-Unit-Tests, 0 Fehler, einschließlich 5 neuer Exporttests (Ordner verschieben/Sonderzeichen, zweite Tabelle, Datum/Zeit, fehlende Ziele, Abnahme-Fotozuordnung, PDF-Ortslabel).
- Lint: 0 Fehler, 391 Warnungen.
- Server: 5 gezielte Tests bestanden (3 Integration, Foto-Referenz, neue Unterbaustellen-PDF-Regression); PDF visuell geprüft.
- Tatsächlich vom Kotlin-Test erzeugte XLSX geöffnet und gerendert. Vorschau-Renderer zeigt benutzerdefinierte Datumsspalten teilweise als Seriennummer; OOXML-Stile und unabhängiges openpyxl lesen Datum/Zeit korrekt. Windows-Excel-Prüfung weiterhin offen.
- Gesamt-CI NICHT BESTANDEN: Source-Vertragsjob 128726829452 rot wegen weiter vorhandener Checks 03/13/19/83/85/86. Nicht als neuer E2E-Erfolg oder Fehlerfreiheit werten. Kein Sicherheits-/Recovery-Test deaktiviert.
- Vorherige Buildversuche mit Gradle-/Maven-Downloadtimeouts; final derselbe geprüfte Gradle 9.3.1 über downloads.gradle.org mit SHA256 und längerem Timeout. Keine unnötige weitere Testwiederholung. Tatsächlicher Monatsminutenverbrauch nicht verfügbar.

## APK und Updategrenze
Artefakt 13616449515 enthält APK und passende Release-MD desselben Builds.
APK: TB-Helfer_1.3.44_200094.apk, 26995569 Bytes.
SHA256: e7d692cb035693c71661c03b9f0f78307048d2c91b8c8169c4868bacc807abfe.
Manifest de.tbhelfer / 1.3.44 / 200094, APK-Signaturprüfung bestanden.
Debug-Zertifikat SHA256: 341161f1d9f8fa7bfdeeef5fed20ec26c49921ed8179cb739d0a204970eba8a5.
Vorige CI-APK 1.3.43: f03307af46bb4b56015110f0e2ee1162127593151abe8a77ff88b7e9fa1837eb.
Zertifikate verschieden: kein direktes Update über genau diese vorige APK möglich. Installierte Gerätesignatur unbekannt. NICHT deinstallieren; für Update bisherigen Signierschlüssel verwenden. Schlüssel nicht verfügbar.

## Offen / nächste Schritte
ANDROID: mit bisherigem Signierschlüssel bauen, dann Installation auf Pixel/Poco und neuer ZIP-/PDF-Export prüfen.
GEMEINSAMER E2E: vollständigen Export entpacken, kompletten Ordner verschieben, Bildlinks in Windows-Excel prüfen; Mängel/Abnahmen und beide MBG-Weichen mit echten Daten vergleichen.
SERVER: NAS-Softwarestand im separaten Serverauftrag abgleichen; GitHub-Code ist kein Deploymentnachweis.
INSTALLIERT: OFFEN. DEPLOYT: NEIN. Pixel/Poco/Windows-Excel/Synology E2E: OFFEN.
Keine Migration, kein NAS-SSH, keine Produktivdatenänderung.
