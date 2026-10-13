# TB-Helfer 2.0 – Selektiver Sofort-Sync statt Vollsync

Stand: 2026-09-23  
SERVER-Branch: `feature/tb-server-1.0`  
ANDROID geprüft: `feature/tb-android-2.0` @ `ac972afecf96504b2f13dd697895100cf4a27fee`  
Android-Version: **1.3.16 / 200066**

## Benutzeranforderung

Wenn z. B. im BTB Text eingegeben/geändert wird, darf der automatische Sofort-Sync **nicht den gesamten Serverbestand** abgleichen. Es soll nur der tatsächlich geänderte Fachbereich synchronisiert werden.

Beispiele:
- BTB-Text → nur dieser BTB;
- BTB-Foto → dieser BTB + BTB-Medien;
- Mangel → nur dieser Mangel (+ Bilder);
- Abnahme → nur diese Abnahme (+ Anlagen);
- Baustellenbaum → nur Baum;
- Streckendaten → nur betroffene Datensätze.

Ein Vollsync bleibt für bewusstes `Neu laden`, Aktivierung/Beitritt und Recovery erhalten.

## Nachgewiesene aktuelle Ursache

Aktuell ruft `BautagebuchSpeicher.speichern(...)` nach jedem erfolgreichen BTB-Schreiben:

```kotlin
SofortSync.anstossen(context.applicationContext)
```

`SofortSync` startet immer den normalen `SyncWorker`.

Der Server-1.0-Pfad des `SyncWorker` lädt zuerst den Katalog und ruft dann:

```kotlin
Server1AndroidSyncCoordinator(applicationContext).synchronize(token)
```

Dieser Coordinator verarbeitet in einem Lauf u. a.:
- Baustellenkontexte,
- Baustellenbaum,
- Streckendaten,
- BTBs,
- Mängel/Abnahmen,
- Dateilisten/Anhänge,
- BTB-Medien/PDF.

Damit ist der heutige Sofortpfad fachlich breiter als nötig.

## Bereits vorhandene Servergrundlage

Keine neue Server-Fachdatenarchitektur nötig:

- granularer BTB-Write-Vertrag vorhanden;
- `GET /api/v1/btbs/{btb_id}` vorhanden;
- `GET /api/v1/sync/changes?after=<cursor>` vorhanden;
- BTB-Entry-Änderungen erzeugen zusätzlich ein `BTB`-Change-Event des Eltern-BTB;
- Change-Feed liefert IDs/Versionen, keine großen Payloads;
- neue Capability: `sync_change_feed_v1=true`.

Das entspricht bereits `docs/TB-SERVER_1_0_ZIELMODELL.md`, Abschnitt 9:
lokal speichern → Outbox → gezielt senden → Change-Event → anderer Client lädt nur die Änderung.

## Sichere Android-Umsetzung – Phase A: BTB-Sofort-Upload

Bestehenden `SyncWorker` **nicht ersetzen**. Er bleibt bestätigter Vollsync/Fallback.

### A1. Separater selektiver Worker

Neu z. B.:

```
app/src/main/java/de/tbhelfer/server/SelectiveSyncWorker.kt
app/src/main/java/de/tbhelfer/server/SelectiveSyncStore.kt
```

Der Store muss Dirty-Scopes **persistent** halten. Nicht nur WorkManager-Input verwenden, weil `ExistingWorkPolicy.KEEP` sonst eine zweite Änderung verlieren kann, während bereits ein Worker wartet/läuft.

Minimaler Scope für ersten Schritt:

```kotlin
BTB(localBaustelleId: Long, clientBtbId: String)
```

Später erweiterbar um `BTB_MEDIA`, `DEFECT`, `ACCEPTANCE`, `WORKSITE_TREE`, `TRACK_DATA`.

### A2. SofortSync wird scoped

Statt:

```kotlin
SofortSync.anstossen(context)
```

für modernen BTB:

```kotlin
SofortSync.btbAnstossen(
    context = context.applicationContext,
    localBaustelleId = normalized.baustelleId,
    clientBtbId = normalized.btbId!!
)
```

Legacy-BTB ohne stabile `btbId` darf vorerst auf den bestehenden Vollsync zurückfallen.

### A3. Nur genau einen BTB in die Outbox exportieren

`CollabLocalBridge.exportLocalChanges()` scannt heute alle gebundenen Baustellen und BTB/Mangel/Abnahme.

Neu ergänzen, z. B.:

```kotlin
suspend fun exportBtbChange(
    localBaustelleId: Long,
    clientBtbId: String
): ExportSummary
```

Diese Methode:
1. löst nur die eine lokale Baustellenbindung auf;
2. lädt nur die eine `btbId`;
3. verwendet dieselbe bestehende `queueIfChanged(... TYPE_BTB ...)`-Logik;
4. scannt **keine** Mängel, Abnahmen oder anderen BTBs.

Keine zweite BTB-Payloadlogik bauen: vorhandene Sanitizing-/Hash-/Queue-Funktionen wiederverwenden.

