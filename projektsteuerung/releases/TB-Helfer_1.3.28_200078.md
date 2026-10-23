TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.28
BUILD: 200078
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Gemeinsame Serversuche für Strecken und Bahnhöfe im NAS-/Synchronisierungsbereich; Referenz-/Katalogtreffer und vorhandene Vermessungsdaten sind erkennbar.
- Fotospeicher-Prüfung mit sichtbarem Ergebnis, ohne Dateien oder Datensätze zu löschen oder zu verschieben.

VERBESSERT:
- Einstellungen nach Konto/Verbindung, Kamera/Darstellung, Bautagebuch/Dokumenten und Wartung geordnet.
- App-Anzeige und Fotobeschriftung direkt erreichbar, ohne vorgeschaltetes Auswahlmenü.
- Startmenü bezeichnet den Bereich klar als Synchronisierung.
- Offline-Katalog zeigt Herkunft, gespeicherten Stand, Zeitpunkt des Abrufversuchs und Fehlerursache.

BEHOBEN:
- Fehlgeschlagene Abfragen werden nicht mehr durch vorhandene Offline-Daten verdeckt.
- Ein leerer Offline-Bestand wird nicht als leerer aktueller Serverbestand bezeichnet.
- Unvollständige Katalogantworten überschreiben keinen gültigen Cache.
- Allgemeine Berechtigungsfehler führen in den korrigierten Geräte-/Team-/Katalog-/Profil-/Legacy-Pfaden nicht mehr zum Löschen der Geräteanmeldung.
- Noch nicht hochgeladene Helferänderungen werden bei beendeter/aufgehobener Teilnahme nicht als erfolgreich übertragen quittiert.
- Nicht mehr verwendeter Einzel-BTB-ZIP-Code entfernt; Tagesabschluss-Export und gespeicherte Dateien bleiben bestehen.

SERVERVERTRAG:
- Geprüfter Server-Quellstand: a471996610a27b2d718f9835bf9c07930f29858f.
- Vorhandene Geräte-Bearer-, Einladungs-, Teilnehmer-, Katalog-/Lookup-/Inventory-Verträge.
- Keine server/**-Änderung, keine NAS-Konfigurationsänderung.
- OFFENE bedingte Kompatibilitätslücke: Bei aktivem TB_RECOVERY_ID verlangt der Server einen kontrollierten Wiederherstellungs-/Rebase-Abgleich. Android quittiert diesen neuen Vertrag nicht automatisch; Fehler wird blockierend erklärt.
- BTB-AUTO-016 (16-Stunden-Abschluss) weiterhin nicht implementiert.

TESTSTATUS:
- XML-/Ressourcen-/Navigations- und gezielte Quellflussprüfungen bestanden.
- Vier neue DeviceAuthorizationTest-Testmethoden vorbereitet, hier nicht ausgeführt.
- Kein Gradle-/Kotlin-/Lint-/Installations-/Pixel-/Poco-/NAS-E2E-Nachweis für200078.
- Keine APK erzeugt und keine CI-Builds ausgelöst; Benutzer baut in Android Studio nach GitHub-Desktop-Pull.
- Gesamtprüfung nicht als funktional abgeschlossen freigegeben.

NÄCHSTER TEST:
- Android Studio: Gradle-Sync, testDebugUnitTest, lintDebug und lokaler Build.
- Handy: Neu laden mit/ohne Netz und Cache; sichtbare Fehlerursache prüfen.
- Serversuche: bekannte Strecke und Bahnhof/Kürzel, Bestand öffnen, Freie Strecke ohne Bahnhof.
- Helfer: Einladung, Schichtende/fortsetzen, Parallel-/Offlineänderung, Portal-Logout versus Gerätefreigabe.
- Speicherprüfung ohne Datenänderung und neue Einstellungswege prüfen.
- Prüfbericht: projektsteuerung/uebergaben/ANDROID_Pruefung_2026-10-23.md.

