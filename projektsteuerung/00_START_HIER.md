# TB-HELFER 2.0 – 00 START HIER

## AKTUELLER KOMPAT-1120 STATUS (20.11.2026)
Historische CI-Basis: Android 1.3.46/200096 cda78c2f42fd + Server 1.0.0-dev/APIv1/PDF6 b924da3e9bbf. Neue aktuelle Codepaarung: Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd und Server 4b4065658d744a9773ca06d2b4af53c737056c02. CI-Partnerpins: Android-Workflow 6e321e7e8aad4464dd6768376e65943b9cda26f1, Server-Workflow 94fe87251c936c42807f2e60ee6c2c808b45d5a8. Neue CI zum Zeitpunkt der Dokumentation noch nicht bestätigt. Codeänderungen F-01/F-02/F-04/F-06 (Server), F-07 (Android) sind NICHT als Praxis-E2E abgenommen; F-03/F-05 OFFEN. Kein Deployment/keine neue APK. Nächste Schritte: CI-Nachweis, Rechte-/Foto-Regressionen, E01–E12.

---


**Einziger Einstieg für neue Chats und neue Arbeitsblöcke.**

Repository: `jensmeier/TB-Helfer-2.0`

## Aktueller Umsetzungsstand
Stand 08.11.2026 – Umsetzung GESAMT-1108 nach ausdrücklichem Bauauftrag.
SERVER-Teststand `b924da3e9bbff603a78ca55753cefb439f1ac607`, Fachcode `b17530d305797837d42f3049681f10cd919ea4b5`.
ANDROID-Fach-/APK-Stand `cda78c2f42fd7b1acd76d60612ce42d882662edf`, 1.3.46 / 200096; nachfolgender Instrumentierungsstand `680ef37e9781eb4e4b0b8da04b5db7e6b2d42340`.
Nachfolgende Dokumentationscommits verändern diese Fachstände nicht.
[Aktueller Bericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [vorherige Statusfassungen](archiv/UMSETZUNG_2026-11-08_VORHER/01_MASTERSTATUS.md).

00–08 bilden die aktuelle Sicht; Archivtexte sind historische Belege. Ausdrücklicher Bauauftrag GESAMT-1108 aktiv; Entwicklung/Tests/Builds beauftragt, NAS-Deployment nicht beauftragt.

## A. LIVE-GITHUB ZUERST
1. `feature/tb-android-2.0` + HEAD + PR-Status (Draft ausdrücklich prüfen)
2. `feature/tb-server-1.0` + HEAD + PR-Status (Draft ausdrücklich prüfen)
3. `docs/TB-HELFER_PROJEKTREGELN.md`
4. `projektsteuerung/01_MASTERSTATUS.md`
5. `projektsteuerung/02_AKTIVER_AUFTRAG.md`
6. `projektsteuerung/03_ANDROID_STATUS.md`
7. `projektsteuerung/04_SERVER_STATUS.md`
8. `projektsteuerung/05_ANDROID_SERVER_VERTRAG.md`
9. `projektsteuerung/06_E2E_TESTSTATUS.md`
10. `projektsteuerung/07_OFFENE_PUNKTE.md`
11. relevante Code-/API-/Sync-Dateien
12. `projektsteuerung/08_CHAT_CHECKPOINT.md` nur ergänzend

GitHub-Code und aktuelle Branches haben Vorrang vor alten Chats und Archivdateien.

## B. NEUE ENTSCHEIDUNG VORHER SICHERN
Neue Anforderung, Entscheidung, Bestätigung, Fehlerfeststellung oder Vertragsänderung zuerst in `02_AKTIVER_AUFTRAG.md` bzw. der passenden Steuerungsdatei festhalten; bei Android↔Server zusätzlich `05_ANDROID_SERVER_VERTRAG.md`; committen; erst dann entwickeln. Reine Erklärungen brauchen keinen Commit.

## C. ROLLENGRENZEN
- ANDROID: `feature/tb-android-2.0`, primär `app/**`
- SERVER: `feature/tb-server-1.0`, primär `server/**`
- PRÜFER: vergleicht beide Seiten; entwickelt nur auf ausdrücklichen Auftrag

## D. SCHUTZ
`☑`, `BESTÄTIGT`, `ERLEDIGT`, `PASST`, `FUNKTIONIERT` und `Nicht ohne Grund ändern = JA` sind geschützt.

## E. TESTBEGRIFFE
Source-Check ≠ Build ≠ Installation ≠ E2E. GitHub Actions ohne Runner-Steps sind kein Testnachweis.

## F. CHAT-SICHERUNG
Ein exakter Chat-Kontext-Prozentwert ist nicht verlässlich verfügbar und kann durch ein Repository-Skript nicht gemessen werden. Deshalb nach jedem atomaren Arbeitsblock, vor ANDROID↔SERVER-Wechsel, nach Build/Test, vor „fertig/erledigt/bestätigt“ und bei Unsicherheit Status + `08_CHAT_CHECKPOINT.md` committen.

## G. ALTE PFADSTRUKTUR ABGESCHAFFT
Nicht neu anlegen: `projektstatus/`, `releases/`, `uebergaben/`.

Neu:
- Releases: `projektsteuerung/releases/`
- Übergaben: `projektsteuerung/uebergaben/`
- Excel: `projektsteuerung/TB-HELFER_STATUS.xlsx`
- Verträge: `projektsteuerung/vertraege/`
- alte Stände: `projektsteuerung/archiv/`

## H. AKTUELLER INTEGRATIONSPUNKT BILDER
Der Bildtransport ist im 15×15-Zielmodell bereits festgelegt:
- Kameraoriginal bleibt auf dem Aufnahme-Handy;
- Handy→Server: verkleinerte Sync-Version + Thumbnail + Metadaten;
- Neue Portal-Baustellenfotos werden vor Upload verkleinert; PC-Original bleibt lokal. Servergrenze seit PORTAL-012: maximal 2.000.000 Bytes pro neuem Foto. Bestehende größere Serverdateien bleiben unverändert;
- Server→Handy und Handy→anderes Handy nutzen kleine Version/Thumbnail statt automatischem Volloriginal.

Vor weiterem Bildumbau `02_AKTIVER_AUFTRAG.md` und `05_ANDROID_SERVER_VERTRAG.md` lesen.


## Entwicklungsauftrag GESAMT-1108 – ausdrücklich freigegeben
Der Nutzer hat nach der Gesamtprüfung mit „okay alles bauen“ die Umsetzung der dort beschriebenen Korrekturen beauftragt. Die vorherige Beschränkung dieses Auftrags auf Prüfung ist damit für diesen Entwicklungsblock aufgehoben.
Umfang: Server-Rechte und PDF-Commit/Cleanup, bekannte manuelle KM-Katalogfehler; Android-Trassendatenabgleich/atomarer Empfang/gemeinsamer KM-Vertrag und Originaltransport; Fachdelta, Logo400, Versionsheader, 16-Stunden-Frist; fachliche Prüfpflege und revisionsgenauer Querabgleich. Bestehende Architektur/IDs/Originale/Bestätigungen erhalten.
Gezielt testen, erforderliche Builds ausführen und Android-APK nur zusammen mit exakt passender Release-MD bereitstellen. Signaturkontinuität muss geprüft werden; kein erfundener Ersatz für einen fehlenden vorhandenen privaten Signierschlüssel. Keine NAS-Übernahme, produktive Migration/Datenmutation oder SSH beauftragt. Kein Budget erhöhen. Einzeltests/Build/CI/Installation/E2E getrennt nachweisen.



## KOMPAT-1120 – gemeinsamer Entwicklungsauftrag (20.11.2026)
Benutzer hat GitHub-Bereinigung ohne Verlust historischer Nachweise, synchrone Android-/Server-Statusführung und gezielte Fehlerkorrekturen freigegeben. Basis: Android 1.3.46/200096 Fachcommit cda78c2f42fd7b1acd76d60612ce42d882662edf, Server 1.0.0-dev/API v1/PDF 6 Testcommit b924da3e9bbff603a78ca55753cefb439f1ac607. Basispaarung ist keine reale Pixel/Poco/NAS-Abnahme. Neue fachliche Commits gesondert ausweisen. E01–E12 bleiben offen. Keine NAS-/Produktivdatenänderung. Neue Fehler F-01 bis F-07 in 09_KOMPATIBILITAETSBASIS.md, Altbestätigungen und Archiv behalten.
