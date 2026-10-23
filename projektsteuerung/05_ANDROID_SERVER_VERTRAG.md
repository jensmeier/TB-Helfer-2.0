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


## Integrationsvertrag 27.09.2026 – Fotos / BTB-Abschluss / Wetter

### Foto-Sync
- Das **Volloriginal einer Handyaufnahme bleibt ausschließlich auf dem Aufnahme-Handy** als lokales Original.
- Android→Server überträgt für normalen Sync nur eine separate verkleinerte/komprimierte Sync-Version plus strukturierte Metadaten; kein Upload des Handy-Volloriginals.
- Server/Portal dürfen aus der empfangenen Sync-Version preview/thumb erzeugen.
- Server→Android und Handy→anderes Handy verwenden preview/thumb bzw. eine kleine Sync-Version; automatischer Volloriginal-Download ist unzulässig.
- Portal-Uploads dürfen serverseitig ihre empfangene Quelldatei behalten; mobile Clients erhalten weiterhin kleine Renditions.
- Foto-Metadatenänderungen (Firma, BTB-Punkt, worksiteNodeKey usw.) ändern **nicht** Originaldatei, Aufnahmezeit oder GPS.

### BTB-Abschluss / Finalisierung
- Der **Server-Lifecycle ist kanonisch**.
- Abschluss am verantwortlichen Android-Hauptgerät muss den Server finalisieren; Portal und andere Teamgeräte müssen den resultierenden FINALIZED-/nicht-aktiven Zustand automatisch über normalen Live-/Delta-Sync übernehmen.
- Zulässiger Abschluss im Portal muss denselben Serverzustand erzeugen; Android übernimmt ihn automatisch über Change-Feed/Delta und darf keinen widersprechenden lokalen Active-/Abschlusszustand behalten.
- FINALIZED ist anschließend read-only gemäß Rollenvertrag.
- Lokale PDF-/Baseline-Marker sind niemals eine zweite kanonische Abschlussquelle.

### Wetter
- BTB-Wetterdaten führen mindestens Temperatur und Niederschlagsmenge.
- tempC und niederschlagMm/äquivalentes kanonisches Feld müssen Android→Server→Portal und Portal/Server→Android verlustfrei über den BTB-Header bzw. Wetter-Slots laufen.
- Bekannter Niederschlag 0.0 mm bedeutet **kein Niederschlag** und bleibt numerisch 0.0; null bedeutet **unbekannt/nicht vorhanden**.
- Portal und Android sollen die Regen-/Niederschlagsmenge mit Einheit mm anzeigen, wenn der Wert bekannt ist.

Status: **VERTRAG GESICHERT / QUELL- UND RUNTIME-ABGLEICH OFFEN**.


## Android-/Server-Abgleich 20.10.2026 – AND-SYNC-AUDIT-20261020
Status: **QUELLABGLEICH ABGESCHLOSSEN / IMPLEMENTIERUNG OFFEN / BUILD UND GERÄTE-E2E OFFEN**.

Auftrag: nach Statusauskunft vom Nutzer mit „okay“ bestätigter gezielter Vertragsabgleich; keine pauschale Implementierungs- oder Buildfreigabe daraus ableiten.
Geprüfte Ausgangsstände: ANDROID `6cec34423e34315c581b743668724abe17daa333`, SERVER `0f82b42da7412a138bf99b6f80b93031e3b5373e`. PR11 offen, kein Draft, PR6 offen/Draft; beide melden mergeable=false. Technische Androidversion unverändert 1.3.26/200076. Keine zusätzliche Versionszeile auf der Startseite.

