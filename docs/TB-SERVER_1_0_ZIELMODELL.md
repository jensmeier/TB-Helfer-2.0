# TB-Server 1.0 – Zielmodell

Stand: 2026-09-17

Dieses Dokument definiert den Zielzustand für den nächsten zusammenhängenden Server-Neuaufbau von TB-Helfer 2.0. Es ergänzt die verbindlichen Projektregeln. Ziel ist ein Server, der spätere Erweiterungen ohne erneuten Grundumbau ermöglicht und Android, TB-Helfer Portal und spätere Clients gegen denselben stabilen Fachvertrag arbeiten lässt.

## 1. Grundentscheidung

Ab jetzt wird die Serverbasis zuerst vollständig und modular aufgebaut. Android 200050 bleibt vorerst Testclient und wird nach Stabilisierung des Serververtrags systematisch angebunden.

Der bestehende Serverbestand ist Testbestand. Benutzer, Baustellen und Testdaten dürfen beim Umstieg auf TB-Server 1.0 verworfen werden. Trotzdem gilt vor jeder produktiven NAS-/PostgreSQL-Änderung weiterhin: vollständiges Backup erstellen und verifizieren, damit ein technischer Rückfall möglich bleibt.

Es wird keine aufwendige Migration von Testfachdaten erzwungen. Bevorzugt wird ein sauberer Neuaufbau des fachlichen Schemas mit anschließender Neuanlage/Aktivierung der Testbenutzer.

## 2. Architekturprinzip

TB-Server 1.0 wird als modularer Monolith betrieben, nicht als Sammlung kleiner Microservices.

Zielaufbau:

- PostgreSQL/PostGIS als zentrale relationale Datenbank;
- TB-Helfer API als eine versionierte Anwendung mit getrennten Fachmodulen/Routern;
- DB-gestützte Hintergrundjobs für asynchrone Arbeiten;
- NAS-Dateispeicher für Bilder, PDFs, DOCX, Vermessungsoriginale, Releases und Archive;
- TB-Helfer Administration für Systemverwaltung;
- TB-Helfer Portal für Chef/TB/Helfer;
- Cloudflare Tunnel für normalen App-/Portal-Zugriff;
- Tailscale ausschließlich für Wartung und Administration.

Keine Fachlogik darf nur in Android existieren, wenn dieselbe Aktion auch im Portal möglich sein soll. Serverregeln für Rechte, Abschluss, Löschung, Versionierung und Konflikte sind kanonisch.

## 3. Erweiterbarkeit / Updates

### 3.1 Stabile API

- öffentliche Client-API bleibt unter `/api/v1/...`;
- additive Erweiterungen innerhalb v1 bevorzugen;
- inkompatible Änderungen nur mit neuer API-Hauptversion;
- jeder Client sendet Plattform, App-Version und Buildnummer;
- Server liefert `minimum_supported_build`, `latest_build` und Kompatibilitätsstatus.

### 3.2 Capabilities statt Schema-Abfragen im Client

Android/Portal fragen einen Capability-Endpunkt ab, z. B.:

- `worksite_tree_v1=true`
- `worksite_permissions_v1=true`
- `btb_live_sync_v1=true`
- `track_data_v1=true`
- `portal_btb_edit_v1=true`
- `company_logo_v1=true`

Clients fragen nicht nach internen PostgreSQL-Schemanummern.

### 3.3 Datenbankmigrationen

- nummerierte, unveränderliche Migrationen;
- bereits ausgeführte Migrationen werden nie nachträglich verändert;
- `schema_migrations` protokolliert Version, Zeitpunkt und Prüfsumme;
- neue Funktionen erhalten neue Migrationen;
- vor produktiver Migration automatischer Preflight;
- nach Migration automatischer Verify-Check;
- für Test/Entwicklung gibt es einen expliziten `reset-test-data`-/Neuinitialisierungsweg;
- produktive Datenbank wird niemals stillschweigend zurückgesetzt.

