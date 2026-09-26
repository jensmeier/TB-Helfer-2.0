# TB-HELFER 2.0 – AKTIVER AUFTRAG

Stand: 2026-09-26

## PS-001 – Projektsteuerung zentralisieren
Status: **UMGESETZT IM QUELLSTAND DIESER STEUERUNGSÄNDERUNG**

Ein gemeinsamer Einstieg unter `projektsteuerung/`; Android/Server-Codebereiche bleiben getrennt; alte Top-Level-Steuerungsordner entfallen; Chat-Sicherung erfolgt ereignisbasiert. Kein `app/**`- oder `server/**`-Fachcode wird durch PS-001 geändert.

## IMG-001 – Bildtransport / Serverlast
Status: **QUELLAUDIT ABGESCHLOSSEN / ABWEICHUNGEN BESTÄTIGT / IMPLEMENTIERUNG NACH E2E-PRIO-001**

Verbindlich:
- Handyoriginal bleibt auf dem Aufnahme-Handy.
- Handy → Server/Portal: verkleinerte/komprimierte Sync-Version + Thumbnail + Metadaten.
- Portal → Handy: kleine Version/Thumbnail.
- Handy → anderes Handy: kleine Version/Thumbnail über Server.
- Portal-Upload darf die empfangene Quelldatei serverseitig behalten; mobile Nutzung über Renditions.

Diese Regel steht bereits in `projektsteuerung/vertraege/TB-SYNC_15x15_ZIELMODELL.md`.

Nächster Schritt:
1. Android-Fotoupload prüfen: Original oder Sync-Version?
2. Server-Empfang/Storage/Renditions prüfen.
3. Handy→Handy-Download prüfen.
4. Abweichungen dokumentieren.
5. erst dann getrennte Android-/Server-Aufträge.
6. E2E mit realen Dateigrößen.

### IMG-001 Audit-Ergebnis 26.09.2026
- Android lädt aktuell das lokale Foto-Original hoch, nicht eine separate kleine Sync-Version.
- Server speichert die empfangene APP-Datei unverändert.
- Server-Renditions `thumb`/ `preview` existieren bereits.
- Android ignoriert diese Renditions und lädt über `/content`.
- Lokales 512-px-Thumbnail existiert, wird im Online-Sync aber nicht genutzt.
- Metadatenlücken: GPS-Genauigkeit nicht persistent; Mangel-/Abnahmefoto-Upload strukturell unvollständig.

Umsetzung ist dokumentiert, aber gemäß `E2E-PRIO-001` erst nach stabiler Hauptgerät-/Poco-Fachkette dran.

## SYNC-DEVICE-001 – Poco erhält keine Daten
Status: **OFFEN**

Serverseitig HELPER im richtigen Team bestätigt; Gerät erhält trotzdem keine Daten. Keine Neu-Anlage von Benutzer/Einladung auf Verdacht. Binding/Sync/API/Logs gezielt eingrenzen.

## E2E-PRIO-001 – Hauptgerät/Portal zuerst, Helfer danach
Status: **VERBINDLICHE ARBEITSREIHENFOLGE**

Neue Prioritätsentscheidung:
1. Ein verantwortliches TB-Handy muss zuerst mit Server und Portal in der echten Fachkette sauber funktionieren.
2. Danach wird `SYNC-DEVICE-001` / Poco als HELPER gezielt bis zum bestandenen Geräte-E2E abgearbeitet.
3. Weitere breite 15×15-Lastreihen werden vorerst zurückgestellt.
4. Nach geplantem RAM-Ausbau auf 8 GB bzw. nach Wechsel auf ein neues NAS wird der vollständige Last-/Kapazitätstest auf der dann maßgeblichen Hardware wiederholt.
5. Bis dahin werden Leistungswerte nur als Diagnose verwendet; funktionale Server-/Sync-Verträge werden nicht aufgrund eines einzelnen Lastwerts auf Verdacht umgebaut.

Bereits praktisch beobachteter Kapazitätsteiltest:
- `/api/v1/sync/changes/wait`
- 255 getrennte Portal-Sessions
- `success=255`, `failure=0`
- Wall 16,43 s, Median 14,28 s, Maximum 15,79 s
- verbliebene Python-Testsessions anschließend kontrolliert von 7 auf 0 bereinigt