### A4. Pending-Upload filterbar machen

Bestehende Methode:

```kotlin
synchronizeServer1BtbPending(token, btbApi)
```

filterbar erweitern, z. B.:

```kotlin
synchronizeServer1BtbPending(
    token,
    btbApi,
    worksiteServerId: String? = null,
    clientBtbId: String? = null
)
```

Bei selektivem BTB-Sync darf nur genau diese Pending-Änderung gesendet werden.

Die bestehende `Applied`-Logik speichert Server-Snapshot + Base-State bereits zurück; diese Logik wiederverwenden.

### A5. SelectiveSyncWorker BTB-Ablauf

Für jeden persistent gemerkten BTB-Scope:

1. Geräte-Token laden;
2. Capability `btb_live_sync_v1` prüfen;
3. lokale Binding-/Team-Zuordnung dieses BTB auflösen;
4. `exportBtbChange(...)`;
5. nur passenden `synchronizeServer1BtbPending(...)` ausführen;
6. bei Erfolg Scope entfernen und Syncstatus aktualisieren;
7. bei temporärem Netzfehler Scope **nicht verlieren**, Worker retry;
8. bei SessionInvalid Token wie bestehender Worker behandeln;
9. bei Konflikt keinen Vollsync still erzwingen: Scope/Fehler erhalten und gezielte Konflikt-/Recoverylogik verwenden.

**Nicht ausführen:** Katalog, Baum, TrackData, Defects, Acceptances, globale Fileliste.

### A6. Debounce bleibt erhalten

Mehrere Autosaves desselben BTB in kurzer Folge sollen zu einem Worker zusammenfallen.

Wichtig:
`ExistingWorkPolicy.KEEP` darf bleiben, **wenn** der Dirty-Scope vorher persistent gemerkt/merged wurde.

## Phase B: inkrementeller Pull auf anderen Geräten

Nach Phase A nutzt der periodische/Foreground-Abgleich die vorhandene Capability `sync_change_feed_v1`.

Neu z. B.:
- `Server1ChangeFeedApi.kt`
- persistenter `last_seen_change_id` pro Serveridentität/Gerät.

Ablauf:
1. `GET /api/v1/sync/changes?after=<cursor>`;
2. Changes nach `worksite_id/entity_type/entity_id` deduplizieren;
3. für `BTB` nur `GET /api/v1/btbs/{btb_id}`;
4. `BTB_ENTRY` braucht keinen zweiten Download, weil der Server bei jeder Entry-Änderung zusätzlich ein Eltern-`BTB`-Event erzeugt;
5. erst nach erfolgreicher lokaler Anwendung Cursor fortschreiben;
6. bei Cursorverlust/Serverwechsel/Reaktivierung → bewusster Vollabgleich.

Damit lädt das zweite Handy nach einer BTB-Änderung ebenfalls nur den geänderten BTB.

## Vollsync bleibt geschützt

`SyncWorker` und `Server1AndroidSyncCoordinator.synchronize(...)` bleiben zunächst unverändert für:
- manuelles `Neu laden`;
- Erstaktivierung/Reaktivierung;
- Baustellenbeitritt;
- Recovery;
- gezielte Regressionstests.

Erst wenn selektive Pfade praktisch bestätigt sind, darf der periodische Standardlauf auf Change-Feed/Delta umgestellt werden.

## Tests Phase A

1. BTB öffnen, nur Text in einem Standardabschnitt ändern.
2. Autosave abwarten.
3. API-Log muss nur BTB-bezogene Endpunkte zeigen; **keine** Katalog-/Baum-/TrackData-/Defect-/Acceptance-Vollabfragen.
4. Zwei schnelle Textänderungen dürfen nicht verloren gehen.
5. Netz aus → ändern → App schließen → Netz an → Scope muss nach Prozessneustart noch übertragen werden.
6. Zweiter BTB darf durch Änderung des ersten BTB nicht hochgeladen werden.
7. Mangel-/Abnahme-Queues dürfen durch reinen BTB-Sofortlauf nicht versendet werden.
8. Manuelles `Neu laden` muss weiterhin den vollständigen bestätigten Sync ausführen.

## Tests Phase B

1. Pixel ändert BTB-Text.
2. Poco fragt Change-Feed ab.
3. Poco lädt nur die geänderte `btbId`.
4. Kein kompletter Baustellen-/Katalog-/Datei-Snapshot.
5. Cursor wird erst nach erfolgreicher Anwendung fortgeschrieben.
6. Offline/Retry und Prozessneustart dürfen keine Änderung verlieren.

## Serverstatus

Serverseitig ist die Grundlage vorhanden. Neu explizit advertised:
`sync_change_feed_v1=true`.

**Keine DB-Migration erforderlich.**

## Zuständigkeit

Die funktionale Änderung liegt überwiegend in `app/**` und wird deshalb im Projekt **TB-Helfer 2.0 – ANDROID** umgesetzt. SERVER bleibt Integrator des API-/Change-Feed-Vertrags.
