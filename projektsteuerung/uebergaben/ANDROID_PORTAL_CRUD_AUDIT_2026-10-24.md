# AND-CRUD-AUDIT-024 – Android und Portal im Vergleich

Stand: 24.10.2026. Auftrag: gezeigte Portalaktionen gegen Android prüfen, einschließlich Löschen, nachträglicher Korrektur und externer Bilder (z. B. WhatsApp). Reine Quellprüfung; keine funktionalen Änderungen beauftragt oder vorgenommen.

## Geprüfte Stände

- Android: `78b9d2c54eb6f198e20383b7491297cc3041c4f3`, Branch `feature/tb-android-2.0`, PR11 offen; Gradle 1.3.30/200080.
- Server: `25b40892affe3c9ea637c53029d3c508ded2d891`, Branch `feature/tb-server-1.0`, Draft-PR6.
- Einstieg und gemeinsamer Leseweg ab `projektsteuerung/00_START_HIER.md` gelesen. Android-/Serververtrag und aktuelle Server-Auftragsdokumentation zusätzlich verglichen.
- Installierte Handyversion und tatsächlich eingesetzter NAS-Commit hier nicht nachgewiesen.

## Ergebnis

| Funktion | Android-Quellstand | Server-/Portalabgleich |
|---|---|---|
| Eintragstext, Zeit/Spanne und KM korrigieren | Vorhanden: Eintrag antippen; Editor mit Speichern | Server PATCH /btbs/{id}/entries/{entryId}, base_version und Konfliktprüfung; gleiche Eintragsidentität |
| Unterbaustelle ändern | Vorhanden: Ortsauswahl im Eintragseditor; stabiler clientNodeKey | Server prüft Node-Zuordnung; PATCH erhält Eintrags-ID |
| Firma/Allgemein ändern | Vorhanden: Eintrag lange drücken, Firma / Allgemein ändern | Payload-/Firmenzuordnung wird im bestehenden versionierten Eintrag übertragen |
| Zwischen Punkt4–8 verschieben | Vorhanden im Langdruckmenü; clientEntryKey bleibt erhalten | Server erlaubt Sectionwechsel innerhalb BTB_CONTENT_SECTIONS; Foto-Kontext wird nachgeführt |
| Einzelnen BTB-Eintrag löschen | Vorhanden, Löschaktion im Editor/Langdruckmenü | Android sendet DELETE nach Basisvergleich; Server soft-deleted Eintrag, löst feste Fotobindung und emittiert BTB-/FILE-Änderungen |
| Ganzen BTB löschen | Vorhanden: BTB-Verlauf mit Bestätigung und persistentem Löschintent | OP_DELETE/kanonischer Server-BTB-Löschpfad vorhanden; keine pauschale App-Datenlöschung |
| Kamera-/Galeriefoto zu einem Eintrag hinzufügen | Vorhanden: Bild hinzufügen, Kamera / Galerie & Dateien | Import mit stabiler btbId/clientEntryKey; Serverbestätigte Entry-ID für btbEntryId beim Upload; bestehender Medienpfad |
| Bereits heruntergeladenes WhatsApp-Bild auswählen | Über Galerie & Dateien möglich, wenn der Android-Dateianbieter das Bild bereitstellt | Regulärer Bildimport, keine WhatsApp-Sonder-API erforderlich |
| WhatsApp → Teilen → TB-Helfer | FEHLT: Manifest besitzt keine SEND/SEND_MULTIPLE-Registrierung, HauptActivity keinen passenden Empfangspfad | Android-Lücke, kein Serverumbau nötig |
| Foto aus aktuellem BTB/PDF ausblenden | Lokal vorhanden, Auswahl-/Excluded-Speicher | Nicht mit serverseitigem Entfernen einer Fotoverknüpfung gleichsetzen |
| Foto vollständig löschen | Lokale UI und App-Datei-/Room-Löschung vorhanden, Originalschutz im Auswahl-Dialog | LÜCKE: im geprüften modernen Android-Medienpfad kein DELETE auf /btbs/{id}/photos/{fileId} und kein dauerhafter entsprechender Unlink-Intent |
| Foto ausschließlich vom einzelnen Eintrag lösen | Portal hat ausdrückliche Aktion; Android zeigt Eintragsfotos und kennt Metadaten-Bindung | Kein gleichwertiger ausdrücklicher Entfernen-Button an der Eintrags-Thumbnailleiste gefunden; nicht mit BTB/PDF-Ausblenden verwechseln |

## Bedienung in Android

1. BTB und gewünschten Abschnitt öffnen.
2. Manuellen Eintrag antippen: Text, Uhrzeit/Spanne, KM und Unterbaustelle bearbeiten.
3. Langes Drücken: Firma/Allgemein, Punkt4–8 oder Löschen.
4. Bei Änderungen/Löschen in der geöffneten Abschnittsliste abschließend auch deren Speichern verwenden: die einfachen Löschcallbacks bearbeiten zunächst den Editorinhalt, die Abschnitt-Speichern-Aktion ruft onSave und saveAndReloadWholeBtbSilently auf.
5. Bilder am Eintrag über Bild hinzufügen → Galerie & Dateien oder Kamera. Vorher geänderte Eingabefelder müssen erst gespeichert und der Eintrag erneut geöffnet werden; dafür gibt es aktuell einen ausdrücklichen Hinweis. Anders als das Portal kein kombinierter Upload ungespeicherter Editoränderungen.
6. WhatsApp-Bild vorher herunterladen/speichern und über den Android-Dateidialog auswählen. Direktes Teilen an TB-Helfer ist nicht implementiert.

