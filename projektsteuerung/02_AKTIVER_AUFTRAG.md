# TB-HELFER 2.0 – AKTIVER AUFTRAG

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

## PS-001 – Projektsteuerung zentralisieren
Status: **UMGESETZT IM QUELLSTAND DIESER STEUERUNGSÄNDERUNG**

Ein gemeinsamer Einstieg unter `projektsteuerung/`; Android/Server-Codebereiche bleiben getrennt; alte Top-Level-Steuerungsordner entfallen; Chat-Sicherung erfolgt ereignisbasiert. Kein `app/**`- oder `server/**`-Fachcode wird durch PS-001 geändert.

## IMG-001 – Bildtransport / Serverlast
Status: **QUELLAUDIT ABGESCHLOSSEN / ABWEICHUNGEN BESTÄTIGT / IMPLEMENTIERUNG NACH E2E-PRIO-001**

Verbindlich:
- Handyoriginal bleibt auf dem Aufnahme-Handy.
- Handy → Server/Portal: verkleinerte/komprimierte Sync-Version + Thumbnail + Metadaten.
- Portal → Handy: kleine Version/Thumbnail.
- Handy → anderes Handy: kleine Version/Thumbnail über Server.
- Portal-Upload darf die empfangene Quelldatei serverseitig behalten; mobile Nutzung über Renditions.

Diese Regel steht bereits in `projektsteuerung/vertraege/TB-SYNC_15x15_ZIELMODELL.md`.

Nächster Schritt:
1. Android-Fotoupload prüfen: Original oder Sync-Version?
2. Server-Empfang/Storage/Renditions prüfen.
3. Handy→Handy-Download prüfen.
4. Abweichungen dokumentieren.
5. erst dann getrennte Android-/Server-Aufträge.
6. E2E mit realen Dateigrößen.

### IMG-001 Audit-Ergebnis 26.09.2026
- Android lädt aktuell das lokale Foto-Original hoch, nicht eine separate kleine Sync-Version.
- Server speichert die empfangene APP-Datei unverändert.
- Server-Renditions `thumb`/ `preview` existieren bereits.
- Android ignoriert diese Renditions und lädt über `/content`.
- Lokales 512-px-Thumbnail existiert, wird im Online-Sync aber nicht genutzt.
- Metadatenlücken: GPS-Genauigkeit nicht persistent; Mangel-/Abnahmefoto-Upload strukturell unvollständig.

Umsetzung ist dokumentiert, aber gemäß `E2E-PRIO-001` erst nach stabiler Hauptgerät-/Poco-Fachkette dran.

## SYNC-DEVICE-001 – Poco erhält keine Daten
Status: **OFFEN**

Serverseitig HELPER im richtigen Team bestätigt; Gerät erhält trotzdem keine Daten. Keine Neu-Anlage von Benutzer/Einladung auf Verdacht. Binding/Sync/API/Logs gezielt eingrenzen.

## E2E-PRIO-001 – Hauptgerät/Portal zuerst, Helfer danach
Status: **VERBINDLICHE ARBEITSREIHENFOLGE**

Neue Prioritätsentscheidung:
1. Ein verantwortliches TB-Handy muss zuerst mit Server und Portal in der echten Fachkette sauber funktionieren.
2. Danach wird `SYNC-DEVICE-001` / Poco als HELPER gezielt bis zum bestandenen Geräte-E2E abgearbeitet.
3. Weitere breite 15×15-Lastreihen werden vorerst zurückgestellt.
4. Nach geplantem RAM-Ausbau auf 8 GB bzw. nach Wechsel auf ein neues NAS wird der vollständige Last-/Kapazitätstest auf der dann maßgeblichen Hardware wiederholt.
5. Bis dahin werden Leistungswerte nur als Diagnose verwendet; funktionale Server-/Sync-Verträge werden nicht aufgrund eines einzelnen Lastwerts auf Verdacht umgebaut.

Bereits praktisch beobachteter Kapazitätsteiltest:
- `/api/v1/sync/changes/wait`
- 255 getrennte Portal-Sessions
- `success=255`, `failure=0`
- Wall 16,43 s, Median 14,28 s, Maximum 15,79 s
- verbliebene Python-Testsessions anschließend kontrolliert von 7 auf 0 bereinigt

Bewertung: **DELTA-WAIT-KAPAZITÄTSTEILTEST BESTANDEN / VOLLSTÄNDIGER 15×15-E2E WEITER OFFEN**.

## NET-001 – Hauptgerät kann api-v1.tb-helfer.de nicht auflösen
Status: **ÖFFENTLICHE DNS/API ERREICHBAR / APP-SEITIGE URSACHE OFFEN**

Praktischer Befund beim Anlegen einer Testbaustelle auf dem Hauptgerät:
- App meldet `Synchronisation unvollständig`;
- konkrete Ursache: `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`;
- damit ist der aktuelle Hauptgerät↔Server/Portal-E2E bereits vor der Fachsync-Prüfung durch DNS/Netzauflösung blockiert.

Vorgehen:
1. öffentliche Erreichbarkeit/DNS von `api-v1.tb-helfer.de` prüfen;
2. bei öffentlich funktionierender Domain Geräte-DNS/Netz/Private-DNS/VPN eingrenzen;
3. erst nach erfolgreicher Namensauflösung Fachsync und Portal-Konvergenz prüfen;
4. keine Sync-/DB-/API-Codeänderung auf Verdacht.

Browser-Nachtest auf demselben Hauptgerät:
- `https://www.tb-helfer.de` erreichbar;
- `https://api-v1.tb-helfer.de/health/live` erreichbar;
- öffentliche Domain/Cloudflare/DNS sind damit grundsätzlich erreichbar;
- nächster Schritt: denselben App-Sync erneut auslösen;
- nur bei erneutem App-only `Unable to resolve host` Android-Netzpfad/Prozess/Private-DNS/VPN/HTTP-Client gezielt eingrenzen;
- keine Sync-/DB-/API-Codeänderung auf Verdacht.

