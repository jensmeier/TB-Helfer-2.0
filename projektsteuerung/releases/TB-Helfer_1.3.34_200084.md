TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.34
BUILD: 200084
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Einstellungen > Wartung & Diagnose > Fehlerprotokoll: maximal200 lokale Fehlergruppen, neueste zuerst; Wiederholungen mit erstem/letztem Zeitpunkt und Meldungszähler.
- Verständlicher Bereich/Vorgang, sichere technische Codes und Build. Bekannte lokale Baustellenzuordnung beim Öffnen; keine vertraulichen Inhalte im Export.
- Einzelnen Eintrag oder gesamtes Protokoll kopieren/über die Android-Auswahl teilen. Löschen nur des Protokolls mit Bestätigung.

VERBESSERT:
- Direkter „Fehler ansehen“-Link bei Fehlern des manuellen Syncs/Katalogabrufs. Bereits vom Worker protokollierte Fehler werden anhand stabiler ID geöffnet, nicht nochmals gezählt.
- Erfasst verfügbare Fehler aus Vollsync, selektivem BTB-/Struktursync, Deltaempfang, sichtbarer Serverprüfung und Wartungs-Updateprüfung/-download.
- Neustartfeste JSON-Einträge im vorhandenen lokalen Recoverybestand, kein zusätzlicher Serverdienst/Upload.

BEHOBEN:
- Neue erfasste Fehler gehen beim Verlassen der Statusseite oder App-Neustart nicht allein wegen der Anzeige verloren.

UNVERÄNDERT:
- Keine Änderung von Nutzer-/Helferrechten, Retry, Cursor, Recovery-Protokoll, Servercode oder produktiven Daten. Bestehende DNS-/Berechtigungsfehler sind dadurch nicht als behoben bestätigt.
- Frühere Ereignisse werden nicht nachträglich rekonstruiert. Unbekannte Rohmeldungen werden neutral klassifiziert statt mit möglichen vertraulichen Inhalten gespeichert.

TESTSTATUS:
- Reiner Produktions-Java-Kern kompiliert/ausgeführt:26 Prüfungen PASS für Klassifikation, Wiederholungen, stabile IDs,200er-Grenze, sichere Feldwiederherstellung und Export ohne Geheimwerte/Fachinhalte.
- Beide Layout-XMLs geparst; strukturelle Kotlinprüfung ist kein Compilerlauf.
- Android-/Kotlin-/Gradle-/KSP-/Lint-/APK-Build, echte SharedPreferences-/Neustart-/UI-/Teilen-/Löschtests OFFEN. Android-SDK/Gradle-Abhängigkeiten fehlen hier.
- Keine APK, Veröffentlichung, Actions oder NAS-Übernahme. DOWNLOAD_URL bleibt leer; MD nur mit APK aus genau1.3.34/200084 verwenden.

NÄCHSTER TEST:
- compileDebugKotlin/testDebugUnitTest/lintDebug, dann APK bauen und als Update ohne Deinstallation installieren.
- Neu laden; Fehlerlink öffnet den passenden Eintrag. Wiederholen und Neustart -> gleicher Eintrag mit Zähler/Zeiten. DNS und files.view getrennt nachvollziehen.
- Kopieren/Teilen: nur sichere Codes/Metadaten, keine Rohantworten/Tokens/Dateinamen/BTB-Inhalte oder Baustellennamen. Löschen bestätigt nur Fehlerprotokoll leeren; Baustellen/Einträge/Fotos bleiben erhalten.
