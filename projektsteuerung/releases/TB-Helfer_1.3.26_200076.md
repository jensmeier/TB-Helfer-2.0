TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.26
BUILD: 200076
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Foto-Sync: Handy-Volloriginal bleibt auf dem Aufnahmegerät; zum Server wird eine separate JPEG-Syncversion mit maximal 1920 px langer Kante und Qualität 82 übertragen.
- Dieselbe kleine Syncbild-Regel gilt für BTB-Fotos, Mängelfotos, Abnahmefotos und bildbasierte Abnahme-Anlagen; PDF-/Nichtbild-Anlagen bleiben unverändert.
- Server→Android nutzt für Bilder die vorhandene mobile preview-Rendition statt des automatischen Vollinhalts.
- BTB-Foto-Metadatenänderungen werden über den versionierten Server-PATCH am bestehenden Foto-Link synchronisiert.
- Wetter zeigt die numerische Niederschlagsmenge in mm an.

VERBESSERT:
- Bereits bekannte Serverfotos werden beim Wechsel auf kleine Syncdateien nicht als neue Bilder dupliziert.
- Foto-PATCH arbeitet gegen link_version und meldet Parallelkonflikte statt still zu überschreiben.
- Stabile worksiteNodeKey-/Firma-/Punkt-Zuordnung wird bei neuen BTB-Fotouploads strukturiert mitgeführt.
- Mobile Serverbilder werden in einem eigenen Preview-Cache gespeichert; alte Vollbild-Caches werden bei Bedarf durch kleine Previews ersetzt.
- BTB-PDF und Druckmodell führen die Niederschlagsmenge zusammen mit der Niederschlagsart.

BEHOBEN:
- Automatisch bekannter Niederschlag 0,0 mm wurde bisher durch eine >0-Prüfung zu null/„unbekannt“ gemacht.
- Android lud BTB-Fotos bisher aus dem lokalen Volloriginalpfad zum Server hoch.
- Android holte Serverbilder bisher automatisch über /content statt über die vorhandene preview-Rendition.
- Lokale BTB-Foto-Kontextkorrekturen hatten noch keinen Android-Clientpfad zum Server-Foto-PATCH.

UNVERÄNDERT:
- Das lokale Volloriginal eines Handyfotos wird nicht verändert oder durch die Syncdatei ersetzt.
- Foto-PATCH verändert weder Originaldatei noch Aufnahmezeit noch GPS.
- BTB-FINAL-001 Lifecycle-Code wurde in diesem Build nicht umgebaut; Android und Portal verwenden weiterhin denselben kanonischen Server-Finalize-Endpunkt.
- BESTÄTIGTE BTB-SYNC-002-, BTB-UI-001- und PORTAL-LIVE-001-Pfade bleiben geschützt.
- Keine server/**-Datei in diesem Android-Build geändert.

SERVERVERTRAG:
- Server-Branch feature/tb-server-1.0.
- Bildrenditions serverseitig: preview 1280 px / JPEG82, thumb 320 px / JPEG72.
- BTB-Foto-PATCH: PATCH /api/v1/btbs/{btb_id}/photos/{file_id} mit base_version/link_version.
- FINALIZED bleibt kanonischer Serverzustand und wird über Change-Feed/SSE/Delta verteilt.
- Wetterfelder tempC und niederschlagMm bleiben gemeinsame Header-/Slot-Felder; 0.0 mm ist ein bekannter Wert.

TESTSTATUS:
- QUELLSTAND IMPLEMENTIERT
- SOURCEGENERATOR-ANKER: geprüft, 14/14 passend
- BUILD OFFEN – aktueller Code-HEAD 17b42917c0439d6fc9540c11fe4a2a9ec2f57914 hat keinen GitHub-Actions-Run; lokaler Gradle-Checkout ist in diesem Chat nicht verfügbar
- INSTALLATION OFFEN
- FOTO-SYNC-/FOTO-PATCH-E2E OFFEN
- WETTER-0-MM-RUNDTRIP OFFEN
- BTB-FINALIZE-E2E BEIDE RICHTUNGEN OFFEN
- POCO/HELPER-E2E OFFEN

NÄCHSTER TEST:
- 1.3.26 / 200076 bauen.
- Pixel installieren.
- neues Handyfoto aufnehmen: lokales Original unverändert lassen; Serverdatei muss deutlich kleiner und höchstens 1920 px an der langen Kante sein.
- dasselbe Bild auf Poco/anderem Handy synchronisieren: mobile Datei muss die 1280-px-preview-Rendition sein.
- bestehendes Foto Punkt/Firma/Unterbaustelle ändern und Portal-Sync prüfen; Foto-ID, Aufnahmezeit und GPS müssen gleich bleiben.
- automatisches Wetter mit 0,0 mm prüfen: Android, Portal und PDF müssen den bekannten 0-mm-Wert behalten.
- Android-BTB abschließen: Portal + zweites Handy müssen automatisch FINALIZED/read-only übernehmen und Abschluss-PDF sehen.
- separaten Test-BTB im Portal abschließen: Android + zweites Handy müssen denselben FINALIZED-Zustand automatisch übernehmen.

BUILD-FIX:
- Android-Studio-Compilefehler `Unresolved reference 'Locale'` in der neuen Wetter-mm-Formatierung behoben.
- Fehlenden `java.util.Locale`-Import ergänzt; Fachlogik unverändert.
- Build 200076 muss danach erneut real ausgeführt werden.

ANZEIGE:
- Startbildschirm zeigt die installierte Version dynamisch als **TB-Helfer 1.3.26 / Build 200076**.
- Wartung/Update verwendet dasselbe BuildConfig-basierte Format.
