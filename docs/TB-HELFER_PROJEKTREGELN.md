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
- Der blaue aktuelle-BTB-Balken und `BTB bearbeiten` im Verlauf öffnen immer exakt die gewählte `btbId`.
- Beim Wechsel BTB 1 ↔ BTB 2 muss der aktuelle BTB zuerst tatsächlich gespeichert sein; keine feste Zeitwarte-Lösung als Datenintegrität.
- Verlauf zeigt nur tatsächlich vorhandene BTBs.

### BTB-Abschluss – verbindliches Ein-PDF-Modell
- Die frühere sichtbare V1/V2/V3-Versionierung ist verworfen.
- **Ein BTB (`btbId`) besitzt höchstens ein aktuell gültiges Abschluss-PDF.**
- Beim ersten `PDF Abschluss` wird aus dem aktuellen Datensatz das PDF erzeugt. Erst wenn die Datei erfolgreich geschrieben wurde, gilt der BTB als `Abgeschlossen`.
- Der Stand dieses erfolgreichen Abschlusses wird intern als **letzter Abschluss-Snapshot** behalten. Er dient nur dem Vergleich bei späterer Nachbearbeitung und ist keine sichtbare Dokumentversion.
- Der Abschluss-Snapshot gehört fachlich zum selben BTB-Datensatz; Implementierungen dürfen für Rückwärtskompatibilität zusätzlich eine interne technische Spiegeldatei halten, aber keine sichtbaren V1/V2/V3 erzeugen.
- Nur das Öffnen/Bearbeiten eines abgeschlossenen BTB macht noch nichts ungültig.
- Sobald nach dem Abschluss tatsächlich eine Änderung gespeichert wird, wechselt der BTB zu `Nachbearbeitung`; das bisherige Abschluss-PDF ist ungültig und wird gelöscht.
- Neue oder geänderte Inhalte gegenüber dem letzten Abschluss-Snapshot werden **rot** dargestellt.
- Gelöschte Inhalte werden im nächsten PDF rot als `(entfernt) ...` dargestellt.
- Unveränderte Inhalte bleiben normal.
- Beim nächsten `PDF Abschluss` wird wieder genau ein aktuelles PDF derselben `btbId` erzeugt und der Abschluss-Snapshot auf diesen neuen Stand gesetzt.
- PDF-Dateiname bleibt für dieselbe fachliche `btbId` stabil/merge-sicher; keine `_V1`, `_V2`, `_V100` und keine `(2)`-Ausweichnamen für fachlich verschiedene BTBs.
- Historische Alt-BTBs mit Tag/Nacht-/Version-Dateien bleiben lesbar; beim nächsten modernen Abschluss werden sie in das neue Ein-PDF-Verhalten überführt.
- `PDF öffnen` im BTB-Verlauf öffnet den App-eigenen PDF-Viewer.
- `ZIP teilen` arbeitet immer mit exakt der ausgewählten `btbId` und ihrem aktuell gültigen PDF/DOCX/Bildbestand.

### BTB-Fotos
- Neue normale BTB-Fotos werden zusätzlich eindeutig der beim Speichern aktiven `btbId` zugeordnet.
- Zwei BTBs mit überlappenden Arbeitszeiten dürfen dadurch nicht dieselben modernen Fotos nur wegen der Uhrzeit übernehmen.
- Datum/Uhrzeit bleiben Anzeige/Sortierung, nicht technische Foto-BTB-Zuordnung.
- Historische noch nicht zugeordnete Fotos dürfen als Legacy-Fallback weiterhin über das Zeitfenster angeboten werden.
- Mängel-/Abnahme-Zuordnungen bleiben als eigene Fachbeziehungen bestehen.
- Allgemeine BTB-Bilder eines Helfers werden nicht automatisch auf alle Helfergeräte verteilt; Server + verantwortlicher TB + Erstellergerät wie beschlossen.

### Merge-sichere ZIP-/Laptop-Struktur
- Baustellen-Gesamtexport besitzt als oberste Struktur `TB-Helfer/<Baustelle>/...`.
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

Die Einstellung bleibt bestehen, bis sie bewusst geändert wird. Nicht dauerhaft gespeicherte Daten dürfen temporär für Sync zwischengespeichert und danach bereinigt werden. Speicheroptionen sind keine Baustellen-Arbeitsfreigabe.

## 9. Offline- und Mehrgeräte-Sync
- Nur benötigte Daten werden offline geladen.
- Lokale Änderungen gehen in eine Upload-Queue.
- Version/Basisversion schützt vor Überschreiben; 409-Konflikte werden sichtbar behandelt.
- Löschungen werden mit Tombstones synchronisiert.
- Hintergrund-Sync zusätzlich zum manuellen Sync.
- Hauptmenü führt `Mit NAS synchronisieren` / `Synchronisieren` statt unklarem `Daten vom NAS`.
- Letzter erfolgreicher Sync wird allgemein und sinnvoll pro Baustelle angezeigt.
- Zeitplan: **Manuell / stündlich / täglich**.
- Netz: **Nur WLAN / nur Mobilfunk / WLAN + Mobilfunk**.
- Manueller Sync darf nicht still blockieren; bei abweichender Netzregel verständlich warnen/bewusste Ausführung ermöglichen.
- Normaler Sync umfasst je nach Berechtigung Baustellenstruktur, Unterbaustellen, BTB, Mängel, Abnahmen, Bild-/Metadaten und Streckendaten.

## 10. Vermessungs-/Kilometrierungsfarben
- Grün: belastbare Original-/Absteckungs-Kilometrierung.
- Orange: manuell hergestellt/geändert.
- Rot: TRA vorhanden, aber keine belastbare Kilometrierung.
- Grau: Objekt ohne Vermessungsgeometrie.
- Blau: temporäre Auswahl/Markierung.

## 11. Arbeitsweise bei Änderungen
- Keine produktive NAS-Migration ohne vorheriges Backup.
- Servermigrationen und neue Android-Versionen erst testen, dann produktiv übernehmen.
- `main` stabil halten; große Änderungen zunächst auf Feature-Branch/Draft-PR.
- Keine Geheimnisse (`.env`, Tunnel-Token, Passwörter, Signing-Key) in GitHub oder Screenshots.
- Vor neuen Grundsatzentscheidungen zuerst diese Projektregeln prüfen.

## 12. GitHub als verbindliche Übergabe zwischen Chats
- Repository: `jensmeier/TB-Helfer-2.0`.
- Vor jeder Änderung zuerst vorhandenen GitHub-Stand prüfen.
- Bei jedem neuen Chat zuerst lesen:
  1. `docs/TB-HELFER_PROJEKTREGELN.md`
  2. `docs/TB-HELFER_AKTUELLER_STAND.md`
  3. offenen Draft-PR inklusive neuestem Commit.
- `docs/TB-HELFER_AKTUELLER_STAND.md` ist die laufende technische Übergabe mit Branch, PR, produktivem NAS-Stand, Änderungen, Tests, Fehlern und nächstem Schritt.
- Nach jeder substanziellen Änderung oder neuen Entscheidung Statusdatei im aktiven Feature-Branch aktualisieren.
- Bei Widersprüchen gilt: produktiver NAS-Stand für tatsächlich laufende Serverdaten; diese Projektregeln für Fachentscheidungen; GitHub-Branch/PR für noch nicht produktive Entwicklung.
- Alte Chat-Zusammenfassungen niemals als alleinige Source of Truth verwenden, wenn GitHub neuer ist.
