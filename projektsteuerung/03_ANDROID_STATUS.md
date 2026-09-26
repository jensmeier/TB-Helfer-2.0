# TB-HELFER 2.0 – ANDROID STATUS

Stand: 2026-09-26
Branch: `feature/tb-android-2.0`
Fach-HEAD vor Projektsteuerungscommit: `d272f8a7586cce3ea1eda29100473f7af5d540bb`
Draft-PR: #7
Version: **1.3.19 / 200069**

- Server/Android-Vertrag 25.09 im Quellstand berücksichtigt.
- Pixel ACTIVE↔PDF-Flackern source-seitig korrigiert.
- Build-Wiederholung 200069: **OFFEN**
- Installation 200069: **OFFEN**
- Pixel/Poco-/Server-E2E: **OFFEN**
- Poco: korrekte HELPER-/Teamzuordnung serverseitig, aber keine Daten auf Gerät.

Geschützt: bestätigter BTB-Lifecycle-Fix 200063, bestätigter Server-App-Updateweg mit Datenerhalt, Geräteaktivierung, kein breiter Vollsync als normaler Autosave-Pfad.

Nächste Schritte: NET-001 klären → Haupt-TB-Handy ↔ Server/Portal E2E → Poco/HELPER E2E → danach IMG-001 implementieren → Build/Regression.

Historischer Tracker: `projektsteuerung/archiv/ANDROID_STATUS_ALT_2026-09-26.md`

## Praxisbefund Hauptgerät – DNS/Hostauflösung

Beim Anlegen einer Testbaustelle meldet die App:
`Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.

Nachtest auf demselben Hauptgerät: `https://www.tb-helfer.de` und `https://api-v1.tb-helfer.de/health/live` sind im Browser erreichbar. Damit ist die öffentliche DNS/API-Erreichbarkeit grundsätzlich vorhanden. Nächster Schritt: App-Sync erneut auslösen; nur bei erneutem App-only `Unable to resolve host` den Android-Netzpfad gezielt untersuchen.


## IMG-001 Quellaudit
Status: **FEHLER BESTÄTIGT / IMPLEMENTIERUNG ZURÜCKGESTELLT BIS E2E-PRIO-001**

- `BtbTeamMediaSync` und `CollabLocalBridge` verwenden lokale Originaldateien als Uploadquelle.
- `Server1FileApi` überträgt exakt diese Datei und kennt aktuell keine Rendition-Downloadmethode.
- `synchronizeServer1Attachments()` lädt fehlende Bilder über `/content` vollständig.
- Lokales 512-px-Thumbnail vorhanden, aber nicht im Online-Sync genutzt.
- `FotoEntity` speichert keine GPS-Genauigkeit; BTB-Upload sendet deshalb `gpsAccuracyM = null`.
- Mangel-/Abnahmefoto-Uploads übergeben keine gleichwertigen strukturierten Foto-Metadaten.

Geplanter Android-Fix nach stabilem Hauptgerät-/Poco-E2E:
kleine Sync-JPEG-Datei → Upload; `preview/thumb` für mobile Downloads; Metadaten vollständig.

### Browser-Nachtest NET-001
Beide öffentlichen TB-Helfer-Seiten sind auf demselben Hauptgerät im Browser erreichbar. App-Retest ist der nächste Schritt.

### NET-001 reproduziert
Browserzugriff auf Portal und API-Health funktioniert auf demselben Gerät; `Neu laden` in der App liefert weiterhin `Unable to resolve host "api-v1.tb-helfer.de"`. Damit ist der Fehler app-/Android-netzpfadspezifisch reproduziert. Source-/Runtime-Diagnose erforderlich; keine Neuaktivierung oder Datenlöschung auf Verdacht.

### NET-001 nach Prozessneustart und Mobilfunk
Force-Stop/Neustart der App sowie WLAN aus / Mobilfunk ändern den Fehler nicht. `Unable to resolve host "api-v1.tb-helfer.de"` bleibt reproduzierbar. Als nächstes systemweite Android-DNS-/VPN-/Filtereinstellungen prüfen; App-Daten/Token nicht löschen.

### Praxisnachtrag – Pixel wieder synchron / Poco frisch aktiviert
- Pixel hat inzwischen wieder erfolgreich mit dem NAS synchronisiert; der zuvor reproduzierte DNS-Fehler blockiert den aktuellen Lauf nicht mehr.
- Poco: App gelöscht, neu installiert und neu aktiviert; erster Datenabruf läuft >3 Minuten.
- Nächster Schritt ist kein weiterer Reset, sondern Erstsync-Pipeline/Datenumfang prüfen und Laufzeitphase eingrenzen.

### BTB-SYNC-001 Praxisfehler 26.09
- Poco-Erstabgleich abgeschlossen; Baustelle erfolgreich geladen.
- Nach neuem Pixel-BTB 26.09 erscheint dieser auf Poco kurz und verschwindet danach wieder („kein BTB vorhanden“).
- Android-seitig daher besonders prüfen: Server-Snapshot ersetzt/entfernt lokalen Active-Pointer? FINALIZED-/Statuslogik? Team-/Group-Filter? Kein Fix ohne Serverzustand.

### BTB-SYNC-001 Versionsabweichung auf Testgeräten
Server-Geräteabfrage: Pixel meldet `1.3.4`, Poco `1.3.19`. Damit ist der aktuelle Geräte-E2E gemischt. Installation des aktuellen Zielstands `1.3.19 / 200069` auf Pixel ist vor weiterer Ursachenbewertung zwingend nachzuweisen.

