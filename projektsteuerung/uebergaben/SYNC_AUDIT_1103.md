# SYNC-AUDIT-1103 – Server ↔ Android, 03.11.2026

Status: QUELLPRÜFUNG ABGESCHLOSSEN / BEFUNDE OFFEN / NAS- UND GERÄTE-E2E OFFEN.

## Auftrag und geprüfter Stand
Benutzer: Senden/Empfangen Server↔Handy auf Probleme, Denkfehler und Beschleunigung prüfen.
Server-Fachbasis d3697c52507cf49a223a773bebf7fef1de1c0809, Draft-PR6.
Android-Fachbasis e53aa0e8f6d4cc525b805ef73936aa72cd3bf6c0, PR11 (kein Draft).
Auftrag vor Prüfung mit 62f2b205f577f784a8fbbeffc4494e11d845b5a0 auf Serverbranch gesichert.
Keine Android-/Server-Runtimeänderung, kein Deployment, keine produktiven Schreibtests.
Die installierten Pixel-/Poco-Versionen und der aktuelle NAS-Dateistand wurden nicht ausgelesen; Befunde gelten für diese GitHub-Stände.

## Was bereits richtig angelegt ist
- Lokale BTB-Änderungen werden vor dem Start des Sendeworkers dauerhaft als Dirty-Scope markiert. Generationsvergleich verhindert im normalen Uploadpfad, dass ein alter Upload eine jüngere Änderung quittiert (SelectiveSyncStore).
- Selektiver BTB-Sendepfad exportiert lokalen Inhalt vor einem Server-Apply; Idempotenz und Basisversionen sind vorhanden. Konflikte werden nicht blind überschrieben.
- Server-Longpoll/SSE teilen pro API-Prozess einen Change-Hub statt pro Handy die DB regelmäßig abzufragen. Rechte werden nach Warten und vor Ausgabe erneut geprüft.
- Empfangscursor wird grundsätzlich erst nach erfolgreicher lokaler Anwendung einer Seite gespeichert.
- BTB-Schichtende/Finalisierung besitzt eine dauerhafte separate Intent-Outbox und explizite Serverbefehle. Kleines Foto plus Metadaten vor Abschluss ist eine bestehende Schutzregel; Kameraoriginale blockieren diesen Pfad nicht.
- 25 Sekunden Longpoll sind eine maximale Leerwartezeit, keine feste Zustellverzögerung. Der gemeinsame Hub prüft den Head im Quellstand alle 1s.

## Befunde nach Priorität

### P1 – Cursor kann spät bestätigte kleinere Änderungsnummer überspringen
Nachweis: server/v1_migrations/001_server_1_0_base.sql:408ff (bigserial); tbserver/events.py emit_change; tbserver/database.py transaction; tbserver/sync.py _latest_change_id, _ChangeHub, wait_changes.
A erhält ID10 und bleibt uncommitted; unabhängiges B erhält ID11 und committed zuerst. Head11/Feedabfrage liefert11, Client übernimmt Cursor11. Wenn A danach committed, wird10 durch change_id>after nicht mehr gelesen; auch der Hub meldet keinen höheren Head.
Idempotenz sperrt pro Benutzer+client_change_id, nicht die globale Commitreihenfolge.
Mit unveränderter Produktionsfunktion wait_changes und einem ausdrücklich simulierten MVCC-Sichtbarkeitsverlauf reproduziert. PostgreSQL selbst wurde hier NICHT gestartet; echte Zwei-Transaktionen-Regression noch erforderlich. Eine spätere Objektvollabfrage kann den Zustand zufällig heilen, ersetzt keine Feed-Zustellgarantie.
Vorschlag: commit-sichere Feedveröffentlichung/High-Watermark entwerfen und mit zwei unabhängigen Transaktionen, Rollback, Pagegrenzen und Fremdfirmenereignissen prüfen. Kein pauschales Minus-N-Cursor oder Zeitfenster als belastbare Lösung; Serialisierung möglicher Lösungsweg mit Lock-/Durchsatz-/Deadlockprüfung.

