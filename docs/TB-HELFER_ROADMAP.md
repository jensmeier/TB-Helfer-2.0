# TB-Helfer – Entwicklungsroadmap

Stand: 2026-09-16

Diese Roadmap sammelt die noch offenen Änderungswünsche aus Projektregeln, aktuellem GitHub-Stand, Chat-Übergaben und den im Projekt verfügbaren bisherigen Chats. Sie ist **keine zweite Source of Truth für Fachregeln**: Fachliche Regeln stehen weiterhin in `docs/TB-HELFER_PROJEKTREGELN.md`, der aktuelle technische Stand in `docs/TB-HELFER_AKTUELLER_STAND.md`.

## Grundstrategie

**Zuerst Android stabil und vollständig machen, danach den produktiven Server ausbauen.**

Ausnahme: Wenn eine Android-Funktion zwingend einen noch fehlenden Serververtrag/API-Endpunkt benötigt, wird auf Android zuerst das Datenmodell/UI vorbereitet und die produktive Serveränderung in die Serverphase verschoben. Keine produktive NAS-/DB-Migration nur für einen Zwischen-Test.

Für jeden Update-Block gilt:

1. aktuellen GitHub-Stand prüfen;
2. bestehenden Code und dokumentierte Entscheidungen verstehen;
3. genau einen zusammenhängenden Funktionsblock ändern;
4. Unit-/Source-Checks und lokalen Android-Build ausführen;
5. Poco testen;
6. Pixel bzw. Zwei-Geräte-Verhalten testen, wenn relevant;
7. Offline -> Online testen, wenn Sync betroffen ist;
8. `docs/TB-HELFER_AKTUELLER_STAND.md` aktualisieren;
9. erst danach den nächsten Block beginnen.

Keine parallelen Ersatzsysteme bauen. Vorhandene Speicher-, Sync-, Foto-, BTB- und Vermessungspfade erweitern.

---

# PHASE A – ANDROID ZUERST

## A0 – Aktuellen 200049-/Auto-Sync-Stand stabilisieren

Ziel: Den derzeitigen Branch sauber auf Poco und Pixel bestätigen, bevor neue Funktionen dazukommen.

Aktueller Stand aus den letzten Gerätetests:
- lokaler 200049-Build nach dem `colorError`-Fix war erfolgreich;
- Live-KM wurde praktisch als funktionierend bestätigt;
- Foto-Erfassen-Maske mit KM/GPS/Strecke wurde bestätigt;
- 10/30-m-Ampel wurde bestätigt;
- Foto-Aufdruck im gespeicherten JPG fehlte, weil die Foto-Overlay-Einstellungen beim Verlassen wieder auf aus springen;
- `Bilder` war im ersten Gerätetest noch vertikal gestapelt; der horizontale Button-Fix ist danach versioniert worden und muss noch praktisch nachgetestet werden;
- BTB-Auto-Sync nach lokalem Speichern ist versioniert, aber nach diesem neuen Commit noch nicht lokal gebaut und auf Poco/Pixel getestet.

Noch testen:
- aktueller Branch `Fetch -> Pull -> Clean -> Build`;
- `Bilder | Abbrechen | Hinzufügen` wirklich horizontal;
- Poco -> BTB ändern/speichern -> **ohne manuellen Sync** auf Pixel übernehmen;
- Pixel -> Poco in Gegenrichtung;
- offline BTB speichern -> Netz wieder an -> automatische Nachlieferung;
- abgeschlossener BTB bleibt im Verlauf und wird nicht automatisch reaktiviert;
- nur `BTB bearbeiten` reaktiviert dieselbe `btbId`.

Abschlusskriterium A0:
- Build grün;
- kein Datenverlust;
- Auto-Sync-Grundweg praktisch bestätigt;
- keine Regression bei Kamera/KM.

## A1 – Einstellungen komplett prüfen, reparieren und neu strukturieren

Dieser Block kommt früh, weil bereits ein echter Persistenzfehler bestätigt ist: Foto-Aufdruck-Schalter schalten sich nach Verlassen der Seite wieder aus.

### A1.1 Technische Prüfung jeder Einstellung

Für **jede** vorhandene Einstellung prüfen:
- Wert laden;
- Änderung speichern;
- zurück -> erneut öffnen -> Wert bleibt;
- App vollständig schließen -> erneut öffnen -> Wert bleibt;
- Hell/Dunkel/System wechseln -> Wert bleibt;
- prüfen, ob die Einstellung tatsächlich eine Wirkung in der App hat;
- doppelte, tote oder widersprüchliche Einstellungen entfernen;
- Initialisieren einer Einstellungsseite darf keine Listener auslösen, die andere Werte mit Standardwerten überschreiben;
- Schalter/Radio-Werte automatisch speichern oder eindeutig über `Speichern`, aber nicht gemischt/unklar.

