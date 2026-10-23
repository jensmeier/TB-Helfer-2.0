# Android-Prüfung und Übergabe – 23.10.2026

## Grundlage und Grenzen
- Android-Ausgang: 2db590f252a25b91d79eafd7f23df04c74d0b10b, feature/tb-android-2.0.
- Server: a471996610a27b2d718f9835bf9c07930f29858f, feature/tb-server-1.0 (nur gelesen).
- Gesamter main-Quellbaum inventarisiert: 281 Kotlin-Dateien und 108 XML-Dateien. Struktur-/Verweis-/Fehlermusterprüfung des Baums, vertiefte Quellverfolgung für Authentifizierung, Team/BTB, Katalog, Navigation und Fotospeicher. Das ist keine vollständige funktionale Abnahme jedes Features.
- Bestehende 15 JVM-Testdateien auf Referenzen des alten Einzel-ZIP-Exports geprüft.
- Keine Verbindung zu Pixel/Poco oder authentifizierter NAS-Runtime; adb und Android-Toolchain hier nicht verfügbar. Test-Android-Apps-Skill geprüft, daher keine Emulatorergebnisse behauptet.
- Keine APK erzeugt, keine Actions gestartet. Lokaler Android-Studio-Build und Geräte-E2E offen. Frühere 200077-Buildnachweise gelten nicht als Buildnachweis für 200078.

## Gefundene und korrigierte Ursachen
1. Cache-Fallback verschluckt Fehler: letzter Abrufversuch und Ursache bleiben sichtbar; Cachezeit und Offline-Herkunft werden gezeigt. Ohne Antwort kein angeblich bekannter Nullbestand.
2. Ungültige HTTP-200-Katalogantwort ohne routes/stations wird abgewiesen und überschreibt keinen Cache.
3. Allgemeines HTTP403 wurde in mehreren Geräte-/Team-/Katalog-/Profil-/Legacy-Pfaden pauschal als ungültige Sitzung behandelt. Gemeinsame Klassifikation: HTTP401 bleibt ungültige Sitzung; HTTP403 nur bei DEVICE_REVOKED, USER_DISABLED oder USER_EXPIRED. Sonstige Berechtigungsfehler bleiben Fehler und löschen keinen Token. Vorhandene bereits differenzierte Server-1.0-Pfade behalten ihren Schutz.
4. Helfer FINISHED/NOT_PARTICIPATING: ausstehender Upload darf nicht Applied melden. Neuer Fehlerzweig erhält die Outbox und verändert keine Teilnehmerrechte. Historischer FINALIZED-Schutz bleibt unverändert.
5. Speicherbereinigung konnte Fachdatensätze bei fehlender Datei löschen und Bilder allein anhand Foto-DAO-Zuordnung verschieben. Worker arbeitet jetzt ausschließlich lesend; historische Job-/Outputnamen für Kompatibilität erhalten, Lösch-/Verschiebezähler null.
6. Einstellungen fachlich gruppiert, Anzeige und Fotobeschriftung direkt erreichbar; Team/QR bleibt bei der Baustelle. Startseite verwendet Synchronisierung statt des missverständlichen NAS-Aktionsnamens.
7. /catalog liefert nur den ergänzbaren Katalog, nicht automatisch sämtliche Referenz-/Vermessungsdaten. Neue reine Serversuche verwendet bestehenden /catalog/lookup-Vertrag und öffnet /catalog/inventory anhand Streckennummer/Bahnhofkürzel. Herkunft ist erkennbar, keine automatische Datenanlage/Downloads.

## Aufräumen
- FotoKmQualitaet, FotoStreckenAnzeige und BtbEntryPhotoActionBinder erscheinen im normalen Quellbaum fast unbenutzt, werden aber im Gradle-Quellgenerator verwendet: ausdrücklich behalten.
- Nummerierte BTB-Patches, Migrationen, Bahnhof-/Betriebsstellen-Assets und lokale Altformatleser nicht pauschal löschen.
- BtbEinzelZip: kein Aufrufer in vollständigem main-Quellbaum, Buildgenerator oder 15 Testdateien; alter Einzel-BTB-Export widerspricht aktuellem Tagesabschluss-ZIP-Einstieg. Als belegbar unbenutzten Code entfernen; Git-Historie erhält ihn. Keine vorhandene ZIP-/Original-/Benutzerdatei löschen.
- Alte Anzeige/Overlay-Zwischenseite bleibt als Kompatibilitätsziel für alte Navigation erhalten, wird im neuen Einstellungsweg nicht mehr vorgeschaltet.
- Weiteres Entfernen produktiver Legacy-/Generatorpfade erst nach Abnahme der erzeugten App; kein kosmetisches Massenlöschen.

