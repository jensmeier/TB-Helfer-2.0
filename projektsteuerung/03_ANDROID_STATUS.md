# TB-HELFER 2.0 – ANDROID STATUS


## AND-TAGESZIP-1106 – Versionsvergleich abgeschlossen, Absturz offen
Nutzer bestätigt1.3.41/200091 installiert; Tages-ZIP startet und App schließt. Verlauf-Handler in1.3.35–41 identisch. Wesentlicher Umbau ab1.3.37: Vorab-Sync, Gesamtmedien-Normalisierung, gesamte Baustellenbilder/Schichtlisten und XLSX-Prüfung. Lokale Bildbytes werden nun im zusätzlichen Archivpfad direkt und mehrfach vollständig gelesen; frühere normale JPG-Exportkopie verkleinert. Exportdateien1.3.37–41 identisch, gemeinsame Sync-/Application-Änderungen separat. Keine bewiesene OOM-/ZIP-Dublettenursache; neuer Pixel-Logcat nötig. Kein Runtimefix/Build/Rollback/Datenlöschen. Details: [Versionsvergleich](uebergaben/AND_TAGESZIP_VERSIONEN_1106.md). QUELLVERGLEICH ABGESCHLOSSEN / GERÄTE-E2E FEHLGESCHLAGEN / URSACHE OFFEN.


## AND-APK-1106 – 1.3.41 / 200091 gebaut
Status: BUILD BESTANDEN / UNIT UND LINT BESTANDEN / GESAMT-CI ROT / INSTALLATION UND E2E OFFEN.
- Auftrag70ce9d45, Baucommit76f2d5beac8665d2d95b83ba3f7a85df55c2e526. CI42262406014, Androidjob128046177059 SUCCESS:218 Tests/0 Fehler/0 übersprungen, Lint0 Fehler/390 Warnungen; assembleDebug, Manifest und Signatur PASS. Erster Versuch128045457579 vor Kompilierung wegen Gradle-Wrapper-Netzfehler ETIMEDOUT/ENETUNREACH gescheitert; gezielter Jobretry, Sicherheitsprüfung unverändert.
- Quellvertragschecks13/19/83/85/86 weiterhin rot, Original-Logs geprüft. Kein Gesamt-CI-Erfolg.
- APK26946254Bytes; SHA256f7f1f1a64a2ce2d5ff1a780791aaded81b4da878825616b9a1b2fd37483fea60. Manifestde.tbhelfer/1.3.41/200091; Signaturv1/v2 gültig. APK/Release-MD im selben Job gepaart und Downloadhash geprüft (Artefakt13526007653).
- CI-Debugzertifikat64f6f9620fe29180ead46936082b01914f4202f12d12a0bc5bebb83308435ae4 weicht von CI200090 ab. Für Update vorhandenen lokalen Schlüssel verwenden; keine Deinstallation/Datenlöschung. Installierte Signatur unbekannt.
- Foto-Sync-/Share-/Widget-Korrekturen enthalten; bestehende Doppelbilder nicht gelöscht. Verlauf-ZIP und Pixel/Poco/Server-E2E offen. Kein Servercode/Deployment/Katalogrelease. Nächster Schritt: datenbewahrendes Update und Geräteprüfplan in uebergaben/AND_FOTO_KORREKTUR_1106.md.

## AND-FOTO-1106 – Sync validiert, Teilen/Receiver korrigiert
Status: QUELLSTAND FERTIG / KOMPILIERUNG-UNIT-LINT BESTANDEN / GESAMT-CI ROT / GERÄTE-E2E OFFEN.
- Vorhandene Foto-Sync-Korrektur0b1951a0 geprüft; zusätzliche Korrekturca780326: interne Bilder sicher über begrenzten Share-Cache teilen/öffnen, Mehrfachteilen und Foto-Auswahl-ZIP mit Fehlerbehandlung. Android-Application behält System-Basiskontext; bestehende Recovery-Speicherumleitung bleibt durch gezielte Delegation erhalten.
- CI42232622122/Androidjob127942055768 SUCCESS. XML tatsächlich geprüft:218 JVM-Tests PASS (17 Foto-Sync,5 ShareStaging),0 Fehler/übersprungen; Lint0 Error/Fatal,390 Warning. Gesamt-CI wegen bekannter Quellvertragschecks13/19/83/85/86 weiterhin FAILURE; keine Abschwächung.
- Konkreter Verlauf-ZIP-Absturz und vorhandene Doppelpaare weiterhin nicht am Gerät geklärt. Keine Dublette gelöscht, keine Original-/BTB-/Room-Migration. Vorschlag: nur eindeutige Foto-/Serveridentität unter Erhalt sämtlicher BTB-Bezüge zusammenführen; mehrdeutige Zuordnung bewusst entscheiden.
- Server6ea0d987 lesend gegengeprüft, unverändert. Kein APK/Versionssprung/Deployment/Installation. Bestehende Signierung und Daten für späteres Update erhalten.
- [Befunde, Nachweise und Geräteprüfplan](uebergaben/AND_FOTO_KORREKTUR_1106.md). Nächster Schritt: Pixel/Poco eigenes Foto zweimal synchronisieren, Portal-/Helferbild teilen, Widget nach Neustart und gemeldeten Verlauf-ZIP prüfen.

## AND-FOTO-SYNC-1106 – Korrektur zur Prüfung
Status: QUELLSTAND FERTIG / SOURCE-CHECK BESTANDEN / UNIT-LINT-CI OFFEN / E2E OFFEN.
- Auftrag77121b60, vertiefter Befund28bfb6aa. Android-Mediensync verwendet vorhandene Dateien über stabile BTB-/Baustellen-/Teamzuordnung oder geprüften Datei-SHA256; Vollabgleich und FILE-Delta nutzen denselben Resolver. Fehlende Portal-/Helferbilder bleiben Preview-Downloads.
- Ein alter BTB-Snapshot darf eine gerade hochgeladene Fotoidentität nicht entfernen: Unlink verlangt zusätzlich einen entfernten/geänderten Dateilink. Eigene neue Uploads und normale Importe laufen mit Datei-Refresh unter gemeinsamem Coroutine-Mutex.
- 17 JVM-Regressionsfälle ergänzt (Dateireuse, Cacheverlust, fremde Bilder, Hashänderung, alter Snapshot/Unlink, Parallelität/Abbruch). Neun lokale Integrations-Source-Checks PASS; noch kein JVM-/Gradle-/Gerätenachweis dieser Änderung.
- Keine bestehende Dublette gelöscht, keine Room-/API-/Server-/Versionsänderung und kein APK. Share-/Widget-Crashes sowie konkrete ZIP-Ursache bleiben separat offen. Bereits verlorene Zuordnungen nicht durch Bildähnlichkeit rekonstruieren.
- Nächster Schritt: gezielte Android-CI mit Unit/Lint; danach Nutzergerät testen. Aktueller Quellversionswert unverändert1.3.40/200090, keine auslieferbare neue APK erzeugt.

## AND-FOTO-SHARE-ZIP-1106 – Nutzerfehler geprüft, Laufzeitnachweis offen
Status: IN ARBEIT / QUELLBEFUNDE DOKUMENTIERT / NICHT BEHOBEN.
- [Befunde und weiterer Prüfweg](uebergaben/AND_FOTO_SHARE_ZIP_1106.md). Basis Android49c5429d / Server6ea0d987 unverändert; installierter Build noch nicht bestätigt.
- Suche zeigt laut Nutzer Fotopaare, rechte Kopie stürzt beim Teilen ab; Verlauf-ZIP für vermutlich05.11.2026 stürzt ebenfalls ab. Screenshot enthält Buildwarnungen, keinen Crashtrace.
- Belegt: interner Downloadpfad files/server_sync/collab fehlt in FileProvider-Freigaben; Foto-Teilen/-Öffnen ohne Catch. Suchliste führt vorhandene Original-/Download-Datensätze nicht anhand Medienidentität zusammen; alte Exportbereinigung galt nicht dieser Ansicht.
- Konkrete Paaridentität und ZIP-Crashursache am Gerät OFFEN. Keine Speichermangel-/Serverursache ohne Logcat behaupten. Nächster Schritt: Pfade des Paars, installierte Version und getrennte Crashtraces aus Android Studio.
- Keine Runtime-/Serveränderung, Datenlöschung oder APK/Version/CI-Ausführung. Bestehende Originale, Offline-Daten, Zuordnungen und bestätigte Funktionen erhalten.

