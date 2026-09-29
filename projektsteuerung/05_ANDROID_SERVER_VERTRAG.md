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


### Selektiver BTB-Sync – verbindliche Reihenfolge
Für einen dirty markierten BTB gilt:
1. lokale Änderung zuerst persistiert erfassen und als Pending/Outbox sichern;
2. danach kanonischen Serverstand/Version laden;
3. Pending gegen diese Version hochladen bzw. konfliktbewusst rebasen;
4. Server-Snapshot erst anwenden, wenn keine unbestätigte lokale Mutation für denselben `clientBtbId` mehr existiert.
Ein vorgelagerter Snapshot-Apply vor Outbox-Sicherung ist unzulässig, weil dadurch lokale Eingaben verloren gehen können.


## BTB strukturierte Nachbearbeitung / Umhängen
Für bearbeitbare BTBs gilt verbindlich:
- BTB-Einträge und BTB-Fotos sind strukturierte Objekte mit änderbarer fachlicher Zuordnung.
- Nachträglich änderbar müssen mindestens sein: Abschnitt/Punkt 4–8, Firma, Unterbaustelle/Bauobjekt, Text/Kurztext, Zeit/KM und bei Fotos die fachliche Zuordnung.
- Unterbaustelle/Bauobjekt referenziert nach Möglichkeit eine stabile Baustellenbaum-/Node-ID; sichtbarer Name allein ist kein Identitätsschlüssel.
- Portal und Android zeigen dieselben auswählbaren Baustellenknoten (z. B. Hauptbaustelle, freie Strecke, BÜ) und schreiben dieselbe kanonische Zuordnung.
- Eine Korrektur wird als normale versionierte Mutation synchronisiert; kein Löschen+Neuanlegen als Standardweg.
- Verschieben von Punkt 4 nach 5/6/7/8 oder Wechsel von Firma/Unterbaustelle muss auf allen Clients nach Sync identisch erscheinen.
- FINALIZED bleibt read-only gemäß bestehendem Lifecyclevertrag.
- Der bestätigte SSE-Live-Refresh bleibt unverändert und reagiert lediglich auf die resultierenden Change-Feed-Ereignisse.


### Kanonische Unterbaustellenreferenz für BTB
- Lokale Android-`Long`-Knoten-IDs und Textmarker wie `@ORT=123` sind **keine** geräteübergreifende Identität.
- Für BTB-Einträge und BTB-Fotos ist die vorhandene stabile Baustellenstruktur-Identität (`clientNodeKey` bzw. ein eindeutig darauf abgebildeter kanonischer Server-Schlüssel) zu verwenden.
- Android löst lokale Knoten über sein Worksite-Node-Binding auf diesen stabilen Schlüssel auf.
- Portal liest dieselbe Baustellenbaumstruktur und speichert denselben Schlüssel.
- Anzeigenamen (z. B. „BÜ 76.215“) bleiben Darstellung und dürfen umbenannt werden, ohne die BTB-Zuordnung zu verlieren.
- Entry-Update muss einen Wechsel des BTB-Abschnitts 4–8 unterstützen; dies ist eine versionierte Mutation desselben Eintrags, kein Löschen+Neuanlegen.
- Foto-Metadaten/Fachzuordnung müssen ebenfalls versioniert nachbearbeitbar werden (Abschnitt/Typ, Firma, Unterbaustelle, Text/KM), ohne das Foto selbst neu hochzuladen.


### BTB-EDIT-001 – exakter Serververtrag
Für den gemeinsamen strukturierten BTB-Edit-Pfad gilt ab dieser Umsetzung:

**Eintrag**
- kanonisches Payload-Feld für die Unterbaustelle: `worksiteNodeKey`;
- Wert = vorhandener stabiler `client_node_key` aus `GET /api/v1/worksites/{worksite_id}/nodes`;
- fehlt/leer = Hauptbaustelle;
- `firmaId` bleibt die stabile BTB-interne Firmenzuordnung; fehlt/leer = Allgemein;
- `PATCH /api/v1/btbs/{btb_id}/entries/{entry_id}` darf bei bestehenden Facheinträgen zusätzlich `section` ändern;
- Abschnittswechsel ist nur zwischen `ARBEIT`, `ABNAHME`, `EINWEISUNG_UNTERWEISUNG`, `BESONDERHEIT`, `MANGEL_SCHADEN` zulässig;
- Identität des Eintrags bleibt erhalten, Version wird erhöht; kein Delete/Create.