### A1.2 Neue Hauptstruktur

Die erste Einstellungsseite soll logisch werden:

1. **Profil & Firma**
   - persönliche Daten;
   - Firmendaten;
   - Firmenlogo;
   - Gerätestatus/Aktivierung;
   - letzte Serveraktualisierung.

2. **Anzeige**
   - System / Hell / Dunkel;
   - Such-/Listenansicht;
   - Spalten/Ansicht;
   - Startbildschirm-Widget hier integrieren, kein eigener Hauptpunkt.

3. **Kamera & Fotos**
   - Fotoqualität;
   - `Foto-Aufdruck` statt technisch unklarem Begriff `Overlay`;
   - Baustelle, Unterbaustelle, Strecke/Gleis, KM, GPS, Datum/Uhrzeit;
   - Position, Schriftgröße, Hintergrund/Transparenz, Kontrast soweit bestehend/sinnvoll.

4. **BTB & PDF**
   - Fotoanzahl/Raster;
   - Bildunterschriften/Felder;
   - PDF-Kopf;
   - Firmenlogo im PDF;
   - Vorschau.

5. **Synchronisation**
   - Auto-Sync;
   - WLAN/Mobilfunk;
   - letzter erfolgreicher Sync;
   - bewusster manueller Sync.

6. **Benachrichtigungen**
   - nur echte Benachrichtigungsoptionen.

7. **Wartung & Diagnose**
   - App-/DB-/Sync-Informationen;
   - Diagnose;
   - Speicherbereinigung und Wartungsfunktionen.

### A1.3 Was pro Gerät bleibt und was später vom Server kommt

**Gerätespezifisch, nicht automatisch Poco <-> Pixel synchronisieren:**
- Hell/Dunkel/System;
- Widget;
- Fotoqualität;
- Foto-Aufdruck und dessen Darstellung;
- lokale Anzeige-/Listenoptionen;
- Benachrichtigungseinstellungen.

**Zentral/serverbezogen:**
- Firmenname, Firmenanschrift, Firmenkontakt, Firmenlogo;
- Benutzeridentität;
- persönliche Telefonnummer/E-Mail, sobald der produktive Serverstand dies unterstützt;
- firmenweites PDF-Logo-Layout, wenn als Firmenstandard festgelegt;
- Gerätestatus/Aktivierungsstatus;
- Baustellen-/Team-/Berechtigungsdaten.

Abschlusskriterium A1:
- kein bestätigter Einstellungswert springt mehr zurück;
- neue 7-Bereiche-Struktur ist verständlich;
- jede sichtbare Einstellung hat nachweisbare Wirkung.

## A2 – Kamera, Fotos und Foto-Aufdruck fertigstellen

Ziel: Livebild, Foto-Maske, gespeichertes JPG, Foto-Detail und PDF zeigen konsistente Daten.

Prüfen/fertigstellen:
- Foto-Aufdruck bleibt gespeichert und wird tatsächlich ins JPG eingebrannt;
- KM im Aufdruck groß/fett;
- KM/GPS/Baustelle/Strecke-Gleis/Firma in allen Stufen identisch;
- keine falschen Hilfsnamen wie `Umbau Heideck`, wenn Hauptbaustelle `Umbau` ist;
- `Strecke/Gleis: 5362-1` statt unpassendem `Strecke/Bahnhof: -`, wenn entsprechende Struktur vorhanden ist;
- 10/30-m-Ampel unverändert lassen, solange kein reproduzierbarer Fehler auftritt;
- GVP-/Mast-/AP-/KOR-Berechnung nicht ohne reproduzierbaren Fehler ändern;
- externe Fotos/Galeriebilder sauber importieren;
- normale Baustellenfotos ohne `btbId` bleiben außerhalb moderner BTBs;
- BTB-Fotos behalten exakt ihre `btbId`.

Danach Auto-Sync für **echte Foto-Speicheraktionen** prüfen/ergänzen:
- lokal zuerst speichern;
- danach vorhandenen Sofort-/Offline-Sync anstoßen;
- nicht bei jedem UI-Klick oder Texteingabe;
- offline aufgenommenes Foto später nachliefern;
- keine Duplikate.

