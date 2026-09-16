# TB-Helfer – aktueller Projektstand

Stand: 2026-09-16

Diese Datei ist die verbindliche technische Übergabe zwischen Chats/Sitzungen. Fachliche Grundsatzentscheidungen stehen in `docs/TB-HELFER_PROJEKTREGELN.md`, die Arbeitsweise in `docs/TB-HELFER_PROJEKTANWEISUNG.md`, die geplante Reihenfolge in `docs/TB-HELFER_ROADMAP.md`.

## Start jedes Chats / jeder Änderung

Zuerst prüfen/lesen:
1. `docs/TB-HELFER_PROJEKTANWEISUNG.md`
2. `docs/TB-HELFER_PROJEKTREGELN.md`
3. diese Datei
4. aktiven Feature-Branch
5. offenen Draft-PR
6. neuesten relevanten Commit
7. Chat-Übergabe nur ergänzend

GitHub-Code und aktiver Branch sind Source of Truth. `main` stabil halten.

## Repository / Branch / PR

- Repository: `jensmeier/TB-Helfer-2.0`
- stabil: `main`
- aktiver Feature-Branch: `feature/admin-offline-sync`
- Draft-PR: `#5 – Admin + Offline-Sync Grundlage`, offen/Draft/mergeable
- `main`: `fffc7c1d32e636c7d851bbadd9ce3b11789c7b25`
- Branch-Head vor dieser Dokumentationsaktualisierung: `e0eb27409d506e5b7eaa5f5709024a86d28ba1cb`
- aktueller Android-Buildkandidat im Gradle-Stand: **200050**
- Produktivserver/NAS unverändert: API **0.7.0**, PostgreSQL **Schema 10**

## Entwicklungsstrategie

**Android zuerst stabil und vollständig machen, danach produktiven Server/NAS ausbauen.**

Keine produktive Servermigration für einen Android-Zwischenstand. Server/NAS-Migrationen nur nach frischem verifiziertem Backup.

---

# Aktueller Android-Stand – BTB-Lebenszyklus

## Letzter lokaler Build

Der Benutzer hat am 16.09.2026 um ca. 20:23 einen 200050-Zwischenstand in Android Studio erfolgreich gebaut:
- Build erfolgreich;
- nur 4 Warnungen;
- keine Compilerfehler.

**Wichtig:** Danach wurden weitere Branch-Änderungen für die feste Buttonzeile sowie BTB-Abschluss-/Lösch-Lebenszyklus eingespielt. Der aktuelle Branch-Head `e0eb274...` ist deshalb noch nicht lokal kompiliert/auf Poco und Pixel bestätigt.

GitHub Actions bleibt kein verlässlicher Compilernachweis: der aktuelle Workflow-Lauf scheitert weiterhin in der bekannten Infrastrukturform, bevor ein aussagekräftiger Android-Build entsteht. Maßgeblicher Test bleibt Android Studio auf dem Windows-PC.

## BTB-Teammodell

Verbindlich:
- BTB ist **nicht** baustellenweit gemeinsam;
- jeder verantwortliche TB besitzt mit seinen Helfern einen eigenen gemeinsamen BTB-Bereich;
- Helfer desselben TB-Teams bearbeiten exakt denselben BTB / dieselbe stabile `btbId`;
- ein anderer verantwortlicher TB besitzt seinen eigenen BTB / seine eigene `btbId`;
- Datum, Zeit und Tag/Nacht sind Attribute, nicht Identität;
- mehrere BTBs am selben Tag und über Mitternacht sind erlaubt;
- ein real angelegter noch nicht abgeschlossener BTB heißt im Benutzer-UI **`BTB aktiv`**, nicht `Entwurf`.

Neue fachliche Klarstellung vom 16.09.2026:
- **Baustellenstruktur, Unterbaustellen, benötigte Streckendaten, Abnahmen, Mängel, Bilderliste gesamt und Mängelliste gesamt sind Baustellendaten und sollen innerhalb der Baustellenmitgliedschaft gemeinsam synchronisiert werden.**
- Die frühere Formulierung, diese normalen Baustellendaten unnötig auf einen einzelnen Helfer-Bereich zu beschränken, gilt für dieses Zielmodell nicht mehr.
- Die eigentliche Teamtrennung bleibt beim **BTB**: verantwortlicher TB + seine Helfer gemeinsam; andere TB-Teams getrennt.
- Berechtigungen bleiben weiterhin für Mitgliedschaft, Rollen und besonders schützenswerte Bearbeitungsaktionen relevant, sollen aber nicht verhindern, dass normale gemeinsame Baustellendaten auf den Baustellengeräten verfügbar sind.