## Befunde und Belegstellen

Android:
- `app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchFragment.kt`: buildManualLine mit bestehendem clientEntryKey; confirmDeleteManualItem; moveManualItem/ToSection; manueller Editor, btnEntryPhotos und launchPhotoForManualItem; Abschnitt-Speichern.
- `BtbPdfAuswahlFragment.kt`: OpenMultipleDocuments/image/*, importExternalPhotos, removePhotoFromCurrentBtb, confirmPhotoAction, deletePhotoPermanently; Ausblenden speichert Auswahl/Excluded, kein Server-Unlink.
- `BtbExternalPhotoImporter.kt`: eigene App-Kopie, EXIF soweit vorhanden, btbId/clientEntryKey-Bindung und gezielter SofortSync. Fehlende GPS-/Aufnahmezeit werden nicht als verlässliche Aufnahmeinformation behauptet.
- `app/src/main/java/de/tbhelfer/server/Server1BtbApi.kt`: versioniertes Entry-PATCH; Entry-DELETE mit Vergleich von Basis und aktuellem Serverstand; Foto-Metadaten-PATCH vorhanden, Foto-DELETE hier nicht implementiert.
- `app/src/main/java/de/tbhelfer/datenbank/BtbTeamMediaSync.kt`: Entry-ID-Auflösung, Bild-Upload, Foto-Link-Metadaten-PATCH und Download; kein serverseitiger Foto-Unlink aus lokaler Löschung.
- `FotoRepository.kt`: lokale Zeilen-/Dateilöschung, Metadatenbereinigung und Sync-Trigger; kein dauerhafter serverseitiger Foto-Lösch-/Unlinkauftrag. Ein Sync-Trigger allein ist kein übertragener Löschauftrag.
- `BtbDeleteSyncCoordinator.kt` und `BtbVerlaufFragment.kt`: separater ganzer-BTB-Löschpfad mit Bestätigung/persistentem Intent.
- `app/src/main/AndroidManifest.xml` / `HauptActivity.kt`: keine Registrierung/Verarbeitung von externem SEND/SEND_MULTIPLE.
- `app/build.gradle.kts`: produktive BTB-Quellgenerierung/Foto-Action-Binder zusätzlich gelesen; nicht allein auf ungepatchte Quellansicht verlassen.

Server:
- `server/templates/v1_btb.html`: Bearbeiten/Löschen/Fotos hinzufügen; Uploads am Eintrag; unbindEntryPhoto (PATCH btbEntryId:null); Foto aus BTB entfernen (DELETE photos).
- `server/tbserver/btb.py`: PATCH/DELETE entries, versionierte Änderungen, Rechte-/Teilnehmerprüfung, prepare_btb_content_change, sync_entry_photos, PATCH/DELETE photos.
- `server/tbserver/files.py`: Uploadvertrag mit btb_entry_id/Foto-Metadaten.

## Bedeutung der Lücken

Android kann vorhandene Texte/Fachzuordnungen bereits ändern und Einträge löschen. Eine Zusage „alle Löschaktionen sind vollständig mit dem Server synchronisiert“ wäre aber falsch: die lokale Fotolöschung besitzt im geprüften Pfad keinen dauerhaften Server-Unlink. Ein bereits hochgeladenes Foto kann deshalb auf dem Server weiterhin vorhanden/zugeordnet sein; tatsächliches Wiedererscheinen auf einem Gerät ist hier nicht reproduziert.

Empfohlene Folgearbeit, noch NICHT umgesetzt:
1. Dauerhaften, idempotenten Foto-Unlink in bestehende Offline-Outbox integrieren: erst Server-ID/BTB-Ziel und Rechte klären, Link entfernen, Originale/historische Referenzen schützen, bei Offline/Fehler Wiederholung erhalten. Entfernen aus Eintrag, aus BTB und lokale Datei-Löschung getrennt benennen.
2. Optionalen externen Bildempfang per SEND/SEND_MULTIPLE ergänzen: bewusste Baustellen-/BTB-/Eintragsauswahl, sichere URI-Rechte und dauerhafte App-Kopie, kein stilles Raten des Ziels.
3. Vorhandene Aktionen im Android-Editor besser sichtbar machen, falls die aktuelle Tipp-/Langdruckbedienung zu versteckt ist.

## Prüfstatus

- Zehn gezielte Quell-/Verkabelungsfragen anhand der gepinnten Dateien geprüft; Befunde bestätigt.
- Kein neuer Gradle-/Unit-/Lint-Lauf: keine Runtimeänderung. Vorherige98 Tests gelten nicht als neuer funktionaler Nachweis dieser Abläufe.
- Kein adb verfügbar, daher kein Emulator-/WhatsApp-/Pixel-/Poco-/NAS-E2E durchgeführt.
- Praktisch offen: Portal→Android ändern und umgekehrt; Eintrag löschen und Fotoablösung kontrollieren; Kamera/Galerie/WhatsApp-Datei bei Offline/Neustart importieren; bestehendes Serverfoto lokal löschen und tatsächliche Serververknüpfung kontrollieren; paralleler Konflikt, Helferrechte und Abschluss-/Originalschutz.
- Keine APK/Versionsänderung und keine NAS-Übernahme oder Deployment.
