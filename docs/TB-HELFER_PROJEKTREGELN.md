# TB-Helfer – verbindliche Projektregeln

Stand: 2026-09-10

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

## 3. Firmen, Benutzer und Profil-Autofill
- Baustellen-Zusammenarbeit ist strikt auf die eigene Firma begrenzt.
- Nutzer verschiedener Firmen dürfen nicht derselben Baustellen-Teamstruktur zugeordnet werden.
- Ein Benutzer hat standardmäßig genau ein aktives Gerät.
- Admin kann Gerät freigeben; danach ist eine neue Aktivierungskarte/Einmal-Schlüssel nötig.
- Nach erfolgreicher Benutzer-/Geräteaktivierung soll die App vorhandene Stammdaten **vom Server automatisch übernehmen**, statt den Benutzer dieselben Daten nochmals eingeben zu lassen.
- Vom Server kommen mindestens: Benutzername, E-Mail soweit hinterlegt, Firma, Zugriffsstatus und ggf. Ablaufdatum/Zugriffsrestzeit.
- Firmenstammdaten werden zentral am Server gepflegt und für alle Benutzer derselben Firma bereitgestellt: Firmenname, Anschrift, Telefon, E-Mail und Firmenlogo.
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
- In der Oberfläche heißt der Abschnitt **`Verantwortliche TBs`** und listet die echten Namen, z. B. `Jens Zinkel`, `Max Müller`.
- Bei jedem verantwortlichen TB gibt es die Aktion **`Helfer einladen`**. Zusätzlich kann ein bereits beteiligter verantwortlicher TB **`Weiteren TB einladen`** verwenden.
- Optional kann zusätzlich ein verständlicher Bereichsname wie `Gleis 1–4`, `km 20–45` oder `Bereich Ost` angezeigt werden.
- Intern bleiben Teams durch stabile IDs getrennt; sichtbare Namen/Bereichsbezeichnungen dürfen geändert werden, ohne BTB- oder Sync-Zuordnungen zu verändern.

## 5. BTB-Regel, Identität und gemeinsamer Langzeit-Export
- BTB ist NICHT baustellenweit gemeinsam.
- Jeder verantwortliche TB besitzt mit seinen Hilfsnutzern seinen eigenen gemeinsamen BTB-Bereich.
- `Jens Zinkel` + seine Hilfsnutzer sehen und bearbeiten denselben BTB dieses TB-Teams.
- `Max Müller` + seine Hilfsnutzer sehen und bearbeiten ihren eigenen BTB.
- Einträge und Fotos eines Hilfsnutzers werden in den BTB seines verantwortlichen TB synchronisiert; der Helfer erzeugt dadurch **keinen eigenen separaten BTB**.
- Hilfsnutzer desselben TB-Teams sehen gegenseitig die BTB-Einträge dieses Teams.
- BTB-Einträge werden nicht automatisch in die BTBs anderer verantwortlicher TBs derselben Baustelle übernommen.
- Die fachliche BTB-Identität innerhalb einer Baustelle ist **verantwortlicher TB / internes TB-Team + Datum + Schicht**.
- Der Baustellenabschnitt bzw. Arbeitsbereich gehört **nicht** zur BTB-Identität. Im BTB selbst wird dokumentiert, an welchen Gleisen, Weichen, Kilometern oder Abschnitten in dieser Schicht gearbeitet wurde.
- Für dieselbe Kombination aus Baustelle, TB-Team, Datum und Schicht existiert genau **ein** gemeinsamer BTB. Neue Helfereinträge, Zeiten und Fotos werden diesem vorhandenen BTB hinzugefügt.
- Es gibt keine künstlichen `-2/-3/-4`-BTBs für denselben verantwortlichen TB, denselben Tag und dieselbe Schicht.
- Beispiel: Jens Zinkel arbeitet mit 30 Helfern tagsüber auf einer sehr großen Baustelle. Alle Arbeiten der 30 Helfer – auch wenn sie an mehreren Stellen der Baustelle erfolgen – landen im gemeinsamen TAG-BTB von Jens für diesen Tag. Max Müller arbeitet mit 30 Helfern nachts und hat für denselben Kalendertag seinen eigenen NACHT-BTB.
- Bei 30 Arbeitstagen entstehen in diesem Beispiel 30 TAG-BTBs von Jens und 30 NACHT-BTBs von Max, nicht für jeden Helfer ein eigener Bericht.
- Sichtbare App-Bezeichnung z. B. `BTB Jens Zinkel · 05.06.2026 · Tag` bzw. `BTB Max Müller · 05.06.2026 · Nacht`.
- PDF **und DOCX** verwenden dieselbe BTB-Identität und denselben Basis-Dateinamen. Beispiel: `BTB_2026-06-05_TAG_Jens_Zinkel.pdf` und `BTB_2026-06-05_TAG_Jens_Zinkel.docx`; für Max entsprechend `BTB_2026-06-05_NACHT_Max_Mueller.*`.
- Der Dokumentkopf enthält mindestens Firma, Baustelle, verantwortlichen TB, Datum und Schicht. Arbeitsbereiche/Abschnitte ergeben sich aus den fachlichen Einträgen im Bericht und können zusätzlich im Inhalt zusammengefasst werden.
- Das zentrale Firmenlogo kann automatisch in PDF/DOCX verwendet werden.
- Die fachliche Zuordnung erfolgt intern immer über stabile IDs; der menschenlesbare BTB-/Dateiname ist nur Anzeige/Exportname und nicht der technische Primärschlüssel.
- Bereits vorhandene historische BTBs müssen nicht nachträglich umbenannt werden; die neue Regel gilt für neu erzeugte Berichte.

