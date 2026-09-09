# TB-Helfer – verbindliche Projektregeln

Stand: 2026-09-09

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

## 3. Firmen und Benutzer
- Baustellen-Zusammenarbeit ist strikt auf die eigene Firma begrenzt.
- Nutzer verschiedener Firmen dürfen nicht derselben Baustellen-Teamstruktur zugeordnet werden.
- Normaler Nutzer: Name Pflicht; E-Mail und Firma optional soweit bestehende Serverlogik dies erlaubt.
- Ein Benutzer hat standardmäßig genau ein aktives Gerät.
- Admin kann Gerät freigeben; danach ist eine neue Aktivierungskarte/Einmal-Schlüssel nötig.

## 4. Baustellen-Teammodell und Einladungen
- Der Admin verwaltet Benutzer, Firmen, Geräte und Server-Speicheroptionen, aber **nicht** die laufende Zuordnung der Mitarbeiter zu einzelnen Baustellen.
- Eine Baustelle wird in der App durch einen verantwortlichen TB angelegt bzw. mit dem Server verknüpft. Dieser Benutzer ist der erste verantwortliche TB dieser Baustelle.
- Weitere verantwortliche TBs treten der konkreten Baustelle über eine vom bereits beteiligten TB erzeugte Einladung bei (QR-Code/Link/Code). Der Server prüft dabei automatisch aktive Benutzer/Geräte und dieselbe Firma.
- Hilfsnutzer werden ebenfalls per Einladung aufgenommen, jedoch gezielt in das Team ihres verantwortlichen TB und mit ihrem freigegebenen Arbeitsbereich.
- Ein Benutzer derselben Firma, der **keine Einladung zu dieser Baustelle angenommen hat**, erhält keine Baustellen-Zusammenarbeitsdaten dieser Baustelle.
- QR-/Link-Einladungen enthalten keine Benutzer-Keys oder Passwörter, sondern kurzlebige bzw. widerrufbare Einladungstokens.
- Die bisherige Bezeichnung A/B/C/D ist nur ein technisches Denkmodell und soll **nicht als sichtbare Benutzerbezeichnung** verwendet werden.
- In der Oberfläche wird das Team über den verantwortlichen TB dargestellt, z. B. `TB Jens Zinkel`, `TB Max Müller`; optional kann zusätzlich ein verständlicher Bereichsname wie `Gleis 1–4` oder `Bereich Ost` angezeigt werden.
- Intern bleiben Teams durch stabile IDs getrennt; die sichtbare Bezeichnung darf geändert werden, ohne BTB- oder Sync-Zuordnungen zu verändern.

## 5. BTB-Regel
- BTB ist NICHT baustellenweit gemeinsam.
- Jeder verantwortliche TB besitzt mit seinen Hilfsnutzern einen eigenen gemeinsamen BTB-Bereich.
- `TB Jens Zinkel` + seine Hilfsnutzer sehen und bearbeiten denselben BTB dieses TB-Teams.
- `TB Max Müller` + seine Hilfsnutzer sehen und bearbeiten ihren eigenen BTB.
- Einträge eines Hilfsnutzers werden zum BTB seines verantwortlichen TB synchronisiert.
- Hilfsnutzer desselben TB-Teams sehen gegenseitig die BTB-Einträge dieses Teams.
- BTB-Einträge werden nicht automatisch in die BTBs anderer verantwortlicher TBs derselben Baustelle übernommen.

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