## A3 – BTB als Zwei-Geräte-/Mehrhelfer-System vollständig testen

Verbindliches Modell beibehalten:
- ein verantwortlicher TB + seine Helfer teilen ihren eigenen BTB-Bereich;
- anderer verantwortlicher TB auf derselben Baustelle hat einen getrennten BTB-Bereich;
- mehrere BTBs pro Tag möglich;
- Tag/Nacht und Mitternacht ändern die `btbId` nicht;
- gleicher offener BTB auf Poco/Pixel = exakt gleiche `btbId`;
- Abschluss löst aktive `btbId`;
- Verlauf bleibt erhalten;
- `BTB bearbeiten` reaktiviert exakt denselben BTB.

Praktische Tests:
- Poco TB / Pixel Helfer;
- Pixel ändert Arbeit/Firma/Foto -> Poco übernimmt;
- Poco ändert -> Pixel übernimmt;
- beide Geräte kurz nacheinander ändern -> Konfliktverhalten dokumentieren;
- 2 verantwortliche TBs mit jeweils Helfern dürfen ihre BTBs nicht vermischen;
- großer Baustellenfall mit Tag-/Nachtteams;
- Offline-Queue in beide Richtungen.

Zusätzliche BTB-UI-Wünsche aus früheren Chats:
- Dunkelmodus-Kontrast im Dialog `Neuen BTB starten` verbessern;
- Start-/Endzeit über grafischen TimePicker statt alter Dropdown-Auswahl;
- nach erstem Abschluss verständlicher Hinweis: erster Abschluss ist das unveränderliche Original; spätere fachliche Änderungen werden rot dokumentiert;
- optional dort später eine sinnvolle ZIP-/Teilen-Aktion anbieten, ohne Abschlusslogik zu vermischen.

## A4 – Baustellen-, Unterbaustellen- und Bereichsstruktur auf Android

Problem aus Gerätetests: Poco kennt z. B. eine lokale/verbundene Baustellenstruktur, Pixel teilweise nur die Hauptbaustelle.

Android-seitig fertig vorbereiten und darstellen:
- mehrere lokale Baustellen gleichzeitig;
- genau eine `aktuelle` Baustelle;
- Hauptbaustelle -> Unterbaustellen -> Strecke/Gleis/Weichen/Objekte;
- Beispiele `5362-1`, `Durchlass`;
- stabile IDs statt sichtbarer Namen;
- verantwortlicher TB sieht die gemeinsame vollständige Struktur;
- Helfer sieht nur den freigegebenen Bereich;
- beim Wechsel der aktuellen Baustelle niemals BTB-/Fotodaten anderer Baustellen mischen;
- Teamumfang und letzter Sync-Stand sichtbar.

Wenn der produktive Server noch nicht genügend Struktur liefert, Android-Datenmodell/UI fertigstellen und den fehlenden Serververtrag explizit für Phase S2 dokumentieren statt einen zweiten lokalen Sonderweg zu bauen.

## A5 – Profil, Firma und Gerät auf Android sauber zusammenführen

Zielbild nach Aktivierung:
`QR/Aktivierung -> Benutzer/Firma/Logo laden -> lokal cachen -> BTB/PDF verwendet dieselben Stammdaten`.

Profilseite soll enthalten:
- sichtbarer Benutzername;
- persönliche Telefonnummer/E-Mail lokal bearbeitbar;
- Firma;
- Anschrift;
- Firmen-Telefon/-E-Mail;
- Firmenlogo;
- Gerätestatus aktiviert/nicht aktiviert;
- letzte Serveraktualisierung;
- maskierte, nicht geheime Support-/Aktivierungskennung.

Für späteren Serverstand UI/Modell vorbereiten:
- gültig bis;
- Resttage;
- Warnung `LÄUFT BALD AB`;
- niemals vollständige Schlüssel anzeigen.

## A6 – PDF, DOCX, Abschluss und Archive lokal fertig prüfen

Offene Wünsche/Prüfungen:
- Fotos mit gleichen fachlichen Daten im PDF genauso sinnvoll gruppieren wie im Abschnittseditor, inklusive Zeitspanne/Bildbereich;
- Original-/Nachtragsgruppen korrekt trennen;
- neue/geänderte fachliche Inhalte rot;
- entfernte Originalinhalte rot `(entfernt) ...`;
- Strukturüberschriften bleiben schwarz;
- Fotozeile im Hauptteil und Foto im Anhang müssen dieselbe Rotlogik haben;
- pro `btbId` höchstens ein gültiges Abschluss-PDF;
- Einzel-BTB-DOCX langfristig dieselbe Original-/Rotlogik wie PDF;
- Tagesabschluss-ZIP;
- Zeitraum-ZIP;
- Gesamtarchiv;
- PDF/DOCX/Bilder vollständig und ohne doppelte/falsch zugeordnete Fotos;
- Laptop/Windows-Ausgabe später auf denselben Datenstand aufsetzen, nicht eigene Fachlogik erfinden.

