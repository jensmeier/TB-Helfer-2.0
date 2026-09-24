# TB-Helfer 2.0 – ANDROID Übergabe Auto-/Delta-Sync 15×15

Stand: 24.09.2026  
Basis: `docs/TB-SYNC_15x15_ZIELMODELL.md`

## Verbindliche neue Benutzeranforderung

Die Android-Auswahl:
- Manuell
- Stündlich
- Täglich

soll entfernt werden.

Normaler Fachsync läuft automatisch als kleiner Delta-Sync. Niemand soll im Mehrgerätebetrieb regelmäßig „Synchronisieren“ drücken müssen.

`Neu laden` bleibt ausschließlich Recovery/Vollabgleich.

## Android Phase 1

1. `SyncEinstellungenSpeicher.Zeitplan` und die Zeitplan-Auswahl aus `EinstellungenFragment` entfernen.
2. Bestehenden periodischen **Vollsync** nicht als Auto-Ersatz einfach alle 15 Minuten laufen lassen.
3. Persistenten `SelectiveSyncWorker` + Dirty-Scope gemäß bestehender Übergabe `TB-ANDROID_SELEKTIVER_SYNC_2026-09-23.md` bauen.
4. Erstes Objekt: BTB.
5. `BautagebuchSpeicher` markiert genau den geänderten `btbId`.
6. Mehrere schnelle Änderungen desselben BTB coalescen/debouncen.
7. Nur den passenden Pending-BTB senden.
8. `Neu laden` ruft weiterhin den geschützten vollständigen `SyncWorker` auf.

## Android Phase 2

Nach Server-Push-/Change-Scope:
- Push-Wake registrieren;
- Push enthält keine Fachinhalte;
- Change-Cursor persistent speichern;
- Push/App-Resume/Netz-Rückkehr → Delta-Worker;
- `/api/v1/sync/changes` verwenden, nicht den Katalog-Changefeed als Ersatz;
- BTB-Event → nur `GET /api/v1/btbs/{id}`;
- Cursor erst nach erfolgreicher lokaler Anwendung fortschreiben;
- verpasster Push wird über Resume/periodischen Delta-Fallback aufgeholt.

## Lastziel

15 TB × 15 Helfer + 15 Portal = 255 aktive Clients als Planungsfall.

Mobile Geräte dürfen deshalb keinen dauerhaften 2-Sekunden-SSE-Poll verwenden.

## Medien

Später:
- Sync-Foto optimiert;
- Thumbnail;
- Lazy-Download;
- Originalarchiv getrennt.

## Zuständigkeit

`app/**` wird im ANDROID-Projekt umgesetzt. SERVER liefert Change-Feed, Push-Zielgruppen, DB-Pooling und Live-Notifier.
