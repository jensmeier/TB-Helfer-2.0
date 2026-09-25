# TB-Helfer 2.0 – Server/Android-Syncvertrag ab 25.09.2026

## Zweck

Dieses Dokument ist die verbindliche Übergabe vom Projekt **SERVER** an das Projekt **ANDROID**.
Der Server bleibt die gemeinsame Wahrheit für BTB-Identität, aktiven Team-BTB, Teilnehmerstatus,
gemeinsame BTB-Inhalte, Dateiverknüpfungen, Versionsstände, FINALIZED-Status und Abschluss-PDF.

Stand bei Erstellung:
- SERVER-Branch: `feature/tb-server-1.0`
- ANDROID-Branch geprüft: `feature/tb-android-2.0`
- Android-Ausgangsstand: `8c2dd7dbc33b8f1cc6136429b3f8382799aa7354`
- Migrationen Synology-Staging: 14/14 praktisch bestätigt
- BTB-Recovery 24.09 → FINALIZED → 25.09 Upload praktisch bestätigt

## 1. Capabilities zuerst lesen

Android soll `GET /api/v1/capabilities` verwenden. Für die hier beschriebenen Verträge sind insbesondere
folgende Capabilities maßgeblich:

- `btb_entries_v1`
- `btb_participant_shift_end_v1`
- `btb_pdf_team_lead_only_v1`
- `btb_general_work_entries_v1`
- `btb_photo_metadata_v1`
- `file_image_renditions_v1`
- `sync_change_feed_v1`
- `sync_change_wait_v1`

Fehlt eine Capability, darf Android nicht still einen inkompatiblen neuen Pfad voraussetzen.

## 2. BTB-Rollen- und Schichtvertrag

`GET /api/v1/btbs/{btb_id}` liefert neben `btb`, `entries`, `photos` und `final_pdf` auch:

- `participants[]`
- `current_user.is_team_lead`
- `current_user.participant_status`
- `current_user.can_view_btb_pdf`
- `current_user.can_finalize_btb`

Android MUSS diese Felder verwenden.

### Helfer

Helfer beendet nur seine eigene Schicht:

`POST /api/v1/btbs/{btb_id}/shift-finish`

Fortsetzen:

`POST /api/v1/btbs/{btb_id}/shift-resume`

Ein Helfer darf den gemeinsamen BTB nicht per `/finalize` abschließen.

### Haupt-TB

Nur der Haupt-TB finalisiert:

`POST /api/v1/btbs/{btb_id}/finalize`

Wenn noch andere Teilnehmer `WORKING` sind, liefert der Server
`409 BTB_PARTICIPANTS_NOT_FINISHED` mit den offenen Teilnehmern.

Nur nach einer **bewussten Benutzerbestätigung** darf Android erneut senden:

`{"allow_unfinished": true}`

Der Haupt-TB muss dabei vorher sehen, welche Teilnehmer noch nicht fertig sind.

### Abschluss-PDF

Das Abschluss-PDF ist nur für den Haupt-TB freigegeben. Android richtet Anzeige/Download nach
`current_user.can_view_btb_pdf` und `final_pdf`.

## 3. Historische/finalisierte BTBs sind read-only

Ein BTB mit Serverstatus `FINALIZED`, der nicht mehr `active_btb_id` des Teams ist, darf im automatischen
Sync **nicht** erneut per Header-/Entry-/Foto-Mutation beschrieben werden.

`BTB_NOT_ACTIVE` ist in diesem Fall ein korrekter Serverschutz und kein Anlass, den Server zu lockern.

Änderungen an einem alten BTB sind nur nach bewusstem Reopen/Nachbearbeitungsweg zulässig.

Android muss deshalb:
- historische FINALIZED-BTBs aus automatischem Header-/Entry-Upload ausschließen,
- historische FINALIZED-BTBs aus automatischem Medienupload ausschließen,
- alte Dirty-/Pending-Zustände nach bestätigtem Serverabschluss sauber auflösen.

## 4. Granulare Einträge und stabile Slots

