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

### REF-DATA-001 – gemeinsamer Import-UX-Vertrag: Datei zuerst
Status: **ANFORDERUNG GESICHERT / SCOPE-ENTSCHEIDUNG OFFEN**
- ADMIN und normaler Nutzer sollen nicht vor Dateianalyse Strecke/Gleis/Scope blind vorgeben.
- Gemeinsamer Zielablauf:
  - Datei wählen;
  - Voranalyse/Erkennung;
  - erkannte Metadaten anzeigen;
  - nur fehlende/mehrdeutige Angaben ergänzen;
  - danach Import bestätigen;
  - Ergebnis mit erkannter und tatsächlich gespeicherter Zuordnung anzeigen.
- Parserdaten haben Vorrang vor manueller Doppelerfassung, sofern sie eindeutig/plausibel sind.
- Manuelle Eingaben ergänzen fehlende Daten oder korrigieren bewusst erkannte Werte; Originaldatei bleibt unverändert.
- ZIP/Mehrfachupload wird pro enthaltenem Original bewertet.
- Android soll später denselben fachlichen Ablauf nutzen; konkrete Android-UI bleibt Zuständigkeit ANDROID.
- Vor Implementierung noch bestätigen: Scope-Regel `GLOBAL_REFERENCE` vs. `COMPANY_WORKSITE_DATA` (automatisch/default/manuelle Auswahl nach Analyse).

### REF-DATA-002 – Vertrag grafische Referenzdatenübersicht
Status: **ANFORDERUNG GESICHERT / DETAILVERTRAG NOCH ZU DEFINIEREN**
- SERVER muss Android/Admin dieselben normalisierten Geometriedaten bereitstellen, damit beide dieselbe fachliche Darstellung erzeugen können.
- Zielobjekte der Visualisierung:
  - Linien/Polylinien für TRA/Gleisachsen;
  - Punkte für KF/Fest-/Absteckpunkte;
  - Kilometeranker/-bereiche;
  - Stations-/Bahnhofs-/Gleis-/Weichen-/Objektinformationen soweit aus Referenzdaten verfügbar;
  - Herkunft/Qualität/Status pro Element.
- Rohdateien werden nicht direkt in der UI interpretiert; UI arbeitet auf serverseitig normalisierten/geparsten Daten.
- Visualisierung ist read-only gegenüber Originaldaten.
- API-Details (Bounding-Box/Viewport, Filter, vereinfachte Geometrie/LOD, Objekt-Detail) werden vor Implementierung festgelegt.

### REF-DATA-002 – normalisiertes Preview-Schema Phase A
Status: **SERVER-SCHEMA SOURCE-SEITIG FESTGELEGT / ANDROID-NUTZUNG SPÄTER**
- Parser-/UI-Grenze für die grafische Vorschau:
  - `preview.line[]`: `x`, `y`, optional `station_m` für TRA/Achslinien;
  - `preview.points[]`: `x`, `y`, `label`, `type`, optional `km_meter`, `route`, `track`;
  - `preview.profile[]`: `km_meter`, `height_m` für späteres Höhen-/Gradientenprofil;
  - `preview.bounds`: min/max X/Y;
  - `km_from_m`/`km_to_m` bzw. `station_from_m`/`station_to_m`;
  - `point_types[]` mit Typzählung.
- Preview wird bewusst begrenzt/gesampelt, damit große Originaldateien nicht vollständig in die UI-Antwort kopiert werden.
- Admin rendert dieses Schema aktuell als SVG; Android soll später dasselbe normalisierte Schema verwenden statt Rohdateien selbst anders zu interpretieren.
- ADMIN-Analyse-/Importendpunkte sind kein Android-Endvertrag; der gemeinsame read-only Viewport-/Referenz-API-Pfad folgt im dauerhaften REF-DATA-002-Block.

### REF-DATA-002 – Darstellungsvertrag: Android-Semantik als gemeinsame Referenz
Status: **FESTGELEGT**
- Die bestehende Android-`TrassenGrafikView` und ZIP-Vorschau definieren die fachliche Bedeutung der Darstellung.
- SERVER/Admin soll nicht jede Quelldatei beliebig einfärben, sondern dieselben Objektgruppen verwenden:
  - Strecke/Streckengleis;
  - Bahnhofsgleis;
  - Weiche/Weichenverbindung;
  - GVP/Festpunkte;
  - Absteck-/Weichenpunkte.
- Linien bleiben Linien (`fill:none`); Punktmarker bleiben eigene Symbole.
- Labels werden aus normalisierten Achsmetadaten erzeugt, nicht aus kompletter Dateiliste.
- Anzeigeauswahl/filterbare Objektgruppen sind Bestandteil des gemeinsamen UX-Vertrags.
- SERVER darf dafür keine Android-Datei ändern; die Android-Implementierung dient als Referenz für Server/API/Visualisierung.
### REF-DATA-002 – Zoom/LOD-Vertrag
Status: **KORREKTURBEDARF FESTGELEGT**
- Darstellung trennt künftig strikt:
  1. **Weltkoordinaten**: Achs-/TRA-Geometrie, die mit Zoom skaliert;
  2. **Screen-Space-Symbole**: Punktmarker, Endsymbole, Auswahl-/Klickziele;
  3. **Screen-Space-Labels**: Achs-/Punkttexte mit Zoomstufen und Kollisionsfilter.
- Marker dürfen beim Hineinzoomen nicht proportional mit der Weltgeometrie anwachsen.
- Labels dürfen Geometrie nicht verdecken; tiefes Zoomen priorisiert Linie/Punkt vor Text.
- Detailinformationen eines Vermessungspunktes werden bei Auswahl vollständig gezeigt; permanente Punktlabels bleiben zoom-/dichteabhängig begrenzt.
- SERVER/Admin und Android sollen dieselbe fachliche LOD-Semantik verwenden, auch wenn Renderingtechnik unterschiedlich ist.

### REF-DATA-002 – KM-Statusvertrag GRA/GVP
Status: **FESTGELEGT**
- GRÜN/AUTOMATISCH = belastbare automatische KM-Kalibrierung aus expliziter KM-GRA **oder** robuster GVP/Festpunkt-Kalibrierung.
- GVP/Festpunkte sind eine eigenständige KM-Quelle; fehlende GRA allein darf eine Achse nicht rot machen.
- GVP-Automatik folgt Android: gleiche Strecke, >=4 GVP mit KM, >=4 Achssamples, max. 120 m Abstand, Medianoffset, ±80 m Ausreißerfilter, >=4 robuste Treffer.
- GRAU/UNPLAUSIBEL = KM/GVP vorhanden, aber automatische Kalibrierung widersprüchlich.
- ROT/FEHLT = Geometrie vorhanden, aber keine belastbare automatische/manuelle KM.
- ORANGE/MANUELL bleibt für bewusst manuell kalibrierte/geänderte KM reserviert.
- SERVER und Android müssen bei gleicher Datenlage dieselbe fachliche Farbbedeutung verwenden.

### REF-DATA-002 – Achseninteraktion Vertrag
Status: **FESTGELEGT**
- Achse und Vermessungspunkt sind beide interaktive Grafikobjekte.
- Hover liefert nur Kurzinfo; Klick persistiert vollständige Detaildaten in `Auswahl`.
- Sichtbare Geometrie und Trefferzone sind getrennt: dünne Fachlinie, breitere unsichtbare Bedienzone.
- Achsendetail enthält mindestens Objektname, Strecke/Gleis, Achstyp, KM-Status, KM-Quelle, KM-Bereich, GVP-Diagnose/Grund und Quelldatei soweit vorhanden.

### REF-DATA-003 – Verfügbarkeitsvertrag Android ↔ Server
Status: **KONZEPT OFFEN**
- Android soll künftig nicht nach Rohdateinamen entscheiden, sondern über einen serverseitigen normalisierten Datenkatalog.
- Katalog-Schlüssel sollen mindestens Strecke + Betriebsstelle/Bahnhof + optional Gleis/Achse unterstützen.
- Android benötigt read-only Verfügbarkeitsinformationen: vorhanden/teilweise/fehlt, Datenarten, KM-Qualität, Version/Aktualität sowie stabile IDs für spätere Detail-/Downloadaufrufe.
- Nutzerbeiträge gelangen nach serverseitiger Analyse/Klassifikation direkt versioniert in den gemeinsam sichtbaren Fachbestand; eine normale Admin-Freigabeschlange ist ausdrücklich nicht vorgesehen.
- Bestehende Android-Vermessungslogik bleibt fachliche Referenz für Achsen-/KM-/GVP-Status; Android-Code wird im SERVER-Projekt nicht geändert.

#### REF-DATA-003 – Katalogverbund und manuelle Ergänzungen
- Verfügbarkeits- und Grafikdaten werden nach stabilen fachlichen Schlüsseln verknüpft: Strecke, Betriebsstelle, Gleis/Achse und KM-/Raumabdeckung.
- Mehrere Quellen derselben Strecke werden nicht als konkurrierende Komplettpakete behandelt, sondern können unterschiedliche KM-Teilbereiche beitragen.
- Android darf fehlende Vermessungsgeometrie als **manuelle GPS-Ergänzung** erzeugen und zum Server übertragen; dafür ist ein eigener Ursprung/Status gegenüber Originaldaten erforderlich.
- Solche Ergänzungen werden nach serverseitiger Plausibilisierung direkt als gemeinsamer Ergänzungsbestand für andere Nutzer verfügbar; Herkunft und Version bleiben sichtbar/nachvollziehbar.
- Geschützte Originale bleiben unverändert. Neue Originaldaten und vorhandene manuelle Ergänzungen werden versioniert nebeneinander geführt, bis fachlich entschieden ist, welche Geometrie für welchen Bereich bevorzugt wird.
- Eine Betriebsstelle/Bahnhofsdarstellung darf dynamisch wachsen, wenn neue zuordenbare Gleise/Achsen/Abschnitte hinzukommen.
- Die spätere Android-Suche soll mindestens über Streckennummer und Betriebsstelle die vorhandenen, teilweisen und manuell ergänzten Abdeckungen erkennen können.

#### REF-DATA-003 – Merge-/Prioritätsvertrag für neue Originaldaten
Status: **FESTGELEGT**
- Der Server bildet keinen monolithischen „Pasing-Datensatz“, der bei jedem Upload komplett ersetzt wird, sondern einen achsen-/abschnittsweisen Verbund.
- Neue Originaldaten werden automatisch gegen vorhandene Geometrie geprüft. X/Y-Nähe/Anschluss, Strecke/Betriebsstelle, Gleis-/Achsenkennung und KM-/Stationsbereich bestimmen, ob dieselbe Achse erweitert/aktualisiert oder eine neue Achse ergänzt wird.
- Neuere passende Originalsegmente dürfen vorhandene ältere Originalsegmente derselben fachlichen Achse bevorzugen; alte Quellen bleiben historisch erhalten.
- Manuelle GPS-Achsen bleiben erhalten, solange ein neuer Originalbestand keine belastbare Entsprechung für genau diese Achse liefert.
- Beispiel: manuelles `Gl 28` in Pasing bleibt bestehen, wenn ein neuer aktueller Pasing-Originalbestand weiterhin kein passendes Gl 28 enthält.
- Passt eine neue TRA geometrisch an eine vorhandene TRA-Achse, darf der Server die sichtbare Achse abschnittsweise verlängern/ergänzen. Die zugrunde liegenden Originalquellen bleiben getrennt.
- Bei widersprüchlicher Überlappung erfolgt keine destruktive automatische Überschreibung; beide Varianten bleiben versioniert und als Konflikt prüfbar.
- Android darf diese zusammengesetzte Sicht laden; Originalquelle und manuelle Herkunft müssen maschinenlesbar unterscheidbar bleiben.

#### REF-DATA-003 – Konfliktstatus Server ↔ Android
Status: **FESTGELEGT**
- Android-Uploads erhalten einen serverseitigen Zuordnungsstatus, mindestens sinngemäß `MERGED`, `ADDED` oder `REVIEW_REQUIRED`.
- `REVIEW_REQUIRED` bedeutet: Original/Beitrag ist gespeichert und nachvollziehbar, aber der Server hat die neue Geometrie wegen uneindeutiger Zuordnung nicht automatisch als bevorzugte gemeinsame Geometrie aktiviert.
- Android muss in diesem Fall keine Adminentscheidung durchführen; es zeigt dem Nutzer nur den Prüfstatus an.
- Bestehende aktive Geometrie bleibt für andere Geräte nutzbar, bis der Konflikt entschieden ist.
- Eine spätere Adminentscheidung erzeugt eine neue Katalog-/Geometrieversion und wird über normalen Server-Change-Feed/Verfügbarkeitsindex an Geräte verteilt.
- Konfliktbenachrichtigung per E-Mail ist serverintern; keine E-Mail-Adresse/SMTP-Geheimnisse werden an Android übertragen.

#### REF-DATA-003 – Erforderliche Android-Sync-Erweiterung
Status: **ANDROID-ANPASSUNG ERFORDERLICH / SERVER dokumentiert**
- Der bisherige Upload-Gate `memberRole == LEAD` für Streckendaten passt nicht mehr zum Zielvertrag.
- Künftig dürfen alle **berechtigten** Nutzer gemeinsame Katalog-/Geometrieänderungen senden; Berechtigung muss serverseitig geprüft werden und darf nicht nur aus einer Clientrolle abgeleitet werden.
- `TrassenManuellerAbschnittEntity` benötigt einen expliziten synchronisierbaren Datentyp mit stabiler ID, Version, Ursprung und mindestens Achsreferenz, Strecke/Gleis/Richtung, station_start/-ende, KM-Anker/-Richtung, GPS-Messmetadaten, Bearbeitungsstatus und Zeitstempeln.
- Ein manuell auf vorhandener Roh-TRA angelegtes Gleis/Teilstück muss serverseitig als eigene fachliche Ebene gespeichert werden und darf die Roh-TRA nicht verändern.
- Konflikte werden serverseitig mit `REVIEW_REQUIRED` beantwortet; Android zeigt den Status, entscheidet ihn aber nicht.
- Der jetzige Android-Abschnittseditor ist **kein freier GPS-TRA-Recorder**. Wenn künftig eine Achse ohne vorhandene Roh-TRA vollständig per GPS aufgenommen werden soll, braucht Android dafür einen eigenen Geometrie-Aufnahmepfad und einen eigenen Serverdatentyp/Vertrag.
- SERVER stellt dafür den API-/Versions-/Konfliktvertrag bereit; `app/**` bleibt Aufgabe des ANDROID-Projekts.


#### REF-DATA-003 – finaler Server-API-Vertrag für Portal/Android
Status: **SERVER-QUELLVERTRAG IMPLEMENTIERT / RUNTIME-NACHWEIS OFFEN**
Dokument: `docs/TB-SERVER_1_0_SURVEY_CATALOG.md`.

Read:
- `GET /api/v1/survey-catalog/search?q=...`
- `GET /api/v1/survey-catalog/availability?route_number=...&station_code=...`
- `GET /api/v1/survey-catalog/geometry`
- `GET /api/v1/survey-catalog/changes?after=...`

Nutzerbeitrag/Änderung:
- `POST /api/v1/survey-catalog/manual-segments`
- `PATCH /api/v1/survey-catalog/manual-segments/{id}`
- `DELETE /api/v1/survey-catalog/manual-segments/{id}` = Deaktivieren, keine physische Löschung
- `PATCH /api/v1/survey-catalog/axes/{id}`

Admin nur für echte Prüffälle:
- `GET /api/v1/survey-catalog/conflicts`
- `POST /api/v1/survey-catalog/conflicts/{id}/resolve`
- `POST /api/v1/survey-catalog/reindex`

Originalupload:
- `POST /api/v1/survey-originals/from-file` unterstützt `catalog_scope`, `station_code`, `station_name`, `source_date`.
- `catalog_scope=GLOBAL` teilt die abgeleitete Geometrie; company-private Rohdatei bleibt company-private.
- Aktive Baustellenmitgliedschaft genügt für Vermessungsbeitrag/Reparse.

Statuswerte:
- `ADDED`, `MERGED`, `REVIEW_REQUIRED`, `DEACTIVATED`.
- KM: `AUTOMATISCH/GRUEN`, `MANUELL/ORANGE`, `UNPLAUSIBEL/GRAU`, `FEHLT/ROT`.

