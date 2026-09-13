# TB-Helfer – verbindliche Projektregeln

Stand: 2026-09-13

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

## 3. Firmen, Benutzer und Profil-Autofill
- Baustellen-Zusammenarbeit ist strikt auf die eigene Firma begrenzt.
- Nutzer verschiedener Firmen dürfen nicht derselben Baustellen-Teamstruktur zugeordnet werden.
- Ein Benutzer hat standardmäßig genau ein aktives Gerät.
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
- Grünes `+`: aktueller BTB wird zuerst gespeichert, aber nicht abgeschlossen; danach Dialog/Karte `Neuen BTB starten` mit Datum/Zeit, Personal, Geräte/Maschinen und Wetter. Erst `Neuer BTB` erzeugt eine neue `btbId`.
- Neben dem grünen `+` gibt es eine **Bearbeiten-Aktion (Stift)** für den aktuell geladenen BTB. Sie ändert immer denselben BTB/dieselbe `btbId`.
- Der Stift öffnet direkt ein kompaktes schwebendes Fenster `Aktuellen BTB bearbeiten` mit Datum/Zeit, Personal, Geräte/Maschinen und Wetter; kein zusätzliches vorgeschaltetes Listenmenü.
- Nach `Bearbeiten` in diesem Fenster wird direkt der gewählte Datum/Zeit-, Personal-, Geräte/Maschinen- oder Wetter-Editor geöffnet. Die alte Vierer-Auswahl mit Status-Pills darf nicht nochmals als Zwischenschritt eingeblendet werden.
- Wenn noch kein BTB existiert, dürfen ausgeblendete Detailfelder keinen vertikalen Leerraum reservieren; die Standardabschnitte sollen möglichst weit oben beginnen. Bearbeiten- und PDF-Aktionen sind ohne realen BTB nicht sichtbar.
- Der blaue aktuelle-BTB-Balken und `BTB bearbeiten` im Verlauf öffnen immer exakt die gewählte `btbId`.
- Beim Wechsel BTB 1 ↔ BTB 2 muss der aktuelle BTB zuerst tatsächlich gespeichert sein; keine feste Zeitwarte-Lösung als Datenintegrität.
- Verlauf zeigt nur tatsächlich vorhandene BTBs.
- `PDF Entwurf` erzeugt nur eine Vorschau und öffnet sie direkt im App-eigenen PDF-Viewer; der BTB wird dadurch nicht abgeschlossen.
- Ein moderner BTB darf im Verlauf nach ausdrücklicher Sicherheitsabfrage vollständig gelöscht werden. Dabei werden BTB-Datensatz, Original-/Abschlussmetadaten und BTB-Dokumente gelöscht. **Echte Baustellenfotos bleiben erhalten** und werden nur von der gelöschten `btbId` gelöst.

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
- Nur historische Fotos ohne `btbId` dürfen weiterhin über Datum/Uhrzeit als Legacy-Fallback angeboten werden.
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
