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

- Strukturelement anlegen;
- neuen Team-BTB starten;
- Mangel anlegen;
- Mangel schließen;
- Abnahme anlegen;
- Abnahme finalisieren.

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
- erst eine gespeicherte Kopf-/Eintragsänderung führt serverseitig zu `REWORK`;
- erneut abschließen nutzt das vorhandene Ein-PDF-/Originalmodell;
- bei gültigem Abschluss wird das aktuelle PDF direkt angeboten.

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

- Mitglied hinzufügen/verschieben und Rechteprofile bearbeiten;
- bestehende Strukturknoten bearbeiten/löschen;
- Mängel und Abnahmen vollständig bearbeiten, nicht nur erstellen/abschließen;
- BTB-Fotoauswahl/-Upload in der Portaloberfläche;
- spezialisierte statt generischer JSON-Fachformulare;
- Systemstatus/Speicher/Background Jobs/Backup- und Restore-Oberfläche;
- visuelle Endabnahme und mobile Browserdarstellung gegen den laufenden Testserver.

Produktivdaten/NAS werden durch diesen Oberflächenbaustein nicht verändert.
