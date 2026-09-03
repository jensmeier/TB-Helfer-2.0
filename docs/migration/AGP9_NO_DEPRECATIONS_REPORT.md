# AGP9 Deprecation-Cleanup

## Aenderungen
- `gradle.properties`: entfernt `android.enableJetifier=true`, `android.builtInKotlin=false`, `android.newDsl=false`.
- `build.gradle.kts` (Root): Plugin-Declaration `org.jetbrains.kotlin.android` entfernt.
- `app/build.gradle.kts`: Plugin `org.jetbrains.kotlin.android` entfernt; JVM-Target kommt bei built-in Kotlin standardmaessig aus `android.compileOptions`.
- `README.md`: Versionshinweise aktualisiert.

## Entfernt (ueberfluessig / generiert)
- Ordner: `.gradle/`, `.idea/`, `.kotlin/`, `build/`, `app/build/`
- Datei: `local.properties`
- Alte Artefakte: vorherige Patch/Report-Dateien aus frueheren Durchlaeufen (z.B. HIGH_IMPACT*, AGP9_MIGRATION*).

## Hinweise
- Falls eine sehr alte Abhaengigkeit noch auf Support Libraries basiert, kann Jetifier temporaer noetig sein. Dann `android.enableJetifier=true` wieder setzen und die Abhaengigkeit aktualisieren.

## Naechste Schritte
1) Projekt oeffnen -> Gradle Sync
2) Clean/Rebuild
