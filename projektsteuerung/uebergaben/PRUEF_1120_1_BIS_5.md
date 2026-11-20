# TB-Helfer – Folgeauftrag PRUEF-1120-1BIS5: technische Abnahme und Rechtekonzept
Datum 20.11.2026 · KEIN produktiver Eingriff · KOMPAT-1120

## Eingangsstand (GitHub, nicht Synology/Pixel/Poco)
Android Fachcode 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd; versionName 1.3.46, versionCode 200096; CI-Gegenpartner Servercode 4b4065658d744a9773ca06d2b4af53c737056c02. Server 1.0.0-dev/API v1/PDF 6. GitHub-Workflows: Android 44643311256 und Server 44643317730 bestanden, aber **keine Geräte-/NAS-Abnahme**.
Aktuelle nachfolgende Testcommits sowie Ergebnis immer frisch anhand GitHub ermitteln; diese historischen Referenzen nicht als automatische Bestätigung späterer Änderungen ausgeben.

## 1. Sicherheitsrechte – F-03 Change-Feed und F-05 Originalzugriff
F-03: `server/tbserver/sync.py` listet Ereignisse über worksite_id bzw. Mitgliedschaft; Fachobjektbereich, Team und sensible Metadaten können abweichen. Vor Korrektur für EVENTS BTB, BTB_ENTRY, BTB_PARTICIPANT, FILE, DEFECT, ACCEPTANCE, TRACK_DATA, WORKSITE_NODE, DELETE/TOMBSTONE die pro Typ gültige Sichtbarkeit bestimmen. Auch Cursor-Paginierung und Delta-Wiederaufnahme schützen: ungefilterte/gesperrte Ereignisse dürfen den Cursor vor berechtigte Ereignisse nicht springen lassen.
F-05: `server/tbserver/survey_originals.py` erteilt firmeneigenen Nutzern Zugriff allein wegen company_id; Quellen können zugleich als freigegebene GLOBAL/COMPANY-Referenzen für den gemeinsamen Fachkatalog dienen. Unterscheiden: GLOBAL_REFERENZ explizit freigegeben, COMPANY_FREIGABE bewusst freigegeben, ursprüngliche baustellengebundene Rohdatei (nur berechtigte Baustelle/Team und freigegebener Bereich), CHEF/Admin nach Rollenvertrag. Keine neue implizite Firma-übergreifende Raw-Dateifreigabe, keinen bisherigen fachlich erlaubten GLOBAL-Katalog blockieren.
Testmatrix: ADMIN, CHEF, verantwortlicher TB, zweiter TB, Helfer (voll/teilweise), READ_ONLY, abgelaufener/entfernter Nutzer, andere Firma; je eigenes/fremdes Team, eigene/fremde Baustelle, eingeschränkte KM, unbekannte Originalgeometrie, nachträglicher Rechteentzug. Prüfen GET Listen/Detail/Content/Change-Feed/Wait/SSE; kein Code-Erfolg ohne echte API-Assertions.

## 2. Datenverlust, Offline und Rückfalltests
- Bestehende Fixes F-01/F-02/F-04/F-06 SERVER und F-07 ANDROID testen; nicht nur Textmarker. F-01 Fehler nach DB-Commit in PDF-Cleanup injizieren: neue Datei darf nicht zurückwandern; bei Fehler vor Commit rollback und Wiederaufnahme prüfen.
- Android: vollständiger Download vor atomarer Room-Übernahme, Prozessabbruch zwischen Geometrie/Journal, Netzverlust beim Upload, 409 beim Dreiwege-Merge, identische Hashes in zwei getrennten Baustellen; keine Original-/Achs-ID-Löschung.
- BTB: parallele Foto-/Eintragsänderung, gleichzeitig Haupt-TB/Helfer, Schichtende bei fehlenden Daten, Wiederöffnung und automatische 16h-Frist inklusive verspäteter Offline-Outbox. Reale Gerätestrecken E01–E08 bleiben OFFEN.
- Tests nur gegen isolierte PostgreSQL-/Android-Testdatenbank, keine Produktionsdateien oder NAS verwenden.

