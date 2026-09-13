# TB-Helfer – aktueller Projektstand

Stand: 2026-09-13

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen zusätzlich in `docs/TB-HELFER_PROJEKTREGELN.md`.

Vor jeder Änderung zuerst lesen:
1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. diese Datei
3. offenen Draft-PR und neuesten Commit

`main` bleibt stabil. Wesentliche Änderungen zuerst im Feature-Branch/Draft-PR und danach diesen Stand aktualisieren.

## Repository / Branch / PR
- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiv: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`
- PR bleibt Draft bis zum erfolgreichen Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test.
- aktueller Android-Entwicklungsstand: **versionCode 200026**, `versionName 1.3`.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets bleiben ausschließlich in `./server/.env`.
- letzter bestätigter Produktivstand: PostgreSQL healthy, API/cloudflared gestartet, 72 OpenAPI-Pfade, `DB_TEST=OK`, Schema 10.
- in der gesamten Android-200025/200026-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter produktiver Backup-Stand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals mit `pg_restore -l` als `BACKUP_PRUEFUNG=OK` bestätigt.

Wichtig: Der Feature-Branch enthält neueren Multi-BTB-Servercode als der produktive Stage-Ordner. Schema 11 für persönliche Nutzerkontakte ist vorbereitet, aber **nicht produktiv migriert**.

## Lokaler Android-Teststand
### Android 200025 – praktisch gebaut/installiert
Am 13.09.2026 lokal in Android Studio erfolgreich gebaut.
- Build lief vollständig durch.
- 7 bekannte Kotlin/Java-Deprecation-/Redundanz-Warnungen, keine Buildfehler.
- Debug-APK mit `apksigner` geprüft: `Verifies`, v1=true, v2=true.
- APK ließ sich zunächst nach Dateiübertragung auf dem Handy nicht parsen; direkte Installation per ADB funktionierte. APK selbst war gültig.
- 200025 wurde auf dem Testgerät praktisch geöffnet/getestet.

Praktisch mit 200025 bestätigt:
- orange Bearbeiten-Stift neben grünem `+` sichtbar.
- `BTB Abschluss`-Grundfunktion/PDF-Erzeugung funktioniert technisch.
- App-interner PDF-Viewer funktioniert grundsätzlich.
- PDF-Diff markiert neue/geänderte Textanteile bereits rot.
- Einzel-BTB-ZIP ist btbId-basiert und enthält genau den ausgewählten BTB statt TAG/NACHT-`(2)`-Ausweichlogik.

Beim Gerätetest 200025 gefundene Punkte:
1. Beim Öffnen einer Baustelle ohne vorhandenen BTB zeigte der alte Banner sofort `Tag ... / Entwurf`. Codeprüfung ergab: dies ist mindestens ein Schein-Entwurf der Anzeige; der neue 200026-Watcher setzt bei fehlender realer btbId `Noch kein BTB`.
2. Der erste Teststand behandelte den jeweils letzten Abschluss noch als Vergleichsbasis. Das wurde fachlich verworfen.
3. Im vorhandenen PDF-Renderer werden bei einem geänderten Abschnitt derzeit zusätzlich Abschnitts-/Gruppenüberschriften rot, weil `SectionChange.changed` auch die Überschrift färbt. Die eigentlichen geänderten Zeilen werden bereits differenziert. Dieses optische Renderer-Aufräumen ist noch offen.
4. PDF-Viewer lag auf dem Testgerät oben zu nah an Kamera/Statusleiste und unten zu nah an Android-Systemnavigation. 200026 berücksichtigt System-Safe-Areas.
5. Beschriftung `PDF Abschluss` soll fachlich `BTB Abschluss` heißen; 200026 setzt dies im Lifecycle-Installer.

## BTB – verbindliches aktuelles Modell
### Identität
- ein fachlicher BTB = eine dauerhafte stabile `btbId`.
- Datum, Uhrzeit und Tag/Nacht sind nur Attribute/Anzeige.
- mehrere BTBs desselben Teams am selben Tag sind erlaubt.
- Wechsel BTB 1 ↔ BTB 2 erfolgt über konkrete `btbId`.
- vor Wechsel wird der aktuelle BTB wirklich gespeichert; die frühere feste 1,7-s-Wartezeit ist im modernen Schnellwechsel entfernt.
- mehrere Baustellen dürfen lokal vorhanden sein; genau eine ist jeweils aktuell. Jede Baustelle besitzt ihren eigenen BTB-/Datenzustand.

### Neuer BTB
- neuer BTB nur über grünes `+`.
- Dialog/Karte `Neuen BTB starten` verwendet Datum/Zeit, Personal, Geräte/Maschinen und Wetter.
- erst `Neuer BTB` erzeugt eine neue `btbId`.
- bloßes Öffnen des Bautagebuchs darf keinen fachlichen BTB erzeugen.

### Abschluss – ab Android 200026
Die frühere V1/V2/V3-Logik ist endgültig verworfen.

Verbindlich:
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- **der erste erfolgreiche BTB-Abschluss ist das unveränderliche Original**.
- Originaldatei intern: `btb_<btbId>_original.json`; sie wird nach dem ersten Erzeugen nicht mehr überschrieben.
- alle späteren roten Änderungen werden IMMER gegen dieses erste Original berechnet.
- späterer Abschluss ersetzt nur das eine aktuelle PDF; das Original bleibt unverändert.
- neue/geänderte Inhalte gegenüber Original bleiben rot, auch wenn der BTB mehrfach erneut abgeschlossen wird.
- zurück auf Originalwert = wieder schwarz/unverändert.
- entfernte Originalinhalte sollen rot als `(entfernt) ...` erscheinen.
- ein technischer `btb_<btbId>_abschluss_meta.json`-Datensatz hält nur Hash/Dateiname/Gültigkeit des aktuell erzeugten PDFs. Das ist keine sichtbare Version und kein Dokumentarchiv.
- nach erster tatsächlicher Änderung nach einem Abschluss wird das alte Abschluss-PDF gelöscht/ungültig und Status `Nachbearbeitung`.
- neuer erfolgreicher `BTB Abschluss` setzt den aktuellen PDF-Prüfhash neu, aber **niemals** das Original.
- bestehende Test-BTBs, deren `_original.json` durch den 200025-Test bereits auf einen späteren Abschluss überschrieben wurde, können das historische erste Original nicht künstlich zurückbekommen. Für den belastbaren Original-Test daher einen frischen BTB verwenden.

Wesentliche Dateien:
- `BtbDokumentVersionierung.kt`
- `BtbRendererBaselineBridge.kt`
- `BtbModernLifecycleInstaller.kt`
- `BtbDokumentIdentitaet.kt`
- `BtbAbschlussPdfFinder.kt`
- `BtbVerlaufFragment.kt`
- `BtbPdfViewerActivity.kt`

### PDF-Viewer 200026
- interner Viewer bleibt erhalten.
- Safe-Area oben: Kamera/Statusleiste werden berücksichtigt.
- Safe-Area unten: Android-Navigationsleiste wird berücksichtigt.
- Navigation unten: `Vorherige` – Seitenzähler – `Nächste` nebeneinander.
- `Zurück` bleibt oben im sicheren Inhaltsbereich.

### Foto-Zuordnung
- neue normale BTB-Fotos erhalten eine eindeutige Zuordnung `fotoId -> btbId` über `BtbFotoZuordnungSpeicher`.
- zwei BTBs mit überlappenden Zeiten übernehmen moderne Fotos dadurch nicht allein wegen der Uhrzeit.
- historische unzugeordnete Fotos dürfen weiter über das Zeitfenster angeboten werden.
- Mängel-/Abnahmebeziehungen bleiben separat.

### ZIP / Export
- `ZIP teilen` eines einzelnen BTB verwendet `BtbEinzelZip` und die konkrete `btbId`.
- keine TAG/NACHT-/`(2)`-Identität mehr im Einzel-BTB-ZIP.
- großer Baustellen-Gesamtexport enthält noch ältere Tag/Nacht-orientierte Teile und bleibt ein späterer Aufräumblock.

## Nutzerprofil / Name
Android 200025/200026:
- persönliche Telefonnummer und persönliche E-Mail sind **immer lokal eingebbar und speicherbar**, auch auf produktivem Schema 10.
- Server-Sync dieser persönlichen Kontakte bleibt optional/zusätzlich, sobald Schema 11 produktiv ist.
- Benutzername soll aus vorhandenem User-/Gerätecache als Fallback erscheinen.
- Firmenprofil bleibt serverseitig getrennt von persönlichen Kontakten.

Schema 11 vorbereitet, nicht produktiv:
- `server/migrations/011_user_contact_profile.sql`
- `server/checks/05_verify_schema11.sql`
- `server/checks/06_verify_schema11_source.py`
- `server/app_parts/18_part.py`
- `GET/PUT /api/v1/user/profile`

## Sync / Teams
Im Branch vorhanden:
- Baustellenkarten können `2 TB · 4 Helfer` + letzten Sync anzeigen.
- Sync-Einstellungen: Manuell / stündlich / täglich; nur WLAN / nur Mobilfunk / WLAN + Mobilfunk.
- Hauptmenü `Mit NAS synchronisieren` + letzter Sync.
- manueller Lauf stößt vollständige Collaboration-Pipeline an.
- `ACCESS_NETWORK_STATE` im Manifest.
- QR-Beitritt fragt `Jetzt öffnen?` / `Später` und ersetzt eine andere aktuelle Baustelle nicht ungefragt.

Produktiver Server unterstützt den neueren stabilen Multi-BTB-Code noch nicht vollständig; kein produktiver Zwei-Geräte-Multi-BTB-Test vor kontrolliertem Server-Code-Update.

## Bekannte Warnungen / technische Restschuld
- 7 Compilerwarnungen aus 200025 noch offen; nächster separater Aufräumpatch, nicht mit funktionaler BTB-Logik vermischen.
- großer `BautagebuchFragment.kt` enthält weiterhin historische Tag/Nacht-/Altpfade, die moderne Installer zur Laufzeit übersteuern.
- PDF-Renderer färbt bei Abschnittsänderung derzeit zusätzlich Abschnitts-/Gruppenüberschriften rot. Gewünschtes Endverhalten: nur tatsächlich vom Original abweichende Inhaltsanteile rot; Überschriften bleiben normal. Noch offen.
- großer Gesamtexport noch nicht vollständig auf btbId-Ein-PDF-Modell umgestellt.

## Tests / nächster Schritt
Android 200026 ist im Branch vorbereitet, aber **noch nicht lokal gebaut/installiert**.

Nächster sicherer Ablauf:
1. `git pull`.
2. `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`.
3. 200026 als Update per ADB installieren, keine App-Daten löschen.
4. Anzeige ohne BTB prüfen: `Noch kein BTB`, kein künstlicher Entwurf.
5. Für Original-Test **frischen BTB** anlegen.
6. Originaltext z. B. `AAA` eingeben und `BTB Abschluss` durchführen.
7. danach `BBB` ergänzen -> Nachbearbeitung; altes PDF muss ungültig/gelöscht werden.
8. erneut `BTB Abschluss`: `BBB` muss rot gegen Original `AAA` bleiben.
9. erneut bearbeiten, `CCC` ergänzen, erneut abschließen: `BBB` und `CCC` müssen weiterhin rot bleiben, weil beide vom ersten Original abweichen.
10. einen Originaleintrag löschen -> nächstes PDF soll rot `(entfernt) ...` zeigen.
11. PDF-Viewer Safe-Area/Navigation oben und unten prüfen.
12. Nutzerprofil: lokale persönliche Telefonnummer/E-Mail nach App-Neustart erhalten; Name-Fallback prüfen.

Erst nach lokal sauberem Android-Block folgt ein kontrolliertes Server-Code-Update. Produktive NAS-/DB-Änderungen weiterhin nur nach frischem verifiziertem Backup.
