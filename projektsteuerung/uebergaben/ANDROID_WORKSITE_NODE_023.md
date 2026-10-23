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


## AND-NODE-DELTA-023 – Abschluss des Quellstands (23.10.2026)
Status: **QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / KOTLIN-, UNIT- UND LINT-PRÜFUNG BESTANDEN / GERÄTE-E2E OFFEN**.
- Geprüfter finaler Runtimecode: `60e8dca5ae509bd6444302d13025996f9283f35b`. Letzter überprüfter Server-HEAD: `6303e25d4e3401f4de4c4f8dba0c05c1b33e604e`. Keine server/**- oder NAS-Konfigurationsänderung.
- Finale CI: [39996754175](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39996754175), Androidjob120562551153 SUCCESS. `:app:testDebugUnitTest` inklusive Kotlin-Kompilierung PASS (3m43s); `:app:lintDebug` PASS (1m27s). JUnit-/Lint-XML aus Artefakt12521180668 tatsächlich gelesen: **98 Tests,0 Fehler,0 Fehlschläge,0 übersprungen; zehn neue WorksiteDeltaPolicyTest-Methoden PASS; Lint0 Fehler/0 Fatal/391 Warnungen**. ZIP-SHA25677ce457c2b8dbc23f5e7696936ef035d8023c715289a0477782866ae99c42de5.
- Gesamt-CI bleibt ROT ausschließlich durch dieselben fünf dokumentierten Quellvertragsprüfungen13/19/83/85/86 (alte Schreib-/DELETE-Textmarker, alte Versionen/Server-/Update-Texte). Original-Logs des finalen Jobs120562550454 gelesen; Strukturprüfung18, Schema13/17 und weitere bestehende Schutzprüfungen PASS. Keine Prüfung deaktiviert/abgeschwächt und kein Fehler als Erfolg umgewertet.
- Neuer Vertrag im Client: WORKSITE_NODE im schnellen Feed; nur betroffene gebundene Baustellenstrukturen vor BTB-/Foto-Ortsauflösung; Cursor nach erfolgreichem Apply. Dirty/Pending/Konflikt/Paralleländerung blockieren Überschreiben. Nur LEAD darf eigene Struktur-Generation quittieren; vorgemerkter Helferbestand bleibt bei reinem Download erhalten. Bestehende Schlüssel/IDs und zusätzliche Serverpayloadfelder bleiben erhalten; bei bewusstem Bahnhofwechsel wird ein alter Bahnhofscode nicht weiter als neue Identität verwendet.
- Lokal speichern → dauerhafte selektive Vormerkung → betroffener BTB plus Bilder, zusätzlich geänderte Struktur. Explizite Statusgenerationen verhindern falsche Serverbestätigung durch Download oder älteren Upload. Bereits gespeicherter BTB-Konflikt blockiert den selektiven Export. Baustellenliste zeigt Strukturstatus/Probleme reaktiv. Unbekannte/abweichende Struktur-Basisversion konservativ erhalten statt automatisch rebasen.
- Reproduzierbarer lokaler16er Source-Check auf finalem Code erneut PASS. Keine APK/assemble, keine Versionsänderung/Release-Veröffentlichung; technischer Stand1.3.29/200079. CI-Validierung ist kein Installations-/Pixel-/Poco-/NAS-E2E-Nachweis.
- Übergabe und praktische Abnahme: `ANDROID_WORKSITE_NODE_023.md`. OFFEN: Portal->Pixel/Poco, Bahnhof/Gl4/Kurzname/Weiche, Struktur anlegen/ändern/löschen, Offline/Neustart, zweiter Save während Upload, parallel409/Basisstand unbekannt, Helfer, gezielte Bilder und Recovery-Barriere. Bekannte Basis-/Baumkonflikte erfordern bewusste Prüfung; kein automatischer Datenreset.
- Übernahme des Android-Quellstands über GitHub Desktop/Android Studio. **Für diesen Android-Auftrag keine NAS-Übernahme nötig; kein Deployment durchgeführt.** Separater Portalstand bleibt eigener Serverauftrag.
