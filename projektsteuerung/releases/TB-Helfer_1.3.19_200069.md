TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.19
BUILD: 200069
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Android an den verbindlichen Server/Android-Syncvertrag vom 25.09.2026 angepasst
- Rollen- und Schichtstatus aus dem Server-BTB werden berücksichtigt
- Helfer-Schichtende und Haupt-TB-Abschluss werden getrennt behandelt
- Historische serverseitig FINALIZED-BTBs werden im automatischen Sync als read-only geschützt
- Stabile Server-Slots für BTB-Einträge werden beim Server→Android→Server-Rundlauf erhalten
- Punkt 4 „Ausgeführte Arbeiten“ unterstützt allgemeine Arbeiten ohne Firma als echte ARBEIT-Einträge
- Automatischer Server→Android-Delta-Pull mit persistentem Cursor und Long-Poll ist vorbereitet
- Strukturierte Foto-Metadaten und Server-Renditions sind in den Android-Syncpfad eingebunden
- Selektiver BTB-Outbox-/Retry-Pfad bleibt persistent und läuft bei vorübergehenden Fehlern weiter

VERBESSERT:
- Alte FINALIZED-BTBs erzeugen keine normalen Header-/Entry-/Foto-Mutationen mehr
- BTB-Dirty-Scope wird gezielt nur für den betroffenen BTB verarbeitet
- Serverstatus, Teilnehmerrechte und PDF-Sichtbarkeit werden aus dem kanonischen BTB-Snapshot übernommen
- Serveränderungen werden gezielt auf betroffene BTBs/Fotos angewendet statt einen allgemeinen Vollsync zu erzwingen
- Für servergebundene Haupt-TBs ist die Buttondarstellung jetzt ausschließlich am kanonischen Server-Lifecycle ausgerichtet

BEHOBEN:
- Buildfehler in BautagebuchPdf.kt behoben: nullable KM-Anzeige wird korrekt geprüft
- Drei fehlerhafte positionale uploadAttachment(...)-Aufrufe in CollabLocalBridge.kt auf benannte Parameter umgestellt
- Die im Android-Studio-Build vom 26.09.2026 sichtbaren 7 Kotlin-Compilefehler sind im Quellstand korrigiert
- Reproduzierbares UI-Flackern auf dem Pixel zwischen „BTB Abschluss“ und PDF-Anzeige behoben:
  bei einem servergebundenen Haupt-TB darf ein lokaler/staler PDF-Status einen serverseitig aktiven BTB nicht mehr als abgeschlossen darstellen
- ACTIVE bzw. bewusst wieder aktiver Server-BTB zeigt stabil „BTB Abschluss“; FINALIZED + nicht aktiv bleibt serverseitig schreibgeschützt und zeigt nur die erlaubte PDF-Ansicht

UNVERÄNDERT:
- Keine server/**-Dateien geändert
- Bestehender BTB-PDF-/Original-/Nachbearbeitungsvertrag bleibt erhalten
- Bestätigter Geräteaktivierungs- und Updateweg bleibt unverändert
- Bewusster „Neu laden“-Vollabgleich bleibt Recovery-Pfad

QUELLSTAND:
- Android-Branch: feature/tb-android-2.0
- UI-Flackerfix: 19e07feee5b2accea9dcacf0348afb759f8f2dd3
- Compilefix KM: 411c09e620386831ee3e841ffb476e9c975907c1
- Compilefix Attachment-Argumente: 0a22f17526a7e5dd3e5ac71cd4b027b7ecb4dfac
- Server geprüft: feature/tb-server-1.0
- Server-HEAD bei letzter Vertragsprüfung: c1204501fd2147452f2eb5e47e1c2b9c871b8ecd
- Letzte Serveränderungen betrafen Status-/Lasttest-Dokumentation; kein neuer Android-API-Vertrag

TESTSTATUS:
- Reproduzierbarer Pixel-UI-Fehler im aktuellen Android-Code eingegrenzt
- Source-Fix umgesetzt und committed
- Build-Wiederholung 1.3.19 / 200069 offen
- Installation offen
- Pixel/Poco-/Server-E2E offen
- Poco: serverseitig HELPER im richtigen Team bestätigt, erhält auf dem Gerät aber weiterhin keine Daten; Ursache noch einzugrenzen (Binding/Sync)
- GitHub Actions ist ohne tatsächlich ausgeführte Runner-Steps kein Buildnachweis

NÄCHSTER TEST:
- Android-Branch in Android Studio pullen
- Generate APKs erneut starten
- Pixel prüfen: kein Wechsel mehr zwischen „BTB Abschluss“ und PDF-Anzeige
- Poco prüfen: Wartung & Diagnose → „Neu laden“ und genaue Syncmeldung festhalten
- Danach Worksite-Binding/Sync auf Poco gezielt prüfen, ohne Benutzer/Einladung neu anzulegen

HINWEIS:
- STATUS.xlsx wird wegen abweichender Android-/Server-Blobs nicht blind überschrieben; Konsolidierung bleibt beim SERVER-Projekt.