Bewertung: **DELTA-WAIT-KAPAZITÄTSTEILTEST BESTANDEN / VOLLSTÄNDIGER 15×15-E2E WEITER OFFEN**.

## NET-001 – Hauptgerät kann api-v1.tb-helfer.de nicht auflösen
Status: **ÖFFENTLICHE DNS/API ERREICHBAR / APP-SEITIGE URSACHE OFFEN**

Praktischer Befund beim Anlegen einer Testbaustelle auf dem Hauptgerät:
- App meldet `Synchronisation unvollständig`;
- konkrete Ursache: `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`;
- damit ist der aktuelle Hauptgerät↔Server/Portal-E2E bereits vor der Fachsync-Prüfung durch DNS/Netzauflösung blockiert.

Vorgehen:
1. öffentliche Erreichbarkeit/DNS von `api-v1.tb-helfer.de` prüfen;
2. bei öffentlich funktionierender Domain Geräte-DNS/Netz/Private-DNS/VPN eingrenzen;
3. erst nach erfolgreicher Namensauflösung Fachsync und Portal-Konvergenz prüfen;
4. keine Sync-/DB-/API-Codeänderung auf Verdacht.

Browser-Nachtest auf demselben Hauptgerät:
- `https://www.tb-helfer.de` erreichbar;
- `https://api-v1.tb-helfer.de/health/live` erreichbar;
- öffentliche Domain/Cloudflare/DNS sind damit grundsätzlich erreichbar;
- nächster Schritt: denselben App-Sync erneut auslösen;
- nur bei erneutem App-only `Unable to resolve host` Android-Netzpfad/Prozess/Private-DNS/VPN/HTTP-Client gezielt eingrenzen;
- keine Sync-/DB-/API-Codeänderung auf Verdacht.

