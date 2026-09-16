# TB-HELFER – VERBINDLICHE PROJEKTANWEISUNG

GitHub-Repository:
`jensmeier/TB-Helfer-2.0`

==================================================
## 1. SOURCE OF TRUTH / START JEDES CHATS
==================================================

Vor jeder Änderung und bei jedem neuen Chat zuerst den aktuellen GitHub-Stand prüfen.

Mindestens prüfen/lesen:

1. `docs/TB-HELFER_PROJEKTREGELN.md`
2. `docs/TB-HELFER_AKTUELLER_STAND.md`
3. aktuell relevanten Feature-Branch
4. aktuell offenen Draft-PR
5. neuesten relevanten Commit

GitHub und die dort versionierten Projekt-MDs sind maßgeblich.

Priorität:

1. aktueller GitHub-Code / aktiver Branch
2. `docs/TB-HELFER_PROJEKTREGELN.md`
3. `docs/TB-HELFER_AKTUELLER_STAND.md`
4. aktuelle Projektdateien
5. Chat-Übergabe-MD
6. alte Chat-Zusammenfassungen / Erinnerungen

Wenn GitHub neuer ist, gilt GitHub.

Alte Chat-Zusammenfassungen oder Erinnerungen niemals als alleinige Source of Truth verwenden.

Bereits dokumentierte Entscheidungen nicht erneut erfinden, unnötig ändern oder erneut abfragen.

==================================================
## 2. PROJEKT-MD
==================================================

Die vollständigen fachlichen und technischen Projektregeln stehen in:

`docs/TB-HELFER_PROJEKTREGELN.md`

Der aktuelle Entwicklungsstand steht in:

`docs/TB-HELFER_AKTUELLER_STAND.md`

Nach jeder wesentlichen Änderung muss
`docs/TB-HELFER_AKTUELLER_STAND.md`
im aktiven Feature-Branch aktualisiert werden.

Dort mindestens festhalten:

- aktueller Stand
- umgesetzte Änderungen
- betroffene Dateien
- wichtige technische Entscheidungen
- bekannte Fehler
- offene Punkte
- Tests und Ergebnisse
- nächster sinnvoller Arbeitsschritt

Die Projekt-MDs sollen zusammen mit den dazugehörigen Codeänderungen versioniert werden.

Wenn eine MD beschädigt, unvollständig, widersprüchlich oder offensichtlich veraltet ist:

**NICHT raten.**

Stattdessen GitHub-Code, Commit-Historie, Branches, PRs und frühere Versionen der MD prüfen.

GitHub muss so dokumentiert sein, dass der wesentliche Projektstand auch ohne alten Chat wiederhergestellt werden kann.

==================================================
## 3. SERVER / DATEN
==================================================

Synology/NAS ist Source of Truth für produktive Projektdaten.

Originale Vermessungsdaten bleiben unverändert.

Nur tatsächlich benötigte Daten werden für Offline-Arbeit auf das Handy übertragen.

Cloudflare:
normale App-Synchronisation.

Tailscale:
nur Wartung und Administration.

Produktive NAS-Migrationen oder größere Datenänderungen nur nach vorherigem Backup.

Die genauen Server-, Sync-, Benutzer-, Team-, BTB-, Mängel-, Abnahme- und Datenregeln stehen in:
`docs/TB-HELFER_PROJEKTREGELN.md`

==================================================
## 4. SICHERHEIT
==================================================

Keine Geheimnisse in GitHub, Commits, Dokumentation, Logs, Screenshots oder Chat-Übergaben speichern.

Dazu gehören insbesondere:

- Passwörter
- `.env`-Dateien bzw. deren Geheimwerte
- API-Schlüssel
- Cloudflare-Tokens
- Tunnel-Tokens
- Tailscale-Schlüssel
- Signing-Keys
- private Zertifikate
- sonstige Zugangsdaten

Wenn solche Daten in einer Datei gefunden werden, nicht ungeprüft wiedergeben oder nach GitHub übernehmen.

==================================================
## 5. GITHUB / ENTWICKLUNG
==================================================

`main` stabil halten.

Größere Änderungen zuerst über:
**Feature-Branch + Draft-PR.**

Vor neuen Lösungen zuerst prüfen, was bereits vorhanden ist.

Bestehende funktionierende Komponenten bevorzugt erweitern.

Keine parallelen Ersatzlösungen bauen, wenn bereits eine Projektlösung vorhanden ist.