**Foto**
- das physische Foto und seine Aufnahme-Metadaten bleiben unverändert;
- BTB-spezifische Korrekturen werden linkbezogen versioniert gespeichert, damit ein Foto nicht für andere Verwendungen umgeschrieben wird;
- kanonische BTB-Fotofelder umfassen weiterhin `typ`, `firma`, `firmaId`, `kurztext`, `km`, Objektfelder sowie neu `worksiteNodeKey`;
- `PATCH /api/v1/btbs/{btb_id}/photos/{file_id}` erhält `base_version` der BTB-Fotoverknüpfung und eine partielle `photo_metadata`-Änderung;
- Antwort der BTB-Detail-API liefert die effektiv zusammengeführten Foto-Metadaten plus `link_version`;
- GPS/Aufnahmezeit/Dateiinhalt werden durch fachliches Umhängen nicht verändert.

Server validiert einen gesetzten `worksiteNodeKey` gegen die aktive Baustellenstruktur derselben Baustelle.


## ADMIN-PDF-001 – Firmenlogo/PDF-Vertrag für Admin und Android
Status: **VERTRAGSAUDIT ANGEFORDERT / ANDROID-BESTAND VOR ÄNDERUNG PRÜFEN**

Verbindliche Benutzeranforderung vom 28.09.2026:
- Firmenlogo, Firmenstammdaten und PDF-Ausgabe müssen zwischen Server/Admin und Android fachlich zusammenpassen.
- SERVER darf für Admin-/Portal-PDFs keine zweite, abweichende Firmenlogo-/PDF-Logik erfinden.
- Zentrale Firmenstammdaten bleiben Server-Source-of-Truth; Android lädt/cacht die für seine PDF-Ausgabe benötigten Firmendaten.
- Vor Anpassung von PDF-Branding oder PDF-Layout im Server ist der aktuelle Android-PDF-Code zu prüfen.
- Wenn Android zusätzliche Brandingfelder benötigt, werden diese zuerst hier vertraglich festgelegt; Änderungen an `app/**` bleiben Aufgabe des ANDROID-Projekts.
- Admin erhält eine kleine **PDF-Vorschau / PDF-Viewer-Funktion**, die mit denselben zentralen Firmen-/Brandingdaten arbeitet wie die tatsächliche PDF-Erzeugung.
- Logo-Änderungen müssen in Vorschau und erzeugten PDFs nachvollziehbar aktualisiert werden; keine separate manuelle Logoablage nur für die Vorschau.
- Bestehende BTB-/Abnahme-/sonstige PDF-Fachlogik und bereits geschützte bestätigte Abläufe dürfen durch die Admin-Vorschau nicht stillschweigend verändert werden.

Offen bis zum Quellaudit:
- welche Firmen-/Logo-Felder Android aktuell tatsächlich für PDF verwendet;
- ob Android PDF selbst lokal rendert oder serverseitige PDFs konsumiert;
- ob PDF-Kopf/Fuß/Layout bereits zentral konfigurierbar sind;
- welche serverseitige Vorschau technisch denselben Datenpfad am zuverlässigsten abbildet.


### ADMIN-PDF-001 – Android-Quellaudit 28.09.2026
Status: **VERTRAG BESTÄTIGT / SERVER DARF DARAUF AUFBAUEN**

Geprüfter Android-HEAD: `423f37892915cb3308a4b1ea9e4d196faa872326`.

Ergebnis:
- `CompanyProfileSync` lädt das zentrale Firmenprofil und das zentrale Firmenlogo vom Server und spiegelt beides in den lokalen, offlinefähigen PDF-Bestand.
- `CompanyPdfLogoApi` verwendet bereits den gemeinsamen Endpunkt `/api/v1/company/profile/pdf-logo-layout`.
- Android und Server verwenden bereits dieselben PDF-Logo-Felder:
  - `logo_width_pt`;
  - `offset_x_pt`;
  - `offset_y_pt`;
  - `show_company_name`;
  - `show_company_email`;
  - `show_company_phone`;
  - `contacts_in_image`;
  - versioniertes `layout_version`.