Diese Klarstellung muss bei der nächsten Konsolidierung auch in `TB-HELFER_PROJEKTREGELN.md` eingearbeitet werden; bis dahin ist sie hier als neuere Benutzerentscheidung dokumentiert.

---

# BTB Speichern / Auto-Sync

Bereits vorhanden:
- lokal immer zuerst sicher speichern;
- danach vorhandenen `SofortSync` anstoßen;
- bestehende WorkManager-/`SyncWorker`-/Collaboration-Queue wiederverwenden;
- kein zweiter Netzwerkpfad;
- ohne Internet bleiben lokale Daten erhalten und werden später übertragen;
- Import vom Server erzeugt keinen simplen Import→Upload-Kreis;
- komplette BTB-Fachdaten einschließlich Punkte 4–8 laufen im selben Snapshot.

Praktisch noch mit zwei Geräten zu bestätigen:
- Poco → Pixel ohne manuellen Sync;
- Pixel → Poco;
- Offline speichern → Netz wieder da;
- mehrere schnelle Änderungen;
- mehrere Helfer / mehrere verantwortliche TB-Teams ohne Vermischung.

---

# BTB Abschluss / Nachbearbeitung

Aktueller Branch besitzt jetzt eine ausdrückliche Lebenszyklussteuerung:

- erfolgreicher `BTB Abschluss` schreibt den fertigen Zustand derselben `btbId`;
- ein abgeschlossener BTB wird aus dem aktiven Kontext gelöst;
- der fertige BTB bleibt vollständig im Verlauf;
- ein auf einem anderen Teamgerät empfangener fertiger BTB darf dort nicht durch einen stale `.active_btb`-Pointer weiter als aktiv erscheinen;
- `BtbActiveContext` erkennt `istFertig || dokumentVersion > 0`, markiert den BTB nach Abschluss inaktiv und löst den aktiven Pointer;
- Abschlusszustand stößt `SofortSync` an, weil der Abschluss nicht in jedem Pfad noch einmal durch den normalen `BautagebuchSpeicher.speichern()`-Hook läuft;
- nur die bewusste Aktion `BTB-Verlauf → BTB bearbeiten` darf exakt dieselbe abgeschlossene `btbId` wieder aktivieren;
- dafür gibt es getrennte Marker für `nach Abschluss inaktiv` und `bewusst reaktiviert`;
- wird danach wirklich geändert, gilt der bestehende Status `Nachbearbeitung` und das aktuelle Abschluss-PDF wird gemäß bestehender Original-/Rotlogik ungültig;
- beim nächsten erfolgreichen Abschluss wird dieselbe `btbId` wieder inaktiv.

