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
- PDF-Bildanhang mit drei Bildern pro Zeile und Nutzung der verfügbaren Seitenbreite, unabhängig von Hoch-/Querformat. Server-Portal ebenfalls drei Karten je Zeile; Server-PDF mit größerem Bildbereich.
- Strukturierte Firmen-/Unterbaustellenzuordnung erreicht Android-PDF und Word. Zugeordnete Bildnummern am manuellen Eintrag, keine zusätzliche Textzeile je verbundenem Foto.
- Abschluss überspringt den Download fremder Medien; eigene Uploads/Änderungen bleiben Voraussetzung. Worker wartet bei erlaubtem Mobilfunk nicht mehr pauschal auf ungetaktetes Netz; vorhandene Netzrichtlinien bleiben geprüft.
- Gesamtbilderliste verwendet bestätigte lokale Foto/Server-Bindung zur Erkennung von Original und Sync-Version; korrigierte Linkmetadaten statt allein Dateinamen. Fehlende Bilddateien/Katalogfehler melden einen unvollständigen Export.

BEHOBEN:
- Manuelles Löschen wird sofort gespeichert; Bilddateien werden beim Löschen eines Eintrags nicht gelöscht, sondern gelöst. Firmen-/Orts-/Punktwechsel behalten bestehende Eintrags-Fotoverknüpfung.
- Doppelpunkt im Text (z.B. 1stab 13:02) wird ohne bekannte Firma nicht als Firmenname interpretiert. Allgemeine Einträge dürfen ihre Unterbaustelle behalten.

TESTSTATUS:
- 22 neue Produktionsfunktionsprüfungen am Server PASS, kontrollierte Transaktions-/Berechtigungsgrenzen, keine echte PostgreSQL/HTTP-Kette.
- Bestehende Portal-016/017 Python+JavaScript-Prüfungen PASS; Berechtigungsprüfungen 132 und138 je11 PASS; Worksite-Head14 PASS.
- Produktions-PDF erzeugt und geprüft: drei Karten tatsächlich in einer Zeile, Bildnummern, KM0, GPS-Links PASS.
- Acht Android-JUnit-Prüfungen ergänzt, noch NICHT ausgeführt. Kein Android-SDK/Gradle/Kotlin-Compiler/ADB vorhanden. Android-Build/KSP/Lint/Installation und reales Android-Helfer-Server-E2E OFFEN.
- Abschlussgeschwindigkeit noch nicht am Gerät gemessen. Kein NAS-Deployment, keine produktiven Änderungen oder Bestands-Duplikatbereinigung. Keine APK/Actions/Veröffentlichung. MD nur mit APK aus exakt1.3.35/200085 verwenden.

ABNAHME NACH BUILD UND SERVER-ÜBERNAHME:
1. Als Update ohne Deinstallation installieren; zwei Testnutzer (Verantwortlicher/Helfer), freigegebene Testbaustelle.
2. Sieben eindeutige Fotos erfassen/synchronisieren, zweimal exportieren: sieben Dateien, sieben Tages-/Gesamtzeilen, alle Links öffnen.
3. Schwellenwechsel eingeben: passende Fotos derselben Firma/Unterbaustelle/Punkt einmal zugeordnet; fremde Firma/Ort und doppelte Zieltexte bleiben getrennt.
4. Eintrag ändern, andere Firma wählen, Ort/Punkt wechseln und löschen; Neustart, Server sowie PDF/Word vergleichen. Stabile IDs, keine verloren gegangenen Bilder.
5. Helfer arbeitet, beendet nur Teilnahme; danach schreibgeschützt. Verantwortlicher beendet BTB; Offline zeigt ausstehend, Serverabschluss erst nach Bestätigung. Fehler/Retry ohne doppelten Abschluss.
6. Neue Schicht starten, altes BTB nachbearbeiten und erneut abschließen: neue Schicht bleibt aktiv; aktuelle PDF/Word erneuert, Ursprungsstand erhalten.
7. Android-/Server-PDF und Portal: drei Bilder pro Zeile, keine abgeschnittenen Beschriftungen; Wetter vor/nach Serverbestätigung separat prüfen.