### 3.4 Modulgrenzen

Servermodule werden fachlich getrennt:

1. `auth`
2. `companies`
3. `users`
4. `devices`
5. `worksites`
6. `permissions`
7. `teams`
8. `btb`
9. `photos_files`
10. `defects`
11. `acceptances`
12. `track_data`
13. `survey_originals`
14. `sync_events`
15. `releases`
16. `portal`
17. `admin`
18. `audit`
19. `system_status`
20. `backup_restore`

Damit kann später ein Modul erweitert werden, ohne den gesamten Server umzubauen.

## 4. Firmen, Benutzer und Rollen

### 4.1 Firma

Jeder normale Benutzer gehört genau einer Firma an. Firmen besitzen stabile UUIDs; sichtbare Firmennamen sind keine Schlüssel.

Zentrale Firmenstammdaten:

- Firmenname;
- Anschrift;
- Firmen-Telefon;
- Firmen-E-Mail;
- Firmenlogo;
- PDF-/Logo-Layout;
- Status;
- optionale Speicherwarnschwellen.

Das Firmenlogo wird nur serverseitig gepflegt. Android/Portal laden und cachen die aktuelle Version. Für PDFs wird bevorzugt PNG verwendet; der PDF-Renderer legt Weiß hinter transparente Logos.

### 4.2 Benutzer

Jeder Benutzer besitzt eine stabile UUID. Gleiche Namen innerhalb derselben Firma sind erlaubt.

Felder u. a.:

- Anzeigename/Vorname/Nachname;
- Firma;
- persönliche E-Mail;
- persönliche Telefonnummer;
- Status;
- Zugriffs-Gültigkeit;
- Firmenrolle;
- Portal-Loginstatus.

Persönliche Kontaktangaben dürfen vom Benutzer auf dem Handy und im Portal gepflegt werden und werden serverseitig synchronisiert.

### 4.3 Firmenrollen

Mindestens:

- `ADMIN` – Systemadministration, nicht normale Firmenrolle;
- `CHEF` – Firmenleitung;
- `USER` – normaler Firmenbenutzer.

`CHEF` sieht automatisch alle Baustellen der eigenen Firma. Standardrechte: lesen, PDF/Export, Bilder/Mängel/Abnahmen/BTBs ansehen. Schreib-/Abschlussrechte können firmenweit oder je Baustelle erweitert werden. Mehrere CHEF-Benutzer pro Firma sind erlaubt.

## 5. Geräte und Portal-Anmeldung

### 5.1 Geräte

Gerätebindung bleibt getrennt von Portal-Login.

Gerät speichert/zeigt:

- Benutzer;
- Plattform;
- Gerätename;
- OS-Version;
- App-Version/Build;
- gebunden seit;
- zuletzt gesehen;
- letzter erfolgreicher Sync;
- letzter Syncfehler;
- Status aktiv/freigegeben/gesperrt.

Verlorenes Gerät kann serverseitig gesperrt werden. Neues Gerät kann danach neu aktiviert und aus dem Serverbestand wiederhergestellt werden.

### 5.2 Portal-Anmeldung

Portal verwendet niemals den geheimen Geräte-Key als Benutzerpasswort.

Ziel:

- eindeutige Benutzerkennung und/oder persönliche E-Mail;
- Passwort-Hash ausschließlich serverseitig mit geeignetem Passwort-Hashverfahren;
- erster Zugang über Einmal-Code/Aktivierung;
- sichere Session-Cookies;
- CSRF-Schutz;
- Rate-Limit/Lockout;
- später Passkey/TOTP optional ergänzbar.

Eine Portal-Sitzung zählt nicht als zusätzliches gebundenes Mobilgerät.

## 6. Baustellenmodell

Eine Baustelle gehört genau einer Firma und besitzt eine stabile UUID.

Baustellenweit gemeinsam sind:

- Hauptbaustelle;
- Unterbaustellen;
- Strecke/Gleis/Weiche/Objekte;
- benötigte mobile Streckendaten;
- Mängel;
- Abnahmen;
- baustellenweite Bildliste;
- Mängelliste gesamt;
- Archive/Exporte.

Baustellenstruktur wird als Baum mit stabilen UUIDs gespeichert. Parent/Child-Beziehungen verwenden niemals lokale Room-IDs.

Ein berechtigter verantwortlicher TB erstellt/ändert die gemeinsame Struktur auf dem Handy oder Portal. Andere Geräte erhalten dieselbe Struktur über den Server.

## 7. Baustellenmitgliedschaft und Rechte

Rechte sind serverseitig kanonisch, aber auf dem Handy und im Portal bedienbar.

Der verantwortliche TB kann direkt auf der Baustelle einen Nutzer auswählen und dessen Rechte bearbeiten. Offline darf die Änderung lokal vorgemerkt werden; endgültig wirksam auf anderen Geräten wird sie nach Server-Sync.

### 7.1 Rollen je Baustelle

Mindestens:

- `RESPONSIBLE_TB`
- `TB`
- `HELPER`
- `READ_ONLY`

Ein Benutzer kann auf Baustelle A verantwortlicher TB und auf Baustelle B Helfer sein.

### 7.2 Rechtevorlagen

Vorlagen reduzieren Bedienaufwand:

- Verantwortlicher TB;
- Helfer Standard;
- Helfer erweitert;
- Nur Lesen.

Danach sind individuelle Abweichungen pro Nutzer/Baustelle möglich.

### 7.3 Einzelrechte

Mindestens getrennt steuerbar:

- BTB ansehen;
- BTB bearbeiten;
- BTB erstellen;
- BTB abschließen;
- BTB löschen;
- Bilder hinzufügen;
- Abnahme erstellen/bearbeiten/abschließen;
- Mangel erstellen/bearbeiten/abschließen;
- Baustellenstruktur ändern;
- Streckendaten bearbeiten;
- Benutzer/Rechte der Baustelle verwalten;
- Export/PDF/Archiv erzeugen.

Kritische Serveraktionen prüfen das Recht beim Server erneut. Ein offline entzogener Nutzer kann auf seinem isolierten Gerät kurzfristig weiterarbeiten; beim nächsten Serverkontakt wird die neue Berechtigung durchgesetzt.

### 7.4 Vertretung

Eine Baustelle darf nie technisch an einem einzelnen Handy hängen. CHEF oder ausreichend berechtigter TB kann die Verantwortung auf einen anderen Benutzer übertragen. Historische Datensätze behalten die ursprüngliche Autoren-/Verantwortlichen-ID.

## 8. TB-Team und BTB

BTB ist nicht baustellenweit gemeinsam.

Je verantwortlichem TB-Team existiert ein eigener BTB-Bereich. Ein verantwortlicher TB und seine Helfer teilen denselben aktiven BTB. Andere verantwortliche TB-Teams derselben Baustelle führen getrennte BTBs.

Server hält pro Team explizit:

- `active_btb_id`;
- Teammitglieder;
- Rollen/Rechte;
- BTB-Historie.

Datum/Uhrzeit/Tag/Nacht sind Attribute, niemals Identität.

### 8.1 BTB nicht mehr als eine einzige überschreibbare JSON-Datei

TB-Server 1.0 speichert BTB fachlich granular:

- BTB-Kopf;
- Personal/Firmen;
- Maschinen/Geräte;
- Wetter;
- Abschnitt 4 Arbeiten – einzelne Einträge;
- Abschnitt 5 Abnahmen – einzelne Referenzen/Einträge;
- Abschnitt 6 Einweisung – einzelne Einträge;
- Abschnitt 7 Besonderheiten – einzelne Einträge;
- Abschnitt 8 Mängel – einzelne Referenzen/Einträge;
- Fotos/Anhänge.

Jeder veränderbare Eintrag erhält eigene UUID und Version. Damit können Nutzer 1 und Helfer A gleichzeitig unterschiedliche Einträge bearbeiten, ohne sich gegenseitig einen gesamten BTB zu überschreiben.