## A7 – Mängel, Abnahmen und weitere mobile Arbeitsabläufe lokal verbessern

Bevor Server-Sync erweitert wird, Android-Abläufe und lokale Speicherung stabil machen:
- Mängel lokal vollständig/offline;
- Abnahmen lokal vollständig/offline;
- Fotos/Anhänge eindeutig zugeordnet;
- SKV-Abnahme inklusive GPS als späterer Fachblock;
- vereinfachte Mangelaufnahme als gewünschter UX-Pfad: `Was? -> Wo? -> Was ist los?`;
- Spracheingabe als späterer Android-Komfortblock;
- externe Fotos sauber in passende Fachvorgänge übernehmen.

## A8 – Vermessung/Streckendaten auf Android fachlich fertigstellen

Bestehende funktionierende KM-/GVP-Logik nicht ohne reproduzierbaren Fehler umbauen.

Langfristiges Android-Ziel aus den früheren Chats:
- TRA/GRA/KF/KOR/KV usw. lesbar und nachvollziehbar;
- rohe Originaldaten unverändert;
- nur benötigte Daten für die aktuelle Baustelle offline halten;
- bearbeitete/berechnete Abschnitte getrennt vom Original;
- mehrere Streckenbereiche später verbinden können, z. B. 40–70, 70–80, 80–130;
- grafische Übersicht der Gleise/TRA und des Bearbeitungsstands;
- fehlende Bereiche klar erkennbar und nacharbeitbar;
- Farbregeln beibehalten: grün belastbare Original-/Absteckungs-KM, orange manuell bearbeitet, rot TRA ohne belastbare KM, grau ohne Vermessungsgeometrie, blau nur Auswahl;
- bei Strecke ist Streckennummer Pflicht, KM-Bereich nicht grundsätzlich Pflicht;
- Weichen/DKW/Kreuzungen weiterhin fachlich korrekt berücksichtigen.

## A9 – Android technische Bereinigung und Release-Gate

Erst nach stabilen Funktionen:
- generierte Source-Brücke seit 200043 sauber zurückbauen;
- bekannte Gradle-/Configuration-Cache-Warnungen bereinigen;
- tote Legacy-Pfade entfernen, ohne Altbestand unlesbar zu machen;
- GitHub Actions wieder als verlässlichen Buildindikator herstellen;
- Unit-/Source-/Integrationstests ausbauen;
- Android-Daten-/API-Verträge für die Serverphase einfrieren;
- stabile Android-Testversion auf Poco und Pixel dokumentieren.

**Erst wenn A0–A9 in den für den Produktivbetrieb nötigen Teilen stabil sind, beginnt die produktive Serverausbauphase.**

---

# PHASE S – SERVER / NAS DANACH

Produktive Änderungen nur nach frischem verifiziertem Backup. NAS bleibt Source of Truth. Cloudflare für normalen App-Sync, Tailscale nur Wartung/Administration.

## S0 – Backup, Staging, Migrationsplan
- vollständiges verifiziertes PostgreSQL-/relevantes Dateibackup;
- aktuelle produktive Version dokumentieren;
- Migration zuerst Staging/Test;
- Rollback festlegen;
- keine Secrets in GitHub/Logs.

## S1 – Benutzer, Firma, Gerät und PDF-Layout

Vorhandene vorbereitete Serverstände sauber produktiv machen, statt neu zu erfinden:
- persönliche Benutzerkontakte (vorbereitetes Schema 11 prüfen);
- firmenweites PDF-/Logo-Layout (vorbereitetes Schema 12 prüfen);
- Firmenstammdaten/Logo als zentrale Quelle;
- Gerätestatus;
- Aktivierungs-/Ablaufdatum und Resttage für Android-Profilseite;
- maskierte Supportkennung, keine geheimen Schlüssel im UI;
- Aktivierungskarte/QR: Schlüssel serverseitig sicher erzeugen, Gerät aktivieren, auf Handy sicher speichern;
- Drucken/PDF der Aktivierungskarte als ein gemeinsamer Vorgang, keine doppelte Fachfunktion.