Der Server verwendet `payload._tb_android_managed=true` und
`payload._tb_android_slot` als stabile fachliche Slot-Identität.

Android darf beim Rundlauf **Server → Android → Server** einen vorhandenen Slot nicht aus einem neuen
Arrayindex neu erzeugen.

Beispiel:
- Server: `abnahme:0`, `abnahme:2`
- Android lädt herunter
- Android darf daraus beim nächsten Upload NICHT `abnahme:0`, `abnahme:1` machen.

Die vorhandene Slot-ID muss lokal erhalten und wieder gesendet werden.

Das gilt für:
- Firmen
- Personal
- Geräte
- Arbeiten
- Abnahmen
- Einweisung/Unterweisung
- Besonderheiten
- Mängel/Schäden

## 5. Punkt 4 „Ausgeführte Arbeiten“

Allgemeine Arbeiten ohne Firma sind echte `ARBEIT`-Einträge.

Der Server/Portal erzeugt dafür Slots:

`arbeit-frei:<n>`

mit Payload z. B.:

`{"text":"Stopfen begonnen","uhrzeit":"10:00"}`

Android muss `SECTION_ARBEIT` **auch ohne `firmaId`** übernehmen und als Punkt 4 anzeigen.
Diese Einträge dürfen nicht als `notizen` behandelt oder verworfen werden.

Alte historische `NOTIZEN`-/`ALLGEMEIN`-Daten bleiben nur lesbar für Kompatibilität; neue allgemeine
Arbeiten werden serverseitig nicht mehr als NOTIZEN erzeugt.

## 6. Fotos – kanonischer Serververtrag

Der Server akzeptiert weiterhin Androids bisherige `BTBP1__...`-Dateinamen und dekodiert daraus
Metadaten, wenn beim Upload keine strukturierten Werte mitgesendet wurden.

Neu liefert der Server bei BTB-Fotos zusätzlich kanonisch:
- `captured_at`
- `gps_lat`
- `gps_lon`
- `gps_accuracy_m`
- `photo_metadata` mit u. a. Typ, Firma, Kurztext, KM, Objekt/Bereich, Indikator (wenn bekannt)

Android darf beim Import **nicht mehr verlangen**, dass der Dateiname mit `BTBP1__` beginnt.
Portal-Fotos mit normalen Dateinamen müssen ebenfalls importiert werden.

Empfohlene Priorität beim Android-Import:
1. strukturierte Serverfelder/`photo_metadata`
2. `captured_at` + GPS-Serverfelder
3. `BTBP1__` nur als Rückwärtskompatibilität
4. falls keine Kategorie vorhanden: `FOTODOKUMENTATION`

Beim Android-Upload sollen bekannte strukturierte Metadaten zusätzlich im
`POST /api/v1/uploads/{upload_id}/complete` gesendet werden:
- `captured_at`
- `gps_lat`
- `gps_lon`
- `gps_accuracy_m`

Für Bilder liefert der Server außerdem unter `renditions` kleinere Downloadpfade:
- `thumb` → maximal 320×320 px
- `preview` → maximal 1280×1280 px

Direkter Vertrag:
- `GET /api/v1/files/{file_id}/rendition/thumb`
- `GET /api/v1/files/{file_id}/rendition/preview`

Die Renditions werden serverseitig speicherbegrenzt erzeugt, als JPEG gecacht und verwenden dieselbe
Berechtigungsprüfung wie das Original. Android soll für Listen/Galerien bevorzugt `thumb` bzw.
`preview` laden und das Original nur bei Bedarf öffnen/exportieren.

Der BTBP1-Dateiname bleibt Übergangskompatibilität, nicht die dauerhafte einzige Datenquelle.

Wenn eine BTB-Fotoverknüpfung serverseitig entfernt wurde, muss Android die lokale **BTB-Zuordnung**
entfernen. Das lokale Originalfoto muss dadurch nicht automatisch gelöscht werden.

## 7. Automatischer Server → Android Delta-Pull

Neuer Serververtrag:

`GET /api/v1/sync/changes/wait`