### 8.2 BTB-Lebenszyklus

- `ACTIVE` – sichtbarer Text `BTB aktiv`;
- `FINALIZED` – abgeschlossen;
- `REWORK` – bewusste Nachbearbeitung eines abgeschlossenen BTB;
- `DELETED` – Tombstone/Papierkorb.

`Neuer BTB` erzeugt genau eine neue `btbId` und setzt diese als `active_btb_id` des Teams.

Abschluss:

- Berechtigung prüfen;
- Server friert den ersten Original-Snapshot unveränderlich ein;
- aktuelles Abschluss-PDF erzeugen/speichern;
- Status `FINALIZED`;
- `active_btb_id = null`;
- andere Geräte erhalten das Ereignis sofort.

Nachbearbeitung reaktiviert exakt dieselbe `btbId`; fachliche Änderungen werden gegen den ersten Original-Snapshot verglichen.

Löschen erzeugt einen Tombstone für dieselbe `btbId`; alle Teamgeräte entfernen den BTB beim Sync. Echte Baustellenfotos bleiben erhalten und verlieren nur die BTB-Zuordnung, soweit fachlich vorgesehen.

## 9. Synchronisation

### 9.1 Grundregel

Speichern bedeutet nicht `alle 5 Minuten vielleicht synchronisieren`.

Ablauf auf Android:

1. fachliche Änderung lokal speichern;
2. in derselben lokalen Transaktion dauerhaften Outbox-Eintrag erzeugen;
3. bei Netz sofort an Server senden;
4. Server wendet Änderung idempotent an;
5. Server erzeugt Sync-Event;
6. andere offene Clients erhalten Signal und laden nur die Änderung.

Ohne Netz bleibt die Outbox erhalten und wird beim nächsten Netzkontakt abgearbeitet.

### 9.2 Live-Aktualisierung

Für geöffnete Android-/Portal-Clients:

- WebSocket oder SSE für Ereignissignal;
- Signal enthält keine großen Nutzdaten, sondern IDs/Versionen;
- Client holt gezielt geänderte Datensätze.

Zusätzlicher Pull:

- beim App-Start;
- beim Resume;
- beim Öffnen einer Baustelle;
- beim Öffnen eines BTB;
- periodischer Hintergrundlauf nur als Sicherheitsnetz.

### 9.3 Idempotenz

Jede schreibende Clientaktion besitzt `client_change_id`. Wiederholte Uploads nach Netzabbruch dürfen serverseitig nicht doppelt angewendet werden.

### 9.4 Konflikte

- unterschiedliche Datensätze/Einträge werden automatisch gemerged;
- zwei Änderungen am exakt selben Eintrag werden über Basisversion erkannt;
- kein stilles Last-Write-Wins bei fachlich relevanten Konflikten;
- Konflikt wird nachvollziehbar angezeigt bzw. nach fachlicher Regel aufgelöst.

### 9.5 Change Feed

Server hält einen monotonen Ereignis-/Change-Cursor. Jeder Client merkt sich `last_seen_change_id` und kann `changes since X` laden. Dadurch muss nie die ganze Baustelle neu übertragen werden.

## 10. Mängel, Abnahmen und Bilder

Mängel und Abnahmen sind baustellenweite Fachobjekte mit stabilen UUIDs, Versionen, Status, Autor und Audit-Historie.

Bilder/Dateien sind eigene Objekte und werden über Referenzen zu BTB/Mangel/Abnahme/Baustelle verbunden.

Jede Datei erhält mindestens:

- UUID;
- Firma;
- Baustelle;
- fachliche Zuordnung;
- MIME-Typ;
- Größe;
- SHA-256;
- Storage-Key/Pfad;
- Ersteller;
- Erstellzeit;
- optional vorhandene Aufnahme-/GPS-Metadaten;
- Löschstatus.

