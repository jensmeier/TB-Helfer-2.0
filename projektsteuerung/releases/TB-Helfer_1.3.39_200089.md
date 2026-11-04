TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.39
BUILD: 200089
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL: Noch nicht veröffentlicht; APK und Begleitdatei gemeinsam als Buildartefakt.

## Änderungen und Fehlerbehebungen
- Mikrofon-Widget startet eigenen sichtbaren Aufnahmedienst statt der letzten App-Seite. Erneut tippen oder Stop in der Benachrichtigung beendet die Aufnahme.
- Rot erst bei bereitem Mikrofon; kurze Start-/Endvibration. Separate antippbare Zahl zeigt offene lokale Sprachentwürfe des aktiven BTB. Widget öffnet nach Aufnahme keinen Prüfdialog.
- BTB-Mikrofon behält seinen Prüfdialog und bisherige Voreinstellungen.
- Gemeinsame Erkennung mit ungefähr fünf Sekunden Sprechpause als Ziel. Kurze Erkennungssitzungen werden zu einem Entwurf zusammengefügt. Teiltext bleibt bei Unterbrechung erhalten, verspätete Ergebnisse werden verworfen. Kein automatischer Neustart nach Prozessende.
- Firmenvergleich gegen konkrete BTB-Firmen mit konservativer Ähnlichkeit. Punkte 4–8 mit zusätzlichen Begriffen und ausdrücklich gesprochenen Punktnummern. Mehrdeutigkeit bleibt offen.
- Firmen-Schreibweisen ausschließlich ausdrücklich merken, lokal an Anmeldung/Baustelle/Firma gebunden. Löschfunktion unter Einstellungen Spracheingabe. Keine automatischen Änderungen am Beschreibungstext.
- Technische Aufnahmeabbrüche im bestehenden Fehlerprotokoll ohne Rohtext/Audio/Secrets.

## Grenzen und Nachweise
Keine Server-/API-Änderung. Übernahme ins BTB weiterhin nur nach Bestätigung und vorhandenen Rechten-/Lifecycle-/Outbox-Prüfungen.
Sprachdienste können Segment-/Pausenvorgaben ignorieren; beim Wechsel kurzer Erkennungssitzungen können geräteabhängige Übergangslücken auftreten. Pixel-/Poco-Mikrofontests und Server-E2E offen.
Erster Start ohne Mikrofonfreigabe öffnet die Freigabeansicht. Keine Sprache: begrenztes Startfenster; Sicherheitsende spätestens nach fünf Minuten pro Aufnahme. Bereits erkannter Text wird als Entwurf erhalten.
Quellbegleitdatei; erfolgreicher Build ergänzt APK-Prüfwert, Commit, Manifest und Signatur. CI-Debugsignatur vor Update mit installierter App vergleichen; keine Deinstallation/Datenlöschung.
