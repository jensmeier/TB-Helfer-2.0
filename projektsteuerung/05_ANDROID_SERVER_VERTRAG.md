# TB-HELFER 2.0 – ANDROID/SERVER VERTRAG

Stand: 2026-09-26

Detailquellen:
- `projektsteuerung/vertraege/TB-SERVER_ANDROID_SYNC_VERTRAG_2026-09-25.md`
- `projektsteuerung/vertraege/TB-SYNC_15x15_ZIELMODELL.md`
- `docs/TB-HELFER_PROJEKTREGELN.md`

## Zuständigkeit
ANDROID primär `app/**`, SERVER primär `server/**`. Neue Serveranforderung zuerst dokumentieren, dann im SERVER-Projekt umsetzen; keine Ersatzlösung auf der falschen Seite.

## Sync
Objektbezogener Delta-/Dirty-Scope; `Neu laden` nur Recovery/Vollabgleich; Cursor persistent; FINALIZED historische BTBs im normalen Sync read-only.

## Rollen
Helfer: `shift-finish`/`shift-resume`, kein finaler BTB-Abschluss. Haupt-TB finalisiert. Abschluss-PDF nach Serverberechtigung/Rolle.

## Bilder – verbindlich
### Handyaufnahme
- Volloriginal bleibt auf dem Aufnahme-Handy.
- Online-Sync überträgt Foto-ID, Metadaten, kleine Sync-Version (Richtwert längste Kante ca. 1920 px, JPEG ca. 80–82 %, nie hochskalieren) und Thumbnail (Richtwert ca. 512 px).
- Andere Handys laden kleine Version/Thumbnail; Volloriginal wird nicht automatisch vervielfältigt.

### Portalaufnahme
- Empfangene Portaldatei darf serverseitige Quelldatei bleiben.
- Server erzeugt Renditions/Preview/Thumbnail.
- Mobile Geräte erhalten bevorzugt kleine Renditions.

### Originalarchiv
Handy-Volloriginale sind kein normaler Delta-Sync und blockieren den BTB-Abschluss nicht.

## IMG-001
Jetzt prüfen, ob Android tatsächlich eine kleine Sync-Version hochlädt, Server keine Volloriginale unnötig an andere Geräte verteilt, Portal→Handy Renditions nutzt und Metadaten vollständig bleiben. Erst Quellprüfung + realer E2E = BESTÄTIGT.


## IMG-001 – Quellaudit 26.09.2026

### IST
- Android übergibt bei BTB/Mangel/Abnahme aktuell die lokale Originaldatei an den Online-Upload.
- Der Server speichert die empfangene APP-Datei unverändert.
- Android lädt Bilder über `/files/{id}/content`; vorhandene Server-`thumb/preview`-Renditions werden nicht genutzt.
- Android erzeugt lokal bereits 512-px-Thumbnails, nutzt sie aber nicht im Online-Sync.
- BTB-Foto-Metadaten sind teilweise strukturiert; GPS-Genauigkeit fehlt. Mangel-/Abnahmefoto-Metadaten sind im Uploadpfad unvollständig.

### SOLL / Umsetzungsgrenze
- ANDROID erzeugt vor dem normalen Online-Fotoupload eine **separate kleine Sync-Datei** (Richtwert längste Kante ca. 1920 px, JPEG ca. 80–82 %, keine Hochskalierung). Das lokale Volloriginal bleibt unverändert.
- Der Server darf diese kleine APP-Sync-Datei als seine APP-Quelldatei speichern und daraus die vorhandenen Renditions erzeugen.
- ANDROID nutzt für mobile Bildübernahme bevorzugt `preview`; für kleine Listenansichten `thumb`. `/content` ist nicht der normale automatische Bildsync.
- Portal-Uploads dürfen ihre empfangene Quelldatei serverseitig behalten; mobile Auslieferung erfolgt über Renditions.
- Strukturierte Aufnahmezeit/GPS/Foto-Metadaten müssen unabhängig vom Dateinamen erhalten bleiben.
- Kein Server-Workaround, der weiterhin hochgeladene Handy-Volloriginale nur nachträglich versteckt.

### Priorität
Implementierung nach `NET-001`, Hauptgerät↔Server/Portal-E2E und HELPER/Poco-E2E gemäß `E2E-PRIO-001`.

## BTB Active-Pointer Vorrangregel
Für servergebundene BTBs gilt verbindlich:
- `teams.active_btb_id == btb.id` ist der kanonische Team-Aktivzustand.
- Android übernimmt diesen Zustand als lokalen Active-Pointer.
- Solange der Server-Snapshot für denselben BTB `active=true` meldet, darf Android den Active-Pointer **nicht** allein wegen lokaler Abschluss-/PDF-/Baseline-Metadaten lösen.
- Ein HELPER mit Teilnehmerstatus `WORKING` bleibt im aktiven BTB.
- Pointer lösen erst bei kanonischem Serverzustand `FINALIZED` + nicht aktiv bzw. bei explizitem zulässigem Lifecycle-Übergang.
- Lokale/stale Abschlussartefakte dürfen den Server-Lifecycle nicht überstimmen.


