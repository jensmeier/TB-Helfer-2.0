# TB-Helfer Server 0.7 – verbindlicher API-Vertrag

Stand: 2026-09-09

Dieser Vertrag beschreibt die Server-0.7-Schnittstelle, die Android `versionCode 200018` bereits erwartet.
Er ist aus `CollabSyncApi.kt`, den Sync-Entities und Migration `009_worksite_collaboration.sql` abgeleitet.
Der produktive Server-0.6.0-Quellcode muss gegen diesen Vertrag erweitert werden; es darf kein neuer Server-Unterbau aus Annahmen erfunden werden.

## 1. Grundregeln

- Authentifizierung weiterhin über das bestehende Geräte-Token: `Authorization: Bearer <token>`.
- Das Token muss serverseitig zu einem aktiven Gerät und aktiven Benutzer aufgelöst werden.
- Firmen-, Gruppen- und Bereichsrechte werden **immer serverseitig** geprüft. Clientwerte sind niemals Berechtigungsquelle.
- Baustellen-Teams bleiben innerhalb derselben Firma.
- BTB ist strikt TB-gruppenbezogen.
- Mängel und Abnahmen sind baustellenweit gemeinsam, aber für Hilfsnutzer nur im berechtigten Bereich sichtbar/bearbeitbar.
- Bilder/Anlagen erben die Sichtbarkeit ihres Parent-Vorgangs.
- Endgültige Status werden nicht wieder geöffnet.
- Wiederholte Uploads derselben `client_change_id` müssen idempotent dieselbe Serverantwort liefern.
- Serverversion `0.7.0` darf erst gemeldet werden, wenn die hier beschriebenen Collab-Endpunkte tatsächlich verfügbar sind.

## 2. Bereichs-/Rechteregel

Die gespeicherte Benutzerfreigabe liegt in `tb.worksite_group_members.area_scope`.
Mängel und Abnahmen besitzen zusätzlich einen eigenen Entity-Scope (`area_scope`).

Android kann für einen Haupt-TB mit `{ "all": true }` einen Mangel/eine Abnahme bereits auf einen konkreteren Entity-Scope aus Bereich/Gleis/Weiche/KM verengen. Der Server muss trotzdem selbst prüfen:

- ein ausdrücklich eingeschränkter Benutzer-Scope darf durch Clientdaten niemals erweitert werden;
- `worksite_id`, Firmenzugehörigkeit und TB-Gruppe werden aus Serverdaten validiert;
- bei BTB ist ausschließlich die eigene TB-Gruppe sichtbar;
- bei Mängeln/Abnahmen wird nur ausgeliefert und bearbeitet, wenn der Benutzer für den Entity-Scope berechtigt ist;
- ein Client darf durch manipuliertes `area_scope`, `group_id` oder `worksite_id` keine zusätzlichen Daten erhalten.

Aktuell verwendete Scope-Felder sind insbesondere:

```json
{
  "all": false,
  "labels": ["Gleis 28", "W13"],
  "label": "Gleis 28, W13",
  "km_from_m": 70600.0,
  "km_to_m": 71200.0
}
```

Die endgültige serverseitige Scope-Matching-Funktion muss beim Einbau in den echten 0.6.0-Quellstand zentral implementiert und für Lesen **und** Schreiben identisch verwendet werden.

## 3. `GET /api/v1/collab/context`

Liefert nur Baustellen, denen der aktivierte Benutzer aktuell berechtigt zugeordnet ist.

Erwartete Antwort:

```json
{
  "worksites": [
    {
      "worksite_id": "uuid",
      "name": "Baustelle Pasing",
      "designation": null,
      "worksite_type": null,
      "status": "ACTIVE",
      "company_id": 1,
      "group_id": "uuid",
      "member_role": "LEAD",
      "area_scope": {"all": true},
      "group_label": "TB A",
      "lead_user_id": "uuid",
      "lead_name_snapshot": "Name"
    }
  ]
}
```

Pflicht für Android: `worksite_id` und `group_id` dürfen nicht leer sein.

Serverregeln:

- Benutzer/Gerät muss aktiv sein.
- Baustelle, Benutzer und Gruppe müssen firmenkompatibel sein.
- Gesperrte/inaktive Benutzer dürfen keinen aktiven Kontext erhalten.
- Ein Benutzer darf pro Baustelle nicht gleichzeitig in mehreren TB-Gruppen auftauchen.

