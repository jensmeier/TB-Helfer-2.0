TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.15
BUILD: 200065
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

BEHOBEN:
- Beim langen Drücken auf einen manuellen BTB-Eintrag wird die Zielauswahl für „Verschieben“ jetzt sichtbar als Liste angezeigt
- Der bisherige Dialog zeigte zwar „Eintrag verschieben“, aber keine auswählbaren Ziele
- Das aktuelle Ziel wird aus der Liste entfernt, damit nur echte Verschiebeziele angeboten werden
- Falls kein anderes Ziel existiert, wird „Kein anderes Ziel vorhanden“ angezeigt

UNVERÄNDERT AUS 1.3.14 / 200064:
- Bilder zu einem gespeicherten manuellen BTB-Eintrag werden groß in **einer horizontalen Reihe** angezeigt
- Die Bildreihe ist nach links / rechts scrollbar bzw. per Wischgeste bedienbar
- Bilder können groß geöffnet werden
- Weitere Bilder können nachträglich über Kamera oder Galerie/Dateien ergänzt werden
- Zugeordnete Bildanzahl wird am Eintrag mit 📷 angezeigt
- Beim Verschieben wird der vorhandene BTB-Fotokontext mitgeführt
- Der bestätigte BTB-Lifecycle-Fix aus 1.3.13 / 200063 bleibt unverändert

TESTSTATUS:
- Build 200064 ist lokal erfolgreich durchgelaufen und auf dem Gerät installiert
- Praktischer Test 200064 bestätigt Bild-Nachtrag, Bildvorschauen und Long-Press-Menü
- Praktischer Fehler gefunden: Zielauswahl „Verschieben“ leer
- Fix ist in 1.3.15 / 200065 im Quellstand umgesetzt
- Build 200065, Installation und erneuter Praxistest sind noch offen

HINWEIS:
- Keine Serveränderung erforderlich
- Serververtrag btbId-/Foto-Synchronisation bleibt unverändert

BUILD-HINWEIS 23.09.2026 14:39:
- Erster lokaler Build 200065 fehlgeschlagen mit 3x Unresolved reference 'LayoutParams'
- Ursache: HorizontalScrollView.LayoutParams war im Kotlin-Quelltext ungültig
- Behoben: photoScroller.addView(photoRow) verwendet die passenden Child-LayoutParams automatisch
- Fix-Stand: 90076c0d43405b3b577e54925969bdee1b7d7936
- Build 200065 muss erneut ausgeführt werden
- BUILD ERFOLGREICH, INSTALLIERT und E2E bleiben offen