### P1 – BTB-DELETE kann schnellen Empfang dauerhaft blockieren
Server btb.py delete_btb emittiert BTB/DELETE; _load_btb liefert für DELETED HTTP404 BTB_NOT_FOUND.
Android Server1DeltaSync.applyPage ignoriert operation für BTB und lädt dieselbe ID nach. Server1BtbApi.loadBtb bildet404 auf DetailResult.Unsupported ab; dies gelangt als Unsupported zurück. Die Seite wird deshalb nicht bestätigt, Poller wartet60s und versucht denselben Cursor erneut. Spätere Änderungen bleiben hinter der blockierenden Seite liegen. Auch noch ältere UPDATE-Events zu einem inzwischen gelöschten BTB sind betroffen.
Server-404 mit unveränderter _load_btb-Funktion ausgeführt; Androidpfad am Quellcode verfolgt, kein Kotlin-/Geräte-Lauf.
Vorschlag: expliziter Tombstone-/NotFound-Vertrag, gezielte lokale Löschung bzw. Konflikt bei ungesendeten Inhalten; nicht beliebige404 als gelöscht behandeln. Tests: Portal-Löschung→zwei Geräte, altes Update vor Delete, Pagegrenze, Neustart, Offlineänderungen.

### P1 – Finalisierter Serverstand kann unbestätigte lokale Änderungen verwerfen
CollabLocalBridge.applyServerStateToLocal: Bei serverFinal zuerst clearServer1BtbPending und clearBtb; die folgenden Dirty-Prüfungen sind für serverFinal ausgenommen. Danach wird die lokale BTB-Datei aus dem Serversnapshot geschrieben.
Risikoszenario: Gerät offline mit lokalem Text, während Portal/Frist/anderes berechtigtes Gerät finalisiert. Die auf dem Server vorhandene Finalisierung ist keine Bestätigung dieses Offline-Texts. Quelle zeigt einen möglichen stillen Verlust; hier kein Android- oder NAS-Reproduktionslauf.
Vorschlag: lokale offene Fassung vor kanonischem Abschluss als Konflikt/Entwurf dauerhaft erhalten; Server-FINALIZED und Rechte nicht automatisch wieder öffnen. Gemeinsam mit bestehenden Lifecycle-/Dirty-Schutzregeln testen.

### P2 – Schneller Empfang deckt nicht alle Fachbereiche ab
Server1DeltaApi.DEFAULT_ENTITY_TYPES enthält BTB, BTB_ENTRY, BTB_PARTICIPANT, FILE, WORKSITE_NODE, WORKSITE, WORKSITE_MEMBERSHIP, WORKSITE_INVITATION.
DEFECT, ACCEPTANCE und TRACK_DATA fehlen; Server1DeltaSync.applyPage implementiert dafür ebenfalls keinen Import. Der globale Cursor läuft über ausgefilterte Änderungen weiter.
Diese Bereiche werden im größeren Server1AndroidSyncCoordinator bzw. noch vorhandenen generischen Syncpfaden behandelt. Portal→Handy-Aktualität folgt daraus aber nicht automatisch beim laufenden BTB-Deltaempfang. Später nur zusätzliche Typen einzutragen würde bereits übersprungene Änderungen nicht nachholen.
Vorschlag: vollständige Fachbereichsmatrix, getrennte Cursor/Bootstrap oder ausdrücklich abgesicherter neuer Startpunkt, danach bereichsbezogene Sender/Empfänger. Tombstones, Rechte und finale Status erhalten.