## AND-SPRACHE-FELDER-1104 – Zeit/KM und Kopfzeile gebaut (04.11.2026)
Status: QUELLSTAND FERTIG / UNIT UND LINT BESTANDEN / BUILD BESTANDEN / GESAMT-CI ROT / E2E OFFEN.
- [Umsetzung, Grenzen und Geräteprüfplan](uebergaben/AND_SPRACHE_FELDER_1104.md). Auftrag930e12cf, Baucommit48d3e7bf38b61e5b8d076418221a76378785c005; Version1.3.40/200090. Server6ea0d987 unverändert.
- Gesprochene Kopfzeile mit Firma, Punkt4–8, Uhrzeit/Bereich und KM/Bereich; optional Textmarker, Originaltext lokal erhalten. Typische Abschnittsfehler wie Besondenheiten berücksichtigt. Bearbeitbare Felder vor ausdrücklicher Übernahme; unklare Angaben bleiben sichtbar, ungültige Feldwerte blockieren.
- Bestehende uhrzeit/uhrzeitBis/kmVon/kmBis samt geschütztem Speicher-/Syncpfad verwendet, keine API-/Serveränderung. Aufnahme/Widget/Voreinstellungen unverändert. Altentwürfe weiter lesbar; manuelle Korrekturen werden nicht erneut zerlegt.
- CI41852652768/Androidjob126652970282 SUCCESS:196 JVM-Tests (25 neue Sprachkopf-Fälle),0 Fehler/übersprungen; Lint0 Error/Fatal,390 Warning, keine neue speech-Kotlin-Issue. assembleDebug erfolgreich. Erster Lauf41852230460 wegen lokal belegtem Uhr-Minuten-Grenzfall durch korrigierten Commit automatisch abgebrochen.
- Quellvertragsjob126652969506 weiterhin FAILURE bei bekannten Checks13/19/83/85/86; Original-Logs geprüft, keine Abschwächung. Gesamt-CI nicht bestanden.
- APK26929813Bytes, SHA25662d4d8e79de26aadc4674a07e695f73b1c722d4a75d01f3e915530f831bcadd5. Manifest/Signatur und heruntergeladenes APK-/Build-MD-Paar geprüft; gemeinsam bereitgestellt.
- CI-Debugsignatur erneut abweichend; Update mit bisheriger lokaler Signierung bauen, keine Deinstallation/Datenlöschung. Keine Installation, Pixel/Poco-/Live-Server-E2E oder Veröffentlichung bestätigt.
- Nächster Schritt: Nutzerbeispiele sprechen, Felder korrigieren/leeren und Entwurf erneut öffnen, dann genau einmal übernehmen; Offline/Neustart/Sync und Server-/Portal-/PDF-Zeit/KM vergleichen. Frühere Widget-/Export-/Serverrenderer-Abnahmen bleiben separat offen.

## AND-SPRACHE-WIDGET-1104 – Punkte1–4 umgesetzt und gebaut (04.11.2026)
Status: QUELLSTAND FERTIG / UNIT UND LINT BESTANDEN / BUILD BESTANDEN / GESAMT-CI ROT / E2E OFFEN.
- [Umsetzung, Grenzen, Nachweise und Geräteprüfplan](uebergaben/AND_SPRACHE_WIDGET_1104.md). Auftrag ae343199; Baucommit fff93819607d313bc664d0c8dc62939e4cec86dc; Version1.3.39/200089. Server6ea0d987 unverändert.
- Widget: eigener Mikrofon-Foreground-Service, Start/Stop, Rot erst bei bereitem Mikrofon, Vibration, Stop-Benachrichtigung; kein automatischer Prüfdialog. Separate Zahl öffnet lokale Entwürfe des aktiven BTB. Berechtigungsansicht nur bei fehlender Freigabe.
- Gemeinsame begrenzte Sprachsession: Teiltext und Abschnitte zusammenhalten, Pausenziel5s, verspätete Rückrufe sperren, Fehlerprotokoll ohne Rohtext/Audio. Sprachdienstabhängige Übergangslücken möglich; Pixel/Poco nicht getestet.
- Firmen nur gegen BTB-Firmen, konservative Ähnlichkeit; Punkte4–8 plus explizite Punktnummern. Alias nur ausdrücklich merken, lokal nach Anmeldung/Baustelle/Firma; Löschfunktion. Bestehender BTB-Prüfdialog/Voreinstellungen und geschützte Übernahme erhalten.
- CI41837314414/Androidjob126599452352 SUCCESS:171 JVM-Tests (33 Sprache),0 Fehler/übersprungen; Lint0 Error/Fatal,390 Warning (2 neue Layouthinweise SmallSp/HardcodedText); assembleDebug bestanden. Zusätzlich41 lokale Java-Prüfungen PASS. Erster Lauf41836472128 mit Compilefehlern dokumentiert und korrigiert.
- Quellvertragsjob126599452848 weiterhin FAILURE bei bekannten Checks13/19/83/85/86; Original-Logs geprüft, keine Abschwächung. Gesamt-CI nicht bestanden.
- APK26913432Bytes, SHA25697e190f1c126f247b2d0ecfe61a09b928325a23614fc6a8151b4f5c0cf7ea639. Manifest/Signatur und identische Build-Release-MD geprüft und gemeinsam bereitgestellt.
- CI-Debugsignatur anders als200088; Update mit bisheriger lokaler Signierung bauen, keine Deinstallation/Datenlöschung. Kein Serverkatalog-Release oder Deployment.
- Nächster Schritt: bestehendes Widget, Freigaben,1–3s Pause/5s Stille, mehrere Entwürfe, Aliasbestätigung/-löschung, Baustellenwechsel und explizite BTB-Übernahme samt Server-Sync auf Pixel/Poco prüfen. E2E OFFEN.


## AND-SPEECH-UI-1104 – direkte Aufnahme und Prüfung gebaut (04.11.2026)
Status: QUELLSTAND FERTIG / UNIT UND LINT BESTANDEN / BUILD BESTANDEN / GESAMT-CI ROT / E2E OFFEN.
- [Umsetzung, Nachweise und Geräteprüfplan](uebergaben/AND_SPEECH_UI_1104.md). Auftrag 8f196209, Baucommit 35241401a698744fd4b6d0d557df844687fc615b; Android 1.3.38 / 200088. Server 6ea0d987 unverändert.
- Widget und BTB-Mikrofon öffnen direkte Aufnahme; technische Einstellungen separat. Anschließend konkreten lokalen Entwurf prüfen, Text/Firma/Punkt/Ort bearbeiten und ausdrücklich übernehmen. Vorhandene Rechte-/Lifecycle-/Outbox-/Speicherschutzprüfungen erhalten.
- CI 41823796209, Androidjob 126553273851 SUCCESS: 154 JVM-Tests (darunter 16 SpeechDraftPolicy), 0 Fehler/übersprungen; Lint 0 Error/Fatal und 388 Warning; assembleDebug bestanden. Quellvertragsjob 126553273292 weiterhin FAILURE bei bekannten Checks 13/19/83/85/86. Gesamt-CI nicht bestanden.
- APK/Release-MD aus demselben Build gemeinsam bereitgestellt und SHA256-Zuordnung geprüft. APK SHA256 58e6be201841fad4b0fc7f16d3bb26f7d1f5806fc649c6d37ab4296ddc07f985; 26878972 Bytes; Manifest de.tbhelfer / 1.3.38 / 200088.
- CI-Debugsignatur erneut anders als voriger CI-Build; bestehende lokale Signierung für Update verwenden. Keine Deinstallation oder Datenlöschung. Keine Installation, Pixel-/Poco-Aufnahme oder Live-Android-Server-E2E bestätigt.
- Nächster Schritt: bestehendes Widget nach Update, BTB-Mikrofon, Berechtigungen/offline, Stop/Sprechpause, Später prüfen/Unterbrechung und genau einmalige Übernahme samt Server-Sync am Gerät prüfen. Export-/Serverrenderer-Befunde bleiben separat offen.



## AND-EXPORT-FIX-1104 – Android umgesetzt und gebaut, Server/E2E offen
Status: QUELLSTAND FERTIG / UNIT UND LINT BESTANDEN / APK-BUILD BESTANDEN / GESAMT-CI ROT / E2E OFFEN.
- [Umsetzung und Nachweise](uebergaben/AND_EXPORT_FIX_1104.md). Auftrag318897e, Vertragd8eab0d, Baucommit9b0571cf5e745179421e987d35a622544e4064c6; Version1.3.37/200087. Server6ea0d987 unverändert.
- Beide ZIP-Einstiege verwenden denselben IO-Export mit Vorab-Sync, Medienidentität und abschließender XLSX-Linkprüfung. Roh-Dateimanifest/CSV korrigiert, Schichtlisten mit BTB-Token, gültiges Abschluss-PDF geschützt, Word-Stand kenntlich gemacht, Scheinfortschritt entfernt.
- CI41794414368: Androidjob126455006956 SUCCESS; tatsächliche XML151 Tests/0 Fehler/0 übersprungen, Lint0 Error/0 Fatal/388 Warning. APK26878939Bytes, SHA256a215370d825f99f112c4f386ca52ee508f3e807b5fb8624d9ea0a702477db0bf; Manifest/Signatur und heruntergeladene APK/MD-Zuordnung geprüft. Quellvertragsjob126455006766 weiterhin FAILURE bei bekannten Checks13/19/83/85/86; keine Abschwächung.
- APK und identische Build-Release-MD zusammen bereitgestellt. CI-Debugsignatur anders als frühere CI-APK200085; für Update mit bestehender Signierung lokal in Android Studio bauen. Keine Deinstallation/Datenlöschung. Kein Gerätetest, Serverkatalog-Release oder NAS-Deployment.
- Offen: SERVER-Gruppierung/Kurz+Lang/Wetter/Kartenspezifikation; Altkopien ohne sichere Bindung; angemeldeter Offline-Gesamtexport ohne Katalog. Pixel/Poco beide Exportwege am selben BTB prüfen; 18→9 Bilder noch nicht auf Nutzergerät bestätigt.

