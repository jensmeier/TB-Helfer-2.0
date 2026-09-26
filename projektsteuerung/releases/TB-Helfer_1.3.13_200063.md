TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.13
BUILD: 200063
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

BEHOBEN:
- Aktiver BTB verschwindet nicht mehr sporadisch und taucht erst nach Verlassen/erneutem Öffnen wieder auf
- Historische reine Lesevorgänge (BTB-Liste, Vortag, Vorschläge, Statusabfragen) verändern den aktiven BTB-Pointer nicht mehr
- „BTB öffnen“/Schnellwechsel markiert eine bewusste Reaktivierung vollständig, damit der Lifecycle-Watcher den gewählten BTB nicht sofort wieder löst
- Navigation aus dem BTB-Verlauf lädt die übergebene stabile btbId deterministisch statt nur indirekt über Datum/Schicht
- Nach Rückkehr aus PDF/Foto/Mangel/Sync wird der kanonische aktive BTB bei geändertem Stand neu geladen, ohne ungespeicherte Eingaben zu überschreiben
- NACHBEARBEITUNG und ENTWURF bleiben aktiv; nur ein wirklich gültiger Abschluss wird automatisch aus dem aktiven Kontext gelöst
- Ein echter `BTB Abschluss` löst den aktiven Pointer jetzt sofort und räumt Reaktivierungsmarker auf; dadurch kann ein zuvor bewusst geöffneter BTB nach dem Abschluss nicht versehentlich aktiv bleiben

SYNC:
- Der Android-Sync entscheidet FERTIG/ENTWURF jetzt nach dem tatsächlichen BTB-Dokumentstatus statt nach Legacy-Feldern wie dokumentVersion
- Ein nachbearbeiteter BTB wird nicht mehr versehentlich erneut als FERTIG an den Server gesendet
- Ein serverseitig noch FINALIZED markierter, aber über active_btb_id wieder aktiver BTB wird nicht mehr durch das vorhandene Server-Abschluss-PDF lokal erneut geschlossen
- Server-Abschlussmarker werden bei einem aktiven/ENTWURF-Snapshot korrekt invalidiert

TECHNIK:
- Der alte Build-time-Patch für die aktive BTB-Ladelogik wurde entfernt; die Logik liegt jetzt direkt in BautagebuchFragment.kt
- BautagebuchSpeicher.laden() und ladenById() sind reine Lesewege ohne versteckte Änderung von .active_btb.json
- Version 1.3.13 / Build 200063 baut auf den 200062-Fixes für BTB-Schutz, XLSX-Bilderlisten und feste ZIP-Struktur auf

TESTSTATUS:
- Quellstand umgesetzt; Source-Checks bestanden
- Android-Build 200063 am 22.09.2026 in Android Studio erfolgreich abgeschlossen (4 Warnungen, keine Build-Fehler)
- Installation und der konkrete BTB-Lifecycle-Fix wurden am 23.09.2026 vom Benutzer praktisch bestätigt („passt und läuft“)
- Vollständiger Pixel↔Poco-Gesamt-E2E der übrigen Sync-Funktionen bleibt separat offen
