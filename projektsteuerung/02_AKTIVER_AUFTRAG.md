# TB-HELFER 2.0 – AKTIVER AUFTRAG

Stand: 2026-09-26

## PS-001 – Projektsteuerung zentralisieren
Status: **UMGESETZT IM QUELLSTAND DIESER STEUERUNGSÄNDERUNG**

Ein gemeinsamer Einstieg unter `projektsteuerung/`; Android/Server-Codebereiche bleiben getrennt; alte Top-Level-Steuerungsordner entfallen; Chat-Sicherung erfolgt ereignisbasiert. Kein `app/**`- oder `server/**`-Fachcode wird durch PS-001 geändert.

## IMG-001 – Bildtransport / Serverlast
Status: **QUELLAUDIT ABGESCHLOSSEN / ABWEICHUNGEN BESTÄTIGT / IMPLEMENTIERUNG OFFEN**

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

### Audit-Ergebnis 26.09.2026
Bestätigt:
- Android lädt derzeit das lokale Foto-Original hoch, nicht eine separate kleine Sync-Version.
- Der Server speichert die empfangene APP-Datei unverändert als Quelldatei.
- Der Server stellt bereits `thumb` (320 px) und `preview` (1280 px) als JPEG-Renditions bereit.
- Android ignoriert die Rendition-URLs und lädt über `/content`.
- Android lädt fehlende sichtbare Attachments im Server-1.0-Sync aktuell sofort herunter statt Bilddaten auf kleine/lazy Renditions zu begrenzen.
- Das lokal ohnehin erzeugte 512-px-Thumbnail wird nicht als Online-Sync-Artefakt genutzt.
- BTB: strukturierte Metadaten vorhanden, aber GPS-Genauigkeit nicht persistent; Mangel/Abnahme: strukturierte Foto-Metadaten im Uploadpfad unvollständig.

Nächste getrennte Umsetzungsaufträge:
1. **ANDROID:** kleine Sync-Datei vor Upload erzeugen; Original unverändert lassen; Bilddownload auf `preview/thumb` umstellen; Metadatenlücken schließen.
2. **SERVER:** keine Ersatzkompression für Android-Originale einbauen. Bestehende Renditions/Migration 015 praktisch verifizieren; Servercode nur ändern, falls Android-Vertrag ohne Serveränderung nicht erfüllbar ist.
3. Danach E2E mit Original-/Sync-/Rendition-Dateigrößen auf Pixel/Poco/Portal.

## SYNC-DEVICE-001 – Poco erhält keine Daten
Status: **OFFEN**

Serverseitig HELPER im richtigen Team bestätigt; Gerät erhält trotzdem keine Daten. Keine Neu-Anlage von Benutzer/Einladung auf Verdacht. Binding/Sync/API/Logs gezielt eingrenzen.