### NET-001 Reproduktion nach Browser-Nachweis
- Auf demselben Hauptgerät sind `www.tb-helfer.de` und `api-v1.tb-helfer.de/health/live` im Browser erreichbar.
- Unmittelbarer erneuter App-Test mit `Neu laden` reproduziert weiterhin exakt `Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.
- Der Fehler ist damit **app-/Android-Netzpfad-spezifisch reproduziert** und nicht durch eine allgemeine öffentliche DNS-/Cloudflare-Störung erklärt.
- Nächster Schritt: Android-Netzpfad, gespeicherte Serverbasis, HTTP-Client und App-Prozess gezielt gegen den Browserpfad prüfen.
- Keine Server-/DB-Änderung auf Verdacht.

### NET-001 Netzwechsel-Reproduktion
- App vollständig gestoppt/neu gestartet: Fehler bleibt identisch.
- WLAN deaktiviert und erneuter App-Test über Mobilfunk: Fehler bleibt identisch.
- Damit ist ein nur im aktuellen WLAN auftretender DNS-Fehler unwahrscheinlich.
- Browserzugriff auf Portal/API war auf demselben Gerät zuvor erfolgreich.
- Nächster Diagnosepunkt: Android systemweite DNS-/VPN-/Filter-Ebene (Privates DNS, VPN/Adblock/NextDNS o. ä.) gegen den Browserpfad prüfen.
- Keine Server-/DB-/Sync-Codeänderung auf Verdacht.

### NET-001 / SYNC-DEVICE-001 Praxisnachtrag – Pixel synchronisiert, Poco Erstabgleich langsam
- Pixel synchronisiert inzwischen wieder mit dem NAS. Der frühere `Unable to resolve host`-Fehler ist aktuell nicht mehr reproduzierbar als dauerhafter Blocker; Ursache bleibt offen.
- Poco wurde deinstalliert, neu installiert und neu aktiviert.
- Poco zeigt beim anschließenden ersten Datenabruf nach >3 Minuten weiterhin laufenden Abruf.
- Das ist noch kein bestätigter Sync-Erfolg und noch kein bestätigter Fehler. Vor Änderungen zuerst klären, welche Erstsync-Phasen/Datenmengen tatsächlich laufen und an welcher Phase Zeit verloren geht.
- Keine Neu-Anlage von Firma/Benutzer/Team/Baustelle auf Verdacht.

## BTB-SYNC-001 – Neuer BTB 26.09 fehlt im Portal und verschwindet auf Helfer
Status: **SOURCE-FIX COMMITTED / BUILD + INSTALLATION + E2E OFFEN**

Praktischer Ablauf:
1. Poco nach Neuinstallation/Neuaktivierung: Erstabgleich abgeschlossen, Baustelle erfolgreich heruntergeladen.
2. Pixel: neuer BTB 26.09 angelegt.
3. Portal: weiterhin letzter sichtbarer BTB 25.09, 26.09 fehlt.
4. Poco: 26.09-BTB erscheint kurz, danach nach ca. einer Sekunde „kein BTB vorhanden“.

Zwingende Diagnose vor Änderung:
- betroffene Firma/Baustelle/Team und `active_btb_id`;
- Server-BTB-Datensatz 26.09: ID, Status, Team, client_key, Version, created/updated;
- Change-Feed-Einträge für diesen BTB;
- API-Antwort für Team-BTB-Liste;
- Android-Snapshot/Active-Pointer-Anwendung auf Poco;
- keine Neu-Anlage von Benutzer/Team/Baustelle und kein Lifecycle-Workaround auf Verdacht.

### BTB-SYNC-001 Diagnosefund – Pixel läuft noch 1.3.4
- Gerätestatus aus der Server-DB:
  - Pixel: App-Version `1.3.4`
  - Poco: App-Version `1.3.19`
- Projektziel/aktueller Android-Fachstand: `1.3.19 / 200069`.
- Change-Feed zeigt wiederholte Updates desselben BTB bis mindestens Version 19.
- Bewertung: erst Versionsgleichstand herstellen/prüfen. Kein Server- oder DB-Fix, solange das Hauptgerät noch mit einem älteren Android-Syncvertrag arbeitet.

### Korrektur zu BTB-SYNC-001 Diagnosefund
- Pixel ist laut Geräteanzeige tatsächlich auf `1.3.19`.
- `tb.devices.app_version` ist nur der bei Aktivierung gemeldete Stand und wird bei normalem Sync nicht aktualisiert.
- Die Schlussfolgerung „Pixel läuft noch 1.3.4“ war falsch und wird nicht weiterverfolgt.
- Nächster Schritt bleibt die eigentliche BTB-Diagnose: Team-`active_btb_id`, 26.09-BTB-Datensatz, Change-Feed und API-Snapshot.

### BTB-SYNC-001 Diagnosefund – Server empfängt 26.09-BTB
- Change-Feed bestätigt den BTB `ba2144b7-9ede-4f53-abdc-1accd172e875` mit `work_date=2026-09-26`.
- Mehrere aufeinanderfolgende `UPDATE`-Changes in wenigen Sekunden, sichtbar mindestens Version 7–19.
- Pixel→Server-Transport funktioniert daher grundsätzlich.
- Nächste Abgrenzung: kompakten DB-Check nur für diesen BTB ausführen:
  - Worksite/Team/Lead
  - `teams.active_btb_id`
  - BTB-Status
  - Client-BTB-ID
  - aktueller Header/Version
  - Teilnehmerstatus Pixel/Poco
- Erst danach Portal-/Android-Leseweg bewerten.

### BTB-SYNC-001 Diagnosefund – Server-Lifecycle vollständig korrekt

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

Nächster Schritt: Portal-Team-BTB-Leseweg und Android-Snapshot/Active-Pointer gezielt prüfen.

### BTB-SYNC-001 Portal bestätigt
Nach Seitenneuladen zeigt das Portal den aktiven 26.09-BTB korrekt. Damit ist die Kette Pixel → Server → Portal für diesen BTB bestätigt.

Weiter offen:
- Poco sieht denselben aktiven BTB kurz und fällt danach auf „kein BTB vorhanden“ zurück.
- Nächster Diagnosepunkt ausschließlich Android: Server-Snapshot → lokaler BTB-Cache → Active-Pointer → UI-Auswahl.

### BTB-SYNC-001 Poco erneut reproduziert nach Portal-Bestätigung
- Portal zeigt den 26.09-BTB nach Reload korrekt als ACTIVE.
- Poco zeigt denselben 26.09-BTB weiterhin nur kurz und springt danach auf „kein BTB vorhanden“.
- Damit ist der Fehler jetzt eindeutig Android/Poco-seitig reproduziert, nachdem Server-Lifecycle und Portal-Read bestätigt sind.
- Nächster Schritt: Android Active-Pointer-/Marker-/UI-Auswahllogik gezielt im Quellstand prüfen; kein Serverfix auf Verdacht.

### BTB-SYNC-001 Source-Ursache eingegrenzt
- Quellpfad isoliert:
  - `CollabLocalBridge.applyServerStateToLocal()` setzt bei `serverActive=true` den lokalen Active-Pointer korrekt.
  - `BtbModernLifecycleInstaller` fragt über `BtbUiSaveBridge.aktuell()` ungefähr alle 600 ms den aktiven BTB ab.
  - `BtbActiveContext.laden()` kann danach den gerade gesetzten Pointer wieder löschen, wenn `BtbDokumentVersionierung.status(...)=ABGESCHLOSSEN` ergibt.
  - Dieser lokale Dokumentstatus wird dort derzeit geprüft, **ohne einen bereits bestätigten `BtbServerStateStore.active=true` als Vorrang zu behandeln**.
- Das passt exakt zum Praxisbild: 26.09 erscheint kurz, nach etwa einer Sekunde „kein BTB vorhanden“.
- Serververtrag ist bereits kanonisch: solange Server-`active_btb_id` auf den BTB zeigt und Teilnehmer `WORKING` ist, darf ein lokaler/staler Abschlusszustand den Active-Pointer nicht lösen.

Benötigte Android-Anpassung: `BtbActiveContext` muss bei vorhandenem ServerState mit `active=true` den kanonischen Server-Lifecycle respektieren und darf den Pointer nicht aufgrund eines lokalen Abschlussstatus lösen. SERVER ändert dafür keinen DB-/API-Vertrag.


### BTB-SYNC-001 Android-Umsetzung
- Projektsteuerungs-/Vertragsstand aus `e71585cb21fa13c01f184036337d3ea68725f37e` vor Codeänderung in den Android-Branch übernommen.
- Codefix `f57c532863d2f92b81729b9af1466b57a22f8723`.
- `BtbActiveContext` prüft den Server-Snapshot jetzt vor lokalen Abschlussmarkern.
- Server `active=true` hat Vorrang vor lokalen PDF-/Baseline-/Abschlussartefakten.
- Pointerlösung durch diesen Leseweg nur bei `FINALIZED + !active`; ohne Server-Snapshot bleibt Legacy-Verhalten erhalten.
- Keine Poco-Daten gelöscht, keine Neuaktivierung, keine NAS-/Serveränderung.
- Nächster Schritt: Android Studio Build 1.3.19 / 200069 → Pixel/Poco installieren → denselben BTB vom 26.09 erneut prüfen.


## REL-001 – Nächster Android-Build
Status: **VERBINDLICHE BENUTZERENTSCHEIDUNG**

- Der nach BTB-SYNC-001 erzeugte APK-Stand wird **1.3.20 / 200070**.
- `1.3.19 / 200069` bleibt der vorherige Stand und wird nicht für eine neue APK wiederverwendet.
- Für jeden weiteren auslieferbaren Test-/Release-Build muss der `versionCode` erneut steigen.
- Nächster Schritt nach dieser Sicherung: Android-Quellstand und Release-MD auf 1.3.20 / 200070 anheben, dann bauen.


## UPD-001 – Nach Update weiter alte Version / Update verfügbar
Status: **FEHLER REPRODUZIERT / DIAGNOSE VOR FIX**

Benutzerbefund:
- Update wurde ausgeführt.
- „Nach Update suchen“ zeigt danach weiterhin **1.3.19** als installierten Stand.
- Hauptansicht zeigt weiterhin **Update verfügbar**.

Verbindliches Soll:
- Installierte Version/Build kommen aus dem aktuell installierten Android-Package (`PackageManager`/BuildConfig), nicht aus einem stale Geräte-Aktivierungswert.
- Releasekatalog/Serverantwort beschreibt das verfügbare Release.
- Updatehinweis nur, wenn verfügbarer `versionCode` **größer** als der tatsächlich installierte `versionCode` ist.
- Nach erfolgreicher Installation 1.3.20 / 200070 darf 200070 nicht erneut angeboten werden.

Vor Codeänderung prüfen:
1. Wartung/Update-UI: Quelle für „installierte Version“.
2. Startseiten-Banner: Vergleichsquelle und Cache.
3. Server Release-API: published version/build.
4. App-Prozess/PackageManager nach Installer-Rückkehr.


### UPD-001 Ursache / Schutzfix
- Anzeigequelle ist nicht stale: `AppUpdateManager.currentVersionLabel()` nutzt den eingebauten Android-`BuildConfig`.
- Startseite prüft bei `onResume()` erneut; `HttpURLConnection.useCaches=false`.
- Symptom „nach Update weiterhin 1.3.19 + Update verfügbar“ bedeutet deshalb: installierte APK ist weiterhin Build 200069 bzw. das neue Release enthielt nicht die erwartete neue APK.
- Server-Upload validiert APK-Inhalt gegen Release-Metadaten derzeit nicht.
- Android lädt/verifiziert SHA/Größe, aber validiert vor Installation weder Package-ID noch eingebettetes `versionName/versionCode`.

Android-Auftrag:
1. heruntergeladene APK vor Installer mit `PackageManager.getPackageArchiveInfo()` prüfen;
2. Package-ID muss `de.tbhelfer` entsprechen;
3. eingebettete Version/Build müssen exakt dem Server-Release entsprechen;
4. bei Abweichung Installation blockieren und klare Fehlermeldung anzeigen;
5. nächster Build **1.3.21 / 200071**.

Serverbedarf separat dokumentiert: auch der Release-Upload sollte künftig APK-Metadaten gegen Draft-Felder validieren. Keine `server/**`-Änderung im Android-Projekt.


### UPD-001 Android-Fix umgesetzt
- Commit `19d688a4752c98209570e0ff69ccd2eae789541e`.
- `AppUpdateManager.downloadApk()` validiert nach SHA/Größe zusätzlich die interne APK-Metadaten.
- Package muss `de.tbhelfer` entsprechen.
- APK-`versionName/versionCode` müssen exakt dem Release entsprechen.
- Bei Mismatch: Datei löschen, verständliche Fehlermeldung, kein Installerstart.
- Version auf **1.3.21 / 200071** angehoben.
- Nächster Schritt: frisch bauen, exakt diese APK im Portal zu 1.3.21/200071 hochladen und Update-E2E durchführen.


## REL-FILE-001 – Release-MD bei jedem Android-Build
Status: **VERBINDLICHE BENUTZERANFORDERUNG**

- Zu jedem neuen Android-Build gehört zwingend eine passende `.md`-Release-Datei wie bei 1.3.16/200066, 1.3.17/200067 und 1.3.18/200068.
- Ablage im Repository: `projektsteuerung/releases/`.
- Die Datei wird bei jeder Build-/GitHub-Übergabe mitgeführt und dem Benutzer zusätzlich zum Download bereitgestellt.
- `VERSION` und `BUILD` müssen exakt mit `app/build.gradle.kts` und der APK übereinstimmen.
- `PFLICHTUPDATE`, `MINIMUM_BUILD`, `DOWNLOAD_URL` sowie Neu/Verbessert/Behoben werden aus dieser Datei in den Release-Draft übernommen.
- Die Datei kann Server-Metadaten korrekt setzen, ändert aber nicht die in der APK eingebettete Version.
- Deshalb dürfen eine alte APK und eine neue Release-MD niemals gemeinsam veröffentlicht werden.


### UPD-001 Praxisbestätigung
- Benutzer bestätigt: Update mit **1.3.21 / 200071** hat funktioniert.
- Release-MD/APK-Paarung und Android-Schutzfix sind praktisch wirksam.
- UPD-001: **BESTÄTIGT**.
- Nächster fachlicher Test bleibt BTB-SYNC-001 auf Pixel/Poco mit demselben 26.09-BTB.


### BTB-SYNC-001 E2E-Nachtest 1.3.21 fehlgeschlagen
- Benutzer testet 1.3.21 / 200071.
- Pixel: weiterhin Flackern der unteren `BTB Abschluss`-Anzeige trotz Haupt-TB-Rolle.
- Poco: 26.09 erscheint nur kurz, danach wieder `kein BTB vorhanden`.
- Bewertung: vorhandener Active-Pointer-Fix deckt den tatsächlichen Laufzeitpfad noch nicht vollständig ab.
- Keine Poco-Daten löschen, keine Neuaktivierung, keine NAS-/Serveränderung.
- Diagnoseauftrag ANDROID:
  1. alle Schreib-/Löschstellen von `BtbServerStateStore` prüfen;
  2. Mapping lokale `btbId` ↔ Server-BTB-ID ↔ Client-BTB-ID prüfen;
  3. alle Stellen prüfen, die Active-Pointer setzen/lösen;
  4. Lifecycle-Watcher/Bottom-Button auf Wechsel zwischen ServerState und Legacy-Fallback prüfen;
  5. erst nach reproduzierbarer Source-Ursache korrigieren.


### BTB-SYNC-001 zweite Runtime-Ursache und Fix
- Zweiter Pointer-Schreiber gefunden: historischer `BtbPatch200033Installer.startActivePointerGuard()`.
- Dieser Guard läuft alle 450 ms und hatte die Server-vor-Lokal-Vorrangregel noch nicht.
- Er konnte den gerade vom Server gesetzten 26.09-Pointer anhand des lokalen `.inactive_after_completion`-Markers wieder löschen.
- Gleichzeitig überschrieb Patch200033 durch Installationsreihenfolge den modernen Rollen-/Finalize-Click-Handler.

Umgesetzt in `4f0e33a8060d0d98279cfe96aed280fee5c8adcb`:
1. servergebundener Guard wertet `BtbServerStateStore` aus;
2. Server `active=true` → Pointer behalten, stale Inaktiv-Marker löschen;
3. Server `FINALIZED && !active` → Pointer lösen erlaubt;
4. ohne ServerState bleibt Legacy-Verhalten;
5. ModernLifecycleInstaller wird in `HauptActivity` zuletzt installiert.

Neuer Build zwingend: **1.3.22 / 200072**.


### BTB-SYNC-001 1.3.22 Praxisfehler / Serverabgleich
- Benutzer bestätigt: **1.3.22 / 200072 behebt den Fehler nicht**.
- Pixel: `BTB Abschluss` flackert weiterhin trotz Haupt-TB-Rolle.
- Poco: Datum `26.09` erscheint kurz und fällt danach wieder auf `kein BTB vorhanden`.
- Die bisher gefundenen Android-Pointer-/Listener-Konflikte waren real, aber nicht die vollständige Ursache.
- Ab jetzt **kein weiterer Android-Fix auf Verdacht**.
- Nächster Diagnoseauftrag ist der direkte Android↔Server-Abgleich:
  1. Team-/Worksite-Kontext und `active_btb_id`;
  2. Team-BTB-Liste;
  3. BTB-Detailantwort;
  4. Delta-/Change-Feed;
  5. `status`, `active`, `client_btb_id`, `team_id`, `current_user` und Teilnehmerstatus;
  6. Android-Parser/Snapshot-Replacement gegen diese Werte.
- Keine Poco-Daten löschen, keine Neuaktivierung, keine NAS-/Serveränderung auf Verdacht.


## BTB-SYNC-002 – Android-Datenverlust vor Outbox
Status: **P0 / URSACHE BESTÄTIGT / ANDROID-FIX JETZT**

Korrektur zum vorherigen Diagnoseauftrag:
- Der Fehler ist **nicht** zuerst serverseitig zu suchen.
- `SelectiveSyncWorker` lädt bei einem bereits bekannten dirty BTB aktuell zuerst den Serverstand, speichert ihn und ruft `bridge.applyServerStateToLocal(...)` auf.
- Erst **danach** wird `bridge.exportBtbChange(...)` ausgeführt.
- Dadurch kann eine gerade lokal auf dem Pixel gespeicherte Änderung durch den Server-Snapshot überschrieben werden, bevor sie überhaupt als Pending/Outbox-Mutation existiert.
- Praxisbild: Pixel schreibt `ABC` → Portal erhält `ABC` nicht → Portal schreibt `XYZ` → Pixel übernimmt `XYZ` → `ABC` verschwindet.
- Server/API/DB-Vertrag ist für BTB-SYNC-002 **nicht die Ursache**.

Verbindlicher Android-Fix:
1. dirty BTB zuerst aus dem lokalen Fachstand in Pending/Outbox exportieren;
2. danach kanonischen Serverstand/Version laden bzw. innerhalb des bestehenden Mutation-/Konfliktpfads prüfen;
3. solange für denselben `clientBtbId` eine unbestätigte lokale Mutation bzw. ein neuer Dirty-Stand existiert, keinen eingehenden Server-Snapshot still lokal anwenden;
4. bei Versionskonflikt Pending erhalten und explizit den bestehenden Conflict-/Rebase-Pfad verwenden;
5. Server-Snapshot erst nach erfolgreichem Upload/Bestätigung anwenden und dabei einen während des Uploads entstandenen neueren Dirty-Stand schützen.

Neuer Android-Build nach dem Fix: **1.3.23 / 200073**.
1.3.22 / 200072 bleibt der getestete, fehlerhafte Vorgängerstand.

## PORTAL-LIVE-001 – Zuständigkeitsabgrenzung
- Der Portal-Source-Fix über SSE/Change-Feed existiert bereits serverseitig.
- Status bleibt **QUELLSTAND FERTIG / DEPLOY OFFEN / E2E OFFEN**.
- Dass auf dem aktuell laufenden NAS noch F5 nötig ist, wird nicht durch einen weiteren Android-Fix behoben.
- ANDROID ändert dafür keine `server/**`-Datei.


### BTB-SYNC-002 Android-Umsetzung 1.3.23
Status: **SOURCE-FIX UMGESETZT / BUILD OFFEN**

Umgesetzt auf `feature/tb-android-2.0`:
- `SelectiveSyncWorker` wendet für einen dirty BTB keinen Server-Snapshot mehr vor dem lokalen Export an.
- Lokaler BTB wird zuerst mit `exportBtbChange(...)` in Pending/Outbox gesichert.
- Serverversion und Konflikte werden danach im bestehenden `synchronizeServer1BtbPending(...)` / `Server1BtbApi.applyPending(...)`-Pfad geprüft.
- Erst nach konfliktfrei bestätigtem Upload wird der bestätigte Server-Snapshot lokal angewendet.
- `CollabLocalBridge.applyServerStateToLocal()` überspringt BTB-Datei-Apply bei Pending **oder Dirty**.
- Lokaler BTB-Save und Server-Apply sind pro BTB über einen Mutationslock serialisiert.
- `SelectiveSyncStore` schützt Dirty-Generation; ein während des Uploads neu entstandener Autosave bleibt erhalten und erzwingt einen weiteren Worker-Lauf.
- Keine `server/**`-Datei geändert.

Quellstand nach Fachfix/Versionierung: `3038d5107bac8cf53cb59b55353628d95653932a` plus Release-MD-Stand im selben Branch.
Zielbuild: **1.3.23 / 200073**.
Build, Installation und Pixel↔Portal-/Poco-E2E bleiben offen.


## BTB-UI-001 – Pixel Buttonleiste flackert
Status: **URSACHE IM ANDROID-UI-PFAD EINGEGRENZT / FIX NÄCHSTER SCHRITT**

Benutzerbefund:
- Pixel ist Haupt-TB und aktuell allein im aktiven BTB.
- Die Abschlussleiste wechselt zwischen nur `BTB Abschluss` und `PDF Entwurf + BTB Abschluss`.

Quellbefund:
- `BtbModernLifecycleInstaller.startLifecycleWatcher()` setzt in jedem 600-ms-Zyklus zunächst `btnPdf` und `btnPdfFinal` beide auf VISIBLE.
- Im servergebundenen Haupt-TB-Zweig wird `btnPdf` anschließend wieder auf GONE gesetzt.
- Damit widerspricht der Haupt-TB-Zweig dem gewünschten UI-Vertrag und erzeugt konkurrierende Sichtbarkeitszustände.

Verbindliches Soll:
- Haupt-TB + aktiver BTB: `PDF Entwurf` sichtbar + `BTB Abschluss` sichtbar, stabil.
- HELPER: kein PDF-Entwurf; nur rollenbezogene Schichtaktion.
- FINALIZED/read-only: nur erlaubte PDF-Ansicht gemäß Serverrecht.
- Die Sichtbarkeit wird pro Zyklus nur aus genau einem Rollen-/Lifecycle-Zweig gesetzt; keine vorgelagerte pauschale VISIBLE-Schaltung.

Nächster Build nach Fix: **1.3.24 / 200074**.


### BTB-UI-001 Android-Fix umgesetzt
Status: **SOURCE-FIX FERTIG / BUILD OFFEN**
- `BtbModernLifecycleInstaller`: keine pauschale Button-Sichtbarkeit mehr vor der Rollenentscheidung.
- Haupt-TB + aktiver BTB: `btnPdf=VISIBLE`, `btnPdfFinal=VISIBLE`, Text `BTB Abschluss`.
- HELPER: `btnPdf=GONE`, nur Schichtaktion.
- FINALIZED/read-only unverändert nach Serverrecht.
- Commit `7fdb4dad2e72515dcda958c00bc2065c38d9f5da`.
- Version **1.3.24 / 200074**, Release-MD vorhanden.


### BTB-UI-001 abgeschlossen
- Benutzer bestätigt 1.3.24 / 200074 auf dem Pixel: kein Springen mehr.
- Haupt-TB-Ansicht mit PDF Entwurf + BTB Abschluss ist praktisch bestätigt.
- Nächster Gerätepunkt: Poco/HELPER-E2E.


### BTB-SYNC-002 Praxis vollständig bestätigt
- Benutzer bestätigt den Pflichttest vollständig:
  - lokale Pixeländerung wird zum Portal übertragen;
  - Portaländerung kommt zum Pixel zurück;
  - erneute Pixeländerung während laufendem Upload bleibt erhalten.
- BTB-SYNC-002: **BESTÄTIGT**.
- Der P0-Datenverlustpfad ist damit geschlossen und geschützt.
- Nächster Arbeitsblock: Poco wieder regulär als HELPER in den aktiven Team-/BTB-Kontext nehmen und HELPER-E2E prüfen.
- Keine App-Daten löschen, keine Neuaktivierung und kein NAS-Umbau auf Verdacht.


## BTB-EDIT-001 – Android-Auftrag
Status: **NEUE FACHANFORDERUNG / VOR IMPLEMENTIERUNG GESICHERT**

Ziel:
- Ein noch bearbeitbarer BTB-Eintrag oder ein BTB-Foto muss nachträglich vollständig fachlich korrigiert werden können, **ohne Löschen+Neuanlegen als Standardweg**.
- Die bestehende Android-Unterbaustellenwahl bleibt fachlich erhalten:
  **Hauptbaustelle → Freie Strecke → BÜ / weitere Unterbaustellen**.
- Gerätespezifische lokale IDs dürfen nicht die geräteübergreifende Sync-Identität sein.

Mindestens nachträglich änderbar:
- BTB-Punkt **4 / 5 / 6 / 7 / 8**;
- **Firma / Allgemein**;
- **Unterbaustelle/Bauobjekt**;
- Uhrzeit;
- KM;
- Text/Kurztext;
- Foto-Zuordnung.

Verbindliche Android-Regeln:
1. bestehende Einträge/Fotos behalten ihre fachliche Identität;
2. Punkt-/Firmen-/Unterbaustellenwechsel ist eine Mutation desselben Objekts, kein Delete/Create;
3. lokale Marker wie `@ORT=<Room-Long-ID>` und lokale `ortId` sind nur Gerätehilfswerte und keine Sync-Identität;
4. für geräteübergreifende Unterbaustellenzuordnung wird die bereits vorhandene stabile Baustellenbaum-Identität `clientNodeKey` bzw. das vorhandene Worksite-Node-Binding verwendet;
5. Zielstruktur:
   `BTB-Punkt → Firma/Allgemein → Unterbaustelle/Bauobjekt → Eintrag/Foto`;
6. Pixel, Poco und Portal müssen dieselbe fachliche Zuordnung sehen;
7. GPS, Aufnahmezeit und Originalfoto bleiben beim Umhängen eines Fotos unverändert erhalten;
8. FINALIZED/abgeschlossene BTBs bleiben read-only;
9. bestätigter BTB-SYNC-002 Outbox-/Dirty-Schutz bleibt geschützt und darf nicht durch Edit-Mutationen umgangen werden.

Android-Diagnose vor Codeänderung:
- Fundstellen von `@ORT=` und lokalen `ortId`-Overrides;
- Speicher-/Lesemodell Punkt 4 gegenüber Punkt 5–8;
- vorhandene stabile `clientNodeKey`-/Node-Bindings;
- Entry-/Foto-Identitäten und vorhandene Edit-/Sync-Mutationen;
- erst danach gemeinsames strukturiertes Android-Modell festlegen.

Servergrenze:
- Android baut keinen Delete/Create-Workaround.
- Der aktuelle Server kann `section` eines bestehenden `btb_entry` noch nicht per PATCH ändern.
- Für bestehende Fotos fehlt noch ein allgemeiner Metadaten-PATCH zum fachlichen Umhängen.
- Android dokumentiert den exakt benötigten Vertrag; SERVER implementiert die API anschließend im Serverprojekt.

Separat und nicht mit BTB-EDIT vermischen:
- `PORTAL-WEATHER-001`: prüfen, warum `tempC` im aktuellen Portal-BTB fehlt;
- fachlich eindeutiger „kein Niederschlag“-Wert bleibt **0 mm** und darf nicht zu `null` werden.


### BTB-EDIT-001 Diagnose abgeschlossen / Serververtrag jetzt vorhanden
- Android-Istmodell vollständig geprüft.
- Kritische Lücke: `@ORT=<lokale ID>` wird in allen Punkten 4–8 vor dem strukturierten `SectionItem` entfernt; stabile Node-Zuordnung fehlt im lokalen Modell.
- `SectionItem` bekommt als Zielmodell `clientEntryKey`, `firmaId/firmaName`, `worksiteNodeKey` zusätzlich zu Zeit/KM/Text.
- Server-HEAD `cd9b18fc062a7a3d4354c611a949ed3d8408d709` enthält inzwischen Entry-`section`-PATCH und versionierten BTB-Foto-Metadaten-PATCH.
- Der zuvor dokumentierte „Serververtrag noch offen“-Stand ist damit überholt. Kein Delete/Create-Workaround nötig.
- Android-Implementierung darf jetzt auf Identitätserhalt + stabile Node-Keys ausgerichtet werden.
- FINALIZED bleibt read-only; BTB-SYNC-002 bleibt geschützt.


### PORTAL-WEATHER-001 Android-Teilbefund
- Android serialisiert `tempC` grundsätzlich bis in `weatherSlots`/Header-Meta.
- konkrete Android-Teilursache für Niederschlag gefunden: `0.0` wird durch `takeIf { it > 0.0 }` zu `null`.
- 0-mm-Erhalt wird in einem separaten Wetter-Fix korrigiert.
- tempC-Fehlstelle erst mit konkretem Runtime-Payload weiter eingrenzen; nicht mit BTB-EDIT vermischen.


### BTB-EDIT-001 Buildziel 1.3.25 / 200075
Status: **BUILD ANGEFORDERT / VERSION RESERVIERT**

- Benutzerauftrag: **bauen**.
- Letzter bestätigter installierter Build: 1.3.24 / 200074.
- Nächster auslieferbarer Android-Build gemäß REL-001: **1.3.25 / 200075**.
- Enthalten: aktueller BTB-EDIT-001-Android-Quellstand mit stabiler Entry-Identität, `worksiteNodeKey`, Firma/Allgemein und Punkt-4–8-Verschiebung ohne Delete/Create.
- Geschützte Fixes BTB-UI-001 und BTB-SYNC-002 bleiben enthalten.
- Wetterpunkt PORTAL-WEATHER-001 wird bewusst **nicht** in diesen BTB-EDIT-Build hineingemischt.


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


## INTEGRATION-AUDIT-001 – Bilder, BTB-Abschluss, Wetter
Status: **NEUE ANFORDERUNG GESICHERT / QUELLVERGLEICH ANDROID↔SERVER↔PORTAL AUSSTEHEND**

Benutzeranforderung 27.09.2026:
1. **Fotos:** Das Aufnahme-Original bleibt auf dem jeweiligen Handy. Für Server/Portal und andere Handys wird nur eine verkleinerte/komprimierte Sync-Version übertragen; andere Handys laden ebenfalls nur kleine Versionen/Renditions.
2. **BTB-Abschluss bidirektional:** Wird ein bearbeitbarer BTB am verantwortlichen Handy abgeschlossen/finalisiert, muss der kanonische Abschluss automatisch im Portal und auf den anderen Teamgeräten ankommen. Wird der BTB zulässig im Portal abgeschlossen/finalisiert, muss derselbe kanonische Lifecycle automatisch auf Android übernommen werden. Keine voneinander abweichenden lokalen Abschlusszustände.
3. **Wetter:** Im BTB müssen Temperatur **und Regen-/Niederschlagsmenge in mm** erhalten und angezeigt werden. Ein bekannter Wert von **0,0 mm** bleibt 0,0 mm und darf nicht als unbekannt/null verschwinden.

Prüfauftrag vor jeder Codeänderung:
- Server-/Portal-API und Datenmodell gegen Android-Erwartung vergleichen;
- tatsächlichen Foto-Upload/Download und Rendition-Pfad prüfen;
- Finalize-/Change-Feed-/Delta-Apply-Pfade Android→Server→Portal sowie Portal→Server→Android prüfen;
- Wetterfelder Header/Payload/Portal-Renderer/Android-Renderer einschließlich 0-mm-Semantik vergleichen;
- Abweichungen dokumentieren; danach Zuständigkeit ANDROID bzw. SERVER getrennt festlegen.

## INTEGRATION-AUDIT-001 – Quellvergleich 27.09.2026
Status: **AUDIT ABGESCHLOSSEN / ANDROID-ABWEICHUNGEN BESTÄTIGT / FINALIZE-VERTRAG SOURCE-SEITIG PASSEND**

### INT-PHOTO-001
- Server besitzt bereits Bildrenditions: `thumb` 320 px/JPEG72 und `preview` 1280 px/JPEG82.
- Server speichert APP-Uploadbytes unverändert; deshalb muss Android selbst eine kleine Sync-Datei liefern.
- Android lädt aktuell `FotoEntity.dateipfad` direkt hoch; das ist das lokale Original.
- Android lädt fremde Serverbilder aktuell über `/api/v1/files/{id}/content`; `preview/thumb` werden nicht verwendet.
- `Server1FileApi.LinkedFile` übernimmt die vom Server angebotenen Rendition-URLs derzeit nicht.
- Lokales 512-px-Thumbnail existiert, ist aber nur UI-Performanceartefakt und keine geeignete alleinige Sync-Version.
- Zuständigkeit: **ANDROID** erzeugt separate kleine Sync-Datei (Zielvertrag ca. 1920 px, JPEG 80–82 %, keine Hochskalierung) und nutzt für Server→Handy bevorzugt `preview`, für Listen `thumb`.
- Kein Server-Ersatzdownscale für falsch hochgeladene Handy-Originale bauen.
- Separat bleibt BTB-EDIT-001 Foto-Metadaten-PATCH: Server-Endpunkt existiert, Android muss ihn noch verwenden.

### BTB-FINAL-001
- Android-Haupt-TB finalisiert über `POST /api/v1/btbs/{id}/finalize`.
- Portal finalisiert über **denselben** Server-Endpunkt.
- Server setzt `FINALIZED`, löscht `teams.active_btb_id`, erzeugt Abschluss-PDF und emittiert BTB-/FILE-Changes.
- Portal hört auf den bestätigten SSE-Change-Feed und lädt bei BTB/FILE-Changes neu.
- Android hört über Server1-Delta/Worker/Poller; ein BTB-FINALIZE-Event lädt genau den BTB neu, übernimmt `FINALIZED && !active` und macht ihn lokal read-only/inaktiv.
- **Kein Source-Vertragsbruch gefunden.** PORTAL-LIVE-001 bleibt geschützt.
- Offen: gezielter Praxis-E2E A) Android-Abschluss → offenes Portal + anderes Handy, B) Portal-Abschluss → Android + anderes Handy, jeweils inkl. Abschluss-PDF.

### WEATHER-001
- Android-Datenmodell/JSON enthält `tempC`, `niederschlag` und `niederschlagMm`; Deserialisierung erhält vorhandenes numerisches 0.0 korrekt.
- Android→Server transportiert `weather` und `weatherSlots` im BTB-Header.
- Portal liest dieselben Felder, kann Temperatur + `Menge mm` bearbeiten und zeigt bekannte `0 mm` korrekt an.
- Android-Fehler bestätigt: automatische Wetterübernahme verwendet an zwei Stellen `precipMm.takeIf { it > 0.0 }`; damit wird bekannter Wert 0.0 zu `null`.
- Android-Wetter-UI zeigt aktuell nur Zeit/Temperatur/Bewölkung/Niederschlagsart, nicht die numerische mm-Menge.
- Android-PDF/BtbDruckModell lässt `niederschlagMm` ebenfalls aus der Wetterdarstellung weg.
- Zuständigkeit: **ANDROID** muss 0.0 erhalten und mm sichtbar machen. Server/Portal-Vertrag benötigt dafür source-seitig keine neue Felddefinition.
- Alter Praxispunkt `tempC` im Portal bleibt Runtime-Probe: Quellseitig ist keine Verluststelle zwischen Android-Serialisierung und Portalrenderer sichtbar.

## ANDROID 1.3.26 / 200076 – Integrationsfix Bilder/Wetter
Status: **ZIELVERSION GESICHERT / IMPLEMENTIERUNG JETZT**

- Neuer Android-Codeblock für INT-PHOTO-001, BTB-EDIT-001 Foto-PATCH und WEATHER-001.
- Vorheriger Zielbuild 1.3.25 / 200075 hatte keinen erfolgreichen Buildnachweis und bleibt historisch offen/fehlgeschlagen; er wird nicht erneut verwendet.
- Nächster eindeutiger Android-Build ist **1.3.26 / 200076**.
- BTB-FINAL-001 Lifecycle-Code bleibt in diesem Block unverändert; Abschluss wird anschließend gezielt E2E getestet.


## ANDROID 1.3.26 / 200076 – Implementierungsstand 27.09.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECK PASS / BUILD OFFEN**

Umgesetzt:
- gemeinsamer Android-Bildencoder: separate Sync-JPEGs, max. 1920 px, JPEG 82; lokale Originale bleiben unverändert;
- kleine Uploads für BTB-, Mängel- und Abnahmefotos sowie bildbasierte Abnahme-Anlagen; PDF-/Nichtbild-Anlagen unverändert;
- Server→Android-Bildsync verwendet `preview` statt automatischem `/content`-Vollbild;
- bestehende Foto-Mappings schützen beim Umstieg gegen unnötige Dubletten;
- Android-BTB-Foto-PATCH gegen `link_version` implementiert; Konflikt wird nicht still überschrieben;
- Wetter: bekannter Wert 0,0 mm bleibt numerisch erhalten; mm-Anzeige in Android-Wetterzeilen sowie PDF/Druckmodell;
- Version auf **1.3.26 / 200076** angehoben; Release-MD vorhanden.

Nicht geändert:
- BTB-FINAL-001 Lifecycle-Code; Android und Portal bleiben am selben kanonischen Server-Finalize-Pfad;
- keine `server/**`-Datei.

Source-Prüfung:
- 19 gezielte Bild-/Wetter-/Versionsprüfungen: PASS;
- Build-Sourcegenerator-Anker: 14/14 PASS;
- Diff ab Audit-Basis enthält nur erwartete `app/**`- und Projektsteuerungsdateien;
- aktueller Fachcode-HEAD vor Statussicherung: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`;
- für diesen HEAD existiert kein GitHub-Actions-Run;
- kein echter Gradle-Build, keine Installation, kein Geräte-E2E in diesem Chat.

Nächster Schritt:
1. Git Pull;
2. Android Studio: Build 1.3.26 / 200076;
3. APK auf Pixel installieren;
4. Foto-Größe/Rendition, Wetter 0 mm, Foto-PATCH und BTB-Finalize Android→Portal sowie Portal→Android praktisch testen;
5. danach Poco/HELPER.

## AND-BUILD-200076 – lokaler Compilefehler Locale
Status: **REPRODUZIERT / URSACHE EINDEUTIG**

- Benutzer-Build in Android Studio am 27.09.2026 scheitert in `:app:compileDebugKotlin`.
- Fehler: `BautagebuchFragment.kt:1214 Unresolved reference 'Locale'`.
- Betroffen ist die neue Wetter-mm-Formatierung `String.format(Locale.GERMANY, "%.1f mm", it)`.
- Ursache: im kanonischen `BautagebuchFragment.kt` fehlt `import java.util.Locale`; der Buildgenerator kopiert diesen Quellstand korrekt in `build/generated/source/btb200043`, daher erscheint der Fehler dort ebenfalls.
- Kein Server-/API-/DB-Fehler.
- Fixziel: ausschließlich fehlenden Kotlin-Import ergänzen; Wetterlogik und geschützte Sync-/Finalize-Pfade nicht verändern.
- Version bleibt **1.3.26 / 200076**, da kein erfolgreicher APK-Build/keine Installation vorliegt.

## AND-BUILD-200076 – Locale-Compilefix
Status: **SOURCE-FIX UMGESETZT / BUILD ERNEUT OFFEN**

- Fixcommit: `199df4b149e210194b22b1c84cc1700ac54435c2`.
- Ausschließliche Codeänderung: `import java.util.Locale` in `BautagebuchFragment.kt`.
- `Locale.GERMANY` ist damit im kanonischen Quelltext auflösbar; der Buildgenerator übernimmt den Import beim Kopieren in `build/generated/source/btb200043`.
- Build-Sourcegenerator-Anker nach Fix: **14/14 PASS**.
- Diff gegen vorherigen Checkpoint enthält im App-Code nur diese eine Importzeile.
- Kein `server/**`, kein Sync-/Finalize-/Wetter-Fachverhalten geändert.
- Version bleibt **1.3.26 / 200076**, weil der vorherige Versuch keinen erfolgreichen APK-Build erzeugt hat.
- Nächster Schritt: Git Pull → Android Studio → Build 200076 erneut.

## AND-VER-001 – Benutzerkorrektur
Status: **ZURÜCKZUNEHMEN / NICHT GEWÜNSCHT**

Benutzerentscheidung 27.09.2026:
- Die zusätzliche sichtbare Versions-/Buildzeile auf dem Android-Startbildschirm war **keine Benutzeranforderung** und soll **nicht** eingebaut werden.
- AND-VER-001 wurde vom Assistenten eigenständig ergänzt und wird vollständig zurückgenommen.
- Auch die dabei geänderte Formatierung von `AppUpdateManager.currentVersionLabel()` wird auf den vorherigen Stand zurückgesetzt.
- Die technische App-Version bleibt unverändert **versionName 1.3.26 / versionCode 200076**.
- Release-/Buildnummern bleiben intern für Build, Updateprüfung und Release-MD erhalten; daraus folgt **keine zusätzliche Startseitenanzeige**.


## BRANCH-CLEANUP-001 – GitHub-Branches bereinigen
Status: **PR-BEREINIGUNG ERLEDIGT / BRANCHLÖSCHUNG OFFEN**

Benutzerentscheidung 27.09.2026:
- Die laufende Repository-Struktur soll auf die aktiven Arbeitszweige reduziert werden.
- Dauerhaft benötigt werden als Branches:
  - `main` als Default-/Basisbranch;
  - `feature/tb-android-2.0` für Android;
  - `feature/tb-server-1.0` für Server.
- Veraltete Entwicklungsbranches sollen entfernt werden, sobald keine aktive PR-/Basisabhängigkeit mehr besteht.
- Alter Draft-PR #1 (`kilometrierung-v1`) und alter Draft-PR #5 (`feature/admin-offline-sync`) werden geschlossen.
- Server-Draft-PR #6 wird vor Entfernung von `feature/admin-offline-sync` auf `main` als Basis umgestellt.
- Vollständig überholte Branches: `feature/admin-offline-sync`, `feature/btb-pdf-identity`, `feature/cloudflare-api`, `feature/server-sync-catalog`, `ci/android-1.3.25-base-200075`.
- `feature/device-activation` und `kilometrierung-v1` besitzen historisch eigene Commits; ihre heutigen Funktionen sind im aktuellen Projektstand abgelöst/weiterentwickelt. Vor Branchlöschung bleiben die bekannten Head-SHAs in der GitHub-/PR-Historie nachvollziehbar.
- Keine Android-/Server-Fachcodeänderung durch diesen Aufräumblock.


### BRANCH-CLEANUP-001 Ergebnis 27.09.2026
- Draft-PR #1 (`kilometrierung-v1`) geschlossen.
- Draft-PR #5 (`feature/admin-offline-sync`) geschlossen.
- Server-Draft-PR #6 von `feature/admin-offline-sync` auf `main` umgestellt; bleibt Draft/offen.
- Aktuell einziger offener PR: #6 SERVER.
- Zu löschende Altbranches nach Sicherheitsprüfung:
  - `ci/android-1.3.25-base-200075`
  - `feature/admin-offline-sync`
  - `feature/btb-pdf-identity`
  - `feature/cloudflare-api`
  - `feature/device-activation`
  - `feature/server-sync-catalog`
  - `kilometrierung-v1`
- Sollzustand Branchliste: `main`, `feature/tb-android-2.0`, `feature/tb-server-1.0`.
- Die verfügbare GitHub-Connector-Schnittstelle kann Branch-Refs nicht löschen; die Branchlöschung selbst ist daher noch manuell in GitHub/GitHub Desktop auszuführen.


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

## AND-IMG-2MB – Umsetzungsauftrag 20.10.2026
Status: **IN ARBEIT**. Nutzer bestätigt ersten Umsetzungsblock mit „okay“.
MobileImageSync muss ausschließlich JPEG-Dateien mit 1..2.000.000Bytes zurückgeben, auch bei Cachetreffern. Bei Übergröße Qualität schrittweise senken und danach Abmessungen reduzieren; nie hochskalieren, Original nicht ändern, kein Originalfallback. Bestehende EXIF-Ausrichtung erhalten. Fehler dürfen keine teilweise Datei als gültigen Cache hinterlassen. Quellprüfung/gezielte Regression erforderlich. Keine Serveränderung, keine Versionsanhebung und kein APK-Buildauftrag.


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

## AND-ENTRY-END – Auftrag 20.10.2026
Status: **IN ARBEIT**. Nutzer „okay weiter“ autorisiert nächsten dokumentierten Android-Kompatibilitätsblock.
Eintrags-Endzeit uhrzeitBis additiv für BTB-Punkt4–8 durch Modell, JSON-Speicher, Editor, Serverpayload und PDF/Druckmodell führen. Bestehende zusammengesetzte Legacy-Zeiten HH:mm–HH:mm kompatibel lesen; neue kanonische Darstellung uhrzeit=Beginn/uhrzeitBis=Ende. Fehlendes Ende bleibt Einzelzeit, bewusstes Löschen muss als null zum Server gelangen. Bestehende stabile Entry-/Node-/Firmenidentität, Offline/Outbox, Diff-/Originalschutz und Reihenfolge erhalten. Ende<Beginn bedeutet Folgetag; gleiche Uhrzeit nicht still24h. Kein server/**, keine Versionsanhebung/kein APK-Buildauftrag. Firmenzeitbindung/Fotobindung bleiben eigene Blöcke.

### AND-ENTRY-END – Schutzentscheidung aus Quellprüfung
BtbDokumentVersionierung vergleicht Roh-JSON per SHA-256. Daher darf ein semantisch unveränderter Eintrag nicht allein durch Aufteilen einer Legacy-Zeit oder Hinzufügen eines null-Endfelds den bestehenden PDF-Hash verändern. Gewählte Grenze: beim lokalen Speichern identische Zeiten je stabiler Entry-ID (Legacy ohne ID nur gleicher Abschnitt/Index und gleicher Text/Firma/Node/KM) in der vorhandenen JSON-Form erhalten. Echte Zeitänderung bleibt Änderung. Nur der ausgehende Serverpayload wird kanonisch in uhrzeit/uhrzeitBis umgewandelt; bewusstes Löschen explizit null. Originaldateien, Hashalgorithmus, Abschlussregeln und Outbox-Reihenfolge nicht ändern.


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

## AND-FIRM-TIME – Auftrag 20.10.2026
Status: **IN ARBEIT**. Nutzer „okay“ bestätigt nächsten Firmenzeitbindungsblock.
Aktuelle HEADs Android841d18c0d3cdc9b96860270f05a10161cdbd7987 / Server0f82b42da7412a138bf99b6f80b93031e3b5373e.
Serverregel aus btb_times.py: personalZeitbindung/maschinenZeitbindung REPORT folgt Berichtszeiten, MANUAL bleibt unangetastet. Legacy ohne Flag folgt nur, wenn beide Zeitwerte leer oder je vorhandenem Wert identisch zur bisherigen Berichtszeit; jede individuelle Abweichung bleibt erhalten.
Android soll Flags in Modell/Speicher/Sync und beiden PM-Einstiegen erhalten; Berichtszeitänderungen auf automatisch gebundene Firmen übertragen; bewusste Firmenzeitwahl als REPORT (exakt Berichtspaar) bzw. MANUAL markieren. Explizites MANUAL bleibt bei bloßem Laden/Speichern auch dann bestehen, wenn Werte zufällig Berichtszeiten entsprechen. Neue Firmen standardmäßig Bericht statt zufälliger Jetzt/+10h-Zeiten, bestehende leere/manuelle Werte beim Laden nicht auffüllen. Vortagsübernahme automatische Zeiten an aktuellen Bericht anpassen, individuelle Zeiten bewahren. Keine neue zusätzliche Modusoberfläche erforderlich. Reiner Flag-/Formatübergang darf vorhandene Abschluss-PDFs nicht ungültig machen; bestehende IDs und unbekannte/unsichtbare Teilsektionsbindung erhalten. Keine server/**-Änderung, keine Versionsanhebung/kein APK-Build.

### AND-FIRM-TIME – ergänzende Schutzgrenzen aus Quellprüfung
Die bisherige PM-Sammlung berechnet Firma-IDs aus Namen neu. Für bereits geladene Firmen wird die vorhandene ID erhalten, damit Bindungskennzeichen und bestehende Entry-/Foto-Firmenreferenzen nicht wegen dieser Anpassung verloren gehen. Bestehende Namens-Zusammenführung und ID-Regel für neue Firmen werden nicht grundlos umgebaut. Teilsektionskennzeichen aus dem geladenen Firmenstand erhalten.
Die bestehende lokale Zeitformat-Sicherung (auch vor Snapshot-Write) wird nur um äquivalente Legacy-Bindungskennzeichen erweitert: unverändertes Zeitpaar, bisher fehlendes/null-Flag und dieselbe implizite Legacy-Semantik => bisherige JSON-Form behalten. Bewusster Wechsel zwischen explizitem MANUAL und REPORT bleibt eine fachliche Änderung, nicht als reine Formatnachlieferung behandeln. Keine Original-/Hashalgorithmusänderung. Programmgesteuerte Uhrzeit-Updates unter pmApplying-Guard; Datum/Schicht-Dialog speichert Bericht+Firmenzeiten gemeinsam über vorhandenen Autosave. Leere autoLinked-Maschinenplatzhalter werden durch automatische Zeiten allein nicht zu echten Maschinenblöcken.


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

## AND-PHOTO-LINK – Auftrag 20.10.2026
Status: **IN ARBEIT**. Nutzer „okay“ bestätigt Foto-Eintragsbindung/GPS-Kompatibilität. Ausgangsstände Android e9e7d20cb999f17a2a2acc16e8109f8688f54554, Server0f82b42da7412a138bf99b6f80b93031e3b5373e.
- Stabile clientEntryKey vom manuellen Eintrag bis Kamera/Galerie weiterreichen; tatsächliche Server-Entry-UUID aus bestätigtem Snapshot zuordnen, nicht aus Text/GPS erraten. Neue Uploads btb_entry_id, bestehende Links versionierter btbEntryId-PATCH; Umhängen/Entkoppeln explizit.
- Server-Metadaten auch für bereits importierte/selbst hochgeladene Fotos aktualisieren. Effektive photo_metadata ist vollständig/autoritativer BTB-Linkstand; gelöschte GPS-Werte dürfen nicht aus Legacy-Dateinamen wiederkehren.
- BTB-bezogene Metadaten als getrennte dauerhafte Korrekturschicht, Original-JPG/Room-Aufnahmebestand unangetastet. Keine Room-Schemaerhöhung: aktuelle Datenbank19 besitzt historische20/21-Kompatibilitätspfade; diese nicht nebenbei umbauen. Nullable GPS/Zeit/Genauigkeit in separater Metadatenschicht darstellen und transportieren, fehlende Aufnahmezeit nicht als „jetzt“ ausgeben.
- Bestätigte lokale Foto-Overrides nach erfolgreichem versioniertem Abgleich nur entfernen, wenn sie seit Export unverändert sind; neuere Benutzereingaben erhalten. Gebundener Kontext folgt Entry, direkte unabhängige Foto-Kontextänderung entkoppelt ausdrücklich.
- PDF/Fotoansicht/Export nutzen effektive GPS; Original bleibt unverändert. Offline-Pending vor Snapshot weiterhin geschützt. Keine Server-/Produktivdatenänderung, keine Versionsanhebung/kein APK-Auftrag. Source-/JVM-Checks von Android-/Geräte-E2E unterscheiden.

## AND-PHOTO-LINK – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Auftrag 0725b1773d811263c913e0e4361af0e5e456c813 source-seitig umgesetzt. Bindung wird vor erstem Galerie-/Kamera-Sync gespeichert; echte Server-UUID aus bestätigtem Snapshot. Pending-Entry ohne bestätigte UUID bleibt offen und liefert verständlichen Sync-Hinweis.
- Stabile clientEntryKey-Zuordnung statt Text-/GPS-Abgleich; bestehende und eigene Fotos übernehmen wirksame Servermetadaten über eine dauerhafte BTB-Korrekturschicht. GPS/Zeit/Genauigkeit können unbekannt bleiben. PDF, Detailansicht und Export verwenden effektive Werte. Originale bleiben erhalten.
- Bestätigte Bindungs-/Kontext-/Textänderungen werden nur bei unverändertem Pendingwert quittiert; spätere lokale Eingaben bleiben erhalten. Unbekannte Unterbaustellen werden nicht still auf Hauptbaustelle umgebogen. Read-only/Lifecycle-/Outbox-/Originalschutz bleibt auf bestehenden Pfaden.
- Grenzen: Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/Server-E2E-Nachweis. Kein APK/Versionssprung, keine Actions gestartet ([skip ci]), kein Servercode geändert. Technischer Stand 1.3.26/200076. Original-EXIF vor Galerie-Konvertierung gelesen; Context7 zu AndroidX ExifInterface ausgewertet, keine neue Bibliothek.


## AND-CATALOG-INVENTORY – Auftrag 20.10.2026
Status: **IN ARBEIT**. Nutzer „okay“ bestätigt Katalog-Bestandsübersicht nach Foto-Block. Android-Ausgang 3d699b17998fed446795a0b1fbbac9d0585225cd, Server 0f82b42da7412a138bf99b6f80b93031e3b5373e.
- Bestehenden gemeinsamen GLOBAL-Bestand über GET /api/v1/catalog/inventory lesend anbinden, gefiltert durch route_number oder station_code. Geräte-Bearer ist laut current_portal_user für diesen Pfad zugelassen; keine Serveränderung.
- In der bestehenden Katalogansicht bei Strecke/Bahnhof „Vorhandene Daten anzeigen“ ergänzen. Objektart (Gleis/Weiche/Weichenverbindung/weitere Daten), Objektkennung, Strecke/Bahnhof und einzelne KM-Abschnitte mit Qualität/fehlender Geometrie darstellen. Getrennte Abschnitte nie zu einem durchgängigen KM-Bereich vereinen; Meter -> Kilometer, einschließlich echtem 0.
- Suche und begrenzte Seitendarstellung innerhalb der geladenen Übersicht. Keine implizite Auswahl, keine Änderung an Baustellen/Katalog/Originalen, keine Änderung an vorhandenen Download-/Offline-/Outboxpfaden. Bestand ist Online-Abfrage; Fehler/fehlender Endpunkt nicht als leerer Bestand ausgeben. Bahnhof ohne stabilen Code nicht über den Namen erraten.
- Dialog lädt erst auf bewusste Aktion; Schließen/Lifecycle beendet ausstehende UI-Aktualisierung. Bestehende Route/Bahnhof-IDs bleiben unverändert. Kein neuer Cache/Room-Schema, keine neue Bibliothek, kein APK und keine Versionsänderung. Quellprüfung getrennt von Build/Geräte-E2E berichten.

## AND-CATALOG-INVENTORY – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Auftrag 21a95890da69386ee33ab43de69e439550e72a88 source-seitig umgesetzt. Fünf Quelldateien: SyncCatalogApi erweitert, SyncKatalogFragment um Menüaktion ergänzt, CatalogInventory/CatalogInventoryDialog/CatalogInventoryTest neu. Umfang bewusst lesende Übersicht im vorhandenen Katalog; keine implizite Objekt-/KM-Übernahme und kein neuer Syncweg.
- Bestand rein lesend aus gemeinsamem GLOBAL-Pool, eindeutige Kennungen, einzelne belastbare KM-Abschnitte; kein Zusammenziehen über Lücken. Fehler bleiben Fehler, kein falscher Leerbestand. Bahnhof ohne Kürzel wird nicht über Namen geraten. Keine Original-/Outbox-/Room-/Lifecycle-Fachänderung.
- Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/NAS-E2E-Lauf; adb/Gradle/Kotlin-Toolchain hier nicht vorhanden. Context7 zu AndroidX Lifecycle/DialogFragment ausgewertet. Keine neue Bibliothek, keine server/**-Änderung, keine Actions gestartet ([skip ci]), keine APK/Versionsänderung. Technischer Stand bleibt 1.3.26/200076; spätere APK braucht höheren Build und passende Release-MD.


## AND-CATALOG-FORM – Auftrag 20.10.2026
Status: **IN ARBEIT**. Nutzer „okay“ bestätigt gezielte Objekt-/KM-Übernahme im Android-Baustellenformular sowie Freie Strecke ohne Bahnhof. Ausgang Android 960f8ff2a2cf652be77d196acac72c000a4f1eb4, Server 0f82b42da7412a138bf99b6f80b93031e3b5373e.
- Bestehende lokale Bahnhofsuche und Objektartwahl erweitern. Gemeinsamer Bestand über vorhandenes inventory; Bahnhof ergänzend bewusst über lookup suchen. Eindeutiger station_code bzw. exakter Assettreffer, kein erster unscharfer Treffer als Identität. Legacy/manuelle Eingabe und Offline-Speichern bleiben möglich.
- Bestandsdialog erhält getrennte Aktionen Objekt übernehmen / KM übernehmen. object_key vor track_key, Objektart Gleis/Weiche/WVB sichtbar; WVB künftig regulär wählbar statt nur versteckter Altbestand. DKW/Kreuzung bleibt erhalten. Jeder einzelne belastbare KM-Abschnitt wird nur nach Klick übernommen, nie implizit und nie über Lücken. Resultate nur bei unverändertem Formular-Kontext anwenden; kein Token in Argumenten.
- Bei bewusstem Kontextwechsel alte Bahnhof-/Objekt-/KM-Auswahl verwerfen; Freie Strecke zeigt keinen Bahnhof. Laden/Bearbeiten/State-Restore unter Guard, damit vorhandene Werte nicht als Benutzereingabe gelöscht werden. Keine automatische Bulk-Korrektur bestehender Baustellen.
- WVB-Präfixnormalisierung berichtigen (bisher removePrefix W lässt VB/WV-Rest). Sync object_type additiv senden und beim Lesen beachten, bestehende track_or_switch-Kennung kompatibel behalten. Semantisch gleiches Legacy-Format darf keine künstliche Sync-Mutation erzeugen. Stationfelder freier Strecke beim Schreiben/Lesen ausblenden, keine Room-Migration.
- Keine server/**-Datei, keine neue Bibliothek, kein APK/Versionssprung. Source-/Unit-/Build-/Geräte-E2E-Nachweise getrennt. Bestehende Outbox/Lifecycle/Originale und lokale Fachfunktionen schützen.

## AND-CATALOG-FORM – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Auftrag fd3affd8b8069061817e70b01e8740f0634d1939 source-seitig umgesetzt. Lokale Bahnhofsuche bleibt; optionaler bewusster Server-Lookup per Geräte-Bearer. Nur exakte/bei Namen eindeutige Assetidentität oder ausdrücklich gewählter Servercode, kein erster unscharfer Treffer. Bestandsdialog mit opt-in Objekt/KM-Resultaten; Standardkatalog bleibt ohne Übernahmebuttons.
- Stabile Knoten-/Objektidentität, keine Originaldatei-/Room-Schemaänderung, keine neue Bibliothek und kein Serverumbau. Keine automatische Bulk-Reparatur alter Baustellen. Vorhandene Fach-/Download-/Offlinepfade bleiben Grundlage.
- Kein Kotlin-/Gradle-/Lint-/Emulator-/Geräte-/NAS-E2E-Nachweis; diese Toolchain hier nicht verfügbar. Context7 zu FragmentResult/Lifecycle geprüft. Keine Actions gestartet ([skip ci]), keine APK und keine Versionsänderung. Technischer Stand weiterhin 1.3.26/200076; neue APK nur mit höherem Build und zugehöriger Release-MD.


## AND-BUILD-BUNDLE – Auftrag 22.10.2026
Status: IN ARBEIT. Nutzer „Okay weiter“ bestätigt nächsten gebündelten Android-Prüf-/Buildschritt nach Katalogformular. Ausgang Android ccf4b958cb230c51fd52d781966e3f787e1458c9, Server 0f82b42da7412a138bf99b6f80b93031e3b5373e.
- Lokale Umgebung weiterhin ohne Gradle/Kotlin/adb; vorhandene GitHub-Android-CI für echte Unit-/Debug-/Release-/Lint-Prüfung nutzen. Letzter Lauf38438042956 endete bereits in server/checks/19 an DELETE-Textmarker; alle Buildschritte wurden übersprungen. Kein Buildnachweis.
- Vorhandene Quellvertragsprüfungen vollständig aktiv lassen, als eigenen verpflichtenden CI-Job alle Ergebnisse sammeln. Gradle-/Release-Prüfjob unabhängig davon ausführen; ein fehlgeschlagener Vertragsjob hält Gesamt-CI rot. Keine Tests deaktivieren oder Fehler als Erfolg markieren.
- Testbuild technisch 1.3.27 / 200077 mit passender Release-MD vorbereiten; keine neue sichtbare Buildanzeige. Keine automatische Veröffentlichung/Serverauslieferung. Artefakte nur aus erfolgreichem Paketlauf übernehmen und Teststatus ehrlich fortschreiben.
- Gefundene Android-Build-/Testfehler gesammelt beheben und tatsächlich erneut prüfen. Keine server/**-Änderung; veraltete serverseitige Quellmarker separat dokumentieren statt durch Scheinmarker in Android zu erfüllen. Geräte-/NAS-E2E bleibt eigene Abnahme.

### Buildbefund 22.10.2026 – Wrapper
CI39714417704: Release-MD/Version und JDK21 PASS; Wrapper-Validierung FAIL (unbekannte eingecheckte JAR, SHA256 91d2ce650f47a3458f52f509b02f0f605513b0315952f717db74d9d51e21053e). Gradle weiterhin nicht gestartet. Entscheidung: ausschließlich diese JAR durch unverändertes offizielles gradle/gradle@v9.3.1-Wrapperartefakt ersetzen, bestehende Gradle9.3.1-Verteilung und Validierung erhalten. Kein Allowlist-Bypass. Erneuter Versuch derselben noch nicht erzeugten Test-APK200077. Alle13 Quellprüfungen tatsächlich gelaufen:8 PASS,5 FAIL (13,19,83,85,86), getrennt dokumentieren; Gesamt-CI bleibt rot.

### Buildbefund – Wiederholung39714587995
Offizielle Wrapper-JAR-Prüfung jetzt PASS (SHA256 b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13). Vorheriger Validierungsversuch scheiterte zusätzlich transient an Netzwerkzugriff. Tatsächlicher Buildstart nun durch fehlendes Unix-Ausführungsbit von gradlew blockiert (Exit126). Routinekorrektur: Git-Modus100644->100755; Launcherinhalt unverändert. Noch keine Kotlin-/Unit-Ausführung.

## BTB-AUTO-016 – Auftrag/Festlegung 22.10.2026

**Benutzerbestätigt; technische Umsetzung und sämtliche Tests OFFEN.** Laufenden BTB 16 Stunden ab eingetragenem Beginn automatisch aus der Erfassung nehmen (07:00 → 23:00). Ausstehende Offline-Daten/Fotos sicher nachsynchronisieren; erst bei bestätigter Vollständigkeit endgültiges PDF/Original erzeugen. Kein automatischer neuer BTB. Server koordiniert, Android behandelt auch Offline/Neustart; spätere bewusste Nachbearbeitung ohne erneuten Sofortabschluss. Vollständiger gemeinsamer Zielvertrag: Abschnitt **BTB-AUTO-016** in `05_ANDROID_SERVER_VERTRAG.md`.
- SERVER entwickelt Worker/Status/Abschluss und Vollständigkeitsprotokoll; ANDROID entwickelt Anzeige/Offline-Erfassungsstopp/Outboxbestätigung. Konkrete technische Vertragsfelder und Grenzfallbehandlung vor Implementierung gemeinsam festlegen.
- Genehmigte Ergänzung zur bisherigen manuellen Haupt-TB-Finalisierung, keine zusätzlichen Helferrechte. Bestehende geschützte Funktionen/Originale bleiben geschützt.
- Auftrag hier: Festlegung in beiden Branches dokumentieren; kein Runtimecode/Build/Deployment und keine produktive Datenänderung.


## AND-BUILD-BUNDLE – Zwischenstand 22.10.2026
- Technischer Testbuild1.3.27/200077 samt passender Release-MD vorbereitet. Keine APK erzeugt/übergeben. Kein neuer sichtbarer Versionsbanner.
- CI39714741475 prüft Code a99e09474c09af33aaa30ee10712131216f44a15: Release-Metadaten/JDK21/offizielle Wrapper-JAR PASS; Gradle Unit/Debug/Release noch IN BEARBEITUNG. Kein Kotlin-/Unit-/APK-/Lint-Erfolg bestätigt. Laufende Joblogs sind über Connector nicht abrufbar.
- Reale CI-Blocker korrigiert: unveränderte offizielle gradle/gradle@v9.3.1-Wrapper-JAR (validierte SHA256 b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13) und gradlew-Modus100755. Ein zusätzlicher transienter Wrapper-Netzwerkfehler wurde beim erneuten tatsächlichen Versuch überwunden.
- Alle13 Quellprüfungen ausgeführt:8 PASS,5 FAIL (13,19,83,85,86).13 erwartet alte writeText-Form; die neuen Formatbewahrungs-Schritte haben den danach vorhandenen Sync-Hook nicht entfernt.19 erwartet wörtliches requestMethod=DELETE; tatsächlicher Aufruf reicht DELETE an requestMethod=method weiter.83/85/86 verlangen teils alte Versionen/Server-/UI-Texte. Diese Prüfungen nicht als PASS umgewertet, Gesamt-CI rot. Keine server/**-Änderung.
- Parallel hinzugekommene BTB-AUTO-016-Dokumentation aus fc9e07861a99f0cafc5b80ed8e0c937998d6c37a erhalten. Diese neue 16-Stunden-Automatik ist weiterhin NICHT implementiert und nicht Teil dieses Testbuild-Codes.
- Lauf: https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39714741475 . Abschlussprotokoll und danach Geräte-/NAS-E2E offen.

### Build-Diagnoseentscheidung 22.10.2026
CI39714741475 bleibt mehr als20 Minuten im kombinierten Gradle-Schritt ohne abrufbare Teil-Logs. Für eine begrenzte, aussagekräftige Fortsetzung Bootstrap (--version), Unit/Debug und Release getrennt ausführen, Job auf30 Minuten begrenzen; alle bisherigen Gradleziele und Lint bleiben erhalten. Der neue Diagnose-Lauf ersetzt den laufenden Versuch über bereits bestehende concurrency/cancel-in-progress-Regel; dessen Abbruchprotokoll danach auswerten. Keine Behauptung eines Compiler-/Testfehlers allein wegen Laufzeit. Zielversion200077 unverändert, noch keine APK erzeugt.


## AND-AUDIT-023 – bestätigter Auftrag 23.10.2026
Benutzer bestätigt Vorschläge zur Prüfung Android↔Server: zuerst Synchronisierungsfehler, dann Geräte-/Portal-Anmeldung, Helfer/weitere TBs und Teilnehmerstatus; Katalogabgleich; Einstellungen/Navigation vereinfachen; belegbar unbenutzte Bestandteile prüfen. Keine pauschale Datenlöschung. Servercode bleibt unverändert; notwendige Gegenstellenkorrekturen separat benennen.
- Arbeitsweise ausdrücklich geändert: Übergabe über GitHub/GitHub Desktop; Benutzer baut/installiert in Android Studio. Keine APK-Herstellung und keine automatischen Buildläufe durch diesen Auftrag; lokale Builds mit höherer Version und passender Release-MD vorbereiten. Keine neuen sichtbaren Versionsbanner.
- Erster bestätigter Befund: SyncKatalogFragment.handleCatalogError verwirft die Fehlermeldung, wenn Cache existiert; leere Cachelisten werden irreführend als leerer Serverbestand bezeichnet. Fehler beim Abschnitt-/Objektabruf werden mit Cache ebenfalls verdeckt.
- Erster Block: Abrufversuch und echten Fehler neben Cachezeit anzeigen; Cache/Server-Leerzustände unterscheiden; erneuten Versuch anbieten. Konkrete Netzwerkursache und Gerät/NAS-E2E weiterhin offen. Geräteaktivierung, Outbox, Originaldaten und server/** nicht auf Verdacht verändern.

### AND-AUDIT-023 – weitere Quellbefunde und Korrekturentscheidung
- TeamManagementApi.loadServerWorksiteContexts behandelt auch ein 403 beim Mitgliederabruf als SessionInvalid; Verbraucher löschen den Geräte-Token. Weitere pauschale 403-Zweige in Katalog/Detail/Änderungen/Profil/Legacy-Einladung. Gezielt korrigieren: 401 bleibt bisherige ungültige Sitzung; 403 nur bei bestätigtem DEVICE_REVOKED/USER_DISABLED/USER_EXPIRED als SessionInvalid (wie bereits Server1BtbApi), andere 403 bleiben fachliche Fehler. Serverautorisierung bleibt unverändert, keine zusätzlichen Rechte. Regressionsfälle ohne Android-Abhängigkeiten testen/vorbereiten.
- StorageCleanupWorker löscht Foto-DB-Einträge bei fehlender Datei und verschiebt Dateien allein nach DAO-Pfadmenge. Das ist kein hinreichender Nachweis unbenutzter Fachreferenzen/Outbox. Für das gewünschte sichere Aufräumen zunächst ausschließlich lesende Speicherprüfung statt automatischer Lösch-/Verschiebeoperationen; Ergebnis sichtbar anzeigen. Keine Fachdatensätze/Originale automatisch bereinigen.
- Einstellungen fachlich gruppieren, bestehende direkte Ziele erhalten; unnötige Zwischenwahl bei Anzeige/Overlay auflösen, keine zweite Team-/QR-Verwaltung. Startmenü Synchronisation klar benennen. Vorhandene Patch-/Generatorquellen wegen produktiver Referenzen nicht pauschal entfernen.

### AND-AUDIT-023 – Offline-Rest nach Helfer-Schichtende
Quellverfolgung zeigt: Server1BtbApi.applyPending liefert für FINISHED/NOT_PARTICIPATING ohne Upload Applied; CollabSyncRepository quittiert daraufhin die lokale Outbox und ersetzt den lokalen Spiegel durch Serverstand. Bei tatsächlich noch ausstehenden Helferänderungen darf das kein Upload-Erfolg sein. Korrektur im bestehenden Helfer-Read-only-Zweig: blockierenden Fehler zurückgeben, Outbox bleibt erhalten, kein Schreibrecht umgehen und keine automatische Wiederaufnahme. Historischen FINALIZED-/Originalschutz unverändert lassen. Bewusste Schichtfortsetzung und gleichzeitige Statusänderung anschließend praktisch testen. Außerdem Katalog-200-Antworten ohne routes/stations nicht als leeren Bestand cachen.

### AND-AUDIT-023 – Katalogliste ist nicht gesamter Vermessungsbestand
Server /api/v1/catalog liest nur catalog_routes/catalog_stations; /catalog/lookup ergänzt Referenzbahnhöfe und GLOBAL-Vermessungsbestand, /catalog/inventory liefert dessen Details. Daher kann auch eine erfolgreiche Katalogliste weniger Einträge zeigen als Admin-Vermessungsübersicht. Entscheidung: Android erhält im bestehenden NAS-Bereich eine gemeinsame, rein lesende Suche für Strecke/Bahnhof mit Kennzeichnung Referenz/Katalog versus bestätigten Vermessungsdaten; Treffer öffnen vorhandenen InventoryDialog anhand Strecke/Kürzel, niemals mit geratenen Katalog-IDs. Bestehende lokale Download-/Bearbeitungsliste bleibt getrennt erkennbar. Keine neue Server-API und keine automatische Anlage/Übernahme/Downloads.

### AND-AUDIT-023 – Abschluss der Quellinventur / sichere Entfernung
Vollständiger main-Baum mit281 Kotlin-/108 XML-Dateien strukturell erfasst. Drei scheinbar unbenutzte Foto-/BTB-Helfer werden vom Buildgenerator verwendet, bleiben erhalten. BtbEinzelZip hat weder im main-Quellbaum noch Generator oder15 bestehenden Testdateien Aufrufer; alten Einzel-ZIP-Code entfernen, aktuellen Tagesabschluss-Export und vorhandene Dateien unverändert lassen. Ausführliche Befunde/Testgrenzen in uebergaben/ANDROID_Pruefung_2026-10-23.md.
Zusätzliche bedingte Serververtragslücke: gesetztes TB_RECOVERY_ID verlangt X-TB-Recovery-ID/kontrollierten Rebase; Android unterstützt den neuen Wiederherstellungsvertrag noch nicht. 409 sichtbar als blockierenden Wiederherstellungsbedarf erklären, keinen Header automatisch quittieren. NAS-Zustand unbekannt, Ursache der Screenshots damit nicht bewiesen.


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


## AND-RECOVERY-024 – Implementierungsentscheidung nach „okay weiter“ (23.10.2026)
- Bestehender Vollsync vermischt lokale Exporte, Uploads und Serverübernahme. Alte Outbox-/Basisstände dürfen nach Restore nicht durch bloßes Nachsenden der Recovery-ID freigegeben werden.
- Sichere lokale Generationen: bisherige Room-Datenbanken, Geschäftseinstellungen, BTB-/Foto-/Dateibestände bleiben vollständig erhalten. Nach erfolgreicher privater Sicherung und isoliertem, authentifiziertem Lesen der aktuellen Servergeneration zeigt die App ausstehende lokale Änderungen und den Serververgleich.
- Bewusste Benutzerentscheidung „Serverbestand neu einlesen“ eröffnet einen neuen leeren lokalen Arbeitsbereich mit neuer Datenbank-/Datei-/Geschäftseinstellungsnamespace. Gerätebindung und aktivierte Serveradresse bleiben; kein Datenlöschen, keine Neuaktivierung und kein blindes Importieren der alten Outbox. Alte lokale Änderungen bleiben im gesicherten Altbestand zur Prüfung/Weitergabe; keine automatische fachliche Zusammenführung oder Erfolgsquittierung dieser Änderungen behaupten.
- Normaler Netzverkehr wird vor tatsächlichem HTTP-I/O zentral gegen den aktuellen Recovery-Stand geprüft. Abweichende/unbekannte Generation sperrt normale Fachreads/-writes; ausschließlich expliziter isolierter GET-Vergleich erhält die aktuelle Kennung. Normaler Schreibzugriff erst in der ausdrücklich gewählten neuen lokalen Generation; bei erneutem Restore wieder sperren. HTTP409 wird niemals durch automatischen Wiederholungsupload umgangen.
- Generationswechsel erfordert Prozessneustart, damit alte Room-/DataStore-Instanzen und Hintergrundjobs nicht weiterarbeiten. Alte Jobs abbrechen; Auswahl atomar journalisieren. Abbruch, fehlender Speicher, inkomplette Sicherung, abgelaufene Anmeldung, neue Recovery-ID während Vergleich oder Neustart halten die Sperre aufrecht.
- Öffentlicher Mein-Ordner bleibt persönlicher Exportbereich und kein neuer Sync-Quellbestand; keine öffentlichen Dateien löschen/verschieben. FileProvider nur um private Wiederherstellungs-/Generationspfade ergänzen, keine allgemeinen Rootfreigaben.
- Keine server/**-Änderungen, kein NAS-Schalter und keine neue Server-API. Vorhandene capabilities/recovery_epoch_v1/recovery_id und Geräte-/Fach-GETs nutzen. Auth/Team-/PDF-Rechte unverändert prüfen.
- Kritische Netzwerk-/Generationslogik mit echten JVM-Unit-Tests und CI-Unit/Lint-Prüfung ohne assemble/Release/APK-Upload validieren; keine APK herstellen. Lokale Android-Studio-/Pixel-/Poco-Abnahme bleibt erforderlich. Versionsziel1.3.29/200079 mit passender Release-MD, kein neuer Versionsbanner.


## Abschlussprüfung 200079: Sicherung vor Prozessende

- SharedPreferences.apply() aktualisiert zunächst den Prozessspeicher. Ein Prozessende vor der Sicherung könnte noch nicht auf Datenträger geschriebene Werte verlieren. Deshalb KEIN automatischer Prozessneustart vor der ersten Sicherung.
- Vor der Sicherung normale Fach-IO sperren, alte Fachoberflächen beenden und Worker/Poller abbrechen; aktuelle SharedPreferences-Werte sowie transaktionale Datenbankexporte und stabile Dateikopien sichern. Vor der bewussten Auswahl erneut sichern/vergleichen. Ein Fehler beim Durchlaufen eines Verzeichnisses muss die Sicherung abbrechen, statt still Dateien auszulassen.
- Erst nach vollständiger geprüfter Sicherung, frischem Serververgleich und ausdrücklicher Auswahl atomar eine neue Generation wählen und den alten Prozess beenden. Das bereits festgelegte Neustartgebot für den Generationswechsel bleibt bestehen.


## 2026-10-23 – aktueller Android-Folgeblock 1.3.29 / 200079

- Runtime-Quellstand: `d3df3108fd66920c7c1bc1bfc93d5817eacedf33`; Server-HEAD bleibt `a471996610a27b2d718f9835bf9c07930f29858f`. Keine server/**- oder NAS-Konfigurationsänderung.
- Wiederherstellungssperre vor tatsächlicher Fach-IO; private geprüfte Sicherung, getrennt lesender Serververgleich und ausdrückliche Auswahl einer neuen lokalen Generation implementiert. Prozessneustart erst NACH Sicherung und Auswahl. Alte Dateien/DBs/Einstellungen/Outbox bleiben erhalten; keine automatische Zusammenführung oder Upload-Quittierung alter Ergänzungen.
- CI-Nachweis: [Lauf 39947580253](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39947580253), Android-Compiler und `:app:testDebugUnitTest` erfolgreich: 88 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen. `:app:lintDebug` erfolgreich: 0 Fehler, 391 Warnungen; keine Warnung in neuen recovery-Dateien. JUnit-/Lint-XML aus dem zugehörigen Artefakt geprüft.
- Gesamt-CI weiterhin rot durch fünf bekannte historische Quellvertragsprüfungen (13/19/83/85/86); der Android-Compiler-/Test-/Lint-Job ist grün. Diese Prüfungen wurden nicht durch Versionskommentare oder deaktivierte Checks umgangen.
- Kein assemble, keine APK-Erzeugung/-Auslieferung. Kein Installations-/Pixel-/Poco-/NAS-E2E-Nachweis. Technische Validierung ist keine funktionale Gerätefreigabe.
- Übergabe/Prüfmatrix: `projektsteuerung/uebergaben/ANDROID_Wiederherstellung_200079.md`. Passende Release-Datei: `projektsteuerung/releases/TB-Helfer_1.3.29_200079.md`.

- Umsetzung des autorisierten Recovery-Folgeblocks technisch abgeschlossen; praktische Abnahme offen. Die gewünschte Gesamtprüfung von Helfer/weiterem TB, Handy-/Portal-Anmeldung, Strecken/Bahnhöfen und Einstellungswegen ist weiterhin nicht als vollständiger E2E-Erfolg abgenommen.


## AND-NODE-DELTA-023 – Auftrag 23.10.2026
Status: IN ARBEIT. Nutzerauftrag: schnellen Änderungsabgleich um WORKSITE_NODE erweitern; Bahnhof plus Gleis/Weiche auch mit Kurzname eindeutig anzeigen, bestehende clientNodeKey-/Room-/BTB-/Fotozuordnungen erhalten. Speichern weiterhin nur betroffenen BTB und dessen Bilder übertragen; geänderte Struktur zusätzlich baustellenbezogen abgleichen. Lokales Speichern, laufende Übertragung, Serverbestätigung und Konflikt/Fehler unterscheiden. Offline-Outbox, Versionskonflikte und Recovery-Barriere erhalten. Kein Server-/NAS-Deployment, keine APK/Versionsänderung beauftragt.
- Basis: Android819bdc89f8ae530af0ecb49e379b6d9edf8bb0cb; Servera471996610a27b2d718f9835bf9c07930f29858f; PR11/6 offen. Git-Clone durch Netzregel403 blockiert; Quellen über GitHub-Connector gelesen.
- Befund: Server1DeltaSync verarbeitet BTB/ENTRY/PARTICIPANT/FILE, ignoriert bislang WORKSITE_NODE und bestätigt dennoch den Cursor. WorksiteStructureSync lädt bisher alle gebundenen Baustellen und übernimmt Snapshots ohne generellen Pending-/Paralleländerungsschutz. SelectiveSyncWorker verarbeitet nur BTB-Scopes. Ergänzung scoped und konservativ: keine Upload-Erfolgsanzeige durch reinen Download; keine automatische Konflikt-Rebase im neuen schnellen Pfad.
- Tests: gezielte Delta-/Scope-/Identitäts-/Status-/Konfliktfälle; tatsächliche Toolchain-Verfügbarkeit prüfen. Gerät-/Pixel-/Poco-/NAS-E2E separat offen ausweisen.


## AND-NODE-DELTA-023 – Quellstand / Validierung gestartet (23.10.2026)
Status: QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / BUILD UND E2E OFFEN.
- WORKSITE_NODE im schnellen Feedfilter; ausschließlich betroffene gebundene Baustellenstruktur vor BTB-/Foto-Ortsauflösung lesen. Cursor erst nach erfolgreichem Apply; bei lokalem Dirty-/Pending-/Konfliktstand kein Snapshot-Overwrite und kein Cursorfortschritt für diese Seite.
- Strukturänderungen vor Room-Schreiben persistent baustellenbezogen markieren. Gemeinsamer lokaler Mutex für Schreiben/Apply, Vergleich vor/nach HTTP, generationstreue Quittierung. Bestehende clientNodeKeys/Room-IDs bleiben erhalten. Neue Schlüssel nur für wirklich ungebundene Knoten. Vorhandene Mutation samt Basisversion/Inhalt bleibt bei Retry unverändert; keine automatische Konflikt-Rebase. Bestätigte Serverversionen separat merken; unbekannte/geänderte Basis konservativ blockieren statt raten. Keine Migration/Originaldatenänderung.
- Selektivworker verarbeitet geänderte Struktur zusätzlich, BTB und Medien weiterhin anhand exakt betroffener IDs. Fehlende Server-BTB-ID startet keinen baustellenweiten Medienlauf. Neuer Save während Worker-Abschluss erhält mit APPEND_OR_REPLACE einen Folgelauf. Offline-Scopes bleiben persistent, Recovery-Barriere bleibt aktiv.
- BTB-Status unterscheidet lokal gespeichert, Übertragung ausstehend/laufend, Konflikt/Fehler und generationstreue Serverbestätigung nach BTB+Medien+lokalem Apply. Download-Zeitstempel bestätigt keine lokale Mutation. Gemeinsamer Unterbaustellen-Formatter zeigt Bahnhof plus Gleis/Weiche plus optionalen Kurzname in BTB-Ortswegen/Fotos/Listen.
- Tatsächlich ausgeführt: 16/16 gezielte Quellfluss-/Schutzprüfungen PASS. Zehn JVM-Unit-Tests hinzugefügt; Kotlin-/Unit-/Lint-Ergebnisse noch ausstehend. Kein adb/Gradle/Kotlin lokal. Bestehende Android-CI enthält Unit/Lint ohne assemble/APK; genau einen Lauf mit dem Implementierungscommit anstoßen. Kein Versionssprung, keine APK/Veröffentlichung und kein NAS-Deployment.
- Server zwischenzeitlich6303e25d4e3401f4de4c4f8dba0c05c1b33e604e: Vergleich zum ursprünglichena4719966 geprüft; nur Portal-Anzeige/Tests/Dokumentation, kein API-Vertragsumbau. Android 1.3.29/200079 unverändert; kein neues auslieferbares Paket erstellt.
- Praktische Abnahme OFFEN: PC->Pixel/Poco-Strukturänderung ohne Vollsync, Bahnhof MM Gl4 versus Berchtesgaden Gl4, Kurzname, Weiche, neue/umbenannte/gelöschte Knoten, Offline-Save/Neustart, zweite Eingabe während Upload, parallele PC-Änderung/409, BTB+eigene Bilder ohne fremde Bilder, Wiederherstellungssperre. Fehlender Basisstand alter lokaler Änderungen erfordert bewusste Konfliktprüfung; kein automatisches Überschreiben.


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


## AND-RELEASE-080 – Nutzerbefund 23.10.2026
Status: IN ARBEIT. Nutzer meldet fehlende TB-Helfer_1.3.30_200080.md. Live-Android0886bd538f8534bb53d4330f33ec301a07d7647c geprüft: Datei fehlt (404); Gradle steht ebenfalls noch auf1.3.29/200079. Entscheidung: nächsten lokalen Android-Studio-Build konsistent als1.3.30/200080 vorbereiten und passende Release-MD mit WORKSITE_NODE-/Statusänderungen ergänzen. Keine APK erzeugen, keine CI/Veröffentlichung und kein Deployment. Vorhandene98er Unit-/Lint-Nachweise gelten für Runtimecode60e8dca unter200079; keinen neuen200080-APK-/Gerätenachweis behaupten. Alte Release-Dateien unverändert erhalten.


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


## AND-NEUANLAGE-026 – Nutzerfehlerbericht 26.10.2026
Status: IN ARBEIT / URSACHE OFFEN.
- Neue Baustelle am Haupt-TB-Handy angelegt; laut Nutzer kein Helfer aktiv. Eintrag für Firma „Ist Werner“ (Text „Einbau Weichenantrieb“, Bahnhof MBG · W 99W1, Uhrzeit/KM leer) lässt sich nicht hinzufügen. PDF-Vorschau fehlt; unten nur BTB Abschluss.
- Screenshot: Berchtesgaden GE/WE, 26.10.2026, 07:00–19:00, lokal gespeichert / Übertragung ausstehend. Portal zeigt unter test.test nur GE Immenstadt - Kempten; Identitätsgleichheit Handy/Portal und installierte APK-Version noch unbekannt. Sync dauert laut Nutzer sehr lange.
- Ausgangsstände live geprüft: Android632276026cfa51c6f3c5cc65a1fa090980936233; Server923cf93709fe82dd93072ddfb5836cb3ca7fcd95; PR11 offen, PR6 offen. Ursache anhand UI-/Neuanlage-/Rollen-/Outboxpfad untersuchen; keine Änderungen auf Verdacht, kein Datenreset/Neuaktivierung, keine server/**-Änderung, kein Deployment oder Buildauftrag.

### AND-NEUANLAGE-026 – Quellbefund
- BautagebuchFragment.kt:7257ff erlaubt leere Uhrzeit/KM. Bei gewähltem Ort scheitert Hinzufügen jedoch vor setRawLines, wenn stableNodeKey null liefert; Snackbar „Unterbaustelle konnte nicht stabil zugeordnet werden.“ erscheint am Fragment hinter dem offenen Dialog (Screenshot zeigt nur abgeschnittenes Ende).
- WorksiteStructureSync.kt:97–122 erzeugt den Schlüssel erst bei bestehendem Serverkontext/Bindung. Rein lokale Neuanlage bekommt damit keinen Schlüssel über diesen Pfad.
- BtbModernLifecycleInstaller.kt:490ff: serverState==null + ENTWURF setzt previewButton GONE und zeigt BTB Abschluss. Das entspricht dem Screenshot; kein Nachweis einer Helferrolle. Haupt-TB mit bekanntem Serverstand erhält beide Buttons.
- SelectiveSyncWorker: ungebundene roots/scopes werden mit retryNeeded weiter versucht, ohne Provisionierung; exponentielles Backoff ab30s. Server1AndroidSyncCoordinator beginnt mit vorhandenen Serverkontexten, keine lokale Neuanlage in diesem Pfad.
- BaustelleNeuFragment/BaustellenBaumRepository erzeugen lokal. Expliziter vorhandener Serveranlagepfad: BaustellenTeamDirekt.oeffneEinladen -> provisionAndLoadTeam -> WorksiteProvisioningApi.create -> synchronizeAll -> bind. Damit hängt die hier geprüfte Serveranlage am Einladungsablauf, obwohl Alleinnutzung ohne Helfer möglich sein muss.
- Quellbedingungen belegt, konkrete installierte APK/Bindung/Serverfehler/Identitätsgleichheit Handy und Portal noch nicht verifiziert. GitHubversion1.3.31/200081; Screenshot enthält keine Version. Keine Laufzeitkorrektur auf Verdacht, kein Build/CI/Emulator/Produktivzugriff. adb/Gradle/kotlinc lokal nicht verfügbar.
- Nächster Nachweis: installierte Version/Build sowie vollständige Meldung des bereits laufenden manuellen Syncs; keine Daten löschen und Baustelle nicht doppelt im Portal erstellen. Gemeinsame Korrektur muss unabhängige lokale Eingabe, stabile lokale Identität, Einzelbenutzer-Serveranlage, anschließenden selektiven Sync und Haupt-TB-PDF-Vorschau erhalten.


## AND-BIDIR-026 – erweiterter Prüfauftrag
Nutzer bestätigt per Screenshot installierte Version1.3.31/200081. Erwartung: Baustelle auf Handy oder im Portal anlegen/bearbeiten; automatische Übertragung zum Server und Abruf auf demselben Nutzer-Handy, Portal und berechtigten Helfergeräten. Helferzuordnung auf Handy oder im Portal muss gemeinsam wirksam sein. Offlineänderungen dauerhaft nachholen; keine manuelle Einladung als Voraussetzung für Alleinnutzung. Katalogseite zeigt Synchronisation vollständig12:41 trotz fehlender Baustelle im Portal; Aussagebereich dieser Anzeige prüfen. Auftrag: vorhandene Sende-/Empfangs-/Identitäts-/Mitgliedschafts-/Deltawege auf beiden Branches prüfen, Lücken dokumentieren; kein pauschaler Umbau/Build/Deployment. Servercode nur lesend. Ausgangsstände Android2368fbc, Server923cf93.


## AND-BIDIR-026 – Quellprüfung 26.10.2026
Status: QUELLPRÜFUNG ABGESCHLOSSEN / KORREKTUR UND GERÄTE-E2E OFFEN. Screenshot bestätigt1.3.31/200081. Android2368fbc, Server923cf93 geprüft. Lokale Neuanlage ohne automatische Serverprovisionierung; Node-Key blockiert lokalen Eintrag; lokaler Entwurf ohne PDF-Vorschau. Schneller Androidfeed ohne WORKSITE/MEMBERSHIP/INVITATION und ohne Kontextinitialisierung; lokales Hauptbaustellen-Umbenennen ohne Sendepfad. Unterbaustellen-/BTB-/Foto-Delta und Portal-Detail-SSE existieren, geschützt. Portal-Liste ohne entsprechenden Livepfad. „Neu laden“ startet auch vollständigen Fachworker, doch SUCCEEDED umfasst ungebundene lokale Baustelle nicht; frühere Nur-Katalog-Vermutung korrigiert. Gemeinsame API-/Nutzeridentität vorgesehen, aktuelle UUIDs nicht verglichen. Vollständige Belege, Umsetzungsteilung und offene A–H-Gerätematrix: uebergaben/ANDROID_BIDIREKTIONAL_2026-10-26.md. Keine Runtime-/Versions-/Servercodeänderung, APK, Actions oder NAS-Übernahme/Deployment.


## AND-BIDIR-026 – gemeinsame Implementierung beauftragt 26.10.2026
Nutzer beauftragt ausdrücklich Android UND Server zu programmieren („bitte jetzt beides programmieren“, „okay lege los“). Ausgangsstände Android6a0e7e4 und Server923cf93. Umsetzung: dauerhafte idempotente Einzelbenutzer-Provisionierung ohne Helfer; stabile lokale Node-Keys vor Binding; lokale Haupt-TB-PDF-Vorschau; Kontext-/Teamempfang ohne globalen Medienvollsync; versionierter Baustellenkopf-PATCH mit Konfliktschutz und Portal-Listen-Livepfad; ehrliche Pending-Anzeige. Bestehende IDs, Originale, Rollen, Dirty-/409-/Recovery-Schutz bewahren. Keine Migration, APK-Veröffentlichung oder NAS-Deployment beauftragt. Build/Source-/Geräte-/NAS-E2E getrennt nachweisen. Auftrag umfasst Code- und Dokumentationscommits auf den beiden Arbeitsbranches.



## AND-SYNC-ANZEIGE-026 – beauftragte Umsetzung
Nutzer meldet Baustelle inzwischen im Portal vorhanden (Teilnachweis, keine vollständige E2E-/Versionsbestätigung) und beauftragt verständliche Handy-Anzeige: Server erreichbar/geprüft oder Prüfung fehlgeschlagen/offline; laufender Sende-/Empfangs-/Vollsync-Schritt; tatsächliche Laufzeit/Schrittdauer; ausstehende lokale Arbeit. Ohne gemessene Gesamtmenge keine erfundene Prozent-/Restzeitanzeige, ausdrücklich „Restzeit noch nicht berechenbar“. Umsetzung ausschließlich Android; keine Server-/Recovery-/Transport-/Retryänderung, kein Deployment. Ziel1.3.33/200083 mit Release-MD, Build/Installation getrennt prüfen. Ausgang Android9b083ab / Server65a14b1. Sichtbarer Diagnosebereich aktualisiert sich nur bei geöffneter Seite; zusätzliche lesende Serverprüfung höchstens alle30s, mit kurzem Timeout, nicht als Berechtigungs-/Datenvollständigkeitsnachweis ausgeben.



## AND-ERRORLOG-026 – Umsetzung beauftragt 26.10.2026
Nutzer bestätigt lokales Fehlerprotokoll unter Einstellungen > Wartung & Diagnose, maximal200 Einträge, Wiederholungen zusammenfassen, Zeitpunkt/Bereich/Vorgang/optionaler lokaler Baustellenbezug, verständliche Meldung und sichere technische Codes; Kopieren/Teilen nur durch Nutzeraktion, Löschen mit Bestätigung. Neustartfest; kein Upload/Serverdienst. Syncseite erhält direkten Fehlerlink. Keine Tokens, URLs, Rohantworten, BTB-/Foto-Inhalte oder Stacktraces speichern: aus Fehlermeldungen nur bekannte Fehlercodes/Klassen ableiten, unbekannte Meldungen neutral klassifizieren. Ziel1.3.34/200084 mit Release-MD. Keine Rechteänderung, keine DNS-Ursache behaupten, kein Deployment; bisherige Fehler werden nicht nachträglich rekonstruiert. Quelle Android16dc829, Server65a14b1.


## BTB-INTEGRATION-031 – Korrekturauftrag 31.10.2026
Ausdrücklicher Nutzerauftrag Android UND Server: Auditfehler korrigieren; Handy-BTB als fachliche Vorlage; drei möglichst große Bilder pro PDF-Reihe; schneller nachvollziehbarer Abschluss; Nachbearbeitung, Gesamtexport, Eintragslöschen/Firmenwechsel/Punktwechsel und Fotozuordnung zu Schwellenwechsel prüfen und korrigieren. Stabile IDs, Originale, Helferrechte, Dirty-/Konflikt-/Recovery-Schutz erhalten. Fotozuordnung über Entry-ID; Textgleichheit nur innerhalb desselben BTB/Firma/Ortskontexts und bei eindeutigem Ziel. Kein Deployment/Produktivzugriff. Tests/Build/Geräte-E2E getrennt ausweisen. Basis Android95b9efba/Server65a14b15. Auftrag vor Runtimeänderung gesichert.


## BTB-INTEGRATION-031 – Code umgesetzt, Abnahme offen (31.10.2026)

Android 1.3.35/200085 und passender Serverstand: drei Bildspalten, strukturierter Drucksnapshot für Firma/Ort und Fotoverweise, sofort gespeichertes Eintragslöschen, erhaltene Fotoverknüpfung beim Wechsel von Firma/Ort/Punkt, eindeutiger kontextgebundener Beschreibungsabgleich, ausdrückliche REWORK-Nachbearbeitung ohne Ersetzen einer neuen aktiven Schicht, Abschluss ohne Medienimport, Export mit bestätigter Original/Sync-Zuordnung und Linkmetadaten. Ausdrückliches Lösen wird zusätzlich als entryBindingCleared in wirksamen Foto-Metadaten transportiert; andere Geräte dürfen es nicht automatisch wieder binden. Historische Originale/IDs/Rollen/Outbox-/Konfliktschutz bleiben geschützt.

Nachweise:22 neue Server-Funktionsprüfungen und7 produktive Portal-Guardfälle PASS; vorhandene132/138 je11, Worksite-Head14, Portal152/153 und List158 PASS; Produktions-PDF mit drei Karten in einer physischen Zeile/KM0/GPS-Links PASS. Acht Android-JUnit-Fälle ergänzt, NICHT ausgeführt. Kein Android-SDK/Gradle/Kotlin/ADB; kein PostgreSQL/HTTP-/Geräte-/NAS-E2E. Keine APK, Actions, Veröffentlichung, Produktivübernahme oder Bestandsbereinigung. Abschlussdauer und Wetterabweichung am Gerät weiterhin offen. Kein Status „alles funktioniert“.

Details, genaue Grenzen und Abnahmeschritte: [BTB-INTEGRATION-031](uebergaben/BTB-INTEGRATION-031.md). Android-Release-MD: projektsteuerung/releases/TB-Helfer_1.3.35_200085.md, DOWNLOAD_URL leer. Nächster verbindlicher Schritt: Android-Build/Tests und gemeinsamer Testbetrieb beider Branchstände mit Haupt-TB/Helfer, Änderungen/Löschen/Verschieben, sieben Bildern und zweimaligem Export.


## AND-BTB-KORREKTUR-1102 – Nutzerfehler und Zielverhalten (02.11.2026)
Status: IN ARBEIT / URSACHENPRÜFUNG. Ausgang Android4427459, Serveradd840e. Nutzer meldet: Firma und Unterbaustelle an bestehenden manuellen Einträgen und Fotos lassen sich nicht zuverlässig ändern; Kurz-/Langtext fehlerhaft; Wetter unzuverlässig; BTB-Abschluss nur teilweise. Vorgabe: Kurztext im Bild und BTB, Langtext im BTB; bei Langtexteingabe gewünschten Kurztext ausdrücklich abfragen. Ein später aufgenommenes Foto mit Kurztext Schwellenwechsel soll dem bestehenden Eintrag 08:00–14:00 Schwellenwechsel im selben BTB/Firma/Unterbaustelle zugeordnet werden. Bei mehreren passenden Zielen keine willkürliche Zuordnung. Bestehende IDs/Originalbilder/Offline-Outbox/Rollen-/Konfliktschutz erhalten. Vor Runtimeänderung Ursachen anhand aktueller Bedien-/Speicher-/Syncpfade prüfen; installierter Build und konkrete Wetter-/Abschlussmeldung noch unbekannt. Androidauftrag; Serverbedarf getrennt dokumentieren. Kein Datenreset, Deployment oder Versionssprung beauftragt.


### AND-BTB-KORREKTUR-1102 – Nutzerrückmeldung Version/Wetter/vorzeitiger Abschluss
Nutzer bestätigt installierte Version1.3.34 (Buildnummer noch nicht abgelesen). Wetter: lädt teilweise nicht bzw. Werte falsch; konkretes Datum/Standort/Beispiel offen. Vermutung des Nutzers: bei eingestelltem BTB07:00–19:00 kein früherer Abschluss möglich. Als unbestätigte Reproduktionshypothese prüfen, nicht als nachgewiesene Endzeitsperre behandeln. In bisher gelesenem Android-Lifecycle keine Uhrzeit-Wartesperre gefunden; Server-/Gerätelaufzeit und übrige Guards nicht vollständig ausgeschlossen. Frühzeitigen manuellen Abschluss fachlich ermöglichen; geplantes Ende nicht ungefragt als tatsächliches Ende überschreiben. Keine neue Runtimeänderung aus dieser Rückmeldung.


### AND-BTB-KORREKTUR-1102 – Screenshotbeleg Haupt-TB/Bearbeitung blockiert
Nutzer meldet als TB/Ersteller keine Bearbeitung mehr. Drei Screenshots: aktuelle Baustelle Berchtesgaden GE/WE; aktueller BTB02.11.2026 mit Arbeitszeit07:00–19:00, Verlauf zeigt BTB aktiv/Kein gültiges Abschluss-PDF und BTB bearbeiten; Editor mit bestehenden Arbeiten/Bildern, PDF Entwurf und Abschluss ausstehend. Historische BTBs30./29.10. zeigen Abgeschlossen – nur lesen/PDF vorhanden. Rollenidentität/UUID und tatsächliche Serverantwort nicht aus Screenshots beweisbar. Geprüfte Quellpfade: BtbModernLifecycleInstaller benennt ausstehenden Outboxauftrag, sperrt deswegen nicht pauschal Facheditor; BtbServerStateStore.readOnly hängt von Kontextrechten, bestätigtem FINALIZED/nichtaktiv und Helfer-Teilnahmestatus ab. Kein Beleg für Uhrzeit-Wartesperre oder alleinige Pending-Sperre. Fehler auf Gerät weiterhin offen; als Nächstes Klickverhalten BTB bearbeiten und Fehlerprotokoll desselben Vorgangs erfassen. Keine Rollenfreischaltung/Outboxlöschung/Datenlöschung/Runtimeänderung auf Verdacht.


## AND-ABSCHLUSS-1102 – Android weiterentwickeln, Auftrag gesichert
Nutzer berichtet: erst Portalabschluss löste den Zustand; Handyabschluss soll allein genügen, kein zweiter Portal-Klick. Android-only fortsetzen. Basis6f8785d/Serveradd840e. Befund: LifecycleWorker und kompletter SelectiveSyncWorker halten denselben processMutex; Laufphasen fehlen, Arbeitsfehler werden nur als Pending dargestellt, Netzwerkverweigerung ohne gespeicherten Wartegrund. Exakte damalige Geräteursache nicht bewiesen. Ziel: Abschlussauftrag vor Ausführung dauerhaft sichern; gezielte Abarbeitung und eindeutige Status-/Fehleranzeige, Schutz vor Doppelauftrag/abgebrochenem aktiven POST und verlorener Wiederholung. Bestehende Rollen-, Helfer-, Konflikt-, Dirty-, Recovery- und Serverbestätigungsgates erhalten. Keine Endzeit-Wartesperre hinzufügen; kein vorgetäuschter Offlineabschluss. Erst bestehende Laufwege/WorkManager-Dokumentation prüfen, dann gezielt korrigieren und testen. Kein server/**, Deployment, Datenreset oder Versionssprung. Build-/Geräte-E2E getrennt offen ausweisen.


### AND-ABSCHLUSS-1102 – geprüfte Umsetzungsteilung
TBHelferApp startet bei Start/Resume/Netzrückkehr nur Delta/Selective; Selective.enqueueIfPending prüft keine LifecycleOutbox. Lifecycle.enqueue verwendet globale APPEND_OR_REPLACE-Kette bzw. REPLACE beim Klick. Zielkorrektur: persistenter WorkManager-Retry bleibt, unmittelbarer gezielter Versuch im Anwendungsscope nutzt denselben serialisierten Prozessor und dieselbe changeId; erneuter Klick ersetzt keinen laufenden POST. Start/Resume/Netzrückkehr holen Lifecycle explizit nach. Selektiver Worker gibt zwischen sicheren Abschnitten an interaktiven Abschluss ab, unterbricht keinen laufenden Upload. Phasen aus echten Ausführungsstellen sichtbar; Netz-/Freigabegrund speichern. API-/Sync-Schutz und Erfolg nur nach Serverbestätigung unverändert; kein HTTP-Test ohne passenden Target/Zugangsdaten. Context7 AndroidX-WorkManager-Policy geprüft; ein relevanter CI-Lauf für Kotlin/Unit/Lint vorgesehen, keine APK/Versionsänderung.


## AND-APK-1102 – ausdrücklicher Buildauftrag
Nutzer beauftragt „dann bauen“. Basis91e45bf, Runtime5291d52 bereits Kotlin/122Tests/Lint geprüft. Ziel erster APK-Build des vorbereiteten1.3.35/200085, höher als installierter1.3.34/200084; kein weiterer Versionssprung ohne Bedarf. Lokales SDK/Gradle fehlt. Separaten gezielten GitHub-APK-Packaginglauf vorbereiten, ohne doppelte Unit-/Lintläufe oder Server-Gesamttests. Manifest, Paketname, VersionCode/-Name und apksigner-Zertifikat prüfen; APK und passende Release-MD im selben Job packen. Kein Veröffentlichung/NAS-Deployment. Runner-Debugsignatur wird gegen installierte APK nicht automatisch als updatekompatibel bestätigt; keine Deinstallation/Datenlöschung zur Umgehung. Buildauftrag vor Workflowänderung gesichert.


## AND-MEDIEN-1102 – eng begrenzte Prüfung und Fehlerkorrektur beauftragt
Nutzer bestätigt nur die zuvor besprochenen Bereiche prüfen, belegte Fehler sofort korrigieren, bestehende Funktionen schützen: Gesamtbilderliste (gelegentliche Doppelzeilen bei einer Aufnahme), PDF höchstens3 Bilder/Reihe und Bearbeitungsdialog mit überlagerten Aktionen. Zusätzlicher Screenshot: Gradle Task.project-Konfigurationscache-Hinweis und KSP/AWT-NullPointer-Diagnose sowie14 Kotlinwarnungen. Ausgang Androidc6d4937, Serveradd840e. Ursachen vor Runtimeänderung prüfen, gezielte Regressionen für Identität/Original-Sync-Bindung, Dateien/Links, Fotoanzahl, Raster und Dialogaktionen. Keine automatische Bestandsbereinigung, keine Serveränderung, keine Rollen-/Sync-/Abschlussumbauten. Keine Bibliotheksupdates auf Verdacht. PDF-Befund aus vorigem Prüfblock: beigefügtes Juni-PDF3Spalten, Bild2/7 fehlt; Handyfoto anderer Bericht2Spalten. APKs baut Nutzer lokal mit GitHub Desktop/Android Studio und bisherigem Schlüssel; kein weiterer Cloud-APK-Build oder Versionssprung beauftragt. Status/Commit sichern, tatsächliche Prüfgrenzen nennen.


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


## EINSTELLUNGEN-1103 – Menüvereinfachung Android und Serverportal beauftragt

03.11.2026: Nutzer beauftragt ausdrücklich eine hübschere, einfachere Einstellungsnavigation auf Android UND Server mit freier gestalterischer Umsetzung. Beide Oberflächen sind Teil dieses Auftrags; die Umsetzung erfolgt getrennt auf den jeweiligen Fachbranches, nicht als beiläufige Server-/Datenbankänderung.

Ziel: wenige klar benannte Bereiche, alltägliche Einstellungen direkt, selten benötigte Zusatz-/Wartungsfunktionen nach bewusstem Aufklappen. Android: Konto, Bautagebuch/PDF, Kamera/Fotobeschriftung, Verbindung/Hilfe; Anzeige/Benachrichtigungen/Widget gebündelt. Portal: Profil, Sicherheit und Firmenkontakte übersichtlich trennen; notwendige Admin-Systemfunktionen erreichbar erhalten. Keine neue Baustellen-/Teamverwaltung in Einstellungen und keine wirkungslose Spracheingabe-Option. Nur belegbar überflüssige Einstiegsdopplungen reduzieren; bestehende Funktionen/IDs/Ziele und gespeicherte Werte erhalten.

Grenzen: Darstellung/Navigation/Beschriftungen; kein API-, Rollen-, Sync-, Room-/DB-, Kamera-/PDF-Fachlogikumbau, keine automatische Änderung gespeicherter Werte oder Datenlöschung. Keine Versionserhöhung, APK, Veröffentlichung oder NAS-Deployment. Android-Quell-/Ressourcenprüfung und vorhandene gezielte CI; Server-Template-/JavaScript-/Rollen-/Responsive-Prüfung lokal, keine unnötigen Gesamttests. Geräte-/NAS-E2E nur bei tatsächlichem Nachweis bestätigen. Status IN ARBEIT.