Relevante Dateien:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActiveContext.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActivePointerControl.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BtbVerlaufFragment.kt`

Letzte relevante Commits:
- `77addce9...` – remote abgeschlossenen BTB aus aktivem Kontext lösen
- `ad5703fa...` – bewusstes Wiederöffnen markieren
- `c6c239fa...` – Abschluss beim Lösen sofort synchronisieren
- `e0eb2740...` – Source-Check für Abschluss/Wiederöffnen

---

# BTB Löschen – Team-Tombstone statt Wiederauferstehen

Das vom Benutzer beobachtete Problem war:
- Poco löscht lokal einen BTB;
- Pixel bzw. Server besitzt dieselbe `btbId` noch;
- nächster Sync schreibt den scheinbar gelöschten BTB wieder zurück.

Der aktuelle Branch besitzt dafür jetzt `BtbDeleteSyncCoordinator`.

Verbindlicher Ablauf bei bewusstem `BTB löschen`:
1. stabile `btbId` und Server-Baustelle/TB-Gruppe bestimmen;
2. Löschvorhaben **vor** dem lokalen Löschen persistent merken;
3. wenn Server-Entity bereits bekannt ist, vorhandene Collaboration-Queue mit `OP_DELETE` verwenden;
4. BTB lokal sofort löschen;
5. `SofortSync` anstoßen;
6. offline bleibt das Löschvorhaben bestehen und wird nach Netzrückkehr übertragen;
7. Server-Tombstone derselben `btbId` wird auf anderen Geräten desselben BTB-Teams lokal angewendet;
8. ein DELETE-Konflikt nach zwischenzeitlicher Änderung eines anderen Teamgeräts wird gegen die aktuelle Serverversion neu eingereiht;
9. echte Baustellenfoto-Dateien bleiben bestehen; nur die Zuordnung zur gelöschten `btbId` wird gelöst;
10. nach dem Löschen wird **kein anderer historischer BTB automatisch aktiviert**.

Wichtige Dateien:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDeleteSyncCoordinator.kt`
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDeleteService.kt`
- `app/src/main/java/de/tbhelfer/server/SyncWorker.kt`
- `app/src/main/java/de/tbhelfer/datenbank/SyncDao.kt`
- `app/src/main/java/de/tbhelfer/ui/baustelle/BtbVerlaufFragment.kt`

Der Worker behandelt DELETE/Tombstones **vor** dem normalen Server-BTB-Import. Neu eingereihte oder gegen eine aktuelle Serverversion rebased Deletes können im selben Worker-Lauf erneut synchronisiert werden. Dadurch soll ein gelöschter BTB nicht mehr zwischen Poco/Pixel wieder auferstehen.

Static Contract:
- `server/checks/15_verify_android_btb_delete_lifecycle_source.py`
- in `.github/workflows/android.yml` eingebunden.

Noch praktisch zu bestätigen:
- online Poco löscht → Pixel löscht denselben BTB;
- online Pixel löscht → Poco löscht denselben BTB;
- danach App-Neustart + erneuter Sync: BTB bleibt weg;
- offline löschen → lokal sofort weg → Netz wieder an → anderes Gerät löscht später;
- Löschen nach paralleler Änderung auf dem anderen Teamgerät;
- echte Fotos bleiben in der Baustelle erhalten.

---

# BTB Punkte 4–8 / Bilder / Buttons

Punkte 4–8 verwenden denselben direkten Bilderpfad mit dem jeweiligen Bildtyp:
- 4 Ausgeführte Arbeiten → `FOTODOKUMENTATION`
- 5 Abnahmen → `ABNAHMEN`
- 6 Einweisung/Unterweisung → `EINWEISUNG_UNTERWEISUNG`
- 7 Besonderheiten → `BESONDERHEITEN`
- 8 Mängel/Schäden → `MAENGEL_SCHAEDEN`

Modern synchronisierte BTB-Fotos werden strikt derselben `btbId` zugeordnet; Zeitfenster ist nur noch Legacy-Fallback für echte Altbestände ohne `btbId`.

## Neue feste Aktionszeile

Der vorherige Versuch, Androids interne AlertDialog-Buttonbar zu zwingen, war auf dem Poco weiterhin abgeschnitten (`Bil… / Abbre… / Hinzu…`).

Aktueller Branch ersetzt die sichtbare Systemleiste durch eine eigene Zeile direkt im Dialoginhalt:
- `Bilder | Abbrechen | Hinzufügen`
- drei `MaterialButton`s;
- je exakt 1/3 Breite;
- 48 dp Höhe;
- einzeilig / zentriert;
- gleiche Darstellung in Punkten 4–8;
- `Hinzufügen` delegiert weiter an die bereits vorhandene positive Dialog-Validierungs-/Commitlogik;
- alte AlertDialog-Systembuttonzeile bleibt nur unsichtbarer technischer Träger und wird optisch ausgeblendet.

Relevante Datei:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt`

Commit:
- `07c7fcd0...` – stabile eigene BTB-Aktionszeile.

**Noch nicht auf Gerät bestätigt**, weil dieser Commit nach dem letzten erfolgreichen lokalen Build kam.

---

# Baustelle / Unterbaustellen / Streckendaten

Noch offen:
- Poco besitzt z. B. `5362-1`, `Durchlass` und weitere Unterbaustellen;
- Pixel/Helfer besitzt teilweise nur die Hauptbaustelle;
- `WorksiteBindingRepository` kann aktuell eine Server-Hauptbaustelle lokal anlegen/binden, synchronisiert aber noch nicht den vollständigen `BaustellenKnoten`-Baum.

Ziel:
- gemeinsame Baustelle → Unterbaustellen → Strecke/Gleis → Objekte über stabile IDs verteilen;
- Helfer muss gemeinsame Unterbaustellen nicht manuell neu anlegen;
- gemeinsame Baustellendaten stehen den Baustellenmitgliedern zur Verfügung;
- Vermessungs-/Streckendateien weiterhin nur bedarfsgerecht auf Geräte übertragen; Originale auf NAS unverändert.

Keine produktive Serveränderung wurde dafür in diesem Android-Schritt vorgenommen.

---

# Kamera / KM / Foto

Praktisch bestätigt aus 200049/200050-Grundlage:
- Live-KM funktioniert;
- Live-KM größer/fett;
- Foto-Erfassen zeigt passenden KM/GPS/Strecke-Gleis-Kontext;
- Qualitätsampel: `<=10 m grün`, `>10–30 m gelb`, `>30 m rot`;
- GPS-Ungenauigkeit darf nur verschlechtern, nie verbessern;
- automatischer KM bleibt im gelben Bereich erhalten.

