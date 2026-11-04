# AND-SPRACHE-FELDER-1104

## Auftrag und Ursache
Bauauftrag vom04.11.2026, vor Code in930e12cf5474194ad18a9cc9a890bc67adfa1a61 gesichert. Basis Android2314b0177525a29308d22a3d8a120c1708860b8f, Server6ea0d987bebc67c346fca24714e4e1b9a8c852ea; PR11/6 offen.
SpeechDraft/SpeechDraftStore und der BTB-Übernahmepfad enthielten bisher keine Zeit-/KM-Felder. SpeechMatching suchte Abschnittswörter im gesamten Text; „Besondenheiten“ fehlte, und „Schwellen gewechselt“ konnte eine ausdrückliche Besonderheiten-Kopfzeile überlagern.

## Umsetzung
- SpeechHeader ist ein reiner Parser für die führende Kopfzeile. Firma ausschließlich aus aktiven Firmen/bestätigten lokalen Aliasen, benannter BTB-Punkt oder Punkt4–8, Uhrzeit bzw. Zeitbereich, Kilometer bzw. KM-Bereich. Unbekannter Text beendet die Zerlegung. Optionales „Text:“ oder gesprochenes „Text“ trennt Beschreibung ab; keine Zahlenentfernung im Beschreibungstext.
- Beispiel: Firma pittrich Besondenheiten von12Uhr bis14Uhr Kilometer33,8 bis Kilometer34,50 Schwellen gewechselt -> Firma pittrich, Punkt7,12:00–14:00,33,800–34,500, Beschreibung „Schwellen gewechselt“.
- Im bestehenden Prüfdialog Originaltext selektierbar, Beschreibung und vier Zeit-/KM-Felder bearbeitbar. Keine automatische BTB-Übernahme. Unterbaustelle bleibt bewusste Auswahl.
- Einmalige Vorbereitung beim Prüfen, additive JSON-Felder in bestehenden lokalen Entwürfen. Alte Entwürfe ohne neue Felder weiter lesbar. Nach erster Prüfung werden manuelle Änderungen nicht erneut zerlegt. Originaltext bleibt lokal und wird nicht in die Serverpayload/Logs aufgenommen.
- Validierung sowohl vor Dialogübernahme als auch im bestehenden geschützten BTB-Commit. Bereits vorhandene SectionItem-Felder uhrzeit/uhrzeitBis/kmVon/kmBis verwenden. Firma/Node/Entry-ID, Recovery-/Owner-/Dirty-/Lifecycle-/Rechte-/Versions-/Idempotenzprüfungen erhalten; keine neue Outbox/DB/Sync-Implementierung.
- Aufnahme-/Widget-Start/Stop, Benachrichtigungspunkt und bestätigte Voreinstellungen unverändert.

## Grenzen
- Kein freies semantisches Raten: ausgeschriebene Zahlwörter, unbekannte Firmen und unklare Kopfzeilen bleiben zur Prüfung im Text. Firmennamen müssen eindeutig sein. Eine unbekannte führende Angabe kann die nachfolgenden Angaben von der automatischen Erkennung ausschließen; der Originaltext bleibt vollständig.
- Punkt oder Komma mit1–3 Nachkommastellen als Kilometer; keine Tausendertrennung. Bloße KM-Ziffernfolgen ab vier Stellen und getrennte Ziffernblöcke werden in der Spracherkennung nicht geraten. Manuelle gültige Zahlen sind weiterhin möglich.
- Nachtzeit Ende vor Beginn bedeutet entsprechend bestehendem Vertrag Folgetag; rückläufige KM werden nicht umsortiert. Gleiche Zeiten bleiben gleich. Keine automatische Datumsänderung.
- Widersprüchliche Abschnitte bleiben offen. Unvollständige/repetitive/ungültige Zeit-/KM-Angaben bleiben als sichtbarer Text mit Hinweis. Bereits eindeutig erkannte Felder bleiben editierbar; der Nutzer entscheidet vor Übernahme.

