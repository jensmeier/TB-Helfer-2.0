# TB-Helfer – aktueller Projektstand

Stand: 2026-09-14

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
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft/mergeable.
- Remote-Head vor dieser Doku-Aktualisierung: `c09946ed7de26c2812e9fd20e266b8003a6615dd` (`fix: make 200035 PowerShell patcher parse on Windows`).
- `main` letzter bekannter stabiler Stand: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`.
- PR bleibt Draft bis zum erfolgreichen Zwei-Geräte-/Mehrhelfer-Ende-zu-Ende-Test.

## WICHTIG – lokaler PC-Stand Android 200035 noch nicht gepusht
Auf dem Windows-PC des Benutzers wurde der versionierte PowerShell-Patch erfolgreich ausgeführt:
- `.github/scripts/patch_200035.ps1`
- Ausgabe u. a. `BautagebuchPdf.kt: PATCH_200035_OK`, `build.gradle.kts: versionCode 200035 gesetzt`, `PATCH_200035_FERTIG`.
- Ein zweiter Lauf meldete korrekt `200035 bereits angewendet`.

Dadurch sind auf dem PC **zwei lokale, noch nicht committete/gepushte Source-Dateien** geändert:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchPdf.kt`
- `app/build.gradle.kts` (`versionCode = 200035`, `versionName = 1.3`)

GitHub Desktop zeigte genau diese zwei lokalen Änderungen. Der Remote-Branch enthält zur Reproduktion die Patchskripte `.github/scripts/patch_200035.py` und `.github/scripts/patch_200035.ps1`, aber die gepatchte Kotlin-Datei/200035-Version selbst war beim Chatwechsel noch nicht gepusht.