## App-Release-/Updateversionierung
- Für Android-Updates ist der `versionCode` strikt monoton steigend.
- Ein neuer APK-/Updatekatalog-Eintrag darf keinen bereits verwendeten `versionCode` wiederverwenden.
- Der auf 1.3.19 / 200069 folgende BTB-SYNC-001-Build ist **1.3.20 / 200070**.
- Diese Nummernregel ändert keinen BTB-/Sync-API-Vertrag; sie stellt nur sicher, dass Server-Updatekatalog und Android einen neuen Build eindeutig erkennen.


## Updatevertrag – installierte Version vs. Releasekatalog
Verbindlich:
- **Installierter Android-Stand** wird lokal aus dem installierten Package bestimmt (`versionName/versionCode`).
- **Verfügbarer Stand** kommt aus dem veröffentlichten Server-Releasekatalog.
- Geräte-Aktivierungsmetadaten wie `tb.devices.app_version/app_build` sind kein zulässiger Runtime-Nachweis für den aktuell installierten App-Stand.
- Update verfügbar genau dann, wenn `release.versionCode > installed.versionCode`.
- Nach Installation eines Releases mit gleichem/höherem installierten `versionCode` darf dieses Release nicht erneut als Update angezeigt werden.


## Release-Binärvertrag
Für Android-Releases gilt zusätzlich:
- Release-Metadaten und APK-Binärdatei müssen dieselbe Package-ID, `versionName` und `versionCode` tragen.
- Android darf eine heruntergeladene APK nicht installieren, wenn APK-Package oder eingebettete Version/Build vom Server-Release abweichen.
- Server-Releaseverwaltung soll perspektivisch denselben Abgleich bereits beim Upload/Publish erzwingen.
- Bis zur Servervalidierung ist der Android-Client-Schutz verbindlich.


## Release-MD als Server-Metadatenquelle
- Die Android-Release-MD ist der vorgesehene Import für den Server-Release-Draft.
- Sie enthält mindestens Plattform, Version, Build, Mindestbuild, Pflichtupdate und Download-URL sowie die Release-Notes.
- Sie ist **Metadatenquelle**, nicht die APK selbst.
- Für einen gültigen Release müssen Release-MD und APK denselben `versionName/versionCode` tragen.
- Android validiert ab 1.3.21 / 200071 zusätzlich die heruntergeladene APK gegen diese Server-Metadaten.


## BTB Konflikt-/Overwrite-Schutz
Für denselben aktiven BTB gilt verbindlich:
- lokal gespeicherte, noch nicht serverbestätigte Android-Änderungen dürfen durch einen eingehenden Server-Snapshot nicht still verloren gehen;
- ANDROID muss einen dirty BTB **vor jedem Snapshot-Apply** zuerst in Pending/Outbox sichern;
- der Server bleibt kanonische Quelle für bestätigte Version/Lifecycle, aber ein Server-Snapshot darf eine noch unbestätigte lokale Mutation nicht vor deren Upload-/Konfliktprüfung überschreiben;
- Uploads arbeiten gegen Basisversionen; bei Versionsabweichung ist der bestehende Conflict-/Rebase-Pfad zu verwenden;
- solange für denselben `clientBtbId` ein Pending oder ein nach Export neu entstandener Dirty-Stand existiert, ist ein stilles `applyServerStateToLocal` für diesen BTB unzulässig;
- nach erfolgreichem Upload darf der bestätigte Server-Snapshot lokal angewendet werden, sofern seit dem verarbeiteten Dirty-Generation-Stand keine neuere lokale Änderung entstanden ist.

### Selektiver BTB-Sync – Reihenfolge
1. lokaler Save markiert Dirty;
2. Worker exportiert genau diesen lokalen BTB in Pending/Outbox;
3. Serverversion wird im Mutation-/Konfliktpfad geprüft;
4. Upload/Conflict wird abgearbeitet;
5. nur bei bestätigtem Upload und unverändertem Dirty-Generation-Stand wird der Server-Snapshot lokal angewendet;
6. ein neuer Autosave während des Uploads bleibt Dirty und darf nicht durch den gerade bestätigten älteren Snapshot ersetzt werden.

BTB-SYNC-002 erfordert dafür **keine Server-/DB-Vertragsänderung**.


## Rollenabhängige BTB-Abschlussleiste
Für einen aktiven servergebundenen BTB gilt:
- verantwortlicher TB/Haupt-TB: `PDF Entwurf` + `BTB Abschluss` sichtbar;
- HELPER: kein `PDF Entwurf`; nur die eigene Schichtaktion gemäß Teilnehmerstatus;
- FINALIZED + nicht aktiv: keine Abschlussaktion; PDF nur gemäß `can_view_btb_pdf`;
- Android darf die Sichtbarkeit nicht zwischen konkurrierenden Legacy-/Lifecycle-Zuständen wechseln.

Die Anzeige folgt `BtbServerStateStore.isTeamLead` und dem kanonischen Server-Lifecycle.


