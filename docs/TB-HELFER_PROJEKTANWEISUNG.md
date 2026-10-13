# TB-HELFER 2.0 – VERBINDLICHE PROJEKTANWEISUNG

Repository: `jensmeier/TB-Helfer-2.0`

## 1. EINZIGER EINSTIEG
Vor jedem neuen Chat und Arbeitsblock zuerst `projektsteuerung/00_START_HIER.md` lesen und danach den dort festgelegten GitHub-Leseweg abarbeiten. Nicht aus altem Chat oder Erinnerung weiterarbeiten.

## 2. GITHUB VOR PROJEKTÄNDERUNG
Enthält eine Benutzernachricht eine neue Projektanforderung, Entscheidung, Vertragsänderung, Bestätigung, Fehlerfeststellung oder Statusänderung, wird sie zuerst in der passenden Datei unter `projektsteuerung/` gesichert und committed, bevor darauf aufbauend entwickelt oder „erledigt“ gemeldet wird. Reine Erklärungen oder unveränderte Auskünfte brauchen keinen Commit.

## 3. ROLLEN
- ANDROID: `feature/tb-android-2.0`, primär `app/**`; kein selbstständiger Server-Umbau.
- SERVER: `feature/tb-server-1.0`, primär `server/**`; kein beiläufiger Android-Umbau; Integrator für gemeinsame Serverstände/Excel-Konflikte.
- PRÜFUNG/WOCHENBERICHT: prüft beide Branches unabhängig; entwickelt normalerweise weder Android noch Server.

## 4. GEMEINSAME STEUERUNG
Verbindlich:
- `projektsteuerung/01_MASTERSTATUS.md`
- `projektsteuerung/02_AKTIVER_AUFTRAG.md`
- `projektsteuerung/03_ANDROID_STATUS.md`
- `projektsteuerung/04_SERVER_STATUS.md`
- `projektsteuerung/05_ANDROID_SERVER_VERTRAG.md`
- `projektsteuerung/06_E2E_TESTSTATUS.md`
- `projektsteuerung/07_OFFENE_PUNKTE.md`
- `projektsteuerung/TB-HELFER_STATUS.xlsx`

Technische Fachregeln bleiben in `docs/TB-HELFER_PROJEKTREGELN.md`.

## 5. SICHERUNG STATT CHAT-PROZENT
Es gibt keinen verlässlichen, aus dem Repository messbaren Chat-Kontext-Prozentwert. Daher Checkpoints nach jedem abgeschlossenen atomaren Arbeitsblock, vor ANDROID↔SERVER-Wechsel, nach Build/Test, vor „fertig/erledigt/bestätigt“ und sobald ältere Details unsicher erscheinen. Dann `projektsteuerung/08_CHAT_CHECKPOINT.md` und die betroffenen Statusdateien aktualisieren und committen.

## 6. ALTE ORDNER
Nicht mehr verwenden und nicht neu anlegen: `projektstatus/`, `releases/`, `uebergaben/`. Deren Inhalte liegen jetzt unter `projektsteuerung/`.

## 7. TESTBEGRIFFE
QUELLSTAND FERTIG ≠ BUILD ERFOLGREICH ≠ INSTALLIERT ≠ E2E. GitHub Actions ohne ausgeführte Runner-Steps sind kein Testnachweis.

## 8. SICHERHEIT
Keine Secrets in GitHub, Status, Logs, Screenshots oder Übergaben.

## GRUNDSATZ
**GitHub lesen → Auftrag sichern → Bestand/Vertrag prüfen → ändern → bauen/testen → Status sichern → committen → erst dann Abschluss melden.**
