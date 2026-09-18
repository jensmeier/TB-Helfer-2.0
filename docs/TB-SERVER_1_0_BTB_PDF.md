# TB-Server 1.0 – BTB-Abschluss-PDF / Original / Nachbearbeitung

Stand: 2026-09-18

Dieses Dokument beschreibt den serverseitigen Vertrag für den modernen BTB-Abschluss. Maßgeblich bleiben die Projektregeln in `docs/TB-HELFER_PROJEKTREGELN.md`.

## Ziel

Für eine fachliche `btbId` gibt es höchstens **ein aktuell gültiges Abschluss-PDF**. Sichtbare V1/V2/V3-Dateien werden nicht erzeugt.

Der bestehende Server-1.0-Datenbestand wird weiterverwendet:

- `tb.btbs` für Status und Version;
- `tb.teams.active_btb_id` für den aktuell geöffneten Team-BTB;
- `tb.btb_original_snapshots` für den unveränderlichen ersten Abschlussstand;
- `tb.files` + `tb.file_links` für BTB-Fotos und das aktuelle Abschluss-PDF.

Für diesen Baustein ist deshalb **keine neue Datenbankmigration** nötig.

## Lebenszyklus

### Erster Abschluss

`POST /api/v1/btbs/{btb_id}/finalize`:

1. sperrt den BTB transaktional;
2. liest Kopf, Fach-Einträge und exakt zur `btbId` verlinkte Bilder;
3. erzeugt den aktuellen Snapshot;
4. rendert das PDF zunächst in eine temporäre Datei;
5. installiert das PDF atomar unter einem stabilen Pfad;
6. speichert beim ersten erfolgreichen Abschluss genau einmal den Original-Snapshot;
7. setzt den BTB auf `FINALIZED`;
8. löst `teams.active_btb_id`;
9. veröffentlicht den FILE-/BTB-Change erst zusammen mit der erfolgreichen DB-Transaktion.

Der erste erfolgreiche Snapshot wird danach nicht überschrieben.

### Reaktivieren ohne Änderung

`POST /api/v1/btbs/{btb_id}/reopen` setzt bei einem abgeschlossenen BTB nur `active_btb_id` wieder auf dieselbe `btbId`.

Wichtig:

- Status bleibt zunächst `FINALIZED`;
- das vorhandene Abschluss-PDF bleibt gültig;
- der Original-Snapshot bleibt unverändert;
- Reaktivieren allein ist **keine Nachbearbeitung**.

Wird ein solcher BTB ohne tatsächliche Änderung wieder abgeschlossen, wird lediglich `active_btb_id` gelöst. Das bestehende PDF wird nicht unnötig neu gerendert.

### Tatsächliche Änderung nach Abschluss

Erst eine tatsächlich gespeicherte Änderung an

- BTB-Kopfdaten,
- Fach-Einträgen oder
- der ausgewählten BTB-Foto-Menge

führt zu:

- `FINALIZED -> REWORK`;
- `finalized_at = NULL`;
- Invalidierung des aktuellen Abschluss-PDF;
- Entfernung der bisherigen PDF-Datei nach erfolgreichem Commit.

Die Änderung ist nur erlaubt, wenn genau diese `btbId` für das Team aktiv ist. Direkte Schreibzugriffe auf einen nicht bewusst reaktivierten abgeschlossenen BTB werden mit `BTB_NOT_ACTIVE` abgelehnt.

### Erneuter Abschluss

Ein `REWORK`-BTB wird erneut gegen **denselben ersten Original-Snapshot** abgeschlossen.

Das neue aktuelle PDF ersetzt das vorherige PDF derselben `btbId`. Der Original-Snapshot wird nicht ersetzt.

## Stabiler PDF-Pfad

Der serverseitige Storage-Key lautet:

`companies/<company_uuid>/worksites/<worksite_uuid>/btb/<btb_uuid>/abschluss.pdf`

Damit entstehen keine sichtbaren `_V1`, `_V2`, `(2)` oder sonstigen Ausweichnamen.

Die zugehörige Datei wird in `tb.files` als `source='SERVER'` und über `tb.file_links` mit Purpose `BTB_FINAL_PDF` mit dem BTB verbunden.

Download:

`GET /api/v1/btbs/{btb_id}/final-pdf`

Der normale BTB-Detailendpunkt liefert zusätzlich Metadaten des aktuell gültigen Abschluss-PDF, soweit eines vorhanden ist.

## Atomarer Dateitausch

`server/tbserver/btb_documents.py` verwendet einen temporären PDF-Pfad und einen Rollback-Pfad.

Bei erneutem Abschluss:

1. neues PDF temporär vollständig schreiben;
2. vorhandenes PDF kurzzeitig als Rollback-Datei verschieben;
3. neues PDF atomar auf den stabilen Zielpfad verschieben;
4. DB-Metadaten/BTB-Status committen;
5. erst danach Rollback-Datei löschen.

Scheitert die DB-Transaktion nach dem Dateitausch, wird das vorherige PDF wiederhergestellt. Beim ersten Abschluss ohne Vorgänger wird die neu installierte Datei wieder entfernt.