## BTB-EDIT-001 – strukturierte Eintrags-/Fotozuordnung
Status: **VERTRAG FEST / SERVER-SOURCE VORHANDEN / ANDROID-ANPASSUNG OFFEN**

### Stabile Unterbaustellenidentität
- Android darf eine BTB-Unterbaustelle geräteübergreifend **nicht** über lokale Room-`Long`-IDs, `@ORT=<id>` oder lokale Foto-`ortId` identifizieren.
- Kanonische mobile Referenz ist `clientNodeKey` bzw. das bestehende stabile Worksite-Node-Binding.
- Lokale DB-IDs dürfen intern gecacht werden, müssen aber jederzeit über das Binding auf den stabilen Node-Key zurückführbar sein.
- Portal und andere Geräte verwenden dieselbe stabile Node-Identität.

### Gemeinsames strukturiertes BTB-Entry-Modell
Ein bearbeitbarer BTB-Eintrag benötigt fachlich mindestens:
- stabile Eintragsidentität;
- `section` / BTB-Punkt 4–8;
- Firma oder Kennzeichen `Allgemein`;
- stabile Unterbaustellen-/Bauobjektreferenz `clientNodeKey` (nullable, wenn fachlich Hauptbaustelle/keine Unterzuordnung);
- Uhrzeit;
- KM;
- Text/Kurztext;
- stabile Foto-Zuordnungen.

Eine Bearbeitung ändert diese Felder **am selben Eintrag**. Identität und Sync-Historie bleiben erhalten.

### Gemeinsames strukturiertes Foto-Modell
Ein bestehendes BTB-Foto benötigt für die fachliche Zuordnung mindestens:
- stabile Fotoidentität;
- BTB-/Entry-Zuordnung;
- BTB-Punkt 4–8;
- Firma/Allgemein soweit fachlich relevant;
- stabile Unterbaustellenreferenz `clientNodeKey`;
- vorhandene Fotoart/Kurzbeschreibung;
- Aufnahmezeit;
- GPS Lat/Lon und Genauigkeit soweit vorhanden.

Beim Umhängen unverändert zu erhalten:
- Originaldatei auf dem Aufnahmegerät;
- Aufnahmezeit;
- GPS-Daten;
- Fotoidentität.

### Servermutationen – Stand Server-HEAD cd9b18fc
Der benötigte Vertrag ist serverseitig source-seitig implementiert:
1. `PATCH /api/v1/btbs/{btb_id}/entries/{entry_id}` unterstützt `section` zusätzlich zu `sort_order`/`payload`;
2. Abschnittswechsel sind bei gleicher Entry-ID zwischen den fünf BTB-Fachsektionen zulässig;
3. kanonisches Payloadfeld für den stabilen Baustellenknoten ist **`worksiteNodeKey`**, Wert = vorhandener `clientNodeKey` des Worksite-Node-Bindings;
4. `PATCH /api/v1/btbs/{btb_id}/photos/{file_id}` nimmt `base_version` + partielle `photo_metadata` für die BTB-Verknüpfung;
5. Foto-Datei, GPS und Aufnahmezeit bleiben beim fachlichen Umhängen unverändert;
6. beide Mutationen bleiben versioniert/conflict-aware und FINALIZED/read-only geschützt.

Android baut weiterhin **keinen Delete/Create-Ersatzweg**, sondern nutzt die bestehenden Identitäten.

### Sync-Schutz
BTB-EDIT-001 muss den bestätigten BTB-SYNC-002-Vertrag einhalten:
- lokale Änderung zuerst Outbox/Pending;
- kein stilles Snapshot-Overwrite eines Dirty-Stands;
- Conflict/Rebase über bestehende Versionslogik;
- Objektidentität bleibt bei fachlicher Umhängung erhalten.


### Android-konkretes Entry-Modell 26.09.2026
Lokales/synchronisiertes `SectionItem` wird rückwärtskompatibel erweitert um:
- `clientEntryKey: String?` – stabile fachliche Entry-/Slot-Identität;
- `firmaId: String?`;
- `firmaName: String?` – Anzeige/Fallback, nicht Primäridentität;
- `worksiteNodeKey: String?` – kanonischer Node-Key; null = Hauptbaustelle/keine Unterzuordnung;
- bestehend: `uhrzeit`, `text`, `kmVon`, `kmBis`.

Der Abschnitt selbst bleibt durch den enthaltenden `SectionBlock` bestimmt. Beim Verschieben wird **dasselbe SectionItem mit demselben clientEntryKey** aus dem Quellblock in den Zielblock übernommen.

Serverimport:
- `_tb_android_slot` → `clientEntryKey`.
Serverexport:
- `clientEntryKey` hat Vorrang vor indexbasiertem Slot-Fallback.
- `worksiteNodeKey` und `firmaId` gehen als strukturierte Payloadfelder.

Legacy:
- `@ORT=<Long>` darf nur zur Migration/Editor-Abwärtskompatibilität gelesen werden.
- Für neue/gespeicherte strukturierte Daten ist `worksiteNodeKey` die dauerhafte Identität.