Android bleibt danach anzupassen:
- heutiges `memberRole == LEAD`-Upload-Gate passt noch nicht;
- `TrassenManuellerAbschnittEntity` wird heute noch nicht als eigener Datentyp synchronisiert;
- Android soll später diesen Serververtrag konsumieren; `app/**` wurde im SERVER-Block nicht geändert.

#### REF-DATA-003 – Datenschutz + Teilsegment-Priorisierung
Status: **VERTRAG NACHGESCHÄRFT 29.09.2026**
- Server→Android/Portal liefert bei globalen Beiträgen für normale Nutzer keine fremden Bearbeiter-/Firmenidentitäten; Herkunftstyp, Version, Qualitätsstatus und neutrale Änderungsinformation bleiben sichtbar.
- Admin darf vollständige Audit-/Bearbeiterinformationen abrufen.
- Ein neuer Originalabschnitt darf ein vorhandenes manuelles/älteres Segment nicht allein wegen hoher Teilüberdeckung komplett deaktivieren.
- Solange ein alter/manueller Rest räumlich nicht durch die neue Quelle abgedeckt ist, bleibt dieser Rest fachlich erhalten. Vollständiges automatisches Supersede ist nur bei nachgewiesener Vollabdeckung desselben Abschnitts zulässig.
- Android/Portal konsumieren die zusammengesetzte aktive Sicht und müssen keine Rohdateien physisch zusammenführen.

#### REF-DATA-003 – Upload-Fallback und Change-Cursor
Status: **SERVERVERTRAG NACHGESCHÄRFT**
- Beim mobilen Originalupload sind Strecke/Betriebsstelle optional übertragbar; fehlen sie, übernimmt der Server soweit vorhanden die Werte der zugehörigen Baustelle.
- Bytegleiche globale Originale werden als bereits verfügbar gemeldet; ein Nutzer erfährt dabei nichts über private Originale anderer Firmen.
- `GET /api/v1/survey-catalog/changes` liefert zusätzlich `next_after`. Android muss diesen Cursor verwenden, auch wenn `changes=[]`, damit nicht sichtbare Firmenänderungen übersprungen werden können.
- `latest` bleibt Diagnose-/Standinformation; der nächste Request basiert auf `next_after`, nicht auf der letzten sichtbaren Entity-ID.
- `source_date` ist das fachliche Bestandsdatum; Upload-/Importzeit bleibt davon getrennt.

#### REF-DATA-003 – Capability-Erkennung
- Android prüft vor Nutzung des neuen Vertrags mindestens survey_reference_catalog_v1, survey_reference_contributions_v1 und survey_reference_change_feed_v1.
- Fehlt eine Capability, darf der Client den betreffenden neuen Pfad nicht auf Verdacht verwenden; bestehende ältere Streckendatenpfade bleiben davon getrennt.

#### REF-DATA-003 – schwache Identität, starke Geometrie
- Ein Upload muss nicht zwingend eine Gleisnummer/Richtung liefern, wenn diese in der Quelle nicht belastbar enthalten ist.
- Server darf bei gleicher/kompatibler Strecke oder Betriebsstelle und räumlich engem X/Y-Kandidaten die Geometrie zur Zuordnung verwenden.
- Eindeutiger Match -> MERGED/ADDED; mehrere plausible Matches -> REVIEW_REQUIRED.

#### REF-DATA-003 – Betriebsstellenzuordnung Import
- Android- und Admin-Import sollen denselben fachlichen Grundsatz verwenden: Bahnhof/Betriebsstelle wird als stabile Referenz (`station_code` + `station_name`) mitgegeben, wenn der Datensatz zu einer Betriebsstelle gehört.
- Freie Streckenabschnitte dürfen ohne Betriebsstelle bleiben.
- Mehrere Gruppen in einem Upload dürfen unterschiedliche Betriebsstellen haben; ein Upload-/ZIP-Default ist nur Komfort und darf gruppenweise überschrieben werden.
- Server speichert die Zuordnung im Katalog; Rohoriginal bleibt unverändert.

#### REF-DATA-003 – Ändern/Löschen über Android
- Android darf globale Referenzoriginale nicht physisch überschreiben.
- Eigene/manuelle Beiträge werden versioniert geändert oder deaktiviert.
- Wird ein falscher Beitrag deaktiviert, verschwindet er aus der aktiven zusammengesetzten Sicht, bleibt aber in Historie/Audit nachvollziehbar.
- Neuere Originaldaten ergänzen/ersetzen nur belegte Bereiche nach den Server-Merge-Regeln.
- Physisches Löschen von globalen Originalen bleibt Admin-/Serveraufgabe.

#### REF-DATA-003 – Suchidentität Bahnhof/Gleis/Strecke + WVB
Status: **AUDIT OFFEN**
- Android-Suche muss für Betriebsstellenbestand prüfen, ob `station_code + track_key` als eindeutige Suchidentität genügt und `route_number` nur zusätzlich filtert, statt zwingend zu sein.
- Bei mehreren gleichnamigen Gleisen an derselben Betriebsstelle bzw. mehreren Strecken bleibt eine eindeutige Auflösung notwendig; dann darf Strecke als Disambiguierung verlangt werden.
- WVB/Weichenverbindungen sind als eigene fachliche Objekte zu behandeln; fehlende eigene Streckennummer darf eine Betriebsstellenzuordnung nicht verhindern.
- SERVER-Projekt ändert hierfür kein `app/**`; Android-Quellstand wird nur gelesen/auditiert.

#### REF-DATA-003 – Audit-Ergebnis Android-Suchlogik
- Bestehender Android-`BAHNHOF_GLEIS`-Kontext matcht Bahnhof + Gleis und verlangt keine Streckennummer.
- Bestehender Android-`BAHNHOF_WEICHE`-Kontext matcht Bahnhof + Weiche.
- Die künftige Survey-Katalogintegration soll dieses Verhalten beibehalten:
  - Bahnhof + Gleis/Weiche/WVB ist Primärkontext;
  - Strecke ergänzt nur bei Mehrdeutigkeit oder freier Strecke.
- Android besitzt bereits Multi-Hit-Unterstützung für überlagerte Roh-TRA in der Grafik. SERVER/Portal soll dieses Bedienprinzip übernehmen; keine Android-Änderung dafür im SERVER-Projekt.

#### REF-DATA-003 – Weiche/WVB-Objektidentität v1
Status: **SERVERVERTRAG SOURCE FEST**
- Für Weichen/WVB wird ein separates `object_key` verwendet:
  - `W:<Nummer>`;
  - `WVB:<WeicheA>-<WeicheB>` in kanonischer Reihenfolge.
- Bahnhof + Gleis bleibt für Gleise Primäridentität; Bahnhof + object_key ist Primäridentität für Weiche/WVB.
- `route_number` ist bei Bahnhofsobjekten Zusatz-/Disambiguierungsinformation und darf für W/WVB fehlen.
- Unterschiedliche bekannte object_keys dürfen nicht wegen bloßer räumlicher Nähe als dieselbe Achse behandelt werden.
- Conflict-Detail ist Admin-/Portaldiagnose; Android erhält dadurch keine neue Pflicht in diesem SERVER-Block.
- Android-`app/**` bleibt unverändert; spätere Android-Katalogintegration soll object_key übernehmen.

#### REF-DATA-003 – Admin-Zentralimport vs. Firmenbeitrag 30.09.2026
- Die SERVER-Admin-Seite `Trassierungsdaten` ist der zentrale Importweg für den **gemeinsamen Referenzbestand** und sendet dort immer `scope=GLOBAL_REFERENCE`.
- Eine Firma/Baustelle wird auf dieser Admin-Seite nicht als Speicherziel gewählt.
- Firmen-/Baustellenbeiträge bleiben fachlich möglich, laufen aber über getrennte Contribution-/Android-/spätere Importpfade und ändern diese Admin-UI-Regel nicht.
- Bahnhof/Betriebsstelle, Strecke, Gleis und object_key bleiben fachliche Zuordnungen innerhalb des globalen Referenzbestands und sind keine Speicherbesitzer-Zuordnung.

#### REF-DATA-003 – Pasing ohne belastbare Kilometrierung: Handy setzt Start/Ende 30.09.2026
Status: **FACHVERTRAG FESTGELEGT**
- Für den aktuellen Pasing/MPB-Bestand liegt **keine belastbare vollständige Kilometrierung** vor.
- Deshalb ist `km_quality=FEHLT` für betroffene Achsen **kein Fehler**, sondern der korrekte Ausgangszustand.
- Der Benutzer setzt in Android auf der vorhandenen TRA-Geometrie manuell:
  - Startpunkt des gültigen Abschnitts;
  - Endpunkt des gültigen Abschnitts;
  - zugehörige KM-Werte/Anker.
- Daraus entsteht ein versionierter manueller KM-Abschnitt; dieser muss serverweit synchronisierbar sein und darf die unveränderte Roh-TRA nicht überschreiben.
- Farblogik bleibt verbindlich: automatisch belastbar = GRÜN, manuell festgelegt = ORANGE, TRA ohne belastbare KM = ROT.
- SERVER darf fehlende Pasing-KM nicht künstlich aus GVP/GRA erzwingen, wenn keine belastbare Zuordnung vorliegt.
- Android-Umsetzung bleibt im ANDROID-Projekt; SERVER hält API/Sync/Versionierung für manuelle Abschnitte bereit bzw. dokumentiert den Bedarf.


## REF-DATA-007 – Manueller KM-Abschnitt Android ↔ Server 02.10.2026
Status: **VERTRAG FEST / ANDROID-UMSETZUNG SPÄTER / SERVER-SUMMARY DARF DARAUF AUFBAUEN**

### Kanonischer Zielpfad
- Fertige manuelle KM-Ergänzungen auf vorhandener TRA-Geometrie werden **nicht** als zweiter `track_data`-Sonderdatensatz erfunden.
- Kanonischer Serverpfad ist der vorhandene Survey-Endpunkt `POST /api/v1/survey-catalog/manual-segments` mit `source_kind=MANUAL_TRA_SECTION`.
- Das unveränderte TRA-Original bleibt geschützt. Der manuelle Abschnitt ist eine getrennte, versionierte Katalogergänzung.
- Android identifiziert das zugrunde liegende TRA über `source_signature`. Die vorhandene Android-`dateiSignatur` ist der SHA-256-Präfix mit 24 Hex-Zeichen und ist mit der serverseitigen Präfixauflösung kompatibel.

### Übertragungsinhalt eines fertigen Abschnitts
- stabile mobile Abschnittsidentität in `metadata.mobile_section_id`;
- `route_number`, `station_code`/`station_name` soweit bekannt, `track_key`, `axis_type`, `display_name`, Richtung;
- `station_from_m` und `station_to_m` als Grenzen auf der TRA-Achse;
- daraus belastbar bestimmte `km_from_m` und `km_to_m`;
- `source_signature` der TRA-Datei;
- Metadaten zur Herkunft: mindestens `origin=ANDROID`, `km_source=MANUELL_ABSCHNITT` oder `MANUELL_2PUNKT`, KM-Richtung sowie – soweit vorhanden – Ankerstation, Anker-KM, GPS Lat/Lon, GPS-Genauigkeit, Achsabstand, Erstell-/Änderungszeit und Notiz.

### OFFEN vs. FERTIG
- Ein lokaler Android-Entwurf mit noch fehlender Abschnittsgrenze oder noch nicht belastbarer KM-Berechnung bleibt zunächst lokaler `OFFEN`-Stand und darf nicht als scheinbar vollständige globale Kilometerierung veröffentlicht werden.
- Erst ein fachlich `FERTIG` bestimmbarer Abschnitt mit Grenzen und belastbaren KM-Endwerten wird als aktiver manueller Survey-Abschnitt zum Server synchronisiert.
- Ein später gewünschter geräteübergreifender Draft-Sync wäre ein eigener Vertrag; er wird nicht durch missbräuchliche Vollachsen-Segmente simuliert.

### Anzeige / Kennzahlen
- `MANUAL_TRA_SECTION` mit gültigen KM-Endwerten zählt in Server-/Admin-Kennzahlen als **MANUELL / HANDY / ORANGE**.
- Automatische Original-/GRA-/GVP-Kilometrierung bleibt **AUTOMATISCH / GRÜN**.
- Abschnitte ohne belastbare KM bleiben **FEHLT / ROT**; unplausible Fälle bleiben getrennt grau/Prüfung nötig.
- Gesamt-km beziehen sich auf geometrische/Stationslänge der aktiven Achssegmente, nicht auf `max(km)-min(km)` über mehrere parallele Gleise.

### Zuständigkeit
- SERVER stellt/erhält den kanonischen `manual-segments`-Vertrag, Summary-/Detailanzeige und Historie.
- ANDROID muss später seine neuere `TrassenManuellerAbschnittEntity`-Logik auf diesen Vertrag abbilden. SERVER ändert dafür kein `app/**`.
- Bestehender AXIS-Sync mit Sample-`km_meter`/`km_quelle` bleibt für Kompatibilität bestehen, ist aber nicht die kanonische Identität eines neuen manuellen Abschnitts.


## REF-DATA-008 – Android-Transportaudit zur Weichenklärung (02.10.2026)
- Read-only geprüft: Android-HEAD 423f37892915cb3308a4b1ea9e4d196faa872326, PR #11 offen, nicht Draft; SERVER-Ausgang d1399a4e5bdd143b5373e25253692407e5eb044e, Draft-PR #6.
- WorksiteTrackDataSync: LEAD baut AXIS-/STRECKENPUNKTE-Datensätze aus lokalen Fachdaten, Helfer lesen baustellenbezogenen Server-Snapshot. AXIS enthält name, achs_typ, quelle_datei, datei_signatur, station_von/bis, Samples mit station/km_meter/km_quelle/x_db_ref/y_db_ref/lat/lon/hoehe_m/richtung_deg. Kein eigener object_key in diesem AXIS-Serializer und kein Stamm-/Abzweig-/Weichengrenzenmodell in TrassenAchseEntity.
- WorksiteTrackDataApi: GET/POST /api/v1/worksites/{id}/track-data und PATCH/DELETE /api/v1/track-data/{id}, mit Versions-/Mutation-ID-Abgleich. Dieser Pfad überträgt abgeleitete Daten, keine Roh-TRA.
- Der neue globale Survey-Katalog mit object_key/axis_id/Segmentversionen ist ein gesonderter Vertrag und durch die neue Admin-Karte nicht automatisch als Android-Integration fertig. Globaler Manifest-/Parsed-/Such-/Geometry-/Changes-Vertrag bereits serverseitig vorhanden; vollständige mobile Kataloganbindung bleibt ANDROID-Aufgabe. Keine neue Geräte-E2E-Bestätigung.
- Bestehender manueller Abschnittsvertrag MANUAL_TRA_SECTION bleibt unverändert. Für eine spätere Weichentopologie wären explizite, belegte Stamm-/Abzweigzuordnung und Grenzen erforderlich; kein neuer API-Vertrag oder Migration in diesem Block festgelegt.


## REF-DATA-009 – Admin-Achsverwaltung, additiver Vertrag (02.10.2026)
- GET /api/v1/survey-catalog/management/axes?archived=true&q=...&limit=...&offset=...: nur Admin, paginierte Liste globaler Achsen für Archiv/Wiederherstellung; keine Geometrie laden.
- PATCH /api/v1/survey-catalog/management/axes/{id}: nur Admin + Admin-CSRF, bestehende AxisAdminUpdate-Felder/base_version; bewusstes Ändern der Katalogzuordnung, Rohoriginal bleibt gleich. Fachliche identity_key muss konsistent aktualisiert werden; Kollisionen als 409 statt Merge. Versionskonflikt 409, keine stille Überschreibung.
- POST /api/v1/survey-catalog/management/axes/{id}/archive und /restore: nur Admin + Admin-CSRF, base_version; nur Achsstatus ändern, Originale/Assets/Abschnitte nicht löschen/deaktivieren. Archivierte Achsen bleiben aus aktiver Karte ausgeblendet. Vorheriger REVIEW-Status/offene Konflikte bleiben beim Wiederherstellen berücksichtigt. Audit/History STATUS + Version.
- Admin-Detail-GET coverage/{id}/details darf auch archivierte globale Achsen für lesenden Nachweis liefern. Aktive Coverage/Geometry-Verträge bleiben unverändert.
- Kein Android-Code und kein neuer mobiler Pflichtvertrag. Künftige Katalogclients müssen AXIS-Statusänderungen über bestehenden Changes-Vertrag beachten. Keine automatische Löschung zugeordneter Bahnhof-/Streckenobjekte.


