# Android: zuverlässiger Schicht- und BTB-Abschluss – 24.10.2026

## Ausgangslage und Auftrag

Nutzer meldet, dass Helfer-Schichtende nicht zuverlässig auf dem Server bzw. beim verantwortlichen TB ankommt. Im Portal wird deshalb nachträglich abgeschlossen. Die Diagnose vom 24.10. bleibt als historische Quellprüfung erhalten; die konkrete damalige Laufzeitursache ist ohne Geräte-/Serverlogs nicht bewiesen. Nach Erklärung hat der Nutzer die Korrektur mit „okay weiter“ beauftragt.

Live-GitHub: Androidbasis `660adf4fb59222c97c8a16b44f5b51e9eee30c8c`, Server `8349f03eec04951a21ad66838a2602d4ae239241`. PR11 offen, PR6 Draft. Einstieg, Projektregeln und Steuerung01–08 gelesen. Entscheidung vor Code in `8842dc7bbb2a83d24ad8b6960f1f3c121248e8b2` gesichert. Neuere Serveränderungen betreffen PDF/Portal, nicht die geprüften Schichtendpunkte. Keine server/**-Änderung.

## Geändertes Verhalten

- Der Klick legt ein ausdrückliches Ziel fest: eigene Schicht beenden, bewusst fortsetzen oder als Haupt-TB den gesamten BTB abschließen. Ein Retry wählt nie anhand eines zwischenzeitlich geänderten Serverstatus die Gegenaktion.
- Auftrag wird synchron auf Datenträger gespeichert, bevor WorkManager gestartet wird. Pro BTB bleibt dieselbe Auftragskennung erhalten; normale Doppelklicks ersetzen sie nicht. Auftrag und Probleme überstehen App-Neustart. Beim Start wird die gespeicherte Warteschlange erneut eingeplant.
- Der Hintergrundpfad beachtet den gewählten Netztyp und die Wiederherstellungssperre. Auftrag folgt dem lokalen Recovery-Workspace; Geräte-/Serverbindung wird per Hash geprüft. Keine Geheimnisse im Auftrag. Bei geänderter Gerätebindung bleibt der alte Auftrag zur Prüfung erhalten und wird nicht unter einer anderen Sitzung ausgeführt.
- Zuerst betroffene geänderte Struktur, dann exakt dessen BTB-Inhalte und kleine Bilder. Vorhandener Versionskonflikt blockiert Export/Abschluss. Die Abfrage der Rollen darf bei Dirty/Pending die ursprüngliche Entity-/Basisversion nicht durch einen neueren Snapshot ersetzen.
- Dirty-Generation wird vor/nach Übertragung geprüft. Währenddessen neu gespeicherte Einträge werden nicht als übertragen quittiert. Lifecycle und selektiver Worker sind innerhalb des Prozesses serialisiert. Späte lokale Änderungen bei noch offenem Abschluss werden durch den selektiven Worker nicht als read-only aufgeräumt.
- Der Client prüft vor erneutem POST den aktuellen Serverstand. Ist das angeforderte Ziel schon bestätigt, ist kein weiterer POST nötig. Nach POST-Erfolg ohne erfolgreiche Statusabfrage bleibt der Auftrag offen, statt den Abschluss als gescheitert und seine Gegenaktion als nächstes Ziel zu behandeln. POST-Retries nutzen dieselbe `X-Client-Change-Id`; der Server hat dafür Mutation-Receipts.
- Unfertige Teilnehmer bleiben sichtbar. Der normale Auftrag wartet und versucht später erneut. `allow_unfinished=true` entsteht ausschließlich nach dem ausdrücklichen Dialog „Trotzdem abschließen“ der Teamleitung, mit eigenständiger Auftragskennung. Kein stilles Umgehen des Helferstatus.
- Knopf während lokaler Vorbereitung/laufendem Auftrag gesperrt. Anzeige unterscheidet auf dem Handy vorgemerkt, laufenden Serverabgleich und ausstehende Bestätigung mit Problem. Erst bestätigter Serverstatus zeigt die Schicht als beendet bzw. den BTB als abgeschlossen.
- Bei fokussierter offener Eingabe darf Abschluss nach einem Speichertimeout keinen lediglich geratenen letzten Stand verwenden. Der strikte Save-Bridge-Modus gilt nur für diesen Abschlussweg. Keine neue btbId, kein Datenreset, keine Neuaktivierung.

## Was unverändert bleibt

Server ist der gemeinsame Zustand. Normale Einträge werden weiterhin lokal gespeichert und selektiv übertragen. Der vorhandene BTB/BTB_ENTRY/BTB_PARTICIPANT/FILE-Delta-Feed liefert Änderungen aus Portal oder anderem Handy; kein neuer Pushkanal. Laufende Clients übernehmen nach erfolgreichem Abruf. Offline-/ausgeschaltete Geräte können erst nach Wiederverbindung nachziehen. Die Änderung ist keine Zusage sofortiger Zustellung an solche Geräte.

Helfer dürfen nur ihren Teilnehmerstatus ändern, kein Finalize und keine PDF-Freigabe. Der 16-Stunden-Autoabschluss und der serverweite Foto-Unlink bleiben getrennte offene Aufträge. WhatsApp→Teilen→TB-Helfer wird nach Nutzerentscheidung nicht ergänzt; Galerie & Dateien bleibt vorhanden.

## Prüfung

Finaler Runtimecode: `2570b4c222fa5fb9338e4471a22421425a99e8c1`, Ziel `1.3.31 / 200081` mit passender Release-MD.

- Reproduzierbarer Source-Check: `python3 projektsteuerung/pruefungen/AND_LIFECYCLE_024.py`, 18/18 PASS auf lokalem finalem Quellstand. Das ist kein Runtime-/Netznachweis.
- Zehn neue JVM-Tests für Zielbestätigung, Rollen, explizites Resume, Portalabschluss und inaktive/historische BTBs. Die Tests prüfen Policyentscheidungen; sie simulieren weder Android-SharedPreferences/WorkManager noch einen realen Verbindungsabbruch oder ein Pixel-/Poco-E2E.
- Erster Implementierungsstand `9627bdbea55a44aa0445e9c4be5d6f9450f7a49f`: CI40108267443, Androidjob120914396842 SUCCESS; Unit-/Kotlin- und Lintsteps erfolgreich. Nachfolgende Schutzkorrekturen benötigen den gesonderten finalen Lauf40108708246. Keine Übertragung des ersten Ergebnisses auf den neueren Code.
- **Finaler Lauf [40108708246](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/40108708246), Androidjob120915704503 SUCCESS:** Kotlin-/Unitstep und Lintstep erfolgreich. JUnit-/Lint-XML aus Artefakt12566170481 tatsächlich heruntergeladen und gelesen: **108 Tests,0 Fehler,0 Fehlschläge,0 übersprungen**, neue `BtbLifecyclePolicyTest`10/10 PASS; Lint0 Error/0 Fatal/391 Warning, keine Issue in neuen BtbLifecycle-Dateien. ZIP-SHA256 `42887659287318d828801142ef9b98e8cd6493c7bc2ad0f0a170840ab7b1a3aa`.
- Gesamt-CI weiterhin ROT durch dieselben fünf historischen Quellvertragsprüfungen13/19/83/85/86. Original-Log des finalen Sourcejobs120915704727 geprüft: alte Schreibpfad-/DELETE-Textmarker sowie alte Versions-/Server-/Updatetexte; Strukturprüfung18 und BTB-Lifecycle-Quellprüfung15 PASS. Keine Prüfung geändert oder abgeschwächt. Dies ist keine vollständige grüne Gesamt-CI und keine Geräte-/NAS-Freigabe.
- Während der Prüfung Server nochmals auf `ee6a879e13a6b97c4b75da97800d5f9a2923ac55` geprüft: weitere Änderungen ausschließlich PDF-Renderer/-Tests/-Dokumentation, keine Änderung des verwendeten Lifecyclevertrags.
- Kein adb/Emulator/Gradle in der lokalen Umgebung. Android-Emulator-QA-Skill gelesen und Verfügbarkeit geprüft. Keine tatsächliche Geräteautomation möglich. Context7-Dokumentation für WorkManager-Persistenz/Retry konsultiert.

## Praktische Abnahme – offen

1. Beide Handys mit demselben geprüften200081 aktualisieren, bestehende App-Daten behalten. Installierte Version im Paket prüfen.
2. Helfer schreibt Eintrag, speichert und tippt einmal Schicht Ende. Server bestätigt FINISHED; Helfer zeigt Schicht fortsetzen, Haupt-TB sieht den geänderten Teilnehmerstatus.
3. Haupt-TB schließt ab. Portal und beide Geräte übernehmen denselben finalen BTB. Verlauf und Fotozuordnungen erhalten; Abschluss-PDF separat prüfen.
4. Helfer offline Schicht Ende, App neu starten, wieder verbinden: vorgemerkter Auftrag bleibt erhalten, später FINISHED. Zeitpunkt und Meldungen auf beiden Geräten protokollieren.
5. Doppelklick und erneuter Klick nach verzögerter Bestätigung: kein ungewolltes Resume. Explizites Schicht fortsetzen ist weiterhin möglich.
6. Bildübertragung gezielt scheitern lassen: kein vorzeitiger Abschluss, Bild-/Inhaltsauftrag bleibt erhalten. Nach Beheben genau betroffenen BTB abschließen.
7. Paralleländerung im Portal, erneuter lokaler Save während Upload und späte Eingabe während Abschluss: keine stillen Überschreibungen; Konflikt bzw. noch ausstehende lokale Änderung sichtbar. Konflikt muss bewusst geprüft werden, kein automatisches Rebase/Resume.
8. POST erfolgreich, folgende Statusabfrage unterbrechen: Auftrag bleibt zur Bestätigung offen; nach erneutem Abruf kein Gegenbefehl/zweiter fachlicher Abschluss.
9. Unfertiger Helfer: Haupt-TB wartet oder bestätigt ausdrücklich Trotzdem abschließen. Keine automatische Übersteuerung. Gerätebindung/Recovery während ausstehendem Auftrag: Sperre und alter Bestand erhalten.

## Übernahme

Android-Quellstand über GitHub Desktop aktualisieren und in Android Studio bauen. Kein assemble/APK-Build, APK-/Release-Upload oder NAS-Deployment durch diesen Arbeitsblock. Für diese Clientänderung ist keine NAS-Dateiübernahme/Migration erforderlich. Bestehende NAS-Laufzeitversion und Pixel-/Poco-/Portal-E2E wurden hier nicht bestätigt. APK nur zusammen mit `projektsteuerung/releases/TB-Helfer_1.3.31_200081.md` verwenden; keine neue sichtbare Versionsleiste.