### Merge-sichere ZIP-/Laptop-Struktur
- Jeder Baustellen-Gesamtexport der App besitzt als oberste Struktur `TB-Helfer/<Baustelle>/...`.
- Auf dem Laptop kann dauerhaft ein gemeinsamer Hauptordner `TB-Helfer` geführt werden. ZIP-Exporte verschiedener Benutzer und verschiedener Baustellen werden in diesen Hauptordner entpackt und ergänzen die vorhandene Struktur.
- Beispiel: Ein Export für Pasing ergänzt `TB-Helfer/Pasing/...`; ein späterer Export für Gessertshausen ergänzt `TB-Helfer/Gessertshausen/...`.
- Exporte verschiedener verantwortlicher TBs derselben Baustelle müssen **zusammenführbar** sein. Jens' TAG-Berichte und Max' NACHT-Berichte landen im selben Baustellenordner, ohne sich gegenseitig zu überschreiben.
- Derselbe fachliche Datensatz muss bei wiederholtem Export immer denselben stabilen Zielpfad erhalten. Wird derselbe BTB später erneut exportiert, aktualisiert/ersetzt er denselben logischen Bericht statt eine zweite Kopie mit Zufallsnamen anzulegen.
- Unterschiedliche fachliche Datensätze dürfen niemals denselben Zielpfad erhalten. Für BTB wird dies über Datum + Schicht + verantwortlichen TB bzw. die intern stabile Team-Zuordnung sichergestellt.
- Nicht nur BTB, sondern auch Bilder, Mängel-, Abnahme- und sonstige Exportdateien müssen merge-sichere, stabile Dateinamen erhalten. Kamera-Standardnamen wie `IMG_1234.jpg` allein sind **nicht** ausreichend; Medien erhalten eine stabile eindeutige Kennung/ID im Exportpfad.
- Wenn zwei verschiedene Benutzer/Firmenmitglieder zufällig denselben sichtbaren Namen haben, darf intern trotzdem kein Dateikonflikt entstehen. Dafür bleibt eine stabile nicht geheime interne Benutzer-/Teamkennung maßgeblich; eine kurze Kennung darf bei tatsächlicher Namenskollision in den Exportnamen aufgenommen werden.
- Ziel ist: Viele Exporte über Monate/Jahre können in einen einzigen `TB-Helfer`-Archivordner zusammengeführt werden, ohne fachlich verschiedene Dateien zu verlieren oder versehentlich zu überschreiben.

## 6. Mängel und Abnahmen
- Mängel und Abnahmen sind **nur innerhalb der konkreten Baustelle** gemeinsam.
- Sie werden ausschließlich an Benutzer synchronisiert, die dieser Baustelle über eine gültige Baustellen-/Team-Einladung beigetreten sind oder die Baustelle selbst angelegt haben.
- Andere Benutzer derselben Firma und erst recht Benutzer anderer Firmen erhalten diese Mängel/Abnahmen nicht.
- Verantwortliche TBs dieser Baustelle sehen die baustellenweiten Mängel/Abnahmen.
- Zugeordnete Hilfsnutzer sehen und bearbeiten Mängel/Abnahmen nur innerhalb ihrer Berechtigung bzw. ihres freigegebenen Arbeitsbereichs.
- Bilder zu Mängeln/Abnahmen werden mit synchronisiert, ebenfalls nur an berechtigte Mitglieder dieser Baustelle.
- Status `abgestellt/erledigt` ist endgültig erledigt; keine zusätzliche verpflichtende Abnahme durch andere verantwortliche TBs.
- Änderungen müssen möglichst schnell auf den anderen berechtigten Geräten derselben Baustelle erscheinen.

## 7. Benachrichtigungen in der App
Für neue synchronisierte Mängel, Abnahmen und relevante BTB-Einträge gibt es pro Gerät eine Einstellung:
- Text + Ton
- nur Ton
- aus

Benachrichtigungen werden nur für Daten aus Baustellen erzeugt, denen der Benutzer tatsächlich beigetreten ist und für die er berechtigt ist.

Beispiel Text: `Neuer Mangel von Nutzer X` bzw. `Neue Abnahme von Nutzer X`.

## 8. Server-Speicheroptionen pro Benutzer
Beim Benutzer-Anlegen/-Bearbeiten erhält der Admin Speicher-Häkchen, mindestens:
- Bilder dauerhaft auf Server speichern
- BTB dauerhaft speichern
- Mängel dauerhaft speichern
- Abnahmen dauerhaft speichern
- Alles dauerhaft speichern

Wenn dauerhaftes Speichern deaktiviert ist, darf der Server Daten trotzdem temporär für die Synchronisation zwischenspeichern und nach erfolgreicher Verteilung/Frist bereinigen.

Die Speicheroptionen sind **keine Baustellen-Arbeitsfreigabe**. Mitarbeiter dürfen für ihre berechtigten Baustellen arbeiten und synchronisieren, ohne dass der Admin jede Baustelle manuell freischalten muss.

## 9. Offline- und Mehrgeräte-Sync
- Nur benötigte Strecken-/Bahnhofs-/Abschnittsdaten werden auf das Gerät geladen.
- Lokale Änderungen gehen in eine Upload-Queue.
- Versionen/Basisversion schützen vor Überschreiben.
- 409-Konflikte werden sichtbar behandelt.
- Löschungen werden mit Tombstones synchronisiert.
- Hintergrund-Sync zusätzlich zum manuellen Sync, unter Android ressourcenschonend.

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
