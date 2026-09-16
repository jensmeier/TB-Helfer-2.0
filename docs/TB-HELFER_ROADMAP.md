# TB-Helfer – Entwicklungsroadmap

Stand: 2026-09-16

Diese Roadmap sammelt die noch offenen Änderungswünsche aus Projektregeln, aktuellem GitHub-Stand, Chat-Übergaben und den im Projekt verfügbaren bisherigen Chats. Sie ist keine zweite Source of Truth für Fachregeln: Fachliche Regeln stehen in `docs/TB-HELFER_PROJEKTREGELN.md`, der aktuelle technische Stand in `docs/TB-HELFER_AKTUELLER_STAND.md`.

## Grundstrategie

**Zuerst Android stabil und vollständig machen, danach den produktiven Server/NAS ausbauen.**

Ausnahme: Wenn eine Android-Funktion zwingend einen noch fehlenden Serververtrag/API-Endpunkt benötigt, wird Android vorbereitet und die produktive Serveränderung ausdrücklich in die Serverphase verschoben. Keine produktive Migration nur für einen Zwischen-Test.

Für jeden Update-Block gilt:
1. aktuellen GitHub-Stand prüfen;
2. vorhandenen Code und Entscheidungen verstehen;
3. einen zusammenhängenden Funktionsblock ändern;
4. Source-/Unit-Checks und lokalen Android-Build ausführen;
5. Poco testen;
6. Pixel/Zwei-Geräte-Verhalten testen, wenn relevant;
7. Offline -> Online testen, wenn Sync betroffen ist;
8. `docs/TB-HELFER_AKTUELLER_STAND.md` aktualisieren;
9. erst danach den nächsten Block beginnen.

Keine parallelen Ersatzsysteme bauen. Vorhandene Speicher-, Sync-, Foto-, BTB- und Vermessungspfade erweitern.

---

# PHASE A – ANDROID ZUERST

## A0 – Android 200050 und Auto-Sync stabilisieren

Aktueller Kandidat: **200050**.

Bereits umgesetzt, aber noch lokal zu bauen/prüfen:
- BTB-Auto-Sync: lokal zuerst speichern, danach vorhandenen `SofortSync` anstoßen;
- Punkte 4–8 verwenden denselben direkten `Bilder`-Pfad und jeweils den passenden `Bildtyp`;
- `Bilder | Abbrechen | Hinzufügen` gleich breit/hoch und horizontal;
- moderner BTB-Medien-Sync strikt über dieselbe `btbId` statt Zeitfenster;
- Legacy-Zeitfenster nur für Altbestand ohne `btbId`;
- Hintergrund-Sync liest alle Baustellenfotos unabhängig vom gerade geöffneten UI-BTB;
- Firmenlogo im PDF bekommt ausdrücklich weißen Hintergrund.

Praktisch testen:
- `Fetch -> Pull -> Clean -> Build`;
- Punkte 4–8: je Text + Bild;
- Poco -> Pixel ohne manuellen Sync;
- Pixel -> Poco;
- offline speichern -> Netz wieder an;
- zwei überlappende BTBs dürfen Fotos nicht vermischen;
- abgeschlossener BTB bleibt im Verlauf und wird nicht automatisch reaktiviert;
- nur `BTB bearbeiten` reaktiviert dieselbe `btbId`;
- PDF-Firmenlogo weiß hinterlegt.

Abschlusskriterium A0:
- Build grün;
- Punkte 4–8 inkl. Bilder auf beiden Geräten richtig;
- kein Datenverlust/keine falsche Fotozuordnung;
- Kamera/KM ohne Regression.

## A1 – Einstellungen vollständig prüfen, reparieren und neu strukturieren

Bestätigter Fehler: Foto-Aufdruck-/Overlay-Schalter springen nach Verlassen der Seite wieder auf `aus`.

Für jede Einstellung prüfen:
- Wert laden;
- Änderung speichern;
- zurück -> erneut öffnen -> Wert bleibt;
- App schließen -> neu öffnen -> Wert bleibt;
- Hell/Dunkel/System wechseln -> Wert bleibt;
- sichtbare Einstellung hat tatsächlich Wirkung;
- tote/doppelte/widersprüchliche Optionen entfernen;
- Initialisierungs-Listener dürfen keine Standardwerte zurückspeichern.

