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

## Final verifizierter GitHub-CI-Stand – 21.11.2026 (ersetzt ältere "läuft/offen"-Notizen)
- SERVER-Fachcode `785c20e2b3d92cc6656c88b5997abaf6b8b028e2`: **CI BESTANDEN**, Gesamtrun https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44897857974; Jobs **foundation** und **Snapshot, rights and 255-client HTTP gate** beide abgeschlossen und erfolgreich. Neue Regressionen prüfen echte Handler/temporäre Dateien und isolierte SQL-Doubles, nicht Synology.
- RECOVERY-Workflow `79a98d6b528f409f2bf481ebeca038332a2ce2a9`: **CI BESTANDEN**, Gesamtrun https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44897725728; **recovery-core** (einschließlich realem Docker-Systemimage-Export/Load) und **restore-ui** beide bestanden. Vorheriger ubuntu-latest/Playwright-Runnerfehler ist durch Pin auf ubuntu-24.04 korrigiert; Checks nicht deaktiviert. Diese Runnerumgebung ist nicht die produktive Synology.
- ANDROID 1.3.47/200097, Workflow-Pin auf SERVER-Fachcode `785c20e...`: **CI BESTANDEN**, Gesamtrun https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44898057369; Jobs **Server and Android source contracts** und **build** erfolgreich. Noch kein Geräte-E2E/NAS-Deployment aus diesem Auftrag.
- Eingangs-HEADs vor serverseitigen Änderungen: SERVER `0eb98fa50532d7101d5dcdc7a3481a33899984a1`, ANDROID `a5d354ba386f7f76084b1f0d9094e0bca3a2110a`. Dokumentationscommits auf beiden Branches verändern weder Server-/Android-Fachcode noch die getesteten CI-SHAs.
- **OFFEN bleiben F-03/F-05/S-06, fachliche 16h-Lead-Randfälle, produktive TB_ENVIRONMENT-Werte und reale E01–E12.** S-01 ist bisher nur im reviewed-Compose fail-closed, nicht automatisch/auf NAS. Kein pauschales "alle Serverfehler beseitigt".

## SERVER-WEITER-1121: zusätzliche Absicherung (21.11.2026)
- SERVER-Codecommit `5be7408734a5df6b3d4c6c3b478426dd5f3121c2`: **beide** NAS-Compose-Vorlagen verlangen `TB_V1_ENVIRONMENT` ausdrücklich; kein stiller staging-Fallback mehr in diesen beiden Vorlagen. Unverändert laufende NAS und deren effektive `.env` sind nicht geprüft.
- SERVER-Codecommit `ffddf9d5d5032f43a5995f4553d6dcdef19d0494`: Baustellenbezogene Sync-Listen, Long-Poll-Neuprüfung und SSE-Einstieg verlangen `worksite.view` zusätzlich zur aktiven Mitgliedschaft. `test_sync_1121_scope.py` prüft den gesperrten und erlaubten Fall. **F-03 bleibt TEILWEISE OFFEN**: Team-/Fachobjekt-/KM-/Tombstone-Filter und Cursor/E2E sind nicht vollständig implementiert.
- SERVER-Testcommit `8c12c1c5c52a2b1a9cc6193484173da97ca64c10`: isolierten älteren Reauthentication-Check `135_verify_sync_reauthentication.py` an neue `require_permission`-Abhängigkeit angepasst, nicht die Sicherheitsprüfung entfernt. Vollständiger Server-Hauptworkflow 44910955648 BESTANDEN; initialer Lauf 44910713496 hatte diesen Prüfskriptfehler.
- SERVER-Codecommit `2fb91c592941c5efba6f02608343b6e3a7f09122`: interner Systemexport verlangt neuen gemeinsamen Schlüssel (`TB_V1_SYSTEM_EXPORT_TOKEN`, mindestens 32 Zeichen) in Worker und `system-export`; Export-Endpunkt verweigert fehlende/falsche Tokens mit 403. Beide NAS-Compose-Dateien verlangen Variable, Recovery-CI nutzt nur eine explizite disposable-Testkennung. Unit-Test `test_system_export_auth.py`. **S-06 bleibt TEILWEISE OFFEN**: Host-Docker-Socket selbst ist weiterhin privilegiert und verlangt einen eng begrenzten Proxy/Exportweg.
- SERVER-Haupt-CI https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44911154233 **CI BESTANDEN**: beide Jobs foundation sowie Snapshot, rights and 255-client HTTP gate. Recovery-Gesamtlauf https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/44911154263 zum Dokumentationszeitpunkt noch nicht als Gesamt-ERFOLG bestätigen; `restore-ui` war erfolgreich.
- ANDROID 1.3.47/200097: CI-Gegenserverpin auf `2fb91c592941c5efba6f02608343b6e3a7f09122` angepasst, Android Workflow-Commit `3471687d7d80cda3b81ff14879a12e49a0f0e633`; zugehörigen CI-Gesamterfolg separat prüfen. Keine APK, keine NAS-Installation, E01–E12 OFFEN.
- **Zwingende Vorbereitung einer späteren NAS-Übernahme:** Zuerst tatsächliche bestehende `.env` prüfen (ohne Secrets an Chat/GitHub zu übertragen), `TB_V1_ENVIRONMENT=production` sowie einen individuell generierten geheimen `TB_V1_SYSTEM_EXPORT_TOKEN` mit >=32 Zeichen in der NAS-`.env` setzen; Worker und Exportdienst müssen denselben Wert sehen. Ohne diesen Preflight NICHT deployen/neustarten. Bestehende DB-/Files-/Recovery-Backups, Originale und Word/PDF-Bestätigungen schützen.
- OFFEN unverändert: F-05 Katalog/Raw-Original-Berechtigungen, verbleibende F-03 Fachobjektrechte, S-06 Docker-Socket-Härtung, BTB-16h bei deaktiviertem Lead, reale NAS-/Pixel-/Poco-E2E.