## 4. `GET /api/v1/collab/changes`

Request:

```text
/api/v1/collab/changes?worksite_id=<uuid>&after=<sequence>&limit=<1..250>
```

Antwort:

```json
{
  "next_sequence_no": 120,
  "latest_sequence_no": 128,
  "has_more": true,
  "changes": [
    {
      "sequence_no": 118,
      "entity_type": "DEFECT",
      "entity_id": "uuid",
      "operation": "UPDATE",
      "entity_version": 4,
      "changed_at": "2026-09-09T12:00:00Z",
      "changed_by_name": "Nutzer X",
      "from_this_device": false,
      "group_id": null,
      "area_scope": {"all": false, "labels": ["Gleis 28"]},
      "data": {}
    }
  ]
}
```

Zulässige `entity_type`:

- `BTB`
- `DEFECT`
- `ACCEPTANCE`
- `ATTACHMENT`

Zulässige `operation`:

- `CREATE`
- `UPDATE`
- `DELETE`

### Sehr wichtig: Cursor trotz Rechtefilter fortschreiben

`tb.collab_change_log.sequence_no` ist der Scan-Cursor. Der Benutzer bekommt nur berechtigte Datensätze, aber `next_sequence_no` muss auch über **nicht sichtbare** Logzeilen hinweg fortschreiten.

Beispiel:

1. Sequenz 100 = BTB A, für Nutzer A sichtbar.
2. Sequenz 101 = BTB B, für Nutzer A nicht sichtbar.
3. Sequenz 102 = Mangel im Bereich von Nutzer A.

Ein Pull von Nutzer A darf nicht bei 101 hängen bleiben. Der Server scannt die Logfolge weiter und setzt `next_sequence_no` monoton auf den zuletzt abgearbeiteten Scanstand.

Damit werden gleichzeitig verhindert:

- Endlosschleifen an fremden BTB-Gruppen,
- Verpassen späterer berechtigter Änderungen,
- Vermischen der BTB-Gruppen.

### Sichtbarkeit

- `BTB`: nur wenn `group_id` der berechtigten TB-Gruppe entspricht.
- `DEFECT`/`ACCEPTANCE`: nur bei gültiger Baustellen- und Bereichsberechtigung.
- `ATTACHMENT`: nur wenn der Parent-Vorgang für den Benutzer sichtbar ist; BTB-Anlagen bleiben somit ebenfalls gruppenbezogen.
- `from_this_device=true`, wenn `changed_by_device` dem aktuellen Geräte-Token entspricht.

Für `DELETE` muss mindestens Tombstone-Information (`entity_id`, `entity_type`, Version, Sequenz) erhalten bleiben; das Gerät darf gelöschte Objekte nicht still wieder anlegen.

## 5. `POST /api/v1/collab/ack`

Request:

```json
{
  "worksite_id": "uuid",
  "sequence_no": 120
}
```

Serverregeln:

- Gerät/Benutzer muss für die Baustelle aktuell berechtigt sein.
- Sequenz darf nicht negativ sein.
- Der bestätigte Stand wird pro `(device_id, worksite_id)` in `tb.collab_device_state` gespeichert.
- ACK ist monoton: ein älterer ACK darf einen höheren bereits gespeicherten Stand nicht zurücksetzen.
- `last_sync_at` wird aktualisiert.

Die ACK-Daten werden später auch für sicheres temporäres Server-Spooling/Retention benötigt.

## 6. `POST /api/v1/collab/mutations`

Android sendet:

```json
{
  "client_change_id": "android-uuid",
  "worksite_id": "uuid",
  "entity_type": "DEFECT",
  "operation": "UPSERT",
  "entity_id": "uuid-optional-bei-update",
  "client_entity_key": "lokaler-stabiler-key",
  "base_version": 3,
  "group_id": "nur-bei-BTB",
  "status": "IN_ARBEIT",
  "area_scope": {"all": false, "labels": ["Gleis 28"]},
  "payload": {}
}
```

Zulässige Mutationstypen:

- `BTB`
- `DEFECT`
- `ACCEPTANCE`

Zulässige Operationen:

- `UPSERT`
- `DELETE`

### Idempotenz

Schlüssel ist `(device_id, client_change_id)` in `tb.collab_mutation_receipts`.

Wenn dieselbe Änderung nach Netz-/App-Abbruch erneut gesendet wird:

- Mutation nicht erneut fachlich anwenden,
- gespeicherten HTTP-Status und dieselbe fachliche Antwort zurückgeben,
- keine zweite Change-Log-Zeile erzeugen.

Das ist Voraussetzung dafür, dass Android unterbrochene `UPLOADING`-Queueeinträge sicher erneut senden kann.

### Version/409

Bei Änderung eines bestehenden Objekts muss `base_version` gegen `current_version` geprüft werden.

Bei Abweichung:

HTTP `409 Conflict`

```json
{
  "detail": "VERSION_CONFLICT",
  "server_version": 4,
  "server_entity": {}
}
```

Keine stille Last-Write-Wins-Überschreibung.

### Erfolgsantwort

HTTP `200`:

```json
{
  "entity_id": "uuid",
  "entity_version": 4,
  "sequence_no": 123,
  "server_entity": {}
}
```

`server_entity` soll den normalisierten gespeicherten Serverstand enthalten, damit Android seine lokale Version direkt aktualisieren kann.

### Rechte je Typ

#### BTB

- `worksite_id` muss berechtigt sein.
- `group_id` muss serverseitig zur eigenen TB-Gruppe gehören.
- Fremde Gruppe = ablehnen, niemals umhängen.
- Hilfsnutzer derselben Gruppe dürfen denselben BTB bearbeiten.

#### Mangel / Abnahme

- Baustelle muss berechtigt sein.
- Entity-Scope muss innerhalb der Benutzerberechtigung liegen bzw. serverseitig auf diese begrenzt werden.
- Manipuliertes `{ "all": true }` eines eingeschränkten Hilfsnutzers darf seine Rechte nicht erweitern.
- Änderung eines bereits existierenden Vorgangs ist nur erlaubt, wenn dieser Vorgang für den Benutzer sichtbar/berechtigt ist.

### Endgültige Status

Zusätzlich zur API-Prüfung schützt Migration 009 die Datenbank:

- Mangel `ERLEDIGT` darf nicht wieder geöffnet werden.
- Abnahme `ABGENOMMEN` oder `NICHT_ABGENOMMEN` darf nicht wieder in einen anderen Status wechseln.

## 7. Attachments

### Upload

```text
POST /api/v1/collab/attachments/<entity_type>/<entity_id>
Authorization: Bearer ...
Content-Type: <mime-type>
X-Worksite-ID: <uuid>
X-Filename: <dateiname>
<body = raw bytes>
```

Erwartete Antwort:

```json
{
  "attachment_id": "uuid",
  "sequence_no": 124,
  "sha256": "...",
  "size_bytes": 123456
}
```

Serverregeln:

- Parent muss existieren.
- `X-Worksite-ID` wird nur validiert, niemals blind vertraut; maßgeblich ist die Baustelle des Parents.
- Benutzer muss den Parent sehen/bearbeiten dürfen.
- BTB-Parent bleibt auf die eigene Gruppe beschränkt.
- Mangel-/Abnahme-Parent folgt der Bereichsberechtigung.
- Dateiname wird serverseitig bereinigt; `storage_path` wird ausschließlich serverseitig erzeugt.
- SHA-256 wird serverseitig über die empfangenen Bytes berechnet.
- aktive identische Datei wird über `(entity_type, entity_id, sha256)` nicht doppelt gespeichert.
- MIME-/Dateityp darf nicht nur anhand des Client-Headers vertraut werden.
- Bilder sowie Abnahme-Anlagen/PDFs sind vorgesehen; konkrete Größenlimits werden beim Einbau in den echten 0.6.0-Server passend zur bestehenden Upload-Infrastruktur festgelegt.

### Download

```text
GET /api/v1/collab/attachments/<attachment_id>
```

Antwort bei Erfolg:

- Raw Bytes
- korrekter `Content-Type`
- `Content-Disposition` mit Dateiname, sofern vorhanden

Die Downloadberechtigung wird bei jedem Abruf über den Parent erneut geprüft. Eine bekannte Attachment-ID allein ist keine Berechtigung.

Wenn temporär gespeicherte Daten entfernt werden, muss vorher/gleichzeitig ein sauberer `ATTACHMENT DELETE`-Tombstone erzeugt werden, damit Geräte nicht endlos eine bereits entfernte Datei nachladen.

## 8. Change-Log beim Schreiben