Neue Hauptstruktur:
1. **Profil & Firma** – Person, Firma, Logo, Gerät/Aktivierung, letzte Serveraktualisierung.
2. **Anzeige** – System/Hell/Dunkel, Listen-/Spaltenansicht, Widget.
3. **Kamera & Fotos** – Fotoqualität und verständlicher `Foto-Aufdruck` statt technischem `Overlay`.
4. **BTB & PDF** – Fotoanzahl/Raster, PDF-Kopf, Logo, Vorschau.
5. **Synchronisation** – Auto-Sync, WLAN/Mobilfunk, letzter Sync, manueller Sync.
6. **Benachrichtigungen**.
7. **Wartung & Diagnose**.

Gerätespezifisch bleiben u. a. Theme, Widget, Fotoqualität, Foto-Aufdruck und Anzeigeoptionen. Firmenstammdaten/Logo, Benutzeridentität, Baustellen-/Teamdaten und später firmenweites PDF-Layout sind Serverdaten.

## A2 – Kamera, Fotos und Foto-Aufdruck fertigstellen

Ziel: `Live-Kamera -> Foto erfassen -> JPG -> Foto-Detail -> PDF` zeigt konsistente Daten.

Fertigstellen/prüfen:
- Foto-Aufdruck bleibt gespeichert und wird ins JPG eingebrannt;
- KM im Aufdruck groß/fett;
- KM/GPS/Baustelle/Strecke-Gleis/Firma überall identisch;
- `Strecke/Gleis: 5362-1` statt unpassendem `Strecke/Bahnhof: -`, wenn Struktur vorhanden;
- keine falschen Hilfsnamen;
- 10/30-m-Ampel beibehalten;
- GVP/Mast/AP/KOR nicht ohne reproduzierbaren Fehler verändern;
- externe Galerie-Fotos sauber importieren;
- normale Baustellenfotos ohne `btbId` bleiben außerhalb moderner BTBs;
- Foto speichern -> lokal zuerst -> vorhandenen Sync anstoßen -> offline später nachliefern.

## A3 – BTB als echtes TB-/Helfer-/Mehrgeräte-System

Verbindlich:
- ein verantwortlicher TB + seine Helfer teilen ihren eigenen BTB-Bereich;
- anderer verantwortlicher TB auf derselben Baustelle hat einen getrennten BTB-Bereich;
- gleicher offener BTB auf Poco/Pixel = gleiche `btbId`;
- Punkte **4–8 einschließlich Bilder** gehören zu genau diesem gemeinsamen BTB und müssen in beide Richtungen synchronisieren;
- mehrere BTBs pro Tag möglich;
- Mitternacht ändert die `btbId` nicht;
- Abschluss löst aktive `btbId`;
- Verlauf bleibt erhalten;
- `BTB bearbeiten` reaktiviert exakt denselben BTB.

Zusätzliche ältere Wünsche:
- Dunkelmodus-Kontrast im Dialog `Neuen BTB starten`;
- Start-/Endzeit über grafischen TimePicker;
- Hinweis nach erstem Abschluss: erster Abschluss ist unveränderliches Original, spätere fachliche Änderungen rot;
- ggf. später sinnvolle ZIP-/Teilen-Aktion nach Abschluss.

## A4 – Baustellen-/Unterbaustellen-/Strecke-Gleis-Struktur auf Android

Bekanntes Problem: Poco kennt z. B. `5362-1` und `Durchlass`, Pixel/Helfer teilweise nur die Hauptbaustelle.

Verbindliches Ziel:
- der Helfer soll gemeinsame Unterbaustellen **nicht manuell neu anlegen**;
- verantwortlicher TB erstellt/verwaltet die gemeinsame Struktur;
- Helfer erhält automatisch den freigegebenen Teilbaum;
- verantwortlicher TB erhält die für ihn vollständige gemeinsame Struktur;
- Hauptbaustelle -> Unterbaustellen -> Strecke/Gleis -> Weichen/Objekte;
- stabile IDs statt sichtbarer Namen;
- mehrere Baustellen lokal, genau eine aktuell;
- keine Datenvermischung beim Wechsel;
- Teamumfang und letzter Sync-Stand sichtbar.

