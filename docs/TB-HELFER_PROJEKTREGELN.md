# TB-Helfer – verbindliche Projektregeln

Stand: 2026-09-11

Diese Datei hält die fachlichen Entscheidungen für Server, Android-App und später iOS fest. Bei Änderungen zuerst diese Datei aktualisieren.

## 1. Grundarchitektur
- Synology/NAS ist zentrale Serverinstanz und Source of Truth.
- Android arbeitet offline mit lokalen Kopien und synchronisiert nur benötigte Daten.
- Originale Vermessungsdateien (z. B. TRA, GRA, KF, KOR) bleiben unverändert und dauerhaft nachvollziehbar.
- Bearbeitete/berechnete Abschnitte werden versioniert gespeichert.
- Cloudflare/TLS für normale App-Synchronisation; Tailscale nur Wartung/Notfall.

## 2. Strecken/Bahnhöfe
- Strecke: Streckennummer Pflicht, KM-Bereich nicht grundsätzlich Pflicht.
- Bahnhofseingabe muss Autovervollständigung nutzen.
- Keine neue externe Bahnhofsliste erfinden: vorhandene App-Daten verwenden.
- Vorhandene App-Assets:
  - `app/src/main/assets/TB-Helfer_DB_Bahnhoefe_aktiv_2026-03-14.json`
  - `app/src/main/assets/TB-Helfer_DB_Betriebsstellen_aktuell_2026-03-14.json`
- Vorhandene Suchlogik beachten: `app/src/main/java/de/tbhelfer/util/BahnhofSucheUtil.kt`.
- Server-Admin soll dieselbe fachliche Datenbasis für Autofill verwenden.
- Originale Vermessungs-/Streckendateien auf dem NAS werden durch mobile Bearbeitung niemals überschrieben.
- Berechnete oder manuell bearbeitete Streckenabschnitte werden getrennt von den Originaldaten versioniert synchronisiert.
- Die App lädt nur die für die jeweilige Baustelle/Berechtigung benötigten Strecken-, Gleis-, Weichen- und Abschnittsdaten.
- Im Streckendaten-Bereich gibt es eine gezielte Aktion `Änderungen mit NAS synchronisieren`; dabei werden nur geänderte/berechnete Abschnitte übertragen.

## 3. Firmen, Benutzer und Profil-Autofill
- Baustellen-Zusammenarbeit ist strikt auf die eigene Firma begrenzt.
- Nutzer verschiedener Firmen dürfen nicht derselben Baustellen-Teamstruktur zugeordnet werden.
- Ein Benutzer hat standardmäßig genau ein aktives Gerät.
- Admin kann Gerät freigeben; danach ist eine neue Aktivierungskarte/Einmal-Schlüssel nötig.
- Nach erfolgreicher Benutzer-/Geräteaktivierung soll die App vorhandene Stammdaten **vom Server automatisch übernehmen**, statt den Benutzer dieselben Daten nochmals eingeben zu lassen.
- Firmenstammdaten werden zentral am Server gepflegt und für alle Benutzer derselben Firma bereitgestellt: Firmenname, Anschrift, Firmentelefon, Firmen-E-Mail und Firmenlogo.
- Jeder Benutzer besitzt zusätzlich ein eigenes Benutzerprofil mit persönlicher Telefonnummer und persönlicher E-Mail-Adresse.
- Persönliche Telefonnummer und persönliche E-Mail dürfen für **alle Benutzer derselben Firma** sichtbar sein; Benutzer anderer Firmen erhalten sie nicht.
- Firmen-Telefon/-E-Mail und persönliche Telefon-/E-Mail sind fachlich getrennte Daten und dürfen nicht gegenseitig überschrieben werden.
- Das Firmenlogo wird einmal serverseitig hinterlegt und von den Geräten derselben Firma automatisch geladen/gecached; nicht jeder Benutzer wählt sein eigenes Firmenlogo.
- Die App darf für Supportzwecke eine **nicht geheime Support-/Gerätekennung** und einen nur maskierten Schlüsselhinweis anzeigen (z. B. letzte 4 Zeichen). Der vollständige Aktivierungs-/Geräteschlüssel wird in der normalen Profiloberfläche nicht offen angezeigt.
- Ein Baustellen-Einladungs-QR verändert die Benutzer-/Firmenstammdaten nicht; er fügt nur die berechtigte Baustellen-/Team-Mitgliedschaft hinzu.

