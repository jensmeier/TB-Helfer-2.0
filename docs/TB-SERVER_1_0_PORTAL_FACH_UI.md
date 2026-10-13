# TB-Server 1.0 – Portal-Fachoberfläche Baustelle / Team / BTB / Mängel / Abnahmen

Stand: 2026-09-18

Dieses Dokument beschreibt die erste native Fachoberfläche des TB-Server-1.0-Portals. Sie nutzt ausschließlich die bereits vorhandenen `/api/v1`-Fachverträge; es entsteht keine parallele Serverlogik.

## Grundsatz

Die HTML-Seiten sind dünne Browser-Clients.

- Authentisierung ausschließlich über die vorhandene Portal-Session;
- Schreibzugriffe zusätzlich über den bestehenden CSRF-Vertrag;
- Browser-Schreibzugriffe erhalten automatisch eine `X-Client-Change-Id`;
- keine Geräte-Bearer-Tokens oder Aktivierungsschlüssel im HTML/JavaScript;
- Rechteentscheidungen bleiben in den Fachendpunkten und `worksite_context`;
- die Oberfläche blendet Aktionen zusätzlich anhand der gelieferten effektiven Rechte aus, ersetzt aber nie die serverseitige Rechteprüfung.

## Seiten

### Baustellenübersicht

`GET /portal/v1/worksites`

Verwendete APIs:

- `GET /api/v1/worksites`;
- `POST /api/v1/worksites`;
- für ADMIN bei der Neuanlage zusätzlich `GET /api/v1/admin/companies`.

Die sichtbare Baustellenmenge folgt damit weiterhin dem Rollenmodell der API:

- ADMIN: alle Firmen;
- CHEF: Firma des Benutzers;
- USER/TB/Helfer: aktive Baustellenmitgliedschaften.

### Baustellendetail

`GET /portal/v1/worksites/{worksite_id}`

Die Seite lädt denselben Rechte-/Baustellenkontext wie Android/andere Clients und bündelt:

- Baustellendaten;
- Strukturknoten;
- Mitglieder;
- Teams;
- Team-BTBs;
- Mängel;
- Abnahmen.

Verwendete Read-APIs:

- `GET /api/v1/worksites/{worksite_id}`;
- `GET /api/v1/worksites/{worksite_id}/nodes`;
- `GET /api/v1/worksites/{worksite_id}/members`;
- `GET /api/v1/worksites/{worksite_id}/teams`;
- `GET /api/v1/worksites/{worksite_id}/teams/{team_id}/btbs`;
- `GET /api/v1/worksites/{worksite_id}/defects`;
- `GET /api/v1/worksites/{worksite_id}/acceptances`.

Aktuelle Schreibfunktionen:

- Strukturelement anlegen, bearbeiten und löschen;
- Rechteprofil/Bereich eines bereits vorhandenen Baustellenmitglieds ändern;
- einen bereits beigetretenen Helfer nachvollziehbar zwischen aktiven TB-Teams derselben Baustelle verschieben;
- neuen Team-BTB starten;
- Mangel anlegen, bearbeiten und schließen;
- Abnahme anlegen, bearbeiten und finalisieren.

Neue Mitglieder werden nicht frei per Direktzuordnung ergänzt. Der frühere generische Schreibpfad `POST /api/v1/worksites/{worksite_id}/members` ist aus dem Server-1.0-Vertrag entfernt. Dafür gilt ausschließlich der gezielte Einladungsvertrag: gleicher Firmenkontext, Zielbenutzer vorab festgelegt, Einmalcode nur gehasht gespeichert und Annahme durch genau den eingeladenen Benutzer. Helfer werden bei Annahme dem Team des einladenden verantwortlichen TB zugeordnet; ein eingeladener TB/Verantwortlicher TB erhält sein eigenes Team.

Einladungskomfort Code/Link/QR:

- der Rohcode wird weiterhin nur in der ersten erfolgreichen Create-Antwort ausgegeben;
- zusätzlich liefert dieselbe Antwort eine App-URI `tbhelfer://worksite-invite?token=...`, einen Browser-Link auf `/portal/v1/invitations/redeem#token=...` und einen serverseitig erzeugten SVG-QR-Code;
- QR und App-URI enthalten denselben kurzlebigen Einladungstoken, aber keine Benutzer-Keys/Passwörter;
- der Browser-Link legt den Token bewusst im URL-Fragment statt in Query/Path ab; dadurch wird er beim normalen HTTP-Aufruf der Einlöseseite nicht an den Server gesendet;
- die Einlöseseite liest das Fragment clientseitig, übernimmt den Token in das Formular und entfernt das Fragment anschließend mit `history.replaceState`;
- keine automatische Annahme: der eingeladene Benutzer bestätigt weiterhin ausdrücklich `Einladung annehmen`;
- die Create-Antwort setzt `Cache-Control: no-store`; Mutation Receipt und Audit enthalten weiterhin weder Rohcode noch Link/QR;
- Replay derselben Create-Mutation kann Code/Link/QR nicht rekonstruieren; bei Bedarf muss die alte Einladung widerrufen und neu erzeugt werden.

Der QR-Code ist damit bereits server-/portal-seitig definiert. Die Android-Aktion `QR scannen` wird später auf dieselbe App-URI verdrahtet, ohne einen zweiten Einladungsvertrag einzuführen.