## REF-DATA-010 – Weichengrenzen aus Originalen oder mobiler GPS-Erfassung (02.10.2026)
- Nutzerauftrag: Originalpaket 2026031_Gessertshausen.zip prüfen; falls Weichenzuordnung nicht plausibel belegbar, am Handy WA, WE und WEZ per GPS setzen können. SERVER/Portal zuerst, ANDROID danach; keine Android-Implementierung in diesem Block.
- Fachliches Ziel: eine Weichenidentität mit gemeinsamem WA, getrenntem WE im Stammgleis und WEZ im Zweiggleis; keine Gleichsetzung der beiden Weglängen. Bestätigte vorhandene Achsen können durch diese Punkte begrenzt/zugeordnet werden. Drei GPS-Punkte allein bestimmen keinen exakten Bogenverlauf; ohne belastbare Achse nur manuelle Näherung/weitere Aufnahme, keine behauptete exakte Trassierung.
- Mobile Ergänzung getrennt/versioniert, Originale unverändert. Punktrollen, Koordinaten, GPS-Genauigkeit, Aufnahmezeit, Herkunft und Weichen-/Achszuordnung erhalten; bei unklarer Zuordnung oder unzureichender Genauigkeit Prüfbedarf anzeigen statt automatisch auf ein Nachbargleis zuzuordnen. Manuelle Herkunft von KM-Qualität getrennt führen; GPS-Punkte erzeugen allein keine belastbare Streckenkilometrierung. API-/Schema-Ausarbeitung und Implementierung offen; bestehender MANUAL_TRA_SECTION-Vertrag nicht still umdeuten.
- Read-only Originalbefund: SW_107W_GGP_x1_ER0.TRA 624 Bytes, SHA256 ad36da35793bc6819f76ba4b78ff0768ca3e4eb649c621c0b830e855f96d4bcd entspricht Portalnachweis. Sechs Elemente/sieben Knoten, Stationen 0, 6.7, 16.7, 26.7, 30, 30.64, 55.840975 m. Elementlängen summieren sich auf 55.840975 m; Koordinatensehnen passen numerisch zu Stationsdifferenzen (6.699979/9.999932/9.999932/3.299977/0.640000/25.190609 m). Stationsspanne damit intern nachvollziehbar, keine automatische Bestätigung baulicher Weichengrenzen.
- Paket enthält Feldriss, drei Trassierungsplanblätter, TRA/GRA, Festpunkte und Koordinaten. Textsuche/Plan-Sichtung ergab bislang keine eindeutige vollständige Zuordnung WA/WE/WEZ zu 107W; nicht als bewiesenes Fehlen aller Hinweise im Plan ausgeben. Roh-TRA ist eine einzelne Achskette, keine explizite Drei-Punkt-/Zwei-Ast-Struktur. Keine Originaldatei verändert oder ins öffentliche Repository kopiert.
- Prüfstatus: lokale Binär-/Koordinatenprüfung und Plan-Sichtung; keine vollständige vermessungstechnische Abnahme, kein NAS-Import, keine Runtime-/Geräte-E2E. Bearbeiten bleibt benutzerbestätigt, Archiv/Wiederherstellung und manueller KM-Overlap-Fix separat offen.


## REF-DATA-010/011 – Präzisierung und nächster Serverblock (02.10.2026)
- Nutzer bestätigt Neuanlageablauf am Beispiel MPB/Weiche 239: zuerst WA, WE und WEZ auf vorhandenen Achsen setzen, danach gemeinsam als neue Weiche benennen/zuordnen. Keine vorher vorhandene Weichenidentität voraussetzen. WA→WE Stammgleis, WA→WEZ Zweiggleis; bei Mehrfachtreffern bewusste Achsauswahl. Ohne eindeutige Achsen bleiben Punkte als manuelle Erfassung mit offener Zuordnung erhalten. Server/Portal zuerst, Android später.
- Nächster freigegebener Serverblock: nachgewiesenen OVERLAP→SUPERSEDED-Fehler für MANUAL_TRA_SECTION korrigieren und Regression prüfen, bevor der manuelle KM-Editor freigeschaltet wird. Manuelle TRA-Ergänzung ist eine zusätzliche KM-Zuordnung auf unveränderter Originalgeometrie, kein zu verdrängender GPS-Geometrieersatz. Auch späterer Originalimport darf diese Ergänzung nicht allein wegen geometrischer Überdeckung deaktivieren. MANUAL_GPS-Priorität bleibt erhalten.
- Vor UI-Freigabe auch Summary-/Überlappungszählung und gültige Abschnittsgrenzen prüfen; keine Freigabe allein aufgrund einer Statuskorrektur. Keine Altbestandsreaktivierung auf Verdacht. Vertrags-/Runtime-Status nach Prüfung getrennt dokumentieren.


### REF-DATA-011 – reproduzierte Summary-Gates vor Freigabe
- Ausführung der echten _coverage_summary_data mit Cursor-Doubles: Original 0..1000 m + manuelle Abschnitte 0..100 und 50..150 ergibt falsch 200 m manuell statt 150 m. Ein manueller Abschnitt 0..100 mit Qualität FEHLT ergibt falsch 100 m manuell. Beide Fälle werden im aktuellen Block korrigiert/regressionsgeprüft: stationsbezogene Vereinigungsmenge auf derselben Originalquelle, nur fachlich MANUELL qualifizierte Abschnitte umklassifizieren; nicht auf andere Achsen/Originalstationierungen übertragen.


