# TB-Server 1.0 – Firmenprofil, Firmenlogo und PDF-Logo-Layout

Stand: 2026-09-18

Dieses Dokument beschreibt den Server-1.0-Vertrag für zentrale Firmenstammdaten, Firmenlogo und das firmenweite BTB/PDF-Logo-Layout. Maßgeblich bleiben die Projektregeln in `docs/TB-HELFER_PROJEKTREGELN.md`.

## Ziel

Firmenname, Anschrift, Firmentelefon, Firmen-E-Mail und Firmenlogo sind zentrale Firmendaten. Das Logo wird einmal serverseitig hinterlegt und von aktivierten Geräten derselben Firma geladen und lokal gecacht.

TB-Server 1.0 verwendet dafür **keine parallele Legacy-Tabelle `company_profiles`**. Die bereits in der Server-1.0-Baseline vorhandenen Felder werden genutzt:

- `tb.companies.name/street/postal_code/city/phone/email`;
- `tb.companies.settings` für zusätzliche Profilwerte und monotone Profil-/Logo-Versionen;
- `tb.companies.pdf_layout` für das firmenweite PDF-Logo-Layout;
- `tb.companies.logo_file_id` als Referenz auf `tb.files`.

Damit ist für diesen Baustein keine neue Datenbankmigration erforderlich.

## Mobile / allgemeine Read-API

### Firmenprofil

`GET /api/v1/company/profile`

Liefert für die Firma des angemeldeten Portal- oder Gerätebenutzers:

- `company_id` als UUID-String;
- Firmenname;
- `address_line1` / `address_line2`;
- PLZ / Ort;
- Firmentelefon / Firmen-E-Mail;
- Webseite;
- Logo-Metadaten;
- `logo_version`;
- `profile_version`.

Ein Benutzer ohne Firmenzuordnung erhält `COMPANY_NOT_ASSIGNED`.

### Firmenlogo

`GET /api/v1/company/profile/logo`

Liefert das Originalbild unverändert aus dem Server-Storage. Unterstützt werden PNG, JPEG und WEBP. Der HTTP-Response enthält den gespeicherten MIME-Typ und einen ETag auf Basis des SHA-256.

Das Logo liegt im Server-1.0-Storage stabil unter:

`companies/<company_uuid>/profile/logo`

Der Dateiname im Storage ist absichtlich unabhängig vom Bildformat; der MIME-Typ steht in `tb.files`. Geräte dürfen daraus weiterhin ihre lokale Cache-Endung ableiten.

### Firmenweites PDF-Logo-Layout

`GET /api/v1/company/profile/pdf-logo-layout`

`PUT /api/v1/company/profile/pdf-logo-layout`

Der PUT bleibt – wie im bisherigen Schema-12-Vertrag – für ein gültig aktiviertes Gerät derselben Firma verfügbar. Dadurch kann der bestehende Android-PDF-Einstellungsdialog das firmenweite Layout weiter speichern.

Felder:

- `logo_width_pt`: 40–250;
- `offset_x_pt`: -120 bis 120;
- `offset_y_pt`: -60 bis 180;
- `show_company_name`;
- `show_company_email`;
- `show_company_phone`;
- `contacts_in_image`;
- `layout_version`.

Default:

- Breite 90 pt;
- X/Y 0;
- Firmenname/E-Mail/Telefon sichtbar;
- `contacts_in_image=false`;
- Layout-Version 1.

Jede erfolgreiche Layoutänderung erhöht `layout_version` und `profile_version`.

## Admin-API

Nur Portal-ADMIN, für Schreibzugriffe zusätzlich CSRF:

- `GET /api/v1/admin/companies/{company_id}/profile`;
- `PUT /api/v1/admin/companies/{company_id}/profile`;
- `GET /api/v1/admin/companies/{company_id}/logo`;
- `PUT /api/v1/admin/companies/{company_id}/logo`;
- `DELETE /api/v1/admin/companies/{company_id}/logo`;
- `PUT /api/v1/admin/companies/{company_id}/pdf-logo-layout`.

Profilwerte werden in den bestehenden Server-1.0-Firmenfeldern gepflegt. `address_line2`, `website`, `profile_version` und `logo_version` liegen im vorhandenen `settings`-JSON, damit keine unnötige zweite Stammdatentabelle entsteht.

## Logo-Upload

Der Admin-Upload nimmt die Bildbytes direkt als Request-Body an.

Sicherheitsregeln:

- maximal 5 MiB;
- Dateityp wird aus Magic Bytes erkannt, nicht nur aus einem Client-Header;
- erlaubt: PNG, JPEG, WEBP;
- SHA-256 wird serverseitig gebildet;
- Storage-Pfad wird ausschließlich serverseitig erzeugt;
- kein Benutzerpfad wird ungeprüft übernommen;
- temporäre Datei + Rollback-Datei schützen den stabilen Zielpfad bei Fehlern zwischen Dateisystem und DB-Transaktion.