Für bereits beigetretene Helfer existiert getrennt `POST /api/v1/worksites/{worksite_id}/members/{member_user_id}/team`. Dieser Pfad erzeugt **keine** neue Baustellenmitgliedschaft. Er darf nur bestehende `HELPER` zwischen aktiven Teams derselben Baustelle verschieben, erhält historische Teamzuordnungen über `removed_at` und lässt frühere Team-BTBs unverändert.

Wenn das effektive Rechteprofil eine Aktion nicht erlaubt, wird ihr Formular/Knopf nicht angeboten. Die API bleibt trotzdem die verbindliche zweite Schranke.

## BTB-Seite

`GET /portal/v1/btbs/{btb_id}`

Verwendete APIs:

- `GET /api/v1/btbs/{btb_id}`;
- `PATCH /api/v1/btbs/{btb_id}/header`;
- `POST /api/v1/btbs/{btb_id}/entries`;
- `PATCH /api/v1/btbs/{btb_id}/entries/{entry_id}`;
- `DELETE /api/v1/btbs/{btb_id}/entries/{entry_id}`;
- `POST /api/v1/btbs/{btb_id}/reopen`;
- `POST /api/v1/btbs/{btb_id}/finalize`;
- `GET /api/v1/btbs/{btb_id}/final-pdf`;
- `DELETE /api/v1/btbs/{btb_id}`.

Die Oberfläche berücksichtigt den bestehenden Lebenszyklus:

- nur der aktive Team-BTB ist normal bearbeitbar;
- ein `FINALIZED`-BTB wird zuerst bewusst reaktiviert;
- Reaktivieren allein macht das Abschluss-PDF noch nicht ungültig;
- erst eine gespeicherte Kopf-/Eintrags-/Fotoänderung führt serverseitig zu `REWORK`;
- erneut abschließen nutzt das vorhandene Ein-PDF-/Originalmodell;
- bei gültigem Abschluss wird das aktuelle PDF direkt angeboten.

BTB-Fotos nutzen denselben resumierbaren Datei-/Chunkupload wie andere Clients. Der Browser berechnet SHA-256, überträgt in Server-Chunkgrößen und verknüpft beim Abschluss des Uploads mit `entity_type=BTB` / Purpose `BTB_PHOTO`. Der BTB-Detailendpunkt liefert nur öffentliche Foto-Metadaten und `/api/v1/files/{file_id}/content`, niemals interne Storage-Keys. Das Lösen eines Fotos entfernt nur die BTB-Verknüpfung; das Baustellenfoto bleibt erhalten.

## Idempotenz / Browser-Schreibzugriffe

Die gemeinsame Funktion `tbApi()` in `v1_base.html` setzt bei jeder Nicht-GET-Anfrage:

- `X-CSRF-Token` aus dem vorhandenen `tb_portal_csrf`-Cookie;
- `X-Client-Change-Id` über `crypto.randomUUID()`, mit lokalem Fallback.

Damit nutzen Browser-Schreibzugriffe denselben Mutation-Receipt-Vertrag wie andere Clients. Eine einzelne API-Anfrage behält ihre ID; eine bewusst neue Benutzeraktion erhält eine neue ID.

## JSON-Fachpayloads

Mängel, Abnahmen und BTB-Einträge besitzen bereits flexible JSON-Payloads. Die erste Server-1.0-Weboberfläche stellt diese zunächst direkt als JSON-Felder bereit, damit keine fachlichen Felder erfunden oder verloren werden.

Spezialisierte Formulare können später additiv auf diesen stabilen API-Vertrag gesetzt werden.

## Capability

`portal_worksite_fach_ui_v1=true`

Zusätzlich: `worksite_team_transfer_v1=true`, `worksite_invitation_qr_v1=true`

Sie bedeutet, dass die Server-1.0-Weboberfläche mindestens Baustellenübersicht, Baustellendetail und den grundlegenden BTB-/Mängel-/Abnahme-Lebenszyklus auf den bestehenden Fachendpunkten anbietet.

## Tests

`server/checks/37_verify_tb_server_1_portal_fach_ui_source.py` prüft:

- Portal-Seitenrouten;
- Portal-Session statt Bearer-Token;
- CSRF + `X-Client-Change-Id`;
- Baustellen-/Struktur-/Mitglieder-/Team-/BTB-/Mängel-/Abnahme-API-Anbindung;
- BTB-Abschluss/Reaktivierung/PDF;
- Capability-/Workflow-Vertrag;
- dass kein `Authorization`-Bearer in den Browser-Templates eingebaut wird.

## Noch offen

Die erste Fachoberfläche ist absichtlich kein zweiter Client mit eigener Fachlogik. Noch zu ergänzen:

- Android-QR-Scanner/Intent-Handling für die jetzt stabile `tbhelfer://worksite-invite`-URI;
- komfortablere Rechteprofil-/Bereichsauswahl statt Text-/JSON-Dialog;
- spezialisierte statt generischer JSON-Fachformulare;
- Foto-Metadaten-/Auswahlkomfort über den aktuellen Upload hinaus;
- Backup-/Restore-Bedienkomfort im Admin-Portal über den bereits vorhandenen sicheren Serververtrag;
- visuelle Endabnahme und mobile Browserdarstellung gegen den laufenden Testserver.

Produktivdaten/NAS werden durch diesen Oberflächenbaustein nicht verändert.