| Priorität | Befund / Quellbeleg | Nächster Androidblock |
|---|---|---|
| P0 | Server files.py: PHOTO_MAX_BYTES=2_000_000. MobileImageSync.kt: 1920px/JPEG82, aber keine Byteobergrenze; auch Cachetreffer nur auf Länge>0 geprüft. Damit fehlt eine garantierte Einhaltung, kein konkreter Live-Uploadfehler behauptet. | Wiederholte Komprimierung/Verkleinerung mit Prüfung <=2.000.000Bytes, Cache ebenfalls prüfen; kein Originalfallback; Original erhalten. |
| P1 | SectionItem in BautagebuchModels.kt und Serializer/Parser in BautagebuchSpeicher.kt besitzen uhrzeit, aber kein uhrzeitBis. Neue Portal-Endzeiten werden dadurch nicht vollständig lokal modelliert/gespeichert. | Ende additiv durch Modell, Speicher, Editor, Sync und PDF führen; Alt-Einzelzeiten erhalten; Rundlauf testen. |
| P1 | Firmenzeitbindungen personalZeitbindung/maschinenZeitbindung fehlen im geprüften Modell/Speicher. Server führt REPORT/MANUAL ein. | Feld-Erhalt und automatische Zeitübernahme einschließlich manueller Ausnahmen gezielt prüfen/ergänzen; kein stilles Überschreiben. |
| P1 | Server UploadComplete hat btb_entry_id; Android Server1FileApi.uploadLinkedFile hat dieses Feld nicht. BtbTeamMediaSync hat keine btbEntryId-Behandlung. Serververtrag PORTAL-017: gebundener Fotokontext folgt dem Eintrag. | Stabile Server-Entry-ID gegen vorhandenen clientEntryKey/Slot abbilden; Foto-Bindung und Entkopplung umsetzen, keine Textheuristik. |
| P1 | FotoEntity hat keine GPS-Genauigkeit, BtbTeamMediaSync sendet gpsAccuracyM=null. Download mergedPhotoMetadata überspringt explizite null-Werte und ergänzt Legacy; Import ersetzt fehlende Koordinaten durch 0.0 und fehlende Zeit notfalls durch jetzt. Das passt nicht zuverlässig zu explizitem GPS-Löschen/fehlenden Metadaten des neuen Vertrags. | Effektive BTB-Link-GPS inklusive null-Semantik, Aufnahmezeit und Genauigkeit prüfen und verlustfrei modellieren; keine erfundenen Aufnahmedaten. Room-Migration erst nach gesondertem Implementierungsblock. |
| P2 | Nachtschicht-Grundlage bereits vorhanden: endeDatumIso samt abgeleitetem Enddatum im Modell; Server1BtbApi übernimmt Datum aus ended_at und sendet endeDatumIso zurück. | Kein kompletter Neubau. Portal->Android->Portal/PDF über Mitternacht, Monats-/Jahreswechsel und Zeitzone testen. |
| P2 | SyncCatalogApi nutzt GET /api/v1/catalog; in diesem Client keine Anbindung an /catalog/lookup oder /catalog/inventory. WorksiteNodeApi transportiert generisches payload und track_or_switch. | Neue Bestandsübersicht/Objektart als eigener UI-/Vertragsblock; generischen Payloadtransport nicht pauschal als Datenverlust werten. |

Prüfumfang: Live-Einstieg/Projektregeln/Steuerungsdateien, aktueller Serververtrag einschließlich PORTAL-012/016/017/019/020/021, Android-Gitbaum, MobileImageSync, Server1BtbApi, Server1FileApi, BtbTeamMediaSync, CollabLocalBridge, FotoEntity, WorksiteNodeApi, WorksiteStructureSync, WorksiteTrackDataSync, SyncCatalogApi, BautagebuchModels/BautagebuchSpeicher sowie Server files.py.
Grenzen: reine Quellprüfung; kein Gradle, Unit-Test, Lint, Emulator, API-/DB-/NAS- oder Geräte-E2E. Keine neue Source-Test-PASS-Aussage. HEAD-Abfrage lieferte keine PR-getriggerten Workflow-Runs; das ist kein Nachweis über sämtliche historischen/push/manuellen Runs. Direkter Git-Transport hier 403, GitHub-Connector lesbar. Keine Runtime-/Server-/Produktivdatenänderung und keine Versionsanhebung.
Bestehende Bestätigungen BTB-SYNC-002, BTB-UI-001, UPD-001 und PORTAL-LIVE-001 bleiben geschützt. Alte September-Kopfstände sind historisch; dieser datierte Abgleich ersetzt keine Geräteabnahme.


## AND-ENTRY-END – Quellstand 20.10.2026
Status: **QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / KOTLIN-UNIT-TESTS ANGELEGT, NICHT AUSGEFÜHRT / BUILD UND E2E OFFEN**.