Das Logo wird in `tb.files` als Firmen-Datei geführt. Beim Ersetzen bleibt der stabile Storage-Key erhalten. Beim Löschen wird die DB-Verknüpfung gelöst, der File-Datensatz soft-deleted und der physische Logo-Inhalt nach erfolgreichem Commit entfernt.

## BTB-Abschluss-PDF

Der Server-PDF-Renderer ist auf `tb-server-btb-pdf-2` angehoben.

`server/tbserver/btb.py` lädt beim Abschluss das zentrale Firmen-Branding direkt aus der Firma der Baustelle. Der Renderer berücksichtigt:

- zentrales Firmenlogo;
- Logo-Breite;
- X-/Y-Versatz;
- Firmenname;
- Firmen-E-Mail;
- Firmentelefon;
- `contacts_in_image`.

Das Logo wird proportional eingepasst und erhält im PDF einen weißen Hintergrundbereich, analog zur bestehenden Android-Regel für transparente Logos.

Die fachliche Original-/Nachbearbeitungsregel bleibt unverändert:

- Firmenkopf/Kopfdaten werden nicht als fachliche rote Änderung markiert;
- der unveränderliche erste BTB-Original-Snapshot bleibt unverändert;
- ein späterer Firmenlogo-/Layoutwechsel schreibt den ersten Original-Snapshot nicht um;
- bei einem späteren erneuten BTB-Abschluss wird das dann aktuelle Firmen-Branding verwendet.

## Android-Kompatibilität

Der bestehende Android-Vertrag wird weiterverwendet:

- `CompanyProfileSync` liest `/api/v1/company/profile` und `/logo`;
- `CompanyPdfLogoApi` liest/speichert `/api/v1/company/profile/pdf-logo-layout`.

TB-Server 1.0 verwendet UUIDs für Firmen. Deshalb wurde `TeamManagementApi.CompanyProfile.companyId` von `Long` auf `String` korrigiert. Das ist zugleich mit dem alten numerischen Serverwert kompatibel, da auch dieser als String gelesen werden kann.

## Server-1.0-Portal-/Adminoberfläche

Die erste native Server-1.0-Weboberfläche ist auf denselben JSON-Endpunkten aufgebaut, nicht auf einer zweiten Fachlogik:

- `GET /portal/v1/login` – Portal-Anmeldung über `POST /api/v1/portal/login`;
- `GET /portal/v1` – einfache Portal-Startseite für den angemeldeten Benutzer;
- `GET /admin/v1/companies` – Firmenliste plus Neuanlage;
- `GET /admin/v1/companies/{company_id}/branding` – Firmenstammdaten, Logo-Upload/-Löschen, PDF-Logo-Layout und Browser-Vorschau.

Schreibaktionen der Weboberfläche nutzen ausschließlich die vorhandene Portal-Session und den `tb_portal_csrf`-Cookie als `X-CSRF-Token`. Es werden keine Bearer-Tokens oder sonstigen Geheimwerte in das HTML eingebaut.

Die Browser-Vorschau dient nur der Layoutpositionierung. Der verbindliche Abschluss bleibt der serverseitig gerenderte BTB-PDF-Renderer.

## Capabilities

Neu/aktiv:

- `company_profile_v1=true`;
- `company_logo_v1=true`;
- `company_pdf_logo_layout_v1=true`;
- `company_branding_admin_ui_v1=true`.

Clients sollen diese Capabilities verwenden und nicht aus einer Schema- oder Serverversionsnummer raten.

## Tests

`server/checks/35_verify_tb_server_1_company_branding_source.py` prüft unter anderem:

- Python-Syntax der neuen Servermodule;
- alle Mobile-/Admin-Endpunkte;
- Portal-/Device-/Admin-Auth-Marker;
- 5-MiB-Limit und Magic-Byte-Erkennung;
- stabilen Logo-Storage-Key;
- PDF-Layout-Grenzen und Defaultwerte;
- Nutzung der bestehenden Baseline-Felder statt paralleler Legacy-Tabelle;
- BTB-PDF-Integration und Renderer v2;
- Capabilities / Workflow;
- Android-UUID-Kompatibilität.

Der bestehende Check 34 bleibt der ausführbare ReportLab-PDF-Smoke-Test und wurde auf Renderer v2 aktualisiert.

`server/checks/36_verify_tb_server_1_admin_web_source.py` prüft Portal-/Admin-Webrouten, CSRF-Nutzung, Branding-API-Anbindung, Templates und die Capability `company_branding_admin_ui_v1`.

Die vollständige PostgreSQL-/FastAPI-/Android-End-to-End-Ausführung bleibt abhängig von einer funktionierenden Test-/CI-Umgebung.

## Noch offen

Dieser Baustein liefert den zentralen Daten-/API-/PDF-Vertrag. Noch separat zu bauen bzw. fertigzustellen sind:

- weitere Server-1.0-Portal-Fachseiten für Baustellen/BTB/Mängel/Abnahmen und Systemstatus;
- visuelle Feinabstimmung/Pixel-Parität des Server-PDFs mit dem Android-PDF;
- vollständiger Android-Server-1.0-Cutover;
- Release-Binärupload, Background Jobs sowie Backup/Restore/Reset.
