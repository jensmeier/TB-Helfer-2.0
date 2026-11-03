TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.36
BUILD: 200086
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

STATUS: QUELLSTAND / LOKALE BAUVORBEREITUNG – NOCH KEINE APK ERZEUGT

NEU:
- Drittes Startbildschirm-Widget TB-Helfer Mikrofon und Aufnahme-Einstieg im BTB.
- Private lokale Sprachentwürfe mit bearbeitbarer Firma, BTB-Punkt, Text und Unterbaustelle. Mikrofon-Anzahl am Abschnitt bzw. bei offener Zuordnung; bei 0 unsichtbar.
- Einstellungen Spracheingabe: Aktivierung, ausdrückliche Onlinefreigabe, Geräte-/Deutsch-Modellprüfung und Testaufnahme ohne BTB-Mutation. On-device-Pfad wird bevorzugt; ohne verfügbaren On-device-Pfad nur online bei Freigabe und Netz.
- Hinweis auf offene Sprachentwürfe vor Schicht-/BTB-Ende. Entwürfe werden erst nach ausdrücklich bestätigter und lokal nachgelesener Übernahme reguläre BTB-Einträge. Keine Roh-Audiodateien, keine Entwürfe im PDF/Sync.

ERHALTEN:
- Kamera-/Baustellenwidgets, Foto-/PDF-Fachwege, selektiver Offline-Sync und vorhandene Rechte-/Recovery-/Abschlussgates.
- Einstellungsmenü und Medienexportkorrekturen aus dem vorherigen Quellstand.

GRENZEN:
- Deutschmodell/Sprachdienst und Mikrofon müssen auf Pixel/Poco geprüft werden. Offline-Sprachdienst vorhanden ist kein Qualitätsnachweis; Offline-Fehler wechseln nicht still in die Cloud.
- Rauschverarbeitung wird ehrlich als vom Sprachdienst verwaltet angezeigt; keine pauschale Zusage zusätzlicher Filterung.
- Bereichsbeschränkte oder unklare Helfer-Scope-Verträge werden nicht geraten; Übernahme bleibt dort zunächst gesperrt, Entwürfe erhalten. Haupt-TB und bestätigte unbeschränkte Helferrechte werden vor Übernahme erneut geprüft.
- Entwürfe bleiben auf dem Aufnahmehandy in der jeweiligen lokalen Generation/Aktivierungsbindung. Ein neuer aktivierter Nutzer bekommt keine alten Entwürfe angezeigt.
- Geräte-/Mikrofon-/Flugmodus-/Lärm-/Querformat-/NAS-E2E offen. Kein Servercode, keine Datenmigration und kein NAS-Deployment.

BAUHINWEIS:
- Kein Cloud-APK-Build beauftragt. Nutzer baut lokal über GitHub Desktop/Android Studio mit bisherigem Schlüssel.
- Diese Datei ist bis zum tatsächlichen APK-Build eine Bauvorbereitung. Eine APK darf nur zusammen mit der aus genau diesem Build kontrollierten Begleitdatei weitergegeben werden; keine alte APK damit kombinieren.


TECHNISCHER TESTNACHWEIS:
- Runtimecommit b6fb187bc7ed40e73ae07653cadd1847fb1d39e0; Android-CI41598885323 / Job125792917398 SUCCESS.
- Kotlin/KSP/Java/Ressourcen,138 JVM-Tests (darunter13 Sprach-Policyfälle),0 Fehler/Fehlschläge/übersprungen. Lint0 Error/Fatal,388 Warning; sieben zusätzliche Glyphen-HardcodedText-Hinweise im BTB-Layout.
- Originalbericht13213542481 ausgewertet. Gesamt-CI wegen unveränderter Altprüfungen13/19/83/85/86 weiterhin ROT.
- Kein APK-/Manifest-/Signatur-/Installationsnachweis aus diesem Lauf: Unit/Lint ohne assemble. Geräte-/Offline-/Mikrofon-/Lärm-/Querformat-E2E weiterhin offen.


SYNC-FIX-1103 (gleiche noch nicht als APK ausgelieferte Quellversion):
- Autorisierte BTB-Tombstones über additiven Serverendpunkt; keine pauschale404-Löschung.
- Lokale Offline-Inhalte bei Serverabschluss/Löschung als Konflikt behalten und zusätzlich dateibasiert sichern. Keine automatische Wiederöffnung.
- Capabilitycache im laufenden Empfang wiederverwenden; Sendeaufträge begrenzen; zusätzlicher15-Minuten-Deltafallback (Android kann verzögern, kein Pushversprechen).
- Benötigt zugehörigen Server-SYNC-FIX-1103. Java-Sicherung lokal geprüft; Kotlin/Unit/Lint für diesen integrierten Quellstand noch OFFEN. Vorherige Sprach-CI gilt nicht als Nachweis dieser neuen Änderungen. Keine neue APK erstellt.

SYNC-FIX-1103 NACHWEIS zum Funktionscommit981fb134:
- Kotlin/KSP/Java,143 Unit-Tests (darunter5 neue Sicherungstests) und Lint in Run41601820053/Buildjob125802121038 bestanden. Lint388 Warnings, keine Error/Fatal.
- Gesamt-CI bleibt wegen Vertragsprüfungsjob rot. Quellprüfung15 danach an den geschützten Löschpfad angepasst/lokalPASS; keine Runtimeänderung durch diese Nacharbeit.
- Keine APK/Installation/NAS-/PostgreSQL-/Zwei-Geräte-E2E. Quelle1.3.36/200086 bleibt Bauvorbereitung mit derselben Begleitdatei.
