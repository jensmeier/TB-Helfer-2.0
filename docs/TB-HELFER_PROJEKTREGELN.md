# TB-Helfer – verbindliche Projektregeln

## 0. Gemeinsame Projektsteuerung

Die gemeinsame Projektsteuerung liegt ausschließlich unter `projektsteuerung/`.

Verbindlicher Einstieg für **jeden neuen Chat und jeden neuen Arbeitsblock**:
1. `projektsteuerung/00_START_HIER.md`
2. Live-GitHub-Stand beider Arbeitsbranches + Draft-PRs
3. `projektsteuerung/01_MASTERSTATUS.md`
4. `projektsteuerung/02_AKTIVER_AUFTRAG.md`
5. eigener Rollenstatus: `03_ANDROID_STATUS.md` oder `04_SERVER_STATUS.md`
6. Gegenstellenstatus
7. `projektsteuerung/05_ANDROID_SERVER_VERTRAG.md`
8. `projektsteuerung/06_E2E_TESTSTATUS.md`
9. `projektsteuerung/07_OFFENE_PUNKTE.md`
10. relevante Fachregeln und aktuelle Code-Dateien

Neue fachliche Anforderungen, Vertragsänderungen oder wesentliche Statusänderungen werden **vor der Implementierung** in der Projektsteuerung gesichert und committed. Reine Erklärungen oder unveränderte Auskünfte brauchen keinen Commit.

`☑`, `BESTÄTIGT`, `ERLEDIGT`, `PASST`, `FUNKTIONIERT` und `Nicht ohne Grund ändern = JA` bleiben geschützt.

Der Chat-Kontext wird nicht mit erfundenen Prozentwerten bewertet. Stattdessen gilt ereignisbasierte Sicherung: nach jedem atomaren Arbeitsblock, vor Android↔Server-Wechsel, nach Build/Test und vor einer Antwort mit „fertig/erledigt“ den Stand in `projektsteuerung/08_CHAT_CHECKPOINT.md` bzw. den zuständigen Statusdateien sichern.

Die früheren Top-Level-Ordner `projektstatus/`, `releases/` und `uebergaben/` sind außer Betrieb und dürfen nicht neu angelegt werden.

Stand: 2026-09-26

Diese Datei hält die fachlichen Entscheidungen für Server, Android-App und später iOS fest. Bei Änderungen zuerst diese Datei aktualisieren.

## 1. Grundarchitektur
- Synology/NAS ist zentrale Serverinstanz und Source of Truth.
- Android arbeitet offline mit lokalen Kopien und synchronisiert nur benötigte Daten.
- Originale Vermessungsdateien (z. B. TRA, GRA, KF, KOR) bleiben unverändert und dauerhaft nachvollziehbar.
- Bearbeitete/berechnete Abschnitte werden getrennt und nachvollziehbar gespeichert.
- Cloudflare/TLS für normale App-Synchronisation; Tailscale nur Wartung/Notfall.