- `BtbPdfHeaderRenderer` ist Android-seitig die gemeinsame reale Firmenkopf-Renderlogik für PDF/Vorschau.
- Android besitzt mit `BtbPdfLayoutVorschau` bereits eine **echte A4-PDF-Vorschau** und mit `BtbPdfViewerActivity` einen **einfachen internen PDF-Viewer** mit Seiten vor/zurück.
- Server 1.0 hat bereits denselben zentralen Firmenprofil-/Logo-/PDF-Layout-Vertrag und rendert den Firmenkopf beim serverseitigen BTB-Abschluss aus denselben Firmendaten.

Folgerung für SERVER:
1. Admin-Firmenbearbeitung nutzt ausschließlich diese bestehenden zentralen Firmendaten/Logo-/Layoutfelder.
2. Admin-PDF-Vorschau soll ein **echtes PDF** aus dem serverseitigen Renderer bzw. einem dedizierten Vorschau-Renderer erzeugen, nicht nur eine abweichende HTML-Mockup-Grafik.
3. Die Vorschau darf im Browser klein eingebettet/angezeigt werden und optional in einer größeren Ansicht geöffnet werden.
4. Logo/Layout-Änderungen sollen nach Speichern dieselben Werte liefern, die Android beim nächsten Firmenprofil-Sync erhält.
5. Android-`app/**` benötigt für diesen Admin-Block aktuell **keine Änderung**.
6. Noch offen bleibt visuelle Pixel-/Layout-Parität der beiden Renderer; fachliche Datenfelder und Layoutparameter sind bereits gemeinsam.

## REF-DATA-001 – Android↔Server globaler Strecken-/Vermessungsdatenbestand
Status: **FACHANFORDERUNG BESTÄTIGT / TECHNISCHER VERTRAG NOCH FESTZULEGEN**

Benutzerentscheidung vom 28.09.2026:
- serverseitig hochgeladene Trassierungs-/Vermessungs-/Festpunktdaten sind künftig nicht grundsätzlich Firmeneigentum, sondern können **globale TB-Helfer-Referenzdaten** sein;
- Upload durch einen berechtigten Nutzer macht diese globalen Daten allen berechtigten Firmen/Nutzern verfügbar;
- Android und Server/Portal sollen denselben zentralen Referenzbestand sehen;
- Darstellung soll fachlich dieselben Quellen/Trassen/Festpunkte wie die vorhandene Android-Streckendatenansicht abbilden;
- Originaldateien bleiben unverändert; bearbeitbare Katalog-/Arbeitsdaten werden getrennt versioniert;
- firmen-/baustellenspezifische Projektdateien bleiben davon getrennt und behalten ihren bestehenden Berechtigungsrahmen.

Vor Umsetzung festzulegen:
- Kennzeichnung `GLOBAL_REFERENCE` vs. `COMPANY/WORKSITE_DATA`;
- Sync-/Downloadvertrag für Android;
- Versionierung und Konfliktschutz bearbeitbarer globaler Katalogdaten;
- Rechte: wer global hochladen, bearbeiten und löschen darf;
- keine Änderung an Android `app/**` durch SERVER; nötige Android-Anpassung wird nur vertraglich beschrieben.

## DEVICE-INFO-001 – Laufende Android-App-Version am Server
Status: **VERTRAGSERGÄNZUNG ERFORDERLICH / SERVER BEREITEN**

Problem:
- `tb.devices.app_version/app_build` werden heute nur bei der Geräteaktivierung geschrieben;
- nach einem normalen App-Update bleibt der alte Aktivierungsstand in der Admin-Anzeige bestehen;
- `last_seen_at` allein reicht nicht zur Aussage über die aktuell installierte App-Version.

Zielvertrag:
- Android meldet bei normalen authentifizierten Serverkontakten die aktuelle `app_version` und `app_build`;
- SERVER aktualisiert diese Werte atomar zusammen mit `last_seen_at`;
- Geräteübersicht darf erst dann wieder `Installierte Version` behaupten;
- bis Android angepasst ist, kennzeichnet SERVER vorhandene Werte als `zuletzt gemeldet`;
- Server-Releasekatalog (`veröffentlicht`) und vom Gerät gemeldete Installation sind zwei getrennte Zustände.

SERVER ändert kein `app/**`. Benötigte Android-Anpassung bleibt beim ANDROID-Projekt.

