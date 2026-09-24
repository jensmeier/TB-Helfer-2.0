# TB-Helfer 2.0 – Sync-Zielbild 15×15

Stand: 24.09.2026  
SERVER: `feature/tb-server-1.0`  
ANDROID geprüft: `feature/tb-android-2.0` @ `ac972afecf96504b2f13dd697895100cf4a27fee` (1.3.16 / 200066)

## 1. Lastmodell

Für Planung und E2E/Lasttest wird bewusst mit Faktor 15 gerechnet:

- 15 verantwortliche TBs;
- je TB 15 Helfer = 225 Helfer;
- zusammen 240 mobile Nutzer/Geräte;
- zusätzlich 15 gleichzeitig offene Portal-/Laptop-Sitzungen;
- Planungsfall somit **255 aktive Clients** auf derselben Serverinstanz.

Die Architektur soll nicht davon abhängen, dass Benutzer auf „Synchronisieren“ drücken.

## 2. Fachlicher Zielablauf

### Gerät A ändert etwas

1. Änderung wird lokal/offline sicher gespeichert.
2. Persistenter Dirty-/Outbox-Eintrag markiert nur das betroffene Objekt.
3. Kurzer Debounce fasst schnelle Folgeschreibvorgänge zusammen.
4. Gerät sendet nur das Delta, z. B. genau einen BTB/Eintrag.
5. Server speichert mit Versions-/Konfliktschutz und erzeugt einen dauerhaften Change-Feed-Eintrag.
6. Server stößt berechtigte andere Geräte nur mit einem **inhaltsfreien Wake-Signal** an.
7. Empfänger lesen ab ihrem Cursor die Changes und laden nur die betroffenen Objekte.

### Portal/Laptop ändert etwas

Das Portal verwendet bereits dieselben kanonischen Server-1.0-Fachendpunkte, z. B.:
- `PATCH /api/v1/btbs/{id}/header`
- `POST/PATCH/DELETE /api/v1/btbs/{id}/entries...`

Diese Serverpfade erzeugen bereits `emit_change(...)`. Damit kann Portal → Server → Handys denselben Delta-Vertrag verwenden wie Handy → Server → andere Handys.

## 3. BTB mit 15 Helfern

Ein TB-Team besteht im Lastmodell aus 1 TB + 15 Helfern = 16 mobilen Nutzern.

Ein neuer/änderter BTB:
- wird nur im Team des verantwortlichen TBs gemeinsam geführt;
- weckt nur dieses Team (Actor-Gerät ausgenommen), nicht alle 240 mobilen Nutzer;
- andere 14 TB-Teams bleiben unberührt.

Bei 15 gleichzeitig arbeitenden TB-Teams können somit bis zu 15 unabhängige BTB-Ströme parallel laufen.

Der bestehende Serververtrag ist dafür grundsätzlich geeignet:
- BTB besitzt stabile ID;
- BTB-Einträge besitzen eigene IDs/Versionen;
- `base_version` schützt konkurrierende Änderungen;
- jede Entry-Änderung erzeugt zusätzlich einen Eltern-`BTB`-Change;
- `GET /api/v1/btbs/{id}` kann genau einen BTB nachladen.

Bei zwei Änderungen am **gleichen Eintrag** muss ein Versionskonflikt sichtbar behandelt werden; kein Last-Write-Wins ohne Warnung.

## 4. Warum der heutige automatische Sync noch nicht genügt

Android aktuell:
- `SyncEinstellungenSpeicher`: `MANUELL / STUENDLICH / TAEGLICH`;
- Default: stündlich;
- `SofortSync` startet den normalen breiten `SyncWorker`;
- dieser verarbeitet mehr als nur das geänderte Objekt.

Folge: Ein um 07:00 neu erzeugter BTB kann auf einem Helfer-Handy fehlen, bis ein manueller/periodischer Lauf erfolgt.

Verbindliche Änderung:
- die Zeitplan-Auswahl wird aus Android entfernt;
- normale kleine Deltas laufen automatisch;
- `Neu laden` bleibt Recovery/Vollabgleich.

## 5. Warum der heutige SSE-Pfad NICHT für 255 Clients genutzt werden darf

Aktuell pollt `GET /api/v1/sync/events` je offener Verbindung alle 2 Sekunden:
- `_changes_for_worksite(...)`
- öffnet dabei jeweils eine neue Psycopg-Verbindung;
- `database.py` verwendet aktuell keinen Connection Pool;
- Synology-Compose startet aktuell einen einzelnen Uvicorn-Prozess.