## 2. Strecken/Bahnhöfe
- Strecke: Streckennummer Pflicht, KM-Bereich nicht grundsätzlich Pflicht.
- Bahnhofseingabe muss Autovervollständigung nutzen.
- Keine neue externe Bahnhofsliste erfinden; vorhandene App-Daten verwenden:
  - `app/src/main/assets/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
  - `app/src/main/assets/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`
  - `app/src/main/java/de/tbhelfer/util/BahnhofSucheUtil.kt`
- Server-Admin soll dieselbe fachliche Datenbasis für Autofill verwenden.
- Originale Vermessungs-/Streckendateien auf dem NAS werden durch mobile Bearbeitung niemals überschrieben.
- Berechnete oder manuell bearbeitete Streckenabschnitte werden getrennt von den Originaldaten synchronisiert.
- Die App lädt nur die für Baustelle/Berechtigung benötigten Strecken-, Gleis-, Weichen- und Abschnittsdaten.
- Im Streckendaten-Bereich gibt es `Änderungen mit NAS synchronisieren`; übertragen werden nur geänderte/berechnete Abschnitte.
- Zusätzlich gibt es einen **firmenübergreifend gemeinsamen Fachkatalog** für Strecken, Bahnhöfe/Betriebsstellen, Gleise, Weichen/Kreuzungen und weitere Bahnhof-/Streckenobjekte. Authentifizierte Benutzer dürfen fehlende Katalogeinträge ergänzen und vorhandene Einträge korrigieren.
- Der gemeinsame Fachkatalog hat **kein normales globales Löschen**. Eine falsche Eingabe wird durch eine neue Korrekturversion berichtigt; der vorherige Stand bleibt in der Änderungshistorie nachvollziehbar.
- Jede Katalogänderung wird mit stabiler Objekt-ID, Versionsnummer, Vorher-/Nachher-Stand sowie Bearbeiter/Firma protokolliert. Gleichzeitige Änderungen werden über Basisversion/Versionskonflikt geschützt.
- Für normale Nutzer werden Bearbeiter/Firma bei Änderungen anderer Firmen nicht offengelegt; sichtbar bleibt nur eine neutrale Änderungsangabe. Admin sieht die vollständige Historie.
- Der gemeinsame Fachkatalog überschreibt niemals geschützte Vermessungsoriginale.

## 2a. Admin-Oberfläche – bestehende Bedienlogik als Referenz
- Die bestehende TB-Helfer-Administration 0.7 ist **Bedien-Referenz** für Server 1.0. Server 1.0 darf technisch anders aufgebaut sein, soll bewährte Admin-Abläufe aber nicht ohne fachlichen Grund neu erfinden.
- Die Hauptnavigation bleibt fachlich getrennt in mindestens: **Übersicht, Strecken, Bahnhöfe, Benutzer, Firmen, Geräte, Dateien, Baustellen, App-Versionen, Papierkorb, Systemstatus**. Eine vorübergehende kombinierte Entwicklungsseite wie `Nutzer/Geräte` ersetzt diese Zielstruktur nicht.
- **App-Versionen** verwendet den vorhandenen Release-Katalog. Der Admin soll eine Release-Info-Datei (`.md`/`.txt`) und die APK/Binärdatei sichtbar auswählen können. Die Release-Datei darf Version, Build, Mindestbuild, Pflichtupdate sowie die Abschnitte `Neu`, `Verbessert`, `Behoben` enthalten. Nach dem Einlesen bleiben alle Angaben im Draft auf dem Server editierbar; veröffentlicht wird erst nach bewusster Prüfung. Eine zweite Update-Infrastruktur wird nicht aufgebaut.
- **Übersicht** zeigt mindestens die zentralen Zähler für Strecken, Bahnhöfe, Gleise/Weichen, Baustellen, Originaldateien, Benutzer, Geräte, App-Versionen und Papierkorb sowie Firmen-/Benutzerzusammenfassung, Baustellenzusammenfassung, Serverversion, Speicherbelegung und Serverzeit.
- Die **Übersicht Server 1.0** zeigt zusätzlich verbindlich: gesamten/freien NAS-Speicher in verständlicher GB/GiB-Darstellung, HTTP-Upload und -Download der letzten 24 Stunden, API-Laufzeit/Online-seit, erkannte Probleme/Warnungen sowie ein 24-Stunden-Datenverkehrsdiagramm. Diese Werte müssen aus realen Servermetriken stammen und dürfen keine statischen Demo-Werte sein.
- Stündliche HTTP-Metriken werden serverseitig datensparsam aggregiert (`request_count`, `error_count`, `bytes_in`, `bytes_out`); es werden dafür keine Request-Inhalte, Tokens oder personenbezogenen Payloads gespeichert.
- **Systemstatus / Automatik** übernimmt die bewährte Bedienlogik des alten Servers: automatische DATABASE-/FILES-/FULL-Backups mit Uhrzeit, automatische Retention, einstellbare Aufbewahrungsdauer/Mindestanzahl, E-Mail-Systemberichte (täglich/wöchentlich/monatlich), Zeitzone, Warnzeitraum für ablaufende Nutzer, Speicherwarnschwelle, Testbericht sowie sichtbare Audit-/Sicherheitsereignisse.
- SMTP-Zugangsdaten sind Server-Secrets. Host/Port/Absender und der Zustand „Passwort gesetzt“ dürfen im Admin angezeigt werden; das SMTP-Passwort selbst wird **niemals** in PostgreSQL, `server_settings`, Audit, MD/XLSX, Logs oder HTML gespeichert/ausgegeben.
- Automatische Backup-/Reportläufe werden ausschließlich über den bestehenden Background-Worker und deduplizierte Zeitplan-Slots ausgeführt; die bewährten manuellen Backup-/Restore-Test-Pfade werden dadurch nicht ersetzt.
- **Benutzer** und **Geräte** bleiben getrennte Admin-Bereiche.
- Die Geräteübersicht zeigt mindestens: Benutzer, Firma, Geräte-ID, Status, letzter Kontakt und Aktionen.
- **Benutzer löschen** folgt dem bestehenden sicheren Admin-Verhalten: Der Benutzer wird soft-deleted/deaktiviert und verschwindet aus der aktiven Benutzerliste; Portal-Sessions, Portal-Aktivierung, offene Geräte-Aktivierungs-Keys und aktive Geräte-Tokens werden sofort ungültig. Gebundene Geräte werden dabei freigegeben. Aktive Baustellen-/Teamzuordnungen werden beendet, historische Fachreferenzen bleiben über die stabile Benutzer-ID erhalten. Ein Benutzer mit aktivem BTB als Teamleitung darf nicht gelöscht werden, bevor der aktive BTB beendet/gelöst ist.
- Ein soft-gelöschter Benutzer blockiert seinen Login-Namen nicht dauerhaft; derselbe Login darf nach dem sicheren Löschen wieder für einen neuen aktiven Benutzer verwendet werden. Endgültiges Löschen/Purge bleibt ein separater Löschcenter-/Papierkorb-Schritt.
- **Endgültig / komplett löschen** ist eine davon getrennte, ausschließlich administrative und irreversible Aktion mit zusätzlicher Texteingabe `ENDGUELTIG LOESCHEN`. Der normale Benutzer-Button `Sicher löschen` bleibt Soft-Delete und schützt historische Fachreferenzen.
- Ein **Benutzer-Purge** entfernt Benutzeridentität, Portal-/Aktivierungsdaten, Geräte und direkte Zuordnungen vollständig. Wenn der Benutzer als Teamleitung bereits BTB-Historie besitzt, wird der Purge absichtlich blockiert; dann muss entweder der sichere Soft-Delete verwendet oder die betroffene Baustelle bewusst komplett gelöscht werden.
- Ein **Geräte-Purge** entfernt den Geräte-Datensatz einschließlich Geräte-Token-/Synczustand vollständig. Eine spätere Nutzung benötigt eine neue Geräteaktivierung.
- Ein **Baustellen-Purge** entfernt die komplette Baustellen-Fachstruktur einschließlich Teams, BTBs, Mängel, Abnahmen, baustellenbezogener Dateien und Upload-Zwischenstände. Das ist ausdrücklich stärker als normales lokales Entfernen/Soft-Delete.
- Ein **Firmen-Purge** entfernt in staging/test die Firma samt ihren Benutzern, Geräten, Baustellen, Dateien und Firmen-Vermessungsoriginalen in einem bewusst bestätigten Schritt. In `production` blockiert der normale Firmen-Purge, sobald geschützte Vermessungsoriginale vorhanden sind; diese Schutzregel bleibt nach dem ersten produktiven Originalimport verbindlich.
- **Release-Drafts** dürfen komplett gelöscht werden, einschließlich bereits hochgeladener Draft-Binärdatei. Ein bereits `PUBLISHED` oder `RETIRED` Release bleibt nachvollziehbar und wird nicht hart gelöscht; veröffentlichte Releases werden nur zurückgezogen.
- Ein aktives Gerät wird im Gerätebereich über **Freigeben** gelöst. Die Benutzer-Aktivierungskarte kann einen weiteren noch unbenutzten Aktivierungs-Key erzeugen, ohne bereits ausgegebene offene 30-Tage-Keys allein dadurch zu widerrufen. Erst die erste erfolgreiche Gerätebindung verbraucht den verwendeten Key und widerruft die übrigen offenen Aktivierungs-Keys desselben Benutzers. Ein bereits gebundenes Gerät bleibt aktiv, bis es ausdrücklich im Gerätebereich freigegeben wird.
- Der Benutzerbereich bietet pro Benutzer die Aktion **Aktivierungskarte**. Die Aktivierungskarte zeigt Name, Firma, Zugriff, Klartext-Aktivierungs-Key und QR-Code sowie die Hinweise zur ersten Gerätebindung und zum sicheren Geräte-Token.
- Die Aktivierungskarte hat die Aktionen **Drucken / als PDF speichern** und **Zurück zu Benutzer**. Eine direkt herunterladbare PDF darf ergänzend angeboten werden.
- Der Büro-/Fernablauf ist verbindlich: **Benutzer anlegen -> Aktivierungskarte erzeugen -> QR/PDF an Nutzer übermitteln -> Nutzer scannt QR in der TB-Helfer-App -> erstes Gerät wird gebunden**.
- Geräte-Aktivierungs-Keys sind ab Erzeugung **30 Tage gültig** und **nur einmal verwendbar**. Ein erneutes Öffnen/Erzeugen einer Aktivierungskarte darf bereits ausgegebene, noch unbenutzte Keys nicht allein dadurch ungültig machen. Sobald ein Key erfolgreich ein Gerät bindet, wird dieser Key verbraucht und alle anderen noch offenen Geräte-Aktivierungs-Keys desselben Benutzers werden ungültig. Benutzerlöschen/-deaktivieren widerruft offene Keys ebenfalls. Bereits gebundene Geräte bleiben von bloß neu erzeugten Aktivierungskarten unberührt.
- Die Server-1.0-Aktivierung darf im QR zusätzlich notwendige technische Verbindungsdaten wie die kanonische öffentliche HTTPS-Serveradresse mitführen; diese Technik bleibt für den Nutzer unsichtbar und ändert den beschriebenen Bedienablauf nicht.
## 3. Firmen, Benutzer und Profil-Autofill
- Baustellen-Zusammenarbeit ist strikt auf die eigene Firma begrenzt.
- Nutzer verschiedener Firmen dürfen nicht derselben Baustellen-Teamstruktur zugeordnet werden.
- Ein Benutzer hat standardmäßig genau ein aktives Gerät.
- Die Geräte-Aktivierungskarte/der Geräte-QR enthält neben dem kurzlebigen Einmal-Schlüssel auch die kanonische öffentliche **HTTPS-Serveradresse**. Ein Benutzer muss niemals eine Serveradresse manuell in der App konfigurieren.
- Android übernimmt die Serveradresse erst nach erfolgreicher Geräteaktivierung, speichert sie lokal als nicht geheime Serveridentität und verwendet sie anschließend für alle Server-/NAS-/Cloudflare-Syncpfade.
- Eine spätere Reaktivierung darf die gespeicherte Serveridentität nur durch eine erneut erfolgreich bestätigte Aktivierung ersetzen.
- BuildConfig/`local.properties` dürfen ausschließlich als Entwickler-/Legacy-Fallback dienen und sind kein normaler Aktivierungs- oder Benutzerkonfigurationsweg.
- Aktivierungskarten dürfen als QR/PDF erzeugt und an entfernte Nutzer versendet werden. QR/PDF enthalten einen geheimen, **30 Tage gültigen Einmal-Schlüssel**, sind vertraulich zu behandeln und werden serverseitig nicht dauerhaft mit Klartext-Schlüssel archiviert.
- Admin kann ein Gerät freigeben; danach ist eine neue Aktivierungskarte/Einmal-Schlüssel nötig.
- Nach Geräteaktivierung übernimmt die App vorhandene Stammdaten automatisch vom Server, soweit der Serverstand sie bereitstellt.
- Firmenstammdaten sind zentral: Firmenname, Anschrift, Firmentelefon, Firmen-E-Mail und Firmenlogo.
- Jeder Benutzer besitzt eigene persönliche Telefonnummer und persönliche E-Mail-Adresse. Diese sind nicht die Firmenkontakte.
- Persönliche Telefonnummer und persönliche E-Mail dürfen für alle Benutzer derselben Firma sichtbar sein; andere Firmen erhalten sie nicht.
- Persönliche Telefonnummer/E-Mail sind auf dem Handy **immer lokal eingebbar und speicherbar**, auch wenn der produktive Server das persönliche Profil noch nicht unterstützt. Sobald der passende Serverstand verfügbar ist, wird derselbe lokale Stand zusätzlich synchronisiert.
- Der sichtbare Benutzername kommt aus der vorhandenen Server-/Geräteidentität und wird nicht durch fehlende persönliche Profilfelder leer gelassen.
- Das Firmenlogo wird einmal serverseitig hinterlegt und von Geräten derselben Firma geladen/gecached.
- Die App darf eine nicht geheime Support-/Gerätekennung und einen maskierten Schlüsselhinweis anzeigen, niemals vollständige Geheimwerte.
- Baustellen-Einladungen verändern Benutzer-/Firmenstammdaten nicht.
- Die technische Benutzeridentität ist die stabile UUID. Gleiche sichtbare Vor-/Nachnamen bei mehreren Personen sind erlaubt und werden nicht als Identitätsschlüssel verwendet.
- Beim administrativen Anlegen eines Benutzers genügen Firma, Vorname, Nachname, Firmenrolle und optionale Zugriffsgültigkeit. Der Server erzeugt einen vorläufigen eindeutigen Portal-Login.
- Bei der **ersten Portal-Aktivierung** legt der Benutzer seinen eigenen eindeutigen Login, Anzeigenamen, Passwort sowie persönliche E-Mail/Telefon fest.
- Der Admin darf Firmenzuordnung, Vor-/Nachname als administrative Stammdaten, Firmenrolle und Zugriffsgültigkeit bearbeiten. Ein Firmenwechsel wird blockiert, sobald Baustellen-/Teamhistorie vorhanden ist.
- Nach erfolgter Portal-Aktivierung überschreibt eine normale Admin-Bearbeitung nicht den selbst gepflegten Login, Anzeigenamen oder persönliche E-Mail/Telefon.
- Persönliche Kontakte sind für aktive Benutzer derselben Firma sichtbar; andere Firmen erhalten sie nicht.

## 4. Baustellen-Teammodell und Einladungen
- Der Admin verwaltet Benutzer, Firmen, Geräte und Server-Speicheroptionen, aber nicht die laufende Baustellenzuordnung.
- Eine Baustelle wird durch einen verantwortlichen TB angelegt/verbunden; dieser ist erster verantwortlicher TB.
- Weitere verantwortliche TBs und Helfer treten über sichere Baustelleneinladungen (QR/Link/Code) bei; gleiche Firma ist Pflicht.
- Helfer werden dem Team ihres verantwortlichen TB und ihrem freigegebenen Bereich zugeordnet.
- Ohne angenommene Einladung keine Baustellen-Zusammenarbeitsdaten.
- Einladungen enthalten keine Benutzer-Keys/Passwörter, sondern kurzlebige/widerrufbare Tokens.
- A/B/C/D bleibt rein technisch und wird Benutzern nicht angezeigt.
- `QR scannen` und `Nutzer hinzufügen` gehören direkt in die Baustellenansicht.
- Rolle kommt aus der Einladung und wird nach dem Scan nicht erneut gewählt.
- Nach Beitritt wird eine bereits aktive andere Baustelle nicht ungefragt ersetzt; die App fragt `Jetzt öffnen?` / `Später`.
- Verantwortliche TBs erhalten vollständige gemeinsame Baustellenstruktur und benötigte Streckendaten.
- Helfer erhalten Struktur/Streckendaten nur innerhalb ihrer Berechtigung.
- Baustellenkarte zeigt mindestens Teamumfang und Sync-Stand, z. B. `2 TB · 4 Helfer` und `Zuletzt synchronisiert ...`.
- Entfernte/ausgetretene Mitglieder behalten historische lokale Daten nur lesbar; kein weiterer Sync/keine Bearbeitung.
- Interne Zuordnung nutzt stabile IDs, nicht sichtbare Namen.
- Mehrere Baustellen dürfen gleichzeitig lokal vorhanden sein. Genau eine Baustelle ist jeweils `aktuell`; beim Umschalten werden deren eigener BTB-/Datenzustand geladen. Daten verschiedener Baustellen dürfen sich nie vermischen.

## 5. BTB-Regel, Identität, Abschluss und Export
- BTB ist NICHT baustellenweit gemeinsam.
- Jeder verantwortliche TB besitzt mit seinen Helfern seinen eigenen gemeinsamen BTB-Bereich.
- Helfer desselben TB-Teams bearbeiten denselben BTB; andere verantwortliche TB-Teams bleiben getrennt.
- Ein BTB wird nur bewusst über `Neuer BTB` angelegt. Bloßes Öffnen erzeugt keinen Entwurf und keine Tag-/Nacht-Leerplätze.
- Jeder neue BTB erhält eine **dauerhafte stabile `btbId`**. Diese `btbId` bleibt beim Bearbeiten, Datums-/Zeitänderungen und Abschließen immer erhalten.
- Datum, Beginn, Ende und ggf. abgeleitete Tag/Nacht-Anzeige sind Attribute, nicht Identität.
- Mehrere BTBs desselben Teams am selben Kalendertag sind erlaubt.
- Ein BTB darf über Mitternacht laufen; ein zusätzliches leeres Tag-/Nacht-BTB entsteht nicht.
- Für moderne BTBs gilt als kanonischer lokaler Lesekontext: **aktuelle Baustelle → aktiver BTB-Pointer → `btbId` → Datensatz dieser `btbId`**. Datum/Uhrzeit/Tag/Nacht dürfen diesen aktiven BTB nicht durch eine unabhängige Kandidatensuche ersetzen.
- Grünes `+`: aktueller BTB wird zuerst gespeichert, aber nicht abgeschlossen; danach Dialog/Karte `Neuen BTB starten` mit Datum/Zeit, Personal, Geräte/Maschinen und Wetter. Erst `Neuer BTB` erzeugt eine neue `btbId`.
- Neben dem grünen `+` gibt es eine **Bearbeiten-Aktion (Stift)** für den aktuell geladenen BTB. Sie ändert immer denselben BTB/dieselbe `btbId`.
- Der Stift öffnet direkt ein kompaktes schwebendes Fenster `Aktuellen BTB bearbeiten` mit Datum/Zeit, Personal, Geräte/Maschinen und Wetter; kein zusätzliches vorgeschaltetes Listenmenü.
- Nach `Bearbeiten` in diesem Fenster wird direkt der gewählte Datum/Zeit-, Personal-, Geräte/Maschinen- oder Wetter-Editor geöffnet. Die alte Vierer-Auswahl mit Status-Pills darf nicht nochmals als Zwischenschritt eingeblendet werden.
- `Details anzeigen` ist ausschließlich eine **kurze schreibgeschützte Zusammenfassung** von Datum/Zeit, Personal, Geräte/Maschinen und Wetter. Änderungen dieser Kopfdaten erfolgen ausschließlich über den orangefarbenen Bearbeiten-Stift.
- Wenn noch kein BTB existiert, dürfen ausgeblendete Detailfelder keinen vertikalen Leerraum reservieren; die Standardabschnitte sollen möglichst weit oben beginnen. Bearbeiten- und PDF-Aktionen sind ohne realen BTB nicht sichtbar.
- Der blaue aktuelle-BTB-Balken und `BTB bearbeiten` im Verlauf öffnen immer exakt die gewählte `btbId`.
- Beim Wechsel BTB 1 ↔ BTB 2 muss der aktuelle BTB zuerst tatsächlich gespeichert sein; keine feste Zeitwarte-Lösung als Datenintegrität.
- Verlauf zeigt nur tatsächlich vorhandene BTBs.
- `PDF Entwurf` erzeugt nur eine Vorschau und öffnet sie direkt im App-eigenen PDF-Viewer; der BTB wird dadurch nicht abgeschlossen.
- Nach einem **erfolgreichen `BTB Abschluss` wird die aktive `btbId` gelöst**. Der abgeschlossene BTB bleibt vollständig im Verlauf gespeichert, aber die BTB-Seite zeigt danach `Noch kein BTB`.
- Ein abgeschlossener BTB wird nur durch eine bewusste Aktion `BTB-Verlauf -> BTB bearbeiten` wieder aktiv. Dabei wird exakt dieselbe `btbId` reaktiviert. Nach dem nächsten erfolgreichen Abschluss wird die aktive `btbId` erneut gelöst.
- Ein moderner BTB darf im Verlauf nach ausdrücklicher Sicherheitsabfrage vollständig gelöscht werden. Dabei werden BTB-Datensatz, Original-/Abschlussmetadaten und BTB-Dokumente gelöscht. **Echte Baustellenfotos bleiben erhalten** und werden nur von der gelöschten `btbId` gelöst.
- Wenn **kein BTB aktiv** ist und ein normaler Kamera-Einstieg der App verwendet wird – insbesondere BTB-Kamera und Startmenü `Aktuelle Baustelle` – fragt die App: `Nur Baustellenfoto` oder `BTB starten/öffnen`. `Nur Baustellenfoto` erhält keine BTB-Zuordnung; `BTB starten/öffnen` führt bewusst zur Neuanlage oder zum BTB-Verlauf. Fachlich spezielle Kamera-Flows wie Mangel-/Abnahmenachweis behalten ihre eigene Zuordnung.

### BTB-Abschluss – verbindliches Ein-PDF-/Original-Modell
- Die frühere sichtbare V1/V2/V3-Versionierung ist verworfen.
- **Ein BTB (`btbId`) besitzt höchstens ein aktuell gültiges Abschluss-PDF.**
- Beim ersten `BTB Abschluss` wird aus dem aktuellen Datensatz das PDF erzeugt. Erst wenn die Datei erfolgreich geschrieben wurde, gilt der BTB als `Abgeschlossen`.
- **Der Stand dieses ersten erfolgreichen Abschlusses ist das unveränderliche Original dieses BTB.**
- Dieses Original wird nach dem ersten Abschluss nie wieder überschrieben – auch nicht nach späteren Nachbearbeitungen oder weiteren Abschlüssen.
- Der Original-Snapshot gehört fachlich zum selben BTB-Datensatz. Eine interne Spiegeldatei ist für Rückwärtskompatibilität zulässig, aber keine sichtbaren V1/V2/V3.
- Nur das Öffnen/Bearbeiten eines abgeschlossenen BTB macht noch nichts ungültig.
- Sobald nach einem Abschluss tatsächlich eine Änderung gespeichert wird, wechselt der BTB zu `Nachbearbeitung`; das bisherige Abschluss-PDF ist ungültig und wird gelöscht.
- Fachliche neue oder geänderte Inhalte in den BTB-Abschnitten werden **immer gegenüber dem ersten Original** verglichen und rot dargestellt.
- Inhalte, die bereits bei einer früheren Nachbearbeitung rot waren, bleiben auch nach weiteren Abschlüssen rot, solange sie vom ersten Original abweichen.
- Wird ein fachlicher Wert später exakt auf den Originalwert zurückgesetzt, ist er wieder normal/schwarz.
- Gelöschte Originalinhalte werden im nächsten PDF rot als `(entfernt) ...` dargestellt.
- Unveränderte Originalinhalte bleiben normal.
- **Arbeitszeit/Datum, Personal/Firmen, Geräte/Maschinen und Wetter sind Kopfdaten und werden im PDF nicht rot markiert.** Sie dürfen geändert werden und können den aktuellen Abschluss technisch ungültig machen, bleiben in der Darstellung aber schwarz.
- Nach dem ersten BTB-Abschluss darf sich das Wetter **nicht mehr automatisch aktualisieren**. Spätere Wetteränderungen erfolgen nur bewusst/manuell über `Aktuellen BTB bearbeiten`.
- Beim nächsten `BTB Abschluss` wird wieder genau ein aktuelles PDF derselben `btbId` erzeugt. Das aktuelle PDF ersetzt das vorherige PDF, **nicht** das unveränderliche Original.
- Ein interner Abschluss-Hash darf speichern, aus welchem aktuellen BTB-Inhalt das gültige PDF erzeugt wurde. Dieser Hash ist keine sichtbare Version und darf das Original nicht ersetzen.
- PDF-Dateiname bleibt für dieselbe fachliche `btbId` stabil/merge-sicher; keine `_V1`, `_V2`, `_V100` und keine `(2)`-Ausweichnamen für fachlich verschiedene BTBs.
- Historische Alt-BTBs mit Tag/Nacht-/Version-Dateien bleiben lesbar; beim nächsten modernen Abschluss werden sie in das neue Ein-PDF-Verhalten überführt.
- `PDF öffnen` im BTB-Verlauf öffnet den App-eigenen PDF-Viewer.
- `ZIP teilen` im BTB-Verlauf ist als **Tagesabschluss-ZIP** zu verstehen: für den Kalendertag des gewählten BTB werden die vorhandenen Baustellen-Daten gemeinsam exportiert (BTBs, Bilder, Mängel, Abnahmen und Gesamtlisten). Es ist nicht mehr nur ein Einzel-BTB-ZIP.

### BTB-Fotos
- Neue normale BTB-Fotos werden zusätzlich eindeutig der beim Speichern aktiven `btbId` zugeordnet.
- Zwei BTBs mit überlappenden Arbeitszeiten dürfen dadurch nicht dieselben modernen Fotos nur wegen der Uhrzeit übernehmen. Fotozähler, Fotoauswahl, PDF und Export müssen dieselbe `btbId`-Zuordnung beachten.
- **Bei modernen, explizit zugeordneten Fotos ist die `btbId` stärker als das Arbeitszeitfenster.** Ein Foto bleibt Teil seines BTB, auch wenn es nach der ursprünglich eingetragenen Endzeit aufgenommen wurde oder die Arbeitszeit später geändert wird.
- **Für einen modernen BTB mit `btbId` gilt strikt: Nur Fotos mit exakt derselben `btbId` gehören in Fotozähler, Abschnittseditor, PDF und BTB-Export.** Ein allgemeines Baustellenfoto ohne `btbId` darf nicht später über Datum/Uhrzeit in diesen BTB hineinrutschen.
- Die Foto-Eingabemaske liest bei einem modernen aktiven BTB die Firmen ebenfalls aus **genau diesem aktiven `btbId`-Datensatz**. Eine separate Suche nach einem zeitlich passenden Tag-/Nacht-BTB darf die Firmenliste nicht bestimmen.
- Nur historische Fotos **und historische BTBs ohne `btbId`** dürfen weiterhin über Datum/Uhrzeit als Legacy-Fallback zusammengeführt werden.
- Der beim **ersten erfolgreichen BTB-Abschluss tatsächlich enthaltene Foto-Bestand gehört ebenfalls zum unveränderlichen Original** dieses BTB.
- Ein Foto, das erst nach diesem ersten Abschluss zum selben BTB hinzukommt, ist eine Änderung gegenüber dem Original und muss im späteren Abschluss-PDF **sichtbar rot als Änderung markiert** werden; das Original-JPG selbst bleibt unverändert.
- Jede tatsächliche Änderung der ausgewählten Foto-Menge nach einem Abschluss – neues Foto, entferntes Foto oder nachträglich ab-/wieder ausgewählte Abweichung – macht das bisherige Abschluss-PDF genauso ungültig wie eine Textänderung und setzt den BTB auf `Nachbearbeitung`, auch wenn der übrige BTB-Text unverändert ist.
- Bildreferenzen im Haupttext müssen dieselbe Original-Diff-Regel verwenden: Referenzen auf nachträglich hinzugefügte Bilder werden rot, Original-Bildreferenzen bleiben schwarz; gemischte Bereiche werden soweit technisch möglich differenziert dargestellt.
- Für bestehende BTBs aus einem älteren App-Stand darf die erste Foto-Basis einmalig aus dem bereits vorhandenen ersten BTB-Original/Abschlusszeitpunkt rekonstruiert werden; danach ist auch diese Foto-Basis unveränderlich.
- Datum/Uhrzeit bleiben Anzeige/Sortierung, nicht technische Foto-BTB-Zuordnung.
- Mängel-/Abnahme-Zuordnungen bleiben als eigene Fachbeziehungen bestehen.
- Allgemeine BTB-Bilder eines Helfers werden nicht automatisch auf alle Helfergeräte verteilt; Server + verantwortlicher TB + Erstellergerät wie beschlossen.

### Merge-sichere ZIP-/Laptop-Struktur
- Baustellen-Gesamtexport/Tagesabschluss besitzt als oberste Struktur `TB-Helfer/<Baustelle>/...` bzw. die bestehende exportierte Baustellenstruktur.
- Ein Tagesabschluss-ZIP umfasst mindestens BTB, Bilder, Mängel, Abnahmen sowie die vorhandenen Gesamtlisten wie `Bilderliste_gesamt.csv/.xlsx`, Mängel- und Abnahmelisten.
- Viele Exporte dürfen in einen gemeinsamen Laptop-Hauptordner `TB-Helfer` zusammengeführt werden.
- Unterschiedliche `btbId` dürfen niemals denselben Zielpfad erhalten.
- Derselbe fachliche BTB erhält bei wiederholtem Export denselben stabilen Dokumentnamen; das aktuelle Dokument ersetzt dort den älteren Stand desselben BTB.
- Bilder, Mängel-, Abnahme- und sonstige Exportdateien erhalten stabile eindeutige Kennungen; `IMG_1234.jpg` allein reicht nicht.
- Gleichnamige Benutzer dürfen intern keine Kollision verursachen; stabile Team-/Benutzerkennungen bleiben maßgeblich.

## 6. Mängel, Abnahmen und Bilder
- Mängel und Abnahmen sind nur innerhalb der konkreten Baustelle gemeinsam.
- Nur Mitglieder dieser Baustelle erhalten diese Daten.
- Verantwortliche TBs derselben Baustelle sehen baustellenweite Mängel/Abnahmen und zugehörige Bilder.
- Helfer sehen/bearbeiten sie nur im freigegebenen Bereich.
- Bilder zu Mängeln/Abnahmen werden nach denselben Berechtigungsregeln synchronisiert.
- Mangelstatus `ERLEDIGT` ist endgültig und darf durch älteren Sync nicht wieder geöffnet werden.
- Endgültige Abnahmestatus bleiben ebenfalls endgültig.
- Änderungen sollen möglichst schnell auf berechtigten Geräten erscheinen.

## 7. Benachrichtigungen
Pro Gerät:
- Text + Ton
- nur Ton
- aus

Nur Daten aus berechtigten Baustellen erzeugen Benachrichtigungen. Nicht jeder einzelne BTB-Tastendruck soll eine Meldung auslösen.

## 8. Server-Speicheroptionen pro Benutzer
Admin-Auswahl mindestens:
- Bilder dauerhaft speichern
- BTB dauerhaft speichern
- Mängel dauerhaft speichern
- Abnahmen dauerhaft speichern
- Alles dauerhaft speichern

## Android-App-Updates über Server 1.0
- Es gibt **kein separates Update-Hauptmenü**. Die vorhandene Server-1.0-Release-Infrastruktur ist der einzige Updatevertrag.
- Beim App-Start wird die Client-Kompatibilität unaufdringlich geprüft. **Kein Update = kein Symbol** neben `TB-Helfer`.
- Normales Update = **blaues** Aktualisieren-Symbol; als wichtig/Pflicht markiertes Update = **rotes** Symbol.
- Antippen zeigt aktuelle/neue Version, die serverseitigen Release Notes („Was ist neu?“) und die Aktion `Aktualisieren`.
- Unter `Einstellungen → Wartung & Diagnose` bleibt `Nach Updates suchen` als manueller Kontrollweg mit aktueller/neuer Version und Release Notes vorhanden.
- APKs werden vom TB-Server über HTTPS geladen. Bei internem Server-Binary werden SHA-256 und Dateigröße vor der Installation geprüft.
- Android startet danach den System-Paketinstaller. Eine eventuell einmalig erforderliche Android-Freigabe für „Apps aus dieser Quelle installieren“ bleibt eine Systementscheidung.
- Ein normales App-Update darf gespeicherte Serveridentität, Geräte-Token, Aktivierung und lokale Baustellendaten nicht löschen. Ein neuer Geräte-QR ist nur bei Freigabe/Neuinstallation/verlorenen App-Daten nötig.