## 4. Baustellen-Teammodell und Einladungen
- Der Admin verwaltet Benutzer, Firmen, Geräte und Server-Speicheroptionen, aber **nicht** die laufende Zuordnung der Mitarbeiter zu einzelnen Baustellen.
- Eine Baustelle wird in der App durch einen verantwortlichen TB angelegt bzw. mit dem Server verknüpft. Dieser Benutzer ist der erste verantwortliche TB dieser Baustelle.
- Weitere verantwortliche TBs treten der konkreten Baustelle über eine vom bereits beteiligten TB erzeugte Einladung bei (QR-Code/Link/Code). Der Server prüft dabei automatisch aktive Benutzer/Geräte und dieselbe Firma.
- Hilfsnutzer werden ebenfalls per Einladung aufgenommen, jedoch gezielt in das Team ihres verantwortlichen TB und mit ihrem freigegebenen Arbeitsbereich.
- Ein Benutzer derselben Firma, der **keine Einladung zu dieser Baustelle angenommen hat**, erhält keine Baustellen-Zusammenarbeitsdaten dieser Baustelle.
- QR-/Link-Einladungen enthalten keine Benutzer-Keys oder Passwörter, sondern kurzlebige bzw. widerrufbare Einladungstokens.
- Die bisherige Bezeichnung A/B/C/D ist nur ein technisches Denkmodell und soll **nicht als sichtbare Benutzerbezeichnung** verwendet werden.
- Baustellen-Team/QR gehört direkt in die normale Baustellenansicht; ein eigenes normales Einstellungs-Menü `Baustellen-Synchronisation` ist nicht vorgesehen.
- In der Baustellenansicht gibt es `QR scannen`. `Nutzer hinzufügen` bietet direkt `Nutzer / verantwortlicher TB` oder `Helfer` und erzeugt danach den passenden QR/Link/Code.
- Die Rolle ist Bestandteil der serverseitigen Einladung; der Empfänger wählt nach dem Scan die Rolle nicht erneut.
- Nach erfolgreichem Beitritt wird die Baustelle lokal angelegt/verbunden. Eine bereits aktive andere Baustelle wird **nicht ungefragt ersetzt**. Die App fragt z. B. `Immenstadt wurde hinzugefügt. Jetzt öffnen?` mit `Öffnen` oder `Später`.
- Verantwortliche TBs erhalten die vollständige gemeinsame Baustellenstruktur einschließlich Unterbaustellen und benötigter Streckendaten.
- Helfer erhalten Baustelle, Unterbaustellen und Streckendaten nur innerhalb ihres freigegebenen Arbeitsbereichs.
- Auf einer Baustellenkarte werden mindestens Teamumfang und Sync-Stand sichtbar, z. B. `2 TB · 4 Helfer` und `Zuletzt synchronisiert: heute 14:32`; ohne Serverstand entsprechend `Noch nicht mit Server synchronisiert`.
- Ein Nutzer/Helfer kann serverseitig aus einer Baustelle entfernt werden; historische Beiträge bleiben erhalten. Bereits lokal heruntergeladene Baustellendaten bleiben danach **nur noch lesbar**, dürfen aber nicht mehr bearbeitet oder weiter synchronisiert werden.
- Ein Nutzer/Helfer darf eine Baustelle auch selbst verlassen. Historische Serverdaten bleiben erhalten; lokal vorhandene Daten bleiben danach ebenfalls nur lesbar.
- Intern bleiben Teams durch stabile IDs getrennt; sichtbare Namen/Bereichsbezeichnungen dürfen geändert werden, ohne BTB- oder Sync-Zuordnungen zu verändern.

