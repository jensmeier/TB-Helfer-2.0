TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.25
BUILD: 200075
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- BTB-EDIT-001: bestehende bearbeitbare BTB-Einträge können fachlich korrigiert werden, ohne ihre Identität zu verlieren.
- Stabile Eintragsidentität über clientEntryKey.
- Stabile Unterbaustellenidentität über worksiteNodeKey/clientNodeKey statt gerätespezifischer Room-Long-ID.
- Punktwechsel zwischen 4 / 5 / 6 / 7 / 8 nutzt denselben Eintrag und denselben Entry-Key.
- Firma / Allgemein, Unterbaustelle, Uhrzeit, KM und Text bleiben strukturierte Editierfelder.
- Foto-Kontext kann stabile Node-/Firmen-/Punkt-Metadaten tragen; Originaldatei, Aufnahmezeit und GPS werden beim Umhängen nicht verändert.

VERBESSERT:
- Serverimport übernimmt vorhandenen _tb_android_slot als stabile clientEntryKey.
- Serverexport nutzt clientEntryKey vor indexbasiertem Slot-Fallback.
- Bestehende lokale @ORT=<Long>-Marker werden nur noch als Legacy/UI-Hilfe behandelt; die Sync-Identität ist worksiteNodeKey.
- Android nutzt den vorhandenen Serververtrag für section-PATCH desselben btb_entry statt Delete/Create.
- BTB-SYNC-002 Outbox-/Dirty-Schutz bleibt unverändert aktiv.

BEHOBEN:
- Unterbaustellenbezug ging bei Punkten 4–8 bisher beim Aufbau des SectionItem verloren.
- Firmenzuordnung war im manuellen SectionItem nicht strukturiert.
- Ein Wechsel von Punkt 4 nach 5–8 konnte die fachliche Entry-Identität nicht zuverlässig behalten.
- Foto-Kontext-Overrides waren bisher nur über lokale ortId gerätegebunden.

UNVERÄNDERT:
- FINALIZED/abgeschlossene BTBs bleiben read-only.
- Keine server/**-Datei in diesem Android-Build geändert.
- Bestätigte Fixes BTB-UI-001 und BTB-SYNC-002 bleiben geschützt enthalten.
- PORTAL-WEATHER-001 ist bewusst nicht mit BTB-EDIT-001 vermischt.

SERVERVERTRAG:
- Server-Branch feature/tb-server-1.0.
- Entry-PATCH unterstützt section-Wechsel am selben Eintrag.
- Kanonische Unterbaustellenreferenz ist worksiteNodeKey = vorhandener clientNodeKey.
- BTB-Foto-Metadaten-PATCH ist serverseitig vorgesehen; Foto-Datei/GPS/Aufnahmezeit bleiben unverändert.
- Server-Deploy/E2E ist separat zu bestätigen.

TESTSTATUS:
- QUELLSTAND VORBEREITET
- BUILD ERNEUT OFFEN – Sourcegenerator-Fix 606aa3a beseitigt den reproduzierten Android-200047-Ankerfehler
- GitHub CI RUN 36252246995 blieb zusätzlich infrastrukturell vor Step 1 ohne Runner
- INSTALLATION OFFEN
- BTB-EDIT-001 E2E OFFEN
- POCO/HELPER-E2E OFFEN

NÄCHSTER TEST:
- 1.3.25 / 200075 bauen.
- Pixel installieren.
- Bestehenden bearbeitbaren Eintrag testen: Firma ändern, Unterbaustelle ändern, Zeit/KM/Text ändern.
- Eintrag Punkt 4 → 5 und zurück verschieben; Identität muss erhalten bleiben.
- Zugeordnetes Foto fachlich umhängen; Original, Aufnahmezeit und GPS müssen unverändert bleiben.
- Pixel ↔ Portal Sync prüfen; BTB-SYNC-002 darf nicht regressieren.


BUILDHINWEIS:
- Temporärer CI-PR #10 war mergebar.
- GitHub Actions Run 36252246995 wurde zweimal gestartet.
- Beide Versuche: build-Job ohne Runnername und ohne Steps; deshalb kein echter Gradle-Build.
- Das ist kein Nachweis eines Source-/Compilefehlers.
- Nächster echter Build muss lokal/Android Studio oder über einen wieder funktionierenden Runner erfolgen.


LOKALER BUILD-FEHLER:
- :app:generateBtb200043Sources
- Android 200047: Hauptbaustellen-Ortsmarker-Anker wurde nicht gefunden
- Ursache: Build-time-Source-Patch erwartet alten BautagebuchFragment-Quellanker, der durch BTB-EDIT-001 nicht mehr existiert.
- 1.3.25 / 200075 bleibt die Zielversion; kein versionCode-Sprung, da kein erfolgreicher APK-Build vorliegt.


SOURCEGENERATOR-FIX:
- Commit 606aa3ad1f8ea438372484b056a0fc1264ae2aac.
- Veralteter Android-200047-Build-time-Patch entfernt.
- Hauptbaustelle schreibt im kanonischen Editor jetzt explizit @ORT=0; Unterbaustellen behalten lokale @ORT-Hilfe plus stabile @NODE/clientNodeKey-Syncidentität.
- Legacy-Standalone-Ortsmarker bleiben lesbar; Inline-Ortsmarker wirken nicht auf Folgezeilen.
- Source-Check PASST; Build 200075 muss erneut tatsächlich ausgeführt werden.
