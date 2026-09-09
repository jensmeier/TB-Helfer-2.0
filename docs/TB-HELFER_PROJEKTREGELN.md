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

## 4. Baustellen-Teammodell
Eine Baustelle kann mehrere verantwortliche TB-Gruppen haben: A, B, C, D, ... innerhalb derselben Firma.

Beispiel:
- TB A mit Hilfsnutzern 1 und 2
- TB B mit Hilfsnutzern 4 und 5
- TB C mit eigenen Hilfsnutzern usw.

Hilfsnutzer sehen grundsätzlich nur ihren zugewiesenen Arbeitsbereich plus die für sie freigegebenen gemeinsamen Baustellendaten.

## 5. BTB-Regel
- BTB ist NICHT baustellenweit gemeinsam.
- Jeder verantwortliche TB besitzt seinen eigenen BTB.
- A + Nutzer 1 + Nutzer 2 sehen und bearbeiten denselben BTB von A.
- B + seine Hilfsnutzer sehen und bearbeiten den BTB von B.
- Einträge eines Hilfsnutzers werden zum BTB seines verantwortlichen TB synchronisiert.
- Hilfsnutzer derselben TB-Gruppe sehen gegenseitig die BTB-Einträge der Gruppe.
- BTB von A wird nicht automatisch in den BTB von B/C/D übernommen.

## 6. Mängel und Abnahmen
- Mängel und Abnahmen sind baustellenweit zwischen A <-> B <-> C <-> D synchronisiert.
- Auch zugeordnete Hilfsnutzer dürfen die baustellenweiten Mängel/Abnahmen sehen und bearbeiten, jedoch nur innerhalb ihrer Berechtigung/ihres Arbeitsbereichs.
- Bilder zu Mängeln/Abnahmen werden mit synchronisiert.
- Status `abgestellt/erledigt` ist endgültig erledigt; keine zusätzliche verpflichtende Abnahme durch A/B/C/D.
- Änderungen müssen möglichst schnell auf den anderen berechtigten Geräten erscheinen.

## 7. Benachrichtigungen in der App
Für neue synchronisierte Mängel, Abnahmen und relevante BTB-Einträge gibt es pro Gerät eine Einstellung:
- Text + Ton
- nur Ton
- aus

Beispiel Text: `Neuer Mangel von Nutzer X` bzw. `Neue Abnahme von Nutzer X`.

## 8. Server-Speicheroptionen pro Benutzer
Beim Benutzer-Anlegen/-Bearbeiten erhält der Admin Speicher-Häkchen, mindestens:
- Bilder dauerhaft auf Server speichern
- BTB dauerhaft speichern
- Mängel dauerhaft speichern
- Abnahmen dauerhaft speichern
- Alles dauerhaft speichern

Wenn dauerhaftes Speichern deaktiviert ist, darf der Server Daten trotzdem temporär für die Synchronisation zwischenspeichern und nach erfolgreicher Verteilung/Frist bereinigen.

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