## 5. BTB-Regel, Identität, Abschluss und Langzeit-Export
- BTB ist NICHT baustellenweit gemeinsam.
- Jeder verantwortliche TB besitzt mit seinen Hilfsnutzern seinen eigenen gemeinsamen BTB-Bereich.
- `Jens Zinkel` + seine Hilfsnutzer sehen und bearbeiten denselben BTB dieses TB-Teams.
- `Max Müller` + seine Hilfsnutzer sehen und bearbeiten ihren eigenen BTB.
- Einträge und BTB-Fotos eines Hilfsnutzers werden in den BTB seines verantwortlichen TB synchronisiert; der Helfer erzeugt dadurch **keinen eigenen separaten Team-BTB**.
- BTB-Einträge werden nicht automatisch in die BTBs anderer verantwortlicher TBs derselben Baustelle übernommen.
- Ein BTB wird **nur bewusst über `Neuer BTB`** angelegt. Das bloße Öffnen des Bautagebuchs erzeugt keinen Entwurf und keine leeren Tag-/Nacht-Platzhalter.
- Jeder bewusst angelegte BTB erhält eine **stabile eigene BTB-ID**. Diese ID ist der technische Primärschlüssel des fachlichen Berichts innerhalb des TB-Teams.
- Datum, Arbeitsbeginn, Arbeitsende und eine daraus ggf. abgeleitete sichtbare Tag-/Nacht-Bezeichnung sind Inhalte/Attribute des BTB und **nicht** mehr seine technische Identität.
- Mehrere BTBs desselben TB-Teams am selben Kalendertag sind ausdrücklich erlaubt, z. B. `07:00–12:00` und `18:00–22:00`.
- Ein BTB darf über Mitternacht laufen, z. B. `10.09.2026 22:00 – 11.09.2026 06:00`, ohne dass zusätzlich ein leerer Tag-BTB erzeugt wird.
- Der vorhandene grüne `+`-Ablauf `Neuen BTB starten` bleibt der zentrale Einstieg. Vorschläge für Datum/Zeit, Personal, Geräte/Maschinen und Wetter dürfen übernommen/bearbeitet werden; erst `✓ Neuer BTB` legt den Datensatz wirklich an.
- Die Verlaufsliste zeigt nur tatsächlich angelegte BTBs; künstliche `Tag – Neu (leer)` / `Nacht – Neu (leer)`-Einträge entfallen.
- Helfer und verantwortlicher TB desselben Teams bearbeiten dieselbe BTB-ID. Paralleländerungen desselben Berichts werden auf Basis der stabilen BTB-ID zusammengeführt bzw. versionsgeschützt behandelt.
- Allgemeine BTB-Bilder eines Teams werden nicht automatisch auf alle Helfergeräte verteilt. Ein vom Helfer aufgenommenes BTB-Bild bleibt auf dessen Gerät, wird zum Server übertragen und dem verantwortlichen TB bereitgestellt.

### BTB-Abschluss und PDF/DOCX-Versionen
- Beim ersten `PDF Abschluss` entsteht Dokumentversion **V1** und der BTB gilt als abgeschlossen.
- Ein abgeschlossener BTB darf später erneut geöffnet und geändert werden; der Abschluss bleibt historisch nachvollziehbar.
- Beim erneuten Abschluss entsteht **V2, V3, ...**. Frühere PDF-/DOCX-Versionen bleiben erhalten und werden nicht überschrieben.
- Das jeweils neue Dokument markiert Inhalte, die gegenüber der vorherigen abgeschlossenen Version geändert wurden, **rot**.
- Der Dokumentkopf zeigt mindestens Dokumentversion, Änderungs-/Abschlusszeitpunkt und den Benutzer, der die neue Version abgeschlossen/geändert hat, zusätzlich zu Firma, Baustelle, verantwortlichem TB und Arbeitszeitraum.
- Nach erfolgreichem Abschluss wird der laufende Eingabebildschirm geschlossen/geleert; beim späteren Öffnen eines abgeschlossenen und danach geänderten BTB muss der Änderungszustand deutlich sichtbar sein.
- PDF **und DOCX** verwenden dieselbe stabile BTB-ID/Versionierung und merge-sichere Dateinamen. Bei mehreren BTBs desselben Tages dürfen sich Dateien nicht überschreiben.
- Historische Alt-BTBs mit dem bisherigen Tag-/Nacht-Speichermodell bleiben lesbar und müssen nicht rückwirkend umbenannt werden.