### NET-001 Reproduktion nach Browser-Nachweis
- Auf demselben Hauptgerät sind `www.tb-helfer.de` und `api-v1.tb-helfer.de/health/live` im Browser erreichbar.
- Unmittelbarer erneuter App-Test mit `Neu laden` reproduziert weiterhin exakt `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.
- Der Fehler ist damit **app-/Android-Netzpfad-spezifisch reproduziert** und nicht durch eine allgemeine öffentliche DNS-/Cloudflare-Störung erklärt.
- Nächster Schritt: Android-Netzpfad, gespeicherte Serverbasis, HTTP-Client und App-Prozess gezielt gegen den Browserpfad prüfen.
- Keine Server-/DB-Änderung auf Verdacht.

### NET-001 Netzwechsel-Reproduktion
- App vollständig gestoppt/neu gestartet: Fehler bleibt identisch.
- WLAN deaktiviert und erneuter App-Test über Mobilfunk: Fehler bleibt identisch.
- Damit ist ein nur im aktuellen WLAN auftretender DNS-Fehler unwahrscheinlich.
- Browserzugriff auf Portal/API war auf demselben Gerät zuvor erfolgreich.
- Nächster Diagnosepunkt: Android systemweite DNS-/VPN-/Filter-Ebene (Privates DNS, VPN/Adblock/NextDNS o. ä.) gegen den Browserpfad prüfen.
- Keine Server-/DB-/Sync-Codeänderung auf Verdacht.

### NET-001 / SYNC-DEVICE-001 Praxisnachtrag – Pixel synchronisiert, Poco Erstabgleich langsam
- Pixel synchronisiert inzwischen wieder mit dem NAS. Der frühere `Unable to resolve host`-Fehler ist aktuell nicht mehr reproduzierbar als dauerhafter Blocker; Ursache bleibt offen.
- Poco wurde deinstalliert, neu installiert und neu aktiviert.
- Poco zeigt beim anschließenden ersten Datenabruf nach >3 Minuten weiterhin laufenden Abruf.
- Das ist noch kein bestätigter Sync-Erfolg und noch kein bestätigter Fehler. Vor Änderungen zuerst klären, welche Erstsync-Phasen/Datenmengen tatsächlich laufen und an welcher Phase Zeit verloren geht.
- Keine Neu-Anlage von Firma/Benutzer/Team/Baustelle auf Verdacht.

## BTB-SYNC-001 – Neuer BTB 26.09 fehlt im Portal und verschwindet auf Helfer
Status: **FEHLER REPRODUZIERT / URSACHE OFFEN**

Praktischer Ablauf:
1. Poco nach Neuinstallation/Neuaktivierung: Erstabgleich abgeschlossen, Baustelle erfolgreich heruntergeladen.
2. Pixel: neuer BTB 26.09 angelegt.
3. Portal: weiterhin letzter sichtbarer BTB 25.09, 26.09 fehlt.
4. Poco: 26.09-BTB erscheint kurz, danach nach ca. einer Sekunde „kein BTB vorhanden“.

Zwingende Diagnose vor Änderung:
- betroffene Firma/Baustelle/Team und `active_btb_id`;
- Server-BTB-Datensatz 26.09: ID, Status, Team, client_key, Version, created/updated;
- Change-Feed-Einträge für diesen BTB;
- API-Antwort für Team-BTB-Liste;
- Android-Snapshot/Active-Pointer-Anwendung auf Poco;
- keine Neu-Anlage von Benutzer/Team/Baustelle und kein Lifecycle-Workaround auf Verdacht.

### BTB-SYNC-001 Diagnosefund – Pixel läuft noch 1.3.4
- Gerätestatus aus der Server-DB:
  - Pixel: App-Version `1.3.4`
  - Poco: App-Version `1.3.19`
- Projektziel/aktueller Android-Fachstand: `1.3.19 / 200069`.
- Change-Feed zeigt wiederholte Updates desselben BTB bis mindestens Version 19.
- Bewertung: erst Versionsgleichstand herstellen/prüfen. Kein Server- oder DB-Fix, solange das Hauptgerät noch mit einem älteren Android-Syncvertrag arbeitet.

### Korrektur zu BTB-SYNC-001 Diagnosefund
- Pixel ist laut Geräteanzeige tatsächlich auf `1.3.19`.
- `tb.devices.app_version` ist nur der bei Aktivierung gemeldete Stand und wird bei normalem Sync nicht aktualisiert.
- Die Schlussfolgerung „Pixel läuft noch 1.3.4“ war falsch und wird nicht weiterverfolgt.
- Nächster Schritt bleibt die eigentliche BTB-Diagnose: Team-`active_btb_id`, 26.09-BTB-Datensatz, Change-Feed und API-Snapshot.

### BTB-SYNC-001 Diagnosefund – Server empfängt 26.09-BTB
- Change-Feed bestätigt den BTB `ba2144b7-9ede-4f53-abdc-1accd172e875` mit `work_date=2026-09-26`.
- Mehrere aufeinanderfolgende `UPDATE`-Changes in wenigen Sekunden, sichtbar mindestens Version 7–19.
- Pixel→Server-Transport funktioniert daher grundsätzlich.
- Nächste Abgrenzung: kompakten DB-Check nur für diesen BTB ausführen:
  - Worksite/Team/Lead
  - `teams.active_btb_id`
  - BTB-Status
  - Client-BTB-ID
  - aktueller Header/Version
  - Teilnehmerstatus Pixel/Poco
- Erst danach Portal-/Android-Leseweg bewerten.

### BTB-SYNC-001 Diagnosefund – Server-Lifecycle vollständig korrekt

- Direkter DB-Check für BTB `ba2144b7-9ede-4f53-abdc-1accd172e875`:
  - Baustelle: `GE Immenstadt - Kempten`
  - Team: `pixeljens`
  - Team-Lead: `zinkel.Jens`
  - `teams.active_btb_id` == BTB-ID
  - `active_date=2026-09-26`
  - `active_status=ACTIVE`
  - BTB selbst: `status=ACTIVE`, `version=19`
  - Client-BTB-ID vorhanden
  - Poco = HELPER / participant `WORKING`
  - zinkel.Jens = LEAD / participant `WORKING`
- Damit ist der kanonische Server-Lifecycle für den 26.09-BTB korrekt. Ursache liegt jetzt im Portal-/Android-Lese-/Anwendungspfad, nicht in fehlendem `active_btb_id` oder Teilnehmerstatus.

Nächster Schritt: Portal-Team-BTB-Leseweg und Android-Snapshot/Active-Pointer gezielt prüfen.

### BTB-SYNC-001 Portal bestätigt
Nach Seitenneuladen zeigt das Portal den aktiven 26.09-BTB korrekt. Damit ist die Kette Pixel → Server → Portal für diesen BTB bestätigt.

Weiter offen:
- Poco sieht denselben aktiven BTB kurz und fällt danach auf „kein BTB vorhanden“ zurück.
- Nächster Diagnosepunkt ausschließlich Android: Server-Snapshot → lokaler BTB-Cache → Active-Pointer → UI-Auswahl.

### BTB-SYNC-001 Poco erneut reproduziert nach Portal-Bestätigung
- Portal zeigt den 26.09-BTB nach Reload korrekt als ACTIVE.
- Poco zeigt denselben 26.09-BTB weiterhin nur kurz und springt danach auf „kein BTB vorhanden“.
- Damit ist der Fehler jetzt eindeutig Android/Poco-seitig reproduziert, nachdem Server-Lifecycle und Portal-Read bestätigt sind.
- Nächster Schritt: Android Active-Pointer-/Marker-/UI-Auswahllogik gezielt im Quellstand prüfen; kein Serverfix auf Verdacht.

### BTB-SYNC-001 Source-Ursache eingegrenzt
- Quellpfad isoliert:
  - `CollabLocalBridge.applyServerStateToLocal()` setzt bei `serverActive=true` den lokalen Active-Pointer korrekt.
  - `BtbModernLifecycleInstaller` fragt über `BtbUiSaveBridge.aktuell()` ungefähr alle 600 ms den aktiven BTB ab.
  - `BtbActiveContext.laden()` kann danach den gerade gesetzten Pointer wieder löschen, wenn `BtbDokumentVersionierung.status(...)=ABGESCHLOSSEN` ergibt.
  - Dieser lokale Dokumentstatus wird dort derzeit geprüft, **ohne einen bereits bestätigten `BtbServerStateStore.active=true` als Vorrang zu behandeln**.
- Das passt exakt zum Praxisbild: 26.09 erscheint kurz, nach etwa einer Sekunde „kein BTB vorhanden“.
- Serververtrag ist bereits kanonisch: solange Server-`active_btb_id` auf den BTB zeigt und Teilnehmer `WORKING` ist, darf ein lokaler/staler Abschlusszustand den Active-Pointer nicht lösen.

Benötigte Android-Anpassung: `BtbActiveContext` muss bei vorhandenem ServerState mit `active=true` den kanonischen Server-Lifecycle respektieren und darf den Pointer nicht aufgrund eines lokalen Abschlussstatus lösen. SERVER ändert dafür keinen DB-/API-Vertrag.

## IMG-PORTAL-001 – 4 Serverbilder, 1 Portalbild
Status: **FEHLER FESTGESTELLT / URSACHE OFFEN**

Praxis:
- aktiver BTB 26.09;
- auf Server/NAS liegen 4 zugehörig wirkende Bilder;
- Portal zeigt nur 1 Bild.

Diagnose vor Änderung:
1. Attachment-Datensätze der vier Dateien prüfen;
2. `parent_entity_type`, `parent_entity_id`, `purpose`, MIME, Dateiname und Metadaten vergleichen;
3. prüfen, welche Fotos der BTB-Detail/API tatsächlich zurückliefert;
4. Portal-Template/-JS nur ändern, wenn API alle 4 liefert, UI aber filtert;
5. keine Dateien oder DB-Zuordnungen auf Verdacht ändern.

## PORTAL-LIVE-001 – Portal ohne F5 automatisch aktualisieren
Status: **NEUE ANFORDERUNG / SOURCE-UMSETZUNG OFFEN**

- Nach manuellem Browser-Reload (F5) erscheinen die zuvor fehlenden BTB-Bilder vollständig im Portal.
- Damit ist die Bildzuordnung/Serverablage grundsätzlich vorhanden; der aktuelle Praxisfehler ist **fehlende Live-Aktualisierung der offenen Portalansicht**.
- Benutzeranforderung: Neue BTBs/Bilder/Fachdaten sollen in einer offenen Portalansicht automatisch sichtbar werden; F5 darf für normalen Betrieb nicht nötig sein.
- Bestehende Architekturregel gilt: offene Portalansichten dürfen live aktualisieren, aber nicht über 2-Sekunden-DB-Polling pro Browser. Vorhandener Change-Feed/SSE-/Long-Poll-Pfad ist zu verwenden.

Umfang zuerst:
1. offene Baustellen-/BTB-Portalansicht an Change-Feed anbinden;
2. bei relevanten Änderungen nur die betroffenen Portalbereiche nachladen (Teams/BTBs, Medien, Mängel, Abnahmen);
3. keine vollständige Seitennavigation und kein permanentes Kurzintervall-Polling;
4. Reconnect/Fehler robust behandeln; F5 bleibt nur Recovery.

### PORTAL-LIVE-001 Source-Fix umgesetzt
- Source-Fix Commit: `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- `v1_btb.html` und `v1_worksite.html` abonnieren jetzt den vorhandenen worksite-bezogenen SSE-Change-Feed `/api/v1/sync/events`.
- Relevante Änderungen werden entprellt und gezielt nachgeladen; auf der BTB-Seite insbesondere `BTB`, `BTB_ENTRY`, `BTB_PARTICIPANT` und `FILE`.
- Baustellenseite aktualisiert Teams/BTBs, Medien, Mängel, Abnahmen, Mitglieder und Baum abhängig vom Change-Typ.
- Kein 2-Sekunden-Polling; vorhandener gemeinsamer Change-Hub wird verwendet.
- Source-Assertions für EventSource/SSE-Pfad und gezielte Reload-Funktionen: **PASS**.
- Status bleibt: **QUELLSTAND FERTIG / DEPLOY OFFEN / E2E OFFEN**.
Nächster Schritt: Serverstand deployen und real prüfen, dass neue Handy-Bilder/BTB-Änderungen ohne F5 in offener Portalansicht erscheinen.

