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
