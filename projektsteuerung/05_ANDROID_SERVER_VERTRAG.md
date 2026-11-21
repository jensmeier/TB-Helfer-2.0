# TB-HELFER – ANDROID/SERVER VERTRAG

## AKTUELLER KOMPAT-1120 STATUS (20.11.2026)
Historische CI-Basis: Android 1.3.46/200096 cda78c2f42fd + Server 1.0.0-dev/APIv1/PDF6 b924da3e9bbf. Neue aktuelle Codepaarung: Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd und Server 4b4065658d744a9773ca06d2b4af53c737056c02. CI-Partnerpins: Android-Workflow 6e321e7e8aad4464dd6768376e65943b9cda26f1, Server-Workflow 94fe87251c936c42807f2e60ee6c2c808b45d5a8. Neue CI zum Zeitpunkt der Dokumentation noch nicht bestätigt. Codeänderungen F-01/F-02/F-04/F-06 (Server), F-07 (Android) sind NICHT als Praxis-E2E abgenommen; F-03/F-05 OFFEN. Kein Deployment/keine neue APK. Nächste Schritte: CI-Nachweis, Rechte-/Foto-Regressionen, E01–E12.

---

Stand 08.11.2026 – Umsetzung GESAMT-1108 nach ausdrücklichem Bauauftrag.
SERVER-Teststand `b924da3e9bbff603a78ca55753cefb439f1ac607`, Fachcode `b17530d305797837d42f3049681f10cd919ea4b5`.
ANDROID-Fach-/APK-Stand `cda78c2f42fd7b1acd76d60612ce42d882662edf`, 1.3.46 / 200096; nachfolgender Instrumentierungsstand `680ef37e9781eb4e4b0b8da04b5db7e6b2d42340`.
Nachfolgende Dokumentationscommits verändern diese Fachstände nicht.
[Aktueller Bericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [vorherige Statusfassungen](archiv/UMSETZUNG_2026-11-08_VORHER/01_MASTERSTATUS.md).

Vorhandene Fachverträge unter `vertraege/` bleiben gültig; kein paralleles Datenmodell.

| Bereich | Aktuelle Umsetzung | Grenze |
|---|---|---|
| Version/Identität | Ursprüngliche Basisversion, persistente Beitrags-/Mutations-ID; kein stilles409-Rebase | Konfliktbedienung offen |
| Trassen | Haupt-TB sendet/empfängt, Helfer empfängt; Dirty-Daten werden geschützt; kein Löschen wegen fehlender lokaler Kopie | Explizite Löschabsicht nicht vollständig angebunden |
| Atomarer Empfang | Validierung, Geometrie und Syncjournal in derselben Room-Transaktion | Reale Pixel-/Poco-Abnahme offen |
| Bereich/Rechte | worksite.view und vollständiger Datenbereich; unbekannter Bereich nicht pauschal für Helfer freigeben | NAS-Rechteentzugsmatrix offen |
| Gemeinsame KM | GET /survey-catalog/km-sync/resolve per Originalhash; vorhandenes /axes/{id}/km-sync mit Quelle/Version/Ankern | linear; KM-Sprünge/GVP-/Mast-Bearbeitung/Deaktivierung noch offen |
| Originale | unveränderte neue Importbytes, WLAN-Outbox → Datei → from-file → Parser; exakte Quellenbindung | ältere Originale reimportieren, mehrdeutige Zuordnung nicht raten |
| Delta | DEFECT/ACCEPTANCE/TRACK_DATA eingebunden; fachlistenbezogener Empfang; persistente Track-Wiederholung | objektweise Fachupdates und globaler Katalogfeed offen |
| Helfer/BTB | Schichtende nach Datenbestätigung; Haupt-TB finalisiert; Server16h-Frist | offline Helfer/verspätete Daten E2E offen |
| PDF | Cleanup nach DB-Commit kann bestätigte Datei nicht zurückrollen | echtes NAS-Dateisystem noch abnehmen |
| Fotos/Listen | Original lokal, kleine Sync-Versionen, drei Bilder/Reihe, Fotoanzahl/Word erhalten | reale Transfer-/Referenzexportabnahme offen |
| Logo/Version |40–400pt; authentifizierte App-Version/Buildheader | NAS-Anzeige/Übernahme offen |
| Update | Manifest/Hash und installiertes Signaturzertifikat vergleichen | privater vorhandener Schlüssel fehlt; kein belegtes kompatibles Update |

Ein Fehler beim Trassenabgleich darf nicht unbestätigt verschwinden: Journal/Fehlerprotokoll/persistenter Arbeitsauftrag bleiben. 15min-WorkManager ist ein verschiebbarer Fallback, keine Sofortzustellgarantie. Erfolgreicher Originalupload allein ist noch keine erfolgreiche fachliche Dekodierung.
Geschützt und erhalten: Word, drei Bilder pro Reihe, gespeicherte Fotoanzahl, stabile IDs und Originale sowie historische BESTÄTIGT/PASST/FUNKTIONIERT/ERLEDIGT-Markierungen. Neue Tests sind keine erneute pauschale Praxisbestätigung. Ungeprüfte ältere Restaufträge bleiben im Archiv offen zur Einordnung.


## KOMPAT-1120 – gemeinsamer Entwicklungsauftrag (20.11.2026)
Benutzer hat GitHub-Bereinigung ohne Verlust historischer Nachweise, synchrone Android-/Server-Statusführung und gezielte Fehlerkorrekturen freigegeben. Basis: Android 1.3.46/200096 Fachcommit cda78c2f42fd7b1acd76d60612ce42d882662edf, Server 1.0.0-dev/API v1/PDF 6 Testcommit b924da3e9bbff603a78ca55753cefb439f1ac607. Basispaarung ist keine reale Pixel/Poco/NAS-Abnahme. Neue fachliche Commits gesondert ausweisen. E01–E12 bleiben offen. Keine NAS-/Produktivdatenänderung. Neue Fehler F-01 bis F-07 in 09_KOMPATIBILITAETSBASIS.md, Altbestätigungen und Archiv behalten.


### PRUEF-1120-1BIS5 – Rechte- und Datenflussabgrenzung
Vor F-03/F-05-Korrekturen betroffene Objekt-/Team-/Bereichsrechte einschließlich Cursor, Tombstones und GLOBAL/COMPANY-Streckenkatalog verifizieren. Keine pauschale Sperre gemeinsamer Originale oder stiller Cursorverlust. Ausführliches Rechte- und Testkonzept: `uebergaben/PRUEF_1120_1_BIS_5.md`. E2E weiterhin offen.


## FACH-1120 – Prüfvertrag Foto/Text/Wetter/Export (OFFEN)
Sichtbare Kette: Kamera/BTB-Editor→Android Room/Offline-Outbox→Server API/DB→Portal und zweites Handy→Word/PDF/XLSX/ZIP. Nachträgliche Foto-Korrekturen müssen Firma, Kurz-/Langtext, Bildtyp, KM, Zuordnung und autorisierte Metadaten ohne Bildoriginal-/ID-Verlust versions- und konfliktgeschützt übernehmen. Wetterwerte inklusive Zeit/Ort/Quelle/0,0 mm zu Schichtbeginn und Schichtende getrennt speichern und in BTB/Word synchron halten. Detaillierte Prüfung in `projektsteuerung/uebergaben/FACH_1120_TEXT_FOTO_ZIP_WETTER.md`, E2E OFFEN.

## FOTO-KURZTEXT / BTB-LANGTEXT – Klarstellung 20.11.2026
Verbindliche Fachentscheidung: **Beim Foto selbst und seiner Bildunterschrift ausschließlich Kurztext anzeigen.** Ein optionaler Langtext (bislang `FotoEntity.notiz` / Server `photo_metadata.notiz`) ist **für die ausführliche BTB-Beschreibung und Word-Ausgabe** bestimmt, nicht als zweite sichtbare Bildbeschriftung. Beide Texte bleiben einzeln nachträglich bearbeitbar; Originalfoto, Bild-ID und ursprüngliche Aufnahmedaten unverändert. Gemeinsamer BTB/PDF/Word-Referenzfall, insbesondere drei Fotos pro Reihe, bleibt geschützt. Gesamte Fotolisten und ZIP sollen Kurztext als Fotobeschriftung führen; technische Langtext-Speicherung für BTB/Word nicht versehentlich löschen. Codeänderungen nur mit gezieltem Regressionstest als abgeschlossen markieren.

## ANDROID-STUDIO-TESTSTAND FACH-1120-UMSETZUNG (20.11.2026)
Android 1.3.47 / 200097, Codecommit `fe8e4f3f873837b06344386ccdc1a4c1cfc64e8f`. Server-Partner Quellcode `451c4310060b56a99ea631d144a6d2d40a75fb2d` (Server 1.0.0-dev, API v1, PDF 6). Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.47_200097.md`. **Noch keine signierte APK, Installation, NAS-Übernahme oder E2E-Abnahme.**

Umgesetzt im Android-Fachcode: Foto-Firma, Kurz- und Langtext bearbeiten; BTB-Overrides atomar speichern; alte firmaId beim Firmennamenwechsel verwerfen; Kurztext nur Fotoliste, Langtext für BTB/Word; Such-Refresh reparieren; ZIP-Einträge/CRC vor Weitergabe prüfen; Fototext-Rechtschreibvorschläge und unveränderte Fachfirmennamen; Spracheingabe-Zwischenspeicherung drosseln; Wetterzeitpunkte Schichtanfang/Mitte/Ende; Firmenfelder im Profil einklappen. Gezielt implementierte Tests: `TagesZipPruefungTest`, `BtbWeatherPolicyTest`, bisherige Foto-/XLSX-/ZIP-/Sprachtests erhalten.

**OFFEN und nicht als erledigt markieren:** erneuter tatsächlicher Wetterdienstabruf bei realem Schichtende mit Quellenvergleich (Startwert darf nicht überschrieben werden); vollständige Fotoänderung auf anderem Handy und im Portal einschließlich nicht BTB-gebundener Fotos, Typ/KM/Unterbaustelle und Schreibrechte; gesamte Suche mit Serverfilter; reale Excel/ZIP/Word/PDF-Referenzfälle; Pixel/Poco/Room20-21/Signatur und NAS E01–E12; separate SERVER-Sicherheitsbefunde. Testkette noch auf vollständigen CI-Gesamtnachweis und Geräte-Praxisprüfung warten. Keine Datenbankmigration hinzugefügt; Originalfotos, stabiler BTB/Word-Bestand und drei Fotos pro Reihe geschützt.


## SERVER-SEC-1121 Vertragsabweichungen
Server 1.0.0-dev/APIv1/PDF6 Fachcode `785c20e2b3d92cc6656c88b5997abaf6b8b028e2` schützt physische Referenzoriginale bei Baustellen-Purge und Release-Dateien nach Commit. Keine Änderung an Android-API-Schemas. Android 1.3.47/200097 wird mit Workflow `92a884f03255cea335ac16d2cf0f91380f20ce92` gegen exakt diesen Serverpartner geprüft; Quelle ≠ reale NAS-Version. Changefeed-Metadaten F-03 und Originalrechte F-05 noch fachobjekt-/team-/bereichssicher abgrenzen ohne globalen Katalog oder Cursor zu verlieren. Siehe Audit-Übergabe.

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