## BTB-SYNC-002 – asymmetrischer BTB-Sync mit Datenverlust
Status: **P0 FEHLER REPRODUZIERT / URSACHE OFFEN**

- Praxisbefund am aktiven BTB 26.09:
  - Eingabe/Änderung auf dem Pixel erscheint **nicht** im Portal.
  - Änderung im Portal kommt anschließend auf dem Pixel an.
  - Beim Eintreffen des Portalstands wird die zuvor auf dem Pixel eingegebene Änderung überschrieben/gelöscht.
- Das ist ein P0-Datenverlust-/Konfliktfehler. Bis zur Ursache keine weiteren produktiven BTB-Eingaben für diesen Testdatensatz erzwingen.
- Vor Änderung prüfen:
  1. ob die Pixel-Eingabe lokal bereits gespeichert ist;
  2. ob dafür eine Android-Outbox/BTB-Pending-Mutation entsteht;
  3. ob diese Mutation zum Server gesendet oder durch einen vorherigen Server-Snapshot verworfen wird;
  4. Server-BTB-Version/Entry-Versionen und Change-Feed vor/nach Pixel- und Portaländerung;
  5. Merge-/Conflict-Regel für dieselbe BTB-Sektion bzw. denselben Slot.

Diagnose-Reihenfolge:
- zuerst Android lokales Save + Pending/Outbox;
- dann Server-API/Version/Change-Feed;
- dann Portal-Mutation/Version;
- erst danach Fix in genau einer verantwortlichen Schicht.