Jede erfolgreiche fachliche CREATE/UPDATE/DELETE-Operation erzeugt genau eine passende Zeile in `tb.collab_change_log` innerhalb derselben DB-Transaktion wie die Fachänderung.

Erforderlich:

- `worksite_id`
- bei gruppenbezogenen Daten `group_id`
- `entity_type`
- `entity_id`
- `operation`
- `entity_version`
- `changed_by`
- `changed_by_device`
- Entity-`area_scope`, soweit relevant
- normalisierte Nutzdaten/Tombstone-Payload

DB-Trigger verhindern zusätzlich Gruppe/Baustelle-Mismatches.

## 9. Transaktionsreihenfolge für Mutationen

Empfohlene atomare Reihenfolge im vorhandenen Server-Framework:

1. Geräte-Token/User auflösen und Aktivstatus prüfen.
2. vorhandenen Mutation-Receipt für `(device_id, client_change_id)` suchen; falls vorhanden gespeicherte Antwort zurückgeben.
3. Baustellen-/Firmen-/Gruppen-/Bereichsrecht prüfen.
4. bei bestehender Entity `base_version` prüfen.
5. Fachobjekt schreiben bzw. Tombstone setzen; `current_version` genau einmal erhöhen.
6. Change-Log-Zeile schreiben.
7. Mutation-Receipt mit HTTP-Status und Antwort speichern.
8. Transaktion committen.

So kann kein Zustand entstehen, bei dem die Fachdaten geändert wurden, aber Idempotenzbeleg oder Change-Log fehlen.

## 10. Fehlercodes

Android behandelt derzeit insbesondere:

- `200`: Erfolg
- `401` / `403`: Sitzung ungültig bzw. nicht berechtigt
- `409`: Versionskonflikt
- `404`: bei alten Serverständen fehlender Collab-Endpunkt = Feature noch nicht unterstützt

Für fachliche Fehler innerhalb des vorhandenen 0.7-Endpunkts sollten deshalb möglichst klare `detail`-Codes und passende 4xx-Codes verwendet werden, ohne einen fehlenden Endpunkt vorzutäuschen.

Beispiele für stabile Detailcodes:

- `VERSION_CONFLICT`
- `COMPANY_MISMATCH`
- `GROUP_WORKSITE_MISMATCH`
- `AREA_SCOPE_FORBIDDEN`
- `ENTITY_NOT_VISIBLE`
- `FINAL_STATUS`
- `ATTACHMENT_PARENT_NOT_FOUND`

Die exakte HTTP-Abbildung wird beim Einbau in den echten 0.6.0-Server an dessen bestehende Fehlerbehandlung angepasst.

## 11. Retention / Benutzer-Speicheroptionen

Migration 009 enthält:

- `tb.user_storage_preferences`
- `retain_permanent`
- `expires_at`
- Geräte-ACK-Stände

Verbindlich bleibt:

- Admin kann pro Benutzer Bilder, BTB, Mängel, Abnahmen bzw. „Alles“ dauerhaft speichern lassen.
- Nicht dauerhaft gespeicherte Daten dürfen temporär für Synchronisation auf dem Server liegen.
- Temporäre Bereinigung darf erst erfolgen, wenn die sichere Verteilung/Retention-Regel erfüllt ist.
- Originale Vermessungsdaten werden von dieser Retention niemals verändert.

Die konkrete Cleanup-Job-Implementierung wird erst gegen den echten produktiven Server-0.6.0-Quellstand gebaut, damit bestehende Storage-/Docker-Pfade nicht erfunden werden.

## 12. Noch vor produktivem 0.7 zwingend

1. produktiven `/volume1/docker/tb-helfer/server/`-Quellstand ohne Secrets in den Feature-Branch übernehmen;
2. `server/checks/00_preflight_schema7_for_008_009.sql` read-only gegen Schema 7 prüfen;
3. 0.7-Endpunkte in den echten vorhandenen Server integrieren;
4. Migration 008/009 zunächst nur auf Backup/Testkopie ausführen;
5. `server/checks/01_verify_schema9.sql` ausführen;
6. Android 200018 gegen Server 0.7 testen;
7. Zwei-Geräte-Test mit mindestens TB A + Hilfsnutzer sowie zweiter TB-Gruppe B;
8. BTB-Gruppentrennung, Mängel-/Abnahme-Bereich, Bilder, 409, Tombstones und unterbrochene Uploads testen;
9. erst danach produktiven NAS aktualisieren.
