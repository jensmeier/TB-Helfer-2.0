# AND-NODE-DELTA-023 – Android-Unterbaustellen im schnellen Abgleich

Stand: 23.10.2026. Android-Branch `feature/tb-android-2.0`. Implementierungsbasis `28756f5faeb72b9f6fab024b37ed078f90b35ab5`; nachfolgende Absicherung der Strukturstatusanzeige und bestehender BTB-Konflikte gehört zum selben Auftrag.

## Verhalten

- Schneller Feed enthält `WORKSITE_NODE`. Struktur wird nur für betroffene gebundene Baustellen gelesen, vor BTB-/Bild-Ortsauflösung. Bestehende `clientNodeKey` und lokale IDs werden übernommen; Anzeige ersetzt keine Identität.
- Unterbaustellen mit Bahnhof zeigen Bahnhof/Name bzw. Kürzel und Gleis/Weiche; optionaler Kurzname ergänzt die Anzeige. Beispiel: `Bahnhof MM · GL 4 · Kurzname` unterscheidet sich von `Bahnhof Berchtesgaden · GL 4 · Kurzname`. Fotoanzeige nutzt denselben Fachformatter.
- BTB-Speichern bleibt zuerst lokal. Der selektive Worker sendet genau diesen BTB und seine Bilder. Nur bei geänderter Struktur kommt der betroffene Baustellenbaum hinzu. Fehlende Server-BTB-ID führt zu Wiederholung, niemals zur Freigabe aller Baustellenbilder.
- `Lokal gespeichert`, `Übertragung ausstehend`, `Übertragung läuft`, `Vom Server bestätigt` und Konflikt/Fehler sind unterscheidbar. Serverbestätigung gilt nur für die aktuelle lokale Generation nach BTB, Medien und erfolgreichem lokalem Apply. Ein Download-Zeitstempel ist keine Uploadquittung.
- Baustellenliste zeigt den eigenen Strukturstatus einschließlich Abgleichproblemen. Lokale Änderungen bleiben während HTTP-Übertragung möglich. DB-Schreiben und Snapshot-Apply verwenden einen kurzen gemeinsamen Mutex und einen erneuten Vergleich; Netzwerk läuft außerhalb dieses Mutex.
- Vorhandene Pending-Mutation bleibt beim Retry unverändert, einschließlich clientChangeId und Basisversion. Konflikte werden nicht automatisch auf die neueste Serverversion umgebogen. Unbekannte/geänderte Basisstände werden konservativ blockiert. Auch parallele Änderungen an verschiedenen Knoten desselben Baums können deshalb zunächst eine bewusste Konfliktprüfung erfordern.
- Bereits vor dieser Erweiterung durchgelaufene Feed-Seiten werden nicht durch einen Cursorreset erneut eingespielt. Der normale vollständige Abgleich bzw. ein späteres Strukturereignis liest den aktuellen Baum. Kein App-Datenreset.

## Gezielte praktische Abnahme – noch offen

1. Zwei Bahnhöfe mit je Gl 4 und identischem Kurzname anlegen/öffnen. In Baum, BTB-Ortsauswahl, gespeichertem BTB und Fotodetail eindeutig unterscheiden; zusätzlich W 4/WVB prüfen.
2. Im Portal eine gebundene Unterbaustelle umbenennen. Pixel und Poco im Vordergrund ohne manuellen Vollsync nachziehen lassen; anschließend BTB/Foto öffnen. IDs und alte Zuordnungen müssen unverändert sein.
3. Struktur neu anlegen und denselben neuen Knoten sofort im BTB samt Bild verwenden. Speichern: zuerst dauerhaft lokal, danach Struktur plus exakt dieser BTB und seine Bilder übertragen. Andere BTBs/Fotos dürfen nicht ungefragt übertragen werden.
4. Flugmodus: Struktur/BTB/Bild ändern, App neu starten. Vormerkung muss erhalten bleiben. Netz wiederherstellen: Abgleich/Bestätigung prüfen.
5. Während Upload einen zweiten lokalen Save erzeugen. Alter Lauf darf weder neue Inhalte überschreiben noch deren Vormerkung bestätigen; Folgelauf muss geplant sein.
6. Gleichzeitig am PC und Handy denselben Knoten ändern. Versionskonflikt sichtbar, lokale Daten/Outbox erhalten, keine automatische Rebase. Dasselbe mit einem bereits gespeicherten BTB-Konflikt und erneutem Worker-Aufruf.
7. Unterknoten am PC löschen. Auf sauberem Handy Tombstone übernehmen, auf lokal geändertem Baum Schutz statt stiller Löschung prüfen. Historische BTB-/Fotozuordnungen kontrollieren.
8. Authentifizierung/fehlende Berechtigung sowie aktive Wiederherstellungssperre prüfen; keine neue Aktivierung, Datenlöschung oder blindes Nachsenden einer Recovery-ID.
9. Nur BTB oder nur FILE-Ereignis: kein zusätzlicher Baumabruf. Strukturereignis einer hier ungebundenen Baustelle darf keinen fremden Baum importieren.
10. Fehler nach erfolgreichem Upload/lokalem Apply sowie Prozessende während Bestätigung prüfen. Ohne vollständig bestätigte aktuelle Generation keine grüne Serverbestätigung.

## Nachweise

- Lokale gezielte Quellflussprüfungen: 16/16 PASS. Reproduzierbares Skript: `projektsteuerung/pruefungen/AND_NODE_DELTA_023.py`.
- Zehn neue Unit-Testmethoden: `app/src/test/java/de/tbhelfer/server/WorksiteDeltaPolicyTest.kt`. Feedumfang, scoped Zuordnung, Tombstones, reine BTB-Seite, Dirty-/Pending-/Paralleländerungen, Basisversionsschutz, Statusgenerationen, Bahnhof/Objekt/Kurzname und unveränderte Identität.
- CI-Ergebnisse werden nach tatsächlichem Abschluss in `06_E2E_TESTSTATUS.md` und `08_CHAT_CHECKPOINT.md` gesichert. Kein Source-Check wird als Kotlin-Build oder Gerätetest bezeichnet.

## Übernahme

In GitHub Desktop den Android-Branch aktualisieren; in Android Studio Unit-Tests und den gewünschten lokalen Build ausführen. Dieser Auftrag erzeugt keine APK und ändert keine Versionsnummer. Vor einer neuen auslieferbaren APK gelten weiterhin höhere Buildnummer und passende Release-MD.

Keine NAS-Übernahme für diesen Android-Code nötig. Keine `server/**`-Dateien oder Serverkonfiguration geändert. Ein separat vorbereiteter Portalstand ist ein eigener Serverauftrag. Kein Deployment ausgeführt.