### BTB-SYNC-002 Source-Ursache – SelectiveSyncWorker überschreibt vor Export
- **Source-Ursache gefunden im aktuellen Android-HEAD `ef7077923e2917ac615b9f3b5143ac2638fb92ac`:**
  - `BautagebuchSpeicher.speichern()` speichert die Pixel-Änderung lokal und markiert den BTB nur im `SelectiveSyncStore` als dirty.
  - `SelectiveSyncWorker` lädt bei bereits bekanntem Server-BTB **zuerst** `loadBtbAccess(...)`, speichert den Server-Snapshot und ruft **vor** `exportBtbChange(...)` bereits `bridge.applyServerStateToLocal(...)` auf.
  - Zu diesem Zeitpunkt existiert noch keine `collabPending`-Mutation für die neue Pixel-Änderung; der Guard in `applyServerStateToLocal()` kann sie daher nicht schützen.
  - Ergebnis: der ältere/andere Serverstand überschreibt die lokal gespeicherte Pixel-Änderung, erst danach wird exportiert. Damit wird der überschriebenen Serverstand statt der Pixel-Änderung hochgeladen.
- Das erklärt exakt den Praxisbefund: Pixel→Portal fehlt; Portal→Pixel kommt an und entfernt die Pixel-Eingabe.
- Server/API/DB sind für diesen Fehler nicht zu ändern.

