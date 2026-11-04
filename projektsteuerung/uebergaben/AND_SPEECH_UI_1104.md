# AND-SPEECH-UI-1104 – direkte Spracheingabe

## Auftrag und Abgrenzung
Benutzerauftrag „okay bauen“ nach Vorschlag: Widget und BTB-Mikrofon öffnen direkte Aufnahme und anschließend den prüfbaren Entwurf. Anforderungen vor Runtimeänderung in 8f19620968bda3772cc9a4e69a3218aa00dc6fcf gesichert.
Androidbasis 5922cec8067729d188433e8cefdb84b28e35ef96; Serverbasis 6ea0d987bebc67c346fca24714e4e1b9a8c852ea unverändert.
Runtime-/Baucommit 35241401a698744fd4b6d0d557df844687fc615b, Version 1.3.38 / Build 200088.

## Umsetzung
- Gemeinsamer recordingIntent für Widget und BTB: explizites Capture-Ziel, CLEAR_TOP; Widget zusätzlich NEW_TASK. Standard-Activity wird neu erzeugt, statt eine zuletzt offene Unterseite nur in den Vordergrund zu holen. Kein CLEAR_TASK.
- Vorhandene Widget-PendingIntents werden über MY_PACKAGE_REPLACED erneuert; kein Löschen des Widgets erforderlich.
- Aufnahme zeigt Baustelle/BTB-Datum und aktuelle Ortsauswahl als Kontext. Erkanntes wird laufend als lokaler Textentwurf gesichert. Die aktive BTB-ID wird vor Aufnahme erneut geprüft.
- Einstellungen erhalten eigenen Modus: Testaufnahme, Aktivierung, ausdrückliche Onlinefreigabe, Geräteprüfung und Deutschmodell. Testaufnahme erzeugt keinen BTB-Entwurf.
- Beenden wartet auf Endergebnis; nach maximal vier Sekunden wird bereits gesicherter Teiltext zur Prüfung geöffnet. Ohne gesicherten Text kein vorgetäuschter Erfolg. Unterbrechung/Rotation bewahren gesicherte Entwürfe.
- Automatische Prüfung nur für die konkrete Entwurfs-ID im geladenen passenden BTB. Signal nach BTB-Laden ermöglicht den Übergang auch beim Kaltstart. Kein beliebiges Öffnen eines anderen Entwurfs.
- Firma, Punkt und Unterbaustelle bleiben editierbar. Grün/Rot zusätzlich mit Textstatus. Eindeutige explizite Ortsnamen als Vorschlag; Mehrdeutigkeit, Teilwort, reine Zahl oder leere Bezeichnung lösen keine Zuordnung aus.
- „Ins BTB übernehmen“ nutzt unverändert den bestehenden acceptSpeechDraft-Pfad. Rechte/Owner, BTB-Identität, schreibbarer Lifecycle, Outbox, lokale Änderungen und Knotenzugehörigkeit werden weiterhin geprüft. Entwurf erst nach erfolgreicher Übernahme entfernen; idempotente Wiederholung bleibt erhalten.
- „Später prüfen“ sichert Änderungen lokal. Keine Audio-Rohdateien, kein stiller Onlinewechsel und keine Änderungen an Server, Datenbank oder API-Payload.

