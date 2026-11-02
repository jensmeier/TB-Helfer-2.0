TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.35
BUILD: 200085
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- BTB-Verlauf: Verantwortlicher kann ein abgeschlossenes eigenes BTB nach Bestätigung zur Nachbearbeitung öffnen. Eine andere laufende Schicht bleibt aktiv; gleicher BTB und gleiche Eintrags-IDs bleiben erhalten. Benötigt passenden Serverstand BTB-INTEGRATION-031.
- Eindeutige gleiche Bildbeschreibung (z.B. Schwellenwechsel) innerhalb desselben BTB, Punkts, derselben Firma und Unterbaustelle kann die fehlende Bildverknüpfung ergänzen. Vorhandene Verknüpfungen und ausdrücklich gelöste Zuordnungen haben Vorrang. Mehrdeutige Ziele bleiben offen.

VERBESSERT:
- Android-Abschluss startet nach Klick einen gezielten Versuch mit dauerhaftem Hintergrund-Retry. Start, App-Rückkehr und wieder verfügbares Netz holen ausstehende Abschlüsse nach. Fortschritt zeigt echte Phasen; erneuter Klick zeigt den vollständigen Wartegrund. Kein zusätzlicher Portalabschluss als normaler Ablauf vorgesehen; praktische Abnahme noch offen.
- Firma und Unterbaustelle im normalen BTB-Eintrags- und Fotogruppen-Dialog korrigieren; Speicherübergabe des Eintrags vor Neuladen repariert. Neue Fotos nach Kurztext eindeutig im selben BTB/Punkt/Firma/Ort binden. Langtexteingabe fragt nach Kurztext; Android-BTB und Druck zeigen beide Texte. Originalbilder bleiben unverändert.
- Wetterantworten nach BTB-/Datums-/Schicht-/Eingabewechsel verwerfen; Standortmetadaten erst mit akzeptierter Antwort setzen. Konkrete Wetterabweichung am Gerät noch offen.
- PDF-Bildanhang mit drei Bildern pro Zeile und Nutzung der verfügbaren Seitenbreite, unabhängig von Hoch-/Querformat. Server-Portal ebenfalls drei Karten je Zeile; Server-PDF mit größerem Bildbereich.
- Strukturierte Firmen-/Unterbaustellenzuordnung erreicht Android-PDF und Word. Zugeordnete Bildnummern am manuellen Eintrag, keine zusätzliche Textzeile je verbundenem Foto.
- Abschluss überspringt den Download fremder Medien; eigene Uploads/Änderungen bleiben Voraussetzung. Worker wartet bei erlaubtem Mobilfunk nicht mehr pauschal auf ungetaktetes Netz; vorhandene Netzrichtlinien bleiben geprüft.
- Gesamtbilderliste verwendet bestätigte lokale Foto/Server-Bindung zur Erkennung von Original und Sync-Version; korrigierte Linkmetadaten statt allein Dateinamen. Fehlende Bilddateien/Katalogfehler melden einen unvollständigen Export.

BEHOBEN:
- Manuelles Löschen wird sofort gespeichert; Bilddateien werden beim Löschen eines Eintrags nicht gelöscht, sondern gelöst. Firmen-/Orts-/Punktwechsel behalten bestehende Eintrags-Fotoverknüpfung.
- Doppelpunkt im Text (z.B. 1stab 13:02) wird ohne bekannte Firma nicht als Firmenname interpretiert. Allgemeine Einträge dürfen ihre Unterbaustelle behalten.

TESTSTATUS:
- Runtimecommit5291d522a5cb7a8826d7d9190545ed3005d04b12, Android-CI41498293472: Androidjob125458915143 SUCCESS. Kotlin/KSP/Java-Kompilierung und testDebugUnitTest PASS;122 Tests ohne Fehler/Fehlschläge/übersprungene Fälle laut gelesenem JUnit-XML. Enthält die8 BtbIntegration031- und6 BtbPhotoDescription-Tests.
- lintDebug PASS:0 Error/0 Fatal,395 Warning laut gelesenem Lint-XML. KSP/AWT-NullPointer-Diagnose im Gradlelog vorhanden, Tasks/Job dennoch SUCCESS; nicht als vollständig fehlerfreies Tooling ausgeben.
- Gesamt-CI ROT: bekannte fünf Quellvertragsprüfungen13/19/83/85/86; unverändert, nicht abgeschwächt. Kein assemble/APK-/Installationsnachweis. Downloadfeld bleibt leer; diese MD keinem älteren APK zuordnen.
- Frühere Server-/Portal-/PDF-Funktionsnachweise für BTB-INTEGRATION-031 bleiben dokumentiert, in diesem Androidblock nicht erneut ausgeführt. Kein neuer PostgreSQL-/HTTP-/NAS-E2E.
- Keine Veröffentlichung, kein NAS-Deployment, keine Datenbereinigung. Abschlussgeschwindigkeit, WorkManager/Prozessneustart/Netzrückkehr und echter Handyabschluss ohne Portalaktion praktisch OFFEN.

ABNAHME NACH BUILD UND SERVER-ÜBERNAHME:
1. Als Update ohne Deinstallation installieren; zwei Testnutzer (Verantwortlicher/Helfer), freigegebene Testbaustelle.
2. Sieben eindeutige Fotos erfassen/synchronisieren, zweimal exportieren: sieben Dateien, sieben Tages-/Gesamtzeilen, alle Links öffnen.
3. Schwellenwechsel eingeben: passende Fotos derselben Firma/Unterbaustelle/Punkt einmal zugeordnet; fremde Firma/Ort und doppelte Zieltexte bleiben getrennt.
4. Eintrag ändern, andere Firma wählen, Ort/Punkt wechseln und löschen; Neustart, Server sowie PDF/Word vergleichen. Stabile IDs, keine verloren gegangenen Bilder.
5. Helfer arbeitet, beendet nur Teilnahme; danach schreibgeschützt. Verantwortlicher beendet BTB; Offline zeigt ausstehend, Serverabschluss erst nach Bestätigung. Fehler/Retry ohne doppelten Abschluss.
6. Neue Schicht starten, altes BTB nachbearbeiten und erneut abschließen: neue Schicht bleibt aktiv; aktuelle PDF/Word erneuert, Ursprungsstand erhalten.
7. Android-/Server-PDF und Portal: drei Bilder pro Zeile, keine abgeschnittenen Beschriftungen; Wetter vor/nach Serverbestätigung separat prüfen.