## ANDROID-WARN-1121 – Android Studio 19 Warnungen (21.11.2026)
Benutzer beauftragt die 19 gelben Kotlin-/Test-Warnungen aus lokalem Build `BUILD SUCCESSFUL`, Android Studio Pixel 8, gezielt im Android-Quellcode zu bereinigen. Aufgenommen: SpeechWidgetService VIBRATOR_SERVICE, KeyboardAutoScroll/InputMethod-Resize, BaustelleNeu unsicherer Spinneradapter-Cast, BaustellenKnoten `flatMapLatest` ExperimentalCoroutinesApi, Bautagebuch Geocoder/Resize/Compiler-Konstantenbedingung, BtbModernLifecycleInstaller redundanter Null-Check, BtbPhotoEntryMatcher erschöpfendes `when`, ImportExport veraltete Systemleistenfarben, FotoErfassen Bundle.get/Resize, OverlayRenderer scaledDensity, CurrentSiteWidgetProvider redundanter else, Export1108BTest nullable parentFile, außerdem `DkwKreuzungsSchemaView` nur im lokalen generierten Buildpfad sichtbar, im aktuellen GitHub-Quellbaum nicht als Datei nachweisbar. KSP/IntelliJ NPE ist separat von Compilerwarnungen und trotz erfolgreichem Build kein App-Laufzeitfehlernachweis. Keine Funktionen abschwächen, insbesondere Tastatur, PDF, Wetter, Import und BTB; bereinigte Warnungen je nach aktuellem Compiler erneut messen. Kein NAS, keine produktiven Änderungen.

## ANDROID-WARN-1121 – Bereinigung 19 lokaler Warnungen (21.11.2026)
Ausgang: Nutzer-Build-Screenshot `BUILD SUCCESSFUL`, 19 Kotlin-Warnungen (17 Hauptquelle, zwei Export-Tests), plus separate KSP/IntelliJ-Ausnahme. CI-Ausgangspunkt Android 1.3.47/200097, Branch `feature/tb-android-2.0`. Quellcodekorrekturen `f201fc2818e997ee310e5a965b59d4287a424ae0` und `4aa3df71304f4162dc39a521035910a2083a5b36` (kein neuer versionCode, keine APK).
- Typ-/Nullsicherheit: Spinneradaptersuche ohne unchecked cast, BTB-Historie prüft tatsächlich nullable External-Files-Root bevor Dateipfad erzeugt wird, Test-Parentverzeichnisse nullsicher.
- Android-API/Anzeige: Vibrator via getSystemService(Klasse), Schriftgrößen über TypedValue statt scaledDensity in Overlay und DKW-Schema, Systemleisten-/IME-Alt-APIs nur dort mit **lokalem** DEPRECATION-Hinweis bewahrt, wo eine direkte Umstellung Tastatur/Full-Screen/Kompatibilität verändern würde.
- Kotlin: Explizites Opt-in zur vorhandenen flatMapLatest-API, erschöpfende Bildtyp-Wenn-Ausdrücke, redundante Nullprüfung beim BTB-Lead entfernt.
- Geocoder: Synchrone Legacy-Methode für alte Geräte auf IO belassen, Deprecation lokal begründet; keine ungeprüfte asynchrone Umstellung der Wetter-Standortlogik.
- Verbindlicher Quellcode-/CI-Nachweis: erster Korrekturcommit `f201fc2` -> GitHub Actions 44919530745 vollständig BESTANDEN; **nur drei** Kotlin-Warnungen im Original-Runnerlog (BautagebuchFragment historischer Nullcheck, DkwKreuzungSchemaView 2× scaledDensity). Zweiter Commit `4aa3df71304f4162dc39a521035910a2083a5b36` korrigiert exakt diese drei. Das Endergebnis des neuen CI-Run 44920144238 muss vor BESTANDEN/NULL WARNUNGEN ausdrücklich im Original-Log verifiziert werden.
- Kein Update auf Pixel/Poco, keine Synology-Aktion, keine Room-/API-/DB-Veränderung, drei Foto-Spalten, Kurztext/Langtext und alle historisch geschützten Funktionen bleiben erhalten. Rote KSP-IDE-NullPointerException gehört nicht zu den 19 gelben Compilerwarnungen und ist separat zu prüfen, falls reproduzierbar.