Portal-Bilder erhalten kein künstlich erzeugtes GPS. Vorhandene EXIF-Informationen dürfen separat ausgewertet werden.

## 11. Strecken- und Vermessungsdaten

### 11.1 Unveränderliche Originale

Originale TRA/GRA/KF/KOR/KV/GVP usw. werden unverändert auf dem NAS abgelegt. Mobile oder Portal-Bearbeitung überschreibt niemals diese Originaldateien.

### 11.2 Arbeitsdaten

Separat versioniert werden:

- Achsen;
- berechnete Samples;
- KM-Werte/Quellen;
- manuelle Korrekturen;
- bearbeitete Abschnitte;
- Streckenpunkte;
- grafischer Bearbeitungszustand.

Langfristige Farbregel:

- grün = belastbare Original-/Absteckungs-KM;
- orange = manuell/bearbeitet;
- rot = Geometrie vorhanden, aber keine belastbare KM;
- grau = Objekt ohne Vermessungsgeometrie;
- blau = Auswahl/temporäre Markierung.

Handys laden nur die für ihre Baustelle benötigten Daten. Serverseitig können passende Abschnitte anhand stabiler Strecken-/Gleis-/Geometrie-Identitäten zusammengeführt werden.

## 12. Dateispeicher

Große Binärdaten liegen nicht als große BLOBs in PostgreSQL.

Logische Struktur:

```text
/tb-helfer-data/
  companies/<company_uuid>/
    company/logo/
    worksites/<worksite_uuid>/
      btb/
      photos/
      defects/
      acceptances/
      exports/
      track-data/
  reference/
    survey-originals/
    routes/
    stations/
  releases/
    android/
    windows/
    ios/
```

Sichtbare Namen dürfen sich ändern; Storage-Keys bleiben UUID-basiert.

Uploads werden streamend/chunkweise unterstützt. Ziel-Chunkgröße ca. 8–16 MiB, damit große Archive/Vermessungsdaten zuverlässig fortgesetzt werden können und keine unnötig großen Einzelrequests über Cloudflare entstehen.

## 13. Löschen, Archiv und Papierkorb

Datenzustände werden getrennt:

- aktiv;
- archiviert;
- Papierkorb/soft deleted;
- endgültig gelöscht.

Baustelle komplett löschen wird von einem berechtigten Baustellenbenutzer/Verantwortlichen ausgelöst. Server erzeugt Tombstone; andere Geräte löschen ihre lokale Kopie. Admin darf nicht versehentlich eine laufende Baustelle ohne besonderen Löschprozess entfernen.

Papierkorb ermöglicht Wiederherstellung innerhalb einer konfigurierbaren Frist. Löschcenter ist für endgültige physische Entfernung nach Sicherheitsfrist/Bestätigung.

Historische Autor-/Benutzerreferenzen werden beim Sperren/Löschen eines Benutzerzugangs nicht zerstört.

## 14. Audit-Protokoll

Server protokolliert mindestens:

- Anmeldung/gesperrte Anmeldung;
- Geräteaktivierung/-sperre;
- Rechteänderung;
- Rollenänderung;
- Baustellenerstellung/-löschung/-wiederherstellung;
- Teamänderung;
- BTB-Erstellung/Abschluss/Nachbearbeitung/Löschung;
- Mangel-/Abnahme-Abschluss;
- Firmenlogo/PDF-Layout-Änderung;
- Releasefreigabe;
- administrative Löschvorgänge.

Auditdaten enthalten keine Geheimnisse.

## 15. TB-Helfer Portal

Portal ist vollwertiger Fachclient gegen dieselbe API wie Android.

Je nach Recht möglich:

- Baustellen anzeigen/wechseln;
- Baustellenstruktur ansehen/bearbeiten;
- BTB erstellen, bearbeiten, abschließen, nachbearbeiten;
- Punkte 4–8 bearbeiten;
- Bilder vom PC hinzufügen;
- Mängel/Abnahmen bearbeiten und abschließen;
- PDF/DOCX/Archive erstellen und herunterladen;
- Mitglieder/Rechte verwalten, soweit erlaubt;
- Streckendaten ansehen/bearbeiten, soweit erlaubt.