## Prüfplan Gerät / Server – E2E OFFEN
1. Update mit passender bisheriger Signierung, ohne Deinstallation oder Datenlöschung. Pixel und Poco getrennt erfassen.
2. Bestehendes Mikrofon-Widget aus beliebiger zuletzt geöffneter App-Seite antippen: direkte Aufnahme, richtiger aktiver BTB, kein alter Einstellungsbildschirm. Auch nach Prozessende prüfen.
3. BTB-Mikrofon: gleicher Ablauf. Ohne aktiven BTB klarer Hinweis und keine Erfassung auf einem geratenen Ziel.
4. Berechtigung erteilen/ablehnen, Spracheingabe deaktivieren, Offline ohne Netz und Online nur nach Freigabe prüfen.
5. Sprechen → Stop / Sprechpause → genau ein Prüfdialog; Text korrigieren und Punkt/Firma/Ort auswählen. Mehrdeutige Ortsnamen bleiben offen.
6. Später prüfen, Rotation und App-Wechsel: Entwurf bleibt auffindbar, keine Hintergrundaufnahme. Testaufnahme erzeugt keinen Entwurf.
7. Explizite Übernahme → genau ein lokaler BTB-Eintrag; nach Sync derselbe Eintrag mit Firma/Ort/Abschnitt am Server. Wiederholung darf nicht duplizieren.
8. Während Aufnahme BTB finalisieren, Rechte entziehen oder Recovery aktivieren: Übernahme muss weiterhin gesperrt sein, Entwurf erhalten.

## Bekannte unabhängige Grenzen
Gesamt-CI bleibt durch Quellvertragschecks 13/19/83/85/86 rot. Original-Logs dieses Laufs gelesen: fehlende frühere Schreibpfad-/DELETE-Marker, feste historische Versionen/Staging-URL sowie Updatequellmarker. Prüfungen nicht abgeschwächt.
Export-/Serverrenderer-Befunde aus AND-EXPORT-FIX-1104 bleiben offen; diese Sprachänderung ist keine Behebung der Serverdarstellung.
Kein adb/Android-Emulator in dieser Umgebung. Keine Installation, Pixel-/Poco-Aufnahme oder Live-Server-E2E bestätigt.

## Tatsächliche Validierung und Auslieferung
- Android-CI 41823796209, Buildjob 126553273851 SUCCESS: testDebugUnitTest inklusive Kotlin/KSP/Java BUILD SUCCESSFUL (4m57s), lintDebug BUILD SUCCESSFUL (1m21s), assembleDebug BUILD SUCCESSFUL (2m44s).
- Heruntergeladene JUnit-XML: 154 Tests, 0 Fehler/Fehlschläge/übersprungen; SpeechDraftPolicyTest 16 Tests inklusive drei neuer Ortszuordnungsfälle. Lint-XML: 0 Error/0 Fatal, 388 Warning; keine Meldung in speech/.
- Prüfberichte-Artefakt 13318487296, Archiv-SHA256 9f8c24aab4837faf5c4fdc6e249588df661b9156d28f3f802ae5498dfd508dc3 lokal geprüft.
- Quellvertragsjob 126553273292 FAILURE: Checks 13/19/83/85/86 weiterhin fehlgeschlagen. GESAMT-CI ROT, kein SOURCE-TEST-Gesamterfolg.
- APK-Artefakt 13319166177, Archiv-SHA256 e1ea4d90446512f90da5c1941d5fe6827dbb66c0e8416de3e4172528e90ba7dd lokal geprüft.
- APK 26878972 Bytes, SHA256 58e6be201841fad4b0fc7f16d3bb26f7d1f5806fc649c6d37ab4296ddc07f985. Manifest vom SDK geprüft: de.tbhelfer / 1.3.38 / 200088. apksigner verify erfolgreich, v1/v2 gültig.
- APK und Release-MD aus demselben Job. Lokaler Vergleich prüft APK-Prüfwert, Manifestzeile und Baucommit in der MD. Release-MD im Repository wird mit genau dieser erzeugten Datei synchronisiert.
- Debug-Zertifikat SHA256 719af09aa800a6636ea2c62b484f0894b788b20a2779c48b55752ea11b4c300e, abweichend von voriger CI-APK 200087. Installierte Nutzersignatur nicht geprüft. Mit bisheriger lokaler Signierung bauen; keine Deinstallation/Datenlöschung.
- Status: QUELLSTAND FERTIG / UNIT UND LINT BESTANDEN / BUILD BESTANDEN / GESAMT-CI ROT / E2E OFFEN. Kein Serverkatalog-Release, NAS-Deployment oder Installationsnachweis.