Bei 255 dauerhaft offenen SSE-Clients wären das im Leerlauf rechnerisch:
- 255 / 2 = **127,5 DB-Verbindungsaufbauten + Change-Queries pro Sekunde**;
- ca. 459.000 solcher Polls pro Stunde.

Das ist für den NAS unnötige Last und darf nicht das Mobile-Zielmodell werden.

## 6. Skalierbares Wake-/Delta-Modell

### Mobile Android

Kein dauerhaftes Mobile-SSE.

Stattdessen:
1. Server schreibt dauerhaften Change-Feed.
2. Server sendet ein inhaltsfreies Push-Wake an berechtigte Geräte.
3. Push enthält keine BTB-Texte, Fotos oder fachlichen Nutzdaten.
4. Android startet einen kleinen Delta-Worker.
5. Delta-Worker liest `/api/v1/sync/changes?after=<cursor>`.
6. Changes werden nach `entity_type/entity_id` dedupliziert.
7. Nur betroffene Objekte werden nachgeladen.
8. Cursor wird erst nach erfolgreicher lokaler Anwendung fortgeschrieben.

Sicherheitsnetz:
- App-Start/Resume → Delta prüfen;
- Netz kommt zurück → Delta prüfen;
- kleiner periodischer Delta-Fallback (Android-Systemgrenze beachten);
- Push verpasst → kein Datenverlust, da Change-Feed dauerhaft ist;
- Cursor inkonsistent/zu alt → kontrollierter `Neu laden`-Recovery.

### Portal/Laptop

Maximaler Planungsfall: 15 offene Browser.

Portal darf Live-Updates benutzen, aber nicht den heutigen 2-Sekunden-DB-Poll pro Verbindung.

Ziel:
- ein gemeinsamer Server-Notifier (z. B. PostgreSQL LISTEN/NOTIFY oder gleichwertiger zentraler Event-Hub);
- durable Wahrheit bleibt `tb.change_feed`;
- Browser-SSE/WebSocket ist nur Wake/Live-Signal;
- Reconnect liest ab letzter `change_id` nach.

## 7. Fanout / Berechtigung

Der heutige Change-Feed ist worksite-basiert und hat Indizes:
- `change_feed(company_id, change_id)`
- `change_feed(worksite_id, change_id)`

Für 15×15 muss der Fanout feiner werden:
- BTB / BTB_ENTRY / BTB-Foto → **TEAM**;
- Worksite-Baum → berechtigte Baustellenmitglieder;
- Mangel/Abnahme → nach bestehender Bereichs-/Rollenfreigabe;
- globale Katalogänderung → nur interessierte/aktive Clients, nicht zwingend Sofort-Push an alle.

Empfehlung für nächsten Server-Schritt:
- Change-Scope/Audience explizit im Eventvertrag abbilden (TEAM / WORKSITE / COMPANY / USER);
- Push-Registrierung pro aktivem Gerät;
- Wake-Zielgruppen serverseitig aus Rechte-/Teamdaten bestimmen.

## 8. DB-/API-Lastschutz

Heute:
- jede DB-Operation öffnet direkt `psycopg.connect(...)`;
- kein `psycopg_pool`;
- ein Uvicorn-Prozess.

Vor 15×15-E2E:
- Connection Pool einführen oder äquivalentes DB-Pooling;
- Delta-Endpunkte klein halten und vorhandene Cursor-Indizes nutzen;
- Wake-Bursts pro Gerät coalescen;
- Hintergrundgeräte mit kurzem Jitter verteilen;
- Actor-Gerät nicht unnötig erneut wecken;
- 15 Teams getrennt fanouten.

Ziel bei 15 gleichzeitig geänderten Team-BTBs:
- maximal ein Wake/Delta-Lauf pro betroffenem Gerät;
- keine 15 Vollsyncs pro Gerät;
- keine 240-fache Verteilung eines einzelnen teaminternen BTBs.

## 9. Medien/Fotos

Verbindliches Hybridmodell:

### Mobile Aufnahme
- Kameroriginal bleibt unverändert auf dem Aufnahme-Handy.
- Online-Sync erzeugt nur:
  - stabile Foto-ID + Metadaten/Bildlisten-Eintrag;
  - kleine BTB-/Portal-Sync-Version (Richtwert längste Kante ca. 1920 px, JPEG 80–82 %, nie hochskalieren);
  - Thumbnail ca. 512 px.
