# AND-SPRACHE-WIDGET-1104 – Widgetaufnahme, Pausen und Zuordnung

## Auftrag und geschützter Bestand
Nutzer bestätigt „1-4 bauen“: Widgetaufnahme mit Zähler, verlässlicheres Pausenverhalten, Firmen-/Punktevergleich und ausdrücklich gemerkte Firmenkorrekturen. Anforderungen vor Entwicklung in ae3431991360f3062a39df95ffc996490aac18a4 gesichert.
Basis Android eb3b23be90d975f3b8aae7bcabc84012fd51b16c, Server 6ea0d987bebc67c346fca24714e4e1b9a8c852ea. Server unverändert; keine API-/Datenbankänderung.
Bestätigt und erhalten: Mikrofon im BTB öffnet Aufnahme und danach Prüfung; bestehende Aktivierungs-/Online-Voreinstellungen und ausdrückliche Übernahme mit bestehendem acceptSpeechDraft-Schutz.
Version 1.3.39 / Build 200089. Initialer Quellcommit 604d0866a0744596c95ed73dabe25b80e4ec41c4; korrigierter Baucommit fff93819607d313bc664d0c8dc62939e4cec86dc.

## Widget
- Explizites PendingIntent auf privaten Mikrofon-Foreground-Service; normaler Klick öffnet keine Activity. Aufnahmezustand ist prozesslokal und wird nach Prozessneustart nicht als laufend restauriert.
- Bei fehlender Mikrofonberechtigung separater Freigabebildschirm in eigener Task; er setzt die Aufnahme nach Freigabe fort und schließt sich. Kein Rückgriff auf zuletzt offene Haupt-App-Seite.
- Rot und kurze Startvibration erst nach onReadyForSpeech. Beim Neuverbinden eines kurzen Erkennungsabschnitts wird „Bitte warten“ gezeigt, keine vorgetäuschte aktive Aufnahme.
- Zweiter Klick oder Benachrichtigung „Beenden“ stoppt. Kein Prüfdialog nach Widgetaufnahme. Separate Zahl zeigt nach Aufnahme offene lokale Entwürfe für aktiven BTB; 0 ausgeblendet. Antippen öffnet Liste, Auswahl öffnet bestehenden Editor.
- Widget-Aktualisierung bei App-/Paketstart, aktiver Baustellenänderung, BTB-Widget-Aktualisierung, Entwurfsänderung und Aufnahmestatus; kurze Bündelung statt periodischer Geräteabfrage.
- BTB/Owner beim Start festhalten; Wechsel während Aufnahme verschiebt den Entwurf nicht. Recovery-/Anmelde-/Freigabewechsel beendet Aufnahme. Kein sticky Mikrofon-Neustart.

## Gemeinsame Spracherkennung
- Ein aktiver Erkennungskern verhindert gleichzeitige Widget-/BTB-Aufnahmen. Zwischen- und Endergebnisse werden zu einem lokalen Textentwurf zusammengeführt; Endergebnis ersetzt den vorläufigen Text seines Abschnitts.
- Ab API33 segmentierte Erkennung mit fünf Sekunden Stille angefragt. Sprachdienste dürfen diese Vorgaben ignorieren. Bei früher normaler Rückgabe erneuter begrenzter Erkennungsabschnitt nach300ms; derselbe Entwurf bleibt erhalten. Übergangslücken sind geräteabhängig und müssen getestet werden.
- Beginn neuer Sprache hebt Pausenfrist auf; wiederholtes Ende-Signal verschiebt sie nicht. Lautstärke allein verlängert keine Aufnahme, weil Maschinenlärm sonst als Sprache gelten könnte.
- Anfangsfenster ohne Sprache zehn Sekunden; automatische Sicherheitsgrenze fünf Minuten. Manueller Stop wartet höchstens1,5s auf Endergebnis und erhält sonst Teiltext. Keine Roh-Audiodateien.
- Späte Rückrufe abgeschlossener Sitzungen ignorieren. Technischer Fehler wird als Unterbrechung angezeigt; im BTB bleibt der Text über „Text und Zuordnung prüfen“ erreichbar. Widget informiert mit Benachrichtigung/Toast. Kein stiller Onlinewechsel.
- Plattformgrenzen: Sprachdienst-Rückmeldungen sind kein akustischer Gerätetest. Exakte fünf Sekunden und lückenlose Erkennung auf Pixel/Poco nicht bestätigt.