### Korrektur Versionsdiagnose
Pixel läuft nach Benutzerbestätigung ebenfalls auf `1.3.19`. Der Serverwert `1.3.4` stammt aus der Geräteaktivierung und ist nach App-Updates stale. Kein Versionsgleichstandsfehler als Ursache von BTB-SYNC-001 bestätigt.

### BTB-SYNC-001 nach Portal-Bestätigung erneut reproduziert
Poco zeigt den serverseitig und im Portal bestätigten aktiven 26.09-BTB kurz und danach wieder „kein BTB vorhanden“. Android-seitige Ursache jetzt isoliert.

### BTB-SYNC-001 Source-Ursache
`BtbActiveContext.laden()` kann einen serverseitig bestätigten aktiven BTB lokal wieder deaktivieren, weil es den lokalen Dokumentstatus `ABGESCHLOSSEN` höher bewertet als `BtbServerStateStore.active=true`. Der UI-Watcher läuft ca. alle 600 ms; das erklärt das kurze Anzeigen und anschließende Verschwinden. Android-Fix erforderlich; noch kein Build/E2E.

### BTB-SYNC-002 – Pixeländerung wird nicht hochgeladen und später überschrieben
Pixel-Eingabe erreicht das Portal nicht; nach Portaländerung übernimmt Android den Serverstand und die lokale Pixel-Eingabe geht verloren. Android muss zuerst Save→Outbox→Upload→Apply-Reihenfolge und Konfliktschutz nachweisen.

### BTB-SYNC-002 exakte Source-Ursache
Im `SelectiveSyncWorker` wird bei bekanntem BTB aktuell der Server-Snapshot angewendet, bevor `exportBtbChange()` die lokal gespeicherte Änderung in die Outbox überführt. Der Dirty-Eintrag im `SelectiveSyncStore` schützt `applyServerStateToLocal()` nicht. Dadurch kann der Serverstand die lokale Änderung vor dem Upload überschreiben. Android-Fix erforderlich.


## BTB-EDIT-001 – Live-Integrationsstand Android
Status: **ENTRY-/NODE-QUELLUMSETZUNG LÄUFT / FOTO-PFAD NOCH OFFEN**

Live geprüft am 26.09.2026:
- Android-HEAD: `623704990072c7006e6a48e611441d7aa08b989e`.
- `SectionItem` enthält jetzt `clientEntryKey`, `firmaId/firmaName`, `worksiteNodeKey`.
- Worksite-Struktur stellt stabile `clientNodeKey`-Zuordnung und Rückauflösung bereit.
- Server1BtbApi sendet bei Entry-PATCH bereits `section` und erhält die Entry-/Slot-Identität.
- Android-Editor migriert Legacy-`@ORT` in stabile `@NODE`-/strukturierte Node-Referenzen.
- Android-Foto-Sync `BtbTeamMediaSync` führt aktuell noch keinen `worksiteNodeKey`/`firmaId`-Umhänge-PATCH für bestehende Fotos aus.
- Daher gemeinsamer BTB-EDIT-001 Geräte-E2E noch offen.


### BTB-EDIT-001 – Android Live-Nachtrag
- aktueller HEAD: `623704990072c7006e6a48e611441d7aa08b989e`.
- sichtbarer BTB-Editor erhält jetzt stabile Entry-/Node-Identitäten (`clientEntryKey`, `firmaId`, `worksiteNodeKey`) und hält lokale `ortId` nur noch als UI-/Legacy-Cache.
- Entry-/Unterbaustellenpfad nähert sich damit dem gemeinsamen Serververtrag.
- Foto-Umhängen über den neuen versionierten Server-Foto-PATCH bleibt weiterhin offen.


### BTB-EDIT-001 – Foto-Metadaten Live-Nachtrag 7c7cf7b5
- Android-HEAD live geprüft: `7c7cf7b502ec1f701f167c13780b10a014bba6d9`.
- `BtbPhotoContextOverrideStore.ContextOverride` enthält jetzt:
  - `firmaId`,
  - `worksiteNodeKey`,
  - `bildtyp`,
  - lokale `ortId` weiterhin nur als UI-/Legacy-ID.
- Damit ist die lokale Android-Grundlage für Foto-Umhängen passend zum Serververtrag vorhanden.
- Noch zu bestätigen/umzusetzen: tatsächlicher versionierter Aufruf von `PATCH /btbs/{btb_id}/photos/{file_id}` und Konflikt-/Sync-Roundtrip.
- Draft-PR #7 aktuell gegen SERVER `mergeable=false`; Integration erst nach abgeschlossenem Android-Arbeitsblock.


### BTB-EDIT-001 – Abschlussaudit vor Integration
Benutzer bestätigt den Android-Arbeitsblock als fertig.

Live-Quellprüfung des Android-HEAD `a1b4667b110a993796e8242d4a21be3748c06d68`:
- Entry-/Node-Identität ist umgesetzt.
- lokale Foto-Kontextdaten enthalten `firmaId`, `worksiteNodeKey` und Bildtyp.
- `Server1BtbApi.kt` enthält jedoch noch keinen Clientpfad für `PATCH /api/v1/btbs/{btb_id}/photos/{file_id}`;
- Android wertet noch kein `link_version` für BTB-Fotoverknüpfungen aus;
- `BtbTeamMediaSync.kt` sendet die lokale Foto-Umsortierung daher noch nicht als versionierte Servermutation.

Einordnung:
- Android-Arbeitsblock ist beendet und Integration freigegeben.
- Der fehlende Foto-PATCH bleibt ein klar abgegrenzter Android-Restpunkt nach Branch-Integration.
- SERVER ändert dafür kein `app/**`.
