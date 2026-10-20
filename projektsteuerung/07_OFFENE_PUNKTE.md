# TB-HELFER 2.0 – OFFENE PUNKTE

Stand: 2026-09-27

| ID | Prio | Bereich | Punkt | Nächster Schritt |
|---|---:|---|---|---|
| AND-BUILD-200076 | P0 | ANDROID | `Locale`-Compilefehler reproduziert und source-seitig behoben; Generatoranker 14/14 PASS; noch kein erfolgreicher APK-Build | Git Pull → Android Studio Build 200076 erneut; danach Pixel installieren |
| BTB-FINAL-001 | P0 | GEMEINSAM | Android/Portal teilen denselben kanonischen Finalize-Lifecycle; Source-Pfad passend | E2E Android→Portal/Poco und Portal→Android/Poco inkl. Abschluss-PDF |
| E2E-HELPER-001 | P0 | ANDROID | HELPER/Poco mit aktuellem erfolgreichen Build noch nicht abschließend geprüft | 200076 auf Poco installieren und Lifecycle-/Daten-Sync testen |
| BTB-EDIT-001 | P1 | GEMEINSAM | Server Source + Migration016 vorhanden; Android Entry/Node + Foto-Metadaten-PATCH source-seitig integriert | Build 200076 → gemeinsamer Portal/Pixel-E2E für Entry- und Foto-Umhängen |
| IMG-001 | P1 | ANDROID | kleine Sync-JPEGs + preview-Download source-seitig für BTB/Mangel/Abnahme/Bild-Anlagen umgesetzt | Original-/Sync-/Preview-Größe, keine Dubletten und Handy→Handy praktisch prüfen |
| WEATHER-001 | P1 | ANDROID | 0,0-mm-Erhalt + mm-Anzeige App/PDF/Druckmodell source-seitig umgesetzt | Android/API/Portal/PDF-Roundtrip mit 0,0 mm + tempC |
| PORTAL-BTB-UI-001 | P1 | SERVER | bestätigter Live-Sync bleibt geschützt; weitere fachlich/optische Portal-BTB-Nacharbeit separat | nur konkrete noch offene Portal-BTB-Anpassungen im SERVER-Projekt bearbeiten |
| PORTAL-PART-001 | P1 | SERVER | nicht mehr aktive Helfer sollen aktuelle BTB-Ansicht nicht unnötig belegen; Historie muss erhalten bleiben | Teilnehmerquelle/Lifecycle im SERVER-Projekt prüfen |
| NET-001 | P1 | GEMEINSAM | früherer App-DNS-Fehler aktuell nicht mehr dauerhaft reproduziert | nur bei erneuter Reproduktion vertiefen |
| SRV-001 | P1 | SERVER | Migration015/Rendition-Metadaten-Runtime-Nachweis als eigener Altpunkt nicht vollständig dokumentiert | SERVER-Projekt entscheidet anhand aktuellem Deploystand, ohne Android-Ersatzfix |
| DEV-META-001 | P2 | SERVER | Geräte-`app_version/app_build` sind Aktivierungs-Snapshot und kein Runtime-Versionsnachweis | später Runtime-Metadatenvertrag definieren |
| SYNC-001 | P2 | GEMEINSAM | vollständiger 15×15 Last-/Delta-E2E offen; Delta-Wait 255/255 Teiltest bestanden | nach funktionalem Geräte-E2E und RAM-/NAS-Ausbau Gesamtlast wiederholen |
| DOC-001 | P2 | STEUERUNG | lokale Arbeitskopien müssen nach neuer Projektstruktur aktuell gehalten werden | bei nächstem lokalen Arbeitsblock Git Pull und alte Top-Level-Ordner nicht neu anlegen |

Geschlossen/geschützt und deshalb **nicht** als offen führen:
- BTB-SYNC-002: **BESTÄTIGT**.
- BTB-UI-001: **BESTÄTIGT**.
- PORTAL-LIVE-001: **BESTÄTIGT**.
- UPD-001: **BESTÄTIGT**.

Keine bestätigten Punkte wieder als offen anlegen.


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


## AND-IMG-2MB – Quellstand 20.10.2026
Status: **QUELLSTAND FERTIG / STATISCHE PRÜFUNG BESTANDEN / BUILD UND GERÄTE-E2E OFFEN**.
- Nutzerauftrag vor Implementierung gesichert: 7020891d4c26fb15d7d2ef5bdc41c408f68d81e0.
- MobileImageSync: 1..2.000.000Bytes als Cache-/Ausgabebedingung; JPEG82/75/68/60, bei Bedarf weitere Reduktion der Kante um20%, nie Hochskalierung. Bestehende1920px-Grenze und EXIF-Ausrichtung erhalten.
- Vorbereitung synchronisiert gegen gleichzeitige Schreiber derselben Cache-/Tempdatei. Tempdatei im finally entfernt; unvollständiger Zielcache nach Publikationsfehler gelöscht. Aufnahmedatei bleibt unverändert, kein Originalfallback.
- Aufrufer geprüft: BtbTeamMediaSync sowie CollabLocalBridge für Mangel-/Abnahmefoto und bildbasierte Abnahmeanlage verwenden gemeinsamen prepare-Pfad und brechen bei null mit Fehler ab. PDF-/Nichtbildanlagen unverändert.
- Context7: offizielle Android-Bitmap-Referenz zu JPEG-Qualität, compress-Rückgabewert und createScaledBitmap-Identität geprüft.
- Ausgeführt: neun statische Schutzprüfungen PASS; Dimensionsfortschritt für jede Kante1..1920 geprüft (max29 Reduktionsschritte). Dies ist keine Kotlin-Kompilierung und kein Bitmap-/Android-Laufzeittest.
- Umgebung: Java vorhanden, kein kotlinc/Gradle/Android-SDK/adb gefunden, direkter Git-Transport403; kein Build, Unit-Test, Lint oder Emulatornachweis. Kein APK erzeugt; Version bleibt1.3.26/200076. Vor nächster auslieferbarer APK neuer höherer versionCode und passende Release-MD erforderlich.
- Nächste praktische Prüfung: detailreiches/großes Bild, kleineres Bild, exakt2.000.000Bytes/Übergröße im Cache, EXIF-Drehung, Fehler bei Dateischreiben und erneuter Versuch, Originalhash unverändert; dann Upload für BTB/Mangel/Abnahme zum aktuellen Server. Aufnahme-/Metadatenverträge und bestätigte Sync-/Lifecycle-Pfade unverändert.
- Keine server/**-Änderung, keine produktiven Datenänderungen, keine Actions gestartet ([skip ci]).
