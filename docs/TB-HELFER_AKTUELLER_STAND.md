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
- aktueller Android-Entwicklungsstand: **versionCode 200031**, `versionName 1.3`.
- Android 200031 ist vorbereitet, aber noch **nicht lokal kompiliert/installiert/getestet**.
- `docs/TB-HELFER_PROJEKTREGELN.md` ist auf die neuen 200031-Regeln (Tages-ZIP, BTB-Löschen, btbId-Foto-Priorität, schwarze Kopfdaten, Wetter-Freeze) abgeglichen.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets bleiben ausschließlich in `./server/.env`.
- letzter bestätigter Produktivstand: PostgreSQL healthy, API/cloudflared gestartet, 72 OpenAPI-Pfade, Schema 10.
- in der gesamten Android-200025 bis 200031-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter produktiver Backup-Stand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals mit `pg_restore -l` als `BACKUP_PRUEFUNG=OK` bestätigt.
- Schema 11 für persönliche Nutzerkontakte ist im Branch vorbereitet, aber **nicht produktiv migriert**.
- vor dem nächsten produktiven Server-/Schema-Schritt genau ein frisches verifiziertes DB-Backup erstellen.

## Praktisch bestätigter Android-Stand bis 200030
- Debug-APK wurde zuvor erfolgreich lokal gebaut/signiert und per ADB installiert; bekannte 7 Kotlin/Java-Warnungen bleiben späterer Aufräumpunkt.
- bloßes Öffnen erzeugt keinen fachlichen BTB; `Noch kein BTB` funktioniert.
- grünes `+` legt erst nach ausdrücklichem `Neuer BTB` eine neue stabile `btbId` an.
- mehrere BTBs am selben Tag funktionieren grundsätzlich.
- erster erfolgreicher `BTB Abschluss` bleibt unveränderliches Original.
- spätere Textänderungen bleiben gegenüber diesem ersten Original rot.
- gelöschter Originaltext erscheint rot als `(entfernt) ...`.
- App-interner PDF-Viewer funktioniert inkl. Safe-Areas/Seitennavigation.
- lokales Nutzerprofil funktioniert: Name, persönliche Telefonnummer und E-Mail bleiben erhalten.
- Foto-Isolation über `btbId` funktioniert im getesteten Verlauf; Zähler zeigten u. a. `3/3` und später `5/5` am selben BTB.
- nach dem ersten Abschluss neue Fotos werden im Fotoanhang rot mit `ÄNDERUNG ZUM ORIGINAL` markiert.
- Fotoänderung invalidiert das aktuelle Abschluss-PDF; aktuelle Screenshots zeigen korrekt `Nachbearbeitung · Abschluss-PDF ungültig` auch im BTB-Verlauf.
- kompakter BTB-Kopf und Fenster `Aktuellen BTB bearbeiten` funktionieren.

