# INTEGRATION-1108 – APK-Baunachweis 08.11.2026

Nutzerauftrag: „bauen“. Geprüfte Ausgangsheads Android49b66e6799e82a16518408be462260f56ab4f74f, Serverd9e85bf2cc2b94a7d32250694c4a1bc7f8a3de58; PR11 offen, PR6 Draft. Erneuter Bauauftrag vor Änderung in3341239f gesichert.

## Ergebnis
Android1.3.43/200093 erfolgreich aus15ed259f4c7385628056f220fbcf6f45f7bb067e gebaut. Anwendungsquellcode entspricht weiterhin Runtime01a6614f. Nur Gradle-Downloadweg/Prüfsumme und Release-/Statusdokumentation geändert. Servercode unverändert.

- Gradle9.3.1 direkt aus offizieller Veröffentlichung gradle/gradle-distributions, AssetSHA256 b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06. Frühere services.gradle.org-Timeouts dadurch überwunden; keine Gradleversion geändert.
- CI42473143629, Buildjob128708410252 SUCCESS: Unit5m10s, Lint1m21s, assembleDebug2m26s. Das sind Gradle-Taskdauern, keine abgerechneten monatlichen Actions-Minuten.
- Aus Original-Test-XML:228 Tests,0 Fehler,0 Fehlschläge,0 übersprungen, einschließlich4 Integration1108Test-Regressionen. LintXML:0 Fehler,391 Warnungen. Source-Contract-Job128708410108 bleibt ROT:03/13/19/83/85/86 wie vor Auftrag; Original-Log erneut geprüft. Gesamtworkflow nicht als bestanden ausgeben.
- Artefakt13614756974 enthält exakt TB-Helfer_1.3.43_200093.apk und TB-Helfer_1.3.43_200093.md. ArchivSHA256 fc0dc7251145c0922e427ae0b39729f1b52ec3e58d9353ef80165fdaae08f8ac lokal geprüft.
- APK26962792Bytes; SHA2565b025a4701e837d98542c323c9571d2c015343a53ac4f2e53836be0891b975aa stimmt mit Release-MD überein. Manifest per apkanalyzer de.tbhelfer /1.3.43/200093; apksigner-Verifikation erfolgreich. Release-MD enthält denselben Baucommit und Signaturnachweis.
- DebugzertifikatSHA256 f03307af46bb4b56015110f0e2ee1162127593151abe8a77ff88b7e9fa1837eb weicht von voriger CI-APK200092 (bf29007f…) ab. Zertifikat der installierten App unbekannt. Bei Update-Signaturkonflikt NICHT deinstallieren; Build mit bisherigem Android-Studio-Schlüssel signieren. Keine Datenlöschung als Updatehilfe.

## Noch offen
Installation/Pixel/Poco-E2E für ZIP speichern/teilen/offline/Rotation, Wetter und Android↔NAS-Abgleich. Der erfolgreiche Build ersetzt diese Tests nicht. NAS nicht deployt; Serverstand und vorherige Server-CI-Nachweise bleiben wie in INTEGRATION_1108.md dokumentiert. Keine produktiven Daten verändert, keine Migration, keine weiteren identischen CI-Wiederholungen, Budget unverändert.