## REF-DATA-011 – Manueller TRA-KM-Overlay: Quellfix und Regression (02.10.2026)
- Status: QUELLSTAND FERTIG / SYNOLOGY-DEPLOY UND HTTP-/DB-E2E OFFEN. Kein manueller KM-Editor in dieser Änderung freigeschaltet; dessen Eingabe-/Grenzen-/Berechtigungspfad bleibt nächster UI-Block. Neuanlage Weiche über WA/WE/WEZ→Nummer/Bahnhof als Zielablauf gesichert, noch nicht implementiert.
- create_manual_segment: MANUAL_TRA_SECTION bleibt bei bestätigter geometrischer Überlappung ACTIVE. Original und KM-Zuordnung bestehen nebeneinander. index_original_in_tx verdrängt bei Originalimport nur überdeckte MANUAL_GPS-Geometrie, keine MANUAL_TRA_SECTION-KM-Ergänzung. Bestehende GPS- und neuere-Original-Priorität unverändert. Kein Eingriff in alte SUPERSEDED-Datensätze.
- Summary: manuelle Abschnitte auf derselben aktiven Originalquelle werden innerhalb der Originalstationsgrenzen als Intervallvereinigung gezählt. Überlappungen/identische/verschachtelte Abschnitte zählen nicht mehrfach. Nur Qualität MANUELL darf Basislänge umklassifizieren; ohne belastbare Stationsüberlappung keine geschätzte Umklassifizierung. Unzugeordnete Abschnitte behalten ihre tatsächlich berechnete Qualitätsklasse statt pauschal MANUELL zu werden. Keine globale Deduplizierung fremder Originalquellen/Parallelgleise behaupten; widersprüchliche KM-Anker verschiedener Ergänzungen werden dadurch noch nicht fachlich aufgelöst.
- GRA-Kontext zählt MANUAL_TRA_SECTION nicht als zusätzliche Grundgeometrie; aktive KM-Overlays schalten die bestehende Einzeloriginal-GRA-Zuordnung nicht ab.
- Reproduktion vor Fix: Create und späterer Import deaktivieren jeweils TRA-KM-Overlay; Summary 0..100 + 50..150 ergibt 200 statt 150 m, FEHLT-Overlay ergibt 100 m manuell. Nach Fix: 16 Tests in server/checks/122_verify_manual_overlay.py PASS (echte Produktionsfunktionen per AST, Cursor-/Geometrie-Doubles; Status, Replay, Review, Quellen/KM-Erhalt, Original-/GPS-Priorität, Intervallgrenzen/Vereinigung, fehlende KM, Mobile-Zählung, GRA-Kontext). Bestehende 12 Management- und 6 Detailtests PASS; Python-Syntax PASS. Keine Behauptung eines echten HTTP-/PostgreSQL-/Postman-/NAS-/Geräte-E2E; kein neuer visueller UI-Test erforderlich, Template unverändert.
- Runtime-Datei nur server/tbserver/survey_catalog.py. Keine Migration/neue Abhängigkeit/app/**-Änderung. GitHub Desktop→File Station in bestehenden tbserver-Mount übernehmen, API-Container neu starten. Ohne aktive manuelle Ergänzungen müssen bestehende Summary-Zahlen unverändert bleiben. Vollständiger manueller Schreib-/Änderungs-/Lese-Nachtest folgt mit dem Editor; Originale geschützt.


## REF-DATA-012 – Admin-Editor für manuelle KM-Abschnitte (04.10.2026)
- Auftrag: nächsten offenen Block umsetzen. Achsdetail erhält manuellen KM-Editor auf einem ausdrücklich gewählten aktiven ORIGINAL_TRA-Abschnitt. Eingaben: Achsposition Anfang/Ende in Metern, zugehörige Strecken-KM, Notiz; fallende KM zulässig, Achspositionen aufsteigend. Vorschau vor Speichern; Rohoriginale unverändert. Gespeicherte manuelle Abschnitte bearbeitbar/deaktivierbar, Historie bleibt.
- Additiver Admin-Vertrag: POST /api/v1/survey-catalog/management/axes/{axis_id}/manual-km/preview (nur prüfen), POST .../manual-km (anlegen/ändern) und POST .../manual-km/{segment_id}/deactivate. Admin-Session + CSRF. Payload mit Achs-base_version, source_segment_id/source_version, station_from_m/station_to_m, km_from_m/km_to_m, optional segment_id/segment_version für Änderung, note und client_change_id beim Speichern. Keine geometrische Neuzuordnung anhand Nähe; persistiert MANUAL_TRA_SECTION auf ausgewählter Achse/Originalquelle.
- Grenzen: endliche Werte, positive Abschnittslänge innerhalb der Quellstationierung, unterschiedliche KM-Endwerte. Vorschau zeigt Länge und KM-Differenz; auffällige Abweichung wird vor Speicherung sichtbar und erfordert bewusste Bestätigung. Widersprechende aktive manuelle KM-Zuordnungen derselben Originalquelle in gemeinsamer Stationsspanne blockieren (409); konsistente Überlappung bleibt zulässig. Sperren/Versionsprüfung und wiederholbare Mutation über Receipt; bei unsicherem Browser-Schreibausgang keine automatische Wiederholung.
- Geometrie des Abschnitts muss an exakten Stationsgrenzen aus verdichteter Originalachse interpoliert werden; ungültiger/leerer Ausschnitt darf nicht auf Vollachse zurückfallen. Originale bleiben unverändert. Map/Details/Summary nach Erfolg neu laden. Keine Android-Dateien, keine neue Weichenerfassung in diesem Block.


## REF-DATA-012 – Admin-KM-Editor Quellstand fertig (04.10.2026)
- Vertrag vor Entwicklung: 7ef531771c4acfc62cc43282b0cc34c971b83704. GitHub-Ausgang SERVER 4ac9792fd4366c525e665dc9bdb07d9194acd213 / Draft-PR #6; ANDROID 423f37892915cb3308a4b1ea9e4d196faa872326 / offener PR #11 unverändert.
- Achsdetail → „KM manuell …“ öffnet Editor: aktiven ORIGINAL_TRA-Abschnitt auswählen, Achsposition Anfang/Ende in Metern, Strecken-km als Dezimalzahl (Komma/Punkt; keine Tausendertrennzeichen), Notiz; Vorschau vor Speicherung. Vorschau zeigt Verlauf mit A/E, Achslänge/KM-Differenz, steigende/fallende Richtung. KM werden zwischen Ankern linear zugeordnet. Abweichung > max(5 m, 5 % der Achslänge) verlangt ausdrückliche Bestätigung; dies ist eine Eingabeplausibilisierung, kein vermessungstechnischer Nachweis. Eingabeänderung verwirft Vorschau.
- Aktive manuelle TRA-KM-Abschnitte können geändert oder mit ausdrücklicher Bestätigung deaktiviert werden. Originale bleiben erhalten; Historie/Audit fortgeführt. Ursprung ANDROID bleibt beim Admin-Ändern erhalten, last_edited_via=ADMIN kommt hinzu. Originalquelle bei Änderung unveränderlich. Aktive Originalquelle muss weiterhin existieren; archivierte Achsen gesperrt.
- Additive Admin-CSRF-Routen preview/save/deactivate wie vertraglich gesichert. Direkte Bindung an ausgewählte Achse/Originalsegment ohne räumliches Raten. Endliche Werte, positive Stationsspanne innerhalb Quelle, verschiedene KM-Endwerte, Achs-/Quell-/Segmentversionen. Bestehende aktive manuelle Zuordnungen derselben Originalquelle werden an beiden Überlappungsgrenzen verglichen, Abweichung >0,01 m oder unvollständige bestehende KM blockiert mit 409. Keine Konfliktauflösung über fremde Originalstationierungen behaupten.
- Schreibtransaktion mit Achs-/Segmentsperren, Mutation-Receipt inklusive SHA256 des Requests; gleicher Request wiederholbar, anderweitig wiederverwendete ID 409. Keine automatische Schreibwiederholung im Browser. Unklarer Schreibausgang/Fehler fordert Aktualisieren des gespeicherten Stands. Nach Erfolg Karte/Details/Summary neu laden.
- Geometrie: _clip_station_points interpoliert Abschnittsgrenzen auf verdichteter Originalgeometrie; kleine Abschnitte unterhalb des bisherigen Abtastabstands funktionieren. Leerer/ungültiger beidseitig begrenzter Ausschnitt liefert keine Vollachse mehr. Unbegrenztes Laden bleibt unverändert. Die Interpolation ist kein Ersatz der Roh-TRA und keine neue vermessene Geometrie.
- Tests: 14 neue Tests server/checks/123_verify_admin_km_editor.py PASS mit echten Produktionsfunktionen/Pydantic und Speicherdoubles (Vorschau read-only, Grenzen, NaN/Inf, fallende KM, Bestätigung, Versionen, Archivschutz, Replay, Mutation-Reuse, widersprechende/konsistente Überlappung, Update/Deaktivierung/Originalschutz, Herkunft). Bestehende 16 Overlay-, 12 Management-, 6 Detailtests PASS; Node-Check119 erweitert PASS für Komma/Einheiten, Vorschauinvalidierung, Escaping, Admin-CSRF, Doppelklick, Konflikt und Timeout ohne erneutes Schreiben. Python-Syntax PASS.
- Testgrenzen: kein echter FastAPI-/PostgreSQL-/Postman-/Synology-/Geräte-E2E. Postman CLI, lokale FastAPI/Psycopg/httpx fehlen. Playwright verfügbar, aber Chromium fehlt; offizieller Browserdownload HTTP403 durch Netzwerkumgebung blockiert. Daher kein visueller Browser-/Screenshotnachweis. Kein Produktivschreibtest.
- Status: QUELLSTAND FERTIG / DEPLOY UND BENUTZER-NACHTEST OFFEN. Runtime-Dateien server/tbserver/survey_catalog.py + server/templates/v1_admin_survey_overview.html; keine Migration/Dependency-/Android-Änderung. Übernahme GitHub Desktop → File Station in bestehende Mountpfade → API neu starten → Browser hart aktualisieren.
- NAS-Nachtest: Achse → KM manuell → aktive Quelle bewusst auswählen → kurze gültige Stationsspanne und fachlich bekannte KM eingeben → Vorschau prüfen → speichern → orange Abschnittslinie/Details und Kennzahlen prüfen; Änderung und Deaktivierung testen, Original bleibt. Zweiter alter Editor muss Versionskonflikt abweisen; unpassende Grenzen und widersprechende Überlappung dürfen nicht speichern. Bestehende GVP-/Kartenabnahme bleibt geschützt.
- Weiter offen: Synology-Abnahme dieses Editors, Archiv/Wiederherstellung eigener Nachtest, Weichenneuanlage WA/WE/WEZ→Nummer/Bahnhof (Server-/Portalmodell zuerst, Android danach), übergreifende Server-/Portal-E2E.


## REF-DATA-013 – GVP-Zweipunkteditor und sichtbare Achsbeschriftung (04.10.2026)
- Nutzerfreigabe: PC-Kilometrierung über zwei ausgewählte GVP mit eingegebenen Strecken-km, Lot auf ausdrücklich gewählte Originalachse, Berechnen/Vorschau/Speichern. Numerischer Editor bleibt Alternative. Screenshot bestätigt Öffnen des bisherigen Editors, nicht dessen vollständigen Schreib-E2E.
- Additiv: Admin-GET management/axes/{axis_id}/gvp?source_segment_id liefert begrenzte räumliche Kandidaten aus aktiven GLOBAL-GVP/KF-Assets plus Quellgeometrie in DBREF. Räumliche Nähe ist keine automatische fachliche Zuordnung; Quelle/Route/Punktname anzeigen, Benutzer wählt ausdrücklich. GET liest nur.
- Admin-CSRF POST .../manual-km/gvp-calculate löst zwei Referenzen (asset_id, asset_version, point_index, eingegebener km_meter) serverseitig auf und projiziert auf gewählte aktive ORIGINAL_TRA-Geometrie. Keine Endpunktklemmung; außerhalb/mehrdeutige Projektion ablehnen. Quell-/Achs-/Assetversion prüfen. Save/Preview prüfen optionale gvp_anchors erneut; Herkunft/Punktdaten/Lotfuß getrennt im manuellen Overlay speichern, Originale unverändert.
- Zweipunktrechnung zwischen aufsteigend geordneten Lotfußstationen; fallende KM erlaubt. Android-Zweipunktkalibrierung als Referenz: mindestens 20 m Stationsabstand, mindestens 1 m KM-Differenz, Betrag Faktor 0,5..1,5. Neuere Android-Einanker-/KM-Sprunglogik ist nicht Bestandteil dieses Blocks. Keine automatische Extrapolation oder gemeinsame Weichenast-Kilometrierung.
- Kartenfehler: Rechteckmitte durch Anker am tatsächlich sichtbaren Linienverlauf ersetzen; Gleisnamen beim Zoom/Pan mitführen. Kollisionsarme Beschriftung mit sichtbarer Zuordnung; keine pauschale Unterdrückung aller Namen ab 31 Achsen. Randhinweise nur geometrisch belegbare Fortsetzung der benannten Achse, keine behauptete topologische Reihenfolge kommender Weichen.
- SERVER-only, keine neue Migration/Dependency, geschützte automatische GVP-Summary unverändert. Quelltests und NAS-/Browser-/DB-E2E getrennt berichten.


## REF-DATA-013 – GVP-Editor und Achslabels: Quellstand (04.10.2026)
- Vertrag vor Code: d15b6c945212a6bbd4b2a73a6a09a51deb1455f4. Basis SERVER ea809ef1cf4b99ff49cddeeef2e4e6ff99622df4 / Draft-PR #6; ANDROID 423f37892915cb3308a4b1ea9e4d196faa872326 / PR #11 unverändert. STATUS: QUELLSTAND IMPLEMENTIERT UND LOKAL GEPRÜFT; NAS-DEPLOY/VISUELLER BROWSER-/ECHTER HTTP-/POSTGRESQL-E2E OFFEN.
- „KM manuell“ startet bei Neuanlage mit GVP-Auswahl. Originalabschnitt bewusst wählen → GVP und Achse laden → zwei Punkte per Karte oder beschrifteter Liste wählen → importierte KM prüfen/ändern (Komma/Punkt, Anzeige km, API Meter) → Berechnen und prüfen → Vorschau/Speichern. Karte zoombar/verschiebbar; gestrichelte Lotlinien, Lotabstand und Achspositionen sichtbar. „Achspositionen direkt eingeben“ erhält Zahleneditor; vorhandene Abschnitte dort änderbar/deaktivierbar. GVP-/KM-/Quellenwechsel verwirft Berechnung und Speichervorschau. Originalgeometrie bleibt unverändert.
- Additive Admin-Routen GET management/axes/{axis_id}/gvp?source_segment_id und CSRF-POST .../manual-km/gvp-calculate. Kandidaten nur aktive globale GVP/KF/Festpunkt-Assets in Quell-BBOX +100 m; höchstens 100 Assets/2000 Punkte, sichtbarer Begrenzungshinweis. Quelle/Punktname/Strecke angezeigt, keine automatische fachliche Zuordnung oder private Daten. Leere Auswahl fordert GVP-Import oder Zahleneditor. Vorschaugeometrie auf 1500 Punkte begrenzt; Berechnung auf vollständiger verdichteter Originalgeometrie.
- Lot serverseitig ohne Endpunktklemmung auf gewählter Originalachse; maximal 100 m Abstand. Ähnlich nahe unterschiedliche Lotfüße (<0,2 m Distanzunterschied, >1 m Stationsunterschied) abweisen, direkte fachliche Eingabe bleibt Alternative. Zwei verschiedene Punkte erforderlich, Stationen sortiert, Mindestabstand 20 m, KM-Differenz mindestens 1 m, Faktor-Betrag 0,5..1,5; fallende KM möglich. Linear nur zwischen Ankern; bestehende Warnbestätigung >max(5 m,5 %) bleibt. Dies bildet Android-Zweipunktkalibrierung ab, nicht zusätzlich dessen Einanker-/KM-Sprungmodell.
- Preview/Save lösen gvp_anchors erneut auf und prüfen Stations-/KM-Ergebnis, Quell-/Assetversion und aktive Zustände. Asset-Lesesperre beim Auflösen. Overlay-Metadaten speichern Referenz, Originalname/-ID/SHA, ursprünglichen/eingegebenen KM-Wert, Koordinaten, Lotfuß/Abstand; ursprüngliche Quellen unverändert. Direkte spätere Zahlenbearbeitung entfernt die aktuelle GVP-Bindung, vorheriger Stand bleibt in Historie. Vorhandene Rechte, Achsversionen, Overlap-Konflikte, Schreib-Receipt und keine automatische Schreibwiederholung unverändert. Keine Android-/Schema-/Abhängigkeitsänderung.
- Kartenlabels: tatsächliche Linien an Viewport clippen, Anker nahe sichtbarer Mitte, nach Zoom/Pan neu setzen. Kollisionsarme Alternativpositionen mit Verbindungslinie/Achspunkt; ausgewählte Achse und Gleisnamen priorisiert. Kein pauschales Ausblenden aller Labels bei >30 Achsen. Bei extremer Dichte bleiben kollidierende Namen unterdrückt; Trefferliste bleibt verfügbar. Ausgewählte Achse erhält links/rechts Fortsetzungspfeile an belegbaren Randkreuzungen. Keine topologische Aussage „nächste Weiche“ aus geometrischer Nähe erfinden.
- Tests: 10 neue echte Produktionsfunktions-/Pydantic-Tests 124 PASS (Lot, Grenzen/Abstand, gemeinsame Ecke, Mehrdeutigkeit, fallende/vertauschte Anker, Plausibilität, Quellversion/Scope, read-only begrenzte Liste, Preview/Save-Provenienz und Manipulationsschutz, Admin/CSRF). Bestehende 48 Backend-Tests 120–123 PASS. Node119 erweitert PASS: Kurvenanker, Verschieben, >30 Achsen, Randhinweis, GVP-Klickauswahl-Funktion/Listen, Einheiten, CSRF, Vorschau und Invalidierung, Escaping; DOM/Transport-Doubles. Python-Syntax PASS. Kein visueller Screenshotnachweis: Chromium lokal nicht verfügbar, voriger Download durch Umgebung HTTP403 blockiert. Kein echter API-/DB-/NAS-/Geräte-E2E.
- Deploy nur server/tbserver/survey_catalog.py + server/templates/v1_admin_survey_overview.html über GitHub Desktop→File Station in vorhandene Mounts; API neu starten, Browser hart aktualisieren. Keine Migration. NAS-Nachtest: Gl1/Gl4 beim Zoom/Pan mit Linie vergleichen; GVP-Quelle öffnen, zwei fachlich bekannte Punkte wählen, Lotfüße/KM prüfen, Vorschau und Speichern, orange Abschnitt/Details/Summary, dann Zahlenänderung/Deaktivierung. Alte Quellen/Versionskonflikte dürfen nicht still speichern.
- Weiter offen: dieser NAS-/Browser-/Schreibnachtest, Archiv/Wiederherstellen eigener E2E, Weichenneuanlage WA/WE/WEZ→Nummer/Bahnhof, Android-Anbindung und übergreifende Server-/Portal-E2E. Bestehende Benutzerabnahme schnelle Karte/Achsbearbeitung geschützt.


## REF-DATA-015 – Gesamtfreigabe Server/Portal (04.10.2026)
- Nutzer: „okay machen wir so und den rest auch gleich mitmachen … lege los“. Umfang: 5302-0/Stationssprünge reparieren; Importprüfung verständlich ergänzen; amtliche OpenData-Luftbilder BY/SN/BW optional zuschalten; originale Achsen nicht skalieren; bestätigte Gleisabschnitte und Weichen WA/WE/WEZ per Kartenklick/Stationswert erfassen; gezieltes Testimport-Zurücksetzen mit Vorschau. Server/Portal zuerst; Android-Vertrag sichern, kein app/**-Umbau. Keine Ausführung einer Löschung des NAS-Bestands durch diesen Auftrag.
- Abschnittszuordnung getrennt von Original und offiziellen Bahnhofsgrenzen; Quelle/Bearbeiter/Version. Punkte beziehen sich auf explizite aktive Originalsegmente und geometrische Stationierung. Weiche: gemeinsame WA-Position, WE Stamm und WEZ Zweig mit eigenen Achsreferenzen; Geometrie bleibt unverändert. Aufnahme per Luftbild ist manuelle Interpretation, kein Vermessungsnachweis.
- Additive Admin-Workspace-Routen für Quellgeometrie, Lesen/Prüfen/Anlegen/Ändern/Deaktivieren der Abschnitts-/Weichenobjekte. Original-/Achsversionskontrolle, Admin+CSRF, transaktionale Historie und idempotente Mutationen. Fremde/private/archivierte Achsen ausgeschlossen; keine automatische Nachbargleiszuordnung. Persistenz auf versionierten Katalogachsen-Metadaten, damit bestehende AXIS-Historie/Changes erhalten bleiben; Android muss neue workspace_records später auswerten.
- Testreset: ausdrücklich ausgewählte GLOBAL-Originalquellen, vorab serverseitige Bestands-/Abhängigkeitsvorschau und Fingerprint; Bestätigung vor Mutation. Katalog-Assets/Abschnitte inaktiv, leere Achsen archivieren; keine Rohoriginale/Dateien/BTB löschen. Gemeinsam verwendete Daten und referenzierende Arbeitsobjekte nicht still löschen. Alte Importe ohne Paket-ID über bewusste Quelldateiauswahl. Reimport-Verhalten ausdrücklich rücksetzbar statt verborgenem Löschzustand. Additive Migration für Assetstatus INACTIVE falls benötigt.
- Luftbild: Anbieterregistrierung und explizites Einschalten mit Attribution, Netzfehler sichtbar, kein Google-API-Konto/Abrechnung. BY/SN/BW-Dienste anhand amtlicher Capabilities/Lizenz prüfen. Automatische Auswahl anhand abgedeckter Kartenposition; bei Grenz-/Mehrfachtreffern Auswahl ermöglichen. Dienstabhängigkeit und Aufnahmealter anzeigen, Offline nicht pauschal versprechen.
- Koordinaten: Stationssprünge nicht durch pauschales Clamping kaschieren. Originale mit vollständigen Rohstationsgrenzen gegen normalisierte Geometrie korrekt darstellen; manuelle geometrische Ausschnitte bleiben streng geprüft. Importdiagnose nennt Sprünge und Grenzen. Regression am bereitgestellten Originalpaket, keine Rohoriginale ins öffentliche Repo.


## REF-DATA-015 – Implementierter Serverblock, Live-Abnahme offen (04.10.2026)
- Konkreter additiver Vertrag steht im verlinkten Übergabedokument. Neue Admin-Routen unter /api/v1/survey-catalog/management/workspace; kind TRACK/SWITCH; Ankerreihenfolge [Anfang,Ende] bzw. [WA,WE,WEZ], geometrische station_m mit axis_id/axis_version/segment_id/segment_version. Ein/aus über active, stable Record-ID und Record-Version; Schreiben benötigt geprüften Input und Basisversion.
- metadata.workspace_records auf Anfangs-/WA-Achse; boundary_type USER_ASSIGNMENT, provenance ADMIN_EXISTING_AXIS. Bestehender AXIS-History/Changes-Pfad; keine neue Android-Berechtigung. coverage/{axis_id}/geometry erhält additiv assignments und parallele stations pro Segment. Quellgeometrie unverändert.
- Migration020: Assetdata_status INACTIVE ergänzend; Workspace-Reset deaktiviert ausgewählte Katalogquellen/Abschnitte, Originale bleiben. Wiederherstellung ausdrücklich, kein verstecktes Wiederaktivieren durch Reimport.
- Android unverändert bei HEAD423f378. Ein bestehendes Handy wertet diese neuen Arbeitszuordnungen noch nicht automatisch aus; App-Verarbeitung/Anzeige/GPS-Erfassung bleiben separater nächster Block.

Details, API-Vertrag, Quellen, Übernahme und Abnahmefälle: [REF-DATA-015_LUFTBILD_ZUORDNUNG_TESTRESET.md](uebergaben/REF-DATA-015_LUFTBILD_ZUORDNUNG_TESTRESET.md).



## REF-DATA-016 – Freigegeben: Anschlussnetz, bidirektionaler Austausch und Serverprüfung (04.10.2026)
- Benutzer bestätigt Gl4→WA→WE→Gl4, Gl4→WE→WA→freie Strecke und Ketten aus mehreren Gleisen/Weichen einschließlich WEZ→WA. Punktrollen gehören zur jeweiligen Weiche; Betrachtungsrichtung ändert sie nicht. Ein gemeinsamer Anschlusspunkt kann mehrere Objektrollen tragen; physische Nähe allein verbindet nichts.
- WA/WE/WEZ unabhängig setzen: Originalachsenreferenz oder ausdrücklich manueller WGS84-Punkt. Gemeinsame stabile Knoten-IDs; versionierte Gleis-/Streckenabschnitte und Weichen; Anschluss wiederverwenden statt Koordinatenduplikat. Gleisabschnittsgrenze ist keine automatisch bestätigte Bahnhofsgrenze. Originale unverändert. Manuelle Verbindung ist eine Näherung, keine erfundene exakte Trassierung.
- KM getrennt von Topologie: Original-/GVP-/manuelle KM-Kalibrierung nie durch Namen, Richtung, Anschluss oder Abschnittsgrenze verändern. Achsposition ist nicht Bahnkilometer. Mehrere streckenbezogene Kilometerreferenzen an demselben Ort zulässig; ohne belegbare Referenz KM unbekannt. Stamm/Abzweig unabhängig.
- PC: kompakte Seitenleiste direkt an der Karte, Punktwahl und Prüfen/Speichern ohne Seitenscrollen; bestehende schnelle Übersicht, GVP-Kalibrierung, Farben und Originalquellen schützen.
- Handy→Server→Handy muss dasselbe versionierte Schema verwenden: clientseitig stabile UUIDs, client_change_id, erwartete Versionen, atomare Änderungen, Idempotenz und Cursor/Deltas einschließlich Deaktivierungen. Keine vollständigen Bestände aus altem Offline-Stand überschreiben. Server und Portal zuerst; keine app/**-Änderung in diesem Block. Android-Implementierung und echter Zwei-Geräte-Roundtrip ausdrücklich separat offen.
- Weitere Bundesländer: offizielle Luftbildanbieter einschließlich Nutzungsbedingungen prüfen; nur verifizierte Dienste anbieten, keine pauschale kostenlose/flächendeckende Zusage. Servercode zusätzlich gezielt auf Fehler prüfen; lokalen Test, echten DB-/HTTP-Test und NAS-Abnahme sauber unterscheiden.
- Benutzerbelege REF015: Migration20/20 ohne ausstehende Migrationen; Bayern-Luftbild sichtbar; Gl4-Anfang/Ende funktioniert. WE/WEZ-Bedienung bisher zu restriktiv. Kein vollständiger Geräte-/Reset-E2E dadurch nachgewiesen.


## REF-DATA-016 – Server-/Portal-Quellstand und Prüfgrenzen (04.10.2026)
- Anforderungs-/Vertragscommit vor Code: 4f44ba0951bebeaead458d1b7c82eb5df9e41bf7. Basis SERVER9740fb0 / PR6 offen Draft; ANDROID423f37892915cb3308a4b1ea9e4d196faa872326 / PR11 offen unverändert. Keine app/**-Änderung, kein NAS-Deployment.
- Neu: versioniertes GLOBAL-Anschlussnetz mit eigenen UUID-Knoten und Gleis-/Strecken-/Weichenobjekten; WA/WE/WEZ unabhängig auf Originalquelle oder manuell per Karte/GPS. Vorhandene Anschlüsse wiederverwendbar, beide Durchlaufrichtungen, gemeinsame Änderungen atomar und versionsgeprüft. Sidepanel neben Karte, Fortsetzen am Anschluss, gespeicherte Zuordnungen bearbeiten/deaktivieren. REF015-Zuordnungen bleiben separat erhalten, keine automatische Migration.
- KM-Kalibrierungen/Originale unverändert. Achsposition != Bahnkilometer. Manuell abgelesene streckenbezogene km_observations bleiben ungeprüfte Beobachtungen; GPS-Position/Genauigkeit/Höhe/Zeit werden erhalten. Ohne belegbaren Originalverlauf nur gestrichelte Näherung. Geänderte/archivierte Quellen → needs_review, keine alte Kurve als gültig anzeigen.
- Neues gemeinsames API /survey-catalog/network: preview, mutations, paginierte entities, Einzelobjekt/Anschlüsse, stationsbezogene changes. Device-Bearer und Portal/Admin-CSRF über bestehende Guards. Idempotenz, stabile IDs/Versionen, ein vollständiger Batch pro Feed-Ereignis; commit_cursor eines Uploads nie als Lesecursor übernehmen. Capability survey_connection_network_v1. Migration021 erstellt Netzbestand/History/Receipts. Original-/KM-Feed weiterhin zusätzlich verfolgen.
- Zusätzliche Prüfung/Korrektur: Workspace-Receipts verwenden AXIS/ASSET statt pauschal SEGMENT; aktive Netzreferenzen schützen Quellenreset und allgemeinen Testreset. Neue Receipts behindern Benutzer-Purge nicht. Veralteten Auth-Test28 auf tatsächlichen case-insensitiven Bearer-Guard korrigiert, kein Auth-Runtimefehler dabei nachgewiesen.
- 97 Python-Prüfungen120–127 PASS (80 bestehende +17 neue), Node119 tatsächliche Templatefunktionen im DOM/Transport-Doppel PASS, Auth-Quellchecks21/28/29 PASS, Python-Syntax PASS. Kein echter FastAPI-/DB-/PostGIS-/NAS-/Geräte-E2E und kein visueller Browsernachweis; Paketinstallation/Browserzugang in Arbeitsumgebung eingeschränkt.
- Luftbilder: zusätzlich NW, HE, TH und BB/BE anhand offizieller Metadaten eingebunden, WMS1.3-Lat/Lon-Reihenfolge geprüft. Insgesamt acht Länder konfiguriert, nur Bayern benutzerseitig als geladen belegt. Die übrigen acht Länder haben offizielle Links mit sichtbarem Prüfstatus; Einbettung ausdrücklich offen. Keine pauschale bundesweite Kosten-/Verfügbarkeitszusage.
- Benutzerbelege zum Vorgänger: Migration20/20, Bayern-Luftbild sichtbar, Gl4-Anfang/Ende funktioniert. Diese Abnahmen bleiben geschützt; neue Netz-/Handy-Abnahme offen.
- Weiter: GitHub Desktop→File Station sieben Runtime-Dateien übernehmen, Migration21/21, API/Worker-Neustart, StrgF5; gemeinsamer Punkt/umgekehrte Weiche/Gl303→freie Strecke und unveränderte GVP-KM auf NAS prüfen. Android-Outbox/Anzeige/Roundtrip erst im nachfolgenden Android-Block. Vollständiger Serveraudit/Last-/BTB-/Backup-E2E nicht durch diese gezielte Prüfung ersetzt.

Vertrag/Übernahme: [REF-DATA-016_ANSCHLUSSNETZ_SYNC.md](uebergaben/REF-DATA-016_ANSCHLUSSNETZ_SYNC.md). Anbieter: [REF-DATA-016_LUFTBILD_PRUEFSTAND.md](uebergaben/REF-DATA-016_LUFTBILD_PRUEFSTAND.md). Serverprüfung: [REF-DATA-016_SERVERPRUEFUNG.md](uebergaben/REF-DATA-016_SERVERPRUEFUNG.md).


## REF-DATA-018 – Punktposition außerhalb der Quelle, Schema1 unverändert
- Editor wählt WA/WE/WEZ unabhängig anhand des Kartenklicks; überlagerte Achsen benötigen ausdrückliche Auswahl. Keine fachliche Gleichsetzung von Gl1 und Strecke5340 allein durch Deckungsgleichheit.
- Innerhalb belegbarer Originalgeometrie binding mit Achs-/Quellenversion/Station; freie Kartenpunkte und ausdrücklich berechnete tangentiale Hilfspunkte vor/nach Quellen werden als position.method=MAP ohne binding synchronisiert. Keine Station außerhalb vorhandener Quelle, keine automatische Bahnkilometer-Extrapolation, keine Änderung von Original-/GVP-/manueller Kalibrierung.
- Hilfspunkt speichert Abstand/Seite/Quellenversion und Hinweis „Näherung“ als Herkunft im vorhandenen note-Feld ([TRA-HILFSPUNKT]); eingefrorene manuelle Position, keine aktive Quellbindung und keine automatische Nachführung. Spätere Verschiebung/Neubindung entfernt diesen veralteten Hinweis; sonstige Notizen/IDs/Basisversionen/KM-Beobachtungen bleiben. Android soll die kanonische Position und Notiz erhalten; neue Netz-Anbindung bleibt gesonderter Android-Auftrag.
- Kein API-/DB-Schemawechsel, keine neue Migration, keine Android-Datei geändert. Vergleich: Android ImportExportFragment GPS-Punkte unabhängig von TRA; TrassenGrafikView Mehrfachtreffer pro Achse/Station. Echter Geräte-Roundtrip weiterhin offen.


## REF-DATA-019 – additive Netz-/Katalogverknüpfung
- schema_version1 ergänzt optionales NetworkObject.catalog_link={id:Katalogobjekt-UUID,version:Katalogbasisversion}. Typ/Betriebsstelle/Version und exklusive Verknüpfung werden beim Preview/Mutate geprüft. Keine Namens-/KM-/Geometrie-Autokopie. Beim Weglassen bleibt bestehender Link erhalten, explizites null entfernt ihn. Andere Clients sollen unbekannte optionale Lesefelder tolerieren und Verknüpfung erhalten.
- GET /network/entities: station_code optional, ohne Filter globaler gemeinsamer Netzbestand, weiterhin UUID-Paging mit has_more/next_id. Stationsbezogener Deltafeed unverändert.
- GET /network/entities/{id}/history: neutrale Feldänderungen einschließlich Punktrollen, neueste50/has_more, keine Actor-/Company-Daten.
- Admin-only GET /coverage/overview-lines?axis_ids=UUID: max8 Achsen, vereinfachte Übersicht + timing_ms. Kein mobiler Vollabgleich daraus ableiten.
- Android bleibt423f378, keine Appänderung/kein Build. Netz-/manuelle Abschnitts-/KM-Anbindung und echter Zwei-Geräte-Roundtrip weiter offen.


## REF-DATA-020 – aktueller Bestand, Kilometerbezug und Weichenfläche (05.10.2026)
- Benutzerfreigabe „okay bauen“ nach fachlichem Vorschlag. Basis SERVER e5aa6cde14048c28f44fb5fe23cd7808f5465b31 / PR6 offen Draft; ANDROID423f378 / PR11 offen, unverändert. Server/Portal zuerst, Android danach.
- Benutzer bestätigt REF019: Gl999 finden, WA/WE speichern und wieder öffnen, Anzeigeauswahl behalten; außerdem Neustart und Datenbestand vorhanden. Keine pauschale Abnahme von Konflikt/Archiv/Last/Android daraus ableiten.
- Aktuelles Gleis ist das stabile Netzobjekt mit begrenztem Verlauf, Anfang/Ende und eigener Kilometerzuordnung. Originalachsen bleiben unverändert als zuschaltbare Grundlage. Bestand erhält Auswahlvorrang. Verlauf über mehrere explizite Originalabschnitte möglich, keine unbelegte Kurve aus zwei Punkten behaupten.
- Optionaler ausdrücklicher Kilometerbezug auf versionierte Originalquelle; ohne Auswahl eigene Punktbindungen verwenden, niemals aus überlagerten Achsen beliebig wählen. Bestehende manuelle Kalibrierungen und belastbare GVP-Zuordnung nutzen, reine GRA-Min/Max ohne punktbezogene Abbildung nicht als exakte Endpunktwerte ausgeben. Außerhalb belegbarer Referenz keine Extrapolation. Verschieben berechnet Werte neu; fehlende/mehrdeutige/veraltete Grundlagen sichtbar statt alter KM am neuen Punkt.
- Optional versionierte Verlaufabschnitte und Positionsstatus; ausgelassene neue Felder durch alte Clients erhalten, explizites null/leer entsprechend dokumentieren. IDs, optimistische Versionen, atomare gemeinsame Punkte, Idempotenz, Historie und Original-/KM-Schutz bleiben.
- Weichen-Auswahlfläche aus beiden Wegen WA→WE und WA→WEZ, jeweils 1,5m seitlicher Breite; gemeinsame äußere Fläche mit WA-Kante3m und aufgeweitetem Endbereich. Darstellung/Erkennung, keine betriebliche Sicherheitsgrenze. W xyz bleibt ohne KM nutzbar.
- Handy-Ziel: Originalimport→gemeinsames Gleis/Weiche→versionierter Deltaabgleich→anderer Nutzer erhält Grenzen/Verlauf/KM samt Herkunft. Keine Android-Implementierung in diesem Serverblock; vorhandene Android-Berechnung read-only vergleichen, später echte Geräte-Abnahme.


## REF-DATA-020 – Quellstand umgesetzt und lokal geprüft (05.10.2026)
- Vorabvertrag/Bestätigungen: 8eb5cf69a17990933e8e81e5ae2dffdfab8958c2. Basis e5aa6cd; Android423f378 unverändert, keine app/**-Änderung, kein APK. Benutzerabnahmen REF019 (Gl999, Punkt-Speichern/Wiederöffnen, Anzeigewahl, Neustart/Datenbestand) bleiben geschützt.
- Befund: Netzobjekte hatten bisher keine berechneten Endpunkt-KM. Jetzt getrennte optionale versionierte km_reference und path_sections sowie position_status; Weglassen durch alte Clients erhält bestehende Werte, Legacy-Receipts bleiben gültig. Objektgrenzen begrenzen tatsächlichen Verlauf; mehrteilige Kurven mit expliziter Reihenfolge/Anschlussprüfung. Bei gemeinsam verschobenen Punkten auf gleicher versionierter Quelle folgen unveränderte Abschnittsgrenzen atomar; Quellenwechsel verlangt erneute Prüfung.
- Kilometer pro Rolle: vorhandene manuelle Teilkalibrierung (auch fallend), sonst robuste GVP-Zuordnung. Ausdrücklich gewählte andere Referenzachse möglich (Gl1 vs5340-0); kein beliebiger Überlagerungstreffer, keine Extrapolation, keine exakten Werte aus bloßen GRA-Min/Max. Konkrete Gründe bei fehlender/mehrdeutiger/veralteter Quelle. Lesepfad prüft aktuelle Kalibrierung; Endpunktwerte mit Herkunft/Version, keine Behauptung lückenloser Innenkilometrierung. Originale/Kalibrierungen unverändert.
- Ansicht Aktueller Bestand (neue Sitzungspräferenz ohne Originale), Mit Originalachsen, Originaldetails eingeklappt, Quelle zum aktiven Punktsetzen vorübergehend sichtbar; bestehende lokale Auswahl bleibt erhalten. KM/Positionsstatus in Liste/Editor/Punktlabels; offene Referenzwahl blockiert Speichern. Weichen-Auswahlfläche aus1,5m beidseitigen Korridoren als gemeinsame äußere Hülle, auch Innenbereich anklickbar. Ohne KM bleibt Weichenidentität sichtbar.
- Schutz: aktive neue Referenzen blockieren Quellenreset und allgemeinen Testreset. Erweiterte Feldhistorie. Androidberechnung read-only verglichen (ManuelleAbschnittKmLogik, Roh-KM, neutrale Originaldarstellung), keine Geräte-/Algorithmusgleichheit behauptet; neue Netz-/KM-Anbindung folgt separat.
- Verifiziert: Python12721 +1282 +12911 =34 Prüfungen PASS, Node119 alte/neue Template-Regressionen PASS, Python-Syntax PASS. Datenbank/CRS/Transport/DOM-Doubles; kein echter FastAPI-/PostgreSQL-/PostGIS-/Browser-/NAS-/Geräte-E2E. Pakete dafür lokal nicht verfügbar. Kein NAS-Deployment, keine gemessene neue NAS-Laufzeit.
- Übernahme4 Runtime-Dateien: server/tbserver/survey_network.py, server/tbserver/survey_workspace.py, server/tbserver/admin_purge.py, server/templates/v1_admin_survey_overview.html. GitHub Desktop→File Station→API-Neustart→StrgF5. Migration bleibt21/21; kein Reset/Reindex.
- Nächste NAS-Abnahme: Gl1→Kilometerbezug Str5340-0→Originalquelle ausdrücklich wählen→Prüfen→Werte/Grund bewerten→Speichern/Wiederöffnen→Anfang versetzen. Weichenfläche und mehrteilige/shared Verläufe prüfen. Gesamt-Admin/Backup/Worker/Rechte/Last und Android bleiben eigene offene Blöcke.

Vertrag, genaue Grenzen, Übernahme und Android-Folgeauftrag: [REF-DATA-020_BESTAND_KILOMETER_WEICHENFLAECHE.md](uebergaben/REF-DATA-020_BESTAND_KILOMETER_WEICHENFLAECHE.md).


## SERVER-GATE-001 – Rechte-/Restorevertrag (06.10.2026)
- Aktive Geräte-/Portalsessions und neue Anmeldung/Aktivierung prüfen zusätzlich Firmenstatus. Gesperrte/gelöschte/fehlende Firma:403 COMPANY_DISABLED. Laufende Syncs übernehmen dies über erneute Authentifizierung. Systemadmin bleibt für Systemverwaltung verfügbar, ohne Baustellen-Fachzugriff.
- Baustellen-Dateiliste/Medien/Metadaten/Inhalt/Vorschau verlangen files.view. Fremde Uploadsession:403 UPLOAD_OWNER_REQUIRED; nur Ersteller liest/ändert/abschließt/bricht ab, Schreiben weiter mit files.add. Keine neuen erfolgreichen Antwortfelder oder Androidänderung.
- Nach Restore eines neuen konsistenten Vollbackups sind zum Snapshotzeitpunkt offene Uploadsessions EXPIRED und müssen neu begonnen werden; bereits vollständig gespeicherte Dateien werden geprüft wiederhergestellt. Live-Uploads werden vom Backup nicht zurückgesetzt.
- Echte FastAPI-/PostgreSQL-CI für diese Grenzen und3x255 HTTP-Longpoll PASS, keine Androidgeräte-/NAS-/Cloudflare-/Fotolastfreigabe. Details server/checks/SERVER_GATE_001.md.


## OPS-RESTORE-003 – bewusster Server-Rollback (06.10.2026)
- Beim administrativ vorbereiteten Zielrestore werden alte Geräte-Tokens und Browser-Sessions ungültig, offene Aktivierungen/Einladungen widerrufen. Außer dem anfordernden Admin bleiben Nutzer im Ziel bis zur Rechteprüfung deaktiviert. Offene Uploads müssen neu begonnen werden. Dies betrifft ausschließlich das Ziel, nicht die laufende Quellinstanz.
- Nach tatsächlichem Cutover Geräte bewusst neu aktivieren und Serverbestand neu laden. Noch lokale ungesendete Daten vorher sichern; kein stilles automatisches Replay einer alten Outbox gegen den zurückgesetzten Server. Android-Code unverändert, Geräteablauf noch nicht abgenommen. Stabile Fach-IDs des Backups bleiben erhalten; keine neue erfolgreiche normale API-Antwortstruktur.
- Vollständiger Ablauf und Grenzen: server/checks/OPS_RESTORE_003.md. Echte CI-API-Umschaltung bestanden, keine NAS-/Android-Abnahme.


## OPS-RESTORE-004 – freigegeben vor Umsetzung (06.10.2026 22:02)
- Nutzerauftrag nach Besprechung: normale Update-Wiederherstellung standardmäßig mit Firmen, Benutzerstatus/Rechten und aktiven Gerätebindungen aus Backup; separate Variante SECURITY für Sicherheitsvorfall mit bisherigen Zugangssperren. Portal-Sessions in beiden Varianten widerrufen. Offene Uploads/Jobs nicht wieder abspielen. UPDATE bewahrt Automatikpolicy, SECURITY deaktiviert sie.
- POST /admin/system/recovery/prepare erhält mode UPDATE|SECURITY (neue Anfragen Default UPDATE); bestehende gespeicherte Zustände ohne mode bleiben SECURITY. Bereits READY-Ziele werden nicht verändert. Frisches Ziel, Sicherheitsbackup, manuelle Umschaltung und DB/Storage-Paarprüfung bleiben.
- Neue FULL-Manifeste erhalten nicht geheime Runtime-/Schema-Metadaten (Version, deterministischer Quellfingerprint, Migrationschecksummen). UPDATE verlangt passende Metadaten/Runtime und Schema; alte Archive ohne Metadaten bleiben für Backup-Prüfung/SECURITY nutzbar. Passendes Serverpaket/Containerimage muss separat vor Update erhalten bleiben; kein automatischer Programmdowngrade.
- Wiederherstellungskennung wird nach tatsächlichem Cutover über capabilities bereitgestellt. Geräte mit erhaltenem Token benötigen passende X-TB-Recovery-ID; fehlende/veraltete Kennung ergibt 409 SERVER_RECOVERY_REBASE_REQUIRED vor Fachzugriff. Kennung ist keine Berechtigung; normale Auth/Rechte bleiben. Android muss lokale Outbox sichern, Serverbestand neu laden und Konflikte abgleichen, bevor es normale Mutationen freigibt. Android hier unverändert, Geräte-E2E offen.
- NAS-Screenshot 21:49 bestätigt OPS-RESTORE-003 READY nach Anlegen fehlender Zielordner, ausdrücklich keine Umschaltung. Anleitung ergänzt Anlage beider leerer Zielordner. Bestehendes Ziel bleibt SECURITY und belegt; keine automatische Löschung/Neupräparation.
- Umfang Server/UI/Vertrag/Tests; keine echte NAS-Wiederherstellung oder E-Mail. Testziel beide Modi, Altzustand, Metadatenfehler, Gerätekennung und bestehende Ziel-/Zugangsschutztests.


## OPS-RESTORE-004 – Servervarianten implementiert (06.10.2026)
- Nutzerfreigabe22:02 nach Besprechung; Vorabvertrag775cc10. Runtime2fb6b8da607761e42610f0513c51e706fbd79d5e, Testergänzung2fad3251214291c91af3a62935a549596270f1cc. Androidbranch423f37892915cb3308a4b1ea9e4d196faa872326 unverändert, kein app/**.
- Neue Vorbereitungen: UPDATE Standard mit Benutzerstatus/Rechten/Ablauf, Firmen und Gerätebindungen/Tokens sowie Automatikpolicy aus Backup; SECURITY erhält bisherige Zugangssperren. Beide Modi widerrufen Portal-Sessions und verwerfen offene Jobs/Uploads. Zustände ohne mode bleiben SECURITY; vorhandenes NAS-READY-Ziel wird nicht geändert oder gelöscht.
- FULL-Manifeste enthalten nicht geheime Runtime-/Schemaidentität. UPDATE verlangt exakten Programmfingerabdruck und restaurierte Migrationschecksummen. Alte Backups ohne Metadaten für UPDATE abgewiesen, Backup-Prüfung/SECURITY weiterhin möglich mit passendem Schema. Serverpaket/Image muss separat gesichert werden; kein automatischer Code-/Schemadowngrade. Programmstand während Vorbereitung/Umschaltung nicht austauschen; spätere normale Updates nicht dauerhaft blockiert.
- capabilities liefert recovery_epoch_v1 + recovery_id (null vor tatsächlichem Cutover). Geräteauth prüft nach normalen Token-/Nutzer-/Firmenrechten X-TB-Recovery-ID; fehlend/falsch→409 SERVER_RECOVERY_REBASE_REQUIRED, passende Kennung erlaubt normalen Rechtepfad. Keine Umgehung durch öffentliche UUID; sie ersetzt keine Auth. Android muss Outbox schützen, Cursor verwerfen, Serverstand getrennt laden und Konflikte abgleichen, bevor normale Mutationen wieder starten. Aktuell unangepasste App bleibt nach Cutover bei Fachzugriff blockiert; daher noch kein fertiger Geräte-Rollback-E2E.
- NAS-Bestätigung Screenshot21:49: vorheriger OPS-RESTORE-003-Vorbereitungslauf READY; keine tatsächliche Umschaltung. Fehlende Bind-Mount-Ordner zuvor nachgewiesen; Anleitung/UI nennt jetzt vor Erststart beide leeren Ordner. Vorhandenes Ziel belegt; neuer Versuch braucht frisches getrenntes Ziel, kein blindes Löschen.
- Übernahme8 Runtime-Dateien aus server/: tbserver/recovery_version.py NEU, recovery.py, recovery_guard.py, system_admin.py, backup.py, devices.py, capabilities.py, templates/v1_system_status.html. API+Worker neu starten; StrgF5, Migration weiterhin21. Noch keine NAS-Übernahme dieser Änderung behauptet. Ablauf/Grenzen/Androidvertrag: server/checks/OPS_RESTORE_004.md.

- Nachweis:89 Verhaltenstests130–141 PASS; Python/JavaScript/YAML-Syntax PASS. Echter PostgreSQL17/FastAPI-Gate zweimal SUCCESS; letzter Run37524540497 Job112478085754: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/37524540497/job/112478085754 . UPDATE und SECURITY jeweils echte Sicherung/Wiederherstellung/API-Neustart mit Portal-/Geräteprüfungen PASS; zusätzlicher Altzustand ohne mode bleibt SECURITY. Quellbestand erhalten, Zielfehler/Lock/Retry-Guards unverändert geprüft. 3x255Clients0Fehler,p95 6,13–6,16s inkl2sWait; keine NAS-/Android-/Foto-/BTB-Schreiblastabnahme.
- Gesamt-CI weiterhin nicht grün: foundation alter FILE_LINK_TARGET_NOT_FOUND-Marker, Android-CI alter Sourcevertrag. Nicht umgangen/abgeschaltet. Weitere Server-/Portal-Arbeit und Android-Recoveryintegration bleiben offen.


## PORTAL-005 – freigegebener Gesamtauftrag (11.10.2026, 17:34 Berlin)
- Nutzerfreigabe: bauen. Portal nach Aufgaben mit Baustellen/Bilder/Einstellungen, Baustellenreitern Übersicht/BTB/Bilder/Unterbaustellen/Mängel und Abnahmen/Team, bedarfsweise Formularen, deutschen Statusnamen und ohne JSON-Bedienfelder umbauen.
- Öffentliche Startseite: Erklärung und aggregierte Streckenkilometer, Bahnhöfe mit Daten, Strecken und Baustellen. Testbaustellen ausdrücklich mitzählen. Keine Firmen-/Nutzer-/Standortdetails oder technischen Speicher-/Serverdaten öffentlich.
- Passwort vergessen mit bestätigter E-Mail, einmaligem befristetem Resetlink, neutraler Antwort und Missbrauchsbegrenzung; administrativer Ersatzweg ohne bestätigte E-Mail. Profil/E-Mail/Passwort in Einstellungen; Geräteaktivierung getrennt erhalten.
- Helfer laut Nutzer vor Backup in Portal und Handy entlassen, dennoch Mitglied und WORKING sichtbar. Source-Lücke: set_own_status überschreibt NOT_PARTICIPATING bei resume und finish; Entlassung darf nur Teamleitung aufheben. Konkreter NAS-Ablauf unbewiesen. Teammitgliedschaft und einzelne BTB-Teilnahme getrennt behandeln; Fotorechte untersuchen, keine pauschalen Rechte vergeben.
- ZIP-Regel BESTÄTIGT: auf exportierendem Handy vorhandene eigene Originale verwenden, Helfer-/Portalbilder verkleinert; Export vom Handy an PC. Vorhandene Implementierung schützen, keine Android-Änderungen in SERVER.
- PORTAL-RIGHTS-004 NAS-Screenshots zeigen Adminlink und files.view-Abbruch behoben; fehlendes Fotorecht selbst bleibt offen. Backupabnahme geschützt. Umsetzung und Tests des neuen Auftrags noch offen.


## EXPORT-008 – Excel-Bildsuche und Originalnachlieferung (12.10.2026)
- Nutzer gibt Fortsetzung mit Bilderliste_gesamt.xlsx frei. Anhang read-only geprüft: ein Blatt,27 Bildzeilen,27 eindeutige relative Links, Bilddatei SpalteM, keine eingebetteten Bilder/Makros, keine separate Bild-ID. Aktueller Generator auf ANDROID423f3789 nutzt15Spalten/BilddateiN; Suchfunktion muss Überschrift statt feste Spalte verwenden.
- Quellbefund GesamtMedienArchiv.kt: lokale Bilder über SHA256(Dateibytes), Serverbilder über item.sha256, Namen hash.take(12), Dateiabgleich/Deduplikation ebenfalls hashbasiert. Original und komprimierte Version besitzen unterschiedliche Dateiinhalte; identischer Exportname/Ersetzung ist dadurch nicht garantiert. Vorherige Aussage zum einfachen Zusammenkopieren war nur unter Namensgleichheit gültig. Ohne Bilddateien/ZIP kein Geräte-E2E-Nachweis.
- Laptop-Zusatz: optionales VBA-Modul für Excel Desktop/Windows, bewusst gestartet, Ordnerwahl, rekursive Suche, nur exakt eindeutiger Dateiname, mehrdeutige Treffer unverändert, Ergebnisprotokoll, Originalarbeitsmappe nicht automatisch speichern. Keine Bilddateien löschen/kopieren; keine Makrosicherheit ändern. Eingebettete XLSM und echter Excel-Lauf sind in aktueller Umgebung nicht verfügbar; separat importierbares Modul liefern, keine fertige XLSM behaupten.
- Android-Folgeauftrag: unveränderliche gemeinsame photo_id für Original/Sync-Version/Thumbnail; Manifest und Excel erhalten ID, Variante und relativen Pfad. Datei-Hash nur Integritätsmerkmal. Originalauswahl nur anhand belegter Variante gleicherID, nicht Dateigröße/ähnlichem Namen. Altexporte ohne Mapping können nicht sicher automatisch ergänzt werden. Keine app/**-Änderung in SERVER.
- Portal-Galerie60er-Seiten/Serverfilter/10000er-Prüfung bleibt nächster separater Serverblock; bisher noch nicht implementiert. ZIP darf nie auf die sichtbare Galerieseite begrenzt werden.


### PORTAL-012 – korrigierte KM-Regel und Portalbildentscheidung (13.10.2026, 14:41 Berlin)
- Nutzer korrigiert: Eingabe13.200 soll Bilder von13.100 bis13.299 anzeigen, nicht nur exakt13.200. Eingabe13.000 bleibt nach vorherigem Auftrag13.000–13.999. Diese konkreten Regeln sind verbindlich; keine allgemeine symmetrische ±100m-Regel behaupten, weil13.300 ausdrücklich nicht eingeschlossen ist. Andere Eingaben bei Umsetzung nachvollziehbar definieren.
- Nutzer entscheidet: Portal ebenfalls nur verkleinerte Bilder hochladen, Original bleibt bereits auf PC/Laptop. Verkleinerung vor Übertragung im Browser, serverseitige Prüfung ergänzend; kein normaler Originalupload/-speicher für neue Portalbaustellenfotos. Dies ersetzt die bisherige Erlaubnis, Portalquellen voll auf NAS aufzubewahren, für diesen neuen Standardpfad. Bestehende Dateien nicht ungefragt verändern/löschen. Konkrete Byte-/Qualitätsgrenzen weiterhin Vorschlag, keine pauschale Bestätigung von1MB daraus ableiten.
- GPS-Frage: Quellprüfung bestätigt GalerieOpenStreetMap, BTBGoogleMaps. Unterschied stammt aus bisheriger Galerieimplementierung, keine technische Notwendigkeit. Einheitliches GoogleMaps als Vorschlag; Wechsel noch nicht ausdrücklich beauftragt.
- Weiterhin Vorschlags-/Entscheidungsblock gemäß Nutzer; kein Programmcode geändert, noch keine Umsetzung/NAS-Abnahme behauptet.


### PDF-CHECK-013 – drei Nutzer-ZIPs read-only geprüft (13.10.2026)
- 22.09.2026: BTB-PDF975269Bytes/5A4-Seiten, ProducerSkia/PDFm148 (Android); bestätigte Referenz mit Fachblöcken, drei Fotokarten pro Zeile, Ort/Firma/Text/GVP-Angaben, sichtbarer Änderungskennzeichnung. PDF keine Linkannotationen. ZIP13Bilder648064–1007625Bytes,2040×1536/1536×2040; Originalupload unverändert.
- 24.09.2026: BTB-PDF8478648Bytes/18A4-Seiten, ProducerReportLab, Marker tb-server-btb-pdf-3, erzeugt26.09.2026. Generischer alter Serverbericht zeigt technische tb_helfer_android_v1-/Slotfelder, codierte BTBP1-Dateinamen, etwa zwei große Bilder pro Seite. ZIP27Bilder270361–1146742Bytes; keine Linkannotationen im PDF. Kein Beweis aktueller Serverlaufzeitversion aus historischem Dokument.
- 05.10.2026: BTB-PDF366329Bytes/3A4-Seiten, ProducerReportLab, Marker tb-server-btb-pdf-5, erzeugt05.10.2026. Bereits Fachlayout, jedoch anderer großer Firmenkopf/Logo-Bereich und Fotoanhang2Spalten/feste238pt-Karten gegenüber Android3Spalten; Orts-/GVP-Block fehlt im gezeigten Fotoanhang. ZIP1Bild288588Bytes/1536×2040; GPS als Text ohne anklickbare PDF-Linkannotation.
- Nachweis: Metadaten, alle Seitentexte/Bildanzahlen/Dimensionen, gerenderte Haupt-/Fortsetzungs-/Fotoanhangseiten verglichen. Keine Pixelparität oder Datenverlust durch Renderer behauptet; Dokumente haben unterschiedliche Fachinhalte.
- Aktueller GitHubserver53a266bc... verwendet weiterhin tb-server-btb-pdf-5 und btb_pdf_android_style._draw_photo_appendix mit2Spalten/fester238ptHöhe/details[:5], ohne Ort/GVP-Zeile und ohne GoogleMaps-Linkannotation. Android423f3789 BtbExportPaket.exportVerlauf exportiert vorhandene v.pdfDatei oder Finderdatei; keine Neurenderung beim ZIP-Packpfad. Daher frischer ZIP-Export kann alte Server-PDF3 enthalten.
- Ursache der nachgewiesenen Darstellungsunterschiede: unterschiedliche Android-/Serverrenderer und historische gespeicherte Abschluss-PDFs, keine Bildkompression als Ursache. Aktiver NASrenderer/konkrete Gerätepfadwahl nicht ferngeprüft. Großer Farbkreis im05.10.-Dokument ist eingebettetes Firmenlogo, kein Nachweis eines beschädigten Fotos.
- Vorschlag, noch nicht freigegeben:22.09.-Layout als Referenz für Serverkopf/Fachblöcke/Fotokarten3Spalten, dynamische Text-/Seitenhöhen, Ortsauflösung und vorhandene GVP-Metadaten, GPSGoogleMaps-Link; Nummern im PDF/Text/Export konsistent vergleichen. Kontrolliertes Neurendern aktueller Abschluss-PDFs nur ausdrücklich beauftragt, ersten Originalsnapshot/Änderungshistorie niemals ersetzen. Nicht alle Fotos auf Hauptbaustelle umhängen oder Ursprungsort/GVP erfinden.
- NutzerzielBildgröße1–2MB/max2MB; kleinere geeignete Bilder nicht vergrößern. ZIPs belegen viele brauchbare Dateien bereits unter1MB, Galeriegrößenbis4,19MB betreffen anderen gespeicherten Bestand. ZIP-Größe/PDF-Bildgröße nicht mit Serveruploadoriginal gleichsetzen. Portalverkleinerung vorUpload beschlossen; adaptive Qualität/Auflösung und serverseitige Grenzen vorschlagen, keine Bestandskompression/Löschung ohne Auftrag.
- Ausschließlich Prüfung/Dokumentation, kein Runtimecode, keine Migration/NAS-/Androidmutation. PORTAL-012kompakte Karten/Text-/KM-Suche unverändert geplant; GoogleMaps-Galeriewechsel und Kompressionsdetails weiterhin Vorschläge. Kein E2E-Lauf in diesem Prüfblock.


### PORTAL-012 / PDF-CHECK-013 – Nutzerpräzisierung (13.10.2026,15:14 Berlin)
- Verbindlich ab jetzt: neue Bilder zum Server maximal2MB; keine Mindestgröße, bestehender Serverbestand laut Nutzer okay und unverändert lassen. Als technische Byteeinheit für Umsetzung 2MB=2000000Bytes vorschlagen/festhalten, keine2MiB-Grenze stillschweigend verwenden. Portal vorUpload verkleinern; Android eigener Folgeblock, tatsächliche Durchsetzung erst nach Umsetzung nachweisen.
- Nutzer sagt „auf dem server ist schon alles okay“ zur PDF-/Serverdiskussion. Nicht pauschal auf alle Einzelpfade ausdehnen: ausdrücklich weiter offen Unterbaustellen/Ort und behauptete abgeschnittene Überschrift beiFoto27. Neu hochgeladene Abschluss-PDF gegen ältere Vergleichs-PDFs prüfen; vorhandene verbesserte Serverdarstellung schützen, keine Rücksetzung auf alte Renderer.
- Foto27 Screenshot: Beschriftung „Fertigstellung Bü7“, Ort „Hauptbaustelle ·5362_R_071“; Nutzer erwartet BÜmitKM oder passende Unterbaustelle. Zuerst Quellmetadaten/Zuordnung/Textgrenzen prüfen, keineBÜ-/KM-/Unterbaustellenzuordnung aus GPS-Nähe erfinden.
- Aktueller Auftrag bleibt ausdrücklich erst alles prüfen und Vorschläge; keine Runtime-/DB-/Androidänderung in diesem Block.


### SUBSITE-CHECK-014 – Foto27/Unterbaustellen read-only untersucht (13.10.2026)
- Neue Abschluss-PDF BTB_bd7bef76... ist BTB06.10.2026,3Seiten, erzeugt11.10.2026, ReportLab/tb-server-btb-pdf-5. Nutzer bestätigt Serverdarstellung als okay; nicht ungefragt Layoutumbau aus PDF-CHECK-013 durchführen. Neue PDF hat nurBild1, enthältFoto27 vom24.09. nicht. Sie beweist nicht Foto27-Zuordnung.
- Foto27Screenshots zeigen vollständig den vom aktuellen Modell gelieferten Kurztext „Fertigstellung Bü7“ und Firma „Peter Schaue“, OrtHauptbaustelle·5362_R_071. Galerie setzt summary.textContent ohne Ellipsis/Zeilenlimit, Textumbruch vorhanden; kein Quellbeleg für CSS-Abschneiden.
- Beleg aus unverändertem24.09.-ZIP: Bilderliste_gesamt.csv fürFoto2026-09-24_16-04-47(2).jpg/Zeit16:04/GPS47.596990,10.253655 enthält UnterbaustelleBÜ78,571, KurzbeschreibungFertigstellungBü78,571, FirmaPeterSchauerTiefbau und Aufnahmekilometer78,577. BÜ-Kilometer78,571 und Aufnahme-KM78,577 getrennte Angaben, nicht gleichsetzen.
- AlterServer-PDF3 Foto27Dateiname BTBP1 decodiert: epoch1790258692605,GPS47.5969887/10.2536554,KM78.5770035, FirmaPeterSchaue,KurztextFertigstellungBü7,Objekt5362_R_071,Objektbereichleer. Damit abgeschnittene Altmetadaten reproduzierbar, passendeOriginalexportzeile anhandZeit/GPS/KM belegt; unveränderlicheFoto-ID zur sicherenBestandsreparatur trotzdem prüfen.
- Android423f3789 BtbTeamMediaSync.encodedPhotoFilename/writeTinyUtf begrenzen Firma auf12UTF8Bytes,Kurztext20,Objekt10,Bereich8; diesesAltformat enthält keineworksiteNodeKey. Neue structuredPhotoMetadata enthält dagegen volleFelder und worksiteNodeKey. BekannteServerfotos werden nicht neuhochgeladen; localPhotoOverridePatch sendet Firma/Ort nurbeiContextoverride undKurztext nurbeiTextoverride. Bloßes normalesSynchronisieren repariert daher fehlendeAltgrundmetadaten nicht garantiert.
- Galerie nodeLabel liefert bei fehlendemworksiteNodeKey pauschalHauptbaustelle; unbekannterKnoten mit vorhandenemKey wird dagegenUnterbaustelle<Key>. DieserFallback unterscheidet nicht zwischen tatsächlicherHauptbaustelle und unbekannterAltzuordnung. „AlleUnterbaustellen fehlen“ fürgesamtenNASbestand nichtnachgewiesen, keinLiveAPI-/DB-Zugriff.
- Vorschlag vorUmsetzung: vollständige strukturierteMetadaten undBTBOverrides bevorzugen; fehlendeAltzuordnung alsOrt/Unterbaustelle nichtzugeordnet kennzeichnen, echteHauptbaustelle separatbelegen. BestehendeKnotenbezeichnungen auflösen(z.B.BÜ78,571), Aufnahmekilometer extraanzeigen. Altdaten ausschließlich überbelegtenID-/Zeit-/GPS-/Originalexportabgleich mitKonfliktprüfung undVorschau ergänzen, keine Zuordnung ausGPS-Nähe/Dateigröße oderbloßemKurztext. FürFoto27 Zielwerte ausCSV verfügbar, keineÄnderung jetzt.
- NeueBildübertragungen max2MB(2000000Bytes) verbindlich, vorhandene2,04MB-/größereBilder unangetastet. PortalvorUploadverkleinern undServerprüfung;Android2MB-Durchsetzung bleibt Folgeauftrag. KeinMindestvolumen.
- NurPrüfung undDokumentation; Runtime/App/DB/NAS/Originale unverändert. PORTAL-012Suche/kompakteKarten weiterhin ungebauteFolgearbeit, keineAbnahmebehauptung.


### PORTAL-012 – Quellstand fertig, isoliert geprüft (13.10.2026)
- Getesteter Stand 322f7b14094c0752eef07bd3bcfb659ef36c1d03. Mehrwortsuche als AND, unabhängig von Groß-/Kleinschreibung, über kanonisch decodierte Alttexte, strukturierte Metadaten, wirksame BTB-Linkoverrides inklusive clear und aufgelöste Ortsnamen. Suchtreffer nur über einen zugleich berechtigten Link; fremde Linktexte dürfen keine sichtbaren Dateien als Treffer offenlegen. Keine Suche nur auf der aktuellen Seite.
- KM13.000→13.000–13.999;13.200→13.100–13.299; Punkt/Komma/optional KM gleich. Andere nichtganzzahlige Werte100m davor bis99m danach; auf angezeigte Metergenauigkeit gerundet. Bereich bei Treffern sichtbar. Migration024 ergänzt nur zwei lesende Such-/Decodefunktionen, kein Bild-/Metadatenrewrite. PostgreSQL17-Byteoffset-/UTF8-Dokumentation über Context7 abgeglichen.
- Galerie kompakter, weiter60 Bilder und kleine Renditions, GoogleMaps-Link. Fehlender worksiteNodeKey liefert „Unterbaustelle nicht zugeordnet“ statt unbelegter Hauptbaustelle; vorhandener nicht auflösbarer Schlüssel gesondert. Vollständige vorhandene Felder werden nicht gekürzt. Aus bereits gekürztem Altdateinamen werden keine verlorenen Texte/Orte erfunden.
- Portal-BTB-/Mangel-/Abnahmefotos werden im Browser vor SHA/Chunkupload als JPEG vorbereitet: maximal2560px, Qualität90–75, gegebenenfalls kleinere Kante, maximal2000000Bytes, nicht hochskalieren. PC-Original unverändert, kein unkomprimierter Fallback. Kleine Dateien erlaubt. Serverseitige2-MB-Prüfung bei Uploadbeginn, kumulierten Chunks und tatsächlichem Abschluss; explizite PHOTO/DEFECT_PHOTO/ACCEPTANCE_PHOTO, Bild-MIME/Endungen und beim Abschluss erkannte verdeckte Bilddateien. Bestehende Dateien sowie größere PDF-/Dokumentuploads unverändert.
- Portalworkflow38417438535/Job115365514637 SUCCESS; echte Steps148/150/149 ausgeführt, Logs und Desktop1440/Mobil390-Screenshots geprüft. Migration24 und Wiederholung,10000 Datensätze über167 Seiten, Nummern/Rechte/Linkänderung/clear/Legacy-/Mehrwort-/Orts-/KM-Suche, fremde Linkmetadaten, Versionsunterschied, Browserverkleinerung/Originalerhalt geprüft. Erste Seite36239Bytes/3SQL/0,1308s; gesamte Traversierung14,792s auf CI. Echter2.000.000Byte-JPEG-Chunkupload/Abschluss bytegleich PASS;2.000.001 bei Beginn/Chunk/Altabschluss abgewiesen, kleine Bilder und größere PDF-Dateien erlaubt. Kein NAS-Performance-/E2E-Nachweis daraus.
- Snapshot-/Rechte-/255Client-HTTP-Gate38417438519/Job115365514320 SUCCESS mit tatsächlich ausgeführten Steps. Gesamt-CI weiterhin nicht vollständig grün: unveränderter Foundation-MarkerFILE_LINK_TARGET_NOT_FOUND, Android-CI rot. Androidbranch423f37892915cb3308a4b1ea9e4d196faa872326 unverändert, kein APK. PR6 bleibt Draft.
- NAS-Installation/Praxisabnahme offen. Acht Runtime-Dateien inklusive Migration024 und neuer Include-Vorlage: [PORTAL-012_NAS_UEBERNAHME.md](uebergaben/PORTAL-012_NAS_UEBERNAHME.md). Über GitHubDesktop→FileStation→ContainerManager Projekt neu erstellen/starten; nicht nurAPIneustarten, da Suchfunktionen fehlen würden. Migration24/24 undPORTAL-012 prüfen; nichts löschen/keine .env/Compose/Daten-/Backupänderung.
- Foto27-Korrekturvorschau mit belegten vollständigen Angaben in derselben Anleitung vorbereitet, noch keine Produktionszuordnung geändert. Kontrollierte Altmetadatennachlieferung/Android bleibt eigener Folgeauftrag. Kein PDF-Layoutumbau/Neurenderung; bestätigte Serverdarstellung erhalten. Neue 2-MB-Servergrenze gilt auch für alte Androidclients; größere neue Syncbilder werden verständlich abgewiesen, Androidverkleinerung nicht als implementiert behaupten.
- Bestehende Exceldatei gemäß dokumentiertem binären Defekt unverändert/blockiert; MD-Steuerung gesichert.


### PORTAL-015 – KM-Regel und Baumreihenfolge umgesetzt (18.10.2026)
- Neue volle-Kilometer-Regel: 13.000/13,000/13/KM13.000 → 12.000–14.000 einschließlich beider Grenzen; volle Kilometer allgemein ±1km. Nichtganzzahlig13.200 → 13.100–13.299 unverändert. Punkt/Komma und optionale KM-Eingabe bleiben gleich. Keine SQL-/Migrationsänderung.
- Baustellenansicht rendert nun Eltern vor ihren Kindern und deren gesamte Unterstruktur zusammenhängend. Bisherige flache nach parent_id sortierte API-Liste konnte die Anbindung vor SchachtV2 anzeigen. Eingabe-/API-Speicherung der parent_id bleibt unverändert; keine echten NAS-Zuordnungen auf Verdacht umgehängt. Fehlende Eltern/Zyklen bleiben sichtbar, Traversierung endet sicher und ist ohne rekursive Aufrufgrenze. Reihenfolge der Geschwister aus API erhalten.
- Tatsächlich lokal ausgeführt: checks/151_portal_km_tree_regression.py PASS, nutzt Produktions-KM-Parser und echte Produktionsfunktionen nodeTreeRows/loadNodes mit DOM-/API-Doubles; Randwerte11999/12000/14000/14001, unveränderte13.200-Grenzen, Null/negative volleKM, Screenshot-Struktur, Einrückung, keine Datenmutation, Zyklen/Orphans und5000 Ebenen. Python-AST und JavaScript-Syntax PASS. Erste Test-Doppel-Scope-Bindung korrigiert, danach vollständiger lokaler Lauf PASS. Bestehender PostgreSQL-Integrationstest150 um neue Grenzen angepasst, in diesem Block nicht ausgeführt.
- CI-Budgetschutz CI-COST-001: gebündelter Commit mit [skip ci], keine großen Actions-Läufe gestartet. Keine neue PostgreSQL-/Chromium-/Synology-E2E-Abnahme behauptet. Bestehende allgemeine CI-Restpunkte bleiben separat.
- NAS-Übernahme nur server/tbserver/media_gallery.py und server/templates/v1_worksite.html in entsprechende vorhandene Unterordner von /volume1/docker/tb-helfer-v1; API-Container neu starten, StrgF5. Keine neue Migration, nichts löschen, .env/Compose/Storage/Backups unverändert. Bisheriger PORTAL-012-Migrationsstand wird vorausgesetzt. Anleitung: [PORTAL-015_NAS_UEBERNAHME.md](uebergaben/PORTAL-015_NAS_UEBERNAHME.md).
- Ausgangsstände live geprüft: SERVER d4008086989d919b7a24aa088c54e5c0bdeff329/PR6 Draft; ANDROID6cec34423e34315c581b743668724abe17daa333/PR11 offen, kein Draft. app/** unverändert. Excel bleibt laut bestehendem dokumentiertem binären Defekt unverändert; keine Ersatzhistorie erzeugt.

### PORTAL-016 – Additive Zeitfelder und Portal-Komfort (18.10.2026)
- Freigegeben: Berichtzeiten automatisch in Personal/Maschinen nachführen, individuelle Abweichungen erhalten. Firmenpayload personalZeitbindung/maschinenZeitbindung = REPORT oder MANUAL; bei Legacy ohne Flag nur leere bzw. der bisherigen Berichtszeit entsprechende Paare übernehmen. Headeränderung + Firmenversionen/Events in derselben Transaktion. Uhrzeitberechnung Europe/Berlin.
- SectionItem behält uhrzeit als Beginn, additiv uhrzeitBis als Ende; bei Ende<Beginn ist der Folgetag gemeint. Alte Einzelzeit bleibt gültig. Portal löscht Endzeit ausdrücklich mit null. Server-PATCH erhält uhrzeitBis bei ausgelassenem Feld älterer Clients. Android-Modell/-Anzeige/-PDF/-Save später erweitern; im Serverprojekt kein app/** ändern. Endzeiten werden deshalb bis Androidanpassung dort nicht angezeigt.
- Viertelstunden sind Portal-Auswahl, keine rückwirkende Rundung alter Fotos/Einträge und kein globales API-Verbot historischer Minutenwerte.
- Direkte Entry-Fotos nutzen bestehenden BTB-Fotoupload/2.000.000Byte-Grenze und dieselbe Bilderliste, übernehmen typ/Firma/firmaId/worksiteNodeKey/kurztext und optional manuelles Eintrags-KM; keine erfundenen GPS. Kein zweiter Bildbestand.
- Wettervorschlag ist read-only und speichert erst über bestehenden versionierten Header-PATCH. Wetterformat WeatherBlock/slots bleibt kompatibel; Quelle Open-Meteo, drei Schichtzeitpunkte wie Handy (1/6,1/2,5/6 auf Viertelstunde aufgerundet). Stundenmodellwerte werden als Vorschlag gekennzeichnet, keine angebliche Vor-Ort-Messung. Vorhandene Werte nicht ohne Bestätigung ersetzen.
- Android HEAD6cec34423e34315c581b743668724abe17daa333 unverändert; kein APK/Android-E2E-Nachweis.

### PORTAL-016 – Serverumsetzung lokal geprüft
- Additive Felder/Altclient-PATCH-Schutz und automatische verknüpfte Firmenzeiten umgesetzt; lokale Checks152/151 sowie PDF-Text/PNG-Prüfung PASS. Endzeit wird bei fehlendem Feld erhalten, null löscht. MANUAL-Abweichungen bleiben geschützt. Kein app/**, kein APK, kein Android-/NAS-Roundtrip bestätigt. Exakte NAS-Dateien und Grenzen siehe uebergaben/PORTAL-016_NAS_UEBERNAHME.md. CI-Budget geschützt, keine Actions gestartet.

### PORTAL-017 – Additive Foto-Eintragsreferenz und GPS-Overrides
- Stabile btbEntryId ausschließlich in BTB-file_links.link_metadata, auf existierenden nicht gelöschten Eintrag desselben BTB validiert. UploadComplete bekommt optional btb_entry_id. Foto-PATCH photo_metadata.btbEntryId setzt UUID oder null zum Lösen. Keine globale Dateibindung und keine Zuordnung anhand gekürzter Texte. Abschnitt/Firma/Unterbaustelle/Kurztext werden für gebundene Fotos aus Eintrag abgeleitet/bei Änderung innerhalb bestehender BTB-Transaktion aktualisiert. KM/GPS bleiben eigene editierbare Fotoangaben.
- Foto-Overrides lat/lon mit endlichen gültigen Bereichen; explizites null löscht. Effektive Koordinaten müssen BTB, Galerie und PDF gleich nutzen. Kein globales Umschreiben der Datei bei BTB-lokaler Korrektur. EXIF-GPS und Aufnahmezeit vor Canvas-Verkleinerung lesen; max2.000.000Bytes bleibt. Fehlende Daten bleiben leer. Android behält bestehende API-Felder; neue Referenz/Anzeige/EXIF-Roundtrip später prüfen, kein app/** im Serverbranch.
- Schicht: work_date ist Starttag, started_at/ended_at speichern tatsächliche Zeitpunkte. Portal leitet Enddatum bei Ende<Beginn automatisch ab. Gleichzeitige Uhrzeiten nicht still als24h interpretieren. Anzeige für Nacht 18/19.10.26, Monats-/Jahreswechsel eindeutig. Bestehende mehrtägige Berichte nicht still überschreiben.

### PORTAL-017 – Serverimplementierung und Testgrenzen
- Referenz nur BTB-Link; vollständiger GPS-Override-Roundtrip für Portal/Galerie/PDF, Bindungskontext folgt Entry-/Firmenänderung, Entkopplung/Upload validiert. Neue Fotos EXIF vor Verkleinerung, kein Originalupload; fehlender EXIF-Zeitzonenoffset als Europe/Berlin interpretiert. Nachtschicht Enddatum automatisch, PDF-Datum als Bereich. Lokal153/152/151 und PDF-PNG PASS, kein DB-/NAS-/Android-E2E und kein APK. Älterer Android-Foto-Kontextpatch kann gebundenen Eintragskontext nicht unabhängig umhängen; neue Referenz/GPS-Overrides/Schichtanzeige im Android-Projekt später prüfen. Details und elf NAS-Dateien siehe uebergaben/PORTAL-017_NAS_UEBERNAHME.md.


### PORTAL-019 – Additive Portal-Katalogsuche und optionale Erstellnamen
WorksiteCreate.name/NodeCreate.name optional/leer zulässig; serverseitige Ableitung, fachlich leere Erstellung HTTP400. Listenresponse zusätzlich version/can_delete ohne vorhandene Felder zu entfernen. Bestehender DELETE-Vertrag und Rechte unverändert, Portal verwendet base_version; TRASHED/Tombstone bleiben für Android maßgeblich. Neuer authentifizierter GET /catalog/lookup?q liefert routes/stations mit data_available und warnings, Bahnhofreferenzen öffentliches Fachwissen, Vermessungssicht weiterhin über bestehenden Userfilter. Unterbaustellen-Payload station_code zusätzlich, station_name bleibt lesbarer Name. Kein app/**, Android-Roundtrip separat offen.


## PORTAL-020 – Bestand je Strecke/Bahnhof (18.10.2026)
Benutzer genehmigt aufklappbare Bestandsübersicht: getrennte echte KM-Abschnitte (keine erfundene Verbindung über Lücken), ohne KM/ohne Geometrie kenntlich, Bahnhof mit Gleisen/Weichen/WVB, Objektsuche, Objekt übernehmen und KM nur ausdrücklich übernehmen. Gemeinsame GLOBAL-Fachdaten firmenübergreifend für jeden angemeldeten Nutzer; firmeneigene Baustellen/BTBs/Fotos nicht freigeben. Bestehende globale Katalog-/Vermessungsdaten wiederverwenden, keine Daten umklassifizieren. Erst Portal/Server, Android unverändert.


### PORTAL-020 – Gemeinsame Bestandsübersicht umgesetzt (18.10.2026)
- Authentifizierter read-only GET /api/v1/catalog/inventory?route_number=… oder station_code=…; whitelisted fachliche Ausgabe aus gemeinsamer GLOBAL-Vermessung. Alle angemeldeten Nutzer erhalten denselben gemeinsamen Bestand; kein Admin-Impersonieren/keine Freigabe firmenprivater Daten. Lookup ebenfalls gemeinsame GLOBAL-Vermessung statt firmenabhängiger data_available-Anzeige. Allgemeiner Katalog/Bahnhofreferenzen bleiben erhalten. Keine Daten umklassifiziert.
- Bestehende availability/_axis_km_summary für echte KM-Qualität wiederverwendet. Segmente einzeln, keine Min/Max-Zusammenfassung über Lücken; AUTOMATISCH/MANUELL mit endlichen Grenzen, FEHLT/UNPLAUSIBEL ohne übernehmbare KM. Achsen ohne aktive Geometrie separat kenntlich. Weitere Datenbestände GVP/KF/KV etc. einzeln mit gespeicherten Dateigrenzen, kein Ersatz durch geometrische Stationswerte. Antwort enthält keine Rohgeometrie/Dateipfade/Urheber-/Firmenmetadaten.
- Gemeinsamer aufklappbarer Bestand bei Strecken-/Bahnhofsauswahl in Haupt- und Unterbaustellenformular. Lädt erst beim Öffnen; Mengen nach Datenart, Objektsuche, zunächst40 Treffer/Weitere anzeigen. Gleis/Weiche/WVB eindeutig über object_key vor track_key auswählbar, KM getrennt nur per Knopf übernehmen. Reset/Eingabe verwerfen veraltete Antworten; Fehler sichtbar/erneutes Öffnen erlaubt Retry. Unterbaustelle bindet Gleis/KM-Zielfelder, Hauptformular reine Übersicht ohne passende Zielfelder.
- Lokal tatsächlicher PASS: neuer Check155 Python/Node (echte Produktionsfunktionen mit DB/API/DOM-Doubles: getrennte/umgedrehte/Null-KM/ungültige Bereiche, Whitelist, GLOBAL-SQL und Qualitätsaufruf, Mengen, Auth-Dependency, lazy loading, Auswahl, Nullkilometer, keine stillen KM-Änderungen, Filter, Race). Check154 Python/Node PASS;154-Erwartung auf expliziten gemeinsamen Suchpool angepasst. Check151 Baum/KM PASS. Python/JS Syntax PASS. Kein echter PostgreSQL-/Chromium-/NAS-E2E, keine Actions/Android-Builds. Excel bekannter Defekt unverändert.
- Vier Runtime-Dateien: tbserver/catalog.py, neu tbserver/catalog_inventory.py, templates/v1_catalog_lookup.html, templates/v1_worksite.html. Keine Migration. Übergabe/Praxistests: uebergaben/PORTAL-020_NAS_UEBERNAHME.md. AndroidHEAD6cec344 unverändert, PR6 bleibt Draft.


## PORTAL-021 – Freie Strecke ohne Bahnhof, Gleis/Weiche eindeutig (19.10.2026)
Benutzer meldet Screenshot: Freie Strecke zeigt alten Bahnhof Pasing und mehrdeutiges Feld Gleis/Weiche mit27. Beauftragt Korrektur: Bahnhof bei FREIE_STRECKE ausblenden/leeren, alte Suche/Bestandsantwort invalidieren, Bahnhof auch serverseitig beim Speichern freier Strecke ausschließen. Eindeutige Objektart Gleis/Weiche/Weichenverbindung und Nummer; Bestandsübernahme setzt Art automatisch, Bestandsschaltfläche passend beschriften. Bestehende track_key/track_or_switch lesbar kompatibel (Weiche/WVB mit Präfix), additives object_type im Payload. Keine automatische Reparatur alter gespeicherter Baustellen ohne Bearbeitung.


### PORTAL-021 – Freie Strecke und Objektart korrigiert (19.10.2026)
- Screenshotfehler bestätigt: Art Freie Strecke ließ alten Bahnhof Pasing und unklare27 stehen. Formular jetzt Bahnhofbereich verborgen+disabled bei FREIE_STRECKE, Bahnhof/Code geleert, Such-/Bestandsantworten invalidiert. Bestandscontainer außerhalb verborgenem Bahnhofblock, Streckenbestand bleibt erreichbar. Bei Artwechsel Objektwahl/KM-Felder zurückgesetzt, keine Übernahme aus vorherigem Bahnhof.
- Eindeutige Objektart Gleis/Weiche/Weichenverbindung plus passend beschriftetes Nummernfeld. Bestandsknopf je Typ Gleis übernehmen/Weiche übernehmen/Weichenverbindung übernehmen; Übernahme setzt Objektart automatisch. Payload object_type additiv; bisherige track_key/track_or_switch für Gleis unverändert, Weiche W27/WVB-Präfix lesbar. Bearbeiten-Prompt nennt explizite Beispiele Gl19/W117/WVB1, freie Strecke bietet keinen Bahnhofsprompt.
- Serverseitig _node_payload entfernt station_name/station_code bei Erstellung UND jedem PATCH freier Strecke, auch bei Name-only-PATCH; andere Felder bleiben erhalten. Namensableitung nutzt bereinigte Felder. Keine automatische Bulk-Reparatur alter Daten/keine Baum- oder Dateilöschung. Kein app/**.
- Lokal tatsächlich PASS:156 Python/Node (Produktionsfunktionen mit Cursor-/DOM-Doubles, freie Strecke verborgen/geleert, stale Payloadguard, Objektart/Präfix, echte Name-only-PATCH-Normalisierung);154 Python/Node und155 Python/Node weiterhin PASS,151 Baum/KM PASS, Python/JS Syntax PASS.154/155 Erwartungen auf neue Namensnormalisierung/Typauswahl aktualisiert. Kein echter DB-/Browser-/NAS-E2E, keine Actions/APK gestartet; Excel bekannter Defekt unverändert.
- Drei Runtime-Dateien: tbserver/worksites.py, templates/v1_worksite.html, templates/v1_catalog_lookup.html. Genaue Übergabe uebergaben/PORTAL-021_NAS_UEBERNAHME.md. API-Neustart+StrgF5; keine Migration, nichts löschen. PORTAL-020 vorausgesetzt.

## BTB-AUTO-016 – bestätigte Festlegung vom 22.10.2026

Status: **FACHLICH BESTÄTIGT / IMPLEMENTIERUNG UND E2E OFFEN**. Benutzer hat die Empfehlung mit „Okay“ bestätigt. Gilt gemeinsam für SERVER/Portal und ANDROID.

### Verbindliches Verhalten
- Ein laufender BTB endet automatisch nach **16 tatsächlich vergangenen Stunden ab dem eingetragenen BTB-Beginn**. Beispiel: 07:00 → 23:00; 18:00 → Folgetag 10:00 (ohne Zeitumstellung). Maßgeblich ist der Beginn samt Datum/Zeitzone, nicht App-Öffnung oder letzte Bearbeitung. Zeitumstellung über absolute Zeitpunkte behandeln.
- Früherer manueller Abschluss durch den Haupt-TB bleibt möglich. Es wird kein neuer BTB automatisch erzeugt; dieselbe stabile btbId bleibt erhalten.
- Zum Fristende endet die laufende Erfassung. Bereits vorhandene, noch nicht übertragene Einträge/Fotos werden verlustfrei nachsynchronisiert. **Erst nach bestätigter Vollständigkeit entstehen endgültiges Abschluss-PDF und unveränderlicher Originalstand.** Ein Server kann unbekannte Offline-Outboxes nicht als leer voraussetzen; bloßer Zeitablauf, letzte Verbindung oder Teilnehmerstatus genügen nicht als Vollständigkeitsnachweis.
- Solange Übertragungen/Bestätigungen fehlen: sichtbarer Zustand sinngemäß „Erfassung beendet – Synchronisierung ausstehend“, kein vorgetäuschter endgültiger Abschluss. Keine automatische Verwerfung von Änderungen oder stilles Abschließen nur des zufällig vorhandenen Serverstands. Kameraoriginale bleiben lokal und blockieren nicht; erforderlich sind Fachtext, Bildmetadaten und kleine Sync-Versionen gemäß bestehendem Vertrag.
- Server koordiniert Frist und gemeinsamen Abschluss unabhängig von geöffneter App. Android/Portal übernehmen denselben kanonischen Zustand. Android berücksichtigt die Frist offline; bei ausgeschaltetem/beendetem Gerät spätestens beim nächsten Start, vor weiterer Erfassung. Keine Zusage minutengenauer Android-Hintergrundausführung bei ausgeschaltetem Handy.
- Anzeige am BTB: „Automatischer Abschluss am … um …“; bei ausstehenden Daten konkreten Synchronisierungsbedarf anzeigen.
- Bewusste spätere Nachbearbeitung derselben btbId darf nicht sofort durch die alte Frist erneut geschlossen werden. Bestehende Original-/PDF-/Änderungsnachweise bleiben geschützt.
- Die automatische Systemaktion ist eine ausdrücklich genehmigte Ergänzung zur bisherigen Regel „nur Haupt-TB finalisiert“. Helfer erhalten dadurch kein manuelles Finalisierungsrecht und keinen zusätzlichen PDF-Zugriff; „Schicht Ende“ bleibt eigener Teilnehmerstatus.

### Zuständigkeiten und Abnahme
- **SERVER:** Fristberechnung, persistenter nachvollziehbarer Erfassungsstopp, Worker/Neustart-Nachholung, deduplizierter Abschluss, sicherer Nachlauf für ausstehende Änderungen, Vollständigkeitsnachweis und Change-Feed/Portal-Anzeige. Vor Umsetzung konkrete additive API-/Capability-/Status- und Quittierungsfelder mit ANDROID festlegen; hier sind noch keine neuen Endpunkte/Feldnamen freigegeben oder implementiert.
- **ANDROID:** Frist-/Statusanzeige, Offline-Erfassungsstopp und Startprüfung, dauerhafte Outbox samt Abschluss-/Vollständigkeitsbestätigung, sichere Übernahme des Serverzustands. Ältere Clients ohne passenden Vertrag dürfen weder Daten verlieren noch fälschlich als vollständig gelten.
- Vor Implementierung Grenzfälle konkretisieren: bewusste Änderung des Beginns, fehlender Beginn, historische bereits überfällige Bestands-BTBs und nicht mehr erreichbare Geräte. Keine rückwirkende Massenfinalisierung oder automatische Datenverwerfung aus dieser Festlegung ableiten.
- **GEMEINSAMER E2E OFFEN:** 07:00/23:00, Nacht/Datumswechsel/Zeitumstellung, manueller früher Abschluss, offline Helfer mit ausstehenden Fotos/Einträgen, ausgeschaltetes Handy, Server-Neustart, doppelte Worker-/Syncversuche, Paralleländerung am Fristende, Altclient und bewusste Nachbearbeitung. Pixel/Poco, echtes PDF/Original und NAS-Runtime getrennt nachweisen.
- Diese Änderung dokumentiert die Anforderung. Kein Runtimecode, keine Migration, APK, Veröffentlichung oder NAS-Änderung in diesem Auftrag.


## PORTAL-022 – Anzeige, kein Transportvertrag (23.10.2026)
Portal/BTB/Galerie ergänzen bei Kurznamen die vorhandenen station_name/station_code und track_or_switch/track_key mit object_type. Bestehende node IDs, client_node_key/worksiteNodeKey, gespeicherte Namen und Payloads unverändert. Fehlende KM sind kein Nullkilometer; echte numerische 0 bleibt sichtbar. Kein neuer API-Endpunkt/Feld, keine Syncänderung, kein app/**. Android bleibt eigener Auftrag. Details/Tests: 04_SERVER_STATUS.md, Übergabe PORTAL-022.