### ADMIN-PDF-007 – PDF-Renderer-Parität geprüft
- Android `BtbPdfHeaderRenderer` und Server-`CompanyHeaderFlowable` verwenden weiterhin denselben bestehenden Layoutvertrag:
  - `logo_width_pt` 40..250;
  - `offset_x_pt` -120..120;
  - `offset_y_pt` -60..180;
  - Firmenname/E-Mail/Telefon/Kontaktdaten-im-Logo.
- Android reserviert eine Logo-Zone bis etwa 174 pt von oben.
- Server war mit 48 mm Headerhöhe deutlich enger und hat besonders quadratische Logos trotz großer Breite wieder verkleinert.
- SERVER wurde auf eine 62-mm-/ca.-174-pt-Zone angepasst; keine Feld-/API-Vertragsänderung und keine Android-Codeänderung.
- Admin-Draft-Vorschau ist rein serverseitige Bedienhilfe; gespeichert und an Android synchronisiert werden weiterhin ausschließlich die bestehenden zentralen Layoutfelder.
### ADMIN-PDF-011 – Logo-Breite 400 pt / Y-Offset
Status: **VERTRAGSÄNDERUNG DOKUMENTIERT / SERVER-UMSETZUNG / ANDROID-ANPASSUNG SEPARAT**
- Neuer gemeinsamer Zielbereich für `logo_width_pt`: **40..400 pt** statt 40..250 pt.
- `offset_x_pt` bleibt vorerst -120..120.
- `offset_y_pt` bleibt vorerst -60..180, muss aber im Renderer tatsächlich eine sichtbare vertikale Verschiebung bewirken.
- SERVER darf Werte bis 400 speichern und im Admin-/Abschluss-PDF verwenden.
- Aktueller Android-Stand `BtbPdfLogoLayoutSpeicher.normalize()` begrenzt weiterhin auf 250 pt. ANDROID muss deshalb in seinem Projekt den Clamp 40..400 übernehmen, bevor vollständige geräteübergreifende Layoutparität wieder behauptet werden darf.
- SERVER ändert `app/**` nicht.

### ADMIN-PDF-011 – Server-Abnahme
Status: **SERVER BESTÄTIGT / ANDROID-ANPASSUNG OFFEN**
- SERVER/Admin akzeptiert und rendert 40..400 pt praktisch bestätigt.
- X/Y und 400-pt-Preview wurden auf Synology vom Benutzer abgenommen.
- Android-Branch begrenzt weiterhin auf 250 pt; nur dieser Android-Anteil bleibt offen.
- Kein Anlass, den bestätigten SERVER-Renderer zurückzubauen.
### REF-DATA-001 – technischer Sync-/Downloadvertrag Phase 1
Status: **FESTGELEGT**
- Scope:
  - `GLOBAL_REFERENCE`: serverweit, `survey_originals.company_id = NULL`;
  - `COMPANY_WORKSITE_DATA`: bestehender firmen-/baustellenbezogener Bestand, `company_id != NULL`.
- Leserechte:
  - `GLOBAL_REFERENCE` ist für jeden authentifizierten aktiven Nutzer lesbar;
  - `COMPANY_WORKSITE_DATA` nur eigene Firma bzw. System-ADMIN.
- Schreibrechte Phase 1:
  - globale Originale nur System-ADMIN über Admin-Import;
  - globale Originale sind immutable und nicht löschbar;
  - bestehende Worksite-/Firmenimporte und `track.edit` bleiben unverändert;
  - globale `catalog_*`-Mutationsrechte werden in dieser Phase nicht verändert; globales DELETE bleibt verboten.
- Android-Zielvertrag:
  - Manifest `GET /api/v1/reference/survey-originals`;
  - geparster Einzelbestand `GET /api/v1/reference/survey-originals/{id}/parsed`;
  - Rohoriginal nur bei ausdrücklichem Bedarf über `/api/v1/survey-originals/{id}/content`;
  - Cache-/Änderungsidentität: `id` + `sha256` + `parser_version` + `parsed_at`;
  - Android lädt im Normalbetrieb geparste Referenzdaten gezielt nach und **keine Roharchive automatisch**;
  - Worksite-`track_data` bleibt getrennte, versionierte Arbeitskopie und darf globale Original-IDs als Quelle referenzieren.
- SERVER darf zur Umsetzung keine Android-Dateien ändern.