Nicht identisch zum Handy:

- keine mobile Live-GPS-Aufnahme;
- keine Kamera-GPS-Funktion;
- PC-Upload erfindet keine Position.

`CHEF` sieht im Portal alle Baustellen der eigenen Firma. Andere Nutzer sehen nur Baustellen, deren Mitglied sie sind bzw. für die ihnen Firmenrechte Zugriff geben.

## 16. Administration

Administration bleibt vom Benutzerportal getrennt.

### Übersicht

Anzeigen:

- NAS-/TB-Helfer-Speicher gesamt/belegt/frei;
- Speicher je Firma;
- Upload/Download 24 h;
- API-Uptime;
- PostgreSQL-Status;
- letzter Backupstatus;
- offene/fehlgeschlagene Syncjobs;
- letzte Fehler;
- aktive Geräte;
- Clientversionen;
- sinnvolle Diagramme für Speicher/Traffic/Fehler.

### Firmen

- immer eigener Firmenbereich, unabhängig von Benutzerzahl;
- Firmenprofil/Logo/PDF-Layout;
- Nutzer/Geräte/Baustellen/Speicherübersicht.

### Benutzer

- Firma Pflicht für normale Benutzer;
- Firmenrolle;
- Zugriff gültig bis;
- Portalstatus;
- Aktivierungskarte;
- Geräte-/Sessionstatus.

### Geräte

Bestehende Geräteansicht erweitern um letzten Sync, Syncfehler und Kompatibilitätsstatus.

### Baustellen

Admin überwacht Zustand, Speicher, Teams und Sync. Laufende fachliche Rechteverwaltung erfolgt primär durch berechtigte Baustellenbenutzer/CHEF, nicht durch normalen Admin-Alltag.

### Dateien

Klare Sicht auf Speicherobjekte, Firma, Baustelle, Typ, Größe, Hash, Erstellzeit und Löschstatus.

### Systemstatus

- API;
- DB;
- Storage;
- Cloudflare-Erreichbarkeit;
- Background Jobs;
- Queue;
- Backup;
- Versions-/Schema-/Capability-Stand.

## 17. App-/Client-Releases

Releaseverwaltung je Plattform:

- Android;
- Windows später optional;
- iOS als Store/TestFlight/MDM-Verweis statt freie IPA-Installation auf normalen Geräten.

Releasefelder:

- Plattform;
- Version;
- Build;
- Datei/Downloadziel;
- SHA-256;
- veröffentlicht am;
- `minimum_supported_build`;
- optional/Pflichtupdate;
- Release Notes;
- aktiv/inaktiv.

Android kann neue APK erkennen/herunterladen; Installation erfolgt entsprechend Android-Sicherheitsmodell und ggf. Benutzerbestätigung.

## 18. Cloudflare / Datenverkehr

Cloudflare Tunnel ist Transportweg, nicht Datenspeicher.

Datenverkehr klein halten:

- Change Feed statt komplette Baustellen laden;
- nur geänderte BTB-Einträge übertragen;
- Bilder einmal hochladen;
- Thumbnails separat;
- große Dateien chunkweise;
- keine unnötigen Vollsyncs;
- keine Worker-/Edge-Abhängigkeit für die Kernfachlogik.

## 19. Backup und Restore

Backup umfasst immer:

- PostgreSQL;
- Dateispeicher;
- notwendige Konfigurationsmetadaten ohne Klartext-Geheimnisse.

Admin zeigt letzten Backupzeitpunkt, Erfolg/Fehler und Größe.

Regelmäßig Restore-Test auf getrenntem Ziel durchführen.

Neues/ersetztes Handy kann nach Aktivierung seine Baustellen aus dem Server wiederherstellen. Syncdaten und vollständiges Baustellenbackup sind konzeptionell getrennt, nutzen aber dieselben serverseitigen Fachobjekte.

