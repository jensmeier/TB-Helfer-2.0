# TB-HELFER – AKTIVER AUFTRAG
Stand08.11.2026 – unabhängige Gesamtprüfung.
Geprüfter SERVER-Fachstand `b213bcbfe8d5fcf49c17e71108b8be04167f4d7f`; ANDROID-Fachstand `a8401775a3f607594df91468e728ee3f6d3a1ec4`.
Nachfolgende Dokumentationscommits verändern diese geprüften Fachstände nicht.
[Vollständiger Prüfbericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [Unveränderte vorherige Fassungen](archiv/GESAMTPRUEFUNG_2026-11-08/README.md).

## Auftrag08.11 – Gesamtprüfung und aktuellen Stand sichern
Nutzer: alle Funktionen/Code, Server↔Handy↔Helfer und Trassendaten einschließlich Änderungen querprüfen, aktuelle technische Möglichkeiten vergleichen, Verbesserungsvorschläge und aktuellen Stand sichern. Bereinigung ausdrücklich auf alte Prüfberichte und GitHub-Status begrenzt („1+3“).
Prüfblock abgeschlossen; reale NAS-/Geräte-E2E bleibt offen. Kein Entwicklungs-/Deploymentauftrag aus Empfehlungen ableiten.
## Neue Feststellungen
AUDIT-1108-01: Trassendaten-Liste/Detail ignorieren worksite.view und Helferbereich; Produktionshandler mit SQL-Doubles reproduziert. Zuständig SERVER.
AUDIT-1108-02: PDF-Cleanupfehler nach DB-Commit löst Dateirollback aus; echte Dateien/Produktionsfunktionen mit DB-Double reproduziert. Zuständig SERVER.
AUDIT-1108-03: Querprüfungen nutzen veraltete Gegenbranchkopien; nächster Integrationslauf muss beide aktuellen Fach-SHAs festhalten. Zuständig gemeinsame Prüfpflege.
Keine Duplikate für TRACK_KM_1103, SHARED-KM-1103, AUD-20261002-01/-02, ADMIN-PDF-011, DEVICE-INFO-001 oder EXPORT-1108B anlegen.
## Empfohlene Reihenfolge bei anschließendem Entwicklungsauftrag
1. SERVER: Rechte-/PDF-Finalizefehler und manuelle KM-Katalogfehler samt Regressionen.
2. ANDROID: verlustfreier Trassendatenabgleich/atomarer Empfang und vorhandener gemeinsamer KM-Vertrag.
3. ANDROID: Fachdelta, stabile Signatur/Update, Logo400 und Versionsheader.
4. GEMEINSAM: vorhandene Prüfungen fachlich reparieren, Branchpaar prüfen; danach E01–E12 aus06.
Bestehende historische Teilaufträge/Bestätigungen bleiben im [Archiv](archiv/GESAMTPRUEFUNG_2026-11-08/02_AKTIVER_AUFTRAG.md) unverändert erhalten. Eine Empfehlung oder Archivierung ist keine pauschale Erledigung/Freigabe.


## Entwicklungsauftrag GESAMT-1108 – ausdrücklich freigegeben
Der Nutzer hat nach der Gesamtprüfung mit „okay alles bauen“ die Umsetzung der dort beschriebenen Korrekturen beauftragt. Die vorherige Beschränkung dieses Auftrags auf Prüfung ist damit für diesen Entwicklungsblock aufgehoben.
Umfang: Server-Rechte und PDF-Commit/Cleanup, bekannte manuelle KM-Katalogfehler; Android-Trassendatenabgleich/atomarer Empfang/gemeinsamer KM-Vertrag und Originaltransport; Fachdelta, Logo400, Versionsheader, 16-Stunden-Frist; fachliche Prüfpflege und revisionsgenauer Querabgleich. Bestehende Architektur/IDs/Originale/Bestätigungen erhalten.
Gezielt testen, erforderliche Builds ausführen und Android-APK nur zusammen mit exakt passender Release-MD bereitstellen. Signaturkontinuität muss geprüft werden; kein erfundener Ersatz für einen fehlenden vorhandenen privaten Signierschlüssel. Keine NAS-Übernahme, produktive Migration/Datenmutation oder SSH beauftragt. Kein Budget erhöhen. Einzeltests/Build/CI/Installation/E2E getrennt nachweisen.
