# TB-HELFER 2.0 – ANDROID/SERVER VERTRAG

Stand: 2026-09-26

Detailquellen:
- `projektsteuerung/vertraege/TB-SERVER_ANDROID_SYNC_VERTRAG_2026-09-25.md`
- `projektsteuerung/vertraege/TB-SYNC_15x15_ZIELMODELL.md`
- `docs/TB-HELFER_PROJEKTREGELN.md`

## Zuständigkeit
ANDROID primär `app/**`, SERVER primär `server/**`. Neue Serveranforderung zuerst dokumentieren, dann im SERVER-Projekt umsetzen; keine Ersatzlösung auf der falschen Seite.

## Sync
Objektbezogener Delta-/Dirty-Scope; `Neu laden` nur Recovery/Vollabgleich; Cursor persistent; FINALIZED historische BTBs im normalen Sync read-only.

## Rollen
Helfer: `shift-finish`/`shift-resume`, kein finaler BTB-Abschluss. Haupt-TB finalisiert. Abschluss-PDF nach Serverberechtigung/Rolle.

## Bilder – verbindlich
### Handyaufnahme
- Volloriginal bleibt auf dem Aufnahme-Handy.
- Online-Sync überträgt Foto-ID, Metadaten, kleine Sync-Version (Richtwert längste Kante ca. 1920 px, JPEG ca. 80–82 %, nie hochskalieren) und Thumbnail (Richtwert ca. 512 px).
- Andere Handys laden kleine Version/Thumbnail; Volloriginal wird nicht automatisch vervielfältigt.

### Portalaufnahme
- Empfangene Portaldatei darf serverseitige Quelldatei bleiben.
- Server erzeugt Renditions/Preview/Thumbnail.
- Mobile Geräte erhalten bevorzugt kleine Renditions.

### Originalarchiv
Handy-Volloriginale sind kein normaler Delta-Sync und blockieren den BTB-Abschluss nicht.

## IMG-001
Jetzt prüfen, ob Android tatsächlich eine kleine Sync-Version hochlädt, Server keine Volloriginale unnötig an andere Geräte verteilt, Portal→Handy Renditions nutzt und Metadaten vollständig bleiben. Erst Quellprüfung + realer E2E = BESTÄTIGT.