## S2 – Baustellen-/Team-/Unterbaustellen-Sync vollständig
- Verantwortliche TBs und Helfer;
- gleiche Firma Pflicht;
- mehrere verantwortliche TBs auf derselben Baustelle;
- getrennte BTB-Gruppen;
- Hauptbaustelle/Unterbaustellen/Strecke/Gleis/Weichen/Objekte;
- Verantwortlicher TB vollständige Struktur;
- Helfer nur freigegebener Bereich;
- Poco angelegte/verbundene Struktur muss entsprechend der Rechte auf Pixel verfügbar werden;
- Entfernen/Verlassen serverseitig sauber behandeln;
- historische Daten danach nur lesbar, kein weiterer Sync.

## S3 – BTB- und Foto-Sync härten
- stabile `btbId` als Identität;
- idempotente Uploads;
- Konflikte zwischen Geräten nachvollziehbar;
- abgeschlossene BTBs bleiben abgeschlossen;
- Reaktivierung nur bewusst;
- BTB-Fotos strikt gleiche `btbId`;
- normale Baustellenfotos bleiben außerhalb;
- Offline-Nachlieferung;
- keine Duplikate;
- große Baustellen/mehrere Helfer testen.

## S4 – Mängel und Abnahmen synchronisieren
- Status/Kommentare;
- Anhänge/Bilder;
- Berechtigungen;
- Offline-Nachlieferung;
- Konfliktregeln;
- SKV-/GPS-Daten, sobald Android-Fachmodell feststeht.

## S5 – Vermessungs-/Streckendaten auf NAS

Ziel aus den früheren Chats:
- Originale Vermessungsdaten dauerhaft unverändert auf NAS;
- App lädt nur benötigte Abschnitte/Dateien;
- nur tatsächlich bearbeitete/berechnete Abschnitte zurück synchronisieren;
- andere Geräte können gezielt benötigte Gleise/Abschnitte laden;
- langfristig Baustellenabschnitte wiederfinden, auch Jahre später;
- mehrere Bereiche einer Strecke logisch zusammenführen;
- Bearbeitungsstand serverseitig nachvollziehbar;
- keine mobile Bearbeitung überschreibt Original-TRA/GRA/KF/KOR.

## S6 – Admin-/Windows-/Laptop-Funktionen
- Benutzer/Firma/Geräte verwalten;
- Aktivierungskarten drucken/PDF;
- Baustellen-/Speicherstatus;
- Windows/Laptop BTB/Mängel/Abnahmen anzeigen/ausgeben und später ggf. bearbeiten;
- dabei dieselben IDs, Regeln und Serverdaten verwenden wie Android;
- `Mein Ordner`/Freigaben mit `lesen` bzw. `lesen+bearbeiten` als späterer Berechtigungsblock.

## S7 – End-to-End, Betrieb und Wiederherstellung
- Poco + Pixel + Server Gesamttest;
- Online/Offline/Netzwechsel;
- mehrere TBs/Helfer;
- große Foto-/BTB-Mengen;
- Backup/Restore praktisch testen;
- Monitoring/Logs ohne Geheimnisse;
- Cloudflare-Sync und Tailscale-Wartungsweg dokumentieren.

---

# Spätere Komfort-/Erweiterungswünsche

Diese Wünsche sind festgehalten, aber sollen die Stabilisierung nicht blockieren:
- Spracheingabe;
- Niederschlagsmenge zusätzlich in mm bzw. l/m²;
- vereinfachte Mangelaufnahme `Was? -> Wo? -> Was ist los?`;
- SKV-Abnahme inkl. GPS;
- `Mein Ordner` mit abgestuften Freigaben;
- Windows/Laptop-Bearbeitung;
- iOS erst auf stabilem Android-/Server-Fachmodell aufbauen.

---

# Priorität ab jetzt

1. **A0 aktuellen Stand bauen und Poco/Pixel-Auto-Sync testen.**
2. **A1 Einstellungen reparieren und neu strukturieren.**
3. **A2 Foto-Aufdruck/Fotos vollständig fertigstellen.**
4. **A3 BTB-Zwei-Geräte-/Mehrhelfer-Verhalten absichern.**
5. **A4 Baustellen-/Unterbaustellenstruktur auf Android.**
6. Danach A5–A9.
7. Erst anschließend produktive Serverphase S0–S7.

Wenn ein neuer Wunsch im Chat entsteht, wird er künftig entweder einer bestehenden Phase zugeordnet oder als neuer Roadmap-Punkt versioniert, statt nur im Chat stehen zu bleiben.
