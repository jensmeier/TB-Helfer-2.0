# TB-HELFER – AKTUELLE OFFENE PUNKTE
Stand08.11.2026 – unabhängige Gesamtprüfung.
Geprüfter SERVER-Fachstand `b213bcbfe8d5fcf49c17e71108b8be04167f4d7f`; ANDROID-Fachstand `a8401775a3f607594df91468e728ee3f6d3a1ec4`.
Nachfolgende Dokumentationscommits verändern diese geprüften Fachstände nicht.
[Vollständiger Prüfbericht](uebergaben/GESAMTPRUEFUNG_2026-11-08.txt) · [Unveränderte vorherige Fassungen](archiv/GESAMTPRUEFUNG_2026-11-08/README.md).

Diese konsolidierte Liste unterscheidet neue reproduzierte Fehler, bekannte Lücken und fehlende Nachweise. Keine historische Erledigung wird durch Archivierung aufgehoben.
| ID / Priorität | Zuständig | Aktueller Befund / Nachweis | Nächster Schritt |
|---|---|---|---|
| AUDIT-1108-01 / P1 NEU | SERVER | Trassendaten-Liste und Detail ignorieren worksite.view=False sowie Helferbereich; tatsächliche Handler und worksite_context mit SQL-Doubles reproduziert | Leserecht und Bereich an beiden Endpunkten erzwingen; Listen-, Detail-, Fremdfirma- und Bereichstests; survey_sections mitprüfen |
| AUDIT-1108-02 / P1 NEU | SERVER | Fehler beim Entfernen der PDF-Sicherung nach erfolgreichem DB-Commit löst Datei-Rollback aus; DB FINALIZED und Dateihash passen danach nicht zusammen; realer Dateisystemtest mit DB-Double | Rollback vor Commit von nachgelagerter Bereinigung trennen; Fehler vor/nach Commit, Wiederholung und Nachbearbeitung testen |
| TRACK_KM_1103 / P1 BESTEHEND | ANDROID + SERVER-Vertrag | LEAD sendet lokalen Gesamtbestand, entfernt serverseitige Datensätze außerhalb dieses Bestands; 409-Neuversuch liest aktuelle Version ohne fachlichen Konfliktvergleich | Objektbezogene Outbox, stabile IDs und ursprüngliche Basisversion; fremde/unverwaltete Datensätze nicht löschen |
| TRACK_KM_1103 Empfang / P1 BESTEHEND | ANDROID | Helfer-Snapshot löscht lokale Achsen/Punkte vor vollständiger Validierung; kein gesamter atomarer Austausch | Erst validieren, dann Room-Transaktion; Dirty-Daten sichern; Fehler-/Abbruchtests |
| SHARED-KM-1103 / P1 BESTEHEND | ANDROID | Server-KM-Vertrag vorhanden, Android-Consumer und Transport ursprünglicher Anker/Genauigkeit fehlen | Vorhandenen /survey-catalog/axes/{id}/km-sync-Vertrag anbinden; kein zweites Katalogmodell |
| AUD-20261002-01 / P1 BESTEHEND | SERVER | MANUAL_GPS mit OVERLAP_CONFIRMED gegen ORIGINAL_TRA weiterhin sofort SUPERSEDED; im betreffenden Zweig kein Original-KM-/Vollabdeckungsschutz | Test Original ohne KM und Teilüberdeckung; Fachregel gezielt korrigieren; keine Neuöffnung des bestätigten WVB-Imports |
| AUD-20261002-02 / P1 BESTEHEND | SERVER | update_manual_segment übernimmt object_key weiterhin nicht in axis_existing | Reinen KM-/Text-PATCH bei W/WVB mit ausgelassener Objektkennung absichern |
| ADMIN-PDF-011 / P1 BESTEHEND | ANDROID | Logo-Breite auf 250 pt begrenzt; Server erlaubt 400 pt; Save normalisiert den Wert | Bestehenden 40–400-Vertrag übernehmen; bestätigte Servervorschau schützen |
| DEVICE-INFO-001 (= DEV-META-001) / P2 BESTEHEND | ANDROID | X-TB-App-Version / X-TB-App-Build in den untersuchten Android-Quellen nicht gesetzt | Zentral an authentifizierten Clients melden; keine Doppelaufgabe unter DEV-META-001 |
| Selektiver Fachsync / P1 BESTEHEND | ANDROID | Delta enthält keine DEFECT/ACCEPTANCE/TRACK_DATA-/globalen Survey-Ereignisse; allgemeiner Vollsync existiert | Fachtypen mit objektbezogenem Empfang ergänzen; Offline-/Cursor-/Rechtewechsel testen |
| EXPORT-1108B Signierung / P1 BESTEHEND | ANDROID | CI-APK 1.3.44 und vorherige CI-APK haben unterschiedliche Debugzertifikate; installierte Signatur unbekannt | Vorhandenen Installationsschlüssel sichern/verwenden; Update ohne Deinstallation prüfen |
| BTB-AUTO-016 / P1 BESTEHEND | SERVER + ANDROID | Vorgabe 16 Stunden dokumentiert, keine vollständige Ausführung in den geprüften Quellen nachgewiesen | Server als maßgebliche Fristinstanz, Android-Anzeige/Offline-Nachlauf; separate fachliche Umsetzung |
| CI-Vertragsprüfungen / P1 BESTEHEND, ergänzt | SERVER/ANDROID-Prüfpflege | Checks03/13/19/83/85/86 blockieren Android-Vertragsjob; Servercheck35 verlangt weiter Renderer5;102 Syntaxfehler;157 DOM-Harnessfehler | Veraltete Erwartungen von echten Lücken einzeln trennen; Funktionsprüfungen erhalten; keine Abschwächung für Grün |
| AUDIT-1108-03 / P1 STEUERUNG/CI | GEMEINSAM | Beide Branches enthalten veraltete Kopien des jeweils anderen Produkts; CI testet eigenen Checkout, nicht das aktuelle Branchpaar | Explizite Server-/Android-SHAs für Querprüfung verwenden; keine blinde Branchzusammenführung |
| NAS-/Geräteabnahme / P1 BESTEHEND | GEMEINSAM | Laufender NAS-Commit, Pixel-/Poco-Version und Zertifikat dieses Prüfstands nicht verifiziert | Versionszuordnung und reale Zwei-Geräte-/Portal-Matrix nach separater Übernahme; keine Diagnose allein aus alten 404-Bildern |
| SYNC-001 / P2 BESTEHEND | GEMEINSAM | 255 Delta-Wait-Clients als Teiltest bestanden; volle 15×15-Fach-/Foto-/Portal-Last offen | Gemischte Last messen, Dauerbetrieb und NAS-Ressourcen prüfen |
| Legacy-Restbacklog / OFFEN ZUR EINORDNUNG | jeweiliger Bereich | Nicht einzeln neu belegte ältere Punkte bleiben im unveränderten Archiv erhalten; weder erledigt noch neu reproduziert erklärt | Vor Bearbeitung gegen aktuelle Quellen/Belege abgleichen; geschützte Bestätigungen nicht neu öffnen |

