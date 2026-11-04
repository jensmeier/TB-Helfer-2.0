TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.40
BUILD: 200090
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL: NICHT_VERÖFFENTLICHT

Neu:
- Spracheingabe prüft eine gesprochene Kopfzeile mit Firma, BTB-Punkt, Uhrzeit und Kilometer; Zeit/KM vor Übernahme einzeln bearbeiten.
- Originaler erkannter Text bleibt im lokalen Entwurf zur Kontrolle erhalten. Optional „Text:“ sprechen, um die Beschreibung ausdrücklich abzutrennen.

Verbessert:
- Punkt/Komma bei eindeutigen KM-Zahlen vereinheitlichen, Einzelwerte und Bereiche unterstützen. Nachtzeiten und rückläufige KM bleiben in gesprochener Reihenfolge.
- Gesprochene Abschnittsbezeichnung in der Kopfzeile hat Vorrang vor Tätigkeiten in der Beschreibung. Typische Schreibweisen wie „Besondenheiten“ werden erkannt.
- Unklare oder unvollständige Angaben bleiben sichtbar; ungültige bearbeitete Zeit-/KM-Felder blockieren die Übernahme. Alte Entwürfe bleiben lesbar, bearbeitete Entwürfe werden nicht erneut zerlegt.

Behoben:
- Bestätigte Sprachzeit-/KM-Angaben gelangen in die vorhandenen strukturierten BTB-Felder und damit den bestehenden Speicher-/Sync-/Dokumentpfad.

Hinweise:
- Lokale Vorschläge, erst ausdrückliche Übernahme schreibt ins BTB. Kein neuer Server-Endpunkt. Aufnahme, Widget und Voreinstellungen unverändert.
- Nur führende Kopfzeile wird ausgewertet; unbekannte Wörter beenden die automatische Zerlegung. Ausgeschriebene Zahlwörter werden nicht geraten. Bloße KM-Ziffernfolgen ab vier Stellen und getrennte Zahlen bleiben zur Prüfung im Text.
- Debug-APK. Installation als Update erfordert den bisherigen Signierschlüssel; keine Deinstallation/Datenlöschung zur Umgehung.
- Geräte-/Pixel-/Poco-/Server-E2E offen. Bekannte historische Quellvertragschecks der Gesamt-CI separat; Build-/Testnachweise werden am konkreten Lauf ergänzt.
