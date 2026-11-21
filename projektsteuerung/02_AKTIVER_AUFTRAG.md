# TB-HELFER – AKTIVER AUFTRAG

## AKTUELLER KOMPAT-1120 STATUS (20.11.2026)
Historische CI-Basis: Android 1.3.46/200096 cda78c2f42fd + Server 1.0.0-dev/APIv1/PDF6 b924da3e9bbf. Neue aktuelle Codepaarung: Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd und Server 4b4065658d744a9773ca06d2b4af53c737056c02. CI-Partnerpins: Android-Workflow 6e321e7e8aad4464dd6768376e65943b9cda26f1, Server-Workflow 94fe87251c936c42807f2e60ee6c2c808b45d5a8. Neue CI zum Zeitpunkt der Dokumentation noch nicht bestätigt. Codeänderungen F-01/F-02/F-04/F-06 (Server), F-07 (Android) sind NICHT als Praxis-E2E abgenommen; F-03/F-05 OFFEN. Kein Deployment/keine neue APK. Nächste Schritte: CI-Nachweis, Rechte-/Foto-Regressionen, E01–E12.

---

Stand 08.11.2026 – Umsetzung GESAMT-1108 nach ausdrücklichem Bauauftrag.
SERVER-Teststand `b924da3e9bbff603a78ca55753cefb439f1ac607`, Fachcode `b17530d305797837d42f3049681f10cd919ea4b5`.
ANDROID-Fach-/APK-Stand `cda78c2f42fd7b1acd76d60612ce42d882662edf`, 1.3.46 / 200096; nachfolgender Instrumentierungsstand `680ef37e9781eb4e4b0b8da04b5db7e6b2d42340`.
Nachfolgende Dokumentationscommits verändern diese Fachstände nicht.
[Aktueller Bericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [vorherige Statusfassungen](archiv/UMSETZUNG_2026-11-08_VORHER/01_MASTERSTATUS.md).

Der ausdrückliche Auftrag „okay alles bauen“ autorisiert Entwicklung, notwendige Tests/Builds und Sicherung auf den Arbeitsbranches. Die Auditbeschränkung „nicht entwickeln“ ist für diesen Auftrag aufgehoben. Keine NAS-Übernahme/SSH oder produktiven Datenänderungen beauftragt.

Implementiert: Helfer-Trassenrechte, PDF-Commit/Cleanup, manuelle Katalogkorrekturen, atomarer Trassenempfang mit dauerhaftem Journal und Dreiwegekonflikten, neuer Originaltransport, gemeinsame lineare KM-Anbindung, Fachdelta-Nachladen, Logo400, Versionsheader, Updatezertifikatsvergleich und serverseitige 16h-Frist. Details und Restabgrenzung in [07](07_OFFENE_PUNKTE.md).

Android BUILD BESTANDEN und gesamter CI-Lauf42495975421 BESTANDEN am Baucommit cda78c2f42fd7b1acd76d60612ce42d882662edf. 245 Unit-Tests in35Suiten,0Fehler/0übersprungen; Lint0Fehler/391Warnungen. APK1.3.46/200096 und exakt passende Release-MD heruntergeladen; Manifest/Commit/SHA256 gegengeprüft.
Server BUILD/CI BESTANDEN: vollständiger Lauf42496224535 am b924da3e9bbff603a78ca55753cefb439f1ac607 gegen Android cda78c2f42fd7b1acd76d60612ce42d882662edf. 66 Unit-Tests,100 Foundation-Prüfskripte,24Migrationen in frischer PostgreSQL-Testumgebung, Authentifizierungsablauf, Container-/Snapshot-/Restore-/Rechte-/Recoverygate sowie255Sync-Wait-Verbindungen erfolgreich. Kein produktiver NAS-Test.
Room INSTRUMENTIERUNG BESTANDEN: Lauf42496070087 am680ef37e9781eb4e4b0b8da04b5db7e6b2d42340, Android15/API35-Emulator,2/2Tests. Echtes Room/SQLite: additive Migration19→22 erhält Achse; Geometrie+Journal rollen bei Ausnahme zusammen zurück und werden bei Erfolg gemeinsam bestätigt; Journale sind nach Owner getrennt. Kein Pixel-/Poco-/NAS-Netzwerktest.
Offen: gemeinsame Trassen-/KM-Löschabsichten und Konfliktoberfläche; KM-Sprünge und GVP-/Mast-Bearbeitung; vollständiger objektweiser Fachdelta-/globaler Katalogfeed; Offline-Android-Nachlauf der 16h-Frist; experimentelle Android-DB 20/21; alter Installationssignierschlüssel und reale NAS-/Pixel-/Poco-E2E. Kein NAS-Deployment, kein Merge, keine produktive Migration oder Datenänderung ausgeführt.

Nächste Schritte:
- SERVER: geprüften Stand separat auf NAS übernehmen lassen; Rechte/PDF/Frist und Wiederanlauf abnehmen.
- ANDROID: vorhandenen Schlüssel/Installationszertifikat klären; kompatibles Update und echte Gerätemigration; verbleibende KM-/Lösch-/Konfliktbedienung umsetzen.
- GEMEINSAM: E01–E12 mit tatsächlichen Versionen abnehmen; keinen Erfolg aus einem Source-Marker ableiten.

Geschützt und erhalten: Word, drei Bilder pro Reihe, gespeicherte Fotoanzahl, stabile IDs und Originale sowie historische BESTÄTIGT/PASST/FUNKTIONIERT/ERLEDIGT-Markierungen. Neue Tests sind keine erneute pauschale Praxisbestätigung. Ungeprüfte ältere Restaufträge bleiben im Archiv offen zur Einordnung.


## KOMPAT-1120 – gemeinsamer Entwicklungsauftrag (20.11.2026)
Benutzer hat GitHub-Bereinigung ohne Verlust historischer Nachweise, synchrone Android-/Server-Statusführung und gezielte Fehlerkorrekturen freigegeben. Basis: Android 1.3.46/200096 Fachcommit cda78c2f42fd7b1acd76d60612ce42d882662edf, Server 1.0.0-dev/API v1/PDF 6 Testcommit b924da3e9bbff603a78ca55753cefb439f1ac607. Basispaarung ist keine reale Pixel/Poco/NAS-Abnahme. Neue fachliche Commits gesondert ausweisen. E01–E12 bleiben offen. Keine NAS-/Produktivdatenänderung. Neue Fehler F-01 bis F-07 in 09_KOMPATIBILITAETSBASIS.md, Altbestätigungen und Archiv behalten.

## Auftrag PRUEF-1120-1BIS5 (20.11.2026) – ausdrücklich beauftragt
Benutzer hat den Start aller fünf Folgeschritte freigegeben: (1) F-03/F-05 Berechtigungsmodell sauber abgrenzen und Regressionen, (2) Upload-/Offline-/Konflikt-/Datenverlusttests und frühere Fixes prüfen, (3) Update-Signatur und Android-DB-Migrationspfade prüfen und Update vorbereiten, (4) Synology-Version/Compose/Backup/Migrationen rein lesend abgleichen und Übernahmeplan erarbeiten, (5) E01–E12 Pixel/Poco/NAS-E2E konkret vorbereiten; ohne Zugang kein Praxisnachweis. Keine produktiven Änderungen/NAS-SSH/Installation ohne gesonderte Freigabe. Keine kostenpflichtigen Dienste, kein erfundenes CI-/E2E-ERLEDIGT.
F-03 und F-05 können den bisher gewollten gemeinsamen Firmen-/Streckenkatalog betreffen: vor Änderungen Rechte- und Bestandsmodell nachvollziehbar prüfen. Sicherheits- und Datenverlusttests nicht abschwächen. Originale, Freigaben, Bestätigungen, Releaseidentität und historisches Archiv schützen.


## FACH-1120-TEXT-FOTO-ZIP-WETTER (Benutzeranforderung – Prüfung vor Änderung)
Der Benutzer verlangt Tastatur-/Wortkorrektur, nachträgliche Foto-/Firmen-/Kurz-/Langtext-Bearbeitung in Android und Portal, Abgleich von Bildgesamtlisten XLSX, ZIP/Tagesabschluss, Bild-/BTB-Suche und zuverlässiges Wetter (Beginn/Ende, Temperatur, mm, trocken/bewölkt) in BTB/Word. Frühere Spracheingabe- und Einstellungsentscheidungen gelten weiter. Details und Prüffälle: `projektsteuerung/uebergaben/FACH_1120_TEXT_FOTO_ZIP_WETTER.md`. Keine Neuimplementierung ohne vorherige Belegprüfung; keine Daten-/NAS-Änderung.

## FOTO-KURZTEXT / BTB-LANGTEXT – Klarstellung 20.11.2026
Verbindliche Fachentscheidung: **Beim Foto selbst und seiner Bildunterschrift ausschließlich Kurztext anzeigen.** Ein optionaler Langtext (bislang `FotoEntity.notiz` / Server `photo_metadata.notiz`) ist **für die ausführliche BTB-Beschreibung und Word-Ausgabe** bestimmt, nicht als zweite sichtbare Bildbeschriftung. Beide Texte bleiben einzeln nachträglich bearbeitbar; Originalfoto, Bild-ID und ursprüngliche Aufnahmedaten unverändert. Gemeinsamer BTB/PDF/Word-Referenzfall, insbesondere drei Fotos pro Reihe, bleibt geschützt. Gesamte Fotolisten und ZIP sollen Kurztext als Fotobeschriftung führen; technische Langtext-Speicherung für BTB/Word nicht versehentlich löschen. Codeänderungen nur mit gezieltem Regressionstest als abgeschlossen markieren.

## ANDROID-STUDIO-TESTSTAND FACH-1120-UMSETZUNG (20.11.2026)
Android 1.3.47 / 200097, Codecommit `fe8e4f3f873837b06344386ccdc1a4c1cfc64e8f`. Server-Partner Quellcode `451c4310060b56a99ea631d144a6d2d40a75fb2d` (Server 1.0.0-dev, API v1, PDF 6). Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.47_200097.md`. **Noch keine signierte APK, Installation, NAS-Übernahme oder E2E-Abnahme.**

Umgesetzt im Android-Fachcode: Foto-Firma, Kurz- und Langtext bearbeiten; BTB-Overrides atomar speichern; alte firmaId beim Firmennamenwechsel verwerfen; Kurztext nur Fotoliste, Langtext für BTB/Word; Such-Refresh reparieren; ZIP-Einträge/CRC vor Weitergabe prüfen; Fototext-Rechtschreibvorschläge und unveränderte Fachfirmennamen; Spracheingabe-Zwischenspeicherung drosseln; Wetterzeitpunkte Schichtanfang/Mitte/Ende; Firmenfelder im Profil einklappen. Gezielt implementierte Tests: `TagesZipPruefungTest`, `BtbWeatherPolicyTest`, bisherige Foto-/XLSX-/ZIP-/Sprachtests erhalten.

**OFFEN und nicht als erledigt markieren:** erneuter tatsächlicher Wetterdienstabruf bei realem Schichtende mit Quellenvergleich (Startwert darf nicht überschrieben werden); vollständige Fotoänderung auf anderem Handy und im Portal einschließlich nicht BTB-gebundener Fotos, Typ/KM/Unterbaustelle und Schreibrechte; gesamte Suche mit Serverfilter; reale Excel/ZIP/Word/PDF-Referenzfälle; Pixel/Poco/Room20-21/Signatur und NAS E01–E12; separate SERVER-Sicherheitsbefunde. Testkette noch auf vollständigen CI-Gesamtnachweis und Geräte-Praxisprüfung warten. Keine Datenbankmigration hinzugefügt; Originalfotos, stabiler BTB/Word-Bestand und drei Fotos pro Reihe geschützt.

## SERVER-SEC-1121 – Auftrag zur Korrektur belegter Serverfehler (21.11.2026)
Benutzer hat ausdrücklich verlangt: alle dokumentierten Serverfehler erneut abgleichen, bereinigt auflisten und tatsächliche Serverfehler mit Regressionen korrigieren. Ausgangs-HEAD Server `0eb98fa50532d7101d5dcdc7a3481a33899984a1`, Android `a5d354ba386f7f76084b1f0d9094e0bca3a2110a` (1.3.47/200097). Letzte Server-CI 44670178818 komplett erfolgreich; CI ist weder NAS-Deployment noch E2E.

Neu/noch offen aus Quellcode:
- S-05 KRITISCH: `admin_purge.py` Baustellen-Purge entfernt Dateipfade, die `survey_originals.py` auf weiter bestehende firmenweite Vermessungsoriginale umgebucht hat; Original/Referenz schützen.
- S-02 HOCH: `releases.py` kann nach bestätigtem Datenbank-Commit bei Datei-Cleanup-Fehler die installierte Release-Datei zurückrollen; vor/nach Commit trennen.
- S-07 MITTEL: `admin_purge.py` Firmen-Purge entfernt leere globale Achsen ohne Firmenbezug; Datenkatalog global erhalten.
- S-01 HOCH: produktiv genutzte Compose-Vorlage enthält stillen staging-Fallback und beeinflusst irreversible Admin-Schutzschranken; vorhandene Runtime-`.env` unbekannt, vor Deployment gesondert verifizieren.
- S-06 HOCH HÄRTUNG: `system-export` hat lesend gemounteten Host-Docker-Socket und HTTP-Export ohne eigenständige Authentifizierung; nur in gesichertem Test-/Deploymentfenster mit funktionierendem Vollbackup ersetzbar.
- F-03 MITTEL: Change-Feed liefert bei Mitgliedschaft zum Teil fremde Fachobjekt-Metadaten; Filter für Team/KM/Tombstones/Wait/SSE gegen Android-Cursor abstimmen.
- F-05 HOCH abhängig von Freigabevertrag: Firmen-Rohoriginale auch ohne individuelle Baustellenmitgliedschaft; GLOBAL/COMPANY/baustellengebundene Referenzen unterscheiden, bestätigten gemeinsamen Katalog nicht sperren.
- BTB-16H: deaktivierter/abgelaufener Lead → automatischer Abschluss möglicherweise nicht ausgeführt, fachliche Rollenübergabe klären.
- Weitere Punkte Wetter Beginn/Ende, Foto-/Text-Portalbearbeitung, Suche, ZIP/Word/XLSX und echte Android↔Server E2E: aus FACH-1120 als Fach-/Prüfaufträge offen; keine pauschale Fehlerbestätigung.

Bereits CODE GEÄNDERT/CI BESTANDEN (nicht erneut öffnen): F-01 Upload-PDF-Cleanup, F-02 Track/KM-Schreibscope, F-04 Survey-Originalimportrechte, F-06 Survey-Paginierung, AUDIT-1108-01/-02. E01–E12 real OFFEN. Neue Korrekturen nur in Server-Branch, kein NAS-SSH/Deployment/produktive Migration/Purge, keine API-Abschwächung und keine neue kostenpflichtige CI-Runneraktion. Teststatus ausdrücklich QUELLCODE GEÄNDERT → TEST/CI → NAS E2E gesondert.


## SERVER-SEC-1121 Ergebnis und Weiterarbeit
Benutzerauftrag "alle Serverfehler auflisten, dann beheben" ist als begonnene mehrteilige Umsetzung zu behandeln: S-05/S-02/S-07 KORRIGIERT/CI BESTANDEN, S-01 Compose-Preflight vorbereitet, echte NAS-Umsetzung offen. F-03/F-05/S-06 nicht grundlos mit unsicherem Pauschalfilter oder ohne gesichertes Systembackup umbauen; fachliche Rechte-/Team-/Katalog- und Restore-Vertragsprüfung zuerst. Vollständige Liste `uebergaben/SERVER_AUDIT_2026-11-21.md`.