Damit darf ein Dateifehler nicht zu einem scheinbar erfolgreich abgeschlossenen BTB führen.

## Original-Diff im PDF

Renderer: `server/tbserver/btb_pdf.py`

Renderer-Version: `tb-server-btb-pdf-2`

Beim ersten Abschluss existiert noch kein Vergleichsoriginal; der gesamte Inhalt wird normal/schwarz dargestellt.

Bei späteren Abschlüssen:

- unveränderte Fach-Einträge bleiben schwarz;
- neue/geänderte Fach-Einträge werden rot dargestellt;
- entfernte Originaleinträge erscheinen rot mit `(entfernt)`;
- neue/geänderte Fotos werden rot markiert;
- entfernte Originalfotos erscheinen rot mit `(entfernt) Foto`;
- Datum/Arbeitszeit und die übrigen Kopfdaten bleiben schwarz.

Die erste Foto-Basis gehört zum Original-Snapshot. Das Original-JPG wird niemals verändert.

Der aktuelle Serverrenderer markiert einen fachlich geänderten granularen BTB-Eintrag als ganze Einheit rot. Feinere Inline-Differenzierung innerhalb desselben Payloads kann später additiv verbessert werden, ohne das Originalmodell zu ändern.

## BTB-Fotos

Nur Dateien mit einer expliziten `file_links(entity_type='BTB', entity_id=<btbId>)`-Zuordnung und Bild-MIME-Typ gehören in den modernen BTB-Fotobestand.

Beim Abschluss wird genau diese Menge in den Snapshot übernommen.

Neue BTB-Bilder über den bestehenden Uploadpfad durchlaufen denselben Nachbearbeitungsvertrag. Allgemeine Baustellenbilder ohne diese `btbId`-Zuordnung werden nicht automatisch in den BTB gezogen.

Ein Bild kann bewusst aus dem BTB gelöst werden über:

`DELETE /api/v1/btbs/{btb_id}/photos/{file_id}`

Dabei wird nur die BTB-Verknüpfung entfernt. Das echte Baustellenfoto bleibt als Datei erhalten.

## BTB vollständig löschen

Der bestehende BTB-Tombstone bleibt erhalten. Beim Löschen werden zusätzlich:

- das aktuell gültige BTB-PDF invalidiert und physisch entfernt;
- der Original-Snapshot gelöscht;
- Bild-Verknüpfungen dieser `btbId` gelöst.

Die eigentlichen Baustellenfotos werden nicht gelöscht.

## Rechte / Auth / Idempotenz

Es gelten unverändert die Server-1.0-Regeln:

- Lesen/PDF-Download: `btb.view`;
- Bearbeitung/Reaktivierung: `btb.edit`;
- Abschluss: `btb.finalize`;
- Löschen: `btb.delete`;
- Teamzugriff wird zusätzlich geprüft;
- bestehender Portal-/Device-Bearer-Vertrag bleibt erhalten;
- Schreiboperationen nutzen weiter `X-Client-Change-Id` / Mutation Receipts.

## Capability

`btb_finalize_with_pdf_v1=true`

Damit dürfen spätere Clients den Abschluss-PDF-Vertrag explizit erkennen, anstatt aus einer Serverversionsnummer zu raten.

## Tests

`server/checks/34_verify_tb_server_1_btb_pdf_source.py` prüft:

- Python-Syntax der beteiligten Servermodule;
- Ein-PDF-/Original-/Reaktivierungs-/Rework-Marker;
- stabilen PDF-Pfad ohne sichtbare V1/V2/V3;
- Fotoänderung über denselben Rework-Vertrag;
- Capability-/Workflow-Vertrag;
- einen echten ReportLab-PDF-Smoke-Test.

Zusätzlich wurde der Renderer lokal mit einem geänderten und einem entfernten Originaleintrag erzeugt und als PNG gerendert/visuell geprüft. Der Test erzeugte ein gültiges PDF mit schwarzem Kopf und roten Fachänderungen.

Noch offen bleibt die vollständige PostgreSQL-/FastAPI-End-to-End-Prüfung auf dem CI-/Testserver. Das bekannte GitHub-Runner-Problem kann weiterhin dazu führen, dass ein Workflow vor dem ersten Schritt scheitert.

## Bewusste Abgrenzung

Dieser Baustein schließt den **fachlichen Serververtrag** für Abschluss-PDF, Original und Nachbearbeitung.

Das zentrale Firmenprofil/Firmenlogo und das firmenweite PDF-Logo-Layout sind inzwischen über `docs/TB-SERVER_1_0_COMPANY_BRANDING.md` angebunden; Renderer v2 verwendet diesen zentralen Stand.

Noch nicht damit erledigt:

- vollständige Pixel-/Layout-Parität zum Android-PDF;
- Server-1.0-HTML-Adminoberfläche und Portal-Vorschau für PDF-Layout;
- Tagesabschluss-ZIP;
- produktiver NAS-Cutover.

`company_profile_v1`, `company_logo_v1` und `company_pdf_logo_layout_v1` sind im zugehörigen Branding-Baustein aktiv.
