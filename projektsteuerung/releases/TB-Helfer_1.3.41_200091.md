TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.41
BUILD: 200091
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL: NICHT_VERÖFFENTLICHT

NEU:
- Gezielter Fehlerkorrektur-Build.

VERBESSERT:
- Eigene vorhandene Fotos anhand stabiler Zuordnung oder geprüftem Hash wiederverwenden; fehlende Portal-/Helferbilder weiter als Vorschau empfangen.
- Foto-Auswahl-ZIP und Mehrfachteilen mit sichtbarer Fehlerbehandlung.

BEHOBEN:
- Älterer BTB-Abgleich darf gerade hochgeladene Fotozuordnung nicht entfernen; Upload/Datei-Refresh serialisiert.
- Interne Fotos sicher über begrenzten Share-Cache öffnen/teilen.
- Android-Systemkontext für Widget-Receiver erhalten; private Recovery-Speicherumleitung bewahrt.

TESTSTATUS:
- Funktionsstand ca780326: 218 Unit-Tests bestanden, Lint 0 Fehler/390 Warnungen. Neuer Build separat zu prüfen.
- Bekannte Quellvertragschecks 13/19/83/85/86 bleiben rot.
- Pixel/Poco-/Server-E2E offen. Verlauf-ZIP-Absturz nicht als behoben bestätigt. Bestehende Doppelbilder nicht gelöscht.

NÄCHSTER TEST:
- Update mit bisheriger Signierung; bei Signaturkonflikt nicht deinstallieren/Daten löschen.
- Eigenes Foto zweimal synchronisieren, empfangenes Foto teilen, Widget nach Neustart und Verlauf-ZIP prüfen.
- Debug-APK; kein Serverkatalog-Release/NAS-Deployment.