## Android 200031 – aktuell vorbereiteter Patch
### Betroffene Dateien
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPatch200031Installer.kt` – neu
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDeleteService.kt` – neu
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPhotoCounterFix.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbFotoZuordnungSpeicher.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbRendererBaselineBridge.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BtbVerlaufFragment.kt`
- `app/src/main/java/de/tbhelfer/ui/HauptActivity.kt`
- `app/build.gradle.kts`
- `docs/TB-HELFER_PROJEKTREGELN.md`

### 1. PDF Entwurf
- `PDF Entwurf` wird im neuen Installer übernommen.
- aktueller BTB wird zuerst sauber gespeichert.
- Entwurfs-PDF nutzt dieselbe btbId-/Original-/Foto-Aufbereitung wie der moderne Abschluss, ohne den BTB abzuschließen.
- nach erfolgreichem Erzeugen öffnet **sofort der App-interne `BtbPdfViewerActivity`**; kein externer Viewer und kein zusätzlicher `Öffnen`-Snackbar-Schritt.

### 2. BTB vollständig löschen
- im `BTB-Verlauf` gibt es pro modernem BTB zusätzlich `BTB löschen` mit Sicherheitsabfrage.
- gelöscht werden der BTB-Datensatz, unveränderliches Original, Abschluss-Metadaten sowie app-eigene PDF/DOCX-Dateien dieser `btbId`.
- **echte Baustellenfotos werden nicht physisch gelöscht**; ihre Foto→btbId-Zuordnung wird nur gelöst. Das verhindert versehentlichen Verlust von Beweis-/Baustellenfotos.
- wenn der gelöschte BTB aktuell war, wird der aktive Pointer entfernt und – falls vorhanden – der nächste verbliebene BTB aktiviert.
- Legacy-BTBs (`legacy:*`) werden durch diese neue Löschfunktion noch nicht destruktiv gelöscht.

### 3. Fotozuordnung schlägt Zeitfenster
- moderne Fotos, die explizit der aktuellen `btbId` zugeordnet sind, zählen künftig **auch außerhalb der ursprünglich eingetragenen Arbeitszeit** zu diesem BTB.
- die Arbeitszeit ist Dokumentinhalt, nicht Foto-Identität.
- ein Foto mit fremder `btbId` bleibt ausgeschlossen.
- nur historische Fotos ohne btbId benutzen weiterhin das Arbeitszeitfenster als Legacy-Fallback.

### 4. Arbeitszeit / Kopfdaten / Wetter
- Klick auf das vorhandene Datum-/Zeit-Feld wird durch 200031 auf einen direkten Arbeitszeit-Dialog umgebogen; das alte Popup mit `+ Neuer BTB / Entfernen` ist für diese Bearbeitung nicht mehr der Zielweg.
- Beginn/Ende werden direkt am **selben BTB / derselben `btbId`** gespeichert.
- anschließend wird die BTB-View neu gebunden, damit private Altwerte im historischen Fragment die gespeicherte Uhrzeit nicht beim nächsten Autosave wieder überschreiben.
- bei Schicht über Mitternacht wird `endeDatumIso` automatisch auf den Folgetag gesetzt.
- Arbeitszeit, Personal/Firmen, Geräte/Maschinen und Wetter werden für die **rote PDF-Darstellung neutralisiert**: diese Kopfdaten sollen schwarz bleiben. Die echte Originaldatei wird dafür nicht verändert; nur die temporäre Renderer-Baseline wird angepasst.
- Abschnitte 4–8 bleiben echter Vergleich gegen das erste Original.
- nach dem ersten Abschluss ist die normale Wetter-Vorschau nicht mehr klickbar und kann keinen automatischen Wetter-Refresh auslösen. Manuelle Wetter-Nachbearbeitung bleibt über den orangefarbenen Stift möglich.

### 5. ZIP im BTB-Verlauf = Tagesabschluss
- `ZIP teilen` im BTB-Verlauf erzeugt nicht mehr nur `BtbEinzelZip`.
- stattdessen wird für den Kalendertag des gewählten BTB der vorhandene `BtbExportPaket.erstellenBaustellenExport(...)` verwendet.
- Ziel ist die bekannte Laptop-/Tagesabschluss-Struktur mit BTB, Bildern, Mängeln, Abnahmen sowie Gesamtlisten (`Bilderliste_gesamt.*`, Mängel-/Abnahmelisten usw.).
- der Exporter liefert ein `Ergebnis`; geteilt wird korrekt dessen `zipFile`.
- dadurch wird keine zweite parallele Exportlogik erfunden.

### Tests 200031
- GitHub-Codepfade statisch gegen den aktuellen Feature-Branch geprüft.
- beim statischen Check wurde der Rückgabetyp des Tagesabschluss-Exporters (`Ergebnis.zipFile`) korrigiert.
- **noch kein lokaler Gradle-Build/ADB-Gerätetest**; nicht als kompiliert bestätigen.
- NAS/DB nicht verändert.

## BTB – verbindliches Kernmodell
### Identität
- ein fachlicher BTB = eine dauerhafte stabile `btbId`.
- Datum/Uhrzeit/Tag/Nacht sind Attribute, nie technische Identität.
- mehrere BTBs desselben Teams am selben Tag sind erlaubt.
- BTB 1 ↔ BTB 2 wird über konkrete `btbId` gewechselt; aktueller BTB wird vorher tatsächlich gespeichert.
- mehrere Baustellen dürfen lokal vorhanden sein; genau eine ist jeweils aktuell. Daten verschiedener Baustellen dürfen sich nie vermischen.

### Abschluss / Original
- keine sichtbaren V1/V2/V3.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- erster erfolgreicher `BTB Abschluss` = unveränderliches Original.
- Originaldatei intern `btb_<btbId>_original.json`; spätere Abschlüsse überschreiben sie nicht.
- fachliche Rotmarkierung bleibt dauerhaft gegen dieses erste Original.
- Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter bleiben bei Änderungen in der PDF-Darstellung schwarz.
- zurück auf Originalwert = wieder schwarz.
- tatsächliche Änderung nach Abschluss invalidiert/löscht das bisherige PDF; Status `Nachbearbeitung`.
- späterer Abschluss ersetzt nur das aktuelle PDF.
- technischer Abschluss-Metadatensatz hält Hash/Dateiname/Gültigkeit des aktuellen PDFs; keine sichtbare Versionierung.

### Fotos
- moderne Fotos erhalten eine eindeutige `btbId`.
- explizite Foto-btbId ist stärker als das Arbeitszeitfenster.
- Foto-ID-Menge des ersten erfolgreichen Abschlusses bleibt unveränderlich.
- neues/entferntes/abgewähltes Foto gegenüber diesem Original invalidiert das Abschluss-PDF.
- neue Fotos werden im PDF-Anhang rot markiert; Original-JPG bleibt unverändert.
- Bildreferenzen im Haupttext sollen ebenfalls zwischen Original- und nachträglich hinzugefügten Bildern unterscheiden.

## Nutzerprofil / Name
Praktisch bestätigt:
- Benutzername wird angezeigt.
- persönliche Telefonnummer und persönliche E-Mail können auf Schema 10 lokal gespeichert werden und bleiben nach Neustart erhalten.
- Firmenprofil bleibt getrennt und serverseitig zentral.

Schema 11 vorbereitet, noch nicht produktiv:
- `server/migrations/011_user_contact_profile.sql`
- `server/checks/05_verify_schema11.sql`
- `server/checks/06_verify_schema11_source.py`
- `server/app_parts/18_part.py`
- `GET /api/v1/user/profile`
- `PUT /api/v1/user/profile`

## Teams / Baustellen / Sync
- Zusammenarbeit strikt innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern seinen eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs baustellenweit gemäß Regeln.
- Baustellenkarte soll Teamumfang und letzten Sync anzeigen, z. B. `2 TB · 4 Helfer`.
- Sync-Einstellungen: Manuell / stündlich / täglich; nur WLAN / nur Mobilfunk / WLAN + Mobilfunk.
- Hauptmenü `Mit NAS synchronisieren` + letzter Sync.
- QR-Beitritt fragt `Jetzt öffnen?` / `Später`.
- entfernte/ausgetretene Mitglieder behalten historische lokale Daten nur lesbar; serverseitige Membership-Removal-API bleibt offen.

## Bekannte offene Punkte
- Android 200031 lokal bauen/installieren/testen.
- große historische `BautagebuchPdf.kt` färbt einzelne Strukturüberschriften noch zu breit rot; Überschriften sollen später schwarz bleiben.
- Bildnummern/-bereiche im Haupttext müssen noch differenziert rot werden, wenn die referenzierten Fotos nach dem Originalabschluss hinzukamen.
- Einzel-BTB-DOCX besitzt noch nicht dieselbe Rotformatierung wie das PDF; der Verlauf verwendet ab 200031 allerdings den bestehenden Tagesabschluss-ZIP statt des Einzel-BTB-ZIP.
- der große PDF-Renderer hat intern weiterhin einen alten Zeitfenster-Vorfilter; moderne btbId-Fotos werden in Zähler/BTB-Zuordnung bereits unabhängig von der Zeit behandelt, die vollständige Renderer-Bereinigung bleibt noch zu prüfen.
- BTB 1 ↔ BTB 2 und Baustelle A ↔ Baustelle B praktisch weiter testen.
- 7 bekannte Compilerwarnungen später in eigenem Aufräumpatch beseitigen.
- Baustellen-Gesamtexport noch nicht vollständig in allen Altpfaden auf btbId/Ein-PDF modernisiert.
- vollständiger Unterbaustellen-/Baustellenstruktur-Sync (`Immenstadt` / `5362-1`) bleibt offen.
- serverseitiges Entfernen/Verlassen mit lokal nur-lesbar bleibt offen.
- Zwei-Geräte-Tests für BTB, Bilder, Mängel, Abnahmen, 409-Konflikte, Tombstones, Netzabbruch und Benachrichtigungen bleiben offen.
- Schema 11 erst nach frischem Backup/Hashprüfung/read-only Verifikation produktiv migrieren.
- danach Vermessungs-/Kilometrierungsblock weiterführen.

## Nächster sicherer Test
1. `git pull`
2. `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
3. nur bei erfolgreichem Build Android 200031 per ADB als Update installieren; App-Daten nicht löschen.
4. `PDF Entwurf` drücken → interner Viewer muss sofort öffnen.
5. beim bestehenden BTB `Datum/Zeit → Bearbeiten`: Beginn/Ende ändern, speichern, danach anderen Abschnitt bearbeiten/neu öffnen; neue Zeit muss erhalten bleiben.
6. bereits abgeschlossenen BTB bearbeiten: Wetter darf sich nicht automatisch aktualisieren; manuell über Stift muss es weiterhin bearbeitbar sein.
7. Foto nach alter Endzeit aufnehmen, solange derselbe BTB aktiv ist → Fotozähler muss es wegen derselben `btbId` mitzählen.
8. PDF prüfen: Arbeitszeit/Personal/Geräte/Wetter bleiben schwarz; fachliche Änderungen in Abschnitt 4–8 bleiben rot.
9. im Verlauf `ZIP teilen` → ZIP-Inhalt als Tagesabschluss mit BTB/Bildern/Mängeln/Abnahmen/Gesamtlisten prüfen.
10. einen entbehrlichen Test-BTB über `BTB löschen` entfernen → BTB/Dokumente weg, echte Fotos weiterhin in der Baustelle vorhanden.
11. produktiven NAS weiterhin nicht anfassen.