## Android↔Server-Quellabgleich
Server1BtbApi normalisiert uhrzeit/uhrzeitBis; vorhandener Abschnittspayload transportiert kmVon/kmBis. Server6ea0d987: btb_times.preserve_entry_time_fields erhält Altclient-Endzeiten; btb_pdf_android_style liest uhrzeit,uhrzeitBis,kmVon,kmBis direkt aus dem Eintrag. Keine API-/Schemaänderung nötig, kein server/** geändert. Kein Live-Server-/NAS-Test aus diesem Quellabgleich ableiten.

## Prüfung
25 neue SpeechHeaderTest-Fälle: beide Nutzerbeispiele, Typo und Tätigkeitskonflikt, Textgrenze, Körperzahlen erhalten, fehlende Werte, Nachtzeit/rückläufige KM, Kurzzeitbereich, ungültige/unvollständige/repetitive Zeiten/KM, Abschnittskonflikt, Firma unbekannt/Alias/mehrteilig, bereits bearbeiteter Entwurf, manuelle Validierung, leere Beschreibung, negative KM, Punkte4–8, Ziffernmehrdeutigkeit, Singular und gesprochene Minuten.
Quellvergleich: zehn bestehende Schutz-/Idempotenzanker in gleicher Anzahl; der BTB-Übernahmediff beschränkt sich auf eine zusätzliche Feldvalidierung und die vier bestehenden Metadatenfelder. Gradle/Workflow ausschließlich Versionspaar geändert; Quellvertragsprüfungen unverändert.
Lokale Java-Regexprobe belegte vor CI-Abschluss, dass13Uhr14Uhr zunächst als13:14 zerlegt wurde; Korrektur mit negativem Uhr-Lookahead lokal nachgewiesen, Regression ergänzt. Erster CI-Lauf41852230460 wurde durch den korrigierten Commit über vorhandene concurrency-Regel abgelöst; keine erste APK ausgeliefert.
Finaler Baucommit48d3e7bf38b61e5b8d076418221a76378785c005, Version1.3.40/200090. CI41852652768/Androidjob126652970282 SUCCESS: testDebugUnitTest inkl. Kotlin/KSP/Java6m51s, lintDebug1m19s, assembleDebug2m33s. Tatsächlich gelesene JUnit-XML:196 Tests,0 Fehler,0 Fehlschläge,0 übersprungen;25 neue SpeechHeader-Fälle PASS. Lint0 Error/Fatal,390 Warning, keine neue Issue in speech-Kotlin. Berichtsartefakt13335484366, ZIP-SHA256ab0b2f3f49249f69dcdb4550558cc1f0e181d0be87b81a542f895709fe40b340.
Quellvertragsjob126652969506 FAILURE: bekannte Checks13/19/83/85/86, Original-Logs geprüft; keine Abschwächung. Gesamt-CI ROT, keine pauschale CI-Freigabe.
APK-Artefakt13335869306, Archiv-SHA256e9cb336aec7b3432487388f66a23d63243063360decb056f6a93637983b337e6. APK-SHA25662d4d8e79de26aadc4674a07e695f73b1c722d4a75d01f3e915530f831bcadd5. Manifestde.tbhelfer/1.3.40/200090 und apksigner verify(v1/v2) erfolgreich. ZertifikatSHA25682e857e402da4c09d1e5ec4cc4dbc54674ad05e23e7be68d816b81e7b9b470e5, abweichend vom vorigen CI-Build. Für Update bisherigen lokalen Signierschlüssel verwenden; keine Deinstallation/Datenlöschung. APK und zugehörige MD aus demselben Job, heruntergeladene Bytes/Hashes/Commit/Versionspaar geprüft. Repo-Release-MD entspricht genau der ausgelieferten Build-MD.
Kein Serverkatalog-Release, NAS-Deployment oder Produktionsdateneingriff. Lokale Umgebung ohne Kotlin/Gradle/ADB; kein Emulator-/Pixel-/Poco-Test.

## Geräteabnahme offen
1. Bestehende Installation datenbewahrend mit bisheriger Signatur aktualisieren. Widgetaufnahme und BTB-Mikrofon unverändert bedienen.
2. Nutzerbeispiele sprechen, Original und getrennte Felder kontrollieren; Eingaben ändern/leeren, „Später prüfen“, App erneut öffnen: manuelle Änderungen erhalten.
3. Punkt4–8, Firmenalias, unklare Firma, unbekannter Beginn, fehlerhafte/fehlende Zeiten und KM, kurze Denkpause, Textmarker/Körperzahlen prüfen.
4. Nur nach bewusster Übernahme genau ein Eintrag mit Text/Firma/Ort/Zeit/KM; offline speichern, Neustart und späterer Sync; Server-/Portal-/PDF-Werte vergleichen.
5. Wiederholte Übernahme/Anmeldungs-/Baustellenwechsel, abgeschlossener BTB und vorhandene Schutzgates prüfen. Kein Datenreset zur Fehlerumgehung.