### P2 – Hintergrundempfang hat keinen durchgängigen Weckweg
TBHelferApp stoppt Server1DeltaPoller bei letzter gestoppter Activity. Server1DeltaWorker ist OneTimeWork, getriggert bei Start/Resume/Netzverfügbarkeit; nach caughtUp endet er. Kein persistenter periodischer Delta-Fallback in diesen Pfaden, kein integrierter Firebase-/Pushdienst in Manifest/Gradle/den geprüften App-/Syncpfaden.
Damit besteht für ein länger ruhendes Gerät bei unverändertem Netz kein durchgängiger zeitnaher Empfang. Das nächste Öffnen startet Catch-up; das ist keine unmittelbare Hintergrundzustellung. Kein Lauf auf schlafendem Pixel/Poco.
Vorschlag: bestehendes 15×15-Ziel mit inhaltsfreiem Push-Wecksignal und dauerhaftem Feed vervollständigen; kleiner periodischer Delta-Fallback plus Start/Resume/Netzrückkehr. Android-Energiesparregeln bleiben Grenzen, keine minutengenaue Zusage.

### P2 – Sendeaufträge werden angehängt, nicht wirklich zusammengefasst
SofortSync.btbAnstossen legt bei jedem Trigger einen neuen SelectiveSyncWorker an. enqueue nutzt APPEND_OR_REPLACE mit2s InitialDelay und exponentiellem Retry ab30s. Gemeinsame Dirty-Scope-Sammlung ist vorhanden, trotzdem entsteht eine WorkManager-Kette. Ein wartender Retry-Vorgänger kann neu angehängte Arbeit bis zum nächsten Versuch zurückhalten; Resume/Netzrückkehr appendieren ebenfalls.
Das ist ein Quell-/WorkManager-Vertragsbefund, keine hier gemessene Android-Warteschlangenlaufzeit. Einfach KEEP zurückzusetzen könnte erneut das bekannte verlorene Folgelaufrennen öffnen; laufende POSTs dürfen nicht blind abgebrochen werden.
Vorschlag: ein aktiver Sendelauf plus garantierter nachfolgender Lauf bei neuer Generation; Resume/Netzrückkehr muss Backoff sinnvoll aufwecken können. Fotos nicht über alle Baustellen mit Lifecycle-Aktionen serialisieren.

### P3 – Unnötige Abfragen und lokales Scannen
- Server1DeltaPoller erzeugt pro Schleifendurchlauf einen neuen Server1DeltaSync; dieser erzeugt je Lauf ServerCapabilitiesApi().load(). Dessen 5min-Cache gehört der jeweiligen Instanz und wird im nächsten Durchlauf nicht wiederverwendet. Auch SelectiveSyncWorker und Lifecycle erzeugen neue Clients. Vorschlag: Cache nach Serveridentität/Recovery-Generation teilen, TTL und explizite Invalidierung erhalten.
- Jeder direkte BTB-Refresh in loadBtbAccess fragt erst die ganze Team-BTB-Liste und dann das Detail ab. Mehrere betroffene BTBs desselben Teams wiederholen die Liste. Pro Seite/Team einmal laden oder aktiven Status additiv mit Details liefern.
- BtbTeamMediaSync liest selbst bei btbServerIds-Filter zunächst Baustellenfotos und gesamten lokalen BTB-Verlauf; der Server-BTB-Filter schränkt diese ersten lokalen Scans nicht ein. Umfang messbar machen und BTB-bezogen indizieren.
- Delta-Cursor bleibt bis nach Medienimport/Download am Batch hängen. Ein Medienfehler lässt bereits angewendete Text-/Statusdaten erneut verarbeiten. Vorschlag: separater dauerhafter Medienauftrag vor Cursorbestätigung; fehlende Bilder sichtbar halten. Kein Cursorfortschritt ohne gesicherte Nacharbeit.
- Generischer SofortSync.anstossen startet weiterhin SyncWorker und Schema13Worker; SyncWorker lädt Katalog und größeren Fachbestand. Die noch nicht selektiv angebundenen Bereiche sind daher Optimierungskandidaten, keine pauschale Abschaltung des Recoverypfads.

