TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.14
BUILD: 200064
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Gespeicherte manuelle BTB-Einträge zeigen beim Öffnen die Bilder, die zu Firma/Ort/Text dieses Eintrags gehören
- Bilder werden im Eintrag als große Vorschauen mit zwei Bildern pro Reihe angezeigt und können groß geöffnet werden
- Über „Bild hinzufügen“ können zu einem bereits gespeicherten Eintrag nachträglich weitere Bilder über Kamera oder Galerie/Dateien ergänzt werden
- Lange auf einen manuellen BTB-Eintrag drücken öffnet jetzt die Aktion „Verschieben“

VERBESSERT:
- Ein manueller Eintrag zeigt bereits in der BTB-Liste mit 📷 die Anzahl der zugeordneten Bilder
- „Eintrag verschieben“ bietet Allgemein und die im BTB vorhandenen Firmen als Ziel an
- Beim Verschieben werden bereits zugehörige BTB-Fotos im vorhandenen Foto-Kontext mitgeführt
- Ort/Unterbaustelle bleibt weiterhin über den normalen Eintrag-Editor änderbar
- Der bestehende Kamera-/Galerie-/BTB-Fotopfad wird wiederverwendet; keine zweite Fotoablage und keine Server-Ersatzlösung

BEHOBEN:
- Nachträglich erfasste Bilder müssen nicht mehr über einen neuen Text-/Fotoeintrag nachgebaut werden
- Ein unter einer falschen Firmenüberschrift abgelegter manueller BTB-Eintrag kann ohne Löschen und Neueingabe verschoben werden

HINWEIS:
- Build 200064 baut auf dem praktisch bestätigten BTB-Lifecycle-Fix 1.3.13 / 200063 auf
- Der Serververtrag wird nicht geändert; die bestehende btbId-/Foto-Synchronisation bleibt erhalten
- Bilder werden einem manuellen Eintrag über den bestehenden fachlichen Kontext Firma + Ort + Text zugeordnet
- Direkte Source-Prüfung bestanden: 200064-Funktionsmarker vorhanden; bestehender BTB-Build-Source-Generator-Anker weiterhin vorhanden
- Android-Build, Installation und praktischer Pixel/Poco-Test sind noch offen

BUILD-HINWEIS 23.09.2026:
- Erster lokaler Buildversuch 07:25 fehlgeschlagen: Unresolved reference 'labelForOrtCached' im generierten BautagebuchFragment
- Ursache behoben in f070fab9277f3296b177f428160972b2f732edaf
- Die redundante autoPhotoOrtLabel-Übergabe wurde entfernt; die bestehende Ort-Auflösung im BtbPdfAuswahlFragment bleibt maßgeblich
- Build 200064 muss erneut ausgeführt werden; BUILD ERFOLGREICH ist noch offen