## Android↔Server: Quellvergleich
| Ablauf | Quellbefund | Praktische Abnahme |
| --- | --- | --- |
| Geräteaktivierung | Einmalkey -> Gerätetoken; gespeicherte Serveridentität | Pixel/Poco offen |
| Portal-An-/Abmeldung | Eigene Cookie-Session; Logout widerruft Portal-Session, kein Geräte-Logout | Handy bleibt nach Portal-Logout angemeldet: offen |
| Einladung Helfer/weiterer TB | Moderne Einlösung /worksites/invitations/redeem, Firmen-/Benutzerprüfung im Server; Rolle aus Antwort | QR/Link, falsche Firma, Ablauf, doppelte Annahme offen |
| Helfer Schichtende/-fortsetzung | Identische shift-finish/shift-resume-Endpunkte; WORKING/FINISHED/NOT_PARTICIPATING und current_user-Felder vorhanden | beidseitige Statusübernahme, Offline-/Parallelfall offen |
| Helfer Schreibschutz/PDF | Server blockiert nicht aktive Teilnehmer; Finalisierung/PDF-Rechte getrennt | UI-/Downloadschutz im realen Gerätetest offen |
| Weiterer verantwortlicher TB | Teambezogene BTB-IDs und Firmen-/Bereichsprüfung vorhanden | keine Vermischung und gemeinsame Mängel/Abnahmen offen |
| Katalog/Suche | Bearbeitbarer Katalog, Referenzliste und GLOBAL-Inventar sind unterschiedliche Quellen | Pasing/Kürzel, bekannte Strecke, Weiche/WVB, KM0/Lücken, Freie Strecke offen |

## Wichtige noch offene Kompatibilität: Server-Wiederherstellung
Der aktuelle Server prüft bei gesetztem TB_RECOVERY_ID in recovery_guard.require_recovery_epoch den Header X-TB-Recovery-ID und antwortet ohne passenden kontrollierten Abgleich mit HTTP409 SERVER_RECOVERY_REBASE_REQUIRED. Der geprüfte Android-Stand implementiert diesen neuen Wiederherstellungs-/Rebasevertrag noch nicht.
- Das ist eine bestätigte bedingte Vertragslücke, kein Nachweis, dass TB_RECOVERY_ID auf dem NAS gesetzt ist oder die Screenshots dadurch entstanden sind.
- Keinen Header blind übernehmen/senden: damit würde der Schutz gegen Replay einer alten Offline-Outbox umgangen.
- Bei diesem Fehler muss ein gemeinsamer, datenerhaltender Wiederherstellungsabgleich zwischen Server und Android spezifiziert und getestet werden. Bis dahin blockierend anzeigen, lokale Daten erhalten.
- Kein Serverfix, keine NAS-Konfiguration und keine produktiven Datenänderungen aus diesem Audit.

## Prüfung für den lokalen Build 1.3.28 / 200078
1. GitHub Desktop: Android-Branch aktualisieren; Android Studio: Sync, testDebugUnitTest, lintDebug und normaler lokaler Build.
2. Neuer DeviceAuthorizationTest: vier Testmethoden für Authentifizierung versus Ressourcenrechte. Hier vorbereitet, mangels Toolchain noch nicht ausgeführt.
3. Auf dem Handy Neu laden: Zeitpunkt/Fehler/Cache unterscheiden. Netz aus, Netz an, fehlender Zugriff, erneuter Versuch, alter/leerer Cache.
4. Server-Suche: Strecke und Bahnhof nach Name/Kürzel; reine Referenz versus Datenbestand; Treffer öffnen; kein automatisches Anlegen.
5. Helfer Schichtende -> Eintrag auf anderem Gerät/Portal -> erneuter Sync, dann Schicht fortsetzen. Keine lokale Outbox als hochgeladen quittieren, solange gesperrt.
6. Portal abmelden darf Gerätetoken nicht widerrufen. Gerätefreigabe im Admin muss dagegen Zugriffe wirksam sperren.
7. Fotospeicher prüfen: Ergebnis zeigt nur Hinweise, keine Dateien/Datensätze werden verändert. Größere Schrift und Zurück-Navigation in Einstellungen prüfen.
8. Die fünf bekannten historischen Quellvertragsfehler aus 200077 bleiben separate offene Punkte. BTB-AUTO-016 weiterhin nicht implementiert.