Benötigte Android-Korrektur:
1. bei dirty BTB zuerst den lokalen Stand in die Outbox/exportieren bzw. gezielt sichern;
2. erst danach Serverzugriff/Version prüfen und konfliktbewusst hochladen;
3. eingehenden Snapshot nicht lokal anwenden, solange für denselben `clientBtbId` ein Dirty-/Pending-Stand existiert;
4. bei Versionskonflikt kein stilles Überschreiben, sondern Rebase/Conflict-Pfad.


## PORTAL-LIVE-001 – Deploy jetzt
Status: **BENUTZER BESTÄTIGT / SERVER-DEPLOY JETZT PRIORITÄT**

- Der bidirektionale Pixel↔Portal-Basissync ist auf Android 1.3.23 / 200073 praktisch bestätigt.
- Benutzerauftrag: als nächstes den bereits vorhandenen Portal-Live-Refresh deployen, damit im normalen Portalbetrieb kein F5 mehr nötig ist.
- Source-Fix ist bereits vorhanden: Commit `c0355161a2d7ecceaadd1fc79f2bbade346a78cc`.
- Betroffene Portaldateien: `server/templates/v1_btb.html` und `server/templates/v1_worksite.html`.
- Vorhandener SSE-/Change-Feed `/api/v1/sync/events` wird genutzt; kein 2-Sekunden-DB-Polling.
- Nächster Schritt: Synology-Deploy nach Projektregel und anschließend E2E mit offenem Portal + Handyänderung/Foto ohne F5.


## PORTAL-LIVE-001 – Praxis bestätigt / BTB-Portal-Nacharbeit separat
Status: **PORTAL-LIVE-001 BESTÄTIGT / PORTAL-BTB-UI-001 OFFEN**

Praktische Benutzerbestätigung:
- offene Portalansicht aktualisiert sich ohne F5;
- Änderungen erscheinen nach ca. 1–2 Sekunden;
- Erstellen und Löschen werden automatisch sichtbar;
- Synchronisation funktioniert in beide Richtungen.

Damit ist PORTAL-LIVE-001 praktisch bestätigt. Der Live-/SSE-Pfad darf nicht ohne neuen reproduzierbaren Fehler geändert werden.

Neue getrennte Anforderung:
- Der BTB im Portal muss fachlich/optisch noch nachgearbeitet werden.
- Diese Nacharbeit wird als `PORTAL-BTB-UI-001` getrennt vom bestätigten Live-Sync geführt.
- Noch keine UI-/Fachänderung auf Verdacht; konkrete gewünschte Portal-BTB-Anpassungen werden separat festgelegt.


## BTB-EDIT-001 – Einträge/Fotos vollständig nachträglich umhängen
Status: **NEUE FACHANFORDERUNG / VOR UMSETZUNG**