## Nicht erneut als fehlend anlegen
Android1.3.44 ist gebaut; die alte generelle Gradle-Downloadblockade ist überwunden. Gesamt-CI und Geräteinstallation bleiben separat offen.
Foto-Metadaten-PATCH, kleine Bildversionen/Vorschaudownload, BTB-Wiederöffnung, aktiverPointer-Schutz, Fehlermeldung bei unvollständigem Medienexport, strukturierte photo_metadata und Connected/WLAN-Prüfung sind umgesetzt; keine pauschale Neuöffnung alter31.10-Behauptungen.
Server-final-pdf verwendet Teamleiterprüfung. Wetter0mm und aktuelle Labels nicht anhand alter Textmarker als erneut kaputt melden. DreiFotos/Reihe und Fotoanzahl bleiben geschützt.
## Vollständiger historischer Restbestand
[Vorherige offene Punkte](archiv/GESAMTPRUEFUNG_2026-11-08/07_OFFENE_PUNKTE.md), [vorheriger Auftrag](archiv/GESAMTPRUEFUNG_2026-11-08/02_AKTIVER_AUFTRAG.md) und [frühere Teststände](archiv/GESAMTPRUEFUNG_2026-11-08/06_E2E_TESTSTATUS.md) bleiben bytegleich erhalten. Ältere nicht hier einzeln neu belegte Aufgaben gelten als RESTSTATUS ZU VERIFIZIEREN, nicht automatisch erledigt. Vor neuer Entwicklung Quellen/zuletzt belegte Bestätigung lesen und in diese aktuelle Liste übernehmen, ohne Duplikat.