Nicht erneut verändern, solange kein reproduzierbarer Fehler vorliegt:
- KF/GVP/Festpunkt-Logik;
- DB_REF/GK-Streifen 3/4/5;
- vorhandene KM-Projektion.

Offen:
- Foto-Aufdruck/Overlay-Einstellungen bleiben nach `Zurück` teilweise nicht gespeichert;
- deshalb tatsächlichen eingebrannten JPG-Aufdruck noch nicht abschließend bestätigt.

---

# Firmenlogo / PDF

Bereits gebaut:
- PDF zeichnet hinter Firmenlogo ausdrücklich weißen Untergrund;
- empfohlenes Serverformat: PNG;
- JPEG/WebP im vorhandenen Android-Downloadpfad ebenfalls möglich;
- SVG derzeit nicht für den Bitmap-PDF-Pfad vorgesehen;
- Schema 10 besitzt bereits Firmenprofil-/Logo-Metadaten.

Produktiver Server bleibt unverändert.

---

# Einstellungen – späterer Android-Block A1

Bestätigter Fehler:
- Foto-Aufdruck-Schalter bleiben nicht zuverlässig persistent.

Geplante Hauptstruktur:
1. Profil & Firma
2. Anzeige
3. Kamera & Fotos
4. BTB & PDF
5. Synchronisation
6. Benachrichtigungen
7. Wartung & Diagnose

Vor dem Redesign jede Einstellung auf Laden, Speichern, Zurück, App-Neustart und tatsächliche Wirkung prüfen. Widget unter Anzeige; `Overlay` benutzerverständlich als `Foto-Aufdruck`.

---

# Produktiver Synology-/Serverstand

Unverändert:
- NAS/Synology = Source of Truth;
- API **0.7.0**;
- PostgreSQL **Schema 10**;
- Schema 11 Nutzerkontakte vorbereitet, nicht produktiv;
- Schema 12 PDF-Logo-Layout vorbereitet, nicht produktiv;
- Secrets nicht in GitHub/Chat/Dokumentation;
- vor produktiver Migration frisches verifiziertes Backup.

---

# Technische Schuld

- Generated-Source-Brücke seit 200043 später sauber zurückbauen;
- GitHub Actions derzeit teilweise ohne echte Build-Steps und daher kein verlässlicher Compilerindikator;
- maßgeblicher Buildtest vorerst Android Studio auf Windows;
- technische Bereinigung erst nach funktionaler Android-Stabilisierung.

---

# Nächster verbindlicher Test

Aktuellen Branch **neu** lokal bauen und auf **Poco und Pixel** installieren, App-Daten nicht löschen.

Test mit genau einem neuen Team-BTB:
1. Poco erstellt BTB → Pixel soll denselben BTB/dieselbe `btbId` als **`BTB aktiv`** erhalten, ohne manuellen Sync.
2. Punkt 4 sowie beispielhaft Punkte 5 und 8 mit Text/Bildern ändern → Gegenstelle prüfen.
3. In Punkten 4–8 muss die neue Zeile `Bilder | Abbrechen | Hinzufügen` gleich groß und vollständig lesbar sein.
4. Pixel ändert denselben BTB → Poco erhält Änderung.
5. Poco führt `BTB Abschluss` aus → Pixel darf diesen BTB nicht weiter aktiv haben; Verlauf zeigt `Abgeschlossen`.
6. Auf Pixel `BTB bearbeiten` → exakt dieselbe `btbId` bewusst reaktivieren; nach echter Änderung Status `Nachbearbeitung`.
7. erneut Abschluss → auf beiden Geräten wieder nicht aktiv.
8. auf einem Gerät `BTB löschen` → auf dem anderen muss exakt derselbe BTB nach Sync verschwinden.
9. beide Apps neu starten und erneut synchronisieren → gelöschter BTB darf **nicht** wieder erscheinen.
10. Wiederholung einmal offline: löschen ohne Netz → lokal sofort weg → Netz an → anderes Gerät muss Löschung nachziehen.
11. kontrollieren, dass echte Foto-Dateien in der Baustelle erhalten bleiben.

Erst wenn dieser Lebenszyklus praktisch sauber ist, den nächsten großen Android-Block beginnen. Danach vorrangig Baustellen-/Unterbaustellen-Verteilung und anschließend Einstellungen/Fotos gemäß Roadmap.
