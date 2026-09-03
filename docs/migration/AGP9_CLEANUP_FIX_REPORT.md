# AGP9 Migration – Fix & Cleanup (Build repariert)

## Problem (warum es bei dir nicht geklappt hat)
Dein Projekt enthielt **AGP 9.0.0**, aber der **Gradle Wrapper war kaputt/inkomplett**:
- `gradlew` / `gradlew.bat` fehlten
- `gradle/wrapper/gradle-wrapper.jar` fehlte

Dadurch hat Android Studio/Gradle **nicht** den Wrapper genutzt, sondern eine vorhandene Gradle-Version (z.B. 8.13),
und AGP 9 bricht dann sofort ab: *„Minimum supported Gradle version is 9.1.0 … current is 8.13“*.

## Fix (was ich geändert habe)
### 1) Gradle Wrapper wiederhergestellt
- `gradlew` (Unix/Mac)
- `gradlew.bat` (Windows)
- `gradle/wrapper/gradle-wrapper.jar` (**minimaler Wrapper**; lädt die in `gradle-wrapper.properties` konfigurierte Gradle-Version)

Die Datei `gradle/wrapper/gradle-wrapper.properties` verweist bereits auf:
- `distributionUrl=.../gradle-9.1.0-bin.zip`

### 2) AGP9-kompatible SDK/Deps bestätigt
In `app/build.gradle.kts` ist gesetzt:
- `compileSdk = 36` (notwendig wegen `androidx.core:core-ktx:1.17.0`)
- `minSdk = 23` (notwendig wegen CameraX 1.5.x / `camera-video` transitiv)
- `targetSdk = 36`

### 3) Überflüssiges entfernt (damit ZIP/Repo sauber bleibt)
Gelöscht (wird immer neu erzeugt / gehört nicht in den Source-Tree):
- `.gradle/`, `.idea/`, `.kotlin/`
- `build/`, `app/build/`
- `local.properties`
- alte Patch-/Report-Dateien im Root (wurden vorher nur für Review genutzt)

> Hinweis: `local.properties` wird bei dir lokal von Android Studio neu angelegt.

## Was du lokal noch tun musst
1. In Android Studio **SDK Manager**:
   - Android API **36** Platform installieren
   - Build Tools **36.x** (falls angeboten) installieren

2. Build:
```bash
./gradlew clean :app:assembleDebug
```

## Wenn du minSdk 21 unbedingt brauchst
Dann muss CameraX runter (1.4.x o.ä.) **oder** Video/CameraX komplett raus. `tools:overrideLibrary` ist möglich, aber riskant (Runtime-Crash).