## 3. Android-Updateprüfung (ohne neue APK/Installation)
- Aktuell de.tbhelfer, versionName 1.3.46, versionCode 200096. Vor neuer APK vorhandenes Pixel-/Poco-Installationszertifikat gegen den tatsächlich vorgesehenen Release-Key vergleichen. Der frühere Debug-Key-Fingerprint in der Dokumentation **belegt kein kompatibles In-Place-Update**.
- Room-Schema `TBDatenbank.kt`: Version 22, registrierte additive Migration 19→22. Experimentelle 20/21 haben unregistrierte Rückweg-Migrationen 20→19 und 21→19: KEINE neue APK für diese Installationen, bevor eine sichere, nichtdestruktive 20/21→22-Migration anhand erhaltener realer Testkopien geprüft ist.
- Prüfen auf beiden Geräten: installierter versionCode, DB user_version, Signatur, lokale ausstehende Outbox/Originale, freier Platz. Keine Deinstallation, keine manuelle Datenbankänderung.

## 4. Synology (read-only Vorbereitung)
- GitHub ersetzt keinen Container. Verbindlicher Bedienweg laut Projektregeln: GitHub Desktop → File Station → Container Manager; ohne expliziten Auftrag nur aktuelle Container/Versionshinweise/Screenshots/Backup- und Migrationstatus ablesen, keine NAS-SSH.
- Inventar mit tatsächlich installierter API-Version, PDF-Renderer, API/Worker/Monitor/DB/Gateway-Images, Umgebungsvariable TB_ENVIRONMENT (niemals Secrets ausgeben), Migrationen 001–024 und Dienste-Zustand anfertigen.
- Vor einer später ausdrücklich freigegebenen Übernahme: gesonderte frische Sicherung von PostgreSQL, Dateispeicher, Originalen und .env (Secrets nicht ins GitHub), Wiederherstellungstest in isolierter Umgebung, Migrationsprobe, Preflight, File-Station-Upload nur benötigter Dateien, kontrollierter Container-Restart, Readiness- und E01–E12-Gates mit Rollbackplan.
- Nie Datenbank-/Storage-/Backupvolumes durch unbedachte Compose-Aktion ersetzen. Der vorhandene `server/compose.synology-reviewed.v1.yml` ist eine Quelle, kein Beweis des produktiv laufenden Compose.

## 5. Pixel/Poco/Synology-E2E – praktisches Testprotokoll
Testerfassung je Szenario: Datum, Pixel Build/Signatur/DB-Version, Poco Build/Signatur/DB-Version, laufende NAS-API/PDF-Version, Baustelle/Firma (nur TEST), Nutzerrolle/Team/KM-Freigabe, Schrittfolge, erwarteter/Ist-Zustand, lokale Outbox, HTTP-Status/Log ohne Secrets, Foto-ID/Datei-Hash, Ergebnis BESTANDEN/FEHLER/OFFEN.
E01 Pixel-Neubaustelle → Server/Portal → Poco ohne doppelte Baustellen-ID.
E02 gleichzeitige Portal-/Handyänderungen mit 409/konfliktsicherer Beibehaltung.
E03 BTB-Eintrag verschieben/löschen/ändern, korrekte Firma, Bildzuordnung und Suchwort.
E04 Helfer offline, App-Kill, Rechteentzug vor Wiederanmeldung; keine unsichtbare Datenvernichtung.
E05 Helfer beendet Schicht, Haupt-TB finalisiert, 16h-Autofrist, Server/Handys/Wiederöffnung und verspätete Daten.
E06 mobile/Portal-Fotos ≤ 2.000.000 Bytes; Thumbnail/Original, EXIF/GPS/KM, Bildmetadaten, Fotoanzahl.
E07 Mängel/Abnahmen samt Anlagen und XLSX, Änderungen/Löschen beide Richtungen.
E08 Original-TRA/GRA/KF/KOR → Serverparser → KM/Anker → zweites Gerät mit eindeutiger Quellenbindung.
E09 Word unverändert; PDF-Unterbaustellen, drei Fotos/Zeile, ZIP, Gesamt-Fotoliste kopierbar, Mangel-/Abnahmeliste.
E10 signaturkompatibles In-place-Update und sichere reale Room-Migration mit Offline-Daten.
E11 Backup/Restore nur in isolierter Testumgebung, Verify Hash/Originale.
E12 gemischte Last 15×15 + 15 Portal-Klienten, Fach-/Datei-/PDF-Verkehr. 255 bloße Sync-Wait-Verbindungen sind kein E12.
Bis die exakten Geräte- und NAS-Nachweise vorhanden sind: E01–E12 E2E OFFEN.

## Schutz
Kein Deployment, kein NAS-SSH, keine Deinstallation, keine Migration oder produktive Datenmutation. Keine erfundene E2E-Bestätigung. Historische BESTÄTIGT/PASST/FUNKTIONIERT/ERLEDIGT bleiben geschützt.