Benutzeranforderung:
- Jeder noch bearbeitbare BTB-Eintrag und jedes Foto muss nachträglich fachlich korrigierbar sein, ohne Löschen/Neuanlegen zu erzwingen.
- Änderbar sein müssen mindestens: BTB-Punkt 4–8, Firma, Unterbaustelle/Bauobjekt (z. B. freie Strecke oder BÜ), Text, Zeit/KM und Foto-Zuordnung.
- Beispiel: Ein Eintrag wurde versehentlich dem falschen BÜ, der falschen Firma oder Punkt 4 statt Punkt 5 „Abnahmen“ zugeordnet; diese Zuordnung muss später geändert werden können.
- Dasselbe gilt für Fotos: Foto muss zwischen Punkt 4–8 und zwischen Unterbaustellen/Bauobjekten umgehängt werden können.
- Portal und Android müssen dasselbe strukturierte Schema verwenden; keine voneinander abweichenden Freitext-Sonderwege.
- Unterbaustellen/Bauobjekte müssen im Portal sichtbar und auswählbar sein wie auf Android (Hauptbaustelle, freie Strecke, BÜ usw.), soweit sie im Baustellenbaum vorhanden sind.
- FINALIZED/historische BTBs bleiben gemäß bestehendem Vertrag geschützt/read-only; die Anforderung gilt für bearbeitbare Zustände.
- Bestehender bestätigter Live-Sync bleibt geschützt.

Vor Implementierung zuerst Istvergleich Android↔Portal/API/DB durchführen und fehlende strukturierte Felder/Mutationsendpunkte bestimmen.


### BTB-EDIT-001 Istvergleich Android ↔ Portal 26.09.2026
Status: **STRUKTURLÜCKE BESTÄTIGT / NOCH NICHT IMPLEMENTIERT**

Quellvergleich:
- Android bietet bei Punkt-4-Einträgen bereits „Ort / Unterbaustelle“ mit hierarchischer Auswahl aus dem Baustellenbaum.
- Android speichert diese Auswahl im aktuellen Editor jedoch teilweise als lokalen Marker `@ORT=<lokale Room-Long-ID>`; diese ID ist gerätespezifisch und kein geeigneter Server-/Mehrgeräte-Schlüssel.
- Beim Umwandeln in strukturierte `SectionItem`-Daten werden `@ORT`-Marker teilweise entfernt; `SectionItem` besitzt selbst kein Unterbaustellenfeld.
- Androids stabiler Baustellenstruktur-Sync kennt bereits `clientNodeKey` für Unterknoten. Für BTB-Zuordnungen muss künftig dieser/stabiler äquivalenter Schlüssel verwendet werden, nicht die lokale Long-ID.
- Portal zeigt aktuell keine Unterbaustellenhierarchie im BTB-Editor und lädt für den BTB-Editor keinen auswählbaren Baustellenbaum.
- Portal-Entry-Editor kann aktuell Zeit, KM und Text ändern; Abschnitt 4–8, Firma und Unterbaustelle sind nicht vollständig umhängbar.
- Server `BtbEntryUpdate` erlaubt aktuell nur `sort_order` und `payload`; `section` kann per PATCH nicht geändert werden. Ein echtes Verschieben 4↔5↔6↔7↔8 fehlt daher im API-Vertrag.
- Bei Fotos existiert Upload + Zuordnung + Detach, aber kein allgemeiner PATCH-Pfad zum nachträglichen Ändern von Foto-Metadaten/Fachzuordnung. Android hat lokale Foto-Kontext-Overrides inkl. `ortId`, überträgt diesen Ort jedoch nicht als stabilen Node-Key in `structuredPhotoMetadata`.
- Ergebnis: die gewünschte Nachbearbeitung erfordert einen gemeinsamen strukturierten Android↔Server-Vertrag, nicht nur eine Portal-UI-Änderung.

Zusätzliche Benutzerhinweise für denselben Portal-BTB-Arbeitsblock:
- gelöschte/nicht mehr aktive Helfer sollen in der aktuellen BTB-Ansicht nicht unnötig weiter angezeigt werden; historische Nachweise dürfen nicht physisch gelöscht werden;
- Wetter: Temperaturen fehlen in der Praxisansicht. Der Portalrenderer kann `tempC` anzeigen, daher muss geprüft werden, ob der aktuelle BTB-Header die Temperaturwerte überhaupt liefert;
- Niederschlagsmenge soll bei fachlich bekanntem „kein Niederschlag“ als 0 statt als fehlender/null-Wert behandelt/angezeigt werden, sofern die Quelle dies eindeutig liefert;
- alle BTB-Fotos sollen ihre GPS-Daten aus Handy- und Portalaufnahme behalten und anklickbar darstellen.