## Firmen und Punkte4–8
- Konkrete aktive BTB-Firmen als Namensbasis. Exakter Name/ausdrücklich bestätigter Alias zuerst; konservativer Schreibabstand bzw. zusammengezogene Wortteile als Vorschlag. Kurze Firmennamen werden nicht unscharf geraten; reine Präfix-/Suffixerweiterungen werden nicht übernommen.
- Mehrere passende Firmen/nahe Alternativen bleiben offen. Keine automatische Umschreibung des gesprochenen Beschreibungstextes; Firmenzuordnung verwendet die bestehende stabile ID.
- „Punkt vier“ bis „Punkt acht“ und Ziffern haben Vorrang. Mehrere ausdrücklich genannte Punkte bleiben offen. Zusätzliche Arbeits-/Abschnittsbegriffe einschließlich Einschottern und Beschädigungen. „Mangel bei Abnahme“ bleibt ohne ausdrücklichen Punkt mehrdeutig.
- Editor: zuerst richtige Firma auswählen, „Firmen-Schreibweise merken“, erkannte Variante eingeben/prüfen, „Zuordnung merken“ ausdrücklich bestätigen. Lokal nach Owner/Baustelle/Firma getrennt; ungültige, widersprüchliche oder entfernte Firmen ignorieren. Maximal200 gespeicherte Aliase.
- Einstellungen Spracheingabe: alle gemerkten Schreibweisen der eigenen Anmeldung löschen, separat bestätigen. BTB-Inhalte bleiben unverändert. Keine Cloud-Lernfunktion.

## Diagnostik und Validierung
- Vorhandenes lokales Fehlerprotokoll um Spracheingabe und feste Audio-/Freigabe-/Belegt-/Sprachpaket-/Unterbrechungs-/Speicher-/Dienstcodes erweitert. Keine gesprochenen Inhalte oder Rohfehlermeldungen gespeichert.
- Lokale Java-Kompilierung und tatsächliche Ausführung: bestehende ErrorHistoryRegression26 Prüfungen PASS; neue SpeechDiagnosticsRegression15 Prüfungen PASS (Klassifikation, Wiederherstellung, Export ohne Rohinhalt). Kein Android-Persistenz-/UI-Nachweis.
- XML-Dateien tatsächlich geparst. Zusatztests: SpeechMatchingTest9 Fälle; SpeechSessionStateTest8 Fälle. Beide Zusatztestklassen im erfolgreichen CI-Lauf bestanden.
- Erstlauf41836472128/Job126596552852 scheiterte bei compileDebugKotlin: fehlende SPEECH-Diagnoseverknüpfung, EditText.hint-Namenskonflikt und companion-verschachtelter Capture-Typ. Original-Logs gelesen, Ursachen korrigiert; keine APK aus diesem Lauf. Anschließend gezielte neue Validierung des korrigierten Commits.
- Quellvertragsjob126596553066: bekannte Checks13/19/83/85/86 weiterhin rot, Original-Logs gelesen. Keine Prüfungen abgeschwächt.

