# TB-HELFER 2.0 – 00 START HIER

**Einziger Einstieg für neue Chats und neue Arbeitsblöcke.**

Repository: `jensmeier/TB-Helfer-2.0`

## Aktueller Prüfstand
Stand08.11.2026 – unabhängige Gesamtprüfung.
Geprüfter SERVER-Fachstand `b213bcbfe8d5fcf49c17e71108b8be04167f4d7f`; ANDROID-Fachstand `a8401775a3f607594df91468e728ee3f6d3a1ec4`.
Nachfolgende Dokumentationscommits verändern diese geprüften Fachstände nicht.
[Vollständiger Prüfbericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [Unveränderte vorherige Fassungen](archiv/GESAMTPRUEFUNG_2026-11-08/README.md).

`01_MASTERSTATUS.md` bis `07_OFFENE_PUNKTE.md` sind die konsolidierte aktuelle Sicht. Archivtexte sind historische Belege, keine zweite aktuelle Aufgabenliste. Geschützte Bestätigungen und nicht abgeschlossene Restaufträge bleiben erhalten.
PRÜFER: prüfen, nicht entwickeln. Keine APK, Migration, produktiven Änderungen oder NAS-Übernahme ohne entsprechenden Auftrag. Der Auftrag08.11 erlaubt ausschließlich Prüfung und Dokumentationsbereinigung.

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