## EXPORT-QUERPRUEFUNG-1104 – belegte Fehler, Korrektur offen (04.11.2026)
Status: QUELLPRÜFUNG ABGESCHLOSSEN / E2E OFFEN. [Vollständiger Prüfbericht](uebergaben/EXPORT_QUERPRUEFUNG_1104.md).
- Androidbasis cc1fa7e, Serverbasis 6ea0d98; Auftrag vor Prüfung in 7078325 gesichert. Keine Runtime-/Serveränderung und kein neuer Build.
- Verlauf-ZIP umgeht GesamtMedien-Bereinigung; Gesamt-XLSX 0/83 vorhandene Bildziele (Tagesliste 9/9); Zeitraum- und Gesamtnummerierung verschieden. Vorherige Exportkorrektur deckt diesen Bedienweg nicht ab.
- PDF wird als vorhandener Serverabschluss kopiert, Word frisch lokal erzeugt. Aktuelle Gruppierung/Notizregel Android gegen Server weiterhin verschieden; Wettertext im Serverrenderer gekürzt.
- 22 bestehende kontrollierte Server-Funktionstests und 1 direkt ausgeführter Renderer-Referenztest PASS. Drei Spalten/GPS im aktuellen Renderer nachgewiesen, nicht im älteren Nutzer-PDF. Kein Gradle-/APK-/Geräte-/Live-NAS-Test; installierter Stand ungeklärt.
- Nächster Schritt: gemeinsamen Android-Exportkern und ein Dateimanifest herstellen; Darstellungsvertrag mit SERVER abgleichen. Kein Original-PDF stillschweigend ersetzen. Fehler bleiben offen.


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
Branch: `feature/tb-android-2.0`
Fachcode-HEAD vor aktueller Statussicherung: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`
Draft-PR: **kein offener Android-Draft-PR**
Version/Zielbuild: **1.3.26 / 200076**

## Aktuell
- Bildtransport source-seitig umgesetzt: Original bleibt lokal; Sync-JPEG max. 1920 px/JPEG82.
- Kleine Uploads: BTB, Mängel, Abnahmen, bildbasierte Abnahme-Anlagen; PDF-/Nichtbild-Anlagen unverändert.
- Mobile Serverbilder: `preview` statt automatischem `/content`.
- BTB-Foto-Metadaten-PATCH via `link_version` source-seitig integriert.
- Wetter: 0,0 mm bleibt numerisch erhalten; mm sichtbar in App/PDF/Druckmodell.
- Version/Release-MD: 1.3.26 / 200076.
- Sourcechecks: PASS; Build-Sourcegenerator 14/14 Anker PASS; Diff ohne `server/**`.
- **BUILD OFFEN / INSTALLATION OFFEN / E2E OFFEN.**

## Geschützt
- BTB-SYNC-002: **BESTÄTIGT**.
- BTB-UI-001: **BESTÄTIGT**.
- UPD-001: **BESTÄTIGT**.
- PORTAL-LIVE-001 ist serverseitig **BESTÄTIGT** und darf Android-seitig nicht als offener F5-Fehler behandelt werden.
- bestätigten Active-Pointer-/Dirty-/Update-Schutz nicht zurückbauen.

## Nächste Schritte
1. Git Pull.
2. Android Studio Build **1.3.26 / 200076**.
3. Pixel installieren.
4. E2E: kleine Bilder + preview, BTB-Foto-PATCH, Wetter 0,0 mm/tempC, Finalize Android→Portal sowie Portal→Android.
5. Poco/HELPER mit demselben erfolgreichen Build.
6. erst danach Gesamtlast/weitere offene Punkte.

Historischer Tracker: `projektsteuerung/archiv/ANDROID_STATUS_ALT_2026-09-26.md`

## Praxisbefund Hauptgerät – DNS/Hostauflösung

Beim Anlegen einer Testbaustelle meldet die App:
`Unable to resolve host "api-v1.tb-helfer.de": No address associated with hostname`.

Nachtest auf demselben Hauptgerät: `https://www.tb-helfer.de` und `https://api-v1.tb-helfer.de/health/live` sind im Browser erreichbar. Damit ist die öffentliche DNS/API-Erreichbarkeit grundsätzlich vorhanden. Nächster Schritt: App-Sync erneut auslösen; nur bei erneutem App-only `Unable to resolve host` den Android-Netzpfad gezielt untersuchen.


## IMG-001 Quellaudit
Status: **FEHLER BESTÄTIGT / IMPLEMENTIERUNG ZURÜCKGESTELLT BIS E2E-PRIO-001**

- `BtbTeamMediaSync` und `CollabLocalBridge` verwenden lokale Originaldateien als Uploadquelle.
- `Server1FileApi` überträgt exakt diese Datei und kennt aktuell keine Rendition-Downloadmethode.
- `synchronizeServer1Attachments()` lädt fehlende Bilder über `/content` vollständig.
- Lokales 512-px-Thumbnail vorhanden, aber nicht im Online-Sync genutzt.
- `FotoEntity` speichert keine GPS-Genauigkeit; BTB-Upload sendet deshalb `gpsAccuracyM = null`.
- Mangel-/Abnahmefoto-Uploads übergeben keine gleichwertigen strukturierten Foto-Metadaten.

Geplanter Android-Fix nach stabilem Hauptgerät-/Poco-E2E:
kleine Sync-JPEG-Datei → Upload; `preview/thumb` für mobile Downloads; Metadaten vollständig.

### Browser-Nachtest NET-001
Beide öffentlichen TB-Helfer-Seiten sind auf demselben Hauptgerät im Browser erreichbar. App-Retest ist der nächste Schritt.

### NET-001 reproduziert
Browserzugriff auf Portal und API-Health funktioniert auf demselben Gerät; `Neu laden` in der App liefert weiterhin `Unable to resolve host "api-v1.tb-helfer.de"`. Damit ist der Fehler app-/Android-netzpfadspezifisch reproduziert. Source-/Runtime-Diagnose erforderlich; keine Neuaktivierung oder Datenlöschung auf Verdacht.

### NET-001 nach Prozessneustart und Mobilfunk
Force-Stop/Neustart der App sowie WLAN aus / Mobilfunk ändern den Fehler nicht. `Unable to resolve host "api-v1.tb-helfer.de"` bleibt reproduzierbar. Als nächstes systemweite Android-DNS-/VPN-/Filtereinstellungen prüfen; App-Daten/Token nicht löschen.

### Praxisnachtrag – Pixel wieder synchron / Poco frisch aktiviert
- Pixel hat inzwischen wieder erfolgreich mit dem NAS synchronisiert; der zuvor reproduzierte DNS-Fehler blockiert den aktuellen Lauf nicht mehr.
- Poco: App gelöscht, neu installiert und neu aktiviert; erster Datenabruf läuft >3 Minuten.
- Nächster Schritt ist kein weiterer Reset, sondern Erstsync-Pipeline/Datenumfang prüfen und Laufzeitphase eingrenzen.

### BTB-SYNC-001 Praxisfehler 26.09
- Poco-Erstabgleich abgeschlossen; Baustelle erfolgreich geladen.
- Nach neuem Pixel-BTB 26.09 erscheint dieser auf Poco kurz und verschwindet danach wieder („kein BTB vorhanden“).
- Android-seitig daher besonders prüfen: Server-Snapshot ersetzt/entfernt lokalen Active-Pointer? FINALIZED-/Statuslogik? Team-/Group-Filter? Kein Fix ohne Serverzustand.

### BTB-SYNC-001 Versionsabweichung auf Testgeräten
Server-Geräteabfrage: Pixel meldet `1.3.4`, Poco `1.3.19`. Damit ist der aktuelle Geräte-E2E gemischt. Installation des aktuellen Zielstands `1.3.19 / 200069` auf Pixel ist vor weiterer Ursachenbewertung zwingend nachzuweisen.

### Korrektur Versionsdiagnose
Pixel läuft nach Benutzerbestätigung ebenfalls auf `1.3.19`. Der Serverwert `1.3.4` stammt aus der Geräteaktivierung und ist nach App-Updates stale. Kein Versionsgleichstandsfehler als Ursache von BTB-SYNC-001 bestätigt.

### BTB-SYNC-001 nach Portal-Bestätigung erneut reproduziert
Poco zeigt den serverseitig und im Portal bestätigten aktiven 26.09-BTB kurz und danach wieder „kein BTB vorhanden“. Android-seitige Ursache jetzt isoliert.

### BTB-SYNC-001 Source-Ursache
`BtbActiveContext.laden()` kann einen serverseitig bestätigten aktiven BTB lokal wieder deaktivieren, weil es den lokalen Dokumentstatus `ABGESCHLOSSEN` höher bewertet als `BtbServerStateStore.active=true`. Der UI-Watcher läuft ca. alle 600 ms; das erklärt das kurze Anzeigen und anschließende Verschwinden. Android-Fix erforderlich; noch kein Build/E2E.


## BTB-SYNC-001 – Active-Pointer-Fix
Status: **SOURCE-FIX COMMITTED / BUILD OFFEN**

- Vorheriger Fehler: `BtbActiveContext.laden()` konnte trotz `BtbServerStateStore.active=true` einen lokalen Abschlussstatus als maßgeblich behandeln und `nachAbschlussInaktivMerken()` auslösen.
- Fix: `f57c532863d2f92b81729b9af1466b57a22f8723`.
- Jetzt gilt für servergebundene BTBs:
  - Server `active=true` → Result bleibt aktiv, lokale Abschlussmarker werden nicht ausgewertet.
  - Server `FINALIZED && !active` → Pointer wird gelöst.
  - anderer vorhandener Serverstatus → kein lokales Abschlussartefakt darf den Server-Lifecycle überstimmen.
  - nur ohne Server-Snapshot gilt die bisherige lokale/Legacy-Abschlusslogik.
- Version: **1.3.20 / 200070**.
- Kein Servercode geändert.
- GitHub Actions: kein Workflow-Run für aktuellen HEAD entstanden, weil die verfügbaren PR-Basen aktuell nicht mergebar sind. Das ist **kein Buildnachweis**.
- Nächster Schritt: lokal in Android Studio 1.3.20 / 200070 bauen, danach Pixel und Poco installieren und BTB 26.09 erneut testen.


## Versionierung – nächster Build 1.3.20 / 200070
- Benutzerentscheidung 26.09.2026: neue APKs dürfen nicht erneut mit bereits verwendeter Buildnummer 200069 erzeugt/hochgeladen werden.
- BTB-SYNC-001 wird als **1.3.20 / 200070** gebaut.
- Build/Installation/E2E bleiben bis zum tatsächlichen Build offen.


### Release-Bump umgesetzt
- Commit `f9a7421ec9265b73ca170d28a1946644527cbe5a`.
- `versionName = 1.3.20`
- `versionCode = 200070`
- neue Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.20_200070.md`
- GitHub-Workflow-Artefakt auf `TB-Helfer-1.3.20-200070` umgestellt.
- Build noch nicht ausgeführt.


## UPD-001 – Updateanzeige stale nach Installation
Status: **REPRODUZIERT / URSACHE OFFEN**

- Updateprozess läuft durch, aber Updateprüfung zeigt weiterhin 1.3.19.
- Startseite zeigt weiterhin „Update verfügbar“.
- Zielstand ist 1.3.20 / 200070.
- Erst prüfen, ob UI/Updateclient die lokale Package-Version oder einen stale Server-/Aktivierungswert verwendet.
- Kein weiterer Versionssprung nur zur Kaschierung dieses Anzeige-/Vergleichsfehlers.


## UPD-001 – Binärdatei gegen Release prüfen
Status: **SOURCE-URSACHE EINGEGRENZT / FIX AUFTRAG**

- Lokale Versionsanzeige basiert auf `BuildConfig`; kein Anzeige-Cache.
- Updatecheck basiert auf dem lokalen `BuildConfig.VERSION_CODE`.
- Fehlerschutz fehlt: APK-Inhalt wird vor dem Installer nicht gegen Release-`versionName/buildNumber` validiert.
- Android-Schutzfix wird in **1.3.21 / 200071** umgesetzt.


## UPD-001 – Schutzfix committed
Status: **BESTÄTIGT**
- Commit: `19d688a4752c98209570e0ff69ccd2eae789541e`.
- `AppUpdateManager` blockiert APKs mit falscher Package-ID oder abweichendem `versionName/versionCode`.
- Zielbuild: **1.3.21 / 200071**.
- Build, Installation und Update-E2E für 1.3.21 / 200071 vom Benutzer bestätigt.


## REL-FILE-001 – Buildbegleitdatei Pflicht
- Ab sofort hat jeder neue Android-Build eine Release-MD unter `projektsteuerung/releases/`.
- Die aktuelle Zielversion 1.3.21 / 200071 besitzt bereits `projektsteuerung/releases/TB-Helfer_1.3.21_200071.md`.
- Bei jeder Übergabe wird diese Datei auch als Download mitgeliefert.
- CI soll VERSION/BUILD der MD gegen `app/build.gradle.kts` prüfen, damit Build und Metadaten nicht auseinanderlaufen.


### REL-FILE-001 CI-Schutz aktiv
- Commit `e76933cd7270da21d8cd251ece1cba87c7db0cf8`.
- Kein Android-CI-Build soll künftig ohne passende Release-MD durchlaufen.
- Artifact-Name und Release-Dateipfad werden aus dem aktuellen `versionName/versionCode` abgeleitet.
- Damit ist die Benutzeranforderung „bei jedem Build/GitHub-Stand Release-Datei mitgeben“ nicht nur dokumentiert, sondern im CI-Paket technisch abgesichert.


### Praxisbestätigung 1.3.21 / 200071
- Benutzer: **hat funktioniert**.
- Build: **ERFOLGREICH**.
- Installation/Update auf Gerät: **BESTÄTIGT**.
- Update-E2E: **BESTÄTIGT**.
- UPD-001 darf ohne neuen reproduzierbaren Fehler nicht wieder geöffnet werden.
- BTB-SYNC-001 / Pixel-Poco-Fach-E2E bleibt offen.


## BTB-SYNC-001 – Praxis 1.3.21 weiterhin fehlerhaft
Status: **REPRODUZIERT / SOURCE-FIX NICHT AUSREICHEND**
- Pixel: `BTB Abschluss` flackert weiterhin.
- Poco: aktiver 26.09-BTB verschwindet weiterhin nach sehr kurzer Anzeige.
- Installierter Stand: 1.3.21 / 200071, Updateweg bestätigt.
- Deshalb keine Versions-/Updateursache mehr.
- Nächste Quellprüfung: ServerState-Persistenz/ID-Mapping/Pointer-Schreiber/Lifecycle-Watcher.


## BTB-SYNC-001 – zweiter Guard-Fix 1.3.22
Status: **SOURCE-FIX COMMITTED / BUILD OFFEN**

- Commit: `4f0e33a8060d0d98279cfe96aed280fee5c8adcb`.
- Ursache Poco: `BtbPatch200033Installer.startActivePointerGuard()` konnte serveraktiven Pointer alle 450 ms über stale Inaktiv-Marker löschen.
- Ursache Pixel-Rollen-/Buttonkonflikt: Patch200033 wurde nach dem modernen Lifecycle installiert und konnte dessen Abschlusslistener überschreiben.
- Fix: alter Guard server-aware + ModernLifecycleInstaller zuletzt binden.
- Version: **1.3.22 / 200072**.
- Direkt geprüfter Quellstand:
  - `BtbServerStateStore` Import vorhanden;
  - `serverState.active` schützt Pointer;
  - `serverFinalReadOnly` löst Pointer;
  - Gradle 1.3.22 / 200072;
  - passende Release-MD vorhanden.
- **Kein Buildnachweis**; GitHub Actions hat für den Fixcommit keinen Run erzeugt.


## BTB-SYNC-002 – P0 Outbox-Reihenfolge korrigiert
Status: **SOURCE-FIX UMGESETZT / BUILD OFFEN**

Ausgangsfehler:
- `SelectiveSyncWorker` konnte einen Server-Snapshot lokal anwenden, bevor der gerade gespeicherte Pixel-Stand in Pending/Outbox exportiert war.
- Dadurch war stiller Verlust lokaler BTB-Fachänderungen möglich.

Android-Fix:
- dirty BTB zuerst `exportBtbChange(...)`;
- kein vorgelagerter `loadBtbAccess(...)` + `applyServerStateToLocal(...)` mehr;
- Upload/Version/Conflict erst danach über den bestehenden Mutation-Pfad;
- `CollabLocalBridge` blockiert BTB-Snapshot-Apply bei Pending oder Dirty;
- lokaler Save und Server-Apply sind pro BTB serialisiert;
- Dirty-Generation verhindert, dass ein während des Uploads neuer Autosave als verarbeitet gilt;
- nach bestätigtem Upload wird der Snapshot nur dann lokal angewendet, wenn der verarbeitete Dirty-Stand noch aktuell ist.

Zielversion: **1.3.23 / 200073**.
Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.23_200073.md`.

Nicht geändert:
- kein `server/**`;
- Server-/DB-Vertrag unverändert;
- PORTAL-LIVE-001 bleibt SERVER: Source-Fix vorhanden, Deploy/E2E offen.

Nächster Schritt:
1. Build 200073;
2. Pixel installieren;
3. Pixeländerung → Portal;
4. Portaländerung → Pixel;
5. parallelen Autosave während Upload prüfen;
6. danach Poco.


## BTB-SYNC-002 – Gerätebestätigung 1.3.23 / 200073
Status: **PIXEL↔PORTAL BASIS-E2E BESTÄTIGT**

- Pixel → Portal: **BESTANDEN**.
- Portal → Pixel: **BESTANDEN**.
- Der frühere Fehler „lokale Pixel-Eingabe erreicht Portal nicht und wird später vom Serverstand überschrieben“ ist im normalen Hin-und-zurück-Test nicht mehr reproduziert.
- Parallel-Autosave/Conflict-E2E bleibt separat offen.
- Poco/HELPER-E2E bleibt separat offen.


## BTB-UI-001 – Abschlussbuttons
Status: **REPRODUZIERT / SOURCE-FIX AUSSTEHEND**

- Pixel als Haupt-TB zeigt wechselnd 1 oder 2 Abschlussbuttons.
- Ursache im aktuellen Quellpfad: Lifecycle-Watcher setzt Buttons zunächst beide sichtbar und blendet `PDF Entwurf` im Haupt-TB-Zweig wieder aus.
- Ziel: Haupt-TB aktiv = stabil `PDF Entwurf` + `BTB Abschluss`.
- Helfer = kein PDF-Entwurf; Rollen-/Schichtaktion separat.
- Neuer Zielbuild: **1.3.24 / 200074**.


## BTB-UI-001 – Fix 1.3.24
Status: **SOURCE-FIX UMGESETZT / SOURCE-CHECK PASST / BUILD OFFEN**
- Haupt-TB-UI zeigt stabil zwei Buttons: `PDF Entwurf` + `BTB Abschluss`.
- Kein vorgelagertes Umschalten auf beide sichtbar und anschließendes Ausblenden mehr.
- HELPER bleibt ohne PDF-Entwurf mit eigener Schichtaktion.
- Version **1.3.24 / 200074**.
- Release-MD: `projektsteuerung/releases/TB-Helfer_1.3.24_200074.md`.


## BTB-UI-001 PRAXIS BESTÄTIGT 1.3.24
- Pixel-Haupt-TB: PDF Entwurf + BTB Abschluss bleiben stabil sichtbar.
- Benutzer bestätigt: kein Springen mehr.
- Pixel-UI-E2E: BESTÄTIGT.
- Poco/HELPER-E2E bleibt offen.


## BTB-SYNC-002 – Praxis vollständig bestätigt
Status: **BESTÄTIGT**

- Pixel ↔ Portal Basissync: **BESTANDEN**.
- Parallel-Autosave während laufendem Upload: **BESTANDEN**.
- Neuere lokale Dirty-Änderung wird nicht durch älteren bestätigten Server-Snapshot verloren.
- Outbox-/Dirty-/Mutationslock-Fix ist praktisch bestätigt.
- Geschützt: nicht ohne neuen reproduzierbaren Fehler ändern.
- Nächster Android-Gerätepunkt: Poco/HELPER-E2E.


## BTB-EDIT-001 – Android-Audit und Zielmodell
Status: **DIAGNOSE ABGESCHLOSSEN / IMPLEMENTIERUNG OFFEN**

Quellbefunde:
- `SectionItem`: nur Zeit/Text/KM.
- `BautagebuchFragment`: technische `@ORT=<Long>`-Marker; `buildEntry()` entfernt sie bei Punkt 4–8.
- Firmenpräfix ist nicht strukturiert im Entry-Modell.
- `BtbPhotoContextOverrideStore`: lokale `ortId`, keine stabile Node-Referenz.
- `WorksiteStructureSync` / `SyncWorksiteNodeBindingEntity`: stabile `clientNodeKey`-Infrastruktur bereits vorhanden.
- `BtbEntrySlotStore`: stabile Server-Slots vorhanden, aber nur parallel zur lokalen Arrayposition.

Android-Ziel:
- Entry: `clientEntryKey`, `firmaId/firmaName`, `worksiteNodeKey`, Zeit, KM, Text; Punkt 4–8 über Zielsektion.
- Foto-Override/Sync: `worksiteNodeKey` zusätzlich/anstatt lokaler `ortId` als kanonische Referenz.
- bestehende IDs bleiben beim Umhängen erhalten.
- alter Local-ID-Pfad wird migriert, nicht als Mehrgerätevertrag weitergeführt.

Serververtrag jetzt source-seitig vorhanden: `cd9b18fc062a7a3d4354c611a949ed3d8408d709`.

## PORTAL-WEATHER-001 – Android-Diagnose 26.09.2026
Status: **TEILURSACHE BESTÄTIGT / tempC-VERLUST NOCH NICHT ANDROID-SEITIG NACHGEWIESEN**

- Androids `BautagebuchSpeicher` serialisiert `weatherSlots[].tempC` und `niederschlagMm`.
- `Server1BtbApi.HEADER_META_KEYS` enthält `weather` und `weatherSlots`; diese Daten sind damit grundsätzlich Teil des Android→Server-Headers.
- Open-Meteo liefert Temperatur und Niederschlag als echte Zahlen; Android rundet `tempC` und setzt es in `WetterSlot`.
- Für bekannten Niederschlag **0.0 mm** existiert jedoch ein konkreter Android-Fehler: Auto-Wetter verwendet `h.precipMm.takeIf { it > 0.0 }`. Dadurch wird genau 0.0 zu `null`.
- Fachregel: Bei erfolgreicher Wetterquelle muss 0.0 als **0.0 mm** erhalten bleiben.
- Dass `tempC` im aktuellen Portal-BTB fehlt, ist nach dem Quellaudit noch nicht als Android-Serialisierungsfehler belegt; hierfür muss der konkrete Server-Header-/Runtime-Payload mit Android-Ausgabe verglichen werden.
- Wetterfix bleibt getrennt von BTB-EDIT-001.


## Buildziel 1.3.25 / 200075
- Benutzer hat Build angefordert.
- Version **1.3.25 / 200075** reserviert.
- Source-Check und echter Build müssen noch durchgeführt werden.
- BUILD ERFOLGREICH erst nach tatsächlichem Gradle/CI-Nachweis.


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

## Integrationsaudit 27.09.2026 – Bilder / Abschluss / Wetter
Status: **SOURCE-AUDIT PASS / ANDROID-ARBEITSPUNKTE IDENTIFIZIERT / KEIN CODE IN DIESEM BLOCK**

- Foto-Sync weicht vom Vertrag ab: Original wird hochgeladen, Serverbilder werden über `/content` statt Renditions geholt. Nächster Android-Block: kleine Sync-JPEG-Datei erzeugen + `preview/thumb` konsumieren.
- BTB-Finalisierung ist source-seitig bidirektional korrekt an den kanonischen Server-Lifecycle angebunden. Kein Umbau auf Verdacht; gezielter E2E beider Richtungen erforderlich.
- Wettermodell transportiert `tempC` und `niederschlagMm`; Android verwirft bei automatischem Wetter jedoch 0.0 mm über `takeIf { it > 0.0 }` und rendert mm weder in der App-Wetterzeile noch vollständig im Android-PDF.
- BTB-EDIT-001 Foto-PATCH bleibt zusätzlicher Android-Restpunkt.
- 1.3.25 / 200075 Buildstatus bleibt unverändert: **BUILD OFFEN**.

## Zielbuild 1.3.26 / 200076
Neuer Android-Codeblock Bilder/Wetter/Foto-PATCH. Buildnummer 200075 wird nicht wiederverwendet. Ziel: **1.3.26 / 200076**. BTB-Finalize-Lifecycle bleibt unverändert; separater E2E danach.

## Android 1.3.26 / 200076 – Bilder / Foto-PATCH / Wetter
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECK PASS / BUILD OFFEN**

- `MobileImageSync`: separate JPEG-Syncdatei im Cache, max. 1920 px, JPEG 82, EXIF-orientiert; Aufnahmeoriginal bleibt unangetastet.
- Kleine Uploads jetzt für BTB-Fotos, Mängelfotos, Abnahmefotos und bildbasierte Abnahme-Anlagen; PDF-/Nichtbild-Anlagen bleiben Originaldateien.
- Server→Android lädt bei Bild-MIME die vorhandene `preview`-Rendition; alte App-Cache-Vollbilder werden bei Bedarf durch mobile Previews ersetzt.
- BTB-Foto-Metadaten-PATCH ist Android-seitig angebunden und versioniert über `link_version`; Parallelkonflikte werden gemeldet.
- Wetter: 0,0 mm wird nicht mehr zu null; bekannte mm-Menge zählt als Wetterwert und wird pro Slot, im Android-PDF und im Druckmodell angezeigt.
- `versionName=1.3.26`, `versionCode=200076`; Release-MD vorhanden.
- BTB-FINAL-001 wurde **nicht** umgebaut; bestehender kanonischer Finalize-Pfad bleibt geschützt.

Prüfung:
- gezielte Source-Checks: PASS;
- Build-Sourcegenerator 14/14 Anker: PASS;
- Diff ohne `server/**`;
- Fachcode-HEAD vor Statussicherung: `17b42917c0439d6fc9540c11fe4a2a9ec2f57914`;
- GitHub Actions für diesen HEAD: kein Run;
- **kein echter Gradle-Build / keine Installation / kein Android-Geräte-E2E**.

## Android 1.3.26 / 200076 – Locale-Compilefix 27.09.2026
- Android-Studio-Build reproduzierbar in `:app:compileDebugKotlin` gescheitert: `Unresolved reference 'Locale'`.
- Ursache: fehlender `java.util.Locale`-Import für die neue mm-Formatierung.
- Source-Fix: `import java.util.Locale` ergänzt.
- Sourcecheck: `Locale.GERMANY` auflösbar; Generatoranker **14/14 PASS**.
- Status: **SOURCE-FIX UMGESETZT / BUILD ERNEUT OFFEN / INSTALLATION OFFEN / E2E OFFEN**.


## AND-VER-001 – zurückgenommen
- Keine zusätzliche Versions-/Buildzeile auf der Startseite.
- Von der nicht beauftragten Änderung betroffene UI-/Formatänderungen wurden auf den vorherigen Stand zurückgesetzt.
- Technischer Zielstand bleibt **1.3.26 / 200076**.


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
- Dauerhafte BTB-Metadatenschicht in SharedPreferences, FotoEntity-Anzeigeangaben mit Room Ignore. Bestehender Room-Konstruktor/DB-Version unverändert; Originaldateien und Aufnahmefelder werden durch Serverkorrekturen nicht überschrieben. Drei Foto-Flows reagieren auf Metadatenänderungen; Unterknotenfilter verwendet wirksamen Ort. Fotoauswahl gruppiert Abschnittswechsel neu und erhält gewählte IDs.
- Stabile clientEntryKey-Zuordnung statt Text-/GPS-Abgleich; bestehende und eigene Fotos übernehmen wirksame Servermetadaten über eine dauerhafte BTB-Korrekturschicht. GPS/Zeit/Genauigkeit können unbekannt bleiben. PDF, Detailansicht und Export verwenden effektive Werte. Originale bleiben erhalten.
- Bestätigte Bindungs-/Kontext-/Textänderungen werden nur bei unverändertem Pendingwert quittiert; spätere lokale Eingaben bleiben erhalten. Unbekannte Unterbaustellen werden nicht still auf Hauptbaustelle umgebogen. Read-only/Lifecycle-/Outbox-/Originalschutz bleibt auf bestehenden Pfaden.
- Grenzen: Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/Server-E2E-Nachweis. Kein APK/Versionssprung, keine Actions gestartet ([skip ci]), kein Servercode geändert. Technischer Stand 1.3.26/200076. Original-EXIF vor Galerie-Konvertierung gelesen; Context7 zu AndroidX ExifInterface ausgewertet, keine neue Bibliothek.

## AND-CATALOG-INVENTORY – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- In beiden Katalog-Aktionsmenüs neue Aktion Vorhandene Daten anzeigen. Dialog lädt bei Öffnen den ausgewählten Strecken- oder Bahnhofbestand, filtert nach Kennung/Objektart/Strecke/Bahnhof und zeigt zunächst 40 Treffer mit Weitere anzeigen. DialogFragment verwaltet Fenster-Lifecycle; Job wird bei Dismiss/onDestroyView abgebrochen, Ergebnis nur an sichtbaren Dialog gebunden. Token wird zur Laufzeit aus SecureTokenStore gelesen, nicht in Argumenten gespeichert.
- Bestand rein lesend aus gemeinsamem GLOBAL-Pool, eindeutige Kennungen, einzelne belastbare KM-Abschnitte; kein Zusammenziehen über Lücken. Fehler bleiben Fehler, kein falscher Leerbestand. Bahnhof ohne Kürzel wird nicht über Namen geraten. Keine Original-/Outbox-/Room-/Lifecycle-Fachänderung.
- Kein Kotlin-/Gradle-/Lint-/UI-/Geräte-/NAS-E2E-Lauf; adb/Gradle/Kotlin-Toolchain hier nicht vorhanden. Context7 zu AndroidX Lifecycle/DialogFragment ausgewertet. Keine neue Bibliothek, keine server/**-Änderung, keine Actions gestartet ([skip ci]), keine APK/Versionsänderung. Technischer Stand bleibt 1.3.26/200076; spätere APK braucht höheren Build und passende Release-MD.

## AND-CATALOG-FORM – Quellstand 20.10.2026
Status: **QUELLSTAND UMGESETZT / SOURCE-CHECKS BESTANDEN / BUILD UND E2E OFFEN**.
- Baustellenformular besitzt Vorhandene Daten anzeigen bei freier Strecke/Bahnhof und Bahnhof auf Server suchen. Übernahme nur im unveränderten Formular-Kontext; Objekt setzt Radioart/Nummer und lässt KM unverändert, KM setzt nur den angeklickten belastbaren Abschnitt. Nutzer-Kontextwechsel räumt alte Bahnhof-/Objekt-/KM-Auswahl. Laden/Rotation unter Guards; wiederhergestellte Eingaben werden nicht erneut aus DB überschrieben. Lookup-Job/Dialogs bei View-Ende geschlossen, späte Antworten bei Kontext-/Lifecyclewechsel verworfen. WVB sichtbar, typbezogene Nummernhinweise; DKW bleibt.
- Stabile Knoten-/Objektidentität, keine Originaldatei-/Room-Schemaänderung, keine neue Bibliothek und kein Serverumbau. Keine automatische Bulk-Reparatur alter Baustellen. Vorhandene Fach-/Download-/Offlinepfade bleiben Grundlage.
- Kein Kotlin-/Gradle-/Lint-/Emulator-/Geräte-/NAS-E2E-Nachweis; diese Toolchain hier nicht verfügbar. Context7 zu FragmentResult/Lifecycle geprüft. Keine Actions gestartet ([skip ci]), keine APK und keine Versionsänderung. Technischer Stand weiterhin 1.3.26/200076; neue APK nur mit höherem Build und zugehöriger Release-MD.


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

- RecoveryHttp deckt alle bisherigen direkten HttpURLConnection-Erzeugungen ab, einschließlich direkter Antwortstreams und Header. Worker prüfen die Sperre vor lokaler Facharbeit; neue Generationen verwenden getrennte Room-Dateien, appinterne/externe App-Dateien und Geschäftseinstellungen. Token und aktivierter Server bleiben global.
- Archiv enthält typisierte transaktionale DB-Exporte, Originaldateikopien und typisierte aktuelle Geschäftseinstellungen; commit wartet auf ausstehende apply-Schreibvorgänge. Verzeichnisfehler brechen ab. Ein fehlgeschlagener Versuch entfernt nur eigene neue Kopien/Manifestdateien.
- Wartung & Diagnose bietet „Gesicherter Handybestand“ zur anmeldegebundenen Einsicht und zum bewussten Export.


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


## AND-LIFECYCLE-024 – Quellkorrektur und technische Prüfung 24.10.2026
Status: QUELLSTAND FERTIG / KOTLIN-, UNIT- UND LINT-PRÜFUNG BESTANDEN / INSTALLATION UND GERÄTE-/NAS-E2E OFFEN.
- Finaler Runtimecode2570b4c222fa5fb9338e4471a22421425a99e8c1, Version1.3.31/200081 mit passender Release-MD. Explizite persistente Schicht-/Abschlussaufträge, stabile Mutationskennung, Serverzielprüfung vor Retry, Mehrfachklickschutz, Startnachholung und sichtbarer ausstehender Zustand. Inhalte/Bilder/Struktur gezielt vorher; Konflikt-/Dirty-/Recovery-/Rollen-/Gerätebindungsschutz. Kein automatisches Resume oder allow_unfinished. Originalbasis bei Dirty/Pending bewahrt, strikte offene Speicherrückmeldung für Abschluss. Galerie bleibt; WhatsApp-Teilen entfällt.
- Finale CI40108708246, Androidjob120915704503 SUCCESS: Kotlin-/Unitstep PASS (2m24s), Lintstep PASS (1m7s). Tatsächlich gelesenes JUnit-/Lint-XML aus Artefakt12566170481:108 Tests,0 Fehler/Fehlschläge/übersprungen;10 neue LifecyclePolicy-Tests PASS. Lint0 Error/0 Fatal/391 Warning, keine neue BtbLifecycle-Issue.18 lokale gezielte Sourcechecks PASS. ZIP-SHA25642887659287318d828801142ef9b98e8cd6493c7bc2ad0f0a170840ab7b1a3aa.
- Gesamt-CI bleibt ROT durch dieselben fünf historischen Quellvertragsprüfungen13/19/83/85/86; finale Original-Logs geprüft. Keine Prüfung deaktiviert/abgeschwächt. Finaler Gradlelog enthält weiterhin einen nicht den Job abbrechenden KSP/AWT-NullPointer-Diagnoseeintrag; Kompilierung, Testtask und Lintstep erfolgreich, nicht als neue Runtimeursache belegt.
- Server zuletzt ee6a879e13a6b97c4b75da97800d5f9a2923ac55 lesend gegen8349f03 verglichen: nur PDF-/Test-/Dokumentationsänderungen, kein neuer Lifecyclevertrag. Keine server/**-Änderung, NAS-Übernahme/Migration oder Deployment durch diesen Block nötig. Keine APK/assemble/Veröffentlichung.
- Übergabe: projektsteuerung/uebergaben/ANDROID_BTBSCHICHT_ABSCHLUSS_200081.md. Nächster Praxisablauf:200081 lokal bauen, Pixel/Poco als Update installieren; Helfer Schicht Ende -> TB sieht FINISHED -> BTB Abschluss -> Portal/beide Handys konsistent. Offline+Neustart, Bildfehler, paralleler Save/409 und POST-Erfolg mit ausgefallenem Folge-GET praktisch prüfen. JVM-Policy-/Sourcechecks sind kein WorkManager-/Netz-/Geräte-E2E. 16h-Autoabschluss und serverweiter Foto-Unlink bleiben separate offene Aufträge. Keine konkrete damalige Laufzeitursache ohne Logs behauptet.


## AND-BIDIR-026 – Quellprüfung 26.10.2026
Status: QUELLPRÜFUNG ABGESCHLOSSEN / KORREKTUR UND GERÄTE-E2E OFFEN. Screenshot bestätigt1.3.31/200081. Android2368fbc, Server923cf93 geprüft. Lokale Neuanlage ohne automatische Serverprovisionierung; Node-Key blockiert lokalen Eintrag; lokaler Entwurf ohne PDF-Vorschau. Schneller Androidfeed ohne WORKSITE/MEMBERSHIP/INVITATION und ohne Kontextinitialisierung; lokales Hauptbaustellen-Umbenennen ohne Sendepfad. Unterbaustellen-/BTB-/Foto-Delta und Portal-Detail-SSE existieren, geschützt. Portal-Liste ohne entsprechenden Livepfad. „Neu laden“ startet auch vollständigen Fachworker, doch SUCCEEDED umfasst ungebundene lokale Baustelle nicht; frühere Nur-Katalog-Vermutung korrigiert. Gemeinsame API-/Nutzeridentität vorgesehen, aktuelle UUIDs nicht verglichen. Vollständige Belege, Umsetzungsteilung und offene A–H-Gerätematrix: uebergaben/ANDROID_BIDIREKTIONAL_2026-10-26.md. Keine Runtime-/Versions-/Servercodeänderung, APK, Actions oder NAS-Übernahme/Deployment.






## BTB-INTEGRATION-031 – Code umgesetzt, Abnahme offen (31.10.2026)

Android 1.3.35/200085 und passender Serverstand: drei Bildspalten, strukturierter Drucksnapshot für Firma/Ort und Fotoverweise, sofort gespeichertes Eintragslöschen, erhaltene Fotoverknüpfung beim Wechsel von Firma/Ort/Punkt, eindeutiger kontextgebundener Beschreibungsabgleich, ausdrückliche REWORK-Nachbearbeitung ohne Ersetzen einer neuen aktiven Schicht, Abschluss ohne Medienimport, Export mit bestätigter Original/Sync-Zuordnung und Linkmetadaten. Ausdrückliches Lösen wird zusätzlich als entryBindingCleared in wirksamen Foto-Metadaten transportiert; andere Geräte dürfen es nicht automatisch wieder binden. Historische Originale/IDs/Rollen/Outbox-/Konfliktschutz bleiben geschützt.

Nachweise:22 neue Server-Funktionsprüfungen und7 produktive Portal-Guardfälle PASS; vorhandene132/138 je11, Worksite-Head14, Portal152/153 und List158 PASS; Produktions-PDF mit drei Karten in einer physischen Zeile/KM0/GPS-Links PASS. Acht Android-JUnit-Fälle ergänzt, NICHT ausgeführt. Kein Android-SDK/Gradle/Kotlin/ADB; kein PostgreSQL/HTTP-/Geräte-/NAS-E2E. Keine APK, Actions, Veröffentlichung, Produktivübernahme oder Bestandsbereinigung. Abschlussdauer und Wetterabweichung am Gerät weiterhin offen. Kein Status „alles funktioniert“.

Details, genaue Grenzen und Abnahmeschritte: [BTB-INTEGRATION-031](uebergaben/BTB-INTEGRATION-031.md). Android-Release-MD: projektsteuerung/releases/TB-Helfer_1.3.35_200085.md, DOWNLOAD_URL leer. Nächster verbindlicher Schritt: Android-Build/Tests und gemeinsamer Testbetrieb beider Branchstände mit Haupt-TB/Helfer, Änderungen/Löschen/Verschieben, sieben Bildern und zweimaligem Export.


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



## SPRACHE-1103 – technischer Quellabschluss 03.11.2026

**Status: QUELLSTAND FERTIG / KOTLIN-, UNIT- UND LINT-PRÜFUNG BESTANDEN / GERÄTE-E2E OFFEN.**

- Geprüfter Runtimecommit `b6fb187bc7ed40e73ae07653cadd1847fb1d39e0`, Basisimplementierung aecf0b92. Finale [Android CI41598885323](https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/41598885323), Androidjob125792917398 SUCCESS: testDebugUnitTest inkl. Kotlin/KSP/Java/Ressourcen PASS3m58s, lintDebug PASS1m33s. Berichtsartefakt13213542481 tatsächlich gelesen: **138 JVM-Tests,0 Fehler,0 Fehlschläge,0 übersprungen;13 SpeechDraftPolicyTest-Fälle PASS. Lint0 Error/Fatal,388 Warnungen.** Sieben zusätzliche HardcodedText-Hinweise betreffen die neuen Mikrofon-Glyphen im bestehenden BTB-Layout; keine Issue in neuen speech-Kotlin-Dateien/Widgetressourcen. Archiv-SHA256 ed84a75b5b3022ab198ad2dc0956dfc54d48f4be3cdfddd36a840849bb85515f.
- Erster Lauf41598309068 hatte Kompilierung/Unit bereits bestanden; nach konkreter Reviewkorrektur für verzögerten Hintergrundstart/parallele Starts wurde er von Concurrency abgebrochen. Daraus keine vollständige Erstlauf-Freigabe ableiten. Finaler Lauf prüft den korrigierten Runtimecode; keine dritte Wiederholung/Cloud-APK.
- Separater Vertragsjob125792917683 weiterhin FAIL an denselben fünf Altprüfungen13/19/83/85/86. Original-Logs gelesen, Foto-/BTB-Lifecycle-/Struktur-/Aktivierungsschutzprüfungen weiterhin PASS. Gesamt-CI daher NICHT BESTANDEN; keine Prüfung abgeschwächt.

### Umgesetzter Ablauf
Zusätzliches Widget **TB-Helfer Mikrofon** und Aufnahmetaste im BTB öffnen sichtbare Aufnahmeansicht. Start nur RESUMED, nach asynchronem BTB-Laden erneut geprüft; kein Receiver-/Hintergrundmikrofon. Mikrofonberechtigung, Doppelklick-/Vorbereitungs-/Berechtigungs-Guard, Cancel beim Stop und Freigabe des Recognizers beim Destroy. Testaufnahme verändert keinen BTB. Kein permanentes Lauschen, keine Roh-Audiodateien.

On-device-Sprachdienst wird bevorzugt, de-DE-Unterstützung nach verfügbarem SDK abgefragt; fehlende/ungeprüfte Unterstützung ehrlich angezeigt. Online nur nach ausdrücklicher Freigabe und validierter Verbindung. Offline-/Sprachfehler wechseln nicht still zum Onlineanbieter. Modelle können ausdrücklich zum Download angefragt und danach erneut geprüft werden; Anfrage ist kein Erfolgsnachweis. Geräuschverarbeitung wird als vom Sprachdienst verwaltet angezeigt; Geräte-NoiseSuppressor-Verfügbarkeit ist kein aktiver Filter-Nachweis.

Erkannter Text wird pro Aufnahme mit stabiler UUID privat im lokalen Recovery-Workspace und an die Aktivierungsbindung gebunden gesichert. Teil-/Endergebnisse aktualisieren denselben Entwurf. Konservative eindeutige Firma-/BTB-Punkt-Erkennung; keine geratenen KM/Orte oder Mehrdeutigkeitsauflösung. Mikrofon+Anzahl am Abschnitt bzw. oben bei offener Zuordnung;0 unsichtbar. Editor bietet Text/Firma/Allgemein/Punkt4–8/Unterbaustelle inklusive Hauptbaustelle; Entwurfsänderungen werden lokal erhalten. Nur Firma/Abschnitt ohne Beschreibung reicht nicht zur Bestätigung.

Erst explizites Speichern übernimmt einen normalen SectionItem mit stabiler clientEntryKey/firmaId/worksiteNodeKey. Aktive btbId/Baustelle/Anmeldung, Recovery, Read-only/Lifecycle, lokale Firmen/Knoten und Kontextrechte werden erneut geprüft. Keine ausstehenden UI-Änderungen überschreiben; parallele BTB-Version ablehnen. Persistenz unter vorhandenem Mutationslock, danach Eintrag nachlesen und Dirty-Vormerkung prüfen. Entwurf erst nach Bestätigung quittieren. Gleiches Ziel/dieselbe UUID ist idempotent; abweichender Inhalt oder anderer Punkt mit derselben ID wird nicht heimlich ersetzt. Vor Schicht-/BTB-Ende Hinweis auf offene Entwürfe, ohne Übernahme/Verwerfen zu automatisieren. Bestehende Kamera-/Baustellenwidgets bleiben unverändert.

### Bewusste Grenzen / Abnahme offen
- **Bereichsbeschränkte oder unklare Helfer-Scope-Verträge werden in der neuen Sprachübernahme konservativ blockiert**; Entwürfe bleiben erhalten. Haupt-TB und bestätigte unbeschränkte Helferkontexte werden unterstützt. Dies ist keine Änderung der bisherigen manuellen Helferfunktionen und keine Erweiterung von Rechten. Vollständige area_scope-Auswertung wäre ein gesondert belegter Folgeblock; nicht als bereits umgesetzt darstellen.
- Entwürfe bleiben auf dem Aufnahmehandy und werden nicht zwischen Geräten übertragen. Neuaktivierung/Nutzerwechsel zeigt keine fremden Altentwürfe; alte Daten werden nicht pauschal gelöscht. Kein dauerhaftes serverseitiges Sprachherkunftsfeld hinzugefügt.
- Offline-/Deutsch-/Berechtigungs-/Qualitätsdaten der echten Pixel/Poco-Geräte wurden hier NICHT ausgelesen. Kein ADB/SDK/Emulator verfügbar. **Kein Mikrofon-, Flugmodus-, Lärm-, Hoch-/Querformat-, Headset-, Geräte-/HTTP-/Server-E2E-Test bestätigt.** Scroll-/Insets-/Dialogvorsorge und JVM-Policytests ersetzen das nicht.
- Gerätefolge: lokal bauen und datenbewahrend mit bisherigem Schlüssel aktualisieren; in Spracheingabe erst Gerät prüfen/Testaufnahme. Pixel/Poco getrennt mit Modell vorhanden/fehlend, Flugmodus, Netzverlust, verweigerter/entzogener Berechtigung, Sprechpause/Stop/Rotation/Hintergrund/Neustart prüfen. Firma allein, Text allein, eindeutige/mehrdeutige Firma/Punkt, alle Unterbaustellen und Speichern/Verwerfen prüfen. Mehrfachklick/Retry, alter/abgeschlossener BTB, Baustellen-/Teamwechsel, Recovery/Dirty und Helferrechte prüfen. Bestehende Kamera-/Fotobindung/manuelle Einträge/PDF/Sync/BTB-Ende als Regression abnehmen. Entwürfe dürfen vor Bestätigung nie im PDF/Sync erscheinen.

### Bau- und Parallelstand
Quellversion **1.3.36 /200086** und passende `projektsteuerung/releases/TB-Helfer_1.3.36_200086.md` als lokale Bauvorbereitung. Kein assemble/APK, keine Signatur-/Installationsprüfung und kein Download-/Release-/NAS-Deployment. Nutzer baut lokal mit Android Studio/GitHub Desktop. Nicht INSTALLIERT oder ERLEDIGT.

Während der Prüfung kamen unabhängige Sync-Dokumentationscommits hinzu: Android694a53c, Server3e8a138. Vergleich zum geprüften Android-Runtimecommit zeigte ausschließlich Dokumentationsänderungen; Server nur Audit-/Auftragsdokumentation plus Prüfskript. Keine geänderte Runtime/API in den Vergleichsständen. Diese Änderungen vollständig erhalten; deren geplante Sync-/KM-Korrekturen bleiben eigener Auftrag. Keine server/**-Datei von SPRACHE-1103 geändert.


## SYNC-FIX-1103 – umgesetzter Schutzblock und KM-Vorschlag (03.11.2026)

- Nutzerfreigabe „okay bauen“ vor Umsetzung gesichert: Server3e8a138/Android694a53c. Funktionsstände Server7a82525b48330efdbdea2cae77c904ceda097994 und Android981fb13445dfa9f39c0e162456fbacd86c675ba1; unabhängigen Sprachstand603ba28 erhalten.
- Implementiert: transaktionaler Feed-Publikationslock vor ID-Vergabe; autorisierter minimaler BTB-sync-state; bestätigte Tombstones statt pauschalem404-Delete; lokale Offlinefassung/Pending bei Abschluss/Löschung sichern und als Konflikt erhalten (auch Prime/Vollsync/Reconcile); read-only Mutationen nicht blind senden; Capabilitycache weiterverwenden, wartende Sendeaufträge begrenzen, zusätzlicher15-Minuten-Deltafallback ohne Push-/Pünktlichkeitszusage.
- Lokal6 neue Serverprüfungen+7 Check135+8 Check139 PASS (Doubles, kein PostgreSQL/HTTP). Java-Sicherungsregression inkl. Schreibfehler/Idempotenz/20 paralleler Fassungen PASS. Android-CI41601820053/Buildjob125802121038 SUCCESS: Kotlin/KSP/Java/Unit/Lint; Originalartefakt13215655718 mit143 Tests,0 Fehler/Fehlschläge/übersprungen, neue5/5; Lint388 Warnings,0 Error/Fatal. Keine APK/Installation.
- Gesamt-CI bleibt FAILURE: Vertragsjob125802121498 mit bekannten Altprüfungen13/19/83/85/86 und neu veraltetem direkten Delete-Quellmarker in15. Original-Logs mit Vorlauf41598885323 verglichen.15 auf den geschützten delegierten Applypfad mit zusätzlichen ID-/Versions-/Team-/Dirty-/Sicherungs-/Lockprüfungen angepasst und lokal PASS, übrige Regeln erhalten. Reine Test-/Dokumentationsnacharbeit nach Funktions-CI, keine Runtimeänderung und keine weitere CI-Runde. Keine Tests deaktiviert.
- PostgreSQL-Concurrency-/Deadlock-/Lasttest vorbereitet, NICHT ausgeführt; Postman5 GET-Requests/10 Assertions vorbereitet, NICHT ausgeführt. Keine neue Server-Gesamt-CI, kein NAS/SSH/Deployment/Produktivschreiben, keine Datenmigration. Zusammengehöriger API-/Worker-Neustart mit events.py+btb.py erst nach echter Integrationsprüfung/Freigabe. Alte/neue Feed-Produzenten nicht mischen.
- Streckendatenbefund: Baustellen-LEAD-Sync überträgt berechnete AXIS-Samples und Streckenpunkte, aber nicht vollständig manuelle Abschnitte/KM-Anker; heutige lokalen Speicherwege haben keinen direkten Sync. Der Sollbestand-Abgleich kann lokal fehlende Server-Datasets löschen bzw. nach neuer Basis mit lokaler Sollfassung überschreiben. Noch keine neue KM-Uploadimplementierung.
- Empfehlung: bestehende gemeinsame Achse/Quelle eindeutig per UUID/Hash/Version binden; manuelle KM-Änderung mit eigenen stabilen IDs/Ankern/Genauigkeit dauerhaft vormerken; gezielt/versioniert senden, Konflikte sichtbar, gemeinsame Katalog-/Netzfeeds empfangen. Originale unverändert, manuellOrange/importiertGrün. Keine weitere parallele Streckendatenablage. Details: [TRACK_KM_1103.md](uebergaben/TRACK_KM_1103.md).
- Vollständiger Umfang, Grenzen, Nachweise und spätere NAS-Dateiliste: [SYNC_FIX_1103.md](uebergaben/SYNC_FIX_1103.md). Mängel-/Abnahme-/Streckendaten-Deltaerweiterung, Mediennachlauf, Push und Konflikt-Zusammenführungsansicht bleiben Folgeblöcke. Echte PostgreSQL-/Postman-/Pixel-/Poco-/NAS-/Schlafmodus-/Offline-/Zwei-Geräte-E2E OFFEN. Status QUELLSTAND DES SCHUTZBLOCKS FERTIG / LOKALE PRÜFUNGEN UND ANDROID BUILD-UNIT-LINT BESTANDEN / GESAMT-CI NICHT BESTANDEN / DEPLOY OFFEN / E2E OFFEN.
- Status-Excel-Nachtrag OFFEN: binärer Connectorabruf nicht als XLSX verifizierbar, direkter Blobabruf nicht-UTF8 abgewiesen. Original nicht überschrieben; daraus keine Beschädigung der Originaldatei behauptet. Markdownstatus gesichert.