### Lokaler Build Android 200035
Der lokale 200035-Source-Stand wurde erfolgreich kompiliert:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat :app:assembleDebug --rerun-tasks
```
Ergebnis:
- `BUILD SUCCESSFUL in 2m 3s`
- 38 Tasks ausgeführt.
- Debug-APK: `app\build\outputs\apk\debug\app-debug.apk`
- neuer APK-Zeitstempel: **14.09.2026 12:48:19**.
- bekannte Compiler-/Deprecated-Warnungen bleiben separater Aufräumpunkt.
- **200035 ist beim Chatwechsel noch nicht als neue APK auf Gerät bestätigt.**

Hinweis: Android Studios Menü `Generate APKs` hatte zuvor die APK nicht neu geschrieben; die alte Datei hatte Zeitstempel 12:00:57. Der direkte Gradle-Build mit gesetztem `JAVA_HOME` erzeugte korrekt die neue APK.

## Produktiver Synology-Stand – unverändert
- NAS/Synology ist Source of Truth.
- API produktiv: **0.7.0**.
- PostgreSQL produktiv: **Schema 10** `010_worksite_invitations_company_profile`.
- produktiver Servercode: `/volume1/docker/tb-helfer/server_schema10_stage`.
- produktive Secrets ausschließlich in `./server/.env`.
- PostgreSQL/API/cloudflared zuletzt bestätigt gesund; 72 OpenAPI-Pfade, Schema 10.
- in der gesamten aktuellen Android-BTB-Runde wurden **keine NAS-/DB-Änderungen** durchgeführt.
- letzter bestätigter Produktiv-Backupstand: `/volume1/docker/tb-helfer/backup/tb_helfer_pre_schema10_20260910_173351.dump`, damals `BACKUP_PRUEFUNG=OK`.
- Schema 11 für persönliche Nutzerkontakte ist im Branch vorbereitet, aber **nicht produktiv migriert**.
- vor produktivem Schema-11-Schritt genau ein frisches verifiziertes DB-Backup erstellen.

## Praktisch bestätigter Stand Android 200034
200034 wurde auf Gerät praktisch bestätigt:
- Startmenü `Aktuelle Baustelle` nutzt ohne aktiven BTB den Kamera-Guard.
- Variante 3 funktioniert: `Nur Baustellenfoto` / `BTB starten/öffnen`.
- allgemeines Baustellenfoto ohne `btbId` läuft nicht mehr in einen modernen BTB hinein.
- abgeschlossenen BTB über Verlauf erneut bearbeiten funktioniert.
- Nachtmodus `Dunkel/Hell/System` bleibt gespeichert und sieht grundsätzlich gut aus.
- nach erfolgreichem `BTB Abschluss` wird die aktive `btbId` gelöst; Bautagebuch zeigt `Noch kein BTB`.
- bewusstes `BTB bearbeiten` reaktiviert exakt dieselbe `btbId`; danach ist erneuter Abschluss möglich.
- moderne BTB-Fotos werden strikt über dieselbe `btbId` zugeordnet; Zeitfenster ist bei modernen Fotos nicht Identität.
- Fotozähler/Abschnittseditor zeigten passende Zuordnungen wie `2/2`, `3/3`, `4/4` usw.
- Nutzerprofil lokal: Name, persönliche Telefonnummer und persönliche E-Mail bleiben erhalten.
- App-interner PDF-Viewer funktioniert.

## Android 200035 – Zweck des lokalen Source-Patches
Der lokale 200035-Patch in `BautagebuchPdf.kt` soll speziell die noch offene Original-Diff-Logik für Fotozeilen korrigieren:
- moderne PDF-Fotoauswahl strikt nach `btbId`, nicht nach Arbeitszeit.
- Foto-ID-Menge des ersten Abschlusses ist Originalbasis.
- nach erstem Abschluss neu hinzugekommene Fotozeilen im Hauptteil werden rot (`forceRed`).
- Original- und Nachtragsfotos mit gleichem Text werden nicht zu einer einzigen schwarzen Gruppe vermischt.
- gelöschte fachliche Originalzeilen werden rot `(entfernt) ...` dargestellt.
- aus dem Original-BTB entfernte Fotozuordnungen sollen ebenfalls als rote `(entfernt) ...`-Änderung dokumentiert werden.

### ACHTUNG zu Screenshots unmittelbar vor dem Chatwechsel
Mehrere Gerät-Screenshots wurden nach Anwenden des Source-Patches aufgenommen, **aber bevor festgestellt wurde, dass `Generate APKs` noch die alte APK von 12:00 verwendete**. Diese Screenshots dürfen deshalb **nicht als Gerätetest von 200035** gewertet werden. Der echte 200035-Gerätetest beginnt erst mit Installation der APK von 12:48:19 oder einer späteren APK aus demselben Source-Stand.

## Verbindliches BTB-Kernmodell
### Identität
- ein fachlicher BTB = dauerhafte stabile `btbId`.
- Datum/Uhrzeit/Tag/Nacht sind Attribute, nie Identität.
- mehrere BTBs desselben Teams am selben Kalendertag sind erlaubt.
- Kamera/Foto, BTB-Kopf, Abschluss und Verlauf verwenden denselben kanonischen aktiven `btbId`-Kontext.
- mehrere Baustellen können lokal vorhanden sein; genau eine ist jeweils aktuell; Daten dürfen sich nicht vermischen.

### Abschluss / Original
- keine sichtbaren V1/V2/V3.
- pro `btbId` höchstens ein aktuell gültiges Abschluss-PDF.
- erster erfolgreicher `BTB Abschluss` = unveränderliches Original.
- spätere fachliche Änderungen werden immer gegen dieses erste Original verglichen.
- Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter sind Kopfdaten und bleiben im PDF schwarz.
- fachliche neue/geänderte Inhalte rot; entfernte Originalinhalte rot als `(entfernt) ...`.
- neues PDF ersetzt das vorige PDF derselben `btbId`, nicht das Original.
- nach erfolgreichem Abschluss wird die aktive `btbId` gelöst.
- abgeschlossener BTB wird nur über Verlauf bewusst wieder aktiviert.
- nach dem ersten Abschluss darf Wetter nicht mehr automatisch aktualisiert werden; spätere Änderung nur bewusst/manuell.

### Fotos
- normale moderne BTB-Fotos erhalten beim Speichern die aktive `btbId`.
- moderner BTB sieht nur Fotos mit exakt derselben `btbId`.
- allgemeine Baustellenfotos ohne `btbId` gehören nie später durch Datum/Uhrzeit zu einem modernen BTB.
- Zeitfenster-Fallback nur für echte Legacy-BTBs ohne `btbId`.
- Foto-ID-Menge des ersten erfolgreichen Abschlusses gehört zum unveränderlichen Original.
- nachträglich hinzugefügte BTB-Fotos invalidieren den Abschluss und werden im Anhang rot markiert.
- Fotozeilen/Bildreferenzen im Hauptteil müssen ebenfalls Original/Nachtrag/Entfernt korrekt unterscheiden.

## Letzte fachliche Beobachtungen / nächste UI-Punkte
Diese Punkte wurden beim letzten Test/Review vor dem Chatwechsel genannt und sind **noch nicht Bestandteil des 200035-Patches**, sofern nicht anders angegeben:
1. **Nachtmodus-Lesbarkeit:** im Dialog `Neuen BTB starten` sind Beschriftungen teilweise zu kontrastarm/unlesbar. Nur Lesbarkeit korrigieren, kein großes Redesign.
2. **Hinweis nach erstem Abschluss:** sinnvoller Hinweis, dass der erste Abschluss das Original festschreibt und spätere fachliche Änderungen rot erscheinen. Benutzer schlug zusätzlich eine direkte ZIP-Senden-Auswahl vor; als UI-Idee noch sauber umsetzen/abstimmen.
3. **Foto-Gruppierung im PDF:** wenn mehrere Fotos dieselben fachlichen Daten/Text haben, zeigt der Abschnittseditor bereits sinnvoll z. B. `12:13–12:16 ... [Bild 2–3]`. Das PDF soll dieselbe Gruppierung/Zeitraum-/Bildbereichsdarstellung verwenden, statt daraus unnötig getrennte Zeilen zu machen.
4. **Neuen BTB – Uhrzeitwahl:** beim Anlegen eines neuen BTB soll Start/Ende über die bekannte Uhr-/TimePicker-Auswahl erfolgen, nicht über die alte Dropdown-Auswahl.
5. **PDF-Strukturfarben:** Strukturüberschriften wie Abschnitt/Firma/Unterbaustelle sollen schwarz bleiben; nur echte fachliche Abweichungen rot.
6. **Einzel-BTB-DOCX:** besitzt noch nicht dieselbe Original-/Rotformatierungslogik wie das PDF.

## Teams / Baustellen / Sync – offen/teilweise vorbereitet
- Zusammenarbeit strikt innerhalb derselben Firma.
- mehrere verantwortliche TBs möglich; jeder verantwortliche TB hat mit seinen Helfern eigenen gemeinsamen BTB-Bereich.
- Helfer nur im freigegebenen Bereich; verantwortliche TBs gemäß Projektregeln baustellenweit.
- Baustellenkarte soll Teamumfang und letzten Sync zeigen, z. B. `2 TB · 4 Helfer` und `Zuletzt synchronisiert ...`.
- Hauptmenü `Mit NAS synchronisieren` + letzter Sync.
- Auto-Sync: manuell / stündlich / täglich plus Netzart `nur WLAN` / `nur Mobilfunk` / `beides`.
- QR-Beitritt fragt `Jetzt öffnen?` / `Später`.
- vollständiger Unterbaustellen-/Baustellenstruktur-Sync bleibt offen.
- serverseitiges Entfernen/Verlassen und Zwei-Geräte-Konflikttests bleiben offen.

## Nutzerprofil / Firma
Praktisch bestätigt:
- Benutzername wird angezeigt.
- persönliche Telefonnummer und persönliche E-Mail können auf Schema 10 lokal gespeichert werden und bleiben nach Neustart erhalten.
- Firmenprofil bleibt getrennt und serverseitig zentral.
- Firmenlogo kommt zentral vom Server und wird für Geräte derselben Firma gecached.

Schema 11 vorbereitet, noch nicht produktiv:
- `server/migrations/011_user_contact_profile.sql`
- `server/checks/05_verify_schema11.sql`
- `server/checks/06_verify_schema11_source.py`
- `server/app_parts/18_part.py`
- `GET /api/v1/user/profile`
- `PUT /api/v1/user/profile`

## Nicht vergessen – spätere Fachfunktionen aus Benutzerplanung
- SKV-Abnahme inkl. GPS.
- BTB auch unter Windows/Laptop bearbeiten; dort ebenfalls Abnahmen und Mängel.
- Spracheingabe.
- vereinfachte Mangelaufnahme: `Was? -> Wo? (aktueller km / km ansagen) -> Was ist los?`.
- `Mein Ordner`: selbst angelegte Ordner mit anderen Nutzern synchronisieren; `nur lesen` oder `lesen & bearbeiten`.
- externe Fotos importieren/einfügen.
- Wetterdarstellung um Niederschlagsmenge `l/m²`/mm sinnvoll ergänzen.
- Aktivierung/QR übernimmt vorhandene Benutzer-/Firmenstammdaten automatisch vom Server.
- Ablauf-/Lizenzhinweis wie `LÄUFT BALD AB – noch 2 Tage`.
- Support-/Gerätekennung und nur maskierten Schlüsselhinweis anzeigen; keine Geheimwerte.
- zentrales Firmenlogo für alle Nutzer derselben Firma.
- Streckendaten/Kilometrierung weiterführen; Originalvermessungsdaten unverändert lassen.

## Nächster sinnvoller Schritt im neuen Chat
1. **GitHub zuerst erneut prüfen** (Regeln, diese MD, Draft-PR #5, neuesten Commit).
2. Übergabe-MD `docs/TB-HELFER_CHAT_UEBERGABE_2026-09-14_feature-admin-offline-sync.md` ergänzend lesen.
3. Auf Benutzer-PC beachten: 200035-Sourceänderungen sind lokal und noch nicht gepusht.
4. APK von 12:48:19 als Update installieren, App-Daten nicht löschen.
5. Echten **200035-Gerätetest** durchführen:
   - neuen BTB / erstes Original mit Text und Fotos abschließen;
   - BTB aus Verlauf wieder öffnen;
   - neues Foto nach Original hinzufügen -> Foto im Anhang rot **und Fotozeile im Hauptteil rot**;
   - Originalinhalt/Originalfotozuordnung entfernen -> im Folge-PDF rot `(entfernt) ...`, nicht lautlos verschwunden;
   - Originaleinträge bleiben schwarz;
   - allgemeines Baustellenfoto bleibt außerhalb moderner BTBs.
6. Wenn 200035-Gerätetest passt: die zwei lokalen Source-Dateien über GitHub Desktop committen/pushen und Projektstand auf `200035 getestet` aktualisieren.
7. Danach die offenen UI-Punkte (Nachtmodus-Dialog, Abschluss-Hinweis/ZIP, Foto-Gruppierung, TimePicker bei neuem BTB) als nächsten kleinen Patch angehen.
8. Produktiven NAS weiterhin nicht anfassen.