- Server und verantwortlicher TB erhalten die kleine Sync-Version; allgemeine Helfergeräte müssen weiterhin nicht jedes fremde Helferfoto automatisch als Bilddatei vorladen.
- Bildlisten/BTB-Zuordnungen sind trotzdem auf allen berechtigten Geräten vollständig bekannt.

### Portal-/Laptop-Aufnahme
- Wird ein Bild im Portal hochgeladen, ist die dort empfangene Datei die serverseitige Quelldatei und darf auf dem NAS erhalten bleiben.
- Der Server erzeugt daraus dieselbe kleine Sync-Version + Thumbnail.
- Mobile Geräte erhalten nur diese kleine Version bzw. Thumbnail, nicht ungefragt die volle Portaldatei.

### Originalübergabe nach Schicht
- Handy-Originale werden nicht über den normalen Delta-Sync vervielfacht.
- Jeder Nutzer kann seine Originale nach Schichtende per Laptop/USB an den verantwortlichen TB übergeben.
- Für die spätere Umsetzung ist ein Exportmanifest mit stabilen Foto-IDs, BTB-ID, Ersteller-ID, Dateiname, Aufnahmezeit und Prüfsumme vorzusehen, damit 15 Nutzer ihre Originale kollisionsfrei in einen gemeinsamen Laptop-Bestand zusammenführen können.
- Die USB-/Laptop-Originalübergabe ist ein Archiv-/Übergabeprozess und kein Grund, den fachlichen Online-BTB-Abschluss zu blockieren.

Lastbeispiel:
- 240 mobile Nutzer × 15 Fotos/Tag = 3.600 Fotos/Tag.
- Volloriginale werden dadurch nicht automatisch auf NAS und anschließend auf Teamgeräte vervielfacht.
- Online-Last besteht primär aus kleinen Sync-Versionen/Thumbnails/Metadaten; Originalarchivierung bleibt separater manueller bzw. später optionaler WLAN-Prozess.

## 9a. Schichtende / gemeinsamer BTB

Ein Team-BTB bleibt ein gemeinsamer BTB des verantwortlichen TBs und seiner Helfer.

Deshalb gibt es zwei getrennte Zustände:

1. **Nutzerbeitrag / Schicht fertig**
   - jeder beteiligte Nutzer markiert nur seinen eigenen Beitrag als fertig;
   - zuvor werden lokale Fachänderungen und kleine Bild-Sync-Versionen übertragen;
   - der gemeinsame BTB bleibt offen;
   - eigener Status ist serverseitig sichtbar und kann vor dem finalen Abschluss bewusst wieder geöffnet werden.

2. **BTB endgültig abschließen**
   - ausschließlich verantwortlicher Haupt-TB / Teamleiter;
   - sieht Status aller am BTB beteiligten Nutzer;
   - normaler Abschluss erst, wenn alle beteiligten Nutzer fertig bzw. bewusst als nicht beteiligt markiert sind;
   - falls trotzdem abgeschlossen werden muss, müssen offene Nutzer ausdrücklich angezeigt und ein Ausnahmeabschluss bestätigt werden;
   - danach greift der bestehende BTB-Finalize-/Original-Snapshot-/PDF-Vertrag.

Der endgültige BTB-Abschluss benötigt **nicht** die manuellen Volloriginale vom USB-Stick. Er benötigt aber vollständig synchronisierte Fachinhalte, Bildmetadaten und kleine Server-Sync-Versionen.


### Rollenabhängige Android-Bedienung

**Helfer:**
- untere Hauptaktion heißt `Schicht Ende`, nicht `BTB Abschluss`;
- kein `PDF Entwurf`;
- kein Öffnen/Anzeigen des BTB-Abschluss-PDF;
- nach bestätigtem Schichtende eigener BTB-Bereich read-only, mit bewusster Aktion `Schicht fortsetzen` solange der Teamleiter noch nicht finalisiert hat;
- eigener Originalbilder-Export bleibt unabhängig verfügbar.

**Verantwortlicher Haupt-TB / Teamleiter (`teams.lead_user_id`):**
- sieht Teilnehmerstatus;
- darf PDF-Entwurf/Abschluss-PDF nutzen;
- besitzt allein `BTB endgültig abschließen`;
- die Teamleiter-ID ist maßgeblich, nicht nur ein sichtbares Rollenlabel.