- Auftrag vor Implementierung: c42bc151817f431352e98029ba907950feb1af97; Schutzentscheidung zu Roh-JSON/PDF-Hash: fc98a8e88de51d95343878de2e8220777757906f. Ausgangscode fcd2985a84a3d51fe44331437ed428223fba327a; Server unverändert0f82b42da7412a138bf99b6f80b93031e3b5373e.
- SectionItem hat additives nullable uhrzeitBis am Ende der Parameterliste; Beginn bleibt uhrzeit. BtbEintragszeit liest getrennte und historische zusammengefasste Zeiten; gleicher Beginn/Ende wird nicht als24h interpretiert, Nachtspanne nicht umsortiert. Auch fehlender Beginn bei vorhandenem Ende bleibt transportierbar.
- BautagebuchSpeicher liest/schreibt Endzeit und unterscheidet fehlendes Ende von explizitem null. Bei semantisch unveränderten Zeiten bleibt vorhandene lokale Zeit-JSON-Form bestehen, damit allein die Formatmigration keinen alten Abschluss-Hash ändert. Zuordnung über stabilen clientEntryKey, ID-loser Legacy-Fallback nur gleicher Abschnitt/Index und Text/Firma/Node/KM. Gleicher Schutz direkt vor Server-Snapshot-Write innerhalb des bestehenden BTB-Mutationslocks. Echte Zeitänderungen bleiben unverändert als Änderung erkennbar; Hashalgorithmus/Originaldateien/Dirty-Reihenfolge nicht verändert.
- Server1BtbApi normalisiert ausgehende Abschnittspayloads für Punkt4–8 auf uhrzeit/uhrzeitBis; fehlendes gewünschtes Ende explizit JSONObject.NULL, damit bewusstes Löschen durch den Altclient-Feldschutz nicht unterdrückt wird. Bestehende Entry-ID/Slot/Node/Firma bleiben erhalten.
- Bestehender Zeitspannen-Editor wird weiterverwendet; Anzeige, PDF einschließlich Originalvergleich, Druckmodell, Text-/ZIP-Export verwenden gemeinsame Zeitdarstellung.
- Tatsächlich ausgeführt:14/14 Buildgenerator-Anker PASS;14 Fälle mit den direkt aus den produktiven Kotlin-Editoren extrahierten Java-Regexen auf JVM17 PASS (Einzelzeit, Spanne, Nacht, Legacy-Klammern, nur Ende, gleiche Zeiten, Text ohne Zeit). Statische Verdrahtung für alle fünf Abschnitte, explizites null und Save-/Apply-Schutz PASS.
- Sechs JUnit-Regressionsfälle in BtbEintragszeitTest.kt ergänzt (Legacy/modern, explizites null, Vorrang, Nacht/gleich, leer/Einzelzeit/nur Ende, Identität). **Nicht ausgeführt**: Kotlin-/JUnit-Lauf, Gradle-Build, Lint, Android-UI, Original-/Abschluss-PDF-Runtime, API-/NAS-/Pixel/Poco-E2E. Java-Regexprüfung ist kein Kotlin-/Android-Build. Kontext7-Abfrage zu LocalTime ohne Treffer; keine neue Bibliothek eingeführt.
- Nächste Prüfung bei verfügbarer Buildumgebung: Unit-Tests und Gradle/Lint, danach Portal-Endzeit->Android öffnen/ohne Änderung speichern->Portal/PDF; Ende ändern und löschen;22:00–06:00; bestehendes Abschluss-PDF bei reinem Formatübergang erhalten, echte Änderung ungültig/rot, Foto-/Entry-Identität unverändert; Pixel und Poco.
- Keine server/**-Datei, kein APK, keine Versionsanhebung; technische Version weiterhin1.3.26/200076. Nächste auslieferbare APK benötigt höheren Build und passende Release-MD. Keine Actions gestartet ([skip ci]).
- Noch offen: Firmenzeitbindung REPORT/MANUAL, Foto-Eintragsbindung/GPS, Katalog-Bestandsübersicht, gemeinsame Geräte-E2Es. Vorheriger AND-IMG-2MB-Block source-seitig gesichert; Bitmap-/Upload-E2E bleibt offen.


## AND-FIRM-TIME – Quellstand 20.10.2026
Status: **QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / UNIT-TESTS ANGELEGT, NICHT AUSGEFÜHRT / BUILD UND E2E OFFEN**.
- Auftrag38d19fb011fd0e04b17f973e96e468708a955c55, ergänzende Schutzentscheidung e5a2195ca9a05cddf6a8291184bc78c9713056d9. Ausgangs-Android841d18c0d3cdc9b96860270f05a10161cdbd7987; Server0f82b42da7412a138bf99b6f80b93031e3b5373e unverändert.
- FirmaBlock/Speicher erhalten personalZeitbindung und maschinenZeitbindung unabhängig. Null-Zeitwerte werden als null statt Zeichenfolge gelesen. Bestehender generischer Firmenpayload in Server1BtbApi transportiert Flags in beide Richtungen; kein neuer Serverendpunkt.
- FirmenZeitbindung: REPORT folgt Bericht, MANUAL bleibt bestehen; ohne Flag nur leere bzw. je vorhandenem Wert passende bisherige Berichtzeiten automatisch. Bekannte fremde/individuelle Zeiten und unbekannte zukünftige Kennzeichen werden konservativ erhalten. Nacht wird nicht umsortiert. Unveränderte Werte erhalten ihr Legacy-Flagformat.
- Beide PM-Einstiege erhalten geladenen Modus und leere Werte; neue Firmen starten mit Berichtzeiten statt Jetzt/+10h. Bewusste Firmenzeitwahl ergibt REPORT bei exakt dem Berichtspaar, sonst MANUAL. Laden/programmgesteuertes Nachführen unter pmApplying/pmSyncing-Guard, kein versehentliches Umstellen von MANUAL.
- Datum-/Schicht-Dialog führt automatische Firmenzeiten nach, auch beim Entfernen der Berichtzeiten und nach Ziel-Schicht-Laden. Vorhandener Autosave speichert Bericht+Firmenstand zusammen. Vortags-/Neuberichtsübernahme übernimmt passende automatische Zeiten, bewahrt manuelle Ausnahmen. AutoLinked-Maschinenplatzhalter bleiben ohne eigenen Inhalt leer.
- PM-Sammlung bewahrt vorhandene geladene Firma-ID sowie Teilsektionskennzeichen; Namens-Zusammenführung und bisherige ID-Regel für neue Firmen bleiben erhalten. Reine äquivalente Flag-Nachlieferung bei identischem Zeitpaar bewahrt bestehende JSON-Form (lokaler Save und Snapshot-Write); expliziter MANUAL<->REPORT-Wechsel bleibt echte Entscheidung. Keine Änderung an Originaldateien, Hashalgorithmus, Finalize oder Dirty-Reihenfolge.
- Tatsächlich ausgeführt: gezielte statische Modell-/Speicher-/UI-/Sync-/Guard-/Identitätsprüfungen PASS; bestehende14/14 Generator-Anker PASS,14/14 produktive JVM-Regexprüfungen PASS. Acht JUnit-Fälle für REPORT/MANUAL/Legacy/Null/Nacht/Identität/Unbekannt angelegt, **nicht ausgeführt**. Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/Server-E2E. Context7 abgefragt; keine neue Bibliothek eingeführt.
- Praktische Folgetests: neuer Bericht07–16 mit Personal REPORT und Maschinen MANUAL; Bericht08–17 => nur Personal folgt. MANUAL mit zufällig gleichem alten Zeitpaar bleibt unverändert. Nacht22–06, Entfernen, Vortag, Portal->Android->Portal, Öffnen/Speichern ohne Änderung (Identität/Flags/PDF gültig), echte Änderung/Moduswechsel, Pixel/Poco. Kotlin-Tests/Gradle/Lint zuerst bei verfügbarer Toolchain.
- Keine server/**-Änderung, kein APK, keine Versionsanhebung, keine Actions gestartet ([skip ci]); Version bleibt1.3.26/200076. Für nächste auslieferbare APK höherer versionCode + passende Release-MD.
- Nächste offene Integration: Foto-Eintragsbindung/GPS; danach Katalogübersicht. Vorherige Bild-/Endzeitblöcke bleiben SOURCE-fertig mit offenem Build und Geräte-E2E.

## AND-PHOTO-LINK – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- UploadComplete übergibt btb_entry_id; bekannte Links erhalten btbEntryId über bestehenden PATCH mit link_version. Entry-Kontext wird serverseitig abgeleitet; GPS/KM bleiben eigenständig. Vollständiges photo_metadata verdrängt Legacy-Metadaten, auch bei fehlenden/gelöschten GPS-Werten. Server-Entry-ID-Map bleibt technischer Snapshotzustand und wird vor lokalem BTB-JSON-Write entfernt. Keine server/**-Änderung.
- Stabile clientEntryKey-Zuordnung statt Text-/GPS-Abgleich; bestehende und eigene Fotos übernehmen wirksame Servermetadaten über eine dauerhafte BTB-Korrekturschicht. GPS/Zeit/Genauigkeit können unbekannt bleiben. PDF, Detailansicht und Export verwenden effektive Werte. Originale bleiben erhalten.
- Bestätigte Bindungs-/Kontext-/Textänderungen werden nur bei unverändertem Pendingwert quittiert; spätere lokale Eingaben bleiben erhalten. Unbekannte Unterbaustellen werden nicht still auf Hauptbaustelle umgebogen. Read-only/Lifecycle-/Outbox-/Originalschutz bleibt auf bestehenden Pfaden.
- Grenzen: Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/Server-E2E-Nachweis. Kein APK/Versionssprung, keine Actions gestartet ([skip ci]), kein Servercode geändert. Technischer Stand 1.3.26/200076. Original-EXIF vor Galerie-Konvertierung gelesen; Context7 zu AndroidX ExifInterface ausgewertet, keine neue Bibliothek.


## AND-CATALOG-INVENTORY – Android-Anbindung 20.10.2026
GET /api/v1/catalog/inventory?route_number=... oder ?station_code=... liefert scope=GLOBAL, rows, counts und item_count. Rows enthalten id/kind/label/route_number/station_code/station_name/track_key/object_key/status/selectable und segments mit status/km_from_m/km_to_m. Android zeigt den gemeinsamen Bestand schreibgeschützt; selectable ist keine automatische Download-/Baustellenzuweisung. KM nur bei belastbarer Abschnittsqualität und beiden endlichen Grenzen anzeigen, Lücken erhalten. 404/405/501 => nicht unterstützt; 401/403 => Sitzung prüfen; keine Mutation und kein Serverumbau.

## AND-CATALOG-INVENTORY – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- PORTAL-020-Vertrag aus aktuellem Server-HEAD geprüft: GET /api/v1/catalog/inventory mit Geräten-Bearer, URL-kodierter route_number/station_code. GLOBAL und rows-Form werden geprüft; Mengen aus gelieferten Rows. Einzelne Segmente bleiben getrennt; nur AUTOMATISCH/MANUELL/KM_VORHANDEN mit beiden endlichen Grenzen zeigen numerische KM (Meter/1000). UNPLAUSIBEL/FEHLT/fehlende Geometrie werden kenntlich. 404/405/501 getrennt von 401/403 und Ladefehler, kein Fallback auf leeren Bestand. Keine Serverdatei oder API geändert.
- Bestand rein lesend aus gemeinsamem GLOBAL-Pool, eindeutige Kennungen, einzelne belastbare KM-Abschnitte; kein Zusammenziehen über Lücken. Fehler bleiben Fehler, kein falscher Leerbestand. Bahnhof ohne Kürzel wird nicht über Namen geraten. Keine Original-/Outbox-/Room-/Lifecycle-Fachänderung.
- Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/NAS-E2E-Lauf; adb/Gradle/Kotlin-Toolchain hier nicht vorhanden. Context7 zu AndroidX Lifecycle/DialogFragment ausgewertet. Keine neue Bibliothek, keine server/**-Änderung, keine Actions gestartet ([skip ci]), keine APK/Versionsänderung. Technischer Stand bleibt 1.3.26/200076; spätere APK braucht höheren Build und passende Release-MD.


## AND-CATALOG-FORM – Vertragsentscheidung 20.10.2026
Android nutzt bestehende GET /catalog/lookup?q=... und /catalog/inventory mit Geräte-Bearer. Bestandsübernahme ist ausdrückliche lokale Formularentscheidung; keine Servermutation durch Klick auf Objekt/KM. Knotenmutation bleibt bestehender WorksiteNodeApi-/Outboxpfad, object_type additiv und track_or_switch weiterhin kompatibel. W/Weiche und WVB/WV/Weichenverbindung eindeutig normalisieren. Freie Strecke enthält keine Bahnhofdaten. Gleichwertige Altpräfixe/fehlendes additives object_type nicht allein als Änderung werten; unbekannte Werte konservativ erhalten.

## AND-CATALOG-FORM – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Bestehende Lookup-/Inventory-GETs und WorksiteNodeApi/Outbox wiederverwendet. Inventar object_key vor track_key; selectable nur für bekannte Gleis/Weiche/WVB-Objektwahl, KM separat pro endlichem belastbarem Segment. object_type wird additiv übertragen/ausgelesen und lokal durch kompatible Kennung erhalten. Portal-Editor erkennt WVB-Präfix, deshalb echte Uploadänderungen WVB statt WV; semantisch gleiche Legacy-Aliase/fehlendes additives Feld lösen allein keinen neuen Sync-Auftrag aus. Unbekannte künftige Arten bei unveränderter Kennung konservativ bewahrt. Freie Strecke liest Bahnhof nicht ein und sendet station_name/station_code=null; alte Bahnhofreste allein erzeugen keinen Bereinigungs-PATCH.
- Stabile Knoten-/Objektidentität, keine Originaldatei-/Room-Schemaänderung, keine neue Bibliothek und kein Serverumbau. Keine automatische Bulk-Reparatur alter Baustellen. Vorhandene Fach-/Download-/Offlinepfade bleiben Grundlage.
- Kein Kotlin-/Gradle-/Lint-/Emulator-/Geräte-/NAS-E2E-Nachweis; diese Toolchain hier nicht verfügbar. Context7 zu FragmentResult/Lifecycle geprüft. Keine Actions gestartet ([skip ci]), keine APK und keine Versionsänderung. Technischer Stand weiterhin 1.3.26/200076; neue APK nur mit höherem Build und zugehöriger Release-MD.

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

## AND-AUDIT-023 – Clientkorrekturen / verbleibende Vertragslücke 23.10.2026
- Geräteauthentifizierung und fachliches403 bleiben getrennt; fehlendes Ressourcenrecht widerruft nicht allein die Geräteaktivierung. Bekannte widerrufene/deaktivierte Geräte-/Benutzerzustände bleiben gesperrt.
- Noch nicht übertragene Helferänderungen bei FINISHED/NOT_PARTICIPATING bleiben lokal vorgemerkt, keine Erfolgsquittierung ohne Upload und keine automatische Schichtfortsetzung. Historischen FINALIZED-Schutz nicht verändert.
- /catalog ist der ergänzbare Katalog; lookup ergänzt Referenz-/GLOBAL-Vermessungssuche, inventory Details. Neue Android-Suche nutzt nur bestehende Endpunkte, keine ID-Raterei und keine automatische Datenanlage.
- Der Serverstand a471996610a27b2d718f9835bf9c07930f29858f verlangt bei aktivem TB_RECOVERY_ID den Header X-TB-Recovery-ID nach kontrolliertem Rebase. Android unterstützt den neuen Wiederherstellungsablauf noch nicht; niemals automatisch den Header nachsenden. Gemeinsame Spezifikation/Abnahme und NAS-Aktivierungszustand offen, Servercode unverändert.
- Build-/Geräte-E2E für200078 offen; Details: uebergaben/ANDROID_Pruefung_2026-10-23.md.


## AND-RECOVERY-024 – Nutzergerät bestätigt Blocker 23.10.2026
- Nutzer-Screenshot zeigt nach Abrufversuch23.10.2026 12:37 den in200078 ausschließlich für HTTP409 SERVER_RECOVERY_REBASE_REQUIRED erzeugten Hinweis. Damit ist der zuvor nur bedingte aktive Wiederherstellungsschutz am tatsächlich angesprochenen NAS nun durch Nutzer-Runtimebeobachtung bestätigt.
- Neues Menü/Serversuche sichtbar, letzter erfolgreicher Synchronisierungsstand weiterhin26.09.; dies ist kein APK-Versionsnachweis. Keine erneute erfolgreiche Synchronisierung behauptet.
- Live-GitHub geprüft: Android5f3288cbafdb21f3b12ed34cf000e0cffd656e10, Servera471996610a27b2d718f9835bf9c07930f29858f. capabilities stellt recovery_epoch_v1/recovery_id bereit; Geräteguard blockiert Fachzugriffe vor normalem Abgleich bei fehlendem X-TB-Recovery-ID.
- Ursache ist die noch fehlende Android-Integration des dokumentierten Wiederherstellungs-/Rebasevertrags. Wiederholt Neu laden oder Geräte-Neuaktivierung allein vervollständigt ihn nicht. Keine App-Daten löschen, keinen Schutz deaktivieren oder Header blind quittieren.
- Erforderlicher Folgeblock: vor jedem erneuten Fachupload verbindlicher lokaler Schreib-/Sync-Barrier, dauerhafte Sicherung von Outbox/Basisständen und zugehörigen Dateien; danach neue Servergeneration getrennt einlesen, alte Cursor/Basisversionen generationstreu behandeln und lokale/serverseitige Änderungen sowie gelöschte Entitäten konfliktbewusst abgleichen; normale Mutationen erst nach abgeschlossenem Abgleich wieder freigeben. Abbruch/Neustart/erneute Wiederherstellung und ausstehende Helferfotos gehören zur Abnahme.
- In diesem Screenshot-Diagnoseblock nur Befund/Checkpoint gesichert. Kein neuer Runtimecode, keine APK, kein Server-/NAS-/Datenreset und kein erfolgreicher Wiederherstellungsabgleich behauptet.


## AND-RECOVERY-024 – Implementierungsentscheidung nach „okay weiter“ (23.10.2026)
- Bestehender Vollsync vermischt lokale Exporte, Uploads und Serverübernahme. Alte Outbox-/Basisstände dürfen nach Restore nicht durch bloßes Nachsenden der Recovery-ID freigegeben werden.
- Sichere lokale Generationen: bisherige Room-Datenbanken, Geschäftseinstellungen, BTB-/Foto-/Dateibestände bleiben vollständig erhalten. Nach erfolgreicher privater Sicherung und isoliertem, authentifiziertem Lesen der aktuellen Servergeneration zeigt die App ausstehende lokale Änderungen und den Serververgleich.
- Bewusste Benutzerentscheidung „Serverbestand neu einlesen“ eröffnet einen neuen leeren lokalen Arbeitsbereich mit neuer Datenbank-/Datei-/Geschäftseinstellungsnamespace. Gerätebindung und aktivierte Serveradresse bleiben; kein Datenlöschen, keine Neuaktivierung und kein blindes Importieren der alten Outbox. Alte lokale Änderungen bleiben im gesicherten Altbestand zur Prüfung/Weitergabe; keine automatische fachliche Zusammenführung oder Erfolgsquittierung dieser Änderungen behaupten.
- Normaler Netzverkehr wird vor tatsächlichem HTTP-I/O zentral gegen den aktuellen Recovery-Stand geprüft. Abweichende/unbekannte Generation sperrt normale Fachreads/-writes; ausschließlich expliziter isolierter GET-Vergleich erhält die aktuelle Kennung. Normaler Schreibzugriff erst in der ausdrücklich gewählten neuen lokalen Generation; bei erneutem Restore wieder sperren. HTTP409 wird niemals durch automatischen Wiederholungsupload umgangen.
- Generationswechsel erfordert Prozessneustart, damit alte Room-/DataStore-Instanzen und Hintergrundjobs nicht weiterarbeiten. Alte Jobs abbrechen; Auswahl atomar journalisieren. Abbruch, fehlender Speicher, inkomplette Sicherung, abgelaufene Anmeldung, neue Recovery-ID während Vergleich oder Neustart halten die Sperre aufrecht.
- Öffentlicher Mein-Ordner bleibt persönlicher Exportbereich und kein neuer Sync-Quellbestand; keine öffentlichen Dateien löschen/verschieben. FileProvider nur um private Wiederherstellungs-/Generationspfade ergänzen, keine allgemeinen Rootfreigaben.
- Keine server/**-Änderungen, kein NAS-Schalter und keine neue Server-API. Vorhandene capabilities/recovery_epoch_v1/recovery_id und Geräte-/Fach-GETs nutzen. Auth/Team-/PDF-Rechte unverändert prüfen.
- Kritische Netzwerk-/Generationslogik mit echten JVM-Unit-Tests und CI-Unit/Lint-Prüfung ohne assemble/Release/APK-Upload validieren; keine APK herstellen. Lokale Android-Studio-/Pixel-/Poco-Abnahme bleibt erforderlich. Versionsziel1.3.29/200079 mit passender Release-MD, kein neuer Versionsbanner.


## Abschlussprüfung 200079: Sicherung vor Prozessende

- SharedPreferences.apply() aktualisiert zunächst den Prozessspeicher. Ein Prozessende vor der Sicherung könnte noch nicht auf Datenträger geschriebene Werte verlieren. Deshalb KEIN automatischer Prozessneustart vor der ersten Sicherung.
- Vor der Sicherung normale Fach-IO sperren, alte Fachoberflächen beenden und Worker/Poller abbrechen; aktuelle SharedPreferences-Werte sowie transaktionale Datenbankexporte und stabile Dateikopien sichern. Vor der bewussten Auswahl erneut sichern/vergleichen. Ein Fehler beim Durchlaufen eines Verzeichnisses muss die Sicherung abbrechen, statt still Dateien auszulassen.
- Erst nach vollständiger geprüfter Sicherung, frischem Serververgleich und ausdrücklicher Auswahl atomar eine neue Generation wählen und den alten Prozess beenden. Das bereits festgelegte Neustartgebot für den Generationswechsel bleibt bestehen.


## 2026-10-23 – aktueller Android-Folgeblock 1.3.29 / 200079

- Runtime-Quellstand: `d3df3108fd66920c7c1bc1bfc93d5817eacedf33`; Server-HEAD bleibt `a471996610a27b2d718f9835bf9c07930f29858f`. Keine server/**- oder NAS-Konfigurationsänderung.
- Wiederherstellungssperre vor tatsächlicher Fach-IO; private geprüfte Sicherung, getrennt lesender Serververgleich und ausdrückliche Auswahl einer neuen lokalen Generation implementiert. Prozessneustart erst NACH Sicherung und Auswahl. Alte Dateien/DBs/Einstellungen/Outbox bleiben erhalten; keine automatische Zusammenführung oder Upload-Quittierung alter Ergänzungen.
- CI-Nachweis: [Lauf 39947580253](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39947580253), Android-Compiler und `:app:testDebugUnitTest` erfolgreich: 88 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen. `:app:lintDebug` erfolgreich: 0 Fehler, 391 Warnungen; keine Warnung in neuen recovery-Dateien. JUnit-/Lint-XML aus dem zugehörigen Artefakt geprüft.
- Gesamt-CI weiterhin rot durch fünf bekannte historische Quellvertragsprüfungen (13/19/83/85/86); der Android-Compiler-/Test-/Lint-Job ist grün. Diese Prüfungen wurden nicht durch Versionskommentare oder deaktivierte Checks umgangen.
- Kein assemble, keine APK-Erzeugung/-Auslieferung. Kein Installations-/Pixel-/Poco-/NAS-E2E-Nachweis. Technische Validierung ist keine funktionale Gerätefreigabe.
- Übergabe/Prüfmatrix: `projektsteuerung/uebergaben/ANDROID_Wiederherstellung_200079.md`. Passende Release-Datei: `projektsteuerung/releases/TB-Helfer_1.3.29_200079.md`.

- X-TB-Recovery-ID wird nur im synchronen GET-Vergleich oder im ausdrücklich ausgewählten passenden neuen Workspace gesetzt. Kein automatischer 409-Mutationsretry und keine alte Outbox-Wiedergabe.
- Vergleich liest Geräteidentität, zugängliche Baustellen/Berechtigungen, eigene TB-Gruppen-BTBs, Baum/Mängel/Abnahmen und Katalog. Dateien/Vermessungsdetails folgen erst im normalen frischen Sync; fehlende Treffer im Vergleich bestätigen keine Löschung.
- Alte lokale Ergänzungen bleiben separat erhalten. Ihre komfortable Einzelübernahme ist nicht implementiert; keine automatische Konfliktauflösung behaupten.
