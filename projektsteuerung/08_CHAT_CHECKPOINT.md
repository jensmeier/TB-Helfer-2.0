# TB-HELFER 2.0 – CHAT CHECKPOINT

## AND-APK-1102 – APK gebaut und geprüft
Status: BUILD BESTANDEN / APK-MANIFEST UND SIGNATUR GEPRÜFT / INSTALLATION UND GERÄTE-E2E OFFEN.
- Ausdrücklicher Buildauftrag vor Workflow in f1adaca gesichert. Baucommit510779453c85a25cefa2c1c0d886844ba1a41c4d; separater Packaginglauf41501002233, Job125468147229 SUCCESS. assembleDebug BUILD SUCCESSFUL2m31s. Eigener Workflow prüft vor Build unveränderte Android-/Gradlequellen gegen bereits validierten Runtimecommit5291d52 und erfolgreichen Androidjob125458915143. Keine doppelte Unit-/Lint-/Serverprüfung und kein weiterer Versionssprung.
- APK: TB-Helfer_1.3.35_200085.apk,26784582Bytes, SHA2568749a52bcda74af47e19998774c434c7e038dc471f0886b109606887a49fe627. Manifest per SDK-apkanalyzer geprüft: de.tbhelfer /1.3.35/200085. apksigner verify erfolgreich, v1/v2 gültig. Debug-Zertifikat SHA256cac77b36f13253f39b4feecd74ebcc8442361467f3eaefaef8f41a734b5a7b76. Gleichheit mit der am Nutzergerät installierten Signatur nicht geprüft; bei Signaturabweichung keine Deinstallation/Datenlöschung, sondern bisherige lokale Signierung verwenden.
- APK und Release-MD im selben Job zusammengestellt, MD enthält Baucommit/APK-SHA256/Manifest/Signaturnachweis. Artefakt13167664372, Archiv-SHA25677f5127f4b87bdf7a24539178d8345b966aab1b9c3d416f1798de381cba6a837. Downloadbytes/Archivhash und lokale APK-/MD-Zuordnung tatsächlich geprüft. Release-MD unter projektsteuerung/releases/ um Nachweis dieses konkreten Builds ergänzt; keine alte APK mit neuer MD.
- Technische Basis bleibt:122 JVM-Tests PASS, Kotlin/KSP/Java und Lint PASS; bekannte fünf Quellvertragsprüfungen der Gesamt-CI weiterhin ROT. Packaging-CI SUCCESS ist keine Aufhebung dieser Befunde. Keine Installation, Pixel/Poco-/HTTP-/NAS-E2E oder Abschlussdauer bestätigt.
- Keine Veröffentlichung im Serverkatalog, kein NAS-Deployment/server/**-Eingriff und keine produktive Änderung. Downloads zur manuellen Prüfung bereitgestellt. Nächster Schritt: Updatefähigkeit mit vorhandener Signatur und danach Handyabschluss ohne Portal-Klick, Bearbeiten/Firma/Ort/Fotobindung/Wetter und Helferablauf praktisch prüfen.

## AND-ABSCHLUSS-1102 – technische Validierung abgeschlossen, Gerätetest offen
Status: QUELLSTAND FERTIG / KOTLIN- UND UNIT-PRÜFUNG BESTANDEN / ANDROID-LINT BESTANDEN / GESAMT-CI ROT / E2E OFFEN.
- Geprüfter Runtimecommit5291d522a5cb7a8826d7d9190545ed3005d04b12. Ein gezielter Android-CI-Lauf41498293472, Androidjob125458915143 SUCCESS. testDebugUnitTest inkl. Kotlin-/KSP-/Java-Kompilierung BUILD SUCCESSFUL6m04s; lintDebug BUILD SUCCESSFUL1m40s. Kein assemble/APK-Build.
- JUnit- und Lint-XML aus Artefakt13168486586 tatsächlich gelesen:122 Tests,0 Fehler,0 Fehlschläge,0 übersprungen. Darunter10 LifecyclePolicy-,8 BtbIntegration031- und6 BtbPhotoDescription-Fälle PASS. Lint0 Error/0 Fatal/395 Warning; keine Issue in den aktuell geänderten Lifecycle-/App-/SelectiveSync-Dateien im Report. Artefakt-SHA2561f018377b9f28f7832ba6d943be4056fab83a33801de68490d4b2a2e7c124da6. Bestehende JVM-Fälle sind kein neuer WorkManager-/Netz-/Geräte-E2E-Test.
- Gesamt-CI ROT wegen Quellvertragsjob125458915716. Original-Logs gelesen: dieselben fünf dokumentierten Checks13/19/83/85/86 (alter Schreibpfadmarker, DELETE-Textanker, feste Altversionen/Server-/Update-Texte). Keine Prüfung deaktiviert/abgeschwächt. Androidjoblog enthält weiterhin nicht den Job abbrechende KSP/AWT-NullPointer-Diagnosen; erfolgreiche Taskausgänge nicht mit fehlerfreiem Toolinglog gleichsetzen. Runner-/Node-Deprecationhinweise separat, keine Budgetänderung.
- Umsetzung: unmittelbarer gezielter Versuch plus dauerhafter per-BTB-WorkManager-Retry mit gleicher changeId und gemeinsamem serialisierten Prozessor. Start/Resume/Netzrückkehr holen Lifecycle unabhängig von Dirty-Scopes nach. Keine laufenden POSTs per REPLACE abbrechen; selektiver Sync gibt an sicheren Grenzen ab. Echte Phasen/Wartegründe und vollständiger Statusdialog; bestehende Offline-/Dirty-/409-/Helfer-/Recovery-/Serverbestätigungsgates erhalten.
- Praxis weiterhin OFFEN: Pixel/Poco als Update, Haupt-TB-Abschluss ohne Portalaktion, Helferfinish, Offline/Neustart/Netzrückkehr, verlorene POST-Antwort, Doppelklick, erneuter Save während Upload,409 und Recovery. Kein Nachweis, dass der konkrete Fehler unter installierter1.3.34 allein hierdurch beseitigt ist. Wetter und Bearbeitungsfälle praktisch weiter offen.
- Quellversion1.3.35/200085 unverändert, Release-Begleitdatei an Quellkorrekturen/Testnachweise angepasst. Keine APK, Installation, Veröffentlichung, server/**-Änderung, Datenlöschung oder NAS-Übernahme. Dieser Androidstand braucht keinen neuen Servercode aus diesem Block. CI-Bericht: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/41498293472 . Nächster Schritt ist lokaler Android-Studio-APK-Build und Installation als Update für die praktische Abnahme.

## AND-ABSCHLUSS-1102 – gezielter Handyabschluss und Nachholung (02.11.2026)
Status: QUELLKORREKTUR / CI AUSSTEHEND / GERÄTE-E2E OFFEN. Auftrag vor Code in8aeffd5/a1fbae9 gesichert; Serveradd840e unverändert, Androidbasis6f8785d.
- Gemeinsamer serialisierter Prozessor für sofortigen Anwendungsscope-Versuch und dauerhaften WorkManager-Retry. Persistenter Auftrag und bestehende changeId werden nach Mutex-Erwerb erneut gelesen. Per-BTB-WorkManager KEEP statt globale APPEND-/REPLACE-Kette; aktiver POST wird nicht durch erneuten Klick abgebrochen. Kein neuer Lifecycle-Endpunkt, keine Rollen-/Dirty-/Teilnehmer-/409-/Recovery-Schutzumgehung.
- Start, Resume und Netzrückkehr holen auch LifecycleOutbox nach, unabhängig von normalen Dirty-Scopes. Unmittelbare Versuche bleiben im Anwendungsscope bei Navigation erhalten; Prozessende wird vom persistenten Hintergrundauftrag abgedeckt.
- SelectiveSyncWorker gibt vor dem nächsten sicheren Struktur-/BTB-Abschnitt an einen interaktiven Abschluss ab. Laufender Upload wird nicht abgebrochen; bestehender gemeinsamer Mutex bleibt erhalten.
- Tatsächliche Phasen aus Coordinator/Prozessor: Verbindung/Rechte, Struktur, Einträge, eigene Bilder, Serverstand, Abschlussbestätigung. Laufend versus wartend klar unterschieden; voller gespeicherter Grund beim erneuten Klick sichtbar. Netzrichtlinie/Gerätefreigabe erhalten konkrete Wartegründe, Lifecycle-Probleme gehen zusätzlich durch vorhandene sichere ErrorLog-Klassifikation. Keine ungeschützte neue Rohloghistorie.
- Lokaler Diff-Whitespacecheck der sechs geänderten Runtime-Dateien ohne Befund. Kein lokales Gradle/Kotlin/ADB/Postman vorhanden. Vorhandene Android-CI wird genau einmal für diesen relevanten Commit angestoßen: testDebugUnitTest inklusive Kotlin/KSP und lintDebug, ohne assemble/APK; bestehende Quellvertragschecks unverändert. Gestartet ist nicht bestanden. Keine neue JVM-/WorkManager-/Netz-/Geräte-E2E-Freigabe aus Quellreview ableiten.
- Noch abzunehmen: Haupt-TB ohne Helfer aus Handy abschließen ohne Portalaktion; Helfer finish dann TB finalize; offline+Neustart+Netzrückkehr; zweimaliger Klick während POST; Antwortverlust nach erfolgreichem POST; neue Eingabe während Upload/409; Recovery-Sperre; paralleler selektiver Sync. Konkrete damalige1.3.34-Laufzeitursache weiter nicht bewiesen. Geplantes Ende07–19 setzt keine neue Wartepflicht.
- Keine server/**-Änderung, Datenlöschung, Versionsanhebung, APK/Release-Veröffentlichung oder NAS-Übernahme. Quellversion1.3.35/200085; Freigabe erst nach aktuellen technischen und praktischen Nachweisen.

## AND-BTB-KORREKTUR-1102 – Teilkorrektur, Build und Praxis offen (02.11.2026)
Status: IN ARBEIT / TEILKORREKTUR IM QUELLCODE / ANDROID-BUILD UND E2E OFFEN. Auftrag vor Code in 6d0730d gesichert; Basis Android4427459, Serveradd840e unverändert.
- Belegter Speicherpfadfehler: normaler manueller Eintragseditor setzte hidden Text ohne onSave vor buildEntry/Neuladen. onSave ergänzt; Firma im normalen Dialog zusammen mit Ort/Text/Zeit/KM wählbar, bestehende Entry-ID bleibt erhalten und gebundene Fotokontexte folgen. Bestehender Langdruck-Firmenwechsel bleibt verfügbar.
- BTB-Fotogruppen-Editor erhält Firma/Unterbaustelle. Stabile Node-Key-Prüfung, bestehende Original-/Aufnahmedaten erhalten; alte Entry-Bindung wird bei Kontextwechsel durch eindeutig passendes Ziel oder ausdrückliches Lösen ersetzt. Text-/Zeit-/KM-Änderung stößt jetzt selektiven BTB-Sync an. Gruppenänderung gilt für alle im Dialog zusammengefassten Bilder. Foto-Detailseite der Suche bleibt lesend; eingebrannte Original-Overlays werden nicht umgeschrieben.
- Neue Fotos werden nach Einfügen über Kurztext innerhalb desselben aktiven BTB/Punkts/Firma/Node eindeutig gebunden; Aufnahmezeit begrenzt diese Zuordnung nicht. Vorhandene explizite Entry-ID hat Vorrang; kein Raten bei Mehrdeutigkeit. Bestehende historische Fotos nicht pauschal umgeordnet.
- Fotomaske fragt bei vorhandenem Langtext nach gewünschtem Kurztext und bestätigt das konkrete Textpaar, statt nur einen fehlenden Pflichtkurztext zu melden. Bildoverlay bleibt Kurztext; Android-BTB-/Druckereignisse zeigen Kurztext und abweichenden Langtext gemeinsam. Kein neuer API-/DB-Datentyp; kurztext/notiz bleiben getrennt. Allgemeiner manueller BTB-Editor besitzt weiterhin ein Textfeld, keine neue persistierte Kurz-/Langtextstruktur auf Verdacht.
- Wetterantwort gegen BTB/Datum/Schicht/Site/Wetterslots und Dirty-Zustand abgesichert; Metadaten erst nach erfolgreicher akzeptierter Antwort setzen, View-Lifecycle statt Fragment-Lifecycle. Das belegt eine Schutzlücke im bisherigen Code, nicht die konkrete Wetterursache am Nutzergerät.
- Tatsächlich ausgeführt: Java-Produktionshelfer BtbPhotoDescription mit java com.sun.tools.javac.Main kompiliert, sechs unabhängige Laufzeitfälle PASS (kurz, kurz+lang, gleich, Legacy-lang, leer, Trim); Diff-Whitespaceprüfung der zwei UI-Dateien ohne Befund. Sechs entsprechende JUnit-Fälle ergänzt, JUnit/Gradle/Kotlin/Android-Lint NICHT ausgeführt. Keine vollständige SOURCE-TEST-/BUILD-/CI-Freigabe. Kein ADB/Gradle/Kotlin lokal; Git-Klon per Shell an Netzwerk403 gescheitert, GitHub-Connector funktioniert.
- OFFEN: Kotlin/Java-Interop und gesamte Android-Toolchain; Pixel/Poco/NAS-E2E; konsistente Server-/Portal-Textdarstellung prüfen (server/** unverändert); normale Such-Fotodetailbearbeitung und Derivat-Overlaykorrektur nicht umgesetzt. Konkrete Wetter- und teilweise Abschlussursache benötigt installierten Build, genauen Ablauf und Fehlerprotokoll/Meldung. Keine weitere Abschluss-/Serveränderung auf Verdacht; Schutz vor unfinished Helfern/409/Offline unverändert.
- Keine Versionserhöhung, APK/Release-MD-Neuauslieferung, Actions-Auslösung, Datenlöschung oder Deployment. Quellversion bleibt1.3.35/200085; vorhandene Release-MD ist kein Buildnachweis. Nächster Schritt: Build/Unit/Lint, dann Eintrag/Fotogruppe Firma+Ort ändern, Kurz+Lang, späteres Schwellenwechsel-Foto, paralleler Wetterabruf/BTB-Wechsel und Haupt-TB/Helfer-Abschluss mit konkreten Logs prüfen.

## AND-ERRORLOG-026 – lokales Fehlerprotokoll, 26.10.2026
**QUELLSTAND 1.3.34/200084 / JAVA-KERNTEST BESTANDEN / ANDROID-BUILD UND GERÄTE-E2E OFFEN.** Nutzer bestätigt Umsetzung des vorgeschlagenen lokalen Fehlerprotokolls, Auftrag vor Codeänderung in f617aef gesichert. Basis Android16dc829 / Server65a14b1.
- Einstellungen > Wartung & Diagnose > Fehlerprotokoll; maximal200 Fehlergruppen, erste/letzte Meldung, Wiederholungszähler, Vorgang/Bereich/Build und sicherer technischer Code. Bekannte lokale Baustellenkennung wird beim Öffnen lokal aufgelöst. Neustartfeste JSON-Präferenzen im bestehenden Handy-/Recoverybestand, keine neue DB oder Migration.
- Detail-/Gesamtkopie, Android-Teilen nur auf Nutzeraktion; bestätigtes Löschen ausschließlich der Diagnosepräferenzen. Neue Fehler können danach neue Einträge erzeugen. Sync-Fehlerlink öffnet die gespeicherte Worker-ID ohne zusätzliche Zählung; historisches Protokoll wird bei späterem Erfolg nicht geleert.
- Privacy durch feste Klassifikation statt Speicherung von Rohmeldungen: keine Tokens/Passwörter/URLs/Rohantworten/Stacktraces/Dateinamen/BTB-Inhalte. Unbekannte Meldung neutral. Exporte enthalten auch keine lokal aufgelösten Baustellennamen. Vor Installation aufgetretene Fehler werden nicht nachträglich rekonstruiert.
- Erfasste Quellen: Vollsync, ausgewählte selektive BTB-/Struktur-/Medienfehler, Delta-Poller/-Worker, Katalogfehler, sichtbare Verbindungsprüfung sowie Wartungs-Updateprüfung/-download. Kein universeller Android-/Gradle-Crashlogger. Fehlerhistorie zählt Meldungen, nicht zwingend eindeutige HTTP-Requests.
- Tatsächlich lokal ausgeführt: Produktions-Java-Kern und Regression kompiliert (java com.sun.tools.javac.Main), **26 Prüfungen PASS** für Aggregation, stabile IDs,250 Kontexte/200er-Grenze, Sortierung, sichere Kernfeldwiederherstellung, Buildtrennung/Uhr-Rücksprung und Export ohne Testgeheimnisse. XML beider Layouts geparst. Kotlin-Strukturprüfung ist kein Build.
- **Kotlin/Java-Android-Interop, Gradle/KSP/Unit/Lint/APK, echte SharedPreferences-/Neustart-/UI-/Teilen-/Löschtests OFFEN.** Android-SDK/Gradle-Abhängigkeiten fehlen lokal; keine alten CI-/Installationsnachweise auf200084 übertragen. Keine APK/Veröffentlichung/Actions/NAS-/Produktivänderung; Budget unverändert, kein neuer Minutenverbrauchsnachweis.
- Frühere files.view-/DNS-Fehler sind durch das Protokoll nicht als behoben bestätigt. Nutzer-/Helferrechte, Retry/Cursor, RecoveryHttp/-Protokoll und Servercode bleiben unverändert. Neue Release-MD [TB-Helfer_1.3.34_200084.md](releases/TB-Helfer_1.3.34_200084.md); Übergabe/Abnahme [FEHLERPROTOKOLL_026.md](uebergaben/FEHLERPROTOKOLL_026.md). Kein neues Serverdeployment für diese Funktion.

## AND-SYNC-ANZEIGE-026 – 26.10.2026, Quellstand 1.3.33/200083
**ANZEIGE IMPLEMENTIERT / JAVA-MONITORREGRESSION BESTANDEN / ANDROID-BUILD UND GERÄTETEST OFFEN.** Nutzer berichtet: Baustelle inzwischen im Portal vorhanden. Das ist ein positiver Teilnachweis, keine Bestätigung der installierten Version oder des gesamten Haupt-/Helfer-/Portal-E2E.
- Auf „Daten vom NAS“ eigener Livebereich: frische lesende Prüfung der tatsächlichen Capability-API mit JSON-Validierung; Offline/DNS/Timeout/HTTP-Fehler unterscheiden. Prüfung nur bei sichtbarer Seite ungefähr alle30s nach vorheriger Antwort, Zeitstempel/Alter sichtbar. Erreichbarkeit ist kein Login-/Berechtigungs- oder Vollständigkeitsnachweis.
- Vorbereitung, Vollabgleich, Senden und Empfang getrennt, mit realen Phasen aus den vorhandenen Laufwegen. Monotone Laufzeit/Schrittdauer sekündlich; normale Long-Poll-Wartezeit als normal bezeichnet. Nur bekannte vorgemerkte BTB-/Strukturaufträge zählen; keine behauptete globale Datei-Gesamtmenge. Fehler-/Konfliktanzeige bleibt separat erhalten.
- Keine erfundene ETA/Prozentzahl: **„Restzeit: noch nicht berechenbar.“** Kein laufender Vorgang bedeutet nicht „alle Daten synchronisiert“. Beendete/abgebrochene Läufe werden per finally aus dem Monitor entfernt; parallele Worker überschreiben einander nicht.
- Reine Beobachtung, keine Änderung an Retry, Cursor, RecoveryHttp/-Protokoll, Mutation, Rollen oder Servercode. Der reine Java-Monitor wird wegen der bestehenden Quellkopie explizit im generierten Java-Source-Set registriert; Kotlin-/Room-Quellen bleiben unverändert zugeordnet.
- Lokal tatsächlich kompiliert und ausgeführt: Java17-Compiler über java com.sun.tools.javac.Main, **13 Prüfungen PASS**, einschließlich800 paralleler Monitor-Lebenszyklen. Layout-XML geparst; strukturelle Kotlin-Klammerprüfung ist kein Kotlin-Build. Kotlin/Java-Android-Interop, Gradle/KSP, Unit/Lint, APK/Installation und echte Netz-/UI-Tests **OFFEN** (Android-SDK/Gradle-Abhängigkeiten fehlen). Keine Actions gestartet, Budget unverändert; keine NAS-/Produktivänderung oder Veröffentlichung.
- Neue passende Release-MD: projektsteuerung/releases/TB-Helfer_1.3.33_200083.md. Keine APK erzeugt. Server bleibt65a14b1; kein neues Serverdeployment für diese Anzeige. Screenshot erlaubt keinen Versions-/Buildnachweis. Details: [SYNC_ANZEIGE_026.md](uebergaben/SYNC_ANZEIGE_026.md). Frühere 200082-Integration bleibt enthalten, ihr Geräte-E2E weiterhin offen.

## Aktueller Integrationsblock AND-BIDIR-026 – 26.10.2026
**QUELLSTAND IMPLEMENTIERT / SERVER-HANDLER- UND PORTAL-TESTS BESTANDEN / ANDROID-BUILD UND GERÄTE-/NAS-E2E OFFEN.** Dieser Abschnitt ersetzt für AND-BIDIR-026 die frühere Angabe „Korrektur offen“; historische Abschnitte unten bleiben als Verlauf erhalten.
- Ausdrücklicher Nutzerauftrag zur Entwicklung beider Seiten. Basis Android6a0e7e4 / Server923cf93, Auftragscommits Android21ce70e / Server1f925bd. Androidziel jetzt **1.3.32/200082** mit passender Release-MD; installierter Altstand laut vorherigem Screenshot1.3.31/200081, keine Installation200082 bestätigt.
- Android: dauerhafte idempotente Neuanlage ohne Helfer, Antwort-/Binding-Recovery, lokale Node-Keys, Haupt-TB-Entwurfsvorschau, versionierte Namensoutbox, Kontext-/Teamempfang und Rechte-/Dirty-Schutz. Gebundene normale BTB-Sends bleiben selektiv. Lokale Pending-Daten nach Teamwechsel bleiben erhalten und werden nicht ins neue Team übertragen.
- Server: worksite_head_update_v1, PATCH /api/v1/worksites/{id} für name+base_version; Rechte/409/Receipt-Replay geschützt. Portal-Umbenennen und automatische Liste über autorisierten, typgefilterten SSE; Reconnect/Focus/Intervall und Schutz vor verspäteten Listenantworten.
- Lokal tatsächlich ausgeführt: **14/14 Python-Handler-/SQL-Fälle PASS** (Produktionsfunktionen, SQLite-Adapter); **Node-VM-Portalregression PASS**; Python-Syntax der geänderten Runtime-Module PASS. Testharnessfehler vor finalem PASS korrigiert, keine Assertions abgeschwächt. Kein PostgreSQL-/FastAPI-HTTP-/Android-/NAS-E2E.
- Android-SDK, Gradle-Cache und Kotlin-Compiler lokal nicht verfügbar; Abhängigkeiten nicht abrufbar. **KOTLIN/ROOM, UNIT, LINT, APK-BUILD, INSTALLATION OFFEN**. Frühere 200081-CI gilt nicht für200082. Keine APK/Veröffentlichung, keine Actions gestartet ([skip ci]); Budget unverändert, tatsächlicher aktueller Minutenverbrauch nicht erhoben. Keine Migration, kein NAS-/Produktivzugriff oder Deployment.
- Geschützte BTB-SYNC-002/BTB-UI-001/PORTAL-LIVE-001/UPD-001 bleiben Vorgabe. Gezielte Ergänzung der Read-only-/Pending-Behandlung bei Team-/Rechtewechsel und der Vorschau für lokalen Haupt-TB ausdrücklich Teil dieses Auftrags. Bestehende IDs, Originalfotos, Abschluss-PDFs und DB-Inhalte nicht verändert. Unabhängige Review und praktische Regression weiterhin offen.
- Übergabe/Dateiliste/Abnahme: [INTEGRATION_BAUSTELLEN_026.md](uebergaben/INTEGRATION_BAUSTELLEN_026.md); Vertrag [BAUSTELLEN_SYNC_026.md](vertraege/BAUSTELLEN_SYNC_026.md). Zuerst Android compile/unit/lint; anschließend passender Serverstand und200082 auf Pixel/Poco testen. Serverübernahme erfordert separaten Deploymentauftrag. Baustelle behalten, keine doppelte Portalneuanlage/Neuaktivierung.

Stand: 2026-09-27

Letzter gesicherter Arbeitsblock: **Projektsteuerung vereinheitlicht und verifiziert**.

- Zentrale: `projektsteuerung/`
- alte Top-Level-Ordner `projektstatus/`, `releases/`, `uebergaben/` entfernt
- branch-spezifische Projektregeln nach dem Umzug kontrolliert und auf denselben Einstieg korrigiert
- kein Android-/Server-Fachcode in diesem Schritt geändert
- Android-Fachstand vor Steuerungsumbau: `d272f8a7586cce3ea1eda29100473f7af5d540bb`
- Server-Fachstand vor Steuerungsumbau: `c1204501fd2147452f2eb5e47e1c2b9c871b8ecd`
- Android 1.3.19 / 200069: Build/Install/E2E offen
- Server 1.0.0-dev: Migration 015 Runtime-E2E offen
- nächster gemeinsamer Technikpunkt: IMG-001 Bildtransport
- weiterer P0: Poco bekommt keine Daten

Neuer Chat: direkt `projektsteuerung/00_START_HIER.md` lesen und Live-HEADs prüfen.

## Arbeitsentscheidung / Testnachtrag 26.09.2026

- Delta-Wait-Kapazitätsteiltest praktisch: 255 getrennte Portal-Sessions, 255/255 erfolgreich, 0 Fehler.
- Testsession-Reste kontrolliert bereinigt; abschließend 0 aktive Python-Test-Sessions.
- Das ist **nicht** der vollständige 15×15-E2E.
- Neue verbindliche Reihenfolge: zuerst Haupt-TB-Handy ↔ Server/Portal, danach HELPER/Poco, danach weitere Fach-E2Es.
- Vollständigen Lasttest erst nach funktionaler Stabilität und nach geplantem 8-GB-RAM-Ausbau bzw. NAS-Wechsel erneut durchführen.
- Keine Performanceänderungen auf Verdacht.

## Gerätebefund NET-001 – Testbaustelle

- Hauptgerät: Testbaustelle angelegt.
- Sync scheitert unmittelbar mit `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.
- Fachsync ist damit in diesem Lauf noch nicht geprüft.
- Nächster Schritt: öffentliche Domain/API-Erreichbarkeit und danach Geräte-DNS/Netz prüfen.
- Keine Codeänderung auf Verdacht.


## IMG-001 Quellaudit 26.09.2026

- Quellaudit abgeschlossen; kein Fachcode geändert.
- Android lädt BTB-/Mangel-/Abnahmefotos aktuell aus dem lokalen Originalpfad hoch.
- Server speichert empfangene Bytes unverändert; APP-Volloriginal auf NAS ist daher Folge des Android-Uploads.
- Server-Renditions existieren: `thumb` 320 px / `preview` 1280 px.
- Android-Dateiclient nutzt diese Renditions nicht und lädt fehlende Bilder über `/content`.
- Lokales Android-Thumbnail 512 px/Qualität 82 existiert bereits, ist aber nicht in den Online-Sync eingebunden.
- Metadatenlücken: GPS-Genauigkeit nicht persistent; Mangel-/Abnahmefoto-Upload strukturell unvollständig.
- Detailnachweis: `projektsteuerung/uebergaben/IMG-001_BILDTRANSPORT_AUDIT_2026-09-26.md`.
- E2E-PRIO-001 bleibt maßgeblich: NET-001 → Hauptgerät↔Portal → HELPER/Poco → danach IMG-001-Implementierung.

## NET-001 Nachtest – Browser erreichbar
- Auf demselben Hauptgerät sind `https://www.tb-helfer.de` und `https://api-v1.tb-helfer.de/health/live` im Browser erreichbar.
- Öffentliche DNS/API-Erreichbarkeit ist damit grundsätzlich vorhanden.
- Der vorherige App-Fehler kann transient oder app-/prozessspezifisch gewesen sein.
- Nächster Schritt: in der App denselben Sync mit `Neu laden` erneut auslösen und die genaue Ausgabe prüfen.
- Keine Codeänderung auf Verdacht.

## NET-001 Reproduktion – App-only DNS-Fehler
- Browser auf demselben Hauptgerät: Portal + API-Health erreichbar.
- App `Neu laden`: gleicher Fehler `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.
- Damit allgemeine öffentliche DNS-/Cloudflare-Störung als alleinige Ursache ausgeschlossen.
- Nächster Schritt: Android-Netzpfad/Serverbasis/HTTP-Client prüfen; Server/DB nicht auf Verdacht ändern.

## NET-001 – Force-Stop + Mobilfunk ohne Änderung
- App vollständig gestoppt und neu gestartet: gleicher DNS-Fehler.
- WLAN aus, Mobilfunk aktiv: gleicher DNS-Fehler.
- WLAN-spezifische Ursache dadurch unwahrscheinlich.
- Nächster Schritt: Android `Privates DNS`, VPN und DNS-/Adblock-Filter prüfen.
- Keine App-Daten löschen, keine Neuaktivierung, keine Serveränderung auf Verdacht.

## Praxisnachtrag – Pixel synchronisiert, Poco Erstabgleich läuft
- Pixel hat inzwischen wieder mit dem NAS synchronisiert.
- Frühere App-DNS-Störung daher aktuell kein dauerhafter Blocker; Ursache weiterhin unbekannt.
- Poco-App gelöscht, neu installiert und neu aktiviert.
- Poco-Erstabgleich läuft nach >3 Minuten weiter.
- Nächster Schritt: anhand Android-Quellpfad klären, was beim Erstabgleich alles geladen wird und welche Phase die lange Laufzeit verursacht; keine weiteren Resets auf Verdacht.

## BTB-SYNC-001 – 26.09 fehlt/verschwindet
- Poco-Erstabgleich abgeschlossen; Baustelle herunterladen erfolgreich.
- Pixel: neuer BTB 26.09 angelegt.
- Portal: zeigt weiterhin BTBs 23./24./25.09, 26.09 fehlt.
- Poco: 26.09 erscheint kurz, dann „kein BTB vorhanden“.
- Nächster Schritt: zuerst Serverzustand/DB/API/Change-Feed prüfen, danach Android-Snapshot/Active-Pointer. Keine Änderung auf Verdacht.

## BTB-SYNC-001 Diagnose – Pixel 1.3.4 vs Poco 1.3.19
- Server-Geräteabfrage zeigt:
  - Pixel / zinkel.jens: TB-Helfer 1.3.4
  - Poco / poco: TB-Helfer 1.3.19
- Change-Feed zeigt für denselben BTB am 26.09 mehrere UPDATEs bis mindestens Version 19.
- Aktueller Android-Zielstand laut Projektsteuerung: 1.3.19 / 200069.
- Nächster Schritt: Pixel-Version angleichen bzw. 200069-Installation nachweisen; erst danach BTB-SYNC-001 erneut testen.

## Korrektur – Pixel-Version
- Benutzer bestätigt Pixel ebenfalls auf TB-Helfer 1.3.19.
- Serverwert 1.3.4 ist stale: `app_version/app_build` werden nur bei Geräteaktivierung geschrieben; normale Requests aktualisieren nur `last_seen_at`.
- Vorherige Schlussfolgerung „gemischte Android-Versionen“ verworfen.
- BTB-SYNC-001 weiter diagnostizieren: 26.09-BTB + Team-`active_btb_id` + API-Snapshot.

## BTB-SYNC-001 – Servereingang 26.09 bestätigt
- BTB-ID: `ba2144b7-9ede-4f53-abdc-1accd172e875`
- work_date: 2026-09-26
- Change-Feed: wiederholte UPDATEs, sichtbar mindestens Version 7–19.
- Pixel→Server ist damit nicht der fehlende Schritt.
- Nächster Diagnosepunkt: Team/`active_btb_id`/Status/Teilnehmer für exakt diesen BTB, danach Portal- und Poco-Leseweg.

## BTB-SYNC-001 – Server-Lifecycle korrekt

- Direkter DB-Check für BTB `ba2144b7-9ede-4f53-abdc-1accd172e875`:
  - Baustelle: `GE Immenstadt - Kempten`
  - Team: `pixeljens`
  - Team-Lead: `zinkel.Jens`
  - `teams.active_btb_id` == BTB-ID
  - `active_date=2026-09-26`
  - `active_status=ACTIVE`
  - BTB selbst: `status=ACTIVE`, `version=19`
  - Client-BTB-ID vorhanden
  - Poco = HELPER / participant `WORKING`
  - zinkel.Jens = LEAD / participant `WORKING`
- Damit ist der kanonische Server-Lifecycle für den 26.09-BTB korrekt. Ursache liegt jetzt im Portal-/Android-Lese-/Anwendungspfad, nicht in fehlendem `active_btb_id` oder Teilnehmerstatus.

## BTB-SYNC-001 – Portal nach Reload korrekt
- Portal zeigt nach vollständigem Reload Team `pixeljens`, aktiven BTB `ba2144b7-9ede-4f53-abdc-1accd172e875`, Status ACTIVE, Datum 26.09.
- Pixel→Server→Portal ist für diesen BTB damit praktisch bestätigt.
- Restfehler ist Android/Poco: BTB kurz sichtbar, danach kein aktiver BTB.
- Nächster Schritt: Android Snapshot-/Active-Pointer-/UI-Pfad diagnostizieren.

## BTB-SYNC-001 – Poco erneut reproduziert nach Portal-Bestätigung
- Portal 26.09 ACTIVE bestätigt.
- Poco zeigt 26.09 kurz und verliert ihn unmittelbar wieder.
- Restursache ist damit auf Android/Poco eingegrenzt.
- Nächster Schritt: Active-Pointer/Marker/UI-Pfad im aktuellen Android-HEAD analysieren.

## BTB-SYNC-001 – Source-Ursache Active-Pointer
- Quellpfad isoliert:
  - `CollabLocalBridge.applyServerStateToLocal()` setzt bei `serverActive=true` den lokalen Active-Pointer korrekt.
  - `BtbModernLifecycleInstaller` fragt über `BtbUiSaveBridge.aktuell()` ungefähr alle 600 ms den aktiven BTB ab.
  - `BtbActiveContext.laden()` kann danach den gerade gesetzten Pointer wieder löschen, wenn `BtbDokumentVersionierung.status(...)=ABGESCHLOSSEN` ergibt.
  - Dieser lokale Dokumentstatus wird dort derzeit geprüft, **ohne einen bereits bestätigten `BtbServerStateStore.active=true` als Vorrang zu behandeln**.
- Das passt exakt zum Praxisbild: 26.09 erscheint kurz, nach etwa einer Sekunde „kein BTB vorhanden“.
- Serververtrag ist bereits kanonisch: solange Server-`active_btb_id` auf den BTB zeigt und Teilnehmer `WORKING` ist, darf ein lokaler/staler Abschlusszustand den Active-Pointer nicht lösen.
- Nächster Zuständigkeitswechsel: ANDROID muss `BtbActiveContext` korrigieren und danach Build/Install/Poco-E2E durchführen.
- SERVER-Seite bleibt unverändert; Server-Lifecycle/Portal sind bestätigt.


## BTB-SYNC-001 – Android-Fix umgesetzt
- Vertrags-/Diagnosestand `e71585cb21fa13c01f184036337d3ea68725f37e` vor Implementierung in Android-Projektsteuerung übernommen.
- Android-Codefix: `f57c532863d2f92b81729b9af1466b57a22f8723`.
- `BtbActiveContext` respektiert `BtbServerStateStore.active=true` vor lokalen PDF-/Baseline-/Abschlusszuständen.
- Pointer wird in diesem servergebundenen Leseweg erst bei `FINALIZED && !active` gelöst; lokale/Legacy-Logik gilt nur ohne Server-Snapshot.
- Keine Server-/NAS-/Poco-Resetänderung.
- Version bleibt 1.3.19 / 200069.
- Build: **OFFEN**. GitHub Actions erzeugte für den aktuellen HEAD keinen Run; kein Buildnachweis.
- Nächster Nutzer-/Geräteblock: Android Studio bauen, APK auf Pixel + Poco installieren, denselben BTB 26.09 erneut prüfen.


## REL-001 – Version hochgezählt
- Benutzerhinweis bestätigt: erneute Verwendung von 1.3.19 / 200069 für eine neue APK würde den Updatepfad nicht sauber unterscheiden.
- Verbindlicher nächster Android-Zielstand: **1.3.20 / 200070**.
- 1.3.19 / 200069 bleibt historischer vorheriger Stand.
- Erst nach Dokumentationscommit werden Builddateien/Release-MD angepasst.


## 1.3.20 / 200070 vorbereitet
- Benutzerhinweis zur wiederverwendeten Buildnummer umgesetzt.
- Dokumentationsentscheidung: `32f57241a9717a6748d427af53eae3a593ad656b`.
- Build-/Release-Quelländerung: `f9a7421ec9265b73ca170d28a1946644527cbe5a`.
- Aktueller Android-Zielstand ist **1.3.20 / 200070**.
- Release-MD liegt unter `projektsteuerung/releases/TB-Helfer_1.3.20_200070.md`.
- Build/Installation/E2E weiterhin offen.


## UPD-001 – Praxisbefund nach Update
- Benutzer meldet: Updatevorgang durchgelaufen.
- Danach zeigt „Nach Update suchen“ weiterhin installierte Version 1.3.19.
- Hauptansicht zeigt weiterhin „Update verfügbar“.
- Zielrelease ist 1.3.20 / 200070.
- Fehler zuerst dokumentiert; nächster Schritt ist Quellprüfung der Updateanzeige und Vergleichslogik.
- Keine Neuaktivierung, kein Löschen von App-Daten, kein Server-/NAS-Umbau auf Verdacht.


## UPD-001 – Quellprüfung abgeschlossen
- `currentVersionLabel()` zeigt `BuildConfig.VERSION_NAME/VERSION_CODE`; daher ist 1.3.19 nach Installer kein stale UI-Wert.
- Startmenü prüft Update beim Resume erneut, Update-HTTP nutzt `useCaches=false`.
- Serverrelease kann aktuell eine Binärdatei erhalten, deren internes APK-`versionName/versionCode` nicht zu den Releasefeldern passt.
- Android prüft aktuell nur SHA-256 und Dateigröße.
- Schutzfix: APK-Package + eingebettete Version/Build vor Installer validieren.
- 200070 ist bereits benutzt; nächster auslieferbarer Build: **1.3.21 / 200071**.


## UPD-001 – Android-Schutzfix
- Commit `19d688a4752c98209570e0ff69ccd2eae789541e`.
- Version **1.3.21 / 200071**.
- heruntergeladene APK wird vor Installer auf Package-ID + eingebettete Version/Build geprüft.
- falsches/altes APK wird verworfen statt installiert.
- Server-Releaseverwaltung sollte denselben Abgleich später zusätzlich serverseitig erzwingen; kein Servercode in diesem Android-Block geändert.
- Build/Installation/Update-E2E offen.


## REL-FILE-001 – dauerhaft merken
- Benutzeranforderung: Bei jedem neuen Android-Build/GitHub-Übergabestand eine Release-MD im bekannten TB-HELFER-RELEASE-Format mitführen und zusätzlich als Download bereitstellen.
- Referenzformat bestätigt durch die bereitgestellten Dateien 1.3.16/200066, 1.3.17/200067 und 1.3.18/200068.
- Die Datei enthält die Release-Metadaten für den Server-Draft, ersetzt aber nicht die APK-interne Version.
- Release-MD + APK müssen immer aus exakt demselben Build stammen.


## REL-FILE-001 – CI abgesichert
- Dauerregel bereits in Projektregeln/Projektsteuerung gesichert.
- CI-Commit: `e76933cd7270da21d8cd251ece1cba87c7db0cf8`.
- CI prüft passende Release-MD gegen Android-`versionName/versionCode`.
- CI-Artefakt enthält automatisch APK + passende Release-MD.
- Zusätzlich wird die jeweilige Release-MD im Chat als Download bereitgestellt.


## UPD-001 – Praxis bestätigt
- Benutzer bestätigt den Updateweg **1.3.21 / 200071** als funktionierend.
- Build und Installation sind damit praktisch nachgewiesen.
- Release-MD/APK-Paarung funktioniert.
- UPD-001: **BESTÄTIGT**.
- Nächster Arbeitsblock: BTB-SYNC-001 mit Pixel und Poco auf dem aktuellen Build erneut testen.


## BTB-SYNC-001 – 1.3.21 E2E fehlgeschlagen
- Update auf 1.3.21 / 200071 funktioniert und ist bestätigt.
- Fach-E2E bleibt jedoch fehlerhaft:
  - Pixel Haupt-TB: `BTB Abschluss` flackert weiterhin.
  - Poco HELPER: 26.09 erscheint kurz und verschwindet sofort wieder.
- Damit ist `f57c532...` nicht ausreichend.
- Nächster Android-Diagnoseblock: `BtbServerStateStore` Persistenz/Keys, lokale/serverseitige BTB-ID-Zuordnung, Pointer-Schreiber und Lifecycle-Watcher.
- Keine App-Daten löschen, keine Neuaktivierung, kein NAS-/Server-Umbau.


## BTB-SYNC-001 – zweiter konkurrierender Guard gefunden
- 1.3.21-Gerätetest blieb fehlerhaft.
- Quellprüfung hat den bisher übersehenen `BtbPatch200033Installer.startActivePointerGuard()` bestätigt.
- Guard läuft alle 450 ms und löste bei stale Inaktiv-Marker den Pointer, ohne den ServerState zu berücksichtigen.
- Parallel überschreibt Patch200033 aufgrund bisheriger Installationsreihenfolge den modernen Abschlusslistener.
- Fixcommit: `4f0e33a8060d0d98279cfe96aed280fee5c8adcb`.
- Neuer Zielstand: **1.3.22 / 200072**.
- Source-Check passt; Build/Installation/Praxis-E2E offen.
- Nichts am Poco löschen, nicht neu aktivieren, nichts am NAS ändern.


## BTB-SYNC-002 – 1.3.23 Source-Fix gesichert
- Benutzerkorrektur übernommen: Ursache ist Android-Reihenfolge, nicht Server/DB-Vertrag.
- P0-Fehler: lokaler Pixel-Stand konnte vor Outbox-Export durch Server-Snapshot überschrieben werden.
- Projektsteuerung vor Codeänderung aktualisiert/committed.
- Android-Fix:
  - Dirty-BTB zuerst `exportBtbChange`;
  - kein Server-Snapshot-Apply vor Outbox;
  - Pending/Dirty sperrt `applyServerStateToLocal`;
  - lokaler Save + Dirty-Markierung und Server-Apply pro BTB serialisiert;
  - neue Dirty-Generation während Upload bleibt erhalten und erzwingt Retry.
- Version angehoben auf **1.3.23 / 200073**.
- Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.23_200073.md`.
- Kein `server/**` geändert.
- PORTAL-LIVE-001 bleibt SERVER-seitig: Source-Fix vorhanden, Deploy/E2E offen; bis Deployment kann F5 auf dem NAS weiter nötig sein.
- Build konnte in diesem Arbeitsblock nicht ausgeführt werden: GitHub Actions erzeugte für die aktuellen PR-Commits keinen Run; Container hat keinen externen GitHub-Netzzugriff.
- Daher Status sauber getrennt: **SOURCE-FIX UMGESETZT / BUILD OFFEN / INSTALLATION OFFEN / E2E OFFEN**.
- Nächster Test nach Build: Pixel `ABC` → Portal `ABC` → Portal `XYZ` → Pixel `XYZ`; Parallel-Autosave darf nicht verschwinden. Danach Poco.


## BTB-SYNC-002 – bidirektionaler Basistest 1.3.23
- Benutzer bestätigt den gerade getesteten BTB-Sync: **funktioniert in beide Richtungen**.
- Pixel → Portal: **BESTANDEN**.
- Portal → Pixel: **BESTANDEN**.
- Damit ist BTB-SYNC-002 für den normalen bidirektionalen Pfad praktisch bestätigt.
- Noch nicht als bestätigt markieren: gezielter Parallel-Autosave-/Conflict-Fall während laufendem Upload.
- Poco/HELPER bleibt separater E2E.
- Server/API/DB unverändert; PORTAL-LIVE-001 bleibt weiterhin eigener SERVER-Deploy/E2E-Punkt.


## BTB-UI-001 – neuer Praxisbefund
- Pixel ist Haupt-TB; Poco/Helfer ist aus der aktiven Helferrolle entlassen.
- Screenshot zeigt Wechsel zwischen:
  - nur `BTB Abschluss`;
  - `PDF Entwurf` + `BTB Abschluss`.
- Gewünschter Haupt-TB-Zustand ist stabil **PDF Entwurf + BTB Abschluss**.
- Quellursache im `BtbModernLifecycleInstaller` nachvollziehbar: pauschal beide Buttons VISIBLE, danach Haupt-TB-Zweig `btnPdf=GONE`.
- Vor Codeänderung dokumentiert. Nächster Zielbuild 1.3.24 / 200074.


## BTB-UI-001 – Source-Fix gesichert
- Praxisbefund: Pixel Haupt-TB wechselte unten zwischen 1 und 2 Buttons.
- Quellursache: Lifecycle-Watcher setzte zunächst beide Buttons sichtbar und blendete im Haupt-TB-Zweig `PDF Entwurf` wieder aus.
- Fixcommit `7fdb4dad2e72515dcda958c00bc2065c38d9f5da`.
- Versions-/Releasecommit `446312c8c09a99ed71950fe3b08adc53070ddc91`.
- Neuer Zielstand **1.3.24 / 200074**.
- Haupt-TB: stabil `PDF Entwurf` + `BTB Abschluss`.
- HELPER: kein PDF-Entwurf; rollenbezogene Schichtaktion.
- Source-Check passt; Build/Installation/E2E offen.


## BTB-UI-001 bestätigt 1.3.24
- Benutzer bestätigt: Pixel springt unten nicht mehr.
- Haupt-TB zeigt stabil PDF Entwurf + BTB Abschluss.
- Punkt geschützt: nur bei neuem reproduzierbaren Fehler wieder öffnen.
- Poco/HELPER-E2E bleibt offen.


## BTB-SYNC-002 – vollständig bestätigt
- Benutzer bestätigt auch den zuvor noch offenen Parallel-Autosave-/Conflict-Test.
- Pixel → Portal: BESTANDEN.
- Portal → Pixel: BESTANDEN.
- neuerer lokaler Pixel-Stand während laufendem Upload bleibt erhalten: BESTANDEN.
- BTB-SYNC-002 ist damit **BESTÄTIGT** und geschützt.
- Nächster Geräteblock: Poco/HELPER-E2E.


## BTB-EDIT-001 – Diagnosecheckpoint
- Anforderung vor Implementierung in Projektsteuerung gesichert: `fb790f782e9097314d6c5f41d75c10c3bdbb1c6b`.
- Quellaudit bestätigt: `@ORT=<Long>`/Foto-`ortId` sind lokale Identitäten; Punkt 4–8 verlieren den Ort beim aktuellen `SectionItem`-Build.
- stabiles Worksite-Node-Binding mit `clientNodeKey` existiert bereits.
- stabiles Entry-Slot-Konzept existiert bereits über `BtbEntrySlotStore`.
- Android-Zielmodell festgelegt: `clientEntryKey + firmaId/firmaName + worksiteNodeKey + Zeit/KM/Text`.
- Server-HEAD `cd9b18fc062a7a3d4354c611a949ed3d8408d709` hat den zuvor fehlenden section-PATCH und BTB-Foto-Metadaten-PATCH inzwischen implementiert.
- Nächster Block: Android-Modell/Serializer/Server1BtbApi/Editor/Foto-Metadaten gezielt anpassen; kein Delete/Create.


## PORTAL-WEATHER-001 – Diagnosecheckpoint
- Android-Serializer trägt `tempC` und `niederschlagMm`.
- konkreter Android-Bug: Auto-Wetter macht aus bekanntem `0.0 mm` ein `null`.
- tempC-Verlust im Portal ist noch nicht source-seitig lokalisiert.
- Wetter bleibt separater Block, nicht Teil von BTB-EDIT-001.


## Buildauftrag 1.3.25 / 200075
- Benutzer: **bauen**.
- Neuer Build muss höher als 200074 sein; daher **1.3.25 / 200075**.
- Vor Build Source-Check der BTB-EDIT-001-Änderungen.
- Release-MD zwingend gemeinsam mit APK.
- Noch kein Buildstatus behaupten, bis CI/Gradle tatsächlich erfolgreich durchgelaufen ist.


## AND-BUILD-200075 – CI-Buildversuch
Status: **BUILD NICHT GESTARTET / CI-RUNNER NICHT ZUGEWIESEN**

- Ziel: **1.3.25 / 200075**.
- Temporärer mergebarer CI-PR #10 wurde ausschließlich für den echten Android-CI-Build angelegt.
- Android-CI Run: `36252246995`.
- Versuch 1 und Wiederholungsversuch 2 endeten jeweils nach ca. 3 Sekunden mit `failure`.
- Der Job `build` erhielt **keinen Runner** (`runner_name = ""`) und **keine Steps wurden gestartet** (`steps = []`).
- Damit wurden weder Checkout noch Source-Checks, Gradle, Tests, APK-Build oder Lint ausgeführt.
- Bewertung: **kein Android-Compilefehler nachgewiesen**; GitHub-Actions-Infrastruktur hat den Job vor Step 1 nicht gestartet.
- Deshalb Status weiterhin sauber getrennt: QUELLSTAND/VERSION VORBEREITET, BUILD OFFEN, INSTALLATION OFFEN, E2E OFFEN.
- Nächster valider Buildweg: Android Studio/lokaler Gradle-Build `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` oder funktionierender GitHub-Runner.


## AND-BUILD-200075 – lokaler Buildfehler generateBtb200043Sources
Status: **REPRODUZIERT / URSACHE EINGEGRENZT**

- Benutzer-Build in Android Studio am 26.09.2026 scheitert in `:app:generateBtb200043Sources`.
- Fehlermeldung: `Android 200047: Hauptbaustellen-Ortsmarker-Anker wurde nicht gefunden`.
- Damit ist jetzt erstmals ein echter lokaler Buildfehler für 1.3.25 / 200075 nachgewiesen.
- Ursache liegt **nicht** in Server/API/DB, sondern im Android-Buildgenerator `GenerateBtb200043Sources` in `app/build.gradle.kts`.
- Der Generator sucht noch nach dem alten 200047-Quellanker `if (effectiveOrtId != 0L) ...`; BTB-EDIT-001 hat den kanonischen Editor inzwischen auf `clientEntryKey/worksiteNodeKey` und eine neue `buildManualLine`-/Parserstruktur umgestellt.
- Fixziel: veralteten Build-time-Patch 200047 nicht mehr gegen den neuen Quelltext laufen lassen; die notwendige Hauptbaustellen-/Legacy-Ortsmarker-Semantik direkt im kanonischen `BautagebuchFragment.kt` erhalten.
- Version bleibt **1.3.25 / 200075**, weil der Build nicht erfolgreich erzeugt/installiert wurde.


## AND-BUILD-200075 – Sourcegenerator-Fix
Status: **SOURCE-FIX UMGESETZT / SOURCE-CHECK PASST / BUILD ERNEUT OFFEN**

- Fehler aus dem lokalen Android-Studio-Build war reproduzierbar: `:app:generateBtb200043Sources` brach am alten Android-200047-Ortsmarker-Anker ab.
- Fixcommit: `606aa3ad1f8ea438372484b056a0fc1264ae2aac`.
- Der veraltete Android-200047-Build-time-Patch wurde aus `GenerateBtb200043Sources` entfernt.
- Ortsmarker-Semantik liegt jetzt direkt im kanonischen `BautagebuchFragment.kt`:
  - jede moderne Zeile schreibt explizit `@ORT=<lokale UI-ID>`, Hauptbaustelle also `@ORT=0`;
  - `@NODE=<clientNodeKey>` bleibt die geräteübergreifende Sync-Identität;
  - Inline-`@ORT` wirkt nur auf die aktuelle Zeile;
  - alte Standalone-`@ORT`-Gruppen bleiben kompatibel.
- Source-Check bestätigt: alter Fehlertext/alte Patch-Anker sind aus `app/build.gradle.kts` entfernt; 1.3.25 / 200075 bleibt unverändert.
- **Noch kein neuer Buildnachweis.** Nächster Schritt: Git Pull → Android-Studio-Build 200075 erneut starten.

## Checkpoint 27.09.2026 – Integrationsaudit Bilder / BTB-Abschluss / Wetter
- Neue Anforderungen vor Prüfung in 02/05/07 gesichert.
- Foto-Ist: Android lädt lokales Original hoch; Server speichert es unverändert. Server hat `preview` 1280/JPEG82 und `thumb` 320/JPEG72; Android lädt bislang `/content`. Android-Fix nötig.
- BTB-Finalisierung: Android und Portal benutzen denselben Server-Endpunkt. Server FINALIZED + active_btb_id=NULL + BTB/FILE-Change; Portal SSE und Android Delta übernehmen. Source-Pfad beidseitig passend, gezielter Finalize-E2E offen.
- Wetter: Android/Server/Portal führen `tempC` und `niederschlagMm`. Portal zeigt 0 mm korrekt. Android macht automatisches 0.0 mm an zwei Stellen zu null und zeigt mm in UI/Android-PDF nicht vollständig. Android-Fix nötig.
- Kein `app/**`- oder `server/**`-Code in diesem Audit geändert.
- Android Build 1.3.25/200075 bleibt **OFFEN**.

## Checkpoint 27.09.2026 – Android 1.3.26 / 200076
- Anforderungen Bilder/BTB-Abschluss/Wetter vor Implementierung in Projektsteuerung gesichert.
- Android-Fachcode umgesetzt:
  - kleine Sync-JPEGs max. 1920 px / Qualität 82; Handyoriginal unverändert;
  - BTB-, Mängel-, Abnahmebilder + bildbasierte Abnahme-Anlagen klein zum Server;
  - mobile Serverbilder über `preview`, nicht automatisches `/content`;
  - BTB-Foto-Link-Metadaten per versioniertem PATCH;
  - 0,0-mm-Wetterwert erhalten und mm in App/PDF/Druckmodell sichtbar.
- BTB-Finalize-Lifecycle **nicht verändert**; Android/Portal verwenden weiterhin denselben Server-Endpunkt.
- Version: **1.3.26 / 200076**, Release-MD vorhanden.
- Sourcechecks: gezielte Prüfungen PASS; Generatoranker 14/14 PASS; Diff ohne `server/**`.
- Fachcode-HEAD vor Statuscommits: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`.
- Build: **OFFEN**; kein Workflow-Run für diesen Code-HEAD, lokaler Checkout/Gradle hier nicht verfügbar.
- ADB/Emulator: nicht verfügbar; kein realer Android-Test.
- Nächster praktischer Schritt: Git Pull → Android Studio Build 200076 → Pixel → E2E Foto/Wetter/Finalize → Poco.


## Abschluss Arbeitsblock 27.09.2026 – 1.3.26 / 200076
- Android-Fachcode für kleinen Bildsync, mobile preview-Renditions, BTB-Foto-PATCH und Wetter-mm umgesetzt.
- Kleine Bilduploads gelten jetzt auch für Mängel-/Abnahmefotos und bildbasierte Abnahme-Anlagen; PDF-/Nichtbild-Anlagen bleiben unverändert.
- BTB-Finalize-Lifecycle wurde nicht geändert.
- Quellprüfung: gezielte Sourcechecks PASS; Generatoranker 14/14 PASS; Diff ohne `server/**`.
- Fachcode-HEAD: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`.
- Projektsteuerungs-Kopfstände 01/03/06/07 auf 27.09. und Zielbuild 1.3.26/200076 bereinigt; alte bestätigte Punkte werden nicht mehr als offen geführt.
- PORTAL-LIVE-001, BTB-SYNC-002, BTB-UI-001 und UPD-001 bleiben **BESTÄTIGT/geschützt**.
- Build 200076: **OFFEN**. Kein Workflow-Run für den Fachcode-HEAD; in dieser Chatumgebung kein lokaler Git-Checkout/Gradle und kein ADB/Emulator.
- Installation: **OFFEN**.
- E2E: **OFFEN** für Bildgrößen/Renditions, Foto-PATCH, Wetter 0,0 mm/tempC, Finalize beide Richtungen und Poco/HELPER.
- Nächster praktischer Schritt: Git Pull → Android Studio Build 200076 → Pixel installieren → E2E-Matrix → Poco.

## Checkpoint 27.09.2026 – Build 200076 Locale-Fix
- Benutzer-Build in Android Studio: `:app:compileDebugKotlin` fehlgeschlagen, `Unresolved reference 'Locale'`.
- Ursache: `BautagebuchFragment.kt` nutzte `Locale.GERMANY` ohne `java.util.Locale`-Import.
- Neue Fehlerfeststellung vor Codeänderung in Projektsteuerung gesichert.
- Fixcommit: `199df4b149e210194b22b1c84cc1700ac54435c2`.
- Codeänderung: genau ein Import; keine Fachlogik, kein Server, kein Finalize-/Sync-Umbau.
- Generatoranker nach Fix 14/14 PASS.
- Version bleibt **1.3.26 / 200076**.
- Buildstatus: **ERNEUT OFFEN**; keine Installation/E2E-Bestätigung.
- Nächster Schritt lokal: Git Pull → Build 200076 erneut.


## Checkpoint 27.09.2026 – AND-VER-001 korrigiert
- Die sichtbare Versions-/Buildzeile war eine nicht beauftragte Assistentenänderung.
- Benutzer hat ausdrücklich entschieden, dass diese Anzeige **nicht** gewünscht ist.
- Startseitenzeile und die damit verbundene Formatänderung wurden vollständig zurückgenommen.
- Technische Version bleibt **1.3.26 / 200076**; Build 200076 bleibt nach Locale-Fix erneut zu bauen.


## Checkpoint 27.09.2026 – Branchbereinigung
- Benutzerziel: Branchliste auf `main`, `feature/tb-android-2.0`, `feature/tb-server-1.0` reduzieren.
- Sicherheitsvergleich gegen aktive Android-/Serverbranches durchgeführt.
- PR #1 und #5 geschlossen.
- Server-Draft-PR #6 auf Basis `main` umgestellt; bleibt offen.
- Altbranches sind fachlich überholt; `feature/device-activation` und `kilometrierung-v1` haben eigene historische Commits, ihre Funktionen sind im aktuellen Projektstand weiterentwickelt/ersetzt.
- Branch-Refs konnten über die verfügbare GitHub-Schnittstelle nicht direkt gelöscht werden; manuelle Löschung der sieben dokumentierten Altbranches bleibt offen.
- Keine Android-/Server-Fachcodeänderung.


## Android-/Server-Abgleich 20.10.2026 – AND-SYNC-AUDIT-20261020
Status: **QUELLABGLEICH ABGESCHLOSSEN / IMPLEMENTIERUNG OFFEN / BUILD UND GERÄTE-E2E OFFEN**.

Auftrag: nach Statusauskunft vom Nutzer mit „okay“ bestätigter gezielter Vertragsabgleich; keine pauschale Implementierungs- oder Buildfreigabe daraus ableiten.
Geprüfte Ausgangsstände: ANDROID `6cec34423e34315c581b743668724abe17daa333`, SERVER `0f82b42da7412a138bf99b6f80b93031e3b5373e`. PR11 offen, kein Draft, PR6 offen/Draft; beide melden mergeable=false. Technische Androidversion unverändert 1.3.26/200076. Keine zusätzliche Versionszeile auf der Startseite.

| Priorität | Befund / Quellbeleg | Nächster Androidblock |
|---|---|---|
| P0 | Server files.py: PHOTO_MAX_BYTES=2_000_000. MobileImageSync.kt: 1920px/JPEG82, aber keine Byteobergrenze; auch Cachetreffer nur auf Länge>0 geprüft. Damit fehlt eine garantierte Einhaltung, kein konkreter Live-Uploadfehler behauptet. | Wiederholte Komprimierung/Verkleinerung mit Prüfung <=2.000.000Bytes, Cache ebenfalls prüfen; kein Originalfallback; Original erhalten. |
| P1 | SectionItem in BautagebuchModels.kt und Serializer/Parser in BautagebuchSpeicher.kt besitzen uhrzeit, aber kein uhrzeitBis. Neue Portal-Endzeiten werden dadurch nicht vollständig lokal modelliert/gespeichert. | Ende additiv durch Modell, Speicher, Editor, Sync und PDF führen; Alt-Einzelzeiten erhalten; Rundlauf testen. |
| P1 | Firmenzeitbindungen personalZeitbindung/maschinenZeitbindung fehlen im geprüften Modell/Speicher. Server führt REPORT/MANUAL ein. | Feld-Erhalt und automatische Zeitübernahme einschließlich manueller Ausnahmen gezielt prüfen/ergänzen; kein stilles Überschreiben. |
| P1 | Server UploadComplete hat btb_entry_id; Android Server1FileApi.uploadLinkedFile hat dieses Feld nicht. BtbTeamMediaSync hat keine btbEntryId-Behandlung. Serververtrag PORTAL-017: gebundener Fotokontext folgt dem Eintrag. | Stabile Server-Entry-ID gegen vorhandenen clientEntryKey/Slot abbilden; Foto-Bindung und Entkopplung umsetzen, keine Textheuristik. |
| P1 | FotoEntity hat keine GPS-Genauigkeit, BtbTeamMediaSync sendet gpsAccuracyM=null. Download mergedPhotoMetadata überspringt explizite null-Werte und ergänzt Legacy; Import ersetzt fehlende Koordinaten durch 0.0 und fehlende Zeit notfalls durch jetzt. Das passt nicht zuverlässig zu explizitem GPS-Löschen/fehlenden Metadaten des neuen Vertrags. | Effektive BTB-Link-GPS inklusive null-Semantik, Aufnahmezeit und Genauigkeit prüfen und verlustfrei modellieren; keine erfundenen Aufnahmedaten. Room-Migration erst nach gesondertem Implementierungsblock. |
| P2 | Nachtschicht-Grundlage bereits vorhanden: endeDatumIso samt abgeleitetem Enddatum im Modell; Server1BtbApi übernimmt Datum aus ended_at und sendet endeDatumIso zurück. | Kein kompletter Neubau. Portal->Android->Portal/PDF über Mitternacht, Monats-/Jahreswechsel und Zeitzone testen. |
| P2 | SyncCatalogApi nutzt GET /api/v1/catalog; in diesem Client keine Anbindung an /catalog/lookup oder /catalog/inventory. WorksiteNodeApi transportiert generisches payload und track_or_switch. | Neue Bestandsübersicht/Objektart als eigener UI-/Vertragsblock; generischen Payloadtransport nicht pauschal als Datenverlust werten. |

Prüfumfang: Live-Einstieg/Projektregeln/Steuerungsdateien, aktueller Serververtrag einschließlich PORTAL-012/016/017/019/020/021, Android-Gitbaum, MobileImageSync, Server1BtbApi, Server1FileApi, BtbTeamMediaSync, CollabLocalBridge, FotoEntity, WorksiteNodeApi, WorksiteStructureSync, WorksiteTrackDataSync, SyncCatalogApi, BautagebuchModels/BautagebuchSpeicher sowie Server files.py.
Grenzen: reine Quellprüfung; kein Gradle, Unit-Test, Lint, Emulator, API-/DB-/NAS- oder Geräte-E2E. Keine neue Source-Test-PASS-Aussage. HEAD-Abfrage lieferte keine PR-getriggerten Workflow-Runs; das ist kein Nachweis über sämtliche historischen/push/manuellen Runs. Direkter Git-Transport hier 403, GitHub-Connector lesbar. Keine Runtime-/Server-/Produktivdatenänderung und keine Versionsanhebung.
Bestehende Bestätigungen BTB-SYNC-002, BTB-UI-001, UPD-001 und PORTAL-LIVE-001 bleiben geschützt. Alte September-Kopfstände sind historisch; dieser datierte Abgleich ersetzt keine Geräteabnahme.


## AND-IMG-2MB – Quellstand 20.10.2026
Status: **QUELLSTAND FERTIG / STATISCHE PRÜFUNG BESTANDEN / BUILD UND GERÄTE-E2E OFFEN**.
- Nutzerauftrag vor Implementierung gesichert: 7020891d4c26fb15d7d2ef5bdc41c408f68d81e0.
- MobileImageSync: 1..2.000.000Bytes als Cache-/Ausgabebedingung; JPEG82/75/68/60, bei Bedarf weitere Reduktion der Kante um20%, nie Hochskalierung. Bestehende1920px-Grenze und EXIF-Ausrichtung erhalten.
- Vorbereitung synchronisiert gegen gleichzeitige Schreiber derselben Cache-/Tempdatei. Tempdatei im finally entfernt; unvollständiger Zielcache nach Publikationsfehler gelöscht. Aufnahmedatei bleibt unverändert, kein Originalfallback.
- Aufrufer geprüft: BtbTeamMediaSync sowie CollabLocalBridge für Mangel-/Abnahmefoto und bildbasierte Abnahmeanlage verwenden gemeinsamen prepare-Pfad und brechen bei null mit Fehler ab. PDF-/Nichtbildanlagen unverändert.
- Context7: offizielle Android-Bitmap-Referenz zu JPEG-Qualität, compress-Rückgabewert und createScaledBitmap-Identität geprüft.
- Ausgeführt: neun statische Schutzprüfungen PASS; Dimensionsfortschritt für jede Kante1..1920 geprüft (max29 Reduktionsschritte). Dies ist keine Kotlin-Kompilierung und kein Bitmap-/Android-Laufzeittest.
- Umgebung: Java vorhanden, kein kotlinc/Gradle/Android-SDK/adb gefunden, direkter Git-Transport403; kein Build, Unit-Test, Lint oder Emulatornachweis. Kein APK erzeugt; Version bleibt1.3.26/200076. Vor nächster auslieferbarer APK neuer höherer versionCode und passende Release-MD erforderlich.
- Nächste praktische Prüfung: detailreiches/großes Bild, kleineres Bild, exakt2.000.000Bytes/Übergröße im Cache, EXIF-Drehung, Fehler bei Dateischreiben und erneuter Versuch, Originalhash unverändert; dann Upload für BTB/Mangel/Abnahme zum aktuellen Server. Aufnahme-/Metadatenverträge und bestätigte Sync-/Lifecycle-Pfade unverändert.
- Keine server/**-Änderung, keine produktiven Datenänderungen, keine Actions gestartet ([skip ci]).


## AND-ENTRY-END – Quellstand 20.10.2026
Status: **QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / KOTLIN-UNIT-TESTS ANGELEGT, NICHT AUSGEFÜHRT / BUILD UND E2E OFFEN**.

- Auftrag vor Implementierung: c42bc151817f431352e98029ba907950feb1af97; Schutzentscheidung zu Roh-JSON/PDF-Hash: fc98a8e88de51d95343878de2e8220777757906f. Ausgangscode fcd2985a84a3d51fe44331437ed428223fba327a; Server unverändert0f82b42da7412a138bf99b6f80b93031e3b5373e.
- SectionItem hat additives nullable uhrzeitBis am Ende der Parameterliste; Beginn bleibt uhrzeit. BtbEintragszeit liest getrennte und historische zusammengefasste Zeiten; gleicher Beginn/Ende wird nicht als24h interpretiert, Nachtspanne nicht umsortiert. Auch fehlender Beginn bei vorhandenem Ende bleibt transportierbar.
- BautagebuchSpeicher liest/schreibt Endzeit und unterscheidet fehlendes Ende von explizitem null. Bei semantisch unveränderten Zeiten bleibt vorhandene lokale Zeit-JSON-Form bestehen, damit allein die Formatmigration keinen alten Abschluss-Hash ändert. Zuordnung über stabilen clientEntryKey, ID-loser Legacy-Fallback nur gleicher Abschnitt/Index und Text/Firma/Node/KM. Gleicher Schutz direkt vor Server-Snapshot-Write innerhalb des bestehenden BTB-Mutationslocks. Echte Zeitänderungen bleiben unverändert als Änderung erkennbar; Hashalgorithmus/Originaldateien/Dirty-Reihenfolge nicht verändert.
- Server1BtbApi normalisiert ausgehende Abschnittspayloads für Punkt4–8 auf uhrzeit/uhrzeitBis; fehlendes gewünschtes Ende explizit JSONObject.NULL, damit bewusstes Löschen durch den Altclient-Feldschutz nicht unterdrückt wird. Bestehende Entry-ID/Slot/Node/Firma bleiben erhalten.
- Bestehender Zeitspannen-Editor wird weiterverwendet; Anzeige, PDF einschließlich Originalvergleich, Druckmodell, Text-/ZIP-Export verwenden gemeinsame Zeitdarstellung.
- Tatsächlich ausgeführt:14/14 Buildgenerator-Anker PASS;14 Fälle mit den direkt aus den produktiven Kotlin-Editoren extrahierten Java-Regexen auf JVM17 PASS (Einzelzeit, Spanne, Nacht, Legacy-Klammern, nur Ende, gleiche Zeiten, Text ohne Zeit). Statische Verdrahtung für alle fünf Abschnitte, explizites null und Save-/Apply-Schutz PASS.
- Sechs JUnit-Regressionsfälle in BtbEintragszeitTest.kt ergänzt (Legacy/modern, explizites null, Vorrang, Nacht/gleich, leer/Einzelzeit/nur Ende, Identität). **Nicht ausgeführt**: Kotlin-/JUnit-Lauf, Gradle-Build, Lint, Android-UI, Original-/Abschluss-PDF-Runtime, API-/NAS-/Pixel/Poco-E2E. Java-Regexprüfung ist kein Kotlin-/Android-Build. Kontext7-Abfrage zu LocalTime ohne Treffer; keine neue Bibliothek eingeführt.
- Nächste Prüfung bei verfügbarer Buildumgebung: Unit-Tests und Gradle/Lint, danach Portal-Endzeit->Android öffnen/ohne Änderung speichern->Portal/PDF; Ende ändern und löschen;22:00–06:00; bestehendes Abschluss-PDF bei reinem Formatübergang erhalten, echte Änderung ungültig/rot, Foto-/Entry-Identität unverändert; Pixel und Poco.
- Keine server/**-Datei, kein APK, keine Versionsanhebung; technische Version weiterhin1.3.26/200076. Nächste auslieferbare APK benötigt höheren Build und passende Release-MD. Keine Actions gestartet ([skip ci]).
- Noch offen: Firmenzeitbindung REPORT/MANUAL, Foto-Eintragsbindung/GPS, Katalog-Bestandsübersicht, gemeinsame Geräte-E2Es. Vorheriger AND-IMG-2MB-Block source-seitig gesichert; Bitmap-/Upload-E2E bleibt offen.


## AND-FIRM-TIME – Quellstand 20.10.2026
Status: **QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / UNIT-TESTS ANGELEGT, NICHT AUSGEFÜHRT / BUILD UND E2E OFFEN**.
- Auftrag38d19fb011fd0e04b17f973e96e468708a955c55, ergänzende Schutzentscheidung e5a2195ca9a05cddf6a8291184bc78c9713056d9. Ausgangs-Android841d18c0d3cdc9b96860270f05a10161cdbd7987; Server0f82b42da7412a138bf99b6f80b93031e3b5373e unverändert.
- FirmaBlock/Speicher erhalten personalZeitbindung und maschinenZeitbindung unabhängig. Null-Zeitwerte werden als null statt Zeichenfolge gelesen. Bestehender generischer Firmenpayload in Server1BtbApi transportiert Flags in beide Richtungen; kein neuer Serverendpunkt.
- FirmenZeitbindung: REPORT folgt Bericht, MANUAL bleibt bestehen; ohne Flag nur leere bzw. je vorhandenem Wert passende bisherige Berichtzeiten automatisch. Bekannte fremde/individuelle Zeiten und unbekannte zukünftige Kennzeichen werden konservativ erhalten. Nacht wird nicht umsortiert. Unveränderte Werte erhalten ihr Legacy-Flagformat.
- Beide PM-Einstiege erhalten geladenen Modus und leere Werte; neue Firmen starten mit Berichtzeiten statt Jetzt/+10h. Bewusste Firmenzeitwahl ergibt REPORT bei exakt dem Berichtspaar, sonst MANUAL. Laden/programmgesteuertes Nachführen unter pmApplying/pmSyncing-Guard, kein versehentliches Umstellen von MANUAL.
- Datum-/Schicht-Dialog führt automatische Firmenzeiten nach, auch beim Entfernen der Berichtzeiten und nach Ziel-Schicht-Laden. Vorhandener Autosave speichert Bericht+Firmenstand zusammen. Vortags-/Neuberichtsübernahme übernimmt passende automatische Zeiten, bewahrt manuelle Ausnahmen. AutoLinked-Maschinenplatzhalter bleiben ohne eigenen Inhalt leer.
- PM-Sammlung bewahrt vorhandene geladene Firma-ID sowie Teilsektionskennzeichen; Namens-Zusammenführung und bisherige ID-Regel für neue Firmen bleiben erhalten. Reine äquivalente Flag-Nachlieferung bei identischem Zeitpaar bewahrt bestehende JSON-Form (lokaler Save und Snapshot-Write); expliziter MANUAL<->REPORT-Wechsel bleibt echte Entscheidung. Keine Änderung an Originaldateien, Hashalgorithmus, Finalize oder Dirty-Reihenfolge.
- Tatsächlich ausgeführt: gezielte statische Modell-/Speicher-/UI-/Sync-/Guard-/Identitätsprüfungen PASS; bestehende14/14 Generator-Anker PASS,14/14 produktive JVM-Regexprüfungen PASS. Acht JUnit-Fälle für REPORT/MANUAL/Legacy/Null/Nacht/Identität/Unbekannt angelegt, **nicht ausgeführt**. Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/Server-E2E. Context7 abgefragt; keine neue Bibliothek eingeführt.
- Praktische Folgetests: neuer Bericht07–16 mit Personal REPORT und Maschinen MANUAL; Bericht08–17 => nur Personal folgt. MANUAL mit zufällig gleichem alten Zeitpaar bleibt unverändert. Nacht22–06, Entfernen, Vortag, Portal->Android->Portal, Öffnen/Speichern ohne Änderung (Identität/Flags/PDF gültig), echte Änderung/Moduswechsel, Pixel/Poco. Kotlin-Tests/Gradle/Lint zuerst bei verfügbarer Toolchain.
- Keine server/**-Änderung, kein APK, keine Versionsanhebung, keine Actions gestartet ([skip ci]); Version bleibt1.3.26/200076. Für nächste auslieferbare APK höherer versionCode + passende Release-MD.
- Nächste offene Integration: Foto-Eintragsbindung/GPS; danach Katalogübersicht. Vorherige Bild-/Endzeitblöcke bleiben SOURCE-fertig mit offenem Build und Geräte-E2E.

## AND-PHOTO-LINK – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Fortsetzung: Foto-Block source-seitig gesichert, nächster fachlicher Block Katalog-Bestandsübersicht. Voraussetzungen vor Auslieferung: Kotlin-Tests/Gradle/Lint und gebündelte Geräte-/Server-E2Es aller bisherigen Blöcke. Kein APK erzeugt, Version weiterhin 1.3.26/200076; spätere APK nur mit höherem versionCode und passender Release-MD.
- Stabile clientEntryKey-Zuordnung statt Text-/GPS-Abgleich; bestehende und eigene Fotos übernehmen wirksame Servermetadaten über eine dauerhafte BTB-Korrekturschicht. GPS/Zeit/Genauigkeit können unbekannt bleiben. PDF, Detailansicht und Export verwenden effektive Werte. Originale bleiben erhalten.
- Bestätigte Bindungs-/Kontext-/Textänderungen werden nur bei unverändertem Pendingwert quittiert; spätere lokale Eingaben bleiben erhalten. Unbekannte Unterbaustellen werden nicht still auf Hauptbaustelle umgebogen. Read-only/Lifecycle-/Outbox-/Originalschutz bleibt auf bestehenden Pfaden.
- Grenzen: Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/Server-E2E-Nachweis. Kein APK/Versionssprung, keine Actions gestartet ([skip ci]), kein Servercode geändert. Technischer Stand 1.3.26/200076. Original-EXIF vor Galerie-Konvertierung gelesen; Context7 zu AndroidX ExifInterface ausgewertet, keine neue Bibliothek.

## AND-CATALOG-INVENTORY – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Katalog-Bestandsübersicht source-seitig implementiert und dokumentiert. Einstieg Katalog -> Strecke oder Bahnhof -> Vorhandene Daten anzeigen. Quell-/Servermapper-Doubles bestanden; acht Kotlin-Tests nicht ausgeführt. Kein APK und kein Android-Toolchain-/Emulator-/NAS-E2E-Nachweis. Nächster fachlicher Integrationspunkt: Android-Baustellenformular/Lookup, Objektart und freie Strecke gemäß PORTAL-021; danach gemeinsame Build-/Geräteabnahme aller bisherigen Blöcke.
- Bestand rein lesend aus gemeinsamem GLOBAL-Pool, eindeutige Kennungen, einzelne belastbare KM-Abschnitte; kein Zusammenziehen über Lücken. Fehler bleiben Fehler, kein falscher Leerbestand. Bahnhof ohne Kürzel wird nicht über Namen geraten. Keine Original-/Outbox-/Room-/Lifecycle-Fachänderung.
- Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/NAS-E2E-Lauf; adb/Gradle/Kotlin-Toolchain hier nicht vorhanden. Context7 zu AndroidX Lifecycle/DialogFragment ausgewertet. Keine neue Bibliothek, keine server/**-Änderung, keine Actions gestartet ([skip ci]), keine APK/Versionsänderung. Technischer Stand bleibt 1.3.26/200076; spätere APK braucht höheren Build und passende Release-MD.

## AND-CATALOG-FORM – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Neun Android-Dateien einschließlich neuer WorksiteObjectIdentity und zwölf Regressionstests implementieren explizite Bestandsübernahme/Objektart und freie Strecke ohne Bahnhof. Standard-Bestandsansicht bleibt read-only; Formularklicks schreiben nicht direkt auf Server, Speichern nutzt vorhandenen Repository-/Outboxpfad. Source-/JVM-Regex-/XML-Prüfungen bestanden, Kotlin-/Android-/NAS-E2E offen. Nächster Abnahmeschritt: gebündelter Android-Build/Unit/Lint und echte Pixel/Poco-/Servertests der bisherigen Integrationsblöcke.
- Stabile Knoten-/Objektidentität, keine Originaldatei-/Room-Schemaänderung, keine neue Bibliothek und kein Serverumbau. Keine automatische Bulk-Reparatur alter Baustellen. Vorhandene Fach-/Download-/Offlinepfade bleiben Grundlage.
- Kein Kotlin-/Gradle-/Lint-/Emulator-/Geräte-/NAS-E2E-Nachweis; diese Toolchain hier nicht verfügbar. Context7 zu FragmentResult/Lifecycle geprüft. Keine Actions gestartet ([skip ci]), keine APK und keine Versionsänderung. Technischer Stand weiterhin 1.3.26/200076; neue APK nur mit höherem Build und zugehöriger Release-MD.

## Checkpoint 22.10.2026 – BTB-AUTO-016
Benutzer bestätigt die gemeinsame 16-Stunden-Festlegung: Erfassung ab eingetragenem Beginn nach16h beenden; ausstehende Offline-Einträge/kleine Foto-Sync-Versionen sicher nachsynchronisieren; erst nach Vollständigkeitsbestätigung endgültiges PDF/Original. Details und offene technische Grenzfälle in `05_ANDROID_SERVER_VERTRAG.md`, Entwicklungsauftrag in `02_AKTIVER_AUFTRAG.md`.
- Prüfeinstieg: SERVER `0f82b42da7412a138bf99b6f80b93031e3b5373e` / PR6 Draft; ANDROID `a99e09474c09af33aaa30ee10712131216f44a15` / PR11 offen, kein Draft.
- Nur Dokumentation der bestätigten Anforderung. Implementierung, Source-Tests, Build, Installation, Deployment und NAS/Pixel/Poco-E2E dieser Automatik OFFEN. Keine bestehende Bestätigung aufgehoben. Keine Workflows manuell ausgelöst.


## AND-BUILD-BUNDLE – Zwischenstand 22.10.2026
- Technischer Testbuild1.3.27/200077 samt passender Release-MD vorbereitet. Keine APK erzeugt/übergeben. Kein neuer sichtbarer Versionsbanner.
- CI39714741475 prüft Code a99e09474c09af33aaa30ee10712131216f44a15: Release-Metadaten/JDK21/offizielle Wrapper-JAR PASS; Gradle Unit/Debug/Release noch IN BEARBEITUNG. Kein Kotlin-/Unit-/APK-/Lint-Erfolg bestätigt. Laufende Joblogs sind über Connector nicht abrufbar.
- Reale CI-Blocker korrigiert: unveränderte offizielle gradle/gradle@v9.3.1-Wrapper-JAR (validierte SHA256 b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13) und gradlew-Modus100755. Ein zusätzlicher transienter Wrapper-Netzwerkfehler wurde beim erneuten tatsächlichen Versuch überwunden.
- Alle13 Quellprüfungen ausgeführt:8 PASS,5 FAIL (13,19,83,85,86).13 erwartet alte writeText-Form; die neuen Formatbewahrungs-Schritte haben den danach vorhandenen Sync-Hook nicht entfernt.19 erwartet wörtliches requestMethod=DELETE; tatsächlicher Aufruf reicht DELETE an requestMethod=method weiter.83/85/86 verlangen teils alte Versionen/Server-/UI-Texte. Diese Prüfungen nicht als PASS umgewertet, Gesamt-CI rot. Keine server/**-Änderung.
- Parallel hinzugekommene BTB-AUTO-016-Dokumentation aus fc9e07861a99f0cafc5b80ed8e0c937998d6c37a erhalten. Diese neue 16-Stunden-Automatik ist weiterhin NICHT implementiert und nicht Teil dieses Testbuild-Codes.
- Lauf: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39714741475 . Abschlussprotokoll und danach Geräte-/NAS-E2E offen.


## AND-BUILD-BUNDLE – echter Unit-/Debug-Nachweis 22.10.2026
- CI39716736906, Code36bebf0a24078f4a81313fe41d5b5f90ba48e9fb, Testversion1.3.27/200077: `:app:testDebugUnitTest :app:assembleDebug` am22.10.2026 03:35:39UTC erfolgreich beendet (Start03:26:31UTC). Kotlin-Kompilierung, Unit-Test-Task und Debug-Assemblierung damit tatsächlich PASS.
- Offizielle Wrapper-Validierung und Gradle-Bootstrap ebenfalls PASS. Der vorherige kombinierte Variantenlauf39714741475 wurde durch CI-Concurrency abgebrochen; daraus kein Compilerfehler abgeleitet. Aktueller Ablauf baut Debug und Release nacheinander.
- Release-Build IN BEARBEITUNG; Lint und Artefakt-Upload noch offen. Keine APK übergeben, Signatur/Updatefähigkeit und Installation/NAS-/Pixel-/Poco-E2E weiterhin ungeprüft.
- Separater Quellvertragsjob weiterhin FAIL (8PASS/5FAIL); Gesamtlauf deshalb nicht grün. Keine server/**-Änderung. BTB-AUTO-016 bleibt dokumentiert, aber nicht implementiert.
- Nachverfolgung: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39716736906


## AND-BUILD-BUNDLE – Abschluss echter CI-Build 22.10.2026
- Testversion1.3.27/200077, geprüfter Code36bebf0a24078f4a81313fe41d5b5f90ba48e9fb. Buildjob119620793503 in Lauf39716736906 abgeschlossen: SUCCESS.
- `:app:testDebugUnitTest :app:assembleDebug` PASS (9m7s), `:app:assembleRelease` PASS (7m57s), `:app:lintDebug` PASS (1m40s). Kotlin-Haupt-/Testquellen tatsächlich kompiliert; keine reine Source-Prüfung. Einzeltestanzahl nicht archiviert, daher keine Anzahl behauptet. Compilerwarnungen vorhanden, kein fehlerfreier Warnungsstatus behauptet.
- Artefakt12400166655 `TB-Helfer-1.3.27-200077`, ZIP35111189Bytes, SHA256dd1b090d67d6ab551e81d4f4da5fa87572d78b0514800cbd74cd490112dee2f4. Original enthält APKs + damalige Release-MD; deren vorbereitender Teststatus durch diesen Nachweis überholt. Aktualisierte Release-MD im Repository und im gesonderten Debug-Testpaket.
- APK-Inhalte geprüft: Debug26566977Bytes, SHA256205ccbb826464a20ec7c3b72999c49546e24c4ca19c3d42d5506b216569169c4, Zertifikatsidentität Android Debug; Release11992339Bytes, SHA256ea53d3d90f45a3afd4a2cda26293f514dfb3a629f2068522efb58c23ac9dc96f, ausdrücklich UNSIGNIERT. Beide Manifestwerte de.tbhelfer /1.3.27/200077 bestätigt. Signaturblöcke/Zertifikat untersucht, keine Geräteinstallation oder vollständige apksigner-Verifikation möglich.
- Quellvertragsjob119620794013 weiterhin FAIL:8PASS/5FAIL (13,19,83,85,86). Gesamtlauf ROT, keine Releasefreigabe. Keine server/**-Änderung; bestehende historische Marker-/Versionsabweichungen bleiben offen.
- Debug-Testpaket enthält nur Debug-APK, passende aktualisierte Release-MD und Testhinweise. Debug-Zertifikat kann mit installierter App inkompatibel sein; kein Datenlöschen/Deinstallieren empfohlen. Pixel/Poco-/NAS-/Portal-E2E und Upgradefähigkeit OFFEN. BTB-AUTO-016 nicht implementiert, nicht Teil dieses Builds.
- Lauf/Artefakt: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39716736906 ; https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39716736906/artifacts/12400166655 (GitHub-Artefaktaufbewahrung bis05.11.2026).


## AND-AUDIT-023 – Übergabe Quellstand 23.10.2026
- 1.3.28/200078 für lokalen Android-Studio-Build vorbereitet; Übergabe über GitHub Desktop. Keine APK erzeugt, kein CI-Lauf ausgelöst ([skip ci]).
- Behoben im Quellstand: verdeckte Cache-/Abruffehler und falscher Nullbestand; ungültige Katalogantworten; pauschale 403->Tokenverlust-Zweige in Geräte-/Team-/Katalog-/Profil-/Legacy-Clients; nicht hochgeladene Helferänderungen bei FINISHED/NOT_PARTICIPATING werden nicht mehr als Applied quittiert.
- Ergänzt: reine Strecke-/Bahnhof-Serversuche über vorhandenes lookup/inventory; gegliederte direkte Einstellungen; Fotospeicherprüfung ohne Lösch-/Verschiebeoperationen; ungenutzten BtbEinzelZip-Code entfernt. Produktive Quellgenerator-/Patch-/Altformatpfade und Nutzerdaten behalten.
- Gesamtinventur main:281 Kotlin-/108 XML-Dateien im Ausgangsstand. XML-Parsing, geänderte Ressourcenverweise, Navigationszielklassen, lexikalische Kotlin-Struktur und gezielte Schutz-/Quellflussprüfungen PASS. Diese Checks ersetzen keinen Kotlin-/Gradle-Build. Vier neue DeviceAuthorizationTest-Methoden vorbereitet, nicht ausgeführt.
- OFFEN: lokaler Build/Unit/Lint und Pixel/Poco-/NAS-/Portal-E2E. Geräteaktivierung, Portal-Logout, Einladungen, Helfer-Schichtende/-fortsetzung und weiterer TB nach Prüfmatrix praktisch abnehmen. Die konkrete Ursache des Screenshot-Abruffehlers ist ohne Runtimefehler nicht bestätigt.
- Bedingte Gegenstellenlücke: aktives TB_RECOVERY_ID erfordert kontrollierten Rebase/X-TB-Recovery-ID; Android unterstützt diesen neuen Wiederherstellungsvertrag noch nicht. Neuer Hinweis blockiert, keine automatische Headerquittierung/Outbox-Wiedergabe. NAS-Konfiguration unbekannt. Server-HEAD a471996610a27b2d718f9835bf9c07930f29858f nur gelesen, keine server/**-Änderung.
- Vollständiger Befund und Abnahmereihenfolge: uebergaben/ANDROID_Pruefung_2026-10-23.md; Release-Datei releases/TB-Helfer_1.3.28_200078.md.
- Bekannte fünf historische Quellvertragsfehler separat offen; BTB-AUTO-016 weiterhin nicht implementiert.


## AND-RECOVERY-024 – Nutzergerät bestätigt Blocker 23.10.2026
- Nutzer-Screenshot zeigt nach Abrufversuch23.10.2026 12:37 den in200078 ausschließlich für HTTP409 SERVER_RECOVERY_REBASE_REQUIRED erzeugten Hinweis. Damit ist der zuvor nur bedingte aktive Wiederherstellungsschutz am tatsächlich angesprochenen NAS nun durch Nutzer-Runtimebeobachtung bestätigt.
- Neues Menü/Serversuche sichtbar, letzter erfolgreicher Synchronisierungsstand weiterhin26.09.; dies ist kein APK-Versionsnachweis. Keine erneute erfolgreiche Synchronisierung behauptet.
- Live-GitHub geprüft: Android5f3288cbafdb21f3b12ed34cf000e0cffd656e10, Servera471996610a27b2d718f9835bf9c07930f29858f. capabilities stellt recovery_epoch_v1/recovery_id bereit; Geräteguard blockiert Fachzugriffe vor normalem Abgleich bei fehlendem X-TB-Recovery-ID.
- Ursache ist die noch fehlende Android-Integration des dokumentierten Wiederherstellungs-/Rebasevertrags. Wiederholt Neu laden oder Geräte-Neuaktivierung allein vervollständigt ihn nicht. Keine App-Daten löschen, keinen Schutz deaktivieren oder Header blind quittieren.
- Erforderlicher Folgeblock: vor jedem erneuten Fachupload verbindlicher lokaler Schreib-/Sync-Barrier, dauerhafte Sicherung von Outbox/Basisständen und zugehörigen Dateien; danach neue Servergeneration getrennt einlesen, alte Cursor/Basisversionen generationstreu behandeln und lokale/serverseitige Änderungen sowie gelöschte Entitäten konfliktbewusst abgleichen; normale Mutationen erst nach abgeschlossenem Abgleich wieder freigeben. Abbruch/Neustart/erneute Wiederherstellung und ausstehende Helferfotos gehören zur Abnahme.
- In diesem Screenshot-Diagnoseblock nur Befund/Checkpoint gesichert. Kein neuer Runtimecode, keine APK, kein Server-/NAS-/Datenreset und kein erfolgreicher Wiederherstellungsabgleich behauptet.


## 2026-10-23 – aktueller Android-Folgeblock 1.3.29 / 200079

- Runtime-Quellstand: `d3df3108fd66920c7c1bc1bfc93d5817eacedf33`; Server-HEAD bleibt `a471996610a27b2d718f9835bf9c07930f29858f`. Keine server/**- oder NAS-Konfigurationsänderung.
- Wiederherstellungssperre vor tatsächlicher Fach-IO; private geprüfte Sicherung, getrennt lesender Serververgleich und ausdrückliche Auswahl einer neuen lokalen Generation implementiert. Prozessneustart erst NACH Sicherung und Auswahl. Alte Dateien/DBs/Einstellungen/Outbox bleiben erhalten; keine automatische Zusammenführung oder Upload-Quittierung alter Ergänzungen.
- CI-Nachweis: [Lauf 39947580253](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39947580253), Android-Compiler und `:app:testDebugUnitTest` erfolgreich: 88 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen. `:app:lintDebug` erfolgreich: 0 Fehler, 391 Warnungen; keine Warnung in neuen recovery-Dateien. JUnit-/Lint-XML aus dem zugehörigen Artefakt geprüft.
- Gesamt-CI weiterhin rot durch fünf bekannte historische Quellvertragsprüfungen (13/19/83/85/86); der Android-Compiler-/Test-/Lint-Job ist grün. Diese Prüfungen wurden nicht durch Versionskommentare oder deaktivierte Checks umgangen.
- Kein assemble, keine APK-Erzeugung/-Auslieferung. Kein Installations-/Pixel-/Poco-/NAS-E2E-Nachweis. Technische Validierung ist keine funktionale Gerätefreigabe.
- Übergabe/Prüfmatrix: `projektsteuerung/uebergaben/ANDROID_Wiederherstellung_200079.md`. Passende Release-Datei: `projektsteuerung/releases/TB-Helfer_1.3.29_200079.md`.

- Benutzer arbeitet mit GitHub Desktop und Android Studio. Nächster konkreter Schritt: Android-Branch holen, Studio synchronisieren und zunächst EIN bestehendes Handy aktualisieren, ohne Deinstallation/App-Datenlöschung. Im Recovery-Bereich Handybestand sichern und vergleichen, lokale Ergänzungen prüfen; erst ausdrücklich den neuen Serverbestand wählen.
- Keine erneute NAS-Backup-Prüfung anfordern: dieser Serverbereich ist bereits benutzerbestätigt. Für diesen Block ist der Android-Recovery-Ablauf die aktuelle praktische Abnahme.
- Keine APK erstellen. Keine volle Android-/NAS-Funktionsfreigabe aus Compiler/Unit/Lint ableiten. Rückmeldungen aus der Geräteprüfung an der Übergabematrix fortsetzen.


## AND-NODE-DELTA-023 – Quellstand / Validierung gestartet (23.10.2026)
Status: QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / BUILD UND E2E OFFEN.
- WORKSITE_NODE im schnellen Feedfilter; ausschließlich betroffene gebundene Baustellenstruktur vor BTB-/Foto-Ortsauflösung lesen. Cursor erst nach erfolgreichem Apply; bei lokalem Dirty-/Pending-/Konfliktstand kein Snapshot-Overwrite und kein Cursorfortschritt für diese Seite.
- Strukturänderungen vor Room-Schreiben persistent baustellenbezogen markieren. Gemeinsamer lokaler Mutex für Schreiben/Apply, Vergleich vor/nach HTTP, generationstreue Quittierung. Bestehende clientNodeKeys/Room-IDs bleiben erhalten. Neue Schlüssel nur für wirklich ungebundene Knoten. Vorhandene Mutation samt Basisversion/Inhalt bleibt bei Retry unverändert; keine automatische Konflikt-Rebase. Bestätigte Serverversionen separat merken; unbekannte/geänderte Basis konservativ blockieren statt raten. Keine Migration/Originaldatenänderung.
- Selektivworker verarbeitet geänderte Struktur zusätzlich, BTB und Medien weiterhin anhand exakt betroffener IDs. Fehlende Server-BTB-ID startet keinen baustellenweiten Medienlauf. Neuer Save während Worker-Abschluss erhält mit APPEND_OR_REPLACE einen Folgelauf. Offline-Scopes bleiben persistent, Recovery-Barriere bleibt aktiv.
- BTB-Status unterscheidet lokal gespeichert, Übertragung ausstehend/laufend, Konflikt/Fehler und generationstreue Serverbestätigung nach BTB+Medien+lokalem Apply. Download-Zeitstempel bestätigt keine lokale Mutation. Gemeinsamer Unterbaustellen-Formatter zeigt Bahnhof plus Gleis/Weiche plus optionalen Kurzname in BTB-Ortswegen/Fotos/Listen.
- Tatsächlich ausgeführt: 16/16 gezielte Quellfluss-/Schutzprüfungen PASS. Zehn JVM-Unit-Tests hinzugefügt; Kotlin-/Unit-/Lint-Ergebnisse noch ausstehend. Kein adb/Gradle/Kotlin lokal. Bestehende Android-CI enthält Unit/Lint ohne assemble/APK; genau einen Lauf mit dem Implementierungscommit anstoßen. Kein Versionssprung, keine APK/Veröffentlichung und kein NAS-Deployment.
- Server zwischenzeitlich6303e25d4e3401f4de4c4f8dba0c05c1b33e604e: Vergleich zum ursprünglichena4719966 geprüft; nur Portal-Anzeige/Tests/Dokumentation, kein API-Vertragsumbau. Android 1.3.29/200079 unverändert; kein neues auslieferbares Paket erstellt.
- Praktische Abnahme OFFEN: PC->Pixel/Poco-Strukturänderung ohne Vollsync, Bahnhof MM Gl4 versus Berchtesgaden Gl4, Kurzname, Weiche, neue/umbenannte/gelöschte Knoten, Offline-Save/Neustart, zweite Eingabe während Upload, parallele PC-Änderung/409, BTB+eigene Bilder ohne fremde Bilder, Wiederherstellungssperre. Fehlender Basisstand alter lokaler Änderungen erfordert bewusste Konfliktprüfung; kein automatisches Überschreiben.


### AND-NODE-DELTA-023 – Nachprüfung
CI39995383662 für28756f5f: Kotlin/Unit-Task und Lint erfolgreich; kein assemble/APK. Quellvertragsjob weiterhin dieselben fünf Fehler13/19/83/85/86; Original-Joblogs gelesen, Strukturprüfung18 PASS. Folgekorrekturen gebündelt: Strukturstatus in Baustellenliste reaktiv anzeigen; gespeicherte BTB-CONFLICT-Zeile vor Export schützen; ältere Strukturfehler überschreiben keine neuere Save-Generation; zusätzliche Serverpayloadfelder/Bahnhofskürzel bewahren (bei bewusstem Bahnhofwechsel alten Code entfernen). Abschlussprüfung dieser tatsächlichen Folgeänderungen erforderlich, erster Lauf ist dafür kein Nachweis. Übergabe und reproduzierbarer16er Source-Check in projektsteuerung/uebergaben/ANDROID_WORKSITE_NODE_023.md bzw. pruefungen/AND_NODE_DELTA_023.py. Keine Server-/NAS-/APK-/Versionsänderung.


### AND-NODE-DELTA-023 – geprüfter Zwischenstand
- Berichtartefakt12518409970 aus CI39995383662 tatsächlich gelesen:98 Tests,0 Fehler/Fehlschläge/übersprungen; neue WorksiteDeltaPolicyTest10/10 PASS. Lint0 Fehler,0 Fatal,391 Warnungen. Kotlin/Unit/Lint beziehen sich auf28756f5f.
- Folgecode963490c4 ergänzt reaktiven Strukturstatus, Konfliktsperre vor BTB-Export und Erhalt zusätzlicher Serverpayloadfelder/Bahnhofskürzel. Lauf39996302565 durch neueren Lauf abgebrochen; daraus kein Compiler-/Testfehler abgeleitet.
- Letzter Code60e8dca5ae509bd6444302d13025996f9283f35b schützt außerdem lokal vorgemerkte Helfer-Strukturänderungen: nur LEAD darf seine Struktur-Generation quittieren; reiner Helfer-Download muss Dirty-Outbox erhalten. Lauf39996754175 prüft exakt diesen Stand, derzeit Unit-Stufe IN ARBEIT; keine Behauptung eines finalen Testerfolgs vor Ergebnis.
- Lokaler reproduzierbarer16er Source-Check erneut PASS. Keine APK/Versions-/Server-/NAS-Änderung. Finaler Ergebnisblock und Pixel/Poco-/NAS-E2E bleiben offen.


## AND-NODE-DELTA-023 – Abschluss des Quellstands (23.10.2026)
Status: **QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / KOTLIN-, UNIT- UND LINT-PRÜFUNG BESTANDEN / GERÄTE-E2E OFFEN**.
- Geprüfter finaler Runtimecode: `60e8dca5ae509bd6444302d13025996f9283f35b`. Letzter überprüfter Server-HEAD: `6303e25d4e3401f4de4c4f8dba0c05c1b33e604e`. Keine server/**- oder NAS-Konfigurationsänderung.
- Finale CI: [39996754175](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39996754175), Androidjob120562551153 SUCCESS. `:app:testDebugUnitTest` inklusive Kotlin-Kompilierung PASS (3m43s); `:app:lintDebug` PASS (1m27s). JUnit-/Lint-XML aus Artefakt12521180668 tatsächlich gelesen: **98 Tests,0 Fehler,0 Fehlschläge,0 übersprungen; zehn neue WorksiteDeltaPolicyTest-Methoden PASS; Lint0 Fehler/0 Fatal/391 Warnungen**. ZIP-SHA25677ce457c2b8dbc23f5e7696936ef035d8023c715289a0477782866ae99c42de5.
- Gesamt-CI bleibt ROT ausschließlich durch dieselben fünf dokumentierten Quellvertragsprüfungen13/19/83/85/86 (alte Schreib-/DELETE-Textmarker, alte Versionen/Server-/Update-Texte). Original-Logs des finalen Jobs120562550454 gelesen; Strukturprüfung18, Schema13/17 und weitere bestehende Schutzprüfungen PASS. Keine Prüfung deaktiviert/abgeschwächt und kein Fehler als Erfolg umgewertet.
- Neuer Vertrag im Client: WORKSITE_NODE im schnellen Feed; nur betroffene gebundene Baustellenstrukturen vor BTB-/Foto-Ortsauflösung; Cursor nach erfolgreichem Apply. Dirty/Pending/Konflikt/Paralleländerung blockieren Überschreiben. Nur LEAD darf eigene Struktur-Generation quittieren; vorgemerkter Helferbestand bleibt bei reinem Download erhalten. Bestehende Schlüssel/IDs und zusätzliche Serverpayloadfelder bleiben erhalten; bei bewusstem Bahnhofwechsel wird ein alter Bahnhofscode nicht weiter als neue Identität verwendet.
- Lokal speichern → dauerhafte selektive Vormerkung → betroffener BTB plus Bilder, zusätzlich geänderte Struktur. Explizite Statusgenerationen verhindern falsche Serverbestätigung durch Download oder älteren Upload. Bereits gespeicherter BTB-Konflikt blockiert den selektiven Export. Baustellenliste zeigt Strukturstatus/Probleme reaktiv. Unbekannte/abweichende Struktur-Basisversion konservativ erhalten statt automatisch rebasen.
- Reproduzierbarer lokaler16er Source-Check auf finalem Code erneut PASS. Keine APK/assemble, keine Versionsänderung/Release-Veröffentlichung; technischer Stand1.3.29/200079. CI-Validierung ist kein Installations-/Pixel-/Poco-/NAS-E2E-Nachweis.
- Übergabe und praktische Abnahme: [ANDROID_WORKSITE_NODE_023.md](uebergaben/ANDROID_WORKSITE_NODE_023.md). OFFEN: Portal->Pixel/Poco, Bahnhof/Gl4/Kurzname/Weiche, Struktur anlegen/ändern/löschen, Offline/Neustart, zweiter Save während Upload, parallel409/Basisstand unbekannt, Helfer, gezielte Bilder und Recovery-Barriere. Bekannte Basis-/Baumkonflikte erfordern bewusste Prüfung; kein automatischer Datenreset.
- Übernahme des Android-Quellstands über GitHub Desktop/Android Studio. **Für diesen Android-Auftrag keine NAS-Übernahme nötig; kein Deployment durchgeführt.** Separater Portalstand bleibt eigener Serverauftrag.


## AND-RELEASE-080 – konsistente Buildvorbereitung 23.10.2026
Status: QUELLSTAND VORBEREITET / METADATENPRÜFUNG BESTANDEN / APK-BUILD200080 UND GERÄTE-E2E OFFEN.
- Nutzerhinweis auf fehlende Release-MD behoben: app/build.gradle.kts jetzt1.3.30/200080 und projektsteuerung/releases/TB-Helfer_1.3.30_200080.md passend ergänzt. Dateiname, Versions-/Buildfelder und alle Pflichtfelder/Releaseabschnitte gezielt geprüft. Bisherige200079-Datei unverändert erhalten.
- Nur Versionsmetadaten und Begleitdokumentation geändert; Runtime-Fachcode unverändert. Die98 bestandenen Tests und Lint0 Fehler/391 Warnungen aus CI39996754175 beziehen sich ausdrücklich auf60e8dca unter200079. Keine neue200080-APK und kein neuer Gradle-/CI-Nachweis behauptet; keine CI ausgelöst ([skip ci]).
- Nächster lokaler Android-Studio-Build soll200080 verwenden. Vor Weitergabe APK-Manifest kontrollieren und ausschließlich die zugehörige Release-MD mit dieser APK verwenden. Kein alter APK-/neuer-MD-Mix. Keine neue sichtbare Buildanzeige, keine Veröffentlichung und kein NAS-Deployment.


## AND-CRUD-AUDIT-024 – Android/Portal-Funktionsabgleich 24.10.2026
Status: QUELLPRÜFUNG ABGESCHLOSSEN / TEILWEISE UMGESETZT / GERÄTE-E2E OFFEN. Android78b9d2c54eb6f198e20383b7491297cc3041c4f3, Server25b40892affe3c9ea637c53029d3c508ded2d891 und PR11/6 geprüft. Nutzer-Screenshots zeigen Portal-Eintragseditor; Android hat bereits Eintrag bearbeiten/löschen, Firma/Ort/Section ändern und Kamera/Galerie-Import mit stabilen IDs. Zehn gezielte Quellfragen bestätigt, keine Runtimeprüfung.
- Lücken: direktes WhatsApp-Teilen per SEND/SEND_MULTIPLE fehlt. Lokale Foto-/Dateilöschung bzw. PDF-Auswahl ist nicht vollständig mit serverseitigem Foto-Unlink gleichgesetzt: im geprüften modernen Android-Medienpfad fehlen dessen DELETE-Aufruf und dauerhafter Outboxauftrag. Portal bietet außerdem ausdrückliches Lösen nur vom Eintrag; am Android-Eintrags-Thumbnail kein gleichwertiger Button gefunden. Nicht pauschal als funktionierender serverweiter Foto-Löschabgleich bestätigen.
- Vorhandene Android-Bedienung: Eintrag antippen; lange drücken für Firma/Punkt4–8/Löschen; nach einfacher Löschung auch Abschnitt speichern. Galerie & Dateien für heruntergeladene WhatsApp-Bilder. Geänderte Editorfelder vor Bildaufnahme/-import erst speichern.
- Vollständiger Befund, Belege und Prüfgrenzen: projektsteuerung/uebergaben/ANDROID_PORTAL_CRUD_AUDIT_2026-10-24.md. Folgeimplementierung nur nach entsprechendem Auftrag; dieser Auftrag ist Prüfung. Keine Runtime-/Versions-/Serveränderung, keine CI/APK, keine NAS-Übernahme/Deployment. Kein adb/Emulator/Pixel-/Poco-/WhatsApp-/NAS-E2E; installierte Version und NAS-Commit nicht verifiziert.


## AND-HELPER-END-024 / WhatsApp-Entscheidung –24.10.2026
- Nutzer: WhatsApp→Teilen→TB-Helfer NICHT benötigt; aus geplanten Folgearbeiten streichen. Galerie & Dateien bleibt. Frühere Missing-Feature-Notizen dazu sind keine offene Anforderung mehr.
- Nutzer meldet fehlendes Helfer-Schichtende bzw. fehlende Serverankunft. Androidb315305846c95dfaacfbec0ad7baeba3ff376667 und Server25b40892affe3c9ea637c53029d3c508ded2d891 ab Einstieg geprüft. Bestehender Helferpfad ruft shift-finish auf; nur eigene Teilnahme FINISHED, Haupt-TB finalisiert weiterhin.
- Quellbefunde: Finish-Wunsch nicht persistent zur Wiederholung vorgemerkt; Inhalts-/Foto-/Netzfehler brechen vor POST ab. Kein Mehrfachklick-Guard; ein weiterer laufender Klick kann bei inzwischenFINISHED automatisch Resume auswählen. Erfolgreicher POST plus fehlgeschlagenes Nachlade-GET werden als Fehler gezeigt, obwohl Serverstatus bereits geändert sein kann. Diese Schwachstellen sind im Code belegt; konkrete Ursache beim Nutzer ohne Laufzeitmeldung/Audit nicht bewiesen.
- Diagnose mit Belegen/Grenzen: projektsteuerung/uebergaben/ANDROID_HELFER_SCHICHTENDE_DIAGNOSE_2026-10-24.md. Benötigt: genaue Meldung nach einem Klick und folgender Buttontext; ggf. Teilnehmer-/Auditstand desselben BTB/Benutzers. Keine Datenlöschung/Neuaktivierung, kein Schutz-Bypass. Dieser Prüfauftrag noch ohne Runtimekorrektur/Build/CI/Serveränderung/Deployment.


## AND-LIFECYCLE-024 – Umsetzung beauftragt 24.10.2026
Status: IN ARBEIT. Nutzer bestätigt nach Diagnose Umsetzung mit „okay weiter“. Basis Android660adf4fb59222c97c8a16b44f5b51e9eee30c8c; Server8349f03eec04951a21ad66838a2602d4ae239241 (neuere PDF-/Portaländerungen, keine Lifecyclevertragsänderung). PR11 offen, PR6 Draft.
- Dauerhafter, generation-/gerätegebundener Auftrag je BTB für explizites SHIFT_FINISH, SHIFT_RESUME oder FINALIZE. Keine automatische Umschaltung zwischen Beenden/Fortsetzen bei Retry. Abschlusswunsch vor Netzaufruf sichern; stabile X-Client-Change-Id für Wiederholungen. Erst serverseitig bestätigtes Ziel quittieren. POST-Erfolg mit fehlender Nachladebestätigung bleibt ausstehend.
- Betroffene Inhalte/Bilder und geänderte Struktur vor Abschluss abgleichen; Dirty/Pending/Konflikt-/Recovery-Schutz bewahren. Keine globale Synchronisierung oder blinde Konfliktauflösung. Mehrfachklickschutz; Hintergrundnachholung und Startwiederaufnahme. Teilnehmerblocker sichtbar, kein unbeaufsichtigtes allow_unfinished. Bewusstes Trotzdem-Abschließen bleibt ausdrücklich bestätigter separater Vorgang.
- Vorhandener Delta-Feed verteilt BTB/Teilnehmeränderungen an laufende Clients; keine sofortige Zustellung an ausgeschaltete/offline Geräte behaupten. Rollenrechte/Helfer ohne PDF/Finalisierung erhalten. Keine server/**-Änderung oder NAS-Deployment. WhatsApp-Teilen entfällt weiterhin.
- Zielversion1.3.31/200081 mit passender Release-MD. Gezielte Kotlin-/Unit-/Lint-Prüfung, kein automatischer APK-/Release-Upload. Pixel/Poco/NAS-E2E separat offen. 16h-Autoabschluss bleibt separater Vertrag, hier nicht implementiert.


## AND-LIFECYCLE-024 – Quellkorrektur und technische Prüfung 24.10.2026
Status: QUELLSTAND FERTIG / KOTLIN-, UNIT- UND LINT-PRÜFUNG BESTANDEN / INSTALLATION UND GERÄTE-/NAS-E2E OFFEN.
- Finaler Runtimecode2570b4c222fa5fb9338e4471a22421425a99e8c1, Version1.3.31/200081 mit passender Release-MD. Explizite persistente Schicht-/Abschlussaufträge, stabile Mutationskennung, Serverzielprüfung vor Retry, Mehrfachklickschutz, Startnachholung und sichtbarer ausstehender Zustand. Inhalte/Bilder/Struktur gezielt vorher; Konflikt-/Dirty-/Recovery-/Rollen-/Gerätebindungsschutz. Kein automatisches Resume oder allow_unfinished. Originalbasis bei Dirty/Pending bewahrt, strikte offene Speicherrückmeldung für Abschluss. Galerie bleibt; WhatsApp-Teilen entfällt.
- Finale CI40108708246, Androidjob120915704503 SUCCESS: Kotlin-/Unitstep PASS (2m24s), Lintstep PASS (1m7s). Tatsächlich gelesenes JUnit-/Lint-XML aus Artefakt12566170481:108 Tests,0 Fehler/Fehlschläge/übersprungen;10 neue LifecyclePolicy-Tests PASS. Lint0 Error/0 Fatal/391 Warning, keine neue BtbLifecycle-Issue.18 lokale gezielte Sourcechecks PASS. ZIP-SHA25642887659287318d828801142ef9b98e8cd6493c7bc2ad0f0a170840ab7b1a3aa.
- Gesamt-CI bleibt ROT durch dieselben fünf historischen Quellvertragsprüfungen13/19/83/85/86; finale Original-Logs geprüft. Keine Prüfung deaktiviert/abgeschwächt. Finaler Gradlelog enthält weiterhin einen nicht den Job abbrechenden KSP/AWT-NullPointer-Diagnoseeintrag; Kompilierung, Testtask und Lintstep erfolgreich, nicht als neue Runtimeursache belegt.
- Server zuletzt ee6a879e13a6b97c4b75da97800d5f9a2923ac55 lesend gegen8349f03 verglichen: nur PDF-/Test-/Dokumentationsänderungen, kein neuer Lifecyclevertrag. Keine server/**-Änderung, NAS-Übernahme/Migration oder Deployment durch diesen Block nötig. Keine APK/assemble/Veröffentlichung.
- Übergabe: projektsteuerung/uebergaben/ANDROID_BTBSCHICHT_ABSCHLUSS_200081.md. Nächster Praxisablauf:200081 lokal bauen, Pixel/Poco als Update installieren; Helfer Schicht Ende -> TB sieht FINISHED -> BTB Abschluss -> Portal/beide Handys konsistent. Offline+Neustart, Bildfehler, paralleler Save/409 und POST-Erfolg mit ausgefallenem Folge-GET praktisch prüfen. JVM-Policy-/Sourcechecks sind kein WorkManager-/Netz-/Geräte-E2E. 16h-Autoabschluss und serverweiter Foto-Unlink bleiben separate offene Aufträge. Keine konkrete damalige Laufzeitursache ohne Logs behauptet.


## 26.10.2026 AND-NEUANLAGE-026
Quelluntersuchung abgeschlossen, Runtimeursache noch offen. Siehe aktuellen Auftrag: lokale Baustelle ohne Serverbindung blockiert Node-Key/Hinzufügen; lokaler Entwurf blendet PDF-Vorschau aus; selektiver Worker provisioniert nicht. Serveranlage im Einladungsablauf gefunden. Keine Runtimeänderung, Versionsanhebung, APK, CI oder NAS-Übernahme. Benötigt: installierter Build und vollständiger Syncfehler, Handy/Portalidentität. Daten erhalten. Ausgangs-HEAD Android6322760, Server923cf93; PR11 offen/nicht Draft, PR6 offen/Draft.


## AND-BIDIR-026 – Quellprüfung 26.10.2026
Status: QUELLPRÜFUNG ABGESCHLOSSEN / KORREKTUR UND GERÄTE-E2E OFFEN. Screenshot bestätigt1.3.31/200081. Android2368fbc, Server923cf93 geprüft. Lokale Neuanlage ohne automatische Serverprovisionierung; Node-Key blockiert lokalen Eintrag; lokaler Entwurf ohne PDF-Vorschau. Schneller Androidfeed ohne WORKSITE/MEMBERSHIP/INVITATION und ohne Kontextinitialisierung; lokales Hauptbaustellen-Umbenennen ohne Sendepfad. Unterbaustellen-/BTB-/Foto-Delta und Portal-Detail-SSE existieren, geschützt. Portal-Liste ohne entsprechenden Livepfad. „Neu laden“ startet auch vollständigen Fachworker, doch SUCCEEDED umfasst ungebundene lokale Baustelle nicht; frühere Nur-Katalog-Vermutung korrigiert. Gemeinsame API-/Nutzeridentität vorgesehen, aktuelle UUIDs nicht verglichen. Vollständige Belege, Umsetzungsteilung und offene A–H-Gerätematrix: uebergaben/ANDROID_BIDIREKTIONAL_2026-10-26.md. Keine Runtime-/Versions-/Servercodeänderung, APK, Actions oder NAS-Übernahme/Deployment.






## BTB-INTEGRATION-031 – Code umgesetzt, Abnahme offen (31.10.2026)

Android 1.3.35/200085 und passender Serverstand: drei Bildspalten, strukturierter Drucksnapshot für Firma/Ort und Fotoverweise, sofort gespeichertes Eintragslöschen, erhaltene Fotoverknüpfung beim Wechsel von Firma/Ort/Punkt, eindeutiger kontextgebundener Beschreibungsabgleich, ausdrückliche REWORK-Nachbearbeitung ohne Ersetzen einer neuen aktiven Schicht, Abschluss ohne Medienimport, Export mit bestätigter Original/Sync-Zuordnung und Linkmetadaten. Ausdrückliches Lösen wird zusätzlich als entryBindingCleared in wirksamen Foto-Metadaten transportiert; andere Geräte dürfen es nicht automatisch wieder binden. Historische Originale/IDs/Rollen/Outbox-/Konfliktschutz bleiben geschützt.

Nachweise:22 neue Server-Funktionsprüfungen und7 produktive Portal-Guardfälle PASS; vorhandene132/138 je11, Worksite-Head14, Portal152/153 und List158 PASS; Produktions-PDF mit drei Karten in einer physischen Zeile/KM0/GPS-Links PASS. Acht Android-JUnit-Fälle ergänzt, NICHT ausgeführt. Kein Android-SDK/Gradle/Kotlin/ADB; kein PostgreSQL/HTTP-/Geräte-/NAS-E2E. Keine APK, Actions, Veröffentlichung, Produktivübernahme oder Bestandsbereinigung. Abschlussdauer und Wetterabweichung am Gerät weiterhin offen. Kein Status „alles funktioniert“.

Details, genaue Grenzen und Abnahmeschritte: [BTB-INTEGRATION-031](uebergaben/BTB-INTEGRATION-031.md). Android-Release-MD: projektsteuerung/releases/TB-Helfer_1.3.35_200085.md, DOWNLOAD_URL leer. Nächster verbindlicher Schritt: Android-Build/Tests und gemeinsamer Testbetrieb beider Branchstände mit Haupt-TB/Helfer, Änderungen/Löschen/Verschieben, sieben Bildern und zweimaligem Export.


BTB-INTEGRATION-031 Quellreview-Nachtrag: lokale matchingFotoIds-Funktion vor den ersten Aufruf im Löschdialog verschoben, damit keine lokale Kotlin-Vorwärtsreferenz entsteht. Android-Compilerlauf weiterhin offen; keine zusätzliche Funktionsfreigabe.


## AND-MEDIEN-1102 – begrenzte Korrektur, 02.11.2026

Status: QUELLSTAND FERTIG; Android-Compiler, Unit-Tests und Lint noch nicht bestätigt; E2E OFFEN.

- Gesamtarchiv lädt alle lokalen Fotos derselben Baustelle über den vorhandenen unbeschränkten Sync-Leseweg. Der geöffnete BTB darf den Baustellen-Gesamtexport nicht beschneiden; die BTB-Auswahl für die Schichtlisten bleibt bestehen.
- Nachgewiesene Original/Server-Zuordnung nutzt denselben Exportschlüssel auch bei fehlendem Katalog-SHA256. Schicht-XLSX führt dasselbe Exportziel nur einmal mit lückenloser Nummerierung auf. Gleiche Beschriftung oder gleicher Dateiname allein werden nicht zur Zusammenführung benutzt; keine Bestandsdaten werden gelöscht.
- Foto-Bearbeitungsdialog hat unten nur Abbrechen/Speichern. Zurücksetzen und Aus-BTB-entfernen stehen unter Weitere Aktionen, mit bestehender Bestätigung. Zurücksetzen stößt wie Speichern Aktualisierung/Sync an; BTB-Wechsel wird abgefangen.
- PDF-Quellprüfung: naechsteAutoZeile nimmt bereits höchstens drei Fotos. PDF-Renderer, Original-/Abschluss-PDFs und Raster bleiben unverändert. Die Herkunft der abweichenden Handyansicht sowie fehlende Bilddateien sind damit nicht geklärt.
- Room-Implementierungsprüfung bleibt aktiv; der im Screenshot unzulässige Task.project-Zugriff im doLast wird durch einen während der Konfiguration erfassten Verzeichnis-Provider ersetzt. Keine Bibliotheksänderung/Unterdrückung der KSP-Ausnahme; Screenshot allein belegt keinen App-Absturz.
- Drei zusätzliche Unit-Testfälle: stabiler Server-Schlüssel ohne Hash, vorhandener Hash unverändert, doppelte Exportzeile ohne Verlust unterschiedlicher Bilder mit gleichem Text.
- Kein Servercode, keine API-/Datenbankänderung, keine APK, keine Versionserhöhung. Android weiterhin 1.3.35 / 200085 als Quellversion; kein neuer auslieferbarer Build.

Offene Abnahme: lokal mit gewohntem Schlüssel bauen; Pixel/Poco Foto einmal aufnehmen, erneut synchronisieren, Gesamt- und Schichtliste zweimal exportieren (einschließlich anderer BTBs derselben Baustelle), zwei echte Bilder mit gleichem Text erhalten; Dialog mit Tastatur/kleinem Display inklusive Speichern/Abbrechen/Zurücksetzen/Entfernen prüfen. Neu erzeugtes PDF mit 1/2/3/4/7 Bildern und vollständigen Beschriftungen prüfen. Vorhandene Daten nicht löschen. Wetter, BTB-Abschluss und Berechtigungen sind nicht Gegenstand dieses Änderungsblocks.


### AND-MEDIEN-1102 – geprüfter Abschluss des Quellblocks

Quellcommit: `5aa60c2ae1fc3b288bcae961cc5f7d2898eefb50` auf `feature/tb-android-2.0`. Android- und Server-HEAD vor Statussicherung erneut geprüft; Server unverändert `add840ea36b5a878e26157fd6bf8aec28d997897`.

Nachweis: [Android CI 41508682580](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/41508682580), Android-Prüfjob `125494515611` SUCCESS. `:app:testDebugUnitTest` einschließlich Kotlin/KSP/Java-Kompilierung sowie `:app:lintDebug` erfolgreich. Original-Berichtsartefakt `13171493725` ausgewertet: 125 Tests in 23 Suiten, 0 Fehler, 0 Fehlschläge, 0 übersprungen; darunter alle fünf Fälle der beiden Medienexport-Testsuiten erfolgreich. Lint: 0 Errors/Fatal, 395 Warnings (wie vorheriger geprüfter Stand). Begrenzte Source-Checks erfolgreich.

Room-Prüfung in beiden Gradle-Läufen ausgeführt, Configuration Cache jeweils gespeichert; der frühere Task.project-Ausführungshinweis erscheint nicht mehr. Die KSP-Tooling-Ausnahme `AWT-EventQueue-0 / ApplicationManager.getApplication() == null` tritt weiterhin auf, obwohl beide Gradle-Läufe erfolgreich enden. Nicht behoben oder als App-Absturz umgedeutet; keine speculative KSP-/Gradle-/SDK-Aktualisierung und keine Prüfung abgeschwächt.

Gesamt-CI ist NICHT BESTANDEN: separater Vertragsjob `125494515143` scheitert laut Original-Log weiterhin an denselben fünf Altprüfungen 13/19/83/85/86 (Schreibpfad-Anker, DELETE-Anker, alte Server-/Versions-/Updater-Anker). Prüfungen 14 (einschließlich stabiler Dreierzeile), 15 (BTB-Lifecycle), 03/04/12/17/18/84 bestehen. Diese Altfehler sind nicht Teil der eng beauftragten Medienkorrektur.

Status: QUELLSTAND FERTIG; SOURCE-TEST BESTANDEN; Android-Kompilierung/Unit-Tests/Lint BESTANDEN; kein APK-Build, kein INSTALLIERT, E2E OFFEN, nicht ERLEDIGT. Keine neue Version/Release-MD, keine APK erstellt. Kein Emulator/ADB verfügbar. Pixel/Poco-, Tastatur-/Dialog-, Original/Sync-Dopplungs-, mehr-BTB-Gesamtexport- und Android/Server-E2E-Abnahme gemäß obiger Matrix bleiben offen. Die Quelle des abweichenden älteren PDF-Rasters und fehlende Bilddateien bleiben ungeklärt. Ein neuer auslieferbarer APK-Build benötigt separat den höheren versionCode und die passende Release-MD aus genau diesem Build.


## EINSTELLUNGEN-1103 – geprüfter Quellabschluss (03.11.2026)

- Android-Funktionsstand `f507cab7b7de7a68da4ec8d05f5ad0a5c046eecc`; Server-Template-Stand `7afa257216ef682bcba90ee41e56c6173a2ad960`, jeweils eigener Fachbranch. Nutzerauftrag und Umfang vor Implementierung separat gesichert (Android6338c1b, Server5b9b1ee).
- Android: Karten Mein Konto / Fotos und Bautagebuch / Verbindung und Hilfe, zusätzliche Anzeige-/Benachrichtigungs-/Widget-Ziele aufklappbar. Bisherige neun Aktionen und Fachaufrufe erhalten, keine Einstellungen umgestellt. Neuer Menüzustand nur im Fragment-/Bundle-Zustand, keine neue Geschäftseinstellung. Lokale Styles schützen die übrigen App-Schaltflächen. Spracheingabe bleibt offener separater Wunsch ohne funktionslose Menüoption.
- Nachweis: [Android CI41586577181](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/41586577181), Androidjob125753367243 SUCCESS. testDebugUnitTest inkl. Kotlin/KSP/Java/Ressourcen erfolgreich (3m55s); lintDebug erfolgreich (1m31s). Original-Berichtsartefakt13208810670 ausgewertet:125 Tests,0 Fehler/Fehlschläge/übersprungen; Lint0 Error/Fatal,381 Warnings. Einstellungsdateien: nur bereits vorhandener HardcodedText-Hinweis für den Titel Einstellungen, keine neue Lint-Fehlerfreigabe nötig. Kein assemble/APK, kein Versionssprung.
- Gesamt-CI bleibt ROT durch denselben separaten Vertragsjob125753367488 mit Altprüfungen13/19/83/85/86; Original-Logs gelesen. Die bekannte nicht abbrechende KSP/AWT-NullPointer-Diagnose bleibt im Compilerlog. Keine Pluginaktualisierung/Prüfungsabschwächung als Teil des Menüauftrags.
- Server: Profil/Sicherheit/Kontakte in drei klaren aufklappbaren Karten; aktive Portalnavigation, System-Sprungziele inkl. sichtbarem Restoreweg. Bestands-IDs, Formularzuordnung und Fach-JavaScript statisch verglichen; JS-Syntax und isolierter neuer Navigationshandler in beiden Restorekonfigurationen PASS; Sourcecheck39 PASS. Check91 bereits im Ausgangsstand identisch FAIL (Workflow-Marker013_job_progress.sql), nicht verdeckt. Backend/API/DB/Rollen/CSRF/Backup/Restore/Mail unverändert. Kein Server-Gesamttest/Deployment.
- Status QUELLSTAND FERTIG / ANDROID-KOMPILIERUNG, UNIT UND LINT BESTANDEN / GEZIELTE SERVER-SOURCE- UND JS-PRÜFUNG BESTANDEN / E2E OFFEN. Keine Aussage INSTALLIERT oder ERLEDIGT. Keine vorhandenen Geschäftseinstellungen/Daten verändert oder Funktionen entfernt.
- Prüfgrenze: kein ADB/Android-Emulator; lokale Browser-/Jinja2-Laufzeit nicht verfügbar, Installationsversuche erfolglos; Cloud-Browser-Vorschau durch URL-Richtlinie abgelehnt, nicht umgangen. Deshalb keine Pixel-/Poco-/visuelle Browser-/Jinja-Render-/Live-HTTP-/NAS-E2E-Abnahme behauptet. CSS für schmale Ansichten ist implementiert, nicht visuell abgenommen.
- Nächster praktischer Schritt: Android über GitHub Desktop/Android Studio übernehmen und als datenbewahrendes Update testen; alle neun Ziele, Extras öffnen/schließen, Rotation/Zurück, große Schrift, Sync-Netzdialog inklusive Abbrechen/Speichern, Wartung/Fehlerprotokoll/Updates prüfen. Portal: Desktop/Handy/Tastatur/große Schrift, Profilfelder und Fehleranzeige, Firmenkontakte, Sicherheit und Adminbedingung sowie vier Systemziele bei beiden Restorekonfigurationen prüfen. Produktive Konto-/Mail-/Restoreaktionen nicht als beiläufigen Test auslösen.
- Serveranzeige benötigt spätere Übernahme der drei Templates server/templates/v1_portal_home.html, v1_base.html und v1_system_status.html über den bestehenden NAS-Übernahmeweg. Nicht durchgeführt. Android und Server sind getrennt zu übernehmen; keine Datenbankmigration oder geänderten API-Verträge erforderlich.


## SPRACHE-1103 – Prüfauftrag und Konzept gesichert

Die am 03.11.2026 bestätigte freie Spracheingabe mit Mikrofon-Anzahl (0 unsichtbar), lokalen bearbeitbaren BTB-Entwürfen und separater Unterbaustellenauswahl ist unter 02_AKTIVER_AUFTRAG.md als SPRACHE-1103 vollständig dokumentiert. Quell-/SDK-Dokumentationsprüfung abgeschlossen; On-device Deutsch und Geräuschverarbeitung müssen pro Pixel/Poco geprüft werden. Bei fehlender Offlinefähigkeit nur freigegebene Onlineerkennung; kein stiller Cloudfallback. Vorschlag für Einstellungen > Spracheingabe und vollständige Hoch-/Querformat-/Rechte-/Offline-/PDF-/Sync-/Widget-Regressionsmatrix festgehalten. Keine Runtimeimplementierung, kein Build/CI/APK/Version/Server-/NAS-Eingriff. Geräte-/Flugmodus-/Lärmtest OFFEN; bestehende 125 Tests sind kein Nachweis dieser neuen Funktion.
