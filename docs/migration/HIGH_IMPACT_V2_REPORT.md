# TB-Helfer – High-Impact Patch v2 (Build + Play-Compliance + Lint-Stopper)

Dieses Patch-Set zielt auf **stabile Builds in Android Studio Otter 3 (2025.2.x)**, entfernt den **Manifest-Stopper** aus dem Inspect-Code-Report und bringt Toolchain/Versionsstände auf einen modernen, kompatiblen Stand.

## Was geändert wurde

### 1) Manifest: Camera Permission → optionales Feature
**Problem:** `uses-permission` für CAMERA ohne passendes `uses-feature` kann (je nach Store/Device) zu unerwünschter Filterung führen und wird von Lint als Error gemeldet.

**Fix:** In `app/src/main/AndroidManifest.xml` ergänzt:
```xml
<uses-feature
    android:name="android.hardware.camera"
    android:required="false" />
```
Damit bleibt die App **auch auf Geräten ohne Kamera installierbar** (Kamera-Funktion ist optional).

---

### 2) SDK-Level: targetSdk 35 (Play-Anforderung) + compileSdk stabil
- `targetSdk = 35` bleibt (entspricht Android 15 / API 35 – für neue Updates im Play Store erforderlich). citeturn0search7turn0search11
- `compileSdk` wurde von **36 → 35** gesetzt, um **Preview-API-Reibung** zu vermeiden.

Datei: `app/build.gradle.kts`

---

### 3) minSdk sinnvoll gesetzt (wegen `java.time` Nutzung)
Im Code wird `java.time.*` genutzt. Ohne Core Library Desugaring ist das erst ab **API 26** sicher.

**Fix:** `minSdk = 26`

> Wenn du **unter API 26** unterstützen willst: sag Bescheid, dann baue ich dir **Desugaring** sauber ein (inkl. `coreLibraryDesugaring` Dependency + `compileOptions`).

---

### 4) Toolchain-Upgrade: AGP 9.0.0 + Kotlin 2.3.0 + KSP 2.3.4
Datei: `build.gradle.kts`
- **Android Gradle Plugin 9.0.0** (Jan 2026). citeturn1view1
- **Kotlin 2.3.0** (Dec 2025). citeturn1view2
- **KSP 2.3.4** (KSP2 Standard, in offiziellen Docs empfohlen). citeturn3search4turn3search2

---

### 5) Gradle Wrapper URL: Gradle 9.1
AGP 9.0.0 verlangt **Gradle 9.1.0** als Minimum/Default in der Kompatibilitätsmatrix. citeturn1view1

Datei: `gradle/wrapper/gradle-wrapper.properties`
- `gradle-8.13` → `gradle-9.1`

Hinweis: In deinem ZIP fehlt aktuell `gradlew/gradlew.bat` und `gradle-wrapper.jar`. Android Studio kann trotzdem bauen, wenn du *Gradle lokal* nutzt. Wenn du komplett „wrapper-clean“ willst, sag kurz Bescheid, dann ergänze ich dir die Wrapper-Dateien passend.

---

### 6) Stabilitäts-Schalter für AGP 9
Datei: `gradle.properties`
- `android.newDsl=false` (opt-out von neuen DSL Defaults – reduziert Migrations-Reibung). citeturn1view1
- `android.builtInKotlin=false` (wenn du Kotlin/KSP bewusst selbst versionierst, kann das stabiler sein; relevant weil AGP 9 built-in Kotlin Runtime-Dependency hat). citeturn1view1turn0search6

## Dateien, die sich geändert haben
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `build.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle.properties`

Zusätzlich aufgeräumt:
- `app/src/main/APP2_cleaned.zip` → **verschoben nach** `docs/APP2_cleaned.zip`
- `app/src/main/ic_launcher-playstore.png` → **verschoben nach** `docs/ic_launcher-playstore.png`
- `app/AGGRESSIVE_CLEANUP_REPORT.md` → **verschoben nach** `docs/AGGRESSIVE_CLEANUP_REPORT.md`

## Wie du es testest
In Android Studio:
1. **Sync**
2. Terminal (Projekt-Root):
```bash
./gradlew --version
./gradlew clean :app:assembleDebug
```
Oder in Windows:
```bat
gradlew --version
gradlew clean :app:assembleDebug
```

## Wenn irgendwas knallt – 2 schnelle Fallbacks
1) **AGP 9 DSL Stress** → `android.newDsl=false` ist schon gesetzt.
2) **Wenn built-in Kotlin Ärger macht** → `android.builtInKotlin=false` ist schon gesetzt.

---