Vor Änderungen:
1. Bestand prüfen
2. vorhandenen Code verstehen
3. dokumentierte Entscheidungen prüfen
4. Änderung planen
5. umsetzen
6. testen
7. dokumentieren

==================================================
## 6. CODIERUNG / AUFTRÄGE
==================================================

Wenn der Benutzer einen klaren Programmier- oder Änderungsauftrag erteilt, diesen möglichst vollständig bearbeiten.

Nicht nach jedem Teilschritt anhalten und nicht unnötig auf ein weiteres „OK“ warten.

Analyse, Codeänderungen, Build, Tests, Fehlerbehebung und Dokumentation gehören zum selben Auftrag, sofern sie für die Aufgabe erforderlich sind.

Nicht nach einem Zwischenstand schreiben:

„Bis hierhin fertig – soll ich weitermachen?“

wenn der ursprüngliche Auftrag bereits die Weiterarbeit umfasst.

Selbstständig weiterarbeiten, bis:

- der gesamte beauftragte Umfang erledigt ist,
- eine echte Entscheidung des Benutzers erforderlich ist,
- notwendige Informationen oder Berechtigungen fehlen,
- eine produktive/riskante Aktion ausdrücklich freigegeben werden muss,
- oder eine technische Begrenzung die Weiterarbeit verhindert.

Nur bei echten Unklarheiten nachfragen.

Am Ende eines Auftrags klar zusammenfassen:

- was geändert wurde
- was getestet wurde
- was funktioniert
- welche Fehler behoben wurden
- was noch offen ist
- welcher nächste Schritt sinnvoll ist

==================================================
## 7. CHAT WIRD ZU VOLL
==================================================

Bei sehr langen Entwicklungs-Chats auf drohenden Kontextverlust achten.

Da kein garantiert exakter Kontext-Prozentwert verfügbar ist, bei erkennbar sehr hohem Verbrauch rechtzeitig reagieren.

Ziel:
ungefähr bei 95–98 %, soweit einschätzbar.

Dann deutlich melden:

**„⚠️ Dieser TB-Helfer-Chat wird sehr voll. Bitte einen neuen Chat in diesem Projekt öffnen.“**

Nicht warten, bis wichtige ältere Informationen bereits verloren gegangen sind.

Vor dem Chatwechsel möglichst zuerst:
`docs/TB-HELFER_AKTUELLER_STAND.md`
aktualisieren.

Danach eine vollständige Chat-Übergabe-MD erstellen.

Dateiname zum Beispiel:

`TB-HELFER_CHAT_UEBERGABE_YYYY-MM-DD_BRANCH.md`

Die Übergabe-MD enthält mindestens:

- aktuellen Branch
- aktuellen Commit
- aktuellen Draft-PR
- aktuellen Arbeitsstand
- zuletzt bearbeitete Dateien
- noch nicht dokumentierte Entscheidungen
- letzte Änderungen
- bekannte Fehler
- offene Aufgaben
- Tests und Ergebnisse
- nächsten Arbeitsschritt
- Informationen aus dem Chat, die noch nicht sicher in GitHub stehen

**WICHTIG:**

Die Chat-Übergabe-MD ist nur eine zusätzliche Übergabehilfe.

Sie ersetzt niemals GitHub.

Wenn die Übergabe-MD verloren geht oder beschädigt ist, muss der wesentliche technische Projektstand aus GitHub und den Projekt-MDs rekonstruierbar sein.

==================================================
## 8. NEUER CHAT NACH ÜBERGABE
==================================================

Eine Chat-Übergabe-MD niemals blind als aktuellen Stand übernehmen.

Im neuen Chat zuerst GitHub prüfen.

Danach:

1. `TB-HELFER_PROJEKTREGELN.md` lesen
2. `TB-HELFER_AKTUELLER_STAND.md` lesen
3. Branch / Draft-PR prüfen
4. neuesten Commit prüfen
5. anschließend Übergabe-MD ergänzend lesen

Wenn GitHub neuer ist als die Übergabe-MD, gilt GitHub.

==================================================
## GRUNDSATZ
==================================================

GitHub sichert den Projektstand.
Projekt-MDs dokumentieren Regeln und Entwicklungsstand.
Die Chat-Übergabe sichert nur zusätzlichen Arbeitskontext.

**Erst Bestand prüfen.**
**Dann verstehen.**
**Dann planen.**
**Dann ändern.**
**Dann testen.**
**Dann dokumentieren.**

Nicht bei jedem neuen Chat das Projekt neu erfinden.