## Geräte- und Serverprüfung – OFFEN
1. Update mit bisheriger Signierung; vorhandenes Widget ohne Neuanlegen testen. Start aus zuletzt geöffneter beliebiger App-Seite und nach Prozessende. Startbildschirm bleibt sichtbar.
2. Freigabe erteilen/ablehnen; Mikrofon global sperren, Onlinefreigabe aus, Offlinebetrieb und fehlendes deutsches Modell prüfen. Keine Falschanzeige „Aufnahme läuft“.
3. Widget Start/Stop/Stop-Benachrichtigung; schnelle Doppelklicks und paralleles BTB-Mikrofon. Rot nur bei bereitem Mikrofon; kein zweiter Rekorder.
4. Satz mit1–3s Denkpause, danach mindestens5s Stille. Text vollständig und genau ein Entwurf. Pixel/Poco und verwendeten Sprachdienst getrennt dokumentieren.
5. Drei getrennte Aufnahmen, Zähler3, Auswahl der richtigen Aufnahme, Später prüfen, explizite Übernahme und fallender Zähler. Kein leeres Diktat mitzählen.
6. „Punkt acht, Pit rich, Geländer beschädigt“, mehrdeutige Firmen/Abschnitte und bewusst falsche Zuordnung korrigieren. Alias ausdrücklich merken; Neustart; andere Baustelle/Anmeldung darf ihn nicht verwenden. Aliase löschen prüfen.
7. Baustelle während Aufnahme wechseln, BTB abschließen, Rolle/Freigabe entziehen, Recovery, Prozessabbruch. Bereits gesicherter Text bleibt am ursprünglichen Owner/BTB. Kein automatischer Mikrofonneustart.
8. Bestehender BTB-Prüfablauf, Voreinstellungen, Offline-Übernahme und spätere Server-Synchronisation mit Firma/Ort/Punkt prüfen. Keine Duplikate bei Wiederholung.
Keine Installation, Pixel-/Poco-Aufnahme oder Live-Server-E2E in dieser Umgebung; adb/Emulator nicht verfügbar.

## Abschließende Buildnachweise
Status: QUELLSTAND FERTIG / UNIT UND LINT BESTANDEN / BUILD BESTANDEN / GESAMT-CI ROT / E2E OFFEN.
- CI41837314414, Androidjob126599452352 SUCCESS. testDebugUnitTest inklusive Kotlin/KSP/Java BUILD SUCCESSFUL8m57s; lintDebug BUILD SUCCESSFUL2m20s; assembleDebug BUILD SUCCESSFUL3m16s.
- Heruntergeladene JUnit-XML tatsächlich gelesen:171 Tests,0 Fehler/Fehlschläge/übersprungen. Sprache:16 SpeechDraftPolicy +9 SpeechMatching +8 SpeechSessionState =33 Tests PASS. Zusätzlich lokale Java41 Prüfungen PASS, separat vom JUnit-Ergebnis.
- Lint-XML:0 Error/0 Fatal,390 Warning. Gegenüber200088 zwei zusätzliche Hinweise im widget_speech.xml: SmallSp(10sp) und HardcodedText(Sprechen). Kein Fehler in den neuen Speech-Kotlin-Dateien; keine Lint-Regel unterdrückt. Widget-Darstellung/Schrift am Gerät noch offen.
- Berichte-Artefakt13328033547, Archiv-SHA25654d9724d8ebd79de8dba6a31b01d8b44cfe7e3e670144fc20771f58d8980ef0f lokal geprüft.
- Quellvertragsjob126599452848 FAILURE; Original-Logs erneut gelesen, unverändert Checks13/19/83/85/86. Gesamt-CI ausdrücklich nicht bestanden.
- APK-Artefakt13328335866; Archiv-SHA25673e75e301bc48544ff46c675210450b1ea0c0454677428bf61781629bc617256 lokal geprüft.
- APK26913432Bytes, SHA25697e190f1c126f247b2d0ecfe61a09b928325a23614fc6a8151b4f5c0cf7ea639; Manifest de.tbhelfer /1.3.39/200089 per SDK geprüft. apksigner verify erfolgreich, v1/v2 gültig.
- Baucommitfff93819607d313bc664d0c8dc62939e4cec86dc. Ausgelieferte MD enthält genau diesen Baucommit, APK-Prüfwert und Manifest; lokal mit heruntergeladenen APK-Bytes verglichen. Identische MD ins Repository übernommen, APK und MD gemeinsam bereitgestellt.
- CI-Debug-Zertifikat SHA25695bbe99ab9856bb47ce78e3c39813322efad46a6f710842fe7ce29f7cc65399b. Anders als200088; installierte Signatur nicht verglichen. Für Update bisherige lokale Signierung verwenden; keine Deinstallation oder Datenlöschung.
- Kein Serverkatalog-Release, kein Deployment und kein Geräte-/Live-Server-E2E-Nachweis. Nach Build nur Nachweisdokumentation geändert.