## Tatsächlich ausgeführte Prüfungen
- python server/checks/135_verify_sync_reauthentication.py:7 Tests, Exit0. Produktionsfunktionen mit Auth-/DB-Doubles; kein HTTP/PostgreSQL.
- python server/checks/139_verify_255_sync_clients.py:8 Tests, Exit0.255 lokale Coroutineclients; im ruhigen Test0,312s, Spitze13 begrenzte Abfragen,14 Hubpolls. Einschließlich lokaler HTTP-Test des Lasttest-Werkzeugs gegen künstlichen Handler. Keine NAS-Kapazitäts-/Internetzustellmessung.
- python server/checks/sync_audit_1103.py:4 Auditszenarien, Exit0. Normales Ereignis; übersprungenes spätes kleineres Ereignis ausdrücklich REPRODUZIERT; ausgefilterte fremde Änderung ohne Datenleck; DELETE-Detail404. Das sind4 Audit-Nachweise, keine Aussage „Sync fehlerfrei“.
- In Summe15 bestehende Prüfungen bestanden und4 Auditszenarien ausgeführt. Androidbefunde durch Quellvergleich, nicht auf Kotlin/Room ausgeführt.

## Werkzeug-, CI- und Praxisgrenzen
- Kein lokaler Docker/PostgreSQL/ADB/Kotlin-Compiler/Postman-CLI; FastAPI/Psycopg lokal nicht installiert. Keine authentifizierte NAS-Testsession vorhanden. Deshalb kein echter DB-/HTTP-/Pixel-/Poco-Roundtrip, kein RTT-/Durchsatzwert.
- Postman-Verbindung geprüft: Workspace6a1ca1c5-c89b-4a51-8d53-378486bf9be3 enthält nur „My Collection“ mit GET/POST gegen postman-echo.com. Keine passende TB-Helfer-Testsammlung gefunden, keine Echo-Anfrage als TB-Helfer-Test ausgeführt, keine Sammlung verändert.
- Context7: offizielle PostgreSQL-Dokumentation zu nextval und READ COMMITTED sowie Android-WorkManager APPEND_OR_REPLACE geprüft. PostgreSQL-IDs sind atomar eindeutig, aber keine Commitreihenfolge; WorkManager appendiert hinter unerledigter Arbeit.
- Aktuell abgefragte Android-CI41586577181 gehört f507cab7, Buildjob125753367243 SUCCESS, Vertragsjob125753367488 FAILURE, Gesamtlauf FAILURE; kein PASS für heutigen HEAD daraus abgeleitet.
- Letzter abgefragter Serverbranchlauf38447605345 FAILURE gehört d400808, nicht heutigem Server-HEAD. Keine neue CI gestartet; Originalfehler dieser alten Läufe hier nicht diagnostiziert. Keine Actions-Minuten verbraucht, aktuelles Budget/Minutenkonto nicht ausgelesen.

## Empfohlene Reihenfolge
1. Feed-Cursor mit echten konkurrierenden PostgreSQL-Transaktionen absichern; BTB-Tombstonepfad und Offline-Inhalte bei Finalisierung schützen.
2. Androidgezielter Folgeauftrag: die drei P1-Fälle zuerst reproduzieren/korrigieren, dann fachbereichsbezogene Deltaabdeckung und Queue-Wakeup. API-Vertrag vorher auf beiden Branches sichern.
3. Cache und Mehrfachabfragen reduzieren; Text/Status vom Mediennachlauf entkoppeln, ohne Datenbestätigung vorzutäuschen.
4. Danach Messung mit Testbaustelle und zwei Geräten: Text speichern, Foto, Mangel, Abnahme, Unterbaustelle, Helfer-Schichtende, Hauptabschluss, Delete, Offline/Netrückkehr/Neustart/Parallelbearbeitung. Zeitpunkte lokal gespeichert→Servercommit→zweites Gerät angewendet getrennt messen. Kein Vollsync nach jedem Tastendruck.

Keine pauschale Neuentwicklung erforderlich. Gemeinsamer dauerhafter Feed und selektive Outbox sind die passende Grundlage, die genannten Lücken brauchen gezielte Änderungen.