### Sync-Sicherheit beim Schichtende

`Schicht Ende` ist ein kleiner Workflow:
1. lokale Eingaben speichern;
2. selektiven BTB-/Bildmetadaten-Sync ausführen;
3. kleine Bild-Sync-Versionen bestätigen;
4. erst danach serverseitigen Teilnehmerstatus auf `FERTIG` setzen.

Bei Offline/Fehler bleibt der lokale Zustand `SCHICHTENDE_AUSSTEHEND`; der Haupt-TB sieht serverseitig weiter `ARBEITET` bzw. `SYNC_AUSSTEHEND` und kann den Ausnahmeabschluss bewusst entscheiden.

### Aktuell nachgewiesene Lücke

Server 1.0 schützt `POST /api/v1/btbs/{id}/finalize` bereits mit `btb.finalize`; `HELPER_STANDARD` besitzt dieses Recht nicht.

Der aktuelle Abschluss-PDF-Endpunkt `GET /api/v1/btbs/{id}/final-pdf` prüft jedoch nur `btb.view`. Da Helfer `btb.view=true` besitzen, ist die neue Fachregel serverseitig noch nicht umgesetzt. Vor E2E ist ein separates PDF-Leserecht/Teamleiter-Guard erforderlich.


## 10. Autosave-/Schreiblast

Nicht jede Taste darf einen Netzrequest erzeugen.

Verbindlich:
- lokal darf häufig gespeichert werden;
- Netz-Dirty-Scope wird pro Objekt zusammengeführt;
- kurzer Debounce nach letzter Änderung;
- laufender Worker darf eine währenddessen neu gesetzte Dirty-Markierung nicht verlieren.

Planungsbeispiel:
- 240 mobile Nutzer × 15 fachliche Änderungen pro Stunde = 3.600 Deltas/Stunde ≈ 1 Delta/Sekunde im Mittel.
- Das ist für PostgreSQL/API mit kleinen indizierten Requests grundsätzlich unkritisch; Bursttests bleiben erforderlich.

## 11. Geplanter Bau in Reihenfolge

1. Android-Zeitplan `stündlich/täglich/manuell` entfernen; `Neu laden` nur Recovery.
2. Persistenter selektiver BTB-Outbox-/Worker (nur geänderter BTB).
3. Server Change-Feed mit Zielgruppe/Team-Scope + Push-Device-Registrierung.
4. Android Push-Wake + Delta-Cursor + App-Resume/Netz-Fallback.
5. Neuer BTB auf TB-Handy → automatisch aufs 15-Helfer-Team.
6. Portal-BTB-Änderung → automatisch aufs Team; Handyänderung → offenes Portal live.
7. Server-Live-Notifier ohne per-Client-2s-DB-Polling.
8. DB-Connection-Pool.
9. Optimierte Foto-Sync-Version + Thumbnail/Lazy-Download + optionales Originalarchiv.
10. Schema auf Mängel, Abnahmen, Baum und Streckendaten ausweiten.
11. Lasttest 15×15 + 15 Portal.

## 12. Testkriterien 15×15

Mindestens simulieren:
- 15 Teams;
- 15 Helfer je Team;
- 240 aktive Geräteidentitäten;
- 15 Portal-Sessions;
- 15 parallele BTB-Ströme;
- gleichzeitige Bursts;
- Offline/Online-Wechsel;
- Prozessneustart;
- Push-Verlust;
- Konflikt auf gleichem BTB-Eintrag;
- Fotos/Uploads;
- Recovery über `Neu laden`.

Erfolg bedeutet:
- kein manueller Sync-Button für Normalbetrieb nötig;
- keine fremden Team-BTBs;
- keine verlorenen Änderungen;
- keine unnötigen Vollsyncs;
- keine DB-Verbindungsstürme;
- Portal und Handys konvergieren automatisch auf denselben berechtigten Stand.

## 13. Aktueller Status

**ARCHITEKTUR GEPRÜFT / 15×15-ZIEL TRAGFÄHIG MIT NACHRÜSTUNGEN / HEUTIGER 2s-SSE+VOLLSYNC-PFAD NICHT FÜR 255 CLIENTS FREIGEGEBEN**

Keine DB-/API-Umsetzung in diesem Prüfschritt. Android-`app/**` wurde im SERVER-Projekt nicht geändert.