Es gibt **keinen einfachen Server-Schalter**, der den heute fehlenden Baum-Sync aktiviert. Schema 10 besitzt Team-/Einladungs-/`area_scope`-Grundlagen, aber die vollständige lokale `BaustellenKnoten`-Synchronisation ist noch ein Entwicklungsblock. Android zuerst vorbereiten, fehlenden Serververtrag später in S2 ergänzen.

## A5 – Profil, Firma, Gerät und Firmenlogo

Ziel: `QR/Aktivierung -> Benutzer/Firma/Logo laden -> lokal cachen -> BTB/PDF verwendet dieselben Stammdaten`.

Profilseite:
- Benutzername;
- persönliche Telefon/E-Mail lokal bearbeitbar;
- Firma, Anschrift, Firmen-Telefon/-E-Mail;
- Firmenlogo;
- Gerätestatus;
- letzte Serveraktualisierung;
- maskierte Support-/Aktivierungskennung;
- später gültig bis / Resttage / `LÄUFT BALD AB`.

Firmenlogo:
- Standardformat **PNG**;
- transparentes PNG zulässig;
- Android-PDF zeichnet weißen Hintergrund;
- JPG/WebP weiterhin lesbar;
- SVG derzeit nicht verwenden, solange PDF-Renderer Bitmap-Dateien erwartet.

Servermodell dafür bereits in Schema 10 angelegt: Datei separat speichern, DB hält u. a. Pfad, MIME-Typ, SHA-256 und Version. Schema 12 betrifft später das firmenweite PDF-/Logo-Layout.

## A6 – PDF, DOCX, Abschluss und Archive

Offen:
- gleiche fachliche Fotos im PDF wie im Editor gruppieren, inkl. Zeitspanne/Bildbereich;
- Original-/Nachtragsgruppen korrekt trennen;
- neue/geänderte fachliche Inhalte rot;
- entfernte Originalinhalte rot `(entfernt) ...`;
- Strukturüberschriften schwarz;
- Fotozeile im Hauptteil und Fotoanhang gleiche Rotlogik;
- pro `btbId` höchstens ein gültiges Abschluss-PDF;
- DOCX langfristig gleiche Original-/Rotlogik;
- Tagesabschluss-ZIP, Zeitraum-ZIP, Gesamtarchiv;
- PDF/DOCX/Bilder vollständig und ohne falsche Zuordnung;
- Windows/Laptop später auf dieselbe Fachlogik aufsetzen.

## A7 – Mängel, Abnahmen und weitere mobile Abläufe

Android zuerst lokal/offline stabil:
- Mängel;
- Abnahmen;
- Fotos/Anhänge eindeutig;
- SKV-Abnahme inkl. GPS als späterer Fachblock;
- vereinfachte Mangelaufnahme `Was? -> Wo? -> Was ist los?`;
- Spracheingabe;
- externe Fotos in passende Fachvorgänge übernehmen.

## A8 – Vermessung / Streckendaten Android

Bestehende funktionierende KM-/GVP-Logik nicht ohne reproduzierbaren Fehler umbauen.

Langfristig:
- TRA/GRA/KF/KOR/KV nachvollziehbar;
- Originaldaten unverändert;
- nur benötigte Daten offline;
- bearbeitete/berechnete Abschnitte getrennt vom Original;
- Bereiche z. B. 40–70, 70–80, 80–130 kombinierbar;
- grafische Übersicht der Gleise/TRA und Bearbeitungsstände;
- Farbregeln: grün belastbare Original-KM, orange manuell, rot TRA ohne belastbare KM, grau ohne Geometrie, blau Auswahl;
- Weichen/DKW/Kreuzungen fachlich berücksichtigen.

## A9 – Android technische Bereinigung / Release-Gate

Erst nach funktionaler Stabilität:
- Generated-Source-Brücke seit 200043 zurückbauen;
- Gradle-/Configuration-Cache-Warnungen bereinigen;
- tote Legacy-Pfade entfernen, Altbestand lesbar halten;
- GitHub Actions wieder zuverlässig machen;
- Unit-/Source-/Integrationstests ausbauen;
- Android/API-Verträge für Serverphase einfrieren;
- stabile Version auf Poco und Pixel dokumentieren.

---

# PHASE S – SERVER / NAS DANACH