Parameter:
- `after=<persistenter Cursor>`
- optional `worksite_id=<uuid>`
- optional `entity_types=BTB,BTB_ENTRY,BTB_PARTICIPANT,FILE,...`
- `limit` (1–1000)
- `wait_seconds` (0–30, empfohlen ca. 25)

Antwort:
- `changes[]`
- `next_after`
- `head`
- `caught_up`

Der Server verwendet einen gemeinsamen Wake-Notifier pro API-Prozess und begrenzt parallele
Delta-DB-Abfragen. Android soll daher **Long-Poll/Delta-Cursor** statt Vollsync-Zeitplan verwenden.

Android muss:
- Cursor persistent speichern,
- auf App-Start/Resume und Netzrückkehr sofort Delta nachziehen,
- während sinnvoller Online-Phasen Long-Poll weiterführen,
- aus Events nur die betroffenen Fachobjekte/BTBs nachladen,
- Cursor erst nach sicher verarbeitetem Batch persistent fortschreiben,
- bei Fehlern denselben Cursor erneut verwenden,
- `Neu laden` nur als Recovery-/Vollabgleich behalten.

Für den BTB-Pfad sind mindestens `BTB,BTB_ENTRY,BTB_PARTICIPANT,FILE` relevant.

## 8. Android Outbox/Retry

Selektiver BTB-Upload bleibt richtig:

lokale BTB-Änderung → persistenter Dirty-Scope → nur dieser BTB zum Server.

Ein Dirty-Scope darf nach fünf Fehlern nicht einfach ohne geplante Fortsetzung liegenbleiben.
Solange Dirty vorhanden ist, muss Android mit begrenztem Backoff später erneut planen.

Netz-/Serverausfall über Stunden darf keinen manuellen App-Neustart erfordern.

## 9. Alten allgemeinen Abschluss-Sync entfernen

Der gezielte Pfad `SofortSync.btbAnstossen(... btbId ...)` ist für BTB-Änderungen maßgeblich.

Der zusätzlich noch vorhandene allgemeine `SofortSync.anstossen(...)`-Pfad nach erkanntem
BTB-Abschluss ist zu prüfen und zu entfernen/deaktivieren, wenn er denselben Abschluss noch einmal
als Vollsync anstößt.

Kein paralleler selektiver BTB-Sync plus alter Vollsync für denselben Abschluss.

## 10. E2E-Abnahmekette für ANDROID + SERVER

Erst nach diesem praktischen Test gilt Gesamt-Sync als BESTÄTIGT:

1. Pixel/Haupt-TB ändert Punkt 4 → Server → Poco sieht Änderung automatisch.
2. Poco/Helfer ergänzt Punkt 5/7 → Server → Pixel sieht Änderung automatisch.
3. Portal ändert Wetter/Eintrag → beide Handys sehen Änderung automatisch.
4. Portal lädt normales BTB-Foto hoch → beide Handys erhalten Foto auch ohne BTBP1-Dateiname.
5. Fotoverknüpfung im Portal entfernen → BTB-Zuordnung verschwindet auf beiden Handys.
6. Poco drückt `Schicht Ende` → Serverparticipant = FINISHED → Pixel sieht FERTIG automatisch.
7. Pixel finalisiert → Server = FINALIZED, `active_btb_id=NULL`, Abschluss-PDF entsteht ohne OOM.
8. Beide Geräte erhalten FINALIZED-Zustand automatisch; nur Haupt-TB bekommt Abschluss-PDF.
9. Alter FINALIZED-BTB erzeugt keine erneuten Header-/Entry-/Foto-Mutationen und kein `BTB_NOT_ACTIVE`.
10. Offline-Test: Server/Netz länger weg → Änderungen bleiben pending und laufen nach Rückkehr ohne manuellen Vollsync weiter.

## 11. Zuständigkeitsgrenze

- SERVER-Projekt ändert kein `app/**`.
- ANDROID-Projekt ändert kein `server/**`.
- Benötigt Android einen neuen Serververtrag, zuerst im Android-Projekt klar dokumentieren und im
  SERVER-Projekt umsetzen lassen.
- Keine stille inkompatible API-Änderung.
