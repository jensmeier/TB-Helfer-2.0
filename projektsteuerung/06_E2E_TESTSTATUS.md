# TB-HELFER 2.0 – E2E TESTSTATUS

Stand: 2026-09-26

| Bereich | Status | Nächster Test |
|---|---|---|
| Android 1.3.19 / 200069 Build | OFFEN | Build-Wiederholung |
| Android 200069 Installation | OFFEN | Pixel/Poco nach Build |
| Pixel ACTIVE↔PDF-Flackern | SOURCE-FIX / E2E OFFEN | 200069 praktisch prüfen |
| Poco Team-/BTB-Daten | FEHLER OFFEN | Binding/Sync/API/Logs |
| Helfer Schicht Ende → Server FINISHED → Pixel | E2E OFFEN | echter Geräte-/Server-Lauf |
| Haupt-TB Finalize + PDF | TEILWEISE BESTÄTIGT | Android-Gesamtkette offen |
| IMG-001 Handyfoto → Server | QUELLAUDIT FEHLER BESTÄTIGT / FIX OFFEN | Nach Android-Fix Original- vs Sync-Dateigröße messen |
| IMG-001 Portal → Handy | QUELLAUDIT FEHLER BESTÄTIGT / FIX OFFEN | Android muss preview/thumb laden; danach Quelle/Rendition/Gerät vergleichen |
| IMG-001 Handy → anderes Handy | QUELLAUDIT FEHLER BESTÄTIGT / FIX OFFEN | Nach Android-Fix Pixel/Poco Abmessungen/Größe vergleichen |
| Migration 015 | DEPLOY/E2E OFFEN | Runtime-Probe |
| 15×15 Delta-/Lasttest | E2E OFFEN | Staging-Probe |

Source-Check ≠ Build ≠ Installation ≠ E2E.