### Merge-sichere ZIP-/Laptop-Struktur
- Jeder Baustellen-Gesamtexport der App besitzt als oberste Struktur `TB-Helfer/<Baustelle>/...`.
- Auf dem Laptop kann dauerhaft ein gemeinsamer Hauptordner `TB-Helfer` geführt werden. ZIP-Exporte verschiedener Benutzer und verschiedener Baustellen werden in diesen Hauptordner entpackt und ergänzen die vorhandene Struktur.
- Exporte verschiedener verantwortlicher TBs derselben Baustelle müssen zusammenführbar sein, ohne sich gegenseitig zu überschreiben.
- Derselbe fachliche BTB + dieselbe Dokumentversion erhält bei wiederholtem Export immer denselben stabilen Zielpfad.
- Unterschiedliche BTB-IDs oder Dokumentversionen dürfen niemals denselben Zielpfad erhalten.
- Nicht nur BTB, sondern auch Bilder, Mängel-, Abnahme- und sonstige Exportdateien müssen merge-sichere, stabile Dateinamen erhalten. Kamera-Standardnamen wie `IMG_1234.jpg` allein sind nicht ausreichend; Medien erhalten eine stabile eindeutige Kennung/ID im Exportpfad.
- Wenn zwei verschiedene Benutzer/Firmenmitglieder zufällig denselben sichtbaren Namen haben, darf intern trotzdem kein Dateikonflikt entstehen. Dafür bleibt eine stabile nicht geheime interne Benutzer-/Teamkennung maßgeblich.
- Ziel ist: Viele Exporte über Monate/Jahre können in einen einzigen `TB-Helfer`-Archivordner zusammengeführt werden, ohne fachlich verschiedene Dateien zu verlieren oder versehentlich zu überschreiben.

## 6. Mängel, Abnahmen und Bilder
- Mängel und Abnahmen sind **nur innerhalb der konkreten Baustelle** gemeinsam.
- Sie werden ausschließlich an Benutzer synchronisiert, die dieser Baustelle über eine gültige Baustellen-/Team-Mitgliedschaft angehören.
- Andere Benutzer derselben Firma und erst recht Benutzer anderer Firmen erhalten diese Mängel/Abnahmen nicht.
- Verantwortliche TBs derselben Baustelle sehen die baustellenweiten Mängel/Abnahmen sowie die dazugehörigen Bilder.
- Zugeordnete Hilfsnutzer sehen und bearbeiten Mängel/Abnahmen nur innerhalb ihrer Berechtigung bzw. ihres freigegebenen Arbeitsbereichs.
- Bilder zu Mängeln/Abnahmen werden innerhalb derselben Berechtigungsregeln mit synchronisiert.
- Der vorhandene Mangelstatus `ERLEDIGT` ist endgültig und darf durch einen älteren Sync nicht wieder geöffnet werden. Keine neue Statusbezeichnung erfinden, wenn das bestehende Fachmodell sie nicht verwendet.
- Endgültige Abnahmestatus bleiben ebenfalls endgültig.
- Änderungen müssen möglichst schnell auf den anderen berechtigten Geräten derselben Baustelle erscheinen.

## 7. Benachrichtigungen in der App
Für neue synchronisierte Mängel, Abnahmen und relevante BTB-Einträge gibt es pro Gerät eine Einstellung:
- Text + Ton
- nur Ton
- aus

Benachrichtigungen werden nur für Daten aus Baustellen erzeugt, denen der Benutzer tatsächlich beigetreten ist und für die er berechtigt ist.
Wenn die Menge stört, muss der Benutzer Benachrichtigungen abschalten können; nicht jeder einzelne BTB-Tastendruck soll eine Meldung auslösen.

