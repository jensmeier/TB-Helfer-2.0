# TB-HELFER – MASTERSTATUS
Stand 20.11.2026 · Auftrag KOMPAT-1120 · GITHUB AKTUELLER ARBEITSSTAND

## Verbindliches vorangegangenes CI-Basispaar (nicht mit den neuen Commits verwechseln)
- ANDROID: 1.3.46 / 200096, Fachcommit cda78c2f42fd7b1acd76d60612ce42d882662edf; Room-Instrumentierung 680ef37e9781eb4e4b0b8da04b5db7e6b2d42340. Android CI 42495975421 / Room CI 42496070087 BESTANDEN.
- SERVER: 1.0.0-dev / API v1 / PDF 6, Fachcode b17530d305797837d42f3049681f10cd919ea4b5, Testcommit b924da3e9bbff603a78ca55753cefb439f1ac607. Server CI 42496224535 BESTANDEN.
- Keine neue APK/versionCode, keine Migration oder NAS-Installation aus diesem Auftrag; E01–E12 unverändert E2E OFFEN.

## Neue Entwicklungscommits, Prüfstatus getrennt
- SERVER: 06191e1315d36d319f68705220c3488c55565276 (F-01/F-02/F-04 Codeänderung), 4b4065658d744a9773ca06d2b4af53c737056c02 (F-06 und Freigabekompatibilität), Workflow-Partnerpin 94fe87251c936c42807f2e60ee6c2c808b45d5a8 gegen Android 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd.
- ANDROID: 5d5c0f7fd8834a48a70d342efdc46c05bd5744fd (F-07 Codeänderung), Workflow-Partnerpin 6e321e7e8aad4464dd6768376e65943b9cda26f1 gegen Server 4b4065658d744a9773ca06d2b4af53c737056c02.
- Die revisionsgenauen CI-Läufe für diese finale Codepaarung wurden gestartet; im Zeitpunkt dieser Statussicherung noch NICHT als BESTANDEN bestätigt. Kein E2E.
- F-01/F-02/F-04/F-06/F-07: QUELLCODE GEÄNDERT, Regression/CI/Gerätebestätigung noch getrennt nachzuweisen. F-03 (Changefeed-Metadaten) und F-05 (firmenweite Rohoriginalsichtbarkeit): OFFEN, nicht als erledigt deklarieren.
- Bekannte offene Punkte bleiben geschützt: Room 20/21, KM-Sprünge, Konflikt-UI/GVP-Mast, Offline-16h, Originale älterer Geräte und fehlende Signaturkontinuität.

## Regeln und Freigaben
Die beiden Arbeitsbranches bleiben getrennt. Fachänderungen und CI-Paarungs-SHAs sind exakt zu vergleichen. GitHub != NAS != Pixel/Poco. Word, drei Fotos pro Zeile, Anzahl-Einstellung, stabile IDs, lokale Originale und historisch BESTÄTIGT/PASST/FUNKTIONIERT/ERLEDIGT sind geschützt. Keine NAS-Änderung, keine produktiven Daten verändert.
Verbindliche Details: 00_START_HIER.md, 02_AKTIVER_AUFTRAG.md, 05_ANDROID_SERVER_VERTRAG.md, 06_E2E_TESTSTATUS.md, 07_OFFENE_PUNKTE.md, 09_KOMPATIBILITAETSBASIS.md. Ältere Stände bleiben im Git-Verlauf, dem Archiv und der Übergabe vom 08.11. nachvollziehbar.