### BTB-EDIT-001 Server-Umsetzung freigegeben
Benutzerauftrag: **weiterbauen**.

Server-Arbeitsumfang dieses Blocks:
1. Entry-PATCH um versionierten Abschnittswechsel 4–8 und `worksiteNodeKey`-Validierung erweitern.
2. BTB-Fotoverknüpfung um versionierte linkbezogene Fachmetadaten erweitern; Original-/Aufnahmedaten nicht verändern.
3. Portal-BTB auf dieselbe Baustellenbaumstruktur anbinden und Bearbeiten von Punkt, Firma, Unterbaustelle, Zeit/KM/Text ermöglichen.
4. Bestehenden bestätigten SSE-Live-Refresh nicht umbauen.
5. Source-Checks ausführen; Deployment/E2E bleibt danach getrennt offen.
6. Android-`app/**` wird in diesem SERVER-Block nicht geändert.


### BTB-EDIT-001 – Server-Quellblock umgesetzt
Status: **QUELLSTAND FERTIG / DEPLOY + MIGRATION 016 + E2E OFFEN**

Server-Umsetzung:
- `PATCH /btbs/{btb_id}/entries/{entry_id}` kann denselben Eintrag versioniert zwischen Punkt 4–8 verschieben.
- `worksiteNodeKey` wird gegen den kanonischen Worksite-Node-Baum validiert.
- Migration `016_btb_photo_link_metadata.sql` ergänzt versionierte BTB-spezifische Foto-Verknüpfungsmetadaten.
- `PATCH /btbs/{btb_id}/photos/{file_id}` ändert die fachliche Foto-Zuordnung, ohne Foto-Datei, Aufnahmezeit oder GPS umzuschreiben.
- BTB-Detail liefert effektive Foto-Metadaten + `link_version`.
- Portal lädt denselben Worksite-Baum wie Android und kann bei Punkt 4–8 Firma/Allgemein, Unterbaustelle, Zeit, KM und Text bearbeiten.
- Fotos können im Portal nachträglich Punkt/Firma/Unterbaustelle/Text/KM zugeordnet werden.
- Unterbaustellen werden hierarchisch und Android-nah dargestellt, z. B. freie Strecke mit KM-Bereich.
- bestätigter SSE-Live-Refresh bleibt erhalten.

Quellprüfung:
- Portal-JavaScript Syntax: **PASS**.
- Source-Assertions 94–100: **PASS**.
- Capture-Schutz für GPS/Aufnahmezeit: **PASS**.
- GitHub Actions aktueller HEAD: kein Testnachweis, da keine Runner-Schritte geliefert wurden.

Parallelstand ANDROID:
- aktueller Android-HEAD `7c7cf7b502ec1f701f167c13780b10a014bba6d9`;
- Entry-Modell/Node-Key/Entry-Identität bereits source-seitig in Umsetzung;
- Android-Foto-Umhängen mit stabilem `worksiteNodeKey`/Foto-PATCH noch offen.

Noch nicht behaupten:
- nicht deployed;
- Migration 016 nicht praktisch angewendet;
- kein Server-Runtime-/Geräte-E2E für BTB-EDIT-001.


### INT-001 – Android-Arbeitsblock vom Benutzer als fertig bestätigt
Benutzerbestätigung am 26.09.2026: **„Android ist fertig“**.

Einordnung gemäß Testregeln:
- die Bestätigung beendet den laufenden Android-Entwicklungsblock und gibt die Branch-Integration an SERVER frei;
- sie ist **keine** nachträgliche Behauptung eines erfolgreichen Builds 1.3.25/200075;
- laut aktuellem Android-HEAD `a1b4667b110a993796e8242d4a21be3748c06d68` wurde der CI-Runner nicht zugewiesen und keine Gradle-Step ausgeführt;
- Build/Installation/BTB-EDIT-001-Geräte-E2E bleiben deshalb getrennt offen.

Nächster Arbeitsblock: SERVER löst `INT-001`, integriert Server- und Android-Branch konfliktbewusst und schützt dabei `server/**` bzw. `app/**`.