Beispiel Text: `Neuer Mangel von Nutzer X` bzw. `Neue Abnahme von Nutzer X`.

## 8. Server-Speicheroptionen pro Benutzer
Beim Benutzer-Anlegen/-Bearbeiten erhält der Admin Speicher-Häkchen, mindestens:
- Bilder dauerhaft auf Server speichern
- BTB dauerhaft speichern
- Mängel dauerhaft speichern
- Abnahmen dauerhaft speichern
- Alles dauerhaft speichern

Die gewählte Einstellung bleibt bestehen, bis sie bewusst wieder geändert wird.
Wenn dauerhaftes Speichern deaktiviert ist, darf der Server Daten trotzdem temporär für die Synchronisation zwischenspeichern und nach erfolgreicher Verteilung/Frist bereinigen.
Die Speicheroptionen sind **keine Baustellen-Arbeitsfreigabe**.

## 9. Offline- und Mehrgeräte-Sync
- Nur benötigte Strecken-/Bahnhofs-/Abschnittsdaten werden auf das Gerät geladen.
- Lokale Änderungen gehen in eine Upload-Queue.
- Versionen/Basisversion schützen vor Überschreiben.
- 409-Konflikte werden sichtbar behandelt.
- Löschungen werden mit Tombstones synchronisiert.
- Hintergrund-Sync zusätzlich zum manuellen Sync, unter Android ressourcenschonend.
- Der normale Hauptmenüpunkt `Daten vom NAS` wird als verständliche Synchronisationsaktion geführt, z. B. `Mit NAS synchronisieren` / `Synchronisieren`.
- Der letzte erfolgreiche Sync-Zeitpunkt wird sichtbar angezeigt, sowohl allgemein als auch sinnvoll pro Baustelle.
- Zeitplan-Einstellung: **Manuell / stündlich / täglich**.
- Netz-Einstellung: **Nur WLAN / nur Mobilfunk / WLAN + Mobilfunk**.
- Ein bewusst ausgelöster manueller Sync darf den Benutzer nicht still blockieren; wenn die gewählte Netzregel nicht zur aktuellen Verbindung passt, soll die App verständlich warnen bzw. eine bewusste Ausführung ermöglichen.
- Normaler Baustellen-Sync umfasst je nach Berechtigung Baustellenstruktur, Unterbaustellen, BTB, Mängel, Abnahmen, benötigte Bild-/Metadaten und Streckendaten.

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
- Vor neuen Grundsatzentscheidungen zuerst diese Projektregeln prüfen, damit bereits festgelegte Punkte nicht erneut abgefragt werden.

## 12. GitHub als verbindliche Übergabe zwischen Chats
- Repository: `jensmeier/TB-Helfer-2.0`.
- Vor jeder Änderung muss zuerst der vorhandene GitHub-Stand geprüft werden.
- Bei jedem neuen Chat zuerst lesen:
  1. `docs/TB-HELFER_PROJEKTREGELN.md`
  2. `docs/TB-HELFER_AKTUELLER_STAND.md`
  3. den aktuell offenen Draft-PR inklusive neuestem Commit.
- `docs/TB-HELFER_AKTUELLER_STAND.md` ist die laufende technische Übergabe und enthält aktuellen Branch, PR, produktiven NAS-Stand, letzte Änderungen, offene Punkte und den nächsten sicheren Schritt.
- Nach jeder substanziellen Änderung oder neuen verbindlichen Entscheidung muss diese Statusdatei im aktiven Feature-Branch aktualisiert werden.
- Bei Widersprüchen gilt: produktiver NAS-Stand für tatsächlich laufende Serverdaten/Quellstand; diese Projektregeln für fachliche Entscheidungen; GitHub-Branch/PR für noch nicht produktive Entwicklung.
- Keine alten Chat-Zusammenfassungen als alleinige Source of Truth verwenden, wenn GitHub einen neueren Stand enthält.