## 20. Test-/Staging-/Produktivstrategie

Vor TB-Server-1.0-Cutover:

1. bestehenden NAS-Stand vollständig sichern;
2. Backup verifizieren;
3. neuen Serverstand zunächst gegen Test-/Staging-Datenbank starten;
4. automatisierte Contract-/Integrationschecks ausführen;
5. Testnutzer/Testbaustellen End-to-End prüfen;
6. danach produktiven Testbestand zurücksetzen;
7. TB-Server 1.0 frisch initialisieren;
8. Administrator/Firmen/Testnutzer neu anlegen;
9. Android/Portal anbinden.

Der Benutzer hat ausdrücklich freigegeben, dass der derzeitige Testbestand an Benutzern/Baustellen/Fachdaten beim 1.0-Neuaufbau verworfen werden darf. Originale Vermessungsdaten auf dem NAS sind davon ausdrücklich ausgenommen und bleiben unverändert.

## 21. Akzeptanztests für Server 1.0

Vor Android-Weiterentwicklung müssen mindestens folgende Szenarien serverseitig bzw. mit Testclient grün sein:

1. Firma + CHEF + TB + Helfer anlegen;
2. Gerät aktivieren und Portal anmelden;
3. TB erstellt Baustelle und Unterbaustellen;
4. zweiter Benutzer sieht dieselbe Struktur;
5. Rechte am Handy/Portal ändern und serverseitig durchsetzen;
6. TB-Team erstellt `BTB aktiv`;
7. Helfer ergänzt gleichzeitig anderen BTB-Eintrag;
8. beide Änderungen bleiben erhalten;
9. Bild hochladen und auf anderem Client sehen;
10. BTB abschließen -> auf allen Clients abgeschlossen;
11. bewusste Nachbearbeitung derselben `btbId`;
12. BTB löschen -> Tombstone auf allen Teamclients;
13. Mangel/Abnahme baustellenweit synchronisieren;
14. mobile Streckendaten übertragen, Originale unverändert;
15. Baustelle löschen -> Server + andere Clients entfernen sie;
16. Offline-Outbox nach Netzrückkehr korrekt abarbeiten;
17. Benutzerrecht entziehen -> Server lehnt künftig verbotene Aktion ab;
18. CHEF sieht alle Baustellen seiner Firma, keine fremde Firma;
19. Portal kann BTB inklusive Bilder/PDF bearbeiten/abschließen;
20. Backup + Restore praktisch testen;
21. alter/in-kompatibler Client wird sauber blockiert oder nur lesend zugelassen.

## 22. Bau-Reihenfolge innerhalb des einen Server-1.0-Blocks

Auch wenn TB-Server 1.0 als zusammenhängender Block gebaut wird, bleibt die interne Reihenfolge kontrolliert:

1. Server-Skelett/Config/Capabilities/Migration Runner;
2. Firmen/Benutzer/Auth/Portal-Login/Geräte;
3. Baustellen/Mitgliedschaften/Rechte/Teams;
4. Sync-Eventlog/Outbox-Vertrag/Idempotenz;
5. granularer BTB + Abschluss/Nachbearbeitung/Tombstones;
6. Dateien/Bilder/Chunkupload;
7. Mängel/Abnahmen;
8. Baustellenbaum + mobile Streckendaten + Originaldatenkatalog;
9. Portal-Fachoberfläche;
10. Admin-Dashboard/Systemstatus/Speicher/Backups;
11. Releases/Client-Kompatibilität;
12. Reset-/Staging-/Backup-/Restore-Werkzeuge;
13. vollständige Integrations- und End-to-End-Tests;
14. erst danach NAS-Cutover und Android-Anpassung.

Diese Reihenfolge ist kein erneutes Tropfen-Flicken: Alle Module werden gegen denselben vorab definierten Server-1.0-Vertrag gebaut und erst als Gesamtstand freigegeben.