Produktive Änderungen nur nach frischem verifiziertem Backup. NAS bleibt Source of Truth. Cloudflare normaler App-Sync, Tailscale Wartung/Administration.

## S0 – Backup, Staging, Migrationsplan
- PostgreSQL-/Dateibackup verifizieren;
- produktiven Stand dokumentieren;
- Migration zuerst Staging;
- Rollback festlegen;
- keine Secrets in GitHub/Logs.

## S1 – Benutzer, Firma, Gerät, Logo und PDF-Layout
- Schema 11 persönliche Kontakte prüfen/produktisieren;
- Schema 12 firmenweites PDF-/Logo-Layout prüfen/produktisieren;
- Firmenstammdaten/Logo zentrale Quelle;
- Gerätestatus, Aktivierungs-/Ablaufdatum, Resttage;
- Aktivierungskarte/QR sicher;
- maskierte Supportkennung.

Logo-Servermodell:
- vorzugsweise Original-PNG als Datei/Objekt;
- DB speichert Pfad, `image/png`, SHA-256 und Version;
- kein separater PDF-Logo-BLOB nötig.

## S2 – Baustellen-/Team-/Unterbaustellen-Sync vollständig
- gleiche Firma Pflicht;
- mehrere verantwortliche TBs;
- getrennte BTB-Gruppen;
- Hauptbaustelle/Unterbaustellen/Strecke/Gleis/Weichen/Objekte;
- Verantwortlicher TB vollständige erlaubte Struktur;
- Helfer nur freigegebener Bereich;
- Poco-Struktur entsprechend Rechten automatisch auf Pixel;
- Entfernen/Verlassen serverseitig;
- historische Daten danach nur lesbar.

## S3 – BTB- und Foto-Sync härten
- stabile `btbId`;
- idempotente Uploads;
- Konflikte nachvollziehbar;
- Punkte 4–8 und Bilder komplett;
- Offline-Nachlieferung;
- keine Duplikate;
- keine Fotovermischung zwischen BTBs/Teams.

## S4 – Mängel und Abnahmen synchronisieren
- Status/Kommentare;
- Anhänge/Bilder;
- Berechtigungen;
- Offline-Nachlieferung;
- Konfliktregeln;
- später SKV/GPS.

## S5 – Vermessungs-/Streckendaten NAS
- Originale dauerhaft unverändert;
- App lädt nur benötigte Abschnitte;
- nur bearbeitete/berechnete Daten zurück;
- andere Geräte laden gezielt benötigte Gleise/Abschnitte;
- Bearbeitungsstand nachvollziehbar;
- langfristige Wiederauffindbarkeit.

## S6 – Admin / Windows / Laptop
- Benutzer/Firma/Geräte;
- Aktivierungskarten;
- Baustellen-/Speicherstatus;
- BTB/Mängel/Abnahmen anzeigen/ausgeben und später ggf. bearbeiten;
- `Mein Ordner` mit `lesen` bzw. `lesen+bearbeiten`.

## S7 – End-to-End / Betrieb / Restore
- Poco + Pixel + Server;
- Online/Offline/Netzwechsel;
- mehrere TBs/Helfer;
- große Daten-/Fotomengen;
- Backup/Restore praktisch;
- Monitoring/Logs ohne Geheimnisse.

---

# Spätere Komfort-/Erweiterungswünsche

Festgehalten, aber nicht stabilitätsblockierend:
- Spracheingabe;
- Niederschlagsmenge in mm bzw. l/m²;
- vereinfachte Mangelaufnahme;
- SKV-Abnahme inkl. GPS;
- `Mein Ordner`;
- Windows/Laptop-Bearbeitung;
- iOS erst auf stabilem Android-/Server-Fachmodell.

# Priorität ab jetzt

1. **A0 Android 200050 lokal bauen und Poco/Pixel testen.**
2. **A1 Einstellungen reparieren und neu strukturieren.**
3. **A2 Foto-Aufdruck/Fotos fertigstellen.**
4. **A3 BTB-Zwei-Geräte-/Mehrhelfer-Verhalten absichern.**
5. **A4 Baustellen-/Unterbaustellenstruktur.**
6. Danach A5–A9.
7. Erst anschließend Serverphase S0–S7.

Neue Wünsche werden künftig einer Roadmap-Phase zugeordnet und versioniert, statt nur im Chat stehen zu bleiben.
